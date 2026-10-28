package pl.wolter.ostoja.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import pl.wolter.ostoja.app
import pl.wolter.ostoja.data.Login
import pl.wolter.ostoja.data.PasswordTools
import pl.wolter.ostoja.data.VaultData
import pl.wolter.ostoja.ui.Ic
import pl.wolter.ostoja.ui.Nav
import pl.wolter.ostoja.ui.Route
import pl.wolter.ostoja.ui.components.Avatar
import pl.wolter.ostoja.ui.components.Divider
import pl.wolter.ostoja.ui.components.Field
import pl.wolter.ostoja.ui.components.Group
import pl.wolter.ostoja.ui.components.IconBtn
import pl.wolter.ostoja.ui.components.LocalToaster
import pl.wolter.ostoja.ui.components.OneDialog
import pl.wolter.ostoja.ui.components.OneUiPage
import pl.wolter.ostoja.ui.components.PillButton
import pl.wolter.ostoja.ui.components.SectionLabel
import pl.wolter.ostoja.ui.components.SettingRow
import pl.wolter.ostoja.ui.components.StrengthBar
import pl.wolter.ostoja.ui.components.SwitchRow
import pl.wolter.ostoja.ui.components.rowPress
import pl.wolter.ostoja.ui.theme.C
import pl.wolter.ostoja.ui.theme.T

