package com.worklogger.app.ui.advance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.worklogger.app.data.repository.SettingsRepository
import com.worklogger.app.data.repository.WorkRepository
import com.worklogger.app.model.AdvanceSalaryRecord
import com.worklogger.app.model.UserSettings
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

data class AdvanceSalaryUiState(
    val isLoading: Boolean = true,
    val allRecords: List<AdvanceSalaryRecord> = emptyList(),
    val totalAmount: Double = 0.0,
    val monthlyAmount: Double = 0.0,
    val recentLocations: List<String> = emptyList(),
    val settings: UserSettings = UserSettings(),
    val showAddDialog: Boolean = false,
    val editingRecord: AdvanceSalaryRecord? = null,
    val showDeleteConfirm: Boolean = false,
    val deleteRecordId: Int? = null
)

class AdvanceSalaryViewModel(
    private val workRepository: WorkRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AdvanceSalaryUiState())
    val uiState: StateFlow<AdvanceSalaryUiState> = _uiState.asStateFlow()

    init {
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            settingsRepository.settings.collect { settings ->
                _uiState.update { it.copy(settings = settings) }
                refreshData()
            }
        }

        viewModelScope.launch {
            workRepository.allAdvanceRecords.collect { records ->
                _uiState.update { it.copy(allRecords = records) }
            }
        }

        viewModelScope.launch {
            workRepository.totalAdvanceAmount.collect { total ->
                _uiState.update { it.copy(totalAmount = total) }
            }
        }

        viewModelScope.launch {
            workRepository.recentAdvanceLocations.collect { locations ->
                _uiState.update { it.copy(recentLocations = locations) }
            }
        }
    }

    fun refreshData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            val now = Calendar.getInstance()
            val year = now.get(Calendar.YEAR)
            val month = now.get(Calendar.MONTH) + 1

            val monthlyAmount = workRepository.getAdvanceAmountByMonth(year, month)

            _uiState.update {
                it.copy(
                    monthlyAmount = monthlyAmount,
                    isLoading = false
                )
            }
        }
    }

    fun showAddDialog() {
        _uiState.update { it.copy(showAddDialog = true, editingRecord = null) }
    }

    fun showEditDialog(record: AdvanceSalaryRecord) {
        _uiState.update { it.copy(showAddDialog = true, editingRecord = record) }
    }

    fun hideAddDialog() {
        _uiState.update { it.copy(showAddDialog = false, editingRecord = null) }
    }

    fun saveRecord(
        date: String,
        time: String,
        location: String,
        amount: Double,
        remark: String
    ) {
        viewModelScope.launch {
            val editingRecord = _uiState.value.editingRecord

            if (editingRecord != null) {
                val updated = editingRecord.copy(
                    date = date,
                    time = time,
                    location = location,
                    amount = amount,
                    remark = remark
                )
                workRepository.insertAdvanceRecord(updated)
            } else {
                val newRecord = AdvanceSalaryRecord(
                    date = date,
                    time = time,
                    location = location,
                    amount = amount,
                    remark = remark
                )
                workRepository.insertAdvanceRecord(newRecord)
            }

            _uiState.update { it.copy(showAddDialog = false, editingRecord = null) }
            refreshData()
        }
    }

    fun showDeleteConfirm(recordId: Int) {
        _uiState.update { it.copy(showDeleteConfirm = true, deleteRecordId = recordId) }
    }

    fun hideDeleteConfirm() {
        _uiState.update { it.copy(showDeleteConfirm = false, deleteRecordId = null) }
    }

    fun confirmDelete() {
        viewModelScope.launch {
            val recordId = _uiState.value.deleteRecordId ?: return@launch
            val record = _uiState.value.allRecords.find { it.id == recordId }
            if (record != null) {
                workRepository.deleteAdvanceRecord(record)
            }
            _uiState.update { it.copy(showDeleteConfirm = false, deleteRecordId = null) }
            refreshData()
        }
    }
}

class AdvanceSalaryViewModelFactory(
    private val workRepository: WorkRepository,
    private val settingsRepository: SettingsRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(AdvanceSalaryViewModel::class.java)) {
            return AdvanceSalaryViewModel(workRepository, settingsRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
