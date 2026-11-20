package com.ztrongz.localmusic

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
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
import org.json.JSONArray
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
            saveTracksToStorage() // Lưu lại vào bộ nhớ máy
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
            songs = tracks,
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

        // Load lại danh sách đã lưu khi mở app
        loadTracksFromStorage()
        connectController()
    }

    // --- HÀM LƯU VÀ ĐỌC DỮ LIỆU TỪ BỘ NHỚ ---
    private fun saveTracksToStorage() {
        val prefs = getSharedPreferences("MusicAppPrefs", Context.MODE_PRIVATE)
        val jsonArray = JSONArray()
        tracks.forEach { jsonArray.put(it.uri.toString()) }
        prefs.edit().putString("saved_tracks", jsonArray.toString()).apply()
    }

    private fun loadTracksFromStorage() {
        val prefs = getSharedPreferences("MusicAppPrefs", Context.MODE_PRIVATE)
        val jsonString = prefs.getString("saved_tracks", null) ?: return
        
        try {
            val jsonArray = JSONArray(jsonString)
            tracks.clear()
            for (i in 0 until jsonArray.length()) {
                val uri = Uri.parse(jsonArray.getString(i))
                val track = extractTrackInfo(uri)
                if (track != null) {
                    tracks.add(track)
                }
            }
            songAdapter.updateData(tracks)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // --- HÀM HIỂN THỊ MENU TÙY CHỈNH ---
    private fun showSongOptions(position: Int, view: View) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_song_options, null)
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        // Làm nền dialog trong suốt để hiện góc bo tròn của CardView
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val track = tracks[position]

        // Ánh xạ view
        val btnFavorite = dialogView.findViewById<View>(R.id.btnMenuFavorite)
        val ivHeart = dialogView.findViewById<ImageView>(R.id.ivMenuHeart)
        val tvFavorite = dialogView.findViewById<TextView>(R.id.tvMenuFavorite)
        val btnRename = dialogView.findViewById<View>(R.id.btnMenuRename)
        val btnDelete = dialogView.findViewById<View>(R.id.btnMenuDelete)

        // Cập nhật trạng thái Yêu thích
        if (track.isFavorite) {
            ivHeart.setImageResource(R.drawable.ic_heart_filled)
            tvFavorite.text = "Bỏ yêu thích"
            tvFavorite.setTextColor(android.graphics.Color.RED)
        } else {
            ivHeart.setImageResource(R.drawable.ic_heart_outline)
            tvFavorite.text = "Yêu thích"
            tvFavorite.setTextColor(android.graphics.Color.parseColor("#1F1B2E"))
        }

        // Gắn sự kiện
        btnFavorite.setOnClickListener {
            toggleFavorite(position)
            dialog.dismiss()
        }

        btnRename.setOnClickListener {
            dialog.dismiss()
            showRenameDialog(position)
        }

        btnDelete.setOnClickListener {
            dialog.dismiss()
            deleteSong(position)
        }

        dialog.show()
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
        saveTracksToStorage() // Cập nhật lại bộ nhớ sau khi xóa
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
            // Đẩy danh sách đã load từ bộ nhớ vào trình phát
            if (tracks.isNotEmpty()) {
                val c = controller ?: return@addListener
                val items = tracks.map { t ->
                    MediaItem.Builder().setUri(t.uri).setMediaMetadata(
                        MediaMetadata.Builder().setTitle(t.title).setArtist(t.artist).build()
                    ).build()
                }
                c.setMediaItems(items)
            }
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
            val metadataBuilder = MediaMetadata.Builder().setTitle(t.title).setArtist(t.artist)
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
            val metadataBuilder = MediaMetadata.Builder().setTitle(t.title).setArtist(t.artist)
            if (t.cover != null) {
                val stream = ByteArrayOutputStream()
                t.cover.compress(Bitmap.CompressFormat.JPEG, 90, stream)
                metadataBuilder.setArtworkData(stream.toByteArray(), MediaMetadata.PICTURE_TYPE_FRONT_COVER)
            } else {
                metadataBuilder.setArtworkData(defaultArtworkBytes, MediaMetadata.PICTURE_TYPE_FRONT_COVER)
            }
            MediaItem.Builder().setUri(t.uri).setMediaMetadata(metadataBuilder.build()).build()
        }

        if (c.mediaItemCount == 0) c.setMediaItems(newItems) else c.addMediaItems(newItems)
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
            val title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE) ?: queryName(uri) ?: "Unknown Title"
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
                contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
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