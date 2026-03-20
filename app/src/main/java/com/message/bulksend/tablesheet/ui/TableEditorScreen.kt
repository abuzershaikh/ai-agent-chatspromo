package com.message.bulksend.tablesheet.ui

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import android.os.Environment
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape

import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.message.bulksend.tablesheet.data.models.ColumnModel
import com.message.bulksend.tablesheet.data.models.ColumnType
import com.message.bulksend.tablesheet.data.models.CurrencyHelper
import com.message.bulksend.tablesheet.data.models.FieldType
import com.message.bulksend.tablesheet.data.models.FieldTypes
import com.message.bulksend.tablesheet.data.models.PriorityOption
import com.message.bulksend.tablesheet.data.models.RowModel
import java.io.File

private val CELL_WIDTH = 120.dp
private val CELL_HEIGHT = 44.dp
private val ROW_NUMBER_WIDTH = 50.dp
private val HEADER_HEIGHT = 48.dp
private val GRID_COLOR = Color(0xFFBDBDBD)
private val HEADER_BG = Color(0xFF1976D2)

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
    onReorderColumns: ((List<ColumnModel>) -> Unit)? = null
) {
    var showAddRowsSheet by remember { mutableStateOf(false) }
    var editingColumn by remember { mutableStateOf<ColumnModel?>(null) }
    var showNewColumnEditor by remember { mutableStateOf(false) }
    var showColumnManager by remember { mutableStateOf(false) }
    var showSettingsSheet by remember { mutableStateOf(false) }
    var showSheetInfo by remember { mutableStateOf(false) }
    var autoAddEnabled by remember { mutableStateOf(true) } // Auto-add preference
    var rowHeight by remember { mutableStateOf(44f) } // Default row height in dp

    var showRowDataFillDialog by remember { mutableStateOf(false) }
    var fillDataRowId by remember { mutableStateOf<Long?>(null) }
    val horizontalScrollState = rememberScrollState()
    val listState = rememberLazyListState()

    Box(modifier = Modifier.fillMaxSize().background(Color.White)) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            Surface(modifier = Modifier.fillMaxWidth(), color = HEADER_BG, shadowElevation = 4.dp) {
                Row(
                    modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBackPressed) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
                    }
                    Column(modifier = Modifier.weight(1f).padding(start = 8.dp)) {
                        Text(tableName, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${rows.size} rows • ${columns.size} columns", fontSize = 12.sp, color = Color.White.copy(alpha = 0.8f))
                    }
                    

                    
                    IconButton(onClick = { showSheetInfo = true }) {
                        Icon(Icons.Default.Info, "Sheet Info", tint = Color.White)
                    }
                }
            }

            // Column Headers Row
            Row(modifier = Modifier.fillMaxWidth().background(HEADER_BG).height(HEADER_HEIGHT)) {
                Box(
                    modifier = Modifier.width(ROW_NUMBER_WIDTH).fillMaxHeight().border(1.dp, GRID_COLOR),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.GridOn, null, tint = Color.White, modifier = Modifier.size(18.dp))
                }
                Row(modifier = Modifier.weight(1f).horizontalScroll(horizontalScrollState)) {
                    columns.forEach { column ->
                        ColumnHeaderCell(
                            column = column,
                            onDelete = { onDeleteColumn(column.id) },
                            onClick = { editingColumn = column },
                            onWidthChange = { newWidth ->
                                onUpdateColumn?.invoke(column.id, column.name, column.type, newWidth, column.selectOptions)
                            }
                        )
                    }
                    // Add column button - only top-right corner rounded (connected to sheet on left and bottom)
                    Box(
                        modifier = Modifier
                            .width(46.dp)
                            .fillMaxHeight()
                            .background(
                                Color(0xFF4CAF50), 
                                RoundedCornerShape(topStart = 0.dp, topEnd = 12.dp, bottomEnd = 0.dp, bottomStart = 0.dp)
                            )
                            .clickable { showNewColumnEditor = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Add, "Add Column", tint = Color.White, modifier = Modifier.size(24.dp))
                    }
                    // 1 inch space after + icon (side)
                    Spacer(modifier = Modifier.width(96.dp))
                }
            }

            // Data Rows with LazyColumn - Add bottom padding for toolbar
            LazyColumn(
                state = listState, 
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = 80.dp) // Add padding for bottom toolbar
            ) {
                itemsIndexed(items = rows, key = { _, row -> row.id }) { index, row ->
                    val isSelected = false
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(rowHeight.dp)
                            .background(if (isSelected) Color(0xFFE3F2FD) else Color.Transparent)

                    ) {
                        // Row number
                        RowNumberCell(
                            index = index + 1, 
                            rowHeight = rowHeight, 
                            isSelected = isSelected,
                            onDelete = { onDeleteRow(row.id) },
                            onSelect = { }
                        )
                        // Data cells
                        Row(modifier = Modifier.weight(1f).horizontalScroll(horizontalScrollState)) {
                            columns.forEach { column ->
                                val cellValue = cellsMap[Pair(row.id, column.id)] ?: ""
                                DataCell(
                                    value = cellValue,
                                    column = column,
                                    rowHeight = rowHeight,
                                    isRowSelected = isSelected,
                                    onValueChange = { onCellValueChange(row.id, column.id, it) },
                                    onFillRowData = {
                                        fillDataRowId = row.id
                                        showRowDataFillDialog = true
                                    }
                                )
                            }
                            // Space matching header (46dp for + icon + 96dp space)
                            Spacer(modifier = Modifier.width(142.dp))
                        }
                    }
                }
                // Add row button - only bottom side rounded (connected to sheet on top)
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(CELL_HEIGHT)
                            .clickable { showAddRowsSheet = true }
                            .background(Color(0xFFF5F5F5)),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Plus icon with only bottom corners rounded
                        Box(
                            modifier = Modifier
                                .width(ROW_NUMBER_WIDTH)
                                .fillMaxHeight()
                                .background(
                                    Color(0xFF4CAF50),
                                    RoundedCornerShape(topStart = 0.dp, topEnd = 0.dp, bottomEnd = 0.dp, bottomStart = 12.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Add, "Add Row", tint = Color.White, modifier = Modifier.size(22.dp))
                        }
                        Text("Tap to add rows", modifier = Modifier.padding(start = 12.dp), color = Color.Gray, fontSize = 14.sp)
                    }
                }
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
                modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(8.dp),
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

    // Dialogs
    if (showAddRowsSheet) {
        AddRowsSheetContent(
            onDismiss = { showAddRowsSheet = false },
            onAddRows = { count -> onAddRows(count); showAddRowsSheet = false }
        )
    }
    
    // Column Editor Full Screen - Edit existing column
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
                editingColumn = null
            },
            onDelete = {
                onDeleteColumn(column.id)
                editingColumn = null
            },
            onDismiss = { editingColumn = null }
        )
    }
    
    // Column Editor Full Screen - Add new column
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
                showNewColumnEditor = false
            },
            onDelete = { showNewColumnEditor = false },
            onDismiss = { showNewColumnEditor = false }
        )
    }
    
    // Column Manager Screen
    if (showColumnManager) {
        ColumnManageScreen(
            columns = columns,
            onDismiss = { showColumnManager = false },
            onAddColumn = { 
                showColumnManager = false
                showNewColumnEditor = true 
            },
            onEditColumn = { column ->
                showColumnManager = false
                editingColumn = column
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
            onRowHeightChange = { rowHeight = it },
            onDismiss = { showSettingsSheet = false }
        )
    }
    
    // Sheet Info Screen
    if (showSheetInfo) {
        SheetInfoScreen(
            tableName = tableName,
            rowCount = rows.size,
            columnCount = columns.size,
            onDismiss = { showSheetInfo = false }
        )
    }
    
    // Row Data Fill Dialog
    if (showRowDataFillDialog && fillDataRowId != null) {
        RowDataFillDialog(
            rowId = fillDataRowId!!,
            columns = columns,
            currentData = cellsMap,
            onDismiss = { 
                showRowDataFillDialog = false
                fillDataRowId = null
            },
            onSave = { rowData ->
                // Save all the row data at once
                rowData.forEach { (columnId, value) ->
                    onCellValueChange(fillDataRowId!!, columnId, value)
                }
                showRowDataFillDialog = false
                fillDataRowId = null
            }
        )
    }
}


