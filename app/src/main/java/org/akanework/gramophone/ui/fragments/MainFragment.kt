package org.akanework.gramophone.ui.fragments

import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.rememberNestedScrollInteropConnection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.fragment.app.Fragment
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay
import org.akanework.gramophone.R
import org.akanework.gramophone.logic.HistoryManager
import org.akanework.gramophone.logic.api.Album
import org.akanework.gramophone.logic.api.Artist
import org.akanework.gramophone.logic.api.NetworkClient
import org.akanework.gramophone.logic.api.Track
import org.akanework.gramophone.ui.AppTab
import org.akanework.gramophone.ui.MainActivity
import org.akanework.gramophone.ui.components.PlayingEqIcon
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class MainFragment : Fragment() {

    private var isMixLoading = false

    private var historyAlbumsState = mutableStateOf<List<Album>>(emptyList())
    private var historyTracksState = mutableStateOf<List<Track>>(emptyList())
    private var favoriteAlbumsState = mutableStateOf<List<Album>>(emptyList())
    private var favoriteArtistsState = mutableStateOf<List<Artist>>(emptyList())
    private var favoriteTracksState = mutableStateOf<List<Track>>(emptyList())

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_main, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupComposeContent(view)

        loadHistory()
        loadFavoriteAlbums()
        loadFavoriteArtists()
        loadFavoriteTracks()
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

    private fun setupComposeContent(view: View) {
        view.findViewById<ComposeView>(R.id.compose_content).setContent {
            MaterialTheme(colorScheme = getThemeColorScheme()) {
                val nestedScrollConnection = rememberNestedScrollInteropConnection()

                var isVisible by remember { mutableStateOf(false) }
                LaunchedEffect(Unit) { isVisible = true }

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

                val recentOrFavoriteTracks = remember(historyTracksState.value, favoriteTracksState.value) {
                    if (historyTracksState.value.isNotEmpty()) {
                        historyTracksState.value.take(6)
                    } else {
                        favoriteTracksState.value.take(6)
                    }
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .nestedScroll(nestedScrollConnection),
                    contentPadding = PaddingValues(bottom = 260.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    // 0. TOP APP BAR (Только Salvation + иконки поиска и настроек)
                    item {
                        ExpressiveTopAppBar(
                            onSearchClick = {
                                (requireActivity() as MainActivity).switchTab(AppTab.SEARCH)
                            },
                            onSettingsClick = {
                                (requireActivity() as MainActivity).startFragment(SettingsFragment())
                            }
                        )
                    }

                    // 1. HERO TITLE & FLOATING PLAY BUTTON ("Your Mix" Style)
                    item {
                        AnimatedVisibility(
                            visible = isVisible,
                            enter = fadeIn(tween(400)) + slideInVertically(tween(500)) { it / 3 }
                        ) {
                            ExpressiveYourMixHeroHeader(
                                tracksCount = favoriteTracksState.value.size,
                                onPlayClick = { if (!isMixLoading) playMyMix() }
                            )
                        }
                    }

                    // 2. BENTO QUICK ACCESS (Симметричный 2x2 грид быстрого доступа)
                    item {
                        AnimatedVisibility(
                            visible = isVisible,
                            enter = fadeIn(tween(600)) + slideInVertically(tween(700)) { it / 3 }
                        ) {
                            ExpressiveBentoGrid(
                                favoritesCount = favoriteTracksState.value.size,
                                albumsCount = favoriteAlbumsState.value.size,
                                artistsCount = favoriteArtistsState.value.size,
                                onFavoritesClick = {
                                    (requireActivity() as MainActivity).startFragment(FavoritesFragment())
                                },
                                onAlbumsClick = {
                                    (requireActivity() as MainActivity).switchTab(AppTab.LIBRARY)
                                },
                                onArtistsClick = {
                                    (requireActivity() as MainActivity).switchTab(AppTab.LIBRARY)
                                },
                                onMixClick = { if (!isMixLoading) playMyMix() }
                            )
                        }
                    }

                    // 4. НЕДАВНО ИГРАЛО / ИЗБРАННЫЕ ТРЕКИ С ЖИВЫМ ЭКВАЛАЙЗЕРОМ
                    if (recentOrFavoriteTracks.isNotEmpty()) {
                        item {
                            AnimatedVisibility(
                                visible = isVisible,
                                enter = fadeIn(tween(700)) + slideInVertically(tween(800)) { it / 3 }
                            ) {
                                ExpressiveQuickTracksSection(
                                    title = if (historyTracksState.value.isNotEmpty()) "Недавно играло" else "Избранные треки в фокусе",
                                    tracks = recentOrFavoriteTracks,
                                    currentPlayingTrackId = currentPlayingTrackId,
                                    isPlaying = isCurrentPlaying,
                                    onTrackClick = { clickedTrack ->
                                        playTrack(clickedTrack, recentOrFavoriteTracks, "Главный экран")
                                    }
                                )
                            }
                        }
                    }

                    // 5. НЕДАВНО В ЭФИРЕ (АЛЬБОМЫ)
                    if (historyAlbumsState.value.isNotEmpty()) {
                        item {
                            AnimatedVisibility(
                                visible = isVisible,
                                enter = fadeIn(tween(800)) + slideInVertically(tween(900)) { it / 3 }
                            ) {
                                SectionWithCarousel(
                                    title = "Недавно в эфире",
                                    items = historyAlbumsState.value,
                                    onItemClick = { openAlbum(it.id, null) }
                                )
                            }
                        }
                    }

                    // 6. ВАШИ АЛЬБОМЫ
                    if (favoriteAlbumsState.value.isNotEmpty()) {
                        item {
                            AnimatedVisibility(
                                visible = isVisible,
                                enter = fadeIn(tween(900)) + slideInVertically(tween(1000)) { it / 3 }
                            ) {
                                SectionWithCarousel(
                                    title = "Альбомы в коллекции",
                                    items = favoriteAlbumsState.value,
                                    onItemClick = { openAlbum(it.id, null) }
                                )
                            }
                        }
                    }

                    // 7. ВАШИ АРТИСТЫ
                    if (favoriteArtistsState.value.isNotEmpty()) {
                        item {
                            AnimatedVisibility(
                                visible = isVisible,
                                enter = fadeIn(tween(1000)) + slideInVertically(tween(1100)) { it / 3 }
                            ) {
                                SectionWithCarouselArtist(
                                    title = "Любимые артисты",
                                    items = favoriteArtistsState.value,
                                    onItemClick = { openArtist(it.id, null) }
                                )
                            }
                        }
                    }

                    // 8. СТАТИСТИКА МЕДИАТЕКИ (PixelPlayer Inspired Overview)
                    item {
                        AnimatedVisibility(
                            visible = isVisible,
                            enter = fadeIn(tween(1100)) + slideInVertically(tween(1200)) { it / 3 }
                        ) {
                            StatsOverviewCard(
                                tracksCount = favoriteTracksState.value.size,
                                albumsCount = favoriteAlbumsState.value.size,
                                artistsCount = favoriteArtistsState.value.size,
                                onClick = {
                                    (requireActivity() as MainActivity).switchTab(AppTab.LIBRARY)
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    // --- Expressive UI Widgets ---

    @Composable
    fun ExpressiveTopAppBar(
        onSearchClick: () -> Unit,
        onSettingsClick: () -> Unit
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(start = 24.dp, end = 20.dp, top = 16.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Salvation",
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontWeight = FontWeight.Black,
                    letterSpacing = (-0.5).sp
                ),
                color = MaterialTheme.colorScheme.primary
            )

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FilledTonalIconButton(
                    onClick = onSearchClick,
                    modifier = Modifier.size(46.dp),
                    shape = CircleShape,
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        contentColor = MaterialTheme.colorScheme.primary,
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    )
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_search),
                        contentDescription = "Поиск",
                        modifier = Modifier.size(22.dp)
                    )
                }

                FilledTonalIconButton(
                    onClick = onSettingsClick,
                    modifier = Modifier.size(46.dp),
                    shape = CircleShape,
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        contentColor = MaterialTheme.colorScheme.primary,
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    )
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_settings),
                        contentDescription = "Настройки",
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }

    @Composable
    fun ExpressiveYourMixHeroHeader(
        tracksCount: Int,
        onPlayClick: () -> Unit
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Ваш\nМикс",
                    style = MaterialTheme.typography.displayMedium.copy(
                        fontWeight = FontWeight.Black,
                        lineHeight = 44.sp,
                        letterSpacing = (-1.2).sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (tracksCount > 0) "$tracksCount треков для вас" else "Микс на сегодня",
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = FontWeight.Medium
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                )
            }

            // Big Floating Play Button (From Reference Screen)
            Surface(
                onClick = onPlayClick,
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                shadowElevation = 8.dp,
                tonalElevation = 6.dp,
                modifier = Modifier.size(80.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_play),
                        contentDescription = "Играть микс",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(34.dp)
                    )
                }
            }
        }
    }

    @Composable
    fun ExpressiveBentoGrid(
        favoritesCount: Int,
        albumsCount: Int,
        artistsCount: Int,
        onFavoritesClick: () -> Unit,
        onAlbumsClick: () -> Unit,
        onArtistsClick: () -> Unit,
        onMixClick: () -> Unit
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
        ) {
            Text(
                text = "Быстрый доступ",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            // ROW 1: "Любимые треки" & "Мой поток"
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                BentoCard(
                    title = "Любимые треки",
                    subtitle = if (favoritesCount > 0) "$favoritesCount треков" else "Медиатека",
                    iconRes = R.drawable.ic_favorite_filled,
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                    contentColor = MaterialTheme.colorScheme.primary,
                    onClick = onFavoritesClick,
                    modifier = Modifier.weight(1f)
                )

                BentoCard(
                    title = "Мой поток",
                    subtitle = "Автомикс",
                    iconRes = R.drawable.ic_shuffle,
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.55f),
                    contentColor = MaterialTheme.colorScheme.tertiary,
                    onClick = onMixClick,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // ROW 2: "Альбомы" & "Артисты"
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                BentoCard(
                    title = "Альбомы",
                    subtitle = if (albumsCount > 0) "$albumsCount релизов" else "Дискография",
                    iconRes = R.drawable.ic_library,
                    containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f),
                    contentColor = MaterialTheme.colorScheme.secondary,
                    onClick = onAlbumsClick,
                    modifier = Modifier.weight(1f)
                )

                BentoCard(
                    title = "Артисты",
                    subtitle = if (artistsCount > 0) "$artistsCount авторов" else "Исполнители",
                    iconRes = R.drawable.ic_person,
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    onClick = onArtistsClick,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }

    @Composable
    fun BentoCard(
        title: String,
        subtitle: String,
        iconRes: Int,
        containerColor: Color,
        contentColor: Color,
        onClick: () -> Unit,
        modifier: Modifier = Modifier
    ) {
        Surface(
            onClick = onClick,
            modifier = modifier.height(78.dp),
            shape = RoundedCornerShape(22.dp),
            color = containerColor,
            tonalElevation = 1.dp
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = contentColor.copy(alpha = 0.18f),
                    modifier = Modifier.size(42.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            painter = painterResource(id = iconRes),
                            contentDescription = null,
                            tint = contentColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }

    @Composable
    fun ExpressiveQuickTracksSection(
        title: String,
        tracks: List<Track>,
        currentPlayingTrackId: String?,
        isPlaying: Boolean,
        onTrackClick: (Track) -> Unit
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
            )

            Column(
                modifier = Modifier.padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                tracks.forEach { track ->
                    val isThisTrackPlaying = track.id == currentPlayingTrackId
                    val cardBgColor by animateColorAsState(
                        targetValue = if (isThisTrackPlaying) {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.7f)
                        },
                        animationSpec = tween(250),
                        label = "trackCardBg"
                    )

                    Surface(
                        onClick = { onTrackClick(track) },
                        shape = RoundedCornerShape(20.dp),
                        color = cardBgColor,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp)
                        ) {
                            val coverUrl = if (track.cover?.startsWith("/") == true) "http://185.196.41.31${track.cover}" else track.cover
                            AsyncImage(
                                model = coverUrl,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(52.dp)
                                    .clip(RoundedCornerShape(14.dp))
                            )

                            Spacer(modifier = Modifier.width(14.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = track.title,
                                    style = MaterialTheme.typography.bodyLarge.copy(
                                        fontWeight = if (isThisTrackPlaying) FontWeight.ExtraBold else FontWeight.Bold
                                    ),
                                    color = if (isThisTrackPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
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

                            if (isThisTrackPlaying) {
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        PlayingEqIcon(
                                            color = MaterialTheme.colorScheme.primary,
                                            isPlaying = isPlaying,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            } else {
                                FilledTonalIconButton(
                                    onClick = { onTrackClick(track) },
                                    modifier = Modifier.size(40.dp),
                                    shape = CircleShape
                                ) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_play),
                                        contentDescription = "Играть",
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    fun SectionWithCarousel(title: String, items: List<Album>, onItemClick: (Album) -> Unit) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
            )
            LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(items, key = { it.id }) { album ->
                    Card(
                        modifier = Modifier
                            .width(172.dp)
                            .height(224.dp)
                            .clickable { onItemClick(album) },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                        shape = RoundedCornerShape(22.dp)
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
                                            0.35f to Color.Transparent,
                                            0.7f to Color.Black.copy(alpha = 0.55f),
                                            1.0f to Color.Black.copy(alpha = 0.90f)
                                        )
                                    )
                            )

                            Row(
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = album.title,
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold),
                                        color = Color.White,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = album.artistName.uppercase(),
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = Color(0xFFD0D0D0),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                Surface(
                                    shape = CircleShape,
                                    color = Color.White.copy(alpha = 0.25f),
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            painter = painterResource(id = R.drawable.ic_play),
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    fun SectionWithCarouselArtist(title: String, items: List<Artist>, onItemClick: (Artist) -> Unit) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
            )
            LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(items, key = { it.id }) { artist ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .width(108.dp)
                            .clickable { onItemClick(artist) }
                    ) {
                        Surface(
                            shape = CircleShape,
                            border = BorderStroke(2.5.dp, MaterialTheme.colorScheme.primaryContainer),
                            tonalElevation = 3.dp,
                            modifier = Modifier.size(100.dp)
                        ) {
                            val coverUrl = if (artist.cover?.startsWith("/") == true) "http://185.196.41.31${artist.cover}" else artist.cover
                            AsyncImage(
                                model = coverUrl,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = artist.name,
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }

    @Composable
    fun StatsOverviewCard(
        tracksCount: Int,
        albumsCount: Int,
        artistsCount: Int,
        onClick: () -> Unit
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            onClick = onClick
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_equalizer),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "ВАША МЕДИАТЕКА",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Black,
                                letterSpacing = 1.sp
                            ),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    StatItem(title = if (tracksCount > 0) "$tracksCount" else "—", subtitle = "Треков")
                    StatItem(title = if (albumsCount > 0) "$albumsCount" else "—", subtitle = "Альбомов")
                    StatItem(title = if (artistsCount > 0) "$artistsCount" else "—", subtitle = "Артистов")
                }
            }
        }
    }

    @Composable
    private fun StatItem(title: String, subtitle: String) {
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Black),
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    // --- Data Loaders & Playback Handlers ---

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

    private fun loadFavoriteTracks() {
        NetworkClient.getApi(requireContext()).getFavorites(skip = 0, limit = 10000)
            .enqueue(object : Callback<List<Track>> {
                override fun onResponse(call: Call<List<Track>>, response: Response<List<Track>>) {
                    if (isAdded && response.isSuccessful) {
                        favoriteTracksState.value = response.body() ?: emptyList()
                    }
                }
                override fun onFailure(call: Call<List<Track>>, t: Throwable) {}
            })
    }

    private fun loadHistory() {
        val historyList = HistoryManager.getHistory(requireContext())
        historyAlbumsState.value = historyList.filterIsInstance<Album>()
        historyTracksState.value = historyList.filterIsInstance<Track>()
    }

    private fun openArtist(id: String, sharedView: View?) {
        val fragment = ArtistFragment.newInstance(id)
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
            val finalCoverUrl = if (track.cover?.startsWith("/") == true) "http://185.196.41.31${track.cover}" else track.cover
            MediaItem.Builder()
                .setMediaId(track.id)
                .setUri("http://185.196.41.31/stream/${track.id}".toUri())
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(track.title)
                        .setArtist(track.artist)
                        .setArtworkUri(finalCoverUrl?.toUri())
                        .setDurationMs((track.duration * 1000L).takeIf { it > 0 })
                        .setExtras(extrasBundle)
                        .build()
                ).build()
        }
        player.setMediaItems(mediaItems, startIndex, 0L)
        player.prepare()
        player.play()
    }

    private fun playRandomFavoriteTrack() {
        val tracks = favoriteTracksState.value
        if (tracks.isNotEmpty()) {
            val randomTrack = tracks.random()
            playTrack(randomTrack, tracks, "Случайная находка")
            Toast.makeText(context, "Играет: ${randomTrack.title}", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "Ваш список избранного пуст", Toast.LENGTH_SHORT).show()
        }
    }

    private fun playMyMix() {
        isMixLoading = true
        Toast.makeText(context, "Собираем микс...", Toast.LENGTH_SHORT).show()

        NetworkClient.getApi(requireContext()).getFavorites(skip = 0, limit = 10000)
            .enqueue(object : Callback<List<Track>> {
                override fun onResponse(call: Call<List<Track>>, response: Response<List<Track>>) {
                    isMixLoading = false
                    if (isAdded && response.isSuccessful) {
                        val tracks = response.body() ?: emptyList()
                        if (tracks.isNotEmpty()) {
                            val shuffledTracks = org.akanework.gramophone.logic.utils.ShuffleUtils.balancedShuffle(tracks) { item ->
                                item.artist.lowercase()
                            }
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