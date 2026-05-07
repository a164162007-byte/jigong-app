package com.worklogger.app.ui.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "记工",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${DateUtils.getYear()}.${DateUtils.getMonth()}月",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
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
    ) { padding ->
        if (uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
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
                            subtitle = "小时",
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
                item {
                    Card(
                        onClick = { viewModel.quickCheckIn() },
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = Primary
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Add,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
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
                
                // 进度条
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Text(
                                text = "月目标进度",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            ProgressBar(
                                progress = uiState.progress,
                                label = "已完成 ${uiState.settings.monthTarget} 天中的 ${String.format("%.1f", uiState.settings.monthTarget * uiState.progress)} 天"
                            )
                        }
                    }
                }
                
                // 漏记提醒
                if (uiState.missedDays.isNotEmpty()) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Outlined.Warning,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "最近7天漏记",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Spacer(modifier = Modifier.height(12.dp))
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    items(uiState.missedDays) { date ->
                                        MissedDayChip(
                                            date = date,
                                            onClick = { viewModel.showAddDialog() }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                
                // 最近记录标题
                item {
                    Text(
                        text = "最近记录",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
                
                // 最近记录列表
                if (uiState.recentRecords.isEmpty()) {
                    item {
                        EmptyState(
                            message = "暂无记录，点击下方按钮记工",
                            modifier = Modifier.padding(vertical = 32.dp)
                        )
                    }
                } else {
                    items(
                        items = uiState.recentRecords,
                        key = { it.id }
                    ) { record ->
                        WorkRecordCard(
                            record = record,
                            onClick = { viewModel.showEditDialog(record) },
                            onDelete = { viewModel.showDeleteConfirm(record.id) }
                        )
                    }
                }
                
                // 底部间距
                item {
                    Spacer(modifier = Modifier.height(80.dp))
                }
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
        
        // 工时异常警告
        if (uiState.showHoursWarning) {
            ConfirmDialog(
                title = "工时异常提醒",
                message = "您输入的工时为 ${uiState.warningHours} 小时，超过12小时或不足1小时，请确认。",
                confirmText = "继续保存",
                onConfirm = {
                    // 需要再次传入参数才能确认，这里简化处理
                },
                onDismiss = { viewModel.cancelHoursWarning() }
            )
        }
        
        // 重复记录警告
        if (uiState.showDuplicateWarning) {
            ConfirmDialog(
                title = "重复记录提醒",
                message = "${DateUtils.formatDisplayFullDate(uiState.duplicateDate)} 已有记录，是否继续添加？",
                confirmText = "继续添加",
                onConfirm = {
                    // 需要再次传入参数才能确认，这里简化处理
                },
                onDismiss = { viewModel.cancelDuplicateWarning() }
            )
        }
    }
}
