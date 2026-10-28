package pl.wolter.ostoja.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import pl.wolter.ostoja.app
import pl.wolter.ostoja.data.OtpAccount
import pl.wolter.ostoja.data.OtpUri
import pl.wolter.ostoja.data.VaultData
import pl.wolter.ostoja.data.countLabel
import pl.wolter.ostoja.security.AppLock
import pl.wolter.ostoja.ui.Ic
import pl.wolter.ostoja.ui.Nav
import pl.wolter.ostoja.ui.Route
import pl.wolter.ostoja.ui.TAB_CODES
import pl.wolter.ostoja.ui.components.Avatar
import pl.wolter.ostoja.ui.components.Divider
import pl.wolter.ostoja.ui.components.Group
import pl.wolter.ostoja.ui.components.LocalToaster
import pl.wolter.ostoja.ui.components.OneUiPage
import pl.wolter.ostoja.ui.components.PillButton
import pl.wolter.ostoja.ui.components.SectionLabel
import pl.wolter.ostoja.ui.components.SettingRow
import pl.wolter.ostoja.ui.theme.C
import pl.wolter.ostoja.ui.theme.T

private const val GOOGLE_AUTH = "com.google.android.apps.authenticator2"
private const val MS_AUTH = "com.azure.authenticator"

/** Ustawienia 2FA popularnych serwisów - stąd przepina się konta z Microsoft Authenticator. */
private val SECURITY_PAGES = listOf(
    "Konto Microsoft" to "https://account.microsoft.com/security",
    "Google" to "https://myaccount.google.com/signinoptions/two-step-verification",
    "GitHub" to "https://github.com/settings/security",
    "Facebook" to "https://accountscenter.facebook.com/password_and_security/two_factor",
    "Instagram" to "https://accountscenter.instagram.com/password_and_security/two_factor",
    "Amazon" to "https://www.amazon.com/a/settings/approval",
    "X" to "https://x.com/settings/account/login_verification",
    "Dropbox" to "https://www.dropbox.com/account/security",
)

/**
 * Przeniesienie kodów 2FA z innych aplikacji.
 * Google Authenticator: eksport „Przenieś konta” to jeden albo kilka kodów QR (otpauth-migration://) -
 * skaner zbiera je po kolei, także ze zdjęć. Microsoft Authenticator nie ma eksportu kodów, więc ekran
 * prowadzi przez ponowne dodanie każdego konta.
 */
