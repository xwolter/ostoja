package pl.wolter.ostoja

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import pl.wolter.ostoja.data.SettingsStore
import pl.wolter.ostoja.data.Vault
import pl.wolter.ostoja.sync.LinkService
import pl.wolter.ostoja.sync.Sync
import pl.wolter.ostoja.widget.Widgets

class OstojaApp : Application() {
    lateinit var vault: Vault
        private set
    lateinit var settings: SettingsStore
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        settings = SettingsStore(this)
        vault = Vault(this) { local -> Widgets.updateAll(this); if (local) Sync.localChanged() }
        createChannels()
        Sync.init(this)
        // Na ekranie: nasłuch zmian i próśb z komputera. W tle robi to LinkService (jeśli włączony).
        androidx.lifecycle.ProcessLifecycleOwner.get().lifecycle.addObserver(object : androidx.lifecycle.DefaultLifecycleObserver {
            override fun onStart(owner: androidx.lifecycle.LifecycleOwner) {
                Sync.hold("ui", true)
                LinkService.start(this@OstojaApp)
            }
            override fun onStop(owner: androidx.lifecycle.LifecycleOwner) = Sync.hold("ui", false)
        })
    }

    private fun createChannels() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CH_CODES, "Przypięte kody 2FA", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Kod widoczny w powiadomieniach i na ekranie blokady przez kilka minut"
                setSound(null, null)
                enableVibration(false)
                setShowBadge(false)
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_REQUESTS, "Prośby logowania z komputera", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "„Czy to Ty logujesz się na komputerze?” - potwierdzenie co 7 dni"
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_LINK, "Połączenie z komputerem", NotificationManager.IMPORTANCE_MIN).apply {
                description = "Stałe powiadomienie, gdy telefon synchronizuje się z komputerem w tle"
                setShowBadge(false)
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_ALERTS, "Alerty bezpieczeństwa", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Hasła znalezione w publicznych wyciekach danych"
            }
        )
    }

    companion object {
        const val CH_CODES = "codes"
        const val CH_ALERTS = "alerts"
        const val CH_REQUESTS = "requests"
        const val CH_LINK = "link"
        lateinit var instance: OstojaApp
            private set
    }
}

val Context.app get() = applicationContext as OstojaApp
