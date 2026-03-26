package com.message.bulksend.autorespond.ai.ui.customai

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.SettingsSuggest
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private data class ToolCardModel(
    val key: String,
    val icon: ImageVector,
    val title: String,
    val subtitle: String,
    val checked: Boolean,
    val enabled: Boolean = true,
    val accentColor: Color,
    val onCheckedChange: (Boolean) -> Unit
)

@Composable
internal fun CustomAIAgentToolsTab(
    paymentEnabled: Boolean,
    paymentVerificationEnabled: Boolean,
    documentEnabled: Boolean,
    agentFormEnabled: Boolean,
    speechEnabled: Boolean,
    autonomousCatalogueEnabled: Boolean,
    sheetReadEnabled: Boolean,
    sheetWriteEnabled: Boolean,
    onPaymentEnabledChange: (Boolean) -> Unit,
    onPaymentVerificationEnabledChange: (Boolean) -> Unit,
    onDocumentEnabledChange: (Boolean) -> Unit,
    onAgentFormEnabledChange: (Boolean) -> Unit,
    onSpeechEnabledChange: (Boolean) -> Unit,
    onAutonomousCatalogueEnabledChange: (Boolean) -> Unit,
    onSheetReadEnabledChange: (Boolean) -> Unit,
    onSheetWriteEnabledChange: (Boolean) -> Unit,
    googleCalendarEnabled: Boolean,
    onGoogleCalendarEnabledChange: (Boolean) -> Unit,
    googleGmailEnabled: Boolean,
    onGoogleGmailEnabledChange: (Boolean) -> Unit,
    nativeToolCallingEnabled: Boolean,
    onNativeToolCallingEnabledChange: (Boolean) -> Unit,
    continuousAutonomousEnabled: Boolean,
    onContinuousAutonomousEnabledChange: (Boolean) -> Unit,
    autonomousSilenceGapMinutesText: String,
    onAutonomousSilenceGapMinutesTextChange: (String) -> Unit,
    autonomousMaxNudgesPerDayText: String,
    onAutonomousMaxNudgesPerDayTextChange: (String) -> Unit,
    autonomousMaxRoundsText: String,
    onAutonomousMaxRoundsTextChange: (String) -> Unit,
    autonomousMaxQueueText: String,
    onAutonomousMaxQueueTextChange: (String) -> Unit,
    autonomousMaxQueuePerUserText: String,
    onAutonomousMaxQueuePerUserTextChange: (String) -> Unit,
    autonomousMaxGoalsPerRunText: String,
    onAutonomousMaxGoalsPerRunTextChange: (String) -> Unit,
    conversationHistoryLimitText: String,
    onConversationHistoryLimitTextChange: (String) -> Unit,
    runtimeQueueSize: Int,
    runtimeLastHeartbeatAt: Long,
    runtimeLastError: String,
    onOpenNeedDiscovery: () -> Unit
) {
    val toolCards =
        listOf(
            ToolCardModel(
                key = "native-tool-calling",
                icon = Icons.Default.SmartToy,
                title = "Native Tool Calling",
                subtitle = "Enable provider-native function calls for ChatGPT + Gemini",
                checked = nativeToolCallingEnabled,
                accentColor = Color(0xFF0EA5E9),
                onCheckedChange = onNativeToolCallingEnabledChange
            ),
            ToolCardModel(
                key = "continuous-autonomous",
                icon = Icons.Default.SettingsSuggest,
                title = "Continuous Autonomous Mode",
                subtitle = "Event + 15m heartbeat loop for proactive follow-up",
                checked = continuousAutonomousEnabled,
                accentColor = Color(0xFFF97316),
                onCheckedChange = onContinuousAutonomousEnabledChange
            ),
            ToolCardModel(
                key = "payment",
                icon = Icons.Default.Payments,
                title = "Payment Tools",
                subtitle = "QR, payment methods, Razorpay link commands",
                checked = paymentEnabled,
                accentColor = Color(0xFF8B5CF6),
                onCheckedChange = onPaymentEnabledChange
            ),
            ToolCardModel(
                key = "payment-verification",
                icon = Icons.Default.CreditCard,
                title = "Payment Verification",
                subtitle = "Screenshot verification and payment-status checks",
                checked = paymentVerificationEnabled,
                enabled = paymentEnabled,
                accentColor = Color(0xFF06B6D4),
                onCheckedChange = onPaymentVerificationEnabledChange
            ),
            ToolCardModel(
                key = "document",
                icon = Icons.AutoMirrored.Filled.Article,
                title = "Document Tool",
                subtitle = "Allows [SEND_DOCUMENT: ...] execution",
                checked = documentEnabled,
                accentColor = Color(0xFF10B981),
                onCheckedChange = onDocumentEnabledChange
            ),
            ToolCardModel(
                key = "agent-form",
                icon = Icons.Default.Campaign,
                title = "Agent Form Tool",
                subtitle = "Allows [SEND_AGENT_FORM: ...] and status checks",
                checked = agentFormEnabled,
                accentColor = Color(0xFFF59E0B),
                onCheckedChange = onAgentFormEnabledChange
            ),
            ToolCardModel(
                key = "speech",
                icon = Icons.Default.GraphicEq,
                title = "Agent Speech Tool",
                subtitle = "Expose voice-reply capability in prompt context",
                checked = speechEnabled,
                accentColor = Color(0xFFEC4899),
                onCheckedChange = onSpeechEnabledChange
            ),
            ToolCardModel(
                key = "autonomous-catalogue",
                icon = Icons.Default.AutoAwesome,
                title = "Autonomous Catalogue",
                subtitle = "Detect intent and auto-send catalogue media",
                checked = autonomousCatalogueEnabled,
                accentColor = Color(0xFF22C55E),
                onCheckedChange = onAutonomousCatalogueEnabledChange
            ),
            ToolCardModel(
                key = "sheet-read",
                icon = Icons.AutoMirrored.Filled.Article,
                title = "Sheet Read Tool",
                subtitle = "Search configured AI Agent Data Sheet source",
                checked = sheetReadEnabled,
                accentColor = Color(0xFF6366F1),
                onCheckedChange = onSheetReadEnabledChange
            ),
            ToolCardModel(
                key = "sheet-write",
                icon = Icons.AutoMirrored.Filled.ListAlt,
                title = "Sheet Write Tool",
                subtitle = "Allow AI to save data via [WRITE_SHEET: ...]",
                checked = sheetWriteEnabled,
                accentColor = Color(0xFF14B8A6),
                onCheckedChange = onSheetWriteEnabledChange
            ),
            ToolCardModel(
                key = "google-calendar",
                icon = Icons.Default.Event,
                title = "Google Calendar Tool",
                subtitle = "Full control: events, Meet links, tasks, reminders, settings",
                checked = googleCalendarEnabled,
                accentColor = Color(0xFF4285F4),
                onCheckedChange = onGoogleCalendarEnabledChange
            ),
            ToolCardModel(
                key = "google-gmail",
                icon = Icons.Default.Email,
                title = "Google Gmail Tool",
                subtitle = "Full control: emails, threads, drafts, labels, tracking",
                checked = googleGmailEnabled,
                accentColor = Color(0xFFEA4335),
                onCheckedChange = onGoogleGmailEnabledChange
            )
        )

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A2E)),
                border = BorderStroke(1.dp, Color(0xFF2A2A4A))
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        "Tool Permissions",
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "Scroll and control native tools + continuous autonomous runtime.",
                        color = Color(0xFF94A3B8),
                        fontSize = 12.sp
                    )
                }
            }
        }

        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF111827)),
                border = BorderStroke(1.dp, Color(0xFF334155)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text("Autonomous Runtime Status", color = Color.White, fontWeight = FontWeight.SemiBold)
                    Text("Queue Size: $runtimeQueueSize", color = Color(0xFFCBD5E1), fontSize = 12.sp)
                    Text(
                        "Last Heartbeat: ${formatHeartbeatTime(runtimeLastHeartbeatAt)}",
                        color = Color(0xFFCBD5E1),
                        fontSize = 12.sp
                    )
                    if (runtimeLastError.isNotBlank()) {
                        Text("Last Error: $runtimeLastError", color = Color(0xFFFCA5A5), fontSize = 12.sp)
                    }
                }
            }
        }
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0B1324)),
                border = BorderStroke(1.dp, Color(0xFF1D4ED8)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Autonomous Follow-up Control", color = Color.White, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Set delay after user silence. Agent proactive message sirf tab jayega jab goal complete na ho.",
                        color = Color(0xFF93C5FD),
                        fontSize = 12.sp
                    )
                    OutlinedTextField(
                        value = autonomousSilenceGapMinutesText,
                        onValueChange = onAutonomousSilenceGapMinutesTextChange,
                        label = { Text("Delay (minutes)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFF3B82F6),
                            unfocusedBorderColor = Color(0xFF334155),
                            focusedLabelColor = Color(0xFF93C5FD),
                            unfocusedLabelColor = Color(0xFF94A3B8)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text("Default: 2 min", color = Color(0xFF94A3B8), fontSize = 11.sp)
                }
            }
        }

        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF101827)),
                border = BorderStroke(1.dp, Color(0xFF334155)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Autonomous Runtime Limits", color = Color.White, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Safety caps for loop size, retries, nudges and context window.",
                        color = Color(0xFFCBD5E1),
                        fontSize = 12.sp
                    )
                    OutlinedTextField(
                        value = autonomousMaxNudgesPerDayText,
                        onValueChange = onAutonomousMaxNudgesPerDayTextChange,
                        label = { Text("Max Nudges / Day") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFF3B82F6),
                            unfocusedBorderColor = Color(0xFF334155),
                            focusedLabelColor = Color(0xFF93C5FD),
                            unfocusedLabelColor = Color(0xFF94A3B8)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = autonomousMaxRoundsText,
                        onValueChange = onAutonomousMaxRoundsTextChange,
                        label = { Text("Max Rounds / Goal") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFF3B82F6),
                            unfocusedBorderColor = Color(0xFF334155),
                            focusedLabelColor = Color(0xFF93C5FD),
                            unfocusedLabelColor = Color(0xFF94A3B8)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = autonomousMaxQueueText,
                        onValueChange = onAutonomousMaxQueueTextChange,
                        label = { Text("Max Queue (Global)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFF3B82F6),
                            unfocusedBorderColor = Color(0xFF334155),
                            focusedLabelColor = Color(0xFF93C5FD),
                            unfocusedLabelColor = Color(0xFF94A3B8)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = autonomousMaxQueuePerUserText,
                        onValueChange = onAutonomousMaxQueuePerUserTextChange,
                        label = { Text("Max Queue / User") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFF3B82F6),
                            unfocusedBorderColor = Color(0xFF334155),
                            focusedLabelColor = Color(0xFF93C5FD),
                            unfocusedLabelColor = Color(0xFF94A3B8)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = autonomousMaxGoalsPerRunText,
                        onValueChange = onAutonomousMaxGoalsPerRunTextChange,
                        label = { Text("Max Goals / Heartbeat Run") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFF3B82F6),
                            unfocusedBorderColor = Color(0xFF334155),
                            focusedLabelColor = Color(0xFF93C5FD),
                            unfocusedLabelColor = Color(0xFF94A3B8)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = conversationHistoryLimitText,
                        onValueChange = onConversationHistoryLimitTextChange,
                        label = { Text("Conversation History Window") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFF3B82F6),
                            unfocusedBorderColor = Color(0xFF334155),
                            focusedLabelColor = Color(0xFF93C5FD),
                            unfocusedLabelColor = Color(0xFF94A3B8)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                border = BorderStroke(1.dp, Color(0xFF1E3A8A)),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpenNeedDiscovery)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        "Need Discovery Setup",
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "Configure what agent should ask to understand user needs naturally.",
                        color = Color(0xFF93C5FD),
                        fontSize = 12.sp
                    )
                }
            }
        }

        items(
            items = toolCards,
            key = { it.key }
        ) { tool ->
            HorizontalToolToggleCard(
                modifier = Modifier.fillMaxWidth(),
                icon = tool.icon,
                title = tool.title,
                subtitle = tool.subtitle,
                checked = tool.checked,
                enabled = tool.enabled,
                accentColor = tool.accentColor,
                onCheckedChange = tool.onCheckedChange
            )
        }
    }
}

private fun formatHeartbeatTime(value: Long): String {
    if (value <= 0L) return "Never"
    return runCatching {
        SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault()).format(Date(value))
    }.getOrDefault(value.toString())
}

