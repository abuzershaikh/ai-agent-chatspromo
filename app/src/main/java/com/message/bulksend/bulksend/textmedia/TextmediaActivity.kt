package com.message.bulksend.bulksend.textmedia



import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.message.bulksend.bulksend.*
import com.message.bulksend.contactmanager.Contact
import com.message.bulksend.contactmanager.ContactsRepository
import com.message.bulksend.contactmanager.ContactzActivity
import com.message.bulksend.contactmanager.Group
import com.message.bulksend.data.ContactStatus
import com.message.bulksend.db.AppDatabase
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
import java.util.UUID
import kotlin.random.Random

class TextmediaActivity : ComponentActivity() {
    
    lateinit var overlayManager: com.message.bulksend.overlay.CampaignOverlayManager
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize overlay manager
        overlayManager = com.message.bulksend.overlay.CampaignOverlayManager(this)
        lifecycle.addObserver(overlayManager)
        
        setContent {
            WhatsAppCampaignTheme {
                TextMediaCampaignManagerScreen()
            }
        }
    }
}

enum class SendOrder {
    TEXT_FIRST, MEDIA_FIRST
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TextMediaCampaignManagerScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val db = remember { AppDatabase.getInstance(context) }
    val campaignDao = remember { db.campaignDao() }
    val settingDao = remember { db.settingDao() }
    val contactsRepository = remember { ContactsRepository(context) }
    val templateRepository = remember { TemplateRepository(context) }

    // States
    val groups by contactsRepository.loadGroups().collectAsState(initial = emptyList())
    var campaignName by remember { mutableStateOf("") }
    var selectedGroup by remember { mutableStateOf<Group?>(null) }
    var message by remember { mutableStateOf("") }
    var mediaUri by remember { mutableStateOf<Uri?>(null) }
    var isSending by remember { mutableStateOf(false) }
    var whatsAppPreference by remember { mutableStateOf("WhatsApp") }
    var campaignProgress by remember { mutableStateOf(0f) }
    var sendingIndex by remember { mutableStateOf(0) }
    var campaignError by remember { mutableStateOf<String?>(null) }
    var selectedDelay by remember { mutableStateOf("Fixed (5 sec)") }
    var uniqueIdentityEnabled by remember { mutableStateOf(false) }
    var sendOrder by remember { mutableStateOf(SendOrder.TEXT_FIRST) }
    var activeTool by remember { mutableStateOf<String?>(null) }
    var toolInputText by remember { mutableStateOf("") }
    var selectedFancyFont by remember { mutableStateOf("Script") }
    var showCustomDelayDialog by remember { mutableStateOf(false) }
    var currentCampaignId by remember { mutableStateOf<String?>(null) }
    var resumableProgress by remember { mutableStateOf<Campaign?>(null) }
    var campaignStatus by remember { mutableStateOf<List<ContactStatus>>(emptyList()) }
    var campaignToResumeLoaded by remember { mutableStateOf(false) }

    var isStep1Expanded by remember { mutableStateOf(true) }
    var isStep2Expanded by remember { mutableStateOf(true) }
    var isStep3Expanded by remember { mutableStateOf(true) }
    var showAccessibilityDialog by remember { mutableStateOf(false) }
    var showOverlayPermissionDialog by remember { mutableStateOf(false) }
    var showScheduleDialog by remember { mutableStateOf(false) }
    var countryCode by remember { mutableStateOf("") }
    var showCountryCodeInfoDialog by remember { mutableStateOf(false) }
    var hasLaunchedContactSelect by remember { mutableStateOf(false) }

