package pl.wolter.ostoja.desktop

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import pl.wolter.ostoja.data.countLabel
import pl.wolter.ostoja.data.securityReport

const val V_VAULT = 0
const val V_CODES = 1
const val V_HEALTH = 2
const val V_DEVICES = 3

sealed interface Dialog {
    data object AddOtp : Dialog
    data class Recovery(val key: String) : Dialog
    data object Wipe : Dialog
    /** Nowy wpis (id = null) albo edycja istniejącego. */
    data class EditLogin(val id: String?) : Dialog
    data class DeleteLogin(val id: String) : Dialog
    /** Rozszerzenie Firefox prosi o połączenie (pokazywane z ExtBridge.pairing). */
    data object ExtPair : Dialog
}

val LocalExt = androidx.compose.runtime.staticCompositionLocalOf<ExtBridge?> { null }

/** Stan okna (to, co w prototypie siedzi w `state` komponentu). */
@Stable
class Ui {
    var view by mutableIntStateOf(V_VAULT)
    var cat by mutableStateOf("all")
    var selected by mutableStateOf<String?>(null)
    var reveal by mutableStateOf(false)
    var len by mutableIntStateOf(20)
    var query by mutableStateOf("")
    var regenTurns by mutableFloatStateOf(0f)
    var dialog by mutableStateOf<Dialog?>(null)
    /** Zmienia się przy każdym przełączeniu widoku - uruchamia kaskadowe wejście wierszy. */
    var viewKey by mutableIntStateOf(0)
    var detailKey by mutableIntStateOf(0)
    val searchFocus = FocusRequester()

    fun go(v: Int) {
        if (v == view) return
        view = v
        viewKey++
    }

    fun select(id: String) {
        if (id == selected) return
        selected = id
        reveal = false
        detailKey++
    }
}

@Stable
class Toaster {
    var text by mutableStateOf("")
    var on by mutableStateOf(false)
    private var job: Job? = null

    fun show(msg: String, scope: kotlinx.coroutines.CoroutineScope) {
        job?.cancel()
        text = msg
        on = true
        job = scope.launch { delay(2000); on = false }
    }
}

/** Bieżący czas co `ms` (relatywne czasy, odliczanie). */
@Composable
fun rememberNow(ms: Long = 1000): State<Long> = produceState(System.currentTimeMillis()) {
    while (isActive) { delay(ms - System.currentTimeMillis() % ms + 5); value = System.currentTimeMillis() }
}

/** Czas dla pierścieni TOTP: 20 razy na sekundę wystarcza na płynny ruch, a nie zajmuje procesora jak pełne 60 kl./s. */
@Composable
fun rememberFrameNow(): State<Long> = produceState(System.currentTimeMillis()) {
    while (isActive) { delay(50); value = System.currentTimeMillis() }
}

@Composable
fun App(store: Store, ui: Ui, dark: Boolean, ext: ExtBridge = remember { ExtBridge(store) {} }) {
    androidx.compose.runtime.CompositionLocalProvider(LocalExt provides ext) { AppContent(store, ui, dark) }
}

@Composable
private fun AppContent(store: Store, ui: Ui, dark: Boolean) {
    val st by store.state.collectAsState()
    val unlock by store.unlock.collectAsState()
    val accent = ACCENTS.getOrElse(st.prefs.accent) { ACCENTS[0] }
    OstojaTheme(dark, accent) {
        val toaster = remember { Toaster() }
        val scope = androidx.compose.runtime.rememberCoroutineScope()
        LaunchedEffect(Unit) { store.toasts.collect { toaster.show(it.text, scope) } }
        val now by rememberNow()
        val lockedNow = st.phones.isEmpty() || now >= st.unlockedUntil
        // Po potwierdzeniu na telefonie karta „Sejf odblokowany” zostaje chwilę, potem wchodzi sejf.
        var celebrate by remember { mutableStateOf(false) }
        LaunchedEffect(unlock) {
            val u = unlock
            if (u is Unlock.Done && u.ok) { celebrate = true; delay(1600); celebrate = false; store.resetUnlock() }
        }
        Box(Modifier.fillMaxSize().backdrop(C)) {
            AnimatedContent(
                targetState = lockedNow || celebrate,
                transitionSpec = { fadeIn(Motion.fade(320, 80)) togetherWith fadeOut(Motion.fade(200)) },
                label = "lock",
            ) { locked ->
                if (locked) LockScreen(store, st, unlock) else Main(store, ui, st) { msg -> toaster.show(msg, scope) }
            }
            ToastHost(toaster)
            DialogHost(store, ui) { msg -> toaster.show(msg, scope) }
        }
    }
}

