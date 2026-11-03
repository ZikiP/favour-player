package com.ziki.favourplayer

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Referer 是能不能播到流的关键。
 *
 * 实测 missav.live 的 CDN（surrit.com）：带 Referer 才返回 200，
 * 不带就是 403「Attention Required! | Cloudflare」。
 * 所以规则没配 refererMode 时必须兜底，而不是留空。
 */
class ResolverRefererTest {

    @Test
    fun defaultsToPageOrigin() {
        assertEquals(
            "https://missav.live/",
            Resolver.buildReferer("page-origin", "https://missav.live/cn/miab-570-chinese-subtitle"),
        )
    }

    @Test
    fun unknownModeFallsBackToOriginInsteadOfBlank() {
        // 留空会被 CDN 直接拒掉，用户只能看到「网络失败」，完全无从排查
        assertEquals(
            "https://missav.live/",
            Resolver.buildReferer("", "https://missav.live/cn/miab-570-chinese-subtitle"),
        )
        assertEquals(
            "https://missav.live/",
            Resolver.buildReferer("whatever", "https://missav.live/cn/miab-570-chinese-subtitle"),
        )
    }

    @Test
    fun supportsFullPageUrl() {
        assertEquals(
            "https://missav.live/cn/miab-570-chinese-subtitle",
            Resolver.buildReferer("page-url", "https://missav.live/cn/miab-570-chinese-subtitle"),
        )
    }

    @Test
    fun respectsExplicitNone() {
        assertEquals(
            "",
            Resolver.buildReferer("none", "https://missav.live/cn/miab-570-chinese-subtitle"),
        )
    }

    @Test
    fun keepsPortInOrigin() {
        assertEquals(
            "http://127.0.0.1:8080/",
            Resolver.buildReferer("page-origin", "http://127.0.0.1:8080/detail/1"),
        )
    }
}
