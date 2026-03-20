package com.message.bulksend.autorespond.ai.ui.customai

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp

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
    onGoogleGmailEnabledChange: (Boolean) -> Unit
) {
    val toolCards =
        listOf(
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
                subtitle = "Full control: events, Meet links, tasks, task lists, reminders, and calendar settings",
                checked = googleCalendarEnabled,
                accentColor = Color(0xFF4285F4),
                onCheckedChange = onGoogleCalendarEnabledChange
            ),
            ToolCardModel(
                key = "google-gmail",
                icon = Icons.Default.Email,
                title = "Google Gmail Tool",
                subtitle = "Full control: emails, threads, drafts, labels, attachments, auto-tracking, and TableSheet history sync",
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
                colors =
                    CardDefaults.cardColors(
                        containerColor = Color(0xFF1A1A2E)
                    ),
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
                        "Scroll vertically and turn tools ON/OFF.",
                        color = Color(0xFF94A3B8),
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
