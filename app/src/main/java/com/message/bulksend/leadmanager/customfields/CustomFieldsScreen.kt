package com.message.bulksend.leadmanager.customfields

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.BackHandler
import com.message.bulksend.leadmanager.customfields.model.CustomField
import com.message.bulksend.leadmanager.customfields.ui.FieldTypeSelector
import com.message.bulksend.leadmanager.customfields.ui.getIconForFieldType
import com.message.bulksend.leadmanager.customfields.ui.getIconTintForFieldType
import com.message.bulksend.leadmanager.customfields.ui.getDisplayNameForFieldType
import com.message.bulksend.leadmanager.database.entities.CustomFieldType
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Custom Fields Management Screen
 * Requirements: 1.1, 1.2, 1.3, 1.4, 1.5, 1.6, 1.7
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomFieldsScreen(
    customFieldsManager: CustomFieldsManager,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    // State
    val customFields by customFieldsManager.getAllFields().collectAsState(initial = emptyList())
    var showAddEditDialog by remember { mutableStateOf(false) }
    var editingField by remember { mutableStateOf<CustomField?>(null) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var fieldToDelete by remember { mutableStateOf<CustomField?>(null) }
    
    val backgroundBrush = Brush.verticalGradient(
        colors = listOf(Color(0xFF0f0c29), Color(0xFF302b63), Color(0xFF24243e))
    )
    
    BackHandler { onBack() }

    // Delete Confirmation Dialog
    if (showDeleteDialog && fieldToDelete != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false; fieldToDelete = null },
            title = { Text("Delete Field", color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(
                        "Are you sure you want to delete \"${fieldToDelete?.fieldName}\"?",
                        color = Color.White
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "⚠️ This will also delete all values stored for this field in all leads.",
                        color = Color(0xFFF59E0B),
                        fontSize = 12.sp
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    fieldToDelete?.let { field ->
                        coroutineScope.launch {
                            customFieldsManager.deleteField(field.id)
                        }
                    }
                    showDeleteDialog = false
                    fieldToDelete = null
                }) { Text("Delete", color = Color(0xFFEF4444)) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false; fieldToDelete = null }) {
                    Text("Cancel", color = Color(0xFF94A3B8))
                }
            },
            containerColor = Color(0xFF1a1a2e)
        )
    }
    
    // Add/Edit Full Screen
    if (showAddEditDialog) {
        AddEditFieldScreen(
            field = editingField,
            onBack = { showAddEditDialog = false; editingField = null },
            onSave = { field ->
                coroutineScope.launch {
                    if (editingField != null) {
                        customFieldsManager.updateField(field)
                    } else {
                        val nextOrder = customFieldsManager.getNextDisplayOrder()
                        customFieldsManager.addField(field.copy(displayOrder = nextOrder))
                    }
                }
                showAddEditDialog = false
                editingField = null
            }
        )
        return
    }

    Box(modifier = Modifier.fillMaxSize().background(backgroundBrush)) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top Bar
            Surface(modifier = Modifier.fillMaxWidth(), color = Color(0xFF1a1a2e), shadowElevation = 8.dp) {
                Row(
                    modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier.size(48.dp).background(Color(0xFFEC4899).copy(alpha = 0.2f), CircleShape)
                        ) {
                            Icon(Icons.Default.ArrowBack, "Back", tint = Color(0xFFEC4899))
                        }
                        Column {
                            Text("Custom Fields", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            Text("${customFields.size} fields configured", fontSize = 14.sp, color = Color(0xFF94A3B8))
                        }
                    }
                    Icon(Icons.Default.DynamicForm, null, tint = Color(0xFFEC4899), modifier = Modifier.size(32.dp))
                }
            }
            
            // Content
            if (customFields.isEmpty()) {
                // Empty State
                Box(
                    modifier = Modifier.fillMaxSize().padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.DynamicForm,
                            null,
                            tint = Color(0xFF64748B),
                            modifier = Modifier.size(80.dp)
                        )
                        Spacer(Modifier.height(16.dp))
                        Text("No Custom Fields", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Create custom fields to capture additional lead information",
                            fontSize = 14.sp,
                            color = Color(0xFF94A3B8),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Spacer(Modifier.height(24.dp))
                        Button(
                            onClick = { showAddEditDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEC4899)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Add, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Add First Field")
                        }
                    }
                }
            } else {
                // Fields List
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(customFields, key = { it.id }) { field ->
                        CustomFieldCard(
                            field = field,
                            onEdit = { editingField = field; showAddEditDialog = true },
                            onDelete = { fieldToDelete = field; showDeleteDialog = true }
                        )
                    }
                    item { Spacer(Modifier.height(80.dp)) }
                }
            }
        }
        
        // FAB
        if (customFields.isNotEmpty()) {
            FloatingActionButton(
                onClick = { showAddEditDialog = true },
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp).navigationBarsPadding(),
                containerColor = Color(0xFFEC4899)
            ) {
                Icon(Icons.Default.Add, "Add Field", tint = Color.White)
            }
        }
    }
}

