package org.akanework.gramophone.desktop.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.automirrored.rounded.VolumeDown
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import org.akanework.gramophone.desktop.player.DesktopSalvationPlayer
import org.akanework.gramophone.desktop.player.PlayerRepeatMode

@Composable
fun DesktopBottomPlayerBar(
    player: DesktopSalvationPlayer,
    isLyricsOpen: Boolean,
    isQueueOpen: Boolean,
    onToggleLyrics: () -> Unit,
    onToggleQueue: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val currentTrack by player.currentTrack.collectAsState()
    val isPlaying by player.isPlaying.collectAsState()
    val posMs by player.positionMs.collectAsState()
    val durMs by player.durationMs.collectAsState()
    val volume by player.volume.collectAsState()
    val repeatMode by player.repeatMode.collectAsState()
    val isShuffle by player.isShuffleEnabled.collectAsState()

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(86.dp),
        color = colors.surfaceContainerLow,
        tonalElevation = 6.dp,
        border = BorderStroke(0.5.dp, colors.outlineVariant.copy(alpha = 0.25f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 1. Левый блок (Трек + Обложка + FLAC + Like)
            Row(
                modifier = Modifier.weight(0.28f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AsyncImage(
                    model = formatCoverUrl(currentTrack?.cover),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(54.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(colors.surfaceContainerHighest)
                )

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Start
                    ) {
                        Text(
                            text = currentTrack?.title ?: "Нет воспроизведения",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (currentTrack?.is_lossless == true) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = Color(0xFFD4AF37).copy(alpha = 0.15f),
                                border = BorderStroke(0.75.dp, Color(0xFFD4AF37).copy(alpha = 0.6f))
                            ) {
                                Box(
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.5.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "FLAC",
                                        color = Color(0xFFD4AF37),
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontSize = 8.5.sp,
                                            fontWeight = FontWeight.Black,
                                            letterSpacing = 0.5.sp,
                                            lineHeight = 10.sp
                                        )
                                    )
                                }
                            }
                        }
                    }
                    Text(
                        text = currentTrack?.artist ?: "Выберите трек для прослушивания",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                        color = colors.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Кнопка Лайка (в цвет интерфейса!)
                IconButton(
                    onClick = { currentTrack?.let { it.is_liked = !it.is_liked } },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = if (currentTrack?.is_liked == true) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                        contentDescription = "Like",
                        tint = if (currentTrack?.is_liked == true) colors.primary else colors.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // 2. Центральный блок (Кнопки + Ползунок времени)
            Column(
                modifier = Modifier.weight(0.44f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    IconButton(onClick = { player.toggleShuffle() }, modifier = Modifier.size(32.dp)) {
                        Icon(
                            imageVector = Icons.Rounded.Shuffle,
                            contentDescription = "Shuffle",
                            tint = if (isShuffle) colors.primary else colors.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    IconButton(onClick = { player.playPrevious() }, modifier = Modifier.size(36.dp)) {
                        Icon(imageVector = Icons.Rounded.SkipPrevious, contentDescription = "Prev", modifier = Modifier.size(24.dp))
                    }

                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(colors.primary)
                            .clickable { player.playPause() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = "Play/Pause",
                            tint = colors.onPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    IconButton(onClick = { player.playNext() }, modifier = Modifier.size(36.dp)) {
                        Icon(imageVector = Icons.Rounded.SkipNext, contentDescription = "Next", modifier = Modifier.size(24.dp))
                    }

                    IconButton(onClick = { player.toggleRepeat() }, modifier = Modifier.size(32.dp)) {
                        Icon(
                            imageVector = if (repeatMode == PlayerRepeatMode.ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                            contentDescription = "Repeat",
                            tint = if (repeatMode != PlayerRepeatMode.OFF) colors.primary else colors.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val posSec = posMs / 1000
                    val durSec = durMs / 1000
                    Text(
                        text = "%d:%02d".format(posSec / 60, posSec % 60),
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                        color = colors.onSurfaceVariant,
                        modifier = Modifier.width(40.dp)
                    )

                    Slider(
                        value = if (durMs > 0) posMs.toFloat() / durMs.toFloat() else 0f,
                        onValueChange = { player.seekTo((it * durMs).toLong()) },
                        modifier = Modifier.weight(1f).height(16.dp),
                        colors = SliderDefaults.colors(
                            thumbColor = colors.primary,
                            activeTrackColor = colors.primary,
                            inactiveTrackColor = colors.surfaceContainerHighest
                        )
                    )

                    Text(
                        text = "%d:%02d".format(durSec / 60, durSec % 60),
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                        color = colors.onSurfaceVariant,
                        modifier = Modifier.width(40.dp)
                    )
                }
            }

            // 3. Правый блок (Текст караоке + Очередь + Громкость)
            Row(
                modifier = Modifier.weight(0.28f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End
            ) {
                IconButton(onClick = onToggleLyrics, modifier = Modifier.size(36.dp)) {
                    Icon(
                        imageVector = Icons.Rounded.Lyrics,
                        contentDescription = "Lyrics",
                        tint = if (isLyricsOpen) colors.primary else colors.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }

                IconButton(onClick = onToggleQueue, modifier = Modifier.size(36.dp)) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.QueueMusic,
                        contentDescription = "Queue",
                        tint = if (isQueueOpen) colors.primary else colors.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Icon(
                    imageVector = when {
                        volume <= 0.01f -> Icons.AutoMirrored.Rounded.VolumeOff
                        volume < 0.5f -> Icons.AutoMirrored.Rounded.VolumeDown
                        else -> Icons.AutoMirrored.Rounded.VolumeUp
                    },
                    contentDescription = "Volume",
                    tint = colors.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )

                Spacer(modifier = Modifier.width(4.dp))

                Slider(
                    value = volume,
                    onValueChange = { player.setVolume(it) },
                    modifier = Modifier.width(100.dp)
                )
            }
        }
    }
}
