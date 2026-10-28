package pl.wolter.ostoja.desktop

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import pl.wolter.ostoja.sync.AnswerRequest
import pl.wolter.ostoja.sync.AnswerResponse
import pl.wolter.ostoja.sync.Envelope
import pl.wolter.ostoja.sync.OpenRequest
import pl.wolter.ostoja.sync.PairRequest
import pl.wolter.ostoja.sync.PairResponse
import pl.wolter.ostoja.sync.SyncCrypto
import pl.wolter.ostoja.sync.SyncProtocol
import pl.wolter.ostoja.sync.SyncRequest
import pl.wolter.ostoja.sync.SyncResponse
import pl.wolter.ostoja.sync.WaitRequest
import pl.wolter.ostoja.sync.WaitResponse
import pl.wolter.ostoja.sync.Wire
import java.io.File
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.util.concurrent.Executors

/**
 * Zapytanie do 127.0.0.1 z nagłówkiem Host innym niż adres lokalny to strona internetowa po „DNS rebinding”
 * (zła.pl nagle wskazuje na 127.0.0.1) - takie odrzucamy.
 */
fun localHost(ex: HttpExchange): Boolean {
    val h = ex.requestHeaders.getFirst("Host")?.lowercase()?.substringBeforeLast(':')?.trim() ?: return false
    return h == "127.0.0.1" || h == "localhost" || h == "[::1]"
}

/**
 * /local/… woła tylko druga kopia aplikacji (zwykłe zapytanie Javy). Przeglądarka zawsze dokłada Origin albo
 * Sec-Fetch-Site - wtedy to strona internetowa próbuje np. zamknąć Ostoję formularzem na 127.0.0.1.
 */
private fun fromApp(ex: HttpExchange) = ex.remoteAddress.address.isLoopbackAddress && localHost(ex) &&
    ex.requestHeaders.getFirst("Origin") == null && ex.requestHeaders.getFirst("Sec-Fetch-Site") == null

/** Klucz główny pliku danych, chroniony przez Windows DPAPI (tylko to konto użytkownika go odczyta). */
object MasterKey {
    fun load(dir: File): ByteArray {
        val dp = File(dir, "key.dpapi")
        val plain = File(dir, "key.bin")
        if (dp.exists()) {
            // Nie wolno tu wygenerować nowego klucza: stary sejf stałby się nie do odczytania i zostałby odłożony jako „uszkodzony”.
            repeat(3) { dpapi(false, dp.readText().trim())?.let { return SyncCrypto.unb64(it) }; Thread.sleep(500) }
            error("Nie udało się odczytać klucza sejfu (Windows DPAPI). Dane w ${dir.path} zostały nietknięte.")
        }
        if (plain.exists()) return plain.readBytes()
        val key = SyncCrypto.random(32)
        val wrapped = if (isWindows) dpapi(true, SyncCrypto.b64(key)) else null
        if (wrapped != null) dp.writeText(wrapped)
        else plain.writeBytes(key).also { plain.setReadable(false, false); plain.setReadable(true, true) }
        return key
    }

    private val isWindows = System.getProperty("os.name").lowercase().contains("win")

    /** ProtectedData z .NET przez PowerShell - dane idą przez stdin, nie przez linię poleceń. */
    private fun dpapi(protect: Boolean, b64: String): String? = runCatching {
        val op = if (protect) "Protect" else "Unprotect"
        val script = "Add-Type -AssemblyName System.Security; \$b=[Convert]::FromBase64String([Console]::In.ReadLine()); " +
            "[Convert]::ToBase64String([Security.Cryptography.ProtectedData]::$op(\$b,\$null,'CurrentUser'))"
        val p = ProcessBuilder("powershell", "-NoProfile", "-NonInteractive", "-Command", script).redirectErrorStream(false).start()
        p.outputStream.bufferedWriter().use { it.write(b64); it.newLine() }
        val out = p.inputStream.bufferedReader().readText().trim()
        if (p.waitFor() == 0 && out.isNotEmpty()) out else null
    }.getOrNull()
}

object Net {
    private val virtual = listOf("virtual", "vmware", "vbox", "hyper-v", "vethernet", "wsl", "docker", "loopback", "bluetooth", "tap", "tun")

