package com.ziki.favourplayer

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SyncSettingsDialog(
    state: LibraryUiState,
    onSave: (String, String, String, Boolean, String, String) -> String?,
    onRegenerateToken: () -> String,
    onRefreshAddresses: () -> Unit,
    onRules: () -> Unit,
    onCache: () -> Unit,
    onDismiss: () -> Unit,
) {
    var mode by rememberSaveable { mutableStateOf(state.mode) }
    var port by rememberSaveable { mutableStateOf(state.port.toString()) }
    var token by rememberSaveable { mutableStateOf(state.token) }
    var enabled by rememberSaveable { mutableStateOf(state.enabled) }
    var pullUrl by rememberSaveable { mutableStateOf(state.pullUrl) }
    var pullToken by rememberSaveable { mutableStateOf(state.pullToken) }
    var receiveTokenVisible by remember { mutableStateOf(false) }
    var pullTokenVisible by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val pullMode = mode == SYNC_MODE_PULL

    fun save(close: Boolean) {
        val message = onSave(mode, port, token, enabled, pullUrl, pullToken)
        if (message == null) {
            error = null
            if (close) onDismiss()
        } else {
            error = message
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .widthIn(max = 560.dp)
                .heightIn(max = 720.dp),
            shape = RoundedCornerShape(18.dp),
            color = PlayerColors.Panel,
            border = BorderStroke(1.dp, PlayerColors.Border),
            shadowElevation = 24.dp,
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    IconButton(onClick = onDismiss, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "关闭", tint = PlayerColors.Faint)
                    }
                }

                Text(
                    text = "同步",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = PlayerColors.Text,
                )
                Text(
                    text = "从中间服务拉取最新收藏，或保留扩展直推模式。",
                    style = MaterialTheme.typography.bodySmall,
                    color = PlayerColors.Muted,
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = pullMode,
                        onClick = { mode = SYNC_MODE_PULL; error = null },
                        label = { Text("中间服务") },
                        colors = modeChipColors(),
                    )
                    FilterChip(
                        selected = !pullMode,
                        onClick = { mode = SYNC_MODE_RECEIVE; error = null },
                        label = { Text("直接接收") },
                        colors = modeChipColors(),
                    )
                }

                if (pullMode) {
                    Text(
                        text = "中间服务",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = PlayerColors.Text,
                    )
                    Text(
                        text = "中间服务常驻在电脑或迷你主机上；手机只需在拉取时开启 Tailscale。",
                        color = PlayerColors.Muted,
                        fontSize = 12.5.sp,
                    )
                    OutlinedTextField(
                        value = pullUrl,
                        onValueChange = { pullUrl = it; error = null },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("服务地址") },
                        placeholder = { Text("https://<主机>/favour") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        colors = fieldColors(),
                    )
                    OutlinedTextField(
                        value = pullToken,
                        onValueChange = { pullToken = it; error = null },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("访问令牌") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        visualTransformation = if (pullTokenVisible) {
                            VisualTransformation.None
                        } else {
                            PasswordVisualTransformation()
                        },
                        trailingIcon = {
                            TextButton(onClick = { pullTokenVisible = !pullTokenVisible }) {
                                Text(
                                    if (pullTokenVisible) "隐藏" else "显示",
                                    color = PlayerColors.Accent,
                                )
                            }
                        },
                        colors = fieldColors(),
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = pullStatusText(state),
                            color = if (state.pullError != null) {
                                PlayerColors.Danger
                            } else {
                                PlayerColors.Muted
                            },
                            fontSize = 12.5.sp,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(
                            onClick = { save(close = false) },
                            enabled = !state.pulling,
                        ) {
                            Text(
                                if (state.pulling) "拉取中…" else "立即拉取",
                                color = PlayerColors.Accent,
                            )
                        }
                    }
                    Text(
                        text = "地址和令牌与扩展「同步设置」里填的一致；填到 /favour，不要带 /api/items。",
                        color = PlayerColors.Faint,
                        fontSize = 11.sp,
                    )
                } else {
                    Text(
                        text = when {
                            state.serverError != null -> "服务异常：${state.serverError}"
                            state.serverRunning -> "服务运行中 · 端口 ${state.port}"
                            else -> "接收服务已停止"
                        },
                        color = if (state.serverError != null) PlayerColors.Danger else PlayerColors.Muted,
                        fontSize = 12.5.sp,
                    )

                    Text(
                        text = "接收地址",
                        style = MaterialTheme.typography.labelLarge,
                        color = PlayerColors.Text,
                    )
                    if (state.addresses.isEmpty()) {
                        Text(
                            text = "未检测到地址，请确认手机已连 Wi-Fi 或 Tailscale。",
                            color = PlayerColors.Faint,
                            fontSize = 12.sp,
                        )
                    }
                    state.addresses.forEach { url ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = url,
                                color = PlayerColors.Text,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(onClick = { copyToClipboard(context, url, "接收地址") }) {
                                Text("复制", color = PlayerColors.Accent)
                            }
                        }
                    }
                    TextButton(onClick = onRefreshAddresses) {
                        Text("刷新地址", color = PlayerColors.Accent)
                    }

                    OutlinedTextField(
                        value = port,
                        onValueChange = {
                            port = it.filter(Char::isDigit).take(5)
                            error = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("端口") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = fieldColors(),
                    )
                    OutlinedTextField(
                        value = token,
                        onValueChange = { token = it; error = null },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("访问令牌") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        visualTransformation = if (receiveTokenVisible) {
                            VisualTransformation.None
                        } else {
                            PasswordVisualTransformation()
                        },
                        trailingIcon = {
                            TextButton(onClick = { receiveTokenVisible = !receiveTokenVisible }) {
                                Text(
                                    if (receiveTokenVisible) "隐藏" else "显示",
                                    color = PlayerColors.Accent,
                                )
                            }
                        },
                        colors = fieldColors(),
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = {
                            token = onRegenerateToken()
                            error = null
                        }) {
                            Text("重新生成令牌", color = PlayerColors.Danger)
                        }
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = { copyToClipboard(context, token, "访问令牌") }) {
                            Text("复制令牌", color = PlayerColors.Accent)
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("开启接收服务", color = PlayerColors.Text, fontSize = 14.sp)
                            Text(
                                "关闭后扩展无法直推；服务运行时会在通知栏常驻。",
                                color = PlayerColors.Faint,
                                fontSize = 11.5.sp,
                            )
                        }
                        Switch(checked = enabled, onCheckedChange = { enabled = it })
                    }

                    Text(
                        text = "在 Favour 扩展 → 同步设置：地址填上面的 http://…，令牌填上面的令牌；手机和电脑需在同一 Tailscale 或局域网。",
                        style = MaterialTheme.typography.bodySmall,
                        color = PlayerColors.Muted,
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(
                        onClick = { save(close = true) },
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PlayerColors.Accent,
                            contentColor = PlayerColors.AccentInk,
                        ),
                    ) {
                        Text("保存")
                    }
                    TextButton(onClick = onRules) {
                        Text("解析规则…", color = PlayerColors.Accent)
                    }
                    TextButton(onClick = onCache) {
                        Text("缓存设置…", color = PlayerColors.Accent)
                    }
                }
                error?.let { message ->
                    Text(text = message, color = PlayerColors.Danger, fontSize = 12.sp)
                }
            }
        }
    }
}

private fun pullStatusText(state: LibraryUiState): String {
    val syncedAt = state.lastReceivedAt?.takeIf { it > 0L }
    return when {
        state.pulling -> "正在拉取…"
        state.pullError != null -> "拉取失败：${state.pullError}"
        syncedAt != null ->
            "上次数据：${formatSyncTime(syncedAt)} · ${state.snapshot?.items?.size ?: 0} 条"
        else -> "尚未拉取"
    }
}

@Composable
private fun modeChipColors() = FilterChipDefaults.filterChipColors(
    selectedContainerColor = PlayerColors.Accent,
    selectedLabelColor = PlayerColors.AccentInk,
)

private fun formatSyncTime(timestamp: Long): String =
    SimpleDateFormat("MM-dd HH:mm", Locale.CHINA).format(Date(timestamp))
