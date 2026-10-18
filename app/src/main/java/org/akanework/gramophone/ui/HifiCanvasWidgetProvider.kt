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
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.Util
import com.bumptech.glide.Glide
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.akanework.gramophone.R
import org.akanework.gramophone.logic.GramophonePlaybackService
import org.akanework.gramophone.logic.LikeCashe
import org.akanework.gramophone.logic.api.NetworkClient
import org.akanework.gramophone.logic.lossless.LosslessStateManager
import org.akanework.gramophone.logic.utils.CoverUtils
import java.util.Locale

/**
 * AppWidgetProvider for Audiophile Hi-Fi Canvas (4x2) widget.
 */
class HifiCanvasWidgetProvider : AppWidgetProvider() {

    override fun onReceive(context: Context, intent: Intent) {
        if (handleAction(context, intent)) {
            return
        }
        super.onReceive(context, intent)
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        updateWidgets(context, appWidgetManager, appWidgetIds)
    }

    override fun onDisabled(context: Context) {
        stopProgressUpdates()
        super.onDisabled(context)
    }

    companion object {
        const val ACTION_HIFI_PLAY_PAUSE = "org.akanework.gramophone.ACTION_HIFI_PLAY_PAUSE"
        const val ACTION_HIFI_PREV = "org.akanework.gramophone.ACTION_HIFI_PREV"
        const val ACTION_HIFI_NEXT = "org.akanework.gramophone.ACTION_HIFI_NEXT"
        const val ACTION_HIFI_SHUFFLE = "org.akanework.gramophone.ACTION_HIFI_SHUFFLE"
        const val ACTION_HIFI_LIKE = "org.akanework.gramophone.ACTION_HIFI_LIKE"

        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        private val handler = Handler(Looper.getMainLooper())

        private var cachedArtKey: String? = null
        private var cachedArtBitmap: Bitmap? = null
        private var currentLoadingKey: String? = null

        private var isProgressTimerRunning = false
        private val progressRunnable = object : Runnable {
            override fun run() {
                val service = GramophonePlaybackService.instanceForWidgetAndLyricsOnly
                val player = service?.endedWorkaroundPlayer
                if (service != null && player?.isPlaying == true && hasWidgets(service)) {
                    updateProgressOnly(service)
                    handler.postDelayed(this, 1000L)
                } else {
                    isProgressTimerRunning = false
                }
            }
        }

        fun hasWidgets(context: Context): Boolean {
            val awm = AppWidgetManager.getInstance(context) ?: return false
            return awm.getAppWidgetIds(
                ComponentName(context, HifiCanvasWidgetProvider::class.java)
            ).isNotEmpty()
        }

        fun handleAction(context: Context, intent: Intent): Boolean {
            val action = intent.action ?: return false
            when (action) {
                ACTION_HIFI_PLAY_PAUSE,
                ACTION_HIFI_PREV,
                ACTION_HIFI_NEXT,
                ACTION_HIFI_SHUFFLE,
                ACTION_HIFI_LIKE -> {
                    val service = GramophonePlaybackService.instanceForWidgetAndLyricsOnly
                    val player = service?.endedWorkaroundPlayer
                    if (service != null && player != null) {
                        handler.post {
                            when (action) {
                                ACTION_HIFI_PLAY_PAUSE -> {
                                    if (player.isPlaying) player.pause() else player.play()
                                }
                                ACTION_HIFI_PREV -> {
                                    player.seekToPrevious()
                                }
                                ACTION_HIFI_NEXT -> {
                                    player.seekToNext()
                                }
                                ACTION_HIFI_SHUFFLE -> {
                                    player.shuffleModeEnabled = !player.shuffleModeEnabled
                                }
                                ACTION_HIFI_LIKE -> {
                                    val currentItem = player.currentMediaItem
                                    val trackId = currentItem?.mediaId
                                    val trackTitle = currentItem?.mediaMetadata?.title?.toString()
                                    val trackArtist = currentItem?.mediaMetadata?.artist?.toString()
                                    if (!trackId.isNullOrBlank()) {
                                        val isLiked = LikeCashe.isLiked(
                                            trackId,
                                            title = trackTitle,
                                            artist = trackArtist
                                        )
                                        val newLiked = !isLiked
                                        if (newLiked) {
                                            LikeCashe.add(trackId, title = trackTitle, artist = trackArtist)
                                        } else {
                                            LikeCashe.remove(trackId, title = trackTitle, artist = trackArtist)
                                        }
                                        CoroutineScope(Dispatchers.IO).launch {
                                            try {
                                                val api = NetworkClient.getApi(context)
                                                if (newLiked) {
                                                    api.likeTrack(trackId).execute()
                                                } else {
                                                    api.unlikeTrack(trackId).execute()
                                                }
                                            } catch (_: Exception) {}
                                        }
                                    }
                                }
                            }
                            updateAll(context)
                        }
                    } else if (action == ACTION_HIFI_PLAY_PAUSE) {
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

        fun updateAll(context: Context) {
            val awm = AppWidgetManager.getInstance(context) ?: return
            val ids = awm.getAppWidgetIds(
                ComponentName(context, HifiCanvasWidgetProvider::class.java)
            )
            if (ids.isNotEmpty()) {
                updateWidgets(context, awm, ids)
            }
        }

        fun updateWidgets(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetIds: IntArray
        ) {
            if (appWidgetIds.isEmpty()) return

            val service = GramophonePlaybackService.instanceForWidgetAndLyricsOnly
            val player = service?.endedWorkaroundPlayer
            val isPlaying = player?.isPlaying == true
            val currentMediaItem = player?.currentMediaItem

            val title = currentMediaItem?.mediaMetadata?.title?.toString()?.takeIf { it.isNotBlank() }
                ?: context.getString(R.string.app_name)
            val artist = currentMediaItem?.mediaMetadata?.artist?.toString()?.takeIf { it.isNotBlank() }
            val album = currentMediaItem?.mediaMetadata?.albumTitle?.toString()?.takeIf { it.isNotBlank() }
            val subtitle = when {
                artist != null && album != null -> "$artist • $album"
                artist != null -> artist
                album != null -> album
                else -> "Tap to open"
            }

            val (formatPillText, isHiResOrLossless) = detectFormatPillText(service, currentMediaItem)

            val position = player?.contentPosition?.coerceAtLeast(0L) ?: 0L
            val duration = player?.duration?.takeIf { it > 0L } ?: 0L
            val progressPercent = if (duration > 0L) {
                ((position.toDouble() / duration.toDouble()) * 100).toInt().coerceIn(0, 100)
            } else 0

            val trackId = currentMediaItem?.mediaId
            val isLiked = if (!trackId.isNullOrBlank()) {
                LikeCashe.isLiked(
                    trackId,
                    title = currentMediaItem.mediaMetadata.title?.toString(),
                    artist = currentMediaItem.mediaMetadata.artist?.toString()
                )
            } else false

            val shuffleMode = player?.shuffleModeEnabled == true

            // PendingIntent for launching MainActivity
            val openAppIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            val openAppPi = PendingIntent.getActivity(
                context,
                HifiCanvasWidgetProvider::class.java.hashCode() and 0x7FFFFFFF,
                openAppIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            // Button action PendingIntents
            val playPausePi = getActionPendingIntent(context, ACTION_HIFI_PLAY_PAUSE, 1)
            val prevPi = getActionPendingIntent(context, ACTION_HIFI_PREV, 2)
            val nextPi = getActionPendingIntent(context, ACTION_HIFI_NEXT, 3)
            val shufflePi = getActionPendingIntent(context, ACTION_HIFI_SHUFFLE, 4)
            val likePi = getActionPendingIntent(context, ACTION_HIFI_LIKE, 5)

            // Artwork resolution
            val artBitmap = getOrLoadArtwork(context, currentMediaItem)

            for (appWidgetId in appWidgetIds) {
                val views = RemoteViews(context.packageName, R.layout.widget_concept_hifi_canvas_4x2)

                // Click targets to open app
                views.setOnClickPendingIntent(R.id.widget_album_art_container, openAppPi)
                views.setOnClickPendingIntent(R.id.widget_title, openAppPi)
                views.setOnClickPendingIntent(R.id.widget_artist, openAppPi)
                views.setOnClickPendingIntent(R.id.widget_format_pill, openAppPi)
                views.setOnClickPendingIntent(android.R.id.background, openAppPi)

                // Track text
                views.setTextViewText(R.id.widget_title, title)
                views.setTextViewText(R.id.widget_artist, subtitle)

                // Audio format pill and hi-res badge
                views.setTextViewText(R.id.widget_audio_format, formatPillText)
                views.setViewVisibility(
                    R.id.widget_art_badge,
                    if (isHiResOrLossless) View.VISIBLE else View.GONE
                )
                views.setViewVisibility(R.id.widget_dac_dot, View.VISIBLE)

                // Progress bar and precision timestamps
                views.setProgressBar(R.id.widget_progress_bar, 100, progressPercent, false)
                views.setTextViewText(R.id.widget_current_time, formatTime(position))
                views.setTextViewText(R.id.widget_total_time, formatTime(duration))

                // Control buttons
                // Shuffle
                views.setOnClickPendingIntent(R.id.widget_btn_shuffle, shufflePi)
                val shuffleTint = if (shuffleMode) {
                    Color.parseColor("#A8C7FA")
                } else {
                    ContextCompat.getColor(context, R.color.md_theme_onSurfaceVariant)
                }
                views.setInt(R.id.widget_btn_shuffle, "setColorFilter", shuffleTint)

                // Previous
                views.setImageViewResource(R.id.widget_btn_prev, R.drawable.ic_skip_previous_filled)
                views.setOnClickPendingIntent(R.id.widget_btn_prev, prevPi)

                // Play / Pause
                views.setImageViewResource(
                    R.id.widget_btn_play_pause,
                    if (isPlaying) R.drawable.ic_pause_filled else R.drawable.ic_play_arrow_filled
                )
                views.setContentDescription(
                    R.id.widget_btn_play_pause,
                    if (isPlaying) "Pause" else context.getString(R.string.play)
                )
                views.setOnClickPendingIntent(R.id.widget_btn_play_pause, playPausePi)

                // Next
                views.setImageViewResource(R.id.widget_btn_next, R.drawable.ic_skip_next_filled)
                views.setOnClickPendingIntent(R.id.widget_btn_next, nextPi)

                // Favorite / Like
                views.setImageViewResource(
                    R.id.widget_btn_favorite,
                    if (isLiked) R.drawable.ic_favorite_filled else R.drawable.ic_favorite
                )
                val favTint = if (isLiked) {
                    Color.parseColor("#FF5370")
                } else {
                    ContextCompat.getColor(context, R.color.md_theme_onSurfaceVariant)
                }
                views.setInt(R.id.widget_btn_favorite, "setColorFilter", favTint)
                views.setOnClickPendingIntent(R.id.widget_btn_favorite, likePi)

                // Album Art
                if (artBitmap != null) {
                    views.setImageViewBitmap(R.id.widget_album_art, artBitmap)
                } else {
                    views.setImageViewResource(R.id.widget_album_art, R.drawable.ic_default_cover)
                }

                appWidgetManager.updateAppWidget(appWidgetId, views)
            }

            if (isPlaying) {
                startProgressUpdates(context)
            } else {
                stopProgressUpdates()
            }
        }

        private fun updateProgressOnly(context: Context) {
            val awm = AppWidgetManager.getInstance(context) ?: return
            val ids = awm.getAppWidgetIds(
                ComponentName(context, HifiCanvasWidgetProvider::class.java)
            )
            if (ids.isEmpty()) return

            val service = GramophonePlaybackService.instanceForWidgetAndLyricsOnly
            val player = service?.endedWorkaroundPlayer
            val position = player?.contentPosition?.coerceAtLeast(0L) ?: 0L
            val duration = player?.duration?.takeIf { it > 0L } ?: 0L
            val progressPercent = if (duration > 0L) {
                ((position.toDouble() / duration.toDouble()) * 100).toInt().coerceIn(0, 100)
            } else 0

            val views = RemoteViews(context.packageName, R.layout.widget_concept_hifi_canvas_4x2)
            views.setProgressBar(R.id.widget_progress_bar, 100, progressPercent, false)
            views.setTextViewText(R.id.widget_current_time, formatTime(position))
            views.setTextViewText(R.id.widget_total_time, formatTime(duration))

            for (id in ids) {
                awm.partiallyUpdateAppWidget(id, views)
            }
        }

        private fun startProgressUpdates(context: Context) {
            if (!isProgressTimerRunning && hasWidgets(context)) {
                isProgressTimerRunning = true
                handler.removeCallbacks(progressRunnable)
                handler.postDelayed(progressRunnable, 1000L)
            }
        }

        private fun stopProgressUpdates() {
            isProgressTimerRunning = false
            handler.removeCallbacks(progressRunnable)
        }

        private fun detectFormatPillText(
            service: GramophonePlaybackService?,
            mediaItem: MediaItem?
        ): Pair<String, Boolean> {
            if (mediaItem == null) {
                return Pair("FLAC 24-bit / 96 kHz • Hi-Res", true)
            }

            val trackId = mediaItem.mediaId
            val isLosslessExtra = mediaItem.mediaMetadata.extras?.getBoolean("IS_LOSSLESS", false) ?: false
            val isTrackLossless = LosslessStateManager.isTrackLossless(trackId, isLosslessExtra)

            val player = service?.endedWorkaroundPlayer
            val exoFormat = service?.currentAudioSinkInputFormat ?: player?.exoPlayer?.audioFormat

            val isFormatMimeLossless = when (exoFormat?.sampleMimeType) {
                MimeTypes.AUDIO_FLAC,
                MimeTypes.AUDIO_ALAC,
                MimeTypes.AUDIO_WAV,
                MimeTypes.AUDIO_RAW,
                MimeTypes.AUDIO_TRUEHD -> true
                else -> false
            }
            val isLossless = isTrackLossless || isFormatMimeLossless

            val sampleRate = exoFormat?.sampleRate?.takeIf { it != Format.NO_VALUE && it > 0 }
                ?: if (isLossless) 96000 else 44100

            val bitDepth = try {
                exoFormat?.pcmEncoding?.let { Util.getBitDepth(it) }?.takeIf { it > 0 }
            } catch (_: Exception) {
                null
            } ?: if (isLossless) 24 else 16

            val srKhz = sampleRate / 1000.0
            val srStr = if (sampleRate % 1000 == 0) "${sampleRate / 1000} kHz" else String.format(Locale.US, "%.1f kHz", srKhz)

            val formatName = when {
                exoFormat?.sampleMimeType?.contains("flac", ignoreCase = true) == true -> "FLAC"
                exoFormat?.sampleMimeType?.contains("alac", ignoreCase = true) == true -> "ALAC"
                exoFormat?.sampleMimeType?.contains("wav", ignoreCase = true) == true -> "WAV"
                isLossless -> "FLAC"
                exoFormat?.sampleMimeType?.contains("mp3", ignoreCase = true) == true ||
                exoFormat?.sampleMimeType?.contains("mpeg", ignoreCase = true) == true -> "MP3"
                exoFormat?.sampleMimeType?.contains("aac", ignoreCase = true) == true -> "AAC"
                exoFormat?.sampleMimeType?.contains("opus", ignoreCase = true) == true -> "OPUS"
                else -> "FLAC"
            }

            val isHiRes = (bitDepth >= 24 && sampleRate >= 88200) || (isLossless && bitDepth >= 24)
            val tier = if (isHiRes) "Hi-Res" else if (isLossless) "Lossless" else "Standard"

            val text = "$formatName $bitDepth-bit / $srStr • $tier"
            return Pair(text, isHiRes || isLossless)
        }

        private fun formatTime(ms: Long): String {
            if (ms <= 0L) return "00:00"
            val totalSec = ms / 1000
            val min = totalSec / 60
            val sec = totalSec % 60
            val hr = min / 60
            return if (hr > 0) {
                String.format(Locale.US, "%d:%02d:%02d", hr, min % 60, sec)
            } else {
                String.format(Locale.US, "%02d:%02d", min, sec)
            }
        }

        private fun getActionPendingIntent(
            context: Context,
            action: String,
            requestCodeOffset: Int
        ): PendingIntent {
            val intent = Intent(context, HifiCanvasWidgetProvider::class.java).apply {
                this.action = action
            }
            val requestCode = ((HifiCanvasWidgetProvider::class.java.hashCode() * 31 + action.hashCode()) * 31 + requestCodeOffset) and 0x7FFFFFFF
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
                            val maxDim = (148 * context.resources.displayMetrics.density).toInt().coerceIn(148, 512)
                            val scaled = scaleDown(raw, maxDim)
                            val rounded = createRoundedBitmap(scaled, 24f, context.resources.displayMetrics.density)
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
                        val maxDim = (148 * appContext.resources.displayMetrics.density).toInt().coerceIn(148, 512)
                        val scaled = scaleDown(it, maxDim)
                        createRoundedBitmap(scaled, 24f, appContext.resources.displayMetrics.density)
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
                        .submit(300, 300)
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
            return Bitmap.createScaledBitmap(
                bitmap,
                width.coerceAtLeast(1),
                height.coerceAtLeast(1),
                true
            )
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
}
