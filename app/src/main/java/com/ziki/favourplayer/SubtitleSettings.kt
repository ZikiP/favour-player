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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.util.Locale

@Composable
fun SubtitleSettingsDialog(
    initial: SubtitleStyle,
    onSave: (SubtitleStyle) -> Unit,
    onDismiss: () -> Unit,
) {
    var style by remember { mutableStateOf(initial) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .widthIn(max = 520.dp)
                .heightIn(max = 620.dp),
            shape = RoundedCornerShape(18.dp),
            color = PlayerColors.Panel,
            border = BorderStroke(1.dp, PlayerColors.Border),
            shadowElevation = 24.dp,
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    IconButton(onClick = onDismiss, modifier = Modifier.size(36.dp)) {
                        Icon(
                            Icons.Rounded.Close,
                            contentDescription = "关闭",
                            tint = PlayerColors.Faint,
                        )
                    }
                }
                Text(
                    text = "字幕设置",
                    color = PlayerColors.Text,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.SemiBold,
                    ),
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(84.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF14161B)),
                    contentAlignment = Alignment.Center,
                ) {
                    SubtitleText(text = "字幕预览效果 Sample", style = style)
                }

                Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = "时间偏移",
                            color = PlayerColors.Text,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Medium,
                        )
                        Text(
                            text = "正数延后出现",
                            color = PlayerColors.Faint,
                            fontSize = 10.5.sp,
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        StepButton("−0.5s") {
                            style = style.copy(
                                offsetMs = (style.offsetMs - SubtitleStyle.OFFSET_STEP_MS)
                                    .coerceIn(
                                        -SubtitleStyle.OFFSET_LIMIT_MS,
                                        SubtitleStyle.OFFSET_LIMIT_MS,
                                    ),
                            )
                        }
                        Text(
                            text = formatOffset(style.offsetMs),
                            color = PlayerColors.Accent,
                            fontSize = 13.sp,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.widthIn(min = 74.dp),
                        )
                        StepButton("+0.5s") {
                            style = style.copy(
                                offsetMs = (style.offsetMs + SubtitleStyle.OFFSET_STEP_MS)
                                    .coerceIn(
                                        -SubtitleStyle.OFFSET_LIMIT_MS,
                                        SubtitleStyle.OFFSET_LIMIT_MS,
                                    ),
                            )
                        }
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = { style = style.copy(offsetMs = 0L) }) {
                            Text("归零", color = PlayerColors.Muted, fontSize = 11.5.sp)
                        }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = "字号",
                            color = PlayerColors.Text,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Medium,
                        )
                        Text(
                            text = "${style.fontSize.toInt()} sp",
                            color = PlayerColors.Muted,
                            fontSize = 11.sp,
                        )
                    }
                    Slider(
                        value = style.fontSize,
                        onValueChange = { style = style.copy(fontSize = it) },
                        valueRange = SubtitleStyle.FONT_MIN..SubtitleStyle.FONT_MAX,
                        steps = (SubtitleStyle.FONT_MAX - SubtitleStyle.FONT_MIN).toInt() - 1,
                        colors = SliderDefaults.colors(
                            thumbColor = PlayerColors.Accent,
                            activeTrackColor = PlayerColors.Accent,
                            inactiveTrackColor = PlayerColors.PanelSoft,
                        ),
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text(
                        text = "颜色",
                        color = PlayerColors.Text,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        SubtitleStyle.COLORS.forEach { (argb, _) ->
                            val selected = style.textColor == argb
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(Color(argb))
                                    .border(
                                        width = if (selected) 2.dp else 1.dp,
                                        color = if (selected) {
                                            PlayerColors.Accent
                                        } else {
                                            Color.White.copy(alpha = 0.18f)
                                        },
                                        shape = CircleShape,
                                    )
                                    .clickable { style = style.copy(textColor = argb) },
                                contentAlignment = Alignment.Center,
                            ) {
                                if (selected) {
                                    Icon(
                                        Icons.Rounded.Check,
                                        contentDescription = null,
                                        tint = Color.Black.copy(alpha = 0.72f),
                                        modifier = Modifier.size(15.dp),
                                    )
                                }
                            }
                        }
                    }
                }

                StyleChoice(
                    label = "背景",
                    options = listOf(0 to "无", 1 to "半透明", 2 to "深色"),
                    selected = style.background,
                    onSelect = { style = style.copy(background = it) },
                )
                StyleChoice(
                    label = "位置",
                    options = listOf(0 to "贴底", 1 to "标准", 2 to "抬高"),
                    selected = style.position,
                    onSelect = { style = style.copy(position = it) },
                )

                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    FilterChip(
                        selected = style.bold,
                        onClick = { style = style.copy(bold = !style.bold) },
                        label = { Text("加粗", fontSize = 11.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = PlayerColors.Accent,
                            selectedLabelColor = PlayerColors.AccentInk,
                            labelColor = PlayerColors.Muted,
                        ),
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        onClick = { style = SubtitleStyle() },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("恢复默认", color = PlayerColors.Muted, fontSize = 12.sp)
                    }
                    Button(
                        onClick = { onSave(style); onDismiss() },
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 46.dp),
                        shape = RoundedCornerShape(11.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PlayerColors.Accent,
                            contentColor = PlayerColors.AccentInk,
                        ),
                    ) {
                        Text("保存", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@Composable
private fun StepButton(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(9.dp))
            .background(PlayerColors.PanelSoft)
            .border(1.dp, PlayerColors.Border, RoundedCornerShape(9.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(
            text = label,
            color = PlayerColors.Text,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun StyleChoice(
    label: String,
    options: List<Pair<Int, String>>,
    selected: Int,
    onSelect: (Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Text(
            text = label,
            color = PlayerColors.Text,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.Medium,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
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

private fun formatOffset(offsetMs: Long): String =
    String.format(Locale.US, "%+.1f 秒", offsetMs / 1000f)
