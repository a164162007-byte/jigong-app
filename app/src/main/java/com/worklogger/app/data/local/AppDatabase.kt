package com.worklogger.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.worklogger.app.model.AdvancePurchaseRecord
import com.worklogger.app.model.AdvanceSalaryRecord
import com.worklogger.app.model.QuickPhrase
import com.worklogger.app.model.WorkRecord

/**
 * Room 数据库
 */
@Database(
    entities = [WorkRecord::class, QuickPhrase::class, AdvanceSalaryRecord::class, AdvancePurchaseRecord::class],
    version = 5,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    
    abstract fun workRecordDao(): WorkRecordDao
    abstract fun quickPhraseDao(): QuickPhraseDao
    abstract fun advanceSalaryDao(): AdvanceSalaryDao
    abstract fun advancePurchaseDao(): AdvancePurchaseDao
    
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
        
        /**
         * Migration 3 -> 4：新增垫资购买记录表
         */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """CREATE TABLE IF NOT EXISTS advance_purchase_records (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        date TEXT NOT NULL,
                        itemName TEXT NOT NULL,
                        location TEXT NOT NULL,
                        amount REAL NOT NULL,
                        quantity INTEGER NOT NULL DEFAULT 1,
                        remark TEXT NOT NULL DEFAULT '',
                        createdAt INTEGER NOT NULL,
                        is_deleted INTEGER NOT NULL DEFAULT 0,
                        deleted_at INTEGER DEFAULT NULL
                    )""".trimIndent()
                )
            }
        }
        
        /**
         * Migration 4 -> 5：添加数据库索引，提升查询性能
         */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // work_records 表索引
                database.execSQL("CREATE INDEX IF NOT EXISTS index_work_records_date ON work_records(date)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_work_records_isOvertime ON work_records(isOvertime)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_work_records_isManual ON work_records(isManual)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_work_records_location ON work_records(location)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_work_records_deleted_at ON work_records(deleted_at)")
                // advance_salary_records 表索引
                database.execSQL("CREATE INDEX IF NOT EXISTS index_advance_salary_records_date ON advance_salary_records(date)")
                // advance_purchase_records 表索引
                database.execSQL("CREATE INDEX IF NOT EXISTS index_advance_purchase_records_date ON advance_purchase_records(date)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_advance_purchase_records_deleted_at ON advance_purchase_records(deleted_at)")
            }
        }
        
        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "work_logger_db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
