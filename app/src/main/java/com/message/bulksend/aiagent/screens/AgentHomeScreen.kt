package com.message.bulksend.aiagent.screens

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.message.bulksend.aiagent.tools.calendar.GoogleCalendarManager
import com.message.bulksend.aiagent.tools.gmail.GoogleGmailManager
import com.message.bulksend.autorespond.ai.settings.AIAgentSettingsManager
import com.message.bulksend.autorespond.tools.sheetconnect.SheetConnectManager
import kotlinx.coroutines.launch

private data class AgentToolItem(
        val icon: ImageVector,
        val label: String,
        val color: Color,
        val statusBadge: String? = null,
        val statusColor: Color? = null,
        val onClick: () -> Unit
)

@Composable
fun AgentHomeScreen() {
        LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
                // Stats Section
                item { AIAgentStatsSection() }

                // Bottom Spacer
                item { Spacer(modifier = Modifier.height(20.dp)) }
        }
}

@Composable
fun AIAgentStatsSection() {
        val context = LocalContext.current
        val lifecycleOwner = LocalLifecycleOwner.current
        val scope = rememberCoroutineScope()
        val sheetConnectManager = remember(context) { SheetConnectManager(context) }
        val googleCalendarManager = remember(context) { GoogleCalendarManager(context) }
        val googleGmailManager = remember(context) { GoogleGmailManager(context) }
        val aiSettingsManager = remember(context) { AIAgentSettingsManager(context) }
        var googleSheetSetupDone by remember { mutableStateOf<Boolean?>(null) }
        var googleCalendarSetupDone by remember { mutableStateOf<Boolean?>(null) }
        var googleCalendarAiEnabled by remember { mutableStateOf(false) }
        var googleGmailSetupDone by remember { mutableStateOf<Boolean?>(null) }
        var googleGmailAiEnabled by remember { mutableStateOf(false) }

        fun refreshGoogleSheetStatus() {
                scope.launch {
                        googleSheetSetupDone =
                                runCatching { sheetConnectManager.isSetupDone() }.getOrDefault(false)
                }
        }

        fun refreshGoogleCalendarStatus() {
                scope.launch {
                        googleCalendarSetupDone =
                                runCatching { googleCalendarManager.isSetupDone() }
                                        .getOrDefault(false)
                        googleCalendarAiEnabled =
                                aiSettingsManager.isAgentEnabled &&
                                aiSettingsManager.activeTemplate.equals("CUSTOM", ignoreCase = true) &&
                                aiSettingsManager.customTemplateEnableGoogleCalendarTool
                }
        }

        fun refreshGoogleGmailStatus() {
                scope.launch {
                        googleGmailSetupDone =
                                runCatching { googleGmailManager.isSetupDone() }
                                        .getOrDefault(false)
                        googleGmailAiEnabled =
                                aiSettingsManager.isAgentEnabled &&
                                aiSettingsManager.activeTemplate.equals("CUSTOM", ignoreCase = true) &&
                                aiSettingsManager.customTemplateEnableGoogleGmailTool
                }
        }

        LaunchedEffect(Unit) {
                refreshGoogleSheetStatus()
                refreshGoogleCalendarStatus()
                refreshGoogleGmailStatus()
        }

        DisposableEffect(lifecycleOwner) {
                val observer = LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_RESUME) {
                                refreshGoogleSheetStatus()
                                refreshGoogleCalendarStatus()
                                refreshGoogleGmailStatus()
                        }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
        }

        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                AgentToolSection(
                        title = "AI Agent Settings",
                        icon = Icons.Outlined.SettingsSuggest,
                        color = Color(0xFF6366F1),
                        tools =
                                listOf(
                                        AgentToolItem(
                                                icon = Icons.Outlined.Settings,
                                                label = "AI Agent Settings",
                                                color = Color(0xFF6366F1),
                                                onClick = {
                                                        context.startActivity(
                                                                Intent(
                                                                        context,
                                                                        com.message.bulksend.autorespond.ui
                                                                                .settings
                                                                                .AIAgentSettingsActivity::class
                                                                                .java
                                                                )
                                                        )
                                                }
                                        ),
                                        AgentToolItem(
                                                icon = Icons.Outlined.AutoAwesome,
                                                label = "Owner Assist",
                                                color = Color(0xFFEAB308),
                                                onClick = {
                                                        context.startActivity(
                                                                Intent(
                                                                        context,
                                                                        com.message.bulksend.aiagent.tools
                                                                                .reverseai
                                                                                .ReverseAIActivity::class
                                                                                .java
                                                                )
                                                        )
                                                }
                                        )
                                )
                )
                AgentToolSection(
                        title = "Catalogue & Documents",
                        icon = Icons.Outlined.Inventory2,
                        color = Color(0xFF14B8A6),
                        tools =
                                listOf(
                                        AgentToolItem(
                                                icon = Icons.Outlined.ShoppingCart,
                                                label = "Product Catalogue",
                                                color = Color(0xFF14B8A6),
                                                onClick = {
                                                        context.startActivity(
                                                                Intent(
                                                                        context,
                                                                        com.message.bulksend.autorespond.ui
                                                                                .catalogue
                                                                                .ProductCatalogueActivity::class
                                                                                .java
                                                                )
                                                        )
                                                }
                                        ),
                                        AgentToolItem(
                                                icon = Icons.Outlined.Folder,
                                                label = "Agent Documents",
                                                color = Color(0xFF8B5CF6),
                                                onClick = {
                                                        context.startActivity(
                                                                Intent(
                                                                        context,
                                                                        com.message.bulksend.aiagent.tools
                                                                                .agentdocument
                                                                                .AgentDocumentActivity::class
                                                                                .java
                                                                )
                                                        )
                                                }
                                        )
                                )
                )
                AgentToolSection(
                        title = "Google Tools",
                        icon = Icons.Outlined.CloudSync,
                        color = Color(0xFF4285F4),
                        tools =
                                listOf(
                                        AgentToolItem(
                                                icon = Icons.Outlined.Email,
                                                label = "Google Gmail",
                                                color = Color(0xFFEA4335),
                                                statusBadge =
                                                        when (googleGmailSetupDone) {
                                                                true ->
                                                                        if (googleGmailAiEnabled) {
                                                                                "AI ON"
                                                                        } else {
                                                                                "Connected"
                                                                        }
                                                                else -> null
                                                        },
                                                statusColor =
                                                        if (googleGmailAiEnabled ||
                                                                        googleGmailSetupDone == true
                                                        ) {
                                                                Color(0xFF22C55E)
                                                        } else {
                                                                Color(0xFFEA4335)
                                                        },
                                                onClick = {
                                                        context.startActivity(
                                                                Intent(
                                                                        context,
                                                                        com.message.bulksend.aiagent.tools
                                                                                .gmail
                                                                                .GoogleGmailSetupActivity::class
                                                                                .java
                                                                )
                                                        )
                                                }
                                        ),
                                        AgentToolItem(
                                                icon = Icons.Outlined.CalendarMonth,
                                                label = "Google Calendar",
                                                color = Color(0xFF4285F4),
                                                statusBadge =
                                                        when (googleCalendarSetupDone) {
                                                                true ->
                                                                        if (googleCalendarAiEnabled) {
                                                                                "AI ON"
                                                                        } else {
                                                                                "Connected"
                                                                        }
                                                                else -> null
                                                        },
                                                statusColor =
                                                        if (googleCalendarAiEnabled ||
                                                                        googleCalendarSetupDone ==
                                                                                true
                                                        ) {
                                                                Color(0xFF22C55E)
                                                        } else {
                                                                Color(0xFF4285F4)
                                                        },
                                                onClick = {
                                                        context.startActivity(
                                                                Intent(
                                                                        context,
                                                                        com.message.bulksend.aiagent.tools
                                                                                .calendar
                                                                                .GoogleCalendarSetupActivity::class
                                                                                .java
                                                                )
                                                        )
                                                }
                                        ),
                                        AgentToolItem(
                                                icon = Icons.Outlined.TableView,
                                                label = "Google Sheets",
                                                color = Color(0xFF34A853),
                                                onClick = {
                                                        val targetActivity =
                                                                if (googleSheetSetupDone == true) {
                                                                        com.message.bulksend
                                                                                .autorespond.tools
                                                                                .sheetconnect
                                                                                .SheetMappingActivity::class
                                                                                .java
                                                                } else {
                                                                        com.message.bulksend
                                                                                .autorespond.tools
                                                                                .sheetconnect
                                                                                .SheetConnectSetupActivity::class
                                                                                .java
                                                                }
                                                        context.startActivity(
                                                                Intent(context, targetActivity)
                                                        )
                                                }
                                        )
                                )
                )
                AgentToolSection(
                        title = "E-commerce Hub",
                        icon = Icons.Outlined.Storefront,
                        color = Color(0xFFFB923C),
                        tools =
                                listOf(
                                        AgentToolItem(
                                                icon = Icons.Outlined.Storefront,
                                                label = "E-commerce Hub",
                                                color = Color(0xFFFB923C),
                                                onClick = {
                                                        context.startActivity(
                                                                Intent(
                                                                        context,
                                                                        com.message.bulksend.aiagent.tools
                                                                                .ecommerce
                                                                                .EcommerceToolsActivity::class
                                                                                .java
                                                                )
                                                        )
                                                }
                                        )
                                )
                )
                AgentToolSection(
                        title = "Payment Tools",
                        icon = Icons.Outlined.Payments,
                        color = Color(0xFFFB923C),
                        tools =
                                listOf(
                                        AgentToolItem(
                                                icon = Icons.Outlined.Payment,
                                                label = "Payment Methods",
                                                color = Color(0xFF10B981),
                                                onClick = {
                                                        context.startActivity(
                                                                Intent(
                                                                        context,
                                                                        com.message.bulksend.aiagent.tools
                                                                                .ecommerce
                                                                                .PaymentMethodActivity::class
                                                                                .java
                                                                )
                                                        )
                                                }
                                        ),
                                        AgentToolItem(
                                                icon = Icons.Outlined.AccountBalanceWallet,
                                                label = "Razorpay Setup",
                                                color = Color(0xFF2563EB),
                                                onClick = {
                                                        context.startActivity(
                                                                Intent(
                                                                        context,
                                                                        com.message.bulksend.aiagent.tools
                                                                                .ecommerce
                                                                                .RazorpayConfigActivity::class
                                                                                .java
                                                                )
                                                        )
                                                }
                                        ),
                                        AgentToolItem(
                                                icon = Icons.Outlined.VerifiedUser,
                                                label = "Payment Verify",
                                                color = Color(0xFF06B6D4),
                                                onClick = {
                                                        context.startActivity(
                                                                Intent(
                                                                        context,
                                                                        com.message.bulksend.aiagent.tools
                                                                                .paymentverification
                                                                                .PaymentVerificationActivity::class
                                                                                .java
                                                                )
                                                        )
                                                }
                                        )
                                )
                )
                AgentToolSection(
                        title = "Shipping Tools",
                        icon = Icons.Outlined.LocalShipping,
                        color = Color(0xFF06B6D4),
                        tools =
                                listOf(
                                        AgentToolItem(
                                                icon = Icons.Outlined.LocalShipping,
                                                label = "Courier Shipping",
                                                color = Color(0xFF06B6D4),
                                                onClick = {
                                                        context.startActivity(
                                                                Intent(
                                                                        context,
                                                                        com.message.bulksend.aiagent.tools
                                                                                .shipping
                                                                                .CourierShippingActivity::class
                                                                                .java
                                                                )
                                                        )
                                                }
                                        )
                                )
                )
                AgentToolSection(
                        title = "Voice Tools",
                        icon = Icons.Outlined.Mic,
                        color = Color(0xFFEC4899),
                        tools =
                                listOf(
                                        AgentToolItem(
                                                icon = Icons.Outlined.Mic,
                                                label = "Voice Note Reply",
                                                color = Color(0xFFEC4899),
                                                onClick = {
                                                        context.startActivity(
                                                                Intent(
                                                                        context,
                                                                        com.message.bulksend
                                                                                .voicenotereply
                                                                                .VoiceNoteReplyActivity::class
                                                                                .java
                                                                )
                                                        )
                                                }
                                        ),
                                        AgentToolItem(
                                                icon = Icons.Outlined.RecordVoiceOver,
                                                label = "Agent Speech",
                                                color = Color(0xFF10B981),
                                                onClick = {
                                                        context.startActivity(
                                                                Intent(
                                                                        context,
                                                                        com.message.bulksend.aiagent.tools
                                                                                .agentspeech
                                                                                .AgentSpeechActivity::class
                                                                                .java
                                                                )
                                                        )
                                                }
                                        )
                                )
                )
                AgentToolSection(
                        title = "Form Tools",
                        icon = Icons.Outlined.Description,
                        color = Color(0xFFFF5722),
                        tools =
                                listOf(
                                        AgentToolItem(
                                                icon = Icons.Outlined.Description,
                                                label = "Agent Form",
                                                color = Color(0xFFFF5722),
                                                onClick = {
                                                        context.startActivity(
                                                                Intent(
                                                                        context,
                                                                        com.message.bulksend.aiagent.tools
                                                                                .agentform
                                                                                .AgentFormTemplatesActivity::class
                                                                                .java
                                                                )
                                                        )
                                                }
                                        )
                                )
                )
        }
}

