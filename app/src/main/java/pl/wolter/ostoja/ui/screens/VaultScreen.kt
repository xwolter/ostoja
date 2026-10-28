package pl.wolter.ostoja.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import pl.wolter.ostoja.app
import pl.wolter.ostoja.data.AppSettings
import pl.wolter.ostoja.data.Demo
import pl.wolter.ostoja.data.Flag
import pl.wolter.ostoja.data.LeakCheck
import pl.wolter.ostoja.data.Login
import pl.wolter.ostoja.data.SecurityReport
import pl.wolter.ostoja.data.VaultData
import pl.wolter.ostoja.data.countLabel
import pl.wolter.ostoja.data.hostOf
import pl.wolter.ostoja.data.plural
import pl.wolter.ostoja.data.securityReport
import pl.wolter.ostoja.security.Clip
import pl.wolter.ostoja.ui.Ic
import pl.wolter.ostoja.ui.Nav
import pl.wolter.ostoja.ui.Route
import pl.wolter.ostoja.ui.components.Avatar
import pl.wolter.ostoja.ui.components.Chip
import pl.wolter.ostoja.ui.components.EmptyState
import pl.wolter.ostoja.ui.components.GroupItem
import pl.wolter.ostoja.ui.components.IconBtn
import pl.wolter.ostoja.ui.components.LocalSettings
import pl.wolter.ostoja.ui.components.LocalToaster
import pl.wolter.ostoja.ui.components.OneUiPage
import pl.wolter.ostoja.ui.components.PillButton
import pl.wolter.ostoja.ui.components.SearchField
import pl.wolter.ostoja.ui.components.press
import pl.wolter.ostoja.ui.components.rowPress
import pl.wolter.ostoja.ui.theme.C
import pl.wolter.ostoja.ui.theme.T

private val CATS = listOf("all" to "Wszystkie", Login.LOGIN to "Loginy", Login.CARD to "Karty", Login.NOTE to "Notatki", Login.WIFI to "Wi‑Fi")

/** Podpis wiersza zależnie od typu wpisu (jak w prototypie: „•••• 4821”, „Notatka zabezpieczona”). */
fun Login.subtitle(): String = when (type) {
    Login.CARD -> "•••• " + password.filter { it.isDigit() }.takeLast(4)
    Login.NOTE -> "Notatka zabezpieczona"
    Login.WIFI -> username.ifBlank { "Sieć Wi‑Fi" }
    else -> username.ifBlank { hostOf(url).ifBlank { "Bez loginu" } }
}

/** Co kopiuje dotknięcie wpisu innego niż login. */
fun Login.quickValue(): String = when (type) {
    Login.NOTE -> note
    else -> password
}

