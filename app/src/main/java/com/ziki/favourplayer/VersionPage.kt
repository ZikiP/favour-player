package com.ziki.favourplayer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun VersionPage(onBack: () -> Unit) {
    val context = LocalContext.current
    // 干净模式：默认跟服务端，用户手动改过就以本机为准
    val scope = rememberCoroutineScope()
    var checking by remember { mutableStateOf(false) }
    var checked by remember { mutableStateOf(false) }
    var updateInfo by remember { mutableStateOf<UpdateInfo?>(null) }
    var downloading by remember { mutableStateOf(false) }
    var updateError by remember { mutableStateOf<String?>(null) }

    fun check() {
        if (checking) return
        checking = true
        updateError = null
        scope.launch {
            val info = withContext(Dispatchers.IO) { Updater.checkLatest() }
            checking = false
            checked = true
            when {
                info == null -> {
                    updateInfo = null
                    updateError = "检查失败，请检查网络"
                }
                info.versionCode > BuildConfig.VERSION_CODE -> updateInfo = info
                else -> updateInfo = null
            }
        }
    }

    fun downloadAndInstall() {
        val info = updateInfo ?: return
        if (downloading) return
        downloading = true
        updateError = null
        scope.launch {
            val file = withContext(Dispatchers.IO) { Updater.downloadApk(context, info.apkUrl) }
            downloading = false
            if (file == null) {
                updateError = "下载失败，稍后重试"
            } else {
                Updater.install(context, file)
            }
        }
    }

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
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "更新",
                color = PlayerColors.Text,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
            )
            val info = updateInfo
            when {
                info != null -> {
                    Text(
                        text = "发现新版本 v${info.versionName}（code ${info.versionCode}）",
                        color = PlayerColors.Accent,
                        fontSize = 12.5.sp,
                    )
                    Button(
                        onClick = { downloadAndInstall() },
                        enabled = !downloading,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PlayerColors.Accent,
                            contentColor = PlayerColors.AccentInk,
                        ),
                    ) {
                        Text(
                            text = if (downloading) "下载中…" else "下载并安装",
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
                updateError != null -> Text(
                    text = updateError.orEmpty(),
                    color = PlayerColors.Danger,
                    fontSize = 11.5.sp,
                )
                checked -> Text(
                    text = "已是最新版本",
                    color = PlayerColors.Muted,
                    fontSize = 12.sp,
                )
                else -> Text(
                    text = "检查是否有新的正式版本",
                    color = PlayerColors.Muted,
                    fontSize = 11.5.sp,
                )
            }
            Button(
                onClick = { check() },
                enabled = !checking && !downloading,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = PlayerColors.PanelSoft,
                    contentColor = PlayerColors.Text,
                ),
            ) {
                Text(text = if (checking) "检查中…" else "检查更新", fontSize = 13.sp)
            }
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
                    checked = CleanMode.enabled,
                    onCheckedChange = { value ->
                        // 手动改过就算本地覆盖：离线也能随时开关，同步不会改回来
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
                    text = if (CleanMode.overridden) {
                        "服务端开着干净模式，你现在按本机设置走（已覆盖）。" +
                            "下面的按钮可以改回跟随服务端。"
                    } else {
                        "服务端开着干净模式，同步后按服务端走。" +
                            "想只看这台设备，可以直接在这里关掉。"
                    },
                    color = PlayerColors.Accent,
                    fontSize = 10.5.sp,
                    lineHeight = 16.sp,
                )
            } else if (CleanMode.overridden) {
                Text(
                    text = "当前按本机设置走（已覆盖服务端），点下面的按钮可以改回跟随服务端。",
                    color = PlayerColors.Accent,
                    fontSize = 10.5.sp,
                    lineHeight = 16.sp,
                )
            }
            if (CleanMode.overridden) {
                TextButton(onClick = { CleanMode.followServer(context) }) {
                    Text("跟随服务端", color = PlayerColors.Accent, fontSize = 11.sp)
                }
            }
        }
    }
}
