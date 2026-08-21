package org.akanework.gramophone.ui.fragments.library.tabs

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
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
import org.akanework.gramophone.logic.LibraryCacheManager
import org.akanework.gramophone.logic.api.Artist
import org.akanework.gramophone.logic.api.NetworkClient
import org.akanework.gramophone.ui.MainActivity
import org.akanework.gramophone.ui.components.library.ExpressiveScrollBar
import org.akanework.gramophone.ui.components.library.LibraryEmptyState
import org.akanework.gramophone.ui.components.library.LibraryTabType
import org.akanework.gramophone.ui.fragments.ArtistFragment

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryArtistsTab(
    searchQuery: String = "",
    bottomPadding: PaddingValues = PaddingValues(0.dp),
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = context as? MainActivity
    val coroutineScope = rememberCoroutineScope()

    val artists = remember { mutableStateListOf<Artist>() }
    var isLoading by remember { mutableStateOf(false) }
    var isRefreshing by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()

    fun loadArtists(refresh: Boolean = false) {
        if (isLoading) return
        isLoading = true
        if (refresh) isRefreshing = true

        coroutineScope.launch(Dispatchers.IO) {
            val cached = LibraryCacheManager.loadCachedArtists(context)
            if (cached.isNotEmpty() && artists.isEmpty()) {
                withContext(Dispatchers.Main) {
                    artists.clear()
                    artists.addAll(cached)
                }
            }

            try {
                val response = NetworkClient.getApi(context).getFavoriteArtists().execute()
                if (response.isSuccessful) {
                    val freshArtists = response.body() ?: emptyList()
                    LibraryCacheManager.saveCachedArtists(context, freshArtists)
                    withContext(Dispatchers.Main) {
                        artists.clear()
                        artists.addAll(freshArtists)
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
        loadArtists()
    }

    val filteredArtists = remember(artists, searchQuery) {
        if (searchQuery.isBlank()) artists
        else artists.filter { it.name.contains(searchQuery, ignoreCase = true) }
    }

    val pullToRefreshState = rememberPullToRefreshState()

    Box(modifier = modifier.fillMaxSize()) {
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = { loadArtists(refresh = true) },
            state = pullToRefreshState,
            modifier = Modifier.fillMaxSize()
        ) {
            if (filteredArtists.isEmpty() && !isLoading) {
                LibraryEmptyState(
                    type = LibraryTabType.ARTISTS,
                    onActionClick = { loadArtists(refresh = true) }
                )
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
                        item(key = "artists_header") {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.55f)
                                ) {
                                    val count = filteredArtists.size
                                    val label = when {
                                        count % 10 == 1 && count % 100 != 11 -> "исполнитель"
                                        count % 10 in 2..4 && (count % 100 < 10 || count % 100 >= 20) -> "исполнителя"
                                        else -> "исполнителей"
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
                            }
                        }

                        itemsIndexed(
                            items = filteredArtists,
                            key = { index, artist -> "artist_${artist.id}_$index" },
                            contentType = { _, _ -> "artist_item" }
                        ) { index, artist ->
                            ArtistListItem(
                                artist = artist,
                                onClick = {
                                    activity?.startFragment(ArtistFragment.newInstance(artist.id))
                                }
                            )
                        }
                    }

                    ExpressiveScrollBar(
                        listState = listState,
                        itemCount = filteredArtists.size,
                        bottomPadding = bottomPadding.calculateBottomPadding() + 200.dp,
                        labelProvider = { index ->
                            filteredArtists.getOrNull(index)?.name?.firstOrNull()?.uppercase() ?: "#"
                        },
                        modifier = Modifier.align(Alignment.CenterEnd)
                    )
                }
            }
        }
    }
}

@Composable
fun ArtistListItem(
    artist: Artist,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable { onClick() },
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                val photoUrl = (artist.cover ?: artist.picture)?.let {
                    if (it.startsWith("/")) "http://185.196.41.31$it" else it
                }

                if (!photoUrl.isNullOrEmpty()) {
                    AsyncImage(
                        model = photoUrl,
                        contentDescription = artist.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        imageVector = Icons.Rounded.Person,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = artist.name,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = "Исполнитель",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
