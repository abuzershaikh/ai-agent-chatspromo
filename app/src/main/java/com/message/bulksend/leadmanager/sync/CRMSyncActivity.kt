package com.message.bulksend.leadmanager.sync

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import com.message.bulksend.ui.theme.BulksendTestTheme
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class CRMSyncActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        setContent {
            BulksendTestTheme {
                CRMSyncScreen(onBack = { finish() })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CRMSyncScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val syncManager = remember { CRMSyncManager(context) }
    
    var isLoading by remember { mutableStateOf(false) }
    var progressMessage by remember { mutableStateOf("") }
    var progressPercent by remember { mutableStateOf(0) }
    var syncStatus by remember { mutableStateOf(syncManager.getSyncStatus()) }
    var localCounts by remember { mutableStateOf<LocalDataCounts?>(null) }
    
    // Load local counts
    LaunchedEffect(Unit) {
        localCounts = syncManager.getLocalDataCounts()
    }
    
    val backgroundBrush = Brush.verticalGradient(
        colors = listOf(
            Color(0xFF0f0c29),
            Color(0xFF302b63),
            Color(0xFF24243e)
        )
    )

    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        "Chatspromo CRM Sync", 
                        color = Color(0xFF10B981),
                        fontWeight = FontWeight.Bold
                    ) 
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF1a1a2e)
                )
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
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // User Info Card
                UserInfoCard(syncStatus)
                
                // Local Data Summary
                localCounts?.let { counts ->
                    LocalDataCard(counts)
                }
                
                // Progress Card (shown during sync)
                if (isLoading) {
                    ProgressCard(progressMessage, progressPercent)
                }
                
                // Backup Button
                ActionButton(
                    icon = Icons.Outlined.CloudUpload,
                    title = "BACKUP TO CLOUD",
                    subtitle = "Upload all CRM data securely",
                    color = Color(0xFF10B981),
                    enabled = !isLoading && syncStatus.isLoggedIn,
                    onClick = {
                        scope.launch {
                            isLoading = true
                            progressMessage = "Starting backup..."
                            progressPercent = 0
                            
                            val result = syncManager.backupAll { msg, percent ->
                                progressMessage = msg
                                progressPercent = percent
                            }
                            
                            result.fold(
                                onSuccess = { stats ->
                                    Toast.makeText(
                                        context,
                                        "Backup complete! ${stats.backedUp} items backed up",
                                        Toast.LENGTH_LONG
                                    ).show()
                                    syncStatus = syncManager.getSyncStatus()
                                },
                                onFailure = { error ->
                                    Toast.makeText(
                                        context,
                                        "Backup failed: ${error.message}",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            )
                            
                            isLoading = false
                        }
                    }
                )
                
                // Restore Button
                ActionButton(
                    icon = Icons.Outlined.CloudDownload,
                    title = "RESTORE FROM CLOUD",
                    subtitle = "Download and restore CRM data",
                    color = Color(0xFF3B82F6),
                    enabled = !isLoading && syncStatus.isLoggedIn,
                    onClick = {
                        scope.launch {
                            isLoading = true
                            progressMessage = "Starting restore..."
                            progressPercent = 0
                            
                            val result = syncManager.restoreAll { msg, percent ->
                                progressMessage = msg
                                progressPercent = percent
                            }
                            
                            result.fold(
                                onSuccess = { stats ->
                                    Toast.makeText(
                                        context,
                                        "Restore complete! +${stats.inserted} ↻${stats.updated} ⊘${stats.skipped}",
                                        Toast.LENGTH_LONG
                                    ).show()
                                    syncStatus = syncManager.getSyncStatus()
                                    // Refresh local counts
                                    localCounts = syncManager.getLocalDataCounts()
                                },
                                onFailure = { error ->
                                    Toast.makeText(
                                        context,
                                        "Restore failed: ${error.message}",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            )
                            
                            isLoading = false
                        }
                    }
                )
                
                // Auto Sync Switch
                AutoSyncCard(
                    isEnabled = syncManager.isAutoSyncEnabled(),
                    isLoggedIn = syncStatus.isLoggedIn,
                    onToggle = { enabled ->
                        syncManager.setAutoSyncEnabled(enabled)
                        if (enabled) {
                            // Trigger initial backup when enabled
                            scope.launch {
                                isLoading = true
                                progressMessage = "Syncing data..."
                                progressPercent = 0
                                syncManager.backupAll { msg, percent ->
                                    progressMessage = msg
                                    progressPercent = percent
                                }
                                isLoading = false
                                syncStatus = syncManager.getSyncStatus()
                            }
                        }
                    }
                )
                
                // Not logged in warning
                if (!syncStatus.isLoggedIn) {
                    NotLoggedInCard()
                }
                
                // Info Card
                InfoCard()
            }
        }
    }
}

