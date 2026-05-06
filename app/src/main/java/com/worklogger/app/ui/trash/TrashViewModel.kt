package com.worklogger.app.ui.trash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.worklogger.app.data.repository.WorkRepository
import com.worklogger.app.model.WorkRecord
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

data class TrashUiState(
    val deletedRecords: List<WorkRecord> = emptyList(),
    val isLoading: Boolean = true,
    val showDeleteConfirm: Boolean = false,
    val deleteRecordId: Int? = null,
    val showPermanentDeleteConfirm: Boolean = false
)

class TrashViewModel(
    private val workRepository: WorkRepository
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(TrashUiState())
    val uiState: StateFlow<TrashUiState> = _uiState.asStateFlow()
    
    init {
        loadData()
        cleanOldRecords()
    }
    
    private fun loadData() {
        viewModelScope.launch {
            workRepository.deletedRecords.collect { records ->
                _uiState.update {
                    it.copy(
                        deletedRecords = records,
                        isLoading = false
                    )
                }
            }
        }
    }
    
    private fun cleanOldRecords() {
        viewModelScope.launch {
            // 自动清理超过30天的已删除记录
            val thirtyDaysAgo = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(30)
            workRepository.cleanOldDeleted(thirtyDaysAgo)
        }
    }
    
    fun restoreRecord(id: Int) {
        viewModelScope.launch {
            workRepository.restoreRecord(id)
        }
    }
    
    fun showDeleteConfirm(id: Int) {
        _uiState.update { it.copy(showDeleteConfirm = true, deleteRecordId = id) }
    }
    
    fun hideDeleteConfirm() {
        _uiState.update { it.copy(showDeleteConfirm = false, deleteRecordId = null) }
    }
    
    fun confirmPermanentDelete() {
        viewModelScope.launch {
            _uiState.value.deleteRecordId?.let { id ->
                workRepository.permanentDeleteRecord(id)
            }
            _uiState.update { it.copy(showDeleteConfirm = false, deleteRecordId = null) }
        }
    }
    
    fun showPermanentDeleteAllConfirm() {
        _uiState.update { it.copy(showPermanentDeleteConfirm = true) }
    }
    
    fun hidePermanentDeleteAllConfirm() {
        _uiState.update { it.copy(showPermanentDeleteConfirm = false) }
    }
    
    fun permanentDeleteAll() {
        viewModelScope.launch {
            _uiState.value.deletedRecords.forEach { record ->
                workRepository.permanentDeleteRecord(record.id)
            }
            _uiState.update { it.copy(showPermanentDeleteConfirm = false) }
        }
    }
}

class TrashViewModelFactory(
    private val workRepository: WorkRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(TrashViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return TrashViewModel(workRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
