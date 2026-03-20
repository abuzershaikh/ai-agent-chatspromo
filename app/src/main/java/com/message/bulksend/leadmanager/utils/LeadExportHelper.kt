package com.message.bulksend.leadmanager.utils

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import com.message.bulksend.leadmanager.model.Lead
import com.message.bulksend.leadmanager.model.LeadStatus
import org.apache.poi.ss.usermodel.*
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

/**
 * Helper class to export leads to CSV and Excel formats
 */
object LeadExportHelper {
    
    private const val TAG = "LeadExportHelper"
    
    /**
     * Export leads to CSV format
     * @param context Android context
     * @param leads List of leads to export
     * @param fileName Base file name (without extension)
     * @param statusFilter Optional status filter for filename
     * @return File path if successful, null otherwise
     */
    fun exportToCSV(
        context: Context,
        leads: List<Lead>,
        fileName: String,
        statusFilter: LeadStatus? = null
    ): String? {
        return try {
            val csvContent = buildString {
                // Header
                appendLine("Name,Phone Number,Email,Country Code,Alternate Phone,Status,Source,Category,Priority,Lead Score,Notes,Tags,Product,Created Date,Last Message")
                
                // Data
                leads.forEach { lead ->
                    val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                    val createdDate = dateFormat.format(Date(lead.timestamp))
                    
                    // Escape commas and quotes in fields
                    val escapedName = escapeCSVField(lead.name)
                    val escapedEmail = escapeCSVField(lead.email)
                    val escapedSource = escapeCSVField(lead.source)
                    val escapedCategory = escapeCSVField(lead.category)
                    val escapedNotes = escapeCSVField(lead.notes)
                    val escapedTags = escapeCSVField(lead.tags.joinToString("; "))
                    val escapedProduct = escapeCSVField(lead.product)
                    val escapedLastMessage = escapeCSVField(lead.lastMessage)
                    
                    appendLine("$escapedName,\"${lead.phoneNumber}\",$escapedEmail,\"${lead.countryCode}\",\"${lead.alternatePhone}\",\"${lead.status.displayName}\",$escapedSource,$escapedCategory,\"${lead.priority.displayName}\",${lead.leadScore},$escapedNotes,$escapedTags,$escapedProduct,\"$createdDate\",$escapedLastMessage")
                }
            }
            
            val finalFileName = if (statusFilter != null) {
                "${fileName}_${statusFilter.displayName.replace(" ", "_")}.csv"
            } else {
                "$fileName.csv"
            }
            
            saveToDownloads(context, csvContent, finalFileName, "text/csv")
        } catch (e: Exception) {
            Log.e(TAG, "Error exporting to CSV", e)
            null
        }
    }
    
