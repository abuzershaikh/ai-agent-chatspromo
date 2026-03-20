package com.message.bulksend.leadmanager.utils

import android.content.Context
import android.net.Uri
import com.message.bulksend.leadmanager.model.Lead
import com.message.bulksend.leadmanager.model.LeadPriority
import com.message.bulksend.leadmanager.model.LeadStatus
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.UUID

/**
 * Utility class to import leads from various file formats
 * Supports: CSV, Excel (XLS/XLSX), VCF (vCard)
 */
class LeadImporter(private val context: Context) {
    
    data class ImportResult(
        val success: Boolean,
        val importedCount: Int,
        val skippedCount: Int,
        val errorCount: Int,
        val leads: List<Lead>,
        val errors: List<String>
    )
    
    /**
     * Import leads from a file URI
     * Auto-detects file type based on extension or source hint
     */
    fun importFromUri(uri: Uri, source: String = "Import"): ImportResult {
        val fileName = getFileName(uri) ?: "unknown"
        val sourceLower = source.lowercase()
        
        // First try to detect from source hint (user selection)
        return when {
            sourceLower.contains("csv") -> importFromCsv(uri, source)
            sourceLower.contains("excel") -> importFromExcel(uri, source)
            sourceLower.contains("vcf") -> importFromVcf(uri, source)
            // Then try file extension
            fileName.endsWith(".csv", ignoreCase = true) || 
            fileName.endsWith(".txt", ignoreCase = true) -> importFromCsv(uri, source)
            fileName.endsWith(".xls", ignoreCase = true) || 
            fileName.endsWith(".xlsx", ignoreCase = true) -> importFromExcel(uri, source)
            fileName.endsWith(".vcf", ignoreCase = true) -> importFromVcf(uri, source)
            // Default to CSV for unknown formats
            else -> importFromCsv(uri, source)
        }
    }
    
    /**
     * Import leads from CSV file
     * Expected columns: Name, Phone, Email (optional), Notes (optional)
     */
    fun importFromCsv(uri: Uri, source: String = "CSV Import"): ImportResult {
        val leads = mutableListOf<Lead>()
        val errors = mutableListOf<String>()
        var skipped = 0
        
        try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                BufferedReader(InputStreamReader(inputStream)).use { reader ->
                    var lineNumber = 0
                    var headerMap: Map<String, Int>? = null
                    
                    reader.forEachLine { line ->
                        lineNumber++
                        
                        if (line.isBlank()) return@forEachLine
                        
                        val columns = parseCsvLine(line)
                        
                        // First line is header
                        if (lineNumber == 1) {
                            headerMap = columns.mapIndexed { index, col -> 
                                col.lowercase().trim() to index 
                            }.toMap()
                            return@forEachLine
                        }
                        
                        try {
                            val lead = parseLeadFromCsv(columns, headerMap, source)
                            if (lead != null) {
                                leads.add(lead)
                            } else {
                                skipped++
                            }
                        } catch (e: Exception) {
                            errors.add("Line $lineNumber: ${e.message}")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            errors.add("Failed to read CSV: ${e.message}")
        }
        
        return ImportResult(
            success = errors.isEmpty() || leads.isNotEmpty(),
            importedCount = leads.size,
            skippedCount = skipped,
            errorCount = errors.size,
            leads = leads,
            errors = errors
        )
    }
    
    /**
     * Import leads from Excel file (XLS/XLSX)
     * Note: For Excel support, treats as CSV with tab delimiter
     */
    fun importFromExcel(uri: Uri, source: String = "Excel Import"): ImportResult {
        // For now, try to parse as CSV (tab-delimited)
        // Full Excel support would require Apache POI library
        return importFromCsv(uri, source)
    }
    
    /**
     * Import leads from VCF (vCard) file
     * Simple parser without external library
     */
    fun importFromVcf(uri: Uri, source: String = "VCF Import"): ImportResult {
        val leads = mutableListOf<Lead>()
        val errors = mutableListOf<String>()
        var skipped = 0
        
        try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val content = inputStream.bufferedReader().readText()
                val vcards = parseVcfContent(content)
                
                vcards.forEachIndexed { index, vcard ->
                    try {
                        val name = vcard["FN"] 
                            ?: vcard["N"]?.split(";")?.let { parts ->
                                "${parts.getOrNull(1) ?: ""} ${parts.getOrNull(0) ?: ""}".trim()
                            }
                            ?: "Contact ${index + 1}"
                        
                        val phone = vcard["TEL"]
                        
                        if (phone.isNullOrBlank()) {
                            skipped++
                            return@forEachIndexed
                        }
                        
                        val email = vcard["EMAIL"] ?: ""
                        val notes = vcard["NOTE"] ?: ""
                        
                        val lead = Lead(
                            id = UUID.randomUUID().toString(),
                            name = name,
                            phoneNumber = normalizePhone(phone),
                            email = email,
                            status = LeadStatus.NEW,
                            source = source,
                            lastMessage = "",
                            timestamp = System.currentTimeMillis(),
                            category = "Imported",
                            notes = notes,
                            priority = LeadPriority.MEDIUM
                        )
                        
                        leads.add(lead)
                    } catch (e: Exception) {
                        errors.add("Contact ${index + 1}: ${e.message}")
                    }
                }
            }
        } catch (e: Exception) {
            errors.add("Failed to read VCF: ${e.message}")
        }
        
        return ImportResult(
            success = errors.isEmpty() || leads.isNotEmpty(),
            importedCount = leads.size,
            skippedCount = skipped,
            errorCount = errors.size,
            leads = leads,
            errors = errors
        )
    }
    
