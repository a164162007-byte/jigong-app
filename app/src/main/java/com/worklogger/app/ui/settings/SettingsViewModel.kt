package com.worklogger.app.ui.settings

import android.app.Activity
import android.content.Context
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.worklogger.app.data.repository.SettingsRepository
import com.worklogger.app.data.repository.WorkRepository
import com.worklogger.app.model.QuickPhrase
import com.worklogger.app.model.UserSettings
import com.worklogger.app.model.WorkRecord
import com.worklogger.app.utils.DataExporter
import com.worklogger.app.utils.DataImporter
import com.worklogger.app.utils.DownloadState
import com.worklogger.app.utils.ParsedWorkEntry
import com.worklogger.app.utils.ReleaseInfo
import com.worklogger.app.utils.TextRecordParser
import com.worklogger.app.utils.UpdateChecker
import com.worklogger.app.utils.UpdateDownloader
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class SettingsUiState(
    val settings: UserSettings = UserSettings(),
    val phrases: List<QuickPhrase> = emptyList(),
    val isLoading: Boolean = true,
    val showClearConfirm: Boolean = false,
    val showImportDialog: Boolean = false,
    val exportResult: String? = null,
    val isExporting: Boolean = false,
    // 导入状态
    val isImporting: Boolean = false,
    val importResult: String? = null,
    val showImportStrategyDialog: Boolean = false,
    val pendingImportRecords: List<WorkRecord>? = null,
    // 粘贴导入状态
    val showBatchImportDialog: Boolean = false,
    // 批量导入分析结果
    val batchImportAnalysis: BatchImportAnalysis? = null,
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

/**
 * 冲突决策
 */
enum class ConflictDecision {
    KEEP_ORIGINAL,  // 保留原有记录
    OVERWRITE       // 用新记录覆盖
}

/**
 * 单条冲突项
 */
data class ConflictItem(
    val newEntry: ParsedWorkEntry,
    val newRecord: WorkRecord,
    val existingRecord: WorkRecord,
    var decision: ConflictDecision = ConflictDecision.KEEP_ORIGINAL
)

/**
 * 批量导入分析结果
 */
data class BatchImportAnalysis(
    val newRecords: List<WorkRecord>,          // 无冲突，可直接导入
    val conflicts: MutableList<ConflictItem>,  // 有冲突，需用户决策
    val failedLines: MutableList<String>,      // 识别失败的行
    val failedLineCorrections: MutableMap<Int, String?> = mutableMapOf()  // index -> 修正后的文本（null表示跳过）
)

class SettingsViewModel(
    private val context: Context,
    private val workRepository: WorkRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()
    
    // 数据导入导出工具
    private val dataExporter = DataExporter(context)
    private val dataImporter = DataImporter(context)
    
    // 更新检查器和下载器
    private val updateChecker = UpdateChecker()
    private val updateDownloader = UpdateDownloader(context)
    
    init {
        loadData()
        viewModelScope.launch {
            updateDownloader.downloadState.collect { state ->
                _uiState.update { it.copy(downloadState = state) }
            }
        }
    }
    
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
                onFailure = { _ -> }
            )
        }
    }
    
    fun getCurrentVersion(): Pair<String, Int> {
        return updateChecker.getCurrentVersion()
    }
    
    fun clearUpdateCheckResult() {
        _uiState.update { it.copy(updateCheckResult = null) }
    }
    
    fun resetDownloadState() {
        updateDownloader.resetState()
    }
    
    private fun loadData() {
        viewModelScope.launch {
            settingsRepository.settings.collect { settings ->
                _uiState.update { 
                    it.copy(
                        settings = settings, 
                        isLoading = false
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
    
    fun updateTheme(theme: String) {
        viewModelScope.launch {
            settingsRepository.updateTheme(theme)
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
    
    // ==================== 数据导入导出相关 ====================
    
    fun exportData(uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isExporting = true, exportResult = null) }
            
            try {
                val settings = _uiState.value.settings
                val records = workRepository.allRecords.first()
                
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
    
    fun getExportFileName(): String {
        return dataExporter.generateFileName()
    }
    
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
    
    fun cancelImport() {
        _uiState.update { 
            it.copy(
                showImportStrategyDialog = false,
                pendingImportRecords = null
            ) 
        }
    }
    
    fun clearImportResult() {
        _uiState.update { it.copy(importResult = null) }
    }
    
    fun clearExportResult() {
        _uiState.update { it.copy(exportResult = null) }
    }

    // ==================== 粘贴导入相关 ====================

    fun showBatchImportDialog() {
        _uiState.update { it.copy(showBatchImportDialog = true) }
    }

    fun hideBatchImportDialog() {
        _uiState.update { it.copy(showBatchImportDialog = false) }
    }

    /**
     * 分析批量导入：区分新增、冲突、识别失败
     */
    fun analyzeBatchImport(entries: List<ParsedWorkEntry>, failedLines: List<String>) {
        viewModelScope.launch {
            val settings = settingsRepository.settings.first()
            val dailyWorkHours = settings.dailyWorkHours
            
            val newRecords = mutableListOf<WorkRecord>()
            val conflicts = mutableListOf<ConflictItem>()
            
            for (entry in entries) {
                val record = if (entry.isOvertime) {
                    WorkRecord(
                        date = entry.date,
                        hours = entry.overtimeHours,
                        isOvertime = true,
                        location = entry.location,
                        remark = "",
                        mealSubsidy = false,
                        isManual = false
                    )
                } else {
                    WorkRecord(
                        date = entry.date,
                        hours = dailyWorkHours,
                        isOvertime = false,
                        location = entry.location,
                        remark = "",
                        mealSubsidy = true,
                        isManual = false
                    )
                }
                
                // 检查同日期同类型是否已有记录（不论是否手动，都视为冲突）
                val existingRecords = workRepository.getRecordsByDate(entry.date)
                val existingMatch = existingRecords.find { it.isOvertime == entry.isOvertime }
                
                if (existingMatch != null) {
                    // 有冲突
                    conflicts.add(ConflictItem(
                        newEntry = entry,
                        newRecord = record,
                        existingRecord = existingMatch
                    ))
                } else {
                    // 无冲突，可直接导入
                    newRecords.add(record)
                }
            }
            
            _uiState.update {
                it.copy(
                    batchImportAnalysis = BatchImportAnalysis(
                        newRecords = newRecords,
                        conflicts = conflicts,
                        failedLines = failedLines.toMutableList()
                    )
                )
            }
        }
    }
    
    /**
     * 更新冲突项的决策
     */
    fun updateConflictDecision(index: Int, decision: ConflictDecision) {
        val analysis = _uiState.value.batchImportAnalysis ?: return
        if (index in analysis.conflicts.indices) {
            analysis.conflicts[index].decision = decision
            _uiState.update { it.copy(batchImportAnalysis = analysis) }
        }
    }
    
    /**
     * 批量设置所有冲突项的决策
     */
    fun setAllConflictDecisions(decision: ConflictDecision) {
        val analysis = _uiState.value.batchImportAnalysis ?: return
        analysis.conflicts.forEach { it.decision = decision }
        _uiState.update { it.copy(batchImportAnalysis = analysis) }
    }
    
    /**
     * 更新识别失败行的修正文本
     */
    fun updateFailedLineCorrection(index: Int, correctedText: String?) {
        val analysis = _uiState.value.batchImportAnalysis ?: return
        if (index in analysis.failedLines.indices) {
            analysis.failedLineCorrections[index] = correctedText
            _uiState.update { it.copy(batchImportAnalysis = analysis) }
        }
    }
    
    /**
     * 跳过所有识别失败的行
     */
    fun skipAllFailedLines() {
        val analysis = _uiState.value.batchImportAnalysis ?: return
        analysis.failedLines.indices.forEach { i ->
            analysis.failedLineCorrections[i] = null
        }
        _uiState.update { it.copy(batchImportAnalysis = analysis) }
    }
    
    /**
     * 重新解析修正后的失败行
     */
    fun reparseFailedLines() {
        viewModelScope.launch {
            val analysis = _uiState.value.batchImportAnalysis ?: return@launch
            val settings = settingsRepository.settings.first()
            val dailyWorkHours = settings.dailyWorkHours
            
            val correctedTexts = analysis.failedLineCorrections
                .filter { it.value != null }
                .map { it.value!! }
                .joinToString("\n")
            
            if (correctedTexts.isBlank()) return@launch
            
            val parseResult = TextRecordParser.parse(correctedTexts, dailyWorkHours = dailyWorkHours)
            
            val updatedNewRecords = analysis.newRecords.toMutableList()
            for (entry in parseResult.entries) {
                val record = if (entry.isOvertime) {
                    WorkRecord(
                        date = entry.date,
                        hours = entry.overtimeHours,
                        isOvertime = true,
                        location = entry.location,
                        remark = "",
                        mealSubsidy = false,
                        isManual = false
                    )
                } else {
                    WorkRecord(
                        date = entry.date,
                        hours = dailyWorkHours,
                        isOvertime = false,
                        location = entry.location,
                        remark = "",
                        mealSubsidy = true,
                        isManual = false
                    )
                }
                updatedNewRecords.add(record)
            }
            
            val updatedAnalysis = BatchImportAnalysis(
                newRecords = updatedNewRecords,
                conflicts = analysis.conflicts,
                failedLines = analysis.failedLines,
                failedLineCorrections = analysis.failedLineCorrections
            )
            
            _uiState.update { it.copy(batchImportAnalysis = updatedAnalysis) }
        }
    }
    
    /**
     * 执行批量导入（根据冲突决策执行）
     */
    fun executeBatchImport() {
        viewModelScope.launch {
            val analysis = _uiState.value.batchImportAnalysis ?: return@launch
            
            var importedCount = 0
            var overwrittenCount = 0
            var skippedCount = 0
            
            try {
                // 1. 导入无冲突的新记录
                for (record in analysis.newRecords) {
                    workRepository.insert(record)
                    importedCount++
                }
                
                // 2. 处理冲突项
                for (conflict in analysis.conflicts) {
                    when (conflict.decision) {
                        ConflictDecision.KEEP_ORIGINAL -> {
                            skippedCount++
                        }
                        ConflictDecision.OVERWRITE -> {
                            // 删除旧记录（软删除），插入新记录
                            workRepository.moveToTrash(conflict.existingRecord.id.toLong())
                            workRepository.insert(conflict.newRecord)
                            overwrittenCount++
                        }
                    }
                }
                
                // 3. 构建结果消息
                val msg = buildString {
                    append("导入完成！")
                    val parts = mutableListOf<String>()
                    if (importedCount > 0) parts.add("新增${importedCount}条")
                    if (overwrittenCount > 0) parts.add("覆盖${overwrittenCount}条")
                    if (skippedCount > 0) parts.add("保留原有${skippedCount}条")
                    if (parts.isEmpty()) parts.add("无变更")
                    append(parts.joinToString("，"))
                    
                    val stillFailed = analysis.failedLines.size - analysis.failedLineCorrections.size
                    if (stillFailed > 0 || analysis.failedLines.isNotEmpty()) {
                        append("，${analysis.failedLines.size}条识别失败")
                    }
                }
                
                _uiState.update {
                    it.copy(
                        showBatchImportDialog = false,
                        batchImportAnalysis = null,
                        importResult = msg
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        importResult = "导入失败：${e.message}"
                    )
                }
            }
        }
    }
    
    /**
     * 清除批量导入分析结果
     */
    fun clearBatchImportAnalysis() {
        _uiState.update { it.copy(batchImportAnalysis = null) }
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
