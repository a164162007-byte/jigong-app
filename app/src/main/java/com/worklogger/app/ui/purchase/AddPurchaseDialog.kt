package com.worklogger.app.ui.purchase

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Note
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.worklogger.app.model.AdvancePurchaseRecord
import com.worklogger.app.ui.theme.*
import com.worklogger.app.utils.DateUtils
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddPurchaseDialog(
    record: AdvancePurchaseRecord? = null,
    recentLocations: List<String> = emptyList(),
    onDismiss: () -> Unit,
    onSave: (date: String, itemName: String, location: String, amount: Double, quantity: Int, remark: String) -> Unit
) {
    val now = Calendar.getInstance()
    val defaultDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(now.time)

    var selectedDate by remember { mutableStateOf(record?.date ?: defaultDate) }
    var itemName by remember { mutableStateOf(record?.itemName ?: "") }
    var location by remember { mutableStateOf(record?.location ?: "") }
    var amount by remember { mutableStateOf(record?.amount?.toString() ?: "") }
    var quantity by remember { mutableStateOf(record?.quantity?.toString() ?: "1") }
    var remark by remember { mutableStateOf(record?.remark ?: "") }
    var showDatePicker by remember { mutableStateOf(false) }
    var showLocationDropdown by remember { mutableStateOf(false) }
    var itemNameError by remember { mutableStateOf(false) }
    var locationError by remember { mutableStateOf(false) }
    var amountError by remember { mutableStateOf(false) }

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
                    text = if (record != null) "编辑购买记录" else "添加垫资购买",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(24.dp))

                // 日期
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

                // 购买名称（强制）
                OutlinedTextField(
                    value = itemName,
                    onValueChange = {
                        itemName = it
                        itemNameError = false
                    },
                    label = { Text("购买名称 *") },
                    isError = itemNameError,
                    supportingText = if (itemNameError) {
                        { Text("请输入购买名称", color = MaterialTheme.colorScheme.error) }
                    } else null,
                    leadingIcon = {
                        Icon(Icons.Outlined.ShoppingCart, contentDescription = null)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(16.dp))

                // 工地名称（强制）
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

                Spacer(modifier = Modifier.height(16.dp))

                // 金额
                OutlinedTextField(
                    value = amount,
                    onValueChange = {
                        val filtered = it.filter { c -> c.isDigit() || c == '.' }
                        amount = filtered
                        amountError = false
                    },
                    label = { Text("金额 *") },
                    prefix = { Text("¥ ") },
                    isError = amountError,
                    supportingText = if (amountError) {
                        { Text("请输入有效金额", color = MaterialTheme.colorScheme.error) }
                    } else null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                // 数量
                OutlinedTextField(
                    value = quantity,
                    onValueChange = {
                        val filtered = it.filter { c -> c.isDigit() }
                        quantity = filtered.ifEmpty { "1" }
                    },
                    label = { Text("数量") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(16.dp))

                // 备注
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
                            if (itemName.isBlank()) {
                                itemNameError = true
                                return@Button
                            }
                            if (location.isBlank()) {
                                locationError = true
                                return@Button
                            }
                            if (amountValue == null || amountValue <= 0) {
                                amountError = true
                                return@Button
                            }
                            val quantityValue = quantity.toIntOrNull() ?: 1
                            onSave(
                                selectedDate,
                                itemName.trim(),
                                location.trim(),
                                amountValue,
                                quantityValue,
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
}
