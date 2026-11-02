package com.ziki.favourplayer

import android.content.Context
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import coil.Coil
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.annotation.ExperimentalCoilApi
import coil.disk.DiskCache
import coil.request.CachePolicy
import coil.request.ImageRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import okhttp3.OkHttpClient

@OptIn(ExperimentalCoilApi::class)
object CoverCache {

    private val lock = Any()
    private var loader: ImageLoader? = null
    private var activeKey: String? = null

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var prefetchJob: Job? = null

    private val client by lazy {
        OkHttpClient.Builder()
            .addInterceptor { chain ->
                chain.proceed(
                    chain.request().newBuilder()
                        .header("User-Agent", Resolver.DESKTOP_UA)
                        .build(),
                )
            }
            .build()
    }

    var revision by mutableIntStateOf(0)
        private set

    fun imageLoader(context: Context): ImageLoader {
        val app = context.applicationContext
        val config = CacheStore(app).load()
        val key = config.coverKey
        synchronized(lock) {
            loader?.takeIf { activeKey == key }?.let { return it }
            runCatching { loader?.shutdown() }
            val created = build(app, config)
            loader = created
            activeKey = key
            return created
        }
    }

    fun onConfigChanged(context: Context, previousLocation: String) {
        val app = context.applicationContext
        val config = CacheStore(app).load()
        synchronized(lock) {
            runCatching { loader?.shutdown() }
            loader = null
            activeKey = null
        }
        if (previousLocation != config.location) {
            runCatching { CacheStore.coverDir(app, previousLocation).deleteRecursively() }
        }
        Coil.setImageLoader(ImageLoaderFactory { imageLoader(app) })
        revision += 1
        if (config.coverEnabled) {
            prefetchCached(app)
        } else {
            prefetchJob?.cancel()
        }
    }

    fun usage(context: Context): CacheUsage {
        val config = CacheStore(context).load()
        val dir = CacheStore.coverDir(context, config.location)
        val files = runCatching { dir.walkTopDown().filter { it.isFile }.toList() }
            .getOrDefault(emptyList())
        val entries = files.mapNotNull { file ->
            file.name.takeIf { ENTRY_NAME.containsMatchIn(it) }?.substringBeforeLast('.')
        }.distinct().size
        return CacheUsage(
            enabled = config.coverEnabled,
            usedBytes = files.sumOf { it.length() },
            maxBytes = config.coverMaxBytes,
            entries = entries,
            path = dir.absolutePath,
        )
    }

    fun clear(context: Context) {
        val app = context.applicationContext
        val config = CacheStore(app).load()
        prefetchJob?.cancel()
        synchronized(lock) {
            runCatching { loader?.shutdown() }
            loader = null
            activeKey = null
        }
        runCatching { CacheStore.coverDir(app, config.location).deleteRecursively() }
        Coil.setImageLoader(ImageLoaderFactory { imageLoader(app) })
        revision += 1
    }

    fun prefetch(context: Context, urls: Collection<String>) {
        launchPrefetch(context.applicationContext) { urls }
    }

    fun prefetchCached(context: Context) {
        val app = context.applicationContext
        launchPrefetch(app) {
            SyncStore(app).loadCached()?.items.orEmpty()
                .map { it.cover }
                .filter { it.isNotBlank() }
        }
    }

    private fun launchPrefetch(context: Context, urls: suspend () -> Collection<String>) {
        if (!CacheStore(context).load().coverEnabled) return
        prefetchJob?.cancel()
        prefetchJob = scope.launch {
            val targets = urls()
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .distinct()
            if (targets.isEmpty()) return@launch

            val imageLoader = imageLoader(context)
            val diskCache = imageLoader.diskCache ?: return@launch
            val semaphore = Semaphore(4)
            coroutineScope {
                targets.map { url ->
                    async {
                        semaphore.withPermit {
                            val existing = runCatching { diskCache.openSnapshot(url) }.getOrNull()
                            if (existing != null) {
                                existing.close()
                                return@withPermit
                            }
                            runCatching {
                                imageLoader.execute(
                                    ImageRequest.Builder(context)
                                        .data(url)
                                        .diskCacheKey(url)
                                        .size(64, 64)
                                        .memoryCachePolicy(CachePolicy.DISABLED)
                                        .diskCachePolicy(CachePolicy.ENABLED)
                                        .build(),
                                )
                            }
                        }
                    }
                }.awaitAll()
            }
            Log.i(TAG, "cover prefetch finished: ${targets.size}")
        }
    }

    private fun build(context: Context, config: CacheConfig): ImageLoader {
        val builder = ImageLoader.Builder(context)
            .okHttpClient(client)
            .crossfade(true)
            .respectCacheHeaders(false)
            .diskCachePolicy(
                if (config.coverEnabled) CachePolicy.ENABLED else CachePolicy.DISABLED,
            )
        val diskCache = if (config.coverEnabled) {
            val dir = CacheStore.coverDir(context, config.location)
            runCatching {
                dir.mkdirs()
                DiskCache.Builder()
                    .directory(dir)
                    .maxSizeBytes(config.coverMaxBytes)
                    .build()
            }.onFailure { Log.w(TAG, "cover cache init failed", it) }.getOrNull()
        } else {
            null
        }
        builder.diskCache(diskCache)
        return builder.build()
    }

    private val ENTRY_NAME = Regex("""\.\d+$""")
    private const val TAG = "FavourPlayer"
}
