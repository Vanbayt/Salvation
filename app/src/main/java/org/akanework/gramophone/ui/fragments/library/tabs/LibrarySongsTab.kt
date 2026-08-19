package org.akanework.gramophone.ui.fragments.library.tabs

import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.fragment.app.FragmentActivity
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.akanework.gramophone.logic.LibraryCacheManager
import org.akanework.gramophone.logic.api.NetworkClient
import org.akanework.gramophone.logic.api.Track
import org.akanework.gramophone.ui.MainActivity
import org.akanework.gramophone.ui.components.library.EnhancedSongListItem
import org.akanework.gramophone.ui.components.library.ExpressiveScrollBar
import org.akanework.gramophone.ui.components.library.LibraryActionRow
import org.akanework.gramophone.ui.components.library.LibraryEmptyState
import org.akanework.gramophone.ui.components.library.LibrarySortBottomSheet
import org.akanework.gramophone.ui.components.library.LibrarySortOption
import org.akanework.gramophone.ui.components.library.LibraryTabType
import org.akanework.gramophone.ui.components.library.MultiSelectionBottomSheet
import org.akanework.gramophone.ui.components.library.SongActionBottomSheet
import org.akanework.gramophone.ui.fragments.AddToPlaylistBottomSheet
import org.akanework.gramophone.ui.fragments.AlbumFragment
import org.akanework.gramophone.ui.fragments.ArtistFragment

