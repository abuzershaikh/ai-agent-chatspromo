package com.message.bulksend.aiagent.screens

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import com.message.bulksend.autorespond.ai.settings.AIAgentSettingsManager
import com.message.bulksend.autorespond.ai.ui.customai.CustomAIAgentActivity
import com.message.bulksend.autorespond.ai.ui.templates.ClinicConfigActivity

@Composable
fun AITemplatesScreen() {
    val context = LocalContext.current
    val settingsManager = remember { AIAgentSettingsManager(context) }
    val isCustomTemplateActive =
        settingsManager.activeTemplate.equals("CUSTOM", ignoreCase = true)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header Card
        item {
            TemplatesHeaderCard()
        }

        // Custom Templates
        item {
            TemplateSectionTitle(
                title = "Custom Templates",
                icon = Icons.Outlined.Edit,
                color = Color(0xFFF59E0B)
            )
        }

        item {
            CustomTemplateCard(
                isActive = isCustomTemplateActive,
                onClick = {
                    context.startActivity(Intent(context, CustomAIAgentActivity::class.java))
                }
            )
        }

        // Clinic Template
        item {
            TemplateSectionTitle(
                title = "Clinic Template",
                icon = Icons.Outlined.LocalHospital,
                color = Color(0xFFEF4444)
            )
        }

        item {
            TemplateCard(
                icon = Icons.Outlined.LocalHospital,
                title = "Clinic Appointment",
                subtitle = "Doctor booking & management",
                color = Color(0xFFEF4444),
                isActive = false,
                onClick = {
                    context.startActivity(Intent(context, ClinicConfigActivity::class.java))
                }
            )
        }

        // Bottom Spacer
        item {
            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@Composable
fun TemplatesHeaderCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E2E).copy(alpha = 0.9f)),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.3f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Outlined.Dashboard,
                contentDescription = "Templates",
                tint = Color(0xFF10B981),
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                "AI Templates",
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Pre-configured AI templates for different business needs",
                fontSize = 13.sp,
                color = Color(0xFF94A3B8),
                textAlign = TextAlign.Center,
                lineHeight = 18.sp
            )
        }
    }
}

@Composable
fun TemplateSectionTitle(title: String, icon: ImageVector, color: Color) {
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
fun TemplateCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    color: Color,
    isActive: Boolean,
    onClick: () -> Unit = {}
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) color.copy(alpha = 0.15f) else Color(0xFF1E1E2E).copy(alpha = 0.9f)
        ),
        border = androidx.compose.foundation.BorderStroke(
            width = if (isActive) 2.dp else 1.dp,
            color = color.copy(alpha = if (isActive) 0.6f else 0.3f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(color.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = color, modifier = Modifier.size(28.dp))
            }
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.White)
                Spacer(Modifier.height(4.dp))
                Text(subtitle, fontSize = 12.sp, color = Color(0xFF94A3B8), lineHeight = 16.sp)
            }
            if (isActive) {
                Icon(
                    Icons.Outlined.CheckCircle,
                    contentDescription = "Active",
                    tint = color,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

@Composable
fun CustomTemplateCard(
    isActive: Boolean,
    onClick: () -> Unit
) {
    val accent = Color(0xFF2563EB) // Blue color
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 180.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E2E).copy(alpha = 0.95f)),
        border = androidx.compose.foundation.BorderStroke(2.dp, accent.copy(alpha = 0.6f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color(0xFF1E293B),
                            Color(0xFF1E1E2E),
                            Color(0xFF1A1A2E)
                        )
                    )
                )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(accent.copy(alpha = 0.2f))
                    .padding(horizontal = 24.dp, vertical = 10.dp)
            ) {
                Text(
                    text = "CREATE CUSTOM AGENT",
                    color = Color(0xFFBFDBFE),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(68.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(
                                brush = Brush.verticalGradient(
                                    colors = listOf(
                                        accent.copy(alpha = 0.3f),
                                        accent.copy(alpha = 0.15f)
                                    )
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Outlined.SmartToy,
                            contentDescription = "AI Agent",
                            tint = accent,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                    Spacer(Modifier.width(18.dp))
                    Text(
                        if (isActive) "Active now - tap to edit settings" else "Build your own AI agent from scratch",
                        modifier = Modifier.weight(1f),
                        fontSize = 13.sp,
                        color = Color(0xFFB0B0B0),
                        lineHeight = 18.sp
                    )
                }

                HorizontalDivider(
                    color = accent.copy(alpha = 0.3f),
                    thickness = 1.dp
                )

                Text(
                    "Design prompts, tools, and workflows in one place for your exact business use case.",
                    fontSize = 13.sp,
                    color = Color(0xFF94A3B8),
                    lineHeight = 19.sp
                )

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CustomTemplateTag(label = "No-Code", color = accent)
                    CustomTemplateTag(label = "Fast Setup", color = accent)
                    CustomTemplateTag(label = "Flexible", color = accent)
                }
            }
        }
    }
}

@Composable
private fun CustomTemplateTag(label: String, color: Color = Color(0xFF2563EB)) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(color.copy(alpha = 0.15f))
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            text = label,
            color = Color(0xFF93C5FD),
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}
