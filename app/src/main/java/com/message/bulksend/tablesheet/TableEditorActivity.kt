package com.message.bulksend.tablesheet

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import com.message.bulksend.tablesheet.data.TableSheetDatabase
import com.message.bulksend.tablesheet.data.agent.SheetCommandExecutor
import com.message.bulksend.tablesheet.data.models.ColumnModel
import com.message.bulksend.tablesheet.data.models.ColumnType
import com.message.bulksend.tablesheet.data.models.RowModel
import com.message.bulksend.tablesheet.data.repository.TableSheetRepository
import com.message.bulksend.tablesheet.extractor.ExtractorResult
import com.message.bulksend.tablesheet.extractor.SheetDataExtractor
import com.message.bulksend.tablesheet.ui.components.TableEditorScreen
import com.message.bulksend.tablesheet.ui.components.sheets.PivotRowSummary
import com.message.bulksend.tablesheet.ui.components.sheets.PivotSummaryCard
import com.message.bulksend.ui.theme.BulksendTestTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import kotlin.math.abs

class TableEditorActivity : ComponentActivity() {
    
    private lateinit var repository: TableSheetRepository
    private var tableId: Long = 0
    private var tableName: String = ""
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        tableId = intent.getLongExtra("tableId", 0)
        tableName = intent.getStringExtra("tableName") ?: "Table"
        
        if (tableId == 0L) {
            Toast.makeText(this, "Invalid table", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        
        val database = TableSheetDatabase.getDatabase(this)
        repository = TableSheetRepository(
            database.tableDao(),
            database.columnDao(),
            database.rowDao(),
            database.cellDao(),
            database.folderDao(),
            database.formulaDependencyDao(),
            database.cellSearchIndexDao(),
            database.rowVersionDao(),
            database.sheetTransactionDao(),
            database.filterViewDao(),
            database.conditionalFormatRuleDao(),
            database
        )
        
        setContent {
            BulksendTestTheme {
                TableEditorScreenWrapper(
                    repository = repository,
                    tableId = tableId,
                    tableName = tableName,
                    onBackPressed = { finish() }
                )
            }
        }
    }
    
}

@Composable
fun TableEditorScreenWrapper(
    repository: TableSheetRepository,
    tableId: Long,
    tableName: String,
    onBackPressed: () -> Unit
) {
    val tables by repository.getAllTables().collectAsState(initial = emptyList())
    val columns by repository.getColumnsByTableId(tableId).collectAsState(initial = emptyList())
    val rows by repository.getRowsByTableId(tableId).collectAsState(initial = emptyList())
    val tableCells by repository.getCellsByTableId(tableId).collectAsState(initial = emptyList())
    val filterViews by repository.getFilterViewsByTableId(tableId).collectAsState(initial = emptyList())
    val conditionalRules by repository.getConditionalFormatRules(tableId).collectAsState(initial = emptyList())
    val tableModel = tables.firstOrNull { it.id == tableId }
    
    var cellsMap by remember { mutableStateOf<Map<Pair<Long, Long>, String>>(emptyMap()) }
    var pendingEdits by remember { mutableStateOf<Map<Pair<Long, Long>, String>>(emptyMap()) }
    var pivotSummaryCard by remember { mutableStateOf<PivotSummaryCard?>(null) }
    var extractorProcessing by remember { mutableStateOf(false) }
    var extractorProgress by remember { mutableFloatStateOf(0f) }
    var extractorStatus by remember { mutableStateOf("Processing file...") }
    
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current
    val sheetCommandExecutor = remember(context) { SheetCommandExecutor(context) }
    val sheetDataExtractor = remember(context) { SheetDataExtractor(context) }
    val pendingSaveJobs = remember { mutableMapOf<Pair<Long, Long>, Job>() }

    val extractorPickerLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            if (uri == null) return@rememberLauncherForActivityResult
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }

