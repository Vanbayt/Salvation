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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import org.akanework.gramophone.R
import kotlin.math.cos
import kotlin.math.sin

/**
 * Material 3 Expressive Cookie Play Button.
 * Features organic morphing shape waves, dynamic sweep gradient arcs during loading,
 * and snappy spring transitions when state changes.
 */
@Composable
fun CookiePlayButton(
    isPlaying: Boolean,
    isLoading: Boolean = false,
    onClick: () -> Unit
) {
    val primaryContainer = MaterialTheme.colorScheme.primaryContainer
    val primaryColor = MaterialTheme.colorScheme.primary
    val tertiaryColor = MaterialTheme.colorScheme.tertiary
    val iconColor = MaterialTheme.colorScheme.onPrimaryContainer

    // 1. M3 Expressive Spring Transition for Teeth Morphing
    val transition = updateTransition(targetState = Pair(isPlaying, isLoading), label = "m3_expressive_transition")

    val baseTeethDepth by transition.animateFloat(
        transitionSpec = { spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow) },
        label = "teeth_depth"
    ) { state ->
        when {
            state.second -> 14f // isLoading: deeper morphing teeth
            state.first -> 10f  // isPlaying: active wavy cookie
            else -> 0f          // idle: smooth circle
        }
    }

    // 2. Continuous rotation & wave phase for fluid motion
    var rotationPhase by remember { mutableFloatStateOf(0f) }
    var pulsePhase by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(isPlaying || isLoading) {
        if (isPlaying || isLoading) {
            while (true) {
                withFrameMillis { time ->
                    val speed = if (isLoading) 6f else 20f
                    rotationPhase = (time / speed) % 360f
                    pulsePhase = (time / 15f) % 360f
                }
            }
        }
    }

    // 3. Icon Spring Scale (Snappy Overshoot on Play/Pause start)
    val iconScale by animateFloatAsState(
        targetValue = if (isLoading) 0.85f else 1.0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "icon_scale"
    )

    Box(
        modifier = Modifier
            .size(88.dp)
            .clip(CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(),
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        // Отрисовка Expressive Cookie Shape Canvas
        Canvas(modifier = Modifier.matchParentSize()) {
            val center = Offset(size.width / 2, size.height / 2)
            val radius = size.width / 2 - 12f
            val path = Path()

            // Dynamic pulse variation during loading
            val dynamicTeethDepth = if (isLoading) {
                baseTeethDepth + 4f * sin(Math.toRadians(pulsePhase.toDouble())).toFloat()
            } else {
                baseTeethDepth
            }

            val teethCount = if (isLoading) 12 else 10

            for (i in 0..360) {
                val rad = Math.toRadians(i.toDouble())
                val r = radius + dynamicTeethDepth * sin(teethCount * rad + Math.toRadians(rotationPhase.toDouble()))
                val x = center.x + r.toFloat() * cos(rad).toFloat()
                val y = center.y + r.toFloat() * sin(rad).toFloat()

                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            path.close()

            // Fill base shape
            drawPath(path, color = primaryContainer)

            // Draw M3 Expressive liquid sweep gradient glow ring when loading
            if (isLoading) {
                val sweepBrush = Brush.sweepGradient(
                    colors = listOf(
                        primaryColor.copy(alpha = 0.05f),
                        primaryColor,
                        tertiaryColor,
                        primaryColor.copy(alpha = 0.05f)
                    ),
                    center = center
                )

                drawPath(
                    path = path,
                    brush = sweepBrush,
                    style = Stroke(width = 6f, cap = StrokeCap.Round)
                )

                // Orbiting liquid particles inside the cookie
                val orbitRadius = radius * 0.45f
                val orbitAngle1 = Math.toRadians(rotationPhase.toDouble() * 2.0)
                val orbitAngle2 = orbitAngle1 + Math.PI

                val dot1 = Offset(
                    center.x + orbitRadius * cos(orbitAngle1).toFloat(),
                    center.y + orbitRadius * sin(orbitAngle1).toFloat()
                )
                val dot2 = Offset(
                    center.x + orbitRadius * cos(orbitAngle2).toFloat(),
                    center.y + orbitRadius * sin(orbitAngle2).toFloat()
                )

                drawCircle(color = primaryColor, radius = 5f, center = dot1)
                drawCircle(color = tertiaryColor, radius = 4f, center = dot2)
            }
        }

        // Center Play / Pause Icon with Expressive Spring Scale
        Box(
            modifier = Modifier.scale(iconScale),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(id = if (isPlaying && !isLoading) R.drawable.ic_pause else R.drawable.ic_play),
                contentDescription = "Play/Pause",
                tint = iconColor,
                modifier = Modifier.size(36.dp)
            )
        }
    }
}