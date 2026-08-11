/*
 * Copyright (C) 2024 Akane Foundation
 *
 * Gramophone is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Gramophone is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package org.akanework.gramophone.logic

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.bluetooth.BluetoothCodecStatus
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.media.AudioDeviceInfo
import android.media.audiofx.AudioEffect
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.Process
import android.os.SystemClock
import androidx.concurrent.futures.CallbackToFutureAdapter
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.DeviceInfo
import androidx.media3.common.Format
import androidx.media3.common.IllegalSeekPositionException
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.Rating
import androidx.media3.common.Timeline
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.Tracks
import androidx.media3.common.util.BitmapLoader
import androidx.media3.common.util.Log
import androidx.media3.common.util.Util.isBitmapFactorySupportedMimeType
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.source.LoadEventInfo
import androidx.media3.exoplayer.source.MediaLoadData
import androidx.media3.exoplayer.source.ShuffleOrder // Импорт для алгоритма-пустышки
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.util.EventLogger
import androidx.media3.extractor.mp3.Mp3Extractor
import androidx.media3.session.CacheBitmapLoader
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaBrowser
import androidx.media3.session.MediaConstants
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSession.MediaItemsWithStartPosition
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionError
import androidx.media3.session.SessionResult
import androidx.preference.PreferenceManager
import coil3.BitmapImage
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.akanework.gramophone.R
import org.akanework.gramophone.logic.ui.MeiZuLyricsMediaNotificationProvider
import org.akanework.gramophone.logic.ui.isManualNotificationUpdate
import org.akanework.gramophone.logic.utils.AfFormatInfo
import org.akanework.gramophone.logic.utils.AfFormatTracker
import org.akanework.gramophone.logic.utils.AudioTrackInfo
import org.akanework.gramophone.logic.utils.BtCodecInfo
import org.akanework.gramophone.logic.utils.Flags
import org.akanework.gramophone.logic.utils.LastPlayedManager
import org.akanework.gramophone.logic.utils.LrcUtils.LrcParserOptions
import org.akanework.gramophone.logic.utils.LrcUtils.extractAndParseLyrics
import org.akanework.gramophone.logic.utils.LrcUtils.loadAndParseLyricsFile
import org.akanework.gramophone.logic.utils.MonoAudioProcessor
import org.akanework.gramophone.logic.utils.ReplayGainAudioProcessor
import org.akanework.gramophone.logic.utils.ReplayGainUtil
import org.akanework.gramophone.logic.utils.SemanticLyrics
import org.akanework.gramophone.logic.utils.LyricsRepository
import org.akanework.gramophone.logic.utils.LyricsSource
import org.akanework.gramophone.logic.utils.exoplayer.EndedWorkaroundPlayer
import org.akanework.gramophone.logic.utils.exoplayer.GramophoneExtractorsFactory
import org.akanework.gramophone.logic.utils.exoplayer.GramophoneMediaSourceFactory
import org.akanework.gramophone.logic.utils.exoplayer.GramophoneRenderFactory
import org.akanework.gramophone.ui.LyricWidgetProvider
import org.akanework.gramophone.ui.MainActivity
import uk.akane.libphonograph.items.albumId
import kotlin.math.pow


/**
 * [GramophonePlaybackService] is a server service.
 * It's using exoplayer2 as its player backend.
 */
