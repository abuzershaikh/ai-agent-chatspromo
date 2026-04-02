package com.message.bulksend.bulksend

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
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.message.bulksend.bulksenderaiagent.AiAgentLaunchExtras
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

class BulksendActivity : ComponentActivity() {
    
    lateinit var overlayManager: com.message.bulksend.overlay.CampaignOverlayManager
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize overlay manager
        overlayManager = com.message.bulksend.overlay.CampaignOverlayManager(this)
        lifecycle.addObserver(overlayManager)
        
        // Get data from intent
        val selectedGroupId = intent.getStringExtra("SELECTED_GROUP_ID")
        val selectedContactNumbers = intent.getStringArrayListExtra("SELECTED_CONTACTS")
        val selectedContactNames = intent.getStringArrayListExtra("SELECTED_NAMES")
        val selectedGroupName = intent.getStringExtra("SELECTED_GROUP_NAME")
        val preCampaignName = intent.getStringExtra("CAMPAIGN_NAME")
        val preCountryCode = intent.getStringExtra("COUNTRY_CODE")
        val preMessage = intent.getStringExtra(AiAgentLaunchExtras.EXTRA_PRESET_MESSAGE)
        val preMediaUri = intent.getStringExtra(AiAgentLaunchExtras.EXTRA_PRESET_MEDIA_URI)
        
        android.util.Log.d("BulksendActivity", "GroupId: $selectedGroupId")
        android.util.Log.d("BulksendActivity", "CampaignName: $preCampaignName")
        android.util.Log.d("BulksendActivity", "CountryCode: $preCountryCode")
        
        // Overlay callbacks will be set in composable with state access
        
        setContent {
            WhatsAppCampaignTheme {
                CampaignManagerScreen(
                    preSelectedGroupId = selectedGroupId,
                    preSelectedContactNumbers = selectedContactNumbers,
                    preSelectedContactNames = selectedContactNames,
                    preSelectedGroupName = selectedGroupName,
                    preCampaignName = preCampaignName,
                    preCountryCode = preCountryCode,
                    preMessage = preMessage,
                    preMediaUri = preMediaUri
                )
            }
        }
    }
}



fun generateRandomString(): String {
    val allowedChars = ('a'..'z') + ('A'..'Z') + ('0'..'9')
    val length = (4..7).random()
    return (1..length)
        .map { allowedChars.random() }
        .joinToString("")
}

