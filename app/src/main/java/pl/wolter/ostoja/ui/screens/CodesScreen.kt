package pl.wolter.ostoja.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.border
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import pl.wolter.ostoja.app
import pl.wolter.ostoja.data.AppSettings
import pl.wolter.ostoja.data.Otp
import pl.wolter.ostoja.data.OtpAccount
import pl.wolter.ostoja.data.OtpUri
import pl.wolter.ostoja.data.VaultData
import pl.wolter.ostoja.data.countLabel
import pl.wolter.ostoja.notify.PinService
import pl.wolter.ostoja.security.Clip
import pl.wolter.ostoja.ui.Ic
import pl.wolter.ostoja.ui.Nav
import pl.wolter.ostoja.ui.Route
import pl.wolter.ostoja.ui.components.Avatar
import pl.wolter.ostoja.ui.components.CodeText
import pl.wolter.ostoja.ui.components.CountdownRing
import pl.wolter.ostoja.ui.components.GroupItem
import pl.wolter.ostoja.ui.components.IconBtn
import pl.wolter.ostoja.ui.components.LocalSettings
import pl.wolter.ostoja.ui.components.LocalToaster
import pl.wolter.ostoja.ui.components.OneDialog
import pl.wolter.ostoja.ui.components.OneUiPage
import pl.wolter.ostoja.ui.components.PillButton
import pl.wolter.ostoja.ui.components.SearchField
import pl.wolter.ostoja.ui.components.rowPress
import pl.wolter.ostoja.ui.theme.C
import pl.wolter.ostoja.ui.theme.T

