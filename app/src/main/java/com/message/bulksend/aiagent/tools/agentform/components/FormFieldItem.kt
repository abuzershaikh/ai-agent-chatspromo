package com.message.bulksend.aiagent.tools.agentform.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.message.bulksend.aiagent.tools.agentform.models.FormField

@Composable
fun FormFieldItem(field: FormField, onDelete: () -> Unit, modifier: Modifier = Modifier) {
    Card(
            modifier = modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                    imageVector = Icons.Default.DragHandle,
                    contentDescription = "Reorder",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
            )

            Spacer(modifier = Modifier.width(8.dp))

            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    AssistChip(
                            onClick = {},
                            label = { Text(field.type.label) },
                            colors =
                                    AssistChipDefaults.assistChipColors(
                                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                            labelColor = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                    )
                    if (field.required) {
                        AssistChip(
                                onClick = {},
                                label = { Text("Required") },
                                colors =
                                        AssistChipDefaults.assistChipColors(
                                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                                labelColor = MaterialTheme.colorScheme.onErrorContainer
                                        )
                        )
                    }
                }

                Text(
                        text = field.label,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                )
                if (field.hint.isNotBlank()) {
                    Text(
                            text = "Hint: ${field.hint}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Spacer(modifier = Modifier.height(0.dp))
                }
            }

            IconButton(onClick = onDelete) {
                Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete Field",
                        tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

