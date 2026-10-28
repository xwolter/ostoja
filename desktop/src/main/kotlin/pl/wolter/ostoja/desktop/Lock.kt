package pl.wolter.ostoja.desktop

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * „Logowanie z telefonem” z prototypu: co 7 dni sejf na komputerze się blokuje, komputer pokazuje liczbę,
 * a na telefonie trzeba wybrać tę samą spośród trzech i dotknąć czytnika linii papilarnych.
 */
@Composable
fun LockScreen(store: Store, st: Persisted, unlock: Unlock) {
    Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 40.dp), contentAlignment = Alignment.Center) {
        if (st.phones.isEmpty()) Setup(store) else LockCard(store, st, unlock)
    }
}

@Composable
private fun Setup(store: Store) {
    val p = C
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(56.dp).clip(CircleShape).background(p.accent), contentAlignment = Alignment.Center) {
            Icon(Ic.Padlock, null, tint = Color.White, modifier = Modifier.size(28.dp))
        }
        Text("Ostoja na komputerze", style = T.hero.copy(fontSize = 40.sp), color = p.text, modifier = Modifier.padding(top = 20.dp))
        Text(
            "Hasła i kody 2FA z telefonu, zawsze zsynchronizowane. Połącz telefon, żeby zacząć.",
            style = T.body, color = p.muted, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 10.dp, bottom = 32.dp).width(520.dp),
        )
        PairCard(store, Modifier.width(440.dp), title = "Połącz telefon")
    }
}

@Composable
private fun LockCard(store: Store, st: Persisted, unlock: Unlock) {
    val p = C
    val phone = st.phones.maxBy { it.lastSeen }
    Card(Modifier.width(420.dp), radius = 28.dp) {
        Column(Modifier.fillMaxWidth().padding(start = 36.dp, end = 36.dp, top = 36.dp, bottom = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            // Na ekranie wyniku zamiast kłódki „wyskakuje” znak potwierdzenia (jak w prototypie).
            if (unlock !is Unlock.Done) Box(Modifier.size(56.dp).clip(CircleShape).background(p.accent), contentAlignment = Alignment.Center) {
                Icon(Ic.Padlock, null, tint = Color.White, modifier = Modifier.size(26.dp))
            }
            // Kroki wchodzą jak w prototypie: opacity + translateY 14 → 0.
            AnimatedContent(
                targetState = unlock,
                contentKey = { it::class },
                transitionSpec = { fadeIn(Motion.fade(260)) togetherWith fadeOut(Motion.fade(120)) },
                label = "step",
            ) { u ->
                Column(Modifier.fillMaxWidth().staggerIn(0, u::class), horizontalAlignment = Alignment.CenterHorizontally) {
                    when (u) {
                        Unlock.Idle -> Locked(store, st, phone)
                        is Unlock.Waiting -> Waiting(store, u, phone)
                        is Unlock.Done -> Done(store, u, phone)
                    }
                }
            }
        }
    }
}

@Composable
private fun Locked(store: Store, st: Persisted, phone: Phone) {
    val p = C
    var recovery by remember { mutableStateOf(false) }
    val expired = st.confirmedAt > 0 && st.unlockedUntil > 0
    Text("Sejf zablokowany", style = T.title.copy(fontSize = 26.sp), color = p.text, modifier = Modifier.padding(top = 20.dp))
    Text(
        if (expired) "Minęło 7 dni od ostatniego potwierdzenia. Potwierdź na telefonie, że to Ty." else "Potwierdź na telefonie, że to Ty.",
        style = T.body.copy(fontSize = 14.5.sp, lineHeight = 22.sp), color = p.muted, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp),
    )
    Row(
        Modifier.padding(top = 28.dp).fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(p.fill).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(p.tint), contentAlignment = Alignment.Center) {
            Icon(Ic.Phone, null, tint = p.accent, modifier = Modifier.size(20.dp))
        }
        Column {
            Text(phone.name, style = T.bodyM, color = p.text)
            Text("Telefon · ostatnio ${ago(phone.lastSeen)}", style = T.sub, color = p.muted, modifier = Modifier.padding(top = 2.dp))
        }
    }
    if (!recovery) {
        Row(
            Modifier.padding(top = 16.dp).fillMaxWidth().press(.97f) { store.startUnlock() }.height(52.dp).clip(RoundedCornerShape(26.dp)).background(p.accent),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center,
        ) {
            Icon(Ic.Padlock, null, tint = Color.White, modifier = Modifier.size(18.dp))
            Text("Potwierdź na telefonie", style = T.bodyM, color = Color.White, modifier = Modifier.padding(start = 10.dp))
        }
        if (st.recovery != null) Text(
            "Użyj klucza odzyskiwania", style = T.btn, color = p.accent,
            modifier = Modifier.padding(top = 16.dp).press { recovery = true },
        )
    } else RecoveryInput(store) { recovery = false }
}

