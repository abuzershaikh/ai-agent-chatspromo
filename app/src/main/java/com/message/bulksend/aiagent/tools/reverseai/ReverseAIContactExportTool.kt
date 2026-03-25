package com.message.bulksend.aiagent.tools.reverseai

import android.content.Context
import android.util.Log
import com.message.bulksend.aiagent.tools.globalsender.GlobalSenderManager
import com.message.bulksend.autorespond.ai.profile.SmartProfileExtractor
import com.message.bulksend.autorespond.documentreply.DocumentType
import com.message.bulksend.tablesheet.data.TableSheetDatabase
import com.message.bulksend.tablesheet.data.models.ColumnModel
import com.message.bulksend.tablesheet.data.models.ColumnType
import com.message.bulksend.tablesheet.data.repository.TableSheetRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ReverseAIContactExportTool(private val context: Context) {

    enum class ExportFormat(val displayName: String, val extension: String) {
        CSV("CSV", "csv"),
        XLSX("Excel", "xlsx"),
        VCF("VCF", "vcf")
    }

    data class ExportResult(
        val success: Boolean,
        val message: String,
        val format: ExportFormat,
        val filePath: String = "",
        val fileName: String = "",
        val contactCount: Int = 0
    )

    private data class SheetContact(
        val name: String,
        val phone: String,
        val sourceSheet: String
    )

    private val tableDatabase = TableSheetDatabase.getDatabase(context)
    private val repository = TableSheetRepository(
        tableDatabase.tableDao(),
        tableDatabase.columnDao(),
        tableDatabase.rowDao(),
        tableDatabase.cellDao(),
        tableDatabase.folderDao(),
        tableDatabase.formulaDependencyDao(),
        tableDatabase.cellSearchIndexDao(),
        tableDatabase.rowVersionDao(),
        tableDatabase.sheetTransactionDao(),
        tableDatabase.filterViewDao(),
        tableDatabase.conditionalFormatRuleDao(),
        tableDatabase
    )
    private val globalSenderManager = GlobalSenderManager(context)

    fun detectFormatFromInstruction(instruction: String): ExportFormat? {
        val normalized = instruction.lowercase(Locale.getDefault())
        return when {
            normalized.contains("vcf") || normalized.contains("vcard") -> ExportFormat.VCF
            normalized.contains("excel") || normalized.contains("xlsx") || normalized.contains("xls") || normalized.contains("exel") -> ExportFormat.XLSX
            normalized.contains("csv") -> ExportFormat.CSV
            else -> null
        }
    }

    fun isCustomerListExportInstruction(instruction: String): Boolean {
        val normalized = instruction.lowercase(Locale.getDefault())
        val hasListTarget = listOf(
            "customer list",
            "customers list",
            "contact list",
            "contacts list",
            "lead list",
            "sheet list",
            "name and number",
            "phone list",
            "mobile list"
        ).any { normalized.contains(it) } || listOf("customer", "contact", "lead", "sheet", "list").count {
            normalized.contains(it)
        } >= 2

        val hasExportAction = listOf(
            "export", "send", "bhej", "share", "download", "generate", "banake", "file"
        ).any { normalized.contains(it) }

        val hasKnownFormat = detectFormatFromInstruction(instruction) != null
        return (hasListTarget && hasExportAction) || (hasListTarget && hasKnownFormat)
    }

    suspend fun exportAndQueueToOwner(ownerPhoneRaw: String, format: ExportFormat): ExportResult =
        withContext(Dispatchers.IO) {
            try {
                val ownerPhone = normalizePhone(ownerPhoneRaw)
                if (ownerPhone.isBlank()) {
                    return@withContext ExportResult(
                        success = false,
                        message = "Owner phone number configured nahi hai.",
                        format = format
                    )
                }

                val contacts = collectContactsFromAllSheets()
                if (contacts.isEmpty()) {
                    return@withContext ExportResult(
                        success = false,
                        message = "Sheet me valid name+number data nahi mila.",
                        format = format
                    )
                }

                val file = generateExportFile(contacts, format)
                val queueResult = globalSenderManager.queueDocumentsForAccessibility(
                    phoneNumber = ownerPhone,
                    senderName = "Owner",
                    keyword = "OWNER_CONTACT_EXPORT_${format.name}",
                    documentPaths = listOf(file.absolutePath),
                    // DocumentSendService now resolves MIME from extension.
                    documentType = DocumentType.PDF
                )

                if (!queueResult.success) {
                    return@withContext ExportResult(
                        success = false,
                        message = "File bani, par send queue fail hui: ${queueResult.message}",
                        format = format,
                        filePath = file.absolutePath,
                        fileName = file.name,
                        contactCount = contacts.size
                    )
                }

                ExportResult(
                    success = true,
                    message = "Export file generate karke owner chat queue me daal di gayi.",
                    format = format,
                    filePath = file.absolutePath,
                    fileName = file.name,
                    contactCount = contacts.size
                )
            } catch (e: Exception) {
                Log.e(TAG, "Contact export failed: ${e.message}", e)
                ExportResult(
                    success = false,
                    message = "Export process fail hua: ${e.message}",
                    format = format
                )
            }
        }

    private suspend fun collectContactsFromAllSheets(): List<SheetContact> {
        val tables = tableDatabase.tableDao().getAllTables().first()
        if (tables.isEmpty()) return emptyList()

        val contacts = mutableListOf<SheetContact>()
        val uniquePhoneMap = linkedMapOf<String, SheetContact>()

        for (table in tables) {
            val columns = repository.getColumnsByTableIdSync(table.id)
            if (columns.isEmpty()) continue

            val columnsById = columns.associateBy { it.id }
            val phoneColumns = columns.filter(::isPhoneColumn)
            val nameColumns = columns.filter(::isNameColumn)
            val rows = repository.getRowsByTableIdSync(table.id)

            for (row in rows) {
                val cells = repository.getCellsByRowIds(listOf(row.id))
                if (cells.isEmpty()) continue

                val valuesByColumnId = cells.associate { it.columnId to it.value.trim() }
                val rowPhones = mutableListOf<String>()

                if (phoneColumns.isNotEmpty()) {
                    for (phoneColumn in phoneColumns) {
                        extractPhones(valuesByColumnId[phoneColumn.id].orEmpty()).forEach { rowPhones += it }
                    }
                }

                if (rowPhones.isEmpty()) {
                    for (cell in cells) {
                        extractPhones(cell.value).forEach { rowPhones += it }
                    }
                }

                if (rowPhones.isEmpty()) continue

                val resolvedName = resolveRowName(cells.map { it.value }, valuesByColumnId, nameColumns)
                val safeName = if (SmartProfileExtractor.isLikelyPersonName(resolvedName)) {
                    resolvedName
                } else {
                    "Unknown Lead"
                }

                rowPhones.map(::normalizePhone).filter { it.isNotBlank() }.distinct().forEach { phone ->
                    val key = phone.filter { it.isDigit() }
                    if (key.length < 7) return@forEach

                    val current = uniquePhoneMap[key]
                    if (current == null || current.name == "Unknown Lead") {
                        uniquePhoneMap[key] = SheetContact(
                            name = safeName,
                            phone = phone,
                            sourceSheet = table.name
                        )
                    }
                }
            }
        }

        contacts += uniquePhoneMap.values
        return contacts
    }

    private fun resolveRowName(
        rowValues: List<String>,
        valuesByColumnId: Map<Long, String>,
        nameColumns: List<ColumnModel>
    ): String {
        for (nameColumn in nameColumns) {
            val value = valuesByColumnId[nameColumn.id].orEmpty().trim()
            if (SmartProfileExtractor.isLikelyPersonName(value)) return value
        }

        for (value in rowValues) {
            val trimmed = value.trim()
            if (trimmed.isBlank()) continue
            if (extractPhones(trimmed).isNotEmpty()) continue
            if (SmartProfileExtractor.isLikelyPersonName(trimmed)) return trimmed
        }

        return "Unknown Lead"
    }

    private fun isPhoneColumn(column: ColumnModel): Boolean {
        if (column.type.equals(ColumnType.PHONE, ignoreCase = true)) return true
        val normalized = column.name.lowercase(Locale.getDefault())
        return normalized.contains("phone") ||
            normalized.contains("mobile") ||
            normalized.contains("whatsapp") ||
            normalized.contains("number") ||
            normalized.contains("contact")
    }

    private fun isNameColumn(column: ColumnModel): Boolean {
        val normalized = column.name.lowercase(Locale.getDefault())
        return normalized.contains("name") ||
            normalized.contains("customer") ||
            normalized.contains("client") ||
            normalized.contains("lead")
    }

    private fun extractPhones(text: String): List<String> {
        if (text.isBlank()) return emptyList()
        return PHONE_REGEX.findAll(text)
            .map { normalizePhone(it.value) }
            .filter { it.isNotBlank() }
            .toList()
    }

    private fun normalizePhone(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isBlank()) return ""

        val hasPlus = trimmed.startsWith("+")
        val digits = trimmed.filter { it.isDigit() }
        if (digits.length < 7) return ""
        return if (hasPlus) "+$digits" else digits
    }

    private fun generateExportFile(contacts: List<SheetContact>, format: ExportFormat): File {
        val exportDir = File(context.filesDir, "documents/reverse_ai_exports")
        if (!exportDir.exists()) {
            exportDir.mkdirs()
        }

        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val file = File(exportDir, "Customer_List_$timeStamp.${format.extension}")

        when (format) {
            ExportFormat.CSV -> writeCsv(file, contacts)
            ExportFormat.VCF -> writeVcf(file, contacts)
            ExportFormat.XLSX -> writeXlsx(file, contacts)
        }

        return file
    }

    private fun writeCsv(file: File, contacts: List<SheetContact>) {
        file.bufferedWriter().use { writer ->
            writer.appendLine("Name,Phone,Source Sheet")
            for (contact in contacts) {
                writer.appendLine(
                    "${escapeCsv(contact.name)},${escapeCsv(contact.phone)},${escapeCsv(contact.sourceSheet)}"
                )
            }
        }
    }

    private fun writeVcf(file: File, contacts: List<SheetContact>) {
        file.bufferedWriter().use { writer ->
            for (contact in contacts) {
                writer.appendLine("BEGIN:VCARD")
                writer.appendLine("VERSION:3.0")
                writer.appendLine("FN:${escapeVcf(contact.name)}")
                writer.appendLine("TEL;TYPE=CELL:${contact.phone}")
                writer.appendLine("NOTE:Source Sheet - ${escapeVcf(contact.sourceSheet)}")
                writer.appendLine("END:VCARD")
            }
        }
    }

    private fun writeXlsx(file: File, contacts: List<SheetContact>) {
        val workbook = XSSFWorkbook()
        try {
            val sheet = workbook.createSheet("Customers")
            val headerRow = sheet.createRow(0)
            headerRow.createCell(0).setCellValue("Name")
            headerRow.createCell(1).setCellValue("Phone")
            headerRow.createCell(2).setCellValue("Source Sheet")

            contacts.forEachIndexed { index, contact ->
                val row = sheet.createRow(index + 1)
                row.createCell(0).setCellValue(contact.name)
                row.createCell(1).setCellValue(contact.phone)
                row.createCell(2).setCellValue(contact.sourceSheet)
            }

            sheet.autoSizeColumn(0)
            sheet.autoSizeColumn(1)
            sheet.autoSizeColumn(2)

            FileOutputStream(file).use { output ->
                workbook.write(output)
            }
        } finally {
            workbook.close()
        }
    }

    private fun escapeCsv(value: String): String {
        val escaped = value.replace("\"", "\"\"")
        return "\"$escaped\""
    }

    private fun escapeVcf(value: String): String {
        return value.replace("\n", " ").replace(";", "\\;").replace(",", "\\,")
    }

    private companion object {
        private const val TAG = "ReverseAIContactExport"
        private val PHONE_REGEX = Regex("(\\+?\\d[\\d\\s()\\-]{7,}\\d)")
    }
}
