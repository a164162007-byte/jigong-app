package com.worklogger.app.data.repository

import com.worklogger.app.data.local.AppDatabase
import com.worklogger.app.data.local.QuickPhraseDao
import com.worklogger.app.data.local.WorkRecordDao
import com.worklogger.app.model.QuickPhrase
import com.worklogger.app.model.WorkRecord
import kotlinx.coroutines.flow.Flow

/**
 * 记工数据仓库
 */
class WorkRepository(
    private val workRecordDao: WorkRecordDao,
    private val quickPhraseDao: QuickPhraseDao
) {
    // 记工记录操作
    val allRecords: Flow<List<WorkRecord>> = workRecordDao.getAllRecords()
    
    fun getRecordsByDate(date: String): Flow<List<WorkRecord>> = 
        workRecordDao.getRecordsByDate(date)
    
    fun getRecordsByDateRange(startDate: String, endDate: String): Flow<List<WorkRecord>> =
        workRecordDao.getRecordsByDateRange(startDate, endDate)
    
    fun getRecordsByYearMonth(yearMonth: String): Flow<List<WorkRecord>> =
        workRecordDao.getRecordsByYearMonth(yearMonth)
    
    suspend fun getRecordsByDateSync(date: String): List<WorkRecord> =
        workRecordDao.getRecordsByDateSync(date)
    
    fun getRecentLocations(): Flow<List<String>> = workRecordDao.getRecentLocations()
    
    suspend fun getRecordById(id: Int): WorkRecord? = workRecordDao.getRecordById(id)
    
    val deletedRecords: Flow<List<WorkRecord>> = workRecordDao.getDeletedRecords()
    
    suspend fun getRecordCountByDateRange(startDate: String, endDate: String): Int =
        workRecordDao.getRecordCountByDateRange(startDate, endDate)
    
    suspend fun insertRecord(record: WorkRecord): Long = workRecordDao.insert(record)
    
    suspend fun updateRecord(record: WorkRecord) = workRecordDao.update(record)
    
    suspend fun softDeleteRecord(id: Int) = workRecordDao.softDelete(id)
    
    suspend fun restoreRecord(id: Int) = workRecordDao.restore(id)
    
    suspend fun permanentDeleteRecord(id: Int) = workRecordDao.permanentDelete(id)
    
    suspend fun cleanOldDeleted(beforeTime: Long) = workRecordDao.cleanOldDeleted(beforeTime)
    
    suspend fun clearAllRecords() = workRecordDao.clearAll()
    
    suspend fun getAllRecordsSync(): List<WorkRecord> = workRecordDao.getAllRecordsSync()
    
    suspend fun insertAllRecords(records: List<WorkRecord>) = workRecordDao.insertAll(records)
    
    // 快捷短语操作
    val allPhrases: Flow<List<QuickPhrase>> = quickPhraseDao.getAllPhrases()
    
    suspend fun getPhraseById(id: Int): QuickPhrase? = quickPhraseDao.getPhraseById(id)
    
    suspend fun insertPhrase(phrase: QuickPhrase): Long = quickPhraseDao.insert(phrase)
    
    suspend fun updatePhrase(phrase: QuickPhrase) = quickPhraseDao.update(phrase)
    
    suspend fun incrementPhraseUseCount(id: Int) = quickPhraseDao.incrementUseCount(id)
    
    suspend fun deletePhrase(id: Int) = quickPhraseDao.delete(id)
    
    suspend fun clearAllPhrases() = quickPhraseDao.clearAll()
}
