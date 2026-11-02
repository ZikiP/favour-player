package com.ziki.favourplayer

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private data class ActressStat(val key: String, val name: String, val count: Int)

@Composable
fun LibraryScreen(viewModel: LibraryViewModel, onPlay: (String) -> Unit) {
    val state = viewModel.state
    var showManual by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }

    var query by rememberSaveable { mutableStateOf("") }
    var tab by rememberSaveable { mutableStateOf("library") }
    var actressFilter by rememberSaveable { mutableStateOf<String?>(null) }
    var detail by remember { mutableStateOf<LibraryItem?>(null) }
    var showRules by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val ruleStore = remember { RuleStore(context) }

    val items = state.snapshot?.items.orEmpty()
    val actressStats = remember(items) { buildActressStats(items) }
    val favoriteCount = remember(items) { items.count { it.favorite } }
    val filtered = remember(items, query, tab, actressFilter) {
        filterItems(items, query, tab, actressFilter)
    }
    val serverError = state.serverError

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(PlayerColors.Bg)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        val wide = maxWidth > 700.dp
        val horizontalPadding = if (wide) 24.dp else 14.dp

        Column(Modifier.fillMaxSize()) {
            LibraryTopBar(
                state = state,
                onSettings = { showSettings = true },
                onManual = { showManual = true },
            )
            if (serverError != null) {
                ServerBanner(
                    message = serverError,
                    onSettings = { showSettings = true },
                    horizontalPadding = horizontalPadding,
                )
            }
            SearchField(
                query = query,
                onQueryChange = { query = it },
                horizontalPadding = horizontalPadding,
            )
            ViewTabs(
                tab = tab,
                libraryCount = items.size,
                actressCount = actressStats.size,
                favoriteCount = favoriteCount,
                onTab = {
                    tab = it
                    actressFilter = null
                    query = ""
                },
                horizontalPadding = horizontalPadding,
            )
            ActressFilterRow(
                actressFilter = actressFilter,
                name = actressStats.firstOrNull { it.key == actressFilter }?.name,
                onClear = { actressFilter = null },
                horizontalPadding = horizontalPadding,
            )
            ListHeading(
                title = when {
                    query.isNotBlank() -> "搜索结果"
                    actressFilter != null ->
                        "演员 · ${actressStats.firstOrNull { it.key == actressFilter }?.name ?: ""}"
                    tab == "favorites" -> "最爱"
                    else -> "全部收藏"
                },
                count = if (tab == "actresses") {
                    actressStats.count { fuzzyMatch(it.name, query) }
                } else {
                    filtered.size
                },
                suffix = if (tab == "actresses") "位" else "条",
                horizontalPadding = horizontalPadding,
            )

            when {
                tab == "actresses" -> {
                    val visible = actressStats.filter { fuzzyMatch(it.name, query) }
                    if (visible.isEmpty()) {
                        EmptyState(
                            modifier = Modifier.weight(1f),
                            title = if (query.isBlank()) "还没有演员信息" else "没有匹配的演员",
                            message = when {
                                items.isEmpty() -> "等待插件推送后会自动汇总。"
                                query.isBlank() -> "收藏带演员字段的作品后会自动汇总到这里。"
                                else -> "换个关键词试试。"
                            },
                            actionLabel = when {
                                items.isEmpty() -> "查看接收设置"
                                query.isNotBlank() -> "清除搜索"
                                else -> null
                            },
                            onAction = when {
                                items.isEmpty() -> ({ showSettings = true })
                                query.isNotBlank() -> ({ query = "" })
                                else -> null
                            },
                        )
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(150.dp),
                            state = rememberLazyGridState(),
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(
                                horizontal = horizontalPadding,
                                vertical = 4.dp,
                            ),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            items(visible, key = { it.key }) { stat ->
                                ActressCard(
                                    name = stat.name,
                                    count = stat.count,
                                    onClick = {
                                        tab = "library"
                                        actressFilter = stat.key
                                        query = ""
                                    },
                                )
                            }
                        }
                    }
                }

                filtered.isEmpty() -> EmptyState(
                    modifier = Modifier.weight(1f),
                    title = when {
                        items.isEmpty() -> "等待插件推送"
                        tab == "favorites" -> "还没有最爱的作品"
                        else -> "没有匹配的收藏"
                    },
                    message = when {
                        items.isEmpty() -> "在 Favour 扩展 → 手机同步里，填入 App 设置显示的接收地址和令牌，然后点立即同步。"
                        tab == "favorites" -> "在桌面端卡片上点爱心加入最爱。"
                        else -> "换个关键词或清除筛选再试试。"
                    },
                    actionLabel = when {
                        items.isEmpty() -> "查看接收设置"
                        tab == "favorites" -> "去收藏"
                        else -> "清除筛选"
                    },
                    onAction = when {
                        items.isEmpty() -> ({ showSettings = true })
                        tab == "favorites" -> ({ tab = "library" })
                        else -> ({ query = ""; actressFilter = null })
                    },
                )

                else -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(150.dp),
                    state = rememberLazyGridState(),
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(
                        horizontal = horizontalPadding,
                        vertical = 4.dp,
                    ),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(filtered, key = { it.id }) { item ->
                        ItemCard(
                            item = item,
                            onOpen = { detail = item },
                            onPlay = { onPlay(item.url) },
                        )
                    }
                }
            }
        }
    }

    detail?.let { item ->
        ItemDetailDialog(
            item = item,
            onDismiss = { detail = null },
            onPlay = {
                detail = null
                onPlay(item.url)
            },
        )
    }
    if (showSettings) {
        ReceiveSettingsDialog(
            state = state,
            onSave = viewModel::saveReceiveConfig,
            onRegenerateToken = viewModel::regenerateToken,
            onRefreshAddresses = viewModel::refreshAddresses,
            onRules = {
                showSettings = false
                showRules = true
            },
            onDismiss = { showSettings = false },
        )
    }
    if (showRules) {
        RulesDialog(
            userJson = ruleStore.userJson(),
            defaultJson = ruleStore.defaultExampleJson(),
            onSave = { ruleStore.saveUserRules(it) },
            onReset = { ruleStore.clearUserRules() },
            onDismiss = { showRules = false },
        )
    }
    if (showManual) {
        ManualPlayDialog(
            onPlay = { showManual = false; onPlay(it) },
            onDismiss = { showManual = false },
        )
    }
}

