package org.akanework.gramophone.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.akanework.gramophone.logic.api.Album
import org.akanework.gramophone.logic.api.NetworkClient
import org.akanework.gramophone.ui.adapters.OnlineSearchAdapter
import org.akanework.gramophone.R
import androidx.core.view.doOnPreDraw
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.core.view.doOnPreDraw
class LibraryAlbumsFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: OnlineSearchAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val density = requireContext().resources.displayMetrics.density
        val paddingPx = (12 * density).toInt()
        val bottomPx = (150 * density).toInt()

        recyclerView = RecyclerView(requireContext()).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            layoutManager = androidx.recyclerview.widget.GridLayoutManager(requireContext(), 2)
            clipToPadding = false
            setPadding(paddingPx, paddingPx, paddingPx, bottomPx)
        }
        return recyclerView
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = OnlineSearchAdapter(
            isGridMode = true,
            onClick = { },
            // 🔥 Заглушка для артистов (принимает 2 параметра)
            onArtistClick = { _, _ -> },
            // 🔥 Передаем View и имя транзакции для альбомов
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

        loadFavoriteAlbums()
    }

    private fun loadFavoriteAlbums() {
        // Читаем кэш в фоне
        lifecycleScope.launch(Dispatchers.IO) {
            val cached = org.akanework.gramophone.logic.LibraryCacheManager.loadCachedAlbums(requireContext())

            withContext(Dispatchers.Main) {
                if (cached.isNotEmpty() && adapter.currentList.isEmpty()) {
                    recyclerView.alpha = 0f
                    adapter.submitList(cached)

                    recyclerView.doOnPreDraw { view ->
                        view.alpha = 1f
                        val rv = view as RecyclerView
                        rv.layoutAnimation = android.view.animation.AnimationUtils.loadLayoutAnimation(requireContext(), R.anim.layout_animation_slide_up)
                        rv.scheduleLayoutAnimation()
                    }
                }
            }
        }

        // Запрашиваем свежие данные с сервера
        NetworkClient.getApi(requireContext()).getFavoriteAlbums()
            .enqueue(object : retrofit2.Callback<List<Album>> {
                override fun onResponse(call: retrofit2.Call<List<Album>>, response: retrofit2.Response<List<Album>>) {
                    if (isAdded && response.isSuccessful) {
                        val albums = response.body() ?: emptyList()
                        org.akanework.gramophone.logic.LibraryCacheManager.saveCachedAlbums(requireContext(), albums)

                        // 🔥 Проверяем пустоту списка ДО сабмита
                        val wasEmpty = adapter.currentList.isEmpty()

                        adapter.submitList(albums)

                        // Анимируем только если до этого ничего не было
                        if (wasEmpty) {
                            recyclerView.post {
                                val animation = android.view.animation.AnimationUtils.loadLayoutAnimation(requireContext(), R.anim.layout_animation_slide_up)
                                recyclerView.layoutAnimation = animation
                                recyclerView.scheduleLayoutAnimation()
                            }
                        }
                    }
                }
                override fun onFailure(call: retrofit2.Call<List<Album>>, t: Throwable) { }
            })
    }
}