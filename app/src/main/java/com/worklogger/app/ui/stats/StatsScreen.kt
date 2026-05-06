package com.worklogger.app.ui.stats

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.*
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.worklogger.app.ui.components.StatsCard
import com.worklogger.app.ui.theme.*
import com.worklogger.app.utils.DateUtils
import java.text.NumberFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    viewModel: StatsViewModel
) {
    val uiState by viewModel.uiState.collectAsState()
    val currencyFormat = remember { NumberFormat.getCurrencyInstance(Locale.CHINA) }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "统计",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
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
                // 月份选择器
                item {
                    MonthSelector(
                        yearMonth = uiState.selectedYearMonth,
                        onPrevious = { viewModel.previousMonth() },
                        onNext = { viewModel.nextMonth() }
                    )
                }
                
                // 统计卡片网格 2x2
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        StatsCard(
                            title = "标准工天数",
                            value = String.format("%.1f", uiState.currentStats.standardDays),
                            color = RecordStandard,
                            modifier = Modifier.weight(1f)
                        )
                        StatsCard(
                            title = "手动折算天数",
                            value = String.format("%.1f", uiState.currentStats.manualDays),
                            color = RecordManual,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        StatsCard(
                            title = "加班总小时",
                            value = String.format("%.1f", uiState.currentStats.overtimeHours),
                            color = RecordOvertime,
                            modifier = Modifier.weight(1f)
                        )
                        StatsCard(
                            title = "加班折算天数",
                            value = String.format("%.1f", uiState.currentStats.overtimeDays),
                            color = RecordOvertime,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                
                // 总标准工详情
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
                                text = "总标准工",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "标准工",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = String.format("%.1f 天", uiState.currentStats.standardDays),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "手动折算",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = String.format("%.1f 天", uiState.currentStats.manualDays),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "加班折算",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = String.format("%.1f 天", uiState.currentStats.overtimeDays),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "合计",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = String.format("%.2f 天", uiState.currentStats.totalStandard),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Primary
                                )
                            }
                        }
                    }
                }
                
                // 饭补和工资统计
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        StatsCard(
                            title = "饭补合计",
                            value = currencyFormat.format(uiState.currentStats.mealSubsidyTotal),
                            subtitle = "日标准 ${currencyFormat.format(uiState.settings.mealSubsidyStandard)}",
                            color = Success,
                            modifier = Modifier.weight(1f)
                        )
                        StatsCard(
                            title = "应发工资",
                            value = currencyFormat.format(uiState.currentStats.wageTotal),
                            subtitle = "日标准 ${currencyFormat.format(uiState.currentStats.dailyWage)}",
                            color = Primary,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                
                // 数据对比
                item {
                    ComparisonCard(
                        currentHours = uiState.currentStats.totalStandard,
                        currentMealSubsidy = uiState.currentStats.mealSubsidyTotal,
                        currentWage = uiState.currentStats.wageTotal + uiState.currentStats.mealSubsidyTotal,
                        previousHours = uiState.previousStats.totalStandard,
                        previousMealSubsidy = uiState.previousStats.mealSubsidyTotal,
                        previousWage = uiState.previousStats.wageTotal + uiState.previousStats.mealSubsidyTotal
                    )
                }
                
                // 加班分布
                item {
                    OvertimeDistributionCard(
                        distribution = uiState.overtimeDistribution,
                        totalDays = uiState.totalOvertimeDays,
                        totalHours = uiState.totalOvertimeHours
                    )
                }
                
                // 近6个月趋势图
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
                                text = "近6个月工时趋势",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            if (uiState.monthlyTrend.isNotEmpty()) {
                                LineChartView(
                                    data = uiState.monthlyTrend,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(200.dp)
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(200.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "暂无数据",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
                
                // 地点分布饼图
                if (uiState.locationDistribution.isNotEmpty()) {
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
                                    text = "本月地点分布",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                
                                PieChartView(
                                    data = uiState.locationDistribution,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(200.dp)
                                )
                            }
                        }
                    }
                }
                
                item {
                    Spacer(modifier = Modifier.height(80.dp))
                }
            }
        }
    }
}

@Composable
fun MonthSelector(
    yearMonth: String,
    onPrevious: () -> Unit,
    onNext: () -> Unit
) {
    val parts = yearMonth.split("-")
    val displayText = if (parts.size == 2) {
        "${parts[0]}年${parts[1]}月"
    } else yearMonth
    
    val isNextEnabled = yearMonth < DateUtils.currentYearMonth()
    
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onPrevious) {
            Icon(
                Icons.Default.KeyboardArrowLeft,
                contentDescription = "上一月"
            )
        }
        Text(
            text = displayText,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        IconButton(onClick = onNext, enabled = isNextEnabled) {
            Icon(
                Icons.Default.KeyboardArrowRight,
                contentDescription = "下一月",
                tint = if (isNextEnabled) MaterialTheme.colorScheme.onSurface 
                       else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
            )
        }
    }
}

