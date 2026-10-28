package pl.wolter.ostoja.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.isActive
import pl.wolter.ostoja.data.GenOptions
import pl.wolter.ostoja.data.PasswordTools
import pl.wolter.ostoja.security.Clip
import pl.wolter.ostoja.ui.Ic
import pl.wolter.ostoja.ui.components.Divider
import pl.wolter.ostoja.ui.components.IconBtn
import pl.wolter.ostoja.ui.components.LocalToaster
import pl.wolter.ostoja.ui.components.PillButton
import pl.wolter.ostoja.ui.components.StrengthBar
import pl.wolter.ostoja.ui.components.SwitchRow
import pl.wolter.ostoja.ui.components.rowPress
import pl.wolter.ostoja.ui.theme.C
import pl.wolter.ostoja.ui.theme.Mono
import pl.wolter.ostoja.ui.theme.T

/** Bieżący czas odświeżany co klatkę - do płynnych pierścieni TOTP. */
@Composable
fun rememberNow(): State<Long> = produceState(System.currentTimeMillis()) {
    while (isActive) {
        withFrameMillis { }
        value = System.currentTimeMillis()
    }
}

/** Siła hasła jak w prototypie: 1-4 segmenty, czerwony / pomarańczowy / akcent. */
fun strengthLevel(score: Int) = when (score) { 0, 1 -> 1; 2 -> 2; 3 -> 3; else -> 4 }
val STRENGTH_LABEL = listOf("", "Słabe", "Średnie", "Silne", "Bardzo silne")

@Composable
fun strengthColor(level: Int) = when (level) { 1 -> C.danger; 2 -> C.warn; else -> C.accent }

/** Arkusz One UI: zaokrąglony, z uchwytem. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OneSheet(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = C.card,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = {
            Box(Modifier.padding(top = 10.dp, bottom = 6.dp).size(36.dp, 4.dp).clip(RoundedCornerShape(2.dp)).background(C.text3.copy(alpha = .5f)))
        },
    ) {
        Column(Modifier.windowInsetsPadding(WindowInsets.navigationBars).padding(bottom = 12.dp)) { content() }
    }
}

@Composable
fun SheetAction(icon: ImageVector, text: String, danger: Boolean = false, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().rowPress(onClick = onClick).padding(horizontal = 24.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = if (danger) C.danger else C.text, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(18.dp))
        Text(text, style = T.body, color = if (danger) C.danger else C.text)
    }
}

/** Hasło z pokolorowanymi cyframi i symbolami - łatwiej je przepisać. */
@Composable
fun ColoredPassword(pw: String, fontSize: Float = 20f) {
    val digit = C.accent
    val sym = C.warn
    val text = buildAnnotatedString {
        pw.forEach { ch ->
            when {
                ch.isDigit() -> withStyle(SpanStyle(color = digit)) { append(ch) }
                !ch.isLetterOrDigit() -> withStyle(SpanStyle(color = sym)) { append(ch) }
                else -> append(ch)
            }
        }
    }
    Text(text, style = T.mono.copy(fontFamily = Mono, fontSize = fontSize.sp, lineHeight = (fontSize * 1.35f).sp), color = C.text)
}

/** Generator haseł. `onUse` != null → przycisk „Użyj” (z ekranu edycji). */
@Composable
fun GeneratorSheet(onDismiss: () -> Unit, onUse: ((String) -> Unit)? = null) {
    val ctx = LocalContext.current
    val toaster = LocalToaster.current
    var opts by remember { mutableStateOf(GenOptions()) }
    var pw by remember { mutableStateOf(PasswordTools.generate(opts)) }
    fun regen(o: GenOptions = opts) { opts = o; pw = PasswordTools.generate(o) }
    val strength = remember(pw) { PasswordTools.strength(pw) }

    OneSheet(onDismiss) {
        Text("Generator haseł", style = T.title, color = C.text, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
        Column(
            Modifier
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(C.field)
                .padding(18.dp)
        ) {
            ColoredPassword(pw)
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                val lvl = strengthLevel(strength)
                StrengthBar(lvl, strengthColor(lvl), Modifier.weight(1f))
                Spacer(Modifier.width(10.dp))
                Text(STRENGTH_LABEL[lvl], style = T.small, color = strengthColor(lvl))
                Spacer(Modifier.width(4.dp))
                IconBtn(Ic.Refresh, { regen() }, tint = C.accent, size = 36.dp, iconSize = 20.dp, desc = "Nowe hasło")
            }
        }
        Row(Modifier.padding(horizontal = 24.dp).padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Długość", style = T.body, color = C.text, modifier = Modifier.weight(1f))
            Text("${opts.length}", style = T.bodyM.copy(fontWeight = FontWeight.SemiBold), color = C.accent)
        }
        Slider(
            value = opts.length.toFloat(),
            onValueChange = { regen(opts.copy(length = it.toInt())) },
            valueRange = 8f..64f,
            colors = SliderDefaults.colors(thumbColor = C.accent, activeTrackColor = C.accent, inactiveTrackColor = C.divider),
            modifier = Modifier.padding(horizontal = 18.dp),
        )
        SwitchRow("Wielkie litery (A-Z)", null, opts.upper, { regen(opts.copy(upper = it)) })
        Divider()
        SwitchRow("Małe litery (a-z)", null, opts.lower, { regen(opts.copy(lower = it)) })
        Divider()
        SwitchRow("Cyfry (0-9)", null, opts.digits, { regen(opts.copy(digits = it)) })
        Divider()
        SwitchRow("Symbole (!@#…)", null, opts.symbols, { regen(opts.copy(symbols = it)) })
        Divider()
        SwitchRow("Bez podobnych znaków", "Pomija l, I, 1, O, 0", opts.noAmbiguous, { regen(opts.copy(noAmbiguous = it)) })
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PillButton("Kopiuj", {
                Clip.copy(ctx, "Wygenerowane hasło", pw)
                toaster.show("Skopiowano nowe hasło")
                if (onUse == null) onDismiss()
            }, primary = onUse == null, icon = Ic.Copy, modifier = Modifier.weight(1f))
            if (onUse != null) PillButton("Użyj", { onUse(pw); onDismiss() }, icon = Ic.Check, modifier = Modifier.weight(1f))
        }
    }
}
