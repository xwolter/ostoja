package pl.wolter.ostoja.sync

import android.content.Context
import android.os.Build
import android.provider.Settings
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import pl.wolter.ostoja.app
import pl.wolter.ostoja.data.KeystoreCipher
import pl.wolter.ostoja.data.newId
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

/** Sparowany komputer. Klucz kanału jest zaszyfrowany kluczem z Android Keystore. */
@Serializable
data class Computer(
    val id: String,
    val name: String,
    val key: String,
    val hosts: List<String>,
    val port: Int,
    val pairedAt: Long,
    val lastSync: Long = 0,
)

/** Prośba „Czy to Ty logujesz się na komputerze?” czekająca na odpowiedź. */
data class Incoming(val computerId: String, val request: LoginRequest)

enum class Link { OFFLINE, ONLINE, SYNCING }

/**
 * Synchronizacja z aplikacją Ostoja na Windows w sieci lokalnej. Telefon zawsze inicjuje połączenie:
 * wysyła cały sejf, komputer scala go ze swoim i odsyła wynik. Zmiany z komputera i prośby o logowanie
 * przychodzą przez długie zapytanie („/p/wait”), które komputer trzyma otwarte do ~45 s.
 */
object Sync {
    private const val TAG = "OstojaSync"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var appCtx: Context
    private val json = SyncProtocol.json

    private val _computers = MutableStateFlow<List<Computer>>(emptyList())
    val computers: StateFlow<List<Computer>> = _computers.asStateFlow()
    val links = MutableStateFlow<Map<String, Link>>(emptyMap())
    val request = MutableStateFlow<Incoming?>(null)
    val events = MutableSharedFlow<String>(extraBufferCapacity = 8)

    private val keys = ConcurrentHashMap<String, ByteArray>()
    private val revs = ConcurrentHashMap<String, Long>()
    private val syncLock = Mutex()
    private val handled = HashSet<String>()

    private val sp by lazy { appCtx.getSharedPreferences("sync", Context.MODE_PRIVATE) }
    val phoneId: String by lazy { sp.getString("phoneId", null) ?: newId().also { sp.edit().putString("phoneId", it).apply() } }
    val live = MutableStateFlow(true)

    fun init(ctx: Context) {
        appCtx = ctx.applicationContext
        _computers.value = runCatching {
            json.decodeFromString(ListSerializer(Computer.serializer()), sp.getString("computers", "[]")!!)
        }.getOrDefault(emptyList())
        live.value = sp.getBoolean("live", true)
        // Kolor akcentu i ikony z telefonu - komputer wygląda tak samo.
        scope.launch {
            ctx.app.settings.flow.map { prefs() }.distinctUntilChanged().collect { if (_computers.value.isNotEmpty()) syncAllSoon() }
        }
    }

    val paired get() = _computers.value.isNotEmpty()

    fun setLive(ctx: Context, on: Boolean) {
        live.value = on
        sp.edit().putBoolean("live", on).apply()
        if (on) LinkService.start(ctx) else LinkService.stop(ctx)
    }

    private fun save(list: List<Computer>) {
        _computers.value = list
        sp.edit().putString("computers", json.encodeToString(ListSerializer(Computer.serializer()), list)).apply()
    }

    private fun update(id: String, fn: (Computer) -> Computer) = save(_computers.value.map { if (it.id == id) fn(it) else it })

    private fun key(c: Computer) = keys.getOrPut(c.id) { KeystoreCipher.decrypt(SyncCrypto.unb64(c.key)) }

    fun phoneName(): String = runCatching { Settings.Global.getString(appCtx.contentResolver, "device_name") }.getOrNull()
        ?.takeIf { it.isNotBlank() } ?: Build.MODEL

    private fun prefs(): SyncPrefs = appCtx.app.settings.value.let { SyncPrefs(it.accent, it.icons, it.groupDigits) }

    // --- transport ---

    private class Gone : IOException("Komputer nie zna już tego telefonu")

    private fun post(host: String, port: Int, path: String, headers: Map<String, String>, body: ByteArray, timeoutMs: Int): ByteArray {
        val c = URL("http://$host:$port/p/$path").openConnection() as HttpURLConnection
        try {
            c.requestMethod = "POST"
            c.connectTimeout = 2500
            c.readTimeout = timeoutMs
            c.doOutput = true
            c.setRequestProperty("Content-Type", "application/octet-stream")
            headers.forEach { (k, v) -> c.setRequestProperty(k, v) }
            c.outputStream.use { it.write(body) }
            return when (val code = c.responseCode) {
                200 -> c.inputStream.use { it.readBytes() }
                410 -> throw Gone()
                else -> throw IOException("HTTP $code")
            }
        } finally { c.disconnect() }
    }

