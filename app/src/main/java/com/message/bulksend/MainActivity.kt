package com.message.bulksend

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Chat
import androidx.compose.runtime.*
import android.provider.Settings
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.ui.window.DialogProperties
import com.message.bulksend.utils.isAccessibilityServiceEnabled
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import com.message.bulksend.support.CustomerChatSupportActivity
import com.message.bulksend.support.WelcomeMessageManager
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.airbnb.lottie.compose.*
import com.message.bulksend.anylatic.ReportlistActivity
import com.message.bulksend.bulksend.BulksendActivity

import com.message.bulksend.bulksend.BulkMessage1Activity
import com.message.bulksend.bulksend.CampaignStatusActivity
import com.message.bulksend.bulksend.SelectActivity
import com.message.bulksend.contactmanager.ContactzActivity
import com.message.bulksend.support.SupportActivity
import com.message.bulksend.templates.TemplateActivity
import com.message.bulksend.auth.UserProfileActivity
import com.message.bulksend.tutorial.FaqActivity
import com.message.bulksend.autorespond.database.MessageDatabase
import com.message.bulksend.autorespond.statusscheduled.StatusBatchManager
import com.message.bulksend.autorespond.statusscheduled.database.StatusBatchRepository

import com.message.bulksend.ui.theme.BulksendTestTheme
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.ktx.analytics
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.ktx.Firebase
import com.message.bulksend.aiagent.tools.woocommerce.WooCommerceAlertProcessor
import com.message.bulksend.userdetails.UserDetailsPreferences
import com.message.bulksend.agreement.PrivacyPolicyDialog
import com.message.bulksend.agreement.PrivacyPolicyHelper
import com.message.bulksend.utils.PreferencesSync
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    private lateinit var firebaseAnalytics: FirebaseAnalytics
    private lateinit var welcomeMessageManager: WelcomeMessageManager
    private lateinit var wooAlertProcessor: WooCommerceAlertProcessor
    private var wooAlertsListener: ListenerRegistration? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Initialize Firebase Analytics
        firebaseAnalytics = Firebase.analytics
        
        // Initialize Welcome Message Manager
        welcomeMessageManager = WelcomeMessageManager(this)
        wooAlertProcessor = WooCommerceAlertProcessor(applicationContext)
        PreferencesSync.startRealtimeSubscriptionSync(applicationContext)

        // Log app open event
        logAnalyticsEvent("app_opened", null)
        
        // Update last seen (only once per day)
        updateLastSeenIfNeeded()
        
        // Send welcome messages for first time users
        lifecycleScope.launch {
            welcomeMessageManager.sendWelcomeMessagesIfNeeded()
        }

        // Restore status scheduler alarms on every app launch so missed/orphan alarms get fixed automatically.
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val database = MessageDatabase.getDatabase(applicationContext)
                val repository = StatusBatchRepository(database.statusBatchDao())
                val manager = StatusBatchManager(applicationContext, repository)
                val restoredCount = manager.restoreScheduledBatches()
                Log.d("MainActivity", "Status scheduler restore completed: $restoredCount batches")
            } catch (e: Exception) {
                Log.e("MainActivity", "Failed to restore status scheduler alarms", e)
            }
        }

        // Process any pending WooCommerce owner alerts on app launch (fallback).
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val result = wooAlertProcessor.processPendingAlerts(limit = 5)
                if (result.totalFetched > 0) {
                    Log.d(
                        "MainActivity",
                        "Woo alerts processed on launch: fetched=${result.totalFetched}, sent=${result.sentCount}, failed=${result.failedCount}"
                    )
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "Failed to process WooCommerce alerts on launch", e)
            }
        }

        startWooRealtimeAlertListener()
        
        // Check if user has agreed to accessibility terms
        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        val hasAgreedToAccessibility = prefs.getBoolean("accessibility_agreed", false)

        setContent {
            BulksendTestTheme {
                var showAccessibilityDialog by remember { mutableStateOf(!hasAgreedToAccessibility) }
                var showPrivacyDialog by remember { mutableStateOf(!PrivacyPolicyHelper.hasSeenPrivacyPolicy(this)) }

                MainScreen()

                // Show privacy policy dialog first (if not seen)
                if (showPrivacyDialog) {
                    PrivacyPolicyDialog(
                        onDismiss = {
                            PrivacyPolicyHelper.markPrivacyPolicyAsSeen(this@MainActivity)
                            showPrivacyDialog = false
                        }
                    )
                }

                // Show accessibility dialog if not agreed (after privacy dialog)
                if (showAccessibilityDialog && !showPrivacyDialog) {
                    AccessibilityDialog(
                        onAgree = {
                            // Save agreement to SharedPreferences
                            prefs.edit().putBoolean("accessibility_agreed", true).apply()
                            showAccessibilityDialog = false
                        },
                        onDisagree = {
                            // Keep showing dialog - don't close
                            Toast.makeText(
                                this@MainActivity,
                                "You must agree to continue using the app",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    )
                }
            }
        }
    }

    // Helper function to log analytics events
    private fun logAnalyticsEvent(eventName: String, params: Bundle?) {
        firebaseAnalytics.logEvent(eventName, params)
    }

    override fun onResume() {
        super.onResume()
        // Log screen view
        firebaseAnalytics.logEvent(FirebaseAnalytics.Event.SCREEN_VIEW, Bundle().apply {
            putString(FirebaseAnalytics.Param.SCREEN_NAME, "MainActivity")
            putString(FirebaseAnalytics.Param.SCREEN_CLASS, "MainActivity")
        })
        
        // Perform auto backup if needed
        performAutoBackup()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        // Cleanup WelcomeMessageManager resources
        welcomeMessageManager.cleanup()
        wooAlertsListener?.remove()
        wooAlertsListener = null
        PreferencesSync.stopRealtimeSubscriptionSync()
    }

    private fun startWooRealtimeAlertListener() {
        wooAlertsListener?.remove()
        wooAlertsListener = wooAlertProcessor.startRealtimeListener(
            scope = lifecycleScope
        ) { alert, success ->
            Log.d(
                "MainActivity",
                "Woo realtime request handled: alertId=${alert.alertId}, sent=$success"
            )
        }
    }
    
    /**
     * Automatically backup data in background if needed
     */
    private fun performAutoBackup() {
        val autoBackupManager = com.message.bulksend.sync.AutoBackupManager(this)
        
        // Use lifecycleScope for coroutine
        lifecycleScope.launch {
            autoBackupManager.performAutoBackupIfNeeded(this) { success, message ->
                if (success) {
                    Log.d("MainActivity", "Auto backup completed: $message")
                    // Optionally show a subtle notification
                    // Toast.makeText(this@MainActivity, "Data backed up", Toast.LENGTH_SHORT).show()
                } else {
                    Log.d("MainActivity", "Auto backup: $message")
                }
            }
        }
    }
    
    /**
     * Update last seen in userDetails collection (only once per day)
     * This tracks daily active users
     */
    private fun updateLastSeenIfNeeded() {
        val userDetailsPrefs = UserDetailsPreferences(this)
        
        // Check if we already updated today
        if (!userDetailsPrefs.shouldUpdateLastSeen()) {
            Log.d("MainActivity", "Last seen already updated today, skipping")
            return
        }
        
        val userId = FirebaseAuth.getInstance().currentUser?.uid
        if (userId == null) {
            Log.d("MainActivity", "User not logged in, skipping last seen update")
            return
        }
        
        lifecycleScope.launch {
            try {
                val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
                    .format(java.util.Date())
                val currentTimestamp = com.google.firebase.Timestamp.now()
                
                // Update Firestore userDetails with lastSeen
                withContext(Dispatchers.IO) {
                    FirebaseFirestore.getInstance()
                        .collection("userDetails")
                        .document(userId)
                        .update(
                            mapOf(
                                "lastSeen" to currentTimestamp,
                                "lastSeenDate" to today
                            )
                        )
                        .await()
                }
                
                // Save to SharedPreferences so we don't update again today
                userDetailsPrefs.setLastSeenDate(today)
                
                Log.d("MainActivity", "✅ Last seen updated for userId: $userId on $today")
            } catch (e: Exception) {
                // If document doesn't exist, try to set with merge
                try {
                    val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
                        .format(java.util.Date())
                    val currentTimestamp = com.google.firebase.Timestamp.now()
                    
                    withContext(Dispatchers.IO) {
                        FirebaseFirestore.getInstance()
                            .collection("userDetails")
                            .document(userId)
                            .set(
                                mapOf(
                                    "lastSeen" to currentTimestamp,
                                    "lastSeenDate" to today
                                ),
                                com.google.firebase.firestore.SetOptions.merge()
                            )
                            .await()
                    }
                    
                    userDetailsPrefs.setLastSeenDate(today)
                    Log.d("MainActivity", "✅ Last seen created/merged for userId: $userId on $today")
                } catch (e2: Exception) {
                    Log.e("MainActivity", "❌ Failed to update last seen", e2)
                }
            }
        }
    }
}

