package com.worklogger.app.utils

import android.content.Context
import android.net.Uri
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.worklogger.app.data.remote.CloudSyncService
import com.worklogger.app.model.WorkRecord
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.Locale

/**
 * 数据导入工具
 * 支持导入与Web端兼容的JSON格式数据
 * 
 * 支持的JSON格式：
 * 1. App导出格式：{"app_source":"jigong-app","records":[...]}
 * 2. Web端导出格式：{"success":true,"data":{"records":[...]}}
 * 3. 简化格式：{"records":[...]}
 * 4. Docker导出格式：{"export_time":...,"export_type":...,"records":[...]}
 * 5. 直接数组格式：[...]
 */
class DataImporter(private val context: Context) {
    
    private val gson = Gson()
    private val cloudSyncService = CloudSyncService
    
    /**
     * 导入结果
     */
    sealed class ImportResult {
        data class Success(
            val totalCount: Int,
            val importedCount: Int,
            val skippedCount: Int,
            val records: List<WorkRecord>
        ) : ImportResult()
        
        data class Error(val message: String) : ImportResult()
    }
    
    /**
     * 导入冲突处理策略
     */
    enum class ConflictStrategy {
        SKIP,       // 跳过重复（默认）
        REPLACE,    // 覆盖已有
        KEEP_BOTH   // 保留两者
    }
    
    /**
     * App导出格式
     */
    data class AppExportFormat(
        @SerializedName("app_source")
        val appSource: String? = null,
        val version: String? = null,
        @SerializedName("export_time")
        val exportTime: String? = null,
        val records: List<Map<String, Any>>? = null
    )
    
    /**
     * Web端导出格式
     */
    data class WebExportFormat(
        val success: Boolean,
        val data: WebData? = null,
        val message: String? = null
    )
    
    data class WebData(
        val records: List<Map<String, Any>>? = null,
        val settings: Map<String, Any>? = null,
        val statistics: Map<String, Any>? = null
    )
    
    /**
     * Docker导出格式
     */
    data class DockerExportFormat(
        @SerializedName("export_time")
        val exportTime: String? = null,
        @SerializedName("export_type")
        val exportType: String? = null,
        val records: List<Map<String, Any>>? = null
    )
    
    /**
     * 从URI导入数据
     * 
     * @param uri 文件URI
     * @param existingRecords 现有的记录（用于去重检测）
     * @param conflictStrategy 冲突处理策略
     * @return 导入结果
     */
    fun importFromUri(
        uri: Uri,
        existingRecords: List<WorkRecord> = emptyList(),
        conflictStrategy: ConflictStrategy = ConflictStrategy.SKIP
    ): ImportResult {
        return try {
            val json = context.contentResolver.openInputStream(uri)?.use { inputStream ->
                BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8)).use { reader ->
                    reader.readText()
                }
            } ?: return ImportResult.Error("无法读取文件")
            
