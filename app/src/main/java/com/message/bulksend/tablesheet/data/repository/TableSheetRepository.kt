package com.message.bulksend.tablesheet.data.repository

import com.message.bulksend.tablesheet.data.dao.*
import com.message.bulksend.tablesheet.data.models.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect

class TableSheetRepository(
    private val tableDao: TableDao,
    private val columnDao: ColumnDao,
    private val rowDao: RowDao,
    private val cellDao: CellDao,
    private val folderDao: FolderDao
) {
    // Table operations
    fun getAllTables(): Flow<List<TableModel>> = tableDao.getAllTables()
    
    suspend fun getTableById(tableId: Long): TableModel? = tableDao.getTableById(tableId)
    
    suspend fun createTable(name: String, description: String = "", tags: String? = null, folderId: Long? = null): Long {
        val table = TableModel(
            name = name, 
            description = description, 
            tags = if (tags.isNullOrBlank()) null else tags,
            columnCount = 4, 
            rowCount = 20,
            folderId = folderId
        )
        val tableId = tableDao.insertTable(table)
        
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
            
            // Create cells for each column
            val cells = columnIds.map { columnId ->
                CellModel(rowId = rowId, columnId = columnId, value = "")
            }
            cellDao.insertCells(cells)
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
    
    suspend fun deleteTable(tableId: Long) = tableDao.deleteTableById(tableId)
    
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
        val count = columnDao.getColumnCount(tableId)
        val column = ColumnModel(
            tableId = tableId,
            name = name,
            type = type,
            orderIndex = count,
            width = width,
            selectOptions = selectOptions
        )
        val columnId = columnDao.insertColumn(column)
        tableDao.updateColumnCount(tableId, count + 1)
        
        // Add empty cells for existing rows
        val rows = rowDao.getRowsByTableIdSync(tableId)
        val cells = rows.map { row ->
            CellModel(rowId = row.id, columnId = columnId, value = "")
        }
        if (cells.isNotEmpty()) {
            cellDao.insertCells(cells)
        }
        
        return columnId
    }

    suspend fun updateColumn(column: ColumnModel) = columnDao.updateColumn(column)
    
    suspend fun updateColumn(columnId: Long, name: String, type: String, width: Float, selectOptions: String?) {
        columnDao.updateColumnProperties(columnId, name, type, width, selectOptions)
    }
    
    suspend fun deleteColumn(columnId: Long, tableId: Long) {
        columnDao.deleteColumnById(columnId)
        val count = columnDao.getColumnCount(tableId)
        tableDao.updateColumnCount(tableId, count)
    }
    
    suspend fun updateColumnsOrder(columns: List<ColumnModel>) {
        columnDao.updateColumnsOrder(columns)
    }
    
    // Row operations
    fun getRowsByTableId(tableId: Long): Flow<List<RowModel>> = 
        rowDao.getRowsByTableId(tableId)
    
    suspend fun getRowsByTableIdSync(tableId: Long): List<RowModel> =
        rowDao.getRowsByTableIdSync(tableId)
    
    suspend fun addRow(tableId: Long): Long {
        val maxOrder = rowDao.getMaxOrderIndex(tableId) ?: -1
        val row = RowModel(tableId = tableId, orderIndex = maxOrder + 1)
        val rowId = rowDao.insertRow(row)
        
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
        
        return rowId
    }
    
    suspend fun addRowAtTop(tableId: Long): Long {
        // Get all existing rows and increment their orderIndex
        val existingRows = rowDao.getRowsByTableIdSync(tableId)
        existingRows.forEach { row ->
            rowDao.updateRow(row.copy(orderIndex = row.orderIndex + 1))
        }
        
        // Add new row at orderIndex = 0
        val row = RowModel(tableId = tableId, orderIndex = 0)
        val rowId = rowDao.insertRow(row)
        
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
        rowDao.deleteRowById(rowId)
        val count = rowDao.getRowCount(tableId)
        tableDao.updateRowCount(tableId, count)
    }
    
    // Cell operations
    suspend fun getCellsByRowIds(rowIds: List<Long>): List<CellModel> =
        cellDao.getCellsByRowIds(rowIds)
    
    suspend fun updateCellValue(rowId: Long, columnId: Long, value: String) {
        val existingCell = cellDao.getCell(rowId, columnId)
        if (existingCell != null) {
            cellDao.updateCellValue(rowId, columnId, value)
        } else {
            cellDao.insertCell(CellModel(rowId = rowId, columnId = columnId, value = value))
        }
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
        // Create table
        val table = TableModel(
            name = name,
            description = description,
            columnCount = headers.size,
            rowCount = rows.size,
            folderId = folderId
        )
        val tableId = tableDao.insertTable(table)
        
        // Create columns
        val columnIds = mutableListOf<Long>()
        headers.forEachIndexed { index, header ->
            val column = ColumnModel(
                tableId = tableId,
                name = header,
                type = "STRING",
                orderIndex = index
            )
            columnIds.add(columnDao.insertColumn(column))
        }
        
        // Create rows and cells
        rows.forEachIndexed { rowIndex, rowData ->
            val row = RowModel(
                tableId = tableId,
                orderIndex = rowIndex
            )
            val rowId = rowDao.insertRow(row)
            
            // Create cells
            val cells = rowData.mapIndexed { colIndex, value ->
                CellModel(
                    rowId = rowId,
                    columnId = columnIds.getOrNull(colIndex) ?: columnIds.last(),
                    value = value
                )
            }
            if (cells.isNotEmpty()) {
                cellDao.insertCells(cells)
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
}
