package com.ziki.favourplayer

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items as rowItems
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class LibraryTab { Library, Collections, Actresses, Favorites }
private enum class LibrarySort { Recent, Oldest, Title, LastOpened }

private data class ActressStat(
    val key: String,
    val name: String,
    val count: Int,
    val avatar: String,
    val favorite: Boolean,
    val order: Int,
)

private data class SeriesStat(
    val key: String,
    val label: String,
    val count: Int,
    val cover: String,
    val latestAt: Long,
)

private data class TagStat(
    val key: String,
    val label: String,
    val count: Int,
)

@Composable
fun LibraryScreen(
    viewModel: LibraryViewModel,
    onPlay: (String, Boolean) -> Unit,
) {
    val state = viewModel.state
    val context = LocalContext.current
    val coverRevision = CoverCache.revision
    val coverLoader = remember(coverRevision) { CoverCache.imageLoader(context) }

    var tabName by rememberSaveable { mutableStateOf(LibraryTab.Library.name) }
    var sortName by rememberSaveable { mutableStateOf(LibrarySort.Recent.name) }
    var query by rememberSaveable { mutableStateOf("") }
    var actressFilter by rememberSaveable { mutableStateOf<String?>(null) }
    var seriesFilter by rememberSaveable { mutableStateOf<String?>(null) }
    var genreFilter by rememberSaveable { mutableStateOf<String?>(null) }
    var onlyWithActress by rememberSaveable { mutableStateOf(false) }
    var unwatchedOnly by rememberSaveable { mutableStateOf(false) }
    var showFilters by rememberSaveable { mutableStateOf(false) }
    var detailId by rememberSaveable { mutableStateOf<String?>(null) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var showManual by rememberSaveable { mutableStateOf(false) }
    // "actor:<key>" 或 "series:<key>"，在当前 Tab 内打开二级页
    var worksPage by rememberSaveable { mutableStateOf<String?>(null) }
    // 从详情页跳进作品页时记住来源，返回时回到那个详情而不是列表
    var worksReturnDetailId by rememberSaveable { mutableStateOf<String?>(null) }

    val libraryGrid = rememberLazyGridState()
    val actressGrid = rememberLazyGridState()
    val collectionsGrid = rememberLazyGridState()
    val searchFocus = remember { FocusRequester() }

    val tab = LibraryTab.entries.firstOrNull { it.name == tabName } ?: LibraryTab.Library
    val sort = LibrarySort.entries.firstOrNull { it.name == sortName } ?: LibrarySort.Recent
    val items = state.snapshot?.items.orEmpty()
    val actressStats = remember(items, state.snapshot?.actressAvatars, state.snapshot?.actressMeta) {
        buildActressStats(
            items,
            state.snapshot?.actressAvatars.orEmpty(),
            state.snapshot?.actressMeta.orEmpty(),
        )
    }
    val seriesStats = remember(items) { buildSeriesStats(items) }
    val tagStats = remember(items) { buildTagStats(items) }
    val favoriteCount = remember(items) { items.count { it.favorite } }
    val detailItem = detailId?.let { id -> items.firstOrNull { it.id == id } }
    val relatedWorks = remember(detailItem, items) {
        detailItem?.let { relatedFor(it, items) }.orEmpty()
    }
    val activeActressName = actressFilter?.let { key ->
        actressStats.firstOrNull { it.key == key }?.name
    }

    // 用宽高比判断，分屏和折叠屏展开时方向值不一定可靠
    val configuration = LocalConfiguration.current
    val landscape = configuration.screenWidthDp > configuration.screenHeightDp

    val filterActive = sort != LibrarySort.Recent ||
        onlyWithActress ||
        unwatchedOnly ||
        actressFilter != null ||
        seriesFilter != null ||
        genreFilter != null

    val selectTab: (LibraryTab) -> Unit = remember {
        { next ->
            tabName = next.name
            query = ""
            actressFilter = null
            seriesFilter = null
            genreFilter = null
            onlyWithActress = false
            unwatchedOnly = false
            worksPage = null
            worksReturnDetailId = null
        }
    }

    val worksActorKey = worksPage?.takeIf { it.startsWith("actor:") }?.removePrefix("actor:")
    val worksSeriesKey = worksPage?.takeIf { it.startsWith("series:") }?.removePrefix("series:")
    val worksActorStat = worksActorKey?.let { key -> actressStats.firstOrNull { it.key == key } }
    val worksSeriesStat = worksSeriesKey?.let { key -> seriesStats.firstOrNull { it.key == key } }
    val worksItems = remember(worksActorKey, worksSeriesKey, items) {
        when {
            worksActorKey != null -> items.filter { item ->
                item.actressNames().any { it.lowercase() == worksActorKey }
            }.sortedByDescending { it.createdAt }

            worksSeriesKey != null -> items.filter { item ->
                item.series().lowercase() == worksSeriesKey
            }.sortedByDescending { it.createdAt }

            else -> emptyList()
        }
    }

    val filtered = remember(
        items,
        tab,
        sort,
        query,
        actressFilter,
        seriesFilter,
        genreFilter,
        onlyWithActress,
        unwatchedOnly,
    ) {
        filterAndSort(
            items,
            query,
            tab,
            actressFilter,
            seriesFilter,
            genreFilter,
            onlyWithActress,
            unwatchedOnly,
            sort,
        )
    }
    val continueItems = remember(items, state.playback, tab, query, actressFilter, seriesFilter, genreFilter, onlyWithActress, unwatchedOnly) {
        if (tab != LibraryTab.Library ||
            query.isNotBlank() ||
            actressFilter != null ||
            seriesFilter != null ||
            genreFilter != null ||
            onlyWithActress ||
            unwatchedOnly
        ) {
            emptyList()
        } else {
            items.mapNotNull { item ->
                state.playback[item.url]?.let { progress -> item to progress }
            }.sortedByDescending { it.second.updatedAt }
                .take(8)
        }
    }

    LaunchedEffect(detailId, items) {
        if (detailId != null && items.none { it.id == detailId }) detailId = null
    }

    BackHandler(enabled = detailItem != null || showSettings) {
        if (detailItem != null) detailId = null else showSettings = false
    }

    val closeWorksPage: () -> Unit = {
        worksPage = null
        worksReturnDetailId?.let { detailId = it }
        worksReturnDetailId = null
    }

    when {
        detailItem != null -> key(detailItem.id) {
            DetailScreen(
            item = detailItem,
            avatars = state.snapshot?.actressAvatars.orEmpty(),
            progress = state.playback[detailItem.url],
            imageLoader = coverLoader,
            onBack = { detailId = null },
            onPlay = { fromStart -> onPlay(detailItem.url, fromStart) },
            onActorClick = { name ->
                worksReturnDetailId = detailItem.id
                worksPage = "actor:${name.lowercase()}"
                detailId = null
            },
            onSeriesClick = { series ->
                worksReturnDetailId = detailItem.id
                worksPage = "series:${series.lowercase()}"
                detailId = null
            },
            onTagClick = { tag ->
                tabName = LibraryTab.Library.name
                genreFilter = tag.lowercase()
                actressFilter = null
                seriesFilter = null
                query = ""
                onlyWithActress = false
                unwatchedOnly = false
                detailId = null
            },
            related = relatedWorks,
            onOpenRelated = { detailId = it.id },
            )
        }

        worksPage != null -> WorksPage(
            title = worksActorStat?.name
                ?: worksSeriesStat?.label
                ?: worksActorKey
                ?: worksSeriesKey
                ?: "",
            subtitle = buildString {
                append(if (worksActorKey != null) "演员" else "系列")
                append(" · ")
                append(worksItems.size)
                append(" 部作品")
            },
            items = worksItems,
            imageLoader = coverLoader,
            onBack = closeWorksPage,
            onOpen = { detailId = it.id },
            onPlay = { onPlay(it.url, false) },
        )

        showSettings -> SettingsScreen(
            state = state,
            onBack = { showSettings = false },
            onRefresh = { viewModel.pullNow() },
            onTest = viewModel::testPullConnection,
            onSaveSync = viewModel::saveSyncSettings,
            onManualPlay = { showManual = true },
        )

        else -> Row(
            modifier = Modifier
                .fillMaxSize()
                .background(PlayerColors.Bg)
                .windowInsetsPadding(WindowInsets.safeDrawing),
        ) {
            if (landscape) {
                FavourRail(
                    selected = tab,
                    onSelect = selectTab,
                    onSettings = { showSettings = true },
                )
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            ) {
                if (landscape) {
                    LandscapeHeader(
                        title = when (tab) {
                            LibraryTab.Library -> "收藏"
                            LibraryTab.Collections -> "合集"
                            LibraryTab.Actresses -> "演员"
                            LibraryTab.Favorites -> "最爱"
                        },
                        summary = when (tab) {
                            LibraryTab.Collections -> "${seriesStats.size} 个系列"
                            LibraryTab.Actresses -> "${actressStats.size} 位"
                            LibraryTab.Favorites -> "$favoriteCount 部"
                            LibraryTab.Library -> "${items.size} 部"
                        },
                        query = query,
                        onQueryChange = { query = it },
                        searchFocus = searchFocus,
                        refreshing = state.pulling,
                        showFilter = tab == LibraryTab.Library || tab == LibraryTab.Favorites,
                        filterActive = filterActive,
                        onFilter = { showFilters = true },
                        onRefresh = { viewModel.pullNow() },
                        onSettings = { showSettings = true },
                    )
                } else {
                    LibraryHeader(
                        subtitle = when (tab) {
                            LibraryTab.Collections -> "${seriesStats.size} 个系列 · ${syncStatusText(state)}"
                            LibraryTab.Actresses -> "${actressStats.size} 位演员 · ${syncStatusText(state)}"
                            LibraryTab.Favorites -> "$favoriteCount 部最爱 · ${syncStatusText(state)}"
                            LibraryTab.Library -> "${items.size} 部收藏 · ${syncStatusText(state)}"
                        },
                        refreshing = state.pulling,
                        onSearch = { searchFocus.requestFocus() },
                        onRefresh = { viewModel.pullNow() },
                        onSettings = { showSettings = true },
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Box(Modifier.weight(1f)) {
                            FavourSearchField(
                                value = query,
                                onValueChange = { query = it },
                                focusRequester = searchFocus,
                            )
                        }
                        if (tab == LibraryTab.Library || tab == LibraryTab.Favorites) {
                            FilterIcon(
                                active = filterActive,
                                onClick = { showFilters = true },
                            )
                        }
                    }
                }

                Spacer(Modifier.height(4.dp))

            when (tab) {
                LibraryTab.Collections -> CollectionsContent(
                    stats = seriesStats,
                    query = query,
                    gridState = collectionsGrid,
                    imageLoader = coverLoader,
                    modifier = Modifier.weight(1f),
                    onSeries = { stat -> worksPage = "series:${stat.key}" },
                    onClearSearch = { query = "" },
                    onSyncSettings = { showSettings = true },
                )

                LibraryTab.Actresses -> ActressesContent(
                    stats = actressStats,
                    query = query,
                    gridState = actressGrid,
                    imageLoader = coverLoader,
                    modifier = Modifier.weight(1f),
                    onActress = { stat -> worksPage = "actor:${stat.key}" },
                    onClearSearch = { query = "" },
                    onSyncSettings = { showSettings = true },
                )

                LibraryTab.Library,
                LibraryTab.Favorites,
                -> if (filtered.isEmpty()) {
                    val noData = items.isEmpty()
                    val noFavorites = tab == LibraryTab.Favorites && favoriteCount == 0
                    EmptyState(
                        modifier = Modifier.weight(1f),
                        title = when {
                            noData -> "等待从中间服务拉取"
                            noFavorites -> "还没有最爱的作品"
                            else -> "没有匹配的收藏"
                        },
                        message = when {
                            noData ->
                                "在设置里填写中间服务的地址和令牌；手机与中间服务在同一 Tailscale 网络时即可拉取。"
                            noFavorites -> "在桌面端卡片上点爱心加入最爱。"
                            else -> "换个关键词或清除筛选再试试。"
                        },
                        actionLabel = when {
                            noData -> "同步设置"
                            noFavorites -> "去收藏"
                            else -> "清除筛选"
                        },
                        onAction = {
                            when {
                                noData -> showSettings = true
                                noFavorites -> tabName = LibraryTab.Library.name
                                else -> {
                                    query = ""
                                    actressFilter = null
                                    seriesFilter = null
                                    genreFilter = null
                                    onlyWithActress = false
                                    unwatchedOnly = false
                                }
                            }
                        },
                    )
                } else {
                    LibraryGridContent(
                        items = filtered,
                        continueItems = continueItems,
                        imageLoader = coverLoader,
                        gridState = libraryGrid,
                        modifier = Modifier.weight(1f),
                        onOpen = { detailId = it.id },
                        onPlay = { onPlay(it.url, false) },
                    )
                }
            }

                if (!landscape) {
                    FavourBottomBar(
                        selected = tab,
                        onSelect = selectTab,
                    )
                }
            }
        }
    }

    if (showFilters) {
        FilterPage(
            sort = sort,
            onlyWithActress = onlyWithActress,
            unwatchedOnly = unwatchedOnly,
            genreFilter = genreFilter,
            tagStats = tagStats,
            actressFilter = actressFilter,
            actressName = activeActressName,
            seriesFilter = seriesFilter,
            seriesLabel = seriesFilter?.let { key ->
                seriesStats.firstOrNull { it.key == key }?.label
            },
            onSort = { sortName = it.name },
            onToggleActress = { onlyWithActress = !onlyWithActress },
            onToggleUnwatched = { unwatchedOnly = !unwatchedOnly },
            onTag = { genreFilter = it },
            onClearActress = { actressFilter = null },
            onClearSeries = { seriesFilter = null },
            onClearTag = { genreFilter = null },
            onReset = {
                sortName = LibrarySort.Recent.name
                onlyWithActress = false
                unwatchedOnly = false
                actressFilter = null
                seriesFilter = null
                genreFilter = null
            },
            onBack = { showFilters = false },
        )
    }

    if (showManual) {
        ManualPlayPage(
            onPlay = {
                showManual = false
                onPlay(it, false)
            },
            onBack = { showManual = false },
        )
    }
}

@Composable
private fun LibraryHeader(
    subtitle: String,
    refreshing: Boolean,
    onSearch: () -> Unit,
    onRefresh: () -> Unit,
    onSettings: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = "FAVOUR",
                color = PlayerColors.Accent,
                fontSize = 9.5.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.6.sp,
            )
            Text(
                text = subtitle,
                color = PlayerColors.Muted,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 5.dp),
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            modifier = Modifier.padding(bottom = 4.dp),
        ) {
            HeaderIcon(Icons.Rounded.Search, "搜索", onSearch)
            Box {
                HeaderIcon(Icons.Rounded.Refresh, "立即同步", onRefresh)
                if (refreshing) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .size(10.dp),
                        strokeWidth = 1.5.dp,
                        color = PlayerColors.Accent,
                    )
                } else if (subtitle.contains("已同步")) {
                    Box(
                        Modifier
                            .align(Alignment.TopEnd)
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(PlayerColors.Success),
                    )
                }
            }
            HeaderIcon(Icons.Rounded.Settings, "同步设置", onSettings)
        }
    }
}

