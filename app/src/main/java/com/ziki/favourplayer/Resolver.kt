package com.ziki.favourplayer

import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URI
import java.util.concurrent.TimeUnit

data class SpriteConfig(
    val frameWidth: Int,
    val frameHeight: Int,
    val cols: Int,
    val rows: Int,
    val frameCount: Int,
) {
    val framesPerSheet: Int get() = cols * rows
}

data class ResolvedStream(
    val pageUrl: String,
    val uuid: String,
    val masterUrl: String,
    val referer: String,
    val title: String,
    val sprite: SpriteConfig?,
    val spriteUrlTemplate: String?,
)

class ResolveException(message: String) : Exception(message)

object Resolver {
    const val DESKTOP_UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val titleTag = Regex("""<title>(.*?)</title>""", RegexOption.DOT_MATCHES_ALL)

    fun normalize(input: String): String {
        val raw = input.trim()
        if (raw.isEmpty()) throw ResolveException("请输入收藏页地址")
        return if (raw.startsWith("http://") || raw.startsWith("https://")) raw else "https://$raw"
    }

    fun resolve(input: String, rules: List<ResolveRule>): ResolvedStream {
        val pageUrl = normalize(input)
        val path = pageUrl.substringBefore('?').substringBefore('#')
        if (path.endsWith(".m3u8", ignoreCase = true)) {
            return ResolvedStream(
                pageUrl = pageUrl,
                uuid = "",
                masterUrl = pageUrl,
                referer = "",
                title = pageUrl.substringAfter("://").substringBefore('/'),
                sprite = null,
                spriteUrlTemplate = null,
            )
        }

        val pageHost = runCatching { URI(pageUrl).host }.getOrNull()
        val rule = rules.firstOrNull { it.matches(pageHost) }
            ?: throw ResolveException("没有匹配的解析规则")

        val request = Request.Builder()
            .url(pageUrl)
            .header("User-Agent", rule.userAgent)
            .header("Accept", "text/html,application/xhtml+xml")
            .header("Accept-Language", rule.acceptLanguage)
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw ResolveException("页面返回 HTTP ${response.code}，可能需要先在浏览器里通过站点校验")
            }
            val html = response.body?.string().orEmpty()
            if (html.isBlank()) throw ResolveException("页面内容为空")

            val matched = rule.sourcePatterns.firstNotNullOfOrNull { pattern ->
                val match = pattern.find(html) ?: return@firstNotNullOfOrNull null
                val host = match.groupValues.getOrNull(rule.hostGroup).orEmpty()
                val uuid = match.groupValues.getOrNull(rule.uuidGroup).orEmpty()
                if (host.isBlank() || uuid.isBlank()) null else host to uuid
            } ?: throw ResolveException("页面上没找到视频源，或规则不匹配")

            val cdnHost = matched.first
            val uuid = matched.second
            val masterUrl = rule.masterUrlTemplate
                .replace("{host}", cdnHost)
                .replace("{uuid}", uuid)

            val spriteRule = rule.sprite
            val sprite = spriteRule?.let { parseSprite(html, it) }
            val spriteUrlTemplate = if (sprite != null && spriteRule != null) {
                spriteRule.urlTemplate
                    .replace("{host}", cdnHost)
                    .replace("{uuid}", uuid)
            } else {
                null
            }

            val finalUrl = response.request.url
            val referer = when (rule.refererMode) {
                "page-origin" -> "${finalUrl.scheme}://${finalUrl.host}/"
                else -> ""
            }
            val title = when (rule.titleMode) {
                "page-title" -> titleTag.find(html)?.groupValues?.get(1)
                    ?.replace(Regex("""\s+"""), " ")
                    ?.trim()
                    ?.take(120)
                    .orEmpty()
                else -> ""
            }

            return ResolvedStream(
                pageUrl = finalUrl.toString(),
                uuid = uuid,
                masterUrl = masterUrl,
                referer = referer,
                title = title,
                sprite = sprite,
                spriteUrlTemplate = spriteUrlTemplate,
            )
        }
    }

    private fun parseSprite(html: String, rule: SpriteRule): SpriteConfig? {
        val anchor = html.indexOf(rule.anchor)
        if (anchor < 0) return null
        val region = html.substring(anchor, minOf(html.length, anchor + rule.window.coerceAtLeast(1)))

        fun number(key: String): Int? =
            Regex("""${Regex.escape(key)}\s*:\s*(\d+)""")
                .find(region)
                ?.groupValues
                ?.get(1)
                ?.toIntOrNull()

        val frames = number(rule.frameCountKey) ?: return null
        val width = number(rule.widthKey) ?: return null
        val height = number(rule.heightKey) ?: return null
        val cols = number(rule.colsKey) ?: return null
        val rows = number(rule.rowsKey) ?: return null
        if (frames <= 0 || width <= 0 || height <= 0 || cols <= 0 || rows <= 0) return null

        return SpriteConfig(width, height, cols, rows, frames)
    }
}
