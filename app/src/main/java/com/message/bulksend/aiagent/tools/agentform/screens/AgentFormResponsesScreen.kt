package com.message.bulksend.aiagent.tools.agentform.screens

import android.util.Log
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.message.bulksend.aiagent.tools.agentform.AgentFormTableSheetSyncManager
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private data class AgentFormResponseItem(
    val id: String,
    val formId: String,
    val status: String,
    val eventType: String,
    val timestamp: Long,
    val details: String
)

@Composable
fun AgentFormResponsesScreen(
    modifier: Modifier = Modifier,
    ownerUid: String,
    ownerPhone: String
) {
    val context = LocalContext.current
    var items by remember { mutableStateOf(emptyList<AgentFormResponseItem>()) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(ownerUid, ownerPhone) {
        isLoading = true
        error = null
        try {
            if (ownerUid.isBlank() || ownerPhone.isBlank()) {
                items = emptyList()
                error = "Login details missing for responses."
                isLoading = false
                return@LaunchedEffect
            }

            val snapshot =
                FirebaseFirestore.getInstance()
                    .collection("users")
                    .document(ownerUid)
                    .collection("numbers")
                    .document(ownerPhone)
                    .collection("responses")
                    .get()
                    .await()

            runCatching {
                AgentFormTableSheetSyncManager(context).syncResponseDocuments(
                    ownerUid = ownerUid,
                    ownerPhone = ownerPhone,
                    documents = snapshot.documents
                )
            }.onFailure { syncError ->
                Log.e("AgentFormResponses", "Failed to sync responses to TableSheet: ${syncError.message}", syncError)
            }

            val mapped =
                snapshot.documents.map { doc ->
                    val status =
                        doc.getString("verificationStatus")
                            ?: doc.getString("submissionStatus")
                            ?: "received"
                    val eventType = doc.getString("eventType") ?: "submission"
                    val formId = doc.getString("form_id") ?: doc.getString("formId") ?: "unknown"
                    val details =
                        doc.getString("reason")
                            ?: doc.getString("verificationMessage")
                            ?: doc.getString("campaign")
                            ?: ""
                    val timestamp = parseTimestamp(doc.get("timestamp"))

                    AgentFormResponseItem(
                        id = doc.id,
                        formId = formId,
                        status = status,
                        eventType = eventType,
                        timestamp = timestamp,
                        details = details
                    )
                }.sortedByDescending { it.timestamp }

            items = mapped
        } catch (e: Exception) {
            error = e.message ?: "Failed to load responses"
            items = emptyList()
        } finally {
            isLoading = false
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(
                colors =
                    CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    )
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Form Responses",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Text(
                        text = "Submissions and contact verification events are listed here.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.85f)
                    )
                }
            }
        }

        when {
            isLoading -> {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Text(
                            text = "Loading responses...",
                            modifier = Modifier.fillMaxWidth().padding(18.dp)
                        )
                    }
                }
            }

            error != null -> {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.ErrorOutline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = error ?: "Error",
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
            }

            items.isEmpty() -> {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Inbox,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "No responses yet",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Share forms from Templates tab. Verification failures (not saved) will also appear here.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Icon(
                                imageVector = Icons.Default.TaskAlt,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            else -> {
                items(items, key = { it.id }) { item ->
                    ResponseItemCard(item)
                }
            }
        }
    }
}

@Composable
private fun ResponseItemCard(item: AgentFormResponseItem) {
    val isNotSaved = item.status.equals("not_saved", ignoreCase = true)
    val chipContainer =
        if (isNotSaved) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer
    val chipColor =
        if (isNotSaved) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Form: ${item.formId}",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold
                )
                AssistChip(
                    onClick = {},
                    label = { Text(item.status.replace('_', ' ')) },
                    colors =
                        AssistChipDefaults.assistChipColors(
                            containerColor = chipContainer,
                            labelColor = chipColor
                        )
                )
            }

            Text(
                text = "Type: ${item.eventType}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "At: ${SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date(item.timestamp))}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (item.details.isNotBlank()) {
                Text(
                    text = item.details,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

private fun parseTimestamp(value: Any?): Long {
    return when (value) {
        is Timestamp -> value.toDate().time
        is Number -> value.toLong()
        is String -> {
            value.toLongOrNull()
                ?: try {
                    val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
                    parser.timeZone = java.util.TimeZone.getTimeZone("UTC")
                    parser.parse(value)?.time ?: System.currentTimeMillis()
                } catch (_: Exception) {
                    System.currentTimeMillis()
                }
        }
        else -> System.currentTimeMillis()
    }
}
