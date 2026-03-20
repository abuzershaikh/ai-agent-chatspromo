package com.message.bulksend.tablesheet.ui.components.header

import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
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
    onDelete: () -> Unit,
    onClick: () -> Unit,
    onWidthChange: (Float) -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    val typeConfig = FieldTypes.getConfig(column.type)
    
    Box(
        modifier = Modifier
            .width((TableTheme.CELL_WIDTH.value * column.width).dp)
            .height(TableTheme.HEADER_HEIGHT)
            .background(TableTheme.HEADER_BG)
            .border(1.dp, TableTheme.GRID_COLOR)
            .clickable { onClick() }
            .pointerInput(Unit) { 
                detectTapGestures(onLongPress = { showMenu = true }) 
            },
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
        
        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false }
        ) {
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
fun TableColumnHeaders(
    columns: List<ColumnModel>,
    horizontalScrollState: ScrollState,
    onDeleteColumn: (Long) -> Unit,
    onEditColumn: (ColumnModel) -> Unit,
    onUpdateColumn: ((columnId: Long, name: String, type: String, width: Float, selectOptions: String?) -> Unit)?,
    onAddColumn: () -> Unit
) {
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
        
        Row(
            modifier = Modifier
                .weight(1f)
                .horizontalScroll(horizontalScrollState)
        ) {
            columns.forEach { column ->
                ColumnHeaderCell(
                    column = column,
                    onDelete = { onDeleteColumn(column.id) },
                    onClick = { onEditColumn(column) },
                    onWidthChange = { newWidth ->
                        onUpdateColumn?.invoke(
                            column.id,
                            column.name,
                            column.type,
                            newWidth,
                            column.selectOptions
                        )
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
                    .clickable { onAddColumn() },
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