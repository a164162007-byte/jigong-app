package com.worklogger.app.ui.stats

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.*
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.worklogger.app.model.WorkRecord
import com.worklogger.app.ui.components.*
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
    
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) { viewModel.refresh() }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val currencyFormat = remember { NumberFormat.getCurrencyInstance(Locale.CHINA) }
    
    Scaffold(
        topBar = {
            if (uiState.isBatchMode) {
                TopAppBar(
                    title = {
                        Text(text = "已选择 ${uiState.selectedRecordIds.size} 条", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
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
                        IconButton(onClick = { viewModel.showBatchDeleteConfirm() }, enabled = uiState.selectedRecordIds.isNotEmpty()) {
                            Icon(Icons.Default.DeleteSweep, contentDescription = "批量删除",
                                tint = if (uiState.selectedRecordIds.isNotEmpty()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f))
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Primary.copy(alpha = 0.1f))
                )
            } else {
                TopAppBar(
                    title = {
                        Text(text = "统计", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    },
                    actions = {
                        if (uiState.monthlyDetailRecords.isNotEmpty()) {
                            IconButton(onClick = { viewModel.enterBatchMode() }) {
                                Icon(Icons.Default.FilterList, contentDescription = "批量操作")
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
                )
            }
        }
    ) { padding ->
        if (uiState.isLoading) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 月/年视图切换
                if (!uiState.isBatchMode) {
                    item {
                        ViewModeToggle(selectedPeriod = uiState.selectedPeriod, onModeSelected = { viewModel.setViewMode(it) })
                    }
                    
                    // 时间选择器
                    item {
                        if (uiState.selectedPeriod == "year") {
                            YearSelector(
                                year = uiState.selectedYear,
                                onPrevious = { viewModel.previousYear() },
                                onNext = { viewModel.nextYear() }
                            )
                        } else {
                            MonthSelector(
                                yearMonth = uiState.selectedYearMonth,
                                onPrevious = { viewModel.previousMonth() },
                                onNext = { viewModel.nextMonth() }
                            )
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
                }
                
                // 统计卡片 2x2
                item {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        StatsCard(title = "标准工天数", value = String.format("%.1f", uiState.currentStats.standardDays), color = RecordStandard, modifier = Modifier.weight(1f))
                        StatsCard(title = "手动折算天数", value = String.format("%.1f", uiState.currentStats.manualDays), color = RecordManual, modifier = Modifier.weight(1f))
                    }
                }
                
                item {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        StatsCard(title = "加班总小时", value = String.format("%.1f", uiState.currentStats.overtimeHours), color = RecordOvertime, modifier = Modifier.weight(1f))
                        StatsCard(title = "加班折算天数", value = String.format("%.1f", uiState.currentStats.overtimeDays), color = RecordOvertime, modifier = Modifier.weight(1f))
                    }
                }
                
                // 总标准工详情
                item {
                    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(text = "总标准工", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(12.dp))
                            DetailRow("标准工", String.format("%.1f 天", uiState.currentStats.standardDays))
                            Spacer(modifier = Modifier.height(4.dp))
                            DetailRow("手动折算", String.format("%.1f 天", uiState.currentStats.manualDays))
                            Spacer(modifier = Modifier.height(4.dp))
                            DetailRow("加班折算", String.format("%.1f 天", uiState.currentStats.overtimeDays))
                            Divider(modifier = Modifier.padding(vertical = 8.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(text = "合计", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                Text(text = String.format("%.2f 天", uiState.currentStats.totalStandard), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = Primary)
                            }
                        }
                    }
                }
                
                // 饭补和工资
                item {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        StatsCard(title = "饭补合计", value = currencyFormat.format(uiState.currentStats.mealSubsidyTotal), subtitle = "日标准 ${currencyFormat.format(uiState.settings.mealSubsidyStandard)}", color = Success, modifier = Modifier.weight(1f))
                        StatsCard(title = "应发工资", value = currencyFormat.format(uiState.currentStats.wageTotal), subtitle = "日标准 ${currencyFormat.format(uiState.currentStats.dailyWage)}", color = Primary, modifier = Modifier.weight(1f))
                    }
                }
                
                // 数据对比
                item {
                    ComparisonCard(
                        currentHours = uiState.currentStats.totalStandard, currentMealSubsidy = uiState.currentStats.mealSubsidyTotal,
                        currentWage = uiState.currentStats.wageTotal + uiState.currentStats.mealSubsidyTotal,
                        previousHours = uiState.previousStats.totalStandard, previousMealSubsidy = uiState.previousStats.mealSubsidyTotal,
                        previousWage = uiState.previousStats.wageTotal + uiState.previousStats.mealSubsidyTotal
                    )
                }
                
                // 加班分布
                item {
                    OvertimeDistributionCard(distribution = uiState.overtimeDistribution, totalDays = uiState.totalOvertimeDays, totalHours = uiState.totalOvertimeHours)
                }
                
                // 年度月度分解柱状图（年视图）
                if (uiState.selectedPeriod == "year" && uiState.yearMonthlyBreakdown.isNotEmpty()) {
                    item {
                        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(text = "${uiState.selectedYear}年月度工时", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(16.dp))
                                AnnualBarChart(data = uiState.yearMonthlyBreakdown, modifier = Modifier.fillMaxWidth().height(220.dp))
                            }
                        }
                    }
                }
                
                // 近6个月趋势图（月视图）
                if (uiState.selectedPeriod == "month") {
                    item {
                        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(text = "近6个月工时趋势", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(16.dp))
                                if (uiState.monthlyTrend.isNotEmpty()) {
                                    LineChartView(data = uiState.monthlyTrend, modifier = Modifier.fillMaxWidth().height(200.dp))
                                } else {
                                    Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                                        Text(text = "暂无数据", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                }
                
                // 地点分布饼图
                if (uiState.locationDistribution.isNotEmpty()) {
                    item {
                        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                val title = if (uiState.selectedPeriod == "year") "${uiState.selectedYear}年地点分布" else "本月地点分布"
                                Text(text = title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(16.dp))
                                PieChartView(data = uiState.locationDistribution, modifier = Modifier.fillMaxWidth().height(200.dp))
                            }
                        }
                    }
                }
                
                // 记工明细
                if (!uiState.isBatchMode) {
                    item {
                        val detailTitle = if (uiState.selectedPeriod == "year") "${uiState.selectedYear}年记工明细" else "本月记工明细"
                        MonthlyDetailCardWithActions(
                            title = "$detailTitle (${uiState.monthlyDetailRecords.size}条)",
                            records = uiState.monthlyDetailRecords,
                            onEdit = { record -> viewModel.showEditDialog(record) },
                            onDelete = { record -> viewModel.showDeleteConfirm(record) }
                        )
                    }
                } else {
                    // 批量选择模式
                    item {
                        Text(text = "选择要删除的记录 (${uiState.selectedRecordIds.size}/${uiState.monthlyDetailRecords.size})", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    }
                    items(items = uiState.monthlyDetailRecords, key = { it.id }) { record ->
                        WorkRecordCardBatch(
                            record = record,
                            isSelected = uiState.selectedRecordIds.contains(record.id),
                            onClick = { viewModel.toggleRecordSelection(record.id) },
                            onLongClick = {}
                        )
                    }
                }
                
                item { Spacer(modifier = Modifier.height(80.dp)) }
            }
        }
        
        // 编辑对话框
        if (uiState.showEditDialog && uiState.editingRecord != null) {
            AddRecordDialog(
                record = uiState.editingRecord, recentLocations = emptyList(),
                onDismiss = { viewModel.hideEditDialog() },
                onSave = { date, hours, isOvertime, location, remark, mealSubsidy, isManual ->
                    viewModel.saveEditedRecord(date, hours, isOvertime, location, remark, mealSubsidy, isManual)
                }
            )
        }
        
        // 删除确认
        if (uiState.showDeleteConfirm && uiState.deletingRecord != null) {
            ConfirmDialog(title = "确认删除", message = "确定要删除 ${uiState.deletingRecord!!.date} 的记录吗？删除后可从回收站恢复。",
                confirmText = "删除", onConfirm = { viewModel.confirmDelete() }, onDismiss = { viewModel.hideDeleteConfirm() }, isDangerous = true)
        }
        
        // 批量删除确认
        if (uiState.showBatchDeleteConfirm) {
            ConfirmDialog(title = "批量删除", message = "确定要删除选中的 ${uiState.selectedRecordIds.size} 条记录吗？删除后可从回收站恢复。",
                confirmText = "删除", onConfirm = { viewModel.confirmBatchDelete() }, onDismiss = { viewModel.hideBatchDeleteConfirm() }, isDangerous = true)
        }
    }
}

// ========== 视图模式切换 ==========

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ViewModeToggle(selectedPeriod: String, onModeSelected: (String) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
    ) {
        FilterChip(
            selected = selectedPeriod == "month",
            onClick = { onModeSelected("month") },
            label = { Text("月视图") },
            leadingIcon = if (selectedPeriod == "month") {
                { Icon(Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp)) }
            } else null
        )
        FilterChip(
            selected = selectedPeriod == "year",
            onClick = { onModeSelected("year") },
            label = { Text("年视图") },
            leadingIcon = if (selectedPeriod == "year") {
                { Icon(Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp)) }
            } else null
        )
    }
}