    /**
     * Simple VCF parser
     */
    private fun parseVcfContent(content: String): List<Map<String, String>> {
        val vcards = mutableListOf<Map<String, String>>()
        var currentCard = mutableMapOf<String, String>()
        var inCard = false
        
        content.lines().forEach { line ->
            val trimmedLine = line.trim()
            
            when {
                trimmedLine.equals("BEGIN:VCARD", ignoreCase = true) -> {
                    inCard = true
                    currentCard = mutableMapOf()
                }
                trimmedLine.equals("END:VCARD", ignoreCase = true) -> {
                    if (inCard && currentCard.isNotEmpty()) {
                        vcards.add(currentCard.toMap())
                    }
                    inCard = false
                }
                inCard && trimmedLine.contains(":") -> {
                    val colonIndex = trimmedLine.indexOf(":")
                    val key = trimmedLine.substring(0, colonIndex)
                        .split(";").first() // Remove parameters like ;TYPE=CELL
                        .uppercase()
                    val value = trimmedLine.substring(colonIndex + 1)
                    
                    // Only store first occurrence of each field
                    if (!currentCard.containsKey(key)) {
                        currentCard[key] = value
                    }
                }
            }
        }
        
        return vcards
    }
    
    private fun parseLeadFromCsv(
        columns: List<String>,
        headerMap: Map<String, Int>?,
        source: String
    ): Lead? {
        if (headerMap == null) return null
        
        fun getColumn(vararg names: String): String {
            for (name in names) {
                val index = headerMap[name.lowercase()]
                if (index != null && index < columns.size) {
                    return columns[index].trim()
                }
            }
            return ""
        }
        
        val name = getColumn("name", "full name", "contact name", "customer name")
        val phone = getColumn("phone", "phone number", "mobile", "contact", "number", "tel")
        
        if (phone.isBlank()) return null
        
        val email = getColumn("email", "e-mail", "mail")
        val notes = getColumn("notes", "note", "comments", "comment", "description")
        val category = getColumn("category", "group", "type")
        val status = getColumn("status", "lead status")
        val priority = getColumn("priority", "lead priority")
        val tags = getColumn("tags", "tag", "labels")
        
        return Lead(
            id = UUID.randomUUID().toString(),
            name = name.ifBlank { "Unknown" },
            phoneNumber = normalizePhone(phone),
            email = email,
            status = parseStatus(status),
            source = source,
            lastMessage = "",
            timestamp = System.currentTimeMillis(),
            category = category.ifBlank { "Imported" },
            notes = notes,
            priority = parsePriority(priority),
            tags = if (tags.isNotBlank()) tags.split(",").map { it.trim() } else emptyList()
        )
    }
    
    private fun parseCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        var current = StringBuilder()
        var inQuotes = false
        
        for (char in line) {
            when {
                char == '"' -> inQuotes = !inQuotes
                char == ',' && !inQuotes -> {
                    result.add(current.toString().trim())
                    current = StringBuilder()
                }
                else -> current.append(char)
            }
        }
        result.add(current.toString().trim())
        
        return result
    }
    
    private fun normalizePhone(phone: String): String {
        return phone.replace(Regex("[^0-9+]"), "")
    }
    
    private fun parseStatus(status: String): LeadStatus {
        return when (status.lowercase()) {
            "new" -> LeadStatus.NEW
            "interested" -> LeadStatus.INTERESTED
            "contacted" -> LeadStatus.CONTACTED
            "qualified" -> LeadStatus.QUALIFIED
            "converted" -> LeadStatus.CONVERTED
            "customer" -> LeadStatus.CUSTOMER
            "lost" -> LeadStatus.LOST
            else -> LeadStatus.NEW
        }
    }
    
    private fun parsePriority(priority: String): LeadPriority {
        return when (priority.lowercase()) {
            "high" -> LeadPriority.HIGH
            "medium" -> LeadPriority.MEDIUM
            "low" -> LeadPriority.LOW
            else -> LeadPriority.MEDIUM
        }
    }
    
    private fun getFileName(uri: Uri): String? {
        return context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            cursor.moveToFirst()
            if (nameIndex >= 0) cursor.getString(nameIndex) else null
        }
    }
}
