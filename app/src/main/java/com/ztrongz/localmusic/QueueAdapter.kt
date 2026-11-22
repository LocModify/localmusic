package com.ztrongz.localmusic

import android.annotation.SuppressLint
import android.content.res.ColorStateList
import android.graphics.BitmapFactory
import android.graphics.Color
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.media3.common.MediaItem
import androidx.recyclerview.widget.RecyclerView
import java.util.Collections
import java.util.concurrent.TimeUnit

/**
 * Adapter hiển thị TOÀN BỘ hàng đợi (vị trí trong adapter == vị trí trong Player).
 * - Bấm: phát bài đó (hoặc tick chọn khi đang ở chế độ chọn nhiều)
 * - Giữ lâu: vào chế độ chọn nhiều
 * - Chạm vào số thứ tự rồi kéo: đổi thứ tự bài
 */
class QueueAdapter(
    private val onItemClick: (Int) -> Unit,
    private val onStartDrag: (RecyclerView.ViewHolder) -> Unit,
    private val onMoreClick: (Int, View) -> Unit,
    private val onSelectionChanged: () -> Unit
) : RecyclerView.Adapter<QueueAdapter.QueueViewHolder>() {

    private val mutableItems = mutableListOf<MediaItem>()

    // Vị trí bài đang phát (-1 nếu không có)
    var currentPlayingIndex = -1
        private set

    // Đang ở chế độ chọn nhiều hay không
    var isSelectionMode = false
        private set

    // Tập các bài đã chọn (định danh bằng uri)
    val selectedUris = mutableSetOf<String>()

    class QueueViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val itemContainer: View = view.findViewById(R.id.itemContainer)
        val tvIndex: TextView = view.findViewById(R.id.tvIndex)
        val tvTitle: TextView = view.findViewById(R.id.tvQueueItemTitle)
        val tvArtist: TextView = view.findViewById(R.id.tvQueueItemArtist)
        val tvDuration: TextView = view.findViewById(R.id.tvDuration)
        val ivThumbnail: ImageView = view.findViewById(R.id.ivQueueThumbnail)
        val ivPlayingIcon: ImageView = view.findViewById(R.id.ivPlayingIcon)
        val btnMore: ImageView = view.findViewById(R.id.btnQueueMore)
        val ivSelectCheckbox: ImageView = view.findViewById(R.id.ivSelectCheckbox)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): QueueViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_queue, parent, false)
        return QueueViewHolder(view)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onBindViewHolder(holder: QueueViewHolder, position: Int) {
        val mediaItem = mutableItems[position]
        val metadata = mediaItem.mediaMetadata
        val uri = uriOf(mediaItem)
        val isCurrent = position == currentPlayingIndex

        holder.tvTitle.text = metadata.title ?: "Unknown Title"
        holder.tvArtist.text = metadata.artist ?: "Unknown Artist"
        holder.tvIndex.text = (position + 1).toString()
        holder.tvDuration.text = formatTime(metadata.durationMs ?: 0L)

        val artworkData = metadata.artworkData
        if (artworkData != null) {
            holder.ivThumbnail.setImageBitmap(BitmapFactory.decodeByteArray(artworkData, 0, artworkData.size))
        } else {
            holder.ivThumbnail.setImageResource(R.drawable.bg_cover_default)
        }

        // ----- Màu sắc / nền: bài đang phát được tô tím -----
        if (isCurrent) {
            holder.itemContainer.setBackgroundResource(R.drawable.bg_current_track_highlight)
            holder.tvTitle.setTextColor(Color.parseColor("#7C3AED"))
            holder.tvArtist.setTextColor(Color.parseColor("#7C3AED"))
            holder.ivPlayingIcon.visibility = View.VISIBLE
            holder.tvDuration.visibility = View.GONE
        } else {
            holder.itemContainer.setBackgroundColor(Color.TRANSPARENT)
            holder.tvTitle.setTextColor(Color.parseColor("#1F1B2E"))
            holder.tvArtist.setTextColor(Color.parseColor("#7A5BA8"))
            holder.ivPlayingIcon.visibility = View.GONE
            holder.tvDuration.visibility = View.VISIBLE
        }

        // ----- Cột số thứ tự <-> vòng tròn chọn (cùng 1 vị trí, dùng INVISIBLE để không lệch layout) -----
        if (isSelectionMode) {
            holder.tvIndex.visibility = View.INVISIBLE
            holder.btnMore.visibility = View.INVISIBLE
            if (isCurrent) {
                // Bài đang phát không được chọn/xóa
                holder.ivSelectCheckbox.visibility = View.INVISIBLE
            } else {
                holder.ivSelectCheckbox.visibility = View.VISIBLE
                if (selectedUris.contains(uri)) {
                    holder.ivSelectCheckbox.setImageResource(R.drawable.ic_check_circle_filled)
                    holder.ivSelectCheckbox.imageTintList = null
                } else {
                    holder.ivSelectCheckbox.setImageResource(R.drawable.ic_check_circle_outline)
                    holder.ivSelectCheckbox.imageTintList = ColorStateList.valueOf(Color.parseColor("#9E9E9E"))
                }
            }
        } else {
            holder.ivSelectCheckbox.visibility = View.GONE
            holder.btnMore.visibility = View.VISIBLE
            holder.tvIndex.visibility = if (isCurrent) View.INVISIBLE else View.VISIBLE
        }

        // ----- Sự kiện -----
        holder.itemView.setOnClickListener {
            val pos = holder.bindingAdapterPosition
            if (pos == RecyclerView.NO_POSITION) return@setOnClickListener
            if (isSelectionMode) {
                if (pos != currentPlayingIndex) toggleSelection(uriOf(mutableItems[pos]))
            } else {
                onItemClick(pos)
            }
        }

        // Giữ lâu => vào chế độ chọn nhiều (bỏ qua bài đang phát)
        holder.itemView.setOnLongClickListener {
            val pos = holder.bindingAdapterPosition
            if (pos == RecyclerView.NO_POSITION || pos == currentPlayingIndex) return@setOnLongClickListener true
            if (!isSelectionMode) {
                isSelectionMode = true
                toggleSelection(uriOf(mutableItems[pos]))
            }
            true
        }

        holder.btnMore.setOnClickListener {
            val pos = holder.bindingAdapterPosition
            if (pos != RecyclerView.NO_POSITION) onMoreClick(pos, it)
        }

        // Chạm vào số thứ tự để kéo đổi vị trí (chỉ khi không ở chế độ chọn)
        holder.tvIndex.setOnTouchListener { _, event ->
            if (event.actionMasked == MotionEvent.ACTION_DOWN && !isSelectionMode) {
                onStartDrag(holder)
            }
            false
        }
    }

    override fun getItemCount(): Int = mutableItems.size

    private fun uriOf(item: MediaItem): String = item.localConfiguration?.uri.toString()

    // ====== Chọn nhiều ======
    fun toggleSelection(uri: String) {
        if (!selectedUris.remove(uri)) selectedUris.add(uri)
        // Bỏ chọn hết thì tự thoát chế độ chọn
        if (selectedUris.isEmpty()) isSelectionMode = false
        notifyDataSetChanged()
        onSelectionChanged()
    }

    /** Số bài có thể chọn (trừ bài đang phát) */
    fun selectableCount(): Int =
        if (currentPlayingIndex in mutableItems.indices) mutableItems.size - 1 else mutableItems.size

    fun isAllSelected(): Boolean = selectableCount() > 0 && selectedUris.size >= selectableCount()

    fun selectAll() {
        mutableItems.forEachIndexed { i, item ->
            if (i != currentPlayingIndex) selectedUris.add(uriOf(item))
        }
        isSelectionMode = selectedUris.isNotEmpty()
        notifyDataSetChanged()
        onSelectionChanged()
    }

    fun deselectAll() {
        selectedUris.clear()
        isSelectionMode = false
        notifyDataSetChanged()
        onSelectionChanged()
    }

    // ====== Dữ liệu ======
    fun updateItems(newItems: List<MediaItem>, playingIndex: Int) {
        mutableItems.clear()
        mutableItems.addAll(newItems)
        currentPlayingIndex = playingIndex

        // Bỏ các bài đã chọn nhưng không còn trong hàng đợi
        val existing = mutableItems.map { uriOf(it) }.toSet()
        val changed = selectedUris.retainAll(existing)
        if (selectedUris.isEmpty()) isSelectionMode = false

        notifyDataSetChanged()
        if (changed || !isSelectionMode) onSelectionChanged()
    }

    fun onItemMove(from: Int, to: Int) {
        Collections.swap(mutableItems, from, to)
        // Cập nhật vị trí bài đang phát theo thao tác kéo
        currentPlayingIndex = when (currentPlayingIndex) {
            from -> to
            to -> from
            else -> currentPlayingIndex
        }
        notifyItemMoved(from, to)
    }

    private fun formatTime(millis: Long): String {
        return String.format(
            "%02d:%02d",
            TimeUnit.MILLISECONDS.toMinutes(millis),
            TimeUnit.MILLISECONDS.toSeconds(millis) % 60
        )
    }
}
