package pl.wolter.ostoja.ui.screens

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.view.autofill.AutofillManager
import androidx.activity.compose.rememberLauncherForActivityResult
import pl.wolter.ostoja.ui.theme.Motion
import androidx.compose.ui.unit.sp
import androidx.compose.animation.core.animateDpAsState
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pl.wolter.ostoja.R
import pl.wolter.ostoja.app
import pl.wolter.ostoja.data.AppSettings
import pl.wolter.ostoja.data.BackupCipher
import pl.wolter.ostoja.data.CsvImport
import pl.wolter.ostoja.data.Demo
import pl.wolter.ostoja.data.OtpAccount
import pl.wolter.ostoja.data.OtpUri
import pl.wolter.ostoja.data.SettingsStore
import pl.wolter.ostoja.data.Vault
import pl.wolter.ostoja.data.VaultData
import pl.wolter.ostoja.data.countLabel
import pl.wolter.ostoja.security.AppLock
import pl.wolter.ostoja.ui.Ic
import pl.wolter.ostoja.ui.Nav
import pl.wolter.ostoja.ui.components.Divider
import pl.wolter.ostoja.ui.components.Field
import pl.wolter.ostoja.ui.components.Group
import pl.wolter.ostoja.ui.components.LocalToaster
import pl.wolter.ostoja.ui.components.OneDialog
import pl.wolter.ostoja.ui.components.OneUiPage
import pl.wolter.ostoja.ui.components.PillButton
import pl.wolter.ostoja.ui.components.SectionLabel
import pl.wolter.ostoja.ui.components.SettingRow
import pl.wolter.ostoja.ui.components.SwitchRow
import pl.wolter.ostoja.ui.components.press
import pl.wolter.ostoja.ui.components.rowPress
import pl.wolter.ostoja.ui.theme.C
import pl.wolter.ostoja.ui.theme.T
import pl.wolter.ostoja.widget.CodeWidget
import pl.wolter.ostoja.widget.CodesWidget
import pl.wolter.ostoja.widget.QuickWidget
import pl.wolter.ostoja.widget.SecurityWidget
import pl.wolter.ostoja.widget.Widgets
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private sealed interface Pick {
    data class Choice(val title: String, val options: List<String>, val selected: Int, val onPick: (Int) -> Unit) : Pick
}

