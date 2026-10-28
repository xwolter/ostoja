package pl.wolter.ostoja.notify

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import pl.wolter.ostoja.MainActivity
import pl.wolter.ostoja.OstojaApp
import pl.wolter.ostoja.R
import pl.wolter.ostoja.data.Login

/** „Ostoja · alert - Hasło Reddit wyciekło” z prototypu ekranu blokady. */
object Alerts {
    fun leaked(ctx: Context, list: List<Login>) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val nm = NotificationManagerCompat.from(ctx)
        list.take(5).forEach { l ->
            val open = PendingIntent.getActivity(
                ctx, l.id.hashCode(),
                Intent(ctx, MainActivity::class.java).setAction(MainActivity.ACTION_ISSUES).putExtra(MainActivity.EXTRA_LOGIN, l.id)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val public = NotificationCompat.Builder(ctx, OstojaApp.CH_ALERTS)
                .setSmallIcon(R.drawable.ic_stat)
                .setColor(0xFFE5484D.toInt())
                .setContentTitle("Alert bezpieczeństwa")
                .setContentText("Odblokuj telefon, aby zobaczyć szczegóły")
                .build()
            val n = NotificationCompat.Builder(ctx, OstojaApp.CH_ALERTS)
                .setSmallIcon(R.drawable.ic_stat)
                .setColor(0xFFE5484D.toInt())
                .setSubText("alert")
                .setContentTitle("Hasło ${l.title} wyciekło")
                .setContentText("Pojawiło się w publicznej bazie wycieków. Zmień je jak najszybciej.")
                .setStyle(NotificationCompat.BigTextStyle().bigText("Pojawiło się w publicznej bazie wycieków. Zmień je jak najszybciej."))
                .setCategory(NotificationCompat.CATEGORY_ERROR)
                .setContentIntent(open)
                .setAutoCancel(true)
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .setPublicVersion(public)
                .build()
            runCatching { nm.notify(2000 + (l.id.hashCode() and 0xFFF), n) }
        }
    }
}