@Composable
private fun YearSelector(year: String, onPrevious: () -> Unit, onNext: () -> Unit) {
    val isNextEnabled = year < DateUtils.currentYear()
    
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onPrevious) {
            Icon(Icons.Default.KeyboardArrowLeft, contentDescription = "上一年")
        }
        Text(text = "${year}年", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        IconButton(onClick = onNext, enabled = isNextEnabled) {
            Icon(Icons.Default.KeyboardArrowRight, contentDescription = "下一年",
                tint = if (isNextEnabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LocationFilterChips(locations: List<String>, selectedLocation: String, onLocationSelected: (String) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            FilterChip(selected = selectedLocation.isEmpty(), onClick = { onLocationSelected("") },
                label = { Text("全部") },
                leadingIcon = if (selectedLocation.isEmpty()) {{ Icon(Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp)) }} else null)
        }
        items(locations) { location ->
            FilterChip(selected = selectedLocation == location, onClick = { onLocationSelected(location) }, label = { Text(location) })
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = value, style = MaterialTheme.typography.bodyMedium)
    }
}

// ========== 年度柱状图 ==========

@Composable
fun AnnualBarChart(data: List<Pair<String, Double>>, modifier: Modifier = Modifier) {
    val primaryColor = Primary.toArgb()
    
    AndroidView(
        factory = { context ->
            BarChart(context).apply {
                description.isEnabled = false
                legend.isEnabled = false
                setTouchEnabled(true)
                setDrawGridBackground(false)
                setDrawValueAboveBar(true)
                
                xAxis.apply {
                    position = XAxis.XAxisPosition.BOTTOM
                    setDrawGridLines(false)
                    granularity = 1f
                    textColor = AndroidColor.GRAY
                    textSize = 9f
                }
                
                axisLeft.apply {
                    setDrawGridLines(true)
                    gridColor = AndroidColor.LTGRAY
                    textColor = AndroidColor.GRAY
                    axisMinimum = 0f
                }
                
                axisRight.isEnabled = false
            }
        },
        update = { chart ->
            val entries = data.mapIndexed { index, (_, value) ->
                BarEntry(index.toFloat(), value.toFloat())
            }
            
            val dataSet = BarDataSet(entries, "工时").apply {
                color = primaryColor
                valueTextColor = AndroidColor.GRAY
                valueTextSize = 9f
            }
            
            chart.xAxis.valueFormatter = IndexAxisValueFormatter(
                data.map { it.first.substring(5) } // MM格式
            )
            
            chart.data = BarData(dataSet).apply {
                barWidth = 0.6f
            }
            chart.invalidate()
        },
        modifier = modifier
    )
}

// ========== 保留的原有组件 ==========

@Composable
fun MonthSelector(yearMonth: String, onPrevious: () -> Unit, onNext: () -> Unit) {
    val parts = yearMonth.split("-")
    val displayText = if (parts.size == 2) "${parts[0]}年${parts[1]}月" else yearMonth
    val isNextEnabled = yearMonth < DateUtils.currentYearMonth()
    
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onPrevious) { Icon(Icons.Default.KeyboardArrowLeft, contentDescription = "上一月") }
        Text(text = displayText, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        IconButton(onClick = onNext, enabled = isNextEnabled) {
            Icon(Icons.Default.KeyboardArrowRight, contentDescription = "下一月",
                tint = if (isNextEnabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f))
        }
    }
}

