package com.message.bulksend.tablesheet.ui.components.cells

import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import android.widget.Toast
import com.message.bulksend.tablesheet.data.models.ColumnModel
import com.message.bulksend.tablesheet.data.models.ColumnType
import com.message.bulksend.tablesheet.data.models.FieldType
import com.message.bulksend.tablesheet.data.models.PriorityOption
import com.message.bulksend.tablesheet.ui.theme.TableTheme

data class CellFormatStyle(
    val backgroundColor: Color? = null,
    val textColor: Color? = null,
    val borderColor: Color? = null
)

@Composable
fun DataCell(
    value: String,
    column: ColumnModel,
    rowHeight: Float = 44f,
    isRowSelected: Boolean = false,
    formatStyle: CellFormatStyle? = null,
    onValueChange: (String) -> Unit,
    onFillRowData: (() -> Unit)? = null,
    onPasteGridData: ((String) -> Unit)? = null
) {
    val cellWidth = (TableTheme.CELL_WIDTH.value * column.width).dp
    val cellHeight = rowHeight.dp
    var showContextMenu by remember { mutableStateOf(false) }
    var showPasteDialog by remember { mutableStateOf(false) }
    var pastedText by remember { mutableStateOf("") }
    var pasteAsMultipleCells by remember { mutableStateOf(true) }
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    
    Box {
        // Cell content with tap detection
        Box(
            modifier = Modifier.pointerInput(Unit) {
                detectTapGestures(
                    onTap = {
                        showContextMenu = true
                    }
                )
            }
        ) {
            when (column.type) {
                ColumnType.SELECT -> SelectCell(
                    value,
                    column,
                    cellWidth,
                    cellHeight,
                    isRowSelected,
                    onValueChange,
                    overrideBackgroundColor = formatStyle?.backgroundColor,
                    overrideTextColor = formatStyle?.textColor,
                    overrideBorderColor = formatStyle?.borderColor
                )
                ColumnType.MULTI_SELECT -> MultiSelectCell(
                    value = value,
                    column = column,
                    cellWidth = cellWidth,
                    cellHeight = cellHeight,
                    isRowSelected = isRowSelected,
                    onValueChange = onValueChange,
                    overrideBackgroundColor = formatStyle?.backgroundColor,
                    overrideTextColor = formatStyle?.textColor,
                    overrideBorderColor = formatStyle?.borderColor
                )
                ColumnType.CHECKBOX -> CheckboxCell(
                    value,
                    cellWidth,
                    cellHeight,
                    isRowSelected,
                    onValueChange,
                    overrideBackgroundColor = formatStyle?.backgroundColor,
                    overrideBorderColor = formatStyle?.borderColor
                )
                ColumnType.DATE, "DATEONLY" -> DateCell(
                    value,
                    cellWidth,
                    cellHeight,
                    isRowSelected,
                    onValueChange,
                    overrideBackgroundColor = formatStyle?.backgroundColor,
                    overrideTextColor = formatStyle?.textColor,
                    overrideBorderColor = formatStyle?.borderColor
                )
                ColumnType.DATETIME -> DateTimeCell(
                    value = value,
                    cellWidth = cellWidth,
                    cellHeight = cellHeight,
                    isRowSelected = isRowSelected,
                    onValueChange = onValueChange,
                    overrideBackgroundColor = formatStyle?.backgroundColor,
                    overrideTextColor = formatStyle?.textColor,
                    overrideBorderColor = formatStyle?.borderColor
                )
                ColumnType.TIME -> TimeCell(
                    value,
                    cellWidth,
                    cellHeight,
                    isRowSelected,
                    onValueChange,
                    overrideBackgroundColor = formatStyle?.backgroundColor,
                    overrideTextColor = formatStyle?.textColor,
                    overrideBorderColor = formatStyle?.borderColor
                )
                ColumnType.EMAIL, "EMAIL" -> EmailCell(
                    value,
                    cellWidth,
                    cellHeight,
                    isRowSelected,
                    onValueChange,
                    overrideBackgroundColor = formatStyle?.backgroundColor,
                    overrideTextColor = formatStyle?.textColor,
                    overrideBorderColor = formatStyle?.borderColor
                )
                ColumnType.AUDIO -> AudioCell(
                    value,
                    cellWidth,
                    cellHeight,
                    isRowSelected,
                    onValueChange,
                    overrideBackgroundColor = formatStyle?.backgroundColor,
                    overrideBorderColor = formatStyle?.borderColor
                )
                ColumnType.AMOUNT -> AmountCell(
                    value,
                    column,
                    cellWidth,
                    cellHeight,
                    isRowSelected,
                    onValueChange,
                    overrideBackgroundColor = formatStyle?.backgroundColor,
                    overrideTextColor = formatStyle?.textColor,
                    overrideBorderColor = formatStyle?.borderColor
                )
                ColumnType.URL -> UrlCell(
                    value = value,
                    cellWidth = cellWidth,
                    cellHeight = cellHeight,
                    isRowSelected = isRowSelected,
                    onValueChange = onValueChange,
                    overrideBackgroundColor = formatStyle?.backgroundColor,
                    overrideTextColor = formatStyle?.textColor,
                    overrideBorderColor = formatStyle?.borderColor
                )
                ColumnType.MAP -> MapCell(
                    value = value,
                    cellWidth = cellWidth,
                    cellHeight = cellHeight,
                    isRowSelected = isRowSelected,
                    onValueChange = onValueChange,
                    overrideBackgroundColor = formatStyle?.backgroundColor,
                    overrideTextColor = formatStyle?.textColor,
                    overrideBorderColor = formatStyle?.borderColor
                )
                ColumnType.MULTILINE -> MultiLineTextCell(
                    value = value,
                    fieldType = column.type,
                    cellWidth = cellWidth,
                    cellHeight = cellHeight,
                    isRowSelected = isRowSelected,
                    onValueChange = onValueChange,
                    overrideBackgroundColor = formatStyle?.backgroundColor,
                    overrideTextColor = formatStyle?.textColor,
                    overrideBorderColor = formatStyle?.borderColor
                )
                ColumnType.JSON -> JsonCell(
                    value = value,
                    cellWidth = cellWidth,
                    cellHeight = cellHeight,
                    isRowSelected = isRowSelected,
                    onValueChange = onValueChange,
                    overrideBackgroundColor = formatStyle?.backgroundColor,
                    overrideTextColor = formatStyle?.textColor,
                    overrideBorderColor = formatStyle?.borderColor
                )
                ColumnType.FILE -> FileCell(
                    value = value,
                    cellWidth = cellWidth,
                    cellHeight = cellHeight,
                    isRowSelected = isRowSelected,
                    onValueChange = onValueChange,
                    overrideBackgroundColor = formatStyle?.backgroundColor,
                    overrideTextColor = formatStyle?.textColor,
                    overrideBorderColor = formatStyle?.borderColor
                )
                ColumnType.FORMULA -> FormulaCell(
                    value = value,
                    cellWidth = cellWidth,
                    cellHeight = cellHeight,
                    isRowSelected = isRowSelected,
                    onValueChange = onValueChange,
                    overrideBackgroundColor = formatStyle?.backgroundColor,
                    overrideTextColor = formatStyle?.textColor,
                    overrideBorderColor = formatStyle?.borderColor
                )
                FieldType.PRIORITY, ColumnType.PRIORITY -> {
                    val priorityOptions = PriorityOption.parseFromString(column.selectOptions)
                    PriorityCell(
                        value,
                        priorityOptions,
                        cellWidth,
                        cellHeight,
                        isRowSelected,
                        onValueChange,
                        overrideBackgroundColor = formatStyle?.backgroundColor,
                        overrideTextColor = formatStyle?.textColor,
                        overrideBorderColor = formatStyle?.borderColor
                    )
                }
                else -> TextInputCell(
                    value,
                    column.type,
                    cellWidth,
                    cellHeight,
                    isRowSelected,
                    onValueChange,
                    overrideBackgroundColor = formatStyle?.backgroundColor,
                    overrideTextColor = formatStyle?.textColor,
                    overrideBorderColor = formatStyle?.borderColor
                )
            }
        }
        
        // Context menu
        DropdownMenu(
            expanded = showContextMenu,
            onDismissRequest = { showContextMenu = false }
        ) {
            DropdownMenuItem(
                text = { Text("Copy Cell Value") },
                onClick = {
                    clipboardManager.setText(AnnotatedString(value))
                    showContextMenu = false
                    Toast.makeText(context, "Cell copied", Toast.LENGTH_SHORT).show()
                },
                leadingIcon = { Icon(Icons.Default.ContentCopy, null, tint = TableTheme.HEADER_BG) }
            )
            if (onPasteGridData != null) {
                DropdownMenuItem(
                    text = { Text("Paste Grid Data") },
                    onClick = {
                        pastedText = clipboardManager.getText()?.text.orEmpty()
                        pasteAsMultipleCells = true
                        showContextMenu = false
                        showPasteDialog = true
                    },
                    leadingIcon = { Icon(Icons.Default.ContentPaste, null, tint = TableTheme.HEADER_BG) }
                )
            }
            DropdownMenuItem(
                text = { Text("Fill Row Data") },
                onClick = { 
                    showContextMenu = false
                    onFillRowData?.invoke()
                },
                leadingIcon = { Icon(Icons.Default.Edit, null, tint = TableTheme.HEADER_BG) }
            )
        }

        if (showPasteDialog) {
            AlertDialog(
                onDismissRequest = { showPasteDialog = false },
                title = { Text("Paste Grid Data") },
                text = {
                    Column {
                        Text(
                            text = "Rows are parsed by new lines, and columns are parsed by tab, comma, or semicolon.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF6B7280)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = pastedText,
                            onValueChange = { pastedText = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 140.dp, max = 260.dp),
                            placeholder = { Text("Paste here...") },
                            minLines = 6
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Paste Mode",
                            style = MaterialTheme.typography.labelLarge,
                            color = Color(0xFF111827)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { pasteAsMultipleCells = true }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = pasteAsMultipleCells,
                                onClick = { pasteAsMultipleCells = true }
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Fill Multiple Cells")
                                Text(
                                    "Each line fills the next row (Google Sheets style).",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFF6B7280),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { pasteAsMultipleCells = false }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = !pasteAsMultipleCells,
                                onClick = { pasteAsMultipleCells = false }
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Paste in One Cell")
                                Text(
                                    "The full text stays in the current cell as-is.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFF6B7280),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            val input = pastedText.trimEnd('\n', '\r')
                            if (input.isNotEmpty()) {
                                if (pasteAsMultipleCells) {
                                    onPasteGridData?.invoke(input)
                                } else {
                                    onValueChange(input)
                                }
                            }
                            showPasteDialog = false
                        }
                    ) {
                        Text("Apply")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showPasteDialog = false }) { Text("Cancel") }
                }
            )
        }
    }
}