@Composable
private fun Main(store: Store, ui: Ui, st: Persisted, toast: (String) -> Unit) {
    val p = C
    val density = LocalDensity.current.density
    val scroll = rememberScrollState()
    var navHidden by remember { mutableStateOf(false) }
    LaunchedEffect(scroll) {
        var last = 0
        snapshotFlow { scroll.value }.collect { y ->
            val dy = (y - last) / density
            last = y
            val yd = y / density
            if (dy > 4 && yd > 80) navHidden = true else if (dy < -4 || yd < 40) navHidden = false
        }
    }
    LaunchedEffect(ui.view) { scroll.animateScrollTo(0); navHidden = false }

    val report = remember(st.vault) { st.vault.securityReport() }
    val now by rememberNow()
    val remSec = 30 - (now / 1000 % 30)
    val flagged = report.issues
    val (title, subtitle) = when {
        ui.query.isNotBlank() && ui.view == V_VAULT -> "Sejf" to "Wyniki dla „${ui.query.trim()}”"
        ui.view == V_VAULT -> "Sejf" to countLabel(st.vault.logins.size, "element", "elementy", "elementów") + ", zawsze w zgodzie z telefonem"
        ui.view == V_CODES -> "Kody 2FA" to countLabel(st.vault.otps.size, "konto", "konta", "kont") + " · nowe kody za $remSec s"
        ui.view == V_HEALTH -> "Kondycja" to if (flagged > 0) countLabel(flagged, "hasło", "hasła", "haseł") + " do poprawy · wynik ${report.score}/100" else "Wszystkie hasła są w porządku"
        else -> "Urządzenia" to "Sejf jest zsynchronizowany na " + countLabel(st.phones.size + 1, "urządzeniu", "urządzeniach", "urządzeniach")
    }

    val y = scroll.value / density
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().verticalScroll(scroll)) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                Column(Modifier.widthIn(max = 1240.dp).fillMaxWidth().padding(horizontal = 32.dp).padding(bottom = 150.dp)) {
                    Header(store, ui, st, title, subtitle, Modifier.graphicsLayer {
                        alpha = (1 - y / 160f).coerceIn(0f, 1f)
                        translationY = minOf(y, 160f) * .2f * density
                    })
                    ViewHost(ui) {
                        when (ui.view) {
                            V_VAULT, V_HEALTH -> VaultView(store, ui, st, report, scroll, toast)
                            V_CODES -> CodesView(ui, st, toast)
                            else -> DevicesView(store, ui, st)
                        }
                    }
                }
            }
        }
        TopBar(title, y)
        // Treść łagodnie znika pod kapsułą zamiast być ucięta.
        Box(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(110.dp)
                .background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color.Transparent, p.bg.copy(alpha = .92f)))),
        )
        Capsule(ui, navHidden, Modifier.align(Alignment.BottomCenter))
    }
}

/** Wejście widoku: opacity 0→1, translateY 18→0 (380 ms, SPRING). */
@Composable
private fun ViewHost(ui: Ui, content: @Composable () -> Unit) {
    val a = remember { Animatable(0f) }
    LaunchedEffect(ui.viewKey) { a.snapTo(0f); a.animateTo(1f, tween(380, easing = Motion.Spring)) }
    Box(Modifier.graphicsLayer { alpha = a.value.coerceIn(0f, 1f); translationY = (1 - a.value) * 18 * density }) { content() }
}

