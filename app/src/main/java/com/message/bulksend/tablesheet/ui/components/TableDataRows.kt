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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.message.bulksend.tablesheet.data.models.ColumnModel
import com.message.bulksend.tablesheet.data.models.RowModel
import com.message.bulksend.tablesheet.ui.components.cells.CellFormatStyle
import com.message.bulksend.tablesheet.ui.components.cells.DataCell
import com.message.bulksend.tablesheet.ui.components.header.RowNumberCell
import com.message.bulksend.tablesheet.ui.theme.TableTheme

@Composable
fun TableDataRows(
    rows: List<RowModel>,
    columns: List<ColumnModel>,
    frozenColumnCount: Int,
    cellsMap: Map<Pair<Long, Long>, String>,
    cellStyles: Map<Pair<Long, Long>, CellFormatStyle>,
    rowHeight: Float,
    horizontalScrollState: ScrollState,
    listState: LazyListState,
    onCellValueChange: (rowId: Long, columnId: Long, value: String) -> Unit,
    onDeleteRow: (rowId: Long) -> Unit,
    onFillRowData: (rowId: Long) -> Unit,
    onPasteGridData: (startRowId: Long, startColumnId: Long, rawText: String) -> Unit,
    onAddRows: () -> Unit
) {
    val frozenCount = frozenColumnCount.coerceIn(0, columns.size)
    val frozenColumns = columns.take(frozenCount)
    val scrollableColumns = columns.drop(frozenCount)
    val density = LocalDensity.current
    val baseCellWidthPx = with(density) { TableTheme.CELL_WIDTH.toPx() }
    val renderBufferPx = with(density) { 360.dp.toPx() }
    var scrollViewportWidthPx by remember { mutableIntStateOf(0) }

    val scrollablePrefixWidthsPx by remember(scrollableColumns, baseCellWidthPx) {
        derivedStateOf {
            val prefix = FloatArray(scrollableColumns.size + 1)
            var running = 0f
            scrollableColumns.forEachIndexed { index, column ->
                prefix[index] = running
                running += column.width * baseCellWidthPx
            }
            prefix[scrollableColumns.size] = running
            prefix
        }
    }

    val visibleColumnWindow by remember(
        scrollableColumns,
        scrollablePrefixWidthsPx,
        horizontalScrollState.value,
        scrollViewportWidthPx,
        renderBufferPx
    ) {
        derivedStateOf {
            computeVisibleColumnWindow(
                prefixWidthsPx = scrollablePrefixWidthsPx,
                scrollX = horizontalScrollState.value.toFloat(),
                viewportWidthPx = scrollViewportWidthPx.toFloat(),
                bufferPx = renderBufferPx,
                density = density
            )
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(bottom = 80.dp) // Add padding for bottom toolbar
    ) {
        itemsIndexed(
            items = rows,
            key = { _, row -> row.id },
            contentType = { _, _ -> "table_row" }
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

                frozenColumns.forEach { column ->
                    val key = Pair(row.id, column.id)
                    val cellValue = cellsMap[key] ?: ""
                    DataCell(
                        value = cellValue,
                        column = column,
                        rowHeight = rowHeight,
                        isRowSelected = isSelected,
                        formatStyle = cellStyles[key],
                        onValueChange = { onCellValueChange(row.id, column.id, it) },
                        onFillRowData = { onFillRowData(row.id) },
                        onPasteGridData = { rawText -> onPasteGridData(row.id, column.id, rawText) }
                    )
                }
                
                // Data cells
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .onSizeChanged { size ->
                            if (scrollViewportWidthPx != size.width) {
                                scrollViewportWidthPx = size.width
                            }
                        }
                        .horizontalScroll(horizontalScrollState)
                ) {
                    if (visibleColumnWindow.leadingSpacerDp > 0.dp) {
                        Spacer(modifier = Modifier.width(visibleColumnWindow.leadingSpacerDp))
                    }

                    for (columnIndex in visibleColumnWindow.startIndex until visibleColumnWindow.endExclusive) {
                        val column = scrollableColumns[columnIndex]
                        val key = Pair(row.id, column.id)
                        val cellValue = cellsMap[key] ?: ""
                        DataCell(
                            value = cellValue,
                            column = column,
                            rowHeight = rowHeight,
                            isRowSelected = isSelected,
                            formatStyle = cellStyles[key],
                            onValueChange = { onCellValueChange(row.id, column.id, it) },
                            onFillRowData = { onFillRowData(row.id) },
                            onPasteGridData = { rawText -> onPasteGridData(row.id, column.id, rawText) }
                        )
                    }

                    if (visibleColumnWindow.trailingSpacerDp > 0.dp) {
                        Spacer(modifier = Modifier.width(visibleColumnWindow.trailingSpacerDp))
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

private data class ColumnWindow(
    val startIndex: Int,
    val endExclusive: Int,
    val leadingSpacerDp: Dp,
    val trailingSpacerDp: Dp
)

private fun computeVisibleColumnWindow(
    prefixWidthsPx: FloatArray,
    scrollX: Float,
    viewportWidthPx: Float,
    bufferPx: Float,
    density: Density
): ColumnWindow {
    val columnCount = prefixWidthsPx.size - 1
    if (columnCount <= 0) {
        return ColumnWindow(0, 0, 0.dp, 0.dp)
    }

    val totalWidthPx = prefixWidthsPx[columnCount]
    val safeViewport = if (viewportWidthPx > 0f) viewportWidthPx else totalWidthPx.coerceAtMost(bufferPx)
    val visibleStartPx = (scrollX - bufferPx).coerceAtLeast(0f)
    val visibleEndPx = (scrollX + safeViewport + bufferPx).coerceAtMost(totalWidthPx)

    var startIndex = 0
    while (startIndex < columnCount && prefixWidthsPx[startIndex + 1] < visibleStartPx) {
        startIndex += 1
    }

    var endExclusive = startIndex
    while (endExclusive < columnCount && prefixWidthsPx[endExclusive] <= visibleEndPx) {
        endExclusive += 1
    }

    if (endExclusive <= startIndex) {
        endExclusive = (startIndex + 1).coerceAtMost(columnCount)
    }

    val leadingPx = prefixWidthsPx[startIndex]
    val trailingPx = (totalWidthPx - prefixWidthsPx[endExclusive]).coerceAtLeast(0f)

    val leadingDp = with(density) { leadingPx.toDp() }
    val trailingDp = with(density) { trailingPx.toDp() }

    return ColumnWindow(
        startIndex = startIndex,
        endExclusive = endExclusive,
        leadingSpacerDp = leadingDp,
        trailingSpacerDp = trailingDp
    )
}
