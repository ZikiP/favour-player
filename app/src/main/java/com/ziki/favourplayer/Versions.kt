package com.ziki.favourplayer

/**
 * 编号版本解析 —— 与 favour-plugin/shared/versions.js 保持一致，改规则请一起改。
 *
 * 同一条目的不同版本在编号上体现为「基础编号 + 版本后缀」：
 *   ABC-123          默认版
 *   ABC-123-SUB      带字幕版
 *   ABC-123-4K       其它版本
 * 后缀从末尾往前剥，只有全部命中字典才算版本；剥不动的（如 ABC-123-2 的「-2」）
 * 视为完整编号，也就是独立的条目。播放优先级：带字幕 > 未删减 > 默认 > 其它。
 */
data class CodeInfo(
    val code: String,
    val base: String,
    val label: String,
    val rank: Int,
    val versioned: Boolean,
    val cnSub: Boolean,
    val uncensored: Boolean,
)

object Versions {
    const val RANK_CN_SUB = 0
    const val RANK_UNCENSORED = 1
    const val RANK_PLAIN = 2
    const val RANK_OTHER = 3

    private val CODE = Regex("""^([A-Z][A-Z0-9]{1,9})-(\d{2,5})((?:-[A-Z0-9]+)*)$""")
    private val URL_SLUG = Regex("""^[a-z]{2,10}-\d{2,5}(?:-[a-z0-9]+)*$""")

    private val CN_SUB = setOf(
        "C", "CH", "CN", "CHS", "ZH", "CHI", "CHINESE", "CHINESESUB", "CHINESESUBTITLE",
        "CNSUB", "CHSUB", "SUBTITLE", "SUB", "中字",
    )
    private val UNCENSORED = setOf(
        "U", "UC", "UNC", "UNCENSORED", "LEAK", "DECENSOR", "DECENSORED", "REDUCED", "MOSAIC", "无码",
    )
    private val QUALITY = setOf(
        "4K", "8K", "FHD", "HD", "FULL", "VR", "AI", "60FPS", "FPS", "UPSCALE", "ENHANCED",
        "REMASTER", "REMASTERED",
    )

    private val PLAIN = CodeInfo(
        code = "",
        base = "",
        label = "原版",
        rank = RANK_PLAIN,
        versioned = false,
        cnSub = false,
        uncensored = false,
    )

    fun parseCode(raw: String?): CodeInfo {
        val code = raw.orEmpty().replace(Regex("""\s+"""), "").uppercase()
        if (code.isEmpty()) return PLAIN

        val match = CODE.matchEntire(code)
        if (match != null) {
            val suffix = match.groupValues[3]
            val tokens = if (suffix.isEmpty()) emptyList() else suffix.removePrefix("-").split('-')
            var cut = tokens.size
            while (cut > 0 && isVersionToken(tokens[cut - 1])) cut -= 1

            if (cut < tokens.size) {
                val versionTokens = tokens.subList(cut, tokens.size)
                val info = summarize(versionTokens)
                if (info != null) {
                    val base = (listOf(match.groupValues[1], match.groupValues[2]) + tokens.subList(0, cut))
                        .joinToString("-")
                    return info.copy(code = code, base = base, versioned = true)
                }
            }
        }

        return PLAIN.copy(code = code, base = code)
    }

    private fun isVersionToken(token: String): Boolean =
        token in CN_SUB || token in UNCENSORED || token in QUALITY

    private fun summarize(tokens: List<String>): CodeInfo? {
        var cnSub = false
        var uncensored = false
        val quality = mutableListOf<String>()

        for (token in tokens) {
            when {
                token in CN_SUB -> cnSub = true
                token in UNCENSORED -> uncensored = true
                token in QUALITY -> quality += token
                else -> return null
            }
        }

        val labels = buildList {
            if (cnSub) add("中文字幕")
            if (uncensored) add("无码")
            addAll(quality)
        }
        return CodeInfo(
            code = "",
            base = "",
            label = labels.joinToString("·"),
            rank = when {
                cnSub -> RANK_CN_SUB
                uncensored -> RANK_UNCENSORED
                else -> RANK_OTHER
            },
            versioned = true,
            cnSub = cnSub,
            uncensored = uncensored,
        )
    }

    fun slugOfUrl(url: String): String {
        val path = runCatching { android.net.Uri.parse(url).path }.getOrNull().orEmpty()
        val segments = path.split('/').filter { it.isNotEmpty() }
        for (index in segments.indices.reversed()) {
            val segment = segments[index].lowercase()
            if (URL_SLUG.matches(segment)) return segment
        }
        return ""
    }

    /** 条目上存的编号字段；没有则退回 URL 末段 slug */
    fun codeOf(item: LibraryItem): String {
        val stored = item.extra("code")?.value?.trim().orEmpty()
        return stored.ifEmpty { slugOfUrl(item.url) }
    }

    fun of(item: LibraryItem): CodeInfo = parseCode(codeOf(item))

    /** 分组键：同一条目的所有版本共用；无编号的条目各成一组 */
    fun workKey(item: LibraryItem): String = parseCode(codeOf(item)).base

    /** 快捷播放选版：中文字幕优先，其次无码，再次原版；同档取最近更新的那条 */
    fun pickPreferred(items: List<LibraryItem>): LibraryItem? {
        var best: LibraryItem? = null
        var bestInfo: CodeInfo? = null
        for (item in items) {
            val info = of(item)
            val current = best
            val currentInfo = bestInfo
            if (current == null || currentInfo == null) {
                best = item
                bestInfo = info
                continue
            }
            if (info.rank < currentInfo.rank ||
                (info.rank == currentInfo.rank && item.createdAt > current.createdAt)
            ) {
                best = item
                bestInfo = info
            }
        }
        return best
    }
}

/** 一个作品（及其全部版本），列表里一张卡片对应一个 */
data class WorkGroup(
    val key: String,
    val code: String,
    val versions: List<LibraryItem>,
    val primary: LibraryItem,
) {
    val id: String get() = primary.id
    val multi: Boolean get() = versions.size > 1
    val title: String
        get() = versions.firstOrNull { it.title.isNotBlank() }?.title ?: primary.title
    val cover: String
        get() = versions.firstOrNull { it.cover.isNotBlank() }?.cover ?: primary.cover
    val favorite: Boolean get() = versions.any { it.favorite }
    val watched: Boolean get() = versions.any { it.watched }
    val createdAt: Long get() = versions.minOf { it.createdAt }
    val lastOpenedAt: Long get() = versions.maxOf { it.lastOpenedAt }
}

/** 按作品分组，保持传入顺序（调用方负责排序） */
fun groupItems(items: List<LibraryItem>): List<WorkGroup> {
    if (items.isEmpty()) return emptyList()

    val buckets = LinkedHashMap<String, MutableList<LibraryItem>>()
    for (item in items) {
        val key = Versions.workKey(item).ifEmpty { "item:${item.id}" }
        buckets.getOrPut(key) { mutableListOf() }.add(item)
    }

    return buckets.map { (key, members) ->
        val versions = members.sortedWith(
            compareBy({ Versions.of(it).rank }, { -it.createdAt }),
        )
        val primary = Versions.pickPreferred(versions) ?: versions.first()
        WorkGroup(
            key = key,
            code = Versions.of(primary).base,
            versions = versions,
            primary = primary,
        )
    }
}
