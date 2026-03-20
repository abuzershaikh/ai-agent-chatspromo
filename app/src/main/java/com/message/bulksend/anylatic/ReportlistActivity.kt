package com.message.bulksend.anylatic

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.message.bulksend.db.AppDatabase
import com.message.bulksend.db.Campaign
import com.message.bulksend.ui.theme.BulksendTestTheme
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*

class ReportlistActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BulksendTestTheme {
                EnhancedReportListScreen()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EnhancedReportListScreen() {
    val context = LocalContext.current
    val db = remember { AppDatabase.getInstance(context) }
    var reports by remember { mutableStateOf<List<Campaign>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var selectedSortOption by remember { mutableStateOf(SortOption.DATE_DESC) }

    LaunchedEffect(Unit) {
        delay(500) // Simulate loading delay
        reports = db.campaignDao().getAllCampaigns()
        isLoading = false
    }

    val sortedReports = remember(reports, selectedSortOption) {
        when (selectedSortOption) {
            SortOption.DATE_DESC -> reports.sortedByDescending { it.timestamp }
            SortOption.DATE_ASC -> reports.sortedBy { it.timestamp }
            SortOption.NAME_ASC -> reports.sortedBy { it.campaignName }
            SortOption.SUCCESS_RATE -> reports.sortedByDescending { report ->
                if (report.totalContacts > 0) {
                    report.sentCount.toFloat() / report.totalContacts
                } else 0f
            }
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            EnhancedReportListTopBar(
                selectedSortOption = selectedSortOption,
                onSortOptionChanged = { selectedSortOption = it },
                totalReports = reports.size
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.background,
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.3f)
                        )
                    )
                )
        ) {
            if (isLoading) {
                LoadingState()
            } else if (reports.isEmpty()) {
                EmptyState()
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item {
                        SummaryCard(reports = reports)
                    }
                    itemsIndexed(sortedReports, key = { _, report -> report.id }) { index, report ->
                        AnimatedReportListItem(
                            report = report,
                            index = index,
                            onClick = {
                                val intent = Intent(context, MessagereportActivity::class.java).apply {
                                    putExtra("CAMPAIGN_ID", report.id)
                                }
                                context.startActivity(intent)
                            }
                        )
                    }
                }
            }
        }
    }
}

enum class SortOption(val displayName: String, val icon: ImageVector) {
    DATE_DESC("Latest First", Icons.Default.CalendarToday),
    DATE_ASC("Oldest First", Icons.Default.History),
    NAME_ASC("Name A-Z", Icons.Default.SortByAlpha),
    SUCCESS_RATE("Success Rate", Icons.Default.TrendingUp)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EnhancedReportListTopBar(
    selectedSortOption: SortOption,
    onSortOptionChanged: (SortOption) -> Unit,
    totalReports: Int
) {
    var showSortMenu by remember { mutableStateOf(false) }

    TopAppBar(
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Assessment,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Campaign Reports",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold
                        )
                    )
                    if (totalReports > 0) {
                        Text(
                            text = "$totalReports reports available",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }
            }
        },
        actions = {
            Box {
                IconButton(
                    onClick = { showSortMenu = true },
                    modifier = Modifier
                        .background(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                            CircleShape
                        )
                ) {
                    Icon(
                        imageVector = selectedSortOption.icon,
                        contentDescription = "Sort options",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                DropdownMenu(
                    expanded = showSortMenu,
                    onDismissRequest = { showSortMenu = false }
                ) {
                    SortOption.values().forEach { option ->
                        DropdownMenuItem(
                            text = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = option.icon,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp),
                                        tint = if (selectedSortOption == option) {
                                            MaterialTheme.colorScheme.primary
                                        } else MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = option.displayName,
                                        fontWeight = if (selectedSortOption == option) {
                                            FontWeight.Bold
                                        } else FontWeight.Normal,
                                        color = if (selectedSortOption == option) {
                                            MaterialTheme.colorScheme.primary
                                        } else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            },
                            onClick = {
                                onSortOptionChanged(option)
                                showSortMenu = false
                            }
                        )
                    }
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
        )
    )
}

