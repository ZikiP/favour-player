package com.ziki.favourplayer

import android.content.Context
import android.util.Log
import java.io.File

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

    fun loadCached(): LibrarySnapshot? {
        if (!cacheFile.isFile) return null
        return runCatching { SnapshotParser.parse(cacheFile.readText()) }.getOrNull()
    }

    @Throws(Exception::class)
    fun saveSnapshot(raw: String): LibrarySnapshot {
        val snapshot = SnapshotParser.parse(raw)
        cacheFile.writeText(raw)
        Log.i(TAG, "stored ${snapshot.items.size} items")
        return snapshot
    }

    companion object {
        private const val KEY_PULL_URL = "pull_url"
        private const val KEY_PULL_TOKEN = "pull_token"
        private const val TAG = "FavourPlayer"
    }
}
