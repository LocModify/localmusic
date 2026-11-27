package com.ztrongz.localmusic

import android.graphics.Bitmap
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

// Danh sách các thư mục (danh sách phát đã lưu) ở màn hình chính
class FolderAdapter(
    private val countOf: (SavedPlaylist) -> Int,   // số bài còn tồn tại trong thư viện
    private val coverOf: (SavedPlaylist) -> Bitmap?, // ảnh bìa của bài đầu tiên (null = dùng ảnh mặc định)
    private val onFolderClick: (SavedPlaylist) -> Unit,
    private val onMoreClick: (SavedPlaylist, View) -> Unit
) : RecyclerView.Adapter<FolderAdapter.VH>() {

    private var items: List<SavedPlaylist> = emptyList()

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val tvName: TextView = view.findViewById(R.id.tvFolderName)
        val tvCount: TextView = view.findViewById(R.id.tvFolderCount)
        val btnMore: ImageView = view.findViewById(R.id.btnFolderMore)
        val ivCover: ImageView = view.findViewById(R.id.ivFolderCover)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_folder, parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) {
        val p = items[position]
        holder.tvName.text = p.name
        holder.tvCount.text = "${countOf(p)} bài hát"
        val cover = coverOf(p)
        if (cover != null) holder.ivCover.setImageBitmap(cover)
        else holder.ivCover.setImageResource(R.drawable.bg_cover_default)
        holder.itemView.setOnClickListener { onFolderClick(p) }
        holder.btnMore.setOnClickListener { onMoreClick(p, it) }
    }

    override fun getItemCount(): Int = items.size

    fun updateData(newList: List<SavedPlaylist>) {
        items = newList
        notifyDataSetChanged()
    }
}