@Composable
private fun LibraryTopBar(
    state: LibraryUiState,
    onSettings: () -> Unit,
    onManual: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BrandMark(34.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = "Favour",
                color = PlayerColors.Text,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            )
            Text(
                text = statusText(state),
                color = PlayerColors.Faint,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = onManual) {
            Icon(Icons.Default.PlayArrow, contentDescription = "手动播放", tint = PlayerColors.Muted)
        }
        IconButton(onClick = onSettings) {
            Icon(Icons.Default.Settings, contentDescription = "接收设置", tint = PlayerColors.Muted)
        }
    }
}

@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    horizontalPadding: androidx.compose.ui.unit.Dp,
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = horizontalPadding, vertical = 6.dp),
        placeholder = { Text("搜索标题、编号、演员…", color = PlayerColors.Faint) },
        leadingIcon = {
            Icon(Icons.Default.Search, contentDescription = null, tint = PlayerColors.Faint)
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Default.Close, contentDescription = "清除", tint = PlayerColors.Muted)
                }
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = PlayerColors.Text,
            unfocusedTextColor = PlayerColors.Text,
            focusedContainerColor = PlayerColors.Panel,
            unfocusedContainerColor = PlayerColors.Panel,
            cursorColor = PlayerColors.Accent,
            focusedBorderColor = PlayerColors.Accent,
            unfocusedBorderColor = PlayerColors.Border,
            focusedPlaceholderColor = PlayerColors.Faint,
            unfocusedPlaceholderColor = PlayerColors.Faint,
        ),
    )
}

@Composable
private fun ViewTabs(
    tab: String,
    libraryCount: Int,
    actressCount: Int,
    favoriteCount: Int,
    onTab: (String) -> Unit,
    horizontalPadding: androidx.compose.ui.unit.Dp,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = horizontalPadding, vertical = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(PlayerColors.PanelSoft)
            .padding(3.dp),
    ) {
        TabButton("收藏", libraryCount, tab == "library", { onTab("library") }, Modifier.weight(1f))
        TabButton("演员", actressCount, tab == "actresses", { onTab("actresses") }, Modifier.weight(1f))
        TabButton("最爱", favoriteCount, tab == "favorites", { onTab("favorites") }, Modifier.weight(1f))
    }
}

@Composable
private fun TabButton(
    label: String,
    count: Int,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .height(36.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(if (active) PlayerColors.Panel else Color.Transparent)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = label,
                color = if (active) PlayerColors.Text else PlayerColors.Muted,
                fontSize = 13.5.sp,
                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
            )
            Spacer(Modifier.width(5.dp))
            Text(
                text = count.toString(),
                color = if (active) PlayerColors.Accent else PlayerColors.Faint,
                fontSize = 11.sp,
            )
        }
    }
}

