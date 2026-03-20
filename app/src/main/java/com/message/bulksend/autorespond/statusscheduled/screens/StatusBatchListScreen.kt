package com.message.bulksend.autorespond.statusscheduled.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.message.bulksend.autorespond.statusscheduled.models.BatchStatus
import com.message.bulksend.autorespond.statusscheduled.models.ScheduleType
import com.message.bulksend.autorespond.statusscheduled.models.StatusBatch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatusBatchListScreen(
    batches: List<StatusBatch>,
    onBatchClick: (StatusBatch) -> Unit,
    onDeleteBatch: (StatusBatch) -> Unit,
    onPostNow: (StatusBatch) -> Unit,
    onScheduleBatch: (StatusBatch) -> Unit,
    onCancelSchedule: (StatusBatch) -> Unit,
    onAddBatch: () -> Unit,
    canAddMore: Boolean
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(top = 24.dp)
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
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Schedule,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(26.dp)
                        )
                        Text(
                            text = "${batches.size} / 30 batches",
                            color = Color.White,
                            fontSize = 19.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    if (!canAddMore) {
                        Surface(
                            color = Color(0xFFFEE2E2),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = "Limit reached",
                                color = Color(0xFFDC2626),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }

            // Batch List
            if (batches.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 100.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Icon(
                            Icons.Default.Schedule,
                            contentDescription = null,
                            tint = Color(0xFF10B981),
                            modifier = Modifier.size(80.dp)
                        )
                        Text(
                            text = "No batches yet",
                            color = Color(0xFF111827),
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Use + button to create your first batch",
                            color = Color(0xFF6B7280),
                            fontSize = 15.sp
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 100.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    items(batches) { batch ->
                        BatchCard(
                            batch = batch,
                            onClick = { onBatchClick(batch) },
                            onDelete = { onDeleteBatch(batch) },
                            onPostNow = { onPostNow(batch) },
                            onSchedule = { onScheduleBatch(batch) },
                            onCancelSchedule = { onCancelSchedule(batch) }
                        )
                    }
                }
            }
        }

        if (canAddMore) {
            FloatingActionButton(
                onClick = onAddBatch,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(bottom = 28.dp, end = 24.dp),
                containerColor = Color(0xFF10B981),
                contentColor = Color.White,
                elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 8.dp)
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = "Add Batch",
                    modifier = Modifier.size(30.dp)
                )
            }
        }
    }
}

@Composable
private fun BatchCard(
    batch: StatusBatch,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onPostNow: () -> Unit,
    onSchedule: () -> Unit,
    onCancelSchedule: () -> Unit
) {
    var showDeleteDialog by remember { mutableStateOf(false) }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF9FAFB)),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        border = androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFFE5E7EB))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        when (batch.scheduleType) {
                            ScheduleType.AUTO -> Icons.Default.Autorenew
                            ScheduleType.MANUAL -> Icons.Default.Schedule
                        },
                        contentDescription = null,
                        tint = Color(0xFF10B981),
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = when (batch.scheduleType) {
                            ScheduleType.AUTO -> "Auto Daily"
                            ScheduleType.MANUAL -> "Manual"
                        },
                        color = Color(0xFF111827),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    // Status Badge
                    StatusBadge(status = batch.status)
                    
                    // Delete Button
                    IconButton(
                        onClick = { showDeleteDialog = true },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Delete",
                            tint = Color(0xFFEF4444),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
            
            // Media Count
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Image,
                    contentDescription = null,
                    tint = Color(0xFF6B7280),
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = "${batch.mediaList.size} media items",
                    color = Color(0xFF4B5563),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            }
            
            // Schedule Info
            if (batch.time != null) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.AccessTime,
                        contentDescription = null,
                        tint = Color(0xFF6B7280),
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "${batch.time} ${batch.amPm ?: ""}",
                        color = Color(0xFF4B5563),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
            
            // Date Info
            if (batch.startDate != null) {
                val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.CalendarToday,
                        contentDescription = null,
                        tint = Color(0xFF6B7280),
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = dateFormat.format(Date(batch.startDate)),
                        color = Color(0xFF4B5563),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
            
            // Reminder
            if (batch.reminderMinutes != null) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Notifications,
                        contentDescription = null,
                        tint = Color(0xFF6B7280),
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "Reminder: ${batch.reminderMinutes} min before",
                        color = Color(0xFF4B5563),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
            
            // Action Buttons
            if (batch.status == BatchStatus.DRAFT) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Post Now Button
                    Button(
                        onClick = onPostNow,
                        modifier = Modifier.weight(1f).height(48.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF10B981)
                        ),
                        shape = RoundedCornerShape(12.dp),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
                    ) {
                        Icon(
                            Icons.Default.Send,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("Post Now", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                    
                    // Schedule Button
                    Button(
                        onClick = onSchedule,
                        modifier = Modifier.weight(1f).height(48.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF059669)
                        ),
                        shape = RoundedCornerShape(12.dp),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
                    ) {
                        Icon(
                            Icons.Default.Schedule,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("Schedule", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                }
            } else if (batch.status == BatchStatus.SCHEDULED) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Post Now Button for scheduled batches too
                    Button(
                        onClick = onPostNow,
                        modifier = Modifier.weight(1f).height(48.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF10B981)
                        ),
                        shape = RoundedCornerShape(12.dp),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
                    ) {
                        Icon(
                            Icons.Default.Send,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("Post Now", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }

                    Button(
                        onClick = onCancelSchedule,
                        modifier = Modifier.weight(1f).height(48.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFEF4444)
                        ),
                        shape = RoundedCornerShape(12.dp),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
                    ) {
                        Icon(
                            Icons.Default.Cancel,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("Cancel", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                }
            } else if (batch.status == BatchStatus.FAILED) {
                Button(
                    onClick = onPostNow,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFF59E0B)
                    ),
                    shape = RoundedCornerShape(12.dp),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
                ) {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Resume From Failed Point", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
            }
        }
    }
    
    // Delete Confirmation Dialog
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Batch?", fontWeight = FontWeight.Bold) },
            text = { Text("This will delete all media in this batch. This action cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete()
                        showDeleteDialog = false
                    }
                ) {
                    Text("Delete", color = Color(0xFFEF4444), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel", fontWeight = FontWeight.SemiBold)
                }
            }
        )
    }
}

@Composable
private fun StatusBadge(status: BatchStatus) {
    val (color, text) = when (status) {
        BatchStatus.DRAFT -> Color(0xFF6B7280) to "Draft"
        BatchStatus.SCHEDULED -> Color(0xFF3B82F6) to "Scheduled"
        BatchStatus.POSTING -> Color(0xFFF59E0B) to "Posting"
        BatchStatus.POSTED -> Color(0xFF10B981) to "Posted"
        BatchStatus.FAILED -> Color(0xFFEF4444) to "Failed"
    }
    
    Surface(
        color = color.copy(alpha = 0.15f),
        shape = RoundedCornerShape(8.dp)
    ) {
        Text(
            text = text,
            color = color,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
        )
    }
}
