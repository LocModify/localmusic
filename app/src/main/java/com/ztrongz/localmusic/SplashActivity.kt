package com.ztrongz.localmusic

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import androidx.appcompat.app.AppCompatActivity

/**
 * Màn hình chờ: logo Lunar Music (trăng đứng yên, vòng quay quanh trăng, nốt nhạc + sao chuyển động)
 * và chữ Lunar Music. Phần animation bên trong logo do LunarLogoLayout tự chạy.
 * Ở đây chỉ làm hiệu ứng xuất hiện:
 *  - Logo phóng to nhẹ + hiện dần (có độ nảy)
 *  - Chữ trượt từ dưới lên + hiện dần (chậm hơn logo một nhịp)
 *  - Quầng sáng tím phía sau thở nhẹ, cả khối logo "hít thở" rất nhẹ
 */
class SplashActivity : AppCompatActivity() {

    // ===== Các số có thể chỉnh =====
    private val logoInMs = 900L          // logo hiện ra mất bao lâu
    private val wordDelayMs = 450L       // chữ hiện chậm hơn logo bao lâu
    private val wordInMs = 700L          // chữ hiện ra mất bao lâu
    private val breatheScale = 1.03f     // khối logo phồng nhẹ tối đa khi "thở"
    private val totalMs = 2800L          // tổng thời gian ở màn hình chờ trước khi vào màn chính

    private val handler = Handler(Looper.getMainLooper())

    private fun dp(v: Float) = v * resources.displayMetrics.density

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        val group = findViewById<View>(R.id.splashLogoGroup)
        val logoFrame = findViewById<View>(R.id.splashLogoFrame)
        val wordFrame = findViewById<View>(R.id.splashWordFrame)
        val backGlow = findViewById<View>(R.id.splashBackGlow)

        // Trạng thái ban đầu (ẩn) trước khi chạy hiệu ứng
        logoFrame.alpha = 0f
        logoFrame.scaleX = 0.8f
        logoFrame.scaleY = 0.8f
        wordFrame.alpha = 0f
        wordFrame.translationY = dp(22f)
        backGlow.alpha = 0f

        // 1) Logo: phóng to + hiện dần, hơi nảy
        logoFrame.animate().alpha(1f).scaleX(1f).scaleY(1f)
            .setDuration(logoInMs).setInterpolator(OvershootInterpolator(1.1f)).start()

        // 2) Chữ: trượt lên + hiện dần
        wordFrame.animate().alpha(1f).translationY(0f)
            .setStartDelay(wordDelayMs).setDuration(wordInMs).setInterpolator(DecelerateInterpolator()).start()

        // 3) Quầng sáng phía sau: hiện dần rồi thở nhẹ
        backGlow.animate().alpha(0.55f).setDuration(1200L).withEndAction {
            ObjectAnimator.ofFloat(backGlow, View.ALPHA, 0.55f, 1f).apply {
                duration = 1800L
                repeatCount = ValueAnimator.INFINITE
                repeatMode = ValueAnimator.REVERSE
                interpolator = AccelerateDecelerateInterpolator()
                start()
            }
        }.start()

        // 4) Cả khối logo + chữ "hít thở" rất nhẹ sau khi đã hiện xong
        handler.postDelayed({
            if (!isFinishing) {
                for (prop in listOf(View.SCALE_X, View.SCALE_Y)) {
                    ObjectAnimator.ofFloat(group, prop, 1f, breatheScale).apply {
                        duration = 1600L
                        repeatCount = ValueAnimator.INFINITE
                        repeatMode = ValueAnimator.REVERSE
                        interpolator = AccelerateDecelerateInterpolator()
                        start()
                    }
                }
            }
        }, logoInMs + wordDelayMs)

        // Hết thời gian thì vào màn hình chính (chuyển cảnh mờ dần)
        handler.postDelayed({
            startActivity(Intent(this, MainActivity::class.java))
            @Suppress("DEPRECATION")
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
            finish()
        }, totalMs)
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}
