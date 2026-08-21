package org.akanework.gramophone.logic

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

object SleepTimerManager {

    private val _isRunning = MutableStateFlow(false)
    val isRunning = _isRunning.asStateFlow()

    private val _remainingSeconds = MutableStateFlow(0L)
    val remainingSeconds = _remainingSeconds.asStateFlow()

    private val _totalSeconds = MutableStateFlow(0L)
    val totalSeconds = _totalSeconds.asStateFlow()

    private val _isEndOfTrack = MutableStateFlow(false)
    val isEndOfTrack = _isEndOfTrack.asStateFlow()

    private var timerJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var activePlayer: Player? = null
    private var endOfTrackListener: Player.Listener? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var stopRunnable: Runnable? = null

    private fun stopPlayback() {
        try {
            activePlayer?.playWhenReady = false
            activePlayer?.pause()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        try {
            GramophonePlaybackService.instanceForWidgetAndLyricsOnly?.endedWorkaroundPlayer?.let { servicePlayer ->
                servicePlayer.playWhenReady = false
                servicePlayer.pause()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun startTimer(minutes: Int, player: Player?, onFinish: (() -> Unit)? = null) {
        cancelTimer()
        if (minutes <= 0) return

        activePlayer = player ?: GramophonePlaybackService.instanceForWidgetAndLyricsOnly?.endedWorkaroundPlayer
        val totalSec = minutes * 60L
        _totalSeconds.value = totalSec
        _remainingSeconds.value = totalSec
        _isRunning.value = true
        _isEndOfTrack.value = false

        val targetEndTimeMs = SystemClock.elapsedRealtime() + totalSec * 1000L

        // Handler-based fallback guarantee
        stopRunnable = Runnable {
            stopPlayback()
            onFinish?.invoke()
            cancelTimer()
        }
        mainHandler.postDelayed(stopRunnable!!, totalSec * 1000L)

        // UI countdown ticker
        timerJob = scope.launch {
            while (isActive) {
                val now = SystemClock.elapsedRealtime()
                val remainingMs = (targetEndTimeMs - now).coerceAtLeast(0L)
                val remainingSec = remainingMs / 1000L
                _remainingSeconds.value = remainingSec

                // Smooth fade out over last 5 seconds
                if (remainingSec in 1..5) {
                    val fadeVolume = (remainingSec.toFloat() / 5f).coerceIn(0.1f, 1.0f)
                    try {
                        activePlayer?.volume = fadeVolume
                        GramophonePlaybackService.instanceForWidgetAndLyricsOnly?.endedWorkaroundPlayer?.volume = fadeVolume
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

                if (remainingMs <= 0L) {
                    stopPlayback()
                    onFinish?.invoke()
                    cancelTimer()
                    break
                }
                delay(1000L)
            }
        }
    }

    fun startEndOfTrack(player: Player?, onFinish: (() -> Unit)? = null) {
        cancelTimer()
        activePlayer = player ?: GramophonePlaybackService.instanceForWidgetAndLyricsOnly?.endedWorkaroundPlayer
        _isEndOfTrack.value = true
        _isRunning.value = true
        _remainingSeconds.value = 0
        _totalSeconds.value = 0

        val servicePlayer = GramophonePlaybackService.instanceForWidgetAndLyricsOnly?.endedWorkaroundPlayer
        val targetPlayer = activePlayer ?: servicePlayer

        val listener = object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                stopPlayback()
                onFinish?.invoke()
                cancelTimer()
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    stopPlayback()
                    onFinish?.invoke()
                    cancelTimer()
                }
            }
        }
        endOfTrackListener = listener
        targetPlayer?.addListener(listener)
        if (servicePlayer != null && servicePlayer != targetPlayer) {
            servicePlayer.addListener(listener)
        }
    }

    fun cancelTimer() {
        stopRunnable?.let { mainHandler.removeCallbacks(it) }
        stopRunnable = null

        timerJob?.cancel()
        timerJob = null

        endOfTrackListener?.let { listener ->
            try {
                activePlayer?.removeListener(listener)
            } catch (_: Exception) {}
            try {
                GramophonePlaybackService.instanceForWidgetAndLyricsOnly?.endedWorkaroundPlayer?.removeListener(listener)
            } catch (_: Exception) {}
        }
        endOfTrackListener = null

        try {
            activePlayer?.volume = 1.0f
            GramophonePlaybackService.instanceForWidgetAndLyricsOnly?.endedWorkaroundPlayer?.volume = 1.0f
        } catch (e: Exception) {
            e.printStackTrace()
        }

        activePlayer = null
        _isRunning.value = false
        _remainingSeconds.value = 0L
        _totalSeconds.value = 0L
        _isEndOfTrack.value = false
    }

    fun formatRemainingTime(seconds: Long): String {
        val mins = seconds / 60
        val secs = seconds % 60
        return String.format("%02d:%02d", mins, secs)
    }
}
