package com.message.bulksend.autorespond

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.message.bulksend.ui.theme.BulksendTestTheme

class NotificationDebugActivity : ComponentActivity() {
    
    companion object {
        var lastNotificationData: NotificationDebugData? = null
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        setContent {
            BulksendTestTheme {
                NotificationDebugScreen(
                    onBackPressed = { finish() }
                )
            }
        }
    }
}

data class NotificationDebugData(
    val title: String,
    val text: String,
    val packageName: String,
    val timestamp: Long,
    val allExtras: Map<String, String>
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationDebugScreen(onBackPressed: () -> Unit) {
    var notificationData by remember { mutableStateOf(NotificationDebugActivity.lastNotificationData) }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        "Notification Debug", 
                        color = Color(0xFF00D4FF), 
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackPressed) {
                        Icon(
                            Icons.Default.ArrowBack, 
                            contentDescription = "Back", 
                            tint = Color(0xFF00D4FF)
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { 
                        notificationData = NotificationDebugActivity.lastNotificationData 
                    }) {
                        Icon(
                            Icons.Default.Refresh, 
                            contentDescription = "Refresh", 
                            tint = Color(0xFF00D4FF)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF1a1a2e)
                )
            )
        },
        containerColor = Color(0xFF0f0c29)
    ) { padding ->
        if (notificationData == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        "No notification received yet",
                        color = Color(0xFF94A3B8),
                        fontSize = 16.sp
                    )
                    Text(
                        "Send a WhatsApp message to see debug info",
                        color = Color(0xFF64748B),
                        fontSize = 14.sp
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Basic Info Card
                item {
                    DebugCard(title = "Basic Info") {
                        DebugItem("Title", notificationData!!.title)
                        DebugItem("Text", notificationData!!.text)
                        DebugItem("Package", notificationData!!.packageName)
                        DebugItem("Timestamp", java.text.SimpleDateFormat(
                            "dd/MM/yyyy HH:mm:ss", 
                            java.util.Locale.getDefault()
                        ).format(java.util.Date(notificationData!!.timestamp)))
                    }
                }
                
                // All Extras Card
                item {
                    DebugCard(title = "All Notification Extras (${notificationData!!.allExtras.size})") {
                        if (notificationData!!.allExtras.isEmpty()) {
                            Text(
                                "No extras found",
                                color = Color(0xFF64748B),
                                fontSize = 14.sp,
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                    }
                }
                
                // Individual extras
                items(notificationData!!.allExtras.toList()) { (key, value) ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFF1a1a2e)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                key,
                                color = Color(0xFF00D4FF),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                value,
                                color = Color.White,
                                fontSize = 14.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
                
                item {
                    Spacer(modifier = Modifier.height(32.dp))
                }
            }
        }
    }
}

@Composable
fun DebugCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF1a1a2e)
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                title,
                color = Color(0xFF10B981),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
            content()
        }
    }
}

@Composable
fun DebugItem(label: String, value: String) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            label,
            color = Color(0xFF64748B),
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
        Text(
            value,
            color = Color.White,
            fontSize = 14.sp,
            fontFamily = FontFamily.Monospace
        )
    }
}
