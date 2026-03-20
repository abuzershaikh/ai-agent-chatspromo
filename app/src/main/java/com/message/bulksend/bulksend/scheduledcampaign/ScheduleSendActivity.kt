 package com.message.bulksend.bulksend.scheduledcampaign

import android.app.AlarmManager
import android.app.DatePickerDialog
import android.app.PendingIntent
import android.app.TimePickerDialog
import android.content.Context
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
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
import androidx.lifecycle.lifecycleScope
import com.message.bulksend.db.AppDatabase
import com.message.bulksend.db.ScheduleStatus
import com.message.bulksend.ui.theme.BulksendTestTheme
import com.message.bulksend.utils.CampaignStatusUpdater
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class ScheduleSendActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        setContent {
            BulksendTestTheme {
                ScheduleSendScreen()
            }
        }
    }
}

// Data class for UI display
data class ScheduledCampaignUI(
    val id: String,
    val title: String,
    val type: CampaignTypeUI,
    val scheduledTime: Long,
    val contactCount: Int,
    val status: CampaignStatusUI,
    val message: String = "",
    val mediaPath: String = "",
    val createdTime: Long = System.currentTimeMillis()
)

enum class CampaignTypeUI(val displayName: String, val icon: ImageVector, val color: Color) {
    TEXT("Text Campaign", Icons.Outlined.Message, Color(0xFF667EEA)),
    MEDIA("Media Campaign", Icons.Outlined.Image, Color(0xFFFF6B9D)),
    TEXT_AND_MEDIA("Text + Media", Icons.Outlined.PermMedia, Color(0xFFFFD740)),
    SHEET("Sheet Campaign", Icons.Outlined.GridOn, Color(0xFF43A047))
}

