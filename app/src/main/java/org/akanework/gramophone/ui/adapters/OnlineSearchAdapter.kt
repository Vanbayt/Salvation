package org.akanework.gramophone.ui.adapters

import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil3.load
import org.akanework.gramophone.R
import org.akanework.gramophone.logic.api.Album
import org.akanework.gramophone.logic.api.Artist
import org.akanework.gramophone.logic.api.Track
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.GradientDrawable
import androidx.core.graphics.ColorUtils
import androidx.palette.graphics.Palette
import coil3.request.allowHardware


data class SearchHistoryItem(val query: String)
data class HeaderItem(val title: String)
data class TopResultItem(val track: Track? = null, val artist: Artist? = null)
data class ArtistCarouselItem(val artists: List<Artist>)
data class AlbumCarouselItem(val albums: List<Album>)

class OnlineSearchAdapter(
    private val isGridMode: Boolean = false,
    private val isCarouselMode: Boolean = false,
    private val onClick: (Track) -> Unit,
    private val onMenuClick: (Track, View) -> Unit = {_, _ ->},
    // 🔥 Добавили View в слушатели кликов
    private val onArtistClick: (Artist, View) -> Unit = { _, _ -> },
    private val onAlbumClick: (Album, View) -> Unit = { _, _ -> },
    private val onHistoryClick: (String) -> Unit = {}
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val items = mutableListOf<Any>()

    val currentList: List<Track> get() = items.filterIsInstance<Track>().ifEmpty {
        items.filterIsInstance<TopResultItem>().mapNotNull { it.track }
    }

    var currentlyPlayingTrackId: String? = null
        set(value) {
            val old = field
            field = value
            if (old != value) {
                // Ищем позицию старого и нового трека и обновляем ТОЛЬКО ИХ
                val oldIndex = items.indexOfFirst { (it is Track && it.id == old) || (it is TopResultItem && it.track?.id == old) }
                val newIndex = items.indexOfFirst { (it is Track && it.id == value) || (it is TopResultItem && it.track?.id == value) }
                if (oldIndex != -1) notifyItemChanged(oldIndex)
                if (newIndex != -1) notifyItemChanged(newIndex)
            }
        }

    companion object {
        private const val TYPE_ARTIST = 0
        private const val TYPE_TRACK = 1
        private const val TYPE_ALBUM = 2
        private const val TYPE_HISTORY = 3
        private const val TYPE_HEADER = 4
        private const val TYPE_ARTIST_CAROUSEL = 5
        private const val TYPE_ALBUM_CAROUSEL = 6
        private const val TYPE_TOP_RESULT = 7
    }

    fun submitList(newItems: List<Any>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int): Int {
        return when (items[position]) {
            is HeaderItem -> TYPE_HEADER
            is TopResultItem -> TYPE_TOP_RESULT
            is ArtistCarouselItem -> TYPE_ARTIST_CAROUSEL
            is AlbumCarouselItem -> TYPE_ALBUM_CAROUSEL
            is Artist -> TYPE_ARTIST
            is Album -> TYPE_ALBUM
            is SearchHistoryItem -> TYPE_HISTORY
            else -> TYPE_TRACK
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            TYPE_HEADER -> {
                val tv = TextView(parent.context).apply {
                    setPadding(
                        (16 * resources.displayMetrics.density).toInt(),
                        (16 * resources.displayMetrics.density).toInt(),
                        (16 * resources.displayMetrics.density).toInt(),
                        (8 * resources.displayMetrics.density).toInt()
                    )
                    textSize = 18f
                    typeface = android.graphics.Typeface.DEFAULT_BOLD
                    val typedValue = android.util.TypedValue()
                    context.theme.resolveAttribute(com.google.android.material.R.attr.colorOnBackground, typedValue, true)
                    setTextColor(typedValue.data)
                }
                HeaderViewHolder(tv)
            }
            TYPE_ARTIST_CAROUSEL, TYPE_ALBUM_CAROUSEL -> {
                val rv = RecyclerView(parent.context).apply {
                    layoutManager = LinearLayoutManager(parent.context, LinearLayoutManager.HORIZONTAL, false)
                    clipToPadding = false
                    val density = resources.displayMetrics.density
                    setPadding((12 * density).toInt(), 0, (12 * density).toInt(), 0)

                    // 🔥 УСТРАНЕНИЕ КОНФЛИКТА С СВАЙПОМ ViewPager2: Предотвращаем перехват касания родителем
                    addOnItemTouchListener(object : RecyclerView.OnItemTouchListener {
                        private var startX = 0f
                        private var startY = 0f

                        override fun onInterceptTouchEvent(rv: RecyclerView, e: MotionEvent): Boolean {
                            when (e.action) {
                                MotionEvent.ACTION_DOWN -> {
                                    startX = e.x
                                    startY = e.y
                                    rv.parent?.requestDisallowInterceptTouchEvent(true)
                                }
                                MotionEvent.ACTION_MOVE -> {
                                    val dx = Math.abs(e.x - startX)
                                    val dy = Math.abs(e.y - startY)
                                    if (dx > dy) {
                                        rv.parent?.requestDisallowInterceptTouchEvent(true)
                                    } else {
                                        rv.parent?.requestDisallowInterceptTouchEvent(false)
                                    }
                                }
                                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                                    rv.parent?.requestDisallowInterceptTouchEvent(false)
                                }
                            }
                            return false
                        }

                        override fun onTouchEvent(rv: RecyclerView, e: MotionEvent) {}
                        override fun onRequestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {}
                    })
                }
                CarouselViewHolder(rv)
            }
            TYPE_HISTORY -> {
                val view = LayoutInflater.from(parent.context).inflate(R.layout.item_search_history, parent, false)
                HistoryViewHolder(view)
            }
            TYPE_ARTIST -> {
                if (isGridMode || isCarouselMode) {
                    val view = LayoutInflater.from(parent.context).inflate(R.layout.item_grid_card, parent, false)
                    if (isCarouselMode) {
                        val lp = view.layoutParams
                        lp.width = (140 * view.context.resources.displayMetrics.density).toInt()
                        view.layoutParams = lp
                    }
                    GridArtistViewHolder(view)
                } else {
                    val view = LayoutInflater.from(parent.context).inflate(R.layout.item_search_artist, parent, false)
                    ArtistViewHolder(view)
                }
            }
            TYPE_ALBUM -> {
                if (isGridMode || isCarouselMode) {
                    val view = LayoutInflater.from(parent.context).inflate(R.layout.item_grid_card, parent, false)
                    if (isCarouselMode) {
                        val lp = view.layoutParams
                        lp.width = (140 * view.context.resources.displayMetrics.density).toInt()
                        view.layoutParams = lp
                    }
                    GridAlbumViewHolder(view)
                } else {
                    val view = LayoutInflater.from(parent.context).inflate(R.layout.item_album_card, parent, false)
                    AlbumViewHolder(view)
                }
            }
            TYPE_TOP_RESULT -> {
                val view = LayoutInflater.from(parent.context).inflate(R.layout.item_top_result, parent, false)
                TopResultViewHolder(view)
            }
            else -> {
                val view = LayoutInflater.from(parent.context).inflate(R.layout.item_search_result, parent, false)
                TrackViewHolder(view)
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = items[position]
        when {
            holder is HeaderViewHolder && item is HeaderItem -> holder.bind(item)
            holder is TopResultViewHolder && item is TopResultItem -> {
                if (item.track != null) holder.bindTrack(item.track)
                else if (item.artist != null) holder.bindArtist(item.artist)
            }
            holder is CarouselViewHolder && item is ArtistCarouselItem -> holder.bindArtists(item.artists)
            holder is CarouselViewHolder && item is AlbumCarouselItem -> holder.bindAlbums(item.albums)
            holder is HistoryViewHolder && item is SearchHistoryItem -> holder.bind(item)
            holder is ArtistViewHolder && item is Artist -> holder.bind(item)
            holder is GridArtistViewHolder && item is Artist -> holder.bind(item)
            holder is TrackViewHolder && item is Track -> holder.bind(item)
            holder is AlbumViewHolder && item is Album -> holder.bind(item)
            holder is GridAlbumViewHolder && item is Album -> holder.bind(item)
        }
    }

    override fun getItemCount() = items.size

    inner class TopResultViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val card: View = itemView.findViewById(R.id.card_top_result)
        private val cover: com.google.android.material.imageview.ShapeableImageView = itemView.findViewById(R.id.iv_cover)
        private val badge: TextView = itemView.findViewById(R.id.tv_badge)
        private val title: TextView = itemView.findViewById(R.id.tv_title)
        private val artist: TextView = itemView.findViewById(R.id.tv_artist)
        private val btnPlay: com.google.android.material.floatingactionbutton.FloatingActionButton = itemView.findViewById(R.id.btn_play)

        fun bindTrack(track: Track) {
            badge.text = "ЛУЧШИЙ РЕЗУЛЬТАТ"
            title.text = track.title
            artist.text = if (track.artist.isNotEmpty()) track.artist else "Unknown Artist"
            cover.shapeAppearanceModel = cover.shapeAppearanceModel.toBuilder()
                .setAllCornerSizes(12 * itemView.resources.displayMetrics.density)
                .build()
            cover.load(track.cover) {
                allowHardware(false)
            }
            btnPlay.setImageResource(R.drawable.ic_play_arrow)
            card.setOnClickListener { onClick(track) }
            btnPlay.setOnClickListener { onClick(track) }
        }

        fun bindArtist(a: Artist) {
            badge.text = "ИСПОЛНИТЕЛЬ"
            title.text = a.name
            artist.text = "Исполнитель"
            cover.shapeAppearanceModel = cover.shapeAppearanceModel.toBuilder()
                .setAllCornerSizes(36 * itemView.resources.displayMetrics.density)
                .build()
            cover.load(a.picture ?: a.cover) {
                allowHardware(false)
            }
            btnPlay.setImageResource(R.drawable.ic_person)
            card.setOnClickListener { onArtistClick(a, card) }
            btnPlay.setOnClickListener { onArtistClick(a, card) }
        }
    }

    inner class CarouselViewHolder(private val recyclerView: RecyclerView) : RecyclerView.ViewHolder(recyclerView) {
        fun bindArtists(artists: List<Artist>) {
            val adapter = OnlineSearchAdapter(
                isCarouselMode = true,
                onClick = {},
                onArtistClick = onArtistClick
            )
            recyclerView.adapter = adapter
            adapter.submitList(artists)
        }

        fun bindAlbums(albums: List<Album>) {
            val adapter = OnlineSearchAdapter(
                isCarouselMode = true,
                onClick = {},
                onAlbumClick = onAlbumClick
            )
            recyclerView.adapter = adapter
            adapter.submitList(albums)
        }
    }

    inner class HeaderViewHolder(private val textView: TextView) : RecyclerView.ViewHolder(textView) {
        fun bind(header: HeaderItem) {
            textView.text = header.title
        }
    }

    inner class HistoryViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val queryText: TextView = view.findViewById(R.id.tv_history_query)
        fun bind(item: SearchHistoryItem) {
            queryText.text = item.query
            itemView.setOnClickListener { onHistoryClick(item.query) }
        }
    }

    inner class ArtistViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val name: TextView = view.findViewById(R.id.tv_artist_name)
        private val cover: ImageView = view.findViewById(R.id.iv_artist_avatar)
        private fun fixCoverUrl(url: String?): String? {
            if (url.isNullOrEmpty()) return null
            return if (url.startsWith("/")) "http://185.196.41.31$url" else url
        }

        fun bind(artist: Artist) {
            // 🔥 Устанавливаем уникальное имя и передаем View
            itemView.transitionName = "artist_card_${artist.id}"
            name.text = artist.name
            val imgUrl = artist.picture ?: artist.cover
            fixCoverUrl(imgUrl)?.let { cover.load(it) } ?: cover.setImageResource(R.drawable.ic_library)
            itemView.setOnClickListener { onArtistClick(artist, itemView) }
        }
    }

    inner class TrackViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val title: TextView = view.findViewById(R.id.tv_title)
        private val artist: TextView = view.findViewById(R.id.tv_artist)
        private val cover: ImageView = view.findViewById(R.id.iv_cover)
        private val menuBtn: ImageButton = view.findViewById(R.id.btn_menu)

        // 🔥 ПЕРЕМЕННАЯ ДЛЯ АНИМАЦИИ ДЫХАНИЯ
        private var pulseAnimator: android.animation.ValueAnimator? = null

        fun bind(track: Track) {
            title.text = track.title
            artist.text = track.artist
            val finalCover = if (track.cover?.startsWith("/") == true) "http://185.196.41.31${track.cover}" else track.cover
            finalCover?.let { cover.load(it) } ?: cover.setImageResource(R.drawable.ic_library)
            itemView.setOnClickListener { onClick(track) }
            menuBtn.setOnClickListener { onMenuClick(track, menuBtn) }

            val isPlaying = track.id == currentlyPlayingTrackId

            val card = itemView as? com.google.android.material.card.MaterialCardView
            val context = itemView.context
            val density = context.resources.displayMetrics.density
            val typedValue = android.util.TypedValue()

            // 1. ОПРЕДЕЛЯЕМ ЦЕЛЕВЫЕ ЗНАЧЕНИЯ
            context.theme.resolveAttribute(
                if (isPlaying) com.google.android.material.R.attr.colorSecondaryContainer
                else com.google.android.material.R.attr.colorSurface,
                typedValue, true
            )
            val targetColor = typedValue.data

            val targetRadius = if (isPlaying) 36f * density else 12f * density

            context.theme.resolveAttribute(
                if (isPlaying) com.google.android.material.R.attr.colorOnSecondaryContainer
                else com.google.android.material.R.attr.colorOnSurface,
                typedValue, true
            )
            val targetTitleColor = typedValue.data

            context.theme.resolveAttribute(
                if (isPlaying) com.google.android.material.R.attr.colorOnSecondaryContainer
                else com.google.android.material.R.attr.colorOnSurfaceVariant,
                typedValue, true
            )
            val targetSubtitleColor = typedValue.data

            // 2. АНИМАЦИЯ ПЕРЕХОДА ЦВЕТА И ФОРМЫ
            val previousTrackId = card?.tag as? String
            val isSameTrack = previousTrackId == track.id
            card?.tag = track.id

            if (card != null) {
                if (isSameTrack && card.radius != targetRadius) {
                    val radiusAnimVal = android.animation.ValueAnimator.ofFloat(card.radius, targetRadius)
                    radiusAnimVal.addUpdateListener { card.radius = it.animatedValue as Float }

                    val titleAnimVal = android.animation.ValueAnimator.ofArgb(title.currentTextColor, targetTitleColor)
                    titleAnimVal.addUpdateListener { title.setTextColor(it.animatedValue as Int) }

                    val subtitleAnimVal = android.animation.ValueAnimator.ofArgb(artist.currentTextColor, targetSubtitleColor)
                    subtitleAnimVal.addUpdateListener {
                        artist.setTextColor(it.animatedValue as Int)
                        menuBtn.setColorFilter(it.animatedValue as Int)
                    }

                    val animatorSet = android.animation.AnimatorSet()
                    animatorSet.playTogether(radiusAnimVal, titleAnimVal, subtitleAnimVal)
                    animatorSet.duration = 250
                    animatorSet.start()

                    card.setCardBackgroundColor(targetColor)
                } else {
                    card.setCardBackgroundColor(targetColor)
                    card.radius = targetRadius
                    title.setTextColor(targetTitleColor)
                    artist.setTextColor(targetSubtitleColor)
                    menuBtn.setColorFilter(targetSubtitleColor)
                }
            }

            // 🔥 3. АНИМАЦИЯ ДЫХАНИЯ (ПУЛЬСАЦИИ)
            if (isPlaying) {
                if (pulseAnimator == null) {
                    // Создаем плавную пульсацию (увеличение на 2%)
                    pulseAnimator = android.animation.ValueAnimator.ofFloat(1.0f, 1.02f).apply {
                        duration = 1200 // 1.2 секунды на вдох/выдох
                        repeatCount = android.animation.ValueAnimator.INFINITE // Бесконечно
                        repeatMode = android.animation.ValueAnimator.REVERSE // Туда-сюда
                        interpolator = android.view.animation.AccelerateDecelerateInterpolator() // Мягкие края анимации
                        addUpdateListener { animator ->
                            val scale = animator.animatedValue as Float
                            itemView.scaleX = scale
                            itemView.scaleY = scale
                        }
                        start()
                    }
                } else if (pulseAnimator?.isRunning == false) {
                    pulseAnimator?.start()
                }
            } else {
                // Если трек не играет - убиваем анимацию и возвращаем масштаб 1.0
                clearAnimation()
            }
        }

        fun clearAnimation() {
            pulseAnimator?.cancel()
            pulseAnimator = null
            itemView.scaleX = 1.0f
            itemView.scaleY = 1.0f
        }
    }

    override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
        super.onViewRecycled(holder)
        if (holder is TrackViewHolder) {
            holder.clearAnimation()
        }
    }

    inner class AlbumViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val title: TextView = view.findViewById(R.id.tv_album_title)
        private val year: TextView = view.findViewById(R.id.tv_album_year)
        private val cover: ImageView = view.findViewById(R.id.iv_album_cover)
        fun bind(album: Album) {
            // 🔥 Устанавливаем уникальное имя и передаем View
            itemView.transitionName = "album_card_${album.id}"
            title.text = album.title
            year.text = album.releaseYear?.toString() ?: ""
            val finalCover = if (album.cover?.startsWith("/") == true) "http://185.196.41.31${album.cover}" else album.cover
            finalCover?.let { cover.load(it) } ?: cover.setImageResource(R.drawable.ic_library)
            itemView.setOnClickListener { onAlbumClick(album, itemView) }
        }
    }

    inner class GridAlbumViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val title: TextView = view.findViewById(R.id.tv_grid_title)
        private val subtitle: TextView = view.findViewById(R.id.tv_grid_subtitle)
        private val cover: ImageView = view.findViewById(R.id.iv_grid_cover)
        // 🔥 Находим слой градиента (убедись, что добавил android:id="@+id/v_scrim" в XML!)
        private val vScrim: View = view.findViewById(R.id.v_scrim)

        fun bind(album: Album) {
            itemView.transitionName = "album_card_${album.id}"
            title.text = album.title
            val yearStr = album.releaseYear?.toString() ?: ""
            subtitle.text = if (yearStr.isNotEmpty()) "${album.artistName} • $yearStr" else album.artistName

            val finalCoverUrl = if (album.cover?.startsWith("/") == true) "http://185.196.41.31${album.cover}" else album.cover
            finalCoverUrl?.let { url ->
                cover.load(url) {
                    allowHardware(false) // Разрешаем чтение пикселей
                    listener(
                        onSuccess = { _, result ->
                            // В Coil 3.x result.image содержит изображение.
                            // Мы пытаемся преобразовать его в Bitmap, если это возможно.
                            val bitmap = (result.image as? coil3.BitmapImage)?.bitmap ?: return@listener

                            Palette.from(bitmap).generate { palette ->
                                val fallbackColor = Color.parseColor("#FF000000")
                                val targetColor = palette?.getDarkMutedColor(fallbackColor)
                                    ?: palette?.getDominantColor(fallbackColor)
                                    ?: fallbackColor

                                val finalColor = ColorUtils.setAlphaComponent(targetColor, 220)
                                val dynamicGradient = GradientDrawable(
                                    GradientDrawable.Orientation.TOP_BOTTOM,
                                    intArrayOf(Color.TRANSPARENT, finalColor)
                                )
                                vScrim.background = dynamicGradient
                            }
                        }
                    )
                }
            } ?: run {
                // Если картинки нет, ставим заглушку и дефолтный черный градиент
                cover.setImageResource(R.drawable.ic_library)
                vScrim.setBackgroundResource(R.drawable.scrim_gradient_vertical)
            }

            itemView.setOnClickListener { onAlbumClick(album, itemView) }
        }
    }

    inner class GridArtistViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val title: TextView = view.findViewById(R.id.tv_grid_title)
        private val subtitle: TextView = view.findViewById(R.id.tv_grid_subtitle)
        private val cover: ImageView = view.findViewById(R.id.iv_grid_cover)
        // 🔥 Находим слой градиента
        private val vScrim: View = view.findViewById(R.id.v_scrim)

        fun bind(artist: Artist) {
            itemView.transitionName = "artist_card_${artist.id}"
            title.text = artist.name
            subtitle.text = "Артист"

            val imgUrl = artist.picture ?: artist.cover
            imgUrl?.let { url ->
                cover.load(url) {
                    allowHardware(false)
                    listener(
                        onSuccess = { _, result ->
                            val bitmap = (result.image as? coil3.BitmapImage)?.bitmap ?: return@listener

                            Palette.from(bitmap).generate { palette ->
                                val fallbackColor = Color.parseColor("#FF000000")
                                val targetColor = palette?.getDarkMutedColor(fallbackColor)
                                    ?: palette?.getDominantColor(fallbackColor)
                                    ?: fallbackColor

                                val finalColor = ColorUtils.setAlphaComponent(targetColor, 220)
                                val dynamicGradient = GradientDrawable(
                                    GradientDrawable.Orientation.TOP_BOTTOM,
                                    intArrayOf(Color.TRANSPARENT, finalColor)
                                )
                                vScrim.background = dynamicGradient
                            }
                        }
                    )
                }
            } ?: run {
                cover.setImageResource(R.drawable.ic_library)
                vScrim.setBackgroundResource(R.drawable.scrim_gradient_vertical)
            }

            itemView.setOnClickListener { onArtistClick(artist, itemView) }
        }
    }
}