package com.worklogger.app.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.worklogger.app.data.repository.SettingsRepository
import com.worklogger.app.data.repository.WorkRepository
import com.worklogger.app.model.MonthlySalarySettlement
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
    // 工资结算单
    val showSettlementDialog: Boolean = false,
    val settlement: MonthlySalarySettlement? = null,
    val settlementLocation: String = "",       // 结算单地点筛选
    val currentPeriodAdvance: Double = 0.0,    // 当前周期预支合计
    val isLoading: Boolean = true
)

class StatsViewModel(
    private val workRepository: WorkRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(StatsUiState())
    val uiState: StateFlow<StatsUiState> = _uiState.asStateFlow()
    
    // 结算单加载任务，用于取消重复的加载请求
    private var settlementLoadJob: kotlinx.coroutines.Job? = null
    
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
        
        // 计算当前周期预支合计
        val allAdvance = workRepository.allAdvanceRecords.first()
        val periodAdvance = allAdvance
            .filter { it.date >= startDate && it.date < endDate }
            .sumOf { it.amount }
        
        // 优化：用SQL直接查distinct地点，不用全表加载
        val recentLocs = workRepository.getAllLocations()
        
        // 年度月度分解 - 优化为一次查询全年数据+内存分组
        val yearBreakdown = if (state.selectedPeriod == "year") {
            calculateYearMonthlyBreakdownOptimized(state.selectedYear, state.selectedLocation, settings)
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
                currentPeriodAdvance = periodAdvance,
                isLoading = false
            )
        }
        
        // 加载近6个月趋势（仅月视图）
        if (state.selectedPeriod == "month") {
            loadMonthlyTrend()
        }
    }
    
    /**
     * 优化版年度月度分解：一次查询全年数据，在内存中按月分组计算
     * 避免12次数据库查询
     */
    private suspend fun calculateYearMonthlyBreakdownOptimized(year: String, location: String, settings: UserSettings): List<Pair<String, Double>> {
        val startDate = "$year-01-01"
        val endDate = "${year.toInt() + 1}-01-01"
        val allYearRecords = workRepository.getRecordsByDateRangeAndLocation(startDate, endDate, location)
        
        // 按月分组
        val groupedByMonth = allYearRecords.groupBy { it.date.substring(0, 7) } // "yyyy-MM"
        
        val breakdown = mutableListOf<Pair<String, Double>>()
        for (month in 1..12) {
            val yearMonth = String.format("%s-%02d", year, month)
            val monthRecords = groupedByMonth[yearMonth] ?: emptyList()
            val stats = StatsCalculator.calculateStats(monthRecords, settings.dailyWorkHours, settings.overtimeWorkHours, settings.mealSubsidyStandard, settings.dailyWage)
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
    
    // ========== 工资结算单 ==========
    
    fun showSettlementSheet() {
        _uiState.update { it.copy(showSettlementDialog = true) }
        // 取消之前的加载任务，避免并发问题
        settlementLoadJob?.cancel()
        settlementLoadJob = viewModelScope.launch { loadSettlement() }
    }
    
    fun hideSettlementSheet() {
        _uiState.update { it.copy(showSettlementDialog = false) }
        // 隐藏弹窗时取消加载任务
        settlementLoadJob?.cancel()
    }
    
    fun updateSettlementLocation(location: String) {
        _uiState.update { it.copy(settlementLocation = location) }
        // 切换地点时取消之前的加载任务，避免并发问题
        settlementLoadJob?.cancel()
        settlementLoadJob = viewModelScope.launch { loadSettlement() }
    }
    
    private suspend fun loadSettlement() {
        try {
            val state = _uiState.value
            val settings = state.settings
            val location = state.settlementLocation

            val (startDate, endDate, yearMonth) = when (state.selectedPeriod) {
                "year" -> {
                    val year = state.selectedYear
                    Triple("$year-01-01", "${year.toInt() + 1}-01-01", "${year}年")
                }
                else -> {
                    val ym = state.selectedYearMonth
                    Triple(DateUtils.getYearMonthFirstDay(ym), DateUtils.getYearMonthNextFirstDay(ym), ym)
                }
            }

            val records = workRepository.getRecordsByDateRangeAndLocation(startDate, endDate, location)

            // 计算同期预支合计（年视图=全年，月视图=当月）
            val allAdvanceList = workRepository.allAdvanceRecords.first()
            val totalAdvance = allAdvanceList
                .filter { it.date >= startDate && it.date < endDate }
                .sumOf { it.amount }

            val settlement = StatsCalculator.calculateSettlement(
                records = records,
                advanceAmount = totalAdvance,
                yearMonth = yearMonth,
                location = location,
                dailyWorkHours = settings.dailyWorkHours,
                overtimeWorkHours = settings.overtimeWorkHours,
                mealSubsidyStandard = settings.mealSubsidyStandard,
                dailyWage = settings.dailyWage
            )

            _uiState.update { it.copy(settlement = settlement) }
        } catch (e: kotlinx.coroutines.CancellationException) {
            // 协程被取消，不处理，保持当前状态
            throw e
        } catch (e: Exception) {
            // 加载失败时设置一个空的 settlement，避免弹窗卡死或闪退
            val state = _uiState.value
            val yearMonth = when (state.selectedPeriod) {
                "year" -> "${state.selectedYear}年"
                else -> state.selectedYearMonth
            }
            val emptySettlement = com.worklogger.app.model.MonthlySalarySettlement(
                yearMonth = yearMonth,
                location = state.settlementLocation,
                standardDays = 0.0,
                standardWage = 0.0,
                overtimeDays = 0.0,
                overtimeWage = 0.0,
                manualDays = 0.0,
                manualWage = 0.0,
                mealSubsidyTotal = 0.0,
                totalEarning = 0.0,
                advanceAmount = 0.0,
                netPayable = 0.0
            )
            _uiState.update { it.copy(settlement = emptySettlement) }
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
