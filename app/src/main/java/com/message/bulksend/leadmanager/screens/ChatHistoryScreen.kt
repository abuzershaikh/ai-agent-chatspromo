package com.message.bulksend.leadmanager.screens

import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.message.bulksend.leadmanager.LeadManager
import com.message.bulksend.leadmanager.database.entities.ChatMessageEntity
import com.message.bulksend.leadmanager.model.Lead
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatHistoryScreen(
    lead: Lead,
    leadManager: LeadManager,
    onBack: () -> Unit
) {
    val messages = remember { leadManager.getChatMessagesForLead(lead.id) }
    
    LaunchedEffect(lead.id) {
        leadManager.markMessagesAsRead(lead.id)
    }
    
    val backgroundBrush = Brush.verticalGradient(
        colors = listOf(Color(0xFF0f0c29), Color(0xFF302b63), Color(0xFF24243e))
    )
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(lead.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text(lead.phoneNumber, color = Color(0xFF94A3B8), fontSize = 12.sp)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color(0xFF10B981))
                    }
                },
                actions = {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFF25D366).copy(alpha = 0.2f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Chat,
                                contentDescription = null,
                                tint = Color(0xFF25D366),
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                "${messages.size} messages",
                                fontSize = 12.sp,
                                color = Color(0xFF25D366)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF1a1a2e))
            )
        },
        containerColor = Color.Transparent
    ) { padding ->
        if (messages.isEmpty()) {
            // Empty State
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(backgroundBrush)
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(32.dp)
                ) {
                    Icon(
                        Icons.Default.ChatBubbleOutline,
                        contentDescription = null,
                        tint = Color(0xFF64748B),
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "No chat history",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF94A3B8)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Messages from AutoRespond will appear here",
                        fontSize = 14.sp,
                        color = Color(0xFF64748B)
                    )
                }
            }
        } else {
            // Chat Messages List
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .background(backgroundBrush)
                    .padding(padding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 16.dp),
                reverseLayout = true // Show newest at bottom
            ) {
                items(messages.reversed()) { message ->
                    ChatMessageBubble(message)
                }
            }
        }
    }
}

@Composable
fun ChatMessageBubble(message: ChatMessageEntity) {
    val isIncoming = message.isIncoming
    val dateFormat = SimpleDateFormat("MMM dd, hh:mm a", Locale.getDefault())
    
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isIncoming) Alignment.Start else Alignment.End
    ) {
        // Message Bubble
        Card(
            modifier = Modifier.widthIn(max = 300.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isIncoming) Color(0xFF1a1a2e) else Color(0xFF10B981).copy(alpha = 0.3f)
            ),
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (isIncoming) 4.dp else 16.dp,
                bottomEnd = if (isIncoming) 16.dp else 4.dp
            )
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                // Auto-reply indicator
                if (message.isAutoReply) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(bottom = 4.dp)
                    ) {
                        Icon(
                            Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = Color(0xFFF59E0B),
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            "Auto Reply",
                            fontSize = 10.sp,
                            color = Color(0xFFF59E0B)
                        )
                        if (!message.matchedKeyword.isNullOrBlank()) {
                            Text(
                                " • ${message.matchedKeyword}",
                                fontSize = 10.sp,
                                color = Color(0xFF64748B)
                            )
                        }
                    }
                }
                
                // Message Text
                Text(
                    message.messageText,
                    fontSize = 14.sp,
                    color = Color.White,
                    lineHeight = 20.sp
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                // Timestamp and status
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        dateFormat.format(Date(message.timestamp)),
                        fontSize = 10.sp,
                        color = Color(0xFF64748B)
                    )
                    if (!isIncoming) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            Icons.Default.DoneAll,
                            contentDescription = null,
                            tint = Color(0xFF10B981),
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }
        
        // Reply type badge
        if (message.replyType != "manual" && !isIncoming) {
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = when (message.replyType) {
                    "keyword" -> Color(0xFF3B82F6)
                    "ai" -> Color(0xFF8B5CF6)
                    "spreadsheet" -> Color(0xFF10B981)
                    else -> Color(0xFF64748B)
                }.copy(alpha = 0.2f),
                modifier = Modifier.padding(top = 4.dp)
            ) {
                Text(
                    message.replyType.uppercase(),
                    fontSize = 9.sp,
                    color = when (message.replyType) {
                        "keyword" -> Color(0xFF3B82F6)
                        "ai" -> Color(0xFF8B5CF6)
                        "spreadsheet" -> Color(0xFF10B981)
                        else -> Color(0xFF64748B)
                    },
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
    }
}
