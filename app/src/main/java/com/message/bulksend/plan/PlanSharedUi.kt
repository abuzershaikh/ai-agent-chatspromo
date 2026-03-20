package com.message.bulksend.plan

import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class PlanSection(
    val shortLabel: String,
    val title: String,
    val subtitle: String,
    val accent: Color,
    val icon: ImageVector,
    val defaultTabNote: String,
    val defaultActionHint: String
) {
    CORE(
        shortLabel = "Monthly",
        title = "Monthly Plan",
        subtitle = "30-day access",
        accent = Color(0xFFF59E0B),
        icon = Icons.Filled.RocketLaunch,
        defaultTabNote = "Rs 299",
        defaultActionHint = "Tap to choose"
    ),
    AUTOREPLY(
        shortLabel = "Yearly",
        title = "Yearly Plan",
        subtitle = "Best annual value",
        accent = Color(0xFFFB7185),
        icon = Icons.Filled.Chat,
        defaultTabNote = "Rs 1499",
        defaultActionHint = "Tap to choose"
    ),
    CRM(
        shortLabel = "Lifetime",
        title = "Lifetime Plan",
        subtitle = "One-time access",
        accent = Color(0xFF10B981),
        icon = Icons.Filled.Groups,
        defaultTabNote = "Rs 2999",
        defaultActionHint = "Tap to choose"
    ),
    AI_AGENT(
        shortLabel = "Agent",
        title = "AI Agent",
        subtitle = "Separate pricing",
        accent = Color(0xFF38BDF8),
        icon = Icons.Filled.AutoAwesome,
        defaultTabNote = "Rs 499",
        defaultActionHint = "Tap to buy"
    )
}

fun switchPlanSection(activity: ComponentActivity, target: PlanSection) {
    if (isCurrentPlanScreen(activity, target)) {
        return
    }

    val intent = Intent(activity, activityForPlanSection(target))
    activity.startActivity(intent)
    activity.finish()
}

private fun isCurrentPlanScreen(activity: ComponentActivity, target: PlanSection): Boolean {
    return when (target) {
        PlanSection.CORE -> activity is PrepackActivity
        PlanSection.AUTOREPLY -> activity is AutoReplyPlanActivity
        PlanSection.CRM -> activity is CRMPlanActivity
        PlanSection.AI_AGENT -> activity is AIAgentPlanActivity
    }
}

private fun activityForPlanSection(target: PlanSection): Class<out ComponentActivity> {
    return when (target) {
        PlanSection.CORE -> PrepackActivity::class.java
        PlanSection.AUTOREPLY -> AutoReplyPlanActivity::class.java
        PlanSection.CRM -> CRMPlanActivity::class.java
        PlanSection.AI_AGENT -> AIAgentPlanActivity::class.java
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlanScreenScaffold(
    section: PlanSection,
    title: String,
    subtitle: String,
    gradient: List<Color>,
    onBackPressed: () -> Unit,
    onSectionSelected: (PlanSection) -> Unit,
    tabNoteOverrides: Map<PlanSection, String> = emptyMap(),
    onCurrentSectionAction: (() -> Unit)? = null,
    currentActionLabel: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val backgroundBrush = Brush.verticalGradient(gradient)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundBrush)
    ) {
        // completely white background, no decorative layer

        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = title,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = subtitle,
                                color = Color.White.copy(alpha = 0.72f),
                                fontSize = 12.sp
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBackPressed) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = Color.White
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent
                    )
                )
            },
            bottomBar = {
                PlanBottomSwitcher(
                    selected = section,
                    onSectionSelected = onSectionSelected,
                    tabNoteOverrides = tabNoteOverrides,
                    onCurrentSectionAction = onCurrentSectionAction,
                    currentActionLabel = currentActionLabel
                )
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 18.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Spacer(modifier = Modifier.height(6.dp))
                content()
                Spacer(modifier = Modifier.height(148.dp))
            }
        }
    }
}

@Composable
private fun DecorativeBackground(accent: Color) {
    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .padding(start = 220.dp, top = 84.dp)
                .size(180.dp)
                .clip(CircleShape)
                .background(accent.copy(alpha = 0.16f))
        )
        Box(
            modifier = Modifier
                .padding(start = 12.dp, top = 220.dp)
                .size(132.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.06f))
        )
        Box(
            modifier = Modifier
                .padding(start = 250.dp, top = 560.dp)
                .size(160.dp)
                .clip(CircleShape)
                .background(accent.copy(alpha = 0.1f))
        )
    }
}

