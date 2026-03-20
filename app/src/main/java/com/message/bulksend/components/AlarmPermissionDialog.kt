package com.message.bulksend.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

/**
 * Dialog to request SCHEDULE_EXACT_ALARM permission
 * Required for Android 12+ (API 31+) to schedule exact alarms
 * 
 * Reference: https://developer.android.com/about/versions/14/changes/schedule-exact-alarms
 */
@Composable
fun AlarmPermissionDialog(
    onDismiss: () -> Unit,
    onRequestPermission: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color.White
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
                // Icon with gradient background
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .background(
                            brush = Brush.linearGradient(
                                colors = listOf(
                                    Color(0xFF9C27B0),
                                    Color(0xFFE91E63)
                                )
                            ),
                            shape = RoundedCornerShape(20.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Schedule,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(40.dp)
                    )
                }
                
                // Title
                Text(
                    text = "⏰ Exact Alarm Permission Required",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1E293B),
                    textAlign = TextAlign.Center
                )
                
                // Description
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFFF1F5F9)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Info,
                                contentDescription = null,
                                tint = Color(0xFF9C27B0),
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "To schedule campaigns at precise times, this app needs permission to set exact alarms.",
                                fontSize = 14.sp,
                                color = Color(0xFF475569),
                                lineHeight = 20.sp
                            )
                        }
                        
                        Divider(color = Color(0xFFCBD5E1))
                        
                        Text(
                            text = "Why this permission is needed:",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF334155)
                        )
                        
                        Column(
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            PermissionReasonItem("✓ Run campaigns at exact scheduled time")
                            PermissionReasonItem("✓ Send messages precisely when you want")
                            PermissionReasonItem("✓ Reliable execution even in battery saver mode")
                        }
                        
                        Divider(color = Color(0xFFCBD5E1))
                        
                        Text(
                            text = "📍 You'll be redirected to:\nSettings > Apps > Special app access > Alarms & reminders",
                            fontSize = 12.sp,
                            color = Color(0xFF64748B),
                            lineHeight = 18.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Action Buttons
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Grant Permission Button
                    Button(
                        onClick = onRequestPermission,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF9C27B0)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Schedule,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "Grant Permission",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    
                    // Cancel Button
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = Color(0xFF64748B)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = "Cancel",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
                
                // Warning Note
                Text(
                    text = "⚠️ Without this permission, scheduled campaigns may not run at the exact time.",
                    fontSize = 11.sp,
                    color = Color(0xFFEF4444),
                    textAlign = TextAlign.Center,
                    lineHeight = 16.sp
                )
            }
        }
    }
}

@Composable
private fun PermissionReasonItem(text: String) {
    Text(
        text = text,
        fontSize = 13.sp,
        color = Color(0xFF475569),
        modifier = Modifier.padding(start = 8.dp)
    )
}
