package com.ziki.favourplayer

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.ImageLoader
import coil.compose.AsyncImage

@Composable
fun DetailScreen(
    item: LibraryItem,
    progress: PlaybackProgress?,
    imageLoader: ImageLoader,
    onBack: () -> Unit,
    onPlay: (fromStart: Boolean) -> Unit,
    onActorClick: (String) -> Unit,
    onSeriesClick: (String) -> Unit,
    onTagClick: (String) -> Unit,
    related: List<LibraryItem>,
    onOpenRelated: (LibraryItem) -> Unit,
) {
    val context = LocalContext.current
    val code = item.extra("code")?.value.orEmpty()
    val actress = item.extra("actress")?.value.orEmpty()
    val releaseDate = item.extra("releaseDate")?.value.orEmpty()
    val publisher = item.extra("publisher")?.value.orEmpty()
    val director = item.extra("director")?.value.orEmpty()
    val label = item.extra("label")?.value.orEmpty()
    val runtime = item.runtime()
    val series = item.series()
    val labels = item.displayLabels()
    val synopsis = listOf("description", "summary", "plot")
        .firstNotNullOfOrNull { key -> item.extra(key)?.value?.takeIf { it.isNotBlank() } }
    val metaLine = listOf(releaseDate, publisher)
        .filter { it.isNotBlank() }
        .joinToString(" · ")
        .ifBlank { hostOf(item.url) }
    val resume = progress?.takeIf { it.resumable }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(PlayerColors.Bg),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(306.dp),
            ) {
                if (item.cover.isNotBlank()) {
                    AsyncImage(
                        model = item.cover,
                        imageLoader = imageLoader,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .alpha(0.62f),
                    )
                } else {
                    CoverFallback(
                        label = "",
                        seed = item.url,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    PlayerColors.Bg.copy(alpha = 0.10f),
                                    PlayerColors.Bg.copy(alpha = 0.38f),
                                    PlayerColors.Bg.copy(alpha = 0.97f),
                                    PlayerColors.Bg,
                                ),
                            ),
                        ),
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    DetailIcon(
                        icon = Icons.AutoMirrored.Rounded.ArrowBack,
                        label = "返回",
                        onClick = onBack,
                    )
                    DetailIcon(
                        icon = Icons.AutoMirrored.Rounded.OpenInNew,
                        label = "打开原页",
                        onClick = { openInBrowser(context, item.url) },
                    )
                }
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 18.dp),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .width(128.dp)
                            .height(86.dp)
                            .clip(RoundedCornerShape(15.dp))
                            .background(PlayerColors.PanelSoft),
                    ) {
                        if (item.cover.isNotBlank()) {
                            AsyncImage(
                                model = item.cover,
                                imageLoader = imageLoader,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                        } else {
                            CoverFallback(
                                label = "",
                                seed = item.url,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                    Column(Modifier.weight(1f)) {
                        if (code.isNotBlank()) {
                            Text(
                                text = code,
                                color = PlayerColors.Accent,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 9.5.sp,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(5.dp))
                                    .background(PlayerColors.AccentSoft)
                                    .padding(horizontal = 6.dp, vertical = 3.dp),
                            )
                        }
                        Text(
                            text = item.title.ifBlank { hostOf(item.url).ifBlank { item.url } },
                            color = PlayerColors.Text,
                            fontSize = 21.sp,
                            lineHeight = 26.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                        Text(
                            text = metaLine,
                            color = PlayerColors.Muted,
                            fontSize = 10.5.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 5.dp),
                        )
                        if (item.favorite || item.watched) {
                            Row(
                                modifier = Modifier.padding(top = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(5.dp),
                            ) {
                                if (item.favorite) {
                                    Row(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(999.dp))
                                            .background(PlayerColors.AccentSoft)
                                            .padding(horizontal = 8.dp, vertical = 3.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Favorite,
                                            contentDescription = null,
                                            tint = PlayerColors.Fav,
                                            modifier = Modifier.size(11.dp),
                                        )
                                        Text(
                                            text = "已收藏",
                                            color = PlayerColors.Fav,
                                            fontSize = 9.5.sp,
                                            fontWeight = FontWeight.SemiBold,
                                        )
                                    }
                                }
                                if (item.watched) {
                                    Text(
                                        text = "已看",
                                        color = PlayerColors.Muted,
                                        fontSize = 9.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(999.dp))
                                            .background(PlayerColors.PanelSoft)
                                            .padding(horizontal = 8.dp, vertical = 3.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(
                        onClick = { onPlay(false) },
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PlayerColors.Accent,
                            contentColor = PlayerColors.AccentInk,
                        ),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(5.dp))
                        Text(
                            text = if (resume != null) {
                                "继续 ${formatTime(resume.positionMs)}"
                            } else {
                                "立即播放"
                            },
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    OutlinedButton(
                        onClick = { openInBrowser(context, item.url) },
                        modifier = Modifier.height(46.dp),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, PlayerColors.BorderStrong),
                    ) {
                        Text("原页", color = PlayerColors.Text)
                    }
                }
                if (resume != null) {
                    TextButton(
                        onClick = { onPlay(true) },
                        modifier = Modifier.padding(top = 2.dp),
                    ) {
                        Text("从头播放", color = PlayerColors.Accent, fontSize = 11.5.sp)
                    }
                }

                if (actress.isNotBlank()) {
                    SectionTitle("演员")
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                    ) {
                        item.actressNames().forEach { name ->
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(PlayerColors.Panel)
                                    .clickable { onActorClick(name) }
                                    .padding(start = 5.dp, end = 11.dp, top = 5.dp, bottom = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(7.dp),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(25.dp)
                                        .clip(CircleShape)
                                        .background(PlayerColors.AccentSoft),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = name.take(1),
                                        color = PlayerColors.Accent,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                                Text(
                                    text = name,
                                    color = PlayerColors.Text,
                                    fontSize = 11.5.sp,
                                )
                            }
                        }
                    }
                }

                if (series.isNotBlank()) {
                    SectionTitle("系列")
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(PlayerColors.Panel)
                            .clickable { onSeriesClick(series) }
                            .padding(horizontal = 12.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = series,
                            color = PlayerColors.Accent,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("›", color = PlayerColors.Accent, fontSize = 15.sp)
                    }
                }

                SectionTitle("信息")
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(PlayerColors.Panel),
                ) {
                    InfoLine("编号", code)
                    InfoLine("发售", releaseDate)
                    InfoLine("厂商", publisher)
                    InfoLine("厂牌", label)
                    InfoLine("导演", director)
                    InfoLine("时长", runtime)
                    InfoLine("来源", item.sourceName)
                    InfoLine(
                        "上次打开",
                        formatDateTime(item.lastOpenedAt),
                    )
                    InfoLine(
                        "收藏时间",
                        formatDate(item.createdAt),
                    )
                }

                if (synopsis != null) {
                    SectionTitle("简介")
                    Text(
                        text = synopsis,
                        color = PlayerColors.Muted,
                        fontSize = 12.5.sp,
                        lineHeight = 20.sp,
                    )
                }

                if (labels.isNotEmpty()) {
                    SectionTitle("标签")
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        labels.forEach { tag ->
                            Text(
                                text = tag,
                                color = PlayerColors.Muted,
                                fontSize = 10.5.sp,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(PlayerColors.PanelSoft)
                                    .clickable { onTagClick(tag) }
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                            )
                        }
                    }
                }

                if (related.isNotEmpty()) {
                    SectionTitle("相关作品")
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(9.dp),
                        contentPadding = PaddingValues(end = 20.dp),
                    ) {
                        items(related, key = { it.id }) { entry ->
                            RelatedCard(
                                item = entry,
                                imageLoader = imageLoader,
                                onClick = { onOpenRelated(entry) },
                            )
                        }
                    }
                }
            }

            Spacer(
                modifier = Modifier
                    .height(28.dp)
                    .navigationBarsPadding(),
            )
        }
    }
}

@Composable
private fun DetailIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.34f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = Color.White,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        color = PlayerColors.Faint,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.2.sp,
        modifier = Modifier.padding(top = 22.dp, bottom = 9.dp),
    )
}

@Composable
private fun InfoLine(label: String, value: String) {
    if (value.isBlank()) return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 13.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = label, color = PlayerColors.Muted, fontSize = 11.5.sp)
        Text(
            text = value,
            color = PlayerColors.Text,
            fontSize = 11.5.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.End,
        )
    }
}

private fun formatDate(timestamp: Long): String {
    if (timestamp <= 0L) return ""
    return java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.CHINA)
        .format(java.util.Date(timestamp))
}

private fun formatDateTime(timestamp: Long): String {
    if (timestamp <= 0L) return ""
    return java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.CHINA)
        .format(java.util.Date(timestamp))
}

internal fun openInBrowser(context: Context, url: String) {
    runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(url))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}

private fun hostOf(url: String): String =
    runCatching { Uri.parse(url).host.orEmpty() }.getOrDefault("")
