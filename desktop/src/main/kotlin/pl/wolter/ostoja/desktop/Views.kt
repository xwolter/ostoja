package pl.wolter.ostoja.desktop

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pl.wolter.ostoja.data.Brand
import pl.wolter.ostoja.data.Flag
import pl.wolter.ostoja.data.Login
import pl.wolter.ostoja.data.Otp
import pl.wolter.ostoja.data.OtpAccount
import pl.wolter.ostoja.data.PasswordTools
import pl.wolter.ostoja.data.SecurityReport
import pl.wolter.ostoja.data.countLabel

private val CATS = listOf("all" to "Wszystkie", Login.LOGIN to "Loginy", Login.CARD to "Karty", Login.NOTE to "Notatki", Login.WIFI to "Wi‑Fi")

/** Siła hasła jak w prototypie: 1-4 segmenty. */
private fun level(pw: String) = when (PasswordTools.strength(pw)) { 0, 1 -> 1; 2 -> 2; 3 -> 3; else -> 4 }
private val ST_LABEL = listOf("", "Słabe", "Średnie", "Silne", "Bardzo silne")

@Composable
private fun flagColors(f: Flag) = when (f) {
    Flag.LEAKED -> C.danger to C.dangerBg
    else -> C.warnText to C.warnBg
}

private fun Login.domain() = Brand.domain(title, url)

/** Podpisy pól zależnie od rodzaju wpisu (jak na telefonie). */
fun userLabel(type: String) = when (type) { Login.CARD -> "Posiadacz karty"; Login.WIFI -> "Nazwa sieci (SSID)"; else -> "Nazwa użytkownika" }
fun secretLabel(type: String) = when (type) { Login.CARD -> "Numer karty"; else -> "Hasło" }

// ---------------------------------------------------------------- Sejf i Kondycja

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VaultView(store: Store, ui: Ui, st: Persisted, report: SecurityReport, scroll: ScrollState, toast: (String) -> Unit) {
    val p = C
    val health = ui.view == V_HEALTH
    val q = ui.query.trim().lowercase()
    val all = remember(st.vault) { st.vault.logins.sortedBy { it.title.lowercase() } }
    val list = when {
        health -> all.filter { report.flag(it.id) != null }
        q.isNotEmpty() -> all.filter { l -> listOf(l.title, l.username, l.url, l.note).any { it.lowercase().contains(q) } }
        else -> all.filter { ui.cat == "all" || it.type == ui.cat }
    }
    val sel = list.firstOrNull { it.id == ui.selected } ?: list.firstOrNull()
    LaunchedEffect(sel?.id) { if (sel != null && ui.selected != sel.id) ui.select(sel.id) }

    val density = LocalDensity.current.density
    var rowTop by remember { mutableFloatStateOf(0f) }
    var rowH by remember { mutableFloatStateOf(0f) }
    var detH by remember { mutableFloatStateOf(0f) }
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (!health && q.isEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Pill("Nowy wpis", p.accent, Color.White, height = 36.dp, icon = Ic.Plus, scale = .96f) { ui.dialog = Dialog.EditLogin(null) }
            CATS.forEach { (k, label) ->
                val on = ui.cat == k
                val bg by animateColorAsState(if (on) p.accent else p.card, tween(240), label = "cat$k")
                val fg by animateColorAsState(if (on) Color.White else p.text, tween(240), label = "catf$k")
                Pill(label, bg, fg, height = 36.dp, border = if (on) null else p.border) { if (!on) { ui.cat = k; ui.viewKey++ } }
            }
        }
        Row(
            Modifier.fillMaxWidth().onGloballyPositioned { rowTop = it.positionInRoot().y; rowH = it.size.height.toFloat() },
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Card(Modifier.weight(1f).widthIn(min = 420.dp)) {
                Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    if (list.isEmpty()) Empty(
                        when {
                            health -> "Wszystkie hasła są w porządku"
                            q.isNotEmpty() -> "Nic nie pasuje do „${ui.query.trim()}”"
                            st.vault.logins.isEmpty() -> "Sejf jest pusty - dodaj wpis na telefonie"
                            else -> "Brak wpisów w tej kategorii"
                        },
                    )
                    list.forEachIndexed { i, l ->
                        key(l.id) {
                            VaultRow(
                                l, i, ui.viewKey, report.flag(l.id),
                                isNew = (st.arrived[l.id] ?: 0) > System.currentTimeMillis() - 10 * 60_000,
                                selected = sel?.id == l.id, icons = st.prefs.icons, has2fa = st.vault.otp(l.otpId) != null,
                            ) {
                                ui.select(l.id)
                            }
                        }
                    }
                }
            }
            // Panel szczegółów „przykleja się” pod paskiem tytułu (position: sticky; top: 76px).
            val stick = (76 * density - rowTop).coerceIn(0f, maxOf(0f, rowH - detH))
            Box(
                Modifier.width(400.dp)
                    .graphicsLayer { translationY = stick }
                    .onGloballyPositioned { detH = it.size.height.toFloat() },
            ) {
                if (sel != null) key(ui.detailKey) { Detail(store, ui, st, sel, report, toast) }
                else if (list.isNotEmpty()) Card(Modifier.fillMaxWidth()) { Empty("Wybierz wpis z listy") }
            }
        }
    }
}

