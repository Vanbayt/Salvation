package org.akanework.gramophone.ui.fragments.library.tabs

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ViewList
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import org.akanework.gramophone.R
import org.akanework.gramophone.logic.LibraryCacheManager
import org.akanework.gramophone.logic.api.Album
import org.akanework.gramophone.logic.api.NetworkClient
import org.akanework.gramophone.ui.MainActivity
import org.akanework.gramophone.ui.components.library.ExpressiveScrollBar
import org.akanework.gramophone.ui.components.library.LibraryEmptyState
import org.akanework.gramophone.ui.components.library.LibraryTabType
import org.akanework.gramophone.ui.fragments.AlbumFragment

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryAlbumsTab(
    searchQuery: String = "",
    bottomPadding: PaddingValues = PaddingValues(0.dp),
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = context as? MainActivity
    val coroutineScope = rememberCoroutineScope()

    val albums = remember { mutableStateListOf<Album>() }
    var isLoading by remember { mutableStateOf(false) }
    var isRefreshing by remember { mutableStateOf(false) }
    var isGridMode by remember { mutableStateOf(true) }

    val listState = rememberLazyListState()
    val gridState = rememberLazyGridState()

    fun loadAlbums(refresh: Boolean = false) {
        if (isLoading) return
        isLoading = true
        if (refresh) isRefreshing = true

        coroutineScope.launch(Dispatchers.IO) {
            val cached = LibraryCacheManager.loadCachedAlbums(context)
            if (cached.isNotEmpty() && albums.isEmpty()) {
                withContext(Dispatchers.Main) {
                    albums.clear()
                    albums.addAll(cached)
                }
            }

            try {
                val response = NetworkClient.getApi(context).getFavoriteAlbums().execute()
                if (response.isSuccessful) {
                    val freshAlbums = response.body() ?: emptyList()
                    LibraryCacheManager.saveCachedAlbums(context, freshAlbums)
                    withContext(Dispatchers.Main) {
                        albums.clear()
                        albums.addAll(freshAlbums)
                    }
                }
            } catch (e: Exception) {
                // Ignore network error gracefully
            } finally {
                withContext(Dispatchers.Main) {
                    isLoading = false
                    isRefreshing = false
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        loadAlbums()
    }

    val filteredAlbums = remember(albums.toList(), searchQuery) {
        if (searchQuery.isBlank()) albums.toList()
        else albums.filter {
            it.title.contains(searchQuery, ignoreCase = true) ||
            it.artistName.contains(searchQuery, ignoreCase = true)
        }
    }

    val pullToRefreshState = rememberPullToRefreshState()

    Box(modifier = modifier.fillMaxSize()) {
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = { loadAlbums(refresh = true) },
            state = pullToRefreshState,
            modifier = Modifier.fillMaxSize()
        ) {
            if (filteredAlbums.isEmpty() && !isLoading) {
                LibraryEmptyState(
                    type = LibraryTabType.ALBUMS,
                    onActionClick = { loadAlbums(refresh = true) }
                )
            } else if (isGridMode) {
                LazyVerticalGrid(
                    state = gridState,
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(
                        start = 14.dp,
                        end = 14.dp,
                        top = 14.dp,
                        bottom = bottomPadding.calculateBottomPadding() + 220.dp
                    ),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    // Скроллируемая строка заголовка с количеством и переключателем Grid / List
                    item(span = { GridItemSpan(maxLineSpan) }, key = "albums_header") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.55f)
                            ) {
                                val count = filteredAlbums.size
                                val label = when {
                                    count % 10 == 1 && count % 100 != 11 -> "альбом"
                                    count % 10 in 2..4 && (count % 100 < 10 || count % 100 >= 20) -> "альбома"
                                    else -> "альбомов"
                                }
                                Text(
                                    text = "$count $label",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 12.5.sp
                                    ),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                )
                            }

                            FilledTonalIconButton(
                                onClick = { isGridMode = !isGridMode },
                                shape = CircleShape,
                                colors = IconButtonDefaults.filledTonalIconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(
                                    imageVector = if (isGridMode) Icons.AutoMirrored.Rounded.ViewList else Icons.Rounded.GridView,
                                    contentDescription = "Toggle Grid/List View",
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }

                    itemsIndexed(
                        items = filteredAlbums,
                        key = { index, album -> "album_grid_${album.id}_$index" },
                        contentType = { _, _ -> "album_grid_item" }
                    ) { index, album ->
                        AlbumGridCard(
                            album = album,
                            onClick = {
                                activity?.startFragment(AlbumFragment.newInstance(album.id))
                            }
                        )
                    }
                }
            } else {
                Box(modifier = Modifier.fillMaxSize()) {
                    LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(
                            start = 14.dp,
                            end = 14.dp,
                            top = 14.dp,
                            bottom = bottomPadding.calculateBottomPadding() + 220.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        item(key = "albums_header_list") {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.55f)
                                ) {
                                    val count = filteredAlbums.size
                                    val label = when {
                                        count % 10 == 1 && count % 100 != 11 -> "альбом"
                                        count % 10 in 2..4 && (count % 100 < 10 || count % 100 >= 20) -> "альбома"
                                        else -> "альбомов"
                                    }
                                    Text(
                                        text = "$count $label",
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 12.5.sp
                                        ),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                    )
                                }

                                FilledTonalIconButton(
                                    onClick = { isGridMode = !isGridMode },
                                    shape = CircleShape,
                                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                    ),
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isGridMode) Icons.AutoMirrored.Rounded.ViewList else Icons.Rounded.GridView,
                                        contentDescription = "Toggle Grid/List View",
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }

                        itemsIndexed(
                            items = filteredAlbums,
                            key = { index, album -> "album_list_${album.id}_$index" },
                            contentType = { _, _ -> "album_list_item" }
                        ) { index, album ->
                            AlbumListCard(
                                album = album,
                                onClick = {
                                    activity?.startFragment(AlbumFragment.newInstance(album.id))
                                }
                            )
                        }
                    }

                    ExpressiveScrollBar(
                        listState = listState,
                        itemCount = filteredAlbums.size,
                        bottomPadding = bottomPadding.calculateBottomPadding() + 200.dp,
                        labelProvider = { index ->
                            filteredAlbums.getOrNull(index)?.title?.firstOrNull()?.uppercase() ?: "#"
                        },
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .padding(end = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun AlbumGridCard(
    album: Album,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                val coverUrl = album.cover?.let {
                    if (it.startsWith("/")) "http://185.196.41.31$it" else it
                }

                if (!coverUrl.isNullOrEmpty()) {
                    AsyncImage(
                        model = coverUrl,
                        contentDescription = album.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        imageVector = Icons.Rounded.Album,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(48.dp)
                    )
                }

                // Золотистый угловой бейдж FLAC
                val isFlac = album.hasFlac || org.akanework.gramophone.logic.lossless.LosslessStateManager.isAlbumLossless(album.id)
                if (isFlac) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp),
                        shape = RoundedCornerShape(6.dp),
                        color = Color(0xFF1E1B18).copy(alpha = 0.88f),
                        border = BorderStroke(0.5.dp, Color(0xFFD4AF37).copy(alpha = 0.6f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_check_circle),
                                contentDescription = null,
                                tint = Color(0xFFD4AF37),
                                modifier = Modifier.size(9.dp)
                            )
                            Text(
                                text = "FLAC",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 8.5.sp,
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 0.5.sp
                                ),
                                color = Color(0xFFD4AF37)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = album.title,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = album.artistName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            if (album.releaseYear != null && album.releaseYear > 0) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${album.releaseYear}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
fun AlbumListCard(
    album: Album,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                val coverUrl = album.cover?.let {
                    if (it.startsWith("/")) "http://185.196.41.31$it" else it
                }

                if (!coverUrl.isNullOrEmpty()) {
                    AsyncImage(
                        model = coverUrl,
                        contentDescription = album.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        imageVector = Icons.Rounded.Album,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(28.dp)
                    )
                }

                // Микро-бейдж FLAC на обложке
                val isFlac = album.hasFlac || org.akanework.gramophone.logic.lossless.LosslessStateManager.isAlbumLossless(album.id)
                if (isFlac) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(3.dp),
                        shape = RoundedCornerShape(4.dp),
                        color = Color(0xFF1E1B18).copy(alpha = 0.88f),
                        border = BorderStroke(0.5.dp, Color(0xFFD4AF37).copy(alpha = 0.6f))
                    ) {
                        Text(
                            text = "FLAC",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 7.5.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 0.4.sp
                            ),
                            color = Color(0xFFD4AF37),
                            modifier = Modifier.padding(horizontal = 3.dp, vertical = 1.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = album.title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(2.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = album.artistName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    if (album.releaseYear != null && album.releaseYear > 0) {
                        Text(
                            text = "•",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                        Text(
                            text = "${album.releaseYear}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}
