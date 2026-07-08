package org.akanework.gramophone.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.akanework.gramophone.R
import org.akanework.gramophone.logic.api.Artist
import org.akanework.gramophone.logic.api.NetworkClient
import org.akanework.gramophone.ui.adapters.OnlineSearchAdapter
import androidx.core.view.doOnPreDraw
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.core.view.doOnPreDraw
class LibraryArtistsFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: OnlineSearchAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val density = requireContext().resources.displayMetrics.density
        val paddingPx = (12 * density).toInt()
        val bottomPx = (150 * density).toInt()

        recyclerView = RecyclerView(requireContext()).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            layoutManager = GridLayoutManager(requireContext(), 2)
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
            // 🔥 Передаем View и имя транзакции для артистов
            onArtistClick = { clickedArtist, cardView ->
                val fragment = ArtistFragment.newInstance(clickedArtist.id)
                (requireActivity() as org.akanework.gramophone.ui.MainActivity).startFragment(
                    frag = fragment,
                    sharedView = cardView,
                    transName = "artist_card_${clickedArtist.id}"
                )
            },
            // 🔥 Заглушка для альбомов (принимает 2 параметра)
            onAlbumClick = { _, _ -> }
        )
        recyclerView.adapter = adapter

        loadFavoriteArtists()
    }

    private fun loadFavoriteArtists() {
        // Читаем кэш в фоне (плавный UI)
        lifecycleScope.launch(Dispatchers.IO) {
            val cached = org.akanework.gramophone.logic.LibraryCacheManager.loadCachedArtists(requireContext())

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
        NetworkClient.getApi(requireContext()).getFavoriteArtists()
            .enqueue(object : retrofit2.Callback<List<Artist>> {
                override fun onResponse(call: retrofit2.Call<List<Artist>>, response: retrofit2.Response<List<Artist>>) {
                    if (isAdded && response.isSuccessful) {
                        val artists = response.body() ?: emptyList()
                        org.akanework.gramophone.logic.LibraryCacheManager.saveCachedArtists(requireContext(), artists)

                        // 🔥 Проверяем, был ли список пуст ДО добавления новых данных
                        val wasEmpty = adapter.currentList.isEmpty()

                        adapter.submitList(artists)

                        // Если кэша не было (список был пуст), запускаем анимацию
                        if (wasEmpty) {
                            recyclerView.post {
                                val animation = android.view.animation.AnimationUtils.loadLayoutAnimation(requireContext(), R.anim.layout_animation_slide_up)
                                recyclerView.layoutAnimation = animation
                                recyclerView.scheduleLayoutAnimation()
                            }
                        }
                    }
                }
                override fun onFailure(call: retrofit2.Call<List<Artist>>, t: Throwable) {}
            })
    }
}