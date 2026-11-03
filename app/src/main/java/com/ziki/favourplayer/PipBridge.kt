package com.ziki.favourplayer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.media3.common.Player

/**
 * 画中画（PiP）的桥接层。
 *
 * 画中画窗口里系统自带的播放/暂停/关闭按钮，本质是发广播给 App：
 * PendingIntent 只能指向一个组件，所以用这个清单里注册的广播接收器接收，
 * 再通过 [PipBridge] 找到当前的 ExoPlayer 实例。
 */
object PipBridge {

    const val ACTION_PLAY = "com.ziki.favourplayer.PIP_PLAY"
    const val ACTION_PAUSE = "com.ziki.favourplayer.PIP_PAUSE"
    const val ACTION_CLOSE = "com.ziki.favourplayer.PIP_CLOSE"

    /** 当前正在播放的播放器（画中画期间由 PlayerActivity 写入）。 */
    @Volatile
    var player: Player? = null

    /** 关闭画中画的动作（回到全屏时由 PlayerActivity 写入）。 */
    @Volatile
    var closeAction: (() -> Unit)? = null
}

/** 播放状态快照，画中画参数（画面比例、按钮图标）要跟着它变。 */
data class PipPlayback(
    val player: Player? = null,
    val width: Int = 0,
    val height: Int = 0,
    val isPlaying: Boolean = false,
)

class PipReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            PipBridge.ACTION_PLAY -> PipBridge.player?.play()
            PipBridge.ACTION_PAUSE -> PipBridge.player?.pause()
            PipBridge.ACTION_CLOSE -> PipBridge.closeAction?.invoke()
        }
    }
}