@Composable
fun SettingsScreen(nav: Nav, vault: VaultData, s: AppSettings) {
    val ctx = LocalContext.current
    val activity = ctx as FragmentActivity
    val toaster = LocalToaster.current
    val scope = rememberCoroutineScope()
    val store = ctx.app.settings
    var pick by remember { mutableStateOf<Pick.Choice?>(null) }
    var exportAsk by remember { mutableStateOf(false) }
    var exportPw by remember { mutableStateOf("") }
    var importBlob by remember { mutableStateOf<ByteArray?>(null) }
    var wipeAsk by remember { mutableStateOf(false) }
    var widgetsSheet by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var resumeTick by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { resumeTick++ }

    val canLock = remember(resumeTick) { AppLock.canAuthenticate(ctx) }
    val autofillOn = remember(resumeTick) {
        runCatching { ctx.getSystemService(AutofillManager::class.java)?.hasEnabledAutofillServices() == true }.getOrDefault(false)
    }
    val autofillSupported = remember { runCatching { ctx.getSystemService(AutofillManager::class.java)?.isAutofillSupported == true }.getOrDefault(false) }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val pw = exportPw
        exportPw = ""
        if (uri == null) return@rememberLauncherForActivityResult
        busy = true
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    val blob = BackupCipher.encrypt(ctx.app.vault.exportJson(), pw.toCharArray())
                    ctx.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(blob) }
                }.isSuccess
            }
            busy = false
            toaster.show(if (ok) "Zapisano zaszyfrowaną kopię" else "Nie udało się zapisać kopii")
        }
    }

    fun importText(text: String) {
        val v = ctx.app.vault
        if (text.contains("otpauth://") || text.contains("otpauth-migration://")) {
            val accounts = mutableListOf<OtpAccount>()
            Regex("otpauth(-migration)?://\\S+").findAll(text).forEach { m -> runCatching { accounts += OtpUri.parse(m.value) } }
            val added = v.addOtps(accounts)
            toaster.show("Zaimportowano " + countLabel(added.size, "konto 2FA", "konta 2FA", "kont 2FA"))
            return
        }
        val logins = CsvImport.parse(text)
        if (logins.isEmpty()) { toaster.show("Nie rozpoznano formatu pliku"); return }
        val known = v.value.logins.map { Triple(it.title.lowercase(), it.username, it.password) }.toSet()
        val fresh = logins.filter { Triple(it.title.lowercase(), it.username, it.password) !in known }
        v.update { d -> d.copy(logins = d.logins + fresh) }
        toaster.show("Zaimportowano " + countLabel(fresh.size, "hasło", "hasła", "haseł"))
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val bytes = runCatching { ctx.contentResolver.openInputStream(uri)!!.use { it.readBytes() } }.getOrNull()
        when {
            bytes == null -> toaster.show("Nie udało się odczytać pliku")
            BackupCipher.isBackup(bytes) -> importBlob = bytes
            else -> importText(String(bytes))
        }
    }

    OneUiPage(title = "Ustawienia", subtitle = "Bezpieczeństwo, kopia, wygląd", state = rememberLazyListState()) {
        item(key = "pc") {
            ComputerSection(nav)
            Spacer(Modifier.height(28.dp))
        }

        item(key = "sec") {
            SectionLabel("Bezpieczeństwo")
            Group {
                SwitchRow(
                    "Autouzupełnianie",
                    when {
                        !autofillSupported -> "Niedostępne na tym urządzeniu"
                        autofillOn -> "Wpisuj hasła i kody w aplikacjach"
                        else -> "Dotknij, aby wybrać Ostoję jako usługę"
                    },
                    autofillOn,
                    {
                        AppLock.allowExternal()
                        runCatching {
                            ctx.startActivity(Intent(Settings.ACTION_REQUEST_SET_AUTOFILL_SERVICE, Uri.parse("package:${ctx.packageName}")))
                        }.onFailure { toaster.show("Ustawienia → Zarządzanie ogólne → Hasła i autouzupełnianie") }
                    },
                    enabled = autofillSupported,
                )
                Divider()
                SwitchRow(
                    "Odblokowanie biometryczne",
                    if (canLock) "Wymagaj odcisku palca przy otwieraniu" else "Ustaw blokadę ekranu w telefonie, aby włączyć",
                    s.biometricLock && canLock,
                    { v ->
                        if (v) store.update { it.copy(biometricLock = true) }
                        else AppLock.prompt(activity, "Wyłączyć blokadę?", "Potwierdź, że to Ty") { ok -> if (ok) store.update { it.copy(biometricLock = false) } }
                    },
                    enabled = canLock,
                )
                Divider()
                SwitchRow("Ukrywaj kody", "Kod widoczny dopiero po skopiowaniu", s.hideCodes, { v -> store.update { it.copy(hideCodes = v) } })
                Divider()
                val lockOpts = listOf(0, 30, 60, 300)
                val lockNames = listOf("Natychmiast", "Po 30 sekundach", "Po 1 minucie", "Po 5 minutach")
                SettingRow("Automatyczna blokada", lockNames[lockOpts.indexOf(s.autoLockSec).coerceAtLeast(0)], subtitleColor = C.accent,
                    enabled = s.biometricLock && canLock, onClick = {
                        pick = Pick.Choice("Automatyczna blokada", lockNames, lockOpts.indexOf(s.autoLockSec)) { i -> store.update { it.copy(autoLockSec = lockOpts[i]) } }
                    })
                Divider()
                val clipOpts = listOf(15, 30, 60, 0)
                val clipNames = listOf("Po 15 sekundach", "Po 30 sekundach", "Po 1 minucie", "Nigdy")
                SettingRow("Czyść schowek", clipNames[clipOpts.indexOf(s.clipClearSec).coerceAtLeast(0)], subtitleColor = C.accent, onClick = {
                    pick = Pick.Choice("Czyść skopiowane hasła", clipNames, clipOpts.indexOf(s.clipClearSec)) { i -> store.update { it.copy(clipClearSec = clipOpts[i]) } }
                })
                Divider()
                SwitchRow("Sprawdzaj wycieki haseł", "Have I Been Pwned · wysyłane jest tylko 5 znaków skrótu", s.leakCheck, { v -> store.update { it.copy(leakCheck = v) } })
                Divider()
                SwitchRow("Blokuj zrzuty ekranu", "Ukrywa też podgląd w ostatnich aplikacjach", s.secureScreen, { v -> store.update { it.copy(secureScreen = v) } })
                if (s.biometricLock && canLock) {
                    Divider()
                    SettingRow("Zablokuj teraz", null, titleColor = C.accent, onClick = { AppLock.lockNow() })
                }
            }
        }

        item(key = "backup") {
            Spacer(Modifier.height(28.dp))
            SectionLabel("Kopia zapasowa")
            Group {
                SettingRow("Eksportuj konta", "Plik .ostoja zaszyfrowany Twoim hasłem", onClick = { exportAsk = true })
                Divider()
                SettingRow("Przenieś kody 2FA", "Z Google Authenticator i Microsoft Authenticator", onClick = { nav.push(pl.wolter.ostoja.ui.Route.ImportOtp) })
                Divider()
                SettingRow("Importuj z innej aplikacji", "Kopia Ostoi, CSV z Chrome / Samsung Pass / Bitwarden / Microsoft Authenticator, otpauth://", onClick = {
                    AppLock.allowExternal()
                    importLauncher.launch(arrayOf("*/*"))
                })
                Divider()
                SettingRow("Wczytaj przykładowe dane", "Wpisy i konta 2FA z prototypu", onClick = {
                    ctx.app.vault.merge(Demo.data()); toaster.show("Dodano przykładowe dane")
                })
            }
        }

        item(key = "look") {
            Spacer(Modifier.height(28.dp))
            SectionLabel("Wygląd")
            Group {
                SwitchRow("Ikony serwisów", null, s.icons, { v -> store.update { it.copy(icons = v) } })
                Divider()
                SwitchRow("Dziel kod na trójki", "123 456 zamiast 123456", s.groupDigits, { v -> store.update { it.copy(groupDigits = v) }; Widgets.updateAll(ctx) })
                Divider()
                SettingRow("Motyw", listOf("Zgodnie z systemem", "Jasny", "Ciemny")[s.theme], subtitleColor = C.accent, onClick = {
                    pick = Pick.Choice("Motyw", listOf("Zgodnie z systemem", "Jasny", "Ciemny"), s.theme) { i -> store.update { it.copy(theme = i) } }
                })
                Divider()
                Column(Modifier.padding(start = 24.dp, end = 20.dp, top = 16.dp, bottom = 18.dp)) {
                    Text("Kolor akcentu", style = T.body, color = C.text)
                    Text(SettingsStore.ACCENT_NAMES[s.accent], style = T.sub.copy(fontSize = 14.sp), color = C.accent, modifier = Modifier.padding(top = 3.dp))
                    Row(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        SettingsStore.ACCENTS.forEachIndexed { i, c ->
                            val sel = i == s.accent
                            val ring by animateDpAsState(if (sel) 2.dp else 0.dp, Motion.toggle(), label = "acc$i")
                            Box(
                                Modifier
                                    .press { store.update { it.copy(accent = i) }; Widgets.updateAll(ctx) }
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .border(ring, C.text, CircleShape)
                                    .padding(5.dp)
                                    .clip(CircleShape)
                                    .background(Color(c)),
                                contentAlignment = Alignment.Center,
                            ) { if (sel) Icon(Ic.Check, null, tint = Color.White, modifier = Modifier.size(16.dp)) }
                        }
                    }
                }
                Divider()
                SwitchRow("Chowaj kapsułę przy przewijaniu", null, s.hideOnScroll, { v -> store.update { it.copy(hideOnScroll = v) } })
            }
        }

        item(key = "widgets") {
            Spacer(Modifier.height(28.dp))
            SectionLabel("Widżety i powiadomienia")
            Group {
                SettingRow("Dodaj widżet", "Kody, kod, skaner, kondycja sejfu", onClick = { widgetsSheet = true })
                Divider()
                SwitchRow("Kody w widżetach", if (s.widgetCodes) "Dotknięcie widżetu kopiuje kod" else "Widżety pokazują ••• •••", s.widgetCodes, { v ->
                    store.update { it.copy(widgetCodes = v) }; Widgets.updateAll(ctx)
                })
                Divider()
                SwitchRow("Kod na ekranie blokady", "Przypięty kod widoczny bez odblokowania", s.lockscreenCode, { v ->
                    store.update { it.copy(lockscreenCode = v) }
                })
            }
        }

        item(key = "about") {
            Spacer(Modifier.height(28.dp))
            Group {
                SettingRow("Informacje o aplikacji", "Wersja ${pl.wolter.ostoja.BuildConfig.VERSION_NAME} · xwolter · " + countLabel(vault.logins.size, "wpis", "wpisy", "wpisów") + ", " + countLabel(vault.otps.size, "kod", "kody", "kodów") + " 2FA")
                Divider()
                SettingRow("Usuń wszystkie dane", null, titleColor = C.danger, onClick = { wipeAsk = true })
            }
        }
    }

    pick?.let { p ->
        OneSheet({ pick = null }) {
            Text(p.title, style = T.title, color = C.text, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
            p.options.forEachIndexed { i, o ->
                Row(
                    Modifier.fillMaxWidth().rowPress { p.onPick(i); pick = null }.padding(horizontal = 24.dp, vertical = 15.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(o, style = T.body, color = if (i == p.selected) C.accent else C.text, modifier = Modifier.weight(1f))
                    if (i == p.selected) Icon(Ic.Check, null, tint = C.accent)
                }
            }
        }
    }

    if (exportAsk) {
        var a by remember { mutableStateOf("") }
        var b by remember { mutableStateOf("") }
        var err by remember { mutableStateOf<String?>(null) }
        OneDialog(
            title = "Hasło do kopii",
            confirm = "Dalej",
            onConfirm = {
                when {
                    a.length < 8 -> err = "Co najmniej 8 znaków"
                    a != b -> err = "Hasła się różnią"
                    else -> {
                        exportPw = a
                        exportAsk = false
                        AppLock.allowExternal()
                        exportLauncher.launch("ostoja-" + SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(Date()) + ".ostoja")
                    }
                }
            },
            onDismiss = { exportAsk = false },
        ) {
            Text("Bez tego hasła kopii nie da się odczytać - zapisz je w bezpiecznym miejscu.", style = T.sub, color = C.text2)
            Spacer(Modifier.height(14.dp))
            Field("Hasło", a, { a = it; err = null }, password = true)
            Spacer(Modifier.height(10.dp))
            Field("Powtórz hasło", b, { b = it; err = null }, password = true, error = err)
        }
    }

    importBlob?.let { blob ->
        var pw by remember { mutableStateOf("") }
        var err by remember { mutableStateOf<String?>(null) }
        OneDialog(
            title = "Odszyfruj kopię",
            confirm = if (busy) "Chwila…" else "Importuj",
            onConfirm = {
                if (busy) return@OneDialog
                busy = true
                scope.launch {
                    val data = withContext(Dispatchers.Default) {
                        runCatching { Vault.decode(BackupCipher.decrypt(blob, pw.toCharArray())) }.getOrNull()
                    }
                    busy = false
                    if (data == null) err = "Złe hasło albo uszkodzony plik"
                    else {
                        ctx.app.vault.merge(data)
                        importBlob = null
                        toaster.show("Przywrócono " + countLabel(data.logins.size, "hasło", "hasła", "haseł") + " i " + countLabel(data.otps.size, "kod", "kody", "kodów"))
                    }
                }
            },
            onDismiss = { importBlob = null },
        ) {
            Text("Podaj hasło ustawione przy eksporcie. Wpisy zostaną dołączone do obecnego sejfu.", style = T.sub, color = C.text2)
            Spacer(Modifier.height(14.dp))
            Field("Hasło kopii", pw, { pw = it; err = null }, password = true, error = err)
        }
    }

    if (wipeAsk) OneDialog(
        title = "Usunąć wszystko?",
        text = "Wszystkie hasła i konta 2FA zostaną trwale usunięte z tego telefonu. Bez kopii zapasowej nie da się ich odzyskać.",
        confirm = "Usuń wszystko", danger = true,
        onConfirm = {
            wipeAsk = false
            AppLock.prompt(activity, "Usunąć wszystkie dane?", "Potwierdź tożsamość") { ok ->
                if (ok) { ctx.app.vault.replaceAll(VaultData()); toaster.show("Sejf jest pusty") }
            }
        },
        onDismiss = { wipeAsk = false },
    )

    if (widgetsSheet) OneSheet({ widgetsSheet = false }) {
        Text("Dodaj widżet", style = T.title, color = C.text, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
        Text(
            "Widżety pokażą się na ekranie głównym. Możesz też przytrzymać pusty fragment ekranu → Widżety → Ostoja.",
            style = T.sub, color = C.text2, modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
        )
        val awm = AppWidgetManager.getInstance(ctx)
        listOf(
            Triple("Ostoja · kody", "4×2 · trzy konta z ikonami i odliczaniem", CodesWidget::class.java),
            Triple("Kod 2FA", "2×2 · jeden duży kod z pierścieniem", CodeWidget::class.java),
            Triple("Skanuj kod QR", "2×2 · kafel w kolorze akcentu", pl.wolter.ostoja.widget.ScanWidget::class.java),
            Triple("Kondycja sejfu", "4×1 · wynik i hasła do poprawy", SecurityWidget::class.java),
            Triple("Szybki dostęp", "4×1 · szukaj, skanuj, generuj", QuickWidget::class.java),
        ).forEach { (name, desc, cls) ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(name, style = T.row, color = C.text)
                    Text(desc, style = T.sub, color = C.text2)
                }
                PillButton("Dodaj", height = 40.dp, onClick = {
                    val ok = awm.isRequestPinAppWidgetSupported && awm.requestPinAppWidget(ComponentName(ctx, cls), null, null)
                    if (!ok) toaster.show("Przytrzymaj ekran główny → Widżety → Ostoja")
                    widgetsSheet = false
                })
            }
        }
        Spacer(Modifier.width(1.dp))
    }
}
