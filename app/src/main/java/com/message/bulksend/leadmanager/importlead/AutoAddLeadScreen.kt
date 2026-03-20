package com.message.bulksend.leadmanager.importlead

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.message.bulksend.leadmanager.screens.AutoAddSettingsScreen
import androidx.activity.compose.BackHandler

@Composable
fun AutoAddLeadScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    
    var showAutoAddSettingsScreen by remember { mutableStateOf(false) }
    
    // Handle back press
    BackHandler { 
        if (showAutoAddSettingsScreen) {
            showAutoAddSettingsScreen = false
        } else {
            onBack()
        }
    }
    
    if (showAutoAddSettingsScreen) {
        // Show the existing AutoAddSettingsScreen
        AutoAddSettingsScreen(
            leadManager = remember { com.message.bulksend.leadmanager.LeadManager(context) },
            onBack = { showAutoAddSettingsScreen = false }
        )
        return
    }
    
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Column {
                Text(
                    "Auto Add Leads",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Automatically import leads from connected platforms",
                    fontSize = 14.sp,
                    color = Color(0xFF94A3B8)
                )
            }
        }
        
        item {
            AutoAddSourceCard(
                icon = Icons.Default.Settings,
                title = "Auto Add Settings",
                description = "Configure automatic lead capture rules",
                status = "Configure",
                statusColor = Color(0xFF10B981),
                onClick = { showAutoAddSettingsScreen = true }
            )
        }
        
        item {
            AutoAddSourceCard(
                icon = Icons.Default.Chat,
                title = "WhatsApp Contacts",
                description = "Import contacts from WhatsApp",
                status = "Connected",
                statusColor = Color(0xFF10B981),
                onClick = { showAutoAddSettingsScreen = true }
            )
        }
        
        item {
            AutoAddSourceCard(
                icon = Icons.Default.Message,
                title = "Telegram Contacts",
                description = "Import contacts from Telegram",
                status = "Not Connected",
                statusColor = Color(0xFFF59E0B),
                onClick = { showAutoAddSettingsScreen = true }
            )
        }
        
        item {
            AutoAddSourceCard(
                icon = Icons.Default.PhotoCamera,
                title = "Instagram Followers",
                description = "Import followers from Instagram",
                status = "Not Connected",
                statusColor = Color(0xFFF59E0B),
                onClick = { showAutoAddSettingsScreen = true }
            )
        }
        
        item {
            AutoAddSourceCard(
                icon = Icons.Default.Group,
                title = "Facebook Contacts",
                description = "Import contacts from Facebook",
                status = "Coming Soon",
                statusColor = Color(0xFF64748B),
                onClick = { /* Coming soon */ }
            )
        }
        
        item {
            Spacer(modifier = Modifier.height(16.dp))
            AutoAddInfoCard()
        }
    }
}

@Composable
fun AutoAddSourceCard(
    icon: ImageVector,
    title: String,
    description: String,
    status: String,
    statusColor: Color,
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
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .background(statusColor.copy(alpha = 0.2f), RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    icon, 
                    contentDescription = null, 
                    tint = statusColor, 
                    modifier = Modifier.size(28.dp)
                )
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title, 
                    fontSize = 16.sp, 
                    fontWeight = FontWeight.Bold, 
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    description, 
                    fontSize = 13.sp, 
                    color = Color(0xFF94A3B8)
                )
            }
            
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = statusColor.copy(alpha = 0.2f)
            ) {
                Text(
                    status,
                    fontSize = 12.sp,
                    color = statusColor,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }
    }
}

@Composable
fun AutoAddInfoCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1a1a2e).copy(alpha = 0.5f)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Info, 
                    contentDescription = null, 
                    tint = Color(0xFF3B82F6), 
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "Auto Add Features", 
                    fontSize = 14.sp, 
                    fontWeight = FontWeight.Bold, 
                    color = Color.White
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                "• Automatically sync new contacts\n" +
                "• Remove duplicates intelligently\n" +
                "• Set custom import intervals\n" +
                "• Filter by contact criteria",
                fontSize = 12.sp,
                color = Color(0xFF94A3B8),
                lineHeight = 18.sp
            )
        }
    }
}