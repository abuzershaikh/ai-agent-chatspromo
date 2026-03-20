package com.message.bulksend.leadmanager.overview

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.message.bulksend.leadmanager.model.Lead
import com.message.bulksend.leadmanager.model.LeadStatus
import com.message.bulksend.leadmanager.utils.LeadExportHelper
import androidx.compose.ui.platform.LocalContext
import android.widget.Toast
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId
import java.text.SimpleDateFormat
import java.util.*

/**
 * Overview Screen - Shows all status categories with click to filter
 */
@Composable
fun OverviewScreen(
    leads: List<Lead>,
    onLeadClick: (Lead) -> Unit,
    onStatusChange: (Lead, LeadStatus) -> Unit = { _, _ -> }
) {
    var selectedStatus by remember { mutableStateOf<LeadStatus?>(null) }
    
    if (selectedStatus != null) {
        // Show filtered leads screen
        FilteredLeadsScreen(
            leads = leads.filter { it.status == selectedStatus },
            status = selectedStatus!!,
            onBack = { selectedStatus = null },
            onLeadClick = onLeadClick,
            onStatusChange = onStatusChange
        )
    } else {
        // Show categories grid
        CategoriesOverviewScreen(
            leads = leads,
            onCategoryClick = { status -> selectedStatus = status }
        )
    }
}

/**
 * Categories Grid Screen - Compact & Super Design
 */
@Composable
fun CategoriesOverviewScreen(
    leads: List<Lead>,
    onCategoryClick: (LeadStatus) -> Unit
) {
    val backgroundBrush = Brush.verticalGradient(
        colors = listOf(Color(0xFF0f0c29), Color(0xFF302b63), Color(0xFF24243e))
    )
    
    var showExportMenu by remember { mutableStateOf(false) }
    var isExporting by remember { mutableStateOf(false) }
    
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundBrush)
            .padding(12.dp)
    ) {
        // Compact Header with Export Button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp), // Increased from 12.dp
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    "Overview",
                    fontSize = 24.sp, // Increased from 20.sp
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    "${leads.size} leads",
                    fontSize = 14.sp, // Increased from 11.sp
                    color = Color(0xFF94A3B8)
                )
            }
            
            // Export All Leads Button
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
                                contentDescription = "Export All",
                                tint = Color(0xFF10B981),
                                modifier = Modifier.size(28.dp) // Increased from 24.dp
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
        
        // Categories Grid - Smaller & Compact
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(LeadStatus.entries.toList()) { status ->
                val count = leads.count { it.status == status }
                CategoryCardClickable(
                    status = status,
                    count = count,
                    onClick = { onCategoryClick(status) }
                )
            }
        }
    }
}

@Composable
fun CategoryCardClickable(
    status: LeadStatus,
    count: Int,
    onClick: () -> Unit
) {
    val statusColor = Color(status.color)
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(75.dp)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        shape = RoundedCornerShape(14.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(14.dp))
        ) {
            // Base gradient background
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        brush = Brush.linearGradient(
                            colors = listOf(
                                statusColor.copy(alpha = 0.9f),
                                statusColor.copy(alpha = 0.7f),
                                statusColor.copy(alpha = 0.5f)
                            ),
                            start = Offset(0f, 0f),
                            end = Offset(300f, 150f)
                        )
                    )
            )
            
            // Content
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left - Icon
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(Color.White.copy(alpha = 0.25f), RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        getStatusIcon(status),
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
                
                // Right - Count & Label
                Column(
                    horizontalAlignment = Alignment.End
                ) {
                    Text(
                        count.toString(),
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        status.displayName,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White.copy(alpha = 0.9f),
                        maxLines = 1
                    )
                }
            }
        }
    }
}

