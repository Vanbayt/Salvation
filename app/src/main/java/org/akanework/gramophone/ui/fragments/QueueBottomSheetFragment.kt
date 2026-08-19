package org.akanework.gramophone.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import coil3.compose.AsyncImage
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.launch
import org.akanework.gramophone.ui.MainActivity
import org.akanework.gramophone.ui.components.library.AudioQualityBadge
import org.akanework.gramophone.ui.components.library.MiniEqualizerIndicator
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

class QueueBottomSheetFragment : BottomSheetDialogFragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                val context = LocalContext.current
                val isDark = isSystemInDarkTheme()
                val colorScheme = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                    if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
                } else {
                    if (isDark) darkColorScheme() else lightColorScheme()
                }

                MaterialTheme(colorScheme = colorScheme) {
                    val activity = requireActivity() as MainActivity
                    val player = activity.getPlayer()

                    var currentMediaIndex by remember { mutableIntStateOf(player?.currentMediaItemIndex ?: -1) }
                    var isPlaying by remember { mutableStateOf(player?.isPlaying ?: false) }
                    var mediaItemCount by remember { mutableIntStateOf(player?.mediaItemCount ?: 0) }

                    // Live Player Listener
                    DisposableEffect(player) {
                        if (player == null) return@DisposableEffect onDispose {}
                        val listener = object : Player.Listener {
                            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                                currentMediaIndex = player.currentMediaItemIndex
                                mediaItemCount = player.mediaItemCount
                            }

                            override fun onIsPlayingChanged(playing: Boolean) {
                                isPlaying = playing
                            }

                            override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) {
                                currentMediaIndex = player.currentMediaItemIndex
                                mediaItemCount = player.mediaItemCount
                            }
                        }
                        player.addListener(listener)
                        onDispose { player.removeListener(listener) }
                    }

                    val currentItem = player?.currentMediaItem
                    val playingFrom = currentItem?.mediaMetadata?.extras?.getString("PLAYING_FROM") ?: "Медиатека"

                    val allItems = remember(currentMediaIndex, mediaItemCount) {
                        val list = mutableListOf<MediaItem>()
                        if (player != null) {
                            for (i in 0 until player.mediaItemCount) {
                                list.add(player.getMediaItemAt(i))
                            }
                        }
                        list
                    }

                    QueueScreenContent(
                        items = allItems,
                        currentIndex = currentMediaIndex,
                        isPlaying = isPlaying,
                        playingFrom = playingFrom,
                        onClose = { dismiss() },
                        onItemClick = { index ->
                            player?.seekToDefaultPosition(index)
                            player?.play()
                        },
                        onMoveMediaItem = { fromIndex, toIndex ->
                            if (player != null && fromIndex in 0 until player.mediaItemCount && toIndex in 0 until player.mediaItemCount) {
                                player.moveMediaItem(fromIndex, toIndex)
                            }
                        },
                        onRemoveItem = { index ->
                            if (player != null && index in 0 until player.mediaItemCount) {
                                player.removeMediaItem(index)
                            }
                        },
                        onClearQueue = {
                            player?.clearMediaItems()
                            dismiss()
                        }
                    )
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        val dialog = dialog as? BottomSheetDialog
        val bottomSheet = dialog?.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
        bottomSheet?.let {
            val behavior = BottomSheetBehavior.from(it)
            behavior.state = BottomSheetBehavior.STATE_EXPANDED
            behavior.skipCollapsed = true
            it.layoutParams.height = ViewGroup.LayoutParams.MATCH_PARENT
            it.requestLayout()
            it.setBackgroundResource(android.R.color.transparent)
        }
    }
}

private data class QueueTrack(
    val originalIndex: Int,
    val mediaItem: MediaItem
)

