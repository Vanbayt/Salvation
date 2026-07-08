package org.akanework.gramophone.ui.fragments

import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.rememberNestedScrollInteropConnection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.fragment.app.Fragment
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import coil3.compose.AsyncImage
import org.akanework.gramophone.R
import org.akanework.gramophone.logic.api.Album
import org.akanework.gramophone.logic.api.Artist
import org.akanework.gramophone.logic.api.NetworkClient
import org.akanework.gramophone.logic.api.Track
import org.akanework.gramophone.ui.MainActivity
import org.akanework.gramophone.ui.components.GreetingHeader
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import coil3.request.crossfade
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.background

class MainFragment : Fragment() {

    private var isMixLoading = false

    // 🔥 Состояния для списков Compose
    private var historyItemsState = mutableStateOf<List<Album>>(emptyList())
    private var favoriteAlbumsState = mutableStateOf<List<Album>>(emptyList())
    private var favoriteArtistsState = mutableStateOf<List<Artist>>(emptyList())

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_main, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupComposeHeader(view)
        setupComposeContent(view)

        loadHistory()
        loadFavoriteAlbums()
        loadFavoriteArtists()
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

    private fun setupComposeHeader(view: View) {
        view.findViewById<ComposeView>(R.id.compose_header).setContent {
            MaterialTheme(colorScheme = getThemeColorScheme()) {
                GreetingHeader(
                    onMixClick = { if (!isMixLoading) playMyMix() },
                    onSearchClick = {
// ✅ ДОБАВИТЬ ЭТО:
                        (requireActivity() as org.akanework.gramophone.ui.MainActivity).switchTab(org.akanework.gramophone.ui.AppTab.SEARCH)
                    },
                    onSettingsClick = {
                        (requireActivity() as MainActivity).startFragment(SettingsFragment())
                    }
                )
            }
        }
    }

