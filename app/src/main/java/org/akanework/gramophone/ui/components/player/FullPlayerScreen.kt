package org.akanework.gramophone.ui.components.player

import android.graphics.drawable.BitmapDrawable
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentManager
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import coil3.compose.AsyncImage
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.akanework.gramophone.logic.LikeCache
import org.akanework.gramophone.logic.api.NetworkClient
import org.akanework.gramophone.logic.utils.LyricsRepository
import org.akanework.gramophone.logic.utils.LyricsResult
import org.akanework.gramophone.logic.utils.LyricsSource
import org.akanework.gramophone.ui.MainActivity
import org.akanework.gramophone.ui.components.CookiePlayButton
import org.akanework.gramophone.ui.components.LyricsScreen
import org.akanework.gramophone.ui.components.SquigglySlider
import org.akanework.gramophone.ui.components.library.AudioQualityBadge
import org.akanework.gramophone.ui.fragments.ArtistFragment
import org.akanework.gramophone.ui.fragments.PlayerMenuBottomSheet
import java.util.Locale
import kotlin.math.roundToInt

private enum class PlaybackActionType { NONE, PREVIOUS, PLAY_PAUSE, NEXT }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun FullPlayerScreen(
    onDismiss: () -> Unit,
    fragmentManager: FragmentManager? = null,
    expansionFraction: Float = 1f,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = context as? MainActivity
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    val api = remember { NetworkClient.getApi(context) }

    // Стейты воспроизведения
    var currentItem by remember { mutableStateOf<MediaItem?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var currentPositionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(1000L) }
    var shuffleEnabled by remember { mutableStateOf(false) }
    var repeatMode by remember { mutableIntStateOf(Player.REPEAT_MODE_OFF) }

    // Тексты караоке
    var isLyricsVisible by remember { mutableStateOf(false) }
    var currentLyricsResult by remember { mutableStateOf<LyricsResult?>(null) }
    var isLyricsLoading by remember { mutableStateOf(false) }
    var selectedLyricsSource by remember { mutableStateOf(LyricsSource.ALL) }

    // Тональный динамический фон
    var dominantColor by remember { mutableStateOf<Color?>(null) }
    var dragOffsetX by remember { mutableFloatStateOf(0f) }

    val player = activity?.getPlayer()

    // Слушатель MediaController
    DisposableEffect(activity) {
        val p = activity?.getPlayer()
        if (p != null) {
            currentItem = p.currentMediaItem
            isPlaying = p.isPlaying
            currentPositionMs = p.currentPosition
            durationMs = p.duration.coerceAtLeast(1000L)
            shuffleEnabled = p.shuffleModeEnabled
            repeatMode = p.repeatMode

            val listener = object : Player.Listener {
                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                    currentItem = mediaItem
                    currentPositionMs = p.currentPosition
                    durationMs = p.duration.coerceAtLeast(1000L)
                }

                override fun onIsPlayingChanged(playing: Boolean) {
                    isPlaying = playing
                }

                override fun onShuffleModeEnabledChanged(enabled: Boolean) {
                    shuffleEnabled = enabled
                }

                override fun onRepeatModeChanged(mode: Int) {
                    repeatMode = mode
                }
            }
            p.addListener(listener)
            onDispose { p.removeListener(listener) }
        } else {
            onDispose {}
        }
    }

    // Опрос позиции
    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            while (true) {
                val p = activity?.getPlayer()
                if (p != null) {
                    currentPositionMs = p.currentPosition
                    durationMs = p.duration.coerceAtLeast(1000L)
                }
                kotlinx.coroutines.delay(32)
            }
        }
    }

    val metadata = currentItem?.mediaMetadata
    val title = metadata?.title?.toString() ?: "Неизвестный трек"
    val artist = metadata?.artist?.toString() ?: "Неизвестный исполнитель"
    val album = metadata?.albumTitle?.toString() ?: ""
    val isLossless = currentItem?.mediaMetadata?.extras?.getBoolean("IS_LOSSLESS") ?: true

    val trackId = currentItem?.mediaId ?: ""
    var isLiked by remember(trackId) {
        mutableStateOf(LikeCache.isLiked(trackId, title = title, artist = artist))
    }

    val defaultPrefs = remember { androidx.preference.PreferenceManager.getDefaultSharedPreferences(context) }
    var isDynamicCoverColorEnabled by remember {
        mutableStateOf(defaultPrefs.getBoolean("dynamic_cover_color", true))
    }

    DisposableEffect(Unit) {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == "dynamic_cover_color") {
                isDynamicCoverColorEnabled = defaultPrefs.getBoolean("dynamic_cover_color", true)
            }
        }
        defaultPrefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose {
            defaultPrefs.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }

    val isDarkTheme = isSystemInDarkTheme()
    var dynamicColors by remember { mutableStateOf<org.akanework.gramophone.ui.theme.DynamicArtworkTheme.ArtworkColors?>(null) }
    val defaultSurface = MaterialTheme.colorScheme.surface
    val defaultSurfaceContainer = MaterialTheme.colorScheme.surfaceContainerLowest

    val coverUrl = metadata?.artworkUri?.toString()?.let {
        if (it.startsWith("/")) "http://185.196.41.31$it" else it
    }

    // Извлечение динамической палитры
    LaunchedEffect(coverUrl, isDarkTheme) {
        if (!coverUrl.isNullOrEmpty()) {
            withContext(Dispatchers.IO) {
                try {
                    val request = ImageRequest.Builder(context)
                        .data(coverUrl)
                        .allowHardware(false)
                        .build()
                    val result = context.imageLoader.execute(request)
                    if (result is SuccessResult) {
                        val bitmap = (result.image as? BitmapDrawable)?.bitmap
                        if (bitmap != null) {
                            androidx.palette.graphics.Palette.from(bitmap).generate { palette ->
                                dynamicColors = org.akanework.gramophone.ui.theme.DynamicArtworkTheme.calculateFromPalette(
                                    palette = palette,
                                    isDarkTheme = isDarkTheme,
                                    defaultSurface = defaultSurface,
                                    defaultSurfaceContainer = defaultSurfaceContainer
                                )
                            }
                        }
                    }
                } catch (_: Exception) {}
            }
        } else {
            dynamicColors = null
        }
    }

    // Загрузка караоке текстов
    fun loadLyrics(source: LyricsSource = selectedLyricsSource) {
        if (title.isBlank()) return
        isLyricsLoading = true
        coroutineScope.launch(Dispatchers.IO) {
            val options = org.akanework.gramophone.logic.utils.LrcUtils.LrcParserOptions(
                trim = true,
                multiLine = false,
                errorText = "Не удалось загрузить текст"
            )
            val result = LyricsRepository.fetchLyrics(
                context = context,
                file = null,
                mimeType = null,
                sampleRate = 0,
                metadata = null,
                artist = artist,
                title = title,
                durationMs = durationMs,
                preferredSource = source,
                options = options
            )
            withContext(Dispatchers.Main) {
                currentLyricsResult = result
                isLyricsLoading = false
            }
        }
    }

    LaunchedEffect(trackId, isLyricsVisible) {
        if (isLyricsVisible && trackId.isNotBlank()) {
            loadLyrics()
        }
    }

    BackHandler(enabled = isLyricsVisible) {
        isLyricsVisible = false
    }

    val targetTopColor = if (isDynamicCoverColorEnabled && dynamicColors != null) {
        dynamicColors!!.fullPlayerGradientTop
    } else {
        MaterialTheme.colorScheme.surfaceContainerLowest
    }

    val targetSecondaryColor = if (isDynamicCoverColorEnabled && dynamicColors != null) {
        dynamicColors!!.fullPlayerSecondaryGlow
    } else {
        MaterialTheme.colorScheme.surface
    }

    val animatedTopColor by animateColorAsState(
        targetValue = targetTopColor,
        animationSpec = tween(durationMillis = 650, easing = FastOutSlowInEasing),
        label = "dominantTopBg"
    )

    val animatedSecondaryColor by animateColorAsState(
        targetValue = targetSecondaryColor,
        animationSpec = tween(durationMillis = 650, easing = FastOutSlowInEasing),
        label = "dominantSecBg"
    )

    val coverScale by animateFloatAsState(
        targetValue = if (isPlaying) 1f else 0.94f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "coverScale"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        // Динамический фон в стиле PixelPlayer
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            animatedTopColor.copy(alpha = 0.55f * expansionFraction),
                            animatedSecondaryColor.copy(alpha = 0.25f * expansionFraction),
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.90f),
                            MaterialTheme.colorScheme.surface
                        )
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 22.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // 1. Верхний Top Bar (Большой круглый Chevron + Заголовок + Бейдж + 3 точки)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                FilledTonalIconButton(
                    onClick = onDismiss,
                    shape = CircleShape,
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.7f),
                        contentColor = MaterialTheme.colorScheme.onSurface
                    ),
                    modifier = Modifier.size(46.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.KeyboardArrowDown,
                        contentDescription = "Dismiss",
                        modifier = Modifier.size(28.dp)
                    )
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f).padding(horizontal = 12.dp)
                ) {
                    Text(
                        text = "СЕЙЧАС ИГРАЕТ",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.5.sp,
                            fontSize = 11.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )

                    if (isLossless) {
                        Spacer(modifier = Modifier.height(3.dp))
                        AudioQualityBadge(text = "FLAC • Lossless")
                    }
                }

                FilledTonalIconButton(
                    onClick = {
                        if (fragmentManager != null) {
                            PlayerMenuBottomSheet().show(fragmentManager, "PLAYER_MENU_SHEET")
                        }
                    },
                    shape = CircleShape,
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.7f),
                        contentColor = MaterialTheme.colorScheme.onSurface
                    ),
                    modifier = Modifier.size(46.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.MoreVert,
                        contentDescription = "Options",
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            // 2. Центральная область (Большая Обложка со скруглением 32.dp ИЛИ Караоке)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(vertical = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                AnimatedContent(
                    targetState = isLyricsVisible,
                    transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(200)) },
                    label = "CoverOrLyrics"
                ) { lyricsMode ->
                    if (lyricsMode) {
                        LyricsScreen(
                            trackTitle = title,
                            artistName = artist,
                            coverUrl = coverUrl ?: "",
                            isPlaying = isPlaying,
                            lyricsResult = currentLyricsResult,
                            isLoading = isLyricsLoading,
                            currentPositionMs = currentPositionMs,
                            selectedSource = selectedLyricsSource,
                            onSourceSelected = { newSource ->
                                selectedLyricsSource = newSource
                                loadLyrics(newSource)
                            },
                            onPlayPauseToggle = {
                                val p = player ?: return@LyricsScreen
                                if (p.playWhenReady) p.pause() else p.play()
                            },
                            onSkipNext = {
                                player?.seekToNext()
                            },
                            onSeekTo = { pos -> player?.seekTo(pos) },
                            onDismiss = { isLyricsVisible = false },
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        val animatedOffsetX by animateFloatAsState(
                            targetValue = dragOffsetX,
                            animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                            label = "coverDragOffset"
                        )

                        Surface(
                            modifier = Modifier
                                .fillMaxWidth(0.96f)
                                .aspectRatio(1f)
                                .offset { IntOffset(animatedOffsetX.roundToInt(), 0) }
                                .scale(coverScale)
                                .shadow(elevation = if (isPlaying) 24.dp else 8.dp, shape = RoundedCornerShape(32.dp))
                                .clip(RoundedCornerShape(32.dp))
                                .pointerInput(Unit) {
                                    detectHorizontalDragGestures(
                                        onDragEnd = {
                                            if (dragOffsetX < -120f) {
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                player?.seekToNext()
                                            } else if (dragOffsetX > 120f) {
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                player?.seekToPrevious()
                                            }
                                            dragOffsetX = 0f
                                        },
                                        onHorizontalDrag = { _, dragAmount ->
                                            dragOffsetX = (dragOffsetX + dragAmount).coerceIn(-260f, 260f)
                                        }
                                    )
                                },
                            shape = RoundedCornerShape(32.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh
                        ) {
                            if (!coverUrl.isNullOrEmpty()) {
                                AsyncImage(
                                    model = coverUrl,
                                    contentDescription = title,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.MusicNote,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(72.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 3. Секция Названия и Исполнителя (Expressive Typography)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 24.sp,
                            letterSpacing = (-0.3).sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        modifier = Modifier.basicMarquee()
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = artist,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 16.sp
                        ),
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.clickable {
                            val artistId = currentItem?.mediaMetadata?.extras?.getString("ARTIST_ID")
                            if (!artistId.isNullOrBlank()) {
                                activity?.startFragment(ArtistFragment.newInstance(artistId))
                                onDismiss()
                            }
                        }
                    )
                }
            }

            // 4. Волнистый слайдер прогресса (SquigglySlider)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                SquigglySlider(
                    position = currentPositionMs.toFloat(),
                    duration = durationMs.toFloat(),
                    isPlaying = isPlaying,
                    onValueChange = { newPos ->
                        currentPositionMs = newPos.toLong()
                    },
                    onValueChangeFinished = {
                        player?.seekTo(currentPositionMs)
                    }
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = formatTime(currentPositionMs),
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.5.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Text(
                        text = formatTime(durationMs),
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.5.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // 5. Экспрессивный блок кнопок управления воспроизведением (PixelPlayer Animated Controls)
            ExpressivePlaybackControlsRow(
                isPlaying = isPlaying,
                onPrevious = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    player?.seekToPrevious()
                },
                onPlayPause = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    val p = player ?: return@ExpressivePlaybackControlsRow
                    if (p.playWhenReady) p.pause() else p.play()
                },
                onNext = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    player?.seekToNext()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            )

            // 6. Нижняя сегментированная капсула (PixelPlayer BottomToggleRow Capsule)
            BottomSegmentedCapsuleRow(
                isShuffleEnabled = shuffleEnabled,
                onShuffleToggle = {
                    val p = player ?: return@BottomSegmentedCapsuleRow
                    val nextShuffle = !p.shuffleModeEnabled
                    p.shuffleModeEnabled = nextShuffle
                    shuffleEnabled = nextShuffle
                },
                repeatMode = repeatMode,
                onRepeatToggle = {
                    val p = player ?: return@BottomSegmentedCapsuleRow
                    val nextMode = when (p.repeatMode) {
                        Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                        Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                        Player.REPEAT_MODE_ONE -> Player.REPEAT_MODE_OFF
                        else -> Player.REPEAT_MODE_OFF
                    }
                    p.repeatMode = nextMode
                    repeatMode = nextMode
                },
                isFavorite = isLiked,
                onFavoriteToggle = {
                    val newLiked = !isLiked
                    isLiked = newLiked
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)

                    if (newLiked) {
                        LikeCache.add(trackId, title = title, artist = artist)
                        api.likeTrack(trackId).enqueue(object : retrofit2.Callback<okhttp3.ResponseBody> {
                            override fun onResponse(call: retrofit2.Call<okhttp3.ResponseBody>, response: retrofit2.Response<okhttp3.ResponseBody>) {}
                            override fun onFailure(call: retrofit2.Call<okhttp3.ResponseBody>, t: Throwable) {
                                LikeCache.remove(trackId, title = title, artist = artist)
                                isLiked = false
                            }
                        })
                    } else {
                        LikeCache.remove(trackId, title = title, artist = artist)
                        api.unlikeTrack(trackId).enqueue(object : retrofit2.Callback<okhttp3.ResponseBody> {
                            override fun onResponse(call: retrofit2.Call<okhttp3.ResponseBody>, response: retrofit2.Response<okhttp3.ResponseBody>) {}
                            override fun onFailure(call: retrofit2.Call<okhttp3.ResponseBody>, t: Throwable) {
                                LikeCache.add(trackId, title = title, artist = artist)
                                isLiked = true
                            }
                        })
                    }
                },
                isLyricsActive = isLyricsVisible,
                onLyricsToggle = { isLyricsVisible = !isLyricsVisible },
                onQueueClick = {
                    if (fragmentManager != null) {
                        PlayerMenuBottomSheet().show(fragmentManager, "PLAYER_MENU_SHEET")
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp, top = 4.dp)
            )
        }
    }
}