@Composable
fun CodesScreen(nav: Nav, vault: VaultData, settings: AppSettings) {
    val ctx = LocalContext.current
    val toaster = LocalToaster.current
    val now = rememberNow()
    var query by rememberSaveable { mutableStateOf("") }
    var searching by rememberSaveable { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var actionsFor by remember { mutableStateOf<OtpAccount?>(null) }
    var qrFor by remember { mutableStateOf<OtpAccount?>(null) }
    var deleteFor by remember { mutableStateOf<OtpAccount?>(null) }
    val revealed = remember { mutableStateMapOf<String, Long>() }
    val listState = rememberLazyListState()
    val focus = remember { FocusRequester() }

    var pendingPin by remember { mutableStateOf<OtpAccount?>(null) }
    val notifPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        pendingPin?.let { a ->
            if (ok) { PinService.start(ctx, a.id); toaster.show("Przypięto kod · ${a.title}") }
            else toaster.show("Bez zgody na powiadomienia nie da się przypiąć kodu")
        }
        pendingPin = null
    }
    fun pin(a: OtpAccount) {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            pendingPin = a
            notifPerm.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            PinService.start(ctx, a.id)
            toaster.show("Przypięto kod · ${a.title}")
        }
    }

    LaunchedEffect(revealed.size) {
        while (revealed.isNotEmpty()) {
            delay(1000)
            val t = System.currentTimeMillis()
            revealed.entries.removeAll { t - it.value > 20_000 }
        }
    }
    LaunchedEffect(nav.highlightOtp) {
        val id = nav.highlightOtp ?: return@LaunchedEffect
        delay(2500)
        if (nav.highlightOtp == id) nav.highlightOtp = null
    }
    LaunchedEffect(searching) {
        if (searching) { delay(120); runCatching { focus.requestFocus() } }
    }

    val list = remember(vault.otps, query) {
        val q = query.trim().lowercase()
        vault.otps
            .filter { q.isEmpty() || it.issuer.lowercase().contains(q) || it.label.lowercase().contains(q) }
            .sortedWith(compareByDescending<OtpAccount> { it.favorite }.thenBy { it.title.lowercase() })
    }

    fun copy(a: OtpAccount) {
        Clip.copy(ctx, "Kod 2FA · ${a.title}", Otp.code(a))
        if (settings.hideCodes) revealed[a.id] = System.currentTimeMillis()
        toaster.show("Skopiowano kod · ${a.title}")
    }

    val empty = vault.otps.isEmpty()
    OneUiPage(
        title = "Kody 2FA",
        subtitle = if (empty) "Brak kont" else countLabel(vault.otps.size, "konto", "konta", "kont"),
        state = listState,
        actions = {
            IconBtn(Ic.Search, { searching = !searching; if (!searching) query = "" }, desc = "Szukaj")
            Box {
                IconBtn(Ic.More, { menu = true }, desc = "Więcej")
                DropdownMenu(menu, { menu = false }, containerColor = C.card, shape = RoundedCornerShape(22.dp)) {
                    DropdownMenuItem(
                        text = { Text("Wpisz klucz ręcznie", style = T.body, color = C.text) },
                        leadingIcon = { Icon(Ic.Keyboard, null, tint = C.text2) },
                        onClick = { menu = false; nav.push(Route.EditOtp(null)) },
                    )
                    DropdownMenuItem(
                        text = { Text("Przenieś z innej aplikacji", style = T.body, color = C.text) },
                        leadingIcon = { Icon(Ic.Download, null, tint = C.text2) },
                        onClick = { menu = false; nav.push(Route.ImportOtp) },
                    )
                    DropdownMenuItem(
                        text = { Text(if (settings.hideCodes) "Pokazuj kody" else "Ukrywaj kody", style = T.body, color = C.text) },
                        leadingIcon = { Icon(if (settings.hideCodes) Ic.Eye else Ic.EyeOff, null, tint = C.text2) },
                        onClick = { menu = false; ctx.app.settings.update { it.copy(hideCodes = !it.hideCodes) } },
                    )
                }
            }
        },
        headerBottom = if (empty) null else ({
            Row(Modifier.padding(top = 22.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HeaderChip("Dodaj konto", Ic.Plus, primary = true) { nav.tab = 2 }
                val secs by remember { derivedStateOf { ((Otp.remainingMs(30, now.value) + 999) / 1000).toInt() } }
                Row(
                    Modifier.height(40.dp).clip(RoundedCornerShape(20.dp)).background(C.card).padding(start = 10.dp, end = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CountdownRing({ Otp.remainingMs(30, now.value) / 30_000f }, secs <= 5, size = 18.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("Nowe kody za $secs s", style = T.num.copy(fontSize = 14.sp), color = C.text2)
                }
            }
        }),
    ) {
        if (empty) {
            item(key = "empty") { EmptyCodes(onScan = { nav.tab = 2 }, onManual = { nav.push(Route.EditOtp(null)) }, onImport = { nav.push(Route.ImportOtp) }) }
            return@OneUiPage
        }

        item(key = "search") {
            AnimatedVisibility(searching, enter = expandVertically(tween(260)) + fadeIn(), exit = shrinkVertically(tween(220)) + fadeOut()) {
                SearchField(query, { query = it }, "Szukaj kont", Modifier.padding(bottom = 16.dp), focus = focus)
            }
        }
        if (list.isEmpty()) item(key = "none") {
            Text("Nic nie pasuje do „$query”", style = T.subtitle, color = C.text2, modifier = Modifier.padding(horizontal = 28.dp, vertical = 8.dp))
        }
        itemsIndexed(list, key = { _, a -> a.id }) { i, a ->
            GroupItem(i, list.size, dividerStart = if (settings.icons) 78.dp else 20.dp) {
                CodeRow(
                    a, now, hidden = settings.hideCodes && a.id !in revealed, highlight = a.id == nav.highlightOtp,
                    onCopy = { copy(a) }, onMenu = { actionsFor = a }, onNext = { ctx.app.vault.nextHotp(a.id) },
                )
            }
        }
        item(key = "hint") {
            Text(
                "Dotknij wiersza, aby skopiować kod. Przytrzymaj, aby przypiąć go do powiadomień, pokazać kod QR lub edytować.",
                style = T.small, color = C.text3, modifier = Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 18.dp),
            )
        }
    }

    actionsFor?.let { a ->
        OneSheet({ actionsFor = null }) {
            Row(Modifier.padding(horizontal = 24.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Avatar(a.title)
                Column(Modifier.padding(start = 14.dp)) {
                    Text(a.title, style = T.row, color = C.text)
                    if (a.label.isNotBlank()) Text(a.label, style = T.sub, color = C.text2)
                }
            }
            SheetAction(Ic.Copy, "Kopiuj kod") { copy(a); actionsFor = null }
            SheetAction(Ic.Pin, "Przypnij do powiadomień i ekranu blokady") { pin(a); actionsFor = null }
            SheetAction(if (a.favorite) Ic.StarFilled else Ic.Star, if (a.favorite) "Usuń z ulubionych" else "Dodaj do ulubionych") {
                ctx.app.vault.toggleOtpFavorite(a.id); actionsFor = null
            }
            SheetAction(Ic.Qr, "Pokaż kod QR (przeniesienie)") { qrFor = a; actionsFor = null }
            SheetAction(Ic.Edit, "Edytuj") { nav.push(Route.EditOtp(a.id)); actionsFor = null }
            SheetAction(Ic.Trash, "Usuń", danger = true) { deleteFor = a; actionsFor = null }
        }
    }

    qrFor?.let { a ->
        val bmp = remember(a) { qrBitmap(OtpUri.build(a), 720) }
        OneDialog(title = a.title, confirm = "Gotowe", onConfirm = { qrFor = null }, onDismiss = { qrFor = null }, dismiss = null) {
            Text("Zeskanuj w innej aplikacji uwierzytelniającej. Nie pokazuj tego kodu nikomu - daje pełny dostęp do kodów 2FA.", style = T.sub, color = C.text2)
            Spacer(Modifier.height(16.dp))
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Color.White).padding(14.dp), contentAlignment = Alignment.Center) {
                Image(bmp, "Kod QR", modifier = Modifier.size(220.dp))
            }
        }
    }

    deleteFor?.let { a ->
        OneDialog(
            title = "Usunąć ${a.title}?",
            text = "Upewnij się, że wyłączyłeś 2FA w serwisie albo masz kody zapasowe - bez tego konta nie da się odzyskać.",
            confirm = "Usuń", danger = true,
            onConfirm = { ctx.app.vault.deleteOtp(a.id); deleteFor = null; toaster.show("Usunięto ${a.title}") },
            onDismiss = { deleteFor = null },
        )
    }
}

