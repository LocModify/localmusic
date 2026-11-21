package com.ztrongz.localmusic

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.animation.AccelerateInterpolator
import androidx.constraintlayout.widget.ConstraintLayout
import kotlin.math.abs

class SwipeableConstraintLayout @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : ConstraintLayout(context, attrs, defStyleAttr) {

    private var startY = 0f
    private var startX = 0f
    private var isDragging = false
    
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val minFlingVelocity = ViewConfiguration.get(context).scaledMinimumFlingVelocity

    var onSwipeDownListener: (() -> Unit)? = null

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                startY = ev.rawY
                startX = ev.rawX
                isDragging = false
            }
            MotionEvent.ACTION_MOVE -> {
                val deltaY = ev.rawY - startY
                val deltaX = ev.rawX - startX
                
                // Chỉ kích hoạt kéo nếu vuốt xuống và hướng dọc nhiều hơn hướng ngang
                if (deltaY > touchSlop && deltaY > abs(deltaX)) {
                    isDragging = true
                    return true
                }
            }
        }
        return super.onInterceptTouchEvent(ev)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_MOVE -> {
                if (isDragging) {
                    val deltaY = event.rawY - startY
                    if (deltaY > 0) {
                        translationY = deltaY // Di chuyển view theo ngón tay
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (isDragging) {
                    isDragging = false
                    val deltaY = event.rawY - startY
                    val velocityY = event.y - startY // Ước lượng vận tốc

                    // Nếu kéo xuống quá 1/4 màn hình hoặc vuốt đủ mạnh
                    if (deltaY > height / 4f || velocityY > minFlingVelocity) {
                        // Trượt xuống và đóng
                        animate()
                            .translationY(height.toFloat())
                            .setDuration(250)
                            .setInterpolator(AccelerateInterpolator())
                            .withEndAction { onSwipeDownListener?.invoke() }
                            .start()
                    } else {
                        // Kéo chưa đủ xa -> Bật ngược trở lại vị trí cũ
                        animate()
                            .translationY(0f)
                            .setDuration(200)
                            .start()
                    }
                    return true
                }
            }
        }
        return super.onTouchEvent(event)
    }
}