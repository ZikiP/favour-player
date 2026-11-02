package com.ziki.favourplayer

import android.content.Context

data class PlayerSettings(
    val defaultSpeed: Float = 1f,
    val autoHideMs: Int = 3500,
    val doubleTapMs: Long = 10_000L,
    val controlBottom: Int = 12,
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
        controlBottom = prefs.getInt(KEY_CONTROL_BOTTOM, 12)
            .takeIf { it in CONTROL_BOTTOM } ?: 12,
    )

    fun save(settings: PlayerSettings) {
        prefs.edit()
            .putFloat(KEY_SPEED, settings.defaultSpeed.takeIf { it in SPEEDS } ?: 1f)
            .putInt(KEY_AUTO_HIDE, settings.autoHideMs.takeIf { it in AUTO_HIDE } ?: 3500)
            .putLong(KEY_DOUBLE_TAP, settings.doubleTapMs.takeIf { it in DOUBLE_TAP } ?: 10_000L)
            .putInt(
                KEY_CONTROL_BOTTOM,
                settings.controlBottom.takeIf { it in CONTROL_BOTTOM } ?: 12,
            )
            .apply()
    }

    companion object {
        val SPEEDS = listOf(0.75f, 1f, 1.25f, 1.5f, 2f)
        val AUTO_HIDE = listOf(2_000, 3_500, 5_000, 8_000)
        val DOUBLE_TAP = listOf(5_000L, 10_000L, 15_000L, 30_000L)
        val CONTROL_BOTTOM = listOf(0, 12, 24, 40)

        private const val KEY_SPEED = "default_speed"
        private const val KEY_AUTO_HIDE = "auto_hide_ms"
        private const val KEY_DOUBLE_TAP = "double_tap_ms"
        private const val KEY_CONTROL_BOTTOM = "control_bottom_dp"
    }
}
