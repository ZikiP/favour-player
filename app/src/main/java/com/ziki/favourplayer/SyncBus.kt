package com.ziki.favourplayer

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

object SyncBus {
    private val _snapshots = MutableSharedFlow<LibrarySnapshot>(
        replay = 1,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val snapshots = _snapshots.asSharedFlow()

    fun publishSnapshot(snapshot: LibrarySnapshot) {
        _snapshots.tryEmit(snapshot)
    }
}
