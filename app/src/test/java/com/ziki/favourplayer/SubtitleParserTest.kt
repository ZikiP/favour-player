package com.ziki.favourplayer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SubtitleParserTest {

    @Test
    fun parsesSrt() {
        val raw = """
            1
            00:00:01,000 --> 00:00:03,500
            第一句

            2
            00:00:04,000 --> 00:00:06,000
            第二句
        """.trimIndent()

        val cues = SubtitleParser.parse(raw)
        assertEquals(2, cues.size)
        assertEquals(1_000L, cues[0].startMs)
        assertEquals(3_500L, cues[0].endMs)
        assertEquals("第一句", cues[0].text)
        assertEquals("第二句", cues[1].text)
    }

    @Test
    fun parsesVtt() {
        val raw = """
            WEBVTT

            00:00:02.000 --> 00:00:04.000
            你好
        """.trimIndent()

        val cues = SubtitleParser.parse(raw)
        assertEquals(1, cues.size)
        assertEquals(2_000L, cues[0].startMs)
        assertEquals("你好", cues[0].text)
    }

    @Test
    fun parsesAssAndStripsStyleTags() {
        val raw = """
            [Script Info]
            Title: test

            [Events]
            Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text
            Dialogue: 0,0:00:01.00,0:00:03.50,Default,,0,0,0,,{\fn微软雅黑\b1}第一句
            Dialogue: 0,0:00:04.00,0:00:06.00,Default,,0,0,0,,第二句\N换行
        """.trimIndent()

        val cues = SubtitleParser.parse(raw)
        assertEquals(2, cues.size)
        assertEquals(1_000L, cues[0].startMs)
        assertEquals(3_500L, cues[0].endMs)
        assertEquals("第一句", cues[0].text)
        assertEquals("第二句\n换行", cues[1].text)
    }

    @Test
    fun keepsCommasInsideAssText() {
        val raw = """
            [Events]
            Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text
            Dialogue: 0,0:00:01.00,0:00:02.00,Default,,0,0,0,,他走了, 然后再也没回来
        """.trimIndent()

        val cues = SubtitleParser.parse(raw)
        assertEquals(1, cues.size)
        assertEquals("他走了, 然后再也没回来", cues[0].text)
    }

    @Test
    fun respectsAssFormatColumnOrder() {
        val raw = """
            [Events]
            Format: Marked, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text
            Dialogue: Marked=0,0:00:02.00,0:00:04.00,Default,,0,0,0,,列序不同也要对
        """.trimIndent()

        val cues = SubtitleParser.parse(raw)
        assertEquals(1, cues.size)
        assertEquals(2_000L, cues[0].startMs)
        assertEquals("列序不同也要对", cues[0].text)
    }

    @Test
    fun returnsEmptyForUnknownFormat() {
        assertTrue(SubtitleParser.parse("这不是字幕文件").isEmpty())
    }
}
