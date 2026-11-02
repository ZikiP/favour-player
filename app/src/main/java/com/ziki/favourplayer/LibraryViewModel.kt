package com.ziki.favourplayer

import android.app.Application
import android.net.ConnectivityManager
import android.net.Network
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.Inet4Address
import java.net.NetworkInterface

data class LibraryUiState(
    val snapshot: LibrarySnapshot? = null,
    val lastReceivedAt: Long? = null,
    val port: Int = SyncStore.DEFAULT_PORT,
    val token: String = "",
    val enabled: Boolean = true,
    val addresses: List<String> = emptyList(),
    val serverRunning: Boolean = false,
    val serverError: String? = null,
    val mode: String = SYNC_MODE_RECEIVE,
    val pullUrl: String = "",
    val pullToken: String = "",
    val pulling: Boolean = false,
    val pullError: String? = null,
)

class LibraryViewModel(app: Application) : AndroidViewModel(app) {

    private val store = SyncStore(app)
    private val connectivityManager: ConnectivityManager? =
        app.getSystemService(ConnectivityManager::class.java)
    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            viewModelScope.launch {
                if (state.mode == SYNC_MODE_PULL) pullNow(auto = true)
            }
        }
    }

    private var pullJob: Job? = null
    private var lastPullAttemptAt = 0L

    var state by mutableStateOf(LibraryUiState())
        private set

    init {
        val config = store.loadConfig()
        val mode = store.loadMode()
        val pull = store.loadPullConfig()
        val cached = store.loadCached()
        state = LibraryUiState(
            snapshot = cached,
            lastReceivedAt = cached?.updatedAt,
            port = config.port,
            token = config.token,
            enabled = config.enabled,
            addresses = deviceAddresses(config.port),
            mode = mode,
            pullUrl = pull.url,
            pullToken = pull.token,
        )

        viewModelScope.launch {
            SyncBus.snapshots.collect { snapshot ->
                state = state.copy(
                    snapshot = snapshot,
                    lastReceivedAt = snapshot.updatedAt,
                    serverError = null,
                    pullError = null,
                )
            }
        }
        viewModelScope.launch {
            SyncBus.server.collect { server ->
                state = state.copy(
                    serverRunning = server.running,
                    serverError = server.error,
                )
            }
        }

        runCatching {
            connectivityManager?.registerDefaultNetworkCallback(networkCallback)
        }

        when {
            mode == SYNC_MODE_PULL -> pullNow(auto = true)
            config.enabled -> SyncService.start(app)
        }
    }

    fun onForeground() {
        if (state.mode == SYNC_MODE_PULL) pullNow(auto = true)
    }

    fun pullNow(auto: Boolean = false) {
        if (state.mode != SYNC_MODE_PULL) return
        if (pullJob?.isActive == true) return

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
                state = state.copy(
                    snapshot = pulled.snapshot,
                    lastReceivedAt = pulled.snapshot.updatedAt,
                    pulling = false,
                    pullError = null,
                )
            }.onFailure { error ->
                state = state.copy(
                    pulling = false,
                    pullError = error.message ?: "拉取失败",
                )
            }
        }
    }

    fun saveSyncSettings(
        mode: String,
        portText: String,
        token: String,
        enabled: Boolean,
        pullUrlText: String,
        pullTokenText: String,
    ): String? {
        val nextMode = if (mode == SYNC_MODE_PULL) SYNC_MODE_PULL else SYNC_MODE_RECEIVE
        var pullUrl = state.pullUrl
        var pullToken = state.pullToken

        if (nextMode == SYNC_MODE_PULL) {
            pullUrl = normalizePullUrl(pullUrlText)
                ?: return "请填写有效的中间服务地址（http/https）"
            pullToken = pullTokenText.trim()
            if (pullToken.isBlank()) return "请填写中间服务令牌"
            store.savePullConfig(PullConfig(pullUrl, pullToken))
        } else {
            val port = portText.trim().toIntOrNull()?.takeIf { it in 1024..65535 }
                ?: return "端口需在 1024-65535"
            val cleanToken = token.trim()
            if (cleanToken.isBlank()) return "令牌不能为空"
            store.saveConfig(ReceiveConfig(port, cleanToken, enabled))
            state = state.copy(
                port = port,
                token = cleanToken,
                enabled = enabled,
                addresses = deviceAddresses(port),
            )
        }

        store.saveMode(nextMode)
        state = state.copy(
            mode = nextMode,
            pullUrl = pullUrl,
            pullToken = pullToken,
            serverError = null,
            pullError = null,
        )

        if (nextMode == SYNC_MODE_PULL) {
            SyncService.stop(getApplication())
            pullNow()
        } else {
            pullJob?.cancel()
            pullJob = null
            state = state.copy(pulling = false)
            if (enabled) {
                SyncService.start(getApplication())
            } else {
                SyncService.stop(getApplication())
            }
        }
        return null
    }

    fun regenerateToken(): String {
        val token = store.regenerateToken()
        state = state.copy(token = token)
        if (state.mode == SYNC_MODE_RECEIVE && state.enabled) {
            SyncService.start(getApplication())
        }
        return token
    }

    fun refreshAddresses() {
        state = state.copy(addresses = deviceAddresses(state.port))
    }

    fun clearLocalData() {
        store.clearSnapshot()
        state = state.copy(snapshot = null, lastReceivedAt = null)
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

    private fun deviceAddresses(port: Int): List<String> = runCatching {
        val result = mutableListOf<String>()
        val interfaces = NetworkInterface.getNetworkInterfaces() ?: return emptyList()
        while (interfaces.hasMoreElements()) {
            val network = interfaces.nextElement()
            if (!network.isUp || network.isLoopback) continue
            val addresses = network.inetAddresses
            while (addresses.hasMoreElements()) {
                val address = addresses.nextElement()
                if (address is Inet4Address && !address.isLoopbackAddress && !address.isLinkLocalAddress) {
                    result += "http://${address.hostAddress}:$port"
                }
            }
        }
        result.distinct()
    }.getOrDefault(emptyList())

    private companion object {
        const val AUTO_PULL_INTERVAL_MS = 10_000L
    }
}
