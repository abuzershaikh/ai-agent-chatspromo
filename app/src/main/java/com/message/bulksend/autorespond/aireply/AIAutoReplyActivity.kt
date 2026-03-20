package com.message.bulksend.autorespond.aireply

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.content.Intent
import android.widget.Toast
import com.message.bulksend.ui.theme.BulksendTestTheme
import kotlinx.coroutines.launch

class AIAutoReplyActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        setContent {
            BulksendTestTheme {
                AIAutoReplyScreen(
                    onBackPressed = { finish() }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AIAutoReplyScreen(onBackPressed: () -> Unit) {
    val context = LocalContext.current
    val configManager = remember { AIConfigManager(context) }
    // DON'T cache AIService - create fresh instance for each test to get latest settings
    val replyManager = remember { AIReplyManager(context) }
    val settingsManager = remember { com.message.bulksend.autorespond.settings.AutoReplySettingsManager(context) }
    val scope = rememberCoroutineScope()
    
    var selectedProvider by remember { 
        mutableStateOf(replyManager.getSelectedProvider())
    }
    var isEnabled by remember { mutableStateOf(settingsManager.isAIReplyEnabled()) }
    var testMessage by remember { mutableStateOf("") }
    var testResult by remember { mutableStateOf("") }
    var isTesting by remember { mutableStateOf(false) }
    
    // Info Dialog States
    var showInfoDialog by remember { mutableStateOf(false) }
    var infoDialogProvider by remember { mutableStateOf(AIProvider.GEMINI) }
    
    // Simple sync function
    /*
    suspend fun performSync() {
        isSyncing = true
        try {
            val success = subscriptionManager.syncFromFirestore()
            subscriptionStatus = subscriptionManager.getSubscriptionStatus()
            
            // Update simple status
            remainingSubTime = subscriptionManager.getSimpleStatus()
            
            // Update last sync time
            val sdf = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
            lastSyncTime = sdf.format(java.util.Date())
            
            if (success) {
                Toast.makeText(context, "✅ Subscription synced successfully", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "⚠️ No active subscription found", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(context, "❌ Sync failed: ${e.message}", Toast.LENGTH_SHORT).show()
        } finally {
            isSyncing = false
        }
    }
    */
    
    // Real-time sync with settings
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(500)
            isEnabled = settingsManager.isAIReplyEnabled()
        }
    }
    
    val providers = listOf(AIProvider.GEMINI, AIProvider.CHATGPT)
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text("AI Auto Reply", color = Color.White, fontWeight = FontWeight.Medium)
                },
                navigationIcon = {
                    IconButton(onClick = onBackPressed) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF8B5CF6))
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFF5F5F5))
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // Simple sync indicator (hidden from user)
            // Sync happens automatically in background
            
            /*
            // ChatsPromo AI Subscription Status Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        val intent = Intent(context, com.message.bulksend.plan.PlansActivity::class.java)
                        context.startActivity(intent)
                    },
                colors = CardDefaults.cardColors(
                    containerColor = when (subscriptionStatus) {
                        SubscriptionStatus.SUBSCRIBED -> Color(0xFF8B5CF6)
                        SubscriptionStatus.TRIAL_ACTIVE -> Color(0xFF3B82F6)
                        else -> Color(0xFFEF4444)
                    }
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            when (subscriptionStatus) {
                                SubscriptionStatus.SUBSCRIBED -> Icons.Default.WorkspacePremium
                                SubscriptionStatus.TRIAL_ACTIVE -> Icons.Default.Timer
                                else -> Icons.Default.Warning
                            },
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "ChatsPromo AI Subscription",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                when (subscriptionStatus) {
                                    SubscriptionStatus.SUBSCRIBED -> "Status: $remainingSubTime"
                                    SubscriptionStatus.TRIAL_ACTIVE -> "Trial Active - 2 Hours Free"
                                    SubscriptionStatus.TRIAL_EXPIRED -> "Trial Expired"
                                    SubscriptionStatus.NO_TRIAL -> "Not Subscribed"
                                },
                                fontSize = 14.sp,
                                color = Color.White.copy(alpha = 0.9f)
                            )
                        }
                        if (isSyncing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                Icons.Default.ChevronRight,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // Explanation text
                    Text(
                        "ChatsPromo AI is our built-in AI that works without API keys. Subscription required for unlimited auto-replies.",
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.8f),
                        lineHeight = 16.sp
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            */
            
            // AI Reply Status Card (Read-only - controlled from Settings)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        val intent = Intent(context, com.message.bulksend.autorespond.settings.AutoReplySettingsActivity::class.java)
                        context.startActivity(intent)
                    },
                colors = CardDefaults.cardColors(
                    containerColor = if (isEnabled) Color(0xFF10B981) else Color(0xFF6B7280)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        if (isEnabled) Icons.Default.CheckCircle else Icons.Default.Cancel,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            if (isEnabled) "AI Reply Active" else "AI Reply Inactive",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            if (isEnabled) "Automatically replying with AI" else "Go to Settings to enable",
                            fontSize = 14.sp,
                            color = Color.White.copy(alpha = 0.9f)
                        )
                    }
                    Icon(
                        Icons.Default.Settings,
                        contentDescription = "Settings",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            

            
            // Select AI Provider
            Text(
                "Select AI Provider",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF212121),
                modifier = Modifier.padding(bottom = 12.dp)
            )
            
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                providers.forEach { provider ->
                    val config = configManager.getConfig(provider)
                    val isConfigured = config.apiKey.isNotEmpty()
                    
                    Card(
                        modifier = Modifier
                            .width(120.dp)
                            .clickable { 
                                selectedProvider = provider
                                replyManager.setSelectedProvider(provider)
                            },
                        colors = CardDefaults.cardColors(
                            containerColor = if (selectedProvider == provider) 
                                Color(0xFF8B5CF6) else Color.White
                        ),
                        shape = RoundedCornerShape(12.dp),
                        elevation = CardDefaults.cardElevation(
                            defaultElevation = if (selectedProvider == provider) 4.dp else 2.dp
                        )
                    ) {
                        Box(modifier = Modifier.fillMaxWidth()) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    tint = if (selectedProvider == provider) Color.White else Color(0xFF8B5CF6),
                                    modifier = Modifier.size(28.dp)
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    provider.displayName,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = if (selectedProvider == provider) Color.White else Color(0xFF212121),
                                    maxLines = 1
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        if (isConfigured) Icons.Default.CheckCircle else Icons.Default.Warning,
                                        contentDescription = null,
                                        tint = if (selectedProvider == provider) {
                                            Color.White
                                        } else {
                                            if (isConfigured) Color(0xFF10B981) else Color(0xFFF59E0B)
                                        },
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        if (isConfigured) "Ready" else "Setup",
                                        fontSize = 11.sp,
                                        color = if (selectedProvider == provider) 
                                            Color.White else Color(0xFF6B7280)
                                    )
                                }
                            }
                            
                            // Info button in top-right corner
                            IconButton(
                                onClick = {
                                    infoDialogProvider = provider
                                    showInfoDialog = true
                                },
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .size(24.dp)
                            ) {
                                Icon(
                                    Icons.Default.Info,
                                    contentDescription = "Info",
                                    tint = if (selectedProvider == provider) 
                                        Color.White.copy(alpha = 0.7f) else Color(0xFF6B7280),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Quick Actions
            Text(
                "Quick Actions",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF212121),
                modifier = Modifier.padding(bottom = 12.dp)
            )
            
            ActionCard(
                icon = Icons.Default.Settings,
                title = "Configure ${selectedProvider.displayName}",
                description = "Setup API key and select model",
                onClick = {
                    val intent = Intent(context, AIReplyActivity::class.java)
                    context.startActivity(intent)
                }
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Test AI Reply
            Text(
                "Test AI Reply",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF212121),
                modifier = Modifier.padding(bottom = 12.dp)
            )
            
            OutlinedTextField(
                value = testMessage,
                onValueChange = { testMessage = it },
                label = { Text("Test Message") },
                placeholder = { Text("Enter a message to test AI reply...") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF8B5CF6),
                    focusedLabelColor = Color(0xFF8B5CF6)
                )
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Button(
                onClick = {
                    if (testMessage.isBlank()) {
                        Toast.makeText(context, "Please enter a test message", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    
                    val config = configManager.getConfig(selectedProvider)
                    if (config.apiKey.isEmpty()) {
                        Toast.makeText(context, "Please configure ${selectedProvider.displayName} first", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    
                    isTesting = true
                    testResult = "Generating reply..."
                    android.util.Log.d("AIAutoReply", "🧪 Starting test with provider: $selectedProvider")
                    
                    scope.launch {
                        try {
                            android.util.Log.d("AIAutoReply", "🧪 Creating fresh AIService instance to get latest settings...")
                            // Create fresh AIService instance to get latest prompt changes
                            val aiService = AIService(context)
                            
                            android.util.Log.d("AIAutoReply", "🧪 Calling generateReply...")
                            // Use test phone number for AI Agent to work properly
                            val reply = aiService.generateReply(selectedProvider, testMessage, "Test User", "+919999999999")
                            android.util.Log.d("AIAutoReply", "✅ Reply received: ${reply.take(100)}")
                            testResult = reply
                        } catch (e: Exception) {
                            android.util.Log.e("AIAutoReply", "❌ Error: ${e.message}", e)
                            testResult = "Error: ${e.message}\n\nStack: ${e.stackTraceToString().take(500)}"
                        } finally {
                            isTesting = false
                            android.util.Log.d("AIAutoReply", "🧪 Test completed")
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8B5CF6)),
                enabled = !isTesting
            ) {
                if (isTesting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(if (isTesting) "Testing..." else "Test AI Reply")
            }
            
            if (testResult.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF3F4F6)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "AI Response:",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF6B7280)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            testResult,
                            fontSize = 14.sp,
                            color = Color(0xFF212121)
                        )
                    }
                }
            }
            
                Spacer(modifier = Modifier.height(80.dp))
            }
        }
    /*
    // Trial Offer Dialog
    if (showTrialDialog) {
        TrialOfferDialog(
            onAccept = {
                scope.launch {
                    val success = subscriptionManager.startTrial()
                    if (success) {
                        showTrialDialog = false
                        selectedProvider = AIProvider.CHATSPROMO
                        replyManager.setSelectedProvider(AIProvider.CHATSPROMO)
                        subscriptionStatus = SubscriptionStatus.TRIAL_ACTIVE
                        Toast.makeText(context, "🎉 Trial started! Enjoy 2 hours free.", Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(context, "Failed to start trial. Please try again.", Toast.LENGTH_SHORT).show()
                    }
                }
            },
            onDismiss = { showTrialDialog = false }
        )
    }
    
    // Trial Expired Dialog
    if (showTrialExpiredDialog) {
        TrialExpiredDialog(
            onUpgrade = {
                showTrialExpiredDialog = false
                val intent = Intent(context, com.message.bulksend.plan.PlansActivity::class.java)
                context.startActivity(intent)
            },
            onDismiss = { showTrialExpiredDialog = false }
        )
    }
    
    */
    // Info Dialog
    if (showInfoDialog) {
        AIProviderInfoDialog(
            provider = infoDialogProvider,
            onDismiss = { showInfoDialog = false }
        )
    }
}

