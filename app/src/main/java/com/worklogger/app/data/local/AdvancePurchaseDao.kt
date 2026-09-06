package com.worklogger.app.data.local

import androidx.room.*
import com.worklogger.app.model.AdvancePurchaseRecord
import kotlinx.coroutines.flow.Flow

@Dao
interface AdvancePurchaseDao {
    
    @Query("SELECT * FROM advance_purchase_records WHERE deleted_at IS NULL ORDER BY date DESC, id DESC")
    fun getAllPurchaseRecords(): Flow<List<AdvancePurchaseRecord>>
    
    @Query("SELECT * FROM advance_purchase_records WHERE deleted_at IS NULL ORDER BY date DESC, id DESC")
    suspend fun getAllPurchaseRecordsOnce(): List<AdvancePurchaseRecord>
    
    @Query("SELECT * FROM advance_purchase_records WHERE date >= :startDate AND date < :endDate AND deleted_at IS NULL ORDER BY date DESC, id DESC")
    suspend fun getPurchaseRecordsByDateRange(startDate: String, endDate: String): List<AdvancePurchaseRecord>
    
    @Query("SELECT COALESCE(SUM(amount), 0.0) FROM advance_purchase_records WHERE deleted_at IS NULL")
    fun getTotalPurchaseAmount(): Flow<Double>
    
    @Query("SELECT COALESCE(SUM(amount), 0.0) FROM advance_purchase_records WHERE deleted_at IS NULL")
    suspend fun getTotalPurchaseAmountOnce(): Double
    
    @Query("SELECT COALESCE(SUM(amount), 0.0) FROM advance_purchase_records WHERE date >= :startDate AND date < :endDate AND deleted_at IS NULL")
    suspend fun getPurchaseAmountByDateRange(startDate: String, endDate: String): Double
    
    @Query("SELECT DISTINCT location FROM advance_purchase_records WHERE deleted_at IS NULL AND location != '' ORDER BY location")
    suspend fun getAllPurchaseLocations(): List<String>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: AdvancePurchaseRecord)
    
    @Update
    suspend fun update(record: AdvancePurchaseRecord)
    
    @Delete
    suspend fun delete(record: AdvancePurchaseRecord)
    
    @Query("DELETE FROM advance_purchase_records")
    suspend fun deleteAll()
    
    // ====================== 回收站操作 ======================
    
    @Query("UPDATE advance_purchase_records SET deleted_at = :timestamp WHERE id = :id")
    suspend fun moveToTrash(id: Long, timestamp: Long = System.currentTimeMillis())
    
    @Query("UPDATE advance_purchase_records SET deleted_at = NULL WHERE id = :id")
    suspend fun restoreFromTrash(id: Long)
    
    @Query("DELETE FROM advance_purchase_records WHERE id = :id")
    suspend fun permanentlyDelete(id: Long)
    
    @Query("SELECT * FROM advance_purchase_records WHERE deleted_at IS NOT NULL ORDER BY deleted_at DESC")
    fun getTrashRecords(): Flow<List<AdvancePurchaseRecord>>
}
