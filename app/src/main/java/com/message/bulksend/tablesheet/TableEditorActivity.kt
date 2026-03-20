package com.message.bulksend.tablesheet

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import androidx.lifecycle.lifecycleScope
import com.message.bulksend.tablesheet.data.TableSheetDatabase
import com.message.bulksend.tablesheet.data.models.ColumnModel
import com.message.bulksend.tablesheet.data.models.RowModel
import com.message.bulksend.tablesheet.data.repository.TableSheetRepository
import com.message.bulksend.tablesheet.ui.components.TableEditorScreen
import com.message.bulksend.ui.theme.BulksendTestTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
            database.folderDao()
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
    val columns by repository.getColumnsByTableId(tableId).collectAsState(initial = emptyList())
    val rows by repository.getRowsByTableId(tableId).collectAsState(initial = emptyList())
    
    var cellsMap by remember { mutableStateOf<Map<Pair<Long, Long>, String>>(emptyMap()) }
    var isLoading by remember { mutableStateOf(true) }
    
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current
    
    // Load cells in background using multiple cores
    LaunchedEffect(rows) {
        if (rows.isNotEmpty()) {
            isLoading = true
            // Load cells on IO dispatcher (background thread)
            withContext(Dispatchers.IO) {
                val rowIds = rows.map { it.id }
                val cells = repository.getCellsByRowIds(rowIds)
                val newMap = cells.associate { Pair(it.rowId, it.columnId) to it.value }
                // Update UI on main thread
                withContext(Dispatchers.Main) {
                    cellsMap = newMap
                    isLoading = false
                }
            }
        } else {
            isLoading = false
        }
    }

    TableEditorScreen(
        tableName = tableName,
        columns = columns,
        rows = rows,
        cellsMap = cellsMap,
        onBackPressed = onBackPressed,
        onAddColumn = { name, type, width, selectOptions ->
            // Add column on background thread with all properties
            scope.launch(Dispatchers.IO) {
                repository.addColumnWithOptions(tableId, name, type, width, selectOptions)
            }
        },
        onAddRows = { count ->
            // Add multiple rows on background thread
            scope.launch(Dispatchers.IO) {
                repeat(count) {
                    repository.addRow(tableId)
                }
            }
        },
        onCellValueChange = { rowId, columnId, value ->
            // Update local state immediately for responsiveness (UI thread)
            cellsMap = cellsMap.toMutableMap().apply {
                put(Pair(rowId, columnId), value)
            }
            // Save to database on background thread
            scope.launch(Dispatchers.IO) {
                repository.updateCellValue(rowId, columnId, value)
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
        isLeadFormSheet = false,
        onRefreshSync = null
    )
}
