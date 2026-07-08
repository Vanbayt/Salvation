package org.akanework.gramophone.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.akanework.gramophone.logic.DiscographyViewModel
import org.akanework.gramophone.ui.adapters.OnlineSearchAdapter

class DiscographyListFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: OnlineSearchAdapter
    private var filterType: String = "album"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        filterType = arguments?.getString("FILTER_TYPE") ?: "album"
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val density = requireContext().resources.displayMetrics.density
        val paddingPx = (12 * density).toInt()
        val bottomPx = (250 * density).toInt() // 🔥 УВЕЛИЧИЛИ ОТСТУП ДЛЯ МИНИ-ПЛЕЕРА

        recyclerView = RecyclerView(requireContext()).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            layoutManager = GridLayoutManager(requireContext(), 2)
            clipToPadding = false
            setPadding(paddingPx, paddingPx, paddingPx, bottomPx)
            setBackgroundColor(android.graphics.Color.TRANSPARENT) // 🔥 ЧИНИМ ФОН
        }
        return recyclerView
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = OnlineSearchAdapter(
            isGridMode = true,
            onClick = { },
            onArtistClick = { _, _ -> },
            onAlbumClick = { clickedAlbum, cardView ->
                val fragment = AlbumFragment.newInstance(clickedAlbum.id)
                (requireActivity() as org.akanework.gramophone.ui.MainActivity).startFragment(
                    frag = fragment,
                    sharedView = cardView,
                    transName = "album_card_${clickedAlbum.id}"
                )
            }
        )
        recyclerView.adapter = adapter

        val viewModel = ViewModelProvider(requireParentFragment())[DiscographyViewModel::class.java]

        viewModel.albums.observe(viewLifecycleOwner) { allAlbums ->
            val filteredList = when (filterType) {
                "album" -> allAlbums.filter { it.recordType == "album" }
                "single" -> allAlbums.filter { it.recordType == "single" || it.recordType == "ep" }
                "compilation" -> allAlbums.filter { it.recordType == "compilation" }
                else -> allAlbums
            }
            adapter.submitList(filteredList)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        recyclerView.adapter = null
    }

    companion object {
        fun newInstance(filterType: String): DiscographyListFragment {
            return DiscographyListFragment().apply {
                arguments = Bundle().apply { putString("FILTER_TYPE", filterType) }
            }
        }
    }
}