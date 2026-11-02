package com.ziki.favourplayer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun PlaybackSettingsPage(
    initial: PlayerSettings,
    onSave: (PlayerSettings) -> Unit,
    onBack: () -> Unit,
) {
    var settings by remember { mutableStateOf(initial) }

    SubPage(
        title = "播放设置",
        subtitle = "下次进入播放器时生效",
        onBack = onBack,
        actions = {
            TextButton(onClick = { onSave(settings); onBack() }) {
                Text(
                    text = "保存",
                    color = PlayerColors.Accent,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        },
    ) {
        ChoiceRow(
            label = "默认倍速",
            options = PlayerPrefs.SPEEDS.map { it to formatSpeed(it) },
            selected = settings.defaultSpeed,
            onSelect = { settings = settings.copy(defaultSpeed = it) },
        )
        ChoiceRow(
            label = "控制栏自动隐藏",
            options = PlayerPrefs.AUTO_HIDE.map { it.toLong() to "${it / 1000} 秒" },
            selected = settings.autoHideMs.toLong(),
            onSelect = { settings = settings.copy(autoHideMs = it.toInt()) },
        )
        ChoiceRow(
            label = "双击跳转",
            options = PlayerPrefs.DOUBLE_TAP.map { it to "${it / 1000} 秒" },
            selected = settings.doubleTapMs,
            onSelect = { settings = settings.copy(doubleTapMs = it) },
        )
        ChoiceRow(
            label = "帧率匹配",
            hint = "按片源帧率切换屏幕刷新率，消除 24p 在 60Hz 上的抖动",
            options = listOf(false to "关闭", true to "开启"),
            selected = settings.frameRateMatch,
            onSelect = { settings = settings.copy(frameRateMatch = it) },
        )
        ChoiceRow(
            label = "画质锐化",
            hint = "只增强亮度细节，不放大画面；片源码率低时建议关闭",
            options = SharpenLevel.values().map { it to it.label },
            selected = settings.sharpen,
            onSelect = { settings = settings.copy(sharpen = it) },
        )
    }
}

@Composable
private fun <T> ChoiceRow(
    label: String,
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    hint: String? = null,
) {
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = label,
                color = PlayerColors.Text,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Medium,
            )
            if (hint != null) {
                Text(
                    text = hint,
                    color = PlayerColors.Faint,
                    fontSize = 10.sp,
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            options.forEach { (value, text) ->
                FilterChip(
                    selected = selected == value,
                    onClick = { onSelect(value) },
                    label = { Text(text, fontSize = 11.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = PlayerColors.Accent,
                        selectedLabelColor = PlayerColors.AccentInk,
                        labelColor = PlayerColors.Muted,
                    ),
                )
            }
        }
    }
}