class GramophonePlaybackService : MediaLibraryService(), MediaSessionService.Listener,
    MediaLibraryService.MediaLibrarySession.Callback, Player.Listener, AnalyticsListener,
    SharedPreferences.OnSharedPreferenceChangeListener {

    companion object {
        private const val TAG = "GramoPlaybackService"
        const val NOTIFY_CHANNEL_ID = "serviceFgsError"
        const val NOTIFY_ID = 1
        private const val PENDING_INTENT_SESSION_ID = 0
        const val PENDING_INTENT_NOTIFY_ID = 1
        const val PENDING_INTENT_WIDGET_ID = 2
        const val SERVICE_SET_TIMER = "set_timer"
        const val SERVICE_QUERY_TIMER = "query_timer"
        const val SERVICE_GET_AUDIO_FORMAT = "get_audio_format"
        const val SERVICE_GET_LYRICS = "get_lyrics"
        const val SERVICE_TIMER_CHANGED = "changed_timer"
        var instanceForWidgetAndLyricsOnly: GramophonePlaybackService? = null
        private val trackDurationMap = java.util.concurrent.ConcurrentHashMap<String, Long>()

        fun updateTrackDuration(trackId: String, durationMs: Long) {
            if (trackId.isNotEmpty() && durationMs > 0L) {
                trackDurationMap[trackId] = durationMs
            }
        }

        fun getTrackDuration(trackId: String?): Long? {
            if (trackId == null) return null
            return trackDurationMap[trackId]
        }
    }

    private var lastSessionId = 0
    private val internalPlaybackThread =
        HandlerThread("ExoPlayer:Playback", Process.THREAD_PRIORITY_AUDIO)
    private var mediaSession: MediaLibrarySession? = null
    val endedWorkaroundPlayer
        get() = mediaSession?.player as EndedWorkaroundPlayer?
    private var controller: MediaBrowser? = null
    private val sendLyrics = Runnable { scheduleSendingLyrics(false) }
    var lyrics: SemanticLyrics? = null
        private set
    val syncedLyrics
        get() = lyrics as? SemanticLyrics.SyncedLyrics
    private lateinit var customCommands: List<CommandButton>
    private lateinit var handler: Handler
    private lateinit var playbackHandler: Handler
    private lateinit var nm: NotificationManagerCompat
    private lateinit var lastPlayedManager: LastPlayedManager
    private lateinit var prefs: SharedPreferences
    private var lastSentHighlightedLyric: String? = null
    private lateinit var afFormatTracker: AfFormatTracker
    private lateinit var rgAp: ReplayGainAudioProcessor
    private lateinit var monoAp: MonoAudioProcessor
    private var rgMode = 0 // 0 = disabled, 1 = track, 2 = album, 3 = smart
    private var updatedLyricAtLeastOnce = false
    private val downstreamFormat = hashSetOf<Pair<Any, Pair<Int, Format>>>()
    private val pendingDownstreamFormat = hashSetOf<Pair<Any, Pair<Int, Format>>>()
    private var afTrackFormat: Pair<Any, AfFormatInfo>? = null
    private val pendingAfTrackFormats = hashMapOf<Any, AfFormatInfo>()
    private var audioSinkInputFormat: Format? = null
    private var audioTrackInfo: AudioTrackInfo? = null
    private var audioTrackInfoCounter = 0
    private var audioTrackReleaseCounter = 0

    // only used for formats where this is significant for quality, but not in header (opus)
    private var bitrate: Int? = null
    private var btInfo: BtCodecInfo? = null
    private var proxy: BtCodecInfo.Companion.Proxy? = null
    private val scope = CoroutineScope(Dispatchers.Default)
    private val lyricsFetcher = CoroutineScope(Dispatchers.IO.limitedParallelism(1))
    private val bitrateFetcher = CoroutineScope(Dispatchers.IO.limitedParallelism(1))

    private fun getRepeatCommand() =
        when (controller!!.repeatMode) {
            Player.REPEAT_MODE_OFF -> customCommands[2]
            Player.REPEAT_MODE_ALL -> customCommands[3]
            Player.REPEAT_MODE_ONE -> customCommands[4]
            else -> throw IllegalArgumentException()
        }

    private fun getShufflingCommand() =
        if (controller!!.shuffleModeEnabled)
            customCommands[1]
        else
            customCommands[0]

    private val timer: Runnable = Runnable {
        if (timerPauseOnEnd) {
            endedWorkaroundPlayer!!.exoPlayer.pauseAtEndOfMediaItems = true
        } else {
            controller!!.pause()
        }
        timerDuration = null
    }
    private var timerPauseOnEnd = false
    private var timerDuration: Long? = null
        set(value) {
            field = value
            if (value != null && value > 0) {
                handler.postDelayed(timer, value - SystemClock.elapsedRealtime())
            } else {
                handler.removeCallbacks(timer)
            }
            mediaSession!!.broadcastCustomCommand(
                SessionCommand(SERVICE_TIMER_CHANGED, Bundle.EMPTY),
                Bundle.EMPTY
            )
        }

    private val seekReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val to =
                intent.extras?.getLong("seekTo", C.INDEX_UNSET.toLong()) ?: C.INDEX_UNSET.toLong()
            if (to != C.INDEX_UNSET.toLong())
                controller?.seekTo(to)
        }
    }

    private val btReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action.equals("android.bluetooth.a2dp.profile.action.CODEC_CONFIG_CHANGED") &&
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O /* before 8, only sbc was supported */
            ) {
                btInfo = BtCodecInfo.fromCodecConfig(
                    @SuppressLint("NewApi") IntentCompat.getParcelableExtra(
                        intent,
                        "android.bluetooth.extra.CODEC_STATUS",
                        BluetoothCodecStatus::class.java
                    )?.codecConfig
                )
                Log.d(TAG, "new bluetooth codec config $btInfo")
            }
        }
    }

    override fun onCreate() {
        Log.i(TAG, "+onCreate()")
        super.onCreate()
        instanceForWidgetAndLyricsOnly = this
        internalPlaybackThread.start()
        playbackHandler = Handler(internalPlaybackThread.looper)
        handler = Handler(Looper.getMainLooper())
        nm = NotificationManagerCompat.from(this)
        prefs = PreferenceManager.getDefaultSharedPreferences(applicationContext)
        setListener(this)
        setMediaNotificationProvider(
            MeiZuLyricsMediaNotificationProvider(this) { lastSentHighlightedLyric }
        )
        setForegroundServiceTimeoutMs(120000)
        setShowNotificationForEmptyPlayer(SHOW_NOTIFICATION_FOR_EMPTY_PLAYER_AFTER_STOP_OR_ERROR)
        if (mayThrowForegroundServiceStartNotAllowed()
            || mayThrowForegroundServiceStartNotAllowedMiui()
        ) {
            nm.createNotificationChannel(
                NotificationChannelCompat.Builder(
                    NOTIFY_CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_HIGH
                ).apply {
                    setName(getString(R.string.fgs_failed_channel))
                    setVibrationEnabled(true)
                    setVibrationPattern(longArrayOf(0L, 200L))
                    setLightsEnabled(false)
                    setShowBadge(false)
                    setSound(null, null)
                }.build()
            )
        } else if (nm.getNotificationChannel(NOTIFY_CHANNEL_ID) != null) {
            // for people who upgraded from S/S_V2 to newer version
            nm.deleteNotificationChannel(NOTIFY_CHANNEL_ID)
        }

        customCommands =
            listOf(
                CommandButton.Builder(CommandButton.ICON_SHUFFLE_OFF) // shuffle currently disabled, click will enable
                    .setDisplayName(getString(R.string.shuffle))
                    .setPlayerCommand(Player.COMMAND_SET_SHUFFLE_MODE, true)
                    .build(),
                CommandButton.Builder(CommandButton.ICON_SHUFFLE_ON) // shuffle currently enabled, click will disable
                    .setDisplayName(getString(R.string.shuffle))
                    .setPlayerCommand(Player.COMMAND_SET_SHUFFLE_MODE, false)
                    .build(),
                CommandButton.Builder(CommandButton.ICON_REPEAT_OFF) // repeat currently disabled, click will repeat all
                    .setDisplayName(getString(R.string.repeat_mode))
                    .setPlayerCommand(Player.COMMAND_SET_REPEAT_MODE, Player.REPEAT_MODE_ALL)
                    .build(),
                CommandButton.Builder(CommandButton.ICON_REPEAT_ALL) // repeat all currently enabled, click will repeat one
                    .setDisplayName(getString(R.string.repeat_mode))
                    .setPlayerCommand(Player.COMMAND_SET_REPEAT_MODE, Player.REPEAT_MODE_ONE)
                    .build(),
                CommandButton.Builder(CommandButton.ICON_REPEAT_ONE) // repeat one currently enabled, click will disable
                    .setDisplayName(getString(R.string.repeat_mode))
                    .setPlayerCommand(Player.COMMAND_SET_REPEAT_MODE, Player.REPEAT_MODE_OFF)
                    .build(),
            )
        afFormatTracker = AfFormatTracker(this, playbackHandler, handler)
        afFormatTracker.formatChangedCallback = { format, period ->
            if (period != null) {
                handler.post {
                    val currentPeriod = controller?.currentPeriodIndex?.takeIf {
                        it != C.INDEX_UNSET &&
                                (controller?.currentTimeline?.periodCount ?: 0) > it
                    }
                        ?.let { controller!!.currentTimeline.getUidOfPeriod(it) }
                    if (currentPeriod != period) {
                        if (format != null) {
                            pendingAfTrackFormats[period] = format
                        } else {
                            pendingAfTrackFormats.remove(period)
                        }
                    } else {
                        afTrackFormat = format?.let { period to it }
                        mediaSession?.broadcastCustomCommand(
                            SessionCommand(SERVICE_GET_AUDIO_FORMAT, Bundle.EMPTY),
                            Bundle.EMPTY
                        )
                    }
                }
            } else {
                Log.e(TAG, "mediaPeriodId is NULL in formatChangedCallback!!")
            }
        }
        rgAp = ReplayGainAudioProcessor()
        monoAp = MonoAudioProcessor()
        prefs.registerOnSharedPreferenceChangeListener(this)
        onSharedPreferenceChanged(prefs, null) // read initial values

        // --- 1. ДОБАВЛЯЕМ АГРЕССИВНЫЕ ТАЙМАУТЫ ДЛЯ СЕТИ ---
        val httpDataSourceFactory = androidx.media3.datasource.DefaultHttpDataSource.Factory()
            .setConnectTimeoutMs(45000)
            .setReadTimeoutMs(45000)

        val dataSourceFactory = androidx.media3.datasource.DefaultDataSource.Factory(
            this,
            httpDataSourceFactory
        )

        // --- 2. ОПТИМИЗИРОВАННЫЙ БУФЕР ДЛЯ МНОГОПОТОЧНОГО M4A/AAC ИСКЛЮЧАЕТ PAUSES СБОРЩИКА МУСОРА (GC) ---
        val customLoadControl = androidx.media3.exoplayer.DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                /* minBufferMs = */ 180000,                       // 3 минуты буфера (~3.6 МБ в RAM) — без тормозов GC
                /* maxBufferMs = */ 600000,                       // 10 минут макс буфер
                /* bufferForPlaybackMs = */ 2500,                // Мгновенный старт (< 100мс)
                /* bufferForPlaybackAfterRebufferMs = */ 4000
            )
            .setBackBuffer(
                /* backBufferDurationMs = */ 120000,             // 2 минуты прошлых байт в RAM для отмотки
                /* retainBackBufferFromKeyframe = */ true
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        val player = EndedWorkaroundPlayer(
            ExoPlayer.Builder(
                this,
                GramophoneRenderFactory(
                    this, rgAp, monoAp, this::onAudioSinkInputFormatChanged,
                    afFormatTracker::setAudioSink,
                    Flags.OFFLOAD && prefs.getStringStrict("offload", "0")?.toIntOrNull() == 3,
                )
                    .setPcmEncodingRestrictionLifted(true)
                    .setEnableDecoderFallback(true)
                    .setEnableAudioTrackPlaybackParams(true)
                    .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON),
                GramophoneMediaSourceFactory(this,
                    dataSourceFactory,
                    GramophoneExtractorsFactory().also {
                        it.setConstantBitrateSeekingEnabled(true)
                        it.setConstantBitrateSeekingAlwaysEnabled(true)
                        it.setMatroskaExtractorFlags(androidx.media3.extractor.mkv.MatroskaExtractor.FLAG_DISABLE_SEEK_FOR_CUES)
                        it.setAdtsExtractorFlags(
                            androidx.media3.extractor.ts.AdtsExtractor.FLAG_ENABLE_CONSTANT_BITRATE_SEEKING or
                            androidx.media3.extractor.ts.AdtsExtractor.FLAG_ENABLE_CONSTANT_BITRATE_SEEKING_ALWAYS
                        )
                        if (prefs.getBooleanStrict("mp3_index_seeking", false))
                            it.setMp3ExtractorFlags(androidx.media3.extractor.mp3.Mp3Extractor.FLAG_ENABLE_INDEX_SEEKING)
                    }
                )
            )
                .setLoadControl(customLoadControl)
                .setWakeMode(C.WAKE_MODE_LOCAL)
                .setAudioAttributes(
                    AudioAttributes
                        .Builder()
                        .setUsage(C.USAGE_MEDIA)
                        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                        .build(),
                    prefs.getBoolean("resume_after_call", true) // 🔥 Берем из настроек!
                )
                .setHandleAudioBecomingNoisy(prefs.getBoolean("resume_after_call", true)) // 🔥 И шум наушников тоже
                .setTrackSelector(DefaultTrackSelector(this).apply {
                    setParameters(
                        buildUponParameters()
                            .setAllowInvalidateSelectionsOnRendererCapabilitiesChange(true)
                            .setAudioOffloadPreferences(
                                TrackSelectionParameters.AudioOffloadPreferences.Builder()
                                    .apply {
                                        val config = prefs.getStringStrict("offload", "0")?.toIntOrNull()
                                        if (config != null && config > 0 && Flags.OFFLOAD) {
                                            rgAp.setOffloadEnabled(true)
                                            setAudioOffloadMode(TrackSelectionParameters.AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_ENABLED)
                                            setIsGaplessSupportRequired(config == 2)
                                        }
                                    }
                                    .build()))
                })
                .setPlaybackLooper(internalPlaybackThread.looper)
                .build()
        )

        player.exoPlayer.addListener(object : androidx.media3.common.Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                val stateStr = when (playbackState) {
                    androidx.media3.common.Player.STATE_BUFFERING -> "BUFFERING"
                    androidx.media3.common.Player.STATE_READY -> "READY"
                    androidx.media3.common.Player.STATE_ENDED -> "ENDED"
                    androidx.media3.common.Player.STATE_IDLE -> "IDLE"
                    else -> "UNKNOWN"
                }
                org.akanework.gramophone.logic.utils.PlaybackLogger.log("EXO_STATE", "State: $stateStr | Pos: ${player.currentPosition}ms")
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                org.akanework.gramophone.logic.utils.PlaybackLogger.log("EXO_ERROR", "Error [${error.errorCodeName}]: ${error.message} | Cause: ${error.cause?.message}")
            }
        })

        org.akanework.gramophone.logic.utils.SmartPlaybackManager.init(this, player.exoPlayer)

        player.exoPlayer.addAnalyticsListener(object : androidx.media3.exoplayer.analytics.AnalyticsListener {
            override fun onBandwidthEstimate(
                eventTime: androidx.media3.exoplayer.analytics.AnalyticsListener.EventTime,
                totalLoadTimeMs: Int,
                totalBytesLoaded: Long,
                bitrateEstimate: Long
            ) {
                val kbps = bitrateEstimate / 1000
                val totalKb = totalBytesLoaded / 1024
                org.akanework.gramophone.logic.utils.PlaybackLogger.log("BANDWIDTH", "Estimate: ${kbps} kbps | Loaded: ${totalKb} KB in ${totalLoadTimeMs}ms")
            }

            override fun onAudioUnderrun(
                eventTime: androidx.media3.exoplayer.analytics.AnalyticsListener.EventTime,
                bufferSize: Int,
                bufferSizeMs: Long,
                elapsedSinceLastFeedMs: Long
            ) {
                org.akanework.gramophone.logic.utils.PlaybackLogger.log("AUDIO_UNDERRUN", "AudioTrack Underrun! BufferSize: ${bufferSize}b (${bufferSizeMs}ms) | ElapsedSinceFeed: ${elapsedSinceLastFeedMs}ms")
            }
        })

        player.exoPlayer.addAnalyticsListener(EventLogger())
        player.exoPlayer.addAnalyticsListener(afFormatTracker)
        player.exoPlayer.addAnalyticsListener(this)

        // 🔥 Устанавливаем алгоритм-пустышку (линейный порядок), чтобы плеер не мешал физический App-Level Shuffle
        player.exoPlayer.setShuffleOrder(ShuffleOrder.UnshuffledShuffleOrder(0))

        lastPlayedManager = LastPlayedManager(this, player)
        lastPlayedManager.allowSavingState = false

        mediaSession =
            MediaLibrarySession
                .Builder(this, player, this)
                .setBitmapLoader(CacheBitmapLoader(object : BitmapLoader {
                    private val limit by lazy { MediaSession.getBitmapDimensionLimit(this@GramophonePlaybackService) }

                    override fun decodeBitmap(data: ByteArray): ListenableFuture<Bitmap> {
                        return CallbackToFutureAdapter.getFuture { completer ->
                            imageLoader.enqueue(
                                ImageRequest.Builder(this@GramophonePlaybackService)
                                    .data(data)
                                    .memoryCacheKey(data.hashCode().toString())
                                    .size(limit, limit)
                                    .allowHardware(false)
                                    .target(
                                        onStart = { _ -> },
                                        onSuccess = { result ->
                                            completer.set((result as BitmapImage).bitmap)
                                        },
                                        onError = { _ ->
                                            completer.setException(Exception("coil onError called for byte array"))
                                        }
                                    )
                                    .build())
                                .also {
                                    completer.addCancellationListener(
                                        { it.dispose() },
                                        ContextCompat.getMainExecutor(this@GramophonePlaybackService)
                                    )
                                }
                            "coil load for ${data.hashCode()}"
                        }
                    }

                    override fun loadBitmap(uri: Uri): ListenableFuture<Bitmap> {
                        val originalUriStr = uri.toString()
                        val finalUri = if (originalUriStr.startsWith("/")) {
                            Uri.parse("http://185.196.41.31$originalUriStr")
                        } else {
                            uri
                        }

                        return CallbackToFutureAdapter.getFuture { completer ->
                            imageLoader.enqueue(
                                ImageRequest.Builder(this@GramophonePlaybackService)
                                    .data(finalUri)
                                    .size(limit, limit)
                                    .allowHardware(false)
                                    .target(
                                        onStart = { _ -> },
                                        onSuccess = { result ->
                                            completer.set((result as BitmapImage).bitmap)
                                        },
                                        onError = { _ ->
                                            completer.setException(Exception("coil onError called (normal if no album art exists)"))
                                        }
                                    )
                                    .build())
                                .also {
                                    completer.addCancellationListener(
                                        { it.dispose() },
                                        ContextCompat.getMainExecutor(this@GramophonePlaybackService)
                                    )
                                }
                            "coil load for $finalUri"
                        }
                    }

                    override fun supportsMimeType(mimeType: String): Boolean {
                        return isBitmapFactorySupportedMimeType(mimeType)
                    }

                    override fun loadBitmapFromMetadata(metadata: MediaMetadata): ListenableFuture<Bitmap>? {
                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                            return super.loadBitmapFromMetadata(metadata)
                        }
                        return metadata.artworkUri?.let { loadBitmap(it) }
                    }
                }))
                .setSessionActivity(
                    PendingIntent.getActivity(
                        this,
                        PENDING_INTENT_SESSION_ID,
                        Intent(this, MainActivity::class.java),
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                    )
                )
                .setSystemUiPlaybackResumptionOptIn(Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                .build()
        addSession(mediaSession!!)
        controller = MediaBrowser.Builder(this, mediaSession!!.token).buildAsync().get()
        controller!!.addListener(this)
        if (controller!!.audioSessionId != C.AUDIO_SESSION_ID_UNSET) {
            onAudioSessionIdChanged(controller!!.audioSessionId)
        }
        ContextCompat.registerReceiver(
            this,
            seekReceiver,
            IntentFilter("$packageName.SEEK_TO"),
            @SuppressLint("WrongConstant") // why is this needed?
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        ContextCompat.registerReceiver(
            this,
            btReceiver,
            IntentFilter("android.bluetooth.a2dp.profile.action.CODEC_CONFIG_CHANGED"),
            @SuppressLint("WrongConstant") // why is this needed?
            ContextCompat.RECEIVER_EXPORTED
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O /* before 8, only sbc was supported */) {
            proxy = BtCodecInfo.getCodec(this) {
                Log.d(TAG, "first bluetooth codec config $btInfo")
                btInfo = it
                mediaSession?.broadcastCustomCommand(
                    SessionCommand(SERVICE_GET_AUDIO_FORMAT, Bundle.EMPTY),
                    Bundle.EMPTY
                )
            }
        }
        scope.launch {
            lastPlayedManager.restore { items, factory ->
                if (mediaSession == null) return@restore
                if (items != null) {
                    if (endedWorkaroundPlayer?.nextShuffleOrder != null)
                        throw IllegalStateException("shuffleFactory was found orphaned")

                    // Если у нас восстанавливается старый шаффл, применяем его, иначе оставляем пустышку
                    if (factory != null) {
                        endedWorkaroundPlayer?.nextShuffleOrder = factory.toFactory()
                    }

                    try {
                        mediaSession?.player?.setMediaItems(
                            items.mediaItems, items.startIndex, items.startPositionMs
                        )
                    } catch (e: IllegalSeekPositionException) {
                        try {
                            mediaSession?.player?.setMediaItems(items.mediaItems)
                            Log.w(TAG, "failed to restore index", e)
                        } catch (_: IllegalSeekPositionException) {
                            Log.e(TAG, "failed to restore", e)
                            endedWorkaroundPlayer?.nextShuffleOrder = null
                        }
                    }
                    if (endedWorkaroundPlayer?.nextShuffleOrder != null)
                        throw IllegalStateException("shuffleFactory was not consumed during restore")
                    if (mediaSession?.connectedControllers?.find {
                            it.connectionHints
                                .getBoolean("PrepareWhenReady", false)
                        } != null) {
                        handler.post { mediaSession?.player?.prepare() }
                    }
                }
                lastPlayedManager.allowSavingState = true
            }
        }
        if (Flags.FAVORITE_SONGS) {
            scope.launch {
                gramophoneApplication.reader.songListFlow.collect { list ->
                    withContext(Dispatchers.Main + NonCancellable) {
                        val cmi = controller?.currentMediaItem?.mediaId ?: return@withContext
                        list.find { it.mediaId == cmi }?.let {
                            controller!!.replaceMediaItem(controller!!.currentMediaItemIndex, it)
                        }
                    }
                }
            }
        }
        Log.i(TAG, "-onCreate()")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        var extras = intent?.extras
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            extras = extras?.deepCopy()
        } else {
            if (extras != null) {
                for (i in extras.keySet()) {
                    @Suppress("deprecation") extras.get(i)
                }
            }
        }
        Log.i(TAG, "onStartCommand(): $intent, ${extras?.toString()}")
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onSetRating(
        session: MediaSession,
        controller: MediaSession.ControllerInfo,
        mediaId: String,
        rating: Rating
    ): ListenableFuture<SessionResult> {
        return Futures.immediateFuture(SessionResult(SessionError.ERROR_NOT_SUPPORTED))
    }

    override fun onSetRating(
        session: MediaSession,
        controller: MediaSession.ControllerInfo,
        rating: Rating
    ): ListenableFuture<SessionResult> {
        val mediaItemId =
            this.controller?.currentMediaItem?.mediaId ?: return Futures.immediateFuture(
                SessionResult(SessionError.ERROR_INVALID_STATE)
            )
        return onSetRating(session, controller, mediaItemId, rating)
    }

    override fun onDestroy() {
        Log.i(TAG, "+onDestroy()")
        instanceForWidgetAndLyricsOnly = null
        unregisterReceiver(seekReceiver)
        unregisterReceiver(btReceiver)
        prefs.unregisterOnSharedPreferenceChangeListener(this)
        lastPlayedManager.save()
        scope.cancel()
        mediaSession!!.player.stop()
        handler.removeCallbacks(timer)
        mediaSession!!.setOptOutOfMediaButtonPlaybackResumption(controller!!.currentTimeline.isEmpty)
        proxy?.let {
            it.adapter.closeProfileProxy(BluetoothProfile.A2DP, it.a2dp)
        }
        controller!!.release()
        controller = null
        mediaSession!!.release()
        mediaSession!!.player.release()
        mediaSession = null
        broadcastAudioSessionClose()
        LyricWidgetProvider.update(this)
        internalPlaybackThread.quitSafely()
        super.onDestroy()
        Log.i(TAG, "-onDestroy()")
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? =
        mediaSession

    override fun onConnect(session: MediaSession, controller: MediaSession.ControllerInfo)
            : MediaSession.ConnectionResult {
        Log.i(TAG, "onConnect(): $controller")
        val builder = MediaSession.ConnectionResult.AcceptedResultBuilder(session)
        val availableSessionCommands =
            MediaSession.ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS.buildUpon()
        if (session.isMediaNotificationController(controller)
            || session.isAutoCompanionController(controller)
            || session.isAutomotiveController(controller)
        ) {
            if (this.controller?.currentTimeline?.isEmpty == false) {
                builder.setMediaButtonPreferences(
                    ImmutableList.of(
                        getRepeatCommand(),
                        getShufflingCommand()
                    )
                )
            }
        }
        if (controller.connectionHints.getBoolean("PrepareWhenReady", false) &&
            this.controller?.currentTimeline?.isEmpty == false
        ) {
            handler.post { this.controller?.prepare() }
        }
        availableSessionCommands.add(SessionCommand(SERVICE_SET_TIMER, Bundle.EMPTY))
        availableSessionCommands.add(SessionCommand(SERVICE_QUERY_TIMER, Bundle.EMPTY))
        availableSessionCommands.add(SessionCommand(SERVICE_GET_LYRICS, Bundle.EMPTY))
        availableSessionCommands.add(SessionCommand(SERVICE_GET_AUDIO_FORMAT, Bundle.EMPTY))
        return builder.setAvailableSessionCommands(availableSessionCommands.build()).build()
    }

    override fun onPostConnect(session: MediaSession, controller: MediaSession.ControllerInfo) {
        Log.i(TAG, "onPostConnect(): $controller")
        session.sendCustomCommand(
            controller,
            SessionCommand(SERVICE_GET_LYRICS, Bundle.EMPTY),
            Bundle.EMPTY
        )
        session.sendCustomCommand(
            controller,
            SessionCommand(SERVICE_GET_AUDIO_FORMAT, Bundle.EMPTY),
            Bundle.EMPTY
        )
    }

    override fun onDisconnected(session: MediaSession, controller: MediaSession.ControllerInfo) {
        Log.i(TAG, "onDisconnected(): $controller")
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String?) {
        var restart = false
        if (key == null || key == "mono_audio") {
            monoAp.isMonoEnabled = prefs.getBoolean("mono_audio", false)
        }
        if (key == null || key == "rg_mode") {
            rgMode = prefs.getStringStrict("rg_mode", "0")!!.toInt()
            restart = !computeRgMode(true)
        }
        if (key == null || key == "rg_drc") {
            val drc = prefs.getBooleanStrict("rg_drc", true)
            restart = !rgAp.setReduceGain(!drc) || restart
        }
        if (key == null || key == "rg_rg_gain") {
            val rgGain = prefs.getIntStrict("rg_rg_gain", 19)
            restart = !rgAp.setRgGain(rgGain - 15) || restart
        }
        if (key == null || key == "rg_no_rg_gain") {
            val nonRgGain = prefs.getIntStrict("rg_no_rg_gain", 0)
            restart = !rgAp.setNonRgGain(-nonRgGain) || restart
        }
        if (key == null || key == "rg_boost_gain") {
            val boostGain = prefs.getIntStrict("rg_boost_gain", 0)
            restart = !rgAp.setBoostGain(boostGain) || restart
        }
        if (restart) {
            controller?.stop()
            controller?.prepare()
        }
    }

    private fun computeRgMode(force: Boolean): Boolean {
        return rgAp.setMode(
            when (rgMode) {
                0 -> ReplayGainUtil.Mode.None
                1 -> ReplayGainUtil.Mode.Track
                2 -> ReplayGainUtil.Mode.Album
                3 -> {
                    val item = controller?.currentMediaItem
                    val idx = controller?.currentMediaItemIndex ?: 0
                    val count = controller?.mediaItemCount ?: 0
                    val next = if (idx + 1 >= count) null else
                        controller?.getMediaItemAt(idx + 1)
                    val prev = if (idx - 1 < 0 || count == 0) null else
                        controller?.getMediaItemAt(idx - 1)
                    if (item != null && (item.mediaMetadata.albumId == next?.mediaMetadata?.albumId ||
                                item.mediaMetadata.albumId == prev?.mediaMetadata?.albumId)
                    )
                        ReplayGainUtil.Mode.Album
                    else ReplayGainUtil.Mode.Track
                }

                else -> throw IllegalArgumentException("invalid rg mode $rgMode")
            }, !force
        )
    }

    override fun onAudioSessionIdChanged(audioSessionId: Int) {
        if (audioSessionId != lastSessionId) {
            broadcastAudioSessionClose()
            lastSessionId = audioSessionId
            broadcastAudioSession()
        }
    }

    private fun broadcastAudioSession() {
        if (lastSessionId != 0) {
            Log.i(TAG, "broadcast audio session open: $lastSessionId")
            sendBroadcast(Intent(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION).apply {
                putExtra(AudioEffect.EXTRA_PACKAGE_NAME, packageName)
                putExtra(AudioEffect.EXTRA_AUDIO_SESSION, lastSessionId)
                putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC)
            })
        } else {
            Log.e(TAG, "session id is 0? why????? THIS MIGHT BREAK EQUALIZER")
        }
    }

    private fun broadcastAudioSessionClose() {
        if (lastSessionId != 0) {
            Log.i(TAG, "broadcast audio session close: $lastSessionId")
            sendBroadcast(Intent(AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION).apply {
                putExtra(AudioEffect.EXTRA_PACKAGE_NAME, packageName)
                putExtra(AudioEffect.EXTRA_AUDIO_SESSION, lastSessionId)
            })
            lastSessionId = 0
        }
    }

    override fun onCustomCommand(
        session: MediaSession,
        controller: MediaSession.ControllerInfo,
        customCommand: SessionCommand,
        args: Bundle
    ): ListenableFuture<SessionResult> {
        return Futures.immediateFuture(
            when (customCommand.customAction) {
                SERVICE_SET_TIMER -> {
                    val duration = customCommand.customExtras.getInt("duration")
                    val pauseOnEnd = customCommand.customExtras.getBoolean("pauseOnEnd")
                    if (duration > 0) {
                        timerPauseOnEnd = pauseOnEnd
                        timerDuration = SystemClock.elapsedRealtime() + duration
                    } else {
                        timerDuration = null
                        this.endedWorkaroundPlayer!!.exoPlayer.pauseAtEndOfMediaItems = pauseOnEnd
                    }
                    SessionResult(SessionResult.RESULT_SUCCESS)
                }

                SERVICE_QUERY_TIMER -> {
                    SessionResult(SessionResult.RESULT_SUCCESS).also {
                        timerDuration?.let { td ->
                            it.extras.putInt(
                                "duration",
                                (td - SystemClock.elapsedRealtime()).toInt()
                            )
                            it.extras.putBoolean("pauseOnEnd", timerPauseOnEnd)
                        } ?: it.extras.putBoolean(
                            "pauseOnEnd",
                            this.endedWorkaroundPlayer!!.exoPlayer.pauseAtEndOfMediaItems
                        )
                    }
                }

                SERVICE_GET_AUDIO_FORMAT -> {
                    SessionResult(SessionResult.RESULT_SUCCESS).also { res ->
                        if (downstreamFormat.isNotEmpty()) {
                            res.extras.putParcelableArrayList(
                                "file_format",
                                ArrayList(downstreamFormat.map {
                                    Bundle().apply {
                                        putInt("type", it.second.first)
                                        val bitrate = bitrate
                                        val format = if (it.second.first == C.TRACK_TYPE_AUDIO &&
                                            bitrate != null &&
                                            it.second.second.sampleMimeType == MimeTypes.AUDIO_OPUS
                                        ) {
                                            it.second.second.buildUpon().setAverageBitrate(bitrate)
                                                .build()
                                        } else it.second.second
                                        putBundle("format", format.toBundle())
                                        putBundle("rg", ReplayGainUtil.parse(format).toBundle())
                                    }
                                })
                            )
                        }
                        res.extras.putBundle("sink_format", audioSinkInputFormat?.toBundle())
                        res.extras.putParcelable("track_format", audioTrackInfo)
                        res.extras.putParcelable("hal_format", afTrackFormat?.second)
                        if (afFormatTracker.format?.routedDeviceType == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP) {
                            res.extras.putParcelable("bt", btInfo)
                        }
                    }
                }

                SERVICE_GET_LYRICS -> {
                    SessionResult(SessionResult.RESULT_SUCCESS).also {
                        it.extras.putParcelable("lyrics", lyrics)
                    }
                }

                else -> {
                    SessionResult(SessionError.ERROR_BAD_VALUE)
                }
            })
    }

    override fun onPlayWhenReadyChanged(
        playWhenReady: Boolean,
        reason: @Player.PlayWhenReadyChangeReason Int
    ) {
        if (reason == Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM) {
            this.endedWorkaroundPlayer?.exoPlayer?.pauseAtEndOfMediaItems = false
        }
    }

    override fun onPlaybackResumption(
        mediaSession: MediaSession,
        controller: MediaSession.ControllerInfo,
        isForPlayback: Boolean
    ): ListenableFuture<MediaItemsWithStartPosition> {
        val settable = SettableFuture.create<MediaItemsWithStartPosition>()
        val job = scope.launch {
            lastPlayedManager.restore { items, factory ->
                if (items == null) {
                    settable.setException(
                        NullPointerException(
                            "null MediaItemsWithStartPosition, see former logs for root cause"
                        ).also { Log.e(TAG, Log.getThrowableString(it)!!) }
                    )
                } else {
                    if (endedWorkaroundPlayer?.nextShuffleOrder != null)
                        throw IllegalStateException("shuffleFactory was found orphaned")
                    if (isForPlayback && items.mediaItems.isNotEmpty()) {

                        // Если восстанавливается старый шаффл, применяем, иначе оставляем пустышку
                        if (factory != null) {
                            endedWorkaroundPlayer?.nextShuffleOrder = factory.toFactory()
                        }

                        settable.set(items)
                        if (endedWorkaroundPlayer?.nextShuffleOrder != null)
                            throw IllegalStateException("shuffleFactory was not consumed during resumption")
                    } else if (items.mediaItems.isNotEmpty()) {
                        var theItem = items.mediaItems[items.startIndex]
                        if (theItem.mediaMetadata.durationMs != null &&
                            theItem.mediaMetadata.durationMs!! > 0 &&
                            items.startPositionMs != C.TIME_UNSET
                        ) {
                            theItem = theItem.buildUpon()
                                .setMediaMetadata(
                                    theItem.mediaMetadata.buildUpon()
                                        .setExtras(Bundle(theItem.mediaMetadata.extras).apply {
                                            if (items.startPositionMs == 0L) {
                                                putInt(
                                                    MediaConstants.EXTRAS_KEY_COMPLETION_STATUS,
                                                    MediaConstants.EXTRAS_VALUE_COMPLETION_STATUS_NOT_PLAYED
                                                )
                                            } else if (items.startPositionMs != theItem.mediaMetadata.durationMs!!) {
                                                putInt(
                                                    MediaConstants.EXTRAS_KEY_COMPLETION_STATUS,
                                                    MediaConstants.EXTRAS_VALUE_COMPLETION_STATUS_PARTIALLY_PLAYED
                                                )
                                                putDouble(
                                                    MediaConstants.EXTRAS_KEY_COMPLETION_PERCENTAGE,
                                                    (items.startPositionMs.toDouble() /
                                                            theItem.mediaMetadata.durationMs!!)
                                                        .coerceIn(0.0, 1.0)
                                                )
                                            } else {
                                                putInt(
                                                    MediaConstants.EXTRAS_KEY_COMPLETION_STATUS,
                                                    MediaConstants.EXTRAS_VALUE_COMPLETION_STATUS_FULLY_PLAYED
                                                )
                                            }
                                        }).build()
                                ).build()
                        }
                        settable.set(
                            MediaItemsWithStartPosition(
                                listOf(theItem),
                                0, items.startPositionMs
                            )
                        )
                    } else {
                        settable.set(items)
                    }
                }
            }
        }
        job.invokeOnCompletion { t ->
            if (t is CancellationException && !settable.isDone) {
                settable.setException(t)
            }
        }
        return settable
    }

    override fun onTracksChanged(tracks: Tracks) {
        if (!tracks.isEmpty && !tracks.isTypeSelected(C.TRACK_TYPE_AUDIO)) {
            Log.e(TAG, "No audio track selected: $tracks")
            controller!!.stop()
        }

        val mediaItem = controller?.currentMediaItem
        lyricsFetcher.launch {
            val trim = prefs.getBoolean("trim_lyrics", true)
            val multiLine = prefs.getBoolean("lyric_multiline", false)
            val options = LrcParserOptions(
                trim = trim, multiLine = multiLine,
                errorText = getString(R.string.failed_to_parse_lyric)
            )
            val format = tracks.getFirstSelectedTrackFormatByType(C.TRACK_TYPE_AUDIO)
            var lrc: SemanticLyrics? = null
            if (format != null) {
                lrc = loadAndParseLyricsFile(
                    mediaItem?.getFile(),
                    format.sampleMimeType, options
                )
                if (lrc == null) {
                    val trackMetadata = format.metadata
                    if (trackMetadata != null) {
                        lrc = extractAndParseLyrics(
                            format.sampleRate.takeIf { it != Format.NO_VALUE } ?: 0,
                            format.sampleMimeType,
                            trackMetadata,
                            options).firstOrNull()
                    }
                }
            }
            if (lrc == null && mediaItem != null) {
                val title = mediaItem.mediaMetadata.title?.toString()
                val artist = mediaItem.mediaMetadata.artist?.toString()
                val durationMs = mediaItem.mediaMetadata.durationMs ?: 0L
                val result = LyricsRepository.fetchLyrics(
                    context = this@GramophonePlaybackService,
                    file = mediaItem.getFile(),
                    mimeType = format?.sampleMimeType,
                    sampleRate = format?.sampleRate?.takeIf { it != Format.NO_VALUE } ?: 0,
                    metadata = format?.metadata,
                    artist = artist,
                    title = title,
                    durationMs = durationMs,
                    preferredSource = LyricsSource.ALL,
                    options = options
                )
                lrc = result?.lyrics
            }
            withContext(Dispatchers.Main) {
                mediaSession?.let {
                    lyrics = lrc
                    it.broadcastCustomCommand(
                        SessionCommand(SERVICE_GET_LYRICS, Bundle.EMPTY),
                        Bundle.EMPTY
                    )
                    scheduleSendingLyrics(true)
                }
            }
        }
    }

    override fun onAudioTrackInitialized(
        eventTime: AnalyticsListener.EventTime,
        audioTrackConfig: AudioSink.AudioTrackConfig
    ) {
        audioTrackInfoCounter++
        audioTrackInfo = AudioTrackInfo.fromMedia3AudioTrackConfig(audioTrackConfig)
        mediaSession?.broadcastCustomCommand(
            SessionCommand(SERVICE_GET_AUDIO_FORMAT, Bundle.EMPTY),
            Bundle.EMPTY
        )
    }

    override fun onAudioTrackReleased(
        eventTime: AnalyticsListener.EventTime,
        audioTrackConfig: AudioSink.AudioTrackConfig
    ) {
        if (++audioTrackReleaseCounter == audioTrackInfoCounter) {
            audioTrackInfo = null
            mediaSession?.broadcastCustomCommand(
                SessionCommand(SERVICE_GET_AUDIO_FORMAT, Bundle.EMPTY),
                Bundle.EMPTY
            )
        }
    }

    override fun onDownstreamFormatChanged(
        eventTime: AnalyticsListener.EventTime,
        mediaLoadData: MediaLoadData
    ) {
        if (eventTime.mediaPeriodId == null) {
            Log.e(TAG, "mediaPeriodId is NULL in onDownstreamFormatChanged()!!")
            return
        }
        val currentPeriod = controller?.currentPeriodIndex?.takeIf {
            it != C.INDEX_UNSET &&
                    (controller?.currentTimeline?.periodCount ?: 0) > it
        }
            ?.let { controller!!.currentTimeline.getUidOfPeriod(it) }
        val item = eventTime.mediaPeriodId!!.periodUid to
                (mediaLoadData.trackType to mediaLoadData.trackFormat!!)
        if (currentPeriod != item.first) {
            pendingDownstreamFormat += item
        } else {
            downstreamFormat += item
            mediaSession?.broadcastCustomCommand(
                SessionCommand(SERVICE_GET_AUDIO_FORMAT, Bundle.EMPTY),
                Bundle.EMPTY
            )
        }
    }

    private fun onAudioSinkInputFormatChanged(inputFormat: Format?) {
        audioSinkInputFormat = inputFormat
        mediaSession?.broadcastCustomCommand(
            SessionCommand(SERVICE_GET_AUDIO_FORMAT, Bundle.EMPTY),
            Bundle.EMPTY
        )
    }

    override fun onPlaybackStateChanged(state: Int) {
        if (state == Player.STATE_IDLE) {
            var changed = false
            if (afTrackFormat != null) {
                Log.e(TAG, "leaked track format: $afTrackFormat")
                afTrackFormat = null
                changed = true
            }
            if (pendingAfTrackFormats.isNotEmpty()) {
                Log.e(TAG, "leaked pending track formats: $pendingAfTrackFormats")
                pendingAfTrackFormats.clear()
            }
            if (downstreamFormat.isNotEmpty()) {
                Log.e(TAG, "leaked downstream formats: $downstreamFormat")
                downstreamFormat.clear()
                changed = true
            }
            if (pendingDownstreamFormat.isNotEmpty()) {
                Log.e(TAG, "leaked pending downstream formats: $pendingDownstreamFormat")
                pendingDownstreamFormat.clear()
            }
            if (changed) {
                mediaSession?.broadcastCustomCommand(
                    SessionCommand(SERVICE_GET_AUDIO_FORMAT, Bundle.EMPTY),
                    Bundle.EMPTY
                )
            }
        }
    }

    override fun onPlaybackParametersChanged(
        eventTime: AnalyticsListener.EventTime,
        playbackParameters: PlaybackParameters
    ) {
        scheduleSendingLyrics(false)
    }

    override fun onPlayerError(error: PlaybackException) {
        // TODO
    }

    override fun onDeviceInfoChanged(deviceInfo: DeviceInfo) {
        if (deviceInfo.playbackType == DeviceInfo.PLAYBACK_TYPE_REMOTE) {
            handler.postDelayed({
                setShowNotificationForEmptyPlayer(SHOW_NOTIFICATION_FOR_EMPTY_PLAYER_NEVER)
            }, 2000)
        } else {
            setShowNotificationForEmptyPlayer(SHOW_NOTIFICATION_FOR_EMPTY_PLAYER_AFTER_STOP_OR_ERROR)
        }
    }

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        // --- МАГИЯ REPLAYGAIN ---
        val gainDb = mediaItem?.mediaMetadata?.extras?.getFloat("replay_gain") ?: 0f
        val volumeMultiplier = 10f.pow(gainDb / 20f).coerceIn(0f, 1f)
        endedWorkaroundPlayer?.exoPlayer?.volume = volumeMultiplier
        Log.i(TAG, "🎛 ReplayGain: $gainDb dB -> Volume multiplier: $volumeMultiplier")
        // -----------------------

        bitrate = null
        bitrateFetcher.launch {
            bitrate = mediaItem?.getBitrate()
            this@GramophonePlaybackService.mediaSession?.broadcastCustomCommand(
                SessionCommand(SERVICE_GET_AUDIO_FORMAT, Bundle.EMPTY),
                Bundle.EMPTY
            )
        }
        lyrics = null
        scheduleSendingLyrics(true)

        // 🔥 Мы полностью удалили пересоздание CircularShuffleOrder здесь,
        // чтобы не ломать наш физический App-Level Shuffle!

        lastPlayedManager.save()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        if (prefs.getBooleanStrict("stopPlayingWhenDismissTask", false)) {
            pauseAllPlayersAndStopSelf()
        } else {
            super.onTaskRemoved(rootIntent)
        }
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        scheduleSendingLyrics(false)
        lastPlayedManager.save()
    }

    override fun onEvents(player: Player, events: Player.Events) {
        super<Player.Listener>.onEvents(player, events)

        // 🔥 Мы полностью удалили перехват EVENT_SHUFFLE_MODE_ENABLED_CHANGED
        // Теперь плеер не будет сам пытаться перемешать списки!
    }

    override fun onAddMediaItems(
        mediaSession: MediaSession,
        controller: MediaSession.ControllerInfo,
        mediaItems: List<MediaItem>
    ): ListenableFuture<List<MediaItem>> {
        if (mediaItems.find { it.localConfiguration == null } == null)
            return Futures.immediateFuture(mediaItems)
        val completion = SettableFuture.create<List<MediaItem>>()
        lifecycleScope.launch(Dispatchers.Default) {
            try {
                val result = mediaItems.flatMap {
                    if (it.localConfiguration != null)
                        listOf(it)
                    else if (it.mediaId != MediaItem.DEFAULT_MEDIA_ID)
                        gramophoneApplication.reader.songListFlow.first()
                            .filter { m -> m.mediaId == it.mediaId }
                    else if (it.requestMetadata.searchQuery != null)
                        searchForMediaItem(it)
                    else
                        throw UnsupportedOperationException("can't do anything with $it")
                }
                completion.set(result)
            } catch (e: UnsupportedOperationException) {
                completion.setException(e)
            }
        }
        return completion
    }

    private suspend fun searchForMediaItem(item: MediaItem): List<MediaItem> {
        val text = item.requestMetadata.searchQuery?.trim() ?: ""
        val list = gramophoneApplication.reader.songListFlow.first()
        return if (text == "") list else list.filter {
            val isMatchingTitle =
                it.mediaMetadata.title?.contains(text, true) == true
            val isMatchingAlbum =
                it.mediaMetadata.albumTitle?.contains(text, true) == true
            val isMatchingArtist =
                it.mediaMetadata.artist?.contains(text, true) == true
            isMatchingTitle || isMatchingAlbum || isMatchingArtist
        }
    }

    override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
        refreshMediaButtonCustomLayout()
        if (needsMissingOnDestroyCallWorkarounds()) {
            handler.post { lastPlayedManager.save() }
        }
    }

    override fun onRepeatModeChanged(repeatMode: Int) {
        refreshMediaButtonCustomLayout()
        if (needsMissingOnDestroyCallWorkarounds()) {
            handler.post { lastPlayedManager.save() }
        }
    }

    override fun onTimelineChanged(timeline: Timeline, reason: @Player.TimelineChangeReason Int) {
        if (reason == Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED) {
            refreshMediaButtonCustomLayout()
            if (!computeRgMode(false))
                throw IllegalStateException("unreachable, mode failed with force=false")
        }
        pendingDownstreamFormat.toSet().forEach {
            if (timeline.getIndexOfPeriod(it.first) == C.INDEX_UNSET) {
                pendingDownstreamFormat.remove(it)
            }
        }
        pendingAfTrackFormats.toMap().forEach { (key, _) ->
            if (timeline.getIndexOfPeriod(key) == C.INDEX_UNSET) {
                pendingAfTrackFormats.remove(key)
            }
        }
    }

    private fun refreshMediaButtonCustomLayout() {
        val isEmpty = controller?.currentTimeline?.isEmpty != false
        mediaSession!!.connectedControllers.forEach {
            if (mediaSession!!.isMediaNotificationController(it)
                || mediaSession!!.isAutoCompanionController(it)
                || mediaSession!!.isAutomotiveController(it)
            ) {
                mediaSession!!.setMediaButtonPreferences(
                    it, if (isEmpty) emptyList() else
                        ImmutableList.of(getRepeatCommand(), getShufflingCommand())
                )
            }
        }
    }

    override fun onLoadCanceled(
        eventTime: AnalyticsListener.EventTime,
        loadEventInfo: LoadEventInfo,
        mediaLoadData: MediaLoadData
    ) {
        pendingDownstreamFormat.removeAll { eventTime.mediaPeriodId?.periodUid == it.first }
    }

    override fun onPositionDiscontinuity(
        oldPosition: Player.PositionInfo,
        newPosition: Player.PositionInfo,
        reason: Int
    ) {
        if (oldPosition.periodUid != newPosition.periodUid) {
            var changed = false
            downstreamFormat.toSet().forEach {
                if (newPosition.periodUid != it.first) {
                    downstreamFormat.remove(it)
                    changed = true
                }
            }
            pendingDownstreamFormat.toSet().forEach {
                if (newPosition.periodUid == it.first) {
                    downstreamFormat.add(it)
                    pendingDownstreamFormat.remove(it)
                    changed = true
                }
            }
            if (afTrackFormat?.first != newPosition.periodUid) {
                afTrackFormat = null
                changed = true
            }
            pendingAfTrackFormats[newPosition.periodUid]?.let { format ->
                afTrackFormat = newPosition.periodUid!! to format
                pendingAfTrackFormats.remove(newPosition.periodUid)
                changed = true
            }
            if (changed) {
                mediaSession?.broadcastCustomCommand(
                    SessionCommand(SERVICE_GET_AUDIO_FORMAT, Bundle.EMPTY),
                    Bundle.EMPTY
                )
            }
        }
        scheduleSendingLyrics(false)
    }

    private fun scheduleSendingLyrics(new: Boolean) {
        handler.removeCallbacks(sendLyrics)
        sendLyricNow(new || !updatedLyricAtLeastOnce)
        updatedLyricAtLeastOnce = true
        val isStatusBarLyricsEnabled = prefs.getBooleanStrict("status_bar_lyrics", false)
        val hnw = !LyricWidgetProvider.hasWidget(this)
        if (controller?.isPlaying != true || (!isStatusBarLyricsEnabled && hnw)) return
        val cPos = (controller?.contentPosition ?: 0).toULong()
        val nextUpdate = syncedLyrics?.text?.flatMap { line ->
            if (hnw && line.start <= cPos) listOf() else if (hnw) listOf(line.start) else
                (line.words?.map { it.timeRange.first }?.filter { it > cPos } ?: listOf())
                    .let { i -> if (line.start > cPos) i + line.start else i }
        }?.minOrNull()
        nextUpdate?.let {
            handler.postDelayed(
                sendLyrics, ((it - cPos).toLong()
                        / (controller?.playbackParameters?.speed ?: 1f)).toLong()
            )
        }
    }

    private fun sendLyricNow(new: Boolean) {
        if (new)
            LyricWidgetProvider.update(this)
        else
            LyricWidgetProvider.adapterUpdate(this)
        val isStatusBarLyricsEnabled = prefs.getBooleanStrict("status_bar_lyrics", false)
        val highlightedLyric = if (isStatusBarLyricsEnabled && controller?.playWhenReady == true)
            getCurrentLyricIndex(false)?.let {
                syncedLyrics?.text?.get(it)?.text
            }
        else null
        if (lastSentHighlightedLyric != highlightedLyric) {
            lastSentHighlightedLyric = highlightedLyric
            mediaSession?.let {
                handler.post {
                    if (Looper.myLooper() != it.player.applicationLooper)
                        throw UnsupportedOperationException("wrong looper for triggerNotificationUpdate")
                    isManualNotificationUpdate = true
                    triggerNotificationUpdate()
                    isManualNotificationUpdate = false
                }
            }
        }
    }

    fun getCurrentLyricIndex(withTranslation: Boolean): Int? {
        val lines = syncedLyrics?.text?.mapIndexed { i, it -> i to it }?.filter {
            it.second.start <= (controller?.currentPosition ?: 0).toULong()
                    && (!it.second.isTranslated || withTranslation)
        }
        val max = lines?.maxByOrNull { it.second.start }
        if (max == null) {
            return null
        }
        val maxLines =
            lines.filter { it.second.start == max.second.start && it.second.text.isNotBlank() }
        return maxLines.firstOrNull()?.first ?: max.first
    }

    override fun onForegroundServiceStartNotAllowedException() {
        Log.w(TAG, "Failed to resume playback :/")
        if (mayThrowForegroundServiceStartNotAllowed()
            || mayThrowForegroundServiceStartNotAllowedMiui()
        ) {
            if (supportsNotificationPermission() && !hasNotificationPermission()) {
                Log.e(
                    TAG, Log.getThrowableString(
                        IllegalStateException(
                            "onForegroundServiceStartNotAllowedException shouldn't be called on T+"
                        )
                    )!!
                )
                return
            }
            @SuppressLint("MissingPermission")
            nm.notify(NOTIFY_ID, NotificationCompat.Builder(this, NOTIFY_CHANNEL_ID).apply {
                setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                setAutoCancel(true)
                setCategory(NotificationCompat.CATEGORY_ERROR)
                setSmallIcon(R.drawable.ic_error)
                setContentTitle(this@GramophonePlaybackService.getString(R.string.fgs_failed_title))
                setContentText(this@GramophonePlaybackService.getString(R.string.fgs_failed_text))
                setContentIntent(
                    PendingIntent.getActivity(
                        this@GramophonePlaybackService,
                        PENDING_INTENT_NOTIFY_ID,
                        Intent(this@GramophonePlaybackService, MainActivity::class.java)
                            .putExtra(MainActivity.PLAYBACK_AUTO_START_FOR_FGS, true),
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                    )
                )
                setVibrate(longArrayOf(0L, 200L))
                setLights(0, 0, 0)
                setBadgeIconType(NotificationCompat.BADGE_ICON_NONE)
                setSound(null)
            }.build())
        } else {
            handler.post {
                throw IllegalStateException("onForegroundServiceStartNotAllowedException shouldn't be called on T+")
            }
        }
    }
}