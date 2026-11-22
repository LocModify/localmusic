package com.ztrongz.localmusic

import android.graphics.Bitmap
import android.net.Uri

data class Track(
    val uri: Uri,
    val title: String,
    val artist: String,
    val duration: Long,
    val cover: Bitmap?,
    val isFavorite: Boolean = false
)