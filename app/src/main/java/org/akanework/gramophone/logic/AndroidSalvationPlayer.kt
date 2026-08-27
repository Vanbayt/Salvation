package org.akanework.gramophone.logic

import androidx.media3.common.Player
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.akanework.gramophone.logic.api.Track

/**
 * Адаптер плеера для Android, связывающий Media3 ExoPlayer с общим интерфейсом SalvationPlayer.
 */
class AndroidSalvationPlayer(
    private val getPlayer: () -> Player?,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
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

    private val _queueIndex = MutableStateFlow(0)
    override val queueIndex: StateFlow<Int> = _queueIndex.asStateFlow()

    init {
        scope.launch {
            while (isActive) {
                val p = getPlayer()
                if (p != null) {
                    _isPlaying.value = p.isPlaying
                    _positionMs.value = p.currentPosition.coerceAtLeast(0L)
                    _durationMs.value = p.duration.coerceAtLeast(0L)
                    _repeatMode.value = when (p.repeatMode) {
                        Player.REPEAT_MODE_ONE -> PlayerRepeatMode.ONE
                        Player.REPEAT_MODE_ALL -> PlayerRepeatMode.ALL
                        else -> PlayerRepeatMode.OFF
                    }
                    _isShuffleEnabled.value = p.shuffleModeEnabled

                    val curItem = p.currentMediaItem
                    if (curItem != null) {
                        val meta = curItem.mediaMetadata
                        _currentTrack.value = Track(
                            id = curItem.mediaId,
                            title = meta.title?.toString() ?: "",
                            artist = meta.artist?.toString() ?: "",
                            album = meta.albumTitle?.toString() ?: "",
                            duration = (p.duration / 1000).toInt(),
                            cover = meta.artworkUri?.toString(),
                            is_lossless = true
                        )
                    }
                }
                delay(250)
            }
        }
    }

    override fun play() {
        getPlayer()?.play()
    }

    override fun pause() {
        getPlayer()?.pause()
    }

    override fun playPause() {
        val p = getPlayer() ?: return
        if (p.isPlaying) p.pause() else p.play()
    }

    override fun seekTo(positionMs: Long) {
        getPlayer()?.seekTo(positionMs)
    }

    override fun playTrack(track: Track, playlist: List<Track>) {
        // Android ExoPlayer воспроизводит через PlaybackService/MainActivity
    }

    override fun playNext() {
        getPlayer()?.seekToNextMediaItem()
    }

    override fun playPrevious() {
        getPlayer()?.seekToPreviousMediaItem()
    }

    override fun setVolume(volume: Float) {
        // Android управляет громкостью через AudioManager / Stream Music
    }

    override fun toggleRepeat() {
        val p = getPlayer() ?: return
        p.repeatMode = when (p.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    override fun toggleShuffle() {
        val p = getPlayer() ?: return
        p.shuffleModeEnabled = !p.shuffleModeEnabled
    }

    override fun removeTrackFromQueue(index: Int) {
        getPlayer()?.removeMediaItem(index)
    }

    override fun moveQueueItem(from: Int, to: Int) {
        getPlayer()?.moveMediaItem(from, to)
    }
}
