package com.ziki.favourplayer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.VerticalAlignBottom
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SettingsScreen(
    state: LibraryUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onTest: (String, String) -> Unit,
    onSaveSync: (String, String) -> String?,
    onManualPlay: () -> Unit,
) {
    val context = LocalContext.current
    val cacheStore = remember { CacheStore(context) }
    val ruleStore = remember { RuleStore(context) }
    val playerPrefs = remember { PlayerPrefs(context) }
    val subtitleStyleStore = remember { SubtitleStyleStore(context) }
    var cache by remember { mutableStateOf(cacheStore.load()) }
    var playerSettings by remember { mutableStateOf(playerPrefs.load()) }
    var subtitleStyle by remember { mutableStateOf(subtitleStyleStore.load()) }
    var showSync by remember { mutableStateOf(false) }
    var showCache by remember { mutableStateOf(false) }
    var showRules by remember { mutableStateOf(false) }
    var showPlayback by remember { mutableStateOf(false) }
    var showControlPosition by remember { mutableStateOf(false) }
    var showSubtitleStyle by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(PlayerColors.Bg)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 28.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(11.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(PlayerColors.Panel)
                        .border(1.dp, PlayerColors.Border, CircleShape)
                        .clickable(onClick = onBack),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "返回",
                        tint = PlayerColors.Text,
                        modifier = Modifier.size(18.dp),
                    )
                }
                Column {
                    Text(
                        text = "设置",
                        color = PlayerColors.Text,
                        style = MaterialTheme.typography.displaySmall,
                    )
                    Text(
                        text = "Favour ${BuildConfig.VERSION_NAME} · 仅供个人使用",
                        color = PlayerColors.Muted,
                        fontSize = 10.5.sp,
                    )
                }
            }

            SyncCard(
                state = state,
                onRefresh = onRefresh,
                onTest = onTest,
                onEdit = { showSync = true },
                modifier = Modifier.padding(top = 18.dp),
            )

            GroupLabel("播放")
            GroupCard {
                SettingsRow(
                    icon = Icons.Rounded.Speed,
                    title = "默认倍速",
                    subtitle = "打开播放器时使用",
                    value = formatSpeed(playerSettings.defaultSpeed),
                    onClick = { showPlayback = true },
                )
                RowDivider()
                SettingsRow(
                    icon = Icons.Rounded.Timer,
                    title = "控制栏自动隐藏",
                    subtitle = "播放中无操作后",
                    value = formatSeconds(playerSettings.autoHideMs),
                    onClick = { showPlayback = true },
                )
                RowDivider()
                SettingsRow(
                    icon = Icons.Rounded.TouchApp,
                    title = "双击跳转",
                    subtitle = "播放画面左右两侧",
                    value = formatSeconds(playerSettings.doubleTapMs.toInt()),
                    onClick = { showPlayback = true },
                )
                RowDivider()
                SettingsRow(
                    icon = Icons.Rounded.Tune,
                    title = "画质增强",
                    subtitle = "帧率匹配 · 锐化强度",
                    value = qualitySummary(playerSettings),
                    onClick = { showPlayback = true },
                )
                RowDivider()
                SettingsRow(
                    icon = Icons.Rounded.VerticalAlignBottom,
                    title = "控件位置",
                    subtitle = "竖屏 / 横屏分别设置",
                    value = "${playerSettings.controlBottomPortrait} / ${playerSettings.controlBottomLandscape} dp",
                    onClick = { showControlPosition = true },
                )
                RowDivider()
                SettingsRow(
                    icon = Icons.Rounded.Subtitles,
                    title = "字幕样式",
                    subtitle = "字号、颜色、背景与时间偏移",
                    value = "${subtitleStyle.fontSize.toInt()} sp",
                    onClick = { showSubtitleStyle = true },
                )
            }

            GroupLabel("缓存与数据")
            GroupCard {
                SettingsRow(
                    icon = Icons.Rounded.Download,
                    title = "封面缓存",
                    subtitle = "离线封面 · ${locationLabel(cache.location)}",
                    value = formatBytes(cache.coverMaxBytes),
                    onClick = { showCache = true },
                )
                RowDivider()
                SettingsRow(
                    icon = Icons.Rounded.VideoLibrary,
                    title = "视频缓存",
                    subtitle = "看过的片段 · ${locationLabel(cache.location)}",
                    value = formatBytes(cache.videoMaxBytes),
                    onClick = { showCache = true },
                )
                RowDivider()
                SettingsRow(
                    icon = Icons.Rounded.Code,
                    title = "解析规则",
                    subtitle = "站点解析与雪碧图",
                    onClick = { showRules = true },
                )
            }

            GroupLabel("关于")
            GroupCard {
                SettingsRow(
                    icon = Icons.Rounded.PlayCircle,
                    title = "手动播放",
                    subtitle = "粘贴收藏页或直链",
                    onClick = onManualPlay,
                )
                RowDivider()
                SettingsRow(
                    icon = Icons.Rounded.Refresh,
                    title = "版本",
                    subtitle = "中间服务拉取 · debug",
                    value = BuildConfig.VERSION_NAME,
                )
            }

            Spacer(Modifier.height(18.dp))
            Text(
                text = "Favour · 私人资料库",
                color = PlayerColors.Faint,
                fontSize = 10.sp,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }
        if (showSync) {
            SyncSettingsPage(
                state = state,
                onSave = onSaveSync,
                onTest = onTest,
                onRules = {
                    showSync = false
                    showRules = true
                },
                onCache = {
                    showSync = false
                    showCache = true
                },
                onBack = { showSync = false },
            )
        } else if (showCache) {
            CacheSettingsPage(
                onBack = {
                    showCache = false
                    cache = cacheStore.load()
                },
            )
        } else if (showRules) {
            RulesPage(
                userJson = ruleStore.userJson(),
                defaultJson = ruleStore.defaultExampleJson(),
                onSave = { ruleStore.saveUserRules(it) },
                onReset = { ruleStore.clearUserRules() },
                onBack = { showRules = false },
            )
        } else if (showPlayback) {
            PlaybackSettingsPage(
                initial = playerSettings,
                onSave = {
                    playerPrefs.save(it)
                    playerSettings = it
                },
                onBack = { showPlayback = false },
            )
        } else if (showControlPosition) {
            ControlPositionPage(
                initial = playerSettings,
                onSave = {
                    playerPrefs.save(it)
                    playerSettings = it
                },
                onBack = { showControlPosition = false },
            )
        } else if (showSubtitleStyle) {
            SubtitleSettingsPage(
                initial = subtitleStyle,
                onSave = {
                    subtitleStyleStore.save(it)
                    subtitleStyle = it
                },
                onBack = { showSubtitleStyle = false },
            )
        }
    }
}