@Composable
private fun Header(store: Store, ui: Ui, st: Persisted, title: String, subtitle: String, modifier: Modifier) {
    val p = C
    // Pasek narzędzi (marka, telefon, wyszukiwarka) nad tytułem widoku - jak w aplikacjach Windows 11.
    Column(modifier.fillMaxWidth().padding(top = 18.dp, bottom = 26.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.foundation.Image(
                androidx.compose.ui.res.painterResource("icon.png"), null,
                Modifier.size(30.dp).clip(RoundedCornerShape(9.dp)),
            )
            Text("Ostoja", style = T.bodyM.copy(fontWeight = FontWeight.SemiBold, fontSize = 17.sp), color = p.text, modifier = Modifier.padding(start = 10.dp).weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                SyncChip(store, st)
                SearchBox(ui)
            }
        }
        Text(title, style = T.hero, color = p.text, modifier = Modifier.padding(top = 30.dp))
        Text(subtitle, style = T.body.copy(fontSize = 15.sp), color = p.muted, modifier = Modifier.padding(top = 6.dp), maxLines = 1, overflow = T.ellipsis)
    }
}

/** „Galaxy S26 Ultra · przed chwilą” z pulsującą kropką w trakcie synchronizacji. */
@Composable
private fun SyncChip(store: Store, st: Persisted) {
    val p = C
    val phone = st.phones.maxByOrNull { it.lastSeen } ?: return
    val now by rememberNow()
    val pulse by store.syncPulse.collectAsState()
    val syncing = now - pulse < 1400
    val online = now - phone.lastSeen < 75_000
    val dot = if (online) p.ok else p.muted
    Row(
        Modifier.height(42.dp).clip(RoundedCornerShape(21.dp)).background(p.card).border(1.dp, p.border, RoundedCornerShape(21.dp)).padding(start = 14.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(10.dp), contentAlignment = Alignment.Center) {
            val ping by animateFloatAsState(if (syncing) 1f else 0f, tween(200), label = "pingO")
            if (ping > 0f) {
                val t = rememberInfiniteTransition(label = "ping")
                val k by t.animateFloat(0f, 1f, infiniteRepeatable(tween(1100, easing = androidx.compose.animation.core.CubicBezierEasing(0f, 0f, .2f, 1f)), RepeatMode.Restart), label = "k")
                Box(Modifier.size(10.dp).graphicsLayer { scaleX = 1 + 1.8f * k; scaleY = 1 + 1.8f * k; alpha = .55f * (1 - k) * ping }.clip(CircleShape).background(dot))
            }
            Box(Modifier.size(10.dp).clip(CircleShape).background(dot))
        }
        Text(phone.name, style = T.btn, color = p.text)
        Text(
            when {
                syncing -> "synchronizuję…"
                !online -> "poza zasięgiem"
                // Telefon nasłuchuje zmian - skoro się odzywa, sejf jest aktualny.
                else -> ago(maxOf(phone.lastSync, phone.lastSeen), now)
            },
            style = T.btn.copy(fontWeight = FontWeight.Normal, fontFeatureSettings = "tnum"), color = p.muted,
        )
    }
}

@Composable
private fun SearchBox(ui: Ui) {
    val p = C
    Row(
        Modifier.height(42.dp).width(260.dp).clip(RoundedCornerShape(21.dp)).background(p.card).border(1.dp, p.border, RoundedCornerShape(21.dp)).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Ic.Search, null, tint = p.muted, modifier = Modifier.size(19.dp))
        Box(Modifier.weight(1f).padding(start = 10.dp)) {
            if (ui.query.isEmpty()) Text("Szukaj", style = T.body.copy(fontSize = 15.sp), color = p.muted)
            BasicTextField(
                ui.query,
                { ui.query = it; if (it.isNotBlank() && ui.view != V_VAULT) ui.go(V_VAULT) },
                singleLine = true,
                textStyle = T.body.copy(fontSize = 15.sp, color = p.text),
                cursorBrush = SolidColor(p.accent),
                modifier = Modifier.fillMaxWidth().focusRequester(ui.searchFocus).onKeyEvent {
                    if (it.key == Key.Escape) { ui.query = ""; true } else false
                },
            )
        }
        Box(Modifier.clip(RoundedCornerShape(7.dp)).background(p.fill).padding(horizontal = 7.dp, vertical = 3.dp)) {
            Text("Ctrl K", style = T.label.copy(fontFamily = FontFamily.Monospace, fontSize = 12.sp), color = p.muted)
        }
    }
}

