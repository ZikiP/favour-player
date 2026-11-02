package com.ziki.favourplayer

import android.content.Context
import androidx.compose.runtime.mutableStateOf

/**
 * 干净模式：打开后不显示任何收藏，也不记录任何使用痕迹
 * （不拉快照、不写播放进度、不上传偏好）。
 *
 * 单独存一个 prefs 文件，这样它不会被「偏好同步」打包带走，
 * 换设备时默认是干净的。
 */
class PrivacyStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("favour_privacy", Context.MODE_PRIVATE)

    fun cleanMode(): Boolean = prefs.getBoolean(KEY_CLEAN_MODE, false)

    fun setCleanMode(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_CLEAN_MODE, enabled).apply()
    }

    private companion object {
        const val KEY_CLEAN_MODE = "clean_mode"
    }
}

/**
 * 全局状态包装：切换后所有读到它的地方都会自动重组，
 * 不需要手动通知资料库、播放器这些界面。
 *
 * 有两个来源：
 * - 本机开关（设置 → 版本 → 干净模式），只影响这台设备
 * - 服务端开关（网页端右上角），影响所有设备
 * 任意一个开启就算开启。
 */
object CleanMode {

    private val localState = mutableStateOf(false)
    private val remoteState = mutableStateOf(false)
    private var attached = false

    /** 当前是否处于干净模式。 */
    val enabled: Boolean get() = localState.value || remoteState.value

    /** 本机开关的状态，服务端开启时这里仍是 false。 */
    val localEnabled: Boolean get() = localState.value

    /** 服务端是否开启了全局干净模式。 */
    val remoteEnabled: Boolean get() = remoteState.value

    /** App 启动时调用一次，把落盘的值读进内存。 */
    fun attach(context: Context) {
        if (attached) return
        attached = true
        localState.value = PrivacyStore(context).cleanMode()
    }

    fun set(context: Context, value: Boolean) {
        PrivacyStore(context).setCleanMode(value)
        localState.value = value
    }

    /** 每次拉取快照后用服务端的值校正。 */
    fun setRemote(value: Boolean) {
        remoteState.value = value
    }
}
