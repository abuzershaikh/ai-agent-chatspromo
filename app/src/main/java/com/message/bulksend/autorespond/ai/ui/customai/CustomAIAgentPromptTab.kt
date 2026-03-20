package com.message.bulksend.autorespond.ai.ui.customai

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun CustomAIAgentPromptTab(
    templateName: String,
    templateGoal: String,
    templateTone: String,
    templateInstructions: String,
    promptMode: String,
    taskModeEnabled: Boolean,
    onTemplateNameChange: (String) -> Unit,
    onTemplateGoalChange: (String) -> Unit,
    onTemplateToneChange: (String) -> Unit,
    onTemplateInstructionsChange: (String) -> Unit,
    onPromptModeChange: (String) -> Unit,
    onOpenStepFlow: () -> Unit
) {
    val simpleMode =
        com.message.bulksend.autorespond.ai.settings.AIAgentSettingsManager.PROMPT_MODE_SIMPLE
    val stepFlowMode =
        com.message.bulksend.autorespond.ai.settings.AIAgentSettingsManager.PROMPT_MODE_STEP_FLOW
    val isStepFlowSelected = promptMode == stepFlowMode

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0F1E)),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // ── Header ──────────────────────────────────────────────────────────
        item {
            PromptHeaderCard(pulseAlpha)
        }

        // ── Mode Selector ────────────────────────────────────────────────────
        item {
            ModeLabel()
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                PromptModeGridCard(
                    icon = Icons.Outlined.AutoFixHigh,
                    title = "Simple",
                    subtitle = "Regular prompt builder",
                    selected = !isStepFlowSelected,
                    accentColor = Color(0xFF38BDF8),
                    gradientColors = listOf(Color(0xFF0EA5E9), Color(0xFF38BDF8)),
                    onClick = { onPromptModeChange(simpleMode) },
                    modifier = Modifier.weight(1f)
                )
                PromptModeGridCard(
                    icon = Icons.Outlined.AccountTree,
                    title = "Flow",
                    subtitle = "Step-by-step task activity",
                    selected = isStepFlowSelected,
                    accentColor = Color(0xFF22C55E),
                    gradientColors = listOf(Color(0xFF16A34A), Color(0xFF22C55E)),
                    onClick = {
                        onPromptModeChange(stepFlowMode)
                        onOpenStepFlow()
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // ── Content Card ──────────────────────────────────────────────────────
        item {
            AnimatedVisibility(
                visible = !isStepFlowSelected,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                SimplePromptFormCard(
                    templateName = templateName,
                    templateGoal = templateGoal,
                    templateTone = templateTone,
                    templateInstructions = templateInstructions,
                    onTemplateNameChange = onTemplateNameChange,
                    onTemplateGoalChange = onTemplateGoalChange,
                    onTemplateToneChange = onTemplateToneChange,
                    onTemplateInstructionsChange = onTemplateInstructionsChange
                )
            }

            AnimatedVisibility(
                visible = isStepFlowSelected,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                FlowModeInfoCard(
                    taskModeEnabled = taskModeEnabled,
                    onOpenStepFlow = onOpenStepFlow
                )
            }
        }

        // ── Bottom spacer ─────────────────────────────────────────────────────
        item { Spacer(modifier = Modifier.height(24.dp)) }
    }
}

// ─── Header Card ─────────────────────────────────────────────────────────────
@Composable
private fun PromptHeaderCard(pulseAlpha: Float) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFF0F172A), Color(0xFF1E1B4B), Color(0xFF0F172A))
                )
            )
            .border(
                width = 1.5.dp,
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        Color(0xFF6366F1).copy(alpha = pulseAlpha),
                        Color(0xFF38BDF8).copy(alpha = pulseAlpha * 0.7f),
                        Color(0xFF6366F1).copy(alpha = pulseAlpha)
                    )
                ),
                shape = RoundedCornerShape(24.dp)
            )
            .padding(24.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color(0xFF6366F1).copy(alpha = 0.5f),
                                Color(0xFF4338CA).copy(alpha = 0.2f)
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Outlined.Psychology,
                    contentDescription = null,
                    tint = Color(0xFF818CF8),
                    modifier = Modifier.size(34.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Prompt Builder",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.3.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Craft your AI agent's personality & instructions",
                    color = Color(0xFF94A3B8),
                    fontSize = 12.sp,
                    lineHeight = 17.sp
                )
            }
        }
    }
}

