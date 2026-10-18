package org.akanework.gramophone.ui

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import com.bumptech.glide.Glide
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.akanework.gramophone.R
import org.akanework.gramophone.logic.GramophonePlaybackService
import org.akanework.gramophone.logic.utils.CoverUtils

/**
 * Base AppWidgetProvider for Pixel-styled widgets handling common click actions and updates.
 */
abstract class BasePixelPlayerWidgetProvider(
    private val layoutResId: Int
) : AppWidgetProvider() {

    override fun onReceive(context: Context, intent: Intent) {
        if (PixelWidgetManager.handleAction(context, intent)) {
            return
        }
        super.onReceive(context, intent)
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        PixelWidgetManager.updateSpecificWidget(
            context,
            appWidgetManager,
            appWidgetIds,
            this::class.java,
            layoutResId
        )
    }
}

/**
 * 2x2 Square Grid Widget: Album art + Play/Pause + Prev + Next.
 */
class PixelSquareWidgetProvider : BasePixelPlayerWidgetProvider(R.layout.widget_pixel_square_2x2)

/**
 * 4x1 Compact Bar Widget: Squircle art + Title/Artist + Prev + Play/Pause + Next.
 */
class PixelCompactWidgetProvider : BasePixelPlayerWidgetProvider(R.layout.widget_pixel_compact_4x1)

/**
 * 4x2 Standard Card Widget: Top art + Title/Artist, bottom 5 squircle controls (Shuffle, Prev, Play/Pause, Next, Repeat).
 */
class PixelStandardWidgetProvider : BasePixelPlayerWidgetProvider(R.layout.widget_pixel_standard_4x2)

/**
 * Central manager responsible for dispatching actions and updating all 3 Pixel widgets.
 */
object PixelWidgetManager {

    const val ACTION_PIXEL_PLAY_PAUSE = "org.akanework.gramophone.ACTION_PIXEL_PLAY_PAUSE"
    const val ACTION_PIXEL_PREV = "org.akanework.gramophone.ACTION_PIXEL_PREV"
    const val ACTION_PIXEL_NEXT = "org.akanework.gramophone.ACTION_PIXEL_NEXT"
    const val ACTION_PIXEL_SHUFFLE = "org.akanework.gramophone.ACTION_PIXEL_SHUFFLE"
    const val ACTION_PIXEL_REPEAT = "org.akanework.gramophone.ACTION_PIXEL_REPEAT"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var cachedArtKey: String? = null
    private var cachedArtBitmap: Bitmap? = null
    private var currentLoadingKey: String? = null

    /**
     * Entry point for custom widget broadcast actions.
     */
    fun onReceive(context: Context, intent: Intent) {
        handleAction(context, intent)
    }

    fun handleAction(context: Context, intent: Intent): Boolean {
        val action = intent.action ?: return false
        when (action) {
            ACTION_PIXEL_PLAY_PAUSE,
            ACTION_PIXEL_PREV,
            ACTION_PIXEL_NEXT,
            ACTION_PIXEL_SHUFFLE,
            ACTION_PIXEL_REPEAT -> {
                val service = GramophonePlaybackService.instanceForWidgetAndLyricsOnly
                val player = service?.endedWorkaroundPlayer
                if (service != null && player != null) {
                    Handler(Looper.getMainLooper()).post {
                        when (action) {
                            ACTION_PIXEL_PLAY_PAUSE -> {
                                if (player.isPlaying) player.pause() else player.play()
                            }
                            ACTION_PIXEL_PREV -> {
                                player.seekToPrevious()
                            }
                            ACTION_PIXEL_NEXT -> {
                                player.seekToNext()
                            }
                            ACTION_PIXEL_SHUFFLE -> {
                                player.shuffleModeEnabled = !player.shuffleModeEnabled
                            }
                            ACTION_PIXEL_REPEAT -> {
                                player.repeatMode = when (player.repeatMode) {
                                    Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                                    Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                                    else -> Player.REPEAT_MODE_OFF
                                }
                            }
                        }
                        updateAll(context)
                    }
                } else if (action == ACTION_PIXEL_PLAY_PAUSE) {
                    try {
                        val launchIntent = Intent(context, MainActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                        }
                        context.startActivity(launchIntent)
                    } catch (_: Exception) {}
                }
                return true
            }
            else -> return false
        }
    }