/**
 * Экспрессивный блок кнопок управления (PixelPlayer AnimatedPlaybackControls Style)
 * 3 соединенные капсулы со squash & stretch динамикой при нажатии
 */
@Composable
private fun ExpressivePlaybackControlsRow(
    isPlaying: Boolean,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier
) {
    var pressedAction by remember { mutableStateOf<PlaybackActionType?>(null) }

    LaunchedEffect(pressedAction) {
        if (pressedAction != null) {
            delay(200)
            pressedAction = null
        }
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(78.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Кнопка Назад
        val prevWeight by animateFloatAsState(
            targetValue = if (pressedAction == PlaybackActionType.PREVIOUS) 1.25f else 1f,
            animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
            label = "prevWeight"
        )
        Surface(
            shape = RoundedCornerShape(32.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.75f),
            modifier = Modifier
                .weight(prevWeight)
                .fillMaxHeight()
                .clip(RoundedCornerShape(32.dp))
                .clickable {
                    pressedAction = PlaybackActionType.PREVIOUS
                    onPrevious()
                }
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Rounded.SkipPrevious,
                    contentDescription = "Previous",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(32.dp)
                )
            }
        }

        // Центральная кнопка Play / Pause (Выразительный Primary Pill)
        val playWeight by animateFloatAsState(
            targetValue = if (pressedAction == PlaybackActionType.PLAY_PAUSE) 1.35f else 1.25f,
            animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
            label = "playWeight"
        )
        Surface(
            shape = RoundedCornerShape(32.dp),
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .weight(playWeight)
                .fillMaxHeight()
                .clip(RoundedCornerShape(32.dp))
                .clickable {
                    pressedAction = PlaybackActionType.PLAY_PAUSE
                    onPlayPause()
                }
        ) {
            Box(contentAlignment = Alignment.Center) {
                AnimatedContent(
                    targetState = isPlaying,
                    transitionSpec = { fadeIn(tween(150)) togetherWith fadeOut(tween(150)) },
                    label = "PlayPauseIcon"
                ) { playing ->
                    Icon(
                        imageVector = if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = if (playing) "Pause" else "Play",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(38.dp)
                    )
                }
            }
        }

        // Кнопка Вперед
        val nextWeight by animateFloatAsState(
            targetValue = if (pressedAction == PlaybackActionType.NEXT) 1.25f else 1f,
            animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
            label = "nextWeight"
        )
        Surface(
            shape = RoundedCornerShape(32.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.75f),
            modifier = Modifier
                .weight(nextWeight)
                .fillMaxHeight()
                .clip(RoundedCornerShape(32.dp))
                .clickable {
                    pressedAction = PlaybackActionType.NEXT
                    onNext()
                }
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Rounded.SkipNext,
                    contentDescription = "Next",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(32.dp)
                )
            }
        }
    }
}

