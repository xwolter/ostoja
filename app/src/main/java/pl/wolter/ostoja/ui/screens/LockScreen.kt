package pl.wolter.ostoja.ui.screens

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import pl.wolter.ostoja.R
import pl.wolter.ostoja.ui.Ic
import pl.wolter.ostoja.ui.components.PillButton
import pl.wolter.ostoja.ui.theme.C
import pl.wolter.ostoja.ui.theme.T

@Composable
fun LockScreen(onUnlock: () -> Unit) {
    LaunchedEffect(Unit) {
        delay(250)
        onUnlock()
    }
    val pulse = rememberInfiniteTransition(label = "pulse")
    val glow by pulse.animateFloat(.0f, .18f, infiniteRepeatable(tween(1600), RepeatMode.Reverse), label = "glow")
    Box(Modifier.fillMaxSize().background(C.bg)) {
        Column(
            Modifier.align(Alignment.Center).padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Box(
                    Modifier.size(150.dp).graphicsLayer { scaleX = 1f + glow; scaleY = 1f + glow; alpha = .9f - glow * 3 }
                        .clip(CircleShape).background(C.accentSoft)
                )
                Image(painterResource(R.drawable.ic_logo), "Ostoja", Modifier.size(96.dp))
            }
            Spacer(Modifier.height(28.dp))
            Text("Ostoja", style = T.big, color = C.text)
            Spacer(Modifier.height(6.dp))
            Text("Sejf jest zablokowany", style = T.body, color = C.text2, textAlign = TextAlign.Center)
        }
        PillButton(
            "Odblokuj", onUnlock, icon = Ic.Fingerprint,
            modifier = Modifier.align(Alignment.BottomCenter).windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = 40.dp, vertical = 36.dp).fillMaxWidth(),
        )
    }
}
