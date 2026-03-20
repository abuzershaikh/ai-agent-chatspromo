package com.message.bulksend.leadmanager.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.message.bulksend.leadmanager.LeadManager
import com.message.bulksend.leadmanager.model.Lead
import com.message.bulksend.leadmanager.model.LeadStatus
import com.message.bulksend.leadmanager.model.FollowUp
import com.message.bulksend.leadmanager.model.FollowUpType
import com.message.bulksend.leadmanager.timeline.VerticalTimeline
import java.text.SimpleDateFormat
import java.util.*
import java.util.Calendar

@Composable
fun LeadDetailScreen(
    lead: Lead,
    leadManager: LeadManager,
    onBack: () -> Unit,
    onLeadUpdated: () -> Unit
) {
    val context = LocalContext.current
    var currentLead by remember { mutableStateOf(lead) }
    var showStatusDialog by remember { mutableStateOf(false) }
    var showProductDialog by remember { mutableStateOf(false) }
    var showEditLeadDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showNotesDialog by remember { mutableStateOf(false) }
    var showFollowUpDialog by remember { mutableStateOf(false) }
    var notes by remember { mutableStateOf(currentLead.notes) }
    var selectedTab by remember { mutableStateOf(0) }
    
    val tabs = listOf("Details", "Timeline")
    
    val backgroundBrush = Brush.verticalGradient(
        colors = listOf(Color(0xFF0f0c29), Color(0xFF302b63), Color(0xFF24243e))
    )
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundBrush)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top Bar
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color(0xFF1a1a2e),
                shadowElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .size(48.dp)
                            .background(Color(0xFF10B981).copy(alpha = 0.2f), CircleShape)
                    ) {
                        Icon(
                            Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = Color(0xFF10B981)
                        )
                    }
                    
                    Spacer(modifier = Modifier.width(12.dp))
                    
                    Text(
                        "Lead Details",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.weight(1f)
                    )
                    
                    // Edit Button
                    IconButton(
                        onClick = { showEditLeadDialog = true },
                        modifier = Modifier
                            .size(48.dp)
                            .background(Color(0xFF3B82F6).copy(alpha = 0.2f), CircleShape)
                    ) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = "Edit",
                            tint = Color(0xFF3B82F6),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    // Delete Button
                    IconButton(
                        onClick = { showDeleteDialog = true },
                        modifier = Modifier
                            .size(48.dp)
                            .background(Color(0xFFEF4444).copy(alpha = 0.2f), CircleShape)
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Delete",
                            tint = Color(0xFFEF4444),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
            
            // Tab Row
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = Color(0xFF1a1a2e),
                contentColor = Color.White,
                indicator = { tabPositions ->
                    TabRowDefaults.Indicator(
                        Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                        color = Color(0xFF10B981)
                    )
                }
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = {
                            Text(
                                title,
                                fontSize = 14.sp,
                                fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    )
                }
            }
            
            // Tab Content
            when (selectedTab) {
                0 -> { // Details Tab
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                // Lead Name Header
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1a1a2e)),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(80.dp)
                                    .background(Color(currentLead.status.color).copy(alpha = 0.2f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.Person,
                                    contentDescription = null,
                                    tint = Color(currentLead.status.color),
                                    modifier = Modifier.size(40.dp)
                                )
                            }
                            
                            Text(
                                currentLead.name,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            
                            Surface(
                                color = Color(currentLead.status.color).copy(alpha = 0.2f),
                                shape = RoundedCornerShape(20.dp)
                            ) {
                                Text(
                                    currentLead.status.displayName,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(currentLead.status.color),
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                )
                            }
                        }
                    }
                }
                
                // Quick Actions
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        QuickActionButton(
                            icon = Icons.Default.Phone,
                            label = "Call",
                            color = Color(0xFF3B82F6),
                            onClick = {
                                if (currentLead.phoneNumber.isNotEmpty()) {
                                    val intent = Intent(Intent.ACTION_DIAL).apply {
                                        data = Uri.parse("tel:${currentLead.phoneNumber}")
                                    }
                                    context.startActivity(intent)
                                }
                            }
                        )
                        
                        QuickActionButton(
                            icon = Icons.Default.Message,
                            label = "SMS",
                            color = Color(0xFFF59E0B),
                            onClick = {
                                if (currentLead.phoneNumber.isNotEmpty()) {
                                    val intent = Intent(Intent.ACTION_VIEW).apply {
                                        data = Uri.parse("sms:${currentLead.phoneNumber}")
                                    }
                                    context.startActivity(intent)
                                }
                            }
                        )
                        
                        QuickActionButton(
                            icon = Icons.Default.Chat,
                            label = "WhatsApp",
                            color = Color(0xFF10B981),
                            onClick = {
                                if (currentLead.phoneNumber.isNotEmpty()) {
                                    val intent = Intent(Intent.ACTION_VIEW).apply {
                                        data = Uri.parse("https://wa.me/${currentLead.phoneNumber}")
                                    }
                                    context.startActivity(intent)
                                }
                            }
                        )
                        
                        QuickActionButton(
                            icon = Icons.Default.Email,
                            label = "Email",
                            color = Color(0xFFEF4444),
                            onClick = {
                                // Open email app
                            }
                        )
                        
                        QuickActionButton(
                            icon = Icons.Default.Share,
                            label = "Share",
                            color = Color(0xFF8B5CF6),
                            onClick = {
                                // Share contact
                            }
                        )
                    }
                }
                
                // Status Management
                item {
                    ManagementCard(
                        icon = Icons.Default.TrendingUp,
                        title = "Status",
                        subtitle = "Update lead status",
                        color = Color(currentLead.status.color),
                        onClick = { showStatusDialog = true }
                    )
                }
                
                // Product Selection
                item {
                    ManagementCard(
                        icon = Icons.Default.ShoppingBag,
                        title = "Product",
                        subtitle = if (currentLead.product.isNotEmpty()) currentLead.product else "Tap to select product",
                        color = Color(0xFFF59E0B),
                        onClick = { showProductDialog = true }
                    )
                }
                
                // Follow-up
                item {
                    ManagementCard(
                        icon = Icons.Default.CalendarToday,
                        title = "Follow-up",
                        subtitle = if (currentLead.nextFollowUpDate != null) {
                            val dateFormat = SimpleDateFormat("MMM dd, yyyy 'at' HH:mm", Locale.getDefault())
                            "Next: ${dateFormat.format(Date(currentLead.nextFollowUpDate!!))}"
                        } else "Tap to schedule follow-up",
                        color = Color(0xFF3B82F6),
                        onClick = { showFollowUpDialog = true }
                    )
                }
                
                // Follow-up History
                if (currentLead.followUps.isNotEmpty()) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF1a1a2e)),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(20.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Text(
                                    "Follow-up History",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF3B82F6)
                                )
                                
                                currentLead.followUps.sortedByDescending { it.scheduledDate }.take(3).forEach { followUp ->
                                    FollowUpHistoryItem(followUp = followUp)
                                }
                                
                                if (currentLead.followUps.size > 3) {
                                    Text(
                                        "View all ${currentLead.followUps.size} follow-ups",
                                        fontSize = 12.sp,
                                        color = Color(0xFF3B82F6),
                                        modifier = Modifier.clickable { /* TODO: Show all follow-ups */ }
                                    )
                                }
                            }
                        }
                    }
                }
                
                // Deals
                item {
                    ManagementCard(
                        icon = Icons.Default.LocalOffer,
                        title = "Deals",
                        subtitle = "Tap to add deals",
                        color = Color(0xFFEF4444),
                        onClick = { /* TODO: Add deals */ }
                    )
                }
                
                // Notes
                item {
                    ManagementCard(
                        icon = Icons.Default.Note,
                        title = "Notes",
                        subtitle = if (notes.isNotEmpty()) notes else "Tap to add notes",
                        color = Color(0xFF8B5CF6),
                        onClick = { showNotesDialog = true }
                    )
                }
                
                // Contact Information
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1a1a2e)),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Text(
                                "Contact Information",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF10B981)
                            )
                            
                            if (currentLead.phoneNumber.isNotEmpty()) {
                                InfoRow(
                                    icon = Icons.Default.Phone,
                                    label = "Phone",
                                    value = currentLead.phoneNumber,
                                    color = Color(0xFF3B82F6)
                                )
                            }
                            
                            if (currentLead.alternatePhone.isNotEmpty()) {
                                InfoRow(
                                    icon = Icons.Default.PhoneAndroid,
                                    label = "Alternate Phone",
                                    value = currentLead.alternatePhone,
                                    color = Color(0xFF06B6D4)
                                )
                            }
                            
                            if (currentLead.email.isNotEmpty()) {
                                InfoRow(
                                    icon = Icons.Default.Email,
                                    label = "Email",
                                    value = currentLead.email,
                                    color = Color(0xFFEC4899)
                                )
                            }
                            
                            if (currentLead.category.isNotEmpty()) {
                                InfoRow(
                                    icon = Icons.Default.Category,
                                    label = "Category",
                                    value = currentLead.category,
                                    color = Color(0xFFF59E0B)
                                )
                            }
                            
                            if (currentLead.source.isNotEmpty()) {
                                InfoRow(
                                    icon = Icons.Default.Source,
                                    label = "Source",
                                    value = currentLead.source,
                                    color = Color(0xFF10B981)
                                )
                            }
                            
                            InfoRow(
                                icon = Icons.Default.Flag,
                                label = "Priority",
                                value = currentLead.priority.displayName,
                                color = Color(currentLead.priority.color)
                            )
                            
                            if (currentLead.tags.isNotEmpty()) {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            Icons.Default.Label,
                                            contentDescription = null,
                                            tint = Color(0xFF8B5CF6),
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Text(
                                            "Tags",
                                            fontSize = 14.sp,
                                            color = Color(0xFF94A3B8)
                                        )
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        currentLead.tags.forEach { tag ->
                                            Surface(
                                                color = Color(0xFF8B5CF6).copy(alpha = 0.2f),
                                                shape = RoundedCornerShape(8.dp)
                                            ) {
                                                Text(
                                                    tag,
                                                    fontSize = 12.sp,
                                                    color = Color(0xFF8B5CF6),
                                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                
                        item {
                            Spacer(modifier = Modifier.height(80.dp))
                        }
                    }
                }
                
                1 -> { // Timeline Tab
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                    ) {
                        // Get timeline entries for this lead
                        val timelineEntries = remember(currentLead.id) {
                            leadManager.getTimelineEntries(currentLead.id)
                        }
                        
                        VerticalTimeline(
                            entries = timelineEntries,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }
    }
    
    // Status Dialog
    if (showStatusDialog) {
        StatusSelectionDialog(
            currentStatus = currentLead.status,
            onDismiss = { showStatusDialog = false },
            onStatusSelected = { newStatus ->
                currentLead = currentLead.copy(status = newStatus)
                leadManager.updateLead(currentLead)
                onLeadUpdated()
                showStatusDialog = false
            }
        )
    }
    
    // Product Dialog
    if (showProductDialog) {
        ProductSelectionDialog(
            leadManager = leadManager,
            currentProduct = currentLead.product,
            onDismiss = { showProductDialog = false },
            onProductSelected = { product ->
                currentLead = currentLead.copy(product = product)
                leadManager.updateLead(currentLead)
                onLeadUpdated()
                showProductDialog = false
            }
        )
    }
    
    // Notes Dialog
    if (showNotesDialog) {
        NotesDialog(
            currentNotes = notes,
            onDismiss = { showNotesDialog = false },
            onSave = { newNotes ->
                notes = newNotes
                currentLead = currentLead.copy(notes = newNotes)
                leadManager.updateLead(currentLead)
                onLeadUpdated()
                showNotesDialog = false
            }
        )
    }
    
    // Follow-up Dialog
    if (showFollowUpDialog) {
        FollowUpDialog(
            leadId = currentLead.id,
            onDismiss = { showFollowUpDialog = false },
            onFollowUpAdded = { followUp ->
                val updatedFollowUps = currentLead.followUps + followUp
                val nextFollowUpDate = if (!followUp.isCompleted) followUp.scheduledDate else currentLead.nextFollowUpDate
                currentLead = currentLead.copy(
                    followUps = updatedFollowUps,
                    nextFollowUpDate = nextFollowUpDate
                )
                leadManager.updateLead(currentLead)
                onLeadUpdated()
                showFollowUpDialog = false
            }
        )
    }
    
    // Edit Lead Dialog
    if (showEditLeadDialog) {
        EditLeadDialog(
            lead = currentLead,
            leadManager = leadManager,
            onDismiss = { showEditLeadDialog = false },
            onSave = { updatedLead ->
                currentLead = updatedLead
                notes = updatedLead.notes
                leadManager.updateLead(updatedLead)
                onLeadUpdated()
                showEditLeadDialog = false
            }
        )
    }
    
    // Delete Confirmation Dialog
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Lead") },
            text = { Text("Are you sure you want to delete ${currentLead.name}?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        leadManager.deleteLead(currentLead.id)
                        onLeadUpdated()
                        onBack()
                    }
                ) {
                    Text("Delete", color = Color(0xFFEF4444))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun QuickActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    color: Color,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .width(100.dp)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1a1a2e)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(color.copy(alpha = 0.2f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(24.dp)
                )
            }
            Text(
                label,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White
            )
        }
    }
}

