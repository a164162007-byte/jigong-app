package com.worklogger.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.worklogger.app.model.AdvanceSalaryRecord
import com.worklogger.app.model.QuickPhrase
import com.worklogger.app.model.WorkRecord

/**
 * Room 数据库
 */
@Database(
    entities = [WorkRecord::class, QuickPhrase::class, AdvanceSalaryRecord::class],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    
    abstract fun workRecordDao(): WorkRecordDao
    abstract fun quickPhraseDao(): QuickPhraseDao
    abstract fun advanceSalaryDao(): AdvanceSalaryDao
    
    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null
        
        /**
         * Migration 1 -> 2：新增预支工资表，保留原有数据
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """CREATE TABLE IF NOT EXISTS advance_salary_records (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        date TEXT NOT NULL,
                        time TEXT NOT NULL,
                        location TEXT NOT NULL,
                        amount REAL NOT NULL,
                        remark TEXT NOT NULL DEFAULT '',
                        createdAt INTEGER NOT NULL
                    )""".trimIndent()
                )
            }
        }
        
        /**
         * Migration 2 -> 3：新增地点筛选和年度汇总所需的查询支持
         * 无需修改表结构，仅配合新增的 DAO 查询方法
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // 无 schema 变更，仅版本号递增以支持新查询
            }
        }
        
        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "work_logger_db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
