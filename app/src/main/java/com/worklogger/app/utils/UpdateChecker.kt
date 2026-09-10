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
 * 通过 GitHub 公开文件检查更新，无需任何 Token，永不过期
 */
class UpdateChecker {
    
    companion object {
        // 主地址：GitHub raw 内容（官方CDN）
        private const val VERSION_JSON_URL = "https://raw.githubusercontent.com/a164162007-byte/jigong-app/main/version.json"
        // 备用地址：jsDelivr CDN（国内访问更稳定）
        private const val VERSION_JSON_URL_BACKUP = "https://cdn.jsdelivr.net/gh/a164162007-byte/jigong-app@latest/version.json"
        
        private const val CURRENT_VERSION_NAME = "2.3.9"
        private const val CURRENT_VERSION_CODE = 2390
    }
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()
    
    private val gson = Gson()
    
    /**
     * 检查更新
     * 优先从 GitHub raw 读取，失败则从 jsDelivr CDN 读取
     * @return ReleaseInfo? 返回最新版本信息，如果请求失败返回 null
     */
    suspend fun checkForUpdate(): Result<ReleaseInfo> = withContext(Dispatchers.IO) {
        // 优先尝试主地址
        var result = fetchVersionInfo(VERSION_JSON_URL)
        
        // 主地址失败，尝试备用地址
        if (result.isFailure) {
            result = fetchVersionInfo(VERSION_JSON_URL_BACKUP)
        }
        
        result
    }
    
    /**
     * 从指定 URL 获取版本信息
     */
    private fun fetchVersionInfo(url: String): Result<ReleaseInfo> {
        return try {
            val request = Request.Builder()
                .url(url)
                .header("Accept", "application/json")
                .get()
                .build()
            
            val response = client.newCall(request).execute()
            
            if (!response.isSuccessful) {
                return Result.failure(Exception("检查更新失败：HTTP ${response.code}"))
            }
            
            val body = response.body?.string()
            if (body.isNullOrEmpty()) {
                return Result.failure(Exception("检查更新失败：响应为空"))
            }
            
            val json = gson.fromJson(body, JsonObject::class.java)
            
            val versionName = json.get("versionName")?.asString
                ?: return Result.failure(Exception("版本信息格式错误"))
            val versionCode = json.get("versionCode")?.asInt ?: 0
            val releaseNotes = json.get("releaseNotes")?.asString ?: "暂无更新说明"
            val downloadUrl = json.get("downloadUrl")?.asString
                ?: return Result.failure(Exception("未找到下载地址"))
            
            val releaseInfo = ReleaseInfo(
                versionName = versionName,
                versionCode = versionCode,
                releaseNotes = releaseNotes,
                downloadUrl = downloadUrl,
                isNewerThan = { currentVersion ->
                    compareVersions(currentVersion, versionName) < 0
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
