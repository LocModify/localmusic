package com.ztrongz.localmusic

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewGroup
import android.view.ViewConfiguration
import android.view.animation.AccelerateInterpolator
import android.widget.Button
import android.widget.SeekBar
import androidx.constraintlayout.widget.ConstraintLayout
import kotlin.math.abs

class SwipeableConstraintLayout @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : ConstraintLayout(context, attrs, defStyleAttr) {

    private var startY = 0f
    private var startX = 0f
    private var isDragging = false

    // Chỉ cho vuốt xuống khi ngón tay BẮT ĐẦU chạm ở vùng trống/ảnh bìa/tên bài,
    // không phải trên thanh tua, nút điều khiển hay nút chức năng
    private var swipeAllowed = true
    private var velocityTracker: VelocityTracker? = null

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop

    // Vuốt nhanh hơn mức này (px/giây) thì đóng ngay dù kéo chưa tới 1/4 màn hình
    private val flingVelocity = 1000f

    var onSwipeDownListener: (() -> Unit)? = null
    var onDragProgressListener: ((Float) -> Unit)? = null

    init {
        // QUAN TRỌNG: nếu không clickable, chạm vào vùng trống sẽ không ai nhận sự kiện DOWN
        // => Android ngừng gửi các sự kiện MOVE/UP về đây => vuốt chỉ hoạt động khi chạm vào nút
        isClickable = true
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        trackVelocity(ev)

        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                startY = ev.rawY
                startX = ev.rawX
                isDragging = false
                swipeAllowed = !isOnInteractiveView(ev.rawX, ev.rawY)
            }

            MotionEvent.ACTION_MOVE -> {
                val deltaY = ev.rawY - startY
                val deltaX = ev.rawX - startX

                // Bắt đầu kéo khi vuốt xuống và hướng dọc nhiều hơn hướng ngang
                if (swipeAllowed && !isDragging && deltaY > touchSlop && deltaY > abs(deltaX)) {
                    isDragging = true
                    // Hủy sự kiện của các View con (SeekBar, nút...) để chúng không xử lý tiếp
                    val cancelEvent = MotionEvent.obtain(ev)
                    cancelEvent.action = MotionEvent.ACTION_CANCEL
                    super.dispatchTouchEvent(cancelEvent)
                    cancelEvent.recycle()
                }

                if (isDragging) {
                    translationY = deltaY.coerceAtLeast(0f)
                    val progress = (1f - (translationY / height)).coerceIn(0f, 1f)
                    onDragProgressListener?.invoke(progress)
                    return true
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (isDragging) {
                    isDragging = false
                    velocityTracker?.computeCurrentVelocity(1000)
                    val velocityY = velocityTracker?.yVelocity ?: 0f
                    releaseTracker()

                    // Kéo quá 1/4 màn hình hoặc vuốt nhanh thì đóng
                    if (translationY > height / 4f || velocityY > flingVelocity) {
                        animate()
                            .translationY(height.toFloat())
                            .alpha(0f)
                            .setDuration(250)
                            .setInterpolator(AccelerateInterpolator())
                            .withEndAction { onSwipeDownListener?.invoke() }
                            .start()
                    } else {
                        // Bật ngược lại vị trí cũ
                        animate()
                            .translationY(0f)
                            .alpha(1f)
                            .setDuration(200)
                            .withEndAction { onDragProgressListener?.invoke(1f) }
                            .start()
                        onDragProgressListener?.invoke(1f)
                    }
                    return true
                }
                releaseTracker()
            }
        }
        return super.dispatchTouchEvent(ev)
    }

    // Kiểm tra điểm chạm (tọa độ màn hình) có nằm trên View tương tác nào không
    private fun isOnInteractiveView(x: Float, y: Float): Boolean = hitInteractive(this, x, y)

    private fun hitInteractive(group: ViewGroup, x: Float, y: Float): Boolean {
        for (i in group.childCount - 1 downTo 0) {
            val child = group.getChildAt(i)
            if (child.visibility != View.VISIBLE) continue
            if (!isInside(child, x, y)) continue
            if (child is SeekBar || child is Button || child.isClickable) return true
            if (child is ViewGroup && hitInteractive(child, x, y)) return true
        }
        return false
    }

    private fun isInside(v: View, x: Float, y: Float): Boolean {
        val loc = IntArray(2)
        v.getLocationOnScreen(loc)
        // Thanh tua mở rộng vùng chạm thêm 16dp lên/xuống để dễ tránh vuốt nhầm
        val extra = if (v is SeekBar) 16 * resources.displayMetrics.density else 0f
        return x >= loc[0] && x <= loc[0] + v.width &&
            y >= loc[1] - extra && y <= loc[1] + v.height + extra
    }

    // Tính vận tốc theo tọa độ màn hình (rawY), vì tọa độ trong View đổi theo khi View đang bị kéo
    private fun trackVelocity(ev: MotionEvent) {
        if (ev.actionMasked == MotionEvent.ACTION_DOWN) {
            velocityTracker?.recycle()
            velocityTracker = VelocityTracker.obtain()
        }
        val copy = MotionEvent.obtain(ev)
        copy.setLocation(ev.rawX, ev.rawY)
        velocityTracker?.addMovement(copy)
        copy.recycle()
    }

    private fun releaseTracker() {
        velocityTracker?.recycle()
        velocityTracker = null
    }
}