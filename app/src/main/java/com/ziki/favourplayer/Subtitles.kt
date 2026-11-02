package com.ziki.favourplayer

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

data class SubtitleCue(
    val startMs: Long,
    val endMs: Long,
    val text: String,
)

data class SubtitleTrack(
    val name: String,
    val cues: List<SubtitleCue>,
) {
    /** 同一时刻可能有多条（双语字幕），全部拼上。 */
    fun textAt(positionMs: Long): String? {
        val hit = cues.filter { positionMs >= it.startMs && positionMs < it.endMs }
        return if (hit.isEmpty()) null else hit.joinToString("\n") { it.text }
    }
}

object SubtitleDecoder {

    private val gb: Charset =
        runCatching { Charset.forName("GB18030") }.getOrDefault(Charsets.ISO_8859_1)

    fun decode(bytes: ByteArray): String {
        if (bytes.isEmpty()) return ""
        if (bytes.size >= 3 &&
            bytes[0] == 0xEF.toByte() &&
            bytes[1] == 0xBB.toByte() &&
            bytes[2] == 0xBF.toByte()
        ) {
            return String(bytes, 3, bytes.size - 3, Charsets.UTF_8)
        }
        if (bytes.size >= 2 &&
            bytes[0] == 0xFF.toByte() &&
            bytes[1] == 0xFE.toByte()
        ) {
            return String(bytes, 2, bytes.size - 2, Charsets.UTF_16LE)
        }
        strictUtf8(bytes)?.let { return it }
        return String(bytes, gb)
    }

    private fun strictUtf8(bytes: ByteArray): String? = runCatching {
        Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes))
            .toString()
    }.getOrNull()
}

object SubtitleParser {

    private val htmlTag = Regex("""</?[a-zA-Z][^>]*>""")
    private val assTag = Regex("""\{\\[^}]*}""")
    private val blankLine = Regex("""\n\s*\n""")
    private val entities = mapOf(
        "&nbsp;" to " ",
        "&amp;" to "&",
        "&lt;" to "<",
        "&gt;" to ">",
        "&quot;" to "\"",
        "&#39;" to "'",
    )

    fun parse(raw: String): List<SubtitleCue> {
        val normalized = raw.replace("\r\n", "\n").replace('\r', '\n')
        val cues = mutableListOf<SubtitleCue>()
        for (block in normalized.split(blankLine)) {
            val lines = block.trim().lines()
            if (lines.isEmpty()) continue
            val timeIndex = lines.indexOfFirst { it.contains("-->") }
            if (timeIndex < 0) continue

            val bounds = lines[timeIndex].split("-->")
            if (bounds.size < 2) continue
            val start = parseTimecode(bounds[0]) ?: continue
            val end = parseTimecode(bounds[1]) ?: continue
            if (end <= start) continue

            val body = clean(lines.drop(timeIndex + 1).joinToString("\n"))
            if (body.isEmpty()) continue
            cues += SubtitleCue(start, end, body)
        }
        return cues.sortedBy { it.startMs }
    }

    /** 支持 `HH:MM:SS,mmm`、`MM:SS.mmm`，分隔符逗号或点。 */
    private fun parseTimecode(value: String): Long? {
        val token = value.trim().substringBefore(' ').replace(',', '.')
        if (token.isEmpty()) return null
        val parts = token.split(':')
        if (parts.size !in 2..3) return null
        return runCatching {
            val seconds = parts.last().toDouble()
            val minutes = parts[parts.size - 2].toLong()
            val hours = if (parts.size == 3) parts[0].toLong() else 0L
            (hours * 3600 + minutes * 60) * 1000 + (seconds * 1000).toLong()
        }.getOrNull()
    }

    private fun clean(value: String): String {
        var text = assTag.replace(value, "")
        text = htmlTag.replace(text, "")
        for ((from, to) in entities) text = text.replace(from, to)
        return text.lines()
            .joinToString("\n") { it.trim() }
            .trim()
    }
}

class SubtitleStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("favour_subtitle", Context.MODE_PRIVATE)

    fun save(key: String, uri: Uri, name: String) {
        if (key.isBlank()) return
        prefs.edit()
            .putString(uriKey(key), uri.toString())
            .putString(nameKey(key), name)
            .apply()
    }

    fun load(key: String): Pair<Uri, String>? {
        if (key.isBlank()) return null
        val raw = prefs.getString(uriKey(key), null) ?: return null
        val uri = runCatching { Uri.parse(raw) }.getOrNull() ?: return null
        return uri to prefs.getString(nameKey(key), "").orEmpty()
    }

    fun clear(key: String) {
        if (key.isBlank()) return
        prefs.edit().remove(uriKey(key)).remove(nameKey(key)).apply()
    }

    private fun uriKey(key: String) = "$key\u0000uri"
    private fun nameKey(key: String) = "$key\u0000name"
}

