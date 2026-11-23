package com.ztrongz.localmusic

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.SweepGradient
import android.util.AttributeSet
import android.view.View
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Vòng quỹ đạo quanh mặt trăng (elip nghiêng) có 2 hạt bi chạy dọc vòng.
 * Dùng 2 bản: tag="back" (nửa sau, nằm DƯỚI mặt trăng) và tag="front" (nửa trước, nằm TRÊN mặt trăng)
 * để tạo cảm giác vòng thật sự quay vòng quanh trăng.
 * Toạ độ vẽ theo khung 1000x625 (cùng khung với ảnh trăng) rồi tự co theo kích thước view.
 */
class OrbitRingView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    // ===== Hình học vòng (đo từ ảnh logo, khung 1000x625) =====
    private val cx = 484f
    private val cy = 382f
    private val radiusA = 499f      // bán trục dài
    private val radiusB = 108f      // bán trục ngắn
    private val tiltDeg = -15.8f    // độ nghiêng (âm = nâng về bên phải)

    // ===== Số có thể chỉnh =====
    private val coreWidth = 13f     // độ dày nét vòng (đơn vị khung 1000px, ~1.3dp)
    private val glowWidth = 36f     // độ dày quầng sáng quanh vòng
    private val beadRadius = 19f    // cỡ hạt bi lớn

    private var phase = 0f          // 0..1, do LunarLogoLayout cập nhật liên tục

    private val rect = RectF(cx - radiusA, cy - radiusB, cx + radiusA, cy + radiusB)
    private val sweepMatrix = Matrix()

    private val corePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = coreWidth
        strokeCap = Paint.Cap.ROUND
    }
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = glowWidth
        alpha = 55
    }
    private val beadFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.WHITE
    }
    private val beadStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 6f
        color = Color.parseColor("#8B5CF6")
    }

    // Màu vòng: tím đậm -> tím nhạt -> tím đậm, xoay theo phase nên thấy "dòng sáng" chạy quanh vòng
    private val sweep = SweepGradient(
        cx, cy,
        intArrayOf(0xFF7C3AED.toInt(), 0xFFC9B6FF.toInt(), 0xFF7C3AED.toInt(), 0xFFC9B6FF.toInt(), 0xFF7C3AED.toInt()),
        floatArrayOf(0f, 0.25f, 0.5f, 0.75f, 1f)
    )

    fun setPhase(p: Float) {
        phase = p
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val front = tag == "front"
        val s = width / 1000f
        canvas.save()
        canvas.scale(s, s)
        canvas.rotate(tiltDeg, cx, cy)

        // Chỉ vẽ nửa dưới (trước) hoặc nửa trên (sau) của elip
        if (front) canvas.clipRect(cx - 900f, cy, cx + 900f, cy + 700f)
        else canvas.clipRect(cx - 900f, cy - 700f, cx + 900f, cy)

        sweepMatrix.setRotate(phase * 360f, cx, cy)
        sweep.setLocalMatrix(sweepMatrix)
        corePaint.shader = sweep
        glowPaint.shader = sweep
        glowPaint.alpha = 55

        canvas.drawOval(rect, glowPaint)
        canvas.drawOval(rect, corePaint)

        // Hai hạt bi chạy quanh vòng; ở nửa trước thì to hơn một chút cho có chiều sâu
        drawBead(canvas, phase * 2f * PI.toFloat(), beadRadius)
        drawBead(canvas, phase * 2f * PI.toFloat() + PI.toFloat(), beadRadius * 0.65f)

        canvas.restore()
    }

    private fun drawBead(canvas: Canvas, t: Float, r: Float) {
        val x = cx + radiusA * cos(t)
        val y = cy + radiusB * sin(t)
        val k = 1f + 0.25f * sin(t)
        canvas.drawCircle(x, y, r * k, beadFill)
        canvas.drawCircle(x, y, r * k, beadStroke)
    }
}
