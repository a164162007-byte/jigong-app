package com.worklogger.app.data.local

import androidx.room.*
import com.worklogger.app.model.QuickPhrase
import com.worklogger.app.model.WorkRecord
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkRecordDao {
    
    // ====================== WorkRecord 操作 ====================
    
    @Query("SELECT * FROM work_records WHERE deleted_at IS NULL ORDER BY date DESC, id DESC")
    fun getAllRecords(): Flow<List<WorkRecord>>
    
    @Query("SELECT * FROM work_records WHERE deleted_at IS NULL ORDER BY date DESC, id DESC")
    suspend fun getAllRecordsOnce(): List<WorkRecord>
    
    @Query("SELECT * FROM work_records WHERE id = :id")
    suspend fun getRecordById(id: Long): WorkRecord?
    
    @Query("SELECT * FROM work_records WHERE date = :date AND deleted_at IS NULL ORDER BY id DESC")
    suspend fun getRecordsByDate(date: String): List<WorkRecord>
    
    @Query("SELECT * FROM work_records WHERE date >= :startDate AND date < :endDate AND deleted_at IS NULL ORDER BY date DESC, id DESC")
    suspend fun getRecordsByDateRange(startDate: String, endDate: String): List<WorkRecord>
    
    @Query("SELECT * FROM work_records WHERE date >= :startDate AND date < :endDate AND deleted_at IS NULL ORDER BY date DESC")
    suspend fun getRecordsByMonth(startDate: String, endDate: String): List<WorkRecord>
    
    @Query("SELECT * FROM work_records WHERE deleted_at IS NOT NULL ORDER BY deleted_at DESC")
    fun getTrashRecords(): Flow<List<WorkRecord>>
    
    @Query("SELECT DISTINCT location FROM work_records WHERE deleted_at IS NULL ORDER BY location")
    suspend fun getAllLocations(): List<String>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: WorkRecord)
    
    @Update
    suspend fun update(record: WorkRecord)
    
    @Delete
    suspend fun delete(record: WorkRecord)
    
    @Query("DELETE FROM work_records WHERE deleted_at IS NULL")
    suspend fun deleteAllRecords()
    
    @Query("UPDATE work_records SET deleted_at = :timestamp WHERE id = :id")
    suspend fun moveToTrash(id: Long, timestamp: Long = System.currentTimeMillis())
    
    @Query("UPDATE work_records SET deleted_at = NULL WHERE id = :id")
    suspend fun restoreFromTrash(id: Long)
    
    @Query("DELETE FROM work_records WHERE id = :id")
    suspend fun permanentlyDelete(id: Long)
    
    @Query("DELETE FROM work_records WHERE deleted_at IS NOT NULL")
    suspend fun emptyTrash()
    
    @Query("DELETE FROM work_records WHERE deleted_at > 0 AND deleted_at < :timestamp")
    suspend fun deleteOldTrashRecords(timestamp: Long)
}

@Dao
interface QuickPhraseDao {
    
    // ====================== QuickPhrase 操作 ====================
    
    @Query("SELECT * FROM quick_phrases ORDER BY id DESC")
    fun getAllPhrases(): Flow<List<QuickPhrase>>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPhrase(phrase: QuickPhrase)
    
    @Update
    suspend fun updatePhrase(phrase: QuickPhrase)
    
    @Delete
    suspend fun deletePhrase(phrase: QuickPhrase)
}
