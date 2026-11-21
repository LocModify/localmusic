package com.ztrongz.localmusic

import android.graphics.Bitmap
import android.graphics.Color

object BitmapUtils {

    // Cắt bỏ viền đen (letterbox) ở 4 cạnh của ảnh bìa, ví dụ thumbnail video 16:9 có 2 dải đen trên/dưới.
    // threshold: độ sáng (0-255) dưới mức này coi là "đen". Giữ thấp (10-15) để không cắt nhầm nền tối của ảnh; tăng nếu viền đen chưa bị cắt hết.
    fun trimBlackBars(src: Bitmap, threshold: Int = 12): Bitmap {
        val w = src.width
        val h = src.height
        if (w < 16 || h < 16) return src

        val stepX = maxOf(1, w / 80)
        val stepY = maxOf(1, h / 80)

        fun isDark(x: Int, y: Int): Boolean {
            val p = src.getPixel(x, y)
            val luma = (Color.red(p) * 299 + Color.green(p) * 587 + Color.blue(p) * 114) / 1000
            return luma < threshold
        }

        // Một hàng/cột được coi là viền đen nếu >= 97% điểm lấy mẫu là màu đen
        fun isDarkRow(y: Int): Boolean {
            var total = 0
            var bright = 0
            var x = 0
            while (x < w) { total++; if (!isDark(x, y)) bright++; x += stepX }
            return bright <= total * 0.03
        }

        fun isDarkCol(x: Int, top: Int, bottom: Int): Boolean {
            var total = 0
            var bright = 0
            var y = top
            while (y <= bottom) { total++; if (!isDark(x, y)) bright++; y += stepY }
            return bright <= total * 0.03
        }

        var top = 0
        var bottom = h - 1
        while (top < h / 2 && isDarkRow(top)) top++
        while (bottom > h / 2 && isDarkRow(bottom)) bottom--

        var left = 0
        var right = w - 1
        while (left < w / 2 && isDarkCol(left, top, bottom)) left++
        while (right > w / 2 && isDarkCol(right, top, bottom)) right--

        val newW = right - left + 1
        val newH = bottom - top + 1

        // Không có viền đen, hoặc ảnh gần như toàn đen thì giữ nguyên
        if (newW == w && newH == h) return src
        if (newW < w * 0.3f || newH < h * 0.3f) return src

        return Bitmap.createBitmap(src, left, top, newW, newH)
    }
}