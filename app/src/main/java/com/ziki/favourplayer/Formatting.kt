package com.ziki.favourplayer

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
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

/**
 * 同步时间的展示：刚发生过的显示「刚刚」（点一下同步就能看到变化），
 * 今天的显示时分，更早的带上日期。
 */
fun formatSyncMoment(timestamp: Long?, now: Long = System.currentTimeMillis()): String {
    if (timestamp == null || timestamp <= 0L) return "未知"
    val elapsed = now - timestamp
    if (elapsed in 0..90_000L) return "刚刚"

    val target = Calendar.getInstance().apply { timeInMillis = timestamp }
    val today = Calendar.getInstance().apply { timeInMillis = now }
    val sameDay = target.get(Calendar.YEAR) == today.get(Calendar.YEAR) &&
        target.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR)
    val pattern = if (sameDay) "HH:mm" else "MM-dd HH:mm"
    return SimpleDateFormat(pattern, Locale.CHINA).format(Date(timestamp))
}

