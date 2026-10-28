package pl.wolter.ostoja.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import pl.wolter.ostoja.MainActivity
import pl.wolter.ostoja.app
import pl.wolter.ostoja.data.OtpAccount
import pl.wolter.ostoja.ui.Ic
import pl.wolter.ostoja.ui.components.Avatar
import pl.wolter.ostoja.ui.components.EmptyState
import pl.wolter.ostoja.ui.components.GroupItem
import pl.wolter.ostoja.ui.components.OneUiPage
import pl.wolter.ostoja.ui.components.PillButton
import pl.wolter.ostoja.ui.components.rowPress
import pl.wolter.ostoja.ui.theme.C
import pl.wolter.ostoja.ui.theme.OstojaTheme
import pl.wolter.ostoja.ui.theme.T

/** Wybór konta dla widżetu „Kod 2FA” (pokazywany przy dodawaniu i przy zmianie ustawień widżetu). */
class CodeWidgetConfigActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val widgetId = intent?.extras?.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) { finish(); return }
        setResult(Activity.RESULT_CANCELED, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))

        fun done(a: OtpAccount?) {
            if (a != null) Widgets.prefs(this).edit().putString("code_$widgetId", a.id).apply()
            Widgets.updateAll(this)
            setResult(Activity.RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
            finish()
        }

        setContent {
            val s by app.settings.flow.collectAsState()
            val vault by app.vault.data.collectAsState()
            val current = Widgets.prefs(this).getString("code_$widgetId", null)
            OstojaTheme(s) {
                val list = vault.otps.sortedWith(compareByDescending<OtpAccount> { it.favorite }.thenBy { it.title.lowercase() })
                OneUiPage(title = "Widżet kodu", subtitle = "Wybierz konto do pokazania", bottomPadding = 32.dp) {
                    if (list.isEmpty()) {
                        item {
                            EmptyState(Ic.ShieldCheck, "Brak kont 2FA", "Dodaj konto w Ostoi - widżet pokaże je automatycznie.") {
                                PillButton("Otwórz Ostoję", {
                                    startActivity(Intent(this@CodeWidgetConfigActivity, MainActivity::class.java).setAction(MainActivity.ACTION_SCAN))
                                    done(null)
                                }, icon = Ic.Scan)
                            }
                        }
                    } else itemsIndexed(list, key = { _, a -> a.id }) { i, a ->
                        GroupItem(i, list.size) {
                            Row(
                                Modifier.fillMaxWidth().rowPress { done(a) }.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Avatar(a.title)
                                Column(Modifier.weight(1f).padding(start = 14.dp)) {
                                    Text(a.title, style = T.bodyM, color = if (a.id == current) C.accent else C.text)
                                    if (a.label.isNotBlank()) Text(a.label, style = T.sub, color = C.text2)
                                }
                                if (a.id == current) androidx.compose.material3.Icon(Ic.Check, null, tint = C.accent)
                            }
                        }
                    }
                }
            }
        }
    }
}
