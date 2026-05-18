package com.worklogger.app.utils

import java.text.SimpleDateFormat
import java.util.*

/**
 * 日期工具类
 */
object DateUtils {
    
    private const val DATE_FORMAT = "yyyy-MM-dd"
    private const val YEAR_MONTH_FORMAT = "yyyy-MM"
    private const val DISPLAY_DATE_FORMAT = "MM月dd日"
    private const val DISPLAY_FULL_FORMAT = "yyyy年MM月dd日"
    private const val TIME_FORMAT = "HH:mm"
    
    private val dateFormatter = SimpleDateFormat(DATE_FORMAT, Locale.getDefault())
    private val yearMonthFormatter = SimpleDateFormat(YEAR_MONTH_FORMAT, Locale.getDefault())
    private val displayDateFormatter = SimpleDateFormat(DISPLAY_DATE_FORMAT, Locale.getDefault())
    private val displayFullFormatter = SimpleDateFormat(DISPLAY_FULL_FORMAT, Locale.getDefault())
    private val timeFormatter = SimpleDateFormat(TIME_FORMAT, Locale.getDefault())
    
    fun today(): String = dateFormatter.format(Date())
    
    fun currentYearMonth(): String = yearMonthFormatter.format(Date())
    
    fun formatDate(date: Date): String = dateFormatter.format(date)
    
    fun formatYearMonth(date: Date): String = yearMonthFormatter.format(date)
    
    fun formatDisplayDate(date: String): String {
        return try {
            val parsed = dateFormatter.parse(date) ?: return date
            displayDateFormatter.format(parsed)
        } catch (e: Exception) {
            date
        }
    }
    
    fun formatDisplayFullDate(date: String): String {
        return try {
            val parsed = dateFormatter.parse(date) ?: return date
            displayFullFormatter.format(parsed)
        } catch (e: Exception) {
            date
        }
    }
    
    fun parseDate(dateString: String): Date? = dateFormatter.parse(dateString)
    
    fun parseYearMonth(yearMonth: String): Date? = yearMonthFormatter.parse(yearMonth)
    
    fun getYear(date: String = today()): Int {
        return try {
            parseDate(date)?.let { Calendar.getInstance().apply { time = it }.get(Calendar.YEAR) } ?: 0
        } catch (e: Exception) {
            0
        }
    }
    
    fun getMonth(date: String = today()): Int {
        return try {
            parseDate(date)?.let { Calendar.getInstance().apply { time = it }.get(Calendar.MONTH) + 1 } ?: 0
        } catch (e: Exception) {
            0
        }
    }
    
    fun getDay(date: String = today()): Int {
        return try {
            parseDate(date)?.let { Calendar.getInstance().apply { time = it }.get(Calendar.DAY_OF_MONTH) } ?: 0
        } catch (e: Exception) {
            0
        }
    }
    
    fun getMonthDays(year: Int, month: Int): Int {
        val calendar = Calendar.getInstance()
        calendar.set(year, month - 1, 1)
        return calendar.getActualMaximum(Calendar.DAY_OF_MONTH)
    }
    
    fun getFirstDayOfMonth(year: Int, month: Int): String {
        val calendar = Calendar.getInstance()
        calendar.set(year, month - 1, 1)
        return dateFormatter.format(calendar.time)
    }
    
    fun getLastDayOfMonth(year: Int, month: Int): String {
        val calendar = Calendar.getInstance()
        calendar.set(year, month - 1, getMonthDays(year, month))
        return dateFormatter.format(calendar.time)
    }
    
    fun getYearMonthFirstDay(yearMonth: String): String {
        return "$yearMonth-01"
    }
    
    fun getYearMonthLastDay(yearMonth: String): String {
        val parts = yearMonth.split("-")
        if (parts.size != 2) return yearMonth
        val year = parts[0].toInt()
        val month = parts[1].toInt()
        val lastDay = getMonthDays(year, month)
        return String.format("%04d-%02d-%02d", year, month, lastDay)
    }
    
    fun addMonths(yearMonth: String, months: Int): String {
        val parts = yearMonth.split("-")
        if (parts.size != 2) return yearMonth
        val year = parts[0].toInt()
        val month = parts[1].toInt()
        
        val calendar = Calendar.getInstance()
        calendar.set(year, month - 1, 1)
        calendar.add(Calendar.MONTH, months)
        
        return yearMonthFormatter.format(calendar.time)
    }
    
    fun addDays(date: String, days: Int): String {
        val parsed = parseDate(date) ?: return date
        val calendar = Calendar.getInstance()
        calendar.time = parsed
        calendar.add(Calendar.DAY_OF_MONTH, days)
        return dateFormatter.format(calendar.time)
    }
    
    fun getDaysAgo(days: Int): String {
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.DAY_OF_MONTH, -days)
        return dateFormatter.format(calendar.time)
    }
    
    fun getWeekday(date: String): Int {
        return try {
            parseDate(date)?.let { 
                Calendar.getInstance().apply { time = it }.get(Calendar.DAY_OF_WEEK) 
            } ?: 1
        } catch (e: Exception) {
            1
        }
    }
    
    fun getWeekdayName(date: String): String {
        return when (getWeekday(date)) {
            Calendar.SUNDAY -> "周日"
            Calendar.MONDAY -> "周一"
            Calendar.TUESDAY -> "周二"
            Calendar.WEDNESDAY -> "周三"
            Calendar.THURSDAY -> "周四"
            Calendar.FRIDAY -> "周五"
            Calendar.SATURDAY -> "周六"
            else -> ""
        }
    }
    
    fun isWeekend(date: String): Boolean {
        val weekday = getWeekday(date)
        return weekday == Calendar.SUNDAY || weekday == Calendar.SATURDAY
    }
    
    fun isToday(date: String): Boolean = date == today()

    fun getYearMonthNextFirstDay(yearMonth: String): String {
        val parts = yearMonth.split("-")
        if (parts.size != 2) return yearMonth
        val year = parts[0].toInt()
        val month = parts[1].toInt()
        return if (month == 12) {
            String.format("%04d-01-01", year + 1)
        } else {
            String.format("%04d-%02d-01", year, month + 1)
        }
    }

    
    fun formatTime(hour: Int, minute: Int): String {
        return String.format("%02d:%02d", hour, minute)
    }
    
    fun getHour(time: String): Int {
        return try {
            time.split(":")[0].toInt()
        } catch (e: Exception) {
            0
        }
    }
    
    fun getMinute(time: String): Int {
        return try {
            time.split(":")[1].toInt()
        } catch (e: Exception) {
            0
        }
    }
    
    fun getRelativeDate(date: String): String {
        val todayStr = today()
        return when {
            date == todayStr -> "今天"
            date == addDays(todayStr, -1) -> "昨天"
            date == addDays(todayStr, 1) -> "明天"
            else -> formatDisplayDate(date)
        }
    }
    
    fun getLast6MonthsYearMonths(): List<String> {
        val result = mutableListOf<String>()
        val calendar = Calendar.getInstance()
        for (i in 0 until 6) {
            result.add(yearMonthFormatter.format(calendar.time))
            calendar.add(Calendar.MONTH, -1)
        }
        return result.reversed()
    }
}
