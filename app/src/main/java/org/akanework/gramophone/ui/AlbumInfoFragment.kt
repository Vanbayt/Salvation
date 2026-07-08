package org.akanework.gramophone.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import org.akanework.gramophone.logic.AlbumViewModel

class AlbumInfoFragment : Fragment() {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val textView = TextView(requireContext()).apply {
            setPadding(40, 40, 40, 40)
            textSize = 16f
        }

        val viewModel = ViewModelProvider(requireParentFragment())[AlbumViewModel::class.java]
        viewModel.album.observe(viewLifecycleOwner) { album ->
            val trackCount = album.tracks?.size ?: 0
            textView.text = "Тип релиза: ${album.recordType}\nКоличество треков: $trackCount\nID: ${album.id}"
        }

        return textView
    }
}