@Composable
private fun ActressFilterRow(
    actressFilter: String?,
    name: String?,
    onClear: () -> Unit,
    horizontalPadding: androidx.compose.ui.unit.Dp,
) {
    if (actressFilter == null) return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = horizontalPadding, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(PlayerColors.AccentSoft)
                .clickable(onClick = onClear)
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "演员 · ${name ?: actressFilter}",
                color = PlayerColors.Accent,
                fontSize = 12.sp,
            )
            Spacer(Modifier.width(6.dp))
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "清除演员筛选",
                tint = PlayerColors.Accent,
                modifier = Modifier.size(14.dp),
            )
        }
    }
}

@Composable
private fun ListHeading(
    title: String,
    count: Int,
    suffix: String,
    horizontalPadding: androidx.compose.ui.unit.Dp,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = horizontalPadding, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            color = PlayerColors.Text,
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = "$count $suffix",
            color = PlayerColors.Faint,
            fontSize = 12.5.sp,
        )
    }
}

@Composable
private fun ServerBanner(
    message: String,
    onSettings: () -> Unit,
    horizontalPadding: androidx.compose.ui.unit.Dp,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = horizontalPadding, vertical = 2.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(PlayerColors.Danger.copy(alpha = 0.12f))
            .padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "接收服务：$message",
            color = PlayerColors.Danger,
            fontSize = 12.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onSettings) {
            Text("设置", color = PlayerColors.Danger)
        }
    }
}

@Composable
private fun EmptyState(
    modifier: Modifier = Modifier,
    title: String,
    message: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            BrandMark(44.dp)
            Spacer(Modifier.height(14.dp))
            Text(
                text = title,
                color = PlayerColors.Text,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = message,
                color = PlayerColors.Muted,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
            )
            if (actionLabel != null && onAction != null) {
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = onAction,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PlayerColors.Accent,
                        contentColor = PlayerColors.AccentInk,
                    ),
                ) {
                    Text(actionLabel)
                }
            }
        }
    }
}

private fun statusText(state: LibraryUiState): String = when {
    state.serverError != null -> "接收服务异常"
    !state.enabled -> "接收已关闭"
    state.serverRunning && state.lastReceivedAt != null ->
        "接收中 · ${formatSyncTime(state.lastReceivedAt)}"
    state.serverRunning -> "接收中 · 端口 ${state.port}"
    state.lastReceivedAt != null -> "已停止 · ${formatSyncTime(state.lastReceivedAt)}"
    else -> "等待插件推送"
}

private fun formatSyncTime(timestamp: Long?): String {
    if (timestamp == null || timestamp <= 0L) return "未知"
    return SimpleDateFormat("MM-dd HH:mm", Locale.CHINA).format(Date(timestamp))
}

private fun buildActressStats(items: List<LibraryItem>): List<ActressStat> {
    val map = linkedMapOf<String, ActressStat>()
    for (item in items) {
        for (name in item.actressNames()) {
            val key = name.lowercase()
            val existing = map[key]
            map[key] = ActressStat(
                key = key,
                name = existing?.name ?: name,
                count = (existing?.count ?: 0) + 1,
            )
        }
    }
    return map.values.sortedWith(
        compareByDescending<ActressStat> { it.count }.thenBy { it.name },
    )
}

private fun filterItems(
    items: List<LibraryItem>,
    query: String,
    tab: String,
    actressFilter: String?,
): List<LibraryItem> {
    var list = items
    if (tab == "favorites") list = list.filter { it.favorite }
    if (actressFilter != null) {
        list = list.filter { item ->
            item.actressNames().any { it.lowercase() == actressFilter }
        }
    }
    if (query.isNotBlank()) {
        list = list.filter { matchesQuery(it, query) }
    }
    return list.sortedByDescending { it.createdAt }
}

private fun matchesQuery(item: LibraryItem, rawQuery: String): Boolean {
    val query = rawQuery.trim().lowercase()
    if (query.isEmpty()) return true
    val extras = item.extras.joinToString(" ") { "${it.label} ${it.value}" }
    val haystack = "${item.title}\n${item.url}\n${item.tags.joinToString(" ")}\n$extras"
        .lowercase()
    return haystack.contains(query)
}

private fun fuzzyMatch(text: String, rawQuery: String): Boolean {
    val source = text.lowercase()
    val needle = rawQuery.replace(" ", "").lowercase()
    if (needle.isEmpty()) return true
    if (source.contains(needle)) return true
    var index = 0
    for (char in source) {
        if (char == needle[index]) index += 1
        if (index == needle.length) return true
    }
    return false
}
