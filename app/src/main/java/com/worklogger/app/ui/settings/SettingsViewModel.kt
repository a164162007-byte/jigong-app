package com.worklogger.app.ui.settings

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.worklogger.app.data.remote.CloudSyncService
import com.worklogger.app.data.repository.SettingsRepository
import com.worklogger.app.data.repository.WorkRepository
import com.worklogger.app.model.QuickPhrase
import com.worklogger.app.model.UserSettings
import com.worklogger.app.model.WorkRecord
import com.worklogger.app.utils.DataExporter
import com.worklogger.app.utils.DataImporter
import com.worklogger.app.utils.DownloadState
import com.worklogger.app.utils.ExcelExporter
import com.worklogger.app.utils.ReleaseInfo
import com.worklogger.app.utils.UpdateChecker
import com.worklogger.app.utils.UpdateDownloader
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
    val showCloudConfigDialog: Boolean = false,
    // 导入状态
    val isImporting: Boolean = false,
    val importResult: String? = null,
    val showImportStrategyDialog: Boolean = false,
    val pendingImportRecords: List<WorkRecord>? = null,
    // 应用更新状态
    val isCheckingUpdate: Boolean = false,
    val updateCheckResult: UpdateCheckResult? = null,
    val downloadState: DownloadState = DownloadState.Idle,
    val releaseInfo: ReleaseInfo? = null
)

sealed class UpdateCheckResult {
    object NoUpdate : UpdateCheckResult()
    data class UpdateAvailable(val info: ReleaseInfo) : UpdateCheckResult()
    data class Error(val message: String) : UpdateCheckResult()
}

