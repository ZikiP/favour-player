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
    val extras: List<LibraryExtra>,
    val cover: String,
    val createdAt: Long,
) {
    fun extra(key: String): LibraryExtra? = extras.firstOrNull { it.key == key }

    fun actressNames(): List<String> =
        extra("actress")?.value
            ?.split(',', '，', '、')
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            .orEmpty()
}

data class LibrarySnapshot(
    val items: List<LibraryItem>,
    val updatedAt: Long,
)

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
                        extras = obj.optJSONArray("extras").toExtras(),
                        cover = obj.optString("cover"),
                        createdAt = obj.optLong("createdAt"),
                    ),
                )
            }
        }
        return LibrarySnapshot(items = items, updatedAt = root.optLong("updatedAt"))
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
