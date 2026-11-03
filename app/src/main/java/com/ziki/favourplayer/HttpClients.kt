package com.ziki.favourplayer

import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * 全局共用的 HTTP 客户端。
 *
 * 之前解析页面（[Resolver]）和拉流（[PlayerScreen]）各自 new 了一个
 * OkHttpClient，两者互不相通，于是：
 *  - 站点在页面上下发的 Cookie（风控 / 会话）到不了 m3u8 请求，
 *    「浏览器里能播、App 里失败」的典型原因；
 *  - 两边的 User-Agent / Referer 容易对不齐，CDN 的防盗链会拒绝。
 *
 * 这里统一成一个客户端：Cookie 落在内存里（不写盘、不留痕），
 * 解析阶段拿到的 Cookie 可以直接被播放器复用。
 *
 * 代理不在这里配置：Clash / Surge 等 TUN 模式下流量走虚拟网卡，
 * 系统路由已经接管，App 再指定 Proxy 反而会打架。
 */
object HttpClients {

    /**
     * 进程内共享的 Cookie 罐。只存在内存中，App 退出即消失。
     * 站点改版或触发风控后可以整体清掉重来。
     */
    private class MemoryCookieJar : CookieJar {
        private val store = mutableMapOf<String, MutableList<Cookie>>()

        @Synchronized
        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            if (cookies.isEmpty()) return
            val list = store.getOrPut(url.host) { mutableListOf() }
            cookies.forEach { cookie ->
                list.removeAll { it.name == cookie.name && it.domain == cookie.domain }
                list.add(cookie)
            }
        }

        @Synchronized
        override fun loadForRequest(url: HttpUrl): List<Cookie> {
            val list = store[url.host] ?: return emptyList()
            val now = System.currentTimeMillis()
            list.removeAll { it.expiresAt < now }
            return list.filter { it.matches(url) }
        }

        @Synchronized
        fun clear() {
            store.clear()
        }
    }

    private val cookieJar = MemoryCookieJar()

    val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .cookieJar(cookieJar)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }

    /** 清掉内存里的 Cookie。站点改版或被风控后可以让用户重置。 */
    fun clearCookies() {
        cookieJar.clear()
    }
}
