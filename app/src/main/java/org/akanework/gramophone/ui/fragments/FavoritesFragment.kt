package org.akanework.gramophone.ui.fragments

import android.content.ComponentName
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import org.akanework.gramophone.R
import org.akanework.gramophone.logic.GramophonePlaybackService
import org.akanework.gramophone.logic.api.NetworkClient
import org.akanework.gramophone.logic.api.Track
import org.akanework.gramophone.ui.adapters.OnlineSearchAdapter
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class FavoritesFragment : BaseFragment(null) {

    private var mediaController: MediaController? = null
    private lateinit var btnPlayAll: MaterialButton // Кнопка Play/Pause

    private lateinit var progressBar: ProgressBar
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: OnlineSearchAdapter
    private lateinit var layoutManager: LinearLayoutManager

    // Элементы шапки
    private lateinit var tvTrackCount: TextView
    private lateinit var etSearchPlaylist: TextInputEditText
    private lateinit var btnShuffleToggle: MaterialButton

    private var isLoading = false
    private var isLastPage = false
    private var currentOffset = 0
    private val pageSize = 50
    private var currentQuery = ""

    // Полный список треков для локального поиска
    private val fullTrackList = mutableListOf<Track>()
    private var isPreparingPlayback = false


    // === ИНИЦИАЛИЗАЦИЯ И СЛУШАТЕЛЬ ПЛЕЕРА ===
    private fun initMediaController() {
        try {
            val serviceComponent = ComponentName(requireContext(), GramophonePlaybackService::class.java)
            val sessionToken = SessionToken(requireContext(), serviceComponent)
            val controllerFuture = MediaController.Builder(requireContext(), sessionToken).buildAsync()

            controllerFuture.addListener({
                try {
                    val controller = controllerFuture.get()
                    mediaController = controller

                    // Устанавливаем иконку при запуске фрагмента
                    updatePlayPauseButton(controller.isPlaying)

                    // Слушаем изменения статуса воспроизведения
                    controller.addListener(object : Player.Listener {
                        override fun onIsPlayingChanged(isPlaying: Boolean) {
                            updatePlayPauseButton(isPlaying)
                        }
                    })
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }, ContextCompat.getMainExecutor(requireContext()))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun updatePlayPauseButton(isPlaying: Boolean) {
        if (isPlaying) {
            btnPlayAll.setIconResource(R.drawable.ic_pause) // Иконка паузы
        } else {
            btnPlayAll.setIconResource(R.drawable.ic_play)  // Иконка play
        }
    }

    private fun filterAndApply() {
        val filtered = if (currentQuery.isEmpty()) {
            fullTrackList.toList()
        } else {
            fullTrackList.filter {
                it.title.contains(currentQuery, ignoreCase = true) ||
                        it.artist.contains(currentQuery, ignoreCase = true)
            }
        }
        adapter.submitList(filtered)
        updateTrackCountUi(filtered.size)
    }

    private fun updateTrackCountUi(count: Int) {
        val word = when {
            count % 10 == 1 && count % 100 != 11 -> "трек"
            count % 10 in 2..4 && (count % 100 < 10 || count % 100 >= 20) -> "трека"
            else -> "треков"
        }
        tvTrackCount.text = "$count $word"
    }

    private fun loadFavorites(isLoadMore: Boolean) {
        if (isLoading) return
        isLoading = true

        if (!isLoadMore) {
            progressBar.visibility = View.VISIBLE
            currentOffset = 0
            fullTrackList.clear()
            isLastPage = false
        }

        NetworkClient.getApi(requireContext())
            .getFavorites(skip = currentOffset, limit = pageSize)
            .enqueue(object : Callback<List<Track>> {
                override fun onResponse(call: Call<List<Track>>, response: Response<List<Track>>) {
                    if (!isAdded) return
                    isLoading = false
                    progressBar.visibility = View.GONE

                    if (response.isSuccessful) {
                        val newTracks = response.body() ?: emptyList()
                        if (newTracks.size < pageSize) isLastPage = true

                        fullTrackList.addAll(newTracks)
                        currentOffset += newTracks.size
                        filterAndApply()
                    }
                }

                override fun onFailure(call: Call<List<Track>>, t: Throwable) {
                    if (!isAdded) return
                    isLoading = false
                    progressBar.visibility = View.GONE
                }
            })
    }

    private fun playFullPlaylist(clickedTrack: Track?, shuffle: Boolean) {
        if (isPreparingPlayback) return
        isPreparingPlayback = true

        progressBar.visibility = View.VISIBLE
        NetworkClient.getApi(requireContext())
            .getFavorites(skip = 0, limit = 5000)
            .enqueue(object : Callback<List<Track>> {
                override fun onResponse(call: Call<List<Track>>, response: Response<List<Track>>) {
                    if (!isAdded) return
                    isLoading = false
                    progressBar.visibility = View.GONE

                    if (response.isSuccessful) {
                        val newTracks = response.body() ?: emptyList()
                        if (newTracks.isNotEmpty()) {
                            fullTrackList.clear()
                            fullTrackList.addAll(newTracks)
                            filterAndApply()
                        }
                        isPreparingPlayback = false
                        launchExoPlayer(clickedTrack, fullTrackList, shuffle)
                    } else {
                        isPreparingPlayback = false
                        launchExoPlayer(clickedTrack, fullTrackList, shuffle)
                    }
                }

                override fun onFailure(call: Call<List<Track>>, t: Throwable) {
                    if (!isAdded) return
                    isPreparingPlayback = false
                    progressBar.visibility = View.GONE
                    launchExoPlayer(clickedTrack, fullTrackList, shuffle)
                }
            })
    }

    private fun launchExoPlayer(clickedTrack: Track?, trackList: List<Track>, shuffle: Boolean) {
        val controller = mediaController
        if (controller == null) {
            Toast.makeText(context, "Подключение к плееру...", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val startIdx = if (clickedTrack != null) {
                trackList.indexOfFirst { it.id == clickedTrack.id }.coerceAtLeast(0)
            } else 0

            val mediaItems = trackList.map { track ->
                val extrasBundle = Bundle().apply {
                    putFloat("replay_gain", track.replayGain)
                    putString("ARTIST_ID", track.artistId)
                    putString("ALBUM_ID", track.albumId)
                    putString("PLAYING_FROM", "Избранное")
                }

                val trackDurationMs = (track.duration ?: 0).toLong() * 1000L

                val builder = MediaItem.Builder()
                    .setMediaId(track.id)
                    .setUri("http://185.196.41.31/stream/${track.id}".toUri())
                    .setMediaMetadata(MediaMetadata.Builder()
                        .setTitle(track.title)
                        .setArtist(track.artist)
                        .setArtworkUri(track.cover?.toUri())
                        .setDurationMs(trackDurationMs.takeIf { it > 0 })
                        .setExtras(extrasBundle)
                        .build())

                builder.build()
            }

            if (shuffle) {
                val shuffledItems = org.akanework.gramophone.logic.utils.ShuffleUtils.balancedShuffle(mediaItems) { item ->
                    item.mediaMetadata.artist?.toString()?.lowercase() ?: ""
                }
                controller.setMediaItems(shuffledItems, 0, 0L)
            } else {
                controller.setMediaItems(mediaItems, startIdx, 0L)
            }

            controller.prepare()
            controller.shuffleModeEnabled = shuffle
            controller.play()

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}