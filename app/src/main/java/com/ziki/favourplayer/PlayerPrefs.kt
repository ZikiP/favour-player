package com.ziki.favourplayer

import android.content.Context

data class PlayerSettings(
    val defaultSpeed: Float = 1f,
    val autoHideMs: Int = 3500,
    val doubleTapMs: Long = 10_000L,
    val controlBottomPortrait: Int = 12,
    val controlBottomLandscape: Int = 0,
    val sharpen: SharpenLevel = SharpenLevel.Off,
    val frameRateMatch: Boolean = true,
    /** 画质优先：锁最高可用码率，不因带宽估算自动降档。默认开启。 */
    val qualityFirst: Boolean = true,
)

class PlayerPrefs(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("favour_player", Context.MODE_PRIVATE)

    fun load(): PlayerSettings = PlayerSettings(
        defaultSpeed = prefs.getFloat(KEY_SPEED, 1f)
            .takeIf { it in SPEEDS } ?: 1f,
        autoHideMs = prefs.getInt(KEY_AUTO_HIDE, 3500)
            .takeIf { it in AUTO_HIDE } ?: 3500,
        doubleTapMs = prefs.getLong(KEY_DOUBLE_TAP, 10_000L)
            .takeIf { it in DOUBLE_TAP } ?: 10_000L,
        // 旧版只有一个 controlBottom，读不到新键时回落到它，等于自动迁移
        controlBottomPortrait = prefs.getInt(
            KEY_CB_PORTRAIT,
            prefs.getInt(KEY_CONTROL_BOTTOM, 12),
        ).coerceIn(0, CONTROL_BOTTOM_MAX),
        controlBottomLandscape = prefs.getInt(
            KEY_CB_LANDSCAPE,
            prefs.getInt(KEY_CONTROL_BOTTOM, 0),
        ).coerceIn(0, CONTROL_BOTTOM_MAX),
        sharpen = SharpenLevel.fromOrdinal(prefs.getInt(KEY_SHARPEN, SharpenLevel.Off.ordinal)),
        frameRateMatch = prefs.getBoolean(KEY_FRAME_RATE, true),
        qualityFirst = prefs.getBoolean(KEY_QUALITY_FIRST, true),
    )

    fun save(settings: PlayerSettings) {
        prefs.edit()
            .putFloat(KEY_SPEED, settings.defaultSpeed.takeIf { it in SPEEDS } ?: 1f)
            .putInt(KEY_AUTO_HIDE, settings.autoHideMs.takeIf { it in AUTO_HIDE } ?: 3500)
            .putLong(KEY_DOUBLE_TAP, settings.doubleTapMs.takeIf { it in DOUBLE_TAP } ?: 10_000L)
            .putInt(
                KEY_CB_PORTRAIT,
                settings.controlBottomPortrait.coerceIn(0, CONTROL_BOTTOM_MAX),
            )
            .putInt(
                KEY_CB_LANDSCAPE,
                settings.controlBottomLandscape.coerceIn(0, CONTROL_BOTTOM_MAX),
            )
            .putInt(KEY_SHARPEN, settings.sharpen.ordinal)
            .putBoolean(KEY_FRAME_RATE, settings.frameRateMatch)
            .putBoolean(KEY_QUALITY_FIRST, settings.qualityFirst)
            .apply()
    }

    companion object {
        val SPEEDS = listOf(0.75f, 1f, 1.25f, 1.5f, 2f)
        val AUTO_HIDE = listOf(2_000, 3_500, 5_000, 8_000)
        val DOUBLE_TAP = listOf(5_000L, 10_000L, 15_000L, 30_000L)
        const val CONTROL_BOTTOM_MAX = 120

        private const val KEY_SPEED = "default_speed"
        private const val KEY_AUTO_HIDE = "auto_hide_ms"
        private const val KEY_DOUBLE_TAP = "double_tap_ms"
        private const val KEY_CONTROL_BOTTOM = "control_bottom_dp"
        private const val KEY_CB_PORTRAIT = "control_bottom_portrait"
        private const val KEY_CB_LANDSCAPE = "control_bottom_landscape"
        private const val KEY_SHARPEN = "sharpen_level"
        private const val KEY_FRAME_RATE = "frame_rate_match"
        private const val KEY_QUALITY_FIRST = "quality_first"
    }
}
