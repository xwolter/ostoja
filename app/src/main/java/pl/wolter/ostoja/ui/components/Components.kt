package pl.wolter.ostoja.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import pl.wolter.ostoja.data.AppSettings
import pl.wolter.ostoja.data.Brand
import pl.wolter.ostoja.data.Favicons
import pl.wolter.ostoja.data.Login
import pl.wolter.ostoja.ui.Ic
import pl.wolter.ostoja.ui.theme.C
import pl.wolter.ostoja.ui.theme.Motion
import pl.wolter.ostoja.ui.theme.T

/** Bieżące ustawienia dostępne w całym drzewie (ikony, dzielenie kodu, ukrywanie). */
val LocalSettings = compositionLocalOf { AppSettings() }

/** Wciśnięcie z prototypu: scale(0.96) po krzywej cubic-bezier(.34,1.5,.64,1), 200 ms - z lekkim odbiciem. */
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.press(
    scaleTo: Float = .96f,
    enabled: Boolean = true,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
): Modifier = composed {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val haptic = LocalHapticFeedback.current
    val scale by animateFloatAsState(if (pressed) scaleTo else 1f, Motion.press(), label = "press")
    this
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .combinedClickable(
            interactionSource = src, indication = null, enabled = enabled,
            onLongClick = onLongClick?.let { { haptic.performHapticFeedback(HapticFeedbackType.LongPress); it() } },
            onClick = onClick,
        )
}

/** Wiersz listy: tło --press przy dotyku (transition: background 150ms). */
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.rowPress(onLongClick: (() -> Unit)? = null, onClick: () -> Unit): Modifier = composed {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val haptic = LocalHapticFeedback.current
    val bg by animateColorAsState(if (pressed) C.press else Color.Transparent, tween(150), label = "rowbg")
    this
        .background(bg)
        .combinedClickable(
            interactionSource = src, indication = null,
            onLongClick = onLongClick?.let { { haptic.performHapticFeedback(HapticFeedbackType.LongPress); it() } },
            onClick = onClick,
        )
}

/** Zaokrąglona karta-grupa (radius 30, margines 12). */
@Composable
fun Group(modifier: Modifier = Modifier, vertical: Dp = 4.dp, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .padding(horizontal = 12.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(30.dp))
            .background(C.card)
            .padding(vertical = vertical),
        content = content,
    )
}

@Composable
fun Divider(start: Dp = 24.dp, end: Dp = 24.dp) {
    Box(Modifier.padding(start = start, end = end).fillMaxWidth().height(1.dp).background(C.divider))
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Row(
        modifier.fillMaxWidth().padding(start = 28.dp, end = 20.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, style = T.section, color = C.text2, modifier = Modifier.weight(1f))
        action?.invoke()
    }
}

/**
 * Ikona serwisu: koło 44 z faviconą na tle --logoBg; bez ikony - litera w kolorze marki
 * na jej jasnym odcieniu (jak w prototypie, gdy favicona się nie wczyta).
 */
