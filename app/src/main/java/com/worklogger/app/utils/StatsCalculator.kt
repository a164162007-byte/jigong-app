package com.worklogger.app.utils

import com.worklogger.app.model.StatsData
import com.worklogger.app.model.WorkRecord

/**
 * 统计计算工具类
 * 
 * 核心规则：
 * - 标准工：hours <= dailyWorkHours 时按比例换算（9小时=1工，4.5小时=0.5工）
 *           hours > dailyWorkHours 时标准工算1工，超出部分按加班折算
 *           例如：12小时 = 1工(标准) + 3小时(加班) = 1 + 3/8 = 1.375工
 * - 加班工：hours / overtimeWorkHours = 工数（8小时=1工，4小时=0.5工）
 * - 手动折算：同标准工逻辑，超出部分按加班折算
 * - 饭补：只有标准工和手动折算有饭补，加班没有饭补
 *         饭补按工时比例换算，但最多1天饭补（超出部分无饭补）
 *         例如：9小时=30元，4.5小时=15元，12小时=30元(超出3小时加班无饭补)
 */
object StatsCalculator {
    
    /**
     * 计算统计数据
     * 
     * @param records 记工记录列表
     * @param dailyWorkHours 标准工时（默认9小时=1工）
     * @param overtimeWorkHours 加班工时（默认8小时=1工）
     * @param mealSubsidyStandard 饭补标准（元/天）
     * @param dailyWage 日工资标准
     * @return 统计数据
     */
    fun calculateStats(
        records: List<WorkRecord>,
        dailyWorkHours: Double,
        overtimeWorkHours: Double,
        mealSubsidyStandard: Double,
        dailyWage: Double
    ): StatsData {
        if (records.isEmpty()) {
            return StatsData()
        }
        
        // 过滤记录类型
        val standardRecords = records.filter { !it.isOvertime && !it.isManual }
        val manualRecords = records.filter { it.isManual && !it.isOvertime }
        val overtimeRecords = records.filter { it.isOvertime }
        
        // 标准工天数：hours <= dailyWorkHours 时按比例换算，超出部分按加班计算
        // 例如：9小时=1工, 4.5小时=0.5工, 12小时=1工(标准)+3小时(加班)
        val standardDays = if (dailyWorkHours > 0) {
            standardRecords.sumOf { record ->
                val hours = record.hours
                if (hours <= dailyWorkHours) {
                    hours / dailyWorkHours  // 按比例：4.5h / 9h = 0.5工
                } else {
                    1.0  // 超出标准工时的部分，标准工算1工，超出部分计入加班
                }
            }
        } else 0.0
        
        // 手动折算天数：同样逻辑，hours <= dailyWorkHours 按比例，超出部分按加班
        val manualDays = if (dailyWorkHours > 0) {
            manualRecords.sumOf { record ->
                val hours = record.hours
                if (hours <= dailyWorkHours) {
                    hours / dailyWorkHours  // 按比例：4.5h / 9h = 0.5工
                } else {
                    1.0  // 超出部分计入加班
                }
            }
        } else 0.0
        
        // 加班总小时 = 单独加班记录 + 标准/手动折算中超出标准工时的部分
        val overtimeFromStandard = if (dailyWorkHours > 0) {
            standardRecords.sumOf { record ->
                val hours = record.hours
                if (hours > dailyWorkHours) hours - dailyWorkHours else 0.0
            }
        } else 0.0
        
        val overtimeFromManual = if (dailyWorkHours > 0) {
            manualRecords.sumOf { record ->
                val hours = record.hours
                if (hours > dailyWorkHours) hours - dailyWorkHours else 0.0
            }
        } else 0.0
        
        val overtimeHours = overtimeRecords.sumOf { it.hours } + overtimeFromStandard + overtimeFromManual
        
        // 加班天数：按加班工时标准换算（hours / overtimeWorkHours）
        val overtimeDays = if (overtimeWorkHours > 0) {
            overtimeHours / overtimeWorkHours
        } else 0.0
        
        // 总标准工（标准工天数 + 手动折算天数 + 加班天数）
        val totalStandard = standardDays + manualDays + overtimeDays
        
        // 饭补计算：只有标准工和手动折算有饭补，加班没有饭补！
        // 饭补按工时比例换算：(hours / dailyWorkHours) × mealSubsidyStandard
        
        // 标准工饭补：按工时比例，但超过标准工时的部分不额外计算饭补
        // 例如：9小时=30元, 4.5小时=15元, 12小时=30元(超出3小时加班无饭补)
        val standardMealSubsidy = if (dailyWorkHours > 0) {
            standardRecords.sumOf { record ->
                if (record.mealSubsidy) {
                    val hours = record.hours
                    if (hours <= dailyWorkHours) {
                        hours / dailyWorkHours  // 按比例
                    } else {
                        1.0  // 超出部分无饭补，最多1天饭补
                    }
                } else 0.0
            } * mealSubsidyStandard
        } else 0.0
        
        // 手动折算饭补：同样逻辑
        val manualMealSubsidy = if (dailyWorkHours > 0) {
            manualRecords.sumOf { record ->
                if (record.mealSubsidy) {
                    val hours = record.hours
                    if (hours <= dailyWorkHours) {
                        hours / dailyWorkHours  // 按比例
                    } else {
                        1.0  // 超出部分无饭补
                    }
                } else 0.0
            } * mealSubsidyStandard
        } else 0.0
        
        // 加班没有饭补！
        val mealSubsidyTotal = standardMealSubsidy + manualMealSubsidy
        
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
     * 按加班工时标准换算天数
     * 
     * @param records 记工记录列表
     * @param overtimeWorkHours 加班工时标准（8小时=1工）
     */
    fun calculateOvertimeDistribution(
        records: List<WorkRecord>,
        overtimeWorkHours: Double
    ): Map<Double, Int> {
        // 按日期分组计算每天的加班天数
        val overtimeByDate = records
            .filter { it.isOvertime }
            .groupBy { it.date }
            .mapValues { (_, dayRecords) ->
                val totalHours = dayRecords.sumOf { it.hours }
                // 按加班工时标准换算天数
                if (overtimeWorkHours > 0) {
                    (totalHours / overtimeWorkHours).let { days ->
                        // 四舍五入到0.5的倍数
                        Math.round(days * 2).toDouble() / 2
                    }
                } else 0.0
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
     * 
     * @param records 记工记录列表
     * @param dailyWorkHours 标准工时
     * @param overtimeWorkHours 加班工时标准
     * @param dailyWage 日工资标准
     * @param mealSubsidyStandard 饭补标准
     */
    fun calculateMonthlyWage(
        records: List<WorkRecord>,
        dailyWorkHours: Double,
        overtimeWorkHours: Double,
        dailyWage: Double,
        mealSubsidyStandard: Double
    ): Double {
        val stats = calculateStats(
            records, dailyWorkHours, overtimeWorkHours, mealSubsidyStandard, dailyWage
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
