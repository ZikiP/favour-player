package com.ziki.favourplayer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

@Composable
fun ControlPositionPage(
    initial: PlayerSettings,
    onSave: (PlayerSettings) -> Unit,
    onBack: () -> Unit,
) {
    var settings by remember { mutableStateOf(initial) }
    var previewLandscape by remember { mutableStateOf(false) }

    val configuration = LocalConfiguration.current
    val longSide = maxOf(configuration.screenWidthDp, configuration.screenHeightDp)
    val shortSide = minOf(configuration.screenWidthDp, configuration.screenHeightDp)

    SubPage(
        title = "控件位置",
        subtitle = "竖屏与横屏分别设置，保存后下次进入播放器生效",
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
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(232.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(PlayerColors.Panel.copy(alpha = 0.45f)),
            contentAlignment = Alignment.Center,
        ) {
            MockScreen(
                landscape = previewLandscape,
                offsetDp = if (previewLandscape) {
                    settings.controlBottomLandscape
                } else {
                    settings.controlBottomPortrait
                },
                realHeightDp = if (previewLandscape) shortSide else longSide,
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = !previewLandscape,
                onClick = { previewLandscape = false },
                label = { Text("竖屏预览", fontSize = 11.sp) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = PlayerColors.Accent,
                    selectedLabelColor = PlayerColors.AccentInk,
                    labelColor = PlayerColors.Muted,
                ),
            )
            FilterChip(
                selected = previewLandscape,
                onClick = { previewLandscape = true },
                label = { Text("横屏预览", fontSize = 11.sp) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = PlayerColors.Accent,
                    selectedLabelColor = PlayerColors.AccentInk,
                    labelColor = PlayerColors.Muted,
                ),
            )
        }

        OffsetSlider(
            label = "竖屏 · 控件距底部",
            value = settings.controlBottomPortrait,
            onChange = {
                settings = settings.copy(controlBottomPortrait = it)
                previewLandscape = false
            },
        )
        OffsetSlider(
            label = "横屏 · 控件距底部",
            value = settings.controlBottomLandscape,
            onChange = {
                settings = settings.copy(controlBottomLandscape = it)
                previewLandscape = true
            },
        )

        Text(
            text = "数值越大控件越靠上。横屏可用高度小，一般留 0 到 24 即可；竖屏可以多留一些，方便单手够到。",
            color = PlayerColors.Faint,
            fontSize = 11.sp,
            lineHeight = 17.sp,
        )
    }
}

@Composable
private fun OffsetSlider(
    label: String,
    value: Int,
    onChange: (Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = label,
                color = PlayerColors.Text,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = if (value <= 0) "贴底" else "$value dp",
                color = PlayerColors.Muted,
                fontSize = 11.sp,
            )
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onChange(it.roundToInt()) },
            valueRange = 0f..PlayerPrefs.CONTROL_BOTTOM_MAX.toFloat(),
            colors = SliderDefaults.colors(
                thumbColor = PlayerColors.Accent,
                activeTrackColor = PlayerColors.Accent,
                inactiveTrackColor = PlayerColors.PanelSoft,
            ),
        )
    }
}

/** 按真实屏幕比例缩放的模拟画面，控件条位置随设置实时变化。 */
@Composable
private fun MockScreen(
    landscape: Boolean,
    offsetDp: Int,
    realHeightDp: Int,
) {
    val sizeModifier = if (landscape) {
        Modifier
            .fillMaxWidth(0.94f)
            .aspectRatio(16f / 9f)
    } else {
        Modifier
            .fillMaxHeight(0.94f)
            .aspectRatio(9f / 16f)
    }

    BoxWithConstraints(
        modifier = sizeModifier
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF0B0D11)),
    ) {
        val scale = maxHeight.value / realHeightDp.coerceAtLeast(1).toFloat()

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xFF23262E), Color(0xFF101217)),
                    ),
                ),
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.86f)),
                    ),
                )
                .padding(bottom = (offsetDp * scale).dp),
        ) {
            Box(
                modifier = Modifier
                    .padding(horizontal = 12.dp, vertical = 7.dp)
                    .fillMaxWidth()
                    .height(2.dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(Color.White.copy(alpha = 0.26f)),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 14.dp, end = 14.dp, bottom = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                repeat(3) { Dot() }
                Spacer(Modifier.weight(1f))
                repeat(2) { Dot() }
            }
        }
    }
}

@Composable
private fun Dot() {
    Box(
        modifier = Modifier
            .size(9.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.2f)),
    )
}
