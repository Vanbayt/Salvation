package org.akanework.gramophone.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun AnimatedListItem(
    index: Int,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val offsetY = remember { Animatable(60f) }
    val alpha = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        val cappedIndex = index.coerceAtMost(8)
        delay((cappedIndex * 40).toLong())
        launch { alpha.animateTo(1f, tween(250)) }
        launch { offsetY.animateTo(0f, tween(durationMillis = 350, easing = FastOutSlowInEasing)) }
    }

    Box(
        modifier = modifier.graphicsLayer {
            translationY = offsetY.value
            this.alpha = alpha.value
        }
    ) {
        content()
    }
}
