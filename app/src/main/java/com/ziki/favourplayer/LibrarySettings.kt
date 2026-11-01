package com.ziki.favourplayer

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

const val DEFAULT_SERVER = ""
const val SERVER_HINT = "https://<your-device>.<tailnet>.ts.net/favour"

@Composable
fun SetupScreen(
    state: LibraryUiState,
    onSave: (String, String) -> Unit,
    onManualPlay: () -> Unit,
) {
    var server by rememberSaveable { mutableStateOf(DEFAULT_SERVER) }
    var token by rememberSaveable { mutableStateOf("") }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(PlayerColors.Bg)
            .windowInsetsPadding(WindowInsets.safeDrawing),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            BrandMark(56.dp)
            Spacer(Modifier.height(16.dp))
            Text(
                text = "连接 Favour 同步",
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
                color = PlayerColors.Text,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "输入服务地址和访问令牌，收藏会同步到本机并缓存。",
                style = MaterialTheme.typography.bodyMedium,
                color = PlayerColors.Muted,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(24.dp))

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                color = PlayerColors.Panel,
                border = BorderStroke(1.dp, PlayerColors.Border),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    SyncFields(
                        server = server,
                        onServerChange = { server = it },
                        token = token,
                        onTokenChange = { token = it },
                    )
                    Button(
                        onClick = { onSave(server, token) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        enabled = server.isNotBlank() && token.isNotBlank() && !state.loading,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PlayerColors.Accent,
                            contentColor = PlayerColors.AccentInk,
                            disabledContainerColor = PlayerColors.Accent.copy(alpha = 0.28f),
                            disabledContentColor = PlayerColors.AccentInk.copy(alpha = 0.55f),
                        ),
                    ) {
                        if (state.loading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = PlayerColors.AccentInk,
                            )
                        } else {
                            Text("保存并同步", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            state.error?.let { message ->
                Spacer(Modifier.height(12.dp))
                Text(
                    text = message,
                    color = PlayerColors.Danger,
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                )
            }

            Spacer(Modifier.height(16.dp))
            TextButton(onClick = onManualPlay) {
                Text("先手动播放一个地址", color = PlayerColors.Accent)
            }
        }
    }
}

@Composable
fun SettingsDialog(
    state: LibraryUiState,
    onSave: (String, String) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
) {
    var server by rememberSaveable { mutableStateOf(state.config?.serverUrl ?: DEFAULT_SERVER) }
    var token by rememberSaveable { mutableStateOf(state.config?.token.orEmpty()) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .widthIn(max = 520.dp)
                .heightIn(max = 560.dp),
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
                            imageVector = Icons.Default.Close,
                            contentDescription = "关闭",
                            tint = PlayerColors.Faint,
                        )
                    }
                }
                Text(
                    text = "同步设置",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = PlayerColors.Text,
                )
                Text(
                    text = "收藏数据从 Favour 同步服务读取；保存后立即同步。",
                    style = MaterialTheme.typography.bodySmall,
                    color = PlayerColors.Muted,
                )
                SyncFields(
                    server = server,
                    onServerChange = { server = it },
                    token = token,
                    onTokenChange = { token = it },
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { onSave(server, token) },
                        enabled = server.isNotBlank() && token.isNotBlank() && !state.loading,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PlayerColors.Accent,
                            contentColor = PlayerColors.AccentInk,
                        ),
                    ) {
                        Text("保存并同步")
                    }
                    OutlinedButton(
                        onClick = onClear,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, PlayerColors.Danger.copy(alpha = 0.5f)),
                    ) {
                        Text("清除配置", color = PlayerColors.Danger)
                    }
                }
                state.error?.let { message ->
                    Text(
                        text = message,
                        color = PlayerColors.Danger,
                        style = MaterialTheme.typography.bodySmall,
                    )
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
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "关闭",
                            tint = PlayerColors.Faint,
                        )
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
                    placeholder = { Text("https://...") },
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
private fun SyncFields(
    server: String,
    onServerChange: (String) -> Unit,
    token: String,
    onTokenChange: (String) -> Unit,
) {
    var tokenVisible by remember { mutableStateOf(false) }
    val colors = fieldColors()

    OutlinedTextField(
        value = server,
        onValueChange = onServerChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text("同步服务地址") },
        placeholder = { Text(SERVER_HINT) },
        singleLine = true,
        shape = RoundedCornerShape(12.dp),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
        colors = colors,
    )
    OutlinedTextField(
        value = token,
        onValueChange = onTokenChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text("访问令牌") },
        singleLine = true,
        shape = RoundedCornerShape(12.dp),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
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
        colors = colors,
    )
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