@Composable
private fun Empty(text: String) {
    Box(Modifier.fillMaxWidth().padding(vertical = 40.dp, horizontal = 20.dp), contentAlignment = Alignment.Center) {
        Text(text, style = T.body, color = C.muted)
    }
}

@Composable
private fun VaultRow(
    l: Login, i: Int, trigger: Int, flag: Flag?, isNew: Boolean,
    selected: Boolean, icons: Boolean, has2fa: Boolean, onClick: () -> Unit,
) {
    val p = C
    Row(
        Modifier.staggerIn(i, trigger).fillMaxWidth()
            .rowHover(if (selected) p.sel else Color.Transparent, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Logo(l.title, l.domain(), icons = icons, letter = !l.isLogin)
        Column(Modifier.weight(1f)) {
            Text(l.title, style = T.row.copy(fontSize = 16.sp), color = p.text, maxLines = 1, overflow = T.ellipsis)
            val sub = when (l.type) {
                Login.CARD -> "•••• " + l.password.filter { it.isDigit() }.takeLast(4)
                Login.NOTE -> "Notatka zabezpieczona"
                else -> l.username
            }
            Text(sub, style = T.sub, color = p.muted, maxLines = 1, overflow = T.ellipsis, modifier = Modifier.padding(top = 2.dp))
        }
        if (isNew) Tag("Z telefonu", p.tint, p.accent)
        if (flag != null) flagColors(flag).let { (c, b) -> Tag(flag.label, b, c) }
        if (has2fa) Tag("2FA", p.tint, p.accent, bold = true)
    }
}

@Composable
private fun Detail(store: Store, ui: Ui, st: Persisted, l: Login, report: SecurityReport, toast: (String) -> Unit) {
    val p = C
    val otp = st.vault.otp(l.otpId)
    Column(Modifier.staggerIn(0, Unit, dy = 0f, dx = 24f), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Card {
            Column(Modifier.padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 4.dp, top = 4.dp, bottom = 18.dp)) {
                    Logo(l.title, l.domain(), size = 56.dp, icons = st.prefs.icons)
                    Column(Modifier.padding(start = 14.dp).weight(1f)) {
                        Text(l.title, style = T.title.copy(fontSize = 24.sp), color = p.text, maxLines = 1, overflow = T.ellipsis)
                        val sub = when (l.type) { Login.CARD -> "Karta płatnicza"; Login.NOTE -> "Notatka zabezpieczona"; Login.WIFI -> "Sieć Wi‑Fi"; else -> l.domain().ifBlank { pl.wolter.ostoja.data.hostOf(l.url) } }
                        Text(sub, style = T.btn.copy(fontWeight = FontWeight.Normal), color = p.accent, modifier = Modifier.padding(top = 2.dp))
                    }
                    IconBtn(Ic.Edit, "Edytuj", bg = p.fill, size = 36.dp) { ui.dialog = Dialog.EditLogin(l.id) }
                    Spacer(Modifier.width(6.dp))
                    IconBtn(Ic.Trash, "Usuń", bg = p.fill, tint = p.danger, size = 36.dp) { ui.dialog = Dialog.DeleteLogin(l.id) }
                }
                if (l.isLogin) {
                    LoginButton(l, toast)
                    Spacer(Modifier.height(10.dp))
                }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (l.type != Login.NOTE || l.username.isNotBlank()) Field(userLabel(l.type), l.username.ifBlank { "-" }) {
                        IconBtn(Ic.Copy, "Kopiuj") { Clipboard.copy(l.username, 0); toast("Skopiowano") }
                    }
                    if (l.password.isNotEmpty()) Field(secretLabel(l.type), if (ui.reveal) l.password else "•".repeat(minOf(l.password.length, 16)), mono = true) {
                        IconBtn(if (ui.reveal) Ic.EyeOff else Ic.Eye, if (ui.reveal) "Ukryj" else "Pokaż") { ui.reveal = !ui.reveal }
                        IconBtn(Ic.Copy, "Kopiuj") { Clipboard.copy(l.password); toast("Skopiowano · schowek wyczyszczę za 30 s") }
                    }
                    if (otp != null) {
                        val now by rememberFrameNow()
                        val code = Otp.code(otp, now)
                        val rem = Otp.remainingMs(otp.period, now) / 1000f
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(p.fill)
                                .rowHover(radius = 16.dp) { Clipboard.copy(code); toast("Skopiowano kod · ${l.title}") }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text("Kod 2FA · kliknij, aby skopiować", style = T.label, color = p.muted)
                                CodeText(code, st.prefs.groupDigits, 28.sp, Modifier.padding(top = 2.dp))
                            }
                            Ring(if (otp.isHotp) 1f else rem / otp.period, !otp.isHotp && rem <= 5, 30.dp)
                        }
                    }
                    if (l.note.isNotBlank()) Field("Notatka", l.note) {
                        IconBtn(Ic.Copy, "Kopiuj") { Clipboard.copy(l.note); toast("Skopiowano notatkę") }
                    }
                }
            }
        }
        if ((l.type == Login.LOGIN || l.type == Login.WIFI) && l.password.isNotEmpty()) StrengthCard(store, ui, l, report.flag(l.id), toast)
    }
}