@Composable
private fun QueueScreenContent(
    items: List<MediaItem>,
    currentIndex: Int,
    isPlaying: Boolean,
    playingFrom: String,
    onClose: () -> Unit,
    onItemClick: (Int) -> Unit,
    onMoveMediaItem: (Int, Int) -> Unit,
    onRemoveItem: (Int) -> Unit,
    onClearQueue: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    // Local mutable list for smooth reordering animations
    val localUpNext = remember(items, currentIndex) {
        mutableStateListOf<QueueTrack>().apply {
            if (currentIndex in items.indices && currentIndex + 1 < items.size) {
                for (i in (currentIndex + 1) until items.size) {
                    add(QueueTrack(i, items[i]))
                }
            }
        }
    }

    val reorderableState = rememberReorderableLazyListState(
        lazyListState = listState,
        onMove = { from, to ->
            val fromKey = from.key.toString()
            val toKey = to.key.toString()

            if (fromKey.startsWith("up_next_") && toKey.startsWith("up_next_")) {
                val fromIdx = localUpNext.indexOfFirst { "up_next_${it.originalIndex}_${it.mediaItem.mediaId}" == fromKey }
                val toIdx = localUpNext.indexOfFirst { "up_next_${it.originalIndex}_${it.mediaItem.mediaId}" == toKey }

                if (fromIdx != -1 && toIdx != -1 && fromIdx != toIdx) {
                    val fromGlobal = localUpNext[fromIdx].originalIndex
                    val toGlobal = localUpNext[toIdx].originalIndex

                    val item = localUpNext.removeAt(fromIdx)
                    localUpNext.add(toIdx, item)

                    onMoveMediaItem(fromGlobal, toGlobal)
                }
            }
        }
    )

    val currentItem = items.getOrNull(currentIndex)
    val historyItems = remember(items, currentIndex) {
        if (currentIndex in 1..items.size) {
            items.subList(0, currentIndex).mapIndexed { idx, mediaItem ->
                QueueTrack(idx, mediaItem)
            }
        } else emptyList()
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        color = colors.surfaceContainerLow,
        tonalElevation = 4.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
        ) {
            // Drag Handle
            Spacer(modifier = Modifier.height(10.dp))
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .size(width = 36.dp, height = 4.dp)
                    .clip(CircleShape)
                    .background(colors.onSurfaceVariant.copy(alpha = 0.35f))
            )

            // Header Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Очередь",
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 24.sp
                        ),
                        color = colors.onSurface
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Surface(
                        shape = CircleShape,
                        color = colors.surfaceContainerHighest.copy(alpha = 0.6f)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.QueueMusic,
                                contentDescription = null,
                                tint = colors.primary,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = "Из: $playingFrom",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 11.5.sp
                                ),
                                color = colors.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Кнопка перехода к играющему треку
                    if (currentIndex >= 0) {
                        FilledTonalIconButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                coroutineScope.launch {
                                    listState.animateScrollToItem(0)
                                }
                            },
                            shape = CircleShape,
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = colors.primaryContainer,
                                contentColor = colors.onPrimaryContainer
                            ),
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.MyLocation,
                                contentDescription = "Scroll to Playing",
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    // Кнопка очистки очереди
                    if (items.isNotEmpty()) {
                        FilledTonalIconButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onClearQueue()
                            },
                            shape = CircleShape,
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = colors.surfaceContainerHigh,
                                contentColor = colors.error
                            ),
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.DeleteSweep,
                                contentDescription = "Clear Queue",
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    // Кнопка закрытия
                    FilledTonalIconButton(
                        onClick = onClose,
                        shape = CircleShape,
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = colors.surfaceContainerHigh,
                            contentColor = colors.onSurfaceVariant
                        ),
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Close Queue",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            HorizontalDivider(
                color = colors.outlineVariant.copy(alpha = 0.25f),
                modifier = Modifier.padding(horizontal = 20.dp)
            )

            if (items.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.QueueMusic,
                            contentDescription = null,
                            tint = colors.onSurfaceVariant.copy(alpha = 0.4f),
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Очередь пуста",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = colors.onSurface
                        )
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    // 1. СЕЙЧАС ИГРАЕТ
                    if (currentItem != null) {
                        item(key = "header_now_playing") {
                            QueueSectionHeader(title = "СЕЙЧАС ИГРАЕТ")
                        }

                        item(key = "current_playing_track_${currentItem.mediaId}") {
                            NowPlayingQueueCard(
                                mediaItem = currentItem,
                                isPlaying = isPlaying,
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onClose()
                                }
                            )
                        }
                    }

                    // 2. ДАЛЕЕ В ОЧЕРЕДИ
                    if (localUpNext.isNotEmpty()) {
                        item(key = "header_up_next") {
                            Spacer(modifier = Modifier.height(8.dp))
                            QueueSectionHeader(
                                title = "ДАЛЕЕ В ОЧЕРЕДИ",
                                badgeText = "${localUpNext.size}"
                            )
                        }

                        itemsIndexed(
                            items = localUpNext,
                            key = { _, item -> "up_next_${item.originalIndex}_${item.mediaItem.mediaId}" }
                        ) { index, item ->
                            ReorderableItem(
                                state = reorderableState,
                                key = "up_next_${item.originalIndex}_${item.mediaItem.mediaId}"
                            ) { isDragging ->
                                val scale by animateFloatAsState(if (isDragging) 1.02f else 1f, label = "scale")

                                QueueTrackItem(
                                    mediaItem = item.mediaItem,
                                    positionNumber = index + 1,
                                    isHistory = false,
                                    modifier = Modifier.scale(scale),
                                    dragHandle = {
                                        Box(
                                            modifier = Modifier
                                                .draggableHandle(
                                                    onDragStarted = {
                                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    },
                                                    onDragStopped = {
                                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    }
                                                )
                                                .padding(6.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.DragIndicator,
                                                contentDescription = "Drag to reorder",
                                                tint = colors.onSurfaceVariant.copy(alpha = 0.6f),
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    },
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        onItemClick(item.originalIndex)
                                    },
                                    onRemove = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        onRemoveItem(item.originalIndex)
                                    }
                                )
                            }
                        }
                    }

                    // 3. РАНЕЕ ПРОСЛУШАНО
                    if (historyItems.isNotEmpty()) {
                        item(key = "header_history") {
                            Spacer(modifier = Modifier.height(8.dp))
                            QueueSectionHeader(
                                title = "РАНЕЕ ПРОСЛУШАНО",
                                badgeText = "${historyItems.size}"
                            )
                        }

                        itemsIndexed(
                            items = historyItems,
                            key = { _, item -> "history_${item.originalIndex}_${item.mediaItem.mediaId}" }
                        ) { _, item ->
                            QueueTrackItem(
                                mediaItem = item.mediaItem,
                                positionNumber = null,
                                isHistory = true,
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onItemClick(item.originalIndex)
                                },
                                onRemove = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onRemoveItem(item.originalIndex)
                                }
                            )
                        }
                    }

                    item {
                        Spacer(modifier = Modifier.height(40.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun QueueSectionHeader(
    title: String,
    badgeText: String? = null
) {
    val colors = MaterialTheme.colorScheme
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp,
                fontSize = 11.5.sp
            ),
            color = colors.primary
        )

        if (badgeText != null) {
            Surface(
                shape = CircleShape,
                color = colors.primaryContainer.copy(alpha = 0.6f)
            ) {
                Text(
                    text = badgeText,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.5.sp
                    ),
                    color = colors.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                )
            }
        }
    }
}

