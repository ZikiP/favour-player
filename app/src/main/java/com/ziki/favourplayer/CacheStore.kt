package com.ziki.favourplayer

import android.content.Context
import java.io.File

const val CACHE_LOCATION_INTERNAL = "internal"
const val CACHE_LOCATION_EXTERNAL = "external"
const val DEFAULT_CACHE_MAX_BYTES = 2L * 1024 * 1024 * 1024
private const val MIN_CACHE_BYTES = 64L * 1024 * 1024

data class CacheConfig(
    val enabled: Boolean = true,
    val maxBytes: Long = DEFAULT_CACHE_MAX_BYTES,
    val location: String = CACHE_LOCATION_INTERNAL,
)

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
            enabled = prefs.getBoolean(KEY_ENABLED, true),
            maxBytes = prefs.getLong(KEY_MAX_BYTES, DEFAULT_CACHE_MAX_BYTES)
                .coerceAtLeast(MIN_CACHE_BYTES),
            location = if (savedLocation == CACHE_LOCATION_EXTERNAL) {
                CACHE_LOCATION_EXTERNAL
            } else {
                CACHE_LOCATION_INTERNAL
            },
        )
    }

    fun save(config: CacheConfig) {
        prefs.edit()
            .putBoolean(KEY_ENABLED, config.enabled)
            .putLong(KEY_MAX_BYTES, config.maxBytes.coerceAtLeast(MIN_CACHE_BYTES))
            .putString(KEY_LOCATION, config.location)
            .apply()
    }

    companion object {
        private const val KEY_ENABLED = "enabled"
        private const val KEY_MAX_BYTES = "max_bytes"
        private const val KEY_LOCATION = "location"

        fun cacheDir(context: Context, location: String): File {
            val base = if (location == CACHE_LOCATION_EXTERNAL) {
                context.externalCacheDir ?: context.cacheDir
            } else {
                context.cacheDir
            }
            return File(base, "media-cache")
        }
    }
}