object SubtitleLoader {

    fun read(context: Context, uri: Uri): String {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: error("无法读取字幕文件")
        return SubtitleDecoder.decode(bytes)
    }

    fun displayName(context: Context, uri: Uri): String {
        val resolved = runCatching {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
            }
        }.getOrNull()
        return resolved?.takeIf { it.isNotBlank() }
            ?: uri.lastPathSegment.orEmpty().substringAfterLast('/')
    }

    /** 让下次启动还能读到同一个文件；文件被删或权限失效时返回 false。 */
    fun takePermission(context: Context, uri: Uri) {
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
    }
}

data class SubtitleStyle(
    val offsetMs: Long = 0L,
    val fontSize: Float = 15f,
    val textColor: Int = 0xFFFFFFFF.toInt(),
    val background: Int = 1,
    val bold: Boolean = false,
    val position: Int = 1,
) {
    companion object {
        const val OFFSET_LIMIT_MS = 10_000L
        const val OFFSET_STEP_MS = 500L
        const val FONT_MIN = 12f
        const val FONT_MAX = 28f

        val COLORS = listOf(
            0xFFFFFFFF.toInt() to "白",
            0xFFFFE082.toInt() to "暖黄",
            0xFFA5E3FF.toInt() to "天蓝",
            0xFFB7F0C2.toInt() to "浅绿",
        )
    }
}

class SubtitleStyleStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("favour_subtitle_style", Context.MODE_PRIVATE)

    fun load(): SubtitleStyle = SubtitleStyle(
        offsetMs = prefs.getLong(KEY_OFFSET, 0L)
            .coerceIn(-SubtitleStyle.OFFSET_LIMIT_MS, SubtitleStyle.OFFSET_LIMIT_MS),
        fontSize = prefs.getFloat(KEY_SIZE, 15f)
            .coerceIn(SubtitleStyle.FONT_MIN, SubtitleStyle.FONT_MAX),
        textColor = prefs.getInt(KEY_COLOR, 0xFFFFFFFF.toInt()),
        background = prefs.getInt(KEY_BG, 1).coerceIn(0, 2),
        bold = prefs.getBoolean(KEY_BOLD, false),
        position = prefs.getInt(KEY_POS, 1).coerceIn(0, 2),
    )

    fun save(style: SubtitleStyle) {
        prefs.edit()
            .putLong(KEY_OFFSET, style.offsetMs)
            .putFloat(KEY_SIZE, style.fontSize)
            .putInt(KEY_COLOR, style.textColor)
            .putInt(KEY_BG, style.background)
            .putBoolean(KEY_BOLD, style.bold)
            .putInt(KEY_POS, style.position)
            .apply()
    }

    private companion object {
        const val KEY_OFFSET = "offset_ms"
        const val KEY_SIZE = "font_size"
        const val KEY_COLOR = "text_color"
        const val KEY_BG = "background"
        const val KEY_BOLD = "bold"
        const val KEY_POS = "position"
    }
}

@Composable
fun SubtitleText(
    text: String,
    style: SubtitleStyle,
    modifier: Modifier = Modifier,
) {
    val background = when (style.background) {
        0 -> Color.Transparent
        2 -> Color.Black.copy(alpha = 0.78f)
        else -> Color.Black.copy(alpha = 0.42f)
    }
    Text(
        text = text,
        style = TextStyle(
            color = Color(style.textColor),
            fontSize = style.fontSize.sp,
            lineHeight = (style.fontSize * 1.38f).sp,
            fontWeight = if (style.bold) FontWeight.Bold else FontWeight.Medium,
            textAlign = TextAlign.Center,
            shadow = Shadow(
                color = Color.Black,
                offset = Offset(1.5f, 1.5f),
                blurRadius = 4f,
            ),
        ),
        modifier = modifier
            .widthIn(max = 560.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(background)
            .padding(horizontal = 11.dp, vertical = 5.dp),
    )
}

@Composable
fun SubtitleOverlay(
    track: SubtitleTrack,
    positionMs: Long,
    style: SubtitleStyle,
    modifier: Modifier = Modifier,
) {
    val text = track.textAt(positionMs - style.offsetMs) ?: return
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        SubtitleText(text = text, style = style)
    }
}

/** 位置档位对应的额外上移距离，叠在基础边距之上。 */
fun SubtitleStyle.extraBottomDp(): Int = when (position) {
    0 -> 0
    2 -> 46
    else -> 18
}
