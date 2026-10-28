package pl.wolter.ostoja.desktop

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pl.wolter.ostoja.data.Login
import pl.wolter.ostoja.data.Otp
import pl.wolter.ostoja.data.OtpAccount
import pl.wolter.ostoja.data.OtpUri
import pl.wolter.ostoja.data.countLabel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.FileDialog
import java.awt.Frame
import java.io.File

@Composable
fun DialogHost(store: Store, ui: Ui, toast: (String) -> Unit) {
    val ext = LocalExt.current
    val pair = ext?.pairing?.collectAsState()?.value
    val d = if (pair?.state == "waiting") Dialog.ExtPair else ui.dialog ?: return
    val close: () -> Unit = { if (d == Dialog.ExtPair) ext?.approve(false) else ui.dialog = null }
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = .4f))
            .clickable(remember { MutableInteractionSource() }, null) { if (d !is Dialog.Recovery) close() },
        contentAlignment = Alignment.Center,
    ) {
        val a = remember { Animatable(0f) }
        LaunchedEffect(d) { a.animateTo(1f, tween(320, easing = Motion.Spring)) }
        Box(
            Modifier.width(460.dp)
                .graphicsLayer { alpha = a.value.coerceIn(0f, 1f); val s = .94f + .06f * a.value; scaleX = s; scaleY = s }
                .shadow(30.dp, RoundedCornerShape(28.dp), ambientColor = Color.Black.copy(.2f), spotColor = Color.Black.copy(.35f))
                .clip(RoundedCornerShape(28.dp)).background(C.card).border(1.dp, C.border, RoundedCornerShape(28.dp))
                .clickable(remember { MutableInteractionSource() }, null) {}
                .padding(28.dp),
        ) {
            when (d) {
                Dialog.AddOtp -> AddOtp(store, close, toast)
                is Dialog.Recovery -> RecoveryShown(d.key, close, toast)
                Dialog.Wipe -> Wipe(store, close)
                is Dialog.EditLogin -> EditLogin(store, ui, d.id, close, toast)
                is Dialog.DeleteLogin -> DeleteLogin(store, d.id, close, toast)
                Dialog.ExtPair -> if (pair != null && ext != null) ExtPairDialog(pair, ext)
            }
        }
    }
}

@Composable
private fun Input(label: String, value: String, onChange: (String) -> Unit, placeholder: String = "", mono: Boolean = false) {
    val p = C
    Column(Modifier.padding(top = 12.dp)) {
        Text(label, style = T.label, color = p.muted, modifier = Modifier.padding(start = 16.dp, bottom = 6.dp))
        Box(Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(24.dp)).background(p.fill).padding(horizontal = 16.dp), contentAlignment = Alignment.CenterStart) {
            val style = (if (mono) T.mono.copy(fontSize = 14.sp) else T.body.copy(fontSize = 15.sp))
            if (value.isEmpty()) Text(placeholder, style = style, color = p.muted, maxLines = 1)
            BasicTextField(value, onChange, singleLine = true, textStyle = style.copy(color = p.text), cursorBrush = SolidColor(p.accent), modifier = Modifier.fillMaxWidth())
        }
    }
}

