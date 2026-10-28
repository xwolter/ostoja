package pl.wolter.ostoja.ui

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import pl.wolter.ostoja.MainActivity
import pl.wolter.ostoja.app
import pl.wolter.ostoja.data.LeakCheck
import pl.wolter.ostoja.data.OtpUri
import pl.wolter.ostoja.data.countLabel
import pl.wolter.ostoja.notify.Alerts
import pl.wolter.ostoja.security.AppLock
import pl.wolter.ostoja.ui.components.Avatar
import pl.wolter.ostoja.ui.components.Capsule
import pl.wolter.ostoja.ui.components.LocalNavChrome
import pl.wolter.ostoja.ui.components.LocalSettings
import pl.wolter.ostoja.ui.components.LocalToaster
import pl.wolter.ostoja.ui.components.NavChrome
import pl.wolter.ostoja.ui.components.OneDialog
import pl.wolter.ostoja.ui.components.TabItem
import pl.wolter.ostoja.ui.components.ToastHost
import pl.wolter.ostoja.ui.components.Toaster
import pl.wolter.ostoja.ui.screens.ApproveSheet
import pl.wolter.ostoja.ui.screens.CodesScreen
import pl.wolter.ostoja.ui.screens.PairDialog
import pl.wolter.ostoja.sync.Sync
import androidx.compose.runtime.mutableStateOf
import pl.wolter.ostoja.ui.screens.EditLoginScreen
import pl.wolter.ostoja.ui.screens.EditOtpScreen
import pl.wolter.ostoja.ui.screens.ImportOtpScreen
import pl.wolter.ostoja.ui.screens.GeneratorSheet
import pl.wolter.ostoja.ui.screens.ItemScreen
import pl.wolter.ostoja.ui.screens.LockScreen
import pl.wolter.ostoja.ui.screens.ScanScreen
import pl.wolter.ostoja.ui.screens.SettingsScreen
import pl.wolter.ostoja.ui.screens.VaultScreen
import pl.wolter.ostoja.ui.theme.C
import pl.wolter.ostoja.ui.theme.OstojaTheme
import pl.wolter.ostoja.ui.theme.T

/** Zakładki kapsuły z prototypu: Sejf · Kody · Skaner · Ustawienia. */
private val TABS = listOf(
    TabItem("Sejf", Ic.Padlock),
    TabItem("Kody", Ic.Clock),
    TabItem("Skaner", Ic.ScanTab),
    TabItem("Ustawienia", Ic.Sliders),
)
const val TAB_VAULT = 0
const val TAB_CODES = 1
const val TAB_SCAN = 2
const val TAB_SETTINGS = 3

