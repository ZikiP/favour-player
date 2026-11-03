package com.ziki.favourplayer

import java.util.Calendar
import org.junit.Assert.assertEquals
import org.junit.Test

class SyncMomentTest {

    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        Calendar.getInstance().apply {
            set(year, month - 1, day, hour, minute, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    @Test
    fun justNowWithinWindow() {
        val now = at(2026, 11, 3, 16, 30)
        assertEquals("刚刚", formatSyncMoment(now - 5_000, now))
        assertEquals("刚刚", formatSyncMoment(now - 90_000, now))
    }

    @Test
    fun todayShowsHourAndMinute() {
        val now = at(2026, 11, 3, 16, 30)
        assertEquals("15:02", formatSyncMoment(at(2026, 11, 3, 15, 2), now))
    }

    @Test
    fun earlierDayCarriesDate() {
        val now = at(2026, 11, 3, 16, 30)
        assertEquals("11-02 09:08", formatSyncMoment(at(2026, 11, 2, 9, 8), now))
    }

    @Test
    fun missingTimestampIsUnknown() {
        assertEquals("未知", formatSyncMoment(null))
        assertEquals("未知", formatSyncMoment(0L))
    }
}
