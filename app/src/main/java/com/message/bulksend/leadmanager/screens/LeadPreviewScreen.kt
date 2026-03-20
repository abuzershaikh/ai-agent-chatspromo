package com.message.bulksend.leadmanager.screens

import android.content.Intent
import android.net.Uri
import android.os.Environment
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.message.bulksend.leadmanager.LeadManager
import com.message.bulksend.leadmanager.model.*
import com.message.bulksend.leadmanager.customfields.CustomFieldsManager
import com.message.bulksend.leadmanager.customfields.model.CustomField
import com.message.bulksend.leadmanager.customfields.ui.CustomFieldInput
import com.message.bulksend.leadmanager.customfields.ui.getIconForFieldType
import com.message.bulksend.leadmanager.customfields.ui.getIconTintForFieldType
import com.message.bulksend.leadmanager.timeline.TimelineManager
import com.message.bulksend.leadmanager.timeline.VerticalTimeline
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import androidx.activity.compose.BackHandler
import com.message.bulksend.leadmanager.payments.PaymentsManager
import com.message.bulksend.leadmanager.payments.PaymentsTabContent

enum class LeadPreviewTab { DETAILS, PRODUCT, FOLLOWUP, NOTES, PAYMENTS, DOCUMENTS, TIMELINE }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LeadPreviewScreen(
    lead: Lead,
    leadManager: LeadManager,
    onBack: () -> Unit,
    onLeadUpdated: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var currentLead by remember { mutableStateOf(lead) }
    var selectedTab by remember { mutableStateOf(LeadPreviewTab.DETAILS) }
    var leadScore by remember { mutableStateOf(lead.leadScore) }
    var showFabMenu by remember { mutableStateOf(false) }

    // Edit dialogs
    var showEditNameDialog by remember { mutableStateOf(false) }
    var showEditPhoneDialog by remember { mutableStateOf(false) }
    var showEditEmailDialog by remember { mutableStateOf(false) }
    var showStatusDialog by remember { mutableStateOf(false) }
    var showPriorityDialog by remember { mutableStateOf(false) }
    var showProductDialog by remember { mutableStateOf(false) }
    var showFollowUpDialog by remember { mutableStateOf(false) }
    var showAddProductScreen by remember { mutableStateOf(false) }
    
    // Timeline
    val timelineManager = remember { TimelineManager(context) }
    var timelineEntries by remember { mutableStateOf<List<TimelineEntry>>(emptyList()) }
    
    // Documents
    var documents by remember { mutableStateOf<List<File>>(emptyList()) }
    
    // Custom Fields
    val customFieldsManager = remember { CustomFieldsManager(context) }
    val customFields by customFieldsManager.getAllActiveFields().collectAsState(initial = emptyList())
    val customFieldValues by customFieldsManager.getValuesForLead(currentLead.id).collectAsState(initial = emptyMap())
    
    LaunchedEffect(currentLead.id) {
        timelineEntries = timelineManager.getTimelineForLeadList(currentLead.id)
        documents = getLeadDocuments(context, currentLead.id)
    }
    
    val statusColor = Color(currentLead.status.color)
    
    // Handle back press
    BackHandler { onBack() }
    
    // Document picker
    val documentPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            coroutineScope.launch {
                saveDocumentForLead(context, currentLead.id, it)
                documents = getLeadDocuments(context, currentLead.id)
                timelineManager.addCustomEvent(currentLead.id, "Document Added", "New document uploaded")
                timelineEntries = timelineManager.getTimelineForLeadList(currentLead.id)
            }
        }
    }
    
    // Show Add Product Screen
    if (showAddProductScreen) {
        AddEditProductScreen(
            product = null,
            fieldSettings = leadManager.getProductFieldSettings(),
            onBack = { showAddProductScreen = false },
            onSave = { product ->
                leadManager.addProductV2(product)
                leadManager.addProduct(product.name)
                showAddProductScreen = false
            },
            onOpenSettings = { }
        )
        return
    }

    Scaffold(
        floatingActionButton = {
            Column(horizontalAlignment = Alignment.End) {
                if (showFabMenu) {
                    FabMenuItem("Add Follow-up", Icons.Default.Schedule, Color(0xFF3B82F6)) {
                        showFollowUpDialog = true
                        showFabMenu = false
                    }
                    Spacer(Modifier.height(8.dp))
                    FabMenuItem("Add Document", Icons.Default.AttachFile, Color(0xFF8B5CF6)) {
                        documentPicker.launch("*/*")
                        showFabMenu = false
                    }
                    Spacer(Modifier.height(8.dp))
                    FabMenuItem("Change Status", Icons.Default.Flag, Color(0xFFF59E0B)) {
                        showStatusDialog = true
                        showFabMenu = false
                    }
                    Spacer(Modifier.height(12.dp))
                }
                FloatingActionButton(
                    onClick = { showFabMenu = !showFabMenu },
                    containerColor = Color(0xFF10B981)
                ) {
                    Icon(
                        if (showFabMenu) Icons.Default.Close else Icons.Default.Add,
                        null,
                        tint = Color.White
                    )
                }
            }
        },
        containerColor = Color(0xFF0f0c29)
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // Header
            LeadPreviewHeader(
                lead = currentLead,
                statusColor = statusColor,
                onBack = onBack,
                onStatusClick = { showStatusDialog = true }
            )
            
            // Tab Row
            ScrollableTabRow(
                selectedTabIndex = selectedTab.ordinal,
                containerColor = Color(0xFF1a1a2e),
                contentColor = Color.White,
                edgePadding = 0.dp
            ) {
                LeadPreviewTab.entries.forEach { tab ->
                    Tab(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        icon = {
                            Icon(
                                when (tab) {
                                    LeadPreviewTab.DETAILS -> Icons.Default.Person
                                    LeadPreviewTab.PRODUCT -> Icons.Default.Inventory
                                    LeadPreviewTab.FOLLOWUP -> Icons.Default.Schedule
                                    LeadPreviewTab.NOTES -> Icons.Default.Edit
                                    LeadPreviewTab.PAYMENTS -> Icons.Default.AccountBalanceWallet
                                    LeadPreviewTab.DOCUMENTS -> Icons.Default.Folder
                                    LeadPreviewTab.TIMELINE -> Icons.Default.Timeline
                                },
                                null,
                                modifier = Modifier.size(20.dp)
                            )
                        },
                        selectedContentColor = statusColor,
                        unselectedContentColor = Color(0xFF64748B)
                    )
                }
            }

            // Tab Content
            when (selectedTab) {
                LeadPreviewTab.DETAILS -> DetailsTabContentV2(
                    lead = currentLead,
                    leadScore = leadScore,
                    leadManager = leadManager,
                    customFields = customFields,
                    customFieldValues = customFieldValues,
                    customFieldsManager = customFieldsManager,
                    onScoreChange = { newScore ->
                        leadScore = newScore
                        currentLead = currentLead.copy(leadScore = newScore)
                        leadManager.updateLead(currentLead)
                    },
                    onEditName = { showEditNameDialog = true },
                    onEditPhone = { showEditPhoneDialog = true },
                    onEditEmail = { showEditEmailDialog = true },
                    onEditStatus = { showStatusDialog = true },
                    onEditPriority = { showPriorityDialog = true },
                    onProductSelected = { product ->
                        currentLead = currentLead.copy(product = product)
                        leadManager.updateLead(currentLead)
                        onLeadUpdated()
                    },
                    onAddProduct = { showAddProductScreen = true },
                    onTagsUpdated = { onLeadUpdated() }
                )
                LeadPreviewTab.PRODUCT -> ProductTabContentV2(
                    lead = currentLead,
                    leadManager = leadManager
                )
                LeadPreviewTab.FOLLOWUP -> FollowUpTabContentV2(
                    lead = currentLead,
                    leadManager = leadManager,
                    onAddFollowUp = { showFollowUpDialog = true },
                    onFollowUpUpdated = {
                        coroutineScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                            val updatedLead = leadManager.getAllLeadsSuspend().find { it.id == currentLead.id }
                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                currentLead = updatedLead ?: currentLead
                                onLeadUpdated()
                            }
                        }
                    }
                )
                LeadPreviewTab.NOTES -> NotesTabContentV2(
                    lead = currentLead,
                    leadManager = leadManager,
                    timelineManager = timelineManager,
                    onLeadUpdated = {
                        coroutineScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                            val updatedLead = leadManager.getAllLeadsSuspend().find { it.id == currentLead.id }
                            val entries = timelineManager.getTimelineForLeadList(currentLead.id)
                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                currentLead = updatedLead ?: currentLead
                                timelineEntries = entries
                                onLeadUpdated()
                            }
                        }
                    }
                )
                LeadPreviewTab.PAYMENTS -> PaymentsTabContent(
                    leadId = currentLead.id,
                    leadName = currentLead.name,
                    leadPhone = currentLead.phoneNumber
                )
                LeadPreviewTab.DOCUMENTS -> DocumentsTabContent(
                    documents = documents,
                    onAddDocument = { documentPicker.launch("*/*") },
                    onOpenDocument = { file ->
                        try {
                            val uri = androidx.core.content.FileProvider.getUriForFile(
                                context,
                                "${context.packageName}.provider",
                                file
                            )
                            val intent = Intent(Intent.ACTION_VIEW).apply {
                                setDataAndType(uri, context.contentResolver.getType(uri) ?: "*/*")
                                flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                            }
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                )
                LeadPreviewTab.TIMELINE -> TimelineTabContentV2(entries = timelineEntries)
            }
        }
    }
    
    // Dialogs
    if (showEditNameDialog) {
        EditFieldDialog(
            title = "Edit Name",
            currentValue = currentLead.name,
            onDismiss = { showEditNameDialog = false },
            onSave = { newValue ->
                currentLead = currentLead.copy(name = newValue)
                leadManager.updateLead(currentLead)
                onLeadUpdated()
                showEditNameDialog = false
            }
        )
    }
    
    if (showEditPhoneDialog) {
        EditFieldDialog(
            title = "Edit Phone",
            currentValue = currentLead.phoneNumber,
            onDismiss = { showEditPhoneDialog = false },
            onSave = { newValue ->
                currentLead = currentLead.copy(phoneNumber = newValue)
                leadManager.updateLead(currentLead)
                onLeadUpdated()
                showEditPhoneDialog = false
            }
        )
    }
    
    if (showEditEmailDialog) {
        EditFieldDialog(
            title = "Edit Email",
            currentValue = currentLead.email,
            onDismiss = { showEditEmailDialog = false },
            onSave = { newValue ->
                currentLead = currentLead.copy(email = newValue)
                leadManager.updateLead(currentLead)
                onLeadUpdated()
                showEditEmailDialog = false
            }
        )
    }
    
    if (showStatusDialog) {
        StatusSelectionDialog(
            currentStatus = currentLead.status,
            onDismiss = { showStatusDialog = false },
            onStatusSelected = { newStatus ->
                coroutineScope.launch {
                    timelineManager.addStatusChangedEvent(currentLead.id, currentLead.status.displayName, newStatus.displayName)
                    currentLead = currentLead.copy(status = newStatus)
                    leadManager.updateLead(currentLead)
                    timelineEntries = timelineManager.getTimelineForLeadList(currentLead.id)
                    onLeadUpdated()
                }
                showStatusDialog = false
            }
        )
    }
    
    if (showPriorityDialog) {
        PrioritySelectionDialog(
            currentPriority = currentLead.priority,
            onDismiss = { showPriorityDialog = false },
            onPrioritySelected = { newPriority ->
                currentLead = currentLead.copy(priority = newPriority)
                leadManager.updateLead(currentLead)
                onLeadUpdated()
                showPriorityDialog = false
            }
        )
    }
    
    if (showFollowUpDialog) {
        FollowUpDialogV2(
            leadId = currentLead.id,
            onDismiss = { showFollowUpDialog = false },
            onFollowUpAdded = { followUp ->
                coroutineScope.launch {
                    val updatedFollowUps = currentLead.followUps + followUp
                    currentLead = currentLead.copy(
                        followUps = updatedFollowUps,
                        nextFollowUpDate = followUp.scheduledDate
                    )
                    leadManager.updateLead(currentLead)
                    timelineManager.addFollowUpScheduledEvent(currentLead.id, followUp.title, followUp.scheduledDate)
                    timelineEntries = timelineManager.getTimelineForLeadList(currentLead.id)
                    onLeadUpdated()
                }
                showFollowUpDialog = false
            }
        )
    }
}


