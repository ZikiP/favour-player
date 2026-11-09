package com.ziki.favourplayer

import android.content.Context
import android.util.Log
import androidx.media3.common.C
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.hls.HlsDataSourceFactory
import java.io.File

object MediaCache {

    private val lock = Any()
    private var cache: SimpleCache? = null
    private var databaseProvider: StandaloneDatabaseProvider? = null
    private var activeKey: String? = null

    fun dataSourceFactory(context: Context, upstream: DataSource.Factory): DataSource.Factory {
        val config = CacheStore(context).load()
        if (!config.videoEnabled) return upstream
        val simpleCache = synchronized(lock) { ensure(context, config) } ?: return upstream
        return CacheDataSource.Factory()
            .setCache(simpleCache)
            .setUpstreamDataSourceFactory(upstream)
            // 不带签名参数做键：同一分片每次拿到的 URL 签名都不同，
            // 用整条 URL 做键等于永远命中不了缓存
            .setCacheKeyFactory { dataSpec -> cacheKeyOf(dataSpec.uri.toString()) }
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
    }

    /**
     * 缓存键：去掉查询串和片段。
     *
     * 分片地址通常形如 /hls/seg-3.ts?sign=…&expires=…，签名每次播放都会换；
     * 按路径做键，同一段视频第二遍播放/拖动才能命中缓存。
     * 代价是查询串决定内容的站点（极少见）会共用一份缓存。
     */
    internal fun cacheKeyOf(rawUrl: String): String {
        val cut = rawUrl.indexOfFirst { it == '?' || it == '#' }
        return if (cut >= 0) rawUrl.substring(0, cut) else rawUrl
    }

    /**
     * 给 HLS 用的数据源工厂：播放列表走原始上游，分片才进缓存。
     *
     * 播放列表（manifest）是会变的：里面写着分片的实际地址，很多站点
     * 每次访问都会换一批带时效签名的分片 URL。把播放列表也缓存下来的话，
     * 缓存里那份一旦过期，播放器就会拿着失效地址去拉分片，
     * 表现就是「转圈很久，最后报网络失败」——而且清缓存之前一直好不了。
     *
     * 分片是不可变的，缓存起来才有意义。
     */
    fun hlsDataSourceFactory(context: Context, upstream: DataSource.Factory): HlsDataSourceFactory =
        object : HlsDataSourceFactory {
            override fun createDataSource(dataType: Int): DataSource {
                // DATA_TYPE_MANIFEST = 播放列表；其余（分片、初始化段）才缓存
                return if (dataType == C.DATA_TYPE_MANIFEST) {
                    upstream.createDataSource()
                } else {
                    dataSourceFactory(context, upstream).createDataSource()
                }
            }
        }

    fun usage(context: Context): CacheUsage {
        val config = CacheStore(context).load()
        val dir = CacheStore.videoDir(context, config.location)
        val simpleCache = if (config.videoEnabled) {
            synchronized(lock) { ensure(context, config) }
        } else {
            null
        }
        return CacheUsage(
            enabled = config.videoEnabled,
            usedBytes = simpleCache?.cacheSpace ?: dirLength(dir),
            maxBytes = config.videoMaxBytes,
            entries = simpleCache?.keys?.size ?: 0,
            path = dir.absolutePath,
        )
    }

    fun onConfigChanged(context: Context, previousLocation: String) {
        synchronized(lock) { releaseLocked() }
        val config = CacheStore(context).load()
        if (previousLocation != config.location) {
            runCatching { CacheStore.videoDir(context, previousLocation).deleteRecursively() }
        }
    }

    fun clear(context: Context) {
        synchronized(lock) {
            releaseLocked()
            CacheStore.videoDir(context, CACHE_LOCATION_INTERNAL).deleteRecursively()
            CacheStore.videoDir(context, CACHE_LOCATION_EXTERNAL).deleteRecursively()
        }
    }

    fun release() {
        synchronized(lock) { releaseLocked() }
    }

    private fun ensure(context: Context, config: CacheConfig): SimpleCache? {
        if (cache != null && activeKey == config.videoKey) return cache

        releaseLocked()
        val dir = CacheStore.videoDir(context, config.location)
        dir.mkdirs()
        val provider = StandaloneDatabaseProvider(context)
        return try {
            val created = SimpleCache(
                dir,
                LeastRecentlyUsedCacheEvictor(config.videoMaxBytes),
                provider,
            )
            cache = created
            databaseProvider = provider
            activeKey = config.videoKey
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
        activeKey = null
    }

    private fun dirLength(dir: File): Long =
        runCatching {
            dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
        }.getOrDefault(0L)
}
