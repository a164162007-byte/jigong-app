package com.worklogger.app.ui.stats

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.worklogger.app.model.MonthlySalarySettlement
import com.worklogger.app.ui.theme.*
import java.text.NumberFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettlementDialog(
    settlement: MonthlySalarySettlement?,
    allLocations: List<String>,
    selectedLocation: String,
    onLocationChange: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val currencyFormat = remember { NumberFormat.getCurrencyInstance(Locale.CHINA) }
    val locationScrollState = rememberScrollState()

    // 安全计算标题：settlement 为 null 时显示加载中提示
    val periodLabel = settlement?.let { s ->
        if (s.yearMonth.contains("年")) {
            s.yearMonth
        } else {
            val parts = s.yearMonth.split("-")
            if (parts.size == 2) {
                try {
                    "${parts[0]}年${parts[1].toInt()}月"
                } catch (e: NumberFormatException) {
                    s.yearMonth
                }
            } else {
                s.yearMonth
            }
        }
    } ?: "加载中..."

    val locationLabel = if (selectedLocation.isNotEmpty()) " · $selectedLocation" else ""

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "工资结算单",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "关闭")
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 月份标签 - 始终安全渲染
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Primary.copy(alpha = 0.08f)
                ) {
                    Text(
                        text = "$periodLabel$locationLabel",
                        style = MaterialTheme.typography.labelLarge,
                        color = Primary,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                    )
                }

                // 地点筛选
                if (allLocations.size > 1) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.horizontalScroll(locationScrollState)
                    ) {
                        FilterChip(
                            selected = selectedLocation.isEmpty(),
                            onClick = { onLocationChange("") },
                            label = { Text("全部", style = MaterialTheme.typography.labelSmall) }
                        )
                        allLocations.forEach { loc ->
                            FilterChip(
                                selected = selectedLocation == loc,
                                onClick = { onLocationChange(loc) },
                                label = { Text(loc, style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }
                }

                // 数据区域：null 时显示加载指示器
                if (settlement == null) {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(32.dp))
                    }
                } else {
                    // 收入明细卡片
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "收入明细",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )

                            SettlementRow(
                                "标准工",
                                "${String.format("%.1f", settlement.standardDays)}天 × ${currencyFormat.format(if (settlement.standardDays > 0) settlement.standardWage / settlement.standardDays else 0.0)}",
                                currencyFormat.format(settlement.standardWage),
                                RecordStandard
                            )

                            SettlementRow(
                                "加班工",
                                "${String.format("%.1f", settlement.overtimeDays)}天 × ${currencyFormat.format(if (settlement.overtimeDays > 0) settlement.overtimeWage / settlement.overtimeDays else 0.0)}",
                                currencyFormat.format(settlement.overtimeWage),
                                RecordOvertime
                            )

                            SettlementRow(
                                "手动折算",
                                "${String.format("%.1f", settlement.manualDays)}天 × ${currencyFormat.format(if (settlement.manualDays > 0) settlement.manualWage / settlement.manualDays else 0.0)}",
                                currencyFormat.format(settlement.manualWage),
                                RecordManual
                            )

                            SettlementRow(
                                "饭补",
                                "合计",
                                currencyFormat.format(settlement.mealSubsidyTotal),
                                Success
                            )

                            Divider(modifier = Modifier.padding(vertical = 4.dp))

                            SettlementRow(
                                "应发合计",
                                "",
                                currencyFormat.format(settlement.totalEarning),
                                Primary,
                                isBold = true
                            )
                        }
                    }

                    // 扣减 & 实发
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "扣减",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )

                            SettlementRow(
                                "预支工资",
                                "",
                                "-${currencyFormat.format(settlement.advanceAmount)}",
                                Decrease
                            )

                            Divider(modifier = Modifier.padding(vertical = 4.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "实发工资",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = currencyFormat.format(settlement.netPayable),
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = if (settlement.netPayable >= 0) Primary else Decrease
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (settlement != null) {
                        val text = generateSettlementText(settlement, currencyFormat)
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, text)
                        }
                        context.startActivity(Intent.createChooser(shareIntent, "分享结算单"))
                    }
                },
                enabled = settlement != null,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("分享结算单")
            }
        },
        dismissButton = null
    )
}

@Composable
private fun SettlementRow(
    label: String,
    detail: String,
    amount: String,
    amountColor: Color,
    isBold: Boolean = false
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = if (isBold) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium,
                fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal
            )
            if (detail.isNotEmpty()) {
                Text(
                    text = detail,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Text(
            text = amount,
            style = if (isBold) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium,
            fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal,
            color = amountColor
        )
    }
}

private fun generateSettlementText(
    settlement: MonthlySalarySettlement,
    currencyFormat: NumberFormat
): String {
    val periodLabel = if (settlement.yearMonth.contains("年"))
        settlement.yearMonth else {
        val parts = settlement.yearMonth.split("-")
        if (parts.size == 2) {
            try {
                "${parts[0]}年${parts[1].toInt()}月"
            } catch (e: NumberFormatException) {
                settlement.yearMonth
            }
        } else {
            settlement.yearMonth
        }
    }
    val locationLabel = if (settlement.location.isNotEmpty()) "（${settlement.location}）" else ""

    return buildString {
        appendLine("【$periodLabel 工资结算单】$locationLabel")
        appendLine()
        appendLine("━━ 收入明细 ━━")
        appendLine("标准工：${String.format("%.1f", settlement.standardDays)}天 → ${currencyFormat.format(settlement.standardWage)}")
        appendLine("加班工：${String.format("%.1f", settlement.overtimeDays)}天 → ${currencyFormat.format(settlement.overtimeWage)}")
        appendLine("手动折算：${String.format("%.1f", settlement.manualDays)}天 → ${currencyFormat.format(settlement.manualWage)}")
        appendLine("饭补合计：${currencyFormat.format(settlement.mealSubsidyTotal)}")
        appendLine("应发合计：${currencyFormat.format(settlement.totalEarning)}")
        appendLine()
        appendLine("━━ 扣减 ━━")
        appendLine("预支工资：-${currencyFormat.format(settlement.advanceAmount)}")
        appendLine()
        appendLine("实发工资：${currencyFormat.format(settlement.netPayable)}")
        appendLine()
        appendLine("—— 记工App 生成")
    }
}
