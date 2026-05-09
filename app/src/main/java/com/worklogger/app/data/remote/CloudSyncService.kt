package com.worklogger.app.data.remote

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import com.worklogger.app.model.WorkRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Credentials.basic
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * 云端记工记录结构
 * 支持 Web 端多种字段命名格式
 */
data class CloudWorkRecord(
    val id: Int? = null,
    val date: String = "",
    val hours: Double = 0.0,
    val isOvertime: Boolean = false,
    val location: String? = null,
    val remark: String? = null,
    val mealSubsidy: Boolean? = null,
    val isDeleted: Boolean = false,
    // Web 端独有字段
    val recordType: String? = null,  // "standard", "overtime", "manual"
    val startTime: String? = null,
    val endTime: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null
)

data class LoginRequest(
    val username: String,
    val password: String
)

data class LoginResponse(
    val success: Boolean,
    val message: String? = null,
    val user_id: Int? = null
)

data class RecordsResponse(
    val success: Boolean,
    val message: String? = null,
    val data: List<CloudWorkRecord>? = null
)

data class SettingsData(
    val key: String,
    val value: String
)

data class SettingsResponse(
    val success: Boolean,
    val message: String? = null,
    val data: List<SettingsData>? = null
)

class SimpleCookieJar : CookieJar {
    private val cookieStore = mutableListOf<Cookie>()

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        cookieStore.addAll(cookies)
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        return cookieStore
    }

    fun clear() {
        cookieStore.clear()
    }
}

object CloudSyncService {
    private val cookieJar = SimpleCookieJar()
    private val client = OkHttpClient.Builder()
        .cookieJar(cookieJar)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val gson: Gson = GsonBuilder().create()

    /**
     * 构建完整 URL
     */
    private fun buildUrl(serverUrl: String, path: String): String {
        val base = if (serverUrl.endsWith("/")) serverUrl.removeSuffix("/") else serverUrl
        val pathWithSlash = if (path.startsWith("/")) path.removePrefix("/") else path
        return "$base/$pathWithSlash"
    }
    
    /**
     * 构建 Basic Auth 认证头
     */
    private fun buildAuthHeader(username: String, password: String): String {
        return basic(username, password)
    }
    
    /**
     * 构建带认证的请求
     */
    private fun buildAuthenticatedRequest(
        url: String,
        username: String,
        password: String,
        method: String = "GET",
        body: RequestBody? = null
    ): Request {
        val builder = Request.Builder()
            .url(url)
            .header("Authorization", buildAuthHeader(username, password))
        
        when (method) {
            "POST" -> builder.post(body!!)
            "GET" -> builder.get()
        }
        return builder.build()
    }

