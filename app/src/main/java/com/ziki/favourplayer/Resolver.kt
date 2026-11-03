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
    /**
     * 解析页面时实际用的 User-Agent。
     *
     * 拉流必须用同一个 UA：不少 CDN 会把「页面请求的 UA」和「媒体请求的 UA」
     * 做一致性校验，不一致就 403。之前这里写死 DESKTOP_UA，
     * 和规则里配置的 userAgent 对不上，等于自己把请求拒了。
     */
    val userAgent: String = Resolver.DESKTOP_UA,
)

class ResolveException(message: String) : Exception(message)

object Resolver {
    const val DESKTOP_UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

    private val client get() = HttpClients.client

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
            val referer = buildReferer(rule.refererMode, finalUrl.toString())
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
                // 拉流用和解析页面同一个 UA，避免 CDN 的一致性校验把请求拒了
                userAgent = rule.userAgent,
            )
        }
    }

    /**
     * 计算拉流请求要带的 Referer。
     *
     * 这是能不能播的关键。实测过 missav.live 的 CDN（surrit.com）：
     * 带 Referer → 200 拿到 m3u8；不带 → 403「Attention Required! | Cloudflare」。
     * 而且 Origin 形式（`https://missav.live/`）就够用，完整页面 URL 也可以。
     *
     * 所以规则没显式写 `refererMode`（或写成未知值）时，这里用页面 origin 兜底，
     * 而不是留空 —— 留空等于让 CDN 拒掉，而用户完全看不出原因。
     */
    internal fun buildReferer(refererMode: String, pageUrl: String): String {
        val origin = runCatching {
            val uri = URI(pageUrl)
            // 用 authority 而不是 host：后者不含端口，本地自建站点会拼错
            "${uri.scheme}://${uri.authority}/"
        }.getOrNull().orEmpty()
        return when (refererMode) {
            // 完整页面 URL：防盗链校验 Referer 时，有些站点比的是完整地址（含路径）
            "page-url" -> pageUrl
            // 显式不要 Referer
            "none" -> ""
            // 默认与未知值都兜底成 origin：宁可多带一个头，也别被 CDN 一刀切掉
            else -> origin
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
