package com.ztrongz.localmusic

import android.content.ComponentName
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import org.json.JSONArray

class NowPlayingActivity : AppCompatActivity() {

    private lateinit var rootLayout: SwipeableConstraintLayout
    private lateinit var bgOverlay: View
    private lateinit var contentContainer: View
    private lateinit var btnMinimize: ImageView
    private lateinit var btnPlayPause: ImageView
    private lateinit var btnNext: ImageView
    private lateinit var btnPrevious: ImageView
    private lateinit var imgAlbumArt: ImageView
    private lateinit var txtSongTitle: TextView
    private lateinit var txtArtist: TextView
    private lateinit var txtCurrentTime: TextView
    private lateinit var txtTotalTime: TextView
    private lateinit var seekBar: SeekBar
    private lateinit var btnFavoriteTop: ImageView
    private lateinit var btnFavoriteBottom: Button

    private lateinit var controllerFuture: ListenableFuture<MediaController>
    private var controller: MediaController? = null
    private val handler = Handler(Looper.getMainLooper())
    private var isUserSeeking = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_now_playing)

        rootLayout = findViewById(R.id.rootLayout)
        bgOverlay = findViewById(R.id.bg_overlay)
        contentContainer = findViewById(R.id.content_container)
        btnMinimize = findViewById(R.id.btnMinimize)
        btnPlayPause = findViewById(R.id.btnPlayPause)
        btnNext = findViewById(R.id.btnNext)
        btnPrevious = findViewById(R.id.btnPrevious)
        imgAlbumArt = findViewById(R.id.imgAlbumArt)
        txtSongTitle = findViewById(R.id.txtSongTitle)
        txtArtist = findViewById(R.id.txtArtist)
        txtCurrentTime = findViewById(R.id.txtCurrentTime)
        txtTotalTime = findViewById(R.id.txtTotalTime)
        seekBar = findViewById(R.id.seekBar)
        btnFavoriteTop = findViewById(R.id.btnFavoriteTop)
        btnFavoriteBottom = findViewById(R.id.btnFavoriteBottom)

        btnMinimize.setOnClickListener {
            closeActivity()
        }

        rootLayout.onSwipeDownListener = {
            closeActivity()
        }

        // Làm mờ cả nền và nội dung khi kéo xuống để lộ màn hình chính
        rootLayout.onDragProgressListener = { progress ->
            bgOverlay.alpha = progress
            contentContainer.alpha = progress
        }

        btnFavoriteTop.setOnClickListener { toggleFavorite() }
        btnFavoriteBottom.setOnClickListener { toggleFavorite() }

        connectController()
    }

    private fun closeActivity() {
        finish()
        // Hiệu ứng đóng: Trượt xuống, MainActivity hiện ra ngay lập tức
        overridePendingTransition(0, R.anim.slide_down)
    }

    private fun connectController() {
        val token = SessionToken(this, ComponentName(this, PlaybackService::class.java))
        controllerFuture = MediaController.Builder(this, token).buildAsync()
        controllerFuture.addListener({
            controller = controllerFuture.get()
            controller?.addListener(playerListener)
            
            updateUI()
            startSeekBarUpdater()
            setupControlButtons()
        }, MoreExecutors.directExecutor())
    }

    private fun setupControlButtons() {
        val c = controller ?: return

        btnPlayPause.setOnClickListener {
            if (c.isPlaying) c.pause() else c.play()
        }

        btnNext.setOnClickListener { c.seekToNextMediaItem() }
        btnPrevious.setOnClickListener { c.seekToPreviousMediaItem() }

        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    txtCurrentTime.text = formatTime(progress.toLong())
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) { isUserSeeking = true }
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                isUserSeeking = false
                controller?.seekTo(seekBar?.progress?.toLong() ?: 0L)
            }
        })
    }

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            updatePlayPauseIcon(isPlaying)
        }
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            updateUI()
            startSeekBarUpdater()
        }
        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_READY) {
                updateUI()
                startSeekBarUpdater()
            }
        }
    }

    private fun updateUI() {
        val c = controller ?: return
        val mediaItem = c.currentMediaItem ?: return
        val metadata = mediaItem.mediaMetadata

        txtSongTitle.text = metadata.title ?: "Unknown Title"
        txtArtist.text = metadata.artist ?: "Unknown Artist"

        val artworkData = metadata.artworkData
        if (artworkData != null) {
            val bitmap = BitmapFactory.decodeByteArray(artworkData, 0, artworkData.size)
            imgAlbumArt.setImageBitmap(bitmap)
        } else {
            imgAlbumArt.setImageResource(R.drawable.bg_cover_default)
        }

        val duration = c.duration
        if (duration > 0) {
            txtTotalTime.text = formatTime(duration)
            seekBar.max = duration.toInt()
        }

        updatePlayPauseIcon(c.isPlaying)
        updateFavoriteUI()
    }

    private fun updatePlayPauseIcon(isPlaying: Boolean) {
        if (isPlaying) {
            btnPlayPause.setImageResource(R.drawable.ic_pause)
        } else {
            btnPlayPause.setImageResource(R.drawable.ic_play)
        }
    }

    private fun getCurrentTrackUri(): String? {
        return controller?.currentMediaItem?.localConfiguration?.uri?.toString()
    }

    private fun updateFavoriteUI() {
        val currentUri = getCurrentTrackUri() ?: return
        val prefs = getSharedPreferences("MusicAppPrefs", Context.MODE_PRIVATE)
        val jsonString = prefs.getString("saved_tracks", null) ?: return
        var isFav = false

        try {
            val jsonArray = JSONArray(jsonString)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                if (obj.getString("uri") == currentUri) {
                    isFav = obj.optBoolean("isFavorite", false)
                    break
                }
            }
        } catch (e: Exception) { e.printStackTrace() }

        if (isFav) {
            btnFavoriteTop.setImageResource(R.drawable.ic_heart_filled)
            btnFavoriteTop.setColorFilter(Color.parseColor("#FF0000"))
            
            btnFavoriteBottom.setText("Đã thích")
            btnFavoriteBottom.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_heart_filled, 0, 0, 0)
            btnFavoriteBottom.compoundDrawableTintList = ColorStateList.valueOf(Color.parseColor("#FF0000"))
            btnFavoriteBottom.setTextColor(Color.parseColor("#FF0000"))
        } else {
            btnFavoriteTop.setImageResource(R.drawable.ic_heart_outline)
            btnFavoriteTop.setColorFilter(Color.parseColor("#8B3DFF"))
            
            btnFavoriteBottom.setText("Yêu thích")
            btnFavoriteBottom.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_heart_outline, 0, 0, 0)
            btnFavoriteBottom.compoundDrawableTintList = ColorStateList.valueOf(Color.parseColor("#5E35B1"))
            btnFavoriteBottom.setTextColor(Color.parseColor("#333333"))
        }
    }

    private fun toggleFavorite() {
        val currentUri = getCurrentTrackUri() ?: return
        val prefs = getSharedPreferences("MusicAppPrefs", Context.MODE_PRIVATE)
        val jsonString = prefs.getString("saved_tracks", null) ?: return

        try {
            val jsonArray = JSONArray(jsonString)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                if (obj.getString("uri") == currentUri) {
                    val currentFav = obj.optBoolean("isFavorite", false)
                    obj.put("isFavorite", !currentFav)
                    break
                }
            }
            prefs.edit().putString("saved_tracks", jsonArray.toString()).apply()
            updateFavoriteUI()
        } catch (e: Exception) { e.printStackTrace() }
    }

    private val seekBarUpdater = object : Runnable {
        override fun run() {
            val c = controller
            if (c != null && c.isConnected && c.isPlaying && !isUserSeeking) {
                val currentPos = c.currentPosition
                seekBar.progress = currentPos.toInt()
                txtCurrentTime.text = formatTime(currentPos)
            }
            handler.postDelayed(this, 1000)
        }
    }

    private fun startSeekBarUpdater() {
        handler.removeCallbacks(seekBarUpdater)
        handler.post(seekBarUpdater)
    }

    private fun formatTime(millis: Long): String {
        val seconds = (millis / 1000) % 60
        val minutes = (millis / (1000 * 60)) % 60
        return String.format("%02d:%02d", minutes, seconds)
    }

    override fun onDestroy() {
        handler.removeCallbacks(seekBarUpdater)
        controller?.removeListener(playerListener)
        if (::controllerFuture.isInitialized) {
            controllerFuture.addListener({
                MediaController.releaseFuture(controllerFuture)
            }, MoreExecutors.directExecutor())
        }
        super.onDestroy()
    }
}