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
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
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

    if (state.needsSetup) {
        SetupScreen(
            state = state,
            onSave = viewModel::saveConfig,
            onManualPlay = { showManual = true },
        )
        if (showManual) {
            ManualPlayDialog(
                onPlay = { showManual = false; onPlay(it) },
                onDismiss = { showManual = false },
            )
        }
        return
    }

    var query by rememberSaveable { mutableStateOf("") }
    var tab by rememberSaveable { mutableStateOf("library") }
    var actressFilter by rememberSaveable { mutableStateOf<String?>(null) }
    var detail by remember { mutableStateOf<LibraryItem?>(null) }

    val items = state.snapshot?.items.orEmpty()
    val actressStats = remember(items) { buildActressStats(items) }
    val favoriteCount = remember(items) { items.count { it.favorite } }
    val filtered = remember(items, query, tab, actressFilter) {
        filterItems(items, query, tab, actressFilter)
    }
    val error = state.error

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
                onRefresh = viewModel::refresh,
                onSettings = { showSettings = true },
                onManual = { showManual = true },
            )
            if (error != null && items.isNotEmpty()) {
                OfflineBanner(
                    message = error,
                    offline = state.offline,
                    onRetry = viewModel::refresh,
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
                state.snapshot == null && state.loading -> LoadingState(Modifier.weight(1f))

                tab == "actresses" -> {
                    val visible = actressStats.filter { fuzzyMatch(it.name, query) }
                    if (visible.isEmpty()) {
                        EmptyState(
                            modifier = Modifier.weight(1f),
                            title = if (query.isBlank()) "还没有演员信息" else "没有匹配的演员",
                            message = if (query.isBlank()) {
                                "收藏带演员字段的作品后会自动汇总到这里。"
                            } else {
                                "换个关键词试试。"
                            },
                            actionLabel = if (query.isNotBlank()) "清除搜索" else null,
                            onAction = if (query.isNotBlank()) ({ query = "" }) else null,
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
                        items.isEmpty() && error != null -> "同步失败"
                        items.isEmpty() -> "还没有收藏"
                        tab == "favorites" -> "还没有最爱的作品"
                        else -> "没有匹配的收藏"
                    },
                    message = when {
                        items.isEmpty() && error != null -> error
                        items.isEmpty() -> "在桌面端收藏后，点右上角同步。"
                        tab == "favorites" -> "在桌面端卡片上点爱心加入最爱。"
                        else -> "换个关键词或清除筛选再试试。"
                    },
                    actionLabel = when {
                        items.isEmpty() && error != null -> "重试"
                        items.isEmpty() -> "立即同步"
                        tab == "favorites" -> "去收藏"
                        else -> "清除筛选"
                    },
                    onAction = when {
                        items.isEmpty() && error != null -> viewModel::refresh
                        items.isEmpty() -> viewModel::refresh
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
        SettingsDialog(
            state = state,
            onSave = { server, token ->
                showSettings = false
                viewModel.saveConfig(server, token)
            },
            onClear = {
                showSettings = false
                viewModel.clearConfig()
            },
            onDismiss = { showSettings = false },
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
    onRefresh: () -> Unit,
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
        if (state.loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
                color = PlayerColors.Accent,
            )
            Spacer(Modifier.width(12.dp))
        } else {
            IconButton(onClick = onRefresh) {
                Icon(Icons.Default.Refresh, contentDescription = "同步", tint = PlayerColors.Muted)
            }
        }
        IconButton(onClick = onManual) {
            Icon(Icons.Default.PlayArrow, contentDescription = "手动播放", tint = PlayerColors.Muted)
        }
        IconButton(onClick = onSettings) {
            Icon(Icons.Default.Settings, contentDescription = "同步设置", tint = PlayerColors.Muted)
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
private fun OfflineBanner(
    message: String,
    offline: Boolean,
    onRetry: () -> Unit,
    horizontalPadding: androidx.compose.ui.unit.Dp,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = horizontalPadding, vertical = 2.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(PlayerColors.AccentSoft)
            .padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = if (offline) "离线数据 · $message" else "同步失败 · $message",
            color = PlayerColors.Accent,
            fontSize = 12.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onRetry) {
            Text("重试", color = PlayerColors.Accent)
        }
    }
}

@Composable
private fun LoadingState(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = PlayerColors.Accent)
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
    state.loading -> "正在同步…"
    state.error != null && state.offline -> "离线 · ${formatSyncTime(state.lastSyncedAt)}"
    state.error != null -> "同步失败"
    state.lastSyncedAt != null -> "同步于 ${formatSyncTime(state.lastSyncedAt)}"
    else -> "未同步"
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
