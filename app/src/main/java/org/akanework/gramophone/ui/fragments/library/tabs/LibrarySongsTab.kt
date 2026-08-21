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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import org.akanework.gramophone.R
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
    var onlyFlacFilter by remember { mutableStateOf(false) }

    // Multi-Select состояние
    val selectedTrackIds = remember { mutableStateListOf<String>() }
    var isSelectionMode by remember { mutableStateOf(false) }

    // BottomSheet детальных действий над треком (3 точки)
    var selectedTrackForMenu by remember { mutableStateOf<Track?>(null) }
    var showTrackActionSheet by remember { mutableStateOf(false) }

    // Состояние текущего трека в плеере
    var currentPlayingTrackId by remember { mutableStateOf<String?>(null) }
    var currentPlayingTitle by remember { mutableStateOf<String?>(null) }
    var currentPlayingArtist by remember { mutableStateOf<String?>(null) }
    var isPlaying by remember { mutableStateOf(false) }

    fun isTrackMatching(track: Track, id: String?, title: String?, artist: String?): Boolean {
        if (!id.isNullOrBlank() && (track.id == id || id.endsWith("/${track.id}") || id.contains(track.id))) {
            return true
        }
        if (!title.isNullOrBlank()) {
            val titleMatch = track.title.trim().equals(title.trim(), ignoreCase = true)
            if (titleMatch) {
                if (artist.isNullOrBlank() || track.artist.trim().equals(artist.trim(), ignoreCase = true)) {
                    return true
                }
            }
        }
        return false
    }

    val listState = rememberLazyListState()

    suspend fun fastScrollToTrack(targetTrackIndex: Int) {
        val targetItemIndex = (targetTrackIndex + 1).coerceAtLeast(0)
        val firstVisible = listState.firstVisibleItemIndex
        val distance = kotlin.math.abs(targetItemIndex - firstVisible)
        if (distance > 8) {
            val jumpIndex = if (targetItemIndex > firstVisible) (targetItemIndex - 2).coerceAtLeast(0) else (targetItemIndex + 2).coerceAtLeast(0)
            listState.scrollToItem(jumpIndex)
        }
        listState.animateScrollToItem(targetItemIndex)
    }

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
            kotlinx.coroutines.delay(500)
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
                            putString("PLAYING_FROM", "Медиатека")
                        })
                        .build()
                )
                .build()
        }

        if (shuffle) {
            org.akanework.gramophone.logic.utils.ShuffleUtils.playWithSmartShuffle(player, mediaItems, track.id)
        } else {
            val clickedIndex = currentList.indexOfFirst { it.id == track.id }.coerceAtLeast(0)
            player.shuffleModeEnabled = false
            player.setMediaItems(mediaItems, clickedIndex, 0)
            player.prepare()
            player.play()
        }
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

                        if (fetched.isNotEmpty()) {
                            val losslessList = fetched.filter { it.is_lossless }.map { it.id }
                            org.akanework.gramophone.logic.lossless.LosslessStateManager.markLossless(context, losslessList)
                        }
                    }

                    // Фоновая предзагрузка всей медиатеки в ОЗУ для моментальной локации треков без сети
                    if ((isInitial || refresh) && searchQuery.isBlank() && (totalHeader ?: 0) > PAGE_LIMIT) {
                        launch(Dispatchers.IO) {
                            try {
                                val fullResp = NetworkClient.getApi(context).getFavorites(
                                    skip = 0,
                                    limit = 2000,
                                    query = null,
                                    sortMode = currentSortOption.key
                                ).execute()
                                if (fullResp.isSuccessful && !fullResp.body().isNullOrEmpty()) {
                                    LibraryCacheManager.setMemoryTracks(currentSortOption.key, fullResp.body()!!)
                                }
                            } catch (e: Exception) {
                                // Silent prefetch
                            }
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

    val currentSongListIndex by remember(tracks.size, currentPlayingTrackId, currentPlayingTitle, currentPlayingArtist) {
        derivedStateOf {
            tracks.indexOfFirst { isTrackMatching(it, currentPlayingTrackId, currentPlayingTitle, currentPlayingArtist) }
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
                    val memoryAllTracks = if (searchQuery.isBlank()) LibraryCacheManager.getMemoryTracks(currentSortOption.key) else null
                    val baseTracks = if (onlyFlacFilter && !memoryAllTracks.isNullOrEmpty()) memoryAllTracks else tracks
                    val displayedTracks = if (!onlyFlacFilter) tracks else baseTracks.filter {
                        org.akanework.gramophone.logic.lossless.LosslessStateManager.isTrackLossless(it.id, it.is_lossless)
                    }

                    Box(modifier = Modifier.fillMaxSize()) {
                        LazyColumn(
                            state = listState,
                            contentPadding = PaddingValues(
                                start = 14.dp,
                                end = 14.dp,
                                top = 14.dp,
                                bottom = bottomPadding.calculateBottomPadding() + 220.dp
                            ),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            // Верхний скроллируемый бар действий (Shuffle, Sort, Locate, Track count)
                            item(key = "library_action_row") {
                                val isTrackPlaying = currentPlayingTrackId != null || activity?.getPlayer()?.currentMediaItem != null

                                LibraryActionRow(
                                    onShuffleClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        val player = activity?.getPlayer() ?: return@LibraryActionRow
                                        if (tracks.isEmpty()) return@LibraryActionRow

                                        Toast.makeText(context, "Перемешивание медиатеки...", Toast.LENGTH_SHORT).show()
                                        coroutineScope.launch(Dispatchers.IO) {
                                            val fullList = if (tracks.size < totalTrackCount && totalTrackCount > 0) {
                                                try {
                                                    val resp = NetworkClient.getApi(context).getFavorites(
                                                        skip = 0,
                                                        limit = 2000,
                                                        query = searchQuery.ifBlank { null },
                                                        sortMode = currentSortOption.key
                                                    ).execute()
                                                    if (resp.isSuccessful && !resp.body().isNullOrEmpty()) {
                                                        resp.body()!!
                                                    } else {
                                                        tracks.toList()
                                                    }
                                                } catch (e: Exception) {
                                                    tracks.toList()
                                                }
                                            } else {
                                                tracks.toList()
                                            }

                                            if (fullList.isEmpty()) return@launch

                                            val mediaItems = fullList.map { item ->
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
                                                                putString("PLAYING_FROM", "Медиатека")
                                                            })
                                                            .build()
                                                    )
                                                    .build()
                                            }

                                            withContext(Dispatchers.Main) {
                                                org.akanework.gramophone.logic.utils.ShuffleUtils.playWithSmartShuffle(player, mediaItems)
                                            }
                                        }
                                    },
                                    onSortClick = { showSortSheet = true },
                                    onLocateClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        val player = activity?.getPlayer()
                                        val currentItem = player?.currentMediaItem
                                        val playingId = currentItem?.mediaId ?: currentPlayingTrackId
                                        val playingTitle = currentItem?.mediaMetadata?.title?.toString() ?: currentPlayingTitle
                                        val playingArtist = currentItem?.mediaMetadata?.artist?.toString() ?: currentPlayingArtist

                                        if (playingId.isNullOrBlank() && playingTitle.isNullOrBlank()) {
                                            Toast.makeText(context, "Сейчас ничего не играет", Toast.LENGTH_SHORT).show()
                                            return@LibraryActionRow
                                        }

                                        // 1. Сначала ищем среди уже загруженных в Compose треков (0 мс)
                                        val localIndex = tracks.indexOfFirst { isTrackMatching(it, playingId, playingTitle, playingArtist) }
                                        if (localIndex >= 0) {
                                            coroutineScope.launch {
                                                fastScrollToTrack(localIndex)
                                            }
                                            return@LibraryActionRow
                                        }

                                        // 2. Проверяем кэш в оперативной памяти (0 мс)
                                        val memCached = if (searchQuery.isBlank()) LibraryCacheManager.getMemoryTracks(currentSortOption.key) else null
                                        if (!memCached.isNullOrEmpty()) {
                                            val cachedIndex = memCached.indexOfFirst { isTrackMatching(it, playingId, playingTitle, playingArtist) }
                                            if (cachedIndex >= 0) {
                                                val existingIds = tracks.map { it.id }.toSet()
                                                val missing = memCached.filter { it.id !in existingIds }
                                                tracks.addAll(missing)

                                                val foundIndex = tracks.indexOfFirst { isTrackMatching(it, playingId, playingTitle, playingArtist) }
                                                if (foundIndex >= 0) {
                                                    coroutineScope.launch {
                                                        fastScrollToTrack(foundIndex)
                                                    }
                                                    return@LibraryActionRow
                                                }
                                            }
                                        }

                                        // 3. Если нет в ОЗУ — выполняем всего 1 быстрый пакетный сетевой запрос (limit = 2000)
                                        coroutineScope.launch {
                                            val newTracks = withContext(Dispatchers.IO) {
                                                try {
                                                    val response = NetworkClient.getApi(context).getFavorites(
                                                        skip = tracks.size,
                                                        limit = 2000,
                                                        query = searchQuery.ifBlank { null },
                                                        sortMode = currentSortOption.key
                                                    ).execute()
                                                    if (response.isSuccessful) response.body() ?: emptyList() else emptyList()
                                                } catch (e: Exception) {
                                                    emptyList()
                                                }
                                            }

                                            if (newTracks.isNotEmpty()) {
                                                val existingIds = tracks.map { it.id }.toSet()
                                                val uniqueNew = newTracks.filter { it.id !in existingIds }
                                                tracks.addAll(uniqueNew)
                                                if (searchQuery.isBlank()) {
                                                    LibraryCacheManager.setMemoryTracks(currentSortOption.key, tracks.toList())
                                                }
                                            }

                                            val foundIndex = tracks.indexOfFirst { isTrackMatching(it, playingId, playingTitle, playingArtist) }
                                            if (foundIndex >= 0) {
                                                fastScrollToTrack(foundIndex)
                                            } else {
                                                Toast.makeText(context, "Трек не найден в медиатеке", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    },
                                    showLocateButton = isTrackPlaying,
                                    itemCount = if (onlyFlacFilter) displayedTracks.size else totalTrackCount,
                                    itemCountLabel = if (onlyFlacFilter) formatTrackCountLabel(displayedTracks.size) else formatTrackCountLabel(totalTrackCount),
                                    isFilterActive = onlyFlacFilter
                                )
                            }

                            itemsIndexed(
                                items = displayedTracks,
                                key = { index, track -> "song_${track.id}_$index" },
                                contentType = { _, _ -> "song_item" }
                            ) { index, track ->
                                val isSelected = selectedTrackIds.contains(track.id)
                                val selectionIndex = if (isSelected) selectedTrackIds.indexOf(track.id) + 1 else null
                                val isCurrentTrack = isTrackMatching(track, currentPlayingTrackId, currentPlayingTitle, currentPlayingArtist)

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
                                item(key = "songs_loading_footer") {
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

                        // Экспрессивный алфавитный быстрый скроллбар с бесшовной поддержкой пагинации
                        val scrollBarItemCount = if (totalTrackCount > 0) totalTrackCount else tracks.size
                        ExpressiveScrollBar(
                            listState = listState,
                            itemCount = scrollBarItemCount,
                            bottomPadding = bottomPadding.calculateBottomPadding() + 200.dp,
                            labelProvider = { index ->
                                val track = tracks.getOrNull(index) ?: LibraryCacheManager.getMemoryTracks(currentSortOption.key)?.getOrNull(index)
                                if (track != null) {
                                    when (currentSortOption) {
                                        LibrarySortOption.ARTIST_AZ -> track.artist.firstOrNull()?.uppercase() ?: "#"
                                        else -> track.title.firstOrNull()?.uppercase() ?: "#"
                                    }
                                } else {
                                    "#"
                                }
                            },
                            onScrollToPosition = { targetIndex ->
                                coroutineScope.launch {
                                    if (targetIndex >= tracks.size) {
                                        val memCached = if (searchQuery.isBlank()) LibraryCacheManager.getMemoryTracks(currentSortOption.key) else null
                                        if (!memCached.isNullOrEmpty()) {
                                            val existingIds = tracks.map { it.id }.toSet()
                                            val missing = memCached.filter { it.id !in existingIds }
                                            if (missing.isNotEmpty()) {
                                                tracks.addAll(missing)
                                            }
                                        } else if (!isLoading && !isLastPage) {
                                            val newTracks = withContext(Dispatchers.IO) {
                                                try {
                                                    val resp = NetworkClient.getApi(context).getFavorites(
                                                        skip = tracks.size,
                                                        limit = 2000,
                                                        query = searchQuery.ifBlank { null },
                                                        sortMode = currentSortOption.key
                                                    ).execute()
                                                    if (resp.isSuccessful) resp.body() ?: emptyList() else emptyList()
                                                } catch (e: Exception) {
                                                    emptyList()
                                                }
                                            }
                                            if (newTracks.isNotEmpty()) {
                                                val existingIds = tracks.map { it.id }.toSet()
                                                tracks.addAll(newTracks.filter { it.id !in existingIds })
                                                if (searchQuery.isBlank()) {
                                                    LibraryCacheManager.setMemoryTracks(currentSortOption.key, tracks.toList())
                                                }
                                            }
                                        }
                                    }
                                    val validIndex = (targetIndex + 1).coerceIn(0, (tracks.size).coerceAtLeast(0))
                                    listState.scrollToItem(validIndex)
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
                onlyFlac = onlyFlacFilter,
                onOnlyFlacToggle = { onlyFlacFilter = it },
                showFlacFilter = true,
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
