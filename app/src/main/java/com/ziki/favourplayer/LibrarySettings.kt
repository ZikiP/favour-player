package com.ziki.favourplayer

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.OutlinedTextFieldDefaults
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

@Composable
fun ReceiveSettingsDialog(
    state: LibraryUiState,
    onSave: (String, String, Boolean) -> String?,
    onRegenerateToken: () -> String,
    onRefreshAddresses: () -> Unit,
    onRules: () -> Unit,
    onDismiss: () -> Unit,
) {
    var port by rememberSaveable { mutableStateOf(state.port.toString()) }
    var token by rememberSaveable { mutableStateOf(state.token) }
    var enabled by rememberSaveable { mutableStateOf(state.enabled) }
    var tokenVisible by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .widthIn(max = 560.dp)
                .heightIn(max = 680.dp),
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
                    text = "接收同步",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = PlayerColors.Text,
                )
                Text(
                    text = "把收藏从 Favour 扩展直接推送到本机，不再经过电脑上的服务。",
                    style = MaterialTheme.typography.bodySmall,
                    color = PlayerColors.Muted,
                )

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
                    onValueChange = { port = it.filter(Char::isDigit).take(5); error = null },
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
                    visualTransformation = if (tokenVisible) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    trailingIcon = {
                        TextButton(onClick = { tokenVisible = !tokenVisible }) {
                            Text(if (tokenVisible) "隐藏" else "显示", color = PlayerColors.Accent)
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
                            "关闭后扩展无法推送；服务运行时会在通知栏常驻。",
                            color = PlayerColors.Faint,
                            fontSize = 11.5.sp,
                        )
                    }
                    Switch(checked = enabled, onCheckedChange = { enabled = it })
                }

                Text(
                    text = "在 Favour 扩展 → 手机同步：地址填上面的 http://…，令牌填上面的令牌；手机和电脑需在同一 Tailscale 或局域网。",
                    style = MaterialTheme.typography.bodySmall,
                    color = PlayerColors.Muted,
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(
                        onClick = {
                            val message = onSave(port, token, enabled)
                            if (message == null) onDismiss() else error = message
                        },
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
                }
                error?.let { message ->
                    Text(text = message, color = PlayerColors.Danger, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
fun ManualPlayDialog(
    onPlay: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var url by rememberSaveable { mutableStateOf("") }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .widthIn(max = 520.dp),
            shape = RoundedCornerShape(18.dp),
            color = PlayerColors.Panel,
            border = BorderStroke(1.dp, PlayerColors.Border),
            shadowElevation = 24.dp,
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
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
                    text = "手动播放",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = PlayerColors.Text,
                )
                Text(
                    text = "粘贴收藏页地址或 .m3u8 直链。",
                    style = MaterialTheme.typography.bodySmall,
                    color = PlayerColors.Muted,
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("地址") },
                    placeholder = { Text("https://example.com/watch/...") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    colors = fieldColors(),
                )
                Button(
                    onClick = { onPlay(url.trim()) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp),
                    enabled = url.isNotBlank(),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PlayerColors.Accent,
                        contentColor = PlayerColors.AccentInk,
                    ),
                ) {
                    Text("播放", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = PlayerColors.Text,
    unfocusedTextColor = PlayerColors.Text,
    focusedContainerColor = PlayerColors.PanelSoft,
    unfocusedContainerColor = PlayerColors.PanelSoft,
    cursorColor = PlayerColors.Accent,
    focusedBorderColor = PlayerColors.Accent,
    unfocusedBorderColor = PlayerColors.Border,
    focusedLabelColor = PlayerColors.Accent,
    unfocusedLabelColor = PlayerColors.Muted,
    focusedPlaceholderColor = PlayerColors.Faint,
    unfocusedPlaceholderColor = PlayerColors.Faint,
)

private fun copyToClipboard(context: Context, text: String, label: String) {
    runCatching {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
        Toast.makeText(context, "已复制", Toast.LENGTH_SHORT).show()
    }
}
