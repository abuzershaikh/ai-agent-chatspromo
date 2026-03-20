package com.message.bulksend.aiagent.tools.agentform.screens

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import com.google.firebase.auth.FirebaseAuth
import com.message.bulksend.aiagent.tools.agentform.AgentFormContactSettingsManager
import com.message.bulksend.aiagent.tools.agentform.models.AgentFormContactSettings
import com.message.bulksend.aiagent.tools.agentform.models.AgentFormPostSubmitPdf
import com.message.bulksend.userdetails.UserDetailsPreferences
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

@Composable
fun AgentFormOverviewScreen(
        modifier: Modifier = Modifier,
        onCreateNew: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settingsManager = remember { AgentFormContactSettingsManager(context) }
    var settings by remember { mutableStateOf(settingsManager.getSettings()) }
    var nameInput by remember { mutableStateOf("") }
    var phoneInput by remember { mutableStateOf("") }
    var videoUrlInput by remember { mutableStateOf(settings.postSubmitVideoUrl) }
    var reminderMessageInput by remember { mutableStateOf(settings.reminderMessage) }
    var verifiedMessageInput by remember { mutableStateOf(settings.verifiedMessage) }
    var isUploadingPdf by remember { mutableStateOf(false) }

    LaunchedEffect(settings.postSubmitVideoUrl) {
        if (videoUrlInput != settings.postSubmitVideoUrl) {
            videoUrlInput = settings.postSubmitVideoUrl
        }
    }
    LaunchedEffect(settings.reminderMessage, settings.verifiedMessage) {
        if (reminderMessageInput != settings.reminderMessage) {
            reminderMessageInput = settings.reminderMessage
        }
        if (verifiedMessageInput != settings.verifiedMessage) {
            verifiedMessageInput = settings.verifiedMessage
        }
    }

    val pdfPickerLauncher =
            rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.OpenDocument()
            ) { uri ->
                if (uri == null) return@rememberLauncherForActivityResult

                if (!settings.postSubmitContentEnabled) {
                    Toast.makeText(
                                    context,
                                    "Enable post-submit content first.",
                                    Toast.LENGTH_SHORT
                            )
                            .show()
                    return@rememberLauncherForActivityResult
                }

                if (settings.postSubmitPdfs.size >= AgentFormContactSettingsManager.MAX_POST_SUBMIT_PDFS) {
                    Toast.makeText(
                                    context,
                                    "Maximum 2 PDFs allowed.",
                                    Toast.LENGTH_SHORT
                            )
                            .show()
                    return@rememberLauncherForActivityResult
                }

                scope.launch {
                    isUploadingPdf = true
                    try {
                        val uploadedPdf = uploadPdfToCloudflare(context, settingsManager, uri)
                        settings = settingsManager.addOrReplacePostSubmitPdf(uploadedPdf)
                        Toast.makeText(
                                        context,
                                        "PDF uploaded successfully.",
                                        Toast.LENGTH_SHORT
                                )
                                .show()
                    } catch (e: Exception) {
                        Toast.makeText(
                                        context,
                                        e.message ?: "PDF upload failed.",
                                        Toast.LENGTH_LONG
                                )
                                .show()
                    } finally {
                        isUploadingPdf = false
                    }
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
                                    containerColor = MaterialTheme.colorScheme.primaryContainer
                            )
            ) {
                Column(
                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                                text = "AgentForm Studio",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                        )
                    }
                    Text(
                            text = "Create dynamic forms, add verification, and share instantly.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Button(onClick = onCreateNew) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Create New Form")
                    }
                }
            }
        }

        item {
            VerifyContactsSettingsCard(
                    settings = settings,
                    nameInput = nameInput,
                    onNameInputChange = { nameInput = it },
                    phoneInput = phoneInput,
                    onPhoneInputChange = { phoneInput = it },
                    onToggleEnabled = { enabled ->
                        settingsManager.setEnabled(enabled)
                        settings = settingsManager.getSettings()
                    },
                    onAddContact = {
                        settings = settingsManager.addOrUpdateContact(nameInput, phoneInput)
                        nameInput = ""
                        phoneInput = ""
                    },
                    onDeleteContact = { contactId ->
                        settings = settingsManager.removeContact(contactId)
                    },
                    onClearContacts = {
                        settings = settingsManager.clearContacts()
                    }
            )
        }

        item {
            PostSubmitContentSettingsCard(
                    settings = settings,
                    videoUrlInput = videoUrlInput,
                    onVideoUrlInputChange = { videoUrlInput = it },
                    isUploadingPdf = isUploadingPdf,
                    onToggleEnabled = { enabled ->
                        settings = settingsManager.setPostSubmitContentEnabled(enabled)
                    },
                    onSaveVideoUrl = {
                        settings = settingsManager.setPostSubmitVideoUrl(videoUrlInput)
                        Toast.makeText(context, "Video link saved.", Toast.LENGTH_SHORT).show()
                    },
                    onUploadPdf = {
                        pdfPickerLauncher.launch(arrayOf("application/pdf"))
                    },
                    onDeletePdf = { pdfId ->
                        settings = settingsManager.removePostSubmitPdf(pdfId)
                    },
                    onClearPdfs = {
                        settings = settingsManager.clearPostSubmitPdfs()
                    }
            )
        }

        item {
            VerificationAutomationSettingsCard(
                    settings = settings,
                    reminderMessageInput = reminderMessageInput,
                    onReminderMessageInputChange = { reminderMessageInput = it },
                    verifiedMessageInput = verifiedMessageInput,
                    onVerifiedMessageInputChange = { verifiedMessageInput = it },
                    onToggleMonitor = { enabled ->
                        settings = settingsManager.setAutoStatusMonitorEnabled(enabled)
                    },
                    onToggleReminder = { enabled ->
                        settings = settingsManager.setAutoReminderEnabled(enabled)
                    },
                    onToggleFollowup = { enabled ->
                        settings = settingsManager.setAutoVerifiedFollowupEnabled(enabled)
                    },
                    onSaveMessages = {
                        settings = settingsManager.setReminderMessage(reminderMessageInput)
                        settings = settingsManager.setVerifiedMessage(verifiedMessageInput)
                        Toast.makeText(context, "Automation messages saved.", Toast.LENGTH_SHORT).show()
                    }
            )
        }

        item {
            Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                            text = "How It Works",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                    )
                    Text(
                            text = "1. Build or select a template form.",
                            style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                            text = "2. Contact settings apply only to Contact Picker templates.",
                            style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                            text = "3. Share link with users for verification.",
                            style = MaterialTheme.typography.bodyMedium
                    )
                    Row {
                        Icon(Icons.Default.Share, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                                text = "4. Enable automation card to auto-check open/verify and send follow-up.",
                                style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun VerifyContactsSettingsCard(
        settings: AgentFormContactSettings,
        nameInput: String,
        onNameInputChange: (String) -> Unit,
        phoneInput: String,
        onPhoneInputChange: (String) -> Unit,
        onToggleEnabled: (Boolean) -> Unit,
        onAddContact: () -> Unit,
        onDeleteContact: (String) -> Unit,
        onClearContacts: () -> Unit
) {
    val canAdd = nameInput.trim().isNotBlank() && phoneInput.replace(Regex("[^0-9]"), "").isNotBlank()

    Card(
            colors =
                    CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                    )
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                    text = "Card 1: Contact Verification Settings",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    fontWeight = FontWeight.SemiBold
            )

            Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                            text = "Enable Contact Verify",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium
                    )
                    Text(
                            text = "Users must verify contact against this saved list.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.85f)
                    )
                }
                Switch(
                        checked = settings.enabled,
                        onCheckedChange = onToggleEnabled
                )
            }

            OutlinedTextField(
                    value = nameInput,
                    onValueChange = onNameInputChange,
                    label = { Text("Contact Name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
            )

            OutlinedTextField(
                    value = phoneInput,
                    onValueChange = onPhoneInputChange,
                    label = { Text("Phone Number") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
            )

            Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                        onClick = onAddContact,
                        enabled = canAdd,
                        modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Add Contact")
                }
                TextButton(
                        onClick = onClearContacts,
                        enabled = settings.contacts.isNotEmpty(),
                        modifier = Modifier.weight(1f)
                ) {
                    Text("Clear All")
                }
            }

            if (settings.contacts.isEmpty()) {
                Text(
                        text = "No contacts added yet.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.85f)
                )
            } else {
                Text(
                        text = "Saved Contacts (${settings.contacts.size})",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                )

                settings.contacts.forEachIndexed { index, contact ->
                    Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                    text = contact.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                            )
                            Text(
                                    text = contact.phone,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.85f)
                            )
                        }
                        Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete contact",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(6.dp)
                        )
                        TextButton(onClick = { onDeleteContact(contact.id) }) { Text("Delete") }
                    }

                    if (index < settings.contacts.lastIndex) {
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun PostSubmitContentSettingsCard(
        settings: AgentFormContactSettings,
        videoUrlInput: String,
        onVideoUrlInputChange: (String) -> Unit,
        isUploadingPdf: Boolean,
        onToggleEnabled: (Boolean) -> Unit,
        onSaveVideoUrl: () -> Unit,
        onUploadPdf: () -> Unit,
        onDeletePdf: (String) -> Unit,
        onClearPdfs: () -> Unit
) {
    val hasCapacity = settings.postSubmitPdfs.size < AgentFormContactSettingsManager.MAX_POST_SUBMIT_PDFS

    Card(
            colors =
                    CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer
                    )
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                    text = "Card 2: Post-Submit Content Page",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    fontWeight = FontWeight.SemiBold
            )

            Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                            text = "Enable Content Page After Submit",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium
                    )
                    Text(
                            text = "Applies only on Contact Picker templates after verification submit.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.85f)
                    )
                }
                Switch(
                        checked = settings.postSubmitContentEnabled,
                        onCheckedChange = onToggleEnabled
                )
            }

            if (settings.postSubmitContentEnabled) {
                OutlinedTextField(
                        value = videoUrlInput,
                        onValueChange = onVideoUrlInputChange,
                        label = { Text("YouTube Video Link") },
                        placeholder = { Text("https://www.youtube.com/watch?v=...") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                )

                Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                            onClick = onSaveVideoUrl,
                            modifier = Modifier.weight(1f)
                    ) {
                        Text("Save Video")
                    }
                    TextButton(
                            onClick = { onVideoUrlInputChange("") },
                            modifier = Modifier.weight(1f)
                    ) {
                        Text("Clear Input")
                    }
                }

                Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                            onClick = onUploadPdf,
                            enabled = !isUploadingPdf && hasCapacity,
                            modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.UploadFile, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                                if (isUploadingPdf) "Uploading..."
                                else "Upload PDF"
                        )
                    }
                    TextButton(
                            onClick = onClearPdfs,
                            enabled = settings.postSubmitPdfs.isNotEmpty(),
                            modifier = Modifier.weight(1f)
                    ) {
                        Text("Clear PDFs")
                    }
                }

                Text(
                        text = "Max 2 PDFs, 10 MB each. Files upload to Cloudflare R2 storage.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.85f)
                )

                if (!hasCapacity) {
                    Text(
                            text = "PDF limit reached. Remove one file to upload another.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                    )
                }

                if (settings.postSubmitPdfs.isEmpty()) {
                    Text(
                            text = "No PDF uploaded yet.",
                            style = MaterialTheme.typography.bodySmall
                    )
                } else {
                    settings.postSubmitPdfs.forEachIndexed { index, pdf ->
                        Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                        text = pdf.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium
                                )
                                Text(
                                        text = formatFileSize(pdf.sizeBytes),
                                        style = MaterialTheme.typography.bodySmall
                                )
                            }
                            TextButton(onClick = { onDeletePdf(pdf.id) }) { Text("Remove") }
                        }

                        if (index < settings.postSubmitPdfs.lastIndex) {
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VerificationAutomationSettingsCard(
        settings: AgentFormContactSettings,
        reminderMessageInput: String,
        onReminderMessageInputChange: (String) -> Unit,
        verifiedMessageInput: String,
        onVerifiedMessageInputChange: (String) -> Unit,
        onToggleMonitor: (Boolean) -> Unit,
        onToggleReminder: (Boolean) -> Unit,
        onToggleFollowup: (Boolean) -> Unit,
        onSaveMessages: () -> Unit
) {
    Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
                modifier = Modifier.fillMaxWidth().padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                    text = "Card 3: AI Verification Automation",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
            )
            Text(
                    text = "Works only for Contact Picker templates shared by AI agent.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f)
            )

            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("AI Status Monitor", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                    Text(
                            "After link send, AI checks form open/verify state.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f)
                    )
                }
                Switch(checked = settings.autoStatusMonitorEnabled, onCheckedChange = onToggleMonitor)
            }

            if (settings.autoStatusMonitorEnabled) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Auto Reminder", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        Text(
                                "If not verified, AI sends reminder message.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f)
                        )
                    }
                    Switch(checked = settings.autoReminderEnabled, onCheckedChange = onToggleReminder)
                }

                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Auto Follow-up", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        Text(
                                "After verification, send configured PDF/video link.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f)
                        )
                    }
                    Switch(
                            checked = settings.autoVerifiedFollowupEnabled,
                            onCheckedChange = onToggleFollowup
                    )
                }

                OutlinedTextField(
                        value = reminderMessageInput,
                        onValueChange = onReminderMessageInputChange,
                        label = { Text("Custom Reminder Message (optional)") },
                        modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                        value = verifiedMessageInput,
                        onValueChange = onVerifiedMessageInputChange,
                        label = { Text("Custom Verified Message (optional)") },
                        modifier = Modifier.fillMaxWidth()
                )

                Button(onClick = onSaveMessages, modifier = Modifier.fillMaxWidth()) {
                    Text("Save Automation Messages")
                }
            }
        }
    }
}

