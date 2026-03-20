package com.message.bulksend.autorespond

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import com.airbnb.lottie.compose.*
import com.message.bulksend.ui.theme.BulksendTestTheme
import java.text.SimpleDateFormat
import java.util.*
import androidx.activity.compose.BackHandler
import kotlinx.coroutines.launch

class AutoRespondActivity : ComponentActivity() {
    
    private lateinit var autoRespondManager: AutoRespondManager
    private val notifications = mutableStateListOf<NotificationData>()
    private var permissionState = mutableStateOf(false)
    private var selectedTabState = mutableStateOf(1) // Default to Menu (1)
    
    private val notificationReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                WhatsAppNotificationListener.ACTION_NOTIFICATION_RECEIVED -> {
                    val senderName = intent.getStringExtra(WhatsAppNotificationListener.EXTRA_SENDER_NAME) ?: ""
                    val messageText = intent.getStringExtra(WhatsAppNotificationListener.EXTRA_MESSAGE_TEXT) ?: ""
                    val packageName = intent.getStringExtra(WhatsAppNotificationListener.EXTRA_PACKAGE_NAME) ?: ""
                    val timestamp = intent.getLongExtra(WhatsAppNotificationListener.EXTRA_TIMESTAMP, System.currentTimeMillis())
                    
                    val notificationData = NotificationData(senderName, messageText, packageName, timestamp)
                    notifications.add(0, notificationData)
                }
                WhatsAppNotificationListener.ACTION_INSTAGRAM_NOTIFICATION_RECEIVED -> {
                    val senderName = intent.getStringExtra(WhatsAppNotificationListener.EXTRA_SENDER_NAME) ?: ""
                    val messageText = intent.getStringExtra(WhatsAppNotificationListener.EXTRA_MESSAGE_TEXT) ?: ""
                    val packageName = intent.getStringExtra(WhatsAppNotificationListener.EXTRA_PACKAGE_NAME) ?: ""
                    val timestamp = intent.getLongExtra(WhatsAppNotificationListener.EXTRA_TIMESTAMP, System.currentTimeMillis())
                    
                    val notificationData = NotificationData(senderName, messageText, packageName, timestamp)
                    notifications.add(0, notificationData)
                }
            }
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        autoRespondManager = AutoRespondManager(this)
        permissionState.value = autoRespondManager.isNotificationPermissionGranted()
        
        // Check if we should navigate to Home tab (from settings redirect)
        handleIntent(intent)
        
        setContent {
            BulksendTestTheme {
                AutoRespondScreen(
                    autoRespondManager = autoRespondManager,
                    notifications = notifications,
                    permissionState = permissionState,
                    onBackPressed = { finish() },
                    selectedTabState = selectedTabState
                )
            }
        }
    }
    
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }
    
    private fun handleIntent(intent: Intent?) {
        val navigateToHome = intent?.getBooleanExtra("navigate_to_autorespond_home", false) ?: false
        if (navigateToHome) {
            selectedTabState.value = 0 // Home tab
        }
    }
    
    override fun onResume() {
        super.onResume()
        // Update permission state when returning from settings
        permissionState.value = autoRespondManager.isNotificationPermissionGranted()
        
        // Register receivers for both WhatsApp and Instagram notifications from unified service
        val whatsappFilter = IntentFilter(WhatsAppNotificationListener.ACTION_NOTIFICATION_RECEIVED)
        val instagramFilter = IntentFilter(WhatsAppNotificationListener.ACTION_INSTAGRAM_NOTIFICATION_RECEIVED)
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(notificationReceiver, whatsappFilter, RECEIVER_NOT_EXPORTED)
            registerReceiver(notificationReceiver, instagramFilter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(notificationReceiver, whatsappFilter)
            registerReceiver(notificationReceiver, instagramFilter)
        }
        
        // Perform auto backup if needed (daily background backup)
        performAutoBackupIfNeeded()
    }
    
    override fun onPause() {
        super.onPause()
        unregisterReceiver(notificationReceiver)
    }
    
    /**
     * Perform auto backup if enabled and 24 hours have passed
     */
    private fun performAutoBackupIfNeeded() {
        val backupManager = com.message.bulksend.autorespond.backup.AutoRespondBackupManager(this)
        
        if (backupManager.isAutoBackupNeeded()) {
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                val result = backupManager.performAutoBackupIfNeeded()
                result?.let {
                    if (it.success) {
                        android.util.Log.d("AutoRespondActivity", "Auto backup completed: ${it.itemsBackedUp} items")
                    } else {
                        android.util.Log.e("AutoRespondActivity", "Auto backup failed: ${it.error}")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutoRespondScreen(
    autoRespondManager: AutoRespondManager,
    notifications: List<NotificationData>,
    permissionState: MutableState<Boolean>,
    onBackPressed: () -> Unit,
    selectedTabState: MutableState<Int> = mutableStateOf(1) // Default to Menu (index 1), 0 = Home
) {
    var selectedTab by selectedTabState
    var isEnabled by remember { mutableStateOf(autoRespondManager.isAutoRespondEnabled()) }
    var responseMessage by remember { mutableStateOf(autoRespondManager.getResponseMessage()) }
    val hasPermission by permissionState
    
    val backgroundBrush = Brush.verticalGradient(
        colors = listOf(Color(0xFF0f0c29), Color(0xFF302b63), Color(0xFF24243e))
    )
    
    // Handle back press - go to Menu tab (default) first, then exit
    // Menu tab is index 1, so if not on Menu, go to Menu first
    BackHandler(enabled = selectedTab != 1) {
        selectedTab = 1 // Go to Menu tab (default tab)
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text("Auto Respond", color = Color(0xFF00D4FF), fontWeight = FontWeight.Bold)
                },
                navigationIcon = {
                    IconButton(onClick = onBackPressed) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color(0xFF00D4FF))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF1a1a2e))
            )
        },
        bottomBar = {
            AutoRespondBottomBar(
                selectedTab = selectedTab,
                onTabSelected = { selectedTab = it }
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
            when (selectedTab) {
                0 -> HomeTabContent(
                    autoRespondManager = autoRespondManager,
                    notifications = notifications,
                    permissionState = permissionState,
                    isEnabled = isEnabled,
                    responseMessage = responseMessage,
                    hasPermission = hasPermission,
                    onToggle = { enabled ->
                        isEnabled = enabled
                        autoRespondManager.setAutoRespondEnabled(enabled)
                    },
                    onMessageChange = { message ->
                        responseMessage = message
                        autoRespondManager.saveResponseMessage(message)
                    }
                )
                1 -> MenuTabContent()
                2 -> StatisticsTabContent(notifications = notifications)
            }
        }
    }
}