@Composable
private fun AgentToolSection(
        title: String,
        icon: ImageVector,
        color: Color,
        tools: List<AgentToolItem>
) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionTitle(title = title, icon = icon, color = color)
                Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                        tools.forEach { tool ->
                                AIAgentStatCard(
                                        icon = tool.icon,
                                        value = "",
                                        label = tool.label,
                                        color = tool.color,
                                        statusBadge = tool.statusBadge,
                                        statusColor = tool.statusColor ?: tool.color,
                                        modifier = Modifier.width(130.dp),
                                        onClick = tool.onClick
                                )
                        }
                }
        }
}

@Composable
fun AIAgentStatCard(
        icon: ImageVector,
        value: String,
        label: String,
        color: Color,
        statusBadge: String? = null,
        statusColor: Color = color,
        modifier: Modifier = Modifier,
        onClick: () -> Unit = {}
) {
        Card(
                modifier = modifier.height(100.dp).clickable(onClick = onClick),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E2E)),
                border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.5f))
        ) {
                Column(
                        modifier = Modifier.fillMaxSize().padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                ) {
                        if (!statusBadge.isNullOrBlank()) {
                                Box(
                                        modifier =
                                                Modifier.clip(RoundedCornerShape(8.dp))
                                                        .background(statusColor.copy(alpha = 0.18f))
                                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                        Text(
                                                statusBadge,
                                                color = statusColor,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                maxLines = 1
                                        )
                                }
                                Spacer(Modifier.height(6.dp))
                        }
                        Icon(icon, null, tint = color, modifier = Modifier.size(28.dp))
                        Spacer(Modifier.height(8.dp))
                        if (value.isNotEmpty()) {
                                Text(
                                        value,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 20.sp,
                                        color = Color.White
                                )
                                Spacer(Modifier.height(4.dp))
                        }
                        Text(
                                label,
                                fontSize = 11.sp,
                                color = Color(0xFFB0B0B0),
                                textAlign = TextAlign.Center,
                                lineHeight = 13.sp,
                                maxLines = 2
                        )
                }
        }
}