@Composable
fun ActionCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = Color(0xFF8B5CF6),
                modifier = Modifier.size(40.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF212121)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    description,
                    fontSize = 14.sp,
                    color = Color(0xFF6B7280)
                )
            }
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = Color(0xFF9CA3AF)
            )
        }
    }
}

/**
 * Beautiful Trial Dialog - 1 Day Free Trial
 */
@Composable
fun TrialOfferDialog(
    onAccept: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1a1a2e),
        shape = RoundedCornerShape(24.dp),
        title = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Animated gift icon
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .background(
                            androidx.compose.ui.graphics.Brush.linearGradient(
                                listOf(Color(0xFF667eea), Color(0xFFf093fb))
                            ),
                            androidx.compose.foundation.shape.CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.CardGiftcard,
                        null,
                        tint = Color.White,
                        modifier = Modifier.size(40.dp)
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "🎉 Free Trial!",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Try ChatsPromo AI FREE for 2 Hours",
                    fontSize = 16.sp,
                    color = Color(0xFF94A3B8),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TrialFeatureRow(Icons.Default.AllInclusive, "Unlimited AI Responses")
                TrialFeatureRow(Icons.Default.Chat, "WhatsApp & Instagram Auto Reply")
                TrialFeatureRow(Icons.Default.Speed, "Fast Response Time")
                TrialFeatureRow(Icons.Default.CreditCardOff, "No Credit Card Required")
            }
        },
        confirmButton = {
            Button(
                onClick = onAccept,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF667eea)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Start Free Trial", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Maybe Later", color = Color(0xFF64748B))
            }
        }
    )
}

