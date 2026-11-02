package com.ziki.favourplayer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun VersionPage(onBack: () -> Unit) {
    val context = LocalContext.current
    var localClean by remember { mutableStateOf(CleanMode.localEnabled) }

    SubPage(
        title = "版本",
        subtitle = "Favour Player",
        onBack = onBack,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(PlayerColors.Panel, RoundedCornerShape(14.dp))
                .border(1.dp, PlayerColors.Border, RoundedCornerShape(14.dp))
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = BuildConfig.VERSION_NAME,
                color = PlayerColors.Text,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = FontFamily.Monospace,
            )
            Text(
                text = "仅用于个人调试，请遵守数据来源的版权与使用条款。",
                color = PlayerColors.Faint,
                fontSize = 11.sp,
                lineHeight = 17.sp,
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(PlayerColors.Panel, RoundedCornerShape(14.dp))
                .border(1.dp, PlayerColors.Border, RoundedCornerShape(14.dp))
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "干净模式",
                        color = PlayerColors.Text,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        text = "打开后不显示任何收藏，也不记录使用数据",
                        color = PlayerColors.Muted,
                        fontSize = 11.5.sp,
                    )
                }
                Switch(
                    checked = localClean,
                    onCheckedChange = { value ->
                        localClean = value
                        CleanMode.set(context, value)
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = PlayerColors.AccentInk,
                        checkedTrackColor = PlayerColors.Accent,
                        uncheckedThumbColor = PlayerColors.Muted,
                        uncheckedTrackColor = PlayerColors.PanelSoft,
                        uncheckedBorderColor = PlayerColors.Border,
                    ),
                )
            }
            Text(
                text = "开启后：不拉取收藏、列表与统计全部为空、不记录播放进度、不上传偏好设置。" +
                    "本地已有数据不会被删除，关掉开关即恢复。",
                color = PlayerColors.Faint,
                fontSize = 10.5.sp,
                lineHeight = 16.sp,
            )
            if (CleanMode.remoteEnabled) {
                Text(
                    text = "服务端已开启全局干净模式，所有设备都看不到收藏。" +
                        "这个开关只能在网页端关闭。",
                    color = PlayerColors.Accent,
                    fontSize = 10.5.sp,
                    lineHeight = 16.sp,
                )
            }
        }
    }
}