@Composable
fun SectionTitle(title: String, icon: ImageVector, color: Color) {
        Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
        ) {
                Box(
                        modifier =
                                Modifier.size(32.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(color.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                ) { Icon(icon, null, tint = color, modifier = Modifier.size(18.dp)) }
                Spacer(Modifier.width(12.dp))
                Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.White)
                Spacer(Modifier.weight(1f))
                Box(
                        modifier =
                                Modifier.weight(1f)
                                        .height(1.dp)
                                        .background(
                                                brush =
                                                        Brush.horizontalGradient(
                                                                colors =
                                                                        listOf(
                                                                                color.copy(
                                                                                        alpha = 0.5f
                                                                                ),
                                                                                Color.Transparent
                                                                        )
                                                        )
                                        )
                )
        }
}

@Composable
fun AIAgentFeaturesGrid() {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                AIAgentFeatureCard(
                        icon = Icons.Outlined.Psychology,
                        title = "Smart Responses",
                        subtitle = "AI-powered intelligent replies",
                        color = Color(0xFF6366F1)
                )
                AIAgentFeatureCard(
                        icon = Icons.Outlined.Language,
                        title = "Multi-Language",
                        subtitle = "Support for multiple languages",
                        color = Color(0xFF10B981)
                )
                AIAgentFeatureCard(
                        icon = Icons.Outlined.School,
                        title = "Custom Training",
                        subtitle = "Train AI with your data",
                        color = Color(0xFFF59E0B)
                )
                AIAgentFeatureCard(
                        icon = Icons.Outlined.Analytics,
                        title = "Analytics",
                        subtitle = "Track performance metrics",
                        color = Color(0xFFEC4899)
                )
        }
}

