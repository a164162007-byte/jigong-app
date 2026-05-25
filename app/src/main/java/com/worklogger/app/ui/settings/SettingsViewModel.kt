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
    // 改密码状态
    val showChangePasswordDialog: Boolean = false,
    val currentPasswordInput: String = "",
    val newPasswordInput: String = "",
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
    private val cloudSyncService = CloudSyncService
    
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
    
    fun updateOvertimeWorkHours(hours: Double) {
        viewModelScope.launch {
            settingsRepository.updateOvertimeWorkHours(hours)
        }
    }
    
    fun updateMealSubsidyStandard(subsidy: Double) {
        viewModelScope.launch {
            settingsRepository.updateMealSubsidyStandard(subsidy)
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
    /**
     * 注册新用户到云端
     * 先测试连接，再注册，注册成功后自动登录验证
     */
    fun registerToCloud() {
        viewModelScope.launch {
            val state = _uiState.value
            val serverUrl = state.cloudServerUrlInput.trim()
            val username = state.cloudUsernameInput.trim()
            val password = state.cloudPasswordInput
            
            if (serverUrl.isBlank()) {
                _uiState.update { it.copy(syncResult = "请输入服务器地址") }
                return@launch
            }
            
            if (username.isBlank()) {
                _uiState.update { it.copy(syncResult = "请输入用户名") }
                return@launch
            }
            
            if (username.length < 2 || username.length > 20) {
                _uiState.update { it.copy(syncResult = "用户名长度应为2-20个字符") }
                return@launch
            }
            
            if (password.isBlank()) {
                _uiState.update { it.copy(syncResult = "请输入密码") }
                return@launch
            }
            
            if (password.length < 6) {
                _uiState.update { it.copy(syncResult = "密码长度至少6个字符") }
                return@launch
            }
            
            _uiState.update { it.copy(isSyncing = true, syncResult = "正在测试连接...") }
            
            // 先测试连接
            val connectionResult = cloudSyncService.testConnection(serverUrl)
            if (!connectionResult.getOrDefault(false)) {
                val errMsg = connectionResult.exceptionOrNull()?.message ?: "连接失败"
                _uiState.update { it.copy(isSyncing = false, syncResult = "连接失败：$errMsg") }
                return@launch
            }
            
            _uiState.update { it.copy(syncResult = "正在注册...") }
            
            // 注册
            val registerResult = cloudSyncService.register(serverUrl, username, password)
            
            registerResult.fold(
                onSuccess = {
                    // 注册成功，用新账号登录验证
                    _uiState.update { it.copy(syncResult = "注册成功，正在验证登录...") }
                    val loginResult = cloudSyncService.login(serverUrl, username, password)
                    
                    if (loginResult.getOrDefault(false)) {
                        // 登录验证成功，保存配置
                        settingsRepository.updateCloudServerUrl(serverUrl)
                        settingsRepository.updateCloudUsername(username)
                        settingsRepository.updateCloudPassword(password)
                        settingsRepository.updateCloudLoggedIn(true)
                        settingsRepository.updateCloudLoginTime(System.currentTimeMillis())
                        _uiState.update { 
                            it.copy(
                                isSyncing = false,
                                syncResult = "注册成功！已登录为 $username",
                                showCloudConfigDialog = false
                            )
                        }
                    } else {
                        // 登录验证失败，仍然保存配置（注册已成功）
                        settingsRepository.updateCloudServerUrl(serverUrl)
                        settingsRepository.updateCloudUsername(username)
                        settingsRepository.updateCloudPassword(password)
                        _uiState.update { 
                            it.copy(
                                isSyncing = false,
                                syncResult = "注册成功，但登录验证失败，请手动测试连接"
                            )
                        }
                    }
                },
                onFailure = { error ->
                    _uiState.update { 
                        it.copy(
                            isSyncing = false,
                            syncResult = "注册失败：${error.message}"
                        )
                    }
                }
            )
        }
    }

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
            val username = state.cloudUsernameInput
            val password = state.cloudPasswordInput
            
            if (serverUrl.isBlank()) {
                _uiState.update { it.copy(syncResult = "请输入服务器地址") }
                return@launch
            }
            
            if (username.isBlank() || password.isBlank()) {
                _uiState.update { it.copy(syncResult = "请输入用户名和密码") }
                return@launch
            }
            
            _uiState.update { it.copy(isSyncing = true, syncResult = "正在测试连接...") }
            
            // 测试连接
            val connectionResult = cloudSyncService.testConnection(serverUrl)
            
            if (!connectionResult.getOrDefault(false)) {
                _uiState.update { it.copy(isSyncing = false, syncResult = "连接失败，请检查服务器地址") }
                return@launch
            }
            
            _uiState.update { it.copy(syncResult = "正在验证登录...") }
            
            // 测试登录
            val loginResult = cloudSyncService.login(serverUrl, username, password)
            
            if (loginResult.getOrDefault(false)) {
                // 保存登录状态
                settingsRepository.updateCloudLoggedIn(true)
                settingsRepository.updateCloudLoginTime(System.currentTimeMillis())
                _uiState.update { 
                    it.copy(isSyncing = false, syncResult = "连接成功！已登录为 \$username") 
                }
            } else {
                settingsRepository.updateCloudLoggedIn(false)
                _uiState.update { 
                    it.copy(isSyncing = false, syncResult = "登录失败，请检查用户名和密码") 
                }
            }
        }
    }
    
    /**
     * 登录到云端
     */
    fun loginToCloud() {
        viewModelScope.launch {
            val state = _uiState.value
            val serverUrl = state.settings.cloudServerUrl
            val username = state.settings.cloudUsername
            val password = state.settings.cloudPassword
            
            if (serverUrl.isBlank() || username.isBlank() || password.isBlank()) {
                _uiState.update { it.copy(syncResult = "请先配置云同步参数") }
                return@launch
            }
            
            _uiState.update { it.copy(isSyncing = true, syncResult = "正在登录...") }
            
            // 测试连接
            val connectionResult = cloudSyncService.testConnection(serverUrl)
            
            if (!connectionResult.getOrDefault(false)) {
                val errMsg = connectionResult.exceptionOrNull()?.message ?: "连接失败"
                _uiState.update { it.copy(isSyncing = false, syncResult = "连接失败：$errMsg") }
                return@launch
            }
            
            // 测试登录
            val loginResult = cloudSyncService.login(serverUrl, username, password)
            
            if (loginResult.getOrDefault(false)) {
                // 保存登录状态
                settingsRepository.updateCloudLoggedIn(true)
                settingsRepository.updateCloudLoginTime(System.currentTimeMillis())
                _uiState.update { it.copy(isSyncing = false, syncResult = "登录成功！") }
            } else {
                settingsRepository.updateCloudLoggedIn(false)
                val errMsg = loginResult.exceptionOrNull()?.message ?: "登录失败"
                _uiState.update { it.copy(isSyncing = false, syncResult = "登录失败：$errMsg") }
            }
        }
    }
    
    /**
     * 登出云端
     */
    fun logoutFromCloud() {
        viewModelScope.launch {
            settingsRepository.updateCloudLoggedIn(false)
            settingsRepository.updateCloudLoginTime(0L)
            _uiState.update { it.copy(syncResult = "已登出云端") }
        }
    }
    
    /**
     * 显示修改密码对话框
     */
    fun showChangePasswordDialog() {
        _uiState.update { it.copy(
            showChangePasswordDialog = true,
            currentPasswordInput = "",
            newPasswordInput = ""
        ) }
    }
    
    /**
     * 隐藏修改密码对话框
     */
    fun hideChangePasswordDialog() {
        _uiState.update { it.copy(showChangePasswordDialog = false) }
    }
    
    /**
     * 更新当前密码输入
     */
    fun updateCurrentPassword(password: String) {
        _uiState.update { it.copy(currentPasswordInput = password) }
    }
    
    /**
     * 更新新密码输入
     */
    fun updateNewPassword(password: String) {
        _uiState.update { it.copy(newPasswordInput = password) }
    }
    
    /**
     * 修改密码
     */
    fun changePassword() {
        viewModelScope.launch {
            val state = _uiState.value
            val serverUrl = state.settings.cloudServerUrl
            val username = state.settings.cloudUsername
            val password = state.settings.cloudPassword
            val currentPassword = state.currentPasswordInput
            val newPassword = state.newPasswordInput
            
            if (serverUrl.isBlank() || username.isBlank() || password.isBlank()) {
                _uiState.update { it.copy(syncResult = "请先配置云同步参数") }
                return@launch
            }
            
            if (currentPassword.isBlank()) {
                _uiState.update { it.copy(syncResult = "请输入当前密码") }
                return@launch
            }
            
            if (newPassword.isBlank()) {
                _uiState.update { it.copy(syncResult = "请输入新密码") }
                return@launch
            }
            
            if (newPassword.length < 6) {
                _uiState.update { it.copy(syncResult = "新密码长度不能少于6位") }
                return@launch
            }
            
            _uiState.update { it.copy(isSyncing = true, syncResult = "正在修改密码...") }
            
            val result = cloudSyncService.changePassword(serverUrl, username, password, currentPassword, newPassword)
            
            result.fold(
                onSuccess = {
                    // 密码修改成功后，更新本地存储的云同步密码，否则下次同步会用旧密码失败
                    settingsRepository.updateCloudPassword(newPassword)
                    _uiState.update { it.copy(
                        isSyncing = false,
                        cloudPasswordInput = newPassword,
                        syncResult = "密码修改成功！",
                        showChangePasswordDialog = false
                    ) }
                },
                onFailure = { error ->
                    _uiState.update { it.copy(
                        isSyncing = false,
                        syncResult = "修改密码失败：${error.message}"
                    ) }
                }
            )
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
                    
                    // 使用upsertFromCloud处理云端数据（新记录插入，已有记录按需更新）
                    var insertedCount = 0
                    var updatedCount = 0
                    for (record in cloudRecords) {
                        val workRecord = cloudSyncService.run { record.toWorkRecord() }
                        if (workRepository.upsertFromCloud(workRecord)) {
                            insertedCount++
                        } else {
                            updatedCount++
                        }
                    }
                    
                    val updatedInfo = if (updatedCount > 0) "，更新${updatedCount}条" else ""
                    _uiState.update { 
                        it.copy(
                            isSyncing = false,
                            syncResult = "同步完成：上传${result.uploadedCount}条，新增${insertedCount}条${updatedInfo}"
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
                
                // 使用upsertFromCloud处理云端数据（新记录插入，已有记录按需更新）
                var insertedCount = 0
                var updatedCount = 0
                for (record in cloudRecords) {
                    val workRecord = cloudSyncService.run { record.toWorkRecord() }
                    if (workRepository.upsertFromCloud(workRecord)) {
                        insertedCount++
                    } else {
                        updatedCount++
                    }
                }
                
                val updatedInfo = if (updatedCount > 0) "，更新${updatedCount}条" else ""
                _uiState.update { 
                    it.copy(
                        isSyncing = false,
                        syncResult = "下载完成：新增${insertedCount}条${updatedInfo}"
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
                val uploadResult = result.getOrNull() ?: Pair(0, 0)
                _uiState.update { 
                    it.copy(
                        isSyncing = false,
                        syncResult = "上传完成：成功${uploadResult.first}条，失败${uploadResult.second}条"
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
                    "meal_subsidy" to settings.mealSubsidyStandard,
                    "daily_wage" to settings.dailyWage,
                    "monthly_hours_target" to settings.monthTarget
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
            workRepository.addPhrase(QuickPhrase(phrase = text))
        }
    }
    
    fun deletePhrase(phrase: QuickPhrase) {
        viewModelScope.launch {
            workRepository.deletePhrase(phrase)
        }
    }
    
    fun updatePhrase(phrase: QuickPhrase, newText: String) {
        viewModelScope.launch {
            workRepository.updatePhrase(phrase, newText)
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
