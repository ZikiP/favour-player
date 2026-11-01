package com.ziki.favourplayer

import android.app.Application
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class LibraryUiState(
    val config: SyncConfig? = null,
    val snapshot: LibrarySnapshot? = null,
    val loading: Boolean = false,
    val error: String? = null,
    val offline: Boolean = false,
    val lastSyncedAt: Long? = null,
    val needsSetup: Boolean = true,
)

class LibraryViewModel(app: Application) : AndroidViewModel(app) {

    private val store = SyncStore(app)

    var state by mutableStateOf(LibraryUiState())
        private set

    init {
        val config = store.loadConfig()
        state = LibraryUiState(config = config, needsSetup = config == null)
        viewModelScope.launch {
            val cached = withContext(Dispatchers.IO) { store.loadCached() }
            if (cached != null) {
                state = state.copy(
                    snapshot = cached,
                    lastSyncedAt = cached.updatedAt,
                    offline = true,
                )
            }
            if (config != null) refresh()
        }
    }

    fun refresh() {
        val config = state.config ?: return
        if (state.loading) return
        state = state.copy(loading = true, error = null)
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { store.refresh(config) }
            }
            val snapshot = result.getOrNull()
            if (snapshot != null) {
                state = state.copy(
                    snapshot = snapshot,
                    loading = false,
                    offline = false,
                    error = null,
                    lastSyncedAt = snapshot.updatedAt,
                    needsSetup = false,
                )
            } else {
                val error = result.exceptionOrNull()
                Log.e("FavourPlayer", "sync failed", error)
                state = state.copy(
                    loading = false,
                    offline = state.snapshot != null,
                    error = error?.message ?: "同步失败",
                    needsSetup = false,
                )
            }
        }
    }

    fun saveConfig(serverUrl: String, token: String) {
        val config = SyncConfig(serverUrl.trim(), token.trim())
        if (config.serverUrl.isBlank() || config.token.isBlank()) return
        store.saveConfig(config)
        state = state.copy(
            config = config,
            needsSetup = false,
            error = null,
        )
        refresh()
    }

    fun clearConfig() {
        store.clearConfig()
        store.clearCached()
        state = LibraryUiState(needsSetup = true)
    }
}
