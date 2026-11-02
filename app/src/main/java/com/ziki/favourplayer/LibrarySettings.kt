package com.ziki.favourplayer

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

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
internal fun fieldColors() = OutlinedTextFieldDefaults.colors(
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

internal fun copyToClipboard(context: Context, text: String, label: String) {
    runCatching {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
        Toast.makeText(context, "已复制", Toast.LENGTH_SHORT).show()
    }
}
