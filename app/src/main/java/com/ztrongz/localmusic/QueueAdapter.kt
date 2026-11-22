package com.ztrongz.localmusic

import android.graphics.BitmapFactory
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import androidx.media3.common.MediaItem
import java.util.Collections

class QueueAdapter(
    private var items: List<MediaItem>,
    private val onItemClick: (Int) -> Unit,
    private val onMoreClick: (Int, View) -> Unit
) : RecyclerView.Adapter<QueueAdapter.QueueViewHolder>() {

    private val mutableItems = items.toMutableList()
    private var currentPlayingIndex = -1

    class QueueViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvTitle: TextView = view.findViewById(R.id.tvQueueItemTitle)
        val tvArtist: TextView = view.findViewById(R.id.tvQueueItemArtist)
        val ivThumbnail: ImageView = view.findViewById(R.id.ivQueueThumbnail)
        val btnMore: ImageView = view.findViewById(R.id.btnQueueMore)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): QueueViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_queue, parent, false)
        return QueueViewHolder(view)
    }

    override fun onBindViewHolder(holder: QueueViewHolder, position: Int) {
        val mediaItem = mutableItems[position]
        val metadata = mediaItem.mediaMetadata

        holder.tvTitle.text = metadata.title ?: "Unknown Title"
        
        // Hiển thị trạng thái bài đang phát
        if (position == currentPlayingIndex) {
            holder.tvArtist.text = "Đang phát"
            holder.tvArtist.setTextColor(android.graphics.Color.parseColor("#7C3AED"))
            holder.tvTitle.setTextColor(android.graphics.Color.parseColor("#7C3AED"))
        } else {
            holder.tvArtist.text = metadata.artist ?: "Unknown Artist"
            holder.tvArtist.setTextColor(android.graphics.Color.parseColor("#7A5BA8"))
            holder.tvTitle.setTextColor(android.graphics.Color.parseColor("#1F1B2E"))
        }

        // Load ảnh bìa
        val artworkData = metadata.artworkData
        if (artworkData != null) {
            val bitmap = BitmapFactory.decodeByteArray(artworkData, 0, artworkData.size)
            holder.ivThumbnail.setImageBitmap(bitmap)
        } else {
            holder.ivThumbnail.setImageResource(R.drawable.bg_cover_default)
        }

        holder.itemView.setOnClickListener {
            onItemClick(holder.bindingAdapterPosition)
        }
        
        holder.btnMore.setOnClickListener {
            onMoreClick(holder.bindingAdapterPosition, it)
        }
    }

    override fun getItemCount(): Int = mutableItems.size

    fun updateItems(newItems: List<MediaItem>, currentIndex: Int) {
        mutableItems.clear()
        mutableItems.addAll(newItems)
        currentPlayingIndex = currentIndex
        notifyDataSetChanged()
    }

    fun setCurrentPlayingIndex(index: Int) {
        val oldIndex = currentPlayingIndex
        currentPlayingIndex = index
        if (oldIndex in mutableItems.indices) {
            notifyItemChanged(oldIndex)
        }
        if (index in mutableItems.indices) {
            notifyItemChanged(index)
        }
    }

    fun onItemMove(fromPosition: Int, toPosition: Int) {
        Collections.swap(mutableItems, fromPosition, toPosition)
        notifyItemMoved(fromPosition, toPosition)
    }

    fun getCurrentList(): List<MediaItem> = mutableItems
}