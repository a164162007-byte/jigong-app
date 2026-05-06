package com.worklogger.app.data.remote

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.worklogger.app.model.WorkRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * 云同步服务
 * 用于与Docker部署的Web版记工App进行数据同步
 * 
 * 数据格式适配Web端：
 * - record_type: "standard" | "manual" | "overtime"
 * - work_date: "2026-05-07"
 * - meal_subsidy: 标准工自动推断为true
 */
class CloudSyncService {
    
    private val gson = Gson()
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .followRedirects(false)  // 不跟随重定向
        .build()
    
    /**
     * 同步结果
     */
    data class SyncResult(
        val success: Boolean,
        val message: String,
        val uploadedCount: Int = 0,
        val downloadedCount: Int = 0
    )
    
    /**
     * 云端记录数据格式 - 匹配Web端 /api/records 返回的格式
     */
    data class CloudWorkRecord(
        val id: Int? = null,
        @SerializedName("user_id")
        val userId: Int? = null,
        @SerializedName("record_type")
        val recordType: String,      // "standard", "manual", "overtime"
        @SerializedName("work_date")
        val workDate: String,        // "2026-05-07"
        val location: String,
        @SerializedName("start_time")
        val startTime: String? = null,
        @SerializedName("end_time")
        val endTime: String? = null,
        @SerializedName("morning_end_time")
        val morningEndTime: String? = null,
        @SerializedName("afternoon_start_time")
        val afternoonStartTime: String? = null,
        val hours: Double,
        @SerializedName("deleted_at")
        val deletedAt: String? = null,
        @SerializedName("created_at")
        val createdAt: String? = null,
        @SerializedName("updated_at")
        val updatedAt: String? = null,
        // App端特有字段，导出时使用
        @SerializedName("meal_subsidy")
        val mealSubsidy: Boolean? = null
    )
    
    /**
     * Web端API响应格式
     */
    data class ApiResponse<T>(
        val success: Boolean,
        val message: String? = null,
        val data: T? = null
    )
    
