package com.message.bulksend.tablesheet.data.repository

import androidx.room.withTransaction
import com.message.bulksend.tablesheet.data.TableSheetDatabase
import com.message.bulksend.tablesheet.data.dao.*
import com.message.bulksend.tablesheet.data.formula.FormulaEngine
import com.message.bulksend.tablesheet.data.formula.FormulaDependencyRef
import com.message.bulksend.tablesheet.data.models.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flowOf
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.ArrayDeque
import java.util.LinkedHashSet
import java.util.Locale
import java.util.UUID

class TableSheetRepository(
    private val tableDao: TableDao,
    private val columnDao: ColumnDao,
    private val rowDao: RowDao,
    private val cellDao: CellDao,
    private val folderDao: FolderDao,
    private val formulaDependencyDao: FormulaDependencyDao,
    private val cellSearchIndexDao: CellSearchIndexDao? = null,
    private val rowVersionDao: RowVersionDao? = null,
    private val sheetTransactionDao: SheetTransactionDao? = null,
    private val filterViewDao: FilterViewDao? = null,
    private val conditionalFormatRuleDao: ConditionalFormatRuleDao? = null,
    private val database: TableSheetDatabase? = null
) {
    // Table operations
    fun getAllTables(): Flow<List<TableModel>> = tableDao.getAllTables()
    
    suspend fun getTableById(tableId: Long): TableModel? = tableDao.getTableById(tableId)
    
    suspend fun createTable(name: String, description: String = "", tags: String? = null, folderId: Long? = null): Long {
        var tableId = -1L
        runInRepositoryTransaction {
            val table = TableModel(
                name = name,
                description = description,
                tags = if (tags.isNullOrBlank()) null else tags,
                columnCount = 4,
                rowCount = 20,
                folderId = folderId
            )
            tableId = tableDao.insertTable(table)

            // Create default 4 columns
            val defaultColumns = listOf("Column A", "Column B", "Column C", "Column D")
            val columnIds = mutableListOf<Long>()
            defaultColumns.forEachIndexed { index, colName ->
                val column = ColumnModel(
                    tableId = tableId,
                    name = colName,
                    type = ColumnType.STRING,
                    orderIndex = index
                )
                columnIds.add(columnDao.insertColumn(column))
            }

            // Create default 20 rows with empty cells
            for (rowIndex in 0 until 20) {
                val row = RowModel(tableId = tableId, orderIndex = rowIndex)
                val rowId = rowDao.insertRow(row)

                val cells = columnIds.map { columnId ->
                    CellModel(rowId = rowId, columnId = columnId, value = "")
                }
                if (cells.isNotEmpty()) {
                    cellDao.insertCells(cells)
                }
            }
        }
        return tableId
    }
    
    suspend fun updateTable(table: TableModel) = tableDao.updateTable(table)
    
    suspend fun refreshTableTimestamp(tableId: Long) {
        val table = getTableById(tableId)
        if (table != null) {
            updateTable(table.copy(updatedAt = System.currentTimeMillis()))
        }
    }
    
    suspend fun deleteTable(tableId: Long) {
        runInRepositoryTransaction {
            tableDao.deleteTableById(tableId)
            cellSearchIndexDao?.deleteByTableId(tableId)
            formulaDependencyDao.deleteDependenciesForTable(tableId)
        }
    }
    
    // Column operations
    fun getColumnsByTableId(tableId: Long): Flow<List<ColumnModel>> = 
        columnDao.getColumnsByTableId(tableId)
    
    suspend fun getColumnsByTableIdSync(tableId: Long): List<ColumnModel> =
        columnDao.getColumnsByTableIdSync(tableId)
    
    suspend fun addColumn(tableId: Long, name: String, type: String = ColumnType.STRING): Long {
        return addColumnWithOptions(tableId, name, type, 1f, null)
    }
    
    suspend fun addColumnWithOptions(
        tableId: Long, 
        name: String, 
        type: String, 
        width: Float = 1f, 
        selectOptions: String? = null
    ): Long {
        var columnId = -1L
        runInRepositoryTransaction {
            val count = columnDao.getColumnCount(tableId)
            val uniqueName = resolveUniqueColumnName(tableId, name)
            val column = ColumnModel(
                tableId = tableId,
                name = uniqueName,
                type = type,
                orderIndex = count,
                width = width,
                selectOptions = selectOptions
            )
            columnId = columnDao.insertColumn(column)
            tableDao.updateColumnCount(tableId, count + 1)

            // Add empty cells for existing rows
            val rows = rowDao.getRowsByTableIdSync(tableId)
            val cells = rows.map { row ->
                CellModel(rowId = row.id, columnId = columnId, value = "")
            }
            if (cells.isNotEmpty()) {
                cellDao.insertCells(cells)
            }
        }
        rebuildAllFormulaDependencies(tableId)
        
        return columnId
    }

    suspend fun updateColumn(column: ColumnModel) = columnDao.updateColumn(column)
    
    suspend fun updateColumn(columnId: Long, name: String, type: String, width: Float, selectOptions: String?) {
        val existing = columnDao.getColumnById(columnId)
        val normalizedName =
            if (existing != null) {
                resolveUniqueColumnName(existing.tableId, name, excludeColumnId = columnId)
            } else {
                name.trim().ifBlank { "Column" }
            }
        columnDao.updateColumnProperties(columnId, normalizedName, type, width, selectOptions)
    }
    
    suspend fun deleteColumn(columnId: Long, tableId: Long) {
        runInRepositoryTransaction {
            columnDao.deleteColumnById(columnId)
            cellSearchIndexDao?.deleteByColumnId(columnId)
            val count = columnDao.getColumnCount(tableId)
            tableDao.updateColumnCount(tableId, count)
        }
        rebuildAllFormulaDependencies(tableId)
    }
    
    suspend fun updateColumnsOrder(columns: List<ColumnModel>) {
        columnDao.updateColumnsOrder(columns)
    }
    
    // Row operations
    fun getRowsByTableId(tableId: Long): Flow<List<RowModel>> = 
        rowDao.getRowsByTableId(tableId)
    
    suspend fun getRowsByTableIdSync(tableId: Long): List<RowModel> =
        rowDao.getRowsByTableIdSync(tableId)

    suspend fun getRowsByTableIdAndIdsSync(tableId: Long, rowIds: List<Long>): List<RowModel> =
        if (rowIds.isEmpty()) emptyList() else rowDao.getRowsByTableIdAndIds(tableId, rowIds)
    
    suspend fun addRow(tableId: Long): Long {
        return addRows(tableId, 1).firstOrNull() ?: -1L
    }

    suspend fun addRows(tableId: Long, count: Int): List<Long> {
        val safeCount = count.coerceAtLeast(0)
        if (safeCount == 0) return emptyList()

        val insertedRowIds = mutableListOf<Long>()
        runInRepositoryTransaction {
            val columns = columnDao.getColumnsByTableIdSync(tableId)
            var maxOrder = rowDao.getMaxOrderIndex(tableId) ?: -1

            repeat(safeCount) {
                maxOrder += 1
                val rowId = rowDao.insertRow(RowModel(tableId = tableId, orderIndex = maxOrder))
                insertedRowIds += rowId
                if (columns.isNotEmpty()) {
                    val cells = columns.map { column ->
                        CellModel(rowId = rowId, columnId = column.id, value = "")
                    }
                    cellDao.insertCells(cells)
                }
            }

            val finalCount = rowDao.getRowCount(tableId)
            tableDao.updateRowCount(tableId, finalCount)
        }

        rebuildAllFormulaDependencies(tableId)
        return insertedRowIds
    }
    
    suspend fun addRowAtTop(tableId: Long): Long {
        var rowId = -1L
        runInRepositoryTransaction {
            // Get all existing rows and increment their orderIndex
            val existingRows = rowDao.getRowsByTableIdSync(tableId)
            existingRows.forEach { row ->
                rowDao.updateRow(row.copy(orderIndex = row.orderIndex + 1))
            }

            // Add new row at orderIndex = 0
            rowId = rowDao.insertRow(RowModel(tableId = tableId, orderIndex = 0))

            // Add empty cells for all columns
            val columns = columnDao.getColumnsByTableIdSync(tableId)
            val cells = columns.map { column ->
                CellModel(rowId = rowId, columnId = column.id, value = "")
            }
            if (cells.isNotEmpty()) {
                cellDao.insertCells(cells)
            }

            val count = rowDao.getRowCount(tableId)
            tableDao.updateRowCount(tableId, count)
        }
        rebuildAllFormulaDependencies(tableId)
        
        return rowId
    }
    
    // Find first empty row and return its ID (for LeadForm submissions)
    suspend fun getFirstEmptyRowId(tableId: Long): Long? {
        val rows = rowDao.getRowsByTableIdSync(tableId)
        val columns = columnDao.getColumnsByTableIdSync(tableId)
        
        android.util.Log.d("TableSheetRepo", "Checking ${rows.size} rows for empty cells in table: $tableId")
        
        // Check each row to see if it's empty
        for (row in rows.sortedBy { it.orderIndex }) {
            val cells = cellDao.getCellsByRowIds(listOf(row.id))
            val isEmpty = cells.all { cell -> cell.value.isBlank() }
            
            android.util.Log.d("TableSheetRepo", "Row ${row.id} (order: ${row.orderIndex}) isEmpty: $isEmpty, cells: ${cells.size}")
            
            if (isEmpty) {
                android.util.Log.d("TableSheetRepo", "Found empty row: ${row.id} at orderIndex: ${row.orderIndex}")
                return row.id
            }
        }
        
        android.util.Log.d("TableSheetRepo", "No empty row found in table: $tableId")
        return null // No empty row found
    }
    
    // Use existing empty row or create new one at top
    suspend fun useEmptyRowOrAddAtTop(tableId: Long): Long {
        android.util.Log.d("TableSheetRepo", "useEmptyRowOrAddAtTop called for table: $tableId")
        
        // First try to find an empty row
        val emptyRowId = getFirstEmptyRowId(tableId)
        
        return if (emptyRowId != null) {
            // Use existing empty row
            android.util.Log.d("TableSheetRepo", "Using existing empty row: $emptyRowId")
            emptyRowId
        } else {
            // No empty row found, add new one at top
            android.util.Log.d("TableSheetRepo", "No empty row found, creating new row at top")
            addRowAtTop(tableId)
        }
    }
    
    suspend fun deleteRow(rowId: Long, tableId: Long) {
        runInRepositoryTransaction {
            rowDao.deleteRowById(rowId)
            cellSearchIndexDao?.deleteByRowId(rowId)
            val count = rowDao.getRowCount(tableId)
            tableDao.updateRowCount(tableId, count)
        }
        rebuildAllFormulaDependencies(tableId)
    }
    
    // Cell operations
    fun getCellsByTableId(tableId: Long): Flow<List<CellModel>> =
        cellDao.getCellsByTableIdFlow(tableId)

    suspend fun getCellsByRowIds(rowIds: List<Long>): List<CellModel> =
        cellDao.getCellsByRowIds(rowIds)
    
    suspend fun updateCellValue(rowId: Long, columnId: Long, value: String) {
        val row = rowDao.getRowById(rowId) ?: return
        val tableId = row.tableId
        val transactionId = createTransaction(
            tableId = tableId,
            action = "UPDATE_CELL",
            metadata = "rowId=$rowId,columnId=$columnId"
        )

        try {
            runInRepositoryTransaction {
                applyCellValueChange(
                    rowId = rowId,
                    columnId = columnId,
                    value = value,
                    transactionId = transactionId,
                    source = SOURCE_DIRECT_EDIT
                )
            }
            completeTransaction(transactionId, SheetTransactionModel.STATUS_COMMITTED)
        } catch (error: Exception) {
            completeTransaction(transactionId, SheetTransactionModel.STATUS_FAILED)
            throw error
        }
    }

    suspend fun updateRowValues(
        rowId: Long,
        valuesByColumnId: Map<Long, String>,
        source: String = SOURCE_DIRECT_EDIT
    ) {
        if (valuesByColumnId.isEmpty()) return
        val row = rowDao.getRowById(rowId) ?: return
        val tableId = row.tableId
        val metadata =
            valuesByColumnId.keys
                .sorted()
                .joinToString(prefix = "rowId=$rowId,columns=[", postfix = "]")

        val transactionId = createTransaction(
            tableId = tableId,
            action = "UPDATE_ROW_VALUES",
            metadata = metadata
        )

        try {
            runInRepositoryTransaction {
                valuesByColumnId.toSortedMap().forEach { (columnId, value) ->
                    applyCellValueChange(
                        rowId = rowId,
                        columnId = columnId,
                        value = value,
                        transactionId = transactionId,
                        source = source
                    )
                }
            }
            completeTransaction(transactionId, SheetTransactionModel.STATUS_COMMITTED)
        } catch (error: Exception) {
            completeTransaction(transactionId, SheetTransactionModel.STATUS_FAILED)
            throw error
        }
    }

    private suspend fun applyCellValueChange(
        rowId: Long,
        columnId: Long,
        value: String,
        transactionId: String,
        source: String
    ) {
        val row = rowDao.getRowById(rowId) ?: return
        val tableId = row.tableId
        val now = System.currentTimeMillis()
        val normalizedInput = value
        val isFormula = normalizedInput.trim().startsWith("=")

        val existingCell = cellDao.getCell(rowId, columnId)
        val previousValue = existingCell?.value.orEmpty()
        val persistedCell =
            if (existingCell != null) {
                if (isFormula) {
                    val updated =
                        existingCell.copy(
                            formula = normalizedInput.trim(),
                            updatedAt = now
                        )
                    cellDao.updateCell(updated)
                    updated
                } else {
                    val inferred = inferTypedValues(normalizedInput)
                    val updated =
                        existingCell.copy(
                            value = normalizedInput,
                            formula = null,
                            cachedValue = null,
                            numberValue = inferred.numberValue,
                            booleanValue = inferred.booleanValue,
                            dateValue = inferred.dateValue,
                            updatedAt = now
                        )
                    cellDao.updateCell(updated)
                    formulaDependencyDao.deleteDependenciesForCell(updated.id)
                    updated
                }
            } else {
                if (isFormula) {
                    val cellId =
                        cellDao.insertCell(
                            CellModel(
                                rowId = rowId,
                                columnId = columnId,
                                value = "",
                                formula = normalizedInput.trim(),
                                cachedValue = "",
                                updatedAt = now
                            )
                        )
                    cellDao.getCellById(cellId)
                        ?: CellModel(
                            id = cellId,
                            rowId = rowId,
                            columnId = columnId,
                            value = "",
                            formula = normalizedInput.trim(),
                            cachedValue = "",
                            updatedAt = now
                        )
                } else {
                    val inferred = inferTypedValues(normalizedInput)
                    val cellId =
                        cellDao.insertCell(
                            CellModel(
                                rowId = rowId,
                                columnId = columnId,
                                value = normalizedInput,
                                numberValue = inferred.numberValue,
                                booleanValue = inferred.booleanValue,
                                dateValue = inferred.dateValue,
                                updatedAt = now
                            )
                        )
                    cellDao.getCellById(cellId)
                        ?: CellModel(
                            id = cellId,
                            rowId = rowId,
                            columnId = columnId,
                            value = normalizedInput,
                            numberValue = inferred.numberValue,
                            booleanValue = inferred.booleanValue,
                            dateValue = inferred.dateValue,
                            updatedAt = now
                        )
                }
            }

        recalculateIncremental(
            tableId = tableId,
            changedCoordinate = rowId to columnId,
            editedFormulaCellId = if (isFormula) persistedCell.id else null,
            transactionId = transactionId
        )
        val latestCell = cellDao.getCell(rowId, columnId)
        val latestValue = latestCell?.value.orEmpty()
        upsertSearchIndex(tableId, rowId, columnId, latestValue)
        if (previousValue != latestValue) {
            recordRowVersion(
                transactionId = transactionId,
                tableId = tableId,
                rowId = rowId,
                columnId = columnId,
                previousValue = previousValue,
                newValue = latestValue,
                source = source
            )
        }
        refreshTableTimestamp(tableId)
    }
    
    suspend fun getCell(rowId: Long, columnId: Long): CellModel? =
        cellDao.getCell(rowId, columnId)
    
    // Tags and Favorite operations
    suspend fun updateTableTags(tableId: Long, tags: String?) = 
        tableDao.updateTags(tableId, tags)
    
    suspend fun updateTableFavorite(tableId: Long, isFavorite: Boolean) = 
        tableDao.updateFavorite(tableId, isFavorite)
    
    suspend fun renameTable(tableId: Long, name: String) = 
        tableDao.updateName(tableId, name)
    
    fun getFavoriteTables(): Flow<List<TableModel>> = 
        tableDao.getFavoriteTables()
    
    // Import table from file data
    suspend fun createTableFromImport(
        name: String,
        description: String,
        headers: List<String>,
        rows: List<List<String>>,
        folderId: Long? = null
    ): Long {
        val maxColumnsInRows = rows.maxOfOrNull { it.size } ?: 0
        val totalColumns = maxOf(headers.size, maxColumnsInRows).coerceAtLeast(1)
        val safeHeaders =
            (0 until totalColumns).map { index ->
                headers.getOrNull(index)?.trim().takeIf { !it.isNullOrBlank() } ?: "Column ${index + 1}"
            }
        val uniqueHeaders = ensureUniqueNames(safeHeaders)
        val inferredColumns = inferImportedColumnDefinitions(uniqueHeaders, rows)

        var tableId = -1L
        runInRepositoryTransaction {
            val table = TableModel(
                name = name,
                description = description,
                columnCount = uniqueHeaders.size,
                rowCount = rows.size,
                folderId = folderId
            )
            tableId = tableDao.insertTable(table)

            val columns = mutableListOf<ColumnModel>()
            uniqueHeaders.forEachIndexed { index, header ->
                val inferred = inferredColumns.getOrNull(index) ?: InferredColumnDefinition()
                val column = ColumnModel(
                    tableId = tableId,
                    name = header.ifBlank { "Column ${index + 1}" },
                    type = inferred.type,
                    orderIndex = index,
                    selectOptions = inferred.selectOptionsJson
                )
                columns += column
            }

            val columnIds = mutableListOf<Long>()
            columns.forEach { column ->
                columnIds += columnDao.insertColumn(column)
            }
            var hasFormulaCells = false

            rows.forEachIndexed { rowIndex, rowData ->
                val rowId = rowDao.insertRow(RowModel(tableId = tableId, orderIndex = rowIndex))
                val cells =
                    columnIds.mapIndexed { colIndex, columnId ->
                        val rawValue = rowData.getOrNull(colIndex).orEmpty()
                        val trimmed = rawValue.trim()
                        val isFormula = trimmed.startsWith("=")
                        if (isFormula) {
                            val cell =
                                CellModel(
                                    rowId = rowId,
                                    columnId = columnId,
                                    value = "",
                                    formula = trimmed,
                                    cachedValue = "",
                                    updatedAt = System.currentTimeMillis()
                                )
                            hasFormulaCells = true
                            cell
                        } else {
                            val inferredTyped = inferTypedValues(rawValue)
                            CellModel(
                                rowId = rowId,
                                columnId = columnId,
                                value = rawValue,
                                numberValue = inferredTyped.numberValue,
                                booleanValue = inferredTyped.booleanValue,
                                dateValue = inferredTyped.dateValue,
                                updatedAt = System.currentTimeMillis()
                            )
                        }
                    }

                if (cells.isNotEmpty()) {
                    cellDao.insertCells(cells)
                    cells.forEach { cell ->
                        upsertSearchIndex(
                            tableId = tableId,
                            rowId = rowId,
                            columnId = cell.columnId,
                            value = cell.value
                        )
                    }
                }
            }

            if (hasFormulaCells) {
                rebuildAllFormulaDependencies(tableId)
            }
        }

        return tableId
    }
    
    // Folder operations
    fun getAllFolders(): Flow<List<FolderModel>> = folderDao.getAllFolders()
    
    suspend fun getFolderById(id: Long): FolderModel? = folderDao.getFolderById(id)
    
    suspend fun createFolder(name: String): Long {
        val folder = FolderModel(name = name)
        return folderDao.insertFolder(folder)
    }

    suspend fun createFolderIfNotExists(name: String): Long {
        val existing = folderDao.getFolderByName(name)
        if (existing != null) {
            return existing.id
        }
        val folder = FolderModel(name = name, colorHex = "#10B981") // Green for AI
        return folderDao.insertFolder(folder)
    }
    
    suspend fun updateFolder(folder: FolderModel) = folderDao.updateFolder(folder)
    
    suspend fun deleteFolder(folder: FolderModel) {
        // Move all tables in this folder to root (folderId = null)
        tableDao.moveTablesFromFolder(folder.id)
        folderDao.deleteFolder(folder)
    }
    
    suspend fun getFolderTableCounts(): Map<Long, Int> {
        val folders = folderDao.getAllFolders()
        val counts = mutableMapOf<Long, Int>()
        folders.collect { folderList ->
            folderList.forEach { folder ->
                counts[folder.id] = folderDao.getTableCountInFolder(folder.id)
            }
        }
        return counts
    }
    
    suspend fun moveTableToFolder(tableId: Long, folderId: Long?) {
        tableDao.updateTableFolder(tableId, folderId)
    }

    suspend fun setFrozenColumnCount(tableId: Long, count: Int) {
        tableDao.updateFrozenColumnCount(tableId, count.coerceAtLeast(0))
    }

    suspend fun searchRowIdsIndexed(
        tableIds: List<Long>,
        query: String,
        limit: Int = 200
    ): List<Long> {
        if (cellSearchIndexDao == null) return emptyList()
        if (tableIds.isEmpty()) return emptyList()
        val normalized = normalizeSearchValue(query)
        if (normalized.isBlank()) return emptyList()

        val prefixMatches = cellSearchIndexDao.searchRowIdsByPrefix(tableIds, normalized, limit)
        if (prefixMatches.size >= limit) return prefixMatches

        val containsMatches = cellSearchIndexDao.searchRowIdsByContains(tableIds, normalized, limit)
        return (prefixMatches + containsMatches).distinct().take(limit)
    }

    suspend fun queryRowIdsByFilterPlan(
        tableId: Long,
        exactFiltersByColumnId: Map<Long, String>,
        containsFiltersByColumnId: Map<Long, String>
    ): Set<Long>? {
        if (exactFiltersByColumnId.isEmpty() && containsFiltersByColumnId.isEmpty()) {
            return null
        }

        var candidate: Set<Long>? = null

        fun intersect(nextRows: List<Long>) {
            val nextSet = nextRows.toSet()
            candidate =
                if (candidate == null) {
                    nextSet
                } else {
                    candidate!!.intersect(nextSet)
                }
        }

        exactFiltersByColumnId.forEach { (columnId, rawValue) ->
            val normalized = normalizeSearchValue(rawValue)
            val matchedRows =
                if (normalized.isBlank()) {
                    emptyList()
                } else {
                    val indexed =
                        cellSearchIndexDao?.searchRowIdsByColumnExact(
                            tableId = tableId,
                            columnId = columnId,
                            normalizedValue = normalized,
                            limit = MAX_FILTER_ROW_SCAN
                        ).orEmpty()
                    val direct = cellDao.findRowIdsByColumnExact(tableId, columnId, rawValue)
                    (indexed + direct).distinct()
                }
            intersect(matchedRows)
            if (candidate.isNullOrEmpty()) return emptySet()
        }

        containsFiltersByColumnId.forEach { (columnId, tokenRaw) ->
            val token = normalizeSearchValue(tokenRaw)
            val matchedRows =
                if (token.isBlank()) {
                    emptyList()
                } else {
                    val indexed =
                        cellSearchIndexDao?.searchRowIdsByColumnContains(
                            tableId = tableId,
                            columnId = columnId,
                            token = token,
                            limit = MAX_FILTER_ROW_SCAN
                        ).orEmpty()
                    val direct = cellDao.findRowIdsByColumnContains(tableId, columnId, tokenRaw)
                    (indexed + direct).distinct()
                }
            intersect(matchedRows)
            if (candidate.isNullOrEmpty()) return emptySet()
        }

        return candidate ?: emptySet()
    }

    fun getFilterViewsByTableId(tableId: Long): Flow<List<FilterViewModel>> {
        return filterViewDao?.getFilterViewsByTableId(tableId) ?: flowOf(emptyList())
    }

    suspend fun saveFilterView(
        tableId: Long,
        name: String,
        filtersJson: String,
        sortColumnId: Long? = null,
        sortDirection: String = "ASC",
        isDefault: Boolean = false
    ): Long {
        val dao = filterViewDao ?: return -1
        if (isDefault) {
            dao.clearDefaultForTable(tableId)
        }
        return dao.insertFilterView(
            FilterViewModel(
                tableId = tableId,
                name = name,
                filtersJson = filtersJson,
                sortColumnId = sortColumnId,
                sortDirection = sortDirection.uppercase(Locale.ROOT),
                isDefault = isDefault
            )
        )
    }

    suspend fun deleteFilterView(viewId: Long) {
        filterViewDao?.deleteFilterView(viewId)
    }

    fun getConditionalFormatRules(tableId: Long): Flow<List<ConditionalFormatRuleModel>> {
        return conditionalFormatRuleDao?.getRulesByTableId(tableId) ?: flowOf(emptyList())
    }

    suspend fun saveConditionalFormatRule(
        tableId: Long,
        columnId: Long,
        ruleType: String,
        criteria: String,
        backgroundColor: String?,
        textColor: String?,
        priority: Int = 0,
        enabled: Boolean = true
    ): Long {
        val dao = conditionalFormatRuleDao ?: return -1
        return dao.insertRule(
            ConditionalFormatRuleModel(
                tableId = tableId,
                columnId = columnId,
                ruleType = ruleType,
                criteria = criteria,
                backgroundColor = backgroundColor,
                textColor = textColor,
                priority = priority,
                enabled = enabled
            )
        )
    }

    suspend fun deleteConditionalFormatRule(ruleId: Long) {
        conditionalFormatRuleDao?.deleteRule(ruleId)
    }

    private suspend fun recalculateIncremental(
        tableId: Long,
        changedCoordinate: Pair<Long, Long>,
        editedFormulaCellId: Long?,
        transactionId: String
    ) {
        val snapshot = loadSheetSnapshot(tableId)
        if (snapshot.rows.isEmpty() || snapshot.columns.isEmpty()) return

        val formulaCells = snapshot.cellsById.values.filter { !it.formula.isNullOrBlank() }
        if (formulaCells.isNotEmpty() && formulaDependencyDao.getDependencyCountForTable(tableId) == 0) {
            formulaCells.forEach { formulaCell ->
                recalculateFormulaCell(tableId, formulaCell.id, snapshot, transactionId)
            }
        }

        val queue = ArrayDeque<Pair<Long, Long>>()
        val queued = mutableSetOf<Pair<Long, Long>>()
        fun enqueue(coordinate: Pair<Long, Long>) {
            if (queued.add(coordinate)) {
                queue.addLast(coordinate)
            }
        }

        enqueue(changedCoordinate)
        if (editedFormulaCellId != null) {
            val updatedCoordinate = recalculateFormulaCell(tableId, editedFormulaCellId, snapshot, transactionId)
            if (updatedCoordinate != null) {
                enqueue(updatedCoordinate)
            }
        }

        val recalcCountByCellId = mutableMapOf<Long, Int>()
        var loopGuard = 0

        while (queue.isNotEmpty() && loopGuard < MAX_RECALC_STEPS) {
            loopGuard += 1
            val source = queue.removeFirst()
            val dependentCellIds =
                formulaDependencyDao.getDependentCellIdsForSource(
                    tableId = tableId,
                    sourceRowId = source.first,
                    sourceColumnId = source.second
                )

            dependentCellIds.forEach { dependentCellId ->
                val count = recalcCountByCellId[dependentCellId] ?: 0
                if (count >= MAX_RECALC_PER_CELL) return@forEach
                recalcCountByCellId[dependentCellId] = count + 1

                val dependentCoordinate = recalculateFormulaCell(tableId, dependentCellId, snapshot, transactionId)
                if (dependentCoordinate != null) {
                    enqueue(dependentCoordinate)
                }
            }
        }
    }

    private suspend fun rebuildAllFormulaDependencies(tableId: Long) {
        formulaDependencyDao.deleteDependenciesForTable(tableId)
        val snapshot = loadSheetSnapshot(tableId)
        val transactionId = createTransaction(tableId, "REBUILD_FORMULAS", null)
        try {
            val formulaCellIds =
                snapshot.cellsById.values
                    .filter { !it.formula.isNullOrBlank() && it.formula.trim().startsWith("=") }
                    .map { it.id }
            formulaCellIds.forEach { formulaCellId ->
                recalculateFormulaCell(tableId, formulaCellId, snapshot, transactionId)
            }
            completeTransaction(transactionId, SheetTransactionModel.STATUS_COMMITTED)
        } catch (error: Exception) {
            completeTransaction(transactionId, SheetTransactionModel.STATUS_FAILED)
            throw error
        }
    }

    private suspend fun recalculateFormulaCell(
        tableId: Long,
        formulaCellId: Long,
        snapshot: SheetSnapshot,
        transactionId: String
    ): Pair<Long, Long>? {
        val existing = snapshot.cellsById[formulaCellId] ?: cellDao.getCellById(formulaCellId) ?: return null
        val formula = existing.formula?.trim().orEmpty()
        if (!formula.startsWith("=")) {
            formulaDependencyDao.deleteDependenciesForCell(formulaCellId)
            return existing.rowId to existing.columnId
        }

        val engine = FormulaEngine(snapshot.rows, snapshot.columns, snapshot.cellsByCoordinate)
        val evaluated = engine.evaluate(formula)
        val hasCircularReference =
            hasCircularDependency(
                tableId = tableId,
                formulaCellId = formulaCellId,
                proposedDependencies = evaluated.dependencies,
                snapshot = snapshot
            )

        val computedValue = if (hasCircularReference) "#CIRCULAR" else evaluated.value
        val inferred = inferTypedValues(computedValue)
        val updated =
            existing.copy(
                formula = formula,
                value = computedValue,
                cachedValue = computedValue,
                numberValue = inferred.numberValue,
                booleanValue = inferred.booleanValue,
                dateValue = inferred.dateValue,
                updatedAt = System.currentTimeMillis()
            )

        val previousValue = existing.value
        if (updated != existing) {
            cellDao.updateCell(updated)
            if (previousValue != updated.value) {
                recordRowVersion(
                    transactionId = transactionId,
                    tableId = tableId,
                    rowId = updated.rowId,
                    columnId = updated.columnId,
                    previousValue = previousValue,
                    newValue = updated.value,
                    source = SOURCE_FORMULA_RECALC
                )
            }
        }
        snapshot.upsert(updated)
        upsertSearchIndex(tableId, updated.rowId, updated.columnId, updated.value)

        formulaDependencyDao.deleteDependenciesForCell(formulaCellId)
        if (!hasCircularReference) {
            val dependencies =
                evaluated.dependencies
                    .distinctBy { it.rowId to it.columnId }
                    .map { dependency ->
                        FormulaDependencyModel(
                            tableId = tableId,
                            dependentCellId = formulaCellId,
                            sourceRowId = dependency.rowId,
                            sourceColumnId = dependency.columnId,
                            sourceAddress = dependency.address
                        )
                    }
            if (dependencies.isNotEmpty()) {
                formulaDependencyDao.insertDependencies(dependencies)
            }
        }

        return updated.rowId to updated.columnId
    }

    private suspend fun hasCircularDependency(
        tableId: Long,
        formulaCellId: Long,
        proposedDependencies: List<FormulaDependencyRef>,
        snapshot: SheetSnapshot
    ): Boolean {
        val dependencies = formulaDependencyDao.getDependenciesForTable(tableId)
        val graph = mutableMapOf<Long, MutableSet<Long>>()

        dependencies.forEach { dependency ->
            val sourceCell =
                snapshot.cellsByCoordinate[dependency.sourceRowId to dependency.sourceColumnId]
                    ?: return@forEach
            if (sourceCell.formula.isNullOrBlank()) return@forEach
            graph.getOrPut(dependency.dependentCellId) { mutableSetOf() }.add(sourceCell.id)
        }

        val proposedSourceFormulaCells =
            proposedDependencies.mapNotNull { ref ->
                snapshot.cellsByCoordinate[ref.rowId to ref.columnId]
                    ?.takeIf { !it.formula.isNullOrBlank() }
                    ?.id
            }.toMutableSet()
        graph[formulaCellId] = proposedSourceFormulaCells

        val visited = mutableSetOf<Long>()
        val visiting = mutableSetOf<Long>()

        fun dfs(node: Long): Boolean {
            if (!visiting.add(node)) return true
            if (!visited.add(node)) {
                visiting.remove(node)
                return false
            }

            graph[node].orEmpty().forEach { next ->
                if (dfs(next)) return true
            }

            visiting.remove(node)
            return false
        }

        return dfs(formulaCellId)
    }

    private suspend fun loadSheetSnapshot(tableId: Long): SheetSnapshot {
        val rows = rowDao.getRowsByTableIdSync(tableId).sortedBy { it.orderIndex }
        val columns = columnDao.getColumnsByTableIdSync(tableId).sortedBy { it.orderIndex }
        if (rows.isEmpty() || columns.isEmpty()) {
            return SheetSnapshot(
                rows = rows,
                columns = columns,
                cellsByCoordinate = mutableMapOf(),
                cellsById = mutableMapOf()
            )
        }

        val cells = cellDao.getCellsByRowIds(rows.map { it.id })
        return SheetSnapshot(
            rows = rows,
            columns = columns,
            cellsByCoordinate = cells.associateBy { it.rowId to it.columnId }.toMutableMap(),
            cellsById = cells.associateBy { it.id }.toMutableMap()
        )
    }

    private fun inferTypedValues(value: String): TypedValues {
        val trimmed = value.trim()
        if (trimmed.isBlank()) return TypedValues(null, null, null)

        val numberValue =
            trimmed
                .replace(",", "")
                .replace(Regex("[^0-9+\\-.]"), "")
                .toDoubleOrNull()

        val booleanValue =
            when (trimmed.lowercase(Locale.ROOT)) {
                "true", "yes", "y", "1" -> true
                "false", "no", "n", "0" -> false
                else -> null
            }

        val dateValue = parseDateToEpoch(trimmed)

        return TypedValues(
            numberValue = numberValue,
            booleanValue = booleanValue,
            dateValue = dateValue
        )
    }

    private fun parseDateToEpoch(raw: String): Long? {
        raw.trim().toLongOrNull()?.let { epoch ->
            if (epoch > 0L) return epoch
        }
        val patterns =
            listOf(
                "yyyy-MM-dd",
                "dd/MM/yyyy",
                "MM/dd/yyyy",
                "yyyy-MM-dd HH:mm",
                "yyyy-MM-dd HH:mm:ss",
                "dd/MM/yyyy HH:mm",
                "MM/dd/yyyy HH:mm",
                "dd/MM/yyyy HH:mm:ss",
                "MM/dd/yyyy HH:mm:ss",
                "yyyy/MM/dd",
                "yyyyMMdd",
                "yyyy-MM-dd'T'HH:mm",
                "yyyy-MM-dd'T'HH:mm:ss"
            )
        patterns.forEach { pattern ->
            runCatching {
                SimpleDateFormat(pattern, Locale.getDefault()).apply {
                    isLenient = false
                }.parse(raw)
            }.getOrNull()?.let { parsed ->
                return parsed.time
            }
        }
        return null
    }

    private suspend fun runInRepositoryTransaction(block: suspend () -> Unit) {
        val db = database
        if (db != null) {
            db.withTransaction {
                block()
            }
        } else {
            block()
        }
    }

    private suspend fun createTransaction(
        tableId: Long,
        action: String,
        metadata: String?
    ): String {
        val transactionId = "tx_${UUID.randomUUID()}"
        sheetTransactionDao?.insertTransaction(
            SheetTransactionModel(
                transactionId = transactionId,
                tableId = tableId,
                action = action,
                status = SheetTransactionModel.STATUS_RUNNING,
                metadata = metadata
            )
        )
        return transactionId
    }

    private suspend fun completeTransaction(
        transactionId: String,
        status: String
    ) {
        sheetTransactionDao?.markTransactionStatus(
            transactionId = transactionId,
            status = status,
            completedAt = System.currentTimeMillis()
        )
    }

    private suspend fun recordRowVersion(
        transactionId: String,
        tableId: Long,
        rowId: Long,
        columnId: Long,
        previousValue: String,
        newValue: String,
        source: String
    ) {
        rowVersionDao?.insertVersion(
            RowVersionModel(
                transactionId = transactionId,
                tableId = tableId,
                rowId = rowId,
                columnId = columnId,
                previousValue = previousValue,
                newValue = newValue,
                source = source
            )
        )
    }

    private suspend fun upsertSearchIndex(
        tableId: Long,
        rowId: Long,
        columnId: Long,
        value: String
    ) {
        val dao = cellSearchIndexDao ?: return
        val normalized = normalizeSearchValue(value)
        if (normalized.isBlank()) {
            dao.deleteByCell(rowId, columnId)
            return
        }
        dao.upsert(
            CellSearchIndexModel(
                tableId = tableId,
                rowId = rowId,
                columnId = columnId,
                normalizedValue = normalized,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    private fun normalizeSearchValue(raw: String): String {
        return raw.trim()
            .lowercase(Locale.ROOT)
            .replace(Regex("\\s+"), " ")
    }

    private fun inferImportedColumnDefinitions(
        headers: List<String>,
        rows: List<List<String>>
    ): List<InferredColumnDefinition> {
        val maxRowsToInspect = rows.take(500)
        return headers.indices.map { index ->
            val header = headers[index].trim()
            val headerLower = header.lowercase(Locale.ROOT)
            val values =
                maxRowsToInspect.mapNotNull { row ->
                    row.getOrNull(index)?.trim()?.takeIf { it.isNotBlank() }
                }
            inferSingleColumnDefinition(headerLower, values)
        }
    }

    private fun inferSingleColumnDefinition(
        headerLower: String,
        values: List<String>
    ): InferredColumnDefinition {
        if (values.isEmpty()) {
            return InferredColumnDefinition(type = ColumnType.STRING)
        }

        val uniqueValues = LinkedHashSet<String>()
        values.forEach { uniqueValues += it }

        val isPhoneHeader =
            headerLower.contains("phone") ||
                headerLower.contains("mobile") ||
                headerLower.contains("whatsapp") ||
                headerLower.contains("contact")
        if (isPhoneHeader) {
            return InferredColumnDefinition(type = ColumnType.PHONE)
        }

        if (headerLower.contains("email")) {
            return InferredColumnDefinition(type = ColumnType.EMAIL)
        }

        if (headerLower.contains("datetime") || headerLower.contains("timestamp")) {
            return InferredColumnDefinition(type = ColumnType.DATETIME)
        }

        if (headerLower.contains("date")) {
            return InferredColumnDefinition(type = ColumnType.DATE)
        }

        if (headerLower.contains("time")) {
            return InferredColumnDefinition(type = ColumnType.TIME)
        }

        if (
            headerLower.contains("url") ||
                headerLower.contains("link") ||
                headerLower.contains("website")
        ) {
            return InferredColumnDefinition(type = ColumnType.URL)
        }

        if (
            headerLower.contains("location") ||
                headerLower.contains("latitude") ||
                headerLower.contains("longitude") ||
                headerLower.contains("map")
        ) {
            return InferredColumnDefinition(type = ColumnType.MAP)
        }

        if (
            headerLower.contains("file") ||
                headerLower.contains("document") ||
                headerLower.contains("attachment")
        ) {
            return InferredColumnDefinition(type = ColumnType.FILE)
        }

        if (headerLower.contains("json")) {
            return InferredColumnDefinition(type = ColumnType.JSON)
        }

        if (headerLower.contains("formula")) {
            return InferredColumnDefinition(type = ColumnType.FORMULA)
        }

        if (
            headerLower.contains("note") ||
                headerLower.contains("description") ||
                headerLower.contains("comment") ||
                headerLower.contains("message")
        ) {
            return InferredColumnDefinition(type = ColumnType.MULTILINE)
        }

        val boolMatches = values.count { parseBooleanToken(it) != null }
        if (boolMatches == values.size) {
            return InferredColumnDefinition(type = ColumnType.CHECKBOX)
        }

        val urlMatches =
            values.count { token ->
                token.startsWith("http://", ignoreCase = true) ||
                    token.startsWith("https://", ignoreCase = true) ||
                    token.startsWith("www.", ignoreCase = true)
            }
        if (urlMatches >= minOf(values.size, 6) && urlMatches * 10 >= values.size * 8) {
            return InferredColumnDefinition(type = ColumnType.URL)
        }

        val dateMatches = values.count { parseDateToEpoch(it) != null }
        if (dateMatches >= minOf(values.size, 8) && dateMatches * 10 >= values.size * 8) {
            val hasTimeTokens =
                values.any { token ->
                    token.contains(":") ||
                        token.contains("T")
                }
            return InferredColumnDefinition(type = if (hasTimeTokens) ColumnType.DATETIME else ColumnType.DATE)
        }

        val numericMatches = values.count { parseNumberToken(it) != null }
        if (numericMatches >= minOf(values.size, 8) && numericMatches * 10 >= values.size * 8) {
            val isAmountHeader =
                headerLower.contains("amount") ||
                    headerLower.contains("price") ||
                    headerLower.contains("cost") ||
                    headerLower.contains("total") ||
                    headerLower.contains("balance") ||
                    headerLower.contains("salary")
            val looksDecimal = values.any { token -> token.trim().contains(".") }
            return InferredColumnDefinition(
                type =
                    when {
                        isAmountHeader -> ColumnType.AMOUNT
                        looksDecimal -> ColumnType.DECIMAL
                        else -> ColumnType.INTEGER
                    }
            )
        }

        val shouldSuggestSelect =
            uniqueValues.size in 2..12 &&
                values.size >= uniqueValues.size &&
                (
                    headerLower.contains("status") ||
                        headerLower.contains("stage") ||
                        headerLower.contains("type") ||
                        headerLower.contains("category") ||
                        headerLower.contains("priority") ||
                        values.size <= 200
                )
        if (shouldSuggestSelect) {
            val shouldSuggestMultiSelect =
                values.any { token ->
                    token.contains(",") || token.contains("|") || token.contains(";")
                }
            return InferredColumnDefinition(
                type = if (shouldSuggestMultiSelect) ColumnType.MULTI_SELECT else ColumnType.SELECT,
                selectOptionsJson = JSONArray(uniqueValues.toList()).toString()
            )
        }

        return InferredColumnDefinition(type = ColumnType.STRING)
    }

    private fun parseNumberToken(raw: String): Double? {
        return raw
            .replace(",", "")
            .replace(Regex("[^0-9+\\-.]"), "")
            .trim()
            .toDoubleOrNull()
    }

    private fun parseBooleanToken(raw: String): Boolean? {
        return when (raw.trim().lowercase(Locale.ROOT)) {
            "true", "yes", "y", "1" -> true
            "false", "no", "n", "0" -> false
            else -> null
        }
    }

    private suspend fun resolveUniqueColumnName(
        tableId: Long,
        rawName: String,
        excludeColumnId: Long? = null
    ): String {
        val baseName = rawName.trim().ifBlank { "Column" }
        val existingNormalized =
            columnDao.getColumnsByTableIdSync(tableId)
                .asSequence()
                .filter { it.id != excludeColumnId }
                .map { normalizeColumnNameForMatch(it.name) }
                .toSet()

        if (normalizeColumnNameForMatch(baseName) !in existingNormalized) {
            return baseName
        }

        var suffix = 2
        while (true) {
            val candidate = "$baseName ($suffix)"
            if (normalizeColumnNameForMatch(candidate) !in existingNormalized) {
                return candidate
            }
            suffix += 1
        }
    }

    private fun normalizeColumnNameForMatch(raw: String): String {
        return raw.trim().lowercase(Locale.ROOT).replace(Regex("\\s+"), " ")
    }

    private fun ensureUniqueNames(names: List<String>): List<String> {
        val seen = mutableSetOf<String>()
        val result = mutableListOf<String>()
        names.forEach { raw ->
            val base = raw.trim().ifBlank { "Column" }
            var candidate = base
            var suffix = 2
            while (!seen.add(normalizeColumnNameForMatch(candidate))) {
                candidate = "$base ($suffix)"
                suffix += 1
            }
            result += candidate
        }
        return result
    }

    private data class TypedValues(
        val numberValue: Double?,
        val booleanValue: Boolean?,
        val dateValue: Long?
    )

    private data class InferredColumnDefinition(
        val type: String = ColumnType.STRING,
        val selectOptionsJson: String? = null
    )

    private data class SheetSnapshot(
        val rows: List<RowModel>,
        val columns: List<ColumnModel>,
        val cellsByCoordinate: MutableMap<Pair<Long, Long>, CellModel>,
        val cellsById: MutableMap<Long, CellModel>
    ) {
        fun upsert(cell: CellModel) {
            cellsByCoordinate[cell.rowId to cell.columnId] = cell
            cellsById[cell.id] = cell
        }
    }

    private companion object {
        const val MAX_RECALC_STEPS = 5000
        const val MAX_RECALC_PER_CELL = 6
        const val MAX_FILTER_ROW_SCAN = 100_000
        const val SOURCE_DIRECT_EDIT = "DIRECT_EDIT"
        const val SOURCE_FORMULA_RECALC = "FORMULA_RECALC"
    }
}
