package pl.wolter.ostoja.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import kotlinx.coroutines.launch
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pl.wolter.ostoja.app
import pl.wolter.ostoja.data.AppSettings
import pl.wolter.ostoja.data.GenOptions
import pl.wolter.ostoja.data.Login
import pl.wolter.ostoja.data.Otp
import pl.wolter.ostoja.data.PasswordTools
import pl.wolter.ostoja.data.VaultData
import pl.wolter.ostoja.data.hostOf
import pl.wolter.ostoja.data.securityReport
import pl.wolter.ostoja.security.Clip
import pl.wolter.ostoja.ui.Ic
import pl.wolter.ostoja.ui.Nav
import pl.wolter.ostoja.ui.Route
import pl.wolter.ostoja.ui.components.Avatar
import pl.wolter.ostoja.ui.components.CodeText
import pl.wolter.ostoja.ui.components.CountdownRing
import pl.wolter.ostoja.ui.components.Divider
import pl.wolter.ostoja.ui.components.Group
import pl.wolter.ostoja.ui.components.IconBtn
import pl.wolter.ostoja.ui.components.LocalToaster
import pl.wolter.ostoja.ui.components.OneDialog
import pl.wolter.ostoja.ui.components.OneUiPage
import pl.wolter.ostoja.ui.components.PillButton
import pl.wolter.ostoja.ui.components.SectionLabel
import pl.wolter.ostoja.ui.components.StrengthBar
import pl.wolter.ostoja.ui.components.rowPress
import pl.wolter.ostoja.ui.theme.C
import pl.wolter.ostoja.ui.theme.T
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val dayFmt = SimpleDateFormat("d MMMM yyyy", Locale("pl"))
private val dateFmt = SimpleDateFormat("d MMM yyyy, HH:mm", Locale("pl"))

