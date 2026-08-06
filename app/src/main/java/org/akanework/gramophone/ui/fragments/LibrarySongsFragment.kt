package org.akanework.gramophone.ui.fragments

import android.content.ComponentName
import android.graphics.Rect
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.widget.FrameLayout
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.core.view.doOnPreDraw
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil3.compose.AsyncImage
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.akanework.gramophone.R
import org.akanework.gramophone.logic.GramophonePlaybackService
import org.akanework.gramophone.logic.LibraryCacheManager
import org.akanework.gramophone.logic.LibrarySearchViewModel
import org.akanework.gramophone.logic.LikeCache
import org.akanework.gramophone.logic.api.NetworkClient
import org.akanework.gramophone.logic.api.Playlist
import org.akanework.gramophone.logic.api.PlaylistTrackAddRequest
import org.akanework.gramophone.logic.api.Track
import org.akanework.gramophone.ui.adapters.OnlineSearchAdapter
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class LibrarySongsFragment : BaseFragment(true) {

    private val PAGE_LIMIT = 50
    private val PRELOAD_THRESHOLD = 5

    private val tracksDataset = mutableListOf<Track>()
    private var isLoading = false
    private var isLastPage = false
    private var isPreparingPlayback = false

    private var mediaController: MediaController? = null
    private var rvTracks: RecyclerView? = null
    private var adapter: OnlineSearchAdapter? = null
    private var currentCall: Call<List<Track>>? = null

    private lateinit var btnShuffle: MaterialButton
    private lateinit var tvTrackCount: TextView

    private val searchViewModel: LibrarySearchViewModel by activityViewModels()
    private var currentSearchQuery = ""

    enum class SortMode(val param: String) {
        NEWEST("newest"), OLDEST("oldest"), TITLE_AZ("title_az"), ARTIST_AZ("artist_az")
    }
    private var currentSortMode = SortMode.NEWEST

    // 🔥 Стейты для управления Compose-меню поверх XML
    private var selectedTrackForMenu = mutableStateOf<Track?>(null)
    private var menuExpanded = mutableStateOf(false)
    private var menuOffset = mutableStateOf(DpOffset.Zero)
    private var showPlaylistDialog = mutableStateOf(false)

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // 1. Инфлейтим наш стандартный XML
        val xmlView = inflater.inflate(R.layout.fragment_library_songs, container, false)

        // 2. Добавляем поверх него ComposeView для плавающих меню
        val composeView = ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                val isDark = androidx.compose.foundation.isSystemInDarkTheme()
                val dynamicColor = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S
                val colors = when {
                    dynamicColor && isDark -> androidx.compose.material3.dynamicDarkColorScheme(requireContext())
                    dynamicColor && !isDark -> androidx.compose.material3.dynamicLightColorScheme(requireContext())
                    isDark -> darkColorScheme()
                    else -> lightColorScheme()
                }

                MaterialTheme(colorScheme = colors) {
                    val track = selectedTrackForMenu.value
                    if (track != null) {
                        TrackContextMenu(
                            expanded = menuExpanded.value, // 🔥 Передаем стейт внутрь!
                            track = track,
                            offset = menuOffset.value,
                            onDismiss = { menuExpanded.value = false },
                            onAddToPlaylist = {
                                menuExpanded.value = false
                                // ... дальше твой код ...
                                // 🔥 ИСПОЛЬЗУЕМ ТВОЙ РОДНОЙ КЛАСС ДЛЯ ПЛЕЙЛИСТОВ!
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
                                } ?: Toast.makeText(requireContext(), "ID артиста неизвестен", Toast.LENGTH_SHORT).show()
                            },
                            onGoToAlbum = { t ->
                                menuExpanded.value = false
                                t.albumId?.let { id ->
                                    (requireActivity() as org.akanework.gramophone.ui.MainActivity)
                                        .startFragment(AlbumFragment.newInstance(id))
                                } ?: Toast.makeText(requireContext(), "ID альбома неизвестен", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                }
            }
        }

        // 3. Оборачиваем XML и ComposeView во FrameLayout, чтобы они лежали друг на друге
        val wrapper = FrameLayout(requireContext()).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            addView(xmlView)
            addView(composeView)
        }

        return wrapper
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.setBackgroundColor(android.graphics.Color.TRANSPARENT)

        rvTracks = view.findViewById(R.id.rv_liked_tracks)
        btnShuffle = view.findViewById(R.id.btn_shuffle)
        tvTrackCount = view.findViewById(R.id.tv_track_count)
        rvTracks?.setBackgroundColor(android.graphics.Color.TRANSPARENT)

        val layoutManager = LinearLayoutManager(requireContext())
        rvTracks?.layoutManager = layoutManager

        initMediaController()

        adapter = OnlineSearchAdapter(
            onClick = { track -> playFullPlaylist(clickedTrack = track, shuffle = false) },
            // 🔥 Вызываем меню Compose и вычисляем координаты
            onMenuClick = { track, anchorView ->
                val rect = Rect()
                anchorView.getGlobalVisibleRect(rect)
                val density = resources.displayMetrics.density

                // 🔥 СМЕЩАЕМ: еще левее (-160.dp) и немного выше (-32.dp)
                menuOffset.value = DpOffset(
                    x = (rect.left / density).dp - 160.dp,
                    y = (rect.top / density).dp - 32.dp
                )

                selectedTrackForMenu.value = track
                menuExpanded.value = true
            }
        )
        rvTracks?.adapter = adapter

        btnShuffle.setOnClickListener {
            val currentList = adapter?.currentList ?: emptyList()
            if (currentList.isNotEmpty()) {
                playFullPlaylist(clickedTrack = currentList.random(), shuffle = true)
            }
        }

        view.findViewById<android.widget.ImageButton>(R.id.btn_sort).setOnClickListener { showSortMenu() }

        rvTracks?.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                if (dy <= 0 || isLoading || isLastPage) return
                if (layoutManager.itemCount > 0 && layoutManager.findLastVisibleItemPosition() >= (layoutManager.itemCount - PRELOAD_THRESHOLD)) {
                    loadFavorites()
                }
            }
        })

        tracksDataset.clear()
        updateTrackCountUi(0)
        loadFavorites()

        searchViewModel.searchQuery.observe(viewLifecycleOwner, Observer { query: String ->
            if (currentSearchQuery == query && tracksDataset.isNotEmpty()) return@Observer
            currentSearchQuery = query
            resetListAndReload()
        })

        val mainActivity = activity as? org.akanework.gramophone.ui.MainActivity
        mainActivity?.controllerViewModel?.addControllerCallback(viewLifecycleOwner.lifecycle) { controller, _ ->
            adapter?.currentlyPlayingTrackId = controller.currentMediaItem?.mediaId
            controller.addListener(object : androidx.media3.common.Player.Listener {
                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                    adapter?.currentlyPlayingTrackId = mediaItem?.mediaId
                }
            })
        }
    }

    private fun loadFavorites() {
        if (isLoading || isLastPage) return
        val safeContext = context ?: return

        val currentSkip = tracksDataset.size
        val queryParam = currentSearchQuery.takeIf { it.isNotEmpty() }

        // ПАРАЛЛЕЛЬНО ЧИТАЕМ КЭШ В ФОНОВОМ ПОТОКЕ (Без фризов UI)
        if (currentSkip == 0 && queryParam == null) {
            lifecycleScope.launch(Dispatchers.IO) {
                val cached = LibraryCacheManager.loadCachedTracks(safeContext, currentSortMode.param)

                withContext(Dispatchers.Main) {
                    if (cached.isNotEmpty() && tracksDataset.isEmpty()) {
                        tracksDataset.clear()
                        tracksDataset.addAll(cached)

                        rvTracks?.alpha = 0f
                        adapter?.submitList(cached.toList())

                        rvTracks?.doOnPreDraw { view ->
                            view.alpha = 1f
                            val rv = view as RecyclerView
                            rv.layoutAnimation = AnimationUtils.loadLayoutAnimation(requireContext(), R.anim.layout_animation_slide_up)
                            rv.scheduleLayoutAnimation()
                        }
                        updateTrackCountUi(cached.size)
                    }
                }
            }
        }

        isLoading = true

        currentCall = NetworkClient.getApi(safeContext)
            .getFavorites(skip = currentSkip, limit = PAGE_LIMIT, query = queryParam, sortMode = currentSortMode.param)

        currentCall?.enqueue(object : Callback<List<Track>> {
            override fun onResponse(call: Call<List<Track>>, response: Response<List<Track>>) {
                isLoading = false
                if (response.isSuccessful) {
                    val newTracks = response.body() ?: emptyList()

                    if (newTracks.isEmpty()) {
                        isLastPage = true
                        if (currentSkip == 0) Toast.makeText(safeContext, if (queryParam != null) "Ничего не найдено" else "Список пуст", Toast.LENGTH_SHORT).show()
                        return
                    }

                    if (currentSkip == 0) {
                        val wasEmpty = tracksDataset.isEmpty()
                        tracksDataset.clear()
                        tracksDataset.addAll(newTracks)

                        if (queryParam == null) {
                            LibraryCacheManager.saveCachedTracks(safeContext, currentSortMode.param, newTracks)
                            syncFullLibraryBackground()
                        }

                        adapter?.submitList(tracksDataset.toList())

                        // Анимируем сеть, только если кэша не было
                        if (wasEmpty) {
                            rvTracks?.post {
                                rvTracks?.layoutAnimation = AnimationUtils.loadLayoutAnimation(requireContext(), R.anim.layout_animation_slide_up)
                                rvTracks?.scheduleLayoutAnimation()
                            }
                        }
                    } else {
                        tracksDataset.addAll(newTracks)
                        adapter?.submitList(tracksDataset.toList())
                    }

                    val totalCountHeader = response.headers()["X-Total-Count"]
                    val totalTracks = totalCountHeader?.toIntOrNull() ?: tracksDataset.size
                    updateTrackCountUi(totalTracks)

                    if (newTracks.size < PAGE_LIMIT) isLastPage = true
                }
            }

            override fun onFailure(call: Call<List<Track>>, t: Throwable) {
                isLoading = false
            }
        })
    }

    private fun syncFullLibraryBackground() {
        val safeContext = context ?: return
        NetworkClient.getApi(safeContext)
            .getFavorites(skip = 0, limit = 5000, query = null, sortMode = currentSortMode.param)
            .enqueue(object : Callback<List<Track>> {
                override fun onResponse(call: Call<List<Track>>, response: Response<List<Track>>) {
                    if (response.isSuccessful) {
                        response.body()?.let { allFavorites ->
                            lifecycleScope.launch {
                                LibraryCacheManager.saveFullPlaylist(safeContext, currentSortMode.param, allFavorites)
                            }
                        }
                    }
                }
                override fun onFailure(call: Call<List<Track>>, t: Throwable) {}
            })
    }

    private fun playFullPlaylist(clickedTrack: Track?, shuffle: Boolean) {
        if (isPreparingPlayback) return
        val safeContext = context ?: return
        isPreparingPlayback = true

        lifecycleScope.launch {
            val fullLibrary = LibraryCacheManager.loadFullPlaylist(safeContext, currentSortMode.param)

            if (fullLibrary.isNotEmpty()) {
                launchExoPlayer(clickedTrack ?: fullLibrary.first(), fullLibrary, shuffle)
                isPreparingPlayback = false
            } else {
                val fallbackList = tracksDataset.toList()
                if (fallbackList.isNotEmpty()) {
                    launchExoPlayer(clickedTrack ?: fallbackList.first(), fallbackList, shuffle)
                }
                Toast.makeText(safeContext, "Синхронизация...", Toast.LENGTH_SHORT).show()
                NetworkClient.getApi(safeContext).getFavorites(0, 5000, null, currentSortMode.param).enqueue(object : Callback<List<Track>> {
                    override fun onResponse(call: Call<List<Track>>, response: Response<List<Track>>) {
                        isPreparingPlayback = false
                        if (response.isSuccessful) response.body()?.let { launchExoPlayer(clickedTrack ?: it.first(), it, shuffle) }
                    }
                    override fun onFailure(call: Call<List<Track>>, t: Throwable) { isPreparingPlayback = false }
                })
            }
        }
    }

    private fun resetListAndReload() {
        currentCall?.cancel()
        tracksDataset.clear()
        adapter?.submitList(emptyList())
        isLastPage = false
        isLoading = false
        updateTrackCountUi(0)
        loadFavorites()
    }

    private fun updateTrackCountUi(count: Int) {
        val word = when {
            count % 10 == 1 && count % 100 != 11 -> "трек"
            count % 10 in 2..4 && (count % 100 < 10 || count % 100 >= 20) -> "трека"
            else -> "треков"
        }
        tvTrackCount.text = "$count $word"
    }

    private fun showSortMenu() {
        val bottomSheetDialog = BottomSheetDialog(requireContext())
        val sheetView = layoutInflater.inflate(R.layout.layout_bottom_sheet_sort, null)
        bottomSheetDialog.setContentView(sheetView)
        val rgOptions = sheetView.findViewById<RadioGroup>(R.id.rg_sort_options)

        val checkedId = when (currentSortMode) {
            SortMode.NEWEST -> R.id.rb_newest
            SortMode.OLDEST -> R.id.rb_oldest
            SortMode.TITLE_AZ -> R.id.rb_title_az
            SortMode.ARTIST_AZ -> R.id.rb_artist_az
        }
        rgOptions.check(checkedId)

        rgOptions.setOnCheckedChangeListener { _, selectedId ->
            val newSortMode = when (selectedId) {
                R.id.rb_newest -> SortMode.NEWEST
                R.id.rb_oldest -> SortMode.OLDEST
                R.id.rb_title_az -> SortMode.TITLE_AZ
                R.id.rb_artist_az -> SortMode.ARTIST_AZ
                else -> SortMode.NEWEST
            }
            if (currentSortMode != newSortMode) {
                currentSortMode = newSortMode
                resetListAndReload()
            }
            bottomSheetDialog.dismiss()
        }
        bottomSheetDialog.show()
    }

    private fun initMediaController() {
        try {
            val serviceComponent = ComponentName(requireContext(), GramophonePlaybackService::class.java)
            val sessionToken = SessionToken(requireContext(), serviceComponent)
            val controllerFuture = MediaController.Builder(requireContext(), sessionToken).buildAsync()

            controllerFuture.addListener({
                try { mediaController = controllerFuture.get() } catch (e: Exception) { e.printStackTrace() }
            }, ContextCompat.getMainExecutor(requireContext()))
        } catch (e: Exception) { e.printStackTrace() }
    }

    private fun launchExoPlayer(clickedTrack: Track, trackList: List<Track>, shuffle: Boolean) {
        val controller = mediaController ?: return
        try {
            val startIdx = trackList.indexOfFirst { it.id == clickedTrack.id }.takeIf { it >= 0 } ?: 0

            val mediaItems = trackList.map { track ->
                val streamUrl = "http://185.196.41.31/stream/${track.id}"
                val originalCover = track.cover ?: ""
                val finalCoverUrl = if (originalCover.startsWith("/")) "http://185.196.41.31$originalCover" else originalCover

                val extrasBundle = Bundle().apply {
                    putFloat("replay_gain", track.replayGain)
                    putString("ARTIST_ID", track.artistId)
                    putString("ALBUM_ID", track.albumId)
                    putString("PLAYING_FROM", "Медиатека")
                }

                MediaItem.Builder()
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
        } catch (e: Exception) { e.printStackTrace() }
    }

    // 🔥 ДОБАВЛЕНИЕ В ОЧЕРЕДЬ
    private fun addTrackToQueueNext(track: Track) {
        val controller = mediaController
        if (controller == null) {
            Toast.makeText(context, "Плеер не готов", Toast.LENGTH_SHORT).show()
            return
        }

        val streamUrl = "http://185.196.41.31/stream/${track.id}"
        val originalCover = track.cover ?: ""
        val finalCoverUrl = if (originalCover.startsWith("/")) "http://185.196.41.31$originalCover" else originalCover

        val extrasBundle = Bundle().apply {
            putFloat("replay_gain", track.replayGain)
            putString("ARTIST_ID", track.artistId)
            putString("ALBUM_ID", track.albumId)
            putString("PLAYING_FROM", "Очередь")
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

        val insertIndex = if (controller.mediaItemCount > 0) controller.currentMediaItemIndex + 1 else 0
        controller.addMediaItem(insertIndex, mediaItem)
        Toast.makeText(context, "Добавлено в очередь", Toast.LENGTH_SHORT).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        currentCall?.cancel()
        rvTracks?.adapter = null
        rvTracks = null
    }
}

// ==========================================
// КОМПОНЕНТЫ COMPOSE ДЛЯ МЕНЮ И ПЛЕЙЛИСТОВ
// ==========================================

@Composable
fun TrackContextMenu(
    expanded: Boolean, // 🔥 1. Добавили новый параметр
    track: Track,
    offset: DpOffset,
    onDismiss: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onPlayNext: (Track) -> Unit,
    onGoToArtist: (Track) -> Unit,
    onGoToAlbum: (Track) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var isLiked by remember { mutableStateOf(LikeCache.likedTracks.contains(track.id)) }

    DropdownMenu(
        expanded = expanded, // 🔥 2. Используем переменную вместо `true`
        onDismissRequest = onDismiss,
        offset = offset,
        shape = RoundedCornerShape(16.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        // ... (внутренности DropdownMenuItem остаются без изменений)
        DropdownMenuItem(
            text = { Text(if (isLiked) "Удалить из избранного" else "Добавить в избранное") },
            leadingIcon = {
                Icon(
                    painter = painterResource(if (isLiked) R.drawable.ic_favorite_filled else R.drawable.ic_favorite),
                    contentDescription = null,
                    tint = if (isLiked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )
            },
            onClick = {
                val newState = !isLiked
                isLiked = newState
                if (newState) LikeCache.likedTracks.add(track.id) else LikeCache.likedTracks.remove(track.id)

                coroutineScope.launch(Dispatchers.IO) {
                    try {
                        val api = NetworkClient.getApi(context)
                        if (newState) api.likeTrack(track.id).execute() else api.unlikeTrack(track.id).execute()
                    } catch (e: Exception) {
                        if (!newState) LikeCache.likedTracks.add(track.id) else LikeCache.likedTracks.remove(track.id)
                    }
                }
                onDismiss()
            }
        )

        DropdownMenuItem(
            text = { Text("Играть следующим") },
            leadingIcon = { Icon(painterResource(R.drawable.ic_playlist_play), null) },
            onClick = { onPlayNext(track) }
        )

        DropdownMenuItem(
            text = { Text("Добавить в плейлист") },
            leadingIcon = { Icon(painterResource(R.drawable.ic_playlist_add), null) },
            onClick = onAddToPlaylist
        )

        DropdownMenuItem(
            text = { Text("Перейти к артисту") },
            leadingIcon = { Icon(painterResource(R.drawable.ic_person), null) },
            onClick = { onGoToArtist(track) }
        )

        DropdownMenuItem(
            text = { Text("Перейти к альбому") },
            leadingIcon = { Icon(painterResource(R.drawable.ic_album), null) },
            onClick = { onGoToAlbum(track) }
        )
    }
}

