package org.akanework.gramophone.logic

import kotlinx.coroutines.flow.StateFlow
import org.akanework.gramophone.logic.api.Track

enum class PlayerRepeatMode {
    OFF,
    ALL,
    ONE
}

/**
 * Кроссплатформенный интерфейс управления плеером Salvation (Android, Windows, Linux).
 * Позволяет UI слою быть полностью независимым от используемого аудиодвижка
 * (Media3 ExoPlayer на Android, VLCJ / LibVLC на Desktop).
 */
interface SalvationPlayer {
    val currentTrack: StateFlow<Track?>
    val isPlaying: StateFlow<Boolean>
    val positionMs: StateFlow<Long>
    val durationMs: StateFlow<Long>
    val volume: StateFlow<Float>
    val repeatMode: StateFlow<PlayerRepeatMode>
    val isShuffleEnabled: StateFlow<Boolean>
    val queue: StateFlow<List<Track>>
    val queueIndex: StateFlow<Int>

    fun play()
    fun pause()
    fun playPause()
    fun seekTo(positionMs: Long)
    fun playTrack(track: Track, playlist: List<Track> = listOf(track))
    fun playNext()
    fun playPrevious()
    fun setVolume(volume: Float)
    fun toggleRepeat()
    fun toggleShuffle()
    fun removeTrackFromQueue(index: Int)
    fun moveQueueItem(from: Int, to: Int)
}
