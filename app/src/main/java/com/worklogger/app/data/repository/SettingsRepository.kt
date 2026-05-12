package com.worklogger.app.data.repository

import com.worklogger.app.data.local.SettingsDataStore
import com.worklogger.app.model.UserSettings
import kotlinx.coroutines.flow.Flow

/**
 * 设置数据仓库
 */
class SettingsRepository(private val settingsDataStore: SettingsDataStore) {
    
    val settings: Flow<UserSettings> = settingsDataStore.settings
    
    suspend fun updateSettings(settings: UserSettings) = settingsDataStore.updateSettings(settings)
    
    suspend fun updateDailyWorkHours(hours: Double) = settingsDataStore.updateDailyWorkHours(hours)
    
    suspend fun updateOvertimeRate(rate: Double) = settingsDataStore.updateOvertimeRate(rate)
    
    suspend fun updateOvertimeWorkHours(hours: Double) = settingsDataStore.updateOvertimeWorkHours(hours)
    
    suspend fun updateDailyWage(wage: Double) = settingsDataStore.updateDailyWage(wage)
    
    suspend fun updateMonthTarget(target: Double) = settingsDataStore.updateMonthTarget(target)
    
    suspend fun updateMealSubsidyStandard(standard: Double) = 
        settingsDataStore.updateMealSubsidyStandard(standard)
    
    suspend fun updateOffWorkTime(time: String) = settingsDataStore.updateOffWorkTime(time)
    
    suspend fun updateOffWorkReminder(enabled: Boolean) = 
        settingsDataStore.updateOffWorkReminder(enabled)
    
    suspend fun updateMissedDayReminder(enabled: Boolean) = 
        settingsDataStore.updateMissedDayReminder(enabled)
    
    suspend fun updateTheme(theme: String) = settingsDataStore.updateTheme(theme)
    
    suspend fun updateBiometricEnabled(enabled: Boolean) = 
        settingsDataStore.updateBiometricEnabled(enabled)
    
    // 云同步配置方法
    suspend fun updateCloudSyncEnabled(enabled: Boolean) = 
        settingsDataStore.updateCloudSyncEnabled(enabled)
    
    suspend fun updateCloudServerUrl(url: String) = 
        settingsDataStore.updateCloudServerUrl(url)
    
    suspend fun updateCloudUsername(username: String) = 
        settingsDataStore.updateCloudUsername(username)
    
    suspend fun updateCloudPassword(password: String) = 
        settingsDataStore.updateCloudPassword(password)
    
    suspend fun updateCloudLastSyncTime(time: Long) = 
        settingsDataStore.updateCloudLastSyncTime(time)
    
    suspend fun updateCloudLoggedIn(loggedIn: Boolean) = 
        settingsDataStore.updateCloudLoggedIn(loggedIn)
    
    suspend fun updateCloudLoginTime(time: Long) = 
        settingsDataStore.updateCloudLoginTime(time)
    
    suspend fun clearAllSettings() = settingsDataStore.clearAll()
}