@Composable
private fun HeaderIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(PlayerColors.Panel)
            .border(1.dp, PlayerColors.Border, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = PlayerColors.Text,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun FavourSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(PlayerColors.Panel)
            .border(1.dp, PlayerColors.Border, RoundedCornerShape(14.dp))
            .padding(horizontal = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Rounded.Search,
            contentDescription = null,
            tint = PlayerColors.Faint,
            modifier = Modifier.size(17.dp),
        )
        Spacer(Modifier.width(9.dp))
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) {
                Text(
                    text = "搜索标题、演员、编号",
                    color = PlayerColors.Faint,
                    fontSize = 12.5.sp,
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = TextStyle(
                    color = PlayerColors.Text,
                    fontSize = 12.5.sp,
                ),
                cursorBrush = SolidColor(PlayerColors.Accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {}),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
            )
        }
        if (value.isNotEmpty()) {
            Text(
                text = "清除",
                color = PlayerColors.Accent,
                fontSize = 10.5.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable { onValueChange("") }
                    .padding(4.dp),
            )
        }
    }
}

@Composable
private fun FilterIcon(active: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(PlayerColors.Panel)
            .border(1.dp, PlayerColors.Border, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Rounded.Tune,
            contentDescription = "筛选与排序",
            tint = if (active) PlayerColors.Accent else PlayerColors.Text,
            modifier = Modifier.size(18.dp),
        )
        if (active) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(9.dp)
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(PlayerColors.Accent),
            )
        }
    }
}

