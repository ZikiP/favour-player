package com.ziki.favourplayer

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
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
)

class LibraryViewModel(app: Application) : AndroidViewModel(app) {

    private val store = SyncStore(app)

    var state by mutableStateOf(LibraryUiState())
        private set

    init {
        val config = store.loadConfig()
        val cached = store.loadCached()
        state = LibraryUiState(
            snapshot = cached,
            lastReceivedAt = cached?.updatedAt,
            port = config.port,
            token = config.token,
            enabled = config.enabled,
            addresses = deviceAddresses(config.port),
        )

        viewModelScope.launch {
            SyncBus.snapshots.collect { snapshot ->
                state = state.copy(
                    snapshot = snapshot,
                    lastReceivedAt = snapshot.updatedAt,
                    serverError = null,
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

        if (config.enabled) SyncService.start(app)
    }

    fun saveReceiveConfig(portText: String, token: String, enabled: Boolean): String? {
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
            serverError = null,
        )
        if (enabled) SyncService.start(getApplication()) else SyncService.stop(getApplication())
        return null
    }

    fun regenerateToken(): String {
        val token = store.regenerateToken()
        state = state.copy(token = token)
        if (state.enabled) SyncService.start(getApplication())
        return token
    }

    fun refreshAddresses() {
        state = state.copy(addresses = deviceAddresses(state.port))
    }

    fun clearLocalData() {
        store.clearSnapshot()
        state = state.copy(snapshot = null, lastReceivedAt = null)
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
}
