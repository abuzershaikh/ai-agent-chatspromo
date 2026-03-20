package com.message.bulksend.autorespond.settings

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.window.DialogProperties
import com.message.bulksend.autorespond.AutoRespondManager
import com.message.bulksend.ui.theme.BulksendTestTheme

class AutoReplySettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        setContent {
            BulksendTestTheme {
                AutoReplySettingsScreen(
                    onBackPressed = { finish() },
                    onNavigateToHome = {
                        // Navigate to AutoRespondActivity with Home tab selected
                        val intent = Intent(this, com.message.bulksend.autorespond.AutoRespondActivity::class.java)
                        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                        intent.putExtra("navigate_to_autorespond_home", true)
                        startActivity(intent)
                        finish()
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutoReplySettingsScreen(
    onBackPressed: () -> Unit,
    onNavigateToHome: () -> Unit = {}
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val settingsManager = remember { AutoReplySettingsManager(context) }
    val autoRespondManager = remember { AutoRespondManager(context) }
    
    var keywordReplyEnabled by remember { mutableStateOf(settingsManager.isKeywordReplyEnabled()) }
    var spreadsheetReplyEnabled by remember { mutableStateOf(settingsManager.isSpreadsheetReplyEnabled()) }
    var aiReplyEnabled by remember { mutableStateOf(settingsManager.isAIReplyEnabled()) }
    var replyPriority by remember { mutableStateOf(settingsManager.getReplyPriority()) }
    
    // Delay Manager
    val delayManager = remember { ReplyDelayManager(context) }
    var selectedDelayType by remember { mutableStateOf(delayManager.getDelayType()) }
    
    // Permission dialog state
    var showPermissionDialog by remember { mutableStateOf(false) }
    
    val backgroundBrush = Brush.verticalGradient(
        colors = listOf(Color(0xFF0f0c29), Color(0xFF302b63), Color(0xFF24243e))
    )
    
    // Permission Required Dialog
    if (showPermissionDialog) {
        PermissionRequiredDialog(
            onDismiss = { showPermissionDialog = false },
            onGoToHome = {
                showPermissionDialog = false
                onNavigateToHome()
            }
        )
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text("Auto Reply Settings", color = Color(0xFF00D4FF), fontWeight = FontWeight.Bold)
                },
                navigationIcon = {
                    IconButton(onClick = onBackPressed) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color(0xFF00D4FF))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF1a1a2e))
            )
        },
        containerColor = Color.Transparent
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(backgroundBrush)
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ==================== WHATSAPP SELECTION (TOP) ====================
            Text(
                "WhatsApp Apps",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            
            Text(
                "Select which WhatsApp apps should receive auto-replies",
                fontSize = 13.sp,
                color = Color(0xFF94A3B8),
                modifier = Modifier.padding(bottom = 4.dp)
            )
            
            // WhatsApp Grid - 2 cards side by side
            WhatsAppGridSelection(settingsManager = settingsManager)
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Divider with label
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                HorizontalDivider(
                    modifier = Modifier.weight(1f),
                    thickness = 1.dp,
                    color = Color(0xFF2D3748)
                )
                Text(
                    "  Reply Methods  ",
                    fontSize = 12.sp,
                    color = Color(0xFF64748B)
                )
                HorizontalDivider(
                    modifier = Modifier.weight(1f),
                    thickness = 1.dp,
                    color = Color(0xFF2D3748)
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // ==================== REPLY SETTINGS ====================
            Text(
                "Reply System",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            
            Text(
                "Configure how your auto-reply system works",
                fontSize = 13.sp,
                color = Color(0xFF94A3B8),
                modifier = Modifier.padding(bottom = 4.dp)
            )
            
            // Helper function to check permissions before enabling
            fun checkPermissionsAndToggle(
                currentValue: Boolean,
                onEnable: () -> Unit
            ) {
                if (!currentValue) {
                    val hasNotificationPermission = autoRespondManager.isNotificationPermissionGranted()
                    val isAutoRespondEnabled = autoRespondManager.isAutoRespondEnabled()
                    
                    if (!hasNotificationPermission || !isAutoRespondEnabled) {
                        showPermissionDialog = true
                    } else {
                        onEnable()
                    }
                } else {
                    onEnable()
                }
            }
            
            // Keyword Reply Setting
            SettingCard(
                icon = Icons.Default.Key,
                title = "Keyword Reply",
                description = "Reply based on matching keywords",
                enabled = keywordReplyEnabled,
                onToggle = { newValue ->
                    checkPermissionsAndToggle(keywordReplyEnabled) {
                        keywordReplyEnabled = newValue
                        settingsManager.setKeywordReplyEnabled(newValue)
                    }
                },
                color = Color(0xFF00D4FF)
            )
            
            // Spreadsheet Reply Setting
            SettingCard(
                icon = Icons.Default.TableChart,
                title = "Spreadsheet Reply",
                description = "Reply based on spreadsheet data",
                enabled = spreadsheetReplyEnabled,
                onToggle = { newValue ->
                    checkPermissionsAndToggle(spreadsheetReplyEnabled) {
                        spreadsheetReplyEnabled = newValue
                        settingsManager.setSpreadsheetReplyEnabled(newValue)
                    }
                },
                color = Color(0xFF10B981)
            )
            
            // AI Reply Setting
            SettingCard(
                icon = Icons.Default.AutoAwesome,
                title = "AI Reply",
                description = "Use AI to generate smart replies",
                enabled = aiReplyEnabled,
                onToggle = { newValue ->
                    checkPermissionsAndToggle(aiReplyEnabled) {
                        aiReplyEnabled = newValue
                        settingsManager.setAIReplyEnabled(newValue)
                    }
                },
                color = Color(0xFF8B5CF6)
            )
            
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 8.dp),
                thickness = 1.dp,
                color = Color(0xFF2D3748)
            )
            
            // Priority Setting
            Text(
                "Reply Priority",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.padding(top = 8.dp)
            )
            
            Text(
                "Choose which system takes priority when both are enabled",
                fontSize = 14.sp,
                color = Color(0xFF94A3B8),
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            PriorityCard(
                title = "Keyword First",
                description = "Try keyword, then spreadsheet, then AI if no match",
                isSelected = replyPriority == ReplyPriority.KEYWORD_FIRST,
                onClick = {
                    replyPriority = ReplyPriority.KEYWORD_FIRST
                    settingsManager.setReplyPriority(ReplyPriority.KEYWORD_FIRST)
                },
                icon = Icons.Default.Key
            )
            
            PriorityCard(
                title = "Spreadsheet First",
                description = "Try spreadsheet, then keyword, then AI if no match",
                isSelected = replyPriority == ReplyPriority.SPREADSHEET_FIRST,
                onClick = {
                    replyPriority = ReplyPriority.SPREADSHEET_FIRST
                    settingsManager.setReplyPriority(ReplyPriority.SPREADSHEET_FIRST)
                },
                icon = Icons.Default.TableChart
            )
            
            PriorityCard(
                title = "AI Only",
                description = "Always use AI, ignore keyword and spreadsheet",
                isSelected = replyPriority == ReplyPriority.AI_ONLY,
                onClick = {
                    replyPriority = ReplyPriority.AI_ONLY
                    settingsManager.setReplyPriority(ReplyPriority.AI_ONLY)
                },
                icon = Icons.Default.AutoAwesome
            )
            
            PriorityCard(
                title = "Keyword Only",
                description = "Only use keyword replies",
                isSelected = replyPriority == ReplyPriority.KEYWORD_ONLY,
                onClick = {
                    replyPriority = ReplyPriority.KEYWORD_ONLY
                    settingsManager.setReplyPriority(ReplyPriority.KEYWORD_ONLY)
                },
                icon = Icons.Default.Key
            )
            
            PriorityCard(
                title = "Spreadsheet Only",
                description = "Only use spreadsheet replies",
                isSelected = replyPriority == ReplyPriority.SPREADSHEET_ONLY,
                onClick = {
                    replyPriority = ReplyPriority.SPREADSHEET_ONLY
                    settingsManager.setReplyPriority(ReplyPriority.SPREADSHEET_ONLY)
                },
                icon = Icons.Default.TableChart
            )
            
            // Info Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1a1a2e).copy(alpha = 0.5f)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = null,
                        tint = Color(0xFF00D4FF),
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            "How it works",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "• Keyword First: Keyword → Spreadsheet → AI\n" +
                            "• Spreadsheet First: Spreadsheet → Keyword → AI\n" +
                            "• AI Only: Always uses AI\n" +
                            "• Keyword Only: Only keywords\n" +
                            "• Spreadsheet Only: Only spreadsheet",
                            fontSize = 12.sp,
                            color = Color(0xFF94A3B8),
                            lineHeight = 18.sp
                        )
                    }
                }
            }
            
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 8.dp),
                thickness = 1.dp,
                color = Color(0xFF2D3748)
            )
            
            // ==================== REPLY DELAY SETTINGS ====================
            Text(
                "Reply Delay",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.padding(top = 8.dp)
            )
            
            Text(
                "Add delay before sending auto-reply (more natural)",
                fontSize = 13.sp,
                color = Color(0xFF94A3B8),
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            // Delay Options
            ReplyDelaySection(
                selectedDelayType = selectedDelayType,
                onDelayTypeSelected = { delayType ->
                    selectedDelayType = delayType
                    delayManager.setDelayType(delayType)
                }
            )
        }
    }
}

