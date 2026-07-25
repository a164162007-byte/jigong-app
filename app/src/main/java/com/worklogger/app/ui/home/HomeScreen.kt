package com.worklogger.app.ui.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.worklogger.app.model.WorkRecord
import com.worklogger.app.ui.components.*
import com.worklogger.app.ui.theme.Primary
import com.worklogger.app.utils.DateUtils
import java.text.NumberFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onNavigateToAdd: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val currencyFormat = remember { NumberFormat.getCurrencyInstance(Locale.CHINA) }
    
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshData()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    
    // 按日期分组记录
    val groupedRecords = remember(uiState.recentRecords) {
        uiState.recentRecords.groupBy { it.date }
    }
    
    Scaffold(
        topBar = {
            if (uiState.isBatchMode) {
                // 批量模式顶栏
                TopAppBar(
                    title = {
                        Text(
                            text = "已选择 ${uiState.selectedRecordIds.size} 条",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { viewModel.exitBatchMode() }) {
                            Icon(Icons.Default.Close, contentDescription = "退出选择")
                        }
                    },
                    actions = {
                        IconButton(onClick = { viewModel.selectAllRecords() }) {
                            Icon(Icons.Default.SelectAll, contentDescription = "全选")
                        }
                        IconButton(
                            onClick = { viewModel.showBatchDeleteConfirm() },
                            enabled = uiState.selectedRecordIds.isNotEmpty()
                        ) {
                            Icon(Icons.Default.DeleteSweep, contentDescription = "批量删除",
                                tint = if (uiState.selectedRecordIds.isNotEmpty()) MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f))
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Primary.copy(alpha = 0.1f)
                    )
                )
            } else {
                TopAppBar(
                    title = {
                        Column {
                            Text(text = "记工", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Text(
                                text = "${DateUtils.getYear()}.${DateUtils.getMonth()}月",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    actions = {
                        // 批量选择按钮
                        if (uiState.recentRecords.isNotEmpty()) {
                            IconButton(onClick = { viewModel.enterBatchMode() }) {
                                Icon(Icons.Default.FilterList, contentDescription = "批量操作")
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            }
        },
        floatingActionButton = {
            if (!uiState.isBatchMode) {
                ExtendedFloatingActionButton(
                    onClick = { viewModel.showAddDialog() },
                    containerColor = Primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("记工")
                }
            }
        }
    ) { padding ->
        if (uiState.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 统计卡片
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        StatsCard(
                            title = "本月工时",
                            value = String.format("%.1f", uiState.totalHours),
                            subtitle = String.format("%.1f 标准工", uiState.totalStandardDays),
                            color = Primary,
                            modifier = Modifier.weight(1f)
                        )
                        StatsCard(
                            title = "本月工资",
                            value = currencyFormat.format(uiState.totalWage),
                            color = if (uiState.totalWage > 0) Primary else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                
                // 一键记工按钮
                if (!uiState.isBatchMode) {
                    item {
                        Card(
                            onClick = { viewModel.quickCheckIn() },
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = Primary)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(20.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "一键记工 (${uiState.settings.dailyWorkHours.toInt()}小时)",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            }
                        }
                    }
                }
                
                // 进度条
                if (!uiState.isBatchMode) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(text = "月目标进度", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(12.dp))
                                ProgressBar(
                                    progress = uiState.progress,
                                    label = "已完成 ${uiState.settings.monthTarget} 天中的 ${String.format("%.1f", uiState.settings.monthTarget * uiState.progress)} 天"
                                )
                            }
                        }
                    }
                }
                
                // 漏记提醒
                if (uiState.missedDays.isNotEmpty() && !uiState.isBatchMode) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f))
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Outlined.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(text = "最近7天漏记", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                }
                                Spacer(modifier = Modifier.height(12.dp))
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    items(uiState.missedDays) { date ->
                                        MissedDayChip(date = date, onClick = { viewModel.showAddDialog() })
                                    }
                                }
                            }
                        }
                    }
                }
                
                // 地点筛选
                if (uiState.allLocations.isNotEmpty()) {
                    item {
                        LocationFilterChips(
                            locations = uiState.allLocations,
                            selectedLocation = uiState.selectedLocation,
                            onLocationSelected = { viewModel.selectLocation(it) }
                        )
                    }
                }
                
                // 最近记录标题
                item {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (uiState.selectedLocation.isNotEmpty()) "「${uiState.selectedLocation}」记录" else "最近记录",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
                
                // 最近记录列表（按日期分组）
                if (uiState.recentRecords.isEmpty()) {
                    item {
                        EmptyState(
                            message = if (uiState.selectedLocation.isNotEmpty()) "该地点暂无记录" else "暂无记录，点击下方按钮记工",
                            modifier = Modifier.padding(vertical = 32.dp)
                        )
                    }
                } else if (uiState.isBatchMode) {
                    // 批量选择模式
                    items(
                        items = uiState.recentRecords,
                        key = { it.id }
                    ) { record ->
                        WorkRecordCardBatch(
                            record = record,
                            isSelected = uiState.selectedRecordIds.contains(record.id),
                            onClick = { viewModel.toggleRecordSelection(record.id) },
                            onLongClick = {}
                        )
                    }
                } else {
                    // 正常模式 - 按日期分组
                    groupedRecords.forEach { (date, records) ->
                        // 日期分组标题
                        item(key = "header_$date") {
                            DateGroupHeader(date = date)
                        }
                        // 该日期下的记录
                        items(
                            items = records,
                            key = { it.id }
                        ) { record ->
                            WorkRecordCard(
                                record = record,
                                onClick = { viewModel.showEditDialog(record) },
                                onDelete = { viewModel.showDeleteConfirm(record.id) }
                            )
                        }
                    }
                }
                
                item { Spacer(modifier = Modifier.height(80.dp)) }
            }
        }
        
        // 添加/编辑对话框
        if (uiState.showAddDialog) {
            AddRecordDialog(
                record = uiState.editingRecord,
                recentLocations = uiState.recentLocations,
                onDismiss = { viewModel.hideAddDialog() },
                onSave = { date, hours, isOvertime, location, remark, mealSubsidy, isManual ->
                    viewModel.saveRecord(date, hours, isOvertime, location, remark, mealSubsidy, isManual)
                }
            )
        }
        
        // 一键记工对话框
        if (uiState.showQuickCheckInDialog) {
            QuickCheckInDialog(
                recentLocations = uiState.recentLocations,
                onDismiss = { viewModel.cancelQuickCheckIn() },
                onConfirm = { location, date -> viewModel.confirmQuickCheckIn(location, date) }
            )
        }
        
        // 删除确认对话框
        if (uiState.showDeleteConfirm) {
            ConfirmDialog(
                title = "确认删除",
                message = "确定要删除这条记录吗？删除后可从回收站恢复。",
                confirmText = "删除",
                onConfirm = { viewModel.confirmDelete() },
                onDismiss = { viewModel.hideDeleteConfirm() },
                isDangerous = true
            )
        }
        
        // 批量删除确认
        if (uiState.showBatchDeleteConfirm) {
            ConfirmDialog(
                title = "批量删除",
                message = "确定要删除选中的 ${uiState.selectedRecordIds.size} 条记录吗？删除后可从回收站恢复。",
                confirmText = "删除",
                onConfirm = { viewModel.confirmBatchDelete() },
                onDismiss = { viewModel.hideBatchDeleteConfirm() },
                isDangerous = true
            )
        }
        
        // 工时异常警告
        if (uiState.showHoursWarning) {
            ConfirmDialog(
                title = "工时异常提醒",
                message = "您输入的工时为 ${uiState.warningHours} 小时，超过12小时或不足1小时，请确认。",
                confirmText = "继续保存",
                onConfirm = { viewModel.confirmSaveAnyway() },
                onDismiss = { viewModel.cancelHoursWarning() }
            )
        }
        
        // 重复记录警告
        if (uiState.showDuplicateWarning) {
            ConfirmDialog(
                title = "重复记录提醒",
                message = "${DateUtils.formatDisplayFullDate(uiState.duplicateDate)} 已有记录，是否继续添加？",
                confirmText = "继续添加",
                onConfirm = { viewModel.confirmDuplicateAnyway() },
                onDismiss = { viewModel.cancelDuplicateWarning() }
            )
        }
    }
}

@Composable
private fun DateGroupHeader(date: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = DateUtils.getDateGroupLabel(date),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = DateUtils.getWeekdayName(date),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.weight(1f))
        Divider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LocationFilterChips(
    locations: List<String>,
    selectedLocation: String,
    onLocationSelected: (String) -> Unit
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            FilterChip(
                selected = selectedLocation.isEmpty(),
                onClick = { onLocationSelected("") },
                label = { Text("全部") },
                leadingIcon = if (selectedLocation.isEmpty()) {
                    { Icon(Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp)) }
                } else null
            )
        }
        items(locations) { location ->
            FilterChip(
                selected = selectedLocation == location,
                onClick = { onLocationSelected(location) },
                label = { Text(location) }
            )
        }
    }
}
