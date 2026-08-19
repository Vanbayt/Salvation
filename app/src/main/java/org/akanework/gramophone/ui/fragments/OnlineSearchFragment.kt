package org.akanework.gramophone.ui.fragments

import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.rememberNestedScrollInteropConnection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.fragment.app.Fragment
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.akanework.gramophone.R
import org.akanework.gramophone.logic.HistoryManager
import org.akanework.gramophone.logic.api.Album
import org.akanework.gramophone.logic.api.Artist
import org.akanework.gramophone.logic.api.NetworkClient
import org.akanework.gramophone.logic.api.SearchResponse
import org.akanework.gramophone.logic.api.Track
import org.akanework.gramophone.ui.MainActivity
import org.akanework.gramophone.ui.components.PlayingEqIcon
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class OnlineSearchFragment : Fragment() {

    private val PREFS_NAME = "salvation_search"
    private val KEY_HISTORY = "history_list"

    private data class GenreCategory(
        val title: String,
        val query: String,
        val gradient: Brush,
        val iconRes: Int
    )

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val composeView = ComposeView(requireContext())
        composeView.setContent {
            MaterialTheme(colorScheme = getThemeColorScheme()) {
                SearchScreenContent()
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

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun SearchScreenContent() {
        var searchQuery by remember { mutableStateOf("") }
        var isSearching by remember { mutableStateOf(false) }
        var selectedFilterIndex by remember { mutableIntStateOf(0) } // 0 = Все, 1 = Треки, 2 = Артисты, 3 = Альбомы
        var searchResult by remember { mutableStateOf<SearchResponse?>(null) }
        var historyList by remember { mutableStateOf(getHistory()) }
        var selectedTrackForSheet by remember { mutableStateOf<Track?>(null) }

        val focusRequester = remember { FocusRequester() }
        val focusManager = LocalFocusManager.current
        val keyboardController = LocalSoftwareKeyboardController.current
        val nestedScrollConnection = rememberNestedScrollInteropConnection()
        val mainActivity = remember { requireActivity() as MainActivity }

        var currentPlayingTrackId by remember { mutableStateOf<String?>(null) }
        var isCurrentPlaying by remember { mutableStateOf(false) }

        LaunchedEffect(Unit) {
            while (true) {
                val p = mainActivity.getPlayer()
                currentPlayingTrackId = p?.currentMediaItem?.mediaId
                isCurrentPlaying = p?.isPlaying == true
                delay(350)
            }
        }

        // Live Debounced Search
        LaunchedEffect(searchQuery) {
            val q = searchQuery.trim()
            if (q.length > 1) {
                delay(350)
                isSearching = true
                NetworkClient.getApi(requireContext()).searchMusic(q)
                    .enqueue(object : Callback<SearchResponse> {
                        override fun onResponse(call: Call<SearchResponse>, response: Response<SearchResponse>) {
                            isSearching = false
                            if (isAdded && response.isSuccessful) {
                                searchResult = response.body()
                                saveToHistory(q)
                                historyList = getHistory()
                            }
                        }

                        override fun onFailure(call: Call<SearchResponse>, t: Throwable) {
                            isSearching = false
                        }
                    })
            } else if (q.isEmpty()) {
                isSearching = false
                searchResult = null
            }
        }

        val genres = remember {
            listOf(
                GenreCategory("Рок & Альтернатива", "Рок", Brush.linearGradient(listOf(Color(0xFFE53935), Color(0xFFFF7043))), R.drawable.ic_equalizer),
                GenreCategory("Поп-музыка", "Поп", Brush.linearGradient(listOf(Color(0xFF8E24AA), Color(0xFFFF4081))), R.drawable.ic_favorite_filled),
                GenreCategory("Электроника & EDM", "Электроника", Brush.linearGradient(listOf(Color(0xFF00897B), Color(0xFF00E5FF))), R.drawable.ic_equalizer),
                GenreCategory("Хип-хоп & Рэп", "Рэп", Brush.linearGradient(listOf(Color(0xFFFF8F00), Color(0xFFFFD54F))), R.drawable.ic_equalizer),
                GenreCategory("Инди & Lo-Fi", "Инди", Brush.linearGradient(listOf(Color(0xFF2E7D32), Color(0xFF81C784))), R.drawable.ic_favorite_filled),
                GenreCategory("Джаз & Соул", "Джаз", Brush.linearGradient(listOf(Color(0xFF3949AB), Color(0xFF7986CB))), R.drawable.ic_library),
                GenreCategory("Саундтреки & Кино", "OST", Brush.linearGradient(listOf(Color(0xFF455A64), Color(0xFF90A4AE))), R.drawable.ic_library),
                GenreCategory("Метал & Драйв", "Метал", Brush.linearGradient(listOf(Color(0xFF37474F), Color(0xFFD32F2F))), R.drawable.ic_equalizer)
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .nestedScroll(nestedScrollConnection)
        ) {
            // 1. TOP SEARCH BAR
            SearchTopBar(
                searchQuery = searchQuery,
                onQueryChange = { searchQuery = it },
                onClearClick = {
                    searchQuery = ""
                    searchResult = null
                },
                focusRequester = focusRequester,
                onSearchAction = {
                    keyboardController?.hide()
                    focusManager.clearFocus()
                }
            )

            // 2. LOADING LINE INDICATOR
            if (isSearching) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceContainer
                )
            } else {
                Spacer(modifier = Modifier.height(3.dp))
            }

            // 3. FILTER CHIPS (Visible during search)
            AnimatedVisibility(visible = searchQuery.isNotBlank() && searchResult != null) {
                FilterChipsBar(
                    selectedIndex = selectedFilterIndex,
                    onSelect = { selectedFilterIndex = it }
                )
            }

            // 4. MAIN CONTENT AREA
            if (searchQuery.isBlank()) {
                // DISCOVERY MODE (History + Genre Categories)
                DiscoveryContent(
                    historyList = historyList,
                    genres = genres,
                    onHistoryClick = { query ->
                        searchQuery = query
                    },
                    onRemoveHistoryItem = { query ->
                        removeFromHistory(query)
                        historyList = getHistory()
                    },
                    onClearAllHistory = {
                        clearAllHistory()
                        historyList = emptyList()
                    },
                    onGenreClick = { genre ->
                        searchQuery = genre.query
                    }
                )
            } else {
                // ACTIVE RESULTS MODE
                val res = searchResult
                if (res != null) {
                    val tracks = res.tracks ?: emptyList()
                    val artists = res.artists ?: emptyList()
                    val albums = res.albums ?: emptyList()

                    val isNoResults = tracks.isEmpty() && artists.isEmpty() && albums.isEmpty() && !isSearching

                    if (isNoResults) {
                        EmptyResultsView(searchQuery = searchQuery)
                    } else {
                        SearchResultsList(
                            filterIndex = selectedFilterIndex,
                            tracks = tracks,
                            artists = artists,
                            albums = albums,
                            currentPlayingTrackId = currentPlayingTrackId,
                            isPlaying = isCurrentPlaying,
                            onTrackClick = { clickedTrack ->
                                HistoryManager.saveToHistory(requireContext(), clickedTrack)
                                playTrack(clickedTrack, tracks, "Поиск")
                            },
                            onTrackMoreClick = { track ->
                                selectedTrackForSheet = track
                            },
                            onArtistClick = { artist ->
                                HistoryManager.saveToHistory(requireContext(), artist)
                                openArtist(artist.id.toString())
                            },
                            onAlbumClick = { album ->
                                HistoryManager.saveToHistory(requireContext(), album)
                                openAlbum(album.id)
                            }
                        )
                    }
                }
            }
        }

        // TRACK CONTEXT BOTTOM SHEET
        selectedTrackForSheet?.let { track ->
            ModalBottomSheet(
                onDismissRequest = { selectedTrackForSheet = null },
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
            ) {
                TrackOptionsBottomSheetContent(
                    track = track,
                    onDismiss = { selectedTrackForSheet = null },
                    onAddToPlaylist = {
                        selectedTrackForSheet = null
                        val trackId = track.id.toIntOrNull()
                        if (trackId != null) {
                            val sheet = AddToPlaylistBottomSheet.newInstance(trackId)
                            sheet.show(requireActivity().supportFragmentManager, "ADD_TO_PLAYLIST_SHEET")
                        } else {
                            Toast.makeText(requireContext(), "Ошибка ID трека", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onPlayNext = {
                        selectedTrackForSheet = null
                        addTrackToQueueNext(track)
                    },
                    onGoToArtist = {
                        selectedTrackForSheet = null
                        track.artistId?.let { openArtist(it) } ?: Toast.makeText(requireContext(), "Артист неизвестен", Toast.LENGTH_SHORT).show()
                    },
                    onGoToAlbum = {
                        selectedTrackForSheet = null
                        track.albumId?.let { openAlbum(it) } ?: Toast.makeText(requireContext(), "Альбом неизвестен", Toast.LENGTH_SHORT).show()
                    }
                )
            }
        }
    }

    // --- Search UI Components ---

    @Composable
    private fun SearchTopBar(
        searchQuery: String,
        onQueryChange: (String) -> Unit,
        onClearClick: () -> Unit,
        focusRequester: FocusRequester,
        onSearchAction: () -> Unit
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 2.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_search),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )

                Spacer(modifier = Modifier.width(12.dp))

                TextField(
                    value = searchQuery,
                    onValueChange = onQueryChange,
                    placeholder = {
                        Text(
                            text = "Треки, артисты, альбомы...",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    },
                    singleLine = true,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                        cursorColor = MaterialTheme.colorScheme.primary
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { onSearchAction() }),
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focusRequester)
                )

                if (searchQuery.isNotEmpty()) {
                    IconButton(
                        onClick = onClearClick,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Очистить",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }

    @Composable
    private fun FilterChipsBar(
        selectedIndex: Int,
        onSelect: (Int) -> Unit
    ) {
        val filters = listOf("Все", "Треки", "Артисты", "Альбомы")
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(filters.size) { index ->
                val selected = selectedIndex == index
                FilterChip(
                    selected = selected,
                    onClick = { onSelect(index) },
                    label = {
                        Text(
                            text = filters[index],
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                        )
                    },
                    shape = RoundedCornerShape(16.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        labelColor = MaterialTheme.colorScheme.onSurface
                    ),
                    border = null
                )
            }
        }
    }

    @Composable
    private fun DiscoveryContent(
        historyList: List<String>,
        genres: List<GenreCategory>,
        onHistoryClick: (String) -> Unit,
        onRemoveHistoryItem: (String) -> Unit,
        onClearAllHistory: () -> Unit,
        onGenreClick: (GenreCategory) -> Unit
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 260.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // Search History
            if (historyList.isNotEmpty()) {
                item {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Недавние запросы",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            TextButton(onClick = onClearAllHistory) {
                                Text(
                                    text = "Очистить",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            historyList.forEach { query ->
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    tonalElevation = 1.dp,
                                    onClick = { onHistoryClick(query) }
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(start = 12.dp, end = 6.dp, top = 6.dp, bottom = 6.dp)
                                    ) {
                                        Icon(
                                            painter = painterResource(id = R.drawable.ic_search),
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = query,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        IconButton(
                                            onClick = { onRemoveHistoryItem(query) },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.Close,
                                                contentDescription = "Удалить",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Genre & Mood Explore Grid
            item {
                Text(
                    text = "Обзор жанров и настроений",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(vertical = 6.dp)
                )
            }

            // 2-Column Grid of Curated Genres
            val chunkedGenres = genres.chunked(2)
            items(chunkedGenres) { rowGenres ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    rowGenres.forEach { genre ->
                        GenreExploreCard(
                            genre = genre,
                            onClick = { onGenreClick(genre) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    if (rowGenres.size == 1) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }

    @Composable
    private fun GenreExploreCard(
        genre: GenreCategory,
        onClick: () -> Unit,
        modifier: Modifier = Modifier
    ) {
        Card(
            onClick = onClick,
            modifier = modifier.height(100.dp),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = Color.Transparent),
            elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(genre.gradient)
                    .padding(14.dp)
            ) {
                Text(
                    text = genre.title,
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Black,
                        color = Color.White
                    ),
                    modifier = Modifier.align(Alignment.TopStart)
                )

                Surface(
                    shape = CircleShape,
                    color = Color.White.copy(alpha = 0.25f),
                    modifier = Modifier
                        .size(36.dp)
                        .align(Alignment.BottomEnd)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            painter = painterResource(id = genre.iconRes),
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }

    @Composable
    private fun SearchResultsList(
        filterIndex: Int,
        tracks: List<Track>,
        artists: List<Artist>,
        albums: List<Album>,
        currentPlayingTrackId: String?,
        isPlaying: Boolean,
        onTrackClick: (Track) -> Unit,
        onTrackMoreClick: (Track) -> Unit,
        onArtistClick: (Artist) -> Unit,
        onAlbumClick: (Album) -> Unit
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 260.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1. TOP RESULT CARD (When "Все" is selected)
            if (filterIndex == 0) {
                if (artists.isNotEmpty()) {
                    item {
                        val topArtist = artists.first()
                        TopResultArtistCard(artist = topArtist, onClick = { onArtistClick(topArtist) })
                    }
                } else if (tracks.isNotEmpty()) {
                    item {
                        val topTrack = tracks.first()
                        TopResultTrackCard(
                            track = topTrack,
                            isCurrentlyPlaying = topTrack.id == currentPlayingTrackId,
                            isPlaying = isPlaying,
                            onClick = { onTrackClick(topTrack) }
                        )
                    }
                }
            }

            // 2. ARTISTS SECTION
            if ((filterIndex == 0 || filterIndex == 2) && artists.isNotEmpty()) {
                item {
                    Text(
                        text = "Исполнители",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                    LazyRow(
                        contentPadding = PaddingValues(vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(artists, key = { it.id }) { artist ->
                            ArtistSearchAvatar(artist = artist, onClick = { onArtistClick(artist) })
                        }
                    }
                }
            }

            // 3. ALBUMS SECTION
            if ((filterIndex == 0 || filterIndex == 3) && albums.isNotEmpty()) {
                item {
                    Text(
                        text = "Альбомы",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                    LazyRow(
                        contentPadding = PaddingValues(vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        items(albums, key = { it.id }) { album ->
                            AlbumSearchCard(album = album, onClick = { onAlbumClick(album) })
                        }
                    }
                }
            }

            // 4. TRACKS SECTION
            if (filterIndex == 0 || filterIndex == 1) {
                if (tracks.isNotEmpty()) {
                    item {
                        Text(
                            text = "Треки",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }

                    val tracksToDisplay = if (filterIndex == 0 && artists.isNotEmpty()) tracks.take(15) else tracks
                    items(tracksToDisplay, key = { it.id }) { track ->
                        TrackSearchRow(
                            track = track,
                            isCurrentlyPlaying = track.id == currentPlayingTrackId,
                            isPlaying = isPlaying,
                            onClick = { onTrackClick(track) },
                            onMoreClick = { onTrackMoreClick(track) }
                        )
                    }
                }
            }
        }
    }

    @Composable
    private fun TopResultArtistCard(artist: Artist, onClick: () -> Unit) {
        Surface(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
            tonalElevation = 2.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val coverUrl = if (artist.cover?.startsWith("/") == true) "http://185.196.41.31${artist.cover}" else artist.cover
                AsyncImage(
                    model = coverUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                )

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = "ЛУЧШИЙ РЕЗУЛЬТАТ",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Black,
                                letterSpacing = 0.8.sp
                            ),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = artist.name,
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Black),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Исполнитель",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }

    @Composable
    private fun TopResultTrackCard(
        track: Track,
        isCurrentlyPlaying: Boolean,
        isPlaying: Boolean,
        onClick: () -> Unit
    ) {
        Surface(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
            tonalElevation = 2.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val coverUrl = if (track.cover?.startsWith("/") == true) "http://185.196.41.31${track.cover}" else track.cover
                AsyncImage(
                    model = coverUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(68.dp)
                        .clip(RoundedCornerShape(18.dp))
                )

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = "ЛУЧШИЙ РЕЗУЛЬТАТ",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Black,
                                letterSpacing = 0.8.sp
                            ),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = track.title,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black),
                        color = MaterialTheme.colorScheme.onSurface,
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

                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (isCurrentlyPlaying) {
                            PlayingEqIcon(
                                color = MaterialTheme.colorScheme.onPrimary,
                                isPlaying = isPlaying,
                                modifier = Modifier.size(20.dp)
                            )
                        } else {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_play),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun ArtistSearchAvatar(artist: Artist, onClick: () -> Unit) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .width(96.dp)
                .clickable { onClick() }
        ) {
            Surface(
                shape = CircleShape,
                border = BorderStroke(2.dp, MaterialTheme.colorScheme.primaryContainer),
                tonalElevation = 2.dp,
                modifier = Modifier.size(86.dp)
            ) {
                val coverUrl = if (artist.cover?.startsWith("/") == true) "http://185.196.41.31${artist.cover}" else artist.cover
                AsyncImage(
                    model = coverUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = artist.name,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        }
    }

    @Composable
    private fun AlbumSearchCard(album: Album, onClick: () -> Unit) {
        Card(
            modifier = Modifier
                .width(150.dp)
                .height(195.dp)
                .clickable { onClick() },
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                val coverUrl = if (album.cover?.startsWith("/") == true) "http://185.196.41.31${album.cover}" else album.cover
                AsyncImage(
                    model = coverUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                0.4f to Color.Transparent,
                                0.8f to Color.Black.copy(alpha = 0.7f),
                                1.0f to Color.Black.copy(alpha = 0.92f)
                            )
                        )
                )

                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(10.dp)
                ) {
                    Text(
                        text = album.title,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Black),
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = album.artistName,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = Color(0xFFD0D0D0),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }

    @Composable
    private fun TrackSearchRow(
        track: Track,
        isCurrentlyPlaying: Boolean,
        isPlaying: Boolean,
        onClick: () -> Unit,
        onMoreClick: () -> Unit
    ) {
        val cardBgColor by animateColorAsState(
            targetValue = if (isCurrentlyPlaying) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
            } else {
                MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.6f)
            },
            animationSpec = tween(250),
            label = "searchTrackBg"
        )

        Surface(
            onClick = onClick,
            shape = RoundedCornerShape(18.dp),
            color = cardBgColor,
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
                        .size(50.dp)
                        .clip(RoundedCornerShape(14.dp))
                )

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = track.title,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = if (isCurrentlyPlaying) FontWeight.ExtraBold else FontWeight.Bold
                        ),
                        color = if (isCurrentlyPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = track.artist,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (isCurrentlyPlaying) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(38.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            PlayingEqIcon(
                                color = MaterialTheme.colorScheme.primary,
                                isPlaying = isPlaying,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                } else {
                    IconButton(
                        onClick = onMoreClick,
                        modifier = Modifier.size(38.dp)
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_more_vert),
                            contentDescription = "Опции",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }

    @Composable
    private fun EmptyResultsView(searchQuery: String) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.size(80.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_search),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }
                Text(
                    text = "Ничего не найдено",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "По запросу «$searchQuery» ничего не удалось найти. Попробуйте изменить формулировку.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
    }

    @Composable
    private fun TrackOptionsBottomSheetContent(
        track: Track,
        onDismiss: () -> Unit,
        onAddToPlaylist: () -> Unit,
        onPlayNext: () -> Unit,
        onGoToArtist: () -> Unit,
        onGoToAlbum: () -> Unit
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp)
        ) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                val coverUrl = if (track.cover?.startsWith("/") == true) "http://185.196.41.31${track.cover}" else track.cover
                AsyncImage(
                    model = coverUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(14.dp))
                )

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = track.title,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
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
            }

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                modifier = Modifier.padding(bottom = 8.dp)
            )

            // Option 1: Добавить в плейлист
            OptionItem(
                title = "Добавить в плейлист",
                iconRes = R.drawable.ic_playlist_add,
                onClick = onAddToPlaylist
            )

            // Option 2: Слушать следующим
            OptionItem(
                title = "Воспроизвести следующим",
                iconRes = R.drawable.ic_playlist_play,
                onClick = onPlayNext
            )

            // Option 3: Перейти к исполнителю
            OptionItem(
                title = "Перейти к исполнителю",
                iconRes = R.drawable.ic_person,
                onClick = onGoToArtist
            )

            // Option 4: Перейти к альбому
            OptionItem(
                title = "Перейти к альбому",
                iconRes = R.drawable.ic_library,
                onClick = onGoToAlbum
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    @Composable
    private fun OptionItem(
        title: String,
        iconRes: Int,
        onClick: () -> Unit
    ) {
        Surface(
            onClick = onClick,
            shape = RoundedCornerShape(16.dp),
            color = Color.Transparent,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp, horizontal = 8.dp)
            ) {
                Icon(
                    painter = painterResource(id = iconRes),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }

    // --- Search History & Playback Logic ---

    private fun getHistory(): List<String> {
        val prefs = requireContext().getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE)
        val historyStr = prefs.getString(KEY_HISTORY, "") ?: ""
        return if (historyStr.isEmpty()) emptyList() else historyStr.split("||")
    }

    private fun saveToHistory(query: String) {
        val prefs = requireContext().getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE)
        val history = getHistory().toMutableList()
        history.remove(query)
        history.add(0, query)
        if (history.size > 10) history.removeAt(history.lastIndex)
        prefs.edit().putString(KEY_HISTORY, history.joinToString("||")).apply()
    }

    private fun removeFromHistory(query: String) {
        val prefs = requireContext().getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE)
        val history = getHistory().toMutableList()
        history.remove(query)
        prefs.edit().putString(KEY_HISTORY, history.joinToString("||")).apply()
    }

    private fun clearAllHistory() {
        val prefs = requireContext().getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE)
        prefs.edit().remove(KEY_HISTORY).apply()
    }

    private fun openArtist(id: String) {
        val fragment = ArtistFragment.newInstance(id)
        (requireActivity() as MainActivity).startFragment(fragment, null, null)
    }

    private fun openAlbum(id: String) {
        val fragment = AlbumFragment.newInstance(id)
        (requireActivity() as MainActivity).startFragment(fragment, null, null)
    }

    private fun playTrack(clickedTrack: Track, allTracks: List<Track>, source: String = "Поиск") {
        val player = (requireActivity() as MainActivity).getPlayer() ?: return
        val startIndex = allTracks.indexOf(clickedTrack).coerceAtLeast(0)

        val mediaItems = allTracks.map { track ->
            val extrasBundle = Bundle().apply {
                putFloat("replay_gain", track.replayGain)
                putString("ARTIST_ID", track.artistId)
                putString("ALBUM_ID", track.albumId)
                putString("PLAYING_FROM", source)
            }
            val finalCoverUrl = if (track.cover?.startsWith("/") == true) "http://185.196.41.31${track.cover}" else track.cover
            MediaItem.Builder()
                .setMediaId(track.id)
                .setUri("http://185.196.41.31/stream/${track.id}".toUri())
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(track.title)
                        .setArtist(track.artist)
                        .setArtworkUri(finalCoverUrl?.toUri())
                        .setAlbumTitle(track.album)
                        .setDurationMs((track.duration * 1000L).takeIf { it > 0 })
                        .setExtras(extrasBundle)
                        .build()
                ).build()
        }
        player.setMediaItems(mediaItems, startIndex, 0L)
        player.prepare()
        player.play()
    }

    private fun addTrackToQueueNext(track: Track) {
        val player = (requireActivity() as MainActivity).getPlayer()
        if (player == null) {
            Toast.makeText(context, "Плеер не готов", Toast.LENGTH_SHORT).show()
            return
        }

        val finalCoverUrl = if (track.cover?.startsWith("/") == true) "http://185.196.41.31${track.cover}" else track.cover
        val extrasBundle = Bundle().apply {
            putFloat("replay_gain", track.replayGain)
            putString("ARTIST_ID", track.artistId)
            putString("ALBUM_ID", track.albumId)
            putString("PLAYING_FROM", "Очередь (Поиск)")
        }

        val mediaItem = MediaItem.Builder()
            .setMediaId(track.id)
            .setUri("http://185.196.41.31/stream/${track.id}".toUri())
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(track.title)
                    .setArtist(track.artist)
                    .setArtworkUri(finalCoverUrl?.toUri())
                    .setAlbumTitle(track.album)
                    .setDurationMs((track.duration * 1000L).takeIf { it > 0 })
                    .setExtras(extrasBundle)
                    .build()
            )
            .build()

        val insertIndex = if (player.mediaItemCount > 0) player.currentMediaItemIndex + 1 else 0
        player.addMediaItem(insertIndex, mediaItem)
        Toast.makeText(context, "Добавлено в очередь", Toast.LENGTH_SHORT).show()
    }
}