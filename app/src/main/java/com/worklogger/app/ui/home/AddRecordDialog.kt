package com.worklogger.app.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Note
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.worklogger.app.model.WorkRecord
import com.worklogger.app.ui.theme.*
import com.worklogger.app.utils.DateUtils
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddRecordDialog(
    record: WorkRecord? = null,
    recentLocations: List<String> = emptyList(),
    onDismiss: () -> Unit,
    onSave: (date: String, hours: Double, isOvertime: Boolean, location: String, remark: String, mealSubsidy: Boolean, isManual: Boolean) -> Unit
) {
    // 工地名称必填
    var locationError by remember { mutableStateOf(false) }
    var selectedDate by remember { mutableStateOf(record?.date ?: DateUtils.today()) }
    var hours by remember { mutableStateOf(record?.hours?.toString() ?: "7") }
    var location by remember { mutableStateOf(record?.location ?: "") }
    var remark by remember { mutableStateOf(record?.remark ?: "") }
    var mealSubsidy by remember { mutableStateOf(record?.mealSubsidy ?: false) }
    var isManual by remember { mutableStateOf(record?.isManual ?: false) }
    var isOvertime by remember { mutableStateOf(record?.isOvertime ?: false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showLocationDropdown by remember { mutableStateOf(false) }
    
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = DateUtils.parseDate(selectedDate)?.time ?: System.currentTimeMillis()
    )
    
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = MaterialTheme.shapes.large
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp)
            ) {
                // 标题
                Text(
                    text = if (record != null) "编辑记录" else "添加记录",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // 日期选择
                OutlinedTextField(
                    value = DateUtils.formatDisplayFullDate(selectedDate),
                    onValueChange = { },
                    label = { Text("日期") },
                    readOnly = true,
                    trailingIcon = {
                        Icon(Icons.Outlined.CalendarToday, contentDescription = null)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showDatePicker = true }
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // 工时输入
                OutlinedTextField(
                    value = hours,
                    onValueChange = { hours = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text("工时") },
                    suffix = { Text("小时") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // 类型选择
                Text(
                    text = "类型",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = !isOvertime && !isManual,
                        onClick = {
                            isOvertime = false
                            isManual = false
                        },
                        label = { Text("标准工") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = RecordStandard.copy(alpha = 0.2f),
                            selectedLabelColor = RecordStandard
                        )
                    )
                    FilterChip(
                        selected = isOvertime,
                        onClick = {
                            isOvertime = !isOvertime
                            if (isOvertime) isManual = false
                        },
                        label = { Text("加班 ⏰") }, // 增强加班标记：添加时钟emoji
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = RecordOvertime.copy(alpha = 0.3f),
                            selectedLabelColor = RecordOvertime
                        ),
                        leadingIcon = if (isOvertime) {
                            { Icon(Icons.Outlined.Warning, contentDescription = null, modifier = Modifier.size(16.dp)) }
                        } else null
                    )
                    FilterChip(
                        selected = isManual,
                        onClick = {
                            isManual = !isManual
                            if (isManual) isOvertime = false
                        },
                        label = { Text("手动折算") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = RecordManual.copy(alpha = 0.2f),
                            selectedLabelColor = RecordManual
                        )
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // 加班快捷选择 - 自定义记工时长
                Text(
                    text = "自定义工时",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // 自定义工时选项
                    val hourOptions = if (isOvertime) {
                        listOf(0.5, 1.0, 1.5, 2.0, 3.0, 4.0) // 加班快捷选项
                    } else {
                        listOf(4.0, 6.0, 7.0, 8.0, 10.0, 12.0) // 标准工快捷选项（默认7小时）
                    }
                    items(hourOptions) { option ->
                        FilterChip(
                            selected = hours.toDoubleOrNull() == option,
                            onClick = { hours = option.toString() },
                            label = { Text("${option}小时") }
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // 地点输入 - 强制输入
                ExposedDropdownMenuBox(
                    expanded = showLocationDropdown && recentLocations.isNotEmpty(),
                    onExpandedChange = { showLocationDropdown = it }
                ) {
                    OutlinedTextField(
                        value = location,
                        onValueChange = {
                            location = it
                            locationError = false // 输入时清除错误
                            showLocationDropdown = true
                        },
                        label = { Text("工地名称 *") }, // 必填标记
                        isError = locationError, // 显示错误状态
                        supportingText = if (locationError) {
                            { Text("请输入工地名称", color = MaterialTheme.colorScheme.error) }
                        } else null,
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = showLocationDropdown)
                        },
                        leadingIcon = {
                            Icon(Icons.Outlined.LocationOn, contentDescription = null)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor()
                    )
                    
                    if (recentLocations.isNotEmpty()) {
                        ExposedDropdownMenu(
                            expanded = showLocationDropdown,
                            onDismissRequest = { showLocationDropdown = false }
                        ) {
                            recentLocations.forEach { loc ->
                                DropdownMenuItem(
                                    text = { Text(loc) },
                                    onClick = {
                                        location = loc
                                        locationError = false
                                        showLocationDropdown = false
                                    }
                                )
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // 备注输入
                OutlinedTextField(
                    value = remark,
                    onValueChange = { remark = it },
                    label = { Text("备注") },
                    leadingIcon = {
                        Icon(Icons.Outlined.Note, contentDescription = null)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // 饭补开关
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "饭补",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Switch(
                        checked = mealSubsidy,
                        onCheckedChange = { mealSubsidy = it }
                    )
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // 按钮
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("取消")
                    }
                    Button(
                        onClick = {
                            // 验证地点是否为空（始终必填）
                            if (location.isBlank()) {
                                locationError = true
                                return@Button
                            }
                            
                            val hoursValue = hours.toDoubleOrNull() ?: 7.0
                            onSave(
                                selectedDate,
                                hoursValue,
                                isOvertime,
                                location.trim(),
                                remark,
                                mealSubsidy,
                                isManual
                            )
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("保存")
                    }
                }
            }
        }
    }
    
    // 日期选择器
    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            selectedDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                                .format(Date(millis))
                        }
                        showDatePicker = false
                    }
                ) {
                    Text("确认")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("取消")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

/**
 * 一键记工对话框 - 仅用于快速记工时输入工地名称
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickCheckInDialog(
    recentLocations: List<String> = emptyList(),
    onDismiss: () -> Unit,
    onConfirm: (location: String) -> Unit
) {
    var location by remember { mutableStateOf("") }
    var locationError by remember { mutableStateOf(false) }
    var showLocationDropdown by remember { mutableStateOf(false) }
    
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = MaterialTheme.shapes.large
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                // 标题
                Text(
                    text = "一键记工",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = "请输入工地名称",
                    style = MaterialTheme.typography.bodyLarge
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // 工地名称输入
                ExposedDropdownMenuBox(
                    expanded = showLocationDropdown && recentLocations.isNotEmpty(),
                    onExpandedChange = { showLocationDropdown = it }
                ) {
                    OutlinedTextField(
                        value = location,
                        onValueChange = {
                            location = it
                            locationError = false
                            showLocationDropdown = true
                        },
                        label = { Text("工地名称 *") },
                        isError = locationError,
                        supportingText = if (locationError) {
                            { Text("请输入工地名称", color = MaterialTheme.colorScheme.error) }
                        } else null,
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = showLocationDropdown)
                        },
                        leadingIcon = {
                            Icon(Icons.Outlined.LocationOn, contentDescription = null)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor()
                    )
                    
                    if (recentLocations.isNotEmpty()) {
                        ExposedDropdownMenu(
                            expanded = showLocationDropdown,
                            onDismissRequest = { showLocationDropdown = false }
                        ) {
                            recentLocations.forEach { loc ->
                                DropdownMenuItem(
                                    text = { Text(loc) },
                                    onClick = {
                                        location = loc
                                        locationError = false
                                        showLocationDropdown = false
                                    }
                                )
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // 按钮
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("取消")
                    }
                    Button(
                        onClick = {
                            if (location.isBlank()) {
                                locationError = true
                                return@Button
                            }
                            onConfirm(location.trim())
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("确认记工")
                    }
                }
            }
        }
    }
}
