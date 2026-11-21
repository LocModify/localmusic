package com.ztrongz.localmusic

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.PopupWindow
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
import org.json.JSONObject
import java.io.ByteArrayOutputStream

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var controllerFuture: ListenableFuture<MediaController>
    private var controller: MediaController? = null
    private val tracks = mutableListOf<Track>()
    private lateinit var songAdapter: SongAdapter
    
    private var isShowingFavorites = false

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
            saveTracksToStorage()
            filterAndShowSongs()
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
            onSongClick = { track -> 
                val index = tracks.indexOf(track)
                if (index != -1) playSongAt(index)
            },
            onMoreClick = { track, view -> 
                val index = tracks.indexOf(track)
                if (index != -1) showSongOptions(index, view)
            }
        )
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = songAdapter

        setupCategoryChips()

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

        loadTracksFromStorage()
        connectController()
    }

    private fun setupCategoryChips() {
        binding.chipAll.setOnClickListener {
            isShowingFavorites = false
            updateChipUI(binding.chipAll, binding.chipFav)
            filterAndShowSongs()
        }

        binding.chipFav.setOnClickListener {
            isShowingFavorites = true
            updateChipUI(binding.chipFav, binding.chipAll)
            filterAndShowSongs()
        }
    }

    private fun updateChipUI(activeChip: View, inactiveChip: View) {
        activeChip.setBackgroundResource(R.drawable.bg_category_active)
        if (activeChip is TextView) {
            activeChip.setTextColor(Color.WHITE)
            activeChip.compoundDrawableTintList = ContextCompat.getColorStateList(this, android.R.color.white)
        }

        inactiveChip.setBackgroundResource(R.drawable.bg_category_inactive)
        if (inactiveChip is TextView) {
            inactiveChip.setTextColor(Color.parseColor("#1F1B2E"))
            inactiveChip.compoundDrawableTintList = ContextCompat.getColorStateList(this, android.R.color.black)
        }
    }

    private fun filterAndShowSongs() {
        val displayList = if (isShowingFavorites) {
            tracks.filter { it.isFavorite }
        } else {
            tracks
        }
        songAdapter.updateData(displayList)
    }

    private fun saveTracksToStorage() {
        val prefs = getSharedPreferences("MusicAppPrefs", Context.MODE_PRIVATE)
        val jsonArray = JSONArray()
        tracks.forEach { 
            val obj = JSONObject()
            obj.put("uri", it.uri.toString())
            obj.put("isFavorite", it.isFavorite)
            obj.put("title", it.title)
            jsonArray.put(obj) 
        }
        prefs.edit().putString("saved_tracks", jsonArray.toString()).apply()
    }

    private fun loadTracksFromStorage() {
        val prefs = getSharedPreferences("MusicAppPrefs", Context.MODE_PRIVATE)
        val jsonString = prefs.getString("saved_tracks", null) ?: return
        
        try {
            val jsonArray = JSONArray(jsonString)
            tracks.clear()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val uri = Uri.parse(obj.getString("uri"))
                val isFav = obj.optBoolean("isFavorite", false)
                val savedTitle = obj.optString("title", "")
                
                val track = extractTrackInfo(uri)
                if (track != null) {
                    val finalTitle = if (savedTitle.isNotEmpty()) savedTitle else track.title
                    tracks.add(track.copy(title = finalTitle, isFavorite = isFav))
                }
            }
            filterAndShowSongs()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun showSongOptions(position: Int, view: View) {
        val popupView = LayoutInflater.from(this).inflate(R.layout.dialog_song_options, null)
        val popupWindow = PopupWindow(
            popupView,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true
        )
        
        popupWindow.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        popupWindow.elevation = 15f

        val track = tracks[position]

        val btnFavorite = popupView.findViewById<View>(R.id.btnMenuFavorite)
        val ivHeart = popupView.findViewById<ImageView>(R.id.ivMenuHeart)
        val tvFavorite = popupView.findViewById<TextView>(R.id.tvMenuFavorite)
        val btnRename = popupView.findViewById<View>(R.id.btnMenuRename)
        val btnDelete = popupView.findViewById<View>(R.id.btnMenuDelete)

        tvFavorite.setTextColor(Color.parseColor("#1F1B2E"))
        
        // Hàm phụ để cập nhật giao diện icon và chữ
        fun updateFavoriteUI(isFav: Boolean) {
            if (isFav) {
                ivHeart.setImageResource(R.drawable.ic_heart_filled)
                tvFavorite.text = "Bỏ yêu thích"
            } else {
                ivHeart.setImageResource(R.drawable.ic_heart_outline)
                tvFavorite.text = "Yêu thích"
            }
        }

        // Khởi tạo trạng thái ban đầu
        updateFavoriteUI(track.isFavorite)

        btnFavorite.setOnClickListener {
            toggleFavorite(position)
            // Cập nhật lại UI ngay tại chỗ, KHÔNG gọi popupWindow.dismiss()
            updateFavoriteUI(tracks[position].isFavorite)
        }

        btnRename.setOnClickListener {
            popupWindow.dismiss()
            showRenameDialog(position)
        }

        btnDelete.setOnClickListener {
            popupWindow.dismiss()
            deleteSong(position)
        }

        popupView.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
        val popupWidth = popupView.measuredWidth
        val offsetX = view.width - popupWidth - 16
        popupWindow.showAsDropDown(view, offsetX, 0)
    }

    private fun toggleFavorite(position: Int) {
        val track = tracks[position]
        tracks[position] = track.copy(isFavorite = !track.isFavorite)
        saveTracksToStorage()
        filterAndShowSongs() 
        // Lưu ý: Hàm filterAndShowSongs sẽ làm mới danh sách bên dưới,
        // nhưng menu Popup vẫn đang mở và hiển thị đúng trạng thái mới nhờ hàm updateFavoriteUI ở trên.
    }

    private fun showRenameDialog(position: Int) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_rename, null)
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()
            
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val edtTitle = dialogView.findViewById<EditText>(R.id.edtNewTitle)
        val btnCancel = dialogView.findViewById<TextView>(R.id.btnCancel)
        val btnSave = dialogView.findViewById<TextView>(R.id.btnSave)

        val currentTitle = tracks[position].title
        val titleWithoutExtension = if (currentTitle.contains(".")) {
            currentTitle.substringBeforeLast(".")
        } else {
            currentTitle
        }

        edtTitle.setText(titleWithoutExtension)
        edtTitle.setSelection(edtTitle.text.length)

        btnCancel.setOnClickListener { dialog.dismiss() }

        btnSave.setOnClickListener {
            val newTitle = edtTitle.text.toString().trim()
            if (newTitle.isNotEmpty()) {
                tracks[position] = tracks[position].copy(title = newTitle)
                saveTracksToStorage()
                filterAndShowSongs()
                updatePlayerPlaylist()
                dialog.dismiss()
            }
        }

        dialog.show()
    }

    private fun deleteSong(position: Int) {
        tracks.removeAt(position)
        updatePlayerPlaylist()
        saveTracksToStorage()
        filterAndShowSongs()
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
            
            if (controller?.mediaItemCount == 0 && tracks.isNotEmpty()) {
                val c = controller ?: return@addListener
                val items = tracks.map { t ->
                    MediaItem.Builder().setUri(t.uri).setMediaMetadata(
                        MediaMetadata.Builder().setTitle(t.title).setArtist(t.artist).build()
                    ).build()
                }
                c.setMediaItems(items)
            }
            updateMiniPlayerUI()
            filterAndShowSongs()
        }, MoreExecutors.directExecutor())
    }

    private fun updateMiniPlayerUI() {
        val c = controller ?: return
        val currentMediaItem = c.currentMediaItem
        
        binding.tvNowPlaying.text = currentMediaItem?.mediaMetadata?.title ?: "Chưa phát bài nào"
        val artistText = currentMediaItem?.mediaMetadata?.artist?.toString() ?: ""
        
        if (artistText.isBlank()) {
            binding.tvMiniArtist.visibility = View.GONE
        } else {
            binding.tvMiniArtist.visibility = View.VISIBLE
            binding.tvMiniArtist.text = artistText
        }

        val currentIndex = c.currentMediaItemIndex
        if (currentIndex in tracks.indices) {
            val currentTrack = tracks[currentIndex]
            if (currentTrack.cover != null) {
                binding.ivMiniCover.setImageBitmap(currentTrack.cover)
            } else {
                binding.ivMiniCover.setImageResource(R.drawable.bg_cover_default)
            }
        } else {
            binding.ivMiniCover.setImageResource(R.drawable.bg_cover_default)
        }

        binding.btnPlayPause.setImageResource(
            if (c.isPlaying) R.drawable.ic_pause else R.drawable.ic_play
        )
    }

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            updateMiniPlayerUI()
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            updateMiniPlayerUI()
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