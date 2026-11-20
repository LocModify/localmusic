package com.ztrongz.localmusic

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class SongAdapter(
    private var songs: List<Track>,
    private val onSongClick: (Int) -> Unit
) : RecyclerView.Adapter<SongAdapter.SongViewHolder>() {

    class SongViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvTitle: TextView = view.findViewById(R.id.tvTitle)
        val tvArtist: TextView = view.findViewById(R.id.tvArtist)
        val tvDuration: TextView = view.findViewById(R.id.tvDuration)
        val ivCover: ImageView = view.findViewById(R.id.ivCover)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SongViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_song, parent, false)
        return SongViewHolder(view)
    }

    override fun onBindViewHolder(holder: SongViewHolder, position: Int) {
        val song = songs[position]
        
        holder.tvTitle.text = song.title
        holder.tvDuration.text = formatDuration(song.durationMs)

        if (song.artist.isBlank()) {
            holder.tvArtist.visibility = View.GONE
        } else {
            holder.tvArtist.visibility = View.VISIBLE
            holder.tvArtist.text = song.artist
        }

        // ĐÃ ĐỔI THÀNH ẢNH MỚI
        if (song.cover != null) {
            holder.ivCover.setImageBitmap(song.cover)
        } else {
            holder.ivCover.setImageResource(R.drawable.bg_cover_default)
        }

        holder.itemView.setOnClickListener {
            onSongClick(position)
        }
    }

    override fun getItemCount(): Int = songs.size

    fun updateData(newSongs: List<Track>) {
        songs = newSongs
        notifyDataSetChanged()
    }

    private fun formatDuration(durationMs: Long): String {
        val totalSeconds = durationMs / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format("%02d:%02d", minutes, seconds)
    }
}