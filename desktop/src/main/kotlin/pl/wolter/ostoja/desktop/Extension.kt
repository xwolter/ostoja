package pl.wolter.ostoja.desktop

import com.sun.net.httpserver.HttpExchange
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import pl.wolter.ostoja.data.Brand
import pl.wolter.ostoja.data.Login
import pl.wolter.ostoja.data.Otp
import pl.wolter.ostoja.data.OtpUri
import pl.wolter.ostoja.data.hostOf
import pl.wolter.ostoja.sync.SyncCrypto
import pl.wolter.ostoja.sync.SyncProtocol
import pl.wolter.ostoja.sync.Wire
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.spec.ECGenParameterSpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.KeyAgreement

// --- wiadomości rozszerzenia (ten sam format JSON co w firefox/lib/protocol.js) ---

@Serializable data class ExtPairRequest(val id: String, val name: String, val pub: String)
@Serializable data class ExtPairResponse(val pub: String, val computerName: String)
@Serializable data class ExtIdRequest(val id: String)
/** Zrzut widocznej części karty (JPEG w base64) - z niego aplikacja odczytuje kod QR konta 2FA. */
@Serializable data class ExtLoginUrl(val id: String, val url: String)
@Serializable data class ExtQrRequest(val image: String, val url: String = "")
/** error: locked | noqr | nototp | exists */
@Serializable data class ExtOtpAdded(val ok: Boolean, val title: String = "", val error: String = "")
@Serializable data class ExtPairStatus(val state: String)
@Serializable data class ExtState(
    val locked: Boolean,
    val paired: Boolean,
    val computerName: String,
    val phoneName: String = "",
    val accent: Int = 0,
    val icons: Boolean = true,
)
@Serializable data class ExtItem(val id: String, val title: String, val user: String, val url: String, val domain: String, val has2fa: Boolean)
@Serializable data class ExtList(val locked: Boolean, val items: List<ExtItem> = emptyList())
@Serializable data class ExtCreds(
    val ok: Boolean,
    val error: String = "",
    val title: String = "",
    val username: String = "",
    val password: String = "",
    val url: String = "",
    val domain: String = "",
    val hasOtp: Boolean = false,
)
@Serializable data class ExtCode(val ok: Boolean, val code: String = "", val remaining: Int = 0)
@Serializable data class ExtWait(val since: Long = 0)
@Serializable data class ExtCommand(val id: String, val type: String, val loginId: String, val source: String, val created: Long)
@Serializable data class ExtCommands(val locked: Boolean, val commands: List<ExtCommand> = emptyList())
@Serializable data class ExtCheckRequest(val url: String, val username: String, val password: String)
/** new | same | changed | locked */
@Serializable data class ExtCheck(val state: String, val id: String = "", val title: String = "")
@Serializable data class ExtSaveRequest(val id: String? = null, val title: String = "", val url: String, val username: String, val password: String, val note: String = "")
@Serializable data class ExtSaveResult(val ok: Boolean, val id: String = "", val title: String = "", val error: String = "")
@Serializable data class ExtUnlock(val state: String, val code: Int = 0, val expires: Long = 0)

/** Prośba rozszerzenia o połączenie - czeka na „Połącz” w oknie aplikacji. */
data class ExtPairing(val id: String, val name: String, val key: ByteArray, val code: String, val state: String = "waiting")

/**
 * Most między aplikacją a rozszerzeniem Ostoja w Firefoksie.
 *
 * Rozszerzenie łączy się tylko z tym komputerem (127.0.0.1). Przy parowaniu obie strony wymieniają
 * klucze ECDH P-256 i z wspólnego sekretu liczą klucz kanału i 6-cyfrowy kod, który użytkownik
 * porównuje w oknie aplikacji i w rozszerzeniu. Potem każde zapytanie jest szyfrowane AES-256-GCM
 * (ten sam format co synchronizacja z telefonem), więc inne programy na komputerze nie odczytają haseł.
 * Hasło trafia do rozszerzenia dopiero w chwili logowania, na konkretny wpis.
 */
