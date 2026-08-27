package org.akanework.gramophone.ui.components.desktop

import android.content.Context
import android.media.AudioManager
import android.net.Uri
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import coil3.compose.AsyncImage
import org.akanework.gramophone.logic.LikeCache
import org.akanework.gramophone.logic.api.NetworkClient
import org.akanework.gramophone.ui.MainActivity
import org.akanework.gramophone.ui.components.SquigglySlider
import org.akanework.gramophone.ui.components.library.AudioQualityBadge

@Composable
fun DesktopBottomPlayerBar(
    mainActivity: MainActivity,
    trackTitle: String,
    trackArtist: String,
    coverUri: Uri?,
    trackId: String?,
    isPlaying: Boolean,
    positionMs: Long,
    durationMs: Long,
    repeatMode: Int,
    isLyricsOpen: Boolean,
    isQueueOpen: Boolean,
    onToggleLyrics: () -> Unit,
    onToggleQueue: () -> Unit,
    onOpenFullPlayer: () -> Unit,
    onOpenMenu: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val colors = MaterialTheme.colorScheme

    val isLiked = if (!trackId.isNullOrBlank()) LikeCache.isLiked(trackId) else false

    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    var volumeLevel by remember {
        val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).toFloat().coerceAtLeast(1f)
        val curVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat()
        mutableFloatStateOf((curVol / maxVol).coerceIn(0f, 1f))
    }

    var showAudioFidelitySheet by remember { mutableStateOf(false) }
    if (showAudioFidelitySheet) {
        val isLossless = org.akanework.gramophone.logic.lossless.LosslessStateManager.rememberIsTrackLossless(trackId, true)
        org.akanework.gramophone.ui.components.player.AudioFidelityBottomSheet(
            trackTitle = trackTitle,
            artistName = trackArtist,
            isLossless = isLossless,
            onDismiss = { showAudioFidelitySheet = false }
        )
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(86.dp),
        color = colors.surfaceContainerLow,
        tonalElevation = 6.dp,
        border = androidx.compose.foundation.BorderStroke(0.5.dp, colors.outlineVariant.copy(alpha = 0.25f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // ================= 1. ЛЕВЫЙ БЛОК: ТРЕК И ОБЛОЖКА (30%) =================
            Row(
                modifier = Modifier
                    .weight(0.30f)
                    .fillMaxHeight(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(colors.surfaceContainerHighest)
                        .clickable { onOpenFullPlayer() },
                    contentAlignment = Alignment.Center
                ) {
                    if (coverUri != null) {
                        AsyncImage(
                            model = coverUri,
                            contentDescription = trackTitle,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.MusicNote,
                            contentDescription = null,
                            tint = colors.onSurfaceVariant,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onOpenFullPlayer
                        ),
                    verticalArrangement = Arrangement.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = trackTitle.ifBlank { "Нет трека" },
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.5.sp
                            ),
                            color = colors.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )

                        AudioQualityBadge(text = "FLAC")
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = trackArtist.ifBlank { "Salvation Music" },
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 12.5.sp
                        ),
                        color = colors.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                if (!trackId.isNullOrBlank()) {
                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            if (isLiked) {
                                LikeCache.remove(trackId)
                                NetworkClient.getApi(context).unlikeTrack(trackId).enqueue(object : retrofit2.Callback<okhttp3.ResponseBody> {
                                    override fun onResponse(call: retrofit2.Call<okhttp3.ResponseBody>, response: retrofit2.Response<okhttp3.ResponseBody>) {}
                                    override fun onFailure(call: retrofit2.Call<okhttp3.ResponseBody>, t: Throwable) {
                                        LikeCache.add(trackId, title = trackTitle, artist = trackArtist)
                                    }
                                })
                            } else {
                                LikeCache.add(trackId, title = trackTitle, artist = trackArtist)
                                NetworkClient.getApi(context).likeTrack(trackId).enqueue(object : retrofit2.Callback<okhttp3.ResponseBody> {
                                    override fun onResponse(call: retrofit2.Call<okhttp3.ResponseBody>, response: retrofit2.Response<okhttp3.ResponseBody>) {}
                                    override fun onFailure(call: retrofit2.Call<okhttp3.ResponseBody>, t: Throwable) {
                                        LikeCache.remove(trackId)
                                    }
                                })
                            }
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = if (isLiked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                            contentDescription = "Лайк",
                            tint = if (isLiked) colors.error else colors.onSurfaceVariant.copy(alpha = 0.7f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // ================= 2. ЦЕНТРАЛЬНЫЙ БЛОК: УПРАВЛЕНИЕ И СЛАЙДЕР (42%) =================
            Column(
                modifier = Modifier
                    .weight(0.42f)
                    .fillMaxHeight(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Кнопки управления
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            mainActivity.getPlayer()?.let { p ->
                                p.shuffleModeEnabled = !p.shuffleModeEnabled
                            }
                        },
                        modifier = Modifier.size(34.dp)
                    ) {
                        val shuffleOn = mainActivity.getPlayer()?.shuffleModeEnabled == true
                        Icon(
                            imageVector = Icons.Rounded.Shuffle,
                            contentDescription = "Перемешать",
                            tint = if (shuffleOn) colors.primary else colors.onSurfaceVariant.copy(alpha = 0.7f),
                            modifier = Modifier.size(19.dp)
                        )
                    }

                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            mainActivity.getPlayer()?.seekToPreviousMediaItem()
                        },
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.SkipPrevious,
                            contentDescription = "Назад",
                            tint = colors.onSurface,
                            modifier = Modifier.size(23.dp)
                        )
                    }

                    FilledIconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            mainActivity.getPlayer()?.let { p ->
                                if (p.isPlaying) p.pause() else p.play()
                            }
                        },
                        modifier = Modifier.size(42.dp),
                        shape = CircleShape,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = colors.primary,
                            contentColor = colors.onPrimary
                        )
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = if (isPlaying) "Пауза" else "Играть",
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            mainActivity.getPlayer()?.seekToNextMediaItem()
                        },
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.SkipNext,
                            contentDescription = "Вперед",
                            tint = colors.onSurface,
                            modifier = Modifier.size(23.dp)
                        )
                    }

                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            mainActivity.getPlayer()?.let { p ->
                                val nextMode = when (p.repeatMode) {
                                    Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                                    Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                                    else -> Player.REPEAT_MODE_OFF
                                }
                                p.repeatMode = nextMode
                            }
                        },
                        modifier = Modifier.size(34.dp)
                    ) {
                        val icon = if (repeatMode == Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat
                        val repeatOn = repeatMode != Player.REPEAT_MODE_OFF
                        Icon(
                            imageVector = icon,
                            contentDescription = "Повтор",
                            tint = if (repeatOn) colors.primary else colors.onSurfaceVariant.copy(alpha = 0.7f),
                            modifier = Modifier.size(19.dp)
                        )
                    }
                }

                // Слайдер и таймеры
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = formatTime(positionMs),
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                        color = colors.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.width(36.dp)
                    )

                    var isDragging by remember { mutableStateOf(false) }
                    var dragFraction by remember { mutableFloatStateOf(0f) }

                    val progress = if (durationMs > 0) {
                        if (isDragging) dragFraction else (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
                    } else 0f

                    SquigglySlider(
                        position = positionMs.toFloat(),
                        duration = durationMs.toFloat(),
                        isPlaying = isPlaying,
                        activeColor = colors.primary,
                        onValueChange = { newPos ->
                            mainActivity.getPlayer()?.seekTo(newPos.toLong())
                        },
                        onValueChangeFinished = {}
                    )

                    Text(
                        text = formatTime(durationMs),
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                        color = colors.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.width(36.dp)
                    )
                }
            }

            // ================= 3. ПРАВЫЙ БЛОК: ТЕКСТ, ОЧЕРЕДЬ, ГРОМКОСТЬ (28%) =================
            Row(
                modifier = Modifier
                    .weight(0.28f)
                    .fillMaxHeight(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End
            ) {
                // Кнопка Текста песни (Lyrics)
                IconButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onToggleLyrics()
                    },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Lyrics,
                        contentDescription = "Текст",
                        tint = if (isLyricsOpen) colors.primary else colors.onSurfaceVariant.copy(alpha = 0.75f),
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Кнопка Очереди (Queue)
                IconButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onToggleQueue()
                    },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.QueueMusic,
                        contentDescription = "Очередь",
                        tint = if (isQueueOpen) colors.primary else colors.onSurfaceVariant.copy(alpha = 0.75f),
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Кнопка Качества звука (Audio Fidelity)
                IconButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        showAudioFidelitySheet = true
                    },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Equalizer,
                        contentDescription = "Аудио тракт",
                        tint = colors.onSurfaceVariant.copy(alpha = 0.75f),
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                // Ползунок громкости
                Icon(
                    imageVector = when {
                        volumeLevel <= 0.01f -> Icons.Rounded.VolumeOff
                        volumeLevel < 0.5f -> Icons.Rounded.VolumeDown
                        else -> Icons.Rounded.VolumeUp
                    },
                    contentDescription = "Громкость",
                    tint = colors.onSurfaceVariant.copy(alpha = 0.75f),
                    modifier = Modifier.size(19.dp)
                )

                Slider(
                    value = volumeLevel,
                    onValueChange = { newVol ->
                        volumeLevel = newVol
                        val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, (newVol * maxVol).toInt(), 0)
                    },
                    modifier = Modifier
                        .width(96.dp)
                        .height(24.dp),
                    colors = SliderDefaults.colors(
                        thumbColor = colors.onSurfaceVariant,
                        activeTrackColor = colors.onSurfaceVariant,
                        inactiveTrackColor = colors.surfaceContainerHighest
                    )
                )

                Spacer(modifier = Modifier.width(6.dp))

                // Кнопка меню 3 точки
                IconButton(
                    onClick = onOpenMenu,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.MoreVert,
                        contentDescription = "Меню",
                        tint = colors.onSurfaceVariant.copy(alpha = 0.75f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

private fun formatTime(millis: Long): String {
    if (millis <= 0) return "0:00"
    val totalSeconds = millis / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}
