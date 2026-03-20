package com.message.bulksend.tablesheet.ui.components

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.message.bulksend.tablesheet.data.models.ColumnModel
import com.message.bulksend.tablesheet.data.models.FieldType
import com.message.bulksend.tablesheet.data.models.RowModel
import com.message.bulksend.tablesheet.ui.components.cells.DataCell
import com.message.bulksend.tablesheet.ui.components.header.RowNumberCell
import com.message.bulksend.tablesheet.ui.theme.TableTheme

@Composable
fun TableDataRows(
    rows: List<RowModel>,
    columns: List<ColumnModel>,
    cellsMap: Map<Pair<Long, Long>, String>,
    rowHeight: Float,
    horizontalScrollState: ScrollState,
    listState: LazyListState,
    onCellValueChange: (rowId: Long, columnId: Long, value: String) -> Unit,
    onDeleteRow: (rowId: Long) -> Unit,
    onFillRowData: (rowId: Long) -> Unit,
    onAddRows: () -> Unit
) {
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(bottom = 80.dp) // Add padding for bottom toolbar
    ) {
        itemsIndexed(
            items = rows,
            key = { _, row -> row.id }
        ) { index, row ->
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
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .horizontalScroll(horizontalScrollState)
                ) {
                    columns.forEach { column ->
                        val cellValue = cellsMap[Pair(row.id, column.id)] ?: ""
                        DataCell(
                            value = cellValue,
                            column = column,
                            rowHeight = rowHeight,
                            isRowSelected = isSelected,
                            onValueChange = { onCellValueChange(row.id, column.id, it) },
                            onFillRowData = { onFillRowData(row.id) }
                        )
                    }
                    // Space matching header
                    Spacer(modifier = Modifier.width(142.dp))
                }
            }
        }
        
        // Add row button
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(TableTheme.CELL_HEIGHT)
                    .clickable { onAddRows() }
                    .background(Color(0xFFF5F5F5)),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .width(TableTheme.ROW_NUMBER_WIDTH)
                        .fillMaxHeight()
                        .background(
                            Color(0xFF4CAF50),
                            RoundedCornerShape(
                                topStart = 0.dp,
                                topEnd = 0.dp,
                                bottomEnd = 0.dp,
                                bottomStart = 12.dp
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Add,
                        "Add Row",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Text(
                    "Tap to add rows",
                    modifier = Modifier.padding(start = 12.dp),
                    color = Color.Gray,
                    fontSize = 14.sp
                )
            }
        }
        
        // Space after + icon
        item {
            Spacer(modifier = Modifier.height(96.dp))
        }
    }
}