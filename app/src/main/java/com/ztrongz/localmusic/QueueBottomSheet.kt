package com.ztrongz.localmusic

import android.content.ComponentName
import android.content.res.ColorStateList
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Bundle
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.transition.AutoTransition
import androidx.transition.TransitionManager
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors

class QueueBottomSheet : BottomSheetDialogFragment() {

    private lateinit var controllerFuture: ListenableFuture<MediaController>
    private var controller: MediaController? = null
    private lateinit var adapter: QueueAdapter
    private lateinit var rvQueue: RecyclerView
    private lateinit var rootView: View

    private lateinit var btnShuffleBottom: ImageView
    private lateinit var btnRepeatBottom: ImageView
    private lateinit var tvPlaylistSubtitle: TextView

    private lateinit var layoutSelectionHeader: View
    private lateinit var tvSelectedCount: TextView
    private lateinit var ivSelectAllIcon: ImageView
    private lateinit var btnSelectAll: View
    private lateinit var btnBack: View

    // Thẻ bài đang phát (ghim trên cùng)
    private lateinit var cardNowPlaying: View
    private lateinit var ivNowCover: ImageView
    private lateinit var tvNowTitle: TextView
    private lateinit var tvNowArtist: TextView
    private lateinit var tvNowIndex: TextView
    private lateinit var nowBars: PlayingBarsView
    private var lastNowUri: String? = null

    private var playerListener: Player.Listener? = null
    private var isDragging = false

    // Danh sách bên dưới KHÔNG chứa bài đang phát => lưu vị trí thật trong Player của từng hàng
    private var rowToPlayer: MutableList<Int> = mutableListOf()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.bottom_sheet_queue, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        rootView = view
        rvQueue = view.findViewById(R.id.rvQueue)
        btnShuffleBottom = view.findViewById(R.id.btnShuffleBottom)
        btnRepeatBottom = view.findViewById(R.id.btnRepeatBottom)
        tvPlaylistSubtitle = view.findViewById(R.id.tvPlaylistSubtitle)
        layoutSelectionHeader = view.findViewById(R.id.layoutSelectionHeader)
        tvSelectedCount = view.findViewById(R.id.tvSelectedCount)
        ivSelectAllIcon = view.findViewById(R.id.ivSelectAllIcon)
        btnSelectAll = view.findViewById(R.id.btnSelectAll)
        btnBack = view.findViewById(R.id.btnBack)

        cardNowPlaying = view.findViewById(R.id.cardNowPlaying)
        ivNowCover = view.findViewById(R.id.ivNowCover)
        tvNowTitle = view.findViewById(R.id.tvNowTitle)
        tvNowArtist = view.findViewById(R.id.tvNowArtist)
        tvNowIndex = view.findViewById(R.id.tvNowIndex)
        nowBars = view.findViewById(R.id.nowBars)

        // Chừa chỗ cho thanh trạng thái vì màn hình phủ toàn bộ
        view.setPadding(0, getStatusBarHeight(), 0, 0)

        btnBack.setOnClickListener { dismiss() }
        cardNowPlaying.setOnClickListener { dismiss() } // bấm thẻ đang phát => quay lại màn hình phát nhạc

