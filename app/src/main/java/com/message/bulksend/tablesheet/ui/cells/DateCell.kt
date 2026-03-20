package com.message.bulksend.tablesheet.ui.cells

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val GRID_COLOR = Color(0xFFBDBDBD)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateCell(
    value: String, 
    cellWidth: Dp, 
    cellHeight: Dp, 
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