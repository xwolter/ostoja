package pl.wolter.ostoja.desktop

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Notification
import androidx.compose.ui.window.Tray
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberTrayState
import androidx.compose.ui.window.rememberWindowState
import pl.wolter.ostoja.sync.SyncProtocol
import java.awt.Dimension
import java.io.File
import java.net.URL
import kotlin.system.exitProcess

/** Folder danych: %APPDATA%\Ostoja (albo --data=ścieżka do testów). */
private fun dataDir(args: Array<String>): File {
    val custom = args.firstOrNull { it.startsWith("--data=") }?.substringAfter('=')
    val base = custom?.let(::File) ?: File(System.getenv("APPDATA") ?: System.getProperty("user.home"), "Ostoja")
    return base.apply { mkdirs() }
}

/** Wersja z instalatora (jpackage); przy uruchomieniu z Gradle - „dev”. */
val APP_VERSION: String = System.getProperty("jpackage.app-version") ?: "dev"

/**
 * Po aktualizacji może jeszcze działać stara kopia (w zasobniku). Jeśli ma inną wersję, prosimy ją
 * o zamknięcie i zajmujemy jej miejsce. Wersje sprzed 1.2.1 nie znają tej prośby - wtedy mówimy wprost,
 * co zrobić, zamiast po cichu pokazywać okno starej wersji.
 */
private fun takeOver(server: SyncServer): Boolean {
    val base = "http://127.0.0.1:${SyncProtocol.PORT}"
    val running = runCatching { URL("$base/local/version").readText().trim() }.getOrNull()
    if (running == APP_VERSION) return false
    val asked = runCatching {
        val c = URL("$base/local/quit").openConnection() as java.net.HttpURLConnection
        c.requestMethod = "POST"; c.connectTimeout = 2000; c.readTimeout = 2000
        c.responseCode == 204
    }.getOrDefault(false)
    if (asked) repeat(40) { Thread.sleep(250); if (server.start()) return true }
    javax.swing.JOptionPane.showMessageDialog(
        null,
        "Działa jeszcze starsza wersja Ostoi.\nZamknij ją: prawy przycisk na ikonie Ostoi obok zegara → Zakończ,\na potem uruchom Ostoję ponownie.",
        "Ostoja $APP_VERSION", javax.swing.JOptionPane.INFORMATION_MESSAGE,
    )
    exitProcess(0)
}

fun main(args: Array<String>) {
    val dir = dataDir(args)
    val store = Store(dir).apply { printPairing = "--print-pair" in args }
    Favicons.dir = File(dir, "icons").apply { mkdirs() }
    var visible by mutableStateOf("--tray" !in args)
    val ext = ExtBridge(store) { visible = true }
    val server = SyncServer(store, ext, onShow = { visible = true }, onQuit = { exitProcess(0) })
    if (!server.start() && !takeOver(server)) {
        // Port zajęty przez tę samą wersję - Ostoja już działa (np. w zasobniku). Pokazujemy tamto okno i kończymy.
        runCatching { URL("http://127.0.0.1:${SyncProtocol.PORT}/local/show").openStream().close() }
        exitProcess(0)
    }

    application {
        val ui = remember { Ui() }
        val tray = rememberTrayState()
        val icon = painterResource("icon.png")
        var autostart by remember { mutableStateOf(Autostart.available && Autostart.enabled()) }
        var hintShown by remember { mutableStateOf(false) }

        fun quit() { server.stop(); exitApplication() }

        Tray(
            icon = icon,
            state = tray,
            tooltip = "Ostoja " + (System.getProperty("jpackage.app-version") ?: "") + " - synchronizacja z telefonem",
            onAction = { visible = true },
            menu = {
                Item("Otwórz Ostoję", onClick = { visible = true })
                Item("Zablokuj", onClick = { store.lockNow() })
                if (Autostart.available) CheckboxItem("Uruchamiaj z Windows", checked = autostart) {
                    Autostart.set(it); autostart = Autostart.enabled()
                }
                Separator()
                Item("Zakończ", onClick = ::quit)
            },
        )

        val state = rememberWindowState(size = DpSize(1280.dp, 880.dp), position = WindowPosition(Alignment.Center))
        Window(
            onCloseRequest = {
                // Zamknięcie okna chowa je do zasobnika - telefon nadal może się synchronizować.
                visible = false
                if (!hintShown) {
                    hintShown = true
                    tray.sendNotification(Notification("Ostoja działa w tle", "Telefon nadal synchronizuje się z komputerem. Zakończysz ją z ikony obok zegara.", Notification.Type.Info))
                }
            },
            visible = visible,
            state = state,
            title = "Ostoja",
            icon = icon,
            onPreviewKeyEvent = { e ->
                if (e.type == KeyEventType.KeyDown && e.isCtrlPressed && e.key == Key.K) {
                    ui.go(V_VAULT); runCatching { ui.searchFocus.requestFocus() }; true
                } else false
            },
        ) {
            LaunchedEffect(Unit) { window.minimumSize = Dimension(980, 680) }
            LaunchedEffect(visible) { if (visible) { window.toFront(); window.requestFocus() } }
            val dark = isSystemInDarkTheme()
            // Pasek tytułu w kolorze tła aplikacji - okno wygląda jak jedna całość.
            LaunchedEffect(dark) {
                val p = palette(dark, ACCENTS[0])
                window.background = java.awt.Color(p.bg.toArgb())
                WinFrame.style(window, dark, p.bg, p.text, if (dark) Color(0xFF2A2C33) else Color(0xFFD9DBE0))
            }
            App(store, ui, dark, ext)
        }
    }
}
