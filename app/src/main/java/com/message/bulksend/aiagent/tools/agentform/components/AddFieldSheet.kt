package com.message.bulksend.aiagent.tools.agentform.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.message.bulksend.aiagent.tools.agentform.models.FieldType
import com.message.bulksend.aiagent.tools.agentform.models.FormField

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddFieldSheet(onDismiss: () -> Unit, onAddField: (FormField) -> Unit) {
    val addableTypes = remember { FieldType.values().filter { !it.isVerificationType } }
    var selectedType by remember { mutableStateOf(FieldType.TEXT) }
    var label by remember { mutableStateOf("") }
    var isRequired by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                    text = "Add Form Field",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold
            )
            Text(
                    text = "Pick the field type and label users will see.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                LazyColumn(
                        modifier = Modifier.fillMaxWidth().height(200.dp).padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(addableTypes) { type ->
                        Row(
                                modifier =
                                        Modifier.fillMaxWidth()
                                                .clickable { selectedType = type }
                                                .padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = selectedType == type, onClick = { selectedType = type })
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = type.label, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }

            OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("Field label") },
                    placeholder = { Text("e.g. Full Name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
            )

            Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Required field", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                        Text(
                                "User must fill this before submit",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = isRequired, onCheckedChange = { isRequired = it })
                }
            }

            Button(
                    onClick = {
                        if (label.isNotBlank()) {
                            onAddField(
                                    FormField(
                                            type = selectedType,
                                            label = label.trim(),
                                            required = isRequired
                                    )
                            )
                            onDismiss()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = label.isNotBlank()
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Add Field")
            }
        }
    }
}

