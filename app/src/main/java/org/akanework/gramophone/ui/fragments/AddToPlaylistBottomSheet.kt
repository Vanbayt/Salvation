package org.akanework.gramophone.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.akanework.gramophone.logic.api.NetworkClient
import org.akanework.gramophone.logic.api.Playlist
import org.akanework.gramophone.logic.api.PlaylistTrackAddRequest

class AddToPlaylistBottomSheet : BottomSheetDialogFragment() {

    private var trackId: Int = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Получаем ID трека, который хотим добавить
        trackId = arguments?.getInt("TRACK_ID") ?: -1
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                MaterialTheme(
                    colorScheme = if (androidx.compose.foundation.isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()
                ) {
                    AddToPlaylistScreen(
                        trackId = trackId,
                        onDismiss = { dismiss() } // Закрываем шторку при успехе
                    )
                }
            }
        }
    }

    companion object {
        fun newInstance(trackId: Int): AddToPlaylistBottomSheet {
            return AddToPlaylistBottomSheet().apply {
                arguments = Bundle().apply {
                    putInt("TRACK_ID", trackId)
                }
            }
        }
    }
}

@Composable
fun AddToPlaylistScreen(trackId: Int, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var playlists by remember { mutableStateOf<List<Playlist>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    // Загружаем список плейлистов
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            try {
                val response = NetworkClient.getApi(context).getMyPlaylists().execute()
                if (response.isSuccessful) {
                    playlists = response.body() ?: emptyList()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { Toast.makeText(context, "Ошибка загрузки", Toast.LENGTH_SHORT).show() }
            } finally {
                isLoading = false
            }
        }
    }

    // Функция добавления трека в выбранный плейлист
    fun addTrack(playlistId: Int) {
        if (trackId == -1) {
            Toast.makeText(context, "Ошибка: неверный ID трека", Toast.LENGTH_SHORT).show()
            return
        }

        coroutineScope.launch(Dispatchers.IO) {
            try {
                val request = PlaylistTrackAddRequest(trackId = trackId)
                val response = NetworkClient.getApi(context).addTrackToPlaylist(playlistId, request).execute()

                withContext(Dispatchers.Main) {
                    if (response.isSuccessful) {
                        Toast.makeText(context, "Добавлено в плейлист!", Toast.LENGTH_SHORT).show()
                        onDismiss() // Закрываем диалог
                    } else {
                        Toast.makeText(context, "Ошибка при добавлении", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { Toast.makeText(context, "Ошибка сети", Toast.LENGTH_SHORT).show() }
            }
        }
    }

    // UI шторки
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp)
    ) {
        Text(
            text = "Добавить в плейлист",
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
        )

        if (isLoading) {
            Box(modifier = Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (playlists.isEmpty()) {
            Text(
                text = "У вас пока нет плейлистов",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)
            )
        } else {
            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                items(playlists) { playlist ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { addTrack(playlist.id) }
                            .padding(horizontal = 24.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Маленькая обложка плейлиста
                        PlaylistCover(playlist = playlist, modifier = Modifier.size(48.dp))

                        Spacer(modifier = Modifier.width(16.dp))

                        Text(
                            text = playlist.title,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}