@Composable
fun LeadPreviewHeader(
    lead: Lead,
    statusColor: Color,
    onBack: () -> Unit,
    onStatusClick: () -> Unit
) {
    val context = LocalContext.current
    val backgroundBrush = Brush.verticalGradient(
        colors = listOf(
            statusColor,
            statusColor.copy(alpha = 0.8f),
            statusColor.copy(alpha = 0.5f),
            Color(0xFF1a1a2e)
        )
    )
    
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(backgroundBrush)
            .statusBarsPadding()
            .padding(16.dp)
    ) {
        // Decorative circles
        Box(
            modifier = Modifier
                .size(120.dp)
                .align(Alignment.TopEnd)
                .offset(x = 40.dp, y = (-30).dp)
                .background(Color.White.copy(alpha = 0.1f), CircleShape)
        )
        Box(
            modifier = Modifier
                .size(80.dp)
                .align(Alignment.TopStart)
                .offset(x = (-20).dp, y = 50.dp)
                .background(Color.White.copy(alpha = 0.08f), CircleShape)
        )
        
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Status Badge with glow effect
                Surface(
                    color = Color.White.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.clickable { onStatusClick() }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(Color.White, CircleShape)
                        )
                        Text(
                            lead.status.displayName.uppercase(),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Icon(
                            Icons.Default.KeyboardArrowDown,
                            null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                
                // Close button
                Surface(
                    color = Color.White.copy(alpha = 0.2f),
                    shape = CircleShape,
                    modifier = Modifier.clickable { onBack() }
                ) {
                    Icon(
                        Icons.Default.Close,
                        "Close",
                        tint = Color.White,
                        modifier = Modifier.padding(8.dp).size(20.dp)
                    )
                }
            }
            
            Spacer(Modifier.height(16.dp))
            
            // Avatar with border
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .background(Color.White.copy(alpha = 0.2f), CircleShape)
                    .padding(4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.White, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        lead.name.take(2).uppercase(),
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = statusColor
                    )
                }
            }
            
            Spacer(Modifier.height(12.dp))
            
            // Name
            Text(
                lead.name,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            
            // Phone number
            Text(
                lead.phoneNumber,
                fontSize = 14.sp,
                color = Color.White.copy(alpha = 0.8f)
            )
            
            Spacer(Modifier.height(16.dp))
            
            // Quick Actions Row
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                QuickActionCircleV2("Call", Icons.Default.Phone, Color(0xFF3B82F6)) {
                    context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${lead.phoneNumber}")))
                }
                QuickActionCircleV2("SMS", Icons.Default.Sms, Color(0xFFF59E0B)) {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("sms:${lead.phoneNumber}")))
                }
                QuickActionCircleV2("WA", Icons.Default.Chat, Color(0xFF25D366)) {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/${lead.phoneNumber}")))
                }
                QuickActionCircleV2("Email", Icons.Default.Email, Color(0xFFEF4444)) {
                    if (lead.email.isNotEmpty()) context.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:${lead.email}")))
                }
                QuickActionCircleV2("Map", Icons.Default.LocationOn, Color(0xFF8B5CF6)) { }
            }
        }
    }
}

