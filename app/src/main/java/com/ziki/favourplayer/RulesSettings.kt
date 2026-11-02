package com.ziki.favourplayer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun RulesPage(
    userJson: String,
    defaultJson: String,
    onSave: (String) -> String?,
    onReset: () -> Unit,
    onBack: () -> Unit,
) {
    var text by rememberSaveable { mutableStateOf(userJson) }
    var error by remember { mutableStateOf<String?>(null) }

    SubPage(
        title = "解析规则",
        subtitle = "自定义规则优先于内置示例",
        onBack = onBack,
        contentSpacing = 12,
    ) {
        Text(
            text = "粘贴 {\"rules\":[...]}，保存后下次播放生效。",
            color = PlayerColors.Muted,
            fontSize = 12.5.sp,
        )
        OutlinedTextField(
            value = text,
            onValueChange = { text = it; error = null },
            modifier = Modifier.fillMaxWidth(),
            minLines = 8,
            maxLines = 14,
            textStyle = MaterialTheme.typography.bodySmall.copy(
                fontFamily = FontFamily.Monospace,
            ),
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = PlayerColors.Text,
                unfocusedTextColor = PlayerColors.Text,
                focusedContainerColor = PlayerColors.PanelSoft,
                unfocusedContainerColor = PlayerColors.PanelSoft,
                cursorColor = PlayerColors.Accent,
                focusedBorderColor = PlayerColors.Accent,
                unfocusedBorderColor = PlayerColors.Border,
            ),
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TextButton(onClick = { text = defaultJson; error = null }) {
                Text("填入默认示例", color = PlayerColors.Accent)
            }
            TextButton(onClick = { onReset(); text = ""; error = null }) {
                Text("恢复默认", color = PlayerColors.Danger)
            }
            Spacer(Modifier.weight(1f))
            Button(
                onClick = {
                    val message = onSave(text)
                    if (message == null) onBack() else error = message
                },
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = PlayerColors.Accent,
                    contentColor = PlayerColors.AccentInk,
                ),
            ) {
                Text("保存")
            }
        }
        error?.let { message ->
            Text(
                text = message,
                color = PlayerColors.Danger,
                fontSize = 12.sp,
            )
        }
    }
}