@Composable
private fun RecoveryInput(store: Store, onCancel: () -> Unit) {
    val p = C
    var text by remember { mutableStateOf("") }
    var err by remember { mutableStateOf<String?>(null) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    fun submit() { err = store.unlockWithRecovery(text) }
    Column(Modifier.padding(top = 16.dp).fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(26.dp)).background(p.fill).padding(horizontal = 18.dp), contentAlignment = Alignment.CenterStart) {
            if (text.isEmpty()) Text("XXXX-XXXX-XXXX-XXXX-XXXX-XXXX", style = T.mono.copy(fontSize = 14.sp), color = p.muted)
            BasicTextField(
                text, { text = it.uppercase(); err = null }, singleLine = true,
                textStyle = T.mono.copy(fontSize = 14.sp, color = p.text), cursorBrush = SolidColor(p.accent),
                keyboardActions = KeyboardActions(onDone = { submit() }),
                modifier = Modifier.fillMaxWidth().focusRequester(focus).onKeyEvent { if (it.key == Key.Enter) { submit(); true } else false },
            )
        }
        if (err != null) Text(err!!, style = T.sub, color = p.danger, modifier = Modifier.padding(top = 8.dp, start = 18.dp))
        Row(Modifier.padding(top = 14.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pill("Wróć", p.fill, p.text, height = 48.dp, modifier = Modifier.weight(1f)) { onCancel() }
            Pill("Odblokuj", p.accent, Color.White, height = 48.dp, modifier = Modifier.weight(1f)) { submit() }
        }
    }
}

@Composable
private fun Waiting(store: Store, u: Unlock.Waiting, phone: Phone) {
    val p = C
    val now by rememberNow(500)
    val left = ((u.request.expires - now) / 1000).coerceAtLeast(0)
    LaunchedEffect(left) { if (left == 0L) store.pendingRequest() }
    val offline = now - phone.lastSeen > 75_000
    Text("Potwierdź na telefonie", style = T.title.copy(fontSize = 26.sp), color = p.text, modifier = Modifier.padding(top = 20.dp))
    Text(
        "Otwórz powiadomienie Ostoi na ${phone.name} i wybierz ten numer:",
        style = T.body.copy(fontSize = 14.5.sp, lineHeight = 22.sp), color = p.muted, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp),
    )
    Box(Modifier.padding(top = 28.dp).size(132.dp), contentAlignment = Alignment.Center) {
        val t = rememberInfiniteTransition(label = "ping")
        val k by t.animateFloat(0f, 1f, infiniteRepeatable(tween(1800, easing = androidx.compose.animation.core.CubicBezierEasing(0f, 0f, .2f, 1f)), RepeatMode.Restart), label = "k")
        Box(Modifier.size(132.dp).graphicsLayer { val s = 1 + 1.2f * k; scaleX = s; scaleY = s; alpha = .5f * (1 - k) }.clip(CircleShape).background(p.tint))
        Box(Modifier.size(132.dp).clip(CircleShape).background(p.tint))
        Text("${u.code}", style = T.hero.copy(fontSize = 60.sp, letterSpacing = (-1).sp, fontFeatureSettings = "tnum"), color = p.accent)
    }
    Text(
        if (offline) "Telefon jest poza zasięgiem - otwórz na nim Ostoję" else "Prośba wygaśnie za ${left / 60}:${(left % 60).toString().padStart(2, '0')}",
        style = T.btn.copy(fontWeight = FontWeight.Normal, fontFeatureSettings = "tnum"), color = if (offline) p.warnText else p.muted,
        modifier = Modifier.padding(top = 28.dp),
    )
    Pill("Anuluj", p.fill, p.text, height = 44.dp, modifier = Modifier.padding(top = 14.dp)) { store.cancelUnlock() }
}

@Composable
private fun Done(store: Store, u: Unlock.Done, phone: Phone) {
    val p = C
    val pop = remember { Animatable(.4f) }
    LaunchedEffect(Unit) { kotlinx.coroutines.delay(60); pop.animateTo(1f, tween(420, easing = Motion.Pop)) }
    Box(
        Modifier.padding(top = 24.dp).size(88.dp).graphicsLayer { scaleX = pop.value; scaleY = pop.value }.clip(CircleShape).background(if (u.ok) p.accent else p.danger),
        contentAlignment = Alignment.Center,
    ) { Icon(if (u.ok) Ic.Check else Ic.Close, null, tint = Color.White, modifier = Modifier.size(40.dp)) }
    val (title, text) = when {
        u.ok -> "Sejf odblokowany" to "Potwierdzono na ${u.reason}. Kolejne potwierdzenie za 7 dni, ${dayMonth(u.until)}."
        u.reason == "wrong" -> "Wybrano inny numer" to "Na telefonie wybrano liczbę, której nie było na komputerze. Spróbuj jeszcze raz."
        u.reason == "expired" -> "Prośba wygasła" to "Telefon nie odpowiedział w ciągu 2 minut. Sprawdź, czy jest w tej samej sieci Wi‑Fi."
        else -> "Odrzucono na telefonie" to "Ten komputer został wylogowany. Jeśli to nie Ty, zmień hasło główne w telefonie."
    }
    Text(title, style = T.title.copy(fontSize = 26.sp), color = p.text, modifier = Modifier.padding(top = 22.dp))
    Text(text, style = T.body.copy(fontSize = 14.5.sp, lineHeight = 22.sp), color = p.muted, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp))
    if (!u.ok) Pill("Zacznij od nowa", p.fill, p.text, height = 48.dp, modifier = Modifier.padding(top = 28.dp)) { store.resetUnlock() }
    else Spacer(Modifier.height(8.dp))
}