@Composable
fun QuickActionCircleV2(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .size(50.dp)
                .background(Color.White.copy(alpha = 0.15f), CircleShape)
                .padding(3.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(color, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = Color.White, modifier = Modifier.size(24.dp))
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            label,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = Color.White
        )
    }
}

@Composable
fun FabMenuItem(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color, onClick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable(onClick = onClick)) {
        Surface(color = Color(0xFF1a1a2e), shape = RoundedCornerShape(8.dp)) {
            Text(label, fontSize = 12.sp, color = Color.White, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
        }
        Spacer(Modifier.width(8.dp))
        FloatingActionButton(onClick = onClick, containerColor = color, modifier = Modifier.size(40.dp)) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(20.dp))
        }
    }
}


// Details Tab V2 with editable fields
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailsTabContentV2(
    lead: Lead,
    leadScore: Int,
    leadManager: LeadManager,
    customFields: List<CustomField> = emptyList(),
    customFieldValues: Map<String, String> = emptyMap(),
    customFieldsManager: CustomFieldsManager? = null,
    onScoreChange: (Int) -> Unit,
    onEditName: () -> Unit,
    onEditPhone: () -> Unit,
    onEditEmail: () -> Unit,
    onEditStatus: () -> Unit,
    onEditPriority: () -> Unit,
    onProductSelected: (String) -> Unit,
    onAddProduct: () -> Unit,
    onTagsUpdated: (Lead) -> Unit = {}
) {
    val coroutineScope = rememberCoroutineScope()
    var showProductDropdown by remember { mutableStateOf(false) }
    val products = remember { leadManager.getAllProducts() }
    
    // Tags state at function level to persist across scrolls
    var showTagsDialog by remember { mutableStateOf(false) }
    val allTags = remember { leadManager.getAllTags() }
    var currentTags by remember(lead.id) { mutableStateOf(lead.tags) }
    
    // Update currentTags when lead changes
    LaunchedEffect(lead.tags) {
        currentTags = lead.tags
    }
    
    // Tags Dialog at function level
    if (showTagsDialog) {
        TagsSelectionDialog(
            allTags = allTags,
            selectedTags = currentTags,
            onDismiss = { showTagsDialog = false },
            onTagsSelected = { newTags ->
                currentTags = newTags
                showTagsDialog = false
            },
            onConfirm = { finalTags ->
                currentTags = finalTags
                val updatedLead = lead.copy(tags = finalTags)
                leadManager.updateLead(updatedLead)
                onTagsUpdated(updatedLead)
                showTagsDialog = false
            }
        )
    }
    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // Contact Info Card
        item {
            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFF1a1a2e)), shape = RoundedCornerShape(16.dp)) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("Contact Information", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF10B981))
                    EditableInfoRow(Icons.Default.Phone, "Phone", lead.phoneNumber, Color(0xFF3B82F6), onEditPhone)
                    if (lead.alternatePhone.isNotEmpty()) {
                        EditableInfoRow(Icons.Default.PhoneAndroid, "Alternate", lead.alternatePhone, Color(0xFF06B6D4)) { }
                    }
                    EditableInfoRow(Icons.Default.Email, "Email", lead.email.ifEmpty { "Not set" }, Color(0xFFEC4899), onEditEmail)
                }
            }
        }
        
        // Lead Score
        item {
            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFF1a1a2e)), shape = RoundedCornerShape(16.dp)) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text("Lead Score : $leadScore", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF10B981))
                    Spacer(Modifier.height(12.dp))
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Slider(
                            value = leadScore.toFloat(), onValueChange = { onScoreChange(it.toInt()) }, valueRange = 0f..100f, modifier = Modifier.weight(1f),
                            colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = when { leadScore < 30 -> Color(0xFFEF4444); leadScore < 60 -> Color(0xFFF59E0B); else -> Color(0xFF10B981) }, inactiveTrackColor = Color(0xFF334155))
                        )
                        Text(when { leadScore < 30 -> "😟"; leadScore < 60 -> "😐"; leadScore < 80 -> "😊"; else -> "🤩" }, fontSize = 28.sp, modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        }
        
        // Lead Details Card
        item {
            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFF1a1a2e)), shape = RoundedCornerShape(16.dp)) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("Lead Details", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF8B5CF6))
                    EditableInfoRow(Icons.Default.Person, "Name", lead.name, Color(0xFF10B981), onEditName)
                    EditableInfoRow(Icons.Default.Category, "Category", lead.category, Color(0xFFF59E0B)) { }
                    EditableInfoRow(Icons.Default.Source, "Source", lead.source, Color(0xFF06B6D4)) { }
                    EditableInfoRow(Icons.Default.Flag, "Priority", lead.priority.displayName, Color(lead.priority.color), onEditPriority)
                    EditableInfoRow(Icons.Default.TrendingUp, "Status", lead.status.displayName, Color(lead.status.color), onEditStatus)
                    
                    // Product Dropdown
                    ExposedDropdownMenuBox(expanded = showProductDropdown, onExpandedChange = { showProductDropdown = it }) {
                        Row(
                            modifier = Modifier.fillMaxWidth().menuAnchor().clickable { showProductDropdown = true },
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Inventory, null, tint = Color(0xFFF59E0B), modifier = Modifier.size(20.dp))
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text("Product", fontSize = 12.sp, color = Color(0xFF64748B))
                                Text(lead.product.ifEmpty { "Select Product" }, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Color.White)
                            }
                            Icon(Icons.Default.ArrowDropDown, null, tint = Color(0xFF64748B))
                        }
                        ExposedDropdownMenu(expanded = showProductDropdown, onDismissRequest = { showProductDropdown = false }) {
                            products.forEach { product ->
                                DropdownMenuItem(
                                    text = { Text(product) },
                                    onClick = { onProductSelected(product); showProductDropdown = false },
                                    leadingIcon = { Icon(Icons.Default.Inventory, null, tint = Color(0xFFF59E0B)) }
                                )
                            }
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text("+ Add New Product", color = Color(0xFF10B981)) },
                                onClick = { onAddProduct(); showProductDropdown = false },
                                leadingIcon = { Icon(Icons.Default.Add, null, tint = Color(0xFF10B981)) }
                            )
                        }
                    }
                }
            }
        }
        
        // Tags - Always show (editable) - using function level state
        item(key = "tags_${lead.id}") {
            Card(
                modifier = Modifier.fillMaxWidth().clickable { showTagsDialog = true },
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1a1a2e)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Label, null, tint = Color(0xFF8B5CF6), modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Tags", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Color.White)
                        }
                        Icon(Icons.Default.Edit, null, tint = Color(0xFF64748B), modifier = Modifier.size(18.dp))
                    }
                    Spacer(Modifier.height(12.dp))
                    if (currentTags.isNotEmpty()) {
                        Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            currentTags.forEach { tag ->
                                Surface(color = Color(0xFF8B5CF6).copy(alpha = 0.2f), shape = RoundedCornerShape(8.dp)) {
                                    Text(tag, fontSize = 12.sp, color = Color(0xFF8B5CF6), modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
                                }
                            }
                        }
                    } else {
                        Text("Tap to add tags", fontSize = 13.sp, color = Color(0xFF64748B))
                    }
                }
            }
        }
        
        // Custom Fields Card
        if (customFields.isNotEmpty()) {
            item(key = "custom_fields_${lead.id}") {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1a1a2e)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.DynamicForm, null, tint = Color(0xFFEC4899), modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Custom Fields", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFFEC4899))
                        }
                        Spacer(Modifier.height(16.dp))
                        
                        customFields.forEach { field ->
                            val value = customFieldValues[field.id] ?: ""
                            var isEditing by remember { mutableStateOf(false) }
                            var editValue by remember(value) { mutableStateOf(value) }
                            
                            if (isEditing) {
                                CustomFieldInput(
                                    field = field,
                                    value = editValue,
                                    onValueChange = { newValue ->
                                        editValue = newValue
                                        // Auto-save on change
                                        customFieldsManager?.let { manager ->
                                            coroutineScope.launch {
                                                manager.saveFieldValue(lead.id, field.id, newValue)
                                            }
                                        }
                                    }
                                )
                                Spacer(Modifier.height(8.dp))
                                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                                    TextButton(onClick = { isEditing = false }) {
                                        Text("Done", color = Color(0xFF10B981))
                                    }
                                }
                            } else {
                                Row(
                                    modifier = Modifier.fillMaxWidth().clickable { isEditing = true }.padding(vertical = 8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        getIconForFieldType(field.fieldType),
                                        null,
                                        tint = getIconTintForFieldType(field.fieldType),
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row {
                                            Text(field.fieldName, fontSize = 12.sp, color = Color(0xFF94A3B8))
                                            if (field.isRequired) {
                                                Text(" *", color = Color(0xFFEF4444), fontSize = 12.sp)
                                            }
                                        }
                                        Text(
                                            if (value.isNotEmpty()) value else "Not set",
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = if (value.isNotEmpty()) Color.White else Color(0xFF64748B)
                                        )
                                    }
                                    Icon(Icons.Default.Edit, null, tint = Color(0xFF64748B), modifier = Modifier.size(16.dp))
                                }
                            }
                            
                            if (customFields.last() != field) {
                                HorizontalDivider(color = Color(0xFF334155), modifier = Modifier.padding(vertical = 4.dp))
                            }
                        }
                    }
                }
            }
        }
        
        // Notes Preview Card - Modern Timeline-based
        item {
            var showNotesScreen by remember { mutableStateOf(false) }
            var showQuickAddNote by remember { mutableStateOf(false) }
            
            com.message.bulksend.leadmanager.notes.NotesPreviewCard(
                leadId = lead.id,
                onViewAllNotes = { showNotesScreen = true },
                onAddNote = { showQuickAddNote = true }
            )
            
            // Quick Add Note Dialog
            if (showQuickAddNote) {
                com.message.bulksend.leadmanager.notes.QuickAddNoteDialog(
                    leadId = lead.id,
                    onDismiss = { showQuickAddNote = false },
                    onNoteAdded = { showQuickAddNote = false }
                )
            }
        }
        
        // Legacy notes preview (if any old notes exist)
        if (lead.notes.isNotEmpty()) {
            item {
                Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFF1a1a2e).copy(alpha = 0.5f)), shape = RoundedCornerShape(16.dp)) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.History, null, tint = Color(0xFF64748B), modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Legacy Notes", fontSize = 14.sp, color = Color(0xFF64748B))
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(lead.notes, fontSize = 13.sp, color = Color(0xFF94A3B8), maxLines = 3)
                    }
                }
            }
        }
        
        item { Spacer(Modifier.height(80.dp)) }
    }
}