private const val PAGE_LIMIT = 50
private const val PRELOAD_THRESHOLD = 10

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibrarySongsTab(
    searchQuery: String = "",
    bottomPadding: PaddingValues = PaddingValues(0.dp),
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = context as? MainActivity
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()

    val tracks = remember { mutableStateListOf<Track>() }
    var isLoading by remember { mutableStateOf(false) }
    var isRefreshing by remember { mutableStateOf(false) }
    var isLastPage by remember { mutableStateOf(false) }
    var totalTrackCount by remember { mutableIntStateOf(0) }
    var currentSortOption by remember { mutableStateOf(LibrarySortOption.NEWEST) }
    var showSortSheet by remember { mutableStateOf(false) }

    // Multi-Select состояние
    val selectedTrackIds = remember { mutableStateListOf<String>() }
    var isSelectionMode by remember { mutableStateOf(false) }

    // BottomSheet детальных действий над треком (3 точки)
    var selectedTrackForMenu by remember { mutableStateOf<Track?>(null) }
    var showTrackActionSheet by remember { mutableStateOf(false) }

    // Состояние текущего трека в плеере
    var currentPlayingTrackId by remember { mutableStateOf<String?>(null) }
    var isPlaying by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()

    // Подписка на плеер
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
            onDispose {
                player.removeListener(listener)
            }
        } else {
            onDispose {}
        }
    }

    fun playTrack(track: Track, shuffle: Boolean = false) {
        val player = activity?.getPlayer() ?: return
        val currentList = tracks.toList()
        if (currentList.isEmpty()) return

        val mediaItems = currentList.map { item ->
            val streamUrl = "http://185.196.41.31/stream/${item.id}"
            val coverUri = item.cover?.let {
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
                        })
                        .build()
                )
                .build()
        }

        val clickedIndex = currentList.indexOfFirst { it.id == track.id }.coerceAtLeast(0)

        if (shuffle) {
            player.shuffleModeEnabled = true
            player.setMediaItems(mediaItems, clickedIndex, 0)
        } else {
            player.shuffleModeEnabled = false
            player.setMediaItems(mediaItems, clickedIndex, 0)
        }
        player.prepare()
        player.play()
    }

    fun addTrackToQueueNext(track: Track) {
        val player = activity?.getPlayer() ?: return
        val streamUrl = "http://185.196.41.31/stream/${track.id}"
        val coverUri = track.cover?.let {
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
                    })
                    .build()
            )
            .build()

        val insertIndex = if (player.mediaItemCount > 0) player.currentMediaItemIndex + 1 else 0
        player.addMediaItem(insertIndex, mediaItem)
        Toast.makeText(context, "Добавлено в очередь следующим", Toast.LENGTH_SHORT).show()
    }

    fun addTrackToQueueEnd(track: Track) {
        val player = activity?.getPlayer() ?: return
        val streamUrl = "http://185.196.41.31/stream/${track.id}"
        val coverUri = track.cover?.let {
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
                    })
                    .build()
            )
            .build()

        player.addMediaItem(mediaItem)
        Toast.makeText(context, "Добавлено в конец очереди", Toast.LENGTH_SHORT).show()
    }

    fun loadTracks(isInitial: Boolean = false, refresh: Boolean = false) {
        if (isLoading) return
        isLoading = true
        if (refresh) isRefreshing = true

        coroutineScope.launch(Dispatchers.IO) {
            val skip = if (isInitial || refresh) 0 else tracks.size

            if (isInitial && searchQuery.isBlank()) {
                val cached = LibraryCacheManager.loadCachedTracks(context, currentSortOption.key)
                if (cached.isNotEmpty()) {
                    withContext(Dispatchers.Main) {
                        tracks.clear()
                        tracks.addAll(cached)
                        totalTrackCount = cached.size
                    }
                }
            }

            try {
                val queryParam = searchQuery.ifBlank { null }
                val response = NetworkClient.getApi(context).getFavorites(
                    skip = skip,
                    limit = PAGE_LIMIT,
                    query = queryParam,
                    sortMode = currentSortOption.key
                ).execute()

                if (response.isSuccessful) {
                    val fetched = response.body() ?: emptyList()
                    val totalHeader = response.headers()["X-Total-Count"]?.toIntOrNull()

                    withContext(Dispatchers.Main) {
                        if (isInitial || refresh) {
                            tracks.clear()
                            tracks.addAll(fetched)
                        } else {
                            tracks.addAll(fetched)
                        }

                        if (totalHeader != null) {
                            totalTrackCount = totalHeader
                        } else if (isInitial || refresh) {
                            totalTrackCount = tracks.size
                        }

                        isLastPage = fetched.size < PAGE_LIMIT

                        if ((isInitial || refresh) && searchQuery.isBlank()) {
                            LibraryCacheManager.saveCachedTracks(context, currentSortOption.key, fetched)
                        }
                    }
                }
            } catch (e: Exception) {
                // Ignore network errors gracefully
            } finally {
                withContext(Dispatchers.Main) {
                    isLoading = false
                    isRefreshing = false
                }
            }
        }
    }

    LaunchedEffect(currentSortOption, searchQuery) {
        isLastPage = false
        loadTracks(isInitial = true)
    }

    // Авто-подгрузка страниц при скролле
    val shouldLoadMore by remember {
        derivedStateOf {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            !isLoading && !isLastPage && lastVisible >= (tracks.size - PRELOAD_THRESHOLD)
        }
    }

    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) {
            loadTracks(isInitial = false)
        }
    }

    val currentSongListIndex by remember(tracks.size, currentPlayingTrackId) {
        derivedStateOf {
            if (currentPlayingTrackId == null) -1
            else tracks.indexOfFirst { it.id == currentPlayingTrackId }
        }
    }

    fun formatTrackCountLabel(count: Int): String {
        val word = when {
            count % 10 == 1 && count % 100 != 11 -> "трек"
            count % 10 in 2..4 && (count % 100 < 10 || count % 100 >= 20) -> "трека"
            else -> "треков"
        }
        return "$count $word"
    }

    val pullToRefreshState = rememberPullToRefreshState()

    Box(modifier = modifier.fillMaxSize()) {
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = { loadTracks(refresh = true) },
            state = pullToRefreshState,
            modifier = Modifier.fillMaxSize()
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Тонкий индикатор фоновой загрузки
                AnimatedVisibility(
                    visible = isLoading && !isRefreshing,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.dp),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                }

                if (tracks.isEmpty() && !isLoading) {
                    LibraryEmptyState(
                        type = LibraryTabType.SONGS,
                        onActionClick = { loadTracks(refresh = true) }
                    )
                } else {
                    Box(modifier = Modifier.fillMaxSize()) {
                        LazyColumn(
                            state = listState,
                            contentPadding = PaddingValues(
                                start = 14.dp,
                                end = 14.dp,
                                top = 14.dp,
                                bottom = bottomPadding.calculateBottomPadding() + 140.dp
                            ),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            // Верхний скроллируемый бар действий (Shuffle, Sort, Locate, Track count)
                            item(key = "library_action_row") {
                                LibraryActionRow(
                                    onShuffleClick = {
                                        if (tracks.isNotEmpty()) {
                                            playTrack(tracks.random(), shuffle = true)
                                        }
                                    },
                                    onSortClick = { showSortSheet = true },
                                    onLocateClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        if (currentSongListIndex >= 0) {
                                            coroutineScope.launch {
                                                listState.animateScrollToItem((currentSongListIndex + 1).coerceAtLeast(0))
                                            }
                                        } else {
                                            Toast.makeText(context, "Трек воспроизводится вне текущего списка", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    showLocateButton = currentPlayingTrackId != null,
                                    itemCount = totalTrackCount,
                                    itemCountLabel = formatTrackCountLabel(totalTrackCount)
                                )
                            }

                            itemsIndexed(
                                items = tracks,
                                key = { _, track -> track.id },
                                contentType = { _, _ -> "song_item" }
                            ) { index, track ->
                                val isSelected = selectedTrackIds.contains(track.id)
                                val selectionIndex = if (isSelected) selectedTrackIds.indexOf(track.id) + 1 else null
                                val isCurrentTrack = track.id == currentPlayingTrackId

                                EnhancedSongListItem(
                                    track = track,
                                    isPlaying = isCurrentTrack && isPlaying,
                                    isCurrentTrack = isCurrentTrack,
                                    isSelected = isSelected,
                                    isSelectionMode = isSelectionMode,
                                    selectionIndex = selectionIndex,
                                    onTrackClick = {
                                        if (isSelectionMode) {
                                            if (isSelected) selectedTrackIds.remove(track.id)
                                            else selectedTrackIds.add(track.id)
                                            if (selectedTrackIds.isEmpty()) isSelectionMode = false
                                        } else {
                                            playTrack(track, shuffle = false)
                                        }
                                    },
                                    onLongPress = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        isSelectionMode = true
                                        if (isSelected) selectedTrackIds.remove(track.id)
                                        else selectedTrackIds.add(track.id)
                                    },
                                    onMoreClick = {
                                        selectedTrackForMenu = track
                                        showTrackActionSheet = true
                                    }
                                )
                            }

                            if (isLoading && tracks.isNotEmpty()) {
                                item {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        CircularProgressIndicator(strokeWidth = 2.dp)
                                    }
                                }
                            }
                        }

                        // Экспрессивный алфавитный быстрый скроллбар
                        ExpressiveScrollBar(
                            listState = listState,
                            itemCount = tracks.size,
                            labelProvider = { index ->
                                val track = tracks.getOrNull(index)
                                when (currentSortOption) {
                                    LibrarySortOption.ARTIST_AZ -> track?.artist?.firstOrNull()?.uppercase() ?: "#"
                                    else -> track?.title?.firstOrNull()?.uppercase() ?: "#"
                                }
                            },
                            modifier = Modifier.align(Alignment.CenterEnd)
                        )
                    }
                }
            }
        }

        // Material 3 Expressive BottomSheet действий с треком (3 точки)
        val currentTrackMenu = selectedTrackForMenu
        if (currentTrackMenu != null && showTrackActionSheet) {
            SongActionBottomSheet(
                track = currentTrackMenu,
                onPlayNext = { addTrackToQueueNext(currentTrackMenu) },
                onAddToQueue = { addTrackToQueueEnd(currentTrackMenu) },
                onAddToPlaylist = {
                    val trackIdInt = currentTrackMenu.id.toIntOrNull()
                    if (trackIdInt != null && activity != null) {
                        val sheet = AddToPlaylistBottomSheet.newInstance(trackIdInt)
                        sheet.show(activity.supportFragmentManager, "ADD_TO_PLAYLIST_SHEET")
                    } else {
                        Toast.makeText(context, "Некорректный ID трека", Toast.LENGTH_SHORT).show()
                    }
                },
                onGoToArtist = {
                    currentTrackMenu.artistId?.let { artistId ->
                        activity?.startFragment(ArtistFragment.newInstance(artistId))
                    } ?: Toast.makeText(context, "Исполнитель неизвестен", Toast.LENGTH_SHORT).show()
                },
                onGoToAlbum = {
                    currentTrackMenu.albumId?.let { albumId ->
                        activity?.startFragment(AlbumFragment.newInstance(albumId))
                    } ?: Toast.makeText(context, "Альбом неизвестен", Toast.LENGTH_SHORT).show()
                },
                onDismiss = { showTrackActionSheet = false }
            )
        }

        // Диалог сортировки
        if (showSortSheet) {
            LibrarySortBottomSheet(
                selectedOption = currentSortOption,
                onOptionSelected = { option ->
                    currentSortOption = option
                    showSortSheet = false
                },
                onDismiss = { showSortSheet = false }
            )
        }

        // Панель Multi-Select
        if (isSelectionMode && selectedTrackIds.isNotEmpty()) {
            MultiSelectionBottomSheet(
                selectedCount = selectedTrackIds.size,
                isAllSelected = selectedTrackIds.size == tracks.size,
                onSelectAllToggle = {
                    if (selectedTrackIds.size == tracks.size) {
                        selectedTrackIds.clear()
                    } else {
                        selectedTrackIds.clear()
                        selectedTrackIds.addAll(tracks.map { it.id })
                    }
                },
                onPlayNext = {
                    val selectedTracks = tracks.filter { selectedTrackIds.contains(it.id) }
                    selectedTracks.reversed().forEach { addTrackToQueueNext(it) }
                    selectedTrackIds.clear()
                    isSelectionMode = false
                },
                onAddToQueue = {
                    val selectedTracks = tracks.filter { selectedTrackIds.contains(it.id) }
                    val player = activity?.getPlayer()
                    if (player != null) {
                        val mediaItems = selectedTracks.map { item ->
                            val streamUrl = "http://185.196.41.31/stream/${item.id}"
                            val coverUri = item.cover?.let {
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
                                        })
                                        .build()
                                )
                                .build()
                        }
                        player.addMediaItems(mediaItems)
                        Toast.makeText(context, "Добавлено в очередь (${selectedTracks.size})", Toast.LENGTH_SHORT).show()
                    }
                    selectedTrackIds.clear()
                    isSelectionMode = false
                },
                onAddToPlaylist = {
                    selectedTrackIds.clear()
                    isSelectionMode = false
                },
                onClearSelection = {
                    selectedTrackIds.clear()
                    isSelectionMode = false
                },
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }
}