class ExtBridge(private val store: Store, private val onShow: () -> Unit) {
    private val json = SyncProtocol.json
    private val _pairing = MutableStateFlow<ExtPairing?>(null)
    val pairing: StateFlow<ExtPairing?> = _pairing.asStateFlow()

    /** Polecenie czeka, aż rozszerzenie pobierze dane wpisu („creds”) - dopiero to znaczy, że karta się otwiera. */
    private class Pending(val cmd: ExtCommand, var sentAt: Long = 0)
    private val queue = ArrayList<Pending>()
    private val signal = Object()

    /** Uruchomienie Firefoksa, gdy nikt nie odebrał polecenia (przeglądarka zamknięta). W testach podmieniane. */
    internal var launchBrowser: () -> Boolean = ::startFirefox
    @Volatile private var launchedAt = 0L

    // --- parowanie ---

    private fun ecdh(peerSpki: ByteArray): Pair<ByteArray, ByteArray> {
        val gen = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }
        val mine = gen.generateKeyPair()
        val peer = KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(peerSpki))
        val shared = KeyAgreement.getInstance("ECDH").apply { init(mine.private); doPhase(peer, true) }.generateSecret()
        return mine.public.encoded to shared
    }

    /** Kod do porównania: 6 cyfr z HKDF wspólnego sekretu (ten sam w rozszerzeniu). */
    private fun pairCode(shared: ByteArray): String {
        val h = SyncCrypto.hkdf(shared, "ostoja-ext-v1", "code")
        val n = ((h[0].toLong() and 0xFF) shl 24) or ((h[1].toLong() and 0xFF) shl 16) or ((h[2].toLong() and 0xFF) shl 8) or (h[3].toLong() and 0xFF)
        return (n % 1_000_000).toString().padStart(6, '0')
    }

    fun approve(ok: Boolean) {
        val p = _pairing.value ?: return
        if (ok) {
            store.mutate { s ->
                s.copy(extensions = s.extensions.filterNot { it.id == p.id } +
                    ExtClient(p.id, p.name, SyncCrypto.b64(p.key), System.currentTimeMillis(), System.currentTimeMillis()))
            }
            store.toast("Połączono z rozszerzeniem · ${p.name}")
        }
        _pairing.value = p.copy(state = if (ok) "ok" else "rejected")
    }

    fun remove(id: String) = store.mutate { s -> s.copy(extensions = s.extensions.filterNot { it.id == id }) }

    fun online(c: ExtClient, now: Long = System.currentTimeMillis()) = now - c.lastSeen < 60_000

    val anyOnline get() = store.value.extensions.any { online(it) }

    // --- polecenia z aplikacji i z telefonu ---

    /**
     * „Otwórz i zaloguj”: rozszerzenie odbierze polecenie przez długie zapytanie. Gdy Firefox jest zamknięty,
     * nikt go nie odbierze - wtedy po chwili uruchamiamy Firefoksa, a polecenie czeka (do minuty), aż
     * rozszerzenie wystartuje i zapyta o polecenia. Fałsz tylko wtedy, gdy żadne rozszerzenie nie jest połączone.
     */
    fun queueLogin(loginId: String, source: String): Boolean {
        if (store.value.extensions.isEmpty()) return false
        val p = Pending(ExtCommand(SyncCrypto.token(), "login", loginId, source, System.currentTimeMillis()))
        synchronized(signal) {
            queue.removeAll { p.cmd.created - it.cmd.created > TTL }
            queue += p
            signal.notifyAll()
        }
        Thread {
            Thread.sleep(CONFIRM_MS)
            if (synchronized(signal) { p !in queue }) return@Thread
            // Zapytanie z zamkniętej przeglądarki mogło „odebrać” polecenie w pustkę - nowa przeglądarka dostanie je od razu.
            synchronized(signal) { p.sentAt = 0 }
            val now = System.currentTimeMillis()
            if (now - launchedAt < 20_000) return@Thread
            launchedAt = now
            if (launchBrowser()) store.toast("Uruchamiam Firefoksa i loguję…")
            else store.toast("Nie udało się uruchomić Firefoksa - otwórz go, a zaloguję się sam")
        }.apply { isDaemon = true }.start()
        return true
    }

    /** Rozszerzenie pobrało dane wpisu - polecenie dotarło. */
    private fun confirm(loginId: String) = synchronized(signal) { queue.removeAll { it.cmd.loginId == loginId } }

    internal fun takeCommands(timeoutMs: Long): List<ExtCommand> {
        val end = System.currentTimeMillis() + timeoutMs
        synchronized(signal) {
            while (true) {
                val now = System.currentTimeMillis()
                queue.removeAll { now - it.cmd.created > TTL }
                // Wysłane, ale niepotwierdzone polecenie idzie jeszcze raz (np. po ponownym uruchomieniu Firefoksa).
                val ready = queue.filter { it.sentAt == 0L || now - it.sentAt > RESEND_MS }
                if (ready.isNotEmpty()) {
                    ready.forEach { it.sentAt = now }
                    return ready.map { it.cmd }
                }
                val left = end - now
                if (left <= 0) return emptyList()
                val resend = queue.minOfOrNull { it.sentAt + RESEND_MS - now } ?: left
                signal.wait(minOf(left, maxOf(50, resend)))
            }
        }
    }

    /** Firefox z Program Files (albo z instalacji dla jednego użytkownika); w ostateczności „start firefox”. */
    private fun startFirefox(): Boolean = runCatching {
        val exe = listOfNotNull(System.getenv("ProgramFiles"), System.getenv("ProgramFiles(x86)"), System.getenv("LOCALAPPDATA"))
            .map { java.io.File(it, "Mozilla Firefox/firefox.exe") }.firstOrNull { it.isFile }
        val cmd = if (exe != null) listOf(exe.path) else listOf("cmd", "/c", "start", "", "firefox")
        ProcessBuilder(cmd).redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD).start()
        true
    }.getOrDefault(false)

    // --- HTTP ---

    private fun reply(ex: HttpExchange, code: Int, body: ByteArray, type: String = "application/octet-stream") {
        ex.responseHeaders.add("Content-Type", type)
        ex.responseHeaders.add("Cache-Control", "no-store")
        // Rozszerzenie ma uprawnienie do 127.0.0.1, ale odpowiedź CORS nie zaszkodzi przy testach z moz-extension://.
        ex.requestHeaders.getFirst("Origin")?.let { ex.responseHeaders.add("Access-Control-Allow-Origin", it) }
        ex.sendResponseHeaders(code, if (body.isEmpty()) -1 else body.size.toLong())
        if (body.isNotEmpty()) ex.responseBody.use { it.write(body) } else ex.close()
    }

    private fun <T> plain(ex: HttpExchange, ser: KSerializer<T>, v: T) =
        reply(ex, 200, json.encodeToString(ser, v).toByteArray(), "application/json")

    /** Strony internetowe nie mogą udawać rozszerzenia: przyjmujemy tylko moz-extension:// (albo brak Origin, np. testy). */
    private fun allowedOrigin(ex: HttpExchange): Boolean {
        val o = ex.requestHeaders.getFirst("Origin") ?: return true
        return o.startsWith("moz-extension://") || o.startsWith("chrome-extension://")
    }

    fun route(ex: HttpExchange) {
        if (!ex.remoteAddress.address.isLoopbackAddress || !localHost(ex) || !allowedOrigin(ex)) return reply(ex, 403, ByteArray(0))
        if (ex.requestMethod == "OPTIONS") {
            ex.responseHeaders.add("Access-Control-Allow-Headers", "Content-Type, ${SyncProtocol.HEADER_DEVICE}")
            ex.responseHeaders.add("Access-Control-Allow-Methods", "GET, POST")
            return reply(ex, 204, ByteArray(0))
        }
        val path = ex.requestURI.path.removePrefix("/ext/")
        // Do 8 MB: zrzut karty z kodem QR na ekranie 4K.
        val body = ex.requestBody.use { it.readNBytes(8 shl 20) }
        when (path) {
            "hello" -> return reply(ex, 200, """{"app":"ostoja","name":${json.encodeToString(kotlinx.serialization.serializer<String>(), store.computerName)}}""".toByteArray(), "application/json")
            "pair" -> {
                val req = json.decodeFromString(ExtPairRequest.serializer(), String(body))
                val (pub, shared) = ecdh(SyncCrypto.unb64(req.pub))
                val key = SyncCrypto.hkdf(shared, "ostoja-ext-v1", "channel")
                _pairing.value = ExtPairing(req.id, req.name.take(40).ifBlank { "Firefox" }, key, pairCode(shared))
                onShow()
                return plain(ex, ExtPairResponse.serializer(), ExtPairResponse(SyncCrypto.b64(pub), store.computerName))
            }
            "pair-status" -> {
                val req = json.decodeFromString(ExtIdRequest.serializer(), String(body))
                val p = _pairing.value
                val state = when {
                    store.value.extensions.any { it.id == req.id } && (p == null || p.id != req.id || p.state == "ok") -> "ok"
                    p != null && p.id == req.id -> p.state
                    else -> "none"
                }
                if (p != null && p.id == req.id && p.state != "waiting") _pairing.value = null
                return plain(ex, ExtPairStatus.serializer(), ExtPairStatus(state))
            }
        }

        val device = ex.requestHeaders.getFirst(SyncProtocol.HEADER_DEVICE) ?: return reply(ex, 400, ByteArray(0))
        val client = store.value.extensions.firstOrNull { it.id == device } ?: return reply(ex, 410, ByteArray(0))
        val key = SyncCrypto.unb64(client.key)
        val env = runCatching { Wire.openRequest(key, path, device, body) }.getOrNull()
        if (env == null || !store.freshNonce(env.nonce, env.ts)) return reply(ex, 403, ByteArray(0))
        seen(client)

        fun <T> send(ser: KSerializer<T>, v: T) {
            val el: JsonElement = json.encodeToJsonElement(ser, v)
            reply(ex, 200, Wire.sealResponse(key, path, device, env.nonce, el))
        }

        val s = store.value
        val locked = store.locked
        when (path) {
            "state" -> send(ExtState.serializer(), ExtState(
                locked, s.phones.isNotEmpty(), store.computerName,
                s.phones.maxByOrNull { it.lastSeen }?.name.orEmpty(), s.prefs.accent, s.prefs.icons,
            ))
            "list" -> send(ExtList.serializer(), if (locked) ExtList(true) else ExtList(false, s.vault.logins.filter { it.isLogin }.map { it.toItem(s) }))
            "creds" -> {
                val id = json.decodeFromJsonElement(ExtIdRequest.serializer(), env.body).id
                confirm(id)
                val l = s.vault.login(id)
                send(ExtCreds.serializer(), when {
                    locked -> ExtCreds(false, "locked")
                    l == null -> ExtCreds(false, "missing")
                    else -> {
                        val otp = pl.wolter.ostoja.data.VaultOps.otpFor(s.vault, l)
                        if (otp != null && l.otpId == null) store.editVault { d -> pl.wolter.ostoja.data.VaultOps.linkOtp(d, l.id) }
                        ExtCreds(true, "", l.title, l.username, l.password, loginUrl(l), l.siteDomain(), otp != null)
                    }
                })
            }
            "otp" -> {
                val id = json.decodeFromJsonElement(ExtIdRequest.serializer(), env.body).id
                val otp = s.vault.login(id)?.let { pl.wolter.ostoja.data.VaultOps.otpFor(s.vault, it) }
                send(ExtCode.serializer(), if (locked || otp == null) ExtCode(false) else {
                    val now = System.currentTimeMillis()
                    // Kod, który zaraz wygaśnie, mógłby nie zdążyć - wtedy bierzemy następny.
                    val rem = (Otp.remainingMs(otp.period, now) / 1000).toInt()
                    if (!otp.isHotp && rem < 3) ExtCode(true, Otp.code(otp, now, 1), otp.period + rem) else ExtCode(true, Otp.code(otp, now), rem)
                })
            }
            "wait" -> {
                val cmds = if (locked) { Thread.sleep(5000); emptyList() } else takeCommands(25_000)
                send(ExtCommands.serializer(), ExtCommands(store.locked, cmds))
            }
            "check" -> {
                // Po zalogowaniu na stronie: czy to nowe konto, zmienione hasło, czy nic nowego.
                val req = json.decodeFromJsonElement(ExtCheckRequest.serializer(), env.body)
                val match = findLogin(s, req.url, req.username)
                send(ExtCheck.serializer(), when {
                    locked -> ExtCheck("locked")
                    match == null -> ExtCheck("new", title = titleFor(req.url))
                    match.password == req.password -> ExtCheck("same", match.id, match.title)
                    else -> ExtCheck("changed", match.id, match.title)
                })
            }
            "save" -> {
                val req = json.decodeFromJsonElement(ExtSaveRequest.serializer(), env.body)
                if (locked) return send(ExtSaveResult.serializer(), ExtSaveResult(false, error = "locked"))
                val old = req.id?.let { s.vault.login(it) }
                val l = old?.copy(
                    password = req.password,
                    username = req.username.ifBlank { old.username },
                    url = old.url.ifBlank { req.url },
                ) ?: Login(
                    title = req.title.trim().ifBlank { titleFor(req.url) },
                    username = req.username.trim(), password = req.password, url = req.url.trim(), note = req.note,
                )
                store.saveLogin(l)
                store.toast(if (old == null) "Dodano z ${client.name} · ${l.title}" else "Zaktualizowano hasło z ${client.name} · ${l.title}")
                send(ExtSaveResult.serializer(), ExtSaveResult(true, l.id, l.title))
            }
            "login-url" -> {
                // Rozszerzenie znalazło stronę logowania (wpis miał samą domenę) - następnym razem otworzymy ją od razu.
                val req = json.decodeFromJsonElement(ExtLoginUrl.serializer(), env.body)
                if (locked) return send(ExtSaveResult.serializer(), ExtSaveResult(false, error = "locked"))
                val old = s.vault.login(req.id)?.takeIf { it.isLogin }
                    ?: return send(ExtSaveResult.serializer(), ExtSaveResult(false, error = "missing"))
                val u = req.url.trim().substringBefore('#').substringBefore('?')
                val path = runCatching { java.net.URI(u).path.orEmpty() }.getOrDefault("")
                val oldPath = runCatching { java.net.URI(loginUrl(old)).path.orEmpty() }.getOrDefault("/x")
                val ok = (u.startsWith("https://") || u.startsWith("http://")) && path.trim('/').isNotEmpty() && path.length <= 80 &&
                    oldPath.trim('/').isEmpty() && pl.wolter.ostoja.data.registrableDomain(hostOf(u)) == old.siteDomain()
                if (ok) {
                    store.saveLogin(old.copy(url = u))
                    store.toast("Zapamiętano stronę logowania ${old.title} · ${hostOf(u)}$path")
                }
                send(ExtSaveResult.serializer(), ExtSaveResult(ok, old.id, old.title, if (ok) "" else "kept"))
            }
            "otp-add" -> {
                val req = json.decodeFromJsonElement(ExtQrRequest.serializer(), env.body)
                if (locked) return send(ExtOtpAdded.serializer(), ExtOtpAdded(false, error = "locked"))
                val img = runCatching { javax.imageio.ImageIO.read(java.io.ByteArrayInputStream(java.util.Base64.getDecoder().decode(req.image))) }.getOrNull()
                val text = img?.let(Qr::decode) ?: return send(ExtOtpAdded.serializer(), ExtOtpAdded(false, error = "noqr"))
                val list = runCatching { OtpUri.parse(text) }.getOrNull() ?: return send(ExtOtpAdded.serializer(), ExtOtpAdded(false, error = "nototp"))
                val added = store.addOtps(list)
                if (added.isEmpty()) return send(ExtOtpAdded.serializer(), ExtOtpAdded(false, list.first().issuer, "exists"))
                store.toast("Dodano kod 2FA z ${client.name} · ${added.first().issuer}")
                send(ExtOtpAdded.serializer(), ExtOtpAdded(true, added.first().issuer))
            }
            "unlock" -> {
                if (locked && s.phones.isNotEmpty() && store.unlock.value !is Unlock.Waiting) store.startUnlock()
                onShow()
                send(ExtUnlock.serializer(), unlockState())
            }
            "unlock-status" -> send(ExtUnlock.serializer(), unlockState())
            else -> reply(ex, 404, ByteArray(0))
        }
    }

    private fun unlockState(): ExtUnlock = when (val u = store.unlock.value) {
        is Unlock.Waiting -> ExtUnlock("waiting", u.code, u.request.expires)
        is Unlock.Done -> ExtUnlock(if (u.ok) "ok" else "no")
        Unlock.Idle -> ExtUnlock(if (store.locked) "locked" else "ok")
    }

    /** Wpis dla tej strony i tego loginu (bez loginu - jedyny wpis na tej domenie). */
    private fun findLogin(s: Persisted, url: String, username: String): Login? {
        val host = hostOf(url)
        if (host.isBlank()) return null
        val domain = pl.wolter.ostoja.data.registrableDomain(host)
        val onSite = s.vault.logins.filter { it.isLogin && (it.siteDomain() == domain || host.endsWith("." + it.siteDomain())) }
        return onSite.firstOrNull { it.username.equals(username.trim(), ignoreCase = true) }
            ?: if (username.isBlank()) onSite.singleOrNull() else null
    }

    /** „allegro.pl” → „Allegro”, „accounts.google.com” → „Google”. */
    private fun titleFor(url: String): String {
        val d = pl.wolter.ostoja.data.registrableDomain(hostOf(url))
        return d.substringBefore('.').replaceFirstChar { it.uppercase() }.ifBlank { "Nowy wpis" }
    }

    private fun seen(c: ExtClient) {
        val now = System.currentTimeMillis()
        if (now - c.lastSeen < 20_000) return
        store.mutate { s -> s.copy(extensions = s.extensions.map { if (it.id == c.id) it.copy(lastSeen = now) else it }) }
    }

    /** Domena, na której wolno wpisać hasło (z adresu strony; bez adresu - ze znanej marki). */
    private fun Login.siteDomain() = hostOf(url).let { if (it.isNotBlank()) pl.wolter.ostoja.data.registrableDomain(it) else Brand.domain(title, url) }
    private fun Login.toItem(s: Persisted) = ExtItem(id, title, username, loginUrl(this), siteDomain(), pl.wolter.ostoja.data.VaultOps.otpFor(s.vault, this) != null)

    companion object {
        private const val TTL = 60_000L
        private const val RESEND_MS = 5_000L
        /** Tyle czeka polecenie na odbiór, zanim uznamy, że Firefox jest zamknięty. */
        internal var CONFIRM_MS = 2_500L

        /** Adres do otwarcia: zapisana strona logowania albo domena serwisu. */
        fun loginUrl(l: Login): String {
            val u = l.url.trim()
            if (u.startsWith("http://") || u.startsWith("https://")) return u
            if (hostOf(u).contains('.')) return "https://$u"
            val d = Brand.domain(l.title, l.url)
            return if (d.isNotBlank()) "https://$d" else ""
        }
    }
}