@Composable
fun EditableInfoRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: String, color: Color, onClick: () -> Unit = {}) {
    Row(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = color, modifier = Modifier.size(20.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, fontSize = 12.sp, color = Color(0xFF64748B))
            Text(value, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Color.White)
        }
        Icon(Icons.Default.Edit, null, tint = Color(0xFF64748B), modifier = Modifier.size(16.dp))
    }
}


// Product Tab V2 - Shows selected product details
@Composable
fun ProductTabContentV2(lead: Lead, leadManager: LeadManager) {
    val selectedProductDetails = remember(lead.product) {
        if (lead.product.isNotEmpty()) {
            leadManager.getProductByName(lead.product)
        } else null
    }
    
    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (lead.product.isEmpty()) {
            item {
                Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Inventory, null, tint = Color(0xFF64748B), modifier = Modifier.size(64.dp))
                        Spacer(Modifier.height(16.dp))
                        Text("No product selected", color = Color(0xFF94A3B8), fontSize = 16.sp)
                        Spacer(Modifier.height(8.dp))
                        Text("Select a product from Details tab", color = Color(0xFF64748B), fontSize = 12.sp)
                    }
                }
            }
        } else if (selectedProductDetails == null) {
            // Product name exists but no full details - show basic info
            item {
                Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFFF59E0B).copy(alpha = 0.1f)), shape = RoundedCornerShape(16.dp)) {
                    Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(modifier = Modifier.size(64.dp).background(Color(0xFFF59E0B).copy(alpha = 0.2f), CircleShape), contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Inventory, null, tint = Color(0xFFF59E0B), modifier = Modifier.size(32.dp))
                        }
                        Spacer(Modifier.height(12.dp))
                        Text(lead.product, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Spacer(Modifier.height(4.dp))
                        Surface(color = Color(0xFFF59E0B).copy(alpha = 0.2f), shape = RoundedCornerShape(8.dp)) {
                            Text("Product", fontSize = 12.sp, color = Color(0xFFF59E0B), modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
                        }
                        Spacer(Modifier.height(16.dp))
                        Text("Full product details not available", fontSize = 12.sp, color = Color(0xFF64748B))
                        Text("Add product details from Settings > Products", fontSize = 11.sp, color = Color(0xFF64748B))
                    }
                }
            }
        } else {
            // Product Header
            item {
                Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(selectedProductDetails.type.color).copy(alpha = 0.1f)), shape = RoundedCornerShape(16.dp)) {
                    Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(modifier = Modifier.size(64.dp).background(Color(selectedProductDetails.type.color).copy(alpha = 0.2f), CircleShape), contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Inventory, null, tint = Color(selectedProductDetails.type.color), modifier = Modifier.size(32.dp))
                        }
                        Spacer(Modifier.height(12.dp))
                        Text(selectedProductDetails.name, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Spacer(Modifier.height(4.dp))
                        Surface(color = Color(selectedProductDetails.type.color).copy(alpha = 0.2f), shape = RoundedCornerShape(8.dp)) {
                            Text(selectedProductDetails.type.displayName, fontSize = 12.sp, color = Color(selectedProductDetails.type.color), modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
                        }
                    }
                }
            }
            
            // Pricing Card
            if (selectedProductDetails.mrp.isNotEmpty() || selectedProductDetails.sellingPrice.isNotEmpty()) {
                item {
                    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFF1a1a2e)), shape = RoundedCornerShape(16.dp)) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Text("Pricing", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF10B981))
                            Spacer(Modifier.height(16.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                                if (selectedProductDetails.mrp.isNotEmpty()) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("MRP", fontSize = 12.sp, color = Color(0xFF64748B))
                                        Text("₹${selectedProductDetails.mrp}", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF94A3B8))
                                    }
                                }
                                if (selectedProductDetails.sellingPrice.isNotEmpty()) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("Selling Price", fontSize = 12.sp, color = Color(0xFF64748B))
                                        Text("₹${selectedProductDetails.sellingPrice}", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color(0xFF10B981))
                                    }
                                }
                            }
                        }
                    }
                }
            }
            
            // Product Details Card
            item {
                Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFF1a1a2e)), shape = RoundedCornerShape(16.dp)) {
                    Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Product Details", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF8B5CF6))
                        
                        if (selectedProductDetails.category.isNotEmpty()) {
                            ProductDetailRow("Category", selectedProductDetails.category)
                        }
                        if (selectedProductDetails.subcategory.isNotEmpty()) {
                            ProductDetailRow("Subcategory", selectedProductDetails.subcategory)
                        }
                        if (selectedProductDetails.description.isNotEmpty()) {
                            ProductDetailRow("Description", selectedProductDetails.description)
                        }
                        
                        // Type-specific fields
                        when (selectedProductDetails.type) {
                            ProductType.PHYSICAL -> {
                                if (selectedProductDetails.color.isNotEmpty()) ProductDetailRow("Color", selectedProductDetails.color)
                                if (selectedProductDetails.size.isNotEmpty()) ProductDetailRow("Size", selectedProductDetails.size)
                                if (selectedProductDetails.weight.isNotEmpty()) ProductDetailRow("Weight", selectedProductDetails.weight)
                                if (selectedProductDetails.height.isNotEmpty()) ProductDetailRow("Height", selectedProductDetails.height)
                                if (selectedProductDetails.width.isNotEmpty()) ProductDetailRow("Width", selectedProductDetails.width)
                            }
                            ProductType.DIGITAL, ProductType.SOFTWARE -> {
                                if (selectedProductDetails.version.isNotEmpty()) ProductDetailRow("Version", selectedProductDetails.version)
                                if (selectedProductDetails.licenseType.isNotEmpty()) ProductDetailRow("License", selectedProductDetails.licenseType)
                                if (selectedProductDetails.downloadLink.isNotEmpty()) ProductDetailRow("Download", selectedProductDetails.downloadLink)
                            }
                            ProductType.SERVICE -> {
                                ProductDetailRow("Service Type", selectedProductDetails.serviceType.displayName)
                                if (selectedProductDetails.duration.isNotEmpty()) ProductDetailRow("Duration", selectedProductDetails.duration)
                                if (selectedProductDetails.deliveryTime.isNotEmpty()) ProductDetailRow("Delivery", selectedProductDetails.deliveryTime)
                            }
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(80.dp)) }
    }
}

