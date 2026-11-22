package com.ztrongz.localmusic

import android.graphics.BitmapFactory
import android.view.LayoutInflater
import android.view.MotionEvent
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
    private val onStartDrag: (RecyclerView.ViewHolder) -> Unit
) : RecyclerView.Adapter<QueueAdapter.QueueViewHolder>() {

    private val mutableItems = items.toMutableList()

    class QueueViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvTitle: TextView = view.findViewById(R.id.tvQueueItemTitle)
        val tvArtist: TextView = view.findViewById(R.id.tvQueueItemArtist)
        val ivThumbnail: ImageView = view.findViewById(R.id.ivQueueThumbnail)
        val btnDragHandle: ImageView = view.findViewById(R.id.btnDragHandle)
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

        // Kéo thả bằng tay nắm
        holder.btnDragHandle.setOnTouchListener { _, event ->
            if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                onStartDrag(holder)
            }
            false
        }
    }

    override fun getItemCount(): Int = mutableItems.size

    fun updateItems(newItems: List<MediaItem>) {
        mutableItems.clear()
        mutableItems.addAll(newItems)
        notifyDataSetChanged()
    }

    fun getItemAt(position: Int): MediaItem = mutableItems[position]

    fun onItemMove(fromPosition: Int, toPosition: Int) {
        Collections.swap(mutableItems, fromPosition, toPosition)
        notifyItemMoved(fromPosition, toPosition)
    }

    fun removeItem(position: Int) {
        mutableItems.removeAt(position)
        notifyItemRemoved(position)
    }

    fun addItem(position: Int, item: MediaItem) {
        mutableItems.add(position, item)
        notifyItemInserted(position)
    }

    fun getCurrentList(): List<MediaItem> = mutableItems
}