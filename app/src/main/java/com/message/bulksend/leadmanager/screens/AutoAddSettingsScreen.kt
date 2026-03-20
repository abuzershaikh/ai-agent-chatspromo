package com.message.bulksend.leadmanager.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import com.message.bulksend.leadmanager.LeadManager
import com.message.bulksend.leadmanager.database.entities.AutoAddKeywordRuleEntity
import com.message.bulksend.leadmanager.database.entities.AutoAddSettingsEntity
import com.message.bulksend.leadmanager.database.entities.KeywordMatchType
import java.util.UUID
import androidx.activity.compose.BackHandler

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutoAddSettingsScreen(
    leadManager: LeadManager,
    onBack: () -> Unit
) {
    // Initialize settings - create default if not exists
    var settings by remember { 
        val existingSettings = leadManager.getAutoAddSettings()
        if (existingSettings == null) {
            val defaultSettings = AutoAddSettingsEntity(
                id = "default",
                isAutoAddEnabled = false,
                autoAddAllMessages = false,
                keywordBasedAdd = true,
                defaultSource = "WhatsApp",
                defaultCategory = "AutoRespond",
                excludeExistingContacts = true,
                notifyOnNewLead = true,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
            leadManager.saveAutoAddSettings(defaultSettings)
            mutableStateOf(defaultSettings)
        } else {
            mutableStateOf(existingSettings)
        }
    }
    var keywordRules by remember { mutableStateOf(leadManager.getAllAutoAddKeywordRules()) }
    var showAddRuleDialog by remember { mutableStateOf(false) }
    var editingRule by remember { mutableStateOf<AutoAddKeywordRuleEntity?>(null) }
    
    val backgroundBrush = Brush.verticalGradient(
        colors = listOf(Color(0xFF0f0c29), Color(0xFF302b63), Color(0xFF24243e))
    )
    
    // Handle back press
    BackHandler { onBack() }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Auto-Add Settings", color = Color(0xFF10B981), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color(0xFF10B981))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF1a1a2e))
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddRuleDialog = true },
                containerColor = Color(0xFF10B981)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Rule", tint = Color.White)
            }
        },
        containerColor = Color.Transparent
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(backgroundBrush)
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Main Settings Card
            item {
                MainSettingsCard(
                    settings = settings,
                    onSettingsChange = { newSettings ->
                        settings = newSettings
                        leadManager.saveAutoAddSettings(newSettings)
                    }
                )
            }
            
            // Keyword Rules Section
            item {
                Text(
                    "Keyword Rules",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
            
            if (keywordRules.isEmpty()) {
                item {
                    EmptyRulesCard()
                }
            } else {
                items(keywordRules) { rule ->
                    KeywordRuleCard(
                        rule = rule,
                        onEdit = { editingRule = rule },
                        onDelete = {
                            leadManager.deleteAutoAddKeywordRule(rule.id)
                            keywordRules = leadManager.getAllAutoAddKeywordRules()
                        },
                        onToggle = {
                            val updated = rule.copy(isEnabled = !rule.isEnabled)
                            leadManager.updateAutoAddKeywordRule(updated)
                            keywordRules = leadManager.getAllAutoAddKeywordRules()
                        }
                    )
                }
            }
        }
    }
    
    // Add/Edit Rule Dialog
    if (showAddRuleDialog || editingRule != null) {
        AddEditRuleDialog(
            rule = editingRule,
            onDismiss = {
                showAddRuleDialog = false
                editingRule = null
            },
            onSave = { rule ->
                if (editingRule != null) {
                    leadManager.updateAutoAddKeywordRule(rule)
                } else {
                    leadManager.addAutoAddKeywordRule(rule)
                }
                keywordRules = leadManager.getAllAutoAddKeywordRules()
                showAddRuleDialog = false
                editingRule = null
            }
        )
    }
}

@Composable
fun MainSettingsCard(
    settings: AutoAddSettingsEntity,
    onSettingsChange: (AutoAddSettingsEntity) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1a1a2e)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                "General Settings",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            
            // Enable Auto-Add
            SettingSwitch(
                title = "Enable Auto-Add",
                description = "Automatically add leads from AutoRespond",
                checked = settings.isAutoAddEnabled,
                onCheckedChange = { onSettingsChange(settings.copy(isAutoAddEnabled = it)) }
            )
            
            // Add All Messages
            SettingSwitch(
                title = "Add All Messages",
                description = "Add all incoming messages as leads",
                checked = settings.autoAddAllMessages,
                onCheckedChange = { onSettingsChange(settings.copy(autoAddAllMessages = it)) },
                enabled = settings.isAutoAddEnabled
            )
            
            // Keyword Based Add
            SettingSwitch(
                title = "Keyword Based",
                description = "Only add when keyword matches",
                checked = settings.keywordBasedAdd,
                onCheckedChange = { onSettingsChange(settings.copy(keywordBasedAdd = it)) },
                enabled = settings.isAutoAddEnabled && !settings.autoAddAllMessages
            )
            
            // Exclude Existing
            SettingSwitch(
                title = "Exclude Existing",
                description = "Don't add if contact already exists",
                checked = settings.excludeExistingContacts,
                onCheckedChange = { onSettingsChange(settings.copy(excludeExistingContacts = it)) },
                enabled = settings.isAutoAddEnabled
            )
            
            // Notify on New Lead
            SettingSwitch(
                title = "Notify on New Lead",
                description = "Show notification when lead is added",
                checked = settings.notifyOnNewLead,
                onCheckedChange = { onSettingsChange(settings.copy(notifyOnNewLead = it)) },
                enabled = settings.isAutoAddEnabled
            )
            
            HorizontalDivider(color = Color(0xFF2D3748))
            
            // Default Settings
            Text(
                "Default Values for New Leads",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF94A3B8)
            )
            
            // Default Source
            OutlinedTextField(
                value = settings.defaultSource,
                onValueChange = { onSettingsChange(settings.copy(defaultSource = it)) },
                label = { Text("Default Source") },
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF10B981),
                    unfocusedBorderColor = Color(0xFF64748B),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                enabled = settings.isAutoAddEnabled
            )
            
            // Default Category
            OutlinedTextField(
                value = settings.defaultCategory,
                onValueChange = { onSettingsChange(settings.copy(defaultCategory = it)) },
                label = { Text("Default Category") },
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF10B981),
                    unfocusedBorderColor = Color(0xFF64748B),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                enabled = settings.isAutoAddEnabled
            )
        }
    }
}