@Composable
fun AutoRespondBottomBar(selectedTab: Int, onTabSelected: (Int) -> Unit) {
    NavigationBar(
        containerColor = Color(0xFF1a1a2e),
        contentColor = Color(0xFF00D4FF)
    ) {
        NavigationBarItem(
            icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
            label = { Text("Home") },
            selected = selectedTab == 0,
            onClick = { onTabSelected(0) },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = Color(0xFF00D4FF),
                selectedTextColor = Color(0xFF00D4FF),
                unselectedIconColor = Color(0xFF64748B),
                unselectedTextColor = Color(0xFF64748B),
                indicatorColor = Color(0xFF1a1a2e)
            )
        )
        NavigationBarItem(
            icon = { Icon(Icons.Default.Menu, contentDescription = "Menu") },
            label = { Text("Menu") },
            selected = selectedTab == 1,
            onClick = { onTabSelected(1) },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = Color(0xFF00D4FF),
                selectedTextColor = Color(0xFF00D4FF),
                unselectedIconColor = Color(0xFF64748B),
                unselectedTextColor = Color(0xFF64748B),
                indicatorColor = Color(0xFF1a1a2e)
            )
        )
        NavigationBarItem(
            icon = { 
                val composition by rememberLottieComposition(LottieCompositionSpec.Asset("Statistics.json"))
                val progress by animateLottieCompositionAsState(
                    composition = composition, 
                    iterations = LottieConstants.IterateForever
                )
                if (composition != null) {
                    LottieAnimation(
                        composition = composition, 
                        progress = { progress }, 
                        modifier = Modifier.size(24.dp)
                    )
                } else {
                    // Fallback icon if Lottie fails to load
                    Icon(Icons.Default.BarChart, contentDescription = "Statistics")
                }
            },
            label = { Text("Statistics") },
            selected = selectedTab == 2,
            onClick = { onTabSelected(2) },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = Color(0xFF00D4FF),
                selectedTextColor = Color(0xFF00D4FF),
                unselectedIconColor = Color(0xFF64748B),
                unselectedTextColor = Color(0xFF64748B),
                indicatorColor = Color(0xFF1a1a2e)
            )
        )
    }
}

