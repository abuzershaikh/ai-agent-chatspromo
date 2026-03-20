package com.message.bulksend.autorespond.instareply

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
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
import com.message.bulksend.autorespond.NotificationData
import com.message.bulksend.autorespond.AutoRespondManager
import java.text.SimpleDateFormat
import java.util.*

class InstagramAutoReplyActivity : ComponentActivity() {
    
    private lateinit var autoRespondManager: AutoRespondManager
    private val notifications = mutableStateListOf<NotificationData>()
    private var permissionState = mutableStateOf(false)
    
    private val notificationReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == com.message.bulksend.autorespond.WhatsAppNotificationListener.ACTION_INSTAGRAM_NOTIFICATION_RECEIVED) {
                val senderName = intent.getStringExtra(com.message.bulksend.autorespond.WhatsAppNotificationListener.EXTRA_SENDER_NAME) ?: ""
                val messageText = intent.getStringExtra(com.message.bulksend.autorespond.WhatsAppNotificationListener.EXTRA_MESSAGE_TEXT) ?: ""
                val packageName = intent.getStringExtra(com.message.bulksend.autorespond.WhatsAppNotificationListener.EXTRA_PACKAGE_NAME) ?: ""
                val timestamp = intent.getLongExtra(com.message.bulksend.autorespond.WhatsAppNotificationListener.EXTRA_TIMESTAMP, System.currentTimeMillis())
                
                val notificationData = NotificationData(senderName, messageText, packageName, timestamp)
                notifications.add(0, notificationData)
            }
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        autoRespondManager = AutoRespondManager(this)
        permissionState.value = autoRespondManager.isNotificationPermissionGranted()
        
        setContent {
            BulksendTestTheme {
                InstagramAutoReplyScreen(
                    autoRespondManager = autoRespondManager,
                    notifications = notifications,
                    permissionState = permissionState,
                    onBackPressed = { finish() }
                )
            }
        }
    }
    
    override fun onResume() {
        super.onResume()
        // Update permission state when returning from settings
        permissionState.value = autoRespondManager.isNotificationPermissionGranted()
        
        val filter = IntentFilter(com.message.bulksend.autorespond.WhatsAppNotificationListener.ACTION_INSTAGRAM_NOTIFICATION_RECEIVED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(notificationReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(notificationReceiver, filter)
        }
    }
    
    override fun onPause() {
        super.onPause()
        unregisterReceiver(notificationReceiver)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InstagramAutoReplyScreen(
    autoRespondManager: AutoRespondManager,
    notifications: List<NotificationData>,
    permissionState: MutableState<Boolean>,
    onBackPressed: () -> Unit
) {
    val hasPermission by permissionState
    val context = androidx.compose.ui.platform.LocalContext.current
    
    val backgroundBrush = Brush.verticalGradient(
        colors = listOf(Color(0xFF0f0c29), Color(0xFF302b63), Color(0xFF24243e))
    )
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text("Instagram Auto Reply", color = Color(0xFFE4405F), fontWeight = FontWeight.Bold)
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
                // Permission Card
                item {
                    PermissionCard(
                        hasPermission = hasPermission,
                        onRequestPermission = { autoRespondManager.openNotificationSettings() }
                    )
                }
                
                // Info Card - Redirect to Settings
                item {
                    InfoCard(
                        onOpenSettings = {
                            val intent = Intent(context, com.message.bulksend.autorespond.settings.AutoReplySettingsActivity::class.java)
                            context.startActivity(intent)
                        }
                    )
                }
                
                // Smart Auto Lead Capture Card
                item {
                    SmartLeadCaptureCard(
                        onOpenLeadCapture = {
                            val intent = Intent(context, com.message.bulksend.autorespond.smartlead.SmartLeadCaptureActivity::class.java)
                            context.startActivity(intent)
                        }
                    )
                }
                
                // Recent Instagram Notifications Card
                item {
                    RecentNotificationsCard(notifications = notifications)
                }
            }
        }
    }
}