        val token = SessionToken(requireContext(), ComponentName(requireContext(), PlaybackService::class.java))
        controllerFuture = MediaController.Builder(requireContext(), token).buildAsync()
        controllerFuture.addListener({
            controller = controllerFuture.get()
            setupRecyclerView()
            setupBottomBar()
            setupMoreMenu()
            setupSelectionHeader()
            observePlayer()
            updateUI()
        }, MoreExecutors.directExecutor())
    }

    override fun onStart() {
        super.onStart()
        val d = dialog as? BottomSheetDialog ?: return

        // Làm sheet phủ TOÀN MÀN HÌNH, nền trong suốt (nền tím do layout tự vẽ)
        val sheet = d.findViewById<FrameLayout>(com.google.android.material.R.id.design_bottom_sheet)
        if (sheet != null) {
            val lp = sheet.layoutParams
            lp.height = ViewGroup.LayoutParams.MATCH_PARENT
            sheet.layoutParams = lp
            sheet.setBackgroundColor(Color.TRANSPARENT)
        }
        d.behavior.apply {
            skipCollapsed = true
            isFitToContents = false
            expandedOffset = 0
            peekHeight = resources.displayMetrics.heightPixels
            state = BottomSheetBehavior.STATE_EXPANDED
        }

        // Nhấn Back: đang chọn nhiều thì thoát chế độ chọn trước, chưa đóng
        d.setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP &&
                ::adapter.isInitialized && adapter.isSelectionMode
            ) {
                adapter.exitSelection()
                true
            } else false
        }
    }

    private fun getStatusBarHeight(): Int {
        val id = resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (id > 0) resources.getDimensionPixelSize(id) else 0
    }

    // ================= Danh sách =================
    private fun setupRecyclerView() {
        val c = controller ?: return

        adapter = QueueAdapter(
            onItemClick = { row ->
                val playerIndex = rowToPlayer.getOrNull(row)
                if (playerIndex != null && playerIndex in 0 until c.mediaItemCount) {
                    c.seekTo(playerIndex, 0L)
                    c.play()
                }
            },
            onStartDrag = { vh -> itemTouchHelper.startDrag(vh) },
            onMoreClick = { _, _ ->
                Toast.makeText(requireContext(), "Tính năng đang phát triển", Toast.LENGTH_SHORT).show()
            },
            onSelectionChanged = { updateSelectionUI() }
        )

        rvQueue.layoutManager = LinearLayoutManager(requireContext())
        rvQueue.adapter = adapter
        itemTouchHelper.attachToRecyclerView(rvQueue)
    }

    // Chỉ kéo thả đổi vị trí (chạm vào số thứ tự rồi kéo), KHÔNG vuốt để xóa
    private val itemTouchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
        ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0
    ) {
        override fun isLongPressDragEnabled() = false
        override fun isItemViewSwipeEnabled() = false

        override fun onMove(
            recyclerView: RecyclerView,
            viewHolder: RecyclerView.ViewHolder,
            target: RecyclerView.ViewHolder
        ): Boolean {
            if (adapter.isSelectionMode) return false
            val c = controller ?: return false
            val from = viewHolder.bindingAdapterPosition
            val to = target.bindingAdapterPosition
            if (from == RecyclerView.NO_POSITION || to == RecyclerView.NO_POSITION) return false
            val pFrom = rowToPlayer.getOrNull(from) ?: return false
            val pTo = rowToPlayer.getOrNull(to) ?: return false

            isDragging = true
            adapter.onItemMove(from, to)
            c.moveMediaItem(pFrom, pTo)          // đổi vị trí ngay trong Player
            rowToPlayer = computeRowMap(c)        // tính lại bảng ánh xạ hàng -> vị trí Player
            return true
        }

        override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {}

        override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
            super.clearView(recyclerView, viewHolder)
            isDragging = false
            updateUI()
        }
    })

    // Vị trí (trong Player) của mọi bài, trừ bài đang phát
    private fun computeRowMap(c: MediaController): MutableList<Int> {
        val cur = c.currentMediaItemIndex
        return (0 until c.mediaItemCount).filter { it != cur }.toMutableList()
    }

    // ================= Chế độ chọn nhiều =================
    private fun setupSelectionHeader() {
        // "Tất cả": chọn tất cả / bỏ tick hết (vẫn ở chế độ chọn nhiều)
        btnSelectAll.setOnClickListener {
            if (adapter.isAllSelected()) adapter.clearSelection() else adapter.selectAll()
        }

        // Nút X: thoát chế độ chọn nhiều
        rootView.findViewById<View>(R.id.btnExitSelection).setOnClickListener {
            adapter.exitSelection()
        }

        // Thùng rác đỏ: xóa các bài đã chọn khỏi hàng đợi
        rootView.findViewById<View>(R.id.btnDeleteSelected).setOnClickListener {
            val c = controller ?: return@setOnClickListener
            val selected = adapter.selectedUris.toSet()
            if (selected.isEmpty()) return@setOnClickListener

            var removed = 0
            // Xóa từ cuối lên đầu để không lệch chỉ số; bỏ qua bài đang phát
            for (i in c.mediaItemCount - 1 downTo 0) {
                if (i == c.currentMediaItemIndex) continue
                val uri = c.getMediaItemAt(i).localConfiguration?.uri.toString()
                if (selected.contains(uri)) {
                    c.removeMediaItem(i)
                    removed++
                }
            }
            adapter.exitSelection()
            updateUI()
            Toast.makeText(requireContext(), "Đã xóa $removed bài hát", Toast.LENGTH_SHORT).show()
        }
    }

    private var lastSelectionMode = false

    private fun updateSelectionUI() {
        if (!::adapter.isInitialized) return

        // Hiệu ứng trượt mượt khi thanh chọn hiện/ẩn (chỉ khi đổi chế độ)
        if (adapter.isSelectionMode != lastSelectionMode) {
            lastSelectionMode = adapter.isSelectionMode
            TransitionManager.beginDelayedTransition(
                rootView as ViewGroup,
                AutoTransition().apply { duration = 200 }
            )
        }

        if (adapter.isSelectionMode) {
            layoutSelectionHeader.visibility = View.VISIBLE
            btnBack.visibility = View.GONE
            tvSelectedCount.text = "Đã chọn ${adapter.selectedUris.size}"
            val all = adapter.isAllSelected()
            ivSelectAllIcon.setImageResource(
                if (all) R.drawable.ic_check_circle_filled else R.drawable.ic_check_circle_outline
            )
            ivSelectAllIcon.imageTintList =
                if (all) null else ColorStateList.valueOf(Color.parseColor("#7A5BA8"))
        } else {
            layoutSelectionHeader.visibility = View.GONE
            btnBack.visibility = View.VISIBLE
        }
    }

    // ================= Thanh dưới + menu ba chấm =================
    private fun setupBottomBar() {
        val c = controller ?: return

        // Bấm cả vùng (icon + chữ) đều được
        rootView.findViewById<View>(R.id.btnShuffleHolder).setOnClickListener {
            c.shuffleModeEnabled = !c.shuffleModeEnabled
            updateHeaderUI()
        }

        rootView.findViewById<View>(R.id.btnRepeatHolder).setOnClickListener {
            c.repeatMode = when (c.repeatMode) {
                Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                else -> Player.REPEAT_MODE_OFF
            }
            updateHeaderUI()
        }

        updateHeaderUI()
    }

    // Nút ba chấm trên tiêu đề: menu "Xóa hàng đợi" (xóa các bài phía sau bài đang phát)
    private fun setupMoreMenu() {
        val c = controller ?: return
        val btn = rootView.findViewById<View>(R.id.btnMoreOptions)
        btn.setOnClickListener {
            val popup = PopupMenu(requireContext(), btn)
            popup.menu.add(0, 1, 0, "Xóa hàng đợi")
            popup.setOnMenuItemClickListener {
                val currentIndex = c.currentMediaItemIndex
                if (currentIndex + 1 < c.mediaItemCount) {
                    adapter.exitSelection()
                    c.removeMediaItems(currentIndex + 1, c.mediaItemCount)
                    updateUI()
                    Toast.makeText(requireContext(), "Đã xóa toàn bộ hàng đợi", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(requireContext(), "Hàng đợi đang trống", Toast.LENGTH_SHORT).show()
                }
                true
            }
            popup.show()
        }
    }

    private fun updateHeaderUI() {
        val c = controller ?: return
        val gray = Color.parseColor("#9E9E9E")
        val purple = Color.parseColor("#7C3AED")

        btnShuffleBottom.imageTintList = ColorStateList.valueOf(if (c.shuffleModeEnabled) purple else gray)

        when (c.repeatMode) {
            Player.REPEAT_MODE_OFF -> {
                btnRepeatBottom.setImageResource(R.drawable.ic_repeat)
                btnRepeatBottom.imageTintList = ColorStateList.valueOf(gray)
            }
            Player.REPEAT_MODE_ALL -> {
                btnRepeatBottom.setImageResource(R.drawable.ic_repeat)
                btnRepeatBottom.imageTintList = ColorStateList.valueOf(purple)
            }
            else -> {
                btnRepeatBottom.setImageResource(R.drawable.ic_repeat_one)
                btnRepeatBottom.imageTintList = ColorStateList.valueOf(purple)
            }
        }
    }

    private fun observePlayer() {
        val c = controller ?: return
        val listener = object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) = updateUI()
            override fun onTimelineChanged(timeline: Timeline, reason: Int) = updateUI()
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                nowBars.setPlaying(isPlaying)
            }
            override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) = updateHeaderUI()
            override fun onRepeatModeChanged(repeatMode: Int) = updateHeaderUI()
        }
        playerListener = listener
        c.addListener(listener)
    }

    // ================= Cập nhật giao diện =================
    private fun updateUI() {
        val c = controller ?: return
        if (!::adapter.isInitialized || isDragging) return

        val count = c.mediaItemCount
        val cur = if (count > 0) c.currentMediaItemIndex else -1

        // Tiêu đề phụ: số bài + tổng thời lượng
        var totalMs = 0L
        for (i in 0 until count) totalMs += c.getMediaItemAt(i).mediaMetadata.durationMs ?: 0L
        tvPlaylistSubtitle.text = "$count bài hát • ${formatTotalDuration(totalMs)}"

        // Thẻ bài đang phát
        updateNowCard(c, cur)

        // Danh sách: mọi bài TRỪ bài đang phát (thứ tự đúng như trong Player)
        rowToPlayer = computeRowMap(c)
        val rows = rowToPlayer.map { c.getMediaItemAt(it) }
        adapter.updateItems(rows, -1)
    }

    private fun updateNowCard(c: MediaController, cur: Int) {
        if (cur < 0) {
            cardNowPlaying.visibility = View.GONE
            return
        }
        cardNowPlaying.visibility = View.VISIBLE
        val item = c.getMediaItemAt(cur)
        val md = item.mediaMetadata
        tvNowTitle.text = md.title ?: "Unknown Title"
        tvNowArtist.text = md.artist ?: "Unknown Artist"
        tvNowIndex.text = (cur + 1).toString()
        nowBars.setPlaying(c.isPlaying)

        // Chỉ giải mã ảnh bìa khi đổi bài
        val uri = item.localConfiguration?.uri.toString()
        if (uri != lastNowUri) {
            lastNowUri = uri
            val art = md.artworkData
            if (art != null) {
                ivNowCover.setImageBitmap(BitmapFactory.decodeByteArray(art, 0, art.size))
            } else {
                ivNowCover.setImageResource(R.drawable.bg_cover_default)
            }
        }
    }

    // "1h 00:30" nếu từ 1 giờ trở lên, ngược lại "MM:SS"
    private fun formatTotalDuration(ms: Long): String {
        val totalSec = ms / 1000
        val h = totalSec / 3600
        val m = (totalSec % 3600) / 60
        val s = totalSec % 60
        return if (h > 0) String.format("%dh %02d:%02d", h, m, s) else String.format("%02d:%02d", m, s)
    }

    override fun onDestroyView() {
        playerListener?.let { controller?.removeListener(it) }
        playerListener = null
        if (::controllerFuture.isInitialized) {
            MediaController.releaseFuture(controllerFuture)
        }
        controller = null
        super.onDestroyView()
    }
}
