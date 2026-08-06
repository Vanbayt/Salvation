package org.akanework.gramophone.ui.fragments

import android.content.ComponentName
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Rect
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
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
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.progressindicator.LinearProgressIndicator
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.akanework.gramophone.R
import org.akanework.gramophone.logic.GramophonePlaybackService
import org.akanework.gramophone.logic.api.NetworkClient
import org.akanework.gramophone.logic.api.SearchResponse
import org.akanework.gramophone.logic.api.Track
import org.akanework.gramophone.ui.adapters.OnlineSearchAdapter
import org.akanework.gramophone.ui.adapters.SearchHistoryItem

class OnlineSearchFragment : BaseFragment(true) {

    // 🔥 Стейты для управления Compose-меню
    private var selectedTrackForMenu = mutableStateOf<Track?>(null)
    private var menuExpanded = mutableStateOf(false)
    private var menuOffset = mutableStateOf(DpOffset.Zero)

    private var searchAdapter: OnlineSearchAdapter? = null
    private var resultsRecycler: RecyclerView? = null

    private var searchJob: Job? = null
    private lateinit var prefs: SharedPreferences
    private lateinit var tvSectionTitle: TextView
    private lateinit var searchInput: EditText
    private lateinit var btnClear: ImageButton
    private lateinit var progressBar: LinearProgressIndicator

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // 1. Инфлейтим родной XML
        val xmlView = inflater.inflate(R.layout.fragment_online_search, container, false)

        // ПРОЗРАЧНОСТЬ КОРНЕВОГО VIEW
        xmlView.setBackgroundColor(android.graphics.Color.TRANSPARENT)

        searchInput = xmlView.findViewById(R.id.search_input)
        btnClear = xmlView.findViewById(R.id.btn_clear_search)
        progressBar = xmlView.findViewById(R.id.progress_bar)
        resultsRecycler = xmlView.findViewById(R.id.recycler_view)
        tvSectionTitle = xmlView.findViewById(R.id.tv_section_title)

        prefs = requireContext().getSharedPreferences("salvation_search", Context.MODE_PRIVATE)

