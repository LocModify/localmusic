package com.ztrongz.localmusic

import android.graphics.Bitmap
import android.util.LruCache

// Ảnh bìa thu nhỏ cho các danh sách: ảnh gốc có thể rất lớn (cả nghìn px) -> cuộn danh sách bị giật và tốn RAM
object ThumbCache {
    private const val TARGET_PX = 160          // cạnh ngắn sau khi thu nhỏ (px). Tăng = nét hơn nhưng nặng hơn
    private val cache = LruCache<String, Bitmap>(64)

    fun get(track: Track): Bitmap? {
        val src = track.cover ?: return null
        val key = track.uri.toString()
        cache.get(key)?.let { return it }

        val shortSide = minOf(src.width, src.height)
        val thumb = if (shortSide <= TARGET_PX) {
            src
        } else {
            val scale = TARGET_PX.toFloat() / shortSide
            Bitmap.createScaledBitmap(
                src,
                (src.width * scale).toInt().coerceAtLeast(1),
                (src.height * scale).toInt().coerceAtLeast(1),
                true
            )
        }
        cache.put(key, thumb)
        return thumb
    }
}