@Composable
fun AppRoot(activity: MainActivity) {
    val app = activity.app
    val settings by app.settings.flow.collectAsState()
    val locked by AppLock.locked.collectAsState()
    val nav = remember { Nav() }
    val toaster = remember { Toaster() }
    val chrome = remember { NavChrome() }
    chrome.hideOnScroll = settings.hideOnScroll

    OstojaTheme(settings) {
        val dark = C.dark
        LaunchedEffect(dark) { activity.applySystemBars(dark) }
        LaunchedEffect(settings.secureScreen) { activity.applySecure(settings.secureScreen) }

        // Skróty, widżety, linki otpauth://
        val pending = activity.pendingIntent
        LaunchedEffect(pending.value, locked) {
            val intent = pending.value ?: return@LaunchedEffect
            if (locked) return@LaunchedEffect
            pending.value = null
            handleIntent(intent, nav, toaster)
        }

        // Sprawdzanie wycieków raz na dobę, po odblokowaniu.
        LaunchedEffect(locked, settings.leakCheck) {
            if (locked || !settings.leakCheck) return@LaunchedEffect
            if (System.currentTimeMillis() - app.settings.value.lastLeakCheck < 24L * 3600 * 1000) return@LaunchedEffect
            val fresh = LeakCheck.run(app.vault)
            app.settings.update { it.copy(lastLeakCheck = System.currentTimeMillis()) }
            if (fresh.isNotEmpty()) Alerts.leaked(activity, fresh)
        }

        CompositionLocalProvider(LocalToaster provides toaster, LocalNavChrome provides chrome, LocalSettings provides settings) {
            Box(Modifier.fillMaxSize().background(C.bg)) {
                Box(Modifier.fillMaxSize().then(if (locked) Modifier.clearAndSetSemantics { } else Modifier)) {
                    Main(nav, chrome, toaster)
                }
                AnimatedVisibility(locked, enter = fadeIn(tween(120)), exit = fadeOut(tween(260))) {
                    LockScreen(onUnlock = { AppLock.unlock(activity) })
                }
            }
        }

        nav.importBatch?.let { batch ->
            OneDialog(
                title = "Zaimportować ${countLabel(batch.size, "konto", "konta", "kont")}?",
                text = "Znaleziono w kodzie migracji:",
                confirm = "Importuj",
                onConfirm = {
                    val added = app.vault.addOtps(batch)
                    nav.importBatch = null
                    nav.reset()
                    nav.tab = TAB_CODES
                    toaster.show(if (added.isEmpty()) "Wszystkie konta już były w Ostoi" else "Dodano " + countLabel(added.size, "konto", "konta", "kont"))
                },
                onDismiss = { nav.importBatch = null },
            ) {
                Spacer(Modifier.height(10.dp))
                batch.take(8).forEach { a ->
                    Row(Modifier.padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                        Avatar(a.title, size = 30.dp)
                        Column(Modifier.padding(start = 10.dp)) {
                            Text(a.title, style = T.body, color = C.text)
                            if (a.label.isNotBlank()) Text(a.label, style = T.small, color = C.text2)
                        }
                    }
                }
                if (batch.size > 8) Text("…i ${batch.size - 8} więcej", style = T.small, color = C.text2)
            }
        }

        // Zdarzenia synchronizacji (np. komputer odłączył telefon) jako toast.
        LaunchedEffect(Unit) { Sync.events.collect { toaster.show(it) } }

        nav.pairUri?.let { uri -> PairDialog(activity, uri, toaster) { nav.pairUri = null } }

        // Prośba „Czy to Ty logujesz się na komputerze?” - arkusz nad wszystkim, z własnym odciskiem palca.
        val incoming by Sync.request.collectAsState()
        var shownFor by remember { mutableStateOf<String?>(null) }
        incoming?.let { inc ->
            if (shownFor != "closed:" + inc.request.id) ApproveSheet(inc) { shownFor = "closed:" + inc.request.id }
        }
    }
}

