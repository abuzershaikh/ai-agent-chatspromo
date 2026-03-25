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
    onValueChange: (String) -> Unit,
    overrideBackgroundColor: Color? = null,
    overrideTextColor: Color? = null,
    overrideBorderColor: Color? = null
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
        overrideBackgroundColor != null -> overrideBackgroundColor
        showDatePicker -> Color(0xFFE3F2FD)
        isRowSelected -> Color(0xFFE3F2FD).copy(alpha = 0.7f)
        else -> Color.White
    }
    
    Box(
        modifier = Modifier
            .width(cellWidth)
            .height(cellHeight)
            .background(backgroundColor)
            .border(
                1.dp,
                when {
                    showDatePicker -> Color(0xFF2196F3)
                    overrideBorderColor != null -> overrideBorderColor
                    else -> TableTheme.GRID_COLOR
                }
            )
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
                color = overrideTextColor ?: Color(0xFF333333),
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
fun DateTimeCell(
    value: String,
    cellWidth: Dp,
    cellHeight: Dp,
    isRowSelected: Boolean = false,
    onValueChange: (String) -> Unit,
    overrideBackgroundColor: Color? = null,
    overrideTextColor: Color? = null,
    overrideBorderColor: Color? = null
) {
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var pendingDateMillis by remember { mutableStateOf<Long?>(null) }

    val timestamp = value.toLongOrNull() ?: parseFlexibleDateTime(value)
    val displayValue =
        if (timestamp != null) {
            java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale.getDefault())
                .format(java.util.Date(timestamp))
        } else {
            value
        }

    val backgroundColor = when {
        overrideBackgroundColor != null -> overrideBackgroundColor
        showDatePicker || showTimePicker -> Color(0xFFE3F2FD)
        isRowSelected -> Color(0xFFE3F2FD).copy(alpha = 0.7f)
        else -> Color.White
    }

    Box(
        modifier = Modifier
            .width(cellWidth)
            .height(cellHeight)
            .background(backgroundColor)
            .border(
                1.dp,
                when {
                    showDatePicker || showTimePicker -> Color(0xFF2196F3)
                    overrideBorderColor != null -> overrideBorderColor
                    else -> TableTheme.GRID_COLOR
                }
            )
            .clickable { showDatePicker = true },
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = displayValue,
                color = overrideTextColor ?: Color(0xFF333333),
                fontSize = 12.sp,
                maxLines = 1,
                modifier = Modifier.weight(1f)
            )
            if (displayValue.isBlank()) {
                Icon(
                    Icons.Default.Event,
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
                    pendingDateMillis = datePickerState.selectedDateMillis ?: System.currentTimeMillis()
                    showDatePicker = false
                    showTimePicker = true
                }) { Text("Next") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showTimePicker) {
        val seed = pendingDateMillis ?: timestamp ?: System.currentTimeMillis()
        val calendar = java.util.Calendar.getInstance().apply { timeInMillis = seed }
        val timePickerState = rememberTimePickerState(
            initialHour = calendar.get(java.util.Calendar.HOUR_OF_DAY),
            initialMinute = calendar.get(java.util.Calendar.MINUTE),
            is24Hour = true
        )
        AlertDialog(
            onDismissRequest = {
                showTimePicker = false
                pendingDateMillis = null
            },
            title = { Text("Select Time") },
            text = { TimePicker(state = timePickerState) },
            confirmButton = {
                TextButton(onClick = {
                    val baseDate = pendingDateMillis ?: System.currentTimeMillis()
                    val merged = java.util.Calendar.getInstance().apply {
                        timeInMillis = baseDate
                        set(java.util.Calendar.HOUR_OF_DAY, timePickerState.hour)
                        set(java.util.Calendar.MINUTE, timePickerState.minute)
                        set(java.util.Calendar.SECOND, 0)
                        set(java.util.Calendar.MILLISECOND, 0)
                    }
                    onValueChange(merged.timeInMillis.toString())
                    showTimePicker = false
                    pendingDateMillis = null
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showTimePicker = false
                    pendingDateMillis = null
                }) { Text("Cancel") }
            }
        )
    }
}

private fun parseFlexibleDateTime(raw: String): Long? {
    val token = raw.trim()
    if (token.isBlank()) return null
    val patterns =
        listOf(
            "yyyy-MM-dd HH:mm:ss",
            "yyyy-MM-dd HH:mm",
            "dd/MM/yyyy HH:mm",
            "MM/dd/yyyy HH:mm",
            "yyyy-MM-dd'T'HH:mm:ss",
            "yyyy-MM-dd'T'HH:mm"
        )
    patterns.forEach { pattern ->
        runCatching {
            java.text.SimpleDateFormat(pattern, java.util.Locale.getDefault()).apply {
                isLenient = false
            }.parse(token)
        }.getOrNull()?.let { parsed ->
            return parsed.time
        }
    }
    return null
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeCell(
    value: String,
    cellWidth: Dp,
    cellHeight: Dp,
    isRowSelected: Boolean = false,
    onValueChange: (String) -> Unit,
    overrideBackgroundColor: Color? = null,
    overrideTextColor: Color? = null,
    overrideBorderColor: Color? = null
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
        overrideBackgroundColor != null -> overrideBackgroundColor
        showTimePicker -> Color(0xFFE3F2FD)
        isRowSelected -> Color(0xFFE3F2FD).copy(alpha = 0.7f)
        else -> Color.White
    }
    
    Box(
        modifier = Modifier
            .width(cellWidth)
            .height(cellHeight)
            .background(backgroundColor)
            .border(
                1.dp,
                when {
                    showTimePicker -> Color(0xFF2196F3)
                    overrideBorderColor != null -> overrideBorderColor
                    else -> TableTheme.GRID_COLOR
                }
            )
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
                color = overrideTextColor ?: Color(0xFF333333),
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
    onValueChange: (String) -> Unit,
    overrideBackgroundColor: Color? = null,
    overrideTextColor: Color? = null,
    overrideBorderColor: Color? = null
) {
    var text by remember { mutableStateOf(value) }
    var isFocused by remember { mutableStateOf(false) }
    
    LaunchedEffect(value) { if (!isFocused) text = value }
    
    val currencyOptions = CurrencyHelper.parseCurrencyOptions(column.selectOptions)
    val symbol = currencyOptions?.second ?: "₹"
    val position = currencyOptions?.third ?: "left"

    val backgroundColor = when {
        overrideBackgroundColor != null -> overrideBackgroundColor
        isFocused -> Color(0xFFE3F2FD)
        isRowSelected -> Color(0xFFE3F2FD).copy(alpha = 0.7f)
        else -> Color.White
    }

    Box(
        modifier = Modifier
            .width(cellWidth)
            .height(cellHeight)
            .background(backgroundColor)
            .border(
                1.dp,
                when {
                    isFocused -> Color(0xFF2196F3)
                    overrideBorderColor != null -> overrideBorderColor
                    else -> TableTheme.GRID_COLOR
                }
            ),
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
                    color = overrideTextColor ?: Color(0xFF333333),
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
