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
import com.google.android.material.snackbar.Snackbar
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors

class QueueBottomSheet : BottomSheetDialogFragment() {

    private lateinit var controllerFuture: ListenableFuture<MediaController>
    private var controller: MediaController? = null
    private lateinit var adapter: QueueAdapter
    private lateinit var rvQueue: RecyclerView
    private lateinit var btnShuffle: ImageView
    private lateinit var btnRepeat: ImageView

    // View cho phần Current Track
    private lateinit var ivCurrentThumbnail: ImageView
    private lateinit var tvCurrentTitle: TextView
    private lateinit var tvCurrentArtist: TextView

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
        
        ivCurrentThumbnail = view.findViewById(R.id.ivCurrentThumbnail)
        tvCurrentTitle = view.findViewById(R.id.tvCurrentTitle)
        tvCurrentArtist = view.findViewById(R.id.tvCurrentArtist)

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
            updateUI()
        }, MoreExecutors.directExecutor())
    }

    override fun onStart() {
        super.onStart()
        val dialog = dialog as? BottomSheetDialog
        dialog?.behavior?.state = BottomSheetBehavior.STATE_EXPANDED
    }

    private fun setupRecyclerView() {
        adapter = QueueAdapter(emptyList(), 
            onItemClick = { index ->
                val c = controller ?: return@QueueAdapter
                val actualIndex = c.currentMediaItemIndex + 1 + index
                if (actualIndex < c.mediaItemCount) {
                    c.seekTo(actualIndex, 0L)
                    c.play()
                }
            },
            onStartDrag = { viewHolder ->
                itemTouchHelper.startDrag(viewHolder)
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
            val fromPos = viewHolder.bindingAdapterPosition
            val toPos = target.bindingAdapterPosition
            adapter.onItemMove(fromPos, toPos)
            return true
        }

        override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
            val position = viewHolder.bindingAdapterPosition
            val c = controller ?: return
            val actualIndex = c.currentMediaItemIndex + 1 + position
            
            if (actualIndex < c.mediaItemCount) {
                val removedItem = c.getMediaItemAt(actualIndex)
                c.removeMediaItem(actualIndex)
                adapter.removeItem(position)
                showUndoSnackbar(position, removedItem)
            }
        }

        override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
            super.clearView(recyclerView, viewHolder)
            // Khi thả tay sau khi kéo thả, cập nhật lại danh sách trong Player
            if (viewHolder.bindingAdapterPosition != RecyclerView.NO_POSITION) {
                syncPlayerQueue()
            }
        }
    })

    private fun syncPlayerQueue() {
        val c = controller ?: return
        val newList = adapter.getCurrentList()
        val currentIndex = c.currentMediaItemIndex
        
        // Lấy danh sách các bài đã phát (bao gồm bài hiện tại)
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

    private fun showUndoSnackbar(position: Int, removedItem: MediaItem) {
        val snackbarContainer = view?.findViewById<View>(R.id.snackbarContainer) ?: return
        val title = removedItem.mediaMetadata.title ?: "bài hát"
        
        Snackbar.make(snackbarContainer, "Đã xóa $title", Snackbar.LENGTH_LONG).apply {
            setAction("Hoàn tác") {
                controller?.let { c ->
                    val actualIndex = c.currentMediaItemIndex + 1 + position
                    if (actualIndex <= c.mediaItemCount) {
                        c.addMediaItem(actualIndex, removedItem)
                        adapter.addItem(position, removedItem)
                    }
                }
            }
            show()
        }
    }

    private fun setupHeaderButtons() {
        val c = controller ?: return

        btnShuffle.setOnClickListener {
            c.shuffleModeEnabled = !c.shuffleModeEnabled
            updateHeaderUI()
            updateUI() // Cập nhật lại danh sách vì thứ tự có thể thay đổi
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

        // 1. Cập nhật phần Current Track
        val currentItem = c.currentMediaItem
        if (currentItem != null) {
            tvCurrentTitle.text = currentItem.mediaMetadata.title ?: "Unknown Title"
            tvCurrentArtist.text = currentItem.mediaMetadata.artist ?: "Unknown Artist"
            
            val artworkData = currentItem.mediaMetadata.artworkData
            if (artworkData != null) {
                val bitmap = BitmapFactory.decodeByteArray(artworkData, 0, artworkData.size)
                ivCurrentThumbnail.setImageBitmap(bitmap)
            } else {
                ivCurrentThumbnail.setImageResource(R.drawable.bg_cover_default)
            }
        }

        // 2. Cập nhật danh sách Up Next (Chỉ lấy các bài sau bài hiện tại)
        val upcomingItems = mutableListOf<MediaItem>()
        for (i in (c.currentMediaItemIndex + 1) until c.mediaItemCount) {
            upcomingItems.add(c.getMediaItemAt(i))
        }
        adapter.updateItems(upcomingItems)
    }

    override fun onDestroyView() {
        if (::controllerFuture.isInitialized) {
            MediaController.releaseFuture(controllerFuture)
        }
        super.onDestroyView()
    }
}