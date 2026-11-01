package com.ziki.favourplayer

import okhttp3.OkHttpClient
import okhttp3.Request
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

    private val seekUuid =
        Regex("""surrit\.com[^0-9a-fA-F]{0,4}([0-9a-fA-F-]{32,36})[^0-9a-fA-F]{0,4}seek""")
    private val playlistUuid =
        Regex("""surrit\.com[^0-9a-fA-F]{0,4}([0-9a-fA-F-]{32,36})[^0-9a-fA-F]{0,4}playlist\.m3u8""")
    private val titleTag = Regex("""<title>(.*?)</title>""", RegexOption.DOT_MATCHES_ALL)

    fun normalize(input: String): String {
        val raw = input.trim()
        if (raw.isEmpty()) throw ResolveException("请输入收藏页地址")
        return if (raw.startsWith("http://") || raw.startsWith("https://")) raw else "https://$raw"
    }

    fun resolve(input: String): ResolvedStream {
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
            )
        }

        val request = Request.Builder()
            .url(pageUrl)
            .header("User-Agent", DESKTOP_UA)
            .header("Accept", "text/html,application/xhtml+xml")
            .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw ResolveException("页面返回 HTTP ${response.code}，可能需要先在浏览器里通过站点校验")
            }
            val html = response.body?.string().orEmpty()
            if (html.isBlank()) throw ResolveException("页面内容为空")

            val uuid = playlistUuid.find(html)?.groupValues?.get(1)
                ?: seekUuid.find(html)?.groupValues?.get(1)
                ?: throw ResolveException("页面上没找到视频源，站点结构可能变了")

            val finalUrl = response.request.url
            val referer = "${finalUrl.scheme}://${finalUrl.host}/"
            val title = titleTag.find(html)?.groupValues?.get(1)
                ?.replace(Regex("""\s+"""), " ")
                ?.trim()
                ?.take(120)
                .orEmpty()

            return ResolvedStream(
                pageUrl = finalUrl.toString(),
                uuid = uuid,
                masterUrl = "https://surrit.com/$uuid/playlist.m3u8",
                referer = referer,
                title = title,
                sprite = parseSprite(html),
            )
        }
    }

    private fun parseSprite(html: String): SpriteConfig? {
        val anchor = html.indexOf("pic_num")
        if (anchor < 0) return null
        val region = html.substring(anchor, minOf(html.length, anchor + 700))

        fun number(key: String): Int? =
            Regex("""$key\s*:\s*(\d+)""").find(region)?.groupValues?.get(1)?.toIntOrNull()

        val frames = number("pic_num") ?: return null
        val width = number("width") ?: return null
        val height = number("height") ?: return null
        val cols = number("col") ?: return null
        val rows = number("row") ?: return null
        if (frames <= 0 || width <= 0 || height <= 0 || cols <= 0 || rows <= 0) return null

        return SpriteConfig(width, height, cols, rows, frames)
    }
}
