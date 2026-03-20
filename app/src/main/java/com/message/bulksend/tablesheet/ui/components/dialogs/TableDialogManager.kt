package com.message.bulksend.tablesheet.ui.components.dialogs

import androidx.compose.runtime.Composable
import com.message.bulksend.tablesheet.data.models.ColumnModel
import com.message.bulksend.tablesheet.data.models.FieldType
import com.message.bulksend.tablesheet.data.models.RowModel
import com.message.bulksend.tablesheet.ui.components.screens.*
import com.message.bulksend.tablesheet.ui.components.sheets.*

@Composable
fun TableDialogManager(
    // Add Rows Sheet
    showAddRowsSheet: Boolean,
    onDismissAddRowsSheet: () -> Unit,
    onAddRows: (Int) -> Unit,
    
    // Edit Column
    editingColumn: ColumnModel?,
    onDismissEditColumn: () -> Unit,
    onUpdateColumn: ((columnId: Long, name: String, type: String, width: Float, selectOptions: String?) -> Unit)?,
    onDeleteColumn: (Long) -> Unit,
    
    // New Column Editor
    showNewColumnEditor: Boolean,
    onDismissNewColumnEditor: () -> Unit,
    onAddColumn: (name: String, type: String, width: Float, selectOptions: String?) -> Unit,
    
    // Column Manager
    showColumnManager: Boolean,
    onDismissColumnManager: () -> Unit,
    columns: List<ColumnModel>,
    onReorderColumns: ((List<ColumnModel>) -> Unit)?,
    
    // Settings Sheet
    showSettingsSheet: Boolean,
    onDismissSettingsSheet: () -> Unit,
    rowHeight: Float,
    onRowHeightChange: (Float) -> Unit,
    
    // Sheet Info
    showSheetInfo: Boolean,
    onDismissSheetInfo: () -> Unit,
    tableName: String,
    
    // Barcode Scanner
    showBarcodeScanner: Boolean,
    onDismissBarcodeScanner: () -> Unit,
    scanningCell: Pair<Long, Long>?,
    rows: List<RowModel>,
    autoAddEnabled: Boolean,
    onAutoAddChanged: (Boolean) -> Unit,
    onCellValueChange: (rowId: Long, columnId: Long, value: String) -> Unit,
    
    // Row Data Fill Dialog
    showRowDataFillDialog: Boolean,
    onDismissRowDataFillDialog: () -> Unit,
    fillDataRowId: Long?
) {
    // Add Rows Sheet
    if (showAddRowsSheet) {
        AddRowsSheetContent(
            onDismiss = onDismissAddRowsSheet,
            onAddRows = onAddRows
        )
    }
    
    // Column Editor - Edit existing column
    editingColumn?.let { column ->
        ColumnEditorScreen(
            column = column,
            isNewColumn = false,
            onSave = { name, type, width, style, options ->
                onUpdateColumn?.invoke(
                    column.id,
                    name,
                    type,
                    width,
                    if (options.isNotEmpty()) options.joinToString(",") else null
                )
                onDismissEditColumn()
            },
            onDelete = {
                onDeleteColumn(column.id)
                onDismissEditColumn()
            },
            onDismiss = onDismissEditColumn
        )
    }
    
    // Column Editor - Add new column
    if (showNewColumnEditor) {
        ColumnEditorScreen(
            column = null,
            isNewColumn = true,
            onSave = { name, type, width, style, options ->
                onAddColumn(
                    name,
                    type,
                    width,
                    if (options.isNotEmpty()) options.joinToString(",") else null
                )
                onDismissNewColumnEditor()
            },
            onDelete = { onDismissNewColumnEditor() },
            onDismiss = onDismissNewColumnEditor
        )
    }
    
    // Column Manager Screen
    if (showColumnManager) {
        ColumnManageScreen(
            columns = columns,
            onDismiss = onDismissColumnManager,
            onAddColumn = { 
                onDismissColumnManager()
                onDismissNewColumnEditor() // This will trigger the new column editor
            },
            onEditColumn = { column ->
                onDismissColumnManager()
                // Set the editing column - this would need to be handled by parent
            },
            onDeleteColumn = { columnId ->
                onDeleteColumn(columnId)
            },
            onReorderColumns = { reorderedList ->
                onReorderColumns?.invoke(reorderedList)
            }
        )
    }
    
    // Settings Bottom Sheet
    if (showSettingsSheet) {
        TableSettingsSheet(
            rowHeight = rowHeight,
            onRowHeightChange = onRowHeightChange,
            onDismiss = onDismissSettingsSheet
        )
    }
    
    // Sheet Info Screen
    if (showSheetInfo) {
        SheetInfoScreen(
            tableName = tableName,
            rowCount = rows.size,
            columnCount = columns.size,
            onDismiss = onDismissSheetInfo
        )
    }
    
    // Row Data Fill Dialog
    if (showRowDataFillDialog && fillDataRowId != null) {
        RowDataFillDialog(
            rowId = fillDataRowId,
            columns = columns,
            currentData = emptyMap(), // This would need to be passed from parent
            onDismiss = onDismissRowDataFillDialog,
            onSave = { rowData ->
                rowData.forEach { (columnId, value) ->
                    onCellValueChange(fillDataRowId, columnId, value)
                }
                onDismissRowDataFillDialog()
            }
        )
    }
}