package com.ztrongz.localmusic

import android.animation.Animator
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Khối logo "Lunar Music" ở header: trăng bồng bềnh, quầng sáng thở,
 * các ngôi sao lấp lánh và chữ Lunar tỏa sáng nhẹ.
 * Tự chạy khi gắn vào màn hình và tự dừng khi gỡ ra (không cần sửa MainActivity).
 */
class LunarLogoLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : LinearLayout(context, attrs) {

    // ===== Các số có thể chỉnh =====
    private val floatDistanceDp = 3f      // trăng bay lên xuống bao nhiêu dp
    private val floatDurationMs = 2800L   // thời gian một nhịp bay (ms)
    private val swayDegrees = 2.5f        // độ lắc nhẹ của trăng
    private val glowMaxAlpha = 0.55f      // quầng sáng sáng nhất (0..1)
    private val glowMaxScale = 1.18f      // quầng sáng nở to nhất
    private val textGlowMinDp = 3f        // chữ Lunar: bóng sáng nhỏ nhất
    private val textGlowMaxDp = 12f       // chữ Lunar: bóng sáng lớn nhất

    private val running = mutableListOf<Animator>()

    private fun dp(v: Float) = v * resources.displayMetrics.density

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        startAnimations()
    }

    override fun onDetachedFromWindow() {
        stopAnimations()
        super.onDetachedFromWindow()
    }

    private fun loop(a: ValueAnimator, duration: Long, delay: Long = 0L) {
        a.duration = duration
        a.startDelay = delay
        a.repeatCount = ValueAnimator.INFINITE
        a.repeatMode = ValueAnimator.REVERSE
        a.interpolator = AccelerateDecelerateInterpolator()
        running.add(a)
        a.start()
    }

    private fun startAnimations() {
        stopAnimations()

        // 1) Trăng bồng bềnh + lắc nhẹ
        findViewById<View>(R.id.ivLunarMoon)?.let { moon ->
            loop(ObjectAnimator.ofFloat(moon, View.TRANSLATION_Y, 0f, -dp(floatDistanceDp)), floatDurationMs)
            loop(ObjectAnimator.ofFloat(moon, View.ROTATION, -swayDegrees, swayDegrees), floatDurationMs + 900L)
        }

        // 2) Quầng sáng thở (cùng bay với trăng để luôn dính vào trăng)
        findViewById<View>(R.id.ivLunarGlow)?.let { glow ->
            loop(ObjectAnimator.ofFloat(glow, View.TRANSLATION_Y, 0f, -dp(floatDistanceDp)), floatDurationMs)
            loop(ObjectAnimator.ofFloat(glow, View.ROTATION, -swayDegrees, swayDegrees), floatDurationMs + 900L)
            loop(ObjectAnimator.ofFloat(glow, View.ALPHA, 0.1f, glowMaxAlpha), 2200L)
            loop(ObjectAnimator.ofFloat(glow, View.SCALE_X, 1.02f, glowMaxScale), 2200L)
            loop(ObjectAnimator.ofFloat(glow, View.SCALE_Y, 1.02f, glowMaxScale), 2200L)
        }

        // 3) Các ngôi sao lấp lánh, mỗi sao một nhịp khác nhau cho tự nhiên
        val sparkles = listOf(
            Triple(R.id.ivSparkle1, 1500L, 0L),
            Triple(R.id.ivSparkle2, 1900L, 400L),
            Triple(R.id.ivSparkle3, 1700L, 900L)
        )
        for ((id, duration, delay) in sparkles) {
            val s = findViewById<View>(id) ?: continue
            loop(ObjectAnimator.ofFloat(s, View.ALPHA, 0f, 1f), duration, delay)
            loop(ObjectAnimator.ofFloat(s, View.SCALE_X, 0.3f, 1.15f), duration, delay)
            loop(ObjectAnimator.ofFloat(s, View.SCALE_Y, 0.3f, 1.15f), duration, delay)
            loop(ObjectAnimator.ofFloat(s, View.ROTATION, 0f, 90f), duration, delay)
        }

        // 4) Chữ Lunar tỏa sáng nhẹ (bóng tím thở)
        findViewById<TextView>(R.id.tvLunar)?.let { tv ->
            val g = ValueAnimator.ofFloat(textGlowMinDp, textGlowMaxDp)
            g.addUpdateListener {
                tv.setShadowLayer(dp(it.animatedValue as Float), 0f, 0f, 0xCC8B5CF6.toInt())
            }
            loop(g, 2200L)
        }
    }

    private fun stopAnimations() {
        for (a in running) a.cancel()
        running.clear()
    }
}