@Composable
private fun FavourChip(
    text: String,
    active: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .height(30.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(if (active) PlayerColors.Accent else PlayerColors.Panel)
            .border(
                1.dp,
                if (active) Color.Transparent else PlayerColors.Border,
                RoundedCornerShape(999.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 11.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = if (active) PlayerColors.AccentInk else PlayerColors.Muted,
            fontSize = 11.sp,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
        )
    }
}

@Composable
private fun ActiveFilterChip(
    label: String,
    onClear: () -> Unit,
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
            text = label,
            color = PlayerColors.Accent,
            fontSize = 11.sp,
            maxLines = 1,
        )
        Spacer(Modifier.width(7.dp))
        Text(
            text = "×",
            color = PlayerColors.Accent,
            fontSize = 13.sp,
        )
    }
}

@Composable
private fun LibraryGridContent(
    items: List<LibraryItem>,
    continueItems: List<Pair<LibraryItem, PlaybackProgress>>,
    imageLoader: coil.ImageLoader,
    gridState: LazyGridState,
    modifier: Modifier,
    onOpen: (LibraryItem) -> Unit,
    onPlay: (LibraryItem) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(150.dp),
        state = gridState,
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(11.dp),
        verticalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        if (continueItems.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                ContinueSection(
                    entries = continueItems,
                    imageLoader = imageLoader,
                    onPlay = { onPlay(it) },
                )
            }
        }
        gridItems(items, key = { it.id }) { item ->
            ItemCard(
                item = item,
                imageLoader = imageLoader,
                onClick = { onOpen(item) },
                onPlay = { onPlay(item) },
            )
        }
    }
}