// Data classes to hold UI information
data class FeatureItem(
    val icon: ImageVector? = null,
    val lottieAsset: String? = null,
    val title: String,
    val subtitle: String = "",
    val infoText: String = "", // Info dialog text
    val hasBadge: Boolean = false,
    val badgeText: String = "",
    val gradient: List<Color> = listOf(Color(0xFFE3F2FD), Color(0xFFBBDEFB)),
    val onClick: () -> Unit = {} // Added onClick lambda
)

@Composable
fun TypewriterText(
    text: String,
    modifier: Modifier = Modifier,
    style: androidx.compose.ui.text.TextStyle = LocalTextStyle.current
) {
    var displayedText by remember { mutableStateOf("") }

    LaunchedEffect(text) {
        displayedText = ""
        text.forEachIndexed { index, _ ->
            displayedText = text.substring(0, index + 1)
            delay(50) // Adjust typing speed here
        }
    }

    Text(
        text = displayedText,
        modifier = modifier,
        style = style
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopBar() {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        Color(0xFF1a1a2e),
                        Color(0xFF16213e),
                        Color(0xFF0f3460)
                    )
                )
            )
    ) {
        // Status bar spacer
        Spacer(modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars))
        
        // Header content
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "ChatsPromo",
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                color = Color(0xFF10B981)
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Video Tutorial Button with Lottie Animation
                AnimatedYouTubeButton(
                    onClick = {
                        context.startActivity(Intent(context, com.message.bulksend.tutorial.VideoTutorialActivity::class.java))
                    }
                )

                // Premium Button with Animation
                AnimatedPremiumButton(
                    onClick = {
                        context.startActivity(Intent(context, com.message.bulksend.plan.PrepackActivity::class.java))
                    }
                )

                // Support Button with Badge
                BadgedSupportIcon(
                    onClick = {
                        context.startActivity(Intent(context, CustomerChatSupportActivity::class.java))
                    }
                )

                // Profile Button
                IconButton(
                    onClick = {
                        context.startActivity(Intent(context, UserProfileActivity::class.java))
                    }
                ) {
                    Icon(
                        Icons.Outlined.AccountCircle,
                        contentDescription = "Profile",
                        tint = Color(0xFF00D4FF),
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun AnimatedYouTubeButton(onClick: () -> Unit) {
    val composition by rememberLottieComposition(
        LottieCompositionSpec.Asset("Youtube.json")
    )
    val progress by animateLottieCompositionAsState(
        composition = composition,
        iterations = LottieConstants.IterateForever
    )

    // Show icon if composition is null (file not found)
    if (composition == null) {
        IconButton(onClick = onClick) {
            Icon(
                Icons.Outlined.PlayCircle,
                contentDescription = "Video Tutorial",
                tint = Color.White,
                modifier = Modifier.size(28.dp)
            )
        }
    } else {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clickable(onClick = onClick)
                .padding(4.dp),
            contentAlignment = Alignment.Center
        ) {
            LottieAnimation(
                composition = composition,
                progress = { progress },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
fun AnimatedPremiumButton(onClick: () -> Unit) {
    // Infinite scale animation for pulsing effect
    val infiniteTransition = rememberInfiniteTransition(label = "premium_pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "premium_scale"
    )

    IconButton(onClick = onClick) {
        Icon(
            painter = androidx.compose.ui.res.painterResource(id = R.drawable.premium),
            contentDescription = "Get Premium",
            tint = Color.Unspecified,
            modifier = Modifier
                .size(24.dp)
                .scale(scale)
        )
    }
}

// Tab data class to support both icons and Lottie animations
data class TabItem(
    val icon: ImageVector? = null,
    val lottieAsset: String? = null,
    val label: String,
    val color: Color
)

@Composable
fun Custom3DTabBar(selectedTab: Int, onTabSelected: (Int) -> Unit) {
    val tabs = listOf(
        TabItem(Icons.Outlined.Home, null, "Home", Color(0xFF6366F1)),
        TabItem(Icons.Outlined.Chat, null, "Auto\nRespond", Color(0xFFEC4899)),
        TabItem(Icons.Outlined.People, null, "Chatspromo\nCRM", Color(0xFF10B981)),
        TabItem(Icons.Outlined.Support, null, "Help\nSupport", Color(0xFF10B981)),
        TabItem(null, "proplan.json", "Plan", Color(0xFFF59E0B))
    )
    
    val scrollState = rememberScrollState()

    // Liquid navigation bar with dark background
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
    ) {
        // Dark curved background
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(90.dp)
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF1a1a2e),
                            Color(0xFF16213e),
                            Color(0xFF0f3460)
                        )
                    ),
                    shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp)
                )
        ) {
            // Horizontal scrollable container
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .horizontalScroll(scrollState)
                    .padding(horizontal = 20.dp, vertical = 15.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                tabs.forEachIndexed { index, tabItem ->
                    LiquidTabItem(
                        tabItem = tabItem,
                        isSelected = selectedTab == index,
                        onClick = { onTabSelected(index) }
                    )
                }
            }
        }
    }
}

