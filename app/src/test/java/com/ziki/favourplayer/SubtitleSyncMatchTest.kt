package com.ziki.favourplayer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 同步时靠 URL 摘要把服务端清单对回本机作品，
 * 这里守住这条映射的三条性质：稳定、区分版本、跳过空值。
 */
class SubtitleSyncMatchTest {

    @Test
    fun sameUrlMapsToSameKey() {
        val url = "https://example.com/dm26/cn/abc-123"
        assertEquals(SubtitleSync.keyOf(url), SubtitleSync.keyOf(url))
    }

    @Test
    fun keyIsShortHex() {
        val key = SubtitleSync.keyOf("https://example.com/a")
        assertEquals(24, key.length)
        assertTrue(key.all { it in '0'..'9' || it in 'a'..'f' })
    }

    @Test
    fun differentVersionsGetDifferentKeys() {
        // 同一部作品的默认版 / 带字幕版是两个地址，字幕各存一份
        val plain = SubtitleSync.keyOf("https://example.com/dm26/abc-123")
        val subbed = SubtitleSync.keyOf("https://example.com/dm26/cn/abc-123")
        assertNotEquals(plain, subbed)
    }

    @Test
    fun indexFindsBackByKey() {
        val urls = listOf(
            "https://example.com/dm26/abc-123",
            "https://example.com/dm26/cn/abc-123",
        )
        val index = SubtitleSync.keyIndex(urls)
        assertEquals(2, index.size)
        assertEquals(urls[0], index[SubtitleSync.keyOf(urls[0])])
        assertEquals(urls[1], index[SubtitleSync.keyOf(urls[1])])
    }

    @Test
    fun blankUrlsDropped() {
        val index = SubtitleSync.keyIndex(listOf("", "   ", "https://example.com/a"))
        assertEquals(1, index.size)
        assertNull(index[""])
    }

    @Test
    fun styleRoundTripKeepsOffset() {
        val style = SubtitleStyle(offsetMs = -1_500L, fontSize = 17f, position = 2, bold = true)
        val decoded = SubtitleStyleCodec.decode(SubtitleStyleCodec.encode(style))
        assertEquals(-1_500L, decoded.offsetMs)
        assertEquals(17f, decoded.fontSize)
        assertEquals(2, decoded.position)
        assertTrue(decoded.bold)
    }
}
