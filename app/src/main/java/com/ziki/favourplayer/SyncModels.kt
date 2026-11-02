package com.ziki.favourplayer

import org.json.JSONArray
import org.json.JSONObject

data class LibraryExtra(
    val key: String,
    val label: String,
    val value: String,
)

data class LibraryItem(
    val id: String,
    val url: String,
    val title: String,
    val tags: List<String>,
    val favorite: Boolean,
    val watched: Boolean,
    val lastOpenedAt: Long,
    val sourceName: String,
    val extras: List<LibraryExtra>,
    val cover: String,
    val createdAt: Long,
) {
    fun extra(key: String): LibraryExtra? = extras.firstOrNull { it.key == key }

    fun actressNames(): List<String> =
        splitLabels(extra("actress")?.value)
            .map(::baseActressName)
            .filter { it.isNotEmpty() }
            .distinctBy { it.lowercase() }

    fun genres(): List<String> =
        splitLabels(extra("genres")?.value)

    fun series(): String =
        extra("series")?.value.orEmpty().trim()

    fun runtime(): String =
        extra("runtime")?.value.orEmpty().trim()

    fun displayLabels(): List<String> =
        (genres() + tags)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinctBy { it.lowercase() }
}

data class ActressMeta(
    val favorite: Boolean,
    val order: Int,
)

data class LibrarySnapshot(
    val items: List<LibraryItem>,
    val updatedAt: Long,
    val actressAvatars: Map<String, String> = emptyMap(),
    val actressMeta: Map<String, ActressMeta> = emptyMap(),
)

private val TRAILING_PAREN = Regex("""\s*[（(][^（）()]*[)）]\s*$""")
private val SPACES = Regex("""\s+""")

private fun baseActressName(name: String): String {
    val value = name.replace(SPACES, " ").trim()
    return value.replace(TRAILING_PAREN, "").trim().ifEmpty { value }
}

private fun splitLabels(raw: String?): List<String> =
    raw.orEmpty()
        .split(',', '，', '、')
        .map { it.trim() }
        .filter { it.isNotEmpty() }

object SnapshotParser {
    fun parse(raw: String): LibrarySnapshot {
        val root = JSONObject(raw)
        val itemsJson = root.optJSONArray("items") ?: JSONArray()
        val items = buildList {
            for (index in 0 until itemsJson.length()) {
                val obj = itemsJson.optJSONObject(index) ?: continue
                val url = obj.optString("url")
                if (url.isBlank()) continue
                add(
                    LibraryItem(
                        id = obj.optString("id").ifBlank { url },
                        url = url,
                        title = obj.optString("title"),
                        tags = obj.optJSONArray("tags").toStringList(),
                        favorite = obj.optBoolean("favorite"),
                        watched = obj.optBoolean("watched"),
                        lastOpenedAt = obj.optLong("lastOpenedAt"),
                        sourceName = obj.optJSONObject("source")?.optString("name").orEmpty(),
                        extras = obj.optJSONArray("extras").toExtras(),
                        cover = obj.optString("cover"),
                        createdAt = obj.optLong("createdAt"),
                    ),
                )
            }
        }
        val avatars = root.optJSONObject("actressAvatars")?.let { source ->
            buildMap {
                for (key in source.keys()) {
                    val url = source.optString(key).trim()
                    if (url.startsWith("http://") || url.startsWith("https://") ||
                        url.startsWith("data:image/")
                    ) {
                        put(key.lowercase(), url)
                    }
                }
            }
        }.orEmpty()
        val actressMeta = root.optJSONObject("actressMeta")?.let { source ->
            buildMap {
                for (key in source.keys()) {
                    val entry = source.optJSONObject(key) ?: continue
                    val favorite = entry.optBoolean("favorite")
                    val order = entry.optInt("order", 0).coerceIn(0, 9999)
                    if (favorite || order > 0) put(key.lowercase(), ActressMeta(favorite, order))
                }
            }
        }.orEmpty()
        return LibrarySnapshot(
            items = items,
            updatedAt = root.optLong("updatedAt"),
            actressAvatars = avatars,
            actressMeta = actressMeta,
        )
    }

    private fun JSONArray?.toStringList(): List<String> {
        if (this == null) return emptyList()
        return buildList {
            for (index in 0 until length()) {
                optString(index).takeIf { it.isNotBlank() }?.let(::add)
            }
        }
    }

    private fun JSONArray?.toExtras(): List<LibraryExtra> {
        if (this == null) return emptyList()
        return buildList {
            for (index in 0 until length()) {
                val obj = optJSONObject(index) ?: continue
                val value = obj.optString("value")
                if (value.isBlank()) continue
                val key = obj.optString("key")
                add(
                    LibraryExtra(
                        key = key,
                        label = obj.optString("label").ifBlank { key },
                        value = value,
                    ),
                )
            }
        }
    }
}
