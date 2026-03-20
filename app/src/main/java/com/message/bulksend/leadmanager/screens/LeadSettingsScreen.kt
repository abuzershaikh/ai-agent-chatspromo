package com.message.bulksend.leadmanager.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.message.bulksend.leadmanager.LeadManager
import kotlinx.coroutines.launch

@Composable
fun LeadSettingsScreen(
    leadManager: LeadManager,
    onManageTags: () -> Unit,
    onManageSources: () -> Unit,
    onManageProducts: () -> Unit,
    onAutoAddSettings: () -> Unit = {},
    onImportLeads: () -> Unit = {},
    onCustomFields: () -> Unit = {}
) {
    val backgroundBrush = Brush.verticalGradient(
        colors = listOf(Color(0xFF0f0c29), Color(0xFF302b63), Color(0xFF24243e))
    )
    
    // Async Stats loading
    var totalLeads by remember { mutableStateOf(0) }
    var totalProducts by remember { mutableStateOf(0) }
    var totalTags by remember { mutableStateOf(0) }
    var totalSources by remember { mutableStateOf(0) }
    var isLoading by remember { mutableStateOf(true) }
    
    LaunchedEffect(Unit) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val stats = leadManager.getStats()
            val products = leadManager.getAllProductsV2().size
            val tags = leadManager.getAllTags().size
            val sources = leadManager.getAllSources().size
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                totalLeads = stats.totalLeads
                totalProducts = products
                totalTags = tags
                totalSources = sources
                isLoading = false
            }
        }
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundBrush)
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            "Settings",
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            "Manage your lead manager preferences",
                            fontSize = 14.sp,
                            color = Color(0xFF94A3B8)
                        )
                    }
                    
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .background(Color(0xFF8B5CF6).copy(alpha = 0.2f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = null,
                            tint = Color(0xFF8B5CF6),
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }
            
            // Quick Stats Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1a1a2e)),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp)
                    ) {
                        Text(
                            "Quick Stats",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF10B981),
                            modifier = Modifier.padding(bottom = 16.dp)
                        )
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            QuickStatItem(
                                icon = Icons.Default.People,
                                value = totalLeads.toString(),
                                label = "Leads",
                                color = Color(0xFF3B82F6)
                            )
                            QuickStatItem(
                                icon = Icons.Default.Inventory,
                                value = totalProducts.toString(),
                                label = "Products",
                                color = Color(0xFFF59E0B)
                            )
                            QuickStatItem(
                                icon = Icons.Default.Label,
                                value = totalTags.toString(),
                                label = "Tags",
                                color = Color(0xFF10B981)
                            )
                            QuickStatItem(
                                icon = Icons.Default.Source,
                                value = totalSources.toString(),
                                label = "Sources",
                                color = Color(0xFF8B5CF6)
                            )
                        }
                    }
                }
            }
            
            // Data Management Section
            item {
                Text(
                    "Data Management",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF94A3B8),
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
            
            item {
                SettingsCard(
                    icon = Icons.Default.Label,
                    title = "Manage Tags",
                    subtitle = "$totalTags tags configured",
                    color = Color(0xFF10B981),
                    onClick = onManageTags
                )
            }
            
            item {
                SettingsCard(
                    icon = Icons.Default.Source,
                    title = "Manage Sources",
                    subtitle = "$totalSources lead sources",
                    color = Color(0xFF3B82F6),
                    onClick = onManageSources
                )
            }
            
            item {
                SettingsCard(
                    icon = Icons.Default.Inventory,
                    title = "Manage Products",
                    subtitle = "$totalProducts products/services",
                    color = Color(0xFFF59E0B),
                    onClick = onManageProducts
                )
            }
            
            item {
                SettingsCard(
                    icon = Icons.Default.DynamicForm,
                    title = "Custom Fields",
                    subtitle = "Add extra fields to capture more lead data",
                    color = Color(0xFFEC4899),
                    onClick = onCustomFields
                )
            }
            
            // AutoRespond Integration Section
            item {
                Text(
                    "AutoRespond Integration",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF94A3B8),
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
            
            item {
                SettingsCard(
                    icon = Icons.Default.AutoAwesome,
                    title = "Auto-Add Settings",
                    subtitle = "Auto-add leads from AutoRespond messages",
                    color = Color(0xFF25D366),
                    onClick = onAutoAddSettings
                )
            }
            
            // Import Section
            item {
                Text(
                    "Import & Export",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF94A3B8),
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
            
            item {
                SettingsCard(
                    icon = Icons.Default.Upload,
                    title = "Import Leads",
                    subtitle = "Import from Excel, CSV, or VCF files",
                    color = Color(0xFF06B6D4),
                    onClick = onImportLeads
                )
            }
            
            // Preferences Section
            item {
                Text(
                    "Preferences",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF94A3B8),
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
            
            item {
                var notificationsEnabled by remember {
                    mutableStateOf(leadManager.isFollowUpReminderEnabled())
                }
                SettingsToggleCard(
                    icon = Icons.Default.Notifications,
                    title = "Follow-up Reminders",
                    subtitle = "Get notified before scheduled follow-ups",
                    color = Color(0xFFEC4899),
                    isEnabled = notificationsEnabled,
                    onToggle = {
                        notificationsEnabled = it
                        leadManager.setFollowUpReminderEnabled(it)
                    }
                )
            }
            
            item {
                var autoBackupEnabled by remember { mutableStateOf(false) }
                SettingsToggleCard(
                    icon = Icons.Default.CloudUpload,
                    title = "Auto Backup",
                    subtitle = "Automatically backup leads to cloud",
                    color = Color(0xFF06B6D4),
                    isEnabled = autoBackupEnabled,
                    onToggle = { autoBackupEnabled = it }
                )
            }
            
            // Danger Zone Section
            item {
                Text(
                    "Danger Zone",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFEF4444),
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
            
            item {
                var showDeleteDialog by remember { mutableStateOf(false) }
                
                SettingsCard(
                    icon = Icons.Default.DeleteForever,
                    title = "Clear All Data",
                    subtitle = "Delete all leads, products, and settings",
                    color = Color(0xFFEF4444),
                    onClick = { showDeleteDialog = true }
                )
                
                if (showDeleteDialog) {
                    val coroutineScope = rememberCoroutineScope()
                    var isDeleting by remember { mutableStateOf(false) }
                    
                    AlertDialog(
                        onDismissRequest = { if (!isDeleting) showDeleteDialog = false },
                        title = { 
                            Text(
                                "Clear All Data?",
                                color = Color(0xFFEF4444),
                                fontWeight = FontWeight.Bold
                            ) 
                        },
                        text = { 
                            if (isDeleting) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(24.dp),
                                        color = Color(0xFFEF4444),
                                        strokeWidth = 2.dp
                                    )
                                    Text(
                                        "Deleting all leads...",
                                        color = Color(0xFF94A3B8)
                                    )
                                }
                            } else {
                                Text(
                                    "This will permanently delete all your leads ($totalLeads leads). This action cannot be undone.",
                                    color = Color(0xFF94A3B8)
                                )
                            }
                        },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    isDeleting = true
                                    coroutineScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                        try {
                                            leadManager.deleteAllLeadsSuspend()
                                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                                totalLeads = 0
                                                isDeleting = false
                                                showDeleteDialog = false
                                            }
                                        } catch (e: Exception) {
                                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                                isDeleting = false
                                                showDeleteDialog = false
                                            }
                                        }
                                    }
                                },
                                enabled = !isDeleting
                            ) {
                                Text("Delete All", color = if (isDeleting) Color(0xFF64748B) else Color(0xFFEF4444))
                            }
                        },
                        dismissButton = {
                            TextButton(
                                onClick = { showDeleteDialog = false },
                                enabled = !isDeleting
                            ) {
                                Text("Cancel", color = Color(0xFF94A3B8))
                            }
                        },
                        containerColor = Color(0xFF1a1a2e)
                    )
                }
            }
            
            // App Info
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1a1a2e).copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "Lead Manager",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF10B981)
                        )
                        Text(
                            "Version 1.0.0",
                            fontSize = 12.sp,
                            color = Color(0xFF64748B)
                        )
                        Text(
                            "Powered by Room Database",
                            fontSize = 10.sp,
                            color = Color(0xFF64748B),
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }
            
            item {
                Spacer(modifier = Modifier.height(80.dp))
            }
        }
    }
}

@Composable
fun QuickStatItem(
    icon: ImageVector,
    value: String,
    label: String,
    color: Color
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .background(color.copy(alpha = 0.2f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(24.dp)
            )
        }
        Text(
            value,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        Text(
            label,
            fontSize = 12.sp,
            color = Color(0xFF94A3B8)
        )
    }
}

@Composable
fun SettingsCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    color: Color,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1a1a2e)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
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
            
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White
                )
                Text(
                    subtitle,
                    fontSize = 13.sp,
                    color = Color(0xFF94A3B8)
                )
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
fun SettingsToggleCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    color: Color,
    isEnabled: Boolean,
    onToggle: (Boolean) -> Unit
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
            horizontalArrangement = Arrangement.spacedBy(16.dp),
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
            
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White
                )
                Text(
                    subtitle,
                    fontSize = 13.sp,
                    color = Color(0xFF94A3B8)
                )
            }
            
            Switch(
                checked = isEnabled,
                onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = color,
                    uncheckedThumbColor = Color(0xFF64748B),
                    uncheckedTrackColor = Color(0xFF334155)
                )
            )
        }
    }
}