@Composable
fun LiquidTabItem(
    tabItem: TabItem,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.15f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "tab_scale"
    )
    
    val iconScale by animateFloatAsState(
        targetValue = if (isSelected) 1.2f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "icon_scale"
    )

    // Card with background for selected tab
    Card(
        modifier = Modifier
            .width(90.dp)
            .height(70.dp)
            .scale(scale),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) tabItem.color.copy(alpha = 0.3f) else Color.Transparent
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isSelected) 4.dp else 0.dp
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    onClick = onClick,
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                )
                .padding(top = 8.dp, bottom = 4.dp, start = 4.dp, end = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Icon or Lottie Animation
            if (tabItem.lottieAsset != null) {
                val composition by rememberLottieComposition(LottieCompositionSpec.Asset(tabItem.lottieAsset))
                val progress by animateLottieCompositionAsState(
                    composition = composition, 
                    iterations = LottieConstants.IterateForever
                )
                LottieAnimation(
                    composition = composition, 
                    progress = { progress }, 
                    modifier = Modifier
                        .size(26.dp)
                        .scale(iconScale)
                )
            } else if (tabItem.icon != null) {
                Icon(
                    imageVector = tabItem.icon,
                    contentDescription = tabItem.label,
                    modifier = Modifier
                        .size(26.dp)
                        .scale(iconScale),
                    tint = if (isSelected) Color.White else Color.White.copy(alpha = 0.6f)
                )
            }
            
            Text(
                text = tabItem.label,
                fontSize = 10.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                color = if (isSelected) Color.White else Color.White.copy(alpha = 0.7f),
                maxLines = 2,
                textAlign = TextAlign.Center,
                lineHeight = 11.sp
            )
        }
    }
}

@Composable
fun Custom3DTabItem(
    icon: ImageVector,
    label: String,
    color: Color,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.1f else 0.95f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "scale"
    )

    val iconScale by animateFloatAsState(
        targetValue = if (isSelected) 1.2f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "icon_scale"
    )

    Column(
        modifier = Modifier
            .width(90.dp)
            .height(50.dp)
            .scale(scale)
            .clickable(
                onClick = onClick,
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            modifier = Modifier
                .size(26.dp)
                .scale(iconScale),
            tint = if (isSelected) Color.White else Color.White.copy(alpha = 0.6f)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            fontSize = 10.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = if (isSelected) Color.White else Color.White.copy(alpha = 0.7f),
            maxLines = 1
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen() {
    val context = LocalContext.current
    var showExtractPermissionDialog by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableStateOf(0) }
    
    val backgroundBrush = Brush.verticalGradient(
        colors = listOf(
            Color(0xFF0f0c29),
            Color(0xFF302b63),
            Color(0xFF24243e),
            Color(0xFF0f0c29)
        )
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundBrush)
    ) {
        Scaffold(
            topBar = { TopBar() },
            bottomBar = {
                Custom3DTabBar(
                    selectedTab = selectedTab,
                    onTabSelected = { index ->
                        when (index) {
                            0 -> selectedTab = 0 // Stay on Home
                            1 -> {
                                // Launch Auto Respond Activity
                                context.startActivity(Intent(context, com.message.bulksend.autorespond.AutoRespondActivity::class.java))
                            }
                            2 -> {
                                // Launch Lead Manager Activity
                                context.startActivity(Intent(context, com.message.bulksend.leadmanager.LeadManagerActivity::class.java))
                            }
                            3 -> {
                                // Launch Support Activity
                                context.startActivity(Intent(context, SupportActivity::class.java))
                            }
                            4 -> {
                                // Launch Plan Activity
                                context.startActivity(Intent(context, com.message.bulksend.plan.PrepackActivity::class.java))
                            }
                        }
                    }
                )
            },
            containerColor = Color.Transparent,
            content = { innerPadding ->
                // Always show Home content
                HomeContent(innerPadding, backgroundBrush, context) { showExtractPermissionDialog = true }
            }
        )
    }

    // Extract Permission Dialog
    if (showExtractPermissionDialog) {
        ExtractPermissionDialog(
            onAgree = {
                showExtractPermissionDialog = false
                context.startActivity(Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS))
            },
            onDismiss = {
                showExtractPermissionDialog = false
            }
        )
    }
}