@Composable
private fun NowPlayingQueueCard(
    mediaItem: MediaItem,
    isPlaying: Boolean,
    onClick: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val meta = mediaItem.mediaMetadata
    val title = meta.title?.toString() ?: "Неизвестный трек"
    val artist = meta.artist?.toString() ?: "Неизвестный артист"
    val album = meta.albumTitle?.toString() ?: ""
    val isLossless = meta.extras?.getBoolean("IS_LOSSLESS", false) ?: false

    val originalUri = meta.artworkUri?.toString() ?: ""
    val coverUrl = if (originalUri.startsWith("/")) "http://185.196.41.31$originalUri" else originalUri

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        color = colors.primaryContainer,
        tonalElevation = 4.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Обложка с эквалайзером
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(colors.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                if (coverUrl.isNotEmpty()) {
                    AsyncImage(
                        model = coverUrl,
                        contentDescription = title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        imageVector = Icons.Rounded.MusicNote,
                        contentDescription = null,
                        tint = colors.onPrimaryContainer,
                        modifier = Modifier.size(28.dp)
                    )
                }

                if (isPlaying) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.45f)),
                        contentAlignment = Alignment.Center
                    ) {
                        MiniEqualizerIndicator(
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 15.5.sp
                        ),
                        color = colors.onPrimaryContainer,
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
                    text = artist + (if (album.isNotBlank()) " • $album" else ""),
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Medium,
                        fontSize = 13.sp
                    ),
                    color = colors.onPrimaryContainer.copy(alpha = 0.8f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Icon(
                imageVector = if (isPlaying) Icons.Rounded.GraphicEq else Icons.Rounded.PlayArrow,
                contentDescription = null,
                tint = colors.onPrimaryContainer,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
private fun QueueTrackItem(
    mediaItem: MediaItem,
    positionNumber: Int?,
    isHistory: Boolean,
    modifier: Modifier = Modifier,
    dragHandle: (@Composable () -> Unit)? = null,
    onClick: () -> Unit,
    onRemove: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val meta = mediaItem.mediaMetadata
    val title = meta.title?.toString() ?: "Неизвестный трек"
    val artist = meta.artist?.toString() ?: "Неизвестный артист"
    val album = meta.albumTitle?.toString() ?: ""
    val isLossless = meta.extras?.getBoolean("IS_LOSSLESS", false) ?: false

    val originalUri = meta.artworkUri?.toString() ?: ""
    val coverUrl = if (originalUri.startsWith("/")) "http://185.196.41.31$originalUri" else originalUri

    val containerColor = if (isHistory) {
        colors.surfaceContainerHighest.copy(alpha = 0.35f)
    } else {
        colors.surfaceContainerHighest.copy(alpha = 0.55f)
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = containerColor
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Номер позиции или иконка истории
            if (positionNumber != null) {
                Box(
                    modifier = Modifier.width(22.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Text(
                        text = "$positionNumber",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        ),
                        color = colors.primary
                    )
                }
            } else if (isHistory) {
                Box(
                    modifier = Modifier.width(22.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Icon(
                        imageVector = Icons.Rounded.History,
                        contentDescription = null,
                        tint = colors.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            // Обложка
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(colors.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                if (coverUrl.isNotEmpty()) {
                    AsyncImage(
                        model = coverUrl,
                        contentDescription = title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        imageVector = Icons.Rounded.MusicNote,
                        contentDescription = null,
                        tint = colors.onSurfaceVariant,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Название и артист
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
                        color = if (isHistory) colors.onSurface.copy(alpha = 0.7f) else colors.onSurface,
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
                    text = artist + (if (album.isNotBlank()) " • $album" else ""),
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                    color = colors.onSurfaceVariant.copy(alpha = if (isHistory) 0.6f else 0.8f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Ручка перетаскивания (Drag Handle)
            if (dragHandle != null) {
                dragHandle()
            }

            // Кнопка удаления из очереди
            FilledTonalIconButton(
                onClick = onRemove,
                shape = CircleShape,
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = Color.Transparent,
                    contentColor = colors.onSurfaceVariant.copy(alpha = 0.6f)
                ),
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "Remove from Queue",
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}