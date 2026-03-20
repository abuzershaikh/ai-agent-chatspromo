package com.message.bulksend.components

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun ScheduleCampaignDialog(
    isVisible: Boolean,
    campaignName: String,
    onDismiss: () -> Unit,
    onSchedule: (Long) -> Unit
) {
    val context = LocalContext.current
    var selectedDate by remember { mutableStateOf<Calendar?>(null) }
    var selectedDateTime by remember { mutableStateOf<Long?>(null) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    
    if (isVisible) {
        AlertDialog(
            onDismissRequest = onDismiss,
            containerColor = Color.White,
            shape = RoundedCornerShape(20.dp),
            icon = {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFF9C27B0).copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.Schedule,
                        contentDescription = null,
                        tint = Color(0xFF9C27B0),
                        modifier = Modifier.size(28.dp)
                    )
                }
            },
            title = {
                Text(
                    "Schedule Campaign",
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    color = Color(0xFF1E293B)
                )
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        "Schedule \"$campaignName\" to run automatically at a specific date and time.",
                        fontSize = 14.sp,
                        color = Color(0xFF64748B),
                        lineHeight = 20.sp
                    )
                    
                    // Date Selection Card
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showDatePicker = true },
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFF9C27B0).copy(alpha = 0.05f)
                        ),
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp, 
                            if (selectedDate != null) Color(0xFF9C27B0) else Color(0xFFE2E8F0)
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .padding(16.dp)
                                .fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    "Select Date",
                                    fontSize = 12.sp,
                                    color = Color(0xFF64748B),
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    selectedDate?.let { 
                                        SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(it.time)
                                    } ?: "Choose date",
                                    fontSize = 16.sp,
                                    color = if (selectedDate != null) Color(0xFF1E293B) else Color(0xFF64748B),
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            Icon(
                                Icons.Filled.DateRange,
                                contentDescription = null,
                                tint = Color(0xFF9C27B0),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                    
                    // Time Selection Card
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { 
                                if (selectedDate != null) {
                                    showTimePicker = true
                                }
                            },
                        colors = CardDefaults.cardColors(
                            containerColor = if (selectedDate != null) 
                                Color(0xFF9C27B0).copy(alpha = 0.05f) 
                            else 
                                Color(0xFF64748B).copy(alpha = 0.05f)
                        ),
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp, 
                            if (selectedDateTime != null) Color(0xFF9C27B0) else Color(0xFFE2E8F0)
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .padding(16.dp)
                                .fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    "Select Time",
                                    fontSize = 12.sp,
                                    color = Color(0xFF64748B),
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    selectedDateTime?.let { 
                                        SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(it))
                                    } ?: if (selectedDate != null) "Choose time" else "Select date first",
                                    fontSize = 16.sp,
                                    color = if (selectedDateTime != null) Color(0xFF1E293B) else Color(0xFF64748B),
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            Icon(
                                Icons.Filled.AccessTime,
                                contentDescription = null,
                                tint = if (selectedDate != null) Color(0xFF9C27B0) else Color(0xFF64748B),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                    
                    if (selectedDateTime != null) {
                        val timeRemaining = getTimeRemaining(selectedDateTime!!)
                        if (timeRemaining.isNotEmpty()) {
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = Color(0xFF10B981).copy(alpha = 0.1f)
                                ),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Filled.Info,
                                        contentDescription = null,
                                        tint = Color(0xFF10B981),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        "Campaign will start $timeRemaining",
                                        fontSize = 12.sp,
                                        color = Color(0xFF10B981),
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        selectedDateTime?.let { onSchedule(it) }
                    },
                    enabled = selectedDateTime != null,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF9C27B0),
                        disabledContainerColor = Color(0xFF64748B)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        Icons.Filled.Schedule,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "Schedule Campaign",
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = onDismiss,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = Color(0xFF64748B)
                    )
                ) {
                    Text("Cancel")
                }
            }
        )
    }
    
    // Date Picker Dialog
    if (showDatePicker) {
        val calendar = Calendar.getInstance()
        DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                val newCalendar = Calendar.getInstance().apply {
                    set(Calendar.YEAR, year)
                    set(Calendar.MONTH, month)
                    set(Calendar.DAY_OF_MONTH, dayOfMonth)
                }
                selectedDate = newCalendar
                showDatePicker = false
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).apply {
            datePicker.minDate = System.currentTimeMillis()
            show()
        }
    }
    
    // Time Picker Dialog
    if (showTimePicker && selectedDate != null) {
        val calendar = Calendar.getInstance()
        TimePickerDialog(
            context,
            { _, hourOfDay, minute ->
                selectedDate?.let { date ->
                    date.set(Calendar.HOUR_OF_DAY, hourOfDay)
                    date.set(Calendar.MINUTE, minute)
                    date.set(Calendar.SECOND, 0)
                    selectedDateTime = date.timeInMillis
                }
                showTimePicker = false
            },
            calendar.get(Calendar.HOUR_OF_DAY),
            calendar.get(Calendar.MINUTE),
            false
        ).show()
    }
}

private fun getTimeRemaining(scheduledTime: Long): String {
    val now = System.currentTimeMillis()
    val diff = scheduledTime - now
    
    if (diff <= 0) return ""
    
    val days = diff / (24 * 60 * 60 * 1000)
    val hours = (diff % (24 * 60 * 60 * 1000)) / (60 * 60 * 1000)
    val minutes = (diff % (60 * 60 * 1000)) / (60 * 1000)
    
    return when {
        days > 0 -> "in ${days}d ${hours}h"
        hours > 0 -> "in ${hours}h ${minutes}m"
        else -> "in ${minutes}m"
    }
}