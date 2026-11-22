package com.ztrongz.localmusic

import android.content.ComponentName
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors

class QueueBottomSheet : BottomSheetDialogFragment() {

    private lateinit var controllerFuture: ListenableFuture<MediaController>
    private var controller: MediaController? = null
    private lateinit var adapter: QueueAdapter
    private lateinit var rvQueue: RecyclerView
    private lateinit var btnShuffle: ImageView
    private lateinit var btnRepeat: ImageView

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
        btnShuffle = view.findViewById(R.id.btnQueueShuffle)
        btnRepeat = view.findViewById(R.id.btnQueueRepeat)

        view.findViewById<ImageView>(R.id.btnCloseQueue).setOnClickListener {
            dismiss()
        }

        val token = SessionToken(requireContext(), ComponentName(requireContext(), PlaybackService::class.java))
        controllerFuture = MediaController.Builder(requireContext(), token).buildAsync()
        controllerFuture.addListener({
            controller = controllerFuture.get()
            setupRecyclerView()
            setupHeaderButtons()
            observePlayer()
            updateQueueList() // Load danh sách ngay lập tức
        }, MoreExecutors.directExecutor())
    }

    private fun setupRecyclerView() {
        adapter = QueueAdapter(emptyList(), 
            onItemClick = { index ->
                controller?.seekTo(index, 0L)
                controller?.play()
            },
            onMoreClick = { index, view ->
                // Xử lý nút "..." nếu cần (ví dụ: xóa khỏi hàng đợi)
            }
        )

        rvQueue.layoutManager = LinearLayoutManager(requireContext())
        rvQueue.adapter = adapter

        val itemTouchHelperCallback = object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN,
            0
        ) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                adapter.onItemMove(viewHolder.bindingAdapterPosition, target.bindingAdapterPosition)
                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {}

            override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
                super.clearView(recyclerView, viewHolder)
                val newList = adapter.getCurrentList()
                controller?.let { c ->
                    val wasPlaying = c.isPlaying
                    val currentItem = c.currentMediaItem
                    val currentPosition = c.currentPosition
                    
                    c.clearMediaItems()
                    c.addMediaItems(newList)
                    
                    if (currentItem != null) {
                        val newIndex = newList.indexOf(currentItem)
                        if (newIndex != -1) {
                            c.seekTo(newIndex, currentPosition)
                            if (wasPlaying) c.play() else c.pause()
                        }
                    }
                    adapter.setCurrentPlayingIndex(c.currentMediaItemIndex)
                }
            }
        }

        ItemTouchHelper(itemTouchHelperCallback).attachToRecyclerView(rvQueue)
    }

    private fun setupHeaderButtons() {
        val c = controller ?: return

        btnShuffle.setOnClickListener {
            c.shuffleModeEnabled = !c.shuffleModeEnabled
            updateHeaderUI()
        }

        btnRepeat.setOnClickListener {
            val nextMode = when (c.repeatMode) {
                Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                else -> Player.REPEAT_MODE_OFF
            }
            c.repeatMode = nextMode
            updateHeaderUI()
        }
        updateHeaderUI()
    }

    private fun updateHeaderUI() {
        val c = controller ?: return
        val grayColor = Color.parseColor("#9E9E9E")
        val purpleColor = Color.parseColor("#7C3AED")

        btnShuffle.imageTintList = ColorStateList.valueOf(if (c.shuffleModeEnabled) purpleColor else grayColor)

        when (c.repeatMode) {
            Player.REPEAT_MODE_OFF -> {
                btnRepeat.setImageResource(R.drawable.ic_repeat)
                btnRepeat.imageTintList = ColorStateList.valueOf(grayColor)
            }
            Player.REPEAT_MODE_ALL -> {
                btnRepeat.setImageResource(R.drawable.ic_repeat)
                btnRepeat.imageTintList = ColorStateList.valueOf(purpleColor)
            }
            Player.REPEAT_MODE_ONE -> {
                btnRepeat.setImageResource(R.drawable.ic_repeat_one)
                btnRepeat.imageTintList = ColorStateList.valueOf(purpleColor)
            }
        }
    }

    private fun observePlayer() {
        val c = controller ?: return
        val listener = object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                updateQueueList()
                updateHeaderUI()
            }
            override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                updateHeaderUI()
            }
            override fun onRepeatModeChanged(repeatMode: Int) {
                updateHeaderUI()
            }
        }
        c.addListener(listener)
    }

    private fun updateQueueList() {
        val c = controller ?: return
        
        // SỬA LỖI: Lấy danh sách bằng mediaItemCount thay vì currentTimeline
        val items = mutableListOf<MediaItem>()
        for (i in 0 until c.mediaItemCount) {
            c.getMediaItemAt(i).let { items.add(it) }
        }
        
        adapter.updateItems(items, c.currentMediaItemIndex)
    }

    override fun onDestroyView() {
        if (::controllerFuture.isInitialized) {
            MediaController.releaseFuture(controllerFuture)
        }
        super.onDestroyView()
    }
}