@Composable
private fun ContinueSection(
    entries: List<Pair<LibraryItem, PlaybackProgress>>,
    imageLoader: coil.ImageLoader,
    onPlay: (LibraryItem) -> Unit,
) {
    Column(Modifier.padding(top = 8.dp, bottom = 8.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "继续观看",
                color = PlayerColors.Text,
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "${entries.size}",
                color = PlayerColors.Faint,
                fontSize = 11.sp,
            )
        }
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(11.dp),
            contentPadding = PaddingValues(end = 20.dp),
        ) {
            rowItems(entries, key = { it.first.id }) { (item, progress) ->
                ContinueCard(
                    item = item,
                    progress = progress,
                    imageLoader = imageLoader,
                    onClick = { onPlay(item) },
                )
            }
        }
    }
}

@Composable
private fun CollectionsContent(
    stats: List<SeriesStat>,
    query: String,
    gridState: LazyGridState,
    imageLoader: coil.ImageLoader,
    modifier: Modifier,
    onSeries: (SeriesStat) -> Unit,
    onClearSearch: () -> Unit,
    onSyncSettings: () -> Unit,
) {
    val visible = stats.filter { fuzzyMatch(it.label, query) }
    if (visible.isEmpty()) {
        EmptyState(
            modifier = modifier,
            title = if (query.isBlank()) "还没有系列信息" else "没有匹配的系列",
            message = when {
                query.isBlank() -> "作品带「系列」字段后会自动汇总到这里。"
                else -> "换个关键词试试。"
            },
            actionLabel = when {
                query.isBlank() -> "同步设置"
                else -> "清除搜索"
            },
            onAction = if (query.isBlank()) onSyncSettings else onClearSearch,
        )
        return
    }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(160.dp),
        state = gridState,
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(11.dp),
        verticalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        gridItems(visible, key = { it.key }) { stat ->
            SeriesCard(
                label = stat.label,
                count = stat.count,
                cover = stat.cover,
                imageLoader = imageLoader,
                onClick = { onSeries(stat) },
            )
        }
    }
}

