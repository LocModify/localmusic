package com.ztrongz.localmusic

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.animation.AnimationUtils
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity

class SplashActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        val logo = findViewById<ImageView>(R.id.splash_logo)

        // Hiệu ứng mờ dần (Fade in) cho logo
        val fadeIn = AnimationUtils.loadAnimation(this, android.R.anim.fade_in)
        fadeIn.duration = 1000 // 1 giây
        logo.startAnimation(fadeIn)

        // --- ĐOẠN CODE KÍCH HOẠT SÓNG NHẠC ---
        val equalizer = findViewById<EqualizerView>(R.id.equalizer_view)
        equalizer.startMusicAnimation() 
        // -------------------------------------

        // Đợi 2 giây rồi tự động chuyển vào màn hình chính (MainActivity)
        Handler(Looper.getMainLooper()).postDelayed({
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
            finish() // Đóng SplashActivity
        }, 2000)
    }
}