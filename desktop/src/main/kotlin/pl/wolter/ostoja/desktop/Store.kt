package pl.wolter.ostoja.desktop

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import pl.wolter.ostoja.data.GenOptions
import pl.wolter.ostoja.data.Login
import pl.wolter.ostoja.data.OtpAccount
import pl.wolter.ostoja.data.PasswordTools
import pl.wolter.ostoja.data.VaultData
import pl.wolter.ostoja.data.VaultOps
import pl.wolter.ostoja.data.VaultSync
import pl.wolter.ostoja.data.countLabel
import pl.wolter.ostoja.sync.LoginRequest
import pl.wolter.ostoja.sync.PairInfo
import pl.wolter.ostoja.sync.SyncCrypto
import pl.wolter.ostoja.sync.SyncPrefs
import pl.wolter.ostoja.sync.SyncProtocol
import java.io.File
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

@Serializable
data class Phone(
    val id: String,
    val name: String,
    /** Klucz kanału (base64) - wyprowadzony z sekretu z kodu QR, nigdy nie przesyłany. */
    val key: String,
    val pairedAt: Long,
    val lastSeen: Long = 0,
    val lastSync: Long = 0,
)

@Serializable
data class Persisted(
    val computerId: String = SyncCrypto.token(),
    val phones: List<Phone> = emptyList(),
    val vault: VaultData = VaultData(),
    val prefs: SyncPrefs = SyncPrefs(),
    /** Do kiedy komputer jest odblokowany (potwierdzenie na telefonie co 7 dni). */
    val unlockedUntil: Long = 0,
    val confirmedAt: Long = 0,
    /** Klucz odzyskiwania: „sól:skrót” PBKDF2. */
    val recovery: String? = null,
    /** Kiedy wpis przyszedł z telefonu - do etykiety „Z telefonu”. */
    val arrived: Map<String, Long> = emptyMap(),
    /** Połączone rozszerzenia przeglądarki (Firefox). */
    val extensions: List<ExtClient> = emptyList(),
)

@Serializable
data class ExtClient(
    val id: String,
    val name: String,
    /** Klucz kanału (base64) z wymiany ECDH przy parowaniu. */
    val key: String,
    val pairedAt: Long,
    val lastSeen: Long = 0,
)

/** Stan logowania na komputerze (ekran „Sejf zablokowany” z prototypu). */
sealed interface Unlock {
    data object Idle : Unlock
    data class Waiting(val request: LoginRequest, val code: Int) : Unlock
    data class Done(val ok: Boolean, val reason: String = "", val until: Long = 0) : Unlock
}

data class Toast(val text: String, val at: Long = System.nanoTime())

/**
 * Stan aplikacji na komputerze. Wszystko trzymane w jednym pliku zaszyfrowanym AES-256-GCM kluczem
 * chronionym przez Windows (DPAPI, konto użytkownika). Zmiany z telefonu i z okna przechodzą tędy,
 * a czekające zapytania telefonu (long-poll) są budzone po każdej zmianie.
 */
class Store(private val dir: File) {
    private val file = File(dir, "ostoja.bin")
    private val key = MasterKey.load(dir)
    private val lock = Object()

    private val _state = MutableStateFlow(load())
    val state: StateFlow<Persisted> = _state.asStateFlow()
    val value get() = _state.value

    private val _unlock = MutableStateFlow<Unlock>(Unlock.Idle)
    val unlock: StateFlow<Unlock> = _unlock.asStateFlow()

    /** Ostatnia wymiana z telefonem (pulsująca kropka „synchronizuję…”). */
    val syncPulse = MutableStateFlow(0L)
    val toasts = MutableSharedFlow<Toast>(extraBufferCapacity = 8)

    /** Licznik zmian; po restarcie zaczyna od bieżącego czasu, więc telefon zawsze zauważy różnicę. */
    @Volatile var rev = System.currentTimeMillis()
        private set

    val computerName: String = System.getenv("COMPUTERNAME")
        ?: runCatching { java.net.InetAddress.getLocalHost().hostName }.getOrDefault("Komputer")

