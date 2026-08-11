package org.akanework.gramophone.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ripple
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import org.akanework.gramophone.R
import kotlin.math.cos
import kotlin.math.sin

/**
 * Clean Material 3 Expressive Cookie Play Button.
 * Features soft 8-lobe organic cookie shape, elegant rounded progress arc loader,
 * and snappy M3 spring physics transitions.
 */
@Composable
fun CookiePlayButton(
    isPlaying: Boolean,
    isLoading: Boolean = false,
    onClick: () -> Unit
) {
    val primaryContainer = MaterialTheme.colorScheme.primaryContainer
    val iconColor = MaterialTheme.colorScheme.onPrimaryContainer
    val primaryColor = MaterialTheme.colorScheme.primary

    // 1. Teeth depth animation with M3 spring physics
    val teethDepth by animateFloatAsState(
        targetValue = if (isPlaying && !isLoading) 6f else if (isLoading) 8f else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "teethDepth"
    )

    // 2. Rotation phase for cookie shape and loading arc
    var rotationPhase by remember { mutableFloatStateOf(0f) }
    var arcStartAngle by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(isPlaying || isLoading) {
        if (isPlaying || isLoading) {
            while (true) {
                withFrameMillis { time ->
                    rotationPhase = (time / 25f) % 360f
                    arcStartAngle = (time / 4f) % 360f
                }
            }
        }
    }

    // 3. Spring pulse scale during loading / click
    val buttonScale by animateFloatAsState(
        targetValue = if (isLoading) 0.94f else 1.0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "buttonScale"
    )

    Box(
        modifier = Modifier
            .size(88.dp)
            .scale(buttonScale)
            .clip(CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(),
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        // Отрисовка чистой формы "Печеньки"
        Canvas(modifier = Modifier.matchParentSize()) {
            val center = Offset(size.width / 2, size.height / 2)
            val radius = size.width / 2 - 10f
            val path = Path()
            val teethCount = 8 // Мягкие 8 лепестков вместо острых зубьев

            for (i in 0..360) {
                val rad = Math.toRadians(i.toDouble())
                val r = radius + teethDepth * sin(teethCount * rad + Math.toRadians(rotationPhase.toDouble()))
                val x = center.x + r.toFloat() * cos(rad).toFloat()
                val y = center.y + r.toFloat() * sin(rad).toFloat()

                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            path.close()

            // Заливка чистым контейнером без проволочных рамок
            drawPath(path, color = primaryContainer)

            // Во время загрузки отрисовываем элегантный аккуратный индикатор M3 Expressive
            if (isLoading) {
                val arcSize = 44.dp.toPx()
                val arcOffset = Offset((size.width - arcSize) / 2, (size.height - arcSize) / 2)

                drawArc(
                    color = iconColor,
                    startAngle = arcStartAngle,
                    sweepAngle = 280f,
                    useCenter = false,
                    topLeft = arcOffset,
                    size = Size(arcSize, arcSize),
                    style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
                )
            }
        }

        // Иконка Воспроизведения / Паузы (отображается при остановке и воспроизведении)
        if (!isLoading) {
            Icon(
                painter = painterResource(id = if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play),
                contentDescription = "Play/Pause",
                tint = iconColor,
                modifier = Modifier.size(36.dp)
            )
        }
    }
}