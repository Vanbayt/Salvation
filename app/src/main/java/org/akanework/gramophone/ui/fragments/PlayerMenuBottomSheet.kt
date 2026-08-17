package org.akanework.gramophone.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil3.load
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.imageview.ShapeableImageView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.akanework.gramophone.R
import org.akanework.gramophone.ui.MainActivity
import android.util.Log

class PlayerMenuBottomSheet : BottomSheetDialogFragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.layout_bottom_sheet_player_menu, container, false)
    }

    // Убираем белый системный фон со скруглениями
    override fun onStart() {
        super.onStart()
        val dialog = dialog as? com.google.android.material.bottomsheet.BottomSheetDialog
        val bottomSheet = dialog?.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
        bottomSheet?.setBackgroundResource(android.R.color.transparent)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val activity = requireActivity() as MainActivity

        activity.controllerViewModel.addControllerCallback(viewLifecycleOwner.lifecycle) { controller, _ ->
            val currentItem = controller.currentMediaItem
            val metadata = currentItem?.mediaMetadata ?: controller.mediaMetadata

            // 1. Заполняем шапку
            view.findViewById<TextView>(R.id.menu_track_title).text = metadata.title ?: "Неизвестный трек"
            view.findViewById<TextView>(R.id.menu_track_artist).text = metadata.artist ?: "Неизвестный артист"

            val coverView = view.findViewById<ShapeableImageView>(R.id.menu_track_cover)
            val originalUri = metadata.artworkUri?.toString() ?: ""
            val finalCoverUrl = if (originalUri.startsWith("/")) "http://185.196.41.31$originalUri" else originalUri

            coverView.setImageResource(R.drawable.ic_library)
            if (finalCoverUrl.isNotEmpty()) {
                coverView.load(finalCoverUrl)
            }

            // 2. Мини-очередь (следующие 2-3 трека)
            val miniQueue = mutableListOf<MediaItem>()
            val currentIndex = controller.currentMediaItemIndex
            for (i in (currentIndex + 1) until controller.mediaItemCount) {
                miniQueue.add(controller.getMediaItemAt(i))
                if (miniQueue.size >= 3) break
            }

            val recycler = view.findViewById<RecyclerView>(R.id.menu_queue_recycler)
            recycler.layoutManager = LinearLayoutManager(requireContext())
            recycler.adapter = MiniQueueAdapter(miniQueue)

            if (miniQueue.isEmpty()) {
                view.findViewById<View>(R.id.menu_queue_title)?.visibility = View.GONE
                view.findViewById<View>(R.id.menu_queue_card)?.visibility = View.GONE
            } else {
                view.findViewById<View>(R.id.menu_action_full_queue)?.setOnClickListener {
                    dismiss()
                    QueueBottomSheetFragment().show(parentFragmentManager, "QUEUE_SHEET")
                }
            }

            // 3. БЕСШОВНАЯ НАВИГАЦИЯ (Ищем ID в кармане плеера или резолвим по имени)
            val extras = metadata.extras
            val artistId = extras?.getString("ARTIST_ID")
            val albumId = extras?.getString("ALBUM_ID")
            val artistName = metadata.artist?.toString()?.trim() ?: ""
            val trackTitle = metadata.title?.toString()?.trim() ?: ""
            val albumTitle = metadata.albumTitle?.toString()?.trim() ?: ""

            Log.d("SALVATION_DEBUG", "Открыли меню для [${metadata.title}], ArtistID: $artistId, AlbumID: $albumId, Artist: $artistName, Album: $albumTitle")

            val btnArtist = view.findViewById<View>(R.id.menu_action_artist)
            val btnAlbum = view.findViewById<View>(R.id.menu_action_album)

            btnArtist.setOnClickListener {
                val primaryArtist = if (artistName.isNotEmpty()) {
                    artistName.split(",", ";", " feat. ", " ft. ", " Feat. ", " Ft. ", " & ")[0].trim()
                } else ""

                // 1. Проверяем локальный кэш
                val cachedId = artistIdCache[primaryArtist.lowercase()]
                if (!cachedId.isNullOrEmpty()) {
                    dismiss()
                    activity.collapsePlayer()
                    activity.startFragment(ArtistFragment.newInstance(cachedId))
                    return@setOnClickListener
                }

                // 2. Если в extras есть валидный artistId (только цифры)
                if (!artistId.isNullOrEmpty() && artistId.all { it.isDigit() }) {
                    dismiss()
                    activity.collapsePlayer()
                    activity.startFragment(ArtistFragment.newInstance(artistId))
                    return@setOnClickListener
                }

                // 3. Мгновенный поиск через ультра-быстрый API (/api/v1/search/artist)
                if (primaryArtist.isNotEmpty() && primaryArtist != "Неизвестный артист" && primaryArtist != "Unknown" && primaryArtist != "Неизвестно") {
                    btnArtist.isEnabled = false

                    viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                        try {
                            val api = org.akanework.gramophone.logic.api.NetworkClient.getApi(requireContext())
                            val resp = api.searchArtistFast(primaryArtist).execute()
                            val artistLookup = resp.body()

                            withContext(Dispatchers.Main) {
                                btnArtist.isEnabled = true
                                if (artistLookup != null && artistLookup.id.isNotEmpty()) {
                                    artistIdCache[primaryArtist.lowercase()] = artistLookup.id
                                    dismiss()
                                    activity.collapsePlayer()
                                    activity.startFragment(ArtistFragment.newInstance(artistLookup.id))
                                } else {
                                    Toast.makeText(context, "Артист '$primaryArtist' не найден", Toast.LENGTH_SHORT).show()
                                }
                            }
                        } catch (e: Exception) {
                            withContext(Dispatchers.Main) {
                                btnArtist.isEnabled = true
                                Toast.makeText(context, "Ошибка поиска: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                } else {
                    Toast.makeText(context, "Исполнитель неизвестен", Toast.LENGTH_SHORT).show()
                }
            }

            btnAlbum.setOnClickListener {
                val currentTrackId = controller.currentMediaItem?.mediaId

                // 1. Если в extras есть валидный albumId (только цифры)
                if (!albumId.isNullOrEmpty() && albumId.all { it.isDigit() }) {
                    dismiss()
                    activity.collapsePlayer()
                    activity.startFragment(AlbumFragment.newInstance(albumId))
                    return@setOnClickListener
                }

                // 2. Резолвим через API по track_id или тексту
                val query = if (albumTitle.isNotEmpty() && albumTitle != "Single" && albumTitle != "Unknown" && albumTitle != "Неизвестно") {
                    "$artistName $albumTitle"
                } else {
                    "$artistName $trackTitle"
                }

                val cacheKey = currentTrackId ?: query.lowercase()
                val cachedAlbumId = albumIdCache[cacheKey]
                if (!cachedAlbumId.isNullOrEmpty()) {
                    dismiss()
                    activity.collapsePlayer()
                    activity.startFragment(AlbumFragment.newInstance(cachedAlbumId))
                    return@setOnClickListener
                }

                if (artistName.isNotEmpty() || !currentTrackId.isNullOrEmpty()) {
                    btnAlbum.isEnabled = false

                    viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                        try {
                            val api = org.akanework.gramophone.logic.api.NetworkClient.getApi(requireContext())
                            val resp = api.searchAlbumFast(query = query.ifEmpty { null }, trackId = currentTrackId).execute()
                            val albumLookup = resp.body()

                            withContext(Dispatchers.Main) {
                                btnAlbum.isEnabled = true
                                if (albumLookup != null && albumLookup.id.isNotEmpty()) {
                                    albumIdCache[cacheKey] = albumLookup.id
                                    dismiss()
                                    activity.collapsePlayer()
                                    activity.startFragment(AlbumFragment.newInstance(albumLookup.id))
                                } else {
                                    Toast.makeText(context, "Альбом не найден", Toast.LENGTH_SHORT).show()
                                }
                            }
                        } catch (e: Exception) {
                            withContext(Dispatchers.Main) {
                                btnAlbum.isEnabled = true
                                Toast.makeText(context, "Ошибка поиска: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                } else {
                    Toast.makeText(context, "Альбом неизвестен", Toast.LENGTH_SHORT).show()
                }
            }

            // 4. ДОБАВИТЬ В ПЛЕЙЛИСТ
            view.findViewById<View>(R.id.menu_action_playlist).setOnClickListener {
                // Извлекаем ID трека из текущего MediaItem. В ExoPlayer он хранится в mediaId как строка.
                val currentTrackIdString = controller.currentMediaItem?.mediaId
                val currentTrackId = currentTrackIdString?.toIntOrNull()

                if (currentTrackId != null) {
                    // Закрываем текущее меню
                    dismiss()

                    // Открываем нашу новую Compose шторку и передаем ей ID трека
                    val addToPlaylistSheet = AddToPlaylistBottomSheet.newInstance(currentTrackId)
                    addToPlaylistSheet.show(activity.supportFragmentManager, "ADD_TO_PLAYLIST_SHEET")
                } else {
                    Toast.makeText(context, "Не удалось определить ID трека", Toast.LENGTH_SHORT).show()
                }
            }

            // 5. ТАЙМЕР СНА (Material 3 Expressive)
            view.findViewById<View>(R.id.menu_action_sleep).setOnClickListener {
                dismiss()
                val sleepTimerSheet = SleepTimerBottomSheet.newInstance()
                sleepTimerSheet.show(activity.supportFragmentManager, "SLEEP_TIMER_SHEET")
            }

            // 6. ЖАЛОБА НА ТРЕК
            view.findViewById<View>(R.id.menu_action_report).setOnClickListener {
                Toast.makeText(context, "Жалоба отправлена", Toast.LENGTH_SHORT).show()
                // TODO: отправка запроса на сервер
            }
        }
    }

    // --- АДАПТЕР МИНИ-ОЧЕРЕДИ ---
    private inner class MiniQueueAdapter(private val items: List<MediaItem>) :
        RecyclerView.Adapter<MiniQueueAdapter.ViewHolder>() {

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val title: TextView = view.findViewById(R.id.tv_title)
            val artist: TextView = view.findViewById(R.id.tv_artist)
            val cover: ImageView = view.findViewById(R.id.iv_cover)
            val menuBtn: View = view.findViewById(R.id.btn_menu)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_search_result, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val metadata = items[position].mediaMetadata
            holder.title.text = metadata.title ?: "Неизвестно"
            holder.artist.text = metadata.artist ?: "Неизвестно"

            // Прячем кнопку меню (три точки) у элементов в мини-очереди
            holder.menuBtn.visibility = View.GONE

            val originalUri = metadata.artworkUri?.toString() ?: ""
            val coverUrl = if (originalUri.startsWith("/")) "http://185.196.41.31$originalUri" else originalUri

            holder.cover.setImageResource(R.drawable.ic_library)
            if (coverUrl.isNotEmpty()) {
                holder.cover.load(coverUrl)
            }
        }

        override fun getItemCount() = items.size
    }

    companion object {
        private val artistIdCache = java.util.concurrent.ConcurrentHashMap<String, String>()
        private val albumIdCache = java.util.concurrent.ConcurrentHashMap<String, String>()
    }
}
