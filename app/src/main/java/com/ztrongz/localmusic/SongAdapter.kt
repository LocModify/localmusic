package com.ztrongz.localmusic

import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.ztrongz.localmusic.databinding.ItemSongBinding
import java.util.concurrent.TimeUnit

class SongAdapter(
    private var songs: List<Track>,
    private val onSongClick: (Track) -> Unit,
    private val onMoreClick: (Track, View) -> Unit,
    private val onSelectionChanged: () -> Unit = {}
) : RecyclerView.Adapter<SongAdapter.SongViewHolder>() {

    private var displayList: MutableList<Track> = songs.toMutableList()

    // ----- Chọn nhiều (dùng trong thư mục để xóa nhiều bài) -----
    var isSelecting = false
        private set
    private val selected = mutableSetOf<String>()   // uri của các bài đã chọn

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

            val thumb = ThumbCache.get(track)
            if (thumb != null) {
                ivCover.setImageBitmap(thumb)
            } else {
                ivCover.setImageResource(R.drawable.bg_cover_default)
            }

            // Chế độ chọn nhiều: thay nút ba chấm bằng vòng tròn chọn
            if (isSelecting) {
                btnMore.visibility = View.INVISIBLE
                ivSelect.visibility = View.VISIBLE
                if (selected.contains(track.uri.toString())) {
                    ivSelect.setImageResource(R.drawable.ic_check_circle_filled)
                    ivSelect.imageTintList = null
                } else {
                    ivSelect.setImageResource(R.drawable.ic_check_circle_outline)
                    ivSelect.imageTintList = ColorStateList.valueOf(Color.parseColor("#9E9E9E"))
                }
            } else {
                btnMore.visibility = View.VISIBLE
                ivSelect.visibility = View.GONE
            }

            // Bắt sự kiện click vào bài hát (đang chọn nhiều thì bấm = chọn / bỏ chọn)
            root.setOnClickListener {
                if (isSelecting) {
                    val key = track.uri.toString()
                    if (!selected.add(key)) selected.remove(key)
                    val pos = holder.bindingAdapterPosition
                    if (pos != RecyclerView.NO_POSITION) notifyItemChanged(pos)
                    onSelectionChanged()
                } else {
                    onSongClick(track)
                }
            }

            // Bắt sự kiện click vào nút 3 chấm
            btnMore.setOnClickListener { view -> onMoreClick(track, view) }
        }
    }

    override fun getItemCount(): Int = displayList.size

    fun updateData(newList: List<Track>) {
        displayList = newList.toMutableList()
        notifyDataSetChanged()
    }

    // ----- Chọn nhiều -----
    fun enterSelection() {
        isSelecting = true
        selected.clear()
        notifyDataSetChanged()
        onSelectionChanged()
    }

    fun exitSelection() {
        isSelecting = false
        selected.clear()
        notifyDataSetChanged()
        onSelectionChanged()
    }

    // Chỉ tính các bài đang hiển thị
    fun selectedUris(): Set<String> {
        val shown = displayList.map { it.uri.toString() }.toSet()
        return selected.filter { it in shown }.toSet()
    }

    fun selectedCount(): Int = selectedUris().size

    // ----- Kéo thả đổi vị trí -----
    fun moveItem(from: Int, to: Int) {
        if (from !in displayList.indices || to !in displayList.indices) return
        val item = displayList.removeAt(from)
        displayList.add(to, item)
        notifyItemMoved(from, to)
    }

    fun currentList(): List<Track> = displayList

    private fun formatTime(millis: Long): String {
        return String.format(
            "%02d:%02d",
            TimeUnit.MILLISECONDS.toMinutes(millis),
            TimeUnit.MILLISECONDS.toSeconds(millis) % 60
        )
    }
}