/**
 * Reply Delay Section with all delay options
 */
@Composable
fun ReplyDelaySection(
    selectedDelayType: ReplyDelayType,
    onDelayTypeSelected: (ReplyDelayType) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        DelayOptionCard(
            title = "No Delay",
            description = "Reply instantly",
            isSelected = selectedDelayType == ReplyDelayType.NO_DELAY,
            onClick = { onDelayTypeSelected(ReplyDelayType.NO_DELAY) },
            icon = Icons.Default.FlashOn
        )
        
        DelayOptionCard(
            title = "5 Seconds",
            description = "Wait 5 seconds before reply",
            isSelected = selectedDelayType == ReplyDelayType.DELAY_5_SEC,
            onClick = { onDelayTypeSelected(ReplyDelayType.DELAY_5_SEC) },
            icon = Icons.Default.Timer
        )
        
        DelayOptionCard(
            title = "10 Seconds",
            description = "Wait 10 seconds before reply",
            isSelected = selectedDelayType == ReplyDelayType.DELAY_10_SEC,
            onClick = { onDelayTypeSelected(ReplyDelayType.DELAY_10_SEC) },
            icon = Icons.Default.Timer
        )
        
        DelayOptionCard(
            title = "15 Seconds",
            description = "Wait 15 seconds before reply",
            isSelected = selectedDelayType == ReplyDelayType.DELAY_15_SEC,
            onClick = { onDelayTypeSelected(ReplyDelayType.DELAY_15_SEC) },
            icon = Icons.Default.Timer
        )
        
        DelayOptionCard(
            title = "Random (5-15 sec)",
            description = "Random delay between 5-15 seconds",
            isSelected = selectedDelayType == ReplyDelayType.RANDOM_5_TO_15,
            onClick = { onDelayTypeSelected(ReplyDelayType.RANDOM_5_TO_15) },
            icon = Icons.Default.Shuffle
        )
        
        // Info about delay
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1a1a2e).copy(alpha = 0.5f)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Lightbulb,
                    contentDescription = null,
                    tint = Color(0xFFF59E0B),
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    "Tip: Adding delay makes replies look more natural and human-like",
                    fontSize = 12.sp,
                    color = Color(0xFF94A3B8)
                )
            }
        }
    }
}

