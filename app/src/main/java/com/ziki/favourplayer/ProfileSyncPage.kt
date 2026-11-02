package com.ziki.favourplayer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ProfileSyncPage(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf(ProfileSync.status(context)) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var confirmingDelete by remember { mutableStateOf(false) }

    fun refresh() {
        status = ProfileSync.status(context)
    }

    // 服务端上的设备列表，和网页的设备管理页看到的是同一份数据
    var remoteDevices by remember { mutableStateOf<List<ProfileSummary>>(emptyList()) }

    suspend fun loadRemote(): List<ProfileSummary> {
        val config = SyncStore(context).loadPullConfig()
        if (config.url.isBlank() || config.token.isBlank()) return emptyList()
        return runCatching { ProfileClient.list(config) }.getOrDefault(emptyList())
    }

    fun run(block: suspend () -> String) {
        if (busy) return
        scope.launch {
            busy = true
            val text = withContext(Dispatchers.IO) { block() }
            message = text
            remoteDevices = loadRemote()
            busy = false
            refresh()
        }
    }

    LaunchedEffect(Unit) {
        remoteDevices = loadRemote()
    }

    SubPage(
        title = "偏好同步",
        subtitle = "按设备保存，重装后自动恢复",
        onBack = onBack,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(PlayerColors.Panel, RoundedCornerShape(14.dp))
                .border(1.dp, PlayerColors.Border, RoundedCornerShape(14.dp))
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Text(
                text = status.label,
                color = PlayerColors.Text,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = "设备 ID · ${status.deviceId.take(10)}",
                color = PlayerColors.Faint,
                fontSize = 10.5.sp,
                fontFamily = FontFamily.Monospace,
            )
            Text(
                text = when {
                    !status.configured -> "先在「中间服务」里填地址和令牌"
                    status.lastUploadAt > 0 -> "上次上传 · ${formatProfileTime(status.lastUploadAt)}"
                    else -> "还没有上传过"
                },
                color = PlayerColors.Muted,
                fontSize = 11.5.sp,
            )
            if (status.lastError.isNotBlank()) {
                Text(
                    text = status.lastError,
                    color = PlayerColors.Danger,
                    fontSize = 11.sp,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            message?.let { text ->
                Text(
                    text = text,
                    color = PlayerColors.Accent,
                    fontSize = 11.5.sp,
                )
            }
        }

        Text(
            text = "同步内容：播放偏好、画质设置、字幕样式（含按作品单独调过的）、缓存上限、自定义解析规则。" +
                "不同步中间服务地址与令牌。",
            color = PlayerColors.Faint,
            fontSize = 11.sp,
            lineHeight = 17.sp,
        )

        Button(
            onClick = {
                run {
                    if (ProfileSync.upload(context, force = true)) "已上传当前设置" else "上传失败"
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp),
            enabled = !busy && status.configured,
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = PlayerColors.Accent,
                contentColor = PlayerColors.AccentInk,
            ),
        ) {
            Text("立即上传", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
        }

        OutlinedButton(
            onClick = {
                run {
                    val result = ProfileSync.pullNow(context)
                    when {
                        result.isFailure -> "恢复失败：${result.exceptionOrNull()?.message.orEmpty()}"
                        result.getOrNull() == true -> "已从服务端恢复，重新进入页面后生效"
                        else -> "服务端还没有这台设备的记录"
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp),
            enabled = !busy && status.configured,
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = PlayerColors.Text),
        ) {
            Text("从服务端恢复", fontSize = 13.sp)
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .background(
                    if (confirmingDelete) {
                        PlayerColors.Danger.copy(alpha = 0.12f)
                    } else {
                        PlayerColors.PanelSoft
                    },
                    RoundedCornerShape(10.dp),
                )
                .border(
                    1.dp,
                    if (confirmingDelete) PlayerColors.Danger else PlayerColors.Border,
                    RoundedCornerShape(10.dp),
                )
                .clickable(enabled = !busy && status.configured) {
                    if (!confirmingDelete) {
                        confirmingDelete = true
                    } else {
                        confirmingDelete = false
                        run {
                            ProfileSync.forget(context)
                            "已删除服务端记录"
                        }
                    }
                },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Text(
                text = if (confirmingDelete) "确认删除？再点一次" else "删除服务端记录",
                color = if (confirmingDelete) PlayerColors.Danger else PlayerColors.Muted,
                fontSize = 12.5.sp,
            )
        }

        Text(
            text = "卸载重装后，首次打开会自动从服务端拉回这台设备的设置。" +
                "换新设备不会自动沿用旧设备的配置。",
            color = PlayerColors.Faint,
            fontSize = 10.5.sp,
            lineHeight = 16.sp,
        )

        if (remoteDevices.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(PlayerColors.Panel, RoundedCornerShape(14.dp))
                    .border(1.dp, PlayerColors.Border, RoundedCornerShape(14.dp))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = "服务端上的设备 · ${remoteDevices.size}",
                    color = PlayerColors.Text,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                )
                remoteDevices.forEach { device ->
                    val isSelf = device.deviceId == status.deviceId
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = device.displayLabel.ifBlank { device.deviceId.take(8) },
                                color = PlayerColors.Text,
                                fontSize = 12.5.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = "${device.deviceId.take(10)} · ${formatProfileTime(device.updatedAt)}",
                                color = PlayerColors.Faint,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        if (isSelf) {
                            Text(
                                text = "本机",
                                color = PlayerColors.Accent,
                                fontSize = 11.5.sp,
                            )
                        } else {
                            TextButton(
                                onClick = {
                                    run {
                                        val config = SyncStore(context).loadPullConfig()
                                        ProfileClient.copy(config, device.deviceId, status.deviceId)
                                        "已复制到本机，回到上一页后点「从服务端恢复」生效"
                                    }
                                },
                                enabled = !busy,
                            ) {
                                Text("复制到本机", color = PlayerColors.Accent, fontSize = 12.sp)
                            }
                            TextButton(
                                onClick = {
                                    run {
                                        val config = SyncStore(context).loadPullConfig()
                                        ProfileClient.delete(config, device.deviceId)
                                        "已删除 ${device.displayLabel}"
                                    }
                                },
                                enabled = !busy,
                            ) {
                                Text("删除", color = PlayerColors.Danger, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatProfileTime(timestamp: Long): String =
    SimpleDateFormat("MM-dd HH:mm", Locale.CHINA).format(Date(timestamp))
