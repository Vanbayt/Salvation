package org.akanework.gramophone.ui.components.library

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.res.painterResource
import org.akanework.gramophone.R
import org.akanework.gramophone.ui.components.PillPosition
import org.akanework.gramophone.ui.components.PillSegmentButton

@Composable
fun LibraryActionRow(
    onShuffleClick: () -> Unit,
    onMyMixClick: () -> Unit = {},
    onSortClick: () -> Unit,
    onLocateClick: () -> Unit = {},
    showLocateButton: Boolean = false,
    itemCount: Int = 0,
    itemCountLabel: String? = null,
    isFilterActive: Boolean = false,
    onDownloadClick: (() -> Unit)? = null,
    isDownloading: Boolean = false,
    isAllDownloaded: Boolean = false,
    downloadPercent: Float = 0f,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme

    androidx.compose.foundation.layout.Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 2.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Ряд 1: Основные действия воспроизведения [ Перемешать | Мой микс ]
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PillSegmentButton(
                onClick = onShuffleClick,
                position = PillPosition.START,
                containerColor = colors.tertiaryContainer,
                contentColor = colors.onTertiaryContainer,
                modifier = Modifier.weight(1f),
                width = null,
                height = 42.dp,
                cornerRadius = 21.dp,
                innerRadius = 5.dp
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Shuffle,
                        contentDescription = "Shuffle Play",
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "Перемешать",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            PillSegmentButton(
                onClick = onMyMixClick,
                position = PillPosition.END,
                containerColor = colors.primaryContainer,
                contentColor = colors.onPrimaryContainer,
                modifier = Modifier.weight(1f),
                width = null,
                height = 42.dp,
                cornerRadius = 21.dp,
                innerRadius = 5.dp
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_widget_sparkle),
                        contentDescription = "Мой микс",
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "Мой микс",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        // Ряд 2: Счетчик треков слева и инструменты (Локация, Сортировка, Скачивание) справа
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Левая часть: Количество треков
            if (itemCountLabel != null && itemCount > 0) {
                Surface(
                    shape = CircleShape,
                    color = colors.surfaceContainerHighest.copy(alpha = 0.55f)
                ) {
                    Text(
                        text = itemCountLabel,
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp
                        ),
                        color = colors.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
            } else {
                Spacer(modifier = Modifier.width(1.dp))
            }

            // Правая часть: Сегментированная группа инструментов
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                val hasLocate = showLocateButton
                val hasDownload = onDownloadClick != null

                // 1. Кнопка быстрого перехода к играющему треку (Найти трек)
                if (hasLocate) {
                    PillSegmentButton(
                        onClick = onLocateClick,
                        position = PillPosition.START,
                        containerColor = colors.surfaceContainerHigh,
                        contentColor = colors.primary,
                        width = 44.dp,
                        height = 36.dp,
                        cornerRadius = 18.dp,
                        innerRadius = 4.dp
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.MyLocation,
                            contentDescription = "Locate Playing Track",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // 2. Кнопка параметров сортировки и фильтрации (Фильтрация)
                val sortPos = when {
                    hasLocate && hasDownload -> PillPosition.MIDDLE
                    hasLocate && !hasDownload -> PillPosition.END
                    !hasLocate && hasDownload -> PillPosition.START
                    else -> PillPosition.STANDALONE
                }
                PillSegmentButton(
                    onClick = onSortClick,
                    position = sortPos,
                    containerColor = if (isFilterActive) colors.primaryContainer else colors.surfaceContainerHigh,
                    contentColor = if (isFilterActive) colors.primary else colors.onSurfaceVariant,
                    width = 44.dp,
                    height = 36.dp,
                    cornerRadius = 18.dp,
                    innerRadius = 4.dp
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.Sort,
                        contentDescription = "Sort Options",
                        modifier = Modifier.size(18.dp)
                    )
                }

                // 3. Кнопка скачивания медиатеки (Скачивание треков)
                if (onDownloadClick != null) {
                    PillSegmentButton(
                        onClick = onDownloadClick,
                        position = PillPosition.END,
                        containerColor = if (isAllDownloaded) colors.primaryContainer else colors.surfaceContainerHigh,
                        contentColor = if (isAllDownloaded) colors.primary else colors.onSurfaceVariant,
                        width = 44.dp,
                        height = 36.dp,
                        cornerRadius = 18.dp,
                        innerRadius = 4.dp
                    ) {
                        when {
                            isDownloading -> {
                                Box(contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator(
                                        progress = { downloadPercent / 100f },
                                        modifier = Modifier.size(22.dp),
                                        strokeWidth = 2.5.dp,
                                        color = colors.primary
                                    )
                                    Text(
                                        text = "${downloadPercent.toInt()}%",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontSize = 7.sp,
                                            fontWeight = FontWeight.Black
                                        ),
                                        color = colors.primary
                                    )
                                }
                            }
                            isAllDownloaded -> {
                                Icon(
                                    painter = androidx.compose.ui.res.painterResource(org.akanework.gramophone.R.drawable.ic_check_circle),
                                    contentDescription = "Медиатека скачана",
                                    tint = colors.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            else -> {
                                Icon(
                                    painter = androidx.compose.ui.res.painterResource(org.akanework.gramophone.R.drawable.ic_download),
                                    contentDescription = "Скачать медиатеку",
                                    tint = colors.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
