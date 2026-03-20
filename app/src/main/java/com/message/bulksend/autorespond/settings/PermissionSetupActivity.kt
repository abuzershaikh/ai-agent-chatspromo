package com.message.bulksend.autorespond.settings

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import com.message.bulksend.ui.theme.BulksendTestTheme

class PermissionSetupActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        setContent {
            BulksendTestTheme {
                PermissionSetupScreen(
                    onBackPressed = { finish() }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionSetupScreen(onBackPressed: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val permissionHelper = remember { AutoReplyPermissionHelper(context) }
    
    var permissionStatus by remember { mutableStateOf(permissionHelper.getAllPermissionStatuses()) }
    val manufacturerInstructions = remember { permissionHelper.getManufacturerInstructions() }
    
    // Refresh permission status when screen becomes visible
    LaunchedEffect(Unit) {
        permissionStatus = permissionHelper.getAllPermissionStatuses()
    }
    
    val backgroundBrush = Brush.verticalGradient(
        colors = listOf(Color(0xFF0f0c29), Color(0xFF302b63), Color(0xFF24243e))
    )
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text("Setup Auto Reply", color = Color(0xFF00D4FF), fontWeight = FontWeight.Bold)
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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(backgroundBrush)
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1a1a2e).copy(alpha = 0.8f)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                Icons.Default.Settings,
                                contentDescription = null,
                                tint = Color(0xFF00D4FF),
                                modifier = Modifier.size(32.dp)
                            )
                            Text(
                                "Required Settings",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                        
                        Text(
                            "For auto-reply to work properly, please complete all the settings below:",
                            fontSize = 14.sp,
                            color = Color(0xFF94A3B8),
                            lineHeight = 20.sp
                        )
                    }
                }
            }
            
            // Permission Cards
            item {
                PermissionCard(
                    title = "1. Notification Access",
                    description = "Allow app to read WhatsApp notifications",
                    isGranted = permissionStatus.notificationAccess,
                    onClick = { 
                        permissionHelper.openNotificationAccessSettings()
                    },
                    icon = Icons.Default.Notifications
                )
            }
            
            item {
                PermissionCard(
                    title = "2. Battery Optimization",
                    description = "Disable battery optimization to prevent app from being killed",
                    isGranted = permissionStatus.batteryOptimization,
                    onClick = { 
                        permissionHelper.openBatteryOptimizationSettings()
                    },
                    icon = Icons.Default.BatteryFull
                )
            }
            
            item {
                PermissionCard(
                    title = "3. Auto-Start Permission",
                    description = "Allow app to start automatically (${manufacturerInstructions.manufacturer})",
                    isGranted = false, // Always show as not granted since it's hard to detect
                    onClick = { permissionHelper.openAutoStartSettings() },
                    icon = Icons.Default.PlayArrow
                )
            }
            
            // Advanced Settings Card
            item {
                AdvancedSettingsCard(permissionHelper = permissionHelper)
            }
            
            // Manufacturer Specific Instructions
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1a1a2e).copy(alpha = 0.8f)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                Icons.Default.PhoneAndroid,
                                contentDescription = null,
                                tint = Color(0xFFF59E0B),
                                modifier = Modifier.size(24.dp)
                            )
                            Text(
                                "${manufacturerInstructions.manufacturer} Instructions",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                        
                        Text(
                            "Follow these steps for your device:",
                            fontSize = 14.sp,
                            color = Color(0xFF94A3B8)
                        )
                    }
                }
            }
            
            // Instructions List
            items(manufacturerInstructions.instructions) { instruction ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF2a2a3e).copy(alpha = 0.6f)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = Color(0xFF10B981),
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            instruction,
                            fontSize = 14.sp,
                            color = Color(0xFFE2E8F0),
                            lineHeight = 20.sp,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
            
            // Status Summary
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (permissionStatus.allGranted) 
                            Color(0xFF10B981).copy(alpha = 0.2f) 
                        else 
                            Color(0xFFEF4444).copy(alpha = 0.2f)
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                if (permissionStatus.allGranted) Icons.Default.CheckCircle else Icons.Default.Warning,
                                contentDescription = null,
                                tint = if (permissionStatus.allGranted) Color(0xFF10B981) else Color(0xFFEF4444),
                                modifier = Modifier.size(24.dp)
                            )
                            Text(
                                if (permissionStatus.allGranted) "Setup Complete!" else "Setup Required",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                        
                        Text(
                            if (permissionStatus.allGranted) 
                                "All permissions are granted. Auto-reply should work properly now!" 
                            else 
                                "Please complete the remaining settings for auto-reply to work reliably.",
                            fontSize = 14.sp,
                            color = Color(0xFF94A3B8),
                            lineHeight = 20.sp
                        )
                    }
                }
            }
            
            // Refresh Button
            item {
                Button(
                    onClick = { 
                        permissionStatus = permissionHelper.getAllPermissionStatuses()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00D4FF)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Refresh Status", fontWeight = FontWeight.Bold)
                }
            }
            
            // Bottom spacing
            item {
                Spacer(modifier = Modifier.height(50.dp))
            }
        }
    }
}