/** Pasek z tytułem, który pojawia się po przewinięciu nagłówka (barO / barY w prototypie). */
@Composable
private fun TopBar(title: String, y: Float) {
    val p = C
    val cp = ((y - 120) / 50f).coerceIn(0f, 1f)
    val shown by animateFloatAsState(cp, tween(220), label = "bar")
    if (shown <= 0.01f) return
    Box(
        Modifier.fillMaxWidth().height(60.dp)
            .graphicsLayer { alpha = shown; translationY = (1 - shown) * -8 * density }
            .background(p.bar),
        contentAlignment = Alignment.Center,
    ) {
        Text(title, style = T.bar, color = p.text)
        Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(0.5.dp).background(p.sep))
    }
}

private data class NavItem(val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)

private val NAV = listOf(
    NavItem("Sejf", Ic.Padlock),
    NavItem("Kody 2FA", Ic.Clock),
    NavItem("Kondycja", Ic.Health),
    NavItem("Urządzenia", Ic.Devices),
)

/** Kapsuła z prototypu: akcentowa pigułka 120×52 przesuwa się po cubic-bezier(.34,1.3,.64,1). */
@Composable
private fun Capsule(ui: Ui, hidden: Boolean, modifier: Modifier) {
    val p = C
    val idx = ui.view
    val ind by animateDpAsState((6 + idx * 54).dp, tween(340, easing = Motion.Nav), label = "ind")
    val ty by animateFloatAsState(if (hidden) 10f else 0f, tween(320, easing = Motion.Spring), label = "navY")
    val sc by animateFloatAsState(if (hidden) .88f else 1f, tween(320, easing = Motion.Spring), label = "navS")
    Box(
        modifier.padding(bottom = 24.dp)
            .graphicsLayer {
                translationY = ty * density; scaleX = sc; scaleY = sc
                transformOrigin = TransformOrigin(.5f, 1f)
            }
            .shadow(18.dp, RoundedCornerShape(34.dp), ambientColor = Color.Black.copy(.25f), spotColor = Color.Black.copy(if (p.dark) .9f else .3f))
            .clip(RoundedCornerShape(34.dp))
            .background(p.capsule)
            .border(0.5.dp, p.capBorder, RoundedCornerShape(34.dp)),
    ) {
        Box(Modifier.offset(x = ind, y = 6.dp).size(120.dp, 52.dp).clip(RoundedCornerShape(26.dp)).background(p.accent))
        Row(Modifier.padding(6.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            NAV.forEachIndexed { i, n ->
                val sel = i == idx
                val w by animateDpAsState(if (sel) 120.dp else 52.dp, tween(320, easing = Motion.Nav), label = "w$i")
                val lw by animateDpAsState(if (sel) 84.dp else 0.dp, tween(320, easing = Motion.Nav), label = "lw$i")
                val lo by animateFloatAsState(if (sel) 1f else 0f, tween(200), label = "lo$i")
                val color by androidx.compose.animation.animateColorAsState(if (sel) Color.White else p.muted, tween(220), label = "c$i")
                Row(
                    Modifier.width(w).height(52.dp).clip(RoundedCornerShape(26.dp)).press(.94f) { ui.go(i) },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Icon(n.icon, n.label, tint = color, modifier = Modifier.size(22.dp))
                    if (lw > 0.5.dp) Text(
                        n.label, style = T.btn, color = color, maxLines = 1, softWrap = false,
                        modifier = Modifier.widthIn(max = lw).padding(start = (8 * lo).dp).graphicsLayer { alpha = lo },
                    )
                }
            }
        }
    }
}

@Composable
private fun ToastHost(t: Toaster) {
    val p = C
    val k by animateFloatAsState(if (t.on) 1f else 0f, tween(300, easing = Motion.Bouncy), label = "toast")
    val a by animateFloatAsState(if (t.on) 1f else 0f, tween(220), label = "toastA")
    if (a <= 0.01f) return
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        Box(
            Modifier.padding(bottom = 100.dp)
                .graphicsLayer { alpha = a; translationY = (1 - k) * 16 * density; val s = .94f + .06f * k; scaleX = s; scaleY = s }
                .clip(RoundedCornerShape(24.dp)).background(p.toast).padding(horizontal = 22.dp, vertical = 12.dp),
        ) { Text(t.text, style = T.btn.copy(fontWeight = FontWeight.Normal), color = Color.White) }
    }
}
