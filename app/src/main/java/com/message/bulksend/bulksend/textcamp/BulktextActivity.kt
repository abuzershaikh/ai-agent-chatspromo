package com.message.bulksend.bulksend.textcamp



import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.message.bulksend.bulksend.*
import com.message.bulksend.contactmanager.Contact
import com.message.bulksend.contactmanager.ContactsRepository
import com.message.bulksend.contactmanager.ContactzActivity
import com.message.bulksend.contactmanager.Group
import com.message.bulksend.data.ContactStatus
import com.message.bulksend.db.AppDatabase
import com.message.bulksend.db.AutonomousSendQueueDao
import com.message.bulksend.db.Campaign
import com.message.bulksend.db.Setting
import com.message.bulksend.templates.TemplateActivity
import com.message.bulksend.templates.TemplateRepository
import com.message.bulksend.utils.CampaignAutoSendManager
import com.message.bulksend.utils.isAccessibilityServiceEnabled
import com.message.bulksend.utils.isPackageInstalled
import com.message.bulksend.utils.AccessibilityPermissionDialog
import com.message.bulksend.components.ScheduleCampaignDialog
import com.message.bulksend.utils.CampaignScheduleHelper
import com.message.bulksend.utils.findActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay

import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlin.math.ceil
import kotlin.math.roundToInt
import kotlin.random.Random

class BulktextActivity : ComponentActivity() {
    
    lateinit var overlayManager: com.message.bulksend.overlay.CampaignOverlayManager
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize overlay manager
        overlayManager = com.message.bulksend.overlay.CampaignOverlayManager(this)
        lifecycle.addObserver(overlayManager)
        
