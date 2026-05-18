package com.worklogger.app.ui.stats

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

data class StatsUiState(
    val selectedPeriod: String = "month", // month, year, custom
    val selectedYearMonth: String = DateUtils.currentYearMonth(),
    val currentStats: StatsData = StatsData(),
    val previousStats: StatsData = StatsData(),
    val comparison: Triple<Double, Double, Double> = Triple(0.0, 0.0, 0.0),
    val overtimeDistribution: Map<Double, Int> = emptyMap(),
    val totalOvertimeDays: Double = 0.0,
    val totalOvertimeHours: Double = 0.0,
    val recentRecords: List<WorkRecord> = emptyList(),
    val monthlyDetailRecords: List<WorkRecord> = emptyList(), // 月度详细记录（按日期倒序）
    val settings: UserSettings = UserSettings(),
    val monthlyTrend: List<Pair<String, Double>> = emptyList(), // yearMonth -> totalHours
    val locationDistribution: Map<String, Int> = emptyMap(), // location -> count
    val recentLocations: List<String> = emptyList(),
    // 编辑功能
    val showEditDialog: Boolean = false,
    val editingRecord: WorkRecord? = null,
    // 删除功能
    val showDeleteConfirm: Boolean = false,
    val deletingRecord: WorkRecord? = null,
    val isLoading: Boolean = true
)

