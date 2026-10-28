package pl.wolter.ostoja.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.launch
import pl.wolter.ostoja.security.AppLock
import pl.wolter.ostoja.sync.Computer
import pl.wolter.ostoja.sync.Link
import pl.wolter.ostoja.sync.PairInfo
import pl.wolter.ostoja.sync.Sync
import pl.wolter.ostoja.ui.Ic
import pl.wolter.ostoja.ui.Nav
import pl.wolter.ostoja.ui.TAB_SCAN
import pl.wolter.ostoja.ui.components.Divider
import pl.wolter.ostoja.ui.components.Group
import pl.wolter.ostoja.ui.components.LocalToaster
import pl.wolter.ostoja.ui.components.OneDialog
import pl.wolter.ostoja.ui.components.PillButton
import pl.wolter.ostoja.ui.components.SectionLabel
import pl.wolter.ostoja.ui.components.SettingRow
import pl.wolter.ostoja.ui.components.SwitchRow
import pl.wolter.ostoja.ui.components.Toaster
import pl.wolter.ostoja.ui.theme.C
import pl.wolter.ostoja.ui.theme.T

/** Sekcja „Komputer” w Ustawieniach: parowanie z Ostoją na Windows i sparowane komputery. */
@Composable
fun ComputerSection(nav: Nav) {
    val ctx = LocalContext.current
    val computers by Sync.computers.collectAsState()
    val links by Sync.links.collectAsState()
    val live by Sync.live.collectAsState()
    var sheetFor by remember { mutableStateOf<Computer?>(null) }

    SectionLabel("Komputer")
    Group {
        SettingRow("Połącz z komputerem", "Zeskanuj kod z aplikacji Ostoja na Windows", icon = Ic.Qr, titleColor = C.accent, onClick = { nav.tab = TAB_SCAN })
        computers.forEach { c ->
            Divider()
            val state = when (links[c.id]) {
                Link.SYNCING -> "Synchronizuję…"
                Link.ONLINE -> "Połączono · " + agoPl(c.lastSync)
                else -> "Poza zasięgiem · ostatnio " + agoPl(c.lastSync)
            }
            SettingRow(c.name, state, icon = Ic.Laptop, subtitleColor = if (links[c.id] == Link.OFFLINE || links[c.id] == null) C.text2 else C.accent, onClick = { sheetFor = c })
        }
        if (computers.isNotEmpty()) {
            Divider()
            SwitchRow("Prośby logowania od razu", if (live) "Stałe połączenie w tle (cicha ikona w powiadomieniach)" else "Synchronizacja tylko przy otwartej Ostoi", live, { Sync.setLive(ctx, it) })
        }
    }

    sheetFor?.let { c -> ComputerSheet(c) { sheetFor = null } }
}

private fun agoPl(t: Long): String {
    val s = (System.currentTimeMillis() - t) / 1000
    return when {
        t <= 0 -> "nigdy"
        s < 10 -> "przed chwilą"
        s < 60 -> "$s s temu"
        s < 3600 -> "${s / 60} min temu"
        s < 86400 -> "${s / 3600} godz. temu"
        else -> "${s / 86400} d. temu"
    }
}

@Composable
private fun ComputerSheet(c: Computer, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val activity = ctx as FragmentActivity
    val toaster = LocalToaster.current
    val scope = rememberCoroutineScope()
    var confirm by remember { mutableStateOf(false) }
    OneSheet(onClose) {
        Text(c.name, style = T.title, color = C.text, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
        Text(
            "Ostoja na Windows · w tej samej sieci Wi‑Fi. Wpisy, kody 2FA i usunięcia synchronizują się w obie strony, szyfrowane kluczem z parowania.",
            style = T.sub, color = C.text2, modifier = Modifier.padding(horizontal = 24.dp),
        )
        Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PillButton("Synchronizuj teraz", onClick = {
                scope.launch { toaster.show(if (Sync.syncNow(c)) "Zsynchronizowano z ${c.name}" else "${c.name} jest poza zasięgiem") }
                onClose()
            }, modifier = Modifier.weight(1f))
            PillButton("Rozłącz", onClick = { confirm = true }, primary = false, modifier = Modifier.weight(1f))
        }
    }
    if (confirm) OneDialog(
        title = "Rozłączyć ${c.name}?",
        text = "Komputer usunie swoją kopię sejfu (jeśli jest w zasięgu) i przestanie się synchronizować. Na telefonie nic nie zniknie.",
        confirm = "Rozłącz", danger = true,
        onConfirm = {
            confirm = false
            AppLock.prompt(activity, "Rozłączyć komputer?", c.name) { ok ->
                if (ok) scope.launch { Sync.unpair(c); toaster.show("Rozłączono ${c.name}"); onClose() }
            }
        },
        onDismiss = { confirm = false },
    )
}

/** Potwierdzenie parowania po zeskanowaniu kodu z komputera: dialog → odcisk palca → połączenie. */
@Composable
fun PairDialog(activity: FragmentActivity, uri: String, toaster: Toaster, onDone: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val info = remember(uri) { runCatching { PairInfo.parse(uri) }.getOrNull() }
    var busy by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf<String?>(null) }
    val notif = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    if (info == null) { toaster.show("Uszkodzony kod parowania"); onDone(); return }

    fun connect() {
        busy = true
        scope.launch {
            try {
                val name = Sync.pair(ctx, uri)
                toaster.show("Połączono z $name")
                // Prośby logowania przychodzą jako powiadomienia - pytamy o zgodę od razu.
                if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                    AppLock.allowExternal(); notif.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
                onDone()
            } catch (e: Exception) {
                err = e.message ?: "Nie udało się połączyć"
                busy = false
            }
        }
    }

    OneDialog(
        title = "Połączyć z ${info.computerName}?",
        confirm = if (busy) "Łączę…" else "Połącz",
        onConfirm = {
            if (busy) return@OneDialog
            if (!AppLock.canAuthenticate(ctx)) connect()
            else AppLock.prompt(activity, "Połącz z komputerem", info.computerName) { ok -> if (ok) connect() }
        },
        onDismiss = { if (!busy) onDone() },
    ) {
        Text(
            "Komputer dostanie zaszyfrowaną kopię sejfu i będzie go synchronizować z telefonem w tej samej sieci Wi‑Fi. Bez chmury i bez konta.",
            style = T.sub, color = C.text2,
        )
        if (err != null) {
            Spacer(Modifier.height(10.dp))
            Text(err!!, style = T.sub, color = C.danger)
        }
    }
}
