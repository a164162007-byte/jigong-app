package com.worklogger.app.ui.settings

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.worklogger.app.data.remote.CloudSyncService
import com.worklogger.app.data.repository.SettingsRepository
import com.worklogger.app.data.repository.WorkRepository
import com.worklogger.app.model.QuickPhrase
import com.worklogger.app.model.UserSettings
import com.worklogger.app.model.WorkRecord
import com.worklogger.app.utils.ExcelExporter
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

data class SettingsUiState(
    val settings: UserSettings = UserSettings(),
    val phrases: List<QuickPhrase> = emptyList(),
    val isLoading: Boolean = true,
    val showClearConfirm: Boolean = false,
    val showImportDialog: Boolean = false,
    val exportResult: String? = null,
    val isExporting: Boolean = false,
    // 云同步状态
    val isSyncing: Boolean = false,
    val syncResult: String? = null,
    val cloudServerUrlInput: String = "",
    val cloudUsernameInput: String = "",
    val cloudPasswordInput: String = "",
    val showCloudConfigDialog: Boolean = false
)

class SettingsViewModel(
    private val context: Context,
    private val workRepository: WorkRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()
    
    // 云同步服务
    private val cloudSyncService = CloudSyncService()
    
    init {
        loadData()
    }
    
    private fun loadData() {
        viewModelScope.launch {
            settingsRepository.settings.collect { settings ->
                _uiState.update { 
                    it.copy(
                        settings = settings, 
                        isLoading = false,
                        cloudServerUrlInput = settings.cloudServerUrl,
                        cloudUsernameInput = settings.cloudUsername,
                        cloudPasswordInput = settings.cloudPassword
                    ) 
                }
            }
        }
        
        viewModelScope.launch {
            workRepository.allPhrases.collect { phrases ->
                _uiState.update { it.copy(phrases = phrases) }
            }
        }
    }
    
    fun updateDailyWorkHours(hours: Double) {
        viewModelScope.launch {
            settingsRepository.updateDailyWorkHours(hours)
        }
    }
    
    fun updateOvertimeRate(rate: Double) {
        viewModelScope.launch {
            settingsRepository.updateOvertimeRate(rate)
        }
    }
    
    fun updateDailyWage(wage: Double) {
        viewModelScope.launch {
            settingsRepository.updateDailyWage(wage)
        }
    }
    
    fun updateMonthTarget(target: Double) {
        viewModelScope.launch {
            settingsRepository.updateMonthTarget(target)
        }
    }
    
    fun updateMealSubsidyStandard(standard: Double) {
        viewModelScope.launch {
            settingsRepository.updateMealSubsidyStandard(standard)
        }
    }
    
    fun updateOffWorkTime(time: String) {
        viewModelScope.launch {
            settingsRepository.updateOffWorkTime(time)
        }
    }
    
    fun updateOffWorkReminder(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.updateOffWorkReminder(enabled)
        }
    }
    
    fun updateMissedDayReminder(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.updateMissedDayReminder(enabled)
        }
    }
    
    fun updateTheme(theme: String) {
        viewModelScope.launch {
            settingsRepository.updateTheme(theme)
        }
    }
    
    fun updateBiometricEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.updateBiometricEnabled(enabled)
        }
    }
    
    // 云同步配置方法
    fun showCloudConfigDialog() {
        val settings = _uiState.value.settings
        _uiState.update { 
            it.copy(
                showCloudConfigDialog = true,
                cloudServerUrlInput = settings.cloudServerUrl,
                cloudUsernameInput = settings.cloudUsername,
                cloudPasswordInput = settings.cloudPassword
            ) 
        }
    }
    
    fun hideCloudConfigDialog() {
        _uiState.update { it.copy(showCloudConfigDialog = false) }
    }
    
    fun updateCloudServerUrl(url: String) {
        _uiState.update { it.copy(cloudServerUrlInput = url) }
    }
    
    fun updateCloudUsername(username: String) {
        _uiState.update { it.copy(cloudUsernameInput = username) }
    }
    
    fun updateCloudPassword(password: String) {
        _uiState.update { it.copy(cloudPasswordInput = password) }
    }
    
    fun saveCloudConfig() {
        viewModelScope.launch {
            val url = _uiState.value.cloudServerUrlInput.trim()
            val username = _uiState.value.cloudUsernameInput.trim()
            val password = _uiState.value.cloudPasswordInput
            
            // 保存配置
            settingsRepository.updateCloudServerUrl(url)
            settingsRepository.updateCloudUsername(username)
            settingsRepository.updateCloudPassword(password)
            
            // 测试连接
            val success = cloudSyncService.testConnection(url, username, password)
            if (success) {
                _uiState.update { 
                    it.copy(
                        showCloudConfigDialog = false,
                        syncResult = "连接成功！配置已保存。"
                    ) 
                }
            } else {
                _uiState.update { 
                    it.copy(
                        showCloudConfigDialog = true,
                        syncResult = "连接失败，请检查服务器地址和账号密码是否正确"
                    ) 
                }
            }
        }
    }
    
    fun toggleCloudSync(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.updateCloudSyncEnabled(enabled)
        }
    }
    
    fun syncToCloud() {
        viewModelScope.launch {
            _uiState.update { it.copy(isSyncing = true) }
            
            val settings = _uiState.value.settings
            if (settings.cloudServerUrl.isBlank() || settings.cloudUsername.isBlank()) {
                _uiState.update { 
                    it.copy(isSyncing = false, syncResult = "请先配置云服务器信息") 
                }
                return@launch
            }
            
            // 获取本地所有记录
            val localRecords = workRepository.getAllRecordsSync()
            
            // 执行同步
            val result = cloudSyncService.syncData(
                serverUrl = settings.cloudServerUrl,
                username = settings.cloudUsername,
                password = settings.cloudPassword,
                localRecords = localRecords
            )
            
            if (result.success) {
                // 更新最后同步时间
                settingsRepository.updateCloudLastSyncTime(System.currentTimeMillis())
                
                val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                val syncTime = dateFormat.format(Date())
                
                _uiState.update { 
                    it.copy(
                        isSyncing = false,
                        syncResult = "同步成功！\n上传: ${result.uploadedCount}条\n下载: ${result.downloadedCount}条\n同步时间: $syncTime"
                    ) 
                }
            } else {
                _uiState.update { 
                    it.copy(isSyncing = false, syncResult = "同步失败: ${result.message}") 
                }
            }
        }
    }
    
    fun clearSyncResult() {
        _uiState.update { it.copy(syncResult = null) }
    }
    
    fun addPhrase(phrase: String) {
        viewModelScope.launch {
            if (phrase.isNotBlank()) {
                workRepository.insertPhrase(QuickPhrase(phrase = phrase.trim()))
            }
        }
    }
    
    fun deletePhrase(id: Int) {
        viewModelScope.launch {
            workRepository.deletePhrase(id)
        }
    }
    
    fun incrementPhraseUseCount(id: Int) {
        viewModelScope.launch {
            workRepository.incrementPhraseUseCount(id)
        }
    }
    
    fun exportExcel() {
        viewModelScope.launch {
            _uiState.update { it.copy(isExporting = true) }
            
            val records = workRepository.getAllRecordsSync()
            val exporter = ExcelExporter(context)
            val result = exporter.exportToExcel(records)
            
            result.fold(
                onSuccess = { file ->
                    val intent = exporter.getShareIntent(file)
                    val chooser = Intent.createChooser(intent, "分享Excel文件")
                    chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(chooser)
                    _uiState.update { it.copy(isExporting = false, exportResult = "导出成功") }
                },
                onFailure = { e ->
                    _uiState.update { it.copy(isExporting = false, exportResult = "导出失败: ${e.message}") }
                }
            )
        }
    }
    
    fun exportJson() {
        viewModelScope.launch {
            _uiState.update { it.copy(isExporting = true) }
            
            val records = workRepository.getAllRecordsSync()
            val exporter = ExcelExporter(context)
            val result = exporter.exportToJson(records)
            
            result.fold(
                onSuccess = { file ->
                    val intent = exporter.getShareIntent(file)
                    val chooser = Intent.createChooser(intent, "分享JSON文件")
                    chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(chooser)
                    _uiState.update { it.copy(isExporting = false, exportResult = "导出成功") }
                },
                onFailure = { e ->
                    _uiState.update { it.copy(isExporting = false, exportResult = "导出失败: ${e.message}") }
                }
            )
        }
    }
    
    fun importFromExcel(file: File) {
        viewModelScope.launch {
            val exporter = ExcelExporter(context)
            val result = exporter.importFromExcel(file)
            
            result.fold(
                onSuccess = { records ->
                    workRepository.insertAllRecords(records)
                    _uiState.update { it.copy(showImportDialog = false, exportResult = "导入成功: ${records.size}条记录") }
                },
                onFailure = { e ->
                    _uiState.update { it.copy(showImportDialog = false, exportResult = "导入失败: ${e.message}") }
                }
            )
        }
    }
    
    fun showClearConfirm() {
        _uiState.update { it.copy(showClearConfirm = true) }
    }
    
    fun hideClearConfirm() {
        _uiState.update { it.copy(showClearConfirm = false) }
    }
    
    fun clearAllData() {
        viewModelScope.launch {
            workRepository.clearAllRecords()
            workRepository.clearAllPhrases()
            settingsRepository.clearAllSettings()
            _uiState.update { it.copy(showClearConfirm = false, exportResult = "数据已清空") }
        }
    }
    
    fun clearExportResult() {
        _uiState.update { it.copy(exportResult = null) }
    }
}

class SettingsViewModelFactory(
    private val context: Context,
    private val workRepository: WorkRepository,
    private val settingsRepository: SettingsRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SettingsViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return SettingsViewModel(context, workRepository, settingsRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