/**
 * Individual Delay Option Card
 */
@Composable
fun DelayOptionCard(
    title: String,
    description: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    val selectedColor = Color(0xFFF59E0B) // Orange for delay
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) selectedColor.copy(alpha = 0.2f) else Color(0xFF1a1a2e)
        ),
        shape = RoundedCornerShape(12.dp),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(
                selected = isSelected,
                onClick = onClick,
                colors = RadioButtonDefaults.colors(
                    selectedColor = selectedColor,
                    unselectedColor = Color(0xFF64748B)
                )
            )
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                icon,
                contentDescription = null,
                tint = if (isSelected) selectedColor else Color(0xFF64748B),
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isSelected) selectedColor else Color.White
                )
                Text(
                    description,
                    fontSize = 12.sp,
                    color = Color(0xFF94A3B8)
                )
            }
        }
    }
}

@Composable
fun SettingCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
    color: Color
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1a1a2e)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(color.copy(alpha = 0.2f), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = color,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        title,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        description,
                        fontSize = 13.sp,
                        color = Color(0xFF94A3B8)
                    )
                }
            }
            Switch(
                checked = enabled,
                onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = color
                )
            )
        }
    }
}

@Composable
fun PriorityCard(
    title: String,
    description: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) Color(0xFF00D4FF).copy(alpha = 0.2f) else Color(0xFF1a1a2e)
        ),
        shape = RoundedCornerShape(12.dp),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(
                selected = isSelected,
                onClick = onClick,
                colors = RadioButtonDefaults.colors(
                    selectedColor = Color(0xFF00D4FF),
                    unselectedColor = Color(0xFF64748B)
                )
            )
            Spacer(modifier = Modifier.width(12.dp))
            Icon(
                icon,
                contentDescription = null,
                tint = if (isSelected) Color(0xFF00D4FF) else Color(0xFF64748B),
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isSelected) Color(0xFF00D4FF) else Color.White
                )
                Text(
                    description,
                    fontSize = 12.sp,
                    color = Color(0xFF94A3B8)
                )
            }
        }
    }
}

