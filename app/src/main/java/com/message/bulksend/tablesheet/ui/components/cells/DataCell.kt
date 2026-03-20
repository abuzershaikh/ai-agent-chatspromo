package com.message.bulksend.tablesheet.ui.components.cells

import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.message.bulksend.tablesheet.data.models.ColumnModel
import com.message.bulksend.tablesheet.data.models.ColumnType
import com.message.bulksend.tablesheet.data.models.FieldType
import com.message.bulksend.tablesheet.data.models.PriorityOption
import com.message.bulksend.tablesheet.ui.theme.TableTheme

@Composable
fun DataCell(
    value: String,
    column: ColumnModel,
    rowHeight: Float = 44f,
    isRowSelected: Boolean = false,
    onValueChange: (String) -> Unit,
    onFillRowData: (() -> Unit)? = null
) {
    val cellWidth = (TableTheme.CELL_WIDTH.value * column.width).dp
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
                ColumnType.SELECT -> SelectCell(
                    value, column, cellWidth, cellHeight, isRowSelected, onValueChange
                )
                ColumnType.CHECKBOX -> CheckboxCell(
                    value, cellWidth, cellHeight, isRowSelected, onValueChange
                )
                ColumnType.DATE, "DATEONLY" -> DateCell(
                    value, cellWidth, cellHeight, isRowSelected, onValueChange
                )
                "TIME" -> TimeCell(
                    value, cellWidth, cellHeight, isRowSelected, onValueChange
                )
                ColumnType.EMAIL, "EMAIL" -> EmailCell(
                    value, cellWidth, cellHeight, isRowSelected, onValueChange
                )
                "AUDIO" -> AudioCell(
                    value, cellWidth, cellHeight, isRowSelected, onValueChange
                )
                ColumnType.AMOUNT -> AmountCell(
                    value, column, cellWidth, cellHeight, isRowSelected, onValueChange
                )
                FieldType.PRIORITY, ColumnType.PRIORITY -> {
                    val priorityOptions = PriorityOption.parseFromString(column.selectOptions)
                    PriorityCell(
                        value, priorityOptions, cellWidth, cellHeight, isRowSelected, onValueChange
                    )
                }
                else -> TextInputCell(
                    value, column.type, cellWidth, cellHeight, isRowSelected, onValueChange
                )
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
                leadingIcon = { Icon(Icons.Default.Edit, null, tint = TableTheme.HEADER_BG) }
            )
        }
    }
}