@Composable
private fun BottomButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Column(
        modifier = Modifier.clickable(onClick = onClick).padding(12.dp, 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, label, tint = Color(0xFF666666), modifier = Modifier.size(24.dp))
        Text(label, fontSize = 10.sp, color = Color(0xFF666666))
    }
}

@Composable
private fun ColumnHeaderCell(
    column: ColumnModel, 
    onDelete: () -> Unit, 
    onClick: () -> Unit,
    onWidthChange: (Float) -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    val typeConfig = FieldTypes.getConfig(column.type)
    
    Box(
        modifier = Modifier
            .width((CELL_WIDTH.value * column.width).dp)
            .height(HEADER_HEIGHT)
            .background(HEADER_BG)
            .border(1.dp, GRID_COLOR)
            .clickable { onClick() }
            .pointerInput(Unit) { detectTapGestures(onLongPress = { showMenu = true }) },
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                typeConfig.icon,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.8f),
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                column.name,
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
            DropdownMenuItem(
                text = { Text("Edit Column") },
                onClick = { showMenu = false; onClick() },
                leadingIcon = { Icon(Icons.Default.Edit, null) }
            )
            DropdownMenuItem(
                text = { Text("Delete Column", color = Color.Red) },
                onClick = { showMenu = false; onDelete() },
                leadingIcon = { Icon(Icons.Default.Delete, null, tint = Color.Red) }
            )
        }
    }
}