@Composable
fun SettingSwitch(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = if (enabled) Color.White else Color(0xFF64748B)
            )
            Text(
                description,
                fontSize = 12.sp,
                color = Color(0xFF64748B)
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = Color(0xFF10B981)
            )
        )
    }
}

@Composable
fun KeywordRuleCard(
    rule: AutoAddKeywordRuleEntity,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onToggle: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (rule.isEnabled) Color(0xFF1a1a2e) else Color(0xFF1a1a2e).copy(alpha = 0.5f)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    rule.keyword,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF10B981)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = Color(0xFF2a2a3e)
                    ) {
                        Text(
                            rule.matchType.name,
                            fontSize = 10.sp,
                            color = Color(0xFF94A3B8),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = Color(0xFF2a2a3e)
                    ) {
                        Text(
                            rule.assignStatus,
                            fontSize = 10.sp,
                            color = Color(0xFF3B82F6),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
            
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                IconButton(onClick = onEdit, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit", tint = Color(0xFF64748B), modifier = Modifier.size(20.dp))
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color(0xFFEF4444), modifier = Modifier.size(20.dp))
                }
                Switch(
                    checked = rule.isEnabled,
                    onCheckedChange = { onToggle() },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = Color(0xFF10B981)
                    )
                )
            }
        }
    }
}

@Composable
fun EmptyRulesCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1a1a2e)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Default.Rule,
                contentDescription = null,
                tint = Color(0xFF64748B),
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text("No keyword rules yet", fontSize = 16.sp, color = Color(0xFF94A3B8))
            Text("Add rules to auto-add leads based on keywords", fontSize = 14.sp, color = Color(0xFF64748B))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditRuleDialog(
    rule: AutoAddKeywordRuleEntity?,
    onDismiss: () -> Unit,
    onSave: (AutoAddKeywordRuleEntity) -> Unit
) {
    var keyword by remember { mutableStateOf(rule?.keyword ?: "") }
    var matchType by remember { mutableStateOf(rule?.matchType ?: KeywordMatchType.CONTAINS) }
    var assignStatus by remember { mutableStateOf(rule?.assignStatus ?: "NEW") }
    var assignCategory by remember { mutableStateOf(rule?.assignCategory ?: "General") }
    var assignPriority by remember { mutableStateOf(rule?.assignPriority ?: "MEDIUM") }
    var matchTypeExpanded by remember { mutableStateOf(false) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1a1a2e),
        title = {
            Text(
                if (rule == null) "Add Keyword Rule" else "Edit Keyword Rule",
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedTextField(
                    value = keyword,
                    onValueChange = { keyword = it },
                    label = { Text("Keyword") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF10B981),
                        unfocusedBorderColor = Color(0xFF64748B),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    )
                )
                
                // Match Type Dropdown
                ExposedDropdownMenuBox(
                    expanded = matchTypeExpanded,
                    onExpandedChange = { matchTypeExpanded = it }
                ) {
                    OutlinedTextField(
                        value = matchType.name,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Match Type") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = matchTypeExpanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF10B981),
                            unfocusedBorderColor = Color(0xFF64748B),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )
                    ExposedDropdownMenu(
                        expanded = matchTypeExpanded,
                        onDismissRequest = { matchTypeExpanded = false }
                    ) {
                        KeywordMatchType.values().forEach { type ->
                            DropdownMenuItem(
                                text = { Text(type.name) },
                                onClick = {
                                    matchType = type
                                    matchTypeExpanded = false
                                }
                            )
                        }
                    }
                }
                
                OutlinedTextField(
                    value = assignCategory,
                    onValueChange = { assignCategory = it },
                    label = { Text("Assign Category") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF10B981),
                        unfocusedBorderColor = Color(0xFF64748B),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    )
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val newRule = AutoAddKeywordRuleEntity(
                        id = rule?.id ?: UUID.randomUUID().toString(),
                        keyword = keyword,
                        matchType = matchType,
                        assignStatus = assignStatus,
                        assignCategory = assignCategory,
                        assignPriority = assignPriority,
                        isEnabled = rule?.isEnabled ?: true,
                        createdAt = rule?.createdAt ?: System.currentTimeMillis()
                    )
                    onSave(newRule)
                },
                enabled = keyword.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Color(0xFF94A3B8))
            }
        }
    )
}
