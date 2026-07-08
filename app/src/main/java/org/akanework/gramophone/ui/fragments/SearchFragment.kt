package org.akanework.gramophone.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Toast
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.appbar.AppBarLayout
import kotlinx.coroutines.*
import org.akanework.gramophone.R
import org.akanework.gramophone.logic.api.NetworkClient
import org.akanework.gramophone.logic.ui.MyRecyclerView
import org.akanework.gramophone.ui.MainActivity
import org.akanework.gramophone.ui.adapters.OnlineSearchAdapter

class SearchFragment : BaseFragment(true) {
    private lateinit var editText: EditText
    private lateinit var adapter: OnlineSearchAdapter
    private var searchJob: Job? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val rootView = inflater.inflate(R.layout.fragment_search, container, false)

        editText = rootView.findViewById(R.id.edit_text)
        val recyclerView = rootView.findViewById<MyRecyclerView>(R.id.recyclerview)
        val returnButton = rootView.findViewById<ImageButton>(R.id.return_button)

        // 🔥 ОБНОВЛЕННЫЕ ВЫЗОВЫ С ПАРАМЕТРОМ VIEW И АНИМАЦИЕЙ
        adapter = OnlineSearchAdapter(
            onClick = { track ->
                Toast.makeText(requireContext(), "Играем: ${track.title}", Toast.LENGTH_SHORT).show()
                // Тут будет запуск плеера
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

        recyclerView.layoutManager = LinearLayoutManager(activity)
        recyclerView.adapter = adapter

        editText.addTextChangedListener { text ->
            searchJob?.cancel()
            val query = text?.toString()?.trim() ?: ""
            if (query.length > 2) {
                searchJob = lifecycleScope.launch(Dispatchers.IO) {
                    delay(500)
                    performSearch(query)
                }
            }
        }

        returnButton.setOnClickListener {
            requireActivity().supportFragmentManager.popBackStack()
        }

        return rootView
    }

    private suspend fun performSearch(query: String) {
        try {
            val response = NetworkClient.getApi(requireContext()).searchMusic(query).execute()
            if (response.isSuccessful && response.body() != null) {
                val body = response.body()!!

                val mergedList = mutableListOf<Any>()
                body.artists?.let { mergedList.addAll(it) }
                body.tracks?.let { mergedList.addAll(it) }

                withContext(Dispatchers.Main) {
                    adapter.submitList(mergedList)
                }
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                // Ошибки сети не мешают пользователю
            }
        }
    }

    override fun onResume() {
        super.onResume()
        editText.requestFocus()
    }
}