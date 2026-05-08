package com.worklogger.app.data.remote

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.worklogger.app.model.WorkRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import java.io.IOException
import java.util.concurrent.TimeUnit

data class CloudWorkRecord(
    val id: Int? = null,
    val date: String,
    val hours: Double,
    val isOvertime: Boolean = false,
    val location: String? = null,
    val remark: String? = null,
    val mealSubsidy: Boolean? = null,
    val isDeleted: Boolean = false
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

    private fun buildUrl(serverUrl: String, path: String): String {
        val base = if (serverUrl.endsWith("/")) serverUrl.removeSuffix("/") else serverUrl
        val pathWithSlash = if (path.startsWith("/")) path.removePrefix("/") else path
        return "$base/$pathWithSlash"
    }

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

    suspend fun fetchRecords(
        serverUrl: String,
        username: String,
        password: String
    ): Result<List<CloudWorkRecord>> {
        return withContext(Dispatchers.IO) {
            try {
                val loginResult = login(serverUrl, username, password)
                if (loginResult.isFailure) {
                    return@withContext Result.failure(loginResult.exceptionOrNull() ?: IOException("登录失败"))
                }

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

    suspend fun uploadRecords(
        serverUrl: String,
        username: String,
        password: String,
        records: List<WorkRecord>
    ): Result<Pair<Int, Int>> {
        return withContext(Dispatchers.IO) {
            try {
                val loginResult = login(serverUrl, username, password)
                if (loginResult.isFailure) {
                    return@withContext Result.failure(loginResult.exceptionOrNull() ?: IOException("登录失败"))
                }

                val url = buildUrl(serverUrl, "api/records")
                val mediaType = MediaType.parse("application/json; charset=utf-8")

                var successCount = 0
                var duplicateCount = 0

                for (record in records) {
                    val cloudRecord = CloudWorkRecord(
                        date = record.date,
                        hours = record.hours,
                        isOvertime = record.isOvertime,
                        location = record.location,
                        remark = record.remark,
                        mealSubsidy = record.mealSubsidy
                    )

                    val body = RequestBody.create(mediaType, gson.toJson(cloudRecord))
                    val request = Request.Builder().url(url).post(body).build()
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

    suspend fun fetchSettings(
        serverUrl: String,
        username: String,
        password: String
    ): Result<List<SettingsData>> {
        return withContext(Dispatchers.IO) {
            try {
                val loginResult = login(serverUrl, username, password)
                if (loginResult.isFailure) {
                    return@withContext Result.failure(loginResult.exceptionOrNull() ?: IOException("登录失败"))
                }

                val url = buildUrl(serverUrl, "api/settings")
                val request = Request.Builder().url(url).get().build()
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

    suspend fun uploadSettings(
        serverUrl: String,
        username: String,
        password: String,
        settings: List<SettingsData>
    ): Result<Boolean> {
        return withContext(Dispatchers.IO) {
            try {
                val loginResult = login(serverUrl, username, password)
                if (loginResult.isFailure) {
                    return@withContext Result.failure(loginResult.exceptionOrNull() ?: IOException("登录失败"))
                }

                val url = buildUrl(serverUrl, "api/settings")
                val mediaType = MediaType.parse("application/json; charset=utf-8")
                val body = RequestBody.create(mediaType, gson.toJson(mapOf("settings" to settings)))
                val request = Request.Builder().url(url).post(body).build()
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

    suspend fun testConnection(serverUrl: String): Result<Boolean> {
        return withContext(Dispatchers.IO) {
            try {
                val url = buildUrl(serverUrl, "api/records")
                val request = Request.Builder().url(url).get().build()
                val response = client.newCall(request).execute()
                // 401可能是正常的（需要登录），只要不是网络错误就说明连接成功
                if (response.isSuccessful || response.code == 401) {
                    Result.success(true)
                } else {
                    Result.success(false)
                }
            } catch (e: Exception) {
                Result.success(false)
            }
        }
    }

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

    suspend fun downloadData(
        serverUrl: String,
        username: String,
        password: String,
        localRecords: List<WorkRecord>
    ): Result<List<CloudWorkRecord>> {
        return fetchRecords(serverUrl, username, password)
    }
}
