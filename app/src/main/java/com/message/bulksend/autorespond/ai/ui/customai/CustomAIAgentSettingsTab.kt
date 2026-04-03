package com.message.bulksend.autorespond.ai.ui.customai

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Dark-theme color palette shared across this screen
private val BgDeep       = Color(0xFF0A0F1E)
private val BgCard       = Color(0xFF111827)
private val BgCardAlt    = Color(0xFF0F172A)
private val BorderSubtle = Color(0xFF1E293B)
private val BorderGlow   = Color(0xFF334155)
private val TextPrimary  = Color.White
private val TextSecondary= Color(0xFF94A3B8)
private val TextHint     = Color(0xFF64748B)
private val AccentBlue   = Color(0xFF38BDF8)
private val AccentPurple = Color(0xFF818CF8)
private val AccentGreen  = Color(0xFF34D399)
private val AccentAmber  = Color(0xFFFBBF24)
private val AccentRed    = Color(0xFFF87171)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CustomAIAgentSettingsTab(
    customEnabled: Boolean,
    setupInProgress: Boolean,
    customSheetFolderName: String,
    availableCustomSheetFolderNames: List<String>,
    availableCustomSheetFolderCounts: Map<String, Int>,
    referenceSheetName: String,
    availableReferenceSheetNames: List<String>,
    availableLocalWriteSheetNames: List<String>,
    linkedWriteSheetName: String,
    linkedWriteSheetColumns: List<String>,
    writeStorageMode: String,
    writeFields: List<CustomWriteFieldSpec>,
    writeFieldTypes: List<String>,
    repeatCounterEnabled: Boolean,
    repeatCounterLimitText: String,
    repeatCounterOwnerNotifyEnabled: Boolean,
    repeatCounterOwnerPhone: String,
    onCustomEnabledChange: (Boolean) -> Unit,
    onRepeatCounterEnabledChange: (Boolean) -> Unit,
    onRepeatCounterLimitTextChange: (String) -> Unit,
    onRepeatCounterOwnerNotifyEnabledChange: (Boolean) -> Unit,
    onRepeatCounterOwnerPhoneChange: (String) -> Unit,
    onCustomSheetFolderNameChange: (String) -> Unit,
    onReferenceSheetNameChange: (String) -> Unit,
    onLinkedWriteSheetNameChange: (String) -> Unit,
    onWriteStorageModeChange: (String) -> Unit,
    onAddWriteField: () -> Unit,
    onWriteFieldNameChange: (Int, String) -> Unit,
    onWriteFieldTypeChange: (Int, String) -> Unit,
    onRemoveWriteField: (Int) -> Unit,
    availableGoogleSpreadsheetOptions: List<GoogleSpreadsheetOption>,
    selectedGoogleSpreadsheetRef: String,
    onGoogleSpreadsheetRefChange: (String) -> Unit,
    onRefreshGoogleSheetsClick: () -> Unit,
    availableGoogleWriteSheetNames: List<String>,
    selectedGoogleWriteSheetName: String,
    googleWriteSheetStatus: String,
    onGoogleWriteSheetNameChange: (String) -> Unit,
    connectedGoogleSheetName: String,
    connectedGoogleSheetId: String,
    googleSheetIdInput: String,
    onGoogleSheetIdInputChange: (String) -> Unit,
    onSetupClick: () -> Unit,
    onOpenAIDataFolderClick: () -> Unit,
    onRefreshLocalSheetsClick: () -> Unit,
    onCreateCustomFolderClick: (String, String) -> Unit,
    onCreateLinkedWriteSheetClick: (String) -> Unit,
    onOpenCustomFolderClick: () -> Unit,
    onOpenGoogleSetupClick: () -> Unit
) {
    val tableMode = writeStorageMode == "TABLE_SHEET"
    var folderMenuExpanded by remember { mutableStateOf(false) }
    var spreadsheetSourceMenuExpanded by remember { mutableStateOf(false) }
    var writeTargetSheetMenuExpanded by remember { mutableStateOf(false) }
    var newFolderName by remember { mutableStateOf("") }
    var newFolderSheetName by remember { mutableStateOf("") }
    var newLinkedSheetName by remember { mutableStateOf("") }
    val effectiveConnectedGoogleSheetName = connectedGoogleSheetName.ifBlank { "No Google file connected" }
    val effectiveConnectedGoogleSheetId = connectedGoogleSheetId.ifBlank { "Not connected" }
    val effectiveSelectedTabName = selectedGoogleWriteSheetName.ifBlank { "No sheet tab selected" }
    val safeWriteSheetList =
        if (availableGoogleWriteSheetNames.isEmpty()) listOf("Select sheet from connection")
        else availableGoogleWriteSheetNames
    val safeFolderList =
        availableCustomSheetFolderNames
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinctBy { it.lowercase() }
            .sortedBy { it.lowercase() }

    fun resolveFolderSheetCount(folderName: String): Int {
        val cleanName = folderName.trim()
        if (cleanName.isBlank()) return 0
        return availableCustomSheetFolderCounts.entries.firstOrNull {
            it.key.equals(cleanName, ignoreCase = true)
        }?.value ?: 0
    }

    fun formatFolderLabel(folderName: String): String {
        val count = resolveFolderSheetCount(folderName)
        val suffix = if (count == 1) "1 sheet" else "$count sheets"
        return "$folderName ($suffix)"
    }

    val selectedFolderSheetCount = resolveFolderSheetCount(customSheetFolderName)
    val effectiveFolderLabel =
        customSheetFolderName.trim()
            .takeIf { it.isNotBlank() }
            ?.let(::formatFolderLabel)
            ?: "Select existing folder"

    val safeLocalWriteSheets =
        availableLocalWriteSheetNames
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinctBy { it.lowercase() }
            .sortedBy { it.lowercase() }
    val localWriteSheetOptions = listOf("Use default AI write sheet") + safeLocalWriteSheets
    val effectiveLinkedWriteSheetName =
        linkedWriteSheetName.ifBlank { "Use default AI write sheet" }
    val selectedSpreadsheetOption =
        availableGoogleSpreadsheetOptions.find {
            it.ref.equals(selectedGoogleSpreadsheetRef.trim(), ignoreCase = true)
        }
    val effectiveSpreadsheetSourceTitle =
        selectedSpreadsheetOption?.title
            ?: selectedGoogleSpreadsheetRef.trim().takeIf { it.isNotBlank() }
            ?: "Select spreadsheet source"
    val effectiveGoogleWriteSheetName =
        selectedGoogleWriteSheetName.ifBlank { "Select target sheet" }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDeep),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {

        // Header
        item { SettingsHeader() }

        // Activate Toggle
        item {
            SettingsSectionCard(
                title = "Template Status",
                icon = Icons.Default.AutoAwesome,
                accentColor = AccentPurple
            ) {
                ToolToggleRow(
                    icon = Icons.Default.AutoAwesome,
                    title = "Activate Custom Template",
                    subtitle = "Use this template as primary AI mode",
                    checked = customEnabled,
                    onCheckedChange = onCustomEnabledChange,
                    accentColor = AccentPurple
                )
            }
        }

        // Sheet Settings
        item {
            SettingsSectionCard(
                title = "Sheet Settings",
                icon = Icons.Default.Sync,
                accentColor = AccentBlue
            ) {
                Text(
                    "Read source sheet is configured in the Sheet tab.",
                    color = TextSecondary,
                    fontSize = 12.sp,
                    lineHeight = 17.sp
                )

                // Write destination chips
                Text(
                    "Write destination",
                    color = AccentBlue,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StyledFilterChip(
                        selected = tableMode,
                        label = "Table Sheet",
                        accentColor = AccentBlue,
                        onClick = { onWriteStorageModeChange("TABLE_SHEET") }
                    )
                    StyledFilterChip(
                        selected = !tableMode,
                        label = "Google Sheet",
                        accentColor = AccentGreen,
                        onClick = { onWriteStorageModeChange("GOOGLE_SHEET") }
                    )
                }

                // Action cards
                if (tableMode) {
                    Text(
                        "Linked TableSheet Folder",
                        color = AccentBlue,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(AccentBlue.copy(alpha = 0.10f))
                            .border(1.dp, AccentBlue.copy(alpha = 0.22f), RoundedCornerShape(14.dp))
                            .padding(12.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                "Step by step",
                                color = AccentBlue,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                "1. Existing folder select karo, ya neeche new folder aur first sheet ka name bharo.",
                                color = TextSecondary,
                                fontSize = 11.sp,
                                lineHeight = 16.sp
                            )
                            Text(
                                "2. Create Folder + Sheet dabate hi folder select ho jayega aur pehli linked sheet saath me ban jayegi.",
                                color = TextSecondary,
                                fontSize = 11.sp,
                                lineHeight = 16.sp
                            )
                            Text(
                                "3. Dropdown me har folder ke saath kitni sheets hain woh bhi dikh raha hai.",
                                color = TextSecondary,
                                fontSize = 11.sp,
                                lineHeight = 16.sp
                            )
                            Text(
                                "4. Write Fields same rakho, phir Create / Refresh Sheet Structure se baaki AI sheets sync kar lo.",
                                color = TextSecondary,
                                fontSize = 11.sp,
                                lineHeight = 16.sp
                            )
                        }
                    }
                    Box {
                        SettingsTextField(
                            value = effectiveFolderLabel,
                            onValueChange = {},
                            label = "Folder",
                            readOnly = true,
                            accentColor = AccentBlue,
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Folder,
                                    contentDescription = null,
                                    tint = AccentBlue
                                )
                            },
                            trailingIcon = {
                                Icon(
                                    Icons.Default.ArrowDropDown,
                                    contentDescription = null,
                                    tint = AccentBlue
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = safeFolderList.isNotEmpty()) {
                                    folderMenuExpanded = true
                                }
                        )
                        DropdownMenu(
                            expanded = folderMenuExpanded,
                            onDismissRequest = { folderMenuExpanded = false },
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF1E293B))
                        ) {
                            safeFolderList.forEach { folder ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            formatFolderLabel(folder),
                                            color = TextPrimary,
                                            fontSize = 14.sp
                                        )
                                    },
                                    onClick = {
                                        onCustomSheetFolderNameChange(folder)
                                        folderMenuExpanded = false
                                    },
                                    modifier = Modifier.background(Color(0xFF1E293B))
                                )
                            }
                        }
                    }
                    SettingsTextField(
                        value = newFolderName,
                        onValueChange = { newFolderName = it },
                        label = "Create new folder",
                        accentColor = AccentBlue,
                        leadingIcon = {
                            Icon(
                                Icons.Default.CreateNewFolder,
                                contentDescription = null,
                                tint = AccentBlue
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    SettingsTextField(
                        value = newFolderSheetName,
                        onValueChange = { newFolderSheetName = it },
                        label = "First sheet name",
                        accentColor = AccentGreen,
                        leadingIcon = {
                            Icon(
                                Icons.Default.TableChart,
                                contentDescription = null,
                                tint = AccentGreen
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        "New folder banate hi ye first linked write sheet bhi create ho jayegi, isliye user ko alag se sheet banane ki zarurat nahi padegi.",
                        color = TextSecondary,
                        fontSize = 11.sp,
                        lineHeight = 16.sp
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = {
                                onCreateCustomFolderClick(newFolderName, newFolderSheetName)
                                newFolderName = ""
                                newFolderSheetName = ""
                            },
                            enabled =
                                !setupInProgress &&
                                    newFolderName.isNotBlank() &&
                                    newFolderSheetName.isNotBlank(),
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AccentBlue.copy(0.2f),
                                contentColor = AccentBlue
                            )
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Create Folder + Sheet", fontWeight = FontWeight.SemiBold)
                        }
                        OutlinedButton(
                            onClick = onRefreshLocalSheetsClick,
                            enabled = !setupInProgress,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentGreen),
                            border = BorderStroke(1.dp, AccentGreen.copy(0.7f))
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Refresh", fontWeight = FontWeight.SemiBold)
                        }
                    }
                    Text(
                        if (customSheetFolderName.isBlank()) {
                            "Existing folder select kar sakte ho, ya naya folder banate waqt first sheet bhi saath me create hogi."
                        } else {
                            "Selected folder me $selectedFolderSheetCount ${if (selectedFolderSheetCount == 1) "sheet" else "sheets"} hain. Isi folder me existing sheet select kar sakte ho ya neeche nayi linked sheet add kar sakte ho."
                        },
                        color = TextSecondary,
                        fontSize = 11.sp,
                        lineHeight = 16.sp
                    )

                    Text(
                        "Linked User Write Sheet (Optional)",
                        color = AccentGreen,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Box {
                        SettingsTextField(
                            value = effectiveLinkedWriteSheetName,
                            onValueChange = {},
                            label = "Select existing TableSheet",
                            readOnly = true,
                            accentColor = AccentGreen,
                            trailingIcon = {
                                Icon(
                                    Icons.Default.ArrowDropDown,
                                    contentDescription = null,
                                    tint = AccentGreen
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = customSheetFolderName.isNotBlank()) {
                                    writeTargetSheetMenuExpanded = true
                                }
                        )
                        DropdownMenu(
                            expanded = writeTargetSheetMenuExpanded,
                            onDismissRequest = { writeTargetSheetMenuExpanded = false },
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF1E293B))
                        ) {
                            localWriteSheetOptions.forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(option, color = TextPrimary, fontSize = 14.sp) },
                                    onClick = {
                                        onLinkedWriteSheetNameChange(
                                            option.takeIf { it != "Use default AI write sheet" }.orEmpty()
                                        )
                                        writeTargetSheetMenuExpanded = false
                                    },
                                    modifier = Modifier.background(Color(0xFF1E293B))
                                )
                            }
                        }
                    }

                    if (customSheetFolderName.isBlank()) {
                        Text(
                            "Folder select/create karne ke baad hi linked user write sheet create ya connect hogi.",
                            color = AccentAmber,
                            fontSize = 11.sp,
                            lineHeight = 16.sp
                        )
                    } else {
                        if (safeLocalWriteSheets.isEmpty()) {
                            Text(
                                "Abhi is folder me koi linked user sheet nahi mili. Neeche new sheet create + link kar sakte ho, ya TableSheet me manually bana kar Refresh dabao.",
                                color = AccentAmber,
                                fontSize = 11.sp,
                                lineHeight = 16.sp
                            )
                        }
                        SettingsTextField(
                            value = newLinkedSheetName,
                            onValueChange = { newLinkedSheetName = it },
                            label = "Create new linked write sheet",
                            accentColor = AccentGreen,
                            leadingIcon = {
                                Icon(
                                    Icons.Default.TableChart,
                                    contentDescription = null,
                                    tint = AccentGreen
                                )
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            "Quick create current Write Fields ke naam se blank linked sheet banata hai. Agar custom columns manually set karne hain to Open Folder / Create Sheet use karo.",
                            color = TextSecondary,
                            fontSize = 11.sp,
                            lineHeight = 16.sp
                        )
                        Button(
                            onClick = {
                                onCreateLinkedWriteSheetClick(newLinkedSheetName)
                                newLinkedSheetName = ""
                            },
                            enabled = !setupInProgress && customSheetFolderName.isNotBlank() && newLinkedSheetName.isNotBlank(),
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AccentGreen.copy(0.2f),
                                contentColor = AccentGreen
                            ),
                            contentPadding = PaddingValues(vertical = 14.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Create + Link Sheet", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                    }

                    if (linkedWriteSheetName.isNotBlank()) {
                        Text(
                            "Connected sheet columns: ${linkedWriteSheetColumns.joinToString(", ").ifBlank { "No columns found" }}",
                            color = TextSecondary,
                            fontSize = 11.sp,
                            lineHeight = 16.sp
                        )
                        Text(
                            "Write Fields section me same fields add karo. AI connected sheet me sirf mapped/configured fields likhega; default AI sheet logging unchanged rahegi.",
                            color = AccentGreen,
                            fontSize = 11.sp,
                            lineHeight = 16.sp
                        )
                    } else {
                        Text(
                            "Agar user-created sheet connect nahi karte ho to existing default custom write sheet behavior waise hi rahega.",
                            color = TextSecondary,
                            fontSize = 11.sp,
                            lineHeight = 16.sp
                        )
                    }

                    ToolSetupActionCard(
                        icon = Icons.Default.Sync,
                        title = "Create / Refresh Sheet Structure",
                        subtitle = "Build local custom write/sales sheets",
                        enabled = !setupInProgress,
                        accentColor = AccentBlue,
                        onClick = onSetupClick
                    )
                } else {
                    Text(
                        "Google Sheet Connection",
                        color = AccentGreen,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(AccentGreen.copy(0.12f))
                                .padding(10.dp)
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.TableChart,
                                    contentDescription = null,
                                    tint = AccentGreen,
                                    modifier = Modifier.size(18.dp)
                                )
                                Column {
                                    Text(
                                        text = "Google File Name",
                                        color = TextSecondary,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1
                                    )
                                    Text(
                                        text = effectiveConnectedGoogleSheetName,
                                        color = TextPrimary,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1
                                    )
                                    Text(
                                        text = "ID: $effectiveConnectedGoogleSheetId",
                                        color = TextSecondary,
                                        fontSize = 10.sp,
                                        maxLines = 1
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "Sheet Tab Name",
                                        color = TextSecondary,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1
                                    )
                                    Text(
                                        text = effectiveSelectedTabName,
                                        color = TextPrimary,
                                        fontSize = 13.sp,
                                        maxLines = 1
                                    )
                                }
                            }
                        }

                        OutlinedButton(
                            onClick = onOpenGoogleSetupClick,
                            colors =
                                ButtonDefaults.buttonColors(
                                    containerColor = AccentGreen.copy(0.15f),
                                    contentColor = AccentGreen
                                ),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)
                        ) {
                            Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Setup")
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        "Google File Name",
                        color = AccentBlue,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Box {
                        SettingsTextField(
                            value = effectiveSpreadsheetSourceTitle,
                            onValueChange = {},
                            label = "Select Google file",
                            readOnly = true,
                            accentColor = AccentGreen,
                            trailingIcon = {
                                Icon(
                                    Icons.Default.ArrowDropDown,
                                    contentDescription = null,
                                    tint = AccentGreen
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { spreadsheetSourceMenuExpanded = true }
                        )
                        DropdownMenu(
                            expanded = spreadsheetSourceMenuExpanded,
                            onDismissRequest = { spreadsheetSourceMenuExpanded = false },
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF1E293B))
                        ) {
                            if (availableGoogleSpreadsheetOptions.isEmpty()) {
                                DropdownMenuItem(
                                    text = { Text("No spreadsheets found", color = TextSecondary, fontSize = 14.sp) },
                                    onClick = { spreadsheetSourceMenuExpanded = false },
                                    modifier = Modifier.background(Color(0xFF1E293B))
                                )
                            } else {
                                availableGoogleSpreadsheetOptions.forEach { option ->
                                    DropdownMenuItem(
                                        text = { Text(option.title, color = TextPrimary, fontSize = 14.sp) },
                                        onClick = {
                                            onGoogleSpreadsheetRefChange(option.ref)
                                            spreadsheetSourceMenuExpanded = false
                                        },
                                        modifier = Modifier.background(Color(0xFF1E293B))
                                    )
                                }
                            }
                        }
                    }

                    if (availableGoogleSpreadsheetOptions.isNotEmpty()) {
                        Text(
                            "Available Google files",
                            color = TextSecondary,
                            fontSize = 11.sp,
                            lineHeight = 14.sp
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            availableGoogleSpreadsheetOptions.forEach { option ->
                                val isSelected =
                                    option.ref.equals(selectedGoogleSpreadsheetRef.trim(), ignoreCase = true)
                                Column(
                                    modifier = Modifier
                                        .width(180.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(
                                            if (isSelected) AccentGreen.copy(0.18f) else BgCardAlt
                                        )
                                        .border(
                                            1.dp,
                                            if (isSelected) AccentGreen else BorderGlow,
                                            RoundedCornerShape(12.dp)
                                        )
                                        .clickable { onGoogleSpreadsheetRefChange(option.ref) }
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = option.title,
                                        color = if (isSelected) TextPrimary else Color(0xFFE2E8F0),
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1
                                    )
                                    Text(
                                        text =
                                            if (option.ref.length > 22) {
                                                option.ref.take(22) + "..."
                                            } else {
                                                option.ref
                                            },
                                        color = TextHint,
                                        fontSize = 10.sp,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }

                    OutlinedButton(
                        onClick = onRefreshGoogleSheetsClick,
                        colors =
                            ButtonDefaults.buttonColors(
                                containerColor = AccentBlue.copy(0.15f),
                                contentColor = AccentBlue
                            ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Fetch File Tabs")
                    }

                    Text(
                        "Sheet Tab Name",
                        color = AccentBlue,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )

                    Box {
                        SettingsTextField(
                            value = effectiveGoogleWriteSheetName,
                            onValueChange = {},
                            label = "Select sheet tab",
                            readOnly = true,
                            accentColor = AccentGreen,
                            trailingIcon = {
                                Icon(
                                    Icons.Default.ArrowDropDown,
                                    contentDescription = null,
                                    tint = AccentGreen
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { writeTargetSheetMenuExpanded = true }
                        )
                        DropdownMenu(
                            expanded = writeTargetSheetMenuExpanded,
                            onDismissRequest = { writeTargetSheetMenuExpanded = false },
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF1E293B))
                        ) {
                            safeWriteSheetList.forEach { sheetName ->
                                DropdownMenuItem(
                                    text = { Text(sheetName, color = TextPrimary, fontSize = 14.sp) },
                                    onClick = {
                                        onGoogleWriteSheetNameChange(sheetName.takeIf { it != "Select sheet from connection" }.orEmpty())
                                        writeTargetSheetMenuExpanded = false
                                    },
                                    modifier = Modifier.background(Color(0xFF1E293B))
                                )
                            }
                        }
                    }

                    if (availableGoogleWriteSheetNames.isEmpty()) {
                        Text(
                            "Connect Google file first or add file ID to load sheet tabs.",
                            color = TextSecondary,
                            fontSize = 11.sp,
                            lineHeight = 14.sp
                        )
                    } else {
                        Text(
                            "Select exactly which tab inside the Google file should receive new customer records.",
                            color = TextSecondary,
                            fontSize = 11.sp,
                            lineHeight = 14.sp
                        )
                    }
                    if (googleWriteSheetStatus.isNotBlank()) {
                        Text(
                            googleWriteSheetStatus,
                            color = AccentRed,
                            fontSize = 11.sp,
                            lineHeight = 14.sp
                        )
                    }

                    SettingsTextField(
                        value = googleSheetIdInput,
                        onValueChange = onGoogleSheetIdInputChange,
                        label = "Google File ID (Optional)",
                        accentColor = AccentGreen,
                        leadingIcon = {
                            Icon(
                                Icons.Default.Link,
                                contentDescription = null,
                                tint = AccentGreen
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text(
                        "Optional: paste Google file ID here. If empty, the connected Google file is used automatically.",
                        color = TextSecondary,
                        fontSize = 11.sp,
                        lineHeight = 14.sp
                    )
                }

                ToolSetupActionCard(
                    icon = Icons.Default.Folder,
                    title = "Open AI Agent Data Sheet",
                    subtitle = "Add/Edit data for read context",
                    accentColor = AccentAmber,
                    onClick = onOpenAIDataFolderClick
                )
                if (tableMode) {
                    ToolSetupActionCard(
                        icon = Icons.Default.Folder,
                        title = "Open Folder / Create Sheet",
                        subtitle =
                            customSheetFolderName.ifBlank {
                                "Select or create a folder above, then open it in TableSheet"
                            },
                        accentColor = AccentAmber,
                        onClick = onOpenCustomFolderClick
                    )
                }
            }
        }

        // Write Fields
        item {
            SettingsSectionCard(
                title = "Write Fields",
                icon = Icons.Outlined.Edit,
                accentColor = AccentGreen
            ) {
                Text(
                    if (tableMode)
                        "These fields are added into local custom write sheet and used for write command."
                    else
                        "These fields are passed as column keys for Google sheet writes.",
                    color = TextSecondary,
                    fontSize = 12.sp,
                    lineHeight = 17.sp
                )

                if (writeFields.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(AccentRed.copy(0.1f))
                            .padding(12.dp)
                    ) {
                        Text(
                            "Add at least one field.",
                            color = AccentRed,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                writeFields.forEachIndexed { index, field ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SettingsTextField(
                            value = field.name,
                            onValueChange = { onWriteFieldNameChange(index, it) },
                            label = "Field Name",
                            accentColor = AccentGreen,
                            modifier = Modifier.weight(1f)
                        )

                        var typeMenuExpanded by remember { mutableStateOf(false) }
                        ExposedDropdownMenuBox(
                            expanded = typeMenuExpanded,
                            onExpandedChange = { typeMenuExpanded = it },
                            modifier = Modifier.weight(0.9f)
                        ) {
                            SettingsTextField(
                                value = field.type,
                                onValueChange = {},
                                label = "Type",
                                readOnly = true,
                                accentColor = AccentPurple,
                                trailingIcon = {
                                    ExposedDropdownMenuDefaults.TrailingIcon(typeMenuExpanded)
                                },
                                modifier = Modifier.menuAnchor()
                            )
                            ExposedDropdownMenu(
                                expanded = typeMenuExpanded,
                                onDismissRequest = { typeMenuExpanded = false },
                                modifier = Modifier.background(Color(0xFF1E293B))
                            ) {
                                writeFieldTypes.forEach { fieldType ->
                                    DropdownMenuItem(
                                        text = { Text(fieldType, color = TextPrimary, fontSize = 13.sp) },
                                        onClick = {
                                            onWriteFieldTypeChange(index, fieldType)
                                            typeMenuExpanded = false
                                        },
                                        modifier = Modifier.background(Color(0xFF1E293B))
                                    )
                                }
                            }
                        }

                        if (writeFields.size > 1) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(AccentRed.copy(0.15f))
                                    .clickable { onRemoveWriteField(index) },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Remove",
                                    tint = AccentRed,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        } else {
                            Spacer(modifier = Modifier.size(40.dp))
                        }
                    }
                }

                Button(
                    onClick = onAddWriteField,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AccentGreen.copy(0.18f),
                        contentColor = AccentGreen
                    ),
                    contentPadding = PaddingValues(vertical = 14.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Add Field", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }
        }

        // Step Repeat Counter
        item {
            SettingsSectionCard(
                title = "Step Repeat Counter",
                icon = Icons.Default.Repeat,
                accentColor = AccentAmber
            ) {
                Text(
                    "Enable loop guard for step-flow. When same step repeats and limit is reached, owner alert can be sent.",
                    color = TextSecondary,
                    fontSize = 12.sp,
                    lineHeight = 17.sp
                )

                ToolToggleRow(
                    icon = Icons.Default.Repeat,
                    title = "Enable Repeat Limit",
                    subtitle = "No default limit. Set your own count.",
                    checked = repeatCounterEnabled,
                    onCheckedChange = onRepeatCounterEnabledChange,
                    accentColor = AccentAmber
                )

                if (repeatCounterEnabled) {
                    SettingsTextField(
                        value = repeatCounterLimitText,
                        onValueChange = { raw ->
                            onRepeatCounterLimitTextChange(raw.filter { it.isDigit() })
                        },
                        label = "Repeat limit count",
                        accentColor = AccentAmber,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )

                    ToolToggleRow(
                        icon = Icons.Default.Notifications,
                        title = "Notify Owner on Limit Hit",
                        subtitle = "Send app notification and owner WhatsApp alert.",
                        checked = repeatCounterOwnerNotifyEnabled,
                        onCheckedChange = onRepeatCounterOwnerNotifyEnabledChange,
                        accentColor = AccentRed
                    )

                    if (repeatCounterOwnerNotifyEnabled) {
                        SettingsTextField(
                            value = repeatCounterOwnerPhone,
                            onValueChange = onRepeatCounterOwnerPhoneChange,
                            label = "Owner alert phone (optional)",
                            accentColor = AccentBlue,
                            leadingIcon = {
                                Icon(Icons.Default.Phone, null, tint = AccentBlue, modifier = Modifier.size(18.dp))
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            "If empty, Reverse AI Owner Assistant number will be used.",
                            color = TextHint,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(24.dp)) }
    }
}

// Header
@Composable
private fun SettingsHeader() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.linearGradient(listOf(Color(0xFF0F172A), Color(0xFF1E1B4B).copy(0.7f)))
            )
            .border(1.dp, AccentPurple.copy(0.35f), RoundedCornerShape(20.dp))
            .padding(20.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(AccentPurple.copy(0.18f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Settings,
                    contentDescription = null,
                    tint = AccentPurple,
                    modifier = Modifier.size(30.dp)
                )
            }
            Column {
                Text(
                    "Agent Settings",
                    color = TextPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Configure sheets, fields, and behaviour",
                    color = TextSecondary,
                    fontSize = 12.sp
                )
            }
        }
    }
}

// Section Card wrapper
@Composable
private fun SettingsSectionCard(
    title: String,
    icon: ImageVector,
    accentColor: Color,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(BgCard)
            .border(1.dp, BorderSubtle, RoundedCornerShape(20.dp))
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Section title row
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(accentColor.copy(0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = accentColor, modifier = Modifier.size(18.dp))
            }
            Text(title, color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
        HorizontalDivider(color = BorderSubtle, thickness = 1.dp)
        content()
    }
}

// Styled OutlinedTextField
@Composable
private fun SettingsTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    accentColor: Color,
    modifier: Modifier = Modifier,
    readOnly: Boolean = false,
    singleLine: Boolean = true,
    minLines: Int = 1,
    maxLines: Int = 1,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, color = accentColor.copy(0.85f), fontSize = 12.sp) },
        readOnly = readOnly,
        singleLine = singleLine,
        minLines = minLines,
        maxLines = maxLines,
        keyboardOptions = keyboardOptions,
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor          = TextPrimary,
            unfocusedTextColor        = Color(0xFFE2E8F0),
            focusedBorderColor        = accentColor,
            unfocusedBorderColor      = BorderGlow,
            focusedContainerColor     = Color(0xFF1E293B),
            unfocusedContainerColor   = BgCardAlt,
            cursorColor               = accentColor,
            focusedLabelColor         = accentColor,
            unfocusedLabelColor       = TextSecondary,
            focusedPlaceholderColor   = TextHint,
            unfocusedPlaceholderColor = TextHint
        ),
        textStyle = androidx.compose.ui.text.TextStyle(
            fontSize = 14.sp,
            color = TextPrimary
        )
    )
}

// Styled FilterChip
@Composable
private fun StyledFilterChip(
    selected: Boolean,
    label: String,
    accentColor: Color,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) accentColor.copy(0.2f) else BgCardAlt)
            .border(
                1.dp,
                if (selected) accentColor else BorderGlow,
                RoundedCornerShape(10.dp)
            )
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            label,
            color = if (selected) accentColor else TextSecondary,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}


