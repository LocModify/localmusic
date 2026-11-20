package com.ztrongz.localmusic

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.util.Locale

class SongAdapter(
    songs: List<Track>,
    private val onItemClick: (Track) -> Unit
) : RecyclerView.Adapter<SongAdapter.SongViewHolder>() {

    private var allSongs: List<Track> = songs.toList()
    private var shownSongs: List<Track> = allSongs

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
        val song = shownSongs[position]
        holder.tvTitle.text = song.title
        holder.tvArtist.text = song.artist
        holder.tvDuration.text = formatDuration(song.durationMs)
        if (song.cover != null) holder.ivCover.setImageBitmap(song.cover)
        else holder.ivCover.setImageResource(R.drawable.bg_cover_placeholder)
        holder.itemView.setOnClickListener { onItemClick(song) }
    }

    override fun getItemCount(): Int = shownSongs.size

    fun updateData(newSongs: List<Track>, query: String = "") {
        allSongs = newSongs.toList()
        filter(query)
    }

    // Lọc realtime theo tên bài hoặc ca sĩ
    fun filter(query: String) {
        val q = query.trim().lowercase(Locale.getDefault())
        shownSongs = if (q.isEmpty()) allSongs
        else allSongs.filter {
            it.title.lowercase(Locale.getDefault()).contains(q) ||
                it.artist.lowercase(Locale.getDefault()).contains(q)
        }
        notifyDataSetChanged()
    }

    private fun formatDuration(ms: Long): String {
        if (ms <= 0) return "--:--"
        val total = ms / 1000
        return String.format(Locale.US, "%02d:%02d", total / 60, total % 60)
    }
}