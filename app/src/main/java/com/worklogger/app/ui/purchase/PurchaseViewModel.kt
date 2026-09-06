package com.worklogger.app.ui.purchase

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.worklogger.app.data.repository.SettingsRepository
import com.worklogger.app.data.repository.WorkRepository
import com.worklogger.app.model.AdvancePurchaseRecord
import com.worklogger.app.model.UserSettings
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.*

data class PurchaseUiState(
    val isLoading: Boolean = true,
    val allRecords: List<AdvancePurchaseRecord> = emptyList(),
    val totalAmount: Double = 0.0,
    val monthlyAmount: Double = 0.0,
    val recentLocations: List<String> = emptyList(),
    val settings: UserSettings = UserSettings(),
    val showAddDialog: Boolean = false,
    val editingRecord: AdvancePurchaseRecord? = null,
    val showDeleteConfirm: Boolean = false,
    val deleteRecordId: Int? = null
)

class PurchaseViewModel(
    private val workRepository: WorkRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(PurchaseUiState())
    val uiState: StateFlow<PurchaseUiState> = _uiState.asStateFlow()

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
            workRepository.allPurchaseRecords.collect { records ->
                _uiState.update { it.copy(allRecords = records) }
            }
        }

        viewModelScope.launch {
            workRepository.totalPurchaseAmount.collect { total ->
                _uiState.update { it.copy(totalAmount = total) }
            }
        }

        viewModelScope.launch {
            workRepository.recentPurchaseLocations.collect { locations ->
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

            val monthlyAmount = workRepository.getPurchaseAmountByMonth(year, month)

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

    fun showEditDialog(record: AdvancePurchaseRecord) {
        _uiState.update { it.copy(showAddDialog = true, editingRecord = record) }
    }

    fun hideAddDialog() {
        _uiState.update { it.copy(showAddDialog = false, editingRecord = null) }
    }

    fun saveRecord(
        date: String,
        itemName: String,
        location: String,
        amount: Double,
        quantity: Int,
        remark: String
    ) {
        viewModelScope.launch {
            val editingRecord = _uiState.value.editingRecord

            if (editingRecord != null) {
                val updated = editingRecord.copy(
                    date = date,
                    itemName = itemName,
                    location = location,
                    amount = amount,
                    quantity = quantity,
                    remark = remark
                )
                workRepository.updatePurchaseRecord(updated)
            } else {
                val newRecord = AdvancePurchaseRecord(
                    date = date,
                    itemName = itemName,
                    location = location,
                    amount = amount,
                    quantity = quantity,
                    remark = remark
                )
                workRepository.insertPurchaseRecord(newRecord)
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
            workRepository.movePurchaseToTrash(recordId)
            _uiState.update { it.copy(showDeleteConfirm = false, deleteRecordId = null) }
            refreshData()
        }
    }
}

class PurchaseViewModelFactory(
    private val workRepository: WorkRepository,
    private val settingsRepository: SettingsRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(PurchaseViewModel::class.java)) {
            return PurchaseViewModel(workRepository, settingsRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