@Composable
fun HomeContent(
    innerPadding: PaddingValues,
    backgroundBrush: Brush,
    context: Context,
    onShowExtractDialog: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .padding(innerPadding)
            .fillMaxSize()
            .background(backgroundBrush),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Hero Card - Send Message
        item { AnimatedSendMessageCard() }
        

        
        // Section Divider with Title
        item { 
            SectionDivider(
                title = "Campaign Manager",
                icon = Icons.Outlined.Campaign,
                color = Color(0xFF6366F1)
            )
        }
        
        // Bulk Sending Section - Modern Grid
        item {
            ModernFeatureGrid(
                items = listOf(
                    FeatureItem(
                        lottieAsset = "autoreply.json",
                        title = "Auto Reply",
                        subtitle = "Automated message responses",
                        infoText = "Set up automatic replies for WhatsApp messages.\n\n• Keyword-based auto replies\n• AI-powered responses\n• Menu-based replies\n• Schedule auto replies\n• Smart lead capture",
                        gradient = listOf(Color(0xFF6366F1), Color(0xFF8B5CF6)),
                        onClick = {
                            context.startActivity(Intent(context, com.message.bulksend.autorespond.AutoRespondActivity::class.java))
                        }
                    ),
                    FeatureItem(
                        lottieAsset = "crm.json",
                        title = "CRM",
                        subtitle = "Customer relationship management",
                        infoText = "Manage your leads and customers effectively.\n\n• Lead tracking and scoring\n• Customer data management\n• Sales pipeline tracking\n• Follow-up reminders\n• Analytics and reports",
                        gradient = listOf(Color(0xFF10B981), Color(0xFF059669)),
                        onClick = {
                            context.startActivity(Intent(context, com.message.bulksend.leadmanager.LeadManagerActivity::class.java))
                        }
                    ),
                    FeatureItem(
                        lottieAsset = "sheet.json",
                        title = "Table Sheet",
                        subtitle = "Data management tool",
                        infoText = "Create and manage data tables efficiently.\n\n• Create custom tables\n• Import/export data\n• Advanced filtering\n• Data visualization\n• Collaboration features",
                        gradient = listOf(Color(0xFFF59E0B), Color(0xFFD97706)),
                        onClick = {
                            context.startActivity(Intent(context, com.message.bulksend.tablesheet.TableSheetActivity::class.java))
                        }
                    )
                )
            )
        }
        
        // AI Agent Section - Single Card
        // AI Agent Section with Custom Design
        item {
            Spacer(modifier = Modifier.height(16.dp))
            AIAgentCard(
                onClick = {
                    context.startActivity(Intent(context, com.message.bulksend.aiagent.AIAgentDashboardActivity::class.java))
                }
            )
        }
        
        // Status Scheduler Card
        item {
            Spacer(modifier = Modifier.height(16.dp))
            StatusSchedulerCard(
                onClick = {
                    context.startActivity(Intent(context, com.message.bulksend.autorespond.statusscheduled.StatusSchedulerActivity::class.java))
                }
            )
        }
        
        // Section Divider
        item { 
            SectionDivider(
                title = "Quick Actions",
                icon = Icons.Outlined.FlashOn,
                color = Color(0xFFEC4899)
            )
        }
        
        // Quick Actions - First Row (2 cards)
        item {
            QuickActionCards(
                items = listOf(
                    QuickAction(
                        icon = Icons.Outlined.TrendingUp,
                        lottieAsset = null,
                        title = "Growth Tools",
                        color = Color(0xFF8B5CF6),
                        onClick = {
                            context.startActivity(Intent(context, com.message.bulksend.tools.ToolsActivity::class.java))
                        }
                    ),
                    QuickAction(
                        icon = Icons.Outlined.CloudSync,
                        lottieAsset = null,
                        title = "Cloud\nSync",
                        color = Color(0xFF3B82F6),
                        onClick = {
                            context.startActivity(Intent(context, com.message.bulksend.sync.SyncActivity::class.java))
                        }
                    )
                )
            )
        }
        
        // Collapsible Help & Support Section
        item {
            CollapsibleHelpSupportSection(
                items = listOf(
                    SupportItem(
                        icon = Icons.Outlined.CardGiftcard,
                        title = "ChatsPromo Affiliate",
                        subtitle = "Track installs, signups and plan sales",
                        onClick = {
                            context.startActivity(Intent(context, com.message.bulksend.referral.ReferralActivity::class.java))
                        }
                    ),
                    SupportItem(
                        icon = Icons.Outlined.SupportAgent,
                        title = "Contact Support",
                        subtitle = "Get help from our team",
                        onClick = {
                            context.startActivity(Intent(context, SupportActivity::class.java))
                        }
                    ),
                    SupportItem(
                        icon = Icons.Outlined.Quiz,
                        title = "FAQs",
                        subtitle = "Frequently asked questions",
                        onClick = {
                            context.startActivity(Intent(context, FaqActivity::class.java))
                        }
                    ),
                    SupportItem(
                        icon = Icons.Outlined.PlayCircle,
                        title = "Video Tutorials",
                        subtitle = "Learn how to use the app",
                        onClick = {
                            context.startActivity(Intent(context, com.message.bulksend.tutorial.VideoTutorialActivity::class.java))
                        }
                    )
                )
            )
        }
        
        // Bottom Spacer
        item { Spacer(modifier = Modifier.height(20.dp)) }
    }
}

// Quick Stats Row - Only Contacts
@Composable
fun QuickStatsRow() {
    val context = LocalContext.current
    var totalContacts by remember { mutableStateOf(0) }
    
    LaunchedEffect(Unit) {
        // Get total contacts from groups
        val sharedPref = context.getSharedPreferences("subscription_prefs", android.content.Context.MODE_PRIVATE)
        totalContacts = sharedPref.getInt("current_contacts", 0)
    }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E2E).copy(alpha = 0.8f)),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF8B5CF6).copy(alpha = 0.3f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF8B5CF6).copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Outlined.People, null, tint = Color(0xFF8B5CF6), modifier = Modifier.size(24.dp))
                }
                Spacer(Modifier.width(14.dp))
                Column {
                    Text("Total Contacts", fontSize = 12.sp, color = Color(0xFF94A3B8))
                    Text(
                        totalContacts.toString(),
                        fontWeight = FontWeight.Bold,
                        fontSize = 24.sp,
                        color = Color.White
                    )
                }
            }
            Icon(
                Icons.AutoMirrored.Filled.ArrowForwardIos,
                null,
                tint = Color(0xFF64748B),
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
fun StatCard(
    icon: ImageVector,
    value: String,
    label: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E2E).copy(alpha = 0.8f)),
        border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.3f))
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, null, tint = color, modifier = Modifier.size(20.dp))
            Spacer(Modifier.height(4.dp))
            Text(value, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color.White)
            Text(label, fontSize = 10.sp, color = Color(0xFF94A3B8))
        }
    }
}

// Section Divider
@Composable
fun SectionDivider(title: String, icon: ImageVector, color: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(color.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = color, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(12.dp))
        Text(
            title,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            color = Color.White
        )
        Spacer(Modifier.weight(1f))
        Box(
            modifier = Modifier
                .weight(1f)
                .height(1.dp)
                .background(
                    brush = Brush.horizontalGradient(
                        colors = listOf(color.copy(alpha = 0.5f), Color.Transparent)
                    )
                )
        )
    }
}

// Modern Feature Grid - Horizontal Scrollable
@Composable
fun ModernFeatureGrid(items: List<FeatureItem>) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items.forEach { item ->
            CompactFeatureCard(item)
        }
    }
}