/** „Dodaj konto”: link otpauth://, sam klucz albo kod QR ze schowka / z pliku (zrzut ekranu ze strony serwisu). */
@Composable
private fun AddOtp(store: Store, close: () -> Unit, toast: (String) -> Unit) {
    val p = C
    var link by remember { mutableStateOf("") }
    var issuer by remember { mutableStateOf("") }
    var label by remember { mutableStateOf("") }
    var err by remember { mutableStateOf<String?>(null) }

    fun add(list: List<OtpAccount>) {
        val added = store.addOtps(list)
        close()
        toast(if (added.isEmpty()) "To konto już jest w Ostoi" else "Dodano " + countLabel(added.size, "konto", "konta", "kont") + " · synchronizuję z telefonem")
    }

    fun fromText(t: String?) {
        if (t == null) { err = "Nie znaleziono kodu QR"; return }
        runCatching { add(OtpUri.parse(t)) }.onFailure { err = it.message ?: "To nie jest kod konta 2FA" }
    }

    val scope = rememberCoroutineScope()
    var scanning by remember { mutableStateOf(false) }

    /** Chowa okno Ostoi, żeby nie zasłaniało strony z kodem, i szuka kodu QR na ekranie. */
    fun scanScreen() {
        if (scanning) return
        scanning = true; err = null
        scope.launch {
            val frames = Frame.getFrames().filter { it.isVisible }
            frames.forEach { it.extendedState = it.extendedState or Frame.ICONIFIED }
            delay(500)
            val text = withContext(Dispatchers.IO) { Qr.scanScreens() }
            frames.forEach { it.extendedState = it.extendedState and Frame.ICONIFIED.inv(); it.toFront() }
            scanning = false
            if (text == null) err = "Nie widać kodu QR na ekranie. Otwórz stronę z kodem obok Ostoi albo przepisz klucz poniżej."
            else fromText(text)
        }
    }

    Column {
        Text("Dodaj konto 2FA", style = T.h2, color = p.text)
        Text(
            "Otwórz w przeglądarce stronę z kodem QR i kliknij „Zeskanuj ekran”. Możesz też wkleić zrzut z kodem (Win + Shift + S) albo przepisać klucz („setup key”).",
            style = T.sub.copy(lineHeight = 20.sp), color = p.muted, modifier = Modifier.padding(top = 6.dp),
        )
        Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pill(if (scanning) "Szukam kodu…" else "Zeskanuj ekran", p.accent, Color.White, height = 40.dp, icon = Ic.Scan) { scanScreen() }
            Pill("Ze schowka", p.tint, p.accent, height = 40.dp, icon = Ic.Clipboard) {
                val img = Clipboard.image()
                val txt = Clipboard.text()?.trim().orEmpty()
                when {
                    img != null -> fromText(Qr.decode(img))
                    txt.startsWith("otpauth", ignoreCase = true) -> fromText(txt)
                    Otp.isValidSecret(txt) -> { link = Otp.normalizeSecret(txt); err = null }
                    else -> err = "W schowku nie ma kodu QR ani klucza"
                }
            }
            Pill("Z pliku…", p.fill, p.text, height = 40.dp, icon = Ic.Image) {
                val fd = FileDialog(null as Frame?, "Wybierz obraz z kodem QR", FileDialog.LOAD).apply { isVisible = true }
                val f = fd.file?.let { File(fd.directory, it) }
                if (f != null) fromText(Qr.decode(f))
            }
        }
        Input("Link albo klucz", link, { link = it.trim(); err = null }, "otpauth://… albo JBSW Y3DP EHPK 3PXP", mono = true)
        if (!link.startsWith("otpauth")) {
            Input("Serwis", issuer, { issuer = it; err = null }, "np. GitHub")
            Input("Konto", label, { label = it }, "np. anna@example.com")
        }
        if (err != null) Text(err!!, style = T.sub, color = p.danger, modifier = Modifier.padding(top = 10.dp, start = 16.dp))
        Row(Modifier.padding(top = 22.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pill("Anuluj", p.fill, p.text, height = 48.dp, modifier = Modifier.weight(1f)) { close() }
            Pill("Dodaj", p.accent, Color.White, height = 48.dp, modifier = Modifier.weight(1f)) {
                when {
                    link.startsWith("otpauth") -> fromText(link)
                    !Otp.isValidSecret(link) -> err = "Niepoprawny klucz - ma tylko litery A-Z i cyfry 2-7"
                    issuer.isBlank() -> err = "Podaj nazwę serwisu"
                    else -> add(listOf(OtpAccount(issuer = issuer.trim(), label = label.trim(), secret = Otp.normalizeSecret(link))))
                }
            }
        }
    }
}

