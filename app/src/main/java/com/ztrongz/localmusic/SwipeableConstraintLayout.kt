package com.ztrongz.localmusic

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.VelocityTracker
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
    private var velocityTracker: VelocityTracker? = null

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val minFlingVelocity = ViewConfiguration.get(context).scaledMinimumFlingVelocity

    var onSwipeDownListener: (() -> Unit)? = null
    var onDragProgressListener: ((Float) -> Unit)? = null

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (velocityTracker == null) {
            velocityTracker = VelocityTracker.obtain()
        }
        velocityTracker?.addMovement(ev)

        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                startY = ev.rawY
                startX = ev.rawX
                isDragging = false
            }
            MotionEvent.ACTION_MOVE -> {
                val deltaY = ev.rawY - startY
                val deltaX = ev.rawX - startX

                // Phát hiện thao tác vuốt xuống (DeltaY > 0 và hướng dọc > hướng ngang)
                if (deltaY > touchSlop && deltaY > abs(deltaX)) {
                    if (!isDragging) {
                        isDragging = true
                        // Gửi sự kiện HỦY đến các View con để chúng không xử lý tiếp (như SeekBar)
                        val cancelEvent = MotionEvent.obtain(ev)
                        cancelEvent.action = MotionEvent.ACTION_CANCEL
                        super.dispatchTouchEvent(cancelEvent)
                        cancelEvent.recycle()
                    }
                    
                    translationY = deltaY
                    
                    val progress = (1f - (deltaY / height)).coerceIn(0f, 1f)
                    onDragProgressListener?.invoke(progress)
                    
                    return true // Chặn các view con không cho chúng nhận sự kiện nữa
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (isDragging) {
                    isDragging = false
                    velocityTracker?.computeCurrentVelocity(1000)
                    val velocityY = velocityTracker?.yVelocity ?: 0f
                    val deltaY = ev.rawY - startY

                    // Nếu kéo quá 1/4 màn hình hoặc vuốt mạnh
                    if (deltaY > height / 4f || velocityY > minFlingVelocity) {
                        animate()
                            .translationY(height.toFloat())
                            .alpha(0f)
                            .setDuration(250)
                            .setInterpolator(AccelerateInterpolator())
                            .withEndAction { onSwipeDownListener?.invoke() }
                            .start()
                    } else {
                        // Bật ngược lại
                        animate()
                            .translationY(0f)
                            .alpha(1f)
                            .setDuration(200)
                            .withEndAction { onDragProgressListener?.invoke(1f) }
                            .start()
                    }
                    velocityTracker?.recycle()
                    velocityTracker = null
                    return true
                }
            }
        }
        return super.dispatchTouchEvent(ev)
    }
}