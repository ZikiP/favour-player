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

    /**
     * 用户有没有在本地手动设置过。
     *
     * 没设置过时跟着服务端走；手动设置过就以本地为准，
     * 下次同步不会把它改回去（可以点「跟随服务端」恢复）。
     */
    fun cleanOverride(): Boolean = prefs.getBoolean(KEY_CLEAN_OVERRIDE, false)

    fun setCleanOverride(value: Boolean) {
        prefs.edit().putBoolean(KEY_CLEAN_OVERRIDE, value).apply()
    }

    private companion object {
        const val KEY_CLEAN_MODE = "clean_mode"
        const val KEY_CLEAN_OVERRIDE = "clean_override"
    }
}

/**
 * 全局状态包装：切换后所有读到它的地方都会自动重组，
 * 不需要手动通知资料库、播放器这些界面。
 *
 * 两个来源：
 * - 服务端开关（网页端右上角），同步时覆盖到本机
 * - 本机开关（设置 → 版本 → 干净模式），可以离线随便切
 *
 * 规则：默认以服务端为准；用户在本机手动改过之后，
 * 就以本机为准，直到用户点「跟随服务端」。
 */
object CleanMode {

    private val localState = mutableStateOf(false)
    private val remoteState = mutableStateOf(false)
    private val overrideState = mutableStateOf(false)
    private var attached = false

    /** 当前是否处于干净模式：本地覆盖优先，否则跟服务端。 */
    val enabled: Boolean get() = if (overrideState.value) localState.value else remoteState.value

    /** 本机开关的状态，服务端开启时这里可能是 false。 */
    val localEnabled: Boolean get() = localState.value

    /** 服务端是否开启了全局干净模式。 */
    val remoteEnabled: Boolean get() = remoteState.value

    /** 用户是否手动覆盖过服务端设置。 */
    val overridden: Boolean get() = overrideState.value

    /** App 启动时调用一次，把落盘的值读进内存。 */
    fun attach(context: Context) {
        if (attached) return
        attached = true
        val store = PrivacyStore(context)
        localState.value = store.cleanMode()
        // 老版本只有 clean_mode 一个键：当时手动打开过就算覆盖，
        // 从没打开过就继续跟随服务端。
        overrideState.value = store.cleanOverride() || store.cleanMode()
    }

    /** 用户手动切换：写本地值并标记为覆盖，之后同步不会改回来。 */
    fun set(context: Context, value: Boolean) {
        val store = PrivacyStore(context)
        store.setCleanMode(value)
        store.setCleanOverride(true)
        setLocal(value)
    }

    /** 只改内存状态，不落盘（界面切换和测试走这条）。 */
    fun setLocal(value: Boolean) {
        localState.value = value
        overrideState.value = true
    }

    /** 清掉全部状态，仅供测试使用。 */
    internal fun reset() {
        localState.value = false
        remoteState.value = false
        overrideState.value = false
    }

    /** 放弃本地覆盖，重新跟随服务端。 */
    fun followServer(context: Context) {
        val store = PrivacyStore(context)
        store.setCleanOverride(false)
        overrideState.value = false
    }

    /** 每次拉取快照后用服务端的值校正（本地有覆盖时不动）。 */
    fun setRemote(value: Boolean) {
        remoteState.value = value
    }
}
