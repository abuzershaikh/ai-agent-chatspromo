package com.message.bulksend.autorespond.welcomemessage

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.message.bulksend.autorespond.documentreply.DocumentFile
import com.message.bulksend.autorespond.documentreply.DocumentPickerDialog
import com.message.bulksend.autorespond.documentreply.DocumentReplyActivity
import com.message.bulksend.autorespond.documentreply.DocumentReplyManager
import com.message.bulksend.ui.theme.BulksendTestTheme
import kotlinx.coroutines.launch

class WelcomeMessageActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BulksendTestTheme {
                WelcomeMessageScreen { finish() }
            }
        }
    }
}

// Colors
private object WelcomeColors {
    val DarkBackground = Color(0xFF0A0A0B)
    val CardBackground = Color(0xFF1A1A2E)
    val AccentPrimary = Color(0xFF6C63FF)
    val AccentGreen = Color(0xFF10B981)
    val TextPrimary = Color(0xFFFFFFFF)
    val TextSecondary = Color(0xFFD1D5DB)
    val TextTertiary = Color(0xFF9CA3AF)
    val DividerColor = Color(0xFF374151)
    val DeleteRed = Color(0xFFEF4444)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WelcomeMessageScreen(onBackPressed: () -> Unit) {
    val context = LocalContext.current
    val manager = remember { WelcomeMessageManager(context) }
    val documentManager = remember { DocumentReplyManager(context) }
    val scope = rememberCoroutineScope()
    
    // State
    var isEnabled by remember { mutableStateOf(manager.isEnabled()) }
    var sendMultiple by remember { mutableStateOf(manager.isSendMultiple()) }
    var messages by remember { mutableStateOf<List<WelcomeMessage>>(emptyList()) }
    var showAddDialog by remember { mutableStateOf(false) }
    var editingMessage by remember { mutableStateOf<WelcomeMessage?>(null) }
    var totalSent by remember { mutableIntStateOf(0) }
    
    // Load data
    LaunchedEffect(Unit) {
        messages = manager.getAllMessages()
        totalSent = manager.getTotalSentCount()
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Welcome Message",
                        color = WelcomeColors.TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackPressed) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            "Back",
                            tint = WelcomeColors.TextPrimary
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            context.startActivity(Intent(context, DocumentReplyActivity::class.java))
                        }
                    ) {
                        Icon(
                            Icons.Default.AttachFile,
                            "Document Library",
                            tint = WelcomeColors.TextPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = WelcomeColors.CardBackground
                )
            )
        },
        floatingActionButton = {
            if (isEnabled) {
                FloatingActionButton(
                    onClick = { showAddDialog = true },
                    containerColor = WelcomeColors.AccentPrimary
                ) {
                    Icon(Icons.Default.Add, "Add Message", tint = Color.White)
                }
            }
        },
        containerColor = WelcomeColors.DarkBackground
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            WelcomeColors.DarkBackground,
                            Color(0xFF0F0F12)
                        )
                    )
                )
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Enable Switch Card
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = WelcomeColors.CardBackground)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    "Enable Welcome Message",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = WelcomeColors.TextPrimary
                                )
                                Text(
                                    "Send welcome to first-time contacts",
                                    fontSize = 14.sp,
                                    color = WelcomeColors.TextTertiary
                                )
                            }
                            Switch(
                                checked = isEnabled,
                                onCheckedChange = {
                                    isEnabled = it
                                    manager.setEnabled(it)
                                },
                                colors = SwitchDefaults.colors(
                                    checkedTrackColor = WelcomeColors.AccentGreen,
                                    checkedThumbColor = Color.White
                                )
                            )
                        }
                    }
                }
                
                if (isEnabled) {
                    // Stats Card
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = WelcomeColors.CardBackground)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(20.dp),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                StatItem("Messages", messages.size.toString())
                                StatItem("Sent", totalSent.toString())
                            }
                        }
                    }
                    
                    // Send Mode Card
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = WelcomeColors.CardBackground)
                        ) {
                            Column(modifier = Modifier.padding(20.dp)) {
                                Text(
                                    "Send Mode",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = WelcomeColors.TextPrimary
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    ModeButton(
                                        text = "Single",
                                        selected = !sendMultiple,
                                        onClick = {
                                            sendMultiple = false
                                            manager.setSendMultiple(false)
                                        },
                                        modifier = Modifier.weight(1f)
                                    )
                                    ModeButton(
                                        text = "Multiple",
                                        selected = sendMultiple,
                                        onClick = {
                                            sendMultiple = true
                                            manager.setSendMultiple(true)
                                        },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                
                                Text(
                                    if (sendMultiple) "All messages will be sent in order"
                                    else "Only first message will be sent",
                                    fontSize = 12.sp,
                                    color = WelcomeColors.TextTertiary,
                                    modifier = Modifier.padding(top = 8.dp)
                                )
                            }
                        }
                    }
                    
                    // Messages Header
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Welcome Messages",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = WelcomeColors.TextPrimary
                            )
                            if (messages.isNotEmpty()) {
                                TextButton(onClick = {
                                    scope.launch {
                                        manager.resetAllWelcomeStatuses()
                                        totalSent = 0
                                    }
                                }) {
                                    Text("Reset Sent", color = WelcomeColors.AccentPrimary)
                                }
                            }
                        }
                    }
                    
                    // Messages List
                    if (messages.isEmpty()) {
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = WelcomeColors.CardBackground)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(40.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(
                                        Icons.Default.Email,
                                        contentDescription = null,
                                        tint = WelcomeColors.TextTertiary,
                                        modifier = Modifier.size(48.dp)
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text(
                                        "No welcome messages",
                                        color = WelcomeColors.TextSecondary,
                                        fontSize = 16.sp
                                    )
                                    Text(
                                        "Tap + to add your first message",
                                        color = WelcomeColors.TextTertiary,
                                        fontSize = 14.sp
                                    )
                                }
                            }
                        }
                    } else {
                        itemsIndexed(messages) { index, message ->
                            MessageCard(
                                message = message,
                                index = index + 1,
                                onEdit = { editingMessage = message },
                                onDelete = {
                                    scope.launch {
                                        manager.deleteMessage(message.id)
                                        messages = manager.getAllMessages()
                                    }
                                }
                            )
                        }
                    }
                }
                
                item { Spacer(modifier = Modifier.height(80.dp)) }
            }
        }
    }
    
    // Add/Edit Dialog
    if (showAddDialog || editingMessage != null) {
        AddEditMessageDialog(
            message = editingMessage,
            documentManager = documentManager,
            onDismiss = {
                showAddDialog = false
                editingMessage = null
            },
            onSave = { text, delay, selectedDocuments ->
                scope.launch {
                    if (editingMessage != null) {
                        manager.updateMessage(
                            editingMessage!!
                                .copy(message = text, delayMs = delay)
                                .withSelectedDocuments(selectedDocuments)
                        )
                    } else {
                        manager.addMessage(text, delay, selectedDocuments)
                    }
                    messages = manager.getAllMessages()
                    showAddDialog = false
                    editingMessage = null
                }
            }
        )
    }
}

