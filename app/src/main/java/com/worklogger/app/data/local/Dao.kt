package com.worklogger.app.data.local

import androidx.room.*
import com.worklogger.app.model.QuickPhrase
import com.worklogger.app.model.WorkRecord
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkRecordDao {
    
    // ====================== WorkRecord 操作 ======================
    
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
    
    @Query("SELECT * FROM work_records WHERE deleted_at IS NOT NULL ORDER BY deleted_at DESC")
    fun getTrashRecords(): Flow<List<WorkRecord>>
    
    @Query("SELECT DISTINCT location FROM work_records WHERE deleted_at IS NULL AND location != '' ORDER BY location")
    suspend fun getAllLocations(): List<String>
    
    // ====================== 优化查重查询 ======================
    
    /**
     * 高效查重查询：按日期+类型+工时+地点匹配
     * 避免全表查询后内存比较，与Docker后端逻辑完全一致
     */
    @Query("SELECT COUNT(*) FROM work_records WHERE date = :date AND is_overtime = :isOvertime AND is_manual = :isManual AND hours = :hours AND location = :location AND deleted_at IS NULL")
    suspend fun countRecordByKey(
        date: String,
        isOvertime: Boolean,
        isManual: Boolean,
        hours: Double,
        location: String
    ): Int
    
    /**
     * 按日期+类型+工时+地点查找记录（用于upsert）
     */
    @Query("SELECT * FROM work_records WHERE date = :date AND is_overtime = :isOvertime AND is_manual = :isManual AND hours = :hours AND location = :location AND deleted_at IS NULL LIMIT 1")
    suspend fun findRecordByKey(
        date: String,
        isOvertime: Boolean,
        isManual: Boolean,
        hours: Double,
        location: String
    ): WorkRecord?
    
    // ====================== 高效批量操作 ======================
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: WorkRecord)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(records: List<WorkRecord>)
    
    @Update
    suspend fun update(record: WorkRecord)
    
    @Delete
    suspend fun delete(record: WorkRecord)
    
    @Query("DELETE FROM work_records WHERE deleted_at IS NULL")
    suspend fun deleteAllRecords()
    
    // ====================== 回收站优化操作 ======================
    
    @Query("UPDATE work_records SET deleted_at = :timestamp WHERE id = :id")
    suspend fun moveToTrash(id: Long, timestamp: Long = System.currentTimeMillis())
    
    @Query("UPDATE work_records SET deleted_at = NULL WHERE id = :id")
    suspend fun restoreFromTrash(id: Long)
    
    @Query("DELETE FROM work_records WHERE id = :id")
    suspend fun permanentlyDelete(id: Long)
    
    @Query("DELETE FROM work_records WHERE deleted_at IS NOT NULL")
    suspend fun emptyTrash()
    
    /**
     * 高效清理旧回收站记录 - 用SQL直接删除，不用先查再删
     */
    @Query("DELETE FROM work_records WHERE deleted_at IS NOT NULL AND deleted_at < :timestamp")
    suspend fun deleteOldTrashRecords(timestamp: Long)
    
    // ====================== 统计优化查询 ======================
    
    /**
     * 高效统计 - 直接用SQL统计标准工天数
     */
    @Query("SELECT COUNT(*) FROM work_records WHERE date >= :startDate AND date < :endDate AND is_overtime = 0 AND is_manual = 0 AND deleted_at IS NULL")
    suspend fun countStandardDays(startDate: String, endDate: String): Int
    
    /**
     * 高效统计加班总工时
     */
    @Query("SELECT SUM(hours) FROM work_records WHERE date >= :startDate AND date < :endDate AND is_overtime = 1 AND deleted_at IS NULL")
    suspend fun sumOvertimeHours(startDate: String, endDate: String): Double?
    
    /**
     * 高效统计饭补天数
     */
    @Query("SELECT COUNT(*) FROM work_records WHERE date >= :startDate AND date < :endDate AND meal_subsidy = 1 AND deleted_at IS NULL")
    suspend fun countMealSubsidyDays(startDate: String, endDate: String): Int
    
    /**
     * 高效统计总工时
     */
    @Query("SELECT SUM(hours) FROM work_records WHERE date >= :startDate AND date < :endDate AND deleted_at IS NULL")
    suspend fun sumTotalHours(startDate: String, endDate: String): Double?
}

@Dao
interface QuickPhraseDao {
    
    // ====================== QuickPhrase 操作 ======================
    
    @Query("SELECT * FROM quick_phrases ORDER BY id DESC")
    fun getAllPhrases(): Flow<List<QuickPhrase>>
    
    @Query("SELECT * FROM quick_phrases WHERE phrase = :phrase LIMIT 1")
    suspend fun findPhraseByText(phrase: String): QuickPhrase?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPhrase(phrase: QuickPhrase)
    
    @Update
    suspend fun updatePhrase(phrase: QuickPhrase)
    
    @Delete
    suspend fun deletePhrase(phrase: QuickPhrase)
}
