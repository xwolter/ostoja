package pl.wolter.ostoja.desktop

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
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
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import pl.wolter.ostoja.data.Brand
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** Wciśnięcie z prototypu: scale(0.96) i odbicie cubic-bezier(.34,1.5,.64,1), kursor-łapka. */
fun Modifier.press(scale: Float = .96f, enabled: Boolean = true, onClick: () -> Unit): Modifier = composed {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val s by animateFloatAsState(if (pressed) scale else 1f, tween(if (pressed) 90 else 220, easing = Motion.Bouncy), label = "press")
    this
        .graphicsLayer { scaleX = s; scaleY = s }
        .pointerHoverIcon(if (enabled) PointerIcon.Hand else PointerIcon.Default)
        .clickable(src, null, enabled = enabled, onClick = onClick)
}

/** Wiersz listy: zaokrąglone podświetlenie wewnątrz karty przy najechaniu i dla zaznaczonego wpisu. */
fun Modifier.rowHover(selected: Color = Color.Transparent, radius: Dp = 18.dp, onClick: () -> Unit): Modifier = composed {
    val src = remember { MutableInteractionSource() }
    val hovered by src.collectIsHoveredAsState()
    val p = C
    val bg by androidx.compose.animation.animateColorAsState(
        when {
            selected != Color.Transparent -> selected
            hovered -> p.press
            else -> Color.Transparent
        },
        tween(150), label = "row",
    )
    this
        .clip(RoundedCornerShape(radius))
        .background(bg)
        .hoverable(src)
        .pointerHoverIcon(PointerIcon.Hand)
        .clickable(src, null, onClick = onClick)
}

/**
 * Tło okna: jednolity kolor u góry (zlewa się z paskiem tytułu) i miękka poświata w kolorze akcentu niżej.
 * Gradient rysowany bezpośrednio w Skia z ditheringiem - bez tego na dużej powierzchni widać „pierścienie”.
 */
fun Modifier.backdrop(p: Palette): Modifier = drawBehind {
    drawRect(p.bg)
    val canvas = drawContext.canvas.nativeCanvas
    fun glow(cx: Float, cy: Float, r: Float, color: Color) {
        val paint = org.jetbrains.skia.Paint().apply {
            isDither = true
            shader = org.jetbrains.skia.Shader.makeRadialGradient(
                cx, cy, r,
                intArrayOf(color.toArgb(), color.copy(alpha = color.alpha * .45f).toArgb(), Color.Transparent.toArgb()),
                floatArrayOf(0f, .45f, 1f),
            )
        }
        canvas.drawCircle(cx, cy, r, paint)
    }
    val r = size.maxDimension * .55f
    glow(size.width * .92f, size.height * .55f, r, p.glow)
    glow(size.width * .05f, size.height * 1.05f, r, p.glow.copy(alpha = p.glow.alpha * .7f))
}

/** Okrągły przycisk z ikoną i podpowiedzią po najechaniu (Kopiuj, Pokaż…). */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun IconBtn(icon: ImageVector, hint: String, bg: Color = C.card, tint: Color = C.text, size: Dp = 38.dp, onClick: () -> Unit) {
    val p = C
    androidx.compose.foundation.TooltipArea(
        tooltip = {
            Box(Modifier.clip(RoundedCornerShape(10.dp)).background(p.toast).padding(horizontal = 10.dp, vertical = 6.dp)) {
                Text(hint, style = T.label, color = Color.White)
            }
        },
        delayMillis = 400,
    ) {
        Box(Modifier.press(.9f, onClick = onClick).size(size).clip(CircleShape).background(bg), contentAlignment = Alignment.Center) {
            Icon(icon, hint, tint = tint, modifier = Modifier.size(size * .47f))
        }
    }
}