@Composable
fun ItemScreen(id: String, nav: Nav, vault: VaultData, settings: AppSettings) {
    val l = vault.login(id)
    if (l == null) {
        LaunchedEffect(Unit) { nav.pop() }
        return
    }
    val ctx = LocalContext.current
    val toaster = LocalToaster.current
    var reveal by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var showHistory by remember { mutableStateOf(false) }
    var wifiQr by remember { mutableStateOf(false) }
    val otp = vault.otp(l.otpId)
    val flag = remember(vault) { vault.securityReport().flag(l.id) }
    val now = rememberNow()

    fun copy(value: String, label: String, msg: String, sensitive: Boolean = true) {
        Clip.copy(ctx, "$label · ${l.title}", value, sensitive)
        toaster.show(msg)
    }
    val pwMsg = "Skopiowano hasło · wyczyszczę za ${settings.clipClearSec} s"

    OneUiPage(
        title = l.title,
        subtitle = when (l.type) {
            Login.CARD -> "Karta płatnicza"
            Login.NOTE -> "Notatka zabezpieczona"
            Login.WIFI -> "Sieć Wi‑Fi"
            else -> hostOf(l.url).ifBlank { l.username.ifBlank { "Login" } }
        },
        onBack = { nav.pop() },
        headerTop = {
            Box(Modifier.padding(bottom = 18.dp).size(60.dp).clip(CircleShape).background(C.card), contentAlignment = Alignment.Center) {
                Avatar(l.title, l.url, size = 60.dp, type = l.type)
            }
        },
        actions = {
            Box {
                IconBtn(Ic.More, { menu = true }, desc = "Więcej")
                DropdownMenu(menu, { menu = false }, containerColor = C.card, shape = RoundedCornerShape(22.dp)) {
                    MenuItem(Ic.Edit, "Edytuj") { menu = false; nav.push(Route.EditLogin(l.id)) }
                    MenuItem(if (l.favorite) Ic.StarFilled else Ic.Star, if (l.favorite) "Usuń z ulubionych" else "Dodaj do ulubionych") {
                        menu = false; ctx.app.vault.toggleFavorite(l.id)
                    }
                    if (l.type == Login.WIFI) MenuItem(Ic.Qr, "Kod QR sieci") { menu = false; wifiQr = true }
                    MenuItem(Ic.Trash, "Usuń", danger = true) { menu = false; confirmDelete = true }
                }
            }
        },
    ) {
        item(key = "fields") {
            Group {
                when (l.type) {
                    Login.NOTE -> FieldRow("Treść", l.note.ifBlank { "-" }, maxLines = 30, onClick = { copy(l.note, "Notatka", "Skopiowano notatkę") }) {
                        IconBtn(Ic.Copy, { copy(l.note, "Notatka", "Skopiowano notatkę") }, tint = C.accent, iconSize = 20.dp)
                    }
                    else -> {
                        val userLabel = when (l.type) { Login.CARD -> "Właściciel karty"; Login.WIFI -> "Nazwa sieci (SSID)"; else -> "Nazwa użytkownika" }
                        val pwLabel = when (l.type) { Login.CARD -> "Numer karty"; else -> "Hasło" }
                        if (l.username.isNotBlank()) {
                            FieldRow(userLabel, l.username, onClick = { copy(l.username, userLabel, "Skopiowano nazwę użytkownika", false) }) {
                                IconBtn(Ic.Copy, { copy(l.username, userLabel, "Skopiowano nazwę użytkownika", false) }, tint = C.accent, iconSize = 20.dp)
                            }
                            Divider()
                        }
                        val masked = if (l.type == Login.CARD) "•••• •••• •••• " + l.password.filter { it.isDigit() }.takeLast(4)
                        else "•".repeat(l.password.length.coerceIn(8, 14))
                        Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 12.dp, top = 14.dp, bottom = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(pwLabel, style = T.sub, color = C.text2)
                                Text(
                                    if (l.password.isEmpty()) "-" else if (reveal) l.password else masked,
                                    style = T.mono, color = C.text, maxLines = if (reveal) 3 else 1, overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(top = 4.dp),
                                )
                            }
                            if (l.password.isNotEmpty()) {
                                IconBtn(if (reveal) Ic.EyeOff else Ic.Eye, { reveal = !reveal }, tint = C.text2, iconSize = 21.dp, desc = "Pokaż")
                                IconBtn(Ic.Copy, { copy(l.password, pwLabel, if (l.type == Login.CARD) "Skopiowano numer karty" else pwMsg) }, tint = C.accent, iconSize = 20.dp, desc = "Kopiuj")
                            }
                        }
                        if (otp != null) {
                            Divider()
                            val counter by remember(otp) { derivedStateOf { Otp.counterAt(otp, now.value) } }
                            val code = remember(otp, counter) { Otp.code(otp, if (otp.isHotp) System.currentTimeMillis() else counter * otp.period * 1000L) }
                            val warn by remember(otp) { derivedStateOf { !otp.isHotp && Otp.remainingMs(otp.period, now.value) <= 5000 } }
                            Row(
                                Modifier.fillMaxWidth().rowPress { copy(code, "Kod 2FA", "Skopiowano kod · ${l.title}") }
                                    .padding(start = 24.dp, end = 22.dp, top = 14.dp, bottom = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text("Kod 2FA", style = T.sub, color = C.text2)
                                    CodeText(code, C.accent, Modifier.padding(top = 2.dp))
                                }
                                if (otp.isHotp) IconBtn(Ic.Refresh, { ctx.app.vault.nextHotp(otp.id) }, tint = C.accent)
                                else CountdownRing({ Otp.remainingMs(otp.period, now.value) / (otp.period * 1000f) }, warn)
                            }
                        }
                        if (l.type == Login.LOGIN && l.url.isNotBlank()) {
                            Divider()
                            FieldRow("Strona", hostOf(l.url).ifBlank { l.url }, valueColor = C.accent, onClick = {
                                val u = if (l.url.contains("://")) l.url else "https://${l.url}"
                                runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(u))) }
                            })
                        }
                        if (l.type != Login.LOGIN && l.note.isNotBlank()) {
                            Divider()
                            FieldRow("Notatka", l.note, maxLines = 8, onClick = { copy(l.note, "Notatka", "Skopiowano notatkę", false) })
                        }
                    }
                }
            }
        }

        if ((l.type == Login.LOGIN || l.type == Login.WIFI) && l.password.isNotEmpty()) item(key = "strength") {
            Spacer(Modifier.height(20.dp))
            val lvl = strengthLevel(PasswordTools.strength(l.password))
            val col = strengthColor(lvl)
            Column(
                Modifier.padding(horizontal = 12.dp).fillMaxWidth().clip(RoundedCornerShape(30.dp)).background(C.card)
                    .padding(horizontal = 24.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("Siła hasła", style = T.row, color = C.text, modifier = Modifier.weight(1f))
                    Text("${STRENGTH_LABEL[lvl]} · ${l.password.length} znaków", style = T.bodyM.copy(fontSize = 14.sp), color = col)
                }
                StrengthBar(lvl, col)
                if (flag != null) Text(flag.note, style = T.sub.copy(fontSize = 14.sp, lineHeight = 20.sp), color = C.text2)
            }
        }

        if (l.isLogin && otp == null) item(key = "add2fa") {
            Spacer(Modifier.height(20.dp))
            Group {
                Row(
                    Modifier.fillMaxWidth().rowPress { nav.scanLinkTo = l.id; nav.push(Route.Scan) }.padding(horizontal = 24.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Dodaj kod 2FA", style = T.body, color = C.accent)
                        Text("Zeskanuj kod QR z ustawień bezpieczeństwa serwisu", style = T.sub.copy(fontSize = 14.sp), color = C.text2, modifier = Modifier.padding(top = 3.dp))
                    }
                    Icon(Ic.ScanTab, null, tint = C.accent, modifier = Modifier.size(22.dp))
                }
            }
        }

        item(key = "buttons") {
            Spacer(Modifier.height(20.dp))
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (l.type == Login.LOGIN || l.type == Login.WIFI) {
                    PillButton("Generuj nowe", {
                        val pw = PasswordTools.generate(GenOptions(length = 18))
                        ctx.app.vault.saveLogin(l.copy(password = pw))
                        reveal = true
                        Clip.copy(ctx, "Hasło · ${l.title}", pw)
                        toaster.show("Wygenerowano i skopiowano nowe hasło")
                    }, icon = Ic.Regen, modifier = Modifier.weight(1f))
                } else {
                    PillButton("Edytuj", { nav.push(Route.EditLogin(l.id)) }, icon = Ic.Edit, modifier = Modifier.weight(1f))
                }
                PillButton("Udostępnij", { share(ctx, l) }, primary = false, modifier = Modifier.weight(1f))
            }
            // Z telefonu na komputer: Firefox z rozszerzeniem Ostoja otwiera stronę i loguje się sam.
            val computers by pl.wolter.ostoja.sync.Sync.computers.collectAsState()
            if (l.isLogin && computers.isNotEmpty()) {
                val scope = androidx.compose.runtime.rememberCoroutineScope()
                Spacer(Modifier.height(10.dp))
                PillButton("Zaloguj na komputerze", {
                    scope.launch {
                        toaster.show(when (pl.wolter.ostoja.sync.Sync.openOnComputer(l.id)) {
                            "ok" -> "Loguję na ${computers.first().name} w Firefoksie…"
                            "locked" -> "Komputer jest zablokowany - potwierdź logowanie na nim"
                            "noext" -> "Na komputerze nie działa rozszerzenie Ostoja w Firefoksie"
                            "missing" -> "Ten wpis nie dotarł jeszcze na komputer"
                            else -> "Komputer jest poza zasięgiem"
                        })
                    }
                }, primary = false, icon = Ic.Laptop, modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp))
            }
        }

        if (l.history.isNotEmpty()) {
            item(key = "histHead") {
                Spacer(Modifier.height(24.dp))
                SectionLabel("Poprzednie hasła (${l.history.size})", action = {
                    Text(
                        if (showHistory) "Ukryj" else "Pokaż", style = T.chip, color = C.accent,
                        modifier = Modifier.clip(RoundedCornerShape(50)).rowPress { showHistory = !showHistory }.padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                })
            }
            item(key = "hist") {
                AnimatedVisibility(showHistory) {
                    Group {
                        l.history.forEachIndexed { i, h ->
                            if (i > 0) Divider()
                            Row(
                                Modifier.fillMaxWidth().rowPress { copy(h.password, "Stare hasło", pwMsg) }.padding(horizontal = 24.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(h.password, style = T.mono, color = C.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text("Używane do ${dateFmt.format(Date(h.changed))}", style = T.small, color = C.text2)
                                }
                                Icon(Ic.Copy, null, tint = C.text2, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        }

        item(key = "meta") {
            Text(
                "Ostatnia zmiana: ${dayFmt.format(Date(l.updated))}",
                style = T.sub.copy(fontSize = 13.sp), color = C.text2, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
            )
        }
    }

    if (confirmDelete) OneDialog(
        title = "Usunąć „${l.title}”?",
        text = "Wpis zniknie z sejfu na tym telefonie. Tej operacji nie można cofnąć." + if (otp != null) " Kod 2FA zostanie w zakładce Kody." else "",
        confirm = "Usuń", danger = true,
        onConfirm = { confirmDelete = false; ctx.app.vault.deleteLogin(l.id); toaster.show("Usunięto ${l.title}") },
        onDismiss = { confirmDelete = false },
    )

    if (wifiQr) {
        val esc = { s: String -> s.replace("\\", "\\\\").replace(";", "\\;").replace(",", "\\,").replace(":", "\\:").replace("\"", "\\\"") }
        val bmp = remember(l) { qrBitmap("WIFI:T:WPA;S:${esc(l.username)};P:${esc(l.password)};;", 720) }
        OneDialog(title = l.username.ifBlank { l.title }, confirm = "Gotowe", onConfirm = { wifiQr = false }, onDismiss = { wifiQr = false }, dismiss = null) {
            Text("Zeskanuj aparatem innego telefonu, aby połączyć się z siecią.", style = T.sub, color = C.text2)
            Spacer(Modifier.height(16.dp))
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Color.White).padding(14.dp), contentAlignment = Alignment.Center) {
                Image(bmp, "Kod QR", modifier = Modifier.size(220.dp))
            }
        }
    }
}

private fun share(ctx: android.content.Context, l: Login) {
    val text = when (l.type) {
        Login.NOTE -> "${l.title}\n${l.note}"
        Login.WIFI -> "Wi‑Fi: ${l.username}\nHasło: ${l.password}"
        Login.CARD -> "${l.title}\n${l.username}\n${l.password}"
        else -> listOfNotNull(l.title, l.url.takeIf { it.isNotBlank() }, "Login: ${l.username}", "Hasło: ${l.password}").joinToString("\n")
    }
    ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), "Udostępnij ${l.title}"))
}

@Composable
private fun MenuItem(icon: ImageVector, text: String, danger: Boolean = false, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(text, style = T.body, color = if (danger) C.danger else C.text) },
        leadingIcon = { Icon(icon, null, tint = if (danger) C.danger else C.text2, modifier = Modifier.size(20.dp)) },
        onClick = onClick,
    )
}

@Composable
private fun FieldRow(
    label: String,
    value: String,
    maxLines: Int = 2,
    valueColor: Color = C.text,
    onClick: (() -> Unit)?,
    trailing: @Composable () -> Unit = {},
) {
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.rowPress(onClick = onClick) else Modifier)
            .padding(start = 24.dp, end = 12.dp, top = 14.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = T.sub, color = C.text2)
            Text(value, style = T.body, color = valueColor, maxLines = maxLines, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 3.dp))
        }
        Spacer(Modifier.width(4.dp))
        trailing()
    }
}
