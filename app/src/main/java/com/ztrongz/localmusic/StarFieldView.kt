package com.ztrongz.localmusic

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.PI
import kotlin.math.sin

/**
 * Nền bụi sao lấp lánh, trôi chậm lên trên (dùng cho màn hình chờ).
 * Tự chạy khi gắn vào màn hình, tự dừng khi gỡ ra.
 */
class StarFieldView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    // ===== Các số có thể chỉnh =====
    private val starCount = 42            // số ngôi sao
    private val cycleMs = 14000L          // một chu kỳ trôi (ms), càng lớn càng chậm
    private val driftFraction = 0.12f     // mỗi chu kỳ trôi lên bao nhiêu phần chiều cao màn hình

    private class Star(
        val x: Float, val y: Float, val radiusDp: Float,
        val phase: Float, val twinkles: Int, val color: Int
    )

    private val stars: List<Star>
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var t = 0f
    private var animator: ValueAnimator? = null

    init {
        val rnd = java.util.Random(11)
        val colors = intArrayOf(Color.WHITE, Color.parseColor("#E9D5FF"), Color.parseColor("#C4B5FD"))
        stars = List(starCount) {
            Star(
                x = rnd.nextFloat(), y = rnd.nextFloat(),
                radiusDp = 0.6f + rnd.nextFloat() * 1.3f,
                phase = rnd.nextFloat(), twinkles = 1 + rnd.nextInt(3),
                color = colors[rnd.nextInt(colors.size)]
            )
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = cycleMs
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener { t = it.animatedValue as Float; invalidate() }
            start()
        }
    }

    override fun onDetachedFromWindow() {
        animator?.cancel()
        animator = null
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val density = resources.displayMetrics.density
        for (s in stars) {
            // trôi lên rồi quay vòng lại từ dưới
            var y = s.y - t * driftFraction
            y -= Math.floor(y.toDouble()).toFloat()
            // nhấp nháy mượt: 0.15 .. 1.0
            val tw = 0.5f + 0.5f * sin(2.0 * PI * (t * s.twinkles + s.phase)).toFloat()
            val a = 0.15f + 0.85f * tw
            paint.color = s.color
            paint.alpha = (a * 255).toInt()
            canvas.drawCircle(s.x * w, y * h, s.radiusDp * density, paint)
        }
    }
}
