package com.ziki.favourplayer

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

data class SyncServerState(
    val running: Boolean = false,
    val port: Int = 0,
    val error: String? = null,
)

object SyncBus {
    private val _snapshots = MutableSharedFlow<LibrarySnapshot>(
        replay = 1,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val snapshots = _snapshots.asSharedFlow()

    private val _server = MutableStateFlow(SyncServerState())
    val server = _server.asStateFlow()

    fun publishSnapshot(snapshot: LibrarySnapshot) {
        _snapshots.tryEmit(snapshot)
    }

    fun publishServer(state: SyncServerState) {
        _server.value = state
    }
}
