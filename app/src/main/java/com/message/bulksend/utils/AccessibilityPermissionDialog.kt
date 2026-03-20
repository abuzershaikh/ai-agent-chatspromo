package com.message.bulksend.utils

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@Composable
fun AccessibilityPermissionDialog(
    onAgree: () -> Unit,
    onDisagree: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var isVisible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(100)
        isVisible = true
    }

    val infiniteTransition = rememberInfiniteTransition(label = "icon_rotation")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ), label = "rotation"
    )

    Dialog(
        onDismissRequest = { /* Keep dialog mandatory */ },
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false
        )
    ) {
        AnimatedVisibility(
            visible = isVisible,
            enter = scaleIn(
                initialScale = 0.8f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow
                )
            ) + fadeIn(),
            exit = scaleOut() + fadeOut()
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Animated Icon
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(
                                        Color(0xFF4F46E5),
                                        Color(0xFF7C3AED)
                                    )
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Accessibility Icon",
                            tint = Color.White,
                            modifier = Modifier
                                .size(40.dp)
                                .rotate(rotation * 0.1f)
                        )
                    }

                    // Title
                    Text(
                        text = "Accessibility Permission Required",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1E293B),
                        textAlign = TextAlign.Center
                    )

                    // Description
                    Text(
                        text = "To send bulk messages, we need accessibility permission. This allows the app to automate message sending.",
                        fontSize = 14.sp,
                        color = Color(0xFF64748B),
                        textAlign = TextAlign.Center,
                        lineHeight = 20.sp
                    )

                    // Instructions Card
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF1F5F9))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "How to Enable:",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1E293B)
                            )

                            Text(
                                text = "1. Tap 'I Agree' below\n2. Find 'Bulk Send' in the list\n3. Turn the switch ON\n4. Confirm by tapping 'Allow'",
                                fontSize = 13.sp,
                                color = Color(0xFF475569),
                                lineHeight = 20.sp
                            )
                        }
                    }

                    // Learn More Link
                    Text(
                        text = "Need help? Tap here",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF4F46E5),
                        modifier = Modifier.clickable {
                            
                        }                    )

                    // Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Disagree Button
                        Button(
                            onClick = {
                                onDisagree()
                                onDismiss()
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(56.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFE2E8F0)
                            )
                        ) {
                            Text(
                                text = "Disagree",
                                color = Color(0xFF64748B),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // Agree Button
                        Button(
                            onClick = {
                                onAgree()
                                onDismiss()
                                redirectToAccessibilitySettings(context)
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(56.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF4F46E5)
                            )
                        ) {
                            Text(
                                text = "I Agree",
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun redirectToAccessibilitySettings(context: Context) {
    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
    context.startActivity(intent)
}

