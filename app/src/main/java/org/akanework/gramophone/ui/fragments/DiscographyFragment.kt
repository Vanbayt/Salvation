package org.akanework.gramophone.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.RecyclerView
import org.akanework.gramophone.R
import org.akanework.gramophone.logic.api.Album
import org.akanework.gramophone.logic.api.NetworkClient
import org.akanework.gramophone.ui.adapters.OnlineSearchAdapter
import androidx.lifecycle.ViewModelProvider
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator

import org.akanework.gramophone.logic.DiscographyViewModel


import androidx.viewpager2.adapter.FragmentStateAdapter

class DiscographyPagerAdapter(fragment: Fragment) : FragmentStateAdapter(fragment) {
    override fun getItemCount(): Int = 3

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> DiscographyListFragment.newInstance("album")
            1 -> DiscographyListFragment.newInstance("single")
            2 -> DiscographyListFragment.newInstance("compilation")
            else -> throw IllegalArgumentException("Неверная позиция: $position")
        }
    }
}

class DiscographyFragment : Fragment() {

    private var artistId: String? = null

    private lateinit var tabLayout: TabLayout
    private lateinit var viewPager: ViewPager2
    private lateinit var viewModel: DiscographyViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        artistId = arguments?.getString("ARTIST_ID")
        // Создаем ViewModel для этого фрагмента
        viewModel = ViewModelProvider(this)[DiscographyViewModel::class.java]
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_discography, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        tabLayout = view.findViewById(R.id.tab_layout_discography)
        viewPager = view.findViewById(R.id.view_pager_discography)

        setupViewPager()

        // Делаем запрос ТОЛЬКО если список пуст (чтобы не грузить заново при перевороте экрана)
        if (viewModel.albums.value == null) {
            artistId?.let { loadDiscography(it) }
        }
    }

    private fun setupViewPager() {
        viewPager.adapter = DiscographyPagerAdapter(this)
        viewPager.offscreenPageLimit = 2 // Держим в памяти соседние вкладки для плавности

        val tabTitles = arrayOf("АЛЬБОМЫ", "СИНГЛЫ И EP", "СБОРНИКИ")

        TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            tab.text = tabTitles[position]
        }.attach()
    }

    private fun loadDiscography(id: String) {
        viewModel.setLoading(true)
        NetworkClient.getApi(requireContext()).getArtistDiscography(id)
            .enqueue(object : retrofit2.Callback<List<Album>> {
                override fun onResponse(call: retrofit2.Call<List<Album>>, response: retrofit2.Response<List<Album>>) {
                    if (isAdded && response.isSuccessful) {
                        val albums = response.body() ?: emptyList()
                        // 🔥 МАГИЯ ЗДЕСЬ: Кладем данные в ViewModel.
                        // Все вкладки автоматически получат уведомление и отфильтруют списки!
                        viewModel.setAlbums(albums)
                    } else {
                        viewModel.setLoading(false)
                    }
                }

                override fun onFailure(call: retrofit2.Call<List<Album>>, t: Throwable) {
                    if (isAdded) {
                        viewModel.setLoading(false)
                        Toast.makeText(context, "Ошибка сети", Toast.LENGTH_SHORT).show()
                    }
                }
            })
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Очищаем ссылки на View, чтобы избежать утечек памяти при переключении фрагментов
        viewPager.adapter = null
    }

    companion object {
        fun newInstance(artistId: String): DiscographyFragment {
            return DiscographyFragment().apply {
                arguments = Bundle().apply { putString("ARTIST_ID", artistId) }
            }
        }
    }
}