@Composable
private fun CodeRow(
    a: OtpAccount,
    now: State<Long>,
    hidden: Boolean,
    highlight: Boolean,
    onCopy: () -> Unit,
    onMenu: () -> Unit,
    onNext: () -> Unit,
) {
    val icons = LocalSettings.current.icons
    val counter by remember(a) { derivedStateOf { Otp.counterAt(a, now.value) } }
    val warn by remember(a) { derivedStateOf { !a.isHotp && Otp.remainingMs(a.period, now.value) <= 5000 } }
    val code = remember(a, counter) { Otp.code(a, if (a.isHotp) System.currentTimeMillis() else counter * a.period * 1000L) }
    val hl by animateColorAsState(if (highlight) C.accentSoft else Color.Transparent, tween(400), label = "hl")

    Row(
        Modifier
            .fillMaxWidth()
            .background(hl)
            .rowPress(onLongClick = onMenu, onClick = onCopy)
            .padding(start = 20.dp, end = 16.dp, top = 16.dp, bottom = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icons) {
            Avatar(a.title)
            Spacer(Modifier.width(14.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(a.title, style = T.row, color = C.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (a.label.isNotBlank()) Text(a.label, style = T.sub, color = C.text2, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 3.dp))
        }
        Spacer(Modifier.width(10.dp))
        CodeText(code, C.accent, hidden = hidden)
        Spacer(Modifier.width(14.dp))
        if (a.isHotp) IconBtn(Ic.Refresh, onNext, tint = C.accent, size = 32.dp, iconSize = 20.dp, desc = "Następny kod")
        else CountdownRing({ Otp.remainingMs(a.period, now.value) / (a.period * 1000f) }, warn)
    }
}

/** Pusty stan z prototypu: koło 208 w odcieniu akcentu, karta z kółkiem i paskami, plusik w rogu. */
@Composable
private fun EmptyCodes(onScan: () -> Unit, onManual: () -> Unit, onImport: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(start = 36.dp, end = 36.dp, top = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(208.dp).clip(CircleShape).background(C.accentSoft), contentAlignment = Alignment.Center) {
            Column(
                Modifier.shadow(12.dp, RoundedCornerShape(24.dp), ambientColor = Color.Black.copy(.15f), spotColor = Color.Black.copy(.3f))
                    .size(100.dp, 128.dp).clip(RoundedCornerShape(24.dp)).background(C.card),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically),
            ) {
                Box(Modifier.size(40.dp).clip(CircleShape).border(3.dp, C.accent, CircleShape))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(Modifier.size(24.dp, 8.dp).clip(RoundedCornerShape(4.dp)).background(C.accent))
                    Box(Modifier.size(24.dp, 8.dp).clip(RoundedCornerShape(4.dp)).background(C.accent.copy(alpha = .45f)))
                }
            }
            Box(
                Modifier.align(Alignment.TopEnd).offset(x = (-22).dp, y = 30.dp).size(48.dp).clip(CircleShape).background(C.accent),
                contentAlignment = Alignment.Center,
            ) { Text("+", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Light) }
        }
        Spacer(Modifier.height(36.dp))
        Text("Brak kont", style = T.title, color = C.text)
        Spacer(Modifier.height(10.dp))
        Text(
            "Zeskanuj kod QR z ustawień zabezpieczeń serwisu, aby dodać pierwsze konto.",
            style = T.subtitle.copy(lineHeight = 22.sp), color = C.text2, textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(32.dp))
        PillButton("Zeskanuj kod QR", onScan, icon = Ic.ScanTab)
        Spacer(Modifier.height(10.dp))
        PillButton("Przenieś z Google / Microsoft", onImport, primary = false, icon = Ic.Download)
        Text(
            "Wpisz klucz ręcznie", style = T.bodyM, color = C.accent,
            modifier = Modifier.padding(top = 14.dp).height(44.dp).clip(RoundedCornerShape(50)).rowPress(onClick = onManual).padding(horizontal = 16.dp, vertical = 12.dp),
        )
    }
}

fun qrBitmap(text: String, size: Int): ImageBitmap {
    val m = com.google.zxing.qrcode.QRCodeWriter().encode(
        text, com.google.zxing.BarcodeFormat.QR_CODE, size, size,
        mapOf(com.google.zxing.EncodeHintType.MARGIN to 1),
    )
    val px = IntArray(size * size) { i -> if (m.get(i % size, i / size)) 0xFF111111.toInt() else 0xFFFFFFFF.toInt() }
    return android.graphics.Bitmap.createBitmap(px, size, size, android.graphics.Bitmap.Config.ARGB_8888).asImageBitmap()
}
