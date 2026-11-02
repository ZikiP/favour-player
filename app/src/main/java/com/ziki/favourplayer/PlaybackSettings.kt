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
    }
}

@Composable
private fun <T> ChoiceRow(
    label: String,
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Text(
            text = label,
            color = PlayerColors.Text,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.Medium,
        )
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
