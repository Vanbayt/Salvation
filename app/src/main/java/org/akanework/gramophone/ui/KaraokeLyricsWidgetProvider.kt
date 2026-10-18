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
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.view.View
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import com.bumptech.glide.Glide
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.akanework.gramophone.R
import org.akanework.gramophone.logic.GramophonePlaybackService
import org.akanework.gramophone.logic.getFirstSelectedTrackFormatByType
import org.akanework.gramophone.logic.utils.CoverUtils
import java.util.Locale

/**
 * Material Expressive Karaoke & Synced Lyrics 2.0 Widget Provider (4x3).
 */
class KaraokeLyricsWidgetProvider : AppWidgetProvider() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action == ACTION_KARAOKE_PLAY_PAUSE || action == ACTION_KARAOKE_NEXT) {
            val service = GramophonePlaybackService.instanceForWidgetAndLyricsOnly
            val player = service?.endedWorkaroundPlayer
            if (player != null) {
                Handler(Looper.getMainLooper()).post {
                    if (action == ACTION_KARAOKE_PLAY_PAUSE) {
                        if (player.isPlaying) player.pause() else player.play()
                    } else if (action == ACTION_KARAOKE_NEXT) {
                        player.seekToNext()
                    }
                    update(context)
                }
            } else if (action == ACTION_KARAOKE_PLAY_PAUSE) {
                try {
                    val launchIntent = Intent(context, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    }
                    context.startActivity(launchIntent)
                } catch (_: Exception) {}
            }
            return
        }
        super.onReceive(context, intent)
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        if (appWidgetIds.isEmpty()) return
        val service = GramophonePlaybackService.instanceForWidgetAndLyricsOnly
        val player = service?.endedWorkaroundPlayer
        val isPlaying = player?.isPlaying == true
        val currentMediaItem = player?.currentMediaItem
        val currentPos = (player?.contentPosition ?: 0L).coerceAtLeast(0L)
        val duration = player?.duration?.takeIf { it > 0 }
            ?: GramophonePlaybackService.getTrackDuration(currentMediaItem?.mediaId)
            ?: 0L

        val title = currentMediaItem?.mediaMetadata?.title?.toString()?.takeIf { it.isNotBlank() }
            ?: "Salvation"
        val artist = currentMediaItem?.mediaMetadata?.artist?.toString()?.takeIf { it.isNotBlank() }
            ?: context.getString(R.string.app_name)
        val audioBadge = getAudioQualityBadge(service)

        // PendingIntents
        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val openAppPi = PendingIntent.getActivity(
            context,
            OPEN_APP_REQUEST_CODE,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val playPauseIntent = Intent(context, KaraokeLyricsWidgetProvider::class.java).apply {
            action = ACTION_KARAOKE_PLAY_PAUSE
        }
        val playPausePi = PendingIntent.getBroadcast(
            context,
            PLAY_PAUSE_REQUEST_CODE,
            playPauseIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val nextIntent = Intent(context, KaraokeLyricsWidgetProvider::class.java).apply {
            action = ACTION_KARAOKE_NEXT
        }
        val nextPi = PendingIntent.getBroadcast(
            context,
            NEXT_REQUEST_CODE,
            nextIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val artBitmap = getOrLoadArtwork(context, currentMediaItem)

        // Lyrics resolution
        val syncedLyrics = service?.syncedLyrics
        val li = service?.getCurrentLyricIndex(true)

        for (appWidgetId in appWidgetIds) {
            val views = RemoteViews(context.packageName, R.layout.widget_concept_lyrics_karaoke_4x3)

            // Open app click targets
            views.setOnClickPendingIntent(android.R.id.background, openAppPi)
            views.setOnClickPendingIntent(R.id.widget_header, openAppPi)
            views.setOnClickPendingIntent(R.id.widget_track_info_container, openAppPi)
            views.setOnClickPendingIntent(R.id.widget_album_art_container, openAppPi)
            views.setOnClickPendingIntent(R.id.widget_pill_karaoke_sync, openAppPi)
            views.setOnClickPendingIntent(R.id.widget_pill_translation, openAppPi)

            // Header metadata
            views.setTextViewText(R.id.widget_track_title, title)
            views.setTextViewText(R.id.widget_track_artist, artist)
            views.setTextViewText(R.id.widget_track_badge, audioBadge)

            // Album artwork
            if (artBitmap != null) {
                views.setImageViewBitmap(R.id.widget_album_art, artBitmap)
            } else {
                views.setImageViewResource(R.id.widget_album_art, R.drawable.ic_default_cover)
            }

            // Controls
            views.setImageViewResource(
                R.id.widget_btn_play_pause,
                if (isPlaying) R.drawable.ic_pause_filled else R.drawable.ic_play_arrow_filled
            )
            views.setContentDescription(
                R.id.widget_btn_play_pause,
                if (isPlaying) "Pause" else context.getString(R.string.play)
            )
            views.setOnClickPendingIntent(R.id.widget_btn_play_pause, playPausePi)

            views.setImageViewResource(R.id.widget_btn_next, R.drawable.ic_skip_next_filled)
            views.setContentDescription(R.id.widget_btn_next, context.getString(R.string.skip_next))
            views.setOnClickPendingIntent(R.id.widget_btn_next, nextPi)

            // Synchronized Lyrics
            if (syncedLyrics != null && syncedLyrics.text.isNotEmpty()) {
                val allLines = syncedLyrics.text
                val activeIndex = li ?: allLines.indexOfFirst { it.start > currentPos.toULong() }
                    .takeIf { it > 0 }?.minus(1) ?: 0
                val activeLine = allLines.getOrNull(activeIndex) ?: allLines.first()

                // Translation extraction
                val activeTranslation = activeLine.translation?.takeIf { it.isNotBlank() }
                    ?: allLines.getOrNull(activeIndex + 1)?.takeIf { it.isTranslated && it.start == activeLine.start }?.text?.takeIf { it.isNotBlank() }
                    ?: (if (activeLine.isTranslated) activeLine.text else null)

                // Past line
                val pastLine = (activeIndex - 1 downTo 0).asSequence()
                    .map { allLines[it] }
                    .firstOrNull { !it.isTranslated && it.text.isNotBlank() }

                // Upcoming lines
                val nextLines = (activeIndex + 1 until allLines.size).asSequence()
                    .map { allLines[it] }
                    .filter { !it.isTranslated && it.start > activeLine.start && it.text.isNotBlank() }
                    .take(2)
                    .toList()
                val next1Line = nextLines.getOrNull(0)
                val next2Line = nextLines.getOrNull(1)

                // 1. Past Line
                if (pastLine != null) {
                    views.setViewVisibility(R.id.widget_lyric_line_prev, View.VISIBLE)
                    views.setTextViewText(R.id.widget_timestamp_prev, formatTime(pastLine.start.toLong()))
                    views.setTextViewText(R.id.widget_text_prev, pastLine.text)
                    views.setOnClickPendingIntent(
                        R.id.widget_lyric_line_prev,
                        getSeekPendingIntent(context, pastLine.start.toLong(), SEEK_PREV_REQUEST_CODE)
                    )
                } else {
                    views.setViewVisibility(R.id.widget_lyric_line_prev, View.INVISIBLE)
                }

                // 2. Active Line
                views.setViewVisibility(R.id.widget_lyric_active_container, View.VISIBLE)
                views.setTextViewText(R.id.widget_timestamp_active, formatTime(activeLine.start.toLong()))
                views.setViewVisibility(R.id.widget_active_equalizer, if (isPlaying) View.VISIBLE else View.INVISIBLE)

                val activeText = activeLine.text
                val sb = SpannableString(activeText)
                if (activeLine.words != null && activeLine.words.isNotEmpty()) {
                    val hlChar = activeLine.words.findLast {
                        it.timeRange.first <= currentPos.toULong()
                    }?.charRange?.last?.plus(1) ?: 0
                    if (hlChar in 1..activeText.length) {
                        val activeColor = ContextCompat.getColor(context, R.color.md_theme_onPrimaryContainer)
                        val inactiveColor = ContextCompat.getColor(context, R.color.md_theme_outline)
                        sb.setSpan(StyleSpan(Typeface.BOLD), 0, hlChar, Spanned.SPAN_INCLUSIVE_INCLUSIVE)
                        sb.setSpan(ForegroundColorSpan(activeColor), 0, hlChar, Spanned.SPAN_INCLUSIVE_INCLUSIVE)
                        if (hlChar < activeText.length) {
                            sb.setSpan(ForegroundColorSpan(inactiveColor), hlChar, activeText.length, Spanned.SPAN_INCLUSIVE_INCLUSIVE)
                        }
                    } else {
                        sb.setSpan(StyleSpan(Typeface.BOLD), 0, activeText.length, Spanned.SPAN_INCLUSIVE_INCLUSIVE)
                    }
                } else {
                    sb.setSpan(StyleSpan(Typeface.BOLD), 0, activeText.length, Spanned.SPAN_INCLUSIVE_INCLUSIVE)
                }
                views.setTextViewText(R.id.widget_text_active, sb)
                views.setOnClickPendingIntent(
                    R.id.widget_lyric_active_container,
                    getSeekPendingIntent(context, activeLine.start.toLong(), SEEK_ACTIVE_REQUEST_CODE)
                )

                // Translation subtitle
                if (!activeTranslation.isNullOrBlank()) {
                    val formattedTl = if (activeTranslation.startsWith("RU", ignoreCase = true)) {
                        activeTranslation
                    } else {
                        "RU «$activeTranslation»"
                    }
                    views.setTextViewText(R.id.widget_translation_active, formattedTl)
                    views.setViewVisibility(R.id.widget_translation_active, View.VISIBLE)
                    views.setViewVisibility(R.id.widget_pill_translation, View.VISIBLE)
                    views.setTextViewText(R.id.widget_translation_toggle_text, "EN ⇄ RU • ON")
                } else {
                    views.setViewVisibility(R.id.widget_translation_active, View.GONE)
                    views.setViewVisibility(R.id.widget_pill_translation, View.GONE)
                }

                // 3. Upcoming line 1
                if (next1Line != null) {
                    views.setViewVisibility(R.id.widget_lyric_line_next1, View.VISIBLE)
                    views.setTextViewText(R.id.widget_timestamp_next1, formatTime(next1Line.start.toLong()))
                    views.setTextViewText(R.id.widget_text_next1, next1Line.text)
                    views.setOnClickPendingIntent(
                        R.id.widget_lyric_line_next1,
                        getSeekPendingIntent(context, next1Line.start.toLong(), SEEK_NEXT1_REQUEST_CODE)
                    )
                } else {
                    views.setViewVisibility(R.id.widget_lyric_line_next1, View.INVISIBLE)
                }

                // 4. Upcoming line 2
                if (next2Line != null) {
                    views.setViewVisibility(R.id.widget_lyric_line_next2, View.VISIBLE)
                    views.setTextViewText(R.id.widget_timestamp_next2, formatTime(next2Line.start.toLong()))
                    views.setTextViewText(R.id.widget_text_next2, next2Line.text)
                    views.setOnClickPendingIntent(
                        R.id.widget_lyric_line_next2,
                        getSeekPendingIntent(context, next2Line.start.toLong(), SEEK_NEXT2_REQUEST_CODE)
                    )
                } else {
                    views.setViewVisibility(R.id.widget_lyric_line_next2, View.GONE)
                }

                views.setTextViewText(R.id.widget_karaoke_sync_text, "KARAOKE SYNC 2.0")
            } else {
                // Fallback: unsynced lyrics or empty state
                val unsyncedList = service?.lyrics?.unsyncedText
                views.setViewVisibility(R.id.widget_lyric_line_prev, View.GONE)
                views.setViewVisibility(R.id.widget_translation_active, View.GONE)
                views.setViewVisibility(R.id.widget_pill_translation, View.GONE)
                views.setViewVisibility(R.id.widget_active_equalizer, View.GONE)

                if (!unsyncedList.isNullOrEmpty()) {
                    views.setTextViewText(R.id.widget_timestamp_active, "--:--")
                    views.setTextViewText(R.id.widget_text_active, unsyncedList.first().first)
                    if (unsyncedList.size > 1) {
                        views.setViewVisibility(R.id.widget_lyric_line_next1, View.VISIBLE)
                        views.setTextViewText(R.id.widget_timestamp_next1, "--:--")
                        views.setTextViewText(R.id.widget_text_next1, unsyncedList[1].first)
                    } else {
                        views.setViewVisibility(R.id.widget_lyric_line_next1, View.GONE)
                    }
                    if (unsyncedList.size > 2) {
                        views.setViewVisibility(R.id.widget_lyric_line_next2, View.VISIBLE)
                        views.setTextViewText(R.id.widget_timestamp_next2, "--:--")
                        views.setTextViewText(R.id.widget_text_next2, unsyncedList[2].first)
                    } else {
                        views.setViewVisibility(R.id.widget_lyric_line_next2, View.GONE)
                    }
                    views.setTextViewText(R.id.widget_karaoke_sync_text, "UNSYNCED LYRICS")
                } else {
                    views.setTextViewText(R.id.widget_timestamp_active, "--:--")
                    views.setTextViewText(
                        R.id.widget_text_active,
                        if (currentMediaItem != null) context.getString(R.string.no_lyric_found) else context.getString(R.string.app_name)
                    )
                    views.setViewVisibility(R.id.widget_lyric_line_next1, View.GONE)
                    views.setViewVisibility(R.id.widget_lyric_line_next2, View.GONE)
                    views.setTextViewText(R.id.widget_karaoke_sync_text, "KARAOKE SYNC 2.0")
                }
            }

            // Pulse dot
            views.setViewVisibility(R.id.widget_pulse_dot, if (isPlaying) View.VISIBLE else View.INVISIBLE)

            // Scrubber section
            val progressPercent = if (duration > 0) {
                ((currentPos.toDouble() / duration.toDouble()) * 100).toInt().coerceIn(0, 100)
            } else 0
            views.setProgressBar(R.id.widget_playback_progress, 100, progressPercent, false)
            views.setTextViewText(R.id.widget_time_current, formatTime(currentPos))
            views.setTextViewText(R.id.widget_time_total, if (duration > 0) formatTime(duration) else "--:--")

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }

    private fun getSeekPendingIntent(context: Context, timeMs: Long, requestCodeBase: Int): PendingIntent {
        val intent = Intent(context.packageName + ".SEEK_TO").apply {
            setPackage(context.packageName)
            putExtra("seekTo", timeMs)
        }
        val requestCode = (requestCodeBase * 31 + (timeMs and 0xFFFF).toInt()) and 0x7FFFFFFF
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    companion object {
        const val ACTION_KARAOKE_PLAY_PAUSE = "org.akanework.gramophone.ACTION_KARAOKE_PLAY_PAUSE"
        const val ACTION_KARAOKE_NEXT = "org.akanework.gramophone.ACTION_KARAOKE_NEXT"

        private const val OPEN_APP_REQUEST_CODE = 2000
        private const val PLAY_PAUSE_REQUEST_CODE = 2001
        private const val NEXT_REQUEST_CODE = 2002
        private const val SEEK_PREV_REQUEST_CODE = 2010
        private const val SEEK_ACTIVE_REQUEST_CODE = 2020
        private const val SEEK_NEXT1_REQUEST_CODE = 2030
        private const val SEEK_NEXT2_REQUEST_CODE = 2040

        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        private var cachedArtKey: String? = null
        private var cachedArtBitmap: Bitmap? = null
        private var currentLoadingKey: String? = null

        fun hasWidget(context: Context): Boolean {
            val awm = AppWidgetManager.getInstance(context) ?: return false
            return awm.getAppWidgetIds(ComponentName(context, KaraokeLyricsWidgetProvider::class.java)).isNotEmpty()
        }

        fun update(context: Context) {
            val awm = AppWidgetManager.getInstance(context) ?: return
            val ids = awm.getAppWidgetIds(ComponentName(context, KaraokeLyricsWidgetProvider::class.java))
            if (ids.isNotEmpty()) {
                KaraokeLyricsWidgetProvider().onUpdate(context, awm, ids)
            }
        }

        private fun formatTime(millis: Long): String {
            val totalSeconds = (millis.coerceAtLeast(0L) / 1000).toInt()
            val minutes = totalSeconds / 60
            val seconds = totalSeconds % 60
            return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
        }

        private fun getAudioQualityBadge(service: GramophonePlaybackService?): String {
            val format = service?.endedWorkaroundPlayer?.currentTracks?.getFirstSelectedTrackFormatByType(C.TRACK_TYPE_AUDIO)
                ?: service?.currentAudioSinkInputFormat
            val sampleRate = format?.sampleRate?.takeIf { it > 0 }
            val bitDepth = when (format?.pcmEncoding) {
                C.ENCODING_PCM_24BIT -> 24
                C.ENCODING_PCM_32BIT -> 32
                C.ENCODING_PCM_16BIT -> 16
                else -> null
            }
            if (bitDepth != null && sampleRate != null) {
                val sampleRateKhz = sampleRate / 1000
                return "$bitDepth-$sampleRateKhz"
            }
            if (sampleRate != null && sampleRate >= 88200) {
                return "HI-RES"
            }
            val mimeType = format?.sampleMimeType
            if (mimeType != null) {
                if (mimeType.contains("flac", ignoreCase = true)) return "FLAC"
                if (mimeType.contains("alac", ignoreCase = true)) return "ALAC"
                if (mimeType.contains("opus", ignoreCase = true)) return "OPUS"
                if (mimeType.contains("mp4", ignoreCase = true) || mimeType.contains("aac", ignoreCase = true)) return "AAC"
                if (mimeType.contains("mpeg", ignoreCase = true)) return "MP3"
            }
            return "24-96"
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

            mediaItem.mediaMetadata.artworkData?.let { data ->
                if (data.isNotEmpty()) {
                    try {
                        val raw = BitmapFactory.decodeByteArray(data, 0, data.size)
                        if (raw != null) {
                            val maxDim = (100 * context.resources.displayMetrics.density).toInt().coerceIn(96, 256)
                            val scaled = scaleDown(raw, maxDim)
                            val rounded = createRoundedBitmap(scaled, 14f, context.resources.displayMetrics.density)
                            cachedArtKey = key
                            cachedArtBitmap = rounded
                            return rounded
                        }
                    } catch (_: Exception) {}
                }
            }

            if (currentLoadingKey != key) {
                currentLoadingKey = key
                val appContext = context.applicationContext
                scope.launch(Dispatchers.IO) {
                    val bitmap = loadArtworkSync(appContext, mediaItem)
                    val rounded = bitmap?.let {
                        val maxDim = (100 * appContext.resources.displayMetrics.density).toInt().coerceIn(96, 256)
                        val scaled = scaleDown(it, maxDim)
                        createRoundedBitmap(scaled, 14f, appContext.resources.displayMetrics.density)
                    }
                    withContext(Dispatchers.Main) {
                        if (currentLoadingKey == key) {
                            cachedArtKey = key
                            cachedArtBitmap = rounded
                            update(appContext)
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
                        .submit(160, 160)
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
}
