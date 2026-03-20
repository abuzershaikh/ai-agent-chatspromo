package com.message.bulksend.tablesheet.ui.components.cells

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import android.os.Environment
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.message.bulksend.tablesheet.data.models.ColumnModel
import com.message.bulksend.tablesheet.data.models.CurrencyHelper
import com.message.bulksend.tablesheet.ui.theme.TableTheme
import java.io.File

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
    
    val timestamp = value.toLongOrNull()
    val displayDate = if (timestamp != null) {
        val calendar = java.util.Calendar.getInstance().apply { timeInMillis = timestamp }
        String.format("%02d/%02d/%d", 
            calendar.get(java.util.Calendar.DAY_OF_MONTH),
            calendar.get(java.util.Calendar.MONTH) + 1,
            calendar.get(java.util.Calendar.YEAR))
    } else if (value.isNotEmpty()) {
        value
    } else {
        ""
    }
    
    val backgroundColor = when {
        showDatePicker -> Color(0xFFE3F2FD)
        isRowSelected -> Color(0xFFE3F2FD).copy(alpha = 0.7f)
        else -> Color.White
    }
    
    Box(
        modifier = Modifier
            .width(cellWidth)
            .height(cellHeight)
            .background(backgroundColor)
            .border(1.dp, if (showDatePicker) Color(0xFF2196F3) else TableTheme.GRID_COLOR)
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
        modifier = Modifier
            .width(cellWidth)
            .height(cellHeight)
            .background(backgroundColor)
            .border(1.dp, if (showTimePicker) Color(0xFF2196F3) else TableTheme.GRID_COLOR)
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

@Composable
fun AmountCell(
    value: String,
    column: ColumnModel,
    cellWidth: Dp,
    cellHeight: Dp,
    isRowSelected: Boolean = false,
    onValueChange: (String) -> Unit
) {
    var text by remember { mutableStateOf(value) }
    var isFocused by remember { mutableStateOf(false) }
    
    LaunchedEffect(value) { if (!isFocused) text = value }
    
    val currencyOptions = CurrencyHelper.parseCurrencyOptions(column.selectOptions)
    val symbol = currencyOptions?.second ?: "₹"
    val position = currencyOptions?.third ?: "left"

    val backgroundColor = when {
        isFocused -> Color(0xFFE3F2FD)
        isRowSelected -> Color(0xFFE3F2FD).copy(alpha = 0.7f)
        else -> Color.White
    }

    Box(
        modifier = Modifier
            .width(cellWidth)
            .height(cellHeight)
            .background(backgroundColor)
            .border(1.dp, if (isFocused) Color(0xFF2196F3) else TableTheme.GRID_COLOR),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (position == "left") {
                Text(
                    symbol,
                    color = Color(0xFFF59E0B),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.width(4.dp))
            }
            
            BasicTextField(
                value = text,
                onValueChange = { newValue ->
                    val filtered = newValue.filter { it.isDigit() || it == '.' }
                    text = filtered
                    onValueChange(filtered)
                },
                textStyle = TextStyle(
                    color = Color(0xFF333333),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                ),
                cursorBrush = SolidColor(Color(0xFF2196F3)),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier
                    .weight(1f)
                    .onFocusChanged { isFocused = it.isFocused }
            )
            
            if (position == "right") {
                Spacer(Modifier.width(4.dp))
                Text(
                    symbol,
                    color = Color(0xFFF59E0B),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}