    /** Szyfrowane zapytanie; przy braku połączenia szuka komputera rozgłoszeniem w sieci (zmiana adresu IP). */
    private fun <Q, R> call(c: Computer, path: String, qs: KSerializer<Q>, q: Q, rs: KSerializer<R>, timeoutMs: Int = 15_000): R {
        val key = key(c)
        val hosts = c.hosts.ifEmpty { listOf() }
        var last: Exception? = null
        for (attempt in 0..1) {
            val list = if (attempt == 0) hosts else listOfNotNull(discover(c.id))
            for (h in list) {
                val nonce = SyncCrypto.token()
                val blob = Wire.sealRequest(key, path, phoneId, json.encodeToJsonElement(qs, q), nonce)
                try {
                    val resp = post(h, c.port, path, mapOf(SyncProtocol.HEADER_DEVICE to phoneId), blob, timeoutMs)
                    if (h != hosts.firstOrNull()) update(c.id) { it.copy(hosts = listOf(h) + it.hosts.filter { x -> x != h }) }
                    return json.decodeFromJsonElement(rs, Wire.openResponse(key, path, phoneId, nonce, resp))
                } catch (e: Gone) {
                    throw e
                } catch (e: Exception) {
                    last = e
                }
            }
        }
        throw last ?: IOException("Komputer poza zasięgiem")
    }

    private fun discover(computerId: String): String? = runCatching {
        DatagramSocket().use { s ->
            s.broadcast = true
            s.soTimeout = 1500
            val msg = ("OSTOJA?$computerId").toByteArray()
            s.send(DatagramPacket(msg, msg.size, InetAddress.getByName("255.255.255.255"), SyncProtocol.DISCOVERY_PORT))
            val buf = ByteArray(256)
            val pkt = DatagramPacket(buf, buf.size)
            s.receive(pkt)
            val ans = String(pkt.data, 0, pkt.length)
            if (ans.startsWith("OSTOJA!$computerId|")) pkt.address.hostAddress else null
        }
    }.getOrNull()

    // --- operacje ---

    /** Parowanie po zeskanowaniu kodu QR z komputera. Zwraca nazwę komputera. */
    suspend fun pair(ctx: Context, uri: String): String = withContext(Dispatchers.IO) {
        val info = PairInfo.parse(uri)
        val key = SyncCrypto.channelKey(info.secret)
        val req = PairRequest(phoneId, phoneName(), ctx.app.vault.value, prefs())
        var last: Exception? = null
        for (h in info.hosts) {
            val nonce = SyncCrypto.token()
            val blob = Wire.sealRequest(key, "pair", phoneId, json.encodeToJsonElement(PairRequest.serializer(), req), nonce)
            try {
                val raw = post(h, info.port, "pair", mapOf(SyncProtocol.HEADER_DEVICE to phoneId, SyncProtocol.HEADER_PAIR to info.ticket), blob, 20_000)
                val resp = json.decodeFromJsonElement(PairResponse.serializer(), Wire.openResponse(key, "pair", phoneId, nonce, raw))
                ctx.app.vault.applyRemote(resp.vault)
                val c = Computer(
                    id = resp.computerId, name = resp.computerName,
                    key = SyncCrypto.b64(KeystoreCipher.encrypt(key)),
                    hosts = listOf(h) + info.hosts.filter { it != h }, port = info.port,
                    pairedAt = System.currentTimeMillis(), lastSync = System.currentTimeMillis(),
                )
                keys[c.id] = key
                revs[c.id] = resp.rev
                save(_computers.value.filterNot { it.id == c.id } + c)
                links.value = links.value + (c.id to Link.ONLINE)
                if (live.value) withContext(Dispatchers.Main) { LinkService.start(ctx) }
                ensureLoop()
                return@withContext resp.computerName
            } catch (e: Exception) {
                last = e
                Log.w(TAG, "pair via $h failed", e)
            }
        }
        throw IOException(
            if (last is IOException && last.message?.startsWith("HTTP 409") == true) "Kod parowania wygasł - odśwież go na komputerze"
            else "Nie udało się połączyć. Telefon i komputer muszą być w tej samej sieci Wi‑Fi.",
        )
    }

    suspend fun syncNow(c: Computer): Boolean = syncLock.withLock {
        withContext(Dispatchers.IO) {
            setLink(c.id, Link.SYNCING)
            try {
                val resp = call(c, "sync", SyncRequest.serializer(), SyncRequest(phoneName(), appCtx.app.vault.value, prefs()), SyncResponse.serializer())
                appCtx.app.vault.applyRemote(resp.vault)
                revs[c.id] = resp.rev
                update(c.id) { it.copy(name = resp.computerName, lastSync = System.currentTimeMillis()) }
                setLink(c.id, Link.ONLINE)
                offer(c, resp.request)
                true
            } catch (e: Gone) {
                forget(c, remote = true)
                false
            } catch (e: Exception) {
                Log.d(TAG, "sync failed: ${e.message}")
                setLink(c.id, Link.OFFLINE)
                false
            }
        }
    }

    private var pendingSync: Job? = null

    /** Lokalna zmiana sejfu - wysyłamy ją chwilę później (kilka szybkich zmian = jedno zapytanie). */
    fun localChanged() {
        if (!::appCtx.isInitialized || !paired) return
        syncAllSoon()
    }

