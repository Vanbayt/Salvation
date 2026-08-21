package org.akanework.gramophone.ui.fragments

import android.content.Context
import android.graphics.drawable.BitmapDrawable
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
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
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
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
import java.io.File
import java.io.FileOutputStream

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistDetailScreen(
    playlist: Playlist,
    onBackClick: () -> Unit,
    onPlayClick: (List<Track>, startIndex: Int) -> Unit
) {
    val context = LocalContext.current
    val activity = context as? MainActivity
    val coroutineScope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val isDarkTheme = isSystemInDarkTheme()

    val tracks = remember { mutableStateListOf<Track>() }
    var originalTracks by remember { mutableStateOf<List<Track>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    // Стейты редактирования
    var isEditing by remember { mutableStateOf(false) }
    var editTitle by remember { mutableStateOf(playlist.title) }
    var isPublic by remember { mutableStateOf(playlist.isPublic) }
    var localCoverUrl by remember { mutableStateOf(playlist.coverUrl) }

    // Стейты Reorder Drag-and-Drop
    var draggingIndex by remember { mutableStateOf<Int?>(null) }
    var dragAccumulatedY by remember { mutableFloatStateOf(0f) }

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

    // Диалоги
    var showAddTracksSheet by remember { mutableStateOf(false) }
    var showAddEditorDialog by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var editorUsername by remember { mutableStateOf("") }

    // Состояние текущего трека в плеере
    var currentPlayingTrackId by remember { mutableStateOf<String?>(null) }
    var currentPlayingTitle by remember { mutableStateOf<String?>(null) }
    var currentPlayingArtist by remember { mutableStateOf<String?>(null) }
    var isPlaying by remember { mutableStateOf(false) }

    // FLAC СТАТУСЫ
    val playlistStatuses by org.akanework.gramophone.logic.lossless.FlacDownloadManager.playlistStatuses.collectAsState()
    val flacStatus = playlistStatuses[playlist.id]
    val isAllFlac = (tracks.isNotEmpty() && tracks.all { org.akanework.gramophone.logic.lossless.LosslessStateManager.isTrackLossless(it.id, it.is_lossless) })
        || flacStatus?.isComplete == true
        || (flacStatus?.percent ?: 0f) >= 100f
        || (flacStatus != null && flacStatus.totalTracks > 0 && flacStatus.flacTracks >= flacStatus.totalTracks)
    val isFlacDownloading = !isAllFlac && (org.akanework.gramophone.logic.lossless.FlacDownloadManager.isPlaylistDownloading(playlist.id)
        || ((flacStatus?.percent ?: 0f) > 0f && (flacStatus?.percent ?: 0f) < 100f && flacStatus?.isComplete != true))
    val flacPercent = flacStatus?.percent ?: 0f

    // ДИНАМИЧЕСКИЕ ЦВЕТА ИЗ ОБЛОЖКИ
    var dynamicColors by remember { mutableStateOf<DynamicArtworkTheme.ArtworkColors?>(null) }
    val defaultSurface = MaterialTheme.colorScheme.surface
    val defaultSurfaceContainer = MaterialTheme.colorScheme.surfaceContainerLowest

    val activeCover = localCoverUrl ?: playlist.coverUrl
    val finalCoverUrl = activeCover?.let {
        if (it.startsWith("/")) "http://185.196.41.31$it" else it
    }

    LaunchedEffect(finalCoverUrl, isDarkTheme) {
        if (!finalCoverUrl.isNullOrEmpty()) {
            withContext(Dispatchers.IO) {
                try {
                    val request = ImageRequest.Builder(context)
                        .data(finalCoverUrl)
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
        label = "playlistBgTop"
    )
    val animatedBgGlow by animateColorAsState(
        targetValue = dynamicColors?.fullPlayerSecondaryGlow ?: MaterialTheme.colorScheme.surfaceContainerLow,
        animationSpec = tween(500),
        label = "playlistBgGlow"
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

    val photoPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            coroutineScope.launch(Dispatchers.IO) {
                try {
                    val inputStream = context.contentResolver.openInputStream(uri)
                    val tempFile = File(context.cacheDir, "temp_cover.jpg")
                    val outputStream = FileOutputStream(tempFile)
                    inputStream?.copyTo(outputStream)
                    val reqFile = tempFile.asRequestBody("image/*".toMediaTypeOrNull())
                    val body = MultipartBody.Part.createFormData("file", tempFile.name, reqFile)

                    val response = NetworkClient.getApi(context).uploadPlaylistCover(playlist.id, body).execute()
                    withContext(Dispatchers.Main) {
                        if (response.isSuccessful) {
                            localCoverUrl = response.body()?.get("cover_url")
                            Toast.makeText(context, "Обложка обновлена", Toast.LENGTH_SHORT).show()
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Ошибка загрузки обложки", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    fun loadTracks() {
        coroutineScope.launch(Dispatchers.IO) {
            try {
                val response = NetworkClient.getApi(context).getPlaylistTracks(playlist.id).execute()
                if (response.isSuccessful && response.body() != null) {
                    val fetched = response.body()!!
                    withContext(Dispatchers.Main) {
                        tracks.clear()
                        tracks.addAll(fetched)
                        if (fetched.isNotEmpty()) {
                            val losslessList = fetched.filter { it.is_lossless }.map { it.id }
                            org.akanework.gramophone.logic.lossless.LosslessStateManager.markLossless(context, losslessList)
                        }
                    }
                }
            } catch (e: Exception) {}
            withContext(Dispatchers.Main) { isLoading = false }
        }
    }

    LaunchedEffect(playlist.id) {
        loadTracks()
        org.akanework.gramophone.logic.lossless.FlacDownloadManager.fetchPlaylistStatus(context, playlist.id)
    }

    fun startEditing() {
        originalTracks = tracks.toList()
        editTitle = playlist.title
        isPublic = playlist.isPublic
        isEditing = true
    }

    fun cancelEditing() {
        tracks.clear()
        tracks.addAll(originalTracks)
        editTitle = playlist.title
        isPublic = playlist.isPublic
        isEditing = false
        isSelectionMode = false
        selectedTrackIds.clear()
        filterQuery = ""
    }

    fun savePlaylistChanges() {
        coroutineScope.launch(Dispatchers.IO) {
            try {
                if (editTitle != playlist.title || isPublic != playlist.isPublic) {
                    val req = PlaylistUpdateRequest(title = editTitle, isPublic = isPublic)
                    NetworkClient.getApi(context).updatePlaylist(playlist.id, req).execute()
                }
                val reorderItems = tracks.mapIndexed { index, track ->
                    TrackReorderItem(trackId = track.id, position = index + 1)
                }
                NetworkClient.getApi(context).reorderPlaylistTracks(playlist.id, PlaylistReorderRequest(tracks = reorderItems)).execute()

                withContext(Dispatchers.Main) {
                    isEditing = false
                    isSelectionMode = false
                    selectedTrackIds.clear()
                    filterQuery = ""
                    Toast.makeText(context, "Изменения сохранены", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { Toast.makeText(context, "Ошибка сохранения", Toast.LENGTH_SHORT).show() }
            }
        }
    }

    fun deletePlaylist() {
        coroutineScope.launch(Dispatchers.IO) {
            try {
                val response = NetworkClient.getApi(context).deletePlaylist(playlist.id).execute()
                withContext(Dispatchers.Main) {
                    if (response.isSuccessful) {
                        Toast.makeText(context, "Плейлист удален", Toast.LENGTH_SHORT).show()
                        onBackClick()
                    } else if (response.code() == 403) {
                        Toast.makeText(context, "Только владелец может удалить плейлист", Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(context, "Ошибка при удалении", Toast.LENGTH_SHORT).show()
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
            LibrarySortOption.NEWEST -> loadTracks()
            LibrarySortOption.OLDEST -> tracks.reverse()
        }
    }

    fun deleteSelectedTracks() {
        coroutineScope.launch(Dispatchers.IO) {
            val idsToRemove = selectedTrackIds.toList()
            idsToRemove.forEach { id ->
                try {
                    NetworkClient.getApi(context).removeTrackFromPlaylist(playlist.id, id).execute()
                } catch (e: Exception) {}
            }
            withContext(Dispatchers.Main) {
                tracks.removeAll { it.id in idsToRemove }
                selectedTrackIds.clear()
                isSelectionMode = false
                Toast.makeText(context, "Удалено треков: ${idsToRemove.size}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun playTrackList(shuffle: Boolean = false, startIndex: Int = 0) {
        val currentList = tracks.toList()
        if (currentList.isEmpty()) return
        val player = activity?.getPlayer() ?: return

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
                            putString("PLAYING_FROM", "Плейлист: ${playlist.title}")
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
                        putString("PLAYING_FROM", "Очередь (Плейлист)")
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

    // ДИАЛОГИ
    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = { Text("Удалить плейлист?", fontWeight = FontWeight.Bold) },
            text = { Text("Это действие нельзя будет отменить.") },
            confirmButton = {
                Button(
                    onClick = { showDeleteConfirmDialog = false; deletePlaylist() },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Удалить") }
            },
            dismissButton = { TextButton(onClick = { showDeleteConfirmDialog = false }) { Text("Отмена") } }
        )
    }

    if (showAddEditorDialog) {
        AlertDialog(
            onDismissRequest = { showAddEditorDialog = false },
            title = { Text("Добавить соавтора", fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = editorUsername,
                    onValueChange = { editorUsername = it },
                    label = { Text("Имя пользователя соавтора") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (editorUsername.isNotBlank()) {
                        coroutineScope.launch(Dispatchers.IO) {
                            try {
                                val res = NetworkClient.getApi(context).addPlaylistEditor(playlist.id, EditorAddRequest(editorUsername)).execute()
                                withContext(Dispatchers.Main) {
                                    if (res.isSuccessful) Toast.makeText(context, "Соавтор добавлен!", Toast.LENGTH_SHORT).show()
                                    else Toast.makeText(context, "Пользователь не найден", Toast.LENGTH_SHORT).show()
                                }
                            } catch (e: Exception) {}
                        }
                    }
                    showAddEditorDialog = false
                    editorUsername = ""
                }) { Text("Добавить") }
            },
            dismissButton = { TextButton(onClick = { showAddEditorDialog = false }) { Text("Отмена") } }
        )
    }

    if (showAddTracksSheet) {
        ModalBottomSheet(
            onDismissRequest = { showAddTracksSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
        ) {
            AddTracksConstructorSheet(
                playlistId = playlist.id,
                onTrackAdded = { loadTracks() }
            )
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
                    val coverUri = activeTrack.cover?.let {
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
                activeTrack.artistId?.let { id ->
                    activity?.startFragment(ArtistFragment.newInstance(id))
                } ?: Toast.makeText(context, "Исполнитель неизвестен", Toast.LENGTH_SHORT).show()
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

    val dynamicGradientBrush = Brush.verticalGradient(
        colors = listOf(
            animatedBgTop,
            animatedBgGlow,
            MaterialTheme.colorScheme.surface
        )
    )

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

            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 260.dp)
            ) {
                // 1. ШАПКА ПЛЕЙЛИСТА (КРУПНАЯ ОБЛОЖКА + ДИНАМИЧЕСКИЙ ГРАДИЕНТ)
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
                        // БОЛЬШАЯ ОБЛОЖКА ПЛЕЙЛИСТА (250dp)
                        Card(
                            modifier = Modifier
                                .size(250.dp)
                                .clickable(enabled = isEditing) {
                                    photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                },
                            shape = RoundedCornerShape(32.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                        ) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                if (!finalCoverUrl.isNullOrBlank()) {
                                    AsyncImage(
                                        model = finalCoverUrl,
                                        contentDescription = playlist.title,
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

                                if (isEditing) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(Color.Black.copy(alpha = 0.5f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Icon(painterResource(R.drawable.ic_add), contentDescription = "Изменить фото", tint = Color.White, modifier = Modifier.size(40.dp))
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text("Сменить фото", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        // НАЗВАНИЕ ПЛЕЙЛИСТА
                        if (isEditing) {
                            OutlinedTextField(
                                value = editTitle,
                                onValueChange = { editTitle = it },
                                singleLine = true,
                                textStyle = MaterialTheme.typography.titleLarge.copy(textAlign = TextAlign.Center, fontWeight = FontWeight.Bold),
                                shape = RoundedCornerShape(18.dp),
                                modifier = Modifier
                                    .padding(horizontal = 16.dp)
                                    .fillMaxWidth()
                            )
                        } else {
                            Text(
                                text = editTitle,
                                style = MaterialTheme.typography.headlineMedium.copy(
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = (-0.5).sp
                                ),
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(horizontal = 16.dp),
                                textAlign = TextAlign.Center
                            )
                        }

                        // СТАТИСТИКА И ЧИП ПРИВАТНОСТИ
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

                            FilterChip(
                                selected = isPublic,
                                onClick = { if (isEditing) isPublic = !isPublic },
                                label = { Text(if (isPublic) "Публичный" else "Приватный", fontWeight = FontWeight.Bold) },
                                leadingIcon = {
                                    Icon(
                                        painter = painterResource(if (isPublic) R.drawable.ic_share else R.drawable.ic_person),
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp)
                                    )
                                },
                                shape = CircleShape
                            )
                        }

                        // ПАНЕЛЬ ДЕЙСТВИЙ (СЛУШАТЬ / ПЕРЕМЕШАТЬ / УПРАВЛЕНИЕ)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 20.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (isEditing) {
                                Button(
                                    onClick = { showAddTracksSheet = true },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(52.dp),
                                    shape = RoundedCornerShape(18.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp)
                                ) {
                                    Icon(painterResource(R.drawable.ic_add), contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Добавить треки", fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }

                                FilledTonalIconButton(
                                    onClick = { showAddEditorDialog = true },
                                    modifier = Modifier.size(52.dp),
                                    shape = RoundedCornerShape(18.dp)
                                ) {
                                    Icon(painterResource(R.drawable.ic_person), contentDescription = "Соавтор", modifier = Modifier.size(20.dp))
                                }

                                FilledTonalIconButton(
                                    onClick = { showDeleteConfirmDialog = true },
                                    modifier = Modifier.size(52.dp),
                                    shape = RoundedCornerShape(18.dp),
                                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                                        containerColor = MaterialTheme.colorScheme.errorContainer,
                                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                ) {
                                    Icon(imageVector = Icons.Rounded.Delete, contentDescription = "Удалить плейлист", modifier = Modifier.size(20.dp))
                                }
                            } else {
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
                                            org.akanework.gramophone.logic.lossless.FlacDownloadManager.downloadPlaylist(
                                                context = context,
                                                playlistId = playlist.id,
                                                playlistTitle = playlist.title,
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
                            }
                        }
                    }
                }

                // 2. СКРУГЛЕННЫЙ КОНТЕЙНЕР ДЛЯ СПИСКА ТРЕКОВ (КАК В МЕДИАТЕКЕ)
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
                            // Ряд управления треками внутри контейнера
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = if (filterQuery.isBlank()) "Все треки (${tracks.size})" else "Найдено (${filteredTracks.size})",
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

                                    // Кнопка редактирования
                                    if (!isEditing) {
                                        FilledTonalIconButton(
                                            onClick = { startEditing() },
                                            modifier = Modifier.size(38.dp),
                                            shape = CircleShape
                                        ) {
                                            Icon(painterResource(R.drawable.ic_edit), contentDescription = "Редактировать", modifier = Modifier.size(18.dp))
                                        }
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
                                            placeholder = { Text("Поиск в плейлисте...", style = MaterialTheme.typography.bodyMedium) },
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

                // 3. САМ СПИСОК ТРЕКОВ (ВНУТРИ СВЕТЛОГО/ТЕМНОГО КОНТЕЙНЕРА)
                if (filteredTracks.isEmpty()) {
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
                                    text = if (filterQuery.isBlank()) "В этом плейлисте пока нет треков" else "Ничего не найдено",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                } else {
                    itemsIndexed(filteredTracks, key = { index, track -> "${track.id}_$index" }) { index, track ->
                        val isCurrentTrack = isTrackMatching(track, currentPlayingTrackId, currentPlayingTitle, currentPlayingArtist)
                        val isSelected = selectedTrackIds.contains(track.id)
                        val isBeingDragged = draggingIndex == index

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

                                // Drag Handle in Reorder Editing Mode
                                if (isEditing && !isSelectionMode) {
                                    Surface(
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                        modifier = Modifier
                                            .align(Alignment.CenterEnd)
                                            .padding(end = 8.dp)
                                            .size(36.dp)
                                            .pointerInput(index, tracks.size) {
                                                detectDragGesturesAfterLongPress(
                                                    onDragStart = {
                                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                        draggingIndex = index
                                                        dragAccumulatedY = 0f
                                                    },
                                                    onDragEnd = {
                                                        draggingIndex = null
                                                        dragAccumulatedY = 0f
                                                    },
                                                    onDragCancel = {
                                                        draggingIndex = null
                                                        dragAccumulatedY = 0f
                                                    },
                                                    onDrag = { change, dragAmount ->
                                                        change.consume()
                                                        dragAccumulatedY += dragAmount.y
                                                        val currIdx = draggingIndex ?: return@detectDragGesturesAfterLongPress
                                                        val stepPx = 140f

                                                        if (dragAccumulatedY > stepPx && currIdx < tracks.size - 1) {
                                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                            val temp = tracks[currIdx]
                                                            tracks[currIdx] = tracks[currIdx + 1]
                                                            tracks[currIdx + 1] = temp
                                                            draggingIndex = currIdx + 1
                                                            dragAccumulatedY -= stepPx
                                                        } else if (dragAccumulatedY < -stepPx && currIdx > 0) {
                                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                            val temp = tracks[currIdx]
                                                            tracks[currIdx] = tracks[currIdx - 1]
                                                            tracks[currIdx - 1] = temp
                                                            draggingIndex = currIdx - 1
                                                            dragAccumulatedY += stepPx
                                                        }
                                                    }
                                                )
                                            }
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = Icons.Rounded.DragHandle,
                                                contentDescription = "Переместить",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Завершающая подложка контейнера
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
            ExpressiveScrollBar(
                listState = listState,
                itemCount = filteredTracks.size,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(top = 100.dp, bottom = 120.dp, end = 2.dp)
            )
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
                onClick = { if (isEditing) cancelEditing() else onBackClick() },
                modifier = Modifier.size(46.dp),
                shape = CircleShape
            ) {
                Icon(
                    imageVector = if (isEditing) Icons.Rounded.Close else Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "Назад/Отмена",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(22.dp)
                )
            }

            AnimatedVisibility(visible = isEditing, enter = fadeIn(), exit = fadeOut()) {
                Button(
                    onClick = { savePlaylistChanges() },
                    shape = CircleShape,
                    contentPadding = PaddingValues(horizontal = 22.dp, vertical = 8.dp)
                ) {
                    Text("Сохранить", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }
        }

        // MULTI-SELECTION BOTTOM BAR
        AnimatedVisibility(
            visible = isSelectionMode,
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
                            val coverUri = t.cover?.let {
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
            val totalCount = flacStatus?.totalTracks?.takeIf { it > 0 } ?: tracks.size
            val flacCount = flacStatus?.flacTracks ?: if (isAllFlac) tracks.size else tracks.count { org.akanework.gramophone.logic.lossless.LosslessStateManager.isTrackLossless(it.id, it.is_lossless) }
            val currentPercent = if (isAllFlac) 100f else flacPercent

            org.akanework.gramophone.ui.components.lossless.DownloadProgressBottomSheet(
                title = playlist.title,
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
fun AddTracksConstructorSheet(playlistId: Int, onTrackAdded: () -> Unit) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var favoriteTracks by remember { mutableStateOf<List<Track>>(emptyList()) }
    var searchQuery by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(true) }
    val addedTrackIds = remember { mutableStateListOf<String>() }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            try {
                val response = NetworkClient.getApi(context).getFavorites(0, 1000).execute()
                if (response.isSuccessful) {
                    favoriteTracks = response.body() ?: emptyList()
                }
            } catch (e: Exception) {}
            withContext(Dispatchers.Main) { isLoading = false }
        }
    }

    val filteredTracks = remember(favoriteTracks, searchQuery) {
        if (searchQuery.isBlank()) {
            favoriteTracks
        } else {
            favoriteTracks.filter { track ->
                track.title.contains(searchQuery, ignoreCase = true) ||
                        track.artist.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        Text(
            text = "Добавить треки из медиатеки",
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Поиск треков...") },
            singleLine = true,
            leadingIcon = { Icon(painterResource(R.drawable.ic_search), contentDescription = null) },
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(12.dp))

        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filteredTracks, key = { it.id }) { track ->
                    val isAlreadyAdded = addedTrackIds.contains(track.id)

                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp)
                        ) {
                            val coverUrl = if (track.cover?.startsWith("/") == true) "http://185.196.41.31${track.cover}" else track.cover
                            AsyncImage(
                                model = coverUrl,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(RoundedCornerShape(12.dp))
                            )

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = track.title,
                                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
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

                            FilledTonalIconButton(
                                onClick = {
                                    if (!isAlreadyAdded) {
                                        val trackIdInt = track.id.toIntOrNull()
                                        if (trackIdInt != null) {
                                            coroutineScope.launch(Dispatchers.IO) {
                                                try {
                                                    val res = NetworkClient.getApi(context).addTrackToPlaylist(playlistId, PlaylistTrackAddRequest(trackIdInt)).execute()
                                                    withContext(Dispatchers.Main) {
                                                        if (res.isSuccessful) {
                                                            addedTrackIds.add(track.id)
                                                            onTrackAdded()
                                                            Toast.makeText(context, "Добавлено!", Toast.LENGTH_SHORT).show()
                                                        }
                                                    }
                                                } catch (e: Exception) {}
                                            }
                                        }
                                    }
                                },
                                shape = CircleShape,
                                modifier = Modifier.size(38.dp),
                                colors = if (isAlreadyAdded) IconButtonDefaults.filledTonalIconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                ) else IconButtonDefaults.filledTonalIconButtonColors()
                            ) {
                                Icon(
                                    imageVector = if (isAlreadyAdded) Icons.Rounded.Check else Icons.Rounded.Add,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}