// ─── Section Label ────────────────────────────────────────────────────────────
@Composable
private fun ModeLabel() {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(4.dp, 18.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xFF6366F1), Color(0xFF38BDF8))
                    )
                )
        )
        Text(
            text = "Select Mode",
            color = Color(0xFFCBD5E1),
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.5.sp
        )
    }
}

// ─── Mode Card ───────────────────────────────────────────────────────────────
@Composable
private fun PromptModeGridCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    selected: Boolean,
    accentColor: Color,
    gradientColors: List<Color>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val borderBrush = if (selected)
        Brush.linearGradient(gradientColors)
    else
        Brush.linearGradient(listOf(Color(0xFF334155), Color(0xFF334155)))

    Box(
        modifier = modifier
            .height(160.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(
                if (selected)
                    Brush.linearGradient(
                        colors = listOf(
                            accentColor.copy(alpha = 0.18f),
                            accentColor.copy(alpha = 0.08f)
                        )
                    )
                else
                    Brush.linearGradient(listOf(Color(0xFF1E293B), Color(0xFF0F172A)))
            )
            .border(
                width = if (selected) 1.5.dp else 1.dp,
                brush = borderBrush,
                shape = RoundedCornerShape(20.dp)
            )
            .clickable(onClick = onClick)
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top: icon + badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            if (selected)
                                Brush.radialGradient(
                                    listOf(accentColor.copy(0.35f), accentColor.copy(0.15f))
                                )
                            else
                                Brush.radialGradient(
                                    listOf(Color(0xFF334155), Color(0xFF1E293B))
                                )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = if (selected) accentColor else Color(0xFF64748B),
                        modifier = Modifier.size(24.dp)
                    )
                }

                if (selected) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Brush.horizontalGradient(gradientColors))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            "ACTIVE",
                            color = Color.White,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.8.sp
                        )
                    }
                }
            }

            // Bottom: title + subtitle
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = title,
                    color = if (selected) Color.White else Color(0xFF94A3B8),
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = subtitle,
                    color = if (selected) accentColor.copy(0.85f) else Color(0xFF475569),
                    fontSize = 11.sp,
                    lineHeight = 15.sp
                )
            }
        }
    }
}

// ─── Simple Prompt Form ────────────────────────────────────────────────────────
@Composable
private fun SimplePromptFormCard(
    templateName: String,
    templateGoal: String,
    templateTone: String,
    templateInstructions: String,
    onTemplateNameChange: (String) -> Unit,
    onTemplateGoalChange: (String) -> Unit,
    onTemplateToneChange: (String) -> Unit,
    onTemplateInstructionsChange: (String) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFF0F172A), Color(0xFF1E1B4B).copy(0.6f))
                )
            )
            .border(
                width = 1.dp,
                color = Color(0xFF334155),
                shape = RoundedCornerShape(24.dp)
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // Section heading
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF38BDF8).copy(0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Outlined.Edit,
                        contentDescription = null,
                        tint = Color(0xFF38BDF8),
                        modifier = Modifier.size(20.dp)
                    )
                }
                Column {
                    Text(
                        "Simple Prompt",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Configure your agent's core behavior",
                        color = Color(0xFF64748B),
                        fontSize = 11.sp
                    )
                }
            }

            HorizontalDivider(color = Color(0xFF1E293B), thickness = 1.dp)

            // Fields
            BeautifulField(
                icon = Icons.Outlined.Badge,
                label = "Template Name",
                hint = "E.g. Sales Assistant, Support Bot…",
                value = templateName,
                onValueChange = onTemplateNameChange,
                accentColor = Color(0xFF818CF8),
                singleLine = true
            )

            BeautifulField(
                icon = Icons.Outlined.TrackChanges,
                label = "Primary Goal",
                hint = "What should the agent achieve?",
                value = templateGoal,
                onValueChange = onTemplateGoalChange,
                accentColor = Color(0xFF34D399),
                minLines = 2,
                maxLines = 4
            )

            BeautifulField(
                icon = Icons.Outlined.RecordVoiceOver,
                label = "Tone",
                hint = "E.g. Polite, Direct, Friendly, Professional…",
                value = templateTone,
                onValueChange = onTemplateToneChange,
                accentColor = Color(0xFFFBBF24),
                singleLine = true
            )

            BeautifulField(
                icon = Icons.Outlined.Notes,
                label = "Custom Instructions",
                hint = "Detailed rules & context for the agent…",
                value = templateInstructions,
                onValueChange = onTemplateInstructionsChange,
                accentColor = Color(0xFFF472B6),
                minLines = 6,
                maxLines = 12
            )

            // Tips row
            TipsRow()
        }
    }
}

