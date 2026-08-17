package org.akanework.gramophone.logic.utils

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.NotificationCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import kotlinx.coroutines.*
import org.akanework.gramophone.R
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

object SmartPlaybackManager {
    private const val TAG = "SmartPlaybackManager"
    private const val CHANNEL_ID = "salvation_playback_alerts"
    private const val CHANNEL_NAME = "Оповещения воспроизведения"

    private const val ACTIVE_SEARCH_TIMEOUT_MS = 15000L // 15s max for YTM search/fallbacks
    private const val STALL_BUFFERING_TIMEOUT_MS = 8000L // 8s max for buffer stalls
    private const val AUTOSKIP_DEBOUNCE_MS = 1500L // Minimum delay between auto-skips

    private val mainHandler = Handler(Looper.getMainLooper())
    private val managerScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private var currentTimeoutJob: Job? = null
    private var currentPlayer: Player? = null
    private var appContext: Context? = null

    private val isSkipping = AtomicBoolean(false)
    private val requestToken = AtomicLong(0L)
    private val consecutiveNetworkFailures = java.util.concurrent.atomic.AtomicInteger(0)
    private var lastAutoSkipTimeMs = 0L

    var isResolving by mutableStateOf(false)
        internal set

    fun resetConsecutiveFailures() {
        if (consecutiveNetworkFailures.get() != 0) {
            PlaybackLogger.log("SMART_MGR", "Resetting consecutive network failure counter to 0.")
            consecutiveNetworkFailures.set(0)
        }
    }

    private fun incrementTokenAndReset(): Long {
        cancelTimeoutTimer()
        isSkipping.set(false)
        return requestToken.incrementAndGet()
    }

