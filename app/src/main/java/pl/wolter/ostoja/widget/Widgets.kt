package pl.wolter.ostoja.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.os.SystemClock
import android.view.View
import android.widget.RemoteViews
import android.widget.Toast
import pl.wolter.ostoja.MainActivity
import pl.wolter.ostoja.R
import pl.wolter.ostoja.app
import pl.wolter.ostoja.data.Brand
import pl.wolter.ostoja.data.Favicons
import pl.wolter.ostoja.data.GenOptions
import pl.wolter.ostoja.data.Otp
import pl.wolter.ostoja.data.OtpAccount
import pl.wolter.ostoja.data.PasswordTools
import pl.wolter.ostoja.data.SettingsStore
import pl.wolter.ostoja.data.plural
import pl.wolter.ostoja.data.securityReport
import pl.wolter.ostoja.security.Clip

object Widgets {
    const val ACTION_COPY = "pl.wolter.ostoja.widget.COPY"
    const val ACTION_GEN = "pl.wolter.ostoja.widget.GEN"
    const val ACTION_TICK = "pl.wolter.ostoja.widget.TICK"
    private const val WINDOW_MS = 30_000L
    /** Co ile odświeżać pierścienie, gdy ekran jest włączony. */
    private const val RING_MS = 5_000L
    private var lastWindow = -1L

    fun prefs(ctx: Context) = ctx.getSharedPreferences("widgets", Context.MODE_PRIVATE)

    private fun ids(ctx: Context, cls: Class<*>): IntArray =
        AppWidgetManager.getInstance(ctx).getAppWidgetIds(ComponentName(ctx, cls))

    private fun accent(ctx: Context) = SettingsStore.ACCENTS.getOrElse(ctx.app.settings.value.accent) { SettingsStore.ACCENTS[0] }

    private const val WARN = 0xFFFF4D4F.toInt()

    fun updateAll(ctx: Context) {
        updateCodes(ctx)
        val m = AppWidgetManager.getInstance(ctx)
        ids(ctx, QuickWidget::class.java).forEach { m.updateAppWidget(it, quick(ctx)) }
        ids(ctx, SecurityWidget::class.java).forEach { m.updateAppWidget(it, security(ctx)) }
        ids(ctx, ScanWidget::class.java).forEach { m.updateAppWidget(it, scan(ctx)) }
    }

    /** Pełne odświeżenie widżetów z kodami (nowe kody, ikony) - na granicy okna 30 s. */
    fun updateCodes(ctx: Context) {
        val m = AppWidgetManager.getInstance(ctx)
        ids(ctx, CodeWidget::class.java).forEach { m.updateAppWidget(it, code(ctx, it)) }
        ids(ctx, CodesWidget::class.java).forEach { m.updateAppWidget(it, codes(ctx)) }
        lastWindow = System.currentTimeMillis() / WINDOW_MS
        scheduleTick(ctx)
    }

    /** Między granicami okna zmienia się tylko pierścień - częściowa aktualizacja bez bitmap. */
    fun tick(ctx: Context) {
        val now = System.currentTimeMillis()
        if (now / WINDOW_MS != lastWindow) { updateCodes(ctx); return }
        val m = AppWidgetManager.getInstance(ctx)
        val warn = Otp.remainingMs(30, now) <= 5_000
        ids(ctx, CodeWidget::class.java).forEach { id ->
            val a = account(ctx, id) ?: return@forEach
            if (a.isHotp) return@forEach
            val rv = RemoteViews(ctx.packageName, R.layout.widget_code)
            ring(ctx, rv, R.id.ring, Otp.remainingMs(a.period, now), a.period, Otp.remainingMs(a.period, now) <= 5_000)
            m.partiallyUpdateAppWidget(id, rv)
        }
        ids(ctx, CodesWidget::class.java).forEach { id ->
            val rv = RemoteViews(ctx.packageName, R.layout.widget_codes)
            ring(ctx, rv, R.id.ring, Otp.remainingMs(30, now), 30, warn)
            m.partiallyUpdateAppWidget(id, rv)
        }
        scheduleTick(ctx)
    }

