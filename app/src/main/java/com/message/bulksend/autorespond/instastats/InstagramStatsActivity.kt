package com.message.bulksend.autorespond.instastats

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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.message.bulksend.ui.theme.BulksendTestTheme
import com.message.bulksend.autorespond.database.MessageRepository
import kotlinx.coroutines.launch

class InstagramStatsActivity : ComponentActivity() {
    
    private lateinit var messageRepository: MessageRepository
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        messageRepository = MessageRepository(this)
        
        setContent {
            BulksendTestTheme {
                InstagramStatsScreen(
                    messageRepository = messageRepository,
                    onBackPressed = { finish() }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InstagramStatsScreen(
    messageRepository: MessageRepository,
    onBackPressed: () -> Unit
) {
    var totalMessages by remember { mutableStateOf(0) }
    var sentReplies by remember { mutableStateOf(0) }
    var failedReplies by remember { mutableStateOf(0) }
    var pendingReplies by remember { mutableStateOf(0) }
    
    val scope = rememberCoroutineScope()
    
    // Load Instagram statistics
    LaunchedEffect(Unit) {
        scope.launch {
            // Get Instagram messages count (messages with instagram_ prefix in phone number)
            totalMessages = messageRepository.getInstagramMessageCount()
            sentReplies = messageRepository.getInstagramRepliesByStatus("SENT")
            failedReplies = messageRepository.getInstagramRepliesByStatus("FAILED")
            pendingReplies = messageRepository.getInstagramRepliesByStatus("PENDING")
        }
    }
    
    val backgroundBrush = Brush.verticalGradient(
        colors = listOf(Color(0xFF0f0c29), Color(0xFF302b63), Color(0xFF24243e))
    )
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text("Instagram Analytics", color = Color(0xFFE4405F), fontWeight = FontWeight.Bold)
                },
                navigationIcon = {
                    IconButton(onClick = onBackPressed) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color(0xFFE4405F))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF1a1a2e))
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
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Overview Cards
                item {
                    Text(
                        "Instagram DM Statistics",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
                
                // Stats Cards Row 1
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        InstagramStatCard(
                            modifier = Modifier.weight(1f),
                            title = "Total Messages",
                            value = totalMessages.toString(),
                            icon = Icons.Default.Message,
                            color = Color(0xFF3B82F6)
                        )
                        
                        InstagramStatCard(
                            modifier = Modifier.weight(1f),
                            title = "Sent Replies",
                            value = sentReplies.toString(),
                            icon = Icons.Default.Send,
                            color = Color(0xFF10B981)
                        )
                    }
                }
                
                // Stats Cards Row 2
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        InstagramStatCard(
                            modifier = Modifier.weight(1f),
                            title = "Failed Replies",
                            value = failedReplies.toString(),
                            icon = Icons.Default.Error,
                            color = Color(0xFFEF4444)
                        )
                        
                        InstagramStatCard(
                            modifier = Modifier.weight(1f),
                            title = "Pending",
                            value = pendingReplies.toString(),
                            icon = Icons.Default.Schedule,
                            color = Color(0xFFF59E0B)
                        )
                    }
                }
                
                // Success Rate Card
                item {
                    val successRate = if (totalMessages > 0) {
                        ((sentReplies.toFloat() / totalMessages.toFloat()) * 100).toInt()
                    } else 0
                    
                    InstagramSuccessRateCard(
                        successRate = successRate,
                        totalMessages = totalMessages,
                        sentReplies = sentReplies
                    )
                }
                
                // Coming Soon Features
                item {
                    ComingSoonCard()
                }
            }
        }
    }
}

@Composable
fun InstagramStatCard(
    modifier: Modifier = Modifier,
    title: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1a1a2e)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(color.copy(alpha = 0.2f), RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(20.dp)
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                value,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            
            Text(
                title,
                fontSize = 12.sp,
                color = Color(0xFF94A3B8),
                maxLines = 1
            )
        }
    }
}

@Composable
fun InstagramSuccessRateCard(
    successRate: Int,
    totalMessages: Int,
    sentReplies: Int
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1a1a2e)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(
                            brush = Brush.linearGradient(
                                colors = listOf(
                                    Color(0xFFE4405F),
                                    Color(0xFFF56040)
                                )
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.TrendingUp,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
                
                Column {
                    Text(
                        "Success Rate",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        "$successRate% replies sent successfully",
                        fontSize = 13.sp,
                        color = Color(0xFF94A3B8)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Progress Bar
            LinearProgressIndicator(
                progress = successRate / 100f,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp),
                color = Color(0xFFE4405F),
                trackColor = Color(0xFF2D3748)
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                "$sentReplies of $totalMessages messages replied",
                fontSize = 12.sp,
                color = Color(0xFF64748B)
            )
        }
    }
}

@Composable
fun ComingSoonCard() {
    val context = androidx.compose.ui.platform.LocalContext.current
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1a1a2e)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Default.TableChart,
                contentDescription = null,
                tint = Color(0xFFE4405F),
                modifier = Modifier.size(48.dp)
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Text(
                "Detailed Message Logs",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                "View all Instagram DM conversations in a detailed spreadsheet format",
                fontSize = 13.sp,
                color = Color(0xFF94A3B8),
                lineHeight = 18.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Button(
                onClick = {
                    val intent = android.content.Intent(context, InstagramMessageLogsActivity::class.java)
                    context.startActivity(intent)
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFE4405F)
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Default.TableChart,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        "View Message Logs",
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Text(
                "More features coming soon:\n• Export to Excel • Filter by date • Search messages",
                fontSize = 11.sp,
                color = Color(0xFF64748B),
                lineHeight = 16.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}