    /** Adresy IPv4 w sieci lokalnej (Wi‑Fi / Ethernet) - trafiają do kodu QR. */
    fun localAddresses(): List<String> = runCatching {
        NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback && !it.isVirtual && virtual.none { v -> it.displayName.lowercase().contains(v) } }
            .sortedBy { if (it.displayName.lowercase().let { n -> "wi-fi" in n || "wireless" in n || "wlan" in n }) 0 else 1 }
            .flatMap { ni -> ni.inetAddresses.toList().filterIsInstance<Inet4Address>().filter { it.isSiteLocalAddress } }
            .map { it.hostAddress }
            .distinct()
    }.getOrDefault(emptyList())
}

/**
 * Serwer w sieci lokalnej dla telefonu. Każde zapytanie poza /p/hello jest zaszyfrowane kluczem kanału
 * konkretnego telefonu; nieznane urządzenie dostaje 410, zła kryptografia - 403.
 */
class SyncServer(private val store: Store, private val ext: ExtBridge, private val onShow: () -> Unit, private val onQuit: () -> Unit = {}) {
    private val json = SyncProtocol.json
    private lateinit var http: HttpServer
    private var udp: DatagramSocket? = null

    /** false = port zajęty, zapewne działa już inna kopia aplikacji. */
    fun start(): Boolean {
        http = runCatching { HttpServer.create(InetSocketAddress(SyncProtocol.PORT), 32) }.getOrNull() ?: return false
        http.executor = Executors.newCachedThreadPool { r -> Thread(r, "ostoja-http").apply { isDaemon = true } }
        http.createContext("/p/") { ex -> runCatching { route(ex) }.onFailure { runCatching { reply(ex, 500, ByteArray(0)) } } }
        http.createContext("/ext/") { ex -> runCatching { ext.route(ex) }.onFailure { runCatching { reply(ex, 500, ByteArray(0)) } } }
        http.createContext("/local/show") { ex ->
            if (fromApp(ex)) onShow()
            reply(ex, 204, ByteArray(0))
        }
        // Wersja działającej kopii - nowsza wersja po aktualizacji prosi starszą o zamknięcie (/local/quit).
        http.createContext("/local/version") { ex ->
            if (!fromApp(ex)) return@createContext reply(ex, 403, ByteArray(0))
            reply(ex, 200, APP_VERSION.toByteArray(), "text/plain")
        }
        http.createContext("/local/quit") { ex ->
            if (!fromApp(ex) || ex.requestMethod != "POST") return@createContext reply(ex, 403, ByteArray(0))
            reply(ex, 204, ByteArray(0))
            Thread { Thread.sleep(200); onQuit() }.start()
        }
        http.start()
        startDiscovery()
        return true
    }

    fun stop() {
        runCatching { http.stop(0) }
        runCatching { udp?.close() }
    }

    /** Telefon w nowej sieci (zmiana adresu przez DHCP) pyta rozgłoszeniem „gdzie jest komputer X?”. */
    private fun startDiscovery() = Thread({
        runCatching {
            val s = DatagramSocket(SyncProtocol.DISCOVERY_PORT).also { udp = it }
            val buf = ByteArray(256)
            while (!s.isClosed) {
                val pkt = DatagramPacket(buf, buf.size)
                s.receive(pkt)
                val msg = String(pkt.data, 0, pkt.length)
                if (msg == "OSTOJA?" + store.value.computerId) {
                    val ans = "OSTOJA!" + store.value.computerId + "|" + SyncProtocol.PORT
                    s.send(DatagramPacket(ans.toByteArray(), ans.length, pkt.address, pkt.port))
                }
            }
        }
    }, "ostoja-discovery").apply { isDaemon = true }.start()

    private fun reply(ex: HttpExchange, code: Int, body: ByteArray, type: String = "application/octet-stream") {
        ex.responseHeaders.add("Content-Type", type)
        ex.responseHeaders.add("Cache-Control", "no-store")
        ex.sendResponseHeaders(code, if (body.isEmpty()) -1 else body.size.toLong())
        if (body.isNotEmpty()) ex.responseBody.use { it.write(body) } else ex.close()
    }

