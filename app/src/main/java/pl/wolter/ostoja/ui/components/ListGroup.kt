package pl.wolter.ostoja.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import pl.wolter.ostoja.ui.theme.C

/**
 * Element listy leniwej, który razem z sąsiadami tworzy jedną kartę z prototypu (radius 30, padding 6 0):
 * pierwszy ma zaokrąglone górne rogi, ostatni dolne, pomiędzy nimi linia 1 px z wcięciem.
 */
@Composable
fun GroupItem(
    index: Int,
    count: Int,
    modifier: Modifier = Modifier,
    dividerStart: Dp = 78.dp,
    dividerEnd: Dp = 22.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val r = 30.dp
    val first = index == 0
    val last = index == count - 1
    val shape = RoundedCornerShape(
        topStart = if (first) r else 0.dp, topEnd = if (first) r else 0.dp,
        bottomStart = if (last) r else 0.dp, bottomEnd = if (last) r else 0.dp,
    )
    Column(modifier.padding(horizontal = 12.dp).fillMaxWidth().clip(shape).background(C.card)) {
        if (first) Spacer(Modifier.height(6.dp))
        content()
        if (!last) Divider(start = dividerStart, end = dividerEnd)
        if (last) Spacer(Modifier.height(6.dp))
    }
}
