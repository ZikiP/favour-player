package com.ziki.favourplayer

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context

/**
 * 复制文本到剪贴板。
 *
 * 失败时静默忽略：剪贴板不可用不是用户要处理的事，
 * 不值得为此弹提示或崩溃。
 */
fun copyToClipboard(context: Context, label: String, text: String) {
    runCatching {
        val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            ?: return
        manager.setPrimaryClip(ClipData.newPlainText(label, text))
    }
}