    /**
     * Export leads to Excel format
     * @param context Android context
     * @param leads List of leads to export
     * @param fileName Base file name (without extension)
     * @param statusFilter Optional status filter for filename
     * @return File path if successful, null otherwise
     */
    fun exportToExcel(
        context: Context,
        leads: List<Lead>,
        fileName: String,
        statusFilter: LeadStatus? = null
    ): String? {
        return try {
            val workbook = XSSFWorkbook()
            val sheet = workbook.createSheet("Leads")
            
            // Create header style
            val headerStyle = workbook.createCellStyle().apply {
                fillForegroundColor = IndexedColors.GREY_25_PERCENT.index
                fillPattern = FillPatternType.SOLID_FOREGROUND
                val font = workbook.createFont().apply {
                    bold = true
                    fontHeightInPoints = 12
                }
                setFont(font)
                borderBottom = BorderStyle.THIN
                borderTop = BorderStyle.THIN
                borderLeft = BorderStyle.THIN
                borderRight = BorderStyle.THIN
            }
            
            // Create data style
            val dataStyle = workbook.createCellStyle().apply {
                borderBottom = BorderStyle.THIN
                borderTop = BorderStyle.THIN
                borderLeft = BorderStyle.THIN
                borderRight = BorderStyle.THIN
            }
            
            // Create date style
            val dateStyle = workbook.createCellStyle().apply {
                dataFormat = workbook.createDataFormat().getFormat("yyyy-mm-dd hh:mm:ss")
                borderBottom = BorderStyle.THIN
                borderTop = BorderStyle.THIN
                borderLeft = BorderStyle.THIN
                borderRight = BorderStyle.THIN
            }
            
            // Create header row
            val headerRow = sheet.createRow(0)
            val headers = arrayOf(
                "Name", "Phone Number", "Email", "Country Code", "Alternate Phone", 
                "Status", "Source", "Category", "Priority", "Lead Score", 
                "Notes", "Tags", "Product", "Created Date", "Last Message"
            )
            
            headers.forEachIndexed { index, header ->
                val cell = headerRow.createCell(index)
                cell.setCellValue(header)
                cell.cellStyle = headerStyle
            }
            
            // Add data rows
            leads.forEachIndexed { rowIndex, lead ->
                val row = sheet.createRow(rowIndex + 1)
                
                // Name
                row.createCell(0).apply {
                    setCellValue(lead.name)
                    cellStyle = dataStyle
                }
                
                // Phone Number
                row.createCell(1).apply {
                    setCellValue(lead.phoneNumber)
                    cellStyle = dataStyle
                }
                
                // Email
                row.createCell(2).apply {
                    setCellValue(lead.email)
                    cellStyle = dataStyle
                }
                
                // Country Code
                row.createCell(3).apply {
                    setCellValue(lead.countryCode)
                    cellStyle = dataStyle
                }
                
                // Alternate Phone
                row.createCell(4).apply {
                    setCellValue(lead.alternatePhone)
                    cellStyle = dataStyle
                }
                
                // Status
                row.createCell(5).apply {
                    setCellValue(lead.status.displayName)
                    cellStyle = dataStyle
                }
                
                // Source
                row.createCell(6).apply {
                    setCellValue(lead.source)
                    cellStyle = dataStyle
                }
                
                // Category
                row.createCell(7).apply {
                    setCellValue(lead.category)
                    cellStyle = dataStyle
                }
                
                // Priority
                row.createCell(8).apply {
                    setCellValue(lead.priority.displayName)
                    cellStyle = dataStyle
                }
                
                // Lead Score
                row.createCell(9).apply {
                    setCellValue(lead.leadScore.toDouble())
                    cellStyle = dataStyle
                }
                
                // Notes
                row.createCell(10).apply {
                    setCellValue(lead.notes)
                    cellStyle = dataStyle
                }
                
                // Tags
                row.createCell(11).apply {
                    setCellValue(lead.tags.joinToString("; "))
                    cellStyle = dataStyle
                }
                
                // Product
                row.createCell(12).apply {
                    setCellValue(lead.product)
                    cellStyle = dataStyle
                }
                
                // Created Date
                row.createCell(13).apply {
                    setCellValue(Date(lead.timestamp))
                    cellStyle = dateStyle
                }
                
                // Last Message
                row.createCell(14).apply {
                    setCellValue(lead.lastMessage)
                    cellStyle = dataStyle
                }
            }
            
            // Auto-size columns
            for (i in headers.indices) {
                sheet.autoSizeColumn(i)
                // Set minimum width
                if (sheet.getColumnWidth(i) < 2000) {
                    sheet.setColumnWidth(i, 2000)
                }
                // Set maximum width to prevent very wide columns
                if (sheet.getColumnWidth(i) > 8000) {
                    sheet.setColumnWidth(i, 8000)
                }
            }
            
            val finalFileName = if (statusFilter != null) {
                "${fileName}_${statusFilter.displayName.replace(" ", "_")}.xlsx"
            } else {
                "$fileName.xlsx"
            }
            
            // Save to downloads
            val filePath = saveExcelToDownloads(context, workbook, finalFileName)
            workbook.close()
            filePath
            
        } catch (e: Exception) {
            Log.e(TAG, "Error exporting to Excel", e)
            null
        }
    }
    
