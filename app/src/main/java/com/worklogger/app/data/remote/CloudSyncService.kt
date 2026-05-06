package com.worklogger.app.data.remote

import com.google.gson.Gson
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
 */
class CloudSyncService {
    
    private val gson = Gson()
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
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
     * 测试服务器连接
     */
    suspend fun testConnection(serverUrl: String, username: String, password: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val url = buildUrl(serverUrl, "api/health")
                val request = buildGetRequest(url, username, password)
                val response = client.newCall(request).execute()
                response.isSuccessful
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
                val url = buildUrl(serverUrl, "api/records")
                val request = buildGetRequest(url, username, password)
                val response = client.newCall(request).execute()
                
                if (!response.isSuccessful) {
                    return@withContext Result.failure(IOException("HTTP ${response.code}: ${response.message}"))
                }
                
                val body = response.body?.string() ?: return@withContext Result.failure(IOException("Empty response"))
                val records = gson.fromJson(body, Array<CloudWorkRecord>::class.java)
                Result.success(records?.toList() ?: emptyList())
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
    
    /**
     * 上传记录到云端
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
                        date = record.date,
                        hours = record.hours,
                        isOvertime = record.isOvertime,
                        location = record.location,
                        remark = record.remark,
                        mealSubsidy = record.mealSubsidy,
                        isManual = record.isManual,
                        createdAt = record.createdAt,
                        updatedAt = record.updatedAt
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
                
                // 2. 找出本地需要上传的记录（不在云端或云端没有的）
                val cloudDates = cloudRecords.map { it.date }.toSet()
                val recordsToUpload = localRecords.filter { it.date !in cloudDates }
                
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
                val localDates = localRecords.map { it.date }.toSet()
                val downloadedCount = cloudRecords.count { it.date !in localDates }
                
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
     * 构建API URL
     */
    private fun buildUrl(baseUrl: String, path: String): String {
        val cleanUrl = baseUrl.trimEnd('/')
        return "$cleanUrl/$path"
    }
    
    /**
     * 构建GET请求
     */
    private fun buildGetRequest(url: String, username: String, password: String): Request {
        return Request.Builder()
            .url(url)
            .header("Authorization", Credentials.basic(username, password))
            .get()
            .build()
    }
}