@Composable
private fun ActressesContent(
    stats: List<ActressStat>,
    query: String,
    gridState: LazyGridState,
    imageLoader: coil.ImageLoader,
    modifier: Modifier,
    onActress: (ActressStat) -> Unit,
    onClearSearch: () -> Unit,
    onSyncSettings: () -> Unit,
) {
    val visible = stats.filter { fuzzyMatch(it.name, query) }
    if (visible.isEmpty()) {
        EmptyState(
            modifier = modifier,
            title = if (query.isBlank()) "还没有演员信息" else "没有匹配的演员",
            message = when {
                query.isBlank() -> "拉取到收藏后会自动汇总。"
                else -> "换个关键词试试。"
            },
            actionLabel = when {
                query.isBlank() -> "同步设置"
                else -> "清除搜索"
            },
            onAction = if (query.isBlank()) onSyncSettings else onClearSearch,
        )
        return
    }

    BoxWithConstraints(modifier) {
        // 按可用宽度算列数，卡片宽度稳定在 96-110dp；窄屏保底两列
        val gap = 10.dp
        val tile = 96.dp
        val innerWidth = maxWidth - 40.dp
        val columns = ((innerWidth + gap) / (tile + gap)).toInt().coerceAtLeast(2)
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            state = gridState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(gap),
            verticalArrangement = Arrangement.spacedBy(gap),
        ) {
            gridItems(visible, key = { it.key }) { stat ->
                ActressCard(
                    name = stat.name,
                    count = stat.count,
                    avatar = stat.avatar,
                    favorite = stat.favorite,
                    imageLoader = imageLoader,
                    onClick = { onActress(stat) },
                )
            }
        }
    }
}

