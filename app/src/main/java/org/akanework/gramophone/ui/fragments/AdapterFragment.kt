package org.akanework.gramophone.ui.fragments

import android.content.ComponentName
import android.content.Context
import android.content.IntentSender
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import me.zhanghai.android.fastscroll.PopupTextProvider
import org.akanework.gramophone.R
import org.akanework.gramophone.logic.GramophonePlaybackService
import org.akanework.gramophone.logic.api.NetworkClient
import org.akanework.gramophone.logic.api.Track
import org.akanework.gramophone.logic.enableEdgeToEdgePaddingListener
import org.akanework.gramophone.logic.ui.ItemHeightHelper
import org.akanework.gramophone.logic.ui.MyRecyclerView
import org.akanework.gramophone.ui.adapters.*
import uk.akane.libphonograph.reader.FlowReader
import org.akanework.gramophone.logic.api.SearchResponse

class AdapterFragment : BaseFragment(null) {

    private var adapter: BaseInterface<*>? = null
    private var recyclerView: MyRecyclerView? = null
    private var swipeRefreshLayout: SwipeRefreshLayout? = null

    private var searchAdapter: OnlineSearchAdapter? = null

    private var pendingRequest: Bundle? = null
    private lateinit var intentSender: ActivityResultLauncher<IntentSenderRequest>

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        val id = arguments?.getInt("ID", -1)
        if (id == R.id.genres) {
            return createStreamingSearchLayout(inflater, container)
        }

        if (savedInstanceState?.containsKey("pendingRequest") == true) {
            pendingRequest = savedInstanceState.getBundle("pendingRequest")
        }

        val rootView = inflater.inflate(R.layout.fragment_recyclerview, container, false)
        recyclerView = rootView.findViewById(R.id.recyclerview)

        recyclerView?.setRecycledViewPool((requireParentFragment() as ViewPagerFragment).recycledViewPool)
        recyclerView?.enableEdgeToEdgePaddingListener()

        adapter = createAdapter()
        recyclerView?.adapter = adapter?.concatAdapter
        recyclerView?.setAppBar((requireParentFragment() as ViewPagerFragment).appBarLayout)

        adapter?.let { safeAdapter ->
            recyclerView?.fastScroll(safeAdapter, safeAdapter.itemHeightHelper)
        }