@Composable
fun HomeTabContent(
    autoRespondManager: AutoRespondManager,
    notifications: List<NotificationData>,
    permissionState: MutableState<Boolean>,
    isEnabled: Boolean,
    responseMessage: String,
    hasPermission: Boolean,
    onToggle: (Boolean) -> Unit,
    onMessageChange: (String) -> Unit
) {
    // State for permission explanation dialog
    var showPermissionExplanationDialog by remember { mutableStateOf(false) }
    
    // Permission Explanation Dialog
    if (showPermissionExplanationDialog) {
        NotificationPermissionExplanationDialog(
            onDismiss = { showPermissionExplanationDialog = false },
            onProceed = {
                showPermissionExplanationDialog = false
                autoRespondManager.openNotificationSettings()
            }
        )
    }
    
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Permission Card
        if (!hasPermission) {
            item {
                PermissionCard(
                    onGrantPermission = {
                        // Show explanation dialog first
                        showPermissionExplanationDialog = true
                    },
                    onCheckPermission = {
                        permissionState.value = autoRespondManager.isNotificationPermissionGranted()
                    }
                )
            }
        }
        
        // Enable/Disable Switch
        item {
            EnableCard(
                isEnabled = isEnabled,
                hasPermission = hasPermission,
                onToggle = onToggle
            )
        }
        
        // Keyword Replies List
        item {
            KeywordRepliesSection()
        }
    }
}



@Composable
fun MenuItemCard(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, description: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1a1a2e))
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = Color(0xFF00D4FF),
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Text(description, fontSize = 14.sp, color = Color(0xFF94A3B8))
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
fun StatCard(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, value: String, color: Color) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1a1a2e))
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(40.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontSize = 14.sp, color = Color(0xFF94A3B8))
                Spacer(modifier = Modifier.height(4.dp))
                Text(value, fontSize = 32.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
        }
    }
}

@Composable
fun PermissionCard(onGrantPermission: () -> Unit, onCheckPermission: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1a1a2e))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFFFB020), modifier = Modifier.size(32.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Text("Permission Required", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                "Enable notification access to read WhatsApp messages",
                fontSize = 14.sp,
                color = Color(0xFF94A3B8)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onGrantPermission,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00D4FF)),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Grant Permission")
                }
                OutlinedButton(
                    onClick = onCheckPermission,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF00D4FF))
                ) {
                    Text("Check")
                }
            }
        }
    }
}

@Composable
fun EnableCard(isEnabled: Boolean, hasPermission: Boolean, onToggle: (Boolean) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1a1a2e))
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Auto Respond", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Text(
                    if (isEnabled) "Active" else "Inactive",
                    fontSize = 14.sp,
                    color = if (isEnabled) Color(0xFF4ADE80) else Color(0xFF94A3B8)
                )
            }
            Switch(
                checked = isEnabled,
                onCheckedChange = { if (hasPermission) onToggle(it) },
                enabled = hasPermission,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = Color(0xFF4ADE80)
                )
            )
        }
    }
}

