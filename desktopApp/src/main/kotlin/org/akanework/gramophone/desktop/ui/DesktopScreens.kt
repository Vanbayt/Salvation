package org.akanework.gramophone.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.akanework.gramophone.desktop.api.*
import org.akanework.gramophone.desktop.cache.DesktopCacheManager
import org.akanework.gramophone.desktop.player.DesktopSalvationPlayer

enum class LibraryTab(val title: String) {
    TRACKS("Треки"),
    ALBUMS("Альбомы"),
    PLAYLISTS("Плейлисты"),
    ARTISTS("Артисты")
}

sealed class LibrarySubView {
    object Main : LibrarySubView()
    data class AlbumDetail(val album: Album, val tracks: List<Track>, val isLoading: Boolean = false) : LibrarySubView()
    data class PlaylistDetail(val playlist: Playlist, val tracks: List<Track>) : LibrarySubView()
    data class ArtistDetail(val artist: Artist, val tracks: List<Track>, val albums: List<Album> = emptyList()) : LibrarySubView()
}

fun formatCoverUrl(url: String?): String? {
    if (url.isNullOrEmpty()) return null
    return if (url.startsWith("/")) "http://185.196.41.31$url" else url
}

@Composable
fun DesktopLibraryScreen(
    player: DesktopSalvationPlayer,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    var selectedTab by remember { mutableStateOf(LibraryTab.TRACKS) }
    var currentSubView by remember { mutableStateOf<LibrarySubView>(LibrarySubView.Main) }

    // Instant initial load from disk cache
    var tracks by remember { mutableStateOf(DesktopCacheManager.loadCachedTracks()) }
    var albums by remember { mutableStateOf(DesktopCacheManager.loadCachedAlbums()) }
    var playlists by remember { mutableStateOf(DesktopCacheManager.loadCachedPlaylists()) }
    var artists by remember { mutableStateOf(DesktopCacheManager.loadCachedArtists()) }

    var isLoading by remember { mutableStateOf(tracks.isEmpty()) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showCreatePlaylistDialog by remember { mutableStateOf(false) }

    val currentTrack by player.currentTrack.collectAsState()
    val isPlaying by player.isPlaying.collectAsState()
    val token = DesktopNetworkClient.tokenStorage.getToken()

    fun loadData(isManual: Boolean = false) {
        if (isManual) isLoading = true
        errorMessage = null
        scope.launch {
            withContext(Dispatchers.IO) {
                try {
                    val api = DesktopNetworkClient.getApi()
                    
                    // 1. Favorites Tracks
                    val favResp = api.getFavorites(skip = 0, limit = 5000).execute()
                    if (favResp.isSuccessful && favResp.body() != null) {
                        val freshTracks = favResp.body()!!
                        tracks = freshTracks
                        DesktopCacheManager.saveCachedTracks(freshTracks)
                    }

                    // 2. Favorite Albums
                    val albResp = api.getFavoriteAlbums().execute()
                    if (albResp.isSuccessful && albResp.body() != null) {
                        val freshAlbums = albResp.body()!!
                        albums = freshAlbums
                        DesktopCacheManager.saveCachedAlbums(freshAlbums)
                    }

                    // 3. Playlists
                    val plResp = api.getMyPlaylists().execute()
                    if (plResp.isSuccessful && plResp.body() != null) {
                        val freshPlaylists = plResp.body()!!
                        playlists = freshPlaylists
                        DesktopCacheManager.saveCachedPlaylists(freshPlaylists)
                    }

                    // 4. Favorite Artists
                    val artResp = api.getFavoriteArtists().execute()
                    if (artResp.isSuccessful && artResp.body() != null) {
                        val freshArtists = artResp.body()!!
                        artists = freshArtists
                        DesktopCacheManager.saveCachedArtists(freshArtists)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    if (tracks.isEmpty()) {
                        errorMessage = "Не удалось загрузить данные: ${e.localizedMessage}"
                    }
                } finally {
                    isLoading = false
                }
            }
        }
    }

    LaunchedEffect(token) {
        if (!token.isNullOrEmpty()) {
            loadData()
        } else {
            isLoading = false
        }
    }

    // Обработка перехода в детальный экран (Альбом, Плейлист, Артист)
    if (currentSubView !is LibrarySubView.Main) {
        when (val sub = currentSubView) {
            is LibrarySubView.AlbumDetail -> {
                AlbumDetailScreen(
                    album = sub.album,
                    initialTracks = sub.tracks,
                    player = player,
                    onBack = { currentSubView = LibrarySubView.Main }
                )
            }
            is LibrarySubView.PlaylistDetail -> {
                PlaylistDetailScreen(
                    playlist = sub.playlist,
                    initialTracks = sub.tracks,
                    player = player,
                    onBack = {
                        currentSubView = LibrarySubView.Main
                        loadData(isManual = true)
                    }
                )
            }
            is LibrarySubView.ArtistDetail -> {
                ArtistDetailScreen(
                    artist = sub.artist,
                    initialTracks = sub.tracks,
                    initialAlbums = sub.albums,
                    player = player,
                    onBack = { currentSubView = LibrarySubView.Main },
                    onOpenAlbum = { alb ->
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                try {
                                    val pageResp = DesktopNetworkClient.getApi().getAlbumPage(alb.id).execute()
                                    val loaded = if (pageResp.isSuccessful && pageResp.body() != null) pageResp.body()!! else alb
                                    withContext(Dispatchers.Main) {
                                        currentSubView = LibrarySubView.AlbumDetail(loaded, loaded.tracks ?: emptyList())
                                    }
                                } catch (e: Exception) {
                                    withContext(Dispatchers.Main) {
                                        currentSubView = LibrarySubView.AlbumDetail(alb, alb.tracks ?: emptyList())
                                    }
                                }
                            }
                        }
                    }
                )
            }
            else -> {}
        }
        return
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(top = 24.dp, start = 32.dp, end = 32.dp)
    ) {
        // Заголовок + действия
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Медиатека",
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                color = colors.onSurface
            )

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (selectedTab == LibraryTab.PLAYLISTS) {
                    Button(
                        onClick = { showCreatePlaylistDialog = true },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = colors.primary)
                    ) {
                        Icon(imageVector = Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Новый плейлист")
                    }
                }

                IconButton(onClick = { loadData(isManual = true) }) {
                    Icon(imageVector = Icons.Rounded.Refresh, contentDescription = "Refresh")
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Таблетки переключения вкладок
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            LibraryTab.values().forEach { tab ->
                val isSelected = selectedTab == tab
                FilterChip(
                    selected = isSelected,
                    onClick = { selectedTab = tab },
                    label = { Text(text = tab.title, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                    shape = RoundedCornerShape(20.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        if (isLoading && tracks.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (errorMessage != null && tracks.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = errorMessage!!, color = MaterialTheme.colorScheme.error)
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(onClick = { loadData(isManual = true) }) { Text("Повторить попытку") }
                }
            }
        } else {
            when (selectedTab) {
                // 1. ТРЕКИ
                LibraryTab.TRACKS -> {
                    if (tracks.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(text = if (token.isNullOrEmpty()) "Войдите в аккаунт в Настройках" else "Медиатека пуста", color = colors.onSurfaceVariant)
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Button(
                                onClick = { player.playWithSmartShuffle(tracks) },
                                shape = RoundedCornerShape(20.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = colors.primaryContainer, contentColor = colors.onPrimaryContainer)
                            ) {
                                Icon(imageVector = Icons.Rounded.Shuffle, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(text = "Перемешать", fontWeight = FontWeight.SemiBold)
                            }

                            Spacer(modifier = Modifier.width(16.dp))

                            Text(
                                text = "${tracks.size} треков",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.onSurfaceVariant
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        DesktopSongTableHeader()
                        HorizontalDivider(color = colors.outlineVariant.copy(alpha = 0.2f))

                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            itemsIndexed(tracks) { idx, track ->
                                DesktopSongTableRow(
                                    index = idx + 1,
                                    track = track,
                                    isPlaying = isPlaying,
                                    isActiveTrack = currentTrack?.id == track.id,
                                    onClick = { player.playTrack(track, tracks) },
                                    onLikeClick = {
                                        track.is_liked = !track.is_liked
                                        tracks = tracks.toList()
                                        DesktopCacheManager.saveCachedTracks(tracks)
                                    }
                                )
                            }
                        }
                    }
                }

                // 2. АЛЬБОМЫ
                LibraryTab.ALBUMS -> {
                    val displayAlbums = remember(albums, tracks) {
                        if (albums.isNotEmpty()) {
                            albums
                        } else {
                            // Fallback
                            tracks.filter { !it.album.isNullOrBlank() }
                                .groupBy { it.album!! }
                                .map { (albumTitle, albumTracks) ->
                                    Album(
                                        id = albumTitle,
                                        title = albumTitle,
                                        artistName = albumTracks.firstOrNull()?.artist ?: "Разные исполнители",
                                        cover = albumTracks.firstOrNull { !it.cover.isNullOrEmpty() }?.cover,
                                        tracks = albumTracks
                                    )
                                }
                        }
                    }

                    if (displayAlbums.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(imageVector = Icons.Rounded.Album, contentDescription = null, modifier = Modifier.size(56.dp), tint = colors.onSurfaceVariant.copy(alpha = 0.4f))
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(text = "Любимые альбомы не найдены", color = colors.onSurfaceVariant)
                            }
                        }
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(185.dp),
                            horizontalArrangement = Arrangement.spacedBy(20.dp),
                            verticalArrangement = Arrangement.spacedBy(24.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(displayAlbums) { album ->
                                Column(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable {
                                            scope.launch {
                                                withContext(Dispatchers.IO) {
                                                    try {
                                                        val resp = DesktopNetworkClient.getApi().getAlbumPage(album.id).execute()
                                                        val loaded = if (resp.isSuccessful && resp.body() != null) resp.body()!! else album
                                                        val trkList = loaded.tracks ?: album.tracks ?: emptyList()
                                                        withContext(Dispatchers.Main) {
                                                            currentSubView = LibrarySubView.AlbumDetail(loaded, trkList)
                                                        }
                                                    } catch (e: Exception) {
                                                        withContext(Dispatchers.Main) {
                                                            currentSubView = LibrarySubView.AlbumDetail(album, album.tracks ?: emptyList())
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                        .padding(8.dp)
                                ) {
                                    AsyncImage(
                                        model = formatCoverUrl(album.cover),
                                        contentDescription = album.title,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .aspectRatio(1f)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(colors.surfaceContainerHighest)
                                    )
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Text(
                                        text = album.title,
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${album.artistName ?: "Неизвестный исполнитель"}${if (album.releaseYear != null && album.releaseYear > 0) " • ${album.releaseYear}" else ""}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = colors.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }

                // 3. ПЛЕЙЛИСТЫ
                LibraryTab.PLAYLISTS -> {
                    if (playlists.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.QueueMusic,
                                    contentDescription = null,
                                    modifier = Modifier.size(56.dp),
                                    tint = colors.onSurfaceVariant.copy(alpha = 0.5f)
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(text = "У вас пока нет созданных плейлистов", color = colors.onSurfaceVariant)
                                Spacer(modifier = Modifier.height(16.dp))
                                Button(
                                    onClick = { showCreatePlaylistDialog = true },
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(imageVector = Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Создать плейлист")
                                }
                            }
                        }
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(185.dp),
                            horizontalArrangement = Arrangement.spacedBy(20.dp),
                            verticalArrangement = Arrangement.spacedBy(24.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(playlists) { pl ->
                                Column(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable {
                                            scope.launch {
                                                withContext(Dispatchers.IO) {
                                                    try {
                                                        val resp = DesktopNetworkClient.getApi().getPlaylistTracks(pl.id).execute()
                                                        val plTracks = if (resp.isSuccessful && resp.body() != null) resp.body()!! else emptyList()
                                                        withContext(Dispatchers.Main) {
                                                            currentSubView = LibrarySubView.PlaylistDetail(pl, plTracks)
                                                        }
                                                    } catch (e: Exception) {
                                                        e.printStackTrace()
                                                    }
                                                }
                                            }
                                        }
                                        .padding(8.dp)
                                ) {
                                    val coverModel = formatCoverUrl(pl.coverUrl ?: pl.autoCovers.firstOrNull())
                                    AsyncImage(
                                        model = coverModel,
                                        contentDescription = pl.title,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .aspectRatio(1f)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(colors.surfaceContainerHighest)
                                    )
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Text(
                                        text = pl.title,
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = pl.description ?: "Плейлист",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = colors.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }

                // 4. АРТИСТЫ
                LibraryTab.ARTISTS -> {
                    val displayArtists = remember(artists, tracks) {
                        if (artists.isNotEmpty()) {
                            artists
                        } else {
                            // Fallback
                            tracks.groupBy { it.artist }
                                .map { (name, artistTracks) ->
                                    Artist(
                                        id = name,
                                        name = name,
                                        cover = artistTracks.firstOrNull { !it.cover.isNullOrEmpty() }?.cover,
                                        tracks = artistTracks
                                    )
                                }
                        }
                    }

                    if (displayArtists.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(text = "Артисты не найдены", color = colors.onSurfaceVariant)
                        }
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(160.dp),
                            horizontalArrangement = Arrangement.spacedBy(20.dp),
                            verticalArrangement = Arrangement.spacedBy(20.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(displayArtists) { artist ->
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable {
                                            scope.launch {
                                                withContext(Dispatchers.IO) {
                                                    try {
                                                        val profResp = DesktopNetworkClient.getApi().getArtistProfile(artist.id).execute()
                                                        val discoResp = DesktopNetworkClient.getApi().getArtistDiscography(artist.id).execute()
                                                        val loadedArt = if (profResp.isSuccessful && profResp.body() != null) profResp.body()!! else artist
                                                        val loadedDisco = if (discoResp.isSuccessful && discoResp.body() != null) discoResp.body()!! else emptyList()
                                                        val trks = loadedArt.topTracks ?: loadedArt.tracks ?: tracks.filter { it.artist.equals(artist.name, ignoreCase = true) }
                                                        withContext(Dispatchers.Main) {
                                                            currentSubView = LibrarySubView.ArtistDetail(loadedArt, trks, loadedDisco)
                                                        }
                                                    } catch (e: Exception) {
                                                        val trks = tracks.filter { it.artist.equals(artist.name, ignoreCase = true) }
                                                        withContext(Dispatchers.Main) {
                                                            currentSubView = LibrarySubView.ArtistDetail(artist, trks)
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                        .padding(12.dp)
                                ) {
                                    AsyncImage(
                                        model = formatCoverUrl(artist.picture ?: artist.cover),
                                        contentDescription = artist.name,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .size(110.dp)
                                            .clip(CircleShape)
                                            .background(colors.surfaceContainerHighest)
                                    )
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Text(
                                        text = artist.name,
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        textAlign = TextAlign.Center
                                    )
                                    Text(
                                        text = "Исполнитель",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = colors.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Диалог создания плейлиста
    if (showCreatePlaylistDialog) {
        var plTitle by remember { mutableStateOf("") }
        var plDesc by remember { mutableStateOf("") }
        var isCreating by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showCreatePlaylistDialog = false },
            title = { Text("Новый плейлист") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = plTitle,
                        onValueChange = { plTitle = it },
                        label = { Text("Название плейлиста") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = plDesc,
                        onValueChange = { plDesc = it },
                        label = { Text("Описание (необязательно)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (plTitle.isNotBlank()) {
                            isCreating = true
                            scope.launch {
                                withContext(Dispatchers.IO) {
                                    try {
                                        val req = PlaylistCreateRequest(title = plTitle.trim(), description = plDesc.trim())
                                        val resp = DesktopNetworkClient.getApi().createPlaylist(req).execute()
                                        if (resp.isSuccessful) {
                                            loadData(isManual = true)
                                        }
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                    } finally {
                                        isCreating = false
                                        showCreatePlaylistDialog = false
                                    }
                                }
                            }
                        }
                    },
                    enabled = plTitle.isNotBlank() && !isCreating
                ) {
                    Text("Создать")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreatePlaylistDialog = false }) {
                    Text("Отмена")
                }
            }
        )
    }
}

// ================= ДЕТАЛИЗИРОВАННЫЕ ЭКРАНЫ =================

@Composable
fun AlbumDetailScreen(
    album: Album,
    initialTracks: List<Track>,
    player: DesktopSalvationPlayer,
    onBack: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val currentTrack by player.currentTrack.collectAsState()
    val isPlaying by player.isPlaying.collectAsState()
    var tracks by remember { mutableStateOf(initialTracks) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 24.dp, start = 32.dp, end = 32.dp)
    ) {
        IconButton(onClick = onBack) {
            Icon(imageVector = Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Назад")
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = formatCoverUrl(album.cover),
                contentDescription = album.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(160.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(colors.surfaceContainerHighest)
            )

            Spacer(modifier = Modifier.width(24.dp))

            Column {
                Text(text = "АЛЬБОМ", style = MaterialTheme.typography.labelMedium, color = colors.primary)
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = album.title, style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold))
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "${album.artistName ?: "Неизвестный исполнитель"}${if (album.releaseYear != null && album.releaseYear > 0) " • ${album.releaseYear}" else ""} • ${tracks.size} треков",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = { if (tracks.isNotEmpty()) player.playTrack(tracks.first(), tracks) },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(imageVector = Icons.Rounded.PlayArrow, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Слушать")
                    }

                    OutlinedButton(
                        onClick = { if (tracks.isNotEmpty()) player.playWithSmartShuffle(tracks) },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(imageVector = Icons.Rounded.Shuffle, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Перемешать")
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        DesktopSongTableHeader()
        HorizontalDivider(color = colors.outlineVariant.copy(alpha = 0.2f))

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            itemsIndexed(tracks) { idx, track ->
                DesktopSongTableRow(
                    index = idx + 1,
                    track = track,
                    isPlaying = isPlaying,
                    isActiveTrack = currentTrack?.id == track.id,
                    onClick = { player.playTrack(track, tracks) },
                    onLikeClick = { track.is_liked = !track.is_liked }
                )
            }
        }
    }
}

@Composable
fun PlaylistDetailScreen(
    playlist: Playlist,
    initialTracks: List<Track>,
    player: DesktopSalvationPlayer,
    onBack: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    var tracks by remember { mutableStateOf(initialTracks) }
    val currentTrack by player.currentTrack.collectAsState()
    val isPlaying by player.isPlaying.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 24.dp, start = 32.dp, end = 32.dp)
    ) {
        IconButton(onClick = onBack) {
            Icon(imageVector = Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Назад")
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val coverModel = formatCoverUrl(playlist.coverUrl ?: playlist.autoCovers.firstOrNull())
            AsyncImage(
                model = coverModel,
                contentDescription = playlist.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(160.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(colors.surfaceContainerHighest)
            )

            Spacer(modifier = Modifier.width(24.dp))

            Column {
                Text(text = "ПЛЕЙЛИСТ", style = MaterialTheme.typography.labelMedium, color = colors.primary)
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = playlist.title, style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold))
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "${playlist.description ?: "Пользовательский плейлист"} • ${tracks.size} треков",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = { if (tracks.isNotEmpty()) player.playTrack(tracks.first(), tracks) },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(imageVector = Icons.Rounded.PlayArrow, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Слушать")
                    }

                    OutlinedButton(
                        onClick = { if (tracks.isNotEmpty()) player.playWithSmartShuffle(tracks) },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(imageVector = Icons.Rounded.Shuffle, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Перемешать")
                    }

                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                withContext(Dispatchers.IO) {
                                    try {
                                        DesktopNetworkClient.getApi().deletePlaylist(playlist.id).execute()
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                    }
                                }
                                onBack()
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Icon(imageVector = Icons.Rounded.Delete, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Удалить")
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        DesktopSongTableHeader()
        HorizontalDivider(color = colors.outlineVariant.copy(alpha = 0.2f))

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            itemsIndexed(tracks) { idx, track ->
                DesktopSongTableRow(
                    index = idx + 1,
                    track = track,
                    isPlaying = isPlaying,
                    isActiveTrack = currentTrack?.id == track.id,
                    onClick = { player.playTrack(track, tracks) },
                    onLikeClick = { track.is_liked = !track.is_liked }
                )
            }
        }
    }
}

@Composable
fun ArtistDetailScreen(
    artist: Artist,
    initialTracks: List<Track>,
    initialAlbums: List<Album> = emptyList(),
    player: DesktopSalvationPlayer,
    onBack: () -> Unit,
    onOpenAlbum: (Album) -> Unit = {}
) {
    val colors = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    val currentTrack by player.currentTrack.collectAsState()
    val isPlaying by player.isPlaying.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 24.dp, start = 32.dp, end = 32.dp)
    ) {
        IconButton(onClick = onBack) {
            Icon(imageVector = Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Назад")
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = formatCoverUrl(artist.picture ?: artist.cover),
                contentDescription = artist.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(160.dp)
                    .clip(CircleShape)
                    .background(colors.surfaceContainerHighest)
            )

            Spacer(modifier = Modifier.width(24.dp))

            Column {
                Text(text = "ИСПОЛНИТЕЛЬ", style = MaterialTheme.typography.labelMedium, color = colors.primary)
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = artist.name, style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold))
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "${initialTracks.size} треков • ${initialAlbums.size} релизов",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = { if (initialTracks.isNotEmpty()) player.playTrack(initialTracks.first(), initialTracks) },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(imageVector = Icons.Rounded.PlayArrow, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Слушать все")
                    }

                    OutlinedButton(
                        onClick = { if (initialTracks.isNotEmpty()) player.playWithSmartShuffle(initialTracks) },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(imageVector = Icons.Rounded.Shuffle, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Перемешать")
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        if (initialAlbums.isNotEmpty()) {
            Text(text = "Релизы и Альбомы", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                initialAlbums.take(5).forEach { alb ->
                    Column(
                        modifier = Modifier
                            .width(130.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onOpenAlbum(alb) }
                            .padding(4.dp)
                    ) {
                        AsyncImage(
                            model = formatCoverUrl(alb.cover),
                            contentDescription = alb.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(122.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(colors.surfaceContainerHighest)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(text = alb.title, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(text = "${alb.releaseYear ?: ""}", style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
                    }
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
        }

        Text(text = "Популярные треки", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
        Spacer(modifier = Modifier.height(8.dp))
        DesktopSongTableHeader()
        HorizontalDivider(color = colors.outlineVariant.copy(alpha = 0.2f))

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            itemsIndexed(initialTracks) { idx, track ->
                DesktopSongTableRow(
                    index = idx + 1,
                    track = track,
                    isPlaying = isPlaying,
                    isActiveTrack = currentTrack?.id == track.id,
                    onClick = { player.playTrack(track, initialTracks) },
                    onLikeClick = { track.is_liked = !track.is_liked }
                )
            }
        }
    }
}

@Composable
fun DesktopHomeScreen(player: DesktopSalvationPlayer) {
    DesktopLibraryScreen(player = player)
}

@Composable
fun DesktopSearchScreen(player: DesktopSalvationPlayer) {
    val colors = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Track>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }

    val currentTrack by player.currentTrack.collectAsState()
    val isPlaying by player.isPlaying.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 24.dp, start = 32.dp, end = 32.dp)
    ) {
        Text(
            text = "Поиск",
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold)
        )

        Spacer(modifier = Modifier.height(20.dp))

        OutlinedTextField(
            value = query,
            onValueChange = {
                query = it
                if (it.length >= 2) {
                    isSearching = true
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            try {
                                val resp = DesktopNetworkClient.getApi().searchMusic(it).execute()
                                if (resp.isSuccessful && resp.body() != null) {
                                    results = resp.body()!!.tracks
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                            } finally {
                                isSearching = false
                            }
                        }
                    }
                } else if (it.isEmpty()) {
                    results = emptyList()
                }
            },
            placeholder = { Text("Треки, исполнители, альбомы...") },
            leadingIcon = { Icon(imageVector = Icons.Rounded.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { query = ""; results = emptyList() }) {
                        Icon(imageVector = Icons.Rounded.Clear, contentDescription = null)
                    }
                }
            },
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(24.dp))

        if (isSearching) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (results.isNotEmpty()) {
            DesktopSongTableHeader()
            HorizontalDivider(color = colors.outlineVariant.copy(alpha = 0.2f))

            LazyColumn(modifier = Modifier.fillMaxSize()) {
                itemsIndexed(results) { idx, track ->
                    DesktopSongTableRow(
                        index = idx + 1,
                        track = track,
                        isPlaying = isPlaying,
                        isActiveTrack = currentTrack?.id == track.id,
                        onClick = { player.playTrack(track, results) },
                        onLikeClick = { track.is_liked = !track.is_liked }
                    )
                }
            }
        } else if (query.isNotBlank()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Ничего не найдено", color = colors.onSurfaceVariant)
            }
        }
    }
}
