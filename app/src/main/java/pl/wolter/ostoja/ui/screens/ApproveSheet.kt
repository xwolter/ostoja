package pl.wolter.ostoja.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import pl.wolter.ostoja.R
import pl.wolter.ostoja.security.AppLock
import pl.wolter.ostoja.sync.Incoming
import pl.wolter.ostoja.sync.Sync
import pl.wolter.ostoja.ui.Ic
import pl.wolter.ostoja.ui.components.Divider
import pl.wolter.ostoja.ui.components.press
import pl.wolter.ostoja.ui.theme.C
import pl.wolter.ostoja.ui.theme.T

private enum class Step { PICK, BIO, SENDING, OK, FAIL }

/**
 * Arkusz „Logujesz się do Ostoi na komputerze?” z prototypu „Logowanie z telefonem”:
 * wybór liczby widocznej na komputerze → odcisk palca → potwierdzenie (sejf odblokowany na 7 dni).
 */
@Composable
fun ApproveSheet(inc: Incoming, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val activity = ctx as FragmentActivity
    val scope = rememberCoroutineScope()
    val r = inc.request
    var step by remember { mutableStateOf(Step.PICK) }
    var picked by remember { mutableIntStateOf(-1) }
    var fail by remember { mutableStateOf("") }
    val bio = remember { Animatable(0f) }

    fun send() {
        step = Step.SENDING
        scope.launch {
            bio.animateTo(1f, tween(800, easing = CubicBezierEasing(.4f, 0f, .2f, 1f)))
            when (Sync.answer(inc, approve = true, picked = picked)) {
                "ok" -> { step = Step.OK; delay(1800); onClose() }
                "wrong" -> { fail = "Na komputerze była inna liczba. Prośba została odrzucona - spróbuj jeszcze raz na komputerze."; step = Step.FAIL }
                "offline" -> { fail = "Komputer jest poza zasięgiem. Sprawdź, czy telefon i komputer są w tej samej sieci Wi‑Fi."; step = Step.FAIL }
                else -> { fail = "Prośba wygasła. Kliknij „Potwierdź na telefonie” na komputerze jeszcze raz."; step = Step.FAIL }
            }
        }
    }

    fun scan() {
        if (step != Step.BIO) return
        if (!AppLock.canAuthenticate(ctx)) { send(); return }
        AppLock.prompt(activity, "Potwierdź logowanie", "${r.app} · ${r.computerName}") { ok -> if (ok) send() }
    }

    fun deny() {
        scope.launch { Sync.answer(inc, approve = false, picked = null) }
        onClose()
    }

    LaunchedEffect(step) { if (step == Step.BIO) { delay(250); scan() } }

    OneSheet(onDismiss = { if (step == Step.PICK) Sync.dismiss(inc); onClose() }) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(10.dp))
            Image(painterResource(R.drawable.ic_logo), null, Modifier.size(60.dp).clip(CircleShape))
            Text(
                if (step == Step.OK) "Gotowe" else "Logujesz się do Ostoi na komputerze?",
                style = T.title.copy(fontSize = 24.sp, fontWeight = FontWeight.SemiBold), color = C.text, textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 16.dp),
            )
            Column(Modifier.padding(top = 20.dp).fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(C.bg).padding(vertical = 4.dp)) {
                InfoRow("Urządzenie", r.computerName)
                Divider()
                InfoRow("Aplikacja", r.app)
                Divider()
                InfoRow("Sesja ważna", "7 dni")
            }
            AnimatedContent(step, transitionSpec = { fadeIn(tween(260)) togetherWith fadeOut(tween(120)) }, label = "step") { s ->
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    when (s) {
                        Step.PICK -> {
                            Text("Wybierz numer widoczny na komputerze", style = T.body.copy(fontSize = 15.sp), color = C.text2, modifier = Modifier.padding(top = 24.dp))
                            Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                                r.options.forEach { n ->
                                    Box(
                                        Modifier.press(.9f) { picked = n; step = Step.BIO }.size(84.dp).clip(CircleShape).background(C.bg),
                                        contentAlignment = Alignment.Center,
                                    ) { Text("$n", style = T.title.copy(fontSize = 32.sp, fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum"), color = C.text) }
                                }
                            }
                            Box(Modifier.padding(top = 22.dp).clip(RoundedCornerShape(22.dp)).press { deny() }.height(44.dp).padding(horizontal = 22.dp), contentAlignment = Alignment.Center) {
                                Text("To nie ja · odrzuć", style = T.body.copy(fontSize = 15.sp, fontWeight = FontWeight.Medium), color = C.danger)
                            }
                        }
                        Step.BIO, Step.SENDING -> Column(Modifier.padding(top = 28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            val sc by animateFloatAsState(if (s == Step.SENDING) .92f else 1f, tween(300, easing = CubicBezierEasing(.34f, 1.6f, .64f, 1f)), label = "bioS")
                            val track = C.track
                            val accent = C.accent
                            Box(Modifier.size(96.dp).press(.95f) { scan() }, contentAlignment = Alignment.Center) {
                                Canvas(Modifier.size(96.dp)) {
                                    val w = 4.dp.toPx()
                                    val rr = 44.dp.toPx()
                                    drawCircle(track, rr, style = Stroke(w))
                                    drawArc(accent, -90f, 360f * bio.value, false, Offset(center.x - rr, center.y - rr), Size(rr * 2, rr * 2), style = Stroke(w, cap = StrokeCap.Round))
                                }
                                Box(Modifier.size(68.dp).graphicsLayer { scaleX = sc; scaleY = sc }.clip(CircleShape).background(C.accentSoft), contentAlignment = Alignment.Center) {
                                    Icon(Ic.Fingerprint, null, tint = C.accent, modifier = Modifier.size(34.dp))
                                }
                            }
                            Text(if (s == Step.SENDING) "Sprawdzam…" else "Dotknij czytnika linii papilarnych", style = T.body.copy(fontSize = 15.sp), color = C.text2, modifier = Modifier.padding(top = 16.dp))
                        }
                        Step.OK -> Column(Modifier.padding(top = 28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            PopCircle(C.accent, Ic.Check)
                            Text("Sejf odblokowany na komputerze na 7 dni", style = T.body.copy(fontSize = 15.sp), color = C.text2, modifier = Modifier.padding(top = 14.dp))
                        }
                        Step.FAIL -> Column(Modifier.padding(top = 28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            PopCircle(C.danger, Ic.Close)
                            Text(fail, style = T.body.copy(fontSize = 15.sp), color = C.text2, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 14.dp, start = 12.dp, end = 12.dp))
                        }
                    }
                }
            }
            Spacer(Modifier.height(28.dp))
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 13.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = T.body.copy(fontSize = 15.sp), color = C.text2)
        Text(value, style = T.body.copy(fontSize = 15.sp), color = C.text)
    }
}

/** Ikona potwierdzenia „wyskakuje” jak w prototypie: scale 0.4 → 1, cubic-bezier(.34,1.7,.64,1). */
@Composable
private fun PopCircle(color: Color, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    val pop = remember { Animatable(.4f) }
    LaunchedEffect(Unit) { delay(60); pop.animateTo(1f, tween(420, easing = CubicBezierEasing(.34f, 1.7f, .64f, 1f))) }
    Box(Modifier.size(80.dp).graphicsLayer { scaleX = pop.value; scaleY = pop.value }.clip(CircleShape).background(color), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(36.dp))
    }
}
