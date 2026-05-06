package com.worklogger.app.data.local

import androidx.room.*
import com.worklogger.app.model.QuickPhrase
import com.worklogger.app.model.WorkRecord
import kotlinx.coroutines.flow.Flow

/**
 * 记工记录 DAO
 */
@Dao
interface WorkRecordDao {
    
    @Query("SELECT * FROM work_records WHERE isDeleted = 0 ORDER BY date DESC, createdAt DESC")
    fun getAllRecords(): Flow<List<WorkRecord>>
    
    @Query("SELECT * FROM work_records WHERE isDeleted = 0 AND date = :date ORDER BY createdAt DESC")
    fun getRecordsByDate(date: String): Flow<List<WorkRecord>>
    
    @Query("SELECT * FROM work_records WHERE isDeleted = 0 AND date BETWEEN :startDate AND :endDate ORDER BY date DESC, createdAt DESC")
    fun getRecordsByDateRange(startDate: String, endDate: String): Flow<List<WorkRecord>>
    
    @Query("SELECT * FROM work_records WHERE isDeleted = 0 AND date LIKE :yearMonth || '%' ORDER BY date DESC, createdAt DESC")
    fun getRecordsByYearMonth(yearMonth: String): Flow<List<WorkRecord>>
    
    @Query("SELECT * FROM work_records WHERE isDeleted = 0 AND date = :date")
    suspend fun getRecordsByDateSync(date: String): List<WorkRecord>
    
    @Query("SELECT DISTINCT location FROM work_records WHERE isDeleted = 0 AND location != '' ORDER BY (SELECT MAX(createdAt) FROM work_records w2 WHERE w2.location = work_records.location) DESC LIMIT 5")
    fun getRecentLocations(): Flow<List<String>>
    
    @Query("SELECT * FROM work_records WHERE id = :id")
    suspend fun getRecordById(id: Int): WorkRecord?
    
    @Query("SELECT * FROM work_records WHERE isDeleted = 1 ORDER BY deletedAt DESC")
    fun getDeletedRecords(): Flow<List<WorkRecord>>
    
    @Query("SELECT COUNT(*) FROM work_records WHERE isDeleted = 0 AND date BETWEEN :startDate AND :endDate")
    suspend fun getRecordCountByDateRange(startDate: String, endDate: String): Int
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: WorkRecord): Long
    
    @Update
    suspend fun update(record: WorkRecord)
    
    @Query("UPDATE work_records SET isDeleted = 1, deletedAt = :deletedAt, updatedAt = :updatedAt WHERE id = :id")
    suspend fun softDelete(id: Int, deletedAt: Long = System.currentTimeMillis(), updatedAt: Long = System.currentTimeMillis())
    
    @Query("UPDATE work_records SET isDeleted = 0, deletedAt = NULL, updatedAt = :updatedAt WHERE id = :id")
    suspend fun restore(id: Int, updatedAt: Long = System.currentTimeMillis())
    
    @Query("DELETE FROM work_records WHERE id = :id")
    suspend fun permanentDelete(id: Int)
    
    @Query("DELETE FROM work_records WHERE isDeleted = 1 AND deletedAt < :beforeTime")
    suspend fun cleanOldDeleted(beforeTime: Long)
    
    @Query("DELETE FROM work_records WHERE isDeleted = 0")
    suspend fun clearAll()
    
    @Query("SELECT * FROM work_records WHERE isDeleted = 0")
    suspend fun getAllRecordsSync(): List<WorkRecord>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(records: List<WorkRecord>)
}

/**
 * 快捷短语 DAO
 */
@Dao
interface QuickPhraseDao {
    
    @Query("SELECT * FROM quick_phrases ORDER BY useCount DESC, createdAt DESC")
    fun getAllPhrases(): Flow<List<QuickPhrase>>
    
    @Query("SELECT * FROM quick_phrases WHERE id = :id")
    suspend fun getPhraseById(id: Int): QuickPhrase?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(phrase: QuickPhrase): Long
    
    @Update
    suspend fun update(phrase: QuickPhrase)
    
    @Query("UPDATE quick_phrases SET useCount = useCount + 1 WHERE id = :id")
    suspend fun incrementUseCount(id: Int)
    
    @Query("DELETE FROM quick_phrases WHERE id = :id")
    suspend fun delete(id: Int)
    
    @Query("DELETE FROM quick_phrases")
    suspend fun clearAll()
}
