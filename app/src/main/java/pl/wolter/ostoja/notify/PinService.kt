package pl.wolter.ostoja.notify

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import pl.wolter.ostoja.MainActivity
import pl.wolter.ostoja.OstojaApp
import pl.wolter.ostoja.R
import pl.wolter.ostoja.app
import pl.wolter.ostoja.data.Otp
import pl.wolter.ostoja.data.OtpAccount
import pl.wolter.ostoja.data.SettingsStore
import pl.wolter.ostoja.security.Clip

/**
 * Kod 2FA przypięty do powiadomień na kilka minut: widać go w panelu, na ekranie blokady
 * i (One UI 8+/Android 16) jako Live Update w Now Barze. Odlicza sekundy i sam się odświeża.
 * Działa jako krótka usługa pierwszoplanowa - system i tak zakończy ją po 3 minutach.
 */
class PinService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var job: Job? = null
    private var otpId: String? = null
    private var source = "Kod 2FA"

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) { stop(); return START_NOT_STICKY }
        val id = intent?.getStringExtra(EXTRA_ID) ?: run { stop(); return START_NOT_STICKY }
        val acc = app.vault.value.otp(id) ?: run { stop(); return START_NOT_STICKY }
        otpId = id
        source = intent.getStringExtra(EXTRA_SOURCE) ?: "Kod 2FA"
        ServiceCompat.startForeground(
            this, NOTIF_ID, build(acc),
            if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SHORT_SERVICE else 0,
        )
        job?.cancel()
        job = scope.launch {
            val until = System.currentTimeMillis() + LIFETIME_MS
            while (System.currentTimeMillis() < until) {
                val a = app.vault.value.otp(id) ?: break
                runCatching { NotificationManagerCompat.from(this@PinService).notify(NOTIF_ID, build(a)) }
                delay(Otp.remainingMs(if (a.isHotp) 30 else a.period) + 120)
            }
            stop()
        }
        return START_NOT_STICKY
    }

    private fun build(a: OtpAccount): Notification {
        val s = app.settings.value
        val accent = SettingsStore.ACCENTS.getOrElse(s.accent) { SettingsStore.ACCENTS[0] }
        val code = Otp.pretty(Otp.code(a))
        val periodEnd = if (a.isHotp) 0L else System.currentTimeMillis() + Otp.remainingMs(a.period)
        val open = PendingIntent.getActivity(
            this, 1,
            Intent(this, MainActivity::class.java).setAction(MainActivity.ACTION_CODES).putExtra(MainActivity.EXTRA_OTP, a.id)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val copy = PendingIntent.getBroadcast(
            this, 2,
            Intent(this, PinActionReceiver::class.java).setAction(ACTION_COPY).setData(Uri.parse("ostoja://pin/${a.id}")),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stop = PendingIntent.getService(
            this, 3, Intent(this, PinService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val who = a.title + if (a.label.isNotBlank()) " · " + a.label else ""

        // Wersja na zablokowany ekran - bez samego kodu, chyba że użytkownik na to pozwolił.
        val public = NotificationCompat.Builder(this, OstojaApp.CH_CODES)
            .setSmallIcon(R.drawable.ic_stat)
            .setColor(accent)
            .setContentTitle("Kod 2FA · ${a.title}")
            .setContentText("Odblokuj telefon, aby zobaczyć kod")
            .build()

        val b = NotificationCompat.Builder(this, OstojaApp.CH_CODES)
            .setSmallIcon(R.drawable.ic_stat)
            .setColor(accent)
            .setContentTitle(code)
            .setContentText("Kod dla $who")
            .setSubText(source)
            .setStyle(NotificationCompat.BigTextStyle().setBigContentTitle(code).bigText("Kod dla $who\nDotknij „Kopiuj”, aby wkleić kod w aplikacji."))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(open)
            .addAction(0, "Kopiuj", copy)
            .addAction(0, "Odepnij", stop)
            .setVisibility(if (s.lockscreenCode) NotificationCompat.VISIBILITY_PUBLIC else NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(public)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
        if (periodEnd > 0) {
            b.setWhen(periodEnd).setShowWhen(true).setUsesChronometer(true).setChronometerCountDown(true)
        }
        // Prośba o „Live Update” (Android 16 / One UI 8+): kod trafia do Now Bara i chipa na pasku stanu.
        b.extras.putBoolean("android.requestPromotedOngoing", true)
        return b.build()
    }

    private fun stop() {
        job?.cancel()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onTimeout(startId: Int) { stop() }

    override fun onTimeout(startId: Int, fgsType: Int) { stop() }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val NOTIF_ID = 42
        private const val LIFETIME_MS = 170_000L
        const val EXTRA_ID = "id"
        const val EXTRA_SOURCE = "source"
        const val ACTION_STOP = "pl.wolter.ostoja.pin.STOP"
        const val ACTION_COPY = "pl.wolter.ostoja.pin.COPY"

        fun start(ctx: Context, otpId: String, source: String? = null) {
            ContextCompat.startForegroundService(
                ctx, Intent(ctx, PinService::class.java).putExtra(EXTRA_ID, otpId).putExtra(EXTRA_SOURCE, source),
            )
        }
    }
}

class PinActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != PinService.ACTION_COPY) return
        val id = intent.data?.lastPathSegment ?: return
        val a = context.app.vault.value.otp(id) ?: return
        Clip.copy(context, "Kod 2FA · ${a.title}", Otp.code(a), toast = true)
    }
}
