package com.worklogger.app.data.repository

import com.worklogger.app.data.local.WorkRecordDao
import com.worklogger.app.data.local.QuickPhraseDao
import com.worklogger.app.model.QuickPhrase
import com.worklogger.app.model.WorkRecord
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

class WorkRepository(
    private val workRecordDao: WorkRecordDao,
    private val quickPhraseDao: QuickPhraseDao
) {
    
    val allRecords: Flow<List<WorkRecord>> = workRecordDao.getAllRecords()
    val allPhrases: Flow<List<QuickPhrase>> = quickPhraseDao.getAllPhrases()
    
    val trashRecords: Flow<List<WorkRecord>> = workRecordDao.getTrashRecords()
    
    suspend fun insert(record: WorkRecord) {
        workRecordDao.insert(record)
    }
    
    /**
     * 🔥 优化：记工记录存在判断 - 用SQL直接查，O(1)性能
     * 判断条件：日期(date) + 是否加班(isOvertime) + 是否手动(isManual) + 工时(hours) + 地点(location)
     * 与Docker后端查重逻辑完全一致！
     */
    suspend fun insertIfNotExists(record: WorkRecord): Boolean {
        // SQL直接查重，不用加载同一天所有记录
        val count = workRecordDao.countRecordByKey(
            date = record.date,
            isOvertime = record.isOvertime,
            isManual = record.isManual,
            hours = record.hours,
            location = record.location
        )
        
        if (count == 0) {
            workRecordDao.insert(record)
            return true
        }
        return false
    }
    
    /**
     * 批量插入不重复的记工记录
     * 用于批量记工和云同步
     */
    suspend fun insertAllIfNotExists(records: List<WorkRecord>): Int {
        var insertedCount = 0
        for (record in records) {
            if (insertIfNotExists(record)) {
                insertedCount++
            }
        }
        return insertedCount
    }
    
    suspend fun update(record: WorkRecord) {
        workRecordDao.update(record)
    }
    
    suspend fun delete(record: WorkRecord) {
        workRecordDao.delete(record)
    }
    
    suspend fun deleteAllRecords() {
        workRecordDao.deleteAllRecords()
    }
    
    suspend fun getRecordById(id: Long): WorkRecord? {
        return workRecordDao.getRecordById(id)
    }
    
    suspend fun getRecordsByDate(date: String): List<WorkRecord> {
        return workRecordDao.getRecordsByDate(date)
    }
    
    suspend fun getRecordsByDateRange(startDate: String, endDate: String): List<WorkRecord> {
        return workRecordDao.getRecordsByDateRange(startDate, endDate)
    }
    
    suspend fun getRecordsByMonth(year: Int, month: Int): List<WorkRecord> {
        val startDate = String.format("%04d-%02d-01", year, month)
        val endDate = if (month == 12) {
            String.format("%04d-01-01", year + 1)
        } else {
            String.format("%04d-%02d-01", year, month + 1)
        }
        return workRecordDao.getRecordsByDateRange(startDate, endDate)
    }
    
    // 回收站操作
    suspend fun moveToTrash(record: WorkRecord) {
        workRecordDao.moveToTrash(record.id.toLong())
    }
    
    suspend fun restoreFromTrash(record: WorkRecord) {
        workRecordDao.restoreFromTrash(record.id.toLong())
    }
    
    suspend fun permanentlyDelete(record: WorkRecord) {
        workRecordDao.permanentlyDelete(record.id.toLong())
    }
    
    suspend fun emptyTrash() {
        workRecordDao.emptyTrash()
    }
    
    suspend fun softDeleteRecord(id: Long) {
        workRecordDao.moveToTrash(id)
    }
    
    // ====================== 🔥 统计优化：SQL直接统计，O(1)性能 ======================
    
    /**
     * 优化：用SQL直接统计标准工天数，不用加载全表
     */
    suspend fun getStandardCount(startDate: String, endDate: String): Int {
        return workRecordDao.countStandardDays(startDate, endDate)
    }
    
    /**
     * 优化：用SQL直接统计加班总工时
     */
    suspend fun getOvertimeHours(startDate: String, endDate: String): Double {
        return workRecordDao.sumOvertimeHours(startDate, endDate) ?: 0.0
    }
    
    /**
     * 优化：用SQL直接统计饭补天数
     */
    suspend fun getMealSubsidyCount(startDate: String, endDate: String): Int {
        return workRecordDao.countMealSubsidyDays(startDate, endDate)
    }
    
    /**
     * 优化：用SQL直接统计总工时
     */
    suspend fun getTotalHours(startDate: String, endDate: String): Double {
        return workRecordDao.sumTotalHours(startDate, endDate) ?: 0.0
    }
    
    // 兼容旧接口（基于内存计算）
    suspend fun getStandardCount(records: List<WorkRecord>): Int {
        return records.count { !it.isOvertime && !it.isManual }
    }
    
    suspend fun getOvertimeHours(records: List<WorkRecord>): Double {
        return records.filter { it.isOvertime }.sumOf { it.hours }
    }
    
    suspend fun getMealSubsidyCount(records: List<WorkRecord>): Int {
        return records.count { !it.isOvertime && !it.isManual && it.mealSubsidy }
    }
    
    suspend fun getTotalHours(records: List<WorkRecord>): Double {
        return records.sumOf { it.hours }
    }
    
    suspend fun getAllLocations(): List<String> {
        return workRecordDao.getAllLocations()
    }
    
    suspend fun restoreFromTrashById(id: Int) {
        workRecordDao.restoreFromTrash(id.toLong())
    }
    
    suspend fun permanentlyDeleteById(id: Int) {
        workRecordDao.permanentlyDelete(id.toLong())
    }

    val recentLocations: Flow<List<String>> = allRecords.map { records ->
        records.map { it.location }.distinct().filter { it.isNotBlank() }.sorted()
    }
    
    suspend fun getAllRecordsOnce(): List<WorkRecord> {
        return workRecordDao.getAllRecordsOnce()
    }
    
    suspend fun getAllLocationsOnce(): List<String> {
        return workRecordDao.getAllLocations()
    }
    
    // 快捷短语
    suspend fun addPhrase(phrase: QuickPhrase) {
        quickPhraseDao.insertPhrase(phrase)
    }
    
    suspend fun deletePhrase(phrase: QuickPhrase) {
        quickPhraseDao.deletePhrase(phrase)
    }
    
    suspend fun updatePhrase(phrase: QuickPhrase, newText: String) {
        quickPhraseDao.updatePhrase(phrase.copy(phrase = newText))
    }

    /**
     * 🔥 优化：直接用SQL清理旧回收站记录，不用先查全表
     */
    suspend fun cleanOldTrashRecords(beforeTime: Long) {
        workRecordDao.deleteOldTrashRecords(beforeTime)
    }

    /**
     * 🔥 优化：从云端同步记录 - 用SQL直接查找，O(1)性能
     * 如果本地已存在同日期同类型同工时同地点的记录则更新，否则插入
     */
    suspend fun upsertFromCloud(record: WorkRecord): Boolean {
        // SQL直接查找，不用加载同一天所有记录
        val existingRecord = workRecordDao.findRecordByKey(
            date = record.date,
            isOvertime = record.isOvertime,
            isManual = record.isManual,
            hours = record.hours,
            location = record.location
        )
        
        return if (existingRecord != null) {
            // 本地已存在，检查是否有差异需要更新
            val hasDifference = existingRecord.hours != record.hours ||
                    existingRecord.location != record.location ||
                    existingRecord.remark != record.remark ||
                    existingRecord.mealSubsidy != record.mealSubsidy
            
            if (hasDifference) {
                // 用云端数据覆盖本地记录，保留原始创建时间
                workRecordDao.update(record.copy(id = existingRecord.id, createdAt = existingRecord.createdAt))
            }
            false  // 返回false表示是更新操作
        } else {
            // 本地不存在，直接插入
            workRecordDao.insert(record)
            true  // 返回true表示是插入操作
        }
    }

    /**
     * 修复历史数据：加班记录mealSubsidy应为false，标准工记录mealSubsidy应为true
     */
    suspend fun fixMealSubsidyData(): Int {
        val allRecords = workRecordDao.getAllRecordsOnce()
        var fixedCount = 0
        for (record in allRecords) {
            val correctMealSubsidy = when {
                record.isOvertime -> false
                !record.isOvertime && !record.isManual -> true
                else -> record.mealSubsidy
            }
            if (record.mealSubsidy != correctMealSubsidy) {
                workRecordDao.update(record.copy(mealSubsidy = correctMealSubsidy, updatedAt = System.currentTimeMillis()))
                fixedCount++
            }
        }
        return fixedCount
    }
}
