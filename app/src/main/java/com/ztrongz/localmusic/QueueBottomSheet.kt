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
import java.util.Locale

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

        // Bảng chiếm toàn màn hình: chừa chỗ cho thanh trạng thái phía trên
        val resId = resources.getIdentifier("status_bar_height", "dimen", "android")
        val statusBarHeight = if (resId > 0) resources.getDimensionPixelSize(resId) else 0
        view.setPadding(0, statusBarHeight, 0, 0)

        val token = SessionToken(requireContext(), ComponentName(requireContext(), PlaybackService::class.java))
        controllerFuture = MediaController.Builder(requireContext(), token).buildAsync()
        controllerFuture.addListener({
            if (!isAdded || view.parent == null) return@addListener
            controller = controllerFuture.get()
            setupRecyclerView()
            setupBottomBar()
            observePlayer()
            updateUI()
            scrollToCurrent()
        }, MoreExecutors.directExecutor())
    }

    override fun onStart() {
        super.onStart()
        val bsDialog = dialog as? BottomSheetDialog ?: return

        // Bảng cao bằng toàn bộ màn hình
        val sheet = bsDialog.findViewById<FrameLayout>(com.google.android.material.R.id.design_bottom_sheet)
        if (sheet != null) {
            sheet.layoutParams.height = ViewGroup.LayoutParams.MATCH_PARENT
            // Bỏ nền mặc định của Material để thấy nền kính mờ của mình
            sheet.setBackgroundColor(Color.TRANSPARENT)
            sheet.requestLayout()
        }

        bsDialog.behavior.apply {
            peekHeight = resources.displayMetrics.heightPixels
            skipCollapsed = true
            state = BottomSheetBehavior.STATE_EXPANDED
        }
    }

    private fun setupRecyclerView() {
        adapter = QueueAdapter(
            items = emptyList(),
            onItemClick = { position ->
                val c = controller
                if (c != null) {
                    if (position == adapter.currentPlayingIndex) {
                        // Bấm vào bài đang phát: bật / tạm dừng
                        if (c.isPlaying) c.pause() else c.play()
                    } else if (position < c.mediaItemCount) {
                        c.seekTo(position, 0L)
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
        // Bài đang phát không cho kéo hay vuốt xóa
        override fun getMovementFlags(
            recyclerView: RecyclerView,
            viewHolder: RecyclerView.ViewHolder
        ): Int {
            val pos = viewHolder.bindingAdapterPosition
            if (pos == RecyclerView.NO_POSITION || pos == adapter.currentPlayingIndex) return 0
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
            if (from == RecyclerView.NO_POSITION || to == RecyclerView.NO_POSITION) return false

            // Không cho kéo bài vượt qua bài đang phát (tránh lệch vị trí tô nền)
            val cur = adapter.currentPlayingIndex
            if (to == cur) return false
            if ((from < cur && to > cur) || (from > cur && to < cur)) return false

            if (dragFrom == -1) dragFrom = from
            dragTo = to
            adapter.onItemMove(from, to)
            return true
        }

        override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
            val position = viewHolder.bindingAdapterPosition
            val c = controller
            if (c == null || position == RecyclerView.NO_POSITION) return
            if (position < c.mediaItemCount) {
                adapter.removeItem(position)
                c.removeMediaItem(position)
                Toast.makeText(requireContext(), "Đã xóa bài hát", Toast.LENGTH_SHORT).show()
            }
        }

        // Thả tay: đồng bộ thứ tự mới sang trình phát (di chuyển 1 bài, không làm ngắt nhạc)
        override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
            super.clearView(recyclerView, viewHolder)
            val c = controller
            if (c != null && dragFrom != -1 && dragTo != -1 && dragFrom != dragTo) {
                c.moveMediaItem(dragFrom, dragTo)
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

        // Xóa hàng đợi: chỉ xóa các bài SAU bài đang phát (các bài đã nghe vẫn giữ)
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
                // Sang bài mới thì cuộn tới bài đang phát
                scrollToCurrent()
            }
            override fun onTimelineChanged(timeline: Timeline, reason: Int) {
                updateUI()
            }
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (::adapter.isInitialized) adapter.setPlaying(isPlaying)
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

    // Hiển thị TOÀN BỘ hàng đợi; bài đang phát được tô nền tím ở đúng vị trí của nó
    private fun updateUI() {
        val c = controller ?: return
        if (!::adapter.isInitialized) return

        val items = mutableListOf<MediaItem>()
        var totalDurationMs = 0L
        for (i in 0 until c.mediaItemCount) {
            val item = c.getMediaItemAt(i)
            items.add(item)
            totalDurationMs += item.mediaMetadata.durationMs ?: 0L
        }

        tvPlaylistSubtitle.text = "${items.size} bài hát • ${formatTotalDuration(totalDurationMs)}"

        adapter.updateItems(items, c.currentMediaItemIndex)
        adapter.setPlaying(c.isPlaying)
    }

    // Đưa bài đang phát lên đầu vùng nhìn thấy
    private fun scrollToCurrent() {
        if (!::adapter.isInitialized) return
        val lm = rvQueue.layoutManager as? LinearLayoutManager ?: return
        val pos = adapter.currentPlayingIndex
        if (pos >= 0) lm.scrollToPositionWithOffset(pos, 0)
    }

    // Dưới 1 giờ: "42:30". Từ 1 giờ trở lên: "1h 00:30"
    private fun formatTotalDuration(millis: Long): String {
        val totalSeconds = (millis.coerceAtLeast(0L)) / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) {
            String.format(Locale.US, "%dh %02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.US, "%02d:%02d", minutes, seconds)
        }
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