package com.ziki.favourplayer

import android.content.Context
import android.util.Log
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import java.io.File

object MediaCache {

    private val lock = Any()
    private var cache: SimpleCache? = null
    private var databaseProvider: StandaloneDatabaseProvider? = null
    private var activeConfig: CacheConfig? = null

    fun dataSourceFactory(context: Context, upstream: DataSource.Factory): DataSource.Factory {
        val config = CacheStore(context).load()
        if (!config.enabled) return upstream
        val simpleCache = synchronized(lock) { ensure(context, config) } ?: return upstream
        return CacheDataSource.Factory()
            .setCache(simpleCache)
            .setUpstreamDataSourceFactory(upstream)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
    }

    fun usage(context: Context): CacheUsage {
        val config = CacheStore(context).load()
        val dir = CacheStore.cacheDir(context, config.location)
        val simpleCache = if (config.enabled) {
            synchronized(lock) { ensure(context, config) }
        } else {
            null
        }
        return CacheUsage(
            enabled = config.enabled,
            usedBytes = simpleCache?.cacheSpace ?: dirLength(dir),
            maxBytes = config.maxBytes,
            entries = simpleCache?.keys?.size ?: 0,
            path = dir.absolutePath,
        )
    }

    fun clear(context: Context) {
        synchronized(lock) {
            releaseLocked()
            CacheStore.cacheDir(context, CACHE_LOCATION_INTERNAL).deleteRecursively()
            CacheStore.cacheDir(context, CACHE_LOCATION_EXTERNAL).deleteRecursively()
        }
    }

    fun release() {
        synchronized(lock) { releaseLocked() }
    }

    private fun ensure(context: Context, config: CacheConfig): SimpleCache? {
        if (cache != null && activeConfig == config) return cache

        releaseLocked()
        val dir = CacheStore.cacheDir(context, config.location)
        dir.mkdirs()
        val provider = StandaloneDatabaseProvider(context)
        return try {
            val created = SimpleCache(
                dir,
                LeastRecentlyUsedCacheEvictor(config.maxBytes),
                provider,
            )
            cache = created
            databaseProvider = provider
            activeConfig = config
            created
        } catch (error: Throwable) {
            Log.e("FavourPlayer", "media cache init failed", error)
            runCatching { provider.close() }
            null
        }
    }

    private fun releaseLocked() {
        runCatching { cache?.release() }
        cache = null
        runCatching { databaseProvider?.close() }
        databaseProvider = null
        activeConfig = null
    }

    private fun dirLength(dir: File): Long =
        runCatching {
            dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
        }.getOrDefault(0L)
}
