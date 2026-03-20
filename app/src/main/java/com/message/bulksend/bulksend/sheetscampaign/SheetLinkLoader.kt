package com.message.bulksend.bulksend.sheetscampaign

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.poi.ss.usermodel.DataFormatter
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

/**
 * Class to handle loading sheet data from URLs/links
 */
class SheetLinkLoader {
    
    companion object {
        private const val TAG = "SheetLinkLoader"
        private const val TIMEOUT_CONNECT = 10000 // 10 seconds
        private const val TIMEOUT_READ = 15000 // 15 seconds
    }
    
    /**
     * Load sheet data from a URL
     * @param url The URL to load the sheet from
     * @return SheetData object or null if failed
     */
    suspend fun loadSheetFromUrl(url: String): SheetData? {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Loading sheet from URL: $url")
                
                // Validate URL
                if (!isValidUrl(url)) {
                    Log.e(TAG, "Invalid URL format: $url")
                    return@withContext null
                }
                
                // Convert Google Sheets URL to CSV export format if needed
                val processedUrl = processGoogleSheetsUrl(url)
                
                // Download the file
                val inputStream = downloadFile(processedUrl)
                    ?: return@withContext null
                
                // Determine file type and parse accordingly
                val sheetData = when {
                    processedUrl.contains("csv") || url.contains("csv") -> {
                        parseCsvFromStream(inputStream)
                    }
                    processedUrl.contains("xlsx") || url.contains("xlsx") -> {
                        parseExcelFromStream(inputStream)
                    }
                    else -> {
                        // Try CSV first, then Excel
                        try {
                            parseCsvFromStream(inputStream)
                        } catch (e: Exception) {
                            Log.d(TAG, "CSV parsing failed, trying Excel format")
                            parseExcelFromStream(inputStream)
                        }
                    }
                }
                
                inputStream.close()
                Log.d(TAG, "Successfully loaded sheet with ${sheetData?.rows?.size ?: 0} rows")
                sheetData
                
            } catch (e: Exception) {
                Log.e(TAG, "Error loading sheet from URL: ${e.message}", e)
                null
            }
        }
    }
    
    /**
     * Refresh sheet data from the same URL
     */
    suspend fun refreshSheetData(url: String): SheetData? {
        return loadSheetFromUrl(url)
    }
    
    /**
     * Validate if the URL is properly formatted
     */
    private fun isValidUrl(url: String): Boolean {
        return try {
            val urlObj = URL(url)
            urlObj.protocol in listOf("http", "https")
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Process Google Sheets URL to make it downloadable
     */
    private fun processGoogleSheetsUrl(url: String): String {
        return when {
            // Google Sheets sharing URL
            url.contains("docs.google.com/spreadsheets") && url.contains("/edit") -> {
                val sheetId = extractGoogleSheetId(url)
                if (sheetId != null) {
                    "https://docs.google.com/spreadsheets/d/$sheetId/export?format=csv"
                } else {
                    url
                }
            }
            // Already a Google Sheets export URL
            url.contains("docs.google.com/spreadsheets") && url.contains("export") -> {
                url
            }
            // Other URLs - return as is
            else -> url
        }
    }
    
    /**
     * Extract Google Sheet ID from sharing URL
     */
    private fun extractGoogleSheetId(url: String): String? {
        return try {
            val regex = "/spreadsheets/d/([a-zA-Z0-9-_]+)".toRegex()
            regex.find(url)?.groupValues?.get(1)
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Download file from URL
     */
    private fun downloadFile(url: String): InputStream? {
        return try {
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.connectTimeout = TIMEOUT_CONNECT
            connection.readTimeout = TIMEOUT_READ
            connection.requestMethod = "GET"
            
            // Add headers to mimic browser request
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            connection.setRequestProperty("Accept", "text/csv,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet,*/*")
            
            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                connection.inputStream
            } else {
                Log.e(TAG, "HTTP Error: $responseCode")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error downloading file: ${e.message}", e)
            null
        }
    }
    
    /**
     * Parse CSV data from InputStream
     */
    private fun parseCsvFromStream(inputStream: InputStream): SheetData? {
        return try {
            val reader = BufferedReader(InputStreamReader(inputStream, "UTF-8"))
            val lines = reader.readLines()
            reader.close()
            
            if (lines.isEmpty()) return null
            
            val headers = lines[0].split(",").map { it.trim().replace("\"", "") }
            val rows = lines.drop(1).mapNotNull { line ->
                val values = parseCsvLine(line)
                if (values.size == headers.size) {
                    val cleanedRow = headers.zip(values).toMap().mapValues { (key, value) ->
                        cleanPhoneNumber(key, value)
                    }
                    cleanedRow
                } else {
                    null // Skip malformed rows
                }
            }
            
            SheetData(headers, rows)
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing CSV: ${e.message}", e)
            null
        }
    }
    
    /**
     * Parse Excel data from InputStream
     */
    private fun parseExcelFromStream(inputStream: InputStream): SheetData? {
        return try {
            val workbook = XSSFWorkbook(inputStream)
            val sheet = workbook.getSheetAt(0)
            val formatter = DataFormatter()
            
            val headerRow = sheet.getRow(0) ?: return null
            val headers = mutableListOf<String>()
            for (cell in headerRow) {
                headers.add(formatter.formatCellValue(cell).trim())
            }
            
            val rows = mutableListOf<Map<String, String>>()
            for (i in 1..sheet.lastRowNum) {
                val currentRow = sheet.getRow(i) ?: continue
                val rowMap = mutableMapOf<String, String>()
                for ((j, header) in headers.withIndex()) {
                    val cell = currentRow.getCell(j)
                    val cellValue = formatter.formatCellValue(cell).trim()
                    val cleanedValue = cleanPhoneNumber(header, cellValue)
                    rowMap[header] = cleanedValue
                }
                rows.add(rowMap)
            }
            
            workbook.close()
            SheetData(headers, rows)
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing Excel: ${e.message}", e)
            null
        }
    }
    
    /**
     * Parse a single CSV line handling quoted values
     */
    private fun parseCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        var current = StringBuilder()
        var inQuotes = false
        var i = 0
        
        while (i < line.length) {
            val char = line[i]
            when {
                char == '"' && !inQuotes -> {
                    inQuotes = true
                }
                char == '"' && inQuotes -> {
                    if (i + 1 < line.length && line[i + 1] == '"') {
                        // Escaped quote
                        current.append('"')
                        i++ // Skip next quote
                    } else {
                        inQuotes = false
                    }
                }
                char == ',' && !inQuotes -> {
                    result.add(current.toString().trim())
                    current = StringBuilder()
                }
                else -> {
                    current.append(char)
                }
            }
            i++
        }
        
        // Add the last field
        result.add(current.toString().trim())
        return result
    }
    
    /**
     * Check if URL is accessible
     */
    suspend fun isUrlAccessible(url: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val connection = URL(processGoogleSheetsUrl(url)).openConnection() as HttpURLConnection
                connection.connectTimeout = 5000
                connection.requestMethod = "HEAD"
                connection.responseCode == HttpURLConnection.HTTP_OK
            } catch (e: Exception) {
                false
            }
        }
    }
    
    /**
     * Clean phone number by removing .0 suffix and other formatting issues
     */
    private fun cleanPhoneNumber(columnName: String, value: String): String {
        // Check if this is a phone number column
        val isPhoneColumn = columnName.contains("phone", ignoreCase = true) || 
                           columnName.contains("number", ignoreCase = true) ||
                           columnName.contains("mobile", ignoreCase = true) ||
                           columnName.contains("contact", ignoreCase = true)
        
        if (isPhoneColumn && value.isNotBlank()) {
            // Remove .0 suffix that appears when Excel treats numbers as decimals
            val cleanedValue = if (value.endsWith(".0")) {
                value.substring(0, value.length - 2)
            } else {
                value
            }
            
            // Remove any other decimal formatting for phone numbers
            return cleanedValue.replace(Regex("\\.0+$"), "").trim()
        }
        
        return value.trim()
    }
}