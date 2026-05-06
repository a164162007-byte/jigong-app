package com.worklogger.app.utils

import android.content.Context
import android.content.Intent
import android.os.Environment
import androidx.core.content.FileProvider
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.worklogger.app.model.WorkRecord
import org.apache.poi.ss.usermodel.*
import org.apache.poi.ss.usermodel.WorkbookFactory
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.File
import java.io.FileOutputStream
import java.io.File
import java.io.FileOutputStreamInputStream
import java.text.SimpleDateFormat
import java.util.*

/**
 * Excel 导出工具
 */
class ExcelExporter(private val context: Context) {
    
    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    
    /**
     * 导出为 Excel
     */
    fun exportToExcel(records: List<WorkRecord>): Result<File> {
        return try {
            val workbook = XSSFWorkbook()
            val sheet = workbook.createSheet("记工记录")
            
            // 创建表头样式
            val headerStyle = workbook.createCellStyle().apply {
                fillForegroundColor = IndexedColors.LIGHT_BLUE.index
                fillPattern = FillPatternType.SOLID_FOREGROUND
                alignment = HorizontalAlignment.CENTER
            }
            val headerFont = workbook.createFont().apply {
                bold = true
                color = IndexedColors.WHITE.index
            }
            headerStyle.setFont(headerFont)
            
            // 创建表头
            val headerRow = sheet.createRow(0)
            val headers = listOf("日期", "工时", "类型", "地点", "备注", "饭补", "创建时间")
            headers.forEachIndexed { index, header ->
                val cell = headerRow.createCell(index)
                cell.setCellValue(header)
                cell.cellStyle = headerStyle
            }
            
            // 填充数据
            records.forEachIndexed { index, record ->
                val row = sheet.createRow(index + 1)
                row.createCell(0).setCellValue(record.date)
                row.createCell(1).setCellValue(record.hours)
                row.createCell(2).setCellValue(
                    when {
                        record.isOvertime -> "加班"
                        record.isManual -> "手动折算"
                        else -> "标准工"
                    }
                )
                row.createCell(3).setCellValue(record.location)
                row.createCell(4).setCellValue(record.remark)
                row.createCell(5).setCellValue(if (record.mealSubsidy) "是" else "否")
                row.createCell(6).setCellValue(dateFormat.format(Date(record.createdAt)))
            }
            
            // 自动调整列宽
            headers.indices.forEach { sheet.setColumnWidth(it, 4500) }
            
            // 保存文件
            val exportDir = File(context.getExternalFilesDir(null), "exports")
            if (!exportDir.exists()) exportDir.mkdirs()
            
            val fileName = "记工记录_${System.currentTimeMillis()}.xlsx"
            val file = File(exportDir, fileName)
            
            workbook.use { wb ->
                FileOutputStream(file).use { fos ->
                    wb.write(fos)
                }
            }
            
            Result.success(file)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * 导出为 JSON
     */
    fun exportToJson(records: List<WorkRecord>): Result<File> {
        return try {
            val exportDir = File(context.getExternalFilesDir(null), "exports")
            if (!exportDir.exists()) exportDir.mkdirs()
            
            val fileName = "记工记录_${System.currentTimeMillis()}.json"
            val file = File(exportDir, fileName)
            
            val jsonData = mapOf(
                "exportTime" to dateFormat.format(Date()),
                "totalRecords" to records.size,
                "records" to records
            )
            
            file.writeText(gson.toJson(jsonData))
            Result.success(file)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * 从 Excel 导入
     */
    fun importFromExcel(file: File): Result<List<WorkRecord>> {
        return try {
            val records = mutableListOf<WorkRecord>()
            
            FileInputStream(file).use { fis ->
                WorkbookFactory.create(fis).use { workbook ->
                    val sheet = workbook.getSheetAt(0)
                    
                    // 跳过表头
                    for (i in 1..sheet.lastRowNum) {
                        val row = sheet.getRow(i) ?: continue
                        
                        val date = row.getCell(0)?.stringCellValue ?: continue
                        val hours = row.getCell(1)?.numericCellValue ?: 0.0
                        val typeStr = row.getCell(2)?.stringCellValue ?: "标准工"
                        val location = row.getCell(3)?.stringCellValue ?: ""
                        val remark = row.getCell(4)?.stringCellValue ?: ""
                        val mealSubsidy = row.getCell(5)?.stringCellValue == "是"
                        
                        val isOvertime = typeStr.contains("加班")
                        val isManual = typeStr.contains("手动")
                        
                        records.add(
                            WorkRecord(
                                date = date,
                                hours = hours,
                                isOvertime = isOvertime,
                                location = location,
                                remark = remark,
                                mealSubsidy = mealSubsidy,
                                isManual = isManual
                            )
                        )
                    }
                }
            }
            
            Result.success(records)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * 获取分享 Intent
     */
    fun getShareIntent(file: File): Intent {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        
        return Intent(Intent.ACTION_SEND).apply {
            type = when {
                file.name.endsWith(".xlsx") -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                file.name.endsWith(".json") -> "application/json"
                else -> "*/*"
            }
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
    
    /**
     * 获取导入模板文件
     */
    fun getImportTemplate(): Result<File> {
        return try {
            val exportDir = File(context.getExternalFilesDir(null), "templates")
            if (!exportDir.exists()) exportDir.mkdirs()
            
            val file = File(exportDir, "导入模板.xlsx")
            
            if (!file.exists()) {
                val workbook = XSSFWorkbook()
                val sheet = workbook.createSheet("记工记录")
                
                val headerRow = sheet.createRow(0)
                val headers = listOf("日期(yyyy-MM-dd)", "工时", "类型(标准工/加班/手动折算)", "地点", "备注", "饭补(是/否)")
                headers.forEachIndexed { index, header ->
                    headerRow.createCell(index).setCellValue(header)
                }
                
                // 添加示例行
                val exampleRow = sheet.createRow(1)
                exampleRow.createCell(0).setCellValue("2024-01-01")
                exampleRow.createCell(1).setCellValue(8.0)
                exampleRow.createCell(2).setCellValue("标准工")
                exampleRow.createCell(3).setCellValue("公司")
                exampleRow.createCell(4).setCellValue("")
                exampleRow.createCell(5).setCellValue("是")
                
                headers.indices.forEach { sheet.setColumnWidth(it, 6000) }
                
                workbook.use { wb ->
                    FileOutputStream(file).use { fos ->
                        wb.write(fos)
                    }
                }
            }
            
            Result.success(file)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