@Composable
fun SummaryCard(reports: List<Campaign>) {
    val totalContacts = reports.sumOf { it.totalContacts }
    val totalSuccess = reports.sumOf { it.sentCount }
    val totalFailed = reports.sumOf { it.failedCount }
    val averageSuccessRate = if (totalContacts > 0) {
        (totalSuccess * 100f / totalContacts)
    } else 0f

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Color(0xFF8B5CF6).copy(alpha = 0.4f), RoundedCornerShape(20.dp)),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.Transparent
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF6366F1).copy(alpha = 0.1f),
                            Color(0xFF8B5CF6).copy(alpha = 0.05f)
                        )
                    )
                )
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column {
                        Text(
                            text = "Overview",
                            style = MaterialTheme.typography.headlineSmall.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "All campaign statistics",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.size(80.dp)
                    ) {
                        CircularProgressIndicator(
                            progress = { averageSuccessRate / 100f },
                            modifier = Modifier.size(70.dp),
                            color = when {
                                averageSuccessRate >= 80f -> Color(0xFF10B981)
                                averageSuccessRate >= 60f -> Color(0xFFF59E0B)
                                else -> Color(0xFFEF4444)
                            },
                            strokeWidth = 6.dp,
                            trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                        )
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "${averageSuccessRate.toInt()}%",
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontWeight = FontWeight.Bold
                                ),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Avg",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                    }
                }
                Row(
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    SummaryStatItem(
                        title = "Total",
                        value = totalContacts.toString(),
                        icon = Icons.Default.People,
                        color = Color(0xFF6366F1)
                    )
                    SummaryStatItem(
                        title = "Success",
                        value = totalSuccess.toString(),
                        icon = Icons.Default.CheckCircle,
                        color = Color(0xFF10B981)
                    )
                    SummaryStatItem(
                        title = "Failed",
                        value = totalFailed.toString(),
                        icon = Icons.Default.Cancel,
                        color = Color(0xFFEF4444)
                    )
                    SummaryStatItem(
                        title = "Reports",
                        value = reports.size.toString(),
                        icon = Icons.Default.Assignment,
                        color = Color(0xFF8B5CF6)
                    )
                }
            }
        }
    }
}

@Composable
fun SummaryStatItem(
    title: String,
    value: String,
    icon: ImageVector,
    color: Color
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .background(color.copy(alpha = 0.1f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(24.dp)
            )
        }
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Bold
            ),
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
    }
}

@Composable
fun AnimatedReportListItem(
    report: Campaign,
    index: Int,
    onClick: () -> Unit
) {
    val animatedVisibilityState = remember { MutableTransitionState(false) }

    LaunchedEffect(Unit) {
        delay(index * 100L) // Staggered animation
        animatedVisibilityState.targetState = true
    }

    AnimatedVisibility(
        visibleState = animatedVisibilityState,
        enter = slideInVertically(
            initialOffsetY = { it },
            animationSpec = tween(600, easing = EaseOutBack)
        ) + fadeIn(animationSpec = tween(600))
    ) {
        EnhancedReportListItem(
            report = report,
            onClick = onClick
        )
    }
}

@Composable
fun EnhancedReportListItem(report: Campaign, onClick: () -> Unit) {
    val successRate = if (report.totalContacts > 0) {
        (report.sentCount * 100f / report.totalContacts)
    } else 0f

    val statusColor = when {
        successRate >= 80f -> Color(0xFF10B981)
        successRate >= 60f -> Color(0xFFF59E0B)
        else -> Color(0xFFEF4444)
    }

    val gradientColors = when {
        successRate >= 80f -> listOf(Color(0xFF10B981), Color(0xFF34D399))
        successRate >= 60f -> listOf(Color(0xFFF59E0B), Color(0xFFFBBF24))
        else -> listOf(Color(0xFFEF4444), Color(0xFFF87171))
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .shadow(6.dp, RoundedCornerShape(16.dp)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Box {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .background(
                        Brush.horizontalGradient(gradientColors)
                    )
            )

            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = report.campaignName,
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Schedule,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = formatTimestamp(report.timestamp),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                    }
                    Card(
                        shape = CircleShape,
                        colors = CardDefaults.cardColors(
                            containerColor = statusColor.copy(alpha = 0.1f)
                        ),
                        modifier = Modifier.size(60.dp)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "${successRate.toInt()}%",
                                    style = MaterialTheme.typography.labelLarge.copy(
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = statusColor
                                )
                                Text(
                                    text = "Success",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = statusColor.copy(alpha = 0.8f),
                                    fontSize = 8.sp
                                )
                            }
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    StatisticItem(
                        icon = Icons.Default.People,
                        label = "Total",
                        value = report.totalContacts.toString(),
                        color = Color(0xFF6366F1)
                    )
                    StatisticItem(
                        icon = Icons.Default.CheckCircle,
                        label = "Success",
                        value = report.sentCount.toString(),
                        color = Color(0xFF10B981)
                    )
                    StatisticItem(
                        icon = Icons.Default.Cancel,
                        label = "Failed",
                        value = report.failedCount.toString(),
                        color = Color(0xFFEF4444)
                    )
                    StatisticItem(
                        icon = Icons.Default.Schedule,
                        label = "Pending",
                        value = (report.totalContacts - report.sentCount - report.failedCount).toString(),
                        color = Color(0xFFF59E0B)
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Tap to view details",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun StatisticItem(
    icon: ImageVector,
    label: String,
    value: String,
    color: Color
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(20.dp)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall.copy(
                fontWeight = FontWeight.Bold
            ),
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
    }
}

@Composable
fun LoadingState() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                        MaterialTheme.colorScheme.background
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(48.dp),
                color = MaterialTheme.colorScheme.primary,
                strokeWidth = 4.dp
            )
            Text(
                text = "Loading reports...",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
fun EmptyState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp)
                .shadow(4.dp, RoundedCornerShape(20.dp)),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
            )
        ) {
            Column(
                modifier = Modifier.padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Assignment,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                    modifier = Modifier.size(80.dp)
                )
                Text(
                    text = "No Reports Available",
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "Start creating campaigns to see reports here",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

private fun formatTimestamp(timestamp: Long): String {
    val sdf = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
    return sdf.format(Date(timestamp))
}