@Composable
fun ComparisonCard(
    currentHours: Double,
    currentMealSubsidy: Double,
    currentWage: Double,
    previousHours: Double,
    previousMealSubsidy: Double,
    previousWage: Double
) {
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
                text = "本月 vs 上月",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(12.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "工时",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${formatDiff(currentHours - previousHours)}",
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (currentHours >= previousHours) Increase else Decrease,
                        fontWeight = FontWeight.Bold
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "饭补",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${formatDiff(currentMealSubsidy - previousMealSubsidy)}",
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (currentMealSubsidy >= previousMealSubsidy) Increase else Decrease,
                        fontWeight = FontWeight.Bold
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "工资",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${formatDiff(currentWage - previousWage)}",
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (currentWage >= previousWage) Increase else Decrease,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun OvertimeDistributionCard(
    distribution: Map<Double, Int>,
    totalDays: Double,
    totalHours: Double
) {
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
                text = "加班分布",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(12.dp))
            
            val labels = listOf(
                0.0 to "无加班",
                0.5 to "0.5天",
                1.0 to "1天",
                1.5 to "1.5天",
                2.0 to "2天+"
            )
            
            labels.forEach { (days, label) ->
                val count = distribution[days] ?: 0
                val barColor = if (days == 0.0) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                               else RecordOvertime
                
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.width(50.dp)
                    )
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(20.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        val maxCount = distribution.values.maxOrNull() ?: 1
                        val progress = if (maxCount > 0) count.toFloat() / maxCount else 0f
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(progress)
                                .clip(RoundedCornerShape(4.dp))
                                .background(barColor.copy(alpha = if (days == 0.0) 0.3f else 0.8f))
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "$count 天",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.width(40.dp)
                    )
                }
            }
            
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "总加班天数",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = String.format("%.1f 天", totalDays),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = RecordOvertime
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "总加班小时",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = String.format("%.1f 小时", totalHours),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = RecordOvertime
                )
            }
        }
    }
}

@Composable
fun LineChartView(
    data: List<Pair<String, Double>>,
    modifier: Modifier = Modifier
) {
    val primaryColor = Primary.toArgb()
    
    AndroidView(
        factory = { context ->
            LineChart(context).apply {
                description.isEnabled = false
                legend.isEnabled = false
                setTouchEnabled(false)
                setDrawGridBackground(false)
                
                xAxis.apply {
                    position = XAxis.XAxisPosition.BOTTOM
                    setDrawGridLines(false)
                    granularity = 1f
                    textColor = AndroidColor.GRAY
                }
                
                axisLeft.apply {
                    setDrawGridLines(true)
                    gridColor = AndroidColor.LTGRAY
                    textColor = AndroidColor.GRAY
                }
                
                axisRight.isEnabled = false
            }
        },
        update = { chart ->
            val entries = data.mapIndexed { index, (_, value) ->
                Entry(index.toFloat(), value.toFloat())
            }
            
            val dataSet = LineDataSet(entries, "工时").apply {
                color = primaryColor
                lineWidth = 2f
                setDrawCircles(true)
                circleRadius = 4f
                setCircleColor(primaryColor)
                setDrawValues(true)
                valueTextColor = AndroidColor.GRAY
                valueTextSize = 10f
                mode = LineDataSet.Mode.CUBIC_BEZIER
                setDrawFilled(true)
                fillColor = primaryColor
                fillAlpha = 50
            }
            
            chart.xAxis.valueFormatter = IndexAxisValueFormatter(
                data.map { it.first.substring(5) } // MM格式
            )
            
            chart.data = LineData(dataSet)
            chart.invalidate()
        },
        modifier = modifier
    )
}

@Composable
fun PieChartView(
    data: Map<String, Int>,
    modifier: Modifier = Modifier
) {
    val colors = listOf(
        Primary.toArgb(),
        RecordOvertime.toArgb(),
        RecordManual.toArgb(),
        Success.toArgb(),
        Warning.toArgb()
    )
    
    AndroidView(
        factory = { context ->
            PieChart(context).apply {
                description.isEnabled = false
                legend.isEnabled = true
                legend.textColor = AndroidColor.GRAY
                setUsePercentValues(true)
                setDrawEntryLabels(false)
                isDrawHoleEnabled = true
                holeRadius = 40f
                transparentCircleRadius = 45f
                setHoleColor(AndroidColor.TRANSPARENT)
            }
        },
        update = { chart ->
            val entries = data.map { (label, value) ->
                PieEntry(value.toFloat(), label)
            }
            
            val dataSet = PieDataSet(entries, "").apply {
                this.colors = colors.take(entries.size)
                valueTextColor = AndroidColor.WHITE
                valueTextSize = 12f
            }
            
            chart.data = PieData(dataSet)
            chart.invalidate()
        },
        modifier = modifier
    )
}

private fun formatDiff(value: Double): String {
    return when {
        value > 0 -> "+${String.format("%.1f", value)}"
        value < 0 -> String.format("%.1f", value)
        else -> "0"
    }
}
