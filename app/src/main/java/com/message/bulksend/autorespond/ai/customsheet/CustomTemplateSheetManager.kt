package com.message.bulksend.autorespond.ai.customsheet

import android.content.Context
import com.message.bulksend.tablesheet.data.TableSheetDatabase
import com.message.bulksend.tablesheet.data.models.CellModel
import com.message.bulksend.tablesheet.data.models.ColumnModel
import com.message.bulksend.tablesheet.data.models.ColumnType
import com.message.bulksend.tablesheet.data.models.FolderModel
import com.message.bulksend.tablesheet.data.models.RowModel
import com.message.bulksend.tablesheet.data.models.TableModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CustomTemplateSheetManager(context: Context) {

    private val database = TableSheetDatabase.getDatabase(context.applicationContext)
    private val folderDao = database.folderDao()
    private val tableDao = database.tableDao()
    private val columnDao = database.columnDao()
    private val rowDao = database.rowDao()
    private val cellDao = database.cellDao()

    companion object {
        const val DEFAULT_READ_SHEET_NAME = "Agent Read Sheet"
        const val DEFAULT_WRITE_SHEET_NAME = "Agent Write Sheet"
        const val DEFAULT_SALES_SHEET_NAME = "Product Sales Sheet"

        private const val FOLDER_PREFIX = "Custom AI Agent"
        private const val PREBUILT_ROWS = 80
        private const val EXPAND_ROWS = 25
    }

    data class SheetSetupResult(
        val folderName: String,
        val readSheetName: String,
        val writeSheetName: String,
        val salesSheetName: String
    )

    data class SheetRowData(
        val tableName: String,
        val values: Map<String, String>
    )

    private data class ColumnSpec(
        val name: String,
        val type: String = ColumnType.STRING
    )

    suspend fun ensureTemplateSheetSystem(
        templateName: String,
        readSheetNameOverride: String? = null,
        writeSheetNameOverride: String? = null,
        salesSheetNameOverride: String? = null,
        writeCustomColumns: List<String> = emptyList()
    ): SheetSetupResult =
        withContext(Dispatchers.IO) {
            val safeTemplateName = normalizeTemplateName(templateName)
            val folderName = buildFolderName(safeTemplateName)
            val folder = ensureFolder(folderName)

            val readSheetName = normalizeSheetName(readSheetNameOverride, DEFAULT_READ_SHEET_NAME)
            val writeSheetName = normalizeSheetName(writeSheetNameOverride, DEFAULT_WRITE_SHEET_NAME)
            val salesSheetName = normalizeSheetName(salesSheetNameOverride, DEFAULT_SALES_SHEET_NAME)

            val writeColumns =
                (
                    listOf(
                        "Timestamp",
                        "Phone Number",
                        "User Name",
                        "Intent",
                        "Source Message",
                        "Key",
                        "Value"
                    ) + writeCustomColumns.map { normalizeColumnName(it) }
                ).distinctBy { it.lowercase(Locale.ROOT) }

            ensureSheet(
                folderId = folder.id,
                name = readSheetName,
                description = "User profile + reference data for $safeTemplateName",
                primaryColumns =
                    listOf(
                        ColumnSpec("Phone Number", ColumnType.PHONE),
                        ColumnSpec("Name", ColumnType.STRING),
                        ColumnSpec("Tag", ColumnType.STRING),
                        ColumnSpec("Context", ColumnType.STRING),
                        ColumnSpec("Notes", ColumnType.STRING),
                        ColumnSpec("Updated At", ColumnType.STRING)
                    )
            )

            ensureSheet(
                folderId = folder.id,
                name = writeSheetName,
                description = "AI write log and captured fields for $safeTemplateName",
                primaryColumns =
                    writeColumns.map { column ->
                        ColumnSpec(
                            name = column,
                            type = if (column.equals("Phone Number", ignoreCase = true)) ColumnType.PHONE else ColumnType.STRING
                        )
                    }
            )

            ensureSheet(
                folderId = folder.id,
                name = salesSheetName,
                description = "Product sales intent log for $safeTemplateName",
                primaryColumns =
                    listOf(
                        ColumnSpec("Timestamp", ColumnType.STRING),
                        ColumnSpec("Phone Number", ColumnType.PHONE),
                        ColumnSpec("Customer Name", ColumnType.STRING),
                        ColumnSpec("Product", ColumnType.STRING),
                        ColumnSpec("Quantity", ColumnType.INTEGER),
                        ColumnSpec("Amount", ColumnType.AMOUNT),
                        ColumnSpec("Status", ColumnType.STRING),
                        ColumnSpec("Notes", ColumnType.STRING)
                    )
            )

            SheetSetupResult(
                folderName = folderName,
                readSheetName = readSheetName,
                writeSheetName = writeSheetName,
                salesSheetName = salesSheetName
            )
        }

    suspend fun listSheetNames(templateName: String): List<String> = withContext(Dispatchers.IO) {
        val setup = ensureTemplateSheetSystem(templateName)
        val folder = folderDao.getFolderByName(setup.folderName) ?: return@withContext emptyList()
        tableDao.getTablesByFolderIdSync(folder.id).map { it.name }.sorted()
    }

    suspend fun listSheetNamesInFolder(folderName: String): List<String> = withContext(Dispatchers.IO) {
        val folder = folderDao.getFolderByName(folderName) ?: return@withContext emptyList()
        tableDao.getTablesByFolderIdSync(folder.id).map { it.name }.sorted()
    }

    suspend fun getReferenceRowByPhone(
        templateName: String,
        sheetName: String?,
        phoneNumber: String
    ): SheetRowData? = withContext(Dispatchers.IO) {
        val setup = ensureTemplateSheetSystem(templateName)
        val folder = folderDao.getFolderByName(setup.folderName) ?: return@withContext null
        val targetSheetName = sheetName?.trim().orEmpty().ifBlank { setup.readSheetName }
        val table = ensureSheetByName(folder.id, targetSheetName) ?: return@withContext null
        val columns = columnDao.getColumnsByTableIdSync(table.id)
        val rowId = findRowIdByPhone(table.id, columns, phoneNumber) ?: return@withContext null
        val cells = cellDao.getCellsByRowIdSync(rowId)
        val mapped =
            cells.mapNotNull { cell ->
                val column = columns.find { it.id == cell.columnId } ?: return@mapNotNull null
                if (cell.value.isBlank()) null else column.name to cell.value
            }.toMap()

        if (mapped.isEmpty()) return@withContext null
        SheetRowData(tableName = table.name, values = mapped)
    }

    suspend fun upsertDataForPhone(
        templateName: String,
        sheetName: String?,
        phoneNumber: String,
        userName: String,
        fields: Map<String, String>,
        sourceMessage: String = ""
    ): Boolean = withContext(Dispatchers.IO) {
        if (phoneNumber.isBlank()) return@withContext false

        val setup = ensureTemplateSheetSystem(templateName)
        val folder = folderDao.getFolderByName(setup.folderName) ?: return@withContext false
        val targetSheetName = sheetName?.trim().orEmpty().ifBlank { setup.writeSheetName }
        val table = ensureSheetByName(folder.id, targetSheetName) ?: createAdhocDataSheet(folder.id, targetSheetName)

        val rowId = findOrCreateRowForPhone(table.id, phoneNumber)
        val normalizedFields =
            fields.mapNotNull { (key, value) ->
                val safeKey = key.trim()
                val safeValue = value.trim()
                if (safeKey.isBlank() || safeValue.isBlank()) null else safeKey to safeValue
            }.toMap()

        upsertCellByColumnName(table.id, rowId, "Phone Number", sanitizePhone(phoneNumber))
        if (userName.isNotBlank()) {
            upsertCellByColumnName(table.id, rowId, "Name", userName.trim())
        }
        upsertCellByColumnName(table.id, rowId, "Updated At", nowFormatted())
        if (sourceMessage.isNotBlank()) {
            upsertCellByColumnName(table.id, rowId, "Last Source Message", sourceMessage.take(300))
        }

        normalizedFields.forEach { (key, value) ->
            upsertCellByColumnName(table.id, rowId, normalizeColumnName(key), value)
        }
        true
    }

    suspend fun logInteraction(
        templateName: String,
        phoneNumber: String,
        userName: String,
        userMessage: String,
        aiReply: String,
        intent: String
    ): Boolean = withContext(Dispatchers.IO) {
        if (phoneNumber.isBlank()) return@withContext false

        val setup = ensureTemplateSheetSystem(templateName)
        val folder = folderDao.getFolderByName(setup.folderName) ?: return@withContext false
        val writeSheet = ensureSheetByName(folder.id, setup.writeSheetName) ?: return@withContext false
        val rowId = findFirstEmptyRow(writeSheet.id) ?: createRow(writeSheet.id)

        upsertCellByColumnName(writeSheet.id, rowId, "Timestamp", nowFormatted())
        upsertCellByColumnName(writeSheet.id, rowId, "Phone Number", sanitizePhone(phoneNumber))
        upsertCellByColumnName(writeSheet.id, rowId, "User Name", userName.take(100))
        upsertCellByColumnName(writeSheet.id, rowId, "Intent", intent.take(80))
        upsertCellByColumnName(writeSheet.id, rowId, "Source Message", userMessage.take(300))
        upsertCellByColumnName(writeSheet.id, rowId, "Key", "AI Reply")
        upsertCellByColumnName(writeSheet.id, rowId, "Value", aiReply.take(400))
        true
    }

    suspend fun logProductLead(
        templateName: String,
        phoneNumber: String,
        userName: String,
        userMessage: String,
        intent: String
    ): Boolean = withContext(Dispatchers.IO) {
        if (phoneNumber.isBlank()) return@withContext false
        val lower = userMessage.lowercase(Locale.ROOT)
        val looksLikeSale =
            intent.contains("order", ignoreCase = true) ||
                intent.contains("buy", ignoreCase = true) ||
                lower.contains("buy") ||
                lower.contains("order") ||
                lower.contains("price") ||
                lower.contains("catalog")

        if (!looksLikeSale) return@withContext false

        val setup = ensureTemplateSheetSystem(templateName)
        val folder = folderDao.getFolderByName(setup.folderName) ?: return@withContext false
        val salesSheet = ensureSheetByName(folder.id, setup.salesSheetName) ?: return@withContext false
        val rowId = findFirstEmptyRow(salesSheet.id) ?: createRow(salesSheet.id)

        upsertCellByColumnName(salesSheet.id, rowId, "Timestamp", nowFormatted())
        upsertCellByColumnName(salesSheet.id, rowId, "Phone Number", sanitizePhone(phoneNumber))
        upsertCellByColumnName(salesSheet.id, rowId, "Customer Name", userName.take(100))
        upsertCellByColumnName(salesSheet.id, rowId, "Product", extractFirstProductHint(userMessage))
        upsertCellByColumnName(salesSheet.id, rowId, "Status", "LEAD")
        upsertCellByColumnName(salesSheet.id, rowId, "Notes", userMessage.take(350))
        true
    }

    fun buildFolderName(templateName: String): String {
        val safeTemplate = normalizeTemplateName(templateName)
        return "$FOLDER_PREFIX - $safeTemplate"
    }

    private suspend fun ensureFolder(folderName: String): FolderModel {
        val existing = folderDao.getFolderByName(folderName)
        if (existing != null) return existing
        val folderId =
            folderDao.insertFolder(
                FolderModel(
                    name = folderName,
                    colorHex = "#0EA5E9"
                )
            )
        return requireNotNull(folderDao.getFolderById(folderId))
    }

    private suspend fun ensureSheet(
        folderId: Long,
        name: String,
        description: String,
        primaryColumns: List<ColumnSpec>
    ): TableModel {
        val existing = tableDao.getTablesByFolderIdSync(folderId).find { it.name == name }
        return if (existing != null) {
            configureSheet(existing, primaryColumns)
            ensureRowBuffer(existing.id)
            existing
        } else {
            createSheet(folderId, name, description, primaryColumns)
        }
    }

    private suspend fun ensureSheetByName(folderId: Long, name: String): TableModel? {
        val sheet = tableDao.getTablesByFolderIdSync(folderId).find { it.name == name } ?: return null
        ensureRowBuffer(sheet.id)
        return sheet
    }

    private suspend fun createAdhocDataSheet(folderId: Long, rawName: String): TableModel {
        val safeName = normalizeSheetName(rawName, "Custom Data Sheet")
        return createSheet(
            folderId = folderId,
            name = safeName,
            description = "User-defined custom data sheet",
            primaryColumns =
                listOf(
                    ColumnSpec("Phone Number", ColumnType.PHONE),
                    ColumnSpec("Name", ColumnType.STRING),
                    ColumnSpec("Updated At", ColumnType.STRING),
                    ColumnSpec("Notes", ColumnType.STRING)
                )
        )
    }

    private suspend fun createSheet(
        folderId: Long,
        name: String,
        description: String,
        primaryColumns: List<ColumnSpec>
    ): TableModel {
        val tableId =
            tableDao.insertTable(
                TableModel(
                    name = name,
                    description = description,
                    folderId = folderId,
                    columnCount = primaryColumns.size + 4,
                    rowCount = PREBUILT_ROWS
                )
            )

        primaryColumns.forEachIndexed { index, spec ->
            columnDao.insertColumn(
                ColumnModel(
                    tableId = tableId,
                    name = spec.name,
                    type = spec.type,
                    orderIndex = index
                )
            )
        }

        repeat(4) { i ->
            columnDao.insertColumn(
                ColumnModel(
                    tableId = tableId,
                    name = "Column ${primaryColumns.size + i + 1}",
                    type = ColumnType.STRING,
                    orderIndex = primaryColumns.size + i
                )
            )
        }

        repeat(PREBUILT_ROWS) { index ->
            rowDao.insertRow(RowModel(tableId = tableId, orderIndex = index))
        }
        return requireNotNull(tableDao.getTableById(tableId))
    }

    private suspend fun configureSheet(sheet: TableModel, primaryColumns: List<ColumnSpec>) {
        val currentColumns = columnDao.getColumnsByTableIdSync(sheet.id).toMutableList()
        if (currentColumns.size < primaryColumns.size) {
            for (index in currentColumns.size until primaryColumns.size) {
                val spec = primaryColumns[index]
                columnDao.insertColumn(
                    ColumnModel(
                        tableId = sheet.id,
                        name = spec.name,
                        type = spec.type,
                        orderIndex = index
                    )
                )
            }
        }

        val freshColumns = columnDao.getColumnsByTableIdSync(sheet.id)
        primaryColumns.forEachIndexed { index, spec ->
            if (index >= freshColumns.size) return@forEachIndexed
            val col = freshColumns[index]
            columnDao.updateColumn(
                col.copy(
                    name = spec.name,
                    type = spec.type
                )
            )
        }
        tableDao.updateColumnCount(sheet.id, columnDao.getColumnsByTableIdSync(sheet.id).size)
    }

    private suspend fun ensureRowBuffer(tableId: Long) {
        val count = rowDao.getRowCountSync(tableId)
        if (count >= PREBUILT_ROWS) return
        val maxOrder = rowDao.getMaxOrderIndex(tableId) ?: -1
        repeat(PREBUILT_ROWS - count) { i ->
            rowDao.insertRow(RowModel(tableId = tableId, orderIndex = maxOrder + i + 1))
        }
        tableDao.updateRowCount(tableId, rowDao.getRowCountSync(tableId))
    }

    private suspend fun findRowIdByPhone(
        tableId: Long,
        columns: List<ColumnModel>,
        phoneNumber: String
    ): Long? {
        val phoneColumn = columns.find { it.name.equals("Phone Number", ignoreCase = true) } ?: return null
        val normalized = sanitizePhone(phoneNumber)
        val candidates = mutableSetOf<Long>()

        if (normalized.isNotBlank()) {
            candidates.addAll(cellDao.findCellsByColumnAndValue(phoneColumn.id, normalized).map { it.rowId })
        }
        if (phoneNumber.isNotBlank() && phoneNumber != normalized) {
            candidates.addAll(cellDao.findCellsByColumnAndValue(phoneColumn.id, phoneNumber).map { it.rowId })
        }
        return candidates.firstOrNull()
    }

    private suspend fun findOrCreateRowForPhone(tableId: Long, phoneNumber: String): Long {
        val columns = columnDao.getColumnsByTableIdSync(tableId)
        val existing = findRowIdByPhone(tableId, columns, phoneNumber)
        if (existing != null) return existing

        val empty = findFirstEmptyRow(tableId)
        if (empty != null) return empty
        return createRow(tableId)
    }

    private suspend fun findFirstEmptyRow(tableId: Long): Long? {
        val rows = rowDao.getRowsByTableIdSync(tableId).sortedBy { it.orderIndex }
        for (row in rows) {
            val cells = cellDao.getCellsByRowIdSync(row.id)
            if (cells.isEmpty() || cells.all { it.value.isBlank() }) {
                return row.id
            }
        }
        return null
    }

    private suspend fun createRow(tableId: Long): Long {
        val maxOrder = rowDao.getMaxOrderIndex(tableId) ?: -1
        var firstRowId: Long = 0L
        repeat(EXPAND_ROWS) { index ->
            val rowId = rowDao.insertRow(RowModel(tableId = tableId, orderIndex = maxOrder + index + 1))
            if (index == 0) {
                firstRowId = rowId
            }
        }
        tableDao.updateRowCount(tableId, rowDao.getRowCountSync(tableId))
        return firstRowId
    }

    private suspend fun upsertCellByColumnName(
        tableId: Long,
        rowId: Long,
        columnName: String,
        value: String
    ) {
        val column = ensureColumn(tableId, columnName)
        val existing = cellDao.getCellSync(rowId, column.id)
        if (existing == null) {
            cellDao.insertCell(
                CellModel(
                    rowId = rowId,
                    columnId = column.id,
                    value = value
                )
            )
        } else if (existing.value != value) {
            cellDao.updateCell(existing.copy(value = value))
        }
    }

    private suspend fun ensureColumn(tableId: Long, columnName: String): ColumnModel {
        val normalizedName = normalizeColumnName(columnName)
        val current = columnDao.getColumnsByTableIdSync(tableId)
        val existing = current.find { it.name.equals(normalizedName, ignoreCase = true) }
        if (existing != null) return existing

        val columnId =
            columnDao.insertColumn(
                ColumnModel(
                    tableId = tableId,
                    name = normalizedName,
                    type = if (normalizedName.equals("Phone Number", ignoreCase = true)) ColumnType.PHONE else ColumnType.STRING,
                    orderIndex = current.size
                )
            )
        tableDao.updateColumnCount(tableId, current.size + 1)
        return requireNotNull(columnDao.getColumnById(columnId))
    }

    private fun normalizeTemplateName(templateName: String): String {
        val sanitized =
            templateName.trim()
                .replace(Regex("[^A-Za-z0-9 _-]"), "")
                .replace(Regex("\\s+"), " ")
                .ifBlank { "Custom Template" }
        return sanitized.take(48)
    }

    private fun normalizeSheetName(rawName: String?, fallback: String): String {
        val cleaned =
            rawName.orEmpty()
                .trim()
                .replace(Regex("[^A-Za-z0-9 _-]"), " ")
                .replace(Regex("\\s+"), " ")
        return cleaned.ifBlank { fallback }.take(60)
    }

    private fun normalizeColumnName(raw: String): String {
        val clean =
            raw.trim()
                .replace(Regex("[^A-Za-z0-9 _-]"), " ")
                .replace(Regex("\\s+"), " ")
                .trim()
        if (clean.isBlank()) return "Field"
        return clean.take(40)
    }

    private fun sanitizePhone(raw: String): String {
        val digits = raw.replace(Regex("[^0-9]"), "")
        if (digits.isBlank()) return raw.trim()
        return if (digits.length > 10) digits.takeLast(10) else digits
    }

    private fun extractFirstProductHint(message: String): String {
        val token =
            message.split(Regex("\\s+"))
                .firstOrNull { it.length > 2 && !it.contains(Regex("[^A-Za-z0-9_-]")) }
        return token ?: "Unknown"
    }

    private fun nowFormatted(): String {
        return SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
    }
}
