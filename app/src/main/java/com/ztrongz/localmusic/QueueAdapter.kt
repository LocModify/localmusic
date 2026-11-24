package com.ztrongz.localmusic

import android.annotation.SuppressLint
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.media3.common.MediaItem
import android.view.animation.OvershootInterpolator
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import java.util.Collections
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Adapter hiển thị TOÀN BỘ hàng đợi (vị trí trong adapter == vị trí trong Player).
 * - Bấm: phát bài đó (hoặc tick chọn khi đang ở chế độ chọn nhiều)
 * - Giữ lâu: vào chế độ chọn nhiều
 * - Chạm vào số thứ tự rồi kéo: đổi thứ tự bài
 */
class QueueAdapter(
    private val onItemClick: (Int) -> Unit,
    private val onStartDrag: (RecyclerView.ViewHolder) -> Unit,
    private val onMoreClick: (Int, View) -> Unit,
    private val onSelectionChanged: () -> Unit
) : RecyclerView.Adapter<QueueAdapter.QueueViewHolder>() {

    companion object {
        const val PAYLOAD_MODE = "mode"       // vào/ra chế độ chọn nhiều (có hiệu ứng mờ dần)
        const val PAYLOAD_TOGGLE = "toggle"   // tick/bỏ tick (có hiệu ứng nảy nhẹ)
        const val PAYLOAD_REBIND = "rebind"   // vẽ lại đầy đủ nhưng không nháy
        const val PAYLOAD_PLAYSTATE = "playstate" // chỉ bật/tắt sóng nhạc

        // Giải mã ảnh bìa ở luồng nền (1 luồng dùng chung) để cuộn / mở bảng không bị giật
        private val thumbExecutor = Executors.newSingleThreadExecutor()
        private const val THUMB_TARGET_PX = 192   // cạnh ngắn của ảnh sau khi thu nhỏ (px). Tăng = nét hơn nhưng nặng hơn
        private const val THUMB_CACHE_SIZE = 64   // số ảnh bìa giữ trong bộ nhớ
    }

    private val mutableItems = mutableListOf<MediaItem>()
    private val bitmapCache = LruCache<String, Bitmap>(THUMB_CACHE_SIZE)
    private val mainHandler = Handler(Looper.getMainLooper())
    // Các view đang chờ ảnh của cùng một bài (tránh giải mã trùng)
    private val pendingThumbs = HashMap<String, MutableList<(Bitmap) -> Unit>>()

    // Vị trí bài đang phát (-1 nếu không có)
    var currentPlayingIndex = -1
        private set

    // Nhạc đang phát hay tạm dừng (để sóng nhạc ở bài hiện tại nhảy / đứng yên)
    private var isPlayerPlaying = false

    fun setPlayerPlaying(playing: Boolean) {
        if (isPlayerPlaying == playing) return
        isPlayerPlaying = playing
        if (currentPlayingIndex in mutableItems.indices) {
            notifyItemChanged(currentPlayingIndex, PAYLOAD_PLAYSTATE)
        }
    }

    // Đang ở chế độ chọn nhiều hay không
    var isSelectionMode = false
        private set

    // Tập các bài đã chọn (định danh bằng uri)
    val selectedUris = mutableSetOf<String>()

    class QueueViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val itemContainer: View = view.findViewById(R.id.itemContainer)
        val tvIndex: TextView = view.findViewById(R.id.tvIndex)
        val tvTitle: TextView = view.findViewById(R.id.tvQueueItemTitle)
        val tvArtist: TextView = view.findViewById(R.id.tvQueueItemArtist)
        val tvDuration: TextView = view.findViewById(R.id.tvDuration)
        val ivThumbnail: ImageView = view.findViewById(R.id.ivQueueThumbnail)
        val ivPlayingIcon: PlayingBarsView = view.findViewById(R.id.ivPlayingIcon)
        val btnMore: ImageView = view.findViewById(R.id.btnQueueMore)
        val ivSelectCheckbox: ImageView = view.findViewById(R.id.ivSelectCheckbox)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): QueueViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_queue, parent, false)
        return QueueViewHolder(view)
    }

    // Giải mã ảnh bìa ở luồng nền, thu nhỏ bằng inSampleSize rồi trả kết quả về luồng chính
    private fun loadThumbAsync(uri: String, data: ByteArray, onLoaded: (Bitmap) -> Unit) {
        val waiting = pendingThumbs[uri]
        if (waiting != null) { waiting.add(onLoaded); return }
        pendingThumbs[uri] = mutableListOf(onLoaded)

        thumbExecutor.execute {
            val bmp: Bitmap? = try {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(data, 0, data.size, bounds)
                var sample = 1
                val shortSide = minOf(bounds.outWidth, bounds.outHeight)
                while (shortSide / (sample * 2) >= THUMB_TARGET_PX) sample *= 2
                val opts = BitmapFactory.Options().apply { inSampleSize = sample }
                BitmapFactory.decodeByteArray(data, 0, data.size, opts)
            } catch (e: Exception) {
                null
            }
            mainHandler.post {
                val callbacks = pendingThumbs.remove(uri)
                if (bmp != null) {
                    bitmapCache.put(uri, bmp)
                    callbacks?.forEach { it(bmp) }
                }
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onBindViewHolder(holder: QueueViewHolder, position: Int) {
        val mediaItem = mutableItems[position]
        val metadata = mediaItem.mediaMetadata
        val uri = uriOf(mediaItem)
        val isCurrent = position == currentPlayingIndex

        holder.tvTitle.text = metadata.title ?: "Unknown Title"
        holder.tvArtist.text = metadata.artist ?: "Unknown Artist"
        holder.tvIndex.text = (position + 1).toString()
        holder.tvDuration.text = formatTime(metadata.durationMs ?: 0L)

        // Ảnh bìa: có sẵn trong cache thì gắn ngay, chưa có thì hiện ảnh mặc định rồi giải mã nền
        val artworkData = metadata.artworkData
        holder.ivThumbnail.tag = uri
        if (artworkData != null) {
            val cached = bitmapCache.get(uri)
            if (cached != null) {
                holder.ivThumbnail.setImageBitmap(cached)
            } else {
                holder.ivThumbnail.setImageResource(R.drawable.bg_cover_default)
                loadThumbAsync(uri, artworkData) { bmp ->
                    // View có thể đã được tái sử dụng cho bài khác: chỉ gắn nếu còn đúng bài
                    if (holder.ivThumbnail.tag == uri) holder.ivThumbnail.setImageBitmap(bmp)
                }
            }
        } else {
            holder.ivThumbnail.setImageResource(R.drawable.bg_cover_default)
        }

        // ----- Màu sắc / nền: bài đang phát được tô tím -----
        if (isCurrent) {
            holder.itemContainer.setBackgroundResource(R.drawable.bg_current_track_highlight)
            holder.tvTitle.setTextColor(Color.parseColor("#1F1B2E"))
            holder.tvArtist.setTextColor(Color.parseColor("#7A5BA8"))
            holder.ivPlayingIcon.visibility = View.VISIBLE
            holder.ivPlayingIcon.setPlaying(isPlayerPlaying)
            holder.tvDuration.visibility = View.GONE
        } else {
            holder.itemContainer.setBackgroundColor(Color.TRANSPARENT)
            holder.tvTitle.setTextColor(Color.parseColor("#1F1B2E"))
            holder.tvArtist.setTextColor(Color.parseColor("#7A5BA8"))
            holder.ivPlayingIcon.setPlaying(false)
            holder.ivPlayingIcon.visibility = View.GONE
            holder.tvDuration.visibility = View.VISIBLE
        }

        // Tên dài: chữ tự chạy lặp lại (marquee cần isSelected = true)
        holder.tvTitle.isSelected = true
        holder.tvArtist.isSelected = true

        applySelectionState(holder, position)

        // ----- Sự kiện -----
        holder.itemView.setOnClickListener {
            val pos = holder.bindingAdapterPosition
            if (pos == RecyclerView.NO_POSITION) return@setOnClickListener
            if (isSelectionMode) {
                if (pos != currentPlayingIndex) toggleSelection(uriOf(mutableItems[pos]))
            } else {
                onItemClick(pos)
            }
        }

        // Giữ lâu => vào chế độ chọn nhiều (bỏ qua bài đang phát)
        holder.itemView.setOnLongClickListener {
            val pos = holder.bindingAdapterPosition
            if (pos == RecyclerView.NO_POSITION || pos == currentPlayingIndex) return@setOnLongClickListener true
            if (!isSelectionMode) startSelection(uriOf(mutableItems[pos]))
            true
        }

        holder.btnMore.setOnClickListener {
            val pos = holder.bindingAdapterPosition
            if (pos != RecyclerView.NO_POSITION) onMoreClick(pos, it)
        }

        // Chạm vào số thứ tự để kéo đổi vị trí (chỉ khi không ở chế độ chọn)
        holder.tvIndex.setOnTouchListener { _, event ->
            if (event.actionMasked == MotionEvent.ACTION_DOWN && !isSelectionMode) {
                onStartDrag(holder)
            }
            false
        }
    }

    // Bind từng phần (payload) để không bị nháy và có animation mượt
    override fun onBindViewHolder(holder: QueueViewHolder, position: Int, payloads: MutableList<Any>) {
        if (payloads.isEmpty() || payloads.contains(PAYLOAD_REBIND)) {
            onBindViewHolder(holder, position)
            return
        }
        if (payloads.contains(PAYLOAD_PLAYSTATE)) {
            holder.ivPlayingIcon.setPlaying(isPlayerPlaying && position == currentPlayingIndex)
            return
        }
        applySelectionState(holder, position)
        val isCurrent = position == currentPlayingIndex

        if (payloads.contains(PAYLOAD_MODE)) {
            // Cột số <-> vòng tròn chọn: mờ dần
            val fadeIn = if (isSelectionMode) holder.ivSelectCheckbox else holder.tvIndex
            if (fadeIn.visibility == View.VISIBLE && !(isCurrent && !isSelectionMode)) {
                fadeIn.alpha = 0f
                fadeIn.animate().alpha(1f).setDuration(200).start()
            }
            if (holder.btnMore.visibility == View.VISIBLE) {
                holder.btnMore.alpha = 0f
                holder.btnMore.animate().alpha(1f).setDuration(200).start()
            }
        } else if (payloads.contains(PAYLOAD_TOGGLE) && holder.ivSelectCheckbox.visibility == View.VISIBLE) {
            holder.ivSelectCheckbox.scaleX = 0.7f
            holder.ivSelectCheckbox.scaleY = 0.7f
            holder.ivSelectCheckbox.animate().scaleX(1f).scaleY(1f)
                .setDuration(180).setInterpolator(OvershootInterpolator(2f)).start()
        }
    }

    // Trạng thái hiển thị của cột số / vòng tròn chọn / nút 3 chấm
    private fun applySelectionState(holder: QueueViewHolder, position: Int) {
        val item = mutableItems[position]
        val isCurrent = position == currentPlayingIndex

        holder.tvIndex.animate().cancel()
        holder.ivSelectCheckbox.animate().cancel()
        holder.btnMore.animate().cancel()
        holder.tvIndex.alpha = 1f
        holder.ivSelectCheckbox.alpha = 1f
        holder.btnMore.alpha = 1f
        holder.ivSelectCheckbox.scaleX = 1f
        holder.ivSelectCheckbox.scaleY = 1f

        if (isSelectionMode) {
            holder.tvIndex.visibility = View.INVISIBLE
            holder.btnMore.visibility = View.INVISIBLE
            if (isCurrent) {
                holder.ivSelectCheckbox.visibility = View.INVISIBLE // bài đang phát không chọn được
            } else {
                holder.ivSelectCheckbox.visibility = View.VISIBLE
                if (selectedUris.contains(uriOf(item))) {
                    holder.ivSelectCheckbox.setImageResource(R.drawable.ic_check_circle_filled)
                    holder.ivSelectCheckbox.imageTintList = null
                } else {
                    holder.ivSelectCheckbox.setImageResource(R.drawable.ic_check_circle_outline)
                    holder.ivSelectCheckbox.imageTintList = ColorStateList.valueOf(Color.parseColor("#9E9E9E"))
                }
            }
        } else {
            holder.ivSelectCheckbox.visibility = View.GONE
            // Bài đang phát: ẩn nút ba chấm, chỗ đó dành cho sóng nhạc
            holder.btnMore.visibility = if (isCurrent) View.INVISIBLE else View.VISIBLE
            holder.tvIndex.visibility = if (isCurrent) View.INVISIBLE else View.VISIBLE
        }
    }

    override fun getItemCount(): Int = mutableItems.size

    private fun uriOf(item: MediaItem): String = item.localConfiguration?.uri.toString()

    // ====== Chọn nhiều ======
    private fun positionOf(uri: String) = mutableItems.indexOfFirst { uriOf(it) == uri }

    /** Bấm 1 bài khi đang chọn: tick / bỏ tick (KHÔNG tự thoát chế độ chọn) */
    fun toggleSelection(uri: String) {
        if (!selectedUris.remove(uri)) selectedUris.add(uri)
        val pos = positionOf(uri)
        if (pos != -1) notifyItemChanged(pos, PAYLOAD_TOGGLE)
        onSelectionChanged()
    }

    /** Giữ lâu: vào chế độ chọn và tick bài đó */
    private fun startSelection(uri: String) {
        isSelectionMode = true
        selectedUris.add(uri)
        notifyItemRangeChanged(0, mutableItems.size, PAYLOAD_MODE)
        onSelectionChanged()
    }

    /** Số bài có thể chọn (trừ bài đang phát) */
    fun selectableCount(): Int =
        if (currentPlayingIndex in mutableItems.indices) mutableItems.size - 1 else mutableItems.size

    fun isAllSelected(): Boolean = selectableCount() > 0 && selectedUris.size >= selectableCount()

    fun selectAll() {
        mutableItems.forEachIndexed { i, item ->
            if (i != currentPlayingIndex) selectedUris.add(uriOf(item))
        }
        notifyItemRangeChanged(0, mutableItems.size, PAYLOAD_TOGGLE)
        onSelectionChanged()
    }

    /** Bỏ tick hết nhưng VẪN Ở chế độ chọn nhiều */
    fun clearSelection() {
        selectedUris.clear()
        notifyItemRangeChanged(0, mutableItems.size, PAYLOAD_TOGGLE)
        onSelectionChanged()
    }

    /** Thoát hẳn chế độ chọn nhiều */
    fun exitSelection() {
        selectedUris.clear()
        isSelectionMode = false
        notifyItemRangeChanged(0, mutableItems.size, PAYLOAD_MODE)
        onSelectionChanged()
    }

    // ====== Dữ liệu ======
    fun updateItems(newItems: List<MediaItem>, playingIndex: Int) {
        val old = mutableItems.toList()
        val diff = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize() = old.size
            override fun getNewListSize() = newItems.size
            override fun areItemsTheSame(o: Int, n: Int) = uriOf(old[o]) == uriOf(newItems[n])
            override fun areContentsTheSame(o: Int, n: Int): Boolean {
                val a = old[o].mediaMetadata
                val b = newItems[n].mediaMetadata
                return a.title == b.title && a.artist == b.artist && a.durationMs == b.durationMs
            }
        })

        mutableItems.clear()
        mutableItems.addAll(newItems)
        currentPlayingIndex = playingIndex
        diff.dispatchUpdatesTo(this)
        // Vẽ lại số thứ tự / highlight bài đang phát mà không nháy
        notifyItemRangeChanged(0, mutableItems.size, PAYLOAD_REBIND)

        // Bỏ các bài đã chọn nhưng không còn trong hàng đợi
        val existing = mutableItems.map { uriOf(it) }.toSet()
        if (selectedUris.retainAll(existing)) onSelectionChanged()
    }

    fun onItemMove(from: Int, to: Int) {
        Collections.swap(mutableItems, from, to)
        // Cập nhật vị trí bài đang phát theo thao tác kéo
        currentPlayingIndex = when (currentPlayingIndex) {
            from -> to
            to -> from
            else -> currentPlayingIndex
        }
        notifyItemMoved(from, to)
    }

    private fun formatTime(millis: Long): String {
        return String.format(
            "%02d:%02d",
            TimeUnit.MILLISECONDS.toMinutes(millis),
            TimeUnit.MILLISECONDS.toSeconds(millis) % 60
        )
    }
}
