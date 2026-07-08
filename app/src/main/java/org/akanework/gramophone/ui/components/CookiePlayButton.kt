package org.akanework.gramophone.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ripple // <-- НОВЫЙ ИМПОРТ РИППЛА
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import org.akanework.gramophone.R

@Composable
fun CookiePlayButton(isPlaying: Boolean, onClick: () -> Unit) {
    val containerColor = MaterialTheme.colorScheme.primaryContainer
    val iconColor = MaterialTheme.colorScheme.onPrimaryContainer

    val transition = updateTransition(isPlaying, label = "playing_transition")

    // 1. Анимация появления зубчиков
    val teethDepth by transition.animateFloat(
        transitionSpec = { spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow) },
        label = "teeth"
    ) { playing -> if (playing) 10f else 0f }

    // 2. Бесконечное вращение (изменяем фазу, а не саму Canvas!)
    var rotationPhase by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            while (true) {
                withFrameMillis { time ->
                    // Скорость вращения. Полный оборот примерно за 7 секунд
                    rotationPhase = (time / 20f) % 360f
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .size(88.dp)
            .clip(CircleShape) // Чтобы Ripple эффект не выходил за рамки
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(), // <-- ИСПРАВЛЕНО: Современный вызов эффекта нажатия
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        // Отрисовка "Печеньки"
        Canvas(modifier = Modifier.matchParentSize()) {
            val radius = size.width / 2 - 10f // Отступ для зубьев
            val path = Path()
            val center = Offset(size.width / 2, size.height / 2)
            val teethCount = 10

            for (i in 0..360) {
                val rad = Math.toRadians(i.toDouble())
                // Формула формы: базовый радиус + глубина * синус(кол-во зубьев * угол + фаза вращения)
                val r = radius + teethDepth * kotlin.math.sin(teethCount * rad + Math.toRadians(rotationPhase.toDouble()))
                val x = center.x + r.toFloat() * kotlin.math.cos(rad).toFloat()
                val y = center.y + r.toFloat() * kotlin.math.sin(rad).toFloat()

                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            path.close()
            drawPath(path, color = containerColor)
        }

        // Иконка (всегда стоит ровно!)
        Icon(
            painter = painterResource(id = if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play),
            contentDescription = "Play/Pause",
            tint = iconColor,
            modifier = Modifier.size(36.dp)
        )
    }
}