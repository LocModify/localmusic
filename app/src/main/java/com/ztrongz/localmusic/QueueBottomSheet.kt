package com.ztrongz.localmusic

import android.content.ComponentName
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
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

    private lateinit var btnShuffleBottom: ImageView
    private lateinit var btnRepeatBottom: ImageView
    private lateinit var tvPlaylistSubtitle: TextView

    private var playerListener: Player.Listener? = null

    // Vị trí bắt đầu / kết thúc của lần kéo thả hiện tại (-1 = không kéo)
    private var dragFrom = -1
    private var dragTo = -1

    private companion object {
        // Chiều cao bảng = 70% chiều cao màn hình (chỉnh số này để cao/thấp hơn)
        const val SHEET_HEIGHT_RATIO = 0.7f
        const val COLOR_ON = "#7C3AED"
        const val COLOR_OFF = "#A892D8"
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.bottom_sheet_queue, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        rvQueue = view.findViewById(R.id.rvQueue)
        btnShuffleBottom = view.findViewById(R.id.btnShuffleBottom)
        btnRepeatBottom = view.findViewById(R.id.btnRepeatBottom)
        tvPlaylistSubtitle = view.findViewById(R.id.tvPlaylistSubtitle)

        val token = SessionToken(requireContext(), ComponentName(requireContext(), PlaybackService::class.java))
        controllerFuture = MediaController.Builder(requireContext(), token).buildAsync()
        controllerFuture.addListener({
            if (!isAdded || view.parent == null) return@addListener
            controller = controllerFuture.get()
            setupRecyclerView()
            setupBottomBar()
            observePlayer()
            updateUI()
        }, MoreExecutors.directExecutor())
    }

    override fun onStart() {
        super.onStart()
        val bsDialog = dialog as? BottomSheetDialog ?: return

        // Đặt cứng chiều cao bảng (trước đây bảng co lại bằng phần nội dung nên chỉ còn 1 dải nhỏ)
        val height = (resources.displayMetrics.heightPixels * SHEET_HEIGHT_RATIO).toInt()
        val sheet = bsDialog.findViewById<FrameLayout>(com.google.android.material.R.id.design_bottom_sheet)
        if (sheet != null) {
            sheet.layoutParams.height = height
            // Bỏ nền mặc định của Material để thấy góc bo và nền kính mờ của mình
            sheet.setBackgroundColor(Color.TRANSPARENT)
            sheet.requestLayout()
        }

        bsDialog.behavior.apply {
            peekHeight = height
            skipCollapsed = true
            state = BottomSheetBehavior.STATE_EXPANDED
        }
        // Làm tối nhẹ màn hình Now Playing phía sau
        bsDialog.window?.setDimAmount(0.35f)
    }

    private fun setupRecyclerView() {
        adapter = QueueAdapter(
            items = emptyList(),
            onItemClick = { position ->
                val c = controller
                // Vị trí 0 là bài đang phát nên không cần chuyển bài
                if (c != null && position > 0) {
                    val actualIndex = c.currentMediaItemIndex + position
                    if (actualIndex < c.mediaItemCount) {
                        c.seekTo(actualIndex, 0L)
                        c.play()
                    }
                }
            },
            onStartDrag = { viewHolder ->
                itemTouchHelper.startDrag(viewHolder)
            },
            onMoreClick = { _, _ ->
                Toast.makeText(requireContext(), "Tính năng đang phát triển", Toast.LENGTH_SHORT).show()
            }
        )

        rvQueue.layoutManager = LinearLayoutManager(requireContext())
        rvQueue.adapter = adapter
        itemTouchHelper.attachToRecyclerView(rvQueue)
    }

    private val itemTouchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
        ItemTouchHelper.UP or ItemTouchHelper.DOWN,
        ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT
    ) {
        // Bài đang phát (vị trí 0) không cho kéo hay vuốt xóa
        override fun getMovementFlags(
            recyclerView: RecyclerView,
            viewHolder: RecyclerView.ViewHolder
        ): Int {
            if (viewHolder.bindingAdapterPosition <= 0) return 0
            return super.getMovementFlags(recyclerView, viewHolder)
        }

        // Kéo thả bắt đầu bằng nhấn giữ (đã gắn trong adapter)
        override fun isLongPressDragEnabled(): Boolean = false

        override fun onMove(
            recyclerView: RecyclerView,
            viewHolder: RecyclerView.ViewHolder,
            target: RecyclerView.ViewHolder
        ): Boolean {
            val from = viewHolder.bindingAdapterPosition
            val to = target.bindingAdapterPosition
            if (from == RecyclerView.NO_POSITION || to == RecyclerView.NO_POSITION || to == 0) return false
            if (dragFrom == -1) dragFrom = from
            dragTo = to
            adapter.onItemMove(from, to)
            return true
        }

        override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
            val position = viewHolder.bindingAdapterPosition
            val c = controller
            if (c == null || position <= 0) return
            val actualIndex = c.currentMediaItemIndex + position
            if (actualIndex < c.mediaItemCount) {
                adapter.removeItem(position)
                c.removeMediaItem(actualIndex)
                Toast.makeText(requireContext(), "Đã xóa bài hát", Toast.LENGTH_SHORT).show()
            }
        }

        // Thả tay: đồng bộ thứ tự mới sang trình phát (di chuyển 1 bài, không làm ngắt nhạc)
        override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
            super.clearView(recyclerView, viewHolder)
            val c = controller
            if (c != null && dragFrom != -1 && dragTo != -1 && dragFrom != dragTo) {
                val base = c.currentMediaItemIndex
                c.moveMediaItem(base + dragFrom, base + dragTo)
            }
            dragFrom = -1
            dragTo = -1
        }
    })

    private fun setupBottomBar() {
        val c = controller ?: return
        val root = view ?: return

        root.findViewById<View>(R.id.groupShuffle).setOnClickListener {
            c.shuffleModeEnabled = !c.shuffleModeEnabled
            updateHeaderUI()
        }

        root.findViewById<View>(R.id.groupRepeat).setOnClickListener {
            c.repeatMode = when (c.repeatMode) {
                Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                else -> Player.REPEAT_MODE_OFF
            }
            updateHeaderUI()
        }

        root.findViewById<View>(R.id.btnClearQueue).setOnClickListener {
            val currentIndex = c.currentMediaItemIndex
            if (currentIndex + 1 < c.mediaItemCount) {
                c.removeMediaItems(currentIndex + 1, c.mediaItemCount)
                updateUI()
                Toast.makeText(requireContext(), "Đã xóa toàn bộ hàng đợi", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(requireContext(), "Hàng đợi đang trống", Toast.LENGTH_SHORT).show()
            }
        }

        updateHeaderUI()
    }

    // Biểu tượng ngẫu nhiên / lặp lại: tím đậm khi bật, tím nhạt khi tắt
    private fun updateHeaderUI() {
        val c = controller ?: return
        val onColor = ColorStateList.valueOf(Color.parseColor(COLOR_ON))
        val offColor = ColorStateList.valueOf(Color.parseColor(COLOR_OFF))

        btnShuffleBottom.imageTintList = if (c.shuffleModeEnabled) onColor else offColor

        when (c.repeatMode) {
            Player.REPEAT_MODE_OFF -> {
                btnRepeatBottom.setImageResource(R.drawable.ic_repeat)
                btnRepeatBottom.imageTintList = offColor
            }
            Player.REPEAT_MODE_ALL -> {
                btnRepeatBottom.setImageResource(R.drawable.ic_repeat)
                btnRepeatBottom.imageTintList = onColor
            }
            else -> {
                btnRepeatBottom.setImageResource(R.drawable.ic_repeat_one)
                btnRepeatBottom.imageTintList = onColor
            }
        }
    }

    private fun observePlayer() {
        val c = controller ?: return
        val listener = object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                updateUI()
            }
            override fun onTimelineChanged(timeline: Timeline, reason: Int) {
                updateUI()
            }
            override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                updateHeaderUI()
            }
            override fun onRepeatModeChanged(repeatMode: Int) {
                updateHeaderUI()
            }
        }
        playerListener = listener
        c.addListener(listener)
    }

    // Danh sách hiển thị = bài đang phát (vị trí 0, tô nền tím) + các bài tiếp theo
    private fun updateUI() {
        val c = controller ?: return
        if (!::adapter.isInitialized) return

        val items = mutableListOf<MediaItem>()
        var totalDurationMs = 0L
        for (i in c.currentMediaItemIndex until c.mediaItemCount) {
            val item = c.getMediaItemAt(i)
            items.add(item)
            totalDurationMs += item.mediaMetadata.durationMs ?: 0L
        }

        // Làm tròn lên theo phút
        val totalMinutes = (totalDurationMs + 59_999L) / 60_000L
        tvPlaylistSubtitle.text = "${items.size} bài hát • $totalMinutes phút"

        adapter.updateItems(items, 0)
    }

    override fun onDestroyView() {
        playerListener?.let { controller?.removeListener(it) }
        playerListener = null
        if (::controllerFuture.isInitialized) {
            MediaController.releaseFuture(controllerFuture)
        }
        super.onDestroyView()
    }
}