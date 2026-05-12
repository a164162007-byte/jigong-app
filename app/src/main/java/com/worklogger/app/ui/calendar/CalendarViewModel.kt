package com.worklogger.app.ui.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
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
    val isLoading: Boolean = true
)

class CalendarViewModel(
    private val workRepository: WorkRepository
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(CalendarUiState())
    val uiState: StateFlow<CalendarUiState> = _uiState.asStateFlow()
    
    init {
        loadData()
    }
    
    private fun loadData() {
        val state = _uiState.value
        val yearMonth = String.format("%04d-%02d", state.currentYear, state.currentMonth)
        val startDate = DateUtils.getYearMonthFirstDay(yearMonth)
        val endDate = DateUtils.getYearMonthNextFirstDay(yearMonth)
        
        viewModelScope.launch {
            val records = workRepository.getRecordsByDateRange(startDate, endDate)
            val grouped = records.groupBy { it.date }
            _uiState.update {
                it.copy(
                    recordsByDate = grouped,
                    isLoading = false
                )
            }
        }
    }
    
    fun previousMonth() {
        _uiState.update {
            val newMonth = if (it.currentMonth == 1) 12 else it.currentMonth - 1
            val newYear = if (it.currentMonth == 1) it.currentYear - 1 else it.currentYear
            it.copy(
                currentYear = newYear,
                currentMonth = newMonth,
                selectedDate = null,
                selectedRecords = emptyList(),
                isLoading = true
            )
        }
        loadData()
    }
    
    fun nextMonth() {
        _uiState.update {
            val newMonth = if (it.currentMonth == 12) 1 else it.currentMonth + 1
            val newYear = if (it.currentMonth == 12) it.currentYear + 1 else it.currentYear
            // 不能超过当前月份
            val currentYearMonth = DateUtils.currentYearMonth()
            val newYearMonth = String.format("%04d-%02d", newYear, newMonth)
            if (newYearMonth > currentYearMonth) {
                return@update it
            }
            it.copy(
                currentYear = newYear,
                currentMonth = newMonth,
                selectedDate = null,
                selectedRecords = emptyList(),
                isLoading = true
            )
        }
        loadData()
    }
    
    fun selectDate(date: String) {
        val records = _uiState.value.recordsByDate[date] ?: emptyList()
        _uiState.update {
            it.copy(
                selectedDate = date,
                selectedRecords = records,
                showDetailDialog = records.isNotEmpty()
            )
        }
    }
    
    fun hideDetailDialog() {
        _uiState.update {
            it.copy(
                showDetailDialog = false,
                selectedDate = null,
                selectedRecords = emptyList()
            )
        }
    }
    
    fun refresh() {
        _uiState.update { it.copy(isLoading = true) }
        loadData()
    }
}

class CalendarViewModelFactory(
    private val workRepository: WorkRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(CalendarViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return CalendarViewModel(workRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
