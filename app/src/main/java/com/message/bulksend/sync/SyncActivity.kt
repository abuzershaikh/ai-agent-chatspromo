package com.message.bulksend.sync

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import com.message.bulksend.ui.theme.BulksendTestTheme
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class SyncActivity : ComponentActivity() {
    
    private lateinit var syncManager: SyncManager
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        syncManager = SyncManager(this)
        
        setContent {
            BulksendTestTheme {
                SyncScreen(syncManager)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncScreen(syncManager: SyncManager) {
    val scope = rememberCoroutineScope()
    var syncStatus by remember { mutableStateOf(syncManager.getSyncStatus()) }
    var isSyncing by remember { mutableStateOf(false) }
    var progressMessage by remember { mutableStateOf("") }
    var resultMessage by remember { mutableStateOf<String?>(null) }
    var isError by remember { mutableStateOf(false) }
    
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
                title = { Text("Cloud Sync", color = Color(0xFF00D4FF)) },
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
                // Status Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color.White.copy(alpha = 0.1f)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            "Sync Status",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF00D4FF)
                        )
                        
                        StatusRow(
                            icon = Icons.Default.AccountCircle,
                            label = "Account",
                            value = if (syncStatus.isLoggedIn) 
                                syncStatus.userEmail ?: "Logged in" 
                            else "Not logged in",
                            color = if (syncStatus.isLoggedIn) Color(0xFF4CAF50) else Color(0xFFFF5252)
                        )
                        
                        syncStatus.lastBackupAt?.let { timestamp ->
                            StatusRow(
                                icon = Icons.Default.CloudUpload,
                                label = "Last Backup",
                                value = formatTimestamp(timestamp),
                                color = Color(0xFF00D4FF)
                            )
                        }
                        
                        syncStatus.lastRestoreAt?.let { timestamp ->
                            StatusRow(
                                icon = Icons.Default.CloudDownload,
                                label = "Last Restore",
                                value = formatTimestamp(timestamp),
                                color = Color(0xFF00D4FF)
                            )
                        }
                    }
                }
                
                // Info Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFF2196F3).copy(alpha = 0.2f)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = null,
                            tint = Color(0xFF2196F3),
                            modifier = Modifier.size(24.dp)
                        )
                        Column {
                            Text(
                                "About Cloud Sync",
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontSize = 14.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "• Backup uploads your local changes to cloud\n" +
                                "• Restore downloads cloud data and merges with local\n" +
                                "• Full Sync does both - keeps everything in sync\n" +
                                "• Newer data always wins in conflicts",
                                color = Color.White.copy(alpha = 0.8f),
                                fontSize = 12.sp,
                                lineHeight = 18.sp
                            )
                        }
                    }
                }
                
                // Action Buttons
                if (!isSyncing) {
                    SyncButton(
                        text = "Backup to Cloud",
                        icon = Icons.Default.CloudUpload,
                        gradient = listOf(Color(0xFF667eea), Color(0xFF764ba2)),
                        enabled = syncStatus.isLoggedIn
                    ) {
                        scope.launch {
                            isSyncing = true
                            resultMessage = null
                            val result = syncManager.backupPending { msg ->
                                progressMessage = msg
                            }
                            isSyncing = false
                            isError = result.isFailure
                            resultMessage = if (result.isSuccess) {
                                "Backup completed successfully!"
                            } else {
                                "Error: ${result.exceptionOrNull()?.message}"
                            }
                            syncStatus = syncManager.getSyncStatus()
                        }
                    }
                    
                    SyncButton(
                        text = "Restore from Cloud",
                        icon = Icons.Default.CloudDownload,
                        gradient = listOf(Color(0xFF4facfe), Color(0xFF00f2fe)),
                        enabled = syncStatus.isLoggedIn
                    ) {
                        scope.launch {
                            isSyncing = true
                            resultMessage = null
                            val result = syncManager.restoreIfExists { msg ->
                                progressMessage = msg
                            }
                            isSyncing = false
                            isError = result.isFailure
                            resultMessage = if (result.isSuccess) {
                                "Restore completed successfully!"
                            } else {
                                "Error: ${result.exceptionOrNull()?.message}"
                            }
                            syncStatus = syncManager.getSyncStatus()
                        }
                    }
                    
                    SyncButton(
                        text = "Full Sync",
                        icon = Icons.Default.Sync,
                        gradient = listOf(Color(0xFFf093fb), Color(0xFFf5576c)),
                        enabled = syncStatus.isLoggedIn
                    ) {
                        scope.launch {
                            isSyncing = true
                            resultMessage = null
                            val result = syncManager.fullSync { msg ->
                                progressMessage = msg
                            }
                            isSyncing = false
                            isError = result.isFailure
                            resultMessage = if (result.isSuccess) {
                                "Full sync completed successfully!"
                            } else {
                                "Error: ${result.exceptionOrNull()?.message}"
                            }
                            syncStatus = syncManager.getSyncStatus()
                        }
                    }
                }
                
                // Progress
                if (isSyncing) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = Color.White.copy(alpha = 0.1f)
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            CircularProgressIndicator(
                                color = Color(0xFF00D4FF),
                                modifier = Modifier.size(48.dp)
                            )
                            Text(
                                progressMessage,
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
                
                // Result
                if (resultMessage != null) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isError) 
                                Color(0xFFFF5252).copy(alpha = 0.2f)
                            else 
                                Color(0xFF4CAF50).copy(alpha = 0.2f)
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                if (isError) Icons.Default.Error else Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = if (isError) Color(0xFFFF5252) else Color(0xFF4CAF50),
                                modifier = Modifier.size(24.dp)
                            )
                            Text(
                                resultMessage!!,
                                color = Color.White,
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StatusRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    color: Color
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(20.dp)
        )
        Column {
            Text(
                label,
                fontSize = 12.sp,
                color = Color.White.copy(alpha = 0.7f)
            )
            Text(
                value,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White
            )
        }
    }
}

@Composable
fun SyncButton(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    gradient: List<Color>,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Transparent,
            disabledContainerColor = Color.Gray.copy(alpha = 0.3f)
        ),
        contentPadding = PaddingValues(0.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    if (enabled) {
                        Brush.horizontalGradient(gradient)
                    } else {
                        Brush.horizontalGradient(listOf(Color.Gray, Color.Gray))
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text,
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

fun formatTimestamp(timestamp: Long): String {
    val sdf = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
    return sdf.format(Date(timestamp))
}