    private var pairing: Pair<PairInfo, Long>? = null
    private val seenNonces = LinkedHashMap<String, Long>()

    // --- odczyt i zapis ---

    private fun load(): Persisted {
        if (!file.exists()) return Persisted()
        return runCatching {
            val plain = SyncCrypto.open(key, file.readBytes(), "ostoja-desktop")
            SyncProtocol.json.decodeFromString(Persisted.serializer(), String(plain))
        }.getOrElse {
            file.renameTo(File(dir, "ostoja.broken.${System.currentTimeMillis()}"))
            Persisted()
        }
    }

    private fun save(p: Persisted) {
        val tmp = File(dir, "ostoja.tmp")
        tmp.writeBytes(SyncCrypto.seal(key, SyncProtocol.json.encodeToString(Persisted.serializer(), p).toByteArray(), "ostoja-desktop"))
        if (!tmp.renameTo(file)) { file.delete(); tmp.renameTo(file) }
    }

    fun mutate(fn: (Persisted) -> Persisted) = synchronized(lock) {
        val cur = _state.value
        val next = fn(cur)
        if (next == cur) return@synchronized
        save(next)
        _state.value = next
        // Czekające telefony budzi tylko zmiana sejfu - sam „ostatnio widziany” nie powinien wywoływać synchronizacji.
        if (next.vault != cur.vault) bump()
    }

    private fun bump() = synchronized(lock) {
        rev++
        lock.notifyAll()
    }

    /** Zmiana sejfu zrobiona na komputerze: znaczniki czasu jak na telefonie. */
    fun editVault(fn: (VaultData) -> VaultData) = mutate { p ->
        val next = fn(p.vault)
        if (next == p.vault) p else p.copy(vault = VaultSync.stamp(p.vault, next))
    }

    /** Long-poll telefonu: wraca, gdy coś się zmieni albo minie czas. */
    fun awaitChange(since: Long, timeoutMs: Long): Long = synchronized(lock) {
        val end = System.currentTimeMillis() + timeoutMs
        while (rev == since) {
            val left = end - System.currentTimeMillis()
            if (left <= 0) break
            lock.wait(left)
        }
        rev
    }

    fun toast(text: String) { toasts.tryEmit(Toast(text)) }

    // --- blokada ---

    val locked get() = value.phones.isEmpty() || System.currentTimeMillis() >= value.unlockedUntil

    fun lockNow() {
        mutate { it.copy(unlockedUntil = 0) }
        _unlock.value = Unlock.Idle
    }

    private fun unlockFor7Days() = System.currentTimeMillis().let { now ->
        val until = now + SyncProtocol.SESSION_MS
        mutate { it.copy(unlockedUntil = until, confirmedAt = now) }
        until
    }

    /** „Potwierdź na telefonie”: liczba na ekranie komputera i trzy do wyboru na telefonie. */
    fun startUnlock() {
        val rnd = java.security.SecureRandom()
        val code = 10 + rnd.nextInt(90)
        val opts = linkedSetOf(code)
        while (opts.size < 3) opts += 10 + rnd.nextInt(90)
        val now = System.currentTimeMillis()
        val req = LoginRequest(
            id = SyncCrypto.token(),
            options = opts.shuffled(rnd),
            computerName = computerName,
            app = "Ostoja na Windows",
            created = now,
            expires = now + SyncProtocol.REQUEST_MS,
        )
        _unlock.value = Unlock.Waiting(req, code)
        bump()
    }

    fun cancelUnlock() {
        _unlock.value = Unlock.Idle
        bump()
    }

    fun resetUnlock() { _unlock.value = Unlock.Idle }

    /** Prośba czekająca na odpowiedź z telefonu (null, gdy żadnej nie ma albo wygasła). */
    fun pendingRequest(): LoginRequest? {
        val u = _unlock.value as? Unlock.Waiting ?: return null
        if (System.currentTimeMillis() > u.request.expires) {
            _unlock.value = Unlock.Done(false, "expired")
            return null
        }
        return u.request
    }

