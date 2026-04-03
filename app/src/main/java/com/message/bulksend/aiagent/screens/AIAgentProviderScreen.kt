package com.message.bulksend.aiagent.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.message.bulksend.autorespond.aireply.AIConfigManager
import com.message.bulksend.autorespond.aireply.AIProvider
import com.message.bulksend.autorespond.aireply.AIReplyManager
import com.message.bulksend.autorespond.aireply.chatspromo.ChatsPromoGeminiService

@Composable
fun AIAgentProviderScreen(onOpenProviderSetup: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val configManager = remember { AIConfigManager(context) }
    val replyManager = remember { AIReplyManager(context) }
    val chatsPromoGeminiService = remember { ChatsPromoGeminiService(context) }

    var selectedProvider by remember { mutableStateOf(replyManager.getSelectedProvider()) }
    var providerConfigs by remember { mutableStateOf(loadProviderConfigs(configManager, chatsPromoGeminiService)) }

    fun refreshProviderState() {
        selectedProvider = replyManager.getSelectedProvider()
        providerConfigs = loadProviderConfigs(configManager, chatsPromoGeminiService)
    }

    DisposableEffect(lifecycleOwner) {
        refreshProviderState()
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshProviderState()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E2E).copy(alpha = 0.92f)),
                border = BorderStroke(1.dp, Color(0xFFF59E0B).copy(alpha = 0.35f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(52.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color(0xFFF59E0B).copy(alpha = 0.16f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Outlined.Key,
                                contentDescription = null,
                                tint = Color(0xFFF59E0B),
                                modifier = Modifier.size(28.dp)
                            )
                        }
                        Column {
                            Text(
                                "AI Provider Setup",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 21.sp
                            )
                            Text(
                                "AutoRespond wali AI key setup screen yahin se open karo.",
                                color = Color(0xFFCBD5E1),
                                fontSize = 13.sp
                            )
                        }
                    }

                    Text(
                        "Selected Provider: ${selectedProvider.displayName}",
                        color = Color(0xFFFFF1C2),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )

                    Button(
                        onClick = onOpenProviderSetup,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF59E0B))
                    ) {
                        Icon(
                            Icons.AutoMirrored.Outlined.OpenInNew,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Text("Open AI Key Setup", modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        }

        item {
            ProviderStatusCard(
                provider = AIProvider.CHATSPROMO,
                summary = providerConfigs.getValue(AIProvider.CHATSPROMO),
                isSelected = selectedProvider == AIProvider.CHATSPROMO
            )
        }

        item {
            ProviderStatusCard(
                provider = AIProvider.GEMINI,
                summary = providerConfigs.getValue(AIProvider.GEMINI),
                isSelected = selectedProvider == AIProvider.GEMINI
            )
        }

        item {
            ProviderStatusCard(
                provider = AIProvider.CHATGPT,
                summary = providerConfigs.getValue(AIProvider.CHATGPT),
                isSelected = selectedProvider == AIProvider.CHATGPT
            )
        }

        item { Spacer(modifier = Modifier.height(8.dp)) }
    }
}

@Composable
private fun ProviderStatusCard(
    provider: AIProvider,
    summary: ProviderSummary,
    isSelected: Boolean
) {
    val accent =
        when (provider) {
            AIProvider.CHATSPROMO -> Color(0xFFF59E0B)
            AIProvider.GEMINI -> Color(0xFF10B981)
            AIProvider.CHATGPT -> Color(0xFF3B82F6)
        }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E2E).copy(alpha = 0.9f)),
        border = BorderStroke(1.dp, accent.copy(alpha = if (isSelected) 0.55f else 0.28f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(accent.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (summary.isConfigured) Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        provider.displayName,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    Text(
                        when (provider) {
                            AIProvider.CHATSPROMO ->
                                if (summary.isConfigured) "Worker connected" else "Worker not configured"
                            else ->
                                if (summary.isConfigured) "API key configured" else "API key missing"
                        },
                        color = if (summary.isConfigured) Color(0xFF86EFAC) else Color(0xFFFCA5A5),
                        fontSize = 12.sp
                    )
                }

                if (isSelected) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(accent.copy(alpha = 0.18f))
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(
                            "Selected",
                            color = accent,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            ProviderInfoRow(title = "Model", value = summary.model)
            ProviderInfoRow(title = "Key", value = if (summary.isConfigured) summary.maskedKey else "Not added")
        }
    }
}

@Composable
private fun ProviderInfoRow(title: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            title,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f),
            fontSize = 12.sp
        )
        Text(
            value,
            color = Color(0xFFE2E8F0),
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private data class ProviderSummary(
    val isConfigured: Boolean,
    val model: String,
    val maskedKey: String
)

private fun loadProviderConfigs(
    configManager: AIConfigManager,
    chatsPromoGeminiService: ChatsPromoGeminiService
): Map<AIProvider, ProviderSummary> {
    return listOf(AIProvider.CHATSPROMO, AIProvider.GEMINI, AIProvider.CHATGPT).associateWith { provider ->
        if (provider == AIProvider.CHATSPROMO) {
            val isConfigured = chatsPromoGeminiService.hasWorkerEndpoint()
            return@associateWith ProviderSummary(
                isConfigured = isConfigured,
                model = if (isConfigured) "Server Managed Gemini" else "Worker not connected",
                maskedKey = if (isConfigured) "Hidden by server" else "Not available"
            )
        }

        val config = configManager.getConfig(provider)
        val rawKey = config.apiKey.trim()
        ProviderSummary(
            isConfigured = rawKey.isNotBlank(),
            model = config.model.ifBlank { provider.defaultModel },
            maskedKey = maskApiKey(rawKey)
        )
    }
}

private fun maskApiKey(rawKey: String): String {
    val cleanKey = rawKey.trim()
    if (cleanKey.isBlank()) return ""
    if (cleanKey.length <= 8) return cleanKey.take(2) + "****"
    return cleanKey.take(4) + "****" + cleanKey.takeLast(4)
}
