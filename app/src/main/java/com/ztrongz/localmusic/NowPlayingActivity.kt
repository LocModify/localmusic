package com.ztrongz.localmusic

import android.content.ComponentName
import android.content.Context
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.LinearInterpolator
import android.widget.Button
import android.widget.ImageView
import android.widget.PopupWindow
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import org.json.JSONArray
import java.io.ByteArrayOutputStream

class NowPlayingActivity : AppCompatActivity() {

    private lateinit var rootLayout: SwipeableConstraintLayout
    private lateinit var bgOverlay: View
    private lateinit var contentContainer: View
    private lateinit var btnMinimize: ImageView
    private lateinit var btnPlayPause: ImageView
    private lateinit var btnNext: ImageView
    private lateinit var btnPrevious: ImageView
    private lateinit var btnShuffle: ImageView
    private lateinit var btnRepeat: ImageView
    private lateinit var imgAlbumArt: ImageView
    private lateinit var txtSongTitle: TextView
    private lateinit var txtArtist: TextView
    private lateinit var txtCurrentTime: TextView
    private lateinit var txtTotalTime: TextView
    private lateinit var seekBar: SeekBar
    private lateinit var btnFavoriteTop: ImageView
    private lateinit var btnFavoriteBottom: Button

    // Hiệu ứng ảnh bìa
    private val effectAnimators = mutableListOf<ObjectAnimator>()
    private var coverSpinAnimator: ObjectAnimator? = null
    private var playGlowAnimator: ObjectAnimator? = null

    private lateinit var controllerFuture: ListenableFuture<MediaController>
    private var controller: MediaController? = null
    private val handler = Handler(Looper.getMainLooper())
    private var isUserSeeking = false

    // Biến quản lý Popup thông báo để không bị đè lên nhau
    private var customPopup: PopupWindow? = null

