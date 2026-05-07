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
    val settings: UserSettings = UserSettings(),
    val monthlyTrend: List<Pair<String, Double>> = emptyList(), // yearMonth -> totalHours
    val locationDistribution: Map<String, Int> = emptyMap(), // location -> count
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
                Pair(DateUtils.getYearMonthFirstDay(ym), DateUtils.getYearMonthLastDay(ym))
            }
            "year" -> {
                val year = DateUtils.getYear(state.selectedYearMonth)
                Pair("$year-01-01", "$year-12-31")
            }
            else -> {
                val ym = state.selectedYearMonth
                Pair(DateUtils.getYearMonthFirstDay(ym), DateUtils.getYearMonthLastDay(ym))
            }
        }
        
        workRepository.getRecordsByDateRange(startDate, endDate).collect { records ->
            val stats = StatsCalculator.calculateStats(
                records,
                settings.dailyWorkHours,
                settings.overtimeRate,
                settings.mealSubsidyStandard,
                settings.dailyWage
            )
            
            val overtimeDist = StatsCalculator.calculateOvertimeDistribution(
                records,
                settings.dailyWorkHours
            )
            
            val (totalDays, totalHours) = StatsCalculator.calculateTotalOvertime(records)
            
            // 计算上月数据用于对比
            val prevYearMonth = DateUtils.addMonths(state.selectedYearMonth, -1)
            val prevStartDate = DateUtils.getYearMonthFirstDay(prevYearMonth)
            val prevEndDate = DateUtils.getYearMonthLastDay(prevYearMonth)
            
            val allRecords = workRepository.getAllRecordsSync()
            val prevRecords = allRecords.filter { 
                it.date >= prevStartDate && it.date <= prevEndDate 
            }
            val prevStats = StatsCalculator.calculateStats(
                prevRecords,
                settings.dailyWorkHours,
                settings.overtimeRate,
                settings.mealSubsidyStandard,
                settings.dailyWage
            )
            
            val comparison = StatsCalculator.calculateComparison(stats, prevStats)
            
            // 计算地点分布
            val locationDist = records
                .filter { !it.isOvertime }
                .groupBy { it.location.ifEmpty { "未填写" } }
                .mapValues { it.value.size }
            
            _uiState.update {
                it.copy(
                    currentStats = stats,
                    previousStats = prevStats,
                    comparison = comparison,
                    overtimeDistribution = overtimeDist,
                    totalOvertimeDays = totalDays,
                    totalOvertimeHours = totalHours,
                    recentRecords = records,
                    locationDistribution = locationDist,
                    isLoading = false
                )
            }
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
            val endDate = DateUtils.getYearMonthLastDay(ym)
            
            val records = workRepository.getAllRecordsSync().filter {
                it.date >= startDate && it.date <= endDate
            }
            
            val stats = StatsCalculator.calculateStats(
                records,
                settings.dailyWorkHours,
                settings.overtimeRate,
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
