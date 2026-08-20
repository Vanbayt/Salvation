package org.akanework.gramophone.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForwardIos
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import coil3.compose.AsyncImage
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.akanework.gramophone.R
import org.akanework.gramophone.ui.MainActivity
import org.akanework.gramophone.ui.components.library.AudioQualityBadge

class PlayerMenuBottomSheet : BottomSheetDialogFragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnLifecycleDestroyed(viewLifecycleOwner))
            setContent {
                val context = LocalContext.current
                val isDark = androidx.compose.foundation.isSystemInDarkTheme()
                val colorScheme = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                    if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
                } else {
                    if (isDark) darkColorScheme() else lightColorScheme()
                }

                MaterialTheme(colorScheme = colorScheme) {
                    val activity = requireActivity() as MainActivity
                    val player = activity.getPlayer()

                    val currentItem = player?.currentMediaItem
                    val metadata = currentItem?.mediaMetadata ?: player?.mediaMetadata

                    val trackTitle = metadata?.title?.toString() ?: "Неизвестный трек"
                    val trackArtist = metadata?.artist?.toString() ?: "Неизвестный артист"
                    val trackAlbum = metadata?.albumTitle?.toString() ?: ""

                    val originalUri = metadata?.artworkUri?.toString() ?: ""
                    val finalCoverUrl = if (originalUri.startsWith("/")) "http://185.196.41.31$originalUri" else originalUri
                    val isLossless = metadata?.extras?.getBoolean("IS_LOSSLESS", false) ?: false

                    val artistId = metadata?.extras?.getString("ARTIST_ID")
                    val albumId = metadata?.extras?.getString("ALBUM_ID")

                    // Next 3 items in queue
                    val miniQueue = remember(player?.currentMediaItemIndex, player?.mediaItemCount) {
                        val list = mutableListOf<MediaItem>()
                        if (player != null) {
                            val currentIndex = player.currentMediaItemIndex
                            for (i in (currentIndex + 1) until player.mediaItemCount) {
                                list.add(player.getMediaItemAt(i))
                                if (list.size >= 3) break
                            }
                        }
                        list
                    }

                    PlayerMenuSheetContent(
                        trackTitle = trackTitle,
                        trackArtist = trackArtist,
                        trackAlbum = trackAlbum,
                        coverUrl = finalCoverUrl,
                        isLossless = isLossless,
                        miniQueue = miniQueue,
                        onAddToPlaylist = {
                            val currentTrackId = currentItem?.mediaId?.toIntOrNull()
                            if (currentTrackId != null) {
                                dismiss()
                                AddToPlaylistBottomSheet.newInstance(currentTrackId).show(activity.supportFragmentManager, "ADD_TO_PLAYLIST_SHEET")
                            } else {
                                Toast.makeText(context, "Не удалось определить ID трека", Toast.LENGTH_SHORT).show()
                            }
                        },
                        onSleepTimer = {
                            dismiss()
                            SleepTimerBottomSheet.newInstance().show(activity.supportFragmentManager, "SLEEP_TIMER_SHEET")
                        },
                        onOpenQueue = {
                            dismiss()
                            QueueBottomSheetFragment().show(activity.supportFragmentManager, "QUEUE_SHEET")
                        },
                        onReport = {
                            Toast.makeText(context, "Жалоба отправлена", Toast.LENGTH_SHORT).show()
                        },
                        onGoToArtist = {
                            resolveAndNavigateToArtist(activity, trackArtist, artistId)
                        },
                        onGoToAlbum = {
                            resolveAndNavigateToAlbum(activity, trackArtist, trackTitle, trackAlbum, albumId, currentItem?.mediaId)
                        },
                        onPlayQueueItem = { queueItem ->
                            if (player != null) {
                                val index = (0 until player.mediaItemCount).firstOrNull {
                                    player.getMediaItemAt(it).mediaId == queueItem.mediaId
                                }
                                if (index != null) {
                                    player.seekToDefaultPosition(index)
                                    player.play()
                                }
                            }
                            dismiss()
                        }
                    )
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        val dialog = dialog as? com.google.android.material.bottomsheet.BottomSheetDialog
        val bottomSheet = dialog?.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
        bottomSheet?.setBackgroundResource(android.R.color.transparent)
    }

    private fun resolveAndNavigateToArtist(activity: MainActivity, artistName: String, directArtistId: String?) {
        val primaryArtist = if (artistName.isNotEmpty()) {
            artistName.split(",", ";", " feat. ", " ft. ", " Feat. ", " Ft. ", " & ")[0].trim()
        } else ""

        val cachedId = artistIdCache[primaryArtist.lowercase()]
        if (!cachedId.isNullOrEmpty()) {
            dismiss()
            activity.collapsePlayer()
            activity.startFragment(ArtistFragment.newInstance(cachedId))
            return
        }

        if (!directArtistId.isNullOrEmpty() && directArtistId.all { it.isDigit() }) {
            dismiss()
            activity.collapsePlayer()
            activity.startFragment(ArtistFragment.newInstance(directArtistId))
            return
        }

        if (primaryArtist.isNotEmpty() && primaryArtist != "Неизвестный артист" && primaryArtist != "Unknown") {
            kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
                try {
                    val api = org.akanework.gramophone.logic.api.NetworkClient.getApi(activity)
                    val resp = api.searchArtistFast(primaryArtist).execute()
                    val artistLookup = resp.body()

                    withContext(Dispatchers.Main) {
                        if (artistLookup != null && artistLookup.id.isNotEmpty()) {
                            artistIdCache[primaryArtist.lowercase()] = artistLookup.id
                            dismiss()
                            activity.collapsePlayer()
                            activity.startFragment(ArtistFragment.newInstance(artistLookup.id))
                        } else {
                            Toast.makeText(activity, "Артист '$primaryArtist' не найден", Toast.LENGTH_SHORT).show()
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(activity, "Ошибка поиска артиста", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        } else {
            Toast.makeText(activity, "Исполнитель неизвестен", Toast.LENGTH_SHORT).show()
        }
    }

    private fun resolveAndNavigateToAlbum(
        activity: MainActivity,
        artistName: String,
        trackTitle: String,
        albumTitle: String,
        directAlbumId: String?,
        trackId: String?
    ) {
        if (!directAlbumId.isNullOrEmpty() && directAlbumId.all { it.isDigit() }) {
            dismiss()
            activity.collapsePlayer()
            activity.startFragment(AlbumFragment.newInstance(directAlbumId))
            return
        }

        val query = if (albumTitle.isNotEmpty() && albumTitle != "Single" && albumTitle != "Unknown") {
            "$artistName $albumTitle"
        } else {
            "$artistName $trackTitle"
        }

        val cacheKey = trackId ?: query.lowercase()
        val cachedAlbumId = albumIdCache[cacheKey]
        if (!cachedAlbumId.isNullOrEmpty()) {
            dismiss()
            activity.collapsePlayer()
            activity.startFragment(AlbumFragment.newInstance(cachedAlbumId))
            return
        }

        if (artistName.isNotEmpty() || !trackId.isNullOrEmpty()) {
            kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
                try {
                    val api = org.akanework.gramophone.logic.api.NetworkClient.getApi(activity)
                    val resp = api.searchAlbumFast(query = query.ifEmpty { null }, trackId = trackId).execute()
                    val albumLookup = resp.body()

                    withContext(Dispatchers.Main) {
                        if (albumLookup != null && albumLookup.id.isNotEmpty()) {
                            albumIdCache[cacheKey] = albumLookup.id
                            dismiss()
                            activity.collapsePlayer()
                            activity.startFragment(AlbumFragment.newInstance(albumLookup.id))
                        } else {
                            Toast.makeText(activity, "Альбом не найден", Toast.LENGTH_SHORT).show()
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(activity, "Ошибка поиска альбома", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        } else {
            Toast.makeText(activity, "Альбом неизвестен", Toast.LENGTH_SHORT).show()
        }
    }

    companion object {
        private val artistIdCache = java.util.concurrent.ConcurrentHashMap<String, String>()
        private val albumIdCache = java.util.concurrent.ConcurrentHashMap<String, String>()
    }
}

@Composable
private fun PlayerMenuSheetContent(
    trackTitle: String,
    trackArtist: String,
    trackAlbum: String,
    coverUrl: String,
    isLossless: Boolean,
    miniQueue: List<MediaItem>,
    onAddToPlaylist: () -> Unit,
    onSleepTimer: () -> Unit,
    onOpenQueue: () -> Unit,
    onReport: () -> Unit,
    onGoToArtist: () -> Unit,
    onGoToAlbum: () -> Unit,
    onPlayQueueItem: (MediaItem) -> Unit
) {
    val colors = MaterialTheme.colorScheme

    Surface(
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        color = colors.surfaceContainerLow,
        tonalElevation = 6.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            // Drag handle
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .size(width = 36.dp, height = 4.dp)
                    .clip(CircleShape)
                    .background(colors.onSurfaceVariant.copy(alpha = 0.35f))
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 1. Шапка трека (Карточка с обложкой и метаданными)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(colors.primaryContainer.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center
                ) {
                    if (coverUrl.isNotEmpty()) {
                        AsyncImage(
                            model = coverUrl,
                            contentDescription = trackTitle,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.MusicNote,
                            contentDescription = null,
                            tint = colors.primary,
                            modifier = Modifier.size(30.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = trackTitle,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 17.sp
                            ),
                            color = colors.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (isLossless) {
                            AudioQualityBadge(text = "FLAC")
                        }
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = trackArtist + (if (trackAlbum.isNotBlank()) " • $trackAlbum" else ""),
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.5.sp
                        ),
                        color = colors.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // 2. Быстрые действия (4 выразительные пилл-кнопки со своими акцентными цветами)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                QuickActionPill(
                    icon = Icons.AutoMirrored.Rounded.PlaylistAdd,
                    label = "В плейлист",
                    containerColor = colors.primaryContainer,
                    contentColor = colors.onPrimaryContainer,
                    iconColor = colors.primary,
                    onClick = onAddToPlaylist,
                    modifier = Modifier.weight(1f)
                )
                QuickActionPill(
                    icon = Icons.Rounded.Bedtime,
                    label = "Таймер",
                    containerColor = colors.secondaryContainer,
                    contentColor = colors.onSecondaryContainer,
                    iconColor = colors.secondary,
                    onClick = onSleepTimer,
                    modifier = Modifier.weight(1f)
                )
                QuickActionPill(
                    icon = Icons.AutoMirrored.Rounded.QueueMusic,
                    label = "Очередь",
                    containerColor = colors.tertiaryContainer,
                    contentColor = colors.onTertiaryContainer,
                    iconColor = colors.tertiary,
                    onClick = onOpenQueue,
                    modifier = Modifier.weight(1f)
                )
                QuickActionPill(
                    icon = Icons.Rounded.ReportProblem,
                    label = "Жалоба",
                    containerColor = colors.errorContainer.copy(alpha = 0.65f),
                    contentColor = colors.onErrorContainer,
                    iconColor = colors.error,
                    onClick = onReport,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            HorizontalDivider(color = colors.outlineVariant.copy(alpha = 0.35f))

            Spacer(modifier = Modifier.height(14.dp))

            // 3. Навигация к артисту и альбому с выразительными акцентами
            MenuNavigationItem(
                icon = Icons.Rounded.Person,
                title = "Исполнитель: $trackArtist",
                subtitle = "Перейти к странице артиста",
                iconContainerColor = colors.primaryContainer,
                iconColor = colors.onPrimaryContainer,
                onClick = onGoToArtist
            )

            if (trackAlbum.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                MenuNavigationItem(
                    icon = Icons.Rounded.Album,
                    title = "Альбом: $trackAlbum",
                    subtitle = "Открыть весь альбом",
                    iconContainerColor = colors.secondaryContainer,
                    iconColor = colors.onSecondaryContainer,
                    onClick = onGoToAlbum
                )
            }

            // 4. Мини-очередь (если есть следующие треки)
            if (miniQueue.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "ДАЛЕЕ В ОЧЕРЕДИ",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.2.sp,
                            fontSize = 11.sp
                        ),
                        color = colors.primary
                    )
                    Text(
                        text = "Смотреть все",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.5.sp
                        ),
                        color = colors.primary,
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable(onClick = onOpenQueue)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = colors.surfaceContainerHighest.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                        miniQueue.forEachIndexed { idx, item ->
                            val meta = item.mediaMetadata
                            val itemTitle = meta.title?.toString() ?: "Неизвестный трек"
                            val itemArtist = meta.artist?.toString() ?: "Неизвестный артист"
                            val itemCoverUri = meta.artworkUri?.toString() ?: ""
                            val itemCover = if (itemCoverUri.startsWith("/")) "http://185.196.41.31$itemCoverUri" else itemCoverUri

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { onPlayQueueItem(item) }
                                    .padding(vertical = 6.dp, horizontal = 4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(colors.surfaceVariant),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (itemCover.isNotEmpty()) {
                                        AsyncImage(
                                            model = itemCover,
                                            contentDescription = itemTitle,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    } else {
                                        Icon(
                                            Icons.Rounded.MusicNote,
                                            contentDescription = null,
                                            tint = colors.onSurfaceVariant,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(10.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = itemTitle,
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 13.5.sp
                                        ),
                                        color = colors.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = itemArtist,
                                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                                        color = colors.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            if (idx < miniQueue.lastIndex) {
                                HorizontalDivider(
                                    color = colors.outlineVariant.copy(alpha = 0.2f),
                                    modifier = Modifier.padding(start = 48.dp)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
        }
    }
}

@Composable
private fun QuickActionPill(
    icon: ImageVector,
    label: String,
    containerColor: Color,
    contentColor: Color,
    iconColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = containerColor,
        modifier = modifier
            .height(68.dp)
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = iconColor,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp
                ),
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun MenuNavigationItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    iconContainerColor: Color,
    iconColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f),
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(iconContainerColor),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.5.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontWeight = FontWeight.Medium,
                        fontSize = 12.sp
                    ),
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowForwardIos,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(14.dp)
            )
        }
    }
}
