package pl.wolter.ostoja.desktop

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp

/**
 * Kolory aplikacji na Windows. Wywodzą się z prototypu „Ostoja Web v2”, ale są dopasowane do okna
 * na komputerze: tło z delikatną poświatą w kolorze akcentu, karty z cienkim obrysem i cieniem,
 * a przyciski w kartach mają własne wypełnienie (`fill`) zamiast koloru tła strony.
 */
@Immutable
data class Palette(
    val dark: Boolean,
    val bg: Color,
    val card: Color,
    /** Wypełnienie pól i przycisków wewnątrz kart. */
    val fill: Color,
    val border: Color,
    val text: Color,
    val muted: Color,
    val sep: Color,
    val press: Color,
    val track: Color,
    val logoBg: Color,
    val toast: Color,
    val ok: Color,
    val bar: Color,
    val capsule: Color,
    val capBorder: Color,
    val accent: Color,
    val tint: Color,
    val sel: Color,
    val glow: Color,
) {
    val danger = Color(0xFFE5484D)
    val warnText = if (dark) Color(0xFFE5A23C) else Color(0xFFB86E00)
    val warnBg = Color(0xFFD98200).copy(alpha = if (dark) .16f else .12f)
    val dangerBg = danger.copy(alpha = if (dark) .18f else .12f)
    val ringWarn = Color(0xFFFF4D4F)
    val orange = Color(0xFFD98200)
}

val ACCENTS = listOf(Color(0xFF3E91FF), Color(0xFF2FA57A), Color(0xFF7A6CF0), Color(0xFFE0704A))

fun palette(dark: Boolean, accent: Color) = if (dark) Palette(
    dark = true,
    bg = Color(0xFF0E0F13),
    card = Color(0xFF17191E),
    fill = Color(0xFF23262D),
    border = Color.White.copy(alpha = .07f),
    text = Color(0xFFF2F3F5),
    muted = Color(0xFF8B90A0),
    sep = Color.White.copy(alpha = .06f),
    press = Color.White.copy(alpha = .045f),
    track = Color.White.copy(alpha = .10f),
    logoBg = Color(0xFF23262D),
    toast = Color(0xFF2A2D34),
    ok = Color(0xFF3FB68B),
    bar = Color(0xFF0E0F13).copy(alpha = .94f),
    capsule = Color(0xFF1E2026).copy(alpha = .97f),
    capBorder = Color.White.copy(alpha = .09f),
    accent = accent,
    tint = accent.copy(alpha = .18f),
    sel = accent.copy(alpha = .16f),
    glow = accent.copy(alpha = .13f),
) else Palette(
    dark = false,
    bg = Color(0xFFF2F3F6),
    card = Color.White,
    fill = Color(0xFFF2F3F6),
    border = Color.Black.copy(alpha = .06f),
    text = Color(0xFF16181D),
    muted = Color(0xFF6E7380),
    sep = Color.Black.copy(alpha = .06f),
    press = Color.Black.copy(alpha = .035f),
    track = Color.Black.copy(alpha = .08f),
    logoBg = Color(0xFFF2F3F6),
    toast = Color(0xFF24262B).copy(alpha = .96f),
    ok = Color(0xFF2E9E74),
    bar = Color(0xFFF2F3F6).copy(alpha = .94f),
    capsule = Color.White.copy(alpha = .97f),
    capBorder = Color.Black.copy(alpha = .07f),
    accent = accent,
    tint = accent.copy(alpha = .10f),
    sel = accent.copy(alpha = .09f),
    glow = accent.copy(alpha = .10f),
)

val LocalPalette = staticCompositionLocalOf { palette(false, ACCENTS[0]) }
val C: Palette @Composable get() = LocalPalette.current

@Composable
fun OstojaTheme(dark: Boolean, accent: Color, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalPalette provides palette(dark, accent), content = content)
}

/** Krzywe z prototypu. */
object Motion {
    /** cubic-bezier(.34,1.35,.64,1) - SPRING z prototypu (wejścia widoków, kapsuła) */
    val Spring = CubicBezierEasing(.34f, 1.35f, .64f, 1f)
    /** cubic-bezier(.34,1.3,.64,1) - wskaźnik kapsuły, pasek tytułu */
    val Nav = CubicBezierEasing(.34f, 1.3f, .64f, 1f)
    /** cubic-bezier(.34,1.5,.64,1) - wciśnięcie przycisku, toast */
    val Bouncy = CubicBezierEasing(.34f, 1.5f, .64f, 1f)
    /** cubic-bezier(.34,1.7,.64,1) - „pop” ikony potwierdzenia */
    val Pop = CubicBezierEasing(.34f, 1.7f, .64f, 1f)
    val Ease = CubicBezierEasing(.25f, .1f, .25f, 1f)

    fun <T> spring(ms: Int = 360, delay: Int = 0): FiniteAnimationSpec<T> = tween(ms, delay, Spring)
    fun <T> fade(ms: Int = 260, delay: Int = 0): FiniteAnimationSpec<T> = tween(ms, delay, Ease)
}

/** Typografia z prototypu (Roboto / system-ui → na Windows Segoe UI). */
object T {
    private val f = FontFamily.Default
    val hero = TextStyle(fontFamily = f, fontSize = 40.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.6).sp, lineHeight = 46.sp)
    val title = TextStyle(fontFamily = f, fontSize = 28.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.3).sp)
    val h2 = TextStyle(fontFamily = f, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
    val bar = TextStyle(fontFamily = f, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
    val row = TextStyle(fontFamily = f, fontSize = 17.sp, fontWeight = FontWeight.Medium)
    val body = TextStyle(fontFamily = f, fontSize = 16.sp)
    val bodyM = TextStyle(fontFamily = f, fontSize = 16.sp, fontWeight = FontWeight.Medium)
    val sub = TextStyle(fontFamily = f, fontSize = 13.5.sp)
    val label = TextStyle(fontFamily = f, fontSize = 13.sp)
    val btn = TextStyle(fontFamily = f, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    val code = TextStyle(fontFamily = f, fontSize = 28.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.4.sp, fontFeatureSettings = "tnum")
    val mono = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 16.sp)
    val ellipsis = TextOverflow.Ellipsis
}