@Composable
fun TrialFeatureRow(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = Color(0xFF10B981), modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(12.dp))
        Text(text, fontSize = 14.sp, color = Color.White)
    }
}

/**
 * Trial Expired Dialog
 */
@Composable
fun TrialExpiredDialog(
    onUpgrade: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1a1a2e),
        shape = RoundedCornerShape(24.dp),
        title = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(70.dp)
                        .background(Color(0xFFEF4444).copy(alpha = 0.2f), androidx.compose.foundation.shape.CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Timer, null, tint = Color(0xFFEF4444), modifier = Modifier.size(36.dp))
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text("Trial Ended", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Your 2-hour free trial has expired.\nUpgrade to continue using ChatsPromo AI.",
                    fontSize = 14.sp,
                    color = Color(0xFF94A3B8),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        },
        text = null,
        confirmButton = {
            Button(
                onClick = onUpgrade,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF667eea)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Upgrade Now", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = Color(0xFF64748B))
            }
        }
    )
}

/**
 * AI Provider Info Dialog
 */
@Composable
fun AIProviderInfoDialog(
    provider: AIProvider,
    onDismiss: () -> Unit
) {
    val (title, description, features, icon, color) = when (provider) {
        AIProvider.CHATSPROMO -> {
            Tuple5(
                "ChatsPromo AI",
                "Our built-in AI service designed for WhatsApp auto-replies. No API key setup required.",
                listOf(
                    "✅ No API key needed",
                    "✅ Unlimited auto-replies",
                    "✅ Optimized for messaging",
                    "✅ Fast response time",
                    "⚠️ Subscription required"
                ),
                Icons.Default.AutoAwesome,
                Color(0xFF8B5CF6)
            )
        }
        AIProvider.GEMINI -> {
            Tuple5(
                "Google Gemini AI",
                "Use Google's powerful Gemini AI with your own API key. No subscription needed.",
                listOf(
                    "✅ Use your own API key",
                    "✅ No subscription required",
                    "✅ Google's latest AI model",
                    "✅ Free tier available",
                    "⚙️ Requires API setup"
                ),
                Icons.Default.Psychology,
                Color(0xFF4285F4)
            )
        }
        AIProvider.CHATGPT -> {
            Tuple5(
                "OpenAI ChatGPT",
                "Use OpenAI's ChatGPT with your own API key. No subscription needed.",
                listOf(
                    "✅ Use your own API key",
                    "✅ No subscription required",
                    "✅ OpenAI's proven AI model",
                    "✅ Pay-per-use pricing",
                    "⚙️ Requires API setup"
                ),
                Icons.Default.Chat,
                Color(0xFF10A37F)
            )
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1a1a2e),
        shape = RoundedCornerShape(24.dp),
        title = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(70.dp)
                        .background(color.copy(alpha = 0.2f), androidx.compose.foundation.shape.CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, null, tint = color, modifier = Modifier.size(36.dp))
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(title, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    description,
                    fontSize = 14.sp,
                    color = Color(0xFF94A3B8),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                features.forEach { feature ->
                    Text(
                        feature,
                        fontSize = 14.sp,
                        color = Color.White,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = color),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Got it!", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = null
    )
}

// Helper data class for tuple
data class Tuple5<A, B, C, D, E>(val first: A, val second: B, val third: C, val fourth: D, val fifth: E)