@Composable
fun ResponseMessageCard(responseMessage: String, onMessageChange: (String) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1a1a2e))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Response Message", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = responseMessage,
                onValueChange = onMessageChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Enter your auto-reply message...") },
                minLines = 3,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF00D4FF),
                    unfocusedBorderColor = Color(0xFF64748B),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                )
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Tip: Use {name} for sender name, {message} for their message",
                fontSize = 12.sp,
                color = Color(0xFF94A3B8)
            )
        }
    }
}

@Composable
fun NotificationCard(notification: NotificationData) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1a1a2e))
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
            Icon(
                if (notification.isWhatsAppBusiness) Icons.Default.Business else Icons.Default.Chat,
                contentDescription = null,
                tint = if (notification.isWhatsAppBusiness) Color(0xFF25D366) else Color(0xFF00D4FF),
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(notification.senderName, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Spacer(modifier = Modifier.height(4.dp))
                Text(notification.messageText, fontSize = 14.sp, color = Color(0xFF94A3B8))
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(notification.timestamp)),
                    fontSize = 12.sp,
                    color = Color(0xFF64748B)
                )
            }
        }
    }
}

@Composable
fun EmptyNotificationsCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1a1a2e))
    ) {
        Column(
            modifier = Modifier.padding(32.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(Icons.Default.Notifications, contentDescription = null, tint = Color(0xFF64748B), modifier = Modifier.size(48.dp))
            Spacer(modifier = Modifier.height(16.dp))
            Text("No notifications yet", fontSize = 16.sp, color = Color(0xFF94A3B8))
            Text("WhatsApp messages will appear here", fontSize = 14.sp, color = Color(0xFF64748B))
        }
    }
}

@Composable
fun KeywordRepliesSection() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val keywordReplyManager = remember { com.message.bulksend.autorespond.keywordreply.KeywordReplyManager(context) }
    var keywordReplies by remember { mutableStateOf(keywordReplyManager.getAllReplies()) }
    
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            "Keyword Replies",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier.padding(vertical = 8.dp)
        )
        
        if (keywordReplies.isEmpty()) {
            EmptyKeywordRepliesCard()
        } else {
            keywordReplies.forEach { reply ->
                KeywordReplyCard(
                    reply = reply,
                    onEdit = {
                        val intent = Intent(
                            context,
                            com.message.bulksend.autorespond.keywordreply.KeywordReplyActivity::class.java
                        ).apply {
                            putExtra(
                                com.message.bulksend.autorespond.keywordreply.KeywordReplyActivity.EXTRA_EDIT_REPLY_ID,
                                reply.id
                            )
                        }
                        context.startActivity(intent)
                    },
                    onToggle = {
                        keywordReplyManager.toggleReplyEnabled(reply.id)
                        keywordReplies = keywordReplyManager.getAllReplies()
                    },
                    onDelete = {
                        keywordReplyManager.deleteReply(reply.id)
                        keywordReplies = keywordReplyManager.getAllReplies()
                    }
                )
            }
        }
    }
}

@Composable
fun EmptyKeywordRepliesCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1a1a2e))
    ) {
        Column(
            modifier = Modifier.padding(32.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(Icons.Default.Key, contentDescription = null, tint = Color(0xFF64748B), modifier = Modifier.size(48.dp))
            Spacer(modifier = Modifier.height(16.dp))
            Text("No keyword replies yet", fontSize = 16.sp, color = Color(0xFF94A3B8))
            Text("Create keyword replies from Menu tab", fontSize = 14.sp, color = Color(0xFF64748B))
        }
    }
}

