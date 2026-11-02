package com.ziki.favourplayer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RefreshRatePickerTest {

    private fun modes(vararg rates: Pair<Int, Float>, width: Int = 1080, height: Int = 2400) =
        rates.map { (id, rate) -> RefreshMode(id, width, height, rate) }

    @Test
    fun picksMultipleOf24fpsOnHighRefreshScreen() {
        val available = modes(1 to 60f, 2 to 120f)
        // 60 / 24 = 2.5 不整除，120 / 24 = 5 整除
        assertEquals(2, RefreshRatePicker.pick(available, currentModeId = 1, videoFps = 24f))
    }

    @Test
    fun keepsLowestMultipleAtOrAbove60() {
        val available = modes(1 to 60f, 2 to 120f)
        assertEquals(1, RefreshRatePicker.pick(available, currentModeId = 2, videoFps = 30f))
    }

    @Test
    fun handles23Point976() {
        val available = modes(1 to 60f, 2 to 120f)
        assertEquals(
            2,
            RefreshRatePicker.pick(available, currentModeId = 1, videoFps = 23.976f),
        )
    }

    @Test
    fun returnsNullWhenNoModeDividesEvenly() {
        val available = modes(1 to 60f)
        assertNull(RefreshRatePicker.pick(available, currentModeId = 1, videoFps = 24f))
    }

    @Test
    fun returnsNullWhenAlreadyOnTarget() {
        val available = modes(1 to 60f, 2 to 120f)
        assertNull(RefreshRatePicker.pick(available, currentModeId = 1, videoFps = 30f))
    }

    @Test
    fun ignoresModesWithDifferentResolution() {
        val small = modes(1 to 60f)
        val large = modes(2 to 120f, width = 1440, height = 3120)
        assertNull(RefreshRatePicker.pick(small + large, currentModeId = 1, videoFps = 24f))
    }

    @Test
    fun skipsBelow48HzWhenHigherMultipleExists() {
        val available = modes(1 to 48f, 2 to 100f, 3 to 120f)
        assertEquals(
            2,
            RefreshRatePicker.pick(available, currentModeId = 1, videoFps = 25f),
        )
    }

    @Test
    fun returnsNullForUnknownFrameRate() {
        val available = modes(1 to 60f, 2 to 120f)
        assertNull(RefreshRatePicker.pick(available, currentModeId = 1, videoFps = -1f))
    }
}
