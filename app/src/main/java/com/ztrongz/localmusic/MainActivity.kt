package com.ztrongz.localmusic

import android.Manifest
import android.content.ComponentName
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.media.ThumbnailUtils
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.text.Editable
import android.text.TextWatcher
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
        persistPermission(uris)
        // Đọc metadata ở luồng nền để không giật UI
        Thread {
            val newTracks = uris.filter { u -> tracks.none { it.uri == u } }.map { loadTrack(it) }
            runOnUiThread {
                tracks.addAll(newTracks)
                refreshPlaylist()
            }
        }.start()
    }

    private val requestNotif = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        songAdapter = SongAdapter(tracks) { track -> playTrack(track) }
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

        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) {
                songAdapter.filter(s?.toString().orEmpty())
            }
        })

        binding.btnPlayPause.setOnClickListener { v ->
            // Hiệu ứng nhún khi bấm
            v.animate().scaleX(0.85f).scaleY(0.85f).setDuration(80).withEndAction {
                v.animate().scaleX(1f).scaleY(1f).setDuration(120).start()
            }.start()
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
            refreshPlaylist()
        }, MoreExecutors.directExecutor())
    }

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            binding.btnPlayPause.setImageResource(
                if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play
            )
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            val track = tracks.firstOrNull { it.uri.toString() == mediaItem?.mediaId }
            binding.tvNowPlaying.text = track?.title ?: "Chưa phát bài nào"
            binding.tvMiniArtist.text = track?.artist ?: "Local"
            if (track?.cover != null) binding.ivMiniCover.setImageBitmap(track.cover)
            else binding.ivMiniCover.setImageResource(R.drawable.bg_cover_placeholder)
        }
    }

    private fun refreshPlaylist() {
        val c = controller ?: return
        val items = tracks.map { t ->
            MediaItem.Builder()
                .setUri(t.uri)
                .setMediaId(t.uri.toString())
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(t.title)
                        .setArtist(t.artist)
                        .build()
                )
                .build()
        }
        c.setMediaItems(items)

        songAdapter.updateData(tracks, binding.etSearch.text.toString())
    }

    private fun playTrack(track: Track) {
        val c = controller ?: return
        val index = tracks.indexOfFirst { it.uri == track.uri }
        if (index < 0) return
        c.seekTo(index, 0L)
        c.prepare()
        c.play()
    }

    private fun loadTrack(uri: Uri): Track {
        val fileName = queryName(uri) ?: uri.lastPathSegment ?: "Unknown"
        val fallbackTitle = fileName.substringBeforeLast('.')
        val mmr = MediaMetadataRetriever()
        return try {
            mmr.setDataSource(this, uri)
            val title = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                ?.takeIf { it.isNotBlank() } ?: fallbackTitle
            val artist = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                ?.takeIf { it.isNotBlank() } ?: "Unknown Artist"
            val duration = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 0L
            val cover = mmr.embeddedPicture?.let { bytes ->
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    ?.let { ThumbnailUtils.extractThumbnail(it, 192, 192) }
            }
            Track(uri, title, artist, duration, cover)
        } catch (e: Exception) {
            Track(uri, fallbackTitle)
        } finally {
            mmr.release()
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