private data class UploadIdentity(
        val uid: String,
        val phone: String
)

private data class LocalPdfPayload(
        val fileName: String,
        val sizeBytes: Long,
        val bytes: ByteArray
)

private suspend fun uploadPdfToCloudflare(
        context: Context,
        settingsManager: AgentFormContactSettingsManager,
        uri: Uri
): AgentFormPostSubmitPdf = withContext(Dispatchers.IO) {
    val identity = resolveUploadIdentity(context, settingsManager)
            ?: throw IllegalStateException("Login or phone number missing.")
    val payload = readPdfPayload(context, uri)

    val requestBody =
            MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("uid", identity.uid)
                    .addFormDataPart("phone", identity.phone)
                    .addFormDataPart(
                            "file",
                            payload.fileName,
                            payload.bytes.toRequestBody("application/pdf".toMediaType())
                    )
                    .build()

    val request =
            Request.Builder()
                    .url("https://chataiform.com/api/upload-resource")
                    .post(requestBody)
                    .build()

    NETWORK_CLIENT.newCall(request).execute().use { response ->
        if (!response.isSuccessful) {
            val serverError = response.body?.string().orEmpty()
            throw IllegalStateException("Upload failed (${response.code}): $serverError")
        }

        val json = JSONObject(response.body?.string().orEmpty().ifBlank { "{}" })
        val url = json.optString("url").trim()
        if (url.isBlank()) {
            throw IllegalStateException("Upload failed: file URL not returned.")
        }

        val id = json.optString("key").trim().ifBlank { UUID.randomUUID().toString() }
        val name = json.optString("name").trim().ifBlank { payload.fileName }
        val sizeBytes = json.optLong("sizeBytes", payload.sizeBytes)
        AgentFormPostSubmitPdf(
                id = id,
                name = name,
                url = url,
                sizeBytes = sizeBytes
        )
    }
}

