package org.akanework.gramophone.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
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
import org.akanework.gramophone.logic.api.NetworkClient
import org.akanework.gramophone.logic.api.RadioResponse
import org.akanework.gramophone.ui.MainActivity
import org.akanework.gramophone.ui.components.PillPosition
import org.akanework.gramophone.ui.components.PillSegmentButton
import org.akanework.gramophone.ui.components.library.AudioQualityBadge

class PlayerMenuBottomSheet : BottomSheetDialogFragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnLifecycleDestroyed(viewLifecycleOwner))
            setContent {
                org.akanework.gramophone.ui.theme.AppTheme {
                    val activity = requireActivity() as MainActivity
                    val player = activity.getPlayer()

                    val currentItem = player?.currentMediaItem
                    val metadata = currentItem?.mediaMetadata ?: player?.mediaMetadata

                    val trackTitle = metadata?.title?.toString() ?: "Неизвестный трек"
                    val trackArtist = metadata?.artist?.toString() ?: "Неизвестный артист"
                    val trackAlbum = metadata?.albumTitle?.toString() ?: ""

                    val originalUri = metadata?.artworkUri?.toString() ?: ""
                    val currentTrackId = currentItem?.mediaId
                    val finalCoverUrl = org.akanework.gramophone.logic.utils.CoverUtils.upgradeCoverQuality(
                        org.akanework.gramophone.logic.utils.CoverUtils.getBestCover(
                            originalUri,
                            org.akanework.gramophone.logic.utils.exoplayer.ClientTrackResolver.getTrackCover(currentTrackId)
                        )
                    ) ?: ""
                    val isLosslessExtra = metadata?.extras?.getBoolean("IS_LOSSLESS", false) ?: false
                    val isLossless = org.akanework.gramophone.logic.lossless.LosslessStateManager.rememberIsTrackLossless(currentTrackId, isLosslessExtra)

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

                    val context = LocalContext.current
                    var isLiked by remember(currentTrackId) {
                        mutableStateOf(
                            if (currentTrackId != null) {
                                org.akanework.gramophone.logic.LikeCache.isLiked(currentTrackId, title = trackTitle, artist = trackArtist)
                            } else false
                        )
                    }
                    var showAudioFidelitySheet by remember { mutableStateOf(false) }

                    if (showAudioFidelitySheet) {
                        org.akanework.gramophone.ui.components.player.AudioFidelityBottomSheet(
                            trackTitle = trackTitle,
                            artistName = trackArtist,
                            isLossless = isLossless,
                            onDismiss = { showAudioFidelitySheet = false }
                        )
                    }

                    PlayerMenuSheetContent(
                        trackTitle = trackTitle,
                        trackArtist = trackArtist,
                        trackAlbum = trackAlbum,
                        coverUrl = finalCoverUrl,
                        isLossless = isLossless,
                        isLiked = isLiked,
                        miniQueue = miniQueue,
                        onToggleLike = {
                            val newLiked = !isLiked
                            isLiked = newLiked
                            if (currentTrackId != null) {
                                if (newLiked) {
                                    org.akanework.gramophone.logic.LikeCache.add(currentTrackId, title = trackTitle, artist = trackArtist)
                                    kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
                                        try {
                                            org.akanework.gramophone.logic.api.NetworkClient.getApi(activity).likeTrack(currentTrackId).execute()
                                        } catch (_: Exception) {}
                                    }
                                } else {
                                    org.akanework.gramophone.logic.LikeCache.remove(currentTrackId, title = trackTitle, artist = trackArtist)
                                    kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
                                        try {
                                            org.akanework.gramophone.logic.api.NetworkClient.getApi(activity).unlikeTrack(currentTrackId).execute()
                                        } catch (_: Exception) {}
                                    }
                                }
                            }
                        },
                        onAddToQueue = {
                            if (player != null && currentItem != null) {
                                player.addMediaItem(currentItem)
                                Toast.makeText(context, "Трек добавлен в конец очереди", Toast.LENGTH_SHORT).show()
                            }
                        },
                        onDownload = {
                            if (currentTrackId != null) {
                                org.akanework.gramophone.logic.lossless.FlacDownloadManager.downloadTrack(context, currentTrackId, trackTitle)
                                Toast.makeText(context, "Загрузка началась", Toast.LENGTH_SHORT).show()
                            }
                        },
                        onShare = {
                            val shareText = "Слушаю трек \"$trackTitle\" — $trackArtist в Salvation!\nhttp://185.196.41.31/track/$currentTrackId"
                            val sendIntent = android.content.Intent().apply {
                                action = android.content.Intent.ACTION_SEND
                                putExtra(android.content.Intent.EXTRA_TEXT, shareText)
                                type = "text/plain"
                            }
                            val shareIntent = android.content.Intent.createChooser(sendIntent, "Поделиться треком")
                            context.startActivity(shareIntent)
                        },
                        onGoToArtist = {
                            resolveAndNavigateToArtist(activity, trackArtist, artistId)
                        },
                        onGoToAlbum = {
                            resolveAndNavigateToAlbum(activity, trackArtist, trackTitle, trackAlbum, albumId, currentItem?.mediaId)
                        },
                        onStartRadio = {
                            val currentItemCopy = currentItem
                            val playerCopy = player
                            val contextCopy = context.applicationContext
                            dismiss()
                            if (currentItemCopy != null && playerCopy != null) {
                                val mediaId = currentItemCopy.mediaId
                                val currentTitle = currentItemCopy.mediaMetadata.title?.toString()
                                val currentArtist = currentItemCopy.mediaMetadata.artist?.toString()
                                val artworkUri = currentItemCopy.mediaMetadata.artworkUri?.toString()
                                org.akanework.gramophone.logic.utils.WaveManager.startTrackRadio(
                                    context = contextCopy,
                                    player = playerCopy,
                                    trackId = mediaId,
                                    artist = currentArtist,
                                    title = currentTitle,
                                    coverUrl = artworkUri
                                )
                            } else {
                                Toast.makeText(context, "Не удалось определить трек", Toast.LENGTH_SHORT).show()
                            }
                        },
                        onOpenLyrics = {
                            dismiss()
                            activity.openLyrics()
                        },
                        onOpenAudioFidelity = {
                            showAudioFidelitySheet = true
                        },
                        onAddToPlaylist = {
                            val cTrackId = currentItem?.mediaId?.toIntOrNull()
                            if (cTrackId != null) {
                                dismiss()
                                AddToPlaylistBottomSheet.newInstance(cTrackId).show(activity.supportFragmentManager, "ADD_TO_PLAYLIST_SHEET")
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
        bottomSheet?.let {
            it.setBackgroundResource(android.R.color.transparent)
            val displayMetrics = resources.displayMetrics
            val screenWidthDp = displayMetrics.widthPixels / displayMetrics.density
            if (screenWidthDp > 600) {
                it.layoutParams.width = (560 * displayMetrics.density).toInt()
                it.requestLayout()
            }
        }
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
    isLiked: Boolean,
    miniQueue: List<MediaItem>,
    onToggleLike: () -> Unit,
    onAddToQueue: () -> Unit,
    onDownload: () -> Unit,
    onShare: () -> Unit,
    onGoToArtist: () -> Unit,
    onGoToAlbum: () -> Unit,
    onStartRadio: () -> Unit,
    onOpenLyrics: () -> Unit,
    onOpenAudioFidelity: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onSleepTimer: () -> Unit,
    onOpenQueue: () -> Unit,
    onPlayQueueItem: (MediaItem) -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val haptic = LocalHapticFeedback.current

    Surface(
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        color = colors.surfaceContainerLow,
        tonalElevation = 6.dp,
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentWidth(Alignment.CenterHorizontally)
            .widthIn(max = 560.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
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

            Spacer(modifier = Modifier.height(16.dp))

            // 2. Сегментированный тулбар быстрых действий (4-кнопочный пилл [ Like | Add to Queue | Download | Share ])
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val likeBg by animateColorAsState(
                    if (isLiked) colors.primaryContainer.copy(alpha = 0.55f) else colors.surfaceContainerHigh,
                    tween(200), label = "pmLikeBg"
                )
                val likeFg by animateColorAsState(
                    if (isLiked) colors.primary else colors.onSurface,
                    tween(200), label = "pmLikeFg"
                )
                PillSegmentButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onToggleLike()
                    },
                    position = PillPosition.START,
                    containerColor = likeBg,
                    contentColor = likeFg,
                    modifier = Modifier.weight(1f),
                    width = null,
                    height = 50.dp,
                    cornerRadius = 24.dp,
                    innerRadius = 6.dp
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = if (isLiked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                            contentDescription = "Лайк",
                            tint = if (isLiked) Color(0xFFFF4081) else colors.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (isLiked) "Любимое" else "Нравится",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 10.5.sp),
                            color = if (isLiked) colors.primary else colors.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                PillSegmentButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onAddToQueue()
                    },
                    position = PillPosition.MIDDLE,
                    containerColor = colors.surfaceContainerHigh,
                    contentColor = colors.onSurface,
                    modifier = Modifier.weight(1f),
                    width = null,
                    height = 50.dp,
                    cornerRadius = 24.dp,
                    innerRadius = 6.dp
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.QueueMusic,
                            contentDescription = "В очередь",
                            tint = colors.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "В очередь",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 10.5.sp),
                            color = colors.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                val dlBg by animateColorAsState(
                    if (isLossless) colors.primaryContainer.copy(alpha = 0.35f) else colors.surfaceContainerHigh,
                    tween(200), label = "pmDlBg"
                )
                PillSegmentButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onDownload()
                    },
                    position = PillPosition.MIDDLE,
                    containerColor = dlBg,
                    contentColor = colors.onSurface,
                    modifier = Modifier.weight(1f),
                    width = null,
                    height = 50.dp,
                    cornerRadius = 24.dp,
                    innerRadius = 6.dp
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = if (isLossless) Icons.Rounded.CheckCircle else Icons.Rounded.Download,
                            contentDescription = "Скачать",
                            tint = if (isLossless) colors.primary else colors.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (isLossless) "FLAC ✓" else "Скачать",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 10.5.sp),
                            color = if (isLossless) colors.primary else colors.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                PillSegmentButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onShare()
                    },
                    position = PillPosition.END,
                    containerColor = colors.surfaceContainerHigh,
                    contentColor = colors.onSurface,
                    modifier = Modifier.weight(1f),
                    width = null,
                    height = 50.dp,
                    cornerRadius = 24.dp,
                    innerRadius = 6.dp
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Share,
                            contentDescription = "Поделиться",
                            tint = colors.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Поделиться",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 10.5.sp),
                            color = colors.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 3. Bento-карточка 1: Навигация (К исполнителю, К альбому, Радио по треку)
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = colors.surfaceContainerHigh.copy(alpha = 0.6f),
                border = BorderStroke(1.dp, colors.outlineVariant.copy(alpha = 0.15f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)) {
                    BentoMenuItem(
                        icon = Icons.Rounded.Person,
                        title = "К исполнителю",
                        subtitle = trackArtist.ifEmpty { "Неизвестный исполнитель" },
                        iconBg = colors.primaryContainer.copy(alpha = 0.6f),
                        iconTint = colors.primary,
                        onClick = onGoToArtist
                    )
                    if (trackAlbum.isNotBlank()) {
                        HorizontalDivider(
                            color = colors.outlineVariant.copy(alpha = 0.12f),
                            modifier = Modifier.padding(start = 52.dp, end = 12.dp)
                        )
                        BentoMenuItem(
                            icon = Icons.Rounded.Album,
                            title = "К альбому",
                            subtitle = trackAlbum,
                            iconBg = colors.secondaryContainer.copy(alpha = 0.6f),
                            iconTint = colors.secondary,
                            onClick = onGoToAlbum
                        )
                    }
                    HorizontalDivider(
                        color = colors.outlineVariant.copy(alpha = 0.12f),
                        modifier = Modifier.padding(start = 52.dp, end = 12.dp)
                    )
                    BentoMenuItem(
                        icon = Icons.Rounded.Radio,
                        title = "Радио по треку",
                        subtitle = "Бесконечный поток похожей музыки",
                        iconBg = colors.tertiaryContainer.copy(alpha = 0.6f),
                        iconTint = colors.tertiary,
                        onClick = onStartRadio
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 4. Bento-карточка 2: Аудио & Инфо (Текст песни, Аудиопоток & Hi-Res, В плейлист, Таймер сна)
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = colors.surfaceContainerHigh.copy(alpha = 0.6f),
                border = BorderStroke(1.dp, colors.outlineVariant.copy(alpha = 0.15f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)) {
                    BentoMenuItem(
                        icon = Icons.Rounded.Lyrics,
                        title = "Текст песни",
                        subtitle = "Синхронизированное караоке",
                        iconBg = colors.primaryContainer.copy(alpha = 0.6f),
                        iconTint = colors.primary,
                        onClick = onOpenLyrics
                    )
                    HorizontalDivider(
                        color = colors.outlineVariant.copy(alpha = 0.12f),
                        modifier = Modifier.padding(start = 52.dp, end = 12.dp)
                    )
                    BentoMenuItem(
                        icon = Icons.Rounded.GraphicEq,
                        title = "Аудиопоток & Hi-Res",
                        subtitle = if (isLossless) "FLAC Lossless • 44.1 kHz / 16-bit" else "High Quality AAC / MP3",
                        badge = if (isLossless) {
                            { AudioQualityBadge(text = "FLAC") }
                        } else null,
                        iconBg = colors.secondaryContainer.copy(alpha = 0.6f),
                        iconTint = colors.secondary,
                        onClick = onOpenAudioFidelity
                    )
                    HorizontalDivider(
                        color = colors.outlineVariant.copy(alpha = 0.12f),
                        modifier = Modifier.padding(start = 52.dp, end = 12.dp)
                    )
                    BentoMenuItem(
                        icon = Icons.AutoMirrored.Rounded.PlaylistAdd,
                        title = "Добавить в плейлист",
                        subtitle = "Сохранить в свой плейлист",
                        iconBg = colors.tertiaryContainer.copy(alpha = 0.6f),
                        iconTint = colors.tertiary,
                        onClick = onAddToPlaylist
                    )
                    HorizontalDivider(
                        color = colors.outlineVariant.copy(alpha = 0.12f),
                        modifier = Modifier.padding(start = 52.dp, end = 12.dp)
                    )
                    BentoMenuItem(
                        icon = Icons.Rounded.Bedtime,
                        title = "Таймер сна",
                        subtitle = "Остановить воспроизведение по времени",
                        iconBg = colors.surfaceVariant.copy(alpha = 0.7f),
                        iconTint = colors.onSurfaceVariant,
                        onClick = onSleepTimer
                    )
                }
            }

            // 5. Мини-очередь (если есть следующие треки)
            if (miniQueue.isNotEmpty()) {
                Spacer(modifier = Modifier.height(14.dp))
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
                    shape = RoundedCornerShape(20.dp),
                    color = colors.surfaceContainerHigh.copy(alpha = 0.5f),
                    border = BorderStroke(1.dp, colors.outlineVariant.copy(alpha = 0.12f)),
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
                                    color = colors.outlineVariant.copy(alpha = 0.15f),
                                    modifier = Modifier.padding(start = 48.dp)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun BentoMenuItem(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    iconTint: Color = MaterialTheme.colorScheme.primary,
    iconBg: Color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
    badge: @Composable (() -> Unit)? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color.Transparent,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(iconBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.5.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    badge?.invoke()
                }
                if (!subtitle.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(1.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontWeight = FontWeight.Medium,
                            fontSize = 12.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowForwardIos,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                modifier = Modifier.size(13.dp)
            )
        }
    }
}
