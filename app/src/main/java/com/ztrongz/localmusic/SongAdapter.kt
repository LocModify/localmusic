package com.ztrongz.localmusic

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.ztrongz.localmusic.databinding.ItemSongBinding
import java.util.concurrent.TimeUnit

class SongAdapter(
    private var songs: List<Track>,
    private val onSongClick: (Track) -> Unit,
    private val onMoreClick: (Track, View) -> Unit
) : RecyclerView.Adapter<SongAdapter.SongViewHolder>() {

    private var displayList: List<Track> = songs

    inner class SongViewHolder(val binding: ItemSongBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SongViewHolder {
        val binding = ItemSongBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return SongViewHolder(binding)
    }

    override fun onBindViewHolder(holder: SongViewHolder, position: Int) {
        val track = displayList[position]
        
        with(holder.binding) {
            // Ánh xạ chính xác với các ID trong file item_song.xml của anh
            tvTitle.text = track.title
            tvArtist.text = track.artist
            tvDuration.text = formatTime(track.duration)

            if (track.cover != null) {
                ivCover.setImageBitmap(track.cover)
            } else {
                ivCover.setImageResource(R.drawable.bg_cover_default)
            }

            // Bắt sự kiện click vào bài hát
            root.setOnClickListener { onSongClick(track) }
            
            // Bắt sự kiện click vào nút 3 chấm
            btnMore.setOnClickListener { view -> onMoreClick(track, view) }
        }
    }

    override fun getItemCount(): Int = displayList.size

    fun updateData(newList: List<Track>) {
        displayList = newList
        notifyDataSetChanged()
    }

    private fun formatTime(millis: Long): String {
        return String.format(
            "%02d:%02d",
            TimeUnit.MILLISECONDS.toMinutes(millis),
            TimeUnit.MILLISECONDS.toSeconds(millis) % 60
        )
    }
}