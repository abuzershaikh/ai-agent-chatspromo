package com.message.bulksend.tablesheet.ui.cells

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.message.bulksend.tablesheet.data.models.ColumnModel

private val GRID_COLOR = Color(0xFFBDBDBD)

@Composable
fun SelectCell(
    value: String, 
    column: ColumnModel, 
    cellWidth: Dp, 
    cellHeight: Dp, 
    isRowSelected: Boolean = false,
    onValueChange: (String) -> Unit
) {
    var showDropdown by remember { mutableStateOf(false) }
    val options = column.selectOptions?.split(",")?.filter { it.isNotBlank() } ?: emptyList()
    
    val backgroundColor = when {
        showDropdown -> Color(0xFFE3F2FD)
        isRowSelected -> Color(0xFFE3F2FD).copy(alpha = 0.7f)
        else -> Color.White
    }
    
    Box(
        modifier = Modifier.width(cellWidth).height(cellHeight)
            .background(backgroundColor)
            .border(1.dp, if (showDropdown) Color(0xFF2196F3) else GRID_COLOR)
            .clickable { showDropdown = true },
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = value,
                color = Color(0xFF333333),
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Icon(
                Icons.Default.ArrowDropDown,
                contentDescription = null,
                tint = Color.Gray,
                modifier = Modifier.size(20.dp)
            )
        }
        
        DropdownMenu(
            expanded = showDropdown,
            onDismissRequest = { showDropdown = false }
        ) {
            if (options.isEmpty()) {
                DropdownMenuItem(
                    text = { Text("No options available", color = Color.Gray) },
                    onClick = { showDropdown = false }
                )
            } else {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { 
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (value == option) {
                                    Icon(Icons.Default.Check, null, tint = Color(0xFF4CAF50), modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                }
                                Text(option)
                            }
                        },
                        onClick = { 
                            onValueChange(option)
                            showDropdown = false 
                        }
                    )
                }
            }
            // Clear option
            if (value.isNotEmpty()) {
                HorizontalDivider()
                DropdownMenuItem(
                    text = { Text("Clear", color = Color(0xFFEF4444)) },
                    onClick = { 
                        onValueChange("")
                        showDropdown = false 
                    },
                    leadingIcon = { Icon(Icons.Default.Clear, null, tint = Color(0xFFEF4444), modifier = Modifier.size(18.dp)) }
                )
            }
        }
    }
}