@Composable
private fun StatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            value,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = WelcomeColors.AccentPrimary
        )
        Text(
            label,
            fontSize = 14.sp,
            color = WelcomeColors.TextTertiary
        )
    }
}

@Composable
private fun ModeButton(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (selected) WelcomeColors.AccentPrimary else WelcomeColors.DividerColor
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Text(text, color = Color.White)
    }
}

@Composable
private fun MessageCard(
    message: WelcomeMessage,
    index: Int,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val selectedDocuments = remember(message.selectedDocumentsJson) { message.getSelectedDocuments() }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = WelcomeColors.CardBackground)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            // Index badge
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .background(WelcomeColors.AccentPrimary, RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "$index",
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            // Message content
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    message.message.ifBlank { "Documents only welcome message" },
                    color = WelcomeColors.TextPrimary,
                    fontSize = 14.sp,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
                if (message.delayMs > 0) {
                    Text(
                        "Delay: ${message.delayMs / 1000}s",
                        color = WelcomeColors.TextTertiary,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                if (selectedDocuments.isNotEmpty()) {
                    Text(
                        "Documents: ${selectedDocuments.size}",
                        color = WelcomeColors.AccentGreen,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                    Text(
                        selectedDocuments.joinToString(", ") { it.originalName },
                        color = WelcomeColors.TextTertiary,
                        fontSize = 12.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            
            // Actions
            IconButton(onClick = onEdit) {
                Icon(Icons.Default.Edit, "Edit", tint = WelcomeColors.TextTertiary)
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, "Delete", tint = WelcomeColors.DeleteRed)
            }
        }
    }
}

@Composable
private fun AddEditMessageDialog(
    message: WelcomeMessage?,
    documentManager: DocumentReplyManager,
    onDismiss: () -> Unit,
    onSave: (String, Long, List<DocumentFile>) -> Unit
) {
    var text by remember { mutableStateOf(message?.message ?: "") }
    var delaySeconds by remember { mutableStateOf((message?.delayMs ?: 0) / 1000) }
    var selectedDocuments by remember { mutableStateOf(message?.getSelectedDocuments() ?: emptyList()) }
    var showDocumentPicker by remember { mutableStateOf(false) }
    val availableDocuments = remember(showDocumentPicker, message?.selectedDocumentsJson) {
        documentManager.getAllDocumentFiles()
    }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (message == null) "Add Welcome Message" else "Edit Message",
                color = WelcomeColors.TextPrimary
            )
        },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Message") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    maxLines = 5,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = WelcomeColors.AccentPrimary,
                        unfocusedBorderColor = WelcomeColors.DividerColor,
                        focusedTextColor = WelcomeColors.TextPrimary,
                        unfocusedTextColor = WelcomeColors.TextSecondary
                    )
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                OutlinedTextField(
                    value = if (delaySeconds > 0) delaySeconds.toString() else "",
                    onValueChange = { delaySeconds = it.toLongOrNull() ?: 0 },
                    label = { Text("Delay (seconds)") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = WelcomeColors.AccentPrimary,
                        unfocusedBorderColor = WelcomeColors.DividerColor,
                        focusedTextColor = WelcomeColors.TextPrimary,
                        unfocusedTextColor = WelcomeColors.TextSecondary
                    )
                )

                Spacer(modifier = Modifier.height(18.dp))

                Text(
                    "Documents (optional)",
                    color = WelcomeColors.TextPrimary,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Selected documents will be sent after this welcome text.",
                    color = WelcomeColors.TextTertiary,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 4.dp, bottom = 10.dp)
                )

                OutlinedButton(
                    onClick = { showDocumentPicker = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = WelcomeColors.AccentPrimary)
                ) {
                    Icon(Icons.Default.AttachFile, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Select from Document Reply")
                }

                if (availableDocuments.isEmpty()) {
                    Text(
                        "No saved documents found. Add files in Document Reply, then come back here to attach them.",
                        color = WelcomeColors.TextTertiary,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                } else if (selectedDocuments.isEmpty()) {
                    Text(
                        "No documents selected yet.",
                        color = WelcomeColors.TextTertiary,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        selectedDocuments.forEach { document ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp),
                                colors = CardDefaults.cardColors(containerColor = WelcomeColors.DividerColor)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            document.originalName,
                                            color = WelcomeColors.TextPrimary,
                                            fontSize = 13.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            documentManager.formatFileSize(document.fileSize),
                                            color = WelcomeColors.TextTertiary,
                                            fontSize = 11.sp
                                        )
                                    }
                                    IconButton(
                                        onClick = {
                                            selectedDocuments =
                                                selectedDocuments.filter { it.id != document.id }
                                        }
                                    ) {
                                        Icon(
                                            Icons.Default.Close,
                                            contentDescription = "Remove document",
                                            tint = WelcomeColors.DeleteRed
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        text.trim(),
                        delaySeconds * 1000,
                        selectedDocuments.distinctBy { it.id }
                    )
                },
                enabled = text.isNotBlank() || selectedDocuments.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(containerColor = WelcomeColors.AccentPrimary)
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = WelcomeColors.TextTertiary)
            }
        },
        containerColor = WelcomeColors.CardBackground
    )

    if (showDocumentPicker) {
        DocumentPickerDialog(
            documents = availableDocuments,
            selectedDocuments = selectedDocuments,
            onDismiss = { showDocumentPicker = false },
            onSelectionChanged = { selectedDocuments = it.distinctBy { doc -> doc.id } }
        )
    }
}
