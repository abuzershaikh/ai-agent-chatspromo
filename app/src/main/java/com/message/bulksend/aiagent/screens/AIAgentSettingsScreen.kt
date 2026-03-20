package com.message.bulksend.aiagent.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.message.bulksend.aiagent.tools.paymentverification.PaymentVerificationAIIntegration
import kotlinx.coroutines.launch

@Composable
fun AIAgentSettingsScreen() {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header Card
        item {
            SettingsHeaderCard()
        }
        
        // Only wired/real settings are shown here.
        item {
            SettingsSectionTitle(
                title = "Connected Settings",
                icon = Icons.Outlined.VerifiedUser,
                color = Color(0xFF06B6D4)
            )
        }
        
        item {
            BehaviorSettings()
        }
        
        // Bottom Spacer
        item {
            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@Composable
fun SettingsHeaderCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E2E).copy(alpha = 0.9f)),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF6366F1).copy(alpha = 0.3f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Outlined.Settings,
                contentDescription = "Settings",
                tint = Color(0xFF6366F1),
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                "AI Agent Settings",
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Configure and customize your AI agent behavior",
                fontSize = 13.sp,
                color = Color(0xFF94A3B8),
                textAlign = TextAlign.Center,
                lineHeight = 18.sp
            )
        }
    }
}

@Composable
fun SettingsSectionTitle(title: String, icon: ImageVector, color: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(color.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = color, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(12.dp))
        Text(
            title,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            color = Color.White
        )
        Spacer(Modifier.weight(1f))
        Box(
            modifier = Modifier
                .weight(1f)
                .height(1.dp)
                .background(
                    brush = Brush.horizontalGradient(
                        colors = listOf(color.copy(alpha = 0.5f), Color.Transparent)
                    )
                )
        )
    }
}

@Composable
fun BehaviorSettings() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val paymentVerifyIntegration = remember { PaymentVerificationAIIntegration.getInstance(context) }
    
    var paymentVerifyEnabled by remember { mutableStateOf(false) }
    
    // Load initial state
    LaunchedEffect(Unit) {
        paymentVerifyEnabled = paymentVerifyIntegration.isEnabled()
    }
    
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SettingsToggleCard(
            icon = Icons.Outlined.VerifiedUser,
            title = "Payment Verification Link",
            subtitle = "Send verification link after QR code",
            color = Color(0xFF06B6D4),
            isEnabled = paymentVerifyEnabled,
            onToggle = { enabled ->
                paymentVerifyEnabled = enabled
                scope.launch {
                    paymentVerifyIntegration.setEnabled(enabled)
                }
            }
        )
    }
}

@Composable
fun SettingsToggleCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    color: Color,
    isEnabled: Boolean,
    onToggle: (Boolean) -> Unit = {}
) {
    var enabled by remember { mutableStateOf(isEnabled) }
    
    // Update when isEnabled prop changes
    LaunchedEffect(isEnabled) {
        enabled = isEnabled
    }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E2E).copy(alpha = 0.9f)),
        border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.3f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(color.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = color, modifier = Modifier.size(24.dp))
            }
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = Color.White)
                Text(subtitle, fontSize = 12.sp, color = Color(0xFF94A3B8))
            }
            Switch(
                checked = enabled,
                onCheckedChange = { 
                    enabled = it
                    onToggle(it)
                },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = color,
                    uncheckedThumbColor = Color(0xFF64748B),
                    uncheckedTrackColor = Color(0xFF1E293B)
                )
            )
        }
    }
}