@Composable
private fun RowNumberCell(
    index: Int, 
    rowHeight: Float = 44f, 
    isSelected: Boolean = false,
    onDelete: () -> Unit,
    onSelect: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .width(ROW_NUMBER_WIDTH)
            .height(rowHeight.dp)
            .background(if (isSelected) Color(0xFF1565C0) else HEADER_BG)
            .border(1.dp, if (isSelected) Color(0xFF0D47A1) else GRID_COLOR)
            .clickable { onSelect() }
            .pointerInput(Unit) { detectTapGestures(onLongPress = { showMenu = true }) },
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (isSelected) {
                Icon(
                    Icons.Default.CheckCircle, 
                    contentDescription = "Selected",
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(4.dp))
            }
            Text(
                "$index", 
                color = Color.White, 
                fontSize = 13.sp, 
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
            )
        }
        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
            DropdownMenuItem(
                text = { Text("Delete Row", color = Color.Red) },
                onClick = { showMenu = false; onDelete() },
                leadingIcon = { Icon(Icons.Default.Delete, null, tint = Color.Red) }
            )
        }
    }
}

@Composable
private fun DataCell(
    value: String, 
    column: ColumnModel, 
    rowHeight: Float = 44f, 
    isRowSelected: Boolean = false,
    onValueChange: (String) -> Unit,
    onFillRowData: (() -> Unit)? = null
) {
    val cellWidth = (CELL_WIDTH.value * column.width).dp
    val cellHeight = rowHeight.dp
    var showContextMenu by remember { mutableStateOf(false) }
    
    Box {
        // Cell content with long press detection
        Box(
            modifier = Modifier.pointerInput(Unit) {
                detectTapGestures(
                    onLongPress = {
                        showContextMenu = true
                    }
                )
            }
        ) {
            when (column.type) {
                ColumnType.SELECT -> com.message.bulksend.tablesheet.ui.cells.SelectCell(value, column, cellWidth, cellHeight, isRowSelected, onValueChange)
                ColumnType.CHECKBOX -> com.message.bulksend.tablesheet.ui.cells.CheckboxCell(value, cellWidth, cellHeight, isRowSelected, onValueChange)
                ColumnType.DATE, "DATEONLY" -> com.message.bulksend.tablesheet.ui.cells.DateCell(value, cellWidth, cellHeight, isRowSelected, onValueChange)
                "TIME" -> com.message.bulksend.tablesheet.ui.cells.TimeCell(value, cellWidth, cellHeight, isRowSelected, onValueChange)
                ColumnType.EMAIL, "EMAIL" -> com.message.bulksend.tablesheet.ui.cells.EmailCell(value, cellWidth, cellHeight, isRowSelected, onValueChange)
                "AUDIO" -> com.message.bulksend.tablesheet.ui.cells.AudioCell(value, cellWidth, cellHeight, isRowSelected, onValueChange)
                ColumnType.AMOUNT -> com.message.bulksend.tablesheet.ui.cells.AmountCell(value, column, cellWidth, cellHeight, isRowSelected, onValueChange)
                FieldType.PRIORITY, ColumnType.PRIORITY -> {
                    val priorityOptions = PriorityOption.parseFromString(column.selectOptions)
                    com.message.bulksend.tablesheet.ui.cells.PriorityCell(value, priorityOptions, cellWidth, cellHeight, isRowSelected, onValueChange)
                }
                else -> com.message.bulksend.tablesheet.ui.cells.TextInputCell(value, column.type, cellWidth, cellHeight, isRowSelected, onValueChange)
            }
        }
        
        // Context menu
        DropdownMenu(
            expanded = showContextMenu,
            onDismissRequest = { showContextMenu = false }
        ) {
            DropdownMenuItem(
                text = { Text("Fill Row Data") },
                onClick = { 
                    showContextMenu = false
                    onFillRowData?.invoke()
                },
                leadingIcon = { Icon(Icons.Default.Edit, null, tint = HEADER_BG) }
            )
        }
    }
}









