package org.akanework.gramophone.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.akanework.gramophone.desktop.lyrics.DesktopLyricLine
import org.akanework.gramophone.desktop.lyrics.DesktopLyrics
import org.akanework.gramophone.desktop.lyrics.DesktopLyricsRepository
import org.akanework.gramophone.desktop.player.DesktopSalvationPlayer
import org.akanework.gramophone.desktop.settings.DesktopSettingsManager

enum class DesktopRightPanelTab(val title: String) {
    LYRICS("Текст песни"),
    QUEUE("Очередь")
}

@Composable
fun DesktopRightSidePanel(
    player: DesktopSalvationPlayer,
    selectedTab: DesktopRightPanelTab,
    onTabSelected: (DesktopRightPanelTab) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val currentTrack by player.currentTrack.collectAsState()
    val queue by player.queue.collectAsState()
    val queueIdx by player.queueIndex.collectAsState()
    val posMs by player.positionMs.collectAsState()

    // Reactive Settings
    val isAutoTranslateLyrics by DesktopSettingsManager.isAutoTranslateLyrics.collectAsState()
    val isKaraokeMode by DesktopSettingsManager.isKaraokeMode.collectAsState()
    val isHighlightActiveLine by DesktopSettingsManager.isHighlightActiveLine.collectAsState()

    var lyricsData by remember { mutableStateOf<DesktopLyrics?>(null) }
    var isLyricsLoading by remember { mutableStateOf(false) }

    // Fetch lyrics on track change
    LaunchedEffect(currentTrack?.id, isAutoTranslateLyrics) {
        val track = currentTrack
        if (track != null) {
            isLyricsLoading = true
            lyricsData = null
            withContext(Dispatchers.IO) {
                val fetched = DesktopLyricsRepository.fetchLyrics(
                    artist = track.artist,
                    title = track.title,
                    durationMs = (track.duration * 1000).toLong()
                )
                lyricsData = fetched
                isLyricsLoading = false
            }
        } else {
            lyricsData = null
            isLyricsLoading = false
        }
    }

    Surface(
        modifier = modifier
            .width(420.dp)
            .fillMaxHeight(),
        color = colors.surfaceContainerLow,
        tonalElevation = 4.dp,
        border = androidx.compose.foundation.BorderStroke(0.5.dp, colors.outlineVariant.copy(alpha = 0.25f))
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 1. Header (Pills + Close)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
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
                                .clickable { onTabSelected(tab) }
                                .padding(horizontal = 16.dp, vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = tab.title,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) colors.onPrimary else colors.onSurfaceVariant
                                )
                            )
                        }
                    }
                }

                IconButton(onClick = onClose) {
                    Icon(imageVector = Icons.Rounded.Close, contentDescription = "Close")
                }
            }

            HorizontalDivider(color = colors.outlineVariant.copy(alpha = 0.2f))

            // 2. Content
            if (selectedTab == DesktopRightPanelTab.LYRICS) {
                if (currentTrack == null) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = "Выберите трек для просмотра текста",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.onSurfaceVariant
                        )
                    }
                } else if (isLyricsLoading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(modifier = Modifier.size(32.dp))
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Поиск текста песни...",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.onSurfaceVariant
                            )
                        }
                    }
                } else if (lyricsData == null || lyricsData!!.lines.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize().padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = currentTrack?.title ?: "",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = currentTrack?.artist ?: "",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = if (lyricsData?.isInstrumental == true) "Инструментальный трек" else "Текст песни не найден",
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }
                    }
                } else {
                    val lines = lyricsData!!.lines
                    val isSynced = lyricsData!!.isSynced && isKaraokeMode
                    val listState = rememberLazyListState()

                    // Find active line index
                    val activeIndex = if (isSynced) {
                        lines.indexOfLast { it.startMs <= posMs }.coerceAtLeast(0)
                    } else -1

                    // Auto scroll to active line
                    LaunchedEffect(activeIndex, isKaraokeMode) {
                        if (isKaraokeMode && activeIndex >= 0) {
                            val targetIndex = (activeIndex - 2).coerceAtLeast(0)
                            listState.animateScrollToItem(targetIndex)
                        }
                    }

                    Column(modifier = Modifier.fillMaxSize()) {
                        // Источник текста
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = if (isSynced) "Синхронизированный караоке" else "Текст песни",
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.onSurfaceVariant
                            )
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = colors.surfaceContainerHighest
                            ) {
                                Text(
                                    text = lyricsData!!.sourceName,
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                    color = colors.primary,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            itemsIndexed(lines) { idx, line ->
                                val isActive = (idx == activeIndex) && isHighlightActiveLine

                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(if (isActive) colors.primaryContainer.copy(alpha = 0.35f) else Color.Transparent)
                                        .clickable {
                                            if (lyricsData!!.isSynced && line.startMs > 0) {
                                                player.seekTo(line.startMs)
                                            }
                                        }
                                        .padding(horizontal = 14.dp, vertical = 8.dp)
                                ) {
                                    Text(
                                        text = line.text,
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                                            fontSize = if (isActive) 18.sp else 15.sp,
                                            lineHeight = if (isActive) 26.sp else 22.sp
                                        ),
                                        color = if (isActive) colors.primary else colors.onSurface.copy(alpha = 0.6f)
                                    )

                                    if (isAutoTranslateLyrics && !line.translation.isNullOrBlank()) {
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = line.translation!!,
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                fontSize = if (isActive) 14.sp else 12.sp
                                            ),
                                            color = if (isActive) colors.primary.copy(alpha = 0.85f) else colors.onSurfaceVariant.copy(alpha = 0.45f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                // Queue Tab
                val listState = rememberLazyListState()
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "В очереди: ${queue.size} треков",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.onSurfaceVariant
                        )
                    }

                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        itemsIndexed(queue) { idx, track ->
                            val isCur = idx == queueIdx
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { player.playTrack(track, queue) },
                                color = if (isCur) colors.primaryContainer.copy(alpha = 0.4f) else colors.surfaceContainer,
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = (idx + 1).toString(),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = colors.onSurfaceVariant,
                                        modifier = Modifier.width(28.dp)
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = track.title,
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = if (isCur) FontWeight.Bold else FontWeight.Medium),
                                            color = if (isCur) colors.primary else colors.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = track.artist,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = colors.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
