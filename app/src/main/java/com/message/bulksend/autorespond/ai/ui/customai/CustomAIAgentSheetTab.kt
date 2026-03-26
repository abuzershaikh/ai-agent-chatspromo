package com.message.bulksend.autorespond.ai.ui.customai

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun CustomAIAgentSheetTab(
    linkedFolderName: String,
    availableFolderNames: List<String>,
    availableReferenceSheetNames: List<String>,
    linkedReferenceSheetName: String,
    linkedMatchFields: String,
    availableMatchFieldOptions: List<String>,
    sheetColumnSummaries: List<SheetColumnSummary>,
    onFolderNameChange: (String) -> Unit,
    onReferenceSheetNameChange: (String) -> Unit,
    onMatchFieldsChange: (String) -> Unit,
    onAddMatchFieldFromSheet: (String) -> Unit,
    onCreateFolder: (String) -> Unit,
    onOpenTableSheet: () -> Unit,
    onRefresh: () -> Unit
) {
    var folderMenuExpanded by rememberSaveable { mutableStateOf(false) }
    var referenceMenuExpanded by rememberSaveable { mutableStateOf(false) }
    var matchFieldMenuExpanded by rememberSaveable { mutableStateOf(false) }
    var newFolderName by rememberSaveable { mutableStateOf("") }
    var activeInfoSection by rememberSaveable { mutableStateOf<String?>(null) }

    val safeFolders =
        availableFolderNames
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinctBy { it.lowercase() }
            .sorted()
    val safeReferences =
        if (availableReferenceSheetNames.isEmpty()) listOf("All Sheets")
        else availableReferenceSheetNames
    val safeMatchFields =
        availableMatchFieldOptions
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinctBy { it.lowercase() }

    val safeSheetSummaries =
        sheetColumnSummaries
            .map { summary ->
                SheetColumnSummary(
                    sheetName = summary.sheetName.trim(),
                    columns =
                        summary.columns
                            .map { it.trim() }
                            .filter { it.isNotBlank() }
                            .distinctBy { it.lowercase() }
                )
            }
            .filter { it.sheetName.isNotBlank() }
            .distinctBy { it.sheetName.lowercase() }
            .sortedBy { it.sheetName.lowercase() }

    val totalSheetCount = safeSheetSummaries.size
    val totalFieldCount = safeSheetSummaries.sumOf { it.columns.size }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                border = BorderStroke(1.dp, Color(0xFF334155))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    SectionHeaderWithInfo(
                        title = "Custom Read Source",
                        sectionId = SheetAgentInfo.READ_SOURCE,
                        onInfoClick = { activeInfoSection = it }
                    )
                    Text(
                        "Only user-added folders and sheets are selected here. The default agent sheet remains active in the background.",
                        color = Color(0xFF93C5FD),
                        fontSize = 12.sp
                    )
                    Text(
                        "Selected Folder: ${linkedFolderName.ifBlank { "Not selected (default active)" }}",
                        color = Color(0xFFCBD5E1),
                        fontSize = 12.sp
                    )
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onOpenTableSheet,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF60A5FA)),
                    border = BorderStroke(1.dp, Color(0xFF1D4ED8))
                ) {
                    Icon(Icons.Default.FolderOpen, contentDescription = null)
                    Text("Open TableSheet", modifier = Modifier.padding(start = 6.dp))
                }
                OutlinedButton(
                    onClick = onRefresh,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF34D399)),
                    border = BorderStroke(1.dp, Color(0xFF059669))
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Text("Refresh", modifier = Modifier.padding(start = 6.dp))
                }
            }
        }

        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF111827)),
                border = BorderStroke(1.dp, Color(0xFF1E293B))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    SectionHeaderWithInfo(
                        title = "Folder Selection",
                        sectionId = SheetAgentInfo.FOLDER_SELECTION,
                        onInfoClick = { activeInfoSection = it }
                    )
                    DropdownField(
                        label = "Custom Data Folder",
                        value = linkedFolderName.ifBlank { "Select custom folder" },
                        expanded = folderMenuExpanded,
                        onExpandedChange = { folderMenuExpanded = it },
                        options = safeFolders,
                        onSelect = {
                            onFolderNameChange(it)
                            folderMenuExpanded = false
                        }
                    )

                    OutlinedTextField(
                        value = newFolderName,
                        onValueChange = { newFolderName = it },
                        label = { Text("Create new folder") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFF38BDF8),
                            unfocusedBorderColor = Color(0xFF334155),
                            focusedLabelColor = Color(0xFF7DD3FC),
                            unfocusedLabelColor = Color(0xFF94A3B8)
                        )
                    )
                    Button(
                        onClick = {
                            onCreateFolder(newFolderName)
                            newFolderName = ""
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1D4ED8))
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Text("Create + Select Folder", modifier = Modifier.padding(start = 6.dp))
                    }

                    if (safeFolders.isEmpty()) {
                        Text(
                            "No custom folder exists yet. Create a folder, then add sheets inside it.",
                            color = Color(0xFFFDE68A),
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }

        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0B1324)),
                border = BorderStroke(1.dp, Color(0xFF1E3A8A))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    SectionHeaderWithInfo(
                        title = "Read Mapping",
                        sectionId = SheetAgentInfo.READ_MAPPING,
                        onInfoClick = { activeInfoSection = it }
                    )
                    Text(
                        "The AI agent reads from the selected custom folder. The default write flow stays unchanged.",
                        color = Color(0xFFBFDBFE),
                        fontSize = 12.sp
                    )

                    DropdownField(
                        label = "Reference Sheet",
                        value = linkedReferenceSheetName.ifBlank { "All Sheets" },
                        expanded = referenceMenuExpanded,
                        onExpandedChange = { referenceMenuExpanded = it },
                        options = safeReferences,
                        onSelect = {
                            onReferenceSheetNameChange(it)
                            referenceMenuExpanded = false
                        }
                    )

                    OutlinedTextField(
                        value = linkedMatchFields,
                        onValueChange = onMatchFieldsChange,
                        readOnly = true,
                        label = { Text("Match Field") },
                        supportingText = {
                            Text(
                                "Select one field from the dropdown. Example: Phone Number or Email.",
                                fontSize = 11.sp,
                                color = Color(0xFF93C5FD)
                            )
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFF38BDF8),
                            unfocusedBorderColor = Color(0xFF334155),
                            focusedLabelColor = Color(0xFF7DD3FC),
                            unfocusedLabelColor = Color(0xFF94A3B8)
                        )
                    )

                    DropdownField(
                        label = "Select Match Field (Single)",
                        value = linkedMatchFields.ifBlank { "Select field" },
                        expanded = matchFieldMenuExpanded,
                        onExpandedChange = { matchFieldMenuExpanded = it },
                        options = safeMatchFields,
                        onSelect = {
                            onAddMatchFieldFromSheet(it)
                            matchFieldMenuExpanded = false
                        }
                    )

                    OutlinedButton(
                        onClick = { onMatchFieldsChange("") },
                        enabled = linkedMatchFields.isNotBlank(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFBFDBFE)),
                        border = BorderStroke(1.dp, Color(0xFF334155))
                    ) {
                        Text("Clear Match Field")
                    }

                    if (linkedFolderName.isBlank()) {
                        Text(
                            "No custom folder is selected. The agent currently uses the default sheet flow.",
                            color = Color(0xFFFDE68A),
                            fontSize = 11.sp
                        )
                    } else if (safeMatchFields.isEmpty()) {
                        Text(
                            "No columns were found in this folder or sheet. Add columns, then tap Refresh.",
                            color = Color(0xFFFDE68A),
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }

        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF101B2F)),
                border = BorderStroke(1.dp, Color(0xFF1E3A8A))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SectionHeaderWithInfo(
                        title = "Folder Sheet Fields",
                        sectionId = SheetAgentInfo.FOLDER_FIELDS,
                        onInfoClick = { activeInfoSection = it }
                    )
                    Text(
                        "Sheets: $totalSheetCount | Total Fields: $totalFieldCount",
                        color = Color(0xFFBFDBFE),
                        fontSize = 12.sp
                    )

                    if (safeSheetSummaries.isEmpty()) {
                        Text(
                            "No sheets or columns were fetched yet for this folder. Tap Refresh or add columns in TableSheet.",
                            color = Color(0xFFFDE68A),
                            fontSize = 11.sp
                        )
                    } else {
                        safeSheetSummaries.forEach { summary ->
                            Text(
                                "${summary.sheetName} (${summary.columns.size} fields)",
                                color = Color(0xFFE2E8F0),
                                fontSize = 12.sp
                            )
                            Text(
                                summary.columns.joinToString(", ").ifBlank { "No fields" },
                                color = Color(0xFF93C5FD),
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }
        }
    }

    val infoContent = activeInfoSection?.let { SheetAgentInfo.get(it) }
    if (infoContent != null) {
        AlertDialog(
            onDismissRequest = { activeInfoSection = null },
            title = {
                Text(
                    text = infoContent.title,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = infoContent.description,
                        color = Color(0xFFBFDBFE),
                        fontSize = 13.sp
                    )
                    infoContent.points.forEach { point ->
                        Text(
                            text = "- $point",
                            color = Color(0xFFCBD5E1),
                            fontSize = 12.sp
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { activeInfoSection = null }) {
                    Text("Got it")
                }
            },
            containerColor = Color(0xFF0F172A)
        )
    }
}

@Composable
private fun SectionHeaderWithInfo(
    title: String,
    sectionId: String,
    onInfoClick: (String) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, color = Color.White, fontWeight = FontWeight.Medium)
        IconButton(onClick = { onInfoClick(sectionId) }) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = "Section info",
                tint = Color(0xFF93C5FD)
            )
        }
    }
}

@Composable
private fun DropdownField(
    label: String,
    value: String,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    options: List<String>,
    onSelect: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, color = Color(0xFF93C5FD), fontSize = 12.sp)
        Box(modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = { onExpandedChange(!expanded) },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFBFDBFE)),
                border = BorderStroke(1.dp, Color(0xFF334155))
            ) {
                Text(value)
                Icon(
                    imageVector = Icons.Default.TableChart,
                    contentDescription = null,
                    tint = Color(0xFF60A5FA),
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { onExpandedChange(false) },
                modifier = Modifier.fillMaxWidth()
            ) {
                if (options.isEmpty()) {
                    DropdownMenuItem(
                        text = { Text("No options available") },
                        onClick = { onExpandedChange(false) }
                    )
                } else {
                    options.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option) },
                            onClick = { onSelect(option) }
                        )
                    }
                }
            }
        }
    }
}
