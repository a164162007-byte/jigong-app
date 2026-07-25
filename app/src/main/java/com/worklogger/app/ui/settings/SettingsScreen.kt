package com.worklogger.app.ui.settings

import android.app.Activity
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.worklogger.app.data.repository.SettingsRepository
import com.worklogger.app.data.repository.WorkRepository
import com.worklogger.app.utils.DownloadState
import com.worklogger.app.utils.ReleaseInfo
import com.worklogger.app.model.QuickPhrase

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    workRepository: WorkRepository,
    settingsRepository: SettingsRepository,
    onExportExcel: () -> Unit
) {
    val context = LocalContext.current
    val viewModel: SettingsViewModel = viewModel(
        factory = SettingsViewModelFactory(context, workRepository, settingsRepository)
    )
    
    val uiState by viewModel.uiState.collectAsState()
    
    // 文件选择器 - 导出
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.let { viewModel.exportData(it) }
    }
    
    // 文件选择器 - 导入
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { viewModel.prepareImport(it) }
    }
    
    val snackbarHostState = remember { SnackbarHostState() }
    
    // 监听导入/导出结果并显示Snackbar
    LaunchedEffect(uiState.importResult, uiState.exportResult) {
        val result = uiState.importResult ?: uiState.exportResult
        result?.let {
            snackbarHostState.showSnackbar(it)
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            // 工作设置区域
            SettingsSection(title = "工作设置") {
                NumberSettingItem(
                    title = "每日标准工时",
                    subtitle = "用于计算加班和进度",
                    value = uiState.settings.dailyWorkHours,
                    onValueChange = { viewModel.updateDailyWorkHours(it) },
                    suffix = "小时"
                )
                
                NumberSettingItem(
                    title = "加班工时标准",
                    subtitle = "加班多少小时算1工",
                    value = uiState.settings.overtimeWorkHours,
                    onValueChange = { viewModel.updateOvertimeWorkHours(it) },
                    suffix = "小时/工"
                )
                
                NumberSettingItem(
                    title = "饭补金额",
                    subtitle = "标准工按工时比例计算",
                    value = uiState.settings.mealSubsidyStandard,
                    onValueChange = { viewModel.updateMealSubsidyStandard(it) },
                    prefix = "¥ ",
                    suffix = "元/天"
                )
                
                NumberSettingItem(
                    title = "日工资标准",
                    subtitle = "用于工资计算",
                    value = uiState.settings.dailyWage,
                    onValueChange = { viewModel.updateDailyWage(it) },
                    prefix = "¥ ",
                    suffix = "元/天"
                )
                
                NumberSettingItem(
                    title = "月工时目标",
                    subtitle = "本月要达到的工时",
                    value = uiState.settings.monthTarget,
                    onValueChange = { viewModel.updateMonthTarget(it) },
                    suffix = "小时"
                )
            }
            
            Divider()
            
            // 外观设置
            SettingsSection(title = "外观") {
                ThemeSettingItem(
                    currentTheme = uiState.settings.theme,
                    onThemeSelected = { viewModel.updateTheme(it) }
                )
            }
            
            Divider()
            
            // 数据管理区域
            SettingsSection(title = "数据管理") {
                // 导出数据
                SettingsClickableItem(
                    icon = Icons.Default.Upload,
                    title = "导出数据",
                    subtitle = "导出为JSON文件（与Web端兼容）",
                    onClick = {
                        val fileName = viewModel.getExportFileName()
                        exportLauncher.launch(fileName)
                    },
                    enabled = !uiState.isExporting
                )
                
                // 导入数据
                SettingsClickableItem(
                    icon = Icons.Default.Download,
                    title = "导入数据",
                    subtitle = "从JSON文件导入（支持Web端格式）",
                    onClick = {
                        importLauncher.launch(arrayOf("application/json"))
                    },
                    enabled = !uiState.isImporting
                )
                
                // 粘贴导入
                SettingsClickableItem(
                    icon = Icons.Default.ContentPaste,
                    title = "粘贴导入",
                    subtitle = "从便签文本批量导入记工记录",
                    onClick = { viewModel.showBatchImportDialog() }
                )

                // Excel导出
                SettingsClickableItem(
                    icon = Icons.Default.TableChart,
                    title = "导出Excel",
                    subtitle = "导出为Excel表格",
                    onClick = onExportExcel
                )
            }
            
            Divider()
            
            // 系统区域
            SettingsSection(title = "系统") {
                // 版本信息
                val versionInfo = viewModel.getCurrentVersion()
                SettingsInfoItem(
                    icon = Icons.Default.Info,
                    title = "版本信息",
                    subtitle = "v${versionInfo.first} (${versionInfo.second})"
                )
                
                // 清除数据
                
                // 检查更新
                SettingsClickableItem(
                    icon = Icons.Default.SystemUpdate,
                    title = "检查更新",
                    subtitle = if (uiState.isCheckingUpdate) "正在检查..." 
                               else when (uiState.updateCheckResult) {
                                   is UpdateCheckResult.UpdateAvailable -> "发现新版本 ${(uiState.updateCheckResult as UpdateCheckResult.UpdateAvailable).info.versionName}"
                                   is UpdateCheckResult.NoUpdate -> "当前已是最新版本"
                                   is UpdateCheckResult.Error -> "检查失败：${(uiState.updateCheckResult as UpdateCheckResult.Error).message}"
                                   else -> "点击检查是否有新版本"
                               },
                    onClick = { viewModel.checkForUpdate() },
                    enabled = !uiState.isCheckingUpdate
                )
                
                SettingsClickableItem(
                    icon = Icons.Default.DeleteForever,
                    title = "清除所有数据",
                    subtitle = "不可恢复，请谨慎操作",
                    onClick = { viewModel.showClearConfirm() },
                    danger = true
                )
            }
            
            Spacer(modifier = Modifier.height(32.dp))
        }
        
        // 清除确认对话框
        if (uiState.showClearConfirm) {
            AlertDialog(
                onDismissRequest = { viewModel.hideClearConfirm() },
                icon = { Icon(Icons.Default.Warning, contentDescription = null) },
                title = { Text("确认清除所有数据？") },
                text = { Text("此操作不可恢复，所有记工记录将被永久删除。") },
                confirmButton = {
                    TextButton(
                        onClick = { viewModel.clearAllData() },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("确认清除")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.hideClearConfirm() }) {
                        Text("取消")
                    }
                }
            )
        }
        
        
        // 更新对话框
        if (uiState.updateCheckResult is UpdateCheckResult.UpdateAvailable) {
            val activity = LocalContext.current as? Activity
            UpdateDialog(
                releaseInfo = (uiState.updateCheckResult as UpdateCheckResult.UpdateAvailable).info,
                currentVersion = viewModel.getCurrentVersion().first,
                downloadState = uiState.downloadState,
                onDismiss = { /* 保持对话框显示，直到用户关闭 */ },
                onDownload = { 
                    activity?.let { viewModel.downloadAndInstallUpdate(it) }
                }
            )
        }
        

        
        // 粘贴导入对话框
        if (uiState.showBatchImportDialog) {
            BatchImportDialog(
                dailyWorkHours = uiState.settings.dailyWorkHours,
                onDismiss = { viewModel.hideBatchImportDialog() },
                onImport = { entries -> viewModel.performBatchImport(entries) }
            )
        }

        // 导入策略对话框
        if (uiState.showImportStrategyDialog) {
            AlertDialog(
                onDismissRequest = { viewModel.cancelImport() },
                icon = { Icon(Icons.Default.Download, contentDescription = null) },
                title = { Text("导入数据") },
                text = { 
                    Text("将导入 ${uiState.pendingImportRecords?.size ?: 0} 条新记录到本地数据库。\n\n重复记录将被自动跳过。") 
                },
                confirmButton = {
                    TextButton(onClick = { viewModel.confirmImport() }) {
                        Text("确认导入")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.cancelImport() }) {
                        Text("取消")
                    }
                }
            )
        }
        
        // 同步加载指示器
        if (uiState.isExporting || uiState.isImporting) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }
    }
}

