package com.ztrongz.localmusic

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Intent
import android.graphics.Rect
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator
import androidx.appcompat.app.AppCompatActivity

/**
 * Màn hình chờ (KHÔNG dùng hiệu ứng phóng to/thu nhỏ). Trình tự xuất hiện:
 *  1) Nền bụi sao + quầng sáng tím hiện dần
 *  2) Mặt trăng nổi lên từ dưới (trượt lên + hiện dần)
 *  3) Vòng quỹ đạo hiện dần, rồi nốt nhạc hiện dần
 *  4) Chữ Lunar Music hiện ra theo kiểu quét từ trái sang phải, có tia sáng chạy theo mép
 *  5) Hết giờ: cả màn hình mờ dần rồi vào màn hình chính
 * Animation liên tục bên trong logo (vòng quay, nốt nhạc bồng bềnh, sao lấp lánh) do LunarLogoLayout tự chạy.
 */
class SplashActivity : AppCompatActivity() {

    // ===== Các số có thể chỉnh (ms) =====
    private val moonDelay = 150L        // trăng bắt đầu nổi lên
    private val moonDuration = 1000L
    private val ringDelay = 650L        // vòng quỹ đạo hiện
    private val noteDelay = 950L        // nốt nhạc hiện
    private val wordDelay = 1100L       // chữ bắt đầu được quét ra
    private val wordDuration = 900L
    private val totalMs = 3100L         // tổng thời gian ở màn hình chờ
    private val fadeOutMs = 350L        // thời gian mờ dần khi thoát
    private val moonRiseDp = 22f        // trăng nổi lên từ thấp hơn bao nhiêu dp

    private val handler = Handler(Looper.getMainLooper())

    private fun dp(v: Float) = v * resources.displayMetrics.density

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        val stars = findViewById<View>(R.id.splashStars)
        val backGlow = findViewById<View>(R.id.splashBackGlow)
        val moon = findViewById<View>(R.id.ivLunarMoon)
        val note = findViewById<View>(R.id.ivLunarNote)
        val ringBack = findViewById<View>(R.id.ringBack)
        val ringFront = findViewById<View>(R.id.ringFront)
        val word = findViewById<View>(R.id.ivWord)
        val wordGlow = findViewById<View>(R.id.ivWordGlow)
        val spark = findViewById<View>(R.id.splashWipeSpark)

        // Trạng thái ban đầu: ẩn hết (alpha không bị LunarLogoLayout động tới ở các view này)
        stars.alpha = 0f
        backGlow.alpha = 0f
        moon.alpha = 0f
        moon.translationY = dp(moonRiseDp)
        note.alpha = 0f
        ringBack.alpha = 0f
        ringFront.alpha = 0f
        word.alpha = 0f
        word.clipBounds = Rect(0, 0, 0, 0)
        wordGlow.clipBounds = Rect(0, 0, 0, 0)

        // 1) Nền sao + quầng sáng tím
        stars.animate().alpha(1f).setDuration(900L).start()
        backGlow.animate().alpha(0.55f).setDuration(1300L).withEndAction {
            ObjectAnimator.ofFloat(backGlow, View.ALPHA, 0.55f, 1f).apply {
                duration = 1800L
                repeatCount = ValueAnimator.INFINITE
                repeatMode = ValueAnimator.REVERSE
                interpolator = AccelerateDecelerateInterpolator()
                start()
            }
        }.start()

        // 2) Trăng nổi lên
        moon.animate().alpha(1f).translationY(0f)
            .setStartDelay(moonDelay).setDuration(moonDuration).setInterpolator(DecelerateInterpolator(1.6f)).start()

        // 3) Vòng quỹ đạo rồi nốt nhạc hiện dần (chỉ đổi alpha để không đụng animation của logo)
        ringBack.animate().alpha(1f).setStartDelay(ringDelay).setDuration(700L).start()
        ringFront.animate().alpha(1f).setStartDelay(ringDelay).setDuration(700L).start()
        note.animate().alpha(1f).setStartDelay(noteDelay).setDuration(700L).start()

        // 4) Chữ được quét ra từ trái sang phải + tia sáng chạy theo mép quét
        word.alpha = 1f
        word.post {
            val w = word.width
            val h = word.height
            spark.translationX = 0f
            ValueAnimator.ofFloat(0f, 1f).apply {
                startDelay = wordDelay
                duration = wordDuration
                interpolator = AccelerateDecelerateInterpolator()
                addUpdateListener {
                    val p = it.animatedValue as Float
                    val r = Rect(0, 0, (w * p).toInt(), h)
                    word.clipBounds = r
                    wordGlow.clipBounds = r
                    spark.translationX = w * p
                    // tia sáng sáng rõ giữa chừng, tắt dần ở cuối
                    spark.alpha = if (p < 0.85f) 1f else (1f - p) / 0.15f
                }
                doOnEndClear(word, wordGlow, spark)
                start()
            }
        }

        // 5) Hết giờ: mờ dần rồi vào màn hình chính
        handler.postDelayed({
            findViewById<View>(android.R.id.content).animate().alpha(0f).setDuration(fadeOutMs).withEndAction {
                startActivity(Intent(this, MainActivity::class.java))
                @Suppress("DEPRECATION")
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
                finish()
            }.start()
        }, totalMs - fadeOutMs)
    }

    // Kết thúc quét: bỏ giới hạn cắt để chữ và quầng sáng hiện đủ
    private fun ValueAnimator.doOnEndClear(word: View, glow: View, spark: View) {
        addListener(object : android.animation.AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: android.animation.Animator) {
                word.clipBounds = null
                glow.clipBounds = null
                spark.alpha = 0f
            }
        })
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}
