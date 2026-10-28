package pl.wolter.ostoja.sync

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import pl.wolter.ostoja.data.VaultData
import java.net.URLDecoder
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Synchronizacja telefon ↔ komputer w sieci lokalnej, bez chmury i bez konta.
 *
 * Komputer (aplikacja Ostoja na Windows) nasłuchuje na porcie [PORT]. Telefon łączy się z nim po
 * zeskanowaniu kodu QR parowania, który niesie losowy 32-bajtowy sekret. Z sekretu oba urządzenia
 * wyprowadzają klucz AES-256-GCM (HKDF-SHA256) i każde zapytanie oraz odpowiedź są nim szyfrowane
 * i uwierzytelniane - sieć widzi tylko szum. Sekret nigdy nie przechodzi przez sieć.
 */
object SyncProtocol {
    const val PORT = 47811
    const val DISCOVERY_PORT = 47812
    const val VERSION = 1
    /** Okno, w którym przyjmowane są zapytania (zegary telefonu i komputera mogą się trochę różnić). */
    const val MAX_SKEW_MS = 10 * 60 * 1000L
    /** Jak długo komputer jest odblokowany po potwierdzeniu na telefonie. */
    const val SESSION_MS = 7L * 24 * 3600 * 1000
    const val REQUEST_MS = 120_000L

    const val HEADER_DEVICE = "X-Ostoja-Device"
    const val HEADER_PAIR = "X-Ostoja-Pair"

    val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; explicitNulls = false }
}

/** Zawartość kodu QR parowania: `ostoja://pair?v=1&c=…&n=…&h=192.168.1.5,10.0.0.2&p=47811&t=…&k=…`. */
data class PairInfo(
    val computerId: String,
    val computerName: String,
    val hosts: List<String>,
    val port: Int,
    val ticket: String,
    val secret: ByteArray,
) {
    fun toUri(): String {
        fun e(s: String) = URLEncoder.encode(s, "UTF-8").replace("+", "%20")
        return "ostoja://pair?v=${SyncProtocol.VERSION}&c=${e(computerId)}&n=${e(computerName)}&h=${e(hosts.joinToString(","))}" +
            "&p=$port&t=${e(ticket)}&k=${SyncCrypto.b64u(secret)}"
    }

    companion object {
        fun isPair(s: String) = s.trim().startsWith("ostoja://pair", ignoreCase = true)

        fun parse(s: String): PairInfo {
            require(isPair(s)) { "To nie jest kod parowania Ostoi" }
            val q = s.substringAfter('?', "").split('&').associate {
                it.substringBefore('=') to URLDecoder.decode(it.substringAfter('=', ""), "UTF-8")
            }
            val secret = SyncCrypto.unb64u(q["k"] ?: error("Brak klucza w kodzie"))
            require(secret.size == 32) { "Uszkodzony kod parowania" }
            return PairInfo(
                computerId = q["c"] ?: error("Uszkodzony kod parowania"),
                computerName = q["n"].orEmpty().ifBlank { "Komputer" },
                hosts = q["h"].orEmpty().split(',').map { it.trim() }.filter { it.isNotEmpty() },
                port = q["p"]?.toIntOrNull() ?: SyncProtocol.PORT,
                ticket = q["t"] ?: error("Uszkodzony kod parowania"),
                secret = secret,
            )
        }
    }
}

object SyncCrypto {
    private val rnd = SecureRandom()

    fun random(n: Int) = ByteArray(n).also(rnd::nextBytes)
    fun token() = b64u(random(18))

    fun b64u(b: ByteArray): String = Base64.getUrlEncoder().withoutPadding().encodeToString(b)
    fun unb64u(s: String): ByteArray = Base64.getUrlDecoder().decode(s.trim())
    fun b64(b: ByteArray): String = Base64.getEncoder().encodeToString(b)
    fun unb64(s: String): ByteArray = Base64.getDecoder().decode(s)

