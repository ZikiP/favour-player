package com.ziki.favourplayer

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.automirrored.rounded.RotateRight
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun PlayerControls(
    title: String,
    subtitle: String,
    isPlaying: Boolean,
    positionMs: Long,
    durationMs: Long,
    bufferedMs: Long,
    speed: Float,
    qualities: List<QualityOption>,
    selectedHeight: Int,
    subtitleActive: Boolean,
    onPickSubtitle: () -> Unit,
    onClearSubtitle: () -> Unit,
    onOpenSubtitleSettings: () -> Unit,
    controlBottom: Int,
    onTogglePlay: () -> Unit,
    onBack: () -> Unit,
    onOpenOriginal: () -> Unit,
    onLock: () -> Unit,
    onSeekStart: () -> Unit,
    onSeek: (Long) -> Unit,
    onSeekEnd: (Long) -> Unit,
    onSpeedSelected: (Float) -> Unit,
    onQualitySelected: (QualityOption?) -> Unit,
    onRotate: () -> Unit,
    onSkipBack: () -> Unit,
    onSkipForward: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier) {
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Black.copy(alpha = 0.76f), Color.Transparent),
                    ),
                )
                .statusBarsPadding()
                .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PlayerIconButton(Icons.AutoMirrored.Rounded.ArrowBack, "返回", onBack)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 4.dp, end = 8.dp),
            ) {
                Text(
                    text = title.ifBlank { "Favour Player" },
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitle.isNotBlank()) {
                    Text(
                        text = subtitle,
                        color = Color.White.copy(alpha = 0.54f),
                        fontSize = 9.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 1.dp),
                    )
                }
            }
            PlayerIconButton(Icons.AutoMirrored.Rounded.OpenInNew, "打开原页", onOpenOriginal)
            Spacer(Modifier.width(6.dp))
            SubtitleButton(
                active = subtitleActive,
                onPick = onPickSubtitle,
                onClear = onClearSubtitle,
                onOpenSettings = onOpenSubtitleSettings,
            )
            Spacer(Modifier.width(6.dp))
            PlayerIconButton(Icons.AutoMirrored.Rounded.RotateRight, "旋转", onRotate)
            Spacer(Modifier.width(6.dp))
            PlayerIconButton(Icons.Rounded.Lock, "锁定", onLock)
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.88f)),
                    ),
                )
                .navigationBarsPadding()
                .padding(
                    start = 20.dp,
                    end = 20.dp,
                    top = 28.dp,
                    bottom = (10 + controlBottom).dp,
                ),
        ) {
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
                    color = Color.White.copy(alpha = 0.68f),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 9.5.sp,
                )
                Text(
                    text = formatTime(durationMs),
                    color = Color.White.copy(alpha = 0.48f),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 9.5.sp,
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                PlayerIconButton(Icons.Rounded.Replay10, "后退 10 秒", onSkipBack)
                PlayPauseButton(isPlaying = isPlaying, onClick = onTogglePlay)
                PlayerIconButton(Icons.Rounded.Forward10, "前进 10 秒", onSkipForward)
                Spacer(Modifier.weight(1f))
                SpeedChip(current = speed, onSelected = onSpeedSelected)
                QualityChip(
                    qualities = qualities,
                    selectedHeight = selectedHeight,
                    onSelected = onQualitySelected,
                )
            }
        }
    }
}

@Composable
private fun PlayerIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    tint: Color = Color.White,
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.09f))
            .border(1.dp, Color.White.copy(alpha = 0.12f), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = tint,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun SubtitleButton(
    active: Boolean,
    onPick: () -> Unit,
    onClear: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        PlayerIconButton(
            icon = Icons.Rounded.Subtitles,
            label = if (active) "字幕已挂载" else "挂载字幕",
            tint = if (active) PlayerColors.Accent else Color.White,
            onClick = { expanded = true },
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            shape = RoundedCornerShape(14.dp),
            containerColor = PlayerColors.Panel,
            border = BorderStroke(1.dp, PlayerColors.Border),
        ) {
            DropdownMenuItem(
                text = { Text("选择字幕文件…", color = PlayerColors.Text) },
                onClick = {
                    expanded = false
                    onPick()
                },
            )
            DropdownMenuItem(
                text = { Text("字幕设置…", color = PlayerColors.Text) },
                onClick = {
                    expanded = false
                    onOpenSettings()
                },
            )
            if (active) {
                DropdownMenuItem(
                    text = { Text("关闭字幕", color = PlayerColors.Text) },
                    onClick = {
                        expanded = false
                        onClear()
                    },
                )
            }
        }
    }
}

@Composable
private fun PlayPauseButton(isPlaying: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(49.dp)
            .clip(CircleShape)
            .background(PlayerColors.Accent)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
            contentDescription = if (isPlaying) "暂停" else "播放",
            tint = PlayerColors.AccentInk,
            modifier = Modifier.size(24.dp),
        )
    }
}

@Composable
private fun SpeedChip(current: Float, onSelected: (Float) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        PlayerPill(formatSpeed(current)) { expanded = true }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            shape = RoundedCornerShape(14.dp),
            containerColor = PlayerColors.Panel,
            border = BorderStroke(1.dp, PlayerColors.Border),
        ) {
            PlayerPrefs.SPEEDS.forEach { rate ->
                DropdownMenuItem(
                    text = { Text(formatSpeed(rate), color = PlayerColors.Text) },
                    leadingIcon = {
                        if (rate == current) {
                            Icon(
                                Icons.Rounded.Check,
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
        PlayerPill(label) { expanded = true }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            shape = RoundedCornerShape(14.dp),
            containerColor = PlayerColors.Panel,
            border = BorderStroke(1.dp, PlayerColors.Border),
        ) {
            DropdownMenuItem(
                text = { Text("自动", color = PlayerColors.Text) },
                leadingIcon = {
                    if (selected == null) {
                        Icon(
                            Icons.Rounded.Check,
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
                                Icons.Rounded.Check,
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
private fun PlayerPill(text: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .height(36.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Color.White.copy(alpha = 0.09f))
            .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = Color.White,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
