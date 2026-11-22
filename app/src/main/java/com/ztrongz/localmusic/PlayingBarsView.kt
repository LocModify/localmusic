package com.ztrongz.localmusic

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator

// 3 cột sóng âm bo tròn, nhảy lên xuống khi đang phát nhạc
class PlayingBarsView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#7C3AED")
        style = Paint.Style.FILL
    }
    private val rect = RectF()

    // Độ cao tương đối của 3 cột (0..1); đây cũng là độ cao khi đứng yên
    private val staticLevels = floatArrayOf(0.45f, 0.9f, 0.6f)
    private val levels = staticLevels.copyOf()

    // Thời gian mỗi nhịp của từng cột (ms): càng nhỏ càng nhanh. Mỗi cột một nhịp khác nhau cho tự nhiên
    private val durations = longArrayOf(520L, 680L, 600L)
    private val animators = arrayOfNulls<ValueAnimator>(3)

    private var playing = false
    private var attached = false

    // Bật / tắt chuyển động
    fun setPlaying(isPlaying: Boolean) {
        if (playing == isPlaying) return
        playing = isPlaying
        if (playing) startAnimators() else stopAnimators()
    }

    private fun startAnimators() {
        if (!attached) return
        for (i in 0 until 3) {
            if (animators[i]?.isRunning == true) continue
            animators[i] = ValueAnimator.ofFloat(0.25f, 1f).apply {
                duration = durations[i]
                startDelay = (i * 120).toLong()
                repeatCount = ValueAnimator.INFINITE
                repeatMode = ValueAnimator.REVERSE
                interpolator = AccelerateDecelerateInterpolator()
                addUpdateListener {
                    levels[i] = it.animatedValue as Float
                    invalidate()
                }
                start()
            }
        }
    }

    private fun stopAnimators() {
        for (i in 0 until 3) {
            animators[i]?.cancel()
            animators[i] = null
            levels[i] = staticLevels[i]
        }
        invalidate()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        attached = true
        if (playing) startAnimators()
    }

    override fun onDetachedFromWindow() {
        // Dừng animator để không rò rỉ bộ nhớ (vẫn nhớ cờ playing để chạy lại khi gắn vào)
        attached = false
        for (i in 0 until 3) {
            animators[i]?.cancel()
            animators[i] = null
        }
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        // 3 cột + 2 khoảng trống = 5 đơn vị chiều rộng
        val unit = width / 5f
        val radius = unit / 2f
        for (i in 0 until 3) {
            val barH = (height * levels[i]).coerceAtLeast(unit)
            val left = unit * (i * 2)
            val top = (height - barH) / 2f
            rect.set(left, top, left + unit, top + barH)
            canvas.drawRoundRect(rect, radius, radius, paint)
        }
    }
}