@Composable
fun ProductDetailRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(label, fontSize = 13.sp, color = Color(0xFF64748B), modifier = Modifier.width(100.dp))
        Text(value, fontSize = 13.sp, color = Color.White, modifier = Modifier.weight(1f))
    }
}

// Follow-up Tab V2
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FollowUpTabContentV2(lead: Lead, leadManager: LeadManager, onAddFollowUp: () -> Unit, onFollowUpUpdated: () -> Unit) {
    val dateFormat = SimpleDateFormat("MMM dd, yyyy 'at' HH:mm", Locale.getDefault())
    val coroutineScope = rememberCoroutineScope()
    
    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Follow-ups", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Button(onClick = onAddFollowUp, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3B82F6)), shape = RoundedCornerShape(8.dp)) {
                    Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Add")
                }
            }
        }
        
        if (lead.followUps.isEmpty()) {
            item {
                Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Schedule, null, tint = Color(0xFF64748B), modifier = Modifier.size(64.dp))
                        Spacer(Modifier.height(16.dp))
                        Text("No follow-ups scheduled", color = Color(0xFF94A3B8))
                    }
                }
            }
        } else {
            items(lead.followUps.sortedByDescending { it.scheduledDate }) { followUp ->
                Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFF1a1a2e)), shape = RoundedCornerShape(12.dp)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier.size(48.dp).background(if (followUp.isCompleted) Color(0xFF10B981).copy(alpha = 0.2f) else Color(followUp.type.color).copy(alpha = 0.2f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(if (followUp.isCompleted) Icons.Default.CheckCircle else Icons.Default.Schedule, null, tint = if (followUp.isCompleted) Color(0xFF10B981) else Color(followUp.type.color))
                            }
                            Spacer(Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(followUp.title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                Text(dateFormat.format(Date(followUp.scheduledDate)), fontSize = 13.sp, color = Color(0xFF94A3B8))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Surface(color = Color(followUp.type.color).copy(alpha = 0.2f), shape = RoundedCornerShape(4.dp)) {
                                        Text(followUp.type.displayName, fontSize = 11.sp, color = Color(followUp.type.color), modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
                                    }
                                }
                            }
                        }
                        
                        if (!followUp.isCompleted) {
                            Spacer(Modifier.height(12.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    onClick = {
                                        coroutineScope.launch {
                                            val updated = followUp.copy(isCompleted = true, completedDate = System.currentTimeMillis())
                                            val newFollowUps = lead.followUps.map { if (it.id == followUp.id) updated else it }
                                            leadManager.updateLead(lead.copy(followUps = newFollowUps))
                                            onFollowUpUpdated()
                                        }
                                    },
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF10B981)),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Complete", fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(80.dp)) }
    }
}


