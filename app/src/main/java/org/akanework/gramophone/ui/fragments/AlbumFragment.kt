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
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.viewpager2.widget.ViewPager2
import coil3.load
import coil3.request.crossfade
import com.google.android.material.button.MaterialButton
import com.google.android.material.imageview.ShapeableImageView
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import com.google.android.material.transition.MaterialContainerTransform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.akanework.gramophone.R
import org.akanework.gramophone.logic.AlbumViewModel
import org.akanework.gramophone.logic.api.NetworkClient
import org.akanework.gramophone.logic.api.Track

class AlbumFragment : Fragment() {

    // 🔥 Стейты для управления Compose-меню поверх ViewPager
    private var selectedTrackForMenu = mutableStateOf<Track?>(null)
    private var menuExpanded = mutableStateOf(false)
    private var menuOffset = mutableStateOf(DpOffset.Zero)

    private var albumId: String? = null
    private lateinit var viewModel: AlbumViewModel

    private lateinit var btnBack: ImageButton
    private lateinit var ivCover: ShapeableImageView
    private lateinit var tvTitle: TextView
    private lateinit var tvArtistYear: TextView

    private lateinit var tabLayout: TabLayout
    private lateinit var viewPager: ViewPager2

    private lateinit var btnPlay: MaterialButton
    private lateinit var btnLike: MaterialButton

    private var tabLayoutMediator: TabLayoutMediator? = null
    private var likeCall: retrofit2.Call<Map<String, String>>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        albumId = arguments?.getString("ALBUM_ID")
        viewModel = ViewModelProvider(this)[AlbumViewModel::class.java]

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
        val xmlView = inflater.inflate(R.layout.fragment_album, container, false)

