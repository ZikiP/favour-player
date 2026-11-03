package com.ziki.favourplayer

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
fun SyncSettingsPage(
    state: LibraryUiState,
    onSave: (String, String) -> String?,
    onTest: (String, String) -> Unit,
    onRules: () -> Unit,
    onCache: () -> Unit,
    onBack: () -> Unit,
) {
    var pullUrl by rememberSaveable { mutableStateOf(state.pullUrl) }
    var pullToken by rememberSaveable { mutableStateOf(state.pullToken) }
    var pullTokenVisible by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun save(close: Boolean) {
        val message = onSave(pullUrl, pullToken)
        if (message == null) {
            error = null
            if (close) onBack()
        } else {
            error = message
        }
    }

    SubPage(
        title = "同步",
        subtitle = "从中间服务拉取最新收藏",
        onBack = onBack,
        contentSpacing = 12,
    ) {
        Text(
            text = "手机只需在拉取时开启 Tailscale，平时可完全离线浏览缓存。",
            color = PlayerColors.Muted,
            fontSize = 12.5.sp,
        )

        Text(
            text = "中间服务",
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
            color = PlayerColors.Text,
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(
                        onClick = { onTest(pullUrl, pullToken) },
                        enabled = !state.pullTesting && !state.pulling,
                    ) {
                        Text(
                            if (state.pullTesting) "测试中…" else "测试连接",
                            color = PlayerColors.Accent,
                        )
                    }
                    state.pullTestMessage?.let { message ->
                        Text(
                            text = message,
                            color = if (state.pullTestOk) {
                                PlayerColors.Muted
                            } else {
                                PlayerColors.Danger
                            },
                            fontSize = 12.sp,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                Text(
                    text = "手机不能填 127.0.0.1；请填中间服务的 Tailscale HTTPS 地址（https://主机/favour，不含 /api/items）。地址和令牌与扩展「同步设置」一致。",
                    color = PlayerColors.Faint,
                    fontSize = 11.sp,
                )

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

private fun pullStatusText(state: LibraryUiState): String {
    val pulledAt = (state.lastPulledAt ?: state.lastReceivedAt)?.takeIf { it > 0L }
    val dataAt = state.lastReceivedAt?.takeIf { it > 0L }
    return when {
        state.pulling -> "正在拉取…"
        state.pullError != null -> "拉取失败：${state.pullError}"
        pulledAt != null -> buildString {
            append("上次同步：${formatSyncMoment(pulledAt)}")
            if (dataAt != null) append(" · 数据 ${formatSyncTime(dataAt)}")
            append(" · ${state.snapshot?.items?.size ?: 0} 条")
        }
        else -> "尚未拉取"
    }
}

private fun formatSyncTime(timestamp: Long): String =
    SimpleDateFormat("MM-dd HH:mm", Locale.CHINA).format(Date(timestamp))
