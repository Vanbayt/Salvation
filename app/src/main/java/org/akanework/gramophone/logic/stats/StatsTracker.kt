package org.akanework.gramophone.logic.stats

import android.content.Context
import androidx.media3.common.MediaItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.akanework.gramophone.logic.api.AuthManager
import org.akanework.gramophone.logic.api.NetworkClient
import org.akanework.gramophone.logic.api.PlaybackEventRequest

/**
 * Энергоэффективный трекер воспроизведения с нулевой нагрузкой на батарею (Zero Polling).
 * Все метки времени считаются только в оперативной памяти (RAM), а запись в Room/SQLite
 * происходит строго по естественным событиям плеера (смена трека, пауза, закрытие сервиса).
 */
object StatsTracker {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var activeTrackId: String? = null
    private var activeTitle: String? = null
    private var activeArtist: String? = null
    private var activeAlbum: String? = null
    private var activeCoverUrl: String? = null
    private var activeDurationMs: Long = 0L
    private var activeContextSource: String? = null

    private var sessionStartTimeMs: Long = 0L
    private var accumulatedListenedMs: Long = 0L
    private var isPlaying: Boolean = false

    private fun getUserId(context: Context): String {
        val token = AuthManager.getToken(context)
        return if (!token.isNullOrBlank()) {
            // Используем хэш/подстроку токена или уникальный ID для изоляции пользователя
            "user_" + token.hashCode().toString()
        } else {
            "local_user"
        }
    }

    /**
     * Вызывается при переключении трека в ExoPlayer.
     */
    fun onMediaItemTransition(context: Context, newMediaItem: MediaItem?, currentlyPlaying: Boolean) {
        // 1. Фиксируем предыдущий трек, если он был
        commitCurrentSession(context, isCompleted = false)

        // 2. Инициализируем новый трек
        if (newMediaItem != null) {
            val meta = newMediaItem.mediaMetadata
            activeTrackId = newMediaItem.mediaId
            activeTitle = meta.title?.toString() ?: "Unknown Track"
            activeArtist = meta.artist?.toString() ?: "Unknown Artist"
            activeAlbum = meta.albumTitle?.toString()
            activeCoverUrl = meta.artworkUri?.toString()
            activeDurationMs = meta.extras?.getLong("DURATION") ?: 0L
            activeContextSource = meta.extras?.getString("PLAYING_FROM")

            accumulatedListenedMs = 0L
            isPlaying = currentlyPlaying
            if (currentlyPlaying) {
                sessionStartTimeMs = System.currentTimeMillis()
            }
        } else {
            resetActiveTrack()
        }
    }

    /**
     * Вызывается при нажатии Play / Pause.
     */
    fun onPlayStateChanged(context: Context, playing: Boolean, currentMediaItem: MediaItem?) {
        val now = System.currentTimeMillis()

        if (isPlaying && !playing) {
            // Переход в паузу: накапливаем прослушанное время в RAM
            if (sessionStartTimeMs > 0) {
                val delta = (now - sessionStartTimeMs).coerceAtLeast(0L)
                accumulatedListenedMs += delta
                sessionStartTimeMs = 0L
            }
            isPlaying = false
        } else if (!isPlaying && playing) {
            // Возобновление воспроизведения
            if (activeTrackId == null && currentMediaItem != null) {
                onMediaItemTransition(context, currentMediaItem, true)
                return
            }
            sessionStartTimeMs = now
            isPlaying = true
        }
    }

