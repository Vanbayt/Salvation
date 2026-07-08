package org.akanework.gramophone.ui.fragments

import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter

class LibraryPagerAdapter(fragment: Fragment) : FragmentStateAdapter(fragment) {

    override fun getItemCount(): Int = 4

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> LibrarySongsFragment()
            1 -> LibraryAlbumsFragment()
            2 -> LibraryPlaylistsFragment()
            3 -> LibraryArtistsFragment()
            else -> throw IllegalArgumentException("Invalid position $position")
        }
    }
}