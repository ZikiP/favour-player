package com.ziki.favourplayer

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ManualPlayPage(
    onPlay: (String) -> Unit,
    onBack: () -> Unit,
) {
    var url by rememberSaveable { mutableStateOf("") }

    SubPage(
        title = "手动播放",
        subtitle = "粘贴收藏页地址或 .m3u8 直链",
        onBack = onBack,
        contentSpacing = 14,
    ) {
        Text(
            text = "解析规则会按地址的域名自动匹配；直链则直接播放。",
            color = PlayerColors.Muted,
            fontSize = 12.5.sp,
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
