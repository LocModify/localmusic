package com.ztrongz.localmusic

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator

class EqualizerView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {
    private val paint = Paint().apply {
        color = Color.WHITE
        strokeWidth = 12f
        strokeCap = Paint.Cap.ROUND
    }
    private val barCount = 5
    private val heights = FloatArray(barCount) { 0.2f }
    private val animators = mutableListOf<ValueAnimator>()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val barWidth = width / (barCount * 2f)
        val spacing = barWidth
        for (i in 0 until barCount) {
            val x = spacing + i * (barWidth + spacing)
            val centerY = height / 2f
            val halfHeight = (height / 2f) * heights[i]
            canvas.drawLine(x, centerY - halfHeight, x, centerY + halfHeight, paint)
        }
    }

    fun startAnimation() {
        for (i in 0 until barCount) {
            val animator = ValueAnimator.ofFloat(0.2f, 1f, 0.2f).apply {
                duration = 500L + (i * 150L) // Tốc độ nhấp nhô so le nhau
                repeatCount = ValueAnimator.INFINITE
                interpolator = LinearInterpolator()
                addUpdateListener {
                    heights[i] = it.animatedValue as Float
                    invalidate()
                }
            }
            animator.start()
            animators.add(animator)
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        animators.forEach { it.cancel() }
    }
}