package com.ziki.favourplayer

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 字幕贴底距离不能是负数 —— Compose 的 padding 遇到负值直接抛异常，
 * 小窗口（画中画、矮屏）下视频比窗口还高时最容易触发。
 */
class SubtitleBottomPaddingTest {

    @Test
    fun negativeLetterboxNeverGoesNegative() {
        // 视频比窗口还高：FIT 之后下边黑边是负的
        assertEquals(
            0.dp,
            subtitleBottomPadding((-40).dp, isLandscape = false, isInPip = true, extraBottomDp = 0),
        )
        assertEquals(
            0.dp,
            subtitleBottomPadding((-20).dp, isLandscape = false, isInPip = false, extraBottomDp = 0),
        )
        assertEquals(
            0.dp,
            subtitleBottomPadding((-20).dp, isLandscape = false, isInPip = false, extraBottomDp = 18),
        )
    }

    @Test
    fun normalLayoutKeepsOriginalOffsets() {
        assertEquals(
            (100 + 24 + 18).dp,
            subtitleBottomPadding(100.dp, isLandscape = true, isInPip = false, extraBottomDp = 18),
        )
        assertEquals(
            (100 + 2).dp,
            subtitleBottomPadding(100.dp, isLandscape = false, isInPip = true, extraBottomDp = 0),
        )
    }
}
