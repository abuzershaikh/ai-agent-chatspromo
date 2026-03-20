package com.message.bulksend.autorespond

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.message.bulksend.autorespond.database.MessageEntity
import com.message.bulksend.autorespond.database.MessageRepository
import com.message.bulksend.ui.theme.BulksendTestTheme

class MessageLogsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        setContent {
            BulksendTestTheme {
                MessageLogsScreen(
                    onBackPressed = { finish() }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessageLogsScreen(onBackPressed: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val messageRepository = remember { MessageRepository(context) }
    val leadManager = remember { com.message.bulksend.leadmanager.LeadManager(context) }
    
    val messages by messageRepository.getAllMessages().collectAsState(initial = emptyList())
    var showTableView by remember { mutableStateOf(true) } // Default to Excel table view
    var showAddToLeadsDialog by remember { mutableStateOf(false) }
    var selectedMessagesForLead by remember { mutableStateOf<List<MessageEntity>>(emptyList()) }
    var addLeadResult by remember { mutableStateOf<String?>(null) }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text("Auto Reply Report", color = Color(0xFF00D4FF), fontWeight = FontWeight.Bold)
                },
                navigationIcon = {
                    IconButton(onClick = onBackPressed) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color(0xFF00D4FF))
                    }
                },
                actions = {
                    // Add All to Leads Button
                    IconButton(
                        onClick = { 
                            selectedMessagesForLead = messages
                            showAddToLeadsDialog = true 
                        }
                    ) {
                        Icon(
                            Icons.Default.PersonAdd,
                            contentDescription = "Add to Leads",
                            tint = Color(0xFF10B981)
                        )
                    }
                    IconButton(onClick = { showTableView = !showTableView }) {
                        Icon(
                            if (showTableView) Icons.Default.ViewList else Icons.Default.TableChart,
                            contentDescription = "Toggle View",
                            tint = Color(0xFF00D4FF)
                        )
                    }
                    Text(
                        "Total: ${messages.size}",
                        color = Color(0xFF00D4FF),
                        modifier = Modifier.padding(end = 16.dp),
                        fontWeight = FontWeight.Bold
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF1a1a2e))
            )
        },
        containerColor = Color(0xFF0f0c29)
    ) { padding ->
        if (messages.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = null,
                        tint = Color(0xFF64748B),
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "No messages yet",
                        color = Color(0xFF64748B),
                        fontSize = 16.sp
                    )
                }
            }
        } else {
            if (showTableView) {
                MessageTableView(
                    messages = messages, 
                    modifier = Modifier.padding(padding),
                    onAddSelectedToLeads = { selectedMessages ->
                        selectedMessagesForLead = selectedMessages
                        showAddToLeadsDialog = true
                    }
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(messages) { message ->
                        MessageLogCard(message)
                    }
                }
            }
        }
    }
    
    // Add to Leads Dialog
    if (showAddToLeadsDialog) {
        AddToLeadsDialog(
            messages = selectedMessagesForLead,
            onDismiss = { 
                showAddToLeadsDialog = false
                selectedMessagesForLead = emptyList()
            },
            onConfirm = { addAll ->
                // Get unique contacts by phone number
                val uniqueMessages = selectedMessagesForLead.distinctBy { it.phoneNumber }
                var addedCount = 0
                var errorCount = 0
                
                // Get existing leads phone numbers - extract last 10 digits for comparison
                val existingLeads = leadManager.getAllLeads()
                val existingPhones = existingLeads.map { 
                    it.phoneNumber.replace(Regex("[^0-9]"), "").takeLast(10)
                }.toSet()
                var hitLeadLimit = false
                uniqueMessages.forEach { message ->
                    try {
                        val normalizedPhone = message.phoneNumber.replace(Regex("[^0-9]"), "").takeLast(10)
                        
                        // Check if phone already exists (compare last 10 digits)
                        val alreadyExists = normalizedPhone.length >= 10 && existingPhones.contains(normalizedPhone)
                        
                        if (!alreadyExists && message.phoneNumber.isNotBlank()) {
                            val newLead = com.message.bulksend.leadmanager.model.Lead(
                                id = java.util.UUID.randomUUID().toString(),
                                name = message.senderName.ifBlank { "Unknown" },
                                phoneNumber = message.phoneNumber,
                                status = com.message.bulksend.leadmanager.model.LeadStatus.NEW,
                                source = "WhatsApp",
                                lastMessage = message.incomingMessage,
                                timestamp = message.timestamp,
                                category = "AutoRespond",
                                notes = "Added from Auto Reply Report",
                                priority = com.message.bulksend.leadmanager.model.LeadPriority.MEDIUM
                            )
                            val added = leadManager.addLead(newLead)
                            if (added) {
                                addedCount++
                            } else {
                                hitLeadLimit = true
                            }
                        }
                    } catch (e: Exception) {
                        errorCount++
                    }
                }
                
                val skippedCount = uniqueMessages.size - addedCount - errorCount
                addLeadResult = if (hitLeadLimit) {
                    "Lead limit reached (5 on free plan). Added: $addedCount, Skipped: ${skippedCount + errorCount}\nUpgrade to Chatspromo Premium to add more."
                } else {
                    "Added: $addedCount leads" + (if (skippedCount > 0) ", Skipped: $skippedCount (already exists)" else "")
                }
                showAddToLeadsDialog = false
                selectedMessagesForLead = emptyList()
            }
        )
    }
    
    // Result Snackbar
    if (addLeadResult != null) {
        LaunchedEffect(addLeadResult) {
            kotlinx.coroutines.delay(3000)
            addLeadResult = null
        }
        
        Snackbar(
            modifier = Modifier.padding(16.dp),
            containerColor = Color(0xFF10B981)
        ) {
            Text(addLeadResult ?: "", color = Color.White)
        }
    }
}

