package org.akanework.gramophone.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import kotlin.math.sin

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SquigglySlider(
    position: Float,
    duration: Float,
    isPlaying: Boolean,
    activeColor: Color? = null,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit
) {
    val haptic = LocalHapticFeedback.current

    // Отслеживаем, держит ли пользователь палец на ползунке прямо сейчас
    val interactionSource = remember { MutableInteractionSource() }
    val isDragged by interactionSource.collectIsDraggedAsState()

    // Бесконечная анимация для бегущей волны
    val shouldAnimateWave = isPlaying || isDragged
    val infiniteTransition = rememberInfiniteTransition(label = "wave_shift")
    val rawPhaseShift by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase_shift"
    )
    val phaseShift = if (shouldAnimateWave) rawPhaseShift else 0f

    // 🔥 ПРУЖИННАЯ АНИМАЦИЯ ИЗ MATERIAL 3 EXPRESSIVE
    val targetAmplitude = when {
        isDragged -> 18f
        isPlaying -> 10f
        else -> 0f
    }

    val amplitude by animateFloatAsState(
        targetValue = targetAmplitude,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "amplitude"
    )

    // Выносим цвета НАРУЖУ из Canvas
    val primaryColor = activeColor ?: MaterialTheme.colorScheme.primary
    val inactiveColor = primaryColor.copy(alpha = 0.24f)

    Slider(
        value = position,
        onValueChange = onValueChange,
        onValueChangeFinished = {
            // Вызываем легкую вибрацию при подтверждении перемотки
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            onValueChangeFinished()
        },
        valueRange = 0f..(if (duration > 0f) duration else 1f),
        interactionSource = interactionSource,

        // 🔥 КРАСИВЫЙ КРУГЛЫЙ ПОЛЗУНОК
        thumb = {
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .background(
                        color = primaryColor,
                        shape = CircleShape
                    )
            )
        },
        colors = SliderDefaults.colors(
            thumbColor = primaryColor,
            activeTrackColor = Color.Transparent,
            inactiveTrackColor = Color.Transparent
        ),
        track = { sliderState ->
            val fraction = (sliderState.value - sliderState.valueRange.start) /
                    (sliderState.valueRange.endInclusive - sliderState.valueRange.start)

            Canvas(modifier = Modifier.fillMaxWidth().height(48.dp)) {
                val width = size.width
                val centerY = size.height / 2f
                val activeWidth = width * fraction

                val path = Path()
                path.moveTo(0f, centerY)

                val frequency = 0.06f
                var x = 0f

                // Рисуем волну, сглаживая её к ползунку
                while (x <= activeWidth) {
                    val distanceToThumb = activeWidth - x
                    val taper = (distanceToThumb / 80f).coerceIn(0f, 1f)
                    val y = centerY + sin(x * frequency - phaseShift) * amplitude * taper
                    path.lineTo(x, y)
                    x += 2f
                }
                path.lineTo(activeWidth, centerY)

                // Рисуем активную часть (Волна)
                drawPath(
                    path = path,
                    color = primaryColor,
                    style = Stroke(width = 10f, cap = StrokeCap.Round)
                )

                // Рисуем неактивную часть (Прямая линия до конца)
                drawLine(
                    color = inactiveColor,
                    start = Offset(activeWidth, centerY),
                    end = Offset(width, centerY),
                    strokeWidth = 10f,
                    cap = StrokeCap.Round
                )
            }
        }
    )
}