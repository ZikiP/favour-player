package com.ziki.favourplayer

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.ImageLoader
import coil.compose.AsyncImage

@Composable
fun ItemCard(
    item: LibraryItem,
    imageLoader: ImageLoader,
    onClick: () -> Unit,
    onPlay: () -> Unit,
) {
    val code = item.extra("code")?.value.orEmpty()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1.5f)
                .clip(RoundedCornerShape(16.dp))
                .background(PlayerColors.PanelSoft),
        ) {
            CoverArt(item, imageLoader)
            if (item.favorite) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.45f))
                        .border(1.dp, Color.White.copy(alpha = 0.15f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Favorite,
                        contentDescription = "最爱",
                        tint = PlayerColors.Fav,
                        modifier = Modifier.size(13.dp),
                    )
                }
            }
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.52f))
                    .border(1.dp, Color.White.copy(alpha = 0.22f), CircleShape)
                    .clickable(onClick = onPlay),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.PlayArrow,
                    contentDescription = "播放",
                    tint = Color.White,
                    modifier = Modifier.size(19.dp),
                )
            }
        }
        if (code.isNotBlank()) {
            Text(
                text = code,
                color = PlayerColors.Muted,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Medium,
                fontSize = 10.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 2.dp, top = 5.dp),
            )
        }
    }
}

@Composable
fun ContinueCard(
    item: LibraryItem,
    progress: PlaybackProgress,
    imageLoader: ImageLoader,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .width(228.dp)
            .aspectRatio(1.5f)
            .clip(RoundedCornerShape(16.dp))
            .background(PlayerColors.PanelSoft)
            .clickable(onClick = onClick),
    ) {
        CoverArt(item, imageLoader)
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.12f),
                            Color.Black.copy(alpha = 0.9f),
                        ),
                    ),
                ),
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(start = 10.dp, end = 44.dp, bottom = 15.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                text = item.title.ifBlank { hostOf(item.url).ifBlank { item.url } },
                color = Color.White,
                fontSize = 12.5.sp,
                lineHeight = 16.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "剩余 ${formatRemaining(progress.remainingMs)} · ${formatTime(progress.positionMs)}",
                color = Color.White.copy(alpha = 0.66f),
                fontSize = 10.sp,
                maxLines = 1,
            )
        }
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(3.dp)
                .background(Color.White.copy(alpha = 0.18f)),
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth(progress.fraction.coerceIn(0f, 1f))
                .height(3.dp)
                .background(PlayerColors.Accent),
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(8.dp)
                .size(32.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.52f))
                .border(1.dp, Color.White.copy(alpha = 0.2f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.PlayArrow,
                contentDescription = "继续观看",
                tint = Color.White,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun CoverArt(item: LibraryItem, imageLoader: ImageLoader) {
    CoverFallback(
        label = "",
        seed = item.url,
        modifier = Modifier.fillMaxSize(),
    )
    if (item.cover.isNotBlank()) {
        AsyncImage(
            model = item.cover,
            imageLoader = imageLoader,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
fun RelatedCard(
    item: LibraryItem,
    imageLoader: ImageLoader,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .width(150.dp)
            .aspectRatio(1.5f)
            .clip(RoundedCornerShape(12.dp))
            .background(PlayerColors.PanelSoft)
            .clickable(onClick = onClick),
    ) {
        CoverArt(item, imageLoader)
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.86f)),
                    ),
                ),
        )
        Text(
            text = item.title.ifBlank { hostOf(item.url) },
            color = Color.White,
            fontSize = 11.sp,
            lineHeight = 14.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(8.dp),
        )
    }
}

@Composable
fun SeriesCard(
    label: String,
    count: Int,
    cover: String,
    imageLoader: ImageLoader,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1.5f)
            .clip(RoundedCornerShape(16.dp))
            .background(PlayerColors.PanelSoft)
            .clickable(onClick = onClick),
    ) {
        if (cover.isNotBlank()) {
            AsyncImage(
                model = cover,
                imageLoader = imageLoader,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            CoverFallback(
                label = "",
                seed = label,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.9f)),
                    ),
                ),
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 9.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = label,
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "$count 部作品",
                color = Color.White.copy(alpha = 0.64f),
                fontSize = 9.5.sp,
            )
        }
    }
}

@Composable
fun ActressCard(
    name: String,
    count: Int,
    avatar: String,
    favorite: Boolean,
    imageLoader: ImageLoader,
    onClick: () -> Unit,
) {
    val hue = hueOf(name)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(PlayerColors.Panel)
            .border(1.dp, PlayerColors.Border, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(Color.hsv(hue, 0.32f, 0.22f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = name.take(1).uppercase(),
                color = Color.hsv(hue, 0.42f, 0.86f),
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
            )
            if (avatar.isNotBlank()) {
                AsyncImage(
                    model = avatar,
                    imageLoader = imageLoader,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Column(Modifier.weight(1f)) {
            Text(
                text = name,
                color = PlayerColors.Text,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "$count 部作品",
                color = PlayerColors.Muted,
                fontSize = 10.5.sp,
            )
        }
        if (favorite) {
            Icon(
                imageVector = Icons.Rounded.Favorite,
                contentDescription = "喜爱的演员",
                tint = PlayerColors.Fav,
                modifier = Modifier.size(13.dp),
            )
        }
    }
}

@Composable
fun CoverFallback(
    label: String,
    seed: String,
    modifier: Modifier = Modifier,
) {
    val hue = hueOf(seed)
    Box(
        modifier = modifier.background(
            Brush.linearGradient(
                listOf(
                    Color.hsv(hue, 0.42f, 0.30f),
                    Color.hsv((hue + 26f) % 360f, 0.34f, 0.13f),
                ),
            ),
        ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = Color.hsv(hue, 0.32f, 0.92f),
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            letterSpacing = 0.6.sp,
        )
    }
}

private fun formatRemaining(ms: Long): String {
    val minutes = (ms / 60_000L).coerceAtLeast(0L)
    if (minutes >= 60) {
        val hours = minutes / 60
        val rest = minutes % 60
        return "${hours}小时${rest}分"
    }
    return "${minutes}分钟"
}

private fun hostOf(url: String): String =
    runCatching { Uri.parse(url).host.orEmpty() }.getOrDefault("")

private fun hueOf(text: String): Float =
    ((text.hashCode() and 0x7fffffff) % 360).toFloat()
