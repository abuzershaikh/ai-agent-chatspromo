package com.message.bulksend.autorespond.keywordreply

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import com.message.bulksend.ui.theme.BulksendTestTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class KeywordReplyActivity : ComponentActivity() {
    companion object {
        const val EXTRA_EDIT_REPLY_ID = "extra_edit_reply_id"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val initialEditReplyId = intent?.getStringExtra(EXTRA_EDIT_REPLY_ID)
        
        setContent {
            BulksendTestTheme {
                KeywordReplyScreen(
                    onBackPressed = { finish() },
                    onSave = { finish() },
                    initialEditReplyId = initialEditReplyId
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KeywordReplyScreen(
    onBackPressed: () -> Unit,
    onSave: () -> Unit,
    initialEditReplyId: String? = null
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val keywordReplyManager = remember { KeywordReplyManager(context) }
    val autoRespondManager = remember { com.message.bulksend.autorespond.AutoRespondManager(context) }
    
    var incomingKeyword by remember { mutableStateOf("") }
    var replyMessage by remember { mutableStateOf("") }
    var selectedMatchOption by remember { mutableStateOf("exact") }
    var selectedReplyOption by remember { mutableStateOf("") } // "", "menu", "chatgpt", "gemini"
    var minWordMatch by remember { mutableStateOf(1f) } // Slider value for minimum word match
    var saveButtonText by remember { mutableStateOf("Save") }
    var editingReplyId by remember { mutableStateOf<String?>(null) }
    var editingCreatedAt by remember { mutableStateOf<Long?>(null) }
    var editingIsEnabled by remember { mutableStateOf(true) }
    var showEnableDialog by remember { mutableStateOf(false) }
    var consumedInitialEdit by remember(initialEditReplyId) {
        mutableStateOf(initialEditReplyId.isNullOrBlank())
    }
    val coroutineScope = rememberCoroutineScope()
    
    // Calculate max words in keyword for slider
    val maxWords = remember(incomingKeyword) {
        if (incomingKeyword.isBlank()) 1
        else incomingKeyword.trim().split("\\s+".toRegex()).size.coerceAtLeast(1)
    }

    val startEditing: (KeywordReplyData) -> Unit = { reply ->
        val wordsCount = reply.incomingKeyword.trim()
            .split("\\s+".toRegex())
            .size
            .coerceAtLeast(1)

        incomingKeyword = reply.incomingKeyword
        replyMessage = reply.replyMessage
        selectedMatchOption = reply.matchOption
        selectedReplyOption = reply.replyOption
        minWordMatch = reply.minWordMatch.coerceIn(1, wordsCount).toFloat()
        editingReplyId = reply.id
        editingCreatedAt = reply.createdAt
        editingIsEnabled = reply.isEnabled
        saveButtonText = "Update"
    }

    LaunchedEffect(initialEditReplyId, consumedInitialEdit) {
        if (!consumedInitialEdit && !initialEditReplyId.isNullOrBlank()) {
            keywordReplyManager.getAllReplies().firstOrNull { it.id == initialEditReplyId }?.let { reply ->
                startEditing(reply)
            }
            consumedInitialEdit = true
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text("Keyword Reply", color = Color(0xFF00D4FF), fontWeight = FontWeight.Bold)
                },
                navigationIcon = {
                    IconButton(onClick = onBackPressed) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color(0xFF00D4FF))
                    }
                },
                actions = {
                    TextButton(onClick = {
                        val trimmedKeyword = incomingKeyword.trim()
                        val trimmedReply = replyMessage.trim()
                        if (trimmedKeyword.isNotBlank() && (trimmedReply.isNotBlank() || selectedReplyOption == "menu")) {
                            // Check notification permission and auto respond enabled
                            val hasPermission = autoRespondManager.isNotificationPermissionGranted()
                            val isAutoRespondEnabled = autoRespondManager.isAutoRespondEnabled()
                            val isEditing = editingReplyId != null

                            val keywordWordCount = trimmedKeyword.split("\\s+".toRegex()).size.coerceAtLeast(1)
                            val resolvedMinWordMatch = if (selectedMatchOption == "contains") {
                                minWordMatch.toInt().coerceIn(1, keywordWordCount)
                            } else {
                                1
                            }

                            val reply = KeywordReplyData(
                                id = editingReplyId ?: System.currentTimeMillis().toString(),
                                incomingKeyword = trimmedKeyword,
                                replyMessage = trimmedReply,
                                replyOption = selectedReplyOption,
                                matchOption = selectedMatchOption,
                                minWordMatch = resolvedMinWordMatch,
                                sendEmail = false,
                                isEnabled = editingIsEnabled,
                                createdAt = editingCreatedAt ?: System.currentTimeMillis()
                            )

                            keywordReplyManager.saveKeywordReply(reply)

                            // Reset editor after save/update
                            incomingKeyword = ""
                            replyMessage = ""
                            selectedMatchOption = "exact"
                            selectedReplyOption = ""
                            minWordMatch = 1f
                            editingReplyId = null
                            editingCreatedAt = null
                            editingIsEnabled = true
                            
                            if (!hasPermission || !isAutoRespondEnabled) {
                                showEnableDialog = true
                                saveButtonText = if (isEditing) "Updated" else "Saved"
                            } else {
                                saveButtonText = if (isEditing) "Updated" else "Saved"
                                val message = if (isEditing) "Keyword updated successfully!" else "Saved successfully!"
                                android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_SHORT).show()
                            }

                            coroutineScope.launch {
                                delay(2000)
                                saveButtonText = "Save"
                            }
                        } else {
                            android.widget.Toast.makeText(context, "Please fill all fields", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }) {
                        Text(saveButtonText, color = Color(0xFF00D4FF), fontWeight = FontWeight.Bold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF1a1a2e))
            )
        },
        containerColor = Color(0xFF0f0c29)
    ) { padding ->
        // Enable Auto Respond Dialog
        if (showEnableDialog) {
            EnableAutoRespondDialog(
                autoRespondManager = autoRespondManager,
                onDismiss = { showEnableDialog = false },
                onGoToEnable = {
                    showEnableDialog = false
                    // Navigate to AutoRespondActivity Home tab
                    val intent = android.content.Intent(context, com.message.bulksend.autorespond.AutoRespondActivity::class.java)
                    intent.putExtra("navigate_to_autorespond_home", true)
                    intent.flags = android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP
                    context.startActivity(intent)
                }
            )
        }
        
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Preview Section
            item {
                PreviewSection(
                    incomingKeyword = incomingKeyword.ifEmpty { "what is price" },
                    replyMessage = if (selectedReplyOption == "menu") "📋 Menu Reply" else replyMessage.ifEmpty { "Type your reply message" },
                    isMenuReply = selectedReplyOption == "menu"
                )
            }
            
            // Incoming Keyword
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1a1a2e)),
                    elevation = CardDefaults.cardElevation(2.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "Incoming Keyword",
                            fontSize = 14.sp,
                            color = Color(0xFF00D4FF),
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = incomingKeyword,
                            onValueChange = { incomingKeyword = it },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("Incoming message keyword", color = Color(0xFF64748B)) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFF00D4FF),
                                unfocusedBorderColor = Color(0xFF64748B),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            )
                        )
                        Text(
                            "Example: Hi, how are you",
                            fontSize = 12.sp,
                            color = Color(0xFF94A3B8),
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }
            
            // Reply Message
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (selectedReplyOption == "menu") Color(0xFF1a1a2e).copy(alpha = 0.5f) else Color(0xFF1a1a2e)
                    ),
                    elevation = CardDefaults.cardElevation(2.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "Reply message",
                            fontSize = 14.sp,
                            color = if (selectedReplyOption == "menu") Color(0xFF64748B) else Color.White,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = replyMessage,
                            onValueChange = { replyMessage = it },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 3,
                            enabled = selectedReplyOption != "menu", // Disable when menu is selected
                            placeholder = { 
                                Text(
                                    if (selectedReplyOption == "menu") "Menu Reply will be used" else "Type your reply message",
                                    color = Color(0xFF64748B)
                                )
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFF00D4FF),
                                unfocusedBorderColor = Color(0xFF64748B),
                                disabledBorderColor = Color(0xFF64748B).copy(alpha = 0.5f),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                disabledTextColor = Color(0xFF64748B)
                            )
                        )
                        Text(
                            if (selectedReplyOption == "menu") 
                                "Reply message disabled - Menu Reply will be triggered" 
                            else if (selectedMatchOption == "contains")
                                "Example: Hi I am good. (Will be sent when ${minWordMatch.toInt()} word${if (minWordMatch.toInt() > 1) "s" else ""} match)"
                            else
                                "Example: Hi I am good.",
                            fontSize = 12.sp,
                            color = Color(0xFF94A3B8),
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }
            
            // Match Options
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1a1a2e)),
                    elevation = CardDefaults.cardElevation(2.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "Match options",
                                fontSize = 14.sp,
                                color = Color(0xFF00D4FF),
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(
                                Icons.Default.Info,
                                contentDescription = "Info",
                                tint = Color(0xFF64748B),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(24.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(
                                    selected = selectedMatchOption == "exact",
                                    onClick = { selectedMatchOption = "exact" },
                                    colors = RadioButtonDefaults.colors(
                                        selectedColor = Color(0xFF00D4FF),
                                        unselectedColor = Color(0xFF64748B)
                                    )
                                )
                                Text("Exact Match", fontSize = 14.sp, color = Color.White)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(
                                    selected = selectedMatchOption == "contains",
                                    onClick = { selectedMatchOption = "contains" },
                                    colors = RadioButtonDefaults.colors(
                                        selectedColor = Color(0xFF00D4FF),
                                        unselectedColor = Color(0xFF64748B)
                                    )
                                )
                                Text("Contains", fontSize = 14.sp, color = Color.White)
                            }
                        }
                        
                        // Slider for minimum word match (only visible when Contains is selected)
                        if (selectedMatchOption == "contains") {
                            Spacer(modifier = Modifier.height(16.dp))
                            HorizontalDivider(thickness = 1.dp, color = Color(0xFF2D3748))
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            Text(
                                "Minimum words to match: ${minWordMatch.toInt()}",
                                fontSize = 13.sp,
                                color = Color(0xFF00D4FF),
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            Slider(
                                value = minWordMatch,
                                onValueChange = { minWordMatch = it },
                                valueRange = 1f..maxWords.toFloat(),
                                steps = if (maxWords > 2) maxWords - 2 else 0,
                                colors = SliderDefaults.colors(
                                    thumbColor = Color(0xFF00D4FF),
                                    activeTrackColor = Color(0xFF00D4FF),
                                    inactiveTrackColor = Color(0xFF64748B)
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                            
                            Text(
                                "Out of $maxWords word${if (maxWords > 1) "s" else ""} in keyword",
                                fontSize = 12.sp,
                                color = Color(0xFF94A3B8)
                            )
                        }
                    }
                }
            }
            
            // Reply Options Section
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1a1a2e)),
                    elevation = CardDefaults.cardElevation(2.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "Reply options",
                                fontSize = 14.sp,
                                color = Color(0xFF00D4FF),
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(
                                Icons.Default.Info,
                                contentDescription = "Info",
                                tint = Color(0xFF64748B),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        // Menu Reply Checkbox
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = selectedReplyOption == "menu",
                                onCheckedChange = { checked ->
                                    selectedReplyOption = if (checked) "menu" else ""
                                },
                                colors = CheckboxDefaults.colors(
                                    checkedColor = Color(0xFF00D4FF),
                                    uncheckedColor = Color(0xFF64748B),
                                    checkmarkColor = Color.White
                                )
                            )
                            Text("Menu Reply", fontSize = 14.sp, color = Color.White)
                        }
                        
                        // Info text when Menu Reply is selected
                        if (selectedReplyOption == "menu") {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "When keyword matches, Menu Reply will be triggered instead of reply message.",
                                fontSize = 12.sp,
                                color = Color(0xFF94A3B8)
                            )
                        }
                    }
                }
            }
            
            // Bottom spacing
            item {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
fun PreviewSection(incomingKeyword: String, replyMessage: String, isMenuReply: Boolean = false) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF0D1418))
            .padding(16.dp)
    ) {
        // Incoming keyword (left side - gray bubble like WhatsApp)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Start
        ) {
            Box(
                modifier = Modifier
                    .widthIn(max = 250.dp)
                    .background(
                        Color(0xFF202C33),
                        RoundedCornerShape(
                            topStart = 0.dp,
                            topEnd = 8.dp,
                            bottomStart = 8.dp,
                            bottomEnd = 8.dp
                        )
                    )
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Text(
                    incomingKeyword.ifEmpty { "Incoming message" },
                    fontSize = 14.sp,
                    color = Color.White
                )
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Reply message (right side - green bubble like WhatsApp)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            Box(
                modifier = Modifier
                    .widthIn(max = 250.dp)
                    .background(
                        if (isMenuReply) Color(0xFF00A693) else Color(0xFF005C4B),
                        RoundedCornerShape(
                            topStart = 8.dp,
                            topEnd = 8.dp,
                            bottomStart = 8.dp,
                            bottomEnd = 0.dp
                        )
                    )
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Text(
                    replyMessage.ifEmpty { "Type your reply message" },
                    fontSize = 14.sp,
                    color = Color.White
                )
            }
        }
    }
}