/**
 * „Otwórz i zaloguj”: z rozszerzeniem Firefox strona otwiera się i loguje sama (login, hasło, kod 2FA).
 * Bez rozszerzenia - otwiera stronę w domyślnej przeglądarce i kładzie hasło do schowka.
 */
@Composable
private fun LoginButton(l: Login, toast: (String) -> Unit) {
    val p = C
    val ext = LocalExt.current
    // Czy rozszerzenie jest teraz połączone (odświeżane co 2 s - zależy od jego długich zapytań).
    val st by androidx.compose.runtime.produceState(ext?.anyOnline == true, ext) {
        while (true) { value = ext?.anyOnline == true; kotlinx.coroutines.delay(2000) }
    }
    val url = ExtBridge.loginUrl(l)
    if (url.isBlank()) return
    Row(
        Modifier.fillMaxWidth().press(.97f) {
            if (ext != null && ext.queueLogin(l.id, "Ostoja na Windows")) toast("Otwieram ${pl.wolter.ostoja.data.hostOf(url)} w Firefoksie i loguję…")
            else {
                runCatching { java.awt.Desktop.getDesktop().browse(java.net.URI(url)) }
                Clipboard.copy(l.password)
                toast("Otworzyłem stronę · hasło w schowku. Z rozszerzeniem Ostoja zaloguję się sam.")
            }
        }.height(46.dp).clip(RoundedCornerShape(23.dp)).background(p.tint),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center,
    ) {
        Icon(Ic.External, null, tint = p.accent, modifier = Modifier.size(18.dp))
        Text(if (st) "Otwórz i zaloguj w Firefoksie" else "Otwórz stronę i skopiuj hasło", style = T.btn.copy(fontSize = 15.sp), color = p.accent, modifier = Modifier.padding(start = 8.dp))
    }
}

/** Pole w szczegółach: kafelek z etykietą, wartością i przyciskami po prawej. */
@Composable
private fun Field(label: String, value: String, mono: Boolean = false, actions: @Composable () -> Unit) {
    val p = C
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(p.fill).padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Column(Modifier.weight(1f).padding(vertical = 2.dp)) {
            Text(label, style = T.label, color = p.muted)
            Text(value, style = if (mono) T.mono.copy(fontSize = 15.sp) else T.body.copy(fontSize = 15.sp), color = p.text, maxLines = 1, overflow = T.ellipsis, modifier = Modifier.padding(top = 3.dp))
        }
        actions()
    }
}

