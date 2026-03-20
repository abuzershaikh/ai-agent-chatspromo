package com.message.bulksend.leadmanager.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.message.bulksend.leadmanager.model.Lead
import com.message.bulksend.leadmanager.model.LeadStatus
import com.message.bulksend.leadmanager.utils.LeadExportHelper
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun LeadsListScreen(
    leads: List<Lead>,
    onLeadClick: (Lead) -> Unit,
    onStatusChange: (Lead, LeadStatus) -> Unit = { _, _ -> },
    onDeleteLead: (Lead) -> Unit = {}
) {
    var showExportMenu by remember { mutableStateOf(false) }
    var isExporting by remember { mutableStateOf(false) }
    
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Header with Export Button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "All Leads (${leads.size})",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            
            // Export Button
            if (leads.isNotEmpty()) {
                Box {
                    IconButton(
                        onClick = { showExportMenu = true },
                        enabled = !isExporting
                    ) {
                        if (isExporting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = Color(0xFF10B981)
                            )
                        } else {
                            Icon(
                                Icons.Default.FileDownload,
                                contentDescription = "Export",
                                tint = Color(0xFF10B981),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                    
                    DropdownMenu(
                        expanded = showExportMenu,
                        onDismissRequest = { showExportMenu = false }
                    ) {
                        Text(
                            "Export All Leads",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF64748B),
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                        
                        DropdownMenuItem(
                            text = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        Icons.Default.TableChart,
                                        contentDescription = null,
                                        tint = Color(0xFF10B981),
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Text("Export as CSV")
                                }
                            },
                            onClick = {
                                showExportMenu = false
                                coroutineScope.launch {
                                    isExporting = true
                                    withContext(Dispatchers.IO) {
                                        val fileName = LeadExportHelper.generateFileName("All_Leads")
                                        val filePath = LeadExportHelper.exportToCSV(
                                            context = context,
                                            leads = leads,
                                            fileName = fileName
                                        )
                                        
                                        withContext(Dispatchers.Main) {
                                            isExporting = false
                                            if (filePath != null) {
                                                Toast.makeText(
                                                    context,
                                                    "CSV exported to $filePath",
                                                    Toast.LENGTH_LONG
                                                ).show()
                                            } else {
                                                Toast.makeText(
                                                    context,
                                                    "Failed to export CSV",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            }
                                        }
                                    }
                                }
                            }
                        )
                        
                        DropdownMenuItem(
                            text = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Description,
                                        contentDescription = null,
                                        tint = Color(0xFF3B82F6),
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Text("Export as Excel")
                                }
                            },
                            onClick = {
                                showExportMenu = false
                                coroutineScope.launch {
                                    isExporting = true
                                    withContext(Dispatchers.IO) {
                                        val fileName = LeadExportHelper.generateFileName("All_Leads")
                                        val filePath = LeadExportHelper.exportToExcel(
                                            context = context,
                                            leads = leads,
                                            fileName = fileName
                                        )
                                        
                                        withContext(Dispatchers.Main) {
                                            isExporting = false
                                            if (filePath != null) {
                                                Toast.makeText(
                                                    context,
                                                    "Excel exported to $filePath",
                                                    Toast.LENGTH_LONG
                                                ).show()
                                            } else {
                                                Toast.makeText(
                                                    context,
                                                    "Failed to export Excel",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            }
                                        }
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
        
        if (leads.isEmpty()) {
            EmptyLeadsState()
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(leads, key = { it.id }) { lead ->
                    AdvancedLeadCard(
                        lead = lead,
                        onClick = { onLeadClick(lead) },
                        onStatusChange = { newStatus -> onStatusChange(lead, newStatus) },
                        onDelete = { onDeleteLead(lead) }
                    )
                }
                item { Spacer(Modifier.height(80.dp)) }
            }
        }
    }
}

@Composable
fun AdvancedLeadCard(
    lead: Lead,
    onClick: () -> Unit,
    onStatusChange: (LeadStatus) -> Unit = {},
    onDelete: () -> Unit = {}
) {
    val context = LocalContext.current
    val statusColor = Color(lead.status.color)
    val scoreProgress = lead.leadScore / 100f
    var showStatusMenu by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = statusColor),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            statusColor,
                            statusColor.copy(alpha = 0.85f),
                            statusColor.copy(alpha = 0.7f)
                        ),
                        start = Offset(0f, 0f),
                        end = Offset(400f, 150f)
                    )
                )
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Header with Lead Score + Quick Actions
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Lead Score with progress and real emoji
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Real keyboard emoji based on lead score
                        Text(
                            text = getScoreTextEmoji(lead.leadScore),
                            fontSize = 16.sp
                        )
                        Text(
                            "Score",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.White.copy(alpha = 0.9f)
                        )
                        Box(
                            modifier = Modifier
                                .width(50.dp)
                                .height(5.dp)
                                .background(Color.White.copy(alpha = 0.3f), RoundedCornerShape(3.dp))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth(scoreProgress)
                                    .background(Color.White, RoundedCornerShape(3.dp))
                            )
                        }
                        Text(
                            "${lead.leadScore}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                    
                    // Quick Action Buttons
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Call
                        QuickActionButton(
                            icon = Icons.Default.Phone,
                            color = Color(0xFF10B981),
                            onClick = {
                                context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${lead.phoneNumber}")))
                            }
                        )
                        // WhatsApp
                        QuickActionButton(
                            icon = Icons.Default.Chat,
                            color = Color(0xFF25D366),
                            onClick = {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/${lead.phoneNumber}")))
                            }
                        )
                        // SMS
                        QuickActionButton(
                            icon = Icons.Default.Sms,
                            color = Color(0xFFF59E0B),
                            onClick = {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("sms:${lead.phoneNumber}")))
                            }
                        )
                    }
                }
                
                // White inner card
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 10.dp, end = 10.dp, bottom = 10.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Avatar
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .background(statusColor.copy(alpha = 0.15f), RoundedCornerShape(10.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                lead.name.take(2).uppercase(),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = statusColor
                            )
                        }
                        
                        // Lead info
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                lead.name,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1a1a2e),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                lead.phoneNumber,
                                fontSize = 12.sp,
                                color = Color(0xFF64748B)
                            )
                            if (lead.lastMessage.isNotEmpty()) {
                                Text(
                                    lead.lastMessage,
                                    fontSize = 11.sp,
                                    color = Color(0xFF94A3B8),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        
                        // Right side - Priority + Menu
                        Column(
                            horizontalAlignment = Alignment.End,
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            // Priority badge
                            Surface(
                                color = Color(lead.priority.color).copy(alpha = 0.15f),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    lead.priority.displayName,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(lead.priority.color),
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                            
                            // Time
                            Text(
                                SimpleDateFormat("MMM dd", Locale.getDefault()).format(Date(lead.timestamp)),
                                fontSize = 10.sp,
                                color = Color(0xFF94A3B8)
                            )
                        }
                        
                        // More options menu
                        Box {
                            IconButton(
                                onClick = { showStatusMenu = true },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    Icons.Default.MoreVert,
                                    contentDescription = "Options",
                                    tint = Color(0xFF64748B),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            
                            DropdownMenu(
                                expanded = showStatusMenu,
                                onDismissRequest = { showStatusMenu = false }
                            ) {
                                Text(
                                    "Move to Status",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF64748B),
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                )
                                
                                LeadStatus.entries.forEach { status ->
                                    val isCurrentStatus = status == lead.status
                                    DropdownMenuItem(
                                        text = {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(8.dp)
                                                        .background(Color(status.color), CircleShape)
                                                )
                                                Text(
                                                    status.displayName,
                                                    fontWeight = if (isCurrentStatus) FontWeight.Bold else FontWeight.Normal,
                                                    color = if (isCurrentStatus) Color(status.color) else Color.Unspecified
                                                )
                                                if (isCurrentStatus) {
                                                    Spacer(Modifier.weight(1f))
                                                    Icon(
                                                        Icons.Default.Check,
                                                        contentDescription = null,
                                                        tint = Color(status.color),
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                            }
                                        },
                                        onClick = {
                                            if (!isCurrentStatus) {
                                                onStatusChange(status)
                                            }
                                            showStatusMenu = false
                                        },
                                        enabled = !isCurrentStatus
                                    )
                                }
                                
                                // Divider before delete
                                HorizontalDivider(
                                    modifier = Modifier.padding(vertical = 4.dp),
                                    color = Color(0xFFE5E7EB)
                                )
                                
                                // Delete option
                                DropdownMenuItem(
                                    text = {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.Delete,
                                                contentDescription = null,
                                                tint = Color(0xFFEF4444),
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Text(
                                                "Delete Lead",
                                                color = Color(0xFFEF4444),
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                    },
                                    onClick = {
                                        showStatusMenu = false
                                        showDeleteDialog = true
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
    
    // Delete Confirmation Dialog
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            containerColor = Color(0xFF1a1a2e),
            title = {
                Text(
                    "Delete Lead?",
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    "Are you sure you want to delete \"${lead.name}\"? This action cannot be undone.",
                    color = Color.White.copy(alpha = 0.8f)
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        onDelete()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFEF4444))
                ) {
                    Text("Delete", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDeleteDialog = false },
                    colors = ButtonDefaults.textButtonColors(contentColor = Color.White)
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun QuickActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .size(28.dp)
            .clickable(onClick = onClick),
        color = Color.White.copy(alpha = 0.2f),
        shape = CircleShape
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                icon,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

// Keep old LeadCard for backward compatibility
@Composable
fun LeadCard(lead: Lead, onClick: () -> Unit) {
    AdvancedLeadCard(lead = lead, onClick = onClick)
}

@Composable
fun EmptyLeadsState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                Icons.Default.People,
                contentDescription = null,
                tint = Color(0xFF64748B),
                modifier = Modifier.size(64.dp)
            )
            Text(
                "No leads yet",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF94A3B8)
            )
            Text(
                "Start adding leads to manage them here",
                fontSize = 14.sp,
                color = Color(0xFF64748B)
            )
        }
    }
}

/**
 * Get real keyboard emoji based on lead score
 */
fun getScoreTextEmoji(score: Int): String {
    return when {
        score < 20 -> "😢"  // Crying face
        score < 40 -> "😟"  // Worried face
        score < 60 -> "😐"  // Neutral face
        score < 80 -> "😊"  // Smiling face
        else -> "🤩"        // Star-struck face
    }
}
