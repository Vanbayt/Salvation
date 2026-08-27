package org.akanework.gramophone.logic.utils

import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import org.akanework.gramophone.logic.api.NetworkClient
import org.akanework.gramophone.logic.api.RadioResponse
import org.akanework.gramophone.logic.api.Track
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.util.concurrent.atomic.AtomicBoolean

object WaveManager {
    private val isAppending = AtomicBoolean(false)

    /**
     * Запуск радио по конкретному треку
     */
    fun startTrackRadio(
        context: Context,
        player: Player?,
        trackId: String?,
        artist: String?,
        title: String?,
        coverUrl: String?
    ) {
        if (player == null) {
            Toast.makeText(context, "Плеер ещё загружается, подождите секунду", Toast.LENGTH_SHORT).show()
            return
        }
        val seedId = trackId?.toLongOrNull()
        val sourceId = if (seedId == null && !trackId.isNullOrEmpty()) trackId else null
        val artistParam = if (!artist.isNullOrEmpty()) artist else null
        val titleParam = if (!title.isNullOrEmpty()) title else null

        if (seedId == null && sourceId == null && (artistParam == null || titleParam == null)) {
            Toast.makeText(context, "Не удалось определить трек для радио", Toast.LENGTH_SHORT).show()
            return
        }

        val displayTitle = title ?: artist ?: "треку"
        Toast.makeText(context, "Подбираем волну «Радио: $displayTitle»...", Toast.LENGTH_SHORT).show()

        NetworkClient.getApi(context).getRadio(
            seedTrackId = seedId,
            sourceId = sourceId,
            artist = artistParam,
            title = titleParam,
            limit = 25
        ).enqueue(object : Callback<RadioResponse> {
            override fun onResponse(call: Call<RadioResponse>, response: Response<RadioResponse>) {
                val radio = response.body()
                val candidates = radio?.tracks ?: emptyList()
                if (candidates.isNotEmpty()) {
                    val tracks = candidates.map { c ->
                        val streamId = if (!c.sourceId.isNullOrEmpty()) c.sourceId else (c.trackId?.toString() ?: "rec_${c.artist}_${c.title}")
                        Track(
                            id = streamId,
                            title = c.title,
                            artist = c.artist,
                            album = c.album ?: "",
                            duration = c.duration,
                            cover = c.coverUrl,
                            sourceId = streamId
                        )
                    }

                    val mediaItems = tracks.map { track ->
                        val extrasBundle = Bundle().apply {
                            putString("PLAYING_FROM", "Радио: $displayTitle")
                            putString("RADIO_SEED_ARTIST", artist ?: "")
                            putString("RADIO_SEED_TITLE", title ?: "")
                            putBoolean("IS_INFINITE_WAVE", true)
                        }
                        val coverFinal = if (track.cover?.startsWith("/") == true) "http://185.196.41.31${track.cover}" else track.cover
                        MediaItem.Builder()
                            .setMediaId(track.id)
                            .setUri("http://185.196.41.31/stream/${track.id}".toUri())
                            .setMediaMetadata(
                                MediaMetadata.Builder()
                                    .setTitle(track.title)
                                    .setArtist(track.artist)
                                    .setArtworkUri(coverFinal?.toUri())
                                    .setDurationMs((track.duration * 1000L).takeIf { it > 0 })
                                    .setExtras(extrasBundle)
                                    .build()
                            ).build()
                    }

                    PlaybackLogger.log("WAVE_RADIO", "Radio loaded: ${tracks.size} tracks for '$displayTitle'")
                    player.shuffleModeEnabled = false
                    player.setMediaItems(mediaItems, 0, 0L)
                    player.prepare()
                    player.play()
                    Toast.makeText(context, "Волна радио запущена (${tracks.size} треков)", Toast.LENGTH_SHORT).show()
                } else {
                    PlaybackLogger.log("WAVE_RADIO", "No tracks returned for radio '$displayTitle'")
                    Toast.makeText(context, "Не удалось подобрать треки для радио", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onFailure(call: Call<RadioResponse>, t: Throwable) {
                PlaybackLogger.log("WAVE_RADIO", "Network failure loading radio: ${t.message}")
                Toast.makeText(context, "Ошибка сети: ${t.message}", Toast.LENGTH_SHORT).show()
            }
        })
    }

    /**
     * Бесшовная фоновая догрузка следующей пачки бесконечной волны (когда в очереди осталось <= 3 трека)
     */
    fun checkAndAppendNextWave(
        context: Context,
        player: Player?,
        currentItem: MediaItem
    ) {
        if (player == null) return
        val extras = currentItem.mediaMetadata.extras ?: return
        val isInfiniteWave = extras.getBoolean("IS_INFINITE_WAVE", false)
        if (!isInfiniteWave) return

        if (!isAppending.compareAndSet(false, true)) {
            return // Уже идёт запрос
        }

        val presetId = extras.getString("PRESET_ID")
        val radioArtist = extras.getString("RADIO_SEED_ARTIST")
        val radioTitle = extras.getString("RADIO_SEED_TITLE")
        val playingFrom = extras.getString("PLAYING_FROM") ?: "Волна"

        // Собираем уже имеющиеся mediaId в текущей очереди для дедупликации
        val existingMediaIds = mutableSetOf<String>()
        for (i in 0 until player.mediaItemCount) {
            existingMediaIds.add(player.getMediaItemAt(i).mediaId)
        }

        val onNewCandidates: (List<org.akanework.gramophone.logic.api.RadioCandidate>) -> Unit = { candidates ->
            val freshCandidates = candidates.filter { c ->
                val sId = if (!c.sourceId.isNullOrEmpty()) c.sourceId else (c.trackId?.toString() ?: "rec_${c.artist}_${c.title}")
                !existingMediaIds.contains(sId)
            }

            if (freshCandidates.isNotEmpty()) {
                val newMediaItems = freshCandidates.map { c ->
                    val streamId = if (!c.sourceId.isNullOrEmpty()) c.sourceId else (c.trackId?.toString() ?: "rec_${c.artist}_${c.title}")
                    val extrasBundle = Bundle().apply {
                        putString("PLAYING_FROM", playingFrom)
                        presetId?.let { putString("PRESET_ID", it) }
                        radioArtist?.let { putString("RADIO_SEED_ARTIST", it) }
                        radioTitle?.let { putString("RADIO_SEED_TITLE", it) }
                        putBoolean("IS_INFINITE_WAVE", true)
                    }
                    val coverFinal = if (c.coverUrl?.startsWith("/") == true) "http://185.196.41.31${c.coverUrl}" else c.coverUrl
                    MediaItem.Builder()
                        .setMediaId(streamId)
                        .setUri("http://185.196.41.31/stream/${streamId}".toUri())
                        .setMediaMetadata(
                            MediaMetadata.Builder()
                                .setTitle(c.title)
                                .setArtist(c.artist)
                                .setArtworkUri(coverFinal?.toUri())
                                .setDurationMs((c.duration * 1000L).takeIf { it > 0 })
                                .setExtras(extrasBundle)
                                .build()
                        ).build()
                }

                player.addMediaItems(newMediaItems)
                PlaybackLogger.log("WAVE_APPEND", "Added ${newMediaItems.size} more tracks to infinite stream ($playingFrom)")
            }
            isAppending.set(false)
        }

        if (!presetId.isNullOrEmpty()) {
            val currentCount = player.mediaItemCount
            NetworkClient.getApi(context).getPresetTracks(presetId, limit = 20, offset = currentCount, wave = true)
                .enqueue(object : Callback<RadioResponse> {
                    override fun onResponse(call: Call<RadioResponse>, response: Response<RadioResponse>) {
                        onNewCandidates(response.body()?.tracks ?: emptyList())
                    }
                    override fun onFailure(call: Call<RadioResponse>, t: Throwable) {
                        isAppending.set(false)
                    }
                })
        } else if (!radioArtist.isNullOrEmpty() || !radioTitle.isNullOrEmpty()) {
            // Wandering Radio: Use the latest track in the playlist as the new anchor seed
            val lastMediaItem = if (player.mediaItemCount > 0) player.getMediaItemAt(player.mediaItemCount - 1) else null
            val nextSeedArtist = lastMediaItem?.mediaMetadata?.artist?.toString()?.takeIf { it.isNotBlank() } ?: radioArtist
            val nextSeedTitle = lastMediaItem?.mediaMetadata?.title?.toString()?.takeIf { it.isNotBlank() } ?: radioTitle

            NetworkClient.getApi(context).getRadio(artist = nextSeedArtist, title = nextSeedTitle, limit = 20)
                .enqueue(object : Callback<RadioResponse> {
                    override fun onResponse(call: Call<RadioResponse>, response: Response<RadioResponse>) {
                        onNewCandidates(response.body()?.tracks ?: emptyList())
                    }
                    override fun onFailure(call: Call<RadioResponse>, t: Throwable) {
                        isAppending.set(false)
                    }
                })
        } else {
            isAppending.set(false)
        }
    }
}