            importFromJson(json, existingRecords, conflictStrategy)
        } catch (e: Exception) {
            ImportResult.Error(e.message ?: "导入失败")
        }
    }
    
    /**
     * 从JSON字符串导入数据
     * 
     * @param json JSON字符串
     * @param existingRecords 现有的记录（用于去重检测）
     * @param conflictStrategy 冲突处理策略
     * @return 导入结果
     */
    fun importFromJson(
        json: String,
        existingRecords: List<WorkRecord> = emptyList(),
        conflictStrategy: ConflictStrategy = ConflictStrategy.SKIP
    ): ImportResult {
        return try {
            // 尝试解析为Docker导出格式
            val dockerFormat = tryParse<DockerExportFormat>(json)
            if (dockerFormat != null && dockerFormat.records != null) {
                return processRecords(
                    dockerFormat.records,
                    existingRecords,
                    conflictStrategy
                )
            }
            
            // 尝试解析为App导出格式
            val appFormat = tryParse<AppExportFormat>(json)
            if (appFormat != null && appFormat.records != null) {
                return processRecords(
                    appFormat.records,
                    existingRecords,
                    conflictStrategy
                )
            }
            
            // 尝试解析为Web端导出格式
            val webFormat = tryParse<WebExportFormat>(json)
            if (webFormat != null && webFormat.success && webFormat.data?.records != null) {
                return processRecords(
                    webFormat.data.records,
                    existingRecords,
                    conflictStrategy
                )
            }
            
            // 尝试解析为直接数组格式
            val directRecords = tryParse<List<Map<String, Any>>>(json)
            if (directRecords != null) {
                return processRecords(
                    directRecords,
                    existingRecords,
                    conflictStrategy
                )
            }
            
            ImportResult.Error("不支持的JSON格式")
        } catch (e: Exception) {
            ImportResult.Error(e.message ?: "JSON格式错误")
        }
    }
    
    /**
     * 处理记录列表
     */
    private fun processRecords(
        rawRecords: List<Map<String, Any>>,
        existingRecords: List<WorkRecord>,
        conflictStrategy: ConflictStrategy
    ): ImportResult {
        val convertedRecords = mutableListOf<WorkRecord>()
        var skippedCount = 0
        
        // 构建已存在记录的key集合
        val existingKeys = when (conflictStrategy) {
            ConflictStrategy.SKIP -> {
                existingRecords.map { getRecordKey(it) }.toSet()
            }
            ConflictStrategy.REPLACE, ConflictStrategy.KEEP_BOTH -> emptySet()
        }
        
        for (rawRecord in rawRecords) {
            try {
                val workRecord = convertToWorkRecord(rawRecord)
                val key = getRecordKey(workRecord)
                
                // 检查是否重复
                if (key in existingKeys) {
                    skippedCount++
                    continue
                }
                
                convertedRecords.add(workRecord)
            } catch (e: Exception) {
                // 跳过无效记录
                skippedCount++
            }
        }
        
        return ImportResult.Success(
            totalCount = rawRecords.size,
            importedCount = convertedRecords.size,
            skippedCount = skippedCount,
            records = convertedRecords
        )
    }
    
    /**
     * 将原始记录Map转换为WorkRecord
     * 
     * 支持的字段映射：
     * - date / work_date -> date
     * - record_type -> isOvertime / isManual
     * - hours -> hours
     * - location -> location
     * - remark / remark_text -> remark
     * - meal_subsidy -> mealSubsidy
     * - created_at / createdAt -> createdAt
     * - updated_at / updatedAt -> updatedAt
     */
    @Suppress("UNCHECKED_CAST")
    private fun convertToWorkRecord(rawRecord: Map<String, Any>): WorkRecord {
        val date = (rawRecord["date"] ?: rawRecord["work_date"])?.toString() ?: ""
        val hours = (rawRecord["hours"] as? Number)?.toDouble() ?: 8.0
        val location = rawRecord["location"]?.toString() ?: ""
        val remark = (rawRecord["remark"] ?: rawRecord["remark_text"])?.toString() ?: ""
        
        // 解析record_type
        val recordType = rawRecord["record_type"]?.toString() ?: "standard"
        val isOvertime = recordType == "overtime"
        val isManual = recordType == "manual"
        
        // 解析meal_subsidy（没有则默认标准工有饭补）
        val mealSubsidy = when (val v = rawRecord["meal_subsidy"]) {
            is Boolean -> v
            is String -> v.toBoolean()
            else -> recordType == "standard"
        }
        
        // 解析时间戳
        val createdAt = parseTimestamp(rawRecord["created_at"] ?: rawRecord["createdAt"])
        val updatedAt = parseTimestamp(rawRecord["updated_at"] ?: rawRecord["updatedAt"])
        
        return WorkRecord(
            date = date,
            hours = hours,
            isOvertime = isOvertime,
            location = location,
            remark = remark,
            mealSubsidy = mealSubsidy,
            isManual = isManual,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }
    
    /**
     * 获取记录唯一键（日期+类型）
     */
    private fun getRecordKey(record: WorkRecord): String {
        val type = when {
            record.isOvertime -> "overtime"
            record.isManual -> "manual"
            else -> "standard"
        }
        return "${record.date}_$type"
    }
    
    /**
     * 解析时间戳
     */
    private fun parseTimestamp(value: Any?): Long {
        if (value == null) return System.currentTimeMillis()
        return when (value) {
            is Number -> value.toLong()
            is String -> {
                if (value.isEmpty()) return System.currentTimeMillis()
                try {
                    // 尝试解析为ISO格式
                    java.time.Instant.parse(value).toEpochMilli()
                } catch (e: Exception) {
                    try {
                        // 尝试解析为 yyyy-MM-dd HH:mm:ss 格式（Docker导出格式）
                        val formatter = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                        formatter.parse(value)?.time ?: System.currentTimeMillis()
                    } catch (e: Exception) {
                        try {
                            // 尝试解析为时间戳字符串
                            value.toLong()
                        } catch (e: Exception) {
                            System.currentTimeMillis()
                        }
                    }
                }
            }
            else -> System.currentTimeMillis()
        }
    }
    
    /**
     * 尝试解析JSON为指定类型
     */
    private inline fun <reified T> tryParse(json: String): T? {
        return try {
            gson.fromJson(json, T::class.java)
        } catch (e: Exception) {
            null
        }
    }
}