@Composable
fun PermissionCard(
    hasPermission: Boolean,
    onRequestPermission: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (hasPermission) Color(0xFF1a1a2e) else Color(0xFF2d1b1b)
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    if (hasPermission) Icons.Default.CheckCircle else Icons.Default.Warning,
                    contentDescription = null,
                    tint = if (hasPermission) Color(0xFF10B981) else Color(0xFFEF4444),
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    "Notification Access",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            
            Text(
                if (hasPermission) 
                    "✓ Permission granted. Instagram notifications will be monitored."
                else 
                    "⚠️ Permission required to read Instagram notifications.",
                color = if (hasPermission) Color(0xFF94A3B8) else Color(0xFFEF4444),
                fontSize = 14.sp
            )
            
            if (!hasPermission) {
                Button(
                    onClick = onRequestPermission,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE4405F)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Grant Permission", color = Color.White)
                }
            }
        }
    }
}

@Composable
fun InfoCard(
    onOpenSettings: () -> Unit
) {
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
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    Icons.Default.Info,
                    contentDescription = null,
                    tint = Color(0xFFE4405F),
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    "Instagram Auto Reply Settings",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            
            Text(
                "Instagram auto-reply uses the same unified system as WhatsApp. Configure your reply methods (Keywords, AI, Spreadsheet) and enable Instagram in the main Auto Reply Settings.",
                color = Color(0xFF94A3B8),
                fontSize = 14.sp,
                lineHeight = 20.sp
            )
            
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "• Enable Instagram in Auto Reply Settings",
                    color = Color(0xFF94A3B8),
                    fontSize = 13.sp
                )
                Text(
                    "• Configure Keywords, AI, or Spreadsheet replies",
                    color = Color(0xFF94A3B8),
                    fontSize = 13.sp
                )
                Text(
                    "• Same reply priority system as WhatsApp",
                    color = Color(0xFF94A3B8),
                    fontSize = 13.sp
                )
            }
            
            Button(
                onClick = onOpenSettings,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE4405F)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Default.Settings,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                    Text("Open Auto Reply Settings", color = Color.White)
                }
            }
        }
    }
}

@Composable
fun SmartLeadCaptureCard(
    onOpenLeadCapture: () -> Unit
) {
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
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    Icons.Default.Person,
                    contentDescription = null,
                    tint = Color(0xFFE4405F),
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    "Smart Auto Lead Capture",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            
            Text(
                "Automatically capture lead information from new Instagram users through sequential questions. Ask for name, mobile number, and custom fields before starting regular auto-reply.",
                color = Color(0xFF94A3B8),
                fontSize = 14.sp,
                lineHeight = 20.sp
            )
            
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "• Detects new Instagram users automatically",
                    color = Color(0xFF94A3B8),
                    fontSize = 13.sp
                )
                Text(
                    "• Sequential question system (name → mobile → custom)",
                    color = Color(0xFF94A3B8),
                    fontSize = 13.sp
                )
                Text(
                    "• Saves lead data before regular auto-reply",
                    color = Color(0xFF94A3B8),
                    fontSize = 13.sp
                )
                Text(
                    "• Configurable custom fields for business needs",
                    color = Color(0xFF94A3B8),
                    fontSize = 13.sp
                )
            }
            
            Button(
                onClick = onOpenLeadCapture,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE4405F)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Default.Person,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                    Text("Configure Lead Capture", color = Color.White)
                }
            }
        }
    }
}

@Composable
fun RecentNotificationsCard(notifications: List<NotificationData>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1a1a2e)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "Recent Instagram Messages (${notifications.size})",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            
            if (notifications.isEmpty()) {
                Text(
                    "No Instagram messages received yet",
                    color = Color(0xFF64748B),
                    fontSize = 14.sp,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            } else {
                notifications.take(5).forEach { notification ->
                    NotificationItem(notification = notification)
                }
            }
        }
    }
}

@Composable
fun NotificationItem(notification: NotificationData) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0f0c29)),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    notification.senderName,
                    color = Color(0xFFE4405F),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(notification.timestamp)),
                    color = Color(0xFF64748B),
                    fontSize = 12.sp
                )
            }
            Text(
                notification.messageText,
                color = Color.White,
                fontSize = 13.sp,
                maxLines = 2
            )
        }
    }
}