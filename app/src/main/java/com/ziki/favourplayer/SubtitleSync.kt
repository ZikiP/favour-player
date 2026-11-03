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

    /**
     * 把本机收藏里的作品地址按服务端 key 建索引，用来反查清单里的条目。
     *
     * 服务端只认 URL 摘要，不存 URL 原文，所以反查只能在本机做。
     * 同一部作品的不同版本是不同 URL，天然是两个 key。
     */
    internal fun keyIndex(contentKeys: List<String>): Map<String, String> =
        contentKeys.asSequence()
            .filter { it.isNotBlank() }
            .associateBy { keyOf(it) }

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
                val style = root.optJSONObject("style")
                    ?.takeIf { it.length() > 0 }
                    ?.let { SubtitleStyleCodec.decode(it) }
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

    /**
     * 上传某作品的共享字幕；text 传空表示只更新样式、保留服务端已有正文。
     *
     * [clientUpdatedAt] 是这份字幕在本机最后改动的时间，服务端拿它裁决先后，
     * 免得旧设备的数据把新改的顶掉。
     */
    fun upload(
        config: PullConfig,
        contentKey: String,
        name: String,
        text: String,
        style: SubtitleStyle?,
        clientUpdatedAt: Long = 0L,
    ): Boolean {
        val base = PullClient.normalizeBase(config.url) ?: return false
        if (config.token.isBlank()) return false
        val payload = JSONObject()
            .put("name", name)
            .put("text", text)
        // style 传 null 表示「这次不动样式」，服务端保留原有那份
        style?.let { payload.put("style", SubtitleStyleCodec.encode(it)) }
        if (clientUpdatedAt > 0L) payload.put("clientUpdatedAt", clientUpdatedAt)
        val body = payload.toString()
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

    /**
     * 服务端已有的全部共享字幕清单（只有摘要，不含正文）。
     *
     * 「点同步」时先拿这份清单，再按需下载正文，避免一次性传几 MB。
     */
    fun list(config: PullConfig): List<SharedSubtitleMeta> {
        val base = PullClient.normalizeBase(config.url) ?: return emptyList()
        if (config.token.isBlank()) return emptyList()
        val request = Request.Builder()
            .url("$base/api/subtitles")
            .header("Authorization", "Bearer ${config.token}")
            .header("Accept", "application/json")
            .get()
            .build()
        return try {
            downloadClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return emptyList()
                parseSubtitleList(response.body?.string().orEmpty())
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    /** 把服务端清单 JSON 解成条目；字段缺失或格式不对就当没有。 */
    internal fun parseSubtitleList(raw: String): List<SharedSubtitleMeta> {
        val root = runCatching { JSONObject(raw) }.getOrNull() ?: return emptyList()
        val array = root.optJSONArray("subtitles") ?: return emptyList()
        return buildList {
            for (index in 0 until array.length()) {
                val entry = array.optJSONObject(index) ?: continue
                val key = entry.optString("key").trim()
                if (key.isBlank()) continue
                add(
                    SharedSubtitleMeta(
                        key = key,
                        name = entry.optString("name"),
                        updatedAt = entry.optLong("updatedAt", 0L),
                        clientUpdatedAt = entry.optLong("clientUpdatedAt", 0L),
                        textLen = entry.optInt("textLen", 0),
                        // 空对象代表服务端没存过样式，别拿默认值把本地设置冲掉
                        style = entry.optJSONObject("style")
                            ?.takeIf { it.length() > 0 }
                            ?.let { SubtitleStyleCodec.decode(it) },
                    ),
                )
            }
        }
    }

    /**
     * 把本机已有的字幕全部推上去。
     *
     * 这是「点同步」的前半步：挂字幕、调偏移往往发生在没开 Tailscale 的时候
     * （那会儿连不上中间服务），所以上传只能在同步时补。
     * 返回推成功的条数。
     */
    fun pushAll(
        config: PullConfig,
        subtitleStore: SubtitleStore,
        styleStore: SubtitleStyleStore,
    ): Int {
        val base = PullClient.normalizeBase(config.url) ?: return 0
        if (config.token.isBlank()) return 0
        // 有正文的 + 只调过样式的，都算「已经设置好的作品」
        val keys = (subtitleStore.allKeys() + styleStore.itemKeys())
            .distinct()
            .take(MAX_SYNC_ITEMS)
        var pushed = 0
        for (contentKey in keys) {
            val local = subtitleStore.loadText(contentKey)
            val hasStyle = styleStore.hasOverride(contentKey)
            if (local == null && !hasStyle) continue
            val ok = upload(
                config,
                contentKey,
                local?.first.orEmpty(),
                local?.second.orEmpty(),
                // 没有单独调过样式就传 null，让服务端保留原有样式
                if (hasStyle) styleStore.load(contentKey) else null,
                subtitleStore.localAt(contentKey),
            )
            if (ok) {
                pushed += 1
                subtitleStore.saveSharedAt(contentKey, System.currentTimeMillis())
            }
        }
        return pushed
    }

    /**
     * 点同步时的字幕双向同步：先把本机已有的推上去，再把服务端更新的拉下来。
     *
     * 只处理本机收藏里有的作品——服务端只认 URL 摘要，反查只能在本机做。
     *
     * 阻塞调用，必须放在 IO 线程。返回统计用于界面提示。
     */
    fun syncAll(
        config: PullConfig,
        contentKeys: List<String>,
        subtitleStore: SubtitleStore,
        styleStore: SubtitleStyleStore,
    ): SubtitleSyncResult {
        val pushed = pushAll(config, subtitleStore, styleStore)
        if (contentKeys.isEmpty()) return SubtitleSyncResult(pushed = pushed)
        val metas = list(config)
        if (metas.isEmpty()) return SubtitleSyncResult(pushed = pushed)

        // 收藏里的每一版都有自己的 URL，服务端 key 是 URL 的摘要，按 key 反查
        val byKey = keyIndex(contentKeys)

        var updated = 0
        var skipped = 0
        var failed = 0
        for (meta in metas.take(MAX_SYNC_ITEMS)) {
            val contentKey = byKey[meta.key] ?: continue
            // 本机这份不比服务端旧（刚推上去的就是这种情况），不用往回拉
            if (meta.clientUpdatedAt > 0L &&
                meta.clientUpdatedAt <= subtitleStore.localAt(contentKey)
            ) {
                skipped += 1
                continue
            }
            val known = subtitleStore.sharedAt(contentKey)
            if (meta.updatedAt > 0L && known >= meta.updatedAt) {
                skipped += 1
                continue
            }
            val remote = download(config, contentKey)
            if (remote == null) {
                failed += 1
                continue
            }
            if (remote.text.isNotBlank()) {
                subtitleStore.saveText(
                    contentKey,
                    remote.name.ifBlank { meta.name },
                    remote.text,
                )
            }
            remote.style?.let { styleStore.save(contentKey, it) }
            // 用服务端记的改动时间对齐本地，避免下次同步又把它当成本机新改动推回去
            if (meta.clientUpdatedAt > 0L) {
                subtitleStore.markLocalAt(contentKey, meta.clientUpdatedAt)
            }
            if (meta.updatedAt > 0L) subtitleStore.saveSharedAt(contentKey, meta.updatedAt)
            updated += 1
        }
        return SubtitleSyncResult(
            pushed = pushed,
            updated = updated,
            skipped = skipped,
            failed = failed,
        )
    }

    /** 一次同步最多处理多少部作品，防止清单异常时把界面拖住。 */
    private const val MAX_SYNC_ITEMS = 200
}

/** 服务端字幕清单里的一条摘要，正文要再单独拉。 */
data class SharedSubtitleMeta(
    val key: String,
    val name: String,
    val updatedAt: Long,
    /** 这份字幕在写入方本机最后改动的时间，用来比先后 */
    val clientUpdatedAt: Long,
    val textLen: Int,
    val style: SubtitleStyle?,
)

/** 一次「同步字幕」的结果统计。 */
data class SubtitleSyncResult(
    /** 本机推上去的条数 */
    val pushed: Int = 0,
    /** 从服务端拉下来并覆盖本地的条数 */
    val updated: Int = 0,
    val skipped: Int = 0,
    val failed: Int = 0,
)

/** 从中间服务拉到的共享字幕。text 可能为空（服务端只存了样式）。 */
data class SharedSubtitle(
    val name: String,
    val text: String,
    val style: SubtitleStyle?,
)
