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
    val selectedPeriod: String = "month", // month, year
    val selectedYearMonth: String = DateUtils.currentYearMonth(),
    val selectedYear: String = DateUtils.currentYear(),
    val currentStats: StatsData = StatsData(),
    val previousStats: StatsData = StatsData(),
    val comparison: Triple<Double, Double, Double> = Triple(0.0, 0.0, 0.0),
    val overtimeDistribution: Map<Double, Int> = emptyMap(),
    val totalOvertimeDays: Double = 0.0,
    val totalOvertimeHours: Double = 0.0,
    val recentRecords: List<WorkRecord> = emptyList(),
    val monthlyDetailRecords: List<WorkRecord> = emptyList(),
    val settings: UserSettings = UserSettings(),
    val monthlyTrend: List<Pair<String, Double>> = emptyList(),
    val locationDistribution: Map<String, Int> = emptyMap(),
    val recentLocations: List<String> = emptyList(),
    // 年度月度分解数据
    val yearMonthlyBreakdown: List<Pair<String, Double>> = emptyList(), // month -> totalDays
    // 地点筛选
    val selectedLocation: String = "",
    val allLocations: List<String> = emptyList(),
    // 批量操作
    val isBatchMode: Boolean = false,
    val selectedRecordIds: Set<Int> = emptySet(),
    val showBatchDeleteConfirm: Boolean = false,
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
        
        val (startDate, endDate) = when (state.selectedPeriod) {
            "year" -> {
                val year = state.selectedYear
                Pair("$year-01-01", "${year.toInt() + 1}-01-01")
            }
            else -> {
                val ym = state.selectedYearMonth
                Pair(DateUtils.getYearMonthFirstDay(ym), DateUtils.getYearMonthNextFirstDay(ym))
            }
        }
        
        // 使用地点筛选
        val records = workRepository.getRecordsByDateRangeAndLocation(startDate, endDate, state.selectedLocation)
        val stats = StatsCalculator.calculateStats(
            records, settings.dailyWorkHours, settings.overtimeWorkHours,
            settings.mealSubsidyStandard, settings.dailyWage
        )
        
        val overtimeDist = StatsCalculator.calculateOvertimeDistribution(records, settings.overtimeWorkHours)
        val (totalDays, totalHours) = StatsCalculator.calculateTotalOvertime(records)
        
        // 计算上期数据
        val prevStats: StatsData
        if (state.selectedPeriod == "year") {
            val prevYear = (state.selectedYear.toInt() - 1).toString()
            val prevRecords = workRepository.getRecordsByDateRangeAndLocation("$prevYear-01-01", "${prevYear.toInt() + 1}-01-01", state.selectedLocation)
            prevStats = StatsCalculator.calculateStats(prevRecords, settings.dailyWorkHours, settings.overtimeWorkHours, settings.mealSubsidyStandard, settings.dailyWage)
        } else {
            val prevYearMonth = DateUtils.addMonths(state.selectedYearMonth, -1)
            val prevRecords = workRepository.getRecordsByDateRangeAndLocation(DateUtils.getYearMonthFirstDay(prevYearMonth), DateUtils.getYearMonthNextFirstDay(prevYearMonth), state.selectedLocation)
            prevStats = StatsCalculator.calculateStats(prevRecords, settings.dailyWorkHours, settings.overtimeWorkHours, settings.mealSubsidyStandard, settings.dailyWage)
        }
        
        val comparison = StatsCalculator.calculateComparison(stats, prevStats)
        
        val locationDist = records
            .filter { !it.isOvertime }
            .groupBy { it.location.ifEmpty { "未填写" } }
            .mapValues { it.value.size }
        
        val sortedDetailRecords = records.sortedByDescending { it.date }
        
        val allRecords = workRepository.allRecords.first()
        val recentLocs = allRecords
            .filter { it.location.isNotEmpty() }
            .groupBy { it.location }
            .mapValues { it.value.size }
            .entries.sortedByDescending { it.value }
            .take(10)
            .map { it.key }
        
        // 年度月度分解
        val yearBreakdown = if (state.selectedPeriod == "year") {
            calculateYearMonthlyBreakdown(state.selectedYear, state.selectedLocation, settings)
        } else {
            emptyList()
        }
        
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
                allLocations = recentLocs,
                yearMonthlyBreakdown = yearBreakdown,
                isLoading = false
            )
        }
        
        // 加载近6个月趋势（仅月视图）
        if (state.selectedPeriod == "month") {
            loadMonthlyTrend()
        }
    }
    
    private suspend fun calculateYearMonthlyBreakdown(year: String, location: String, settings: UserSettings): List<Pair<String, Double>> {
        val breakdown = mutableListOf<Pair<String, Double>>()
        for (month in 1..12) {
            val yearMonth = String.format("%s-%02d", year, month)
            val startDate = DateUtils.getYearMonthFirstDay(yearMonth)
            val endDate = DateUtils.getYearMonthNextFirstDay(yearMonth)
            val records = workRepository.getRecordsByDateRangeAndLocation(startDate, endDate, location)
            val stats = StatsCalculator.calculateStats(records, settings.dailyWorkHours, settings.overtimeWorkHours, settings.mealSubsidyStandard, settings.dailyWage)
            breakdown.add(yearMonth to stats.totalStandard)
        }
        return breakdown
    }
    
    private suspend fun loadMonthlyTrend() {
        val yearMonths = DateUtils.getLast6MonthsYearMonths()
        val settings = _uiState.value.settings
        
        val trend = mutableListOf<Pair<String, Double>>()
        for (ym in yearMonths) {
            val startDate = DateUtils.getYearMonthFirstDay(ym)
            val endDate = DateUtils.getYearMonthNextFirstDay(ym)
            val records = workRepository.getRecordsByDateRangeAndLocation(startDate, endDate, _uiState.value.selectedLocation)
            val stats = StatsCalculator.calculateStats(records, settings.dailyWorkHours, settings.overtimeWorkHours, settings.mealSubsidyStandard, settings.dailyWage)
            trend.add(ym to stats.totalStandard)
        }
        
        _uiState.update { it.copy(monthlyTrend = trend) }
    }
    
    // ========== 视图模式切换 ==========
    
    fun setViewMode(mode: String) {
        _uiState.update { it.copy(selectedPeriod = mode, isLoading = true) }
        viewModelScope.launch { loadStatsData() }
    }
    
    fun setSelectedYearMonth(yearMonth: String) {
        _uiState.update { it.copy(selectedYearMonth = yearMonth, isLoading = true) }
        viewModelScope.launch { loadStatsData() }
    }
    
    fun setSelectedYear(year: String) {
        _uiState.update { it.copy(selectedYear = year, isLoading = true) }
        viewModelScope.launch { loadStatsData() }
    }
    
    fun previousMonth() {
        val newYearMonth = DateUtils.addMonths(_uiState.value.selectedYearMonth, -1)
        setSelectedYearMonth(newYearMonth)
    }
    
    fun nextMonth() {
        val newYearMonth = DateUtils.addMonths(_uiState.value.selectedYearMonth, 1)
        if (newYearMonth <= DateUtils.currentYearMonth()) {
            setSelectedYearMonth(newYearMonth)
        }
    }
    
    fun previousYear() {
        val newYear = DateUtils.addYears(_uiState.value.selectedYear, -1)
        setSelectedYear(newYear)
    }
    
    fun nextYear() {
        val newYear = DateUtils.addYears(_uiState.value.selectedYear, 1)
        if (newYear <= DateUtils.currentYear()) {
            setSelectedYear(newYear)
        }
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
        viewModelScope.launch { loadStatsData() }
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
            if (newSet.contains(recordId)) newSet.remove(recordId) else newSet.add(recordId)
            state.copy(selectedRecordIds = newSet)
        }
    }
    
    fun selectAllRecords() {
        val allIds = _uiState.value.monthlyDetailRecords.map { it.id }.toSet()
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
                workRepository.moveToTrash(workRepository.getRecordById(id.toLong()) ?: continue)
            }
            _uiState.update {
                it.copy(showBatchDeleteConfirm = false, isBatchMode = false, selectedRecordIds = emptySet())
            }
            refresh()
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
            val shouldSplit = !isOvertime && hours > dailyWorkHours
            
            if (shouldSplit) {
                val updatedStandard = record.copy(date = date, hours = dailyWorkHours, isOvertime = false, location = location, remark = remark, mealSubsidy = true, isManual = isManual, updatedAt = System.currentTimeMillis())
                val newOvertime = WorkRecord(date = date, hours = hours - dailyWorkHours, isOvertime = true, location = location, remark = "", mealSubsidy = false, isManual = false)
                workRepository.update(updatedStandard)
                workRepository.insert(newOvertime)
            } else {
                val finalMealSubsidy = when { isOvertime -> false; !isOvertime && !isManual -> true; else -> mealSubsidy }
                val updatedRecord = record.copy(date = date, hours = hours, isOvertime = isOvertime, location = location, remark = remark, mealSubsidy = finalMealSubsidy, isManual = isManual, updatedAt = System.currentTimeMillis())
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