        searchAdapter = OnlineSearchAdapter(
            onClick = { track ->
                org.akanework.gramophone.logic.HistoryManager.saveToHistory(requireContext(), track)
                playInMainService(track, searchAdapter?.currentList ?: emptyList())
            },
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
            },
            onArtistClick = { artist, cardView ->
                org.akanework.gramophone.logic.HistoryManager.saveToHistory(requireContext(), artist)
                val artistFragment = ArtistFragment.newInstance(artist.id.toString())
                (requireActivity() as org.akanework.gramophone.ui.MainActivity).startFragment(
                    frag = artistFragment,
                    sharedView = cardView,
                    transName = "artist_card_${artist.id}"
                )
            },
            onAlbumClick = { album, cardView ->
                org.akanework.gramophone.logic.HistoryManager.saveToHistory(requireContext(), album)
                val albumFragment = AlbumFragment.newInstance(album.id)
                (requireActivity() as org.akanework.gramophone.ui.MainActivity).startFragment(
                    frag = albumFragment,
                    sharedView = cardView,
                    transName = "album_card_${album.id}"
                )
            },
            onHistoryClick = { query ->
                searchInput.setText(query)
                searchInput.setSelection(query.length)
                performSearch(query)
            }
        )

        resultsRecycler?.layoutManager = LinearLayoutManager(context)
        resultsRecycler?.adapter = searchAdapter

        // ПОДКЛЮЧАЕМ ПОДСВЕТКУ ИГРАЮЩЕГО ТРЕКА В ПОИСКЕ
        val mainActivity = activity as? org.akanework.gramophone.ui.MainActivity
        mainActivity?.controllerViewModel?.addControllerCallback(viewLifecycleOwner.lifecycle) { controller, _ ->
            searchAdapter?.currentlyPlayingTrackId = controller.currentMediaItem?.mediaId
            controller.addListener(object : androidx.media3.common.Player.Listener {
                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                    searchAdapter?.currentlyPlayingTrackId = mediaItem?.mediaId
                }
            })
        }

        showHistory()

        // Обработка кнопки "Очистить"
        btnClear.setOnClickListener {
            searchInput.text.clear()
            showHistory()
        }

        searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable?) {
                val query = s?.toString()?.trim() ?: ""
                searchJob?.cancel()

                btnClear.visibility = if (query.isNotEmpty()) View.VISIBLE else View.GONE

                if (query.isEmpty()) {
                    showHistory()
                } else {
                    searchJob = lifecycleScope.launch {
                        delay(600)
                        performSearch(query)
                    }
                }
            }
        })

        searchInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                val query = searchInput.text.toString().trim()
                if (query.isNotEmpty()) {
                    searchJob?.cancel()
                    hideKeyboard()
                    performSearch(query)
                }
                true
            } else false
        }

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

    private fun performSearch(query: String) {
        if (query.isEmpty()) return

        progressBar.visibility = View.VISIBLE
        resultsRecycler?.visibility = View.GONE
        tvSectionTitle.text = "Результаты поиска"

        searchAdapter?.submitList(emptyList())

        NetworkClient.getApi(requireContext())
            .searchMusic(query)
            .enqueue(object : retrofit2.Callback<SearchResponse> {
                override fun onResponse(call: retrofit2.Call<SearchResponse>, response: retrofit2.Response<SearchResponse>) {
                    if (!isAdded) return
                    progressBar.visibility = View.GONE
                    resultsRecycler?.visibility = View.VISIBLE

                    if (response.isSuccessful) {
                        val body = response.body()
                        val mergedList = mutableListOf<Any>()

                        if (!body?.artists.isNullOrEmpty()) {
                            mergedList.add(org.akanework.gramophone.ui.adapters.HeaderItem("Исполнители"))
                            mergedList.add(org.akanework.gramophone.ui.adapters.ArtistCarouselItem(body!!.artists!!))
                        }
                        if (!body?.albums.isNullOrEmpty()) {
                            mergedList.add(org.akanework.gramophone.ui.adapters.HeaderItem("Альбомы"))
                            mergedList.add(org.akanework.gramophone.ui.adapters.AlbumCarouselItem(body!!.albums!!))
                        }
                        if (!body?.tracks.isNullOrEmpty()) {
                            mergedList.add(org.akanework.gramophone.ui.adapters.HeaderItem("Треки"))
                            mergedList.addAll(body!!.tracks!!)
                        }

                        if (mergedList.isEmpty()) {
                            tvSectionTitle.text = "Ничего не найдено"
                        } else {
                            searchAdapter?.submitList(mergedList)
                            val animation = android.view.animation.AnimationUtils.loadLayoutAnimation(requireContext(), R.anim.layout_animation_slide_up)
                            resultsRecycler?.layoutAnimation = animation
                            resultsRecycler?.scheduleLayoutAnimation()

                            saveToHistory(query)
                        }
                    }
                }

                override fun onFailure(call: retrofit2.Call<SearchResponse>, t: Throwable) {
                    if (!isAdded) return
                    progressBar.visibility = View.GONE
                    tvSectionTitle.text = "Ошибка сети"
                    Toast.makeText(context, "Проверьте подключение", Toast.LENGTH_SHORT).show()
                }
            })
    }

    private fun showHistory() {
        progressBar.visibility = View.GONE
        resultsRecycler?.visibility = View.VISIBLE

        val historyList = getHistory().map { SearchHistoryItem(it) }

        if (historyList.isEmpty()) {
            tvSectionTitle.text = "Введите запрос"
            searchAdapter?.submitList(emptyList())
        } else {
            tvSectionTitle.text = "Недавние запросы"
            searchAdapter?.submitList(historyList)
        }
    }

    private fun getHistory(): List<String> {
        val historyStr = prefs.getString("history_list", "") ?: ""
        return if (historyStr.isEmpty()) emptyList() else historyStr.split("||")
    }

    private fun saveToHistory(query: String) {
        val history = getHistory().toMutableList()
        history.remove(query)
        history.add(0, query)

        if (history.size > 10) {
            history.removeAt(history.lastIndex)
        }

        prefs.edit().putString("history_list", history.joinToString("||")).apply()
    }

    private fun hideKeyboard() {
        val imm = requireActivity().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(searchInput.windowToken, 0)
    }

    private fun playInMainService(clickedTrack: Track, allTracks: List<Any>, source: String = "Поиск") {
        try {
            val serviceComponent = ComponentName(requireContext(), GramophonePlaybackService::class.java)
            val sessionToken = SessionToken(requireContext(), serviceComponent)
            val controllerFuture = MediaController.Builder(requireContext(), sessionToken).buildAsync()

            controllerFuture.addListener({
                try {
                    val controller = controllerFuture.get()

                    val trackList = allTracks.filterIsInstance<Track>()
                    val startIndex = trackList.indexOfFirst { it.id == clickedTrack.id }.coerceAtLeast(0)

                    val mediaItems = trackList.map { track ->
                        val streamUrl = "http://185.196.41.31/stream/${track.id}"

                        val extrasBundle = Bundle().apply {
                            putFloat("replay_gain", track.replayGain)
                            putString("ARTIST_ID", track.artistId)
                            putString("ALBUM_ID", track.albumId)
                            putString("PLAYING_FROM", source)
                        }

                        MediaItem.Builder()
                            .setMediaId(track.id)
                            .setUri(streamUrl.toUri())
                            .setMediaMetadata(
                                MediaMetadata.Builder()
                                    .setTitle(track.title)
                                    .setArtist(track.artist)
                                    .setArtworkUri(if (track.cover != null) track.cover.toUri() else null)
                                    .setAlbumTitle(track.album)
                                    .setDurationMs((track.duration * 1000L).takeIf { it > 0 })
                                    .setExtras(extrasBundle)
                                    .build()
                            )
                            .build()
                    }

                    controller.setMediaItems(mediaItems, startIndex, 0L)
                    controller.prepare()
                    controller.play()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }, ContextCompat.getMainExecutor(requireContext()))
        } catch (e: Exception) {
            e.printStackTrace()
        }
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

        val extrasBundle = Bundle().apply {
            putFloat("replay_gain", track.replayGain)
            putString("ARTIST_ID", track.artistId)
            putString("ALBUM_ID", track.albumId)
            putString("PLAYING_FROM", "Очередь (Поиск)")
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

    override fun onDestroyView() {
        searchJob?.cancel()
        resultsRecycler?.adapter = null
        searchAdapter = null
        super.onDestroyView()
    }
}