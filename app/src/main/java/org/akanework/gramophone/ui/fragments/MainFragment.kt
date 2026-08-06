package org.akanework.gramophone.ui.fragments

import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
import org.akanework.gramophone.R
import org.akanework.gramophone.logic.api.Album
import org.akanework.gramophone.logic.api.Artist
import org.akanework.gramophone.logic.api.NetworkClient
import org.akanework.gramophone.logic.api.Track
import org.akanework.gramophone.ui.AppTab
import org.akanework.gramophone.ui.MainActivity
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.util.Calendar

class MainFragment : Fragment() {

    private var isMixLoading = false

    private var historyItemsState = mutableStateOf<List<Album>>(emptyList())
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

                var selectedCategory by remember { mutableStateOf("Все") }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .nestedScroll(nestedScrollConnection),
                    // 🔥 Большой отступ от нижнего бара и плеера (260dp), статус бар padding сверху
                    contentPadding = PaddingValues(bottom = 260.dp)
                ) {
                    // 0. TOP HEADER (Встроен прямо в LazyColumn с безопасными отступами от статуса)
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

                    // 1. HERO SPOTLIGHT CARD (Фокус медиатеки)
                    item {
                        AnimatedVisibility(
                            visible = isVisible,
                            enter = fadeIn(tween(400)) + slideInVertically(tween(500)) { it / 3 }
                        ) {
                            ExpressiveSpotlightHeroCard(
                                tracksCount = favoriteTracksState.value.size,
                                albumsCount = favoriteAlbumsState.value.size,
                                latestAlbum = historyItemsState.value.firstOrNull(),
                                onMixClick = { if (!isMixLoading) playMyMix() },
                                onRandomClick = { playRandomFavoriteTrack() }
                            )
                        }
                    }

                    // 2. SMART QUICK GRID (Асимметричные плитки быстрого доступа с разной геометрией)
                    item {
                        AnimatedVisibility(
                            visible = isVisible,
                            enter = fadeIn(tween(500)) + slideInVertically(tween(600)) { it / 3 }
                        ) {
                            ExpressiveAsymmetricQuickGrid(
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
                                onHistoryClick = {
                                    if (historyItemsState.value.isNotEmpty()) {
                                        openAlbum(historyItemsState.value.first().id, null)
                                    } else {
                                        Toast.makeText(context, "История пуста", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                onMixClick = { if (!isMixLoading) playMyMix() },
                                onSearchClick = {
                                    (requireActivity() as MainActivity).switchTab(AppTab.SEARCH)
                                }
                            )
                        }
                    }

                    // 3. ИЗБРАННЫЕ ТРЕКИ В ФОКУСЕ
                    if (favoriteTracksState.value.isNotEmpty()) {
                        item {
                            AnimatedVisibility(
                                visible = isVisible,
                                enter = fadeIn(tween(600)) + slideInVertically(tween(700)) { it / 3 }
                            ) {
                                ExpressiveQuickTracksSection(
                                    tracks = favoriteTracksState.value.take(6),
                                    onTrackClick = { clickedTrack ->
                                        playTrack(clickedTrack, favoriteTracksState.value, "Избранное (Главная)")
                                    }
                                )
                            }
                        }
                    }

                    // 4. НЕДАВНО В ЭФИРЕ
                    if (historyItemsState.value.isNotEmpty()) {
                        item {
                            AnimatedVisibility(
                                visible = isVisible,
                                enter = fadeIn(tween(700)) + slideInVertically(tween(800)) { it / 3 }
                            ) {
                                SectionWithCarousel(
                                    title = "Недавно в эфире",
                                    items = historyItemsState.value,
                                    onItemClick = { openAlbum(it.id, null) }
                                )
                            }
                        }
                    }

                    // 5. ВАШИ АЛЬБОМЫ
                    if (favoriteAlbumsState.value.isNotEmpty()) {
                        item {
                            AnimatedVisibility(
                                visible = isVisible,
                                enter = fadeIn(tween(800)) + slideInVertically(tween(900)) { it / 3 }
                            ) {
                                SectionWithCarousel(
                                    title = "Ваши альбомы",
                                    items = favoriteAlbumsState.value,
                                    onItemClick = { openAlbum(it.id, null) }
                                )
                            }
                        }
                    }

                    // 6. ВАШИ АРТИСТЫ
                    if (favoriteArtistsState.value.isNotEmpty()) {
                        item {
                            AnimatedVisibility(
                                visible = isVisible,
                                enter = fadeIn(tween(900)) + slideInVertically(tween(1000)) { it / 3 }
                            ) {
                                SectionWithCarouselArtist(
                                    title = "Любимые артисты",
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

    // --- Expressive UI Widgets ---

    @Composable
    fun ExpressiveTopAppBar(
        onSearchClick: () -> Unit,
        onSettingsClick: () -> Unit
    ) {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val greetingText = when (hour) {
            in 5..11 -> "Доброе утро"
            in 12..16 -> "Добрый день"
            in 17..23 -> "Добрый вечер"
            else -> "Доброй ночи"
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding() // 🔥 Безопасный отступ от статусного бара
                .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Salvation",
                    style = MaterialTheme.typography.headlineLarge.copy(
                        fontWeight = FontWeight.Black,
                        letterSpacing = (-0.5).sp
                    ),
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = greetingText,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalIconButton(
                    onClick = onSearchClick,
                    modifier = Modifier.size(46.dp),
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
    fun ExpressiveSpotlightHeroCard(
        tracksCount: Int,
        albumsCount: Int,
        latestAlbum: Album?,
        onMixClick: () -> Unit,
        onRandomClick: () -> Unit
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            shape = RoundedCornerShape(topStart = 36.dp, topEnd = 16.dp, bottomEnd = 36.dp, bottomStart = 16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primaryContainer,
                                MaterialTheme.colorScheme.tertiaryContainer,
                                MaterialTheme.colorScheme.surfaceContainerHigh
                            )
                        )
                    )
                    .padding(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_equalizer),
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "ФОКУС МЕДИАТЕКИ",
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            fontWeight = FontWeight.Black,
                                            letterSpacing = 1.2.sp
                                        ),
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = "Музыкальный поток",
                            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Black),
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Spacer(modifier = Modifier.height(2.dp))

                        Text(
                            text = if (tracksCount > 0) "$tracksCount треков в коллекции" else "Загрузка вашей музыки...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(18.dp))

                        // 🔥 КНОПКИ БЕЗ ПЕРЕНОСА СТРОК И КРИВЫХ ТЕКСТОВ
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Button(
                                onClick = onMixClick,
                                modifier = Modifier
                                    .weight(1.2f)
                                    .height(48.dp),
                                shape = RoundedCornerShape(16.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_play),
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Микс",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            FilledTonalButton(
                                onClick = onRandomClick,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp),
                                shape = RoundedCornerShape(16.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_shuffle),
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Случайный",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }

                    if (latestAlbum != null) {
                        Spacer(modifier = Modifier.width(14.dp))
                        Card(
                            shape = RoundedCornerShape(20.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                            modifier = Modifier
                                .size(96.dp)
                                .clickable { openAlbum(latestAlbum.id, null) }
                        ) {
                            val coverUrl = if (latestAlbum.cover?.startsWith("/") == true) "http://185.196.41.31${latestAlbum.cover}" else latestAlbum.cover
                            AsyncImage(
                                model = coverUrl,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
            }
        }
    }

    @Composable
    fun ExpressiveAsymmetricQuickGrid(
        favoritesCount: Int,
        albumsCount: Int,
        artistsCount: Int,
        onFavoritesClick: () -> Unit,
        onAlbumsClick: () -> Unit,
        onArtistsClick: () -> Unit,
        onHistoryClick: () -> Unit,
        onMixClick: () -> Unit,
        onSearchClick: () -> Unit
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            Text(
                text = "Быстрый доступ",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            // ROW 1: Wide Hero Tile (Избранное) + Pill Tile (Мой Микс)
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                AsymmetricShapeCard(
                    title = "Избранное",
                    subtitle = if (favoritesCount > 0) "$favoritesCount треков" else "Любимое",
                    iconRes = R.drawable.ic_favorite_filled,
                    shape = RoundedCornerShape(topStart = 32.dp, topEnd = 14.dp, bottomEnd = 32.dp, bottomStart = 14.dp),
                    cardHeight = 84.dp,
                    onClick = onFavoritesClick,
                    modifier = Modifier.weight(1.3f)
                )

                AsymmetricShapeCard(
                    title = "Мой Микс",
                    subtitle = "Микс дня",
                    iconRes = R.drawable.ic_shuffle,
                    shape = RoundedCornerShape(26.dp),
                    cardHeight = 84.dp,
                    onClick = onMixClick,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // ROW 2: Organic Pill (Альбомы) + Opposite Asymmetric (Артисты)
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                AsymmetricShapeCard(
                    title = "Альбомы",
                    subtitle = if (albumsCount > 0) "$albumsCount релизов" else "Дискография",
                    iconRes = R.drawable.ic_library,
                    shape = RoundedCornerShape(28.dp),
                    cardHeight = 76.dp,
                    onClick = onAlbumsClick,
                    modifier = Modifier.weight(1f)
                )

                AsymmetricShapeCard(
                    title = "Артисты",
                    subtitle = if (artistsCount > 0) "$artistsCount авторов" else "Исполнители",
                    iconRes = R.drawable.ic_person,
                    shape = RoundedCornerShape(topStart = 14.dp, topEnd = 32.dp, bottomEnd = 14.dp, bottomStart = 32.dp),
                    cardHeight = 76.dp,
                    onClick = onArtistsClick,
                    modifier = Modifier.weight(1.2f)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // ROW 3: Smooth Rounded Cards (История & Поиск)
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                AsymmetricShapeCard(
                    title = "История",
                    subtitle = "Недавно",
                    iconRes = R.drawable.ic_timer,
                    shape = RoundedCornerShape(22.dp),
                    cardHeight = 74.dp,
                    onClick = onHistoryClick,
                    modifier = Modifier.weight(1.1f)
                )

                AsymmetricShapeCard(
                    title = "Поиск",
                    subtitle = "Каталог",
                    iconRes = R.drawable.ic_search,
                    shape = RoundedCornerShape(22.dp),
                    cardHeight = 74.dp,
                    onClick = onSearchClick,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }

    @Composable
    fun AsymmetricShapeCard(
        title: String,
        subtitle: String,
        iconRes: Int,
        shape: RoundedCornerShape,
        cardHeight: androidx.compose.ui.unit.Dp,
        onClick: () -> Unit,
        modifier: Modifier = Modifier
    ) {
        Surface(
            onClick = onClick,
            modifier = modifier.height(cardHeight),
            shape = shape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 2.dp
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            painter = painterResource(id = iconRes),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(10.dp))

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
        tracks: List<Track>,
        onTrackClick: (Track) -> Unit
    ) {
        Column(modifier = Modifier.padding(top = 12.dp)) {
            Text(
                text = "Избранные треки в фокусе",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
            )

            Column(
                modifier = Modifier.padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                tracks.forEach { track ->
                    Surface(
                        onClick = { onTrackClick(track) },
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
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
                                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
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

    // 🔥 СТИЛЬНЫЕ СТЕКЛЯННЫЕ КАРТОЧКИ АЛЬБОМОВ С КНОПКОЙ PLAY
    @Composable
    fun SectionWithCarousel(title: String, items: List<Album>, onItemClick: (Album) -> Unit) {
        Column(modifier = Modifier.padding(top = 20.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
            )
            LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(items, key = { it.id }) { album ->
                    Card(
                        modifier = Modifier
                            .width(176.dp)
                            .height(230.dp)
                            .clickable { onItemClick(album) },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                        shape = RoundedCornerShape(26.dp)
                    ) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            val coverUrl = if (album.cover?.startsWith("/") == true) "http://185.196.41.31${album.cover}" else album.cover
                            AsyncImage(
                                model = coverUrl,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )

                            // Градиентное затемнение
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.verticalGradient(
                                            0.3f to Color.Transparent,
                                            0.7f to Color.Black.copy(alpha = 0.6f),
                                            1.0f to Color.Black.copy(alpha = 0.92f)
                                        )
                                    )
                            )

                            // Плавающий текстовый плашка с заголовком
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

    // 🔥 СТИЛЬНЫЕ АВАТАРЫ АРТИСТОВ С СВЕТЯЩИМСЯ КОЛЬЦОМ
    @Composable
    fun SectionWithCarouselArtist(title: String, items: List<Artist>, onItemClick: (Artist) -> Unit) {
        Column(modifier = Modifier.padding(top = 20.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
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
                        Surface(
                            shape = CircleShape,
                            border = BorderStroke(3.dp, MaterialTheme.colorScheme.primaryContainer),
                            tonalElevation = 4.dp,
                            modifier = Modifier.size(106.dp)
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
        NetworkClient.getApi(requireContext()).getFavorites(0, 500)
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
        val historyList = org.akanework.gramophone.logic.HistoryManager.getHistory(requireContext())
        val albumsOnly = historyList.filterIsInstance<Album>()
        historyItemsState.value = albumsOnly
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

        NetworkClient.getApi(requireContext()).getFavorites(0, 500)
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