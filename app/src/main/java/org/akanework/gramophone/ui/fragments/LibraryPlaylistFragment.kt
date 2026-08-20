package org.akanework.gramophone.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.akanework.gramophone.R
import org.akanework.gramophone.logic.api.NetworkClient
import org.akanework.gramophone.logic.api.Playlist
import org.akanework.gramophone.ui.MainActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.PickVisualMediaRequest
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.io.FileOutputStream
import android.net.Uri
import androidx.compose.ui.layout.ContentScale
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.delay

class LibraryPlaylistsFragment : androidx.fragment.app.Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnLifecycleDestroyed(viewLifecycleOwner))

            setContent {
                val context = LocalContext.current
                val isDarkTheme = androidx.compose.foundation.isSystemInDarkTheme()
                val dynamicColorScheme = when {
                    android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S -> {
                        if (isDarkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
                    }
                    isDarkTheme -> darkColorScheme()
                    else -> lightColorScheme()
                }

                MaterialTheme(colorScheme = dynamicColorScheme) {
                    PlaylistsScreen(
                        onPlaylistClick = { playlist ->
                            // Открываем экран плейлиста
                            val fragment = ComposeContainerFragment.newInstance(playlist)
                            (requireActivity() as MainActivity).startFragment(frag = fragment)
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun PlaylistsScreen(
    onPlaylistClick: (Playlist) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var playlists by remember { mutableStateOf<List<Playlist>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    // Добавь эти стейты под newPlaylistName:
    var isPublic by remember { mutableStateOf(false) }
    var coverUri by remember { mutableStateOf<Uri?>(null) }
    var isCreating by remember { mutableStateOf(false) }

    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        coverUri = uri
    }

    // Стейты для диалога создания
    var showCreateDialog by remember { mutableStateOf(false) }
    var newPlaylistName by remember { mutableStateOf("") }

    fun loadPlaylists() {
        coroutineScope.launch(Dispatchers.IO) {
            // 1. Показываем кэш моментально
            val cached = org.akanework.gramophone.logic.LibraryCacheManager.loadCachedPlaylists(context)
            if (cached.isNotEmpty()) {
                withContext(Dispatchers.Main) {
                    playlists = cached
                    isLoading = false
                }
            }

            try {
                // 2. Тянем свежие с сервера
                val response = NetworkClient.getApi(context).getMyPlaylists().execute()
                if (response.isSuccessful) {
                    val freshPlaylists = response.body() ?: emptyList()

                    // 3. Сохраняем в кэш и обновляем UI (Compose сам сделает diffing)
                    org.akanework.gramophone.logic.LibraryCacheManager.saveCachedPlaylists(context, freshPlaylists)
                    withContext(Dispatchers.Main) {
                        playlists = freshPlaylists
                    }
                }
            } catch (e: Exception) {
                // Игнорируем ошибку сети, т.к. UI уже отрисован из кэша
            } finally {
                withContext(Dispatchers.Main) { isLoading = false }
            }
        }
    }

    LaunchedEffect(Unit) {
        loadPlaylists()
    }



    // ДИАЛОГ СОЗДАНИЯ ПЛЕЙЛИСТА
    if (showCreateDialog) {
        Dialog(onDismissRequest = { showCreateDialog = false }) {
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth().wrapContentHeight()
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Новый плейлист", style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold))
                    Spacer(modifier = Modifier.height(16.dp))

                    // ВЫБОР ОБЛОЖКИ
                    Box(
                        modifier = Modifier
                            .size(100.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .clickable { photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                        contentAlignment = Alignment.Center
                    ) {
                        if (coverUri != null) {
                            coil3.compose.AsyncImage(model = coverUri, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                        } else {
                            Icon(painterResource(R.drawable.ic_add), contentDescription = "Добавить фото", modifier = Modifier.size(32.dp))
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = newPlaylistName, onValueChange = { newPlaylistName = it },
                        label = { Text("Название плейлиста") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)
                    )

                    // ФЛАЖОК ПУБЛИЧНОСТИ
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Публичный плейлист", color = MaterialTheme.colorScheme.onSurface)
                        Switch(checked = isPublic, onCheckedChange = { isPublic = it })
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { showCreateDialog = false }) { Text("Отмена") }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            // Кнопка недоступна, если имя пустое ИЛИ если уже идет создание
                            enabled = newPlaylistName.isNotBlank() && !isCreating,
                            onClick = {
                                isCreating = true // Включаем блокировку и спиннер
                                coroutineScope.launch(Dispatchers.IO) {
                                    try {
                                        val req = org.akanework.gramophone.logic.api.PlaylistCreateRequest(newPlaylistName.trim(), null, isPublic)
                                        val res = NetworkClient.getApi(context).createPlaylist(req).execute()

                                        if (res.isSuccessful) {
                                            val newId = res.body()?.id
                                            if (newId != null && coverUri != null) {
                                                // Загружаем картинку
                                                val inputStream = context.contentResolver.openInputStream(coverUri!!)
                                                val tempFile = File(context.cacheDir, "temp.jpg")
                                                val outputStream = FileOutputStream(tempFile)
                                                inputStream?.copyTo(outputStream)
                                                val reqFile = tempFile.asRequestBody("image/*".toMediaTypeOrNull())
                                                val body = MultipartBody.Part.createFormData("file", tempFile.name, reqFile)
                                                NetworkClient.getApi(context).uploadPlaylistCover(newId, body).execute()
                                            }

                                            // Успех! Закрываем диалог и обновляем список
                                            withContext(Dispatchers.Main) {
                                                Toast.makeText(context, "Плейлист создан!", Toast.LENGTH_SHORT).show()
                                                showCreateDialog = false
                                                newPlaylistName = ""
                                                coverUri = null
                                            }
                                            loadPlaylists()
                                        } else {
                                            withContext(Dispatchers.Main) { Toast.makeText(context, "Ошибка сервера", Toast.LENGTH_SHORT).show() }
                                        }
                                    } catch (e: Exception) {
                                        withContext(Dispatchers.Main) { Toast.makeText(context, "Ошибка сети", Toast.LENGTH_SHORT).show() }
                                    } finally {
                                        // В любом случае (даже при ошибке) снимаем блокировку
                                        withContext(Dispatchers.Main) { isCreating = false }
                                    }
                                }
                            }
                        ) {
                            // Показываем спиннер, если идет создание
                            if (isCreating) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                            } else {
                                Text("Создать")
                            }
                        }
                    }
                }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 150.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxSize()
            ) {

                // Карточка "Создать" всегда первая (индекс 0)
                item {
                    AnimatedGridItem(index = 0) {
                        CreatePlaylistCard(onClick = { showCreateDialog = true })
                    }
                }

                // Используем itemsIndexed, чтобы получить номер по порядку
                itemsIndexed(playlists, key = { _, p -> p.id }) { index, playlist ->
                    // Передаем index + 1, так как индекс 0 уже занят кнопкой создания
                    AnimatedGridItem(index = index + 1) {
                        PlaylistGridItem(
                            playlist = playlist,
                            onClick = { onPlaylistClick(playlist) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PlaylistGridItem(playlist: Playlist, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
    ) {
        PlaylistCover(
            playlist = playlist,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = playlist.title,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 4.dp)
        )

        Text(
            text = if (playlist.isPublic) "Публичный" else "Приватный",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
    }
}

@Composable
fun CreatePlaylistCard(onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_add),
                contentDescription = "Создать",
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Создать",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
    }
}

@Composable
fun AnimatedGridItem(index: Int, content: @Composable () -> Unit) {
    // Начальные значения: смещено вниз на 100 пикселей и полностью прозрачно
    val offsetY = remember { Animatable(100f) }
    val alpha = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        // Делаем задержку в зависимости от индекса (эффект каскада), но не больше 300мс
        delay((index * 40).toLong().coerceAtMost(300L))

        // Запускаем анимации параллельно
        launch {
            alpha.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 300)
            )
        }
        launch {
            offsetY.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing)
            )
        }
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