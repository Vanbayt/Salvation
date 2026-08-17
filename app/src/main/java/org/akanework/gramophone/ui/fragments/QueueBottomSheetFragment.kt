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
import androidx.core.view.HapticFeedbackConstantsCompat
import androidx.core.view.ViewCompat
import org.akanework.gramophone.R
import org.akanework.gramophone.logic.dpToPx
import org.akanework.gramophone.ui.MainActivity

class QueueBottomSheetFragment : BottomSheetDialogFragment() {

    companion object {
        private const val TYPE_HEADER = 0
        private const val TYPE_ITEM = 1
    }

    sealed class QueueRow {
        data class Header(val title: String) : QueueRow()
        data class Item(
            val mediaItem: MediaItem,
            var originalIndex: Int,
            val isCurrent: Boolean
        ) : QueueRow()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.layout_bottom_sheet_queue, container, false)
    }

    override fun onStart() {
        super.onStart()
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
        view.findViewById<View>(R.id.btn_close_queue).setOnClickListener { dismiss() }

        val activity = requireActivity() as MainActivity
        activity.controllerViewModel.addControllerCallback(viewLifecycleOwner.lifecycle) { controller, _ ->

            val currentItem = controller.currentMediaItem
            val playingFrom = currentItem?.mediaMetadata?.extras?.getString("PLAYING_FROM") ?: "Медиатека"
            view.findViewById<TextView>(R.id.tv_playing_from)?.text = "Играет из: $playingFrom"

            val currentIndex = controller.currentMediaItemIndex
            val rows = mutableListOf<QueueRow>()

            if (currentIndex >= 0 && currentIndex < controller.mediaItemCount) {
                // 1. Сейчас играет
                rows.add(QueueRow.Header("Сейчас играет"))
                rows.add(QueueRow.Item(controller.getMediaItemAt(currentIndex), currentIndex, isCurrent = true))

                // 2. Далее в очереди
                if (currentIndex + 1 < controller.mediaItemCount) {
                    rows.add(QueueRow.Header("Далее в очереди"))
                    for (i in (currentIndex + 1) until controller.mediaItemCount) {
                        rows.add(QueueRow.Item(controller.getMediaItemAt(i), i, isCurrent = false))
                    }
                }

                // 3. Ранее прослушано
                if (currentIndex > 0) {
                    rows.add(QueueRow.Header("Ранее прослушано"))
                    for (i in 0 until currentIndex) {
                        rows.add(QueueRow.Item(controller.getMediaItemAt(i), i, isCurrent = false))
                    }
                }
            } else {
                rows.add(QueueRow.Header("В очереди"))
                for (i in 0 until controller.mediaItemCount) {
                    rows.add(QueueRow.Item(controller.getMediaItemAt(i), i, isCurrent = (i == 0)))
                }
            }

            val recycler = view.findViewById<RecyclerView>(R.id.rv_full_queue)
            recycler.layoutManager = LinearLayoutManager(requireContext())

            var itemTouchHelper: ItemTouchHelper? = null

            val adapter = FullQueueAdapter(
                rows = rows,
                onItemClick = { originalIndex ->
                    controller.seekToDefaultPosition(originalIndex)
                    controller.play()
                    dismiss()
                },
                onDragStart = { viewHolder ->
                    itemTouchHelper?.startDrag(viewHolder)
                }
            )
            recycler.adapter = adapter

            val swipeAndDragHelper = object : ItemTouchHelper.SimpleCallback(
                ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0
            ) {
                override fun getMovementFlags(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder): Int {
                    if (viewHolder is FullQueueAdapter.HeaderViewHolder) {
                        return makeMovementFlags(0, 0)
                    }
                    return super.getMovementFlags(recyclerView, viewHolder)
                }

                override fun onMove(
                    recyclerView: RecyclerView,
                    viewHolder: RecyclerView.ViewHolder,
                    target: RecyclerView.ViewHolder
                ): Boolean {
                    if (target is FullQueueAdapter.HeaderViewHolder) return false

                    val fromPos = viewHolder.bindingAdapterPosition
                    val toPos = target.bindingAdapterPosition

                    val fromRow = rows.getOrNull(fromPos) as? QueueRow.Item ?: return false
                    val toRow = rows.getOrNull(toPos) as? QueueRow.Item ?: return false

                    val oldFromIndex = fromRow.originalIndex
                    val oldToIndex = toRow.originalIndex

                    fromRow.originalIndex = oldToIndex
                    toRow.originalIndex = oldFromIndex

                    rows[fromPos] = toRow
                    rows[toPos] = fromRow
                    adapter.notifyItemMoved(fromPos, toPos)

                    controller.moveMediaItem(oldFromIndex, oldToIndex)
                    return true
                }

                override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
                    super.onSelectedChanged(viewHolder, actionState)
                    if (actionState == ItemTouchHelper.ACTION_STATE_DRAG && viewHolder != null) {
                        ViewCompat.performHapticFeedback(viewHolder.itemView, HapticFeedbackConstantsCompat.LONG_PRESS)
                        viewHolder.itemView.animate()
                            .scaleX(1.03f)
                            .scaleY(1.03f)
                            .translationZ(12.dpToPx(viewHolder.itemView.context).toFloat())
                            .setDuration(150)
                            .start()
                    }
                }

                override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
                    super.clearView(recyclerView, viewHolder)
                    viewHolder.itemView.animate()
                        .scaleX(1.0f)
                        .scaleY(1.0f)
                        .translationZ(0f)
                        .setDuration(150)
                        .start()
                }

                override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {}
                override fun isLongPressDragEnabled(): Boolean = true
            }

            itemTouchHelper = ItemTouchHelper(swipeAndDragHelper)
            itemTouchHelper.attachToRecyclerView(recycler)

            recycler.scrollToPosition(0)
        }
    }

    private inner class FullQueueAdapter(
        private val rows: MutableList<QueueRow>,
        private val onItemClick: (Int) -> Unit,
        private val onDragStart: (RecyclerView.ViewHolder) -> Unit
    ) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

        inner class HeaderViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val title: TextView = view as TextView
        }

        inner class ItemViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val title: TextView = view.findViewById(R.id.tv_track_title)
            val artist: TextView = view.findViewById(R.id.tv_track_artist)
            val cover: ImageView = view.findViewById(R.id.iv_track_cover)
            val dragHandle: ImageView = view.findViewById(R.id.iv_drag_handle)
            val defaultTitleColors = title.textColors

            init {
                view.setOnClickListener {
                    val pos = bindingAdapterPosition
                    val item = rows.getOrNull(pos) as? QueueRow.Item
                    if (item != null) onItemClick(item.originalIndex)
                }
                view.setOnLongClickListener {
                    val item = rows.getOrNull(bindingAdapterPosition) as? QueueRow.Item
                    if (item != null) onDragStart(this)
                    true
                }
            }
        }

        override fun getItemViewType(position: Int): Int {
            return when (rows[position]) {
                is QueueRow.Header -> TYPE_HEADER
                is QueueRow.Item -> TYPE_ITEM
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            return if (viewType == TYPE_HEADER) {
                val tv = TextView(parent.context).apply {
                    layoutParams = ViewGroup.MarginLayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    ).apply {
                        setMargins(
                            16.dpToPx(context),
                            16.dpToPx(context),
                            16.dpToPx(context),
                            8.dpToPx(context)
                        )
                    }
                    setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleMedium)
                    setTextColor(context.getColor(R.color.sl_fav_button))
                    typeface = android.graphics.Typeface.DEFAULT_BOLD
                }
                HeaderViewHolder(tv)
            } else {
                ItemViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_queue_track, parent, false))
            }
        }

        @SuppressLint("ClickableViewAccessibility")
        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            when (val row = rows[position]) {
                is QueueRow.Header -> {
                    (holder as HeaderViewHolder).title.text = row.title
                }
                is QueueRow.Item -> {
                    val itemHolder = holder as ItemViewHolder
                    val metadata = row.mediaItem.mediaMetadata
                    itemHolder.title.text = metadata.title ?: "Неизвестно"
                    itemHolder.artist.text = metadata.artist ?: "Неизвестно"

                    if (row.isCurrent) {
                        itemHolder.title.setTextColor(requireContext().getColor(R.color.sl_fav_button))
                    } else {
                        itemHolder.title.setTextColor(itemHolder.defaultTitleColors)
                    }

                    val originalUri = metadata.artworkUri?.toString() ?: ""
                    val coverUrl = if (originalUri.startsWith("/")) "http://185.196.41.31$originalUri" else originalUri

                    itemHolder.cover.setImageResource(R.drawable.ic_library)
                    if (coverUrl.isNotEmpty()) itemHolder.cover.load(coverUrl)

                    itemHolder.dragHandle.setOnTouchListener { _, event ->
                        if (event.action == MotionEvent.ACTION_DOWN) {
                            onDragStart(itemHolder)
                        }
                        false
                    }
                }
            }
        }

        override fun getItemCount() = rows.size
    }
}