@Composable
fun VaultScreen(nav: Nav, vault: VaultData, settings: AppSettings) {
    val ctx = LocalContext.current
    val toaster = LocalToaster.current
    val scope = rememberCoroutineScope()
    var query by rememberSaveable { mutableStateOf("") }
    var cat by rememberSaveable { mutableStateOf("all") }
    var menu by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val focus = remember { FocusRequester() }
    val report = remember(vault) { vault.securityReport() }

    LaunchedEffect(nav.focusSearch) {
        if (nav.focusSearch > 0) {
            listState.animateScrollToItem(1)
            runCatching { focus.requestFocus() }
        }
    }

    val list = remember(vault, query, cat, settings.vaultSort, report) {
        val q = query.trim().lowercase()
        vault.logins
            .filter { l -> q.isEmpty() || l.title.lowercase().contains(q) || l.username.lowercase().contains(q) || hostOf(l.url).contains(q) }
            .filter { l -> when (cat) { "all" -> true; "issues" -> report.flag(l.id) != null; else -> l.type == cat } }
            .let { l -> if (settings.vaultSort == 1) l.sortedByDescending { it.updated } else l.sortedWith(compareByDescending<Login> { it.favorite }.thenBy { it.title.lowercase() }) }
    }

    fun copy(l: Login, value: String, what: String) {
        if (value.isEmpty()) return
        Clip.copy(ctx, "$what · ${l.title}", value)
        toaster.show(if (what == "Hasło") "Skopiowano hasło · wyczyszczę za ${settings.clipClearSec} s" else "Skopiowano · ${l.title}")
    }

    val n = vault.logins.size
    OneUiPage(
        title = "Ostoja",
        subtitle = if (n == 0) "Sejf jest pusty" else
            "${countLabel(n, "element", "elementy", "elementów")} · ${countLabel(vault.otps.size, "kod", "kody", "kodów")} 2FA",
        state = listState,
        actions = {
            IconBtn(Ic.Search, { scope.launch { listState.animateScrollToItem(1); runCatching { focus.requestFocus() } } }, desc = "Szukaj")
            Box {
                IconBtn(Ic.More, { menu = true }, desc = "Więcej")
                DropdownMenu(menu, { menu = false }, containerColor = C.card, shape = RoundedCornerShape(22.dp)) {
                    listOf("Alfabetycznie", "Ostatnio zmienione").forEachIndexed { i, s ->
                        DropdownMenuItem(
                            text = { Text(s, style = T.body, color = if (settings.vaultSort == i) C.accent else C.text) },
                            trailingIcon = { if (settings.vaultSort == i) Icon(Ic.Check, null, tint = C.accent, modifier = Modifier.size(18.dp)) },
                            onClick = { ctx.app.settings.update { it.copy(vaultSort = i) }; menu = false },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("Sprawdź wycieki teraz", style = T.body, color = C.text) },
                        onClick = {
                            menu = false
                            toaster.show("Sprawdzam hasła…")
                            scope.launch {
                                val fresh = LeakCheck.run(ctx.app.vault, maxAgeMs = 0)
                                toaster.show(if (fresh.isEmpty()) "Nie znaleziono nowych wycieków" else "Wycieki: " + fresh.joinToString { it.title })
                            }
                        },
                    )
                }
            }
        },
        headerBottom = {
            Row(Modifier.padding(top = 22.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HeaderChip("Nowy wpis", Ic.Plus, primary = true) { nav.push(Route.EditLogin(null)) }
                HeaderChip("Generator", Ic.Wand, primary = false) { nav.generator = true }
            }
        },
    ) {
        if (n == 0) {
            item(key = "empty") {
                EmptyState(Ic.Padlock, "Sejf jest pusty", "Dodaj pierwsze hasło albo zaimportuj je z Chrome lub Samsung Pass w Ustawieniach.") {
                    PillButton("Dodaj wpis", { nav.push(Route.EditLogin(null)) }, icon = Ic.Plus)
                    Text(
                        "Pokaż przykładowe dane", style = T.bodyM, color = C.accent,
                        modifier = Modifier.padding(top = 14.dp).clip(RoundedCornerShape(50)).rowPress { ctx.app.vault.merge(Demo.data()) }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                    )
                }
            }
            return@OneUiPage
        }

        item(key = "search") { SearchField(query, { query = it }, "Szukaj w sejfie", focus = focus) }
        item(key = "gap1") { Spacer(Modifier.height(20.dp)) }
        item(key = "health") { HealthCard(report) { cat = if (cat == "issues") "all" else "issues" } }
        item(key = "gap2") { Spacer(Modifier.height(20.dp)) }
        item(key = "cats") {
            LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val cats = if (cat == "issues") CATS + ("issues" to "Do poprawy") else CATS
                items(cats.size) { i -> Chip(cats[i].second, cat == cats[i].first, { cat = cats[i].first }) }
            }
        }
        item(key = "gap3") { Spacer(Modifier.height(20.dp)) }

        if (list.isEmpty()) item(key = "none") {
            Text(
                if (query.isNotBlank()) "Nic nie pasuje do „$query”" else "Brak wpisów w tej kategorii",
                style = T.subtitle, color = C.text2, modifier = Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 8.dp),
            )
        }
        itemsIndexed(list, key = { _, l -> l.id }) { i, l ->
            GroupItem(i, list.size) {
                VaultRow(
                    l, report.flag(l.id),
                    onTap = { if (l.isLogin) nav.push(Route.Item(l.id)) else copy(l, l.quickValue(), "Wartość") },
                    onLong = { if (l.isLogin) copy(l, l.password, "Hasło") else nav.push(Route.Item(l.id)) },
                )
            }
        }
    }
}

