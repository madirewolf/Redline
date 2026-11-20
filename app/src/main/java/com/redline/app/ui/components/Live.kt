package com.redline.app.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import kotlinx.coroutines.delay

/** Wall-clock time that ticks on each whole second, for live timers. */
@Composable
fun rememberNow(): Long {
    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            delay(1000 - System.currentTimeMillis() % 1000)
            value = System.currentTimeMillis()
        }
    }
    return now
}

/** A small breathing dot that marks something as happening right now. */
@Composable
fun LiveDot(color: Color = MaterialTheme.colorScheme.primary, modifier: Modifier = Modifier) {
    val pulse = rememberInfiniteTransition(label = "live")
    val alpha by pulse.animateFloat(
        initialValue = 1f,
        targetValue = 0.25f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "liveAlpha"
    )
    Box(
        modifier = modifier
            .size(8.dp)
            .graphicsLayer { this.alpha = alpha }
            .background(color, CircleShape)
    )
}
