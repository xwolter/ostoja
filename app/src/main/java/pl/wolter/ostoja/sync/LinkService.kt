package pl.wolter.ostoja.sync

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import pl.wolter.ostoja.MainActivity
import pl.wolter.ostoja.OstojaApp
import pl.wolter.ostoja.R
import pl.wolter.ostoja.app
import pl.wolter.ostoja.data.SettingsStore

/**
 * Stałe połączenie z komputerem w tle (usługa „połączone urządzenie”, jak przy zegarkach):
 * dzięki niej prośba o logowanie z komputera pojawia się od razu, także przy zamkniętej aplikacji.
 * Można ją wyłączyć w Ustawieniach - wtedy telefon synchronizuje się tylko przy otwartej Ostoi.
 */
class LinkService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var watcher: kotlinx.coroutines.Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!Sync.paired || !Sync.live.value) { stopSelf(); return START_NOT_STICKY }
        val ok = runCatching {
            ServiceCompat.startForeground(
                this, NOTIF_ID, build(),
                if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE else 0,
            )
        }.isSuccess
        if (!ok) { stopSelf(); return START_NOT_STICKY }
        Sync.hold("service", true)
        watcher?.cancel()
        watcher = scope.launch {
            Sync.computers.collect { runCatching { NotificationManagerCompat.from(this@LinkService).notify(NOTIF_ID, build()) } }
        }
        return START_STICKY
    }

    private fun build(): Notification {
        val names = Sync.computers.value.joinToString(", ") { it.name }
        val accent = SettingsStore.ACCENTS.getOrElse(app.settings.value.accent) { SettingsStore.ACCENTS[0] }
        val open = PendingIntent.getActivity(
            this, 20, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, OstojaApp.CH_LINK)
            .setSmallIcon(R.drawable.ic_stat)
            .setColor(accent)
            .setContentTitle("Połączono z komputerem")
            .setContentText("$names · sejf synchronizuje się w tle")
            .setContentIntent(open)
            .setOngoing(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    override fun onDestroy() {
        Sync.hold("service", false)
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val NOTIF_ID = 31

        fun start(ctx: Context) {
            if (!Sync.paired || !Sync.live.value) return
            // Start z tła (np. proces obudzony przez widżet) Android może odrzucić - wtedy wystarczy pętla przy otwartej aplikacji.
            runCatching { ContextCompat.startForegroundService(ctx, Intent(ctx, LinkService::class.java)) }
        }

        fun stop(ctx: Context) {
            ctx.stopService(Intent(ctx, LinkService::class.java))
        }
    }
}

/** Powiadomienie „Czy to Ty logujesz się na komputerze?” z prototypu (Odrzuć / Sprawdź). */
object RequestNotification {
    private const val NOTIF_ID = 32
    const val ACTION_DENY = "pl.wolter.ostoja.DENY"
    const val EXTRA_REQUEST = "request"

    fun show(ctx: Context, inc: Incoming) {
        val r = inc.request
        val accent = SettingsStore.ACCENTS.getOrElse(ctx.app.settings.value.accent) { SettingsStore.ACCENTS[0] }
        val open = PendingIntent.getActivity(
            ctx, 21,
            Intent(ctx, MainActivity::class.java).putExtra(EXTRA_REQUEST, r.id).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val deny = PendingIntent.getBroadcast(
            ctx, 22, Intent(ctx, RequestReceiver::class.java).setAction(ACTION_DENY).putExtra(EXTRA_REQUEST, r.id),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(ctx, OstojaApp.CH_REQUESTS)
            .setSmallIcon(R.drawable.ic_stat)
            .setColor(accent)
            .setContentTitle("Czy to Ty logujesz się na komputerze?")
            .setContentText("${r.app} · ${r.computerName}")
            .setContentIntent(open)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setTimeoutAfter((r.expires - System.currentTimeMillis()).coerceAtLeast(1000))
            .addAction(0, "Odrzuć", deny)
            .addAction(0, "Sprawdź", open)
            .build()
        runCatching { NotificationManagerCompat.from(ctx).notify(NOTIF_ID, n) }
    }

    fun cancel(ctx: Context) = NotificationManagerCompat.from(ctx).cancel(NOTIF_ID)
}

/** „Odrzuć” prosto z powiadomienia - komputer zostaje wylogowany. */
class RequestReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra(RequestNotification.EXTRA_REQUEST) ?: return
        val inc = Sync.request.value?.takeIf { it.request.id == id } ?: run { RequestNotification.cancel(context); return }
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try { Sync.answer(inc, approve = false, picked = null) } finally { pending.finish() }
        }
    }
}
