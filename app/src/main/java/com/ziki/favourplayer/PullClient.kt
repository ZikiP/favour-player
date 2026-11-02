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

data class PullResult(val snapshot: LibrarySnapshot)

data class PullTestResult(val count: Int)

class PullException(message: String) : Exception(message)

object PullClient {

    internal val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
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

                val saved = try {
                    SyncStore(context).saveSnapshot(raw)
                } catch (error: Throwable) {
                    throw PullException("保存快照失败")
                }
                CoverCache.prefetch(context, saved.items.map { it.cover })
                SyncBus.publishSnapshot(saved)
                return PullResult(saved)
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