/**
 * WhatsApp Grid Selection - 3 cards (WhatsApp, WhatsApp Business, Instagram)
 */
@Composable
fun WhatsAppGridSelection(settingsManager: AutoReplySettingsManager) {
    var whatsAppEnabled by remember { mutableStateOf(settingsManager.isWhatsAppEnabled()) }
    var whatsAppBusinessEnabled by remember { mutableStateOf(settingsManager.isWhatsAppBusinessEnabled()) }
    var instagramEnabled by remember { mutableStateOf(settingsManager.isInstagramEnabled()) }
    var showInfoDialog by remember { mutableStateOf(false) }
    
    Column {
        // First Row - WhatsApp and WhatsApp Business
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // WhatsApp Personal Card
            WhatsAppGridCard(
                modifier = Modifier.weight(1f),
                title = "WhatsApp",
                subtitle = "Personal",
                iconText = "W",
                color = Color(0xFF25D366),
                enabled = whatsAppEnabled,
                onToggle = { newValue ->
                    whatsAppEnabled = newValue
                    settingsManager.setWhatsAppEnabled(newValue)
                }
            )
            
            // WhatsApp Business Card
            WhatsAppGridCard(
                modifier = Modifier.weight(1f),
                title = "WA Business",
                subtitle = "Business",
                iconText = "WB",
                color = Color(0xFF128C7E),
                enabled = whatsAppBusinessEnabled,
                onToggle = { newValue ->
                    whatsAppBusinessEnabled = newValue
                    settingsManager.setWhatsAppBusinessEnabled(newValue)
                }
            )
        }
        
        Spacer(modifier = Modifier.height(12.dp))
        
        // Divider
        HorizontalDivider(
            thickness = 1.dp,
            color = Color(0xFF2D3748),
            modifier = Modifier.padding(vertical = 8.dp)
        )
        
        // Instagram Section Title
        Text(
            "Instagram",
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFF94A3B8),
            modifier = Modifier.padding(vertical = 8.dp)
        )
        
        // Instagram Section - Beautiful Full Width Card
        InstagramCard(
            modifier = Modifier.fillMaxWidth(),
            enabled = instagramEnabled,
            onToggle = { newValue ->
                instagramEnabled = newValue
                settingsManager.setInstagramEnabled(newValue)
            }
        )
        
        Spacer(modifier = Modifier.height(10.dp))
        
        // Status bar with info button
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val enabledApps = listOfNotNull(
                if (whatsAppEnabled) "WhatsApp" else null,
                if (whatsAppBusinessEnabled) "Business" else null,
                if (instagramEnabled) "Instagram" else null
            )
            
            val statusText = when {
                enabledApps.size == 3 -> "All apps enabled"
                enabledApps.size == 2 -> "${enabledApps.joinToString(" & ")} enabled"
                enabledApps.size == 1 -> "Only ${enabledApps.first()} enabled"
                else -> "No apps enabled"
            }
            
            val statusColor = when {
                enabledApps.size >= 2 -> Color(0xFF10B981)
                enabledApps.size == 1 -> Color(0xFFF59E0B)
                else -> Color(0xFFEF4444)
            }
            
            Row(
                modifier = Modifier
                    .weight(1f)
                    .background(statusColor.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    when {
                        enabledApps.size >= 2 -> Icons.Default.CheckCircle
                        enabledApps.size == 1 -> Icons.Default.Info
                        else -> Icons.Default.Warning
                    },
                    contentDescription = null,
                    tint = statusColor,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    statusText,
                    fontSize = 13.sp,
                    color = statusColor
                )
            }
            
            Spacer(modifier = Modifier.width(8.dp))
            
            // Info button
            IconButton(
                onClick = { showInfoDialog = true },
                modifier = Modifier
                    .size(36.dp)
                    .background(Color(0xFF1a1a2e), RoundedCornerShape(8.dp))
            ) {
                Icon(
                    Icons.Default.Info,
                    contentDescription = "Info",
                    tint = Color(0xFF64748B),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
    
    // Info Dialog
    if (showInfoDialog) {
        WhatsAppInfoDialog(onDismiss = { showInfoDialog = false })
    }
}

/**
 * Individual WhatsApp Grid Card
 */
@Composable
fun WhatsAppGridCard(
    modifier: Modifier = Modifier,
    title: String,
    subtitle: String,
    iconText: String,
    color: Color,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = if (enabled) color.copy(alpha = 0.15f) else Color(0xFF1a1a2e)
        ),
        shape = RoundedCornerShape(12.dp),
        border = if (enabled) androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.5f)) else null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Icon
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(color, RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    iconText,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = if (iconText.length > 1) 14.sp else 18.sp
                )
            }
            
            Spacer(modifier = Modifier.height(10.dp))
            
            // Title
            Text(
                title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = if (enabled) color else Color.White
            )
            
            // Subtitle
            Text(
                subtitle,
                fontSize = 11.sp,
                color = Color(0xFF94A3B8)
            )
            
            Spacer(modifier = Modifier.height(10.dp))
            
            // Switch
            Switch(
                checked = enabled,
                onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = color,
                    uncheckedThumbColor = Color(0xFF64748B),
                    uncheckedTrackColor = Color(0xFF2D3748)
                ),
                modifier = Modifier.height(24.dp)
            )
        }
    }
}

