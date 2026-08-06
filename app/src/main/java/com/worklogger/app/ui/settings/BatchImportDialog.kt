package com.worklogger.app.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Warning
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BatchImportDialog(
    dailyWorkHours: Double,
    analysis: BatchImportAnalysis?,
    onDismiss: () -> Unit,
    onAnalyze: (List<ParsedWorkEntry>, List<String>) -> Unit,
    onExecute: () -> Unit,
    onUpdateConflictDecision: (Int, ConflictDecision) -> Unit,
    onSetAllConflictDecisions: (ConflictDecision) -> Unit,
    onUpdateFailedLineCorrection: (Int, String?) -> Unit,
    onReparseFailedLines: () -> Unit,
    onClearAnalysis: () -> Unit
) {
    var rawText by remember { mutableStateOf("") }

    // 当文本变化时实时解析
    val previewResult = remember(rawText) {
        if (rawText.isNotBlank()) {
            TextRecordParser.parse(rawText, dailyWorkHours = dailyWorkHours)
        } else {
            null
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.ContentPaste, contentDescription = null) },
        title = {
            Text(if (analysis == null) "粘贴导入" else "确认导入")
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                if (analysis == null) {
                    // ====== 步骤1：输入阶段 ======
                    InputStep(
                        rawText = rawText,
                        onTextChange = { rawText = it },
                        previewResult = previewResult,
                        dailyWorkHours = dailyWorkHours
                    )
                } else {
                    // ====== 步骤2：分析结果确认阶段 ======
                    ReviewStep(
                        analysis = analysis,
                        dailyWorkHours = dailyWorkHours,
                        onUpdateConflictDecision = onUpdateConflictDecision,
                        onSetAllConflictDecisions = onSetAllConflictDecisions,
                        onUpdateFailedLineCorrection = onUpdateFailedLineCorrection,
                        onReparseFailedLines = onReparseFailedLines
                    )
                }
            }
        },
        confirmButton = {
            if (analysis == null) {
                // 输入阶段 → 分析
                TextButton(
                    onClick = {
                        if (previewResult != null) {
                            onAnalyze(previewResult.entries, previewResult.failedLines)
                        }
                    },
                    enabled = previewResult != null && (previewResult.entries.isNotEmpty() || previewResult.failedLines.isNotEmpty())
                ) {
                    Text("下一步")
                }
            } else {
                // 分析阶段 → 执行导入
                TextButton(
                    onClick = { onExecute() },
                    enabled = analysis.newRecords.isNotEmpty() || analysis.conflicts.any { it.decision == ConflictDecision.OVERWRITE }
                ) {
                    Text("确认导入")
                }
            }
        },
        dismissButton = {
            Row {
                if (analysis != null) {
                    TextButton(onClick = {
                        // 返回编辑阶段，保留文本
                        onClearAnalysis()
                    }) {
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

/**
 * 步骤1：文本输入 + 实时预览
 */
@Composable
private fun InputStep(
    rawText: String,
    onTextChange: (String) -> Unit,
    previewResult: com.worklogger.app.utils.ParseResult?,
    dailyWorkHours: Double
) {
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
        onValueChange = onTextChange,
        placeholder = { Text("在此粘贴文本...") },
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 150.dp),
        maxLines = 20
    )

    // 实时预览计数
    if (previewResult != null) {
        Spacer(modifier = Modifier.height(8.dp))
        val stdCount = previewResult.entries.count { !it.isOvertime }
        val otCount = previewResult.entries.count { it.isOvertime }
        val otHours = previewResult.entries.filter { it.isOvertime }.sumOf { it.overtimeHours }
        val failedCount = previewResult.failedLines.size

        Text(
            text = buildString {
                if (stdCount > 0 || otCount > 0) {
                    append("已识别：${stdCount}条标准工")
                    if (otCount > 0) append("，${otCount}条加班共${String.format("%.1f", otHours)}小时")
                }
                if (failedCount > 0) {
                    if (isNotEmpty()) append("，")
                    append("${failedCount}行无法识别")
                }
            },
            style = MaterialTheme.typography.bodySmall,
            color = if (failedCount > 0) MaterialTheme.colorScheme.error else Primary
        )
    }
}

/**
 * 步骤2：分析结果确认
 */
@Composable
private fun ReviewStep(
    analysis: BatchImportAnalysis,
    dailyWorkHours: Double,
    onUpdateConflictDecision: (Int, ConflictDecision) -> Unit,
    onSetAllConflictDecisions: (ConflictDecision) -> Unit,
    onUpdateFailedLineCorrection: (Int, String?) -> Unit,
    onReparseFailedLines: () -> Unit
) {
    // 摘要
    val summaryText = buildString {
        append("共${analysis.newRecords.size + analysis.conflicts.size}条记录")
        if (analysis.newRecords.isNotEmpty()) append("，${analysis.newRecords.size}条可新增")
        if (analysis.conflicts.isNotEmpty()) append("，${analysis.conflicts.size}条冲突")
        if (analysis.failedLines.isNotEmpty()) append("，${analysis.failedLines.size}条识别失败")
    }

    Text(
        text = summaryText,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = Primary
    )
    Spacer(modifier = Modifier.height(12.dp))

    // ====== 无冲突新增预览 ======
    if (analysis.newRecords.isNotEmpty()) {
        Text(
            text = "✅ 新增记录（${analysis.newRecords.size}条）",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(4.dp))

        val grouped = analysis.newRecords.groupBy { it.date }
        grouped.toSortedMap().forEach { (date, records) ->
            Text(
                text = DateUtils.formatDisplayFullDate(date),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
            )
            records.forEach { record ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(start = 8.dp, bottom = 2.dp)
                ) {
                    Icon(
                        imageVector = if (record.isOvertime) Icons.Outlined.Schedule else Icons.Outlined.Work,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = if (record.isOvertime) RecordOvertime else RecordStandard
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (record.isOvertime)
                            "加班 ${String.format("%.1f", record.hours)}小时 · ${record.location}"
                        else
                            "${dailyWorkHours.toInt()}小时 · ${record.location}",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
    }

    // ====== 冲突项 ======
    if (analysis.conflicts.isNotEmpty()) {
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        Text(
            text = "⚠️ 冲突记录（${analysis.conflicts.size}条）",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.error,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(4.dp))

        // 批量操作按钮
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { onSetAllConflictDecisions(ConflictDecision.KEEP_ORIGINAL) },
                modifier = Modifier.weight(1f)
            ) {
                Text("全部保留原有", style = MaterialTheme.typography.labelSmall)
            }
            OutlinedButton(
                onClick = { onSetAllConflictDecisions(ConflictDecision.OVERWRITE) },
                modifier = Modifier.weight(1f)
            ) {
                Text("全部用新覆盖", style = MaterialTheme.typography.labelSmall)
            }
        }
        Spacer(modifier = Modifier.height(8.dp))

        // 逐条冲突
        analysis.conflicts.forEachIndexed { index, conflict ->
            ConflictItemCard(
                index = index,
                conflict = conflict,
                dailyWorkHours = dailyWorkHours,
                onDecisionChange = { decision -> onUpdateConflictDecision(index, decision) }
            )
            if (index < analysis.conflicts.size - 1) {
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
    }

    // ====== 识别失败 ======
    if (analysis.failedLines.isNotEmpty()) {
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        Text(
            text = "❌ 识别失败（${analysis.failedLines.size}行）",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.error,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = "修改文本后点击\"重新解析\"，或留空跳过",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))

        analysis.failedLines.forEachIndexed { index, line ->
            FailedLineItem(
                index = index,
                originalLine = line,
                currentCorrection = analysis.failedLineCorrections[index],
                onCorrectionChange = { correctedText ->
                    onUpdateFailedLineCorrection(index, correctedText)
                }
            )
            if (index < analysis.failedLines.size - 1) {
                Spacer(modifier = Modifier.height(8.dp))
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 重新解析按钮
        Button(
            onClick = { onReparseFailedLines() },
            enabled = analysis.failedLineCorrections.any { it.value != null && it.value!!.isNotBlank() },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("重新解析修改的行")
        }
    }
}

/**
 * 单条冲突项卡片
 */
@Composable
private fun ConflictItemCard(
    index: Int,
    conflict: ConflictItem,
    dailyWorkHours: Double,
    onDecisionChange: (ConflictDecision) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // 日期标题
            Text(
                text = DateUtils.formatDisplayFullDate(conflict.newEntry.date),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(6.dp))

            // 原有记录
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "原有：",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Icon(
                    imageVector = if (conflict.existingRecord.isOvertime) Icons.Outlined.Schedule else Icons.Outlined.Work,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = if (conflict.existingRecord.isOvertime) RecordOvertime else RecordStandard
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = if (conflict.existingRecord.isOvertime)
                        "加班 ${String.format("%.1f", conflict.existingRecord.hours)}小时 · ${conflict.existingRecord.location}"
                    else
                        "${dailyWorkHours.toInt()}小时 · ${conflict.existingRecord.location}",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Spacer(modifier = Modifier.height(4.dp))

            // 新记录
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "新的：",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = Primary
                )
                Icon(
                    imageVector = if (conflict.newEntry.isOvertime) Icons.Outlined.Schedule else Icons.Outlined.Work,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = if (conflict.newEntry.isOvertime) RecordOvertime else RecordStandard
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = if (conflict.newEntry.isOvertime)
                        "加班 ${String.format("%.1f", conflict.newEntry.overtimeHours)}小时 · ${conflict.newEntry.location}"
                    else
                        "${dailyWorkHours.toInt()}小时 · ${conflict.newEntry.location}",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Spacer(modifier = Modifier.height(8.dp))

            // 决策单选
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = conflict.decision == ConflictDecision.KEEP_ORIGINAL,
                        onClick = { onDecisionChange(ConflictDecision.KEEP_ORIGINAL) }
                    )
                    Text("保留原有", style = MaterialTheme.typography.bodySmall)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = conflict.decision == ConflictDecision.OVERWRITE,
                        onClick = { onDecisionChange(ConflictDecision.OVERWRITE) }
                    )
                    Text("用新覆盖", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

/**
 * 识别失败行项
 */
@Composable
private fun FailedLineItem(
    index: Int,
    originalLine: String,
    currentCorrection: String?,
    onCorrectionChange: (String?) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.Warning,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = originalLine,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            OutlinedTextField(
                value = currentCorrection ?: "",
                onValueChange = { newText ->
                    onCorrectionChange(if (newText.isBlank()) null else newText)
                },
                placeholder = { Text("修正格式后重新解析，留空则跳过", style = MaterialTheme.typography.bodySmall) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                textStyle = MaterialTheme.typography.bodySmall
            )
        }
    }
}