@Composable
fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
        )
        content()
    }
}

@Composable
fun SettingsInfoItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun SettingsClickableItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    danger: Boolean = false
) {
    val contentColor = when {
        !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        danger -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurface
    }
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (danger) MaterialTheme.colorScheme.error 
                   else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = contentColor
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant 
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
        }
        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        )
    }
}

@Composable
fun NumberSettingItem(
    title: String,
    subtitle: String,
    value: Double,
    onValueChange: (Double) -> Unit,
    prefix: String = "",
    suffix: String = ""
) {
    var textValue by remember(value) { mutableStateOf(if (value == value.toLong().toDouble()) 
        value.toLong().toString() else value.toString()) }
    var isFocused by remember { mutableStateOf(false) }
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        
        OutlinedTextField(
            value = textValue,
            onValueChange = { 
                val filtered = it.filter { c -> c.isDigit() || c == '.' }
                textValue = filtered
                filtered.toDoubleOrNull()?.let { v -> onValueChange(v) }
            },
            modifier = Modifier
                .width(120.dp)
                .onFocusEvent { focusState ->
                    isFocused = focusState.isFocused
                    if (focusState.isFocused) {
                        textValue = if (value == value.toLong().toDouble()) 
                            value.toLong().toString() else value.toString()
                    }
                },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            prefix = if (prefix.isNotEmpty()) {
                { Text(prefix, style = MaterialTheme.typography.bodySmall) }
            } else null,
            suffix = if (suffix.isNotEmpty()) {
                { Text(suffix, style = MaterialTheme.typography.bodySmall) }
            } else null,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline
            )
        )
    }
}

