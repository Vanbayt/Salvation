package org.akanework.gramophone.ui.fragments

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
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
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

class ArtistFragment : Fragment() {

    private var artistId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        artistId = arguments?.getString("ARTIST_ID")
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
                    ArtistDetailScreen(
                        artistId = artistId ?: "",
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
        fun newInstance(artistId: String): ArtistFragment {
            val fragment = ArtistFragment()
            val args = Bundle()
            args.putString("ARTIST_ID", artistId)
            fragment.arguments = args
            return fragment
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArtistDetailScreen(
    artistId: String,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? MainActivity
    val coroutineScope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current

    var artist by remember { mutableStateOf<Artist?>(null) }
    val topTracks = remember { mutableStateListOf<Track>() }
    val albums = remember { mutableStateListOf<Album>() }
    var isLoading by remember { mutableStateOf(true) }
    var isLiked by remember { mutableStateOf(false) }

    // Стейты ограничения количества популярных треков
    var showAllTracks by remember { mutableStateOf(false) }

    // Вкладки: 0 - Популярные треки, 1 - Релизы, 2 - Об артисте
    var selectedTabIndex by remember { mutableIntStateOf(0) }

    // Стейты Пакетного Выбора (Multi-Select)
    var isSelectionMode by remember { mutableStateOf(false) }
    val selectedTrackIds = remember { mutableStateListOf<String>() }

    // Стейты Поиска и Сортировки треков
    var isSearchExpanded by remember { mutableStateOf(false) }
    var filterQuery by remember { mutableStateOf("") }
    var currentSortOption by remember { mutableStateOf(LibrarySortOption.NEWEST) }
    var showSortSheet by remember { mutableStateOf(false) }

    // BottomSheet действий над треком (3 точки)
    var selectedTrackForMenu by remember { mutableStateOf<Track?>(null) }
    var showTrackActionSheet by remember { mutableStateOf(false) }

    // Состояние плеера
    var currentPlayingTrackId by remember { mutableStateOf<String?>(null) }
    var isPlaying by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()

    DisposableEffect(activity) {
        val player = activity?.getPlayer()
        if (player != null) {
            currentPlayingTrackId = player.currentMediaItem?.mediaId
            isPlaying = player.isPlaying

            val listener = object : Player.Listener {
                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                    currentPlayingTrackId = mediaItem?.mediaId
                }

                override fun onIsPlayingChanged(playing: Boolean) {
                    isPlaying = playing
                }
            }
            player.addListener(listener)
            onDispose { player.removeListener(listener) }
        } else {
            onDispose {}
        }
    }

    fun loadArtistData() {
        if (artistId.isBlank()) return
        coroutineScope.launch(Dispatchers.IO) {
            try {
                val response = NetworkClient.getApi(context).getArtistPage(artistId).execute()
                if (response.isSuccessful && response.body() != null) {
                    val loadedArtist = response.body()!!
                    withContext(Dispatchers.Main) {
                        artist = loadedArtist
                        isLiked = loadedArtist.isLiked
                        topTracks.clear()
                        loadedArtist.topTracks?.let { topTracks.addAll(it) }
                            ?: loadedArtist.tracks?.let { topTracks.addAll(it) }

                        albums.clear()
                        loadedArtist.albums?.let { albums.addAll(it) }
                    }
                } else {
                    android.util.Log.e("GramoDebug", "Artist error: ${response.code()} ${response.message()}")
                }
            } catch (e: Exception) {
                android.util.Log.e("GramoDebug", "Artist fetch exception", e)
            }
            withContext(Dispatchers.Main) { isLoading = false }
        }
    }

    LaunchedEffect(artistId) { loadArtistData() }

    fun toggleLike() {
        coroutineScope.launch(Dispatchers.IO) {
            try {
                val response = NetworkClient.getApi(context).toggleArtistLike(artistId).execute()
                if (response.isSuccessful) {
                    val status = response.body()?.get("status")
                    withContext(Dispatchers.Main) {
                        if (status == "liked") {
                            isLiked = true
                            artist?.isLiked = true
                            Toast.makeText(context, "Артист добавлен в избранное", Toast.LENGTH_SHORT).show()
                        } else {
                            isLiked = false
                            artist?.isLiked = false
                            Toast.makeText(context, "Артист удален из избранного", Toast.LENGTH_SHORT).show()
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
            LibrarySortOption.TITLE_AZ -> topTracks.sortBy { it.title.lowercase() }
            LibrarySortOption.ARTIST_AZ -> topTracks.sortBy { it.artist.lowercase() }
            LibrarySortOption.NEWEST -> loadArtistData()
            LibrarySortOption.OLDEST -> topTracks.reverse()
        }
    }

    fun playTrackList(shuffle: Boolean = false, startIndex: Int = 0) {
        val currentList = topTracks.toList()
        if (currentList.isEmpty()) return
        val player = activity?.getPlayer() ?: return
        val currentArtist = artist

        val mediaItems = currentList.map { item ->
            val streamUrl = "http://185.196.41.31/stream/${item.id}"
            val coverUri = (item.cover ?: currentArtist?.cover)?.let {
                (if (it.startsWith("/")) "http://185.196.41.31$it" else it).toUri()
            }
            MediaItem.Builder()
                .setMediaId(item.id)
                .setUri(streamUrl.toUri())
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(item.title)
                        .setArtist(item.artist)
                        .setAlbumTitle(item.album)
                        .setArtworkUri(coverUri)
                        .setExtras(Bundle().apply {
                            putLong("DURATION", item.duration.toLong())
                            putBoolean("IS_LOSSLESS", item.is_lossless)
                            putString("ARTIST_ID", item.artistId ?: currentArtist?.id)
                            putString("ALBUM_ID", item.albumId ?: "")
                            putString("PLAYING_FROM", "Артист: ${currentArtist?.name}")
                        })
                        .build()
                )
                .build()
        }

        if (shuffle) {
            player.shuffleModeEnabled = true
            player.setMediaItems(mediaItems, startIndex, 0)
        } else {
            player.shuffleModeEnabled = false
            player.setMediaItems(mediaItems, startIndex, 0)
        }
        player.prepare()
        player.play()
    }

    fun addTrackToQueueNext(track: Track) {
        val player = activity?.getPlayer() ?: return
        val streamUrl = "http://185.196.41.31/stream/${track.id}"
        val coverUri = (track.cover ?: artist?.cover)?.let {
            (if (it.startsWith("/")) "http://185.196.41.31$it" else it).toUri()
        }
        val mediaItem = MediaItem.Builder()
            .setMediaId(track.id)
            .setUri(streamUrl.toUri())
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(track.title)
                    .setArtist(track.artist)
                    .setAlbumTitle(track.album)
                    .setArtworkUri(coverUri)
                    .setExtras(Bundle().apply {
                        putLong("DURATION", track.duration.toLong())
                        putBoolean("IS_LOSSLESS", track.is_lossless)
                        putString("PLAYING_FROM", "Очередь (${artist?.name})")
                    })
                    .build()
            )
            .build()

        val insertIndex = if (player.mediaItemCount > 0) player.currentMediaItemIndex + 1 else 0
        player.addMediaItem(insertIndex, mediaItem)
        Toast.makeText(context, "Добавлено в очередь следующим", Toast.LENGTH_SHORT).show()
    }

    val filteredTracks = if (filterQuery.isBlank()) {
        topTracks
    } else {
        topTracks.filter {
            it.title.contains(filterQuery, ignoreCase = true) ||
                    it.artist.contains(filterQuery, ignoreCase = true)
        }
    }

    val displayedTracks = if (filterQuery.isNotBlank() || showAllTracks) {
        filteredTracks
    } else {
        filteredTracks.take(5)
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
                    val coverUri = (activeTrack.cover ?: artist?.cover)?.let {
                        (if (it.startsWith("/")) "http://185.196.41.31$it" else it).toUri()
                    }
                    val mediaItem = MediaItem.Builder()
                        .setMediaId(activeTrack.id)
                        .setUri(streamUrl.toUri())
                        .setMediaMetadata(
                            MediaMetadata.Builder()
                                .setTitle(activeTrack.title)
                                .setArtist(activeTrack.artist)
                                .setAlbumTitle(activeTrack.album)
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
            },
            onGoToAlbum = {
                showTrackActionSheet = false
                activeTrack.albumId?.let { id ->
                    activity?.startFragment(AlbumFragment.newInstance(id))
                } ?: Toast.makeText(context, "Альбом неизвестен", Toast.LENGTH_SHORT).show()
            },
            onDismiss = {
                showTrackActionSheet = false
                selectedTrackForMenu = null
            }
        )
    }

    // Чистый Material 3 фон (БЕЗ ГРАДИЕНТОВ)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        } else {
            val firstVisibleIndex = remember { derivedStateOf { listState.firstVisibleItemIndex } }
            val firstVisibleOffset = remember { derivedStateOf { listState.firstVisibleItemScrollOffset } }

            val headerAlpha by animateFloatAsState(if (firstVisibleIndex.value > 0) 0f else (1f - (firstVisibleOffset.value / 600f)).coerceIn(0f, 1f))
            val headerScale by animateFloatAsState(if (firstVisibleIndex.value > 0) 0.86f else (1f - (firstVisibleOffset.value / 1500f)).coerceIn(0.86f, 1f))

            val loadedArtist = artist
            val artistCover = loadedArtist?.cover?.let {
                if (it.startsWith("/")) "http://185.196.41.31$it" else it
            }

            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 260.dp)
            ) {
                // 1. ШАПКА АРТИСТА (КВАДРАТНАЯ ОБЛОЖКА 220dp СО СКРУГЛЕНИЕМ 32dp)
                item {
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
                        // КВАДРАТНАЯ АВАТАРКА АРТИСТА СО СКРУГЛЕНИЕМ
                        Card(
                            modifier = Modifier.size(220.dp),
                            shape = RoundedCornerShape(32.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                        ) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                if (!artistCover.isNullOrBlank()) {
                                    AsyncImage(
                                        model = artistCover,
                                        contentDescription = loadedArtist?.name,
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
                                                painter = painterResource(id = R.drawable.ic_person),
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

                        // ИМЯ АРТИСТА
                        Text(
                            text = loadedArtist?.name ?: "Исполнитель",
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontWeight = FontWeight.Black,
                                letterSpacing = (-0.5).sp
                            ),
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = 16.dp),
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
                                    text = "Исполнитель • ${albums.size} релизов",
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
                                    contentDescription = "Любимый артист",
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        // ПАНЕЛЬ ДЕЙСТВИЙ (СЛУШАТЬ / ПЕРЕМЕШАТЬ / ДИСКОГРАФИЯ)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 20.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = { playTrackList(shuffle = false) },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(52.dp),
                                shape = RoundedCornerShape(18.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp)
                            ) {
                                Icon(painterResource(R.drawable.ic_play), contentDescription = "Play", modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Слушать",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            FilledTonalButton(
                                onClick = { playTrackList(shuffle = true) },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(52.dp),
                                shape = RoundedCornerShape(18.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp)
                            ) {
                                Icon(painterResource(R.drawable.ic_shuffle), contentDescription = "Shuffle", modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Перемешать",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            FilledTonalIconButton(
                                onClick = {
                                    activity?.startFragment(DiscographyFragment.newInstance(artistId))
                                },
                                modifier = Modifier.size(52.dp),
                                shape = RoundedCornerShape(18.dp)
                            ) {
                                Icon(painterResource(R.drawable.ic_library), contentDescription = "Дискография", modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }

                // 2. СКРУГЛЕННЫЙ КОНТЕЙНЕР СО ВКЛАДКАМИ В СТИЛЕ МЕДИАТЕКИ (PILL TABS)
                item {
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
                                val tabs = listOf("Популярное", "Релизы (${albums.size})", "Инфо")
                                itemsIndexed(tabs) { index, title ->
                                    val isSelected = selectedTabIndex == index
                                    val tabContainerColor by animateColorAsState(
                                        targetValue = if (isSelected) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.6f),
                                        animationSpec = tween(durationMillis = 200),
                                        label = "artistPillTabColor"
                                    )
                                    val tabContentColor by animateColorAsState(
                                        targetValue = if (isSelected) MaterialTheme.colorScheme.onPrimary
                                        else MaterialTheme.colorScheme.onSurfaceVariant,
                                        animationSpec = tween(durationMillis = 200),
                                        label = "artistPillTabTextColor"
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

                            // Если открыта вкладка "ПОПУЛЯРНОЕ": строка поиска и сортировка
                            if (selectedTabIndex == 0) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = if (filterQuery.isBlank()) "Популярные треки" else "Найдено (${filteredTracks.size})",
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )

                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
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

                                        FilledTonalIconButton(
                                            onClick = { showSortSheet = true },
                                            modifier = Modifier.size(38.dp),
                                            shape = CircleShape
                                        ) {
                                            Icon(imageVector = Icons.AutoMirrored.Rounded.Sort, contentDescription = "Сортировка", modifier = Modifier.size(18.dp))
                                        }
                                    }
                                }

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
                                                placeholder = { Text("Поиск трека...", style = MaterialTheme.typography.bodyMedium) },
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

                // 3. СОДЕРЖИМОЕ ВКЛАДОК
                if (selectedTabIndex == 0) {
                    // ВКЛАДКА "ПОПУЛЯРНЫЕ ТРЕКИ" (ЛАКОНИЧНЫЙ ТОП-5 С ВОЗМОЖНОСТЬЮ РАЗВЕРНУТЬ)
                    if (displayedTracks.isEmpty()) {
                        item {
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
                                        text = if (filterQuery.isBlank()) "У артиста пока нет треков" else "Ничего не найдено",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    } else {
                        itemsIndexed(displayedTracks, key = { index, track -> "${track.id}_$index" }) { index, track ->
                            val isCurrentTrack = track.id == currentPlayingTrackId
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
                                                playTrackList(shuffle = false, startIndex = index)
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

                        // Кнопка развернуть / свернуть все треки
                        if (filteredTracks.size > 5 && filterQuery.isBlank()) {
                            item {
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 8.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        FilledTonalButton(
                                            onClick = { showAllTracks = !showAllTracks },
                                            shape = RoundedCornerShape(14.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text(
                                                text = if (showAllTracks) "Свернуть до Топ-5" else "Показать все треки (${filteredTracks.size})",
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else if (selectedTabIndex == 1) {
                    // ВКЛАДКА "РЕЛИЗЫ" (ТОП-4 СВЕЖИХ РЕЛИЗА + КНОПКА ДИСКОГРАФИИ)
                    if (albums.isEmpty()) {
                        item {
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
                                    Text("Релизы не найдены", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    } else {
                        item {
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 8.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    val topAlbums = albums.take(4)
                                    topAlbums.forEach { albumItem ->
                                        Surface(
                                            shape = RoundedCornerShape(20.dp),
                                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    activity?.startFragment(AlbumFragment.newInstance(albumItem.id))
                                                }
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(12.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                val albCover = albumItem.cover?.let {
                                                    if (it.startsWith("/")) "http://185.196.41.31$it" else it
                                                }
                                                AsyncImage(
                                                    model = albCover,
                                                    contentDescription = albumItem.title,
                                                    contentScale = ContentScale.Crop,
                                                    modifier = Modifier
                                                        .size(64.dp)
                                                        .clip(RoundedCornerShape(16.dp))
                                                )

                                                Spacer(modifier = Modifier.width(14.dp))

                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = albumItem.title,
                                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                    Spacer(modifier = Modifier.height(2.dp))
                                                    val type = when (albumItem.recordType?.lowercase()) {
                                                        "ep" -> "EP"
                                                        "single" -> "Сингл"
                                                        else -> "Альбом"
                                                    }
                                                    val year = albumItem.releaseYear?.toString() ?: ""
                                                    Text(
                                                        text = listOf(type, year).filter { it.isNotBlank() }.joinToString(" • "),
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
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

                                    Spacer(modifier = Modifier.height(6.dp))

                                    Button(
                                        onClick = {
                                            activity?.startFragment(DiscographyFragment.newInstance(artistId))
                                        },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(50.dp),
                                        shape = RoundedCornerShape(16.dp)
                                    ) {
                                        Text("Открыть полную дискографию (${albums.size})", fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // ВКЛАДКА "ИНФО / ОБ АРТИСТЕ"
                    item {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            ArtistRichInfoContent(
                                artist = loadedArtist,
                                topTracksCount = topTracks.size,
                                albumsCount = albums.size
                            )
                        }
                    }
                }

                // Завершающая подложка
                item {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                    ) {}
                }
            }

            // Быстрый скроллбар
            if (selectedTabIndex == 0) {
                ExpressiveScrollBar(
                    listState = listState,
                    itemCount = displayedTracks.size,
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
                isAllSelected = selectedTrackIds.size == topTracks.size && topTracks.isNotEmpty(),
                onSelectAllToggle = {
                    if (selectedTrackIds.size == topTracks.size) {
                        selectedTrackIds.clear()
                        isSelectionMode = false
                    } else {
                        selectedTrackIds.clear()
                        selectedTrackIds.addAll(topTracks.map { it.id })
                    }
                },
                onPlayNext = {
                    val selected = topTracks.filter { it.id in selectedTrackIds }
                    selected.forEach { addTrackToQueueNext(it) }
                    isSelectionMode = false
                    selectedTrackIds.clear()
                },
                onAddToQueue = {
                    val selected = topTracks.filter { it.id in selectedTrackIds }
                    val p = activity?.getPlayer()
                    if (p != null) {
                        selected.forEach { t ->
                            val streamUrl = "http://185.196.41.31/stream/${t.id}"
                            val coverUri = (t.cover ?: artist?.cover)?.let {
                                (if (it.startsWith("/")) "http://185.196.41.31$it" else it).toUri()
                            }
                            val mediaItem = MediaItem.Builder()
                                .setMediaId(t.id)
                                .setUri(streamUrl.toUri())
                                .setMediaMetadata(
                                    MediaMetadata.Builder()
                                        .setTitle(t.title)
                                        .setArtist(t.artist)
                                        .setAlbumTitle(t.album)
                                        .setArtworkUri(coverUri)
                                        .build()
                                ).build()
                            p.addMediaItem(mediaItem)
                        }
                        Toast.makeText(context, "Добавлено в очередь: ${selected.size}", Toast.LENGTH_SHORT).show()
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
    }
}

@Composable
fun ArtistInfoBentoCard(
    title: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ArtistRichInfoContent(
    artist: Artist?,
    topTracksCount: Int,
    albumsCount: Int
) {
    val info = artist?.info
    var isBioExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. БАЗОВАЯ BENTO-СЕТКА ФАКТОВ (ПРОИСХОЖДЕНИЕ, СТАТУС, РЕЛИЗЫ)
        if (info?.origin != null || info?.status != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                info.origin?.let { orig ->
                    ArtistInfoBentoCard(
                        title = "Происхождение",
                        value = orig,
                        icon = Icons.Rounded.Place,
                        modifier = Modifier.weight(1f)
                    )
                }
                info.status?.let { st ->
                    ArtistInfoBentoCard(
                        title = "Период активности",
                        value = st,
                        icon = Icons.Rounded.CalendarToday,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // 2. ЖАНРЫ (CHIPS)
        if (!info?.genres.isNullOrEmpty()) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Жанры и стили",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        info?.genres?.forEach { genre ->
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                            ) {
                                Text(
                                    text = genre,
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // 3. БИОГРАФИЯ И ИСТОРИЯ (РАЗВОРАЧИВАЕМАЯ)
        val biographyText = info?.biography ?: artist?.bio
        if (!biographyText.isNullOrBlank()) {
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
                                text = "Биография и история",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                        }

                        if (biographyText.length > 220) {
                            TextButton(
                                onClick = { isBioExpanded = !isBioExpanded },
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                            ) {
                                Text(
                                    text = if (isBioExpanded) "Свернуть" else "Подробнее",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = biographyText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 22.sp,
                        maxLines = if (isBioExpanded) Int.MAX_VALUE else 5,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        // 4. СОСТАВ ГРУППЫ (BAND LINEUP)
        val members = info?.members
        if (!members.isNullOrEmpty()) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.Groups,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Состав коллектива",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    }

                    val activeMembers = members.filter { it.status == "active" }
                    val formerMembers = members.filter { it.status == "former" }

                    if (activeMembers.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Текущий состав",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        activeMembers.forEach { member ->
                            ArtistMemberItem(member = member, isActive = true)
                            Spacer(modifier = Modifier.height(6.dp))
                        }
                    }

                    if (formerMembers.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "Бывшие участники",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        formerMembers.forEach { member ->
                            ArtistMemberItem(member = member, isActive = false)
                            Spacer(modifier = Modifier.height(6.dp))
                        }
                    }
                }
            }
        }

        // 5. КЛЮЧЕВЫЕ ВЕХИ КАРЬЕРЫ (MILESTONES TIMELINE)
        val milestones = info?.milestones
        if (!milestones.isNullOrEmpty()) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.Timeline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Ключевые вехи карьеры",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    milestones.forEach { milestone ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.Top
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.size(44.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = milestone.year.takeLast(2).let { "'$it" },
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Black),
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${milestone.year} • ${milestone.title}",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = milestone.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    lineHeight = 18.sp
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                    }
                }
            }
        }

        // 6. ИНТЕРЕСНЫЕ ФАКТЫ И КУЛЬТУРНЫЙ СЛЕД
        val facts = info?.facts
        if (!facts.isNullOrEmpty()) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.Lightbulb,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Интересные факты",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    facts.forEach { fact ->
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = fact.title,
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = fact.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    lineHeight = 18.sp
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
        }

        // 7. СТАТИСТИКА
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ArtistInfoBentoCard(
                title = "Популярных треков",
                value = "$topTracksCount",
                icon = Icons.Rounded.MusicNote,
                modifier = Modifier.weight(1f)
            )
            ArtistInfoBentoCard(
                title = "Всего релизов",
                value = "$albumsCount",
                icon = Icons.Rounded.Album,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
fun ArtistMemberItem(
    member: BandMember,
    isActive: Boolean
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (isActive) MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f)
        else MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.4f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = if (isActive) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
                modifier = Modifier.size(34.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.Person,
                        contentDescription = null,
                        tint = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = member.name,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = listOfNotNull(member.role, member.years).filter { it.isNotBlank() }.joinToString(" • "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}