/**
 * Info dialog explaining WhatsApp selection
 */
@Composable
fun WhatsAppInfoDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1a1a2e),
        shape = RoundedCornerShape(20.dp),
        icon = {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .background(Color(0xFF25D366).copy(alpha = 0.2f), RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Chat,
                    contentDescription = null,
                    tint = Color(0xFF25D366),
                    modifier = Modifier.size(36.dp)
                )
            }
        },
        title = {
            Text(
                "WhatsApp Selection",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Control which WhatsApp apps receive auto-replies:",
                    fontSize = 14.sp,
                    color = Color(0xFF94A3B8)
                )
                
                // WhatsApp explanation
                Row(verticalAlignment = Alignment.Top) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .background(Color(0xFF25D366), RoundedCornerShape(6.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("W", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text("WhatsApp", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Text(
                            "Personal WhatsApp app (com.whatsapp)",
                            fontSize = 12.sp,
                            color = Color(0xFF94A3B8)
                        )
                    }
                }
                
                // WhatsApp Business explanation
                Row(verticalAlignment = Alignment.Top) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .background(Color(0xFF128C7E), RoundedCornerShape(6.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("WB", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 8.sp)
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text("WhatsApp Business", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Text(
                            "Business WhatsApp app (com.whatsapp.w4b)",
                            fontSize = 12.sp,
                            color = Color(0xFF94A3B8)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Usage scenarios
                Text(
                    "Usage Scenarios:",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                
                Text(
                    "• Both ON: Auto-reply to messages from both apps\n" +
                    "• WhatsApp ON only: Reply only to personal WhatsApp\n" +
                    "• Business ON only: Reply only to WhatsApp Business\n" +
                    "• Both OFF: No auto-replies to any WhatsApp",
                    fontSize = 12.sp,
                    color = Color(0xFF94A3B8),
                    lineHeight = 18.sp
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF25D366)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Got it", fontWeight = FontWeight.Bold)
            }
        }
    )
}

/**
 * Dialog shown when user tries to enable settings without required permissions
 */
@Composable
fun PermissionRequiredDialog(
    onDismiss: () -> Unit,
    onGoToHome: () -> Unit
) {
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
                "Permission Required",
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
                    "To enable auto-reply settings, you need to:",
                    fontSize = 14.sp,
                    color = Color(0xFF94A3B8)
                )
                
                // Step 1
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .background(Color(0xFF00D4FF).copy(alpha = 0.2f), RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("1", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF00D4FF))
                    }
                    Text(
                        "Allow Notification Access Permission",
                        fontSize = 13.sp,
                        color = Color.White
                    )
                }
                
                // Step 2
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .background(Color(0xFF00D4FF).copy(alpha = 0.2f), RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("2", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF00D4FF))
                    }
                    Text(
                        "Enable Auto Respond Service",
                        fontSize = 13.sp,
                        color = Color.White
                    )
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    "You will be redirected to Auto Respond Home screen where you can enable these settings.",
                    fontSize = 12.sp,
                    color = Color(0xFF64748B)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onGoToHome,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00D4FF)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    Icons.Default.ArrowForward,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Go to Home", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Color(0xFF94A3B8))
            }
        },
        properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = true)
    )
}

