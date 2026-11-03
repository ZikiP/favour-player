package com.ziki.favourplayer

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 同步请求要绕过 HTTP 代理直连私有网段的中间服务。
 * 手机挂着 Clash 看网页、同步时才切 Tailscale，这是常态，
 * 判错了就会出现「明明开了 Tailscale 却连不上」。
 */
class ProxyBypassTest {

    @Test
    fun tailscaleRangeGoesDirect() {
        // 100.64.0.0/10 是 Tailscale 用的 CGNAT 段
        assertTrue(isPrivateHost("100.64.0.1"))
        assertTrue(isPrivateHost("100.101.102.103"))
        assertTrue(isPrivateHost("100.127.255.254"))
    }

    @Test
    fun outsideTailscaleRangeUsesDefault() {
        assertFalse(isPrivateHost("100.63.0.1"))
        assertFalse(isPrivateHost("100.128.0.1"))
    }

    @Test
    fun lanAndLoopbackGoDirect() {
        assertTrue(isPrivateHost("127.0.0.1"))
        assertTrue(isPrivateHost("localhost"))
        assertTrue(isPrivateHost("192.168.1.10"))
        assertTrue(isPrivateHost("10.0.0.5"))
        assertTrue(isPrivateHost("172.16.3.9"))
        assertTrue(isPrivateHost("172.31.255.1"))
    }

    @Test
    fun outsidePrivateRangeUsesDefault() {
        assertFalse(isPrivateHost("172.15.0.1"))
        assertFalse(isPrivateHost("172.32.0.1"))
        assertFalse(isPrivateHost("8.8.8.8"))
        assertFalse(isPrivateHost("example.com"))
        assertFalse(isPrivateHost("api.github.com"))
    }

    @Test
    fun bareHostnameGoesDirect() {
        // Tailscale MagicDNS 给的主机名通常不带点，交给系统解析
        assertTrue(isPrivateHost("ziki-pc"))
        assertTrue(isPrivateHost(""))
    }
}
