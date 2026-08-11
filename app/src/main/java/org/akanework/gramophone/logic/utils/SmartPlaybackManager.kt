package org.akanework.gramophone.logic.utils

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.NotificationCompat
import androidx.media3.common.Player
import kotlinx.coroutines.*
import org.akanework.gramophone.R
import java.util.concurrent.atomic.AtomicBoolean

object SmartPlaybackManager {
    private const val TAG = "SmartPlaybackManager"
    private const val CHANNEL_ID = "salvation_playback_alerts"
    private const val CHANNEL_NAME = "Оповещения воспроизведения"

    private const val ACTIVE_SEARCH_TIMEOUT_MS = 15000L // 15s max for YTM search/fallbacks
    private const val STALL_BUFFERING_TIMEOUT_MS = 8000L // 8s max for buffer stalls

    private val mainHandler = Handler(Looper.getMainLooper())
    private val managerScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private var currentTimeoutJob: Job? = null
    private var currentPlayer: Player? = null
    private var appContext: Context? = null

    private val isSkipping = AtomicBoolean(false)

    var isResolving by mutableStateOf(false)
        internal set

    fun init(context: Context, player: Player) {
        appContext = context.applicationContext
        currentPlayer = player

        createNotificationChannel(appContext!!)
        PlaybackLogger.init(appContext!!)

        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) {
                    isResolving = false
                    cancelTimeoutTimer()
                }
            }

            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int
            ) {
                if (newPosition.positionMs > 0) {
                    isResolving = false
                    cancelTimeoutTimer()
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_BUFFERING -> {
                        PlaybackLogger.log("SMART_MGR", "Player entered STATE_BUFFERING")
                        startStallTimeoutTimer()
                    }
                    Player.STATE_READY -> {
                        PlaybackLogger.log("SMART_MGR", "Player entered STATE_READY. Cancelling timeout timers.")
                        isResolving = false
                        cancelTimeoutTimer()
                        isSkipping.set(false)
                    }
                    Player.STATE_ENDED, Player.STATE_IDLE -> {
                        isResolving = false
                        cancelTimeoutTimer()
                    }
                }
            }

            private val fallbackAttempted = mutableSetOf<String>()

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                isResolving = false
                val currentItem = currentPlayer?.currentMediaItem
                val mediaId = currentItem?.mediaId ?: ""

                if (mediaId.isNotEmpty() && !fallbackAttempted.contains(mediaId)) {
                    fallbackAttempted.add(mediaId)
                    PlaybackLogger.log("SMART_FALLBACK", "Player error [${error.errorCodeName}: ${error.message}]. Attempting Level 2 Server Proxy Fallback for track $mediaId!")
                    mainHandler.post {
                        try {
                            val serverUri = android.net.Uri.parse("http://185.196.41.31/stream/$mediaId")
                            val newItem = currentItem!!.buildUpon().setUri(serverUri).build()
                            val curIdx = currentPlayer?.currentMediaItemIndex ?: -1
                            if (curIdx >= 0) {
                                currentPlayer?.replaceMediaItem(curIdx, newItem)
                                currentPlayer?.prepare()
                                currentPlayer?.play()
                                return@post
                            }
                        } catch (e: Exception) {
                            PlaybackLogger.log("SMART_FALLBACK_ERR", "Failed Level 2 fallback: ${e.message}")
                        }
                        onHardFailure("Player Error: ${error.message}")
                    }
                    return
                }

                PlaybackLogger.log("SMART_MGR", "Hard Player Error: ${error.message}. Triggering instant skip!")
                onHardFailure("Player Error: ${error.message}")
            }
        })
    }

    /**
     * Called when a new track is requested to be resolved/played.
     */
    fun onTrackRequested(trackTitle: String = "") {
        cancelTimeoutTimer()
        isSkipping.set(false)
        isResolving = true
        PlaybackLogger.log("SMART_MGR", "Track requested: '$trackTitle'. Starting active resolution timer (15s).")

        currentTimeoutJob = managerScope.launch {
            delay(ACTIVE_SEARCH_TIMEOUT_MS)
            if (!isSkipping.get() && currentPlayer?.playbackState != Player.STATE_READY) {
                PlaybackLogger.log("SMART_TIMEOUT", "Active search/resolution timed out after 15s for '$trackTitle'. Skipping!")
                triggerAutoSkip(trackTitle, "Таймаут поиска трека")
            }
        }
    }

    /**
     * Called when a hard failure occurs (NO_MATCH, HTTP 403, 404, etc.) -> INSTANT SKIP (< 1s)
     */
    fun onHardFailure(reason: String, trackTitle: String = "") {
        cancelTimeoutTimer()
        isResolving = false
        if (isSkipping.compareAndSet(false, true)) {
            PlaybackLogger.log("SMART_HARD_FAIL", "Hard failure triggered: $reason. Instant auto-skipping...")
            mainHandler.post {
                triggerAutoSkip(trackTitle, reason)
            }
        }
    }

    private fun startStallTimeoutTimer() {
        cancelTimeoutTimer()
        val player = currentPlayer ?: return
        val isMidTrack = player.currentPosition > 500L
        val timeoutMs = if (isMidTrack) 25000L else 12000L

        currentTimeoutJob = managerScope.launch {
            delay(timeoutMs)
            if (!isSkipping.get() && currentPlayer?.playbackState == Player.STATE_BUFFERING) {
                PlaybackLogger.log("SMART_STALL_TIMEOUT", "Buffer stalled for > ${timeoutMs / 1000}s (MidTrack: $isMidTrack). Triggering auto-skip!")
                triggerAutoSkip("", "Буфер застрял при воспроизведении")
            }
        }
    }

    private fun cancelTimeoutTimer() {
        currentTimeoutJob?.cancel()
        currentTimeoutJob = null
    }

    private fun triggerAutoSkip(trackTitle: String, reason: String) {
        val player = currentPlayer ?: return
        val ctx = appContext

        val titleDisplay = if (trackTitle.isNotEmpty()) "'$trackTitle'" else "Текущий трек"
        val alertMsg = "Не удалось загрузить $titleDisplay ($reason). Переход к следующему..."

        PlaybackLogger.log("SMART_AUTOSKIP", alertMsg)

        if (ctx != null) {
            mainHandler.post {
                Toast.makeText(ctx, alertMsg, Toast.LENGTH_SHORT).show()
            }
            sendBackgroundNotification(ctx, alertMsg)
        }

        try {
            if (player.hasNextMediaItem()) {
                player.seekToNextMediaItem()
                player.prepare()
                player.play()
            } else {
                player.seekToNext()
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