    /**
     * 测试服务器连接
     * 调用 /api/health 接口，无需认证
     */
    suspend fun testConnection(serverUrl: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val url = buildUrl(serverUrl, "api/health")
                val request = Request.Builder()
                    .url(url)
                    .get()
                    .build()
                val response = client.newCall(request).execute()
                response.isSuccessful
            } catch (e: Exception) {
                false
            }
        }
    }
    
    /**
     * 从云端获取所有记录
     * 调用 GET /api/records，使用 Basic Auth 认证
     */
    suspend fun fetchRecords(
        serverUrl: String,
        username: String,
        password: String
    ): Result<List<CloudWorkRecord>> {
        return withContext(Dispatchers.IO) {
            try {
                val url = buildUrl(serverUrl, "api/records")
                val request = Request.Builder()
                    .url(url)
                    .header("Authorization", Credentials.basic(username, password))
                    .get()
                    .build()
                
                val response = client.newCall(request).execute()
                
                // 检查认证失败
                if (response.code == 401) {
                    return@withContext Result.failure(IOException("认证失败，请检查用户名和密码"))
                }
                
                if (response.code == 302) {
                    return@withContext Result.failure(IOException("认证失败，需要登录"))
                }
                
                if (!response.isSuccessful) {
                    return@withContext Result.failure(IOException("HTTP ${response.code}: ${response.message}"))
                }
                
                val body = response.body?.string() ?: return@withContext Result.failure(IOException("Empty response"))
                
                // 解析响应，Web端返回 {"success": true, "data": [...]}
                val apiResponse = gson.fromJson(body, ApiResponse::class.java)
                if (apiResponse?.success != true || apiResponse.data == null) {
                    return@withContext Result.failure(IOException(apiResponse?.message ?: "获取数据失败"))
                }
                
                // data可能是列表或对象
                val records = if (apiResponse.data is List<*>) {
                    @Suppress("UNCHECKED_CAST")
                    (apiResponse.data as List<Map<String, Any>>).map { map ->
                        gson.fromJson(gson.toJson(map), CloudWorkRecord::class.java)
                    }
                } else {
                    emptyList()
                }
                
                Result.success(records)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
    
    /**
     * 上传记录到云端
     * 调用 POST /api/records
     */
    suspend fun uploadRecords(
        serverUrl: String,
        username: String,
        password: String,
        records: List<WorkRecord>
    ): Result<Int> {
        return withContext(Dispatchers.IO) {
            try {
                val url = buildUrl(serverUrl, "api/records")
                
                // 将WorkRecord转换为CloudWorkRecord
                val cloudRecords = records.map { record ->
                    CloudWorkRecord(
                        recordType = when {
                            record.isOvertime -> "overtime"
                            record.isManual -> "manual"
                            else -> "standard"
                        },
                        workDate = record.date,
                        location = record.location,
                        startTime = null,
                        endTime = null,
                        hours = record.hours,
                        createdAt = null,
                        updatedAt = null,
                        mealSubsidy = record.mealSubsidy
                    )
                }
                
                val json = gson.toJson(cloudRecords)
                val mediaType = "application/json; charset=utf-8".toMediaType()
                val body = json.toRequestBody(mediaType)
                
                val request = Request.Builder()
                    .url(url)
                    .header("Authorization", Credentials.basic(username, password))
                    .post(body)
                    .build()
                
                val response = client.newCall(request).execute()
                
                if (!response.isSuccessful) {
                    return@withContext Result.failure(IOException("HTTP ${response.code}: ${response.message}"))
                }
                
                Result.success(records.size)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
    
    /**
     * 同步数据（上传本地新增/修改，下载云端新增/修改）
     */
    suspend fun syncData(
        serverUrl: String,
        username: String,
        password: String,
        localRecords: List<WorkRecord>
    ): SyncResult {
        return withContext(Dispatchers.IO) {
            try {
                // 1. 先获取云端所有记录
                val cloudResult = fetchRecords(serverUrl, username, password)
                if (cloudResult.isFailure) {
                    return@withContext SyncResult(
                        success = false,
                        message = "获取云端数据失败: ${cloudResult.exceptionOrNull()?.message}"
                    )
                }
                val cloudRecords = cloudResult.getOrNull() ?: emptyList()
                
                // 2. 找出本地需要上传的记录（云端没有的）
                val cloudKeys = cloudRecords.map { "${it.workDate}_${it.recordType}" }.toSet()
                val recordsToUpload = localRecords.filter { record ->
                    val key = "${record.date}_${getRecordType(record)}"
                    key !in cloudKeys
                }
                
                // 3. 上传本地新记录
                var uploadedCount = 0
                if (recordsToUpload.isNotEmpty()) {
                    val uploadResult = uploadRecords(serverUrl, username, password, recordsToUpload)
                    if (uploadResult.isSuccess) {
                        uploadedCount = uploadResult.getOrNull() ?: 0
                    } else {
                        return@withContext SyncResult(
                            success = false,
                            message = "上传数据失败: ${uploadResult.exceptionOrNull()?.message}"
                        )
                    }
                }
                
                // 4. 计算下载数量（云端有但本地没有的）
                val localKeys = localRecords.map { "${it.date}_${getRecordType(it)}" }.toSet()
                val downloadedCount = cloudRecords.count { cloud ->
                    "${cloud.workDate}_${cloud.recordType}" !in localKeys
                }
                
                SyncResult(
                    success = true,
                    message = "同步成功",
                    uploadedCount = uploadedCount,
                    downloadedCount = downloadedCount
                )
            } catch (e: Exception) {
                SyncResult(
                    success = false,
                    message = "同步失败: ${e.message}"
                )
            }
        }
    }
    
    /**
     * 下载云端数据到本地
     * 返回云端有但本地没有的记录列表
     */
    suspend fun downloadData(
        serverUrl: String,
        username: String,
        password: String,
        localRecords: List<WorkRecord>
    ): Result<List<WorkRecord>> {
        return withContext(Dispatchers.IO) {
            try {
                // 1. 获取云端所有记录
                val cloudResult = fetchRecords(serverUrl, username, password)
                if (cloudResult.isFailure) {
                    return@withContext Result.failure(
                        cloudResult.exceptionOrNull() ?: IOException("获取云端数据失败")
                    )
                }
                val cloudRecords = cloudResult.getOrNull() ?: emptyList()
                
                // 2. 找出云端有但本地没有的记录
                val localKeys = localRecords.map { "${it.date}_${getRecordType(it)}" }.toSet()
                val recordsToDownload = cloudRecords.filter { cloud ->
                    "${cloud.workDate}_${cloud.recordType}" !in localKeys
                }
                
                // 3. 转换为WorkRecord
                val workRecords = recordsToDownload.map { cloud ->
                    cloudRecordToWorkRecord(cloud)
                }
                
                Result.success(workRecords)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
    
    /**
     * 将CloudWorkRecord转换为WorkRecord
     * 
     * 字段映射：
     * - work_date -> date
     * - record_type -> isOvertime / isManual
     * - hours -> hours
     * - location -> location
     * - meal_subsidy: Web端没有此字段，标准工自动推断为true
     */
    fun cloudRecordToWorkRecord(cloudRecord: CloudWorkRecord): WorkRecord {
        val recordType = cloudRecord.recordType
        // 标准工自动有饭补
        val mealSubsidy = cloudRecord.mealSubsidy ?: (recordType == "standard")
        
        return WorkRecord(
            date = cloudRecord.workDate,
            hours = cloudRecord.hours,
            isOvertime = recordType == "overtime",
            location = cloudRecord.location,
            remark = "",  // Web端没有备注字段
            mealSubsidy = mealSubsidy,
            isManual = recordType == "manual",
            createdAt = parseIsoDateTime(cloudRecord.createdAt),
            updatedAt = parseIsoDateTime(cloudRecord.updatedAt)
        )
    }
    
    /**
     * 将WorkRecord转换为用于导出的Map格式
     */
    fun workRecordToExportMap(record: WorkRecord): Map<String, Any?> {
        return mapOf(
            "date" to record.date,
            "record_type" to getRecordType(record),
            "hours" to record.hours,
            "location" to record.location,
            "remark" to record.remark,
            "meal_subsidy" to record.mealSubsidy,
            "is_manual" to record.isManual,
            "is_overtime" to record.isOvertime,
            "created_at" to record.createdAt,
            "updated_at" to record.updatedAt
        )
    }
    
    /**
     * 获取记录类型字符串
     */
    private fun getRecordType(record: WorkRecord): String {
        return when {
            record.isOvertime -> "overtime"
            record.isManual -> "manual"
            else -> "standard"
        }
    }
    
    /**
     * 解析ISO格式日期时间字符串为时间戳
     */
    private fun parseIsoDateTime(isoString: String?): Long {
        if (isoString.isNullOrEmpty()) return System.currentTimeMillis()
        return try {
            java.time.Instant.parse(isoString).toEpochMilli()
        } catch (e: Exception) {
            System.currentTimeMillis()
        }
    }
    
    /**
     * 构建API URL
     */
    private fun buildUrl(baseUrl: String, path: String): String {
        val cleanUrl = baseUrl.trimEnd('/')
        return "$cleanUrl/$path"
    }
}