// Compact Feature Card for horizontal grid
@Composable
fun CompactFeatureCard(item: FeatureItem) {
    var showInfoDialog by remember { mutableStateOf(false) }
    
    // Info Dialog
    if (showInfoDialog && item.infoText.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { showInfoDialog = false },
            containerColor = Color.White,
            shape = RoundedCornerShape(20.dp),
            icon = {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(brush = Brush.horizontalGradient(item.gradient)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        item.icon ?: Icons.Filled.Star,
                        null,
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            },
            title = {
                Text(item.title, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color(0xFF1E293B))
            },
            text = {
                Text(item.infoText, fontSize = 14.sp, lineHeight = 22.sp, color = Color(0xFF64748B))
            },
            confirmButton = {
                Button(
                    onClick = { showInfoDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = item.gradient.firstOrNull() ?: Color(0xFF6366F1)),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Got it!", fontWeight = FontWeight.SemiBold)
                }
            }
        )
    }
    
    Card(
        modifier = Modifier
            .width(130.dp)
            .clickable(onClick = item.onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(brush = Brush.verticalGradient(item.gradient), shape = RoundedCornerShape(16.dp))
                .padding(12.dp)
        ) {
            // Info button at top right
            if (item.infoText.isNotEmpty()) {
                Icon(
                    Icons.Filled.Info,
                    "Info",
                    tint = Color.White.copy(alpha = 0.3f),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(14.dp)
                        .clickable { showInfoDialog = true }
                )
            }
            
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Animation/Icon directly without box
                if (item.lottieAsset != null) {
                    val composition by rememberLottieComposition(LottieCompositionSpec.Asset(item.lottieAsset))
                    val progress by animateLottieCompositionAsState(composition = composition, iterations = LottieConstants.IterateForever)
                    LottieAnimation(composition = composition, progress = { progress }, modifier = Modifier.size(80.dp))
                } else if (item.icon != null) {
                    Icon(item.icon, null, tint = Color.White, modifier = Modifier.size(40.dp))
                }
                
                Spacer(Modifier.height(10.dp))
                
                Text(
                    item.title,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    lineHeight = 14.sp
                )
            }
        }
    }
}

@Composable
fun ModernFeatureCard(item: FeatureItem) {
    var isPressed by remember { mutableStateOf(false) }
    var showInfoDialog by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.98f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = ""
    )
    
    // Info Dialog
    if (showInfoDialog && item.infoText.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { showInfoDialog = false },
            containerColor = Color.White,
            shape = RoundedCornerShape(20.dp),
            icon = {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            brush = Brush.horizontalGradient(item.gradient)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        item.icon ?: Icons.Filled.Star,
                        null,
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            },
            title = {
                Text(
                    item.title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = Color(0xFF1E293B)
                )
            },
            text = {
                Text(
                    item.infoText,
                    fontSize = 14.sp,
                    lineHeight = 22.sp,
                    color = Color(0xFF64748B)
                )
            },
            confirmButton = {
                Button(
                    onClick = { showInfoDialog = false },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = item.gradient.firstOrNull() ?: Color(0xFF6366F1)
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Got it!", fontWeight = FontWeight.SemiBold)
                }
            }
        )
    }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .clickable(
                onClick = item.onClick,
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.horizontalGradient(item.gradient),
                    shape = RoundedCornerShape(20.dp)
                )
        ) {
            // Info button at top right (subtle)
            if (item.infoText.isNotEmpty()) {
                IconButton(
                    onClick = { showInfoDialog = true },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .size(28.dp)
                ) {
                    Icon(
                        Icons.Filled.Info,
                        "Info",
                        tint = Color.White.copy(alpha = 0.4f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Icon Box
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.White.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    if (item.lottieAsset != null) {
                        val composition by rememberLottieComposition(
                            LottieCompositionSpec.Asset(item.lottieAsset)
                        )
                        val progress by animateLottieCompositionAsState(
                            composition = composition,
                            iterations = LottieConstants.IterateForever
                        )
                        LottieAnimation(
                            composition = composition,
                            progress = { progress },
                            modifier = Modifier.size(48.dp)
                        )
                    } else if (item.icon != null) {
                        Icon(item.icon, null, tint = Color.White, modifier = Modifier.size(28.dp))
                    }
                }
                
                Spacer(Modifier.width(16.dp))
                
                // Text Content
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        item.title,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = Color.White
                    )
                    if (item.subtitle.isNotEmpty()) {
                        Text(
                            item.subtitle,
                            fontSize = 12.sp,
                            color = Color.White.copy(alpha = 0.8f)
                        )
                    }
                }
                
                // Arrow
                Icon(
                    Icons.AutoMirrored.Filled.ArrowForwardIos,
                    null,
                    tint = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

// Quick Action Data
data class QuickAction(
    val icon: ImageVector? = null,
    val lottieAsset: String? = null,
    val title: String,
    val color: Color,
    val onClick: () -> Unit
)

@Composable
fun QuickActionCards(items: List<QuickAction>) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items.forEach { item ->
            QuickActionCard(item, Modifier.weight(1f))
        }
    }
}

@Composable
fun QuickActionCard(item: QuickAction, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier
            .clickable(onClick = item.onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E2E).copy(alpha = 0.9f)),
        border = androidx.compose.foundation.BorderStroke(1.dp, item.color.copy(alpha = 0.3f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(item.color.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                // Icon or Lottie Animation
                if (item.lottieAsset != null) {
                    val composition by rememberLottieComposition(LottieCompositionSpec.Asset(item.lottieAsset))
                    val progress by animateLottieCompositionAsState(
                        composition = composition, 
                        iterations = LottieConstants.IterateForever
                    )
                    if (composition != null) {
                        LottieAnimation(
                            composition = composition, 
                            progress = { progress }, 
                            modifier = Modifier.size(36.dp)
                        )
                    } else {
                        // Fallback icon if Lottie fails - using different icon to debug
                        Icon(Icons.Outlined.Android, null, tint = item.color, modifier = Modifier.size(22.dp))
                    }
                } else if (item.icon != null) {
                    Icon(item.icon, null, tint = item.color, modifier = Modifier.size(22.dp))
                }
            }
            Spacer(Modifier.width(12.dp))
            Text(item.title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = Color.White)
        }
    }
}

// Support Section
data class SupportItem(
    val icon: ImageVector,
    val title: String,
    val subtitle: String,
    val onClick: () -> Unit
)

