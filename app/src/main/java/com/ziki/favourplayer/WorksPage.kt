package com.ziki.favourplayer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.ImageLoader

/**
 * 演员或系列的作品合集页。留在当前 Tab 内，用二级页展示，不切换 Tab。
 * 内容自身滚动，所以 SubPage 关掉外层滚动，避免嵌套滚动崩溃。
 */
@Composable
fun WorksPage(
    title: String,
    subtitle: String,
    items: List<LibraryItem>,
    imageLoader: ImageLoader,
    onBack: () -> Unit,
    onOpen: (LibraryItem) -> Unit,
    onPlay: (LibraryItem) -> Unit,
) {
    SubPage(
        title = title,
        subtitle = subtitle,
        onBack = onBack,
        scrollable = false,
    ) {
        if (items.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "这里还没有作品",
                    color = PlayerColors.Muted,
                    fontSize = 13.sp,
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(150.dp),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
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
    }
}