@Composable
fun MessageLogCard(message: MessageEntity) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1a1a2e)),
        elevation = CardDefaults.cardElevation(2.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Header with Sr No and Status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Sr. ${message.srNo}",
                    color = Color(0xFF00D4FF),
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                
                StatusChip(message.status)
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Sender Info
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Person,
                    contentDescription = null,
                    tint = Color(0xFF00D4FF),
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        message.senderName,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Text(
                        message.phoneNumber,
                        color = Color(0xFF94A3B8),
                        fontSize = 12.sp
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Incoming Message
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF202C33), RoundedCornerShape(8.dp))
                    .padding(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.ArrowDownward,
                        contentDescription = null,
                        tint = Color(0xFF94A3B8),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        "Incoming",
                        color = Color(0xFF94A3B8),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    message.incomingMessage,
                    color = Color.White,
                    fontSize = 14.sp
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Outgoing Message
            if (message.outgoingMessage.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF005C4B), RoundedCornerShape(8.dp))
                        .padding(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.ArrowUpward,
                            contentDescription = null,
                            tint = Color(0xFF94A3B8),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            "Outgoing",
                            color = Color(0xFF94A3B8),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        message.outgoingMessage,
                        color = Color.White,
                        fontSize = 14.sp
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Timestamp
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Schedule,
                    contentDescription = null,
                    tint = Color(0xFF64748B),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    message.dateTime,
                    color = Color(0xFF64748B),
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
fun MessageTableView(
    messages: List<MessageEntity>, 
    modifier: Modifier = Modifier,
    onAddSelectedToLeads: (List<MessageEntity>) -> Unit = {}
) {
    var selectedMessages by remember { mutableStateOf(setOf<Int>()) }
    var highlightedRowId by remember { mutableStateOf<Int?>(null) } // Currently highlighted row for reading
    var sortColumn by remember { mutableStateOf("srNo") }
    var sortAscending by remember { mutableStateOf(true) }
    val horizontalScrollState = rememberScrollState()
    
    val sortedMessages = remember(messages, sortColumn, sortAscending) {
        when (sortColumn) {
            "srNo" -> if (sortAscending) messages.sortedBy { it.srNo } else messages.sortedByDescending { it.srNo }
            "sender" -> if (sortAscending) messages.sortedBy { it.senderName } else messages.sortedByDescending { it.senderName }
            "phone" -> if (sortAscending) messages.sortedBy { it.phoneNumber } else messages.sortedByDescending { it.phoneNumber }
            "status" -> if (sortAscending) messages.sortedBy { it.status } else messages.sortedByDescending { it.status }
            "date" -> if (sortAscending) messages.sortedBy { it.timestamp } else messages.sortedByDescending { it.timestamp }
            else -> messages
        }
    }
    
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Table Controls
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Message Logs (${messages.size})",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            
            if (selectedMessages.isNotEmpty()) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFF00D4FF).copy(alpha = 0.2f)
                    ) {
                        Text(
                            "${selectedMessages.size} selected",
                            fontSize = 12.sp,
                            color = Color(0xFF00D4FF),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                    
                    // Add Selected to Leads Button
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFF10B981),
                        modifier = Modifier.clickable {
                            val selectedMessagesList = messages.filter { selectedMessages.contains(it.id) }
                            onAddSelectedToLeads(selectedMessagesList)
                        }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.PersonAdd,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                "Add to Leads",
                                fontSize = 12.sp,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
        
        // Whole Table with Synchronized Horizontal Scroll
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .horizontalScroll(horizontalScrollState)
        ) {
            // Fixed Table Header
            Card(
                modifier = Modifier.wrapContentWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1a1a2e)),
                shape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp, bottomStart = 0.dp, bottomEnd = 0.dp),
                elevation = CardDefaults.cardElevation(4.dp)
            ) {
                Row(
                    modifier = Modifier
                        .background(Color(0xFF2a2a3e))
                        .border(1.dp, Color(0xFF64748B).copy(alpha = 0.3f))
                        .padding(vertical = 14.dp, horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Checkbox column
                    Box(
                        modifier = Modifier.width(50.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Checkbox(
                            checked = selectedMessages.size == messages.size && messages.isNotEmpty(),
                            onCheckedChange = { checked ->
                                selectedMessages = if (checked) {
                                    messages.map { it.id }.toSet()
                                } else {
                                    emptySet()
                                }
                            },
                            colors = CheckboxDefaults.colors(
                                checkedColor = Color(0xFF00D4FF),
                                uncheckedColor = Color(0xFF64748B)
                            )
                        )
                    }
                    
                    SortableHeaderCell("Sr No", 70.dp, "srNo", sortColumn, sortAscending) { 
                        sortColumn = "srNo"
                        sortAscending = if (sortColumn == "srNo") !sortAscending else true
                    }
                    SortableHeaderCell("Sender", 130.dp, "sender", sortColumn, sortAscending) { 
                        sortColumn = "sender"
                        sortAscending = if (sortColumn == "sender") !sortAscending else true
                    }
                    SortableHeaderCell("Phone", 120.dp, "phone", sortColumn, sortAscending) { 
                        sortColumn = "phone"
                        sortAscending = if (sortColumn == "phone") !sortAscending else true
                    }
                    TableHeaderCell("Incoming", 200.dp)
                    TableHeaderCell("Outgoing", 200.dp)
                    SortableHeaderCell("Status", 110.dp, "status", sortColumn, sortAscending) { 
                        sortColumn = "status"
                        sortAscending = if (sortColumn == "status") !sortAscending else true
                    }
                    SortableHeaderCell("Date & Time", 140.dp, "date", sortColumn, sortAscending) { 
                        sortColumn = "date"
                        sortAscending = if (sortColumn == "date") !sortAscending else true
                    }
                }
            }
            
            // Scrollable Table Body (Vertical Only)
            LazyColumn(
                modifier = Modifier.wrapContentWidth()
            ) {
                items(sortedMessages.size) { index ->
                    val message = sortedMessages[index]
                    val isLastItem = index == sortedMessages.size - 1
                    val isSelected = selectedMessages.contains(message.id)
                    val isHighlighted = highlightedRowId == message.id // Check if this row is highlighted for reading
                    
                    Card(
                        modifier = Modifier
                            .wrapContentWidth()
                            .clickable { 
                                // Toggle highlight on click - if same row clicked again, remove highlight
                                highlightedRowId = if (highlightedRowId == message.id) null else message.id
                            },
                        colors = CardDefaults.cardColors(
                            containerColor = when {
                                isHighlighted -> Color(0xFF00D4FF).copy(alpha = 0.25f) // Highlighted for reading
                                isSelected -> Color(0xFF00D4FF).copy(alpha = 0.15f)
                                index % 2 == 0 -> Color(0xFF1a1a2e)
                                else -> Color(0xFF16162a)
                            }
                        ),
                        shape = if (isLastItem) {
                            RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp)
                        } else {
                            RoundedCornerShape(0.dp)
                        }
                    ) {
                        Row(
                            modifier = Modifier
                                .border(
                                    width = if (isHighlighted) 2.dp else 0.5.dp,
                                    color = if (isHighlighted) Color(0xFF00D4FF) else Color(0xFF64748B).copy(alpha = 0.2f)
                                )
                                .padding(vertical = 12.dp, horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Checkbox
                            Box(
                                modifier = Modifier.width(50.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Checkbox(
                                    checked = isSelected,
                                    onCheckedChange = { checked ->
                                        selectedMessages = if (checked) {
                                            selectedMessages + message.id
                                        } else {
                                            selectedMessages - message.id
                                        }
                                    },
                                    colors = CheckboxDefaults.colors(
                                        checkedColor = Color(0xFF00D4FF),
                                        uncheckedColor = Color(0xFF64748B)
                                    )
                                )
                            }
                            
                            TableDataCell(
                                "#${message.srNo}", 
                                70.dp, 
                                fontWeight = FontWeight.Bold, 
                                color = Color(0xFF00D4FF)
                            )
                            TableDataCell(message.senderName, 130.dp)
                            TableDataCell(message.phoneNumber, 120.dp, fontSize = 11.sp)
                            TableDataCell(message.incomingMessage, 200.dp)
                            TableDataCell(message.outgoingMessage, 200.dp)
                            Box(
                                modifier = Modifier
                                    .width(110.dp)
                                    .padding(horizontal = 4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                StatusChip(message.status)
                            }
                            TableDataCell(message.dateTime, 140.dp, fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RowScope.TableHeaderCell(text: String, width: androidx.compose.ui.unit.Dp) {
    Box(
        modifier = Modifier
            .width(width)
            .padding(horizontal = 4.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(
            text = text,
            color = Color(0xFF94A3B8),
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun RowScope.SortableHeaderCell(
    text: String, 
    width: androidx.compose.ui.unit.Dp,
    columnKey: String,
    currentSortColumn: String,
    sortAscending: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .width(width)
            .clickable { onClick() }
            .padding(horizontal = 4.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = text,
                color = Color(0xFF94A3B8),
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (currentSortColumn == columnKey) {
                Icon(
                    imageVector = if (sortAscending) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
                    contentDescription = "Sort",
                    tint = Color(0xFF00D4FF),
                    modifier = Modifier.size(14.dp)
                )
            } else {
                Icon(
                    imageVector = Icons.Default.UnfoldMore,
                    contentDescription = "Sort",
                    tint = Color(0xFF64748B),
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}

@Composable
fun RowScope.TableDataCell(
    text: String, 
    width: androidx.compose.ui.unit.Dp,
    fontWeight: FontWeight = FontWeight.Normal,
    fontSize: androidx.compose.ui.unit.TextUnit = 12.sp,
    color: Color = Color.White
) {
    Box(
        modifier = Modifier
            .width(width)
            .padding(horizontal = 4.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(
            text = text,
            color = color,
            fontWeight = fontWeight,
            fontSize = fontSize,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun StatusChip(status: String) {
    val (backgroundColor, textColor, text) = when (status) {
        "SENT" -> Triple(Color(0xFF10B981), Color.White, "Sent")
        "PENDING" -> Triple(Color(0xFFF59E0B), Color(0xFF1a1a2e), "Pending")
        "FAILED" -> Triple(Color(0xFFEF4444), Color.White, "Failed")
        "NO_MATCH" -> Triple(Color(0xFF6B7280), Color.White, "No Match")
        else -> Triple(Color(0xFF64748B), Color.White, status)
    }
    
    Surface(
        color = backgroundColor,
        shape = RoundedCornerShape(6.dp)
    ) {
        Text(
            text,
            color = textColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
        )
    }
}


@Composable
fun AddToLeadsDialog(
    messages: List<MessageEntity>,
    onDismiss: () -> Unit,
    onConfirm: (addAll: Boolean) -> Unit
) {
    val uniqueContacts = messages.distinctBy { it.phoneNumber }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1a1a2e),
        shape = RoundedCornerShape(20.dp),
        icon = {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .background(Color(0xFF10B981).copy(alpha = 0.2f), RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.PersonAdd,
                    contentDescription = null,
                    tint = Color(0xFF10B981),
                    modifier = Modifier.size(32.dp)
                )
            }
        },
        title = {
            Text(
                "Add to Lead Manager",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "Add contacts from Auto Reply messages to Lead Manager",
                    fontSize = 14.sp,
                    color = Color(0xFF94A3B8)
                )
                
                // Stats
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF2a2a3e)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Total Messages", fontSize = 13.sp, color = Color(0xFF94A3B8))
                            Text("${messages.size}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Unique Contacts", fontSize = 13.sp, color = Color(0xFF94A3B8))
                            Text("${uniqueContacts.size}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF10B981))
                        }
                    }
                }
                
                Text(
                    "• Duplicate phone numbers will be skipped\n• Source will be set to 'WhatsApp'\n• Category will be 'AutoRespond'",
                    fontSize = 12.sp,
                    color = Color(0xFF64748B),
                    lineHeight = 18.sp
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(false) },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Add ${uniqueContacts.size} Leads", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Color(0xFF94A3B8))
            }
        }
    )
}