// Notes Tab V2 - Modern Timeline-based Notes
@Composable
fun NotesTabContentV2(lead: Lead, leadManager: LeadManager, timelineManager: TimelineManager, onLeadUpdated: () -> Unit) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val notesManager = remember { com.message.bulksend.leadmanager.notes.NotesManager(context) }
    
    var noteGroups by remember { mutableStateOf<List<com.message.bulksend.leadmanager.notes.NoteGroup>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var showAddNoteSheet by remember { mutableStateOf(false) }
    var editingNote by remember { mutableStateOf<com.message.bulksend.leadmanager.notes.Note?>(null) }
    var replyingToNote by remember { mutableStateOf<com.message.bulksend.leadmanager.notes.Note?>(null) }
    var showQuickAddDialog by remember { mutableStateOf(false) }
    
    // Load notes
    fun loadNotes() {
        coroutineScope.launch {
            isLoading = true
            noteGroups = notesManager.getNotesGroupedByDate(lead.id)
            isLoading = false
        }
    }
    
    LaunchedEffect(lead.id) {
        loadNotes()
    }
    
    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Quick Add Bar
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1a1a2e)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showQuickAddDialog = true }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(Color(0xFF8B5CF6).copy(alpha = 0.2f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Add, null, tint = Color(0xFF8B5CF6))
                    }
                    Text(
                        "Add a note...",
                        fontSize = 14.sp,
                        color = Color(0xFF64748B),
                        modifier = Modifier.weight(1f)
                    )
                    Icon(Icons.Default.Edit, null, tint = Color(0xFF64748B), modifier = Modifier.size(20.dp))
                }
            }
            
            // Notes Timeline
            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = Color(0xFF8B5CF6))
                }
            } else {
                com.message.bulksend.leadmanager.notes.NotesTimeline(
                    noteGroups = noteGroups,
                    onNoteClick = { /* Expand handled internally */ },
                    onEditNote = { editingNote = it },
                    onDeleteNote = { note ->
                        coroutineScope.launch {
                            notesManager.deleteNote(note.id)
                            loadNotes()
                            // Also add to timeline
                            timelineManager.addCustomEvent(lead.id, "Note Deleted", "Note '${note.title}' was deleted")
                        }
                    },
                    onPinNote = { note ->
                        coroutineScope.launch {
                            notesManager.togglePin(note.id)
                            loadNotes()
                        }
                    },
                    onAddReply = { replyingToNote = it }
                )
            }
        }
        
        // FAB for full note editor
        FloatingActionButton(
            onClick = { showAddNoteSheet = true },
            containerColor = Color(0xFF8B5CF6),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        ) {
            Icon(Icons.Default.NoteAdd, null, tint = Color.White)
        }
    }
    
    // Quick Add Dialog
    if (showQuickAddDialog) {
        com.message.bulksend.leadmanager.notes.QuickAddNoteDialog(
            leadId = lead.id,
            onDismiss = { showQuickAddDialog = false },
            onNoteAdded = {
                showQuickAddDialog = false
                loadNotes()
                // Add to timeline
                coroutineScope.launch {
                    timelineManager.addNoteEvent(lead.id, "New note added")
                }
                onLeadUpdated()
            }
        )
    }
    
    // Full Add/Edit Note Sheet
    if (showAddNoteSheet || editingNote != null) {
        com.message.bulksend.leadmanager.notes.AddEditNoteSheet(
            note = editingNote,
            onDismiss = {
                showAddNoteSheet = false
                editingNote = null
            },
            onSave = { title, content, noteType, priority, tags ->
                coroutineScope.launch {
                    if (editingNote != null) {
                        notesManager.updateNote(
                            id = editingNote!!.id,
                            title = title,
                            content = content,
                            noteType = noteType,
                            priority = priority,
                            tags = tags
                        )
                        timelineManager.addCustomEvent(lead.id, "Note Updated", "Note '$title' was updated")
                    } else {
                        notesManager.addNote(
                            leadId = lead.id,
                            title = title,
                            content = content,
                            noteType = noteType,
                            priority = priority,
                            tags = tags
                        )
                        timelineManager.addNoteEvent(lead.id, "Note: $title")
                    }
                    showAddNoteSheet = false
                    editingNote = null
                    loadNotes()
                    onLeadUpdated()
                }
            }
        )
    }
    
    // Reply Sheet
    if (replyingToNote != null) {
        com.message.bulksend.leadmanager.notes.ReplyNoteSheet(
            parentNote = replyingToNote!!,
            onDismiss = { replyingToNote = null },
            onReply = { content ->
                coroutineScope.launch {
                    notesManager.addReply(replyingToNote!!.id, content)
                    replyingToNote = null
                    loadNotes()
                }
            }
        )
    }
}

