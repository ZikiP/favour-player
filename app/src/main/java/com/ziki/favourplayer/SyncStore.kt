package com.ziki.favourplayer

import android.content.Context
import android.util.Log
import java.io.File
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

data class ReceiveConfig(
    val port: Int,
    val token: String,
    val enabled: Boolean,
)

class SyncStore(context: Context) {

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("favour_receive", Context.MODE_PRIVATE)
    private val cacheFile = File(appContext.filesDir, "snapshot.json")

    fun loadConfig(): ReceiveConfig {
        val port = prefs.getInt(KEY_PORT, DEFAULT_PORT).takeIf { it in PORT_RANGE } ?: DEFAULT_PORT
        val token = prefs.getString(KEY_TOKEN, "").orEmpty().ifBlank {
            generateToken().also { prefs.edit().putString(KEY_TOKEN, it).apply() }
        }
        val enabled = prefs.getBoolean(KEY_ENABLED, true)
        return ReceiveConfig(port, token, enabled)
    }

    fun saveConfig(config: ReceiveConfig) {
        prefs.edit()
            .putInt(KEY_PORT, config.port)
            .putString(KEY_TOKEN, config.token)
            .putBoolean(KEY_ENABLED, config.enabled)
            .apply()
    }

    fun regenerateToken(): String {
        val token = generateToken()
        prefs.edit().putString(KEY_TOKEN, token).apply()
        return token
    }

    fun loadCached(): LibrarySnapshot? {
        if (!cacheFile.isFile) return null
        return runCatching { SnapshotParser.parse(cacheFile.readText()) }.getOrNull()
    }

    fun rawSnapshot(): String? =
        runCatching { cacheFile.takeIf { it.isFile }?.readText() }.getOrNull()

    @Throws(Exception::class)
    fun saveSnapshot(raw: String): LibrarySnapshot {
        val snapshot = SnapshotParser.parse(raw)
        cacheFile.writeText(raw)
        Log.i(TAG, "received ${snapshot.items.size} items")
        return snapshot
    }

    fun clearSnapshot() {
        runCatching { cacheFile.delete() }
    }

    fun tokenMatches(given: String): Boolean {
        val expected = loadConfig().token.toByteArray()
        val actual = given.toByteArray()
        return expected.isNotEmpty() &&
            expected.size == actual.size &&
            MessageDigest.isEqual(expected, actual)
    }

    companion object {
        const val DEFAULT_PORT = 8790
        private val PORT_RANGE = 1024..65535
        private const val KEY_PORT = "port"
        private const val KEY_TOKEN = "token"
        private const val KEY_ENABLED = "enabled"
        private const val TAG = "FavourPlayer"

        private fun generateToken(): String {
            val bytes = ByteArray(24)
            SecureRandom().nextBytes(bytes)
            return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
        }
    }
}