    private fun setupComposeContent(view: View) {
        view.findViewById<ComposeView>(R.id.compose_content).setContent {
            MaterialTheme(colorScheme = getThemeColorScheme()) {
                val nestedScrollConnection = rememberNestedScrollInteropConnection()

                var isVisible by remember { mutableStateOf(false) }
                LaunchedEffect(Unit) { isVisible = true }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .nestedScroll(nestedScrollConnection),
                    // 🔥 УВЕЛИЧИЛИ ОТСТУП ДО 250dp: Теперь всё 100% будет выше плеера
                    contentPadding = PaddingValues(top = 8.dp, bottom = 250.dp)
                ) {

                    // Недавно играло
                    item {
                        if (historyItemsState.value.isNotEmpty()) {
                            AnimatedVisibility(
                                visible = isVisible,
                                enter = fadeIn(tween(500)) + slideInVertically(tween(700)) { it / 3 }
                            ) {
                                SectionWithCarousel(
                                    title = "Недавно играло",
                                    items = historyItemsState.value,
                                    onItemClick = { openAlbum(it.id, null) }
                                )
                            }
                        }
                    }

                    // Альбомы
                    item {
                        if (favoriteAlbumsState.value.isNotEmpty()) {
                            AnimatedVisibility(
                                visible = isVisible,
                                enter = fadeIn(tween(700)) + slideInVertically(tween(900)) { it / 3 }
                            ) {
                                SectionWithCarousel(
                                    title = "Ваши альбомы",
                                    items = favoriteAlbumsState.value,
                                    onItemClick = { openAlbum(it.id, null) }
                                )
                            }
                        }
                    }

                    // Артисты
                    item {
                        if (favoriteArtistsState.value.isNotEmpty()) {
                            AnimatedVisibility(
                                visible = isVisible,
                                enter = fadeIn(tween(900)) + slideInVertically(tween(1100)) { it / 3 }
                            ) {
                                SectionWithCarouselArtist(
                                    title = "Ваши артисты",
                                    items = favoriteArtistsState.value,
                                    onItemClick = { openArtist(it.id, null) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // --- Compose Виджеты ---

    @Composable
    fun SectionWithCarousel(title: String, items: List<Album>, onItemClick: (Album) -> Unit) {
        Column(modifier = Modifier.padding(top = 24.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
            )
            LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(items, key = { it.id }) { album ->
                    Card(
                        // 🔥 Увеличили размер карточки альбома до 164dp
                        modifier = Modifier
                            .size(200.dp)
                            .clickable { onItemClick(album) },
                        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                        shape = RoundedCornerShape(28.dp)
                    ) {
                        Box(modifier = Modifier.fillMaxSize()) {

                            // СЛОЙ 1: Обложка
                            AsyncImage(
                                model = album.cover,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )

                            // 🔥 СЛОЙ 2: Улучшенный градиент (работает по процентам)
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.verticalGradient(
                                            0.4f to Color.Transparent, // Верхние 40% прозрачные
                                            0.7f to Color.Black.copy(alpha = 0.5f), // Плавный переход
                                            1.0f to Color.Black.copy(alpha = 0.9f)  // Плотный низ
                                        )
                                    )
                            )

                            // СЛОЙ 3: Текстовый блок
                            Column(
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(14.dp) // Чуть больше отступ для большой карточки
                            ) {
                                Text(
                                    text = album.title,
                                    // 🔥 Сделали шрифт крупнее и МАКСИМАЛЬНО ЖИРНЫМ
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "ARTIST ${album.artistName.uppercase()}",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                    color = Color(0xFFD0D0D0),
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
    fun SectionWithCarouselArtist(title: String, items: List<Artist>, onItemClick: (Artist) -> Unit) {
        Column(modifier = Modifier.padding(top = 24.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
            )
            LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(items, key = { it.id }) { artist ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .width(110.dp)
                            .clickable { onItemClick(artist) }
                    ) {
                        AsyncImage(
                            model = artist.cover,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(110.dp)
                                .clip(RoundedCornerShape(50)) // Круглые аватарки для артистов
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = artist.name,
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }

    // --- Загрузка данных (Обновление State) ---

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

    private fun loadHistory() {
        val historyList = org.akanework.gramophone.logic.HistoryManager.getHistory(requireContext())
        val albumsOnly = historyList.filterIsInstance<Album>()
        historyItemsState.value = albumsOnly
    }

    private fun openArtist(id: String, sharedView: View?) {
        val fragment = ArtistFragment.newInstance(id)
        // В Compose мы используем плавный fade (передаем null вместо view)
        (requireActivity() as MainActivity).startFragment(fragment, null, null)
    }

    private fun openAlbum(id: String, sharedView: View?) {
        val fragment = AlbumFragment.newInstance(id)
        (requireActivity() as MainActivity).startFragment(fragment, null, null)
    }

    private fun playTrack(clickedTrack: Track, allTracks: List<Track>, source: String = "Неизвестно") {
        val player = (requireActivity() as MainActivity).getPlayer() ?: return
        val startIndex = allTracks.indexOf(clickedTrack).coerceAtLeast(0)

        val mediaItems = allTracks.map { track ->
            val extrasBundle = Bundle().apply {
                putFloat("replay_gain", track.replayGain)
                putString("ARTIST_ID", track.artistId)
                putString("ALBUM_ID", track.albumId)
                putString("PLAYING_FROM", source)
            }
            MediaItem.Builder()
                .setMediaId(track.id)
                .setUri("http://185.196.41.31/stream/${track.id}".toUri())
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(track.title)
                        .setArtist(track.artist)
                        .setArtworkUri(track.cover?.toUri())
                        .setExtras(extrasBundle)
                        .build()
                ).build()
        }
        player.setMediaItems(mediaItems, startIndex, 0L)
        player.prepare()
        player.play()
    }

    private fun playMyMix() {
        isMixLoading = true
        Toast.makeText(context, "Собираем микс...", Toast.LENGTH_SHORT).show()

        NetworkClient.getApi(requireContext()).getFavorites(0, 500)
            .enqueue(object : Callback<List<Track>> {
                override fun onResponse(call: Call<List<Track>>, response: Response<List<Track>>) {
                    isMixLoading = false
                    if (isAdded && response.isSuccessful) {
                        val tracks = response.body() ?: emptyList()
                        if (tracks.isNotEmpty()) {
                            val shuffledTracks = tracks.shuffled()
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
}