@Composable
fun ComparisonCard(currentHours: Double, currentMealSubsidy: Double, currentWage: Double, previousHours: Double, previousMealSubsidy: Double, previousWage: Double) {
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = if (currentHours == previousHours && currentWage == previousWage) "与上期持平" else "数据对比", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column { Text(text = "工时", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(text = "${formatDiff(currentHours - previousHours)}", style = MaterialTheme.typography.bodyLarge, color = if (currentHours >= previousHours) Increase else Decrease, fontWeight = FontWeight.Bold) }
                Column(horizontalAlignment = Alignment.End) { Text(text = "饭补", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(text = "${formatDiff(currentMealSubsidy - previousMealSubsidy)}", style = MaterialTheme.typography.bodyLarge, color = if (currentMealSubsidy >= previousMealSubsidy) Increase else Decrease, fontWeight = FontWeight.Bold) }
                Column(horizontalAlignment = Alignment.End) { Text(text = "工资", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(text = "${formatDiff(currentWage - previousWage)}", style = MaterialTheme.typography.bodyLarge, color = if (currentWage >= previousWage) Increase else Decrease, fontWeight = FontWeight.Bold) }
            }
        }
    }
}

@Composable
fun OvertimeDistributionCard(distribution: Map<Double, Int>, totalDays: Double, totalHours: Double) {
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = "加班分布", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(12.dp))
            val labels = listOf(0.0 to "无加班", 0.5 to "0.5天", 1.0 to "1天", 1.5 to "1.5天", 2.0 to "2天+")
            labels.forEach { (days, label) ->
                val count = distribution[days] ?: 0
                val barColor = if (days == 0.0) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f) else RecordOvertime
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(text = label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(50.dp))
                    Box(modifier = Modifier.weight(1f).height(20.dp).clip(RoundedCornerShape(4.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
                        val maxCount = distribution.values.maxOrNull() ?: 1
                        val progress = if (maxCount > 0) count.toFloat() / maxCount else 0f
                        Box(modifier = Modifier.fillMaxHeight().fillMaxWidth(progress).clip(RoundedCornerShape(4.dp)).background(barColor.copy(alpha = if (days == 0.0) 0.3f else 0.8f)))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "$count 天", style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(40.dp))
                }
            }
            Divider(modifier = Modifier.padding(vertical = 8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(text = "总加班天数", style = MaterialTheme.typography.bodyMedium)
                Text(text = String.format("%.1f 天", totalDays), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = RecordOvertime)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(text = "总加班小时", style = MaterialTheme.typography.bodyMedium)
                Text(text = String.format("%.1f 小时", totalHours), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = RecordOvertime)
            }
        }
    }
}