@Composable
private fun SyncCard(
    state: LibraryUiState,
    onRefresh: () -> Unit,
    onTest: (String, String) -> Unit,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val configured = state.pullUrl.isNotBlank() && state.pullToken.isNotBlank()
    val statusColor = when {
        state.pullError != null -> PlayerColors.Danger
        state.pulling -> PlayerColors.Accent
        configured && state.snapshot != null -> PlayerColors.Success
        else -> PlayerColors.Faint
    }
    val statusText = when {
        !configured -> "等待配置中间服务"
        state.pulling -> "正在拉取…"
        state.pullError != null && state.snapshot != null -> "拉取失败 · 使用缓存"
        state.pullError != null -> "拉取失败"
        state.lastReceivedAt != null -> "已同步 · ${formatSyncTime(state.lastReceivedAt)}"
        else -> "等待拉取"
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(PlayerColors.Panel)
            .border(1.dp, PlayerColors.Border, RoundedCornerShape(18.dp))
            .padding(15.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(statusColor),
            )
            Spacer(Modifier.width(7.dp))
            Text(
                text = statusText,
                color = PlayerColors.Text,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.weight(1f))
            Button(
                onClick = onRefresh,
                enabled = !state.pulling,
                modifier = Modifier.height(33.dp),
                shape = RoundedCornerShape(9.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 11.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = PlayerColors.Accent,
                    contentColor = PlayerColors.AccentInk,
                ),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(13.dp),
                )
                Spacer(Modifier.width(5.dp))
                Text(
                    text = if (state.pulling) "同步中…" else "立即同步",
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        Text(
            text = "${state.snapshot?.items?.size ?: 0} 部收藏 · 中间服务",
            color = PlayerColors.Muted,
            fontSize = 10.5.sp,
            modifier = Modifier.padding(top = 6.dp),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp)
                .border(1.dp, PlayerColors.Border, RoundedCornerShape(10.dp))
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = maskUrl(state.pullUrl),
                color = PlayerColors.Muted,
                fontFamily = FontFamily.Monospace,
                fontSize = 9.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onEdit, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp)) {
                Text("编辑", color = PlayerColors.Accent, fontSize = 10.5.sp)
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(top = 4.dp),
        ) {
            TextButton(
                onClick = { onTest(state.pullUrl, state.pullToken) },
                enabled = configured,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp, vertical = 2.dp),
            ) {
                Text(
                    text = if (state.pullTestMessage == null) "测试连接" else "重新测试",
                    color = PlayerColors.Accent,
                    fontSize = 10.5.sp,
                )
            }
            state.pullTestMessage?.let { message ->
                Spacer(Modifier.width(6.dp))
                Text(
                    text = message,
                    color = if (state.pullTestOk) PlayerColors.Muted else PlayerColors.Danger,
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun GroupLabel(text: String) {
    Text(
        text = text,
        color = PlayerColors.Faint,
        fontSize = 9.5.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.4.sp,
        modifier = Modifier.padding(start = 4.dp, top = 22.dp, bottom = 8.dp),
    )
}

@Composable
private fun GroupCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(PlayerColors.Panel)
            .border(1.dp, PlayerColors.Border, RoundedCornerShape(16.dp)),
        content = content,
    )
}

@Composable
private fun RowDivider() {
    Box(
        Modifier
            .fillMaxWidth()
            .height(1.dp)
            .padding(horizontal = 13.dp)
            .background(PlayerColors.Border),
    )
}

@Composable
private fun SettingsRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    value: String? = null,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 13.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        Box(
            modifier = Modifier
                .size(29.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(PlayerColors.PanelSoft),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = PlayerColors.Muted,
                modifier = Modifier.size(15.dp),
            )
        }
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                color = PlayerColors.Text,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    color = PlayerColors.Muted,
                    fontSize = 9.8.sp,
                    modifier = Modifier.padding(top = 1.dp),
                )
            }
        }
        if (value != null) {
            Text(
                text = value,
                color = PlayerColors.Muted,
                fontSize = 10.5.sp,
                maxLines = 1,
            )
        }
        if (onClick != null) {
            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = PlayerColors.Faint,
                modifier = Modifier.size(15.dp),
            )
        }
    }
}

private fun maskUrl(url: String): String {
    if (url.isBlank()) return "未配置中间服务"
    return url.replace(Regex("""//[^/]+"""), "//•••••••")
}

private fun locationLabel(location: String): String =
    if (location == CACHE_LOCATION_EXTERNAL) "外部存储" else "内部存储"

private fun formatSeconds(ms: Int): String {
    val value = ms / 1000f
    return if (value % 1f == 0f) "${value.toInt()} 秒" else String.format(Locale.US, "%.1f 秒", value)
}

private fun formatSyncTime(timestamp: Long?): String {
    if (timestamp == null || timestamp <= 0L) return "未知"
    return SimpleDateFormat("MM-dd HH:mm", Locale.CHINA).format(Date(timestamp))
}

private fun qualitySummary(settings: PlayerSettings): String {
    val parts = buildList {
        if (settings.frameRateMatch) add("帧率匹配")
        if (settings.sharpen.enabled) add("${settings.sharpen.label}锐化")
    }
    return parts.joinToString(" · ").ifBlank { "关闭" }
}
