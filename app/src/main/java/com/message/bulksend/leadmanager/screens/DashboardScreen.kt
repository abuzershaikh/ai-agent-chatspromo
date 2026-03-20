package com.message.bulksend.leadmanager.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.message.bulksend.leadmanager.model.LeadStats
import com.message.bulksend.leadmanager.model.LeadStatus

@Composable
fun DashboardScreen(
    stats: LeadStats,
    onAddLeadClick: () -> Unit = {},
    onImportLeadsClick: () -> Unit = {},
    onSettingsClick: () -> Unit = {},
    onProductsClick: () -> Unit = {},
    onCustomFieldsClick: () -> Unit = {},
    onCloudSyncClick: () -> Unit = {},
    onStatusClick: (LeadStatus) -> Unit = {}
) {
    var showHelpDialog by remember { mutableStateOf(false) }
    
    // Beautiful gradient background
    val backgroundBrush = Brush.verticalGradient(
        colors = listOf(
            Color(0xFF667eea),
            Color(0xFF764ba2),
            Color(0xFF6B8DD6),
            Color(0xFF8E37D7)
        )
    )

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header with info button
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Dashboard", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Text("Welcome back! 👋", fontSize = 13.sp, color = Color.White.copy(alpha = 0.8f))
                    }
                    // Info button - subtle visibility
                    IconButton(
                        onClick = { showHelpDialog = true },
                        modifier = Modifier.size(36.dp).background(Color.White.copy(alpha = 0.1f), CircleShape)
                    ) {
                        Icon(Icons.Default.Info, null, tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(18.dp))
                    }
                }
            }
            
            // Stats Cards - Compact horizontal scroll
            item {
                Text("📊 Lead Stats", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.9f))
            }
            item { CompactStatsRow(stats, onStatusClick) }
            
            // Quick Actions Grid
            item {
                Text("⚡ Quick Actions", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.9f), modifier = Modifier.padding(top = 4.dp))
            }
            
            // Row 1: Add Lead + Import
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CuteActionCard(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.PersonAdd,
                        title = "Add Lead",
                        subtitle = "New contact",
                        gradientColors = listOf(Color(0xFF10B981), Color(0xFF059669)),
                        onClick = onAddLeadClick
                    )
                    CuteActionCard(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.Upload,
                        title = "Import",
                        subtitle = "From file",
                        gradientColors = listOf(Color(0xFF06B6D4), Color(0xFF0891B2)),
                        onClick = onImportLeadsClick
                    )
                }
            }
            
            // Row 2: Products + Custom Fields
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CuteActionCard(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.Inventory,
                        title = "Products",
                        subtitle = "Manage items",
                        gradientColors = listOf(Color(0xFFF59E0B), Color(0xFFD97706)),
                        onClick = onProductsClick
                    )
                    CuteActionCard(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.DynamicForm,
                        title = "Custom Fields",
                        subtitle = "Extra data",
                        gradientColors = listOf(Color(0xFFEC4899), Color(0xFFDB2777)),
                        onClick = onCustomFieldsClick
                    )
                }
            }
            
            // Row 3: Settings + Cloud Sync
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CuteActionCard(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.Settings,
                        title = "Settings",
                        subtitle = "Configure",
                        gradientColors = listOf(Color(0xFF8B5CF6), Color(0xFF7C3AED)),
                        onClick = onSettingsClick
                    )
                    CuteActionCard(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.CloudSync,
                        title = "Cloud Sync",
                        subtitle = "Backup data",
                        gradientColors = listOf(Color(0xFF06B6D4), Color(0xFF0284C7)),
                        onClick = onCloudSyncClick
                    )
                }
            }

            // Tips Card
            item {
                TipsCard()
            }
            
            item { Spacer(Modifier.height(80.dp)) }
        }
    }
    
    // Help Dialog
    if (showHelpDialog) {
        LeadCRMInfoDialog(onDismiss = { showHelpDialog = false })
    }
}

@Composable
fun CompactStatsRow(stats: LeadStats, onStatusClick: (LeadStatus) -> Unit = {}) {
    val scrollState = androidx.compose.foundation.lazy.rememberLazyListState()
    
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(3000)
            val nextIndex = if (scrollState.firstVisibleItemIndex >= 5) 0 else scrollState.firstVisibleItemIndex + 1
            scrollState.animateScrollToItem(nextIndex)
        }
    }
    
    LazyRow(
        state = scrollState,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        flingBehavior = ScrollableDefaults.flingBehavior()
    ) {
        item { CompactStatCard(Icons.Default.People, "Total", stats.totalLeads.toString(), Color(0xFF3B82F6), null, onStatusClick) }
        item { CompactStatCard(Icons.Default.FiberNew, "New", stats.newLeads.toString(), Color(0xFF10B981), LeadStatus.NEW, onStatusClick) }
        item { CompactStatCard(Icons.Default.Favorite, "Interested", stats.interested.toString(), Color(0xFFEC4899), LeadStatus.INTERESTED, onStatusClick) }
        item { CompactStatCard(Icons.Default.Phone, "Contacted", stats.contacted.toString(), Color(0xFFF59E0B), LeadStatus.CONTACTED, onStatusClick) }
        item { CompactStatCard(Icons.Default.CheckCircle, "Qualified", stats.qualified.toString(), Color(0xFF8B5CF6), LeadStatus.QUALIFIED, onStatusClick) }
        item { CompactStatCard(Icons.Default.TrendingUp, "Converted", stats.converted.toString(), Color(0xFF22C55E), LeadStatus.CONVERTED, onStatusClick) }
    }
}

