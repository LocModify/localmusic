package com.ztrongz.localmusic

import android.graphics.Bitmap
import android.net.Uri

data class Track(
    val uri: Uri,
    val title: String,
    val artist: String = "",
    val durationMs: Long = 0L,
    val cover: Bitmap? = null,
    val isFavorite: Boolean = false // Đã thêm biến này
)