@Composable
fun LineChartView(data: List<Pair<String, Double>>, modifier: Modifier = Modifier) {
    val primaryColor = Primary.toArgb()
    AndroidView(
        factory = { context ->
            LineChart(context).apply {
                description.isEnabled = false; legend.isEnabled = false; setTouchEnabled(false); setDrawGridBackground(false)
                xAxis.apply { position = XAxis.XAxisPosition.BOTTOM; setDrawGridLines(false); granularity = 1f; textColor = AndroidColor.GRAY }
                axisLeft.apply { setDrawGridLines(true); gridColor = AndroidColor.LTGRAY; textColor = AndroidColor.GRAY }
                axisRight.isEnabled = false
            }
        },
        update = { chart ->
            val entries = data.mapIndexed { index, (_, value) -> Entry(index.toFloat(), value.toFloat()) }
            val dataSet = LineDataSet(entries, "工时").apply {
                color = primaryColor; lineWidth = 2f; setDrawCircles(true); circleRadius = 4f; setCircleColor(primaryColor)
                setDrawValues(true); valueTextColor = AndroidColor.GRAY; valueTextSize = 10f; mode = LineDataSet.Mode.CUBIC_BEZIER
                setDrawFilled(true); fillColor = primaryColor; fillAlpha = 50
            }
            chart.xAxis.valueFormatter = IndexAxisValueFormatter(data.map { it.first.substring(5) })
            chart.data = LineData(dataSet); chart.invalidate()
        },
        modifier = modifier
    )
}

