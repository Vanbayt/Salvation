package org.akanework.gramophone.ui.components.desktop

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.akanework.gramophone.logic.utils.LyricsRepository
import org.akanework.gramophone.logic.utils.LyricsResult
import org.akanework.gramophone.logic.utils.LyricsSource
import org.akanework.gramophone.logic.utils.LrcUtils
import org.akanework.gramophone.ui.MainActivity
import org.akanework.gramophone.ui.components.LyricsScreen

enum class DesktopRightPanelTab(val title: String) {
    LYRICS("Текст песни"),
    QUEUE("Очередь")
}

@Composable
fun DesktopRightSidePanel(
    mainActivity: MainActivity,
    selectedTab: DesktopRightPanelTab,
    onTabSelected: (DesktopRightPanelTab) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val haptic = LocalHapticFeedback.current

    val player = mainActivity.getPlayer()
    val curItem = player?.currentMediaItem
    val title = curItem?.mediaMetadata?.title?.toString() ?: ""
    val artist = curItem?.mediaMetadata?.artist?.toString() ?: ""
    val rawCover = curItem?.mediaMetadata?.artworkUri?.toString() ?: ""
    val trackId = curItem?.mediaId
    val coverUrl = org.akanework.gramophone.logic.utils.CoverUtils.upgradeCoverQuality(
        org.akanework.gramophone.logic.utils.CoverUtils.getBestCover(
            rawCover,
            org.akanework.gramophone.logic.utils.exoplayer.ClientTrackResolver.getTrackCover(trackId)
        )
    ) ?: ""

    var isPlaying by remember { mutableStateOf(player?.isPlaying == true) }
    var currentPositionMs by remember { mutableLongStateOf(player?.currentPosition ?: 0L) }

    LaunchedEffect(player) {
        if (player == null) return@LaunchedEffect
        while (true) {
            isPlaying = player.isPlaying
            currentPositionMs = player.currentPosition
            delay(250)
        }
    }

    var currentLyricsResult by remember { mutableStateOf<LyricsResult?>(null) }
    var isLyricsLoading by remember { mutableStateOf(false) }
    var selectedLyricsSource by remember { mutableStateOf(LyricsSource.ALL) }

    fun loadLyrics(source: LyricsSource = selectedLyricsSource) {
        if (title.isBlank()) return
        isLyricsLoading = true
        val durationMs = curItem?.mediaMetadata?.durationMs ?: 0L
        val options = LrcUtils.LrcParserOptions(
            trim = true, multiLine = false,
            errorText = "Не удалось распарсить текст"
        )

        CoroutineScope(Dispatchers.Main).launch {
            val res = LyricsRepository.fetchLyrics(
                context = mainActivity,
                file = null,
                mimeType = null,
                sampleRate = 0,
                metadata = null,
                artist = artist,
                title = title,
                durationMs = durationMs,
                preferredSource = source,
                options = options
            )
            currentLyricsResult = res
            isLyricsLoading = false
        }
    }

    LaunchedEffect(title) {
        currentLyricsResult = null
        loadLyrics()
    }

    Surface(
        modifier = modifier
            .width(380.dp)
            .fillMaxHeight(),
        color = colors.surfaceContainerLow,
        tonalElevation = 4.dp,
        border = androidx.compose.foundation.BorderStroke(0.5.dp, colors.outlineVariant.copy(alpha = 0.25f))
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header: Pill Tabs + Close Button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Segmented Tabs Pill
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(colors.surfaceContainerHighest)
                        .padding(3.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    DesktopRightPanelTab.values().forEach { tab ->
                        val isSelected = selectedTab == tab
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(18.dp))
                                .background(if (isSelected) colors.primary else Color.Transparent)
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onTabSelected(tab)
                                }
                                .padding(horizontal = 14.dp, vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = tab.title,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 12.5.sp
                                ),
                                color = if (isSelected) colors.onPrimary else colors.onSurfaceVariant
                            )
                        }
                    }
                }

                // Close Button
                IconButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onClose()
                    },
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Закрыть",
                        tint = colors.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            HorizontalDivider(color = colors.outlineVariant.copy(alpha = 0.25f))

            // Body: Content depending on active tab
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                AnimatedContent(
                    targetState = selectedTab,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "sidePanelTab"
                ) { tab ->
                    when (tab) {
                        DesktopRightPanelTab.LYRICS -> {
                            LyricsScreen(
                                trackTitle = title,
                                artistName = artist,
                                coverUrl = coverUrl,
                                isPlaying = isPlaying,
                                lyricsResult = currentLyricsResult,
                                isLoading = isLyricsLoading,
                                currentPositionMs = currentPositionMs,
                                onSeekTo = { pos -> player?.seekTo(pos) },
                                onDismiss = onClose,
                                onSourceSelected = { newSource ->
                                    selectedLyricsSource = newSource
                                    loadLyrics(newSource)
                                },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        DesktopRightPanelTab.QUEUE -> {
                            org.akanework.gramophone.ui.fragments.DesktopQueueView(
                                player = player,
                                onClose = onClose,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
            }
        }
    }
}