@Composable
fun KeywordReplyCard(
    reply: com.message.bulksend.autorespond.keywordreply.KeywordReplyData,
    onEdit: () -> Unit,
    onToggle: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1a1a2e))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        reply.incomingKeyword,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF00D4FF)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        reply.replyMessage,
                        fontSize = 14.sp,
                        color = Color(0xFF94A3B8),
                        maxLines = 2
                    )
                }
                Switch(
                    checked = reply.isEnabled,
                    onCheckedChange = { onToggle() },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = Color(0xFF4ADE80)
                    )
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (reply.replyOption.isNotEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFF2a2a3e)
                        ) {
                            Text(
                                reply.replyOption.uppercase(),
                                fontSize = 10.sp,
                                color = Color(0xFF00D4FF),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFF2a2a3e)
                    ) {
                        Text(
                            reply.matchOption.uppercase(),
                            fontSize = 10.sp,
                            color = Color(0xFF94A3B8),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(
                        onClick = onEdit,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                    ) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = "Edit",
                            tint = Color(0xFF00D4FF),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Edit",
                            color = Color(0xFF00D4FF),
                            fontSize = 12.sp
                        )
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Delete",
                            tint = Color(0xFFEF4444),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}


@Composable
fun AutoReplyReportCardCompact(onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1a1a2e))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            Color(0xFF00D4FF).copy(alpha = 0.1f),
                            Color(0xFF1a1a2e)
                        )
                    )
                )
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .background(
                        Color(0xFF00D4FF).copy(alpha = 0.2f),
                        RoundedCornerShape(12.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.TableChart,
                    contentDescription = null,
                    tint = Color(0xFF00D4FF),
                    modifier = Modifier.size(28.dp)
                )
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            // Content
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "View Detailed Report",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Message logs & analytics",
                    fontSize = 13.sp,
                    color = Color(0xFF94A3B8)
                )
            }
            
            // Button
            Button(
                onClick = onClick,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF00D4FF)
                ),
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(
                    "Open",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

/**
 * Dialog explaining why notification permission is needed
 */
@Composable
fun NotificationPermissionExplanationDialog(
    onDismiss: () -> Unit,
    onProceed: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1a1a2e),
        shape = RoundedCornerShape(20.dp),
        icon = {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .background(
                        Color(0xFF00D4FF).copy(alpha = 0.2f),
                        RoundedCornerShape(18.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Notifications,
                    contentDescription = null,
                    tint = Color(0xFF00D4FF),
                    modifier = Modifier.size(40.dp)
                )
            }
        },
        title = {
            Text(
                "Notification Access Required",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    "To enable Auto Reply feature, we need access to read your WhatsApp notifications.",
                    fontSize = 14.sp,
                    color = Color(0xFF94A3B8),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                
                // Why we need this section
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF2a2a3e)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            "Why we need this permission:",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF00D4FF)
                        )
                        
                        PermissionReasonItem(
                            icon = Icons.Default.Message,
                            text = "Read incoming WhatsApp messages"
                        )
                        
                        PermissionReasonItem(
                            icon = Icons.Default.Reply,
                            text = "Send automatic replies based on your settings"
                        )
                        
                        PermissionReasonItem(
                            icon = Icons.Default.Key,
                            text = "Match keywords to trigger specific responses"
                        )
                    }
                }
                
                // Privacy note
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Security,
                        contentDescription = null,
                        tint = Color(0xFF4ADE80),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "Your data stays on your device",
                        fontSize = 12.sp,
                        color = Color(0xFF4ADE80)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onProceed,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00D4FF)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    Icons.Default.Settings,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Open Settings", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Cancel", color = Color(0xFF94A3B8))
            }
        }
    )
}

@Composable
fun PermissionReasonItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .background(Color(0xFF00D4FF).copy(alpha = 0.2f), RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = Color(0xFF00D4FF),
                modifier = Modifier.size(18.dp)
            )
        }
        Text(
            text,
            fontSize = 13.sp,
            color = Color.White
        )
    }
}
