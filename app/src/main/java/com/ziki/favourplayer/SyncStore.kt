package com.ziki.favourplayer

import android.content.Context
import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

data class SyncConfig(
    val serverUrl: String,
    val token: String,
)

class SyncException(message: String) : Exception(message)

class SyncStore(context: Context) {

    private val prefs = context.getSharedPreferences("favour_library", Context.MODE_PRIVATE)
    private val cacheFile = File(context.filesDir, "snapshot.json")
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    fun loadConfig(): SyncConfig? {
        val server = prefs.getString(KEY_SERVER, null)?.trim().orEmpty()
        val token = prefs.getString(KEY_TOKEN, null)?.trim().orEmpty()
        if (server.isBlank() || token.isBlank()) return null
        return SyncConfig(server, token)
    }

    fun saveConfig(config: SyncConfig) {
        prefs.edit()
            .putString(KEY_SERVER, config.serverUrl.trim())
            .putString(KEY_TOKEN, config.token.trim())
            .apply()
    }

    fun clearConfig() {
        prefs.edit().clear().apply()
    }

    fun loadCached(): LibrarySnapshot? {
        if (!cacheFile.isFile) return null
        return runCatching { SnapshotParser.parse(cacheFile.readText()) }.getOrNull()
    }

    fun clearCached() {
        runCatching { cacheFile.delete() }
    }

    fun refresh(config: SyncConfig): LibrarySnapshot {
        val base = normalizeBase(config.serverUrl)
        val request = Request.Builder()
            .url("$base/api/items")
            .header("Authorization", "Bearer ${config.token}")
            .header("Accept", "application/json")
            .header("User-Agent", Resolver.DESKTOP_UA)
            .build()

        Log.i(TAG, "sync start: $base")
        client.newCall(request).execute().use { response ->
            if (response.code == 401) throw SyncException("令牌无效或已更换")
            if (!response.isSuccessful) throw SyncException("服务返回 HTTP ${response.code}")
            val raw = response.body?.string().orEmpty()
            val snapshot = SnapshotParser.parse(raw)
            runCatching { cacheFile.writeText(raw) }
            Log.i(TAG, "sync ok: ${snapshot.items.size} items")
            return snapshot
        }
    }

    private fun normalizeBase(input: String): String {
        var base = input.trim().trimEnd('/').removeSuffix("/api/items")
        if (base.isBlank()) throw SyncException("请填写同步服务地址")
        if (!base.startsWith("http://") && !base.startsWith("https://")) {
            base = "https://$base"
        }
        return base
    }

    companion object {
        private const val TAG = "FavourPlayer"
        private const val KEY_SERVER = "server_url"
        private const val KEY_TOKEN = "token"
    }
}
