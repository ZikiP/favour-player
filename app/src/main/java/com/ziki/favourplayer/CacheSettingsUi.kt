package com.ziki.favourplayer

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal val COVER_CACHE_SIZES = listOf(
    128L * 1024 * 1024 to "128 MB",
    256L * 1024 * 1024 to "256 MB",
    512L * 1024 * 1024 to "512 MB",
    1L * 1024 * 1024 * 1024 to "1 GB",
    2L * 1024 * 1024 * 1024 to "2 GB",
)

internal val VIDEO_CACHE_SIZES = listOf(
    512L * 1024 * 1024 to "512 MB",
    1L * 1024 * 1024 * 1024 to "1 GB",
    2L * 1024 * 1024 * 1024 to "2 GB",
    4L * 1024 * 1024 * 1024 to "4 GB",
    8L * 1024 * 1024 * 1024 to "8 GB",
)

@Composable
internal fun SizeChips(
    label: String,
    sizes: List<Pair<Long, String>>,
    selected: Long,
    onSelect: (Long) -> Unit,
) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelLarge,
        color = PlayerColors.Text,
    )
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        sizes.forEach { (bytes, text) ->
            FilterChip(
                selected = selected == bytes,
                onClick = { onSelect(bytes) },
                label = { Text(text) },
                colors = selectedChipColors(),
            )
        }
    }
}

@Composable
internal fun UsageText(
    usage: CacheUsage?,
    maxBytes: Long,
    unit: String,
    enabled: Boolean,
) {
    val used = usage?.usedBytes ?: 0L
    val count = usage?.entries ?: 0
    Text(
        text = if (!enabled) {
            "已关闭 · 不再写入新缓存"
        } else {
            "已用 ${formatBytes(used)} / 上限 ${formatBytes(maxBytes)} · $count $unit"
        },
        color = PlayerColors.Muted,
        fontSize = 12.5.sp,
    )
    usage?.path?.let { path ->
        Text(
            text = path,
            color = PlayerColors.Faint,
            fontFamily = FontFamily.Monospace,
            fontSize = 10.5.sp,
        )
    }
}

@Composable
internal fun SectionDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(PlayerColors.Border),
    )
}

@Composable
internal fun selectedChipColors() = FilterChipDefaults.filterChipColors(
    selectedContainerColor = PlayerColors.Accent,
    selectedLabelColor = PlayerColors.AccentInk,
)

internal fun formatBytes(bytes: Long): String {
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

internal fun formatCacheTime(timestamp: Long): String =
    SimpleDateFormat("MM-dd HH:mm", Locale.CHINA).format(Date(timestamp))