@Composable
private fun RecoveryShown(key: String, close: () -> Unit, toast: (String) -> Unit) {
    val p = C
    Column {
        Text("Klucz odzyskiwania", style = T.h2, color = p.text)
        Text(
            "Zapisz go na papierze albo w innym bezpiecznym miejscu. Odblokuje ten komputer na 7 dni, gdy telefonu nie ma pod ręką. Pokazuję go tylko raz - poprzedni klucz przestał działać.",
            style = T.sub.copy(lineHeight = 20.sp), color = p.muted, modifier = Modifier.padding(top = 6.dp),
        )
        Box(Modifier.padding(top = 18.dp).fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(p.fill).padding(vertical = 18.dp), contentAlignment = Alignment.Center) {
            Text(key, style = T.mono.copy(fontSize = 17.sp, letterSpacing = 0.5.sp), color = p.text)
        }
        Row(Modifier.padding(top = 22.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pill("Kopiuj", p.fill, p.text, height = 48.dp, modifier = Modifier.weight(1f)) { Clipboard.copy(key, 120); toast("Skopiowano klucz · schowek wyczyszczę za 2 min") }
            Pill("Zapisałem", p.accent, Color.White, height = 48.dp, modifier = Modifier.weight(1f)) { close() }
        }
    }
}

@Composable
private fun Wipe(store: Store, close: () -> Unit) {
    val p = C
    Column {
        Text("Odłączyć ten komputer?", style = T.h2, color = p.text)
        Text(
            "Hasła i kody zostaną usunięte z komputera, a telefon przestanie się z nim synchronizować. Na telefonie wszystko zostaje.",
            style = T.sub.copy(lineHeight = 20.sp), color = p.muted, modifier = Modifier.padding(top = 6.dp),
        )
        Row(Modifier.padding(top = 22.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pill("Anuluj", p.fill, p.text, height = 48.dp, modifier = Modifier.weight(1f)) { close() }
            Pill("Odłącz i usuń", p.danger, Color.White, height = 48.dp, modifier = Modifier.weight(1f)) { store.wipe(); close() }
        }
    }
}

/** „Połączyć rozszerzenie?” - ten sam 6-cyfrowy kod widać w okienku rozszerzenia (wymiana kluczy ECDH). */
@Composable
private fun ExtPairDialog(pair: ExtPairing, ext: ExtBridge) {
    val p = C
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(48.dp).clip(androidx.compose.foundation.shape.CircleShape).background(p.tint), contentAlignment = Alignment.Center) {
                androidx.compose.material3.Icon(Ic.Globe, null, tint = p.accent, modifier = Modifier.size(24.dp))
            }
            Column(Modifier.padding(start = 14.dp)) {
                Text("Połączyć rozszerzenie?", style = T.h2, color = p.text)
                Text("${pair.name} · Ostoja dla przeglądarki", style = T.sub, color = p.muted, modifier = Modifier.padding(top = 2.dp))
            }
        }
        Text(
            "Rozszerzenie będzie mogło wypełniać i logować na stronach z Twojego sejfu. Połącz tylko, jeśli w okienku rozszerzenia widzisz ten sam kod:",
            style = T.sub.copy(lineHeight = 20.sp), color = p.muted, modifier = Modifier.padding(top = 16.dp),
        )
        Box(Modifier.padding(top = 16.dp).fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(p.fill).padding(vertical = 18.dp), contentAlignment = Alignment.Center) {
            Text(pair.code.take(3) + " " + pair.code.drop(3), style = T.hero.copy(fontSize = 38.sp, letterSpacing = 2.sp, fontFeatureSettings = "tnum"), color = p.accent)
        }
        Row(Modifier.padding(top = 22.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pill("Odrzuć", p.fill, p.text, height = 48.dp, modifier = Modifier.weight(1f)) { ext.approve(false) }
            Pill("Połącz", p.accent, Color.White, height = 48.dp, modifier = Modifier.weight(1f)) { ext.approve(true) }
        }
    }
}