        // 2. Создаем прозрачный слой Compose для меню (поверх всего экрана)
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
                                } else {
                                    Toast.makeText(requireContext(), "Ошибка ID трека", Toast.LENGTH_SHORT).show()
                                }
                            },
                            onPlayNext = { t ->
                                menuExpanded.value = false
                                addTrackToQueueNext(t)
                            },
                            onGoToArtist = { t ->
                                menuExpanded.value = false
                                t.artistId?.let { id ->
                                    (requireActivity() as org.akanework.gramophone.ui.MainActivity)
                                        .startFragment(ArtistFragment.newInstance(id))
                                } ?: Toast.makeText(requireContext(), "Артист неизвестен", Toast.LENGTH_SHORT).show()
                            },
                            onGoToAlbum = { t ->
                                menuExpanded.value = false
                                t.albumId?.let { id ->
                                    (requireActivity() as org.akanework.gramophone.ui.MainActivity)
                                        .startFragment(newInstance(id))
                                } ?: Toast.makeText(requireContext(), "Альбом неизвестен", Toast.LENGTH_SHORT).show()
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

        view.transitionName = "album_card_${albumId}"

        // Ищем внутри view (который теперь FrameLayout)
        btnBack = view.findViewById(R.id.btn_back)
        ivCover = view.findViewById(R.id.iv_album_cover_large)
        tvTitle = view.findViewById(R.id.tv_album_title_large)
        tvArtistYear = view.findViewById(R.id.tv_album_artist_year)
        tabLayout = view.findViewById(R.id.tab_layout_album)
        viewPager = view.findViewById(R.id.view_pager_album)
        btnPlay = view.findViewById(R.id.btn_play_album)
        btnLike = view.findViewById(R.id.btn_like_album)

        btnBack.setOnClickListener {
            requireActivity().onBackPressed()
        }

        viewPager.adapter = AlbumPagerAdapter(this)
        viewPager.offscreenPageLimit = 2

        tabLayoutMediator = TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            tab.text = if (position == 0) "ТРЕКИ" else "ИНФО"
        }.apply { attach() }

        viewModel.album.observe(viewLifecycleOwner) { album ->
            tvTitle.text = album.title ?: "Неизвестный альбом"

            val type = album.recordType?.let {
                if (it == "ep") "EP" else it.replaceFirstChar { char -> char.uppercase() }
            } ?: "Релиз"

            val artist = album.artistName ?: "Неизвестный исполнитель"
            val year = album.releaseYear?.toString() ?: ""

            val subtitleParts = listOf(type, artist, year).filter { it.isNotBlank() }
            tvArtistYear.text = subtitleParts.joinToString(" • ")

            if (!album.cover.isNullOrEmpty()) {
                ivCover.load(album.cover) {
                    crossfade(true)
                    crossfade(300)
                }
            } else {
                ivCover.setImageResource(R.drawable.ic_library)
            }

            if (album.isLiked) {
                btnLike.setIconResource(R.drawable.ic_favorite_filled)
            } else {
                btnLike.setIconResource(R.drawable.ic_favorite)
            }
        }

        btnPlay.setOnClickListener {
            val currentAlbum = viewModel.album.value
            val tracks = currentAlbum?.tracks

            if (currentAlbum != null && !tracks.isNullOrEmpty()) {
                val player = (requireActivity() as org.akanework.gramophone.ui.MainActivity).getPlayer()
                if (player != null) {
                    val mediaItems = tracks.map { track ->
                        val extrasBundle = Bundle().apply {
                            putFloat("replay_gain", track.replayGain)
                            putString("ARTIST_ID", track.artistId ?: currentAlbum.artistId)
                            putString("ALBUM_ID", track.albumId ?: currentAlbum.id)
                            putString("PLAYING_FROM", "Альбом: ${currentAlbum.title}")
                        }

                        MediaItem.Builder()
                            .setMediaId(track.id)
                            .setUri("http://185.196.41.31/stream/${track.id}".toUri())
                            .setMediaMetadata(
                                MediaMetadata.Builder()
                                    .setTitle(track.title)
                                    .setArtist(track.artist)
                                    .setArtworkUri(currentAlbum.cover?.toUri())
                                    .setDurationMs((track.duration * 1000L).takeIf { it > 0 })
                                    .setExtras(extrasBundle)
                                    .build()
                            )
                            .build()
                    }

                    player.setMediaItems(mediaItems, 0, 0L)
                    player.prepare()
                    player.play()
                } else {
                    Toast.makeText(requireContext(), "Плеер еще загружается...", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(requireContext(), "Треки загружаются или альбом пуст...", Toast.LENGTH_SHORT).show()
            }
        }

        btnLike.setOnClickListener {
            val currentAlbumId = albumId ?: return@setOnClickListener
            btnLike.isEnabled = false

            likeCall?.cancel()
            likeCall = NetworkClient.getApi(requireContext()).toggleAlbumLike(currentAlbumId).apply {
                enqueue(object : retrofit2.Callback<Map<String, String>> {
                    override fun onResponse(call: retrofit2.Call<Map<String, String>>, response: retrofit2.Response<Map<String, String>>) {
                        btnLike.isEnabled = true
                        if (response.isSuccessful) {
                            val status = response.body()?.get("status")
                            if (status == "liked") {
                                btnLike.setIconResource(R.drawable.ic_favorite_filled)
                                viewModel.album.value?.isLiked = true
                                Toast.makeText(context, "Альбом добавлен", Toast.LENGTH_SHORT).show()
                            } else {
                                btnLike.setIconResource(R.drawable.ic_favorite)
                                viewModel.album.value?.isLiked = false
                                Toast.makeText(context, "Альбом удален", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }

                    override fun onFailure(call: retrofit2.Call<Map<String, String>>, t: Throwable) {
                        btnLike.isEnabled = true
                        Toast.makeText(context, "Ошибка сети", Toast.LENGTH_SHORT).show()
                    }
                })
            }
        }

        if (viewModel.album.value == null) {
            albumId?.let { id -> loadAlbumData(id) }
        }
    }

    override fun onDestroyView() {
        likeCall?.cancel()
        likeCall = null
        tabLayoutMediator?.detach()
        tabLayoutMediator = null
        if (::viewPager.isInitialized) {
            viewPager.adapter = null
        }
        super.onDestroyView()
    }

    private fun loadAlbumData(id: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val response = NetworkClient.getApi(requireContext()).getAlbumPage(id).execute()

                withContext(Dispatchers.Main) {
                    if (response.isSuccessful && response.body() != null) {
                        viewModel.setAlbum(response.body()!!)
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

    // 🔥 ПУБЛИЧНЫЙ МЕТОД ДЛЯ ДОЧЕРНЕГО ФРАГМЕНТА
    fun showTrackMenu(track: Track, anchorView: View) {
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

    // 🔥 ДОБАВЛЕНИЕ В ОЧЕРЕДЬ
    private fun addTrackToQueueNext(track: Track) {
        val player = (requireActivity() as org.akanework.gramophone.ui.MainActivity).getPlayer()
        if (player == null) {
            Toast.makeText(context, "Плеер не готов", Toast.LENGTH_SHORT).show()
            return
        }

        val streamUrl = "http://185.196.41.31/stream/${track.id}"
        val originalCover = track.cover ?: ""
        val finalCoverUrl = if (originalCover.startsWith("/")) "http://185.196.41.31$originalCover" else originalCover

        val albumObj = viewModel.album.value
        val albumTitle = albumObj?.title ?: "Альбом"
        val extrasBundle = Bundle().apply {
            putFloat("replay_gain", track.replayGain)
            putString("ARTIST_ID", track.artistId ?: albumObj?.artistId)
            putString("ALBUM_ID", track.albumId ?: albumObj?.id)
            putString("PLAYING_FROM", "Очередь ($albumTitle)")
        }

        val mediaItem = MediaItem.Builder()
            .setMediaId(track.id)
            .setUri(streamUrl.toUri())
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(track.title)
                    .setArtist(track.artist)
                    .setArtworkUri(finalCoverUrl.toUri())
                    .setAlbumTitle(track.album)
                    .setDurationMs((track.duration * 1000L).takeIf { it > 0 })
                    .setExtras(extrasBundle)
                    .build()
            )
            .build()

        val insertIndex = if (player.mediaItemCount > 0) player.currentMediaItemIndex + 1 else 0
        player.addMediaItem(insertIndex, mediaItem)
        Toast.makeText(context, "Добавлено в очередь", Toast.LENGTH_SHORT).show()
    }

    companion object {
        fun newInstance(albumId: String): AlbumFragment {
            val fragment = AlbumFragment()
            val args = Bundle()
            args.putString("ALBUM_ID", albumId)
            fragment.arguments = args
            return fragment
        }
    }
}