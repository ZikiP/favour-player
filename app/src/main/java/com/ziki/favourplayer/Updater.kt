package com.ziki.favourplayer

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.io.File
import java.io.FileOutputStream

/** 正式 tag 版本（v*）的信息。 */
data class UpdateInfo(
    val versionName: String,
    val versionCode: Int,
    val apkUrl: String,
)

/**
 * 应用内自更新：从 GitHub Release 拉取最新的正式 tag（v*）版本，
 * 和本机 versionCode 比较，有更新就下载 APK 并调起系统安装器。
 *
 * 只看正式 tag，忽略 debug-latest 滚动包——正式发版才提示，日常 push main 不打扰。
 */
object Updater {

    private const val REPO_API = "https://api.github.com/repos/ZikiP/favour-player"

    private val client = OkHttpClient.Builder()
        // GitHub API 强制要求 User-Agent，缺了直接 403
        .addInterceptor { chain ->
            chain.proceed(
                chain.request().newBuilder()
                    .header("User-Agent", "FavourPlayer")
                    .header("Accept", "application/vnd.github+json")
                    .build(),
            )
        }
        .build()

    /** 检查最新正式 tag 版本；返回 null 表示已是最新、或网络/解析失败。 */
    fun checkLatest(): UpdateInfo? {
        val request = Request.Builder().url("$REPO_API/releases?per_page=100").get().build()
        val json = try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                response.body?.string().orEmpty()
            }
        } catch (_: Exception) {
            return null
        }
        val array = runCatching { JSONArray(json) }.getOrNull() ?: return null
        // releases 按创建时间降序，取第一个 tag_name 以 v 开头的正式版本
        for (i in 0 until array.length()) {
            val release = array.optJSONObject(i) ?: continue
            val tag = release.optString("tag_name")
            if (!tag.startsWith("v")) continue
            val code = Regex("""\(code (\d+)\)""")
                .find(release.optString("body"))
                ?.groupValues?.get(1)?.toIntOrNull()
                ?: continue
            val apkUrl = apkUrlOf(release) ?: continue
            return UpdateInfo(
                versionName = tag.removePrefix("v"),
                versionCode = code,
                apkUrl = apkUrl,
            )
        }
        return null
    }

    private fun apkUrlOf(release: org.json.JSONObject): String? {
        val assets = release.optJSONArray("assets") ?: return null
        for (i in 0 until assets.length()) {
            val asset = assets.optJSONObject(i) ?: continue
            if (asset.optString("name").endsWith(".apk")) {
                return asset.optString("browser_download_url").takeIf { it.isNotBlank() }
            }
        }
        return null
    }

    /** 下载 APK 到缓存目录，返回文件；失败返回 null。 */
    fun downloadApk(context: Context, url: String): File? {
        val file = File(context.cacheDir, "update.apk")
        return try {
            val response = client.newCall(Request.Builder().url(url).get().build()).execute()
            response.use {
                if (!it.isSuccessful) return null
                val body = it.body ?: return null
                body.byteStream().use { input ->
                    FileOutputStream(file).use { output -> input.copyTo(output) }
                }
            }
            file
        } catch (_: Exception) {
            null
        }
    }

    /** 调起系统安装器安装 APK。 */
    fun install(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
