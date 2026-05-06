package com.worklogger.app

import android.app.Application
import com.worklogger.app.data.local.AppDatabase
import com.worklogger.app.data.local.SettingsDataStore
import com.worklogger.app.data.repository.SettingsRepository
import com.worklogger.app.data.repository.WorkRepository

/**
 * Application 类
 */
class WorkLoggerApp : Application() {
    
    lateinit var database: AppDatabase
        private set
    
    lateinit var workRepository: WorkRepository
        private set
    
    lateinit var settingsRepository: SettingsRepository
        private set
    
    override fun onCreate() {
        super.onCreate()
        instance = this
        
        // 初始化数据库
        database = AppDatabase.getInstance(this)
        
        // 初始化仓库
        workRepository = WorkRepository(
            database.workRecordDao(),
            database.quickPhraseDao()
        )
        
        settingsRepository = SettingsRepository(SettingsDataStore(this))
    }
    
    companion object {
        lateinit var instance: WorkLoggerApp
            private set
    }
}
