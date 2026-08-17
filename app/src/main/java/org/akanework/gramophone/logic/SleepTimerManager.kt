package org.akanework.gramophone.logic

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

    fun startTimer(minutes: Int, player: Player?, onFinish: (() -> Unit)? = null) {
        cancelTimer()
        if (minutes <= 0) return

        activePlayer = player
        val totalSec = minutes * 60L
        _totalSeconds.value = totalSec
        _remainingSeconds.value = totalSec
        _isRunning.value = true
        _isEndOfTrack.value = false

        timerJob = scope.launch {
            var timeLeft = totalSec
            while (timeLeft > 0 && isActive) {
                delay(1000L)
                timeLeft--
                _remainingSeconds.value = timeLeft

                // Плавное затухание громкости за последние 5 секунд
                if (timeLeft in 1..5) {
                    val fadeVolume = (timeLeft.toFloat() / 5f).coerceIn(0.1f, 1.0f)
                    try {
                        activePlayer?.volume = fadeVolume
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }

            if (isActive) {
                try {
                    activePlayer?.pause()
                    activePlayer?.volume = 1.0f
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                onFinish?.invoke()
                cancelTimer()
            }
        }
    }

    fun startEndOfTrack(player: Player?, onFinish: (() -> Unit)? = null) {
        cancelTimer()
        activePlayer = player
        _isEndOfTrack.value = true
        _isRunning.value = true
        _remainingSeconds.value = 0
        _totalSeconds.value = 0

        val listener = object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                try {
                    activePlayer?.pause()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                onFinish?.invoke()
                cancelTimer()
            }
        }
        endOfTrackListener = listener
        player?.addListener(listener)
    }

    fun cancelTimer() {
        timerJob?.cancel()
        timerJob = null

        endOfTrackListener?.let { listener ->
            activePlayer?.removeListener(listener)
        }
        endOfTrackListener = null

        try {
            activePlayer?.volume = 1.0f
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
