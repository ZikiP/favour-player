package com.ziki.favourplayer

import android.content.Context
import java.io.File

const val CACHE_LOCATION_INTERNAL = "internal"
const val CACHE_LOCATION_EXTERNAL = "external"
const val DEFAULT_COVER_CACHE_BYTES = 512L * 1024 * 1024
const val DEFAULT_VIDEO_CACHE_BYTES = 2L * 1024 * 1024 * 1024
private const val MIN_CACHE_BYTES = 64L * 1024 * 1024

data class CacheConfig(
    val coverEnabled: Boolean = true,
    val coverMaxBytes: Long = DEFAULT_COVER_CACHE_BYTES,
    val videoEnabled: Boolean = true,
    val videoMaxBytes: Long = DEFAULT_VIDEO_CACHE_BYTES,
    val location: String = CACHE_LOCATION_INTERNAL,
) {
    val coverKey: String get() = "$coverEnabled|$coverMaxBytes|$location"
    val videoKey: String get() = "$videoEnabled|$videoMaxBytes|$location"
}

data class CacheUsage(
    val enabled: Boolean,
    val usedBytes: Long,
    val maxBytes: Long,
    val entries: Int,
    val path: String,
)

class CacheStore(context: Context) {

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("favour_cache", Context.MODE_PRIVATE)

    fun load(): CacheConfig {
        val savedLocation = prefs.getString(KEY_LOCATION, CACHE_LOCATION_INTERNAL).orEmpty()
        return CacheConfig(
            coverEnabled = prefs.getBoolean(KEY_COVER_ENABLED, true),
            coverMaxBytes = prefs.getLong(KEY_COVER_MAX_BYTES, DEFAULT_COVER_CACHE_BYTES)
                .coerceAtLeast(MIN_CACHE_BYTES),
            videoEnabled = prefs.getBoolean(
                KEY_VIDEO_ENABLED,
                prefs.getBoolean(KEY_LEGACY_ENABLED, true),
            ),
            videoMaxBytes = prefs.getLong(
                KEY_VIDEO_MAX_BYTES,
                prefs.getLong(KEY_LEGACY_MAX_BYTES, DEFAULT_VIDEO_CACHE_BYTES),
            ).coerceAtLeast(MIN_CACHE_BYTES),
            location = if (savedLocation == CACHE_LOCATION_EXTERNAL) {
                CACHE_LOCATION_EXTERNAL
            } else {
                CACHE_LOCATION_INTERNAL
            },
        )
    }

    fun save(config: CacheConfig) {
        prefs.edit()
            .putBoolean(KEY_COVER_ENABLED, config.coverEnabled)
            .putLong(KEY_COVER_MAX_BYTES, config.coverMaxBytes.coerceAtLeast(MIN_CACHE_BYTES))
            .putBoolean(KEY_VIDEO_ENABLED, config.videoEnabled)
            .putLong(KEY_VIDEO_MAX_BYTES, config.videoMaxBytes.coerceAtLeast(MIN_CACHE_BYTES))
            .putString(KEY_LOCATION, config.location)
            .remove(KEY_LEGACY_ENABLED)
            .remove(KEY_LEGACY_MAX_BYTES)
            .apply()
    }

    companion object {
        private const val KEY_COVER_ENABLED = "cover_enabled"
        private const val KEY_COVER_MAX_BYTES = "cover_max_bytes"
        private const val KEY_VIDEO_ENABLED = "video_enabled"
        private const val KEY_VIDEO_MAX_BYTES = "video_max_bytes"
        private const val KEY_LOCATION = "location"
        private const val KEY_LEGACY_ENABLED = "enabled"
        private const val KEY_LEGACY_MAX_BYTES = "max_bytes"

        fun coverDir(context: Context, location: String): File =
            File(baseDir(context, location), "cover-cache")

        fun videoDir(context: Context, location: String): File =
            File(baseDir(context, location), "media-cache")

        private fun baseDir(context: Context, location: String): File =
            if (location == CACHE_LOCATION_EXTERNAL) {
                context.externalCacheDir ?: context.cacheDir
            } else {
                context.cacheDir
            }
    }
}