@Composable
fun UserInfoCard(status: CRMSyncStatus) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1a1a2e)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    Icons.Outlined.AccountCircle,
                    contentDescription = null,
                    tint = if (status.isLoggedIn) Color(0xFF10B981) else Color.Gray,
                    modifier = Modifier.size(40.dp)
                )
                Column {
                    Text(
                        status.userEmail ?: "Not logged in",
                        color = Color.White,
                        fontWeight = FontWeight.Medium,
                        fontSize = 16.sp
                    )
                    Text(
                        if (status.isLoggedIn) "Connected" else "Please login to sync",
                        color = if (status.isLoggedIn) Color(0xFF10B981) else Color.Gray,
                        fontSize = 12.sp
                    )
                }
            }
            
            Divider(color = Color.White.copy(alpha = 0.1f))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Last Backup", color = Color.Gray, fontSize = 12.sp)
                    Text(
                        status.lastBackupAt?.let { formatTime(it) } ?: "Never",
                        color = Color.White,
                        fontSize = 14.sp
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Last Restore", color = Color.Gray, fontSize = 12.sp)
                    Text(
                        status.lastRestoreAt?.let { formatTime(it) } ?: "Never",
                        color = Color.White,
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}

@Composable
fun LocalDataCard(counts: LocalDataCounts) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1a1a2e)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Outlined.Storage,
                    contentDescription = null,
                    tint = Color(0xFF8B5CF6),
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    "Local Data Summary",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }
            
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DataRow("Leads", counts.leads, Icons.Outlined.People)
                DataRow("Products", counts.products, Icons.Outlined.Inventory)
                DataRow("Follow-ups", counts.followUps, Icons.Outlined.Schedule)
                DataRow("Notes", counts.notes, Icons.Outlined.Note)
                DataRow("Custom Fields", counts.customFields, Icons.Outlined.TextFields)
                DataRow("Payments", counts.payments, Icons.Outlined.Payment)
                DataRow("Invoices", counts.invoices, Icons.Outlined.Receipt)
            }
        }
    }
}

@Composable
fun DataRow(label: String, count: Int, icon: ImageVector) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = Color.Gray,
                modifier = Modifier.size(18.dp)
            )
            Text(label, color = Color.Gray, fontSize = 14.sp)
        }
        Text(
            count.toString(),
            color = Color.White,
            fontWeight = FontWeight.Medium,
            fontSize = 14.sp
        )
    }
}


@Composable
fun AutoSyncCard(
    isEnabled: Boolean,
    isLoggedIn: Boolean,
    onToggle: (Boolean) -> Unit
) {
    var checked by remember { mutableStateOf(isEnabled) }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1a1a2e)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Outlined.Sync,
                    contentDescription = null,
                    tint = if (checked && isLoggedIn) Color(0xFF10B981) else Color.Gray,
                    modifier = Modifier.size(28.dp)
                )
                Column {
                    Text(
                        "Auto Sync",
                        color = Color.White,
                        fontWeight = FontWeight.Medium,
                        fontSize = 16.sp
                    )
                    Text(
                        if (checked && isLoggedIn) "Data syncs automatically" else "Enable to sync data in real-time",
                        color = Color.Gray,
                        fontSize = 12.sp
                    )
                }
            }
            
            Switch(
                checked = checked,
                onCheckedChange = { newValue ->
                    if (isLoggedIn) {
                        checked = newValue
                        onToggle(newValue)
                    }
                },
                enabled = isLoggedIn,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = Color(0xFF10B981),
                    uncheckedThumbColor = Color.Gray,
                    uncheckedTrackColor = Color.DarkGray
                )
            )
        }
    }
}

@Composable
fun ProgressCard(message: String, percent: Int) {
    val infiniteTransition = rememberInfiniteTransition(label = "sync_animation")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF10B981).copy(alpha = 0.2f)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Outlined.Sync,
                contentDescription = null,
                tint = Color(0xFF10B981),
                modifier = Modifier
                    .size(32.dp)
                    .rotate(rotation)
            )
            
            Text(
                message,
                color = Color.White,
                fontSize = 14.sp,
                textAlign = TextAlign.Center
            )
            
            LinearProgressIndicator(
                progress = { percent / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp),
                color = Color(0xFF10B981),
                trackColor = Color.White.copy(alpha = 0.2f)
            )
            
            Text(
                "$percent%",
                color = Color(0xFF10B981),
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
        }
    }
}

@Composable
fun ActionButton(
    icon: ImageVector,
    title: String,
    subtitle: String,
    color: Color,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (enabled) color.copy(alpha = 0.2f) else Color.Gray.copy(alpha = 0.1f)
        ),
        shape = RoundedCornerShape(16.dp),
        onClick = { if (enabled) onClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (enabled) color else Color.Gray,
                modifier = Modifier.size(40.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    color = if (enabled) Color.White else Color.Gray,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                Text(
                    subtitle,
                    color = if (enabled) Color.Gray else Color.Gray.copy(alpha = 0.5f),
                    fontSize = 12.sp
                )
            }
            Icon(
                Icons.Outlined.ChevronRight,
                contentDescription = null,
                tint = if (enabled) color else Color.Gray
            )
        }
    }
}

@Composable
fun NotLoggedInCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFEF4444).copy(alpha = 0.2f)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Outlined.Warning,
                contentDescription = null,
                tint = Color(0xFFEF4444),
                modifier = Modifier.size(24.dp)
            )
            Column {
                Text(
                    "Login Required",
                    color = Color(0xFFEF4444),
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                Text(
                    "Please login from the main app to enable cloud sync",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
fun InfoCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1a1a2e)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Outlined.Info,
                    contentDescription = null,
                    tint = Color(0xFF3B82F6),
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    "About Sync",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
            
            Text(
                "• Backup saves all your CRM data to cloud\n" +
                "• Restore downloads data from cloud\n" +
                "• Data is stored securely under your account\n" +
                "• Newer data takes priority during restore\n" +
                "• Custom fields, tags, and settings are included",
                color = Color.Gray,
                fontSize = 12.sp,
                lineHeight = 18.sp
            )
        }
    }
}

private fun formatTime(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    
    return when {
        diff < 60_000 -> "Just now"
        diff < 3600_000 -> "${diff / 60_000} min ago"
        diff < 86400_000 -> "${diff / 3600_000} hours ago"
        else -> {
            val sdf = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())
            sdf.format(Date(timestamp))
        }
    }
}