@Composable
private fun PlanBottomSwitcher(
    selected: PlanSection,
    onSectionSelected: (PlanSection) -> Unit,
    tabNoteOverrides: Map<PlanSection, String>,
    onCurrentSectionAction: (() -> Unit)?,
    currentActionLabel: String?
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        color = Color(0xFF07101D).copy(alpha = 0.94f),
        shape = RoundedCornerShape(32.dp),
        tonalElevation = 12.dp,
        shadowElevation = 20.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 10.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            PlanSection.values().forEach { section ->
                val isSelected = selected == section
                val border = if (isSelected) {
                    section.accent.copy(alpha = 0.75f)
                } else {
                    Color.White.copy(alpha = 0.08f)
                }
                val tabNote = tabNoteOverrides[section] ?: section.defaultTabNote
                val onClick = {
                    if (isSelected && onCurrentSectionAction != null) {
                        onCurrentSectionAction()
                    } else {
                        onSectionSelected(section)
                    }
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .height(110.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(
                            brush = if (isSelected) {
                                Brush.verticalGradient(
                                    listOf(
                                        section.accent.copy(alpha = 0.3f),
                                        Color(0xFF122033)
                                    )
                                )
                            } else {
                                Brush.verticalGradient(
                                    listOf(
                                        Color.White.copy(alpha = 0.06f),
                                        Color.White.copy(alpha = 0.04f)
                                    )
                                )
                            }
                        )
                        .border(
                            width = 1.dp,
                            color = border,
                            shape = RoundedCornerShape(20.dp)
                        )
                        .clickable(onClick = onClick)
                        .padding(vertical = 10.dp, horizontal = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        color = if (isSelected) {
                            Color.White.copy(alpha = 0.14f)
                        } else {
                            Color.White.copy(alpha = 0.08f)
                        },
                        shape = CircleShape
                    ) {
                        Box(
                            modifier = Modifier.size(34.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = section.icon,
                                contentDescription = section.title,
                                tint = if (isSelected) Color.White else Color.White.copy(alpha = 0.76f),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    Text(
                        text = section.shortLabel,
                        color = if (isSelected) Color.White else Color.White.copy(alpha = 0.74f),
                        fontSize = 13.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                    )
                    Text(
                        text = tabNote,
                        color = if (isSelected) {
                            Color.White.copy(alpha = 0.92f)
                        } else {
                            Color.White.copy(alpha = 0.62f)
                        },
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (isSelected && onCurrentSectionAction != null) {
                        Surface(
                            color = Color.White.copy(alpha = 0.18f),
                            shape = RoundedCornerShape(50)
                        ) {
                            Text(
                                text = currentActionLabel ?: section.defaultActionHint,
                                color = Color.White,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                    }
                }
            }
            
            // Added button as requested
            Button(
                onClick = { /* Action */ },
                modifier = Modifier.padding(start = 8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E88E5))
            ) {
                Text("Select", color = Color.White)
            }
        }
    }
}

@Composable
fun PlanHeroCard(
    badge: String,
    title: String,
    description: String,
    accent: Color,
    metrics: List<Pair<String, String>>
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF0E1728).copy(alpha = 0.9f)
        ),
        shape = RoundedCornerShape(32.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Surface(
                color = accent.copy(alpha = 0.16f),
                shape = RoundedCornerShape(50)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.Star,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = badge,
                        color = accent,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Text(
                text = title,
                color = Color.White,
                fontSize = 28.sp,
                fontWeight = FontWeight.Black,
                lineHeight = 32.sp
            )

            Text(
                text = description,
                color = Color(0xFFB8C4D6),
                fontSize = 14.sp,
                lineHeight = 20.sp
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                metrics.forEach { (value, label) ->
                    Surface(
                        modifier = Modifier.weight(1f),
                        color = Color.White.copy(alpha = 0.05f),
                        shape = RoundedCornerShape(22.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = value,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            )
                            Text(
                                text = label,
                                color = Color(0xFF9FB0C7),
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PlanTierCard(
    title: String,
    price: String,
    period: String,
    accent: Color,
    description: String,
    features: List<String>,
    badge: String? = null,
    originalPrice: String? = null,
    buttonText: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFF8FBFF)
        ),
        shape = RoundedCornerShape(26.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = title,
                        color = Color(0xFF0F172A),
                        fontSize = 21.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text = description,
                        color = Color(0xFF54657E),
                        fontSize = 13.sp
                    )
                }
                if (badge != null) {
                    Surface(
                        color = accent.copy(alpha = 0.14f),
                        shape = RoundedCornerShape(50)
                    ) {
                        Text(
                            text = badge,
                            color = accent,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                        )
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = price,
                    color = Color(0xFF0F172A),
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Black
                )
                Text(
                    text = period,
                    color = Color(0xFF64748B),
                    fontSize = 13.sp,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
                if (!originalPrice.isNullOrBlank()) {
                    Text(
                        text = originalPrice,
                        color = Color(0xFF94A3B8),
                        fontSize = 14.sp,
                        textDecoration = TextDecoration.LineThrough,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                features.forEach { feature ->
                    PlanFeatureRow(
                        text = feature,
                        accent = accent
                    )
                }
            }

            Button(
                onClick = onClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = accent),
                shape = RoundedCornerShape(18.dp)
            ) {
                Text(
                    text = buttonText,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }
        }
    }
}

@Composable
fun PlanFeatureRow(
    text: String,
    accent: Color,
    textColor: Color = Color(0xFF334155)
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(accent.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "+",
                color = accent,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
        }
        Text(
            text = text,
            color = textColor,
            fontSize = 14.sp
        )
    }
}

@Composable
fun PlanDetailCard(
    title: String,
    description: String,
    accent: Color,
    features: List<String>,
    footnote: String? = null
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color.White.copy(alpha = 0.08f)
        ),
        shape = RoundedCornerShape(26.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = title,
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = description,
                color = Color(0xFFD3DDEC),
                fontSize = 13.sp,
                lineHeight = 19.sp
            )
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                features.forEach { feature ->
                    PlanFeatureRow(
                        text = feature,
                        accent = accent,
                        textColor = Color(0xFFDCE7F5)
                    )
                }
            }
            if (!footnote.isNullOrBlank()) {
                Surface(
                    color = accent.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(50)
                ) {
                    Text(
                        text = footnote,
                        color = accent,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun PlanSupportCard(
    title: String,
    description: String,
    accent: Color
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color.White.copy(alpha = 0.08f)
        ),
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = title,
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = description,
                color = Color(0xFFD2DEEE),
                fontSize = 13.sp,
                lineHeight = 19.sp
            )
            Surface(
                color = accent.copy(alpha = 0.16f),
                shape = RoundedCornerShape(50)
            ) {
                Text(
                    text = "Priority assistance",
                    color = accent,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                )
            }
        }
    }
}

@Composable
fun PlanPrimaryButton(
    text: String,
    accent: Color,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
        colors = ButtonDefaults.buttonColors(containerColor = accent),
        shape = RoundedCornerShape(18.dp)
    ) {
        Text(
            text = text,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp
        )
    }
}

@Composable
fun PlanSecondaryButton(
    text: String,
    accent: Color,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.White.copy(alpha = 0.08f),
            contentColor = Color.White
        ),
        shape = RoundedCornerShape(18.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(
            text = text,
            color = accent,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp
        )
    }
}

data class PlanChoiceOption(
    val id: String,
    val title: String,
    val price: String,
    val period: String,
    val description: String,
    val badge: String? = null
)

@Composable
fun PlanOptionSelectionDialog(
    title: String,
    accent: Color,
    options: List<PlanChoiceOption>,
    onDismiss: () -> Unit,
    onOptionSelected: (String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF0D1525),
        shape = RoundedCornerShape(28.dp),
        title = {
            Text(
                text = title,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 21.sp
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                options.forEach { option ->
                    PlanOptionChoiceCard(
                        option = option,
                        accent = accent,
                        onClick = { onOptionSelected(option.id) }
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = "Close",
                    color = Color(0xFF94A3B8)
                )
            }
        }
    )
}

@Composable
private fun PlanOptionChoiceCard(
    option: PlanChoiceOption,
    accent: Color,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = Color.White.copy(alpha = 0.06f)
        ),
        shape = RoundedCornerShape(22.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = option.title,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp
                )
                if (option.badge != null) {
                    Surface(
                        color = accent.copy(alpha = 0.16f),
                        shape = RoundedCornerShape(50)
                    ) {
                        Text(
                            text = option.badge,
                            color = accent,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            Text(
                text = option.description,
                color = Color(0xFFD3DDEC),
                fontSize = 12.sp,
                lineHeight = 18.sp
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                Text(
                    text = option.price,
                    color = Color.White,
                    fontWeight = FontWeight.Black,
                    fontSize = 24.sp
                )
                Text(
                    text = option.period,
                    color = Color(0xFF9FB0C7),
                    fontSize = 12.sp,
                    modifier = Modifier.padding(bottom = 3.dp)
                )
            }
        }
    }
}

@Composable
fun PlanPaymentChoiceDialog(
    title: String,
    price: String,
    accent: Color,
    showOnlyRazorpay: Boolean,
    onDismiss: () -> Unit,
    onRazorpay: () -> Unit,
    onPlayStore: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        shape = RoundedCornerShape(32.dp),
        title = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Glow ring + icon
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .background(
                            brush = Brush.radialGradient(
                                listOf(
                                    accent.copy(alpha = 0.16f),
                                    accent.copy(alpha = 0.05f),
                                    Color.Transparent
                                )
                            ),
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .background(accent.copy(alpha = 0.2f), CircleShape)
                            .border(1.5.dp, accent.copy(alpha = 0.5f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Lock,
                            contentDescription = null,
                            tint = accent,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }

                Text(
                    text = title,
                    color = Color(0xFF0F172A),
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 20.sp,
                    textAlign = TextAlign.Center
                )

                // Price chip
                Surface(
                    color = accent.copy(alpha = 0.18f),
                    shape = RoundedCornerShape(50)
                ) {
                    Text(
                        text = price,
                        color = accent,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 7.dp)
                    )
                }

                Text(
                    text = "Choose your payment method",
                    color = Color(0xFF64748B),
                    fontSize = 12.sp
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Razorpay Card
                RazorpayPaymentCard(onClick = onRazorpay)
                if (!showOnlyRazorpay) {
                    // Play Store Card
                    PlayStorePaymentCard(onClick = onPlayStore)
                }

                // Security note
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.Lock,
                        contentDescription = null,
                        tint = Color(0xFF64748B),
                        modifier = Modifier.size(11.dp)
                    )
                    Spacer(modifier = Modifier.size(4.dp))
                    Text(
                        text = "256-bit SSL encrypted & secure",
                        color = Color(0xFF64748B),
                        fontSize = 11.sp
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = "Cancel",
                    color = Color(0xFF334155),
                    fontWeight = FontWeight.Medium
                )
            }
        }
    )
}

