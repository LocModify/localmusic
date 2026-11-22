package com.ztrongz.localmusic

import android.content.ComponentName
import android.content.res.ColorStateList
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
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
import java.util.concurrent.TimeUnit

class QueueBottomSheet : BottomSheetDialogFragment() {

    private lateinit var controllerFuture: ListenableFuture<MediaController>
    private var controller: MediaController? = null
    private lateinit var adapter: QueueAdapter
    private lateinit var rvQueue: RecyclerView
    
    private lateinit var btnShuffleBottom: ImageView
    private lateinit var btnRepeatBottom: ImageView
    private lateinit var tvPlaylistTitle: TextView
    private lateinit var tvPlaylistSubtitle: TextView

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
        tvPlaylistTitle = view.findViewById(R.id.tvPlaylistTitle)
        tvPlaylistSubtitle = view.findViewById(R.id.tvPlaylistSubtitle)

        val token = SessionToken(requireContext(), ComponentName(requireContext(), PlaybackService::class.java))
        controllerFuture = MediaController.Builder(requireContext(), token).buildAsync()
        controllerFuture.addListener({
            controller = controllerFuture.get()
            setupRecyclerView()
            setupBottomBar()
            observePlayer()
            updateUI()
        }, MoreExecutors.directExecutor())
    }

    override fun onStart() {
        super.onStart()
        val dialog = dialog as? BottomSheetDialog
        dialog?.behavior?.apply {
            state = BottomSheetBehavior.STATE_EXPANDED
            skipCollapsed = true
            // Ép chiều cao của BottomSheet bằng toàn bộ màn hình
            peekHeight = ViewGroup.LayoutParams.MATCH_PARENT
        }
        dialog?.window?.setDimAmount(0.4f)
    }

    private fun setupRecyclerView() {
        val c = controller ?: return
        val startIndex = c.currentMediaItemIndex + 2

        adapter = QueueAdapter(emptyList(), startIndex,
            onItemClick = { index ->
                val actualIndex = c.currentMediaItemIndex + 1 + index
                if (actualIndex < c.mediaItemCount) {
                    c.seekTo(actualIndex, 0L)
                    c.play()
                }
            },
            onStartDrag = { viewHolder ->
                itemTouchHelper.startDrag(viewHolder)
            },
            onMoreClick = { index, _ ->
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
        override fun onMove(
            recyclerView: RecyclerView,
            viewHolder: RecyclerView.ViewHolder,
            target: RecyclerView.ViewHolder
        ): Boolean {
            adapter.onItemMove(viewHolder.bindingAdapterPosition, target.bindingAdapterPosition)
            return true
        }

        override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
            val position = viewHolder.bindingAdapterPosition
            val c = controller ?: return
            val actualIndex = c.currentMediaItemIndex + 1 + position
            
            if (actualIndex < c.mediaItemCount) {
                c.removeMediaItem(actualIndex)
                adapter.removeItem(position)
                Toast.makeText(requireContext(), "Đã xóa bài hát", Toast.LENGTH_SHORT).show()
            }
        }

        override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
            super.clearView(recyclerView, viewHolder)
            if (viewHolder.bindingAdapterPosition != RecyclerView.NO_POSITION) {
                syncPlayerQueue()
            }
        }
    })

    private fun syncPlayerQueue() {
        val c = controller ?: return
        val newList = adapter.getCurrentList()
        val currentIndex = c.currentMediaItemIndex
        
        val itemsToKeep = mutableListOf<MediaItem>()
        for (i in 0..currentIndex) {
            itemsToKeep.add(c.getMediaItemAt(i))
        }
        
        val wasPlaying = c.isPlaying
        val currentPos = c.currentPosition
        
        c.clearMediaItems()
        c.addMediaItems(itemsToKeep)
        c.addMediaItems(newList)
        c.seekTo(currentIndex, currentPos)
        if (wasPlaying) c.play() else c.pause()
    }

    private fun setupBottomBar() {
        val c = controller ?: return

        btnShuffleBottom.setOnClickListener {
            c.shuffleModeEnabled = !c.shuffleModeEnabled
            updateHeaderUI()
            updateUI()
        }

        btnRepeatBottom.setOnClickListener {
            val nextMode = when (c.repeatMode) {
                Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                else -> Player.REPEAT_MODE_OFF
            }
            c.repeatMode = nextMode
            updateHeaderUI()
        }

        view?.findViewById<View>(R.id.btnClearQueue)?.setOnClickListener {
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

    private fun updateHeaderUI() {
        val c = controller ?: return
        val grayColor = Color.parseColor("#9E9E9E")
        val purpleColor = Color.parseColor("#7C3AED")

        btnShuffleBottom.imageTintList = ColorStateList.valueOf(if (c.shuffleModeEnabled) purpleColor else grayColor)

        when (c.repeatMode) {
            Player.REPEAT_MODE_OFF -> {
                btnRepeatBottom.setImageResource(R.drawable.ic_repeat)
                btnRepeatBottom.imageTintList = ColorStateList.valueOf(grayColor)
            }
            Player.REPEAT_MODE_ALL -> {
                btnRepeatBottom.setImageResource(R.drawable.ic_repeat)
                btnRepeatBottom.imageTintList = ColorStateList.valueOf(purpleColor)
            }
            Player.REPEAT_MODE_ONE -> {
                btnRepeatBottom.setImageResource(R.drawable.ic_repeat_one)
                btnRepeatBottom.imageTintList = ColorStateList.valueOf(purpleColor)
            }
        }
    }

    private fun observePlayer() {
        val c = controller ?: return
        val listener = object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                updateUI()
            }
            override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) {
                updateUI()
            }
            override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                updateHeaderUI()
                updateUI() 
            }
            override fun onRepeatModeChanged(repeatMode: Int) {
                updateHeaderUI()
            }
        }
        c.addListener(listener)
    }

    private fun updateUI() {
        val c = controller ?: return

        var totalDurationMs = 0L
        for (i in 0 until c.mediaItemCount) {
            totalDurationMs += (c.getMediaItemAt(i).mediaMetadata.durationMs ?: 0L)
        }
        val totalMinutes = TimeUnit.MILLISECONDS.toMinutes(totalDurationMs)
        tvPlaylistSubtitle.text = "${c.mediaItemCount} bài hát • $totalMinutes phút"

        val upcomingItems = mutableListOf<MediaItem>()
        for (i in (c.currentMediaItemIndex + 1) until c.mediaItemCount) {
            upcomingItems.add(c.getMediaItemAt(i))
        }
        
        adapter.updateItems(upcomingItems, -1)
    }

    override fun onDestroyView() {
        if (::controllerFuture.isInitialized) {
            MediaController.releaseFuture(controllerFuture)
        }
        super.onDestroyView()
    }
}