private fun resolveUploadIdentity(
        context: Context,
        settingsManager: AgentFormContactSettingsManager
): UploadIdentity? {
    val uid = FirebaseAuth.getInstance().currentUser?.uid.orEmpty().trim()
    val phone = settingsManager.sanitizePhone(UserDetailsPreferences(context).getPhoneNumber().orEmpty())
    if (uid.isBlank() || phone.isBlank()) return null
    return UploadIdentity(uid = uid, phone = phone)
}

private fun readPdfPayload(context: Context, uri: Uri): LocalPdfPayload {
    val resolver = context.contentResolver
    var fileName = "document.pdf"
    var fileSizeBytes = -1L

    resolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
            null,
            null,
            null
    )?.use { cursor ->
        val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
        if (cursor.moveToFirst()) {
            if (nameIdx >= 0) {
                fileName = cursor.getString(nameIdx).orEmpty().ifBlank { fileName }
            }
            if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) {
                fileSizeBytes = cursor.getLong(sizeIdx)
            }
        }
    }

    if (!fileName.lowercase(Locale.ROOT).endsWith(".pdf")) {
        throw IllegalStateException("Only PDF files are allowed.")
    }

    if (fileSizeBytes > AgentFormContactSettingsManager.MAX_POST_SUBMIT_PDF_SIZE_BYTES) {
        throw IllegalStateException("PDF size must be 10 MB or less.")
    }

    val bytes = resolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw IllegalStateException("Unable to read PDF file.")
    if (bytes.isEmpty()) {
        throw IllegalStateException("Selected PDF is empty.")
    }
    if (bytes.size.toLong() > AgentFormContactSettingsManager.MAX_POST_SUBMIT_PDF_SIZE_BYTES) {
        throw IllegalStateException("PDF size must be 10 MB or less.")
    }

    if (fileSizeBytes <= 0L) {
        fileSizeBytes = bytes.size.toLong()
    }

    return LocalPdfPayload(
            fileName = fileName,
            sizeBytes = fileSizeBytes,
            bytes = bytes
    )
}

private fun formatFileSize(sizeBytes: Long): String {
    if (sizeBytes <= 0L) return "Unknown size"
    val sizeMb = sizeBytes / (1024.0 * 1024.0)
    return String.format(Locale.US, "%.2f MB", sizeMb)
}

private val NETWORK_CLIENT = OkHttpClient()
