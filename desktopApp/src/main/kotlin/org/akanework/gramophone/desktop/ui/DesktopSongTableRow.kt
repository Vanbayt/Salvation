package org.akanework.gramophone.desktop.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
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
import org.akanework.gramophone.desktop.api.Track

@Composable
fun DesktopSongTableHeader(modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "#",
            modifier = Modifier.width(36.dp),
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold, fontSize = 11.sp),
            color = colors.onSurfaceVariant.copy(alpha = 0.6f)
        )
        Text(
            text = "НАЗВАНИЕ",
            modifier = Modifier.weight(3.2f),
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold, fontSize = 11.sp),
            color = colors.onSurfaceVariant.copy(alpha = 0.6f)
        )
        Text(
            text = "ИСПОЛНИТЕЛЬ",
            modifier = Modifier.weight(2.2f),
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold, fontSize = 11.sp),
            color = colors.onSurfaceVariant.copy(alpha = 0.6f)
        )
        Text(
            text = "АЛЬБОМ",
            modifier = Modifier.weight(2.0f),
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold, fontSize = 11.sp),
            color = colors.onSurfaceVariant.copy(alpha = 0.6f)
        )
        Icon(
            imageVector = Icons.Rounded.Schedule,
            contentDescription = "Длительность",
            modifier = Modifier.size(16.dp),
            tint = colors.onSurfaceVariant.copy(alpha = 0.6f)
        )
        Spacer(modifier = Modifier.width(64.dp))
    }
}

@Composable
fun DesktopSongTableRow(
    index: Int,
    track: Track,
    isPlaying: Boolean,
    isActiveTrack: Boolean,
    onClick: () -> Unit,
    onLikeClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val rowBg = if (isActiveTrack) colors.primaryContainer.copy(alpha = 0.35f) else Color.Transparent

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(rowBg)
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 1. Номер
        Box(modifier = Modifier.width(36.dp), contentAlignment = Alignment.CenterStart) {
            if (isActiveTrack && isPlaying) {
                Icon(
                    imageVector = Icons.Rounded.Equalizer,
                    contentDescription = null,
                    tint = colors.primary,
                    modifier = Modifier.size(18.dp)
                )
            } else {
                Text(
                    text = index.toString(),
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = if (isActiveTrack) FontWeight.Bold else FontWeight.Normal,
                        color = if (isActiveTrack) colors.primary else colors.onSurfaceVariant.copy(alpha = 0.8f)
                    )
                )
            }
        }

        // 2. Название + Обложка + FLAC
        Row(
            modifier = Modifier.weight(3.2f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val coverUrl = formatCoverUrl(track.cover)
            AsyncImage(
                model = coverUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(colors.surfaceContainerHighest)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Start
                ) {
                    Text(
                        text = track.title,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = if (isActiveTrack) FontWeight.Bold else FontWeight.Medium,
                            color = if (isActiveTrack) colors.primary else colors.onSurface
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (track.is_lossless) {
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
            }
        }

        // 3. Исполнитель
        Text(
            text = track.artist,
            modifier = Modifier.weight(2.2f).padding(horizontal = 8.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        // 4. Альбом
        Text(
            text = track.album ?: "—",
            modifier = Modifier.weight(2.0f).padding(horizontal = 8.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurfaceVariant.copy(alpha = 0.8f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        // 5. Длительность
        val mins = track.duration / 60
        val secs = track.duration % 60
        Text(
            text = "%d:%02d".format(mins, secs),
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant,
            modifier = Modifier.width(42.dp)
        )

        // 6. Кнопка Лайка (в цвет темы!)
        IconButton(
            onClick = onLikeClick,
            modifier = Modifier.size(36.dp)
        ) {
            Icon(
                imageVector = if (track.is_liked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                contentDescription = "Like",
                tint = if (track.is_liked) colors.primary else colors.onSurfaceVariant.copy(alpha = 0.4f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
