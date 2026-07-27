package com.worklogger.app.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 记工记录实体
 */
@Entity(tableName = "work_records")
data class WorkRecord(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val date: String,           // yyyy-MM-dd
    val hours: Double,          // 工时
    val isOvertime: Boolean,    // 是否加班
    val location: String,       // 工作地点
    val remark: String,         // 备注
    val mealSubsidy: Boolean,   // 是否有饭补
    val isManual: Boolean,      // 是否手动折算
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "is_deleted")
    val isDeleted: Boolean = false,
    @ColumnInfo(name = "deleted_at")
    val deletedAt: Long? = null
)

/**
 * 快捷短语实体
 */
@Entity(tableName = "quick_phrases")
data class QuickPhrase(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val phrase: String,
    val useCount: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * 用户设置数据类
 */
data class UserSettings(
    val dailyWorkHours: Double = 9.0,       // 标准工9小时=1工
    val overtimeWorkHours: Double = 8.0,   // 加班8小时=1工
    val overtimeRate: Double = 1.0,         // 保留但不使用（向后兼容）
    val dailyWage: Double = 260.0,          // 日工资标准260元/天
    val monthTarget: Double = 22.0,         // 月工时目标（标准工天数）
    val mealSubsidyStandard: Double = 30.0, // 饭补30元/天
    val offWorkTime: String = "18:00",     // 下班时间 HH:mm
    val offWorkReminder: Boolean = true,   // 下班提醒开关
    val missedDayReminder: Boolean = true, // 漏记提醒开关
    val theme: String = "system",         // system/light/dark
    val biometricEnabled: Boolean = false  // 生物识别开关
)

/**
 * 统计数据类
 */
data class StatsData(
    val standardDays: Double = 0.0,       // 标准工天数
    val manualDays: Double = 0.0,          // 手动折算天数
    val overtimeHours: Double = 0.0,      // 加班总小时
    val overtimeDays: Double = 0.0,        // 加班折算天数
    val totalStandard: Double = 0.0,       // 总标准工
    val mealSubsidyTotal: Double = 0.0,    // 饭补合计
    val wageTotal: Double = 0.0,          // 应发工资
    val dailyWage: Double = 0.0           // 日工资标准
)

/**
 * 月份统计数据
 */
data class MonthlyStats(
    val year: Int,
    val month: Int,
    val stats: StatsData,
    val recordCount: Int,
    val overtimeDistribution: Map<Double, Int> // 加班天数分布
)

/**
 * 记录类型枚举
 */
enum class RecordType {
    STANDARD,    // 标准工
    OVERTIME,    // 加班
    MANUAL       // 手动折算
}

/**
 * 记录颜色类型
 */
enum class RecordColorType {
    STANDARD,    // 蓝色
    OVERTIME,    // 橙色
    MANUAL,      // 青色
    DELETED      // 灰色
}

/**
 * 预支工资记录实体
 */
@Entity(tableName = "advance_salary_records")
data class AdvanceSalaryRecord(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val date: String,          // yyyy-MM-dd
    val time: String,          // HH:mm
    val location: String,      // 地点
    val amount: Double,        // 预支金额
    val remark: String = "",   // 备注
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * 月度工资结算单数据类
 */
data class MonthlySalarySettlement(
    val yearMonth: String,                    // yyyy-MM 或 "yyyy年"
    val location: String = "",                // 地点筛选（空=全部）
    val standardDays: Double = 0.0,           // 标准工天数
    val standardWage: Double = 0.0,           // 标准工工资
    val overtimeDays: Double = 0.0,           // 加班折算天数
    val overtimeWage: Double = 0.0,           // 加班工资
    val manualDays: Double = 0.0,             // 手动折算天数
    val manualWage: Double = 0.0,             // 手动折算工资
    val mealSubsidyTotal: Double = 0.0,       // 饭补合计
    val totalEarning: Double = 0.0,           // 应发合计
    val advanceAmount: Double = 0.0,          // 预支合计
    val netPayable: Double = 0.0              // 实发工资
)
