package pl.wolter.ostoja.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import pl.wolter.ostoja.ui.Ic
import pl.wolter.ostoja.ui.theme.C
import pl.wolter.ostoja.ui.theme.Motion
import pl.wolter.ostoja.ui.theme.T

/** Stan kapsuły współdzielony między ekranami (chowanie przy przewijaniu). */
@Stable
class NavChrome {
    var hidden by mutableStateOf(false)
    var hideOnScroll = true
}

val LocalNavChrome = staticCompositionLocalOf { NavChrome() }

/**
 * Ekran z prototypu: pasek u góry (status + 56 dp) zawsze na tle strony. Duży tytuł (34 sp) przewija się
 * z treścią i znika (opacity 1 − y/110, przesunięcie y·0,25 - paralaksa), a gdy zniknie, w pasku
 * pojawia się wyśrodkowany mały tytuł (opacity (y−90)/40, wjazd z 6 px).
 */
@Composable
fun OneUiPage(
    title: String,
    subtitle: String? = null,
    state: LazyListState = rememberLazyListState(),
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    headerTop: (@Composable () -> Unit)? = null,
    headerBottom: (@Composable ColumnScope.() -> Unit)? = null,
    bottomPadding: Dp = 150.dp,
    content: LazyListScope.() -> Unit,
) {
    val density = LocalDensity.current
    val chrome = LocalNavChrome.current
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val barH = 56.dp
    val px = { d: Dp -> with(density) { d.toPx() } }

    val y by remember(state) {
        derivedStateOf { if (state.firstVisibleItemIndex > 0) 100_000f else state.firstVisibleItemScrollOffset.toFloat() }
    }

    // Chowanie kapsuły jak w prototypie: dy > 3 i y > 60 → chowaj; dy < −3 → pokaż; y < 20 → zawsze pokaż.
    val conn = remember(state, chrome) {
        object : NestedScrollConnection {
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                val dy = -consumed.y
                val yy = if (state.firstVisibleItemIndex > 0) 100_000f else state.firstVisibleItemScrollOffset.toFloat()
                if (chrome.hideOnScroll) {
                    if (dy > 3f && yy > px(60.dp)) chrome.hidden = true
                    else if (dy < -3f) chrome.hidden = false
                }
                if (yy < px(20.dp)) chrome.hidden = false
                return Offset.Zero
            }
        }
    }

    Box(Modifier.fillMaxSize().background(C.bg)) {
        LazyColumn(
            state = state,
            modifier = Modifier.fillMaxSize().nestedScroll(conn),
            contentPadding = PaddingValues(top = statusTop + barH, bottom = navBottom + bottomPadding),
        ) {
            item(key = "__header", contentType = "header") {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 28.dp, end = 24.dp, top = 92.dp)
                        .graphicsLayer {
                            alpha = (1f - y / px(110.dp)).coerceIn(0f, 1f)
                            translationY = minOf(y, px(120.dp)) * .25f
                        },
                ) {
                    headerTop?.invoke()
                    Text(title, style = T.big, color = C.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (subtitle != null) Text(subtitle, style = T.subtitle, color = C.text2, modifier = Modifier.padding(top = 6.dp))
                    headerBottom?.invoke(this)
                }
                Spacer(Modifier.height(28.dp))
            }
            content()
        }

        // Pasek: status bar + 56 dp, zawsze nieprzezroczysty.
        Box(Modifier.fillMaxWidth().background(C.bg).padding(top = statusTop).height(barH)) {
            Text(
                title,
                style = T.bar,
                color = C.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 100.dp)
                    .graphicsLayer {
                        val a = ((y - px(90.dp)) / px(40.dp)).coerceIn(0f, 1f)
                        alpha = a
                        translationY = (1f - a) * px(6.dp)
                    },
            )
            if (onBack != null) IconBtn(Ic.Back, onBack, Modifier.align(Alignment.CenterStart).padding(start = 10.dp), desc = "Wstecz")
            Row(Modifier.align(Alignment.CenterEnd).padding(end = 10.dp), verticalAlignment = Alignment.CenterVertically, content = actions)
        }
    }
}

