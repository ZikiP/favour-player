package com.ziki.favourplayer

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import android.util.LruCache
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

class SpriteLoader(
    private val referer: String,
    private val urlTemplate: String,
) {
    private val client = OkHttpClient.Builder().build()
    private val cache = LruCache<Int, ImageBitmap>(6)

    suspend fun sheet(index: Int): ImageBitmap? {
        cache.get(index)?.let { return it }
        return withContext(Dispatchers.IO) {
            cache.get(index)?.let { return@withContext it }
            val request = Request.Builder()
                .url(urlTemplate.replace("{sheet}", index.toString()))
                .header("User-Agent", Resolver.DESKTOP_UA)
                .header("Referer", referer)
                .build()
            runCatching {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@withContext null
                    val bytes = response.body?.bytes() ?: return@withContext null
                    val options = BitmapFactory.Options().apply {
                        inPreferredConfig = Bitmap.Config.RGB_565
                    }
                    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
                        ?: return@withContext null
                    bitmap.asImageBitmap().also {
                        cache.put(index, it)
                        Log.i("FavourPlayer", "sprite sheet=$index ${bitmap.width}x${bitmap.height}")
                    }
                }
            }.getOrNull()
        }
    }
}

private data class FrameRef(val sheet: Int, val col: Int, val row: Int)

private fun frameRef(frame: Int, config: SpriteConfig): FrameRef {
    val perSheet = config.framesPerSheet
    val sheet = frame / perSheet
    val cell = frame % perSheet
    return FrameRef(sheet, cell % config.cols, cell / config.cols)
}

@Composable
fun SpritePreview(
    frame: Int,
    positionMs: Long,
    config: SpriteConfig,
    loader: SpriteLoader,
    modifier: Modifier = Modifier,
) {
    val ref = frameRef(frame, config)
    val sheet by produceState<ImageBitmap?>(initialValue = null, key1 = ref.sheet) {
        value = loader.sheet(ref.sheet)
    }

    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .shadow(10.dp, RoundedCornerShape(10.dp))
                .background(PlayerColors.Panel, RoundedCornerShape(10.dp))
                .border(1.dp, PlayerColors.Border, RoundedCornerShape(10.dp))
                .padding(4.dp),
        ) {
            Canvas(
                Modifier
                    .width(200.dp)
                    .aspectRatio(config.frameWidth.toFloat() / config.frameHeight.toFloat()),
            ) {
                val bitmap = sheet ?: return@Canvas
                drawImage(
                    image = bitmap,
                    srcOffset = IntOffset(
                        ref.col * config.frameWidth,
                        ref.row * config.frameHeight,
                    ),
                    srcSize = IntSize(config.frameWidth, config.frameHeight),
                    dstSize = IntSize(size.width.toInt(), size.height.toInt()),
                    filterQuality = FilterQuality.Low,
                )
            }
        }
        Text(
            text = formatTime(positionMs),
            color = PlayerColors.Muted,
            fontSize = 12.sp,
            modifier = Modifier.padding(top = 5.dp),
        )
    }
}
