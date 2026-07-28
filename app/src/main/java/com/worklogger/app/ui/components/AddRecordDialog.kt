package com.worklogger.app.ui.components

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
    var locationError by remember { mutableStateOf(false) }
    var selectedDate by remember { mutableStateOf(record?.date ?: DateUtils.today()) }
    var hours by remember { mutableStateOf(record?.hours?.toString() ?: "9") }
    var location by remember { mutableStateOf(record?.location ?: "") }
    var remark by remember { mutableStateOf(record?.remark ?: "") }
    var mealSubsidy by remember { mutableStateOf(record?.mealSubsidy ?: true) }
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
                Text(
                    text = if (record != null) "编辑记录" else "添加记录",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showDatePicker = true }
                ) {
                    OutlinedTextField(
                        value = DateUtils.formatDisplayFullDate(selectedDate),
                        onValueChange = { },
                        label = { Text("日期") },
                        readOnly = true,
                        trailingIcon = {
                            Icon(Icons.Outlined.CalendarToday, contentDescription = null)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = false,
                        colors = OutlinedTextFieldDefaults.colors(
                            disabledTextColor = MaterialTheme.colorScheme.onSurface,
                            disabledBorderColor = MaterialTheme.colorScheme.outline,
                            disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            disabledTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                OutlinedTextField(
                    value = hours,
                    onValueChange = { hours = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text("工时") },
                    suffix = { Text("小时") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                
                val excessHours = (hours.toDoubleOrNull() ?: 0.0) - 9.0
                if (!isOvertime && excessHours > 0) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "超出标准工时${String.format("%.1f", excessHours)}小时将自动折算为加班",
                        style = MaterialTheme.typography.bodySmall,
                        color = RecordOvertime
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = "类型",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = !isOvertime && !isManual,
                        onClick = {
                            isOvertime = false
                            isManual = false
                            mealSubsidy = true
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
                            if (isOvertime) {
                                isManual = false
                                mealSubsidy = false
                            } else {
                                // 取消加班时回到标准工，饭补强制为true
                                mealSubsidy = true
                            }
                        },
                        label = { Text("加班") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = RecordOvertime.copy(alpha = 0.3f),
                            selectedLabelColor = RecordOvertime
                        )
                    )
                    FilterChip(
                        selected = isManual,
                        onClick = {
                            isManual = !isManual
                            if (isManual) {
                                isOvertime = false
                                mealSubsidy = true  // 手动折算默认有饭补
                            } else {
                                // 取消手动折算时回到标准工，饭补强制为true
                                mealSubsidy = true
                            }
                        },
                        label = { Text("手动折算") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = RecordManual.copy(alpha = 0.2f),
                            selectedLabelColor = RecordManual
                        )
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = "自定义工时",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(listOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16)) { h ->
                        FilterChip(
                            selected = hours.toDoubleOrNull() == h.toDouble(),
                            onClick = { hours = h.toString() },
                            label = { Text("${h}h") }
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
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
                        modifier = Modifier.fillMaxWidth().menuAnchor()
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
                
                val mealSubsidyEnabled = !isOvertime && isManual
                val mealSubsidyText = when {
                    !isOvertime && !isManual -> "饭补（标准工必含）"
                    isOvertime -> "饭补（加班无饭补）"
                    else -> "饭补"
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = mealSubsidyText,
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (!mealSubsidyEnabled) MaterialTheme.colorScheme.onSurfaceVariant 
                                else MaterialTheme.colorScheme.onSurface
                    )
                    Switch(
                        checked = mealSubsidy,
                        onCheckedChange = { if (mealSubsidyEnabled) mealSubsidy = it },
                        enabled = mealSubsidyEnabled
                    )
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                        Text("取消")
                    }
                    Button(
                        onClick = {
                            if (location.isBlank()) {
                                locationError = true
                                return@Button
                            }
                            val hoursValue = hours.toDoubleOrNull() ?: 7.0
                            onSave(selectedDate, hoursValue, isOvertime, location.trim(), remark, mealSubsidy, isManual)
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("保存")
                    }
                }
            }
        }
    }
    
    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            selectedDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(millis))
                        }
                        showDatePicker = false
                    }
                ) { Text("确认") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("取消") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickCheckInDialog(
    recentLocations: List<String> = emptyList(),
    onDismiss: () -> Unit,
    onConfirm: (location: String, date: String, overtimeHours: Double) -> Unit
) {
    var location by remember { mutableStateOf("") }
    var locationError by remember { mutableStateOf(false) }
    var showLocationDropdown by remember { mutableStateOf(false) }
    var selectedDate by remember { mutableStateOf(DateUtils.today()) }
    var showDatePicker by remember { mutableStateOf(false) }
    var overtimeHours by remember { mutableStateOf("0") }
    
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
                    .padding(24.dp)
            ) {
                Text(
                    text = "一键记工",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // 日期选择
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showDatePicker = true }
                ) {
                    OutlinedTextField(
                        value = DateUtils.formatDisplayFullDate(selectedDate),
                        onValueChange = { },
                        label = { Text("记工日期") },
                        readOnly = true,
                        trailingIcon = {
                            Icon(Icons.Outlined.CalendarToday, contentDescription = null)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = false,
                        colors = OutlinedTextFieldDefaults.colors(
                            disabledTextColor = MaterialTheme.colorScheme.onSurface,
                            disabledBorderColor = MaterialTheme.colorScheme.outline,
                            disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            disabledTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
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
                        modifier = Modifier.fillMaxWidth().menuAnchor()
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
                                        showLocationDropdown = false
                                    }
                                )
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // 加班工时输入
                OutlinedTextField(
                    value = overtimeHours,
                    onValueChange = { overtimeHours = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text("加班工时（可选）") },
                    placeholder = { Text("0") },
                    suffix = { Text("小时") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                
                val overtimeValue = overtimeHours.toDoubleOrNull() ?: 0.0
                if (overtimeValue > 0) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "加班无饭补，仅标准工计入饭补",
                        style = MaterialTheme.typography.bodySmall,
                        color = RecordOvertime
                    )
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                        Text("取消")
                    }
                    Button(
                        onClick = {
                            if (location.isBlank()) {
                                locationError = true
                                return@Button
                            }
                            onConfirm(location.trim(), selectedDate, overtimeValue)
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("确认记工")
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