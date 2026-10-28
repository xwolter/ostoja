package pl.wolter.ostoja.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

/**
 * Ikony serwisów. Pobierane raz (Google S2, sama domena - bez loginów i haseł) i trzymane w plikach,
 * więc widżety i kolejne uruchomienia działają bez sieci.
 */
object Favicons {
    private val mem = ConcurrentHashMap<String, Bitmap>()
    private val missing = ConcurrentHashMap.newKeySet<String>()
    private val lock = Mutex()
    private const val RETRY_MS = 7L * 24 * 3600 * 1000

    private fun dir(ctx: Context) = File(ctx.filesDir, "icons").apply { mkdirs() }
    private fun safe(domain: String) = domain.lowercase().filter { it.isLetterOrDigit() || it == '.' || it == '-' }

    /** Tylko z pamięci/dysku - do widżetów i pierwszej klatki. */
    fun cached(ctx: Context, domain: String): Bitmap? {
        if (domain.isBlank()) return null
        val d = safe(domain)
        mem[d]?.let { return it }
        val f = File(dir(ctx), "$d.png")
        if (!f.exists()) return null
        return BitmapFactory.decodeFile(f.absolutePath)?.also { mem[d] = it }
    }

    suspend fun load(ctx: Context, domain: String): Bitmap? {
        if (domain.isBlank()) return null
        val d = safe(domain)
        cached(ctx, d)?.let { return it }
        if (d in missing) return null
        return withContext(Dispatchers.IO) {
            lock.withLock {
                cached(ctx, d)?.let { return@withLock it }
                val miss = File(dir(ctx), "$d.none")
                if (miss.exists() && System.currentTimeMillis() - miss.lastModified() < RETRY_MS) { missing += d; return@withLock null }
                val bytes = runCatching {
                    val c = URL("https://www.google.com/s2/favicons?domain=$d&sz=128").openConnection() as HttpURLConnection
                    c.connectTimeout = 4000
                    c.readTimeout = 4000
                    c.instanceFollowRedirects = true
                    try { if (c.responseCode == 200) c.inputStream.use { it.readBytes() } else null } finally { c.disconnect() }
                }.getOrNull()
                val bmp = bytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }?.takeIf { it.width >= 24 }
                if (bmp == null) {
                    // brak sieci nie oznacza braku ikony - znacznik tylko przy odpowiedzi serwera
                    if (bytes != null) miss.writeText("")
                    missing += d
                    null
                } else {
                    runCatching { File(dir(ctx), "$d.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) } }
                    mem[d] = bmp
                    bmp
                }
            }
        }
    }
}

/**
 * Sprawdzanie haseł w bazie Have I Been Pwned metodą k-anonimowości: do serwera trafia tylko
 * 5 pierwszych znaków skrótu SHA-1, a porównanie reszty odbywa się na telefonie.
 */
object LeakCheck {
    private fun sha1(s: String) = MessageDigest.getInstance("SHA-1").digest(s.toByteArray())
        .joinToString("") { "%02X".format(it) }

    /** null - nie udało się sprawdzić (brak sieci). */
    private fun pwned(password: String): Boolean? = runCatching {
        val h = sha1(password)
        val c = URL("https://api.pwnedpasswords.com/range/${h.take(5)}").openConnection() as HttpURLConnection
        c.connectTimeout = 6000
        c.readTimeout = 6000
        c.setRequestProperty("Add-Padding", "true")
        c.setRequestProperty("User-Agent", "Ostoja-Android")
        try {
            if (c.responseCode != 200) return@runCatching null
            val suffix = h.drop(5)
            c.inputStream.bufferedReader().useLines { lines ->
                lines.any { line ->
                    val parts = line.split(':')
                    parts.size == 2 && parts[0].equals(suffix, true) && (parts[1].trim().toIntOrNull() ?: 0) > 0
                }
            }
        } finally { c.disconnect() }
    }.getOrNull()

    /** Sprawdza hasła, których jeszcze nie sprawdzano (albo starsze niż `maxAgeMs`). Zwraca nowo wykryte wycieki. */
    suspend fun run(vault: Vault, maxAgeMs: Long = 7L * 24 * 3600 * 1000): List<Login> = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val todo = vault.value.logins.filter {
            it.password.isNotEmpty() && (it.type == Login.LOGIN || it.type == Login.WIFI) && now - it.breachChecked > maxAgeMs
        }
        if (todo.isEmpty()) return@withContext emptyList()
        // jedno zapytanie na unikalne hasło
        val results = todo.map { it.password }.distinct().associateWith { pwned(it) }
        val fresh = mutableListOf<Login>()
        vault.update { d ->
            d.copy(logins = d.logins.map { l ->
                val r = if (todo.any { it.id == l.id }) results[l.password] else null
                if (r == null) l else {
                    if (r && !l.breached) fresh += l
                    l.copy(breached = r, breachChecked = now)
                }
            })
        }
        fresh
    }
}
