package com.ziki.favourplayer

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage

@Composable
fun ItemCard(item: LibraryItem, onOpen: () -> Unit, onPlay: () -> Unit) {
    val code = item.extra("code")?.value.orEmpty()
    val actress = item.extra("actress")?.value.orEmpty()
    val date = item.extra("releaseDate")?.value.orEmpty()

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen),
        shape = RoundedCornerShape(14.dp),
        color = PlayerColors.Panel,
        border = BorderStroke(1.dp, PlayerColors.Border),
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(3f / 2f)
                    .background(PlayerColors.PanelSoft),
            ) {
                CoverFallback(
                    label = code.ifBlank { hostOf(item.url).take(1).uppercase() },
                    seed = item.url,
                )
                if (item.cover.isNotBlank()) {
                    AsyncImage(
                        model = item.cover,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                if (item.favorite) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(8.dp)
                            .size(27.dp)
                            .clip(CircleShape)
                            .background(PlayerColors.Bg.copy(alpha = 0.6f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Favorite,
                            contentDescription = "最爱",
                            tint = PlayerColors.Fav,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }
                if (date.isNotBlank()) {
                    DateBadge(date, Modifier.align(Alignment.TopEnd).padding(8.dp))
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(PlayerColors.Bg.copy(alpha = 0.62f))
                        .border(1.dp, Color.White.copy(alpha = 0.14f), CircleShape)
                        .clickable(onClick = onPlay),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "播放",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
            Column(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                if (code.isNotBlank() || actress.isNotBlank()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        if (code.isNotBlank()) CodeChip(code)
                        if (actress.isNotBlank()) {
                            Text(
                                text = actress,
                                color = PlayerColors.Muted,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false),
                            )
                        }
                    }
                }
                Text(
                    text = item.title.ifBlank { hostOf(item.url).ifBlank { item.url } },
                    color = PlayerColors.Text,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    lineHeight = 18.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
fun ActressCard(name: String, count: Int, onClick: () -> Unit) {
    val hue = hueOf(name)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = PlayerColors.Panel,
        border = BorderStroke(1.dp, PlayerColors.Border),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(11.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(Color.hsv(hue, 0.30f, 0.20f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = name.take(1).uppercase(),
                    color = Color.hsv(hue, 0.45f, 0.85f),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                )
            }
            Column(Modifier.weight(1f)) {
                Text(
                    text = name,
                    color = PlayerColors.Text,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "$count 部作品",
                    color = PlayerColors.Faint,
                    fontSize = 11.5.sp,
                )
            }
        }
    }
}

@Composable
fun ItemDetailDialog(item: LibraryItem, onDismiss: () -> Unit, onPlay: () -> Unit) {
    val context = LocalContext.current
    val code = item.extra("code")?.value.orEmpty()
    val meta = listOf(
        "演员" to item.extra("actress")?.value,
        "发售" to item.extra("releaseDate")?.value,
        "厂商" to item.extra("publisher")?.value,
        "导演" to item.extra("director")?.value,
    ).filter { !it.second.isNullOrBlank() }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .widthIn(max = 720.dp)
                .heightIn(max = 560.dp),
            shape = RoundedCornerShape(18.dp),
            color = PlayerColors.Panel,
            border = BorderStroke(1.dp, PlayerColors.Border),
            shadowElevation = 24.dp,
        ) {
            BoxWithConstraints {
                val wide = maxWidth > 560.dp
                Column(
                    modifier = Modifier
                        .verticalScroll(rememberScrollState())
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        IconButton(onClick = onDismiss, modifier = Modifier.size(36.dp)) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "关闭",
                                tint = PlayerColors.Faint,
                            )
                        }
                    }
                    if (wide) {
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            DetailCover(item, code, Modifier.width(220.dp))
                            DetailInfo(
                                item = item,
                                code = code,
                                meta = meta,
                                onPlay = onPlay,
                                onOpenOriginal = { openOriginal(context, item.url) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            DetailCover(item, code, Modifier.fillMaxWidth())
                            DetailInfo(
                                item = item,
                                code = code,
                                meta = meta,
                                onPlay = onPlay,
                                onOpenOriginal = { openOriginal(context, item.url) },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailCover(item: LibraryItem, code: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .aspectRatio(3f / 2f)
            .clip(RoundedCornerShape(12.dp))
            .background(PlayerColors.PanelSoft),
    ) {
        CoverFallback(
            label = code.ifBlank { hostOf(item.url).take(1).uppercase() },
            seed = item.url,
        )
        if (item.cover.isNotBlank()) {
            AsyncImage(
                model = item.cover,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun DetailInfo(
    item: LibraryItem,
    code: String,
    meta: List<Pair<String, String?>>,
    onPlay: () -> Unit,
    onOpenOriginal: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (code.isNotBlank()) CodeChip(code)
        Text(
            text = item.title.ifBlank { hostOf(item.url).ifBlank { item.url } },
            color = PlayerColors.Text,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            lineHeight = 24.sp,
        )
        if (meta.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                meta.forEach { (label, value) ->
                    if (value != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = label,
                                color = PlayerColors.Faint,
                                fontSize = 12.5.sp,
                                modifier = Modifier.width(44.dp),
                            )
                            Text(
                                text = value,
                                color = PlayerColors.Text,
                                fontSize = 12.5.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
        Row(
            modifier = Modifier.padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                onClick = onPlay,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = PlayerColors.Accent,
                    contentColor = PlayerColors.AccentInk,
                ),
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text("播放")
            }
            OutlinedButton(
                onClick = onOpenOriginal,
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, PlayerColors.BorderStrong),
            ) {
                Text("打开原页", color = PlayerColors.Text)
            }
        }
        Text(
            text = "手机直连 CDN 播放，不经过电脑中继",
            color = PlayerColors.Faint,
            fontSize = 11.5.sp,
        )
    }
}

@Composable
private fun CoverFallback(label: String, seed: String) {
    val hue = hueOf(seed)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    listOf(
                        Color.hsv(hue, 0.35f, 0.24f),
                        Color.hsv(hue, 0.30f, 0.14f),
                    ),
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = Color.hsv(hue, 0.40f, 0.92f),
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 17.sp,
            letterSpacing = 0.5.sp,
        )
    }
}

@Composable
private fun CodeChip(text: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(5.dp))
            .background(PlayerColors.AccentSoft)
            .padding(horizontal = 6.dp, vertical = 1.dp),
    ) {
        Text(
            text = text,
            color = PlayerColors.Accent,
            fontFamily = FontFamily.Monospace,
            fontSize = 10.5.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun DateBadge(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(PlayerColors.Bg.copy(alpha = 0.55f))
            .padding(horizontal = 7.dp, vertical = 2.dp),
    ) {
        Text(text = text, color = Color.White, fontSize = 10.5.sp)
    }
}

private fun hostOf(url: String): String =
    runCatching { Uri.parse(url).host.orEmpty() }.getOrDefault("")

private fun hueOf(text: String): Float =
    ((text.hashCode() and 0x7fffffff) % 360).toFloat()

private fun openOriginal(context: android.content.Context, url: String) {
    runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}
