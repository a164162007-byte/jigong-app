package com.worklogger.app.utils

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

/**
 * APK 下载状态
 */
sealed class DownloadState {
    object Idle : DownloadState()
    data class Downloading(val progress: Int, val downloadedBytes: Long, val totalBytes: Long) : DownloadState()
    object Completed : DownloadState()
    data class Error(val message: String) : DownloadState()
}

/**
 * APK 下载器
 * 用于下载并安装新版本 APK
 */
class UpdateDownloader(private val context: Context) {
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()
    
    private val _downloadState = MutableStateFlow<DownloadState>(DownloadState.Idle)
    val downloadState: StateFlow<DownloadState> = _downloadState.asStateFlow()
    
    private var downloadedFile: File? = null
    
    /**
     * 下载 APK 文件
     * @param downloadUrl APK 下载链接
     * @param fileName 保存的文件名
     */
    suspend fun downloadApk(downloadUrl: String, fileName: String = "jigong-update.apk"): Result<File> = withContext(Dispatchers.IO) {
        try {
            _downloadState.value = DownloadState.Downloading(0, 0, 0)
            
            val request = Request.Builder()
                .url(downloadUrl)
                .header("Accept", "application/vnd.android.package-archive")
                .get()
                .build()
            
            val response = client.newCall(request).execute()
            
            if (!response.isSuccessful) {
                _downloadState.value = DownloadState.Error("下载失败: HTTP ${response.code}")
                return@withContext Result.failure(Exception("下载失败: HTTP ${response.code}"))
            }
            
            val body = response.body
            if (body == null) {
                _downloadState.value = DownloadState.Error("下载失败: 响应为空")
                return@withContext Result.failure(Exception("下载失败: 响应为空"))
            }
            
            val totalBytes = body.contentLength()
            val cacheDir = context.cacheDir
            val apkFile = File(cacheDir, fileName)
            
            // 删除已存在的文件
            if (apkFile.exists()) {
                apkFile.delete()
            }
            
            var downloadedBytes = 0L
            
            body.byteStream().use { inputStream ->
                FileOutputStream(apkFile).use { outputStream ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    
                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        outputStream.write(buffer, 0, bytesRead)
                        downloadedBytes += bytesRead
                        
                        // 更新下载进度
                        val progress = if (totalBytes > 0) {
                            ((downloadedBytes * 100) / totalBytes).toInt()
                        } else {
                            0
                        }
                        
                        _downloadState.value = DownloadState.Downloading(progress, downloadedBytes, totalBytes)
                    }
                }
            }
            
            downloadedFile = apkFile
            _downloadState.value = DownloadState.Completed
            
            Result.success(apkFile)
            
        } catch (e: Exception) {
            _downloadState.value = DownloadState.Error("下载失败: ${e.message}")
            Result.failure(e)
        }
    }
    
    /**
     * 安装 APK
     * @param activity 用于启动安装界面的 Activity
     * @param apkFile 要安装的 APK 文件
     */
    fun installApk(activity: Activity, apkFile: File) {
        try {
            val uri = getApkUri(apkFile)
            val intent = createInstallIntent(uri)
            
            // Android 8.0+ 需要请求安装未知来源权限
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (activity.packageManager.canRequestPackageInstalls()) {
                    activity.startActivity(intent)
                } else {
                    // 请求权限
                    val permissionIntent = Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
                    permissionIntent.data = Uri.parse("package:${activity.packageName}")
                    permissionIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    activity.startActivity(permissionIntent)
                }
            } else {
                activity.startActivity(intent)
            }
            
        } catch (e: Exception) {
            _downloadState.value = DownloadState.Error("安装失败: ${e.message}")
        }
    }
    
    /**
     * 使用 FileProvider 获取 APK 的 Uri
     */
    private fun getApkUri(apkFile: File): Uri {
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile
        )
    }
    
    /**
     * 创建安装 Intent
     */
    private fun createInstallIntent(uri: Uri): Intent {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return intent
    }
    
    /**
     * 重置下载状态
     */
    fun resetState() {
        _downloadState.value = DownloadState.Idle
    }
    
    /**
     * 格式化字节数
     */
    fun formatBytes(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> String.format("%.1f KB", bytes / 1024.0)
            else -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
        }
    }
}
