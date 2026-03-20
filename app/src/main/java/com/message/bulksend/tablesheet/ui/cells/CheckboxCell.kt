package com.message.bulksend.tablesheet.ui.cells

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private val GRID_COLOR = Color(0xFFBDBDBD)

@Composable
fun CheckboxCell(
    value: String, 
    cellWidth: Dp, 
    cellHeight: Dp, 
    isRowSelected: Boolean = false,
    onValueChange: (String) -> Unit
) {
    val isChecked = value.equals("true", ignoreCase = true)
    
    Box(
        modifier = Modifier.width(cellWidth).height(cellHeight)
            .background(if (isRowSelected) Color(0xFFE3F2FD).copy(alpha = 0.7f) else Color.White)
            .border(1.dp, GRID_COLOR)
            .clickable { onValueChange(if (isChecked) "false" else "true") },
        contentAlignment = Alignment.Center
    ) {
        Checkbox(
            checked = isChecked,
            onCheckedChange = { onValueChange(if (it) "true" else "false") },
            colors = CheckboxDefaults.colors(
                checkedColor = Color(0xFF4CAF50),
                uncheckedColor = Color.Gray
            )
        )
    }
}