@Composable
private fun BeautifulField(
    icon: ImageVector,
    label: String,
    hint: String,
    value: String,
    onValueChange: (String) -> Unit,
    accentColor: Color,
    singleLine: Boolean = false,
    minLines: Int = 1,
    maxLines: Int = 1
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(15.dp)
            )
            Text(
                text = label,
                color = accentColor,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.4.sp
            )
        }

        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = {
                Text(
                    hint,
                    color = Color(0xFF94A3B8),  // clearly visible light-grey hint
                    fontSize = 13.sp
                )
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = singleLine,
            minLines = minLines,
            maxLines = maxLines,
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor   = Color.White,
                unfocusedTextColor = Color(0xFFE2E8F0),   // bright enough when not focused
                focusedBorderColor   = accentColor,
                unfocusedBorderColor = Color(0xFF334155),
                focusedContainerColor   = Color(0xFF1E293B),  // solid — no alpha issues
                unfocusedContainerColor = Color(0xFF0F172A),  // solid dark background
                cursorColor = accentColor,
                focusedPlaceholderColor   = Color(0xFF94A3B8),
                unfocusedPlaceholderColor = Color(0xFF94A3B8)
            ),
            textStyle = LocalTextStyle.current.copy(fontSize = 14.sp, color = Color.White)
        )
    }
}

@Composable
private fun TipsRow() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF38BDF8).copy(0.07f))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            Icons.Outlined.Lightbulb,
            contentDescription = null,
            tint = Color(0xFFFBBF24),
            modifier = Modifier.size(16.dp)
        )
        Text(
            text = "Tip: Be specific with instructions — the AI follows them precisely.",
            color = Color(0xFF94A3B8),
            fontSize = 11.sp,
            lineHeight = 16.sp,
            modifier = Modifier.weight(1f)
        )
    }
}

// ─── Flow Mode Info Card ───────────────────────────────────────────────────────
@Composable
private fun FlowModeInfoCard(
    taskModeEnabled: Boolean,
    onOpenStepFlow: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFF052E16).copy(0.9f), Color(0xFF0F172A))
                )
            )
            .border(
                width = 1.5.dp,
                brush = Brush.linearGradient(
                    listOf(Color(0xFF22C55E).copy(0.6f), Color(0xFF16A34A).copy(0.3f))
                ),
                shape = RoundedCornerShape(24.dp)
            )
            .padding(22.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFF22C55E).copy(0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Outlined.AccountTree,
                        contentDescription = null,
                        tint = Color(0xFF22C55E),
                        modifier = Modifier.size(28.dp)
                    )
                }
                Column {
                    Text(
                        "Flow Mode Active",
                        color = Color.White,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Step-by-step task orchestration",
                        color = Color(0xFF4ADE80),
                        fontSize = 12.sp
                    )
                }
            }

            HorizontalDivider(color = Color(0xFF14532D), thickness = 1.dp)

            Text(
                "Use the Flow activity to define sequential tasks, runtime conditions, and automated progressions for your agent.",
                color = Color(0xFF86EFAC),
                fontSize = 13.sp,
                lineHeight = 19.sp
            )

            // Task Mode Badge
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (taskModeEnabled)
                                Color(0xFF22C55E).copy(0.2f)
                            else
                                Color(0xFFEF4444).copy(0.2f)
                        )
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Text(
                        "Task Mode: ${if (taskModeEnabled) "ON" else "OFF"}",
                        color = if (taskModeEnabled) Color(0xFF4ADE80) else Color(0xFFF87171),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Button(
                onClick = onOpenStepFlow,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF22C55E)
                ),
                contentPadding = PaddingValues(vertical = 14.dp)
            ) {
                Icon(
                    Icons.Outlined.OpenInNew,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "Open Flow Activity",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = Color.White
                )
            }
        }
    }
}
