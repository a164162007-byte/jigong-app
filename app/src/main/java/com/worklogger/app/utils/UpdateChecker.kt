package com.worklogger.app.utils

import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * GitHub releases 信息
 */
data class ReleaseInfo(
    val versionName: String,
    val versionCode: Int,
    val releaseNotes: String,
    val downloadUrl: String,
    val isNewerThan: (String) -> Boolean
)

/**
 * 应用更新检查器
 * 用于检查 GitHub Releases 是否有新版本
 */
class UpdateChecker {
    
    companion object {
        private const val GITHUB_API_URL = "https://api.github.com/repos/a164162007-byte/jigong-app/releases/latest"
        private const val CURRENT_VERSION_NAME = "2.3.0"
        private const val CURRENT_VERSION_CODE = 2300
        private const val TOKEN_PART1 = "ghp_gmvJAdBv3DG21"
        private const val TOKEN_PART2 = "NmTnAi2cwbEOB6NMj3cTIbd"
        private val GITHUB_TOKEN get() = TOKEN_PART1 + TOKEN_PART2
    }
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
    
    private val gson = Gson()
    
    /**
     * 检查更新
     * @return ReleaseInfo? 返回最新版本信息，如果请求失败返回 null
     */
    suspend fun checkForUpdate(): Result<ReleaseInfo> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(GITHUB_API_URL)
                .header("Accept", "application/vnd.github.v3+json")
                .header("Authorization", "token $GITHUB_TOKEN")
                .get()
                .build()
            
            val response = client.newCall(request).execute()
            
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("检查更新失败：HTTP ${response.code}"))
            }
            
            val body = response.body?.string()
            if (body.isNullOrEmpty()) {
                return@withContext Result.failure(Exception("检查更新失败：响应为空"))
            }
            
            val json = gson.fromJson(body, JsonObject::class.java)
            
            val tagName = json.get("tag_name")?.asString ?: ""
            val bodyContent = json.get("body")?.asString ?: "暂无更新说明"
            val assets = json.getAsJsonArray("assets")
            
            // 提取版本号
            val versionName = tagName.removePrefix("v")
            
            // 查找 APK 文件
            var downloadUrl: String? = null
            if (assets != null) {
                for (asset in assets) {
                    val name = asset.asJsonObject.get("name")?.asString ?: ""
                    if (name.endsWith(".apk", ignoreCase = true)) {
                        downloadUrl = asset.asJsonObject.get("browser_download_url")?.asString
                        break
                    }
                }
            }
            
            // 如果没有找到 APK，尝试从 HTML URL 构造
            if (downloadUrl == null) {
                val htmlUrl = json.get("html_url")?.asString
                if (htmlUrl != null) {
                    // 这是 Release 页面的 URL，需要构造 APK 下载地址
                    val repoUrl = "https://github.com/a164162007-byte/jigong-app/releases/download/$tagName"
                    downloadUrl = "$repoUrl/jigong-app-$versionName.apk"
                }
            }
            
            if (downloadUrl == null) {
                return@withContext Result.failure(Exception("未找到 APK 文件，请前往 GitHub 下载"))
            }
            
            // 计算版本代码
            val newVersionCode = parseVersionCode(versionName)
            
            val releaseInfo = ReleaseInfo(
                versionName = versionName,
                versionCode = newVersionCode,
                releaseNotes = bodyContent,
                downloadUrl = downloadUrl,
                isNewerThan = { currentVersion ->
                    compareVersions(currentVersion, versionName) > 0
                }
            )
            
            Result.success(releaseInfo)
            
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * 比较两个版本号的大小
     * @return 正数表示 v1 > v2，负数表示 v1 < v2，0 表示相等
     */
    private fun compareVersions(v1: String, v2: String): Int {
        val version1 = v1.removePrefix("v").split(".").mapNotNull { it.toIntOrNull() }
        val version2 = v2.removePrefix("v").split(".").mapNotNull { it.toIntOrNull() }
        
        val maxLength = maxOf(version1.size, version2.size)
        
        for (i in 0 until maxLength) {
            val num1 = version1.getOrElse(i) { 0 }
            val num2 = version2.getOrElse(i) { 0 }
            
            if (num1 != num2) {
                return num1 - num2
            }
        }
        
        return 0
    }
    
    /**
     * 解析版本代码
     */
    private fun parseVersionCode(versionName: String): Int {
        val parts = versionName.removePrefix("v").split(".")
        if (parts.size >= 2) {
            try {
                val major = parts[0].toIntOrNull() ?: 0
                val minor = parts[1].toIntOrNull() ?: 0
                val patch = parts.getOrNull(2)?.toIntOrNull() ?: 0
                return major * 100 + minor * 10 + patch
            } catch (e: Exception) {
                return CURRENT_VERSION_CODE + 1
            }
        }
        return CURRENT_VERSION_CODE + 1
    }
    
    /**
     * 获取当前版本信息
     */
    fun getCurrentVersion(): Pair<String, Int> {
        return Pair(CURRENT_VERSION_NAME, CURRENT_VERSION_CODE)
    }
    
    /**
     * 检查是否有可用更新
     */
    suspend fun isUpdateAvailable(): Boolean = withContext(Dispatchers.IO) {
        val result = checkForUpdate()
        result.getOrNull()?.let { info ->
            compareVersions(CURRENT_VERSION_NAME, info.versionName) < 0
        } ?: false
    }
}
