package com.ziki.favourplayer

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlin.math.abs

fun Modifier.playerGestures(
    blockBottomPx: Float = 0f,
    onTap: () -> Unit,
    onDoubleTap: (isLeft: Boolean) -> Unit,
    onLongPressChange: (pressed: Boolean) -> Unit,
    onSeekBegin: () -> Unit,
    onSeekFraction: (Float) -> Unit,
    onSeekEnd: () -> Unit,
    onVerticalDelta: (isLeft: Boolean, delta: Float) -> Unit,
): Modifier = this
    .pointerInput(blockBottomPx) {
        fun blocked(y: Float): Boolean =
            blockBottomPx > 0f && y > size.height - blockBottomPx

        detectTapGestures(
            onTap = { offset ->
                if (!blocked(offset.y)) onTap()
            },
            onDoubleTap = { offset ->
                if (!blocked(offset.y)) onDoubleTap(offset.x < size.width / 2f)
            },
            onLongPress = { offset ->
                if (!blocked(offset.y)) onLongPressChange(true)
            },
            onPress = { offset ->
                tryAwaitRelease()
                if (!blocked(offset.y)) onLongPressChange(false)
            },
        )
    }
    .pointerInput(blockBottomPx) {
        var startX = 0f
        var decided = false
        var horizontal = false
        var ignored = false
        var totalX = 0f
        var totalY = 0f
        val threshold = 12.dp.toPx()

        fun blocked(y: Float): Boolean =
            blockBottomPx > 0f && y > size.height - blockBottomPx

        fun finish() {
            if (!ignored && decided && horizontal) onSeekEnd()
            decided = false
            horizontal = false
            ignored = false
            totalX = 0f
            totalY = 0f
        }

        detectDragGestures(
            onDragStart = { offset ->
                ignored = blocked(offset.y)
                startX = offset.x
                decided = false
                horizontal = false
                totalX = 0f
                totalY = 0f
            },
            onDrag = { change, dragAmount ->
                if (ignored) return@detectDragGestures
                totalX += dragAmount.x
                totalY += dragAmount.y
                if (!decided && abs(totalX) + abs(totalY) > threshold) {
                    decided = true
                    horizontal = abs(totalX) > abs(totalY)
                    if (horizontal) {
                        onSeekBegin()
                        onSeekFraction((totalX / size.width).coerceIn(-1f, 1f))
                    } else {
                        onVerticalDelta(startX < size.width / 2f, -totalY / size.height)
                        totalY = 0f
                    }
                } else if (decided) {
                    if (horizontal) {
                        onSeekFraction((totalX / size.width).coerceIn(-1f, 1f))
                    } else {
                        val delta = -dragAmount.y / size.height
                        if (delta != 0f) onVerticalDelta(startX < size.width / 2f, delta)
                    }
                }
                change.consume()
            },
            onDragEnd = { finish() },
            onDragCancel = { finish() },
        )
    }
