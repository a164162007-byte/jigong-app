package com.worklogger.app.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Work
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.worklogger.app.ui.theme.*
import com.worklogger.app.utils.DateUtils
import com.worklogger.app.utils.ParsedWorkEntry
import com.worklogger.app.utils.TextRecordParser

@Composable
fun BatchImportDialog(
    dailyWorkHours: Double,
    onDismiss: () -> Unit,
    onImport: (List<ParsedWorkEntry>) -> Unit
) {
    var rawText by remember { mutableStateOf("") }
    var showPreview by remember { mutableStateOf(false) }
    var parsedEntries by remember { mutableStateOf<List<ParsedWorkEntry>>(emptyList()) }

    // 当文本变化时实时解析
    val previewEntries = remember(rawText) {
        if (rawText.isNotBlank()) {
            TextRecordParser.parse(rawText, dailyWorkHours = dailyWorkHours)
        } else {
            emptyList()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.ContentPaste, contentDescription = null) },
        title = { Text("粘贴导入") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                if (!showPreview) {
                    // 输入阶段
                    Text(
                        text = "粘贴便签文本，自动解析记工记录",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "格式示例：\n2月\n1号北京土城\n2号北京土城（加班3小时）",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = rawText,
                        onValueChange = { rawText = it },
                        placeholder = { Text("在此粘贴文本...") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 150.dp),
                        maxLines = 20
                    )

                    // 实时预览计数
                    if (previewEntries.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        val stdCount = previewEntries.count { !it.isOvertime }
                        val otCount = previewEntries.count { it.isOvertime }
                        val otHours = previewEntries.filter { it.isOvertime }.sumOf { it.overtimeHours }
                        Text(
                            text = "已识别：${stdCount}条标准工" +
                                    if (otCount > 0) "，${otCount}条加班共${String.format("%.1f", otHours)}小时" else "",
                            style = MaterialTheme.typography.bodySmall,
                            color = Primary
                        )
                    }
                } else {
                    // 预览阶段 - 按日期分组展示
                    Text(
                        text = "预览导入内容（${parsedEntries.size}条记录）",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    val grouped = parsedEntries.groupBy { it.date }
                    grouped.forEach { (date, entries) ->
                        Text(
                            text = DateUtils.formatDisplayFullDate(date),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                        )
                        entries.forEach { entry ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(start = 8.dp, bottom = 2.dp)
                            ) {
                                Icon(
                                    imageVector = if (entry.isOvertime) Icons.Outlined.Schedule else Icons.Outlined.Work,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = if (entry.isOvertime) RecordOvertime else RecordStandard
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (entry.isOvertime)
                                        "加班 ${String.format("%.1f", entry.overtimeHours)}小时 · ${entry.location}"
                                    else
                                        "${dailyWorkHours.toInt()}小时 · ${entry.location}",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (!showPreview) {
                TextButton(
                    onClick = {
                        parsedEntries = previewEntries
                        showPreview = true
                    },
                    enabled = previewEntries.isNotEmpty()
                ) {
                    Text("预览")
                }
            } else {
                TextButton(
                    onClick = { onImport(parsedEntries) }
                ) {
                    Text("确认导入")
                }
            }
        },
        dismissButton = {
            Row {
                if (showPreview) {
                    TextButton(onClick = { showPreview = false }) {
                        Text("返回编辑")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                }
                TextButton(onClick = onDismiss) {
                    Text("取消")
                }
            }
        }
    )
}
