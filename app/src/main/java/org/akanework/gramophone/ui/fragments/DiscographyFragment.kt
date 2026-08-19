package org.akanework.gramophone.ui.fragments

import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.Fragment
import coil3.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.akanework.gramophone.R
import org.akanework.gramophone.logic.api.Album
import org.akanework.gramophone.logic.api.NetworkClient
import org.akanework.gramophone.ui.MainActivity
import org.akanework.gramophone.ui.components.library.ExpressiveScrollBar
import org.akanework.gramophone.ui.components.library.LibrarySortBottomSheet
import org.akanework.gramophone.ui.components.library.LibrarySortOption

class DiscographyFragment : Fragment() {

    private var artistId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        artistId = arguments?.getString("ARTIST_ID")
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val composeView = ComposeView(requireContext())
        composeView.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        composeView.setContent {
            val colorScheme = getThemeColorScheme()
            MaterialTheme(colorScheme = colorScheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = colorScheme.surface
                ) {
                    DiscographyScreen(
                        artistId = artistId ?: "",
                        onBackClick = { requireActivity().onBackPressed() }
                    )
                }
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

    companion object {
        fun newInstance(artistId: String): DiscographyFragment {
            val fragment = DiscographyFragment()
            val args = Bundle()
            args.putString("ARTIST_ID", artistId)
            fragment.arguments = args
            return fragment
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscographyScreen(
    artistId: String,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? MainActivity
    val coroutineScope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current

    val albums = remember { mutableStateListOf<Album>() }
    var isLoading by remember { mutableStateOf(true) }
    var selectedFilterIndex by remember { mutableIntStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }
    var isSearchExpanded by remember { mutableStateOf(false) }

    var currentSortOption by remember { mutableStateOf(LibrarySortOption.NEWEST) }
    var showSortSheet by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()

    fun loadDiscography() {
        if (artistId.isBlank()) return
        coroutineScope.launch(Dispatchers.IO) {
            try {
                val response = NetworkClient.getApi(context).getArtistDiscography(artistId).execute()
                if (response.isSuccessful && response.body() != null) {
                    withContext(Dispatchers.Main) {
                        albums.clear()
                        albums.addAll(response.body()!!)
                    }
                } else {
                    android.util.Log.e("GramoDebug", "Discography error: ${response.code()} ${response.message()}")
                }
            } catch (e: Exception) {
                android.util.Log.e("GramoDebug", "Discography fetch exception", e)
            }
            withContext(Dispatchers.Main) { isLoading = false }
        }
    }

    LaunchedEffect(artistId) { loadDiscography() }

    fun applySort(option: LibrarySortOption) {
        currentSortOption = option
        when (option) {
            LibrarySortOption.TITLE_AZ -> albums.sortBy { it.title.lowercase() }
            LibrarySortOption.ARTIST_AZ -> albums.sortBy { it.artistName.lowercase() }
            LibrarySortOption.NEWEST -> albums.sortByDescending { it.releaseYear ?: 0 }
            LibrarySortOption.OLDEST -> albums.sortBy { it.releaseYear ?: 0 }
        }
    }

    val filterOptions = listOf("Все", "Альбомы", "Синглы и EP", "Концертные", "Сборники")

    val searchedAlbums = if (searchQuery.isBlank()) {
        albums
    } else {
        albums.filter { it.title.contains(searchQuery, ignoreCase = true) }
    }

    // Категоризация релизов
    val studioAlbums = searchedAlbums.filter {
        (it.recordType?.equals("album", ignoreCase = true) == true || it.recordType == null || it.recordType.isBlank()) &&
                !it.title.contains("live", ignoreCase = true) &&
                !it.title.contains("концерт", ignoreCase = true)
    }
    val singlesAndEps = searchedAlbums.filter {
        it.recordType?.equals("single", ignoreCase = true) == true ||
                it.recordType?.equals("ep", ignoreCase = true) == true ||
                it.title.contains(" - Single", ignoreCase = true) ||
                it.title.contains(" - EP", ignoreCase = true)
    }
    val liveAlbums = searchedAlbums.filter {
        it.recordType?.equals("live", ignoreCase = true) == true ||
                it.title.contains("live", ignoreCase = true) ||
                it.title.contains("концерт", ignoreCase = true)
    }
    val compilationAlbums = searchedAlbums.filter {
        it.recordType?.equals("compilation", ignoreCase = true) == true ||
                it.title.contains("best", ignoreCase = true) ||
                it.title.contains("greatest", ignoreCase = true)
    }

    data class ReleaseCategory(val name: String, val list: List<Album>, val icon: androidx.compose.ui.graphics.vector.ImageVector)

    val activeCategories = when (selectedFilterIndex) {
        1 -> listOf(ReleaseCategory("Студийные альбомы", studioAlbums, Icons.Rounded.Album))
        2 -> listOf(ReleaseCategory("Синглы и EP", singlesAndEps, Icons.Rounded.MusicNote))
        3 -> listOf(ReleaseCategory("Концертные записи (Live)", liveAlbums, Icons.Rounded.Mic))
        4 -> listOf(ReleaseCategory("Сборники и компиляции", compilationAlbums, Icons.Rounded.LibraryMusic))
        else -> {
            val categorized = studioAlbums.map { it.id }.toSet() +
                    singlesAndEps.map { it.id }.toSet() +
                    liveAlbums.map { it.id }.toSet() +
                    compilationAlbums.map { it.id }.toSet()
            val other = searchedAlbums.filter { it.id !in categorized }

            listOf(
                ReleaseCategory("Студийные альбомы", studioAlbums, Icons.Rounded.Album),
                ReleaseCategory("Синглы и EP", singlesAndEps, Icons.Rounded.MusicNote),
                ReleaseCategory("Концертные записи", liveAlbums, Icons.Rounded.Mic),
                ReleaseCategory("Сборники и компиляции", compilationAlbums, Icons.Rounded.LibraryMusic),
                ReleaseCategory("Другие релизы", other, Icons.Rounded.Album)
            ).filter { it.list.isNotEmpty() }
        }
    }.let { list ->
        if (list.isEmpty() && searchedAlbums.isNotEmpty()) {
            listOf(ReleaseCategory("Все релизы", searchedAlbums, Icons.Rounded.Album))
        } else {
            list
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            // ВЕРХНИЙ ТУЛБАР
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilledTonalIconButton(
                    onClick = onBackClick,
                    modifier = Modifier.size(46.dp),
                    shape = CircleShape
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Назад",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Дискография",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${albums.size} релизов",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilledTonalIconButton(
                        onClick = { isSearchExpanded = !isSearchExpanded },
                        modifier = Modifier.size(42.dp),
                        shape = CircleShape,
                        colors = if (isSearchExpanded || searchQuery.isNotEmpty()) IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ) else IconButtonDefaults.filledTonalIconButtonColors()
                    ) {
                        Icon(painterResource(R.drawable.ic_search), contentDescription = "Поиск", modifier = Modifier.size(18.dp))
                    }

                    FilledTonalIconButton(
                        onClick = { showSortSheet = true },
                        modifier = Modifier.size(42.dp),
                        shape = CircleShape
                    ) {
                        Icon(imageVector = Icons.AutoMirrored.Rounded.Sort, contentDescription = "Сортировка", modifier = Modifier.size(18.dp))
                    }
                }
            }

            // РАСКРЫВАЮЩИЙСЯ ПОИСК
            AnimatedVisibility(visible = isSearchExpanded) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
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
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Поиск релиза...", style = MaterialTheme.typography.bodyMedium) },
                            singleLine = true,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent
                            ),
                            modifier = Modifier.weight(1f)
                        )
                        if (searchQuery.isNotEmpty()) {
                            IconButton(
                                onClick = { searchQuery = "" },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(imageVector = Icons.Rounded.Close, contentDescription = "Очистить", modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }

            // ФИЛЬТР-ЧИПЫ В СТИЛЕ МЕДИАТЕКИ (PILL TABS)
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 16.dp)
            ) {
                itemsIndexed(filterOptions) { index, title ->
                    val isSelected = selectedFilterIndex == index
                    val tabContainerColor by animateColorAsState(
                        targetValue = if (isSelected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.6f),
                        animationSpec = tween(durationMillis = 200),
                        label = "discoPillTabColor"
                    )
                    val tabContentColor by animateColorAsState(
                        targetValue = if (isSelected) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        animationSpec = tween(durationMillis = 200),
                        label = "discoPillTabTextColor"
                    )

                    Surface(
                        shape = CircleShape,
                        color = tabContainerColor,
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                selectedFilterIndex = index
                            }
                    ) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 13.sp
                            ),
                            color = tabContentColor,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 7.dp)
                        )
                    }
                }
            }

            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (activeCategories.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Релизы не найдены", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                // КАТЕГОРИЗИРОВАННЫЙ СПИСОК С РАЗДЕЛАМИ
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = 8.dp, bottom = 260.dp)
                ) {
                    activeCategories.forEach { category ->
                        // Заголовок категории
                        item(key = "header_${category.name}") {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = category.icon,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "${category.name} (${category.list.size})",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                HorizontalDivider(
                                    modifier = Modifier.weight(1f),
                                    color = MaterialTheme.colorScheme.surfaceContainerHighest
                                )
                            }
                        }

                        // Сетка карточек по 2 в ряду
                        val chunkedRows = category.list.chunked(2)
                        itemsIndexed(chunkedRows, key = { rowIdx, rowItems -> "${category.name}_row_${rowIdx}_${rowItems.first().id}" }) { _, pair ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                pair.forEach { albumItem ->
                                    Box(modifier = Modifier.weight(1f)) {
                                        DiscographyAlbumCard(
                                            album = albumItem,
                                            onClick = {
                                                activity?.startFragment(AlbumFragment.newInstance(albumItem.id))
                                            }
                                        )
                                    }
                                }
                                if (pair.size == 1) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            }
        }

        // Быстрый скроллбар
        ExpressiveScrollBar(
            listState = listState,
            itemCount = searchedAlbums.size,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(top = 100.dp, bottom = 120.dp, end = 2.dp)
        )
    }
}

@Composable
fun DiscographyAlbumCard(
    album: Album,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 2.dp,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp)
        ) {
            val coverUrl = album.cover?.let {
                if (it.startsWith("/")) "http://185.196.41.31$it" else it
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                contentAlignment = Alignment.Center
            ) {
                if (!coverUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = coverUrl,
                        contentDescription = album.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_library),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(48.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = album.title,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(2.dp))

            val type = when (album.recordType?.lowercase()) {
                "ep" -> "EP"
                "single" -> "Сингл"
                "live" -> "Live"
                "compilation" -> "Сборник"
                else -> "Альбом"
            }
            val year = album.releaseYear?.toString() ?: ""
            val subtitle = listOf(type, year).filter { it.isNotBlank() }.joinToString(" • ")

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