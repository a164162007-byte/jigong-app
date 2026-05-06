package com.worklogger.app.data.repository

import com.worklogger.app.data.local.Dao
import com.worklogger.app.data.local.AppDatabase
import com.worklogger.app.model.QuickPhrase
import com.worklogger.app.model.WorkRecord
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

class WorkRepository(private val dao: Dao) {
    
    val allRecords: Flow<List<WorkRecord>> = dao.getAllRecords()
    val allPhrases: Flow<List<QuickPhrase>> = dao.getAllPhrases()
    
    val trashRecords: Flow<List<WorkRecord>> = dao.getTrashRecords()
    
    suspend fun insert(record: WorkRecord) {
        dao.insert(record)
    }
    
    /**
     * 插入记录（如果不存在则插入，存在则跳过）
     * 
     * 判断依据：日期(date) + 记录类型(isOvertime, isManual)
     * 标准工、加班、手动记工可以同时存在于同一天
     * 
     * @param record 要插入的记录
     * @return true 如果成功插入，false 如果记录已存在
     */
    suspend fun insertIfNotExists(record: WorkRecord): Boolean {
        // 构建唯一键
        val key = "${record.date}_${record.isOvertime}_${record.isManual}"
        
        // 获取所有记录
        val allRecords = dao.getAllRecordsOnce()
        
        // 检查是否存在相同键的记录
        val exists = allRecords.any { r ->
            "${r.date}_${r.isOvertime}_${r.isManual}" == key
        }
        
        if (!exists) {
            dao.insert(record)
            return true
        }
        return false
    }
    
    /**
     * 批量插入记录（如果不存在则插入）
     * 
     * @param records 要插入的记录列表
     * @return 实际插入的记录数量
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
        dao.update(record)
    }
    
    suspend fun delete(record: WorkRecord) {
        dao.delete(record)
    }
    
    suspend fun deleteAllRecords() {
        dao.deleteAllRecords()
    }
    
    suspend fun getRecordById(id: Long): WorkRecord? {
        return dao.getRecordById(id)
    }
    
    suspend fun getRecordsByDate(date: String): List<WorkRecord> {
        return dao.getRecordsByDate(date)
    }
    
    suspend fun getRecordsByDateRange(startDate: String, endDate: String): List<WorkRecord> {
        return dao.getRecordsByDateRange(startDate, endDate)
    }
    
    suspend fun getRecordsByMonth(year: Int, month: Int): List<WorkRecord> {
        val startDate = String.format("%04d-%02d-01", year, month)
        val endDate = if (month == 12) {
            String.format("%04d-01-01", year + 1)
        } else {
            String.format("%04d-%02d-01", year, month + 1)
        }
        return dao.getRecordsByDateRange(startDate, endDate)
    }
    
    suspend fun getRecordsByYear(year: Int): List<WorkRecord> {
        val startDate = String.format("%04d-01-01", year)
        val endDate = String.format("%04d-01-01", year + 1)
        return dao.getRecordsByDateRange(startDate, endDate)
    }
    
    // 快捷短语相关
    suspend fun addPhrase(phrase: QuickPhrase) {
        dao.insertPhrase(phrase)
    }
    
    suspend fun deletePhrase(phrase: QuickPhrase) {
        dao.deletePhrase(phrase)
    }
    
    suspend fun updatePhrase(phrase: QuickPhrase) {
        dao.updatePhrase(phrase)
    }
    
    // 回收站相关
    suspend fun moveToTrash(record: WorkRecord) {
        dao.moveToTrash(record.id)
    }
    
    suspend fun restoreFromTrash(record: WorkRecord) {
        dao.restoreFromTrash(record.id)
    }
    
    suspend fun permanentlyDelete(record: WorkRecord) {
        dao.permanentlyDelete(record.id)
    }
    
    suspend fun emptyTrash() {
        dao.emptyTrash()
    }
    
    // 统计相关
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
        return dao.getAllLocations()
    }
}
