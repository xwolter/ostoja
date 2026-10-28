package pl.wolter.ostoja.autofill

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.service.autofill.Dataset
import android.view.autofill.AutofillId
import android.view.autofill.AutofillManager
import android.view.autofill.AutofillValue
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import pl.wolter.ostoja.app
import pl.wolter.ostoja.data.Login
import pl.wolter.ostoja.data.hostOf
import pl.wolter.ostoja.security.AppLock
import pl.wolter.ostoja.ui.components.Avatar
import pl.wolter.ostoja.ui.components.GroupItem
import pl.wolter.ostoja.ui.components.OneUiPage
import pl.wolter.ostoja.ui.components.SearchField
import pl.wolter.ostoja.ui.components.rowPress
import pl.wolter.ostoja.ui.theme.C
import pl.wolter.ostoja.ui.theme.OstojaTheme
import pl.wolter.ostoja.ui.theme.T

/** Odblokowanie przed wypełnieniem pola (biometria) i - dla „Szukaj w Ostoi…” - wybór wpisu. */
class AutofillAuthActivity : FragmentActivity() {
    private var picking by mutableStateOf(false)
    private var asking by mutableStateOf<Login?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setResult(Activity.RESULT_CANCELED)
        val loginId = intent.getStringExtra(EXTRA_LOGIN)

        setContent {
            val s by app.settings.flow.collectAsState()
            if (picking) OstojaTheme(s) { Picker(intent.getStringExtra(EXTRA_DOMAIN).orEmpty()) { finishWith(it) } }
            asking?.let { l -> OstojaTheme(s) { ConfirmApp(l, intent.getStringExtra(EXTRA_APP).orEmpty(), { finishWith(l) }) { finish() } } }
        }

        val proceed = {
            val l = loginId?.let { app.vault.value.login(it) }
            when {
                loginId == null -> picking = true
                l == null -> finish()
                // Hasło dla aplikacji (nie przeglądarki): pokazujemy, który pakiet o nie prosi.
                intent.getStringExtra(EXTRA_APP) != null -> asking = l
                else -> finishWith(l)
            }
        }
        if (AppLock.enabled(this)) AppLock.prompt(this, "Autouzupełnianie", "Potwierdź, aby wpisać dane logowania") { ok ->
            if (ok) { AppLock.locked.value = false; proceed() } else finish()
        } else proceed()
    }

    private fun ids(key: String): List<AutofillId> =
        if (Build.VERSION.SDK_INT >= 33) intent.getParcelableArrayListExtra(key, AutofillId::class.java).orEmpty()
        else @Suppress("DEPRECATION") intent.getParcelableArrayListExtra<AutofillId>(key).orEmpty()

    @Suppress("DEPRECATION")
    private fun finishWith(l: Login) {
        val presentation = OstojaAutofillService.item(this, l.title, l.username)
        val ds = Dataset.Builder(presentation)
        ids(EXTRA_USER).forEach { ds.setValue(it, AutofillValue.forText(l.username), presentation) }
        ids(EXTRA_PASS).forEach { ds.setValue(it, AutofillValue.forText(l.password), presentation) }
        setResult(Activity.RESULT_OK, Intent().putExtra(AutofillManager.EXTRA_AUTHENTICATION_RESULT, ds.build()))
        // „Ostoja · autouzupełnianie - Kod dla GitHub”: kod w schowku i w powiadomieniu, gotowy na następny ekran logowania
        app.vault.value.otp(l.otpId)?.let { otp ->
            pl.wolter.ostoja.security.Clip.copy(this, "Kod 2FA · ${otp.title}", pl.wolter.ostoja.data.Otp.code(otp))
            runCatching { pl.wolter.ostoja.notify.PinService.start(this, otp.id, "autouzupełnianie") }
        }
        finish()
    }

    companion object {
        const val EXTRA_LOGIN = "login"
        const val EXTRA_USER = "user"
        const val EXTRA_PASS = "pass"
        const val EXTRA_DOMAIN = "domain"
        const val EXTRA_APP = "app"
    }
}

@androidx.compose.runtime.Composable
private fun Picker(domain: String, onPick: (Login) -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val vault by ctx.app.vault.data.collectAsState()
    var q by remember { mutableStateOf("") }
    val list = vault.logins
        .filter { q.isBlank() || it.title.contains(q, true) || it.username.contains(q, true) || hostOf(it.url).contains(q, true) }
        .sortedBy { it.title.lowercase() }
    Box(Modifier.fillMaxSize().background(C.bg)) {
        OneUiPage(title = "Wybierz konto", subtitle = domain.ifBlank { null }, bottomPadding = 24.dp) {
            item { SearchField(q, { q = it }, "Szukaj w sejfie", Modifier.padding(bottom = 10.dp)) }
            itemsIndexed(list, key = { _, l -> l.id }) { i, l ->
                GroupItem(i, list.size) {
                    Row(Modifier.fillMaxWidth().rowPress { onPick(l) }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Avatar(l.title, l.url, type = l.type)
                        Column(Modifier.padding(start = 14.dp)) {
                            Text(l.title, style = T.bodyM, color = C.text)
                            if (l.username.isNotBlank()) Text(l.username, style = T.sub, color = C.text2)
                        }
                    }
                }
            }
        }
    }
}

/** „Wpisać hasło do Allegro w aplikacji com.example.app?” - chroni przed aplikacją podszywającą się nazwą pakietu. */
@androidx.compose.runtime.Composable
private fun ConfirmApp(l: Login, pkg: String, onYes: () -> Unit, onNo: () -> Unit) {
    Box(Modifier.fillMaxSize().background(C.bg), contentAlignment = Alignment.Center) {
        Column(Modifier.padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Avatar(l.title, l.url, size = 60.dp, type = l.type)
            Text("Wpisać hasło do ${l.title}?", style = T.title, color = C.text, modifier = Modifier.padding(top = 18.dp))
            Text(
                "Prosi o nie aplikacja $pkg. Upewnij się, że to oficjalna aplikacja ${l.title}, a nie podróbka.",
                style = T.sub, color = C.text2, modifier = Modifier.padding(top = 8.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            Row(Modifier.padding(top = 26.dp)) {
                androidx.compose.material3.TextButton(onClick = onNo) { Text("Anuluj", color = C.text) }
                androidx.compose.foundation.layout.Spacer(Modifier.padding(horizontal = 8.dp))
                androidx.compose.material3.Button(onClick = onYes) { Text("Wpisz") }
            }
        }
    }
}
