package com.message.bulksend.autorespond.backup

import android.os.Bundle
import android.widget.Toast
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.message.bulksend.ui.theme.BulksendTestTheme
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class AutoRespondBackupActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            BulksendTestTheme {
                AutoRespondBackupScreen(
                    onBackPressed = { finish() }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutoRespondBackupScreen(onBackPressed: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val backupManager = remember { AutoRespondBackupManager(context) }
    val scope = rememberCoroutineScope()

    var isLoading by remember { mutableStateOf(false) }
    var loadingMessage by remember { mutableStateOf("") }
    var backupStatus by remember { mutableStateOf<BackupStatus?>(null) }
    var autoBackupEnabled by remember { mutableStateOf(backupManager.isAutoBackupEnabled()) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showRestoreDialog by remember { mutableStateOf(false) }

    val backgroundBrush = Brush.verticalGradient(
        colors = listOf(Color(0xFF0f0c29), Color(0xFF302b63), Color(0xFF24243e))
    )

    // Load backup status on start
    LaunchedEffect(Unit) {
        isLoading = true
        loadingMessage = "Loading backup status..."
        backupStatus = backupManager.getBackupStatus()
        isLoading = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("Backup & Restore", color = Color(0xFF00D4FF), fontWeight = FontWeight.Bold)
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
                if (backupManager.isUserLoggedIn()) {
                    UserInfoCard(email = backupManager.getCurrentUserEmail() ?: "")
                } else {
                    LoginRequiredCard()
                }

                // Auto Backup Switch
                AutoBackupCard(
                    enabled = autoBackupEnabled,
                    onToggle = { enabled ->
                        autoBackupEnabled = enabled
                        backupManager.setAutoBackupEnabled(enabled)
                        Toast.makeText(
                            context,
                            if (enabled) "Auto backup enabled" else "Auto backup disabled",
                            Toast.LENGTH_SHORT
                        ).show()
                    },
                    lastAutoBackup = backupManager.getLastAutoBackupTime()
                )

                // Backup Status Card
                backupStatus?.let { status ->
                    BackupStatusCard(status = status)
                }

                // Backup Now Button
                BackupActionCard(
                    icon = Icons.Default.CloudUpload,
                    title = "Backup Now",
                    description = "Upload all AutoRespond data to cloud",
                    buttonText = "Backup",
                    buttonColor = Color(0xFF00D4FF),
                    enabled = backupManager.isUserLoggedIn() && !isLoading,
                    onClick = {
                        scope.launch {
                            isLoading = true
                            val result = backupManager.performBackup { progress ->
                                loadingMessage = progress
                            }
                            isLoading = false

                            if (result.success) {
                                Toast.makeText(context, "Backup successful! ${result.itemsBackedUp} items backed up", Toast.LENGTH_LONG).show()
                                backupStatus = backupManager.getBackupStatus()
                            } else {
                                Toast.makeText(context, "Backup failed: ${result.error}", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                )

                // Restore Button
                BackupActionCard(
                    icon = Icons.Default.CloudDownload,
                    title = "Restore from Cloud",
                    description = "Download and restore your backup data",
                    buttonText = "Restore",
                    buttonColor = Color(0xFF10B981),
                    enabled = backupManager.isUserLoggedIn() && !isLoading && (backupStatus?.hasBackup == true),
                    onClick = { showRestoreDialog = true }
                )

                // Delete Backup Button
                if (backupStatus?.hasBackup == true) {
                    BackupActionCard(
                        icon = Icons.Default.DeleteForever,
                        title = "Delete Cloud Backup",
                        description = "Remove all backup data from cloud",
                        buttonText = "Delete",
                        buttonColor = Color(0xFFEF4444),
                        enabled = backupManager.isUserLoggedIn() && !isLoading,
                        onClick = { showDeleteDialog = true }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
            }

            // Loading Overlay
            if (isLoading) {
                LoadingOverlay(message = loadingMessage)
            }
        }
    }

    // Restore Confirmation Dialog
    if (showRestoreDialog) {
        AlertDialog(
            onDismissRequest = { showRestoreDialog = false },
            containerColor = Color(0xFF1a1a2e),
            shape = RoundedCornerShape(20.dp),
            icon = {
                Icon(Icons.Default.Restore, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(48.dp))
            },
            title = {
                Text("Restore Backup?", color = Color.White, fontWeight = FontWeight.Bold)
            },
            text = {
                Text(
                    "This will restore all your AutoRespond data from cloud. Existing data may be overwritten.",
                    color = Color(0xFF94A3B8),
                    textAlign = TextAlign.Center
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showRestoreDialog = false
                        scope.launch {
                            isLoading = true
                            val result = backupManager.performRestore { progress ->
                                loadingMessage = progress
                            }
                            isLoading = false

                            if (result.success) {
                                Toast.makeText(
                                    context,
                                    "Restored: ${result.keywordRepliesRestored} keywords, ${result.spreadsheetsRestored} sheets, ${result.messageLogsRestored} logs",
                                    Toast.LENGTH_LONG
                                ).show()
                            } else {
                                Toast.makeText(context, "Restore failed: ${result.error}", Toast.LENGTH_LONG).show()
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
                ) {
                    Text("Restore")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRestoreDialog = false }) {
                    Text("Cancel", color = Color(0xFF64748B))
                }
            }
        )
    }

    // Delete Confirmation Dialog
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            containerColor = Color(0xFF1a1a2e),
            shape = RoundedCornerShape(20.dp),
            icon = {
                Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFEF4444), modifier = Modifier.size(48.dp))
            },
            title = {
                Text("Delete Backup?", color = Color.White, fontWeight = FontWeight.Bold)
            },
            text = {
                Text(
                    "This will permanently delete all your backup data from cloud. This action cannot be undone.",
                    color = Color(0xFF94A3B8),
                    textAlign = TextAlign.Center
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteDialog = false
                        scope.launch {
                            isLoading = true
                            loadingMessage = "Deleting backup..."
                            val success = backupManager.deleteBackup()
                            isLoading = false

                            if (success) {
                                Toast.makeText(context, "Backup deleted", Toast.LENGTH_SHORT).show()
                                backupStatus = backupManager.getBackupStatus()
                            } else {
                                Toast.makeText(context, "Failed to delete backup", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel", color = Color(0xFF64748B))
                }
            }
        )
    }
}


// ==================== UI Components ====================

@Composable
fun UserInfoCard(email: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1a1a2e)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(Color(0xFF00D4FF).copy(alpha = 0.2f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Person, contentDescription = null, tint = Color(0xFF00D4FF), modifier = Modifier.size(28.dp))
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text("Logged in as", fontSize = 12.sp, color = Color(0xFF94A3B8))
                Text(email, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
        }
    }
}

@Composable
fun LoginRequiredCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFEF4444).copy(alpha = 0.2f)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFEF4444), modifier = Modifier.size(32.dp))
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text("Login Required", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFFEF4444))
                Text("Please login to use backup feature", fontSize = 14.sp, color = Color(0xFF94A3B8))
            }
        }
    }
}

