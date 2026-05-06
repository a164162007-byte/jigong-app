package com.worklogger.app.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.worklogger.app.BuildConfig
import com.worklogger.app.ui.components.ConfirmDialog
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onNavigateToTrash: () -> Unit,
    onNavigateToPhrases: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    var showTimePicker by remember { mutableStateOf(false) }
    
    val snackbarHostState = remember { SnackbarHostState() }
    
    LaunchedEffect(uiState.exportResult) {
        uiState.exportResult?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearExportResult()
        }
    }
    
    LaunchedEffect(uiState.syncResult) {
        uiState.syncResult?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearSyncResult()
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "设置",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 工时规则
            item {
                SettingsSection(title = "工时规则")
            }
            
            item {
                NumberSettingItem(
                    title = "每日标准工时",
                    value = uiState.settings.dailyWorkHours,
                    suffix = "小时",
                    onValueChange = { viewModel.updateDailyWorkHours(it) }
                )
            }
            
            item {
                NumberSettingItem(
                    title = "加班折算比例",
                    value = uiState.settings.overtimeRate,
                    suffix = "（1小时加班= ? 标准工时）",
                    onValueChange = { viewModel.updateOvertimeRate(it) }
                )
            }
            
            // 工资设置
            item {
                Spacer(modifier = Modifier.height(8.dp))
                SettingsSection(title = "工资设置")
            }
            
            item {
                NumberSettingItem(
                    title = "日工资标准",
                    value = uiState.settings.dailyWage,
                    prefix = "¥",
                    suffix = "元",
                    onValueChange = { viewModel.updateDailyWage(it) }
                )
            }
            
            item {
                NumberSettingItem(
                    title = "月工时目标",
                    value = uiState.settings.monthTarget,
                    suffix = "天",
                    onValueChange = { viewModel.updateMonthTarget(it) }
                )
            }
            
            item {
                NumberSettingItem(
                    title = "饭补标准",
                    value = uiState.settings.mealSubsidyStandard,
                    prefix = "¥",
                    suffix = "元/天",
                    onValueChange = { viewModel.updateMealSubsidyStandard(it) }
                )
            }
            
            // 提醒设置
            item {
                Spacer(modifier = Modifier.height(8.dp))
                SettingsSection(title = "提醒设置")
            }
            
            item {
                SwitchSettingItem(
                    title = "下班提醒",
                    subtitle = "下班时间提醒记工",
                    checked = uiState.settings.offWorkReminder,
                    onCheckedChange = { viewModel.updateOffWorkReminder(it) }
                )
            }
            
            item {
                ClickableSettingItem(
                    title = "下班时间",
                    value = uiState.settings.offWorkTime,
                    onClick = { showTimePicker = true }
                )
            }
            
            item {
                SwitchSettingItem(
                    title = "漏记提醒",
                    subtitle = "晚上8点提醒补记",
                    checked = uiState.settings.missedDayReminder,
                    onCheckedChange = { viewModel.updateMissedDayReminder(it) }
                )
            }
            
            // 主题设置
            item {
                Spacer(modifier = Modifier.height(8.dp))
                SettingsSection(title = "主题")
            }
            
            item {
                ThemeSelector(
                    selectedTheme = uiState.settings.theme,
                    onThemeChange = { viewModel.updateTheme(it) }
                )
            }
            
            // 云同步设置
            item {
                Spacer(modifier = Modifier.height(8.dp))
                SettingsSection(title = "云同步 (Docker)")
            }
            
            // 云同步开关
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
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.CloudSync,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "启用云同步",
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                Text(
                                    text = "与群晖NAS上的Web版同步数据",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = uiState.settings.cloudSyncEnabled,
                                onCheckedChange = { viewModel.toggleCloudSync(it) }
                            )
                        }
                    }
                }
            }
            
            // 云同步状态和操作
            if (uiState.settings.cloudSyncEnabled) {
                // 服务器配置按钮
                item {
                    SettingItem(
                        title = "服务器配置",
                        subtitle = if (uiState.settings.cloudServerUrl.isNotEmpty()) 
                            uiState.settings.cloudServerUrl else "点击配置服务器信息",
                        icon = Icons.Outlined.Dns,
                        onClick = { viewModel.showCloudConfigDialog() }
                    )
                }
                
                // 最后同步时间
                if (uiState.settings.cloudLastSyncTime > 0) {
                    item {
                        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                        val syncTime = dateFormat.format(Date(uiState.settings.cloudLastSyncTime))
                        SettingItem(
                            title = "上次同步",
                            value = syncTime,
                            icon = Icons.Outlined.Schedule
                        )
                    }
                }
                
                // 同步按钮
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = !uiState.isSyncing) { viewModel.syncToCloud() },
                        colors = CardDefaults.cardColors(
                            containerColor = if (uiState.isSyncing) 
                                MaterialTheme.colorScheme.surfaceVariant 
                            else MaterialTheme.colorScheme.primaryContainer
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (uiState.isSyncing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = "同步中...",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Outlined.Sync,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = "同步到云端",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
            
            // 快捷短语
            item {
                Spacer(modifier = Modifier.height(8.dp))
                SettingsSection(title = "快捷短语")
            }
            
            item {
                SettingItem(
                    title = "管理快捷短语",
                    subtitle = "${uiState.phrases.size} 个短语",
                    icon = Icons.Outlined.ShortText,
                    onClick = onNavigateToPhrases
                )
            }
            
            // 数据管理
            item {
                Spacer(modifier = Modifier.height(8.dp))
                SettingsSection(title = "数据管理")
            }
            
            item {
                SettingItem(
                    title = "导出Excel",
                    icon = Icons.Outlined.TableChart,
                    onClick = { viewModel.exportExcel() }
                )
            }
            
            item {
                SettingItem(
                    title = "导出JSON",
                    icon = Icons.Outlined.Code,
                    onClick = { viewModel.exportJson() }
                )
            }
            
            item {
                SettingItem(
                    title = "回收站",
                    icon = Icons.Outlined.DeleteSweep,
                    onClick = onNavigateToTrash
                )
            }
            
            item {
                SettingItem(
                    title = "清空所有数据",
                    icon = Icons.Outlined.DeleteForever,
                    onClick = { viewModel.showClearConfirm() },
                    isDangerous = true
                )
            }
            
            // 关于
            item {
                Spacer(modifier = Modifier.height(8.dp))
                SettingsSection(title = "关于")
            }
            
            item {
                SettingItem(
                    title = "版本",
                    value = BuildConfig.VERSION_NAME,
                    icon = Icons.Outlined.Info
                )
            }
            
            item {
                Spacer(modifier = Modifier.height(80.dp))
            }
        }
        
        // 时间选择器
        if (showTimePicker) {
            TimePickerDialog(
                initialHour = uiState.settings.offWorkTime.substringBefore(":").toIntOrNull() ?: 18,
                initialMinute = uiState.settings.offWorkTime.substringAfter(":").toIntOrNull() ?: 0,
                onDismiss = { showTimePicker = false },
                onConfirm = { hour, minute ->
                    val time = String.format("%02d:%02d", hour, minute)
                    viewModel.updateOffWorkTime(time)
                    showTimePicker = false
                }
            )
        }
        
        // 清空确认对话框
        if (uiState.showClearConfirm) {
            ConfirmDialog(
                title = "确认清空所有数据",
                message = "确定要清空所有数据吗？此操作不可恢复！",
                confirmText = "清空",
                onConfirm = { viewModel.clearAllData() },
                onDismiss = { viewModel.hideClearConfirm() },
                isDangerous = true
            )
        }
        
        // 云同步配置对话框
        if (uiState.showCloudConfigDialog) {
            CloudConfigDialog(
                serverUrl = uiState.cloudServerUrlInput,
                username = uiState.cloudUsernameInput,
                password = uiState.cloudPasswordInput,
                syncResult = uiState.syncResult,
                onServerUrlChange = { viewModel.updateCloudServerUrl(it) },
                onUsernameChange = { viewModel.updateCloudUsername(it) },
                onPasswordChange = { viewModel.updateCloudPassword(it) },
                onDismiss = { viewModel.hideCloudConfigDialog() },
                onSave = { viewModel.saveCloudConfig() }
            )
        }
    }
}

