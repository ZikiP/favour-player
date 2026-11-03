package com.ziki.favourplayer

import androidx.media3.common.PlaybackException
import androidx.media3.datasource.HttpDataSource
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/**
 * 把 ExoPlayer 的异常翻译成人能看懂的原因。
 *
 * 之前播放器只显示 errorCodeName（类似 ERROR_CODE_IO_NETWORK_CONNECTION_FAILED），
 * 用户看不出到底是 DNS 不通、连接超时、被 403 拒了，还是证书有问题。
 * 这里把底层原因挖出来，配合「复制诊断信息」让问题一眼可查。
 */
object PlaybackDiagnostics {

    data class Report(
        /** 一句话结论，例如「HTTP 403 · 服务器拒绝了请求」。 */
        val summary: String,
        /** 出错的请求地址。 */
        val url: String,
        /** 完整诊断文本，可复制给开发者。 */
        val detail: String,
    )

    fun describe(error: PlaybackException, masterUrl: String): Report {
        val chain = causeChain(error)
        val url = chain.firstNotNullOfOrNull { cause ->
            (cause as? HttpDataSource.HttpDataSourceException)?.dataSpec?.uri?.toString()
        } ?: masterUrl

        val summary = summarize(error, chain)
        val detail = buildString {
            appendLine("结论：$summary")
            appendLine("地址：$url")
            appendLine("错误码：${error.errorCodeName} (${error.errorCode})")
            chain.forEach { cause ->
                appendLine("· ${cause.javaClass.simpleName}: ${cause.message ?: "无详细信息"}")
            }
        }.trim()
        return Report(summary, url, detail)
    }

    private fun causeChain(error: PlaybackException): List<Throwable> {
        val chain = mutableListOf<Throwable>()
        var current: Throwable? = error
        var guard = 0
        while (current != null && guard < 10) {
            chain.add(current)
            current = current.cause
            guard++
        }
        return chain
    }

    private fun summarize(error: PlaybackException, chain: List<Throwable>): String {
        chain.forEach { cause ->
            if (cause is HttpDataSource.InvalidResponseCodeException) {
                return "HTTP ${cause.responseCode} · 服务器拒绝了请求"
            }
            if (cause is HttpDataSource.CleartextNotPermittedException) {
                return "地址是 http 明文 · 被网络安全策略拦下"
            }
            if (cause is UnknownHostException) {
                return "域名解析失败 · 检查 DNS 或代理是否生效"
            }
            if (cause is SocketTimeoutException) {
                return "连接超时 · 代理链路太慢或被阻断"
            }
            if (cause is SSLException) {
                return "HTTPS 握手失败 · 证书或代理转发有问题"
            }
            if (cause is IOException) {
                val detail = cause.message?.takeIf { it.isNotBlank() }
                return "网络错误${detail?.let { " · $it" }.orEmpty()}"
            }
        }
        return when (error.errorCode) {
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED -> "网络连接失败"
            PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS -> "服务器返回了异常状态码"
            PlaybackException.ERROR_CODE_IO_CLEARTEXT_NOT_PERMITTED -> "http 明文地址被网络安全策略拦下"
            PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND -> "资源不存在"
            else -> "播放失败"
        }
    }
}
