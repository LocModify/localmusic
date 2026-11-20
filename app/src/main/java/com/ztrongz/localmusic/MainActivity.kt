package com.ztrongz.localmusic

import android.Manifest
import android.content.ComponentName
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.result.contract.ActivityResultContracts
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
        
        // Danh sách chứa các bài hát MỚI ĐƯỢC THÊM
        val newlyAddedTracks = mutableListOf<Track>()
        
        uris.forEach { uri ->
            val track = extractTrackInfo(uri)
            // Kiểm tra xem bài hát đã có trong danh sách chưa
            if (track != null && tracks.none { it.uri == uri }) {
                tracks.add(track)
                newlyAddedTracks.add(track) // Thêm vào danh sách mới
            }
        }
        persistPermission(uris)
        
        // Nếu có bài hát mới thì mới cập nhật vào trình phát
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

        songAdapter = SongAdapter(tracks)
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

    private fun connectController() {
        val token = SessionToken(this, ComponentName(this, PlaybackService::class.java))
        controllerFuture = MediaController.Builder(this, token).buildAsync()
        controllerFuture.addListener({
            controller = controllerFuture.get()
            controller?.addListener(playerListener)
            // Chỉ cập nhật giao diện, không nạp lại danh sách nhạc
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
            binding.tvMiniArtist.text = mediaItem?.mediaMetadata?.artist ?: "Local"

            val currentIndex = controller?.currentMediaItemIndex ?: -1
            if (currentIndex in tracks.indices) {
                val currentTrack = tracks[currentIndex]
                if (currentTrack.cover != null) {
                    binding.ivMiniCover.setImageBitmap(currentTrack.cover)
                } else {
                    binding.ivMiniCover.setImageResource(R.drawable.bg_cover_placeholder)
                }
            }
        }
    }

    // Hàm xử lý thêm nhạc vào danh sách phát mà không làm gián đoạn bài đang phát
    private fun addTracksToPlaylist(newTracks: List<Track>) {
        val c = controller ?: return
        
        val newItems = newTracks.map { t ->
            MediaItem.Builder()
                .setUri(t.uri)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(t.title)
                        .setArtist(t.artist)
                        .build()
                )
                .build()
        }

        // Nếu danh sách phát đang trống (chưa phát gì) thì nạp toàn bộ.
        // Nếu đang phát rồi thì CHỈ NỐI THÊM vào cuối danh sách (không reset).
        if (c.mediaItemCount == 0) {
            c.setMediaItems(newItems)
        } else {
            c.addMediaItems(newItems)
        }
        
        // Cập nhật lại giao diện danh sách (RecyclerView)
        songAdapter.updateData(tracks)
    }

    private fun extractTrackInfo(uri: Uri): Track? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(this, uri)
            
            val title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE) 
                ?: queryName(uri) ?: "Unknown Title"
            
            val artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST) 
                ?: "Unknown Artist"
            
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