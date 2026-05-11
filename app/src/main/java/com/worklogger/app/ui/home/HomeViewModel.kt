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
    val showQuickCheckInDialog: Boolean = false  // 一键记工需要输入工地名称
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
    
    private fun loadData() {
        viewModelScope.launch {
            // 收集设置
            settingsRepository.settings.collect { settings ->
                _uiState.update { it.copy(settings = settings) }
                loadMonthlyData(settings)
            }
        }
        
        viewModelScope.launch {
            // 收集最近地点
            workRepository.recentLocations.collect { locations ->
                _uiState.update { it.copy(recentLocations = locations) }
            }
        }
    }
    
    private suspend fun loadMonthlyData(settings: UserSettings) {
        val currentMonth = _uiState.value.currentMonth
        val startDate = DateUtils.getYearMonthFirstDay(currentMonth)
        val endDate = DateUtils.getYearMonthLastDay(currentMonth)
        
        val records = workRepository.getRecordsByDateRange(startDate, endDate)
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
        
        // 计算总工时（标准+加班）
        val totalHours = records.sumOf { it.hours }
        
        // 计算总工资
        val totalWage = stats.wageTotal + stats.mealSubsidyTotal
        
        // 获取最近7天的漏记日期
        val missedDays = findMissedDays(records, settings)
        
        _uiState.update {
            it.copy(
                totalHours = totalHours,
                totalWage = totalWage,
                progress = progress,
                recentRecords = records.take(10),
                missedDays = missedDays,
                isLoading = false
            )
        }
    }
    
    private fun findMissedDays(records: List<WorkRecord>, settings: UserSettings): List<String> {
        val recordedDates = records.map { it.date }.toSet()
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
            if (hours > 12 || (hours < 1 && hours > 0)) {
                _uiState.update { it.copy(showHoursWarning = true, warningHours = hours) }
                return@launch
            }
            
            // 检查重复
            val existingRecords = workRepository.getRecordsByDate(date)
            if (existingRecords.isNotEmpty() && _uiState.value.editingRecord == null) {
                _uiState.update { it.copy(showDuplicateWarning = true, duplicateDate = date) }
                return@launch
            }
            
            performSave(date, hours, isOvertime, location, remark, mealSubsidy, isManual)
        }
    }
    
    fun confirmSaveAnyway(
        date: String,
        hours: Double,
        isOvertime: Boolean,
        location: String,
        remark: String,
        mealSubsidy: Boolean,
        isManual: Boolean
    ) {
        _uiState.update { it.copy(showHoursWarning = false) }
        viewModelScope.launch {
            performSave(date, hours, isOvertime, location, remark, mealSubsidy, isManual)
        }
    }
    
    fun cancelHoursWarning() {
        _uiState.update { it.copy(showHoursWarning = false) }
    }
    
    fun confirmDuplicateAnyway(
        date: String,
        hours: Double,
        isOvertime: Boolean,
        location: String,
        remark: String,
        mealSubsidy: Boolean,
        isManual: Boolean
    ) {
        _uiState.update { it.copy(showDuplicateWarning = false) }
        viewModelScope.launch {
            performSave(date, hours, isOvertime, location, remark, mealSubsidy, isManual)
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
        
        // 业务规则：标准工(!isOvertime && !isManual)强制mealSubsidy=true，加班(isOvertime)强制mealSubsidy=false
        // 手动折算的饭补可以自由选择
        val finalMealSubsidy = when {
            isOvertime -> false  // 加班没有饭补
            !isOvertime && !isManual -> true  // 标准工必须有饭补
            else -> mealSubsidy  // 手动折算可以自由选择
        }
        
        if (editingRecord != null) {
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
        } else {
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
        
        _uiState.update { it.copy(showAddDialog = false, editingRecord = null) }
        refreshData()
    }
    
    fun quickCheckIn() {
        viewModelScope.launch {
            val settings = _uiState.value.settings
            
            // 保存一键记工的参数
            pendingQuickCheckInHours = settings.dailyWorkHours  // 标准工时 = 9.0
            pendingQuickCheckInOvertime = false
            pendingQuickCheckInMealSubsidy = true  // 标准工必须有饭补
            
            // 始终弹出对话框，强制输入工地名称
            _uiState.update { it.copy(showQuickCheckInDialog = true) }
        }
    }
    
    fun confirmQuickCheckIn(location: String) {
        viewModelScope.launch {
            val today = DateUtils.today()
            
            // 一键记工：标准工（标准工时），强制饭补=true
            val record = WorkRecord(
                date = today,
                hours = pendingQuickCheckInHours,
                isOvertime = false,  // 标准工
                location = location.trim(),  // 可以为空
                remark = "",
                mealSubsidy = true,  // 标准工必须有饭补
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
