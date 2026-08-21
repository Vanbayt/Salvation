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

@Composable
fun LibraryActionRow(
    onShuffleClick: () -> Unit,
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

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Левая группа: Кнопка Shuffle и Счетчик
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.weight(1f, fill = false)
        ) {
            FilledTonalButton(
                onClick = onShuffleClick,
                shape = CircleShape,
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = colors.tertiaryContainer,
                    contentColor = colors.onTertiaryContainer
                ),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                modifier = Modifier.height(38.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Shuffle,
                    contentDescription = "Shuffle Play",
                    modifier = Modifier.size(17.dp)
                )
                Spacer(modifier = Modifier.width(5.dp))
                Text(
                    text = "Перемешать",
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.5.sp
                    ),
                    maxLines = 1
                )
            }

            if (itemCountLabel != null && itemCount > 0) {
                Surface(
                    shape = CircleShape,
                    color = colors.surfaceContainerHighest.copy(alpha = 0.55f)
                ) {
                    Text(
                        text = itemCountLabel,
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 11.5.sp
                        ),
                        color = colors.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp)
                    )
                }
            }
        }

        // Правая группа: Кнопка сортировки, прыжка к треку и скачивания
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Кнопка быстрого перехода к играющему треку
            AnimatedVisibility(
                visible = showLocateButton,
                enter = fadeIn() + scaleIn(),
                exit = fadeOut() + scaleOut()
            ) {
                FilledTonalIconButton(
                    onClick = onLocateClick,
                    shape = CircleShape,
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = colors.primaryContainer,
                        contentColor = colors.onPrimaryContainer
                    ),
                    modifier = Modifier.size(38.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.MyLocation,
                        contentDescription = "Locate Playing Track",
                        modifier = Modifier.size(19.dp)
                    )
                }
            }

            // Кнопка открытия параметров сортировки
            FilledTonalIconButton(
                onClick = onSortClick,
                shape = CircleShape,
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = if (isFilterActive) colors.primaryContainer else colors.surfaceContainerHigh,
                    contentColor = if (isFilterActive) colors.primary else colors.onSurfaceVariant
                ),
                modifier = Modifier.size(38.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.Sort,
                    contentDescription = "Sort Options",
                    modifier = Modifier.size(19.dp)
                )
            }

            // Кнопка скачивания медиатеки (FLAC-style индикатор / Загрузка)
            if (onDownloadClick != null) {
                FilledTonalIconButton(
                    onClick = onDownloadClick,
                    shape = CircleShape,
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = if (isAllDownloaded) colors.primaryContainer else colors.surfaceContainerHigh,
                        contentColor = if (isAllDownloaded) colors.primary else colors.onSurfaceVariant
                    ),
                    modifier = Modifier.size(38.dp)
                ) {
                    when {
                        isDownloading -> {
                            Box(contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(
                                    progress = { downloadPercent / 100f },
                                    modifier = Modifier.size(26.dp),
                                    strokeWidth = 2.5.dp,
                                    color = colors.primary
                                )
                                Text(
                                    text = "${downloadPercent.toInt()}%",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 7.5.sp,
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
                                modifier = Modifier.size(20.dp)
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
