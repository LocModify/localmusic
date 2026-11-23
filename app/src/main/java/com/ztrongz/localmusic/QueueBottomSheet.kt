package com.ztrongz.localmusic

import android.content.ComponentName
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.MotionEvent
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
    private lateinit var btnShuffleHolder: View
    private lateinit var btnRepeatHolder: View
    private lateinit var tvShuffleLabel: TextView
    private lateinit var tvRepeatLabel: TextView
    private lateinit var tvPlaylistSubtitle: TextView

    private lateinit var layoutNormalHeader: View
    private lateinit var layoutSelectionHeader: View
    private lateinit var tvSelectedCount: TextView
    private lateinit var ivSelectAllIcon: ImageView
    private lateinit var btnSelectAll: View
    private lateinit var bottomBar: View

    private var playerListener: Player.Listener? = null
    private var isDragging = false
    private var lastSelectionMode = false

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
        btnShuffleHolder = view.findViewById(R.id.btnShuffleHolder)
        btnRepeatHolder = view.findViewById(R.id.btnRepeatHolder)
        tvShuffleLabel = view.findViewById(R.id.tvShuffleLabel)
        tvRepeatLabel = view.findViewById(R.id.tvRepeatLabel)
        tvPlaylistSubtitle = view.findViewById(R.id.tvPlaylistSubtitle)
        
        layoutNormalHeader = view.findViewById(R.id.layoutNormalHeader)
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

        // === Làm sheet phủ TOÀN MÀN HÌNH ===
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
        d.window?.setDimAmount(0.4f)

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

        adapter.setPlayerPlaying(c.isPlaying)
        rvQueue.layoutManager = LinearLayoutManager(requireContext())
        rvQueue.adapter = adapter
        itemTouchHelper.attachToRecyclerView(rvQueue)
    }

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
            controller?.moveMediaItem(from, to) 
            return true
        }

        override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {}

        override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
            super.clearView(recyclerView, viewHolder)
            isDragging = false
            updateUI(scrollToCurrent = false) 
        }
    })

    private fun setupSelectionHeader() {
        btnSelectAll.setOnClickListener {
            if (adapter.isAllSelected()) adapter.clearSelection() else adapter.selectAll()
        }

        rootView.findViewById<View>(R.id.btnExitSelection).setOnClickListener {
            adapter.exitSelection()
        }

        rootView.findViewById<View>(R.id.btnDeleteSelected).setOnClickListener {
            val c = controller ?: return@setOnClickListener
            val selected = adapter.selectedUris.toSet()
            if (selected.isEmpty()) return@setOnClickListener

            var removed = 0
            for (i in c.mediaItemCount - 1 downTo 0) {
                if (i == c.currentMediaItemIndex) continue
                val uri = c.getMediaItemAt(i).localConfiguration?.uri.toString()
                if (selected.contains(uri)) {
                    c.removeMediaItem(i)
                    removed++
                }
            }
            adapter.exitSelection()
            updateUI(scrollToCurrent = false)
            Toast.makeText(requireContext(), "Đã xóa $removed bài hát", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateSelectionUI() {
        if (!::adapter.isInitialized) return

        if (adapter.isSelectionMode != lastSelectionMode) {
            lastSelectionMode = adapter.isSelectionMode
            TransitionManager.beginDelayedTransition(
                rootView as ViewGroup,
                AutoTransition().apply { duration = 200 }
            )
        }

        if (adapter.isSelectionMode) {
            layoutNormalHeader.visibility = View.GONE
            layoutSelectionHeader.visibility = View.VISIBLE
            
            tvSelectedCount.text = "Đã chọn ${adapter.selectedUris.size}"
            val color = if (adapter.isAllSelected()) "#7C3AED" else "#7A5BA8"
            ivSelectAllIcon.setImageResource(
                if (adapter.isAllSelected()) R.drawable.ic_check_circle_filled else R.drawable.ic_check_circle_outline
            )
            ivSelectAllIcon.imageTintList =
                if (adapter.isAllSelected()) null else ColorStateList.valueOf(Color.parseColor(color))
        } else {
            layoutNormalHeader.visibility = View.VISIBLE
            layoutSelectionHeader.visibility = View.GONE
        }
    }

    // Hiệu ứng nhấn: nút thu nhỏ nhẹ rồi bật lại (có nảy)
    private fun addPressEffect(v: View) {
        v.setOnTouchListener { view, ev ->
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN ->
                    view.animate().scaleX(0.94f).scaleY(0.94f).setDuration(90).start()
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                    view.animate().scaleX(1f).scaleY(1f).setDuration(220)
                        .setInterpolator(android.view.animation.OvershootInterpolator(3f)).start()
            }
            false // vẫn cho phép onClick chạy
        }
    }

    private fun setupBottomBar() {
        val c = controller ?: return

        // Bấm cả vùng (icon + chữ) đều được
        btnShuffleHolder.setOnClickListener {
            c.shuffleModeEnabled = !c.shuffleModeEnabled
            updateHeaderUI()
        }

        btnRepeatHolder.setOnClickListener {
            c.repeatMode = when (c.repeatMode) {
                Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                else -> Player.REPEAT_MODE_OFF
            }
            updateHeaderUI()
        }

        // Xóa hàng đợi: chỉ xóa các bài PHÍA SAU bài đang phát
        val btnClear = rootView.findViewById<View>(R.id.btnClearQueue)
        btnClear.setOnClickListener {
            val currentIndex = c.currentMediaItemIndex
            if (currentIndex + 1 < c.mediaItemCount) {
                adapter.exitSelection()
                c.removeMediaItems(currentIndex + 1, c.mediaItemCount)
                updateUI(scrollToCurrent = false)
                Toast.makeText(requireContext(), "Đã xóa toàn bộ hàng đợi", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(requireContext(), "Hàng đợi đang trống", Toast.LENGTH_SHORT).show()
            }
        }

        // Bóng phát sáng màu tím quanh nút Xóa (Android 9+)
        if (android.os.Build.VERSION.SDK_INT >= 28) {
            btnClear.outlineAmbientShadowColor = Color.parseColor("#7C3AED")
            btnClear.outlineSpotShadowColor = Color.parseColor("#7C3AED")
        }

        addPressEffect(btnShuffleHolder)
        addPressEffect(btnRepeatHolder)
        addPressEffect(btnClear)

        updateHeaderUI()
    }

    private fun updateHeaderUI() {
        val c = controller ?: return
        val gray = Color.parseColor("#9E9E9E")
        val purple = Color.parseColor("#7C3AED")
        val labelOff = Color.parseColor("#7A5BA8")

        // Ngẫu nhiên: bật => icon + chữ tím đậm, nền viên thuốc tím nhạt
        val shuffleOn = c.shuffleModeEnabled
        btnShuffleBottom.imageTintList = ColorStateList.valueOf(if (shuffleOn) purple else gray)
        tvShuffleLabel.setTextColor(if (shuffleOn) purple else labelOff)
        btnShuffleHolder.setBackgroundResource(if (shuffleOn) R.drawable.bg_bar_btn_active else 0)

        val repeatOn = c.repeatMode != Player.REPEAT_MODE_OFF
        btnRepeatBottom.setImageResource(
            if (c.repeatMode == Player.REPEAT_MODE_ONE) R.drawable.ic_repeat_one else R.drawable.ic_repeat
        )
        btnRepeatBottom.imageTintList = ColorStateList.valueOf(if (repeatOn) purple else gray)
        tvRepeatLabel.setTextColor(if (repeatOn) purple else labelOff)
        btnRepeatHolder.setBackgroundResource(if (repeatOn) R.drawable.bg_bar_btn_active else 0)
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
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (::adapter.isInitialized) adapter.setPlayerPlaying(isPlaying)
            }
            override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) = updateHeaderUI()
            override fun onRepeatModeChanged(repeatMode: Int) = updateHeaderUI()
        }
        playerListener = listener
        c.addListener(listener)
    }

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