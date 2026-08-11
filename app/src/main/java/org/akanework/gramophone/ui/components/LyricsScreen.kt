package org.akanework.gramophone.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.preference.PreferenceManager
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch
import org.akanework.gramophone.R
import org.akanework.gramophone.logic.utils.LyricsResult
import org.akanework.gramophone.logic.utils.LyricsSource
import org.akanework.gramophone.logic.utils.SemanticLyrics

sealed class LyricsDisplayItem {
    data class NormalLine(val line: SemanticLyrics.LyricLine) : LyricsDisplayItem()
    data class InstrumentalInterlude(
        val startMs: Long,
        val endMs: Long
    ) : LyricsDisplayItem()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LyricsScreen(
    trackTitle: String,
    artistName: String,
    coverUrl: String = "",
    isPlaying: Boolean = false,
    lyricsResult: LyricsResult?,
    isLoading: Boolean,
    currentPositionMs: Long,
    selectedSource: LyricsSource = LyricsSource.ALL,
    onSourceSelected: (LyricsSource) -> Unit = {},
    onPlayPauseToggle: () -> Unit = {},
    onSkipNext: () -> Unit = {},
    onSeekTo: (Long) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current

    val defaultPrefs = remember(context) { PreferenceManager.getDefaultSharedPreferences(context) }
    val isAutoTranslateEnabled = remember { defaultPrefs.getBoolean("lyrics_auto_translate", true) }

    // Handle system back gesture
    BackHandler {
        onDismiss()
    }

    // Physics-based swipe down to dismiss
    val dragOffsetY = remember { Animatable(0f) }
    val dismissThresholdPx = with(LocalDensity.current) { 160.dp.toPx() }

    // Ambient background pulsing glow
    val ambientTransition = rememberInfiniteTransition(label = "ambientAura")
    val ambientPulse by ambientTransition.animateFloat(
        initialValue = 0.8f, targetValue = 1.2f,
        animationSpec = infiniteRepeatable(tween(4000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "ambientPulse"
    )

    // Synced lyrics lines
    val syncedLyrics = lyricsResult?.lyrics as? SemanticLyrics.SyncedLyrics
    val validSyncedLines = remember(syncedLyrics) {
        syncedLyrics?.text?.filter { it.text.trim() != "null" && it.text.isNotBlank() } ?: emptyList()
    }

    // Process synced lines to insert instrumental interludes for vocal gaps >= 4 seconds
    val displayItems = remember(validSyncedLines) {
        val list = mutableListOf<LyricsDisplayItem>()
        for (i in validSyncedLines.indices) {
            val currentLine = validSyncedLines[i]
            list.add(LyricsDisplayItem.NormalLine(currentLine))

            if (i < validSyncedLines.lastIndex) {
                val nextLine = validSyncedLines[i + 1]
                val gapStart = currentLine.end.toLong().coerceAtLeast(currentLine.start.toLong() + 2000L)
                val gapEnd = nextLine.start.toLong()

                if (gapEnd - gapStart >= 4000L) {
                    list.add(LyricsDisplayItem.InstrumentalInterlude(gapStart, gapEnd))
                }
            }
        }
        list
    }

    val activeItemIndex = remember(displayItems, currentPositionMs) {
        if (displayItems.isEmpty()) -1
        else {
            val idx = displayItems.indexOfLast { item ->
                when (item) {
                    is LyricsDisplayItem.NormalLine -> currentPositionMs >= item.line.start.toLong()
                    is LyricsDisplayItem.InstrumentalInterlude -> currentPositionMs >= item.startMs
                }
            }
            if (idx >= 0) idx else 0
        }
    }

    // Smooth auto-scroll with Spring physics
    LaunchedEffect(activeItemIndex) {
        if (activeItemIndex >= 0 && activeItemIndex < displayItems.size) {
            listState.animateScrollToItem(
                index = (activeItemIndex - 2).coerceAtLeast(0)
            )
        }
    }

    val primaryColor = MaterialTheme.colorScheme.primary

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .drawBehind {
                // Expressive Ambient Backdrop Glow
                val centerOffset = Offset(size.width * 0.5f, size.height * 0.35f)
                val brush = Brush.radialGradient(
                    colors = listOf(
                        primaryColor.copy(alpha = 0.12f * ambientPulse),
                        primaryColor.copy(alpha = 0.03f),
                        Color.Transparent
                    ),
                    center = centerOffset,
                    radius = size.width * 0.85f * ambientPulse
                )
                drawCircle(brush = brush, center = centerOffset, radius = size.width * 0.85f * ambientPulse)
            }
            .graphicsLayer {
                translationY = dragOffsetY.value.coerceAtLeast(0f)
            }
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragEnd = {
                        if (dragOffsetY.value > dismissThresholdPx) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            coroutineScope.launch {
                                dragOffsetY.animateTo(
                                    targetValue = 2000f,
                                    animationSpec = tween(durationMillis = 220, easing = FastOutLinearInEasing)
                                )
                                onDismiss()
                            }
                        } else {
                            coroutineScope.launch {
                                dragOffsetY.animateTo(0f, spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMediumLow))
                            }
                        }
                    },
                    onDragCancel = {
                        coroutineScope.launch {
                            dragOffsetY.animateTo(0f, spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMediumLow))
                        }
                    }
                ) { change, dragAmount ->
                    if (listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0) {
                        if (dragAmount > 0 || dragOffsetY.value > 0) {
                            change.consume()
                            val newOffset = dragOffsetY.value + dragAmount
                            coroutineScope.launch {
                                dragOffsetY.snapTo(newOffset.coerceAtLeast(0f))
                            }
                        }
                    }
                }
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            // M3 Expressive Pull Handle Bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp, bottom = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .width(44.dp)
                        .height(5.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.30f))
                )
            }

            // MiniPlayer-Styled Header Card
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp, bottomStart = 10.dp, bottomEnd = 10.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 6.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Track Cover Image
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        if (coverUrl.isNotEmpty()) {
                            AsyncImage(
                                model = coverUrl,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(CircleShape)
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.MusicNote,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    // Track Title & Artist
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = trackTitle.ifEmpty { "Загрузка..." },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = artistName.ifEmpty { "Неизвестный исполнитель" },
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    if (lyricsResult?.sourceName != null) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        ) {
                            Text(
                                text = lyricsResult.sourceName,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    // Play/Pause Button
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer)
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onPlayPauseToggle()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play),
                            contentDescription = "Play/Pause",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    // Next Track Button
                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onSkipNext()
                        },
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_skip_next),
                            contentDescription = "Next",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Main Content Area
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                val unsyncedLyrics = lyricsResult?.lyrics as? SemanticLyrics.UnsyncedLyrics
                val validUnsyncedLines = remember(unsyncedLyrics) {
                    unsyncedLyrics?.unsyncedText?.filter { (line, _) -> line.trim() != "null" && line.isNotBlank() } ?: emptyList()
                }

                when {
                    isLoading -> {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator(
                                strokeWidth = 4.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(20.dp))
                            Text(
                                text = "Поиск текста в агрегаторах...",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    lyricsResult?.isInstrumental == true -> {
                        EmptyLyricsState(
                            message = "Инструментальный трек (без текста)",
                            onRetry = { onSourceSelected(selectedSource) }
                        )
                    }

                    displayItems.isNotEmpty() -> {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 28.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            itemsIndexed(displayItems) { index, item ->
                                val isActive = index == activeItemIndex

                                when (item) {
                                    is LyricsDisplayItem.NormalLine -> {
                                        val line = item.line

                                        val lineScale by animateFloatAsState(
                                            targetValue = if (isActive) 1.05f else 1.0f,
                                            animationSpec = spring(
                                                stiffness = Spring.StiffnessLow,
                                                dampingRatio = Spring.DampingRatioMediumBouncy
                                            ),
                                            label = "lineScale"
                                        )

                                        val lineBgColor by animateColorAsState(
                                            targetValue = if (isActive) {
                                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                                            } else {
                                                Color.Transparent
                                            },
                                            animationSpec = tween(durationMillis = 280),
                                            label = "lineBgColor"
                                        )

                                        val fontSize = if (isActive) 30.sp else 20.sp
                                        val fontWeight = if (isActive) FontWeight.Black else FontWeight.SemiBold
                                        val lineHeight = if (isActive) 38.sp else 28.sp

                                        // Compute progress fraction across line duration
                                        val startMs = line.start.toLong()
                                        val endMs = if (line.end > line.start) line.end.toLong() else startMs + 3000L
                                        val totalDuration = (endMs - startMs).coerceAtLeast(100L)
                                        val elapsed = (currentPositionMs - startMs).coerceAtLeast(0L)
                                        val lineProgressFraction = if (isActive) (elapsed.toFloat() / totalDuration.toFloat()).coerceIn(0f, 1f) else 0f

                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .scale(lineScale)
                                                .clip(RoundedCornerShape(18.dp))
                                                .background(lineBgColor)
                                                .clickable {
                                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    onSeekTo(line.start.toLong())
                                                }
                                                .padding(vertical = 10.dp, horizontal = 16.dp)
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                if (isActive) {
                                                    val barHeight by animateDpAsState(
                                                        targetValue = if (isActive) 26.dp else 0.dp,
                                                        animationSpec = spring(stiffness = Spring.StiffnessMedium),
                                                        label = "barHeight"
                                                    )
                                                    Box(
                                                        modifier = Modifier
                                                            .padding(end = 12.dp)
                                                            .width(4.dp)
                                                            .height(barHeight)
                                                            .clip(CircleShape)
                                                            .background(MaterialTheme.colorScheme.primary)
                                                    )
                                                }

                                                Column(modifier = Modifier.weight(1f)) {
                                                    LiquidKaraokeText(
                                                        text = line.text.ifEmpty { "♪" },
                                                        progressFraction = lineProgressFraction,
                                                        isActive = isActive,
                                                        fontSize = fontSize,
                                                        fontWeight = fontWeight,
                                                        lineHeight = lineHeight,
                                                        activeColor = MaterialTheme.colorScheme.primary,
                                                        inactiveColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
                                                    )

                                                    if (isAutoTranslateEnabled && !line.translation.isNullOrBlank()) {
                                                        Spacer(modifier = Modifier.height(4.dp))
                                                        Text(
                                                            text = line.translation!!,
                                                            style = MaterialTheme.typography.bodyMedium,
                                                            fontSize = if (isActive) 16.sp else 14.sp,
                                                            fontWeight = FontWeight.Medium,
                                                            color = if (isActive) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                                                            lineHeight = if (isActive) 22.sp else 18.sp
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    is LyricsDisplayItem.InstrumentalInterlude -> {
                                        val totalDuration = (item.endMs - item.startMs).coerceAtLeast(1L)
                                        val elapsed = (currentPositionMs - item.startMs).coerceAtLeast(0L)
                                        val progressFraction = (elapsed.toFloat() / totalDuration.toFloat()).coerceIn(0f, 1f)

                                        val cardBgColor by animateColorAsState(
                                            targetValue = if (isActive) {
                                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                                            } else {
                                                MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.30f)
                                            },
                                            animationSpec = tween(300),
                                            label = "cardBgColor"
                                        )

                                        Surface(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 6.dp)
                                                .clickable {
                                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    onSeekTo(item.startMs)
                                                },
                                            shape = RoundedCornerShape(24.dp),
                                            color = cardBgColor,
                                            tonalElevation = if (isActive) 4.dp else 0.dp
                                        ) {
                                            Column(
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                verticalArrangement = Arrangement.Center,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(vertical = 18.dp, horizontal = 24.dp)
                                            ) {
                                                // Expressive 3 Bouncing Rhythm Dots
                                                ExpressiveRhythmDots(
                                                    isActive = isActive,
                                                    isPlaying = isPlaying,
                                                    dotColor = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.40f)
                                                )

                                                // Progress indicator properly spaced below dots
                                                if (isActive && progressFraction in 0f..1f) {
                                                    Spacer(modifier = Modifier.height(14.dp))
                                                    LinearProgressIndicator(
                                                        progress = { progressFraction },
                                                        modifier = Modifier
                                                            .fillMaxWidth(0.5f)
                                                            .height(3.dp)
                                                            .clip(CircleShape),
                                                        color = MaterialTheme.colorScheme.primary,
                                                        trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    validUnsyncedLines.isNotEmpty() -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 28.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            itemsIndexed(validUnsyncedLines) { _, (lineText, _) ->
                                Text(
                                    text = lineText,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    lineHeight = 32.sp,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }

                    else -> {
                        EmptyLyricsState(
                            message = "Текст для этой песни не найден",
                            onRetry = { onSourceSelected(selectedSource) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LiquidKaraokeText(
    text: String,
    progressFraction: Float,
    isActive: Boolean,
    fontSize: TextUnit,
    fontWeight: FontWeight,
    lineHeight: TextUnit,
    activeColor: Color,
    inactiveColor: Color,
    modifier: Modifier = Modifier
) {
    if (!isActive) {
        Text(
            text = text,
            fontSize = fontSize,
            fontWeight = fontWeight,
            color = inactiveColor,
            lineHeight = lineHeight,
            modifier = modifier
        )
        return
    }

    val animatedProgress by animateFloatAsState(
        targetValue = progressFraction.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 140, easing = LinearEasing),
        label = "liquidProgress"
    )

    Box(modifier = modifier) {
        // Base Layer: Inactive dimmed text
        Text(
            text = text,
            fontSize = fontSize,
            fontWeight = fontWeight,
            color = inactiveColor,
            lineHeight = lineHeight
        )

        // Overlay Layer: Active brightly lit text smoothly clipped by gradient sweep
        Text(
            text = text,
            fontSize = fontSize,
            fontWeight = fontWeight,
            color = activeColor,
            lineHeight = lineHeight,
            modifier = Modifier
                .graphicsLayer {
                    compositingStrategy = CompositingStrategy.Offscreen
                }
                .drawWithContent {
                    drawContent()
                    val currentX = size.width * animatedProgress
                    val edgeWidth = 36.dp.toPx()
                    val pStop = (currentX / size.width).coerceIn(0f, 1f)
                    val eStop = ((currentX + edgeWidth) / size.width).coerceIn(0f, 1f)

                    val brush = Brush.horizontalGradient(
                        0f to Color.Black,
                        pStop to Color.Black,
                        eStop to Color.Transparent,
                        1f to Color.Transparent
                    )
                    drawRect(
                        brush = brush,
                        blendMode = BlendMode.DstIn
                    )
                }
        )
    }
}

@Composable
private fun ExpressiveRhythmDots(
    isActive: Boolean,
    isPlaying: Boolean,
    dotColor: Color,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "rhythmDots")

    val scale1 by infiniteTransition.animateFloat(
        initialValue = 0.6f, targetValue = 1.3f,
        animationSpec = infiniteRepeatable(tween(600, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "d1"
    )
    val scale2 by infiniteTransition.animateFloat(
        initialValue = 1.3f, targetValue = 0.6f,
        animationSpec = infiniteRepeatable(tween(600, delayMillis = 200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "d2"
    )
    val scale3 by infiniteTransition.animateFloat(
        initialValue = 0.6f, targetValue = 1.3f,
        animationSpec = infiniteRepeatable(tween(600, delayMillis = 400, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "d3"
    )

    val alpha1 by infiniteTransition.animateFloat(
        initialValue = 0.35f, targetValue = 1.0f,
        animationSpec = infiniteRepeatable(tween(600, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "a1"
    )
    val alpha2 by infiniteTransition.animateFloat(
        initialValue = 1.0f, targetValue = 0.35f,
        animationSpec = infiniteRepeatable(tween(600, delayMillis = 200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "a2"
    )
    val alpha3 by infiniteTransition.animateFloat(
        initialValue = 0.35f, targetValue = 1.0f,
        animationSpec = infiniteRepeatable(tween(600, delayMillis = 400, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "a3"
    )

    Row(
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
    ) {
        val activeState = isPlaying && isActive
        val scales = if (activeState) listOf(scale1, scale2, scale3) else listOf(1f, 1f, 1f)
        val alphas = if (activeState) listOf(alpha1, alpha2, alpha3) else listOf(0.4f, 0.4f, 0.4f)

        for (i in 0..2) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .scale(scales[i])
                    .clip(CircleShape)
                    .background(dotColor.copy(alpha = alphas[i]))
            )
        }
    }
}

@Composable
private fun EmptyLyricsState(
    message: String,
    onRetry: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.padding(32.dp)
    ) {
        Icon(
            imageVector = Icons.Default.MusicNote,
            contentDescription = null,
            modifier = Modifier.size(72.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
        )
        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(20.dp))
        Button(
            onClick = onRetry,
            shape = RoundedCornerShape(24.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            )
        ) {
            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Обновить поиск", fontWeight = FontWeight.Bold)
        }
    }
}
