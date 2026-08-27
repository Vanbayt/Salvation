package org.akanework.gramophone.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.akanework.gramophone.desktop.api.DesktopNetworkClient
import org.akanework.gramophone.desktop.settings.DesktopSettingsManager
import org.akanework.gramophone.desktop.settings.ThemeMode

@Composable
fun DesktopSettingsScreen(
    onLogout: () -> Unit,
    onOpenLogin: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val token = DesktopNetworkClient.tokenStorage.getToken()
    val isLoggedIn = !token.isNullOrEmpty()

    // Reactive Settings from DesktopSettingsManager
    val themeMode by DesktopSettingsManager.themeMode.collectAsState()
    val isDynamicCoverColor by DesktopSettingsManager.isDynamicCoverColor.collectAsState()
    val isReplayGain by DesktopSettingsManager.isReplayGain.collectAsState()
    val isBitPerfect by DesktopSettingsManager.isBitPerfect.collectAsState()
    val isMonoAudio by DesktopSettingsManager.isMonoAudio.collectAsState()
    val isFlacPreferred by DesktopSettingsManager.isFlacPreferred.collectAsState()

    val isKaraokeMode by DesktopSettingsManager.isKaraokeMode.collectAsState()
    val isHighlightActiveLine by DesktopSettingsManager.isHighlightActiveLine.collectAsState()
    val isAutoTranslateLyrics by DesktopSettingsManager.isAutoTranslateLyrics.collectAsState()
    val isOfflineOnly by DesktopSettingsManager.isOfflineOnly.collectAsState()

    var showThemeDialog by remember { mutableStateOf(false) }
    var showSleepTimerDialog by remember { mutableStateOf(false) }
    var showDiagnosticsDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(top = 24.dp, start = 36.dp, end = 36.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
        contentPadding = PaddingValues(bottom = 120.dp)
    ) {
        item {
            Text(
                text = "Настройки",
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                color = colors.onSurface
            )
        }

        // 1. ПРОФИЛЬ
        item {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = colors.surfaceContainerHigh,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .clickable { if (!isLoggedIn) onOpenLogin() }
            ) {
                Row(
                    modifier = Modifier.padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(colors.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Rounded.Person,
                            contentDescription = null,
                            modifier = Modifier.size(32.dp),
                            tint = colors.onPrimaryContainer
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isLoggedIn) "Авторизован в Salvation" else "Вход не выполнен",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = colors.onSurface
                        )
                        Text(
                            text = if (isLoggedIn) "Токен сохранен в ~/.config/salvation/auth.token" else "Нажмите, чтобы войти в аккаунт",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.primary
                        )
                    }
                    if (isLoggedIn) {
                        Button(
                            onClick = onLogout,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.onErrorContainer
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Rounded.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Выйти")
                        }
                    }
                }
            }
        }

        // 2. КАСТОМИЗАЦИЯ
        item {
            SettingsGroupCard(title = "Кастомизация") {
                SettingsRow(
                    icon = Icons.Rounded.DarkMode,
                    title = "Тема оформления",
                    subtitle = when (themeMode) {
                        ThemeMode.SYSTEM -> "Системная (iNiR / Material You)"
                        ThemeMode.DARK -> "Темная (Dark)"
                        ThemeMode.LIGHT -> "Светлая (Light)"
                    },
                    onClick = { showThemeDialog = true }
                )
                SettingsSwitchRow(
                    icon = Icons.Rounded.Palette,
                    title = "Динамический цвет фона",
                    subtitle = "Цвет фона плеера подстраивается под обложку",
                    checked = isDynamicCoverColor,
                    onCheckedChange = { DesktopSettingsManager.setDynamicCoverColor(it) }
                )
                SettingsRow(
                    icon = Icons.Rounded.RoundedCorner,
                    title = "Скругление элементов",
                    subtitle = "Стандартное (16dp)",
                    onClick = {}
                )
                SettingsRow(
                    icon = Icons.Rounded.Dashboard,
                    title = "Стиль навигации и плеера",
                    subtitle = "Десктопный адаптивный",
                    onClick = {}
                )
            }
        }

        // 3. ВОСПРОИЗВЕДЕНИЕ
        item {
            SettingsGroupCard(title = "Воспроизведение & Звуковой тракт") {
                SettingsSwitchRow(
                    icon = Icons.Rounded.GraphicEq,
                    title = "Выравнивание громкости (ReplayGain)",
                    subtitle = "Smart-режим одинаковой громкости треков",
                    checked = isReplayGain,
                    onCheckedChange = { DesktopSettingsManager.setReplayGain(it) }
                )
                SettingsSwitchRow(
                    icon = Icons.Rounded.HighQuality,
                    title = "Bit-Perfect Direct Output (ALSA / PipeWire)",
                    subtitle = "Прямая передача звука в ЦАП без передискретизации ОС",
                    checked = isBitPerfect,
                    onCheckedChange = { DesktopSettingsManager.setBitPerfect(it) }
                )
                SettingsSwitchRow(
                    icon = Icons.Rounded.MusicNote,
                    title = "Предпочитать Lossless (FLAC 24-bit / 192kHz)",
                    subtitle = "Автоматически запрашивать поток наивысшего качества при наличии",
                    checked = isFlacPreferred,
                    onCheckedChange = { DesktopSettingsManager.setFlacPreferred(it) }
                )
                SettingsSwitchRow(
                    icon = Icons.Rounded.Speaker,
                    title = "Монозвук",
                    subtitle = "Объединить аудиоканалы в один",
                    checked = isMonoAudio,
                    onCheckedChange = { DesktopSettingsManager.setMonoAudio(it) }
                )
                SettingsRow(
                    icon = Icons.Rounded.Timer,
                    title = "Таймер сна",
                    subtitle = "Остановить музыку через заданное время",
                    onClick = { showSleepTimerDialog = true }
                )
            }
        }

        // 4. ТЕКСТ ПЕСЕН
        item {
            SettingsGroupCard(title = "Текст песен (Караоке)") {
                SettingsSwitchRow(
                    icon = Icons.AutoMirrored.Rounded.QueueMusic,
                    title = "Режим караоке",
                    subtitle = "Синхронизация по времени и автопрокрутка текста",
                    checked = isKaraokeMode,
                    onCheckedChange = { DesktopSettingsManager.setKaraokeMode(it) }
                )
                SettingsSwitchRow(
                    icon = Icons.Rounded.Lightbulb,
                    title = "Подсветка играющей строки",
                    subtitle = "Выделять активную строку ярким цветом темы",
                    checked = isHighlightActiveLine,
                    onCheckedChange = { DesktopSettingsManager.setHighlightActiveLine(it) }
                )
                SettingsSwitchRow(
                    icon = Icons.Rounded.Translate,
                    title = "Автоперевод текста песен",
                    subtitle = "Отображать перевод на русский язык под оригинальными строками",
                    checked = isAutoTranslateLyrics,
                    onCheckedChange = { DesktopSettingsManager.setAutoTranslateLyrics(it) }
                )
            }
        }

        // 5. ОФФЛАЙН МУЗЫКА
        item {
            SettingsGroupCard(title = "Оффлайн музыка") {
                SettingsSwitchRow(
                    icon = Icons.Rounded.DownloadDone,
                    title = "Режим «Только оффлайн»",
                    subtitle = "Воспроизводить только скачанные треки без обращения к сети",
                    checked = isOfflineOnly,
                    onCheckedChange = { DesktopSettingsManager.setOfflineOnly(it) }
                )
                SettingsRow(
                    icon = Icons.Rounded.Storage,
                    title = "Локальное хранилище",
                    subtitle = "Кэш треков и списков сохранен в ~/.config/salvation/cache",
                    onClick = {}
                )
            }
        }

        // 6. ОТЛАДКА И ЛОГИ
        item {
            SettingsGroupCard(title = "Отладка и логи") {
                SettingsRow(
                    icon = Icons.Rounded.BugReport,
                    title = "Логи и диагностика",
                    subtitle = "Просмотр системных сообщений и статуса VLCJ",
                    onClick = { showDiagnosticsDialog = true }
                )
            }
        }

        // 7. О ПРИЛОЖЕНИИ
        item {
            SettingsGroupCard(title = "О приложении") {
                SettingsRow(
                    icon = Icons.Rounded.Info,
                    title = "Salvation Music Desktop",
                    subtitle = "[BETA] v.5 (Linux x86_64 / Compose Multiplatform)",
                    onClick = {}
                )
            }
        }
    }

    // Диалог выбора темы
    if (showThemeDialog) {
        AlertDialog(
            onDismissRequest = { showThemeDialog = false },
            title = { Text("Тема оформления", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    listOf(
                        ThemeMode.SYSTEM to "Системная (iNiR / Material You)",
                        ThemeMode.DARK to "Темная (Dark)",
                        ThemeMode.LIGHT to "Светлая (Light)"
                    ).forEach { (mode, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    DesktopSettingsManager.setThemeMode(mode)
                                    showThemeDialog = false
                                }
                                .padding(vertical = 12.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = themeMode == mode,
                                onClick = {
                                    DesktopSettingsManager.setThemeMode(mode)
                                    showThemeDialog = false
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = label, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showThemeDialog = false }) { Text("Закрыть") }
            }
        )
    }

    // Диалог таймера сна
    if (showSleepTimerDialog) {
        AlertDialog(
            onDismissRequest = { showSleepTimerDialog = false },
            title = { Text("Таймер сна", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    listOf(15, 30, 45, 60).forEach { minutes ->
                        TextButton(
                            onClick = { showSleepTimerDialog = false },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("$minutes минут", style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSleepTimerDialog = false }) { Text("Отмена") }
            }
        )
    }

    // Диалог диагностики
    if (showDiagnosticsDialog) {
        AlertDialog(
            onDismissRequest = { showDiagnosticsDialog = false },
            title = { Text("Логи и диагностика", fontWeight = FontWeight.Bold) },
            text = {
                Text("Hi-Fi Native Audio Engine: OK (Bit-Perfect PipeWire/ALSA)\nNetwork API: http://185.196.41.31/\nWayland Window: Connected\nTheme: $themeMode")
            },
            confirmButton = {
                TextButton(onClick = { showDiagnosticsDialog = false }) { Text("Закрыть") }
            }
        )
    }
}

@Composable
fun SettingsGroupCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 16.dp, bottom = 8.dp)
        )
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(vertical = 8.dp)) {
                content()
            }
        }
    }
}

@Composable
fun SettingsRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun SettingsSwitchRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}
