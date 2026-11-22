package com.ztrongz.localmusic

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
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
            // ⚠️ SỬA ID TẠI ĐÂY NẾU CẦN (Ví dụ: nếu layout của anh tên là tvTitle thay vì tvSongTitle)
            tvSongTitle.text = track.title 
            tvSongArtist.text = track.artist
            
            // ĐÃ SỬA LỖI: Dùng track.duration thay vì track.durationMs
            tvSongDuration.text = formatTime(track.duration)

            if (track.cover != null) {
                ivSongCover.setImageBitmap(track.cover)
            } else {
                ivSongCover.setImageResource(R.drawable.bg_cover_default)
            }

            // Hiển thị trạng thái yêu thích
            if (track.isFavorite) {
                ivFavoriteIcon.setImageResource(R.drawable.ic_heart_filled)
                ivFavoriteIcon.setColorFilter(android.graphics.Color.parseColor("#FF3B5C"))
            } else {
                ivFavoriteIcon.setImageResource(R.drawable.ic_heart_outline)
                ivFavoriteIcon.setColorFilter(android.graphics.Color.parseColor("#B45CFF"))
            }

            root.setOnClickListener { onSongClick(track) }
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