        setContent {
            WhatsAppCampaignTheme {
                TextCampaignManagerScreen()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TextCampaignManagerScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Database and Repositories
    val db = remember { AppDatabase.getInstance(context) }
    val campaignDao = remember { db.campaignDao() }
    val settingDao = remember { db.settingDao() }
    val autonomousQueueDao = remember { db.autonomousSendQueueDao() }
    val contactsRepository = remember { ContactsRepository(context) }
    val templateRepository = remember { TemplateRepository(context) }

    // States
    val groups by contactsRepository.loadGroups().collectAsState(initial = emptyList())
    var campaignName by remember { mutableStateOf("") }
    var selectedGroup by remember { mutableStateOf<Group?>(null) }
    var message by remember { mutableStateOf("") }
    var isSending by remember { mutableStateOf(false) }
    var whatsAppPreference by remember { mutableStateOf("WhatsApp") } // Default value
    var campaignProgress by remember { mutableStateOf(0f) }
    var sendingIndex by remember { mutableStateOf(0) }
    var campaignStatus by remember { mutableStateOf<List<ContactStatus>>(emptyList()) }
    var campaignError by remember { mutableStateOf<String?>(null) }
    var selectedDelay by remember { mutableStateOf("Fixed (5 sec)") }
    var uniqueIdentityEnabled by remember { mutableStateOf(false) }
    var showCustomDelayDialog by remember { mutableStateOf(false) }
    var activeTool by remember { mutableStateOf<String?>(null) }
    var toolInputText by remember { mutableStateOf("") }
    var selectedFancyFont by remember { mutableStateOf("Script") }
    var showToolInfoDialog by remember { mutableStateOf(false) }
    var isDropdownExpanded by remember { mutableStateOf(false) }
    var currentCampaignId by remember { mutableStateOf<String?>(null) }
    var resumableProgress by remember { mutableStateOf<Campaign?>(null) }
    var campaignToResumeLoaded by remember { mutableStateOf(false) }

    var isGroupStepExpanded by remember { mutableStateOf(true) }
    var showAccessibilityDialog by remember { mutableStateOf(false) }
    var showOverlayPermissionDialog by remember { mutableStateOf(false) }
    var isMessageStepExpanded by remember { mutableStateOf(true) }
    var hasLaunchedContactSelect by remember { mutableStateOf(false) }
    var countryCode by remember { mutableStateOf("") }
    var showCountryCodeInfoDialog by remember { mutableStateOf(false) }
    var showScheduleDialog by remember { mutableStateOf(false) }
    var showAutonomousRiskDialog by remember { mutableStateOf(false) }
    var autonomousRiskOverrideAccepted by remember { mutableStateOf(false) }
    var autonomousStats by remember { mutableStateOf(AutonomousExecutionStats()) }
    var autoPauseReason by remember { mutableStateOf<String?>(null) }

    val intent = context.findActivity()?.intent
    val campaignIdToResumeFromHistory = remember { intent?.getStringExtra("CAMPAIGN_ID_TO_RESUME") }
    val selectedGroupId = remember { intent?.getStringExtra("SELECTED_GROUP_ID") }
    val fromContactSelect = remember { intent?.getBooleanExtra("FROM_CONTACT_SELECT", false) ?: false }
    val incomingCampaignName = remember { intent?.getStringExtra("CAMPAIGN_NAME") }
    val incomingCountryCode = remember { intent?.getStringExtra("COUNTRY_CODE") }
    val incomingSelectedContactNumbers = remember { intent?.getStringArrayListExtra("SELECTED_CONTACTS") }
    val incomingSelectedContactNames = remember { intent?.getStringArrayListExtra("SELECTED_NAMES") ?: arrayListOf() }
    val incomingSelectedGroupName = remember { intent?.getStringExtra("SELECTED_GROUP_NAME") }
    val autonomousMessage = remember { intent?.getStringExtra("AUTONOMOUS_MESSAGE") }
    val autonomousUnique = remember { intent?.getBooleanExtra("AUTONOMOUS_UNIQUE", false) ?: false }
    var autonomousDays by remember { mutableStateOf((intent?.getIntExtra("AUTONOMOUS_DAYS", 0) ?: 0).coerceAtLeast(0)) }
    var autonomousRiskScore by remember { mutableStateOf(intent?.getIntExtra("AUTONOMOUS_RISK_SCORE", -1) ?: -1) }
    var isAutonomousMode by remember {
        mutableStateOf(
        autonomousDays > 0 || !autonomousMessage.isNullOrBlank()
        )
    }
    val autonomousRecommendedDays = remember(selectedGroup, isAutonomousMode) {
        if (!isAutonomousMode) 0 else recommendedAutonomousDays(selectedGroup?.contacts?.size ?: 0)
    }

    val progressAnimation by animateFloatAsState(
        targetValue = campaignProgress,
        animationSpec = tween(500),
        label = "progress"
    )
    
    // Setup overlay callbacks with state access
    LaunchedEffect(Unit) {
        val activity = context as? BulktextActivity
        activity?.overlayManager?.setOnStartCallback {
            // Resume campaign - overlay se start button click hua
            android.util.Log.d("BulktextActivity", "Campaign resumed from overlay")
        }
        
        activity?.overlayManager?.setOnStopCallback {
            // Pause campaign - overlay se stop button click hua
            android.util.Log.d("BulktextActivity", "Campaign paused from overlay")
        }
    }

    val templateSelectorLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val templateId = result.data?.getStringExtra("SELECTED_TEMPLATE_ID")
            if (templateId != null) {
                val template = templateRepository.getTemplateById(templateId)
                if (template != null) {
                    message = template.message
                    Toast.makeText(context, "Template '${template.name}' loaded!", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    val contactzActivityLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) {
        // Groups will be auto-refreshed by the Flow
    }
    
    val contactSelectLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val groupId = result.data?.getStringExtra("SELECTED_GROUP_ID")
            val selectedContactNumbers = result.data?.getStringArrayListExtra("SELECTED_CONTACTS")
            
            if (groupId != null) {
                // Full group selected
                val group = groups.find { it.id.toString() == groupId }
                if (group != null) {
                    selectedGroup = group
                    Toast.makeText(context, "Selected: ${group.name} (${group.contacts.size} contacts)", Toast.LENGTH_SHORT).show()
                }
            } else if (selectedContactNumbers != null) {
                // Specific contacts selected - create temporary group
                val selectedNames = result.data?.getStringArrayListExtra("SELECTED_NAMES") ?: arrayListOf()
                val tempContacts = selectedContactNumbers.mapIndexed { index, number ->
                    Contact(
                        name = selectedNames.getOrNull(index) ?: number,
                        number = number
                    )
                }
                selectedGroup = Group(
                    id = System.currentTimeMillis(),
                    name = "Selected Contacts (${tempContacts.size})",
                    contacts = tempContacts,
                    timestamp = System.currentTimeMillis()
                )
                Toast.makeText(context, "Selected ${tempContacts.size} contacts", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Load WhatsApp preference
    LaunchedEffect(Unit) {
        val pref = withContext(Dispatchers.IO) { db.settingDao().getSetting("whatsapp_preference") }
        whatsAppPreference = pref?.value ?: "WhatsApp"
    }

    // Effect to handle resuming a campaign from CampaignStatusActivity
    LaunchedEffect(groups, campaignIdToResumeFromHistory) {
        if (campaignIdToResumeFromHistory != null && groups.isNotEmpty() && !campaignToResumeLoaded) {
            val campaign = withContext(Dispatchers.IO) {
                campaignDao.getCampaignById(campaignIdToResumeFromHistory)
            }
            if (campaign != null) {
                val resumedAsAutonomous = campaign.campaignType == "BULKTEXT_AUTONOMOUS"
                isAutonomousMode = resumedAsAutonomous
                if (resumedAsAutonomous) {
                    val restoredDays = withContext(Dispatchers.IO) {
                        val queueDays =
                            autonomousQueueDao
                                .getAllForCampaign(campaign.id)
                                .maxOfOrNull { it.dayIndex + 1 } ?: 0
                        maxOf(
                            1,
                            queueDays,
                            recommendedAutonomousDays(campaign.totalContacts)
                        )
                    }
                    autonomousDays = restoredDays
                    autonomousRiskScore =
                        calculateAutonomousRiskScore(
                            totalContacts = campaign.totalContacts,
                            selectedDays = restoredDays
                        )
                } else {
                    autonomousDays = 0
                    autonomousRiskScore = -1
                }

                val group = groups.find { it.id.toString() == campaign.groupId }
                if (group != null) {
                    selectedGroup = group
                    campaignName = campaign.campaignName
                    message = campaign.message
                    resumableProgress = campaign
                    campaignStatus = campaign.contactStatuses
                    // Restore country code from saved campaign
                    countryCode = campaign.countryCode ?: ""
                    isGroupStepExpanded = false
                    campaignToResumeLoaded = true // Mark as loaded to prevent re-triggering
                } else {
                    val reconstructedContacts = campaign.contactStatuses.map { status ->
                        Contact(name = status.number, number = status.number)
                    }
                    if (reconstructedContacts.isNotEmpty()) {
                        selectedGroup = Group(
                            id = System.currentTimeMillis(),
                            name = "Selected Contacts (${reconstructedContacts.size})",
                            contacts = reconstructedContacts,
                            timestamp = campaign.timestamp
                        )
                        campaignName = campaign.campaignName
                        message = campaign.message
                        resumableProgress = campaign
                        campaignStatus = campaign.contactStatuses
                        countryCode = campaign.countryCode ?: ""
                        isGroupStepExpanded = false
                        campaignToResumeLoaded = true
                        Toast.makeText(context, "Resumed using saved selected contacts.", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Campaign group not found for resuming.", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }


    // Auto-launch ContactSelectActivity on first open (if not resuming campaign and not coming from ContactSelect)
    LaunchedEffect(Unit) {
        if (
            campaignIdToResumeFromHistory == null &&
            !hasLaunchedContactSelect &&
            selectedGroupId == null &&
            incomingSelectedContactNumbers.isNullOrEmpty()
        ) {
            hasLaunchedContactSelect = true
            val intent = Intent(context, ContactSelectActivity::class.java).apply {
                putExtra("TARGET_ACTIVITY", "BulktextActivity")
            }
            context.startActivity(intent)
            (context as? Activity)?.finish()
        }
    }

    LaunchedEffect(incomingSelectedContactNumbers, incomingSelectedContactNames, incomingSelectedGroupName, selectedGroupId) {
        if (selectedGroup == null && selectedGroupId == null && !incomingSelectedContactNumbers.isNullOrEmpty()) {
            val tempContacts = incomingSelectedContactNumbers.mapIndexed { index, number ->
                Contact(
                    name = incomingSelectedContactNames.getOrNull(index)?.takeIf { it.isNotBlank() } ?: number,
                    number = number
                )
            }
            selectedGroup = Group(
                id = System.currentTimeMillis(),
                name = incomingSelectedGroupName ?: "Selected Contacts (${tempContacts.size})",
                contacts = tempContacts,
                timestamp = System.currentTimeMillis()
            )
            if (incomingCampaignName != null) campaignName = incomingCampaignName
            if (incomingCountryCode != null) countryCode = incomingCountryCode
            hasLaunchedContactSelect = true
            Toast.makeText(context, "Selected ${tempContacts.size} contacts", Toast.LENGTH_SHORT).show()
        }
    }
    
    // Load selected group from ContactSelectActivity
    LaunchedEffect(groups, selectedGroupId) {
        if (selectedGroupId != null && groups.isNotEmpty() && selectedGroup == null) {
            val group = groups.find { it.id.toString() == selectedGroupId }
            if (group != null) {
                selectedGroup = group
                if (incomingCampaignName != null) campaignName = incomingCampaignName
                if (incomingCountryCode != null) countryCode = incomingCountryCode
                hasLaunchedContactSelect = true // Prevent re-launching
            }
        }
    }

    LaunchedEffect(isAutonomousMode, autonomousMessage, autonomousUnique) {
        if (isAutonomousMode) {
            if (!autonomousMessage.isNullOrBlank() && message.isBlank()) {
                message = autonomousMessage
            }
            uniqueIdentityEnabled = autonomousUnique
            selectedDelay = "Auto (AI Scheduler)"
            if (campaignName.isBlank() && selectedGroup != null) {
                campaignName = "Autonomous_${selectedGroup?.name ?: "Campaign"}"
            }
        }
    }

    // Effect for when a group is selected MANUALLY
    LaunchedEffect(selectedGroup) {
        // This should only run if we are NOT resuming from history
        if (campaignIdToResumeFromHistory == null) {
            // When a new group is selected, always treat it as a new campaign.
            // Clear any previous resumable progress and related fields.
            resumableProgress = null
            campaignStatus = emptyList()
            message = ""
            autonomousRiskOverrideAccepted = false
            // We can keep the campaignName if the user has already typed it.
        }
    }

    LaunchedEffect(isAutonomousMode, autonomousDays, selectedGroup?.contacts?.size) {
        if (!isAutonomousMode) return@LaunchedEffect
        val totalContacts = selectedGroup?.contacts?.size ?: 0
        if (totalContacts <= 0) return@LaunchedEffect
        autonomousRiskScore =
            calculateAutonomousRiskScore(
                totalContacts = totalContacts,
                selectedDays = autonomousDays.coerceAtLeast(1)
            )
    }

    LaunchedEffect(isAutonomousMode, isSending, currentCampaignId, autoPauseReason) {
        if (!isAutonomousMode) return@LaunchedEffect
        while (isSending && !currentCampaignId.isNullOrBlank()) {
            val stats = withContext(Dispatchers.IO) {
                loadAutonomousExecutionStats(
                    dao = autonomousQueueDao,
                    campaignId = currentCampaignId!!,
                    pauseReason = autoPauseReason
                )
            }
            autonomousStats = stats
            delay(3000)
        }
    }

    LaunchedEffect(
        isAutonomousMode,
        isSending,
        currentCampaignId,
        resumableProgress?.id,
        campaignIdToResumeFromHistory,
        whatsAppPreference
    ) {
        if (!isAutonomousMode || isSending) return@LaunchedEffect
        val targetCampaignId =
            currentCampaignId ?: resumableProgress?.id ?: campaignIdToResumeFromHistory
        if (targetCampaignId.isNullOrBlank()) return@LaunchedEffect

        val packageName =
            when (whatsAppPreference) {
                "WhatsApp" -> "com.whatsapp"
                "WhatsApp Business" -> "com.whatsapp.w4b"
                else -> null
            }
        val pauseReason =
            when {
                !isAccessibilityServiceEnabled(context) -> "Accessibility permission is off"
                !isInternetConnected(context) -> "No internet connection"
                packageName != null && !isPackageInstalled(context, packageName) ->
                    "$whatsAppPreference is unavailable"
                else -> null
            }
        autoPauseReason = pauseReason
        autonomousStats =
            withContext(Dispatchers.IO) {
                loadAutonomousExecutionStats(
                    dao = autonomousQueueDao,
                    campaignId = targetCampaignId,
                    pauseReason = pauseReason
                )
            }
    }


    Scaffold(
        topBar = {
            TopAppBar(
                modifier = Modifier.height(72.dp),
                title = {
                    Box(modifier = Modifier.fillMaxHeight(), contentAlignment = Alignment.Center) {
                        Text("Bulk Text Campaign", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    }
                },
                navigationIcon = {
                    Box(modifier = Modifier.fillMaxHeight(), contentAlignment = Alignment.Center) {
                        IconButton(onClick = { 
                            val intent = Intent(context, com.message.bulksend.MainActivity::class.java)
                            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                            context.startActivity(intent)
                            (context as? Activity)?.finish()
                        }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                },
                actions = {
                    Box(modifier = Modifier.fillMaxHeight(), contentAlignment = Alignment.Center) {
                        TopBarWhatsAppSelector(
                            selectedPreference = whatsAppPreference,
                            onPreferenceChange = {
                                whatsAppPreference = it
                                scope.launch(Dispatchers.IO) {
                                    db.settingDao().upsertSetting(Setting("whatsapp_preference", it))
                                }
                            },
                            expanded = isDropdownExpanded,
                            onExpandedChange = { isDropdownExpanded = it }
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(20.dp)
            ) {
                item {
                    StepCard(
                        stepNumber = 1,
                        title = "Campaign Setup",
                        icon = Icons.Filled.Campaign,
                        isCompleted = selectedGroup != null && campaignName.isNotBlank(),
                        isExpanded = isGroupStepExpanded,
                        onHeaderClick = { isGroupStepExpanded = !isGroupStepExpanded },
                        summaryContent = {
                            if (selectedGroup != null && campaignName.isNotBlank()) {
                                Column {
                                    Text(
                                        campaignName,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.primary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        "${selectedGroup!!.name} (${selectedGroup!!.contacts.size} contacts)",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                    )
                                }
                            }
                        }
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            OutlinedTextField(
                                value = campaignName,
                                onValueChange = { campaignName = it },
                                label = { Text("Campaign Name") },
                                placeholder = { Text("e.g., Holi Wishes") },
                                leadingIcon = { Icon(Icons.Outlined.Label, contentDescription = "Campaign Name Icon") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )

                            OutlinedTextField(
                                value = countryCode,
                                onValueChange = { countryCode = it },
                                label = { Text("Country Code (Required)") },
                                placeholder = { Text("e.g., +91 for India") },
                                leadingIcon = { Icon(Icons.Outlined.Phone, contentDescription = "Country Code") },
                                trailingIcon = {
                                    IconButton(onClick = { showCountryCodeInfoDialog = true }) {
                                        Icon(Icons.Outlined.Info, contentDescription = "Country Code Info")
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )

                            ImportButton(
                                text = "Add/Manage Lists",
                                icon = Icons.Filled.GroupAdd,
                                onClick = {
                                    contactzActivityLauncher.launch(Intent(context, ContactzActivity::class.java))
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                            
                            // Contact Selection Button
                            Button(
                                onClick = {
                                    val intent = Intent(context, ContactSelectActivity::class.java).apply {
                                        putExtra("IS_FOR_SELECTION", true)
                                        putExtra("CALLING_ACTIVITY", "BulktextActivity")
                                    }
                                    contactSelectLauncher.launch(intent)
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(56.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (selectedGroup != null) 
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                                    else 
                                        MaterialTheme.colorScheme.primary
                                ),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                            ) {
                                Icon(
                                    Icons.Filled.Group,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    if (selectedGroup != null) 
                                        "${selectedGroup!!.name} (${selectedGroup!!.contacts.size} contacts)"
                                    else 
                                        "Select Contact Group",
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
                item {
                    StepCard(
                        stepNumber = 2,
                        title = "Compose Message",
                        icon = Icons.Filled.Message,
                        isCompleted = message.isNotBlank(),
                        isExpanded = isMessageStepExpanded,
                        onHeaderClick = { isMessageStepExpanded = !isMessageStepExpanded }
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                AssistChip(
                                    onClick = {
                                        val intent = Intent(context, TemplateActivity::class.java).apply {
                                            putExtra("IS_FOR_SELECTION", true)
                                        }
                                        templateSelectorLauncher.launch(intent)
                                    },
                                    label = { Text("Use Template") },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Outlined.Style,
                                            contentDescription = "Use Template",
                                            modifier = Modifier.size(AssistChipDefaults.IconSize)
                                        )
                                    }
                                )
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    Text("Unique ID", fontSize = 14.sp)
                                    Spacer(Modifier.width(4.dp))
                                    Switch(
                                        checked = uniqueIdentityEnabled,
                                        onCheckedChange = { uniqueIdentityEnabled = it }
                                    )
                                }
                            }
                            if (!isAutonomousMode) {
                                DelaySelector(
                                    selectedDelay = selectedDelay,
                                    onDelaySelected = { selectedDelay = it },
                                    onCustomClick = { showCustomDelayDialog = true }
                                )
                            } else {
                                Text(
                                    text = "Autonomous mode: app auto-plans day/hour slots (5-7 per hour, max 24 per day).",
                                    color = MaterialTheme.colorScheme.primary,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            BulkTextMessageComposerWithTools(
                                value = message,
                                onValueChange = { message = it },
                                activeTool = activeTool,
                                onActiveToolChange = { tool -> activeTool = if (activeTool == tool) null else tool },
                                toolInputText = toolInputText,
                                onToolInputChange = { toolInputText = it },
                                selectedFancyFont = selectedFancyFont,
                                onFancyFontChange = { selectedFancyFont = it },
                                onInfoClick = { showToolInfoDialog = true }
                            )
                        }
                    }
                }
                item {
                    TextCampaignSummaryCard(
                        campaignName = campaignName,
                        selectedGroup = selectedGroup,
                        message = message,
                        isSending = isSending,
                        progress = progressAnimation,
                        sendingIndex = sendingIndex,
                        campaignStatus = campaignStatus,
                        onLaunchCampaign = { isResuming ->
                            if (campaignName.isBlank()) {
                                Toast.makeText(context, "Please add campaign name first", Toast.LENGTH_SHORT).show()
                                return@TextCampaignSummaryCard
                            }
                            val group = selectedGroup
                            if (group == null) {
                                Toast.makeText(context, "Please select a contact group", Toast.LENGTH_SHORT).show()
                                return@TextCampaignSummaryCard
                            }
                            if (message.isBlank()){
                                Toast.makeText(context, "Please write a message to send", Toast.LENGTH_SHORT).show()
                                return@TextCampaignSummaryCard
                            }

                            // Country code is mandatory
                            if (countryCode.isBlank()) {
                                campaignError = "Country code is required. Please enter a country code (e.g., +91)."
                                return@TextCampaignSummaryCard
                            }

                            if (
                                isAutonomousMode &&
                                autonomousDays > 0 &&
                                group.contacts.isNotEmpty() &&
                                autonomousDays < autonomousRecommendedDays &&
                                !autonomousRiskOverrideAccepted
                            ) {
                                showAutonomousRiskDialog = true
                                return@TextCampaignSummaryCard
                            }

                            // Check overlay permission first
                            if (!com.message.bulksend.overlay.OverlayHelper.hasOverlayPermission(context)) {
                                showOverlayPermissionDialog = true
                                return@TextCampaignSummaryCard
                            }

                            if (!isAccessibilityServiceEnabled(context)) {
                                showAccessibilityDialog = true
                                return@TextCampaignSummaryCard
                            }
                            val packageName = when (whatsAppPreference) {
                                "WhatsApp" -> "com.whatsapp"
                                "WhatsApp Business" -> "com.whatsapp.w4b"
                                else -> null
                            }
                            if (packageName != null && !isPackageInstalled(context, packageName)) {
                                campaignError = "$whatsAppPreference is not installed on your device."
                                return@TextCampaignSummaryCard
                            }

                            scope.launch {
                                isSending = true
                                var campaignStoppedPrematurely = false

                                val campaignToRun = if (isResuming && resumableProgress != null) {
                                    resumableProgress!!.copy(isStopped = false, isRunning = true)
                                } else {
                                    Campaign(
                                        id = UUID.randomUUID().toString(),
                                        groupId = group.id.toString(),
                                        campaignName = campaignName,
                                        message = message,
                                        timestamp = System.currentTimeMillis(),
                                        totalContacts = group.contacts.size,
                                        contactStatuses = group.contacts.map { ContactStatus(it.number, "pending") },
                                        isStopped = false,
                                        isRunning = true,
                                        campaignType = if (isAutonomousMode) "BULKTEXT_AUTONOMOUS" else "BULKTEXT",
                                        countryCode = countryCode
                                    )
                                }

                                currentCampaignId = campaignToRun.id
                                withContext(Dispatchers.IO) { campaignDao.upsertCampaign(campaignToRun) }

                                // Campaign launch hone par auto-send service enable karein
                                CampaignAutoSendManager.onCampaignLaunched(campaignToRun)

                                // Start overlay with campaign
                                (context as? BulktextActivity)?.overlayManager?.startCampaignWithOverlay(campaignToRun.totalContacts)

                                val contactsToSend = campaignToRun.contactStatuses.filter { it.status == "pending" }

                                // Reset stop flag before starting campaign
                                CampaignState.shouldStop = false
                                
                                try {
                                    if (isAutonomousMode) {
                                        val plannedDays = autonomousDays.coerceAtLeast(1)
                                        val queuedEntries = withContext(Dispatchers.IO) {
                                            val existingQueue = autonomousQueueDao.getQueuedForCampaign(campaignToRun.id)
                                            if (existingQueue.isEmpty() && contactsToSend.isNotEmpty()) {
                                                val pendingContacts = contactsToSend.mapNotNull { status ->
                                                    group.contacts.find { it.number == status.number }
                                                }
                                                val plan = buildAutonomousQueuePlan(
                                                    campaignId = campaignToRun.id,
                                                    contacts = pendingContacts,
                                                    selectedDays = plannedDays
                                                )
                                                autonomousQueueDao.upsertAll(plan)
                                            }
                                            autonomousQueueDao.getQueuedForCampaign(campaignToRun.id)
                                        }

                                        autonomousStats = withContext(Dispatchers.IO) {
                                            loadAutonomousExecutionStats(
                                                dao = autonomousQueueDao,
                                                campaignId = campaignToRun.id,
                                                pauseReason = autoPauseReason
                                            )
                                        }

                                        for (queueEntry in queuedEntries) {
                                            if (CampaignState.shouldStop) {
                                                campaignStoppedPrematurely = true
                                                break
                                            }

                                            while (true) {
                                                val reason = when {
                                                    !isAccessibilityServiceEnabled(context) -> "Accessibility permission is off"
                                                    !isInternetConnected(context) -> "No internet connection"
                                                    packageName != null && !isPackageInstalled(context, packageName) ->
                                                        "$whatsAppPreference is unavailable"
                                                    else -> null
                                                }
                                                autoPauseReason = reason
                                                if (reason == null) break
                                                autonomousStats = withContext(Dispatchers.IO) {
                                                    loadAutonomousExecutionStats(
                                                        dao = autonomousQueueDao,
                                                        campaignId = campaignToRun.id,
                                                        pauseReason = reason
                                                    )
                                                }
                                                delay(5000)
                                                if (CampaignState.shouldStop) {
                                                    campaignStoppedPrematurely = true
                                                    break
                                                }
                                            }
                                            if (campaignStoppedPrematurely) break

                                            while (System.currentTimeMillis() < queueEntry.plannedTimeMillis) {
                                                if (CampaignState.shouldStop) {
                                                    campaignStoppedPrematurely = true
                                                    break
                                                }
                                                val remaining = queueEntry.plannedTimeMillis - System.currentTimeMillis()
                                                delay(minOf(30_000L, maxOf(500L, remaining)))
                                            }
                                            if (campaignStoppedPrematurely) break

                                            val currentState = withContext(Dispatchers.IO) { campaignDao.getCampaignById(currentCampaignId!!) }
                                            if (currentState == null || currentState.isStopped) {
                                                campaignStoppedPrematurely = true
                                                break
                                            }

                                            sendingIndex = currentState.sentCount + currentState.failedCount + 1
                                            campaignProgress = sendingIndex.toFloat() / currentState.totalContacts
                                            (context as? BulktextActivity)?.overlayManager?.updateProgress(sendingIndex, currentState.totalContacts)

                                            val contact = group.contacts.find { it.number == queueEntry.contactNumber }
                                            if (contact == null) {
                                                withContext(Dispatchers.IO) {
                                                    autonomousQueueDao.updateDeliveryStatus(
                                                        id = queueEntry.id,
                                                        status = "failed",
                                                        retryCount = queueEntry.retryCount,
                                                        lastError = "contact_not_found",
                                                        sentTimeMillis = null
                                                    )
                                                }
                                                continue
                                            }

                                            CampaignState.isSendActionSuccessful = null
                                            CampaignState.sendFailureReason = null
                                            withContext(Dispatchers.Main) {
                                                Toast.makeText(
                                                    context,
                                                    "Auto send $sendingIndex/${currentState.totalContacts}: ${contact.name}",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            }

                                            val finalNumber = if (contact.number.startsWith("+")) {
                                                contact.number.replace(Regex("[^\\d+]"), "")
                                            } else {
                                                val cleanCode = countryCode.replace(Regex("[^\\d+]"), "")
                                                val cleanNum = contact.number.replace(Regex("[^\\d]"), "")
                                                "$cleanCode$cleanNum"
                                            }
                                            val cleanNumber = finalNumber.replace("+", "")
                                            val baseMessage = if (uniqueIdentityEnabled) message + "\n\n" + generateRandomString() else message
                                            val personalizedMessage = baseMessage.replace("#name#", contact.name, ignoreCase = true)
                                            val encodedMessage = URLEncoder.encode(personalizedMessage, "UTF-8")
                                            val waIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$cleanNumber?text=$encodedMessage"))
                                            waIntent.setPackage(packageName)
                                            waIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                            context.startActivity(waIntent)

                                            val startTime = System.currentTimeMillis()
                                            var confirmationReceived = false
                                            while (System.currentTimeMillis() - startTime < 7000L) {
                                                if (CampaignState.shouldStop) {
                                                    campaignStoppedPrematurely = true
                                                    break
                                                }
                                                if (CampaignState.isSendActionSuccessful == true) {
                                                    confirmationReceived = true
                                                    break
                                                }
                                                if (CampaignState.isSendActionSuccessful == false) break
                                                delay(100)
                                            }
                                            if (campaignStoppedPrematurely) break

                                            val failureReason = if (!confirmationReceived &&
                                                CampaignState.sendFailureReason == CampaignState.FAILURE_NOT_ON_WHATSAPP
                                            ) {
                                                "not_on_whatsapp"
                                            } else {
                                                CampaignState.sendFailureReason
                                            }

                                            withContext(Dispatchers.IO) {
                                                if (confirmationReceived) {
                                                    campaignDao.updateContactStatus(
                                                        currentCampaignId!!,
                                                        contact.number,
                                                        "sent",
                                                        null
                                                    )
                                                    autonomousQueueDao.updateDeliveryStatus(
                                                        id = queueEntry.id,
                                                        status = "sent",
                                                        retryCount = queueEntry.retryCount,
                                                        lastError = null,
                                                        sentTimeMillis = System.currentTimeMillis()
                                                    )
                                                } else if (queueEntry.retryCount < 2) {
                                                    val (nextTime, nextHour) = computeNextRetryPlan(System.currentTimeMillis())
                                                    autonomousQueueDao.requeueWithNewPlan(
                                                        id = queueEntry.id,
                                                        retryCount = queueEntry.retryCount + 1,
                                                        plannedTimeMillis = nextTime,
                                                        hourOfDay = nextHour,
                                                        lastError = failureReason ?: "send_failed"
                                                    )
                                                } else {
                                                    campaignDao.updateContactStatus(
                                                        currentCampaignId!!,
                                                        contact.number,
                                                        "failed",
                                                        failureReason
                                                    )
                                                    autonomousQueueDao.updateDeliveryStatus(
                                                        id = queueEntry.id,
                                                        status = "failed",
                                                        retryCount = queueEntry.retryCount + 1,
                                                        lastError = failureReason ?: "send_failed",
                                                        sentTimeMillis = null
                                                    )
                                                }
                                            }

                                            val updatedCampaign = withContext(Dispatchers.IO) { campaignDao.getCampaignById(currentCampaignId!!) }
                                            if (updatedCampaign != null) {
                                                campaignStatus = updatedCampaign.contactStatuses
                                            }
                                            autonomousStats = withContext(Dispatchers.IO) {
                                                loadAutonomousExecutionStats(
                                                    dao = autonomousQueueDao,
                                                    campaignId = campaignToRun.id,
                                                    pauseReason = autoPauseReason
                                                )
                                            }
                                            delay(500)
                                        }
                                    } else {
                                        for (contactStatus in contactsToSend) {
                                            // Check if stopped by overlay - immediate break
                                            if (CampaignState.shouldStop) {
                                                android.util.Log.d("BulktextActivity", "Campaign stopped by overlay (CampaignState.shouldStop) - breaking loop")
                                                campaignStoppedPrematurely = true
                                                break
                                            }
                                            
                                            val currentState = withContext(Dispatchers.IO) { campaignDao.getCampaignById(currentCampaignId!!) }
                                            if (currentState == null || currentState.isStopped) {
                                                campaignStoppedPrematurely = true
                                                break
                                            }

                                            sendingIndex = currentState.sentCount + currentState.failedCount + 1
                                            campaignProgress = sendingIndex.toFloat() / currentState.totalContacts
                                            
                                            // Update overlay progress
                                            (context as? BulktextActivity)?.overlayManager?.updateProgress(sendingIndex, currentState.totalContacts)
                                            
                                            val contact = group.contacts.find { it.number == contactStatus.number } ?: continue
                                            CampaignState.isSendActionSuccessful = null
                                            CampaignState.sendFailureReason = null

                                            withContext(Dispatchers.Main) {
                                                Toast.makeText(context, "Sending to $sendingIndex/${currentState.totalContacts}: ${contact.name}", Toast.LENGTH_SHORT).show()
                                            }

                                            // Add country code if number doesn't start with +
                                            val finalNumber = if (contact.number.startsWith("+")) {
                                                contact.number.replace(Regex("[^\\d+]"), "")
                                            } else {
                                                val cleanCode = countryCode.replace(Regex("[^\\d+]"), "")
                                                val cleanNum = contact.number.replace(Regex("[^\\d]"), "")
                                                "$cleanCode$cleanNum"
                                            }
                                            val cleanNumber = finalNumber.replace("+", "")
                                            val baseMessage = if (uniqueIdentityEnabled) message + "\n\n" + generateRandomString() else message
                                            val personalizedMessage = baseMessage.replace("#name#", contact.name, ignoreCase = true)
                                            val encodedMessage = URLEncoder.encode(personalizedMessage, "UTF-8")
                                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$cleanNumber?text=$encodedMessage"))
                                            intent.setPackage(packageName)
                                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                            context.startActivity(intent)

                                            // Wait for confirmation
                                            val startTime = System.currentTimeMillis()
                                            var confirmationReceived = false
                                            while (System.currentTimeMillis() - startTime < 7000L) {
                                                // Check if stopped during confirmation wait
                                                if (CampaignState.shouldStop) {
                                                    android.util.Log.d("BulktextActivity", "Campaign stopped during confirmation wait (CampaignState.shouldStop)")
                                                    campaignStoppedPrematurely = true
                                                    break
                                                }
                                                if (CampaignState.isSendActionSuccessful == true) {
                                                    confirmationReceived = true
                                                    break
                                                }
                                                if (CampaignState.isSendActionSuccessful == false) break
                                                delay(100)
                                            }
                                            if (campaignStoppedPrematurely) break

                                            val finalStatus = if (confirmationReceived) "sent" else "failed"
                                            val failureReason = if (!confirmationReceived &&
                                                CampaignState.sendFailureReason == CampaignState.FAILURE_NOT_ON_WHATSAPP
                                            ) {
                                                "not_on_whatsapp"
                                            } else {
                                                null
                                            }
                                            withContext(Dispatchers.IO) {
                                                campaignDao.updateContactStatus(
                                                    currentCampaignId!!,
                                                    contact.number,
                                                    finalStatus,
                                                    failureReason
                                                )
                                            }

                                            // Update local status for UI
                                            val updatedCampaign = withContext(Dispatchers.IO) { campaignDao.getCampaignById(currentCampaignId!!) }
                                            if (updatedCampaign != null) {
                                                campaignStatus = updatedCampaign.contactStatuses
                                            }

                                            val delayMillis = if (selectedDelay.startsWith("Custom")) {
                                                try { selectedDelay.substringAfter("(").substringBefore(" sec").trim().toLong() * 1000 } catch (e: Exception) { 5000L }
                                            } else if (selectedDelay.startsWith("Random")) {
                                                Random.nextLong(5000, 15001)
                                            } else {
                                                try { selectedDelay.split(" ")[0].toLong() * 1000 } catch(e: Exception) { 5000L }
                                            }
                                            
                                            // Check if stopped during delay - break immediately
                                            val totalDelay = maxOf(3000L, delayMillis)
                                            val delayChunks = (totalDelay / 500).toInt()
                                            for (i in 0 until delayChunks) {
                                                if (CampaignState.shouldStop) {
                                                    android.util.Log.d("BulktextActivity", "Campaign stopped during delay (CampaignState.shouldStop) - breaking loop")
                                                    campaignStoppedPrematurely = true
                                                    break
                                                }
                                                delay(500)
                                            }
                                            if (campaignStoppedPrematurely) break
                                        }
                                    }
                                } finally {
                                    // Reset stop flag
                                    CampaignState.shouldStop = false
                                    
                                    // Stop overlay when campaign ends
                                    (context as? BulktextActivity)?.overlayManager?.stopCampaign()
                                    
                                    val finalState = withContext(Dispatchers.IO) { campaignDao.getCampaignById(currentCampaignId!!) }
                                    if (finalState != null) {
                                        val pendingQueueCount = if (isAutonomousMode) {
                                            withContext(Dispatchers.IO) {
                                                autonomousQueueDao.countByStatus(finalState.id, "queued")
                                            }
                                        } else {
                                            0
                                        }
                                        val hasPendingAutonomousQueue = isAutonomousMode && pendingQueueCount > 0
                                        val finishedCampaign = finalState.copy(
                                            isRunning = false,
                                            isStopped = campaignStoppedPrematurely || hasPendingAutonomousQueue
                                        )
                                        withContext(Dispatchers.IO) { campaignDao.upsertCampaign(finishedCampaign) }

                                        if (campaignStoppedPrematurely || hasPendingAutonomousQueue) {
                                            CampaignAutoSendManager.onCampaignStopped(finishedCampaign)
                                            resumableProgress = finishedCampaign
                                            if (hasPendingAutonomousQueue) {
                                                Toast.makeText(
                                                    context,
                                                    "Autonomous campaign paused with pending queue. Resume later safely.",
                                                    Toast.LENGTH_LONG
                                                ).show()
                                            } else {
                                                Toast.makeText(context, "Campaign stopped. Progress saved.", Toast.LENGTH_SHORT).show()
                                            }
                                        } else {
                                            CampaignAutoSendManager.onCampaignCompleted(finishedCampaign)
                                            resumableProgress = null
                                            campaignStatus = emptyList()
                                            Toast.makeText(context, "Campaign finished!", Toast.LENGTH_LONG).show()
                                            message = ""
                                            campaignName = ""
                                            selectedGroup = null
                                        }
                                    }
                                    isSending = false
                                    autoPauseReason = null
                                    currentCampaignId = null
                                }
                            }
                        },
                        onStartOver = {
                            // Don't delete from the database. Just clear the UI to start a new campaign.
                            // The existing campaign will remain in CampaignStatusActivity for later resuming.
                            resumableProgress = null
                            campaignStatus = emptyList()
                            message = ""
                            campaignName = ""
                            selectedGroup = null // Important to clear the group selection as well
                            autonomousRiskOverrideAccepted = false
                            Toast.makeText(context, "Cleared. You can start a new campaign.", Toast.LENGTH_SHORT).show()
                        },
                        onScheduleCampaign = {
                            showScheduleDialog = true
                        }
                    )
                }

                if (isAutonomousMode) {
                    item {
                        AutonomousExecutionDashboardCard(
                            stats = autonomousStats,
                            recommendedDays = autonomousRecommendedDays,
                            selectedDays = autonomousDays.coerceAtLeast(1),
                            riskScore = autonomousRiskScore
                        )
                    }
                }

                // Stop/Resume buttons removed - now controlled via overlay only
            }

            PulsingRippleOverlay(visible = isDropdownExpanded)
        }
    }

    // ResumeConfirmationDialog and StopConfirmationDialog removed - controlled via overlay


    if (showCustomDelayDialog) {
        CustomDelayDialog(
            onDismiss = { showCustomDelayDialog = false },
            onConfirm = { delayInSeconds ->
                if (delayInSeconds >= 3) {
                    selectedDelay = "Custom ($delayInSeconds sec)"
                    showCustomDelayDialog = false
                } else {
                    Toast.makeText(context, "Minimum delay is 3 seconds.", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    if (showToolInfoDialog) {
        ToolInfoDialog(onDismiss = { showToolInfoDialog = false })
    }

    if (showAutonomousRiskDialog) {
        AlertDialog(
            onDismissRequest = { showAutonomousRiskDialog = false },
            icon = { Icon(Icons.Outlined.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("High-Risk Configuration") },
            text = {
                Text(
                    "Selected days are below recommended safe days ($autonomousRecommendedDays). " +
                        "System will keep hourly cap and may continue into extra time. Continue?"
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        autonomousRiskOverrideAccepted = true
                        showAutonomousRiskDialog = false
                        Toast.makeText(context, "Risk override accepted.", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text("Continue")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAutonomousRiskDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (campaignError != null) {
        AlertDialog(
            onDismissRequest = { campaignError = null },
            icon = { Icon(Icons.Filled.Error, contentDescription = null) },
            title = { Text("Campaign Error") },
            text = { Text(campaignError!!) },
            confirmButton = {
                Button(onClick = {
                    if (campaignError?.contains("Accessibility Service") == true) {
                        context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    }
                    campaignError = null
                }) {
                    Text("OK")
                }
            }
        )
    }

    // Overlay Permission Dialog
    if (showOverlayPermissionDialog) {
        AlertDialog(
            onDismissRequest = { showOverlayPermissionDialog = false },
            icon = { 
                Icon(
                    Icons.Outlined.Layers, 
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                ) 
            },
            title = { 
                Text(
                    "Overlay Permission Required",
                    fontWeight = FontWeight.Bold
                ) 
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "Overlay permission is required for campaign control.",
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "The overlay allows you to pause and resume campaigns without opening the app:",
                        fontSize = 14.sp
                    )
                    Column(
                        modifier = Modifier.padding(start = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(verticalAlignment = Alignment.Top) {
                            Text("• ", fontWeight = FontWeight.Bold)
                            Text("Overlay appears on screen when campaign is running", fontSize = 13.sp)
                        }
                        Row(verticalAlignment = Alignment.Top) {
                            Text("• ", fontWeight = FontWeight.Bold)
                            Text("Control campaign with Stop/Start button", fontSize = 13.sp)
                        }
                        Row(verticalAlignment = Alignment.Top) {
                            Text("• ", fontWeight = FontWeight.Bold)
                            Text("View real-time progress", fontSize = 13.sp)
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Enable 'Display over other apps' permission in Settings.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showOverlayPermissionDialog = false
                        com.message.bulksend.overlay.OverlayHelper.requestOverlayPermission(context)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text("I Agree")
                }
            },
            dismissButton = {
                TextButton(onClick = { showOverlayPermissionDialog = false }) {
                    Text("Cancel")
                }
            },
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp
        )
    }

    if (showCountryCodeInfoDialog) {
        AlertDialog(
            onDismissRequest = { showCountryCodeInfoDialog = false },
            icon = { Icon(Icons.Outlined.Info, contentDescription = null) },
            title = { Text("Country Code Information") },
            text = {
                Text(
                    "📱 Country code is required for all contacts.\n\n" +
                            "Examples:\n" +
                            "• India: +91\n" +
                            "• USA: +1\n" +
                            "• UK: +44\n" +
                            "• UAE: +971\n\n" +
                            "The country code will be added to numbers that don't already have a + prefix."
                )
            },
            confirmButton = {
                Button(onClick = { showCountryCodeInfoDialog = false }) {
                    Text("OK")
                }
            }
        )
    }

    // Schedule Campaign Dialog
    ScheduleCampaignDialog(
        isVisible = showScheduleDialog,
        campaignName = campaignName,
        onDismiss = { showScheduleDialog = false },
        onSchedule = { scheduledTime ->
            val group = selectedGroup
            if (group != null) {
                CampaignScheduleHelper.scheduleTextCampaign(
                    context = context,
                    campaignName = campaignName,
                    message = message,
                    contacts = group.contacts,
                    groupId = group.id.toString(),
                    groupName = group.name,
                    countryCode = countryCode,
                    delaySettings = selectedDelay,
                    uniqueIdEnabled = uniqueIdentityEnabled,
                    whatsAppPreference = whatsAppPreference,
                    scheduledTime = scheduledTime,
                    onSuccess = {
                        showScheduleDialog = false
                        // Navigate back to main activity
                        val intent = Intent(context, com.message.bulksend.MainActivity::class.java)
                        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                        context.startActivity(intent)
                        (context as? Activity)?.finish()
                    },
                    onError = { error ->
                        Toast.makeText(context, error, Toast.LENGTH_LONG).show()
                    }
                )
            }
        }
    )

    // Accessibility Permission Dialog
    if (showAccessibilityDialog) {
        AccessibilityPermissionDialog(
            onAgree = {
                Toast.makeText(context, "Please enable permission in Settings", Toast.LENGTH_SHORT).show()
            },
            onDisagree = {
                Toast.makeText(context, "Accessibility permission is required", Toast.LENGTH_SHORT).show()
            },
            onDismiss = {
                showAccessibilityDialog = false
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopBarWhatsAppSelector(
    selectedPreference: String,
    onPreferenceChange: (String) -> Unit,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit
) {
    val options = listOf("WhatsApp Business", "WhatsApp")

    Box(modifier = Modifier.padding(end = 8.dp)) {
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = onExpandedChange
        ) {
            Row(
                modifier = Modifier
                    .menuAnchor()
                    .clickable { onExpandedChange(!expanded) }
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), RoundedCornerShape(50))
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(selectedPreference, color = MaterialTheme.colorScheme.primary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Icon(
                    imageVector = if (expanded) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown,
                    contentDescription = "Dropdown",
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { onExpandedChange(false) },
                modifier = Modifier.background(MaterialTheme.colorScheme.surface)
            ) {
                options.forEach { selectionOption ->
                    DropdownMenuItem(
                        text = { Text(selectionOption) },
                        onClick = {
                            onPreferenceChange(selectionOption)
                            onExpandedChange(false)
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun PulsingRippleOverlay(visible: Boolean) {
    val infiniteTransition = rememberInfiniteTransition(label = "ripple_transition")

    val scale by infiniteTransition.animateFloat(
        initialValue = 0.1f,
        targetValue = 8f, // Scale to a large size
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ), label = "ripple_scale"
    )

    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 0f, // Fade out
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ), label = "ripple_alpha"
    )

    AnimatedVisibility(visible = visible, enter =fadeIn(), exit = fadeOut()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 36.dp, end = 60.dp) // Position near dropdown
                    .scale(scale)
                    .size(100.dp) // Initial size
                    .border(
                        width = 2.dp,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = alpha),
                        shape = CircleShape
                    )
            )
        }
    }
}

@Composable
fun BulkTextMessageComposerWithTools(
    value: String,
    onValueChange: (String) -> Unit,
    activeTool: String?,
    onActiveToolChange: (String?) -> Unit,
    toolInputText: String,
    onToolInputChange: (String) -> Unit,
    selectedFancyFont: String,
    onFancyFontChange: (String) -> Unit,
    onInfoClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isFontDropdownExpanded by remember { mutableStateOf(false) }
    val fancyFonts = listOf("Script", "Bold Fraktur", "Monospace", "Small Caps", "Cursive")

    Column(modifier = modifier) {
        Box {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp),
                placeholder = {
                    Text(
                        "Enter message here...",
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                },
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                    cursorColor = MaterialTheme.colorScheme.primary
                )
            )
            IconButton(
                onClick = onInfoClick,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
            ) {
                Icon(Icons.Outlined.Info, contentDescription = "Tool Information", tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
            }
        }


        Spacer(Modifier.height(8.dp))

        // Tool buttons with vibrant colors
        Column(Modifier.padding(horizontal = 4.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // #name# button
                IconButton(
                    onClick = { onValueChange(value + "#name#") }
                ) {
                    Icon(
                        Icons.Default.Tag, "#name#",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
                // Bold button
                IconButton(
                    onClick = { onActiveToolChange("bold") },
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(
                            if (activeTool == "bold") MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                            else Color.Transparent
                        )
                ) {
                    Icon(
                        Icons.Default.FormatBold, "Bold",
                        tint = if (activeTool == "bold") MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface
                    )
                }

                // Italic button
                IconButton(
                    onClick = { onActiveToolChange("italic") },
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(
                            if (activeTool == "italic") MaterialTheme.colorScheme.secondary.copy(alpha = 0.3f)
                            else Color.Transparent
                        )
                ) {
                    Icon(
                        Icons.Default.FormatItalic, "Italic",
                        tint = if (activeTool == "italic") MaterialTheme.colorScheme.secondary
                        else MaterialTheme.colorScheme.onSurface
                    )
                }

                // Strikethrough button
                IconButton(
                    onClick = { onActiveToolChange("strikethrough") },
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(
                            if (activeTool == "strikethrough") MaterialTheme.colorScheme.tertiary.copy(alpha = 0.3f)
                            else Color.Transparent
                        )
                ) {
                    Icon(
                        Icons.Default.FormatStrikethrough, "Strikethrough",
                        tint = if (activeTool == "strikethrough") MaterialTheme.colorScheme.tertiary
                        else MaterialTheme.colorScheme.onSurface
                    )
                }

                // Fancy font button
                Box {
                    IconButton(
                        onClick = {
                            isFontDropdownExpanded = true
                            onActiveToolChange("fancy")
                        },
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(
                                if (activeTool == "fancy") MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                                else Color.Transparent
                            )
                    ) {
                        Icon(
                            Icons.Default.TextFields, "Fancy Font",
                            tint = if (activeTool == "fancy") MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface
                        )
                    }
                    DropdownMenu(
                        expanded = isFontDropdownExpanded,
                        onDismissRequest = { isFontDropdownExpanded = false },
                        modifier = Modifier.background(MaterialTheme.colorScheme.surface)
                    ) {
                        fancyFonts.forEach { font ->
                            DropdownMenuItem(
                                text = { Text(font, color = MaterialTheme.colorScheme.onSurface) },
                                onClick = {
                                    onFancyFontChange(font)
                                    isFontDropdownExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            // Tool input field
            AnimatedVisibility(visible = activeTool != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = toolInputText,
                        onValueChange = onToolInputChange,
                        modifier = Modifier.weight(1f),
                        label = {
                            Text(
                                if (activeTool == "fancy") "Text in $selectedFancyFont"
                                else "Text to be ${activeTool ?: ""}",
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                            focusedLabelColor = MaterialTheme.colorScheme.primary,
                            unfocusedLabelColor = MaterialTheme.colorScheme.onSurface
                        )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = {
                            val prefix = if (value.isNotEmpty() && activeTool in listOf("bold", "italic", "strikethrough")) " " else ""
                            val formattedText = when (activeTool) {
                                "bold" -> "*$toolInputText*"
                                "italic" -> "_${toolInputText}_"
                                "strikethrough" -> "~$toolInputText~"
                                "fancy" -> applyFancyFont(toolInputText, selectedFancyFont)
                                else -> toolInputText
                            }
                            onValueChange(value + prefix + formattedText)
                            onToolInputChange("")
                            onActiveToolChange(null)
                        },
                        enabled = toolInputText.isNotBlank(),
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(Icons.Default.Check, "Apply", tint = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
fun ToolInfoDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Formatting Tools Help") },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                item {
                    ToolInfoItem(
                        title = "#name# - Personalization",
                        description = "Use #name# in your message to automatically insert the contact's name. This makes your message feel personal.",
                        example = "Example: 'Hi #name#, ...' becomes 'Hi Rohan, ...'"
                    )
                }
                item {
                    ToolInfoItem(
                        title = "Bold",
                        description = "Makes your text stand out.",
                        example = "How to use: Click B, type your text, and press ✓. It will appear as *your text*."
                    )
                }
                item {
                    ToolInfoItem(
                        title = "Italic",
                        description = "Emphasizes your text.",
                        example = "How to use: Click I, type your text, and press ✓. It will appear as _your text_."
                    )
                }
                item {
                    ToolInfoItem(
                        title = "Strikethrough",
                        description = "Shows text that has been crossed out.",
                        example = "How to use: Click S, type your text, and press ✓. It will appear as ~your text~."
                    )
                }
                item {
                    ToolInfoItem(
                        title = "Fancy Fonts",
                        description = "Use different font styles to make your message unique.",
                        example = "How to use: Click the font icon, select a style, type your text, and press ✓."
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Got it!")
            }
        }
    )
}

@Composable
fun ToolInfoItem(title: String, description: String, example: String) {
    Column {
        Text(title, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(4.dp))
        Text(description, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(4.dp))
        Text(
            buildAnnotatedString {
                withStyle(style = SpanStyle(fontWeight = FontWeight.SemiBold)) {
                    append(example)
                }
            },
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace
        )
    }
}


@Composable
fun TextCampaignSummaryCard(
    campaignName: String,
    selectedGroup: Group?,
    message: String,
    isSending: Boolean,
    progress: Float,
    sendingIndex: Int,
    campaignStatus: List<ContactStatus>,
    onLaunchCampaign: (isResuming: Boolean) -> Unit,
    onStartOver: () -> Unit,
    onScheduleCampaign: () -> Unit = {}
) {
    val hasPending = campaignStatus.any { it.status == "pending" }

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "🚀 Campaign Summary",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            if (selectedGroup != null) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (campaignName.isNotBlank()) {
                        SummaryItem(icon = Icons.Outlined.Label, label = "Campaign Name", value = campaignName)
                    }
                    SummaryItem(icon = Icons.Filled.Group, label = "Contact Group", value = "${selectedGroup.name} (${selectedGroup.contacts.size} contacts)")
                    if (message.isNotBlank()) {
                        SummaryItem(icon = Icons.Filled.Message, label = "Message", value = message, singleLine = true)
                    }
                    if (campaignStatus.isNotEmpty()) {
                        val sentCount = campaignStatus.count { it.status == "sent" }
                        val failedCount = campaignStatus.count { it.status == "failed" }
                        SummaryItem(
                            icon = Icons.Outlined.History,
                            label = "Progress",
                            value = "Sent: $sentCount, Failed: $failedCount, Pending: ${selectedGroup.contacts.size - sentCount - failedCount}"
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                if (isSending) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Sending: $sendingIndex of ${selectedGroup.contacts.size}",
                            fontWeight = FontWeight.Medium
                        )
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                if (campaignStatus.isNotEmpty() && hasPending) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(onClick = onStartOver, enabled = !isSending, modifier = Modifier.weight(1f)) {
                            Text("Start Over")
                        }
                        Button(onClick = { onLaunchCampaign(true) }, enabled = !isSending, modifier = Modifier.weight(1f)) {
                            Text("Resume")
                        }
                    }
                } else {
                    // Launch and Schedule buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Schedule Button
                        OutlinedButton(
                            onClick = onScheduleCampaign,
                            enabled = !isSending && campaignName.isNotBlank() && selectedGroup != null && message.isNotBlank(),
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = Color(0xFF9C27B0)
                            ),
                            border = BorderStroke(1.dp, Color(0xFF9C27B0))
                        ) {
                            Icon(
                                Icons.Filled.Schedule,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Schedule", fontSize = 14.sp)
                        }
                        
                        // Launch Button
                        Button(
                            onClick = { onLaunchCampaign(false) },
                            enabled = !isSending,
                            modifier = Modifier.weight(1f)
                        ) {
                            if (isSending) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Running...", fontSize = 14.sp)
                            } else {
                                Icon(Icons.Filled.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Launch", fontSize = 14.sp)
                            }
                        }
                    }
                }
            } else {
                Text(
                    text = "Please complete all steps to launch campaign",
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
fun AutonomousExecutionDashboardCard(
    stats: AutonomousExecutionStats,
    recommendedDays: Int,
    selectedDays: Int,
    riskScore: Int
) {
    val nextSend = stats.nextSendTimeMillis?.let { formatEpochTime(it) } ?: "--"
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Autonomous Dashboard",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Selected days: $selectedDays | Recommended: $recommendedDays | Risk score: ${if (riskScore >= 0) riskScore else "--"}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                DashboardStat("Sent Today", "${stats.sentToday}")
                DashboardStat("Queued", "${stats.queued}")
                DashboardStat("Failed", "${stats.failed}")
            }
            Text(
                text = "Remaining days: ${stats.remainingDays} | Next send: $nextSend",
                style = MaterialTheme.typography.bodySmall
            )
            if (!stats.autoPauseReason.isNullOrBlank()) {
                Text(
                    text = "Auto-paused: ${stats.autoPauseReason}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun DashboardStat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

private suspend fun loadAutonomousExecutionStats(
    dao: AutonomousSendQueueDao,
    campaignId: String,
    pauseReason: String?
): AutonomousExecutionStats {
    val (dayStart, dayEnd) = startAndEndOfToday()
    val queuedEntries = dao.getQueuedForCampaign(campaignId)
    val sentToday = dao.getSentTodayCount(campaignId, dayStart, dayEnd)
    val queuedToday = dao.getQueuedTodayCount(campaignId, dayStart, dayEnd)
    val failed = dao.countByStatus(campaignId, "failed")
    val nextSend = dao.getNextSendTime(campaignId)
    val remainingDays = queuedEntries.map { it.dayIndex }.distinct().count()
    return AutonomousExecutionStats(
        sentToday = sentToday,
        queuedToday = queuedToday,
        queued = queuedEntries.size,
        failed = failed,
        remainingDays = remainingDays,
        nextSendTimeMillis = nextSend,
        autoPauseReason = pauseReason
    )
}

private fun isInternetConnected(context: Context): Boolean {
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
    val active = cm.activeNetwork ?: return false
    val caps = cm.getNetworkCapabilities(active) ?: return false
    return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
        caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
}

private fun formatEpochTime(epochMillis: Long): String {
    val formatter = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault())
    return formatter.format(Date(epochMillis))
}

private fun calculateAutonomousRiskScore(totalContacts: Int, selectedDays: Int): Int {
    if (totalContacts <= 0) return 0

    val safeDailyLimit = 24
    val days = selectedDays.coerceAtLeast(1)
    val suggestedMinimumDays = recommendedAutonomousDays(totalContacts)
    val averagePerDay = ceil(totalContacts / days.toDouble()).toInt().coerceAtLeast(1)
    val loadFactor = averagePerDay / safeDailyLimit.toDouble()

    var riskScore =
        when {
            loadFactor <= 1.0 -> (15 + (loadFactor * 12)).roundToInt()
            loadFactor <= 1.5 -> (28 + ((loadFactor - 1.0) * 42)).roundToInt()
            loadFactor <= 2.5 -> (50 + ((loadFactor - 1.5) * 25)).roundToInt()
            else -> (75 + ((loadFactor - 2.5) * 12)).roundToInt()
        }.coerceIn(0, 100)

    if (days < suggestedMinimumDays) {
        val shortageFactor = (suggestedMinimumDays - days) / suggestedMinimumDays.toDouble()
        riskScore = (riskScore + (shortageFactor * 35).roundToInt()).coerceIn(0, 100)
    }

    return riskScore
}

private fun applyFancyFont(text: String, fontStyle: String): String {
    val fontMap: Map<Char, String> = when(fontStyle) {
        "Script" -> scriptMap
        "Bold Fraktur" -> boldFrakturMap
        "Monospace" -> monospaceMap
        "Small Caps" -> smallCapsMap
        "Cursive" -> cursiveMap
        else -> return text
    }
    return text.map { fontMap[it] ?: it.toString() }.joinToString("")
}

private val scriptMap: Map<Char, String> = mapOf(
    'A' to "𝒜", 'B' to "ℬ", 'C' to "𝒞", 'D' to "𝒟", 'E' to "ℰ", 'F' to "ℱ", 'G' to "𝒢", 'H' to "ℋ", 'I' to "ℐ", 'J' to "𝒥", 'K' to "𝒦", 'L' to "ℒ", 'M' to "ℳ", 'N' to "𝒩", 'O' to "𝒪", 'P' to "𝒫", 'Q' to "𝒬", 'R' to "ℛ", 'S' to "𝒮", 'T' to "𝒯", 'U' to "𝒰", 'V' to "𝒱", 'W' to "𝒲", 'X' to "𝒳", 'Y' to "𝒴", 'Z' to "𝒵",
    'a' to "𝒶", 'b' to "𝒷", 'c' to "𝒸", 'd' to "𝒹", 'e' to "ℯ", 'f' to "𝒻", 'g' to "ℊ", 'h' to "𝒽", 'i' to "𝒾", 'j' to "𝒿", 'k' to "𝓀", 'l' to "𝓁", 'm' to "𝓂", 'n' to "𝓃", 'o' to "ℴ", 'p' to "𝓅", 'q' to "𝓆", 'r' to "𝓇", 's' to "𝓈", 't' to "𝓉", 'u' to "𝓊", 'v' to "𝓋", 'w' to "𝓌", 'x' to "𝓍", 'y' to "𝓎", 'z' to "𝓏"
)

private val boldFrakturMap: Map<Char, String> = mapOf(
    'A' to "𝕬", 'B' to "𝕭", 'C' to "𝕮", 'D' to "𝕯", 'E' to "𝕰", 'F' to "𝕱", 'G' to "𝕲", 'H' to "𝕳", 'I' to "𝕴", 'J' to "𝕵", 'K' to "𝕶", 'L' to "𝕷", 'M' to "𝕸", 'N' to "𝕹", 'O' to "𝕺", 'P' to "𝕻", 'Q' to "𝕼", 'R' to "𝕽", 'S' to "𝕾", 'T' to "𝕿", 'U' to "𝖀", 'V' to "𝖁", 'W' to "𝖂", 'X' to "𝖃", 'Y' to "𝖄", 'Z' to "𝖅",
    'a' to "𝖆", 'b' to "𝖇", 'c' to "𝖈", 'd' to "𝖉", 'e' to "𝖊", 'f' to "𝖋", 'g' to "𝖌", 'h' to "𝖍", 'i' to "𝖎", 'j' to "𝖏", 'k' to "𝖐", 'l' to "𝖑", 'm' to "𝖒", 'n' to "𝖓", 'o' to "𝖔", 'p' to "𝖕", 'q' to "𝖖", 'r' to "𝖗", 's' to "𝖘", 't' to "𝖙", 'u' to "𝖚", 'v' to "𝖛", 'w' to "𝖜", 'x' to "𝖝", 'y' to "𝖞", 'z' to "𝖟"
)

private val monospaceMap: Map<Char, String> = mapOf(
    'A' to "𝙰", 'B' to "𝙱", 'C' to "𝙲", 'D' to "𝙳", 'E' to "𝙴", 'F' to "𝙵", 'G' to "𝙶", 'H' to "𝙷", 'I' to "𝙸", 'J' to "𝙹", 'K' to "𝙺", 'L' to "𝙻", 'M' to "𝙼", 'N' to "𝙽", 'O' to "𝙾", 'P' to "𝙿", 'Q' to "𝚀", 'R' to "𝚁", 'S' to "𝚂", 'T' to "𝚃", 'U' to "𝚄", 'V' to "𝚅", 'W' to "𝚆", 'X' to "𝚇", 'Y' to "𝚈", 'Z' to "𝚉",
    'a' to "𝚊", 'b' to "𝚋", 'c' to "𝚌", 'd' to "𝚍", 'e' to "𝚎", 'f' to "𝚏", 'g' to "𝚐", 'h' to "𝚑", 'i' to "𝚒", 'j' to "𝚓", 'k' to "𝚔", 'l' to "𝚕", 'm' to "𝚖", 'n' to "𝚗", 'o' to "𝚘", 'p' to "𝚙", 'q' to "𝚚", 'r' to "𝚛", 's' to "𝚜", 't' to "𝚝", 'u' to "𝚞", 'v' to "𝚟", 'w' to "𝚠", 'x' to "𝚡", 'y' to "𝚢", 'z' to "𝚣"
)

private val smallCapsMap: Map<Char, String> = mapOf(
    'A' to "ᴀ", 'B' to "ʙ", 'C' to "ᴄ", 'D' to "ᴅ", 'E' to "ᴇ", 'F' to "ꜰ", 'G' to "ɢ", 'H' to "ʜ", 'I' to "ɪ", 'J' to "ᴊ", 'K' to "ᴋ", 'L' to "ʟ", 'M' to "ᴍ", 'N' to "ɴ", 'O' to "ᴏ", 'P' to "ᴘ", 'Q' to "ǫ", 'R' to "ʀ", 'S' to "ꜱ", 'T' to "ᴛ", 'U' to "ᴜ", 'V' to "ᴠ", 'W' to "ᴡ", 'X' to "x", 'Y' to "ʏ", 'Z' to "ᴢ"
)

private val cursiveMap: Map<Char, String> = mapOf(
    'A' to "𝓐", 'B' to "𝓑", 'C' to "𝓒", 'D' to "𝓓", 'E' to "𝓔", 'F' to "𝓕", 'G' to "𝓖", 'H' to "𝓗", 'I' to "𝓘", 'J' to "𝓙", 'K' to "𝓚", 'L' to "𝓛", 'M' to "𝓜", 'N' to "𝓝", 'O' to "𝓞", 'P' to "𝓟", 'Q' to "𝓠", 'R' to "𝓡", 'S' to "𝓢", 'T' to "𝓣", 'U' to "𝓤", 'V' to "𝓥", 'W' to "𝓦", 'X' to "𝓧", 'Y' to "𝓨", 'Z' to "𝓩",
    'a' to "𝓪", 'b' to "𝓫", 'c' to "𝓬", 'd' to "𝓭", 'e' to "𝓮", 'f' to "𝓯", 'g' to "𝓰", 'h' to "𝓱", 'i' to "𝓲", 'j' to "𝓳", 'k' to "𝓴", 'l' to "𝓵", 'm' to "𝓶", 'n' to "𝓷", 'o' to "𝓸", 'p' to "𝓹", 'q' to "𝓺", 'r' to "𝓻", 's' to "𝓼", 't' to "𝓽", 'u' to "𝓾", 'v' to "𝓿", 'w' to "𝔀", 'x' to "𝔁", 'y' to "𝔂", 'z' to "𝔃"
)


// Dummy AppPreferences object to resolve references
object AppPreferences {
    fun getWhatsAppPreference(context: Context): String {
        val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        return prefs.getString("whatsapp_preference", "WhatsApp") ?: "WhatsApp"
    }

    fun saveWhatsAppPreference(context: Context, preference: String) {
        val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString("whatsapp_preference", preference).apply()
    }
}