@Composable
fun Avatar(name: String, url: String = "", size: Dp = 44.dp, type: String = Login.LOGIN) {
    val ctx = LocalContext.current
    val dark = C.dark
    when (type) {
        Login.CARD, Login.NOTE, Login.WIFI -> {
            Box(Modifier.size(size).clip(CircleShape).background(C.logoBg), contentAlignment = Alignment.Center) {
                Icon(
                    when (type) { Login.CARD -> Ic.Card; Login.NOTE -> Ic.NoteDoc; else -> Ic.Wifi },
                    null, tint = C.accent, modifier = Modifier.size(size * .5f),
                )
            }
            return
        }
    }
    val domain = remember(name, url) { Brand.domain(name, url) }
    val icon by produceState(Favicons.cached(ctx, domain)?.asImageBitmap(), domain) {
        if (value == null) value = Favicons.load(ctx, domain)?.asImageBitmap()
    }
    val brand = Color(Brand.color(name, url))
    val bg = if (icon != null) C.logoBg else brand.copy(alpha = if (dark) .26f else .12f).compositeOver(C.card)
    val fg = if (dark) brand.copy(alpha = .55f).compositeOver(Color.White) else brand
    Box(Modifier.size(size).clip(CircleShape).background(bg), contentAlignment = Alignment.Center) {
        val img = icon
        if (img != null) Image(img, name, Modifier.size(size * .58f))
        else Text(Brand.initial(name), color = fg, fontSize = (size.value * .37f).sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun IconBtn(icon: ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier, tint: Color = C.text, size: Dp = 44.dp, iconSize: Dp = 22.dp, desc: String? = null) {
    Box(
        modifier.size(size).clip(CircleShape).rowPress(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, desc, tint = tint, modifier = Modifier.size(iconSize)) }
}

@Composable
fun PillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = true,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    danger: Boolean = false,
    height: Dp = 52.dp,
) {
    val bg = when {
        !enabled -> C.field
        danger && primary -> C.danger
        primary -> C.accent
        else -> C.card
    }
    val fg = when {
        !enabled -> C.text3
        primary -> Color.White
        danger -> C.danger
        else -> C.text
    }
    Row(
        modifier
            .press(scaleTo = .97f, enabled = enabled, onClick = onClick)
            .heightIn(min = height)
            .clip(RoundedCornerShape(50))
            .background(bg)
            .padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = fg, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, color = fg, style = T.bodyM, maxLines = 1)
    }
}

/** Chip kategorii: background/color 220 ms. */
@Composable
fun Chip(text: String, selected: Boolean, onClick: () -> Unit, count: Int? = null) {
    val bg by animateColorAsState(if (selected) C.accent else C.card, tween(220), label = "chip")
    val fg by animateColorAsState(if (selected) Color.White else C.text, tween(220), label = "chipfg")
    Row(
        Modifier
            .height(40.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(bg)
            .rowPress(onClick = onClick)
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, color = fg, style = T.chip)
        if (count != null && count > 0) {
            Spacer(Modifier.width(6.dp))
            Text(count.toString(), color = fg.copy(alpha = .7f), style = T.small)
        }
    }
}

@Composable
fun SearchField(
    value: String,
    onChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    focus: FocusRequester? = null,
) {
    Row(
        modifier
            .padding(horizontal = 12.dp)
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(26.dp))
            .background(C.card)
            .padding(start = 20.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Ic.Search, null, tint = C.text2, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Box(Modifier.weight(1f)) {
            val style = TextStyle(fontSize = 16.sp, color = C.text)
            if (value.isEmpty()) Text(placeholder, style = style.copy(color = C.text2))
            BasicTextField(
                value = value, onValueChange = onChange, singleLine = true,
                textStyle = style, cursorBrush = SolidColor(C.accent),
                modifier = Modifier.fillMaxWidth().then(if (focus != null) Modifier.focusRequester(focus) else Modifier),
            )
        }
        if (value.isNotEmpty()) IconBtn(Ic.Close, { onChange("") }, size = 40.dp, iconSize = 18.dp, tint = C.text2)
    }
}

/** Pole formularza: etykieta nad zaokrąglonym polem. */
@Composable
fun Field(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    password: Boolean = false,
    revealed: Boolean = false,
    mono: Boolean = false,
    singleLine: Boolean = true,
    keyboard: KeyboardType = KeyboardType.Text,
    error: String? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    Column(modifier.fillMaxWidth()) {
        Text(label, style = T.sub, color = if (error != null) C.danger else C.text2, modifier = Modifier.padding(start = 6.dp, bottom = 6.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(C.field)
                .padding(start = 16.dp, end = if (trailing != null) 4.dp else 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.weight(1f).padding(vertical = 14.dp)) {
                val style = (if (mono) T.mono else TextStyle(fontSize = 17.sp)).copy(color = C.text)
                if (value.isEmpty()) Text(placeholder, style = style.copy(color = C.text3))
                BasicTextField(
                    value = value, onValueChange = onChange, singleLine = singleLine,
                    textStyle = style, cursorBrush = SolidColor(C.accent),
                    visualTransformation = if (password && !revealed) PasswordVisualTransformation() else VisualTransformation.None,
                    keyboardOptions = KeyboardOptions(keyboardType = if (password) KeyboardType.Password else keyboard, autoCorrectEnabled = false),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            trailing?.invoke(this)
        }
        if (error != null) Text(error, style = T.small, color = C.danger, modifier = Modifier.padding(start = 6.dp, top = 4.dp))
    }
}

/**
 * Przełącznik z prototypu: tor 52×30, kciuk 24 z cieniem przesuwany o 22 px po krzywej
 * cubic-bezier(.34,1.5,.64,1) w 280 ms (z odbiciem), kolor toru 220 ms.
 */
@Composable
fun OneSwitch(checked: Boolean, onChange: (Boolean) -> Unit, enabled: Boolean = true) {
    val track by animateColorAsState(if (checked) C.accent else C.toggleOff, tween(220), label = "track")
    val x by animateDpAsState(if (checked) 22.dp else 0.dp, Motion.toggle(), label = "thumb")
    Box(
        Modifier
            .graphicsLayer { alpha = if (enabled) 1f else .45f }
            .size(52.dp, 30.dp)
            .clip(RoundedCornerShape(15.dp))
            .background(track)
            .then(if (enabled) Modifier.rowPress { onChange(!checked) } else Modifier)
    ) {
        Box(
            Modifier
                .offset { IntOffset((3.dp + x).roundToPx(), 3.dp.roundToPx()) }
                .shadow(2.dp, CircleShape)
                .size(24.dp)
                .clip(CircleShape)
                .background(Color.White)
        )
    }
}

/** Wiersz ustawień z prototypu: 17 sp, podpis 14 sp, padding 16/20/16/24. */
@Composable
fun SettingRow(
    title: String,
    subtitle: String? = null,
    icon: ImageVector? = null,
    iconTint: Color = C.accent,
    subtitleColor: Color = C.text2,
    titleColor: Color = C.text,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null && enabled) Modifier.rowPress(onClick = onClick) else Modifier)
            .graphicsLayer { alpha = if (enabled) 1f else .45f }
            .heightIn(min = 64.dp)
            .padding(start = 24.dp, end = 20.dp, top = 16.dp, bottom = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = iconTint, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(16.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = T.body, color = titleColor)
            if (subtitle != null) Text(subtitle, style = T.sub.copy(fontSize = 14.sp), color = subtitleColor, modifier = Modifier.padding(top = 3.dp))
        }
        if (trailing != null) {
            Spacer(Modifier.width(16.dp))
            trailing()
        }
    }
}

@Composable
fun SwitchRow(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit, icon: ImageVector? = null, enabled: Boolean = true) {
    SettingRow(title, subtitle, icon, enabled = enabled, onClick = { onChange(!checked) }) {
        OneSwitch(checked, onChange, enabled)
    }
}

/**
 * Pierścień odliczania: widoczny łuk = pozostały czas, od góry zgodnie z ruchem wskazówek.
 * `fraction` czytany w fazie rysowania - płynnie co klatkę bez rekompozycji.
 * Kolor przechodzi w czerwony (#FF4D4F) w ostatnich 5 s z przejściem 250 ms.
 */
@Composable
fun CountdownRing(fraction: () -> Float, warn: Boolean, modifier: Modifier = Modifier, size: Dp = 24.dp, stroke: Dp = 2.4.dp, label: String? = null) {
    val track = C.track
    val color by animateColorAsState(if (warn) C.ringWarn else C.accent, tween(250), label = "ring")
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val w = stroke.toPx()
            val inset = w / 2
            val arc = Size(this.size.width - w, this.size.height - w)
            drawArc(track, 0f, 360f, false, Offset(inset, inset), arc, style = Stroke(w))
            val f = fraction().coerceIn(0f, 1f)
            if (f > 0.001f) drawArc(color, -90f, 360f * f, false, Offset(inset, inset), arc, style = Stroke(w, cap = StrokeCap.Round))
        }
        if (label != null) Text(label, style = T.small.copy(fontWeight = FontWeight.SemiBold, fontSize = 11.sp), color = color)
    }
}