// Documents Tab
@Composable
fun DocumentsTabContent(documents: List<File>, onAddDocument: () -> Unit, onOpenDocument: (File) -> Unit) {
    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Documents", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Button(onClick = onAddDocument, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8B5CF6)), shape = RoundedCornerShape(8.dp)) {
                    Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Add")
                }
            }
        }
        
        if (documents.isEmpty()) {
            item {
                Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Folder, null, tint = Color(0xFF64748B), modifier = Modifier.size(64.dp))
                        Spacer(Modifier.height(16.dp))
                        Text("No documents yet", color = Color(0xFF94A3B8))
                        Spacer(Modifier.height(8.dp))
                        Text("Tap + to add documents", color = Color(0xFF64748B), fontSize = 12.sp)
                    }
                }
            }
        } else {
            items(documents) { file ->
                Card(modifier = Modifier.fillMaxWidth().clickable { onOpenDocument(file) }, colors = CardDefaults.cardColors(containerColor = Color(0xFF1a1a2e)), shape = RoundedCornerShape(12.dp)) {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(48.dp).background(Color(0xFF8B5CF6).copy(alpha = 0.2f), RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
                            Icon(
                                when {
                                    file.name.endsWith(".pdf") -> Icons.Default.PictureAsPdf
                                    file.name.endsWith(".jpg") || file.name.endsWith(".png") -> Icons.Default.Image
                                    else -> Icons.Default.InsertDriveFile
                                },
                                null, tint = Color(0xFF8B5CF6)
                            )
                        }
                        Spacer(Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(file.name, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Color.White)
                            Text("${file.length() / 1024} KB", fontSize = 12.sp, color = Color(0xFF64748B))
                        }
                        Icon(Icons.Default.ChevronRight, null, tint = Color(0xFF64748B))
                    }
                }
            }
        }
        item { Spacer(Modifier.height(80.dp)) }
    }
}

// Timeline Tab V2
@Composable
fun TimelineTabContentV2(entries: List<TimelineEntry>) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item { VerticalTimeline(entries = entries) }
        item { Spacer(Modifier.height(80.dp)) }
    }
}


// Dialogs
@Composable
fun EditFieldDialog(title: String, currentValue: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var value by remember { mutableStateOf(currentValue) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, color = Color.White) },
        text = {
            OutlinedTextField(
                value = value, onValueChange = { value = it },
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color(0xFF10B981), unfocusedBorderColor = Color(0xFF64748B), focusedTextColor = Color.White, unfocusedTextColor = Color.White),
                singleLine = true
            )
        },
        confirmButton = { TextButton(onClick = { onSave(value) }) { Text("Save", color = Color(0xFF10B981)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = Color(0xFF94A3B8)) } },
        containerColor = Color(0xFF1a1a2e)
    )
}

@Composable
fun PrioritySelectionDialog(currentPriority: LeadPriority, onDismiss: () -> Unit, onPrioritySelected: (LeadPriority) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Priority", color = Color.White) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                LeadPriority.entries.forEach { priority ->
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable { onPrioritySelected(priority) },
                        colors = CardDefaults.cardColors(containerColor = if (priority == currentPriority) Color(priority.color).copy(alpha = 0.2f) else Color(0xFF1a1a2e))
                    ) {
                        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Flag, null, tint = Color(priority.color))
                            Spacer(Modifier.width(12.dp))
                            Text(priority.displayName, color = Color.White, modifier = Modifier.weight(1f))
                            if (priority == currentPriority) Icon(Icons.Default.Check, null, tint = Color(priority.color))
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = Color(0xFF94A3B8)) } },
        containerColor = Color(0xFF1a1a2e)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FollowUpDialogV2(leadId: String, onDismiss: () -> Unit, onFollowUpAdded: (FollowUp) -> Unit) {
    var title by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf(FollowUpType.CALL) }
    var selectedDate by remember { mutableStateOf(Calendar.getInstance().apply { add(Calendar.DAY_OF_MONTH, 1) }) }
    var selectedTime by remember { mutableStateOf("10:00") }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Schedule Follow-up", color = Color.White, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(
                    value = title, onValueChange = { title = it },
                    label = { Text("Title") }, placeholder = { Text("e.g., Call back") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color(0xFF3B82F6), unfocusedBorderColor = Color(0xFF64748B), focusedTextColor = Color.White, unfocusedTextColor = Color.White)
                )
                
                Text("Type", fontSize = 12.sp, color = Color(0xFF94A3B8))
                Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FollowUpType.entries.forEach { type ->
                        FilterChip(
                            onClick = { selectedType = type }, label = { Text(type.displayName, fontSize = 12.sp) }, selected = selectedType == type,
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Color(type.color).copy(alpha = 0.3f), selectedLabelColor = Color(type.color))
                        )
                    }
                }
                
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(selectedDate.time),
                        onValueChange = {}, readOnly = true, label = { Text("Date") },
                        modifier = Modifier.weight(1f).clickable { showDatePicker = true },
                        trailingIcon = { IconButton(onClick = { showDatePicker = true }) { Icon(Icons.Default.DateRange, null, tint = Color(0xFF3B82F6)) } },
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color(0xFF3B82F6), unfocusedBorderColor = Color(0xFF64748B), focusedTextColor = Color.White, unfocusedTextColor = Color.White)
                    )
                    OutlinedTextField(
                        value = selectedTime, onValueChange = {}, readOnly = true, label = { Text("Time") },
                        modifier = Modifier.weight(1f).clickable { showTimePicker = true },
                        trailingIcon = { IconButton(onClick = { showTimePicker = true }) { Icon(Icons.Default.AccessTime, null, tint = Color(0xFF3B82F6)) } },
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color(0xFF3B82F6), unfocusedBorderColor = Color(0xFF64748B), focusedTextColor = Color.White, unfocusedTextColor = Color.White)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isNotBlank()) {
                        val calendar = selectedDate.clone() as Calendar
                        val timeParts = selectedTime.split(":")
                        calendar.set(Calendar.HOUR_OF_DAY, timeParts[0].toInt())
                        calendar.set(Calendar.MINUTE, timeParts[1].toInt())
                        onFollowUpAdded(FollowUp(id = UUID.randomUUID().toString(), leadId = leadId, title = title, scheduledDate = calendar.timeInMillis, scheduledTime = selectedTime, type = selectedType))
                    }
                },
                enabled = title.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3B82F6))
            ) { Text("Schedule") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = Color(0xFF94A3B8)) } },
        containerColor = Color(0xFF1a1a2e)
    )
    
    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = selectedDate.timeInMillis)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = { TextButton(onClick = { datePickerState.selectedDateMillis?.let { selectedDate.timeInMillis = it }; showDatePicker = false }) { Text("OK") } },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancel") } }
        ) { DatePicker(state = datePickerState) }
    }
    
    if (showTimePicker) {
        val timePickerState = rememberTimePickerState(initialHour = selectedTime.split(":")[0].toInt(), initialMinute = selectedTime.split(":")[1].toInt())
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            confirmButton = { TextButton(onClick = { selectedTime = String.format("%02d:%02d", timePickerState.hour, timePickerState.minute); showTimePicker = false }) { Text("OK") } },
            dismissButton = { TextButton(onClick = { showTimePicker = false }) { Text("Cancel") } },
            text = { TimePicker(state = timePickerState) }
        )
    }
}


