package com.message.bulksend.tablesheet.ui.cells

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Schedule
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
fun TimeCell(
    value: String, 
    cellWidth: Dp, 
    cellHeight: Dp, 
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