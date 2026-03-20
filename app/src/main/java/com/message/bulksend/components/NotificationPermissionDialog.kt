package com.message.bulksend.components

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.message.bulksend.utils.NotificationPermissionHelper

/**
 * Dialog to request notification permission
 * Shows when user tries to schedule a campaign without notification permission
 */
@Composable
fun NotificationPermissionDialog(
    onDismiss: () -> Unit,
    onRequestPermission: () -> Unit,
    onOpenSettings: () -> Unit,
    showSettingsOption: Boolean = false
) {
    val context = LocalContext.current
    
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Icon
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .background(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(40.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (showSettingsOption) Icons.Default.NotificationsOff else Icons.Default.Notifications,
                        contentDescription = "Notification",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(40.dp)
                    )
                }
                
                // Title
                Text(
                    text = if (showSettingsOption) "Notification Permission Denied" else "Enable Notifications",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )
                
                // Description
                Text(
                    text = if (showSettingsOption) {
                        "Notification permission is required to show campaign progress and status updates.\n\nPlease enable notifications from app settings."
                    } else {
                        "Notification permission is required to:\n\n" +
                        "• Show scheduled campaign progress\n" +
                        "• Display campaign status updates\n" +
                        "• Track message sending in background\n" +
                        "• Notify when campaigns complete"
                    },
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Buttons
                if (showSettingsOption) {
                    // Show "Open Settings" button when permission is permanently denied
                    Button(
                        onClick = {
                            onOpenSettings()
                            onDismiss()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            Icons.Default.Notifications,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Open Settings",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            "Cancel",
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                } else {
                    // Show "Allow" button for first-time permission request
                    Button(
                        onClick = {
                            onRequestPermission()
                            onDismiss()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            Icons.Default.Notifications,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Allow Notifications",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            "Not Now",
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Compact notification permission banner
 * Shows at the top of screen when permission is not granted
 */
@Composable
fun NotificationPermissionBanner(
    onRequestPermission: () -> Unit,
    onDismiss: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        ),
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
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.NotificationsOff,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(24.dp)
                )
                Column {
                    Text(
                        "Notifications Disabled",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Text(
                        "Enable to track campaigns",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.7f)
                    )
                }
            }
            
            TextButton(onClick = onRequestPermission) {
                Text(
                    "Enable",
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
