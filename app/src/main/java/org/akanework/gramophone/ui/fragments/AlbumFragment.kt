package org.akanework.gramophone.ui.fragments

import android.content.Intent
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.fragment.app.Fragment
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import coil3.compose.AsyncImage
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.akanework.gramophone.R
import org.akanework.gramophone.logic.api.*
import org.akanework.gramophone.ui.MainActivity
import org.akanework.gramophone.ui.components.library.EnhancedSongListItem
import org.akanework.gramophone.ui.components.library.ExpressiveScrollBar
import org.akanework.gramophone.ui.components.library.LibrarySortBottomSheet
import org.akanework.gramophone.ui.components.library.LibrarySortOption
import org.akanework.gramophone.ui.components.library.MultiSelectionBottomSheet
import org.akanework.gramophone.ui.components.library.SongActionBottomSheet
import org.akanework.gramophone.ui.theme.DynamicArtworkTheme

class AlbumFragment : Fragment() {

    private var albumId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        albumId = arguments?.getString("ALBUM_ID")
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val composeView = ComposeView(requireContext())
        composeView.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnLifecycleDestroyed(viewLifecycleOwner))
        composeView.setContent {
            val colorScheme = getThemeColorScheme()
            MaterialTheme(colorScheme = colorScheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = colorScheme.surface
                ) {
                    AlbumDetailScreen(
                        albumId = albumId ?: "",
                        onBackClick = { requireActivity().onBackPressed() }
                    )
                }
            }
        }
        return composeView
    }

    private fun getThemeColorScheme(): ColorScheme {
        val context = requireContext()
        val darkTheme = resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK == android.content.res.Configuration.UI_MODE_NIGHT_YES
        val hasDynamicColor = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
        return when {
            hasDynamicColor && darkTheme -> dynamicDarkColorScheme(context)
            hasDynamicColor && !darkTheme -> dynamicLightColorScheme(context)
            darkTheme -> darkColorScheme()
            else -> lightColorScheme()
        }
    }

    companion object {
        fun newInstance(albumId: String): AlbumFragment {
            val fragment = AlbumFragment()
            val args = Bundle()
            args.putString("ALBUM_ID", albumId)
            fragment.arguments = args
            return fragment
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumDetailScreen(
    albumId: String,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? MainActivity
    val coroutineScope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val isDarkTheme = isSystemInDarkTheme()

    var album by remember { mutableStateOf<Album?>(null) }
    val tracks = remember { mutableStateListOf<Track>() }
    var isLoading by remember { mutableStateOf(true) }
    var isLiked by remember { mutableStateOf(false) }

    // Переключение вкладок: 0 - Треки, 1 - Информация
    var selectedTabIndex by remember { mutableIntStateOf(0) }

    // Стейты Пакетного Выбора (Multi-Select)
    var isSelectionMode by remember { mutableStateOf(false) }
    val selectedTrackIds = remember { mutableStateListOf<String>() }

    // Стейты Поиска и Сортировки
    var isSearchExpanded by remember { mutableStateOf(false) }
    var filterQuery by remember { mutableStateOf("") }
    var currentSortOption by remember { mutableStateOf(LibrarySortOption.NEWEST) }
    var showSortSheet by remember { mutableStateOf(false) }

    // BottomSheet детальных действий над треком (3 точки)
    var selectedTrackForMenu by remember { mutableStateOf<Track?>(null) }
    var showTrackActionSheet by remember { mutableStateOf(false) }
    var showDownloadProgressSheet by remember { mutableStateOf(false) }

    // Состояние текущего трека в плеере
    var currentPlayingTrackId by remember { mutableStateOf<String?>(null) }
    var currentPlayingTitle by remember { mutableStateOf<String?>(null) }
    var currentPlayingArtist by remember { mutableStateOf<String?>(null) }
    var isPlaying by remember { mutableStateOf(false) }

    // FLAC СТАТУСЫ
    val albumStatuses by org.akanework.gramophone.logic.lossless.FlacDownloadManager.albumStatuses.collectAsState()
    val flacStatus = albumStatuses[albumId]
    val isAllFlac = (tracks.isNotEmpty() && tracks.all { org.akanework.gramophone.logic.lossless.LosslessStateManager.isTrackLossless(it.id, it.is_lossless) })
        || flacStatus?.hasFlac == true
        || flacStatus?.isComplete == true
        || (flacStatus?.percent ?: 0f) >= 100f
        || (flacStatus != null && flacStatus.totalTracks > 0 && flacStatus.flacTracks >= flacStatus.totalTracks)
    val isFlacDownloading = !isAllFlac && (org.akanework.gramophone.logic.lossless.FlacDownloadManager.isAlbumDownloading(albumId)
        || ((flacStatus?.percent ?: 0f) > 0f && (flacStatus?.percent ?: 0f) < 100f && flacStatus?.hasFlac != true && flacStatus?.isComplete != true))
    val flacPercent = flacStatus?.percent ?: 0f

    // ДИНАМИЧЕСКИЕ ЦВЕТА ИЗ ОБЛОЖКИ
    var dynamicColors by remember { mutableStateOf<DynamicArtworkTheme.ArtworkColors?>(null) }
    val defaultSurface = MaterialTheme.colorScheme.surface
    val defaultSurfaceContainer = MaterialTheme.colorScheme.surfaceContainerLowest

    val coverUrl = album?.cover?.let {
        if (it.startsWith("/")) "http://185.196.41.31$it" else it
    }

    LaunchedEffect(coverUrl, isDarkTheme) {
        if (!coverUrl.isNullOrEmpty()) {
            withContext(Dispatchers.IO) {
                try {
                    val request = ImageRequest.Builder(context)
                        .data(coverUrl)
                        .allowHardware(false)
                        .build()
                    val result = context.imageLoader.execute(request)
                    if (result is SuccessResult) {
                        val bitmap = (result.image as? BitmapDrawable)?.bitmap
                        if (bitmap != null) {
                            androidx.palette.graphics.Palette.from(bitmap).generate { palette ->
                                dynamicColors = DynamicArtworkTheme.calculateFromPalette(
                                    palette = palette,
                                    isDarkTheme = isDarkTheme,
                                    defaultSurface = defaultSurface,
                                    defaultSurfaceContainer = defaultSurfaceContainer
                                )
                            }
                        }
                    }
                } catch (_: Exception) {}
            }
        } else {
            dynamicColors = null
        }
    }

    val animatedBgTop by animateColorAsState(
        targetValue = dynamicColors?.fullPlayerGradientTop ?: MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
        animationSpec = tween(500),
        label = "albumBgTop"
    )
    val animatedBgGlow by animateColorAsState(
        targetValue = dynamicColors?.fullPlayerSecondaryGlow ?: MaterialTheme.colorScheme.surfaceContainerLow,
        animationSpec = tween(500),
        label = "albumBgGlow"
    )

    val listState = rememberLazyListState()

    // Непрерывная синхронизация с плеером
    LaunchedEffect(activity) {
        while (true) {
            val player = activity?.getPlayer()
            if (player != null) {
                val mediaItem = player.currentMediaItem
                currentPlayingTrackId = mediaItem?.mediaId
                currentPlayingTitle = mediaItem?.mediaMetadata?.title?.toString()
                currentPlayingArtist = mediaItem?.mediaMetadata?.artist?.toString()
                isPlaying = player.isPlaying
            }
            kotlinx.coroutines.delay(400)
        }
    }

    fun isTrackMatching(track: Track, playingId: String?, playingTitle: String?, playingArtist: String?): Boolean {
        if (!playingId.isNullOrBlank()) {
            if (track.id == playingId) return true
            if (!track.sourceId.isNullOrBlank() && track.sourceId == playingId) return true
            if (playingId.startsWith("deezer_") && track.id == playingId.removePrefix("deezer_")) return true
        }
        if (!playingTitle.isNullOrBlank() && !playingArtist.isNullOrBlank()) {
            val titleMatch = track.title.trim().equals(playingTitle.trim(), ignoreCase = true)
            val artistMatch = track.artist.trim().equals(playingArtist.trim(), ignoreCase = true) ||
                    track.artist.contains(playingArtist, ignoreCase = true) ||
                    playingArtist.contains(track.artist, ignoreCase = true)
            if (titleMatch && artistMatch) return true
        }
        return false
    }

    fun loadAlbumData() {
        if (albumId.isBlank()) return
        coroutineScope.launch(Dispatchers.IO) {
            try {
                val response = NetworkClient.getApi(context).getAlbumPage(albumId).execute()
                if (response.isSuccessful && response.body() != null) {
                    val loadedAlbum = response.body()!!
                    withContext(Dispatchers.Main) {
                        album = loadedAlbum
                        isLiked = loadedAlbum.isLiked
                        tracks.clear()
                        val loadedTracks = loadedAlbum.tracks ?: emptyList()
                        tracks.addAll(loadedTracks)
                        if (loadedTracks.isNotEmpty()) {
                            val losslessList = loadedTracks.filter { it.is_lossless }.map { it.id }
                            org.akanework.gramophone.logic.lossless.LosslessStateManager.markLossless(context, losslessList)
                        }
                    }
                } else {
                    android.util.Log.e("GramoDebug", "Album error: ${response.code()} ${response.message()}")
                }
            } catch (e: Exception) {
                android.util.Log.e("GramoDebug", "Album fetch exception", e)
            }
            withContext(Dispatchers.Main) { isLoading = false }
        }
    }

    LaunchedEffect(albumId) {
        loadAlbumData()
        org.akanework.gramophone.logic.lossless.FlacDownloadManager.fetchAlbumStatus(context, albumId)
    }

    fun toggleLike() {
        coroutineScope.launch(Dispatchers.IO) {
            try {
                val response = NetworkClient.getApi(context).toggleAlbumLike(albumId).execute()
                if (response.isSuccessful) {
                    val status = response.body()?.get("status")
                    withContext(Dispatchers.Main) {
                        if (status == "liked") {
                            isLiked = true
                            album?.isLiked = true
                            Toast.makeText(context, "Альбом добавлен в любимые", Toast.LENGTH_SHORT).show()
                        } else {
                            isLiked = false
                            album?.isLiked = false
                            Toast.makeText(context, "Альбом удален из любимых", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { Toast.makeText(context, "Ошибка сети", Toast.LENGTH_SHORT).show() }
            }
        }
    }

    fun applySort(option: LibrarySortOption) {
        currentSortOption = option
        when (option) {
            LibrarySortOption.TITLE_AZ -> tracks.sortBy { it.title.lowercase() }
            LibrarySortOption.ARTIST_AZ -> tracks.sortBy { it.artist.lowercase() }
            LibrarySortOption.NEWEST -> loadAlbumData()
            LibrarySortOption.OLDEST -> tracks.reverse()
        }
    }

    fun playTrackList(shuffle: Boolean = false, startIndex: Int = 0) {
        val currentList = tracks.toList()
        if (currentList.isEmpty()) return
        val player = activity?.getPlayer() ?: return
        val currentAlbum = album

        val mediaItems = currentList.map { item ->
            val streamUrl = "http://185.196.41.31/stream/${item.id}"
            val coverUri = currentAlbum?.cover?.let {
                (if (it.startsWith("/")) "http://185.196.41.31$it" else it).toUri()
            }
            MediaItem.Builder()
                .setMediaId(item.id)
                .setUri(streamUrl.toUri())
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(item.title)
                        .setArtist(item.artist)
                        .setAlbumTitle(currentAlbum?.title ?: item.album)
                        .setArtworkUri(coverUri)
                        .setExtras(Bundle().apply {
                            putLong("DURATION", item.duration.toLong())
                            putBoolean("IS_LOSSLESS", item.is_lossless)
                            putString("ARTIST_ID", item.artistId ?: currentAlbum?.artistId)
                            putString("ALBUM_ID", item.albumId ?: currentAlbum?.id)
                            putString("PLAYING_FROM", "Альбом: ${currentAlbum?.title}")
                        })
                        .build()
                )
                .build()
        }

        if (shuffle) {
            org.akanework.gramophone.logic.utils.ShuffleUtils.playWithSmartShuffle(player, mediaItems)
        } else {
            player.shuffleModeEnabled = false
            player.setMediaItems(mediaItems, startIndex, 0)
            player.prepare()
            player.play()
        }
    }

    fun addTrackToQueueNext(track: Track) {
        val player = activity?.getPlayer() ?: return
        val streamUrl = "http://185.196.41.31/stream/${track.id}"
        val coverUri = album?.cover?.let {
            (if (it.startsWith("/")) "http://185.196.41.31$it" else it).toUri()
        }
        val mediaItem = MediaItem.Builder()
            .setMediaId(track.id)
            .setUri(streamUrl.toUri())
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(track.title)
                    .setArtist(track.artist)
                    .setAlbumTitle(album?.title ?: track.album)
                    .setArtworkUri(coverUri)
                    .setExtras(Bundle().apply {
                        putLong("DURATION", track.duration.toLong())
                        putBoolean("IS_LOSSLESS", track.is_lossless)
                        putString("PLAYING_FROM", "Очередь (${album?.title})")
                    })
                    .build()
            )
            .build()

        val insertIndex = if (player.mediaItemCount > 0) player.currentMediaItemIndex + 1 else 0
        player.addMediaItem(insertIndex, mediaItem)
        Toast.makeText(context, "Добавлено в очередь следующим", Toast.LENGTH_SHORT).show()
    }

    val filteredTracks = remember(tracks.toList(), filterQuery) {
        if (filterQuery.isBlank()) tracks
        else tracks.filter {
            it.title.contains(filterQuery, ignoreCase = true) ||
                    it.artist.contains(filterQuery, ignoreCase = true)
        }
    }

    val totalDurationSeconds = remember(tracks.toList()) {
        tracks.sumOf { it.duration.toLong() }
    }
    val formattedDuration = remember(totalDurationSeconds) {
        val minutes = totalDurationSeconds / 60
        if (minutes >= 60) {
            val hours = minutes / 60
            val remMinutes = minutes % 60
            "$hours ч $remMinutes мин"
        } else {
            "$minutes мин"
        }
    }

    if (showSortSheet) {
        LibrarySortBottomSheet(
            selectedOption = currentSortOption,
            onOptionSelected = { option ->
                applySort(option)
                showSortSheet = false
            },
            onDismiss = { showSortSheet = false }
        )
    }

    // 3-Dots Action BottomSheet for Track
    if (showTrackActionSheet && selectedTrackForMenu != null) {
        val activeTrack = selectedTrackForMenu!!
        SongActionBottomSheet(
            track = activeTrack,
            onPlayNext = {
                showTrackActionSheet = false
                addTrackToQueueNext(activeTrack)
            },
            onAddToQueue = {
                showTrackActionSheet = false
                val p = activity?.getPlayer()
                if (p != null) {
                    val streamUrl = "http://185.196.41.31/stream/${activeTrack.id}"
                    val coverUri = (activeTrack.cover ?: album?.cover)?.let {
                        (if (it.startsWith("/")) "http://185.196.41.31$it" else it).toUri()
                    }
                    val mediaItem = MediaItem.Builder()
                        .setMediaId(activeTrack.id)
                        .setUri(streamUrl.toUri())
                        .setMediaMetadata(
                            MediaMetadata.Builder()
                                .setTitle(activeTrack.title)
                                .setArtist(activeTrack.artist)
                                .setAlbumTitle(album?.title ?: activeTrack.album)
                                .setArtworkUri(coverUri)
                                .build()
                        ).build()
                    p.addMediaItem(mediaItem)
                    Toast.makeText(context, "Добавлено в конец очереди", Toast.LENGTH_SHORT).show()
                }
            },
            onAddToPlaylist = {
                showTrackActionSheet = false
                val trackId = activeTrack.id.toIntOrNull()
                if (trackId != null && activity != null) {
                    val sheet = AddToPlaylistBottomSheet.newInstance(trackId)
                    sheet.show(activity.supportFragmentManager, "ADD_TO_PLAYLIST_SHEET")
                }
            },
            onGoToArtist = {
                showTrackActionSheet = false
                (activeTrack.artistId ?: album?.artistId)?.let { id ->
                    activity?.startFragment(ArtistFragment.newInstance(id))
                } ?: Toast.makeText(context, "Исполнитель неизвестен", Toast.LENGTH_SHORT).show()
            },
            onGoToAlbum = {
                showTrackActionSheet = false
            },
            onDismiss = {
                showTrackActionSheet = false
                selectedTrackForMenu = null
            }
        )
    }

    val dynamicGradientBrush = Brush.verticalGradient(
        colors = listOf(
            animatedBgTop,
            animatedBgGlow,
            MaterialTheme.colorScheme.surface
        )
    )

    // Непрозрачный базовый Box с поверхностью темы
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .background(brush = dynamicGradientBrush)
    ) {
        if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        } else {
            val firstVisibleIndex = remember { derivedStateOf { listState.firstVisibleItemIndex } }
            val firstVisibleOffset = remember { derivedStateOf { listState.firstVisibleItemScrollOffset } }

            val headerAlpha by animateFloatAsState(if (firstVisibleIndex.value > 0) 0f else (1f - (firstVisibleOffset.value / 600f)).coerceIn(0f, 1f))
            val headerScale by animateFloatAsState(if (firstVisibleIndex.value > 0) 0.86f else (1f - (firstVisibleOffset.value / 1500f)).coerceIn(0.86f, 1f))

            val loadedAlbum = album

            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 260.dp)
            ) {
                // 1. ШАПКА АЛЬБОМА (КРУПНАЯ ОБЛОЖКА 250dp + ДИНАМИЧЕСКИЙ ГРАДИЕНТ)
                item(key = "album_hero_header") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 70.dp, start = 20.dp, end = 20.dp, bottom = 24.dp)
                            .graphicsLayer {
                                alpha = headerAlpha
                                scaleX = headerScale
                                scaleY = headerScale
                                translationY = firstVisibleOffset.value * 0.38f
                            },
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // БОЛЬШАЯ ОБЛОЖКА АЛЬБОМА (250dp)
                        Card(
                            modifier = Modifier.size(250.dp),
                            shape = RoundedCornerShape(32.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                        ) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                if (!coverUrl.isNullOrBlank()) {
                                    AsyncImage(
                                        model = coverUrl,
                                        contentDescription = loadedAlbum?.title,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    Surface(
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        modifier = Modifier.fillMaxSize()
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                painter = painterResource(id = R.drawable.ic_library),
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(72.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        // НАЗВАНИЕ АЛЬБОМА
                        Text(
                            text = loadedAlbum?.title ?: "Альбом",
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontWeight = FontWeight.Black,
                                letterSpacing = (-0.5).sp
                            ),
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = 16.dp),
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        // ИСПОЛНИТЕЛЬ И ГОД
                        val type = loadedAlbum?.recordType?.let {
                            if (it.equals("ep", ignoreCase = true)) "EP" else it.replaceFirstChar { char -> char.uppercase() }
                        } ?: "Релиз"
                        val artist = loadedAlbum?.artistName ?: "Исполнитель"
                        val origYear = loadedAlbum?.info?.releaseDate?.take(4)?.toIntOrNull()
                        val catalogYear = loadedAlbum?.releaseYear
                        val displayYear = origYear?.toString() ?: catalogYear?.toString() ?: ""
                        val subtitle = listOf(type, artist, displayYear).filter { it.isNotBlank() }.joinToString(" • ")

                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .padding(horizontal = 16.dp)
                                .clickable {
                                    loadedAlbum?.artistId?.let { id ->
                                        activity?.startFragment(ArtistFragment.newInstance(id))
                                    }
                                },
                            textAlign = TextAlign.Center
                        )

                        // СТАТИСТИКА И ЛАЙК
                        Row(
                            modifier = Modifier.padding(top = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.7f)
                            ) {
                                Text(
                                    text = "${tracks.size} треков • $formattedDuration",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }

                            FilledTonalIconButton(
                                onClick = { toggleLike() },
                                modifier = Modifier.size(36.dp),
                                shape = CircleShape,
                                colors = if (isLiked) IconButtonDefaults.filledTonalIconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                    contentColor = MaterialTheme.colorScheme.primary
                                ) else IconButtonDefaults.filledTonalIconButtonColors()
                            ) {
                                Icon(
                                    painter = painterResource(if (isLiked) R.drawable.ic_favorite_filled else R.drawable.ic_favorite),
                                    contentDescription = "Любимый альбом",
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        // ПАНЕЛЬ ДЕЙСТВИЙ (СЛУШАТЬ / ПЕРЕМЕШАТЬ / СКАЧАТЬ FLAC / ПЕРЕЙТИ К АРТИСТУ)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 20.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // 1. Главная акцентная кнопка «Слушать»
                            Button(
                                onClick = { playTrackList(shuffle = false) },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(52.dp),
                                shape = RoundedCornerShape(18.dp),
                                contentPadding = PaddingValues(horizontal = 14.dp)
                            ) {
                                Icon(painterResource(R.drawable.ic_play), contentDescription = "Play", modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Слушать",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            // 2. Компактная кнопка «Перемешать»
                            FilledTonalIconButton(
                                onClick = { playTrackList(shuffle = true) },
                                modifier = Modifier.size(52.dp),
                                shape = RoundedCornerShape(18.dp),
                                colors = IconButtonDefaults.filledTonalIconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                                )
                            ) {
                                Icon(painterResource(R.drawable.ic_shuffle), contentDescription = "Перемешать", modifier = Modifier.size(20.dp))
                            }

                            // 3. Кнопка «Скачать FLAC» / Индикатор FLAC
                            FilledTonalIconButton(
                                onClick = {
                                    if (!isAllFlac && !isFlacDownloading) {
                                        org.akanework.gramophone.logic.lossless.FlacDownloadManager.downloadAlbum(
                                            context = context,
                                            albumId = albumId,
                                            albumTitle = loadedAlbum?.title ?: "Альбом",
                                            tracks = tracks
                                        )
                                    }
                                    showDownloadProgressSheet = true
                                },
                                modifier = Modifier.size(52.dp),
                                shape = RoundedCornerShape(18.dp),
                                colors = IconButtonDefaults.filledTonalIconButtonColors(
                                    containerColor = if (isAllFlac) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                                    contentColor = if (isAllFlac) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            ) {
                                when {
                                    isFlacDownloading -> {
                                        Box(contentAlignment = Alignment.Center) {
                                            CircularProgressIndicator(
                                                progress = { flacPercent / 100f },
                                                modifier = Modifier.size(32.dp),
                                                strokeWidth = 2.5.dp,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                            Text(
                                                text = "${flacPercent.toInt()}%",
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Black
                                                ),
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                    isAllFlac -> {
                                        Icon(
                                            painter = painterResource(R.drawable.ic_check_circle),
                                            contentDescription = "FLAC Lossless",
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                    else -> {
                                        Icon(
                                            painter = painterResource(R.drawable.ic_download),
                                            contentDescription = "Скачать FLAC",
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                }
                            }

                            // 4. Кнопка «Артист» (если есть)
                            loadedAlbum?.artistId?.let { aId ->
                                FilledTonalIconButton(
                                    onClick = { activity?.startFragment(ArtistFragment.newInstance(aId)) },
                                    modifier = Modifier.size(52.dp),
                                    shape = RoundedCornerShape(18.dp),
                                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                                    )
                                ) {
                                    Icon(painterResource(R.drawable.ic_person), contentDescription = "Артист", modifier = Modifier.size(20.dp))
                                }
                            }
                        }
                    }
                }

                // 2. СКРУГЛЕННЫЙ КОНТЕЙНЕР СО ВКЛАДКАМИ (ТРЕКИ / ИНФОРМАЦИЯ)
                item(key = "album_tabs_and_controls") {
                    Surface(
                        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        tonalElevation = 2.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 16.dp, start = 16.dp, end = 16.dp, bottom = 8.dp)
                        ) {
                            // Вкладки в стиле медиатеки (Pill Tabs)
                            LazyRow(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                val tabs = listOf("Треки (${tracks.size})", "Информация")
                                itemsIndexed(tabs) { index, title ->
                                    val isSelected = selectedTabIndex == index
                                    val tabContainerColor by animateColorAsState(
                                        targetValue = if (isSelected) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.6f),
                                        animationSpec = tween(durationMillis = 200),
                                        label = "pillTabColor"
                                    )
                                    val tabContentColor by animateColorAsState(
                                        targetValue = if (isSelected) MaterialTheme.colorScheme.onPrimary
                                        else MaterialTheme.colorScheme.onSurfaceVariant,
                                        animationSpec = tween(durationMillis = 200),
                                        label = "pillTabTextColor"
                                    )

                                    Surface(
                                        shape = CircleShape,
                                        color = tabContainerColor,
                                        modifier = Modifier
                                            .clip(CircleShape)
                                            .clickable {
                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                selectedTabIndex = index
                                            }
                                    ) {
                                        Text(
                                            text = title,
                                            style = MaterialTheme.typography.titleSmall.copy(
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                fontSize = 13.5.sp
                                            ),
                                            color = tabContentColor,
                                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            // Если открыта вкладка "ТРЕКИ": строка поиска и сортировка
                            if (selectedTabIndex == 0) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = if (filterQuery.isBlank()) "Список треков" else "Найдено (${filteredTracks.size})",
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )

                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        // Кнопка поиска
                                        FilledTonalIconButton(
                                            onClick = { isSearchExpanded = !isSearchExpanded },
                                            modifier = Modifier.size(38.dp),
                                            shape = CircleShape,
                                            colors = if (isSearchExpanded || filterQuery.isNotEmpty()) IconButtonDefaults.filledTonalIconButtonColors(
                                                containerColor = MaterialTheme.colorScheme.primary,
                                                contentColor = MaterialTheme.colorScheme.onPrimary
                                            ) else IconButtonDefaults.filledTonalIconButtonColors()
                                        ) {
                                            Icon(painterResource(R.drawable.ic_search), contentDescription = "Поиск", modifier = Modifier.size(18.dp))
                                        }

                                        // Кнопка сортировки
                                        FilledTonalIconButton(
                                            onClick = { showSortSheet = true },
                                            modifier = Modifier.size(38.dp),
                                            shape = CircleShape
                                        ) {
                                            Icon(imageVector = Icons.AutoMirrored.Rounded.Sort, contentDescription = "Сортировка", modifier = Modifier.size(18.dp))
                                        }
                                    }
                                }

                                // Анимированное поле поиска внутри контейнера
                                AnimatedVisibility(visible = isSearchExpanded) {
                                    Surface(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 10.dp),
                                        shape = RoundedCornerShape(16.dp),
                                        color = MaterialTheme.colorScheme.surfaceContainerHigh
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 12.dp, vertical = 2.dp)
                                        ) {
                                            Icon(
                                                painter = painterResource(id = R.drawable.ic_search),
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            TextField(
                                                value = filterQuery,
                                                onValueChange = { filterQuery = it },
                                                placeholder = { Text("Поиск по альбому...", style = MaterialTheme.typography.bodyMedium) },
                                                singleLine = true,
                                                colors = TextFieldDefaults.colors(
                                                    focusedContainerColor = Color.Transparent,
                                                    unfocusedContainerColor = Color.Transparent,
                                                    focusedIndicatorColor = Color.Transparent,
                                                    unfocusedIndicatorColor = Color.Transparent
                                                ),
                                                modifier = Modifier.weight(1f)
                                            )
                                            if (filterQuery.isNotEmpty()) {
                                                IconButton(
                                                    onClick = { filterQuery = "" },
                                                    modifier = Modifier.size(28.dp)
                                                ) {
                                                    Icon(imageVector = Icons.Rounded.Close, contentDescription = "Очистить", modifier = Modifier.size(16.dp))
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // 3. СОДЕРЖИМОЕ В ЗАВИСИМОСТИ ОТ ВКЛАДКИ
                if (selectedTabIndex == 0) {
                    // ВКЛАДКА "ТРЕКИ" (С РАЗДЕЛЕНИЕМ НА ДИСКИ)
                    if (filteredTracks.isEmpty()) {
                        item(key = "album_tracks_empty_state") {
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(40.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = if (filterQuery.isBlank()) "В этом альбоме нет треков" else "Ничего не найдено",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    } else {
                        val discsMap = filteredTracks.groupBy { it.discNumber ?: 1 }
                        val hasMultipleDiscs = discsMap.keys.size > 1

                        discsMap.toSortedMap().forEach { (discNum, discTracks) ->
                            if (hasMultipleDiscs) {
                                item(key = "disc_header_$discNum") {
                                    Surface(
                                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 20.dp, vertical = 12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.Album,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = "Диск $discNum",
                                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                            Spacer(modifier = Modifier.width(12.dp))
                                            HorizontalDivider(
                                                modifier = Modifier.weight(1f),
                                                color = MaterialTheme.colorScheme.surfaceContainerHighest
                                            )
                                        }
                                    }
                                }
                            }

                            itemsIndexed(discTracks, key = { index, track -> "${track.id}_${discNum}_$index" }) { _, track ->
                                val globalIndex = tracks.indexOfFirst { it.id == track.id }.coerceAtLeast(0)
                                val isCurrentTrack = isTrackMatching(track, currentPlayingTrackId, currentPlayingTitle, currentPlayingArtist)
                                val isSelected = selectedTrackIds.contains(track.id)

                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 3.dp)
                                            .animateItem()
                                    ) {
                                        EnhancedSongListItem(
                                            track = track,
                                            isPlaying = isPlaying,
                                            isCurrentTrack = isCurrentTrack,
                                            isSelected = isSelected,
                                            isSelectionMode = isSelectionMode,
                                            selectionIndex = if (isSelected) selectedTrackIds.indexOf(track.id) + 1 else null,
                                            onTrackClick = {
                                                if (isSelectionMode) {
                                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    if (isSelected) selectedTrackIds.remove(track.id)
                                                    else selectedTrackIds.add(track.id)
                                                    if (selectedTrackIds.isEmpty()) isSelectionMode = false
                                                } else {
                                                    playTrackList(shuffle = false, startIndex = globalIndex)
                                                }
                                            },
                                            onLongPress = {
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                if (!isSelectionMode) {
                                                    isSelectionMode = true
                                                    selectedTrackIds.add(track.id)
                                                } else {
                                                    if (isSelected) selectedTrackIds.remove(track.id)
                                                    else selectedTrackIds.add(track.id)
                                                    if (selectedTrackIds.isEmpty()) isSelectionMode = false
                                                }
                                            },
                                            onMoreClick = {
                                                selectedTrackForMenu = track
                                                showTrackActionSheet = true
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // ВКЛАДКА "ИНФОРМАЦИЯ" (ОБОГАЩЕННЫЙ СТОРИТЕЛЛИНГ И BENTO СЕТКА)
                    item(key = "album_info_tab_content") {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            AlbumRichInfoContent(
                                album = loadedAlbum,
                                tracks = tracks,
                                formattedDuration = formattedDuration,
                                onArtistClick = {
                                    loadedAlbum?.artistId?.let { id ->
                                        activity?.startFragment(ArtistFragment.newInstance(id))
                                    }
                                }
                            )
                        }
                    }
                }

                // Завершающая подложка контейнера
                item(key = "album_bottom_spacer") {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                    ) {}
                }
            }

            // Быстрый скроллбар (только на вкладке треков)
            if (selectedTabIndex == 0) {
                ExpressiveScrollBar(
                    listState = listState,
                    itemCount = filteredTracks.size,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(top = 100.dp, bottom = 120.dp, end = 2.dp)
                )
            }
        }

        // ВЕРХНИЙ ТУЛБАР
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilledTonalIconButton(
                onClick = onBackClick,
                modifier = Modifier.size(46.dp),
                shape = CircleShape
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "Назад",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        // MULTI-SELECTION BOTTOM BAR
        AnimatedVisibility(
            visible = isSelectionMode && selectedTabIndex == 0,
            enter = slideInVertically { it },
            exit = slideOutVertically { it },
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            MultiSelectionBottomSheet(
                selectedCount = selectedTrackIds.size,
                isAllSelected = selectedTrackIds.size == tracks.size && tracks.isNotEmpty(),
                onSelectAllToggle = {
                    if (selectedTrackIds.size == tracks.size) {
                        selectedTrackIds.clear()
                        isSelectionMode = false
                    } else {
                        selectedTrackIds.clear()
                        selectedTrackIds.addAll(tracks.map { it.id })
                    }
                },
                onPlayNext = {
                    val selectedTracks = tracks.filter { it.id in selectedTrackIds }
                    selectedTracks.forEach { addTrackToQueueNext(it) }
                    isSelectionMode = false
                    selectedTrackIds.clear()
                },
                onAddToQueue = {
                    val selectedTracks = tracks.filter { it.id in selectedTrackIds }
                    val p = activity?.getPlayer()
                    if (p != null) {
                        selectedTracks.forEach { t ->
                            val streamUrl = "http://185.196.41.31/stream/${t.id}"
                            val coverUri = (t.cover ?: album?.cover)?.let {
                                (if (it.startsWith("/")) "http://185.196.41.31$it" else it).toUri()
                            }
                            val mediaItem = MediaItem.Builder()
                                .setMediaId(t.id)
                                .setUri(streamUrl.toUri())
                                .setMediaMetadata(
                                    MediaMetadata.Builder()
                                        .setTitle(t.title)
                                        .setArtist(t.artist)
                                        .setAlbumTitle(album?.title ?: t.album)
                                        .setArtworkUri(coverUri)
                                        .build()
                                ).build()
                            p.addMediaItem(mediaItem)
                        }
                        Toast.makeText(context, "Добавлено в очередь: ${selectedTracks.size}", Toast.LENGTH_SHORT).show()
                    }
                    isSelectionMode = false
                    selectedTrackIds.clear()
                },
                onAddToPlaylist = {
                    val firstTrackId = selectedTrackIds.firstOrNull()?.toIntOrNull()
                    if (firstTrackId != null && activity != null) {
                        val sheet = AddToPlaylistBottomSheet.newInstance(firstTrackId)
                        sheet.show(activity.supportFragmentManager, "ADD_TO_PLAYLIST_SHEET")
                    }
                    isSelectionMode = false
                    selectedTrackIds.clear()
                },
                onClearSelection = {
                    isSelectionMode = false
                    selectedTrackIds.clear()
                }
            )
        }

        if (showDownloadProgressSheet) {
            val currentAlbumTitle = album?.title ?: "Альбом"
            val totalCount = flacStatus?.totalTracks?.takeIf { it > 0 } ?: tracks.size
            val flacCount = flacStatus?.flacTracks ?: if (isAllFlac) tracks.size else tracks.count { org.akanework.gramophone.logic.lossless.LosslessStateManager.isTrackLossless(it.id, it.is_lossless) }
            val currentPercent = if (isAllFlac) 100f else flacPercent

            org.akanework.gramophone.ui.components.lossless.DownloadProgressBottomSheet(
                title = currentAlbumTitle,
                totalTracks = totalCount,
                flacTracks = flacCount,
                percent = currentPercent,
                tracks = tracks,
                onDismiss = { showDownloadProgressSheet = false }
            )
        }
    }
}

@Composable
fun AlbumInfoBentoCard(
    title: String,
    value: String,
    icon: ImageVector,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun AlbumRichInfoContent(
    album: Album?,
    tracks: List<Track>,
    formattedDuration: String,
    onArtistClick: () -> Unit
) {
    val context = LocalContext.current
    val info = album?.info
    var isOverviewExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. КАРТОЧКА ИСПОЛНИТЕЛЯ
        album?.let { alb ->
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onArtistClick() }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                painter = painterResource(R.drawable.ic_person),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Исполнитель",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = alb.artistName,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Icon(
                        imageVector = Icons.Rounded.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // 2. ИСТОРИЯ И КОНТЕКСТ СОЗДАНИЯ (ОБЗОР РЕЛИЗА)
        val overviewText = info?.overview
        if (!overviewText.isNullOrBlank()) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp)
                        .animateContentSize()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Rounded.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "О релизе",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                        }

                        if (overviewText.length > 200) {
                            TextButton(
                                onClick = { isOverviewExpanded = !isOverviewExpanded },
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                            ) {
                                Text(
                                    text = if (isOverviewExpanded) "Свернуть" else "Подробнее",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = overviewText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 22.sp,
                        maxLines = if (isOverviewExpanded) Int.MAX_VALUE else 5,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        // 3. ПРОДЮСИРОВАНИЕ И ЗАПИСЬ (BENTO CREDITS)
        val producers = info?.producers
        val studios = info?.studios
        val label = info?.label
        val releaseDate = info?.releaseDate

        if (!producers.isNullOrEmpty() || !studios.isNullOrEmpty() || !label.isNullOrBlank() || !releaseDate.isNullOrBlank()) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        text = "Создание и продакшн",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    if (!producers.isNullOrEmpty()) {
                        AlbumCreditRow(
                            label = "Продюсирование",
                            value = producers.joinToString(", "),
                            icon = Icons.Rounded.Headphones
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    if (!studios.isNullOrEmpty()) {
                        AlbumCreditRow(
                            label = "Студия звукозаписи",
                            value = studios.joinToString(", "),
                            icon = Icons.Rounded.Mic
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    if (!label.isNullOrBlank()) {
                        AlbumCreditRow(
                            label = "Лейбл",
                            value = label,
                            icon = Icons.Rounded.Album
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    if (!releaseDate.isNullOrBlank()) {
                        AlbumCreditRow(
                            label = "Дата релиза",
                            value = releaseDate,
                            icon = Icons.Rounded.CalendarToday
                        )
                    }
                }
            }
        }

        // 4. КОНЦЕПЦИЯ И ТЕМЫ ТЕКСТОВ
        info?.conceptThemes?.takeIf { it.isNotBlank() }?.let { themes ->
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.MenuBook,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Концепция и темы текстов",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = themes,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 22.sp
                    )
                }
            }
        }

        // 5. ИСТОРИЯ ОБЛОЖКИ И ВИЗУАЛЬНЫЙ СТИЛЬ
        info?.coverStory?.takeIf { it.isNotBlank() }?.let { coverStory ->
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.Palette,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Обложка и визуальный стиль",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = coverStory,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 22.sp
                    )
                }
            }
        }

        // 6. ПРИЁМ КРИТИКОВ И НАГРАДЫ
        info?.receptionAwards?.takeIf { it.isNotBlank() }?.let { awards ->
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.EmojiEvents,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Приём критиков и награды",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = awards,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 22.sp
                    )
                }
            }
        }

        // 7. СТАНДАРТНАЯ BENTO-СЕТКА ТЕХНИЧЕСКИХ ПАРАМЕТРОВ
        album?.let { alb ->
            val origYear = info?.releaseDate?.take(4)?.toIntOrNull()
            val catalogYear = alb.releaseYear
            val yearDisplay = when {
                origYear != null && catalogYear != null && origYear != catalogYear -> "$origYear (Ремастер: $catalogYear)"
                origYear != null -> "$origYear"
                catalogYear != null -> "$catalogYear"
                else -> "Не указан"
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                AlbumInfoBentoCard(
                    title = "Год выпуска",
                    value = yearDisplay,
                    icon = Icons.Rounded.CalendarToday,
                    modifier = Modifier.weight(1f)
                )
                AlbumInfoBentoCard(
                    title = "Тип релиза",
                    value = when (alb.recordType?.lowercase()) {
                        "ep" -> "EP (Мини-альбом)"
                        "single" -> "Сингл"
                        else -> "Студийный альбом"
                    },
                    icon = Icons.Rounded.Album,
                    modifier = Modifier.weight(1f)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                AlbumInfoBentoCard(
                    title = "Количество треков",
                    value = "${tracks.size} композиций",
                    icon = Icons.Rounded.MusicNote,
                    modifier = Modifier.weight(1f)
                )
                AlbumInfoBentoCard(
                    title = "Общее время",
                    value = formattedDuration,
                    icon = Icons.Rounded.AccessTime,
                    modifier = Modifier.weight(1f)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                AlbumInfoBentoCard(
                    title = "Качество звука",
                    value = if (tracks.any { it.is_lossless }) "Lossless / Hi-Fi FLAC" else "320 kbps MP3",
                    icon = Icons.Rounded.HighQuality,
                    modifier = Modifier.weight(1f)
                )
                AlbumInfoBentoCard(
                    title = "Источник",
                    value = "Salvation Cloud",
                    icon = Icons.Rounded.CloudDone,
                    modifier = Modifier.weight(1f)
                )
            }

            // Кнопка поделиться альбомом
            FilledTonalButton(
                onClick = {
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, "Слушайте альбом «${alb.title}» исполнителя ${alb.artistName} в Salvation!")
                    }
                    context.startActivity(Intent.createChooser(shareIntent, "Поделиться альбомом"))
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(painterResource(R.drawable.ic_share), contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Поделиться альбомом", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun AlbumCreditRow(
    label: String,
    value: String,
    icon: ImageVector
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(36.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}