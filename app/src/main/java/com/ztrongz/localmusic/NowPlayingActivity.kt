package com.ztrongz.localmusic

import android.content.ComponentName
import android.graphics.BitmapFactory
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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

class NowPlayingActivity : AppCompatActivity() {

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

    private lateinit var controllerFuture: ListenableFuture<MediaController>
    private var controller: MediaController? = null
    private val handler = Handler(Looper.getMainLooper())
    private var isUserSeeking = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_now_playing)

        // Ánh xạ View từ XML
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

        // Xử lý nút thu nhỏ (Mũi tên xuống góc trái)
        btnMinimize.setOnClickListener {
            finish()
            overridePendingTransition(R.anim.slide_down, R.anim.slide_up)
        }

        // Kết nối với PlaybackService
        connectController()
    }

    private fun connectController() {
        val token = SessionToken(this, ComponentName(this, PlaybackService::class.java))
        controllerFuture = MediaController.Builder(this, token).buildAsync()
        controllerFuture.addListener({
            controller = controllerFuture.get()
            controller?.addListener(playerListener)
            
            // Cập nhật giao diện ngay khi kết nối thành công
            updateUI()
            startSeekBarUpdater()
            
            // Gắn sự kiện cho các nút điều khiển
            setupControlButtons()
        }, MoreExecutors.directExecutor())
    }

    private fun setupControlButtons() {
        val c = controller ?: return

        // Bấm Play/Pause
        btnPlayPause.setOnClickListener {
            if (c.isPlaying) c.pause() else c.play()
        }

        // Chuyển bài
        btnNext.setOnClickListener { c.seekToNextMediaItem() }
        btnPrevious.setOnClickListener { c.seekToPreviousMediaItem() }

        // Kéo thanh SeekBar để tua nhạc
        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    txtCurrentTime.text = formatTime(progress.toLong())
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                isUserSeeking = true
            }

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

        // Cập nhật tên bài hát và nghệ sĩ
        txtSongTitle.text = metadata.title ?: "Unknown Title"
        txtArtist.text = metadata.artist ?: "Unknown Artist"

        // Cập nhật ảnh bìa (từ artworkData)
        val artworkData = metadata.artworkData
        if (artworkData != null) {
            val bitmap = BitmapFactory.decodeByteArray(artworkData, 0, artworkData.size)
            imgAlbumArt.setImageBitmap(bitmap)
        } else {
            imgAlbumArt.setImageResource(R.drawable.bg_cover_default)
        }

        // Cập nhật tổng thời gian bài hát
        val duration = c.duration
        if (duration > 0) {
            txtTotalTime.text = formatTime(duration)
            seekBar.max = duration.toInt()
        }

        updatePlayPauseIcon(c.isPlaying)
    }

    private fun updatePlayPauseIcon(isPlaying: Boolean) {
        if (isPlaying) {
            // Đang phát -> hiển thị icon Pause
            btnPlayPause.setImageResource(android.R.drawable.ic_media_pause)
        } else {
            // Đang dừng -> hiển thị icon Play
            btnPlayPause.setImageResource(android.R.drawable.ic_media_play)
        }
    }

    // Tự động cập nhật thanh SeekBar mỗi 1 giây
    private val seekBarUpdater = object : Runnable {
        override fun run() {
            val c = controller
            if (c != null && c.isConnected && c.isPlaying && !isUserSeeking) {
                val currentPos = c.currentPosition
                seekBar.progress = currentPos.toInt()
                txtCurrentTime.text = formatTime(currentPos)
            }
            handler.postDelayed(this, 1000) // Lặp lại sau 1 giây
        }
    }

    private fun startSeekBarUpdater() {
        handler.removeCallbacks(seekBarUpdater)
        handler.post(seekBarUpdater)
    }

    // Hàm chuyển đổi mili-giây thành định dạng mm:ss
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