/** Nowy wpis albo edycja: login, karta, notatka, Wi‑Fi. Zmiana od razu idzie na telefon. */
@Composable
private fun EditLogin(store: Store, ui: Ui, id: String?, close: () -> Unit, toast: (String) -> Unit) {
    val p = C
    val old = remember(id) { id?.let { store.value.vault.login(it) } }
    var type by remember { mutableStateOf(old?.type ?: Login.LOGIN) }
    var title by remember { mutableStateOf(old?.title.orEmpty()) }
    var user by remember { mutableStateOf(old?.username.orEmpty()) }
    var pw by remember { mutableStateOf(old?.password.orEmpty()) }
    var url by remember { mutableStateOf(old?.url.orEmpty()) }
    var note by remember { mutableStateOf(old?.note.orEmpty()) }
    var show by remember { mutableStateOf(old == null) }
    var err by remember { mutableStateOf<String?>(null) }

    fun save() {
        val t = title.trim().ifBlank { pl.wolter.ostoja.data.registrableDomain(pl.wolter.ostoja.data.hostOf(url)).substringBefore('.').replaceFirstChar { it.uppercase() } }
        if (t.isBlank()) { err = "Podaj nazwę"; return }
        val base = old ?: Login(title = t)
        val l = base.copy(
            title = t, type = type, username = user.trim(), password = pw,
            url = if (type == Login.LOGIN) url.trim().let { if (it.isNotBlank() && !it.contains("://")) "https://$it" else it } else "",
            note = note,
        )
        store.saveLogin(l)
        ui.select(l.id)
        close()
        toast(if (old == null) "Dodano ${l.title} · synchronizuję z telefonem" else "Zapisano zmiany · synchronizuję z telefonem")
    }

    Column {
        Text(if (old == null) "Nowy wpis" else "Edytuj wpis", style = T.h2, color = p.text)
        Row(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(Login.LOGIN to "Login", Login.CARD to "Karta", Login.NOTE to "Notatka", Login.WIFI to "Wi‑Fi").forEach { (k, label) ->
                val on = type == k
                Pill(label, if (on) p.accent else p.fill, if (on) Color.White else p.text, height = 34.dp, scale = .94f) { type = k }
            }
        }
        Input("Nazwa", title, { title = it; err = null }, when (type) { Login.CARD -> "np. Visa · mBank"; Login.WIFI -> "np. Dom"; Login.NOTE -> "np. Kod do bramy"; else -> "np. Allegro" })
        if (type != Login.NOTE) Input(userLabel(type), user, { user = it }, if (type == Login.LOGIN) "e-mail albo login" else "")
        if (type != Login.NOTE) {
            Column(Modifier.padding(top = 12.dp)) {
                Text(secretLabel(type), style = T.label, color = p.muted, modifier = Modifier.padding(start = 16.dp, bottom = 6.dp))
                Row(
                    Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(24.dp)).background(p.fill).padding(start = 16.dp, end = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BasicTextField(
                        pw, { pw = it }, singleLine = true,
                        textStyle = T.mono.copy(fontSize = 14.sp, color = p.text), cursorBrush = SolidColor(p.accent),
                        visualTransformation = if (show) androidx.compose.ui.text.input.VisualTransformation.None else androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        modifier = Modifier.weight(1f),
                    )
                    IconBtn(if (show) Ic.EyeOff else Ic.Eye, if (show) "Ukryj" else "Pokaż", bg = p.card, size = 34.dp) { show = !show }
                    if (type != Login.CARD) {
                        androidx.compose.foundation.layout.Spacer(Modifier.width(4.dp))
                        IconBtn(Ic.Regen, "Wygeneruj silne hasło", bg = p.card, tint = p.accent, size = 34.dp) {
                            pw = pl.wolter.ostoja.data.PasswordTools.generate(pl.wolter.ostoja.data.GenOptions(length = 20)); show = true
                        }
                    }
                }
            }
        }
        if (type == Login.LOGIN) Input("Strona logowania", url, { url = it }, "np. https://github.com/login")
        Input("Notatka", note, { note = it }, if (type == Login.CARD) "np. ważna do 09/29" else "")
        if (err != null) Text(err!!, style = T.sub, color = p.danger, modifier = Modifier.padding(top = 10.dp, start = 16.dp))
        Row(Modifier.padding(top = 22.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pill("Anuluj", p.fill, p.text, height = 48.dp, modifier = Modifier.weight(1f)) { close() }
            Pill(if (old == null) "Dodaj" else "Zapisz", p.accent, Color.White, height = 48.dp, modifier = Modifier.weight(1f)) { save() }
        }
    }
}

@Composable
private fun DeleteLogin(store: Store, id: String, close: () -> Unit, toast: (String) -> Unit) {
    val p = C
    val l = store.value.vault.login(id) ?: run { close(); return }
    Column {
        Text("Usunąć ${l.title}?", style = T.h2, color = p.text)
        Text("Wpis zniknie z komputera i z telefonu.", style = T.sub.copy(lineHeight = 20.sp), color = p.muted, modifier = Modifier.padding(top = 6.dp))
        Row(Modifier.padding(top = 22.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pill("Anuluj", p.fill, p.text, height = 48.dp, modifier = Modifier.weight(1f)) { close() }
            Pill("Usuń", p.danger, Color.White, height = 48.dp, modifier = Modifier.weight(1f)) {
                store.editVault { d -> d.copy(logins = d.logins.filterNot { it.id == id }) }
                close()
                toast("Usunięto ${l.title}")
            }
        }
    }
}
