package com.ziki.favourplayer

import android.content.Context
import android.os.Build
import android.provider.Settings
import android.util.Log
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * 设备标识。用 Android ID 派生：同一台设备卸载重装后取值不变，
 * 所以要「重装也能找回自己的设置」就必须靠它，不能用随机 UUID。
 */
object DeviceIdentity {

    fun id(context: Context): String {
        val androidId = runCatching {
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        }.getOrNull().orEmpty()
        val seed = if (androidId.isBlank()) {
            fallbackSeed(context)
        } else {
            "$androidId:${context.packageName}"
        }
        return sha256(seed).take(24)
    }

    fun label(): String {
        val raw = listOf(Build.MANUFACTURER, Build.BRAND, Build.MODEL)
            .map { it.orEmpty().trim() }
            .filter { it.isNotEmpty() }
            .distinctBy { it.lowercase() }
            .joinToString(" ")
        return raw.ifEmpty { "Android" }.take(60)
    }

    /** 极少数设备读不到 ANDROID_ID，退回随机值：能同步，但重装后会换身份。 */
    private fun fallbackSeed(context: Context): String {
        val prefs = context.applicationContext
            .getSharedPreferences("favour_profile", Context.MODE_PRIVATE)
        prefs.getString("fallback_seed", null)?.let { return it }
        val generated = java.util.UUID.randomUUID().toString()
        prefs.edit().putString("fallback_seed", generated).apply()
        return generated
    }

    internal fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
            .joinToString("") { "%02x".format(it.toInt() and 0xFF) }
}

/** 把散落在各处的偏好打包成一份 JSON，也能反向写回去。 */
object SettingsBackup {

    const val SCHEMA_VERSION = 1

    fun collect(context: Context): JSONObject {
        val settings = JSONObject()

        val player = PlayerPrefs(context).load()
        settings.put(
            "player",
            JSONObject().apply {
                put("defaultSpeed", player.defaultSpeed.toDouble())
                put("autoHideMs", player.autoHideMs)
                put("doubleTapMs", player.doubleTapMs)
                put("controlBottomPortrait", player.controlBottomPortrait)
                put("controlBottomLandscape", player.controlBottomLandscape)
                put("sharpen", player.sharpen.name)
                put("frameRateMatch", player.frameRateMatch)
            },
        )

        val styles = SubtitleStyleStore(context)
        settings.put("subtitleStyle", SubtitleStyleCodec.encode(styles.load()))
        settings.put("subtitleItems", styles.exportItems())

        val cache = CacheStore(context).load()
        settings.put(
            "cache",
            JSONObject().apply {
                put("coverEnabled", cache.coverEnabled)
                put("coverMaxBytes", cache.coverMaxBytes)
                put("videoEnabled", cache.videoEnabled)
                put("videoMaxBytes", cache.videoMaxBytes)
            },
        )

        RuleStore(context).userJson().takeIf { it.isNotBlank() }?.let { rules ->
            settings.put("rules", rules)
        }

        return settings
    }

    /**
     * 写回设置。每一项都做范围校验，坏数据直接回落到当前值，
     * 不会把播放器搞成非法状态。
     */
    fun apply(context: Context, settings: JSONObject) {
        settings.optJSONObject("player")?.let { json ->
            val store = PlayerPrefs(context)
            val current = store.load()
            store.save(
                current.copy(
                    defaultSpeed = json.optDouble("defaultSpeed", current.defaultSpeed.toDouble())
                        .toFloat()
                        .takeIf { it in PlayerPrefs.SPEEDS } ?: current.defaultSpeed,
                    autoHideMs = json.optInt("autoHideMs", current.autoHideMs)
                        .takeIf { it in PlayerPrefs.AUTO_HIDE } ?: current.autoHideMs,
                    doubleTapMs = json.optLong("doubleTapMs", current.doubleTapMs)
                        .takeIf { it in PlayerPrefs.DOUBLE_TAP } ?: current.doubleTapMs,
                    controlBottomPortrait = json
                        .optInt("controlBottomPortrait", current.controlBottomPortrait)
                        .coerceIn(0, PlayerPrefs.CONTROL_BOTTOM_MAX),
                    controlBottomLandscape = json
                        .optInt("controlBottomLandscape", current.controlBottomLandscape)
                        .coerceIn(0, PlayerPrefs.CONTROL_BOTTOM_MAX),
                    sharpen = json.optString("sharpen").let { name ->
                        SharpenLevel.values().firstOrNull { it.name == name }
                    } ?: current.sharpen,
                    frameRateMatch = json.optBoolean("frameRateMatch", current.frameRateMatch),
                ),
            )
        }

        val styles = SubtitleStyleStore(context)
        settings.optJSONObject("subtitleStyle")?.let { styles.save(SubtitleStyleCodec.decode(it)) }
        settings.optJSONObject("subtitleItems")?.let { styles.importItems(it) }

        settings.optJSONObject("cache")?.let { json ->
            val store = CacheStore(context)
            val current = store.load()
            store.save(
                current.copy(
                    coverEnabled = json.optBoolean("coverEnabled", current.coverEnabled),
                    coverMaxBytes = json.optLong("coverMaxBytes", current.coverMaxBytes),
                    videoEnabled = json.optBoolean("videoEnabled", current.videoEnabled),
                    videoMaxBytes = json.optLong("videoMaxBytes", current.videoMaxBytes),
                ),
            )
        }

        settings.optString("rules").takeIf { it.isNotBlank() }?.let { rules ->
            RuleStore(context).saveUserRules(rules)
        }
    }
}