@Composable
fun ManagementCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    color: Color,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1a1a2e)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .background(color.copy(alpha = 0.2f), RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(28.dp)
                )
            }
            
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    subtitle,
                    fontSize = 13.sp,
                    color = Color(0xFF94A3B8)
                )
            }
            
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = Color(0xFF64748B)
            )
        }
    }
}

@Composable
fun InfoRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    color: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(20.dp)
        )
        Column(
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                label,
                fontSize = 12.sp,
                color = Color(0xFF64748B)
            )
            Text(
                value,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White
            )
        }
    }
}

@Composable
fun StatusSelectionDialog(
    currentStatus: LeadStatus,
    onDismiss: () -> Unit,
    onStatusSelected: (LeadStatus) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Update Status") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                LeadStatus.values().forEach { status ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onStatusSelected(status) },
                        colors = CardDefaults.cardColors(
                            containerColor = if (status == currentStatus) 
                                Color(status.color).copy(alpha = 0.2f) 
                            else Color(0xFF1a1a2e)
                        )
                    ) {
                        Text(
                            status.displayName,
                            fontSize = 14.sp,
                            fontWeight = if (status == currentStatus) FontWeight.Bold else FontWeight.Normal,
                            color = Color.White,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun ProductSelectionDialog(
    leadManager: LeadManager,
    currentProduct: String,
    onDismiss: () -> Unit,
    onProductSelected: (String) -> Unit
) {
    val products = remember { leadManager.getAllProducts() }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Product") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                products.forEach { product ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onProductSelected(product) },
                        colors = CardDefaults.cardColors(
                            containerColor = if (product == currentProduct) 
                                Color(0xFFF59E0B).copy(alpha = 0.2f) 
                            else Color(0xFF1a1a2e)
                        )
                    ) {
                        Text(
                            product,
                            fontSize = 14.sp,
                            fontWeight = if (product == currentProduct) FontWeight.Bold else FontWeight.Normal,
                            color = Color.White,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun NotesDialog(
    currentNotes: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var notes by remember { mutableStateOf(currentNotes) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Notes") },
        text = {
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp),
                placeholder = { Text("Add notes...") }
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(notes) }) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FollowUpDialog(
    leadId: String,
    onDismiss: () -> Unit,
    onFollowUpAdded: (FollowUp) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf(FollowUpType.CALL) }
    var selectedDate by remember { mutableStateOf(Calendar.getInstance()) }
    var selectedTime by remember { mutableStateOf("09:00") }
    var reminderMinutes by remember { mutableStateOf(15) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { 
            Text(
                "Schedule Follow-up",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            ) 
        },
        text = {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.height(400.dp)
            ) {
                item {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Title") },
                        placeholder = { Text("e.g., Follow-up call") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
                
                item {
                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text("Description (Optional)") },
                        placeholder = { Text("Add details...") },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 3
                    )
                }
                
                item {
                    Text(
                        "Follow-up Type",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF64748B)
                    )
                    
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FollowUpType.values().forEach { type ->
                            FilterChip(
                                onClick = { selectedType = type },
                                label = { Text(type.displayName) },
                                selected = selectedType == type,
                                leadingIcon = {
                                    Icon(
                                        when (type) {
                                            FollowUpType.CALL -> Icons.Default.Phone
                                            FollowUpType.EMAIL -> Icons.Default.Email
                                            FollowUpType.MEETING -> Icons.Default.Group
                                            FollowUpType.WHATSAPP -> Icons.Default.Chat
                                            FollowUpType.VISIT -> Icons.Default.LocationOn
                                            FollowUpType.OTHER -> Icons.Default.Event
                                        },
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            )
                        }
                    }
                }
                
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(selectedDate.time),
                            onValueChange = { },
                            label = { Text("Date") },
                            readOnly = true,
                            modifier = Modifier
                                .weight(1f)
                                .clickable { showDatePicker = true },
                            trailingIcon = {
                                IconButton(onClick = { showDatePicker = true }) {
                                    Icon(Icons.Default.DateRange, contentDescription = "Select Date")
                                }
                            }
                        )
                        
                        OutlinedTextField(
                            value = selectedTime,
                            onValueChange = { },
                            label = { Text("Time") },
                            readOnly = true,
                            modifier = Modifier
                                .weight(1f)
                                .clickable { showTimePicker = true },
                            trailingIcon = {
                                IconButton(onClick = { showTimePicker = true }) {
                                    Icon(Icons.Default.AccessTime, contentDescription = "Select Time")
                                }
                            }
                        )
                    }
                }
                
                item {
                    Text(
                        "Reminder",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF64748B)
                    )
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(5, 15, 30, 60).forEach { minutes ->
                            FilterChip(
                                onClick = { reminderMinutes = minutes },
                                label = { Text("${minutes}m") },
                                selected = reminderMinutes == minutes
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (title.isNotBlank()) {
                        val calendar = selectedDate.clone() as Calendar
                        val timeParts = selectedTime.split(":")
                        calendar.set(Calendar.HOUR_OF_DAY, timeParts[0].toInt())
                        calendar.set(Calendar.MINUTE, timeParts[1].toInt())
                        
                        val followUp = FollowUp(
                            id = UUID.randomUUID().toString(),
                            leadId = leadId,
                            title = title,
                            description = description,
                            scheduledDate = calendar.timeInMillis,
                            scheduledTime = selectedTime,
                            type = selectedType,
                            reminderMinutes = reminderMinutes
                        )
                        onFollowUpAdded(followUp)
                    }
                },
                enabled = title.isNotBlank()
            ) {
                Text("Schedule")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
    
    // Date Picker
    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = selectedDate.timeInMillis
        )
        
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            selectedDate.timeInMillis = millis
                        }
                        showDatePicker = false
                    }
                ) {
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
    
    // Time Picker
    if (showTimePicker) {
        val timePickerState = rememberTimePickerState(
            initialHour = selectedTime.split(":")[0].toInt(),
            initialMinute = selectedTime.split(":")[1].toInt()
        )
        
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        selectedTime = String.format("%02d:%02d", timePickerState.hour, timePickerState.minute)
                        showTimePicker = false
                    }
                ) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) {
                    Text("Cancel")
                }
            },
            text = {
                TimePicker(state = timePickerState)
            }
        )
    }
}