@Composable
fun PieChartView(data: Map<String, Int>, modifier: Modifier = Modifier) {
    val colors = listOf(Primary.toArgb(), RecordOvertime.toArgb(), RecordManual.toArgb(), Success.toArgb(), Warning.toArgb())
    AndroidView(
        factory = { context ->
            PieChart(context).apply {
                description.isEnabled = false; legend.isEnabled = true; legend.textColor = AndroidColor.GRAY
                setUsePercentValues(true); setDrawEntryLabels(false); isDrawHoleEnabled = true
                holeRadius = 40f; transparentCircleRadius = 45f; setHoleColor(AndroidColor.TRANSPARENT)
            }
        },
        update = { chart ->
            val entries = data.map { (label, value) -> PieEntry(value.toFloat(), label) }
            val dataSet = PieDataSet(entries, "").apply { this.colors = colors.take(entries.size); valueTextColor = AndroidColor.WHITE; valueTextSize = 12f }
            chart.data = PieData(dataSet); chart.invalidate()
        },
        modifier = modifier
    )
}

private fun formatDiff(value: Double): String = when { value > 0 -> "+${String.format("%.1f", value)}"; value < 0 -> String.format("%.1f", value); else -> "0" }

@Composable
fun MonthlyDetailCardWithActions(title: String = "", records: List<WorkRecord>, onEdit: (WorkRecord) -> Unit, onDelete: (WorkRecord) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val displayTitle = title.ifEmpty { "记工明细 (${records.size}条)" }
    
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(text = displayTitle, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, contentDescription = if (expanded) "收起" else "展开")
                }
            }
            if (expanded) {
                Spacer(modifier = Modifier.height(8.dp))
                if (records.isEmpty()) {
                    Text(text = "暂无记录", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    records.forEach { record ->
                        RecordDetailItemWithActions(record = record, onEdit = { onEdit(record) }, onDelete = { onDelete(record) })
                        if (record != records.last()) { Divider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant) }
                    }
                }
            }
        }
    }
}

@Composable
private fun RecordDetailItemWithActions(record: WorkRecord, onEdit: () -> Unit, onDelete: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(text = record.date, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val typeText = when { record.isOvertime -> "加班"; record.isManual -> "手动折算"; else -> "标准工" }
                val typeColor = when { record.isOvertime -> RecordOvertime; record.isManual -> RecordManual; else -> RecordStandard }
                Text(text = typeText, style = MaterialTheme.typography.labelMedium, color = typeColor)
                Text(text = "${String.format("%.1f", record.hours)}小时", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) { Icon(Icons.Default.Edit, contentDescription = "编辑", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp)) }
                IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) { Icon(Icons.Default.Delete, contentDescription = "删除", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp)) }
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(text = if (record.location.isNotEmpty()) record.location else "未填写工地", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(text = if (record.mealSubsidy && !record.isOvertime) "有饭补" else "无饭补", style = MaterialTheme.typography.bodySmall, color = if (record.mealSubsidy && !record.isOvertime) Success else MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (record.remark.isNotEmpty()) { Spacer(modifier = Modifier.height(2.dp)); Text(text = record.remark, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}
