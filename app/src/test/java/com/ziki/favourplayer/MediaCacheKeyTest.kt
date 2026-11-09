package com.ziki.favourplayer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * 缓存键必须对签名参数不敏感：CDN 分片 URL 每轮播放都换签名，
 * 键跟着变就等于永远命不中缓存。
 */
class MediaCacheKeyTest {

    @Test
    fun stripsQueryString() {
        assertEquals(
            "https://cdn.example.com/hls/seg-3.ts",
            MediaCache.cacheKeyOf("https://cdn.example.com/hls/seg-3.ts?sign=abc&expires=1700000000"),
        )
    }

    @Test
    fun stripsFragment() {
        assertEquals(
            "https://cdn.example.com/hls/seg-3.ts",
            MediaCache.cacheKeyOf("https://cdn.example.com/hls/seg-3.ts#t=10"),
        )
    }

    @Test
    fun stripsQueryAndFragment() {
        assertEquals(
            "https://cdn.example.com/hls/seg-3.ts",
            MediaCache.cacheKeyOf("https://cdn.example.com/hls/seg-3.ts?sign=a#t=10"),
        )
    }

    @Test
    fun plainUrlUntouched() {
        val url = "https://cdn.example.com/hls/seg-3.ts"
        assertEquals(url, MediaCache.cacheKeyOf(url))
    }

    @Test
    fun rotatedSignaturesHitSameKey() {
        val first = MediaCache.cacheKeyOf("https://cdn.example.com/seg.ts?sign=aaa&expires=1")
        val second = MediaCache.cacheKeyOf("https://cdn.example.com/seg.ts?sign=bbb&expires=2")
        assertEquals(first, second)
    }

    @Test
    fun differentSegmentsStayDistinct() {
        assertNotEquals(
            MediaCache.cacheKeyOf("https://cdn.example.com/720p/seg-3.ts?sign=a"),
            MediaCache.cacheKeyOf("https://cdn.example.com/1080p/seg-3.ts?sign=a"),
        )
    }
}
