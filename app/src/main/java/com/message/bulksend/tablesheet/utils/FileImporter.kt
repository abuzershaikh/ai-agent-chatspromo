package com.message.bulksend.tablesheet.utils

import android.content.Context
import android.net.Uri
import org.apache.poi.ss.usermodel.WorkbookFactory
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.apache.poi.hssf.usermodel.HSSFWorkbook
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * File Importer for CSV, Excel, VCF files and Google Sheets links
 */
object FileImporter {
    
    data class ImportedData(
        val headers: List<String>,
        val rows: List<List<String>>,
        val fileName: String,
        val fileType: FileType
    )
    
    enum class FileType {
        CSV, EXCEL, VCF, GOOGLE_SHEET, EXCEL_ONLINE, UNKNOWN
    }
    
    /**
     * Import from URL (Google Sheets or Excel Online)
     */
    suspend fun importFromUrl(url: String): ImportedData? = withContext(Dispatchers.IO) {
        try {
            val csvUrl = convertToExportUrl(url)
            if (csvUrl == null) {
                return@withContext null
            }
            
            val connection = URL(csvUrl).openConnection()
            connection.connectTimeout = 15000
            connection.readTimeout = 15000
            
            val inputStream = connection.getInputStream()
            val reader = BufferedReader(InputStreamReader(inputStream))
            val records = reader.use { buffered -> parseCsvRecords(buffered) }
            inputStream.close()
            if (records.isEmpty()) return@withContext null

            val headers = sanitizeCsvHeader(records.first())
            val rows =
                records.drop(1).map { row ->
                    if (row.size < headers.size) row + List(headers.size - row.size) { "" } else row
                }.filter { row -> row.any { it.isNotBlank() } }
            
            val fileName = when {
                url.contains("docs.google.com") -> "Google Sheet Import"
                url.contains("1drv.ms") || url.contains("onedrive") -> "Excel Online Import"
                else -> "Online Sheet Import"
            }
            
            ImportedData(headers, rows, fileName, FileType.GOOGLE_SHEET)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    
    /**
     * Convert Google Sheets or Excel Online URL to CSV export URL
     */
    private fun convertToExportUrl(url: String): String? {
        return when {
            // Google Sheets URL
            url.contains("docs.google.com/spreadsheets") -> {
                // Extract sheet ID from various URL formats
                val sheetIdRegex = "/d/([a-zA-Z0-9-_]+)".toRegex()
                val matchResult = sheetIdRegex.find(url)
                val sheetId = matchResult?.groupValues?.get(1)
                
                if (sheetId != null) {
                    // Convert to CSV export URL
                    "https://docs.google.com/spreadsheets/d/$sheetId/export?format=csv"
                } else {
                    null
                }
            }
            
            // Excel Online (OneDrive/SharePoint) - Try direct download
            url.contains("1drv.ms") || url.contains("onedrive.live.com") -> {
                // For OneDrive, try to convert to download link
                if (url.contains("?")) {
                    "$url&download=1"
                } else {
                    "$url?download=1"
                }
            }
            
            // Already a direct CSV/Excel URL
            url.endsWith(".csv") || url.endsWith(".xlsx") || url.endsWith(".xls") -> {
                url
            }
            
            else -> null
        }
    }
    
    /**
     * Check if URL is a valid sheet link
     */
    fun isValidSheetUrl(url: String): Boolean {
        return url.contains("docs.google.com/spreadsheets") ||
               url.contains("1drv.ms") ||
               url.contains("onedrive.live.com") ||
               url.contains("sharepoint.com") ||
               url.endsWith(".csv") ||
               url.endsWith(".xlsx") ||
               url.endsWith(".xls")
    }
    
    /**
     * Detect file type from URI
     */
    fun detectFileType(context: Context, uri: Uri): FileType {
        val mimeType = context.contentResolver.getType(uri)
        val fileName = getFileName(context, uri)
        
        return when {
            mimeType?.contains("csv") == true || fileName.endsWith(".csv", true) -> FileType.CSV
            mimeType?.contains("sheet") == true || 
            fileName.endsWith(".xlsx", true) || 
            fileName.endsWith(".xls", true) -> FileType.EXCEL
            mimeType?.contains("vcard") == true || 
            fileName.endsWith(".vcf", true) -> FileType.VCF
            else -> FileType.UNKNOWN
        }
    }
    
    /**
     * Get file name from URI
     */
    private fun getFileName(context: Context, uri: Uri): String {
        var fileName = "unknown"
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (cursor.moveToFirst() && nameIndex >= 0) {
                fileName = cursor.getString(nameIndex)
            }
        }
        return fileName
    }
    
    /**
     * Import file and return data
     */
    fun importFile(context: Context, uri: Uri): ImportedData? {
        val fileType = detectFileType(context, uri)
        val fileName = getFileName(context, uri)
        
        return when (fileType) {
            FileType.CSV -> importCSV(context, uri, fileName)
            FileType.EXCEL -> importExcel(context, uri, fileName)
            FileType.VCF -> importVCF(context, uri, fileName)
            FileType.GOOGLE_SHEET, FileType.EXCEL_ONLINE -> null // These are handled by importFromUrl
            FileType.UNKNOWN -> null
        }
    }
    
    /**
     * Import CSV file
     */
    private fun importCSV(context: Context, uri: Uri, fileName: String): ImportedData? {
        try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return null
            val reader = BufferedReader(InputStreamReader(inputStream))
            val records = reader.use { buffered -> parseCsvRecords(buffered) }
            inputStream.close()
            if (records.isEmpty()) return null

            val headers = sanitizeCsvHeader(records.first())
            val rows =
                records.drop(1).map { row ->
                    if (row.size < headers.size) row + List(headers.size - row.size) { "" } else row
                }.filter { row -> row.any { it.isNotBlank() } }
            
            return ImportedData(headers, rows, fileName, FileType.CSV)
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }
    
    /**
     * RFC4180-style CSV parser with quoted multiline field support.
     */
    private fun parseCsvRecords(reader: BufferedReader): List<List<String>> {
        val records = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val field = StringBuilder()
        var inQuotes = false

        while (true) {
            val codePoint = reader.read()
            if (codePoint == -1) {
                if (inQuotes) {
                    // If quote isn't closed, treat remaining content as final field.
                    inQuotes = false
                }
                if (field.isNotEmpty() || row.isNotEmpty()) {
                    row.add(field.toString())
                    records.add(row.toList())
                }
                break
            }

            val ch = codePoint.toChar()
            if (inQuotes) {
                if (ch == '"') {
                    reader.mark(1)
                    val next = reader.read()
                    if (next == '"'.code) {
                        field.append('"')
                    } else {
                        inQuotes = false
                        if (next != -1) {
                            reader.reset()
                        }
                    }
                } else {
                    field.append(ch)
                }
            } else {
                when (ch) {
                    '"' -> inQuotes = true
                    ',' -> {
                        row.add(field.toString())
                        field.setLength(0)
                    }
                    '\r' -> {
                        reader.mark(1)
                        val next = reader.read()
                        if (next != '\n'.code && next != -1) {
                            reader.reset()
                        }
                        row.add(field.toString())
                        field.setLength(0)
                        records.add(row.toList())
                        row = mutableListOf()
                    }
                    '\n' -> {
                        row.add(field.toString())
                        field.setLength(0)
                        records.add(row.toList())
                        row = mutableListOf()
                    }
                    else -> field.append(ch)
                }
            }
        }

        return records
    }

    private fun sanitizeCsvHeader(headerRow: List<String>): List<String> {
        if (headerRow.isEmpty()) return emptyList()
        return headerRow.mapIndexed { index, token ->
            token
                .removePrefix("\uFEFF")
                .trim()
                .ifBlank { "Column ${index + 1}" }
        }
    }
    
    /**
     * Import Excel file (.xlsx or .xls)
     */
    private fun importExcel(context: Context, uri: Uri, fileName: String): ImportedData? {
        try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return null
            val workbook = WorkbookFactory.create(inputStream)
            
            // Get first sheet
            val sheet = workbook.getSheetAt(0)
            if (sheet.physicalNumberOfRows == 0) return null
            
            // First row as headers
            val headerRow = sheet.getRow(0)
            val headers = mutableListOf<String>()
            for (i in 0 until headerRow.lastCellNum) {
                val cell = headerRow.getCell(i)
                headers.add(cell?.toString()?.trim() ?: "Column ${i + 1}")
            }
            
            // Rest as data rows
            val rows = mutableListOf<List<String>>()
            for (i in 1 until sheet.physicalNumberOfRows) {
                val row = sheet.getRow(i) ?: continue
                val rowData = mutableListOf<String>()
                
                for (j in 0 until headers.size) {
                    val cell = row.getCell(j)
                    rowData.add(cell?.toString()?.trim() ?: "")
                }
                
                rows.add(rowData)
            }
            
            workbook.close()
            inputStream.close()
            
            return ImportedData(headers, rows, fileName, FileType.EXCEL)
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }
    
    /**
     * Import VCF (vCard) file
     */
    private fun importVCF(context: Context, uri: Uri, fileName: String): ImportedData? {
        try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return null
            val reader = BufferedReader(InputStreamReader(inputStream))
            
            val contacts = mutableListOf<Map<String, String>>()
            var currentContact = mutableMapOf<String, String>()
            
            reader.forEachLine { line ->
                when {
                    line.startsWith("BEGIN:VCARD") -> {
                        currentContact = mutableMapOf()
                    }
                    line.startsWith("END:VCARD") -> {
                        if (currentContact.isNotEmpty()) {
                            contacts.add(currentContact)
                        }
                    }
                    line.contains(":") -> {
                        val parts = line.split(":", limit = 2)
                        if (parts.size == 2) {
                            val key = parts[0].split(";")[0] // Remove parameters
                            val value = parts[1].trim()
                            
                            when (key) {
                                "FN" -> currentContact["Name"] = value
                                "TEL" -> {
                                    val phoneKey = if (currentContact.containsKey("Phone")) {
                                        "Phone ${currentContact.size}"
                                    } else {
                                        "Phone"
                                    }
                                    currentContact[phoneKey] = value
                                }
                                "EMAIL" -> {
                                    val emailKey = if (currentContact.containsKey("Email")) {
                                        "Email ${currentContact.size}"
                                    } else {
                                        "Email"
                                    }
                                    currentContact[emailKey] = value
                                }
                                "ORG" -> currentContact["Organization"] = value
                                "TITLE" -> currentContact["Title"] = value
                                "ADR" -> currentContact["Address"] = value
                                "NOTE" -> currentContact["Note"] = value
                            }
                        }
                    }
                }
            }
            
            reader.close()
            
            if (contacts.isEmpty()) return null
            
            // Extract all unique headers
            val headers = contacts.flatMap { it.keys }.distinct().sorted()
            
            // Convert to rows
            val rows = contacts.map { contact ->
                headers.map { header -> contact[header] ?: "" }
            }
            
            return ImportedData(headers, rows, fileName, FileType.VCF)
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }
}
