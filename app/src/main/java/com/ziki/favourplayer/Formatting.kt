package com.ziki.favourplayer

import java.util.Locale

fun formatTime(ms: Long): String {
    if (ms < 0L) return "00:00"
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }
}

fun formatSpeed(rate: Float): String {
    val text = if (rate == rate.toInt().toFloat()) {
        String.format(Locale.US, "%.1f", rate)
    } else {
        rate.toString()
    }
    return "${text}×"
}