    private fun syncAllSoon() {
        pendingSync?.cancel()
        pendingSync = scope.launch {
            delay(700)
            _computers.value.forEach { syncNow(it) }
        }
    }

    private fun setLink(id: String, l: Link) { links.value = links.value + (id to l) }

    /** Odpowiedź na prośbę z komputera. Zwraca ok | wrong | expired | rejected | offline. */
    suspend fun answer(inc: Incoming, approve: Boolean, picked: Int?): String = withContext(Dispatchers.IO) {
        val c = _computers.value.firstOrNull { it.id == inc.computerId } ?: return@withContext "expired"
        synchronized(handled) { handled += inc.request.id }
        val r = runCatching {
            call(c, "answer", AnswerRequest.serializer(), AnswerRequest(inc.request.id, approve, picked), AnswerResponse.serializer()).result
        }.getOrDefault("offline")
        if (request.value?.request?.id == inc.request.id) request.value = null
        RequestNotification.cancel(appCtx)
        r
    }

    /** „Zaloguj na komputerze”. Zwraca ok | locked | noext | missing | offline. */
    suspend fun openOnComputer(loginId: String): String = withContext(Dispatchers.IO) {
        val c = _computers.value.firstOrNull() ?: return@withContext "offline"
        // Najpierw synchronizacja - świeży wpis musi już być na komputerze.
        syncNow(c)
        runCatching { call(c, "open", OpenRequest.serializer(), OpenRequest(loginId), AnswerResponse.serializer()).result }.getOrDefault("offline")
    }

    fun dismiss(inc: Incoming) {
        synchronized(handled) { handled += inc.request.id }
        if (request.value?.request?.id == inc.request.id) request.value = null
        RequestNotification.cancel(appCtx)
    }

    suspend fun unpair(c: Computer) = withContext(Dispatchers.IO) {
        runCatching { call(c, "unpair", Empty.serializer(), Empty(), AnswerResponse.serializer(), 5000) }
        forget(c, remote = false)
    }

    private fun forget(c: Computer, remote: Boolean) {
        save(_computers.value.filterNot { it.id == c.id })
        keys.remove(c.id)
        links.value = links.value - c.id
        if (remote) events.tryEmit("${c.name} odłączył ten telefon")
        if (!paired) LinkService.stop(appCtx)
    }

    private fun offer(c: Computer, r: LoginRequest?) {
        val cur = request.value
        if (r == null) {
            // Komputer anulował prośbę albo wygasła.
            if (cur != null && cur.computerId == c.id) { request.value = null; RequestNotification.cancel(appCtx) }
            return
        }
        if (System.currentTimeMillis() > r.expires || synchronized(handled) { r.id in handled }) return
        if (cur?.request?.id == r.id) return
        val inc = Incoming(c.id, r)
        request.value = inc
        RequestNotification.show(appCtx, inc)
    }

    // --- pętla nasłuchu ---

    private var loop: Job? = null
    private val holders = HashSet<String>()

    /** Pętla działa, gdy aplikacja jest na ekranie albo działa LinkService. */
    fun hold(who: String, on: Boolean) {
        synchronized(holders) { if (on) holders += who else holders -= who }
        ensureLoop()
    }

    private fun ensureLoop() = synchronized(holders) {
        val want = holders.isNotEmpty() && paired
        if (want && loop?.isActive != true) loop = scope.launch { runLoop() }
        if (!want) { loop?.cancel(); loop = null }
    }

    private suspend fun runLoop() {
        val jobs = HashMap<String, Job>()
        while (scope.isActive) {
            val list = _computers.value
            list.forEach { c -> if (jobs[c.id]?.isActive != true) jobs[c.id] = scope.launch { watch(c.id) } }
            (jobs.keys - list.map { it.id }.toSet()).forEach { jobs.remove(it)?.cancel() }
            if (list.isEmpty()) break
            delay(3000)
        }
    }

    private suspend fun watch(id: String) {
        var backoff = 2000L
        var first = true
        while (true) {
            val c = _computers.value.firstOrNull { it.id == id } ?: return
            if (first) { first = false; if (!syncNow(c)) { delay(backoff); backoff = minOf(backoff * 2, 60_000); first = true; continue } }
            try {
                val rev = revs[id] ?: 0
                val r = withContext(Dispatchers.IO) { call(c, "wait", WaitRequest.serializer(), WaitRequest(rev), WaitResponse.serializer(), 60_000) }
                setLink(id, Link.ONLINE)
                offer(c, r.request)
                if (r.rev != rev) { revs[id] = r.rev; syncNow(c) }
                backoff = 2000
            } catch (e: Gone) {
                forget(c, remote = true); return
            } catch (e: Exception) {
                setLink(id, Link.OFFLINE)
                delay(backoff)
                backoff = minOf(backoff * 2, 60_000)
                first = true
            }
        }
    }
}
