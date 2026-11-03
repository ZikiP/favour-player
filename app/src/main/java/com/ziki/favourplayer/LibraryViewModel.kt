package com.ziki.favourplayer

import android.app.Application
import android.net.ConnectivityManager
import android.net.Network
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class LibraryUiState(
    val snapshot: LibrarySnapshot? = null,
    val lastReceivedAt: Long? = null,
    val pullUrl: String = "",
    val pullToken: String = "",
    val pulling: Boolean = false,
    val pullError: String? = null,
    val pullTesting: Boolean = false,
    val pullTestMessage: String? = null,
    val pullTestOk: Boolean = false,
    val playback: Map<String, PlaybackProgress> = emptyMap(),
    /** 本机最近一次成功拉取的时刻（点同步成功就刷新，与数据是否变化无关） */
    val lastPulledAt: Long? = null,
)

class LibraryViewModel(app: Application) : AndroidViewModel(app) {

    private val store = SyncStore(app)
    private val playbackStore = PlaybackStore(app)
    private val connectivityManager: ConnectivityManager? =
        app.getSystemService(ConnectivityManager::class.java)
    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            viewModelScope.launch { pullNow(auto = true) }
        }
    }

    private var pullJob: Job? = null
    private var lastPullAttemptAt = 0L

    var state by mutableStateOf(LibraryUiState())
        private set

    init {
        val pull = store.loadPullConfig()
        val cached = store.loadCached()
        val clean = CleanMode.enabled
        state = LibraryUiState(
            snapshot = if (clean) null else cached,
            lastReceivedAt = if (clean) null else cached?.updatedAt,
            pullUrl = pull.url,
            pullToken = pull.token,
            playback = if (clean) emptyMap() else playbackStore.all(),
            lastPulledAt = if (clean) null else store.loadLastPulledAt().takeIf { it > 0L },
        )

        viewModelScope.launch {
            SyncBus.snapshots.collect { snapshot ->
                if (CleanMode.enabled) return@collect
                state = state.copy(
                    snapshot = snapshot,
                    lastReceivedAt = snapshot.updatedAt,
                    pullError = null,
                )
            }
        }

        // 干净模式开关变化：打开就清空内存里的收藏和进度，关闭再恢复
        viewModelScope.launch {
            snapshotFlow { CleanMode.enabled }.collect { clean ->
                if (clean) {
                    state = state.copy(
                        snapshot = null,
                        lastReceivedAt = null,
                        lastPulledAt = null,
                        playback = emptyMap(),
                    )
                } else {
                    val restored = store.loadCached()
                    state = state.copy(
                        snapshot = restored,
                        lastReceivedAt = restored?.updatedAt,
                        playback = playbackStore.all(),
                        lastPulledAt = store.loadLastPulledAt().takeIf { it > 0L },
                    )
                    pullNow(auto = true)
                }
            }
        }

        runCatching {
            connectivityManager?.registerDefaultNetworkCallback(networkCallback)
        }

        pullNow(auto = true)
    }

    fun onForeground() {
        refreshPlayback()
        pullNow(auto = true)
    }

    fun refreshPlayback() {
        state = state.copy(playback = playbackStore.all())
    }

    fun pullNow(auto: Boolean = false) {
        if (pullJob?.isActive == true) return
        // 干净模式不碰中间服务：连拉取都不发生
        if (CleanMode.enabled) return

        val now = System.currentTimeMillis()
        if (auto && state.pullError == null && now - lastPullAttemptAt < AUTO_PULL_INTERVAL_MS) {
            return
        }

        val config = PullConfig(state.pullUrl, state.pullToken)
        if (config.url.isBlank() || config.token.isBlank()) {
            state = state.copy(pullError = "未配置中间服务")
            return
        }

        lastPullAttemptAt = now
        pullJob = viewModelScope.launch {
            state = state.copy(pulling = true, pullError = null)
            val result = runCatching {
                withContext(Dispatchers.IO) { PullClient.pull(getApplication(), config) }
            }
            result.exceptionOrNull()?.let { if (it is CancellationException) throw it }
            result.onSuccess { pulled ->
                // 这次拉取成功的时刻，和快照本身的 updatedAt 是两回事：
                // 数据没变时点同步，这里照样会刷新，界面上就能看到刚同步过
                val pulledAt = System.currentTimeMillis()
                store.saveLastPulledAt(pulledAt)
                state = state.copy(
                    snapshot = pulled.snapshot,
                    lastReceivedAt = pulled.snapshot.updatedAt,
                    pulling = false,
                    pullError = null,
                    lastPulledAt = pulledAt,
                )
            }.onFailure { error ->
                state = state.copy(
                    pulling = false,
                    pullError = error.message ?: "拉取失败",
                )
            }
        }
    }

    fun testPullConnection(urlText: String, tokenText: String) {
        val url = normalizePullUrl(urlText)
        if (url == null) {
            state = state.copy(pullTestOk = false, pullTestMessage = "请填写有效的中间服务地址")
            return
        }
        val token = tokenText.trim()
        if (token.isBlank()) {
            state = state.copy(pullTestOk = false, pullTestMessage = "请填写中间服务令牌")
            return
        }
        if (state.pullTesting) return

        val config = PullConfig(url, token)
        viewModelScope.launch {
            state = state.copy(pullTesting = true, pullTestMessage = null)
            val result = runCatching {
                withContext(Dispatchers.IO) { PullClient.test(config) }
            }
            result.exceptionOrNull()?.let { if (it is CancellationException) throw it }
            result.onSuccess {
                state = state.copy(
                    pullTesting = false,
                    pullTestOk = true,
                    pullTestMessage = "连接正常 · ${it.count} 条",
                )
            }.onFailure {
                state = state.copy(
                    pullTesting = false,
                    pullTestOk = false,
                    pullTestMessage = it.message ?: "测试失败",
                )
            }
        }
    }

    fun saveSyncSettings(pullUrlText: String, pullTokenText: String): String? {
        val pullUrl = normalizePullUrl(pullUrlText)
            ?: return "请填写有效的中间服务地址（http/https）"
        val pullToken = pullTokenText.trim()
        if (pullToken.isBlank()) return "请填写中间服务令牌"

        store.savePullConfig(PullConfig(pullUrl, pullToken))
        state = state.copy(
            pullUrl = pullUrl,
            pullToken = pullToken,
            pullError = null,
            pullTesting = false,
            pullTestMessage = null,
            pullTestOk = false,
        )
        pullNow()
        return null
    }

    override fun onCleared() {
        runCatching { connectivityManager?.unregisterNetworkCallback(networkCallback) }
        super.onCleared()
    }

    private fun normalizePullUrl(raw: String): String? {
        val value = raw.trim().trimEnd('/')
        if (value.isEmpty()) return null
        val uri = runCatching { Uri.parse(value) }.getOrNull() ?: return null
        val scheme = uri.scheme?.lowercase() ?: return null
        if (scheme != "http" && scheme != "https") return null
        if (uri.host.isNullOrBlank()) return null
        return value
    }

    private companion object {
        const val AUTO_PULL_INTERVAL_MS = 10_000L
    }
}