    /** Odpowiedź telefonu. Zwraca ok | wrong | expired | rejected. */
    fun answer(phone: Phone, id: String, approve: Boolean, picked: Int?): Pair<String, Long> {
        val u = _unlock.value as? Unlock.Waiting
        if (u == null || u.request.id != id) return "expired" to 0L
        if (System.currentTimeMillis() > u.request.expires) {
            _unlock.value = Unlock.Done(false, "expired"); bump(); return "expired" to 0L
        }
        return when {
            !approve -> {
                // „To nie ja” - komputer zostaje wylogowany.
                mutate { it.copy(unlockedUntil = 0) }
                _unlock.value = Unlock.Done(false, "rejected")
                bump()
                "rejected" to 0L
            }
            picked != u.code -> {
                _unlock.value = Unlock.Done(false, "wrong")
                bump()
                "wrong" to 0L
            }
            else -> {
                val until = unlockFor7Days()
                _unlock.value = Unlock.Done(true, phone.name, until)
                bump()
                "ok" to until
            }
        }
    }

    // --- klucz odzyskiwania ---

    private fun pbkdf2(key: String, salt: ByteArray): ByteArray =
        SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            .generateSecret(PBEKeySpec(normalizeRecovery(key).toCharArray(), salt, 310_000, 256)).encoded

    private fun normalizeRecovery(k: String) = k.uppercase().filter { it.isLetterOrDigit() }

    fun newRecoveryKey(): String {
        val alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        val rnd = java.security.SecureRandom()
        val raw = (1..24).map { alphabet[rnd.nextInt(alphabet.length)] }.joinToString("")
        val salt = SyncCrypto.random(16)
        mutate { it.copy(recovery = SyncCrypto.b64(salt) + ":" + SyncCrypto.b64(pbkdf2(raw, salt))) }
        return raw.chunked(4).joinToString("-")
    }

    private var recoveryFails = ArrayDeque<Long>()

    /** null = odblokowano; inaczej komunikat błędu. */
    fun unlockWithRecovery(input: String): String? {
        val rec = value.recovery ?: return "Klucz odzyskiwania nie został utworzony"
        val now = System.currentTimeMillis()
        while (recoveryFails.isNotEmpty() && now - recoveryFails.first() > 5 * 60_000) recoveryFails.removeFirst()
        if (recoveryFails.size >= 5) return "Za dużo prób - spróbuj za kilka minut"
        val (salt, hash) = rec.split(':').let { SyncCrypto.unb64(it[0]) to SyncCrypto.unb64(it[1]) }
        if (!java.security.MessageDigest.isEqual(pbkdf2(input, salt), hash)) {
            recoveryFails.addLast(now)
            return "Niepoprawny klucz"
        }
        unlockFor7Days()
        _unlock.value = Unlock.Done(true, "klucz odzyskiwania", value.unlockedUntil)
        return null
    }

    // --- parowanie ---

    /** Kod QR parowania, ważny 10 minut; odświeżany przez ekran Urządzenia. */
    fun pairingInfo(): PairInfo = synchronized(lock) {
        val now = System.currentTimeMillis()
        pairing?.let { (p, exp) -> if (exp - now > 60_000) return p }
        val p = PairInfo(value.computerId, computerName, Net.localAddresses(), SyncProtocol.PORT, SyncCrypto.token(), SyncCrypto.random(32))
        pairing = p to now + 10 * 60_000
        if (printPairing) println("PAIR " + p.toUri())
        p
    }

    fun pairingSecret(ticket: String): ByteArray? = synchronized(lock) {
        val (p, exp) = pairing ?: return null
        if (p.ticket != ticket || System.currentTimeMillis() > exp) null else p.secret
    }

    fun completePairing(phoneId: String, phoneName: String, channelKey: ByteArray, phoneVault: VaultData, prefs: SyncPrefs) {
        synchronized(lock) { pairing = null }
        val now = System.currentTimeMillis()
        mutate { p ->
            val phone = Phone(phoneId, phoneName, SyncCrypto.b64(channelKey), now, now, now)
            p.copy(
                phones = p.phones.filterNot { it.id == phoneId } + phone,
                vault = VaultSync.merge(p.vault, phoneVault),
                prefs = prefs,
                // Skan kodu i odcisk palca na telefonie to potwierdzenie tożsamości - komputer jest odblokowany.
                unlockedUntil = now + SyncProtocol.SESSION_MS,
                confirmedAt = now,
            )
        }
        syncPulse.value = now
        toast("Połączono z $phoneName")
    }