/**
 * Нижняя сегментированная капсула переключателей (PixelPlayer BottomToggleRow Style)
 */
@Composable
private fun BottomSegmentedCapsuleRow(
    isShuffleEnabled: Boolean,
    onShuffleToggle: () -> Unit,
    repeatMode: Int,
    onRepeatToggle: () -> Unit,
    isFavorite: Boolean,
    onFavoriteToggle: () -> Unit,
    isLyricsActive: Boolean,
    onLyricsToggle: () -> Unit,
    onQueueClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.65f),
        modifier = modifier
            .fillMaxWidth()
            .height(58.dp)
            .clip(CircleShape)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 1. Shuffle
            SegmentedCapsuleItem(
                icon = Icons.Rounded.Shuffle,
                contentDescription = "Shuffle",
                isActive = isShuffleEnabled,
                activeContainerColor = MaterialTheme.colorScheme.primaryContainer,
                activeContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                onClick = onShuffleToggle,
                modifier = Modifier.weight(1f)
            )

            // 2. Repeat
            val isRepeatActive = repeatMode != Player.REPEAT_MODE_OFF
            val repeatIcon = if (repeatMode == Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat
            SegmentedCapsuleItem(
                icon = repeatIcon,
                contentDescription = "Repeat",
                isActive = isRepeatActive,
                activeContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                activeContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                onClick = onRepeatToggle,
                modifier = Modifier.weight(1f)
            )

            // 3. Favorite
            SegmentedCapsuleItem(
                icon = if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                contentDescription = "Favorite",
                isActive = isFavorite,
                activeContainerColor = MaterialTheme.colorScheme.tertiaryContainer,
                activeContentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                onClick = onFavoriteToggle,
                modifier = Modifier.weight(1f)
            )

            // 4. Lyrics
            SegmentedCapsuleItem(
                icon = Icons.Rounded.Lyrics,
                contentDescription = "Lyrics",
                isActive = isLyricsActive,
                activeContainerColor = MaterialTheme.colorScheme.primaryContainer,
                activeContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                onClick = onLyricsToggle,
                modifier = Modifier.weight(1f)
            )

            // 5. Queue
            SegmentedCapsuleItem(
                icon = Icons.AutoMirrored.Rounded.QueueMusic,
                contentDescription = "Queue",
                isActive = false,
                activeContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                activeContentColor = MaterialTheme.colorScheme.onSurface,
                onClick = onQueueClick,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun SegmentedCapsuleItem(
    icon: ImageVector,
    contentDescription: String,
    isActive: Boolean,
    activeContainerColor: Color,
    activeContentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val containerColor by animateColorAsState(
        targetValue = if (isActive) activeContainerColor else Color.Transparent,
        animationSpec = tween(200),
        label = "segContainer"
    )
    val contentColor by animateColorAsState(
        targetValue = if (isActive) activeContentColor else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = tween(200),
        label = "segContent"
    )

    Surface(
        shape = CircleShape,
        color = containerColor,
        modifier = modifier
            .fillMaxHeight()
            .clip(CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = true),
                onClick = onClick
            )
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = contentColor,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

private fun formatTime(millis: Long): String {
    val totalSeconds = (millis / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.getDefault(), "%d:%02d", minutes, seconds)
}
