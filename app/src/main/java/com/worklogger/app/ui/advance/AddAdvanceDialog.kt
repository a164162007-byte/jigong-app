package com.worklogger.app.ui.advance

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
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.worklogger.app.model.AdvanceSalaryRecord
import com.worklogger.app.ui.theme.*
import com.worklogger.app.utils.DateUtils
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddAdvanceSalaryDialog(
    record: AdvanceSalaryRecord? = null,
    recentLocations: List<String> = emptyList(),
    onDismiss: () -> Unit,
    onSave: (date: String, time: String, location: String, amount: Double, remark: String) -> Unit
) {
    val now = Calendar.getInstance()
    val defaultDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(now.time)
    val defaultTime = SimpleDateFormat("HH:mm", Locale.getDefault()).format(now.time)

    var selectedDate by remember { mutableStateOf(record?.date ?: defaultDate) }
    var selectedTime by remember { mutableStateOf(record?.time ?: defaultTime) }
    var location by remember { mutableStateOf(record?.location ?: "") }
    var amount by remember { mutableStateOf(record?.amount?.toString() ?: "") }
    var remark by remember { mutableStateOf(record?.remark ?: "") }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var showLocationDropdown by remember { mutableStateOf(false) }
    var amountError by remember { mutableStateOf(false) }
    var locationError by remember { mutableStateOf(false) }

    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = DateUtils.parseDate(selectedDate)?.time ?: System.currentTimeMillis()
    )

    val timePickerState = rememberTimePickerState(
        initialHour = selectedTime.split(":")[0].toIntOrNull() ?: now.get(Calendar.HOUR_OF_DAY),
        initialMinute = selectedTime.split(":")[1].toIntOrNull() ?: now.get(Calendar.MINUTE),
        is24Hour = true
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
                    text = if (record != null) "编辑预支记录" else "添加预支工资",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(24.dp))

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

                OutlinedTextField(
                    value = selectedTime,
                    onValueChange = { },
                    label = { Text("时间") },
                    readOnly = true,
                    trailingIcon = {
                        Icon(Icons.Outlined.Schedule, contentDescription = null)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showTimePicker = true }
                )

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
                        label = { Text("地点 *") },
                        isError = locationError,
                        supportingText = if (locationError) {
                            { Text("请输入地点", color = MaterialTheme.colorScheme.error) }
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

                OutlinedTextField(
                    value = amount,
                    onValueChange = {
                        val filtered = it.filter { c -> c.isDigit() || c == '.' }
                        amount = filtered
                        amountError = false
                    },
                    label = { Text("预支金额 *") },
                    prefix = { Text("¥ ") },
                    isError = amountError,
                    supportingText = if (amountError) {
                        { Text("请输入有效金额", color = MaterialTheme.colorScheme.error) }
                    } else null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(listOf(100.0, 200.0, 500.0, 1000.0, 2000.0, 5000.0)) { option ->
                        FilterChip(
                            selected = amount.toDoubleOrNull() == option,
                            onClick = { amount = option.toString() },
                            label = { Text("¥${option.toInt()}") }
                        )
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

                Spacer(modifier = Modifier.height(24.dp))

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
                            val amountValue = amount.toDoubleOrNull()
                            if (amountValue == null || amountValue <= 0) {
                                amountError = true
                                return@Button
                            }
                            if (location.isBlank()) {
                                locationError = true
                                return@Button
                            }
                            onSave(
                                selectedDate,
                                selectedTime,
                                location.trim(),
                                amountValue,
                                remark
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

    if (showTimePicker) {
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            title = { Text("选择时间") },
            text = {
                TimePicker(state = timePickerState)
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        selectedTime = String.format(
                            "%02d:%02d",
                            timePickerState.hour,
                            timePickerState.minute
                        )
                        showTimePicker = false
                    }
                ) {
                    Text("确认")
                }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) {
                    Text("取消")
                }
            }
        )
    }
}