@Composable
fun CodeText(code: String, group: Boolean, size: androidx.compose.ui.unit.TextUnit, modifier: Modifier = Modifier) {
    val half = if (code.length == 8) 4 else 3
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(if (group) 8.dp else 0.dp)) {
        val s = T.code.copy(fontSize = size)
        if (group) { Text(code.take(half), style = s, color = C.accent); Text(code.drop(half), style = s, color = C.accent) }
        else Text(code, style = s, color = C.accent)
    }
}

@Composable
private fun StrengthCard(store: Store, ui: Ui, l: Login, flag: Flag?, toast: (String) -> Unit) {
    val p = C
    val lv = level(l.password)
    val stColor = when (lv) { 1 -> p.danger; 2 -> p.orange; else -> p.accent }
    val labelColor by animateColorAsState(stColor, tween(260), label = "st")
    Card {
        Column(Modifier.padding(horizontal = 22.dp, vertical = 20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text("Siła hasła", style = T.bodyM, color = p.text, modifier = Modifier.weight(1f))
                Text("${ST_LABEL[lv]} · ${countLabel(l.password.length, "znak", "znaki", "znaków")}", style = T.btn.copy(fontSize = 13.5.sp), color = labelColor)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                (1..4).forEach { n ->
                    val c by animateColorAsState(if (lv >= n) stColor else p.track, tween(300, delayMillis = (n - 1) * 60), label = "seg$n")
                    Box(Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(3.dp)).background(c))
                }
            }
            if (flag != null) Text(flag.note, style = T.btn.copy(fontWeight = FontWeight.Normal, lineHeight = 20.sp), color = p.muted)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Długość", style = T.btn.copy(fontSize = 13.5.sp, fontWeight = FontWeight.Normal), color = p.muted, modifier = Modifier.padding(end = 4.dp))
                listOf(16, 20, 24).forEach { n ->
                    val on = ui.len == n
                    val bg by animateColorAsState(if (on) p.accent else p.fill, tween(220), label = "len$n")
                    val fg by animateColorAsState(if (on) Color.White else p.text, tween(220), label = "lenf$n")
                    Pill("$n", bg, fg, height = 34.dp, style = T.btn.copy(fontSize = 13.5.sp), scale = .92f) { ui.len = n }
                }
            }
            val rot by animateFloatAsState(ui.regenTurns * 360f, tween(500, easing = androidx.compose.animation.core.CubicBezierEasing(.34f, 1.4f, .64f, 1f)), label = "rot")
            Row(
                Modifier.fillMaxWidth().press {
                    store.regenerate(l.id, ui.len)
                    ui.reveal = true
                    ui.regenTurns += 1f
                    toast("Nowe hasło zapisane · synchronizuję z telefonem")
                }.height(46.dp).clip(RoundedCornerShape(23.dp)).background(p.accent),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Icon(Ic.Regen, null, tint = Color.White, modifier = Modifier.size(17.dp).graphicsLayer { rotationZ = rot })
                Text("Generuj nowe hasło", style = T.btn.copy(fontSize = 15.sp), color = Color.White, modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}

// ---------------------------------------------------------------- Kody 2FA

@Composable
fun CodesView(ui: Ui, st: Persisted, toast: (String) -> Unit) {
    val p = C
    val now by rememberFrameNow()
    val rem = Otp.remainingMs(30, now) / 1000f
    val accounts = remember(st.vault) { st.vault.otps.sortedWith(compareByDescending<OtpAccount> { it.favorite }.thenBy { it.title.lowercase() }) }
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pill("Dodaj konto", p.accent, Color.White, height = 36.dp, icon = Ic.Plus, scale = .96f) { ui.dialog = Dialog.AddOtp }
            Row(
                Modifier.height(36.dp).clip(RoundedCornerShape(18.dp)).background(p.card).border(1.dp, p.border, RoundedCornerShape(18.dp)).padding(start = 10.dp, end = 14.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Ring(rem / 30f, rem <= 5, 18.dp, radiusRatio = 9f / 12f)
                Text("Nowe kody za ${kotlin.math.ceil(rem).toInt()} s", style = T.btn.copy(fontWeight = FontWeight.Normal, fontFeatureSettings = "tnum"), color = p.muted)
            }
        }
        if (accounts.isEmpty()) Card(Modifier.fillMaxWidth()) { Empty("Brak kont 2FA - dodaj pierwsze albo zeskanuj kod na telefonie") }
        // Siatka kafelków: na szerokim oknie 3 kolumny, na węższym 2.
        BoxWithConstraints {
            val cols = maxOf(1, ((maxWidth + 14.dp) / (330.dp + 14.dp)).toInt())
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                accounts.chunked(cols).forEachIndexed { r, row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        row.forEachIndexed { c, a ->
                            key(a.id) {
                                val code = Otp.code(a, now)
                                val left = Otp.remainingMs(a.period, now) / 1000f
                                Card(Modifier.weight(1f).staggerIn(r * cols + c, ui.viewKey)) {
                                    Column(Modifier.fillMaxWidth().rowHover(radius = 24.dp) { Clipboard.copy(code); toast("Skopiowano kod · ${a.title}") }.padding(18.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                            Logo(a.title, Brand.domain(a.issuer), size = 40.dp, icons = st.prefs.icons)
                                            Column(Modifier.weight(1f)) {
                                                Text(a.title, style = T.bodyM, color = p.text, maxLines = 1, overflow = T.ellipsis)
                                                Text(a.label.ifBlank { "Kod 2FA" }, style = T.sub, color = p.muted, maxLines = 1, overflow = T.ellipsis, modifier = Modifier.padding(top = 2.dp))
                                            }
                                            Ring(if (a.isHotp) 1f else left / a.period, !a.isHotp && left <= 5, 26.dp)
                                        }
                                        CodeText(code, st.prefs.groupDigits, 32.sp, Modifier.padding(top = 14.dp, start = 2.dp))
                                    }
                                }
                            }
                        }
                        repeat(cols - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Urządzenia

@Composable
fun DevicesView(store: Store, ui: Ui, st: Persisted) {
    val p = C
    val now by rememberNow()
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f).widthIn(min = 420.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Card {
                Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    var i = 0
                    st.phones.sortedByDescending { it.lastSeen }.forEach { ph ->
                        val online = now - ph.lastSeen < 75_000
                        DeviceRow(i++, ui.viewKey, Ic.Phone, ph.name, "Telefon · zsynchronizowano ${ago(maxOf(ph.lastSync, ph.lastSeen), now)}",
                            if (online) "Online" else "Offline", if (online) p.ok else p.muted)
                    }
                    DeviceRow(i++, ui.viewKey, Ic.Laptop, store.computerName, "Ten komputer · Ostoja na Windows", "Online", p.ok)
                    val ext = LocalExt.current
                    st.extensions.forEach { e ->
                        val online = ext?.online(e, now) == true
                        DeviceRow(i++, ui.viewKey, Ic.Globe, e.name, "Rozszerzenie przeglądarki · połączone ${dayMonth(e.pairedAt)}",
                            if (online) "Online" else "Offline", if (online) p.ok else p.muted) {
                            IconBtn(Ic.Close, "Odłącz rozszerzenie", bg = p.fill, size = 32.dp) { ext?.remove(e.id) }
                        }
                    }
                }
            }
            if (st.extensions.isEmpty()) Card {
                Row(Modifier.padding(22.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Box(Modifier.size(44.dp).clip(CircleShape).background(p.tint), contentAlignment = Alignment.Center) {
                        Icon(Ic.Globe, null, tint = p.accent, modifier = Modifier.size(22.dp))
                    }
                    Column {
                        Text("Logowanie jednym kliknięciem", style = T.bodyM, color = p.text)
                        Text(
                            "Zainstaluj rozszerzenie Ostoja w Firefoksie (plik Ostoja-Firefox.xpi) i kliknij w nim „Połącz”. Wtedy wybrana strona otworzy się i zaloguje sama - razem z kodem 2FA.",
                            style = T.sub.copy(lineHeight = 19.sp), color = p.muted, modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }
            Card {
                Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    ActionRow(Ic.Padlock, "Zablokuj teraz", "Odblokowane do ${dayMonth(st.unlockedUntil)} · potem potwierdzenie na telefonie", p.accent) { store.lockNow() }
                    ActionRow(
                        Ic.Key,
                        if (st.recovery == null) "Utwórz klucz odzyskiwania" else "Utwórz nowy klucz odzyskiwania",
                        "Odblokowuje komputer, gdy telefonu nie ma pod ręką", p.accent,
                    ) { ui.dialog = Dialog.Recovery(store.newRecoveryKey()) }
                    ActionRow(Ic.Trash, "Odłącz i usuń dane z komputera", "Sejf zostaje tylko na telefonie", p.danger) { ui.dialog = Dialog.Wipe }
                }
            }
        }
        PairCard(store, Modifier.width(400.dp))
    }
}

@Composable
private fun DeviceRow(i: Int, trigger: Int, icon: ImageVector, name: String, meta: String, state: String, dot: Color, trailing: @Composable () -> Unit = {}) {
    val p = C
    Row(
        Modifier.staggerIn(i, trigger).fillMaxWidth().rowHover {}.padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(Modifier.size(44.dp).clip(CircleShape).background(p.tint), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = p.accent, modifier = Modifier.size(22.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(name, style = T.bodyM, color = p.text)
            Text(meta, style = T.sub, color = p.muted, modifier = Modifier.padding(top = 3.dp))
        }
        Row(
            Modifier.height(28.dp).clip(RoundedCornerShape(14.dp)).background(p.fill).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(Modifier.size(7.dp).clip(CircleShape).background(dot))
            Text(state, style = T.label.copy(fontSize = 12.5.sp, fontWeight = FontWeight.Medium), color = p.text)
        }
        trailing()
    }
}

@Composable
private fun ActionRow(icon: ImageVector, title: String, sub: String, color: Color, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().rowHover(onClick = onClick).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(Modifier.size(44.dp).clip(CircleShape).background(color.copy(alpha = .12f)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = color, modifier = Modifier.size(21.dp))
        }
        Column {
            Text(title, style = T.bodyM, color = color)
            Text(sub, style = T.sub, color = C.muted, modifier = Modifier.padding(top = 3.dp))
        }
    }
}

/** „Połącz nowy telefon”: prawdziwy kod QR parowania (ważny 10 minut, odświeżany sam). */
@Composable
fun PairCard(store: Store, modifier: Modifier = Modifier, title: String = "Połącz nowy telefon") {
    val p = C
    var info by remember { mutableStateOf(store.pairingInfo()) }
    LaunchedEffect(Unit) {
        while (true) { kotlinx.coroutines.delay(30_000); info = store.pairingInfo() }
    }
    Card(modifier) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Box(Modifier.size(184.dp).clip(RoundedCornerShape(20.dp)).background(Color.White).border(1.dp, p.border, RoundedCornerShape(20.dp)).padding(12.dp)) {
                QrCode(info.toUri(), 160.dp, Color(0xFF111111))
            }
            Column {
                Text(title, style = T.h2, color = p.text)
                Column(Modifier.padding(top = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Step(1, "Otwórz Ostoję na telefonie")
                    Step(2, "Ustawienia → Połącz z komputerem")
                    Step(3, "Zeskanuj kod i potwierdź odciskiem palca")
                }
                Text(
                    if (info.hosts.isEmpty()) "Nie wykryto sieci Wi‑Fi ani Ethernet - połącz komputer z siecią, w której jest telefon."
                    else "Synchronizacja działa automatycznie w tle, w tej samej sieci Wi‑Fi, z szyfrowaniem end‑to‑end. Bez chmury i bez konta.",
                    style = T.btn.copy(fontSize = 13.5.sp, fontWeight = FontWeight.Normal, lineHeight = 20.sp), color = p.muted,
                    modifier = Modifier.padding(top = 16.dp),
                )
            }
        }
    }
}

@Composable
private fun Step(n: Int, text: String) {
    val p = C
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(24.dp).clip(CircleShape).background(p.tint), contentAlignment = Alignment.Center) {
            Text("$n", style = T.label.copy(fontWeight = FontWeight.SemiBold), color = p.accent)
        }
        Text(text, style = T.body.copy(fontSize = 15.sp), color = p.text)
    }
}