@Composable
private fun FavourRail(
    selected: LibraryTab,
    onSelect: (LibraryTab) -> Unit,
    onSettings: () -> Unit,
) {
    Column(
        modifier = Modifier
            .width(74.dp)
            .fillMaxHeight()
            .background(PlayerColors.Panel.copy(alpha = 0.5f))
            .padding(vertical = 10.dp, horizontal = 7.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        RailItem("收藏", Icons.Rounded.GridView, selected == LibraryTab.Library) {
            onSelect(LibraryTab.Library)
        }
        RailItem("合集", Icons.Rounded.VideoLibrary, selected == LibraryTab.Collections) {
            onSelect(LibraryTab.Collections)
        }
        RailItem("演员", Icons.Rounded.Person, selected == LibraryTab.Actresses) {
            onSelect(LibraryTab.Actresses)
        }
        RailItem("最爱", Icons.Rounded.Favorite, selected == LibraryTab.Favorites) {
            onSelect(LibraryTab.Favorites)
        }
        Spacer(Modifier.weight(1f))
        RailItem("设置", Icons.Rounded.Settings, false, onSettings)
    }
}

@Composable
private fun RailItem(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    active: Boolean,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (active) PlayerColors.AccentSoft else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = 7.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (active) PlayerColors.Accent else PlayerColors.Faint,
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = label,
            color = if (active) PlayerColors.Accent else PlayerColors.Faint,
            fontSize = 9.sp,
            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
        )
    }
}

/** 横屏用：标题与搜索合并成一行，把纵向空间还给列表。 */
@Composable
private fun LandscapeHeader(
    title: String,
    summary: String,
    query: String,
    onQueryChange: (String) -> Unit,
    searchFocus: FocusRequester,
    refreshing: Boolean,
    showFilter: Boolean,
    filterActive: Boolean,
    onFilter: () -> Unit,
    onRefresh: () -> Unit,
    onSettings: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 14.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Column(Modifier.width(78.dp)) {
            Text(
                text = title,
                color = PlayerColors.Text,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
            Text(
                text = summary,
                color = PlayerColors.Muted,
                fontSize = 10.sp,
                maxLines = 1,
            )
        }
        Box(Modifier.weight(1f)) {
            FavourSearchField(
                value = query,
                onValueChange = onQueryChange,
                focusRequester = searchFocus,
            )
        }
        if (showFilter) {
            FilterIcon(active = filterActive, onClick = onFilter)
        }
        Box {
            HeaderIcon(Icons.Rounded.Refresh, "立即同步", onRefresh)
            if (refreshing) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(10.dp),
                    strokeWidth = 1.5.dp,
                    color = PlayerColors.Accent,
                )
            }
        }
        HeaderIcon(Icons.Rounded.Settings, "同步设置", onSettings)
    }
}