/**
 * Custom Field Card composable
 * Requirements: 1.1, 1.7
 */
@Composable
fun CustomFieldCard(
    field: CustomField,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val iconTint = getIconTintForFieldType(field.fieldType)
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1a1a2e)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Icon
            Box(
                modifier = Modifier.size(48.dp).background(iconTint.copy(alpha = 0.15f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(getIconForFieldType(field.fieldType), null, tint = iconTint, modifier = Modifier.size(24.dp))
            }
            
            // Field Info
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(field.fieldName, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (field.isRequired) {
                        Text(" *", color = Color(0xFFEF4444), fontWeight = FontWeight.Bold)
                    }
                    if (!field.isActive) {
                        Spacer(Modifier.width(8.dp))
                        Surface(color = Color(0xFF64748B).copy(alpha = 0.3f), shape = RoundedCornerShape(4.dp)) {
                            Text("Inactive", fontSize = 10.sp, color = Color(0xFF94A3B8), modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(color = iconTint.copy(alpha = 0.15f), shape = RoundedCornerShape(4.dp)) {
                        Text(getDisplayNameForFieldType(field.fieldType), fontSize = 11.sp, color = iconTint, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                    }
                    if (field.fieldType == CustomFieldType.DROPDOWN && field.options.isNotEmpty()) {
                        Text("${field.options.size} options", fontSize = 11.sp, color = Color(0xFF94A3B8))
                    }
                    if (field.defaultValue.isNotEmpty()) {
                        Text("Default: ${field.defaultValue}", fontSize = 11.sp, color = Color(0xFF94A3B8), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            
            // Actions
            IconButton(onClick = onEdit, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.Edit, "Edit", tint = Color(0xFF3B82F6), modifier = Modifier.size(20.dp))
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.Delete, "Delete", tint = Color(0xFFEF4444), modifier = Modifier.size(20.dp))
            }
        }
    }
}


/**
 * Add/Edit Field Full Screen
 * Requirements: 1.2, 1.3, 1.4
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditFieldScreen(
    field: CustomField?,
    onBack: () -> Unit,
    onSave: (CustomField) -> Unit
) {
    var fieldName by remember { mutableStateOf(field?.fieldName ?: "") }
    var fieldType by remember { mutableStateOf(field?.fieldType ?: CustomFieldType.TEXT) }
    var isRequired by remember { mutableStateOf(field?.isRequired ?: false) }
    var defaultValue by remember { mutableStateOf(field?.defaultValue ?: "") }
    var options by remember { mutableStateOf(field?.options ?: emptyList()) }
    var newOption by remember { mutableStateOf("") }
    var showError by remember { mutableStateOf(false) }
    
    val isEditing = field != null
    
    val backgroundBrush = Brush.verticalGradient(
        colors = listOf(Color(0xFF0f0c29), Color(0xFF302b63), Color(0xFF24243e))
    )
    
    BackHandler { onBack() }
    
    Box(modifier = Modifier.fillMaxSize().background(backgroundBrush)) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top Bar
            Surface(modifier = Modifier.fillMaxWidth(), color = Color(0xFF1a1a2e), shadowElevation = 8.dp) {
                Row(
                    modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier.size(48.dp).background(Color(0xFFEC4899).copy(alpha = 0.2f), CircleShape)
                        ) {
                            Icon(Icons.Default.ArrowBack, "Back", tint = Color(0xFFEC4899))
                        }
                        Column {
                            Text(
                                if (isEditing) "Edit Field" else "Add New Field",
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text("Configure field properties", fontSize = 14.sp, color = Color(0xFF94A3B8))
                        }
                    }
                    Icon(if (isEditing) Icons.Default.Edit else Icons.Default.Add, null, tint = Color(0xFFEC4899), modifier = Modifier.size(32.dp))
                }
            }
            
            // Content
            LazyColumn(
                modifier = Modifier.fillMaxSize().weight(1f).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Field Name Card
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1a1a2e)),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Field Name", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFFEC4899))
                            Spacer(Modifier.height(12.dp))
                            OutlinedTextField(
                                value = fieldName,
                                onValueChange = { fieldName = it; showError = false },
                                label = { Text("Field Name *", color = Color(0xFF94A3B8)) },
                                placeholder = { Text("e.g., Company Name", color = Color(0xFF64748B)) },
                                singleLine = true,
                                isError = showError && fieldName.isBlank(),
                                modifier = Modifier.fillMaxWidth(),
                                colors = fullScreenTextFieldColors(),
                                shape = RoundedCornerShape(12.dp)
                            )
                            if (showError && fieldName.isBlank()) {
                                Text("Field name is required", color = Color(0xFFEF4444), fontSize = 12.sp, modifier = Modifier.padding(start = 4.dp, top = 4.dp))
                            }
                        }
                    }
                }
                
                // Field Type Card
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1a1a2e)),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            FieldTypeSelector(
                                selectedType = fieldType,
                                onTypeSelected = { fieldType = it }
                            )
                        }
                    }
                }
                
                // Settings Card
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1a1a2e)),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Settings", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFFEC4899))
                            Spacer(Modifier.height(12.dp))
                            
                            // Required Toggle
                            Row(
                                modifier = Modifier.fillMaxWidth().background(Color(0xFF334155).copy(alpha = 0.3f), RoundedCornerShape(12.dp)).padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text("Required Field", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Color.White)
                                    Text("User must fill this field", fontSize = 12.sp, color = Color(0xFF94A3B8))
                                }
                                Switch(
                                    checked = isRequired,
                                    onCheckedChange = { isRequired = it },
                                    colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = Color(0xFFEC4899))
                                )
                            }
                            
                            // Default Value (not for checkbox)
                            if (fieldType != CustomFieldType.CHECKBOX) {
                                Spacer(Modifier.height(12.dp))
                                OutlinedTextField(
                                    value = defaultValue,
                                    onValueChange = { defaultValue = it },
                                    label = { Text("Default Value (Optional)", color = Color(0xFF94A3B8)) },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = fullScreenTextFieldColors(),
                                    shape = RoundedCornerShape(12.dp)
                                )
                            }
                        }
                    }
                }
                
                // Dropdown Options Card
                if (fieldType == CustomFieldType.DROPDOWN) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF1a1a2e)),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                DropdownOptionsEditor(
                                    options = options,
                                    newOption = newOption,
                                    onNewOptionChange = { newOption = it },
                                    onAddOption = {
                                        if (newOption.isNotBlank() && !options.contains(newOption.trim())) {
                                            options = options + newOption.trim()
                                            newOption = ""
                                        }
                                    },
                                    onRemoveOption = { options = options - it }
                                )
                                if (showError && options.size < 2) {
                                    Text("At least 2 options required", color = Color(0xFFEF4444), fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
                                }
                            }
                        }
                    }
                }
                
                item { Spacer(Modifier.height(80.dp)) }
            }
            
            // Bottom Save Button
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color(0xFF1a1a2e),
                shadowElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onBack,
                        modifier = Modifier.weight(1f).height(56.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF94A3B8)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF64748B)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Cancel", fontSize = 16.sp)
                    }
                    Button(
                        onClick = {
                            if (fieldName.isBlank()) {
                                showError = true
                                return@Button
                            }
                            if (fieldType == CustomFieldType.DROPDOWN && options.size < 2) {
                                showError = true
                                return@Button
                            }
                            
                            val newField = CustomField(
                                id = field?.id ?: UUID.randomUUID().toString(),
                                fieldName = fieldName.trim(),
                                fieldType = fieldType,
                                isRequired = isRequired,
                                defaultValue = defaultValue.trim(),
                                options = if (fieldType == CustomFieldType.DROPDOWN) options else emptyList(),
                                displayOrder = field?.displayOrder ?: 0,
                                isActive = field?.isActive ?: true,
                                createdAt = field?.createdAt ?: System.currentTimeMillis()
                            )
                            onSave(newField)
                        },
                        modifier = Modifier.weight(1f).height(56.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEC4899)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Check, null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Save Field", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

/**
 * Dropdown Options Editor
 * Requirements: 1.3
 */
@Composable
fun DropdownOptionsEditor(
    options: List<String>,
    newOption: String,
    onNewOptionChange: (String) -> Unit,
    onAddOption: () -> Unit,
    onRemoveOption: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Dropdown Options", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
        
        // Add new option
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = newOption,
                onValueChange = onNewOptionChange,
                placeholder = { Text("Add option...") },
                singleLine = true,
                modifier = Modifier.weight(1f),
                colors = dialogTextFieldColors(),
                shape = RoundedCornerShape(8.dp)
            )
            IconButton(
                onClick = onAddOption,
                modifier = Modifier.size(48.dp).background(Color(0xFF10B981).copy(alpha = 0.2f), CircleShape)
            ) {
                Icon(Icons.Default.Add, "Add", tint = Color(0xFF10B981))
            }
        }
        
        // Options list
        if (options.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                options.forEachIndexed { index, option ->
                    Row(
                        modifier = Modifier.fillMaxWidth().background(Color(0xFF334155).copy(alpha = 0.3f), RoundedCornerShape(8.dp)).padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("${index + 1}.", color = Color(0xFF94A3B8), fontSize = 12.sp)
                            Text(option, color = Color.White, fontSize = 14.sp)
                        }
                        IconButton(onClick = { onRemoveOption(option) }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Close, "Remove", tint = Color(0xFFEF4444), modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        } else {
            Text("No options added yet", fontSize = 12.sp, color = Color(0xFF94A3B8))
        }
    }
}

@Composable
fun dialogTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Color(0xFFEC4899),
    focusedLabelColor = Color(0xFFEC4899),
    unfocusedBorderColor = Color(0xFF64748B),
    unfocusedLabelColor = Color(0xFF94A3B8),
    focusedTextColor = Color.White,
    unfocusedTextColor = Color.White,
    cursorColor = Color(0xFFEC4899)
)

@Composable
fun fullScreenTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Color(0xFFEC4899),
    focusedLabelColor = Color(0xFFEC4899),
    unfocusedBorderColor = Color(0xFF64748B),
    unfocusedLabelColor = Color(0xFF94A3B8),
    focusedTextColor = Color.White,
    unfocusedTextColor = Color.White,
    cursorColor = Color(0xFFEC4899),
    focusedPlaceholderColor = Color(0xFF64748B),
    unfocusedPlaceholderColor = Color(0xFF64748B)
)