class ProfileException(message: String) : Exception(message)

data class ProfileSummary(
    val deviceId: String,
    val label: String,
    val customLabel: String,
    val displayLabel: String,
    val updatedAt: Long,
)

object ProfileClient {

    private val JSON = "application/json; charset=utf-8".toMediaType()

    /** 恢复走短超时，避免启动时卡白屏太久。 */
    private val restoreClient by lazy {
        PullClient.client.newBuilder()
            .callTimeout(6, TimeUnit.SECONDS)
            .build()
    }

    fun upload(config: PullConfig, deviceId: String, label: String, settings: JSONObject) {
        val base = baseOf(config)
        val body = JSONObject()
            .put("schemaVersion", SettingsBackup.SCHEMA_VERSION)
            .put("label", label)
            .put("settings", settings)
            .toString()

        val request = Request.Builder()
            .url("$base/api/profiles/$deviceId")
            .header("Authorization", "Bearer ${config.token}")
            .header("Accept", "application/json")
            .put(body.toRequestBody(JSON))
            .build()

        try {
            PullClient.client.newCall(request).execute().use { response ->
                when {
                    response.code == 401 -> throw ProfileException("令牌无效")
                    response.isSuccessful -> return
                    else -> throw ProfileException("上传失败：HTTP ${response.code}")
                }
            }
        } catch (error: ProfileException) {
            throw error
        } catch (error: java.io.IOException) {
            throw ProfileException(connectionMessage(error))
        }
    }

    /** 返回 null 表示服务端还没这台设备的记录。 */
    fun download(config: PullConfig, deviceId: String): JSONObject? {
        val base = baseOf(config)
        val request = Request.Builder()
            .url("$base/api/profiles/$deviceId")
            .header("Authorization", "Bearer ${config.token}")
            .header("Accept", "application/json")
            .get()
            .build()

        try {
            restoreClient.newCall(request).execute().use { response ->
                when {
                    response.code == 401 -> throw ProfileException("令牌无效")
                    response.code == 404 -> return null
                    !response.isSuccessful -> throw ProfileException("读取失败：HTTP ${response.code}")
                }
                val raw = response.body?.string().orEmpty()
                if (raw.isBlank()) return null
                return JSONObject(raw).optJSONObject("settings")
            }
        } catch (error: ProfileException) {
            throw error
        } catch (error: java.io.IOException) {
            throw ProfileException(connectionMessage(error))
        }
    }

