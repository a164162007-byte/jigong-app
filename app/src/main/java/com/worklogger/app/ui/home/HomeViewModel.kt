package com.worklogger.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.worklogger.app.data.repository.SettingsRepository
import com.worklogger.app.data.repository.WorkRepository
import com.worklogger.app.model.StatsData
import com.worklogger.app.model.UserSettings
import com.worklogger.app.model.WorkRecord
import com.worklogger.app.utils.DateUtils
import com.worklogger.app.utils.StatsCalculator
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class HomeUiState(
    val currentMonth: String = DateUtils.currentYearMonth(),
    val totalHours: Double = 0.0,
    val totalStandardDays: Double = 0.0,
    val totalWage: Double = 0.0,
    val progress: Float = 0f,
    val recentRecords: List<WorkRecord> = emptyList(),
    val missedDays: List<String> = emptyList(),
    val recentLocations: List<String> = emptyList(),
    val settings: UserSettings = UserSettings(),
    val isLoading: Boolean = true,
    val showAddDialog: Boolean = false,
    val editingRecord: WorkRecord? = null,
    val showDeleteConfirm: Boolean = false,
    val deleteRecordId: Int? = null,
    val showHoursWarning: Boolean = false,
    val warningHours: Double = 0.0,
    val showDuplicateWarning: Boolean = false,
    val duplicateDate: String = "",
    val showQuickCheckInDialog: Boolean = false,
    // 地点筛选
    val selectedLocation: String = "",
    val allLocations: List<String> = emptyList(),
    // 批量操作
    val isBatchMode: Boolean = false,
    val selectedRecordIds: Set<Int> = emptySet(),
    val showBatchDeleteConfirm: Boolean = false,
    // 待保存的记录参数
    val pendingSaveDate: String = "",
    val pendingSaveHours: Double = 0.0,
    val pendingSaveIsOvertime: Boolean = false,
    val pendingSaveLocation: String = "",
    val pendingSaveRemark: String = "",
    val pendingSaveMealSubsidy: Boolean = false,
    val pendingSaveIsManual: Boolean = false
)