    /**
     * 登录
     * 同时支持 Cookie 和 Basic Auth 认证方式
     */
    suspend fun login(
        serverUrl: String,
        username: String,
        password: String
    ): Result<Boolean> {
        return withContext(Dispatchers.IO) {
            try {
                cookieJar.clear()

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
                
                val body = response.body?.string() ?: ""
                
                if (response.isSuccessful || response.code == 200 || body.contains("success")) {
                    Result.success(true)
                } else {
                    Result.failure(IOException("登录失败: HTTP ${response.code}"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    /**
     * 获取云端记录列表
     * 使用 Basic Auth 认证，兼容 Web 端返回格式
     */
    suspend fun fetchRecords(
        serverUrl: String,
        username: String,
        password: String
    ): Result<List<CloudWorkRecord>> {
        return withContext(Dispatchers.IO) {
            try {
                val url = buildUrl(serverUrl, "api/records?limit=10000")
                
                // 使用 Basic Auth 认证
                val request = buildAuthenticatedRequest(url, username, password)
                val response = client.newCall(request).execute()

                if (!response.isSuccessful) {
                    return@withContext Result.failure(IOException("HTTP ${response.code}: ${response.message}"))
                }

                val body = response.body?.string() ?: return@withContext Result.failure(IOException("Empty response"))
                
                // 解析为通用 Map，兼容 Web 端返回格式
                val rootType = object : TypeToken<Map<String, Any>>() {}.type
                val root = gson.fromJson<Map<String, Any>>(body, rootType)
                
                if (root["success"] != true) {
                    return@withContext Result.failure(IOException(root["message"]?.toString() ?: "获取记录失败"))
                }
                
                @Suppress("UNCHECKED_CAST")
                val dataList = root["data"] as? List<Map<String, Any>> ?: return@withContext Result.success(emptyList())
                
                // 逐条转换
                val records = dataList.map { rawMap -> parseCloudWorkRecord(rawMap) }
                Result.success(records)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
    
    /**
     * 解析云端记录，智能兼容多种字段命名格式
     */
    private fun parseCloudWorkRecord(raw: Map<String, Any>): CloudWorkRecord {
        val recordType = (raw["record_type"] ?: raw["recordType"])?.toString()
        val date = (raw["work_date"] ?: raw["date"])?.toString() ?: ""
        val hours = (raw["hours"] as? Number)?.toDouble() ?: 0.0
        val location = raw["location"]?.toString()
        val remark = (raw["remark"] ?: raw["remark_text"])?.toString()
        val mealSubsidy = when (val v = raw["meal_subsidy"] ?: raw["mealSubsidy"]) {
            is Boolean -> v
            is Number -> v.toDouble() != 0.0
            is String -> v == "true" || v == "1"
            null -> null
            else -> null
        }
        val isDeleted = (raw["is_deleted"] ?: raw["isDeleted"])?.let {
            when (it) {
                is Boolean -> it
                is Number -> it.toDouble() != 0.0
                is String -> it == "true" || it == "1"
                else -> false
            }
        } ?: false
        
        return CloudWorkRecord(
            date = date,
            hours = hours,
            location = location,
            remark = remark,
            mealSubsidy = mealSubsidy,
            recordType = recordType,
            isOvertime = recordType == "overtime",
            isDeleted = isDeleted
        )
    }

    /**
     * 上传记录到云端
     * 转换为 Web 端格式
     */
    suspend fun uploadRecords(
        serverUrl: String,
        username: String,
        password: String,
        records: List<WorkRecord>
    ): Result<Pair<Int, Int>> {
        return withContext(Dispatchers.IO) {
            try {
                val url = buildUrl(serverUrl, "api/records")
                val mediaType = "application/json; charset=utf-8".toMediaType()

                var successCount = 0
                var duplicateCount = 0

                for (record in records) {
                    // 转换为 Web 端格式
                    val cloudRecord = mapOf(
                        "work_date" to record.date,
                        "hours" to record.hours,
                        "record_type" to when {
                            record.isOvertime -> "overtime"
                            record.isManual -> "manual"
                            else -> "standard"
                        },
                        "location" to record.location,
                        "remark" to record.remark,
                        "meal_subsidy" to record.mealSubsidy
                    )
                    
                    val body = RequestBody.create(mediaType, gson.toJson(cloudRecord))
                    val request = buildAuthenticatedRequest(url, username, password, "POST", body)
                    val response = client.newCall(request).execute()

                    if (response.isSuccessful) {
                        val responseBody = response.body?.string() ?: ""
                        val responseData = gson.fromJson(responseBody, RecordsResponse::class.java)

                        if (responseData.success) {
                            successCount++
                        } else if (responseData.message?.contains("duplicate") == true ||
                            responseData.message?.contains("重复") == true) {
                            duplicateCount++
                        }
                    }
                }

                Result.success(Pair(successCount, duplicateCount))
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    /**
     * 获取云端设置
     */
    suspend fun fetchSettings(
        serverUrl: String,
        username: String,
        password: String
    ): Result<List<SettingsData>> {
        return withContext(Dispatchers.IO) {
            try {
                val url = buildUrl(serverUrl, "api/settings")
                val request = buildAuthenticatedRequest(url, username, password)
                val response = client.newCall(request).execute()

                if (!response.isSuccessful) {
                    return@withContext Result.failure(IOException("HTTP ${response.code}"))
                }

                val body = response.body?.string() ?: return@withContext Result.failure(IOException("Empty response"))
                val responseData = gson.fromJson(body, SettingsResponse::class.java)

                if (!responseData.success) {
                    return@withContext Result.failure(IOException(responseData.message ?: "获取设置失败"))
                }

                Result.success(responseData.data ?: emptyList())
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    /**
     * 上传设置到云端
     */
    suspend fun uploadSettings(
        serverUrl: String,
        username: String,
        password: String,
        settings: List<SettingsData>
    ): Result<Boolean> {
        return withContext(Dispatchers.IO) {
            try {
                val url = buildUrl(serverUrl, "api/settings")
                val mediaType = "application/json; charset=utf-8".toMediaType()
                val body = RequestBody.create(mediaType, gson.toJson(mapOf("settings" to settings)))
                val request = buildAuthenticatedRequest(url, username, password, "POST", body)
                val response = client.newCall(request).execute()

                if (!response.isSuccessful) {
                    return@withContext Result.failure(IOException("HTTP ${response.code}"))
                }

                val responseBody = response.body?.string() ?: ""
                val responseData = gson.fromJson(responseBody, SettingsResponse::class.java)

                Result.success(responseData.success)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    // 同步结果数据类
    data class SyncResult(
        val success: Boolean,
        val uploadedCount: Int = 0,
        val downloadedCount: Int = 0,
        val message: String? = null
    )

    /**
     * 测试连接
     * 使用 /api/health 端点（不需要认证）
     */
    suspend fun testConnection(serverUrl: String): Result<Boolean> {
        return withContext(Dispatchers.IO) {
            try {
                val url = buildUrl(serverUrl, "api/health")
                val request = Request.Builder().url(url).get().build()
                val response = client.newCall(request).execute()
                
                if (response.isSuccessful) {
                    Result.success(true)
                } else if (response.code == 404) {
                    // 如果 /api/health 不存在，尝试访问根路径
                    val rootUrl = buildUrl(serverUrl, "")
                    val rootRequest = Request.Builder().url(rootUrl).get().build()
                    val rootResponse = client.newCall(rootRequest).execute()
                    if (rootResponse.isSuccessful || rootResponse.code == 401) {
                        Result.success(true)
                    } else {
                        Result.failure(IOException("服务器未找到，请检查地址"))
                    }
                } else {
                    Result.failure(IOException("连接失败：HTTP ${response.code}"))
                }
            } catch (e: java.net.UnknownHostException) {
                Result.failure(IOException("域名解析失败，请检查服务器地址"))
            } catch (e: java.net.ConnectException) {
                Result.failure(IOException("无法连接服务器，请检查地址和端口"))
            } catch (e: java.net.SocketTimeoutException) {
                Result.failure(IOException("连接超时，请检查网络"))
            } catch (e: Exception) {
                Result.failure(IOException("连接失败：${e.message}"))
            }
        }
    }

    /**
     * 同步数据（上传 + 下载）
     */
    suspend fun syncData(
        serverUrl: String,
        username: String,
        password: String,
        localRecords: List<WorkRecord>
    ): SyncResult {
        return withContext(Dispatchers.IO) {
            try {
                // 先上传
                val uploadResult = uploadRecords(serverUrl, username, password, localRecords)
                val uploadedCount = if (uploadResult.isSuccess) uploadResult.getOrNull()?.first ?: 0 else 0
                
                // 返回结果
                SyncResult(
                    success = true,
                    uploadedCount = uploadedCount,
                    message = "同步完成"
                )
            } catch (e: Exception) {
                SyncResult(
                    success = false,
                    message = e.message ?: "同步失败"
                )
            }
        }
    }

    /**
     * 下载云端数据
     */
    suspend fun downloadData(
        serverUrl: String,
        username: String,
        password: String,
        localRecords: List<WorkRecord>
    ): Result<List<CloudWorkRecord>> {
        return fetchRecords(serverUrl, username, password)
    }

    /**
     * CloudWorkRecord 转换为 WorkRecord
     * 智能判断 recordType
     */
    fun CloudWorkRecord.toWorkRecord(): WorkRecord {
        // 优先使用 recordType 判断，回退到 isOvertime
        val (isOvertime, isManual) = when (recordType) {
            "overtime" -> true to false
            "manual" -> false to true
            "standard" -> false to false
            else -> isOvertime to false
        }
        
        // 饭补：如果没有明确值，标准工默认有饭补
        val hasMealSubsidy = mealSubsidy ?: (recordType == "standard" || (!isOvertime && !isManual))
        
        return WorkRecord(
            date = date,
            hours = hours,
            isOvertime = isOvertime,
            location = location ?: "",
            remark = remark ?: "",
            mealSubsidy = hasMealSubsidy,
            isManual = isManual,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
            isDeleted = isDeleted,
            deletedAt = if (isDeleted) System.currentTimeMillis() else null
        )
    }

    /**
     * WorkRecord 转换为 Map 用于导出
     */
    fun workRecordToExportMap(record: WorkRecord): Map<String, Any?> {
        return mapOf(
            "id" to record.id,
            "date" to record.date,
            "hours" to record.hours,
            "isOvertime" to record.isOvertime,
            "location" to record.location,
            "remark" to record.remark,
            "mealSubsidy" to record.mealSubsidy,
            "isManual" to record.isManual,
            "createdAt" to record.createdAt,
            "updatedAt" to record.updatedAt,
            "isDeleted" to record.isDeleted,
            "deletedAt" to record.deletedAt
        )
    }
}
