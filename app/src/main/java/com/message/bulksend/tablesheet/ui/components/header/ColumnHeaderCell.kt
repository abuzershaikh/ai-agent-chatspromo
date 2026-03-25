package com.message.bulksend.tablesheet.ui.components.header

import androidx.compose.foundation.*
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.message.bulksend.tablesheet.data.models.ColumnModel
import com.message.bulksend.tablesheet.data.models.FieldTypes
import com.message.bulksend.tablesheet.ui.theme.TableTheme

@Composable
fun ColumnHeaderCell(
    column: ColumnModel,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onDelete: () -> Unit,
    onEdit: () -> Unit,
    onWidthPreviewChange: (Float) -> Unit,
    onWidthChangeCommitted: (Float) -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    var dragWidth by remember(column.id) { mutableFloatStateOf(column.width) }
    val typeConfig = FieldTypes.getConfig(column.type)
    val baseCellWidthPx = with(LocalDensity.current) { TableTheme.CELL_WIDTH.toPx() }

    LaunchedEffect(column.width) {
        dragWidth = column.width
    }

    Box(
        modifier = Modifier
            .width((TableTheme.CELL_WIDTH.value * column.width).dp)
            .height(TableTheme.HEADER_HEIGHT)
            .background(TableTheme.HEADER_BG)
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) Color.White else TableTheme.GRID_COLOR
            )
            .combinedClickable(
                onClick = onSelect,
                onLongClick = { showMenu = true }
            ),
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

        if (isSelected) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .width(2.dp)
                    .background(Color.White.copy(alpha = 0.95f))
            )

            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .offset(x = 9.dp)
                    .width(18.dp)
                    .height(34.dp)
                    .background(
                        color = Color.White,
                        shape = RoundedCornerShape(9.dp)
                    )
                    .border(1.dp, Color(0xFF1976D2), RoundedCornerShape(9.dp))
                    .pointerInput(column.id, baseCellWidthPx) {
                        detectDragGestures(
                            onDragStart = {
                                onSelect()
                                dragWidth = column.width
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                val deltaWidth = dragAmount.x / baseCellWidthPx
                                dragWidth = (dragWidth + deltaWidth)
                                    .coerceIn(MIN_WIDTH_FACTOR, MAX_WIDTH_FACTOR)
                                onWidthPreviewChange(dragWidth)
                            },
                            onDragEnd = {
                                onWidthChangeCommitted(dragWidth)
                            },
                            onDragCancel = {
                                onWidthChangeCommitted(dragWidth)
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .height(18.dp)
                        .background(Color(0xFF1976D2), RoundedCornerShape(2.dp))
                )
            }
        }

        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false }
        ) {
            DropdownMenuItem(
                text = { Text("Edit Column") },
                onClick = { showMenu = false; onEdit() },
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
fun TableColumnHeaders(
    columns: List<ColumnModel>,
    frozenColumnCount: Int,
    horizontalScrollState: ScrollState,
    onDeleteColumn: (Long) -> Unit,
    onEditColumn: (ColumnModel) -> Unit,
    onUpdateColumn: ((columnId: Long, name: String, type: String, width: Float, selectOptions: String?) -> Unit)?,
    onPreviewColumnWidthChange: ((columnId: Long, width: Float) -> Unit)? = null,
    onCommitColumnWidthChange: ((columnId: Long, width: Float) -> Unit)? = null,
    onAddColumn: () -> Unit
) {
    val frozenCount = frozenColumnCount.coerceIn(0, columns.size)
    val frozenColumns = columns.take(frozenCount)
    val scrollableColumns = columns.drop(frozenCount)
    var selectedColumnId by remember { mutableStateOf<Long?>(null) }

    LaunchedEffect(columns) {
        val existingIds = columns.map { it.id }.toSet()
        if (selectedColumnId != null && selectedColumnId !in existingIds) {
            selectedColumnId = null
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(TableTheme.HEADER_BG)
            .height(TableTheme.HEADER_HEIGHT)
    ) {
        Box(
            modifier = Modifier
                .width(TableTheme.ROW_NUMBER_WIDTH)
                .fillMaxHeight()
                .border(1.dp, TableTheme.GRID_COLOR),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.GridOn,
                null,
                tint = Color.White,
                modifier = Modifier.size(18.dp)
            )
        }

        frozenColumns.forEach { column ->
            ColumnHeaderCell(
                column = column,
                isSelected = selectedColumnId == column.id,
                onSelect = { selectedColumnId = column.id },
                onDelete = { onDeleteColumn(column.id) },
                onEdit = { onEditColumn(column) },
                onWidthPreviewChange = { newWidth ->
                    onPreviewColumnWidthChange?.invoke(column.id, newWidth)
                },
                onWidthChangeCommitted = { newWidth ->
                    onCommitColumnWidthChange?.invoke(column.id, newWidth)
                    if (onCommitColumnWidthChange == null) {
                        onUpdateColumn?.invoke(
                            column.id,
                            column.name,
                            column.type,
                            newWidth,
                            column.selectOptions
                        )
                    }
                }
            )
        }

        Row(
            modifier = Modifier
                .weight(1f)
                .horizontalScroll(horizontalScrollState)
        ) {
            scrollableColumns.forEach { column ->
                ColumnHeaderCell(
                    column = column,
                    isSelected = selectedColumnId == column.id,
                    onSelect = { selectedColumnId = column.id },
                    onDelete = { onDeleteColumn(column.id) },
                    onEdit = { onEditColumn(column) },
                    onWidthPreviewChange = { newWidth ->
                        onPreviewColumnWidthChange?.invoke(column.id, newWidth)
                    },
                    onWidthChangeCommitted = { newWidth ->
                        onCommitColumnWidthChange?.invoke(column.id, newWidth)
                        if (onCommitColumnWidthChange == null) {
                            onUpdateColumn?.invoke(
                                column.id,
                                column.name,
                                column.type,
                                newWidth,
                                column.selectOptions
                            )
                        }
                    }
                )
            }
            
            // Add column button
            Box(
                modifier = Modifier
                    .width(46.dp)
                    .fillMaxHeight()
                    .background(
                        Color(0xFF4CAF50),
                        RoundedCornerShape(
                            topStart = 0.dp,
                            topEnd = 12.dp,
                            bottomEnd = 0.dp,
                            bottomStart = 0.dp
                        )
                    )
                    .clickable {
                        selectedColumnId = null
                        onAddColumn()
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Add,
                    "Add Column",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
            
            // Space after + icon
            Spacer(modifier = Modifier.width(96.dp))
        }
    }
}

private const val MIN_WIDTH_FACTOR = 0.6f
private const val MAX_WIDTH_FACTOR = 6.0f
