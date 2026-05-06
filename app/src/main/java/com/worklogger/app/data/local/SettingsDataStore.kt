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
        private val DAILY_WAGE = doublePreferencesKey("daily_wage")
        private val MONTH_TARGET = doublePreferencesKey("month_target")
        private val MEAL_SUBSIDY_STANDARD = doublePreferencesKey("meal_subsidy_standard")
        private val OFF_WORK_TIME = stringPreferencesKey("off_work_time")
        private val OFF_WORK_REMINDER = booleanPreferencesKey("off_work_reminder")
        private val MISSED_DAY_REMINDER = booleanPreferencesKey("missed_day_reminder")
        private val THEME = stringPreferencesKey("theme")
        private val BIOMETRIC_ENABLED = booleanPreferencesKey("biometric_enabled")
    }
    
    val settings: Flow<UserSettings> = context.dataStore.data.map { preferences ->
        UserSettings(
            dailyWorkHours = preferences[DAILY_WORK_HOURS] ?: 8.0,
            overtimeRate = preferences[OVERTIME_RATE] ?: 1.0,
            dailyWage = preferences[DAILY_WAGE] ?: 0.0,
            monthTarget = preferences[MONTH_TARGET] ?: 22.0,
            mealSubsidyStandard = preferences[MEAL_SUBSIDY_STANDARD] ?: 0.0,
            offWorkTime = preferences[OFF_WORK_TIME] ?: "18:00",
            offWorkReminder = preferences[OFF_WORK_REMINDER] ?: true,
            missedDayReminder = preferences[MISSED_DAY_REMINDER] ?: true,
            theme = preferences[THEME] ?: "system",
            biometricEnabled = preferences[BIOMETRIC_ENABLED] ?: false
        )
    }
    
    suspend fun updateSettings(settings: UserSettings) {
        context.dataStore.edit { preferences ->
            preferences[DAILY_WORK_HOURS] = settings.dailyWorkHours
            preferences[OVERTIME_RATE] = settings.overtimeRate
            preferences[DAILY_WAGE] = settings.dailyWage
            preferences[MONTH_TARGET] = settings.monthTarget
            preferences[MEAL_SUBSIDY_STANDARD] = settings.mealSubsidyStandard
            preferences[OFF_WORK_TIME] = settings.offWorkTime
            preferences[OFF_WORK_REMINDER] = settings.offWorkReminder
            preferences[MISSED_DAY_REMINDER] = settings.missedDayReminder
            preferences[THEME] = settings.theme
            preferences[BIOMETRIC_ENABLED] = settings.biometricEnabled
        }
    }
    
    suspend fun updateDailyWorkHours(hours: Double) {
        context.dataStore.edit { it[DAILY_WORK_HOURS] = hours }
    }
    
    suspend fun updateOvertimeRate(rate: Double) {
        context.dataStore.edit { it[OVERTIME_RATE] = rate }
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
    
    suspend fun clearAll() {
        context.dataStore.edit { it.clear() }
    }
}
