package com.message.bulksend.leadmanager

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.message.bulksend.leadmanager.model.Lead
import com.message.bulksend.leadmanager.model.LeadPriority
import com.message.bulksend.leadmanager.model.LeadStatus
import com.message.bulksend.leadmanager.model.LeadStats
import com.message.bulksend.leadmanager.model.SyncState
import com.message.bulksend.leadmanager.screens.AutoAddSettingsScreen
import com.message.bulksend.leadmanager.screens.CategoriesScreen
import com.message.bulksend.leadmanager.screens.DashboardScreen
import com.message.bulksend.leadmanager.screens.ImportLeadsScreen
import com.message.bulksend.leadmanager.screens.LeadDetailScreen
import com.message.bulksend.leadmanager.screens.LeadsListScreen
import com.message.bulksend.leadmanager.screens.ManagementScreen
import com.message.bulksend.leadmanager.screens.ScheduledScreen
import com.message.bulksend.leadmanager.screens.LeadSettingsScreen
import com.message.bulksend.leadmanager.screens.ManagementType
import com.message.bulksend.leadmanager.screens.LeadPreviewScreen
import com.message.bulksend.leadmanager.customfields.CustomFieldsManager
import com.message.bulksend.leadmanager.customfields.CustomFieldsScreen
import com.message.bulksend.ui.theme.BulksendTestTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.Date
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.activity.compose.BackHandler

// Auto-sync interval (1 minute)
private const val AUTO_SYNC_INTERVAL = 60_000L

class LeadManagerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        setContent {
            BulksendTestTheme {
                LeadManagerScreen(
                    initialTab = intent?.getIntExtra(EXTRA_INITIAL_TAB, 0) ?: 0,
                    initialLeadId = intent?.getStringExtra(EXTRA_LEAD_ID),
                    onBackPressed = { finish() }
                )
            }
        }
    }

    companion object {
        const val EXTRA_INITIAL_TAB = "lead_manager_initial_tab"
        const val EXTRA_LEAD_ID = "lead_manager_lead_id"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LeadManagerScreen(
    onBackPressed: () -> Unit,
    initialTab: Int = 0,
    initialLeadId: String? = null
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val leadManager = remember { LeadManager(context) }
    val customFieldsManager = remember { CustomFieldsManager(context) }
    val coroutineScope = rememberCoroutineScope()
    
    // Async state for leads and stats
    var leads by remember { mutableStateOf<List<Lead>>(emptyList()) }
    var stats by remember { mutableStateOf(LeadStats()) }
    var isLoading by remember { mutableStateOf(true) }
    
    // Sync state
    var syncState by remember { mutableStateOf(SyncState()) }
    var isSyncing by remember { mutableStateOf(false) }
    
    // Reactive state - auto-refresh counter
    var refreshTrigger by remember { mutableStateOf(0) }
    
    // Format last sync time
    val lastSyncText = remember(syncState.lastSyncTime) {
        if (syncState.lastSyncTime > 0) {
            val diff = System.currentTimeMillis() - syncState.lastSyncTime
            when {
                diff < 60_000 -> "Just now"
                diff < 3600_000 -> "${diff / 60_000}m ago"
                diff < 86400_000 -> "${diff / 3600_000}h ago"
                else -> SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()).format(Date(syncState.lastSyncTime))
            }
        } else "Never"
    }
    
    // Async data loading function using suspend
    fun loadDataAsync(showLoading: Boolean = true) {
        coroutineScope.launch(Dispatchers.IO) {
            try {
                if (showLoading) {
                    withContext(Dispatchers.Main) { isLoading = true }
                }
                withContext(Dispatchers.Main) { isSyncing = true }
                
                val loadedLeads = leadManager.getAllLeadsSuspend()
                val loadedStats = leadManager.getStatsSuspend()
                leadManager.updateLastSyncTime()
                
                withContext(Dispatchers.Main) {
                    leads = loadedLeads
                    stats = loadedStats
                    syncState = SyncState(
                        lastSyncTime = System.currentTimeMillis(),
                        isSyncing = false,
                        totalLeads = loadedStats.totalLeads
                    )
                    isLoading = false
                    isSyncing = false
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    isLoading = false
                    isSyncing = false
                }
            }
        }
    }
    
    // Initial load
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            leadManager.generateSampleData()
        }
        // Load initial sync time
        syncState = SyncState(lastSyncTime = leadManager.getLastSyncTime())
        leadManager.resyncFollowUpNotifications()
        loadDataAsync()
    }
    
    // Auto-sync every 1 minute
    LaunchedEffect(Unit) {
        while (true) {
            delay(AUTO_SYNC_INTERVAL)
            loadDataAsync(showLoading = false) // Silent sync
        }
    }
    
    // Reload when refresh trigger changes
    LaunchedEffect(refreshTrigger) {
        if (refreshTrigger > 0) {
            loadDataAsync()
        }
    }
    
    // Auto-refresh function
    fun refreshData() {
        coroutineScope.launch {
            delay(100) // Small delay to ensure DB write completes
            refreshTrigger++
        }
    }
    
    var selectedTab by remember { mutableStateOf(initialTab.coerceIn(0, 5)) }
    var showManagementScreen by remember { mutableStateOf<ManagementType?>(null) }
    var showMenu by remember { mutableStateOf(false) }
    var showAddLeadScreen by remember { mutableStateOf(false) }
    var selectedLead by remember { mutableStateOf<Lead?>(null) }
    var pendingLeadId by remember { mutableStateOf(initialLeadId) }
    var showAutoAddSettingsScreen by remember { mutableStateOf(false) }
    var showCustomFieldsScreen by remember { mutableStateOf(false) }
    var selectedStatusFilter by remember { mutableStateOf<LeadStatus?>(null) }
    
    val backgroundBrush = Brush.verticalGradient(
        colors = listOf(Color(0xFF0f0c29), Color(0xFF302b63), Color(0xFF24243e))
    )

    LaunchedEffect(leads, pendingLeadId) {
        val leadId = pendingLeadId ?: return@LaunchedEffect
        val matchedLead = leads.firstOrNull { it.id == leadId } ?: return@LaunchedEffect
        selectedTab = 3
        selectedLead = matchedLead
        pendingLeadId = null
    }
    
    if (showManagementScreen != null) {
        BackHandler { 
            showManagementScreen = null
            refreshData()
        }
        ManagementScreen(
            leadManager = leadManager,
            type = showManagementScreen!!,
            onBack = { 
                showManagementScreen = null
                refreshData()
            }
        )
        return
    }
    
    if (showAddLeadScreen) {
        BackHandler { showAddLeadScreen = false }
        com.message.bulksend.leadmanager.screens.AddLeadScreen(
            leadManager = leadManager,
            onBack = { showAddLeadScreen = false },
            onLeadAdded = {
                showAddLeadScreen = false
                refreshData()
            }
        )
        return
    }
    
    if (selectedLead != null) {
        BackHandler { 
            selectedLead = null
            refreshData()
        }
        LeadPreviewScreen(
            lead = selectedLead!!,
            leadManager = leadManager,
            onBack = { 
                selectedLead = null
                refreshData()
            },
            onLeadUpdated = {
                refreshData()
            }
        )
        return
    }
    
    if (showAutoAddSettingsScreen) {
        BackHandler { 
            showAutoAddSettingsScreen = false
            refreshData()
        }
        AutoAddSettingsScreen(
            leadManager = leadManager,
            onBack = { 
                showAutoAddSettingsScreen = false
                refreshData()
            }
        )
        return
    }
    
    if (showCustomFieldsScreen) {
        BackHandler { showCustomFieldsScreen = false }
        CustomFieldsScreen(
            customFieldsManager = customFieldsManager,
            onBack = { showCustomFieldsScreen = false }
        )
        return
    }
    
    // Show filtered leads by status from Dashboard
    if (selectedStatusFilter != null) {
        BackHandler { 
            selectedStatusFilter = null
            refreshData()
        }
        com.message.bulksend.leadmanager.overview.FilteredLeadsScreen(
            leads = leads.filter { it.status == selectedStatusFilter },
            status = selectedStatusFilter!!,
            onBack = { 
                selectedStatusFilter = null
                refreshData()
            },
            onLeadClick = { lead -> 
                selectedStatusFilter = null
                selectedLead = lead 
            },
            onStatusChange = { lead, newStatus ->
                coroutineScope.launch {
                    val updatedLead = lead.copy(status = newStatus)
                    leadManager.updateLead(updatedLead)
                    refreshData()
                }
            }
        )
        return
    }
    
    // Handle back press for tabs - go to Dashboard first, then exit
    BackHandler(enabled = selectedTab != 0) {
        selectedTab = 0 // Go to Dashboard tab
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Column {
                        Text("Chatspromo CRM", color = Color(0xFF10B981), fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            if (isSyncing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(10.dp),
                                    strokeWidth = 1.5.dp,
                                    color = Color(0xFF64748B)
                                )
                            } else {
                                Icon(
                                    Icons.Default.Sync,
                                    contentDescription = null,
                                    modifier = Modifier.size(10.dp),
                                    tint = Color(0xFF64748B)
                                )
                            }
                            Text(
                                if (isSyncing) "Syncing..." else "Synced $lastSyncText • ${stats.totalLeads} leads",
                                fontSize = 10.sp,
                                color = Color(0xFF64748B)
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackPressed) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color(0xFF10B981))
                    }
                },
                actions = {
                    IconButton(onClick = { loadDataAsync() }) {
                        if (isSyncing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = Color(0xFF10B981)
                            )
                        } else {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = Color(0xFF10B981))
                        }
                    }
                    
                    Box {
                        IconButton(onClick = { showMenu = !showMenu }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Menu", tint = Color(0xFF10B981))
                        }
                        
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Add Lead") },
                                onClick = {
                                    showAddLeadScreen = true
                                    showMenu = false
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.PersonAdd, contentDescription = null, tint = Color(0xFF10B981))
                                }
                            )
                            Divider()
                            DropdownMenuItem(
                                text = { Text("Manage Tags") },
                                onClick = {
                                    showManagementScreen = ManagementType.TAGS
                                    showMenu = false
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.Label, contentDescription = null, tint = Color(0xFF10B981))
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Manage Sources") },
                                onClick = {
                                    showManagementScreen = ManagementType.SOURCES
                                    showMenu = false
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.Source, contentDescription = null, tint = Color(0xFF3B82F6))
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Manage Products") },
                                onClick = {
                                    showManagementScreen = ManagementType.PRODUCTS
                                    showMenu = false
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.Inventory, contentDescription = null, tint = Color(0xFFF59E0B))
                                }
                            )
                            Divider()
                            DropdownMenuItem(
                                text = { Text("Cloud Sync") },
                                onClick = {
                                    context.startActivity(android.content.Intent(context, com.message.bulksend.leadmanager.sync.CRMSyncActivity::class.java))
                                    showMenu = false
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.CloudSync, contentDescription = null, tint = Color(0xFF8B5CF6))
                                }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF1a1a2e))
            )
        },
        bottomBar = {
            // Scrollable Tab Bar with navigation bar padding and scroll indicator
            val scrollState = rememberScrollState()
            val showLeftIndicator by remember { derivedStateOf { scrollState.value > 20 } }
            val showRightIndicator by remember { derivedStateOf { scrollState.value < scrollState.maxValue - 20 } }
            
            Surface(
                color = Color(0xFF1a1a2e),
                shadowElevation = 8.dp,
                modifier = Modifier.navigationBarsPadding()
            ) {
                Column {
                    // Scroll indicator dots
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Left arrow indicator
                        AnimatedVisibility(
                            visible = showLeftIndicator,
                            enter = fadeIn(),
                            exit = fadeOut()
                        ) {
                            Icon(
                                Icons.Default.ChevronLeft,
                                contentDescription = "Scroll left",
                                tint = Color(0xFF64748B),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        
                        // Dot indicators
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.padding(horizontal = 8.dp)
                        ) {
                            repeat(6) { index ->
                                val isActive = index == selectedTab
                                Box(
                                    modifier = Modifier
                                        .size(if (isActive) 8.dp else 6.dp)
                                        .background(
                                            if (isActive) {
                                                when (index) {
                                                    0 -> Color(0xFF10B981)
                                                    1 -> Color(0xFF3B82F6)
                                                    2 -> Color(0xFFF59E0B)
                                                    3 -> Color(0xFF8B5CF6)
                                                    4 -> Color(0xFFEC4899)
                                                    else -> Color(0xFF64748B)
                                                }
                                            } else Color(0xFF64748B).copy(alpha = 0.4f),
                                            CircleShape
                                        )
                                )
                            }
                        }
                        
                        // Right arrow indicator
                        AnimatedVisibility(
                            visible = showRightIndicator,
                            enter = fadeIn(),
                            exit = fadeOut()
                        ) {
                            Icon(
                                Icons.Default.ChevronRight,
                                contentDescription = "Scroll right",
                                tint = Color(0xFF64748B),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    
                    // Tab items
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(scrollState)
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ScrollableTabItem(
                            icon = Icons.Default.Dashboard,
                            label = "Dashboard",
                            isSelected = selectedTab == 0,
                            color = Color(0xFF10B981),
                            onClick = { selectedTab = 0 }
                        )
                        ScrollableTabItem(
                            icon = Icons.Default.People,
                            label = "Leads",
                            isSelected = selectedTab == 1,
                            color = Color(0xFF3B82F6),
                            onClick = { selectedTab = 1 }
                        )
                        ScrollableTabItem(
                            icon = Icons.Default.Visibility,
                            label = "Overview",
                            isSelected = selectedTab == 2,
                            color = Color(0xFFF59E0B),
                            onClick = { selectedTab = 2 }
                        )
                        ScrollableTabItem(
                            icon = Icons.Default.Schedule,
                            label = "Scheduled",
                            isSelected = selectedTab == 3,
                            color = Color(0xFF8B5CF6),
                            onClick = { selectedTab = 3 }
                        )
                        ScrollableTabItem(
                            icon = Icons.Default.Assessment,
                            label = "Reports",
                            isSelected = selectedTab == 4,
                            color = Color(0xFFEC4899),
                            onClick = { selectedTab = 4 }
                        )
                        ScrollableTabItem(
                            icon = Icons.Default.Settings,
                            label = "Settings",
                            isSelected = selectedTab == 5,
                            color = Color(0xFF64748B),
                            onClick = { selectedTab = 5 }
                        )
                    }
                }
            }
        },
        containerColor = Color.Transparent
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(backgroundBrush)
                .padding(padding)
        ) {
            when (selectedTab) {
                0 -> DashboardScreen(
                    stats = stats,
                    onAddLeadClick = { showAddLeadScreen = true },
                    onImportLeadsClick = { 
                        context.startActivity(android.content.Intent(context, com.message.bulksend.leadmanager.importlead.ImportLeadActivity::class.java))
                    },
                    onSettingsClick = { selectedTab = 5 },
                    onProductsClick = { showManagementScreen = ManagementType.PRODUCTS },
                    onCustomFieldsClick = { showCustomFieldsScreen = true },
                    onCloudSyncClick = { 
                        context.startActivity(android.content.Intent(context, com.message.bulksend.leadmanager.sync.CRMSyncActivity::class.java))
                    },
                    onStatusClick = { status -> selectedStatusFilter = status }
                )
                1 -> LeadsListScreen(
                    leads = leads,
                    onLeadClick = { lead -> selectedLead = lead },
                    onStatusChange = { lead, newStatus ->
                        coroutineScope.launch {
                            val updatedLead = lead.copy(status = newStatus)
                            leadManager.updateLead(updatedLead)
                            refreshData()
                        }
                    },
                    onDeleteLead = { lead ->
                        coroutineScope.launch {
                            leadManager.deleteLead(lead.id)
                            refreshData()
                        }
                    }
                )
                2 -> com.message.bulksend.leadmanager.overview.OverviewScreen(
                    leads = leads,
                    onLeadClick = { lead -> selectedLead = lead },
                    onStatusChange = { lead, newStatus ->
                        coroutineScope.launch {
                            val updatedLead = lead.copy(status = newStatus)
                            leadManager.updateLead(updatedLead)
                            refreshData()
                        }
                    }
                )
                3 -> ScheduledScreen(
                    leads = leads,
                    onMarkComplete = { lead, followUp ->
                        coroutineScope.launch {
                            val updatedFollowUp = followUp.copy(isCompleted = true, completedDate = System.currentTimeMillis())
                            val updatedFollowUps = lead.followUps.map { if (it.id == followUp.id) updatedFollowUp else it }
                            leadManager.updateLead(lead.copy(followUps = updatedFollowUps))
                            refreshData()
                        }
                    },
                    onReschedule = { lead, followUp, newDate ->
                        coroutineScope.launch {
                            val updatedFollowUp = followUp.copy(scheduledDate = newDate)
                            val updatedFollowUps = lead.followUps.map { if (it.id == followUp.id) updatedFollowUp else it }
                            leadManager.updateLead(lead.copy(followUps = updatedFollowUps, nextFollowUpDate = newDate))
                            refreshData()
                        }
                    },
                    onLeadClick = { lead -> selectedLead = lead }
                )
                4 -> com.message.bulksend.leadmanager.screens.ReportsScreen(leads = leads)
                5 -> LeadSettingsScreen(
                    leadManager = leadManager,
                    onManageTags = { showManagementScreen = ManagementType.TAGS },
                    onManageSources = { showManagementScreen = ManagementType.SOURCES },
                    onManageProducts = { showManagementScreen = ManagementType.PRODUCTS },
                    onAutoAddSettings = { showAutoAddSettingsScreen = true },
                    onImportLeads = { 
                        context.startActivity(android.content.Intent(context, com.message.bulksend.leadmanager.importlead.ImportLeadActivity::class.java))
                    },
                    onCustomFields = { showCustomFieldsScreen = true }
                )
            }
        }
    }
}

@Composable
fun ScrollableTabItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    isSelected: Boolean,
    color: Color,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .clickable(onClick = onClick),
        color = if (isSelected) color.copy(alpha = 0.2f) else Color.Transparent,
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = label,
                tint = if (isSelected) color else Color(0xFF64748B),
                modifier = Modifier.size(20.dp)
            )
            Text(
                label,
                fontSize = 13.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                color = if (isSelected) color else Color(0xFF64748B)
            )
        }
    }
}


