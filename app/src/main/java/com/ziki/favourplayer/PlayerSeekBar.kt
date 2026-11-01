package com.ziki.favourplayer

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

@Composable
fun PlayerSeekBar(
    positionMs: Long,
    durationMs: Long,
    bufferedMs: Long,
    onScrubStart: () -> Unit,
    onScrub: (Long) -> Unit,
    onScrubEnd: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val fraction = if (durationMs > 0L) {
        (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)
    } else {
        0f
    }
    val buffered = if (durationMs > 0L) {
        (bufferedMs.toFloat() / durationMs).coerceIn(0f, 1f)
    } else {
        0f
    }
    var dragMs by remember { mutableLongStateOf(0L) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(40.dp)
            .pointerInput(durationMs) {
                fun timeAt(x: Float): Long {
                    if (durationMs <= 0L) return 0L
                    val width = size.width.toFloat().coerceAtLeast(1f)
                    return ((x / width).coerceIn(0f, 1f) * durationMs).toLong()
                }

                detectTapGestures { offset ->
                    onScrubEnd(timeAt(offset.x))
                }
            }
            .pointerInput(durationMs) {
                fun timeAt(x: Float): Long {
                    if (durationMs <= 0L) return 0L
                    val width = size.width.toFloat().coerceAtLeast(1f)
                    return ((x / width).coerceIn(0f, 1f) * durationMs).toLong()
                }

                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        if (durationMs > 0L) {
                            dragMs = timeAt(offset.x)
                            onScrubStart()
                            onScrub(dragMs)
                        }
                    },
                    onHorizontalDrag = { change, _ ->
                        if (durationMs > 0L) {
                            dragMs = timeAt(change.position.x)
                            onScrub(dragMs)
                        }
                        change.consume()
                    },
                    onDragEnd = {
                        if (durationMs > 0L) onScrubEnd(dragMs)
                    },
                    onDragCancel = {
                        if (durationMs > 0L) onScrubEnd(dragMs)
                    },
                )
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val centerY = size.height / 2f
            val trackHeight = 4.dp.toPx()
            val thumbRadius = 7.dp.toPx()
            val left = thumbRadius
            val right = (size.width - thumbRadius).coerceAtLeast(left + 1f)
            val usable = right - left

            drawRoundRect(
                color = PlayerColors.BorderStrong,
                topLeft = Offset(left, centerY - trackHeight / 2f),
                size = Size(usable, trackHeight),
                cornerRadius = CornerRadius(trackHeight / 2f),
            )

            if (buffered > 0f) {
                drawRoundRect(
                    color = PlayerColors.Muted.copy(alpha = 0.35f),
                    topLeft = Offset(left, centerY - trackHeight / 2f),
                    size = Size(usable * buffered, trackHeight),
                    cornerRadius = CornerRadius(trackHeight / 2f),
                )
            }

            drawRoundRect(
                color = PlayerColors.Accent,
                topLeft = Offset(left, centerY - trackHeight / 2f),
                size = Size(usable * fraction, trackHeight),
                cornerRadius = CornerRadius(trackHeight / 2f),
            )

            val centerX = left + usable * fraction
            drawCircle(
                color = PlayerColors.Accent.copy(alpha = 0.22f),
                radius = thumbRadius * 2.4f,
                center = Offset(centerX, centerY),
            )
            drawCircle(
                color = Color.White,
                radius = thumbRadius,
                center = Offset(centerX, centerY),
            )
            drawCircle(
                color = PlayerColors.Accent,
                radius = thumbRadius * 0.52f,
                center = Offset(centerX, centerY),
            )
        }
    }
}
