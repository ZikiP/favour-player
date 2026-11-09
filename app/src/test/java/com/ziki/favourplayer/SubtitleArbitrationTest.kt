package com.ziki.favourplayer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 打开作品时拉到的共享字幕要不要用：本机没改过直接用；
 * 改过则按时间戳裁决，旧设备的本地改动不能被服务端旧数据顶掉。
 */
class SubtitleArbitrationTest {

    private fun remote(clientUpdatedAt: Long = 0L, updatedAt: Long = 0L) =
        SharedSubtitle(name = "a.srt", text = "1", style = null, updatedAt = updatedAt, clientUpdatedAt = clientUpdatedAt)

    @Test
    fun freshDeviceAlwaysApplies() {
        assertTrue(SubtitleSync.shouldApplyRemote(0L, remote(clientUpdatedAt = 1L)))
        assertTrue(SubtitleSync.shouldApplyRemote(0L, remote()))
    }

    @Test
    fun newerRemoteWins() {
        assertTrue(SubtitleSync.shouldApplyRemote(100L, remote(clientUpdatedAt = 200L)))
    }

    @Test
    fun olderRemoteDoesNotOverwriteLocalEdits() {
        assertFalse(SubtitleSync.shouldApplyRemote(200L, remote(clientUpdatedAt = 100L)))
    }

    @Test
    fun sameTimeKeepsLocal() {
        assertFalse(SubtitleSync.shouldApplyRemote(200L, remote(clientUpdatedAt = 200L)))
    }

    @Test
    fun fallsBackToServerTimeWhenClientTimeMissing() {
        // 老版本设备推上来的数据没有 clientUpdatedAt，只能用服务端落盘时间比较
        assertTrue(SubtitleSync.shouldApplyRemote(100L, remote(updatedAt = 200L)))
        assertFalse(SubtitleSync.shouldApplyRemote(200L, remote(updatedAt = 100L)))
    }

    @Test
    fun noRemoteTimestampNeverOverwritesLocalEdits() {
        assertFalse(SubtitleSync.shouldApplyRemote(100L, remote()))
    }

    @Test
    fun parseReadsTimestampsAndStyle() {
        val raw = """
            {"key":"abc","name":"a.srt","text":"1\n00:00:01,000 --> 00:00:02,000\nhi",
             "style":{"offset":-1500,"size":17,"color":-1,"bg":1,"bold":true,"pos":2},
             "clientUpdatedAt":123,"updatedAt":456}
        """.trimIndent()
        val parsed = SubtitleSync.parseSharedSubtitle(raw)
        assertEquals("a.srt", parsed?.name)
        assertEquals(123L, parsed?.clientUpdatedAt)
        assertEquals(456L, parsed?.updatedAt)
        assertEquals(-1500L, parsed?.style?.offsetMs)
    }

    @Test
    fun parseToleratesMissingFields() {
        // 样式为空对象代表服务端没存过样式，别拿默认值冲掉本地设置
        val parsed = SubtitleSync.parseSharedSubtitle("""{"name":"n","text":"t","style":{}}""")
        assertEquals(0L, parsed?.clientUpdatedAt)
        assertEquals(0L, parsed?.updatedAt)
        assertNull(parsed?.style)
    }

    @Test
    fun parseHandlesGarbage() {
        assertNull(SubtitleSync.parseSharedSubtitle("not json"))
        assertNull(SubtitleSync.parseSharedSubtitle(""))
        assertNull(SubtitleSync.parseSharedSubtitle("null"))
    }
}