class StatsViewModel(
    private val workRepository: WorkRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(StatsUiState())
    val uiState: StateFlow<StatsUiState> = _uiState.asStateFlow()
    
    init {
        loadData()
    }
    
    private fun loadData() {
        viewModelScope.launch {
            settingsRepository.settings.collect { settings ->
                _uiState.update { it.copy(settings = settings) }
                loadStatsData()
            }
        }
    }
    
    private suspend fun loadStatsData() {
        val state = _uiState.value
        val settings = state.settings
        
        // 获取当前期间数据
        val (startDate, endDate) = when (state.selectedPeriod) {
            "month" -> {
                val ym = state.selectedYearMonth
                Pair(DateUtils.getYearMonthFirstDay(ym), DateUtils.getYearMonthNextFirstDay(ym))
            }
            "year" -> {
                val year = DateUtils.getYear(state.selectedYearMonth)
                Pair("$year-01-01", "${year + 1}-01-01")
            }
            else -> {
                val ym = state.selectedYearMonth
                Pair(DateUtils.getYearMonthFirstDay(ym), DateUtils.getYearMonthLastDay(ym))
            }
        }
        
        val records = workRepository.getRecordsByDateRange(startDate, endDate)
        val stats = StatsCalculator.calculateStats(
            records,
            settings.dailyWorkHours,
            settings.overtimeWorkHours,
            settings.mealSubsidyStandard,
            settings.dailyWage
        )
        
        val overtimeDist = StatsCalculator.calculateOvertimeDistribution(
            records,
            settings.overtimeWorkHours
        )
        
        val (totalDays, totalHours) = StatsCalculator.calculateTotalOvertime(records)
        
        // 计算上月数据用于对比
        val prevYearMonth = DateUtils.addMonths(state.selectedYearMonth, -1)
        val prevStartDate = DateUtils.getYearMonthFirstDay(prevYearMonth)
        val prevEndDate = DateUtils.getYearMonthNextFirstDay(prevYearMonth)
        
        val prevRecords = workRepository.getRecordsByDateRange(prevStartDate, prevEndDate)
        val prevStats = StatsCalculator.calculateStats(
            prevRecords,
            settings.dailyWorkHours,
            settings.overtimeWorkHours,
            settings.mealSubsidyStandard,
            settings.dailyWage
        )
        
        val comparison = StatsCalculator.calculateComparison(stats, prevStats)
        
        // 计算地点分布
        val locationDist = records
            .filter { !it.isOvertime }
            .groupBy { it.location.ifEmpty { "未填写" } }
            .mapValues { it.value.size }
        
        // 计算月度详细记录（按日期倒序）
        val sortedDetailRecords = records.sortedByDescending { it.date }
        
        // 获取最近工地列表
        val allRecords = workRepository.allRecords.first()
        val recentLocs = allRecords
            .filter { it.location.isNotEmpty() }
            .groupBy { it.location }
            .mapValues { it.value.size }
            .entries.sortedByDescending { it.value }
            .take(10)
            .map { it.key }
        
        _uiState.update {
            it.copy(
                currentStats = stats,
                previousStats = prevStats,
                comparison = comparison,
                overtimeDistribution = overtimeDist,
                totalOvertimeDays = totalDays,
                totalOvertimeHours = totalHours,
                recentRecords = records,
                monthlyDetailRecords = sortedDetailRecords,
                locationDistribution = locationDist,
                recentLocations = recentLocs,
                isLoading = false
            )
        }
        
        // 加载近6个月趋势
        loadMonthlyTrend()
    }
    
    private suspend fun loadMonthlyTrend() {
        val yearMonths = DateUtils.getLast6MonthsYearMonths()
        val settings = _uiState.value.settings
        
        val trend = mutableListOf<Pair<String, Double>>()
        
        for (ym in yearMonths) {
            val startDate = DateUtils.getYearMonthFirstDay(ym)
            val endDate = DateUtils.getYearMonthNextFirstDay(ym)
            
            val records = workRepository.getRecordsByDateRange(startDate, endDate)
            
            val stats = StatsCalculator.calculateStats(
                records,
                settings.dailyWorkHours,
                settings.overtimeWorkHours,
                settings.mealSubsidyStandard,
                settings.dailyWage
            )
            
            trend.add(ym to stats.totalStandard)
        }
        
        _uiState.update { it.copy(monthlyTrend = trend) }
    }
    
    fun setSelectedPeriod(period: String) {
        _uiState.update { it.copy(selectedPeriod = period, isLoading = true) }
        viewModelScope.launch { loadStatsData() }
    }
    
    fun setSelectedYearMonth(yearMonth: String) {
        _uiState.update { it.copy(selectedYearMonth = yearMonth, isLoading = true) }
        viewModelScope.launch { loadStatsData() }
    }
    
    fun previousMonth() {
        val newYearMonth = DateUtils.addMonths(_uiState.value.selectedYearMonth, -1)
        setSelectedYearMonth(newYearMonth)
    }
    
    fun nextMonth() {
        val newYearMonth = DateUtils.addMonths(_uiState.value.selectedYearMonth, 1)
        // 不能超过当前月份
        if (newYearMonth <= DateUtils.currentYearMonth()) {
            setSelectedYearMonth(newYearMonth)
        }
    }
    
    fun refresh() {
        _uiState.update { it.copy(isLoading = true) }
        viewModelScope.launch { loadStatsData() }
    }
    
    // ========== 编辑功能 ==========
    
    fun showEditDialog(record: WorkRecord) {
        _uiState.update { it.copy(showEditDialog = true, editingRecord = record) }
    }
    
    fun hideEditDialog() {
        _uiState.update { it.copy(showEditDialog = false, editingRecord = null) }
    }
    
    fun saveEditedRecord(date: String, hours: Double, isOvertime: Boolean, location: String, remark: String, mealSubsidy: Boolean, isManual: Boolean) {
        val record = _uiState.value.editingRecord ?: return
        viewModelScope.launch {
            val settings = settingsRepository.settings.first()
            val dailyWorkHours = settings.dailyWorkHours
            
            // 自动拆分：当 hours > dailyWorkHours 且类型是标准工或手动折算时，拆分为两条记录
            val shouldSplit = !isOvertime && hours > dailyWorkHours
            
            if (shouldSplit) {
                // 更新标准工记录
                val updatedStandard = record.copy(
                    date = date,
                    hours = dailyWorkHours,
                    isOvertime = false,
                    location = location,
                    remark = remark,
                    mealSubsidy = true,  // 标准工必有饭补
                    isManual = isManual,
                    updatedAt = System.currentTimeMillis()
                )
                // 新增加班记录
                val newOvertime = WorkRecord(
                    date = date,
                    hours = hours - dailyWorkHours,
                    isOvertime = true,
                    location = location,
                    remark = "",
                    mealSubsidy = false,  // 加班无饭补
                    isManual = false
                )
                workRepository.update(updatedStandard)
                workRepository.insert(newOvertime)
            } else {
                // 业务规则：标准工强制mealSubsidy=true，加班强制mealSubsidy=false，手动折算可自由选择
                val finalMealSubsidy = when {
                    isOvertime -> false
                    !isOvertime && !isManual -> true
                    else -> mealSubsidy
                }
                val updatedRecord = record.copy(
                    date = date, hours = hours, isOvertime = isOvertime,
                    location = location, remark = remark, mealSubsidy = finalMealSubsidy,
                    isManual = isManual, updatedAt = System.currentTimeMillis()
                )
                workRepository.update(updatedRecord)
            }
            hideEditDialog()
            refresh()
        }
    }
    
    // ========== 删除功能 ==========
    
    fun showDeleteConfirm(record: WorkRecord) {
        _uiState.update { it.copy(showDeleteConfirm = true, deletingRecord = record) }
    }
    
    fun hideDeleteConfirm() {
        _uiState.update { it.copy(showDeleteConfirm = false, deletingRecord = null) }
    }
    
    fun confirmDelete() {
        val record = _uiState.value.deletingRecord ?: return
        viewModelScope.launch {
            workRepository.moveToTrash(record)
            hideDeleteConfirm()
            refresh()
        }
    }
}

class StatsViewModelFactory(
    private val workRepository: WorkRepository,
    private val settingsRepository: SettingsRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(StatsViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return StatsViewModel(workRepository, settingsRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