/**
 * Filtered Leads Screen - Shows leads of selected status
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilteredLeadsScreen(
    leads: List<Lead>,
    status: LeadStatus,
    onBack: () -> Unit,
    onLeadClick: (Lead) -> Unit,
    onStatusChange: (Lead, LeadStatus) -> Unit = { _, _ -> }
) {
    val backgroundBrush = Brush.verticalGradient(
        colors = listOf(Color(0xFF0f0c29), Color(0xFF302b63), Color(0xFF24243e))
    )
    
    // Track current leads to handle status changes
    var currentLeads by remember(leads) { mutableStateOf(leads) }
    var showExportMenu by remember { mutableStateOf(false) }
    var isExporting by remember { mutableStateOf(false) }
    
    // Calendar date filter state
    var showDatePicker by remember { mutableStateOf(false) }
    var selectedDate by remember { mutableStateOf<java.time.LocalDate?>(null) }
    val datePickerState = rememberDatePickerState()
    
    // Filter leads by selected date
    val filteredLeads = remember(currentLeads, selectedDate) {
        if (selectedDate != null) {
            val startOfDay = selectedDate!!.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
            val endOfDay = selectedDate!!.plusDays(1).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli() - 1
            currentLeads.filter { lead ->
                lead.timestamp in startOfDay..endOfDay
            }
        } else {
            currentLeads
        }
    }
    
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    BackHandler { onBack() }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            status.displayName,
                            fontSize = 20.sp, // Increased from 18sp
                            fontWeight = FontWeight.Bold,
                            color = Color(status.color)
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                "${filteredLeads.size} leads",
                                fontSize = 14.sp, // Increased from 12sp
                                color = Color(0xFF94A3B8)
                            )
                            if (selectedDate != null) {
                                Surface(
                                    color = Color(status.color).copy(alpha = 0.2f),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text(
                                        selectedDate!!.format(java.time.format.DateTimeFormatter.ofPattern("MMM dd, yyyy")),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = Color(status.color),
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color(status.color)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF1a1a2e)
                ),
                actions = {
                    // Calendar Date Filter Button
                    IconButton(
                        onClick = { showDatePicker = true }
                    ) {
                        Icon(
                            Icons.Default.CalendarToday,
                            contentDescription = "Filter by Date",
                            tint = if (selectedDate != null) Color(status.color) else Color(0xFF94A3B8)
                        )
                    }
                    
                    // Clear Date Filter Button (only show if date is selected)
                    if (selectedDate != null) {
                        IconButton(
                            onClick = { selectedDate = null }
                        ) {
                            Icon(
                                Icons.Default.Clear,
                                contentDescription = "Clear Date Filter",
                                tint = Color(0xFF94A3B8)
                            )
                        }
                    }
                    
                    // Export button
                    if (filteredLeads.isNotEmpty()) {
                        Box {
                            IconButton(
                                onClick = { showExportMenu = true },
                                enabled = !isExporting
                            ) {
                                if (isExporting) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        strokeWidth = 2.dp,
                                        color = Color(status.color)
                                    )
                                } else {
                                    Icon(
                                        Icons.Default.FileDownload,
                                        contentDescription = "Export",
                                        tint = Color(status.color)
                                    )
                                }
                            }
                            
                            DropdownMenu(
                                expanded = showExportMenu,
                                onDismissRequest = { showExportMenu = false }
                            ) {
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
                                                val dateStr = selectedDate?.format(java.time.format.DateTimeFormatter.ofPattern("yyyy_MM_dd")) ?: ""
                                                val fileName = if (dateStr.isNotEmpty()) {
                                                    LeadExportHelper.generateFileName("${status.displayName}_Leads_$dateStr")
                                                } else {
                                                    LeadExportHelper.generateFileName("${status.displayName}_Leads")
                                                }
                                                val filePath = LeadExportHelper.exportToCSV(
                                                    context = context,
                                                    leads = filteredLeads,
                                                    fileName = fileName,
                                                    statusFilter = status
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
                                                val dateStr = selectedDate?.format(java.time.format.DateTimeFormatter.ofPattern("yyyy_MM_dd")) ?: ""
                                                val fileName = if (dateStr.isNotEmpty()) {
                                                    LeadExportHelper.generateFileName("${status.displayName}_Leads_$dateStr")
                                                } else {
                                                    LeadExportHelper.generateFileName("${status.displayName}_Leads")
                                                }
                                                val filePath = LeadExportHelper.exportToExcel(
                                                    context = context,
                                                    leads = filteredLeads,
                                                    fileName = fileName,
                                                    statusFilter = status
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
                    
                    // Status badge
                    Surface(
                        color = Color(status.color).copy(alpha = 0.2f),
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.padding(end = 16.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp), // Increased padding
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                getStatusIcon(status),
                                contentDescription = null,
                                tint = Color(status.color),
                                modifier = Modifier.size(18.dp) // Increased from 16dp
                            )
                            Text(
                                filteredLeads.size.toString(),
                                fontSize = 16.sp, // Increased from 14sp
                                fontWeight = FontWeight.Bold,
                                color = Color(status.color)
                            )
                        }
                    }
                }
            )
        },
        containerColor = Color.Transparent
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(backgroundBrush)
                .padding(padding)
        ) {
            if (filteredLeads.isEmpty()) {
                // Empty state
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(80.dp)
                                .background(Color(status.color).copy(alpha = 0.2f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                getStatusIcon(status),
                                contentDescription = null,
                                tint = Color(status.color),
                                modifier = Modifier.size(40.dp)
                            )
                        }
                        Text(
                            if (selectedDate != null) "No leads found for selected date" else "No ${status.displayName} leads",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            if (selectedDate != null) "Try selecting a different date or clear the filter" else "Leads with this status will appear here",
                            fontSize = 14.sp,
                            color = Color(0xFF94A3B8)
                        )
                        if (selectedDate != null) {
                            TextButton(
                                onClick = { selectedDate = null }
                            ) {
                                Icon(
                                    Icons.Default.Clear,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = Color(status.color)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    "Clear Date Filter",
                                    color = Color(status.color)
                                )
                            }
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Date filter info card (if date is selected)
                    if (selectedDate != null) {
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = Color(status.color).copy(alpha = 0.1f)),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.CalendarToday,
                                            contentDescription = null,
                                            tint = Color(status.color),
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Column {
                                            Text(
                                                "Filtered by Date",
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(status.color)
                                            )
                                            Text(
                                                selectedDate!!.format(java.time.format.DateTimeFormatter.ofPattern("EEEE, MMMM dd, yyyy")),
                                                fontSize = 12.sp,
                                                color = Color(status.color).copy(alpha = 0.8f)
                                            )
                                        }
                                    }
                                    IconButton(
                                        onClick = { selectedDate = null }
                                    ) {
                                        Icon(
                                            Icons.Default.Close,
                                            contentDescription = "Clear Filter",
                                            tint = Color(status.color),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                    
                    items(
                        items = filteredLeads.sortedByDescending { it.timestamp },
                        key = { it.id }
                    ) { lead ->
                        FilteredLeadCard(
                            lead = lead,
                            onClick = { onLeadClick(lead) },
                            onStatusChange = { newStatus ->
                                // Remove from current list when status changes
                                currentLeads = currentLeads.filter { it.id != lead.id }
                                onStatusChange(lead, newStatus)
                            }
                        )
                    }
                    
                    item { Spacer(Modifier.height(80.dp)) }
                }
            }
        }
        
        // Date Picker Dialog
        if (showDatePicker) {
            DatePickerDialog(
                onDismissRequest = { showDatePicker = false },
                confirmButton = {
                    TextButton(
                        onClick = {
                            datePickerState.selectedDateMillis?.let { millis ->
                                selectedDate = LocalDate.ofInstant(
                                    java.time.Instant.ofEpochMilli(millis),
                                    ZoneId.systemDefault()
                                )
                            }
                            showDatePicker = false
                        }
                    ) {
                        Text("OK", color = Color(status.color))
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { showDatePicker = false }
                    ) {
                        Text("Cancel", color = Color(0xFF94A3B8))
                    }
                }
            ) {
                DatePicker(
                    state = datePickerState,
                    colors = androidx.compose.material3.DatePickerDefaults.colors(
                        selectedDayContainerColor = Color(status.color),
                        todayDateBorderColor = Color(status.color),
                        todayContentColor = Color(status.color)
                    )
                )
            }
        }
    }
}

@Composable
fun FilteredLeadCard(
    lead: Lead,
    onClick: () -> Unit,
    onStatusChange: (LeadStatus) -> Unit = {}
) {
    val statusColor = Color(lead.status.color)
    val scoreProgress = lead.leadScore / 100f
    var showStatusMenu by remember { mutableStateOf(false) }
    
    // Card design like image - gradient top with progress, white inner card
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = statusColor),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
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
            
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                // Top section - Lead Score with progress bar and emoji
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Real keyboard emoji based on lead score
                        Text(
                            text = getScoreTextEmoji(lead.leadScore),
                            fontSize = 18.sp
                        )
                        Text(
                            "Lead Score",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                    
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Progress bar
                        Box(
                            modifier = Modifier
                                .width(80.dp)
                                .height(6.dp)
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
                            "${lead.leadScore}/100",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
                
                // White inner card
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
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
                        // Avatar icon
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(Color(0xFFF5F5F5), RoundedCornerShape(10.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                lead.name.take(2).uppercase(),
                                fontSize = 14.sp,
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
                                fontSize = 14.sp,
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
                        }
                        
                        // More options with dropdown
                        Box {
                            IconButton(
                                onClick = { showStatusMenu = true },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    Icons.Default.MoreVert,
                                    contentDescription = "Options",
                                    tint = Color(0xFF64748B),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            
                            // Status change dropdown menu
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
                                                        modifier = Modifier.size(18.dp)
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
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Get icon for status
 */
fun getStatusIcon(status: LeadStatus): ImageVector {
    return when (status) {
        LeadStatus.NEW -> Icons.Default.FiberNew
        LeadStatus.INTERESTED -> Icons.Default.Favorite
        LeadStatus.CONTACTED -> Icons.Default.Phone
        LeadStatus.QUALIFIED -> Icons.Default.CheckCircle
        LeadStatus.CONVERTED -> Icons.Default.TrendingUp
        LeadStatus.CUSTOMER -> Icons.Default.Person
        LeadStatus.LOST -> Icons.Default.Cancel
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
