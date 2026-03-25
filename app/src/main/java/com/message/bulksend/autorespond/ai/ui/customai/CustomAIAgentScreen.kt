package com.message.bulksend.autorespond.ai.ui.customai

import android.content.Intent
import android.util.Log
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.message.bulksend.autorespond.ai.autonomous.AutonomousGoalRuntime
import com.message.bulksend.autorespond.ai.customsheet.CustomTemplateSheetManager
import com.message.bulksend.autorespond.ai.settings.AIAgentSettingsManager
import com.message.bulksend.autorespond.tools.sheetconnect.SheetConnectSetupActivity
import com.message.bulksend.autorespond.tools.sheetconnect.SheetConnectManager
import com.message.bulksend.tablesheet.TableSheetActivity
import com.message.bulksend.autorespond.ai.needdiscovery.ui.NeedDiscoveryActivity
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

private const val DEFAULT_CUSTOM_FIELD_TYPE = "Text"

internal data class CustomWriteFieldSpec(
    val name: String,
    val type: String
)

internal data class GoogleSpreadsheetOption(
    val ref: String,
    val title: String
)

private val customWriteFieldTypes = listOf(
    "Text",
    "Number",
    "Date",
    "Email",
    "Phone",
    "URL",
    "Checkbox",
    "Currency"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CustomAIAgentScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val settingsManager = remember { AIAgentSettingsManager(context) }
    val sheetManager = remember { CustomTemplateSheetManager(context) }
    val sheetConnectManager = remember { SheetConnectManager(context) }

    val autonomousRuntime = remember { AutonomousGoalRuntime(context) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var selectedTab by rememberSaveable { mutableStateOf(CustomAIAgentTab.PROMPT) }

    var customEnabled by rememberSaveable {
        mutableStateOf(settingsManager.activeTemplate.equals("CUSTOM", ignoreCase = true))
    }
    var templateName by rememberSaveable { mutableStateOf(settingsManager.customTemplateName) }
    var templateGoal by rememberSaveable { mutableStateOf(settingsManager.customTemplateGoal) }
    var templateTone by rememberSaveable { mutableStateOf(settingsManager.customTemplateTone) }
    var templateInstructions by rememberSaveable {
        mutableStateOf(settingsManager.customTemplateInstructions)
    }
    var promptMode by rememberSaveable {
        mutableStateOf(settingsManager.customTemplatePromptMode)
    }
    var taskModeEnabled by rememberSaveable {
        mutableStateOf(settingsManager.customTemplateTaskModeEnabled)
    }
    var repeatCounterEnabled by rememberSaveable {
        mutableStateOf(settingsManager.customTemplateRepeatCounterEnabled)
    }
    var repeatCounterLimitText by rememberSaveable {
        val saved = settingsManager.customTemplateRepeatCounterLimit
        mutableStateOf(if (saved > 0) saved.toString() else "")
    }
    var repeatCounterOwnerNotifyEnabled by rememberSaveable {
        mutableStateOf(settingsManager.customTemplateRepeatCounterOwnerNotifyEnabled)
    }
    var repeatCounterOwnerPhone by rememberSaveable {
        mutableStateOf(settingsManager.customTemplateRepeatCounterOwnerPhone)
    }

    var paymentEnabled by rememberSaveable {
        mutableStateOf(settingsManager.customTemplateEnablePaymentTool)
    }
    var paymentVerificationEnabled by rememberSaveable {
        mutableStateOf(settingsManager.customTemplateEnablePaymentVerificationTool)
    }
    var documentEnabled by rememberSaveable {
        mutableStateOf(settingsManager.customTemplateEnableDocumentTool)
    }
    var agentFormEnabled by rememberSaveable {
        mutableStateOf(settingsManager.customTemplateEnableAgentFormTool)
    }
    var speechEnabled by rememberSaveable {
        mutableStateOf(settingsManager.customTemplateEnableSpeechTool)
    }
    var autonomousCatalogueEnabled by rememberSaveable {
        mutableStateOf(settingsManager.customTemplateEnableAutonomousCatalogueSend)
    }
    var sheetReadEnabled by rememberSaveable {
        mutableStateOf(settingsManager.customTemplateEnableSheetReadTool)
    }
    var sheetWriteEnabled by rememberSaveable {
        mutableStateOf(settingsManager.customTemplateEnableSheetWriteTool)
    }
    var googleCalendarEnabled by rememberSaveable {
        mutableStateOf(settingsManager.customTemplateEnableGoogleCalendarTool)
    }
    var googleGmailEnabled by rememberSaveable {
        mutableStateOf(settingsManager.customTemplateEnableGoogleGmailTool)
    }
    var nativeToolCallingEnabled by rememberSaveable {
        mutableStateOf(settingsManager.customTemplateNativeToolCallingEnabled)
    }
    var continuousAutonomousEnabled by rememberSaveable {
        mutableStateOf(settingsManager.customTemplateContinuousAutonomousEnabled)
    }
    var autonomousSilenceGapMinutesText by rememberSaveable {
        mutableStateOf(settingsManager.customTemplateAutonomousSilenceGapMinutes.toString())
    }
    var autonomousQueueSize by rememberSaveable { mutableStateOf(0) }
    var autonomousLastHeartbeatAt by rememberSaveable { mutableStateOf(0L) }
    var autonomousLastError by rememberSaveable { mutableStateOf("") }

    var sheetFolderName by rememberSaveable {
        mutableStateOf(settingsManager.customTemplateSheetFolderName)
    }
    var readSheetName by rememberSaveable {
        mutableStateOf(settingsManager.customTemplateReadSheetName)
    }
    var referenceSheetName by rememberSaveable {
        mutableStateOf(settingsManager.customTemplateReferenceSheetName)
    }
    var writeSheetName by rememberSaveable {
        mutableStateOf(settingsManager.customTemplateWriteSheetName)
    }
    var salesSheetName by rememberSaveable {
        mutableStateOf(settingsManager.customTemplateSalesSheetName)
    }
    var writeStorageMode by rememberSaveable {
        mutableStateOf(settingsManager.customTemplateWriteStorageMode)
    }
    var googleSheetIdInput by rememberSaveable {
        mutableStateOf(settingsManager.customTemplateGoogleSheetId)
    }
    var googleWriteSheetName by rememberSaveable {
        mutableStateOf(settingsManager.customTemplateGoogleWriteSheetName)
    }
    var connectedGoogleSheetId by rememberSaveable {
        mutableStateOf(settingsManager.customTemplateGoogleSheetId)
    }
    var connectedGoogleSheetName by rememberSaveable {
        mutableStateOf(settingsManager.customTemplateGoogleSheetName)
    }

    val writeFieldSpecs =
        remember {
            val raw = parseWriteFieldSpecs(settingsManager.customTemplateWriteFieldSchema)
            val fallback = parseWriteFieldColumns(settingsManager.customTemplateWriteSheetColumns)
            val initial =
                raw.ifEmpty { fallback.ifEmpty { listOf(CustomWriteFieldSpec("", DEFAULT_CUSTOM_FIELD_TYPE)) } }
            mutableStateListOf<CustomWriteFieldSpec>().apply {
                addAll(initial.distinctBy { it.name.lowercase() })
            }
        }

    var setupInProgress by rememberSaveable { mutableStateOf(false) }
    var availableReferenceSheetNames by remember { mutableStateOf(listOf("All Sheets")) }
    var availableGoogleSpreadsheetOptions by remember { mutableStateOf(listOf<GoogleSpreadsheetOption>()) }
    var availableGoogleWriteSheetNames by remember { mutableStateOf(listOf<String>()) }
    var googleWriteSheetStatus by rememberSaveable { mutableStateOf("") }
    val referenceSheetFolderName = "AI Agent Data Sheet"
    val lifecycleOwner = LocalLifecycleOwner.current

    val resolvedTemplateName = templateName.trim().ifBlank { "Custom AI Template" }

    fun refreshAutonomousStatus() {
        val status = autonomousRuntime.getRuntimeStatus()
        autonomousQueueSize = status.queueSize
        autonomousLastHeartbeatAt = status.lastHeartbeatAt
        autonomousLastError = status.lastError
    }

    fun persistWriteFields() {
        val cleanFields =
            writeFieldSpecs
                .map { CustomWriteFieldSpec(it.name.trim(), normalizeWriteFieldType(it.type)) }
                .filter { it.name.isNotBlank() }
                .distinctBy { it.name.lowercase() }

        settingsManager.customTemplateWriteFieldSchema = buildWriteFieldSchemaJson(cleanFields)
        settingsManager.customTemplateWriteSheetColumns = cleanFields.joinToString(",") { it.name }
    }

    suspend fun setupSheets(showSnackbar: Boolean) {
        setupInProgress = true
        runCatching {
            val cleanFields =
                writeFieldSpecs
                    .map { CustomWriteFieldSpec(it.name.trim(), normalizeWriteFieldType(it.type)) }
                    .filter { it.name.isNotBlank() }
                    .distinctBy { it.name.lowercase() }
            val cleanNames = cleanFields.map { it.name }
            val setup =
                sheetManager.ensureTemplateSheetSystem(
                    templateName = resolvedTemplateName,
                    readSheetNameOverride = readSheetName,
                    writeSheetNameOverride = writeSheetName,
                    salesSheetNameOverride = salesSheetName,
                    writeCustomColumns = cleanNames
                )
            sheetFolderName = setup.folderName
            readSheetName = setup.readSheetName
            writeSheetName = setup.writeSheetName
            salesSheetName = setup.salesSheetName
            settingsManager.customTemplateSheetFolderName = sheetFolderName
            settingsManager.customTemplateReadSheetName = readSheetName
            settingsManager.customTemplateWriteSheetName = writeSheetName
            settingsManager.customTemplateSalesSheetName = salesSheetName
            settingsManager.customTemplateWriteSheetColumns = cleanNames.joinToString(",")
            settingsManager.customTemplateWriteFieldSchema = buildWriteFieldSchemaJson(cleanFields)
            if (showSnackbar) snackbarHostState.showSnackbar("Custom sheet tools ready.")
        }.onFailure {
            snackbarHostState.showSnackbar("Failed to setup sheets: ${it.message}")
        }
        setupInProgress = false
    }

    suspend fun syncReferenceSheetOptions() {
        val sheetNames =
            runCatching {
                sheetManager.listSheetNamesInFolder(referenceSheetFolderName)
            }.getOrElse { emptyList() }
                .mapNotNull { it.trim().takeIf { name -> name.isNotBlank() } }
                .distinct()
                .sorted()

        val fallback =
            when {
                sheetNames.isEmpty() -> "All Sheets"
                settingsManager.customTemplateReferenceSheetName.equals(
                    "All Sheets",
                    ignoreCase = true
                ) || settingsManager.customTemplateReferenceSheetName.isBlank() -> "All Sheets"
                sheetNames.any {
                    it.equals(settingsManager.customTemplateReferenceSheetName, ignoreCase = true)
                } -> settingsManager.customTemplateReferenceSheetName
                else -> sheetNames.first()
            }
        availableReferenceSheetNames =
            listOf("All Sheets") + sheetNames
        referenceSheetName = fallback
        settingsManager.customTemplateReferenceSheetName = fallback
    }

    suspend fun refreshGoogleConnectionSummary() {
        val config = runCatching { sheetConnectManager.getMappingConfig() }.getOrNull()
        val manualId = settingsManager.customTemplateGoogleSheetId.trim()
        val manualName = settingsManager.customTemplateGoogleSheetName.trim()
        val targetNameSetting = settingsManager.customTemplateGoogleWriteSheetName.trim()
        val createdSheets = runCatching { sheetConnectManager.getCreatedSheets() }.getOrElse { emptyList() }
        val mappingRef =
            config?.spreadsheetId.orEmpty().trim().ifBlank {
                config?.spreadsheetUrlId.orEmpty().trim()
            }
        val mappingTitle = config?.sheetName.orEmpty().trim()

        fun extractSpreadsheetId(ref: String): String {
            val raw = ref.trim()
            val match = Regex("/spreadsheets/d/([a-zA-Z0-9-_]+)").find(raw)
            return match?.groupValues?.getOrNull(1)?.trim().orEmpty()
        }

        fun normalizeSpreadsheetRef(ref: String): String {
            val cleanRef = ref.trim()
            return extractSpreadsheetId(cleanRef).ifBlank { cleanRef }
        }

        fun refsMatch(first: String, second: String): Boolean {
            val cleanFirst = normalizeSpreadsheetRef(first)
            val cleanSecond = normalizeSpreadsheetRef(second)
            if (cleanFirst.isBlank() || cleanSecond.isBlank()) return false
            if (cleanFirst.equals(cleanSecond, ignoreCase = true)) return true

            val firstId = extractSpreadsheetId(cleanFirst).ifBlank { cleanFirst.takeIf { !it.contains('/') }.orEmpty() }
            val secondId = extractSpreadsheetId(cleanSecond).ifBlank { cleanSecond.takeIf { !it.contains('/') }.orEmpty() }
            return firstId.isNotBlank() && secondId.isNotBlank() && firstId.equals(secondId, ignoreCase = true)
        }

        fun createdTitleForRef(ref: String): String {
            val cleanRef = ref.trim()
            if (cleanRef.isBlank()) return ""
            return createdSheets.firstOrNull { created ->
                refsMatch(created.spreadsheetId, cleanRef) || refsMatch(created.spreadsheetUrl, cleanRef)
            }?.title?.trim().orEmpty()
        }

        fun looksLikeSheetTabName(name: String): Boolean {
            val cleanName = name.trim()
            if (cleanName.isBlank()) return false
            return Regex("^sheet\\s*\\d*$", RegexOption.IGNORE_CASE).matches(cleanName) ||
                cleanName.equals(targetNameSetting, ignoreCase = true) ||
                cleanName.equals(mappingTitle, ignoreCase = true)
        }

        val selectedRef = normalizeSpreadsheetRef(googleSheetIdInput.trim().ifBlank { manualId })
        val normalizedMappingRef = normalizeSpreadsheetRef(mappingRef)
        val legacyMappedSelection =
            selectedRef.isNotBlank() &&
                refsMatch(selectedRef, normalizedMappingRef) &&
                manualName.equals(mappingTitle, ignoreCase = true)
        val shouldPreferCreatedSheet =
            createdSheets.isNotEmpty() &&
                (selectedRef.isBlank() || legacyMappedSelection || looksLikeSheetTabName(manualName))

        val optionMap = linkedMapOf<String, GoogleSpreadsheetOption>()

        fun addOption(ref: String, title: String) {
            val cleanRef = normalizeSpreadsheetRef(ref)
            if (cleanRef.isBlank()) return
            val resolvedTitle =
                createdTitleForRef(cleanRef).ifBlank {
                    title.trim()
                }.ifBlank { "Google Spreadsheet" }

            val existing = optionMap[cleanRef]
            if (existing == null || existing.title.equals("Google Spreadsheet", ignoreCase = true)) {
                optionMap[cleanRef] = GoogleSpreadsheetOption(cleanRef, resolvedTitle)
            }
        }

        createdSheets.forEach { sheet ->
            addOption(sheet.spreadsheetId, sheet.title.ifBlank { "Created Spreadsheet" })
            addOption(sheet.spreadsheetUrl, sheet.title.ifBlank { "Created Spreadsheet (URL)" })
        }
        addOption(manualId, manualName.ifBlank { "Manual Spreadsheet" })
        addOption(mappingRef, createdTitleForRef(mappingRef).ifBlank { mappingTitle.ifBlank { "Mapped Spreadsheet" } })
        availableGoogleSpreadsheetOptions = optionMap.values.toList()

        val candidateRefs = linkedSetOf<String>()
        val preferredCreatedRef =
            createdSheets.firstOrNull()?.spreadsheetId?.trim().takeIf { !it.isNullOrBlank() }
                ?: createdSheets.firstOrNull()?.spreadsheetUrl?.trim().orEmpty()
        val preferredRef =
            when {
                shouldPreferCreatedSheet && preferredCreatedRef.isNotBlank() -> normalizeSpreadsheetRef(preferredCreatedRef)
                selectedRef.isNotBlank() -> selectedRef
                else -> ""
            }

        if (preferredRef.isNotBlank()) candidateRefs.add(preferredRef)
        optionMap.keys.forEach { ref ->
            if (ref.isNotBlank()) candidateRefs.add(ref)
        }

        Log.d(
            "CustomAIAgentScreen",
            "refreshGoogleConnectionSummary manualId=${manualId.isNotBlank()} mappingRef=${mappingRef.isNotBlank()} createdSheets=${createdSheets.size} options=${availableGoogleSpreadsheetOptions.size} candidates=${candidateRefs.size} preferCreated=$shouldPreferCreatedSheet"
        )

        if (candidateRefs.isEmpty()) {
            availableGoogleWriteSheetNames = listOf()
            googleWriteSheetStatus = "Spreadsheet ID missing. Setup ya ID paste karke reload karo."
            connectedGoogleSheetId = ""
            connectedGoogleSheetName = "Google sheet not connected"
            return
        }

        var resolvedSpreadsheetRef = candidateRefs.first()
        var sheets = emptyList<String>()
        var lastError = ""
        var resolvedSpreadsheetTitle = optionMap[resolvedSpreadsheetRef]?.title.orEmpty()

        for (candidateRef in candidateRefs) {
            val metadataResult = runCatching { sheetConnectManager.fetchSheetMetadata(candidateRef) }.getOrNull()
            if (metadataResult == null || metadataResult.isFailure) {
                val errorMsg = metadataResult?.exceptionOrNull()?.message.orEmpty()
                if (errorMsg.isNotBlank()) {
                    lastError = errorMsg
                }
                Log.e(
                    "CustomAIAgentScreen",
                    "metadata fetch failed ref='${candidateRef.take(120)}' error='$errorMsg'"
                )
                continue
            }

            val metadata = metadataResult.getOrNull()
            val currentSheets =
                metadata
                    ?.sheets
                    .orEmpty()
                    .mapNotNull { it.sheetName.trim().takeIf { name -> name.isNotBlank() } }
                    .distinct()
                    .sorted()
            Log.d(
                "CustomAIAgentScreen",
                "metadata fetch success ref='${candidateRef.take(120)}' tabs=${currentSheets.size}"
            )

            resolvedSpreadsheetRef = candidateRef
            sheets = currentSheets
            resolvedSpreadsheetTitle =
                metadata?.spreadsheetTitle?.trim().orEmpty()
                    .ifBlank { optionMap[candidateRef]?.title.orEmpty() }
            if (currentSheets.isNotEmpty()) {
                break
            }
        }

        val resolvedSpreadsheetName =
            resolvedSpreadsheetTitle.ifBlank {
                optionMap[resolvedSpreadsheetRef]?.title
                    ?: manualName.takeUnless { looksLikeSheetTabName(it) }
                    ?: createdTitleForRef(resolvedSpreadsheetRef)
                    ?: mappingTitle.takeUnless { looksLikeSheetTabName(it) }
                    ?: createdSheets.firstOrNull()?.title.orEmpty()
            }

        connectedGoogleSheetId = resolvedSpreadsheetRef
        connectedGoogleSheetName = resolvedSpreadsheetName.ifBlank { "Custom Google Sheet" }
        settingsManager.customTemplateGoogleSheetId = resolvedSpreadsheetRef
        settingsManager.customTemplateGoogleSheetName = connectedGoogleSheetName
        if (!googleSheetIdInput.equals(resolvedSpreadsheetRef, ignoreCase = false)) {
            googleSheetIdInput = resolvedSpreadsheetRef
        }
        availableGoogleWriteSheetNames = sheets
        googleWriteSheetStatus = ""
        if (sheets.isEmpty()) {
            googleWriteSheetStatus =
                if (lastError.isNotBlank()) {
                    "Sheet list load failed: $lastError"
                } else {
                    "No tabs found. Sheet ke first row/header ya access permission check karo."
                }
        }

        val resolvedTargetSheet =
            when {
                targetNameSetting.isNotBlank() &&
                        sheets.any { it.equals(targetNameSetting, ignoreCase = true) } -> targetNameSetting
                resolvedSpreadsheetName.isNotBlank() &&
                        sheets.any { it.equals(resolvedSpreadsheetName, ignoreCase = true) } -> resolvedSpreadsheetName
                sheets.isNotEmpty() -> sheets.first()
                else -> targetNameSetting
            }

        googleWriteSheetName = resolvedTargetSheet
        settingsManager.customTemplateGoogleWriteSheetName = resolvedTargetSheet
    }

    DisposableEffect(lifecycleOwner) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    customEnabled = settingsManager.activeTemplate.equals("CUSTOM", ignoreCase = true)
                    promptMode = settingsManager.customTemplatePromptMode
                    taskModeEnabled = settingsManager.customTemplateTaskModeEnabled
                    scope.launch {
                        syncReferenceSheetOptions()
                        refreshGoogleConnectionSummary()
                    }
                    refreshAutonomousStatus()
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(Unit) {
        syncReferenceSheetOptions()
        refreshGoogleConnectionSummary()
        persistWriteFields()
        refreshAutonomousStatus()
        if (continuousAutonomousEnabled) {
            autonomousRuntime.scheduleHeartbeat()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        CustomAIDottedBackground(modifier = Modifier.matchParentSize())

        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            "Custom AI Agent",
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = Color.White
                            )
                        }
                    },
                    colors =
                        TopAppBarDefaults.topAppBarColors(
                            containerColor = Color(0xFF2563EB),
                            titleContentColor = Color.White,
                            navigationIconContentColor = Color.White
                        )
                )
            },
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            containerColor = Color.Transparent,
            bottomBar = {
                NavigationBar(
                    containerColor = Color(0xFF1E1E2E).copy(alpha = 0.95f),
                    contentColor = Color.White
                ) {
                    CustomAIAgentTab.entries.forEach { tab ->
                        NavigationBarItem(
                            selected = selectedTab == tab,
                            onClick = { selectedTab = tab },
                            icon = { Icon(tab.icon, contentDescription = tab.title) },
                            label = { Text(tab.title) }
                        )
                    }
                }
            }
        ) { paddingValues ->
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
            ) {
                when (selectedTab) {
                    CustomAIAgentTab.PROMPT ->
                        CustomAIAgentPromptTab(
                            templateName = templateName,
                            templateGoal = templateGoal,
                            templateTone = templateTone,
                            templateInstructions = templateInstructions,
                            promptMode = promptMode,
                            taskModeEnabled = taskModeEnabled,
                            onTemplateNameChange = {
                                templateName = it
                                settingsManager.customTemplateName = it.trim().ifBlank { "Custom AI Template" }
                            },
                            onTemplateGoalChange = {
                                templateGoal = it
                                settingsManager.customTemplateGoal = it.trim()
                            },
                            onTemplateToneChange = {
                                templateTone = it
                                settingsManager.customTemplateTone = it.trim()
                            },
                            onTemplateInstructionsChange = {
                                templateInstructions = it
                                settingsManager.customTemplateInstructions = it.trim()
                            },
                            onPromptModeChange = { selectedMode ->
                                promptMode = selectedMode
                                settingsManager.customTemplatePromptMode = selectedMode
                            },
                            onOpenStepFlow = {
                                context.startActivity(
                                    Intent(
                                        context,
                                        com.message.bulksend.autorespond.ai.customtask.ui.AgentTaskActivity::class.java
                                    )
                                )
                            }
                        )

                    CustomAIAgentTab.SETTINGS ->
                        CustomAIAgentSettingsTab(
                            customEnabled = customEnabled,
                            setupInProgress = setupInProgress,
                            customSheetFolderName = sheetFolderName,
                            referenceSheetName = referenceSheetName,
                            availableReferenceSheetNames = availableReferenceSheetNames,
                            writeStorageMode = writeStorageMode,
                            writeFields = writeFieldSpecs.toList(),
                            writeFieldTypes = customWriteFieldTypes,
                            repeatCounterEnabled = repeatCounterEnabled,
                            repeatCounterLimitText = repeatCounterLimitText,
                            repeatCounterOwnerNotifyEnabled = repeatCounterOwnerNotifyEnabled,
                            repeatCounterOwnerPhone = repeatCounterOwnerPhone,
                            onCustomEnabledChange = { enabled ->
                                customEnabled = enabled
                                if (enabled) {
                                    settingsManager.activeTemplate = "CUSTOM"
                                    scope.launch {
                                        setupSheets(showSnackbar = false)
                                        syncReferenceSheetOptions()
                                        snackbarHostState.showSnackbar("Custom template enabled.")
                                    }
                                } else {
                                    if (settingsManager.activeTemplate.equals("CUSTOM", ignoreCase = true)) {
                                        settingsManager.activeTemplate = "NONE"
                                    }
                                    scope.launch { snackbarHostState.showSnackbar("Custom template disabled.") }
                                }
                            },
                            onRepeatCounterEnabledChange = { enabled ->
                                repeatCounterEnabled = enabled
                                settingsManager.customTemplateRepeatCounterEnabled = enabled
                                if (!enabled) {
                                    repeatCounterLimitText = ""
                                    repeatCounterOwnerNotifyEnabled = false
                                    repeatCounterOwnerPhone = ""
                                    settingsManager.customTemplateRepeatCounterLimit = 0
                                    settingsManager.customTemplateRepeatCounterOwnerNotifyEnabled = false
                                    settingsManager.customTemplateRepeatCounterOwnerPhone = ""
                                }
                            },
                            onRepeatCounterLimitTextChange = {
                                repeatCounterLimitText = it
                                settingsManager.customTemplateRepeatCounterLimit = it.toIntOrNull() ?: 0
                            },
                            onRepeatCounterOwnerNotifyEnabledChange = { enabled ->
                                repeatCounterOwnerNotifyEnabled = enabled
                                settingsManager.customTemplateRepeatCounterOwnerNotifyEnabled = enabled
                                if (!enabled) {
                                    repeatCounterOwnerPhone = ""
                                    settingsManager.customTemplateRepeatCounterOwnerPhone = ""
                                }
                            },
                            onRepeatCounterOwnerPhoneChange = {
                                repeatCounterOwnerPhone = it
                                settingsManager.customTemplateRepeatCounterOwnerPhone = it.trim()
                            },
                            onReferenceSheetNameChange = { selected ->
                                referenceSheetName = selected
                                settingsManager.customTemplateReferenceSheetName = selected
                            },
                            onWriteStorageModeChange = { mode ->
                                writeStorageMode = mode
                                settingsManager.customTemplateWriteStorageMode = mode
                                if (mode.equals(AIAgentSettingsManager.SHEET_WRITE_MODE_GOOGLE, ignoreCase = true)) {
                                    scope.launch { refreshGoogleConnectionSummary() }
                                }
                            },
                            availableGoogleSpreadsheetOptions = availableGoogleSpreadsheetOptions,
                            selectedGoogleSpreadsheetRef = googleSheetIdInput.trim(),
                            onGoogleSpreadsheetRefChange = { selectedRef ->
                                val cleanRef = selectedRef.trim()
                                val selected =
                                    availableGoogleSpreadsheetOptions.find {
                                        it.ref.equals(cleanRef, ignoreCase = true)
                                    }
                                googleSheetIdInput = cleanRef
                                settingsManager.customTemplateGoogleSheetId = cleanRef
                                settingsManager.customTemplateGoogleSheetName = selected?.title.orEmpty()
                                scope.launch { refreshGoogleConnectionSummary() }
                            },
                            onRefreshGoogleSheetsClick = {
                                scope.launch { refreshGoogleConnectionSummary() }
                            },
                            availableGoogleWriteSheetNames = availableGoogleWriteSheetNames,
                            selectedGoogleWriteSheetName = googleWriteSheetName,
                            googleWriteSheetStatus = googleWriteSheetStatus,
                            connectedGoogleSheetName = connectedGoogleSheetName,
                            connectedGoogleSheetId = connectedGoogleSheetId,
                            googleSheetIdInput = googleSheetIdInput,
                            onGoogleSheetIdInputChange = { sheetId ->
                                val cleanId = sheetId.trim()
                                googleSheetIdInput = sheetId
                                settingsManager.customTemplateGoogleSheetId = cleanId
                                if (cleanId.isBlank()) {
                                    settingsManager.customTemplateGoogleSheetName = ""
                                }
                                if (googleWriteSheetName.isBlank()) {
                                    settingsManager.customTemplateGoogleWriteSheetName = ""
                                }
                                scope.launch { refreshGoogleConnectionSummary() }
                            },
                            onGoogleWriteSheetNameChange = { sheetName ->
                                googleWriteSheetName = sheetName
                                settingsManager.customTemplateGoogleWriteSheetName = sheetName.trim()
                            },
                            onAddWriteField = {
                                writeFieldSpecs.add(CustomWriteFieldSpec("", DEFAULT_CUSTOM_FIELD_TYPE))
                                persistWriteFields()
                            },
                            onWriteFieldNameChange = { index, value ->
                                if (index in writeFieldSpecs.indices) {
                                    writeFieldSpecs[index] = writeFieldSpecs[index].copy(name = value)
                                    persistWriteFields()
                                }
                            },
                            onWriteFieldTypeChange = { index, value ->
                                if (index in writeFieldSpecs.indices) {
                                    writeFieldSpecs[index] = writeFieldSpecs[index].copy(type = value)
                                    persistWriteFields()
                                }
                            },
                            onRemoveWriteField = { index ->
                                if (writeFieldSpecs.size > 1 && index in writeFieldSpecs.indices) {
                                    writeFieldSpecs.removeAt(index)
                                    if (writeFieldSpecs.isEmpty()) {
                                        writeFieldSpecs.add(CustomWriteFieldSpec("", DEFAULT_CUSTOM_FIELD_TYPE))
                                    }
                                    persistWriteFields()
                                }
                            },
                            onSetupClick = {
                                scope.launch {
                                    if (writeStorageMode == AIAgentSettingsManager.SHEET_WRITE_MODE_TABLE) {
                                        persistWriteFields()
                                        setupSheets(showSnackbar = true)
                                        syncReferenceSheetOptions()
                                    } else {
                                        context.startActivity(
                                            Intent(context, SheetConnectSetupActivity::class.java)
                                        )
                                    }
                                }
                            },
                            onOpenAIDataFolderClick = {
                                context.startActivity(
                                    Intent(context, TableSheetActivity::class.java).apply {
                                        putExtra("openFolder", referenceSheetFolderName)
                                    }
                                )
                            },
                            onOpenCustomFolderClick = {
                                val folderToOpen =
                                    sheetFolderName.ifBlank {
                                        sheetManager.buildFolderName(resolvedTemplateName)
                                    }
                                context.startActivity(
                                    Intent(context, TableSheetActivity::class.java).apply {
                                        putExtra("openFolder", folderToOpen)
                                    }
                                )
                            },
                            onOpenGoogleSetupClick = {
                                context.startActivity(
                                    Intent(context, SheetConnectSetupActivity::class.java)
                                )
                            }
                        )

                    CustomAIAgentTab.TOOLS ->
                        CustomAIAgentToolsTab(
                            paymentEnabled = paymentEnabled,
                            paymentVerificationEnabled = paymentVerificationEnabled,
                            documentEnabled = documentEnabled,
                            agentFormEnabled = agentFormEnabled,
                            speechEnabled = speechEnabled,
                            autonomousCatalogueEnabled = autonomousCatalogueEnabled,
                            sheetReadEnabled = sheetReadEnabled,
                            sheetWriteEnabled = sheetWriteEnabled,
                            onPaymentEnabledChange = {
                                paymentEnabled = it
                                settingsManager.customTemplateEnablePaymentTool = it
                                if (!it) {
                                    paymentVerificationEnabled = false
                                    settingsManager.customTemplateEnablePaymentVerificationTool = false
                                }
                            },
                            onPaymentVerificationEnabledChange = {
                                paymentVerificationEnabled = it
                                settingsManager.customTemplateEnablePaymentVerificationTool = it
                            },
                            onDocumentEnabledChange = {
                                documentEnabled = it
                                settingsManager.customTemplateEnableDocumentTool = it
                            },
                            onAgentFormEnabledChange = {
                                agentFormEnabled = it
                                settingsManager.customTemplateEnableAgentFormTool = it
                            },
                            onSpeechEnabledChange = {
                                speechEnabled = it
                                settingsManager.customTemplateEnableSpeechTool = it
                            },
                            onAutonomousCatalogueEnabledChange = {
                                autonomousCatalogueEnabled = it
                                settingsManager.customTemplateEnableAutonomousCatalogueSend = it
                            },
                            onSheetReadEnabledChange = {
                                sheetReadEnabled = it
                                settingsManager.customTemplateEnableSheetReadTool = it
                            },
                            onSheetWriteEnabledChange = {
                                sheetWriteEnabled = it
                                settingsManager.customTemplateEnableSheetWriteTool = it
                            },
                            googleCalendarEnabled = googleCalendarEnabled,
                            onGoogleCalendarEnabledChange = {
                                googleCalendarEnabled = it
                                settingsManager.customTemplateEnableGoogleCalendarTool = it
                            },
                            googleGmailEnabled = googleGmailEnabled,
                            onGoogleGmailEnabledChange = {
                                googleGmailEnabled = it
                                settingsManager.customTemplateEnableGoogleGmailTool = it
                            },
                            nativeToolCallingEnabled = nativeToolCallingEnabled,
                            onNativeToolCallingEnabledChange = {
                                nativeToolCallingEnabled = it
                                settingsManager.customTemplateNativeToolCallingEnabled = it
                            },
                            continuousAutonomousEnabled = continuousAutonomousEnabled,
                            onContinuousAutonomousEnabledChange = {
                                continuousAutonomousEnabled = it
                                settingsManager.customTemplateContinuousAutonomousEnabled = it
                                if (it) {
                                    val delayMinutes = autonomousSilenceGapMinutesText.toIntOrNull() ?: 2
                                    settingsManager.customTemplateAutonomousSilenceGapMinutes = delayMinutes
                                    autonomousSilenceGapMinutesText = delayMinutes.toString()
                                    autonomousRuntime.scheduleHeartbeat()
                                    autonomousRuntime.scheduleImmediateKick(delaySeconds = 5)
                                } else {
                                    autonomousRuntime.cancelHeartbeat()
                                }

                                refreshAutonomousStatus()
                            },
                            autonomousSilenceGapMinutesText = autonomousSilenceGapMinutesText,
                            onAutonomousSilenceGapMinutesTextChange = { raw ->
                                val filtered = raw.filter { ch -> ch.isDigit() }.take(4)
                                autonomousSilenceGapMinutesText = filtered
                                val parsed = filtered.toIntOrNull()
                                if (parsed != null) {
                                    settingsManager.customTemplateAutonomousSilenceGapMinutes = parsed
                                }
                            },
                            runtimeQueueSize = autonomousQueueSize,
                            runtimeLastHeartbeatAt = autonomousLastHeartbeatAt,
                            runtimeLastError = autonomousLastError,
                            onOpenNeedDiscovery = {
                                context.startActivity(Intent(context, NeedDiscoveryActivity::class.java))
                            }
                        )
                }
            }
        }
    }
}

