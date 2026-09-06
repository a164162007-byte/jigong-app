package com.worklogger.app.utils

/**
 * 解析后的单条记录（可能对应1或2条 WorkRecord：标准工 + 加班）
 */
data class ParsedWorkEntry(
    val date: String,            // yyyy-MM-dd
    val location: String,
    val isOvertime: Boolean,
    val overtimeHours: Double = 0.0,  // 仅加班记录有值
    val rawLine: String               // 原始文本行，用于预览
)

/**
 * 解析结果，包含成功解析的条目和无法识别的行
 */
data class ParseResult(
    val entries: List<ParsedWorkEntry>,
    val failedLines: List<String>
)

/**
 * 文本记工解析器
 *
 * 支持的格式：
 *   2月
 *   1号北京土城
 *   2号北京土城（加班3小时）
 *   3号 北京土城(加班 3 小时)
 *   2026年3月
 *   2026年3月1日 北京土城（加班3小时）   ← 带年份的完整日期也支持
 */
object TextRecordParser {

    // 纯月份标题：2月 / 02月 / 2026年2月（不匹配记录行）
    private val monthOnlyRegex = Regex("""^(?:\d{4}年)?(\d{1,2})月\s*$""")

    // 完整日期行：2026年3月1日 北京土城（加班3小时）
    private val fullDateRegex = Regex(
        """^(\d{4})年(\d{1,2})月(\d{1,2})日\s*(.+?)(?:[（(]\s*加班\s*([\d.]+)\s*小时\s*[）)])?\s*$"""
    )

    // 简写记录行：1号北京土城 / 1号 北京土城 / 1号北京土城（加班3小时）
    private val shortRecordRegex = Regex(
        """^(\d{1,2})号\s*(.+?)(?:[（(]\s*加班\s*([\d.]+)\s*小时\s*[）)])?\s*$"""
    )

    /**
     * 解析粘贴文本，返回扁平化的 ParsedWorkEntry 列表。
     * 含加班的行会生成 2 条 entry（标准工 + 加班）。
     *
     * @param text 用户粘贴的原始文本
     * @param fallbackYear 文本中没有年份信息时使用的年份，默认当前年
     * @param fallbackMonth 文本中没有月份信息时使用的月份，默认当前月
     * @param dailyWorkHours 标准工时，默认 9
     */
    fun parse(
        text: String,
        fallbackYear: Int = DateUtils.getYear(),
        fallbackMonth: Int = DateUtils.getMonth(),
        dailyWorkHours: Double = 9.0
    ): ParseResult {
        val entries = mutableListOf<ParsedWorkEntry>()
        val failedLines = mutableListOf<String>()
        val lines = text.lines().map { it.trim() }.filter { it.isNotBlank() }

        var currentMonth: Int? = null
        var currentYear: Int? = null

        for (line in lines) {

            // 1. 纯月份标题（含"2026年2月"这种年+月行）
            val monthMatch = monthOnlyRegex.find(line)
            if (monthMatch != null) {
                currentMonth = monthMatch.groupValues[1].toInt()
                // 如果行首有年份，也提取
                val yearInLine = Regex("""^(\d{4})年""").find(line)
                if (yearInLine != null) {
                    currentYear = yearInLine.groupValues[1].toInt()
                }
                continue
            }

            // 2. 完整日期行：2026年3月1日 ...
            val fullMatch = fullDateRegex.find(line)
            if (fullMatch != null) {
                val y = fullMatch.groupValues[1].toInt()
                val m = fullMatch.groupValues[2].toInt()
                val d = fullMatch.groupValues[3].toInt()
                val loc = fullMatch.groupValues[4].trim()
                val otHours = fullMatch.groupValues[5].toDoubleOrNull()
                val dateStr = String.format("%04d-%02d-%02d", y, m, d)
                entries += buildEntries(dateStr, loc, otHours, dailyWorkHours, line)
                continue
            }

            // 3. 简写记录行：X号 地点 ...
            val shortMatch = shortRecordRegex.find(line)
            if (shortMatch != null) {
                val d = shortMatch.groupValues[1].toInt()
                val loc = shortMatch.groupValues[2].trim()
                val otHours = shortMatch.groupValues[3].toDoubleOrNull()
                val y = currentYear ?: fallbackYear
                val m = currentMonth ?: fallbackMonth
                val dateStr = String.format("%04d-%02d-%02d", y, m, d)
                entries += buildEntries(dateStr, loc, otHours, dailyWorkHours, line)
                continue
            }

            // 无法识别的行收集到failedLines
            failedLines.add(line)
        }

        return ParseResult(entries, failedLines)
    }

    /**
     * 构造 entry：无加班→1条标准工；有加班→1条标准工 + 1条加班
     */
    private fun buildEntries(
        date: String,
        location: String,
        overtimeHours: Double?,
        dailyWorkHours: Double,
        rawLine: String
    ): List<ParsedWorkEntry> {
        val list = mutableListOf<ParsedWorkEntry>()
        // 标准工
        list.add(ParsedWorkEntry(
            date = date,
            location = location,
            isOvertime = false,
            overtimeHours = 0.0,
            rawLine = rawLine
        ))
        // 加班
        if (overtimeHours != null && overtimeHours > 0) {
            list.add(ParsedWorkEntry(
                date = date,
                location = location,
                isOvertime = true,
                overtimeHours = overtimeHours,
                rawLine = rawLine
            ))
        }
        return list
    }
}
