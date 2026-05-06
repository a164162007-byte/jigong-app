package com.worklogger.app.utils

import com.worklogger.app.model.StatsData
import com.worklogger.app.model.WorkRecord

/**
 * 统计计算工具类
 */
object StatsCalculator {
    
    /**
     * 计算统计数据
     */
    fun calculateStats(
        records: List<WorkRecord>,
        dailyWorkHours: Double,
        overtimeRate: Double,
        mealSubsidyStandard: Double,
        dailyWage: Double
    ): StatsData {
        if (records.isEmpty()) {
            return StatsData()
        }
        
        // 标准工记录：不是加班且不是手动的记录
        val standardRecords = records.filter { !it.isOvertime && !it.isManual }
        
        // 标准工天数：按实际工时换算（而非记录数量）
        // 例如：8小时 = 1天，4小时 = 0.5天
        val standardDays = if (dailyWorkHours > 0) {
            standardRecords.sumOf { it.hours } / dailyWorkHours
        } else {
            0.0
        }
        
        // 手动折算天数：按实际工时换算
        val manualRecords = records.filter { it.isManual && !it.isOvertime }
        val manualDays = if (dailyWorkHours > 0) {
            manualRecords.sumOf { it.hours } / dailyWorkHours
        } else {
            0.0
        }
        
        // 加班总小时
        val overtimeRecords = records.filter { it.isOvertime }
        val overtimeHours = overtimeRecords.sumOf { it.hours }
        
        // 加班折算天数 = 加班小时 × 加班折算比例 ÷ 每日标准工时
        val overtimeDays = overtimeHours * overtimeRate / dailyWorkHours
        
        // 总标准工
        val totalStandard = standardDays + manualDays + overtimeDays
        
        // 饭补计算：标准工按实际工时比例计算
        // 例如：8小时全额饭补，4小时半额饭补
        val mealSubsidyDays = if (dailyWorkHours > 0) {
            standardRecords.sumOf { 
                if (it.mealSubsidy) it.hours / dailyWorkHours else 0.0 
            }
        } else {
            0.0
        }
        val mealSubsidyTotal = mealSubsidyDays * mealSubsidyStandard
        
        // 应发工资
        val wageTotal = totalStandard * dailyWage
        
        return StatsData(
            standardDays = standardDays,
            manualDays = manualDays,
            overtimeHours = overtimeHours,
            overtimeDays = overtimeDays,
            totalStandard = totalStandard,
            mealSubsidyTotal = mealSubsidyTotal,
            wageTotal = wageTotal,
            dailyWage = dailyWage
        )
    }
    
    /**
     * 计算加班分布
     * 按每4小时为0.5天粒度统计
     */
    fun calculateOvertimeDistribution(
        records: List<WorkRecord>,
        dailyWorkHours: Double
    ): Map<Double, Int> {
        // 按日期分组计算每天的加班天数
        val overtimeByDate = records
            .filter { it.isOvertime }
            .groupBy { it.date }
            .mapValues { (_, dayRecords) ->
                val totalHours = dayRecords.sumOf { it.hours }
                // 每4小时 = 0.5天
                (totalHours / 4.0 * 0.5).let { days ->
                    // 四舍五入到0.5的倍数
                    Math.round(days * 2).toDouble() / 2
                }
            }
        
        // 统计分布
        val distribution = mutableMapOf<Double, Int>()
        distribution[0.0] = 0  // 无加班
        distribution[0.5] = 0
        distribution[1.0] = 0
        distribution[1.5] = 0
        distribution[2.0] = 0  // 2天及以上
        
        overtimeByDate.values.forEach { days ->
            val key = when {
                days == 0.0 -> 0.0
                days <= 0.5 -> 0.5
                days <= 1.0 -> 1.0
                days <= 1.5 -> 1.5
                else -> 2.0
            }
            distribution[key] = (distribution[key] ?: 0) + 1
        }
        
        // 如果有无加班的天数（但这一天有记录），需要计算
        val datesWithRecords = records.map { it.date }.distinct()
        val overtimeDates = overtimeByDate.keys
        val noOvertimeDates = datesWithRecords - overtimeDates.toSet()
        
        // 只统计有标准工记录的无加班天数
        val noOvertimeDays = records
            .filter { !it.isOvertime && it.date in noOvertimeDates }
            .map { it.date }
            .distinct()
            .size
        
        distribution[0.0] = distribution[0.0]!! + noOvertimeDays
        
        return distribution
    }
    
    /**
     * 计算总加班天数和小时数
     */
    fun calculateTotalOvertime(
        records: List<WorkRecord>
    ): Pair<Double, Double> {
        val overtimeRecords = records.filter { it.isOvertime }
        val totalHours = overtimeRecords.sumOf { it.hours }
        val totalDays = overtimeRecords.map { it.date }.distinct().size.toDouble()
        return Pair(totalDays, totalHours)
    }
    
    /**
     * 计算月工时目标完成度
     */
    fun calculateProgress(
        currentStandardDays: Double,
        monthTarget: Double
    ): Float {
        if (monthTarget <= 0) return 0f
        return (currentStandardDays / monthTarget).toFloat().coerceIn(0f, 1f)
    }
    
    /**
     * 计算月总工时（标准工+加班）
     */
    fun calculateMonthlyHours(
        records: List<WorkRecord>,
        dailyWorkHours: Double
    ): Double {
        val standardHours = records.filter { !it.isOvertime }.sumOf { it.hours }
        val overtimeHours = records.filter { it.isOvertime }.sumOf { it.hours }
        return standardHours + overtimeHours
    }
    
    /**
     * 计算月总工资
     */
    fun calculateMonthlyWage(
        records: List<WorkRecord>,
        dailyWorkHours: Double,
        overtimeRate: Double,
        dailyWage: Double,
        mealSubsidyStandard: Double
    ): Double {
        val stats = calculateStats(
            records, dailyWorkHours, overtimeRate, mealSubsidyStandard, dailyWage
        )
        return stats.wageTotal + stats.mealSubsidyTotal
    }
    
    /**
     * 同比计算（本月 vs 上月）
     */
    fun calculateComparison(
        currentStats: StatsData,
        previousStats: StatsData
    ): Triple<Double, Double, Double> {
        val hoursDiff = currentStats.totalStandard - previousStats.totalStandard
        val mealDiff = currentStats.mealSubsidyTotal - previousStats.mealSubsidyTotal
        val wageDiff = (currentStats.wageTotal + currentStats.mealSubsidyTotal) - 
                       (previousStats.wageTotal + previousStats.mealSubsidyTotal)
        return Triple(hoursDiff, mealDiff, wageDiff)
    }
}
