package org.akanework.gramophone.ui

import android.content.Intent
import android.content.IntentSender
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.provider.Settings
import android.view.Choreographer
import android.view.View
import android.widget.Toast
import android.app.NotificationManager
import android.app.SearchManager
import android.app.assist.AssistContent
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ContentResolver
import android.content.ContentUris
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.annotation.RequiresApi
import androidx.core.app.ActivityCompat
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.Log
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaController
import androidx.viewpager2.adapter.FragmentStateAdapter // 🔥 Импорт для ViewPager
import androidx.viewpager2.widget.ViewPager2 // 🔥 Импорт для ViewPager
import coil3.imageLoader
import coil3.compose.AsyncImage

// Compose Imports
import androidx.compose.animation.core.*
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.graphics.lerp
import org.akanework.gramophone.ui.components.library.AudioQualityBadge
import coil3.request.allowHardware
import coil3.request.SuccessResult
import coil3.BitmapImage

// App Imports
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.transition.MaterialElevationScale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.akanework.gramophone.R
import org.akanework.gramophone.logic.LikeCache
import org.akanework.gramophone.logic.enableEdgeToEdgeProperly
import org.akanework.gramophone.logic.gramophoneApplication
import org.akanework.gramophone.logic.hasAudioPermission
import org.akanework.gramophone.logic.hasScopedStorageV2
import org.akanework.gramophone.logic.hasScopedStorageWithMediaTypes
import org.akanework.gramophone.logic.needsMissingOnDestroyCallWorkarounds
import org.akanework.gramophone.logic.postAtFrontOfQueueAsync
import org.akanework.gramophone.logic.ui.BaseActivity
import org.akanework.gramophone.ui.adapters.PlaylistAdapter
import org.akanework.gramophone.ui.fragments.LibraryFragment
import org.akanework.gramophone.ui.fragments.MainFragment
import org.akanework.gramophone.ui.fragments.OnlineSearchFragment
import org.akanework.gramophone.ui.fragments.SearchFragment
import org.akanework.gramophone.ui.fragments.ViewPagerFragment
import org.akanework.gramophone.ui.components.CookiePlayButton
import org.akanework.gramophone.ui.components.SquigglySlider
import org.akanework.gramophone.ui.fragments.ComposeContainerFragment
import uk.akane.libphonograph.manipulator.ItemManipulator
import java.io.File

enum class AppTab(val title: String, val iconRes: Int) {
    HOME("Главная", R.drawable.ic_home),
    SEARCH("Поиск", R.drawable.ic_search),
    LIBRARY("Медиатека", R.drawable.ic_library)
}

class MainActivity : BaseActivity() {

    companion object {
        private const val PERMISSION_READ_MEDIA_AUDIO = 100
        const val PLAYBACK_AUTO_START_FOR_FGS = "AutoStartFgs"
        const val PLAYBACK_AUTO_PLAY_ID = "AutoStartId"
        const val PLAYBACK_AUTO_PLAY_POSITION = "AutoStartPos"
    }

    val controllerViewModel: MediaControllerViewModel by viewModels()
    val startingActivity = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {}

    private val handler = Handler(Looper.getMainLooper())
    private val reportFullyDrawnRunnable = Runnable { if (!ready) reportFullyDrawn() }
    private var ready = false

    // Стейты плеера
    private var miniPlayerVisible by mutableStateOf(false)
    var isGlobalUiVisible by mutableStateOf(true)
    private var trackTitle by mutableStateOf("")
    private var trackArtist by mutableStateOf("")
    private var coverUrl by mutableStateOf("")
    private var isPlaying by mutableStateOf(false)
    private var isBuffering by mutableStateOf(false)
    private var currentPosition by mutableFloatStateOf(0f)
    private var trackDuration by mutableFloatStateOf(100f)
    private var isLiked by mutableStateOf(false)
    private var isShuffle by mutableStateOf(false)
    private var isLoop by mutableStateOf(false)
    private var repeatModeState by mutableIntStateOf(Player.REPEAT_MODE_OFF)
    private var currentBottomTab by mutableStateOf(AppTab.HOME)

    // 🔥 Ссылка на пейджер
    private lateinit var mainPager: ViewPager2

    private var collapsePlayerAction: (() -> Unit)? = null

    fun collapsePlayer() {
        runOnUiThread {
            collapsePlayerAction?.invoke()
        }
    }

    private var isForeground = false
    private val progressHandler = Handler(Looper.getMainLooper())
    private val progressRunnable = object : Runnable {
        override fun run() {
            if (!isForeground) return
            val player = getPlayer()
            if (player != null) {
                isBuffering = player.playbackState == Player.STATE_BUFFERING
                val isPlaying = player.isPlaying || (player.playWhenReady && player.playbackState != Player.STATE_ENDED && player.playbackState != Player.STATE_IDLE)
                if (isPlaying) {
                    val currentId = player.currentMediaItem?.mediaId
                    val dynamicDur = currentId?.let { org.akanework.gramophone.logic.GramophonePlaybackService.getTrackDuration(it) }
                    val rawDur = if (player.duration > 0) player.duration else (player.currentMediaItem?.mediaMetadata?.durationMs ?: dynamicDur)
                    val dur = if (rawDur != null && rawDur > 0) rawDur.toFloat() else 1f
                    trackDuration = dur
                    currentPosition = player.currentPosition.toFloat().coerceIn(0f, dur)
                    progressHandler.postDelayed(this, 32)
                } else {
                    progressHandler.postDelayed(this, 500)
                }
            } else {
                progressHandler.postDelayed(this, 500)
            }
        }
    }

    private lateinit var intentDelete: ActivityResultLauncher<Intent>
    private lateinit var intentSenderDelete: ActivityResultLauncher<IntentSenderRequest>
    private lateinit var addToPlaylistIntentSender: ActivityResultLauncher<IntentSenderRequest>
    private var pendingRequest: Bundle? = null
    private var pendingDeleteRequest: Bundle? = null

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        Log.i("MainActivity", "onCreate($intent)")
        installSplashScreen().setKeepOnScreenCondition { !ready }
        super.onCreate(savedInstanceState)
        org.akanework.gramophone.logic.lossless.FlacDownloadManager.init(this)
        lifecycle.addObserver(controllerViewModel)
        enableEdgeToEdgeProperly()

