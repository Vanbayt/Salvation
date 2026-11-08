package org.akanework.gramophone.ui.fragments

import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.rememberNestedScrollInteropConnection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.fragment.app.Fragment
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay
import org.akanework.gramophone.R
import org.akanework.gramophone.logic.HistoryManager
import org.akanework.gramophone.logic.api.Album
import org.akanework.gramophone.logic.api.Artist
import org.akanework.gramophone.logic.api.BackendStatsSummary
import org.akanework.gramophone.logic.api.NetworkClient
import org.akanework.gramophone.logic.api.PresetCard
import org.akanework.gramophone.logic.api.Track
import org.akanework.gramophone.logic.recommendation.ContextManager
import org.akanework.gramophone.ui.AppTab
import org.akanework.gramophone.ui.MainActivity
import org.akanework.gramophone.ui.components.PlayingEqIcon
import org.akanework.gramophone.ui.fragments.stats.StatsFragment
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class MainFragment : Fragment() {

    private var isMixLoading = false

    private var historyAlbumsState = mutableStateOf<List<Album>>(emptyList())
    private var historyTracksState = mutableStateOf<List<Track>>(emptyList())
    private var favoriteAlbumsState = mutableStateOf<List<Album>>(emptyList())
    private var favoriteArtistsState = mutableStateOf<List<Artist>>(emptyList())
    private var favoriteTracksState = mutableStateOf<List<Track>>(emptyList())
    private var contextPresetsState = mutableStateOf<List<PresetCard>>(emptyList())
    private var isPresetsLoading = mutableStateOf(false)
    private var statsSummaryState = mutableStateOf<BackendStatsSummary?>(null)

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_main, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupComposeContent(view)

        loadHistory()
        loadFavoriteAlbums()
        loadFavoriteArtists()
        loadFavoriteTracks()
        loadContextPresets()
        loadStatsSummary()
    }

    override fun onResume() {
        super.onResume()
        loadContextPresets()
        loadStatsSummary()
    }

    private fun loadStatsSummary() {
        val ctx = context ?: return
        NetworkClient.getApi(ctx).getStatsSummary("week").enqueue(object : Callback<BackendStatsSummary> {
            override fun onResponse(call: Call<BackendStatsSummary>, response: Response<BackendStatsSummary>) {
                if (response.isSuccessful && response.body() != null) {
                    statsSummaryState.value = response.body()
                }
            }
            override fun onFailure(call: Call<BackendStatsSummary>, t: Throwable) {
                // Silently ignore
            }
        })
    }

    private fun getThemeColorScheme(): ColorScheme {
        return org.akanework.gramophone.ui.theme.AppThemeHelper.getColorScheme(requireContext())
    }

    private fun setupComposeContent(view: View) {
        view.findViewById<ComposeView>(R.id.compose_content).setContent {
            org.akanework.gramophone.ui.theme.AppTheme {
                val nestedScrollConnection = rememberNestedScrollInteropConnection()

                var isVisible by remember { mutableStateOf(false) }
                LaunchedEffect(Unit) { isVisible = true }

                val mainActivity = remember { requireActivity() as MainActivity }
                var currentPlayingTrackId by remember { mutableStateOf<String?>(null) }
                var isCurrentPlaying by remember { mutableStateOf(false) }
                var selectedPreset by remember { mutableStateOf<PresetCard?>(null) }

                LaunchedEffect(Unit) {
                    while (true) {
                        val p = mainActivity.getPlayer()
                        currentPlayingTrackId = p?.currentMediaItem?.mediaId
                        isCurrentPlaying = p?.isPlaying == true
                        delay(350)
                    }
                }

                val recentOrFavoriteTracks = remember(historyTracksState.value, favoriteTracksState.value) {
                    if (historyTracksState.value.isNotEmpty()) {
                        historyTracksState.value.take(6)
                    } else {
                        favoriteTracksState.value.take(6)
                    }
                }

            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.TopCenter
            ) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .widthIn(max = 1100.dp)
                        .nestedScroll(nestedScrollConnection),
                    contentPadding = PaddingValues(bottom = 260.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    // 0. TOP APP BAR (Только Salvation + иконки поиска и настроек)
                    item {
                        ExpressiveTopAppBar(
                            onSearchClick = {
                                (requireActivity() as MainActivity).switchTab(AppTab.SEARCH)
                            },
                            onSettingsClick = {
                                (requireActivity() as MainActivity).startFragment(SettingsFragment())
                            }
                        )
                    }

                    // 1. HERO TITLE & FLOATING PLAY BUTTON ("Your Mix" Style with dynamic mood)
                    item {
                        AnimatedVisibility(
                            visible = isVisible,
                            enter = fadeIn(tween(400)) + slideInVertically(tween(500)) { it / 3 }
                        ) {
                            ExpressiveYourMixHeroHeader(
                                tracksCount = favoriteTracksState.value.size,
                                selectedPreset = selectedPreset,
                                onPlayClick = {
                                    val target = selectedPreset
                                        ?: contextPresetsState.value.firstOrNull { it.id == "daily_mix" }
                                        ?: contextPresetsState.value.firstOrNull()
                                    if (target != null) {
                                        playPreset(target)
                                    } else {
                                        playPresetById("daily_mix", "Ваш Микс")
                                    }
                                }
                            )
                        }
                    }

                    // 1.2. MATERIAL 3 EXPRESSIVE MOOD PILLS
                    if (contextPresetsState.value.isNotEmpty()) {
                        item {
                            AnimatedVisibility(
                                visible = isVisible,
                                enter = fadeIn(tween(450)) + slideInVertically(tween(550)) { it / 3 }
                            ) {
                                ExpressiveMoodPillsRow(
                                    presets = contextPresetsState.value,
                                    selectedPreset = selectedPreset,
                                    onSelect = { preset ->
                                        selectedPreset = preset
                                        playPreset(preset)
                                    }
                                )
                            }
                        }
                    }

                    // 2. BENTO QUICK ACCESS (Симметричный 2x2 грид быстрого доступа)
                    item {
                        AnimatedVisibility(
                            visible = isVisible,
                            enter = fadeIn(tween(600)) + slideInVertically(tween(700)) { it / 3 }
                        ) {
                            ExpressiveBentoGrid(
                                favoritesCount = favoriteTracksState.value.size,
                                albumsCount = favoriteAlbumsState.value.size,
                                artistsCount = favoriteArtistsState.value.size,
                                onFavoritesClick = {
                                    (requireActivity() as MainActivity).startFragment(FavoritesFragment())
                                },
                                onAlbumsClick = {
                                    (requireActivity() as MainActivity).switchTab(AppTab.LIBRARY)
                                },
                                onArtistsClick = {
                                    (requireActivity() as MainActivity).switchTab(AppTab.LIBRARY)
                                },
                                onMixClick = {
                                    val target = selectedPreset
                                        ?: contextPresetsState.value.firstOrNull { it.id == "daily_mix" }
                                        ?: contextPresetsState.value.firstOrNull()
                                    if (target != null) {
                                        playPreset(target)
                                    } else {
                                        playPresetById("daily_mix", "Ваш Микс")
                                    }
                                }
                            )
                        }
                    }

                    // 3. STATS HIGHLIGHTS (Компактные блоки статистики за неделю: не сплошной Hero, а несколько мелких)
                    if (statsSummaryState.value != null && (statsSummaryState.value?.totalPlaysCount ?: 0L) > 0L) {
                        item {
                            AnimatedVisibility(
                                visible = isVisible,
                                enter = fadeIn(tween(650)) + slideInVertically(tween(750)) { it / 3 }
                            ) {
                                ExpressiveHomeStatsSection(
                                    summary = statsSummaryState.value,
                                    onOpenStats = {
                                        (requireActivity() as MainActivity).startFragment(StatsFragment())
                                    }
                                )
                            }
                        }
                    }

                    // 4. НЕДАВНО ИГРАЛО / ИЗБРАННЫЕ ТРЕКИ С ЖИВЫМ ЭКВАЛАЙЗЕРОМ
                    if (recentOrFavoriteTracks.isNotEmpty()) {
                        item {
                            AnimatedVisibility(
                                visible = isVisible,
                                enter = fadeIn(tween(700)) + slideInVertically(tween(800)) { it / 3 }
                            ) {
                                ExpressiveQuickTracksSection(
                                    title = if (historyTracksState.value.isNotEmpty()) "Недавно играло" else "Избранные треки в фокусе",
                                    tracks = recentOrFavoriteTracks,
                                    currentPlayingTrackId = currentPlayingTrackId,
                                    isPlaying = isCurrentPlaying,
                                    onTrackClick = { clickedTrack ->
                                        playTrack(clickedTrack, recentOrFavoriteTracks, "Главный экран")
                                    }
                                )
                            }
                        }
                    }

                    // 5. НЕДАВНО В ЭФИРЕ (АЛЬБОМЫ)
                    if (historyAlbumsState.value.isNotEmpty()) {
                        item {
                            AnimatedVisibility(
                                visible = isVisible,
                                enter = fadeIn(tween(800)) + slideInVertically(tween(900)) { it / 3 }
                            ) {
                                SectionWithCarousel(
                                    title = "Недавно в эфире",
                                    items = historyAlbumsState.value,
                                    onItemClick = { openAlbum(it.id, null) }
                                )
                            }
                        }
                    }

                    // 6. ВАШИ АЛЬБОМЫ
                    if (favoriteAlbumsState.value.isNotEmpty()) {
                        item {
                            AnimatedVisibility(
                                visible = isVisible,
                                enter = fadeIn(tween(900)) + slideInVertically(tween(1000)) { it / 3 }
                            ) {
                                SectionWithCarousel(
                                    title = "Альбомы в коллекции",
                                    items = favoriteAlbumsState.value,
                                    onItemClick = { openAlbum(it.id, null) }
                                )
                            }
                        }
                    }

                    // 7. ВАШИ АРТИСТЫ
                    if (favoriteArtistsState.value.isNotEmpty()) {
                        item {
                            AnimatedVisibility(
                                visible = isVisible,
                                enter = fadeIn(tween(1000)) + slideInVertically(tween(1100)) { it / 3 }
                            ) {
                                SectionWithCarouselArtist(
                                    title = "Любимые артисты",
                                    items = favoriteArtistsState.value,
                                    onItemClick = { openArtist(it.id, null) }
                                )
                            }
                        }
                    }

                    // 8. СТАТИСТИКА МЕДИАТЕКИ (PixelPlayer Inspired Overview)
                    item {
                        AnimatedVisibility(
                            visible = isVisible,
                            enter = fadeIn(tween(1100)) + slideInVertically(tween(1200)) { it / 3 }
                        ) {
                            StatsOverviewCard(
                                tracksCount = favoriteTracksState.value.size,
                                albumsCount = favoriteAlbumsState.value.size,
                                artistsCount = favoriteArtistsState.value.size,
                                onClick = {
                                    (requireActivity() as MainActivity).startFragment(org.akanework.gramophone.ui.fragments.stats.StatsFragment.newInstance())
                                }
                            )
                        }
                    }
                }
            }
            }
        }
    }

    // --- Expressive UI Widgets ---

    @Composable
    fun ExpressiveTopAppBar(
        onSearchClick: () -> Unit,
        onSettingsClick: () -> Unit
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(start = 24.dp, end = 20.dp, top = 16.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Salvation",
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontWeight = FontWeight.Black,
                    letterSpacing = (-0.5).sp
                ),
                color = MaterialTheme.colorScheme.primary
            )

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FilledTonalIconButton(
                    onClick = onSearchClick,
                    modifier = Modifier.size(46.dp),
                    shape = CircleShape,
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        contentColor = MaterialTheme.colorScheme.primary,
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    )
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_search),
                        contentDescription = "Поиск",
                        modifier = Modifier.size(22.dp)
                    )
                }

                FilledTonalIconButton(
                    onClick = onSettingsClick,
                    modifier = Modifier.size(46.dp),
                    shape = CircleShape,
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        contentColor = MaterialTheme.colorScheme.primary,
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    )
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_settings),
                        contentDescription = "Настройки",
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }

    @Composable
    fun ExpressiveMoodPillsRow(
        presets: List<PresetCard>,
        selectedPreset: PresetCard?,
        onSelect: (PresetCard) -> Unit
    ) {
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(presets, key = { it.id }) { preset ->
                val isSelected = (selectedPreset?.id == preset.id) || (selectedPreset == null && preset.id == "daily_mix")
                val presetIcon = when (preset.id) {
                    "daily_mix" -> Icons.Rounded.AutoAwesome
                    "flow_mix" -> Icons.Rounded.Waves
                    "discovery_radar" -> Icons.Rounded.Explore
                    "energy_flow" -> Icons.Rounded.Bolt
                    "calm_focus" -> Icons.Rounded.Spa
                    "time_capsule" -> Icons.Rounded.History
                    else -> Icons.Rounded.GraphicEq
                }

                Surface(
                    onClick = { onSelect(preset) },
                    shape = CircleShape,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.65f)
                    },
                    contentColor = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    border = if (isSelected) {
                        BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
                    } else {
                        BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
                    },
                    shadowElevation = if (isSelected) 3.dp else 0.dp,
                    modifier = Modifier.height(38.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            imageVector = presetIcon,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = preset.moodTag,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.SemiBold,
                                fontSize = 13.sp
                            )
                        )
                    }
                }
            }
        }
    }

    @Composable
    fun DynamicPresetCarousel(
        presets: List<PresetCard>,
        selectedPresetId: String?,
        onPresetClick: (PresetCard) -> Unit
    ) {
        if (presets.isEmpty()) return

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 2.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Персональные волны",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Black,
                    letterSpacing = (-0.3).sp,
                    fontSize = 20.sp
                ),
                modifier = Modifier.padding(horizontal = 24.dp),
                color = MaterialTheme.colorScheme.onSurface
            )

            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                items(presets, key = { it.id }) { preset ->
                    val isSelected = preset.id == selectedPresetId
                    val gradientColors = remember(preset.gradientHex) {
                        if (preset.gradientHex.size >= 2) {
                            preset.gradientHex.map {
                                try {
                                    Color(android.graphics.Color.parseColor(it))
                                } catch (e: Exception) {
                                    Color(0xFF1E293B)
                                }
                            }
                        } else {
                            listOf(Color(0xFF1E293B), Color(0xFF0F172A))
                        }
                    }

                    val presetIcon = when (preset.id) {
                        "daily_mix" -> Icons.Rounded.AutoAwesome
                        "discovery_radar" -> Icons.Rounded.Explore
                        "energy_flow" -> Icons.Rounded.Bolt
                        "calm_focus" -> Icons.Rounded.Spa
                        "time_capsule" -> Icons.Rounded.History
                        else -> Icons.Rounded.GraphicEq
                    }

                    // Material 3 Expressive Asymmetrical Corner Shape Token (Android 15-16)
                    val cardShape = remember(preset.isContrast) {
                        if (preset.isContrast) {
                            RoundedCornerShape(topStart = 16.dp, topEnd = 32.dp, bottomEnd = 16.dp, bottomStart = 32.dp)
                        } else {
                            RoundedCornerShape(topStart = 32.dp, topEnd = 16.dp, bottomEnd = 32.dp, bottomStart = 16.dp)
                        }
                    }

                    Card(
                        onClick = { onPresetClick(preset) },
                        modifier = Modifier
                            .width(162.dp)
                            .height(195.dp),
                        shape = cardShape,
                        border = when {
                            isSelected -> BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
                            preset.isContrast -> BorderStroke(1.5.dp, Brush.horizontalGradient(listOf(Color(0xFFBA8B02), Color(0xFFFFD700))))
                            else -> BorderStroke(1.dp, Color.White.copy(alpha = 0.12f))
                        },
                        elevation = CardDefaults.cardElevation(
                            defaultElevation = if (isSelected) 6.dp else 2.dp,
                            pressedElevation = 8.dp
                        )
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Brush.linearGradient(gradientColors))
                        ) {
                            // Subtle artwork preview if cover available
                            if (!preset.coverUrl.isNullOrEmpty()) {
                                AsyncImage(
                                    model = preset.coverUrl,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .graphicsLayer(alpha = 0.35f)
                                )
                            }

                            // High-contrast M3 Expressive film overlay
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.verticalGradient(
                                            listOf(
                                                Color.Black.copy(alpha = 0.12f),
                                                Color.Black.copy(alpha = 0.45f),
                                                Color.Black.copy(alpha = 0.88f)
                                            )
                                        )
                                    )
                                    .padding(14.dp)
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxSize(),
                                    verticalArrangement = Arrangement.SpaceBetween
                                ) {
                                    // Top Row: Vector Mood Tag Pill & Quick Play
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Surface(
                                            shape = CircleShape,
                                            color = Color.Black.copy(alpha = 0.38f),
                                            contentColor = if (preset.isContrast) Color(0xFFFFD700) else Color.White
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp)
                                            ) {
                                                Icon(
                                                    imageVector = presetIcon,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(13.dp),
                                                    tint = if (preset.isContrast) Color(0xFFFFD700) else Color.White
                                                )
                                                Spacer(modifier = Modifier.width(5.dp))
                                                Text(
                                                    text = preset.moodTag.uppercase(),
                                                    style = MaterialTheme.typography.labelSmall.copy(
                                                        fontWeight = FontWeight.Black,
                                                        fontSize = 9.sp,
                                                        letterSpacing = 0.8.sp
                                                    ),
                                                    color = if (preset.isContrast) Color(0xFFFFD700) else Color.White
                                                )
                                            }
                                        }

                                        Surface(
                                            shape = CircleShape,
                                            color = Color.White.copy(alpha = 0.25f),
                                            modifier = Modifier.size(34.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(
                                                    imageVector = Icons.Rounded.PlayArrow,
                                                    contentDescription = "Воспроизвести",
                                                    tint = Color.White,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                        }
                                    }

                                    // Bottom Text Content
                                    Column {
                                        Text(
                                            text = preset.title,
                                            color = Color.White,
                                            style = MaterialTheme.typography.titleMedium.copy(
                                                fontWeight = FontWeight.Black,
                                                fontSize = 16.sp,
                                                lineHeight = 20.sp,
                                                letterSpacing = (-0.3).sp
                                            ),
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Spacer(modifier = Modifier.height(3.dp))
                                        Text(
                                            text = preset.subtitle,
                                            color = Color.White.copy(alpha = 0.80f),
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                fontSize = 11.5.sp,
                                                lineHeight = 14.sp
                                            ),
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    fun ExpressiveYourMixHeroHeader(
        tracksCount: Int,
        selectedPreset: PresetCard?,
        onPlayClick: () -> Unit
    ) {
        val displayTitle = when (selectedPreset?.id) {
            "flow_mix" -> "Музыкальный\nПоток"
            "energy_flow" -> "Энергия\nи Драйв"
            "calm_focus" -> "Спокойствие\nи Фокус"
            "discovery_radar" -> "Радар\nОткрытий"
            "time_capsule" -> "Глубокое\nПогружение"
            else -> "Мой\nМикс"
        }

        val displaySubtitle = when (selectedPreset?.id) {
            "flow_mix" -> "Непрерывное радио в вашем вкусе"
            "energy_flow" -> "Бодрый ритм и динамичное звучание"
            "calm_focus" -> "Мягкая музыка для отдыха и концентрации"
            "discovery_radar" -> "Свежие треки от новых для вас артистов"
            "time_capsule" -> "Редкие находки и забытые треки"
            else -> if (tracksCount > 0) "$tracksCount треков • Медиатека и открытия" else "Моя музыка с новыми треками"
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
                Text(
                    text = displayTitle,
                    style = MaterialTheme.typography.displayMedium.copy(
                        fontWeight = FontWeight.Black,
                        lineHeight = 44.sp,
                        letterSpacing = (-1.2).sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = displaySubtitle,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Medium,
                        lineHeight = 18.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Big Floating Play Button (From Reference Screen)
            Surface(
                onClick = onPlayClick,
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                shadowElevation = 8.dp,
                tonalElevation = 6.dp,
                modifier = Modifier.size(76.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.PlayArrow,
                        contentDescription = "Играть волну",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(38.dp)
                    )
                }
            }
        }
    }

    @Composable
    fun ExpressiveBentoGrid(
        favoritesCount: Int,
        albumsCount: Int,
        artistsCount: Int,
        onFavoritesClick: () -> Unit,
        onAlbumsClick: () -> Unit,
        onArtistsClick: () -> Unit,
        onMixClick: () -> Unit
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
        ) {
            Text(
                text = "Быстрый доступ",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            // ROW 1: "Любимые треки" & "Мой поток"
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                BentoCard(
                    title = "Любимые треки",
                    subtitle = if (favoritesCount > 0) "$favoritesCount треков" else "Медиатека",
                    iconRes = R.drawable.ic_favorite_filled,
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                    contentColor = MaterialTheme.colorScheme.primary,
                    onClick = onFavoritesClick,
                    modifier = Modifier.weight(1f)
                )

                BentoCard(
                    title = "Мой поток",
                    subtitle = "Автомикс",
                    iconRes = R.drawable.ic_shuffle,
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.55f),
                    contentColor = MaterialTheme.colorScheme.tertiary,
                    onClick = onMixClick,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // ROW 2: "Альбомы" & "Артисты"
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                BentoCard(
                    title = "Альбомы",
                    subtitle = if (albumsCount > 0) "$albumsCount релизов" else "Дискография",
                    iconRes = R.drawable.ic_library,
                    containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f),
                    contentColor = MaterialTheme.colorScheme.secondary,
                    onClick = onAlbumsClick,
                    modifier = Modifier.weight(1f)
                )

                BentoCard(
                    title = "Артисты",
                    subtitle = if (artistsCount > 0) "$artistsCount авторов" else "Исполнители",
                    iconRes = R.drawable.ic_person,
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    onClick = onArtistsClick,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }

    @Composable
    fun ExpressiveHomeStatsSection(
        summary: BackendStatsSummary?,
        onOpenStats: () -> Unit
    ) {
        if (summary == null || summary.totalPlaysCount <= 0L) return

        val totalMins = summary.totalListenedMs / (1000 * 60)
        val hours = totalMins / 60
        val mins = totalMins % 60
        val timeStr = if (hours > 0) "$hours ч. $mins м." else "$mins мин."

        val topArtist = summary.topArtists.firstOrNull()
        val topTrack = summary.topTracks.firstOrNull()

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
        ) {
            // Заголовок секции со стрелочкой перехода
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onOpenStats),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Insights,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Статистика недели",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = "Вся стата",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            // Ряд из 3 компактных мини-блоков
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Блок 1: Время / Треки
                Surface(
                    onClick = onOpenStats,
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    tonalElevation = 1.dp,
                    modifier = Modifier
                        .weight(1f)
                        .height(90.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(11.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Schedule,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "ВРЕМЯ",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Black,
                                    fontSize = 9.5.sp,
                                    letterSpacing = 0.5.sp
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Column {
                            Text(
                                text = timeStr,
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 14.sp
                                ),
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${summary.totalPlaysCount} треков",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }
                    }
                }

                // Блок 2: Топ артист
                if (topArtist != null) {
                    Surface(
                        onClick = onOpenStats,
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        tonalElevation = 1.dp,
                        modifier = Modifier
                            .weight(1f)
                            .height(90.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(11.dp),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Person,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = "АРТИСТ #1",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Black,
                                        fontSize = 9.5.sp,
                                        letterSpacing = 0.5.sp
                                    ),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Column {
                                Text(
                                    text = topArtist.artist,
                                    style = MaterialTheme.typography.titleSmall.copy(
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 14.sp
                                    ),
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${topArtist.playCount} раз",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }

                // Блок 3: Топ трек
                if (topTrack != null) {
                    Surface(
                        onClick = onOpenStats,
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        tonalElevation = 1.dp,
                        modifier = Modifier
                            .weight(1f)
                            .height(90.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(11.dp),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Audiotrack,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.tertiary,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = "ТРЕК #1",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Black,
                                        fontSize = 9.5.sp,
                                        letterSpacing = 0.5.sp
                                    ),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Column {
                                Text(
                                    text = topTrack.title,
                                    style = MaterialTheme.typography.titleSmall.copy(
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 14.sp
                                    ),
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = topTrack.artist,
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    fun BentoCard(
        title: String,
        subtitle: String,
        iconRes: Int,
        containerColor: Color,
        contentColor: Color,
        onClick: () -> Unit,
        modifier: Modifier = Modifier
    ) {
        Surface(
            onClick = onClick,
            modifier = modifier.height(78.dp),
            shape = RoundedCornerShape(22.dp),
            color = containerColor,
            tonalElevation = 1.dp
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = contentColor.copy(alpha = 0.18f),
                    modifier = Modifier.size(42.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            painter = painterResource(id = iconRes),
                            contentDescription = null,
                            tint = contentColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }

    @Composable
    fun ExpressiveQuickTracksSection(
        title: String,
        tracks: List<Track>,
        currentPlayingTrackId: String?,
        isPlaying: Boolean,
        onTrackClick: (Track) -> Unit
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
            )

            Column(
                modifier = Modifier.padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                tracks.forEach { track ->
                    val isThisTrackPlaying = track.id == currentPlayingTrackId
                    val cardBgColor by animateColorAsState(
                        targetValue = if (isThisTrackPlaying) {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.7f)
                        },
                        animationSpec = tween(250),
                        label = "trackCardBg"
                    )

                    Surface(
                        onClick = { onTrackClick(track) },
                        shape = RoundedCornerShape(20.dp),
                        color = cardBgColor,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp)
                        ) {
                            val coverUrl = if (track.cover?.startsWith("/") == true) "http://185.196.41.31${track.cover}" else track.cover
                            AsyncImage(
                                model = coverUrl,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(52.dp)
                                    .clip(RoundedCornerShape(14.dp))
                            )

                            Spacer(modifier = Modifier.width(14.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = track.title,
                                    style = MaterialTheme.typography.bodyLarge.copy(
                                        fontWeight = if (isThisTrackPlaying) FontWeight.ExtraBold else FontWeight.Bold
                                    ),
                                    color = if (isThisTrackPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = track.artist,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            if (isThisTrackPlaying) {
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        PlayingEqIcon(
                                            color = MaterialTheme.colorScheme.primary,
                                            isPlaying = isPlaying,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            } else {
                                FilledTonalIconButton(
                                    onClick = { onTrackClick(track) },
                                    modifier = Modifier.size(40.dp),
                                    shape = CircleShape
                                ) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_play),
                                        contentDescription = "Играть",
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

    @Composable
    fun SectionWithCarousel(title: String, items: List<Album>, onItemClick: (Album) -> Unit) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
            )
            LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(items, key = { it.id }) { album ->
                    Card(
                        modifier = Modifier
                            .width(172.dp)
                            .height(224.dp)
                            .clickable { onItemClick(album) },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                        shape = RoundedCornerShape(22.dp)
                    ) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            val coverUrl = if (album.cover?.startsWith("/") == true) "http://185.196.41.31${album.cover}" else album.cover
                            AsyncImage(
                                model = coverUrl,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )

                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.verticalGradient(
                                            0.35f to Color.Transparent,
                                            0.7f to Color.Black.copy(alpha = 0.55f),
                                            1.0f to Color.Black.copy(alpha = 0.90f)
                                        )
                                    )
                            )

                            Row(
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = album.title,
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold),
                                        color = Color.White,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = album.artistName.uppercase(),
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = Color(0xFFD0D0D0),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                Surface(
                                    shape = CircleShape,
                                    color = Color.White.copy(alpha = 0.25f),
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            painter = painterResource(id = R.drawable.ic_play),
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    fun SectionWithCarouselArtist(title: String, items: List<Artist>, onItemClick: (Artist) -> Unit) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
            )
            LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(items, key = { it.id }) { artist ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .width(108.dp)
                            .clickable { onItemClick(artist) }
                    ) {
                        Surface(
                            shape = CircleShape,
                            border = BorderStroke(2.5.dp, MaterialTheme.colorScheme.primaryContainer),
                            tonalElevation = 3.dp,
                            modifier = Modifier.size(100.dp)
                        ) {
                            val coverUrl = if (artist.cover?.startsWith("/") == true) "http://185.196.41.31${artist.cover}" else artist.cover
                            AsyncImage(
                                model = coverUrl,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = artist.name,
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }

    @Composable
    fun StatsOverviewCard(
        tracksCount: Int,
        albumsCount: Int,
        artistsCount: Int,
        onClick: () -> Unit
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            onClick = onClick
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_equalizer),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "ВАША МЕДИАТЕКА",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Black,
                                letterSpacing = 1.sp
                            ),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    StatItem(title = if (tracksCount > 0) "$tracksCount" else "—", subtitle = "Треков")
                    StatItem(title = if (albumsCount > 0) "$albumsCount" else "—", subtitle = "Альбомов")
                    StatItem(title = if (artistsCount > 0) "$artistsCount" else "—", subtitle = "Артистов")
                }
            }
        }
    }

    @Composable
    private fun StatItem(title: String, subtitle: String) {
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Black),
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    // --- Data Loaders & Playback Handlers ---

    private fun loadFavoriteAlbums() {
        NetworkClient.getApi(requireContext()).getFavoriteAlbums()
            .enqueue(object : Callback<List<Album>> {
                override fun onResponse(call: Call<List<Album>>, response: Response<List<Album>>) {
                    if (isAdded && response.isSuccessful) {
                        favoriteAlbumsState.value = response.body() ?: emptyList()
                    }
                }
                override fun onFailure(call: Call<List<Album>>, t: Throwable) {}
            })
    }

    private fun loadFavoriteArtists() {
        NetworkClient.getApi(requireContext()).getFavoriteArtists()
            .enqueue(object : Callback<List<Artist>> {
                override fun onResponse(call: Call<List<Artist>>, response: Response<List<Artist>>) {
                    if (isAdded && response.isSuccessful) {
                        favoriteArtistsState.value = response.body() ?: emptyList()
                    }
                }
                override fun onFailure(call: Call<List<Artist>>, t: Throwable) {}
            })
    }

    private fun loadFavoriteTracks() {
        NetworkClient.getApi(requireContext()).getFavorites(skip = 0, limit = 10000)
            .enqueue(object : Callback<List<Track>> {
                override fun onResponse(call: Call<List<Track>>, response: Response<List<Track>>) {
                    if (isAdded && response.isSuccessful) {
                        favoriteTracksState.value = response.body() ?: emptyList()
                    }
                }
                override fun onFailure(call: Call<List<Track>>, t: Throwable) {}
            })
    }

    private fun loadHistory() {
        val historyList = HistoryManager.getHistory(requireContext())
        historyAlbumsState.value = historyList.filterIsInstance<Album>()
        historyTracksState.value = historyList.filterIsInstance<Track>()
    }

    private fun openArtist(id: String, sharedView: View?) {
        val fragment = ArtistFragment.newInstance(id)
        (requireActivity() as MainActivity).startFragment(fragment, null, null)
    }

    private fun openAlbum(id: String, sharedView: View?) {
        val fragment = AlbumFragment.newInstance(id)
        (requireActivity() as MainActivity).startFragment(fragment, null, null)
    }

    private fun playTrack(clickedTrack: Track, allTracks: List<Track>, source: String = "Неизвестно", presetId: String? = null, isWave: Boolean = false) {
        val player = (requireActivity() as MainActivity).getPlayer() ?: return
        val startIndex = allTracks.indexOf(clickedTrack).coerceAtLeast(0)

        val mediaItems = allTracks.map { track ->
            val extrasBundle = Bundle().apply {
                putFloat("replay_gain", track.replayGain)
                putString("ARTIST_ID", track.artistId)
                putString("ALBUM_ID", track.albumId)
                putString("PLAYING_FROM", source)
                if (presetId != null) putString("PRESET_ID", presetId)
                if (isWave) putBoolean("IS_INFINITE_WAVE", true)
            }
            val finalCoverUrl = if (track.cover?.startsWith("/") == true) "http://185.196.41.31${track.cover}" else track.cover
            MediaItem.Builder()
                .setMediaId(track.id)
                .setUri("http://185.196.41.31/stream/${track.id}".toUri())
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(track.title)
                        .setArtist(track.artist)
                        .setArtworkUri(finalCoverUrl?.toUri())
                        .setDurationMs((track.duration * 1000L).takeIf { it > 0 })
                        .setExtras(extrasBundle)
                        .build()
                ).build()
        }
        player.setMediaItems(mediaItems, startIndex, 0L)
        player.prepare()
        player.play()
    }

    private fun playRandomFavoriteTrack() {
        val tracks = favoriteTracksState.value
        if (tracks.isNotEmpty()) {
            val randomTrack = tracks.random()
            playTrack(randomTrack, tracks, "Случайная находка")
            Toast.makeText(context, "Играет: ${randomTrack.title}", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "Ваш список избранного пуст", Toast.LENGTH_SHORT).show()
        }
    }

    private fun playMyMix() {
        isMixLoading = true
        Toast.makeText(context, "Собираем микс...", Toast.LENGTH_SHORT).show()

        NetworkClient.getApi(requireContext()).getFavorites(skip = 0, limit = 10000)
            .enqueue(object : Callback<List<Track>> {
                override fun onResponse(call: Call<List<Track>>, response: Response<List<Track>>) {
                    isMixLoading = false
                    if (isAdded && response.isSuccessful) {
                        val tracks = response.body() ?: emptyList()
                        if (tracks.isNotEmpty()) {
                            val shuffledTracks = org.akanework.gramophone.logic.utils.ShuffleUtils.smartShuffle(
                                items = tracks,
                                getArtist = { it.artist },
                                getAlbum = { it.album ?: "" },
                                getId = { it.id },
                                collectionKey = "my_mix",
                                affinityProfile = org.akanework.gramophone.logic.utils.ShuffleUtils.getAffinityProfile(requireContext())
                            )
                            playTrack(shuffledTracks.first(), shuffledTracks, "Мой микс")
                        } else {
                            Toast.makeText(context, "Ваш список избранного пуст", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        Toast.makeText(context, "Не удалось загрузить микс", Toast.LENGTH_SHORT).show()
                    }
                }
                override fun onFailure(call: Call<List<Track>>, t: Throwable) {
                    isMixLoading = false
                    if (isAdded) Toast.makeText(context, "Ошибка сети", Toast.LENGTH_SHORT).show()
                }
            })
    }

    private fun loadContextPresets() {
        val ctx = context ?: return
        val telemetry = ContextManager.gatherTelemetry(ctx)

        isPresetsLoading.value = true
        NetworkClient.getApi(ctx).getContextPresets(
            deviceType = telemetry.deviceType,
            latitude = telemetry.latitude,
            longitude = telemetry.longitude,
            localHour = telemetry.localHour,
            dayOfWeek = telemetry.dayOfWeek,
            isCharging = telemetry.isCharging,
            batteryPct = telemetry.batteryPct,
            ambientLux = telemetry.ambientLux,
            recentSkipCount = telemetry.recentSkipCount,
            skipVelocity = telemetry.skipVelocity,
            sessionDurationSec = telemetry.sessionDurationSec
        )
            .enqueue(object : Callback<List<PresetCard>> {
                override fun onResponse(call: Call<List<PresetCard>>, response: Response<List<PresetCard>>) {
                    isPresetsLoading.value = false
                    if (isAdded && response.isSuccessful) {
                        contextPresetsState.value = response.body() ?: emptyList()
                    }
                }
                override fun onFailure(call: Call<List<PresetCard>>, t: Throwable) {
                    isPresetsLoading.value = false
                }
            })
    }

    private fun playPresetById(presetId: String, title: String) {
        val dummyPreset = PresetCard(
            id = presetId,
            title = title,
            subtitle = "",
            coverUrl = null,
            gradientHex = emptyList(),
            moodTag = "",
            isContrast = false,
            seedQuery = ""
        )
        playPreset(dummyPreset)
    }

    private fun playPreset(preset: PresetCard) {
        val player = (activity as? MainActivity)?.getPlayer()
        org.akanework.gramophone.logic.utils.WaveManager.startPresetWave(
            context = requireContext().applicationContext,
            player = player,
            presetId = preset.id,
            presetTitle = preset.title,
            initialTrack = null
        )
    }
}