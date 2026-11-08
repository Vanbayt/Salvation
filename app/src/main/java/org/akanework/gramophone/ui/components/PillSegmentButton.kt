package org.akanework.gramophone.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class PillPosition {
    START,
    MIDDLE,
    END,
    STANDALONE
}

fun pillShape(
    position: PillPosition,
    cornerRadius: Dp = 24.dp,
    innerRadius: Dp = 6.dp
): RoundedCornerShape {
    return when (position) {
        PillPosition.STANDALONE -> RoundedCornerShape(cornerRadius)
        PillPosition.START -> RoundedCornerShape(
            topStart = cornerRadius,
            bottomStart = cornerRadius,
            topEnd = innerRadius,
            bottomEnd = innerRadius
        )
        PillPosition.MIDDLE -> RoundedCornerShape(innerRadius)
        PillPosition.END -> RoundedCornerShape(
            topStart = innerRadius,
            bottomStart = innerRadius,
            topEnd = cornerRadius,
            bottomEnd = cornerRadius
        )
    }
}

@Composable
fun PillSegmentButton(
    onClick: () -> Unit,
    position: PillPosition,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    width: Dp? = 48.dp,
    height: Dp = 44.dp,
    cornerRadius: Dp = 24.dp,
    innerRadius: Dp = 6.dp,
    content: @Composable () -> Unit
) {
    val shape = pillShape(position, cornerRadius, innerRadius)
    val sizeModifier = if (width != null) {
        modifier.size(width = width, height = height)
    } else {
        modifier.height(height)
    }
    Surface(
        onClick = onClick,
        shape = shape,
        color = containerColor,
        contentColor = contentColor,
        modifier = sizeModifier.clip(shape)
    ) {
        Box(contentAlignment = Alignment.Center) {
            content()
        }
    }
}
