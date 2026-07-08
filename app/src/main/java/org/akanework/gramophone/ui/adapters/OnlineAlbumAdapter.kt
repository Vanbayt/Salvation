package org.akanework.gramophone.ui.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import coil3.load
import com.google.android.material.imageview.ShapeableImageView
import org.akanework.gramophone.R
import org.akanework.gramophone.logic.api.Album

class OnlineAlbumAdapter(
    private val albums: List<Album>,
    private val onAlbumClick: (Album) -> Unit
) : RecyclerView.Adapter<OnlineAlbumAdapter.AlbumViewHolder>() {

    inner class AlbumViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val ivCover: ShapeableImageView = view.findViewById(R.id.iv_album_cover)
        val tvTitle: TextView = view.findViewById(R.id.tv_album_title)
        val tvYear: TextView = view.findViewById(R.id.tv_album_year)

        init {
            view.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onAlbumClick(albums[position])
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AlbumViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_album_card, parent, false)
        return AlbumViewHolder(view)
    }

    override fun onBindViewHolder(holder: AlbumViewHolder, position: Int) {
        val album = albums[position]

        holder.tvTitle.text = album.title
        holder.tvYear.text = album.releaseYear?.toString() ?: "Неизвестный год"

        if (!album.cover.isNullOrEmpty()) {
            holder.ivCover.load(album.cover)
        } else {
            holder.ivCover.setImageResource(R.drawable.ic_library)
        }
    }

    override fun getItemCount(): Int = albums.size
}