data class TabItem(val label: String, val icon: ImageVector)

/**
 * Kapsuła z prototypu: 294×64, padding 6, cztery zakładki. Wybrana ma 120 dp szerokości i podpis,
 * pozostałe 52 dp. Pod spodem akcentowa pigułka 120×52 przesuwana na left = 6 + i·54 po krzywej
 * cubic-bezier(.34,1.3,.64,1) w 300 ms - z lekkim przestrzeleniem.
 * Przy przewijaniu kapsuła nie znika, tylko opada o 12 dp i zmniejsza się do 86% (od dołu, środek).
 * Przy skanerze zjeżdża o 110 dp i gaśnie.
 */
@Composable
fun Capsule(
    tabs: List<TabItem>,
    selected: Int,
    onSelect: (Int) -> Unit,
    hidden: Boolean,
    gone: Boolean,
    modifier: Modifier = Modifier,
) {
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val hideEase = tween<Float>(300, easing = Motion.Hide)
    val ty by animateFloatAsState(if (gone) 110f else if (hidden) 12f else 0f, hideEase, label = "capY")
    val scale by animateFloatAsState(if (!gone && hidden) .86f else 1f, hideEase, label = "capS")
    val alpha by animateFloatAsState(if (gone) 0f else 1f, tween(220), label = "capA")
    val ind by animateDpAsState(6.dp + 54.dp * selected, tween(300, easing = Motion.Springy), label = "ind")

    Box(modifier.fillMaxWidth().padding(bottom = navBottom + 26.dp), contentAlignment = Alignment.BottomCenter) {
        Box(
            Modifier
                .graphicsLayer {
                    translationY = ty.dp.toPx()
                    scaleX = scale; scaleY = scale
                    this.alpha = alpha
                    transformOrigin = TransformOrigin(.5f, 1f)
                }
                .shadow(14.dp, RoundedCornerShape(32.dp), ambientColor = Color.Black.copy(.25f), spotColor = Color.Black.copy(.3f))
                .size(294.dp, 64.dp)
                .clip(RoundedCornerShape(32.dp))
                .background(C.capsule)
                .border(0.5.dp, C.capsuleBorder, RoundedCornerShape(32.dp))
        ) {
            Box(
                Modifier
                    .offset { IntOffset(ind.roundToPx(), 6.dp.roundToPx()) }
                    .size(120.dp, 52.dp)
                    .clip(RoundedCornerShape(26.dp))
                    .background(C.accent)
            )
            Row(Modifier.padding(6.dp)) {
                tabs.forEachIndexed { i, t ->
                    if (i > 0) Spacer(Modifier.width(2.dp))
                    val sel = i == selected
                    val w by animateDpAsState(if (sel) 120.dp else 52.dp, tween(300, easing = Motion.Springy), label = "w$i")
                    val labelW by animateDpAsState(if (sel) 84.dp else 0.dp, tween(300, easing = Motion.Springy), label = "lw$i")
                    val gap by animateDpAsState(if (sel) 8.dp else 0.dp, tween(300), label = "g$i")
                    val la by animateFloatAsState(if (sel) 1f else 0f, tween(200), label = "la$i")
                    val fg by animateColorAsState(if (sel) Color.White else C.text2, tween(200), label = "fg$i")
                    Row(
                        Modifier
                            .width(w)
                            .height(52.dp)
                            .clip(RoundedCornerShape(26.dp))
                            .rowPress { onSelect(i) },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
                    ) {
                        Icon(t.icon, t.label, tint = fg, modifier = Modifier.size(22.dp))
                        if (labelW > 0.5.dp) Box(Modifier.padding(start = gap).widthIn(max = labelW).clip(RoundedCornerShape(0.dp))) {
                            Text(t.label, style = T.chip, color = fg, maxLines = 1, softWrap = false, modifier = Modifier.graphicsLayer { this.alpha = la })
                        }
                    }
                }
            }
        }
    }
}
