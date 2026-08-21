package org.akanework.gramophone.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
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
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
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
import kotlin.math.abs

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
    dynamicArtworkColors: org.akanework.gramophone.ui.theme.DynamicArtworkTheme.ArtworkColors? = null,
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
    var isAutoTranslateEnabled by remember {
        mutableStateOf(defaultPrefs.getBoolean("lyrics_auto_translate", true))
    }
    var isKaraokeModeEnabled by remember {
        mutableStateOf(defaultPrefs.getBoolean("lyrics_karaoke_mode", true))
    }
    var isHighlightActiveLineEnabled by remember {
        mutableStateOf(defaultPrefs.getBoolean("lyrics_highlight_active_line", true))
    }

    // Sub-frame 120 FPS high-precision position engine with zero-overhead frame clock
    var syncPositionMs by remember { mutableLongStateOf(currentPositionMs) }
    var syncSysTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var currentFrameTime by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(currentPositionMs) {
        syncPositionMs = currentPositionMs
        syncSysTime = System.currentTimeMillis()
        currentFrameTime = syncSysTime
    }

    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            while (true) {
                withFrameMillis {
                    currentFrameTime = System.currentTimeMillis()
                }
            }
        }
    }

    val smoothPositionMs = if (isPlaying) {
        val elapsedLocal = (currentFrameTime - syncSysTime).coerceAtLeast(0L)
        syncPositionMs + elapsedLocal
    } else {
        syncPositionMs
    }

    // 350ms Predictive Vocal Lead Compensation
    val adjustedPositionMs = smoothPositionMs + 350L

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
        initialValue = 0.85f, targetValue = 1.15f,
        animationSpec = infiniteRepeatable(tween(4500, easing = FastOutSlowInEasing), RepeatMode.Reverse),
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

    val activeItemIndex = remember(displayItems, adjustedPositionMs) {
        if (displayItems.isEmpty()) -1
        else {
            val idx = displayItems.indexOfLast { item ->
                when (item) {
                    is LyricsDisplayItem.NormalLine -> adjustedPositionMs >= item.line.start.toLong()
                    is LyricsDisplayItem.InstrumentalInterlude -> adjustedPositionMs >= item.startMs
                }
            }
            if (idx >= 0) idx else 0
        }
    }

    // Smooth auto-scroll with Spring physics (only if karaoke mode is enabled)
    LaunchedEffect(activeItemIndex, isKaraokeModeEnabled) {
        if (isKaraokeModeEnabled && activeItemIndex >= 0 && activeItemIndex < displayItems.size) {
            listState.animateScrollToItem(
                index = (activeItemIndex - 2).coerceAtLeast(0)
            )
        }
    }

    val gradientTop = dynamicArtworkColors?.fullPlayerGradientTop ?: MaterialTheme.colorScheme.surface
    val secondaryGlow = dynamicArtworkColors?.fullPlayerSecondaryGlow ?: MaterialTheme.colorScheme.surfaceContainer
    val gradientBottom = dynamicArtworkColors?.fullPlayerGradientBottom ?: MaterialTheme.colorScheme.surfaceContainerHigh
    val accentColor = dynamicArtworkColors?.accentColor ?: MaterialTheme.colorScheme.primary
    val surfaceColor = MaterialTheme.colorScheme.surface

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(surfaceColor)
            .drawBehind {
                // Exact player dynamic background gradient
                if (dynamicArtworkColors != null) {
                    val gradientBrush = Brush.verticalGradient(
                        colors = listOf(
                            gradientTop.copy(alpha = 0.95f),
                            secondaryGlow.copy(alpha = 0.85f),
                            gradientBottom.copy(alpha = 0.95f)
                        )
                    )
                    drawRect(brush = gradientBrush)
                }

                // PixelPlayer-styled Expressive Ambient Radial Glow
                val centerOffset = Offset(size.width * 0.5f, size.height * 0.28f)
                val glowRadius = size.width * 1.05f * ambientPulse
                val brush = Brush.radialGradient(
                    colors = listOf(
                        accentColor.copy(alpha = 0.26f),
                        accentColor.copy(alpha = 0.08f),
                        Color.Transparent
                    ),
                    center = centerOffset,
                    radius = glowRadius
                )
                drawCircle(brush = brush, center = centerOffset, radius = glowRadius)
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

            // Top Floating Header Card (PixelPlayer Inspired)
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                shape = RoundedCornerShape(24.dp),
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
                            .size(46.dp)
                            .clip(RoundedCornerShape(14.dp))
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
                                    .clip(RoundedCornerShape(14.dp))
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
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            modifier = Modifier.basicMarquee()
                        )
                        Text(
                            text = artistName.ifEmpty { "Неизвестный исполнитель" },
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Translation Toggle Button
                    IconButton(
                        onClick = {
                            val next = !isAutoTranslateEnabled
                            isAutoTranslateEnabled = next
                            defaultPrefs.edit().putBoolean("lyrics_auto_translate", next).apply()
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        },
                        modifier = Modifier.size(38.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Translate,
                            contentDescription = "Translate",
                            tint = if (isAutoTranslateEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Play/Pause Button
                    FilledIconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onPlayPauseToggle()
                        },
                        shape = CircleShape,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        modifier = Modifier.size(38.dp)
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = "Play/Pause",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

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
                                strokeWidth = 3.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Поиск текста в агрегаторах...",
                                style = MaterialTheme.typography.bodyMedium,
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
                            contentPadding = PaddingValues(horizontal = 22.dp, vertical = 24.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            itemsIndexed(displayItems) { index, item ->
                                val isActive = index == activeItemIndex
                                val isHighlighted = isActive && isHighlightActiveLineEnabled
                                val distanceFromCurrent = if (activeItemIndex != -1) abs(activeItemIndex - index) else 100

                                when (item) {
                                    is LyricsDisplayItem.NormalLine -> {
                                        val line = item.line

                                        // PixelPlayer & Apple Music Fisheye scaling & focus depth
                                        val targetScale = if (!isHighlightActiveLineEnabled) {
                                            1.0f
                                        } else {
                                            when (distanceFromCurrent) {
                                                0 -> 1.05f
                                                1 -> 0.98f
                                                else -> 0.92f
                                            }
                                        }

                                        val targetAlpha = if (!isHighlightActiveLineEnabled) {
                                            0.90f
                                        } else {
                                            when (distanceFromCurrent) {
                                                0 -> 1.0f
                                                1 -> 0.65f
                                                else -> 0.35f
                                            }
                                        }

                                        val lineScale by animateFloatAsState(
                                            targetValue = targetScale,
                                            animationSpec = spring(
                                                stiffness = Spring.StiffnessMediumLow,
                                                dampingRatio = Spring.DampingRatioLowBouncy
                                            ),
                                            label = "lineScale"
                                        )

                                        val lineAlpha by animateFloatAsState(
                                            targetValue = targetAlpha,
                                            animationSpec = tween(250),
                                            label = "lineAlpha"
                                        )

                                        val lineBgColor by animateColorAsState(
                                            targetValue = if (isHighlighted) {
                                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.28f)
                                            } else {
                                                Color.Transparent
                                            },
                                            animationSpec = tween(durationMillis = 260),
                                            label = "lineBgColor"
                                        )

                                        val fontSize = if (isHighlighted) 23.sp else 18.sp
                                        val fontWeight = if (isHighlighted) FontWeight.ExtraBold else FontWeight.SemiBold
                                        val lineHeight = if (isHighlighted) 32.sp else 25.sp

                                        // Compute Vocal Attack Pacing S-Curve across line duration
                                        val startMs = line.start.toLong()
                                        val endMs = if (line.end > line.start) line.end.toLong() else startMs + 3200L
                                        val totalDurationMs = (endMs - startMs).coerceAtLeast(100L)
                                        val elapsedMs = (adjustedPositionMs - startMs).coerceAtLeast(0L)

                                        val vocalProgressFraction = if (isActive) {
                                            if (isKaraokeModeEnabled) calculateVocalProgress(elapsedMs, totalDurationMs) else (if (isHighlighted) 1f else 0f)
                                        } else {
                                            0f
                                        }

                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .graphicsLayer {
                                                    scaleX = lineScale
                                                    scaleY = lineScale
                                                    alpha = lineAlpha
                                                }
                                                .clip(RoundedCornerShape(18.dp))
                                                .background(lineBgColor)
                                                .clickable {
                                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    onSeekTo(line.start.toLong())
                                                }
                                                .padding(vertical = 10.dp, horizontal = 16.dp)
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                if (isHighlighted) {
                                                    val barHeight by animateDpAsState(
                                                        targetValue = if (isHighlighted) 24.dp else 0.dp,
                                                        animationSpec = spring(stiffness = Spring.StiffnessMedium),
                                                        label = "barHeight"
                                                    )
                                                    Box(
                                                        modifier = Modifier
                                                            .padding(end = 12.dp)
                                                            .width(4.5.dp)
                                                            .height(barHeight)
                                                            .clip(CircleShape)
                                                            .background(accentColor)
                                                    )
                                                }

                                                Column(modifier = Modifier.weight(1f)) {
                                                    LiquidKaraokeText(
                                                        text = line.text.ifEmpty { "♪" },
                                                        progressFraction = vocalProgressFraction,
                                                        isActive = isHighlighted,
                                                        fontSize = fontSize,
                                                        fontWeight = fontWeight,
                                                        lineHeight = lineHeight,
                                                        activeColor = accentColor,
                                                        inactiveColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
                                                    )

                                                    if (isAutoTranslateEnabled && !line.translation.isNullOrBlank()) {
                                                        Spacer(modifier = Modifier.height(4.dp))
                                                        Text(
                                                            text = line.translation!!,
                                                            fontSize = if (isHighlighted) 15.5.sp else 13.5.sp,
                                                            fontWeight = if (isHighlighted) FontWeight.Medium else FontWeight.Normal,
                                                            color = if (isHighlighted) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.90f)
                                                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.50f),
                                                            lineHeight = if (isHighlighted) 21.sp else 18.sp
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    is LyricsDisplayItem.InstrumentalInterlude -> {
                                        val totalDuration = (item.endMs - item.startMs).coerceAtLeast(1L)
                                        val elapsed = (adjustedPositionMs - item.startMs).coerceAtLeast(0L)
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
                                            shape = RoundedCornerShape(20.dp),
                                            color = cardBgColor,
                                            tonalElevation = if (isActive) 3.dp else 0.dp
                                        ) {
                                            Column(
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                verticalArrangement = Arrangement.Center,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(vertical = 14.dp, horizontal = 20.dp)
                                            ) {
                                                // Expressive 3 Bouncing Rhythm Dots
                                                ExpressiveRhythmDots(
                                                    isActive = isActive,
                                                    isPlaying = isPlaying,
                                                    dotColor = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.40f)
                                                )

                                                if (isActive && progressFraction in 0f..1f) {
                                                    Spacer(modifier = Modifier.height(12.dp))
                                                    LinearProgressIndicator(
                                                        progress = { progressFraction },
                                                        modifier = Modifier
                                                            .fillMaxWidth(0.45f)
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
                        Column(modifier = Modifier.fillMaxSize()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 20.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.End
                            ) {
                                TextButton(
                                    onClick = {
                                        val fullText = validUnsyncedLines.joinToString("\n") { it.first }
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        clipboard.setPrimaryClip(ClipData.newPlainText("Lyrics", fullText))
                                        Toast.makeText(context, "Текст скопирован в буфер", Toast.LENGTH_SHORT).show()
                                    }
                                ) {
                                    Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Скопировать текст")
                                }
                            }

                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(horizontal = 22.dp, vertical = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                itemsIndexed(validUnsyncedLines) { _, (lineText, _) ->
                                    Text(
                                        text = lineText,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.90f),
                                        fontSize = 19.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        lineHeight = 28.sp,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
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

private fun calculateVocalProgress(elapsedMs: Long, totalDurationMs: Long): Float {
    if (totalDurationMs <= 0) return 0f
    val raw = (elapsedMs.toFloat() / totalDurationMs.toFloat()).coerceIn(0f, 1f)
    // Singer Pacing S-Curve: Fast initial vocal attack (0..0.6 -> 0..0.8), smooth tail sustain (0.6..1.0 -> 0.8..1.0)
    return if (raw < 0.60f) {
        (raw / 0.60f) * 0.80f
    } else {
        0.80f + ((raw - 0.60f) / 0.40f) * 0.20f
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

    val totalLength = text.length
    val charProgress = totalLength * progressFraction.coerceIn(0f, 1f)
    val fullSungCount = charProgress.toInt().coerceIn(0, totalLength)
    val partialCharFraction = charProgress - fullSungCount
    val quantizedAlphaStep = (partialCharFraction * 10f).toInt().coerceIn(0, 10)

    val annotatedString = remember(text, fullSungCount, quantizedAlphaStep, activeColor, inactiveColor) {
        val currentAlpha = 0.45f + (0.55f * (quantizedAlphaStep / 10f))
        buildAnnotatedString {
            for (i in 0 until totalLength) {
                val ch = text[i].toString()
                when {
                    i < fullSungCount -> {
                        withStyle(
                            SpanStyle(
                                color = activeColor,
                                fontWeight = FontWeight.ExtraBold
                            )
                        ) {
                            append(ch)
                        }
                    }
                    i == fullSungCount -> {
                        withStyle(
                            SpanStyle(
                                color = activeColor.copy(alpha = currentAlpha),
                                fontWeight = FontWeight.ExtraBold
                            )
                        ) {
                            append(ch)
                        }
                    }
                    else -> {
                        withStyle(
                            SpanStyle(
                                color = inactiveColor,
                                fontWeight = FontWeight.SemiBold
                            )
                        ) {
                            append(ch)
                        }
                    }
                }
            }
        }
    }

    Text(
        text = annotatedString,
        fontSize = fontSize,
        lineHeight = lineHeight,
        modifier = modifier
    )
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
