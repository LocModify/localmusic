package com.ztrongz.localmusic

import android.content.ComponentName
import android.content.DialogInterface
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import java.text.Collator
import java.util.Locale
import android.widget.PopupWindow
import android.os.Bundle
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.MotionEvent
import android.graphics.Matrix
import android.graphics.Outline
import android.view.View
import android.view.ViewOutlineProvider
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
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

    companion object {
        // Tỉ lệ chiều cao bảng so với màn hình (1.0 = toàn màn hình, 0.72 = giống ảnh demo)
        private const val SHEET_HEIGHT_RATIO = 1.0f
    }

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

    // Activity đặt vào để biết khi bảng đóng (dùng để bật lại hiệu ứng ở màn hình Now Playing)
    var onDismissed: (() -> Unit)? = null

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        onDismissed?.invoke()
    }

    // Mở màn hình "Thêm bài hát"; khi quay lại thì làm mới danh sách (bài mới đã được thêm vào Player)
    private val pickLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (::adapter.isInitialized) updateUI(scrollToCurrent = false)
    }
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

        // Bo tròn góc trên: KHÔNG cắt cả bảng bằng clipToOutline nữa (rất nặng khi vuốt),
        // mà chỉ bo riêng ảnh nền (ShapeableImageView) và lớp kính mờ phía trên.
        // Toàn màn hình: đẩy thanh kéo (và cả nội dung bên dưới) xuống dưới thanh trạng thái
        val handle = view.findViewById<View>(R.id.dragHandle)
        (handle.layoutParams as ViewGroup.MarginLayoutParams).topMargin += getStatusBarHeight()

        // Ảnh nền phóng vừa chiều rộng, căn SÁT ĐÁY (thấy phần sóng tím ở dưới giống demo)
        val bg = view.findViewById<ImageView>(R.id.bgImage)
        bg.addOnLayoutChangeListener { v, l, t, r, b, _, _, _, _ ->
            val dr = bg.drawable ?: return@addOnLayoutChangeListener
            val w = (r - l).toFloat()
            val h = (b - t).toFloat()
            if (w <= 0f || h <= 0f) return@addOnLayoutChangeListener
            val s = maxOf(w / dr.intrinsicWidth, h / dr.intrinsicHeight)
            val m = Matrix()
            m.setScale(s, s)
            m.postTranslate((w - dr.intrinsicWidth * s) / 2f, h - dr.intrinsicHeight * s)
            bg.imageMatrix = m
        }
        rvQueue = view.findViewById(R.id.rvQueue)
        rvQueue.setHasFixedSize(true)
        rvQueue.setItemViewCacheSize(10)
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

        // Nút +♪: mở màn hình chọn nhiều bài từ thư viện để thêm vào danh sách phát
        val btnAddSong = view.findViewById<View>(R.id.btnAddSong)
        addPressEffect(btnAddSong)
        btnAddSong.setOnClickListener { openPicker() }

        // Nút "...": menu tùy chọn cho cả hàng đợi
        val btnMore = view.findViewById<View>(R.id.btnMoreOptions)
        addPressEffect(btnMore)
        btnMore.setOnClickListener { showQueueOptions(it) }

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

        // Bảng cao bằng SHEET_HEIGHT_RATIO chiều cao màn hình (1.0 = phủ toàn màn hình)
        val screenH = resources.displayMetrics.heightPixels
        val sheetH = (screenH * SHEET_HEIGHT_RATIO).toInt()
        val sheet = d.findViewById<FrameLayout>(com.google.android.material.R.id.design_bottom_sheet)
        if (sheet != null) {
            val lp = sheet.layoutParams
            lp.height = sheetH
            sheet.layoutParams = lp
            sheet.setBackgroundColor(Color.TRANSPARENT)
        }
        d.behavior.apply {
            isFitToContents = true
            skipCollapsed = true
            peekHeight = sheetH
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

    // ===== Menu tùy chọn hàng đợi =====
    private fun showQueueOptions(anchor: View) {
        val popupView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_queue_options, null)
        val popup = PopupWindow(
            popupView,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true
        )
        popup.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        popup.elevation = 15f

        popupView.findViewById<View>(R.id.btnQoSort).setOnClickListener {
            popup.dismiss()
            showSortDialog()
        }
        popupView.findViewById<View>(R.id.btnQoDelete).setOnClickListener {
            popup.dismiss()
            showDeletePanel()
        }

        // Căn mép phải menu với mép phải nút, hiện ngay bên dưới nút
        popupView.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
        val offsetX = anchor.width - popupView.measuredWidth
        popup.showAsDropDown(anchor, offsetX, 0)
    }

    // ===== Bảng dùng chung: thẻ trắng bo góc, nhỏ gọn, hiện ngay cạnh nút "..." =====
    private class PanelRow(
        val text: String,
        val color: Int = Color.parseColor("#1F1B2E"),
        val iconRes: Int = 0,   // icon đầu dòng (0 = không có)
        val onClick: () -> Unit
    )

    private fun showPanel(
        title: String,
        rows: List<PanelRow>,
        message: String? = null,
        okText: String? = null,
        onOk: (() -> Unit)? = null
    ) {
        val v = layoutInflater.inflate(R.layout.dialog_queue_panel, null)
        val popup = PopupWindow(
            v,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true
        )
        popup.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        popup.elevation = 15f

        v.findViewById<TextView>(R.id.tvPanelTitle).apply {
            if (title.isBlank()) visibility = View.GONE else text = title   // không có tiêu đề thì ẩn luôn
        }
        if (message != null) {
            v.findViewById<TextView>(R.id.tvPanelMessage).apply { text = message; visibility = View.VISIBLE }
        }

        val container = v.findViewById<ViewGroup>(R.id.panelRows)
        for (row in rows) {
            val tv = layoutInflater.inflate(R.layout.item_queue_menu_row, container, false) as TextView
            tv.text = row.text
            tv.setTextColor(row.color)
            if (row.iconRes != 0) tv.setCompoundDrawablesRelativeWithIntrinsicBounds(row.iconRes, 0, 0, 0)
            tv.setOnClickListener { popup.dismiss(); row.onClick() }
            container.addView(tv)
        }

        if (okText != null && onOk != null) {
            v.findViewById<View>(R.id.panelButtons).visibility = View.VISIBLE
            v.findViewById<View>(R.id.btnPanelCancel).setOnClickListener { popup.dismiss() }
            v.findViewById<TextView>(R.id.btnPanelOk).apply {
                text = okText
                setOnClickListener { popup.dismiss(); onOk() }
            }
        }

        // Hiện ngay dưới nút "...", căn mép phải với nút (giống menu ba chấm)
        val anchor = rootView.findViewById<View>(R.id.btnMoreOptions)
        v.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
        popup.showAsDropDown(anchor, anchor.width - v.measuredWidth, 0)
    }

    // Bảng chọn kiểu sắp xếp
    private fun showSortDialog() {
        showPanel(
            title = "Sắp xếp danh sách",
            rows = listOf(
                PanelRow("Tên bài hát (A → Z)", iconRes = R.drawable.ic_qs_alpha) { sortQueue(0) },
                PanelRow("Ca sĩ (A → Z)", iconRes = R.drawable.ic_qs_person) { sortQueue(1) },
                PanelRow("Thời lượng (ngắn → dài)", iconRes = R.drawable.ic_qs_time_up) { sortQueue(2) },
                PanelRow("Thời lượng (dài → ngắn)", iconRes = R.drawable.ic_qs_time_down) { sortQueue(3) }
            )
        )
    }

    // Menu dấu 3 chấm cạnh từng bài trong hàng đợi: chia sẻ file nhạc
    private fun showRowMenu(index: Int, anchor: View) {
        val c = controller ?: return
        if (index !in 0 until c.mediaItemCount) return
        val item = c.getMediaItemAt(index)
        val title = item.mediaMetadata.title?.toString()?.takeIf { it.isNotBlank() } ?: "Bài hát"

        val popupView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_queue_row_menu, null)
        val popup = PopupWindow(
            popupView,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true
        )
        popup.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        popup.elevation = 15f

        popupView.findViewById<View>(R.id.btnRowShare).setOnClickListener {
            popup.dismiss()
            ShareHelper.shareSong(requireContext(), item.localConfiguration?.uri, title)
        }

        popupView.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
        popup.showAsDropDown(anchor, anchor.width - popupView.measuredWidth, 0)
    }

    // Bảng "Xóa": xóa bài này / xóa nhiều / xóa tất cả
    private fun showDeletePanel() {
        val red = Color.parseColor("#FF0000")
        showPanel(
            title = "",   // không cần tiêu đề
            rows = listOf(
                PanelRow("Xóa bài này", red, R.drawable.ic_qd_one) { deleteCurrentSong() },
                PanelRow("Xóa nhiều", red, R.drawable.ic_qd_multi) { if (::adapter.isInitialized) adapter.enterSelectionMode() },
                PanelRow("Xóa tất cả", red, R.drawable.ic_qd_all) { confirmClearAll() }
            )
        )
    }

    // Xóa bài đang phát khỏi hàng đợi (nhạc tự chuyển sang bài kế tiếp)
    private fun deleteCurrentSong() {
        val c = controller ?: return
        if (c.mediaItemCount <= 1) {
            Toast.makeText(requireContext(), "Hàng đợi chỉ còn 1 bài, không thể xóa", Toast.LENGTH_SHORT).show()
            return
        }
        c.removeMediaItem(c.currentMediaItemIndex)
        updateUI(scrollToCurrent = false)
        Toast.makeText(requireContext(), "Đã xóa bài đang phát", Toast.LENGTH_SHORT).show()
    }

    // Xóa tất cả: giữ lại duy nhất bài đang phát (nhạc không bị ngắt)
    private fun confirmClearAll() {
        val c = controller ?: return
        if (c.mediaItemCount <= 1) {
            Toast.makeText(requireContext(), "Hàng đợi không còn bài nào để xóa", Toast.LENGTH_SHORT).show()
            return
        }
        showPanel(
            title = "Xóa tất cả?",
            rows = emptyList(),
            message = "Tất cả bài hát sẽ bị xóa khỏi danh sách phát, trừ bài đang phát.",
            okText = "Xóa",
            onOk = {
                val cur = c.currentMediaItemIndex
                if (cur + 1 < c.mediaItemCount) c.removeMediaItems(cur + 1, c.mediaItemCount)
                if (cur > 0) c.removeMediaItems(0, cur)
                updateUI(scrollToCurrent = false)
            }
        )
    }

    // Sắp xếp bằng cách di chuyển từng bài (moveMediaItem) => bài đang phát KHÔNG bị ngắt
    private fun sortQueue(mode: Int) {
        val c = controller ?: return
        if (c.mediaItemCount < 2) return
        val collator = Collator.getInstance(Locale("vi"))
        val mirror = (0 until c.mediaItemCount).map { c.getMediaItemAt(it) }.toMutableList()

        val sorted = when (mode) {
            0 -> mirror.sortedWith { a, b ->
                collator.compare(a.mediaMetadata.title?.toString() ?: "", b.mediaMetadata.title?.toString() ?: "")
            }
            1 -> mirror.sortedWith { a, b ->
                collator.compare(a.mediaMetadata.artist?.toString() ?: "", b.mediaMetadata.artist?.toString() ?: "")
            }
            2 -> mirror.sortedBy { it.mediaMetadata.durationMs ?: 0L }
            else -> mirror.sortedByDescending { it.mediaMetadata.durationMs ?: 0L }
        }

        for (i in sorted.indices) {
            val j = mirror.indexOfFirst { it === sorted[i] }
            if (j != i) {
                c.moveMediaItem(j, i)
                mirror.add(i, mirror.removeAt(j))
            }
        }
        updateUI(scrollToCurrent = true)
        Toast.makeText(requireContext(), "Đã sắp xếp danh sách", Toast.LENGTH_SHORT).show()
    }

    // Gửi danh sách uri đang có trong hàng đợi sang để màn hình chọn đánh dấu "Đã có"
    private fun openPicker() {
        val c = controller
        val queueUris = ArrayList<String>()
        if (c != null) {
            for (i in 0 until c.mediaItemCount) {
                c.getMediaItemAt(i).localConfiguration?.uri?.toString()?.let { queueUris.add(it) }
            }
        }
        val intent = Intent(requireContext(), PickSongsActivity::class.java)
            .putStringArrayListExtra(PickSongsActivity.EXTRA_QUEUE_URIS, queueUris)
        pickLauncher.launch(intent)
        requireActivity().overridePendingTransition(R.anim.slide_up, 0)
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
            onMoreClick = { index, view -> showRowMenu(index, view) },
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

        addPressEffect(btnShuffleHolder)
        addPressEffect(btnRepeatHolder)

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