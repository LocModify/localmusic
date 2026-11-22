package com.ztrongz.localmusic

import android.content.ComponentName
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
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
    private lateinit var bottomBar: View

    private var playerListener: Player.Listener? = null
    private var isDragging = false

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
        bottomBar = view.findViewById(R.id.bottomBar)

        // Chừa chỗ cho thanh trạng thái vì sheet phủ toàn màn hình
        view.setPadding(0, getStatusBarHeight(), 0, 0)

        val token = SessionToken(requireContext(), ComponentName(requireContext(), PlaybackService::class.java))
        controllerFuture = MediaController.Builder(requireContext(), token).buildAsync()
        controllerFuture.addListener({
            controller = controllerFuture.get()
            setupRecyclerView()
            setupBottomBar()
            setupSelectionHeader()
            observePlayer()
            updateUI(scrollToCurrent = true)
        }, MoreExecutors.directExecutor())
    }

    override fun onStart() {
        super.onStart()
        val d = dialog as? BottomSheetDialog ?: return

        // === Làm sheet phủ TOÀN MÀN HÌNH (sửa lỗi sheet chỉ cao một đoạn) ===
        val sheet = d.findViewById<FrameLayout>(com.google.android.material.R.id.design_bottom_sheet)
        if (sheet != null) {
            val lp = sheet.layoutParams
            lp.height = ViewGroup.LayoutParams.MATCH_PARENT
            sheet.layoutParams = lp
            sheet.setBackgroundColor(Color.TRANSPARENT) // nền bo góc do layout tự vẽ
        }
        d.behavior.apply {
            skipCollapsed = true
            isFitToContents = false
            expandedOffset = 0
            peekHeight = resources.displayMetrics.heightPixels
            state = BottomSheetBehavior.STATE_EXPANDED
        }
        d.window?.setDimAmount(0.4f)

        // Nhấn Back: đang chọn nhiều thì thoát chế độ chọn trước, chưa đóng sheet
        d.setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP &&
                ::adapter.isInitialized && adapter.isSelectionMode
            ) {
                adapter.deselectAll()
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
            onItemClick = { index ->
                if (index in 0 until c.mediaItemCount) {
                    c.seekTo(index, 0L)
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

    // Chỉ kéo thả đổi vị trí, KHÔNG còn vuốt để xóa
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
            val from = viewHolder.bindingAdapterPosition
            val to = target.bindingAdapterPosition
            if (from == RecyclerView.NO_POSITION || to == RecyclerView.NO_POSITION) return false
            isDragging = true
            adapter.onItemMove(from, to)
            controller?.moveMediaItem(from, to) // đổi vị trí ngay trong Player
            return true
        }

        override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {}

        override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
            super.clearView(recyclerView, viewHolder)
            isDragging = false
            updateUI(scrollToCurrent = false) // đồng bộ lại số thứ tự
        }
    })

    // ================= Chế độ chọn nhiều =================
    private fun setupSelectionHeader() {
        // "Tất cả": chọn tất cả / bỏ chọn tất cả
        btnSelectAll.setOnClickListener {
            if (adapter.isAllSelected()) adapter.deselectAll() else adapter.selectAll()
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
            adapter.deselectAll()
            updateUI(scrollToCurrent = false)
            Toast.makeText(requireContext(), "Đã xóa $removed bài hát", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateSelectionUI() {
        if (!::adapter.isInitialized) return
        if (adapter.isSelectionMode) {
            layoutSelectionHeader.visibility = View.VISIBLE
            tvSelectedCount.text = "Đã chọn ${adapter.selectedUris.size}"
            val color = if (adapter.isAllSelected()) "#7C3AED" else "#7A5BA8"
            ivSelectAllIcon.setImageResource(
                if (adapter.isAllSelected()) R.drawable.ic_check_circle_filled else R.drawable.ic_check_circle_outline
            )
            ivSelectAllIcon.imageTintList =
                if (adapter.isAllSelected()) null else ColorStateList.valueOf(Color.parseColor(color))
        } else {
            layoutSelectionHeader.visibility = View.GONE
        }
    }

    // ================= Thanh dưới =================
    private fun setupBottomBar() {
        val c = controller ?: return

        btnShuffleBottom.setOnClickListener {
            c.shuffleModeEnabled = !c.shuffleModeEnabled
            updateHeaderUI()
        }

        btnRepeatBottom.setOnClickListener {
            c.repeatMode = when (c.repeatMode) {
                Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                else -> Player.REPEAT_MODE_OFF
            }
            updateHeaderUI()
        }

        // Xóa hàng đợi: chỉ xóa các bài PHÍA SAU bài đang phát
        rootView.findViewById<View>(R.id.btnClearQueue).setOnClickListener {
            val currentIndex = c.currentMediaItemIndex
            if (currentIndex + 1 < c.mediaItemCount) {
                adapter.deselectAll()
                c.removeMediaItems(currentIndex + 1, c.mediaItemCount)
                updateUI(scrollToCurrent = false)
                Toast.makeText(requireContext(), "Đã xóa toàn bộ hàng đợi", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(requireContext(), "Hàng đợi đang trống", Toast.LENGTH_SHORT).show()
            }
        }

        updateHeaderUI()
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
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                updateUI(scrollToCurrent = false)
            }
            override fun onTimelineChanged(timeline: Timeline, reason: Int) {
                updateUI(scrollToCurrent = false)
            }
            override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) = updateHeaderUI()
            override fun onRepeatModeChanged(repeatMode: Int) = updateHeaderUI()
        }
        playerListener = listener
        c.addListener(listener)
    }

    // ================= Cập nhật giao diện =================
    private fun updateUI(scrollToCurrent: Boolean) {
        val c = controller ?: return
        if (!::adapter.isInitialized || isDragging) return

        val all = (0 until c.mediaItemCount).map { c.getMediaItemAt(it) }
        val totalMs = all.sumOf { it.mediaMetadata.durationMs ?: 0L }
        tvPlaylistSubtitle.text = "${all.size} bài hát • ${formatTotalDuration(totalMs)}"

        val playing = if (c.mediaItemCount > 0) c.currentMediaItemIndex else -1
        adapter.updateItems(all, playing)

        if (scrollToCurrent && playing >= 0) {
            (rvQueue.layoutManager as? LinearLayoutManager)?.scrollToPositionWithOffset(playing, 0)
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
