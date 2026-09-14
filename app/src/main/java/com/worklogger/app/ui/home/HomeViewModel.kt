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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    val pendingSaveIsManual: Boolean = false,
    // 记工逻辑优化
    val showNoStandardWarning: Boolean = false,
    val showAutoConvertSnackbar: Boolean = false
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
    // 防抖Job
    private var loadDataJob: kotlinx.coroutines.Job? = null
    
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
        val monthStartDate = DateUtils.getYearMonthFirstDay(currentMonth)
        val sevenDaysAgo = DateUtils.getDaysAgo(7)
        // 取更早的日期作为查询起点，确保最近7天的跨月记录不被遗漏
        val startDate = if (sevenDaysAgo < monthStartDate) sevenDaysAgo else monthStartDate
        val endDate = DateUtils.getYearMonthNextFirstDay(currentMonth)
        
        // 使用地点筛选查询
        val records = workRepository.getRecordsByDateRangeAndLocation(startDate, endDate, state.selectedLocation)
        
        // 统计计算只用当月数据
        val monthRecords = records.filter { it.date >= monthStartDate }
        
        // CPU密集型计算移到后台线程
        val calcResult = withContext(Dispatchers.Default) {
            val s = StatsCalculator.calculateStats(
                monthRecords,
                settings.dailyWorkHours,
                settings.overtimeWorkHours,
                settings.mealSubsidyStandard,
                settings.dailyWage
            )
            
            val p = StatsCalculator.calculateProgress(
                s.totalStandard,
                settings.monthTarget
            )
            
            val th = monthRecords.sumOf { it.hours }
            val tw = s.wageTotal + s.mealSubsidyTotal
            
            val md = findMissedDays(records)
            
            val rr = if (state.selectedLocation.isNotEmpty()) {
                records
            } else {
                val sevenDaysAgo = DateUtils.getDaysAgo(7)
                records.filter { it.date >= sevenDaysAgo }
            }
            
            listOf(s, p, th, tw, md, rr)
        }
        
        val stats = calcResult[0] as StatsData
        val progress = calcResult[1] as Float
        val totalHours = calcResult[2] as Double
        val totalWage = calcResult[3] as Double
        @Suppress("UNCHECKED_CAST")
        val missedDays = calcResult[4] as List<String>
        @Suppress("UNCHECKED_CAST")
        val recentRecordsFinal = calcResult[5] as List<WorkRecord>
        
        _uiState.update {
            it.copy(
                totalHours = totalHours,
                totalStandardDays = stats.totalStandard,
                totalWage = totalWage,
                progress = progress,
                recentRecords = recentRecordsFinal,
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
        loadDataJob?.cancel()
        loadDataJob = viewModelScope.launch {
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
                // 检查当天是否有标准工记录
                val hasStandardRecord = existingRecords.any { !it.isOvertime && !it.isManual && !it.isDeleted }
                
                // 记加班但当天没有标准工，强制提醒
                if (isOvertime && !hasStandardRecord) {
                    _uiState.update {
                        it.copy(
                            showNoStandardWarning = true,
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
                
                // 当天已有标准工，再记非加班非手动记录时自动转为加班
                val finalIsOvertime = if (!isOvertime && !isManual && hasStandardRecord) {
                    _uiState.update { it.copy(showAutoConvertSnackbar = true) }
                    true
                } else {
                    isOvertime
                }
                
                _uiState.update { 
                    it.copy(
                        showDuplicateWarning = true, 
                        duplicateDate = date,
                        pendingSaveDate = date,
                        pendingSaveHours = hours,
                        pendingSaveIsOvertime = finalIsOvertime,
                        pendingSaveLocation = location,
                        pendingSaveRemark = remark,
                        pendingSaveMealSubsidy = if (finalIsOvertime) false else mealSubsidy,
                        pendingSaveIsManual = isManual
                    ) 
                }
                return@launch
            }
            
            performSave(date, hours, isOvertime, location, remark, mealSubsidy, isManual, existingRecords)
        }
    }
    
    fun confirmSaveAnyway() {
        val state = _uiState.value
        _uiState.update { it.copy(showHoursWarning = false) }
        viewModelScope.launch {
            val existingRecords = workRepository.getRecordsByDate(state.pendingSaveDate)
            performSave(
                state.pendingSaveDate, state.pendingSaveHours, state.pendingSaveIsOvertime,
                state.pendingSaveLocation, state.pendingSaveRemark, state.pendingSaveMealSubsidy,
                state.pendingSaveIsManual, existingRecords
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
            val existingRecords = workRepository.getRecordsByDate(state.pendingSaveDate)
            performSave(
                state.pendingSaveDate, state.pendingSaveHours, state.pendingSaveIsOvertime,
                state.pendingSaveLocation, state.pendingSaveRemark, state.pendingSaveMealSubsidy,
                state.pendingSaveIsManual, existingRecords
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
    
    fun confirmSaveOvertimeNoStandard() {
        val state = _uiState.value
        _uiState.update { it.copy(showNoStandardWarning = false) }
        viewModelScope.launch {
            val existingRecords = workRepository.getRecordsByDate(state.pendingSaveDate)
            performSave(
                state.pendingSaveDate, state.pendingSaveHours, state.pendingSaveIsOvertime,
                state.pendingSaveLocation, state.pendingSaveRemark, state.pendingSaveMealSubsidy,
                state.pendingSaveIsManual, existingRecords
            )
        }
    }
    
    fun cancelNoStandardWarning() {
        _uiState.update { it.copy(showNoStandardWarning = false) }
    }
    
    fun dismissAutoConvertSnackbar() {
        _uiState.update { it.copy(showAutoConvertSnackbar = false) }
    }
    
    private suspend fun performSave(
        date: String,
        hours: Double,
        isOvertime: Boolean,
        location: String,
        remark: String,
        mealSubsidy: Boolean,
        isManual: Boolean,
        existingRecords: List<WorkRecord> = emptyList()
    ) {
        val editingRecord = _uiState.value.editingRecord
        val settings = settingsRepository.settings.first()
        val dailyWorkHours = settings.dailyWorkHours
        
        // 如果已有标准工记录且当前不是加班/手动记录，自动转为加班
        var finalIsOvertime = isOvertime
        if (!isOvertime && !isManual && editingRecord == null) {
            val hasStandard = existingRecords.any { !it.isOvertime && !it.isManual && !it.isDeleted }
            if (hasStandard) {
                finalIsOvertime = true
                _uiState.update { it.copy(showAutoConvertSnackbar = true) }
            }
        }
        
        val shouldSplit = !finalIsOvertime && hours > dailyWorkHours
        
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
                    finalIsOvertime -> false
                    !finalIsOvertime && !isManual -> true
                    else -> mealSubsidy
                }
                val updated = editingRecord.copy(
                    date = date, hours = hours, isOvertime = finalIsOvertime,
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
                    finalIsOvertime -> false
                    !finalIsOvertime && !isManual -> true
                    else -> mealSubsidy
                }
                val newRecord = WorkRecord(
                    date = date, hours = hours, isOvertime = finalIsOvertime,
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
        loadDataJob?.cancel()
        loadDataJob = viewModelScope.launch {
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