    private val defaultArtworkBytes: ByteArray by lazy {
        val bmp = BitmapFactory.decodeResource(resources, R.drawable.bg_cover_default)
        val stream = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.JPEG, 90, stream)
        stream.toByteArray()
    }

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
        btnShuffle = findViewById(R.id.btnShuffle)
        btnRepeat = findViewById(R.id.btnRepeat)
        imgAlbumArt = findViewById(R.id.imgAlbumArt)
        txtSongTitle = findViewById(R.id.txtSongTitle)
        txtArtist = findViewById(R.id.txtArtist)
        txtCurrentTime = findViewById(R.id.txtCurrentTime)
        txtTotalTime = findViewById(R.id.txtTotalTime)
        seekBar = findViewById(R.id.seekBar)
        btnFavoriteTop = findViewById(R.id.btnFavoriteTop)
        btnFavoriteBottom = findViewById(R.id.btnFavoriteBottom)

        setupCoverEffects()

        btnMinimize.setOnClickListener { closeActivity() }

        rootLayout.onSwipeDownListener = {
            closeActivity()
        }

        rootLayout.onDragProgressListener = { progress ->
            bgOverlay.alpha = progress
            contentContainer.alpha = progress
        }

        btnFavoriteTop.setOnClickListener { toggleFavorite() }
        btnFavoriteBottom.setOnClickListener { toggleFavorite() }

        connectController()
    }

    private fun setupCoverEffects() {
        val density = resources.displayMetrics.density

        coverSpinAnimator = ObjectAnimator.ofFloat(findViewById<View>(R.id.cardCoverInner), View.ROTATION, 0f, 360f).apply {
            duration = 20000
            interpolator = LinearInterpolator()
            repeatCount = ObjectAnimator.INFINITE
            start()
            pause()
        }
        effectAnimators.add(coverSpinAnimator!!)

        playGlowAnimator = ObjectAnimator.ofPropertyValuesHolder(
            findViewById<View>(R.id.vPlayGlow),
            PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 1.12f),
            PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 1.12f),
            PropertyValuesHolder.ofFloat(View.ALPHA, 1f, 0.55f)
        ).apply {
            duration = 1400
            interpolator = AccelerateDecelerateInterpolator()
            repeatCount = ObjectAnimator.INFINITE
            repeatMode = ObjectAnimator.REVERSE
            start()
            pause()
        }
        effectAnimators.add(playGlowAnimator!!)

        val orbit1 = findViewById<View>(R.id.vOrbit1)
        val orbit2 = findViewById<View>(R.id.vOrbit2)
        effectAnimators.add(spin(orbit1, orbit1.rotation, orbit1.rotation + 360f, 40000))
        effectAnimators.add(spin(orbit2, orbit2.rotation, orbit2.rotation - 360f, 55000))

        val glow = findViewById<View>(R.id.vGlow)
        for (prop in listOf(View.SCALE_X, View.SCALE_Y)) {
            val a = ObjectAnimator.ofFloat(glow, prop, 1f, 1.06f).apply {
                duration = 1800
                interpolator = AccelerateDecelerateInterpolator()
                repeatCount = ObjectAnimator.INFINITE
                repeatMode = ObjectAnimator.REVERSE
                start()
            }
            effectAnimators.add(a)
        }

        val notes = listOf(R.id.note1, R.id.note2, R.id.note3, R.id.note4)
        val durations = listOf(2200L, 2600L, 3000L, 2400L)
        notes.forEachIndexed { i, id ->
            val v = findViewById<View>(id)
            val baseY = v.translationY
            val a = ObjectAnimator.ofFloat(v, View.TRANSLATION_Y, baseY, baseY - 14 * density).apply {
                duration = durations[i]
                interpolator = AccelerateDecelerateInterpolator()
                repeatCount = ObjectAnimator.INFINITE
                repeatMode = ObjectAnimator.REVERSE
                start()
            }
            effectAnimators.add(a)
        }
    }

    private fun spin(v: View, from: Float, to: Float, ms: Long): ObjectAnimator =
        ObjectAnimator.ofFloat(v, View.ROTATION, from, to).apply {
            duration = ms
            interpolator = LinearInterpolator()
            repeatCount = ObjectAnimator.INFINITE
            start()
        }

    private fun closeActivity() {
        finish()
        overridePendingTransition(0, R.anim.slide_down)
    }

    private fun connectController() {
        val token = SessionToken(this, ComponentName(this, PlaybackService::class.java))
        controllerFuture = MediaController.Builder(this, token).buildAsync()
        controllerFuture.addListener({
            controller = controllerFuture.get()
            controller?.addListener(playerListener)
            
            // Gửi lệnh khôi phục trạng thái dừng cuối bài
            updatePauseAtEnd(controller?.repeatMode == Player.REPEAT_MODE_OFF)
            
            updateUI()
            startSeekBarUpdater()
            setupControlButtons()
        }, MoreExecutors.directExecutor())
    }

    // Hàm gửi lệnh xuống Service để bật/tắt tính năng dừng cuối bài (Đã sửa)
    private fun updatePauseAtEnd(pause: Boolean) {
        controller?.sendCustomCommand(
            SessionCommand("SET_PAUSE_AT_END", Bundle.EMPTY),
            Bundle().apply { putBoolean("pause", pause) }
        )
    }

    private fun setupControlButtons() {
        val c = controller ?: return

        btnPlayPause.setOnClickListener {
            if (c.isPlaying) c.pause() else c.play()
        }

        btnNext.setOnClickListener { c.seekToNextMediaItem() }
        btnPrevious.setOnClickListener { c.seekToPreviousMediaItem() }

        // Xử lý nút Shuffle
        btnShuffle.setOnClickListener {
            val newState = !c.shuffleModeEnabled
            c.shuffleModeEnabled = newState
            updateShuffleRepeatUI(newState, c.repeatMode)
            
            val msg = if (newState) "Bật xáo trộn" else "Tắt xáo trộn"
            showCustomPopup(msg)
        }

        // Xử lý nút Repeat
        btnRepeat.setOnClickListener {
            val nextMode = when (c.repeatMode) {
                Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                else -> Player.REPEAT_MODE_OFF
            }
            c.repeatMode = nextMode
            
            // Gửi lệnh bật/tắt dừng cuối bài
            updatePauseAtEnd(nextMode == Player.REPEAT_MODE_OFF)
            
            updateShuffleRepeatUI(c.shuffleModeEnabled, nextMode)

            val msg = when (nextMode) {
                Player.REPEAT_MODE_OFF -> "Lặp lại tắt"
                Player.REPEAT_MODE_ALL -> "Lặp lại tất cả"
                Player.REPEAT_MODE_ONE -> "Lặp lại 1 bài"
                else -> "Lặp lại tắt"
            }
            showCustomPopup(msg)
        }

        updateShuffleRepeatUI(c.shuffleModeEnabled, c.repeatMode)

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

    private fun showCustomPopup(message: String) {
        customPopup?.dismiss() 

        val inflater = getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        val layout = inflater.inflate(R.layout.toast_layout, null)
        layout.findViewById<TextView>(R.id.tvToastMessage).text = message

        customPopup = PopupWindow(
            layout,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            false
        )
        customPopup?.elevation = 10f
        customPopup?.showAtLocation(rootLayout, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL, 0, 250)

        Handler(Looper.getMainLooper()).postDelayed({ 
            customPopup?.dismiss() 
        }, 1500)
    }

    private fun updateShuffleRepeatUI(isShuffleOn: Boolean, repeatMode: Int) {
        val grayColor = Color.parseColor("#9E9E9E")
        val purpleColor = Color.parseColor("#7C3AED")

        btnShuffle.imageTintList = ColorStateList.valueOf(if (isShuffleOn) purpleColor else grayColor)

        when (repeatMode) {
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
        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
            updateShuffleRepeatUI(shuffleModeEnabled, controller?.repeatMode ?: Player.REPEAT_MODE_OFF)
        }

        override fun onRepeatModeChanged(repeatMode: Int) {
            updateShuffleRepeatUI(controller?.shuffleModeEnabled ?: false, repeatMode)
        }
    }

    private fun updateUI() {
        val c = controller ?: return
        val mediaItem = c.currentMediaItem ?: return
        val metadata = mediaItem.mediaMetadata

        txtSongTitle.text = metadata.title ?: "Unknown Title"
        txtArtist.text = metadata.artist ?: "Unknown Artist"

        val artworkData = metadata.artworkData
        if (artworkData == null || artworkData.contentEquals(defaultArtworkBytes)) {
            imgAlbumArt.setImageResource(R.drawable.bg_cover_default)
        } else {
            val decoded = BitmapFactory.decodeByteArray(artworkData, 0, artworkData.size)
            val bitmap = if (decoded != null) BitmapUtils.trimBlackBars(decoded) else null
            if (bitmap != null) imgAlbumArt.setImageBitmap(bitmap)
            else imgAlbumArt.setImageResource(R.drawable.bg_cover_default)
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
        val spin = coverSpinAnimator
        if (spin != null) {
            if (isPlaying && spin.isPaused) spin.resume()
            if (!isPlaying && spin.isRunning && !spin.isPaused) spin.pause()
        }
        val glow = playGlowAnimator
        if (glow != null) {
            if (isPlaying && glow.isPaused) glow.resume()
            if (!isPlaying && glow.isRunning && !glow.isPaused) {
                glow.pause()
            }
        }
        if (isPlaying) {
            btnPlayPause.setImageResource(R.drawable.ic_np_pause)
        } else {
            btnPlayPause.setImageResource(R.drawable.ic_np_play)
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
            btnFavoriteTop.setColorFilter(Color.parseColor("#FF3B5C"))
            
            btnFavoriteBottom.setText("Đã thích")
            btnFavoriteBottom.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_heart_filled, 0, 0, 0)
            btnFavoriteBottom.compoundDrawableTintList = ColorStateList.valueOf(Color.parseColor("#FF3B5C"))
            btnFavoriteBottom.setTextColor(Color.parseColor("#FF3B5C"))
        } else {
            btnFavoriteTop.setImageResource(R.drawable.ic_heart_outline)
            btnFavoriteTop.setColorFilter(Color.parseColor("#B45CFF"))
            
            btnFavoriteBottom.setText("Yêu thích")
            btnFavoriteBottom.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_heart_outline, 0, 0, 0)
            btnFavoriteBottom.compoundDrawableTintList = ColorStateList.valueOf(Color.parseColor("#B45CFF"))
            btnFavoriteBottom.setTextColor(Color.parseColor("#4A2A7A"))
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

    private fun formatTime(millis: Long): String = TimeUtils.format(millis)

    override fun onDestroy() {
        effectAnimators.forEach { it.cancel() }
        effectAnimators.clear()
        handler.removeCallbacks(seekBarUpdater)
        customPopup?.dismiss()
        controller?.removeListener(playerListener)
        if (::controllerFuture.isInitialized) {
            controllerFuture.addListener({
                MediaController.releaseFuture(controllerFuture)
            }, MoreExecutors.directExecutor())
        }
        super.onDestroy()
    }
}