    /**
     * Updates all 3 Pixel widget providers with the latest playback state and track metadata.
     */
    fun updateAll(context: Context) {
        val awm = AppWidgetManager.getInstance(context) ?: return
        val squareIds = awm.getAppWidgetIds(ComponentName(context, PixelSquareWidgetProvider::class.java))
        val compactIds = awm.getAppWidgetIds(ComponentName(context, PixelCompactWidgetProvider::class.java))
        val standardIds = awm.getAppWidgetIds(ComponentName(context, PixelStandardWidgetProvider::class.java))

        if (squareIds.isNotEmpty()) {
            updateSpecificWidget(context, awm, squareIds, PixelSquareWidgetProvider::class.java, R.layout.widget_pixel_square_2x2)
        }
        if (compactIds.isNotEmpty()) {
            updateSpecificWidget(context, awm, compactIds, PixelCompactWidgetProvider::class.java, R.layout.widget_pixel_compact_4x1)
        }
        if (standardIds.isNotEmpty()) {
            updateSpecificWidget(context, awm, standardIds, PixelStandardWidgetProvider::class.java, R.layout.widget_pixel_standard_4x2)
        }
    }

    fun updateSpecificWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
        providerClass: Class<out AppWidgetProvider>,
        layoutResId: Int
    ) {
        if (appWidgetIds.isEmpty()) return

        val service = GramophonePlaybackService.instanceForWidgetAndLyricsOnly
        val player = service?.endedWorkaroundPlayer
        val isPlaying = player?.isPlaying == true
        val currentMediaItem = player?.currentMediaItem
        val title = currentMediaItem?.mediaMetadata?.title?.toString()?.takeIf { it.isNotBlank() }
            ?: "Salvation"
        val artist = currentMediaItem?.mediaMetadata?.artist?.toString()?.takeIf { it.isNotBlank() }
            ?: "Tap to open"
        val shuffleMode = player?.shuffleModeEnabled == true
        val repeatMode = player?.repeatMode ?: Player.REPEAT_MODE_OFF

        // PendingIntent for launching MainActivity
        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val openAppPi = PendingIntent.getActivity(
            context,
            providerClass.hashCode() and 0x7FFFFFFF,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Button action PendingIntents
        val playPausePi = getActionPendingIntent(context, ACTION_PIXEL_PLAY_PAUSE, providerClass, 1)
        val prevPi = getActionPendingIntent(context, ACTION_PIXEL_PREV, providerClass, 2)
        val nextPi = getActionPendingIntent(context, ACTION_PIXEL_NEXT, providerClass, 3)
        val shufflePi = getActionPendingIntent(context, ACTION_PIXEL_SHUFFLE, providerClass, 4)
        val repeatPi = getActionPendingIntent(context, ACTION_PIXEL_REPEAT, providerClass, 5)

        // Artwork resolution
        val artBitmap = getOrLoadArtwork(context, currentMediaItem)

        for (appWidgetId in appWidgetIds) {
            val views = RemoteViews(context.packageName, layoutResId)

            // Open app click targets
            views.setOnClickPendingIntent(R.id.widget_art_container, openAppPi)
            views.setOnClickPendingIntent(R.id.widget_root, openAppPi)

            // Play / Pause button
            views.setImageViewResource(
                R.id.widget_btn_play_pause,
                if (isPlaying) R.drawable.ic_pause_filled else R.drawable.ic_play_arrow_filled
            )
            views.setContentDescription(
                R.id.widget_btn_play_pause,
                if (isPlaying) "Pause" else context.getString(R.string.play)
            )
            views.setOnClickPendingIntent(R.id.widget_btn_play_pause, playPausePi)

            // Previous and Next buttons
            views.setImageViewResource(R.id.widget_btn_prev, R.drawable.ic_skip_previous_filled)
            views.setOnClickPendingIntent(R.id.widget_btn_prev, prevPi)
            views.setImageViewResource(R.id.widget_btn_next, R.drawable.ic_skip_next_filled)
            views.setOnClickPendingIntent(R.id.widget_btn_next, nextPi)

            // Album artwork display
            if (artBitmap != null) {
                views.setViewPadding(R.id.widget_album_art, 0, 0, 0, 0)
                views.setImageViewBitmap(R.id.widget_album_art, artBitmap)
            } else {
                val pad = (14 * context.resources.displayMetrics.density).toInt()
                views.setViewPadding(R.id.widget_album_art, pad, pad, pad, pad)
                views.setImageViewResource(R.id.widget_album_art, R.drawable.ic_pixel_music_note)
            }

            // Layout specific elements
            when (layoutResId) {
                R.layout.widget_pixel_compact_4x1 -> {
                    views.setOnClickPendingIntent(R.id.widget_info_container, openAppPi)
                    views.setTextViewText(R.id.widget_track_title, title)
                    views.setTextViewText(R.id.widget_track_artist, artist)
                }
                R.layout.widget_pixel_standard_4x2 -> {
                    views.setOnClickPendingIntent(R.id.widget_info_container, openAppPi)
                    views.setTextViewText(R.id.widget_track_title, title)
                    views.setTextViewText(R.id.widget_track_artist, artist)

                    // Shuffle button styling and action
                    views.setImageViewResource(R.id.widget_btn_shuffle, R.drawable.ic_shuffle)
                    val shuffleTint = ContextCompat.getColor(
                        context,
                        if (shuffleMode) R.color.widget_pixel_active_tint else R.color.widget_pixel_on_surface
                    )
                    views.setInt(R.id.widget_btn_shuffle, "setColorFilter", shuffleTint)
                    views.setInt(
                        R.id.widget_btn_shuffle,
                        "setBackgroundResource",
                        if (shuffleMode) R.drawable.widget_pixel_tile_active_bg else R.drawable.widget_pixel_tile_bg
                    )
                    views.setOnClickPendingIntent(R.id.widget_btn_shuffle, shufflePi)

                    // Repeat button styling and action
                    views.setImageViewResource(R.id.widget_btn_repeat, R.drawable.ic_repeat)
                    val isRepeatActive = repeatMode != Player.REPEAT_MODE_OFF
                    val repeatTint = ContextCompat.getColor(
                        context,
                        if (isRepeatActive) R.color.widget_pixel_active_tint else R.color.widget_pixel_on_surface
                    )
                    views.setInt(R.id.widget_btn_repeat, "setColorFilter", repeatTint)
                    views.setInt(
                        R.id.widget_btn_repeat,
                        "setBackgroundResource",
                        if (isRepeatActive) R.drawable.widget_pixel_tile_active_bg else R.drawable.widget_pixel_tile_bg
                    )
                    views.setOnClickPendingIntent(R.id.widget_btn_repeat, repeatPi)
                }
            }

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }

    private fun getActionPendingIntent(
        context: Context,
        action: String,
        providerClass: Class<out AppWidgetProvider>,
        requestCodeOffset: Int
    ): PendingIntent {
        val intent = Intent(context, providerClass).apply {
            this.action = action
        }
        val requestCode = ((providerClass.hashCode() * 31 + action.hashCode()) * 31 + requestCodeOffset) and 0x7FFFFFFF
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun getOrLoadArtwork(context: Context, mediaItem: MediaItem?): Bitmap? {
        if (mediaItem == null) {
            cachedArtKey = null
            cachedArtBitmap = null
            currentLoadingKey = null
            return null
        }

        val key = "${mediaItem.mediaId}_${mediaItem.mediaMetadata.artworkUri ?: ""}"
        if (cachedArtKey == key && cachedArtBitmap != null) {
            return cachedArtBitmap
        }

        // Fast path: synchronous decode for in-memory artworkData
        mediaItem.mediaMetadata.artworkData?.let { data ->
            if (data.isNotEmpty()) {
                try {
                    val raw = BitmapFactory.decodeByteArray(data, 0, data.size)
                    if (raw != null) {
                        val maxDim = (140 * context.resources.displayMetrics.density).toInt().coerceIn(128, 256)
                        val scaled = scaleDown(raw, maxDim)
                        val rounded = createRoundedBitmap(scaled, 18f, context.resources.displayMetrics.density)
                        cachedArtKey = key
                        cachedArtBitmap = rounded
                        return rounded
                    }
                } catch (_: Exception) {}
            }
        }

        // Asynchronous load for local URI / Web URL
        if (currentLoadingKey != key) {
            currentLoadingKey = key
            val appContext = context.applicationContext
            scope.launch(Dispatchers.IO) {
                val bitmap = loadArtworkSync(appContext, mediaItem)
                val rounded = bitmap?.let {
                    val maxDim = (140 * appContext.resources.displayMetrics.density).toInt().coerceIn(128, 256)
                    val scaled = scaleDown(it, maxDim)
                    createRoundedBitmap(scaled, 18f, appContext.resources.displayMetrics.density)
                }
                withContext(Dispatchers.Main) {
                    if (currentLoadingKey == key) {
                        cachedArtKey = key
                        cachedArtBitmap = rounded
                        updateAll(appContext)
                    }
                }
            }
        }

        return cachedArtBitmap
    }

    private fun loadArtworkSync(context: Context, mediaItem: MediaItem): Bitmap? {
        mediaItem.mediaMetadata.artworkData?.let { data ->
            try {
                val raw = BitmapFactory.decodeByteArray(data, 0, data.size)
                if (raw != null) return raw
            } catch (_: Exception) {}
        }

        mediaItem.mediaMetadata.artworkUri?.let { uri ->
            val scheme = uri.scheme
            if (scheme == "content" || scheme == "file" || scheme == "android.resource") {
                try {
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        val raw = BitmapFactory.decodeStream(stream)
                        if (raw != null) return raw
                    }
                } catch (_: Exception) {}
            }
        }

        val rawCoverUrl = mediaItem.mediaMetadata.artworkUri?.toString()
            ?: GramophonePlaybackService.getTrackCover(mediaItem.mediaId)
        val coverUrl = CoverUtils.upgradeCoverQuality(rawCoverUrl) ?: rawCoverUrl
        if (!coverUrl.isNullOrBlank()) {
            try {
                return Glide.with(context)
                    .asBitmap()
                    .load(coverUrl)
                    .submit(256, 256)
                    .get()
            } catch (_: Exception) {}
        }

        return null
    }

    private fun scaleDown(bitmap: Bitmap, maxDim: Int): Bitmap {
        if (bitmap.width <= maxDim && bitmap.height <= maxDim) return bitmap
        val ratio = bitmap.width.toFloat() / bitmap.height.toFloat()
        val width = if (ratio >= 1f) maxDim else (maxDim * ratio).toInt()
        val height = if (ratio >= 1f) (maxDim / ratio).toInt() else maxDim
        return Bitmap.createScaledBitmap(bitmap, width.coerceAtLeast(1), height.coerceAtLeast(1), true)
    }

    private fun createRoundedBitmap(src: Bitmap, cornerRadiusDp: Float, density: Float): Bitmap {
        val radiusPx = cornerRadiusDp * density
        val size = minOf(src.width, src.height)
        val output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val rectF = RectF(0f, 0f, size.toFloat(), size.toFloat())
        canvas.drawRoundRect(rectF, radiusPx, radiusPx, paint)
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        val left = (src.width - size) / 2
        val top = (src.height - size) / 2
        val srcRect = Rect(left, top, left + size, top + size)
        val dstRect = Rect(0, 0, size, size)
        canvas.drawBitmap(src, srcRect, dstRect, paint)
        return output
    }
}
