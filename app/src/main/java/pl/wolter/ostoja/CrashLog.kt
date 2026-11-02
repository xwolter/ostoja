package pl.wolter.ostoja

import android.content.Context
import android.os.Build
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Dziennik błędów na telefonie. Zamknięcie aplikacji (nieobsłużony wyjątek) i błędy złapane w usługach działających
 * w tle (autouzupełnianie, widżety, powiadomienia) trafiają do pliku. Przy następnym otwarciu Ostoja pokazuje,
 * co się stało, i pozwala skopiować szczegóły. Nic nie wychodzi z telefonu samo.
 */
object CrashLog {
    private const val FILE = "crash.log"
    private const val MAX = 24_000
    private lateinit var ctx: Context

    fun install(app: Context) {
        ctx = app.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            runCatching { write("ZAMKNIĘCIE", t.name, e) }
            previous?.uncaughtException(t, e)
        }
    }

    /** Błąd złapany po cichu (aplikacja działa dalej), ale wart zapisania. */
    fun record(where: String, e: Throwable) {
        if (!::ctx.isInitialized) return
        runCatching { write("BŁĄD · $where", Thread.currentThread().name, e) }
    }

    /** Uruchamia [block]; wyjątek zapisuje zamiast zamykać aplikację. */
    inline fun <T> guard(where: String, fallback: T, block: () -> T): T =
        try { block() } catch (e: Throwable) { record(where, e); fallback }

    private fun write(kind: String, thread: String, e: Throwable) {
        val f = File(ctx.filesDir, FILE)
        val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT).format(Date())
        val entry = buildString {
            append("=== ").append(kind).append(" · ").append(stamp).append('\n')
            append("Ostoja ").append(BuildConfig.VERSION_NAME).append(" (").append(BuildConfig.VERSION_CODE).append(") · ")
            append(Build.MANUFACTURER).append(' ').append(Build.MODEL).append(" · Android ").append(Build.VERSION.RELEASE)
            append(" · wątek ").append(thread).append('\n')
            // Błędy parsowania potrafią cytować fragment danych - wycinamy go, żeby w dzienniku nie było treści sejfu.
            append(e.stackTraceToString().replace(Regex("JSON input: .*"), "JSON input: (ukryte)").take(6000)).append('\n')
        }
        val old = if (f.exists()) f.readText() else ""
        f.writeText((entry + old).take(MAX))
        ctx.getSharedPreferences("crash", Context.MODE_PRIVATE).edit().putLong("last", System.currentTimeMillis()).commit()
    }

    fun copy(ctx: Context) {
        val cm = ctx.getSystemService(android.content.ClipboardManager::class.java) ?: return
        cm.setPrimaryClip(android.content.ClipData.newPlainText("Dziennik błędów Ostoi", text().ifBlank { "Brak zapisanych błędów" }))
    }

    fun text(): String = runCatching { File(ctx.filesDir, FILE).readText() }.getOrDefault("")

    /** Czy od ostatniego obejrzenia doszło do zamknięcia aplikacji (pokazujemy komunikat przy otwarciu). */
    fun unseenCrash(): Boolean {
        val sp = ctx.getSharedPreferences("crash", Context.MODE_PRIVATE)
        return sp.getLong("last", 0) > sp.getLong("seen", 0) && text().startsWith("=== ZAMKNIĘCIE")
    }

    fun markSeen() {
        ctx.getSharedPreferences("crash", Context.MODE_PRIVATE).edit().putLong("seen", System.currentTimeMillis()).apply()
    }

    fun clear() {
        runCatching { File(ctx.filesDir, FILE).delete() }
        markSeen()
    }
}
