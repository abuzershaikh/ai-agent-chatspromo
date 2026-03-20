package com.message.bulksend.bulksend.sheetscampaign



import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.message.bulksend.bulksend.*
import com.message.bulksend.data.ContactStatus
import com.message.bulksend.db.AppDatabase
import com.message.bulksend.db.Campaign
import com.message.bulksend.db.Setting
import com.message.bulksend.utils.CampaignAutoSendManager
import com.message.bulksend.utils.isAccessibilityServiceEnabled
import com.message.bulksend.utils.isPackageInstalled
import com.message.bulksend.utils.AccessibilityPermissionDialog
import com.message.bulksend.utils.SubscriptionUtils
import com.message.bulksend.utils.PremiumUpgradeDialog
import com.message.bulksend.components.ScheduleCampaignDialog
import com.message.bulksend.utils.CampaignScheduleHelper
import com.message.bulksend.utils.findActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.apache.poi.ss.usermodel.DataFormatter
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.net.URLEncoder
import java.util.UUID
import kotlin.random.Random

// Data class to hold the parsed sheet data
data class SheetData(
    val headers: List<String> = emptyList(),
    val rows: List<Map<String, String>> = emptyList()
)

// Data class for the final message to be sent
data class FinalMessage(
    val recipientName: String,
    val recipientNumber: String,
    val messageBody: String
)

class SheetsendActivity : ComponentActivity() {
    
    lateinit var overlayManager: com.message.bulksend.overlay.CampaignOverlayManager
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize overlay manager
        overlayManager = com.message.bulksend.overlay.CampaignOverlayManager(this)
        lifecycle.addObserver(overlayManager)
        