class SettingsViewModel(
    private val context: Context,
    private val workRepository: WorkRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()
    
    // 云同步服务
    private val cloudSyncService = CloudSyncService()
    
    // 数据导入导出工具
    private val dataExporter = DataExporter(context)
    private val dataImporter = DataImporter(context)
    
    // 更新检查器和下载器
    private val updateChecker = UpdateChecker()
    private val updateDownloader = UpdateDownloader(context)
    
    init {
        loadData()
        // 收集下载状态
        viewModelScope.launch {
            updateDownloader.downloadState.collect { state ->
                _uiState.update { it.copy(downloadState = state) }
            }
        }
    }
    
    /**
     * 检查应用更新
     */
    fun checkForUpdate() {
        viewModelScope.launch {
            _uiState.update { it.copy(isCheckingUpdate = true, updateCheckResult = null) }
            
            val result = updateChecker.checkForUpdate()
            
            result.fold(
                onSuccess = { releaseInfo ->
                    val currentVersion = updateChecker.getCurrentVersion().first
                    val isUpdateAvailable = releaseInfo.isNewerThan(currentVersion)
                    
                    if (isUpdateAvailable) {
                        _uiState.update { 
                            it.copy(
                                isCheckingUpdate = false,
                                updateCheckResult = UpdateCheckResult.UpdateAvailable(releaseInfo),
                                releaseInfo = releaseInfo
                            ) 
                        }
                    } else {
                        _uiState.update { 
                            it.copy(
                                isCheckingUpdate = false,
                                updateCheckResult = UpdateCheckResult.NoUpdate
                            ) 
                        }
                    }
                },
                onFailure = { error ->
                    _uiState.update { 
                        it.copy(
                            isCheckingUpdate = false,
                            updateCheckResult = UpdateCheckResult.Error(error.message ?: "检查更新失败")
                        ) 
                    }
                }
            )
        }
    }
    
    /**
     * 下载并安装更新
     * @param activity 用于启动安装界面的 Activity
     */
    fun downloadAndInstallUpdate(activity: Activity) {
        val releaseInfo = _uiState.value.releaseInfo ?: return
        
        viewModelScope.launch {
            val result = updateDownloader.downloadApk(
                downloadUrl = releaseInfo.downloadUrl,
                fileName = "jigong-${releaseInfo.versionName}.apk"
            )
            
            result.fold(
                onSuccess = { apkFile ->
                    updateDownloader.installApk(activity, apkFile)
                },
                onFailure = { error ->
                    // 错误已在 downloadApk 中处理
                }
            )
        }
    }
    
    /**
     * 获取当前版本信息
     */
    fun getCurrentVersion(): Pair<String, Int> {
        return updateChecker.getCurrentVersion()
    }
    
    /**
     * 清除更新检查结果
     */
    fun clearUpdateCheckResult() {
        _uiState.update { it.copy(updateCheckResult = null) }
    }
    
    /**
     * 重置下载状态
     */
    fun resetDownloadState() {
        updateDownloader.resetState()
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
    
    fun updateMealSubsidy(subsidy: Double) {
        viewModelScope.launch {
            settingsRepository.updateMealSubsidy(subsidy)
        }
    }
    
    fun updateDailyWage(wage: Double) {
        viewModelScope.launch {
            settingsRepository.updateDailyWage(wage)
        }
    }
    
    fun updateMonthlyHoursTarget(target: Double) {
        viewModelScope.launch {
            settingsRepository.updateMonthlyHoursTarget(target)
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
            workRepository.deleteAllRecords()
            hideClearConfirm()
        }
    }
    
    // ==================== 云同步相关 ====================
    
    /**
     * 显示云配置对话框
     */
    fun showCloudConfigDialog() {
        _uiState.update { it.copy(showCloudConfigDialog = true) }
    }
    
    /**
     * 隐藏云配置对话框
     */
    fun hideCloudConfigDialog() {
        _uiState.update { it.copy(showCloudConfigDialog = false) }
    }
    
    /**
     * 更新云服务器URL输入
     */
    fun updateCloudServerUrl(url: String) {
        _uiState.update { it.copy(cloudServerUrlInput = url) }
    }
    
    /**
     * 更新云用户名输入
     */
    fun updateCloudUsername(username: String) {
        _uiState.update { it.copy(cloudUsernameInput = username) }
    }
    
    /**
     * 更新云密码输入
     */
    fun updateCloudPassword(password: String) {
        _uiState.update { it.copy(cloudPasswordInput = password) }
    }
    
    /**
     * 保存云同步配置
     */
    fun saveCloudConfig() {
        viewModelScope.launch {
            val state = _uiState.value
            settingsRepository.updateCloudServerUrl(state.cloudServerUrlInput)
            settingsRepository.updateCloudUsername(state.cloudUsernameInput)
            settingsRepository.updateCloudPassword(state.cloudPasswordInput)
            hideCloudConfigDialog()
        }
    }
    
    /**
     * 测试云服务器连接
     */
    fun testCloudConnection() {
        viewModelScope.launch {
            val state = _uiState.value
            val serverUrl = state.cloudServerUrlInput
            
            if (serverUrl.isBlank()) {
                _uiState.update { it.copy(syncResult = "请输入服务器地址") }
                return@launch
            }
            
            _uiState.update { it.copy(syncResult = "正在测试连接...") }
            
            val success = cloudSyncService.testConnection(serverUrl)
            
            _uiState.update { 
                it.copy(
                    syncResult = if (success) "连接成功！" else "连接失败，请检查服务器地址"
                ) 
            }
        }
    }
    
    /**
     * 同步云端数据
     * 上传本地数据，下载云端数据
     */
    fun syncCloudData() {
        viewModelScope.launch {
            val settings = _uiState.value.settings
            val serverUrl = settings.cloudServerUrl
            val username = settings.cloudUsername
            val password = settings.cloudPassword
            
            if (serverUrl.isBlank() || username.isBlank() || password.isBlank()) {
                _uiState.update { it.copy(syncResult = "请先配置云同步参数") }
                return@launch
            }
            
            _uiState.update { it.copy(isSyncing = true, syncResult = "正在同步...") }
            
            // 获取本地所有记录
            val localRecords = workRepository.allRecords.first()
            
            // 执行同步
            val result = cloudSyncService.syncData(serverUrl, username, password, localRecords)
            
            if (result.success) {
                // 下载云端数据到本地
                val downloadResult = cloudSyncService.downloadData(serverUrl, username, password, localRecords)
                
                if (downloadResult.isSuccess) {
                    val cloudRecords = downloadResult.getOrNull() ?: emptyList()
                    
                    // 插入下载的记录
                    var downloadedCount = 0
                    for (record in cloudRecords) {
                        workRepository.insertIfNotExists(record)
                        downloadedCount++
                    }
                    
                    _uiState.update { 
                        it.copy(
                            isSyncing = false,
                            syncResult = "同步完成：上传${result.uploadedCount}条，下载${downloadedCount}条"
                        ) 
                    }
                } else {
                    _uiState.update { 
                        it.copy(
                            isSyncing = false,
                            syncResult = "同步完成，上传${result.uploadedCount}条，下载失败"
                        ) 
                    }
                }
            } else {
                _uiState.update { 
                    it.copy(
                        isSyncing = false,
                        syncResult = "同步失败：${result.message}"
                    ) 
                }
            }
        }
    }
    
    /**
     * 从云端下载数据
     */
    fun downloadFromCloud() {
        viewModelScope.launch {
            val settings = _uiState.value.settings
            val serverUrl = settings.cloudServerUrl
            val username = settings.cloudUsername
            val password = settings.cloudPassword
            
            if (serverUrl.isBlank() || username.isBlank() || password.isBlank()) {
                _uiState.update { it.copy(syncResult = "请先配置云同步参数") }
                return@launch
            }
            
            _uiState.update { it.copy(isSyncing = true, syncResult = "正在下载...") }
            
            // 获取本地所有记录
            val localRecords = workRepository.allRecords.first()
            
            // 下载云端数据
            val result = cloudSyncService.downloadData(serverUrl, username, password, localRecords)
            
            if (result.isSuccess) {
                val cloudRecords = result.getOrNull() ?: emptyList()
                
                // 插入下载的记录
                var downloadedCount = 0
                for (record in cloudRecords) {
                    workRepository.insertIfNotExists(record)
                    downloadedCount++
                }
                
                _uiState.update { 
                    it.copy(
                        isSyncing = false,
                        syncResult = "下载完成：${downloadedCount}条"
                    ) 
                }
            } else {
                _uiState.update { 
                    it.copy(
                        isSyncing = false,
                        syncResult = "下载失败：${result.exceptionOrNull()?.message}"
                    ) 
                }
            }
        }
    }
    
    /**
     * 上传数据到云端
     */
    fun uploadToCloud() {
        viewModelScope.launch {
            val settings = _uiState.value.settings
            val serverUrl = settings.cloudServerUrl
            val username = settings.cloudUsername
            val password = settings.cloudPassword
            
            if (serverUrl.isBlank() || username.isBlank() || password.isBlank()) {
                _uiState.update { it.copy(syncResult = "请先配置云同步参数") }
                return@launch
            }
            
            _uiState.update { it.copy(isSyncing = true, syncResult = "正在上传...") }
            
            // 获取本地所有记录
            val localRecords = workRepository.allRecords.first()
            
            // 上传数据
            val result = cloudSyncService.uploadRecords(serverUrl, username, password, localRecords)
            
            if (result.isSuccess) {
                _uiState.update { 
                    it.copy(
                        isSyncing = false,
                        syncResult = "上传完成：${result.getOrNull()}条"
                    ) 
                }
            } else {
                _uiState.update { 
                    it.copy(
                        isSyncing = false,
                        syncResult = "上传失败：${result.exceptionOrNull()?.message}"
                    ) 
                }
            }
        }
    }
    
    /**
     * 清除同步结果
     */
    fun clearSyncResult() {
        _uiState.update { it.copy(syncResult = null) }
    }
    
    // ==================== 数据导入导出相关 ====================
    
    /**
     * 导出数据到指定URI
     * 
     * @param uri 文件保存的URI
     */
    fun exportData(uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isExporting = true, exportResult = null) }
            
            try {
                val settings = _uiState.value.settings
                val records = workRepository.allRecords.first()
                
                // 构建设置Map
                val settingsMap = mapOf(
                    "daily_hours" to settings.dailyWorkHours,
                    "overtime_rate" to settings.overtimeRate,
                    "meal_subsidy" to settings.mealSubsidy,
                    "daily_wage" to settings.dailyWage,
                    "monthly_hours_target" to settings.monthlyHoursTarget
                )
                
                val result = dataExporter.exportToUri(records, uri, settingsMap)
                
                when (result) {
                    is DataExporter.ExportResult.Success -> {
                        _uiState.update { 
                            it.copy(
                                isExporting = false,
                                exportResult = "导出成功！共${result.recordCount}条记录"
                            ) 
                        }
                    }
                    is DataExporter.ExportResult.Error -> {
                        _uiState.update { 
                            it.copy(
                                isExporting = false,
                                exportResult = "导出失败：${result.message}"
                            ) 
                        }
                    }
                }
            } catch (e: Exception) {
                _uiState.update { 
                    it.copy(
                        isExporting = false,
                        exportResult = "导出失败：${e.message}"
                    ) 
                }
            }
        }
    }
    
    /**
     * 生成导出文件名
     */
    fun getExportFileName(): String {
        return dataExporter.generateFileName()
    }
    
    /**
     * 准备导入数据（从URI读取并解析）
     * 
     * @param uri 文件的URI
     */
    fun prepareImport(uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isImporting = true, importResult = null) }
            
            try {
                val existingRecords = workRepository.allRecords.first()
                
                val result = dataImporter.importFromUri(uri, existingRecords)
                
                when (result) {
                    is DataImporter.ImportResult.Success -> {
                        if (result.importedCount == 0) {
                            _uiState.update { 
                                it.copy(
                                    isImporting = false,
                                    importResult = "没有需要导入的记录（可能已全部存在）"
                                ) 
                            }
                        } else {
                            // 显示策略选择对话框
                            _uiState.update { 
                                it.copy(
                                    isImporting = false,
                                    showImportStrategyDialog = true,
                                    pendingImportRecords = result.records
                                ) 
                            }
                        }
                    }
                    is DataImporter.ImportResult.Error -> {
                        _uiState.update { 
                            it.copy(
                                isImporting = false,
                                importResult = "导入失败：${result.message}"
                            ) 
                        }
                    }
                }
            } catch (e: Exception) {
                _uiState.update { 
                    it.copy(
                        isImporting = false,
                        importResult = "导入失败：${e.message}"
                    ) 
                }
            }
        }
    }
    
    /**
     * 确认导入数据
     * 
     * @param conflictStrategy 冲突处理策略
     */
    fun confirmImport() {
        viewModelScope.launch {
            val records = _uiState.value.pendingImportRecords ?: return@launch
            
            try {
                var importedCount = 0
                for (record in records) {
                    workRepository.insertIfNotExists(record)
                    importedCount++
                }
                
                _uiState.update { 
                    it.copy(
                        showImportStrategyDialog = false,
                        pendingImportRecords = null,
                        importResult = "导入成功！共${importedCount}条记录"
                    ) 
                }
            } catch (e: Exception) {
                _uiState.update { 
                    it.copy(
                        showImportStrategyDialog = false,
                        pendingImportRecords = null,
                        importResult = "导入失败：${e.message}"
                    ) 
                }
            }
        }
    }
    
    /**
     * 取消导入
     */
    fun cancelImport() {
        _uiState.update { 
            it.copy(
                showImportStrategyDialog = false,
                pendingImportRecords = null
            ) 
        }
    }
    
    /**
     * 清除导入结果
     */
    fun clearImportResult() {
        _uiState.update { it.copy(importResult = null) }
    }
    
    /**
     * 清除导出结果
     */
    fun clearExportResult() {
        _uiState.update { it.copy(exportResult = null) }
    }
    
    // ==================== 快捷短语相关 ====================
    
    fun addPhrase(text: String) {
        if (text.isBlank()) return
        viewModelScope.launch {
            workRepository.addPhrase(QuickPhrase(text = text))
        }
    }
    
    fun deletePhrase(phrase: QuickPhrase) {
        viewModelScope.launch {
            workRepository.deletePhrase(phrase)
        }
    }
    
    fun updatePhrase(phrase: QuickPhrase, newText: String) {
        viewModelScope.launch {
            workRepository.updatePhrase(phrase.copy(text = newText))
        }
    }
}

class SettingsViewModelFactory(
    private val context: Context,
    private val workRepository: WorkRepository,
    private val settingsRepository: SettingsRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SettingsViewModel::class.java)) {
            return SettingsViewModel(context, workRepository, settingsRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
