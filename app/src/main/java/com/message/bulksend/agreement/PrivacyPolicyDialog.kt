package com.message.bulksend.agreement

import android.content.Context
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@Composable
fun PrivacyPolicyDialog(
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.9f),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF1E1E1E)
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = "Privacy",
                            tint = Color(0xFF10B981),
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Privacy & Permissions",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Scrollable Content
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                ) {
                    // Introduction
                    Text(
                        text = "ChatsPromo values your privacy above all. This app uses the following permissions to provide you with the best experience:",
                        fontSize = 15.sp,
                        color = Color.White.copy(alpha = 0.9f),
                        lineHeight = 22.sp
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    // Permissions List
                    PermissionItem(
                        icon = Icons.Default.Contacts,
                        title = "📱 Contacts",
                        description = "To read your phone contacts so you can send bulk messages and manage contacts efficiently. This data stays only on your device and is never uploaded anywhere."
                    )

                    PermissionItem(
                        icon = Icons.Default.Storage,
                        title = "💾 Storage",
                        description = "To import/export CSV/Excel files, save contacts, and send media files (images, videos, documents) through messaging apps."
                    )

                    PermissionItem(
                        icon = Icons.Default.Wifi,
                        title = "🌐 Internet",
                        description = "For Firebase cloud sync, customer support, and app updates. Your data is transferred in encrypted form for maximum security."
                    )

                    PermissionItem(
                        icon = Icons.Default.Notifications,
                        title = "🔔 Notifications",
                        description = "To show notifications for campaign status updates, customer support messages, and important alerts about your campaigns."
                    )

                    PermissionItem(
                        icon = Icons.Default.Mic,
                        title = "🎤 Microphone",
                        description = "To record audio notes in TableSheet feature. Audio recording only happens when you manually press the record button - no background recording."
                    )

                    PermissionItem(
                        icon = Icons.Default.Accessibility,
                        title = "♿ Accessibility Service",
                        description = "For messaging app automation - to automatically send bulk messages. This service only interacts with messaging apps and does not collect any personal data."
                    )

                    PermissionItem(
                        icon = Icons.Default.Window,
                        title = "🪟 Overlay Window",
                        description = "To show campaign progress in a floating window so you can see the progress while using other apps on your phone."
                    )

                    PermissionItem(
                        icon = Icons.Default.Schedule,
                        title = "⏰ Exact Alarm & Wake Lock",
                        description = "To execute scheduled campaigns at exact times, even when your phone is in sleep mode. Ensures your scheduled messages are sent on time."
                    )

                    PermissionItem(
                        icon = Icons.Default.FolderSpecial,
                        title = "🔄 Foreground Service",
                        description = "To reliably execute scheduled campaigns in the background. This ensures your scheduled messages are sent even when the app is not actively open."
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    // Firebase Backup Section
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFF2A2A2A)
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Cloud,
                                    contentDescription = "Cloud",
                                    tint = Color(0xFF10B981),
                                    modifier = Modifier.size(28.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = "☁️ Firebase Cloud Backup",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Text(
                                text = "Your data is securely backed up to Google Firebase cloud:",
                                fontSize = 14.sp,
                                color = Color.White.copy(alpha = 0.9f),
                                fontWeight = FontWeight.SemiBold
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            BackupItem("✅ User profile & subscription details")
                            BackupItem("✅ Contacts & groups (encrypted)")
                            BackupItem("✅ Templates & campaigns")
                            BackupItem("✅ Auto-reply settings & AI configurations")
                            BackupItem("✅ Lead forms & TableSheet data")
                            BackupItem("✅ Message logs & analytics")

                            Spacer(modifier = Modifier.height(12.dp))

                            Text(
                                text = "🔒 Security: All data is end-to-end encrypted and accessible only from your account. We never share your data with any third parties.",
                                fontSize = 13.sp,
                                color = Color(0xFF10B981),
                                lineHeight = 20.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Data Privacy Section
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFF1A3A2A)
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Shield,
                                    contentDescription = "Shield",
                                    tint = Color(0xFF10B981),
                                    modifier = Modifier.size(28.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = "🛡️ Data Privacy Guarantee",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            PrivacyItem("❌ We do NOT read your messages")
                            PrivacyItem("❌ We do NOT sell your contacts to third parties")
                            PrivacyItem("❌ We do NOT share your personal information")
                            PrivacyItem("✅ All data is locally stored and encrypted")
                            PrivacyItem("✅ Cloud backup is optional and user-controlled")
                            PrivacyItem("✅ You can delete your data anytime")
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Footer Note
                    Text(
                        text = "By using this app, you agree to these permissions and privacy policy. For any questions, please contact customer support.",
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.6f),
                        textAlign = TextAlign.Center,
                        lineHeight = 18.sp,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Dismiss Button
                Button(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF10B981)
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(
                        text = "I Understand, Continue",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }
    }
}

@Composable
fun PermissionItem(
    icon: ImageVector,
    title: String,
    description: String
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF2A2A2A)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = Color(0xFF10B981),
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = description,
                    fontSize = 13.sp,
                    color = Color.White.copy(alpha = 0.8f),
                    lineHeight = 20.sp
                )
            }
        }
    }
}

@Composable
fun BackupItem(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = text,
            fontSize = 13.sp,
            color = Color.White.copy(alpha = 0.85f),
            lineHeight = 20.sp
        )
    }
}

@Composable
fun PrivacyItem(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = text,
            fontSize = 13.sp,
            color = Color.White.copy(alpha = 0.9f),
            lineHeight = 20.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

/**
 * Helper function to check if user has seen privacy policy
 */
object PrivacyPolicyHelper {
    private const val PREFS_NAME = "privacy_prefs"
    private const val KEY_POLICY_SHOWN = "policy_shown"

    fun hasSeenPrivacyPolicy(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_POLICY_SHOWN, false)
    }

    fun markPrivacyPolicyAsSeen(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_POLICY_SHOWN, true).apply()
    }

    fun resetPrivacyPolicy(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_POLICY_SHOWN, false).apply()
    }
}