    fun sha256(b: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(b)

    private fun hmac(key: ByteArray, data: ByteArray): ByteArray =
        Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(key, "HmacSHA256")) }.doFinal(data)

    /** HKDF-SHA256 (RFC 5869) dla jednego bloku wyjścia. */
    fun hkdf(ikm: ByteArray, salt: String, info: String): ByteArray {
        val prk = hmac(salt.toByteArray(), ikm)
        return hmac(prk, info.toByteArray() + byteArrayOf(1))
    }

    /** Klucz kanału wyprowadzony z sekretu z kodu QR. */
    fun channelKey(secret: ByteArray) = hkdf(secret, "ostoja-sync-v1", "channel")

    fun seal(key: ByteArray, plain: ByteArray, aad: String): ByteArray {
        val iv = random(12)
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
        c.updateAAD(aad.toByteArray())
        return iv + c.doFinal(plain)
    }

    /** Rzuca wyjątek, gdy dane zostały zmienione albo zaszyfrowano je innym kluczem. */
    fun open(key: ByteArray, blob: ByteArray, aad: String): ByteArray {
        require(blob.size > 28) { "Za krótka wiadomość" }
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, blob, 0, 12))
        c.updateAAD(aad.toByteArray())
        return c.doFinal(blob, 12, blob.size - 12)
    }
}

/** Koperta każdej wiadomości: czas i jednorazowy numer chronią przed powtórzeniem przechwyconego zapytania. */
@Serializable
data class Envelope(val ts: Long, val nonce: String, val body: JsonElement)

/** Szyfrowanie zapytań i odpowiedzi. AAD wiąże wiadomość z kierunkiem, ścieżką i urządzeniem. */
object Wire {
    private fun aad(dir: String, path: String, device: String) = "ostoja${SyncProtocol.VERSION}|$dir|$path|$device"

    fun sealRequest(key: ByteArray, path: String, device: String, body: JsonElement, nonce: String, now: Long = System.currentTimeMillis()) =
        SyncCrypto.seal(key, SyncProtocol.json.encodeToString(Envelope.serializer(), Envelope(now, nonce, body)).toByteArray(), aad("req", path, device))

    fun openRequest(key: ByteArray, path: String, device: String, blob: ByteArray): Envelope =
        SyncProtocol.json.decodeFromString(Envelope.serializer(), String(SyncCrypto.open(key, blob, aad("req", path, device))))

    fun sealResponse(key: ByteArray, path: String, device: String, nonce: String, body: JsonElement) =
        SyncCrypto.seal(key, SyncProtocol.json.encodeToString(Envelope.serializer(), Envelope(System.currentTimeMillis(), nonce, body)).toByteArray(), aad("resp", path, device))

    fun openResponse(key: ByteArray, path: String, device: String, nonce: String, blob: ByteArray): JsonElement {
        val env = SyncProtocol.json.decodeFromString(Envelope.serializer(), String(SyncCrypto.open(key, blob, aad("resp", path, device))))
        require(env.nonce == nonce) { "Odpowiedź nie pasuje do zapytania" }
        return env.body
    }
}

/** Ustawienia wyglądu z telefonu, żeby komputer wyglądał tak samo. */
@Serializable
data class SyncPrefs(val accent: Int = 0, val icons: Boolean = true, val groupDigits: Boolean = true)

@Serializable
data class PairRequest(val phoneId: String, val phoneName: String, val vault: VaultData, val prefs: SyncPrefs = SyncPrefs())

@Serializable
data class PairResponse(val computerId: String, val computerName: String, val vault: VaultData, val rev: Long)

@Serializable
data class SyncRequest(val phoneName: String, val vault: VaultData, val prefs: SyncPrefs = SyncPrefs())

@Serializable
data class SyncResponse(val vault: VaultData, val rev: Long, val computerName: String, val request: LoginRequest? = null)

@Serializable
data class WaitRequest(val rev: Long)

@Serializable
data class WaitResponse(val rev: Long, val request: LoginRequest? = null)

/** Prośba z komputera „Czy to Ty logujesz się?”. Telefon nie zna poprawnej liczby - sprawdza ją komputer. */
@Serializable
data class LoginRequest(
    val id: String,
    val options: List<Int>,
    val computerName: String,
    val app: String,
    val created: Long,
    val expires: Long,
)

@Serializable
data class AnswerRequest(val id: String, val approve: Boolean, val picked: Int? = null)

/** ok | wrong | expired | rejected */
@Serializable
data class AnswerResponse(val result: String, val until: Long = 0)

/** „Zaloguj na komputerze”: komputer przekazuje polecenie do rozszerzenia Firefox. Odpowiedź: ok | locked | noext | missing. */
@Serializable
data class OpenRequest(val loginId: String)

@Serializable
class Empty