        setContent {
            WhatsAppCampaignTheme {
                SheetCampaignScreen()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SheetCampaignScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val db = remember { AppDatabase.getInstance(context) }
    val campaignDao = remember { db.campaignDao() }
    val settingDao = remember { db.settingDao() }
    val gson = remember { Gson() }
    val sheetLinkLoader = remember { SheetLinkLoader() }

    // State management
    var campaignName by remember { mutableStateOf("") }
    var sheetData by remember { mutableStateOf<SheetData?>(null) }
    var selectedSheetUri by remember { mutableStateOf<Uri?>(null) }
    var sheetFileName by remember { mutableStateOf<String?>(null) }
    var sheetUrl by remember { mutableStateOf("") }
    var isLoadingFromUrl by remember { mutableStateOf(false) }
    var mediaUri by remember { mutableStateOf<Uri?>(null) }
    var templateMessage by remember { mutableStateOf(TextFieldValue("")) }
    var countryCode by remember { mutableStateOf("") }
    var showCountryCodeInfoDialog by remember { mutableStateOf(false) }
    var showUrlHelpDialog by remember { mutableStateOf(false) }
    var generatedMessages by remember { mutableStateOf<List<FinalMessage>>(emptyList()) }
    var isSending by remember { mutableStateOf(false) }
    var whatsAppPreference by remember { mutableStateOf("WhatsApp") }
    var selectedDelay by remember { mutableStateOf("Fixed (5 sec)") }
    var showCustomDelayDialog by remember { mutableStateOf(false) }
    var campaignError by remember { mutableStateOf<String?>(null) }
    var sendingIndex by remember { mutableStateOf(0) }
    var cellWidth by remember { mutableStateOf(120.dp) }
    var campaignJob by remember { mutableStateOf<Job?>(null) }
    var uniqueIdentityEnabled by remember { mutableStateOf(false) }
    var campaignStatus by remember { mutableStateOf<List<ContactStatus>>(emptyList()) }
    var currentCampaignId by remember { mutableStateOf<String?>(null) }
    var resumableProgress by remember { mutableStateOf<Campaign?>(null) }
    var campaignToResumeLoaded by remember { mutableStateOf(false) }

    var isStep1Expanded by remember { mutableStateOf(true) }
    var isStep2Expanded by remember { mutableStateOf(true) }
    var isStep3Expanded by remember { mutableStateOf(true) }
    var isStep4Expanded by remember { mutableStateOf(true) }
    var isStep5Expanded by remember { mutableStateOf(true) }
    var showAccessibilityDialog by remember { mutableStateOf(false) }
    var showOverlayPermissionDialog by remember { mutableStateOf(false) }
    var showScheduleDialog by remember { mutableStateOf(false) }
    var isStep6Expanded by remember { mutableStateOf(true) }
    var showPremiumDialog by remember { mutableStateOf(false) }
    var contactsToSendCount by remember { mutableStateOf(0) }

    val intent = context.findActivity()?.intent
    val campaignIdToResumeFromHistory = remember { intent?.getStringExtra("CAMPAIGN_ID_TO_RESUME") }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        uri?.let {
            selectedSheetUri = it
            sheetFileName = getFileName(context, it)
            sheetUrl = "" // Clear URL when file is selected
            resumableProgress = null
            campaignStatus = emptyList()
            scope.launch(Dispatchers.IO) {
                val parsedData = parseSheet(context, it)
                withContext(Dispatchers.Main) {
                    sheetData = parsedData
                    if (sheetData == null) {
                        Toast.makeText(context, "Error parsing file.", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    val mediaPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri ->
            if (uri != null) {
                try {
                    val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                    context.contentResolver.takePersistableUriPermission(uri, flags)
                    mediaUri = uri
                } catch (e: SecurityException) {
                    Toast.makeText(context, "Failed to get permission for media file.", Toast.LENGTH_LONG).show()
                }
            }
        }
    )
    
    // Setup overlay callbacks with state access
    LaunchedEffect(Unit) {
        val activity = context as? SheetsendActivity
        activity?.overlayManager?.setOnStartCallback {
            // Resume campaign - overlay se start button click hua
            android.util.Log.d("SheetsendActivity", "Campaign resumed from overlay")
        }
        
        activity?.overlayManager?.setOnStopCallback {
            // Pause campaign - overlay se stop button click hua
            android.util.Log.d("SheetsendActivity", "Campaign paused from overlay")
        }
    }

    LaunchedEffect(Unit) {
        val pref = withContext(Dispatchers.IO) { settingDao.getSetting("whatsapp_preference") }
        whatsAppPreference = pref?.value ?: "WhatsApp"
    }

    LaunchedEffect(campaignIdToResumeFromHistory) {
        if (campaignIdToResumeFromHistory != null && !campaignToResumeLoaded) {
            val campaign = withContext(Dispatchers.IO) { campaignDao.getCampaignById(campaignIdToResumeFromHistory) }
            if (campaign != null) {
                campaignName = campaign.campaignName
                templateMessage = TextFieldValue(campaign.message)
                countryCode = campaign.countryCode ?: ""
                sheetFileName = campaign.sheetFileName
                sheetUrl = campaign.sheetUrl ?: ""
                resumableProgress = campaign
                campaignStatus = campaign.contactStatuses

                // Parse sheet data from JSON
                campaign.sheetDataJson?.let {
                    val type = object : TypeToken<SheetData>() {}.type
                    sheetData = gson.fromJson(it, type)
                }

                isStep1Expanded = false
                isStep2Expanded = false
                isStep3Expanded = false
                campaignToResumeLoaded = true
            }
        }
    }

    LaunchedEffect(templateMessage.text, sheetData, countryCode) {
        delay(300)
        generatedMessages = generateMessagesFromSheet(templateMessage.text, sheetData, countryCode)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Sheet Campaign", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { (context as? Activity)?.finish() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    com.message.bulksend.bulksend.TopBarWhatsAppSelector(
                        selectedPreference = whatsAppPreference,
                        onPreferenceChange = {
                            whatsAppPreference = it
                            scope.launch(Dispatchers.IO) {
                                settingDao.upsertSetting(Setting("whatsapp_preference", it))
                            }
                        }
                    )
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    StepCard(
                        stepNumber = 1,
                        title = "Campaign Name",
                        icon = Icons.Filled.Label,
                        isCompleted = campaignName.isNotBlank(),
                        isExpanded = isStep1Expanded,
                        onHeaderClick = { isStep1Expanded = !isStep1Expanded }
                    ) {
                        OutlinedTextField(
                            value = campaignName,
                            onValueChange = { campaignName = it },
                            label = { Text("Enter a name for your campaign") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    }
                }
                item {
                    StepCard(
                        stepNumber = 2,
                        title = "Upload Spreadsheet",
                        icon = Icons.Outlined.UploadFile,
                        isCompleted = sheetData != null || sheetFileName != null,
                        isExpanded = isStep2Expanded,
                        onHeaderClick = { isStep2Expanded = !isStep2Expanded }
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            // File upload section
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Text(
                                        "Option 1: Upload File",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Button(onClick = {
                                        filePickerLauncher.launch(
                                            arrayOf(
                                                "text/csv",
                                                "text/comma-separated-values",
                                                "application/csv",
                                                "application/vnd.ms-excel",
                                                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                                            )
                                        )
                                    }) {
                                        Icon(Icons.Default.Upload, contentDescription = "Upload")
                                        Spacer(Modifier.width(8.dp))
                                        Text(if (sheetFileName == null) "Select Sheet File (.csv, .xlsx)" else "Change Sheet")
                                    }
                                    sheetFileName?.let {
                                        Text("File: $it", style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                            
                            // Divider
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Divider(modifier = Modifier.weight(1f))
                                Text(
                                    "OR",
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                                Divider(modifier = Modifier.weight(1f))
                            }
                            
                            // URL input section
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            "Option 2: Load from URL",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                        IconButton(
                                            onClick = { showUrlHelpDialog = true },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(
                                                Icons.Outlined.Help,
                                                contentDescription = "URL Help",
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        OutlinedTextField(
                                            value = sheetUrl,
                                            onValueChange = { sheetUrl = it },
                                            label = { Text("Sheet URL") },
                                            placeholder = { Text("https://docs.google.com/spreadsheets/...") },
                                            leadingIcon = { Icon(Icons.Default.Link, contentDescription = "URL") },
                                            modifier = Modifier.weight(1f),
                                            singleLine = true,
                                            enabled = !isLoadingFromUrl
                                        )
                                        IconButton(
                                            onClick = {
                                                if (sheetUrl.isNotBlank()) {
                                                    scope.launch {
                                                        isLoadingFromUrl = true
                                                        try {
                                                            val loadedData = sheetLinkLoader.loadSheetFromUrl(sheetUrl)
                                                            if (loadedData != null) {
                                                                sheetData = loadedData
                                                                sheetFileName = "Sheet from URL"
                                                                selectedSheetUri = null
                                                                resumableProgress = null
                                                                campaignStatus = emptyList()
                                                                Toast.makeText(context, "Sheet loaded successfully!", Toast.LENGTH_SHORT).show()
                                                            } else {
                                                                Toast.makeText(context, "Failed to load sheet from URL", Toast.LENGTH_LONG).show()
                                                            }
                                                        } catch (e: Exception) {
                                                            Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                                                        } finally {
                                                            isLoadingFromUrl = false
                                                        }
                                                    }
                                                }
                                            },
                                            enabled = sheetUrl.isNotBlank() && !isLoadingFromUrl
                                        ) {
                                            if (isLoadingFromUrl) {
                                                CircularProgressIndicator(
                                                    modifier = Modifier.size(20.dp),
                                                    strokeWidth = 2.dp
                                                )
                                            } else {
                                                Icon(
                                                    Icons.Default.Refresh,
                                                    contentDescription = "Load/Refresh Sheet",
                                                    tint = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                        }
                                    }
                                    Text(
                                        "Supports Google Sheets, CSV, and Excel files",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                    )
                                }
                            }
                            
                            Spacer(Modifier.height(8.dp))
                            OutlinedTextField(
                                value = countryCode,
                                onValueChange = { countryCode = it },
                                label = { Text("Country Code") },
                                placeholder = { Text("e.g., +91") },
                                leadingIcon = { Icon(Icons.Default.Public, contentDescription = "Country Code") },
                                trailingIcon = {
                                    IconButton(onClick = { showCountryCodeInfoDialog = true }) {
                                        Icon(Icons.Outlined.Info, contentDescription = "Country Code Info")
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                            sheetData?.let { data ->
                                Spacer(Modifier.height(8.dp))

                                // Contact limit warning for free users
                                val subscriptionInfo = SubscriptionUtils.getLocalSubscriptionInfo(context)
                                val subscriptionType = subscriptionInfo["type"] as? String ?: "free"
                                val isExpired = subscriptionInfo["isExpired"] as? Boolean ?: false
                                val isPremium = subscriptionType == "premium" && !isExpired
                                val totalContacts = data.rows.size

                                if (!isPremium && totalContacts > 10) {
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = CardDefaults.cardColors(
                                            containerColor = Color(0xFFFFF3CD)
                                        ),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                Icons.Default.Warning,
                                                contentDescription = "Warning",
                                                tint = Color(0xFFFF9800),
                                                modifier = Modifier.size(24.dp)
                                            )
                                            Spacer(Modifier.width(8.dp))
                                            Column {
                                                Text(
                                                    "⚠️ Free Plan Limit",
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 14.sp,
                                                    color = Color(0xFF856404)
                                                )
                                                Text(
                                                    "You have $totalContacts contacts but can only send to 10 contacts.\n💎 Upgrade to Premium for unlimited!",
                                                    fontSize = 12.sp,
                                                    color = Color(0xFF856404)
                                                )
                                            }
                                        }
                                    }
                                    Spacer(Modifier.height(8.dp))
                                }

                                Text("Detected Headers:", fontWeight = FontWeight.SemiBold)
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    items(data.headers) { header ->
                                        AssistChip(onClick = { /* For display only */ }, label = { Text(header) })
                                    }
                                }
                                Spacer(Modifier.height(8.dp))
                                SheetPreviewTable(
                                    sheetData = data,
                                    cellWidth = cellWidth,
                                    onCellWidthChange = { newWidth ->
                                        if (newWidth >= 80.dp) {
                                            cellWidth = newWidth
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
                item {
                    StepCard(
                        stepNumber = 3,
                        title = "Compose Message Template",
                        icon = Icons.Filled.Message,
                        isCompleted = templateMessage.text.isNotBlank(),
                        isExpanded = isStep3Expanded,
                        onHeaderClick = { isStep3Expanded = !isStep3Expanded }
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Unique ID", fontSize = 14.sp)
                                Spacer(Modifier.width(8.dp))
                                Switch(
                                    checked = uniqueIdentityEnabled,
                                    onCheckedChange = { uniqueIdentityEnabled = it }
                                )
                            }
                            OutlinedTextField(
                                value = templateMessage,
                                onValueChange = { templateMessage = it },
                                label = { Text("Type your message here") },
                                placeholder = { Text("e.g., Hi (Name), your order is (OrderId).") },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(150.dp)
                            )
                            sheetData?.let { data ->
                                Text("Click to add placeholders:", fontWeight = FontWeight.SemiBold)
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    items(data.headers) { header ->
                                        SuggestionChip(onClick = {
                                            val cursorPosition = templateMessage.selection.start
                                            val text = templateMessage.text
                                            val placeholder = "($header)"
                                            val newText = text.substring(0, cursorPosition) + placeholder + text.substring(cursorPosition)
                                            templateMessage = TextFieldValue(
                                                text = newText,
                                                selection = TextRange(cursorPosition + placeholder.length)
                                            )
                                        }, label = { Text("($header)") })
                                    }
                                }
                            }
                        }
                    }
                }
                item {
                    StepCard(
                        stepNumber = 4,
                        title = "Attach Media (Optional)",
                        icon = Icons.Filled.Attachment,
                        isCompleted = mediaUri != null,
                        isExpanded = isStep4Expanded,
                        onHeaderClick = { isStep4Expanded = !isStep4Expanded }
                    ) {
                        AttachMediaContent(
                            mediaUri = mediaUri,
                            onAttachClick = {
                                mediaPickerLauncher.launch(arrayOf("*/*"))
                            },
                            onRemoveClick = { mediaUri = null }
                        )
                    }
                }
                item {
                    StepCard(
                        stepNumber = 5,
                        title = "Settings",
                        icon = Icons.Filled.Settings,
                        isCompleted = true,
                        isExpanded = isStep5Expanded,
                        onHeaderClick = { isStep5Expanded = !isStep5Expanded }
                    ) {
                        DelaySelector(
                            selectedDelay = selectedDelay,
                            onDelaySelected = { selectedDelay = it },
                            onCustomClick = { showCustomDelayDialog = true }
                        )
                    }
                }
                item {
                    StepCard(
                        stepNumber = 6,
                        title = "Preview & Launch",
                        icon = Icons.Filled.Send,
                        isCompleted = generatedMessages.isNotEmpty() || resumableProgress != null,
                        isExpanded = isStep6Expanded,
                        onHeaderClick = { isStep6Expanded = !isStep6Expanded }
                    ) {
                        val finalGeneratedMessages = if (resumableProgress != null && sheetData != null) {
                            generateMessagesFromSheet(resumableProgress!!.message, sheetData, resumableProgress!!.countryCode ?: "")
                        } else {
                            generatedMessages
                        }

                        val campaignReady = (finalGeneratedMessages.isNotEmpty() || resumableProgress != null) && campaignName.isNotBlank()
                        val isResuming = resumableProgress != null && resumableProgress!!.contactStatuses.any { it.status == "pending" }

                        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            if (finalGeneratedMessages.isNotEmpty()) {
                                // Check subscription for preview
                                val subscriptionInfo = SubscriptionUtils.getLocalSubscriptionInfo(context)
                                val subscriptionType = subscriptionInfo["type"] as? String ?: "free"
                                val isExpired = subscriptionInfo["isExpired"] as? Boolean ?: false
                                val isPremium = subscriptionType == "premium" && !isExpired

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        "Preview (${finalGeneratedMessages.size} messages):",
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    if (!isPremium && finalGeneratedMessages.size > 10) {
                                        Text(
                                            "Only 10 will be sent",
                                            fontSize = 12.sp,
                                            color = Color(0xFFFF9800),
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = 300.dp)
                                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                                ) {
                                    LazyColumn(contentPadding = PaddingValues(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        items(finalGeneratedMessages) { finalMessage ->
                                            MessagePreviewItem(finalMessage)
                                        }
                                    }
                                }
                            } else if (sheetData != null && sheetData!!.rows.isNotEmpty()) {
                                // Show debug info when no messages are generated
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3CD))
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Text(
                                            "No messages generated",
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF856404)
                                        )
                                        Text(
                                            "Possible issues:",
                                            fontWeight = FontWeight.SemiBold,
                                            color = Color(0xFF856404),
                                            modifier = Modifier.padding(top = 8.dp)
                                        )
                                        Text(
                                            "• No phone number column found (looking for: phone, number, mobile, contact)",
                                            fontSize = 12.sp,
                                            color = Color(0xFF856404)
                                        )
                                        Text(
                                            "• Phone numbers are empty or invalid",
                                            fontSize = 12.sp,
                                            color = Color(0xFF856404)
                                        )
                                        Text(
                                            "• Sheet has ${sheetData!!.rows.size} rows with headers: ${sheetData!!.headers.joinToString(", ")}",
                                            fontSize = 12.sp,
                                            color = Color(0xFF856404),
                                            modifier = Modifier.padding(top = 4.dp)
                                        )
                                    }
                                }
                            }

                            if (isSending) {
                                Button(
                                    onClick = {
                                        campaignJob?.cancel()
                                        isSending = false
                                        currentCampaignId?.let {
                                            scope.launch(Dispatchers.IO) {
                                                campaignDao.updateStopFlag(it, true)
                                            }
                                        }
                                        Toast.makeText(context, "Campaign stopped.", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.fillMaxWidth().height(50.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                                ) {
                                    Icon(Icons.Default.Stop, contentDescription = "Stop")
                                    Spacer(Modifier.width(8.dp))
                                    Text("Stop Campaign")
                                }
                            } else {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    if (isResuming) {
                                        OutlinedButton(
                                            onClick = {
                                                resumableProgress = null
                                                campaignStatus = emptyList()
                                                selectedSheetUri = null
                                                sheetData = null
                                                sheetFileName = null
                                                campaignName = ""
                                                templateMessage = TextFieldValue("")
                                            },
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text("Start Over")
                                        }
                                        Button(
                                            onClick = {
                                                val numbersNeedCode = generatedMessages.any { !it.recipientNumber.startsWith("+") }
                                                if (sheetData != null && numbersNeedCode && countryCode.isBlank() && !isResuming) {
                                                    Toast.makeText(context, "Some numbers need a country code.", Toast.LENGTH_LONG).show()
                                                    return@Button
                                                }
                                                if (isResuming && mediaUri == null && resumableProgress?.campaignType == "TEXTMEDIA") {
                                                    Toast.makeText(context, "Please re-select the media file to resume.", Toast.LENGTH_LONG).show()
                                                    return@Button
                                                }

                                                // Check contact limit for free users
                                                if (!isResuming) {
                                                    val subscriptionInfo = SubscriptionUtils.getLocalSubscriptionInfo(context)
                                                    val subscriptionType = subscriptionInfo["type"] as? String ?: "free"
                                                    val isExpired = subscriptionInfo["isExpired"] as? Boolean ?: false
                                                    val isPremium = subscriptionType == "premium" && !isExpired

                                                    if (!isPremium && generatedMessages.size > 10) {
                                                        contactsToSendCount = generatedMessages.size
                                                        showPremiumDialog = true
                                                        return@Button
                                                    }
                                                }

                                                // Check overlay permission first
                                                if (!com.message.bulksend.overlay.OverlayHelper.hasOverlayPermission(context)) {
                                                    showOverlayPermissionDialog = true
                                                    return@Button
                                                }

                                                if (!isAccessibilityServiceEnabled(context)) {
                                                    showAccessibilityDialog = true
                                                    return@Button
                                                }
                                                val packageName = when (whatsAppPreference) { "WhatsApp" -> "com.whatsapp"; "WhatsApp Business" -> "com.whatsapp.w4b"; else -> null }
                                                if (packageName != null && !isPackageInstalled(context, packageName)) {
                                                    campaignError = "$whatsAppPreference is not installed."
                                                    return@Button
                                                }

                                                campaignJob = scope.launch {
                                                    isSending = true
                                                    var campaignStoppedPrematurely = false

                                                    val campaignToRun = if (isResuming) {
                                                        resumableProgress!!.copy(isStopped = false, isRunning = true)
                                                    } else {
                                                        Campaign(
                                                            id = UUID.randomUUID().toString(),
                                                            groupId = "SHEET_${sheetFileName ?: "Unknown"}",
                                                            campaignName = campaignName,
                                                            message = templateMessage.text,
                                                            timestamp = System.currentTimeMillis(),
                                                            totalContacts = generatedMessages.size,
                                                            contactStatuses = generatedMessages.map { ContactStatus(it.recipientNumber, "pending") },
                                                            isStopped = false,
                                                            isRunning = true,
                                                            campaignType = "SHEETSSEND",
                                                            sheetFileName = sheetFileName,
                                                            countryCode = countryCode,
                                                            sheetDataJson = gson.toJson(sheetData),
                                                            sheetUrl = if (sheetUrl.isNotBlank()) sheetUrl else null
                                                        )
                                                    }
                                                    currentCampaignId = campaignToRun.id
                                                    withContext(Dispatchers.IO) { campaignDao.upsertCampaign(campaignToRun) }

                                                    // Campaign launch hone par auto-send service enable karein
                                                    CampaignAutoSendManager.onCampaignLaunched(campaignToRun)

                                                    // Start overlay with campaign
                                                    (context as? SheetsendActivity)?.overlayManager?.startCampaignWithOverlay(campaignToRun.totalContacts)

                                                    val contactsToSend = campaignToRun.contactStatuses.filter { it.status == "pending" }
                                                    val messagesMap = finalGeneratedMessages.associateBy { it.recipientNumber }

                                                    try {
                                                        for (contactStatus in contactsToSend) {
                                                            // Check if paused by overlay
                                                            while ((context as? SheetsendActivity)?.overlayManager?.isPaused() == true) {
                                                                delay(500)
                                                            }
                                                            
                                                            if (!isActive) { campaignStoppedPrematurely = true; break }

                                                            val currentState = withContext(Dispatchers.IO) { campaignDao.getCampaignById(currentCampaignId!!) }
                                                            if (currentState == null || currentState.isStopped) {
                                                                campaignStoppedPrematurely = true
                                                                break
                                                            }

                                                            sendingIndex = currentState.sentCount + currentState.failedCount + 1
                                                            
                                                            // Update overlay progress
                                                            (context as? SheetsendActivity)?.overlayManager?.updateProgress(sendingIndex, currentState.totalContacts)
                                                            
                                                            val finalMessage = messagesMap[contactStatus.number] ?: continue
                                                            CampaignState.isSendActionSuccessful = null
                                                            CampaignState.sendFailureReason = null

                                                            withContext(Dispatchers.Main) {
                                                                Toast.makeText(context, "Sending to ${finalMessage.recipientName} (${sendingIndex}/${generatedMessages.size})", Toast.LENGTH_SHORT).show()
                                                            }

                                                            val cleanNumber = finalMessage.recipientNumber.replace(Regex("[^\\d]"), "")

                                                            val messageToSend = if (uniqueIdentityEnabled) {
                                                                finalMessage.messageBody + "\n\n" + generateRandomString()
                                                            } else {
                                                                finalMessage.messageBody
                                                            }

                                                            val encodedMessage = URLEncoder.encode(messageToSend, "UTF-8")

                                                            val textIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$cleanNumber?text=$encodedMessage")).apply {
                                                                setPackage(packageName)
                                                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                            }

                                                            if (mediaUri != null) {
                                                                val openChatIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$cleanNumber")).apply {
                                                                    setPackage(packageName)
                                                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                                }
                                                                context.startActivity(openChatIntent)
                                                                delay(2500)

                                                                val sendMediaIntent = Intent(Intent.ACTION_SEND).apply {
                                                                    putExtra(Intent.EXTRA_STREAM, mediaUri)
                                                                    type = context.contentResolver.getType(mediaUri!!) ?: "*/*"
                                                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                                    if (messageToSend.isNotBlank()) putExtra(Intent.EXTRA_TEXT, messageToSend)
                                                                    putExtra("jid", "$cleanNumber@s.whatsapp.net")
                                                                    setPackage(packageName)
                                                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                                }
                                                                context.startActivity(sendMediaIntent)
                                                            } else {
                                                                context.startActivity(textIntent)
                                                            }

                                                            val startTime = System.currentTimeMillis()
                                                            var confirmationReceived = false
                                                            while (System.currentTimeMillis() - startTime < 7000L) {
                                                                if (CampaignState.isSendActionSuccessful == true) {
                                                                    confirmationReceived = true
                                                                    break
                                                                }
                                                                if (CampaignState.isSendActionSuccessful == false) break
                                                                delay(100)
                                                            }

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
                                                                    contactStatus.number,
                                                                    finalStatus,
                                                                    failureReason
                                                                )
                                                            }

                                                            val updatedCampaign = withContext(Dispatchers.IO) { campaignDao.getCampaignById(currentCampaignId!!) }
                                                            if (updatedCampaign != null) {
                                                                campaignStatus = updatedCampaign.contactStatuses
                                                            }

                                                            val delayMillis = if (selectedDelay.startsWith("Custom")) {
                                                                try { selectedDelay.substringAfter("(").substringBefore(" sec").trim().toLong() * 1000 } catch (e: Exception) { 5000L }
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
                                                            delay(delayMillis)
                                                        }
                                                    } finally {
                                                        val finalState = withContext(Dispatchers.IO) { campaignDao.getCampaignById(currentCampaignId!!) }
                                                        if (finalState != null) {
                                                            val finishedCampaign = finalState.copy(isRunning = false, isStopped = campaignStoppedPrematurely)
                                                            withContext(Dispatchers.IO) { campaignDao.upsertCampaign(finishedCampaign) }
                                                            if (campaignStoppedPrematurely) {
                                                                // Campaign stopped, auto-send service disable karein
                                                                CampaignAutoSendManager.onCampaignStopped(finishedCampaign)
                                                                resumableProgress = finishedCampaign
                                                                Toast.makeText(context, "Campaign stopped.", Toast.LENGTH_SHORT).show()
                                                            } else {
                                                                // Campaign completed, auto-send service disable karein
                                                                CampaignAutoSendManager.onCampaignCompleted(finishedCampaign)
                                                                resumableProgress = null
                                                                Toast.makeText(context, "Campaign finished!", Toast.LENGTH_LONG).show()
                                                            }
                                                        }
                                                        isSending = false
                                                    }
                                                }
                                            },
                                            enabled = campaignReady && !isSending,
                                            modifier = Modifier.weight(1f).height(50.dp)
                                        ) {
                                            Icon(Icons.Default.PlayArrow, contentDescription = "Resume")
                                            Spacer(Modifier.width(8.dp))
                                            Text("Resume")
                                        }
                                    } else {
                                        // Schedule Button
                                        OutlinedButton(
                                            onClick = {
                                                showScheduleDialog = true
                                            },
                                            enabled = campaignReady && !isSending,
                                            modifier = Modifier.weight(1f).height(50.dp),
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
                                            Spacer(Modifier.width(8.dp))
                                            Text("Schedule", fontSize = 14.sp)
                                        }
                                        
                                        // Launch Button
                                        Button(
                                            onClick = {
                                                val numbersNeedCode = generatedMessages.any { !it.recipientNumber.startsWith("+") }
                                                if (sheetData != null && numbersNeedCode && countryCode.isBlank() && !isResuming) {
                                                    Toast.makeText(context, "Some numbers need a country code.", Toast.LENGTH_LONG).show()
                                                    return@Button
                                                }
                                                if (isResuming && mediaUri == null && resumableProgress?.campaignType == "TEXTMEDIA") {
                                                    Toast.makeText(context, "Please re-select the media file to resume.", Toast.LENGTH_LONG).show()
                                                    return@Button
                                                }

                                                // Check contact limit for free users
                                                if (!isResuming) {
                                                    val subscriptionInfo = SubscriptionUtils.getLocalSubscriptionInfo(context)
                                                    val subscriptionType = subscriptionInfo["type"] as? String ?: "free"
                                                    val isExpired = subscriptionInfo["isExpired"] as? Boolean ?: false
                                                    val isPremium = subscriptionType == "premium" && !isExpired

                                                    if (!isPremium && generatedMessages.size > 10) {
                                                        contactsToSendCount = generatedMessages.size
                                                        showPremiumDialog = true
                                                        return@Button
                                                    }
                                                }

                                                // Check overlay permission first
                                                if (!com.message.bulksend.overlay.OverlayHelper.hasOverlayPermission(context)) {
                                                    showOverlayPermissionDialog = true
                                                    return@Button
                                                }

                                                if (!isAccessibilityServiceEnabled(context)) {
                                                    showAccessibilityDialog = true
                                                    return@Button
                                                }
                                                val packageName = when (whatsAppPreference) { "WhatsApp" -> "com.whatsapp"; "WhatsApp Business" -> "com.whatsapp.w4b"; else -> null }
                                                if (packageName != null && !isPackageInstalled(context, packageName)) {
                                                    campaignError = "$whatsAppPreference is not installed."
                                                    return@Button
                                                }

                                                campaignJob = scope.launch {
                                                    isSending = true
                                                    var campaignStoppedPrematurely = false

                                                    val campaignToRun = Campaign(
                                                        id = UUID.randomUUID().toString(),
                                                        groupId = "SHEET_${sheetFileName ?: "Unknown"}",
                                                        campaignName = campaignName,
                                                        message = templateMessage.text,
                                                        timestamp = System.currentTimeMillis(),
                                                        totalContacts = generatedMessages.size,
                                                        contactStatuses = generatedMessages.map { ContactStatus(it.recipientNumber, "pending") },
                                                        isStopped = false,
                                                        isRunning = true,
                                                        campaignType = "SHEETSSEND",
                                                        sheetFileName = sheetFileName,
                                                        countryCode = countryCode,
                                                        sheetDataJson = gson.toJson(sheetData),
                                                        sheetUrl = if (sheetUrl.isNotBlank()) sheetUrl else null
                                                    )
                                                    
                                                    currentCampaignId = campaignToRun.id
                                                    withContext(Dispatchers.IO) { campaignDao.upsertCampaign(campaignToRun) }

                                                    // Campaign launch hone par auto-send service enable karein
                                                    CampaignAutoSendManager.onCampaignLaunched(campaignToRun)

                                                    // Start overlay with campaign
                                                    (context as? SheetsendActivity)?.overlayManager?.startCampaignWithOverlay(campaignToRun.totalContacts)

                                                    val contactsToSend = campaignToRun.contactStatuses.filter { it.status == "pending" }
                                                    val messagesMap = finalGeneratedMessages.associateBy { it.recipientNumber }

                                                    try {
                                                        for (contactStatus in contactsToSend) {
                                                            // Check if paused by overlay
                                                            while ((context as? SheetsendActivity)?.overlayManager?.isPaused() == true) {
                                                                delay(500)
                                                            }
                                                            
                                                            if (!isActive) { campaignStoppedPrematurely = true; break }

                                                            val currentState = withContext(Dispatchers.IO) { campaignDao.getCampaignById(currentCampaignId!!) }
                                                            if (currentState == null || currentState.isStopped) {
                                                                campaignStoppedPrematurely = true
                                                                break
                                                            }

                                                            sendingIndex = currentState.sentCount + currentState.failedCount + 1
                                                            
                                                            // Update overlay progress
                                                            (context as? SheetsendActivity)?.overlayManager?.updateProgress(sendingIndex, currentState.totalContacts)
                                                            
                                                            val finalMessage = messagesMap[contactStatus.number] ?: continue
                                                            CampaignState.isSendActionSuccessful = null
                                                            CampaignState.sendFailureReason = null

                                                            withContext(Dispatchers.Main) {
                                                                Toast.makeText(context, "Sending to ${finalMessage.recipientName} (${sendingIndex}/${generatedMessages.size})", Toast.LENGTH_SHORT).show()
                                                            }

                                                            val cleanNumber = finalMessage.recipientNumber.replace(Regex("[^\\d]"), "")

                                                            val messageToSend = if (uniqueIdentityEnabled) {
                                                                finalMessage.messageBody + "\n\n" + generateRandomString()
                                                            } else {
                                                                finalMessage.messageBody
                                                            }

                                                            val encodedMessage = URLEncoder.encode(messageToSend, "UTF-8")

                                                            val textIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$cleanNumber?text=$encodedMessage")).apply {
                                                                setPackage(packageName)
                                                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                            }

                                                            if (mediaUri != null) {
                                                                val openChatIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$cleanNumber")).apply {
                                                                    setPackage(packageName)
                                                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                                }
                                                                context.startActivity(openChatIntent)
                                                                delay(2500)

                                                                val sendMediaIntent = Intent(Intent.ACTION_SEND).apply {
                                                                    putExtra(Intent.EXTRA_STREAM, mediaUri)
                                                                    type = context.contentResolver.getType(mediaUri!!) ?: "*/*"
                                                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                                    if (messageToSend.isNotBlank()) putExtra(Intent.EXTRA_TEXT, messageToSend)
                                                                    putExtra("jid", "$cleanNumber@s.whatsapp.net")
                                                                    setPackage(packageName)
                                                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                                }
                                                                context.startActivity(sendMediaIntent)
                                                            } else {
                                                                context.startActivity(textIntent)
                                                            }

                                                            val startTime = System.currentTimeMillis()
                                                            var confirmationReceived = false
                                                            while (System.currentTimeMillis() - startTime < 7000L) {
                                                                if (CampaignState.isSendActionSuccessful == true) {
                                                                    confirmationReceived = true
                                                                    break
                                                                }
                                                                if (CampaignState.isSendActionSuccessful == false) break
                                                                delay(100)
                                                            }

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
                                                                    contactStatus.number,
                                                                    finalStatus,
                                                                    failureReason
                                                                )
                                                            }

                                                            val updatedCampaign = withContext(Dispatchers.IO) { campaignDao.getCampaignById(currentCampaignId!!) }
                                                            if (updatedCampaign != null) {
                                                                campaignStatus = updatedCampaign.contactStatuses
                                                            }

                                                            val delayMillis = if (selectedDelay.startsWith("Custom")) {
                                                                try { selectedDelay.substringAfter("(").substringBefore(" sec").trim().toLong() * 1000 } catch (e: Exception) { 5000L }
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
                                                            delay(delayMillis)
                                                        }
                                                    } finally {
                                                        val finalState = withContext(Dispatchers.IO) { campaignDao.getCampaignById(currentCampaignId!!) }
                                                        if (finalState != null) {
                                                            val finishedCampaign = finalState.copy(isRunning = false, isStopped = campaignStoppedPrematurely)
                                                            withContext(Dispatchers.IO) { campaignDao.upsertCampaign(finishedCampaign) }
                                                            if (campaignStoppedPrematurely) {
                                                                // Campaign stopped, auto-send service disable karein
                                                                CampaignAutoSendManager.onCampaignStopped(finishedCampaign)
                                                                resumableProgress = finishedCampaign
                                                                Toast.makeText(context, "Campaign stopped.", Toast.LENGTH_SHORT).show()
                                                            } else {
                                                                // Campaign completed, auto-send service disable karein
                                                                CampaignAutoSendManager.onCampaignCompleted(finishedCampaign)
                                                                resumableProgress = null
                                                                Toast.makeText(context, "Campaign finished!", Toast.LENGTH_LONG).show()
                                                            }
                                                        }
                                                        isSending = false
                                                    }
                                                }
                                            },
                                            enabled = campaignReady && !isSending,
                                            modifier = Modifier.weight(1f).height(50.dp)
                                        ) {
                                            Icon(Icons.Default.Send, contentDescription = "Launch")
                                            Spacer(Modifier.width(8.dp))
                                            Text("Launch (${finalGeneratedMessages.size})", fontSize = 14.sp)
                                        }
                                    }
                                    }
                                }
                            }

                        }
                    }
                }
            }
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

        if (showCountryCodeInfoDialog) {
            CountryCodeInfoDialog {
                showCountryCodeInfoDialog = false
            }
        }

        if (showUrlHelpDialog) {
            UrlHelpDialog {
                showUrlHelpDialog = false
            }
        }

        // Premium Upgrade Dialog
        if (showPremiumDialog) {
            PremiumUpgradeDialog(
                currentContacts = contactsToSendCount,
                contactsLimit = 10,
                onDismiss = {
                    showPremiumDialog = false
                },
                onUpgrade = {
                    // Navigate to admin panel or subscription page
                    Toast.makeText(context, "Opening subscription page...", Toast.LENGTH_SHORT).show()
                    showPremiumDialog = false
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

        // Schedule Campaign Dialog
        ScheduleCampaignDialog(
            isVisible = showScheduleDialog,
            campaignName = campaignName,
            onDismiss = { showScheduleDialog = false },
            onSchedule = { scheduledTime: Long ->
                if (sheetData != null) {
                    // Serialize sheet data to JSON to ensure it's saved with the campaign
                    val sheetDataJson = gson.toJson(sheetData)
                    val contactCount = sheetData?.rows?.size ?: 0
                    
                    CampaignScheduleHelper.scheduleSheetCampaign(
                        context = context,
                        campaignName = campaignName,
                        templateMessage = templateMessage.text,
                        sheetUrl = if (sheetUrl.isNotBlank()) sheetUrl else null,
                        sheetFileName = sheetFileName,
                        sheetDataJson = sheetDataJson,
                        contactCount = contactCount,
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
                    Toast.makeText(context, "Settings me jakar permission enable karein", Toast.LENGTH_SHORT).show()
                },
                onDisagree = {
                    Toast.makeText(context, "Accessibility permission zaroori hai", Toast.LENGTH_SHORT).show()
                },
                onDismiss = {
                    showAccessibilityDialog = false
                }
            )
        }
    }

@Composable
fun CountryCodeInfoDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.Info, contentDescription = null) },
        title = { Text("About Country Codes") },
        text = {
            Text(
                "• It is required to add a country code to send messages.\n\n" +
                        "• If a phone number in your sheet does not start with a '+', the code from this field will be automatically added.\n\n" +
                        "• If a number already has a country code (e.g., +1, +91), this field will be ignored for that number."
            )
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Got it")
            }
        }
    )
}