@Composable
fun CustomAIDottedBackground(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.fillMaxSize()) {
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color(0xFF0B0F17),
                    Color(0xFF121826)
                )
            )
        )
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0xFF2563EB).copy(alpha = 0.08f),
                    Color.Transparent
                ),
                center = Offset(size.width * 0.1f, size.height * 0.85f),
                radius = size.minDimension * 0.75f
            )
        )
        val dotRadius = 2.0f
        val spacing = 26f
        val dotColor = Color(0xFF2F3A4D).copy(alpha = 0.7f)
        val columns = (size.width / spacing).toInt() + 2
        val rows = (size.height / spacing).toInt() + 1

        for (i in 0 until columns) {
            for (j in 0 until rows) {
                val xOffset = if (j % 2 == 0) 0f else spacing / 2f
                drawCircle(
                    color = dotColor,
                    radius = dotRadius,
                    center = Offset(i * spacing + xOffset, j * spacing)
                )
            }
        }
    }
}

private fun parseWriteFieldColumns(raw: String): List<CustomWriteFieldSpec> {
    return raw
        .split(",")
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .map { CustomWriteFieldSpec(name = it, type = DEFAULT_CUSTOM_FIELD_TYPE) }
}

private fun parseWriteFieldSpecs(raw: String): List<CustomWriteFieldSpec> {
    if (raw.isBlank()) return emptyList()
    return try {
        val arr = JSONArray(raw)
        val out = mutableListOf<CustomWriteFieldSpec>()
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val name = obj.optString("name").trim()
            val type = normalizeWriteFieldType(obj.optString("type"))
            if (name.isNotBlank()) {
                out.add(CustomWriteFieldSpec(name = name, type = type))
            }
        }
        out
    } catch (_: Exception) {
        parseWriteFieldColumns(raw)
    }
}

private fun normalizeWriteFieldType(raw: String): String {
    val clean = raw.trim()
    return if (clean.isBlank() || !customWriteFieldTypes.contains(clean)) {
        DEFAULT_CUSTOM_FIELD_TYPE
    } else {
        clean
    }
}

private fun buildWriteFieldSchemaJson(fields: List<CustomWriteFieldSpec>): String {
    val arr = JSONArray()
    fields.forEach { field ->
        if (field.name.isNotBlank()) {
            arr.put(
                JSONObject().put("name", field.name.trim()).put(
                    "type",
                    normalizeWriteFieldType(field.type)
                )
            )
        }
    }
    return arr.toString()
}












