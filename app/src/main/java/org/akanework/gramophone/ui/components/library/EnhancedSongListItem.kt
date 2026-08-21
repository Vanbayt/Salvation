package org.akanework.gramophone.ui.components.library

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import org.akanework.gramophone.logic.api.Track

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun EnhancedSongListItem(
    track: Track,
    isPlaying: Boolean,
    isCurrentTrack: Boolean = false,
    isSelected: Boolean = false,
    isSelectionMode: Boolean = false,
    selectionIndex: Int? = null,
    showCover: Boolean = true,
    coverSize: Dp = 52.dp,
    onTrackClick: () -> Unit,
    onLongPress: () -> Unit = {},
    onMoreClick: () -> Unit = {},
    onLikeClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    // 1. Анимированный морфинг карточки: 16.dp -> 50.dp (capsule-pill) при воспроизведении
    val animatedCornerRadius by animateDpAsState(
        targetValue = if (isCurrentTrack) 50.dp else 16.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "itemCornerRadius"
    )

    // 2. Анимированный морфинг обложки: 12.dp -> 50.dp (круг) при воспроизведении
    val animatedCoverRadius by animateDpAsState(
        targetValue = if (isCurrentTrack) 50.dp else 12.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "coverCornerRadius"
    )

    // 3. Пружинящий микро-масштаб при выборе в Multi-Select режиме
    val itemScale by animateFloatAsState(
        targetValue = if (isSelected) 0.97f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "itemScale"
    )

    val colors = MaterialTheme.colorScheme
    val targetContainerColor = when {
        isSelected -> colors.secondaryContainer
        isCurrentTrack -> colors.primaryContainer
        else -> colors.surfaceContainerHighest.copy(alpha = 0.5f)
    }

    val animatedContainerColor by animateColorAsState(
        targetValue = targetContainerColor,
        animationSpec = tween(durationMillis = 250),
        label = "containerColor"
    )

    val targetContentColor = when {
        isSelected -> colors.onSecondaryContainer
        isCurrentTrack -> colors.onPrimaryContainer
        else -> colors.onSurface
    }

    val itemShape = remember(animatedCornerRadius) { RoundedCornerShape(animatedCornerRadius) }
    val coverShape = remember(animatedCoverRadius) { RoundedCornerShape(animatedCoverRadius) }

    val borderModifier = if (isSelected) {
        Modifier.border(width = 2.dp, color = colors.primary, shape = itemShape)
    } else {
        Modifier
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .scale(itemScale)
            .then(borderModifier)
            .clip(itemShape)
            .combinedClickable(
                onClick = onTrackClick,
                onLongClick = onLongPress
            ),
        shape = itemShape,
        color = animatedContainerColor
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Обложка трека / Индикатор Multi-Select
            if (isSelectionMode && isSelected) {
                Box(
                    modifier = Modifier
                        .size(coverSize)
                        .clip(CircleShape)
                        .background(colors.primary),
                    contentAlignment = Alignment.Center
                ) {
                    if (selectionIndex != null && selectionIndex > 0) {
                        Text(
                            text = selectionIndex.toString(),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = colors.onPrimary
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.Check,
                            contentDescription = "Selected",
                            tint = colors.onPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            } else if (showCover) {
                Box(
                    modifier = Modifier
                        .size(coverSize)
                        .clip(coverShape)
                        .background(colors.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    val coverUrl = track.cover?.let {
                        if (it.startsWith("/")) "http://185.196.41.31$it" else it
                    }

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
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    // Анимированные волны эквалайзера рендерятся ТОЛЬКО если трек активен и играет!
                    if (isCurrentTrack && isPlaying) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.45f)),
                            contentAlignment = Alignment.Center
                        ) {
                            MiniEqualizerIndicator(
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            // Текстовая информация о треке
            val isLossless = org.akanework.gramophone.logic.lossless.LosslessStateManager.rememberIsTrackLossless(track.id, track.is_lossless)

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = track.title,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = if (isCurrentTrack) FontWeight.Bold else FontWeight.SemiBold,
                            fontSize = 15.sp
                        ),
                        color = targetContentColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )

                    // Бейдж качества аудио (Lossless / FLAC)
                    if (isLossless) {
                        AudioQualityBadge(text = "FLAC")
                    }
                }

                Spacer(modifier = Modifier.height(3.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = track.artist,
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                        color = if (isCurrentTrack) targetContentColor.copy(alpha = 0.8f) else colors.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    if (!track.album.isNullOrBlank()) {
                        Text(
                            text = "•",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                        Text(
                            text = track.album,
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                            color = colors.onSurfaceVariant.copy(alpha = 0.75f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // Кнопка Лайка (опционально)
            if (onLikeClick != null) {
                IconButton(
                    onClick = onLikeClick,
                    modifier = Modifier.size(38.dp)
                ) {
                    Icon(
                        imageVector = if (track.is_liked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                        contentDescription = "Like",
                        tint = if (track.is_liked) colors.primary else colors.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // Кнопка контекстного меню
            IconButton(
                onClick = onMoreClick,
                modifier = Modifier.size(38.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.MoreVert,
                    contentDescription = "More Options",
                    tint = if (isCurrentTrack) targetContentColor.copy(alpha = 0.85f) else colors.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
fun AudioQualityBadge(
    text: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(4.dp),
        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.85f)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold
            ),
            color = MaterialTheme.colorScheme.onTertiaryContainer,
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
        )
    }
}

@Composable
fun MiniEqualizerIndicator(
    tint: Color = MaterialTheme.colorScheme.primary,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "equalizer")

    val bar1Height by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(450, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bar1"
    )

    val bar2Height by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 0.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(380, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bar2"
    )

    val bar3Height by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(520, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bar3"
    )

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(2.5.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .fillMaxHeight(bar1Height)
                .clip(RoundedCornerShape(2.dp))
                .background(tint)
        )
        Box(
            modifier = Modifier
                .width(3.dp)
                .fillMaxHeight(bar2Height)
                .clip(RoundedCornerShape(2.dp))
                .background(tint)
        )
        Box(
            modifier = Modifier
                .width(3.dp)
                .fillMaxHeight(bar3Height)
                .clip(RoundedCornerShape(2.dp))
                .background(tint)
        )
    }
}