    /**
     * Escape CSV field to handle commas, quotes, and newlines
     */
    private fun escapeCSVField(field: String): String {
        return if (field.contains(",") || field.contains("\"") || field.contains("\n")) {
            "\"${field.replace("\"", "\"\"")}\""
        } else {
            field
        }
    }
    
    /**
     * Save content to Downloads folder
     */
    private fun saveToDownloads(
        context: Context,
        content: String,
        fileName: String,
        mimeType: String
    ): String? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 10+ use MediaStore
            saveUsingMediaStore(context, content, fileName, mimeType)
        } else {
            // Older Android versions
            saveToExternalStorage(context, content, fileName)
        }
    }
    
    /**
     * Save Excel workbook to Downloads folder
     */
    private fun saveExcelToDownloads(
        context: Context,
        workbook: XSSFWorkbook,
        fileName: String
    ): String? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 10+ use MediaStore
            saveExcelUsingMediaStore(context, workbook, fileName)
        } else {
            // Older Android versions
            saveExcelToExternalStorage(context, workbook, fileName)
        }
    }
    
    /**
     * Save using MediaStore API (Android 10+)
     */
    private fun saveUsingMediaStore(
        context: Context,
        content: String,
        fileName: String,
        mimeType: String
    ): String? {
        return try {
            val contentValues = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, mimeType)
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/ChatsPromo/Leads")
            }
            
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
            
            uri?.let {
                resolver.openOutputStream(it)?.use { outputStream ->
                    outputStream.write(content.toByteArray())
                }
                Log.d(TAG, "CSV file saved: $fileName")
                "Downloads/ChatsPromo/Leads/$fileName"
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error saving CSV with MediaStore", e)
            null
        }
    }
    
    /**
     * Save Excel using MediaStore API (Android 10+)
     */
    private fun saveExcelUsingMediaStore(
        context: Context,
        workbook: XSSFWorkbook,
        fileName: String
    ): String? {
        return try {
            val contentValues = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/ChatsPromo/Leads")
            }
            
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
            
            uri?.let {
                resolver.openOutputStream(it)?.use { outputStream ->
                    workbook.write(outputStream)
                }
                Log.d(TAG, "Excel file saved: $fileName")
                "Downloads/ChatsPromo/Leads/$fileName"
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error saving Excel with MediaStore", e)
            null
        }
    }
    
    /**
     * Save to external storage (Android 9 and below)
     */
    @Suppress("DEPRECATION")
    private fun saveToExternalStorage(
        context: Context,
        content: String,
        fileName: String
    ): String? {
        return try {
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val chatsPromoDir = File(downloadsDir, "ChatsPromo")
            val leadsDir = File(chatsPromoDir, "Leads")
            
            if (!leadsDir.exists()) {
                leadsDir.mkdirs()
            }
            
            val file = File(leadsDir, fileName)
            FileOutputStream(file).use { outputStream ->
                outputStream.write(content.toByteArray())
            }
            
            Log.d(TAG, "CSV file saved: ${file.absolutePath}")
            file.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "Error saving CSV to external storage", e)
            null
        }
    }
    
    /**
     * Save Excel to external storage (Android 9 and below)
     */
    @Suppress("DEPRECATION")
    private fun saveExcelToExternalStorage(
        context: Context,
        workbook: XSSFWorkbook,
        fileName: String
    ): String? {
        return try {
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val chatsPromoDir = File(downloadsDir, "ChatsPromo")
            val leadsDir = File(chatsPromoDir, "Leads")
            
            if (!leadsDir.exists()) {
                leadsDir.mkdirs()
            }
            
            val file = File(leadsDir, fileName)
            FileOutputStream(file).use { outputStream ->
                workbook.write(outputStream)
            }
            
            Log.d(TAG, "Excel file saved: ${file.absolutePath}")
            file.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "Error saving Excel to external storage", e)
            null
        }
    }
    
    /**
     * Generate timestamped filename
     */
    fun generateFileName(prefix: String = "Leads"): String {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        return "${prefix}_$timeStamp"
    }
}