class HomeViewModel(
    private val workRepository: WorkRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()
    
    private var pendingQuickCheckInHours: Double = 0.0
    private var pendingQuickCheckInOvertimeHours: Double = 0.0
    private var pendingQuickCheckInMealSubsidy: Boolean = false
    
    init {
        loadData()
    }
    
    private fun loadData() {
        viewModelScope.launch {
            combine(
                settingsRepository.settings,
                workRepository.recentLocations
            ) { settings, locations ->
                Pair(settings, locations)
            }.collect { (settings, locations) ->
                _uiState.update { it.copy(settings = settings, recentLocations = locations, allLocations = locations) }
                loadMonthlyData(settings)
            }
        }
    }
    
    private suspend fun loadMonthlyData(settings: UserSettings) {
        val state = _uiState.value
        val currentMonth = state.currentMonth
        val startDate = DateUtils.getYearMonthFirstDay(currentMonth)
        val endDate = DateUtils.getYearMonthNextFirstDay(currentMonth)
        
        // 使用地点筛选查询
        val records = workRepository.getRecordsByDateRangeAndLocation(startDate, endDate, state.selectedLocation)
        
        val stats = StatsCalculator.calculateStats(
            records,
            settings.dailyWorkHours,
            settings.overtimeWorkHours,
            settings.mealSubsidyStandard,
            settings.dailyWage
        )
        
        val progress = StatsCalculator.calculateProgress(
            stats.totalStandard,
            settings.monthTarget
        )
        
        val totalHours = records.sumOf { it.hours }
        val totalWage = stats.wageTotal + stats.mealSubsidyTotal
        
        val missedDays = findMissedDays(records)
        
        // 优化：按日期分组取最近7天的完整数据，避免截断破坏分组完整性
        val recentRecords = if (state.selectedLocation.isNotEmpty()) {
            records // 地点筛选时显示全部匹配记录
        } else {
            val sevenDaysAgo = DateUtils.getDaysAgo(7)
            records.filter { it.date >= sevenDaysAgo }
        }
        
        _uiState.update {
            it.copy(
                totalHours = totalHours,
                totalStandardDays = stats.totalStandard,
                totalWage = totalWage,
                progress = progress,
                recentRecords = recentRecords,
                missedDays = missedDays,
                isLoading = false
            )
        }
    }
    
    private fun findMissedDays(records: List<WorkRecord>): List<String> {
        val recordedDates = records.map { it.date }.toHashSet()
        val missedDays = mutableListOf<String>()
        
        for (i in 1..7) {
            val date = DateUtils.getDaysAgo(i)
            if (date !in recordedDates) {
                missedDays.add(date)
            }
        }
        
        return missedDays
    }
    
    // ========== 地点筛选 ==========
    
    fun selectLocation(location: String) {
        _uiState.update { 
            it.copy(
                selectedLocation = if (it.selectedLocation == location) "" else location,
                isLoading = true,
                isBatchMode = false,
                selectedRecordIds = emptySet()
            ) 
        }
        viewModelScope.launch {
            settingsRepository.settings.first().let { settings ->
                loadMonthlyData(settings)
            }
        }
    }
    
    // ========== 批量操作 ==========
    
    fun enterBatchMode() {
        _uiState.update { it.copy(isBatchMode = true, selectedRecordIds = emptySet()) }
    }
    
    fun exitBatchMode() {
        _uiState.update { it.copy(isBatchMode = false, selectedRecordIds = emptySet()) }
    }
    
    fun toggleRecordSelection(recordId: Int) {
        _uiState.update { state ->
            val newSet = state.selectedRecordIds.toMutableSet()
            if (newSet.contains(recordId)) {
                newSet.remove(recordId)
            } else {
                newSet.add(recordId)
            }
            state.copy(selectedRecordIds = newSet)
        }
    }
    
    fun selectAllRecords() {
        val allIds = _uiState.value.recentRecords.map { it.id }.toSet()
        _uiState.update { it.copy(selectedRecordIds = allIds) }
    }
    
    fun showBatchDeleteConfirm() {
        if (_uiState.value.selectedRecordIds.isNotEmpty()) {
            _uiState.update { it.copy(showBatchDeleteConfirm = true) }
        }
    }
    
    fun hideBatchDeleteConfirm() {
        _uiState.update { it.copy(showBatchDeleteConfirm = false) }
    }
    
    fun confirmBatchDelete() {
        viewModelScope.launch {
            val ids = _uiState.value.selectedRecordIds
            for (id in ids) {
                workRepository.softDeleteRecord(id.toLong())
            }
            _uiState.update { 
                it.copy(
                    showBatchDeleteConfirm = false,
                    isBatchMode = false,
                    selectedRecordIds = emptySet()
                ) 
            }
            refreshData()
        }
    }
    
    // ========== 对话框操作 ==========
    
    fun showAddDialog() {
        _uiState.update { it.copy(showAddDialog = true, editingRecord = null) }
    }
    
    fun showEditDialog(record: WorkRecord) {
        _uiState.update { it.copy(showAddDialog = true, editingRecord = record) }
    }
    
    fun hideAddDialog() {
        _uiState.update { it.copy(showAddDialog = false, editingRecord = null) }
    }
    
    fun saveRecord(
        date: String,
        hours: Double,
        isOvertime: Boolean,
        location: String,
        remark: String,
        mealSubsidy: Boolean,
        isManual: Boolean
    ) {
        viewModelScope.launch {
            // 普通记工流程重置一键记工的加班缓存
            pendingQuickCheckInOvertimeHours = 0.0
            
            if (hours > 12 || hours < 1) {
                _uiState.update { 
                    it.copy(
                        showHoursWarning = true, 
                        warningHours = hours,
                        pendingSaveDate = date,
                        pendingSaveHours = hours,
                        pendingSaveIsOvertime = isOvertime,
                        pendingSaveLocation = location,
                        pendingSaveRemark = remark,
                        pendingSaveMealSubsidy = mealSubsidy,
                        pendingSaveIsManual = isManual
                    ) 
                }
                return@launch
            }
            
            val existingRecords = workRepository.getRecordsByDate(date)
            if (existingRecords.isNotEmpty() && _uiState.value.editingRecord == null) {
                _uiState.update { 
                    it.copy(
                        showDuplicateWarning = true, 
                        duplicateDate = date,
                        pendingSaveDate = date,
                        pendingSaveHours = hours,
                        pendingSaveIsOvertime = isOvertime,
                        pendingSaveLocation = location,
                        pendingSaveRemark = remark,
                        pendingSaveMealSubsidy = mealSubsidy,
                        pendingSaveIsManual = isManual
                    ) 
                }
                return@launch
            }
            
            performSave(date, hours, isOvertime, location, remark, mealSubsidy, isManual)
        }
    }
    
    fun confirmSaveAnyway() {
        val state = _uiState.value
        _uiState.update { it.copy(showHoursWarning = false) }
        viewModelScope.launch {
            performSave(
                state.pendingSaveDate, state.pendingSaveHours, state.pendingSaveIsOvertime,
                state.pendingSaveLocation, state.pendingSaveRemark, state.pendingSaveMealSubsidy,
                state.pendingSaveIsManual
            )
        }
    }
    
    fun cancelHoursWarning() {
        _uiState.update { it.copy(showHoursWarning = false) }
    }
    
    fun confirmDuplicateAnyway() {
        val state = _uiState.value
        _uiState.update { it.copy(showDuplicateWarning = false) }
        viewModelScope.launch {
            performSave(
                state.pendingSaveDate, state.pendingSaveHours, state.pendingSaveIsOvertime,
                state.pendingSaveLocation, state.pendingSaveRemark, state.pendingSaveMealSubsidy,
                state.pendingSaveIsManual
            )
            
            // 一键记工带加班时，重复确认后也补上加班记录（无饭补）
            if (pendingQuickCheckInOvertimeHours > 0) {
                val overtimeRecord = WorkRecord(
                    date = state.pendingSaveDate, hours = pendingQuickCheckInOvertimeHours,
                    isOvertime = true, location = state.pendingSaveLocation,
                    remark = "", mealSubsidy = false, isManual = false
                )
                workRepository.insert(overtimeRecord)
                pendingQuickCheckInOvertimeHours = 0.0
                refreshData()
            }
        }
    }
    
    fun cancelDuplicateWarning() {
        _uiState.update { it.copy(showDuplicateWarning = false) }
    }
    
    private suspend fun performSave(
        date: String,
        hours: Double,
        isOvertime: Boolean,
        location: String,
        remark: String,
        mealSubsidy: Boolean,
        isManual: Boolean
    ) {
        val editingRecord = _uiState.value.editingRecord
        val settings = settingsRepository.settings.first()
        val dailyWorkHours = settings.dailyWorkHours
        
        val shouldSplit = !isOvertime && hours > dailyWorkHours
        
        if (editingRecord != null) {
            if (shouldSplit) {
                val updatedStandard = editingRecord.copy(
                    date = date, hours = dailyWorkHours, isOvertime = false,
                    location = location, remark = remark, mealSubsidy = true,
                    isManual = isManual, updatedAt = System.currentTimeMillis()
                )
                val newOvertime = WorkRecord(
                    date = date, hours = hours - dailyWorkHours, isOvertime = true,
                    location = location, remark = "", mealSubsidy = false, isManual = false
                )
                workRepository.update(updatedStandard)
                workRepository.insert(newOvertime)
            } else {
                val finalMealSubsidy = when {
                    isOvertime -> false
                    !isOvertime && !isManual -> true
                    else -> mealSubsidy
                }
                val updated = editingRecord.copy(
                    date = date, hours = hours, isOvertime = isOvertime,
                    location = location, remark = remark, mealSubsidy = finalMealSubsidy,
                    isManual = isManual, updatedAt = System.currentTimeMillis()
                )
                workRepository.update(updated)
            }
        } else {
            if (shouldSplit) {
                val standardRecord = WorkRecord(
                    date = date, hours = dailyWorkHours, isOvertime = false,
                    location = location, remark = remark, mealSubsidy = true, isManual = isManual
                )
                val overtimeRecord = WorkRecord(
                    date = date, hours = hours - dailyWorkHours, isOvertime = true,
                    location = location, remark = "", mealSubsidy = false, isManual = false
                )
                workRepository.insert(standardRecord)
                workRepository.insert(overtimeRecord)
            } else {
                val finalMealSubsidy = when {
                    isOvertime -> false
                    !isOvertime && !isManual -> true
                    else -> mealSubsidy
                }
                val newRecord = WorkRecord(
                    date = date, hours = hours, isOvertime = isOvertime,
                    location = location, remark = remark, mealSubsidy = finalMealSubsidy,
                    isManual = isManual
                )
                workRepository.insert(newRecord)
            }
        }
        
        _uiState.update { it.copy(showAddDialog = false, editingRecord = null) }
        refreshData()
    }
    
    fun quickCheckIn() {
        viewModelScope.launch {
            val settings = _uiState.value.settings
            pendingQuickCheckInHours = settings.dailyWorkHours
            pendingQuickCheckInOvertimeHours = 0.0
            pendingQuickCheckInMealSubsidy = true
            _uiState.update { it.copy(showQuickCheckInDialog = true) }
        }
    }
    
    fun confirmQuickCheckIn(location: String, date: String = DateUtils.today(), overtimeHours: Double = 0.0) {
        viewModelScope.launch {
            // 保存加班工时到类变量，供重复确认时使用
            pendingQuickCheckInOvertimeHours = overtimeHours
            
            val existingRecords = workRepository.getRecordsByDate(date)
            if (existingRecords.isNotEmpty()) {
                _uiState.update {
                    it.copy(
                        showQuickCheckInDialog = false,
                        showDuplicateWarning = true,
                        duplicateDate = date,
                        pendingSaveDate = date,
                        pendingSaveHours = pendingQuickCheckInHours,
                        pendingSaveIsOvertime = false,
                        pendingSaveLocation = location.trim(),
                        pendingSaveRemark = "",
                        pendingSaveMealSubsidy = true,
                        pendingSaveIsManual = false
                    )
                }
                return@launch
            }
            
            val settings = settingsRepository.settings.first()
            val trimmedLocation = location.trim()
            
            // 创建标准工记录（有饭补）
            val standardRecord = WorkRecord(
                date = date, hours = settings.dailyWorkHours, isOvertime = false,
                location = trimmedLocation, remark = "", mealSubsidy = true, isManual = false
            )
            workRepository.insert(standardRecord)
            
            // 如果有加班工时，创建加班记录（无饭补）
            if (overtimeHours > 0) {
                val overtimeRecord = WorkRecord(
                    date = date, hours = overtimeHours, isOvertime = true,
                    location = trimmedLocation, remark = "", mealSubsidy = false, isManual = false
                )
                workRepository.insert(overtimeRecord)
            }
            
            _uiState.update { it.copy(showQuickCheckInDialog = false) }
            refreshData()
        }
    }
    
    fun cancelQuickCheckIn() {
        pendingQuickCheckInOvertimeHours = 0.0
        _uiState.update { it.copy(showQuickCheckInDialog = false) }
    }
    
    fun showDeleteConfirm(recordId: Int) {
        _uiState.update { it.copy(showDeleteConfirm = true, deleteRecordId = recordId) }
    }
    
    fun hideDeleteConfirm() {
        _uiState.update { it.copy(showDeleteConfirm = false, deleteRecordId = null) }
    }
    
    fun confirmDelete() {
        viewModelScope.launch {
            _uiState.value.deleteRecordId?.let { id ->
                workRepository.softDeleteRecord(id.toLong())
            }
            _uiState.update { it.copy(showDeleteConfirm = false, deleteRecordId = null) }
            refreshData()
        }
    }
    
    fun refreshData() {
        _uiState.update { it.copy(isLoading = true) }
        viewModelScope.launch {
            settingsRepository.settings.first().let { settings ->
                loadMonthlyData(settings)
            }
        }
    }
}

class HomeViewModelFactory(
    private val workRepository: WorkRepository,
    private val settingsRepository: SettingsRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(HomeViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return HomeViewModel(workRepository, settingsRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