@Composable
fun UrlHelpDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.Help, contentDescription = null) },
        title = { Text("How to Use Sheet URLs") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Supported Sources:",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleSmall
                )
                Text("• Google Sheets (public or shared links)")
                Text("• Direct CSV file URLs")
                Text("• Direct Excel file URLs")
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    "Instructions:",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleSmall
                )
                Text("1. For Google Sheets: Share your sheet and copy the link")
                Text("2. Make sure the sheet is publicly accessible")
                Text("3. Paste the URL in the 'Sheet URL' field")
                Text("4. Click the refresh icon to load data")
                Text("5. Use refresh icon to reload latest data anytime")
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    "Example Google Sheets URL:",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    "https://docs.google.com/spreadsheets/d/YOUR_SHEET_ID/edit#gid=0",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Got it")
            }
        }
    )
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
                        text = getFileName(LocalContext.current, mediaUri) ?: "Attached File",
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
fun SheetPreviewTable(
    sheetData: SheetData,
    cellWidth: Dp,
    onCellWidthChange: (Dp) -> Unit
) {
    if (sheetData.headers.isEmpty()) return

    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                "Sheet Preview:",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Row {
                IconButton(onClick = { onCellWidthChange(cellWidth - 10.dp) }, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.Remove, contentDescription = "Decrease column width")
                }
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(onClick = { onCellWidthChange(cellWidth + 10.dp) }, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.Add, contentDescription = "Increase column width")
                }
            }
        }


        Box(
            modifier = Modifier
                .heightIn(max = 240.dp)
                .clip(RoundedCornerShape(8.dp))
                .border(1.dp, Color.LightGray, RoundedCornerShape(8.dp))
        ) {
            val horizontalScrollState = rememberScrollState()
            val verticalScrollState = rememberScrollState()
            Box(
                modifier = Modifier
                    .verticalScroll(verticalScrollState)
                    .horizontalScroll(horizontalScrollState)
            ) {
                Column {
                    Row(
                        Modifier
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        sheetData.headers.forEach { header ->
                            TableCell(text = header, isHeader = true, width = cellWidth)
                        }
                    }
                    Divider()

                    sheetData.rows.forEach { rowData ->
                        Row {
                            sheetData.headers.forEach { header ->
                                TableCell(text = rowData[header] ?: "", width = cellWidth)
                            }
                        }
                        Divider()
                    }
                }
            }
        }
    }
}

