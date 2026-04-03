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
private const val DEFAULT_LEGACY_AGENT_FOLDER = "AI Agent Data Sheet"

internal data class CustomWriteFieldSpec(
    val name: String,
    val type: String
)

internal data class GoogleSpreadsheetOption(
    val ref: String,
    val title: String
)


internal data class SheetColumnSummary(
    val sheetName: String,
    val columns: List<String>
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
    var longChatSummaryEnabled by rememberSaveable {
        mutableStateOf(settingsManager.customTemplateLongChatSummaryEnabled)
    }
    var autonomousSilenceGapMinutesText by rememberSaveable {
        mutableStateOf(settingsManager.customTemplateAutonomousSilenceGapMinutes.toString())
    }
    var autonomousMaxNudgesPerDayText by rememberSaveable {
        mutableStateOf(settingsManager.customTemplateAutonomousMaxNudgesPerDay.toString())
    }
    var autonomousMaxRoundsText by rememberSaveable {
        mutableStateOf(settingsManager.customTemplateAutonomousMaxRounds.toString())
    }
    var autonomousMaxQueueText by rememberSaveable {
        mutableStateOf(settingsManager.customTemplateAutonomousMaxQueue.toString())
    }
    var autonomousMaxQueuePerUserText by rememberSaveable {
        mutableStateOf(settingsManager.customTemplateAutonomousMaxQueuePerUser.toString())
    }
    var autonomousMaxGoalsPerRunText by rememberSaveable {
        mutableStateOf(settingsManager.customTemplateAutonomousMaxGoalsPerRun.toString())
    }
    var conversationHistoryLimitText by rememberSaveable {
        mutableStateOf(settingsManager.customTemplateConversationHistoryLimit.toString())
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
    var linkedWriteSheetName by rememberSaveable {
        mutableStateOf(settingsManager.customTemplateLinkedWriteSheetName)
    }
    var salesSheetName by rememberSaveable {
        mutableStateOf(settingsManager.customTemplateSalesSheetName)
    }
    var sheetMatchFields by rememberSaveable {
        mutableStateOf(settingsManager.customTemplateSheetMatchFields)
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
    var availableSheetFolderNames by remember { mutableStateOf(listOf<String>()) }
    var availableSheetFolderCounts by remember { mutableStateOf(mapOf<String, Int>()) }
    var availableFolderSheetNames by remember { mutableStateOf(listOf<String>()) }
    var availableReferenceSheetNames by remember { mutableStateOf(listOf("All Sheets")) }
    var availableMatchFieldOptions by remember { mutableStateOf(listOf<String>()) }
    var sheetColumnSummaries by remember { mutableStateOf(listOf<SheetColumnSummary>()) }
    var linkedWriteSheetColumns by remember { mutableStateOf(listOf<String>()) }
    var availableGoogleSpreadsheetOptions by remember { mutableStateOf(listOf<GoogleSpreadsheetOption>()) }
    var availableGoogleWriteSheetNames by remember { mutableStateOf(listOf<String>()) }
    var googleWriteSheetStatus by rememberSaveable { mutableStateOf("") }
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

    fun resolveSheetColumns(sheetName: String): List<String> {
        val cleanSheetName = sheetName.trim()
        if (cleanSheetName.isBlank()) return emptyList()
        return sheetColumnSummaries
            .firstOrNull { it.sheetName.equals(cleanSheetName, ignoreCase = true) }
            ?.columns
            .orEmpty()
    }

    fun reconcileLinkedWriteSheetSelection(sheetNames: List<String>) {
        val preferred =
            linkedWriteSheetName.trim()
                .ifBlank { settingsManager.customTemplateLinkedWriteSheetName.trim() }
        val resolved =
            when {
                preferred.isBlank() -> ""
                sheetNames.any { it.equals(preferred, ignoreCase = true) } ->
                    sheetNames.first { it.equals(preferred, ignoreCase = true) }
                else -> ""
            }

        linkedWriteSheetName = resolved
        settingsManager.customTemplateLinkedWriteSheetName = resolved
        linkedWriteSheetColumns = resolveSheetColumns(resolved)
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
            val linkedFolderName =
                sheetFolderName.trim()
                    .ifBlank { settingsManager.customTemplateSheetFolderName.trim() }
                    .ifBlank { sheetManager.buildFolderName(resolvedTemplateName) }
            val setup =
                sheetManager.ensureTemplateSheetSystem(
                    templateName = resolvedTemplateName,
                    folderNameOverride = linkedFolderName,
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

    suspend fun collectManualMatchFieldFallbackColumns(): List<String> {
        val folders =
            runCatching { sheetManager.listFolderNames() }
                .getOrElse { emptyList() }
                .mapNotNull { it.trim().takeIf { name -> name.isNotBlank() } }
                .filterNot { it.equals(DEFAULT_LEGACY_AGENT_FOLDER, ignoreCase = true) }
                .distinctBy { it.lowercase() }

        if (folders.isEmpty()) return emptyList()

        val merged = mutableListOf<String>()
        folders.forEach { folder ->
            val cols = runCatching { sheetManager.listColumnNamesInFolder(folder, null) }.getOrElse { emptyList() }
            merged.addAll(cols)
        }
        return sanitizeMatchFieldOptions(merged)
    }

    suspend fun collectSheetColumnSummaries(
        folderName: String,
        sheetNames: List<String>
    ): List<SheetColumnSummary> {
        if (folderName.isBlank() || sheetNames.isEmpty()) return emptyList()

        val summaries = mutableListOf<SheetColumnSummary>()
        sheetNames.forEach { sheetName ->
            val cols =
                runCatching { sheetManager.listColumnNamesInFolder(folderName, sheetName) }
                    .getOrElse { emptyList() }
            summaries.add(
                SheetColumnSummary(
                    sheetName = sheetName,
                    columns = sanitizeMatchFieldOptions(cols)
                )
            )
        }
        return summaries
    }

    suspend fun syncReferenceSheetOptions() {
        val selectedFolder =
            sheetFolderName.trim()
                .ifBlank { settingsManager.customTemplateSheetFolderName.trim() }

        if (selectedFolder.isBlank()) {
            availableFolderSheetNames = emptyList()
            availableReferenceSheetNames = listOf("All Sheets")
            referenceSheetName = "All Sheets"
            settingsManager.customTemplateReferenceSheetName = "All Sheets"
            availableMatchFieldOptions = emptyList()
            sheetColumnSummaries = emptyList()
            linkedWriteSheetName = ""
            settingsManager.customTemplateLinkedWriteSheetName = ""
            linkedWriteSheetColumns = emptyList()
            return
        }

        sheetFolderName = selectedFolder
        settingsManager.customTemplateSheetFolderName = selectedFolder

        val sheetNames =
            runCatching { sheetManager.listSheetNamesInFolder(selectedFolder) }
                .getOrElse { emptyList() }
                .mapNotNull { it.trim().takeIf { name -> name.isNotBlank() } }
                .distinctBy { it.lowercase() }
                .sortedBy { it.lowercase() }

        availableFolderSheetNames = sheetNames
        sheetColumnSummaries = collectSheetColumnSummaries(selectedFolder, sheetNames)
        reconcileLinkedWriteSheetSelection(sheetNames)

        val fallbackReference =
            when {
                sheetNames.isEmpty() -> "All Sheets"
                referenceSheetName.equals("All Sheets", ignoreCase = true) -> "All Sheets"
                settingsManager.customTemplateReferenceSheetName.equals("All Sheets", ignoreCase = true) -> "All Sheets"
                sheetNames.any { it.equals(referenceSheetName, ignoreCase = true) } ->
                    sheetNames.first { it.equals(referenceSheetName, ignoreCase = true) }
                sheetNames.any { it.equals(settingsManager.customTemplateReferenceSheetName, ignoreCase = true) } ->
                    sheetNames.first { it.equals(settingsManager.customTemplateReferenceSheetName, ignoreCase = true) }
                else -> "All Sheets"
            }

        availableReferenceSheetNames = listOf("All Sheets") + sheetNames
        referenceSheetName = fallbackReference
        settingsManager.customTemplateReferenceSheetName = fallbackReference

        val sheetFilter =
            fallbackReference.trim().takeIf {
                it.isNotBlank() && !it.equals("All Sheets", ignoreCase = true)
            }

        val allFolderColumns =
            runCatching { sheetManager.listColumnNamesInFolder(selectedFolder, null) }
                .getOrElse { emptyList() }
        val filteredColumns =
            runCatching { sheetManager.listColumnNamesInFolder(selectedFolder, sheetFilter) }
                .getOrElse { emptyList() }

        val resolvedColumns =
            sanitizeMatchFieldOptions(
                if (sheetFilter == null) allFolderColumns else filteredColumns + allFolderColumns
            )
        val manualFallbackColumns =
            if (resolvedColumns.size > 1) {
                emptyList()
            } else {
                collectManualMatchFieldFallbackColumns()
            }
        val finalColumns = sanitizeMatchFieldOptions(resolvedColumns + manualFallbackColumns)
        availableMatchFieldOptions = finalColumns

        Log.d(
            "CustomAIAgentSheet",
            "syncReference folder=$selectedFolder sheets=${sheetNames.size} summarySheets=${sheetColumnSummaries.size} reference=$fallbackReference finalColumns=${finalColumns.size} cols=${finalColumns.joinToString(" | ").take(240)}"
        )

        val reconciledFields = reconcileMatchFields(sheetMatchFields, finalColumns)
        if (sheetMatchFields != reconciledFields) {
            sheetMatchFields = reconciledFields
            settingsManager.customTemplateSheetMatchFields = reconciledFields
        }
    }

    suspend fun syncMatchFieldOptions(
        selectedFolder: String = sheetFolderName,
        selectedReferenceSheet: String = referenceSheetName
    ) {
        val cleanFolder = selectedFolder.trim()
        if (cleanFolder.isBlank()) {
            availableMatchFieldOptions = emptyList()
            sheetColumnSummaries = emptyList()
            return
        }

        val sheetFilter =
            selectedReferenceSheet.trim().takeIf {
                it.isNotBlank() && !it.equals("All Sheets", ignoreCase = true)
            }

        val allFolderColumns =
            runCatching { sheetManager.listColumnNamesInFolder(cleanFolder, null) }
                .getOrElse { emptyList() }
        val filteredColumns =
            runCatching { sheetManager.listColumnNamesInFolder(cleanFolder, sheetFilter) }
                .getOrElse { emptyList() }

        val columns =
            sanitizeMatchFieldOptions(
                if (sheetFilter == null) allFolderColumns else filteredColumns + allFolderColumns
            )
        val manualFallbackColumns =
            if (columns.size > 1) {
                emptyList()
            } else {
                collectManualMatchFieldFallbackColumns()
            }
        val finalColumns = sanitizeMatchFieldOptions(columns + manualFallbackColumns)
        availableMatchFieldOptions = finalColumns

        Log.d(
            "CustomAIAgentSheet",
            "syncMatch folder=$cleanFolder reference=$selectedReferenceSheet finalColumns=${finalColumns.size} cols=${finalColumns.joinToString(" | ").take(240)}"
        )

        val reconciledFields = reconcileMatchFields(sheetMatchFields, finalColumns)
        if (sheetMatchFields != reconciledFields) {
            sheetMatchFields = reconciledFields
            settingsManager.customTemplateSheetMatchFields = reconciledFields
        }
    }
    suspend fun syncSheetFolderOptions() {
        val folders =
            runCatching { sheetManager.listFolderNames() }
                .getOrElse { emptyList() }
                .mapNotNull { it.trim().takeIf { name -> name.isNotBlank() } }
                .filterNot { it.equals(DEFAULT_LEGACY_AGENT_FOLDER, ignoreCase = true) }
                .distinct()
                .sortedBy { it.lowercase() }

        val folderCounts =
            folders.associateWith { folder ->
                runCatching { sheetManager.listSheetNamesInFolder(folder) }
                    .getOrElse { emptyList() }
                    .mapNotNull { it.trim().takeIf { name -> name.isNotBlank() } }
                    .distinctBy { it.lowercase() }
                    .size
            }

        availableSheetFolderNames = folders
        availableSheetFolderCounts = folderCounts

        val preferred =
            sheetFolderName.trim().ifBlank { settingsManager.customTemplateSheetFolderName.trim() }
        val resolvedFolder =
            when {
                preferred.isNotBlank() && folders.any { it.equals(preferred, ignoreCase = true) } ->
                    folders.first { it.equals(preferred, ignoreCase = true) }
                preferred.equals(DEFAULT_LEGACY_AGENT_FOLDER, ignoreCase = true) -> ""
                else -> ""
            }

        sheetFolderName = resolvedFolder
        settingsManager.customTemplateSheetFolderName = resolvedFolder
        syncReferenceSheetOptions()
    }

    suspend fun createLinkedFolderWithSheet(rawFolderName: String, rawSheetName: String) {
        val cleanFolderName = rawFolderName.trim()
        if (cleanFolderName.isBlank()) {
            snackbarHostState.showSnackbar("Folder name required")
            return
        }

        val cleanSheetName = rawSheetName.trim()
        if (cleanSheetName.isBlank()) {
            snackbarHostState.showSnackbar("First sheet name required")
            return
        }

        val cleanFields =
            writeFieldSpecs
                .map { CustomWriteFieldSpec(it.name.trim(), normalizeWriteFieldType(it.type)) }
                .filter { it.name.isNotBlank() }
                .distinctBy { it.name.lowercase() }

        persistWriteFields()

        val createdResult =
            runCatching {
                val createdFolder = sheetManager.createFolderIfMissing(cleanFolderName)
                val createdSheet =
                    sheetManager.createLinkedWriteSheet(
                        folderName = createdFolder,
                        rawSheetName = cleanSheetName,
                        fieldSpecs = cleanFields.map { it.name to it.type }
                    )
                createdFolder to createdSheet
            }
                .onFailure { snackbarHostState.showSnackbar("Folder + sheet create failed: ${it.message}") }
                .getOrNull()

        if (createdResult == null) return

        val (createdFolder, createdSheet) = createdResult
        sheetFolderName = createdFolder
        settingsManager.customTemplateSheetFolderName = createdFolder
        linkedWriteSheetName = createdSheet
        settingsManager.customTemplateLinkedWriteSheetName = createdSheet
        syncSheetFolderOptions()
        linkedWriteSheetColumns = resolveSheetColumns(createdSheet)
        snackbarHostState.showSnackbar("Folder ready with linked sheet: $createdFolder / $createdSheet")
    }

    suspend fun createLinkedFolder(rawFolderName: String) {
        val cleanName = rawFolderName.trim()
        if (cleanName.isBlank()) {
            snackbarHostState.showSnackbar("Folder name required")
            return
        }

        val createdName =
            runCatching { sheetManager.createFolderIfMissing(cleanName) }
                .onFailure { snackbarHostState.showSnackbar("Folder create failed: ${it.message}") }
                .getOrNull()
                .orEmpty()

        if (createdName.isBlank()) return

        sheetFolderName = createdName
        settingsManager.customTemplateSheetFolderName = createdName
        syncSheetFolderOptions()
        snackbarHostState.showSnackbar("Linked folder: $createdName")
    }

    suspend fun createLinkedWriteSheet(rawSheetName: String) {
        val cleanSheetName = rawSheetName.trim()
        if (cleanSheetName.isBlank()) {
            snackbarHostState.showSnackbar("Sheet name required")
            return
        }

        val cleanFolderName =
            sheetFolderName.trim().ifBlank { settingsManager.customTemplateSheetFolderName.trim() }
        if (cleanFolderName.isBlank()) {
            snackbarHostState.showSnackbar("Select or create a folder first")
            return
        }

        val cleanFields =
            writeFieldSpecs
                .map { CustomWriteFieldSpec(it.name.trim(), normalizeWriteFieldType(it.type)) }
                .filter { it.name.isNotBlank() }
                .distinctBy { it.name.lowercase() }

        persistWriteFields()

        val createdName =
            runCatching {
                sheetManager.createLinkedWriteSheet(
                    folderName = cleanFolderName,
                    rawSheetName = cleanSheetName,
                    fieldSpecs = cleanFields.map { it.name to it.type }
                )
            }
                .onFailure { snackbarHostState.showSnackbar("Sheet create failed: ${it.message}") }
                .getOrNull()
                .orEmpty()

        if (createdName.isBlank()) return

        linkedWriteSheetName = createdName
        settingsManager.customTemplateLinkedWriteSheetName = createdName
        syncReferenceSheetOptions()
        linkedWriteSheetColumns = resolveSheetColumns(createdName)
        snackbarHostState.showSnackbar("Linked sheet ready: $createdName")
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
                    linkedWriteSheetName = settingsManager.customTemplateLinkedWriteSheetName
                    autonomousSilenceGapMinutesText = settingsManager.customTemplateAutonomousSilenceGapMinutes.toString()
                    autonomousMaxNudgesPerDayText = settingsManager.customTemplateAutonomousMaxNudgesPerDay.toString()
                    autonomousMaxRoundsText = settingsManager.customTemplateAutonomousMaxRounds.toString()
                    autonomousMaxQueueText = settingsManager.customTemplateAutonomousMaxQueue.toString()
                    autonomousMaxQueuePerUserText = settingsManager.customTemplateAutonomousMaxQueuePerUser.toString()
                    autonomousMaxGoalsPerRunText = settingsManager.customTemplateAutonomousMaxGoalsPerRun.toString()
                    conversationHistoryLimitText = settingsManager.customTemplateConversationHistoryLimit.toString()
                    longChatSummaryEnabled = settingsManager.customTemplateLongChatSummaryEnabled
                    scope.launch {
                        syncSheetFolderOptions()
                        refreshGoogleConnectionSummary()
                    }
                    refreshAutonomousStatus()
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(Unit) {
        syncSheetFolderOptions()
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
                            availableCustomSheetFolderNames = availableSheetFolderNames,
                            availableCustomSheetFolderCounts = availableSheetFolderCounts,
                            referenceSheetName = referenceSheetName,
                            availableReferenceSheetNames = availableReferenceSheetNames,
                            availableLocalWriteSheetNames = availableFolderSheetNames,
                            linkedWriteSheetName = linkedWriteSheetName,
                            linkedWriteSheetColumns = linkedWriteSheetColumns,
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
                                        syncSheetFolderOptions()
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
                            onCustomSheetFolderNameChange = { selectedFolder ->
                                val resolvedFolder =
                                    availableSheetFolderNames.firstOrNull {
                                        it.equals(selectedFolder.trim(), ignoreCase = true)
                                    } ?: selectedFolder.trim()
                                sheetFolderName = resolvedFolder
                                settingsManager.customTemplateSheetFolderName = resolvedFolder
                                scope.launch { syncReferenceSheetOptions() }
                            },
                            onReferenceSheetNameChange = { selected ->
                                referenceSheetName = selected
                                settingsManager.customTemplateReferenceSheetName = selected
                            },
                            onLinkedWriteSheetNameChange = { selected ->
                                linkedWriteSheetName = selected.trim()
                                settingsManager.customTemplateLinkedWriteSheetName = linkedWriteSheetName
                                linkedWriteSheetColumns = resolveSheetColumns(linkedWriteSheetName)
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
                                        syncSheetFolderOptions()
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
                                        putExtra("openFolder", "AI Agent Data Sheet")
                                    }
                                )
                            },
                            onRefreshLocalSheetsClick = {
                                scope.launch {
                                    syncSheetFolderOptions()
                                    snackbarHostState.showSnackbar("TableSheet folders refreshed.")
                                }
                            },
                            onCreateCustomFolderClick = { folderName, firstSheetName ->
                                scope.launch { createLinkedFolderWithSheet(folderName, firstSheetName) }
                            },
                            onCreateLinkedWriteSheetClick = { sheetName ->
                                scope.launch { createLinkedWriteSheet(sheetName) }
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
                            longChatSummaryEnabled = longChatSummaryEnabled,
                            onLongChatSummaryEnabledChange = {
                                longChatSummaryEnabled = it
                                settingsManager.customTemplateLongChatSummaryEnabled = it
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
                            autonomousMaxNudgesPerDayText = autonomousMaxNudgesPerDayText,
                            onAutonomousMaxNudgesPerDayTextChange = { raw ->
                                val filtered = raw.filter { ch -> ch.isDigit() }.take(3)
                                autonomousMaxNudgesPerDayText = filtered
                                val parsed = filtered.toIntOrNull()
                                if (parsed != null) {
                                    settingsManager.customTemplateAutonomousMaxNudgesPerDay = parsed
                                }
                            },
                            autonomousMaxRoundsText = autonomousMaxRoundsText,
                            onAutonomousMaxRoundsTextChange = { raw ->
                                val filtered = raw.filter { ch -> ch.isDigit() }.take(3)
                                autonomousMaxRoundsText = filtered
                                val parsed = filtered.toIntOrNull()
                                if (parsed != null) {
                                    settingsManager.customTemplateAutonomousMaxRounds = parsed
                                }
                            },
                            autonomousMaxQueueText = autonomousMaxQueueText,
                            onAutonomousMaxQueueTextChange = { raw ->
                                val filtered = raw.filter { ch -> ch.isDigit() }.take(4)
                                autonomousMaxQueueText = filtered
                                val parsed = filtered.toIntOrNull()
                                if (parsed != null) {
                                    settingsManager.customTemplateAutonomousMaxQueue = parsed
                                }
                            },
                            autonomousMaxQueuePerUserText = autonomousMaxQueuePerUserText,
                            onAutonomousMaxQueuePerUserTextChange = { raw ->
                                val filtered = raw.filter { ch -> ch.isDigit() }.take(3)
                                autonomousMaxQueuePerUserText = filtered
                                val parsed = filtered.toIntOrNull()
                                if (parsed != null) {
                                    settingsManager.customTemplateAutonomousMaxQueuePerUser = parsed
                                }
                            },
                            autonomousMaxGoalsPerRunText = autonomousMaxGoalsPerRunText,
                            onAutonomousMaxGoalsPerRunTextChange = { raw ->
                                val filtered = raw.filter { ch -> ch.isDigit() }.take(3)
                                autonomousMaxGoalsPerRunText = filtered
                                val parsed = filtered.toIntOrNull()
                                if (parsed != null) {
                                    settingsManager.customTemplateAutonomousMaxGoalsPerRun = parsed
                                }
                            },
                            conversationHistoryLimitText = conversationHistoryLimitText,
                            onConversationHistoryLimitTextChange = { raw ->
                                val filtered = raw.filter { ch -> ch.isDigit() }.take(3)
                                conversationHistoryLimitText = filtered
                                val parsed = filtered.toIntOrNull()
                                if (parsed != null) {
                                    settingsManager.customTemplateConversationHistoryLimit = parsed
                                }
                            },
                            runtimeQueueSize = autonomousQueueSize,
                            runtimeLastHeartbeatAt = autonomousLastHeartbeatAt,
                            runtimeLastError = autonomousLastError,
                            onOpenNeedDiscovery = {
                                context.startActivity(Intent(context, NeedDiscoveryActivity::class.java))
                            }
                        )

                    CustomAIAgentTab.SHEET ->                        CustomAIAgentSheetTab(
                            linkedFolderName = sheetFolderName,
                            availableFolderNames = availableSheetFolderNames,
                            availableFolderSheetCounts = availableSheetFolderCounts,
                            availableReferenceSheetNames = availableReferenceSheetNames,
                            linkedReferenceSheetName = referenceSheetName,
                            linkedMatchFields = sheetMatchFields,
                            availableMatchFieldOptions = availableMatchFieldOptions,
                            sheetColumnSummaries = sheetColumnSummaries,
                            onFolderNameChange = { folder ->
                                sheetFolderName = folder
                                settingsManager.customTemplateSheetFolderName = folder
                                scope.launch { syncReferenceSheetOptions() }
                            },
                            onReferenceSheetNameChange = { sheet ->
                                referenceSheetName = sheet
                                settingsManager.customTemplateReferenceSheetName = sheet
                                scope.launch { syncMatchFieldOptions() }
                            },
                            onMatchFieldsChange = { raw ->
                                val single =
                                    raw.split(",")
                                        .map { it.trim() }
                                        .firstOrNull { it.isNotBlank() }
                                        .orEmpty()
                                sheetMatchFields = single
                                settingsManager.customTemplateSheetMatchFields = single
                            },
                            onAddMatchFieldFromSheet = { field ->
                                val single = field.trim()
                                sheetMatchFields = single
                                settingsManager.customTemplateSheetMatchFields = single
                            },
                            onCreateFolder = { folderName ->
                                scope.launch { createLinkedFolder(folderName) }
                            },
                            onOpenTableSheet = {
                                val folderToOpen = sheetFolderName.trim()
                                context.startActivity(
                                    Intent(context, TableSheetActivity::class.java).apply {
                                        if (folderToOpen.isNotBlank()) {
                                            putExtra("openFolder", folderToOpen)
                                        }
                                    }
                                )
                            },
                            onRefresh = {
                                scope.launch {
                                    syncSheetFolderOptions()
                                    snackbarHostState.showSnackbar("Sheet links refreshed.")
                                }
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

private fun sanitizeMatchFieldOptions(rawColumns: List<String>): List<String> {
    return rawColumns
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .distinctBy { it.lowercase() }
        .sortedBy { it.lowercase() }
}

private fun reconcileMatchFields(existingRaw: String, availableOptions: List<String>): String {
    if (availableOptions.isEmpty()) return ""

    val normalizedMap =
        availableOptions.associateBy { option -> option.trim().lowercase() }

    return existingRaw.split(",")
        .map { it.trim() }
        .firstOrNull { it.isNotBlank() }
        ?.let { token -> normalizedMap[token.lowercase()] }
        .orEmpty()
}




















