package com.worklogger.app.ui.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.worklogger.app.data.repository.SettingsRepository
import com.worklogger.app.data.repository.WorkRepository
import com.worklogger.app.model.WorkRecord
import com.worklogger.app.utils.DateUtils
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class CalendarUiState(
    val currentYear: Int = DateUtils.getYear(),
    val currentMonth: Int = DateUtils.getMonth(),
    val recordsByDate: Map<String, List<WorkRecord>> = emptyMap(),
    val selectedDate: String? = null,
    val selectedRecords: List<WorkRecord> = emptyList(),
    val showDetailDialog: Boolean = false,
    val recentLocations: List<String> = emptyList(),
    val showEditDialog: Boolean = false,
    val editingRecord: WorkRecord? = null,
    val showDeleteConfirm: Boolean = false,
    val deletingRecord: WorkRecord? = null,
    val isLoading: Boolean = true
)

class CalendarViewModel(
    private val workRepository: WorkRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(CalendarUiState())
    val uiState: StateFlow<CalendarUiState> = _uiState.asStateFlow()
    
    init {
        loadData()
        loadRecentLocations()
    }
    
    private fun loadData() {
        val state = _uiState.value
        val yearMonth = String.format("%04d-%02d", state.currentYear, state.currentMonth)
        val startDate = DateUtils.getYearMonthFirstDay(yearMonth)
        val endDate = DateUtils.getYearMonthNextFirstDay(yearMonth)
        
        viewModelScope.launch {
            val records = workRepository.getRecordsByDateRange(startDate, endDate)
            val grouped = records.groupBy { it.date }
            _uiState.update { it.copy(recordsByDate = grouped, isLoading = false) }
        }
    }
    
    private fun loadRecentLocations() {
        viewModelScope.launch {
            workRepository.allRecords.collect { allRecords ->
                val recentLocs = allRecords
                    .filter { it.location.isNotEmpty() }
                    .groupBy { it.location }
                    .mapValues { it.value.size }
                    .entries.sortedByDescending { it.value }
                    .take(10)
                    .map { it.key }
                _uiState.update { it.copy(recentLocations = recentLocs) }
            }
        }
    }
    
    fun previousMonth() {
        _uiState.update {
            val newMonth = if (it.currentMonth == 1) 12 else it.currentMonth - 1
            val newYear = if (it.currentMonth == 1) it.currentYear - 1 else it.currentYear
            it.copy(currentYear = newYear, currentMonth = newMonth, selectedDate = null, selectedRecords = emptyList(), isLoading = true)
        }
        loadData()
    }
    
    fun nextMonth() {
        _uiState.update {
            val newMonth = if (it.currentMonth == 12) 1 else it.currentMonth + 1
            val newYear = if (it.currentMonth == 12) it.currentYear + 1 else it.currentYear
            val currentYearMonth = DateUtils.currentYearMonth()
            val newYearMonth = String.format("%04d-%02d", newYear, newMonth)
            if (newYearMonth > currentYearMonth) return@update it
            it.copy(currentYear = newYear, currentMonth = newMonth, selectedDate = null, selectedRecords = emptyList(), isLoading = true)
        }
        loadData()
    }
    
    fun selectDate(date: String) {
        val records = _uiState.value.recordsByDate[date] ?: emptyList()
        _uiState.update { it.copy(selectedDate = date, selectedRecords = records, showDetailDialog = records.isNotEmpty()) }
    }
    
    fun hideDetailDialog() {
        _uiState.update { it.copy(showDetailDialog = false, selectedDate = null, selectedRecords = emptyList()) }
    }
    
    fun refresh() {
        _uiState.update { it.copy(isLoading = true) }
        loadData()
    }
    
    fun showEditDialog(record: WorkRecord) {
        _uiState.update { it.copy(showEditDialog = true, editingRecord = record, showDetailDialog = false) }
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
    
    fun showDeleteConfirm(record: WorkRecord) {
        _uiState.update { it.copy(showDeleteConfirm = true, deletingRecord = record, showDetailDialog = false) }
    }
    
    fun hideDeleteConfirm() {
        _uiState.update { it.copy(showDeleteConfirm = false, deletingRecord = null) }
    }
    
    fun confirmDelete() {
        val record = _uiState.value.deletingRecord ?: return
        viewModelScope.launch {
            val deletedRecord = record.copy(
                isDeleted = true, deletedAt = System.currentTimeMillis(), updatedAt = System.currentTimeMillis()
            )
            workRepository.update(deletedRecord)
            hideDeleteConfirm()
            refresh()
        }
    }
}

class CalendarViewModelFactory(
    private val workRepository: WorkRepository,
    private val settingsRepository: SettingsRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(CalendarViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return CalendarViewModel(workRepository, settingsRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
