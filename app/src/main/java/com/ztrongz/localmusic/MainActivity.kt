package com.ztrongz.localmusic

import android.Manifest
import android.content.ComponentName
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.View
import android.widget.EditText
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.ztrongz.localmusic.databinding.ActivityMainBinding
import java.io.ByteArrayOutputStream

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var controllerFuture: ListenableFuture<MediaController>
    private var controller: MediaController? = null
    private val tracks = mutableListOf<Track>()
    private lateinit var songAdapter: SongAdapter

    private val pickFiles = registerForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNullOrEmpty()) return@registerForActivityResult
        
        val newlyAddedTracks = mutableListOf<Track>()
        
        uris.forEach { uri ->
            val track = extractTrackInfo(uri)
            if (track != null && tracks.none { it.uri == uri }) {
                tracks.add(track)
                newlyAddedTracks.add(track)
            }
        }
        persistPermission(uris)
        
        if (newlyAddedTracks.isNotEmpty()) {
            addTracksToPlaylist(newlyAddedTracks)
        }
    }

    private val requestNotif = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        songAdapter = SongAdapter(
            tracks,
            onSongClick = { position -> playSongAt(position) },
            onMoreClick = { position, view -> showSongOptions(position, view) }
        )
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = songAdapter

        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            requestNotif.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        binding.btnAdd.setOnClickListener {
            pickFiles.launch(arrayOf("audio/*"))
        }

        binding.btnPlayPause.setOnClickListener {
            controller?.let { c ->
                if (c.isPlaying) c.pause() else c.play()
            }
        }

        binding.btnNext.setOnClickListener { controller?.seekToNextMediaItem() }
        binding.btnPrev.setOnClickListener { controller?.seekToPreviousMediaItem() }

        connectController()
    }

    private fun showSongOptions(position: Int, view: View) {
        val popup = PopupMenu(this, view)
        popup.menuInflater.inflate(R.menu.song_options_menu, popup.menu)

        val track = tracks[position]

        val favItem = popup.menu.findItem(R.id.action_favorite)
        if (track.isFavorite) {
            favItem.setIcon(R.drawable.ic_heart_filled)
            favItem.title = "Bỏ yêu thích"
        } else {
            favItem.setIcon(R.drawable.ic_heart_outline)
            favItem.title = "Yêu thích"
        }

        val deleteItem = popup.menu.findItem(R.id.action_delete)
        val spannable = SpannableString(deleteItem.title)
        spannable.setSpan(ForegroundColorSpan(Color.RED), 0, spannable.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        deleteItem.title = spannable

        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_favorite -> {
                    toggleFavorite(position)
                    true
                }
                R.id.action_rename -> {
                    showRenameDialog(position)
                    true
                }
                R.id.action_delete -> {
                    deleteSong(position)
                    true
                }
                else -> false
            }
        }
        popup.show()
    }

    private fun toggleFavorite(position: Int) {
        val track = tracks[position]
        tracks[position] = track.copy(isFavorite = !track.isFavorite)
        songAdapter.notifyItemChanged(position)
    }

    private fun showRenameDialog(position: Int) {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Đổi tên bài hát")

        val input = EditText(this)
        input.setText(tracks[position].title)
        builder.setView(input)

        builder.setPositiveButton("Lưu") { _, _ ->
            val newTitle = input.text.toString().trim()
            if (newTitle.isNotEmpty()) {
                tracks[position] = tracks[position].copy(title = newTitle)
                songAdapter.notifyItemChanged(position)
                updatePlayerPlaylist()
            }
        }
        builder.setNegativeButton("Hủy") { dialog, _ -> dialog.cancel() }
        builder.show()
    }

    private fun deleteSong(position: Int) {
        tracks.removeAt(position)
        songAdapter.notifyItemRemoved(position)
        songAdapter.notifyItemRangeChanged(position, tracks.size)
        updatePlayerPlaylist()
    }

    private fun playSongAt(position: Int) {
        controller?.let { c ->
            c.seekTo(position, 0L)
            c.play()
        }
    }

    private fun connectController() {
        val token = SessionToken(this, ComponentName(this, PlaybackService::class.java))
        controllerFuture = MediaController.Builder(this, token).buildAsync()
        controllerFuture.addListener({
            controller = controllerFuture.get()
            controller?.addListener(playerListener)
            songAdapter.updateData(tracks)
        }, MoreExecutors.directExecutor())
    }

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            binding.btnPlayPause.setImageResource(
                if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play
            )
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            binding.tvNowPlaying.text = mediaItem?.mediaMetadata?.title ?: "Chưa phát bài nào"
            
            val artistText = mediaItem?.mediaMetadata?.artist?.toString() ?: ""
            if (artistText.isBlank()) {
                binding.tvMiniArtist.visibility = View.GONE
            } else {
                binding.tvMiniArtist.visibility = View.VISIBLE
                binding.tvMiniArtist.text = artistText
            }

            val currentIndex = controller?.currentMediaItemIndex ?: -1
            if (currentIndex in tracks.indices) {
                val currentTrack = tracks[currentIndex]
                if (currentTrack.cover != null) {
                    binding.ivMiniCover.setImageBitmap(currentTrack.cover)
                } else {
                    binding.ivMiniCover.setImageResource(R.drawable.bg_cover_default)
                }
            }
        }
    }

    private fun updatePlayerPlaylist() {
        val c = controller ?: return
        val wasPlaying = c.isPlaying
        val currentPos = c.currentMediaItemIndex
        val currentTime = c.currentPosition

        val defaultArtworkBytes = getDefaultArtworkBytes()
        val items = tracks.map { t ->
            val metadataBuilder = MediaMetadata.Builder()
                .setTitle(t.title)
                .setArtist(t.artist)

            if (t.cover != null) {
                val stream = ByteArrayOutputStream()
                t.cover.compress(Bitmap.CompressFormat.JPEG, 90, stream)
                metadataBuilder.setArtworkData(stream.toByteArray(), MediaMetadata.PICTURE_TYPE_FRONT_COVER)
            } else {
                metadataBuilder.setArtworkData(defaultArtworkBytes, MediaMetadata.PICTURE_TYPE_FRONT_COVER)
            }

            MediaItem.Builder().setUri(t.uri).setMediaMetadata(metadataBuilder.build()).build()
        }

        c.setMediaItems(items, if (currentPos < items.size) currentPos else 0, currentTime)
        if (wasPlaying) c.play()
    }

    private fun addTracksToPlaylist(newTracks: List<Track>) {
        val c = controller ?: return
        val defaultArtworkBytes = getDefaultArtworkBytes()

        val newItems = newTracks.map { t ->
            val metadataBuilder = MediaMetadata.Builder()
                .setTitle(t.title)
                .setArtist(t.artist)

            if (t.cover != null) {
                val stream = ByteArrayOutputStream()
                t.cover.compress(Bitmap.CompressFormat.JPEG, 90, stream)
                metadataBuilder.setArtworkData(stream.toByteArray(), MediaMetadata.PICTURE_TYPE_FRONT_COVER)
            } else {
                metadataBuilder.setArtworkData(defaultArtworkBytes, MediaMetadata.PICTURE_TYPE_FRONT_COVER)
            }

            MediaItem.Builder().setUri(t.uri).setMediaMetadata(metadataBuilder.build()).build()
        }

        if (c.mediaItemCount == 0) {
            c.setMediaItems(newItems)
        } else {
            c.addMediaItems(newItems)
        }
        
        songAdapter.updateData(tracks)
    }

    private fun getDefaultArtworkBytes(): ByteArray {
        val bitmap = BitmapFactory.decodeResource(resources, R.drawable.bg_cover_default)
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, stream)
        return stream.toByteArray()
    }

    private fun extractTrackInfo(uri: Uri): Track? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(this, uri)
            
            val title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE) 
                ?: queryName(uri) ?: "Unknown Title"
            
            val artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST) ?: ""
            
            val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            val duration = durationStr?.toLongOrNull() ?: 0L
            
            val embeddedPicture = retriever.embeddedPicture
            var coverBitmap: Bitmap? = null
            if (embeddedPicture != null) {
                coverBitmap = BitmapFactory.decodeByteArray(embeddedPicture, 0, embeddedPicture.size)
            }

            Track(uri, title, artist, duration, coverBitmap)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        } finally {
            retriever.release()
        }
    }

    private fun queryName(uri: Uri): String? {
        contentResolver.query(uri, null, null, null, null)?.use { cur ->
            val idx = cur.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && cur.moveToFirst()) return cur.getString(idx)
        }
        return null
    }

    private fun persistPermission(uris: List<Uri>) {
        uris.forEach { uri ->
            try {
                contentResolver.takePersistableUriPermission(
                    uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: SecurityException) {}
        }
    }

    override fun onDestroy() {
        controller?.removeListener(playerListener)
        controllerFuture.addListener({
            MediaController.releaseFuture(controllerFuture)
        }, MoreExecutors.directExecutor())
        super.onDestroy()
    }
}