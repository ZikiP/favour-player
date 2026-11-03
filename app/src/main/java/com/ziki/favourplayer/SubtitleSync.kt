package com.ziki.favourplayer

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * 字幕按「作品」跨设备共享：正文 + 样式存到中间服务，
 * 任何设备打开该作品都能拉到并自动应用。
 *
 * 与偏好同步（按设备隔离）不同，字幕是作品的属性——同一部作品、同一份字幕，
 * 偏移和样式在所有设备上应当一致，所以独立成一个按作品 key 的端点。
 */
object SubtitleSync {

    private val JSON = "application/json; charset=utf-8".toMediaType()

    // 打开作品时拉字幕属于「顺带」，用短超时避免中间服务不可达时后台连接挂太久。
    private val downloadClient by lazy {
        PullClient.client.newBuilder().callTimeout(6, TimeUnit.SECONDS).build()
    }

    /** 作品地址 -> 服务端存储 key（SHA-256 前 24 位，和 deviceId 同风格）。 */
    internal fun keyOf(contentKey: String): String =
        DeviceIdentity.sha256(contentKey).take(24)

    /** 拉取某作品的共享字幕；服务端没有（404）或失败返回 null。 */
    fun download(config: PullConfig, contentKey: String): SharedSubtitle? {
        val base = PullClient.normalizeBase(config.url) ?: return null
        if (config.token.isBlank()) return null
        val request = Request.Builder()
            .url("$base/api/subtitles/${keyOf(contentKey)}")
            .header("Authorization", "Bearer ${config.token}")
            .header("Accept", "application/json")
            .get()
            .build()
        return try {
            downloadClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val root = runCatching { JSONObject(response.body?.string().orEmpty()) }
                    .getOrNull() ?: return null
                val style = root.optJSONObject("style")?.let { SubtitleStyleCodec.decode(it) }
                SharedSubtitle(
                    name = root.optString("name"),
                    text = root.optString("text"),
                    style = style,
                )
            }
        } catch (_: Exception) {
            null
        }
    }

    /** 上传某作品的共享字幕；text 传空表示只更新样式、保留服务端已有正文。 */
    fun upload(
        config: PullConfig,
        contentKey: String,
        name: String,
        text: String,
        style: SubtitleStyle,
    ): Boolean {
        val base = PullClient.normalizeBase(config.url) ?: return false
        if (config.token.isBlank()) return false
        val body = JSONObject()
            .put("name", name)
            .put("text", text)
            .put("style", SubtitleStyleCodec.encode(style))
            .toString()
        val request = Request.Builder()
            .url("$base/api/subtitles/${keyOf(contentKey)}")
            .header("Authorization", "Bearer ${config.token}")
            .header("Accept", "application/json")
            .put(body.toRequestBody(JSON))
            .build()
        return try {
            PullClient.client.newCall(request).execute().use { it.isSuccessful }
        } catch (_: Exception) {
            false
        }
    }

    /** 删除某作品的共享字幕。 */
    fun delete(config: PullConfig, contentKey: String): Boolean {
        val base = PullClient.normalizeBase(config.url) ?: return false
        if (config.token.isBlank()) return false
        val request = Request.Builder()
            .url("$base/api/subtitles/${keyOf(contentKey)}")
            .header("Authorization", "Bearer ${config.token}")
            .delete()
            .build()
        return try {
            PullClient.client.newCall(request).execute().use { it.isSuccessful }
        } catch (_: Exception) {
            false
        }
    }
}

/** 从中间服务拉到的共享字幕。text 可能为空（服务端只存了样式）。 */
data class SharedSubtitle(
    val name: String,
    val text: String,
    val style: SubtitleStyle?,
)