@Composable
fun RowScope.TableCell(
    text: String,
    width: Dp,
    isHeader: Boolean = false
) {
    Text(
        text = text,
        modifier = Modifier
            .border(0.5.dp, Color.LightGray)
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .width(width),
        fontWeight = if (isHeader) FontWeight.Bold else FontWeight.Normal,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        textAlign = if (isHeader) TextAlign.Center else TextAlign.Start
    )
}

@Composable
fun MessagePreviewItem(finalMessage: FinalMessage) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.8f))
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(
                buildAnnotatedString {
                    withStyle(style = SpanStyle(fontWeight = FontWeight.Bold, color = Color.Black)) {
                        append("To: ${finalMessage.recipientName} ")
                    }
                    withStyle(style = SpanStyle(color = Color.Gray, fontSize = 12.sp)) {
                        append("(${finalMessage.recipientNumber})")
                    }
                }
            )
            Divider(modifier = Modifier.padding(vertical = 4.dp), color = Color.LightGray)
            Text(finalMessage.messageBody, color = Color.DarkGray)
        }
    }
}

private suspend fun parseSheet(context: Context, uri: Uri): SheetData? {
    val fileName = getFileName(context, uri)
    return when {
        fileName?.endsWith(".csv", ignoreCase = true) == true -> parseCsv(context, uri)
        fileName?.endsWith(".xlsx", ignoreCase = true) == true -> parseExcelSheet(context, uri)
        else -> {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Unsupported file type.", Toast.LENGTH_LONG).show()
            }
            null
        }
    }
}