/**
 * 更新对话框
 */
@Composable
fun UpdateDialog(
    releaseInfo: ReleaseInfo,
    currentVersion: String,
    downloadState: DownloadState,
    onDismiss: () -> Unit,
    onDownload: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.SystemUpdate, contentDescription = null) },
        title = { Text("发现新版本") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 版本信息对比
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("当前版本：v$currentVersion", style = MaterialTheme.typography.bodyMedium)
                    Text("最新版本：v${releaseInfo.versionName}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                }
                
                Divider()
                
                // 更新说明
                Text("更新说明：", style = MaterialTheme.typography.titleSmall)
                Text(
                    text = releaseInfo.releaseNotes,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .heightIn(max = 200.dp)
                )
                
                // 下载进度
                if (downloadState is DownloadState.Downloading) {
                    Divider()
                    Text("下载进度：${downloadState.progress}%", style = MaterialTheme.typography.bodyMedium)
                    LinearProgressIndicator(
                        progress = downloadState.progress / 100f,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDownload,
                enabled = downloadState !is DownloadState.Downloading
            ) {
                Text(if (downloadState is DownloadState.Downloading) "下载中..." else "立即更新")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("稍后")
            }
        }
    )
}

@Composable
fun ThemeSettingItem(
    currentTheme: String,
    onThemeSelected: (String) -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    
    val themeLabels = mapOf(
        "system" to "跟随系统",
        "light" to "浅色模式",
        "dark" to "深色模式"
    )
    
    val themeIcons = mapOf(
        "system" to Icons.Default.SettingsBrightness,
        "light" to Icons.Default.LightMode,
        "dark" to Icons.Default.DarkMode
    )
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { showMenu = true }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = themeIcons[currentTheme] ?: Icons.Default.SettingsBrightness,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = "主题模式", style = MaterialTheme.typography.bodyLarge)
            Text(
                text = themeLabels[currentTheme] ?: "跟随系统",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        )
    }
    
    if (showMenu) {
        AlertDialog(
            onDismissRequest = { showMenu = false },
            icon = { Icon(Icons.Default.Palette, contentDescription = null) },
            title = { Text("选择主题") },
            text = {
                Column {
                    themeLabels.forEach { (key, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onThemeSelected(key)
                                    showMenu = false
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = themeIcons[key]!!,
                                contentDescription = null,
                                tint = if (currentTheme == key) MaterialTheme.colorScheme.primary 
                                       else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = label,
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (currentTheme == key) MaterialTheme.colorScheme.primary 
                                        else MaterialTheme.colorScheme.onSurface
                            )
                            if (currentTheme == key) {
                                Spacer(modifier = Modifier.weight(1f))
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showMenu = false }) {
                    Text("取消")
                }
            }
        )
    }
}