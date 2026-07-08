package org.akanework.gramophone.ui.fragments

import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter

class AlbumPagerAdapter(fragment: Fragment) : FragmentStateAdapter(fragment) {
    override fun getItemCount(): Int = 2

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> AlbumTracksFragment()
            1 -> AlbumInfoFragment()
            else -> throw IllegalArgumentException("Invalid position")
        }
    }
}