@Composable
fun PermissionCard(
    title: String,
    description: String,
    isGranted: Boolean,
    onClick: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = if (isGranted) 
                Color(0xFF10B981).copy(alpha = 0.15f) 
            else 
                Color(0xFF1a1a2e).copy(alpha = 0.8f)
        ),
        shape = RoundedCornerShape(16.dp),
        border = if (isGranted) 
            androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.5f)) 
        else null
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Icon
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(
                        if (isGranted) Color(0xFF10B981).copy(alpha = 0.2f) else Color(0xFF64748B).copy(alpha = 0.2f),
                        RoundedCornerShape(12.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = if (isGranted) Color(0xFF10B981) else Color(0xFF64748B),
                    modifier = Modifier.size(24.dp)
                )
            }
            
            // Content
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    description,
                    fontSize = 13.sp,
                    color = Color(0xFF94A3B8),
                    lineHeight = 18.sp
                )
            }
            
            // Status
            Icon(
                if (isGranted) Icons.Default.CheckCircle else Icons.Default.ArrowForward,
                contentDescription = null,
                tint = if (isGranted) Color(0xFF10B981) else Color(0xFF64748B),
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
fun AdvancedSettingsCard(permissionHelper: AutoReplyPermissionHelper) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var isExpanded by remember { mutableStateOf(false) }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1a1a2e).copy(alpha = 0.8f)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header with expand/collapse
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        Icons.Default.Tune,
                        contentDescription = null,
                        tint = Color(0xFFEF4444),
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        "Advanced Settings",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
                
                Icon(
                    if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = Color(0xFF64748B),
                    modifier = Modifier.size(24.dp)
                )
            }
            
            Text(
                "Critical settings for reliable auto-reply functionality",
                fontSize = 14.sp,
                color = Color(0xFF94A3B8)
            )
            
            // Expandable content
            if (isExpanded) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    HorizontalDivider(
                        thickness = 1.dp,
                        color = Color(0xFF2D3748)
                    )
                    
                    // Battery Optimization Whitelist
                    AdvancedSettingItem(
                        title = "Battery Optimization Whitelist",
                        description = "Add app to battery optimization whitelist to prevent Android from killing the service",
                        buttonText = "Whitelist App",
                        onClick = { 
                            android.widget.Toast.makeText(context, "Opening settings... Go to Battery → Unrestricted", android.widget.Toast.LENGTH_LONG).show()
                            permissionHelper.openBatteryWhitelistSettings() 
                        },
                        icon = Icons.Default.BatteryAlert,
                        iconColor = Color(0xFFEF4444)
                    )
                    
                    // Pause App Activity Setting
                    AdvancedSettingItem(
                        title = "Disable App Pause",
                        description = "Turn OFF 'Pause app activity if unused' to keep auto-reply active",
                        buttonText = "App Settings",
                        onClick = { permissionHelper.openAppSettings() },
                        icon = Icons.Default.PauseCircle,
                        iconColor = Color(0xFFF59E0B)
                    )
                    
                    // Background Activity
                    AdvancedSettingItem(
                        title = "Allow Background Activity",
                        description = "Enable 'Allow background activity' for continuous operation",
                        buttonText = "Background Settings",
                        onClick = { permissionHelper.openAppSettings() },
                        icon = Icons.Default.PlayCircle,
                        iconColor = Color(0xFF10B981)
                    )
                    
                    // Unrestricted Battery Usage
                    AdvancedSettingItem(
                        title = "Unrestricted Battery Usage",
                        description = "Set battery usage to 'Unrestricted' or 'No restrictions'",
                        buttonText = "Battery Settings",
                        onClick = { 
                            try {
                                val intent = android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                                intent.data = android.net.Uri.parse("package:${context.packageName}")
                                intent.flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                permissionHelper.openAppSettings()
                            }
                        },
                        icon = Icons.Default.BatteryFull,
                        iconColor = Color(0xFF8B5CF6)
                    )
                    
                    // Warning Card
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFEF4444).copy(alpha = 0.1f)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                Icons.Default.Warning,
                                contentDescription = null,
                                tint = Color(0xFFEF4444),
                                modifier = Modifier.size(20.dp)
                            )
                            Column {
                                Text(
                                    "Important",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFEF4444)
                                )
                                Text(
                                    "These settings are crucial for auto-reply to work reliably. Without them, Android may kill the app in background.",
                                    fontSize = 12.sp,
                                    color = Color(0xFF94A3B8),
                                    lineHeight = 16.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AdvancedSettingItem(
    title: String,
    description: String,
    buttonText: String,
    onClick: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconColor: Color
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF2a2a3e).copy(alpha = 0.6f)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Top row with icon and title
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Icon
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(iconColor.copy(alpha = 0.2f), RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = iconColor,
                        modifier = Modifier.size(20.dp)
                    )
                }
                
                // Title
                Text(
                    title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.weight(1f)
                )
            }
            
            // Description
            Text(
                description,
                fontSize = 12.sp,
                color = Color(0xFF94A3B8),
                lineHeight = 16.sp,
                modifier = Modifier.fillMaxWidth()
            )
            
            // Button
            Button(
                onClick = onClick,
                colors = ButtonDefaults.buttonColors(containerColor = iconColor),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    buttonText,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }
    }
}