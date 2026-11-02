package com.ziki.favourplayer

import android.widget.Toast
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private data class SnapshotInfo(
    val items: Int,
    val bytes: Long,
    val updatedAt: Long,
)

@Composable
fun CacheSettingsDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val store = remember { CacheStore(context) }
    val scope = rememberCoroutineScope()
    var config by remember { mutableStateOf(store.load()) }
    var coverUsage by remember { mutableStateOf<CacheUsage?>(null) }
    var videoUsage by remember { mutableStateOf<CacheUsage?>(null) }
    var snapshotInfo by remember { mutableStateOf<SnapshotInfo?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(reload) {
        coverUsage = withContext(Dispatchers.IO) { CoverCache.usage(context) }
        videoUsage = withContext(Dispatchers.IO) { MediaCache.usage(context) }
        snapshotInfo = withContext(Dispatchers.IO) {
            val cached = SyncStore(context).loadCached()
            SnapshotInfo(
                items = cached?.items?.size ?: 0,
                bytes = File(context.filesDir, "snapshot.json").length(),
                updatedAt = cached?.updatedAt ?: 0L,
            )
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
                    text = "缓存",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = PlayerColors.Text,
                )
                Text(
                    text = "收藏文字、封面和视频片段可分别缓存；封面与视频的大小、位置可调。",
                    style = MaterialTheme.typography.bodySmall,
                    color = PlayerColors.Muted,
                )

                Text(
                    text = "收藏信息",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = PlayerColors.Text,
                )
                Text(
                    text = "标题、编号、演员、标签、最爱等文字已保存在本机，离线时仍可浏览、搜索和查看详情。",
                    color = PlayerColors.Muted,
                    fontSize = 12.5.sp,
                )
                val info = snapshotInfo
                Text(
                    text = when {
                        info == null -> "读取中…"
                        info.items == 0 -> "尚无本地收藏，连接中间服务拉取一次即可离线查看。"
                        else -> "${info.items} 条 · ${formatBytes(info.bytes)}" +
                            if (info.updatedAt > 0) " · 更新于 ${formatCacheTime(info.updatedAt)}" else ""
                    },
                    color = PlayerColors.Faint,
                    fontSize = 11.5.sp,
                )
                Text(
                    text = "文字体积很小，固定保存在应用私有目录，不随下面的存储位置变化。",
                    color = PlayerColors.Faint,
                    fontSize = 11.sp,
                )

                SectionDivider()

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("缓存封面", color = PlayerColors.Text, fontSize = 14.sp)
                        Text(
                            "同步后自动缓存，离线也能看到封面。",
                            color = PlayerColors.Faint,
                            fontSize = 11.5.sp,
                        )
                    }
                    Switch(
                        checked = config.coverEnabled,
                        onCheckedChange = { config = config.copy(coverEnabled = it) },
                    )
                }
                SizeChips(
                    label = "封面缓存上限",
                    sizes = COVER_CACHE_SIZES,
                    selected = config.coverMaxBytes,
                    onSelect = { config = config.copy(coverMaxBytes = it) },
                )
                UsageText(
                    usage = coverUsage,
                    maxBytes = config.coverMaxBytes,
                    unit = "张",
                    enabled = config.coverEnabled,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(
                        onClick = {
                            if (store.load().coverEnabled) {
                                CoverCache.prefetchCached(context)
                                Toast.makeText(context, "已开始缓存封面", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "请先保存开启封面缓存", Toast.LENGTH_SHORT).show()
                            }
                        },
                        enabled = config.coverEnabled && (info?.items ?: 0) > 0,
                    ) {
                        Text("缓存全部封面", color = PlayerColors.Accent)
                    }
                    TextButton(
                        onClick = {
                            scope.launch {
                                busy = "cover"
                                withContext(Dispatchers.IO) { CoverCache.clear(context) }
                                reload += 1
                                busy = null
                            }
                        },
                        enabled = busy == null,
                    ) {
                        Text(
                            if (busy == "cover") "清理中…" else "清理封面",
                            color = PlayerColors.Danger,
                        )
                    }
                }

                SectionDivider()

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("缓存视频片段", color = PlayerColors.Text, fontSize = 14.sp)
                        Text(
                            "复用已加载的 HLS 分片，减少重复下载。",
                            color = PlayerColors.Faint,
                            fontSize = 11.5.sp,
                        )
                    }
                    Switch(
                        checked = config.videoEnabled,
                        onCheckedChange = { config = config.copy(videoEnabled = it) },
                    )
                }
                SizeChips(
                    label = "视频缓存上限",
                    sizes = VIDEO_CACHE_SIZES,
                    selected = config.videoMaxBytes,
                    onSelect = { config = config.copy(videoMaxBytes = it) },
                )
                UsageText(
                    usage = videoUsage,
                    maxBytes = config.videoMaxBytes,
                    unit = "项",
                    enabled = config.videoEnabled,
                )
                Text(
                    text = "只缓存播放过的片段，不保证完整离线；解析页面仍需网络。",
                    color = PlayerColors.Faint,
                    fontSize = 11.sp,
                )
                TextButton(
                    onClick = {
                        scope.launch {
                            busy = "video"
                            withContext(Dispatchers.IO) { MediaCache.clear(context) }
                            reload += 1
                            busy = null
                        }
                    },
                    enabled = busy == null,
                ) {
                    Text(
                        if (busy == "video") "清理中…" else "清理视频",
                        color = PlayerColors.Danger,
                    )
                }

                SectionDivider()

                Text(
                    text = "存储位置（封面 + 视频）",
                    style = MaterialTheme.typography.labelLarge,
                    color = PlayerColors.Text,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = config.location == CACHE_LOCATION_INTERNAL,
                        onClick = { config = config.copy(location = CACHE_LOCATION_INTERNAL) },
                        label = { Text("内部存储") },
                        colors = selectedChipColors(),
                    )
                    FilterChip(
                        selected = config.location == CACHE_LOCATION_EXTERNAL,
                        onClick = { config = config.copy(location = CACHE_LOCATION_EXTERNAL) },
                        label = { Text("外部存储") },
                        colors = selectedChipColors(),
                    )
                }
                Text(
                    text = "外部存储使用 App 专属缓存目录；卸载 App 时系统会一并清理。更换位置会清空旧位置的缓存。",
                    color = PlayerColors.Faint,
                    fontSize = 11.sp,
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(
                        onClick = {
                            val previous = store.load()
                            store.save(config)
                            CoverCache.onConfigChanged(context, previous.location)
                            MediaCache.onConfigChanged(context, previous.location)
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
                    Text(
                        text = "保存后立即生效",
                        color = PlayerColors.Faint,
                        fontSize = 11.sp,
                    )
                }
            }
        }
    }
}

