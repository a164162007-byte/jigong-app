package com.worklogger.app.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.worklogger.app.model.WorkRecord
import com.worklogger.app.ui.theme.*
import com.worklogger.app.utils.DateUtils
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(
    viewModel: CalendarViewModel
) {
    val uiState by viewModel.uiState.collectAsState()
    
    // 页面恢复时自动刷新数据
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refresh()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "日历",
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // 月份导航
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { viewModel.previousMonth() }) {
                    Icon(Icons.Default.KeyboardArrowLeft, contentDescription = "上一月")
                }
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = "${uiState.currentYear}年${uiState.currentMonth}月",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.width(16.dp))
                IconButton(onClick = { viewModel.nextMonth() }) {
                    Icon(Icons.Default.KeyboardArrowRight, contentDescription = "下一月")
                }
            }
            
            // 星期标题
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp)
            ) {
                listOf("日", "一", "二", "三", "四", "五", "六").forEach { day ->
                    Text(
                        text = day,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // 日历网格
            CalendarGrid(
                year = uiState.currentYear,
                month = uiState.currentMonth,
                recordsByDate = uiState.recordsByDate,
                onDateClick = { date -> viewModel.selectDate(date) }
            )
        }
        
        // 日期详情弹窗
        if (uiState.showDetailDialog && uiState.selectedDate != null) {
            RecordDetailDialog(
                date = uiState.selectedDate!!,
                records = uiState.selectedRecords,
                onDismiss = { viewModel.hideDetailDialog() }
            )
        }
    }
}

@Composable
fun CalendarGrid(
    year: Int,
    month: Int,
    recordsByDate: Map<String, List<WorkRecord>>,
    onDateClick: (String) -> Unit
) {
    val daysInMonth = DateUtils.getMonthDays(year, month)
    
    // 计算第一天是星期几
    val calendar = Calendar.getInstance()
    calendar.set(year, month - 1, 1)
    val firstDayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
    
    // 计算需要填充的空白天数
    val emptyDays = firstDayOfWeek - Calendar.SUNDAY
    
    // 生成日期列表
    val days = (1..daysInMonth).map { day ->
        String.format("%04d-%02d-%02d", year, month, day)
    }
    
    LazyVerticalGrid(
        columns = GridCells.Fixed(7),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        contentPadding = PaddingValues(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // 空白填充
        items(emptyDays) {
            Box(modifier = Modifier.aspectRatio(1f))
        }
        
        // 日期
        items(days) { date ->
            val records = recordsByDate[date] ?: emptyList()
            val isToday = date == DateUtils.today()
            val hasRecords = records.isNotEmpty()
            
            // 判断记录类型
            val recordColor = when {
                records.isEmpty() -> null
                records.any { it.isOvertime } -> RecordOvertime
                records.any { it.isManual } -> RecordManual
                else -> RecordStandard
            }
            
            DayCell(
                day = DateUtils.getDay(date),
                isToday = isToday,
                hasRecords = hasRecords,
                recordColor = recordColor,
                onClick = { onDateClick(date) }
            )
        }
    }
}

@Composable
fun DayCell(
    day: Int,
    isToday: Boolean,
    hasRecords: Boolean,
    recordColor: Color?,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(8.dp))
            .then(
                if (isToday) Modifier.border(
                    2.dp,
                    Primary,
                    RoundedCornerShape(8.dp)
                ) else Modifier
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = day.toString(),
                style = MaterialTheme.typography.bodyMedium,
                color = if (isToday) Primary 
                       else MaterialTheme.colorScheme.onSurface
            )
            
            if (hasRecords && recordColor != null) {
                Spacer(modifier = Modifier.height(2.dp))
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(recordColor)
                )
            }
        }
    }
}

@Composable
fun RecordDetailDialog(
    date: String,
    records: List<WorkRecord>,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = MaterialTheme.shapes.large
        ) {
            Column(
                modifier = Modifier.padding(24.dp)
            ) {
                Text(
                    text = DateUtils.formatDisplayFullDate(date),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = DateUtils.getWeekdayName(date),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                records.forEach { record ->
                    RecordDetailItem(record)
                    if (record != records.last()) {
                        Divider(modifier = Modifier.padding(vertical = 8.dp))
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // 汇总
                val totalHours = records.sumOf { it.hours }
                val hasMealSubsidy = records.any { it.mealSubsidy && !it.isOvertime }
                
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
                        text = "${totalHours}小时${if (hasMealSubsidy) " + 饭补" else ""}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = Primary
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("关闭")
                }
            }
        }
    }
}

@Composable
fun RecordDetailItem(record: WorkRecord) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(
                    when {
                        record.isOvertime -> RecordOvertime
                        record.isManual -> RecordManual
                        else -> RecordStandard
                    }
                )
        )
        
        Spacer(modifier = Modifier.width(12.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "${record.hours}小时",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = listOfNotNull(
                    if (record.isOvertime) "加班" else if (record.isManual) "手动折算" else "标准工",
                    record.location.takeIf { it.isNotEmpty() },
                    record.remark.takeIf { it.isNotEmpty() },
                    if (record.mealSubsidy) "🍱饭补" else null
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
