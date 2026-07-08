package org.akanework.gramophone.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.net.toUri
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.akanework.gramophone.logic.AlbumViewModel
import org.akanework.gramophone.ui.adapters.OnlineSearchAdapter

class AlbumTracksFragment : Fragment() {
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: OnlineSearchAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val density = requireContext().resources.displayMetrics.density
        // 🔥 Увеличили отступ до 250dp для гарантированного скролла над мини-плеером
        val bottomPx = (250 * density).toInt()

        recyclerView = RecyclerView(requireContext()).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            layoutManager = LinearLayoutManager(requireContext())
            clipToPadding = false
            setPadding(0, 0, 0, bottomPx)
            // Делаем фон прозрачным, чтобы просвечивала подложка родителя
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
        }
        return recyclerView
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val viewModel = ViewModelProvider(requireParentFragment())[AlbumViewModel::class.java]

        adapter = OnlineSearchAdapter(
            onClick = { clickedTrack ->
                org.akanework.gramophone.logic.HistoryManager.saveToHistory(requireContext(), clickedTrack)

                val player = (requireActivity() as org.akanework.gramophone.ui.MainActivity).getPlayer()

                if (player != null) {
                    val currentAlbum = viewModel.album.value
                    val tracks = adapter.currentList

                    val startIndex = tracks.indexOfFirst { it.id == clickedTrack.id }.coerceAtLeast(0)

                    val mediaItems = tracks.map { track ->
                        val extrasBundle = Bundle().apply {
                            putFloat("replay_gain", track.replayGain)
                            putString("ARTIST_ID", track.artistId ?: currentAlbum?.artistName)
                            putString("ALBUM_ID", track.albumId ?: currentAlbum?.id)
                            putString("PLAYING_FROM", "Альбом: ${currentAlbum?.title ?: "Неизвестно"}")
                        }

                        val finalCover = track.cover ?: currentAlbum?.cover

                        MediaItem.Builder()
                            .setMediaId(track.id)
                            .setUri("http://185.196.41.31/stream/${track.id}".toUri())
                            .setMediaMetadata(
                                MediaMetadata.Builder()
                                    .setTitle(track.title)
                                    .setArtist(track.artist ?: currentAlbum?.artistName)
                                    .setArtworkUri(finalCover?.toUri())
                                    .setExtras(extrasBundle)
                                    .build()
                            )
                            .build()
                    }

                    player.setMediaItems(mediaItems, startIndex, 0L)
                    player.prepare()
                    player.play()
                } else {
                    Toast.makeText(requireContext(), "Плеер еще загружается...", Toast.LENGTH_SHORT).show()
                }
            },
            // 🔥 ВОТ ОНО: ВЫЗЫВАЕМ МЕНЮ ИЗ РОДИТЕЛЬСКОГО ФРАГМЕНТА
            onMenuClick = { track, anchorView ->
                val parent = parentFragment as? AlbumFragment
                parent?.showTrackMenu(track, anchorView)
            },
            onArtistClick = { _, _ -> },
            onAlbumClick = { _, _ -> }
        )
        recyclerView.adapter = adapter

        // 🔥 ПОДКЛЮЧАЕМ СЛУШАТЕЛЬ ПЛЕЕРА ДЛЯ ПОДСВЕТКИ ТРЕКА
        val mainActivity = activity as? org.akanework.gramophone.ui.MainActivity
        mainActivity?.controllerViewModel?.addControllerCallback(viewLifecycleOwner.lifecycle) { controller, _ ->
            adapter.currentlyPlayingTrackId = controller.currentMediaItem?.mediaId
            controller.addListener(object : androidx.media3.common.Player.Listener {
                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                    adapter.currentlyPlayingTrackId = mediaItem?.mediaId
                }
            })
        }

        viewModel.album.observe(viewLifecycleOwner) { album ->
            adapter.submitList(album.tracks ?: emptyList())
        }
    }
}