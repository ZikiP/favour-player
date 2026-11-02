package com.ziki.favourplayer

import android.content.Context

class RuleStore(context: Context) {

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("resolver_rules", Context.MODE_PRIVATE)

    private val defaultJson: String by lazy {
        runCatching {
            appContext.assets.open("resolver_rules.json").bufferedReader().use { it.readText() }
        }.getOrDefault("{\"rules\":[]}")
    }

    private val defaultRules: List<ResolveRule> by lazy {
        runCatching { ResolveRuleParser.parse(defaultJson) }.getOrDefault(emptyList())
    }

    fun userJson(): String = prefs.getString(KEY_USER_RULES, "").orEmpty()

    fun defaultExampleJson(): String = defaultJson

    fun loadRules(): List<ResolveRule> {
        val userRules = runCatching { ResolveRuleParser.parse(userJson()) }.getOrDefault(emptyList())
        return userRules + defaultRules
    }

    fun saveUserRules(json: String): String? {
        val error = ResolveRuleParser.validate(json)
        if (error != null) return error
        prefs.edit().putString(KEY_USER_RULES, json).apply()
        return null
    }

    fun clearUserRules() {
        prefs.edit().remove(KEY_USER_RULES).apply()
    }

    companion object {
        private const val KEY_USER_RULES = "rules_json"
    }
}
