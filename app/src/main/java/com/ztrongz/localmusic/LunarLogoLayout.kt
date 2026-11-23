package com.ztrongz.localmusic

import android.animation.Animator
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.LinearInterpolator
import android.widget.LinearLayout

/**
 * Khối logo "Lunar Music" ở header:
 *  - Mặt trăng ĐỨNG YÊN (chỉ có quầng sáng nền thở nhẹ)
 *  - Vòng quỹ đạo quay quanh mặt trăng (OrbitRingView x2: sau + trước)
 *  - Nốt nhạc bồng bềnh, lắc nhẹ
 *  - Các ngôi sao lấp lánh
 *  - Chữ Lunar Music tỏa sáng nhẹ
 * Tự chạy khi gắn vào màn hình và tự dừng khi gỡ ra (không cần sửa MainActivity).
 */
class LunarLogoLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : LinearLayout(context, attrs) {

    // ===== Các số có thể chỉnh =====
    private val orbitDurationMs = 9000L   // một vòng quay quanh trăng (ms), càng lớn càng chậm
    private val noteFloatDp = 2.5f        // nốt nhạc bay lên xuống bao nhiêu dp
    private val noteFloatMs = 2400L       // thời gian một nhịp bay
    private val noteSwayDeg = 4f          // độ lắc của nốt nhạc
    private val moonGlowMaxAlpha = 0.5f   // quầng sáng sau trăng sáng nhất (0..1)
    private val wordGlowMinAlpha = 0.3f   // quầng sáng chữ: mờ nhất
    private val wordGlowMaxAlpha = 1.0f   // quầng sáng chữ: sáng nhất

    private val running = mutableListOf<Animator>()

    private fun dp(v: Float) = v * resources.displayMetrics.density

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        // post để chắc chắn các view con đã có kích thước (cần cho tâm xoay)
        post { if (isAttachedToWindow) startAnimations() }
    }

    override fun onDetachedFromWindow() {
        stopAnimations()
        super.onDetachedFromWindow()
    }

    private fun loop(a: ValueAnimator, duration: Long, delay: Long = 0L, linear: Boolean = false) {
        a.duration = duration
        a.startDelay = delay
        a.repeatCount = ValueAnimator.INFINITE
        a.repeatMode = if (linear) ValueAnimator.RESTART else ValueAnimator.REVERSE
        a.interpolator = if (linear) LinearInterpolator() else AccelerateDecelerateInterpolator()
        running.add(a)
        a.start()
    }

    private fun startAnimations() {
        stopAnimations()

        // 1) Vòng quỹ đạo quay quanh trăng: cập nhật "phase" cho cả nửa sau lẫn nửa trước
        val ringBack = findViewById<OrbitRingView>(R.id.ringBack)
        val ringFront = findViewById<OrbitRingView>(R.id.ringFront)
        if (ringBack != null || ringFront != null) {
            val orbit = ValueAnimator.ofFloat(0f, 1f)
            orbit.addUpdateListener {
                val p = it.animatedValue as Float
                ringBack?.setPhase(p)
                ringFront?.setPhase(p)
            }
            loop(orbit, orbitDurationMs, linear = true)
        }

        // 2) Quầng sáng sau mặt trăng: chỉ đổi độ sáng, KHÔNG di chuyển (trăng đứng yên)
        findViewById<View>(R.id.ivLunarGlow)?.let { glow ->
            loop(ObjectAnimator.ofFloat(glow, View.ALPHA, 0.12f, moonGlowMaxAlpha), 2400L)
        }

        // 3) Nốt nhạc bồng bềnh + lắc nhẹ quanh phần đáy của nốt
        findViewById<View>(R.id.ivLunarNote)?.let { note ->
            note.pivotX = note.width * 0.61f
            note.pivotY = note.height * 0.60f
            loop(ObjectAnimator.ofFloat(note, View.TRANSLATION_Y, 0f, -dp(noteFloatDp)), noteFloatMs)
            loop(ObjectAnimator.ofFloat(note, View.ROTATION, -noteSwayDeg, noteSwayDeg), noteFloatMs + 700L)
            loop(ObjectAnimator.ofFloat(note, View.SCALE_X, 1f, 1.04f), noteFloatMs - 300L)
            loop(ObjectAnimator.ofFloat(note, View.SCALE_Y, 1f, 1.04f), noteFloatMs - 300L)
        }

        // 4) Các ngôi sao lấp lánh, mỗi sao một nhịp khác nhau cho tự nhiên
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

        // 5) Chữ Lunar Music: quầng sáng tím thở nhẹ phía sau chữ
        findViewById<View>(R.id.ivWordGlow)?.let { g ->
            loop(ObjectAnimator.ofFloat(g, View.ALPHA, wordGlowMinAlpha, wordGlowMaxAlpha), 2200L)
        }
    }

    private fun stopAnimations() {
        for (a in running) a.cancel()
        running.clear()
    }
}