    private fun route(ex: HttpExchange) {
        val path = ex.requestURI.path.removePrefix("/p/")
        if (path == "hello") {
            val body = buildJsonObject { put("app", "ostoja"); put("id", store.value.computerId); put("name", store.computerName) }
            return reply(ex, 200, body.toString().toByteArray(), "application/json")
        }
        if (ex.requestMethod != "POST") return reply(ex, 405, ByteArray(0))
        val device = ex.requestHeaders.getFirst(SyncProtocol.HEADER_DEVICE) ?: return reply(ex, 400, ByteArray(0))
        // Treść czytamy dopiero dla znanego telefonu albo trwającego parowania - obcy w sieci nie zajmie pamięci 8-megabajtowymi zapytaniami.
        if (path != "pair" && store.phone(device) == null) return reply(ex, 410, ByteArray(0))
        val blob = ex.requestBody.use { it.readNBytes(8 * 1024 * 1024) }

        if (path == "pair") {
            val ticket = ex.requestHeaders.getFirst(SyncProtocol.HEADER_PAIR) ?: return reply(ex, 400, ByteArray(0))
            val secret = store.pairingSecret(ticket) ?: return reply(ex, 409, ByteArray(0))
            val key = SyncCrypto.channelKey(secret)
            val env = open(key, path, device, blob) ?: return reply(ex, 403, ByteArray(0))
            val req = json.decodeFromJsonElement(PairRequest.serializer(), env.body)
            store.completePairing(req.phoneId.ifBlank { device }, req.phoneName, key, req.vault, req.prefs)
            val resp = PairResponse(store.value.computerId, store.computerName, store.value.vault, store.rev)
            return send(ex, key, path, device, env.nonce, PairResponse.serializer(), resp)
        }

        val phone = store.phone(device) ?: return reply(ex, 410, ByteArray(0))
        val key = SyncCrypto.unb64(phone.key)
        val env = open(key, path, device, blob) ?: return reply(ex, 403, ByteArray(0))
        store.seen(phone)
        when (path) {
            "sync" -> {
                val req = json.decodeFromJsonElement(SyncRequest.serializer(), env.body)
                val merged = store.syncFromPhone(phone, req.phoneName, req.vault, req.prefs)
                send(ex, key, path, device, env.nonce, SyncResponse.serializer(),
                    SyncResponse(merged, store.rev, store.computerName, store.pendingRequest()))
            }
            "wait" -> {
                val req = json.decodeFromJsonElement(WaitRequest.serializer(), env.body)
                val rev = store.awaitChange(req.rev, 45_000)
                send(ex, key, path, device, env.nonce, WaitResponse.serializer(), WaitResponse(rev, store.pendingRequest()))
            }
            "answer" -> {
                val req = json.decodeFromJsonElement(AnswerRequest.serializer(), env.body)
                val (result, until) = store.answer(phone, req.id, req.approve, req.picked)
                send(ex, key, path, device, env.nonce, AnswerResponse.serializer(), AnswerResponse(result, until))
            }
            "open" -> {
                // „Zaloguj na komputerze” z telefonu: Firefox z rozszerzeniem otwiera stronę i loguje.
                val req = json.decodeFromJsonElement(OpenRequest.serializer(), env.body)
                val result = when {
                    store.locked -> "locked"
                    store.value.vault.login(req.loginId) == null -> "missing"
                    !ext.queueLogin(req.loginId, phone.name) -> "noext"
                    else -> "ok"
                }
                send(ex, key, path, device, env.nonce, AnswerResponse.serializer(), AnswerResponse(result))
            }
            "unpair" -> {
                // Najpierw odpowiedź (kluczem, który zaraz zniknie), potem usunięcie.
                send(ex, key, path, device, env.nonce, AnswerResponse.serializer(), AnswerResponse("ok"))
                store.unpair(phone.id)
                store.toast("Telefon ${phone.name} został odłączony")
            }
            else -> reply(ex, 404, ByteArray(0))
        }
    }

    private fun open(key: ByteArray, path: String, device: String, blob: ByteArray): Envelope? {
        val env = runCatching { Wire.openRequest(key, path, device, blob) }.getOrNull() ?: return null
        return if (store.freshNonce(env.nonce, env.ts)) env else null
    }

    private fun <T> send(ex: HttpExchange, key: ByteArray, path: String, device: String, nonce: String, ser: KSerializer<T>, value: T) {
        val body: JsonElement = json.encodeToJsonElement(ser, value)
        reply(ex, 200, Wire.sealResponse(key, path, device, nonce, body))
    }
}