@Composable
fun EditLoginScreen(id: String?, nav: Nav, vault: VaultData) {
    val ctx = LocalContext.current
    val toaster = LocalToaster.current
    val orig = remember(id) { id?.let { vault.login(it) } }
    var title by rememberSaveable { mutableStateOf(orig?.title ?: "") }
    var user by rememberSaveable { mutableStateOf(orig?.username ?: "") }
    var pw by rememberSaveable { mutableStateOf(orig?.password ?: "") }
    var url by rememberSaveable { mutableStateOf(orig?.url ?: "") }
    var note by rememberSaveable { mutableStateOf(orig?.note ?: "") }
    var fav by rememberSaveable { mutableStateOf(orig?.favorite ?: false) }
    var otpId by rememberSaveable { mutableStateOf(orig?.otpId) }
    var type by rememberSaveable { mutableStateOf(orig?.type ?: Login.LOGIN) }
    var reveal by remember { mutableStateOf(orig == null) }
    var gen by remember { mutableStateOf(false) }
    var pickOtp by remember { mutableStateOf(false) }
    var askDiscard by remember { mutableStateOf(false) }
    var titleError by remember { mutableStateOf(false) }

    val dirty = title != (orig?.title ?: "") || user != (orig?.username ?: "") || pw != (orig?.password ?: "") ||
        url != (orig?.url ?: "") || note != (orig?.note ?: "") || fav != (orig?.favorite ?: false) || otpId != orig?.otpId || type != (orig?.type ?: Login.LOGIN)

    fun close() { if (dirty) askDiscard = true else nav.pop() }
    BackHandler { close() }

    fun save() {
        val t = title.trim().ifBlank {
            // tytuł z domeny, jeśli podano tylko stronę
            pl.wolter.ostoja.data.registrableDomain(pl.wolter.ostoja.data.hostOf(url)).substringBefore('.').replaceFirstChar { it.uppercase() }
        }
        if (t.isBlank()) { titleError = true; return }
        val base = orig ?: Login(title = t)
        ctx.app.vault.saveLogin(
            base.copy(
                title = t, type = type, username = user.trim(), password = pw,
                url = if (type == Login.LOGIN) url.trim() else "", note = note.trim(), favorite = fav,
                otpId = if (type == Login.LOGIN) otpId else null,
            )
        )
        toaster.show(if (orig == null) "Zapisano $t" else "Zaktualizowano $t")
        if (orig == null) nav.replaceTop(Route.Item(base.id)) else nav.pop()
    }

    OneUiPage(
        title = if (orig == null) "Nowy wpis" else "Edytuj",
        subtitle = orig?.title,
        state = rememberLazyListState(),
        onBack = { close() },
        bottomPadding = 40.dp,
        actions = { IconBtn(Ic.Check, { save() }, tint = C.accent, desc = "Zapisz") },
        headerBottom = if (orig != null) null else ({
            androidx.compose.foundation.lazy.LazyRow(
                Modifier.padding(top = 22.dp),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
            ) {
                val types = listOf(Login.LOGIN to "Login", Login.CARD to "Karta", Login.NOTE to "Notatka", Login.WIFI to "Wi‑Fi")
                items(types.size) { i -> pl.wolter.ostoja.ui.components.Chip(types[i].second, type == types[i].first, { type = types[i].first }) }
            }
        }),
    ) {
        item(key = "form") {
            Group {
                Column(Modifier.padding(horizontal = 18.dp, vertical = 14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Avatar(title.ifBlank { "?" }, url, size = 48.dp, type = type)
                        Spacer(Modifier.width(14.dp))
                        Field(
                            "Nazwa", title, { title = it; titleError = false },
                            placeholder = when (type) { Login.CARD -> "np. Visa · mBank"; Login.NOTE -> "np. Kod do bramy"; Login.WIFI -> "np. Dom"; else -> "np. Google" },
                            error = if (titleError) "Podaj nazwę albo stronę" else null,
                        )
                    }
                    if (type != Login.NOTE) {
                        Spacer(Modifier.height(16.dp))
                        Field(
                            when (type) { Login.CARD -> "Właściciel karty"; Login.WIFI -> "Nazwa sieci (SSID)"; else -> "Nazwa użytkownika lub e-mail" },
                            user, { user = it },
                            placeholder = when (type) { Login.CARD -> "Anna Nowak"; Login.WIFI -> "Polna_5G"; else -> "anna.nowak@gmail.com" },
                            keyboard = if (type == Login.LOGIN) KeyboardType.Email else KeyboardType.Text,
                        )
                        Spacer(Modifier.height(16.dp))
                        if (type == Login.CARD) {
                            Field("Numer karty", pw, { v -> pw = v.filter { it.isDigit() || it == ' ' } }, password = true, revealed = reveal, mono = true,
                                placeholder = "0000 0000 0000 0000", keyboard = KeyboardType.Number) {
                                IconBtn(if (reveal) Ic.EyeOff else Ic.Eye, { reveal = !reveal }, tint = C.text2, size = 40.dp, iconSize = 20.dp)
                            }
                        } else {
                            Field("Hasło", pw, { pw = it }, password = true, revealed = reveal, mono = true, placeholder = "••••••••") {
                                IconBtn(if (reveal) Ic.EyeOff else Ic.Eye, { reveal = !reveal }, tint = C.text2, size = 40.dp, iconSize = 20.dp)
                                IconBtn(Ic.Wand, { gen = true }, tint = C.accent, size = 40.dp, iconSize = 20.dp, desc = "Generuj")
                            }
                            if (pw.isNotEmpty()) {
                                val lvl = strengthLevel(PasswordTools.strength(pw))
                                Row(Modifier.padding(start = 6.dp, top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                    StrengthBar(lvl, strengthColor(lvl), Modifier.weight(1f))
                                    Spacer(Modifier.width(10.dp))
                                    Text(STRENGTH_LABEL[lvl], style = T.small, color = strengthColor(lvl))
                                }
                            }
                        }
                    }
                    if (type == Login.LOGIN) {
                        Spacer(Modifier.height(16.dp))
                        Field("Strona logowania", url, { url = it }, placeholder = "np. https://github.com/login", keyboard = KeyboardType.Uri)
                    }
                    Spacer(Modifier.height(16.dp))
                    Field(
                        if (type == Login.NOTE) "Treść" else "Notatka", note, { note = it }, singleLine = false,
                        placeholder = when (type) { Login.NOTE -> "Treść notatki"; Login.CARD -> "Ważna do, CVV, PIN…"; else -> "Pytania pomocnicze, kody zapasowe…" },
                    )
                }
            }
        }
        item(key = "more") {
            Spacer(Modifier.height(28.dp))
            SectionLabel("Więcej")
            Group {
                if (type == Login.LOGIN) {
                    val otp = vault.otp(otpId)
                    SettingRow(
                        "Kod 2FA",
                        otp?.let { "${it.title}${if (it.label.isNotBlank()) " · " + it.label else ""}" } ?: "Nie powiązano",
                        subtitleColor = if (otp != null) C.accent else C.text2,
                        onClick = { pickOtp = true },
                    )
                    Divider()
                }
                SwitchRow("Ulubione", "Pokazuj na górze sejfu", fav, { fav = it })
            }
        }
        item(key = "save") {
            PillButton(
                if (orig == null) "Zapisz w sejfie" else "Zapisz zmiany", { save() }, icon = Ic.Check,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 20.dp),
            )
        }
    }

    if (gen) GeneratorSheet(onDismiss = { gen = false }, onUse = { pw = it; reveal = true })

    if (pickOtp) OneSheet({ pickOtp = false }) {
        Text("Powiąż kod 2FA", style = T.title, color = C.text, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
        val used = vault.logins.filter { it.id != id }.mapNotNull { it.otpId }.toSet()
        SheetAction(Ic.Close, "Bez kodu 2FA") { otpId = null; pickOtp = false }
        vault.otps.sortedBy { it.title.lowercase() }.forEach { a ->
            Row(
                Modifier.fillMaxWidth().rowPress { otpId = a.id; pickOtp = false }.padding(horizontal = 20.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Avatar(a.title, size = 36.dp)
                Column(Modifier.weight(1f).padding(start = 14.dp)) {
                    Text(a.title, style = T.body, color = if (a.id == otpId) C.accent else C.text)
                    val extra = if (a.id in used) " · powiązany z innym hasłem" else ""
                    if (a.label.isNotBlank() || extra.isNotEmpty()) Text(a.label + extra, style = T.small, color = C.text2)
                }
                if (a.id == otpId) androidx.compose.material3.Icon(Ic.Check, null, tint = C.accent)
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "Zeskanuj nowy kod QR", style = T.bodyM, color = C.accent,
            modifier = Modifier.padding(horizontal = 12.dp).clip(RoundedCornerShape(50)).rowPress {
                pickOtp = false
                if (orig != null) { nav.scanLinkTo = orig.id; nav.push(Route.Scan) }
                else toaster.show("Najpierw zapisz hasło")
            }.padding(horizontal = 12.dp, vertical = 10.dp),
        )
    }

    if (askDiscard) OneDialog(
        title = "Odrzucić zmiany?",
        text = "Wprowadzone zmiany nie zostaną zapisane.",
        confirm = "Odrzuć", danger = true,
        onConfirm = { askDiscard = false; nav.pop() },
        onDismiss = { askDiscard = false },
        dismiss = "Edytuj dalej",
    )
}
