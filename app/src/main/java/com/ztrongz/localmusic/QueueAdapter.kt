package com.ztrongz.localmusic

import android.graphics.BitmapFactory
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import androidx.media3.common.MediaItem
import java.util.Collections
import java.util.concurrent.TimeUnit

class QueueAdapter(
    private var items: List<MediaItem>,
    private val startIndex: Int,
    private val onItemClick: (Int) -> Unit,
    private val onStartDrag: (RecyclerView.ViewHolder) -> Unit,
    private val onMoreClick: (Int, View) -> Unit,
    private val onSelectionChanged: () -> Unit
) : RecyclerView.Adapter<QueueAdapter.QueueViewHolder>() {

    private val mutableItems = items.toMutableList()
    var currentPlayingIndex = -1 // Đã public để bên ngoài truy cập được

    var isSelectionMode = false
        private set
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
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_queue, parent, false)
        return QueueViewHolder(view)
    }

    override fun onBindViewHolder(holder: QueueViewHolder, position: Int) {
        val mediaItem = mutableItems[position]
        val metadata = mediaItem.mediaMetadata
        val uri = mediaItem.localConfiguration?.uri.toString()

        holder.tvTitle.text = metadata.title ?: "Unknown Title"
        holder.tvArtist.text = metadata.artist ?: "Unknown Artist"
        holder.tvIndex.text = (position + startIndex).toString()

        val durationMs = metadata.durationMs ?: 0L
        holder.tvDuration.text = formatTime(durationMs)

        val artworkData = metadata.artworkData
        if (artworkData != null) {
            val bitmap = BitmapFactory.decodeByteArray(artworkData, 0, artworkData.size)
            holder.ivThumbnail.setImageBitmap(bitmap)
        } else {
            holder.ivThumbnail.setImageResource(R.drawable.bg_cover_default)
        }

        if (isSelectionMode) {
            holder.ivSelectCheckbox.visibility = View.VISIBLE
            holder.tvIndex.visibility = View.GONE
            holder.btnMore.visibility = View.GONE
            holder.itemContainer.setBackgroundColor(Color.TRANSPARENT)

            if (selectedUris.contains(uri)) {
                holder.ivSelectCheckbox.setImageResource(R.drawable.ic_check_circle_outline)
                holder.ivSelectCheckbox.setColorFilter(Color.parseColor("#7C3AED"))
            } else {
                holder.ivSelectCheckbox.setImageResource(R.drawable.ic_check_circle_outline)
                holder.ivSelectCheckbox.setColorFilter(Color.parseColor("#9E9E9E"))
            }
        } else {
            holder.ivSelectCheckbox.visibility = View.GONE
            holder.btnMore.visibility = View.VISIBLE

            if (position == currentPlayingIndex) {
                holder.itemContainer.setBackgroundResource(R.drawable.bg_current_track_highlight)
                holder.tvIndex.visibility = View.GONE
                holder.ivPlayingIcon.visibility = View.VISIBLE
                holder.tvTitle.setTextColor(Color.parseColor("#7C3AED"))
                holder.tvArtist.setTextColor(Color.parseColor("#7C3AED"))
                holder.tvDuration.visibility = View.GONE
            } else {
                holder.itemContainer.setBackgroundColor(Color.TRANSPARENT)
                holder.tvIndex.visibility = View.VISIBLE
                holder.ivPlayingIcon.visibility = View.GONE
                holder.tvTitle.setTextColor(Color.parseColor("#1F1B2E"))
                holder.tvArtist.setTextColor(Color.parseColor("#7A5BA8"))
                holder.tvDuration.visibility = View.VISIBLE
            }
        }

        holder.itemView.setOnClickListener {
            if (isSelectionMode) {
                toggleSelection(uri)
            } else {
                onItemClick(holder.bindingAdapterPosition)
            }
        }

        holder.itemView.setOnLongClickListener {
            if (!isSelectionMode) {
                isSelectionMode = true
                toggleSelection(uri)
            }
            true
        }

        holder.btnMore.setOnClickListener {
            onMoreClick(holder.bindingAdapterPosition, it)
        }

        holder.itemView.setOnLongClickListener {
            if (isSelectionMode) return@setOnLongClickListener false
            onStartDrag(holder)
            true
        }
    }

    override fun getItemCount(): Int = mutableItems.size

    fun toggleSelection(uri: String) {
        if (selectedUris.contains(uri)) {
            selectedUris.remove(uri)
            if (selectedUris.isEmpty()) isSelectionMode = false
        } else {
            selectedUris.add(uri)
        }
        notifyDataSetChanged()
        onSelectionChanged()
    }

    fun selectAll() {
        for (item in mutableItems) {
            item.localConfiguration?.uri?.toString()?.let { selectedUris.add(it) }
        }
        isSelectionMode = true
        notifyDataSetChanged()
        onSelectionChanged()
    }

    fun deselectAll() {
        selectedUris.clear()
        isSelectionMode = false
        notifyDataSetChanged()
        onSelectionChanged()
    }

    fun getSelectedItems(): List<MediaItem> {
        return mutableItems.filter { selectedUris.contains(it.localConfiguration?.uri.toString()) }
    }

    fun updateItems(newItems: List<MediaItem>, playingIndex: Int) {
        mutableItems.clear()
        mutableItems.addAll(newItems)
        currentPlayingIndex = playingIndex
        notifyDataSetChanged()
    }

    fun onItemMove(fromPosition: Int, toPosition: Int) {
        Collections.swap(mutableItems, fromPosition, toPosition)
        notifyItemMoved(fromPosition, toPosition)
    }

    fun getCurrentList(): List<MediaItem> = mutableItems

    private fun formatTime(millis: Long): String {
        return String.format(
            "%02d:%02d",
            TimeUnit.MILLISECONDS.toMinutes(millis),
            TimeUnit.MILLISECONDS.toSeconds(millis) % 60
        )
    }
}