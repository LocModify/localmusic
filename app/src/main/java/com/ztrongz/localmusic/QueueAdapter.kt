package com.ztrongz.localmusic

import android.graphics.BitmapFactory
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.media3.common.MediaItem
import androidx.recyclerview.widget.RecyclerView
import java.util.Collections

class QueueAdapter(
    items: List<MediaItem>,
    // Số thứ tự hiển thị của dòng đầu tiên (mặc định 1)
    private val startIndex: Int = 1,
    private val onItemClick: (Int) -> Unit,
    private val onStartDrag: (RecyclerView.ViewHolder) -> Unit,
    private val onMoreClick: (Int, View) -> Unit
) : RecyclerView.Adapter<QueueAdapter.QueueViewHolder>() {

    private val mutableItems = items.toMutableList()

    // Vị trí của bài đang phát trong danh sách (-1 = không có)
    private var currentPlayingIndex = -1

    class QueueViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val itemContainer: View = view.findViewById(R.id.itemContainer)
        val tvIndex: TextView = view.findViewById(R.id.tvIndex)
        val tvTitle: TextView = view.findViewById(R.id.tvQueueItemTitle)
        val tvArtist: TextView = view.findViewById(R.id.tvQueueItemArtist)
        val tvDuration: TextView = view.findViewById(R.id.tvDuration)
        val ivThumbnail: ImageView = view.findViewById(R.id.ivQueueThumbnail)
        val ivPlayingIcon: ImageView = view.findViewById(R.id.ivPlayingIcon)
        val btnMore: ImageView = view.findViewById(R.id.btnQueueMore)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): QueueViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_queue, parent, false)
        return QueueViewHolder(view)
    }

    override fun onBindViewHolder(holder: QueueViewHolder, position: Int) {
        val metadata = mutableItems[position].mediaMetadata

        holder.tvTitle.text = metadata.title ?: "Unknown Title"
        holder.tvArtist.text = metadata.artist ?: "Unknown Artist"
        holder.tvIndex.text = (position + startIndex).toString()
        holder.tvDuration.text = TimeUtils.format(metadata.durationMs ?: 0L)

        val artworkData = metadata.artworkData
        if (artworkData != null) {
            val bitmap = BitmapFactory.decodeByteArray(artworkData, 0, artworkData.size)
            if (bitmap != null) holder.ivThumbnail.setImageBitmap(bitmap)
            else holder.ivThumbnail.setImageResource(R.drawable.bg_cover_default)
        } else {
            holder.ivThumbnail.setImageResource(R.drawable.bg_cover_default)
        }

        if (position == currentPlayingIndex) {
            // Bài đang phát: nền tím nhạt + biểu tượng sóng âm (ẩn số thứ tự, giữ chỗ cho thời lượng/ba chấm)
            holder.itemContainer.setBackgroundResource(R.drawable.bg_current_track_highlight)
            holder.tvIndex.visibility = View.GONE
            holder.ivPlayingIcon.visibility = View.VISIBLE
            holder.tvDuration.visibility = View.INVISIBLE
            holder.btnMore.visibility = View.INVISIBLE
        } else {
            holder.itemContainer.setBackgroundColor(Color.TRANSPARENT)
            holder.tvIndex.visibility = View.VISIBLE
            holder.ivPlayingIcon.visibility = View.GONE
            holder.tvDuration.visibility = View.VISIBLE
            holder.btnMore.visibility = View.VISIBLE
        }

        holder.itemView.setOnClickListener {
            val pos = holder.bindingAdapterPosition
            if (pos != RecyclerView.NO_POSITION) onItemClick(pos)
        }

        holder.btnMore.setOnClickListener {
            val pos = holder.bindingAdapterPosition
            if (pos != RecyclerView.NO_POSITION) onMoreClick(pos, it)
        }

        holder.itemView.setOnLongClickListener {
            onStartDrag(holder)
            true
        }
    }

    override fun getItemCount(): Int = mutableItems.size

    // Cập nhật danh sách. playingIndex = vị trí bài đang phát để tô nền (-1 = không tô)
    fun updateItems(newItems: List<MediaItem>, playingIndex: Int = 0) {
        // Danh sách không đổi thì bỏ qua để không làm giật khi đang vuốt/kéo
        if (playingIndex == currentPlayingIndex && isSameQueue(newItems)) return
        mutableItems.clear()
        mutableItems.addAll(newItems)
        currentPlayingIndex = playingIndex
        notifyDataSetChanged()
    }

    private fun isSameQueue(newItems: List<MediaItem>): Boolean {
        if (newItems.size != mutableItems.size) return false
        for (i in newItems.indices) {
            val a = newItems[i]
            val b = mutableItems[i]
            if (a.localConfiguration?.uri != b.localConfiguration?.uri) return false
            if (a.mediaMetadata.title != b.mediaMetadata.title) return false
        }
        return true
    }

    fun onItemMove(fromPosition: Int, toPosition: Int) {
        Collections.swap(mutableItems, fromPosition, toPosition)
        notifyItemMoved(fromPosition, toPosition)
    }

    fun removeItem(position: Int) {
        if (position in mutableItems.indices) {
            mutableItems.removeAt(position)
            notifyItemRemoved(position)
        }
    }

    fun addItem(position: Int, item: MediaItem) {
        if (position <= mutableItems.size) {
            mutableItems.add(position, item)
            notifyItemInserted(position)
        }
    }

    fun getCurrentList(): List<MediaItem> = mutableItems
}