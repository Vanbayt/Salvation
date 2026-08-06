package org.akanework.gramophone.ui.fragments

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
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
import java.util.Collections

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistDetailScreen(
    playlist: Playlist,
    onBackClick: () -> Unit,
    onPlayClick: (List<Track>, startIndex: Int) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val tracks = remember { mutableStateListOf<Track>() }
    var isLoading by remember { mutableStateOf(true) }
    var isLiked by remember { mutableStateOf(false) }

    // СТЕЙТЫ РЕЖИМА РЕДАКТИРОВАНИЯ
    var isEditing by remember { mutableStateOf(false) }
    var editTitle by remember { mutableStateOf(playlist.title) }
    var localCoverUrl by remember { mutableStateOf(playlist.coverUrl) }

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
                } catch (e: Exception) {}
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

    fun savePlaylistChanges() {
        coroutineScope.launch(Dispatchers.IO) {
            try {
                if (editTitle != playlist.title) {
                    val req = PlaylistUpdateRequest(title = editTitle)
                    NetworkClient.getApi(context).updatePlaylist(playlist.id, req).execute()
                }
                val reorderItems = tracks.mapIndexed { index, track ->
                    TrackReorderItem(trackId = track.id.toInt(), position = index + 1)
                }
                NetworkClient.getApi(context).reorderPlaylistTracks(playlist.id, PlaylistReorderRequest(tracks = reorderItems)).execute()

                withContext(Dispatchers.Main) {
                    isEditing = false
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
                        onBackClick() // Уходим с экрана
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

    // ДИАЛОГИ
    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = { Text("Удалить плейлист?") },
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
            title = { Text("Добавить соавтора") },
            text = {
                OutlinedTextField(
                    value = editorUsername,
                    onValueChange = { editorUsername = it },
                    label = { Text("MAHORAGA HELP ME!!!") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    coroutineScope.launch(Dispatchers.IO) {
                        try {
                            val res = NetworkClient.getApi(context).addPlaylistEditor(playlist.id, EditorAddRequest(editorUsername)).execute()
                            withContext(Dispatchers.Main) {
                                if (res.isSuccessful) Toast.makeText(context, "Соавтор добавлен!", Toast.LENGTH_SHORT).show()
                                else Toast.makeText(context, "Пользователь не найден", Toast.LENGTH_SHORT).show()
                            }
                        } catch (e: Exception) {}
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

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        } else {
            val firstVisibleIndex = remember { derivedStateOf { listState.firstVisibleItemIndex } }
            val firstVisibleOffset = remember { derivedStateOf { listState.firstVisibleItemScrollOffset } }

            val headerAlpha by animateFloatAsState(if (firstVisibleIndex.value > 0) 0f else (1f - (firstVisibleOffset.value / 600f)).coerceIn(0f, 1f))
            val headerScale by animateFloatAsState(if (firstVisibleIndex.value > 0) 0.8f else 1f - (firstVisibleOffset.value / 1500f).coerceIn(0f, 0.2f))

            LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                // ШАПКА
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(top = 72.dp).graphicsLayer {
                            alpha = headerAlpha; scaleX = headerScale; scaleY = headerScale; translationY = firstVisibleOffset.value * 0.5f
                        },
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // КРАСИВАЯ ОБЛОЖКА
                        Box(
                            modifier = Modifier
                                .size(240.dp)
                                .clip(RoundedCornerShape(28.dp))
                                .clickable(enabled = isEditing) {
                                    photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            PlaylistCover(playlist = playlist.copy(coverUrl = localCoverUrl), modifier = Modifier.fillMaxSize())

                            // Элегантное затемнение при редактировании
                            if (isEditing) {
                                Box(
                                    modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(painterResource(R.drawable.ic_add), contentDescription = "Изменить фото", tint = Color.White, modifier = Modifier.size(48.dp))
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        // ЭЛЕГАНТНЫЙ ВВОД ТЕКСТА
                        if (isEditing) {
                            TextField(
                                value = editTitle,
                                onValueChange = { editTitle = it },
                                singleLine = true,
                                textStyle = MaterialTheme.typography.headlineMedium.copy(textAlign = TextAlign.Center, fontWeight = FontWeight.Bold),
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent,
                                    focusedIndicatorColor = MaterialTheme.colorScheme.primary,
                                    unfocusedIndicatorColor = Color.Transparent
                                ),
                                modifier = Modifier.padding(horizontal = 24.dp).fillMaxWidth()
                            )
                        } else {
                            Text(text = editTitle, style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Black), color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(horizontal = 24.dp), textAlign = TextAlign.Center)
                        }

                        Text(text = "Плейлист • ${tracks.size} треков", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))

                        // ПАНЕЛЬ КНОПОК
                        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (isEditing) {
                                Button(onClick = { showAddTracksSheet = true }, modifier = Modifier.weight(1f).height(64.dp), shape = RoundedCornerShape(20.dp), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer)) {
                                    Icon(painterResource(R.drawable.ic_add), contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                                    Text("Добавить треки", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                FilledTonalIconButton(onClick = { showAddEditorDialog = true }, modifier = Modifier.size(64.dp), shape = RoundedCornerShape(20.dp)) {
                                    // Используем стандартную иконку пользователя, если нет кастомной, замени на ic_person
                                    Icon(painterResource(R.drawable.ic_person), contentDescription = "Соавтор")
                                }
                            } else {
                                Button(onClick = { if (tracks.isNotEmpty()) onPlayClick(tracks, 0) }, modifier = Modifier.weight(1f).height(64.dp), shape = RoundedCornerShape(20.dp)) {
                                    Icon(painterResource(R.drawable.ic_play), contentDescription = "Play", modifier = Modifier.padding(end = 8.dp))
                                    Text("Слушать", fontSize = 18.sp, fontWeight = FontWeight.Medium)
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                FilledTonalIconButton(onClick = { isEditing = true }, modifier = Modifier.size(64.dp), shape = RoundedCornerShape(20.dp)) {
                                    Icon(painterResource(R.drawable.ic_edit), contentDescription = "Редактировать")
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                FilledTonalIconButton(
                                    onClick = {
                                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                            type = "text/plain"
                                            putExtra(Intent.EXTRA_TEXT, "Слушай мой плейлист «${playlist.title}»!\n\nhttp://185.196.41.31/playlist/${playlist.id}")
                                        }
                                        context.startActivity(Intent.createChooser(shareIntent, "Поделиться"))
                                    }, modifier = Modifier.size(64.dp), shape = RoundedCornerShape(20.dp)
                                ) {
                                    Icon(painterResource(R.drawable.ic_share), contentDescription = "Share")
                                }
                            }
                        }
                    }
                }

                item {
                    Box(modifier = Modifier.fillMaxWidth().height(24.dp).background(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)))
                }

                // СПИСОК ТРЕКОВ
                itemsIndexed(tracks, key = { index, track -> "${index}_${track.id}" }) { index, track ->
                    Box(modifier = Modifier.background(MaterialTheme.colorScheme.surfaceContainerLow).animateItem()) {
                        TrackListItem(
                            track = track,
                            isEditing = isEditing,
                            onRemove = {
                                coroutineScope.launch(Dispatchers.IO) {
                                    NetworkClient.getApi(context).removeTrackFromPlaylist(playlist.id, track.id.toInt()).execute()
                                    withContext(Dispatchers.Main) { tracks.removeAt(index) }
                                }
                            },
                            modifier = Modifier.clickable(enabled = !isEditing) { onPlayClick(tracks, index) },
                            dragModifier = if (isEditing) {
                                Modifier.pointerInput(track.id) {
                                    detectDragGesturesAfterLongPress(
                                        onDrag = { change, dragAmount ->
                                            change.consume()
                                            if (dragAmount.y > 40 && index < tracks.size - 1) {
                                                val temp = tracks[index]; tracks[index] = tracks[index + 1]; tracks[index + 1] = temp
                                            } else if (dragAmount.y < -40 && index > 0) {
                                                val temp = tracks[index]; tracks[index] = tracks[index - 1]; tracks[index - 1] = temp
                                            }
                                        }
                                    )
                                }
                            } else Modifier
                        )
                    }
                }

                // КНОПКА УДАЛЕНИЯ ПЛЕЙЛИСТА
                if (isEditing) {
                    item {
                        Box(modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerLow).padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
                            Button(
                                onClick = { showDeleteConfirmDialog = true },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.errorContainer,
                                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                                ),
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).height(56.dp)
                            ) {
                                // Замени на ic_delete, если есть
                                Icon(painterResource(R.drawable.ic_close), contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                                Text("Удалить плейлист", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            }
                        }
                    }
                }

                item {
                    Box(modifier = Modifier.fillMaxWidth().height(180.dp).background(MaterialTheme.colorScheme.surfaceContainerLow))
                }
            }
        }

        // ВЕРХНЯЯ ПАНЕЛЬ
        Row(
            modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = { if (isEditing) { isEditing = false; editTitle = playlist.title } else onBackClick() },
                modifier = Modifier.size(48.dp).background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f), CircleShape)
            ) {
                Icon(painterResource(if (isEditing) R.drawable.ic_close else R.drawable.ic_arrow_back), "Back/Cancel", tint = MaterialTheme.colorScheme.onSurface)
            }

            if (isEditing) {
                Button(
                    onClick = { savePlaylistChanges() },
                    shape = RoundedCornerShape(50),
                    contentPadding = PaddingValues(horizontal = 24.dp)
                ) {
                    Text("Сохранить", fontWeight = FontWeight.Bold)
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
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            try {
                val response = NetworkClient.getApi(context).getFavorites(0, 50).execute()
                if (response.isSuccessful) { favoriteTracks = response.body() ?: emptyList() }
            } catch (e: Exception) {}
            withContext(Dispatchers.Main) { isLoading = false }
        }
    }

    Column(modifier = Modifier.fillMaxWidth().fillMaxHeight(0.8f).padding(16.dp)) {
        Text("Добавить из избранного", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold), modifier = Modifier.padding(bottom = 16.dp))
        if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
        } else {
            LazyColumn {
                itemsIndexed(favoriteTracks) { _, track ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable {
                            coroutineScope.launch(Dispatchers.IO) {
                                try {
                                    val res = NetworkClient.getApi(context).addTrackToPlaylist(playlistId, PlaylistTrackAddRequest(track.id.toInt())).execute()
                                    if (res.isSuccessful) {
                                        withContext(Dispatchers.Main) {
                                            Toast.makeText(context, "Добавлено!", Toast.LENGTH_SHORT).show()
                                            onTrackAdded()
                                        }
                                    }
                                } catch (e: Exception) {}
                            }
                        }.padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AsyncImage(model = track.cover, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)))
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(track.title ?: "Unknown", style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(track.artist ?: "Unknown", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Icon(painterResource(R.drawable.ic_add), contentDescription = "Добавить", tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

@Composable
fun TrackListItem(track: Track, isEditing: Boolean, onRemove: () -> Unit, modifier: Modifier = Modifier, dragModifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {

            // Элегантная кнопка удаления слева
            if (isEditing) {
                IconButton(
                    onClick = onRemove,
                    modifier = Modifier.size(36.dp).padding(end = 8.dp)
                ) {
                    Icon(painterResource(R.drawable.ic_close), contentDescription = "Удалить", tint = MaterialTheme.colorScheme.error)
                }
            }

            AsyncImage(model = track.cover ?: R.drawable.ic_library, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(56.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant))

            Column(modifier = Modifier.weight(1f).padding(start = 16.dp, end = 12.dp), verticalArrangement = Arrangement.Center) {
                Text(text = track.title ?: "Неизвестный трек", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(text = track.artist ?: "Неизвестный исполнитель", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }

            if (isEditing) {
                Icon(painterResource(R.drawable.ic_drag_handle), contentDescription = "Тащить", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = dragModifier.size(48.dp).padding(12.dp))
            } else {
                IconButton(onClick = { /* TODO: Меню трека */ }, modifier = Modifier.size(48.dp)) {
                    Icon(painterResource(R.drawable.ic_more_vert), contentDescription = "Меню", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}