    /**
     * Alarm RTC (bez wybudzania): przy wygaszonym ekranie system odkłada go do wybudzenia,
     * więc w kieszeni widżet nie zużywa baterii. Przy włączonym ekranie - co 5 s pierścień, co 30 s kody.
     */
    private fun scheduleTick(ctx: Context) {
        val am = ctx.getSystemService(AlarmManager::class.java) ?: return
        val pi = PendingIntent.getBroadcast(
            ctx, 7, Intent(ctx, ActionReceiver::class.java).setAction(ACTION_TICK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val needed = (ids(ctx, CodeWidget::class.java).isNotEmpty() || ids(ctx, CodesWidget::class.java).isNotEmpty()) &&
            ctx.app.vault.value.otps.any { !it.isHotp } && ctx.app.settings.value.widgetCodes
        if (!needed) { am.cancel(pi); return }
        val now = System.currentTimeMillis()
        val boundary = (now / WINDOW_MS + 1) * WINDOW_MS + 150
        val interactive = ctx.getSystemService(PowerManager::class.java)?.isInteractive ?: true
        val next = if (interactive) minOf(boundary, (now / RING_MS + 1) * RING_MS + 100) else boundary
        if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) am.setWindow(AlarmManager.RTC, next, 1000, pi)
        else am.setExact(AlarmManager.RTC, next, pi)
    }

    // --- pomocnicze ---

    fun open(ctx: Context, action: String, req: Int, otp: String? = null): PendingIntent {
        val i = Intent(ctx, MainActivity::class.java).setAction(action)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        if (otp != null) i.putExtra(MainActivity.EXTRA_OTP, otp).data = Uri.parse("ostoja://otp/$otp")
        return PendingIntent.getActivity(ctx, req, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    fun copy(ctx: Context, otpId: String): PendingIntent = PendingIntent.getBroadcast(
        ctx, 100, Intent(ctx, ActionReceiver::class.java).setAction(ACTION_COPY).setData(Uri.parse("ostoja://copy/$otpId")),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun sorted(list: List<OtpAccount>) =
        list.sortedWith(compareByDescending<OtpAccount> { it.favorite }.thenBy { it.title.lowercase() })

    private fun account(ctx: Context, widgetId: Int): OtpAccount? {
        val d = ctx.app.vault.value
        return d.otp(prefs(ctx).getString("code_$widgetId", null)) ?: sorted(d.otps).firstOrNull()
    }

    private fun ring(ctx: Context, rv: RemoteViews, id: Int, remaining: Long, period: Int, warn: Boolean) {
        rv.setProgressBar(id, 1000, (remaining * 1000 / (period * 1000L)).toInt(), false)
        if (Build.VERSION.SDK_INT >= 31) rv.setColorStateList(id, "setProgressTintList", ColorStateList.valueOf(if (warn) WARN else accent(ctx)))
    }

    private fun pretty(ctx: Context, code: String): String = if (ctx.app.settings.value.groupDigits) Otp.pretty(code) else code

    /** Ikona serwisu z pamięci podręcznej (zmniejszona, żeby nie obciążać IPC) albo litera w kolorze marki. */
    private fun logo(ctx: Context, rv: RemoteViews, bg: Int, icon: Int, letter: Int, name: String, sizePx: Int = 72) {
        val bmp = if (ctx.app.settings.value.icons) Favicons.cached(ctx, Brand.domain(name)) else null
        val brand = Brand.color(name).toInt()
        if (bmp != null) {
            rv.setViewVisibility(icon, View.VISIBLE)
            rv.setViewVisibility(letter, View.GONE)
            rv.setImageViewBitmap(icon, Bitmap.createScaledBitmap(bmp, sizePx, sizePx, true))
            rv.setInt(bg, "setColorFilter", ctx.getColor(R.color.w_logo))
        } else {
            rv.setViewVisibility(icon, View.GONE)
            rv.setViewVisibility(letter, View.VISIBLE)
            rv.setTextViewText(letter, Brand.initial(name))
            rv.setTextColor(letter, brand)
            rv.setInt(bg, "setColorFilter", (brand and 0x00FFFFFF) or 0x24000000)
        }
    }

    // --- duży kod 2×2 ---

    fun code(ctx: Context, widgetId: Int): RemoteViews {
        val rv = RemoteViews(ctx.packageName, R.layout.widget_code)
        val s = ctx.app.settings.value
        val acc = accent(ctx)
        val a = account(ctx, widgetId)
        rv.setTextColor(R.id.code, acc)
        if (a == null) {
            rv.setTextViewText(R.id.name, "Ostoja")
            rv.setTextViewText(R.id.code, "Dodaj konto")
            rv.setViewVisibility(R.id.ring, View.INVISIBLE)
            rv.setViewVisibility(R.id.timer, View.GONE)
            logo(ctx, rv, R.id.big_bg, R.id.big_icon, R.id.big_letter, "+")
            rv.setOnClickPendingIntent(R.id.root, open(ctx, MainActivity.ACTION_SCAN, 11))
            return rv
        }
        logo(ctx, rv, R.id.big_bg, R.id.big_icon, R.id.big_letter, a.title, 80)
        rv.setTextViewText(R.id.name, a.title)
        if (s.widgetCodes) {
            rv.setTextViewText(R.id.code, pretty(ctx, Otp.code(a)))
            rv.setOnClickPendingIntent(R.id.root, copy(ctx, a.id))
        } else {
            rv.setTextViewText(R.id.code, Otp.masked(a.digits))
            rv.setOnClickPendingIntent(R.id.root, open(ctx, MainActivity.ACTION_CODES, 12, a.id))
        }
        val timed = !a.isHotp && s.widgetCodes
        rv.setViewVisibility(R.id.ring, if (timed) View.VISIBLE else View.INVISIBLE)
        rv.setViewVisibility(R.id.timer, if (timed) View.VISIBLE else View.GONE)
        if (timed) {
            val rem = Otp.remainingMs(a.period)
            ring(ctx, rv, R.id.ring, rem, a.period, rem <= 5_000)
            rv.setChronometer(R.id.timer, SystemClock.elapsedRealtime() + rem, null, true)
            rv.setChronometerCountDown(R.id.timer, true)
        }
        return rv
    }

    // --- „Ostoja · kody” 4×2 ---

    private val ROWS = listOf(
        intArrayOf(R.id.r0, R.id.r0_bg, R.id.r0_icon, R.id.r0_letter, R.id.r0_name, R.id.r0_email, R.id.r0_code, R.id.r0_sep),
        intArrayOf(R.id.r1, R.id.r1_bg, R.id.r1_icon, R.id.r1_letter, R.id.r1_name, R.id.r1_email, R.id.r1_code, R.id.r1_sep),
        intArrayOf(R.id.r2, R.id.r2_bg, R.id.r2_icon, R.id.r2_letter, R.id.r2_name, R.id.r2_email, R.id.r2_code, 0),
    )

    fun codes(ctx: Context): RemoteViews {
        val rv = RemoteViews(ctx.packageName, R.layout.widget_codes)
        val s = ctx.app.settings.value
        val acc = accent(ctx)
        val list = sorted(ctx.app.vault.value.otps).take(3)
        rv.setInt(R.id.brand_bg, "setColorFilter", acc)
        rv.setOnClickPendingIntent(R.id.root, open(ctx, MainActivity.ACTION_CODES, 22))
        if (list.isEmpty()) {
            rv.setViewVisibility(R.id.list, View.GONE)
            rv.setViewVisibility(R.id.empty, View.VISIBLE)
            rv.setViewVisibility(R.id.timer, View.GONE)
            rv.setViewVisibility(R.id.ring, View.GONE)
            rv.setOnClickPendingIntent(R.id.root, open(ctx, MainActivity.ACTION_SCAN, 21))
            return rv
        }
        rv.setViewVisibility(R.id.list, View.VISIBLE)
        rv.setViewVisibility(R.id.empty, View.GONE)
        val timed = s.widgetCodes && list.any { !it.isHotp }
        rv.setViewVisibility(R.id.timer, if (timed) View.VISIBLE else View.GONE)
        rv.setViewVisibility(R.id.ring, if (timed) View.VISIBLE else View.GONE)
        if (timed) {
            val rem = Otp.remainingMs(30)
            ring(ctx, rv, R.id.ring, rem, 30, rem <= 5_000)
            rv.setChronometer(R.id.timer, SystemClock.elapsedRealtime() + rem, null, true)
            rv.setChronometerCountDown(R.id.timer, true)
        }
        ROWS.forEachIndexed { i, ids ->
            val a = list.getOrNull(i)
            if (a == null) {
                rv.setViewVisibility(ids[0], View.INVISIBLE)
                if (ids[7] != 0) rv.setViewVisibility(ids[7], View.INVISIBLE)
                return@forEachIndexed
            }
            rv.setViewVisibility(ids[0], View.VISIBLE)
            if (ids[7] != 0) rv.setViewVisibility(ids[7], if (list.getOrNull(i + 1) != null) View.VISIBLE else View.INVISIBLE)
            logo(ctx, rv, ids[1], ids[2], ids[3], a.title)
            rv.setTextViewText(ids[4], a.title)
            rv.setTextViewText(ids[5], a.label.ifBlank { if (a.isHotp) "Kod licznikowy" else "Kod jednorazowy" })
            rv.setTextColor(ids[6], acc)
            if (s.widgetCodes) {
                rv.setTextViewText(ids[6], pretty(ctx, Otp.code(a)))
                rv.setOnClickPendingIntent(ids[0], copy(ctx, a.id))
            } else {
                rv.setTextViewText(ids[6], Otp.masked(a.digits))
                rv.setOnClickPendingIntent(ids[0], open(ctx, MainActivity.ACTION_CODES, 30 + i, a.id))
            }
        }
        return rv
    }

    // --- kafel skanera 2×2 ---

    fun scan(ctx: Context): RemoteViews {
        val rv = RemoteViews(ctx.packageName, R.layout.widget_scan)
        rv.setInt(R.id.bg, "setColorFilter", accent(ctx))
        rv.setOnClickPendingIntent(R.id.root, open(ctx, MainActivity.ACTION_SCAN, 60))
        return rv
    }

    // --- szybki dostęp 4×1 ---

    fun quick(ctx: Context): RemoteViews {
        val rv = RemoteViews(ctx.packageName, R.layout.widget_quick)
        val acc = accent(ctx)
        val soft = (acc and 0x00FFFFFF) or 0x24000000
        listOf(R.id.b_scan_bg, R.id.b_gen_bg).forEach { rv.setInt(it, "setColorFilter", soft) }
        listOf(R.id.b_scan_ic, R.id.b_gen_ic).forEach { rv.setInt(it, "setColorFilter", acc) }
        rv.setInt(R.id.b_add_bg, "setColorFilter", acc)
        rv.setOnClickPendingIntent(R.id.logo, open(ctx, Intent.ACTION_MAIN, 40))
        rv.setOnClickPendingIntent(R.id.search, open(ctx, MainActivity.ACTION_SEARCH, 41))
        rv.setOnClickPendingIntent(R.id.b_scan, open(ctx, MainActivity.ACTION_SCAN, 42))
        rv.setOnClickPendingIntent(R.id.b_add, open(ctx, MainActivity.ACTION_NEW, 43))
        rv.setOnClickPendingIntent(
            R.id.b_gen,
            PendingIntent.getBroadcast(
                ctx, 44, Intent(ctx, ActionReceiver::class.java).setAction(ACTION_GEN),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            ),
        )
        return rv
    }

    // --- „Kondycja sejfu” 4×1 ---

    fun security(ctx: Context): RemoteViews {
        val rv = RemoteViews(ctx.packageName, R.layout.widget_security)
        val d = ctx.app.vault.value
        val r = d.securityReport()
        val acc = accent(ctx)
        val empty = d.logins.isEmpty()
        rv.setTextViewText(R.id.score, if (empty) "-" else r.score.toString())
        rv.setProgressBar(R.id.ring, 100, if (empty) 0 else r.score, false)
        if (Build.VERSION.SDK_INT >= 31) rv.setColorStateList(R.id.ring, "setProgressTintList", ColorStateList.valueOf(acc))
        val toFix = (r.weak + r.reused).size
        rv.setTextViewText(
            R.id.stats,
            when {
                empty -> "Dodaj pierwsze hasło"
                toFix == 0 && r.breached.isEmpty() -> "Wszystkie hasła w porządku"
                else -> "$toFix ${plural(toFix, "hasło", "hasła", "haseł")} do poprawy, ${r.breached.size} w wycieku"
            },
        )
        rv.setInt(R.id.fix_bg, "setColorFilter", (acc and 0x00FFFFFF) or 0x1F000000)
        rv.setTextColor(R.id.fix_text, acc)
        rv.setTextViewText(R.id.fix_text, if (empty) "Dodaj" else "Napraw")
        rv.setOnClickPendingIntent(R.id.fix, open(ctx, if (empty) MainActivity.ACTION_NEW else MainActivity.ACTION_ISSUES, 51))
        rv.setOnClickPendingIntent(R.id.root, open(ctx, Intent.ACTION_MAIN, 50))
        return rv
    }
}

abstract class BaseWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        Widgets.updateAll(context)
    }

    override fun onDisabled(context: Context) {
        Widgets.updateCodes(context)
    }
}

class CodeWidget : BaseWidget() {
    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        val e = Widgets.prefs(context).edit()
        appWidgetIds.forEach { e.remove("code_$it") }
        e.apply()
    }
}

class CodesWidget : BaseWidget()
class QuickWidget : BaseWidget()
class SecurityWidget : BaseWidget()
class ScanWidget : BaseWidget()

class ActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Widgets.ACTION_TICK -> Widgets.tick(context)
            Widgets.ACTION_COPY -> {
                val id = intent.data?.lastPathSegment ?: return
                val a = context.app.vault.value.otp(id) ?: return
                Clip.copy(context, "Kod 2FA · ${a.title}", Otp.code(a), toast = true)
            }
            Widgets.ACTION_GEN -> {
                val pw = PasswordTools.generate(GenOptions())
                Clip.copy(context, "Nowe hasło", pw)
                Toast.makeText(context, "Skopiowano nowe silne hasło (${pw.length} znaków)", Toast.LENGTH_SHORT).show()
            }
        }
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Widgets.updateAll(context)
        pl.wolter.ostoja.sync.LinkService.start(context)
    }
}
