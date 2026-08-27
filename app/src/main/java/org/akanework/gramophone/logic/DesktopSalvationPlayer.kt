package org.akanework.gramophone.logic

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.akanework.gramophone.logic.api.Track

/**
 * Десктопная реализация плеера Salvation (Windows / Linux).
 * Управляет состоянием воспроизведения, очередью, позицией, громкостью и таймингами.
 */
class DesktopSalvationPlayer(
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
) : SalvationPlayer {

    private val _currentTrack = MutableStateFlow<Track?>(null)
    override val currentTrack: StateFlow<Track?> = _currentTrack.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    override val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _positionMs = MutableStateFlow(0L)
    override val positionMs: StateFlow<Long> = _positionMs.asStateFlow()

    private val _durationMs = MutableStateFlow(0L)
    override val durationMs: StateFlow<Long> = _durationMs.asStateFlow()

    private val _volume = MutableStateFlow(1.0f)
    override val volume: StateFlow<Float> = _volume.asStateFlow()

    private val _repeatMode = MutableStateFlow(PlayerRepeatMode.OFF)
    override val repeatMode: StateFlow<PlayerRepeatMode> = _repeatMode.asStateFlow()

    private val _isShuffleEnabled = MutableStateFlow(false)
    override val isShuffleEnabled: StateFlow<Boolean> = _isShuffleEnabled.asStateFlow()

    private val _queue = MutableStateFlow<List<Track>>(emptyList())
    override val queue: StateFlow<List<Track>> = _queue.asStateFlow()

    private val _queueIndex = MutableStateFlow(-1)
    override val queueIndex: StateFlow<Int> = _queueIndex.asStateFlow()

    private var progressJob: Job? = null

    init {
        startProgressTicker()
    }

    private fun startProgressTicker() {
        progressJob?.cancel()
        progressJob = scope.launch {
            while (isActive) {
                if (_isPlaying.value) {
                    val cur = _positionMs.value
                    val dur = _durationMs.value
                    if (dur > 0 && cur >= dur) {
                        onTrackEnded()
                    } else {
                        _positionMs.value = cur + 250
                    }
                }
                delay(250)
            }
        }
    }

    private fun onTrackEnded() {
        when (_repeatMode.value) {
            PlayerRepeatMode.ONE -> {
                seekTo(0L)
                play()
            }
            PlayerRepeatMode.ALL -> {
                playNext()
            }
            PlayerRepeatMode.OFF -> {
                val nextIdx = _queueIndex.value + 1
                if (nextIdx < _queue.value.size) {
                    playNext()
                } else {
                    pause()
                    _positionMs.value = 0L
                }
            }
        }
    }

    override fun play() {
        if (_currentTrack.value != null) {
            _isPlaying.value = true
        }
    }

    override fun pause() {
        _isPlaying.value = false
    }

    override fun playPause() {
        if (_isPlaying.value) pause() else play()
    }

    override fun seekTo(positionMs: Long) {
        _positionMs.value = positionMs.coerceIn(0L, _durationMs.value.coerceAtLeast(0L))
    }

    override fun playTrack(track: Track, playlist: List<Track>) {
        val targetPlaylist = if (playlist.isEmpty()) listOf(track) else playlist
        _queue.value = targetPlaylist
        val idx = targetPlaylist.indexOfFirst { it.id == track.id }.let { if (it == -1) 0 else it }
        _queueIndex.value = idx
        _currentTrack.value = track
        _positionMs.value = 0L
        _durationMs.value = (track.duration * 1000).toLong().coerceAtLeast(0L)
        _isPlaying.value = true
    }

    override fun playNext() {
        val q = _queue.value
        if (q.isEmpty()) return
        val nextIdx = (_queueIndex.value + 1) % q.size
        _queueIndex.value = nextIdx
        val nextTrack = q[nextIdx]
        playTrack(nextTrack, q)
    }

    override fun playPrevious() {
        val q = _queue.value
        if (q.isEmpty()) return
        if (_positionMs.value > 3000L) {
            seekTo(0L)
            return
        }
        val prevIdx = if (_queueIndex.value - 1 < 0) q.size - 1 else _queueIndex.value - 1
        _queueIndex.value = prevIdx
        val prevTrack = q[prevIdx]
        playTrack(prevTrack, q)
    }

    override fun setVolume(volume: Float) {
        _volume.value = volume.coerceIn(0.0f, 1.0f)
    }

    override fun toggleRepeat() {
        _repeatMode.value = when (_repeatMode.value) {
            PlayerRepeatMode.OFF -> PlayerRepeatMode.ALL
            PlayerRepeatMode.ALL -> PlayerRepeatMode.ONE
            PlayerRepeatMode.ONE -> PlayerRepeatMode.OFF
        }
    }

    override fun toggleShuffle() {
        _isShuffleEnabled.value = !_isShuffleEnabled.value
    }

    override fun removeTrackFromQueue(index: Int) {
        val q = _queue.value.toMutableList()
        if (index in q.indices) {
            q.removeAt(index)
            _queue.value = q
            if (_queueIndex.value >= q.size) {
                _queueIndex.value = (q.size - 1).coerceAtLeast(0)
            }
        }
    }

    override fun moveQueueItem(from: Int, to: Int) {
        val q = _queue.value.toMutableList()
        if (from in q.indices && to in q.indices) {
            val item = q.removeAt(from)
            q.add(to, item)
            _queue.value = q
        }
    }
}
