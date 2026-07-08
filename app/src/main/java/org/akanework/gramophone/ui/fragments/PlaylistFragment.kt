package org.akanework.gramophone.ui.fragments

import android.content.ComponentName
import android.graphics.Rect
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
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
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.CollapsingToolbarLayout
import com.google.android.material.button.MaterialButton
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.google.android.material.textfield.TextInputEditText
import org.akanework.gramophone.R
import org.akanework.gramophone.logic.GramophonePlaybackService
import org.akanework.gramophone.logic.api.NetworkClient
import org.akanework.gramophone.ui.adapters.OnlineSearchAdapter
import org.akanework.gramophone.logic.api.Track
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class PlaylistFragment : BaseFragment(true) {

    // 🔥 Стейты для управления Compose-меню
    private var selectedTrackForMenu = mutableStateOf<Track?>(null)
    private var menuExpanded = mutableStateOf(false)
    private var menuOffset = mutableStateOf(DpOffset.Zero)

    private val PAGE_LIMIT = 50
    private val PRELOAD_THRESHOLD = 5

    private val tracksDataset = mutableListOf<Track>()
    private var isLoading = false
    private var isLastPage = false
    private var isPreparingPlayback = false
    private var currentQuery = "" // Для локального поиска

    // Подключение к плееру (чтобы кнопка Play менялась на Pause)
    private var mediaController: MediaController? = null

    // UI элементы
    private var rvTracks: RecyclerView? = null
    private var adapter: OnlineSearchAdapter? = null
    private var progressBar: CircularProgressIndicator? = null
    private var currentCall: Call<List<Track>>? = null
    private lateinit var btnPlayAll: MaterialButton
    private lateinit var btnShuffleToggle: MaterialButton
    private lateinit var tvTrackCount: TextView
    private lateinit var etSearchPlaylist: TextInputEditText

    private val TAG = "GRAM_DEBUG"

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // 1. Инфлейтим родной XML
        val xmlView = inflater.inflate(R.layout.fragment_playlist, container, false)

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
                                        .startFragment(AlbumFragment.newInstance(id))
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

        // Инициализация UI (ищем внутри view, который теперь FrameLayout, но id те же)
        val collapsingToolbar = view.findViewById<CollapsingToolbarLayout>(R.id.collapsingToolbar)
        btnPlayAll = view.findViewById(R.id.btn_play_all)
        btnShuffleToggle = view.findViewById(R.id.btn_shuffle_toggle)
        tvTrackCount = view.findViewById(R.id.tv_track_count)
        etSearchPlaylist = view.findViewById(R.id.et_search_playlist)
        rvTracks = view.findViewById(R.id.rv_playlist_tracks)
        progressBar = view.findViewById(R.id.loading_indicator)

        val layoutManager = LinearLayoutManager(requireContext())
        rvTracks?.layoutManager = layoutManager

        // Инициализация связи с плеером (чтобы кнопка менялась)
        initMediaController()

        // === НАСТРОЙКА КНОПОК ===
        btnShuffleToggle.addOnCheckedChangeListener { buttonView, isChecked ->
            val msg = if (isChecked) "Перемешивание включено" else "Перемешивание выключено"
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        }

        btnPlayAll.setOnClickListener {
            val controller = mediaController
            if (controller != null && controller.playbackState != Player.STATE_IDLE) {
                if (controller.isPlaying) {
                    controller.pause()
                } else {
                    controller.play()
                }
            } else {
                val currentList = adapter?.currentList ?: emptyList()
                if (currentList.isNotEmpty()) {
                    playFullPlaylist(clickedTrack = currentList.first(), shuffle = btnShuffleToggle.isChecked)
                }
            }
        }

        // === НАСТРОЙКА ПОИСКА ===
        etSearchPlaylist.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                currentQuery = s?.toString()?.trim() ?: ""
                filterAndApply()
            }
        })

        // === НАСТРОЙКА АДАПТЕРА ===
        adapter = OnlineSearchAdapter(
            onClick = { track -> playFullPlaylist(clickedTrack = track, shuffle = btnShuffleToggle.isChecked) },
            onMenuClick = { track, anchorView ->
                // 🔥 Вычисляем координаты и открываем меню
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
        )
        rvTracks?.adapter = adapter

        // Подключаем выделение текущего трека
        val mainActivity = activity as? org.akanework.gramophone.ui.MainActivity
        mainActivity?.controllerViewModel?.addControllerCallback(viewLifecycleOwner.lifecycle) { controller, _ ->
            adapter?.currentlyPlayingTrackId = controller.currentMediaItem?.mediaId
            controller.addListener(object : androidx.media3.common.Player.Listener {
                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                    adapter?.currentlyPlayingTrackId = mediaItem?.mediaId
                }
            })
        }

        // Слушатель скролла (пагинация)
        rvTracks?.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                if (dy <= 0) return
                if (isLoading || isLastPage) return

                val totalItemCount = layoutManager.itemCount
                val lastVisibleItemPosition = layoutManager.findLastVisibleItemPosition()

                if (totalItemCount > 0 && lastVisibleItemPosition >= (totalItemCount - PRELOAD_THRESHOLD)) {
                    loadFavorites()
                }
            }
        })

        val tvPlaylistTitle = view.findViewById<TextView>(R.id.tv_playlist_title)
        val isLikedTracks = arguments?.getBoolean("IS_LIKED_TRACKS") ?: false

        if (isLikedTracks) {
            tvPlaylistTitle.text = "Любимые треки"
            tracksDataset.clear()
            loadFavorites()
        } else {
            tvPlaylistTitle.text = "Плейлист"
        }
    }

    // === ФИЛЬТРАЦИЯ И СЧЕТЧИК ===
    private fun filterAndApply() {
        val filtered = if (currentQuery.isEmpty()) {
            tracksDataset.toList()
        } else {
            tracksDataset.filter {
                it.title.contains(currentQuery, ignoreCase = true) ||
                        it.artist.contains(currentQuery, ignoreCase = true)
            }
        }
        adapter?.submitList(filtered)
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

    // === СЕТЕВАЯ ЗАГРУЗКА ===
    private fun loadFavorites() {
        if (isLoading || isLastPage) return
        isLoading = true
        val currentSkip = tracksDataset.size

        if (currentSkip == 0) progressBar?.visibility = View.VISIBLE

        currentCall = NetworkClient.getApi(requireContext())
            .getFavorites(skip = currentSkip, limit = PAGE_LIMIT)

        currentCall?.enqueue(object : Callback<List<Track>> {
            override fun onResponse(call: Call<List<Track>>, response: Response<List<Track>>) {
                progressBar?.visibility = View.GONE
                isLoading = false

                if (response.isSuccessful) {
                    val newTracks = response.body() ?: emptyList()
                    if (newTracks.isEmpty()) {
                        isLastPage = true
                        if (currentSkip == 0) Toast.makeText(context, "Список пуст", Toast.LENGTH_SHORT).show()
                        return
                    }

                    tracksDataset.addAll(newTracks)
                    filterAndApply()

                    if (newTracks.size < PAGE_LIMIT) {
                        isLastPage = true
                    }
                } else {
                    Toast.makeText(context, "Ошибка сервера: ${response.code()}", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onFailure(call: Call<List<Track>>, t: Throwable) {
                isLoading = false
                progressBar?.visibility = View.GONE
                Toast.makeText(context, "Слабое интернет-соединение", Toast.LENGTH_SHORT).show()
            }
        })
    }

    // --- ПОДКЛЮЧЕНИЕ К ПЛЕЕРУ И СМЕНА ИКОНКИ ---
    private fun initMediaController() {
        try {
            val serviceComponent = ComponentName(requireContext(), GramophonePlaybackService::class.java)
            val sessionToken = SessionToken(requireContext(), serviceComponent)
            val controllerFuture = MediaController.Builder(requireContext(), sessionToken).buildAsync()

            controllerFuture.addListener({
                try {
                    val controller = controllerFuture.get()
                    mediaController = controller

                    updatePlayPauseButton(controller.isPlaying)

                    controller.addListener(object : Player.Listener {
                        override fun onIsPlayingChanged(isPlaying: Boolean) {
                            updatePlayPauseButton(isPlaying)
                        }
                    })
                } catch (e: Exception) { e.printStackTrace() }
            }, ContextCompat.getMainExecutor(requireContext()))
        } catch (e: Exception) { e.printStackTrace() }
    }

    private fun updatePlayPauseButton(isPlaying: Boolean) {
        if (isPlaying) {
            btnPlayAll.setIconResource(R.drawable.ic_pause)
        } else {
            btnPlayAll.setIconResource(R.drawable.ic_play)
        }
    }

    // --- ЛОГИКА ВОСПРОИЗВЕДЕНИЯ ВСЕЙ БАЗЫ ---
    private fun playFullPlaylist(clickedTrack: Track?, shuffle: Boolean) {
        if (isPreparingPlayback) return
        isPreparingPlayback = true

        Toast.makeText(context, "Синхронизация...", Toast.LENGTH_SHORT).show()
        progressBar?.visibility = View.VISIBLE

        NetworkClient.getApi(requireContext())
            .getFavorites(skip = 0, limit = 5000)
            .enqueue(object : Callback<List<Track>> {
                override fun onResponse(call: Call<List<Track>>, response: Response<List<Track>>) {
                    isPreparingPlayback = false
                    progressBar?.visibility = View.GONE

                    if (response.isSuccessful) {
                        val allFavorites = response.body() ?: tracksDataset
                        if (allFavorites.isEmpty()) return
                        launchExoPlayer(clickedTrack ?: allFavorites.first(), allFavorites, shuffle)
                    } else {
                        Toast.makeText(context, "Сбой сети. Играет локальный кэш.", Toast.LENGTH_LONG).show()
                        if (tracksDataset.isNotEmpty()) {
                            launchExoPlayer(clickedTrack ?: tracksDataset.first(), tracksDataset, shuffle)
                        }
                    }
                }

                override fun onFailure(call: Call<List<Track>>, t: Throwable) {
                    isPreparingPlayback = false
                    progressBar?.visibility = View.GONE
                    Toast.makeText(context, "Нет сети. Играет локальный кэш.", Toast.LENGTH_LONG).show()
                    if (tracksDataset.isNotEmpty()) {
                        launchExoPlayer(clickedTrack ?: tracksDataset.first(), tracksDataset, shuffle)
                    }
                }
            })
    }

    private fun launchExoPlayer(clickedTrack: Track, trackList: List<Track>, shuffle: Boolean) {
        val controller = mediaController
        if (controller == null) {
            Toast.makeText(context, "Плеер еще не готов", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val listToSend = if (shuffle) {
                trackList.shuffled().take(100)
            } else {
                val startIdx = trackList.indexOfFirst { it.id == clickedTrack.id }.takeIf { it >= 0 } ?: 0
                trackList.drop(startIdx).take(100)
            }

            val mediaItems = listToSend.map { track ->
                val streamUrl = "http://185.196.41.31/stream/${track.id}"

                val extrasBundle = Bundle().apply {
                    putFloat("replay_gain", track.replayGain)
                    putString("ARTIST_ID", track.artistId)
                    putString("ALBUM_ID", track.albumId)
                    putString("PLAYING_FROM", "Плейлист")
                }

                MediaItem.Builder()
                    .setMediaId(track.id)
                    .setUri(streamUrl.toUri())
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setTitle(track.title)
                            .setArtist(track.artist)
                            .setArtworkUri(track.cover?.toUri())
                            .setAlbumTitle(track.album)
                            .setExtras(extrasBundle)
                            .build()
                    )
                    .build()
            }

            controller.shuffleModeEnabled = false
            controller.setMediaItems(mediaItems, 0, 0L)
            controller.prepare()
            controller.play()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // 🔥 ДОБАВЛЕНИЕ В ОЧЕРЕДЬ
    private fun addTrackToQueueNext(track: Track) {
        val player = mediaController
        if (player == null) {
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
            putString("PLAYING_FROM", "Очередь (Плейлист)")
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
                    .setExtras(extrasBundle)
                    .build()
            )
            .build()

        val insertIndex = if (player.mediaItemCount > 0) player.currentMediaItemIndex + 1 else 0
        player.addMediaItem(insertIndex, mediaItem)
        Toast.makeText(context, "Добавлено в очередь", Toast.LENGTH_SHORT).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        currentCall?.cancel()
        rvTracks = null
    }
}