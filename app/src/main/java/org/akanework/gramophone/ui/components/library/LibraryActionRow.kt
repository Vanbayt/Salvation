package org.akanework.gramophone.ui.components.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.preference.PreferenceManager
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
    val context = LocalContext.current
    val prefs = remember(context) { PreferenceManager.getDefaultSharedPreferences(context) }

    var isMyMixActive by remember {
        mutableStateOf(prefs.getBoolean("library_action_is_my_mix", false))
    }
    var showMenu by remember { mutableStateOf(false) }

    val mainContainerColor = if (isMyMixActive) colors.primaryContainer else colors.tertiaryContainer
    val mainContentColor = if (isMyMixActive) colors.onPrimaryContainer else colors.onTertiaryContainer

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Левая часть: Сплит-кнопка воспроизведения [ Действие (START) | Стрелка (END) ]
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            // Основная кнопка действия (Перемешать или Мой микс)
            PillSegmentButton(
                onClick = {
                    if (isMyMixActive) {
                        onMyMixClick()
                    } else {
                        onShuffleClick()
                    }
                },
                position = PillPosition.START,
                containerColor = mainContainerColor,
                contentColor = mainContentColor,
                width = null,
                height = 38.dp,
                cornerRadius = 19.dp,
                innerRadius = 4.dp
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(horizontal = 12.dp)
                ) {
                    if (isMyMixActive) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_widget_sparkle),
                            contentDescription = "Мой микс",
                            modifier = Modifier.size(17.dp)
                        )
                        Text(
                            text = "Мой микс",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.5.sp
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.Shuffle,
                            contentDescription = "Перемешать",
                            modifier = Modifier.size(17.dp)
                        )
                        Text(
                            text = "Перемешать",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.5.sp
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // Мини-кнопка со стрелкой для вызова меню выбора режима
            Box {
                PillSegmentButton(
                    onClick = { showMenu = true },
                    position = PillPosition.END,
                    containerColor = mainContainerColor,
                    contentColor = mainContentColor,
                    width = 28.dp,
                    height = 38.dp,
                    cornerRadius = 19.dp,
                    innerRadius = 4.dp
                ) {
                    Icon(
                        imageVector = Icons.Rounded.KeyboardArrowDown,
                        contentDescription = "Выбор режима воспроизведения",
                        modifier = Modifier.size(18.dp)
                    )
                }

                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false },
                    shape = RoundedCornerShape(18.dp),
                    containerColor = colors.surfaceContainerHigh
                ) {
                    DropdownMenuItem(
                        text = {
                            Text(
                                "Обычное перемешивание",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = if (!isMyMixActive) FontWeight.Bold else FontWeight.Normal
                                )
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Rounded.Shuffle,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                        },
                        trailingIcon = {
                            if (!isMyMixActive) {
                                Icon(
                                    imageVector = Icons.Rounded.Check,
                                    contentDescription = null,
                                    tint = colors.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        },
                        onClick = {
                            isMyMixActive = false
                            prefs.edit().putBoolean("library_action_is_my_mix", false).apply()
                            showMenu = false
                        }
                    )

                    DropdownMenuItem(
                        text = {
                            Text(
                                "Мой микс",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = if (isMyMixActive) FontWeight.Bold else FontWeight.Normal
                                )
                            )
                        },
                        leadingIcon = {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_widget_sparkle),
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                        },
                        trailingIcon = {
                            if (isMyMixActive) {
                                Icon(
                                    imageVector = Icons.Rounded.Check,
                                    contentDescription = null,
                                    tint = colors.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        },
                        onClick = {
                            isMyMixActive = true
                            prefs.edit().putBoolean("library_action_is_my_mix", true).apply()
                            showMenu = false
                        }
                    )
                }
            }
        }

        // Центральная часть: компактный бейдж количества треков
        if (itemCountLabel != null && itemCount > 0) {
            Surface(
                shape = CircleShape,
                color = colors.surfaceContainerHighest.copy(alpha = 0.55f),
                modifier = Modifier.padding(horizontal = 4.dp)
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
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        } else {
            Spacer(modifier = Modifier.width(4.dp))
        }

        Spacer(modifier = Modifier.weight(1f, fill = false))

        // Правая часть: Сегментированная группа инструментов [ Locate | Sort | Download ]
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
                    width = 38.dp,
                    height = 38.dp,
                    cornerRadius = 19.dp,
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
                width = 38.dp,
                height = 38.dp,
                cornerRadius = 19.dp,
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
                    width = 38.dp,
                    height = 38.dp,
                    cornerRadius = 19.dp,
                    innerRadius = 4.dp
                ) {
                    when {
                        isDownloading -> {
                            Box(contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(
                                    progress = { downloadPercent / 100f },
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
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
                                painter = painterResource(R.drawable.ic_check_circle),
                                contentDescription = "Медиатека скачана",
                                tint = colors.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        else -> {
                            Icon(
                                painter = painterResource(R.drawable.ic_download),
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
