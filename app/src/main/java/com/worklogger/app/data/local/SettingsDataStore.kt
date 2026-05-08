package com.worklogger.app.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.worklogger.app.model.UserSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/**
 * 设置数据存储
 */
class SettingsDataStore(private val context: Context) {
    
    companion object {
        private val DAILY_WORK_HOURS = doublePreferencesKey("daily_work_hours")
        private val OVERTIME_RATE = doublePreferencesKey("overtime_rate")
    private val OVERTIME_WORK_HOURS = doublePreferencesKey("overtime_work_hours")
        private val DAILY_WAGE = doublePreferencesKey("daily_wage")
        private val MONTH_TARGET = doublePreferencesKey("month_target")
        private val MEAL_SUBSIDY_STANDARD = doublePreferencesKey("meal_subsidy_standard")
        private val OFF_WORK_TIME = stringPreferencesKey("off_work_time")
        private val OFF_WORK_REMINDER = booleanPreferencesKey("off_work_reminder")
        private val MISSED_DAY_REMINDER = booleanPreferencesKey("missed_day_reminder")
        private val THEME = stringPreferencesKey("theme")
        private val BIOMETRIC_ENABLED = booleanPreferencesKey("biometric_enabled")
        // 云同步配置键
        private val CLOUD_SYNC_ENABLED = booleanPreferencesKey("cloud_sync_enabled")
        private val CLOUD_SERVER_URL = stringPreferencesKey("cloud_server_url")
        private val CLOUD_USERNAME = stringPreferencesKey("cloud_username")
        private val CLOUD_PASSWORD = stringPreferencesKey("cloud_password")
        private val CLOUD_LAST_SYNC_TIME = longPreferencesKey("cloud_last_sync_time")
    }
    
    val settings: Flow<UserSettings> = context.dataStore.data.map { preferences ->
        UserSettings(
            dailyWorkHours = preferences[DAILY_WORK_HOURS] ?: 9.0,
            overtimeWorkHours = preferences[OVERTIME_WORK_HOURS] ?: 8.0,
            overtimeRate = preferences[OVERTIME_RATE] ?: 1.5,
            dailyWage = preferences[DAILY_WAGE] ?: 350.0,
            monthTarget = preferences[MONTH_TARGET] ?: 22.0,
            mealSubsidyStandard = preferences[MEAL_SUBSIDY_STANDARD] ?: 30.0,
            offWorkTime = preferences[OFF_WORK_TIME] ?: "18:00",
            offWorkReminder = preferences[OFF_WORK_REMINDER] ?: true,
            missedDayReminder = preferences[MISSED_DAY_REMINDER] ?: true,
            theme = preferences[THEME] ?: "system",
            biometricEnabled = preferences[BIOMETRIC_ENABLED] ?: false,
            // 云同步配置
            cloudSyncEnabled = preferences[CLOUD_SYNC_ENABLED] ?: false,
            cloudServerUrl = preferences[CLOUD_SERVER_URL] ?: "",
            cloudUsername = preferences[CLOUD_USERNAME] ?: "",
            cloudPassword = preferences[CLOUD_PASSWORD] ?: "",
            cloudLastSyncTime = preferences[CLOUD_LAST_SYNC_TIME] ?: 0L
        )
    }
    
    suspend fun updateSettings(settings: UserSettings) {
        context.dataStore.edit { preferences ->
            preferences[DAILY_WORK_HOURS] = settings.dailyWorkHours
            preferences[OVERTIME_WORK_HOURS] = settings.overtimeWorkHours
            preferences[OVERTIME_RATE] = settings.overtimeRate
            preferences[DAILY_WAGE] = settings.dailyWage
            preferences[MONTH_TARGET] = settings.monthTarget
            preferences[MEAL_SUBSIDY_STANDARD] = settings.mealSubsidyStandard
            preferences[OFF_WORK_TIME] = settings.offWorkTime
            preferences[OFF_WORK_REMINDER] = settings.offWorkReminder
            preferences[MISSED_DAY_REMINDER] = settings.missedDayReminder
            preferences[THEME] = settings.theme
            preferences[BIOMETRIC_ENABLED] = settings.biometricEnabled
            // 云同步配置
            preferences[CLOUD_SYNC_ENABLED] = settings.cloudSyncEnabled
            preferences[CLOUD_SERVER_URL] = settings.cloudServerUrl
            preferences[CLOUD_USERNAME] = settings.cloudUsername
            preferences[CLOUD_PASSWORD] = settings.cloudPassword
            preferences[CLOUD_LAST_SYNC_TIME] = settings.cloudLastSyncTime
        }
    }
    
    suspend fun updateDailyWorkHours(hours: Double) {
        context.dataStore.edit { it[DAILY_WORK_HOURS] = hours }
    }
    
    suspend fun updateOvertimeRate(rate: Double) {
        context.dataStore.edit { it[OVERTIME_RATE] = rate }
    }
    
    suspend fun updateOvertimeWorkHours(hours: Double) {
        context.dataStore.edit { it[OVERTIME_WORK_HOURS] = hours }
    }
    
    suspend fun updateDailyWage(wage: Double) {
        context.dataStore.edit { it[DAILY_WAGE] = wage }
    }
    
    suspend fun updateMonthTarget(target: Double) {
        context.dataStore.edit { it[MONTH_TARGET] = target }
    }
    
    suspend fun updateMealSubsidyStandard(standard: Double) {
        context.dataStore.edit { it[MEAL_SUBSIDY_STANDARD] = standard }
    }
    
    suspend fun updateOffWorkTime(time: String) {
        context.dataStore.edit { it[OFF_WORK_TIME] = time }
    }
    
    suspend fun updateOffWorkReminder(enabled: Boolean) {
        context.dataStore.edit { it[OFF_WORK_REMINDER] = enabled }
    }
    
    suspend fun updateMissedDayReminder(enabled: Boolean) {
        context.dataStore.edit { it[MISSED_DAY_REMINDER] = enabled }
    }
    
    suspend fun updateTheme(theme: String) {
        context.dataStore.edit { it[THEME] = theme }
    }
    
    suspend fun updateBiometricEnabled(enabled: Boolean) {
        context.dataStore.edit { it[BIOMETRIC_ENABLED] = enabled }
    }
    
    // 云同步配置方法
    suspend fun updateCloudSyncEnabled(enabled: Boolean) {
        context.dataStore.edit { it[CLOUD_SYNC_ENABLED] = enabled }
    }
    
    suspend fun updateCloudServerUrl(url: String) {
        context.dataStore.edit { it[CLOUD_SERVER_URL] = url }
    }
    
    suspend fun updateCloudUsername(username: String) {
        context.dataStore.edit { it[CLOUD_USERNAME] = username }
    }
    
    suspend fun updateCloudPassword(password: String) {
        context.dataStore.edit { it[CLOUD_PASSWORD] = password }
    }
    
    suspend fun updateCloudLastSyncTime(time: Long) {
        context.dataStore.edit { it[CLOUD_LAST_SYNC_TIME] = time }
    }
    
    suspend fun clearAll() {
        context.dataStore.edit { it.clear() }
    }
}
