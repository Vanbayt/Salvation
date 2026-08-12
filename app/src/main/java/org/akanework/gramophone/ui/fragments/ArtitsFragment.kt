package org.akanework.gramophone.ui.fragments

import android.graphics.Color
import android.graphics.Rect
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil3.load
import coil3.request.crossfade
import com.google.android.material.imageview.ShapeableImageView
import com.google.android.material.transition.MaterialContainerTransform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.akanework.gramophone.R
import org.akanework.gramophone.logic.api.Artist
import org.akanework.gramophone.logic.api.NetworkClient
import org.akanework.gramophone.logic.api.Track
import org.akanework.gramophone.ui.adapters.OnlineSearchAdapter

class ArtistFragment : Fragment() {

    // 🔥 Стейты для управления Compose-меню поверх списка
    private var selectedTrackForMenu = mutableStateOf<Track?>(null)
    private var menuExpanded = mutableStateOf(false)
    private var menuOffset = mutableStateOf(DpOffset.Zero)

    private var artistId: String? = null
    private var currentArtist: Artist? = null

    private lateinit var btnBack: ImageButton
    private lateinit var ivCover: ShapeableImageView
    private lateinit var tvName: TextView
    private lateinit var rvTracks: RecyclerView
    private lateinit var rvAlbums: RecyclerView

    private lateinit var btnLike: com.google.android.material.button.MaterialButton
    private lateinit var btnAllAlbums: com.google.android.material.button.MaterialButton
    private lateinit var layoutAlbumsHeader: View

    private lateinit var trackAdapter: OnlineSearchAdapter
    private var playerController: androidx.media3.session.MediaController? = null
    private var playerListener: androidx.media3.common.Player.Listener? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        artistId = arguments?.getString("ARTIST_ID")

