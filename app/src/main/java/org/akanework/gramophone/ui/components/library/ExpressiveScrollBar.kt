package org.akanework.gramophone.ui.components.library

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun ExpressiveScrollBar(
    listState: LazyListState,
    itemCount: Int,
    labelProvider: ((Int) -> String)? = null,
    modifier: Modifier = Modifier,
    thumbMinHeight: Dp = 48.dp,
    thumbThickness: Dp = 6.dp
) {
    if (itemCount <= 0) return

    val coroutineScope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current

    var isDragging by remember { mutableStateOf(false) }
    var dragProgress by remember { mutableFloatStateOf(0f) }
    var currentLabel by remember { mutableStateOf("") }
    var lastHapticLabel by remember { mutableStateOf("") }

    // Авто-скрытие скроллбара в покое
    var isThumbVisible by remember { mutableStateOf(false) }

    LaunchedEffect(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset, isDragging) {
        isThumbVisible = true
        if (!isDragging) {
            delay(1800)
            isThumbVisible = false
        }
    }

    val animatedThumbAlpha by animateFloatAsState(
        targetValue = if (isThumbVisible || isDragging) 1f else 0f,
        animationSpec = tween(durationMillis = 250),
        label = "scrollThumbAlpha"
    )

    val animatedThumbThickness by animateDpAsState(
        targetValue = if (isDragging) 10.dp else thumbThickness,
        animationSpec = tween(durationMillis = 150),
        label = "thumbThickness"
    )

    val scrollProgress by remember(itemCount) {
        derivedStateOf {
            val totalItems = itemCount.coerceAtLeast(1)
            val firstIndex = listState.firstVisibleItemIndex
            val offset = listState.firstVisibleItemScrollOffset
            val firstItemSize = listState.layoutInfo.visibleItemsInfo.firstOrNull()?.size ?: 1

            val fractional = (firstIndex.toFloat() + (offset.toFloat() / firstItemSize.coerceAtLeast(1))) / totalItems
            fractional.coerceIn(0f, 1f)
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxHeight()
            .width(60.dp)
            .padding(end = 4.dp),
        contentAlignment = Alignment.CenterEnd
    ) {
        val totalTrackHeightPx = constraints.maxHeight.toFloat()
        val thumbHeightPx = with(density) { thumbMinHeight.toPx() }
        val maxScrollRange = (totalTrackHeightPx - thumbHeightPx).coerceAtLeast(1f)

        val activeProgress = if (isDragging) dragProgress else scrollProgress
        val thumbTopOffsetPx = activeProgress * maxScrollRange

        // Всплывающий индикатор буквы/метки при перетаскивании (Pill Bubble)
        AnimatedVisibility(
            visible = isDragging && currentLabel.isNotBlank(),
            enter = fadeIn() + scaleIn(),
            exit = fadeOut() + scaleOut(),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset {
                    IntOffset(
                        x = -with(density) { 36.dp.roundToPx() },
                        y = (thumbTopOffsetPx - with(density) { 8.dp.toPx() }).roundToInt()
                    )
                }
        ) {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.primary,
                shadowElevation = 8.dp,
                modifier = Modifier.size(52.dp, 44.dp)
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = currentLabel,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        ),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        }

        // Ползунок скроллбара (Thumb)
        if (animatedThumbAlpha > 0.01f) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset { IntOffset(0, thumbTopOffsetPx.roundToInt()) }
                    .height(thumbMinHeight)
                    .width(animatedThumbThickness)
                    .clip(CircleShape)
                    .background(
                        if (isDragging) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f * animatedThumbAlpha)
                    )
                    .pointerInput(itemCount, labelProvider) {
                        detectVerticalDragGestures(
                            onDragStart = { offset ->
                                isDragging = true
                                val startY = (offset.y + thumbTopOffsetPx).coerceIn(0f, totalTrackHeightPx)
                                dragProgress = (startY / totalTrackHeightPx).coerceIn(0f, 1f)

                                val targetIndex = ((dragProgress * (itemCount - 1)).roundToInt()).coerceIn(0, itemCount - 1)
                                if (labelProvider != null) {
                                    val label = labelProvider(targetIndex)
                                    currentLabel = label
                                    if (label != lastHapticLabel) {
                                        lastHapticLabel = label
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    }
                                }

                                coroutineScope.launch {
                                    listState.scrollToItem(targetIndex)
                                }
                            },
                            onDragEnd = {
                                isDragging = false
                            },
                            onDragCancel = {
                                isDragging = false
                            },
                            onVerticalDrag = { change, _ ->
                                val currentY = change.position.y + thumbTopOffsetPx
                                dragProgress = (currentY / totalTrackHeightPx).coerceIn(0f, 1f)

                                val targetIndex = ((dragProgress * (itemCount - 1)).roundToInt()).coerceIn(0, itemCount - 1)
                                if (labelProvider != null) {
                                    val label = labelProvider(targetIndex)
                                    currentLabel = label
                                    if (label != lastHapticLabel) {
                                        lastHapticLabel = label
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    }
                                }

                                coroutineScope.launch {
                                    listState.scrollToItem(targetIndex)
                                }
                            }
                        )
                    }
            )
        }
    }
}