// Collapsible Help & Support Section
@Composable
fun CollapsibleHelpSupportSection(items: List<SupportItem>) {
    var isExpanded by remember { mutableStateOf(false) }

    Column {
        // Clickable Header
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.1f)),
            modifier = Modifier
                .fillMaxWidth()
                .clickable { isExpanded = !isExpanded }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF10B981).copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Outlined.Support,
                            null,
                            tint = Color(0xFF10B981),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Text(
                        "Help & Support",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = Color.White
                    )
                }
                
                Icon(
                    Icons.AutoMirrored.Filled.ArrowForwardIos,
                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                    tint = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier
                        .size(16.dp)
                        .rotate(if (isExpanded) 90f else 0f)
                )
            }
        }
        
        // Expandable Content
        AnimatedVisibility(
            visible = isExpanded,
            enter = expandVertically(
                animationSpec = tween(300),
                expandFrom = Alignment.Top
            ) + fadeIn(animationSpec = tween(300)),
            exit = shrinkVertically(
                animationSpec = tween(300),
                shrinkTowards = Alignment.Top
            ) + fadeOut(animationSpec = tween(300))
        ) {
            Spacer(modifier = Modifier.height(12.dp))
            SupportSection(items = items)
        }
    }
}

@Composable
fun SupportSection(items: List<SupportItem>) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E2E).copy(alpha = 0.9f)),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF3F3F5F))
    ) {
        Column {
            items.forEachIndexed { index, item ->
                SupportItemRow(item)
                if (index < items.size - 1) {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = Color(0xFF3F3F5F)
                    )
                }
            }
        }
    }
}

@Composable
fun SupportItemRow(item: SupportItem) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = item.onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF10B981).copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(item.icon, null, tint = Color(0xFF10B981), modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(item.title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = Color.White)
            Text(item.subtitle, fontSize = 12.sp, color = Color(0xFF94A3B8))
        }
        Icon(
            Icons.AutoMirrored.Filled.ArrowForwardIos,
            null,
            tint = Color(0xFF64748B),
            modifier = Modifier.size(14.dp)
        )
    }
}

// Custom AI Agent Card with Section Title and Robot Icon
@Composable
fun AIAgentCard(onClick: () -> Unit) {
    val infiniteTransition = rememberInfiniteTransition(label = "ai_glow")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow_alpha"
    )
    
    val scale by animateFloatAsState(
        targetValue = 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "scale"
    )
    
    Column(modifier = Modifier.fillMaxWidth()) {
        // Section Title - AI Agent
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF6366F1).copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Outlined.SmartToy,
                    contentDescription = "AI Agent",
                    tint = Color(0xFF6366F1),
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Text(
                "AI Agent",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = Color.White
            )
            Spacer(Modifier.weight(1f))
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(1.dp)
                    .background(
                        brush = Brush.horizontalGradient(
                            colors = listOf(Color(0xFF6366F1).copy(alpha = 0.5f), Color.Transparent)
                        )
                    )
            )
        }
        
        // AI Agent Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .scale(scale)
                .clickable(onClick = onClick),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color.Transparent)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        width = 2.dp,
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                Color(0xFF6366F1).copy(alpha = glowAlpha),
                                Color(0xFF8B5CF6).copy(alpha = glowAlpha),
                                Color(0xFF6366F1).copy(alpha = glowAlpha)
                            )
                        ),
                        shape = RoundedCornerShape(20.dp)
                    )
                    .background(
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                Color(0xFF1E1E2E).copy(alpha = 0.95f),
                                Color(0xFF1E2A3E).copy(alpha = 0.95f)
                            )
                        ),
                        shape = RoundedCornerShape(20.dp)
                    )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Robot Icon with Glow Effect
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(
                                brush = Brush.radialGradient(
                                    colors = listOf(
                                        Color(0xFF6366F1).copy(alpha = 0.3f),
                                        Color(0xFF8B5CF6).copy(alpha = 0.1f)
                                    )
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Outlined.SmartToy,
                            contentDescription = "AI Robot",
                            tint = Color(0xFF6366F1),
                            modifier = Modifier.size(36.dp)
                        )
                    }
                    
                    Spacer(Modifier.width(16.dp))
                    
                    // Text Content
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "AI Agent",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = Color.White
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Intelligent conversation assistant",
                            fontSize = 13.sp,
                            color = Color(0xFF94A3B8),
                            lineHeight = 16.sp
                        )
                    }
                    
                    // Arrow with Glow
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF6366F1).copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowForwardIos,
                            contentDescription = "Open",
                            tint = Color(0xFF6366F1),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

// Status Scheduler Card
@Composable
fun StatusSchedulerCard(onClick: () -> Unit) {
    val infiniteTransition = rememberInfiniteTransition(label = "status_glow")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow_alpha"
    )
    
    val scale by animateFloatAsState(
        targetValue = 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "scale"
    )
    
    Column(modifier = Modifier.fillMaxWidth()) {
        // Section Title - Status Scheduler
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF22C55E).copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Outlined.Schedule,
                    contentDescription = "Status Scheduler",
                    tint = Color(0xFF22C55E),
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Text(
                "Status Scheduler",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = Color.White
            )
            Spacer(Modifier.weight(1f))
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(1.dp)
                    .background(
                        brush = Brush.horizontalGradient(
                            colors = listOf(Color(0xFF22C55E).copy(alpha = 0.5f), Color.Transparent)
                        )
                    )
            )
        }
        
        // Status Scheduler Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .scale(scale)
                .clickable(onClick = onClick),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color.Transparent)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        width = 2.dp,
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                Color(0xFF22C55E).copy(alpha = glowAlpha),
                                Color(0xFF10B981).copy(alpha = glowAlpha),
                                Color(0xFF22C55E).copy(alpha = glowAlpha)
                            )
                        ),
                        shape = RoundedCornerShape(20.dp)
                    )
                    .background(
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                Color(0xFF1E2E1E).copy(alpha = 0.95f),
                                Color(0xFF1E3A2E).copy(alpha = 0.95f)
                            )
                        ),
                        shape = RoundedCornerShape(20.dp)
                    )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Schedule Icon with Glow Effect
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(
                                brush = Brush.radialGradient(
                                    colors = listOf(
                                        Color(0xFF22C55E).copy(alpha = 0.3f),
                                        Color(0xFF10B981).copy(alpha = 0.1f)
                                    )
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Outlined.Schedule,
                            contentDescription = "Status Scheduler",
                            tint = Color(0xFF22C55E),
                            modifier = Modifier.size(36.dp)
                        )
                    }
                    
                    Spacer(Modifier.width(16.dp))
                    
                    // Text Content
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Status Scheduler",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = Color.White
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Schedule WhatsApp status posts",
                            fontSize = 13.sp,
                            color = Color(0xFF94A3B8),
                            lineHeight = 16.sp
                        )
                    }
                    
                    // Arrow with Glow
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF22C55E).copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowForwardIos,
                            contentDescription = "Open",
                            tint = Color(0xFF22C55E),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ChatAIContent(innerPadding: PaddingValues, backgroundBrush: Brush) {
    Box(
        modifier = Modifier
            .padding(innerPadding)
            .fillMaxSize()
            .background(backgroundBrush),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(
                Icons.Outlined.Chat,
                contentDescription = "Chat AI",
                modifier = Modifier.size(80.dp),
                tint = Color(0xFF4F46E5)
            )
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                "Chat AI",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1E293B)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                "AI-powered chat assistant coming soon!",
                fontSize = 16.sp,
                color = Color(0xFF64748B),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun AutoRespondContent(innerPadding: PaddingValues, backgroundBrush: Brush) {
    Box(
        modifier = Modifier
            .padding(innerPadding)
            .fillMaxSize()
            .background(backgroundBrush),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(
                Icons.Outlined.AutoAwesome,
                contentDescription = "Auto Respond",
                modifier = Modifier.size(80.dp),
                tint = Color(0xFF4F46E5)
            )
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                "Auto Respond",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1E293B)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                "Automatic message responses coming soon!",
                fontSize = 16.sp,
                color = Color(0xFF64748B),
                textAlign = TextAlign.Center
            )
        }
    }
}