private fun parseCsv(context: Context, uri: Uri): SheetData? {
    return try {
        val inputStream = context.contentResolver.openInputStream(uri)
        val reader = BufferedReader(InputStreamReader(inputStream, "UTF-8"))
        val lines = reader.readLines()
        reader.close()
        inputStream?.close()

        if (lines.isEmpty()) return null

        val headers = lines[0].split(",").map { it.trim() }
        val rows = lines.drop(1).mapNotNull { line ->
            val values = line.split(",").map { it.trim() }
            if (values.size == headers.size) {
                val cleanedRow = headers.zip(values).toMap().mapValues { (key, value) ->
                    cleanPhoneNumberValue(key, value)
                }
                cleanedRow
            } else {
                null // Skip malformed rows
            }
        }
        SheetData(headers, rows)
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

private fun parseExcelSheet(context: Context, uri: Uri): SheetData? {
    return try {
        val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
        val workbook = XSSFWorkbook(inputStream)
        val sheet = workbook.getSheetAt(0)
        val formatter = DataFormatter()

        val headerRow = sheet.getRow(0) ?: return null
        val headers = mutableListOf<String>()
        for (cell in headerRow) {
            headers.add(formatter.formatCellValue(cell).trim())
        }

        val rows = mutableListOf<Map<String, String>>()
        for (i in 1..sheet.lastRowNum) {
            val currentRow = sheet.getRow(i) ?: continue
            val rowMap = mutableMapOf<String, String>()
            for ((j, header) in headers.withIndex()) {
                val cell = currentRow.getCell(j)
                val cellValue = formatter.formatCellValue(cell).trim()
                val cleanedValue = cleanPhoneNumberValue(header, cellValue)
                rowMap[header] = cleanedValue
            }
            rows.add(rowMap)
        }
        workbook.close()
        inputStream?.close()
        SheetData(headers, rows)
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}


private fun generateMessagesFromSheet(template: String, data: SheetData?, countryCode: String): List<FinalMessage> {
    if (data == null) return emptyList()

    val messages = mutableListOf<FinalMessage>()
    val hasMessageColumn = data.headers.any { it.equals("Message", ignoreCase = true) }
    val useTemplate = template.isNotBlank()

    for (row in data.rows) {
        val name = row.entries.firstOrNull { it.key.equals("Name", ignoreCase = true) }?.value ?: "Customer"
        
        // Improved phone number detection - check multiple possible column names
        val number = row.entries.firstOrNull { entry ->
            val key = entry.key.lowercase()
            key.contains("phone") || key.contains("number") || key.contains("mobile") || 
            key.contains("contact") || key == "phone number" || key == "phonenumber"
        }?.value
        
        if (number.isNullOrBlank()) continue

        val trimmedNumber = number.trim()
        val finalNumber = if (trimmedNumber.startsWith("+")) {
            trimmedNumber
        } else {
            countryCode.trim() + trimmedNumber
        }

        val messageBody = when {
            useTemplate -> {
                var tempMessage = template
                data.headers.forEach { header ->
                    val placeholder = "($header)"
                    row[header]?.let { value ->
                        tempMessage = tempMessage.replace(placeholder, value, ignoreCase = true)
                    }
                }
                tempMessage
            }
            hasMessageColumn -> {
                row.entries.firstOrNull { it.key.equals("Message", ignoreCase = true) }?.value ?: ""
            }
            else -> template // Use template even if blank for preview
        }
        
        // Always add message for preview, even if messageBody is blank
        messages.add(FinalMessage(name, finalNumber, messageBody))
    }
    return messages
}

@SuppressLint("Range")
fun getFileName(context: Context, uri: Uri): String? {
    var result: String? = null
    if (uri.scheme == "content") {
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        try {
            if (cursor != null && cursor.moveToFirst()) {
                result = cursor.getString(cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME))
            }
        } finally {
            cursor?.close()
        }
    }
    if (result == null) {
        result = uri.path
        val cut = result?.lastIndexOf('/')
        if (cut != -1) {
            if (cut != null) {
                result = result.substring(cut + 1)
            }
        }
    }
    return result
}

/**
 * Clean phone number by removing .0 suffix and other formatting issues
 */
private fun cleanPhoneNumberValue(columnName: String, value: String): String {
    // Check if this is a phone number column
    val isPhoneColumn = columnName.contains("phone", ignoreCase = true) || 
                       columnName.contains("number", ignoreCase = true) ||
                       columnName.contains("mobile", ignoreCase = true) ||
                       columnName.contains("contact", ignoreCase = true)
    
    if (isPhoneColumn && value.isNotBlank()) {
        // Remove .0 suffix that appears when Excel treats numbers as decimals
        val cleanedValue = if (value.endsWith(".0")) {
            value.substring(0, value.length - 2)
        } else {
            value
        }
        
        // Remove any other decimal formatting for phone numbers
        return cleanedValue.replace(Regex("\\.0+$"), "").trim()
    }
    
    return value.trim()
}

