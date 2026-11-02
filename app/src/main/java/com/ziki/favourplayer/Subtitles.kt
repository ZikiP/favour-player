package com.ziki.favourplayer

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
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
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.security.MessageDigest

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
        if (bytes.size >= 2 &&
            bytes[0] == 0xFE.toByte() &&
            bytes[1] == 0xFF.toByte()
        ) {
            return String(bytes, 2, bytes.size - 2, Charsets.UTF_16BE)
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
        // 番剧外挂字幕大量是 ASS/SSA，它没有「-->」，得先识别再走另一条解析路径
        return if (isAss(normalized)) parseAss(normalized) else parseTimed(normalized)
    }

    private fun isAss(text: String): Boolean =
        text.contains("[Events]", ignoreCase = true) ||
            text.contains("Dialogue:", ignoreCase = true)

    private fun parseTimed(normalized: String): List<SubtitleCue> {
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

    /**
     * ASS/SSA：只取时间和文本，样式标签一律丢掉，按纯文本显示。
     * 字段位置从 `Format:` 行读，不同工具导出的列顺序不一样。
     */
    private fun parseAss(text: String): List<SubtitleCue> {
        val cues = mutableListOf<SubtitleCue>()
        var startIndex = 1
        var endIndex = 2
        var textIndex = 9
        var fieldCount = 10
        var inEvents = false

        for (rawLine in text.lines()) {
            val line = rawLine.trim()
            if (line.isEmpty()) continue
            if (line.startsWith("[")) {
                inEvents = line.equals("[Events]", ignoreCase = true)
                continue
            }
            if (!inEvents) continue

            if (line.startsWith("Format:", ignoreCase = true)) {
                val fields = line.substringAfter(':').split(',').map { it.trim().lowercase() }
                if (fields.isNotEmpty()) {
                    fieldCount = fields.size
                    fields.indexOf("start").takeIf { it >= 0 }?.let { startIndex = it }
                    fields.indexOf("end").takeIf { it >= 0 }?.let { endIndex = it }
                    fields.indexOf("text").takeIf { it >= 0 }?.let { textIndex = it }
                }
                continue
            }
            if (!line.startsWith("Dialogue:", ignoreCase = true)) continue

            val parts = line.substringAfter(':').split(",", limit = fieldCount)
            if (parts.size <= maxOf(startIndex, endIndex, textIndex)) continue
            val start = parseAssTime(parts[startIndex]) ?: continue
            val end = parseAssTime(parts[endIndex]) ?: continue
            if (end <= start) continue

            val body = cleanAss(parts[textIndex])
            if (body.isEmpty()) continue
            cues += SubtitleCue(start, end, body)
        }
        return cues.sortedBy { it.startMs }
    }

    /** `H:MM:SS.cc`，小数部分是百分之一秒。 */
    private fun parseAssTime(value: String): Long? {
        val parts = value.trim().split(':')
        if (parts.size != 3) return null
        return runCatching {
            val seconds = parts[2].toDouble()
            (parts[0].toLong() * 3600 + parts[1].toLong() * 60) * 1000 +
                (seconds * 1000).toLong()
        }.getOrNull()
    }

    private fun cleanAss(value: String): String {
        val stripped = assTag.replace(value, "")
            .replace("\\N", "\n")
            .replace("\\n", "\n")
            .replace("\\h", " ")
        return stripped.lines()
            .joinToString("\n") { it.trim() }
            .trim()
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

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("favour_subtitle", Context.MODE_PRIVATE)
    private val dir: File get() = File(appContext.filesDir, "subtitles")

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

    /**
     * 把字幕正文复制一份到应用私有目录。外部 URI 的授权经常失效
     * （换机、文件被移走、provider 不支持持久授权），存下来就不怕了。
     */
    fun saveText(key: String, name: String, text: String) {
        if (key.isBlank() || text.isBlank()) return
        runCatching {
            dir.mkdirs()
            val fileName = fileNameFor(key)
            File(dir, fileName).writeText(text)
            prefs.edit()
                .putString(textKey(key), fileName)
                .putString(nameKey(key), name)
                .apply()
        }
    }

    fun loadText(key: String): Pair<String, String>? {
        if (key.isBlank()) return null
        val fileName = prefs.getString(textKey(key), null) ?: return null
        val file = File(dir, fileName)
        if (!file.isFile) return null
        val text = runCatching { file.readText() }.getOrNull()
            ?.takeIf { it.isNotBlank() } ?: return null
        return prefs.getString(nameKey(key), "").orEmpty() to text
    }

    fun clear(key: String) {
        if (key.isBlank()) return
        prefs.getString(textKey(key), null)?.let { name ->
            runCatching { File(dir, name).delete() }
        }
        prefs.edit()
            .remove(uriKey(key))
            .remove(nameKey(key))
            .remove(textKey(key))
            .apply()
    }

    private fun fileNameFor(key: String): String {
        val digest = MessageDigest.getInstance("SHA-1").digest(key.toByteArray())
        return digest.joinToString("") { "%02x".format(it.toInt() and 0xFF) }.take(16) + ".txt"
    }

    private fun uriKey(key: String) = "$key\u0000uri"
    private fun nameKey(key: String) = "$key\u0000name"
    private fun textKey(key: String) = "$key\u0000text"
}

object SubtitleLoader {

    private const val TAG = "FavourPlayer"

    fun read(context: Context, uri: Uri): String {
        val bytes = openBytes(context, uri) ?: throw IOException(blockedReason(uri))
        if (bytes.isEmpty()) throw IOException("文件是空的")
        return SubtitleDecoder.decode(bytes)
    }

    /**
     * 依次试三种读法。有些 provider 的 openInputStream 会抛空消息的
     * SecurityException，但 openFileDescriptor 反而能过。
     */
    private fun openBytes(context: Context, uri: Uri): ByteArray? {
        val resolver = context.contentResolver
        try {
            resolver.openInputStream(uri)?.use { return it.readBytes() }
        } catch (error: Exception) {
            Log.w(TAG, "subtitle openInputStream: ${error.javaClass.simpleName}")
        }
        try {
            resolver.openFileDescriptor(uri, "r")?.use { pfd ->
                FileInputStream(pfd.fileDescriptor).use { return it.readBytes() }
            }
        } catch (error: Exception) {
            Log.w(TAG, "subtitle openFileDescriptor: ${error.javaClass.simpleName}")
        }
        try {
            resolver.query(
                uri,
                arrayOf(MediaStore.MediaColumns.DATA),
                null,
                null,
                null,
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val path = cursor.getString(0)
                    if (!path.isNullOrBlank()) {
                        val file = File(path)
                        if (file.isFile) return file.readBytes()
                    }
                }
            }
        } catch (error: Exception) {
            Log.w(TAG, "subtitle query path: ${error.javaClass.simpleName}")
        }
        return null
    }

    /** Android 11 起 Android/data 下的文件对其他 App 一律不可读，SAF 授权也绕不过。 */
    private fun blockedReason(uri: Uri): String {
        val text = uri.toString()
        val inAppDir = text.contains("/Android/data/", true) ||
            text.contains("Android%2Fdata", true)
        return if (inAppDir) {
            "这个位置系统不开放读取，请把字幕移到「下载」目录再选一次"
        } else {
            "打不开文件，可能授权已失效，换个位置再选一次"
        }
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

    /** 全局默认样式，设置页改的就是它。 */
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

    /** 某个作品单独的样式；没单独设过就沿用全局默认。 */
    fun load(key: String): SubtitleStyle {
        if (key.isBlank()) return load()
        val raw = prefs.getString(itemKey(key), null) ?: return load()
        return decode(raw) ?: load()
    }

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

    fun save(key: String, style: SubtitleStyle) {
        if (key.isBlank()) return
        prefs.edit().putString(itemKey(key), encode(style)).apply()
    }

    /** 清掉该作品的单独设置，回到全局默认。 */
    fun clear(key: String) {
        if (key.isBlank()) return
        prefs.edit().remove(itemKey(key)).apply()
    }

    fun hasOverride(key: String): Boolean =
        key.isNotBlank() && prefs.contains(itemKey(key))

    private fun itemKey(key: String) = "item\u0000$key"

    private fun encode(style: SubtitleStyle): String = JSONObject()
        .put("offset", style.offsetMs)
        .put("size", style.fontSize.toDouble())
        .put("color", style.textColor)
        .put("bg", style.background)
        .put("bold", style.bold)
        .put("pos", style.position)
        .toString()

    private fun decode(raw: String): SubtitleStyle? = runCatching {
        val json = JSONObject(raw)
        SubtitleStyle(
            offsetMs = json.optLong("offset", 0L)
                .coerceIn(-SubtitleStyle.OFFSET_LIMIT_MS, SubtitleStyle.OFFSET_LIMIT_MS),
            fontSize = json.optDouble("size", 15.0).toFloat()
                .coerceIn(SubtitleStyle.FONT_MIN, SubtitleStyle.FONT_MAX),
            textColor = json.optInt("color", 0xFFFFFFFF.toInt()),
            background = json.optInt("bg", 1).coerceIn(0, 2),
            bold = json.optBoolean("bold", false),
            position = json.optInt("pos", 1).coerceIn(0, 2),
        )
    }.getOrNull()

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
