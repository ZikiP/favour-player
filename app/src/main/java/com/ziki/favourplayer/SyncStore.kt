package com.ziki.favourplayer

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileOutputStream

data class PullConfig(
    val url: String = "",
    val token: String = "",
)

class SyncStore(context: Context) {

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("favour_receive", Context.MODE_PRIVATE)
    private val cacheFile = File(appContext.filesDir, "snapshot.json")

    fun loadPullConfig(): PullConfig = PullConfig(
        url = prefs.getString(KEY_PULL_URL, "").orEmpty().trim().trimEnd('/'),
        token = prefs.getString(KEY_PULL_TOKEN, "").orEmpty().trim(),
    )

    fun savePullConfig(config: PullConfig) {
        prefs.edit()
            .putString(KEY_PULL_URL, config.url.trim().trimEnd('/'))
            .putString(KEY_PULL_TOKEN, config.token.trim())
            .apply()
    }

    /** 本机最近一次成功拉取的时刻；0 表示还没成功过 */
    fun loadLastPulledAt(): Long = prefs.getLong(KEY_LAST_PULLED_AT, 0L)

    fun saveLastPulledAt(timestamp: Long) {
        prefs.edit().putLong(KEY_LAST_PULLED_AT, timestamp).apply()
    }

    fun loadCached(): LibrarySnapshot? {
        if (!cacheFile.isFile) return null
        return runCatching { SnapshotParser.parse(cacheFile.readText()) }.getOrNull()
    }

    @Throws(Exception::class)
    fun saveSnapshot(raw: String): LibrarySnapshot {
        val snapshot = SnapshotParser.parse(raw)
        writeAtomically(raw)
        Log.i(TAG, "stored ${snapshot.items.size} items")
        return snapshot
    }

    /**
     * 先写临时文件再 rename，避免写一半被杀导致 snapshot.json 变成截断的 JSON
     * （那样 loadCached 会静默返回 null，资料库看起来是空的）。
     */
    private fun writeAtomically(raw: String) {
        val tmp = File(cacheFile.parentFile, "${cacheFile.name}.tmp")
        try {
            FileOutputStream(tmp).use { out ->
                out.write(raw.toByteArray(Charsets.UTF_8))
                out.flush()
                out.fd.sync()
            }
            if (tmp.renameTo(cacheFile)) return
            Log.w(TAG, "snapshot rename failed, falling back to direct write")
            cacheFile.writeText(raw)
        } finally {
            if (tmp.exists()) tmp.delete()
        }
    }

    companion object {
        private const val KEY_PULL_URL = "pull_url"
        private const val KEY_PULL_TOKEN = "pull_token"
        private const val KEY_LAST_PULLED_AT = "last_pulled_at"
        private const val TAG = "FavourPlayer"
    }
}