        (adapter as? RequestAdapter)?.let { it1 ->
            intentSender =
                registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
                    it1.onRequest(it.resultCode, pendingRequest.also { pendingRequest = null }
                        ?: throw IllegalStateException("pendingRequest null, why?"))
                }
        }

        swipeRefreshLayout = SwipeRefreshLayout(requireContext())
        if (rootView.parent != null) (rootView.parent as ViewGroup).removeView(rootView)
        swipeRefreshLayout?.addView(rootView)

        swipeRefreshLayout?.setOnRefreshListener {
            viewLifecycleOwner.lifecycleScope.launch {
                FlowReader.INSTANCE?.refresh()
                swipeRefreshLayout?.isRefreshing = false
            }
        }
        return swipeRefreshLayout
    }

    override fun onDestroyView() {
        adapter?.onFullyDrawnListener = null
        recyclerView?.adapter = null

        recyclerView = null
        swipeRefreshLayout = null
        adapter = null
        searchAdapter = null

        super.onDestroyView()
    }

    private fun createStreamingSearchLayout(inflater: LayoutInflater, container: ViewGroup?): View {
        val view = inflater.inflate(R.layout.fragment_online_search, container, false)

        val searchInput = view.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.search_input)
        val progressBar = view.findViewById<ProgressBar>(R.id.progress_bar)
        val resultsRecycler = view.findViewById<RecyclerView>(R.id.recycler_view)
        val tvSectionTitle = view.findViewById<TextView>(R.id.tv_section_title)

        // 🔥 ОБНОВЛЕННЫЕ ВЫЗОВЫ С ПАРАМЕТРОМ VIEW И АНИМАЦИЕЙ
        searchAdapter = OnlineSearchAdapter(
            onClick = { track -> playInMainService(track, searchAdapter?.currentList ?: emptyList()) },
            onMenuClick = { track, _ ->
                Toast.makeText(context, "Меню для: ${track.title}", Toast.LENGTH_SHORT).show()
            },
            onArtistClick = { artist, cardView ->
                val artistFragment = ArtistFragment.newInstance(artist.id.toString())
                (requireActivity() as org.akanework.gramophone.ui.MainActivity).startFragment(
                    frag = artistFragment,
                    sharedView = cardView,
                    transName = "artist_card_${artist.id}"
                )
            },
            onAlbumClick = { album, cardView ->
                val albumFragment = AlbumFragment.newInstance(album.id)
                (requireActivity() as org.akanework.gramophone.ui.MainActivity).startFragment(
                    frag = albumFragment,
                    sharedView = cardView,
                    transName = "album_card_${album.id}"
                )
            }
        )

        resultsRecycler.layoutManager = LinearLayoutManager(context)
        resultsRecycler.adapter = searchAdapter

        fun performSearch() {
            val query = searchInput.text.toString().trim()
            if (query.isEmpty()) return

            val imm = requireActivity().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(searchInput.windowToken, 0)

            progressBar.visibility = View.VISIBLE
            resultsRecycler.visibility = View.GONE
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

                            body?.artists?.let { mergedList.addAll(it) }
                            body?.tracks?.let { mergedList.addAll(it) }

                            if (mergedList.isEmpty()) {
                                Toast.makeText(context, "Ничего не найдено", Toast.LENGTH_SHORT).show()
                            } else {
                                searchAdapter?.submitList(mergedList)
                            }
                        } else if (response.code() == 401) {
                            Toast.makeText(context, "Сессия истекла", Toast.LENGTH_LONG).show()
                        } else {
                            Toast.makeText(context, "Ошибка сервера: ${response.code()}", Toast.LENGTH_SHORT).show()
                        }
                    }

                    override fun onFailure(call: retrofit2.Call<SearchResponse>, t: Throwable) {
                        if (!isAdded) return
                        progressBar.visibility = View.GONE
                        Toast.makeText(context, "Ошибка сети", Toast.LENGTH_SHORT).show()
                    }
                })
        }

        searchInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                performSearch()
                true
            } else false
        }

        return view
    }

    private fun playInMainService(clickedTrack: Track, allTracks: List<Track>) {
        Toast.makeText(context, "Запуск: ${clickedTrack.title}...", Toast.LENGTH_SHORT).show()
        try {
            val serviceComponent = ComponentName(requireContext(), GramophonePlaybackService::class.java)
            val sessionToken = SessionToken(requireContext(), serviceComponent)
            val controllerFuture = MediaController.Builder(requireContext(), sessionToken).buildAsync()

            controllerFuture.addListener({
                try {
                    val controller = controllerFuture.get()
                    val startIndex = allTracks.indexOf(clickedTrack).takeIf { it >= 0 } ?: 0

                    val mediaItems = allTracks.map { track ->
                        val streamUrl = "http://185.196.41.31/stream/${track.id}"
                        MediaItem.Builder()
                            .setMediaId(track.id)
                            .setUri(streamUrl.toUri())
                            .setMediaMetadata(
                                MediaMetadata.Builder()
                                    .setTitle(track.title)
                                    .setArtist(track.artist)
                                    .setArtworkUri(track.cover?.toUri())
                                    .setAlbumTitle(track.album)
                                    .setDurationMs((track.duration * 1000L).takeIf { it > 0 })
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

    fun startRequest(sender: IntentSender, data: Bundle) {
        pendingRequest = data
        intentSender.launch(IntentSenderRequest.Builder(sender).build())
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (pendingRequest != null) outState.putBundle("pendingRequest", pendingRequest)
    }

    private fun createAdapter(): BaseInterface<*> {
        val id = arguments?.getInt("ID", -1)
        return when (id) {
            R.id.songs -> SongAdapter(this)
            R.id.albums -> AlbumAdapter(this)
            R.id.artists -> ArtistAdapter(this)
            R.id.genres -> GenreAdapter(this)
            R.id.dates -> DateAdapter(this)
            R.id.folders -> DetailedFolderAdapter(this, false)
            R.id.detailed_folders -> DetailedFolderAdapter(this, true)
            R.id.playlists -> PlaylistAdapter(this)
            -1, null -> throw IllegalArgumentException("unset ID value")
            else -> throw IllegalArgumentException("invalid ID value")
        }.apply {
            onFullyDrawnListener = { (requireParentFragment() as ViewPagerFragment).maybeReportFullyDrawn(id) }
        }
    }

    abstract class BaseInterface<T : RecyclerView.ViewHolder> : MyRecyclerView.Adapter<T>(), PopupTextProvider {
        abstract val concatAdapter: ConcatAdapter
        abstract val itemHeightHelper: ItemHeightHelper?
        var onFullyDrawnListener: (() -> Unit)? = null
        abstract val context: Context
        abstract val layoutInflater: LayoutInflater
        abstract val canChangeLayout: Boolean
        abstract val sortType: StateFlow<Sorter.Type>
        abstract val sortTypes: Set<Sorter.Type>
        abstract var layoutType: BaseAdapter.LayoutType?
        abstract fun sort(type: Sorter.Type)
        abstract val itemCountForDecor: Int
    }

    interface RequestAdapter {
        fun onRequest(resultCode: Int, data: Bundle)
    }
}