/**
 * Dialog to prompt user to enable Auto Respond
 */
@Composable
fun EnableAutoRespondDialog(
    autoRespondManager: com.message.bulksend.autorespond.AutoRespondManager,
    onDismiss: () -> Unit,
    onGoToEnable: () -> Unit
) {
    val hasPermission = autoRespondManager.isNotificationPermissionGranted()
    val isAutoRespondEnabled = autoRespondManager.isAutoRespondEnabled()
    
    val message = when {
        !hasPermission && !isAutoRespondEnabled -> "Notification access permission is required and Auto Respond needs to be enabled for keyword replies to work."
        !hasPermission -> "Notification access permission is required for keyword replies to work."
        !isAutoRespondEnabled -> "Auto Respond needs to be enabled for keyword replies to work."
        else -> ""
    }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1a1a2e),
        shape = RoundedCornerShape(20.dp),
        icon = {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .background(
                        Color(0xFFFFB020).copy(alpha = 0.2f),
                        RoundedCornerShape(16.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Warning,
                    contentDescription = null,
                    tint = Color(0xFFFFB020),
                    modifier = Modifier.size(36.dp)
                )
            }
        },
        title = {
            Text(
                "Enable Auto Respond",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        },
        text = {
            Text(
                message,
                fontSize = 14.sp,
                color = Color(0xFF94A3B8),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        },
        confirmButton = {
            Button(
                onClick = onGoToEnable,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00D4FF)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Go to Enable", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Later", color = Color(0xFF64748B))
            }
        }
    )
}
