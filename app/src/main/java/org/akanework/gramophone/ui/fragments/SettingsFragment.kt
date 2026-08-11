package org.akanework.gramophone.ui.fragments

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import org.akanework.gramophone.ui.MainActivity
import java.io.File
import androidx.preference.PreferenceManager

class SettingsFragment : BaseFragment(true) {

    // 🔥 Прячем Compose-навбар и плеер через стейт MainActivity
    override fun onStart() {
        super.onStart()
        (requireActivity() as MainActivity).isGlobalUiVisible = false
    }

    // 🔥 Возвращаем их при закрытии настроек
    override fun onStop() {
        super.onStop()
        (requireActivity() as MainActivity).isGlobalUiVisible = true
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setBackgroundColor(android.graphics.Color.TRANSPARENT)

            setContent {
                val isDark = isSystemInDarkTheme()
                val dynamicColor = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S
                val colors = when {
                    dynamicColor && isDark -> dynamicDarkColorScheme(requireContext())
                    dynamicColor && !isDark -> dynamicLightColorScheme(requireContext())
                    isDark -> darkColorScheme()
                    else -> lightColorScheme()
                }

                MaterialTheme(colorScheme = colors) {
                    SettingsScreen(
                        onBackClick = { requireActivity().onBackPressed() }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBackClick: () -> Unit) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var showSleepTimerDialog by remember { mutableStateOf(false) }
    var showDiagnosticsDialog by remember { mutableStateOf(false) }
    var logsText by remember { mutableStateOf("") }

    // --- ПРОФИЛЬ ---
    val authPrefs = context.getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)
    val username = authPrefs.getString("username", "Гость") ?: "Гость"

    // --- ПОДКЛЮЧАЕМСЯ К ГЛОБАЛЬНЫМ НАСТРОЙКАМ ПЛЕЕРА ---
    val defaultPrefs = remember { PreferenceManager.getDefaultSharedPreferences(context) }

    // ReplayGain (В сервисе "0" = выкл, "3" = smart-режим)
    var isReplayGainEnabled by remember {
        mutableStateOf(defaultPrefs.getString("rg_mode", "0") != "0")
    }

    // Монозвук (Создадим новый ключ)
    var isMonoAudioEnabled by remember {
        mutableStateOf(defaultPrefs.getBoolean("mono_audio", false))
    }

    // Авто-старт (Создадим новый ключ)
    var isResumeAfterCallEnabled by remember {
        mutableStateOf(defaultPrefs.getBoolean("resume_after_call", true))
    }

    // Автоперевод текста песен
    var isLyricsAutoTranslateEnabled by remember {
        mutableStateOf(defaultPrefs.getBoolean("lyrics_auto_translate", true))
    }

    // --- КЭШ ---
    var imageCacheSize by remember { mutableStateOf("Вычисление...") }
    var dataCacheSize by remember { mutableStateOf("Вычисление...") }

    fun calculateCacheSizes() {
        coroutineScope.launch(Dispatchers.IO) {
            val coilCacheDir = File(context.cacheDir, "image_cache")
            val imgSize = if (coilCacheDir.exists()) getDirSize(coilCacheDir) else 0L

            val filesDir = context.filesDir
            val dataSize = if (filesDir.exists()) getDirSize(filesDir) else 0L

            withContext(Dispatchers.Main) {
                imageCacheSize = formatSize(imgSize)
                dataCacheSize = formatSize(dataSize)
            }
        }
    }

    LaunchedEffect(Unit) { calculateCacheSizes() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Настройки", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) { Icon(Icons.Rounded.ArrowBack, "Назад") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(bottom = 120.dp, top = 8.dp)
        ) {
            // 1. ПРОФИЛЬ
            item {
                AnimatedListItem(index = 0) {
                    ProfileCard(
                        username = username, // 🔥 Теперь тут реальный логин
                        onClick = { Toast.makeText(context, "Раздел профиля в разработке", Toast.LENGTH_SHORT).show() }
                    )
                }
            }

            // 2. КАСТОМИЗАЦИЯ
            item {
                AnimatedListItem(index = 1) {
                    SettingsGroupCard(title = "Кастомизация") {
                        SettingsRow(
                            icon = Icons.Rounded.Palette,
                            title = "Тема",
                            subtitle = "Системная",
                            onClick = { Toast.makeText(context, "Выбор темы (в разработке)", Toast.LENGTH_SHORT).show() }
                        )
                        SettingsRow(
                            icon = Icons.Rounded.RoundedCorner,
                            title = "Скругление элементов",
                            subtitle = "Стандартное (16dp)",
                            onClick = { Toast.makeText(context, "Скругления (в разработке)", Toast.LENGTH_SHORT).show() }
                        )
                        SettingsRow(
                            icon = Icons.Rounded.Dashboard,
                            title = "Стиль навигации и плеера",
                            subtitle = "Современный плавающий",
                            onClick = { Toast.makeText(context, "Стиль (в разработке)", Toast.LENGTH_SHORT).show() }
                        )
                    }
                }
            }

            // 3. ВОСПРОИЗВЕДЕНИЕ
            item {
                AnimatedListItem(index = 2) {
                    SettingsGroupCard(title = "Воспроизведение") {
                        SettingsSwitchRow(
                            icon = Icons.Rounded.GraphicEq,
                            title = "Выравнивание громкости",
                            subtitle = "Replay Gain (одинаковая громкость треков)",
                            checked = isReplayGainEnabled,
                            onCheckedChange = { isChecked ->
                                isReplayGainEnabled = isChecked
                                // 🔥 Пишем в настройки, сервис сам подхватит!
                                defaultPrefs.edit().putString("rg_mode", if (isChecked) "3" else "0").apply()
                            }
                        )
                        SettingsSwitchRow(
                            icon = Icons.Rounded.Speaker,
                            title = "Монозвук",
                            subtitle = "Объединить аудиоканалы в один",
                            checked = isMonoAudioEnabled,
                            onCheckedChange = { isChecked ->
                                isMonoAudioEnabled = isChecked
                                defaultPrefs.edit().putBoolean("mono_audio", isChecked).apply()
                                Toast.makeText(context, "Требуется перезапуск трека", Toast.LENGTH_SHORT).show()
                            }
                        )
                        SettingsSwitchRow(
                            icon = Icons.Rounded.PhoneCallback,
                            title = "Авто-старт после звонка",
                            subtitle = "Продолжить воспроизведение автоматически",
                            checked = isResumeAfterCallEnabled,
                            onCheckedChange = { isChecked ->
                                isResumeAfterCallEnabled = isChecked
                                defaultPrefs.edit().putBoolean("resume_after_call", isChecked).apply()
                            }
                        )
                        SettingsRow(
                            icon = Icons.Rounded.Tune,
                            title = "Эквалайзер",
                            subtitle = "Системные настройки звука",
                            onClick = {
                                val player = (context as? org.akanework.gramophone.ui.MainActivity)?.getPlayer()
                                val sessionId = player?.audioSessionId ?: 0

                                val intent = android.content.Intent(android.media.audiofx.AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL).apply {
                                    putExtra(android.media.audiofx.AudioEffect.EXTRA_AUDIO_SESSION, sessionId)
                                    putExtra(android.media.audiofx.AudioEffect.EXTRA_PACKAGE_NAME, context.packageName)
                                    putExtra(android.media.audiofx.AudioEffect.EXTRA_CONTENT_TYPE, android.media.audiofx.AudioEffect.CONTENT_TYPE_MUSIC)
                                }

                                try {
                                    context.startActivity(intent)
                                } catch (e: android.content.ActivityNotFoundException) {
                                    Toast.makeText(context, "На вашем устройстве нет системного эквалайзера", Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                        SettingsRow(
                            icon = Icons.Rounded.Timer,
                            title = "Таймер сна",
                            subtitle = "Остановить музыку через время",
                            onClick = { showSleepTimerDialog = true }
                        )
                    }
                }
            }

            // 4. ТЕКСТ ПЕСЕН
            item {
                AnimatedListItem(index = 3) {
                    SettingsGroupCard(title = "Текст песен") {
                        SettingsSwitchRow(
                            icon = Icons.Rounded.Translate,
                            title = "Автоперевод текста песен",
                            subtitle = "Отображать перевод под оригинальными строками (при наличии)",
                            checked = isLyricsAutoTranslateEnabled,
                            onCheckedChange = { isChecked ->
                                isLyricsAutoTranslateEnabled = isChecked
                                defaultPrefs.edit().putBoolean("lyrics_auto_translate", isChecked).apply()
                            }
                        )
                    }
                }
            }

            // 5. ДАННЫЕ И КЭШ
            item {
                AnimatedListItem(index = 4) {
                    SettingsGroupCard(title = "Данные и кэш") {
                        SettingsRow(
                            icon = Icons.Rounded.Image,
                            title = "Очистить кэш изображений",
                            subtitle = "Занято: $imageCacheSize",
                            onClick = {
                                coroutineScope.launch(Dispatchers.IO) {
                                    context.cacheDir.deleteRecursively()
                                    calculateCacheSizes()
                                    withContext(Dispatchers.Main) { Toast.makeText(context, "Кэш картинок очищен", Toast.LENGTH_SHORT).show() }
                                }
                            }
                        )
                        SettingsRow(
                            icon = Icons.Rounded.LibraryMusic,
                            title = "Очистить медиатеку",
                            subtitle = "Сохраненные JSON. Занято: $dataCacheSize",
                            onClick = {
                                coroutineScope.launch(Dispatchers.IO) {
                                    context.filesDir.listFiles()?.forEach { if (it.name.endsWith(".json")) it.delete() }
                                    calculateCacheSizes()
                                    withContext(Dispatchers.Main) { Toast.makeText(context, "Локальная база сброшена", Toast.LENGTH_SHORT).show() }
                                }
                            }
                        )
                    }
                }
            }

            // 5. ОТЛАДКА И ЛОГИ
            item {
                AnimatedListItem(index = 4) {
                    SettingsGroupCard(title = "Отладка и логи") {
                        SettingsRow(
                            icon = Icons.Rounded.BugReport,
                            title = "Логи и диагностика",
                            subtitle = "Просмотр и копирование логов воспроизведения",
                            onClick = {
                                org.akanework.gramophone.logic.utils.PlaybackLogger.init(context)
                                logsText = org.akanework.gramophone.logic.utils.PlaybackLogger.getLogs()
                                showDiagnosticsDialog = true
                            }
                        )
                    }
                }
            }

            // 6. О ПРИЛОЖЕНИИ
            item {
                AnimatedListItem(index = 5) {
                    SettingsGroupCard(title = "О приложении") {
                        SettingsRow(
                            icon = Icons.Rounded.Info,
                            title = "Salvation Music",
                            subtitle = "[BETA] v.5",
                            onClick = { Toast.makeText(context, "Вы используете новейшую версию", Toast.LENGTH_SHORT).show() }
                        )
                        // 🔥 КНОПКА ВЫХОДА ИЗ АККАУНТА
                        SettingsRow(
                            icon = Icons.Rounded.Logout,
                            title = "Выйти из аккаунта",
                            onClick = {
                                // Стираем токен и логин, перезапускаем приложение
                                authPrefs.edit().clear().apply()
                                org.akanework.gramophone.logic.api.AuthManager.clearToken(context)

                                val intent = android.content.Intent(context, org.akanework.gramophone.ui.LoginActivity::class.java)
                                intent.flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK
                                context.startActivity(intent)
                            }
                        )
                    }
                }
            }
        }
    }

    // 🔥 ДИАЛОГ ТАЙМЕРА СНА
    if (showSleepTimerDialog) {
        AlertDialog(
            onDismissRequest = { showSleepTimerDialog = false },
            title = { Text("Таймер сна", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    val options = listOf(15, 30, 45, 60)
                    options.forEach { minutes ->
                        TextButton(
                            onClick = {
                                showSleepTimerDialog = false
                                coroutineScope.launch {
                                    Toast.makeText(context, "Музыка остановится через $minutes мин.", Toast.LENGTH_SHORT).show()
                                    delay(minutes * 60 * 1000L)
                                    val player = (context as? org.akanework.gramophone.ui.MainActivity)?.getPlayer()
                                    player?.pause()
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("$minutes минут", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSleepTimerDialog = false }) { Text("Отмена") }
            },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    }

    // 🔥 ДИАЛОГ ЛОГОВ И ДИАГНОСТИКИ
    if (showDiagnosticsDialog) {
        AlertDialog(
            onDismissRequest = { showDiagnosticsDialog = false },
            title = { Text("Логи воспроизведения", fontWeight = FontWeight.Bold) },
            text = {
                val scrollState = rememberScrollState()
                Box(
                    modifier = Modifier
                        .heightIn(max = 400.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(8.dp)
                ) {
                    Text(
                        text = if (logsText.isNotEmpty()) logsText else "Логи пока пусты.",
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        fontSize = androidx.compose.ui.unit.TextUnit(11f, androidx.compose.ui.unit.TextUnitType.Sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(scrollState)
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    val clip = android.content.ClipData.newPlainText("Salvation Logs", logsText)
                    clipboard.setPrimaryClip(clip)
                    Toast.makeText(context, "Логи скопированы в буфер", Toast.LENGTH_SHORT).show()
                }) {
                    Text("Скопировать")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    org.akanework.gramophone.logic.utils.PlaybackLogger.clearLogs()
                    logsText = "Логи очищены."
                    Toast.makeText(context, "Логи очищены", Toast.LENGTH_SHORT).show()
                }) {
                    Text("Очистить")
                }
            },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    }
}

fun getDirSize(dir: File): Long {
    var size = 0L
    val files = dir.listFiles()
    if (files != null) {
        for (file in files) {
            size += if (file.isDirectory) getDirSize(file) else file.length()
        }
    }
    return size
}

fun formatSize(sizeBytes: Long): String {
    if (sizeBytes <= 0) return "0 Б"
    val units = arrayOf("Б", "КБ", "МБ", "ГБ")
    val digitGroups = (Math.log10(sizeBytes.toDouble()) / Math.log10(1024.0)).toInt()
    return String.format("%.1f %s", sizeBytes / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
}

@Composable
fun ProfileCard(username: String, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Rounded.Person,
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = username,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Перейти в профиль",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Icon(
                Icons.Rounded.ChevronRight,
                contentDescription = "Перейти",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
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
            color = MaterialTheme.colorScheme.surfaceContainer,
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

@Composable
fun AnimatedListItem(index: Int, content: @Composable () -> Unit) {
    val offsetY = remember { Animatable(100f) }
    val alpha = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        delay((index * 50).toLong().coerceAtMost(300L))
        launch { alpha.animateTo(1f, tween(300)) }
        launch { offsetY.animateTo(0f, tween(durationMillis = 400, easing = FastOutSlowInEasing)) }
    }

    Box(
        modifier = Modifier.graphicsLayer {
            translationY = offsetY.value
            this.alpha = alpha.value
        }
    ) {
        content()
    }
}