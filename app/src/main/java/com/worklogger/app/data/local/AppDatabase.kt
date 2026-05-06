package com.worklogger.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.worklogger.app.model.QuickPhrase
import com.worklogger.app.model.WorkRecord

/**
 * Room 数据库
 */
@Database(
    entities = [WorkRecord::class, QuickPhrase::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    
    abstract fun workRecordDao(): WorkRecordDao
    abstract fun quickPhraseDao(): QuickPhraseDao
    
    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null
        
        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "work_logger_db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
