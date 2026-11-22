package com.ztrongz.localmusic

import android.content.ComponentName
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Timeline
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

        val token = SessionToken(requireContext(), ComponentName(requireContext(), PlaybackService::class.java))
        controllerFuture = MediaController.Builder(requireContext(), token).buildAsync()
        controllerFuture.addListener({
            controller = controllerFuture.get()
            setupRecyclerView()
            observePlayer()
        }, MoreExecutors.directExecutor())
    }

    private fun setupRecyclerView() {
        val c = controller ?: return
        val timeline = c.currentTimeline
        val items = mutableListOf<MediaItem>()
        for (i in 0 until timeline.windowCount) {
            items.add(timeline.getWindow(i, Timeline.Window()).mediaItem)
        }

        adapter = QueueAdapter(items) { index ->
            controller?.seekTo(index, 0L)
            controller?.play()
        }

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

    private fun observePlayer() {
        val c = controller ?: return
        val listener = object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                updateQueueList()
            }
        }
        c.addListener(listener)
        updateQueueList()
    }

    private fun updateQueueList() {
        val c = controller ?: return
        val timeline = c.currentTimeline
        val items = mutableListOf<MediaItem>()
        for (i in 0 until timeline.windowCount) {
            items.add(timeline.getWindow(i, Timeline.Window()).mediaItem)
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