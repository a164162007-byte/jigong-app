package com.worklogger.app.ui.settings

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.worklogger.app.data.repository.SettingsRepository
import com.worklogger.app.data.repository.WorkRepository
import com.worklogger.app.utils.DownloadState
import com.worklogger.app.utils.ReleaseInfo
import com.worklogger.app.utils.UpdateCheckResult

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
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
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
                    title = "加班折算率",
                    subtitle = "加班小时 × 折算率 = 标准工时",
                    value = uiState.settings.overtimeRate,
                    onValueChange = { viewModel.updateOvertimeRate(it) },
                    suffix = "倍"
                )
                
                NumberSettingItem(
                    title = "饭补金额",
                    subtitle = "标准工天 × 饭补 = 饭补总额",
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
                
                // Excel导出
                SettingsClickableItem(
                    icon = Icons.Default.TableChart,
                    title = "导出Excel",
                    subtitle = "导出为Excel表格",
                    onClick = onExportExcel
                )
            }
            
            Divider()
            
            // 云同步区域
            SettingsSection(title = "云同步") {
                // 云配置
                SettingsClickableItem(
                    icon = Icons.Default.Cloud,
                    title = "云同步设置",
                    subtitle = if (uiState.settings.cloudServerUrl.isNotBlank())
                        "已配置: ${uiState.settings.cloudServerUrl}"
                    else "点击配置云同步",
                    onClick = { viewModel.showCloudConfigDialog() }
                )
                
                // 测试连接
                SettingsClickableItem(
                    icon = Icons.Default.Wifi,
                    title = "测试连接",
                    subtitle = "测试与云服务器的连接",
                    onClick = { viewModel.testCloudConnection() },
                    enabled = !uiState.isSyncing && uiState.settings.cloudServerUrl.isNotBlank()
                )
                
                // 同步数据
                SettingsClickableItem(
                    icon = Icons.Default.Sync,
                    title = "同步数据",
                    subtitle = "上传本地 + 下载云端",
                    onClick = { viewModel.syncCloudData() },
                    enabled = !uiState.isSyncing && uiState.settings.cloudServerUrl.isNotBlank()
                )
                
                // 从云端下载
                SettingsClickableItem(
                    icon = Icons.Default.CloudDownload,
                    title = "从云端下载",
                    subtitle = "仅下载云端数据到本地",
                    onClick = { viewModel.downloadFromCloud() },
                    enabled = !uiState.isSyncing && uiState.settings.cloudServerUrl.isNotBlank()
                )
                
                // 上传到云端
                SettingsClickableItem(
                    icon = Icons.Default.CloudUpload,
                    title = "上传到云端",
                    subtitle = "仅上传本地数据到云端",
                    onClick = { viewModel.uploadToCloud() },
                    enabled = !uiState.isSyncing && uiState.settings.cloudServerUrl.isNotBlank()
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
        
        // 云配置对话框
        if (uiState.showCloudConfigDialog) {
            CloudConfigDialog(
                serverUrl = uiState.cloudServerUrlInput,
                username = uiState.cloudUsernameInput,
                password = uiState.cloudPasswordInput,
                onServerUrlChange = { viewModel.updateCloudServerUrl(it) },
                onUsernameChange = { viewModel.updateCloudUsername(it) },
                onPasswordChange = { viewModel.updateCloudPassword(it) },
                onDismiss = { viewModel.hideCloudConfigDialog() },
                onSave = { viewModel.saveCloudConfig() }
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
        
        // 同步结果Snackbar
        uiState.syncResult?.let { result ->
            LaunchedEffect(result) {
                // 显示Snackbar后自动清除
                kotlinx.coroutines.delay(3000)
                viewModel.clearSyncResult()
            }
        }
        
        // 导入结果Snackbar
        uiState.importResult?.let { result ->
            LaunchedEffect(result) {
                kotlinx.coroutines.delay(3000)
                viewModel.clearImportResult()
            }
        }
        
        // 导出结果Snackbar
        uiState.exportResult?.let { result ->
            LaunchedEffect(result) {
                kotlinx.coroutines.delay(3000)
                viewModel.clearExportResult()
            }
        }
        
        // 显示结果Snackbar
        val snackbarHostState = remember { SnackbarHostState() }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.padding(16.dp)
        )
        
        LaunchedEffect(uiState.syncResult, uiState.importResult, uiState.exportResult) {
            val result = uiState.syncResult ?: uiState.importResult ?: uiState.exportResult
            result?.let {
                snackbarHostState.showSnackbar(it)
            }
        }
        
        // 同步加载指示器
        if (uiState.isSyncing || uiState.isExporting || uiState.isImporting) {
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
    var isEditing by remember { mutableStateOf(false) }
    
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
            value = if (isEditing) textValue else "$prefix$value$suffix",
            onValueChange = { 
                textValue = it.filter { c -> c.isDigit() || c == '.' }
                it.toDoubleOrNull()?.let { v -> onValueChange(v) }
            },
            modifier = Modifier.width(120.dp),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            trailingIcon = { Text(suffix, style = MaterialTheme.typography.bodySmall) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline
            )
        )
    }
}

@Composable
fun CloudConfigDialog(
    serverUrl: String,
    username: String,
    password: String,
    onServerUrlChange: (String) -> Unit,
    onUsernameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit
) {
    var passwordVisible by remember { mutableStateOf(false) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Cloud, contentDescription = null) },
        title = { Text("云同步设置") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedTextField(
                    value = serverUrl,
                    onValueChange = onServerUrlChange,
                    label = { Text("服务器地址") },
                    placeholder = { Text("http://www.example.com:8080") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    supportingText = { Text("Web端Docker服务地址") }
                )
                
                OutlinedTextField(
                    value = username,
                    onValueChange = onUsernameChange,
                    label = { Text("用户名") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                
                OutlinedTextField(
                    value = password,
                    onValueChange = onPasswordChange,
                    label = { Text("密码") },
                    singleLine = true,
                    visualTransformation = if (passwordVisible) 
                        VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                imageVector = if (passwordVisible) Icons.Default.VisibilityOff 
                                             else Icons.Default.Visibility,
                                contentDescription = if (passwordVisible) "隐藏密码" else "显示密码"
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onSave) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
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
                
                HorizontalDivider()
                
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
                    HorizontalDivider()
                    Text("下载进度：${downloadState.progress}%", style = MaterialTheme.typography.bodyMedium)
                    LinearProgressIndicator(
                        progress = { downloadState.progress / 100f },
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