@Composable
fun FollowUpHistoryItem(followUp: FollowUp) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(
                    if (followUp.isCompleted) Color(0xFF10B981).copy(alpha = 0.2f) 
                    else Color(followUp.type.color).copy(alpha = 0.2f),
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                if (followUp.isCompleted) Icons.Default.CheckCircle else when (followUp.type) {
                    FollowUpType.CALL -> Icons.Default.Phone
                    FollowUpType.EMAIL -> Icons.Default.Email
                    FollowUpType.MEETING -> Icons.Default.Group
                    FollowUpType.WHATSAPP -> Icons.Default.Chat
                    FollowUpType.VISIT -> Icons.Default.LocationOn
                    FollowUpType.OTHER -> Icons.Default.Event
                },
                contentDescription = null,
                tint = if (followUp.isCompleted) Color(0xFF10B981) else Color(followUp.type.color),
                modifier = Modifier.size(20.dp)
            )
        }
        
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                followUp.title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White
            )
            
            val dateFormat = SimpleDateFormat("MMM dd, yyyy 'at' HH:mm", Locale.getDefault())
            Text(
                dateFormat.format(Date(followUp.scheduledDate)),
                fontSize = 12.sp,
                color = Color(0xFF94A3B8)
            )
            
            if (followUp.description.isNotEmpty()) {
                Text(
                    followUp.description,
                    fontSize = 12.sp,
                    color = Color(0xFF64748B),
                    maxLines = 1
                )
            }
        }
        
        if (followUp.isCompleted) {
            Surface(
                color = Color(0xFF10B981).copy(alpha = 0.2f),
                shape = RoundedCornerShape(6.dp)
            ) {
                Text(
                    "Done",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF10B981),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        } else {
            Surface(
                color = Color(0xFFF59E0B).copy(alpha = 0.2f),
                shape = RoundedCornerShape(6.dp)
            ) {
                Text(
                    "Pending",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFFF59E0B),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}

/**
 * Dialog to edit lead details
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditLeadDialog(
    lead: Lead,
    leadManager: LeadManager,
    onDismiss: () -> Unit,
    onSave: (Lead) -> Unit
) {
    var name by remember { mutableStateOf(lead.name) }
    var phoneNumber by remember { mutableStateOf(lead.phoneNumber) }
    var alternatePhone by remember { mutableStateOf(lead.alternatePhone) }
    var email by remember { mutableStateOf(lead.email) }
    var emailTouched by remember { mutableStateOf(false) }
    var category by remember { mutableStateOf(lead.category) }
    var source by remember { mutableStateOf(lead.source) }
    var notes by remember { mutableStateOf(lead.notes) }
    var selectedPriority by remember { mutableStateOf(lead.priority) }
    var selectedStatus by remember { mutableStateOf(lead.status) }
    var selectedTags by remember { mutableStateOf(lead.tags.toMutableList()) }
    
    val allTags = remember { leadManager.getAllTags() }
    val allSources = remember { leadManager.getAllSources() }
    
    var showStatusDropdown by remember { mutableStateOf(false) }
    var showPriorityDropdown by remember { mutableStateOf(false) }
    var showSourceDropdown by remember { mutableStateOf(false) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1a1a2e),
        shape = RoundedCornerShape(20.dp),
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(Color(0xFF3B82F6).copy(alpha = 0.2f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = null,
                        tint = Color(0xFF3B82F6),
                        modifier = Modifier.size(20.dp)
                    )
                }
                Text(
                    "Edit Lead",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 500.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Name
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, tint = Color(0xFF10B981)) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF10B981),
                        unfocusedBorderColor = Color(0xFF64748B),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    singleLine = true
                )
                
                // Phone Number
                OutlinedTextField(
                    value = phoneNumber,
                    onValueChange = { phoneNumber = it.filter { char -> char.isDigit() || char == '+' } },
                    label = { Text("Phone Number") },
                    leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null, tint = Color(0xFF3B82F6)) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF10B981),
                        unfocusedBorderColor = Color(0xFF64748B),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
                )
                
                // Alternate Phone Number
                OutlinedTextField(
                    value = alternatePhone,
                    onValueChange = { alternatePhone = it.filter { char -> char.isDigit() || char == '+' } },
                    label = { Text("Alternate Phone (Optional)") },
                    leadingIcon = { Icon(Icons.Default.PhoneAndroid, contentDescription = null, tint = Color(0xFF06B6D4)) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF10B981),
                        unfocusedBorderColor = Color(0xFF64748B),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
                )
                
                // Email
                val isEmailValid = email.isEmpty() || android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()
                val showEmailError = emailTouched && email.isNotEmpty() && !isEmailValid
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it.trim().replace(" ", "") },
                    label = { Text("Email") },
                    leadingIcon = { Icon(Icons.Default.Email, contentDescription = null, tint = Color(0xFFEC4899)) },
                    modifier = Modifier.fillMaxWidth().onFocusChanged { if (!it.isFocused && email.isNotEmpty()) emailTouched = true },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = if (!showEmailError) Color(0xFF10B981) else Color(0xFFEF4444),
                        unfocusedBorderColor = if (!showEmailError) Color(0xFF64748B) else Color(0xFFEF4444),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    isError = showEmailError,
                    supportingText = if (showEmailError) {{ Text("Invalid email format", color = Color(0xFFEF4444)) }} else null
                )
                
                // Status Dropdown
                ExposedDropdownMenuBox(
                    expanded = showStatusDropdown,
                    onExpandedChange = { showStatusDropdown = it }
                ) {
                    OutlinedTextField(
                        value = selectedStatus.displayName,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Status") },
                        leadingIcon = { 
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .background(Color(selectedStatus.color), CircleShape)
                            )
                        },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = showStatusDropdown) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF10B981),
                            unfocusedBorderColor = Color(0xFF64748B),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )
                    ExposedDropdownMenu(
                        expanded = showStatusDropdown,
                        onDismissRequest = { showStatusDropdown = false }
                    ) {
                        LeadStatus.values().forEach { status ->
                            DropdownMenuItem(
                                text = { 
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(10.dp)
                                                .background(Color(status.color), CircleShape)
                                        )
                                        Text(status.displayName)
                                    }
                                },
                                onClick = {
                                    selectedStatus = status
                                    showStatusDropdown = false
                                }
                            )
                        }
                    }
                }
                
                // Priority Dropdown
                ExposedDropdownMenuBox(
                    expanded = showPriorityDropdown,
                    onExpandedChange = { showPriorityDropdown = it }
                ) {
                    OutlinedTextField(
                        value = selectedPriority.displayName,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Priority") },
                        leadingIcon = { Icon(Icons.Default.Flag, contentDescription = null, tint = Color(selectedPriority.color)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = showPriorityDropdown) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF10B981),
                            unfocusedBorderColor = Color(0xFF64748B),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )
                    ExposedDropdownMenu(
                        expanded = showPriorityDropdown,
                        onDismissRequest = { showPriorityDropdown = false }
                    ) {
                        com.message.bulksend.leadmanager.model.LeadPriority.values().forEach { priority ->
                            DropdownMenuItem(
                                text = { 
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.Flag, contentDescription = null, tint = Color(priority.color), modifier = Modifier.size(16.dp))
                                        Text(priority.displayName)
                                    }
                                },
                                onClick = {
                                    selectedPriority = priority
                                    showPriorityDropdown = false
                                }
                            )
                        }
                    }
                }
                
                // Source Dropdown
                ExposedDropdownMenuBox(
                    expanded = showSourceDropdown,
                    onExpandedChange = { showSourceDropdown = it }
                ) {
                    OutlinedTextField(
                        value = source,
                        onValueChange = { source = it },
                        label = { Text("Source") },
                        leadingIcon = { Icon(Icons.Default.Source, contentDescription = null, tint = Color(0xFFF59E0B)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = showSourceDropdown) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF10B981),
                            unfocusedBorderColor = Color(0xFF64748B),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )
                    ExposedDropdownMenu(
                        expanded = showSourceDropdown,
                        onDismissRequest = { showSourceDropdown = false }
                    ) {
                        allSources.forEach { src ->
                            DropdownMenuItem(
                                text = { Text(src) },
                                onClick = {
                                    source = src
                                    showSourceDropdown = false
                                }
                            )
                        }
                    }
                }
                
                // Category
                OutlinedTextField(
                    value = category,
                    onValueChange = { category = it },
                    label = { Text("Category") },
                    leadingIcon = { Icon(Icons.Default.Category, contentDescription = null, tint = Color(0xFF8B5CF6)) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF10B981),
                        unfocusedBorderColor = Color(0xFF64748B),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    singleLine = true
                )
                
                // Tags
                Text(
                    "Tags",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF94A3B8)
                )
                
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    allTags.forEach { tag ->
                        val isSelected = selectedTags.contains(tag)
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                if (isSelected) {
                                    selectedTags.remove(tag)
                                } else {
                                    selectedTags.add(tag)
                                }
                            },
                            label = { Text(tag, fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFF8B5CF6).copy(alpha = 0.3f),
                                selectedLabelColor = Color(0xFF8B5CF6)
                            )
                        )
                    }
                }
                
                // Notes
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes") },
                    leadingIcon = { Icon(Icons.Default.Note, contentDescription = null, tint = Color(0xFFEC4899)) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF10B981),
                        unfocusedBorderColor = Color(0xFF64748B),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    minLines = 3,
                    maxLines = 5
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val updatedLead = lead.copy(
                        name = name.ifBlank { "Unknown" },
                        phoneNumber = phoneNumber,
                        alternatePhone = alternatePhone,
                        email = email,
                        status = selectedStatus,
                        priority = selectedPriority,
                        source = source,
                        category = category,
                        tags = selectedTags.toList(),
                        notes = notes
                    )
                    onSave(updatedLead)
                },
                enabled = name.isNotBlank() || phoneNumber.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Save Changes", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Color(0xFF94A3B8))
            }
        }
    )
}