@Composable
fun HeaderChip(text: String, icon: ImageVector, primary: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .press(onClick = onClick)
            .height(40.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(if (primary) C.accent else C.card)
            .padding(start = 12.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = if (primary) Color.White else C.text2, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, style = T.chip, color = if (primary) Color.White else C.text2)
    }
}

@Composable
private fun VaultRow(l: Login, flag: Flag?, onTap: () -> Unit, onLong: () -> Unit) {
    val icons = LocalSettings.current.icons
    Row(
        Modifier.fillMaxWidth().rowPress(onLongClick = onLong, onClick = onTap).padding(start = 20.dp, end = 16.dp, top = 14.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icons || !l.isLogin) {
            Avatar(l.title, l.url, type = l.type)
            Spacer(Modifier.width(14.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(l.title, style = T.row, color = C.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(l.subtitle(), style = T.sub, color = C.text2, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 3.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            if (flag != null) FlagPill(flag)
            if (l.otpId != null) Text(
                "2FA", style = T.small.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold), color = C.accent,
                modifier = Modifier.height(24.dp).clip(RoundedCornerShape(12.dp)).background(C.accentSoft).padding(horizontal = 9.dp, vertical = 4.dp),
            )
        }
    }
}

@Composable
fun FlagPill(flag: Flag) {
    val leaked = flag == Flag.LEAKED
    val fg = if (leaked) C.danger else C.warnText
    Text(
        flag.label, style = T.small.copy(fontSize = 12.sp, fontWeight = FontWeight.Medium), color = fg,
        modifier = Modifier.height(24.dp).clip(RoundedCornerShape(12.dp)).background((if (leaked) C.danger else C.warn).copy(alpha = .14f))
            .padding(horizontal = 9.dp, vertical = 4.dp),
    )
}

/** „Kondycja sejfu” z prototypu: wynik 36 sp w akcencie, pasek 6 px i kropki ze statystykami. */
@Composable
private fun HealthCard(r: SecurityReport, onClick: () -> Unit) {
    val anim by animateFloatAsState(r.score / 100f, tween(700), label = "health")
    val issues = r.issues
    Column(
        Modifier
            .padding(horizontal = 12.dp)
            .press(scaleTo = .98f, onClick = onClick)
            .fillMaxWidth()
            .clip(RoundedCornerShape(30.dp))
            .background(C.card)
            .padding(horizontal = 22.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                Text("Kondycja sejfu", style = T.sub.copy(fontSize = 14.sp), color = C.text2)
                Text(
                    if (issues == 0) "${r.label} · wszystko w porządku" else "${r.label} · $issues ${plural(issues, "hasło", "hasła", "haseł")} do poprawy",
                    style = T.head.copy(fontSize = 16.sp), color = C.text, modifier = Modifier.padding(top = 3.dp),
                )
            }
            Text("${(anim * 100).toInt()}", style = T.num.copy(fontSize = 36.sp, fontWeight = FontWeight.SemiBold, lineHeight = 36.sp), color = C.accent)
        }
        Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(C.track)) {
            Box(Modifier.fillMaxWidth(anim).fillMaxHeight().clip(RoundedCornerShape(3.dp)).background(C.accent))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatDot("${r.weak.size} słabe", C.warn)
            StatDot("${r.reused.size} powtórzone", C.warn)
            StatDot("${r.breached.size} w wycieku", C.danger)
        }
    }
}

@Composable
private fun StatDot(text: String, dot: Color) {
    Row(
        Modifier.height(34.dp).clip(RoundedCornerShape(17.dp)).background(C.bg).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(dot))
        Spacer(Modifier.width(7.dp))
        Text(text, style = T.sub, color = C.text)
    }
}
