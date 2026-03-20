package com.message.bulksend.aiagent.tools.agentform.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Style
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.message.bulksend.aiagent.tools.agentform.components.AddFieldSheet
import com.message.bulksend.aiagent.tools.agentform.components.FormFieldItem
import com.message.bulksend.aiagent.tools.agentform.models.FieldType
import com.message.bulksend.aiagent.tools.agentform.models.FormField
import com.message.bulksend.aiagent.tools.agentform.models.FormVerificationSettings
import com.message.bulksend.aiagent.tools.agentform.models.StoredFormConfig
import com.message.bulksend.db.AgentFormEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FormBuilderScreen(
        initialForm: AgentFormEntity? = null,
        onSave: (String, String, List<FormField>, FormVerificationSettings) -> Unit,
        onBack: () -> Unit
) {
    val parsedConfig = remember(initialForm?.fieldsJson) { parseStoredFormConfig(initialForm?.fieldsJson) }

    var formTitle by remember { mutableStateOf(initialForm?.title ?: "New Agent Form") }
    var formDescription by remember { mutableStateOf(initialForm?.description ?: "") }
    var fields by remember(parsedConfig) { mutableStateOf(parsedConfig.fields) }
    var verification by remember(parsedConfig) { mutableStateOf(parsedConfig.verification) }
    var showAddSheet by remember { mutableStateOf(false) }

    val verificationCount =
            listOf(
                            verification.requireGoogleAuth,
                            verification.requireContactVerification,
                            verification.requireLocationVerification
                    )
                    .count { it }

    Scaffold(
            topBar = {
                TopAppBar(
                        title = {
                            Column {
                                Text(
                                        text = if (initialForm != null) "Edit Form" else "Create Form",
                                        style = MaterialTheme.typography.titleLarge
                                )
                                Text(
                                        text = "AgentForm Builder",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        },
                        navigationIcon = {
                            IconButton(onClick = onBack) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                            }
                        },
                        actions = {
                            TextButton(onClick = { onSave(formTitle, formDescription, fields, verification) }) {
                                Icon(Icons.Default.Check, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Save")
                            }
                        }
                )
            },
            floatingActionButton = {
                ExtendedFloatingActionButton(
                        onClick = { showAddSheet = true },
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        icon = { Icon(Icons.Default.Add, contentDescription = null) },
                        text = { Text("Add Field") }
                )
            }
    ) { paddingValues ->
        LazyColumn(
                modifier = Modifier.fillMaxSize().padding(paddingValues),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                BuilderOverviewCard(
                        isEditMode = initialForm != null,
                        fieldCount = fields.size,
                        verificationCount = verificationCount
                )
            }

            item {
                FormDetailsCard(
                        formTitle = formTitle,
                        onTitleChange = { formTitle = it },
                        formDescription = formDescription,
                        onDescriptionChange = { formDescription = it }
                )
            }

            item {
                VerificationSection(
                        verification = verification,
                        onVerificationChange = { verification = it }
                )
            }

            item { SectionHeader(title = "Form Fields", badgeText = "${fields.size} Added") }

            if (fields.isEmpty()) {
                item { EmptyFieldsState() }
            } else {
                items(fields, key = { it.id }) { field ->
                    FormFieldItem(
                            field = field,
                            onDelete = { fields = fields.filter { it.id != field.id } }
                    )
                }
            }
        }

        if (showAddSheet) {
            AddFieldSheet(
                    onDismiss = { showAddSheet = false },
                    onAddField = { newField ->
                        fields = fields + newField
                        showAddSheet = false
                    }
            )
        }
    }
}

@Composable
private fun BuilderOverviewCard(
        isEditMode: Boolean,
        fieldCount: Int,
        verificationCount: Int
) {
    Card(
            colors =
                    CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                    text = if (isEditMode) "Refine your form experience" else "Design a high-converting form",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    fontWeight = FontWeight.SemiBold
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(
                        onClick = {},
                        label = { Text("$fieldCount fields") },
                        leadingIcon = { Icon(Icons.Default.Style, contentDescription = null) },
                        colors =
                                AssistChipDefaults.assistChipColors(
                                        containerColor = MaterialTheme.colorScheme.surface,
                                        labelColor = MaterialTheme.colorScheme.onSurface
                                )
                )
                AssistChip(
                        onClick = {},
                        label = { Text("$verificationCount verifications") },
                        leadingIcon = { Icon(Icons.Default.Security, contentDescription = null) },
                        colors =
                                AssistChipDefaults.assistChipColors(
                                        containerColor = MaterialTheme.colorScheme.surface,
                                        labelColor = MaterialTheme.colorScheme.onSurface
                                )
                )
            }
        }
    }
}

