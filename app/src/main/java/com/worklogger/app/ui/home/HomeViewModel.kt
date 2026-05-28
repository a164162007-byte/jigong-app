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
    
    // 临时保存一键记工的工时数据
    private var pendingQuickCheckInHours: Double = 0.0
    private var pendingQuickCheckInOvertime: Boolean = false
    private var pendingQuickCheckInMealSubsidy: Boolean = false
    
    init {
        loadData()
    }
    
    /**
     * 🔥 优化：使用combine合并多个Flow，避免多次收集
     */
    private fun loadData() {
        viewModelScope.launch {
            // 合并设置和最近地点，一次性更新UI
            combine(
                settingsRepository.settings,
                workRepository.recentLocations
            ) { settings, locations ->
                Pair(settings, locations)
            }.collect { (settings, locations) ->
                _uiState.update { it.copy(settings = settings, recentLocations = locations) }
                loadMonthlyData(settings)
            }
        }
    }
    
    /**
     * 加载月度数据 - 减少重复计算
     */
    private suspend fun loadMonthlyData(settings: UserSettings) {
        val currentMonth = _uiState.value.currentMonth
        val startDate = DateUtils.getYearMonthFirstDay(currentMonth)
        val endDate = DateUtils.getYearMonthNextFirstDay(currentMonth)
        
        // 一次查询本月所有记录，然后统一计算
        val records = workRepository.getRecordsByDateRange(startDate, endDate)
        
        // 一次性计算所有统计
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
        
        // 计算总工时和总工资
        val totalHours = records.sumOf { it.hours }
        val totalWage = stats.wageTotal + stats.mealSubsidyTotal
        
        // 获取最近7天的漏记日期
        val missedDays = findMissedDays(records)
        
        // 🔥 优化：一次性更新所有UI状态，减少 recompose
        _uiState.update {
            it.copy(
                totalHours = totalHours,
                totalStandardDays = stats.totalStandard,
                totalWage = totalWage,
                progress = progress,
                recentRecords = records.take(10),
                missedDays = missedDays,
                isLoading = false
            )
        }
    }
    
    /**
     * 查找最近7天漏记日期
     */
    private fun findMissedDays(records: List<WorkRecord>): List<String> {
        val recordedDates = records.map { it.date }.toHashSet()  // 🔥 优化：用HashSet，contains更快
        val missedDays = mutableListOf<String>()
        
        for (i in 1..7) {
            val date = DateUtils.getDaysAgo(i)
            if (date !in recordedDates) {
                missedDays.add(date)
            }
        }
        
        return missedDays
    }
    
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
            // 检查工时异常
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
            
            // 检查重复
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
        
        // 自动拆分：当 hours > dailyWorkHours 且类型是标准工或手动折算时，拆分为两条记录
        val shouldSplit = !isOvertime && hours > dailyWorkHours
        
        if (editingRecord != null) {
            if (shouldSplit) {
                // 编辑模式下的拆分：先删原记录，再插入两条新记录
                val updatedStandard = editingRecord.copy(
                    date = date,
                    hours = dailyWorkHours,
                    isOvertime = false,
                    location = location,
                    remark = remark,
                    mealSubsidy = true,
                    isManual = isManual,
                    updatedAt = System.currentTimeMillis()
                )
                val newOvertime = WorkRecord(
                    date = date,
                    hours = hours - dailyWorkHours,
                    isOvertime = true,
                    location = location,
                    remark = "",
                    mealSubsidy = false,
                    isManual = false
                )
                workRepository.update(updatedStandard)
                workRepository.insert(newOvertime)
            } else {
                // 业务规则：标准工强制mealSubsidy=true，加班强制mealSubsidy=false
                // 🔥 与后端逻辑完全一致！
                val finalMealSubsidy = when {
                    isOvertime -> false
                    !isOvertime && !isManual -> true
                    else -> mealSubsidy
                }
                val updated = editingRecord.copy(
                    date = date,
                    hours = hours,
                    isOvertime = isOvertime,
                    location = location,
                    remark = remark,
                    mealSubsidy = finalMealSubsidy,
                    isManual = isManual,
                    updatedAt = System.currentTimeMillis()
                )
                workRepository.update(updated)
            }
        } else {
            if (shouldSplit) {
                // 新增模式下的拆分
                val standardRecord = WorkRecord(
                    date = date,
                    hours = dailyWorkHours,
                    isOvertime = false,
                    location = location,
                    remark = remark,
                    mealSubsidy = true,
                    isManual = isManual
                )
                val overtimeRecord = WorkRecord(
                    date = date,
                    hours = hours - dailyWorkHours,
                    isOvertime = true,
                    location = location,
                    remark = "",
                    mealSubsidy = false,
                    isManual = false
                )
                workRepository.insert(standardRecord)
                workRepository.insert(overtimeRecord)
            } else {
                // 业务规则：标准工强制mealSubsidy=true，加班强制mealSubsidy=false
                // 🔥 与后端逻辑完全一致！
                val finalMealSubsidy = when {
                    isOvertime -> false
                    !isOvertime && !isManual -> true
                    else -> mealSubsidy
                }
                val newRecord = WorkRecord(
                    date = date,
                    hours = hours,
                    isOvertime = isOvertime,
                    location = location,
                    remark = remark,
                    mealSubsidy = finalMealSubsidy,
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
            
            // 保存一键记工的参数
            pendingQuickCheckInHours = settings.dailyWorkHours
            pendingQuickCheckInOvertime = false
            pendingQuickCheckInMealSubsidy = true
            
            // 始终弹出对话框，强制输入工地名称
            _uiState.update { it.copy(showQuickCheckInDialog = true) }
        }
    }
    
    fun confirmQuickCheckIn(location: String, date: String = DateUtils.today()) {
        viewModelScope.launch {
            // 检查该日期是否已有记录
            val existingRecords = workRepository.getRecordsByDate(date)
            if (existingRecords.isNotEmpty()) {
                // 保存待确认参数，弹出重复提醒
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
            
            // 一键记工：标准工（标准工时），强制饭补=true
            val record = WorkRecord(
                date = date,
                hours = pendingQuickCheckInHours,
                isOvertime = false,
                location = location.trim(),
                remark = "",
                mealSubsidy = true,
                isManual = false
            )
            
            workRepository.insert(record)
            _uiState.update { it.copy(showQuickCheckInDialog = false) }
            refreshData()
        }
    }
    
    fun cancelQuickCheckIn() {
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