/**
 * Beautiful Instagram Card Component - Horizontal Layout
 */
@Composable
fun InstagramCard(
    modifier: Modifier = Modifier,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit
) {
    val instagramColor = Color(0xFFE4405F)
    
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = if (enabled) instagramColor.copy(alpha = 0.12f) else Color(0xFF1a1a2e)
        ),
        shape = RoundedCornerShape(16.dp),
        border = if (enabled) androidx.compose.foundation.BorderStroke(1.5.dp, instagramColor.copy(alpha = 0.6f)) else null
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left side - Icon and Text
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.weight(1f)
            ) {
                // Instagram Icon with Gradient Background
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(
                            brush = Brush.linearGradient(
                                colors = listOf(
                                    Color(0xFFE4405F),
                                    Color(0xFFF56040),
                                    Color(0xFFFCAF45)
                                )
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "IG",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                }
                
                // Title and Subtitle
                Column(
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        "Instagram",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (enabled) instagramColor else Color.White
                    )
                    Text(
                        "Direct Messages",
                        fontSize = 11.sp,
                        color = Color(0xFF94A3B8),
                        maxLines = 1
                    )
                }
            }
            
            // Right side - Switch with proper spacing
            Box(
                modifier = Modifier.padding(start = 12.dp)
            ) {
                Switch(
                    checked = enabled,
                    onCheckedChange = onToggle,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = instagramColor,
                        uncheckedThumbColor = Color(0xFF64748B),
                        uncheckedTrackColor = Color(0xFF2D3748)
                    )
                )
            }
        }
    }
}
