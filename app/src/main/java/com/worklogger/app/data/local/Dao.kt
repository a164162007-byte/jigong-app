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
    
    // ====================== 优化查重查询（SQL直接查，O(1)） ======================
    
    /**
     * 高效查重查询：按日期+类型+工时+地点匹配
     * 注意：Room直接用属性名作为列名（驼峰），不是下划线！
     */
    @Query("SELECT COUNT(*) FROM work_records WHERE date = :date AND isOvertime = :isOvertime AND isManual = :isManual AND hours = :hours AND location = :location AND deleted_at IS NULL")
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
    @Query("SELECT * FROM work_records WHERE date = :date AND isOvertime = :isOvertime AND isManual = :isManual AND hours = :hours AND location = :location AND deleted_at IS NULL LIMIT 1")
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
     * 高效清理旧回收站记录 - 用SQL直接删除，不用先查全表
     */
    @Query("DELETE FROM work_records WHERE deleted_at IS NOT NULL AND deleted_at < :timestamp")
    suspend fun deleteOldTrashRecords(timestamp: Long)
    
    // ====================== 统计优化查询（SQL直接统计，不用加载全表） ======================
    
    /**
     * 高效统计标准工天数
     * 注意：列名是驼峰！isOvertime = 0, isManual = 0
     */
    @Query("SELECT COUNT(*) FROM work_records WHERE date >= :startDate AND date < :endDate AND isOvertime = 0 AND isManual = 0 AND deleted_at IS NULL")
    suspend fun countStandardDays(startDate: String, endDate: String): Int
    
    /**
     * 高效统计加班总工时
     * 注意：列名是驼峰！isOvertime = 1
     */
    @Query("SELECT SUM(hours) FROM work_records WHERE date >= :startDate AND date < :endDate AND isOvertime = 1 AND deleted_at IS NULL")
    suspend fun sumOvertimeHours(startDate: String, endDate: String): Double?
    
    /**
     * 高效统计饭补天数
     * 注意：列名是驼峰！mealSubsidy = 1
     */
    @Query("SELECT COUNT(*) FROM work_records WHERE date >= :startDate AND date < :endDate AND mealSubsidy = 1 AND deleted_at IS NULL")
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
