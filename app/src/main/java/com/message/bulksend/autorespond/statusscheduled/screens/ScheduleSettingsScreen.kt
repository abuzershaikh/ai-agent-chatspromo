package com.message.bulksend.autorespond.statusscheduled.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.message.bulksend.autorespond.statusscheduled.models.ScheduleType
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleSettingsScreen(
    scheduleType: ScheduleType,
    onScheduleTypeChanged: (ScheduleType) -> Unit,
    startDate: Long?,
    onStartDateChanged: (Long) -> Unit,
    time: String?,
    onTimeChanged: (String) -> Unit,
    amPm: String?,
    onAmPmChanged: (String) -> Unit,
    repeatDaily: Boolean,
    onRepeatDailyChanged: (Boolean) -> Unit,
    reminderMinutes: Int?,
    onReminderMinutesChanged: (Int?) -> Unit,
    onSave: () -> Unit,
    onBack: () -> Unit
) {
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var showReminderDialog by remember { mutableStateOf(false) }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .statusBarsPadding()
            .padding(top = 24.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // Header
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF10B981)),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Schedule Settings",
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Configure when to post this batch",
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 14.sp
                )
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Schedule Type Selection
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFF9FAFB)),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE5E7EB))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "Schedule Mode",
                    color = Color(0xFF111827),
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
                
                // Auto Schedule Option
                ScheduleTypeCard(
                    icon = Icons.Default.Autorenew,
                    title = "Auto Daily Batch",
                    description = "Post automatically every day at the same time",
                    isSelected = scheduleType == ScheduleType.AUTO,
                    onClick = { onScheduleTypeChanged(ScheduleType.AUTO) }
                )
                
                // Manual Schedule Option
                ScheduleTypeCard(
                    icon = Icons.Default.Schedule,
                    title = "Manual Schedule",
                    description = "Set specific date and time for this batch",
                    isSelected = scheduleType == ScheduleType.MANUAL,
                    onClick = { onScheduleTypeChanged(ScheduleType.MANUAL) }
                )
            }
        }
        
        Spacer(modifier = Modifier.height(12.dp))
        
        // Schedule Details
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFF9FAFB)),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE5E7EB))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "Schedule Details",
                    color = Color(0xFF111827),
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
                
                // Date Picker
                SettingItem(
                    icon = Icons.Default.CalendarToday,
                    label = "Start Date",
                    value = if (startDate != null) {
                        SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(startDate))
                    } else {
                        "Select date"
                    },
                    onClick = { showDatePicker = true }
                )
                
                // Time Picker
                SettingItem(
                    icon = Icons.Default.AccessTime,
                    label = "Time",
                    value = if (time != null && amPm != null) {
                        "$time $amPm"
                    } else {
                        "Select time"
                    },
                    onClick = { showTimePicker = true }
                )
                
                // Repeat Daily (Only for Auto mode)
                if (scheduleType == ScheduleType.AUTO) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE5E7EB))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Repeat,
                                    contentDescription = null,
                                    tint = Color(0xFF10B981),
                                    modifier = Modifier.size(24.dp)
                                )
                                Column {
                                    Text(
                                        text = "Repeat Daily",
                                        color = Color(0xFF111827),
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = "Post every day at this time",
                                        color = Color(0xFF6B7280),
                                        fontSize = 13.sp
                                    )
                                }
                            }
                            Switch(
                                checked = repeatDaily,
                                onCheckedChange = onRepeatDailyChanged,
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = Color(0xFF10B981)
                                )
                            )
                        }
                    }
                }
                
                // Reminder
                SettingItem(
                    icon = Icons.Default.Notifications,
                    label = "Reminder",
                    value = if (reminderMinutes != null) {
                        "$reminderMinutes min before"
                    } else {
                        "No reminder"
                    },
                    onClick = { showReminderDialog = true }
                )
            }
        }
        
        Spacer(modifier = Modifier.height(12.dp))
        
        // Warning for Manual Mode
        if (scheduleType == ScheduleType.MANUAL) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF3C7)),
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFDE68A))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = null,
                        tint = Color(0xFFF59E0B),
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = "Manual mode will apply to all batches. Mix mode is not allowed.",
                        color = Color(0xFF92400E),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Action Buttons
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = onBack,
                modifier = Modifier.weight(1f).height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = Color(0xFF10B981)
                ),
                border = androidx.compose.foundation.BorderStroke(2.dp, Color(0xFF10B981))
            ) {
                Text("Back", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
            
            Button(
                onClick = onSave,
                modifier = Modifier.weight(1f).height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                shape = RoundedCornerShape(14.dp),
                enabled = startDate != null && time != null && amPm != null,
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
            ) {
                Text("Save Batch", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
    }
    
    // Date Picker Dialog
    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = startDate ?: System.currentTimeMillis()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { onStartDateChanged(it) }
                        showDatePicker = false
                    }
                ) {
                    Text("OK", fontWeight = FontWeight.Bold, color = Color(0xFF10B981))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("Cancel", fontWeight = FontWeight.SemiBold)
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
    
    // Time Picker Dialog
    if (showTimePicker) {
        TimePickerDialog(
            onDismiss = { showTimePicker = false },
            onConfirm = { hour, minute, amPmValue ->
                val timeStr = String.format("%02d:%02d", hour, minute)
                onTimeChanged(timeStr)
                onAmPmChanged(amPmValue)
                showTimePicker = false
            }
        )
    }
    
    // Reminder Dialog
    if (showReminderDialog) {
        ReminderPickerDialog(
            currentReminder = reminderMinutes,
            onDismiss = { showReminderDialog = false },
            onConfirm = { minutes ->
                onReminderMinutesChanged(minutes)
                showReminderDialog = false
            }
        )
    }
}

@Composable
private fun ScheduleTypeCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) Color(0xFFD1FAE5) else Color.White
        ),
        shape = RoundedCornerShape(12.dp),
        onClick = onClick,
        border = androidx.compose.foundation.BorderStroke(
            width = if (isSelected) 2.dp else 1.dp,
            color = if (isSelected) Color(0xFF10B981) else Color(0xFFE5E7EB)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (isSelected) Color(0xFF10B981) else Color(0xFF6B7280),
                modifier = Modifier.size(32.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = Color(0xFF111827),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = description,
                    color = Color(0xFF6B7280),
                    fontSize = 13.sp
                )
            }
            RadioButton(
                selected = isSelected,
                onClick = onClick,
                colors = RadioButtonDefaults.colors(
                    selectedColor = Color(0xFF10B981)
                )
            )
        }
    }
}