@Composable
fun Pill(
    text: String,
    bg: Color,
    color: Color,
    modifier: Modifier = Modifier,
    height: Dp = 40.dp,
    style: TextStyle = T.btn,
    icon: ImageVector? = null,
    scale: Float = .95f,
    border: Color? = null,
    onClick: () -> Unit,
) {
    Row(
        modifier.press(scale, onClick = onClick).height(height).clip(RoundedCornerShape(50)).background(bg)
            .then(if (border != null) Modifier.border(1.dp, border, RoundedCornerShape(50)) else Modifier)
            .padding(start = if (icon != null) 12.dp else 18.dp, end = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
    ) {
        if (icon != null) Icon(icon, null, tint = color, modifier = Modifier.size(18.dp))
        Text(text, style = style, color = color, maxLines = 1)
    }
}

/** Mała etykieta „2FA”, „Słabe”, „Z telefonu”. */
@Composable
fun Tag(text: String, bg: Color, color: Color, bold: Boolean = false) {
    Box(Modifier.height(24.dp).clip(RoundedCornerShape(12.dp)).background(bg).padding(horizontal = 9.dp), contentAlignment = Alignment.Center) {
        Text(text, style = T.label.copy(fontSize = 12.sp, fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Medium), color = color)
    }
}

/** Karta: w jasnym motywie miękki cień, w ciemnym - cienki jasny obrys zamiast cienia. */
@Composable
fun Card(modifier: Modifier = Modifier, radius: Dp = 24.dp, color: Color = C.card, content: @Composable BoxScope.() -> Unit) {
    val p = C
    val shape = RoundedCornerShape(radius)
    Box(
        modifier
            .then(if (p.dark) Modifier else Modifier.shadow(3.dp, shape, ambientColor = Color.Black.copy(.03f), spotColor = Color.Black.copy(.06f)))
            .clip(shape)
            .background(color)
            .border(1.dp, p.border, shape),
        content = content,
    )
}

@Composable
fun Sep(start: Dp = 0.dp, end: Dp = 0.dp) {
    Box(Modifier.padding(start = start, end = end).height(1.dp).fillMaxWidth().background(C.sep))
}

/** Pierścień odliczania TOTP (stroke-dashoffset w prototypie): pełny → pusty, czerwony przez ostatnie 5 s. */
@Composable
fun Ring(fraction: Float, warn: Boolean, size: Dp = 26.dp, stroke: Dp = 2.4.dp, radiusRatio: Float = 10f / 12f) {
    val p = C
    val color by androidx.compose.animation.animateColorAsState(if (warn) p.ringWarn else p.accent, tween(250), label = "ring")
    Canvas(Modifier.size(size)) {
        val w = stroke.toPx()
        val r = this.size.minDimension / 2 * radiusRatio
        val tl = Offset(center.x - r, center.y - r)
        drawCircle(p.track, r, center, style = Stroke(w))
        drawArc(color, -90f, 360f * fraction, false, tl, Size(r * 2, r * 2), style = Stroke(w, cap = StrokeCap.Round))
    }
}

// --- ikony serwisów ---

/** Favicony z Google S2 (jak w prototypie), zapisywane na dysku, żeby pobrać każdą tylko raz. */
object Favicons {
    lateinit var dir: File
    private val mem = mutableStateMapOf<String, ImageBitmap?>()
    private val loading = HashSet<String>()

    @Composable
    fun get(domain: String, enabled: Boolean): ImageBitmap? {
        if (!enabled || domain.isBlank()) return null
        val cached = mem[domain]
        LaunchedEffect(domain) {
            if (domain in mem || !loading.add(domain)) return@LaunchedEffect
            mem[domain] = withContext(Dispatchers.IO) { load(domain) }
        }
        return cached
    }

    private fun load(domain: String): ImageBitmap? = runCatching {
        val f = File(dir, domain.replace(Regex("[^a-z0-9.-]"), "_") + ".png")
        val bytes = if (f.exists() && f.length() > 0) f.readBytes() else {
            val c = URL("https://www.google.com/s2/favicons?domain=$domain&sz=128").openConnection() as HttpURLConnection
            c.connectTimeout = 5000; c.readTimeout = 5000
            val b = c.inputStream.use { it.readBytes() }
            if (c.responseCode != 200 || b.size < 100) return null
            f.writeBytes(b); b
        }
        org.jetbrains.skia.Image.makeFromEncoded(bytes).toComposeImageBitmap()
    }.getOrNull()
}

/** Kółko z logo serwisu albo literą (karty, notatki, Wi‑Fi). */
@Composable
fun Logo(name: String, domain: String, size: Dp = 44.dp, icons: Boolean = true, letter: Boolean = false) {
    val p = C
    Box(Modifier.size(size).clip(CircleShape).background(p.logoBg), contentAlignment = Alignment.Center) {
        val img = if (letter) null else Favicons.get(domain, icons)
        if (img != null) Image(img, name, Modifier.size(size * (24f / 44f)))
        else Text(Brand.initial(name), style = T.bodyM.copy(fontSize = (size.value * .36f).sp, fontWeight = FontWeight.SemiBold), color = p.accent)
    }
}

/** Wejście z opóźnieniem (stagger w prototypie): opacity 0→1, translateY 14→0, sprężyście. */
fun Modifier.staggerIn(index: Int, trigger: Any?, dy: Float = 14f, dx: Float = 0f): Modifier = composed {
    val a = remember { Animatable(0f) }
    LaunchedEffect(trigger) {
        a.snapTo(0f)
        a.animateTo(1f, tween(360, delayMillis = minOf(index * 35, 350), easing = Motion.Spring))
    }
    graphicsLayer {
        alpha = a.value.coerceIn(0f, 1f)
        translationY = (1 - a.value) * dy * density
        translationX = (1 - a.value) * dx * density
    }
}
