package com.worklogger.app.utils

import android.content.Context
import android.net.Uri
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.worklogger.app.model.WorkRecord
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.*

/**
 * 数据导出工具
 * 导出为与Web端兼容的JSON格式
 */
class DataExporter(private val context: Context) {
    
    private val gson = Gson()
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private val dateTimeFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
    
    /**
     * 导出数据结构 - 与Web端 /api/export 格式兼容
     */
    data class ExportData(
        @SerializedName("app_source")
        val appSource: String = "jigong-app",
        val version: String = "1.0",
        @SerializedName("export_time")
        val exportTime: String,
        val records: List<Map<String, Any?>>,
        val settings: Map<String, Any>? = null,
        val statistics: Map<String, Any>? = null
    )
    
    /**
     * 导出结果
     */
    sealed class ExportResult {
        data class Success(val fileName: String, val recordCount: Int) : ExportResult()
        data class Error(val message: String) : ExportResult()
    }
    
    /**
     * 导出数据为JSON字符串
     * 
     * @param records 要导出的记工记录列表
     * @param settings 用户设置（可选）
     * @param statistics 统计数据（可选）
     * @return JSON字符串
     */
    fun exportToJson(
        records: List<WorkRecord>,
        settings: Map<String, Any>? = null,
        statistics: Map<String, Any>? = null
    ): String {
        // 转换记录为导出格式
        val exportRecords = records.map { record ->
            workRecordToExportMap(record)
        }
        
        val exportData = ExportData(
            appSource = "jigong-app",
            version = "1.0",
            exportTime = dateTimeFormat.format(Date()),
            records = exportRecords,
            settings = settings,
            statistics = statistics
        )
        
        return gson.toJson(exportData)
    }
    
    /**
     * 导出数据到URI（用于文件保存）
     * 
     * @param records 要导出的记工记录列表
     * @param uri 输出URI
     * @param settings 用户设置（可选）
     * @param statistics 统计数据（可选）
     * @return 导出结果
     */
    fun exportToUri(
        records: List<WorkRecord>,
        uri: Uri,
        settings: Map<String, Any>? = null,
        statistics: Map<String, Any>? = null
    ): ExportResult {
        return try {
            val json = exportToJson(records, settings, statistics)
            
            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                outputStream.write(json.toByteArray(Charsets.UTF_8))
            } ?: return ExportResult.Error("无法打开输出流")
            
            ExportResult.Success(
                fileName = generateFileName(),
                recordCount = records.size
            )
        } catch (e: Exception) {
            ExportResult.Error(e.message ?: "导出失败")
        }
    }
    
    /**
     * 导出数据到OutputStream
     * 
     * @param records 要导出的记工记录列表
     * @param outputStream 输出流
     * @param settings 用户设置（可选）
     * @param statistics 统计数据（可选）
     * @return 导出结果
     */
    fun exportToOutputStream(
        records: List<WorkRecord>,
        outputStream: OutputStream,
        settings: Map<String, Any>? = null,
        statistics: Map<String, Any>? = null
    ): ExportResult {
        return try {
            val json = exportToJson(records, settings, statistics)
            outputStream.write(json.toByteArray(Charsets.UTF_8))
            
            ExportResult.Success(
                fileName = generateFileName(),
                recordCount = records.size
            )
        } catch (e: Exception) {
            ExportResult.Error(e.message ?: "导出失败")
        }
    }
    
    /**
     * 将 WorkRecord 转换为导出用的 Map
     */
    private fun workRecordToExportMap(record: WorkRecord): Map<String, Any?> {
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
    
    /**
     * 生成导出文件名
     * 格式：记工数据_YYYYMMDD_HHmmss.json
     */
    fun generateFileName(): String {
        val now = Date()
        val dateStr = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(now)
        return "记工数据_$dateStr.json"
    }
    
    /**
     * 生成默认保存文件名（不带时间戳）
     */
    fun generateDefaultFileName(): String {
        val now = Date()
        val dateStr = dateFormat.format(now).replace("-", "")
        return "记工数据_$dateStr.json"
    }
}