@Composable
private fun FormDetailsCard(
        formTitle: String,
        onTitleChange: (String) -> Unit,
        formDescription: String,
        onDescriptionChange: (String) -> Unit
) {
    Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border =
                    BorderStroke(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.outlineVariant
                    )
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                    text = "Form Details",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
            )

            OutlinedTextField(
                    value = formTitle,
                    onValueChange = onTitleChange,
                    label = { Text("Form title / brand") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
            )

            OutlinedTextField(
                    value = formDescription,
                    onValueChange = onDescriptionChange,
                    label = { Text("Description") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 4
            )
        }
    }
}

@Composable
private fun VerificationSection(
        verification: FormVerificationSettings,
        onVerificationChange: (FormVerificationSettings) -> Unit
) {
    Card(
            colors =
                    CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                    )
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                    text = "Verification",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    fontWeight = FontWeight.SemiBold
            )
            Text(
                    text = "Enable only what your form actually needs.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
            )

            VerificationToggleRow(
                    title = "Google Auth",
                    subtitle = "Users must sign in before submit",
                    checked = verification.requireGoogleAuth,
                    onCheckedChange = {
                        onVerificationChange(verification.copy(requireGoogleAuth = it))
                    }
            )
            VerificationToggleRow(
                    title = "Contact Verify",
                    subtitle = "Collect and verify contact name + number",
                    checked = verification.requireContactVerification,
                    onCheckedChange = {
                        onVerificationChange(verification.copy(requireContactVerification = it))
                    }
            )
            VerificationToggleRow(
                    title = "Maps Location",
                    subtitle = "Ask for live location verification",
                    checked = verification.requireLocationVerification,
                    onCheckedChange = {
                        onVerificationChange(verification.copy(requireLocationVerification = it))
                    }
            )
        }
    }
}

@Composable
private fun VerificationToggleRow(
        title: String,
        subtitle: String,
        checked: Boolean,
        onCheckedChange: (Boolean) -> Unit
) {
    Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.75f)
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SectionHeader(title: String, badgeText: String) {
    Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        AssistChip(
                onClick = {},
                label = { Text(badgeText) },
                colors =
                        AssistChipDefaults.assistChipColors(
                                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                labelColor = MaterialTheme.colorScheme.onTertiaryContainer
                        )
        )
    }
}

@Composable
private fun EmptyFieldsState() {
    Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                    text = "No fields added yet",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                    text = "Tap Add Field to start building your form.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun parseStoredFormConfig(fieldsJson: String?): StoredFormConfig {
    if (fieldsJson.isNullOrBlank()) {
        return StoredFormConfig()
    }

    return try {
        val gson = Gson()
        val trimmed = fieldsJson.trim()

        val parsed =
                if (trimmed.startsWith("[")) {
                    val fields: List<FormField> =
                            gson.fromJson(
                                    trimmed,
                                    object : TypeToken<List<FormField>>() {}.type
                            ) ?: emptyList()
                    StoredFormConfig(fields = fields)
                } else {
                    gson.fromJson(trimmed, StoredFormConfig::class.java) ?: StoredFormConfig()
                }

        foldLegacyVerification(parsed)
    } catch (e: Exception) {
        StoredFormConfig()
    }
}

private fun foldLegacyVerification(config: StoredFormConfig): StoredFormConfig {
    val hasLegacyGoogleAuth = config.fields.any { it.type == FieldType.GOOGLE_AUTH }
    val hasLegacyContactPicker = config.fields.any { it.type == FieldType.CONTACT_PICKER }
    val hasLegacyLocation = config.fields.any { it.type == FieldType.LOCATION }

    val cleanedFields =
            config.fields.filterNot {
                it.type == FieldType.GOOGLE_AUTH ||
                        it.type == FieldType.CONTACT_PICKER ||
                        it.type == FieldType.LOCATION
            }

    val verification =
            config.verification.copy(
                    requireGoogleAuth = config.verification.requireGoogleAuth || hasLegacyGoogleAuth,
                    requireContactVerification =
                            config.verification.requireContactVerification ||
                                    hasLegacyContactPicker,
                    requireLocationVerification =
                            config.verification.requireLocationVerification || hasLegacyLocation
            )

    return StoredFormConfig(fields = cleanedFields, verification = verification)
}