    /** 把某台设备的配置复制到另一台（一般是复制到本机）。 */
    fun copy(config: PullConfig, sourceId: String, targetId: String) {
        val base = baseOf(config)
        val request = Request.Builder()
            .url("$base/api/profiles/$sourceId/copy")
            .header("Authorization", "Bearer ${config.token}")
            .header("Content-Type", "application/json")
            .post(
                JSONObject().put("targetDeviceId", targetId).toString()
                    .toRequestBody(JSON),
            )
            .build()
        try {
            PullClient.client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw ProfileException("复制失败：HTTP ${response.code}")
                }
            }
        } catch (error: ProfileException) {
            throw error
        } catch (error: java.io.IOException) {
            throw ProfileException(connectionMessage(error))
        }
    }

    fun delete(config: PullConfig, deviceId: String) {
        val base = baseOf(config)
        val request = Request.Builder()
            .url("$base/api/profiles/$deviceId")
            .header("Authorization", "Bearer ${config.token}")
            .delete()
            .build()
        try {
            PullClient.client.newCall(request).execute().use { response ->
                if (!response.isSuccessful && response.code != 404) {
                    throw ProfileException("删除失败：HTTP ${response.code}")
                }
            }
        } catch (error: ProfileException) {
            throw error
        } catch (error: java.io.IOException) {
            throw ProfileException(connectionMessage(error))
        }
    }

    fun list(config: PullConfig): List<ProfileSummary> {
        val base = baseOf(config)
        val request = Request.Builder()
            .url("$base/api/profiles")
            .header("Authorization", "Bearer ${config.token}")
            .header("Accept", "application/json")
            .get()
            .build()
        try {
            restoreClient.newCall(request).execute().use { response ->
                when {
                    response.code == 401 -> throw ProfileException("令牌无效")
                    !response.isSuccessful -> throw ProfileException("读取失败：HTTP ${response.code}")
                }
                val raw = response.body?.string().orEmpty()
                val array = JSONObject(raw).optJSONArray("profiles") ?: return emptyList()
                return buildList {
                    for (index in 0 until array.length()) {
                        val item = array.optJSONObject(index) ?: continue
                        val deviceId = item.optString("deviceId")
                        if (deviceId.isBlank()) continue
                        add(
                            ProfileSummary(
                                deviceId = deviceId,
                                label = item.optString("label"),
                                customLabel = item.optString("customLabel"),
                                displayLabel = item.optString("displayLabel")
                                    .ifBlank { item.optString("label") },
                                updatedAt = item.optLong("updatedAt"),
                            ),
                        )
                    }
                }
            }
        } catch (error: ProfileException) {
            throw error
        } catch (error: java.io.IOException) {
            throw ProfileException(connectionMessage(error))
        }
    }

    private fun baseOf(config: PullConfig): String {
        val base = PullClient.normalizeBase(config.url)
            ?: throw ProfileException("请先填写中间服务地址")
        if (config.token.isBlank()) throw ProfileException("请先填写中间服务令牌")
        return base
    }

    private fun connectionMessage(error: java.io.IOException): String =
        "连不上中间服务，检查 Tailscale 是否开启"
}

/**
 * 偏好同步的编排：启动时按需恢复，离开 App 时按内容哈希去重上传。
 * 只同步「用户偏好」，不同步服务地址/令牌（安全）和缓存文件本身。
 */
object ProfileSync {

    private const val TAG = "FavourPlayer"
    private const val PREFS = "favour_profile"
    private const val KEY_RESTORED = "restored"
    private const val KEY_LAST_UPLOAD = "last_upload_at"
    private const val KEY_LAST_HASH = "last_hash"
    private const val KEY_LAST_ERROR = "last_error"
    private const val KEY_LAST_RESULT = "last_result"

    data class Status(
        val deviceId: String,
        val label: String,
        val lastUploadAt: Long,
        val lastError: String,
        val lastResult: String,
        val configured: Boolean,
    )

    fun status(context: Context): Status {
        val appContext = context.applicationContext
        val prefs = prefs(appContext)
        val config = SyncStore(appContext).loadPullConfig()
        return Status(
            deviceId = DeviceIdentity.id(appContext),
            label = DeviceIdentity.label(),
            lastUploadAt = prefs.getLong(KEY_LAST_UPLOAD, 0L),
            lastError = prefs.getString(KEY_LAST_ERROR, "").orEmpty(),
            lastResult = prefs.getString(KEY_LAST_RESULT, "").orEmpty(),
            configured = config.url.isNotBlank() && config.token.isNotBlank(),
        )
    }

    /** 值得为它显示一次「正在恢复设置」的启动。 */
    fun needsRestore(context: Context): Boolean {
        val appContext = context.applicationContext
        if (prefs(appContext).getBoolean(KEY_RESTORED, false)) return false
        val config = SyncStore(appContext).loadPullConfig()
        return config.url.isNotBlank() && config.token.isNotBlank()
    }

