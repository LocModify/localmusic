package com.ztrongz.localmusic

import android.content.Context
import android.text.TextUtils
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatTextView

// TextView tự chạy chữ (marquee) khi tên quá dài, lặp lại mãi mãi.
// Mẹo: Android chỉ chạy marquee khi View đang được focus, nên ta luôn báo là "đang focus".
class MarqueeTextView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = android.R.attr.textViewStyle
) : AppCompatTextView(context, attrs, defStyleAttr) {

    init {
        isSingleLine = true
        ellipsize = TextUtils.TruncateAt.MARQUEE
        marqueeRepeatLimit = -1 // -1 = lặp vô hạn
    }

    override fun isFocused(): Boolean = true
}