@Composable
fun AnimatedSendMessageCard() {
    var isVisible by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0.8f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy), label = ""
    )

    LaunchedEffect(Unit) {
        delay(300)
        isVisible = true
    }

    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .clickable { }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            Color(0xFF667eea),
                            Color(0xFF764ba2),
                            Color(0xFF6B73FF)
                        )
                    ),
                    shape = RoundedCornerShape(24.dp)
                )
        ) {
            Row(
                modifier = Modifier
                    .padding(20.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    TypewriterText(
                        "Send Message",
                        style = androidx.compose.ui.text.TextStyle(
                            color = Color.White,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    TypewriterText(
                        "Create campaign and send bulk messages",
                        style = androidx.compose.ui.text.TextStyle(
                            color = Color.White.copy(alpha = 0.9f),
                            fontSize = 14.sp
                        )
                    )
                }

                val infiniteTransition = rememberInfiniteTransition(label = "")
                val buttonScale by infiniteTransition.animateFloat(
                    initialValue = 1f,
                    targetValue = 1.1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(600, easing = LinearEasing),
                        repeatMode = RepeatMode.Reverse
                    ), label = ""
                )
                val context = LocalContext.current

                Button(
                    onClick = {
                        val intent = Intent(context, BulkMessage1Activity::class.java)
                        context.startActivity(intent)
                    },
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                    modifier = Modifier.scale(buttonScale)
                ) {
                    Text(
                        "Start",
                        color = Color(0xFF667eea),
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun AnimatedReportButtons() {
    var isVisible by remember { mutableStateOf(false) }
    val context = LocalContext.current // Get context for starting activity

    LaunchedEffect(Unit) {
        delay(600)
        isVisible = true
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Define button data and actions
        val reportItems = listOf(
            Triple(Icons.Outlined.Message, "Message Reports") {
                context.startActivity(Intent(context, ReportlistActivity::class.java))
            },
            Triple(Icons.Outlined.Campaign, "Campaign Status") {
                // Action to start CampaignstatusActivity
                context.startActivity(Intent(context, CampaignStatusActivity::class.java))
            }
        )

        reportItems.forEachIndexed { index, (icon, text, onClickAction) ->
            val offsetX by animateIntAsState(
                targetValue = if (isVisible) 0 else if (index == 0) -300 else 300,
                animationSpec = tween(800, delayMillis = index * 100), label = ""
            )

            AnimatedReportButton(
                icon = icon,
                text = text,
                modifier = Modifier
                    .weight(1f)
                    .offset(x = offsetX.dp),
                onClick = onClickAction // Pass the defined action
            )
        }
    }
}

@Composable
fun AnimatedReportButton(
    icon: ImageVector,
    text: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit // Accept an onClick lambda
) {
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = tween(100), label = ""
    )

    Button(
        onClick = onClick, // Use the passed onClick lambda
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Color.White),
        modifier = modifier
            .height(70.dp)
            .scale(scale),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                icon,
                contentDescription = text,
                tint = Color(0xFF4F46E5),
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text,
                color = Color(0xFF1E293B),
                fontWeight = FontWeight.Medium,
                fontSize = 13.sp
            )
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
                Icons.AutoMirrored.Filled.ArrowForwardIos,
                contentDescription = null,
                tint = Color(0xFF64748B),
                modifier = Modifier.size(14.dp)
            )
        }
    }
}