@Composable
fun ImportOtpScreen(nav: Nav, vault: VaultData) {
    val ctx = LocalContext.current
    val toaster = LocalToaster.current
    val found = remember { mutableStateListOf<OtpAccount>() }
    // seria eksportu → (liczba kodów, odczytane numery)
    val batches = remember { mutableStateMapOf<Long, Pair<Int, Set<Int>>>() }
    var scanning by remember { mutableStateOf(false) }

    val have = remember(vault.otps) { vault.otps.map { it.secret }.toSet() }

    /** Zwraca opis wyniku do pokazania w toaście. */
    fun take(text: String): String {
        val m = runCatching { OtpUri.migration(text) }.getOrElse { return it.message ?: "Uszkodzony kod" }
        val list = m?.accounts ?: runCatching { OtpUri.parse(text) }.getOrElse { return "To nie jest kod z Google Authenticator" }
        var added = 0
        list.forEach { a -> if (found.none { it.secret == a.secret }) { found += a; added++ } }
        if (m != null) {
            val (size, seen) = batches[m.id] ?: (m.size to emptySet())
            batches[m.id] = size to (seen + m.index)
            val left = size - (seen + m.index).size
            return "Kod ${m.index + 1} z ${m.size}: " + countLabel(m.accounts.size, "konto", "konta", "kont") +
                if (left > 0) " · zostało ${countLabel(left, "kod", "kody", "kodów")}" else " · to już wszystkie"
        }
        return if (added > 0) "Dodano do listy: " + countLabel(added, "konto", "konta", "kont") else "To konto już jest na liście"
    }

    val photos = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(20)) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        var ok = 0
        uris.forEach { u -> decodeQrFromImage(ctx, u)?.let { take(it); ok++ } }
        toaster.show(if (ok == 0) "Na zdjęciach nie ma kodów QR" else "Odczytano " + countLabel(ok, "kod", "kody", "kodów") + " ze zdjęć")
    }

    fun open(url: String) {
        AppLock.allowExternal()
        runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            .onFailure { toaster.show("Nie ma aplikacji do otwarcia linku") }
    }

    fun launch(pkg: String, name: String) {
        val i = ctx.packageManager.getLaunchIntentForPackage(pkg)
        if (i == null) toaster.show("$name nie jest zainstalowany na tym telefonie")
        else { AppLock.allowExternal(); ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    if (scanning) {
        val progress = batches.values.joinToString(" · ") { (size, seen) -> "kody ${seen.size} z $size" }
        ScanScreen(
            onClose = { scanning = false },
            onManual = {},
            onResult = {},
            onEach = { text -> toaster.show(take(text)) },
            title = "Kody z Google Authenticator",
            hint = if (found.isEmpty()) "Skanuj kolejno wszystkie kody eksportu" else
                countLabel(found.size, "konto", "konta", "kont") + (if (progress.isNotEmpty()) " · $progress" else "") + " · dotknij Gotowe",
        )
        return
    }

    val fresh = found.filter { it.secret !in have }
    val missing = batches.values.sumOf { (size, seen) -> size - seen.size }

    OneUiPage(title = "Przenieś kody 2FA", subtitle = "Z Google i Microsoft Authenticator", onBack = { nav.pop() }, bottomPadding = 40.dp) {
        if (found.isNotEmpty()) item(key = "found") {
            SectionLabel("Znalezione konta")
            Group {
                Column(Modifier.padding(horizontal = 20.dp, vertical = 14.dp)) {
                    Text(
                        countLabel(found.size, "konto", "konta", "kont") +
                            (if (found.size != fresh.size) " · ${found.size - fresh.size} już w Ostoi" else ""),
                        style = T.bodyM, color = C.text,
                    )
                    if (missing > 0) Text(
                        "Brakuje jeszcze ${countLabel(missing, "kodu", "kodów", "kodów")} z eksportu - zeskanuj je, żeby przenieść wszystko.",
                        style = T.sub, color = C.warnText, modifier = Modifier.padding(top = 4.dp),
                    )
                    Spacer(Modifier.height(10.dp))
                    found.take(12).forEach { a ->
                        Row(Modifier.padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                            Avatar(a.title, size = 32.dp)
                            Column(Modifier.padding(start = 12.dp).weight(1f)) {
                                Text(a.title, style = T.body, color = C.text)
                                if (a.label.isNotBlank()) Text(a.label, style = T.small, color = C.text2)
                            }
                            if (a.secret in have) Text("już jest", style = T.small, color = C.text2)
                        }
                    }
                    if (found.size > 12) Text("i jeszcze ${found.size - 12}…", style = T.small, color = C.text2, modifier = Modifier.padding(top = 4.dp))
                    Spacer(Modifier.height(14.dp))
                    PillButton(
                        if (fresh.isEmpty()) "Wszystkie już są w Ostoi" else "Dodaj " + countLabel(fresh.size, "konto", "konta", "kont"),
                        onClick = {
                            val added = ctx.app.vault.addOtps(fresh)
                            toaster.show("Dodano " + countLabel(added.size, "konto", "konta", "kont") + " · sprawdź kod w Ostoi, zanim usuniesz konta ze starej aplikacji")
                            nav.reset()
                            nav.tab = TAB_CODES
                        },
                        enabled = fresh.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
        }

        item(key = "google") {
            SectionLabel("Google Authenticator")
            Group {
                Column(Modifier.padding(horizontal = 20.dp, vertical = 14.dp)) {
                    Step(1, "W Google Authenticator otwórz menu i wybierz „Przenieś konta” → „Eksportuj konta”.")
                    Step(2, "Zaznacz konta i dotknij „Dalej”. Przy wielu kontach pojawi się kilka kodów QR („1 z 3”).")
                    Step(3, "Zeskanuj po kolei wszystkie kody. Ostoja zbierze je w jedną listę i pokaże, których brakuje.")
                    Text(
                        "Google Authenticator nie pozwala robić zrzutów ekranu z kodami. Jeśli jest na tym telefonie, sfotografuj kody innym telefonem i wybierz zdjęcia.",
                        style = T.sub, color = C.text2, modifier = Modifier.padding(top = 6.dp, bottom = 14.dp),
                    )
                    PillButton("Skanuj kody aparatem", onClick = { scanning = true }, icon = Ic.Scan, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    PillButton("Wybierz zdjęcia kodów", onClick = {
                        AppLock.allowExternal()
                        photos.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }, primary = false, icon = Ic.Image, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    PillButton("Otwórz Google Authenticator", onClick = { launch(GOOGLE_AUTH, "Google Authenticator") }, primary = false, icon = Ic.External, modifier = Modifier.fillMaxWidth())
                }
            }
            Spacer(Modifier.height(24.dp))
        }

        item(key = "microsoft") {
            SectionLabel("Microsoft Authenticator")
            Group {
                Column(Modifier.padding(horizontal = 20.dp, vertical = 14.dp)) {
                    Text(
                        "Microsoft nie pozwala wyeksportować kodów 2FA do innej aplikacji - jego kopia zapasowa działa tylko w Microsoft Authenticator. Konta trzeba przepiąć po jednym:",
                        style = T.sub, color = C.text2, modifier = Modifier.padding(bottom = 10.dp),
                    )
                    Step(1, "Otwórz ustawienia bezpieczeństwa serwisu (lista poniżej) i wybierz „Aplikacja uwierzytelniająca” albo „Zmień aplikację”.")
                    Step(2, "Zeskanuj nowy kod QR w Ostoi i wpisz kod z Ostoi, żeby potwierdzić.")
                    Step(3, "Usuń konto z Microsoft Authenticator dopiero, gdy logowanie z kodem z Ostoi zadziała.")
                    Spacer(Modifier.height(10.dp))
                    PillButton("Zeskanuj nowy kod", onClick = { nav.push(Route.Scan) }, icon = Ic.Scan, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    PillButton("Otwórz Microsoft Authenticator", onClick = { launch(MS_AUTH, "Microsoft Authenticator") }, primary = false, icon = Ic.External, modifier = Modifier.fillMaxWidth())
                }
            }
            Spacer(Modifier.height(16.dp))
            Group {
                SECURITY_PAGES.forEachIndexed { i, (name, url) ->
                    if (i > 0) Divider()
                    SettingRow(name, "Ustawienia 2FA", onClick = { open(url) }, trailing = {
                        Icon(Ic.External, null, tint = C.text2, modifier = Modifier.size(18.dp))
                    })
                }
            }
            Text(
                "Hasła zapisane w Microsoft Authenticator wyeksportujesz tam do pliku CSV i wczytasz w Ostoi: Ustawienia → Importuj z innej aplikacji. Plik CSV usuń po imporcie.",
                style = T.sub, color = C.text2, modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 12.dp),
            )
        }
    }
}

@Composable
private fun Step(n: Int, text: String) {
    Row(Modifier.padding(vertical = 5.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(24.dp).clip(CircleShape).background(C.accent), contentAlignment = Alignment.Center) {
            Text("$n", style = T.small, color = androidx.compose.ui.graphics.Color.White)
        }
        Text(text, style = T.body, color = C.text, modifier = Modifier.weight(1f))
    }
}
