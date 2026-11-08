package org.akanework.gramophone.ui.fragments.stats

import android.os.Bundle
import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.akanework.gramophone.R
import org.akanework.gramophone.logic.api.AuthManager
import org.akanework.gramophone.logic.api.NetworkClient
import org.akanework.gramophone.logic.stats.EraStat
import org.akanework.gramophone.logic.stats.PlaybackStatsDatabase
import org.akanework.gramophone.logic.stats.StatsSummary
import org.akanework.gramophone.logic.stats.TopAlbumStat
import org.akanework.gramophone.logic.stats.TopArtistStat
import org.akanework.gramophone.logic.stats.TopTrackStat
import org.akanework.gramophone.ui.MainActivity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    onBackPressed: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? MainActivity
    val coroutineScope = rememberCoroutineScope()

    var selectedPeriod by remember { mutableStateOf("all") }
    var statsSummary by remember { mutableStateOf<StatsSummary?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    fun loadStats(period: String = selectedPeriod) {
        isLoading = true
        coroutineScope.launch(Dispatchers.IO) {
            val token = AuthManager.getToken(context)
            val userId = if (!token.isNullOrBlank()) "user_" + token.hashCode().toString() else "local_user"

            // Сбрасываем время текущего трека до миллисекунды
            org.akanework.gramophone.logic.stats.StatsTracker.checkpoint(context)

            // 1. Быстрая локальная загрузка из базы
            val db = PlaybackStatsDatabase.getInstance(context)
            val localSummary = db.getStatsSummary(userId, period)

            withContext(Dispatchers.Main) {
                if (selectedPeriod == period) {
                    statsSummary = localSummary
                    isLoading = false
                }
            }

            // 2. Фоновое обновление из Go-бэкенда при наличии сети
            try {
                val api = NetworkClient.getApi(context)
                val response = api.getStatsSummary(period).execute()
                if (response.isSuccessful) {
                    val body = response.body()
                    if (body != null && selectedPeriod == period) {
                        val useServerDist = (body.totalPlaysCount >= localSummary.totalPlaysCount) && !body.dayDistribution.isNullOrEmpty()
                        val mergedSummary = StatsSummary(
                            period = period,
                            totalListenedMs = maxOf(localSummary.totalListenedMs, body.totalListenedMs),
                            totalPlaysCount = maxOf(localSummary.totalPlaysCount, body.totalPlaysCount),
                            uniqueTracksCount = maxOf(localSummary.uniqueTracksCount, body.uniqueTracksCount),
                            uniqueArtistsCount = maxOf(localSummary.uniqueArtistsCount, body.uniqueArtistsCount),
                            uniqueAlbumsCount = maxOf(localSummary.uniqueAlbumsCount, body.uniqueAlbumsCount),
                            topTracks = if (body.topTracks.isNotEmpty()) body.topTracks.map {
                                TopTrackStat(it.trackId, it.title, it.artist, it.album, it.coverUrl, it.playCount, it.totalListenedMs)
                            } else localSummary.topTracks,
                            topArtists = if (body.topArtists.isNotEmpty()) body.topArtists.map {
                                TopArtistStat(it.artist, it.coverUrl, it.playCount, it.totalListenedMs)
                            } else localSummary.topArtists,
                            topAlbums = if (body.topAlbums.isNotEmpty()) body.topAlbums.map {
                                TopAlbumStat(it.album, it.artist, it.coverUrl, it.playCount, it.totalListenedMs)
                            } else localSummary.topAlbums,
                            eras = if (body.eras.isNotEmpty()) body.eras.map {
                                EraStat(it.era, it.count, it.ratio)
                            } else localSummary.eras,
                            streakDays = localSummary.streakDays,
                            peakHour = if (body.totalPlaysCount > localSummary.totalPlaysCount) body.peakHour else localSummary.peakHour,
                            favoriteDayOfWeek = if (body.totalPlaysCount > localSummary.totalPlaysCount) body.favoriteDayOfWeek else localSummary.favoriteDayOfWeek,
                            dayDistribution = if (useServerDist) body.dayDistribution else localSummary.dayDistribution,
                            hourlyDistribution = if (useServerDist && !body.hourlyDistribution.isNullOrEmpty()) body.hourlyDistribution else localSummary.hourlyDistribution,
                            totalRewinds = maxOf(localSummary.totalRewinds, body.totalRewinds),
                            totalRepeats = maxOf(localSummary.totalRepeats, body.totalRepeats),
                            earlySkipsCount = maxOf(localSummary.earlySkipsCount, body.earlySkipsCount),
                            longTracksCount = maxOf(localSummary.longTracksCount, body.longTracksCount)
                        )
                        withContext(Dispatchers.Main) {
                            statsSummary = mergedSummary
                        }
                    }
                }
            } catch (_: Exception) {}
        }
    }

    LaunchedEffect(Unit) {
        loadStats("all")
    }

    fun playTrack(track: TopTrackStat) {
        val player = activity?.getPlayer()
        if (player == null) {
            Toast.makeText(context, "Плеер недоступен", Toast.LENGTH_SHORT).show()
            return
        }
        val streamUrl = "http://185.196.41.31/stream/${track.trackId}"
        val coverUri = track.coverUrl?.let {
            (if (it.startsWith("/")) "http://185.196.41.31$it" else it).toUri()
        }

        val mediaItem = MediaItem.Builder()
            .setMediaId(track.trackId)
            .setUri(streamUrl.toUri())
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(track.title)
                    .setArtist(track.artist)
                    .setAlbumTitle(track.album)
                    .setArtworkUri(coverUri)
                    .setExtras(Bundle().apply {
                        putString("PLAYING_FROM", "Статистика: Топ-треки")
                    })
                    .build()
            )
            .build()

        player.setMediaItem(mediaItem, 0)
        player.prepare()
        player.play()
        Toast.makeText(context, "Воспроизведение: ${track.title}", Toast.LENGTH_SHORT).show()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Статистика",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 24.sp
                        )
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackPressed) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    IconButton(onClick = { loadStats(selectedPeriod) }) {
                        Icon(Icons.Rounded.Refresh, contentDescription = "Обновить")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.surface
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(top = 8.dp, bottom = 220.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 0. Селектор периодов (Filter Chips)
            item(key = "stats_period_selector") {
                val periods = listOf(
                    "week" to "Неделя",
                    "month" to "Месяц",
                    "year" to "Год",
                    "all" to "Всё время"
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    periods.forEach { (key, label) ->
                        val isSelected = selectedPeriod == key
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primary
                                   else MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(16.dp))
                                .clickable {
                                    if (selectedPeriod != key) {
                                        selectedPeriod = key
                                        loadStats(key)
                                    }
                                }
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.padding(vertical = 10.dp)
                            ) {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                    ),
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary
                                           else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // 1. Главная Hero Bento-карточка (PixelPlayer Hero Widget)
            item(key = "stats_hero_card") {
                val summary = statsSummary
                val totalMs = summary?.totalListenedMs ?: 0L
                val totalMinutes = totalMs / (1000 * 60)
                val hours = totalMinutes / 60
                val mins = totalMinutes % 60

                Surface(
                    shape = RoundedCornerShape(28.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.verticalGradient(
                                    listOf(
                                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                                        MaterialTheme.colorScheme.surfaceContainerHigh
                                    )
                                )
                            )
                            .padding(22.dp)
                    ) {
                        Column {
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(42.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            painter = painterResource(id = R.drawable.ic_headphones),
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onPrimary,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(14.dp))

                                Column {
                                    Text(
                                        text = "ВРЕМЯ ПРОСЛУШИВАНИЯ",
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            fontWeight = FontWeight.Black,
                                            letterSpacing = 1.sp
                                        ),
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = if (hours > 0) "$hours ч. $mins мин." else "$mins мин.",
                                        style = MaterialTheme.typography.headlineMedium.copy(
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 30.sp
                                        ),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(18.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.7f),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = Icons.Rounded.MusicNote,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = "${summary?.uniqueTracksCount ?: 0}",
                                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = "Треков",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.7f),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = Icons.Rounded.Person,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = "${summary?.uniqueArtistsCount ?: 0}",
                                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = "Артистов",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.7f),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = Icons.Rounded.Album,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = "${summary?.uniqueAlbumsCount ?: 0}",
                                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = "Альбомов",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 2. Парные Bento-плитки: Серия дней + Пик активности
            item(key = "stats_paired_bento_tiles") {
                val streak = statsSummary?.streakDays ?: 1
                val peakH = statsSummary?.peakHour ?: 12
                val peakSubtext = when (peakH) {
                    in 6..11 -> "Утренний заряд"
                    in 12..16 -> "Дневной пик"
                    in 17..22 -> "Вечерний релакс"
                    else -> "Ночной эфир"
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Плитка: Серия дней
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(18.dp)) {
                            Surface(
                                shape = CircleShape,
                                color = Color(0xFFFF9800).copy(alpha = 0.15f),
                                modifier = Modifier.size(38.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Rounded.LocalFireDepartment,
                                        contentDescription = null,
                                        tint = Color(0xFFFF9800),
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "СЕРИЯ ДНЕЙ",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 0.8.sp
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "$streak ${getDaysString(streak).uppercase()}",
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Подряд без пропусков",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Плитка: Пик активности
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(18.dp)) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.size(38.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Rounded.AccessTime,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "ПИК АКТИВНОСТИ",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 0.8.sp
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "$peakH:00 – ${(peakH + 2) % 24}:00",
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = peakSubtext,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // 2.5 Поведенческая статистика (На репите, Перемотки, Эпики)
            item(key = "stats_behavior_bento") {
                val rewinds = statsSummary?.totalRewinds ?: 0L
                val repeats = statsSummary?.totalRepeats ?: 0L
                val epicTracks = statsSummary?.longTracksCount ?: 0L

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // 1. На репите
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Rounded.Repeat,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "НА РЕПИТЕ",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 0.4.sp,
                                    fontSize = 10.sp
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "$repeats",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Кругов",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // 2. Перемотки назад
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Surface(
                                shape = CircleShape,
                                color = Color(0xFFFF9800).copy(alpha = 0.15f),
                                modifier = Modifier.size(32.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Rounded.SkipPrevious,
                                        contentDescription = null,
                                        tint = Color(0xFFFF9800),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "ПЕРЕМОТКИ",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 0.4.sp,
                                    fontSize = 10.sp
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "$rewinds",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Возвратов",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // 3. Эпические треки
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Surface(
                                shape = CircleShape,
                                color = Color(0xFF00E676).copy(alpha = 0.15f),
                                modifier = Modifier.size(32.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Rounded.Timer,
                                        contentDescription = null,
                                        tint = Color(0xFF00E676),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "ЭПИКИ 7+ М",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 0.4.sp,
                                    fontSize = 10.sp
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "$epicTracks",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Треков",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // 3. Индикатор дней недели (PixelPlayer Days Bar Chart)
            item(key = "stats_days_bar_chart") {
                val dayNames = listOf("ПН", "ВТ", "СР", "ЧТ", "ПТ", "СБ", "ВС")
                val dayDistribution = statsSummary?.dayDistribution ?: listOf(0.2f, 0.4f, 0.3f, 1.0f, 0.7f, 0.5f, 0.2f)
                val favDay = statsSummary?.favoriteDayOfWeek ?: "Четверг"

                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Today,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "ЛЮБИМЫЙ ДЕНЬ: ${favDay.uppercase()}",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 0.8.sp
                                ),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(90.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Bottom
                        ) {
                            dayNames.forEachIndexed { index, name ->
                                val fraction = dayDistribution.getOrElse(index) { 0.15f }
                                val isPeakDay = when (favDay.lowercase()) {
                                    "понедельник" -> index == 0
                                    "вторник" -> index == 1
                                    "среда" -> index == 2
                                    "четверг" -> index == 3
                                    "пятница" -> index == 4
                                    "суббота" -> index == 5
                                    "воскресенье" -> index == 6
                                    else -> index == 3
                                }

                                val animatedHeight by animateFloatAsState(
                                    targetValue = fraction,
                                    animationSpec = tween(600),
                                    label = "barHeight"
                                )

                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .width(22.dp)
                                            .height((56 * animatedHeight).dp.coerceAtLeast(10.dp))
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(
                                                if (isPeakDay) MaterialTheme.colorScheme.primary
                                                else MaterialTheme.colorScheme.surfaceContainerHighest
                                            )
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = name,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = if (isPeakDay) FontWeight.ExtraBold else FontWeight.Medium
                                        ),
                                        color = if (isPeakDay) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 4. Секция: ТОП-5 ТРЕКОВ
            item(key = "stats_top_tracks_header") {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "ТОП-5 ТРЕКОВ",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.sp
                        ),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            val topTracks = statsSummary?.topTracks ?: emptyList()
            if (topTracks.isEmpty()) {
                item(key = "stats_top_tracks_empty") {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                    ) {
                        Text(
                            text = "Пока нет данных о прослушиваниях за этот период.\nВключайте любимые треки, чтобы собрать топ!",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(24.dp)
                        )
                    }
                }
            } else {
                itemsIndexed(
                    items = topTracks,
                    key = { index, track -> "stats_top_track_${track.trackId}_$index" }
                ) { index, track ->
                    PixelTopTrackCard(
                        rank = index + 1,
                        track = track,
                        onPlayClick = { playTrack(track) }
                    )
                }
            }

            // 5. Секция: ТОП ИСПОЛНИТЕЛЕЙ
            val topArtists = statsSummary?.topArtists ?: emptyList()
            if (topArtists.isNotEmpty()) {
                item(key = "stats_top_artists_section") {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "ТОП ИСПОЛНИТЕЛЕЙ",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.sp
                        ),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 20.dp)
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        topArtists.forEachIndexed { idx, artist ->
                            PixelTopArtistCard(rank = idx + 1, artist = artist)
                        }
                    }
                }
            }

            // 6. Секция: ТОП АЛЬБОМОВ
            val topAlbums = statsSummary?.topAlbums ?: emptyList()
            if (topAlbums.isNotEmpty()) {
                item(key = "stats_top_albums_section") {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "ТОП АЛЬБОМОВ",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.sp
                        ),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 20.dp)
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        topAlbums.forEachIndexed { idx, album ->
                            PixelTopAlbumCard(rank = idx + 1, album = album)
                        }
                    }
                }
            }

            // 7. Секция: МУЗЫКАЛЬНЫЕ ЭПОХИ
            val eras = statsSummary?.eras ?: emptyList()
            if (eras.isNotEmpty()) {
                item(key = "stats_eras_section") {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "МУЗЫКАЛЬНЫЕ ЭПОХИ",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.sp
                        ),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 20.dp)
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            eras.forEach { era ->
                                val eraColor = when (era.era) {
                                    "2020-е" -> MaterialTheme.colorScheme.primary
                                    "2010-е" -> Color(0xFF00B0FF)
                                    "2000-е" -> Color(0xFF00E676)
                                    "90-е" -> Color(0xFFFF9100)
                                    "80-е" -> Color(0xFFFF4081)
                                    else -> Color(0xFFE040FB)
                                }

                                val animRatio by animateFloatAsState(
                                    targetValue = era.ratio.coerceIn(0f, 1f),
                                    animationSpec = tween(600),
                                    label = "eraProgress"
                                )

                                Column {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = era.era,
                                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold),
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "${(era.ratio * 100).toInt()}% • ${era.count} ${getTracksCountString(era.count)}",
                                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                            color = eraColor
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(8.dp)
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxHeight()
                                                .fillMaxWidth(animRatio.coerceAtLeast(0.02f))
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(eraColor)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 8. Секция: АКТИВНОСТЬ ПО ЧАСАМ СУТОК (Pixel 24h Activity Wave)
            item(key = "stats_hourly_chart_section") {
                val hourlyDist = statsSummary?.hourlyDistribution ?: List(24) { 0.1f }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "АКТИВНОСТЬ ПО ЧАСАМ СУТОК",
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp
                    ),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 20.dp)
                )
                Spacer(modifier = Modifier.height(10.dp))

                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(64.dp),
                            horizontalArrangement = Arrangement.spacedBy(3.dp),
                            verticalAlignment = Alignment.Bottom
                        ) {
                            hourlyDist.forEachIndexed { hour, frac ->
                                val peakH = statsSummary?.peakHour ?: 12
                                val isPeak = hour == peakH || hour == (peakH + 1) % 24

                                val animH by animateFloatAsState(
                                    targetValue = frac,
                                    animationSpec = tween(500),
                                    label = "hourH"
                                )

                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height((48 * animH).dp.coerceAtLeast(6.dp))
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(
                                            if (isPeak) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.surfaceContainerHighest
                                        )
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("00:00", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("06:00", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("12:00", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("18:00", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("23:59", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PixelTopTrackCard(
    rank: Int,
    track: TopTrackStat,
    onPlayClick: () -> Unit
) {
    val rankColor = when (rank) {
        1 -> Color(0xFFFFD700) // Золото
        2 -> Color(0xFFC0C0C0) // Серебро
        3 -> Color(0xFFCD7F32) // Бронза
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onPlayClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Номер места
            Surface(
                shape = CircleShape,
                color = when (rank) {
                    1 -> Color(0xFFFFD700).copy(alpha = 0.15f)
                    2 -> Color(0xFFC0C0C0).copy(alpha = 0.15f)
                    3 -> Color(0xFFCD7F32).copy(alpha = 0.15f)
                    else -> MaterialTheme.colorScheme.surfaceContainerHighest
                },
                modifier = Modifier.size(34.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = "#$rank",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Black,
                            fontSize = 13.sp
                        ),
                        color = rankColor
                    )
                }
            }

            Spacer(modifier = Modifier.width(6.dp))

            // Обложка
            val coverUrl = track.coverUrl?.let {
                if (it.startsWith("/")) "http://185.196.41.31$it" else it
            }
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                modifier = Modifier.size(52.dp)
            ) {
                if (!coverUrl.isNullOrEmpty()) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(coverUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.MusicNote,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            // Название и артист
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = track.artist,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                val totalMins = track.totalListenedMs / (1000 * 60)
                Text(
                    text = "${track.playCount} ${getPlaysString(track.playCount)} • $totalMins мин.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Кнопка Play
            IconButton(
                onClick = onPlayClick,
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.PlayArrow,
                    contentDescription = "Слушать",
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

@Composable
fun PixelTopArtistCard(
    rank: Int,
    artist: TopArtistStat
) {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.width(135.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val coverUrl = artist.coverUrl?.let {
                if (it.startsWith("/")) "http://185.196.41.31$it" else it
            }

            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                modifier = Modifier.size(72.dp)
            ) {
                if (!coverUrl.isNullOrEmpty()) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(coverUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.Person,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = artist.artist,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(2.dp))

            val totalHours = artist.totalListenedMs / (1000 * 60 * 60)
            val totalMins = (artist.totalListenedMs / (1000 * 60)) % 60
            Text(
                text = if (totalHours > 0) "$totalHours ч. $totalMins м." else "$totalMins мин.",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center
            )
        }
    }
}

private fun getPlaysString(count: Long): String {
    val lastDigit = count % 10
    val lastTwoDigits = count % 100
    return when {
        lastTwoDigits in 11..19 -> "прослушиваний"
        lastDigit == 1L -> "прослушивание"
        lastDigit in 2..4 -> "прослушивания"
        else -> "прослушиваний"
    }
}

private fun getDaysString(count: Int): String {
    val lastDigit = count % 10
    val lastTwoDigits = count % 100
    return when {
        lastTwoDigits in 11..19 -> "дней"
        lastDigit == 1 -> "день"
        lastDigit in 2..4 -> "дня"
        else -> "дней"
    }
}

private fun getTracksCountString(count: Long): String {
    val lastDigit = count % 10
    val lastTwoDigits = count % 100
    return when {
        lastTwoDigits in 11..19 -> "треков"
        lastDigit == 1L -> "трек"
        lastDigit in 2..4 -> "трека"
        else -> "треков"
    }
}

@Composable
fun PixelTopAlbumCard(
    rank: Int,
    album: TopAlbumStat
) {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.width(140.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val coverUrl = album.coverUrl?.let {
                if (it.startsWith("/")) "http://185.196.41.31$it" else it
            }

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                modifier = Modifier.size(80.dp)
            ) {
                if (!coverUrl.isNullOrEmpty()) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(coverUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.Album,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(38.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = album.album,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = album.artist,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(4.dp))

            val totalMins = album.totalListenedMs / (1000 * 60)
            Text(
                text = "${album.playCount} ${getPlaysString(album.playCount)} • $totalMins мин.",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center
            )
        }
    }
}