            scope.launch(Dispatchers.IO) {
                withContext(Dispatchers.Main) {
                    extractorProcessing = true
                    extractorProgress = 0.02f
                    extractorStatus = "Processing file..."
                }

                try {
                    val extraction =
                        runCatching {
                            sheetDataExtractor.extract(uri) { progress ->
                                scope.launch(Dispatchers.Main) {
                                    extractorProgress = progress.fraction
                                    extractorStatus = progress.message
                                }
                            }
                        }.getOrElse { error ->
                            withContext(Dispatchers.Main) {
                                Toast.makeText(
                                    context,
                                    "Extractor failed: ${error.message ?: "unable to read file"}",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                            return@launch
                        }

                    withContext(Dispatchers.Main) {
                        extractorProgress = 0.99f
                        extractorStatus = "Saving results..."
                    }

                    val insertedRows =
                        appendExtractorResultToSheet(
                            repository = repository,
                            tableId = tableId,
                            result = extraction
                        )

                    withContext(Dispatchers.Main) {
                        extractorProgress = 1f
                        extractorStatus = "Completed."
                        if (insertedRows <= 0) {
                            Toast.makeText(
                                context,
                                "No mobile/email found in selected ${extraction.sourceLabel}.",
                                Toast.LENGTH_LONG
                            ).show()
                        } else {
                            Toast.makeText(
                                context,
                                "Extractor added $insertedRows rows (${extraction.phoneNumbers.size} mobiles, ${extraction.emails.size} emails).",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                } finally {
                    withContext(Dispatchers.Main) {
                        extractorProcessing = false
                        extractorProgress = 0f
                        extractorStatus = "Processing file..."
                    }
                }
            }
        }
    
    DisposableEffect(Unit) {
        onDispose {
            pendingSaveJobs.values.forEach { it.cancel() }
            pendingSaveJobs.clear()
        }
    }

    LaunchedEffect(tableCells, pendingEdits) {
        val baseMap = tableCells.associate { Pair(it.rowId, it.columnId) to it.value }
        cellsMap = baseMap.toMutableMap().apply { putAll(pendingEdits) }
    }

    TableEditorScreen(
        tableName = tableName,
        columns = columns,
        rows = rows,
        cellsMap = cellsMap,
        filterViews = filterViews,
        conditionalRules = conditionalRules,
        pivotSummaryCard = pivotSummaryCard,
        frozenColumnCount = tableModel?.frozenColumnCount ?: 0,
        onBackPressed = onBackPressed,
        onAddColumn = { name, type, width, selectOptions ->
            // Add column on background thread with all properties
            scope.launch(Dispatchers.IO) {
                repository.addColumnWithOptions(tableId, name, type, width, selectOptions)
            }
        },
        onAddRows = { count ->
            scope.launch(Dispatchers.IO) {
                repository.addRows(tableId, count)
            }
        },
        onCellValueChange = { rowId, columnId, value ->
            val key = Pair(rowId, columnId)
            pendingEdits = pendingEdits.toMutableMap().apply { put(key, value) }
            cellsMap = cellsMap.toMutableMap().apply { put(key, value) }

            pendingSaveJobs.remove(key)?.cancel()
            pendingSaveJobs[key] = scope.launch(Dispatchers.IO) {
                delay(180)
                runCatching {
                    repository.updateCellValue(rowId, columnId, value)
                }.onSuccess {
                    withContext(Dispatchers.Main) {
                        pendingEdits = pendingEdits.toMutableMap().apply { remove(key) }
                    }
                }.onFailure {
                    withContext(Dispatchers.Main) {
                        pendingEdits = pendingEdits.toMutableMap().apply { remove(key) }
                    }
                }
            }
        },
        onPasteGridData = { startRowId, startColumnId, rawText ->
            scope.launch(Dispatchers.IO) {
                val parsedRows = parseClipboardGrid(rawText)
                if (parsedRows.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "No pasteable data found", Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }

                val orderedColumns = columns.sortedBy { it.orderIndex }
                val startColumnIndex = orderedColumns.indexOfFirst { it.id == startColumnId }
                if (startColumnIndex < 0) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Start column not found", Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }

                var orderedRows = repository.getRowsByTableIdSync(tableId).sortedBy { it.orderIndex }
                val startRowIndex = orderedRows.indexOfFirst { it.id == startRowId }
                if (startRowIndex < 0) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Start row not found", Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }

                val requiredRows = startRowIndex + parsedRows.size
                val missingRows = (requiredRows - orderedRows.size).coerceAtLeast(0)
                if (missingRows > 0) {
                    repository.addRows(tableId, missingRows)
                    orderedRows = repository.getRowsByTableIdSync(tableId).sortedBy { it.orderIndex }
                }

                var updatedCells = 0
                var updatedRows = 0
                var truncatedCells = 0

                for (rowOffset in parsedRows.indices) {
                    val targetRow = orderedRows.getOrNull(startRowIndex + rowOffset) ?: break
                    val sourceValues = parsedRows[rowOffset]
                    if (sourceValues.isEmpty()) continue

                    val rowUpdates = linkedMapOf<Long, String>()
                    for (columnOffset in sourceValues.indices) {
                        val targetColumn =
                            orderedColumns.getOrNull(startColumnIndex + columnOffset)
                        if (targetColumn == null) {
                            truncatedCells += 1
                            continue
                        }
                        rowUpdates[targetColumn.id] =
                            normalizePastedValueForColumn(sourceValues[columnOffset], targetColumn)
                    }

                    if (rowUpdates.isNotEmpty()) {
                        repository.updateRowValues(targetRow.id, rowUpdates)
                        updatedRows += 1
                        updatedCells += rowUpdates.size
                    }
                }

                withContext(Dispatchers.Main) {
                    val suffix =
                        buildString {
                            if (missingRows > 0) append(" Added $missingRows new rows.")
                            if (truncatedCells > 0) append(" $truncatedCells values skipped (no more columns).")
                        }
                    Toast.makeText(
                        context,
                        "Pasted $updatedCells cells across $updatedRows rows.$suffix",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        },
        onDeleteRow = { rowId ->
            scope.launch(Dispatchers.IO) {
                repository.deleteRow(rowId, tableId)
            }
        },
        onDeleteColumn = { columnId ->
            scope.launch(Dispatchers.IO) {
                repository.deleteColumn(columnId, tableId)
            }
        },
        onUpdateColumn = { columnId, name, type, width, selectOptions ->
            scope.launch(Dispatchers.IO) {
                repository.updateColumn(columnId, name, type, width, selectOptions)
            }
        },
        onReorderColumns = { reorderedColumns ->
            scope.launch(Dispatchers.IO) {
                repository.updateColumnsOrder(reorderedColumns)
            }
        },
        onFrozenColumnCountChange = { count ->
            scope.launch(Dispatchers.IO) {
                repository.setFrozenColumnCount(tableId, count)
            }
        },
        onSaveFilterView = { name, filtersJson, sortColumnId, sortDirection, isDefault ->
            scope.launch(Dispatchers.IO) {
                repository.saveFilterView(
                    tableId = tableId,
                    name = name,
                    filtersJson = filtersJson,
                    sortColumnId = sortColumnId,
                    sortDirection = sortDirection,
                    isDefault = isDefault
                )
            }
        },
        onDeleteFilterView = { viewId ->
            scope.launch(Dispatchers.IO) {
                repository.deleteFilterView(viewId)
            }
        },
        onSaveConditionalFormatRule = { columnId, ruleType, criteria, backgroundColor, textColor, priority, enabled ->
            scope.launch(Dispatchers.IO) {
                repository.saveConditionalFormatRule(
                    tableId = tableId,
                    columnId = columnId,
                    ruleType = ruleType,
                    criteria = criteria,
                    backgroundColor = backgroundColor,
                    textColor = textColor,
                    priority = priority,
                    enabled = enabled
                )
            }
        },
        onDeleteConditionalFormatRule = { ruleId ->
            scope.launch(Dispatchers.IO) {
                repository.deleteConditionalFormatRule(ruleId)
            }
        },
        onRunPivotRequest = { request ->
            scope.launch(Dispatchers.IO) {
                val groupByColumnName = columns.firstOrNull { it.id == request.groupByColumnId }?.name
                if (groupByColumnName.isNullOrBlank()) {
                    withContext(Dispatchers.Main) {
                        pivotSummaryCard = pivotErrorCard("Group-by column not found in current sheet schema.")
                    }
                    return@launch
                }

                val payload =
                    JSONObject()
                        .put("tableId", tableId)
                        .put("groupBy", groupByColumnName)
                        .put("operation", request.operation)

                if (!request.operation.equals("COUNT", ignoreCase = true)) {
                    val valueColumnName = columns.firstOrNull { it.id == request.valueColumnId }?.name
                    if (valueColumnName.isNullOrBlank()) {
                        withContext(Dispatchers.Main) {
                            pivotSummaryCard = pivotErrorCard("Value column is required for ${request.operation}.")
                        }
                        return@launch
                    }
                    payload.put("valueColumn", valueColumnName)
                }

                val result =
                    runCatching {
                        sheetCommandExecutor.execute("SHEET_PIVOT", payload)
                    }.getOrElse { error ->
                        SheetCommandExecutor.CommandResult(
                            success = false,
                            command = "SHEET_PIVOT",
                            message = error.message ?: "Failed to run SHEET_PIVOT"
                        )
                    }

                val card =
                    buildPivotSummaryCard(
                        result = result,
                        groupByColumnName = groupByColumnName,
                        operationRaw = request.operation
                    )
                withContext(Dispatchers.Main) {
                    pivotSummaryCard = card
                }
            }
        },
        onShareSheet = {
            scope.launch(Dispatchers.Default) {
                val csv =
                    buildSheetCsv(
                        columns = columns,
                        rows = rows,
                        cellsMap = cellsMap
                    )
                withContext(Dispatchers.Main) {
                    shareSheetAsCsv(context, tableName, csv)
                }
            }
        },
        extractorProcessing = extractorProcessing,
        extractorProgress = extractorProgress,
        extractorStatus = extractorStatus,
        onExtractorPickRequest = {
            extractorPickerLauncher.launch(arrayOf("image/*", "video/*", "text/plain", "text/*"))
        },
        isLeadFormSheet = false,
        onRefreshSync = null
    )
}

private fun buildPivotSummaryCard(
    result: SheetCommandExecutor.CommandResult,
    groupByColumnName: String,
    operationRaw: String
): PivotSummaryCard {
    val metric = operationRaw.trim().uppercase(Locale.ROOT).ifBlank { "COUNT" }
    val rowSummaries =
        if (result.success) {
            result.rows.map { row ->
                val label = row[groupByColumnName].orEmpty().ifBlank { "(blank)" }
                val value = row[metric].orEmpty().ifBlank { "0" }
                PivotRowSummary(label = label, metric = value)
            }
        } else {
            emptyList()
        }

    return PivotSummaryCard(
        success = result.success,
        title = if (result.success) "$metric by $groupByColumnName" else "SHEET_PIVOT Failed",
        message = result.message,
        groupByColumn = groupByColumnName,
        metric = metric,
        rows = rowSummaries,
        totalGroups = rowSummaries.size
    )
}

private fun pivotErrorCard(message: String): PivotSummaryCard {
    return PivotSummaryCard(
        success = false,
        title = "SHEET_PIVOT Failed",
        message = message,
        groupByColumn = "-",
        metric = "-",
        rows = emptyList(),
        totalGroups = 0
    )
}

private fun buildSheetCsv(
    columns: List<ColumnModel>,
    rows: List<RowModel>,
    cellsMap: Map<Pair<Long, Long>, String>
): String {
    val orderedColumns = columns.sortedBy { it.orderIndex }
    val orderedRows = rows.sortedBy { it.orderIndex }

    val header = orderedColumns.joinToString(",") { column -> toCsvToken(column.name) }
    val body =
        orderedRows.joinToString("\n") { row ->
            orderedColumns.joinToString(",") { column ->
                toCsvToken(cellsMap[row.id to column.id].orEmpty())
            }
        }

    return if (body.isBlank()) header else "$header\n$body"
}

private fun toCsvToken(raw: String): String {
    val escaped = raw.replace("\"", "\"\"")
    return "\"$escaped\""
}

private fun shareSheetAsCsv(
    context: Context,
    tableName: String,
    csvContent: String
) {
    val intent =
        Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_SUBJECT, "$tableName.csv")
            putExtra(Intent.EXTRA_TEXT, csvContent)
    }
    context.startActivity(Intent.createChooser(intent, "Share TableSheet"))
}

private suspend fun appendExtractorResultToSheet(
    repository: TableSheetRepository,
    tableId: Long,
    result: ExtractorResult
): Int {
    val emails = result.emails.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
    val phones = result.phoneNumbers.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
    if (emails.isEmpty() && phones.isEmpty()) return 0

    var columns = repository.getColumnsByTableIdSync(tableId).sortedBy { it.orderIndex }

    if (columns.none(::isEmailColumnCandidate)) {
        repository.addColumnWithOptions(
            tableId = tableId,
            name = "Email",
            type = ColumnType.EMAIL,
            width = 1.2f,
            selectOptions = null
        )
    }
    if (columns.none(::isPhoneColumnCandidate)) {
        repository.addColumnWithOptions(
            tableId = tableId,
            name = "Phone",
            type = ColumnType.PHONE,
            width = 1.2f,
            selectOptions = null
        )
    }

    columns = repository.getColumnsByTableIdSync(tableId).sortedBy { it.orderIndex }
    var rows = repository.getRowsByTableIdSync(tableId).sortedBy { it.orderIndex }

    val rowIds = rows.map { it.id }
    val candidateIds =
        columns
            .asSequence()
            .filter { isEmailColumnCandidate(it) || isPhoneColumnCandidate(it) }
            .map { it.id }
            .toSet()
    val candidateCells =
        if (rowIds.isEmpty() || candidateIds.isEmpty()) {
            emptyList()
        } else {
            repository.getCellsByRowIds(rowIds).filter { it.columnId in candidateIds }
        }
    val nonBlankCountByColumnId =
        candidateCells
            .groupBy { it.columnId }
            .mapValues { entry -> entry.value.count { it.value.trim().isNotEmpty() } }

    var primaryEmailColumnId =
        pickPrimaryExtractorColumnId(
            candidates = columns.filter(::isEmailColumnCandidate),
            nonBlankCountByColumnId = nonBlankCountByColumnId
        )
    var primaryPhoneColumnId =
        pickPrimaryExtractorColumnId(
            candidates = columns.filter(::isPhoneColumnCandidate),
            nonBlankCountByColumnId = nonBlankCountByColumnId
        )

    if (primaryEmailColumnId == null || primaryPhoneColumnId == null) return 0
    if (primaryEmailColumnId == primaryPhoneColumnId) {
        val alternatePhoneId =
            columns
                .firstOrNull { column ->
                    column.id != primaryEmailColumnId && isPhoneColumnCandidate(column)
                }
                ?.id
        if (alternatePhoneId != null) {
            primaryPhoneColumnId = alternatePhoneId
        } else {
            repository.addColumnWithOptions(
                tableId = tableId,
                name = "Phone",
                type = ColumnType.PHONE,
                width = 1.2f,
                selectOptions = null
            )
            columns = repository.getColumnsByTableIdSync(tableId).sortedBy { it.orderIndex }
            primaryPhoneColumnId =
                columns
                    .firstOrNull { column ->
                        column.id != primaryEmailColumnId && isPhoneColumnCandidate(column)
                    }
                    ?.id
        }
    }
    if (primaryPhoneColumnId == null || primaryEmailColumnId == primaryPhoneColumnId) {
        return 0
    }

    val allowedColumnIds = setOf(primaryEmailColumnId, primaryPhoneColumnId)
    columns
        .filter { it.id !in allowedColumnIds }
        .forEach { column ->
            repository.deleteColumn(column.id, tableId)
        }

    columns = repository.getColumnsByTableIdSync(tableId).sortedBy { it.orderIndex }
    val emailColumnId =
        columns.firstOrNull { it.id == primaryEmailColumnId }?.id
            ?: columns.firstOrNull(::isEmailColumnCandidate)?.id
    val phoneColumnId =
        columns.firstOrNull { it.id == primaryPhoneColumnId }?.id
            ?: columns.firstOrNull(::isPhoneColumnCandidate)?.id
    if (emailColumnId == null || phoneColumnId == null) return 0

    val incomingRowCount = maxOf(emails.size, phones.size)
    if (incomingRowCount <= 0) return 0

    if (rows.isEmpty()) {
        repository.addRows(tableId, incomingRowCount)
        rows = repository.getRowsByTableIdSync(tableId).sortedBy { it.orderIndex }
    }

    val currentValueLookup =
        repository
            .getCellsByRowIds(rows.map { it.id })
            .asSequence()
            .filter { cell -> cell.columnId == emailColumnId || cell.columnId == phoneColumnId }
            .associate { cell -> (cell.rowId to cell.columnId) to cell.value.trim() }

    val startRowIndex =
        rows.indexOfFirst { row ->
            currentValueLookup[row.id to emailColumnId].orEmpty().isBlank() &&
                currentValueLookup[row.id to phoneColumnId].orEmpty().isBlank()
        }.let { index -> if (index >= 0) index else rows.size }

    val requiredRows = startRowIndex + incomingRowCount
    val missingRows = (requiredRows - rows.size).coerceAtLeast(0)
    if (missingRows > 0) {
        repository.addRows(tableId, missingRows)
        rows = repository.getRowsByTableIdSync(tableId).sortedBy { it.orderIndex }
    }

    var writtenRows = 0
    for (offset in 0 until incomingRowCount) {
        val targetRow = rows.getOrNull(startRowIndex + offset) ?: break
        val values = linkedMapOf<Long, String>()
        emails.getOrNull(offset)?.takeIf { it.isNotBlank() }?.let { values[emailColumnId] = it }
        phones.getOrNull(offset)?.takeIf { it.isNotBlank() }?.let { values[phoneColumnId] = it }
        if (values.isNotEmpty()) {
            repository.updateRowValues(targetRow.id, values, source = "EXTRACTOR_IMPORT")
            writtenRows += 1
        }
    }

    return writtenRows
}

private fun pickPrimaryExtractorColumnId(
    candidates: List<ColumnModel>,
    nonBlankCountByColumnId: Map<Long, Int>
): Long? {
    return candidates
        .sortedWith(
            compareByDescending<ColumnModel> { nonBlankCountByColumnId[it.id] ?: 0 }
                .thenBy { it.orderIndex }
        )
        .firstOrNull()
        ?.id
}

private fun isEmailColumnCandidate(column: ColumnModel): Boolean {
    val normalized = normalizeColumnName(column.name)
    return column.type.equals(ColumnType.EMAIL, ignoreCase = true) ||
        normalized == "email" ||
        normalized == "email id" ||
        normalized == "email address"
}

private fun isPhoneColumnCandidate(column: ColumnModel): Boolean {
    val normalized = normalizeColumnName(column.name)
    return column.type.equals(ColumnType.PHONE, ignoreCase = true) ||
        normalized == "phone" ||
        normalized == "mobile" ||
        normalized == "number" ||
        normalized == "phone number" ||
        normalized == "mobile number" ||
        normalized == "contact number"
}

private fun normalizeColumnName(raw: String): String {
    return raw.trim().lowercase(Locale.ROOT).replace(Regex("\\s+"), " ")
}

private fun parseClipboardGrid(raw: String): List<List<String>> {
    val decoded = decodeEscapedClipboardDelimiters(raw)
    val normalized =
        decoded
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .trim('\n')

    if (normalized.isBlank()) return emptyList()

    val parsed =
        detectGridDelimiter(normalized)?.let { delimiter ->
            parseDelimitedGrid(normalized, delimiter)
        } ?: run {
            val lines = normalized.split('\n').filter { it.isNotBlank() }
            if (lines.any { MULTI_SPACE_DELIMITER_REGEX.containsMatchIn(it) }) {
                parseWhitespaceGrid(normalized)
            } else {
                lines.map { listOf(it) }
            }
        }

    return parsed
        .map { row ->
            row.map { token -> token.replace('\u0000', ' ').trim() }
        }
        .filter { row -> row.any { it.isNotEmpty() } }
}

private fun decodeEscapedClipboardDelimiters(raw: String): String {
    val hasRealDelimiter = raw.contains('\n') || raw.contains('\t') || raw.contains('\r')
    if (hasRealDelimiter) return raw

    val hasEscapedDelimiter = raw.contains("\\n") || raw.contains("\\t") || raw.contains("\\r")
    if (!hasEscapedDelimiter) return raw

    return raw
        .replace("\\r\\n", "\n")
        .replace("\\n", "\n")
        .replace("\\t", "\t")
        .replace("\\r", "\n")
}

private fun detectGridDelimiter(input: String): Char? {
    if (input.contains('\t')) return '\t'

    val lines = input.split('\n').filter { it.isNotBlank() }.take(20)
    if (lines.isEmpty()) return null

    val candidates = listOf(',', ';', '|')
    var bestDelimiter: Char? = null
    var bestScore = 0

    candidates.forEach { delimiter ->
        val score = lines.sumOf { line -> countDelimiterOutsideQuotes(line, delimiter) }
        if (score > bestScore) {
            bestScore = score
            bestDelimiter = delimiter
        }
    }

    return if (bestScore > 0) bestDelimiter else null
}

private fun countDelimiterOutsideQuotes(line: String, delimiter: Char): Int {
    var inQuotes = false
    var count = 0
    var index = 0

    while (index < line.length) {
        val ch = line[index]
        if (ch == '"') {
            val next = line.getOrNull(index + 1)
            if (inQuotes && next == '"') {
                index += 1
            } else {
                inQuotes = !inQuotes
            }
        } else if (!inQuotes && ch == delimiter) {
            count += 1
        }
        index += 1
    }

    return count
}

private fun parseDelimitedGrid(input: String, delimiter: Char): List<List<String>> {
    val rows = mutableListOf<List<String>>()
    var currentRow = mutableListOf<String>()
    val field = StringBuilder()
    var inQuotes = false
    var index = 0

    while (index < input.length) {
        val ch = input[index]
        if (inQuotes) {
            if (ch == '"') {
                val next = input.getOrNull(index + 1)
                if (next == '"') {
                    field.append('"')
                    index += 1
                } else {
                    inQuotes = false
                }
            } else {
                field.append(ch)
            }
            index += 1
            continue
        }

        when (ch) {
            '"' -> inQuotes = true
            delimiter -> {
                currentRow += field.toString()
                field.setLength(0)
            }
            '\n' -> {
                currentRow += field.toString()
                rows += currentRow
                currentRow = mutableListOf()
                field.setLength(0)
            }
            else -> field.append(ch)
        }
        index += 1
    }

    currentRow += field.toString()
    rows += currentRow
    return rows
}

private fun parseWhitespaceGrid(input: String): List<List<String>> {
    return input
        .split('\n')
        .mapNotNull { line ->
            val trimmed = line.trim()
            if (trimmed.isBlank()) {
                null
            } else {
                MULTI_SPACE_DELIMITER_REGEX.split(trimmed).map { it.trim() }
            }
        }
}

private fun normalizePastedValueForColumn(raw: String, column: ColumnModel): String {
    val value = raw.trim()
    if (value.isEmpty()) return ""

    return when (column.type.uppercase(Locale.ROOT)) {
        ColumnType.CHECKBOX -> normalizeBooleanToken(value) ?: value
        ColumnType.INTEGER -> {
            val sanitized = value.replace(",", "").replace(NON_INTEGER_REGEX, "")
            sanitized.toLongOrNull()?.toString() ?: value
        }
        ColumnType.DECIMAL, ColumnType.AMOUNT -> {
            val sanitized = value.replace(",", "").replace(NON_DECIMAL_REGEX, "")
            sanitized.toDoubleOrNull()?.let { number -> formatDecimal(number) } ?: value
        }
        ColumnType.MULTI_SELECT -> normalizeMultiSelectValue(value, column.selectOptions)
        ColumnType.SELECT, ColumnType.PRIORITY -> normalizeSingleSelectValue(value, column.selectOptions)
        else -> value
    }
}

private fun normalizeBooleanToken(raw: String): String? {
    return when (raw.trim().lowercase(Locale.ROOT)) {
        "true", "1", "yes", "y", "checked", "on" -> "true"
        "false", "0", "no", "n", "unchecked", "off" -> "false"
        else -> null
    }
}

private fun normalizeMultiSelectValue(raw: String, optionsRaw: String?): String {
    val candidates =
        raw
            .replace("\r", "\n")
            .split(',', ';', '|', '\n')
            .map { it.trim() }
            .filter { it.isNotEmpty() }

    if (candidates.isEmpty()) return ""

    val normalized = linkedSetOf<String>()
    candidates.forEach { token ->
        normalized += normalizeSingleSelectValue(token, optionsRaw)
    }
    return normalized.joinToString(", ")
}

private fun normalizeSingleSelectValue(raw: String, optionsRaw: String?): String {
    val token = raw.trim()
    if (token.isEmpty()) return ""

    val options = parseColumnOptions(optionsRaw)
    val canonical = options.firstOrNull { option -> option.equals(token, ignoreCase = true) }
    return canonical ?: token
}

private fun parseColumnOptions(raw: String?): List<String> {
    val input = raw?.trim().orEmpty()
    if (input.isEmpty()) return emptyList()

    if (input.startsWith("[") && input.endsWith("]")) {
        val parsed =
            runCatching {
                buildList {
                    val array = JSONArray(input)
                    for (index in 0 until array.length()) {
                        val value = array.optString(index).trim().trim('"')
                        if (value.isNotEmpty()) add(value)
                    }
                }
            }.getOrDefault(emptyList())

        if (parsed.isNotEmpty()) return parsed
    }

    return input
        .replace("\r", "\n")
        .split(',', ';', '\n')
        .map { it.trim().trim('"') }
        .filter { it.isNotEmpty() }
}

private fun formatDecimal(value: Double): String {
    if (abs(value - value.toLong().toDouble()) < 0.0000001) {
        return value.toLong().toString()
    }
    return value.toString().trimEnd('0').trimEnd('.')
}

private val MULTI_SPACE_DELIMITER_REGEX = Regex("\\s{2,}")
private val NON_INTEGER_REGEX = Regex("[^0-9+\\-]")
private val NON_DECIMAL_REGEX = Regex("[^0-9+\\-.]")
