package com.message.bulksend.tablesheet.ui.components

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.message.bulksend.tablesheet.data.models.ColumnModel
import com.message.bulksend.tablesheet.data.models.FieldType
import com.message.bulksend.tablesheet.data.models.RowModel
import com.message.bulksend.tablesheet.ui.components.cells.DataCell
import com.message.bulksend.tablesheet.ui.components.header.ColumnHeaderCell
import com.message.bulksend.tablesheet.ui.components.header.RowNumberCell
import com.message.bulksend.tablesheet.ui.components.header.TableHeader
import com.message.bulksend.tablesheet.ui.components.header.TableColumnHeaders
import com.message.bulksend.tablesheet.ui.components.dialogs.*
import com.message.bulksend.tablesheet.ui.components.sheets.*
import com.message.bulksend.tablesheet.ui.components.screens.*
import com.message.bulksend.tablesheet.ui.theme.TableTheme
import androidx.compose.ui.graphics.vector.ImageVector

@Composable
fun TableEditorScreen(
    tableName: String,
    columns: List<ColumnModel>,
    rows: List<RowModel>,
    cellsMap: Map<Pair<Long, Long>, String>,
    onBackPressed: () -> Unit,
    onAddColumn: (name: String, type: String, width: Float, selectOptions: String?) -> Unit,
    onAddRows: (Int) -> Unit,
    onCellValueChange: (rowId: Long, columnId: Long, value: String) -> Unit,
    onDeleteRow: (rowId: Long) -> Unit,
    onDeleteColumn: (columnId: Long) -> Unit,
    onUpdateColumn: ((columnId: Long, name: String, type: String, width: Float, selectOptions: String?) -> Unit)? = null,
    onReorderColumns: ((List<ColumnModel>) -> Unit)? = null,
    isLeadFormSheet: Boolean = false,
    onRefreshSync: (() -> Unit)? = null
) {
    var showAddRowsSheet by remember { mutableStateOf(false) }
    var editingColumn by remember { mutableStateOf<ColumnModel?>(null) }
    var showNewColumnEditor by remember { mutableStateOf(false) }
    var showColumnManager by remember { mutableStateOf(false) }
    var showSettingsSheet by remember { mutableStateOf(false) }
    var showSheetInfo by remember { mutableStateOf(false) }
    var showBarcodeScanner by remember { mutableStateOf(false) }
    var scanningCell by remember { mutableStateOf<Pair<Long, Long>?>(null) }
    var autoAddEnabled by remember { mutableStateOf(true) }
    var rowHeight by remember { mutableStateOf(44f) }
    var showRowDataFillDialog by remember { mutableStateOf(false) }
    var fillDataRowId by remember { mutableStateOf<Long?>(null) }
    
    val horizontalScrollState = rememberScrollState()
    val listState = rememberLazyListState()

    Box(modifier = Modifier.fillMaxSize().background(Color.White)) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            TableHeader(
                tableName = tableName,
                rowCount = rows.size,
                columnCount = columns.size,
                onBackPressed = onBackPressed,
                onShowSheetInfo = { showSheetInfo = true },
                isLeadFormSheet = isLeadFormSheet,
                onRefreshSync = onRefreshSync
            )

            // Column Headers Row
            TableColumnHeaders(
                columns = columns,
                horizontalScrollState = horizontalScrollState,
                onDeleteColumn = onDeleteColumn,
                onEditColumn = { column -> editingColumn = column },
                onUpdateColumn = onUpdateColumn,
                onAddColumn = { showNewColumnEditor = true }
            )

            // Data Rows - Add bottom padding for toolbar and make it fill remaining space
            Box(modifier = Modifier.weight(1f)) {
                TableDataRows(
                    rows = rows,
                    columns = columns,
                    cellsMap = cellsMap,
                    rowHeight = rowHeight,
                    horizontalScrollState = horizontalScrollState,
                    listState = listState,
                    onCellValueChange = onCellValueChange,
                    onDeleteRow = onDeleteRow,
                    onFillRowData = { rowId ->
                        fillDataRowId = rowId
                        showRowDataFillDialog = true
                    },
                    onAddRows = { showAddRowsSheet = true }
                )
            }
        }

        // Fixed Bottom Bar - positioned at bottom of screen
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter),
            color = Color(0xFFF5F5F5),
            shadowElevation = 8.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                BottomButton(Icons.Default.ViewColumn, "Column") { showColumnManager = true }
                BottomButton(Icons.Default.Add, "Row") { showAddRowsSheet = true }
                BottomButton(Icons.Default.FilterList, "Filter") { }
                BottomButton(Icons.Default.Settings, "Settings") { showSettingsSheet = true }
                BottomButton(Icons.Default.Share, "Share") { }
            }
        }
    }

    // Handle all dialogs and screens
    TableDialogManager(
        showAddRowsSheet = showAddRowsSheet,
        onDismissAddRowsSheet = { showAddRowsSheet = false },
        onAddRows = { count -> onAddRows(count); showAddRowsSheet = false },
        
        editingColumn = editingColumn,
        onDismissEditColumn = { editingColumn = null },
        onUpdateColumn = onUpdateColumn,
        onDeleteColumn = onDeleteColumn,
        
        showNewColumnEditor = showNewColumnEditor,
        onDismissNewColumnEditor = { showNewColumnEditor = false },
        onAddColumn = onAddColumn,
        
        showColumnManager = showColumnManager,
        onDismissColumnManager = { showColumnManager = false },
        columns = columns,
        onReorderColumns = onReorderColumns,
        
        showSettingsSheet = showSettingsSheet,
        onDismissSettingsSheet = { showSettingsSheet = false },
        rowHeight = rowHeight,
        onRowHeightChange = { rowHeight = it },
        
        showSheetInfo = showSheetInfo,
        onDismissSheetInfo = { showSheetInfo = false },
        tableName = tableName,
        
        showBarcodeScanner = showBarcodeScanner,
        onDismissBarcodeScanner = { 
            showBarcodeScanner = false
            scanningCell = null
        },
        scanningCell = scanningCell,
        rows = rows,
        autoAddEnabled = autoAddEnabled,
        onAutoAddChanged = { autoAddEnabled = it },
        onCellValueChange = onCellValueChange,
        
        showRowDataFillDialog = showRowDataFillDialog,
        onDismissRowDataFillDialog = { 
            showRowDataFillDialog = false
            fillDataRowId = null
        },
        fillDataRowId = fillDataRowId
    )
}

@Composable
private fun BottomButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(12.dp, 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            icon,
            label,
            tint = Color(0xFF666666),
            modifier = Modifier.size(24.dp)
        )
        Text(
            label,
            fontSize = 10.sp,
            color = Color(0xFF666666)
        )
    }
}