package pl.wolter.ostoja.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
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
import pl.wolter.ostoja.data.Otp
import pl.wolter.ostoja.data.OtpAccount
import pl.wolter.ostoja.data.VaultData
import pl.wolter.ostoja.ui.Ic
import pl.wolter.ostoja.ui.Nav
import pl.wolter.ostoja.ui.components.Avatar
import pl.wolter.ostoja.ui.components.Chip
import pl.wolter.ostoja.ui.components.CountdownRing
import pl.wolter.ostoja.ui.components.Divider
import pl.wolter.ostoja.ui.components.Field
import pl.wolter.ostoja.ui.components.Group
import pl.wolter.ostoja.ui.components.IconBtn
import pl.wolter.ostoja.ui.components.LocalToaster
import pl.wolter.ostoja.ui.components.OneUiPage
import pl.wolter.ostoja.ui.components.PillButton
import pl.wolter.ostoja.ui.components.SectionLabel
import pl.wolter.ostoja.ui.components.SettingRow
import pl.wolter.ostoja.ui.components.rowPress
import pl.wolter.ostoja.ui.theme.C
import pl.wolter.ostoja.ui.theme.T

/** Dodawanie (ręczne lub potwierdzenie po skanie) i edycja konta 2FA. */
@Composable
fun EditOtpScreen(id: String?, prefill: OtpAccount?, linkTo: String?, nav: Nav, vault: VaultData) {
    val ctx = LocalContext.current
    val toaster = LocalToaster.current
    val orig = remember(id) { id?.let { vault.otp(it) } }
    val base = orig ?: prefill
    var issuer by rememberSaveable { mutableStateOf(base?.issuer ?: "") }
    var label by rememberSaveable { mutableStateOf(base?.label ?: "") }
    var secret by rememberSaveable { mutableStateOf(base?.secret ?: "") }
    var type by rememberSaveable { mutableStateOf(base?.type ?: OtpAccount.TOTP) }
    var alg by rememberSaveable { mutableStateOf(base?.algorithm ?: "SHA1") }
    var digits by rememberSaveable { mutableIntStateOf(base?.digits ?: 6) }
    var period by rememberSaveable { mutableIntStateOf(base?.period ?: 30) }
    var counter by rememberSaveable { mutableLongStateOf(base?.counter ?: 0L) }
    var advanced by remember { mutableStateOf(base != null && (base.algorithm != "SHA1" || base.digits != 6 || base.period != 30 || base.isHotp)) }
    var link by rememberSaveable {
        mutableStateOf(linkTo ?: orig?.let { o -> vault.logins.firstOrNull { it.otpId == o.id }?.id })
    }
    var pickLogin by remember { mutableStateOf(false) }
    var showErrors by remember { mutableStateOf(false) }

    val secretOk = Otp.isValidSecret(secret)
    val draft = OtpAccount(
        id = orig?.id ?: base?.id ?: pl.wolter.ostoja.data.newId(),
        issuer = issuer.trim(), label = label.trim(), secret = Otp.normalizeSecret(secret),
        algorithm = alg, digits = digits, period = period, type = type, counter = counter,
        favorite = orig?.favorite ?: false, created = orig?.created ?: System.currentTimeMillis(),
    )

    fun save() {
        if (issuer.isBlank() || !secretOk) { showErrors = true; return }
        val v = ctx.app.vault
        if (orig == null) {
            val added = v.addOtps(listOf(draft), link)
            if (added.isEmpty()) { toaster.show("To konto już jest w Ostoi"); nav.pop(); return }
        } else v.saveOtp(draft)
        // powiązanie z hasłem (bez wyboru zostaje automatyczne dopasowanie po nazwie z addOtps)
        if (orig != null || link != null) v.update { d ->
            d.copy(logins = d.logins.map {
                when {
                    it.id == link -> it.copy(otpId = draft.id)
                    it.otpId == draft.id && it.id != link -> it.copy(otpId = null)
                    else -> it
                }
            })
        }
        toaster.show(if (orig == null) "Dodano ${draft.title}" else "Zapisano zmiany")
        nav.highlightOtp = draft.id
        nav.tab = 1
        nav.reset()
    }

    OneUiPage(
        title = if (orig != null) "Edytuj konto" else if (prefill != null) "Nowe konto 2FA" else "Wpisz klucz",
        subtitle = if (prefill != null && orig == null) "Odczytano z kodu QR - sprawdź i zapisz" else null,
        state = rememberLazyListState(),
        onBack = { nav.pop() },
        bottomPadding = 40.dp,
        actions = { IconBtn(Ic.Check, { save() }, tint = C.accent, desc = "Zapisz") },
    ) {
        item(key = "preview") { CodePreview(draft, secretOk) }
        item(key = "form") {
            Spacer(Modifier.height(20.dp))
            Group {
                Column(Modifier.padding(horizontal = 18.dp, vertical = 14.dp)) {
                    Field("Serwis", issuer, { issuer = it }, placeholder = "np. Google", error = if (showErrors && issuer.isBlank()) "Podaj nazwę serwisu" else null)
                    Spacer(Modifier.height(16.dp))
                    Field("Konto", label, { label = it }, placeholder = "e-mail lub login (opcjonalnie)", keyboard = KeyboardType.Email)
                    Spacer(Modifier.height(16.dp))
                    Field(
                        "Klucz tajny", secret, { secret = it }, mono = true, singleLine = false,
                        placeholder = "JBSW Y3DP EHPK 3PXP",
                        error = if ((showErrors || secret.length > 6) && !secretOk) "Klucz w formacie Base32 (litery A-Z i cyfry 2-7)" else null,
                    )
                }
            }
        }
        item(key = "adv") {
            Spacer(Modifier.height(28.dp))
            SectionLabel("Zaawansowane", action = {
                Text(
                    if (advanced) "Ukryj" else "Pokaż", style = T.sub, color = C.accent,
                    modifier = Modifier.clip(RoundedCornerShape(50)).rowPress { advanced = !advanced }.padding(horizontal = 10.dp, vertical = 4.dp),
                )
            })
            AnimatedVisibility(advanced) {
                Group {
                    Column(Modifier.padding(18.dp)) {
                        Text("Typ", style = T.small, color = C.text2)
                        Row(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ChipF("Czasowy (TOTP)", type == OtpAccount.TOTP) { type = OtpAccount.TOTP }
                            ChipF("Licznik (HOTP)", type == OtpAccount.HOTP) { type = OtpAccount.HOTP }
                        }
                        Text("Algorytm", style = T.small, color = C.text2, modifier = Modifier.padding(top = 8.dp))
                        Row(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("SHA1", "SHA256", "SHA512").forEach { a -> ChipF(a, alg == a) { alg = a } }
                        }
                        Text("Liczba cyfr", style = T.small, color = C.text2, modifier = Modifier.padding(top = 8.dp))
                        Row(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(6, 7, 8).forEach { d -> ChipF("$d", digits == d) { digits = d } }
                        }
                        if (type == OtpAccount.TOTP) {
                            Text("Okres", style = T.small, color = C.text2, modifier = Modifier.padding(top = 8.dp))
                            Row(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf(30, 60).forEach { p -> ChipF("$p s", period == p) { period = p } }
                            }
                        } else {
                            Spacer(Modifier.height(8.dp))
                            Field("Licznik", counter.toString(), { s -> s.filter { it.isDigit() }.toLongOrNull()?.let { counter = it } }, keyboard = KeyboardType.Number)
                        }
                    }
                }
            }
        }
        item(key = "link") {
            Spacer(Modifier.height(28.dp))
            SectionLabel("Powiązanie")
            Group {
                val l = link?.let { vault.login(it) }
                SettingRow(
                    "Hasło w sejfie", l?.let { it.title + if (it.username.isNotBlank()) " · " + it.username else "" } ?: "Nie powiązano",
                    subtitleColor = if (l != null) C.accent else C.text2,
                    onClick = { pickLogin = true },
                )
            }
        }
        item(key = "save") {
            PillButton(
                if (orig == null) "Dodaj konto" else "Zapisz zmiany", { save() }, icon = Ic.Check,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 20.dp),
            )
        }
    }

    if (pickLogin) OneSheet({ pickLogin = false }) {
        Text("Powiąż z hasłem", style = T.title, color = C.text, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
        SheetAction(Ic.Close, "Bez powiązania") { link = null; pickLogin = false }
        vault.logins.sortedBy { it.title.lowercase() }.forEach { l ->
            Row(
                Modifier.fillMaxWidth().rowPress { link = l.id; pickLogin = false }.padding(horizontal = 20.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Avatar(l.title, l.url, size = 36.dp, type = l.type)
                Column(Modifier.weight(1f).padding(start = 14.dp)) {
                    Text(l.title, style = T.body, color = if (l.id == link) C.accent else C.text)
                    if (l.username.isNotBlank()) Text(l.username, style = T.small, color = C.text2)
                }
                if (l.id == link) Icon(Ic.Check, null, tint = C.accent)
            }
        }
    }
}

@Composable
private fun ChipF(text: String, selected: Boolean, onClick: () -> Unit) {
    // Chip na tle karty - w zaznaczeniu akcent, inaczej pole.
    androidx.compose.foundation.layout.Box(
        Modifier.clip(RoundedCornerShape(50)).background(if (selected) C.accent else C.field)
            .rowPress(onClick = onClick).padding(horizontal = 14.dp, vertical = 8.dp)
    ) { Text(text, style = T.sub, color = if (selected) androidx.compose.ui.graphics.Color.White else C.text) }
}

@Composable
private fun CodePreview(a: OtpAccount, ok: Boolean) {
    val now = rememberNow()
    val counter by remember(a) { derivedStateOf { Otp.counterAt(a, now.value) } }
    val secs by remember(a) { derivedStateOf { ((Otp.remainingMs(a.period, now.value) + 999) / 1000).toInt() } }
    val code = remember(a, counter) { if (ok) Otp.code(a, if (a.isHotp) 0 else counter * a.period * 1000L) else "" }
    Row(
        Modifier
            .padding(horizontal = 12.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(30.dp))
            .background(C.card)
            .padding(horizontal = 20.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(a.issuer.ifBlank { "?" })
        Column(Modifier.weight(1f).padding(start = 14.dp)) {
            Text(a.issuer.ifBlank { "Serwis" }, style = T.bodyM, color = C.text)
            pl.wolter.ostoja.ui.components.CodeText(if (ok) code else "0".repeat(a.digits), if (ok) C.accent else C.text3, hidden = !ok)
        }
        if (ok && !a.isHotp) CountdownRing({ Otp.remainingMs(a.period, now.value) / (a.period * 1000f) }, secs <= 5)
        Spacer(Modifier.width(2.dp))
    }
}
