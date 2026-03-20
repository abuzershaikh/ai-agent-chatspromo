package com.message.bulksend.aiagent.tools.agentform.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileCopy
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.message.bulksend.db.AgentFormEntity
import com.message.bulksend.db.AppDatabase
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

@Composable
fun AgentFormTemplatesTabScreen(
        modifier: Modifier = Modifier,
        onCreateNew: () -> Unit,
        onEdit: (String) -> Unit,
        ownerUid: String,
        ownerPhone: String
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var forms by remember { mutableStateOf(emptyList<AgentFormEntity>()) }

    LaunchedEffect(Unit) {
        AppDatabase.getInstance(context).agentFormDao().getAllForms().collect { forms = it }
    }

    LazyColumn(
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { TemplatesOverviewCard(total = forms.size) }

        if (forms.isEmpty()) {
            item { EmptyTemplatesState(onCreateNew = onCreateNew) }
        } else {
            items(forms, key = { it.formId }) { form ->
                FormTemplateCard(
                        form = form,
                        onEdit = { onEdit(form.formId) },
                        onDelete = {
                            scope.launch {
                                AppDatabase.getInstance(context).agentFormDao().deleteForm(form)
                            }
                        }
                )
            }
        }
    }
}

@Composable
private fun TemplatesOverviewCard(total: Int) {
    Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
    ) {
        Row(
                modifier = Modifier.fillMaxWidth().padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                    imageVector = Icons.Default.FolderOpen,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onTertiaryContainer
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                        text = "Template Library",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                )
                Text(
                        text = "Reuse templates to launch forms faster.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f)
                )
            }
            AssistChip(
                    onClick = {},
                    label = { Text("$total total") },
                    colors =
                            AssistChipDefaults.assistChipColors(
                                    containerColor = MaterialTheme.colorScheme.surface,
                                    labelColor = MaterialTheme.colorScheme.onSurface
                            )
            )
        }
    }
}

@Composable
private fun EmptyTemplatesState(onCreateNew: () -> Unit) {
    Card(
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Box(modifier = Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.FileCopy, contentDescription = null)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                        text = "No templates yet",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                        text = "Create your first form template and share it instantly.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                FilledTonalButton(onClick = onCreateNew) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Create Form")
                }
            }
        }
    }
}

@Composable
fun FormTemplateCard(
        form: AgentFormEntity,
        onEdit: () -> Unit,
        onDelete: () -> Unit
) {
    val statusText = if (form.formId.contains("-")) "Local only" else "Cloud synced"
    val statusColor =
            if (form.formId.contains("-")) MaterialTheme.colorScheme.errorContainer
            else MaterialTheme.colorScheme.primaryContainer
    val statusTextColor =
            if (form.formId.contains("-")) MaterialTheme.colorScheme.onErrorContainer
            else MaterialTheme.colorScheme.onPrimaryContainer

    Card(
            elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                        text = form.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                )
                AssistChip(
                        onClick = {},
                        label = { Text(statusText) },
                        colors =
                                AssistChipDefaults.assistChipColors(
                                        containerColor = statusColor,
                                        labelColor = statusTextColor
                                )
                )
            }

            Text(
                    text =
                            "Created: ${SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(form.createdAt))}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onDelete, modifier = Modifier.weight(1f)) {
                    Icon(
                            Icons.Default.Delete,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }

                FilledTonalButton(onClick = onEdit, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Edit, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Edit")
                }
            }
        }
    }
}
