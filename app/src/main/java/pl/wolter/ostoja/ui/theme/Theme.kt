package pl.wolter.ostoja.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import pl.wolter.ostoja.R
import pl.wolter.ostoja.data.AppSettings
import pl.wolter.ostoja.data.SettingsStore

/** Kolory aplikacji (jasny i ciemny motyw). */
@Immutable
data class Palette(
    val dark: Boolean,
    val bg: Color,
    val card: Color,
    val text: Color,
    val text2: Color,
    val text3: Color,
    val divider: Color,
    val field: Color,
    val accent: Color,
    /** --tint: akcent 10% (16% w ciemnym) */
    val accentSoft: Color,
    val track: Color,
    val logoBg: Color,
    val press: Color,
    val toast: Color,
    val toggleOff: Color,
    val danger: Color,
    val warn: Color,
    val warnText: Color,
    val ringWarn: Color,
    val good: Color,
    val capsule: Color,
    val capsuleBorder: Color,
)

fun palette(dark: Boolean, accent: Color): Palette = if (!dark) Palette(
    dark = false,
    bg = Color(0xFFF6F6F6),
    card = Color.White,
    text = Color(0xFF1A1A1A),
    text2 = Color(0xFF7A7A7A),
    text3 = Color(0xFFA6A6AB),
    divider = Color(0x12000000),
    field = Color(0xFFF1F1F3),
    accent = accent,
    accentSoft = accent.copy(alpha = .10f).compositeOver(Color.White),
    track = Color(0x14000000),
    logoBg = Color(0xFFF1F1F3),
    press = Color(0x0B000000),
    toast = Color(0xF0242426),
    toggleOff = Color(0xFFD6D6DA),
    danger = Color(0xFFE5484D),
    warn = Color(0xFFD98200),
    warnText = Color(0xFFC27400),
    ringWarn = Color(0xFFFF4D4F),
    good = Color(0xFF2FA57A),
    capsule = Color(0xF7FFFFFF),
    capsuleBorder = Color(0x12000000),
) else Palette(
    dark = true,
    bg = Color(0xFF000000),
    card = Color(0xFF1C1C1E),
    text = Color.White,
    text2 = Color(0xFF8E8E93),
    text3 = Color(0xFF636366),
    divider = Color(0x14FFFFFF),
    field = Color(0xFF2C2C2E),
    accent = accent,
    accentSoft = accent.copy(alpha = .16f).compositeOver(Color(0xFF1C1C1E)),
    track = Color(0x21FFFFFF),
    logoBg = Color(0xFF2C2C2E),
    press = Color(0x0FFFFFFF),
    toast = Color(0xFF3A3A3C),
    toggleOff = Color(0xFF48484A),
    danger = Color(0xFFFF6369),
    warn = Color(0xFFD98200),
    warnText = Color(0xFFF0A030),
    ringWarn = Color(0xFFFF4D4F),
    good = Color(0xFF3CC08E),
    capsule = Color(0xF52C2C2E),
    capsuleBorder = Color(0x17FFFFFF),
)

val LocalPalette = staticCompositionLocalOf { palette(false, Color(0xFF3E91FF)) }

/** Skrót do bieżącej palety: `C.accent`. */
val C: Palette
    @Composable get() = LocalPalette.current

val Mono = FontFamily(Font(R.font.roboto_mono, FontWeight.Normal))

/** Krzywe z prototypu. */
object Motion {
    /** cubic-bezier(.34,1.3,.64,1) - kapsuła, wskaźnik zakładki, toast */
    val Springy = CubicBezierEasing(0.34f, 1.3f, 0.64f, 1f)
    /** cubic-bezier(.34,1.5,.64,1) - wciśnięcia przycisków i kart */
    val Bouncy = CubicBezierEasing(0.34f, 1.5f, 0.64f, 1f)
    /** cubic-bezier(.34,1.35,.64,1) - chowanie kapsuły */
    val Hide = CubicBezierEasing(0.34f, 1.35f, 0.64f, 1f)

    fun <T> nav() = tween<T>(300, easing = Springy)
    fun <T> press() = tween<T>(200, easing = Bouncy)
    fun <T> toggle() = tween<T>(280, easing = Bouncy)
}

object T {
    private val tnum = "tnum"
    val big = TextStyle(fontSize = 34.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.4).sp, lineHeight = 39.sp)
    val bar = TextStyle(fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
    val title = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
    val head = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.Medium)
    val row = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.Medium)
    val body = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.Normal, lineHeight = 22.sp)
    val bodyM = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Medium, lineHeight = 20.sp)
    val sub = TextStyle(fontSize = 13.5.sp, fontWeight = FontWeight.Normal, lineHeight = 18.sp)
    val subtitle = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Normal)
    val chip = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium)
    val small = TextStyle(fontSize = 12.5.sp, fontWeight = FontWeight.Normal, lineHeight = 16.sp)
    val section = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium)
    val code = TextStyle(fontSize = 25.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.4.sp, fontFeatureSettings = tnum)
    val num = TextStyle(fontFeatureSettings = tnum)
    val mono = TextStyle(fontFamily = Mono, fontSize = 17.sp, letterSpacing = 0.5.sp)
}

@Composable
fun OstojaTheme(s: AppSettings, content: @Composable () -> Unit) {
    val dark = when (s.theme) { 1 -> false; 2 -> true; else -> isSystemInDarkTheme() }
    val accent = Color(SettingsStore.ACCENTS.getOrElse(s.accent) { SettingsStore.ACCENTS[0] })
    val p = palette(dark, accent)
    val scheme = if (dark) darkColorScheme(
        primary = accent, onPrimary = Color.White, background = p.bg, surface = p.card, onSurface = p.text,
        surfaceContainerLow = p.card, surfaceContainer = p.card, surfaceContainerHigh = p.card, surfaceContainerHighest = p.field,
        onSurfaceVariant = p.text2, outline = p.text3, outlineVariant = p.divider, secondaryContainer = p.accentSoft,
    ) else lightColorScheme(
        primary = accent, onPrimary = Color.White, background = p.bg, surface = p.card, onSurface = p.text,
        surfaceContainerLow = p.card, surfaceContainer = p.card, surfaceContainerHigh = p.card, surfaceContainerHighest = p.field,
        onSurfaceVariant = p.text2, outline = p.text3, outlineVariant = p.divider, secondaryContainer = p.accentSoft,
    )
    CompositionLocalProvider(LocalPalette provides p) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
