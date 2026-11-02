package com.ziki.favourplayer

import org.json.JSONArray
import org.json.JSONObject

data class SpriteRule(
    val anchor: String,
    val window: Int,
    val frameCountKey: String,
    val widthKey: String,
    val heightKey: String,
    val colsKey: String,
    val rowsKey: String,
    val urlTemplate: String,
)

data class ResolveRule(
    val id: String,
    val name: String,
    val hosts: List<String>,
    val userAgent: String,
    val acceptLanguage: String,
    val sourcePatterns: List<Regex>,
    val hostGroup: Int,
    val uuidGroup: Int,
    val masterUrlTemplate: String,
    val sprite: SpriteRule?,
    val refererMode: String,
    val titleMode: String,
) {
    fun matches(host: String?): Boolean {
        if (hosts.isEmpty()) return true
        val target = host?.lowercase() ?: return false
        return hosts.any { raw ->
            val ruleHost = raw.lowercase().removePrefix("*.").removePrefix(".")
            ruleHost.isNotBlank() && (target == ruleHost || target.endsWith(".$ruleHost"))
        }
    }
}

object ResolveRuleParser {
    fun parse(json: String): List<ResolveRule> {
        if (json.isBlank()) return emptyList()
        val root = JSONObject(json)
        val array = root.optJSONArray("rules") ?: JSONArray()
        val result = mutableListOf<ResolveRule>()
        for (index in 0 until array.length()) {
            val obj = array.optJSONObject(index) ?: continue
            parseRule(obj)?.let(result::add)
        }
        return result
    }

    fun validate(json: String): String? = runCatching {
        if (parse(json).isEmpty()) "规则为空或格式不正确" else null
    }.getOrElse { "JSON 无效：${it.message}" }

    private fun parseRule(obj: JSONObject): ResolveRule? {
        val id = obj.optString("id").ifBlank { return null }
        val patterns = obj.optJSONArray("sourcePatterns")?.let { array ->
            buildList {
                for (index in 0 until array.length()) {
                    val raw = array.optString(index)
                    if (raw.isBlank()) continue
                    runCatching { Regex(raw) }.getOrNull()?.let(::add)
                }
            }
        }.orEmpty()
        if (patterns.isEmpty()) return null

        val master = obj.optString("masterUrlTemplate")
        if (master.isBlank()) return null

        val hosts = obj.optJSONArray("hosts")?.let { array ->
            buildList {
                for (index in 0 until array.length()) {
                    array.optString(index).trim().takeIf { it.isNotBlank() }?.let(::add)
                }
            }
        }.orEmpty()

        val sprite = obj.optJSONObject("sprite")?.let { spriteObj ->
            if (!spriteObj.optBoolean("enabled", true)) return@let null
            val urlTemplate = spriteObj.optString("urlTemplate")
            if (urlTemplate.isBlank()) return@let null
            SpriteRule(
                anchor = spriteObj.optString("anchor", "pic_num"),
                window = spriteObj.optInt("window", 700),
                frameCountKey = spriteObj.optString("frameCountKey", "pic_num"),
                widthKey = spriteObj.optString("widthKey", "width"),
                heightKey = spriteObj.optString("heightKey", "height"),
                colsKey = spriteObj.optString("colsKey", "col"),
                rowsKey = spriteObj.optString("rowsKey", "row"),
                urlTemplate = urlTemplate,
            )
        }

        return ResolveRule(
            id = id,
            name = obj.optString("name", id),
            hosts = hosts,
            userAgent = obj.optString("userAgent", Resolver.DESKTOP_UA),
            acceptLanguage = obj.optString("acceptLanguage", "zh-CN,zh;q=0.9,en;q=0.8"),
            sourcePatterns = patterns,
            hostGroup = obj.optInt("hostGroup", 1),
            uuidGroup = obj.optInt("uuidGroup", 2),
            masterUrlTemplate = master,
            sprite = sprite,
            refererMode = obj.optString("refererMode", "page-origin"),
            titleMode = obj.optString("titleMode", "page-title"),
        )
    }
}
