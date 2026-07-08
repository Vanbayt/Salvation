package org.akanework.gramophone.ui.fragments

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.media3.common.MediaItem
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil3.load
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import org.akanework.gramophone.R
import org.akanework.gramophone.ui.MainActivity
import java.util.Collections

class QueueBottomSheetFragment : BottomSheetDialogFragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        // Используем твой новый layout_bottom_sheet_queue
        return inflater.inflate(R.layout.layout_bottom_sheet_queue, container, false)
    }

    override fun onStart() {
        super.onStart()
        // Растягиваем очередь на весь экран (как в Spotify)
        val dialog = dialog as? BottomSheetDialog
        val bottomSheet = dialog?.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
        bottomSheet?.let {
            val behavior = BottomSheetBehavior.from(it)
            behavior.state = BottomSheetBehavior.STATE_EXPANDED
            behavior.skipCollapsed = true
            it.layoutParams.height = ViewGroup.LayoutParams.MATCH_PARENT
            it.requestLayout()
            it.setBackgroundResource(android.R.color.transparent)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
val bottomSheet = dialog?.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
        view.findViewById<View>(R.id.btn_close_queue).setOnClickListener { dismiss() }

        val activity = requireActivity() as MainActivity
        activity.controllerViewModel.addControllerCallback(viewLifecycleOwner.lifecycle) { controller, _ ->

            // 1. Читаем ИСТОЧНИК из extras текущего играющего трека
            val currentItem = controller.currentMediaItem
            val playingFrom = currentItem?.mediaMetadata?.extras?.getString("PLAYING_FROM") ?: "Медиатека"

            // Записываем в TextView
            view.findViewById<TextView>(R.id.tv_playing_from)?.text = "Играет из: $playingFrom"

            val queue = mutableListOf<MediaItem>()
            for (i in 0 until controller.mediaItemCount) {
                queue.add(controller.getMediaItemAt(i))
            }

            val recycler = view.findViewById<RecyclerView>(R.id.rv_full_queue)
            recycler.layoutManager = LinearLayoutManager(requireContext())

            // Создаем ItemTouchHelper ДО адаптера, чтобы передать его внутрь
            var itemTouchHelper: ItemTouchHelper? = null

            val adapter = FullQueueAdapter(
                items = queue,
                currentIndex = controller.currentMediaItemIndex,
                onItemClick = { clickedIndex ->
                    // При клике на трек в очереди - прыгаем на него
                    controller.seekToDefaultPosition(clickedIndex)
                    controller.play()
                    dismiss() // Закрываем очередь после выбора
                },
                onDragStart = { viewHolder ->
                    // Нажали на 3 полоски - начинаем перетаскивание
                    itemTouchHelper?.startDrag(viewHolder)
                }
            )
            recycler.adapter = adapter

            // 2. Логика Перетаскивания (Drag & Drop)
            val swipeAndDragHelper = object : ItemTouchHelper.SimpleCallback(
                ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0 // Разрешаем тянуть вверх и вниз
            ) {
                override fun onMove(
                    recyclerView: RecyclerView,
                    viewHolder: RecyclerView.ViewHolder,
                    target: RecyclerView.ViewHolder
                ): Boolean {
                    val fromPosition = viewHolder.bindingAdapterPosition
                    val toPosition = target.bindingAdapterPosition

                    // Меняем элементы в нашем локальном списке
                    Collections.swap(queue, fromPosition, toPosition)
                    adapter.notifyItemMoved(fromPosition, toPosition)

                    // Говорим плееру поменять порядок на лету!
                    controller.moveMediaItem(fromPosition, toPosition)

                    // Обновляем currentIndex если перетащили текущий трек
                    if (fromPosition == adapter.currentIndex) {
                        adapter.currentIndex = toPosition
                    } else if (toPosition == adapter.currentIndex) {
                        adapter.currentIndex = fromPosition
                    }
                    return true
                }

                override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                    // Свайп влево/вправо пока отключен
                }

                // Отключаем автоматический Drag по долгому нажатию на весь элемент
                override fun isLongPressDragEnabled(): Boolean = false
            }

            itemTouchHelper = ItemTouchHelper(swipeAndDragHelper)
            itemTouchHelper.attachToRecyclerView(recycler)

            // Скроллим к текущему треку
            if (controller.currentMediaItemIndex >= 0) {
                recycler.scrollToPosition(controller.currentMediaItemIndex)
            }
        }
    }

    private inner class FullQueueAdapter(
        private val items: MutableList<MediaItem>,
        var currentIndex: Int,
        private val onItemClick: (Int) -> Unit,
        private val onDragStart: (ViewHolder) -> Unit
    ) : RecyclerView.Adapter<FullQueueAdapter.ViewHolder>() {

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val title: TextView = view.findViewById(R.id.tv_track_title)
            val artist: TextView = view.findViewById(R.id.tv_track_artist)
            val cover: ImageView = view.findViewById(R.id.iv_track_cover)
            val dragHandle: ImageView = view.findViewById(R.id.iv_drag_handle)

            // 🔥 ХАК: Запоминаем родной цвет текста из XML (он уже правильный для любой темы)
            val defaultTitleColors = title.textColors

            init {
                view.setOnClickListener { onItemClick(bindingAdapterPosition) }
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            return ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_queue_track, parent, false))
        }

        @SuppressLint("ClickableViewAccessibility")
        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val metadata = items[position].mediaMetadata
            holder.title.text = metadata.title ?: "Неизвестно"
            holder.artist.text = metadata.artist ?: "Неизвестно"

            // 🎨 Раскрашиваем заголовок
            if (position == currentIndex) {
                // Текущий играющий трек (твой рабочий цвет)
                holder.title.setTextColor(requireContext().getColor(R.color.sl_fav_button))
            } else {
                // Все остальные треки (возвращаем исходный цвет темы)
                holder.title.setTextColor(holder.defaultTitleColors)
            }

            val originalUri = metadata.artworkUri?.toString() ?: ""
            val coverUrl = if (originalUri.startsWith("/")) "http://185.196.41.31$originalUri" else originalUri

            holder.cover.setImageResource(R.drawable.ic_library)
            if (coverUrl.isNotEmpty()) holder.cover.load(coverUrl)

            // Слушаем прикосновения именно к иконке "три полоски" для Drag&Drop
            holder.dragHandle.setOnTouchListener { _, event ->
                if (event.action == MotionEvent.ACTION_DOWN) {
                    onDragStart(holder)
                }
                false
            }
        }

        override fun getItemCount() = items.size
    }
}