package org.akanework.gramophone.ui.components.library

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
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
    onScrollToPosition: ((Int) -> Unit)? = null,
    modifier: Modifier = Modifier,
    bottomPadding: Dp = 0.dp,
    thumbMinHeight: Dp = 44.dp,
    thumbThickness: Dp = 4.dp
) {
    if (itemCount <= 0) return

    val coroutineScope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current

    var isDragging by remember { mutableStateOf(false) }
    var dragOffsetPx by remember { mutableFloatStateOf(0f) }
    var currentLabel by remember { mutableStateOf("") }
    var lastHapticLabel by remember { mutableStateOf("") }

    // Авто-скрытие скроллбара в покое
    var isThumbVisible by remember { mutableStateOf(false) }

    LaunchedEffect(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset, isDragging) {
        isThumbVisible = true
        if (!isDragging) {
            delay(1600)
            isThumbVisible = false
        }
    }

    val animatedThumbAlpha by animateFloatAsState(
        targetValue = if (isThumbVisible || isDragging) 1f else 0f,
        animationSpec = tween(durationMillis = 200),
        label = "scrollThumbAlpha"
    )

    val animatedThumbThickness by animateDpAsState(
        targetValue = if (isDragging) 10.dp else thumbThickness,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "thumbThickness"
    )

    val animatedThumbHeight by animateDpAsState(
        targetValue = if (isDragging) 56.dp else thumbMinHeight,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "thumbHeight"
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
            .padding(bottom = bottomPadding)
            .width(52.dp),
        contentAlignment = Alignment.CenterEnd
    ) {
        val totalTrackHeightPx = constraints.maxHeight.toFloat()
        val thumbHeightPx = with(density) { animatedThumbHeight.toPx() }
        val maxScrollRange = (totalTrackHeightPx - thumbHeightPx).coerceAtLeast(1f)

        val targetOffsetPx = if (isDragging) dragOffsetPx else scrollProgress * maxScrollRange

        // Плавное физическое следование бегунка за жестом
        val animatedOffsetPx by animateFloatAsState(
            targetValue = targetOffsetPx,
            animationSpec = if (isDragging) {
                spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessHigh)
            } else {
                spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)
            },
            label = "thumbOffsetPx"
        )

        // Всплывающий индикатор буквы/метки при перетаскивании (Pill Bubble)
        AnimatedVisibility(
            visible = isDragging && currentLabel.isNotBlank(),
            enter = fadeIn(spring(stiffness = Spring.StiffnessHigh)) + scaleIn(spring(dampingRatio = Spring.DampingRatioMediumBouncy)),
            exit = fadeOut(tween(150)) + scaleOut(tween(150)),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset {
                    IntOffset(
                        x = -with(density) { 38.dp.roundToPx() },
                        y = (animatedOffsetPx - with(density) { 6.dp.toPx() }).roundToInt()
                    )
                }
        ) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.primary,
                shadowElevation = 8.dp,
                modifier = Modifier.size(56.dp, 46.dp)
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = currentLabel,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Black,
                            fontSize = 20.sp
                        ),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        }

        // Область перетаскивания и сам бегунок
        if (animatedThumbAlpha > 0.01f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(itemCount, maxScrollRange, labelProvider, onScrollToPosition) {
                        detectVerticalDragGestures(
                            onDragStart = { offset ->
                                isDragging = true
                                val startY = offset.y.coerceIn(0f, maxScrollRange)
                                dragOffsetPx = startY
                                val fraction = (startY / maxScrollRange).coerceIn(0f, 1f)

                                val targetIndex = ((fraction * (itemCount - 1)).roundToInt()).coerceIn(0, itemCount - 1)
                                if (labelProvider != null) {
                                    val label = labelProvider(targetIndex)
                                    currentLabel = label
                                    if (label != lastHapticLabel) {
                                        lastHapticLabel = label
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    }
                                }

                                if (onScrollToPosition != null) {
                                    onScrollToPosition(targetIndex)
                                } else {
                                    coroutineScope.launch {
                                        listState.scrollToItem(targetIndex)
                                    }
                                }
                            },
                            onDragEnd = {
                                isDragging = false
                            },
                            onDragCancel = {
                                isDragging = false
                            },
                            onVerticalDrag = { change, dragAmount ->
                                change.consume()
                                dragOffsetPx = (dragOffsetPx + dragAmount).coerceIn(0f, maxScrollRange)
                                val fraction = (dragOffsetPx / maxScrollRange).coerceIn(0f, 1f)

                                val targetIndex = ((fraction * (itemCount - 1)).roundToInt()).coerceIn(0, itemCount - 1)
                                if (labelProvider != null) {
                                    val label = labelProvider(targetIndex)
                                    currentLabel = label
                                    if (label != lastHapticLabel) {
                                        lastHapticLabel = label
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    }
                                }

                                if (onScrollToPosition != null) {
                                    onScrollToPosition(targetIndex)
                                } else {
                                    coroutineScope.launch {
                                        listState.scrollToItem(targetIndex)
                                    }
                                }
                            }
                        )
                    }
            ) {
                // Визуальный бегунок (Thumb)
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(end = 4.dp)
                        .offset { IntOffset(0, animatedOffsetPx.roundToInt()) }
                        .height(animatedThumbHeight)
                        .width(animatedThumbThickness)
                        .clip(CircleShape)
                        .background(
                            if (isDragging) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f * animatedThumbAlpha)
                        )
                )
            }
        }
    }
}