@Composable
fun AutoBackupCard(enabled: Boolean, onToggle: (Boolean) -> Unit, lastAutoBackup: Long) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1a1a2e)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .background(Color(0xFF8B5CF6).copy(alpha = 0.2f), RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Sync, contentDescription = null, tint = Color(0xFF8B5CF6), modifier = Modifier.size(28.dp))
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text("Auto Sync Backup", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Text("Daily automatic backup", fontSize = 13.sp, color = Color(0xFF94A3B8))
                    }
                }
                Switch(
                    checked = enabled,
                    onCheckedChange = onToggle,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = Color(0xFF8B5CF6)
                    )
                )
            }

            if (lastAutoBackup > 0) {
                Spacer(modifier = Modifier.height(12.dp))
                val dateFormat = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
                Text(
                    "Last auto backup: ${dateFormat.format(Date(lastAutoBackup))}",
                    fontSize = 12.sp,
                    color = Color(0xFF64748B)
                )
            }
        }
    }
}

@Composable
fun BackupStatusCard(status: BackupStatus) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1a1a2e)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (status.hasBackup) Icons.Default.CloudDone else Icons.Default.CloudOff,
                    contentDescription = null,
                    tint = if (status.hasBackup) Color(0xFF10B981) else Color(0xFF64748B),
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    if (status.hasBackup) "Backup Available" else "No Backup Found",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            if (status.hasBackup) {
                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = Color(0xFF2D3748))
                Spacer(modifier = Modifier.height(16.dp))

                // Last backup time
                status.lastBackupAt?.let { timestamp ->
                    val dateFormat = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
                    StatusRow(label = "Last Backup", value = dateFormat.format(Date(timestamp)))
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Backup contents
                Text("Backup Contents:", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF94A3B8))
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    BackupStatItem(count = status.keywordRepliesCount, label = "Keywords")
                    BackupStatItem(count = status.spreadsheetsCount, label = "Sheets")
                    BackupStatItem(count = status.messageLogsCount, label = "Logs")
                }
            }
        }
    }
}

@Composable
fun StatusRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, fontSize = 14.sp, color = Color(0xFF94A3B8))
        Text(value, fontSize = 14.sp, color = Color.White)
    }
}

@Composable
fun BackupStatItem(count: Int, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(count.toString(), fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color(0xFF00D4FF))
        Text(label, fontSize = 12.sp, color = Color(0xFF94A3B8))
    }
}


@Composable
fun BackupActionCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
    buttonText: String,
    buttonColor: Color,
    enabled: Boolean,
    onClick: () -> Unit
) {
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
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(buttonColor.copy(alpha = 0.2f), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = null, tint = buttonColor, modifier = Modifier.size(28.dp))
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Text(description, fontSize = 13.sp, color = Color(0xFF94A3B8))
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Button(
                onClick = onClick,
                enabled = enabled,
                colors = ButtonDefaults.buttonColors(
                    containerColor = buttonColor,
                    disabledContainerColor = buttonColor.copy(alpha = 0.3f)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(buttonText, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun LoadingOverlay(message: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.7f)),
        contentAlignment = Alignment.Center
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1a1a2e)),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(
                modifier = Modifier.padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CircularProgressIndicator(color = Color(0xFF00D4FF))
                Spacer(modifier = Modifier.height(16.dp))
                Text(message, fontSize = 16.sp, color = Color.White)
            }
        }
    }
}