        sharedElementEnterTransition = MaterialContainerTransform().apply {
            duration = 450
            scrimColor = Color.TRANSPARENT
            fadeMode = MaterialContainerTransform.FADE_MODE_THROUGH
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // 1. Инфлейтим родной XML
        val xmlView = inflater.inflate(R.layout.fragment_artist, container, false)

        // 2. Создаем прозрачный слой Compose для меню
        val composeView = ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                val isDark = isSystemInDarkTheme()
                val dynamicColor = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S
                val colors = when {
                    dynamicColor && isDark -> dynamicDarkColorScheme(requireContext())
                    dynamicColor && !isDark -> dynamicLightColorScheme(requireContext())
                    isDark -> darkColorScheme()
                    else -> lightColorScheme()
                }

                MaterialTheme(colorScheme = colors) {
                    val track = selectedTrackForMenu.value
                    if (track != null) {
                        TrackContextMenu(
                            expanded = menuExpanded.value,
                            track = track,
                            offset = menuOffset.value,
                            onDismiss = { menuExpanded.value = false },
                            onAddToPlaylist = {
                                menuExpanded.value = false
                                val trackId = track.id.toIntOrNull()
                                if (trackId != null) {
                                    val sheet = AddToPlaylistBottomSheet.newInstance(trackId)
                                    sheet.show(requireActivity().supportFragmentManager, "ADD_TO_PLAYLIST_SHEET")
                                }
                            },
                            onPlayNext = { t ->
                                menuExpanded.value = false
                                addTrackToQueueNext(t)
                            },
                            onGoToArtist = { t ->
                                menuExpanded.value = false
                                t.artistId?.let { id ->
                                    // Если мы уже на этой странице артиста - просто закроем меню
                                    if (id == artistId) return@TrackContextMenu
                                    (requireActivity() as org.akanework.gramophone.ui.MainActivity)
                                        .startFragment(newInstance(id))
                                }
                            },
                            onGoToAlbum = { t ->
                                menuExpanded.value = false
                                t.albumId?.let { id ->
                                    (requireActivity() as org.akanework.gramophone.ui.MainActivity)
                                        .startFragment(AlbumFragment.newInstance(id))
                                }
                            }
                        )
                    }
                }
            }
        }

        // 3. Собираем бутерброд
        val wrapper = FrameLayout(requireContext()).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            addView(xmlView)
            addView(composeView)
        }

        return wrapper
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.transitionName = "artist_card_${artistId}"

        btnBack = view.findViewById(R.id.btn_back)
        ivCover = view.findViewById(R.id.iv_artist_cover)
        tvName = view.findViewById(R.id.tv_artist_name)
        rvTracks = view.findViewById(R.id.rv_top_tracks)
        rvAlbums = view.findViewById(R.id.rv_albums)
        btnLike = view.findViewById(R.id.btn_like_artist)
        btnAllAlbums = view.findViewById(R.id.btn_all_albums)
        layoutAlbumsHeader = view.findViewById(R.id.layout_albums_header)

        rvTracks.layoutManager = LinearLayoutManager(requireContext())
        rvTracks.setBackgroundColor(Color.TRANSPARENT)

        btnBack.setOnClickListener {
            requireActivity().onBackPressed()
        }

        btnLike.setOnClickListener {
            val currentArtistId = artistId ?: return@setOnClickListener
            btnLike.isEnabled = false

            NetworkClient.getApi(requireContext()).toggleArtistLike(currentArtistId)
                .enqueue(object : retrofit2.Callback<Map<String, String>> {
                    override fun onResponse(call: retrofit2.Call<Map<String, String>>, response: retrofit2.Response<Map<String, String>>) {
                        btnLike.isEnabled = true
                        if (response.isSuccessful) {
                            val status = response.body()?.get("status")
                            if (status == "liked") {
                                btnLike.text = "В избранном"
                                btnLike.setIconResource(R.drawable.ic_favorite_filled)
                                currentArtist?.isLiked = true
                                Toast.makeText(context, "Добавлено в медиатеку", Toast.LENGTH_SHORT).show()
                            } else {
                                btnLike.text = "В избранное"
                                btnLike.setIconResource(R.drawable.ic_favorite)
                                currentArtist?.isLiked = false
                                Toast.makeText(context, "Удалено из медиатеки", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }

                    override fun onFailure(call: retrofit2.Call<Map<String, String>>, t: Throwable) {
                        btnLike.isEnabled = true
                        Toast.makeText(context, "Ошибка сети", Toast.LENGTH_SHORT).show()
                    }
                })
        }

        btnAllAlbums.setOnClickListener {
            artistId?.let { id ->
                val discographyFragment = DiscographyFragment.newInstance(id)
                (requireActivity() as org.akanework.gramophone.ui.MainActivity).startFragment(discographyFragment)
            }
        }

        artistId?.let { id ->
            loadArtistData(id)
        }
    }

    private fun loadArtistData(id: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val response = NetworkClient.getApi(requireContext()).getArtistPage(id).execute()

                withContext(Dispatchers.Main) {
                    if (response.isSuccessful && response.body() != null) {
                        bindData(response.body()!!)
                    } else {
                        Toast.makeText(requireContext(), "Ошибка загрузки: ${response.code()}", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "Ошибка сети: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun bindData(artist: Artist) {
        currentArtist = artist
        tvName.text = artist.name

        if (!artist.cover.isNullOrEmpty()) {
            val finalCover = if (artist.cover.startsWith("/")) "http://185.196.41.31${artist.cover}" else artist.cover
            ivCover.load(finalCover) {
                crossfade(true)
                crossfade(300)
            }
        } else {
            ivCover.setImageResource(R.drawable.ic_library)
        }

        if (artist.isLiked) {
            btnLike.text = "В избранном"
            btnLike.setIconResource(R.drawable.ic_favorite_filled)
        } else {
            btnLike.text = "В избранное"
            btnLike.setIconResource(R.drawable.ic_favorite)
        }

        val tracks = artist.tracks ?: emptyList()
        if (tracks.isNotEmpty()) {
            trackAdapter = OnlineSearchAdapter(
                onClick = { clickedTrack ->
                    org.akanework.gramophone.logic.HistoryManager.saveToHistory(requireContext(), clickedTrack)
                    val player = (requireActivity() as org.akanework.gramophone.ui.MainActivity).getPlayer()
                    if (player != null) {
                        val startIndex = tracks.indexOfFirst { it.id == clickedTrack.id }.coerceAtLeast(0)
                        val mediaItems = tracks.map { track ->
                            val extrasBundle = Bundle().apply {
                                putFloat("replay_gain", track.replayGain)
                                putString("ARTIST_ID", track.artistId ?: artist.id)
                                putString("ALBUM_ID", track.albumId)
                                putString("PLAYING_FROM", "Артист: ${artist.name}")
                            }
                            MediaItem.Builder()
                                .setMediaId(track.id)
                                .setUri("http://185.196.41.31/stream/${track.id}".toUri())
                                .setMediaMetadata(
                                    MediaMetadata.Builder()
                                        .setTitle(track.title)
                                        .setArtist(track.artist)
                                        .setArtworkUri(track.cover?.toUri())
                                        .setDurationMs((track.duration * 1000L).takeIf { it > 0 })
                                        .setExtras(extrasBundle)
                                        .build()
                                )
                                .build()
                        }
                        player.setMediaItems(mediaItems, startIndex, 0L)
                        player.prepare()
                        player.play()
                    }
                },
                // 🔥 ОЖИВЛЯЕМ МЕНЮ В СПИСКЕ ТРЕКОВ АРТИСТА
                onMenuClick = { track, anchorView ->
                    showTrackMenu(track, anchorView)
                },
                onArtistClick = { _, _ -> },
                onAlbumClick = { _, _ -> }
            )
            rvTracks.adapter = trackAdapter
            trackAdapter.submitList(tracks)

            val mainActivity = activity as? org.akanework.gramophone.ui.MainActivity
            mainActivity?.controllerViewModel?.addControllerCallback(viewLifecycleOwner.lifecycle) { controller, _ ->
                playerListener?.let { playerController?.removeListener(it) }
                playerController = controller
                trackAdapter.currentlyPlayingTrackId = controller.currentMediaItem?.mediaId
                val listener = object : androidx.media3.common.Player.Listener {
                    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                        trackAdapter.currentlyPlayingTrackId = mediaItem?.mediaId
                    }
                }
                playerListener = listener
                controller.addListener(listener)
            }
        }

        val albums = artist.albums ?: emptyList()
        if (albums.isNotEmpty()) {
            layoutAlbumsHeader.visibility = View.VISIBLE
            rvAlbums.visibility = View.VISIBLE

            val albumAdapter = OnlineSearchAdapter(
                isGridMode = false,
                onClick = { },
                onArtistClick = { _, _ -> },
                onAlbumClick = { clickedAlbum, cardView ->
                    org.akanework.gramophone.logic.HistoryManager.saveToHistory(requireContext(), clickedAlbum)
                    val albumFragment = AlbumFragment.newInstance(clickedAlbum.id)
                    (requireActivity() as org.akanework.gramophone.ui.MainActivity).startFragment(
                        frag = albumFragment,
                        sharedView = cardView,
                        transName = "album_card_${clickedAlbum.id}"
                    )
                }
            )
            rvAlbums.adapter = albumAdapter
            albumAdapter.submitList(albums)
        } else {
            layoutAlbumsHeader.visibility = View.GONE
            rvAlbums.visibility = View.GONE
        }
    }

    // 🔥 ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ ДЛЯ МЕНЮ
    private fun showTrackMenu(track: Track, anchorView: View) {
        val rect = Rect()
        anchorView.getGlobalVisibleRect(rect)
        val density = resources.displayMetrics.density
        menuOffset.value = DpOffset(
            x = (rect.left / density).dp - 160.dp,
            y = (rect.top / density).dp - 32.dp
        )
        selectedTrackForMenu.value = track
        menuExpanded.value = true
    }

    private fun addTrackToQueueNext(track: Track) {
        val player = (requireActivity() as org.akanework.gramophone.ui.MainActivity).getPlayer() ?: return
        val streamUrl = "http://185.196.41.31/stream/${track.id}"
        val finalCoverUrl = if (track.cover?.startsWith("/") == true) "http://185.196.41.31${track.cover}" else track.cover

        val extrasBundle = Bundle().apply {
            putFloat("replay_gain", track.replayGain)
            putString("ARTIST_ID", track.artistId)
            putString("ALBUM_ID", track.albumId)
            putString("PLAYING_FROM", "Очередь (Артист)")
        }

        val mediaItem = MediaItem.Builder()
            .setMediaId(track.id)
            .setUri(streamUrl.toUri())
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(track.title)
                    .setArtist(track.artist)
                    .setArtworkUri(finalCoverUrl?.toUri())
                    .setAlbumTitle(track.album)
                    .setExtras(extrasBundle)
                    .build()
            )
            .build()

        val insertIndex = if (player.mediaItemCount > 0) player.currentMediaItemIndex + 1 else 0
        player.addMediaItem(insertIndex, mediaItem)
        Toast.makeText(context, "Добавлено в очередь", Toast.LENGTH_SHORT).show()
    }

    override fun onDestroyView() {
        playerListener?.let { playerController?.removeListener(it) }
        playerListener = null
        playerController = null
        if (::rvTracks.isInitialized) {
            rvTracks.adapter = null
        }
        if (::rvAlbums.isInitialized) {
            rvAlbums.adapter = null
        }
        super.onDestroyView()
    }

    companion object {
        fun newInstance(artistId: String): ArtistFragment {
            val fragment = ArtistFragment()
            val args = Bundle()
            args.putString("ARTIST_ID", artistId)
            fragment.arguments = args
            return fragment
        }
    }
}