@Composable
fun CloudConfigDialog(
    serverUrl: String,
    username: String,
    password: String,
    syncResult: String?,
    onServerUrlChange: (String) -> Unit,
    onUsernameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit
) {
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
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Outlined.CloudSync,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "云服务器配置",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = "配置与群晖NAS上Docker部署的Web版进行数据同步",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // 服务器地址
                OutlinedTextField(
                    value = serverUrl,
                    onValueChange = onServerUrlChange,
                    label = { Text("服务器地址") },
                    placeholder = { Text("http://192.168.1.100:5000") },
                    leadingIcon = {
                        Icon(Icons.Outlined.Link, contentDescription = null)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // 用户名
                OutlinedTextField(
                    value = username,
                    onValueChange = onUsernameChange,
                    label = { Text("用户名") },
                    leadingIcon = {
                        Icon(Icons.Outlined.Person, contentDescription = null)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // 密码
                OutlinedTextField(
                    value = password,
                    onValueChange = onPasswordChange,
                    label = { Text("密码") },
                    leadingIcon = {
                        Icon(Icons.Outlined.Lock, contentDescription = null)
                    },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                
                // 提示信息
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "💡 服务器地址需要包含完整的URL和端口号",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                // 同步结果
                if (syncResult != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (syncResult.contains("成功")) 
                                MaterialTheme.colorScheme.primaryContainer 
                            else MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Text(
                            text = syncResult,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(12.dp)
                        )
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
                        onClick = onSave,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("保存并测试")
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsSection(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(vertical = 8.dp)
    )
}

@Composable
fun SettingItem(
    title: String,
    subtitle: String? = null,
    value: String? = null,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: (() -> Unit)? = null,
    isDangerous: Boolean = false
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isDangerous) MaterialTheme.colorScheme.error 
                       else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (isDangerous) MaterialTheme.colorScheme.error 
                           else MaterialTheme.colorScheme.onSurface
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (value != null) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (onClick != null) {
                Icon(
                    imageVector = Icons.Outlined.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun SwitchSettingItem(
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange
            )
        }
    }
}

@Composable
fun ClickableSettingItem(
    title: String,
    value: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
fun NumberSettingItem(
    title: String,
    value: Double,
    prefix: String = "",
    suffix: String = "",
    onValueChange: (Double) -> Unit
) {
    var textValue by remember(value) { mutableStateOf(if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()) }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = textValue,
                onValueChange = { newValue ->
                    textValue = newValue.filter { it.isDigit() || it == '.' }
                    textValue.toDoubleOrNull()?.let { onValueChange(it) }
                },
                prefix = if (prefix.isNotEmpty()) { { Text(prefix) } } else null,
                suffix = if (suffix.isNotEmpty()) { { Text(suffix) } } else null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
        }
    }
}

@Composable
fun ThemeSelector(
    selectedTheme: String,
    onThemeChange: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    "system" to "跟随系统",
                    "light" to "浅色",
                    "dark" to "深色"
                ).forEach { (value, label) ->
                    FilterChip(
                        selected = selectedTheme == value,
                        onClick = { onThemeChange(value) },
                        label = { Text(label) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimePickerDialog(
    initialHour: Int,
    initialMinute: Int,
    onDismiss: () -> Unit,
    onConfirm: (hour: Int, minute: Int) -> Unit
) {
    val timePickerState = rememberTimePickerState(
        initialHour = initialHour,
        initialMinute = initialMinute
    )
    
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.padding(16.dp),
            shape = MaterialTheme.shapes.large
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "选择时间",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(16.dp))
                TimePicker(state = timePickerState)
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("取消")
                    }
                    TextButton(onClick = { onConfirm(timePickerState.hour, timePickerState.minute) }) {
                        Text("确认")
                    }
                }
            }
        }
    }
}