    fun phone(id: String) = value.phones.firstOrNull { it.id == id }

    /** Odrzuca powtórzone zapytanie (ten sam numer jednorazowy) i zapytania spoza okna czasu. */
    fun freshNonce(nonce: String, ts: Long): Boolean = synchronized(seenNonces) {
        val now = System.currentTimeMillis()
        if (kotlin.math.abs(now - ts) > SyncProtocol.MAX_SKEW_MS) return false
        seenNonces.entries.removeIf { now - it.value > 2 * SyncProtocol.MAX_SKEW_MS }
        seenNonces.put(nonce, now) == null
    }

    /** Synchronizacja z telefonem: scalenie, zapamiętanie nowych wpisów do etykiety „Z telefonu”. */
    fun syncFromPhone(phone: Phone, name: String, phoneVault: VaultData, prefs: SyncPrefs): VaultData {
        val now = System.currentTimeMillis()
        var fresh = emptyList<String>()
        mutate { p ->
            val merged = VaultSync.merge(p.vault, phoneVault)
            val before = (p.vault.logins.map { it.id } + p.vault.otps.map { it.id }).toSet()
            val newLogins = merged.logins.filter { it.id !in before && phoneVault.login(it.id) != null }
            val newOtps = merged.otps.filter { it.id !in before && phoneVault.otp(it.id) != null }
            fresh = newLogins.map { it.title } + newOtps.map { it.title }
            val arrived = (p.arrived + (newLogins.map { it.id } + newOtps.map { it.id }).associateWith { now })
                .filter { now - it.value < 24 * 3600_000L }
            p.copy(
                vault = merged,
                prefs = prefs,
                arrived = arrived,
                phones = p.phones.map { if (it.id == phone.id) it.copy(name = name, lastSeen = now, lastSync = now) else it },
            )
        }
        syncPulse.value = now
        when (fresh.size) {
            0 -> {}
            1 -> toast("Nowe konto z ${name} · ${fresh.first()}")
            else -> toast(countLabel(fresh.size, "nowy wpis", "nowe wpisy", "nowych wpisów") + " z telefonu")
        }
        return value.vault
    }

    /** Telefon jest w zasięgu - aktualizowane przy każdym zapytaniu, bez zapisu na dysk co chwilę. */
    fun seen(phone: Phone) {
        val now = System.currentTimeMillis()
        if (now - phone.lastSeen < 30_000) return
        mutate { p -> p.copy(phones = p.phones.map { if (it.id == phone.id) it.copy(lastSeen = now) else it }) }
    }

    fun unpair(phoneId: String) {
        mutate { p ->
            val left = p.phones.filterNot { it.id == phoneId }
            if (left.isEmpty()) Persisted(computerId = p.computerId) else p.copy(phones = left)
        }
    }

    /** „Odłącz i usuń dane z komputera”. */
    fun wipe() {
        mutate { p -> Persisted(computerId = SyncCrypto.token()) }
        _unlock.value = Unlock.Idle
    }

    // --- operacje z okna ---

    fun regenerate(id: String, length: Int): String {
        val pw = PasswordTools.generate(GenOptions(length = length))
        editVault { d -> d.login(id)?.let { VaultOps.saveLogin(d, it.copy(password = pw)) } ?: d }
        return pw
    }

    fun addOtps(list: List<OtpAccount>): List<OtpAccount> {
        var added = emptyList<OtpAccount>()
        editVault { d -> VaultOps.addOtps(d, list).also { added = it.second }.first }
        return added
    }

    fun saveLogin(l: Login) = editVault { d -> VaultOps.linkOtp(VaultOps.saveLogin(d, l), l.id) }

    /** Tylko do testów (--print-pair): kod parowania na standardowe wyjście, gdy nie ma aparatu (emulator). */
    var printPairing = false
}