@Composable
private fun FavourBottomBar(
    selected: LibraryTab,
    onSelect: (LibraryTab) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(PlayerColors.Panel.copy(alpha = 0.96f))
            .border(1.dp, PlayerColors.Border.copy(alpha = 0.7f))
            .height(64.dp)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BottomItem(
            label = "收藏",
            icon = Icons.Rounded.GridView,
            active = selected == LibraryTab.Library,
            onClick = { onSelect(LibraryTab.Library) },
            modifier = Modifier.weight(1f),
        )
        BottomItem(
            label = "合集",
            icon = Icons.Rounded.VideoLibrary,
            active = selected == LibraryTab.Collections,
            onClick = { onSelect(LibraryTab.Collections) },
            modifier = Modifier.weight(1f),
        )
        BottomItem(
            label = "演员",
            icon = Icons.Rounded.Person,
            active = selected == LibraryTab.Actresses,
            onClick = { onSelect(LibraryTab.Actresses) },
            modifier = Modifier.weight(1f),
        )
        BottomItem(
            label = "最爱",
            icon = Icons.Rounded.Favorite,
            active = selected == LibraryTab.Favorites,
            onClick = { onSelect(LibraryTab.Favorites) },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun BottomItem(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (active) PlayerColors.Accent else PlayerColors.Faint,
            modifier = Modifier.size(21.dp),
        )
        Text(
            text = label,
            color = if (active) PlayerColors.Accent else PlayerColors.Faint,
            fontSize = 9.5.sp,
            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
        )
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
            modifier = Modifier.padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            BrandMark(46.dp)
            Spacer(Modifier.height(14.dp))
            Text(
                text = title,
                color = PlayerColors.Text,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(5.dp))
            Text(
                text = message,
                color = PlayerColors.Muted,
                fontSize = 12.5.sp,
                lineHeight = 19.sp,
                textAlign = TextAlign.Center,
            )
            if (actionLabel != null && onAction != null) {
                Spacer(Modifier.height(14.dp))
                Button(
                    onClick = onAction,
                    shape = RoundedCornerShape(11.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PlayerColors.Accent,
                        contentColor = PlayerColors.AccentInk,
                    ),
                ) {
                    Text(actionLabel, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

private fun syncStatusText(state: LibraryUiState): String {
    if (state.pullUrl.isBlank() || state.pullToken.isBlank()) return "等待配置中间服务"
    val syncedAt = state.lastReceivedAt?.takeIf { it > 0L }
    return when {
        state.pulling -> "正在拉取…"
        state.pullError != null && state.snapshot != null -> "拉取失败 · 使用缓存"
        state.pullError != null -> "拉取失败"
        syncedAt != null -> "已同步 ${formatSyncTime(syncedAt)}"
        else -> "等待拉取"
    }
}

private fun formatSyncTime(timestamp: Long?): String {
    if (timestamp == null || timestamp <= 0L) return "未知"
    return SimpleDateFormat("MM-dd HH:mm", Locale.CHINA).format(Date(timestamp))
}

private fun relatedFor(
    item: LibraryItem,
    all: List<LibraryItem>,
    limit: Int = 6,
): List<LibraryItem> {
    val series = item.series().lowercase()
    val genres = item.genres().map { it.lowercase() }
    val actresses = item.actressNames().map { it.lowercase() }
    return all.asSequence()
        .filter { it.id != item.id }
        .map { other ->
            var score = 0
            if (series.isNotBlank() && other.series().lowercase() == series) score += 4
            score += other.genres().map { it.lowercase() }.count { it in genres }
            if (actresses.any { name ->
                    other.actressNames().any { it.lowercase() == name }
                }
            ) {
                score += 2
            }
            other to score
        }
        .filter { it.second > 0 }
        .sortedWith(
            compareByDescending<Pair<LibraryItem, Int>> { it.second }
                .thenByDescending { it.first.createdAt },
        )
        .take(limit)
        .map { it.first }
        .toList()
}

private fun buildActressStats(
    items: List<LibraryItem>,
    avatars: Map<String, String>,
    meta: Map<String, ActressMeta>,
): List<ActressStat> {
    val map = linkedMapOf<String, ActressStat>()
    for (item in items) {
        for (name in item.actressNames()) {
            val key = name.lowercase()
            val existing = map[key]
            val actressMeta = meta[key]
            map[key] = ActressStat(
                key = key,
                name = existing?.name ?: name,
                count = (existing?.count ?: 0) + 1,
                avatar = avatars[key] ?: existing?.avatar.orEmpty(),
                favorite = actressMeta?.favorite ?: existing?.favorite ?: false,
                order = actressMeta?.order ?: existing?.order ?: 0,
            )
        }
    }
    return map.values.sortedWith(
        compareByDescending<ActressStat> { it.favorite }
            .thenByDescending { it.order }
            .thenByDescending { it.count }
            .thenBy { it.name },
    )
}

private fun buildSeriesStats(items: List<LibraryItem>): List<SeriesStat> {
    val map = linkedMapOf<String, SeriesStat>()
    for (item in items) {
        val label = item.series()
        if (label.isBlank()) continue
        val key = label.lowercase()
        val existing = map[key]
        val latestAt = maxOf(existing?.latestAt ?: 0L, item.createdAt)
        val cover = when {
            item.cover.isBlank() -> existing?.cover.orEmpty()
            existing == null || item.createdAt >= existing.latestAt -> item.cover
            else -> existing.cover
        }
        map[key] = SeriesStat(
            key = key,
            label = existing?.label ?: label,
            count = (existing?.count ?: 0) + 1,
            cover = cover,
            latestAt = latestAt,
        )
    }
    return map.values.sortedWith(
        compareByDescending<SeriesStat> { it.count }
            .thenByDescending { it.latestAt }
            .thenBy { it.label },
    )
}

private fun buildTagStats(items: List<LibraryItem>): List<TagStat> {
    val map = linkedMapOf<String, TagStat>()
    for (item in items) {
        for (label in item.displayLabels()) {
            val key = label.lowercase()
            val existing = map[key]
            map[key] = TagStat(
                key = key,
                label = existing?.label ?: label,
                count = (existing?.count ?: 0) + 1,
            )
        }
    }
    return map.values.sortedWith(
        compareByDescending<TagStat> { it.count }.thenBy { it.label },
    )
}

private fun filterAndSort(
    items: List<LibraryItem>,
    query: String,
    tab: LibraryTab,
    actressFilter: String?,
    seriesFilter: String?,
    genreFilter: String?,
    onlyWithActress: Boolean,
    unwatchedOnly: Boolean,
    sort: LibrarySort,
): List<LibraryItem> {
    var list = items
    if (tab == LibraryTab.Favorites) list = list.filter { it.favorite }
    if (onlyWithActress) list = list.filter { it.actressNames().isNotEmpty() }
    if (unwatchedOnly) list = list.filter { !it.watched }
    if (actressFilter != null) {
        list = list.filter { item ->
            item.actressNames().any { it.lowercase() == actressFilter }
        }
    }
    if (seriesFilter != null) {
        list = list.filter { it.series().lowercase() == seriesFilter }
    }
    if (genreFilter != null) {
        list = list.filter { item ->
            item.displayLabels().any { it.lowercase() == genreFilter }
        }
    }
    if (query.isNotBlank()) list = list.filter { matchesQuery(it, query) }
    return when (sort) {
        LibrarySort.Recent -> list.sortedByDescending { it.createdAt }
        LibrarySort.LastOpened -> list.sortedByDescending { it.lastOpenedAt }
        LibrarySort.Oldest -> list.sortedBy { it.createdAt }
        LibrarySort.Title -> list.sortedBy { it.title.lowercase() }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun FilterPage(
    sort: LibrarySort,
    onlyWithActress: Boolean,
    unwatchedOnly: Boolean,
    genreFilter: String?,
    tagStats: List<TagStat>,
    actressFilter: String?,
    actressName: String?,
    seriesFilter: String?,
    seriesLabel: String?,
    onSort: (LibrarySort) -> Unit,
    onToggleActress: () -> Unit,
    onToggleUnwatched: () -> Unit,
    onTag: (String?) -> Unit,
    onClearActress: () -> Unit,
    onClearSeries: () -> Unit,
    onClearTag: () -> Unit,
    onReset: () -> Unit,
    onBack: () -> Unit,
) {
    SubPage(
        title = "筛选与排序",
        subtitle = "排序、筛选与标签",
        onBack = onBack,
        contentSpacing = 12,
        actions = {
            TextButton(onClick = onReset) {
                Text("重置", color = PlayerColors.Accent, fontSize = 11.sp)
            }
        },
    ) {

            if (actressFilter != null || seriesFilter != null || genreFilter != null) {
                FilterSection("当前")
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                    verticalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    actressFilter?.let { key ->
                        ActiveFilterChip(
                            label = "演员 · ${actressName ?: key}",
                            onClear = onClearActress,
                        )
                    }
                    seriesFilter?.let { key ->
                        ActiveFilterChip(
                            label = "系列 · ${seriesLabel ?: key}",
                            onClear = onClearSeries,
                        )
                    }
                    genreFilter?.let { key ->
                        val label = tagStats.firstOrNull { it.key == key }?.label ?: key
                        ActiveFilterChip(
                            label = "标签 · $label",
                            onClear = onClearTag,
                        )
                    }
                }
            }

            FilterSection("排序")
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                FavourChip(
                    text = "最近添加",
                    active = sort == LibrarySort.Recent,
                    onClick = { onSort(LibrarySort.Recent) },
                )
                FavourChip(
                    text = "最近打开",
                    active = sort == LibrarySort.LastOpened,
                    onClick = { onSort(LibrarySort.LastOpened) },
                )
                FavourChip(
                    text = "最早收藏",
                    active = sort == LibrarySort.Oldest,
                    onClick = { onSort(LibrarySort.Oldest) },
                )
                FavourChip(
                    text = "标题",
                    active = sort == LibrarySort.Title,
                    onClick = { onSort(LibrarySort.Title) },
                )
            }

            FilterSection("筛选")
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                FavourChip(
                    text = "有演员",
                    active = onlyWithActress,
                    onClick = onToggleActress,
                )
                FavourChip(
                    text = "未看",
                    active = unwatchedOnly,
                    onClick = onToggleUnwatched,
                )
            }

            FilterSection("标签")
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                FavourChip(
                    text = "全部",
                    active = genreFilter == null,
                    onClick = { onTag(null) },
                )
                tagStats.forEach { stat ->
                    FavourChip(
                        text = "${stat.label} ${stat.count}",
                        active = genreFilter == stat.key,
                        onClick = { onTag(stat.key) },
                    )
                }
            }

            Button(
                onClick = onBack,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 46.dp)
                    .padding(top = 4.dp),
                shape = RoundedCornerShape(11.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = PlayerColors.Accent,
                    contentColor = PlayerColors.AccentInk,
                ),
            ) {
                Text("完成", fontWeight = FontWeight.SemiBold)
            }
    }
}

@Composable
private fun FilterSection(text: String) {
    Text(
        text = text,
        color = PlayerColors.Faint,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.2.sp,
        modifier = Modifier.padding(top = 4.dp),
    )
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