@Composable
private fun SettingItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(12.dp),
        onClick = onClick,
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE5E7EB))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = Color(0xFF10B981),
                modifier = Modifier.size(24.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    color = Color(0xFF6B7280),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = value,
                    color = Color(0xFF111827),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = Color(0xFF9CA3AF)
            )
        }
    }
}

@Composable
private fun TimePickerDialog(
    onDismiss: () -> Unit,
    onConfirm: (Int, Int, String) -> Unit
) {
    var hour by remember { mutableStateOf(10) }
    var minute by remember { mutableStateOf(0) }
    var amPm by remember { mutableStateOf("AM") }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Time", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Hour
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        IconButton(onClick = { hour = if (hour == 12) 1 else hour + 1 }) {
                            Icon(Icons.Default.KeyboardArrowUp, contentDescription = null, tint = Color(0xFF10B981))
                        }
                        Text(String.format("%02d", hour), fontSize = 28.sp, fontWeight = FontWeight.Bold)
                        IconButton(onClick = { hour = if (hour == 1) 12 else hour - 1 }) {
                            Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, tint = Color(0xFF10B981))
                        }
                    }
                    
                    Text(":", fontSize = 28.sp, fontWeight = FontWeight.Bold)
                    
                    // Minute
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        IconButton(onClick = { minute = (minute + 1) % 60 }) {
                            Icon(Icons.Default.KeyboardArrowUp, contentDescription = null, tint = Color(0xFF10B981))
                        }
                        Text(String.format("%02d", minute), fontSize = 28.sp, fontWeight = FontWeight.Bold)
                        IconButton(onClick = { minute = if (minute == 0) 59 else minute - 1 }) {
                            Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, tint = Color(0xFF10B981))
                        }
                    }
                    
                    // AM/PM
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        IconButton(onClick = { amPm = if (amPm == "AM") "PM" else "AM" }) {
                            Icon(Icons.Default.KeyboardArrowUp, contentDescription = null, tint = Color(0xFF10B981))
                        }
                        Text(amPm, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                        IconButton(onClick = { amPm = if (amPm == "AM") "PM" else "AM" }) {
                            Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, tint = Color(0xFF10B981))
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(hour, minute, amPm) }) {
                Text("Set", fontWeight = FontWeight.Bold, color = Color(0xFF10B981))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", fontWeight = FontWeight.SemiBold)
            }
        }
    )
}

@Composable
private fun ReminderPickerDialog(
    currentReminder: Int?,
    onDismiss: () -> Unit,
    onConfirm: (Int?) -> Unit
) {
    val options = listOf(
        null to "No reminder",
        5 to "5 minutes before",
        10 to "10 minutes before",
        15 to "15 minutes before",
        30 to "30 minutes before",
        60 to "1 hour before"
    )
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Set Reminder", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                options.forEach { (minutes, label) ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = if (currentReminder == minutes) 
                                Color(0xFFD1FAE5) 
                            else Color.White
                        ),
                        onClick = { onConfirm(minutes) },
                        border = androidx.compose.foundation.BorderStroke(
                            width = if (currentReminder == minutes) 2.dp else 1.dp,
                            color = if (currentReminder == minutes) Color(0xFF10B981) else Color(0xFFE5E7EB)
                        ),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(label, fontWeight = FontWeight.Medium)
                            if (currentReminder == minutes) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = null,
                                    tint = Color(0xFF10B981)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", fontWeight = FontWeight.SemiBold)
            }
        }
    )
}