@Composable
fun WhatsAppCampaignTheme(content: @Composable () -> Unit) {
    val colors = darkColorScheme(
        primary = Color(0xFF00C853),
        primaryContainer = Color(0xFF00E676),
        secondary = Color(0xFF2979FF),
        secondaryContainer = Color(0xFF82B1FF),
        tertiary = Color(0xFFD500F9),
        surface = Color(0xFF1A1A2E),
        surfaceVariant = Color(0xFF16213E),
        background = Color(0xFF0F0F1E),
        onPrimary = Color.White,
        onSecondary = Color.White,
        onTertiary = Color.White,
        onSurface = Color(0xFFE0E0FF),
        onBackground = Color(0xFFE0E0FF),
        outline = Color(0xFF3F51B5),
        outlineVariant = Color(0xFF5C6BC0),
        error = Color(0xFFFF5252),
        onError = Color.White,
        scrim = Color(0xFF000000)
    )

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val activity = view.context.findActivity()
            if (activity != null) {
                val window = activity.window
                window.statusBarColor = colors.surface.toArgb()
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
            }
        }
    }

    MaterialTheme(
        colorScheme = colors,
        typography = Typography(
            displayLarge = MaterialTheme.typography.displayLarge.copy(fontWeight = FontWeight.Bold),
            titleLarge = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
            bodyLarge = MaterialTheme.typography.bodyLarge.copy(lineHeight = 24.sp)
        )
    ) {
        content()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CampaignManagerScreen(
    preSelectedGroupId: String? = null,
    preSelectedContactNumbers: ArrayList<String>? = null,
    preSelectedContactNames: ArrayList<String>? = null,
    preSelectedGroupName: String? = null,
    preCampaignName: String? = null,
    preCountryCode: String? = null,
    preMessage: String? = null,
    preMediaUri: String? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val db = remember { AppDatabase.getInstance(context) }
    val campaignDao = remember { db.campaignDao() }
    val settingDao = remember { db.settingDao() }
    val contactsRepository = remember { ContactsRepository(context) }
    val templateRepository = remember { TemplateRepository(context) }

    val groups by contactsRepository.loadGroups().collectAsState(initial = emptyList())
    var campaignToResumeLoaded by remember { mutableStateOf(false) }


    var campaignName by remember { mutableStateOf(preCampaignName ?: "") }
    var selectedGroup by remember { mutableStateOf<Group?>(null) }
    var message by remember { mutableStateOf(preMessage ?: "") }
    var mediaUri by remember { mutableStateOf(preMediaUri?.let(Uri::parse)) }
    var isSending by remember { mutableStateOf(false) }
    var whatsAppPreference by remember { mutableStateOf("WhatsApp Business") }
    var campaignProgress by remember { mutableStateOf(0f) }
    var sendingIndex by remember { mutableStateOf(0) }
    var campaignStatus by remember { mutableStateOf<List<ContactStatus>>(emptyList()) }
    var campaignError by remember { mutableStateOf<String?>(null) }
    var selectedDelay by remember { mutableStateOf("Fixed (5 sec)") }
    var uniqueIdentityEnabled by remember { mutableStateOf(false) }
    var currentCampaignId by remember { mutableStateOf<String?>(null) }
    var resumableProgress by remember { mutableStateOf<Campaign?>(null) }
    var isGroupStepExpanded by remember { mutableStateOf(true) }
    
    // Auto-select group if preSelectedGroupId is provided
    LaunchedEffect(groups, preSelectedGroupId) {
        if (preSelectedGroupId != null && groups.isNotEmpty() && selectedGroup == null) {
            val groupToSelect = groups.find { it.id.toString() == preSelectedGroupId }
            if (groupToSelect != null) {
                selectedGroup = groupToSelect
                isGroupStepExpanded = false // Collapse group selection step
                android.util.Log.d("BulksendActivity", "Auto-selected group: ${groupToSelect.name}")
            }
        }
    }

    // Use directly selected contacts (without creating/saving a group in DB).
    LaunchedEffect(preSelectedContactNumbers, preSelectedContactNames, preSelectedGroupName) {
        if (!preSelectedContactNumbers.isNullOrEmpty() && selectedGroup == null) {
            val tempContacts = preSelectedContactNumbers.mapIndexed { index, number ->
                Contact(
                    name = preSelectedContactNames?.getOrNull(index)?.takeIf { it.isNotBlank() } ?: number,
                    number = number
                )
            }
            selectedGroup = Group(
                id = System.currentTimeMillis(),
                name = preSelectedGroupName ?: "Selected Contacts (${tempContacts.size})",
                contacts = tempContacts,
                timestamp = System.currentTimeMillis()
            )
            isGroupStepExpanded = false
            android.util.Log.d("BulksendActivity", "Auto-selected ${tempContacts.size} direct contacts")
        }
    }
    var activeTool by remember { mutableStateOf<String?>(null) }
    var toolInputText by remember { mutableStateOf("") }
    var selectedFancyFont by remember { mutableStateOf("Script") }
    var showCustomDelayDialog by remember { mutableStateOf(false) }
    var showCreateGroupDialog by remember { mutableStateOf(false) }
    var newGroupName by remember { mutableStateOf("") }
    var importedContacts by remember { mutableStateOf<List<Contact>>(emptyList()) }
    var showAccessibilityDialog by remember { mutableStateOf(false) }
    var showOverlayPermissionDialog by remember { mutableStateOf(false) }
    var showScheduleDialog by remember { mutableStateOf(false) }
    var countryCode by remember { mutableStateOf(preCountryCode ?: "") }
    var showCountryCodeInfoDialog by remember { mutableStateOf(false) }

    val intent = context.findActivity()?.intent
    val campaignIdToResumeFromHistory = remember { intent?.getStringExtra("CAMPAIGN_ID_TO_RESUME") }

    val progressAnimation by animateFloatAsState(
        targetValue = campaignProgress,
        animationSpec = tween(500),
        label = "progress"
    )
    
    // Setup overlay callbacks with state access
    LaunchedEffect(Unit) {
        val activity = context as? BulksendActivity
        activity?.overlayManager?.setOnStartCallback {
            // Resume campaign - overlay se start button click hua
            android.util.Log.d("BulksendActivity", "Campaign resumed from overlay")
            // Campaign resume hoga automatically kyunki isPaused() false ho jayega
        }
        
        activity?.overlayManager?.setOnStopCallback {
            // Pause campaign - overlay se stop button click hua
            android.util.Log.d("BulksendActivity", "Campaign paused from overlay")
            // Campaign pause hoga automatically kyunki isPaused() true ho jayega
            // Note: isSending state true hi rahega, sirf pause hoga
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

    val contactzActivityLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) {
        // Groups list will update automatically due to the Flow.
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
                                android.util.Log.d("BulksendActivity", "Media saved to: $savedPath")
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

    LaunchedEffect(Unit) {
        scope.launch(Dispatchers.IO) {
            campaignDao.recoverInterruptedCampaigns()
            settingDao.getSetting("whatsapp_preference")?.let {
                withContext(Dispatchers.Main) {
                    whatsAppPreference = it.value
                }
            }
        }
    }

    LaunchedEffect(groups) {
        if (campaignIdToResumeFromHistory != null && groups.isNotEmpty() && !campaignToResumeLoaded) {
            val campaign = withContext(Dispatchers.IO) {
                db.campaignDao().getCampaignById(campaignIdToResumeFromHistory)
            }
            if (campaign != null) {
                val group = groups.find { it.id.toString() == campaign.groupId }
                if (group != null) {
                    selectedGroup = group
                    campaignName = campaign.campaignName
                    message = campaign.message
                    resumableProgress = campaign
                    campaignStatus = campaign.contactStatuses
                    // Restore country code from saved campaign
                    countryCode = campaign.countryCode ?: ""
                    
                    // Restore media from local storage
                    if (campaign.mediaPath != null) {
                        localMediaPath = campaign.mediaPath
                        val localFile = com.message.bulksend.utils.MediaStorageHelper.getLocalMediaFile(context, campaign.mediaPath)
                        if (localFile != null) {
                            mediaUri = Uri.fromFile(localFile)
                            android.util.Log.d("BulksendActivity", "Media restored from: ${campaign.mediaPath}")
                        } else {
                            android.util.Log.w("BulksendActivity", "Media file not found: ${campaign.mediaPath}")
                        }
                    }
                    
                    isGroupStepExpanded = false
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
                        isGroupStepExpanded = false
                        campaignToResumeLoaded = true
                        Toast.makeText(context, "Resumed using saved selected contacts.", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Campaign group not found for resume.", Toast.LENGTH_LONG).show()
                        campaignToResumeLoaded = true
                    }
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Caption Campaign", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold) },
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
                    // WhatsApp Selection Dropdown
                    var whatsAppExpanded by remember { mutableStateOf(false) }
                    val whatsAppOptions = listOf("WhatsApp", "WhatsApp Business")
                    
                    Box {
                        TextButton(onClick = { whatsAppExpanded = true }) {
                            Icon(
                                Icons.Default.PhoneAndroid,
                                contentDescription = "WhatsApp",
                                tint = Color(0xFF25D366),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                whatsAppPreference,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp
                            )
                            Icon(Icons.Default.ArrowDropDown, contentDescription = "Change", tint = MaterialTheme.colorScheme.primary)
                        }
                        DropdownMenu(
                            expanded = whatsAppExpanded,
                            onDismissRequest = { whatsAppExpanded = false }
                        ) {
                            whatsAppOptions.forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(option) },
                                    onClick = {
                                        whatsAppPreference = option
                                        scope.launch(Dispatchers.IO) {
                                            settingDao.upsertSetting(Setting("whatsapp_preference", option))
                                        }
                                        whatsAppExpanded = false
                                    }
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
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
                    cardColor = MaterialTheme.colorScheme.surface,
                    summaryContent = {
                        if (selectedGroup != null && campaignName.isNotBlank()) {
                            Column {
                                Text(campaignName, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("${selectedGroup!!.name} (${selectedGroup!!.contacts.size} contacts)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                            }
                        }
                    }
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        OutlinedTextField(
                            value = campaignName,
                            onValueChange = { campaignName = it },
                            label = { Text("Campaign Name") },
                            placeholder = { Text("e.g., Summer Sale 2025") },
                            leadingIcon = { Icon(Icons.Outlined.Label, contentDescription = "Campaign Name Icon") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline
                            )
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
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline
                            )
                        )

                        ImportButton(
                            text = "Add New List",
                            icon = Icons.Filled.GroupAdd,
                            onClick = { contactzActivityLauncher.launch(Intent(context, ContactzActivity::class.java)) },
                            modifier = Modifier.fillMaxWidth()
                        )
                        GroupSelector(
                            groups = groups,
                            selectedGroup = selectedGroup,
                            onGroupSelected = { group ->
                                selectedGroup = group
                                if (campaignIdToResumeFromHistory == null) {
                                    resumableProgress = null
                                    campaignStatus = emptyList()
                                    message = ""
                                }
                            }
                        )
                    }
                }
            }

            item {
                StepCard(
                    stepNumber = 2,
                    title = "Create Caption Text (Required)",
                    icon = Icons.Filled.Message,
                    isCompleted = message.isNotBlank(),
                    isExpanded = true,
                    onHeaderClick = { },
                    cardColor = MaterialTheme.colorScheme.surface
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
                                label = { Text("Use Template", color = MaterialTheme.colorScheme.onSurface) },
                                leadingIcon = {
                                    Icon(
                                        Icons.Outlined.Style,
                                        contentDescription = "Use Template",
                                        modifier = Modifier.size(AssistChipDefaults.IconSize),
                                        tint = MaterialTheme.colorScheme.onSurface
                                    )
                                },
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.End
                            ) {
                                Text("Unique ID", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
                                Spacer(Modifier.width(4.dp))
                                Switch(
                                    checked = uniqueIdentityEnabled,
                                    onCheckedChange = { uniqueIdentityEnabled = it },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = MaterialTheme.colorScheme.primary,
                                        checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                                        uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                                        uncheckedTrackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                                        uncheckedBorderColor = MaterialTheme.colorScheme.outline
                                    )
                                )
                            }
                        }
                        DelaySelector(
                            selectedDelay = selectedDelay,
                            onDelaySelected = { selectedDelay = it },
                            onCustomClick = { showCustomDelayDialog = true }
                        )
                        MessageComposerWithTools(
                            value = message,
                            onValueChange = { message = it },
                            activeTool = activeTool,
                            onActiveToolChange = { tool ->
                                activeTool = if (activeTool == tool) null else tool
                            },
                            toolInputText = toolInputText,
                            onToolInputChange = { toolInputText = it },
                            selectedFancyFont = selectedFancyFont,
                            onFancyFontChange = { selectedFancyFont = it }
                        )
                    }
                }
            }

            item {
                StepCard(
                    stepNumber = 3,
                    title = "Attach Media (Required)",
                    icon = Icons.Filled.Attachment,
                    isCompleted = mediaUri != null,
                    isExpanded = true,
                    onHeaderClick = {},
                    cardColor = MaterialTheme.colorScheme.surface
                ) {
                    AttachMediaContent(
                        mediaUri = mediaUri,
                        onAttachClick = {
                            mediaPicker.launch(arrayOf("*/*"))
                        },
                        onRemoveClick = { mediaUri = null }
                    )
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
                        if (campaignName.isBlank() || selectedGroup == null) {
                            campaignError = "Please enter campaign name and select a contact group."
                            return@CampaignSummaryCard
                        }

                        // Media attachment compulsory for Caption Campaign
                        if (mediaUri == null) {
                            campaignError = "Media file is required for Caption Campaign. Please attach a file."
                            return@CampaignSummaryCard
                        }

                        // Caption text is mandatory
                        if (message.isBlank()) {
                            campaignError = "Caption text is required. Please write a caption for your media."
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
                        
                        // Get WhatsApp from WhatsPref
                        // Get WhatsApp from WhatsPref
                        // Fix: Use local state instead of WhatsPref which wasn't being updated
                        val packageName = if (whatsAppPreference == "WhatsApp Business") "com.whatsapp.w4b" else "com.whatsapp"
                        val selectedAppName = whatsAppPreference
                        
                        if (!isPackageInstalled(context, packageName)) {
                            campaignError = "$selectedAppName aapke device mein installed nahi hai."
                            return@CampaignSummaryCard
                        }

                        scope.launch {
                            isSending = true
                            var campaignStoppedPrematurely = false
                            val group = selectedGroup!!

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
                                    campaignType = "BULKSEND",
                                    countryCode = countryCode,
                                    mediaPath = finalMediaPath
                                )
                            }
                            currentCampaignId = campaignToRun.id
                            withContext(Dispatchers.IO) { campaignDao.upsertCampaign(campaignToRun) }

                            // Campaign launch hone par auto-send service enable karein
                            CampaignAutoSendManager.onCampaignLaunched(campaignToRun)

                            // Start overlay with campaign
                            (context as? BulksendActivity)?.overlayManager?.startCampaignWithOverlay(campaignToRun.totalContacts)

                            val contactsToSend = campaignToRun.contactStatuses.filter { it.status == "pending" }

                            // Reset stop flag before starting campaign
                            CampaignState.shouldStop = false
                            
                            try {
                                for (contactStatus in contactsToSend) {
                                    // Check if stopped by overlay - immediate break
                                    if (CampaignState.shouldStop) {
                                        android.util.Log.d("BulksendActivity", "Campaign stopped by overlay (CampaignState.shouldStop) - breaking loop")
                                        campaignStoppedPrematurely = true
                                        break
                                    }
                                    
                                    val currentState = withContext(Dispatchers.IO) { campaignDao.getCampaignById(currentCampaignId!!) }
                                    if (currentState == null || currentState.isStopped) {
                                        campaignStoppedPrematurely = true
                                        break
                                    }

                                    sendingIndex = currentState.sentCount + 1
                                    campaignProgress = sendingIndex.toFloat() / currentState.totalContacts

                                    // Update overlay progress
                                    (context as? BulksendActivity)?.overlayManager?.updateProgress(sendingIndex, currentState.totalContacts)

                                    val contact = group.contacts.find { it.number == contactStatus.number } ?: continue

                                    // Reset state for each contact
                                    CampaignState.isSendActionSuccessful = null
                                    CampaignState.sendFailureReason = null

                                    Toast.makeText(context, "Sending $sendingIndex/${currentState.totalContacts}: ${contact.name}", Toast.LENGTH_SHORT).show()

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
                                    val finalMessage = baseMessage.replace("#name#", contact.name, ignoreCase = true)

                                    try {
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
                                        
                                        if (mediaToSend != null) {
                                            // Open chat first
                                            val openChatIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$cleanNumber")).apply {
                                                setPackage(packageName)
                                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                            }
                                            context.startActivity(openChatIntent)
                                            delay(2500)

                                            // Send media
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
                                                context.contentResolver.getType(mediaToSend) ?: "*/*"
                                            }
                                            
                                            val sendMediaIntent = Intent(Intent.ACTION_SEND).apply {
                                                putExtra(Intent.EXTRA_STREAM, mediaToSend)
                                                type = mimeType
                                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                if (finalMessage.isNotBlank()) putExtra(Intent.EXTRA_TEXT, finalMessage)
                                                putExtra("jid", "$cleanNumber@s.whatsapp.net")
                                                setPackage(packageName)
                                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                            }
                                            context.startActivity(sendMediaIntent)
                                        } else {
                                            val encodedMessage = URLEncoder.encode(finalMessage, "UTF-8")
                                            val textIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$cleanNumber?text=$encodedMessage")).apply {
                                                setPackage(packageName)
                                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                            }
                                            context.startActivity(textIntent)
                                        }
                                    } catch (e: Exception) {
                                        android.util.Log.e("BulksendActivity", "Send failed: ${e.message}")
                                        CampaignState.isSendActionSuccessful = false
                                    }


                                    // Wait for confirmation with timeout
                                    val startTime = System.currentTimeMillis()
                                    val timeout = 7000L // 7 seconds
                                    var confirmationReceived = false
                                    while (System.currentTimeMillis() - startTime < timeout) {
                                        // Check if stopped during confirmation wait
                                        if (CampaignState.shouldStop) {
                                            android.util.Log.d("BulksendActivity", "Campaign stopped during confirmation wait (CampaignState.shouldStop)")
                                            campaignStoppedPrematurely = true
                                            break
                                        }
                                        if (CampaignState.isSendActionSuccessful == true) {
                                            confirmationReceived = true
                                            break
                                        }
                                        if (CampaignState.isSendActionSuccessful == false) {
                                            break
                                        }
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
                                    if (finalStatus == "failed") {
                                        withContext(Dispatchers.Main) {
                                            val message = if (failureReason == "not_on_whatsapp") {
                                                "${contact.name} is not on WhatsApp"
                                            } else {
                                                "Failed to send: ${contact.name}"
                                            }
                                            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                                        }
                                    }

                                    withContext(Dispatchers.IO) {
                                        campaignDao.updateContactStatus(
                                            currentCampaignId!!,
                                            contact.number,
                                            finalStatus,
                                            failureReason
                                        )
                                    }

                                    val delayMillis = if (selectedDelay.startsWith("Custom")) {
                                        try {
                                            selectedDelay.substringAfter("(").substringBefore(" sec").trim().toLong() * 1000
                                        } catch (e: Exception) { 5000L }
                                    } else {
                                        when (selectedDelay) {
                                            "7 sec" -> 7000L
                                            "8 sec" -> 8000L
                                            "9 sec" -> 9000L
                                            "10 sec" -> 10000L
                                            "Random (5-15 sec)" -> Random.nextLong(5000, 15001)
                                            else -> 5000L
                                        }
                                    }
                                    
                                    // Check if stopped during delay - break immediately
                                    val delayChunks = (delayMillis / 500).toInt()
                                    for (i in 0 until delayChunks) {
                                        if (CampaignState.shouldStop) {
                                            android.util.Log.d("BulksendActivity", "Campaign stopped during delay (CampaignState.shouldStop) - breaking loop")
                                            campaignStoppedPrematurely = true
                                            break
                                        }
                                        delay(500)
                                    }
                                    if (campaignStoppedPrematurely) break

                                    val updatedCampaign = withContext(Dispatchers.IO) { campaignDao.getCampaignById(currentCampaignId!!) }
                                    if(updatedCampaign != null) campaignStatus = updatedCampaign.contactStatuses
                                }
                            } finally {
                                // Reset stop flag
                                CampaignState.shouldStop = false
                                
                                // Stop overlay when campaign ends
                                (context as? BulksendActivity)?.overlayManager?.stopCampaign()
                                
                                val finalState = withContext(Dispatchers.IO) { campaignDao.getCampaignById(currentCampaignId!!) }
                                if(finalState != null) {
                                    val finishedCampaign = finalState.copy(
                                        isRunning = false,
                                        isStopped = campaignStoppedPrematurely
                                    )
                                    withContext(Dispatchers.IO) { campaignDao.upsertCampaign(finishedCampaign) }

                                    if (campaignStoppedPrematurely) {
                                        // Campaign stopped, auto-send service disable karein
                                        CampaignAutoSendManager.onCampaignStopped(finishedCampaign)
                                        resumableProgress = finishedCampaign
                                        Toast.makeText(context, "Campaign ruka. Progress save ho gaya hai.", Toast.LENGTH_SHORT).show()
                                    } else {
                                        // Campaign completed, auto-send service disable karein
                                        CampaignAutoSendManager.onCampaignCompleted(finishedCampaign)
                                        resumableProgress = null
                                        campaignStatus = emptyList()
                                        Toast.makeText(context, "Campaign safaltapoorvak poora hua! 🎉", Toast.LENGTH_LONG).show()
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
                        campaignName = ""
                        message = ""
                        Toast.makeText(context, "Ready to start a new campaign.", Toast.LENGTH_SHORT).show()
                    },
                    onScheduleCampaign = {
                        showScheduleDialog = true
                    }
                )
            }

            // Stop/Resume buttons removed - now controlled via overlay only
        }
    }

    // ResumeConfirmationDialog removed - resume handled via overlay

    if (showCreateGroupDialog) {
        CreateGroupDialog(
            contactCount = importedContacts.size,
            groupName = newGroupName,
            onGroupNameChange = { newGroupName = it },
            onConfirm = {
                if (newGroupName.isNotBlank()) {
                    scope.launch {
                        contactsRepository.saveGroup(newGroupName, importedContacts)
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "Group '$newGroupName' safaltapoorvak save hua! ✅", Toast.LENGTH_SHORT).show()
                            newGroupName = ""
                            importedContacts = emptyList()
                            showCreateGroupDialog = false
                        }
                    }
                }
            },
            onDismiss = {
                showCreateGroupDialog = false
                newGroupName = ""
                importedContacts = emptyList()
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

    if (campaignError != null) {
        AlertDialog(
            onDismissRequest = { campaignError = null },
            icon = { Icon(Icons.Filled.Error, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Campaign Error") },
            text = { Text(campaignError!!) },
            confirmButton = {
                Button(onClick = {
                    val errorMsg = campaignError
                    campaignError = null
                    if (errorMsg?.contains("Accessibility Service") == true){
                        context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    }
                }) {
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
                CampaignScheduleHelper.scheduleMediaCampaign(
                    context = context,
                    campaignName = campaignName,
                    captionText = message,
                    mediaPath = localMediaPath,
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
fun TopBarWhatsAppSelector(
    selectedPreference: String,
    onPreferenceChange: (String) -> Unit
) {
    Row(modifier = Modifier.padding(end = 16.dp)) {
        OutlinedButton(
            onClick = { onPreferenceChange("WhatsApp") },
            shape = RoundedCornerShape(topStartPercent = 50, bottomStartPercent = 50),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = if (selectedPreference == "WhatsApp") MaterialTheme.colorScheme.primary else Color.Transparent,
                contentColor = if (selectedPreference == "WhatsApp") Color.White else MaterialTheme.colorScheme.primary
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
            contentPadding = PaddingValues(horizontal = 12.dp),
            modifier = Modifier.height(32.dp)
        ) {
            Text("WhatsApp", fontSize = 11.sp)
        }
        OutlinedButton(
            onClick = { onPreferenceChange("WhatsApp Business") },
            shape = RoundedCornerShape(topEndPercent = 50, bottomEndPercent = 50),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = if (selectedPreference == "WhatsApp Business") MaterialTheme.colorScheme.primary else Color.Transparent,
                contentColor = if (selectedPreference == "WhatsApp Business") Color.White else MaterialTheme.colorScheme.primary
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
            contentPadding = PaddingValues(horizontal = 12.dp),
            modifier = Modifier
                .height(32.dp)
                .offset(x = (-1).dp)
        ) {
            Text("Business", fontSize = 11.sp)
        }
    }
}

@Composable
fun StepCard(
    stepNumber: Int,
    title: String,
    icon: ImageVector,
    isCompleted: Boolean,
    isExpanded: Boolean,
    onHeaderClick: () -> Unit,
    cardColor: Color = MaterialTheme.colorScheme.surface,
    summaryContent: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val animatedElevation by animateDpAsState(
        targetValue = if (isExpanded) 8.dp else 2.dp,
        animationSpec = tween(300),
        label = "elevation"
    )

    val borderGradient = Brush.linearGradient(
        colors = listOf(
            MaterialTheme.colorScheme.primary,
            MaterialTheme.colorScheme.secondary,
            MaterialTheme.colorScheme.tertiary
        ),
        start = Offset.Zero,
        end = Offset(300f, 300f)
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
            .border(
                width = 2.dp,
                brush = borderGradient,
                shape = RoundedCornerShape(16.dp)
            ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = cardColor
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = animatedElevation)
    ) {
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.clickable(onClick = onHeaderClick)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(
                            brush = Brush.linearGradient(
                                colors = if (isCompleted) {
                                    listOf(
                                        MaterialTheme.colorScheme.primary,
                                        MaterialTheme.colorScheme.primaryContainer
                                    )
                                } else if (isExpanded) {
                                    listOf(
                                        MaterialTheme.colorScheme.secondary,
                                        MaterialTheme.colorScheme.secondaryContainer
                                    )
                                } else {
                                    listOf(
                                        MaterialTheme.colorScheme.outline,
                                        MaterialTheme.colorScheme.outlineVariant
                                    )
                                }
                            )
                        )
                ) {
                    if (isCompleted && !isExpanded) {
                        Icon(
                            Icons.Filled.Check,
                            contentDescription = "Completed",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    } else {
                        Icon(
                            icon,
                            contentDescription = null,
                            tint = if (isExpanded || isCompleted) Color.White else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Step $stepNumber",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    if (isExpanded || summaryContent == null) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    } else {
                        summaryContent()
                    }
                }
                if (summaryContent != null) {
                    Icon(
                        imageVector = if(isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = "Toggle",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
            AnimatedVisibility(visible = isExpanded) {
                Column {
                    Spacer(modifier = Modifier.height(16.dp))
                    content()
                }
            }
        }
    }
}

@Composable
fun ImportButton(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(56.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = MaterialTheme.colorScheme.onSurface
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(text, fontWeight = FontWeight.Medium)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupSelector(
    groups: List<Group>,
    selectedGroup: Group?,
    onGroupSelected: (Group) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded }
    ) {
        OutlinedTextField(
            value = selectedGroup?.name ?: "Select a Contact Group",
            onValueChange = {},
            readOnly = true,
            label = { Text("Contact Group", color = MaterialTheme.colorScheme.onSurface) },
            trailingIcon = {
                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
            },
            leadingIcon = {
                Icon(
                    Icons.Filled.Group,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface
                )
            },
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                focusedLabelColor = MaterialTheme.colorScheme.primary,
                unfocusedLabelColor = MaterialTheme.colorScheme.onSurface,
                focusedLeadingIconColor = MaterialTheme.colorScheme.primary,
                unfocusedLeadingIconColor = MaterialTheme.colorScheme.onSurface,
                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                unfocusedTextColor = MaterialTheme.colorScheme.onSurface
            )
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(MaterialTheme.colorScheme.surface)
        ) {
            groups.forEach { group ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(
                                text = group.name,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "${group.contacts.size} contacts",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                        }
                    },
                    onClick = {
                        onGroupSelected(group)
                        expanded = false
                    },
                    leadingIcon = {
                        Icon(
                            Icons.Filled.Group,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DelaySelector(
    selectedDelay: String,
    onDelaySelected: (String) -> Unit,
    onCustomClick: () -> Unit
) {
    val delayOptions = listOf("Fixed (5 sec)", "7 sec", "8 sec", "9 sec", "10 sec", "Random (5-15 sec)", "Custom...")
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
        OutlinedTextField(
            value = selectedDelay,
            onValueChange = {},
            readOnly = true,
            label = { Text("Delay Between Messages", color = MaterialTheme.colorScheme.onSurface) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            leadingIcon = { Icon(Icons.Outlined.Timer, contentDescription = "Delay Icon", tint = MaterialTheme.colorScheme.onSurface) },
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                focusedLabelColor = MaterialTheme.colorScheme.primary,
                unfocusedLabelColor = MaterialTheme.colorScheme.onSurface,
                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                focusedLeadingIconColor = MaterialTheme.colorScheme.primary,
                unfocusedLeadingIconColor = MaterialTheme.colorScheme.onSurface
            )
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(MaterialTheme.colorScheme.surface)
        ) {
            delayOptions.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option, color = MaterialTheme.colorScheme.onSurface) },
                    onClick = {
                        if (option == "Custom...") {
                            onCustomClick()
                        } else {
                            onDelaySelected(option)
                        }
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
fun CustomDelayDialog(
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    var customDelayInput by remember { mutableStateOf("") }
    val isError = customDelayInput.toIntOrNull() == null && customDelayInput.isNotEmpty()

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Filled.Timer, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
        title = { Text("Set Custom Delay", color = MaterialTheme.colorScheme.onSurface) },
        text = {
            Column {
                Text("Enter the delay between messages in seconds. (Minimum 3 seconds)", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f))
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = customDelayInput,
                    onValueChange = { customDelayInput = it.filter { char -> char.isDigit() } },
                    label = { Text("Delay (seconds)") },
                    singleLine = true,
                    isError = isError,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                        errorBorderColor = MaterialTheme.colorScheme.error,
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                    )
                )
                if (isError) {
                    Text("Please enter a valid number.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    customDelayInput.toIntOrNull()?.let {
                        onConfirm(it)
                    }
                },
                enabled = customDelayInput.toIntOrNull() != null
            ) {
                Text("Set")
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Text("Cancel")
            }
        },
        containerColor = MaterialTheme.colorScheme.surface
    )
}

@Composable
fun MessageComposerWithTools(
    value: String,
    onValueChange: (String) -> Unit,
    activeTool: String?,
    onActiveToolChange: (String?) -> Unit,
    toolInputText: String,
    onToolInputChange: (String) -> Unit,
    selectedFancyFont: String,
    onFancyFontChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var isFontDropdownExpanded by remember { mutableStateOf(false) }
    val fancyFonts = listOf("Script", "Bold Fraktur", "Monospace", "Small Caps", "Cursive")

    Column(modifier = modifier) {
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

        Spacer(Modifier.height(8.dp))

        Column(Modifier.padding(horizontal = 4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // #name# placeholder button
                IconButton(
                    onClick = { 
                        val cursorPosition = value.length
                        val newText = value + "#name#"
                        onValueChange(newText)
                    },
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(Color.Transparent)
                ) {
                    Icon(
                        Icons.Default.Tag, "#name#",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
                
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
                                "fancy" -> apply1FancyFont(toolInputText, selectedFancyFont)
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
fun AttachMediaContent(
    mediaUri: Uri?,
    onAttachClick: () -> Unit,
    onRemoveClick: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (mediaUri != null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Icon(
                        Icons.Filled.Description,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = mediaUri.lastPathSegment ?: "Attached File",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    IconButton(onClick = onRemoveClick) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = "Remove File",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
        Button(
            onClick = onAttachClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = Color.White
            )
        ) {
            Icon(
                Icons.Filled.AttachFile,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(if (mediaUri == null) "Attach Any File" else "Change File", fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
fun CampaignSummaryCard(
    campaignName: String,
    selectedGroup: Group?,
    message: String,
    mediaUri: Uri?,
    whatsAppPreference: String,
    isSending: Boolean,
    progress: Float,
    sendingIndex: Int,
    campaignStatus: List<ContactStatus>,
    onLaunchCampaign: (Boolean) -> Unit,
    onStartOver: () -> Unit,
    onScheduleCampaign: () -> Unit = {}
) {
    val hasPending = campaignStatus.any { it.status == "pending" }
    val isResumable = campaignStatus.isNotEmpty()

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "🚀 Campaign Summary",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (selectedGroup != null) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (campaignName.isNotBlank()) {
                        SummaryItem(
                            icon = Icons.Outlined.Label,
                            label = "Campaign Name",
                            value = campaignName
                        )
                    }
                    SummaryItem(
                        icon = Icons.Filled.Group,
                        label = "Contact Group",
                        value = "${selectedGroup.name} (${selectedGroup.contacts.size} contacts)"
                    )
                    if (message.isNotBlank()){
                        SummaryItem(
                            icon = Icons.Filled.Message,
                            label = "Message",
                            value = message,
                            singleLine = true
                        )
                    }
                    if (mediaUri != null) {
                        SummaryItem(
                            icon = Icons.Filled.Attachment,
                            label = "Attachment",
                            value = mediaUri.lastPathSegment ?: "Media File"
                        )
                    }
                    if (campaignStatus.isNotEmpty()) {
                        val sentCount = campaignStatus.count { it.status == "sent" }
                        val failedCount = campaignStatus.count { it.status == "failed" }
                        SummaryItem(
                            icon = Icons.Outlined.History,
                            label = "Campaign Progress",
                            value = "Sent: $sentCount, Failed: $failedCount, Pending: ${selectedGroup.contacts.size - sentCount - failedCount}"
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                if (isSending) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Sending to contact $sendingIndex of ${selectedGroup.contacts.size}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.outline
                        )
                        Text(
                            text = "${(progress * 100).toInt()}% Complete",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.align(Alignment.End)
                        )
                    }
                }
                if (isResumable) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = onStartOver,
                            enabled = !isSending,
                            modifier = Modifier
                                .weight(1f)
                                .height(56.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.onSurface,
                                disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                            ),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                        ) {
                            Icon(Icons.Filled.Refresh, contentDescription = "Start Over")
                            Spacer(Modifier.width(8.dp))
                            Text("Start Over")
                        }
                        Button(
                            onClick = { onLaunchCampaign(true) },
                            enabled = !isSending && hasPending,
                            modifier = Modifier
                                .weight(1f)
                                .height(56.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondary,
                                contentColor = Color.White
                            )
                        ) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = "Resume")
                            Spacer(Modifier.width(8.dp))
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
                            enabled = !isSending && campaignName.isNotBlank() && selectedGroup != null && message.isNotBlank() && mediaUri != null,
                            modifier = Modifier
                                .weight(1f)
                                .height(56.dp),
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
                            modifier = Modifier
                                .weight(1f)
                                .height(56.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = Color.White,
                                disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                            )
                        ) {
                            if (isSending) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                    color = Color.White
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = "Running...",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            } else {
                                Icon(
                                    Icons.Filled.Send,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Launch",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            } else {
                Text(
                    text = "Please complete all steps above to launch your campaign",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
fun SummaryItem(
    icon: ImageVector,
    label: String,
    value: String,
    singleLine: Boolean = false
) {
    Row(
        verticalAlignment = if(singleLine) Alignment.CenterVertically else Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .size(20.dp)
                .padding(top = 2.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = if(singleLine) 1 else Int.MAX_VALUE,
                overflow = if(singleLine) TextOverflow.Ellipsis else TextOverflow.Clip,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
fun CreateGroupDialog(
    contactCount: Int,
    groupName: String,
    onGroupNameChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                Icons.Filled.GroupAdd,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )
        },
        title = {
            Text(
                text = "Create New Group",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            Icons.Filled.ContactPhone,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Column {
                            Text(
                                text = "Contacts Found",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = "$contactCount contacts",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = groupName,
                    onValueChange = onGroupNameChange,
                    label = { Text("Group Name", color = MaterialTheme.colorScheme.onSurface) },
                    placeholder = { Text("Enter a name for this group", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)) },
                    leadingIcon = {
                        Icon(
                            Icons.Filled.DriveFileRenameOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        focusedLabelColor = MaterialTheme.colorScheme.primary,
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                    )
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = groupName.isNotBlank(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = Color.White
                )
            ) {
                Icon(
                    Icons.Filled.Save,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Save Group")
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.onSurface
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Text("Cancel")
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(16.dp)
    )
}

private fun apply1FancyFont(text: String, fontStyle: String): String {
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



