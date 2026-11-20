package com.ztrongz.localmusic

import android.graphics.Bitmap
import android.net.Uri

data class Track(
    val uri: Uri,
    val title: String,
    val artist: String = "Unknown Artist",
    val durationMs: Long = 0L,
    val cover: Bitmap? = null
)