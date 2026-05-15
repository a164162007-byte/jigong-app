package com.worklogger.app

import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.worklogger.app.ui.calendar.CalendarScreen
import com.worklogger.app.ui.calendar.CalendarViewModel
import com.worklogger.app.ui.calendar.CalendarViewModelFactory
import com.worklogger.app.ui.home.HomeScreen
import com.worklogger.app.ui.home.HomeViewModel
import com.worklogger.app.ui.home.HomeViewModelFactory
import com.worklogger.app.ui.settings.QuickPhraseScreen
import com.worklogger.app.ui.settings.SettingsScreen
import com.worklogger.app.ui.settings.SettingsViewModel
import com.worklogger.app.ui.settings.SettingsViewModelFactory
import com.worklogger.app.ui.stats.StatsScreen
import com.worklogger.app.ui.stats.StatsViewModel
import com.worklogger.app.ui.stats.StatsViewModelFactory
import com.worklogger.app.ui.theme.WorkLoggerTheme
import com.worklogger.app.ui.trash.TrashScreen
import com.worklogger.app.ui.trash.TrashViewModel
import com.worklogger.app.ui.trash.TrashViewModelFactory
import com.worklogger.app.utils.DateUtils
import com.worklogger.app.utils.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

sealed class Screen(
    val route: String,
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    object Home : Screen("home", "记工", Icons.Filled.Home, Icons.Outlined.Home)
    object Stats : Screen("stats", "统计", Icons.Outlined.BarChart, Icons.Outlined.BarChart)
    object Calendar : Screen("calendar", "日历", Icons.Filled.DateRange, Icons.Outlined.DateRange)
    object Settings : Screen("settings", "设置", Icons.Filled.Settings, Icons.Outlined.Settings)
}

class MainActivity : ComponentActivity() {
    
    // 使用 SupervisorJob 管理协程生命周期
    private val activityJob = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.Main + activityJob)
    
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        val app = application as WorkLoggerApp
        
        // 初始化通知
        val notificationHelper = NotificationHelper(this)
        
        // 设置提醒 - 使用 scope 管理协程生命周期
        scope.launch {
            app.settingsRepository.settings.collect { settings ->
                notificationHelper.scheduleOffWorkReminder(
                    DateUtils.getHour(settings.offWorkTime),
                    DateUtils.getMinute(settings.offWorkTime),
                    settings.offWorkReminder
                )
                notificationHelper.scheduleMissedDayReminder(settings.missedDayReminder)
            }
        }
        
        // 修复旧数据中加班饭补不一致的问题
        lifecycleScope.launch(Dispatchers.IO) { (application as WorkLoggerApp).workRepository.fixMealSubsidyData() }

        setContent {
            val settings by app.settingsRepository.settings.collectAsStateWithLifecycle(
                initialValue = com.worklogger.app.model.UserSettings()
            )
            
            WorkLoggerTheme(theme = settings.theme) {
                MainScreen(app)
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        // 取消所有协程，防止内存泄漏
        activityJob.cancel()
        // 立即杀掉进程，释放内存，避免后台驻留
        android.os.Process.killProcess(android.os.Process.myPid())
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(app: WorkLoggerApp) {
    // 返回键退出应用并立即销毁进程
    val backPressedState = remember { mutableStateOf(true) }
    BackHandler(enabled = backPressedState.value) {
        (app as? android.app.Activity)?.let { activity ->
            activity.finishAndRemoveTask()
            android.os.Process.killProcess(android.os.Process.myPid())
        }
    }
    val navController = rememberNavController()
    val screens = listOf(Screen.Home, Screen.Stats, Screen.Calendar, Screen.Settings)
    
    Scaffold(
        bottomBar = {
            NavigationBar {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = navBackStackEntry?.destination
                
                screens.forEach { screen ->
                    val selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true
                    
                    NavigationBarItem(
                        icon = {
                            Icon(
                                imageVector = if (selected) screen.selectedIcon else screen.unselectedIcon,
                                contentDescription = screen.title
                            )
                        },
                        label = { Text(screen.title) },
                        selected = selected,
                        onClick = {
                            navController.navigate(screen.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Home.route) {
                val viewModel = androidx.lifecycle.viewmodel.compose.viewModel<HomeViewModel>(
                    factory = HomeViewModelFactory(app.workRepository, app.settingsRepository)
                )
                HomeScreen(viewModel = viewModel, onNavigateToAdd = { })
            }
            
            composable(Screen.Stats.route) {
                val viewModel = androidx.lifecycle.viewmodel.compose.viewModel<StatsViewModel>(
                    factory = StatsViewModelFactory(app.workRepository, app.settingsRepository)
                )
                StatsScreen(viewModel = viewModel)
            }
            
            composable(Screen.Calendar.route) {
                val viewModel = androidx.lifecycle.viewmodel.compose.viewModel<CalendarViewModel>(
                    factory = CalendarViewModelFactory(app.workRepository, app.settingsRepository)
                )
                CalendarScreen(viewModel = viewModel)
            }
            
            composable(Screen.Settings.route) {
                val viewModel = androidx.lifecycle.viewmodel.compose.viewModel<SettingsViewModel>(
                    factory = SettingsViewModelFactory(app, app.workRepository, app.settingsRepository)
                )
                SettingsScreen(
                    workRepository = app.workRepository,
                    settingsRepository = app.settingsRepository,
                    onExportExcel = { /* TODO: Implement Excel export */ }
                )
            }
            
            composable("trash") {
                val viewModel = androidx.lifecycle.viewmodel.compose.viewModel<TrashViewModel>(
                    factory = TrashViewModelFactory(app.workRepository)
                )
                TrashScreen(viewModel = viewModel, onNavigateBack = { navController.popBackStack() })
            }
            
            composable("phrases") {
                val settingsViewModel = androidx.lifecycle.viewmodel.compose.viewModel<SettingsViewModel>(
                    factory = SettingsViewModelFactory(app, app.workRepository, app.settingsRepository)
                )
                val uiState by settingsViewModel.uiState.collectAsState()
                QuickPhraseScreen(
                    phrases = uiState.phrases,
                    onAddPhrase = { settingsViewModel.addPhrase(it) },
                    onDeletePhrase = { settingsViewModel.deletePhrase(it) },
                    onNavigateBack = { navController.popBackStack() }
                )
            }
        }
    }
}