@Composable
fun CompactStatCard(
    icon: ImageVector,
    title: String,
    value: String,
    color: Color,
    status: LeadStatus? = null,
    onStatusClick: (LeadStatus) -> Unit = {}
) {
    Card(
        modifier = Modifier
            .width(115.dp)
            .height(95.dp)
            .clickable(enabled = status != null) { status?.let { onStatusClick(it) } },
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.15f)),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Box(
                modifier = Modifier.size(26.dp).background(color.copy(alpha = 0.3f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = Color.White, modifier = Modifier.size(14.dp))
            }
            Text(
                text = value,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 1
            )
            Text(
                text = title,
                fontSize = 11.sp,
                color = Color.White.copy(alpha = 0.9f),
                maxLines = 1,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun CuteActionCard(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    title: String,
    subtitle: String,
    gradientColors: List<Color>,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier.height(75.dp).clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        shape = RoundedCornerShape(14.dp)
    ) {
        Box(
            modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(14.dp))
                .background(Brush.linearGradient(gradientColors, start = Offset(0f, 0f), end = Offset(200f, 100f)))
        ) {
            Row(
                modifier = Modifier.fillMaxSize().padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier.size(38.dp).background(Color.White.copy(alpha = 0.25f), RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, null, tint = Color.White, modifier = Modifier.size(20.dp))
                }
                Column {
                    Text(title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Text(subtitle, fontSize = 10.sp, color = Color.White.copy(alpha = 0.8f))
                }
            }
        }
    }
}

@Composable
fun TipsCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.15f)),
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("💡", fontSize = 24.sp)
            Column(modifier = Modifier.weight(1f)) {
                Text("Pro Tip", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Text("Tap ℹ️ button for complete CRM guide!", fontSize = 11.sp, color = Color.White.copy(alpha = 0.8f))
            }
        }
    }
}

@Composable
fun LeadCRMInfoDialog(onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth().fillMaxHeight(0.85f),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(20.dp)
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("📚 Lead CRM Guide", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1a1a2e))
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, null, tint = Color(0xFF64748B))
                        }
                    }
                }
                
                // Features list
                item { HelpSection("🏠 Dashboard", "View lead statistics, quick actions, and navigate to different sections. Stats auto-scroll to show all categories.") }
                item { HelpSection("👥 Leads Tab", "View all leads with advanced cards showing:\n• Lead Score with emoji indicator\n• Quick Call/WhatsApp/SMS buttons\n• Status-wise colored cards\n• 3-dot menu to change status") }
                item { HelpSection("📊 Overview Tab", "Category-wise lead view:\n• Click any status card to filter leads\n• See count per status\n• Quick status change from cards") }
                item { HelpSection("📅 Scheduled Tab", "Calendar-based follow-up management:\n• Monthly calendar with colored indicators\n• Filter: All/Overdue/Today/Upcoming/Completed\n• Actions: Call, WhatsApp, Reschedule, Complete\n• Quick reschedule with date picker") }
                item { HelpSection("📈 Reports Tab", "Analytics and insights about your leads and conversions.") }
                item { HelpSection("⚙️ Settings Tab", "Configure:\n• Tags - Label your leads\n• Sources - Track lead origins\n• Products - Manage your offerings\n• Custom Fields - Add extra data\n• Auto-Add Settings - Automation rules\n• Import Leads - Bulk import from files") }

                item { HelpSection("👤 Lead Details", "Click any lead to see full details:\n• Contact info (editable)\n• Lead Score slider\n• Product assignment\n• Tags management\n• Notes with timeline\n• Follow-up scheduling\n• Documents upload\n• Activity timeline") }
                item { HelpSection("📝 Notes System", "Advanced notes with:\n• 10 note types (General, Call, Meeting, etc.)\n• Priority levels (Low/Medium/High/Urgent)\n• Pin important notes\n• Reply threads\n• Search & archive") }
                item { HelpSection("🔔 Follow-ups", "Never miss a follow-up:\n• Schedule calls, meetings, visits\n• Set date & time\n• Get reminders\n• Mark complete or reschedule\n• Track overdue items") }
                item { HelpSection("📦 Products", "Manage your offerings:\n• Physical/Digital/Service types\n• Pricing (MRP & Selling)\n• Categories & descriptions\n• Assign to leads") }
                item { HelpSection("🏷️ Lead Status", "Track lead journey:\n• New → Interested → Contacted\n• Qualified → Converted → Customer\n• Lost (for dropped leads)\n• Each status has unique color") }
                item { HelpSection("⭐ Lead Score", "Rate lead quality (0-100):\n• 😢 0-19: Very low\n• 😟 20-39: Low\n• 😐 40-59: Medium\n• 😊 60-79: Good\n• 🤩 80-100: Excellent") }
                item { HelpSection("📱 Quick Actions", "From lead cards:\n• 📞 Call - Direct dial\n• 💬 WhatsApp - Open chat\n• 📱 SMS - Send message\n• ✉️ Email - Compose mail") }
                
                item {
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8B5CF6)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Got it! 👍", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun HelpSection(title: String, description: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1a1a2e))
        Text(description, fontSize = 12.sp, color = Color(0xFF64748B), lineHeight = 18.sp)
        HorizontalDivider(color = Color(0xFFF1F5F9), modifier = Modifier.padding(top = 8.dp))
    }
}