    val intent = context.findActivity()?.intent
    val campaignIdToResumeFromHistory = remember { intent?.getStringExtra("CAMPAIGN_ID_TO_RESUME") }
    val selectedGroupId = remember { intent?.getStringExtra("SELECTED_GROUP_ID") }
    val fromContactSelect = remember { intent?.getBooleanExtra("FROM_CONTACT_SELECT", false) ?: false }
    val incomingCampaignName = remember { intent?.getStringExtra("CAMPAIGN_NAME") }
    val incomingCountryCode = remember { intent?.getStringExtra("COUNTRY_CODE") }
    val incomingSelectedContactNumbers = remember { intent?.getStringArrayListExtra("SELECTED_CONTACTS") }
    val incomingSelectedContactNames = remember { intent?.getStringArrayListExtra("SELECTED_NAMES") ?: arrayListOf() }
    val incomingSelectedGroupName = remember { intent?.getStringExtra("SELECTED_GROUP_NAME") }

    val progressAnimation by animateFloatAsState(
        targetValue = campaignProgress,
        animationSpec = tween(500),
        label = "progress"
    )
    
    // Setup overlay callbacks with state access
    LaunchedEffect(currentCampaignId) {
        val activity = context as? TextmediaActivity
        activity?.overlayManager?.setOnStartCallback {
            // Resume campaign - overlay se start button click hua
            android.util.Log.d("TextmediaActivity", "Campaign resumed from overlay")
        }
        
        activity?.overlayManager?.setOnStopCallback {
            // Stop campaign - overlay se stop button click hua
            android.util.Log.d("TextmediaActivity", "Campaign stopped from overlay - setting CampaignState.shouldStop = true")
            
            // Set global stop flag - this will be checked in campaign loop
            CampaignState.shouldStop = true
            
            // Update database to stop campaign
            if (currentCampaignId != null) {
                scope.launch(Dispatchers.IO) {
                    campaignDao.updateStopFlag(currentCampaignId!!, true)
                    android.util.Log.d("TextmediaActivity", "Campaign stop flag updated in database")
                }
            }
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
                    mediaUri = template.mediaUri?.let { Uri.parse(it) }
                    Toast.makeText(context, "Template '${template.name}' loaded!", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // Local media path for persistence
    var localMediaPath by remember { mutableStateOf<String?>(null) }
    
    val mediaPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri ->
            if (uri != null) {
                try {
                    val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                    context.contentResolver.takePersistableUriPermission(uri, flags)
                    mediaUri = uri
                    
                    // Save media to local storage for persistence
                    scope.launch(Dispatchers.IO) {
                        val tempCampaignId = currentCampaignId ?: UUID.randomUUID().toString()
                        val savedPath = com.message.bulksend.utils.MediaStorageHelper.saveMediaToLocal(
                            context, uri, tempCampaignId
                        )
                        withContext(Dispatchers.Main) {
                            if (savedPath != null) {
                                localMediaPath = savedPath
                                android.util.Log.d("TextmediaActivity", "Media saved to: $savedPath")
                            } else {
                                Toast.makeText(context, "Failed to save media locally", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                } catch (e: SecurityException) {
                    Toast.makeText(context, "Failed to get permission for media file.", Toast.LENGTH_LONG).show()
                }
            }
        }
    )

    val contactzActivityLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) {
        // Groups will auto-update via Flow
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

    LaunchedEffect(Unit) {
        val pref = withContext(Dispatchers.IO) { settingDao.getSetting("whatsapp_preference") }
        whatsAppPreference = pref?.value ?: "WhatsApp"
    }

    LaunchedEffect(groups, campaignIdToResumeFromHistory) {
        if (campaignIdToResumeFromHistory != null && groups.isNotEmpty() && !campaignToResumeLoaded) {
            val campaign = withContext(Dispatchers.IO) { campaignDao.getCampaignById(campaignIdToResumeFromHistory) }
            if (campaign != null) {
                val group = groups.find { it.id.toString() == campaign.groupId }
                if (group != null) {
                    selectedGroup = group
                    campaignName = campaign.campaignName
                    message = campaign.message
                    
                    // Restore media from local storage
                    if (campaign.mediaPath != null) {
                        localMediaPath = campaign.mediaPath
                        val localFile = com.message.bulksend.utils.MediaStorageHelper.getLocalMediaFile(context, campaign.mediaPath)
                        if (localFile != null) {
                            mediaUri = Uri.fromFile(localFile)
                            android.util.Log.d("TextmediaActivity", "Media restored from: ${campaign.mediaPath}")
                        } else {
                            android.util.Log.w("TextmediaActivity", "Media file not found: ${campaign.mediaPath}")
                        }
                    }
                    
                    resumableProgress = campaign
                    campaignStatus = campaign.contactStatuses
                    // Restore country code from saved campaign
                    countryCode = campaign.countryCode ?: ""
                    isStep1Expanded = false
                    campaignToResumeLoaded = true
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
                        isStep1Expanded = false
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
                putExtra("TARGET_ACTIVITY", "TextmediaActivity")
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

    // Effect for when a group is selected MANUALLY
    LaunchedEffect(selectedGroup) {
        if (campaignIdToResumeFromHistory == null) {
            resumableProgress = null
            campaignStatus = emptyList()
            message = ""
            mediaUri = null
        }
    }


    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("File + Text Campaign", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface) },
                navigationIcon = {
                    IconButton(onClick = { 
                        val intent = Intent(context, com.message.bulksend.MainActivity::class.java)
                        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                        context.startActivity(intent)
                        (context as? Activity)?.finish()
                    }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onSurface)
                    }
                },
                actions = {
                    var expanded by remember { mutableStateOf(false) }
                    Box(modifier = androidx.compose.ui.Modifier.padding(end = 8.dp)) {
                        Row(
                            modifier = androidx.compose.ui.Modifier
                                .clickable { expanded = !expanded }
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), RoundedCornerShape(50))
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                whatsAppPreference,
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Icon(
                                imageVector = if (expanded) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown,
                                contentDescription = "Dropdown",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        DropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false },
                            modifier = androidx.compose.ui.Modifier.background(MaterialTheme.colorScheme.surface)
                        ) {
                            DropdownMenuItem(
                                text = { Text("WhatsApp") },
                                onClick = {
                                    whatsAppPreference = "WhatsApp"
                                    expanded = false
                                    scope.launch(Dispatchers.IO) {
                                        settingDao.upsertSetting(Setting("whatsapp_preference", "WhatsApp"))
                                    }
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.Chat, contentDescription = null)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("WhatsApp Business") },
                                onClick = {
                                    whatsAppPreference = "WhatsApp Business"
                                    expanded = false
                                    scope.launch(Dispatchers.IO) {
                                        settingDao.upsertSetting(Setting("whatsapp_preference", "WhatsApp Business"))
                                    }
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.Business, contentDescription = null)
                                }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                StepCard(
                    stepNumber = 1,
                    title = "Campaign Setup",
                    icon = Icons.Filled.Campaign,
                    isCompleted = campaignName.isNotBlank() && selectedGroup != null,
                    isExpanded = isStep1Expanded,
                    onHeaderClick = { isStep1Expanded = !isStep1Expanded }
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        OutlinedTextField(
                            value = campaignName,
                            onValueChange = { campaignName = it },
                            label = { Text("Campaign Name *") },
                            modifier = Modifier.fillMaxWidth()
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
                                    putExtra("CALLING_ACTIVITY", "TextmediaActivity")
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
                    title = "Message & Media",
                    icon = Icons.Filled.Message,
                    isCompleted = message.isNotBlank() && mediaUri != null,
                    isExpanded = isStep2Expanded,
                    onHeaderClick = { isStep2Expanded = !isStep2Expanded }
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
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
                                label = { Text("Use Template", color = MaterialTheme.colorScheme.onSurface) },
                                leadingIcon = {
                                    Icon(
                                        Icons.Outlined.Style,
                                        contentDescription = "Use Template",
                                        modifier = Modifier.size(AssistChipDefaults.IconSize),
                                        tint = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            )
                        }
                        MessageComposerWithTools(
                            value = message,
                            onValueChange = { message = it },
                            activeTool = activeTool,
                            onActiveToolChange = { tool -> activeTool = if (activeTool == tool) null else tool },
                            toolInputText = toolInputText,
                            onToolInputChange = { toolInputText = it },
                            selectedFancyFont = selectedFancyFont,
                            onFancyFontChange = { selectedFancyFont = it }
                        )
                        AttachMediaContent(
                            mediaUri = mediaUri,
                            onAttachClick = { mediaPicker.launch(arrayOf("*/*")) },
                            onRemoveClick = { mediaUri = null }
                        )
                        SendOrderSelector(
                            sendOrder = sendOrder,
                            onOrderChange = { sendOrder = it }
                        )
                    }
                }
            }
            item {
                StepCard(
                    stepNumber = 3,
                    title = "Settings",
                    icon = Icons.Filled.Settings,
                    isCompleted = true,
                    isExpanded = isStep3Expanded,
                    onHeaderClick = { isStep3Expanded = !isStep3Expanded }
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Unique ID per Message")
                            Switch(
                                checked = uniqueIdentityEnabled,
                                onCheckedChange = { uniqueIdentityEnabled = it }
                            )
                        }
                        DelaySelector(
                            selectedDelay = selectedDelay,
                            onDelaySelected = { selectedDelay = it },
                            onCustomClick = { showCustomDelayDialog = true }
                        )
                    }
                }
            }
            item {
                CampaignSummaryCard(
                    campaignName = campaignName,
                    selectedGroup = selectedGroup,
                    message = message,
                    mediaUri = mediaUri,
                    whatsAppPreference = whatsAppPreference,
                    isSending = isSending,
                    progress = progressAnimation,
                    sendingIndex = sendingIndex,
                    campaignStatus = campaignStatus,
                    onLaunchCampaign = { isResuming ->
                        if (campaignName.isBlank() || selectedGroup == null || message.isBlank() || mediaUri == null) {
                            Toast.makeText(context, "Please fill all required fields.", Toast.LENGTH_SHORT).show()
                            return@CampaignSummaryCard
                        }

                        // Country code is mandatory
                        if (countryCode.isBlank()) {
                            campaignError = "Country code is required. Please enter a country code (e.g., +91)."
                            return@CampaignSummaryCard
                        }

                        // Check overlay permission first
                        if (!com.message.bulksend.overlay.OverlayHelper.hasOverlayPermission(context)) {
                            showOverlayPermissionDialog = true
                            return@CampaignSummaryCard
                        }

                        if (!isAccessibilityServiceEnabled(context)) {
                            showAccessibilityDialog = true
                            return@CampaignSummaryCard
                        }
                        val packageName = when (whatsAppPreference) {
                            "WhatsApp" -> "com.whatsapp"
                            "WhatsApp Business" -> "com.whatsapp.w4b"
                            else -> null
                        }
                        if (packageName != null && !isPackageInstalled(context, packageName)) {
                            campaignError = "$whatsAppPreference is not installed."
                            return@CampaignSummaryCard
                        }

                        isSending = true
                        scope.launch {
                            val group = selectedGroup!!
                            var campaignStoppedPrematurely = false

                            val campaignToRun = if (isResuming && resumableProgress != null) {
                                resumableProgress!!.copy(isStopped = false, isRunning = true)
                            } else {
                                val newCampaignId = UUID.randomUUID().toString()
                                
                                // Save media to local storage if not already saved
                                val finalMediaPath = if (localMediaPath != null) {
                                    localMediaPath
                                } else if (mediaUri != null) {
                                    withContext(Dispatchers.IO) {
                                        com.message.bulksend.utils.MediaStorageHelper.saveMediaToLocal(
                                            context, mediaUri!!, newCampaignId
                                        )
                                    }
                                } else {
                                    null
                                }
                                
                                // Update localMediaPath for use in sending
                                if (finalMediaPath != null) {
                                    localMediaPath = finalMediaPath
                                }
                                
                                Campaign(
                                    id = newCampaignId,
                                    groupId = group.id.toString(),
                                    campaignName = campaignName,
                                    message = message,
                                    timestamp = System.currentTimeMillis(),
                                    totalContacts = group.contacts.size,
                                    contactStatuses = group.contacts.map { ContactStatus(it.number, "pending") },
                                    isStopped = false,
                                    isRunning = true,
                                    campaignType = "TEXTMEDIA",
                                    countryCode = countryCode,
                                    mediaPath = finalMediaPath
                                )
                            }
                            currentCampaignId = campaignToRun.id
                            withContext(Dispatchers.IO) { campaignDao.upsertCampaign(campaignToRun) }

                            // Campaign launch hone par auto-send service enable karein
                            CampaignAutoSendManager.onCampaignLaunched(campaignToRun)

                            // Start overlay with campaign
                            (context as? TextmediaActivity)?.overlayManager?.startCampaignWithOverlay(campaignToRun.totalContacts)

                            val contactsToSend = campaignToRun.contactStatuses.filter { it.status == "pending" }
                            
                            android.util.Log.d("TextmediaActivity", "Campaign '${campaignToRun.campaignName}' - Total contacts: ${campaignToRun.totalContacts}, Contacts to send: ${contactsToSend.size}")
                            
                            if (contactsToSend.isEmpty()) {
                                android.util.Log.w("TextmediaActivity", "No pending contacts found! All contacts may have been processed already.")
                                Toast.makeText(context, "No pending contacts found. Campaign already completed?", Toast.LENGTH_LONG).show()
                                return@launch
                            }

                            // Reset stop flag before starting campaign
                            CampaignState.shouldStop = false
                            
                            try {
                                android.util.Log.d("TextmediaActivity", "Starting campaign loop with ${contactsToSend.size} contacts")
                                
                                for (contactStatus in contactsToSend) {
                                    android.util.Log.d("TextmediaActivity", "Processing contact: ${contactStatus.number} with status: ${contactStatus.status}")
                                    
                                    // Check if stopped by overlay - immediate break
                                    if (CampaignState.shouldStop) {
                                        android.util.Log.d("TextmediaActivity", "Campaign stopped by overlay (CampaignState.shouldStop) - breaking loop")
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
                                    (context as? TextmediaActivity)?.overlayManager?.updateProgress(sendingIndex, currentState.totalContacts)
                                    
                                    val contact = group.contacts.find { it.number == contactStatus.number } ?: continue
                                    CampaignState.isSendActionSuccessful = null
                                    CampaignState.sendFailureReason = null

                                    Toast.makeText(context, "Sending to ${contact.name} ($sendingIndex/${group.contacts.size})", Toast.LENGTH_SHORT).show()

                                    // Add country code if number doesn't start with +
                                    val finalNumber = if (contact.number.startsWith("+")) {
                                        contact.number.replace(Regex("[^\\d+]"), "")
                                    } else {
                                        val cleanCode = countryCode.replace(Regex("[^\\d+]"), "")
                                        val cleanNum = contact.number.replace(Regex("[^\\d]"), "")
                                        "$cleanCode$cleanNum"
                                    }
                                    val cleanNumber = finalNumber.replace("+", "")
                                    val finalMessage = if (uniqueIdentityEnabled) message + "\n\n" + generateRandomString() else message
                                    val encodedMessage = URLEncoder.encode(finalMessage, "UTF-8")

                                    val textIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$cleanNumber?text=$encodedMessage")).apply {
                                        setPackage(packageName)
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                    // Get media URI - prefer local file, fallback to original URI
                                    val mediaToSend: Uri? = if (localMediaPath != null) {
                                        val localFile = java.io.File(localMediaPath!!)
                                        if (localFile.exists()) {
                                            androidx.core.content.FileProvider.getUriForFile(
                                                context,
                                                "${context.packageName}.provider",
                                                localFile
                                            )
                                        } else {
                                            mediaUri
                                        }
                                    } else {
                                        mediaUri
                                    }
                                    
                                    val mimeType = if (localMediaPath != null) {
                                        val extension = localMediaPath!!.substringAfterLast('.', "").lowercase()
                                        when (extension) {
                                            "jpg", "jpeg" -> "image/jpeg"
                                            "png" -> "image/png"
                                            "gif" -> "image/gif"
                                            "mp4" -> "video/mp4"
                                            "3gp" -> "video/3gpp"
                                            "mp3" -> "audio/mpeg"
                                            "pdf" -> "application/pdf"
                                            else -> "*/*"
                                        }
                                    } else {
                                        context.contentResolver.getType(mediaUri!!) ?: "*/*"
                                    }
                                    
                                    val mediaIntent = Intent(Intent.ACTION_SEND).apply {
                                        putExtra(Intent.EXTRA_STREAM, mediaToSend)
                                        type = mimeType
                                        putExtra("jid", "$cleanNumber@s.whatsapp.net")
                                        setPackage(packageName)
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    val openChatIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$cleanNumber")).apply {
                                        setPackage(packageName)
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }

                                    // Reset confirmation state before sending
                                    CampaignState.isSendActionSuccessful = null
                                    
                                    if (sendOrder == SendOrder.TEXT_FIRST) {
                                        context.startActivity(textIntent)
                                        delay(4000)
                                        context.startActivity(mediaIntent)
                                        // Extra delay for "Share with" dialog to be handled by accessibility service
                                        delay(3000)
                                    } else { // MEDIA_FIRST
                                        context.startActivity(openChatIntent)
                                        delay(2500)
                                        context.startActivity(mediaIntent)
                                        // Extra delay for "Share with" dialog to be handled by accessibility service
                                        delay(3000)
                                        context.startActivity(textIntent)
                                    }

                                    // Wait for confirmation from accessibility service
                                    val startTime = System.currentTimeMillis()
                                    var confirmationReceived = false
                                    while (System.currentTimeMillis() - startTime < 7000L) {
                                        // Check if stopped during confirmation wait
                                        if (CampaignState.shouldStop) {
                                            android.util.Log.d("TextmediaActivity", "Campaign stopped during confirmation wait (CampaignState.shouldStop)")
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
                                    if (updatedCampaign != null) campaignStatus = updatedCampaign.contactStatuses


                                    val delayMillis = if (selectedDelay.startsWith("Custom")) {
                                        try { selectedDelay.substringAfter("(").substringBefore(" sec").trim().toLong() * 1000 } catch (e: Exception) { 5000L }
                                    } else if (selectedDelay.startsWith("Random")) {
                                        Random.nextLong(5000, 15001)
                                    } else {
                                        try { selectedDelay.split(" ")[0].toLong() * 1000 } catch (e: Exception) { 5000L }
                                    }
                                    
                                    // Check if stopped during delay - break immediately
                                    val totalDelay = maxOf(3000L, delayMillis)
                                    val delayChunks = (totalDelay / 500).toInt()
                                    for (i in 0 until delayChunks) {
                                        if (CampaignState.shouldStop) {
                                            android.util.Log.d("TextmediaActivity", "Campaign stopped during delay (CampaignState.shouldStop) - breaking loop")
                                            campaignStoppedPrematurely = true
                                            break
                                        }
                                        delay(500)
                                    }
                                    if (campaignStoppedPrematurely) break

                                }
                            } catch (e: Exception) {
                                android.util.Log.e("TextmediaActivity", "Exception in campaign loop: ${e.message}", e)
                                campaignStoppedPrematurely = true
                            } finally {
                                // Reset stop flag
                                CampaignState.shouldStop = false
                                
                                // Stop overlay
                                (context as? TextmediaActivity)?.overlayManager?.stopCampaign()
                                
                                val finalState = withContext(Dispatchers.IO) { campaignDao.getCampaignById(currentCampaignId!!) }
                                if (finalState != null) {
                                    val finishedCampaign = finalState.copy(isRunning = false, isStopped = campaignStoppedPrematurely)
                                    withContext(Dispatchers.IO) { campaignDao.upsertCampaign(finishedCampaign) }

                                    if (campaignStoppedPrematurely) {
                                        // Campaign stopped, auto-send service disable karein
                                        CampaignAutoSendManager.onCampaignStopped(finishedCampaign)
                                        resumableProgress = finishedCampaign
                                        Toast.makeText(context, "Campaign stopped. Progress saved.", Toast.LENGTH_SHORT).show()
                                    } else {
                                        // Campaign completed, auto-send service disable karein
                                        CampaignAutoSendManager.onCampaignCompleted(finishedCampaign)
                                        Toast.makeText(context, "Campaign Finished!", Toast.LENGTH_LONG).show()
                                        selectedGroup = null
                                        campaignName = ""
                                        message = ""
                                        mediaUri = null
                                        resumableProgress = null
                                        campaignStatus = emptyList()
                                    }
                                }
                                isSending = false
                                currentCampaignId = null
                            }
                        }
                    },
                    onStartOver = {
                        resumableProgress = null
                        campaignStatus = emptyList()
                        message = ""
                        campaignName = ""
                        selectedGroup = null
                        mediaUri = null
                        Toast.makeText(context, "Cleared. You can start a new campaign.", Toast.LENGTH_SHORT).show()
                    },
                    onScheduleCampaign = {
                        showScheduleDialog = true
                    }
                )
            }
            if (isSending) {
                item {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                        Spacer(modifier = Modifier.height(16.dp))
                        LinearProgressIndicator(progress = { progressAnimation }, modifier = Modifier.fillMaxWidth())
                        Text("Sending ${sendingIndex} of ${selectedGroup?.contacts?.size ?: 0}...", color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }

            // Stop/Resume buttons removed - now controlled via overlay only
        }
    }

    // ResumeConfirmationDialog and StopConfirmationDialog removed - controlled via overlay

    if (campaignError != null) {
        AlertDialog(
            onDismissRequest = { campaignError = null },
            title = { Text("Error") },
            text = { Text(campaignError!!) },
            confirmButton = {
                Button(onClick = {
                    if (campaignError!!.contains("Accessibility")) {
                        context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    }
                    campaignError = null
                }) {
                    Text("OK")
                }
            }
        )
    }
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
        onSchedule = { scheduledTime: Long ->
            val group = selectedGroup
            if (group != null) {
                CampaignScheduleHelper.scheduleTextMediaCampaign(
                    context = context,
                    campaignName = campaignName,
                    messageText = message,
                    mediaPath = localMediaPath,
                    sendOrder = sendOrder.name,
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
                    onError = { error: String ->
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

@Composable
fun SendOrderSelector(
    sendOrder: SendOrder,
    onOrderChange: (SendOrder) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Text("Send Order:", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Start
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onOrderChange(SendOrder.TEXT_FIRST) }
                        .padding(vertical = 4.dp)
                ) {
                    RadioButton(
                        selected = sendOrder == SendOrder.TEXT_FIRST,
                        onClick = { onOrderChange(SendOrder.TEXT_FIRST) }
                    )
                    Text("Text ➔ Media", color = MaterialTheme.colorScheme.onSurface)
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onOrderChange(SendOrder.MEDIA_FIRST) }
                        .padding(vertical = 4.dp)
                ) {
                    RadioButton(
                        selected = sendOrder == SendOrder.MEDIA_FIRST,
                        onClick = { onOrderChange(SendOrder.MEDIA_FIRST) }
                    )
                    Text("Media ➔ Text", color = MaterialTheme.colorScheme.onSurface)
                }
            }
        }
    }
}