@Composable
fun AnimatedFeatureSection(title: String, items: List<FeatureItem>) {
    var isVisible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(900)
        isVisible = true
    }

    val slideOffset by animateIntAsState(
        targetValue = if (isVisible) 0 else 100,
        animationSpec = tween(800), label = ""
    )

    Column(
        modifier = Modifier.offset(y = slideOffset.dp)
    ) {
        Text(
            text = title,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF00D4FF),
            modifier = Modifier.padding(bottom = 16.dp)
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .border(1.dp, Color(0xFFD1D5DB), RoundedCornerShape(20.dp))
                .background(Color.White)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            items.chunked(3).forEachIndexed { rowIndex, rowItems ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.Top
                ) {
                    rowItems.forEachIndexed { itemIndex, item ->
                        Box(
                            modifier = Modifier.weight(1f),
                            contentAlignment = Alignment.TopCenter
                        ) {
                            AnimatedFeatureGridItem(
                                item = item,
                                delay = (rowIndex * 3 + itemIndex) * 100
                            )
                        }
                    }
                    repeat(3 - rowItems.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
fun AnimatedFeatureGridItem(item: FeatureItem, delay: Int = 0) {
    var isVisible by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ), label = ""
    )
    val rotation by animateFloatAsState(
        targetValue = if (isVisible) 0f else -180f,
        animationSpec = tween(600, delayMillis = delay), label = ""
    )

    LaunchedEffect(Unit) {
        delay(delay.toLong())
        isVisible = true
    }

    Box(
        contentAlignment = Alignment.TopCenter,
        modifier = Modifier
            .scale(scale)
            .clickable(onClick = item.onClick) // Updated to use onClick from item
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(
                        brush = Brush.linearGradient(item.gradient)
                    )
                    .border(
                        1.dp,
                        Color.White.copy(alpha = 0.3f),
                        RoundedCornerShape(20.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (item.lottieAsset != null) {
                    // Lottie Animation
                    val composition by rememberLottieComposition(
                        LottieCompositionSpec.Asset(item.lottieAsset)
                    )
                    val lottieProgress by animateLottieCompositionAsState(
                        composition = composition,
                        iterations = LottieConstants.IterateForever,
                        isPlaying = true
                    )
                    
                    if (composition != null) {
                        LottieAnimation(
                            composition = composition,
                            progress = { lottieProgress },
                            modifier = Modifier.size(68.dp)
                        )
                    } else if (item.icon != null) {
                        // Fallback to icon if Lottie fails to load
                        Icon(
                            item.icon,
                            contentDescription = item.title,
                            modifier = Modifier
                                .size(32.dp)
                                .rotate(rotation),
                            tint = Color.White
                        )
                    }
                } else if (item.icon != null) {
                    // Regular Icon
                    Icon(
                        item.icon,
                        contentDescription = item.title,
                        modifier = Modifier
                            .size(32.dp)
                            .rotate(rotation),
                        tint = Color(0xFF374151)
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = item.title,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                lineHeight = 16.sp,
                color = Color(0xFF374151),
                fontWeight = FontWeight.Medium
            )
        }

        if (item.hasBadge) {
            val badgeScale by animateFloatAsState(
                targetValue = if (isVisible) 1f else 0f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessHigh
                ), label = ""
            )

            Badge(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 8.dp, y = (-4).dp)
                    .scale(badgeScale),
                containerColor = Color(0xFFEF4444)
            ) {
                if (item.badgeText.isNotEmpty()) {
                    Text(
                        item.badgeText,
                        color = Color.White,
                        fontSize = 10.sp,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun AccessibilityDialog(
    onAgree: () -> Unit,
    onDisagree: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { /* Don't allow dismiss by clicking outside */ },
        icon = {
            Icon(
                imageVector = Icons.Outlined.Accessibility,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = Color(0xFF4F46E5)
            )
        },
        title = {
            Text(
                text = "Accessibility Permission",
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                textAlign = TextAlign.Center
            )
        },
        text = {
            Column(
                horizontalAlignment = Alignment.Start,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "This app uses Accessibility Service for automation purposes.",
                    fontSize = 15.sp,
                    color = Color(0xFF374151)
                )

                Text(
                    text = "Core Features:",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    color = Color(0xFF1F2937)
                )

                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    BulletPoint("Automate message sending")
                    BulletPoint("Read and interact with WhatsApp")
                    BulletPoint("Improve user experience")
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "⚠️ We respect your privacy and only use this permission for app functionality.",
                    fontSize = 13.sp,
                    color = Color(0xFF6B7280),
                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onAgree,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF10B981)
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(0.48f)
            ) {
                Text(
                    text = "I Agree",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDisagree,
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = Color(0xFFEF4444)
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(0.48f)
            ) {
                Text(
                    text = "Disagree",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }
        },
        shape = RoundedCornerShape(24.dp),
        containerColor = Color.White
    )
}

@Composable
fun BulletPoint(text: String) {
    Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "•",
            fontSize = 16.sp,
            color = Color(0xFF4F46E5),
            fontWeight = FontWeight.Bold
        )
        Text(
            text = text,
            fontSize = 14.sp,
            color = Color(0xFF374151)
        )
    }
}

@Composable
fun BadgedSupportIcon(
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val welcomeMessageManager = remember { WelcomeMessageManager(context) }
    var unreadCount by remember { mutableStateOf(0) }
    val scope = rememberCoroutineScope()
    
    // Check for unread messages periodically
    LaunchedEffect(Unit) {
        while (true) {
            val newCount = welcomeMessageManager.checkForNewMessages()
            android.util.Log.d("BadgedSupportIcon", "Badge count updated: $newCount")
            unreadCount = newCount
            delay(5000) // Check every 5 seconds
        }
    }
    
    Box {
        IconButton(
            onClick = {
                // Clear unread count when opening chat using coroutine scope
                scope.launch {
                    android.util.Log.d("BadgedSupportIcon", "Clearing unread count, was: $unreadCount")
                    welcomeMessageManager.clearUnreadCount()
                    unreadCount = 0 // Update local state immediately
                }
                onClick()
            }
        ) {
            Icon(
                Icons.Outlined.SupportAgent,
                contentDescription = "Support",
                tint = Color(0xFF10B981),
                modifier = Modifier.size(26.dp)
            )
        }
        
        // Badge for unread messages
        if (unreadCount > 0) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 4.dp, y = (-4).dp)
                    .size(20.dp)
                    .background(Color.Red, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (unreadCount > 99) "99+" else unreadCount.toString(),
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Preview(showBackground = true, device = "id:pixel_6")
@Composable
fun MainScreenPreview() {
    BulksendTestTheme {
        MainScreen()
    }
}


// Extract Permission Dialog
@Composable
fun ExtractPermissionDialog(
    onAgree: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Outlined.Accessibility,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = Color(0xFF4F46E5)
            )
        },
        title = {
            Text(
                text = "Accessibility Permission Required",
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                textAlign = TextAlign.Center
            )
        },
        text = {
            Column(
                horizontalAlignment = Alignment.Start,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "To extract contacts from chats, we need Accessibility permission.",
                    fontSize = 15.sp,
                    color = Color(0xFF374151)
                )

                Text(
                    text = "What we do:",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    color = Color(0xFF1F2937)
                )

                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    BulletPoint("Read contact numbers from chats")
                    BulletPoint("Extract unsaved contacts automatically")
                    BulletPoint("All data stays on your device")
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "⚠️ Enable accessibility service for this app in the next screen.",
                    fontSize = 13.sp,
                    color = Color(0xFF6B7280),
                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onAgree,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF10B981)
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(0.48f)
            ) {
                Text(
                    text = "Enable",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = Color(0xFFEF4444)
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(0.48f)
            ) {
                Text(
                    text = "Cancel",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }
        },
        shape = RoundedCornerShape(24.dp),
        containerColor = Color.White
    )
}

// Helper function to check if accessibility service is enabled
private fun isAccessibilityServiceEnabled(context: Context): Boolean {
    val serviceId = "${context.packageName}/${com.message.bulksend.bulksend.WhatsAppAutoSendService::class.java.name}"
    val enabledServices = android.provider.Settings.Secure.getString(
        context.contentResolver,
        android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
    )
    return enabledServices?.contains(serviceId, ignoreCase = true) ?: false
}
