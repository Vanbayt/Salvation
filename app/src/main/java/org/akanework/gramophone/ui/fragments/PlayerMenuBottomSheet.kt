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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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

            // 3. БЕСШОВНАЯ НАВИГАЦИЯ (Ищем ID в кармане плеера)
            val extras = metadata.extras
            val artistId = extras?.getString("ARTIST_ID")
            val albumId = extras?.getString("ALBUM_ID")

            Log.d("SALVATION_DEBUG", "Открыли меню для [${metadata.title}], ArtistID из Extras: $artistId")

            view.findViewById<View>(R.id.menu_action_artist).setOnClickListener {
                if (artistId != null) {
                    // 1. Сворачиваем текущее маленькое меню
                    dismiss()

                    // 2. Находим большой плеер в MainActivity по твоему тегу и закрываем его
                    val fullPlayer = activity.supportFragmentManager.findFragmentByTag("FULL_PLAYER") as? BottomSheetDialogFragment
                    fullPlayer?.dismiss()

                    // 3. Открываем страницу артиста
                    activity.startFragment(ArtistFragment.newInstance(artistId))
                } else {
                    Toast.makeText(context, "ID артиста недоступен", Toast.LENGTH_SHORT).show()
                }
            }

            view.findViewById<View>(R.id.menu_action_album).setOnClickListener {
                if (albumId != null) {
                    // 1. Сворачиваем текущее маленькое меню
                    dismiss()

                    // 2. Находим большой плеер в MainActivity по твоему тегу и закрываем его
                    val fullPlayer = activity.supportFragmentManager.findFragmentByTag("FULL_PLAYER") as? BottomSheetDialogFragment
                    fullPlayer?.dismiss()

                    // 3. Открываем страницу альбома
                    activity.startFragment(AlbumFragment.newInstance(albumId))
                } else {
                    Toast.makeText(context, "ID альбома недоступен", Toast.LENGTH_SHORT).show()
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

            // 5. ТАЙМЕР СНА
            view.findViewById<View>(R.id.menu_action_sleep).setOnClickListener {
                val options = arrayOf("15 минут", "30 минут", "45 минут", "60 минут", "Отключить")
                val times = arrayOf(15, 30, 45, 60, 0)

                MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Таймер сна")
                    .setItems(options) { _, which ->
                        val minutes = times[which]
                        if (minutes > 0) {
                            activity.lifecycleScope.launch {
                                Toast.makeText(context, "Музыка остановится через $minutes мин.", Toast.LENGTH_SHORT).show()
                                delay(minutes * 60 * 1000L)
                                controller.pause()
                            }
                        } else {
                            Toast.makeText(context, "Таймер отключен", Toast.LENGTH_SHORT).show()
                        }
                    }
                    .show()
                dismiss()
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
}
