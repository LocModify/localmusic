package com.ztrongz.localmusic

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

// Chia sẻ CHÍNH FILE nhạc sang ứng dụng khác (Zalo, Messenger, Drive, Bluetooth...)
object ShareHelper {
    fun shareSong(context: Context, uri: Uri?, title: String) {
        if (uri == null) {
            Toast.makeText(context, "Không tìm thấy file nhạc", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            // Lấy đúng loại file (audio/mpeg, audio/flac...), không biết thì dùng audio/*
            val mime = context.contentResolver.getType(uri) ?: "audio/*"
            val send = Intent(Intent.ACTION_SEND).apply {
                type = mime
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, title)
                putExtra(Intent.EXTRA_TITLE, title)
                // Cấp quyền đọc file cho app nhận
                clipData = ClipData.newRawUri(title, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(send, "Chia sẻ bài hát"))
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Không thể chia sẻ file này", Toast.LENGTH_SHORT).show()
        }
    }
}
