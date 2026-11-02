package com.ziki.favourplayer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import coil.ImageLoader
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class CardScreenshotTest {

    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_5,
        theme = "android:Theme.Material.NoActionBar",
    )

    private val avatar = "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAEAAAABACAYAAACqaXHeAAAAZUlEQVR42u3QQREAAAQAMBFFFEcrcjh7rMAiu+azECBAgAABAgQIECBAgAABAgQIECBAgAABAgQIECBAgAABAgQIECBAgAABAgQIECBAgAABAgQIECBAgAABAgQIECBAgAABAu5b35ByhuY7u18AAAAASUVORK5CYII="

    @Test
    fun cardsWithTestData() {
        val loader = ImageLoader.Builder(paparazzi.context).build()
        val first = LibraryItem(
            id = "1",
            url = "https://example.com/first",
            title = "雨夜列车与没有寄出的信",
            tags = emptyList(),
            favorite = true,
            watched = true,
            lastOpenedAt = 1_790_000_000_000L,
            sourceName = "Example",
            extras = listOf(
                LibraryExtra("code", "编号", "ABC-123"),
                LibraryExtra("actress", "演员", "演员甲 (别名)"),
                LibraryExtra("releaseDate", "发售", "2024-06-18"),
                LibraryExtra("genres", "类型", "剧情,悬疑"),
            ),
            cover = avatar,
            createdAt = 1_790_000_000_000L,
        )
        val second = LibraryItem(
            id = "2",
            url = "https://example.com/second",
            title = "夏日回声",
            tags = emptyList(),
            favorite = false,
            watched = false,
            lastOpenedAt = 0L,
            sourceName = "Example",
            extras = listOf(
                LibraryExtra("code", "编号", "XYZ-789"),
                LibraryExtra("actress", "演员", "演员乙"),
                LibraryExtra("releaseDate", "发售", "2023-08-02"),
            ),
            cover = "",
            createdAt = 1_780_000_000_000L,
        )

        paparazzi.snapshot {
            FavourPlayerTheme {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(PlayerColors.Bg)
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    Text(
                        text = "卡片测试 · 390px",
                        color = PlayerColors.Muted,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(11.dp)) {
                        Box(Modifier.weight(1f)) {
                            ItemCard(first, loader, {}, {})
                        }
                        Box(Modifier.weight(1f)) {
                            ItemCard(second, loader, {}, {})
                        }
                    }
                    Text(
                        text = "演员卡片 · 头像 / 头像失败回退",
                        color = PlayerColors.Muted,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(Modifier.width(100.dp)) {
                            ActressCard(
                                name = first.actressNames().first(),
                                count = 12,
                                avatar = avatar,
                                favorite = true,
                                imageLoader = loader,
                                onClick = {},
                            )
                        }
                        Box(Modifier.width(100.dp)) {
                            ActressCard(
                                name = "头像加载失败",
                                count = 3,
                                avatar = "https://invalid.example.invalid/avatar.jpg",
                                favorite = false,
                                imageLoader = loader,
                                onClick = {},
                            )
                        }
                    }
                }
            }
        }
    }

    @Test
    fun actressNamesStripAlias() {
        val item = LibraryItem(
            id = "alias",
            url = "https://example.com/alias",
            title = "alias",
            tags = emptyList(),
            favorite = false,
            watched = false,
            lastOpenedAt = 0L,
            sourceName = "",
            extras = listOf(
                LibraryExtra("actress", "演员", "演员甲 (别名), 演员乙"),
            ),
            cover = "",
            createdAt = 1L,
        )

        assertEquals(listOf("演员甲", "演员乙"), item.actressNames())
    }
}