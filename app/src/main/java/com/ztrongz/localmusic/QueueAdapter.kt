package com.ztrongz.localmusic

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
    private val onItemClick: (Int) -> Unit
) : RecyclerView.Adapter<QueueAdapter.QueueViewHolder>() {

    private val mutableItems = items.toMutableList()
    private var currentPlayingIndex = -1

    class QueueViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvTitle: TextView = view.findViewById(R.id.tvQueueItemTitle)
        val tvArtist: TextView = view.findViewById(R.id.tvQueueItemArtist)
        val ivPlaying: ImageView = view.findViewById(R.id.ivQueueItemPlaying)
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
        holder.tvArtist.text = metadata.artist ?: "Unknown Artist"

        holder.ivPlaying.visibility = if (position == currentPlayingIndex) View.VISIBLE else View.INVISIBLE

        holder.itemView.setOnClickListener {
            onItemClick(holder.bindingAdapterPosition)
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