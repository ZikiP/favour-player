package com.ziki.favourplayer

import android.util.Log
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun PlayerControls(
    title: String,
    isPlaying: Boolean,
    positionMs: Long,
    durationMs: Long,
    bufferedMs: Long,
    speed: Float,
    qualities: List<QualityOption>,
    selectedHeight: Int,
    onTogglePlay: () -> Unit,
    onBack: () -> Unit,
    onSeekStart: () -> Unit,
    onSeek: (Long) -> Unit,
    onSeekEnd: (Long) -> Unit,
    onSpeedSelected: (Float) -> Unit,
    onQualitySelected: (QualityOption?) -> Unit,
    onRotate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(Unit) {
        Log.i("FavourPlayer", "controls composed")
    }

    val timeStyle = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace)

    Box(modifier) {
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(PlayerColors.Bg.copy(alpha = 0.94f), Color.Transparent),
                    ),
                )
                .statusBarsPadding()
                .padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "返回",
                    tint = PlayerColors.Text,
                )
            }
            Text(
                text = title.ifBlank { "Favour Player" },
                color = PlayerColors.Text,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 12.dp),
            )
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 720.dp),
                shape = RoundedCornerShape(18.dp),
                color = PlayerColors.Panel,
                border = BorderStroke(1.dp, PlayerColors.Border),
                shadowElevation = 16.dp,
            ) {
                Column(Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                    PlayerSeekBar(
                        positionMs = positionMs,
                        durationMs = durationMs,
                        bufferedMs = bufferedMs,
                        onScrubStart = onSeekStart,
                        onScrub = onSeek,
                        onScrubEnd = onSeekEnd,
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = formatTime(positionMs),
                            color = PlayerColors.Muted,
                            style = timeStyle,
                        )
                        Text(
                            text = formatTime(durationMs),
                            color = PlayerColors.Faint,
                            style = timeStyle,
                        )
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        PlayPauseButton(isPlaying = isPlaying, onClick = onTogglePlay)
                        SpeedChip(current = speed, onSelected = onSpeedSelected)
                        QualityChip(
                            qualities = qualities,
                            selectedHeight = selectedHeight,
                            onSelected = onQualitySelected,
                        )
                        Spacer(Modifier.weight(1f))
                        ControlChip("旋转", onRotate)
                    }
                }
            }
        }
    }
}

@Composable
private fun PlayPauseButton(isPlaying: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(50.dp)
            .clip(CircleShape)
            .background(PlayerColors.Accent)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(18.dp)) {
            val ink = PlayerColors.AccentInk
            if (isPlaying) {
                val bar = size.width * 0.3f
                drawRoundRect(
                    color = ink,
                    topLeft = Offset.Zero,
                    size = Size(bar, size.height),
                    cornerRadius = CornerRadius(2f),
                )
                drawRoundRect(
                    color = ink,
                    topLeft = Offset(size.width - bar, 0f),
                    size = Size(bar, size.height),
                    cornerRadius = CornerRadius(2f),
                )
            } else {
                val triangle = Path().apply {
                    moveTo(size.width * 0.18f, 0f)
                    lineTo(size.width, size.height / 2f)
                    lineTo(size.width * 0.18f, size.height)
                    close()
                }
                drawPath(triangle, ink)
            }
        }
    }
}

@Composable
private fun SpeedChip(current: Float, onSelected: (Float) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        ControlChip(formatSpeed(current)) { expanded = true }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            shape = RoundedCornerShape(14.dp),
            containerColor = PlayerColors.Panel,
            border = BorderStroke(1.dp, PlayerColors.Border),
            shadowElevation = 14.dp,
        ) {
            listOf(0.75f, 1f, 1.25f, 1.5f, 2f).forEach { rate ->
                DropdownMenuItem(
                    text = { Text(formatSpeed(rate), color = PlayerColors.Text) },
                    leadingIcon = {
                        if (rate == current) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = PlayerColors.Accent,
                            )
                        } else {
                            Spacer(Modifier.size(24.dp))
                        }
                    },
                    onClick = {
                        expanded = false
                        onSelected(rate)
                    },
                )
            }
        }
    }
}

@Composable
private fun QualityChip(
    qualities: List<QualityOption>,
    selectedHeight: Int,
    onSelected: (QualityOption?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = qualities.firstOrNull { it.height == selectedHeight }
    val label = selected?.label ?: "自动"

    Box {
        ControlChip(label) {
            expanded = true
            Log.i("FavourPlayer", "quality menu open")
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            shape = RoundedCornerShape(14.dp),
            containerColor = PlayerColors.Panel,
            border = BorderStroke(1.dp, PlayerColors.Border),
            shadowElevation = 14.dp,
        ) {
            DropdownMenuItem(
                text = { Text("自动", color = PlayerColors.Text) },
                leadingIcon = {
                    if (selected == null) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = PlayerColors.Accent,
                        )
                    } else {
                        Spacer(Modifier.size(24.dp))
                    }
                },
                onClick = {
                    expanded = false
                    onSelected(null)
                },
            )
            qualities.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.label, color = PlayerColors.Text) },
                    leadingIcon = {
                        if (selected?.height == option.height) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = PlayerColors.Accent,
                            )
                        } else {
                            Spacer(Modifier.size(24.dp))
                        }
                    },
                    onClick = {
                        expanded = false
                        onSelected(option)
                    },
                )
            }
        }
    }
}

@Composable
private fun ControlChip(text: String, onClick: () -> Unit) {
    Text(
        text = text,
        color = PlayerColors.Text,
        style = MaterialTheme.typography.labelLarge,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(PlayerColors.PanelSoft)
            .border(1.dp, PlayerColors.Border, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 9.dp),
    )
}
