package com.ziki.favourplayer

import android.content.Context
import org.json.JSONObject

data class PlaybackProgress(
    val positionMs: Long,
    val durationMs: Long,
    val updatedAt: Long,
) {
    val fraction: Float
        get() = if (durationMs > 0L) {
            (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)
        } else {
            0f
        }

    val remainingMs: Long
        get() = (durationMs - positionMs).coerceAtLeast(0L)

    val resumable: Boolean
        get() = durationMs > 0L &&
            positionMs >= MIN_RESUME_MS &&
            positionMs < durationMs - MIN_RESUME_MS

    companion object {
        const val MIN_RESUME_MS = 30_000L
    }
}

class PlaybackStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("favour_playback", Context.MODE_PRIVATE)

    @Synchronized
    fun all(): Map<String, PlaybackProgress> {
        val raw = prefs.getString(KEY_PROGRESS, null) ?: return emptyMap()
        val root = runCatching { JSONObject(raw) }.getOrNull() ?: return emptyMap()
        val result = LinkedHashMap<String, PlaybackProgress>()
        for (key in root.keys()) {
            val entry = root.optJSONObject(key) ?: continue
            val position = entry.optLong("position")
            val duration = entry.optLong("duration")
            val updatedAt = entry.optLong("updated")
            if (position <= 0L || duration <= 0L) continue
            val progress = PlaybackProgress(position, duration, updatedAt)
            if (progress.resumable) result[key] = progress
        }
        return result
    }

    @Synchronized
    fun load(key: String): PlaybackProgress? = all()[key]

    @Synchronized
    fun save(key: String, positionMs: Long, durationMs: Long) {
        if (key.isBlank() || durationMs <= 0L) return
        val root = readRoot()
        val position = positionMs.coerceIn(0L, durationMs)
        val progress = PlaybackProgress(position, durationMs, System.currentTimeMillis())
        if (!progress.resumable) {
            root.remove(key)
        } else {
            root.put(
                key,
                JSONObject()
                    .put("position", position)
                    .put("duration", durationMs)
                    .put("updated", progress.updatedAt),
            )
            trim(root)
        }
        prefs.edit().putString(KEY_PROGRESS, root.toString()).apply()
    }

    @Synchronized
    fun clear(key: String) {
        val root = readRoot()
        if (root.remove(key) == null) return
        prefs.edit().putString(KEY_PROGRESS, root.toString()).apply()
    }

    private fun readRoot(): JSONObject {
        val raw = prefs.getString(KEY_PROGRESS, null) ?: return JSONObject()
        return runCatching { JSONObject(raw) }.getOrElse { JSONObject() }
    }

    private fun trim(root: JSONObject) {
        val keys = root.keys().asSequence().sortedByDescending { key ->
            root.optJSONObject(key)?.optLong("updated") ?: 0L
        }.toList()
        if (keys.size <= MAX_ENTRIES) return
        keys.drop(MAX_ENTRIES).forEach(root::remove)
    }

    private companion object {
        const val KEY_PROGRESS = "progress"
        const val MAX_ENTRIES = 200
    }
}