/** Kod podzielony na dwie części z odstępem (Dziel kod na trójki), cyfry tabelaryczne. */
@Composable
fun CodeText(code: String, color: Color, modifier: Modifier = Modifier, style: TextStyle = T.code, hidden: Boolean = false, group: Boolean = LocalSettings.current.groupDigits) {
    val shown = if (hidden) "•".repeat(code.length.coerceAtLeast(6)) else code
    val cut = when (shown.length) { 8 -> 4; else -> 3 }
    val gap = (style.fontSize.value * .28f).dp
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(if (group) gap else 0.dp)) {
        Text(shown.take(cut), style = style, color = color, maxLines = 1)
        Text(shown.drop(cut), style = style, color = color, maxLines = 1)
    }
}

/** Siła hasła: cztery segmenty 6 px, kolor przechodzi w 260 ms. */
@Composable
fun StrengthBar(score: Int, color: Color, modifier: Modifier = Modifier) {
    val filled = score.coerceIn(1, 4)
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(4) { i ->
            val c by animateColorAsState(if (i < filled) color else C.track, tween(260), label = "seg$i")
            Box(Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(3.dp)).background(c))
        }
    }
}

@Composable
fun OneDialog(
    title: String,
    text: String? = null,
    confirm: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    dismiss: String? = "Anuluj",
    danger: Boolean = false,
    content: (@Composable ColumnScope.() -> Unit)? = null,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = C.card,
        shape = RoundedCornerShape(30.dp),
        title = { Text(title, style = T.title, color = C.text) },
        text = {
            Column {
                if (text != null) Text(text, style = T.subtitle, color = C.text2)
                content?.invoke(this)
            }
        },
        confirmButton = {
            Text(
                confirm, style = T.bodyM, color = if (danger) C.danger else C.accent,
                modifier = Modifier.clip(RoundedCornerShape(50)).rowPress(onClick = onConfirm).padding(horizontal = 16.dp, vertical = 10.dp),
            )
        },
        dismissButton = if (dismiss == null) null else ({
            Text(
                dismiss, style = T.bodyM, color = C.text,
                modifier = Modifier.clip(RoundedCornerShape(50)).rowPress(onClick = onDismiss).padding(horizontal = 16.dp, vertical = 10.dp),
            )
        }),
    )
}

