package com.ztrongz.localmusic

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class SongAdapter(private var songs: List<Track>) : RecyclerView.Adapter<SongAdapter.SongViewHolder>() {

    class SongViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvTitle: TextView = view.findViewById(R.id.tvTitle)
        val tvArtist: TextView = view.findViewById(R.id.tvArtist)
        val tvDuration: TextView = view.findViewById(R.id.tvDuration)
        val imgCover: ImageView = view.findViewById(R.id.imgCover)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SongViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_song, parent, false)
        return SongViewHolder(view)
    }

    override fun onBindViewHolder(holder: SongViewHolder, position: Int) {
        val song = songs[position]
        holder.tvTitle.text = song.title
        holder.tvArtist.text = "Unknown Artist" 
        holder.tvDuration.text = "--:--" 
    }

    override fun getItemCount(): Int = songs.size

    fun updateData(newSongs: List<Track>) {
        songs = newSongs
        notifyDataSetChanged()
    }
}