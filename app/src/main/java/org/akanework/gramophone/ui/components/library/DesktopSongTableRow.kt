package org.akanework.gramophone.ui.components.library

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import org.akanework.gramophone.logic.LikeCache
import org.akanework.gramophone.logic.api.Track
import org.akanework.gramophone.logic.lossless.LosslessStateManager

@Composable
fun DesktopSongTableHeader(modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "#",
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                ),
                color = colors.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.width(36.dp)
            )

            Text(
                text = "НАЗВАНИЕ",
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.5.sp,
                    letterSpacing = 0.8.sp
                ),
                color = colors.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.weight(3.2f)
            )

            Text(
                text = "ИСПОЛНИТЕЛЬ",
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.5.sp,
                    letterSpacing = 0.8.sp
                ),
                color = colors.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.weight(2.4f)
            )

            Text(
                text = "АЛЬБОМ",
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.5.sp,
                    letterSpacing = 0.8.sp
                ),
                color = colors.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.weight(2.4f)
            )

            Row(
                modifier = Modifier.width(60.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.AccessTime,
                    contentDescription = "Длительность",
                    tint = colors.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.size(15.dp)
                )
            }

            Spacer(modifier = Modifier.width(80.dp))
        }

        HorizontalDivider(
            color = colors.outlineVariant.copy(alpha = 0.35f),
            modifier = Modifier.padding(horizontal = 8.dp)
        )
    }
}

@Composable
fun DesktopSongTableRow(
    index: Int,
    track: Track,
    isPlaying: Boolean,
    isCurrentTrack: Boolean,
    onTrackClick: () -> Unit,
    onMoreClick: () -> Unit,
    onLikeClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val isLiked = LikeCache.isLiked(track.id)
    val isLossless = LosslessStateManager.rememberIsTrackLossless(track.id, track.is_lossless)

    val coverUrl = track.cover?.let {
        if (it.startsWith("/")) "http://185.196.41.31$it" else it
    }

    val animatedBg by animateColorAsState(
        targetValue = when {
            isCurrentTrack -> colors.primaryContainer.copy(alpha = 0.65f)
            else -> Color.Transparent
        },
        animationSpec = tween(250),
        label = "rowBg"
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onTrackClick),
        shape = RoundedCornerShape(12.dp),
        color = animatedBg
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 1. Порядковый номер / Эквалайзер воспроизведения
            Box(
                modifier = Modifier.width(36.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                if (isCurrentTrack && isPlaying) {
                    MiniEqualizerIndicator(
                        tint = colors.primary,
                        modifier = Modifier.size(16.dp)
                    )
                } else {
                    Text(
                        text = "$index",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = if (isCurrentTrack) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 13.sp
                        ),
                        color = if (isCurrentTrack) colors.primary else colors.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }

            // 2. Обложка + Название трека + Бейдж FLAC
            Row(
                modifier = Modifier.weight(3.2f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(colors.surfaceContainerHighest),
                    contentAlignment = Alignment.Center
                ) {
                    if (!coverUrl.isNullOrEmpty()) {
                        AsyncImage(
                            model = coverUrl,
                            contentDescription = track.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.MusicNote,
                            contentDescription = null,
                            tint = colors.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = track.title,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = if (isCurrentTrack) FontWeight.Bold else FontWeight.SemiBold,
                                fontSize = 14.sp
                            ),
                            color = if (isCurrentTrack) colors.primary else colors.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )

                        if (isLossless) {
                            AudioQualityBadge(text = "FLAC")
                        }
                    }
                }
            }

            // 3. Артист
            Text(
                text = track.artist,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 13.5.sp,
                    fontWeight = if (isCurrentTrack) FontWeight.Medium else FontWeight.Normal
                ),
                color = if (isCurrentTrack) colors.onSurface else colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(2.4f)
                    .padding(end = 12.dp)
            )

            // 4. Альбом
            Text(
                text = track.album ?: "—",
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Normal
                ),
                color = colors.onSurfaceVariant.copy(alpha = 0.8f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(2.4f)
                    .padding(end = 12.dp)
            )

            // 5. Длительность
            val durationText = formatTrackDuration(track.duration)
            Text(
                text = durationText,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Medium
                ),
                color = colors.onSurfaceVariant.copy(alpha = 0.75f),
                modifier = Modifier
                    .width(60.dp)
                    .wrapContentWidth(Alignment.CenterHorizontally)
            )

            // 6. Быстрые действия (Лайк, Меню)
            Row(
                modifier = Modifier.width(80.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onLikeClick,
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        imageVector = if (isLiked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                        contentDescription = "Лайк",
                        tint = if (isLiked) colors.error else colors.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.size(18.dp)
                    )
                }

                IconButton(
                    onClick = onMoreClick,
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.MoreVert,
                        contentDescription = "Меню",
                        tint = colors.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

private fun formatTrackDuration(seconds: Int): String {
    if (seconds <= 0) return "0:00"
    val mins = seconds / 60
    val secs = seconds % 60
    return "%d:%02d".format(mins, secs)
}
