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
    // Callback trả về % độ mờ (từ 1.0 giảm về 0.0)
    var onDragProgressListener: ((Float) -> Unit)? = null

    init {
        // Đảm bảo layout có thể nhận sự kiện chạm
        isClickable = true
        isFocusable = true
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                startY = ev.rawY
                startX = ev.rawX
                isDragging = false
                velocityTracker?.recycle()
                velocityTracker = VelocityTracker.obtain()
                velocityTracker?.addMovement(ev)
            }
            MotionEvent.ACTION_MOVE -> {
                velocityTracker?.addMovement(ev)
                val deltaY = ev.rawY - startY
                val deltaX = ev.rawX - startX

                // Phát hiện thao tác kéo xuống (DeltaY > 0 và hướng dọc > hướng ngang)
                if (deltaY > touchSlop && deltaY > abs(deltaX)) {
                    isDragging = true
                    // Trả về true để "cướp" sự kiện từ các View con (như ảnh bìa, nút bấm)
                    return true
                }
            }
        }
        return super.onInterceptTouchEvent(ev)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        velocityTracker?.addMovement(event)

        when (event.actionMasked) {
            MotionEvent.ACTION_MOVE -> {
                if (isDragging) {
                    val deltaY = event.rawY - startY
                    if (deltaY > 0) {
                        translationY = deltaY // Kéo màn hình xuống theo tay
                        
                        // Tính toán độ mờ
                        val progress = (1f - (deltaY / height)).coerceIn(0f, 1f)
                        onDragProgressListener?.invoke(progress)
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (isDragging) {
                    isDragging = false
                    velocityTracker?.computeCurrentVelocity(1000)
                    val velocityY = velocityTracker?.yVelocity ?: 0f
                    val deltaY = event.rawY - startY

                    // Nếu kéo quá 1/4 màn hình HOẶC vuốt với tốc độ đủ mạnh
                    if (deltaY > height / 4f || velocityY > minFlingVelocity) {
                        animate()
                            .translationY(height.toFloat())
                            .alpha(0f)
                            .setDuration(250)
                            .setInterpolator(AccelerateInterpolator())
                            .withEndAction { onSwipeDownListener?.invoke() }
                            .start()
                    } else {
                        // Kéo chưa đủ xa -> Bật ngược trở lại vị trí cũ
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
        return super.onTouchEvent(event)
    }
}