        if (savedInstanceState?.containsKey("AddToPlaylistPendingRequest") == true) pendingRequest = savedInstanceState.getBundle("AddToPlaylistPendingRequest")
        if (savedInstanceState?.containsKey("DeletePendingRequest") == true) pendingDeleteRequest = savedInstanceState.getBundle("DeletePendingRequest")
        intentDelete = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            val req = pendingDeleteRequest ?: return@registerForActivityResult
            pendingDeleteRequest = null
            CoroutineScope(Dispatchers.Default).launch { ItemManipulator.continueDeleteFromIntent(this@MainActivity, it.resultCode, it.data, req) }
        }
        intentSenderDelete = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
            val req = pendingDeleteRequest ?: return@registerForActivityResult
            pendingDeleteRequest = null
            CoroutineScope(Dispatchers.Default).launch { ItemManipulator.continueDeleteFromPendingIntent(this@MainActivity, it.resultCode, req) }
        }
        addToPlaylistIntentSender = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
            val req = pendingRequest ?: return@registerForActivityResult
            pendingRequest = null
            CoroutineScope(Dispatchers.Default).launch { doAddToPlaylist(it.resultCode, req) }
        }

        setContentView(R.layout.activity_main)

        // 🔥 ИНИЦИАЛИЗАЦИЯ VIEWPAGER
        mainPager = findViewById(R.id.main_pager)
        mainPager.isUserInputEnabled = false // Отключаем свайп между корневыми экранами навбара
        mainPager.adapter = object : FragmentStateAdapter(this) {
            override fun getItemCount() = 3
            override fun createFragment(position: Int): Fragment {
                return when (position) {
                    0 -> MainFragment()
                    1 -> OnlineSearchFragment()
                    2 -> LibraryFragment()
                    else -> throw IllegalStateException("Invalid position")
                }
            }
        }
        mainPager.offscreenPageLimit = 2 // Держим в памяти, чтобы было плавно
        // 🔥 ПРАВИЛЬНАЯ ПРЕМИАЛЬНАЯ АНИМАЦИЯ (Без наложения экранов)
        mainPager.setPageTransformer { page, position ->
            val absPos = Math.abs(position)
            page.apply {
                if (absPos >= 1f) {
                    alpha = 0f // Полностью прячем соседние экраны
                } else {
                    // Плавное затухание до 0
                    alpha = 1f - absPos
                    // Микро-масштабирование (эффект глубины)
                    val scale = 0.95f + (1f - 0.95f) * (1f - absPos)
                    scaleX = scale
                    scaleY = scale
                    // ОБЯЗАТЕЛЬНО СБРАСЫВАЕМ СДВИГ, чтобы они не наезжали друг на друга!
                    translationX = 0f
                }
            }
        }
        mainPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                // Синхронизируем Compose-стейт с пейджером
                currentBottomTab = AppTab.values()[position]
            }
        })

        val composeOverlay = findViewById<ComposeView>(R.id.compose_overlay)
        composeOverlay.setViewCompositionStrategy(androidx.compose.ui.platform.ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)

        composeOverlay.setContent {
            val context = LocalContext.current
            val isDarkTheme = isSystemInDarkTheme()
            val dynamicColorScheme = when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                    if (isDarkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
                }
                isDarkTheme -> darkColorScheme()
                else -> lightColorScheme()
            }

            MaterialTheme(colorScheme = dynamicColorScheme) {
                val coroutineScope = rememberCoroutineScope()
                val density = LocalDensity.current
                val configuration = LocalConfiguration.current

                val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

                val navBarHeight = 80.dp
                val navBarOffset = 16.dp
                val gap = 4.dp
                val miniPlayerHeight = 72.dp

                val collapsedBottomSpace = navBarBottom + navBarOffset + navBarHeight + gap
                val staticPeekHeight = collapsedBottomSpace + miniPlayerHeight

                val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }
                val peekPx = with(density) { staticPeekHeight.toPx() }
                val maxTravel = (screenHeightPx - peekPx).coerceAtLeast(1f)

                val scaffoldState = rememberBottomSheetScaffoldState(
                    bottomSheetState = rememberStandardBottomSheetState(
                        initialValue = SheetValue.PartiallyExpanded,
                        skipHiddenState = true
                    )
                )

                DisposableEffect(scaffoldState) {
                    collapsePlayerAction = {
                        coroutineScope.launch {
                            try {
                                scaffoldState.bottomSheetState.partialExpand()
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    }
                    onDispose {
                        collapsePlayerAction = null
                    }
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

                var dynamicColors by remember { mutableStateOf<org.akanework.gramophone.ui.theme.DynamicArtworkTheme.ArtworkColors?>(null) }
                var isLyricsOpenState by remember { mutableStateOf(false) }

                val baseMiniColor = MaterialTheme.colorScheme.surfaceVariant
                val fullColor = MaterialTheme.colorScheme.surface

                LaunchedEffect(coverUrl, isDarkTheme) {
                    if (coverUrl.isNotEmpty()) {
                        withContext(Dispatchers.IO) {
                            try {
                                val request = coil3.request.ImageRequest.Builder(context)
                                    .data(coverUrl)
                                    .allowHardware(false)
                                    .build()
                                val result = context.imageLoader.execute(request)

                                val bitmap = (result as? SuccessResult)?.image?.let { it as? BitmapImage }?.bitmap

                                bitmap?.let { bmp ->
                                    androidx.palette.graphics.Palette.from(bmp).generate { palette ->
                                        dynamicColors = org.akanework.gramophone.ui.theme.DynamicArtworkTheme.calculateFromPalette(
                                            palette = palette,
                                            isDarkTheme = isDarkTheme,
                                            defaultSurface = fullColor,
                                            defaultSurfaceContainer = baseMiniColor
                                        )
                                    }
                                }
                            } catch (e: Exception) { dynamicColors = null }
                        }
                    } else {
                        dynamicColors = null
                    }
                }

                val targetMiniColor = if (isDynamicCoverColorEnabled && dynamicColors != null) {
                    dynamicColors!!.miniPlayerContainer
                } else baseMiniColor

                val targetFullColor = if (isDynamicCoverColorEnabled && dynamicColors != null) {
                    dynamicColors!!.fullPlayerGradientTop
                } else fullColor

                val targetGlowColor = if (isDynamicCoverColorEnabled && dynamicColors != null) {
                    dynamicColors!!.fullPlayerSecondaryGlow
                } else fullColor

                val targetBottomColor = if (isDynamicCoverColorEnabled && dynamicColors != null) {
                    dynamicColors!!.fullPlayerGradientBottom
                } else fullColor

                val animatedMiniColor by androidx.compose.animation.animateColorAsState(
                    targetValue = targetMiniColor,
                    animationSpec = tween(durationMillis = 650, easing = FastOutSlowInEasing),
                    label = "miniPlayerColorAnim"
                )

                val animatedFullColor by androidx.compose.animation.animateColorAsState(
                    targetValue = targetFullColor,
                    animationSpec = tween(durationMillis = 650, easing = FastOutSlowInEasing),
                    label = "fullPlayerColorAnim"
                )

                val animatedGlowColor by androidx.compose.animation.animateColorAsState(
                    targetValue = targetGlowColor,
                    animationSpec = tween(durationMillis = 650, easing = FastOutSlowInEasing),
                    label = "fullPlayerGlowAnim"
                )

                val animatedBottomColor by androidx.compose.animation.animateColorAsState(
                    targetValue = targetBottomColor,
                    animationSpec = tween(durationMillis = 650, easing = FastOutSlowInEasing),
                    label = "fullPlayerBottomAnim"
                )

                Box(modifier = Modifier.fillMaxSize()) {
                    val fractionState = remember {
                        derivedStateOf {
                            val state = scaffoldState.bottomSheetState
                            val offset = try { state.requireOffset() } catch (e: Exception) { Float.NaN }
                            if (offset.isNaN()) {
                                if (state.targetValue == SheetValue.Expanded) 1f else 0f
                            } else {
                                val raw = (1f - (offset / maxTravel)).coerceIn(0f, 1f)
                                if (raw <= 0.03f) 0f else if (raw >= 0.98f) 1f else raw
                            }
                        }
                    }

                    BottomSheetScaffold(
                        scaffoldState = scaffoldState,
                        sheetSwipeEnabled = !isLyricsOpenState,
                        sheetPeekHeight = if (miniPlayerVisible && isGlobalUiVisible) staticPeekHeight else 0.dp,
                        sheetShape = androidx.compose.ui.graphics.RectangleShape,
                        sheetContainerColor = Color.Transparent,
                        sheetTonalElevation = 0.dp,
                        sheetShadowElevation = 0.dp,
                        sheetDragHandle = {},
                        containerColor = Color.Transparent,
                        content = {},
                        sheetContent = {
                            val fraction = fractionState.value
                            val sheetProgress = androidx.compose.animation.core.FastOutSlowInEasing.transform(fraction)

                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .graphicsLayer {
                                        val topRadius = 32.dp.toPx()
                                        val bottomRadius = 12.dp.toPx() + (20.dp.toPx() * sheetProgress)
                                        val hPad = 16.dp.toPx() * (1f - sheetProgress)

                                        val collapsedHeight = miniPlayerHeight.toPx()
                                        val currentHeight = collapsedHeight + (size.height - collapsedHeight) * sheetProgress

                                        shape = object : androidx.compose.ui.graphics.Shape {
                                            override fun createOutline(size: androidx.compose.ui.geometry.Size, layoutDirection: androidx.compose.ui.unit.LayoutDirection, density: androidx.compose.ui.unit.Density): androidx.compose.ui.graphics.Outline {
                                                return androidx.compose.ui.graphics.Outline.Rounded(
                                                    androidx.compose.ui.geometry.RoundRect(
                                                        left = hPad,
                                                        top = 0f,
                                                        right = size.width - hPad,
                                                        bottom = currentHeight,
                                                        topLeftCornerRadius = androidx.compose.ui.geometry.CornerRadius(topRadius),
                                                        topRightCornerRadius = androidx.compose.ui.geometry.CornerRadius(topRadius),
                                                        bottomLeftCornerRadius = androidx.compose.ui.geometry.CornerRadius(bottomRadius),
                                                        bottomRightCornerRadius = androidx.compose.ui.geometry.CornerRadius(bottomRadius)
                                                    )
                                                )
                                            }
                                        }
                                        clip = true
                                    }
                                    .drawBehind {
                                        drawRect(fullColor)
                                        if (isDynamicCoverColorEnabled) {
                                            val gradientBrush = Brush.verticalGradient(
                                                colors = listOf(
                                                    animatedFullColor,
                                                    animatedGlowColor,
                                                    animatedBottomColor
                                                )
                                            )
                                            drawRect(
                                                brush = gradientBrush,
                                                alpha = sheetProgress.coerceIn(0f, 1f)
                                            )
                                        }
                                        drawRect(animatedMiniColor.copy(alpha = (1f - sheetProgress).coerceIn(0f, 1f)))
                                    }
                            ) {
                                if (miniPlayerVisible) {
                                    MorphingPlayerComposeBlock(
                                        fraction = fraction,
                                        auraColor = animatedMiniColor,
                                        dynamicArtworkColors = if (isDynamicCoverColorEnabled) dynamicColors else null,
                                        onLyricsStateChange = { isLyricsOpenState = it },
                                        onClose = { coroutineScope.launch { scaffoldState.bottomSheetState.partialExpand() } },
                                        onExpand = { coroutineScope.launch { scaffoldState.bottomSheetState.expand() } }
                                    )
                                }
                            }
                        }
                    )

                    androidx.compose.animation.AnimatedVisibility(
                        visible = isGlobalUiVisible,
                        modifier = Modifier.align(Alignment.BottomCenter),
                        enter = androidx.compose.animation.slideInVertically(initialOffsetY = { it }),
                        exit = androidx.compose.animation.slideOutVertically(targetOffsetY = { it })
                    ) {
                        BottomNavComposeBlock(
                            modifier = Modifier
                                .padding(bottom = navBarBottom + navBarOffset)
                                .padding(horizontal = 16.dp)
                                .graphicsLayer {
                                    val fraction = fractionState.value
                                    alpha = (1f - fraction * 2.2f).coerceIn(0f, 1f)
                                    translationY = fraction * 200f
                                }
                        )
                    }
                }
            }
        }

        controllerViewModel.addControllerCallback(lifecycle) { controller, _ -> setupMiniPlayer(controller) }
        if (!hasAudioPermission()) {
            ActivityCompat.requestPermissions(this,
                if (hasScopedStorageWithMediaTypes()) arrayOf(android.Manifest.permission.READ_MEDIA_AUDIO)
                else if (hasScopedStorageV2()) arrayOf(android.Manifest.permission.READ_EXTERNAL_STORAGE)
                else arrayOf(android.Manifest.permission.READ_EXTERNAL_STORAGE, android.Manifest.permission.WRITE_EXTERNAL_STORAGE),
                PERMISSION_READ_MEDIA_AUDIO,
            )
        } else {
            if (!this@MainActivity.reader.hadFirstRefresh) updateLibrary() else onLibraryLoaded()
        }
        if (supportFragmentManager.findFragmentById(R.id.container) !is ViewPagerFragment) handler.post { maybeReportFullyDrawn() }

        syncLikedTracksCache()
        handleDeepLink(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (ready) doPlayFromIntent(intent)
        handleDeepLink(intent)
    }

    private fun handleDeepLink(intent: Intent?) {
        val action = intent?.action
        val data = intent?.data

        if (Intent.ACTION_VIEW == action && data != null) {
            if (data.path?.startsWith("/playlist/") == true) {
                val playlistId = data.lastPathSegment?.toIntOrNull()
                if (playlistId != null) {
                    openPlaylistFromLink(playlistId)
                }
            }
        }
    }

    private fun openPlaylistFromLink(id: Int) {
        Toast.makeText(this, "Загрузка плейлиста...", Toast.LENGTH_SHORT).show()

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val response = org.akanework.gramophone.logic.api.NetworkClient.getApi(this@MainActivity).getPlaylist(id).execute()

                withContext(Dispatchers.Main) {
                    if (response.isSuccessful && response.body() != null) {
                        val fragment = ComposeContainerFragment.newInstance(response.body()!!)
                        startFragment(fragment)
                    } else {
                        Toast.makeText(this@MainActivity, "Плейлист недоступен или удален", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Ошибка сети при открытии ссылки", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    @Composable
    private fun BottomNavComposeBlock(modifier: Modifier = Modifier) {
        val isDarkTheme = isSystemInDarkTheme()
        val haptic = LocalHapticFeedback.current // 🔥 Haptic

        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(80.dp)
                .graphicsLayer {
                    shadowElevation = 24.dp.toPx()
                    shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 32.dp, bottomEnd = 32.dp)
                    clip = true
                }
                .background(if (isDarkTheme) Color(0xFF1E1E1E) else Color(0xFFF0F0F0))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                AppTab.values().forEach { tab ->
                    val isSelected = currentBottomTab == tab
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress) // 🔥 Вибрация
                                switchTab(tab)
                            }
                            .padding(vertical = 4.dp)
                            .width(76.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .width(56.dp)
                                .height(30.dp)
                                .background(
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                    shape = RoundedCornerShape(16.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painterResource(id = tab.iconRes),
                                contentDescription = tab.title,
                                tint = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = tab.title,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            ),
                            color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }
    }



    @Composable
    fun AnimatedPlayingIndicator(isPlaying: Boolean) {
        val shouldAnimate = isPlaying && isForeground
        val infiniteTransition = androidx.compose.animation.core.rememberInfiniteTransition(label = "eq_transition")

        val bar1 by infiniteTransition.animateFloat(
            initialValue = 0.3f, targetValue = 1f,
            animationSpec = androidx.compose.animation.core.infiniteRepeatable(
                androidx.compose.animation.core.tween(400, easing = androidx.compose.animation.core.LinearEasing),
                androidx.compose.animation.core.RepeatMode.Reverse
            ),
            label = "bar1"
        )
        val bar2 by infiniteTransition.animateFloat(
            initialValue = 0.5f, targetValue = 1f,
            animationSpec = androidx.compose.animation.core.infiniteRepeatable(
                androidx.compose.animation.core.tween(300, easing = androidx.compose.animation.core.LinearEasing),
                androidx.compose.animation.core.RepeatMode.Reverse
            ),
            label = "bar2"
        )
        val bar3 by infiniteTransition.animateFloat(
            initialValue = 0.4f, targetValue = 1f,
            animationSpec = androidx.compose.animation.core.infiniteRepeatable(
                androidx.compose.animation.core.tween(500, easing = androidx.compose.animation.core.LinearEasing),
                androidx.compose.animation.core.RepeatMode.Reverse
            ),
            label = "bar3"
        )

        val h1 = if (shouldAnimate) bar1 else 0.3f
        val h2 = if (shouldAnimate) bar2 else 0.5f
        val h3 = if (shouldAnimate) bar3 else 0.4f

        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            modifier = Modifier.height(16.dp)
        ) {
            Box(modifier = Modifier.width(3.dp).fillMaxHeight(h1).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
            Box(modifier = Modifier.width(3.dp).fillMaxHeight(h2).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
            Box(modifier = Modifier.width(3.dp).fillMaxHeight(h3).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
        }
    }

    @Composable
    private fun MorphingPlayerComposeBlock(
        fraction: Float,
        auraColor: Color?,
        dynamicArtworkColors: org.akanework.gramophone.ui.theme.DynamicArtworkTheme.ArtworkColors? = null,
        onLyricsStateChange: (Boolean) -> Unit,
        onClose: () -> Unit,
        onExpand: () -> Unit
    ) {
        val coroutineScope = rememberCoroutineScope()
        val haptic = LocalHapticFeedback.current

        var showLyricsScreen by remember { mutableStateOf(false) }
        LaunchedEffect(showLyricsScreen) {
            onLyricsStateChange(showLyricsScreen)
        }

        var currentLyricsResult by remember { mutableStateOf<org.akanework.gramophone.logic.utils.LyricsResult?>(null) }
        var isLyricsLoading by remember { mutableStateOf(false) }
        var selectedLyricsSource by remember { mutableStateOf(org.akanework.gramophone.logic.utils.LyricsSource.ALL) }

        fun loadLyrics(source: org.akanework.gramophone.logic.utils.LyricsSource = selectedLyricsSource) {
            val player = getPlayer() ?: return
            val mediaItem = player.currentMediaItem ?: return
            isLyricsLoading = true
            val title = mediaItem.mediaMetadata.title?.toString()
            val artist = mediaItem.mediaMetadata.artist?.toString()
            val durationMs = mediaItem.mediaMetadata.durationMs ?: 0L

            val options = org.akanework.gramophone.logic.utils.LrcUtils.LrcParserOptions(
                trim = true, multiLine = false,
                errorText = "Не удалось распарсить текст"
            )

            CoroutineScope(Dispatchers.Main).launch {
                val res = org.akanework.gramophone.logic.utils.LyricsRepository.fetchLyrics(
                    context = this@MainActivity,
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
                currentLyricsResult = res
                isLyricsLoading = false
            }
        }

        LaunchedEffect(trackTitle) {
            currentLyricsResult = null
            if (showLyricsScreen) {
                loadLyrics()
            }
        }

        val dragOffset = remember { androidx.compose.animation.core.Animatable(0f) }
        var isDragging by remember { mutableStateOf(false) }

        var visuallyPlaying by remember { mutableStateOf(isPlaying) }
        LaunchedEffect(isPlaying) {
            if (isPlaying) {
                visuallyPlaying = true
            } else {
                kotlinx.coroutines.delay(200)
                visuallyPlaying = isPlaying
            }
        }

        val finalCoverScale by animateFloatAsState(
            targetValue = if (visuallyPlaying) 1f else 0.85f,
            animationSpec = spring(dampingRatio = 0.6f, stiffness = 300f),
            label = "playScale"
        )

        LaunchedEffect(coverUrl) {
            dragOffset.snapTo(0f)
        }

        var nextTrackName by remember { mutableStateOf("") }
        var prevCoverUrl by remember { mutableStateOf("") }
        var nextCoverUrl by remember { mutableStateOf("") }

        LaunchedEffect(trackTitle, isShuffle, isLoop) {
            val player = getPlayer() ?: return@LaunchedEffect

            fun getCoverUrl(index: Int): String {
                if (index == androidx.media3.common.C.INDEX_UNSET || index < 0 || index >= player.mediaItemCount) return ""
                val uri = player.getMediaItemAt(index).mediaMetadata.artworkUri?.toString() ?: ""
                return if (uri.startsWith("/")) "http://185.196.41.31$uri" else uri
            }

            val nextIdx = player.nextMediaItemIndex
            nextCoverUrl = getCoverUrl(nextIdx)
            if (nextIdx != androidx.media3.common.C.INDEX_UNSET) {
                val nextItem = player.getMediaItemAt(nextIdx)
                val title = nextItem.mediaMetadata.title?.toString()
                val artist = nextItem.mediaMetadata.artist?.toString()
                nextTrackName = if (!artist.isNullOrEmpty() && !title.isNullOrEmpty()) "$artist — $title" else title ?: "Неизвестный трек"
            } else {
                nextTrackName = ""
            }

            val prevIdx = player.previousMediaItemIndex
            prevCoverUrl = getCoverUrl(prevIdx)
        }

        val showNextTrack by remember(currentPosition, trackDuration) {
            derivedStateOf {
                val timeLeft = trackDuration - currentPosition
                timeLeft in 0f..15000f && trackDuration > 20000f
            }
        }

        val context = androidx.compose.ui.platform.LocalContext.current
        val currentImageRequest = remember(coverUrl) {
            coil3.request.ImageRequest.Builder(context)
                .data(coverUrl.ifEmpty { R.drawable.ic_library })
                .size(coil3.size.Size.ORIGINAL)
                .build()
        }

        val prevImageRequest = remember(prevCoverUrl) {
            if (prevCoverUrl.isNotEmpty()) {
                coil3.request.ImageRequest.Builder(context)
                    .data(prevCoverUrl)
                    .size(coil3.size.Size.ORIGINAL)
                    .build()
            } else null
        }

        val nextImageRequest = remember(nextCoverUrl) {
            if (nextCoverUrl.isNotEmpty()) {
                coil3.request.ImageRequest.Builder(context)
                    .data(nextCoverUrl)
                    .size(coil3.size.Size.ORIGINAL)
                    .build()
            } else null
        }

        val configuration = LocalConfiguration.current
        val density = LocalDensity.current
        val screenWidthDp = configuration.screenWidthDp.dp
        val screenHeightDp = configuration.screenHeightDp.dp

        val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

        // 1. Full Player Geometry (Anchored to screen bottom & top)
        val fullHeaderTop = statusBarTop + 8.dp
        val fullHeaderHeight = 48.dp
        val fullHeaderBottom = fullHeaderTop + fullHeaderHeight

        val fullBottomPadding = maxOf(16.dp, navBarBottom + 12.dp)

        val bottomActionsHeight = 56.dp
        val actionsToControlsGap = 16.dp
        val mainControlsHeight = 76.dp
        val controlsToTimeGap = 12.dp
        val timeHeight = 16.dp
        val sliderHeight = 38.dp
        val symmetricGap = 18.dp
        val textBlockHeight = 58.dp

        val totalBottomStackHeight = bottomActionsHeight + actionsToControlsGap + mainControlsHeight + controlsToTimeGap + timeHeight + sliderHeight + symmetricGap + textBlockHeight

        val fullTextY = screenHeightDp - fullBottomPadding - totalBottomStackHeight
        val fullControlsY = fullTextY + textBlockHeight + symmetricGap

        val availableCoverHeight = fullTextY - fullHeaderBottom - symmetricGap
        val maxFullCoverSize = minOf(screenWidthDp - 44.dp, maxOf(200.dp, availableCoverHeight))
        val targetFullCoverSize = maxFullCoverSize * finalCoverScale
        val targetFullCoverX = (screenWidthDp - targetFullCoverSize) / 2
        val targetFullCoverY = (fullHeaderBottom + (availableCoverHeight - targetFullCoverSize) / 2).coerceAtLeast(fullHeaderBottom + 4.dp)
        val fullCoverRadius = 28.dp

        val fullTextX = 24.dp
        val fullTextWidth = screenWidthDp - 48.dp

        // 2. Mini Player Geometry
        val miniHPad = 16.dp
        val miniCoverSize = 48.dp
        val miniCoverX = miniHPad + 12.dp // 28.dp
        val miniCoverY = 12.dp

        val miniTextX = miniCoverX + miniCoverSize + 12.dp // 88.dp
        val miniTextY = 15.dp
        val miniTextWidth = maxOf(0.dp, screenWidthDp - miniTextX - 116.dp)

        // 3. Expressive Motion Curves (Душа анимации)
        val emphasizedDecelerate = remember { androidx.compose.animation.core.CubicBezierEasing(0.05f, 0.7f, 0.1f, 1.0f) }
        val emphasizedSpring = remember { androidx.compose.animation.core.CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f) }

        val coverProgress = emphasizedDecelerate.transform(fraction)
        val textProgress = emphasizedSpring.transform(((fraction - 0.03f) / 0.97f).coerceIn(0f, 1f))
        val controlsProgress = androidx.compose.animation.core.FastOutSlowInEasing.transform(((fraction - 0.12f) / 0.88f).coerceIn(0f, 1f))

        // Current interpolated values:
        val curCoverSize = miniCoverSize + (targetFullCoverSize - miniCoverSize) * coverProgress
        val curCoverX = miniCoverX + (targetFullCoverX - miniCoverX) * coverProgress
        val curCoverY = miniCoverY + (targetFullCoverY - miniCoverY) * coverProgress

        val circleRadius = curCoverSize / 2f
        val curCoverRadius = androidx.compose.ui.unit.lerp(circleRadius, fullCoverRadius, coverProgress)

        val curTextX = miniTextX + (fullTextX - miniTextX) * textProgress
        val curTextY = miniTextY + (fullTextY - miniTextY) * textProgress
        val curTextWidth = miniTextWidth + (fullTextWidth - miniTextWidth) * textProgress

        val shouldAnimateAura = isPlaying && isForeground
        val infiniteTransition = rememberInfiniteTransition(label = "aura")

        val rawPulse by infiniteTransition.animateFloat(
            initialValue = 1f,
            targetValue = 1.2f,
            animationSpec = infiniteRepeatable(tween(3000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "pulse"
        )

        val rawRotation by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(tween(10000, easing = LinearEasing)),
            label = "rotation"
        )

        val auraPulse = if (shouldAnimateAura) rawPulse else 1f
        val auraRotation = if (shouldAnimateAura) rawRotation else 0f

        val widthPx = with(density) { (screenWidthDp - 32.dp).toPx() }
        val spacingPx = with(density) { 32.dp.toPx() }
        val totalOffsetPx = widthPx + spacingPx

        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (fraction < 0.2f) {
                        Modifier.clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onExpand()
                            }
                        )
                    } else Modifier
                )
        ) {
            // 1. FULL HEADER (Close, Playing From Source, Menu)
            if (fraction > 0.1f) {
                val playingFrom = remember(trackTitle, trackArtist) {
                    val meta = getPlayer()?.currentMediaItem?.mediaMetadata
                    val fromExtra = meta?.extras?.getString("PLAYING_FROM")
                    when {
                        !fromExtra.isNullOrBlank() -> fromExtra
                        !meta?.albumTitle.isNullOrBlank() -> "Альбом: ${meta.albumTitle}"
                        else -> "Медиатека"
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = fullHeaderTop, start = 16.dp, end = 16.dp)
                        .graphicsLayer {
                            alpha = ((fraction - 0.4f) / 0.6f).coerceIn(0f, 1f)
                            translationY = -30f * (1f - coverProgress)
                        },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilledTonalIconButton(
                        onClick = onClose,
                        shape = CircleShape,
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.7f),
                            contentColor = MaterialTheme.colorScheme.onSurface
                        ),
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(Icons.Rounded.KeyboardArrowDown, "Close", modifier = Modifier.size(28.dp))
                    }
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp)
                    ) {
                        Text(
                            "СЕЙЧАС ИГРАЕТ",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.4.sp,
                                fontSize = 10.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.6f),
                            modifier = Modifier
                                .clip(CircleShape)
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    org.akanework.gramophone.ui.fragments.QueueBottomSheetFragment().show(this@MainActivity.supportFragmentManager, "QUEUE_SHEET")
                                }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_library),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = playingFrom,
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 11.5.sp
                                    ),
                                    color = MaterialTheme.colorScheme.primary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                    FilledTonalIconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            org.akanework.gramophone.ui.fragments.PlayerMenuBottomSheet().show(this@MainActivity.supportFragmentManager, "PLAYER_MENU_SHEET")
                        },
                        shape = CircleShape,
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.7f),
                            contentColor = MaterialTheme.colorScheme.onSurface
                        ),
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(Icons.Rounded.MoreVert, "Menu", modifier = Modifier.size(22.dp))
                    }
                }
            }

            // 2. MINI CONTROLS (Play/Pause, Next)
            if (fraction < 0.6f) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 14.dp, end = 28.dp)
                        .graphicsLayer {
                            alpha = (1f - fraction * 2.8f).coerceIn(0f, 1f)
                            translationX = fraction * 30f
                        }
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer)
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                getPlayer()?.let { if (it.isPlaying) it.pause() else it.play() }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        val isLoading = (org.akanework.gramophone.logic.utils.SmartPlaybackManager.isResolving || getPlayer()?.playbackState == androidx.media3.common.Player.STATE_BUFFERING) && !isPlaying
                        if (isLoading) {
                            androidx.compose.material3.CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                strokeWidth = 2.5.dp
                            )
                        } else {
                            Icon(
                                painterResource(id = if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play),
                                contentDescription = "Play/Pause",
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            getPlayer()?.seekToNext()
                        },
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(
                            painterResource(id = R.drawable.ic_skip_next),
                            contentDescription = "Next",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }

            // 3. SHARED COVER ARTWORK (Continuous Morphing size, position, radius)
            Box(
                modifier = Modifier
                    .offset(x = curCoverX, y = curCoverY)
                    .size(curCoverSize)
                    .then(
                        if (fraction > 0.85f) {
                            Modifier.pointerInput(Unit) {
                                detectHorizontalDragGestures(
                                    onDragStart = { isDragging = true },
                                    onDragEnd = {
                                        isDragging = false
                                        val threshold = widthPx * 0.25f
                                        coroutineScope.launch {
                                            if (dragOffset.value > threshold && prevCoverUrl.isNotEmpty()) {
                                                dragOffset.animateTo(totalOffsetPx, tween(250, easing = androidx.compose.animation.core.FastOutSlowInEasing))
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                getPlayer()?.seekToPrevious()
                                            } else if (dragOffset.value < -threshold && nextCoverUrl.isNotEmpty()) {
                                                dragOffset.animateTo(-totalOffsetPx, tween(250, easing = androidx.compose.animation.core.FastOutSlowInEasing))
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                getPlayer()?.seekToNext()
                                            } else {
                                                dragOffset.animateTo(0f, spring(dampingRatio = 0.65f, stiffness = 400f))
                                            }
                                        }
                                    },
                                    onDragCancel = {
                                        isDragging = false
                                        coroutineScope.launch { dragOffset.animateTo(0f, spring(dampingRatio = 0.65f, stiffness = 400f)) }
                                    }
                                ) { change, dragAmount ->
                                    change.consume()
                                    coroutineScope.launch { dragOffset.snapTo(dragOffset.value + dragAmount) }
                                }
                            }
                        } else Modifier
                    ),
                contentAlignment = Alignment.Center
            ) {
                // Aura (visible in mini state)
                if (auraColor != null && fraction < 0.5f) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .scale(auraPulse)
                            .graphicsLayer {
                                rotationZ = auraRotation
                                alpha = (1f - fraction * 2f).coerceIn(0f, 1f)
                            }
                            .drawBehind {
                                val brush = Brush.radialGradient(
                                    colors = listOf(
                                        auraColor.copy(alpha = 0.25f),
                                        auraColor.copy(alpha = 0.08f),
                                        Color.Transparent
                                    ),
                                    center = Offset(size.width * 0.5f, size.height * 0.5f),
                                    radius = size.width / 1.8f
                                )
                                drawCircle(brush)
                            }
                    )
                }

                // Full Player Ambient Album Glow (PixelPlayer style)
                if (auraColor != null && fraction > 0.6f && isPlaying) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .scale(1.18f)
                            .graphicsLayer {
                                alpha = ((fraction - 0.6f) / 0.4f) * 0.42f
                            }
                            .drawBehind {
                                val brush = Brush.radialGradient(
                                    colors = listOf(
                                        auraColor.copy(alpha = 0.35f),
                                        auraColor.copy(alpha = 0.10f),
                                        Color.Transparent
                                    ),
                                    center = Offset(size.width * 0.5f, size.height * 0.5f),
                                    radius = size.width / 1.5f
                                )
                                drawCircle(brush)
                            }
                    )
                }

                val leftProgress = (-dragOffset.value / widthPx).coerceIn(0f, 1f)
                val rightProgress = (dragOffset.value / widthPx).coerceIn(0f, 1f)
                val maxProgress = maxOf(leftProgress, rightProgress)

                val dragScaleFactor = if (fraction > 0.85f) (1f - (maxProgress * 0.15f)) else 1f
                val dragAlphaFactor = if (fraction > 0.85f) (1f - (maxProgress * 0.5f)) else 1f

                if (fraction > 0.85f && prevCoverUrl.isNotEmpty() && prevImageRequest != null) {
                    val prevScale = 0.85f + (rightProgress * 0.15f)
                    val prevAlpha = 0.5f + (rightProgress * 0.5f)
                    AsyncImage(
                        model = prevImageRequest,
                        contentDescription = "Prev Cover",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                translationX = dragOffset.value - totalOffsetPx
                                scaleX = prevScale
                                scaleY = prevScale
                                alpha = prevAlpha
                                shape = RoundedCornerShape(curCoverRadius)
                                clip = true
                            }
                    )
                }

                AsyncImage(
                    model = currentImageRequest,
                    contentDescription = "Cover",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            translationX = if (fraction > 0.85f) dragOffset.value else 0f
                            scaleX = dragScaleFactor
                            scaleY = dragScaleFactor
                            alpha = dragAlphaFactor
                            shadowElevation = if (visuallyPlaying && fraction > 0.8f) (20.dp.toPx() * fraction) else 0f
                            shape = RoundedCornerShape(curCoverRadius)
                            clip = true
                        }
                )

                if (fraction > 0.85f && nextCoverUrl.isNotEmpty() && nextImageRequest != null) {
                    val nextScale = 0.85f + (leftProgress * 0.15f)
                    val nextAlpha = 0.5f + (leftProgress * 0.5f)
                    AsyncImage(
                        model = nextImageRequest,
                        contentDescription = "Next Cover",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                translationX = dragOffset.value + totalOffsetPx
                                scaleX = nextScale
                                scaleY = nextScale
                                alpha = nextAlpha
                                shape = RoundedCornerShape(curCoverRadius)
                                clip = true
                            }
                    )
                }
            }

            // 4. SHARED TRACK TITLE & ARTIST (Continuous morphing position, typography, width)
            Column(
                modifier = Modifier
                    .offset(x = curTextX, y = curTextY)
                    .width(curTextWidth)
            ) {
                val titleFontSize = androidx.compose.ui.unit.lerp(15.sp, 24.sp, textProgress)
                val titleLineHeight = androidx.compose.ui.unit.lerp(20.sp, 28.sp, textProgress)
                val artistFontSize = androidx.compose.ui.unit.lerp(13.sp, 16.sp, textProgress)
                val artistLineHeight = androidx.compose.ui.unit.lerp(16.sp, 22.sp, textProgress)
                val textSpacing = androidx.compose.ui.unit.lerp(2.dp, 6.dp, textProgress)

                Text(
                    text = trackTitle.ifEmpty { "Загрузка..." },
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontSize = titleFontSize,
                        lineHeight = titleLineHeight,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = (-0.3).sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    modifier = Modifier.basicMarquee(),
                    textAlign = TextAlign.Start
                )
                Spacer(modifier = Modifier.height(textSpacing))
                Text(
                    text = trackArtist.ifEmpty { "Ожидание" },
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = artistFontSize,
                        lineHeight = artistLineHeight,
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = if (fraction > 0.5f) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Start,
                    modifier = Modifier.clickable {
                        val artistId = getPlayer()?.currentMediaItem?.mediaMetadata?.extras?.getString("ARTIST_ID")
                        if (!artistId.isNullOrBlank()) {
                            startFragment(org.akanework.gramophone.ui.fragments.ArtistFragment.newInstance(artistId))
                            collapsePlayer()
                        }
                    }
                )
            }

            // 5. FULL PLAYER CONTROLS (Slider, Play/Pause, Shuffle, Loop, Like, Lyrics)
            if (fraction > 0.12f) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .offset(y = fullControlsY)
                        .padding(horizontal = 16.dp)
                        .graphicsLayer {
                            alpha = ((fraction - 0.25f) / 0.75f).coerceIn(0f, 1f)
                            translationY = 60f * (1f - controlsProgress)
                        }
                ) {
                    // Интерактивный баннер следующего трека (Next Track Pill с динамическими цветами)
                    AnimatedVisibility(
                        visible = showNextTrack && nextTrackName.isNotEmpty(),
                        enter = fadeIn(tween(350)) + slideInVertically(tween(350)) { it / 2 } + androidx.compose.animation.scaleIn(tween(350), initialScale = 0.92f),
                        exit = fadeOut(tween(250)) + slideOutVertically(tween(250)) { it / 2 } + androidx.compose.animation.scaleOut(tween(250), targetScale = 0.92f)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = dynamicArtworkColors?.playerContainer ?: MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                .clip(CircleShape)
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    getPlayer()?.seekToNext()
                                }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.SkipNext,
                                    contentDescription = null,
                                    tint = dynamicArtworkColors?.accentColor ?: MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    "Далее: ",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 12.sp),
                                    color = dynamicArtworkColors?.accentColor ?: MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = nextTrackName,
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold, fontSize = 12.sp),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = dynamicArtworkColors?.playerOnContainer ?: MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }

                    Box(modifier = Modifier.fillMaxWidth().height(sliderHeight).padding(horizontal = 8.dp)) {
                        SquigglySlider(
                            position = currentPosition,
                            duration = trackDuration,
                            isPlaying = isPlaying,
                            activeColor = dynamicArtworkColors?.accentColor ?: MaterialTheme.colorScheme.primary,
                            onValueChange = { newValue ->
                                currentPosition = newValue
                                progressHandler.removeCallbacks(progressRunnable)
                            },
                            onValueChangeFinished = {
                                getPlayer()?.seekTo(currentPosition.toLong())
                                if (isPlaying) progressHandler.post(progressRunnable)
                            }
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            formatTime(currentPosition.toLong()),
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, fontSize = 12.5.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            formatTime(trackDuration.toLong()),
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, fontSize = 12.5.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.height(controlsToTimeGap))

                    // Экспрессивные соединенные кнопки Prev / Play-Pause / Next (PixelPlayer Animated Controls)
                    var pressedAction by remember { mutableStateOf<String?>(null) }
                    LaunchedEffect(pressedAction) {
                        if (pressedAction != null) {
                            kotlinx.coroutines.delay(200)
                            pressedAction = null
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(76.dp)
                            .padding(horizontal = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val prevWeight by animateFloatAsState(
                            targetValue = if (pressedAction == "PREV") 1.25f else 1f,
                            animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                            label = "prevWeight"
                        )
                        Surface(
                            shape = RoundedCornerShape(32.dp),
                            color = dynamicArtworkColors?.playerContainer ?: MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.75f),
                            modifier = Modifier
                                .weight(prevWeight)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(32.dp))
                                .clickable {
                                    pressedAction = "PREV"
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    getPlayer()?.seekToPrevious()
                                }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Rounded.SkipPrevious,
                                    "Prev",
                                    modifier = Modifier.size(32.dp),
                                    tint = dynamicArtworkColors?.playerOnContainer ?: MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }

                        // Экспрессивная кнопка Play/Pause (Морфинг круга в суперэллипс)
                        val playWeight by animateFloatAsState(
                            targetValue = if (pressedAction == "PLAY") 1.8f else 1.55f,
                            animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                            label = "playWeight"
                        )
                        Surface(
                            shape = RoundedCornerShape(32.dp),
                            color = dynamicArtworkColors?.accentColor ?: MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .weight(playWeight)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(32.dp))
                                .clickable {
                                    pressedAction = "PLAY"
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    getPlayer()?.let { if (it.isPlaying) it.pause() else it.play() }
                                }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                val isLoading = (org.akanework.gramophone.logic.utils.SmartPlaybackManager.isResolving || getPlayer()?.playbackState == androidx.media3.common.Player.STATE_BUFFERING) && !isPlaying
                                if (isLoading) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(34.dp),
                                        color = dynamicArtworkColors?.playerOnPrimary ?: MaterialTheme.colorScheme.onPrimary,
                                        strokeWidth = 3.5.dp
                                    )
                                } else {
                                    Icon(
                                        painterResource(id = if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play),
                                        contentDescription = "Play/Pause",
                                        tint = dynamicArtworkColors?.playerOnPrimary ?: MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.size(38.dp)
                                    )
                                }
                            }
                        }

                        val nextWeight by animateFloatAsState(
                            targetValue = if (pressedAction == "NEXT") 1.25f else 1f,
                            animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                            label = "nextWeight"
                        )
                        Surface(
                            shape = RoundedCornerShape(32.dp),
                            color = dynamicArtworkColors?.playerContainer ?: MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.75f),
                            modifier = Modifier
                                .weight(nextWeight)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(32.dp))
                                .clickable {
                                    pressedAction = "NEXT"
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    getPlayer()?.seekToNext()
                                }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Rounded.SkipNext,
                                    "Next",
                                    modifier = Modifier.size(32.dp),
                                    tint = dynamicArtworkColors?.playerOnContainer ?: MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(actionsToControlsGap))

                    // Нижняя сегментированная капсула 4 переключателей (PixelPlayer BottomToggleRow Style)
                    Surface(
                        shape = CircleShape,
                        color = dynamicArtworkColors?.playerContainer?.copy(alpha = 0.65f) ?: MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.65f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .padding(horizontal = 6.dp)
                            .clip(CircleShape)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize().padding(4.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // 1. Shuffle
                            SegmentedCapsuleBtn(
                                icon = Icons.Rounded.Shuffle,
                                isActive = isShuffle,
                                activeBg = dynamicArtworkColors?.playerActiveContainer ?: MaterialTheme.colorScheme.primaryContainer,
                                activeFg = dynamicArtworkColors?.playerOnActiveContainer ?: MaterialTheme.colorScheme.onPrimaryContainer,
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    val p = getPlayer() ?: return@SegmentedCapsuleBtn
                                    val newState = !p.shuffleModeEnabled
                                    p.shuffleModeEnabled = newState
                                    if (newState) applyPhysicalShuffle(p)
                                },
                                modifier = Modifier.weight(1f)
                            )

                            // 2. Loop
                            val currentRepeatMode = getPlayer()?.repeatMode ?: androidx.media3.common.Player.REPEAT_MODE_OFF
                            SegmentedCapsuleBtn(
                                icon = if (currentRepeatMode == androidx.media3.common.Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                                isActive = isLoop,
                                activeBg = dynamicArtworkColors?.playerActiveContainer ?: MaterialTheme.colorScheme.secondaryContainer,
                                activeFg = dynamicArtworkColors?.playerOnActiveContainer ?: MaterialTheme.colorScheme.onSecondaryContainer,
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    val p = getPlayer() ?: return@SegmentedCapsuleBtn
                                    val newMode = when (p.repeatMode) {
                                        androidx.media3.common.Player.REPEAT_MODE_OFF -> androidx.media3.common.Player.REPEAT_MODE_ALL
                                        androidx.media3.common.Player.REPEAT_MODE_ALL -> androidx.media3.common.Player.REPEAT_MODE_ONE
                                        else -> androidx.media3.common.Player.REPEAT_MODE_OFF
                                    }
                                    p.repeatMode = newMode
                                },
                                modifier = Modifier.weight(1f)
                            )

                            // 3. Favorite
                            SegmentedCapsuleBtn(
                                icon = if (isLiked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                                isActive = isLiked,
                                activeBg = dynamicArtworkColors?.playerActiveContainer ?: MaterialTheme.colorScheme.tertiaryContainer,
                                activeFg = dynamicArtworkColors?.playerOnActiveContainer ?: MaterialTheme.colorScheme.onTertiaryContainer,
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    val currentTrackId = getPlayer()?.currentMediaItem?.mediaId ?: return@SegmentedCapsuleBtn
                                    if (currentTrackId.isEmpty()) return@SegmentedCapsuleBtn
                                    val newLikeState = !isLiked
                                    isLiked = newLikeState
                                    if (newLikeState) LikeCache.add(currentTrackId, title = trackTitle, artist = trackArtist) else LikeCache.remove(currentTrackId, title = trackTitle, artist = trackArtist)
                                    CoroutineScope(Dispatchers.IO).launch {
                                        try {
                                            val api = org.akanework.gramophone.logic.api.NetworkClient.getApi(this@MainActivity)
                                            if (newLikeState) api.likeTrack(currentTrackId).execute() else api.unlikeTrack(currentTrackId).execute()
                                        } catch (_: Exception) {}
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            )

                            // 4. Lyrics
                            SegmentedCapsuleBtn(
                                icon = Icons.Rounded.Lyrics,
                                isActive = showLyricsScreen,
                                activeBg = dynamicArtworkColors?.playerActiveContainer ?: MaterialTheme.colorScheme.primaryContainer,
                                activeFg = dynamicArtworkColors?.playerOnActiveContainer ?: MaterialTheme.colorScheme.onPrimaryContainer,
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    val targetState = !showLyricsScreen
                                    showLyricsScreen = targetState
                                    if (targetState && currentLyricsResult == null && !isLyricsLoading) {
                                        loadLyrics()
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            // 6. LYRICS FULLSCREEN OVERLAY
            androidx.compose.animation.AnimatedVisibility(
                visible = showLyricsScreen,
                enter = androidx.compose.animation.fadeIn(tween(320, easing = androidx.compose.animation.core.FastOutSlowInEasing)) +
                        androidx.compose.animation.slideInVertically(
                            animationSpec = spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow),
                            initialOffsetY = { it }
                        ) +
                        androidx.compose.animation.scaleIn(
                            animationSpec = spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow),
                            initialScale = 0.92f
                        ),
                exit = androidx.compose.animation.fadeOut(tween(240, easing = androidx.compose.animation.core.FastOutLinearInEasing)) +
                       androidx.compose.animation.slideOutVertically(
                           animationSpec = tween(240, easing = androidx.compose.animation.core.FastOutLinearInEasing),
                           targetOffsetY = { it }
                       ) +
                       androidx.compose.animation.scaleOut(
                           animationSpec = tween(240, easing = androidx.compose.animation.core.FastOutLinearInEasing),
                           targetScale = 0.92f
                       )
            ) {
                org.akanework.gramophone.ui.components.LyricsScreen(
                    trackTitle = trackTitle,
                    artistName = trackArtist,
                    coverUrl = coverUrl,
                    isPlaying = isPlaying,
                    lyricsResult = currentLyricsResult,
                    isLoading = isLyricsLoading,
                    currentPositionMs = currentPosition.toLong(),
                    selectedSource = selectedLyricsSource,
                    dynamicArtworkColors = dynamicArtworkColors,
                    onSourceSelected = { newSource ->
                        selectedLyricsSource = newSource
                        loadLyrics(newSource)
                    },
                    onPlayPauseToggle = {
                        getPlayer()?.let { if (it.isPlaying) it.pause() else it.play() }
                    },
                    onSkipNext = {
                        getPlayer()?.seekToNext()
                    },
                    onSeekTo = { pos ->
                        getPlayer()?.seekTo(pos)
                    },
                    onDismiss = {
                        showLyricsScreen = false
                    }
                )
            }
        }
    }

    @Composable
    private fun SegmentedCapsuleBtn(
        icon: androidx.compose.ui.graphics.vector.ImageVector,
        isActive: Boolean,
        activeBg: Color,
        activeFg: Color,
        onClick: () -> Unit,
        modifier: Modifier = Modifier
    ) {
        val containerColor by animateColorAsState(
            targetValue = if (isActive) activeBg else Color.Transparent,
            animationSpec = tween(200),
            label = "segContainer"
        )
        val contentColor by animateColorAsState(
            targetValue = if (isActive) activeFg else MaterialTheme.colorScheme.onSurfaceVariant,
            animationSpec = tween(200),
            label = "segContent"
        )

        Surface(
            shape = CircleShape,
            color = containerColor,
            modifier = modifier
                .fillMaxHeight()
                .clip(CircleShape)
                .clickable(onClick = onClick)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }

    private fun formatTime(ms: Long): String {
        if (ms < 0) return "0:00"
        val seconds = (ms / 1000) % 60
        val minutes = (ms / 1000) / 60
        return String.format("%d:%02d", minutes, seconds)
    }

    // 🔥 ИЗМЕНЕНА ЛОГИКА ПЕРЕКЛЮЧЕНИЯ
    fun switchTab(tab: AppTab) {
        currentBottomTab = tab

        // Перелистываем ViewPager
        if (mainPager.currentItem != tab.ordinal) {
            mainPager.setCurrentItem(tab.ordinal, true)
        }

        // Если у нас открыты альбомы, настройки или другие фрагменты поверх — закрываем их
        if (supportFragmentManager.backStackEntryCount > 0) {
            supportFragmentManager.popBackStack(null, androidx.fragment.app.FragmentManager.POP_BACK_STACK_INCLUSIVE)
        }
    }

    // 🔥 ФУНКЦИЯ startFragment ТЕПЕРЬ ВСЕГДА КЛАДЕТ ФРАГМЕНТ В ПРОЗРАЧНЫЙ СЛОЙ @+id/container С ИММЕРСИВНЫМИ АНИМАЦИЯМИ
    fun startFragment(frag: Fragment, sharedView: View? = null, transName: String? = null, args: (Bundle.() -> Unit)? = null) {
        supportFragmentManager.commit(allowStateLoss = true) {
            setReorderingAllowed(true)
            val currentFragment = supportFragmentManager.fragments.lastOrNull { it.isVisible && it.id == R.id.container }

            frag.enterTransition = com.google.android.material.transition.MaterialSharedAxis(com.google.android.material.transition.MaterialSharedAxis.Z, true).apply { duration = 350 }
            frag.returnTransition = com.google.android.material.transition.MaterialSharedAxis(com.google.android.material.transition.MaterialSharedAxis.Z, false).apply { duration = 350 }

            if (sharedView != null && transName != null && !sharedView.transitionName.isNullOrEmpty()) {
                currentFragment?.exitTransition = MaterialElevationScale(false).apply { duration = 350 }
                currentFragment?.reenterTransition = MaterialElevationScale(true).apply { duration = 350 }
                addSharedElement(sharedView, transName)
                addToBackStack(System.currentTimeMillis().toString())
                replace(R.id.container, frag.apply { args?.let { arguments = Bundle().apply(it) } })
            } else {
                currentFragment?.exitTransition = com.google.android.material.transition.MaterialSharedAxis(com.google.android.material.transition.MaterialSharedAxis.Z, true).apply { duration = 350 }
                currentFragment?.reenterTransition = com.google.android.material.transition.MaterialSharedAxis(com.google.android.material.transition.MaterialSharedAxis.Z, false).apply { duration = 350 }
                addToBackStack(System.currentTimeMillis().toString())
                if (currentFragment != null) hide(currentFragment)
                add(R.id.container, frag.apply { args?.let { arguments = Bundle().apply(it) } })
            }
        }
    }

    @OptIn(FlowPreview::class)
    fun addToPlaylistDialog(song: File?) {
        if (song == null) {
            Toast.makeText(this@MainActivity, getString(R.string.edit_playlist_failed, "song == null"), Toast.LENGTH_LONG).show()
            return
        }
        val playlists = runBlocking { reader.playlistListFlow.first().filter { it.id != null } }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.add_to_playlist)
            .setIcon(R.drawable.ic_playlist_play)
            .setItems((playlists.map { it.title ?: it.path?.absolutePath ?: it.id.toString() } + getString(R.string.create_playlist)).toTypedArray()) { _, item ->
                if (playlists.size == item) {
                    PlaylistAdapter.playlistNameDialog(this, R.string.create_playlist, "") { name ->
                        CoroutineScope(Dispatchers.Default).launch {
                            val f = try { ItemManipulator.createPlaylist(this@MainActivity, name) } catch (e: Exception) { return@launch }
                            try { ItemManipulator.setPlaylistContent(this@MainActivity, f, listOf(song)) } catch (e: Exception) {}
                        }
                    }
                    return@setItems
                }
                val pl = playlists[item]
                setPlaylist(pl.path!!, ContentUris.withAppendedId(@Suppress("deprecation") MediaStore.Audio.Playlists.getContentUri("external"), pl.id!!), true, listOf(song))
            }
            .setNegativeButton(android.R.string.cancel) { _, _ -> }
            .show()
    }

    fun setPlaylist(playlist: File, uri: Uri, addToEnd: Boolean, songs: List<File>) {
        setPlaylist(playlist, uri, addToEnd, ArrayList(songs.map { it.absolutePath }))
    }

    fun setPlaylist(playlist: File, uri: Uri, addToEnd: Boolean, songs: ArrayList<String>) {
        val data = Bundle().apply {
            putBoolean("AddToEnd", addToEnd)
            putStringArrayList("Songs", songs)
            putString("PlaylistPath", playlist.absolutePath)
        }
        CoroutineScope(Dispatchers.Default).launch {
            if (ItemManipulator.needRequestWrite(this@MainActivity, uri)) {
                pendingRequest = data
                val pendingIntent = MediaStore.createWriteRequest(contentResolver, listOf(uri))
                addToPlaylistIntentSender.launch(IntentSenderRequest.Builder(pendingIntent.intentSender).build())
            } else doAddToPlaylist(RESULT_OK, data)
        }
    }

    fun runIntentForDelete(intent: Intent, bundle: Bundle) {
        try {
            intentDelete.launch(intent)
            pendingDeleteRequest = bundle
        } catch (e: ActivityNotFoundException) {
            CoroutineScope(Dispatchers.Default).launch { ItemManipulator.continueDeleteFromIntent(this@MainActivity, RESULT_CANCELED, Intent(), bundle) }
        }
    }

    fun runIntentForDelete(intent: IntentSender, bundle: Bundle) {
        try {
            intentSenderDelete.launch(IntentSenderRequest.Builder(intent).build())
            pendingDeleteRequest = bundle
        } catch (e: ActivityNotFoundException) {
            CoroutineScope(Dispatchers.Default).launch { ItemManipulator.continueDeleteFromPendingIntent(this@MainActivity, RESULT_CANCELED, bundle) }
        }
    }

    private suspend fun doAddToPlaylist(resultCode: Int, data: Bundle) {
        if (resultCode == RESULT_OK) {
            val add = data.getBoolean("AddToEnd")
            val path = File(data.getString("PlaylistPath")!!)
            val songs = data.getStringArrayList("Songs")!!.map { File(it) }
            try {
                if (add) ItemManipulator.addToPlaylist(this@MainActivity, path, songs)
                else ItemManipulator.setPlaylistContent(this@MainActivity, path, songs)
            } catch (e: Exception) {}
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (pendingRequest != null) outState.putBundle("AddToPlaylistPendingRequest", pendingRequest)
        if (pendingDeleteRequest != null) outState.putBundle("DeletePendingRequest", pendingDeleteRequest)
    }

    private fun doPlayFromIntent(intent: Intent) {
        intent.extras?.getString(PLAYBACK_AUTO_PLAY_ID)?.let { id ->
            val pos = intent.extras?.getLong(PLAYBACK_AUTO_PLAY_POSITION, C.TIME_UNSET) ?: C.TIME_UNSET
            controllerViewModel.addControllerCallback(lifecycle) { controller, _ ->
                CoroutineScope(Dispatchers.Main).launch {
                    withContext(Dispatchers.Default) {
                        val col = reader.idMapFlow.firstOrNull()
                        id.toLongOrNull()?.let { col?.get(it) }
                    }.let { mediaItem ->
                        if (mediaItem != null) {
                            controller.setMediaItem(mediaItem, pos)
                            controller.prepare()
                            controller.play()
                        }
                    }
                }
                dispose()
            }
        }
        if (intent.action == Intent.ACTION_SEARCH || intent.action == "com.google.android.gms.actions.SEARCH_ACTION") {
            startFragment(SearchFragment()) { Bundle().apply { putString("query", intent.getStringExtra(SearchManager.QUERY)) } }
        }
    }

    fun updateLibrary(then: (() -> Unit)? = null) {
        if (!ready) handler.postDelayed(reportFullyDrawnRunnable, 2000)
        CoroutineScope(Dispatchers.Default).launch {
            this@MainActivity.gramophoneApplication.reader.refresh()
            withContext(Dispatchers.Main) { onLibraryLoaded(); then?.let { it() } }
        }
    }

    override fun reportFullyDrawn() {
        handler.removeCallbacks(reportFullyDrawnRunnable)
        ready = true
        Choreographer.getInstance().postFrameCallback {
            handler.postAtFrontOfQueueAsync { try { super.reportFullyDrawn() } catch (e: Exception) {} }
        }
    }

    @RequiresApi(23)
    override fun onProvideAssistContent(outContent: AssistContent?) {
        super.onProvideAssistContent(outContent)
        val instance = getPlayer()
        if (instance != null && outContent != null) {
            try {
                val item = instance.currentMediaItem
                val uri = item?.requestMetadata?.mediaUri ?: item?.localConfiguration?.uri
                val contentUri = if (uri?.scheme == "file") FileProvider.getUriForFile(this, "$packageName.fileProvider", File(uri.path!!)) else uri
                if (contentUri != null) outContent.clipData = ClipData.newUri(contentResolver, item?.mediaMetadata?.title ?: "", contentUri)
            } catch (e: Exception) {}
        }
    }

    fun onLibraryLoaded() { doPlayFromIntent(intent) }
    fun maybeReportFullyDrawn() { if (!ready) reportFullyDrawn() }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_READ_MEDIA_AUDIO) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) updateLibrary()
            else {
                maybeReportFullyDrawn()
                Toast.makeText(this, getString(R.string.grant_audio), Toast.LENGTH_LONG).show()
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply { setData("package:$packageName".toUri()) }
                startActivity(intent)
                finish()
            }
        }
    }

    override fun onStart() {
        super.onStart()
        isForeground = true
        getPlayer()?.let { player -> updatePlayerUI(player) }
        progressHandler.removeCallbacks(progressRunnable)
        progressHandler.post(progressRunnable)
    }

    override fun onResume() {
        super.onResume()
        isForeground = true
        getPlayer()?.let { player -> updatePlayerUI(player) }
        progressHandler.removeCallbacks(progressRunnable)
        progressHandler.post(progressRunnable)
    }

    override fun onPause() {
        super.onPause()
        isForeground = false
        progressHandler.removeCallbacks(progressRunnable)
    }

    override fun onStop() {
        super.onStop()
        isForeground = false
        progressHandler.removeCallbacks(progressRunnable)
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= TRIM_MEMORY_UI_HIDDEN) {
            imageLoader.memoryCache?.clear()
        }
    }

    override fun onDestroy() {
        isForeground = false
        if (needsMissingOnDestroyCallWorkarounds() && (getPlayer()?.playWhenReady != true || getPlayer()?.mediaItemCount == 0)) {
            val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            nm.cancel(DefaultMediaNotificationProvider.DEFAULT_NOTIFICATION_ID)
        }
        super.onDestroy()
        imageLoader.memoryCache?.clear()
        progressHandler.removeCallbacks(progressRunnable)
    }

    fun getPlayer() = controllerViewModel.get()
    inline val reader get() = gramophoneApplication.reader

    private fun setupMiniPlayer(controller: MediaController) {
        updatePlayerUI(controller)
        controller.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) = updatePlayerUI(controller)
            override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) = updatePlayerUI(controller)
            override fun onIsPlayingChanged(isPlayingChanged: Boolean) {
                updatePlayerUI(controller)
                if (isPlayingChanged) progressHandler.post(progressRunnable) else progressHandler.removeCallbacks(progressRunnable)
            }
            override fun onPlaybackStateChanged(playbackState: Int) = updatePlayerUI(controller)
            override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) { isShuffle = shuffleModeEnabled }
            override fun onRepeatModeChanged(repeatMode: Int) {
                repeatModeState = repeatMode
                isLoop = repeatMode != Player.REPEAT_MODE_OFF
            }
        })
    }

    private fun updatePlayerUI(controller: MediaController) {
        if (controller.currentMediaItem == null) {
            miniPlayerVisible = false
            return
        }
        miniPlayerVisible = true
        val metadata = controller.mediaMetadata
        trackTitle = metadata.title?.toString() ?: "Неизвестный трек"
        trackArtist = metadata.artist?.toString() ?: "Неизвестный артист"
        isPlaying = controller.isPlaying
        isBuffering = controller.playbackState == Player.STATE_BUFFERING

        val trackId = controller.currentMediaItem?.mediaId ?: ""
        isLiked = LikeCache.isLiked(trackId, title = trackTitle, artist = trackArtist)

        android.util.Log.d("SalvationLike", "Cache keys: ${LikeCache.likedTracks.take(10)}, type is ${LikeCache.likedTracks.firstOrNull()?.let { it::class.simpleName }}")
        android.util.Log.d("SalvationLike", "updatePlayerUI: trackId='$trackId', isLiked_state=$isLiked, isPlaying=$isPlaying")

        isShuffle = controller.shuffleModeEnabled
        repeatModeState = controller.repeatMode
        isLoop = controller.repeatMode != Player.REPEAT_MODE_OFF

        val originalUri = metadata.artworkUri?.toString() ?: ""
        coverUrl = if (originalUri.startsWith("/")) "http://185.196.41.31$originalUri" else originalUri

        val currentId = controller.currentMediaItem?.mediaId
        val dynamicDur = currentId?.let { org.akanework.gramophone.logic.GramophonePlaybackService.getTrackDuration(it) }
        val rawDur = if (controller.duration > 0) controller.duration else (controller.currentMediaItem?.mediaMetadata?.durationMs ?: dynamicDur)
        val dur = if (rawDur != null && rawDur > 0) rawDur.toFloat() else 1f
        trackDuration = dur
        currentPosition = controller.currentPosition.toFloat().coerceIn(0f, dur)
    }

    private fun applyPhysicalShuffle(player: MediaController) {
        org.akanework.gramophone.logic.utils.ShuffleUtils.applyPhysicalShuffle(player)
    }

    private fun syncLikedTracksCache() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                android.util.Log.d("SalvationLike", "Начинаем фоновую синхронизацию лайков...")
                val api = org.akanework.gramophone.logic.api.NetworkClient.getApi(this@MainActivity)

                val response = api.getFavorites(skip = 0, limit = 10000).execute()

                if (response.isSuccessful) {
                    val tracks = response.body() ?: emptyList()
                    LikeCache.clear()
                    tracks.forEach { track ->
                        LikeCache.add(track.id, track.sourceId, track.title, track.artist)
                    }

                    android.util.Log.d("SalvationLike", "Синхронизация успешна! В кэш загружено ${LikeCache.likedTracks.size} ключей и ${LikeCache.likedSignatures.size} сигнатур для ${tracks.size} треков.")

                    withContext(Dispatchers.Main) {
                        getPlayer()?.let { updatePlayerUI(it) }
                    }
                } else {
                    android.util.Log.e("SalvationLike", "Ошибка синхронизации лайков: сервер вернул код ${response.code()}")
                }
            } catch (e: Exception) {
                android.util.Log.e("SalvationLike", "Эксепшен при загрузке кэша лайков: ${e.message}", e)
            }
        }
    }
}