@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateCell(
    value: String, 
    cellWidth: androidx.compose.ui.unit.Dp, 
    cellHeight: androidx.compose.ui.unit.Dp, 
    isRowSelected: Boolean = false,
    onValueChange: (String) -> Unit
) {
    var showDatePicker by remember { mutableStateOf(false) }
    
    // Parse existing timestamp or use current date
    val timestamp = value.toLongOrNull()
    val displayDate = if (timestamp != null) {
        val calendar = java.util.Calendar.getInstance().apply { timeInMillis = timestamp }
        String.format("%02d/%02d/%d", 
            calendar.get(java.util.Calendar.DAY_OF_MONTH),
            calendar.get(java.util.Calendar.MONTH) + 1,
            calendar.get(java.util.Calendar.YEAR))
    } else if (value.isNotEmpty()) {
        value // Show as-is if not timestamp
    } else {
        ""
    }
    
    val backgroundColor = when {
        showDatePicker -> Color(0xFFE3F2FD)
        isRowSelected -> Color(0xFFE3F2FD).copy(alpha = 0.7f)
        else -> Color.White
    }
    
    Box(
        modifier = Modifier.width(cellWidth).height(cellHeight)
            .background(backgroundColor)
            .border(1.dp, if (showDatePicker) Color(0xFF2196F3) else GRID_COLOR)
            .clickable { showDatePicker = true },
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = displayDate,
                color = Color(0xFF333333),
                fontSize = 12.sp,
                maxLines = 1,
                modifier = Modifier.weight(1f)
            )
            if (displayDate.isEmpty()) {
                Icon(
                    Icons.Default.CalendarMonth,
                    contentDescription = null,
                    tint = Color(0xFFBDBDBD),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
    
    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = timestamp ?: System.currentTimeMillis()
        )
        
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        onValueChange(millis.toString())
                    }
                    showDatePicker = false
                }) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("Cancel")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeCell(
    value: String, 
    cellWidth: androidx.compose.ui.unit.Dp, 
    cellHeight: androidx.compose.ui.unit.Dp, 
    isRowSelected: Boolean = false,
    onValueChange: (String) -> Unit
) {
    var showTimePicker by remember { mutableStateOf(false) }
    
    // Parse existing timestamp or use current time
    val timestamp = value.toLongOrNull()
    val calendar = java.util.Calendar.getInstance().apply {
        if (timestamp != null) timeInMillis = timestamp
    }
    val hour = calendar.get(java.util.Calendar.HOUR_OF_DAY)
    val minute = calendar.get(java.util.Calendar.MINUTE)
    
    val displayTime = if (timestamp != null || value.contains(":")) {
        if (timestamp != null) {
            String.format("%02d:%02d", hour, minute)
        } else {
            value
        }
    } else if (value.isNotEmpty()) {
        value
    } else {
        ""
    }
    
    val backgroundColor = when {
        showTimePicker -> Color(0xFFE3F2FD)
        isRowSelected -> Color(0xFFE3F2FD).copy(alpha = 0.7f)
        else -> Color.White
    }
    
    Box(
        modifier = Modifier.width(cellWidth).height(cellHeight)
            .background(backgroundColor)
            .border(1.dp, if (showTimePicker) Color(0xFF2196F3) else GRID_COLOR)
            .clickable { showTimePicker = true },
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = displayTime,
                color = Color(0xFF333333),
                fontSize = 12.sp,
                maxLines = 1,
                modifier = Modifier.weight(1f)
            )
            if (displayTime.isEmpty()) {
                Icon(
                    Icons.Default.Schedule,
                    contentDescription = null,
                    tint = Color(0xFFBDBDBD),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
    
    if (showTimePicker) {
        val timePickerState = rememberTimePickerState(
            initialHour = hour,
            initialMinute = minute,
            is24Hour = false
        )
        
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            title = { Text("Select Time") },
            text = {
                TimePicker(state = timePickerState)
            },
            confirmButton = {
                TextButton(onClick = {
                    val cal = java.util.Calendar.getInstance().apply {
                        set(java.util.Calendar.HOUR_OF_DAY, timePickerState.hour)
                        set(java.util.Calendar.MINUTE, timePickerState.minute)
                        set(java.util.Calendar.SECOND, 0)
                        set(java.util.Calendar.MILLISECOND, 0)
                    }
                    onValueChange(cal.timeInMillis.toString())
                    showTimePicker = false
                }) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddRowsSheetContent(onDismiss: () -> Unit, onAddRows: (Int) -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Color.White) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp).padding(bottom = 32.dp)) {
            Text("Add Blank Rows", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                listOf(1, 5, 10, 50, 100).forEach { n ->
                    Surface(
                        modifier = Modifier.clickable { onAddRows(n) },
                        shape = RoundedCornerShape(20.dp),
                        color = HEADER_BG.copy(alpha = 0.1f),
                        border = BorderStroke(1.dp, HEADER_BG)
                    ) {
                        Text("$n", modifier = Modifier.padding(20.dp, 10.dp), color = HEADER_BG, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@Composable
private fun AudioCell(
    value: String,
    cellWidth: androidx.compose.ui.unit.Dp,
    cellHeight: androidx.compose.ui.unit.Dp,
    isRowSelected: Boolean = false,
    onValueChange: (String) -> Unit
) {
    val context = LocalContext.current
    var isRecording by remember { mutableStateOf(false) }
    var isPlaying by remember { mutableStateOf(false) }
    var hasPermission by remember { mutableStateOf(false) }
    var mediaRecorder by remember { mutableStateOf<MediaRecorder?>(null) }
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    var currentFilePath by remember { mutableStateOf("") }
    
    val hasAudio = value.isNotEmpty() && File(value).exists()
    
    // Function to start recording
    fun startRecording() {
        try {
            // Create directory: Documents/ChatsPromo/Sheet/Audio
            val audioDir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
                "ChatsPromo/Sheet/Audio"
            )
            if (!audioDir.exists()) audioDir.mkdirs()
            
            // Create file
            val fileName = "audio_${System.currentTimeMillis()}.m4a"
            val audioFile = File(audioDir, fileName)
            currentFilePath = audioFile.absolutePath
            
            // Setup recorder
            mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setOutputFile(currentFilePath)
                prepare()
                start()
            }
            isRecording = true
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    // Function to stop recording
    fun stopRecording() {
        try {
            mediaRecorder?.stop()
            mediaRecorder?.release()
            mediaRecorder = null
            isRecording = false
            if (currentFilePath.isNotEmpty()) {
                onValueChange(currentFilePath)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    // Permission launcher - start recording after permission granted
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
        if (granted && !hasAudio) {
            startRecording()
        }
    }
    
    // Check permission
    LaunchedEffect(Unit) {
        hasPermission = ContextCompat.checkSelfPermission(
            context, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }
    
    // Cleanup
    DisposableEffect(Unit) {
        onDispose {
            mediaRecorder?.release()
            mediaPlayer?.release()
        }
    }
    
    val backgroundColor = when {
        isRecording -> Color(0xFFFFEBEE)
        isRowSelected -> Color(0xFFE3F2FD).copy(alpha = 0.7f)
        else -> Color.White
    }
    
    Box(
        modifier = Modifier
            .width(cellWidth)
            .height(cellHeight)
            .background(backgroundColor)
            .border(1.dp, if (isRecording) Color(0xFFF43F5E) else GRID_COLOR),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isRecording) {
                // Recording state - show stop button
                IconButton(
                    onClick = { stopRecording() },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        Icons.Default.Stop,
                        contentDescription = "Stop Recording",
                        tint = Color(0xFFF43F5E),
                        modifier = Modifier.size(24.dp)
                    )
                }
                Text("REC", fontSize = 10.sp, color = Color(0xFFF43F5E), fontWeight = FontWeight.Bold)
            } else if (hasAudio) {
                // Has audio - show play/delete
                IconButton(
                    onClick = {
                        if (isPlaying) {
                            mediaPlayer?.stop()
                            mediaPlayer?.release()
                            mediaPlayer = null
                            isPlaying = false
                        } else {
                            try {
                                mediaPlayer = MediaPlayer().apply {
                                    setDataSource(value)
                                    prepare()
                                    start()
                                    setOnCompletionListener {
                                        isPlaying = false
                                        release()
                                        mediaPlayer = null
                                    }
                                }
                                isPlaying = true
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        if (isPlaying) Icons.Default.Stop else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Stop" else "Play",
                        tint = Color(0xFF4CAF50),
                        modifier = Modifier.size(20.dp)
                    )
                }
                // Delete button
                IconButton(
                    onClick = {
                        try { File(value).delete() } catch (e: Exception) { }
                        onValueChange("")
                    },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = Color(0xFFEF4444),
                        modifier = Modifier.size(18.dp)
                    )
                }
            } else {
                // No audio - show mic to record
                IconButton(
                    onClick = {
                        if (hasPermission) {
                            startRecording()
                        } else {
                            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        Icons.Default.Mic,
                        contentDescription = "Record",
                        tint = Color(0xFFF43F5E),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun AmountCell(
    value: String,
    column: ColumnModel,
    cellWidth: androidx.compose.ui.unit.Dp,
    cellHeight: androidx.compose.ui.unit.Dp,
    isRowSelected: Boolean = false,
    onValueChange: (String) -> Unit
) {
    var text by remember { mutableStateOf(value) }
    var isFocused by remember { mutableStateOf(false) }
    
    LaunchedEffect(value) { if (!isFocused) text = value }
    
    // Parse currency options from column.selectOptions (format: "INR|₹|left")
    val currencyOptions = CurrencyHelper.parseCurrencyOptions(column.selectOptions)
    val symbol = currencyOptions?.second ?: "₹"
    val position = currencyOptions?.third ?: "left"

    val backgroundColor = when {
        isFocused -> Color(0xFFE3F2FD)
        isRowSelected -> Color(0xFFE3F2FD).copy(alpha = 0.7f)
        else -> Color.White
    }

    Box(
        modifier = Modifier
            .width(cellWidth)
            .height(cellHeight)
            .background(backgroundColor)
            .border(1.dp, if (isFocused) Color(0xFF2196F3) else GRID_COLOR),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Symbol on left
            if (position == "left") {
                Text(
                    symbol,
                    color = Color(0xFFF59E0B),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.width(4.dp))
            }
            
            // Amount input
            BasicTextField(
                value = text,
                onValueChange = { newValue ->
                    // Only allow numbers and decimal
                    val filtered = newValue.filter { it.isDigit() || it == '.' }
                    text = filtered
                    onValueChange(filtered)
                },
                textStyle = TextStyle(
                    color = Color(0xFF333333),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                ),
                cursorBrush = SolidColor(Color(0xFF2196F3)),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier
                    .weight(1f)
                    .onFocusChanged { isFocused = it.isFocused }
            )
            
            // Symbol on right
            if (position == "right") {
                Spacer(Modifier.width(4.dp))
                Text(
                    symbol,
                    color = Color(0xFFF59E0B),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TableSettingsSheet(
    rowHeight: Float,
    onRowHeightChange: (Float) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color.White
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(
                "Table Settings",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF333333)
            )
            
            Spacer(Modifier.height(24.dp))
            
            // Row Height Section
            Text(
                "Row Height",
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF374151)
            )
            Spacer(Modifier.height(8.dp))
            
            Text(
                "${rowHeight.toInt()} dp",
                fontSize = 14.sp,
                color = Color.Gray
            )
            
            Spacer(Modifier.height(8.dp))
            
            // Row height slider
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { if (rowHeight > 30f) onRowHeightChange(rowHeight - 5f) }) {
                    Icon(Icons.Default.Remove, "Decrease", tint = HEADER_BG)
                }
                
                Slider(
                    value = rowHeight,
                    onValueChange = onRowHeightChange,
                    valueRange = 30f..100f,
                    steps = 13,
                    colors = SliderDefaults.colors(
                        thumbColor = HEADER_BG,
                        activeTrackColor = HEADER_BG
                    ),
                    modifier = Modifier.weight(1f)
                )
                
                IconButton(onClick = { if (rowHeight < 100f) onRowHeightChange(rowHeight + 5f) }) {
                    Icon(Icons.Default.Add, "Increase", tint = HEADER_BG)
                }
            }
            
            Spacer(Modifier.height(8.dp))
            
            // Preset buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    "Small" to 36f,
                    "Medium" to 44f,
                    "Large" to 56f,
                    "XL" to 72f
                ).forEach { (label, height) ->
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onRowHeightChange(height) },
                        shape = RoundedCornerShape(8.dp),
                        color = if (rowHeight == height) HEADER_BG else Color(0xFFF3F4F6),
                        border = BorderStroke(1.dp, if (rowHeight == height) HEADER_BG else Color(0xFFE5E7EB))
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                label,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = if (rowHeight == height) Color.White else Color(0xFF374151)
                            )
                            Text(
                                "${height.toInt()}dp",
                                fontSize = 10.sp,
                                color = if (rowHeight == height) Color.White.copy(alpha = 0.8f) else Color.Gray
                            )
                        }
                    }
                }
            }
            
            Spacer(Modifier.height(24.dp))
            
            // Preview
            Text(
                "Preview",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF374151)
            )
            Spacer(Modifier.height(8.dp))
            
            // Preview row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(rowHeight.dp)
                    .border(1.dp, GRID_COLOR, RoundedCornerShape(4.dp))
            ) {
                Box(
                    modifier = Modifier
                        .width(50.dp)
                        .fillMaxHeight()
                        .background(HEADER_BG, RoundedCornerShape(topStart = 4.dp, bottomStart = 4.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("1", color = Color.White, fontWeight = FontWeight.Medium)
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(Color.White),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Text(
                        "Sample cell content",
                        modifier = Modifier.padding(horizontal = 12.dp),
                        color = Color(0xFF333333),
                        fontSize = 13.sp
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RowDataFillDialog(
    rowId: Long,
    columns: List<ColumnModel>,
    currentData: Map<Pair<Long, Long>, String>,
    onDismiss: () -> Unit,
    onSave: (Map<Long, String>) -> Unit
) {
    // Initialize form data with current values
    val formData = remember(rowId, columns) {
        mutableStateMapOf<Long, String>().apply {
            columns.forEach { column ->
                val currentValue = currentData[Pair(rowId, column.id)] ?: ""
                put(column.id, currentValue)
            }
        }
    }
    
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        modifier = Modifier.fillMaxHeight(0.9f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Fill Row Data",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF333333)
                )
                Row {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = Color.Gray)
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = { onSave(formData.toMap()) },
                        colors = ButtonDefaults.buttonColors(containerColor = HEADER_BG)
                    ) {
                        Text("Save", color = Color.White)
                    }
                }
            }
            
            Spacer(Modifier.height(16.dp))
            HorizontalDivider(color = Color(0xFFE5E7EB))
            Spacer(Modifier.height(16.dp))
            
            // Form fields
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(
                    count = columns.size,
                    key = { index -> columns[index].id }
                ) { index ->
                    val column = columns[index]
                    RowDataField(
                        column = column,
                        value = formData[column.id] ?: "",
                        onValueChange = { newValue ->
                            formData[column.id] = newValue
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RowDataField(
    column: ColumnModel,
    value: String,
    onValueChange: (String) -> Unit,
    onScanBarcode: (() -> Unit)? = null
) {
    val typeConfig = FieldTypes.getConfig(column.type)
    
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        // Field label with icon
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 8.dp)
        ) {
            Icon(
                typeConfig.icon,
                contentDescription = null,
                tint = typeConfig.color,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                column.name,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF374151)
            )
        }
        
        // Field input based on type
        when (column.type) {
            ColumnType.SELECT -> {
                SelectFieldInput(column, value, onValueChange)
            }
            ColumnType.CHECKBOX -> {
                CheckboxFieldInput(value, onValueChange)
            }
            ColumnType.DATE, "DATEONLY" -> {
                DateFieldInput(value, onValueChange)
            }
            "TIME" -> {
                TimeFieldInput(value, onValueChange)
            }
            ColumnType.AMOUNT -> {
                AmountFieldInput(column, value, onValueChange)
            }
            FieldType.PRIORITY, ColumnType.PRIORITY -> {
                PriorityFieldInput(column, value, onValueChange)
            }
            else -> {
                TextFieldInput(column.type, value, onValueChange)
            }
        }
    }
}

@Composable
private fun TextFieldInput(
    fieldType: String,
    value: String,
    onValueChange: (String) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(
            keyboardType = when (fieldType) {
                ColumnType.INTEGER -> KeyboardType.Number
                ColumnType.PHONE -> KeyboardType.Phone
                ColumnType.EMAIL -> KeyboardType.Email
                else -> KeyboardType.Text
            }
        ),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = HEADER_BG,
            focusedLabelColor = HEADER_BG
        )
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectFieldInput(
    column: ColumnModel,
    value: String,
    onValueChange: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val options = column.selectOptions?.split(",")?.filter { it.isNotBlank() } ?: emptyList()
    
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded }
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = { },
            readOnly = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = HEADER_BG
            )
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        onValueChange(option)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun CheckboxFieldInput(
    value: String,
    onValueChange: (String) -> Unit
) {
    val isChecked = value.equals("true", ignoreCase = true)
    
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Checkbox(
            checked = isChecked,
            onCheckedChange = { onValueChange(if (it) "true" else "false") },
            colors = CheckboxDefaults.colors(checkedColor = HEADER_BG)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            if (isChecked) "Checked" else "Unchecked",
            color = Color(0xFF6B7280)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateFieldInput(
    value: String,
    onValueChange: (String) -> Unit
) {
    var showDatePicker by remember { mutableStateOf(false) }
    
    val timestamp = value.toLongOrNull()
    val displayDate = if (timestamp != null) {
        val calendar = java.util.Calendar.getInstance().apply { timeInMillis = timestamp }
        String.format("%02d/%02d/%d", 
            calendar.get(java.util.Calendar.DAY_OF_MONTH),
            calendar.get(java.util.Calendar.MONTH) + 1,
            calendar.get(java.util.Calendar.YEAR))
    } else if (value.isNotEmpty()) {
        value
    } else {
        ""
    }
    
    OutlinedTextField(
        value = displayDate,
        onValueChange = { },
        readOnly = true,
        trailingIcon = {
            IconButton(onClick = { showDatePicker = true }) {
                Icon(Icons.Default.CalendarMonth, "Select Date")
            }
        },
        modifier = Modifier.fillMaxWidth(),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = HEADER_BG
        )
    )
    
    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = timestamp ?: System.currentTimeMillis()
        )
        
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        onValueChange(millis.toString())
                    }
                    showDatePicker = false
                }) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("Cancel")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeFieldInput(
    value: String,
    onValueChange: (String) -> Unit
) {
    var showTimePicker by remember { mutableStateOf(false) }
    
    val timestamp = value.toLongOrNull()
    val calendar = java.util.Calendar.getInstance().apply {
        if (timestamp != null) timeInMillis = timestamp
    }
    val hour = calendar.get(java.util.Calendar.HOUR_OF_DAY)
    val minute = calendar.get(java.util.Calendar.MINUTE)
    
    val displayTime = if (timestamp != null || value.contains(":")) {
        if (timestamp != null) {
            String.format("%02d:%02d", hour, minute)
        } else {
            value
        }
    } else if (value.isNotEmpty()) {
        value
    } else {
        ""
    }
    
    OutlinedTextField(
        value = displayTime,
        onValueChange = { },
        readOnly = true,
        trailingIcon = {
            IconButton(onClick = { showTimePicker = true }) {
                Icon(Icons.Default.Schedule, "Select Time")
            }
        },
        modifier = Modifier.fillMaxWidth(),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = HEADER_BG
        )
    )
    
    if (showTimePicker) {
        val timePickerState = rememberTimePickerState(
            initialHour = hour,
            initialMinute = minute,
            is24Hour = false
        )
        
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            title = { Text("Select Time") },
            text = {
                TimePicker(state = timePickerState)
            },
            confirmButton = {
                TextButton(onClick = {
                    val cal = java.util.Calendar.getInstance().apply {
                        set(java.util.Calendar.HOUR_OF_DAY, timePickerState.hour)
                        set(java.util.Calendar.MINUTE, timePickerState.minute)
                        set(java.util.Calendar.SECOND, 0)
                        set(java.util.Calendar.MILLISECOND, 0)
                    }
                    onValueChange(cal.timeInMillis.toString())
                    showTimePicker = false
                }) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun AmountFieldInput(
    column: ColumnModel,
    value: String,
    onValueChange: (String) -> Unit
) {
    val currencyOptions = CurrencyHelper.parseCurrencyOptions(column.selectOptions)
    val symbol = currencyOptions?.second ?: "₹"
    val position = currencyOptions?.third ?: "left"
    
    OutlinedTextField(
        value = value,
        onValueChange = { newValue ->
            val filtered = newValue.filter { it.isDigit() || it == '.' }
            onValueChange(filtered)
        },
        leadingIcon = if (position == "left") {
            { Text(symbol, color = Color(0xFFF59E0B), fontWeight = FontWeight.Bold) }
        } else null,
        trailingIcon = if (position == "right") {
            { Text(symbol, color = Color(0xFFF59E0B), fontWeight = FontWeight.Bold) }
        } else null,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth(),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = HEADER_BG
        )
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PriorityFieldInput(
    column: ColumnModel,
    value: String,
    onValueChange: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val priorityOptions = PriorityOption.parseFromString(column.selectOptions)
    val selectedOption = priorityOptions.find { it.name == value }
    
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded }
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = { },
            readOnly = true,
            leadingIcon = if (selectedOption != null) {
                {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .background(selectedOption.color, CircleShape)
                    )
                }
            } else null,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = selectedOption?.color ?: HEADER_BG
            )
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            priorityOptions.forEach { option ->
                DropdownMenuItem(
                    text = { 
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .background(option.color, CircleShape)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(option.name, color = option.color, fontWeight = FontWeight.Medium)
                        }
                    },
                    onClick = {
                        onValueChange(option.name)
                        expanded = false
                    }
                )
            }
        }
    }
}