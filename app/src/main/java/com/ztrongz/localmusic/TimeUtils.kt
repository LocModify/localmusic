package com.ztrongz.localmusic

import java.util.Locale

object TimeUtils {

    // Định dạng thời gian dùng chung cho danh sách bài hát và màn hình Now Playing:
    // dưới 1 giờ -> mm:ss (vd 03:24), từ 1 giờ trở lên -> h:mm:ss (vd 1:01:15)
    fun format(millis: Long): String {
        val totalSeconds = (millis.coerceAtLeast(0L)) / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) {
            String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.US, "%02d:%02d", minutes, seconds)
        }
    }
}