package com.worklogger.app.data.remote

import com.google.gson.Gson
import com.worklogger.app.model.WorkRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * 云同步服务
 * 用于与Flask后端进行数据同步（Session-based认证）
 * v2.1.3.0
 */
class CloudSyncService {

    private val gson = Gson()

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .cookieJar(SimpleCookieJar())
        .build()

    /**
     * 简单的Cookie管理器，用于保存Session Cookie
     */
    private class SimpleCookieJar : okhttp3.CookieJar {
        private val cookies: MutableList<okhttp3.Cookie> = mutableListOf()

        override fun saveFromResponse(url: okhttp3.HttpUrl, cookies: List<okhttp3.Cookie>) {
            this.cookies.clear()
            this.cookies.addAll(cookies)
        }

        override fun loadForRequest(url: okhttp3.HttpUrl): List<okhttp3.Cookie> {
            return cookies.filter { it.matches(url) }
        }

        fun clear() {
            cookies.clear()
        }
    }

    /**
     * 同步结果
     */
    data class SyncResult(
        val success: Boolean,
        val message: String,
        val uploadedCount: Int = 0,
        val downloadedCount: Int = 0,
        val duplicateCount: Int = 0
    )

    /**
     * 云端记录数据格式
     */
    data class CloudWorkRecord(
        val date: String,
        val hours: Double,
        val isOvertime: Boolean,
        val location: String,
        val remark: String,
        val mealSubsidy: Boolean,
        val isManual: Boolean,
        val createdAt: Long,
        val updatedAt: Long
    )

    /**
     * 登录响应
     */
    data class LoginResponse(
        val success: Boolean,
        val message: String? = null,
        val need_login: Boolean? = null
    )

    /**
     * 记录API响应
     */
    data class RecordsResponse(
        val success: Boolean,
        val data: List<CloudWorkRecord>? = null,
        val message: String? = null
    )

    /**
     * 单条记录上传响应
     */
    data class UploadResponse(
        val success: Boolean,
        val duplicate: Boolean? = false,
        val message: String? = null
    )

    /**
     * 设置API响应
     */
    data class SettingsResponse(
        val success: Boolean,
        val data: Map<String, Any>? = null,
        val message: String? = null
    )

    /**
     * 登录并获取Session Cookie
     */
    private suspend fun login(serverUrl: String, username: String, password: String): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                (client.cookieJar as SimpleCookieJar).clear()

                val url = buildUrl(serverUrl, "login")
                val formBody = FormBody.Builder()
                    .add("username", username)
                    .add("password", password)
                    .build()

                val request = Request.Builder()
                    .url(url)
                    .post(formBody)
                    .build()

                val response = client.newCall(request).execute()

                // 检查是否有need_login字段（可能在重定向后的页面中）
                val body = response.body?.string() ?: ""
                if (body.contains("need_login", ignoreCase = true)) {
                    return@withContext Result.failure(IOException("登录失败: 需要重新登录"))
                }

                if (!response.isSuccessful && response.code != 302) {
                    return@withContext Result.failure(IOException("HTTP ${response.code}: ${response.message}"))
                }

                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    /**
     * 测试服务器连接
     */
    suspend fun testConnection(serverUrl: String, username: String, password: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                // 先登录
                val loginResult = login(serverUrl, username, password)
                if (loginResult.isFailure) {
                    return@withContext false
                }

                // 尝试访问version或直接验证登录状态
                val url = buildUrl(serverUrl, "api/version")
                val request = Request.Builder().url(url).get().build()
                val response = client.newCall(request).execute()

                // 如果能成功访问或302重定向到登录页也算连接成功（至少服务可用）
                response.isSuccessful || response.code == 302
            } catch (e: Exception) {
                false
            }
        }
    }

    /**
     * 从云端获取所有记录
     */
    suspend fun fetchRecords(
        serverUrl: String,
        username: String,
        password: String
    ): Result<List<CloudWorkRecord>> {
        return withContext(Dispatchers.IO) {
            try {
                // 先登录
                val loginResult = login(serverUrl, username, password)
                if (loginResult.isFailure) {
                    return@withContext Result.failure(loginResult.exceptionOrNull() ?: IOException("登录失败"))
                }

                // 使用大limit获取所有记录
                val url = buildUrl(serverUrl, "api/records?limit=10000")
                val request = Request.Builder().url(url).get().build()
                val response = client.newCall(request).execute()

                if (!response.isSuccessful) {
                    return@withContext Result.failure(IOException("HTTP ${response.code}: ${response.message}"))
                }

                val body = response.body?.string() ?: return@withContext Result.failure(IOException("Empty response"))
                val responseData = gson.fromJson(body, RecordsResponse::class.java)

                if (!responseData.success) {
                    return@withContext Result.failure(IOException(responseData.message ?: "获取记录失败"))
                }

                Result.success(responseData.data ?: emptyList())
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    /**
     * 上传记录到云端（逐条上传）
     */
    suspend fun uploadRecords(
        serverUrl: String,
        username: String,
        password: String,
        records: List<WorkRecord>
    ): Result<Pair<Int, Int>> { // Pair<成功数量, 重复数量>
        return withContext(Dispatchers.IO) {
            try {
                // 先登录
                val loginResult = login(serverUrl, username, password)
                if (loginResult.isFailure) {
                    return@withContext Result.failure(loginResult.exceptionOrNull() ?: IOException("登录失败"))
                }

                val url = buildUrl(serverUrl, "api/records")
                val mediaType = "application/json; charset=utf-8".toMediaType()

                var successCount = 0
                var duplicateCount = 0

                for (record in records) {
                    val cloudRecord = CloudWorkRecord(
                        date = record.date,
                        hours = record.hours,
                        isOvertime = record.isOverti