@Composable
fun EmptyState(icon: ImageVector, title: String, text: String, modifier: Modifier = Modifier, actions: @Composable ColumnScope.() -> Unit = {}) {
    Column(modifier.fillMaxWidth().padding(horizontal = 36.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(140.dp).clip(CircleShape).background(C.accentSoft), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = C.accent, modifier = Modifier.size(56.dp))
        }
        Spacer(Modifier.height(32.dp))
        Text(title, style = T.title, color = C.text, textAlign = TextAlign.Center)
        Spacer(Modifier.height(10.dp))
        Text(text, style = T.subtitle.copy(lineHeight = 22.sp), color = C.text2, textAlign = TextAlign.Center)
        Spacer(Modifier.height(28.dp))
        actions()
    }
}

// --- Toast z prototypu: kapsuła rgba(36,36,38,.94), wjazd 14 px z odbiciem (280 ms) + fade 220 ms ---

@Stable
class Toaster {
    var message by mutableStateOf<String?>(null)
        private set
    var seq by mutableIntStateOf(0)
        private set

    fun show(msg: String) {
        message = msg
        seq++
    }

    fun clear() { message = null }
}

val LocalToaster = staticCompositionLocalOf { Toaster() }

@Composable
fun ToastHost(toaster: Toaster, bottom: Dp, modifier: Modifier = Modifier) {
    LaunchedEffect(toaster.seq) {
        if (toaster.message != null) {
            delay(1600)
            toaster.clear()
        }
    }
    var last by remember { mutableStateOf("") }
    toaster.message?.let { last = it }
    val on = toaster.message != null
    val alpha by animateFloatAsState(if (on) 1f else 0f, tween(220), label = "toastA")
    val y by animateDpAsState(if (on) 0.dp else 14.dp, tween(280, easing = Motion.Springy), label = "toastY")
    val b by animateDpAsState(bottom, tween(280, easing = Motion.Springy), label = "toastB")
    if (alpha > 0.01f) Box(modifier.fillMaxWidth().padding(bottom = b), contentAlignment = Alignment.BottomCenter) {
        Text(
            last, color = Color.White, style = TextStyle(fontSize = 14.sp), maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .graphicsLayer { this.alpha = alpha; translationY = y.toPx() }
                .padding(horizontal = 32.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(C.toast)
                .padding(horizontal = 20.dp, vertical = 12.dp),
        )
    }
}