    /**
     * Сброс текущей сессии в базу данных.
     */
    private fun commitCurrentSession(context: Context, isCompleted: Boolean) {
        val now = System.currentTimeMillis()
        if (isPlaying && sessionStartTimeMs > 0) {
            val delta = (now - sessionStartTimeMs).coerceAtLeast(0L)
            accumulatedListenedMs += delta
            sessionStartTimeMs = 0L
        }

        val trackId = activeTrackId
        val title = activeTitle
        val artist = activeArtist
        val listened = accumulatedListenedMs

        // Логируем в базу только если трек играл более 5 секунд (отсекаем случайные миссклики)
        if (!trackId.isNullOrBlank() && !title.isNullOrBlank() && !artist.isNullOrBlank() && listened >= 5000L) {
            val event = PlaybackEvent(
                userId = getUserId(context),
                trackId = trackId,
                title = title,
                artist = artist,
                album = activeAlbum,
                coverUrl = activeCoverUrl,
                durationMs = activeDurationMs,
                listenedMs = listened,
                timestamp = now,
                isCompleted = isCompleted || (activeDurationMs > 0 && listened >= (activeDurationMs * 0.85)),
                contextSource = activeContextSource,
                isSynced = false
            )

            val appContext = context.applicationContext
            scope.launch {
                val db = PlaybackStatsDatabase.getInstance(appContext)
                val insertedId = db.insertEvent(event)
                if (insertedId > 0) {
                    syncUnsyncedEvents(appContext)
                }
            }
        }

        accumulatedListenedMs = 0L
    }

    /**
     * Контрольная точка (Checkpoint): сохраняет уже прослушанное время в базу,
     * не останавливая и не сбрасывая активный трек.
     */
    fun checkpoint(context: Context) {
        val now = System.currentTimeMillis()
        if (isPlaying && sessionStartTimeMs > 0) {
            val delta = (now - sessionStartTimeMs).coerceAtLeast(0L)
            accumulatedListenedMs += delta
            sessionStartTimeMs = now // продолжаем отсчет дальше
        }

        val trackId = activeTrackId
        val title = activeTitle
        val artist = activeArtist
        val listened = accumulatedListenedMs

        if (!trackId.isNullOrBlank() && !title.isNullOrBlank() && !artist.isNullOrBlank() && listened >= 5000L) {
            val event = PlaybackEvent(
                userId = getUserId(context),
                trackId = trackId,
                title = title,
                artist = artist,
                album = activeAlbum,
                coverUrl = activeCoverUrl,
                durationMs = activeDurationMs,
                listenedMs = listened,
                timestamp = now,
                isCompleted = activeDurationMs > 0 && listened >= (activeDurationMs * 0.85),
                contextSource = activeContextSource,
                isSynced = false
            )

            accumulatedListenedMs = 0L

            val appContext = context.applicationContext
            scope.launch {
                val db = PlaybackStatsDatabase.getInstance(appContext)
                val insertedId = db.insertEvent(event)
                if (insertedId > 0) {
                    syncUnsyncedEvents(appContext)
                }
            }
        }
    }

    /**
     * Принудительный сброс при закрытии плеера/сервиса.
     */
    fun flush(context: Context) {
        commitCurrentSession(context, isCompleted = false)
        resetActiveTrack()
    }

    private fun resetActiveTrack() {
        activeTrackId = null
        activeTitle = null
        activeArtist = null
        activeAlbum = null
        activeCoverUrl = null
        activeDurationMs = 0L
        activeContextSource = null
        sessionStartTimeMs = 0L
        accumulatedListenedMs = 0L
        isPlaying = false
    }

    /**
     * Фоновая синхронизация пачек неотправленных событий с бэкендом.
     */
    private fun syncUnsyncedEvents(context: Context) {
        val db = PlaybackStatsDatabase.getInstance(context)
        val unsynced = db.getUnsyncedEvents(50)
        if (unsynced.isEmpty()) return

        val dtoList = unsynced.map {
            PlaybackEventRequest(
                trackId = it.trackId,
                title = it.title,
                artist = it.artist,
                album = it.album,
                coverUrl = it.coverUrl,
                durationMs = it.durationMs,
                listenedMs = it.listenedMs,
                timestamp = it.timestamp,
                isCompleted = it.isCompleted,
                contextSource = it.contextSource
            )
        }

        try {
            val api = NetworkClient.getApi(context)
            val response = api.sendStatsEvents(dtoList).execute()
            if (response.isSuccessful) {
                db.markEventsSynced(unsynced.map { it.id })
            }
        } catch (_: Exception) {
            // Оффлайн - события останутся в локальной базе и синхронизируются позже
        }
    }
}
