package org.akanework.gramophone.ui.fragments

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
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
import coil3.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import org.akanework.gramophone.R
import org.akanework.gramophone.logic.api.*
import java.io.File
import java.io.FileOutputStream

enum class PlaylistSortMode {
    DEFAULT, TITLE_ASC, ARTIST_ASC
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistDetailScreen(
    playlist: Playlist,
    onBackClick: () -> Unit,
    onPlayClick: (List<Track>, startIndex: Int) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current

    val tracks = remember { mutableStateListOf<Track>() }
    var originalTracks by remember { mutableStateOf<List<Track>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    // СТЕЙТЫ РЕЖИМА РЕДАКТИРОВАНИЯ
    var isEditing by remember { mutableStateOf(false) }
    var editTitle by remember { mutableStateOf(playlist.title) }
    var isPublic by remember { mutableStateOf(playlist.isPublic) }
    var localCoverUrl by remember { mutableStateOf(playlist.coverUrl) }

    // Стейты Reorder Drag-and-Drop
    var draggingIndex by remember { mutableStateOf<Int?>(null) }
    var dragAccumulatedY by remember { mutableFloatStateOf(0f) }

    // Стейты Пакетного Выбора (Multi-Select)
    var isMultiSelectMode by remember { mutableStateOf(false) }
    val selectedTrackIds = remember { mutableStateListOf<Any>() }

    // Стейты Поиска и Сортировки
    var filterQuery by remember { mutableStateOf("") }
    var currentSortMode by remember { mutableStateOf(PlaylistSortMode.DEFAULT) }
    var showSortMenu by remember { mutableStateOf(false) }

    // Диалоги
    var showAddTracksSheet by remember { mutableStateOf(false) }
    var showAddEditorDialog by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var editorUsername by remember { mutableStateOf("") }

    val listState = rememberLazyListState()

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
                    withContext(Dispatchers.Main) {
                        tracks.clear()
                        tracks.addAll(response.body()!!)
                    }
                }
            } catch (e: Exception) {}
            withContext(Dispatchers.Main) { isLoading = false }
        }
    }

    LaunchedEffect(playlist.id) { loadTracks() }

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
        isMultiSelectMode = false
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
                    isMultiSelectMode = false
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

    fun applySort(mode: PlaylistSortMode) {
        currentSortMode = mode
        when (mode) {
            PlaylistSortMode.TITLE_ASC -> tracks.sortBy { it.title ?: "" }
            PlaylistSortMode.ARTIST_ASC -> tracks.sortBy { it.artist ?: "" }
            PlaylistSortMode.DEFAULT -> loadTracks()
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
                Toast.makeText(context, "Выбранные треки удалены", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val filteredTracks = remember(tracks.toList(), filterQuery) {
        if (filterQuery.isBlank()) tracks
        else tracks.filter {
            (it.title?.contains(filterQuery, ignoreCase = true) == true) ||
                    (it.artist?.contains(filterQuery, ignoreCase = true) == true)
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
        ModalBottomSheet(onDismissRequest = { showAddTracksSheet = false }) {
            AddTracksConstructorSheet(playlistId = playlist.id, onTrackAdded = { loadTracks() })
        }
    }

    val gradientBrush = Brush.verticalGradient(
        colors = listOf(
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.surfaceContainerLow,
            MaterialTheme.colorScheme.surface
        )
    )

    Box(modifier = Modifier.fillMaxSize().background(brush = gradientBrush)) {
        if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        } else {
            val firstVisibleIndex = remember { derivedStateOf { listState.firstVisibleItemIndex } }
            val firstVisibleOffset = remember { derivedStateOf { listState.firstVisibleItemScrollOffset } }

            val headerAlpha by animateFloatAsState(if (firstVisibleIndex.value > 0) 0f else (1f - (firstVisibleOffset.value / 600f)).coerceIn(0f, 1f))
            val headerScale by animateFloatAsState(if (firstVisibleIndex.value > 0) 0.85f else (1f - (firstVisibleOffset.value / 1500f)).coerceIn(0.85f, 1f))

            LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                // ШАПКА ПЛЕЙЛИСТА
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 64.dp)
                            .graphicsLayer {
                                alpha = headerAlpha
                                scaleX = headerScale
                                scaleY = headerScale
                                translationY = firstVisibleOffset.value * 0.4f
                            },
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // ОБЛОЖКА ПЛЕЙЛИСТА
                        Box(
                            modifier = Modifier
                                .size(230.dp)
                                .clip(RoundedCornerShape(32.dp))
                                .clickable(enabled = isEditing) {
                                    photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            PlaylistCover(playlist = playlist.copy(coverUrl = localCoverUrl), modifier = Modifier.fillMaxSize())

                            if (isEditing) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(Color.Black.copy(alpha = 0.45f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Icon(painterResource(R.drawable.ic_add), contentDescription = "Изменить фото", tint = Color.White, modifier = Modifier.size(40.dp))
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text("Сменить фото", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(18.dp))

                        // НАЗВАНИЕ ПЛЕЙЛИСТА
                        if (isEditing) {
                            OutlinedTextField(
                                value = editTitle,
                                onValueChange = { editTitle = it },
                                singleLine = true,
                                textStyle = MaterialTheme.typography.titleLarge.copy(textAlign = TextAlign.Center, fontWeight = FontWeight.Bold),
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier
                                    .padding(horizontal = 24.dp)
                                    .fillMaxWidth()
                            )
                        } else {
                            Text(
                                text = editTitle,
                                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Black),
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(horizontal = 24.dp),
                                textAlign = TextAlign.Center
                            )
                        }

                        // СТАТИСТИКА И ЧИП ПРИВАТНОСТИ
                        Row(
                            modifier = Modifier.padding(top = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            AssistChip(
                                onClick = {},
                                label = { Text("${tracks.size} треков", fontWeight = FontWeight.Medium) },
                                shape = RoundedCornerShape(12.dp)
                            )
                            FilterChip(
                                selected = isPublic,
                                onClick = { if (isEditing) isPublic = !isPublic },
                                label = { Text(if (isPublic) "Публичный" else "Приватный", fontWeight = FontWeight.Bold) },
                                leadingIcon = {
                                    Icon(
                                        painter = painterResource(if (isPublic) R.drawable.ic_share else R.drawable.ic_person),
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                shape = RoundedCornerShape(12.dp)
                            )
                        }

                        // ПАНЕЛЬ КНОПОК МАТЕРИАЛ 3 EXPRESSIVE
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (isEditing) {
                                Button(
                                    onClick = { showAddTracksSheet = true },
                                    modifier = Modifier.weight(1f).height(52.dp),
                                    shape = RoundedCornerShape(16.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                ) {
                                    Icon(painterResource(R.drawable.ic_add), contentDescription = null, modifier = Modifier.padding(end = 6.dp))
                                    Text("Добавить треки", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                FilledTonalIconButton(
                                    onClick = { isMultiSelectMode = !isMultiSelectMode },
                                    modifier = Modifier.size(52.dp),
                                    shape = RoundedCornerShape(16.dp),
                                    colors = if (isMultiSelectMode) IconButtonDefaults.filledTonalIconButtonColors(
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        contentColor = MaterialTheme.colorScheme.onPrimary
                                    ) else IconButtonDefaults.filledTonalIconButtonColors()
                                ) {
                                    Icon(painterResource(R.drawable.ic_library), contentDescription = "Выбор")
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                FilledTonalIconButton(
                                    onClick = { showAddEditorDialog = true },
                                    modifier = Modifier.size(52.dp),
                                    shape = RoundedCornerShape(16.dp)
                                ) {
                                    Icon(painterResource(R.drawable.ic_person), contentDescription = "Соавтор")
                                }
                            } else {
                                Button(
                                    onClick = { if (tracks.isNotEmpty()) onPlayClick(tracks, 0) },
                                    modifier = Modifier.weight(1f).height(54.dp),
                                    shape = RoundedCornerShape(18.dp)
                                ) {
                                    Icon(painterResource(R.drawable.ic_play), contentDescription = "Play", modifier = Modifier.padding(end = 8.dp))
                                    Text("Слушать", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                FilledTonalIconButton(
                                    onClick = { startEditing() },
                                    modifier = Modifier.size(54.dp),
                                    shape = RoundedCornerShape(18.dp)
                                ) {
                                    Icon(painterResource(R.drawable.ic_edit), contentDescription = "Редактировать")
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Box {
                                    FilledTonalIconButton(
                                        onClick = { showSortMenu = true },
                                        modifier = Modifier.size(54.dp),
                                        shape = RoundedCornerShape(18.dp)
                                    ) {
                                        Icon(painterResource(R.drawable.ic_more_vert), contentDescription = "Сортировка")
                                    }
                                    DropdownMenu(
                                        expanded = showSortMenu,
                                        onDismissRequest = { showSortMenu = false }
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text("По умолчанию") },
                                            onClick = { showSortMenu = false; applySort(PlaylistSortMode.DEFAULT) }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("По названию (A-Z)") },
                                            onClick = { showSortMenu = false; applySort(PlaylistSortMode.TITLE_ASC) }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("По артисту (A-Z)") },
                                            onClick = { showSortMenu = false; applySort(PlaylistSortMode.ARTIST_ASC) }
                                        )
                                    }
                                }
                            }
                        }

                        // СТРОКА ПОИСКА/ФИЛЬТРА В РЕДАКТОРЕ
                        if (isEditing) {
                            OutlinedTextField(
                                value = filterQuery,
                                onValueChange = { filterQuery = it },
                                placeholder = { Text("Фильтр треков в плейлисте...") },
                                singleLine = true,
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 20.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(16.dp)
                            .background(
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
                            )
                    )
                }

                // СПИСОК ТРЕКОВ
                itemsIndexed(filteredTracks, key = { index, track -> "${index}_${track.id}" }) { index, track ->
                    val isBeingDragged = draggingIndex == index
                    val isSelected = selectedTrackIds.contains(track.id)

                    val dismissState = rememberSwipeToDismissBoxState(
                        confirmValueChange = { dismissValue ->
                            if (dismissValue == SwipeToDismissBoxValue.EndToStart) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                coroutineScope.launch(Dispatchers.IO) {
                                    NetworkClient.getApi(context).removeTrackFromPlaylist(playlist.id, track.id).execute()
                                    withContext(Dispatchers.Main) {
                                        tracks.remove(track)
                                        Toast.makeText(context, "Удалено", Toast.LENGTH_SHORT).show()
                                    }
                                }
                                true
                            } else false
                        }
                    )

                    SwipeToDismissBox(
                        state = dismissState,
                        enableDismissFromStartToEnd = false,
                        enableDismissFromEndToStart = isEditing && !isBeingDragged,
                        backgroundContent = {
                            val color by animateColorAsState(
                                if (dismissState.targetValue == SwipeToDismissBoxValue.EndToStart) MaterialTheme.colorScheme.errorContainer else Color.Transparent
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 14.dp, vertical = 4.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(color)
                                    .padding(end = 20.dp),
                                contentAlignment = Alignment.CenterEnd
                            ) {
                                Icon(painterResource(R.drawable.ic_close), contentDescription = "Удалить", tint = MaterialTheme.colorScheme.onErrorContainer)
                            }
                        }
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                                .animateItem()
                        ) {
                            TrackListItem(
                                track = track,
                                isEditing = isEditing,
                                isBeingDragged = isBeingDragged,
                                isMultiSelectMode = isMultiSelectMode,
                                isSelected = isSelected,
                                onSelectToggle = {
                                    if (isSelected) selectedTrackIds.remove(track.id)
                                    else selectedTrackIds.add(track.id)
                                },
                                onRemove = {
                                    coroutineScope.launch(Dispatchers.IO) {
                                        NetworkClient.getApi(context).removeTrackFromPlaylist(playlist.id, track.id).execute()
                                        withContext(Dispatchers.Main) {
                                            tracks.remove(track)
                                        }
                                    }
                                },
                                modifier = Modifier.clickable(enabled = !isEditing) { onPlayClick(tracks, index) },
                                dragModifier = if (isEditing && !isMultiSelectMode) {
                                    Modifier.pointerInput(index, tracks.size) {
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
                                                val stepPx = 160f

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
                                } else Modifier
                            )
                        }
                    }
                }

                // ПАНЕЛЬ ПАКЕТНОГО УДАЛЕНИЯ ИЛИ УДАЛЕНИЯ ПЛЕЙЛИСТА
                if (isEditing) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                                .padding(vertical = 20.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isMultiSelectMode && selectedTrackIds.isNotEmpty()) {
                                Button(
                                    onClick = { deleteSelectedTracks() },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.errorContainer,
                                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                                    ),
                                    shape = RoundedCornerShape(16.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 20.dp)
                                        .height(54.dp)
                                ) {
                                    Icon(painterResource(R.drawable.ic_close), contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                                    Text("Удалить выбранные (${selectedTrackIds.size})", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                }
                            } else {
                                Button(
                                    onClick = { showDeleteConfirmDialog = true },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.errorContainer,
                                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                                    ),
                                    shape = RoundedCornerShape(16.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 20.dp)
                                        .height(54.dp)
                                ) {
                                    Icon(painterResource(R.drawable.ic_close), contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                                    Text("Удалить плейлист", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                }
                            }
                        }
                    }
                }

                item {
                    Box(modifier = Modifier.fillMaxWidth().height(160.dp).background(MaterialTheme.colorScheme.surfaceContainerLow))
                }
            }
        }

        // ВЕРХНЯЯ ПАНЕЛЬ С ДЕЙСТВИЯМИ
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = { if (isEditing) cancelEditing() else onBackClick() },
                modifier = Modifier
                    .size(44.dp)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.8f), CircleShape)
            ) {
                Icon(
                    painter = painterResource(if (isEditing) R.drawable.ic_close else R.drawable.ic_arrow_back),
                    contentDescription = "Back/Cancel",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }

            AnimatedVisibility(visible = isEditing, enter = fadeIn(), exit = fadeOut()) {
                Button(
                    onClick = { savePlaylistChanges() },
                    shape = RoundedCornerShape(50),
                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 10.dp)
                ) {
                    Text("Сохранить", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
            }
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

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            try {
                val response = NetworkClient.getApi(context).getFavorites(0, 100).execute()
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
                (track.title?.contains(searchQuery, ignoreCase = true) == true) ||
                        (track.artist?.contains(searchQuery, ignoreCase = true) == true)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.85f)
            .padding(16.dp)
    ) {
        Text(
            text = "Добавить из избранного",
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            modifier = Modifier.padding(bottom = 12.dp)
        )

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Поиск по трекам...") },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
        )

        if (isLoading) {
            Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (filteredTracks.isEmpty()) {
            Box(modifier = Modifier.fillMaxWidth().height(150.dp), contentAlignment = Alignment.Center) {
                Text("Ничего не найдено", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                itemsIndexed(filteredTracks, key = { index, track -> "${index}_${track.id}" }) { _, track ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                coroutineScope.launch(Dispatchers.IO) {
                                    try {
                                        val req = PlaylistTrackAddRequest(
                                            trackId = track.id
                                        )
                                        val res = NetworkClient.getApi(context).addTrackToPlaylist(playlistId, req).execute()
                                        if (res.isSuccessful) {
                                            withContext(Dispatchers.Main) {
                                                Toast.makeText(context, "Добавлено!", Toast.LENGTH_SHORT).show()
                                                onTrackAdded()
                                            }
                                        }
                                    } catch (e: Exception) {}
                                }
                            }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AsyncImage(
                            model = track.cover ?: R.drawable.ic_library,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(10.dp))
                        )
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = track.title ?: "Без названия",
                                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = track.artist ?: "Неизвестный исполнитель",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Icon(
                            painter = painterResource(R.drawable.ic_add),
                            contentDescription = "Добавить",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun TrackListItem(
    track: Track,
    isEditing: Boolean,
    isBeingDragged: Boolean = false,
    isMultiSelectMode: Boolean = false,
    isSelected: Boolean = false,
    onSelectToggle: () -> Unit = {},
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
    dragModifier: Modifier = Modifier
) {
    val scale by animateFloatAsState(
        targetValue = if (isBeingDragged) 1.05f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)
    )
    val elevation by animateDpAsState(if (isBeingDragged) 12.dp else 0.dp)
    val cardColor = if (isBeingDragged) {
        MaterialTheme.colorScheme.surfaceContainerHigh
    } else if (isSelected) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
    } else {
        MaterialTheme.colorScheme.surface
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 4.dp)
            .scale(scale),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = cardColor),
        elevation = CardDefaults.cardElevation(defaultElevation = elevation)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isEditing && isMultiSelectMode) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onSelectToggle() },
                    modifier = Modifier.padding(end = 6.dp)
                )
            } else if (isEditing) {
                IconButton(
                    onClick = onRemove,
                    modifier = Modifier
                        .size(36.dp)
                        .padding(end = 6.dp)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_close),
                        contentDescription = "Удалить",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }

            AsyncImage(
                model = track.cover ?: R.drawable.ic_library,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 14.dp, end = 10.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = track.title ?: "Неизвестный трек",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = track.artist ?: "Неизвестный исполнитель",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (isEditing && !isMultiSelectMode) {
                Icon(
                    painter = painterResource(R.drawable.ic_drag_handle),
                    contentDescription = "Перетащить",
                    tint = if (isBeingDragged) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = dragModifier
                        .size(48.dp)
                        .padding(12.dp)
                )
            } else if (!isEditing) {
                IconButton(onClick = { /* Track menu */ }, modifier = Modifier.size(40.dp)) {
                    Icon(
                        painter = painterResource(R.drawable.ic_more_vert),
                        contentDescription = "Меню",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}