@Composable
private fun Main(nav: Nav, chrome: NavChrome, toaster: Toaster) {
    val ctx = LocalContext.current
    val app = ctx.app
    val settings by app.settings.flow.collectAsState()
    val vault by app.vault.data.collectAsState()
    var prevTab by remember { mutableIntStateOf(TAB_CODES) }

    fun selectTab(i: Int) {
        if (i == TAB_SCAN && nav.tab != TAB_SCAN) prevTab = nav.tab
        nav.stack.clear()
        nav.tab = i
        chrome.hidden = false
    }

    BackHandler(enabled = nav.stack.isNotEmpty()) { nav.pop() }
    BackHandler(enabled = nav.stack.isEmpty() && nav.tab == TAB_SCAN) { nav.tab = prevTab }
    BackHandler(enabled = nav.stack.isEmpty() && nav.tab != TAB_VAULT && nav.tab != TAB_SCAN) { nav.tab = TAB_VAULT }

    Box(Modifier.fillMaxSize()) {
        // Zakładki: krótki fade, jak przełączenie ekranu w prototypie (przewinięcie na górę).
        AnimatedContent(
            targetState = nav.tab,
            transitionSpec = { fadeIn(tween(200, delayMillis = 40)) togetherWith fadeOut(tween(120)) },
            label = "tabs",
        ) { tab ->
            when (tab) {
                TAB_VAULT -> VaultScreen(nav, vault, settings)
                TAB_CODES -> CodesScreen(nav, vault, settings)
                TAB_SCAN -> ScanScreen(
                    onClose = { nav.tab = prevTab },
                    onManual = { nav.push(Route.EditOtp(null)) },
                    onResult = { list ->
                        if (list.size == 1) nav.push(Route.EditOtp(null, list.first()))
                        else { nav.tab = TAB_CODES; nav.importBatch = list }
                    },
                    onPair = { uri -> nav.tab = TAB_SETTINGS; nav.pairUri = uri },
                )
                else -> SettingsScreen(nav, vault, settings)
            }
        }

        // Ekrany nałożone (szczegóły, edycja, skaner z poziomu wpisu) wjeżdżają z prawej.
        var depth by remember { mutableIntStateOf(0) }
        val forward = nav.stack.size >= depth
        LaunchedEffect(nav.stack.size) { depth = nav.stack.size }
        AnimatedContent(
            targetState = nav.top,
            transitionSpec = {
                if (forward) (slideInHorizontally(tween(320)) { it } + fadeIn(tween(200))) togetherWith fadeOut(tween(250))
                else fadeIn(tween(200)) togetherWith (slideOutHorizontally(tween(300)) { it } + fadeOut(tween(250)))
            },
            contentKey = { r -> r?.let { it::class.simpleName + (it as? Route.Item)?.id + (it as? Route.EditLogin)?.id + (it as? Route.EditOtp)?.id } },
            label = "routes",
        ) { r ->
            when (r) {
                null -> Box(Modifier)
                is Route.Item -> ItemScreen(r.id, nav, vault, settings)
                is Route.EditLogin -> EditLoginScreen(r.id, nav, vault)
                is Route.EditOtp -> EditOtpScreen(r.id, r.prefill, r.linkTo, nav, vault)
                Route.ImportOtp -> ImportOtpScreen(nav, vault)
                Route.Scan -> ScanScreen(
                    onClose = { nav.pop() },
                    onManual = { nav.replaceTop(Route.EditOtp(null, linkTo = nav.scanLinkTo)) },
                    onResult = { list ->
                        val link = nav.scanLinkTo
                        nav.scanLinkTo = null
                        if (list.size == 1) nav.replaceTop(Route.EditOtp(null, list.first(), link))
                        else { nav.pop(); nav.importBatch = list }
                    },
                )
            }
        }

        val top = nav.top
        val scanning = nav.tab == TAB_SCAN || top is Route.Scan
        // Na szczegółach wpisu kapsuła zostaje (jak w prototypie), przy edycji i skanerze znika.
        val gone = scanning || top is Route.EditLogin || top is Route.EditOtp || top is Route.ImportOtp
        Capsule(
            tabs = TABS,
            selected = if (top is Route.Item) TAB_VAULT else nav.tab,
            onSelect = { selectTab(it) },
            hidden = chrome.hidden,
            gone = gone,
            modifier = Modifier.align(Alignment.BottomCenter),
        )

        val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        ToastHost(
            toaster,
            bottom = navBottom + when { gone -> 40.dp; chrome.hidden -> 96.dp; else -> 108.dp },
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }

    if (nav.generator) GeneratorSheet(onDismiss = { nav.generator = false })
}

private fun handleIntent(intent: Intent, nav: Nav, toaster: Toaster) {
    // Tylko w wersji testowej: parowanie bez aparatu (emulator) - adb … -a pl.wolter.ostoja.PAIR --es uri ostoja://pair?…
    val debuggable = (pl.wolter.ostoja.OstojaApp.instance.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
    if (debuggable && intent.action == "pl.wolter.ostoja.PAIR") { nav.reset(); nav.tab = TAB_SETTINGS; nav.pairUri = intent.getStringExtra("uri"); return }
    when (intent.action) {
        MainActivity.ACTION_SCAN -> { nav.reset(); nav.tab = TAB_SCAN }
        MainActivity.ACTION_NEW -> { nav.reset(); nav.tab = TAB_VAULT; nav.push(Route.EditLogin(null)) }
        MainActivity.ACTION_CODES -> { nav.reset(); nav.tab = TAB_CODES }
        MainActivity.ACTION_SEARCH -> { nav.reset(); nav.tab = TAB_VAULT; nav.focusSearch++ }
        MainActivity.ACTION_GEN -> { nav.reset(); nav.tab = TAB_VAULT; nav.generator = true }
        MainActivity.ACTION_ISSUES -> { nav.reset(); nav.tab = TAB_VAULT }
        Intent.ACTION_VIEW -> {
            val data = intent.dataString ?: return
            if (!data.startsWith("otpauth")) return
            try {
                val list = OtpUri.parse(data)
                nav.reset()
                nav.tab = TAB_CODES
                if (list.size == 1) nav.push(Route.EditOtp(null, list.first())) else nav.importBatch = list
            } catch (e: Exception) {
                toaster.show(e.message ?: "Niepoprawny link")
            }
        }
    }
    intent.getStringExtra(MainActivity.EXTRA_OTP)?.let { nav.reset(); nav.tab = TAB_CODES; nav.highlightOtp = it }
    intent.getStringExtra(MainActivity.EXTRA_LOGIN)?.let { nav.reset(); nav.tab = TAB_VAULT; nav.push(Route.Item(it)) }
}
