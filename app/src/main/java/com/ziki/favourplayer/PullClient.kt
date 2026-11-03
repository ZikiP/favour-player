package com.ziki.favourplayer

import android.content.Context
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.URI
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

data class PullResult(val snapshot: LibrarySnapshot, val cleanMode: Boolean = false)

data class PullTestResult(val count: Int)

class PullException(message: String) : Exception(message)

object PullClient {

    internal val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        // 中间服务通常在 Tailscale 私有网段。手机同时挂着 HTTP 代理时，
        // OkHttp 会把内网请求也丢给代理，代理访问不到 100.64/10 就报连接失败。
        // 这里对私有地址强制直连，公网地址仍然按系统代理走。
        .proxySelector(PrivateDirectProxySelector)
        .build()

    fun pull(context: Context, config: PullConfig): PullResult {
        val base = normalizeBase(config.url)
            ?: throw PullException("请填写有效的中间服务地址")
        if (config.token.isBlank()) throw PullException("请填写中间服务令牌")

        val request = try {
            Request.Builder()
                .url("$base/api/items")
                .header("Authorization", "Bearer ${config.token}")
                .header("Accept", "application/json")
                .get()
                .build()
        } catch (error: IllegalArgumentException) {
            throw PullException("中间服务地址无效")
        }

        try {
            client.newCall(request).execute().use { response ->
                when (response.code) {
                    401 -> throw PullException("令牌无效")
                    404 -> throw PullException("地址不对，检查是否包含 /favour")
                }
                if (!response.isSuccessful) {
                    throw PullException("中间服务返回 HTTP ${response.code}")
                }
                val raw = response.body?.string().orEmpty()
                if (raw.isBlank()) throw PullException("中间服务返回空数据")

                runCatching { SnapshotParser.parse(raw) }
                    .getOrElse { throw PullException("中间服务数据格式不正确") }

                // 服务端的全局干净模式：同步时以服务端为准
                val cleanMode = runCatching {
                    JSONObject(raw).optBoolean("cleanMode")
                }.getOrDefault(false)
                CleanMode.setRemote(cleanMode)

                val saved = if (cleanMode) {
                    if (CleanMode.enabled) {
                        // 干净模式返回的是空快照，别拿它把本地缓存覆盖掉
                        SnapshotParser.parse(raw)
                    } else {
                        // 用户在本机关掉了干净模式：继续用本地缓存的内容
                        SyncStore(context).loadCached() ?: SnapshotParser.parse(raw)
                    }
                } else {
                    try {
                        SyncStore(context).saveSnapshot(raw)
                    } catch (error: Throwable) {
                        throw PullException("保存快照失败")
                    }
                }
                CoverCache.prefetch(context, saved.items.map { it.cover })
                SyncBus.publishSnapshot(saved)
                // 同一次「同步」里把本机偏好也推上去。
                // 放在这里同步等完成，网页的「同步于」才会立刻变成当前时间。
                runCatching { ProfileSync.upload(context) }
                return PullResult(saved, cleanMode)
            }
        } catch (error: PullException) {
            throw error
        } catch (error: java.io.IOException) {
            throw PullException(connectionMessage(error))
        } catch (error: Throwable) {
            throw PullException(error.message ?: "拉取失败")
        }
    }

    fun test(config: PullConfig): PullTestResult {
        val base = normalizeBase(config.url)
            ?: throw PullException("请填写有效的中间服务地址")
        if (config.token.isBlank()) throw PullException("请填写中间服务令牌")

        val request = try {
            Request.Builder()
                .url("$base/api/items")
                .header("Authorization", "Bearer ${config.token}")
                .header("Accept", "application/json")
                .get()
                .build()
        } catch (error: IllegalArgumentException) {
            throw PullException("中间服务地址无效")
        }

        try {
            client.newCall(request).execute().use { response ->
                when (response.code) {
                    401 -> throw PullException("令牌无效")
                    404 -> throw PullException("地址不对，检查是否包含 /favour")
                }
                if (!response.isSuccessful) {
                    throw PullException("中间服务返回 HTTP ${response.code}")
                }
                val raw = response.body?.string().orEmpty()
                if (raw.isBlank()) throw PullException("中间服务返回空数据")

                val count = runCatching {
                    val root = JSONObject(raw)
                    root.optJSONArray("items")?.length() ?: root.optInt("count", 0)
                }.getOrElse { throw PullException("中间服务数据格式不正确") }
                return PullTestResult(count)
            }
        } catch (error: PullException) {
            throw error
        } catch (error: java.io.IOException) {
            throw PullException(connectionMessage(error))
        } catch (error: Throwable) {
            throw PullException(error.message ?: "测试失败")
        }
    }

    internal fun normalizeBase(raw: String): String? {
        val value = raw.trim().trimEnd('/')
        if (value.isEmpty()) return null
        val uri = runCatching { URI(value) }.getOrNull() ?: return null
        val scheme = uri.scheme?.lowercase() ?: return null
        if (scheme != "http" && scheme != "https") return null
        if (uri.host.isNullOrBlank()) return null
        return value
    }

    private fun connectionMessage(error: java.io.IOException): String = when (error) {
        is UnknownHostException, is ConnectException, is SocketTimeoutException ->
            "无法连接中间服务，请确认 Tailscale 已开启"
        else -> "拉取失败：${error.message ?: "网络异常"}"
    }
}

/**
 * 私有网段的请求不走系统代理，其余照旧走默认选择器。
 *
 * 手机切到 Clash 这类代理时，OkHttp 默认会把所有请求（包括 Tailscale 的
 * 100.x 地址）发给代理服务器，结果是「明明开了 Tailscale 却连不上」。
 */
internal object PrivateDirectProxySelector : java.net.ProxySelector() {

    override fun select(uri: URI): List<java.net.Proxy> {
        val host = uri.host?.lowercase().orEmpty()
        if (isPrivateHost(host)) return listOf(java.net.Proxy.NO_PROXY)
        return java.net.ProxySelector.getDefault()?.select(uri)
            ?: listOf(java.net.Proxy.NO_PROXY)
    }

    override fun connectFailed(uri: URI?, sa: java.net.SocketAddress?, ioe: java.io.IOException?) = Unit
}

/** 回环、局域网、Tailscale CGNAT 段，以及不带点的内网主机名。 */
internal fun isPrivateHost(host: String): Boolean {
    if (host.isBlank()) return true
    if (host == "localhost" || host == "::1" || host.startsWith("[")) return true
    // MagicDNS 之类的主机名没有点，交给系统解析更靠得住
    if (!host.contains('.')) return true
    val parts = host.split('.')
    val a = parts.getOrNull(0)?.toIntOrNull() ?: return false
    val b = parts.getOrNull(1)?.toIntOrNull() ?: return false
    return when {
        a == 10 -> true
        a == 127 -> true
        a == 192 && b == 168 -> true
        a == 172 && b in 16..31 -> true
        a == 100 && b in 64..127 -> true // Tailscale 100.64.0.0/10
        a == 169 && b == 254 -> true
        else -> false
    }
}
