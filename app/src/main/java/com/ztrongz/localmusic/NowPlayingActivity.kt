package com.ztrongz.localmusic

import android.os.Bundle
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity

class NowPlayingActivity : AppCompatActivity() {

    private lateinit var btnMinimize: ImageView
    private lateinit var btnPlayPause: ImageView
    private var isPlaying = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_now_playing)

        btnMinimize = findViewById(R.id.btnMinimize)
        btnPlayPause = findViewById(R.id.btnPlayPause)

        // Xử lý nút thu nhỏ
        btnMinimize.setOnClickListener {
            finish()
            overridePendingTransition(R.anim.slide_down, R.anim.slide_up)
        }

        // Xử lý nút Play/Pause
        btnPlayPause.setOnClickListener {
            if (isPlaying) {
                btnPlayPause.setImageResource(android.R.drawable.ic_media_play)
                isPlaying = false
                // TODO: Gọi lệnh pause() 
            } else {
                btnPlayPause.setImageResource(android.R.drawable.ic_media_pause)
                isPlaying = true
                // TODO: Gọi lệnh play()
            }
        }
    }
}