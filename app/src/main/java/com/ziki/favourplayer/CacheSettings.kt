package com.ziki.favourplayer

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

private val CACHE_SIZES = listOf(
    512L * 1024 * 1024 to "512 MB",
    1L * 1024 * 1024 * 1024 to "1 GB",
    2L * 1024 * 1024 * 1024 to "2 GB",
    4L * 1024 * 1024 * 1024 to "4 GB",
    8L * 1024 * 1024 * 1024 to "8 GB",
)

@Composable
fun CacheSettingsDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val store = remember { CacheStore(context) }
    val scope = rememberCoroutineScope()
    var config by remember { mutableStateOf(store.load()) }
    var usage by remember { mutableStateOf<CacheUsage?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(false) }

    LaunchedEffect(config, reload) {
        usage = withContext(Dispatchers.IO) { MediaCache.usage(context) }
    }

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
                    text = "缓存",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = PlayerColors.Text,
                )
                Text(
                    text = "视频分片缓存用于复用已加载内容、减少重复下载；不保证完整离线。",
                    style = MaterialTheme.typography.bodySmall,
                    color = PlayerColors.Muted,
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("开启视频缓存", color = PlayerColors.Text, fontSize = 14.sp)
                        Text(
                            "关闭后每次播放都重新下载。",
                            color = PlayerColors.Faint,
                            fontSize = 11.5.sp,
                        )
                    }
                    Switch(
                        checked = config.enabled,
                        onCheckedChange = { config = config.copy(enabled = it) },
                    )
                }

                Text(
                    text = "最大缓存",
                    style = MaterialTheme.typography.labelLarge,
                    color = PlayerColors.Text,
                )
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CACHE_SIZES.forEach { (bytes, label) ->
                        FilterChip(
                            selected = config.maxBytes == bytes,
                            onClick = { config = config.copy(maxBytes = bytes) },
                            label = { Text(label) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = PlayerColors.Accent,
                                selectedLabelColor = PlayerColors.AccentInk,
                            ),
                        )
                    }
                }

                Text(
                    text = "存储位置",
                    style = MaterialTheme.typography.labelLarge,
                    color = PlayerColors.Text,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = config.location == CACHE_LOCATION_INTERNAL,
                        onClick = { config = config.copy(location = CACHE_LOCATION_INTERNAL) },
                        label = { Text("内部存储") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = PlayerColors.Accent,
                            selectedLabelColor = PlayerColors.AccentInk,
                        ),
                    )
                    FilterChip(
                        selected = config.location == CACHE_LOCATION_EXTERNAL,
                        onClick = { config = config.copy(location = CACHE_LOCATION_EXTERNAL) },
                        label = { Text("外部存储") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = PlayerColors.Accent,
                            selectedLabelColor = PlayerColors.AccentInk,
                        ),
                    )
                }

                val current = usage
                Text(
                    text = "已用 ${formatBytes(current?.usedBytes ?: 0)} / " +
                        "上限 ${formatBytes(config.maxBytes)} · ${current?.entries ?: 0} 项",
                    color = PlayerColors.Muted,
                    fontSize = 12.5.sp,
                )
                Text(
                    text = current?.path ?: CacheStore.cacheDir(context, config.location).absolutePath,
                    color = PlayerColors.Faint,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.5.sp,
                )
                Text(
                    text = "外部存储使用 App 专属缓存目录；卸载 App 时系统会一并清理。",
                    color = PlayerColors.Faint,
                    fontSize = 11.sp,
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(
                        onClick = {
                            store.save(config)
                            MediaCache.release()
                            onDismiss()
                        },
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PlayerColors.Accent,
                            contentColor = PlayerColors.AccentInk,
                        ),
                    ) {
                        Text("保存")
                    }
                    TextButton(
                        onClick = {
                            scope.launch {
                                busy = true
                                withContext(Dispatchers.IO) { MediaCache.clear(context) }
                                reload += 1
                                busy = false
                            }
                        },
                        enabled = !busy,
                    ) {
                        Text(if (busy) "清理中…" else "清理缓存", color = PlayerColors.Danger)
                    }
                }
            }
        }
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0L) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var index = 0
    while (value >= 1024 && index < units.lastIndex) {
        value /= 1024
        index += 1
    }
    return if (index == 0) {
        "${bytes.toLong()} ${units[index]}"
    } else {
        String.format(Locale.US, "%.1f %s", value, units[index])
    }
}