// Document helper functions
fun getChatsPromoFolder(context: android.content.Context): File {
    // Try Documents directory first
    val documentsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
    var chatsPromoDir = File(documentsDir, "ChatsPromo")
    
    if (!chatsPromoDir.exists()) {
        val created = chatsPromoDir.mkdirs()
        if (!created) {
            // Fallback to app's external files directory
            chatsPromoDir = File(context.getExternalFilesDir(null), "ChatsPromo")
            if (!chatsPromoDir.exists()) {
                chatsPromoDir.mkdirs()
            }
        }
    }
    return chatsPromoDir
}

fun getLeadDocumentsFolder(context: android.content.Context, leadId: String): File {
    val chatsPromoDir = getChatsPromoFolder(context)
    val leadDir = File(chatsPromoDir, "leads/$leadId/documents")
    if (!leadDir.exists()) {
        leadDir.mkdirs()
    }
    return leadDir
}

fun getLeadDocuments(context: android.content.Context, leadId: String): List<File> {
    val folder = getLeadDocumentsFolder(context, leadId)
    return folder.listFiles()?.toList() ?: emptyList()
}

fun saveDocumentForLead(context: android.content.Context, leadId: String, uri: Uri) {
    try {
        val folder = getLeadDocumentsFolder(context, leadId)
        
        // Get proper filename from URI
        var fileName = "document_${System.currentTimeMillis()}"
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0) {
                    fileName = cursor.getString(nameIndex) ?: fileName
                }
            }
        }
        
        val destFile = File(folder, fileName)
        
        context.contentResolver.openInputStream(uri)?.use { input ->
            destFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

// Tags Selection Dialog
@Composable
fun TagsSelectionDialog(
    allTags: List<String>,
    selectedTags: List<String>,
    onDismiss: () -> Unit,
    onTagsSelected: (List<String>) -> Unit = {},
    onConfirm: (List<String>) -> Unit = onTagsSelected
) {
    var currentSelection by remember(selectedTags) { mutableStateOf(selectedTags.toList()) }
    var newTagText by remember { mutableStateOf("") }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Label, null, tint = Color(0xFF8B5CF6), modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(8.dp))
                Text("Select Tags", color = Color.White, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Add new tag input
                OutlinedTextField(
                    value = newTagText,
                    onValueChange = { newTagText = it },
                    label = { Text("Add new tag") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF8B5CF6),
                        unfocusedBorderColor = Color(0xFF64748B),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    trailingIcon = {
                        if (newTagText.isNotBlank()) {
                            IconButton(onClick = {
                                if (newTagText.isNotBlank() && !currentSelection.contains(newTagText)) {
                                    currentSelection = currentSelection + newTagText
                                    newTagText = ""
                                }
                            }) {
                                Icon(Icons.Default.Add, null, tint = Color(0xFF8B5CF6))
                            }
                        }
                    }
                )
                
                // Available tags
                if (allTags.isNotEmpty()) {
                    Text("Available Tags", fontSize = 12.sp, color = Color(0xFF94A3B8))
                    
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        allTags.chunked(2).forEach { rowTags ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                rowTags.forEach { tag ->
                                    val isSelected = currentSelection.contains(tag)
                                    Surface(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable {
                                                currentSelection = if (isSelected) {
                                                    (currentSelection - tag).toMutableList()
                                                } else {
                                                    (currentSelection + tag).toMutableList()
                                                }
                                            },
                                        color = if (isSelected) Color(0xFF8B5CF6) else Color(0xFF8B5CF6).copy(alpha = 0.2f),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.Center
                                        ) {
                                            if (isSelected) {
                                                Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(16.dp))
                                                Spacer(Modifier.width(4.dp))
                                            }
                                            Text(
                                                tag,
                                                fontSize = 12.sp,
                                                color = if (isSelected) Color.White else Color(0xFF8B5CF6),
                                                maxLines = 1
                                            )
                                        }
                                    }
                                }
                                // Fill empty space if odd number
                                if (rowTags.size == 1) {
                                    Spacer(Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
                
                // Selected tags preview
                if (currentSelection.isNotEmpty()) {
                    Text("Selected (${currentSelection.size})", fontSize = 12.sp, color = Color(0xFF94A3B8))
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        currentSelection.forEach { tag ->
                            Surface(
                                color = Color(0xFF10B981),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(tag, fontSize = 11.sp, color = Color.White)
                                    Spacer(Modifier.width(4.dp))
                                    Icon(
                                        Icons.Default.Close,
                                        null,
                                        tint = Color.White,
                                        modifier = Modifier
                                            .size(14.dp)
                                            .clickable { currentSelection = (currentSelection - tag).toMutableList() }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(currentSelection) },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8B5CF6))
            ) {
                Text("Save Tags")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Color(0xFF94A3B8))
            }
        },
        containerColor = Color(0xFF1a1a2e)
    )
}