@Composable
fun AIAgentFeatureCard(icon: ImageVector, title: String, subtitle: String, color: Color) {
        Card(
                modifier = Modifier.fillMaxWidth().clickable {},
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.3f))
        ) {
                Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                ) {
                        Box(
                                modifier =
                                        Modifier.size(48.dp)
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(color.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                        ) { Icon(icon, null, tint = color, modifier = Modifier.size(24.dp)) }
                        Spacer(Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                                Text(
                                        title,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 15.sp,
                                        color = Color.Black
                                )
                                Text(subtitle, fontSize = 12.sp, color = Color.Gray)
                        }
                }
        }
}

@Composable
fun AIAgentQuickActions() {
        Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
                AIAgentQuickActionCard(
                        icon = Icons.Outlined.Settings,
                        title = "Settings",
                        color = Color(0xFF6366F1),
                        modifier = Modifier.weight(1f)
                )
                AIAgentQuickActionCard(
                        icon = Icons.Outlined.History,
                        title = "History",
                        color = Color(0xFF10B981),
                        modifier = Modifier.weight(1f)
                )
        }
}

@Composable
fun AIAgentQuickActionCard(
        icon: ImageVector,
        title: String,
        color: Color,
        modifier: Modifier = Modifier
) {
        Card(
                modifier = modifier.clickable {},
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.3f))
        ) {
                Column(
                        modifier = Modifier.fillMaxWidth().padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                ) {
                        Box(
                                modifier =
                                        Modifier.size(48.dp)
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(color.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                        ) { Icon(icon, null, tint = color, modifier = Modifier.size(24.dp)) }
                        Spacer(Modifier.height(12.dp))
                        Text(
                                title,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp,
                                color = Color.Black
                        )
                }
        }
}