@Composable
private fun RazorpayPaymentCard(onClick: () -> Unit) {
    val razorpayOrange = Color(0xFFFF6B35)
    val cardBackground = Color.White
    val cardBorder = razorpayOrange.copy(alpha = 0.35f)
    val titleColor = Color(0xFF111827)
    val subtitleColor = Color(0xFF64748B)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(cardBackground)
            .border(
                width = 1.5.dp,
                color = cardBorder,
                shape = RoundedCornerShape(22.dp)
            )
            .clickable(onClick = onClick)
            .padding(18.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Icon box
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(
                        color = razorpayOrange.copy(alpha = 0.10f),
                        shape = RoundedCornerShape(14.dp)
                    )
                    .border(1.dp, razorpayOrange.copy(alpha = 0.22f), RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.CreditCard,
                    contentDescription = null,
                    tint = razorpayOrange,
                    modifier = Modifier.size(26.dp)
                )
            }

            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Pay with Razorpay",
                    color = titleColor,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 15.sp
                )
                Text(
                    text = "UPI, cards and net banking",
                    color = subtitleColor,
                    fontSize = 12.sp
                )
            }

            Icon(
                imageVector = Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = razorpayOrange,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

@Composable
private fun PlayStorePaymentCard(onClick: () -> Unit) {
    val googleGreen = Color(0xFF01875F)
    val cardBackground = Color.White
    val cardBorder = googleGreen.copy(alpha = 0.35f)
    val titleColor = Color(0xFF111827)
    val subtitleColor = Color(0xFF64748B)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(cardBackground)
            .border(
                width = 1.5.dp,
                color = cardBorder,
                shape = RoundedCornerShape(22.dp)
            )
            .clickable(onClick = onClick)
            .padding(18.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Icon box
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(
                        color = googleGreen.copy(alpha = 0.10f),
                        shape = RoundedCornerShape(14.dp)
                    )
                    .border(1.dp, googleGreen.copy(alpha = 0.22f), RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.ShoppingCart,
                    contentDescription = null,
                    tint = googleGreen,
                    modifier = Modifier.size(26.dp)
                )
            }

            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Pay with Play Store",
                    color = titleColor,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 15.sp
                )
                Text(
                    text = "Google Play billing with saved payment methods",
                    color = subtitleColor,
                    fontSize = 12.sp
                )
            }

            Icon(
                imageVector = Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = googleGreen,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}