    /** 从服务端恢复设置。返回 true 表示真的写入了远端配置。 */
    fun restore(context: Context): Boolean {
        val appContext = context.applicationContext
        val prefs = prefs(appContext)
        if (prefs.getBoolean(KEY_RESTORED, false)) return false
        val config = SyncStore(appContext).loadPullConfig()
        if (config.url.isBlank() || config.token.isBlank()) return false

        val deviceId = DeviceIdentity.id(appContext)
        return try {
            val settings = ProfileClient.download(config, deviceId)
            if (settings == null) {
                // 服务端也没有，正常初始化，别每次启动都来问一遍
                prefs.edit().putBoolean(KEY_RESTORED, true).apply()
                false
            } else {
                SettingsBackup.apply(appContext, settings)
                prefs.edit()
                    .putBoolean(KEY_RESTORED, true)
                    .putString(KEY_LAST_ERROR, "")
                    .putString(KEY_LAST_RESULT, "已从服务端恢复")
                    .apply()
                Log.i(TAG, "profile restored from server")
                true
            }
        } catch (error: Exception) {
            prefs.edit().putString(KEY_LAST_ERROR, error.message.orEmpty()).apply()
            Log.w(TAG, "profile restore failed", error)
            false
        }
    }

    /** 上传当前设置；内容没变化且不是强制时直接跳过。返回是否成功。 */
    fun upload(context: Context, force: Boolean = false): Boolean {
        val appContext = context.applicationContext
        val prefs = prefs(appContext)
        // 干净模式：偏好里有「按作品单独调过的字幕样式」，key 就是片源地址，
        // 上传上去等于暴露看过哪些片，所以不推
        if (CleanMode.enabled) {
            Log.i(TAG, "profile upload skipped: clean mode")
            return false
        }
        val config = SyncStore(appContext).loadPullConfig()
        if (config.url.isBlank() || config.token.isBlank()) {
            prefs.edit().putString(KEY_LAST_ERROR, "未配置中间服务").apply()
            return false
        }
        // 还没完成过一次恢复（例如首次启动时断网）就先别上传，
        // 否则会用默认值把服务端已有的配置盖掉
        if (!force && !prefs.getBoolean(KEY_RESTORED, false)) {
            Log.i(TAG, "profile upload skipped: restore not settled")
            return false
        }

        val settings = SettingsBackup.collect(appContext)
        val hash = DeviceIdentity.sha256(settings.toString())
        // 即使内容没变也照常推一次：服务端的「同步于」要跟着这次动作走，
        // 否则用户点了刷新却看到时间没变，会以为没同步上
        return try {
            ProfileClient.upload(
                config = config,
                deviceId = DeviceIdentity.id(appContext),
                label = DeviceIdentity.label(),
                settings = settings,
            )
            prefs.edit()
                .putBoolean(KEY_RESTORED, true)
                .putString(KEY_LAST_HASH, hash)
                .putLong(KEY_LAST_UPLOAD, System.currentTimeMillis())
                .putString(KEY_LAST_ERROR, "")
                .putString(KEY_LAST_RESULT, "已上传")
                .apply()
            Log.i(TAG, "profile uploaded")
            true
        } catch (error: Exception) {
            prefs.edit().putString(KEY_LAST_ERROR, error.message.orEmpty()).apply()
            Log.w(TAG, "profile upload failed", error)
            false
        }
    }

    /** 手动从服务端拉取并覆盖本地，返回是否成功。 */
    fun pullNow(context: Context): Result<Boolean> {
        val appContext = context.applicationContext
        val config = SyncStore(appContext).loadPullConfig()
        return runCatching {
            val settings = ProfileClient.download(config, DeviceIdentity.id(appContext))
                ?: return Result.success(false)
            SettingsBackup.apply(appContext, settings)
            prefs(appContext).edit()
                .putBoolean(KEY_RESTORED, true)
                .putString(KEY_LAST_ERROR, "")
                .putString(KEY_LAST_RESULT, "已从服务端恢复")
                .apply()
            Result.success(true)
        }.getOrElse { error ->
            prefs(appContext).edit().putString(KEY_LAST_ERROR, error.message.orEmpty()).apply()
            Result.failure(error)
        }
    }

    fun forget(context: Context) {
        val appContext = context.applicationContext
        runCatching {
            ProfileClient.delete(SyncStore(appContext).loadPullConfig(), DeviceIdentity.id(appContext))
        }
        prefs(appContext).edit()
            .remove(KEY_LAST_UPLOAD)
            .remove(KEY_LAST_HASH)
            .remove(KEY_LAST_ERROR)
            .putString(KEY_LAST_RESULT, "已删除服务端记录")
            .apply()
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