enum class CampaignStatusUI(val displayName: String, val color: Color) {
    SCHEDULED("Scheduled", Color(0xFF10B981)),
    RUNNING("Running", Color(0xFFF59E0B)),
    COMPLETED("Completed", Color(0xFF6366F1)),
    CANCELLED("Cancelled", Color(0xFFEF4444)),
    FAILED("Failed", Color(0xFFDC2626)),
    FINISHED("Finished", Color(0xFF10B981)) // Changed to green
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleSendScreen() {
    val context = LocalContext.current
    var selectedTab by remember { mutableStateOf(0) }
    var scheduledCampaigns by remember { mutableStateOf<List<ScheduledCampaignUI>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    
    // Load campaigns from database
    LaunchedEffect(Unit) {
        loadScheduledCampaigns(context) { campaigns ->
            scheduledCampaigns = campaigns
            isLoading = false
        }
    }
    
    val backgroundBrush = Brush.verticalGradient(
        colors = listOf(
            Color(0xFF0f0c29),
            Color(0xFF302b63),
            Color(0xFF24243e),
            Color(0xFF0f0c29)
        )
    )

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    // Navigate back to BulkMessage1Activity to create new campaigns
                    (context as ComponentActivity).finish()
                },
                containerColor = Color(0xFF10B981), // Changed to green
                contentColor = Color.White,
                modifier = Modifier.size(56.dp)
            ) {
                Icon(
                    Icons.Filled.Add,
                    contentDescription = "Create New Campaign",
                    modifier = Modifier.size(24.dp)
                )
            }
        },
        containerColor = Color.Transparent
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(backgroundBrush)
        ) {
            Column(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize()
            ) {
                // Custom Header Row with Back Button and Title
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Back Button
                    IconButton(
                        onClick = { 
                            (context as ComponentActivity).finish()
                        }
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color(0xFF00D4FF),
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    
                    // Title
                    Text(
                        "Scheduled History",
                        color = Color(0xFF10B981),
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                    
                    // Refresh Button
                    IconButton(
                        onClick = {
                            // Refresh campaigns
                            isLoading = true
                            loadScheduledCampaigns(context) { campaigns ->
                                scheduledCampaigns = campaigns
                                isLoading = false
                            }
                        }
                    ) {
                        Icon(
                            Icons.Outlined.Refresh,
                            contentDescription = "Refresh",
                            tint = Color(0xFF00D4FF),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
                
                // Tab Row
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = Color.Transparent,
                    contentColor = Color.White,
                    indicator = { tabPositions ->
                        TabRowDefaults.Indicator(
                            color = Color(0xFF10B981), // Changed to green
                            height = 3.dp
                        )
                    }
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = {
                            Text(
                                "Scheduled",
                                fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = {
                            Text(
                                "History",
                                fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    )
                }
                
                // Loading state
                if (isLoading) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            CircularProgressIndicator(
                                color = Color(0xFF10B981), // Changed to green
                                modifier = Modifier.size(48.dp)
                            )
                            Text(
                                "Loading scheduled campaigns...",
                                color = Color.White.copy(alpha = 0.7f),
                                fontSize = 14.sp
                            )
                        }
                    }
                } else {
                    // Content based on selected tab
                    when (selectedTab) {
                        0 -> ScheduledCampaignsContent(
                            campaigns = scheduledCampaigns.filter { 
                                it.status == CampaignStatusUI.SCHEDULED || it.status == CampaignStatusUI.RUNNING 
                            },
                            onCancelCampaign = { campaign ->
                                cancelScheduledCampaign(context, campaign.id) {
                                    // Refresh campaigns after cancellation
                                    loadScheduledCampaigns(context) { campaigns ->
                                        scheduledCampaigns = campaigns
                                    }
                                }
                            },
                            onRescheduleCampaign = { campaign ->
                                showRescheduleDialog(context, campaign) { newTime ->
                                    rescheduleScheduledCampaign(context, campaign.id, newTime) {
                                        // Refresh campaigns after rescheduling
                                        loadScheduledCampaigns(context) { campaigns ->
                                            scheduledCampaigns = campaigns
                                        }
                                    }
                                }
                            }
                        )
                        1 -> HistoryCampaignsContent(
                            campaigns = scheduledCampaigns.filter { 
                                it.status == CampaignStatusUI.COMPLETED || 
                                it.status == CampaignStatusUI.CANCELLED ||
                                it.status == CampaignStatusUI.FAILED ||
                                it.status == CampaignStatusUI.FINISHED
                            },
                            onDeleteCampaign = { campaign ->
                                deleteScheduledCampaign(context, campaign.id) {
                                    // Refresh campaigns after deletion
                                    loadScheduledCampaigns(context) { campaigns ->
                                        scheduledCampaigns = campaigns
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ScheduledCampaignsContent(
    campaigns: List<ScheduledCampaignUI>,
    onCancelCampaign: (ScheduledCampaignUI) -> Unit,
    onRescheduleCampaign: (ScheduledCampaignUI) -> Unit
) {
    if (campaigns.isEmpty()) {
        EmptyStateContent(
            icon = Icons.Outlined.Schedule,
            title = "No Scheduled Campaigns",
            subtitle = "Create your first scheduled campaign using the + button",
            color = Color(0xFF10B981) // Changed to green
        )
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(campaigns) { campaign ->
                ScheduledCampaignCard(
                    campaign = campaign,
                    onCancel = { onCancelCampaign(campaign) },
                    onReschedule = { onRescheduleCampaign(campaign) }
                )
            }
        }
    }
}

@Composable
fun HistoryCampaignsContent(
    campaigns: List<ScheduledCampaignUI>,
    onDeleteCampaign: (ScheduledCampaignUI) -> Unit
) {
    if (campaigns.isEmpty()) {
        EmptyStateContent(
            icon = Icons.Outlined.History,
            title = "No Campaign History",
            subtitle = "Your completed, cancelled, and failed campaigns will appear here",
            color = Color(0xFF6366F1)
        )
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(campaigns.sortedByDescending { it.createdTime }) { campaign ->
                HistoryCampaignCard(
                    campaign = campaign,
                    onDelete = { onDeleteCampaign(campaign) }
                )
            }
        }
    }
}

@Composable
fun ScheduledCampaignCard(
    campaign: ScheduledCampaignUI,
    onCancel: () -> Unit,
    onReschedule: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.95f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Header with campaign type and menu
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(campaign.type.color.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            campaign.type.icon,
                            contentDescription = null,
                            tint = campaign.type.color,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Column {
                        Text(
                            campaign.title,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = Color(0xFF1E293B)
                        )
                        Text(
                            campaign.type.displayName,
                            fontSize = 12.sp,
                            color = Color(0xFF64748B)
                        )
                    }
                }
                
                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(
                            Icons.Filled.MoreVert,
                            contentDescription = "More options",
                            tint = Color(0xFF64748B)
                        )
                    }
                    
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Reschedule") },
                            onClick = {
                                showMenu = false
                                onReschedule()
                            },
                            leadingIcon = {
                                Icon(Icons.Outlined.Schedule, contentDescription = null)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Cancel", color = Color.Red) },
                            onClick = {
                                showMenu = false
                                onCancel()
                            },
                            leadingIcon = {
                                Icon(Icons.Outlined.Cancel, contentDescription = null, tint = Color.Red)
                            }
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Campaign details
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        "Scheduled Time",
                        fontSize = 12.sp,
                        color = Color(0xFF64748B),
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        formatDateTime(campaign.scheduledTime),
                        fontSize = 14.sp,
                        color = Color(0xFF1E293B),
                        fontWeight = FontWeight.SemiBold
                    )
                }
                
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        "Contacts",
                        fontSize = 12.sp,
                        color = Color(0xFF64748B),
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        "${campaign.contactCount}",
                        fontSize = 14.sp,
                        color = Color(0xFF1E293B),
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Status badge and time remaining
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                StatusBadge(status = campaign.status)
                
                // Time remaining or finished indicator
                when {
                    campaign.status == CampaignStatusUI.FINISHED -> {
                        Text(
                            "Time passed",
                            fontSize = 12.sp,
                            color = Color(0xFF10B981), // Changed to green
                            fontWeight = FontWeight.Medium
                        )
                    }
                    campaign.status == CampaignStatusUI.SCHEDULED -> {
                        val timeRemaining = getTimeRemaining(campaign.scheduledTime)
                        if (timeRemaining.isNotEmpty()) {
                            Text(
                                timeRemaining,
                                fontSize = 12.sp,
                                color = Color(0xFF64748B),
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun HistoryCampaignCard(
    campaign: ScheduledCampaignUI,
    onDelete: (ScheduledCampaignUI) -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.95f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Header with delete menu
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(campaign.type.color.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            campaign.type.icon,
                            contentDescription = null,
                            tint = campaign.type.color,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Column {
                        Text(
                            campaign.title,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = Color(0xFF1E293B)
                        )
                        Text(
                            campaign.type.displayName,
                            fontSize = 12.sp,
                            color = Color(0xFF64748B)
                        )
                    }
                }
                
                // Delete menu
                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(
                            Icons.Filled.MoreVert,
                            contentDescription = "More options",
                            tint = Color(0xFF64748B)
                        )
                    }
                    
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Delete", color = Color.Red) },
                            onClick = {
                                showMenu = false
                                onDelete(campaign)
                            },
                            leadingIcon = {
                                Icon(Icons.Outlined.Delete, contentDescription = null, tint = Color.Red)
                            }
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Details
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        "Scheduled Time",
                        fontSize = 12.sp,
                        color = Color(0xFF64748B),
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        formatDateTime(campaign.scheduledTime),
                        fontSize = 14.sp,
                        color = Color(0xFF1E293B),
                        fontWeight = FontWeight.SemiBold
                    )
                }
                
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        "Contacts",
                        fontSize = 12.sp,
                        color = Color(0xFF64748B),
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        "${campaign.contactCount}",
                        fontSize = 14.sp,
                        color = Color(0xFF1E293B),
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Created time for history
            Text(
                "Created: ${formatDateTime(campaign.createdTime)}",
                fontSize = 11.sp,
                color = Color(0xFF64748B),
                fontWeight = FontWeight.Normal
            )
            
            // Special message for finished campaigns
            if (campaign.status == CampaignStatusUI.FINISHED) {
                Text(
                    "⏰ Scheduled time has passed",
                    fontSize = 11.sp,
                    color = Color(0xFF10B981), // Changed to green
                    fontWeight = FontWeight.Medium
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            StatusBadge(status = campaign.status)
        }
    }
}

@Composable
fun StatusBadge(status: CampaignStatusUI) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(status.color.copy(alpha = 0.1f))
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            status.displayName,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = status.color
        )
    }
}

@Composable
fun EmptyStateContent(
    icon: ImageVector,
    title: String,
    subtitle: String,
    color: Color
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(40.dp)
            )
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Text(
            title,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            subtitle,
            fontSize = 14.sp,
            color = Color.White.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
            lineHeight = 20.sp
        )
    }
}

// Helper functions
fun formatDateTime(timestamp: Long): String {
    val sdf = SimpleDateFormat("MMM dd, yyyy 'at' hh:mm a", Locale.getDefault())
    return sdf.format(Date(timestamp))
}

fun getTimeRemaining(scheduledTime: Long): String {
    val now = System.currentTimeMillis()
    val diff = scheduledTime - now
    
    if (diff <= 0) return ""
    
    val days = diff / (24 * 60 * 60 * 1000)
    val hours = (diff % (24 * 60 * 60 * 1000)) / (60 * 60 * 1000)
    val minutes = (diff % (60 * 60 * 1000)) / (60 * 1000)
    
    return when {
        days > 0 -> "in ${days}d ${hours}h"
        hours > 0 -> "in ${hours}h ${minutes}m"
        else -> "in ${minutes}m"
    }
}

// Database functions
fun loadScheduledCampaigns(context: Context, onResult: (List<ScheduledCampaignUI>) -> Unit) {
    val database = AppDatabase.getInstance(context)
    val dao = database.scheduledCampaignDao()
    
    // Use coroutine to load data
    (context as ComponentActivity).lifecycleScope.launch {
        try {
            // First, update campaign statuses based on time
            CampaignStatusUpdater.updateCampaignStatuses(context) {
                // After updating statuses, load campaigns
                (context as ComponentActivity).lifecycleScope.launch {
                    try {
                        val campaigns = dao.getAllCampaigns()
                        val currentTime = System.currentTimeMillis()
                        
                        val uiCampaigns = campaigns.map { dbCampaign ->
                            // Get display status (handles automatic "Finished" status)
                            val (displayStatus, isFinished) = CampaignStatusUpdater.getFormattedDisplayStatus(
                                dbCampaign.scheduledTime, 
                                dbCampaign.status
                            )
                            
                            val uiStatus = when {
                                isFinished -> CampaignStatusUI.FINISHED
                                else -> mapCampaignStatus(dbCampaign.status)
                            }
                            
                            ScheduledCampaignUI(
                                id = dbCampaign.id,
                                title = dbCampaign.campaignName,
                                type = mapCampaignType(dbCampaign.campaignType),
                                scheduledTime = dbCampaign.scheduledTime,
                                contactCount = dbCampaign.contactCount,
                                status = uiStatus,
                                createdTime = dbCampaign.createdTime
                            )
                        }
                        onResult(uiCampaigns)
                    } catch (e: Exception) {
                        // If error, return empty list
                        onResult(emptyList())
                    }
                }
            }
        } catch (e: Exception) {
            // If error, return empty list
            onResult(emptyList())
        }
    }
}

fun cancelScheduledCampaign(context: Context, campaignId: String, onComplete: () -> Unit) {
    val database = AppDatabase.getInstance(context)
    val dao = database.scheduledCampaignDao()
    
    (context as ComponentActivity).lifecycleScope.launch {
        try {
            dao.updateCampaignStatus(campaignId, ScheduleStatus.CANCELLED.name)
            onComplete()
        } catch (e: Exception) {
            // Handle error
            onComplete()
        }
    }
}

fun rescheduleScheduledCampaign(context: Context, campaignId: String, newTime: Long, onComplete: () -> Unit) {
    val database = AppDatabase.getInstance(context)
    val dao = database.scheduledCampaignDao()
    
    (context as ComponentActivity).lifecycleScope.launch {
        try {
            dao.updateScheduledTime(campaignId, newTime)
            onComplete()
        } catch (e: Exception) {
            // Handle error
            onComplete()
        }
    }
}

// Mapping functions
fun mapCampaignType(dbType: String): CampaignTypeUI {
    return when (dbType) {
        "TEXT" -> CampaignTypeUI.TEXT
        "MEDIA" -> CampaignTypeUI.MEDIA
        "TEXT_AND_MEDIA" -> CampaignTypeUI.TEXT_AND_MEDIA
        "SHEET" -> CampaignTypeUI.SHEET
        else -> CampaignTypeUI.TEXT
    }
}

fun mapCampaignStatus(dbStatus: String): CampaignStatusUI {
    return when (dbStatus) {
        "SCHEDULED" -> CampaignStatusUI.SCHEDULED
        "RUNNING" -> CampaignStatusUI.RUNNING
        "COMPLETED" -> CampaignStatusUI.COMPLETED
        "CANCELLED" -> CampaignStatusUI.CANCELLED
        "FAILED" -> CampaignStatusUI.FAILED
        "FINISHED" -> CampaignStatusUI.FINISHED
        else -> CampaignStatusUI.SCHEDULED
    }
}

fun showRescheduleDialog(
    context: Context, 
    campaign: ScheduledCampaignUI, 
    onReschedule: (Long) -> Unit
) {
    val calendar = Calendar.getInstance()
    
    DatePickerDialog(
        context,
        { _, year, month, dayOfMonth ->
            calendar.set(Calendar.YEAR, year)
            calendar.set(Calendar.MONTH, month)
            calendar.set(Calendar.DAY_OF_MONTH, dayOfMonth)
            
            TimePickerDialog(
                context,
                { _, hourOfDay, minute ->
                    calendar.set(Calendar.HOUR_OF_DAY, hourOfDay)
                    calendar.set(Calendar.MINUTE, minute)
                    calendar.set(Calendar.SECOND, 0)
                    
                    onReschedule(calendar.timeInMillis)
                },
                calendar.get(Calendar.HOUR_OF_DAY),
                calendar.get(Calendar.MINUTE),
                false
            ).show()
        },
        calendar.get(Calendar.YEAR),
        calendar.get(Calendar.MONTH),
        calendar.get(Calendar.DAY_OF_MONTH)
    ).show()
}

fun deleteScheduledCampaign(context: Context, campaignId: String, onComplete: () -> Unit) {
    val database = AppDatabase.getInstance(context)
    val dao = database.scheduledCampaignDao()
    
    (context as ComponentActivity).lifecycleScope.launch {
        try {
            dao.deleteCampaign(campaignId)
            onComplete()
        } catch (e: Exception) {
            // Handle error
            onComplete()
        }
    }
}