    fun init(context: Context, player: Player) {
        appContext = context.applicationContext
        currentPlayer = player

        createNotificationChannel(appContext!!)
        PlaybackLogger.init(appContext!!)

        player.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val token = incrementTokenAndReset()
                val title = mediaItem?.mediaMetadata?.title ?: mediaItem?.mediaId ?: "Unknown Track"
                PlaybackLogger.log("SMART_MGR", "MediaItem transition [token=$token, reason=$reason]: '$title'")
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) {
                    isResolving = false
                    cancelTimeoutTimer()
                    PlaybackLogger.log("SMART_MGR", "Player started playing. Token=${requestToken.get()}")
                }
            }

            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int
            ) {
                val isTrackChange = oldPosition.mediaItemIndex != newPosition.mediaItemIndex
                val isManualSeekOrSkip = reason == Player.DISCONTINUITY_REASON_SEEK || reason == Player.DISCONTINUITY_REASON_SKIP

                if (isTrackChange || isManualSeekOrSkip) {
                    val token = incrementTokenAndReset()
                    PlaybackLogger.log(
                        "SMART_MGR",
                        "Manual skip/seek or track position discontinuity detected [token=$token, reason=$reason, idx: ${oldPosition.mediaItemIndex} -> ${newPosition.mediaItemIndex}]"
                    )
                } else if (newPosition.positionMs > 0) {
                    isResolving = false
                    cancelTimeoutTimer()
                }
            }

            private var stallStartMs: Long = 0L
            private var stallPosMs: Long = 0L

            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_BUFFERING -> {
                        val player = currentPlayer
                        val curPos = player?.currentPosition ?: 0L
                        val bufferedAhead = player?.totalBufferedDuration ?: 0L
                        val mediaId = player?.currentMediaItem?.mediaId ?: ""
                        val trackId = mediaId.toLongOrNull() ?: 0L

                        if (curPos > 500L && stallStartMs == 0L) {
                            stallStartMs = System.currentTimeMillis()
                            stallPosMs = curPos
                            PlaybackLogger.log(
                                "STALL_START",
                                "Player STALL detected at pos ${curPos}ms | Track: $mediaId | Buffered ahead: ${bufferedAhead}ms [token=${requestToken.get()}]"
                            )
                            if (trackId > 0) {
                                org.akanework.gramophone.logic.utils.exoplayer.ClientTrackResolver.sendTelemetryDirect(
                                    "STALL_START",
                                    trackId,
                                    message = "Stall at pos ${curPos}ms, buffered ahead: ${bufferedAhead}ms"
                                )
                            }
                        }

                        PlaybackLogger.log("SMART_MGR", "Player entered STATE_BUFFERING [token=${requestToken.get()}]")
                        startStallTimeoutTimer()
                    }
                    Player.STATE_READY -> {
                        if (stallStartMs > 0L) {
                            val duration = System.currentTimeMillis() - stallStartMs
                            val player = currentPlayer
                            val curPos = player?.currentPosition ?: 0L
                            val mediaId = player?.currentMediaItem?.mediaId ?: ""
                            val trackId = mediaId.toLongOrNull() ?: 0L

                            PlaybackLogger.log(
                                "STALL_END",
                                "Player STALL ENDED after ${duration}ms | Resumed at pos ${curPos}ms (Stalled from ${stallPosMs}ms) | Track: $mediaId"
                            )
                            if (trackId > 0) {
                                org.akanework.gramophone.logic.utils.exoplayer.ClientTrackResolver.sendTelemetryDirect(
                                    "STALL_END",
                                    trackId,
                                    elapsedTimeMs = duration,
                                    message = "Stall ended after ${duration}ms, resumed at ${curPos}ms"
                                )
                            }
                            stallStartMs = 0L
                        }

                        PlaybackLogger.log("SMART_MGR", "Player entered STATE_READY [token=${requestToken.get()}]. Cancelling timeout timers.")
                        isResolving = false
                        cancelTimeoutTimer()
                        isSkipping.set(false)
                    }
                    Player.STATE_ENDED, Player.STATE_IDLE -> {
                        stallStartMs = 0L
                        isResolving = false
                        cancelTimeoutTimer()
                    }
                }
            }

            private val fallbackAttempted = mutableSetOf<String>()

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                isResolving = false
                val currentToken = requestToken.get()
                val currentItem = currentPlayer?.currentMediaItem
                val mediaId = currentItem?.mediaId ?: ""

                if (mediaId.isNotEmpty() && !fallbackAttempted.contains(mediaId)) {
                    fallbackAttempted.add(mediaId)
                    PlaybackLogger.log("SMART_FALLBACK", "Player error [${error.errorCodeName}: ${error.message}]. Attempting Level 2 Server Proxy Fallback for track $mediaId [token=$currentToken]!")
                    mainHandler.post {
                        if (currentToken != requestToken.get()) {
                            PlaybackLogger.log("SMART_FALLBACK_STALE", "Ignored stale fallback attempt for track $mediaId [token=$currentToken vs current=${requestToken.get()}]")
                            return@post
                        }
                        try {
                            val mediaIdLong = mediaId.toLongOrNull()
                            val directUrl = mediaIdLong?.let { org.akanework.gramophone.logic.utils.exoplayer.ClientTrackResolver.getDirectStreamUrl(it) }
                            val currentUriStr = currentItem?.localConfiguration?.uri?.toString() ?: ""
                            val fallbackUri = if (!directUrl.isNullOrEmpty() && currentUriStr.contains("185.196.41.31")) {
                                android.net.Uri.parse(directUrl)
                            } else {
                                android.net.Uri.parse("http://185.196.41.31/stream/$mediaId")
                            }
                            PlaybackLogger.log("SMART_FALLBACK_URI", "Replacing failed item with alternate fallback URI: $fallbackUri")
                            val newItem = currentItem!!.buildUpon().setUri(fallbackUri).build()
                            val currentPos = currentPlayer?.currentPosition ?: 0L
                            val curIdx = currentPlayer?.currentMediaItemIndex ?: -1
                            if (curIdx >= 0) {
                                currentPlayer?.replaceMediaItem(curIdx, newItem)
                                if (currentPos > 0L) {
                                    currentPlayer?.seekTo(curIdx, currentPos)
                                }
                                currentPlayer?.prepare()
                                currentPlayer?.play()
                                return@post
                            }
                        } catch (e: Exception) {
                            PlaybackLogger.log("SMART_FALLBACK_ERR", "Failed Level 2 fallback: ${e.message}")
                        }
                        onHardFailure("Player Error: ${error.message}", expectedToken = currentToken)
                    }
                    return
                }

                PlaybackLogger.log("SMART_MGR", "Hard Player Error: ${error.message}. Triggering instant skip! [token=$currentToken]")
                onHardFailure("Player Error: ${error.message}", expectedToken = currentToken)
            }
        })
    }

    /*
     * TODO: OFFLINE PLAYBACK ROUTING (ОФЛАЙН-РЕЖИМ):
     * При нажатии кнопки "Скачать" в UI или при прослушивании скачанного плейлиста в оффлайне:
     * 1. Проверять флаг `isDownloaded` для запрашиваемого трека в локальной БД.
     * 2. Если трек скачан офлайн, мгновенно запускать воспроизведение из локального файла / `CacheDataSource`
     *    без запуска сетевого резолвера `ClientTrackResolver` и без ожидания 15-секундного таймера.
     */
    fun onTrackRequested(trackTitle: String = ""): Long {
        val token = incrementTokenAndReset()
        isResolving = true
        PlaybackLogger.log("SMART_MGR", "Track requested [token=$token]: '$trackTitle'. Starting active resolution timer (15s).")

        currentTimeoutJob = managerScope.launch {
            delay(ACTIVE_SEARCH_TIMEOUT_MS)
            if (token == requestToken.get() && !isSkipping.get() && currentPlayer?.playbackState != Player.STATE_READY) {
                PlaybackLogger.log("SMART_TIMEOUT", "Active search/resolution timed out after 15s for '$trackTitle' [token=$token]. Skipping!")
                triggerAutoSkip(trackTitle, "Таймаут поиска трека", token)
            } else {
                PlaybackLogger.log("SMART_TIMEOUT", "Ignored stale timeout for '$trackTitle' [token=$token vs current=${requestToken.get()}]")
            }
        }
        return token
    }

    /**
     * Динамическое продление тайм-аута при успешном открытии сетевого потока (STREAM_SUCCESS).
     * Дает ExoPlayer еще 8 секунд на заполнение начального аудио-буфера до STATE_READY.
     */
    fun extendTimeoutOnStreamOpened() {
        val token = requestToken.get()
        mainHandler.post {
            if (token != requestToken.get() || isSkipping.get()) return@post
            if (currentPlayer?.playbackState == Player.STATE_READY) return@post

            PlaybackLogger.log("SMART_MGR", "Stream opened successfully [token=$token]. Extending resolution timeout by 8s for buffering.")
            cancelTimeoutTimer()
            currentTimeoutJob = managerScope.launch {
                delay(8000L) // 8 дополнительный секунд на наполнение буфера
                if (token == requestToken.get() && !isSkipping.get() && currentPlayer?.playbackState != Player.STATE_READY) {
                    PlaybackLogger.log("SMART_TIMEOUT", "Extended buffering timed out after 8s extra [token=$token]. Skipping!")
                    triggerAutoSkip("", "Таймаут наполнения буфера", token)
                }
            }
        }
    }

    /**
     * Called when a hard failure occurs (NO_MATCH, HTTP 403, 404, etc.) -> INSTANT SKIP (< 1s)
     */
    fun onHardFailure(reason: String, trackTitle: String = "", expectedToken: Long = -1L) {
        val currentToken = requestToken.get()
        if (expectedToken != -1L && expectedToken != currentToken) {
            PlaybackLogger.log("SMART_HARD_FAIL_STALE", "Ignored stale hard failure '$reason' for track '$trackTitle' [token=$expectedToken vs current=$currentToken]")
            return
        }

        cancelTimeoutTimer()
        isResolving = false
        if (isSkipping.compareAndSet(false, true)) {
            PlaybackLogger.log("SMART_HARD_FAIL", "Hard failure triggered [token=$currentToken]: $reason. Instant auto-skipping...")
            mainHandler.post {
                if (currentToken == requestToken.get()) {
                    triggerAutoSkip(trackTitle, reason, currentToken)
                } else {
                    PlaybackLogger.log("SMART_HARD_FAIL_STALE", "Cancelled auto-skip Runnable on main thread: token mismatched ($currentToken vs ${requestToken.get()})")
                }
            }
        }
    }

    private fun startStallTimeoutTimer() {
        cancelTimeoutTimer()
        val token = requestToken.get()
        val player = currentPlayer ?: return
        val isMidTrack = player.currentPosition > 500L
        val timeoutMs = if (isMidTrack) 25000L else 12000L

        currentTimeoutJob = managerScope.launch {
            delay(timeoutMs)
            if (token == requestToken.get() && !isSkipping.get() && currentPlayer?.playbackState == Player.STATE_BUFFERING) {
                PlaybackLogger.log("SMART_STALL_TIMEOUT", "Buffer stalled for > ${timeoutMs / 1000}s (MidTrack: $isMidTrack) [token=$token]. Triggering auto-skip!")
                triggerAutoSkip("", "Буфер застрял при воспроизведении", token)
            }
        }
    }

    private fun cancelTimeoutTimer() {
        currentTimeoutJob?.cancel()
        currentTimeoutJob = null
    }

    private fun triggerAutoSkip(trackTitle: String, reason: String, token: Long = -1L) {
        val currentToken = requestToken.get()
        if (token != -1L && token != currentToken) {
            PlaybackLogger.log("SMART_AUTOSKIP_CANCEL", "Auto-skip cancelled: token $token is stale (current token: $currentToken)")
            return
        }

        val now = SystemClock.elapsedRealtime()
        if (now - lastAutoSkipTimeMs < AUTOSKIP_DEBOUNCE_MS) {
            PlaybackLogger.log("SMART_AUTOSKIP_THROTTLED", "Auto-skip throttled (too frequent: ${now - lastAutoSkipTimeMs}ms < ${AUTOSKIP_DEBOUNCE_MS}ms)")
            return
        }
        lastAutoSkipTimeMs = now

        val failuresCount = consecutiveNetworkFailures.incrementAndGet()
        PlaybackLogger.log("SMART_MGR", "Consecutive failure count: $failuresCount")

        if (failuresCount >= 3) {
            PlaybackLogger.log("SMART_OFFLINE", "3 consecutive network failures reached. Halting auto-skip cascade and pausing playback!")
            isResolving = false
            cancelTimeoutTimer()
            mainHandler.post {
                val ctx = appContext
                if (ctx != null) {
                    Toast.makeText(ctx, "Воспроизведение приостановлено: нет подключения к сети", Toast.LENGTH_LONG).show()
                }
                try {
                    currentPlayer?.pause()
                } catch (_: Exception) {}
            }
            return
        }

        val player = currentPlayer ?: return
        val ctx = appContext

        val titleDisplay = if (trackTitle.isNotEmpty()) "'$trackTitle'" else "Текущий трек"
        val alertMsg = "Не удалось загрузить $titleDisplay ($reason). Переход к следующему..."

        PlaybackLogger.log("SMART_AUTOSKIP", "$alertMsg [token=$currentToken]")

        if (ctx != null) {
            mainHandler.post {
                Toast.makeText(ctx, alertMsg, Toast.LENGTH_SHORT).show()
            }
            sendBackgroundNotification(ctx, alertMsg)
        }

        try {
            if (player.hasNextMediaItem()) {
                val nextIdx = player.nextMediaItemIndex
                player.seekTo(nextIdx, 0L)
                player.prepare()
                player.play()
            } else {
                player.seekToNext()
                player.seekTo(0L)
                player.prepare()
                player.play()
            }
        } catch (e: Exception) {
            PlaybackLogger.log("SMART_AUTOSKIP_ERR", "Error skipping to next: ${e.message}")
        }
    }

    private fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Уведомления об автоматическом пропуске недоступных треков"
            }
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun sendBackgroundNotification(context: Context, message: String) {
        try {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val builder = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallResourceIcon(R.drawable.ic_salvation_note)
                .setContentTitle("Salvation Music")
                .setContentText(message)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)

            notificationManager.notify(1002, builder.build())
        } catch (e: Exception) {
            PlaybackLogger.log("NOTIF_ERR", "Failed to send background notification: ${e.message}")
        }
    }

    private fun NotificationCompat.Builder.setSmallResourceIcon(iconRes: Int): NotificationCompat.Builder {
        return try {
            setSmallIcon(iconRes)
        } catch (e: Exception) {
            this
        }
    }
}
