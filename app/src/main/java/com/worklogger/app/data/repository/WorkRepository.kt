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
     * 记工记录存在判断
     * 判断条件：日期(date) + 是否加班(isOvertime, isManual)
     * 用于判断是否重复记工
     *
     * @param record 记工记录
     * @return true 重复记工，false 新增记工
     */
    suspend fun insertIfNotExists(record: WorkRecord): Boolean {
        // 构造唯一键
        val key = "${record.date}_${record.isOvertime}_${record.isManual}"
        
        // 查询所有记工记录
        val allRecords = workRecordDao.getAllRecordsOnce()
        
        // 检查是否存在相同记录
        val exists = allRecords.any { r ->
            "${r.date}_${r.isOvertime}_${r.isManual}" == key
        }
        
        if (!exists) {
            workRecordDao.insert(record)
            return true
        }
        return false
    }
    
    /**
     * 批量插入不重复的记工记录
     * 用于批量记工
     *
     * @param records 记工记录列表
     * @return 插入成功数量
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
    
    suspend fun getRecordsByYear(year: Int): List<WorkRecord> {
        val startDate = String.format("%04d-01-01", year)
        val endDate = String.format("%04d-01-01", year + 1)
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
}
