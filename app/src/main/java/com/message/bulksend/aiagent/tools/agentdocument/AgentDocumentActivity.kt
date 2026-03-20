package com.message.bulksend.aiagent.tools.agentdocument

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.message.bulksend.autorespond.documentreply.DocumentFile
import com.message.bulksend.autorespond.documentreply.DocumentReplyData
import com.message.bulksend.autorespond.documentreply.DocumentReplyManager
import com.message.bulksend.autorespond.documentreply.DocumentType
import com.message.bulksend.ui.theme.BulksendTestTheme
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class AgentDocumentActivity : ComponentActivity() {
    
    private lateinit var documentManager: AgentDocumentManager
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        documentManager = AgentDocumentManager(this)
        
        setContent {
            BulksendTestTheme {
                AgentDocumentScreen(
                    documentManager = documentManager,
                    onBackPressed = { finish() }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgentDocumentScreen(
    documentManager: AgentDocumentManager,
    onBackPressed: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val documentReplyManager = remember { DocumentReplyManager(context) }
    
    var showAddDialog by remember { mutableStateOf(false) }
    var showNameDialog by remember { mutableStateOf(false) }
    var showGroupDialog by remember { mutableStateOf(false) }
    var selectedMediaType by remember { mutableStateOf(MediaType.IMAGE) }
    var selectedFileUri by remember { mutableStateOf<Uri?>(null) }
    var documentName by remember { mutableStateOf("") }
    var documentDescription by remember { mutableStateOf("") }
    var showEditDialog by remember { mutableStateOf(false) }
    var editingDocument by remember { mutableStateOf<AgentDocument?>(null) }
    val documents by documentManager.getAllDocuments().collectAsState(initial = emptyList())
    
    // File picker launcher
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedFileUri = uri
            showNameDialog = true
        }
    }
    
    val backgroundBrush = Brush.verticalGradient(
        colors = listOf(
            Color(0xFF0f0c29),
            Color(0xFF302b63),
            Color(0xFF24243e),
            Color(0xFF0f0c29)
        )
    )
    
    Scaffold(
        topBar = {
            AgentDocumentTopBar(onBackPressed = onBackPressed)
        },
        floatingActionButton = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.End
            ) {
                SmallFloatingActionButton(
                    onClick = { showGroupDialog = true },
                    containerColor = Color(0xFF0EA5E9),
                    contentColor = Color.White
                ) {
                    Text("Grp", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
                FloatingActionButton(
                    onClick = { showAddDialog = true },
                    containerColor = Color(0xFF6366F1),
                    contentColor = Color.White
                ) {
                    Icon(Icons.Filled.Add, "Add Document")
                }
            }
        },
        containerColor = Color.Transparent
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(backgroundBrush)
                .padding(innerPadding)
        ) {
            if (documents.isEmpty()) {
                EmptyDocumentsView()
            } else {
                DocumentsList(
                    documents = documents,
                    onDocumentClick = { document ->
                        editingDocument = document
                        showEditDialog = true
                    },
                    onEditClick = { document ->
                        editingDocument = document
                        showEditDialog = true
                    },
                    onDeleteClick = { document ->
                        scope.launch {
                            documentManager.deleteDocument(document.id)
                            Toast.makeText(context, "Document deleted", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            }
        }
    }
    
    if (showAddDialog) {
        AddDocumentDialog(
            onDismiss = { showAddDialog = false },
            onMediaTypeSelected = { mediaType ->
                selectedMediaType = mediaType
                showAddDialog = false
                // Open file picker based on media type
                val mimeType = when (mediaType) {
                    MediaType.IMAGE -> "image/*"
                    MediaType.PDF -> "application/pdf"
                    MediaType.VIDEO -> "video/*"
                    MediaType.AUDIO -> "audio/*"
                }
                filePickerLauncher.launch(mimeType)
            }
        )
    }
    
    if (showNameDialog && selectedFileUri != null) {
        AddDocumentNameDialog(
            mediaType = selectedMediaType,
            onDismiss = {
                showNameDialog = false
                selectedFileUri = null
                documentName = ""
                documentDescription = ""
            },
            onConfirm = { name, description, tags ->
                scope.launch {
                    try {
                        val mimeType = context.contentResolver.getType(selectedFileUri!!) ?: ""
                        val result = documentManager.addDocument(
                            name = name,
                            description = description,
                            tags = tags,
                            sourceUri = selectedFileUri!!,
                            mimeType = mimeType
                        )
                        
                        if (result.isSuccess) {
                            Toast.makeText(context, "Document added successfully", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Failed to add document", Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                    
                    showNameDialog = false
                    selectedFileUri = null
                    documentName = ""
                    documentDescription = ""
                }
            }
        )
    }

    if (showEditDialog && editingDocument != null) {
        EditDocumentDialog(
            document = editingDocument!!,
            onDismiss = {
                showEditDialog = false
                editingDocument = null
            },
            onConfirm = { name, description, tags ->
                val currentDoc = editingDocument ?: return@EditDocumentDialog
                scope.launch {
                    val result = documentManager.updateDocument(
                        documentId = currentDoc.id,
                        name = name,
                        description = description,
                        tags = tags
                    )
                    if (result.isSuccess) {
                        Toast.makeText(context, "Document updated", Toast.LENGTH_SHORT).show()
                        showEditDialog = false
                        editingDocument = null
                    } else {
                        Toast.makeText(context, "Failed to update document", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        )
    }

    if (showGroupDialog) {
        CreateDocumentGroupDialog(
            documents = documents,
            onDismiss = { showGroupDialog = false },
            onSave = { keyword, selectedDocs, matchOption, minWordMatch ->
                scope.launch {
                    if (selectedDocs.isEmpty()) {
                        Toast.makeText(context, "Select at least one document", Toast.LENGTH_SHORT).show()
                        return@launch
                    }

                    val reply = DocumentReplyData(
                        keyword = keyword,
                        documents = selectedDocs.map { it.toReplyDocumentFile() },
                        matchOption = matchOption,
                        minWordMatch = minWordMatch,
                        isEnabled = true
                    )
                    documentReplyManager.saveDocumentReply(reply)
                    documentReplyManager.setDocumentReplyEnabled(true)
                    Toast.makeText(
                        context,
                        "Document group saved. It will auto-send on keyword match.",
                        Toast.LENGTH_SHORT
                    ).show()
                    showGroupDialog = false
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgentDocumentTopBar(onBackPressed: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        Color(0xFF1a1a2e),
                        Color(0xFF16213e),
                        Color(0xFF0f3460)
                    )
                )
            )
    ) {
        Spacer(modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars))
        
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBackPressed) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White
                )
            }
            
            Icon(
                Icons.Outlined.Folder,
                contentDescription = "Documents",
                tint = Color(0xFF6366F1),
                modifier = Modifier.size(28.dp)
            )
            
            Spacer(Modifier.width(12.dp))
            
            Text(
                "Agent Documents",
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                color = Color.White
            )
        }
    }
}

@Composable
fun EmptyDocumentsView() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(
                Icons.Outlined.FolderOpen,
                contentDescription = "No Documents",
                modifier = Modifier.size(80.dp),
                tint = Color(0xFF6366F1).copy(alpha = 0.5f)
            )
            Spacer(Modifier.height(16.dp))
            Text(
                "No Documents Yet",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Add media files for AI Agent to send",
                fontSize = 14.sp,
                color = Color(0xFF94A3B8),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

@Composable
fun DocumentsList(
    documents: List<AgentDocument>,
    onDocumentClick: (AgentDocument) -> Unit,
    onEditClick: (AgentDocument) -> Unit,
    onDeleteClick: (AgentDocument) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(documents) { document ->
            DocumentCard(
                document = document,
                onClick = { onDocumentClick(document) },
                onEditClick = { onEditClick(document) },
                onDeleteClick = { onDeleteClick(document) }
            )
        }
    }
}

@Composable
fun DocumentCard(
    document: AgentDocument,
    onClick: () -> Unit,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    var showDeleteDialog by remember { mutableStateOf(false) }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E2E).copy(alpha = 0.9f)),
        border = androidx.compose.foundation.BorderStroke(1.dp, getMediaTypeColor(document.mediaType).copy(alpha = 0.3f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Media Type Icon
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(getMediaTypeColor(document.mediaType).copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    getMediaTypeIcon(document.mediaType),
                    contentDescription = document.mediaType.displayName,
                    tint = getMediaTypeColor(document.mediaType),
                    modifier = Modifier.size(28.dp)
                )
            }
            
            Spacer(Modifier.width(16.dp))
            
            // Document Info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    document.name,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    document.description,
                    fontSize = 13.sp,
                    color = Color(0xFF94A3B8),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (document.tags.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Tags: ${document.tags}",
                        fontSize = 11.sp,
                        color = Color(0xFF60A5FA),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        document.mediaType.displayName,
                        fontSize = 11.sp,
                        color = getMediaTypeColor(document.mediaType)
                    )
                    Text("•", fontSize = 11.sp, color = Color(0xFF64748B))
                    Text(
                        formatFileSize(document.fileSize),
                        fontSize = 11.sp,
                        color = Color(0xFF64748B)
                    )
                }
            }
            
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onEditClick) {
                    Icon(
                        Icons.Outlined.Edit,
                        contentDescription = "Edit",
                        tint = Color(0xFF60A5FA),
                        modifier = Modifier.size(20.dp)
                    )
                }
                IconButton(onClick = { showDeleteDialog = true }) {
                    Icon(
                        Icons.Outlined.Delete,
                        contentDescription = "Delete",
                        tint = Color(0xFFFF6B6B),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
    
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Document?") },
            text = { Text("Are you sure you want to delete \"${document.name}\"?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteClick()
                        showDeleteDialog = false
                    }
                ) {
                    Text("Delete", color = Color(0xFFFF6B6B))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun AddDocumentDialog(
    onDismiss: () -> Unit,
    onMediaTypeSelected: (MediaType) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "Select Media Type",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                MediaType.values().forEach { mediaType ->
                    MediaTypeOption(
                        mediaType = mediaType,
                        onClick = { onMediaTypeSelected(mediaType) }
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun AddDocumentNameDialog(
    mediaType: MediaType,
    onDismiss: () -> Unit,
    onConfirm: (String, String, String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var tags by remember { mutableStateOf("") }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    getMediaTypeIcon(mediaType),
                    contentDescription = null,
                    tint = getMediaTypeColor(mediaType)
                )
                Text("Add ${mediaType.displayName}")
            }
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Document Name") },
                    placeholder = { Text("e.g., Product Brochure") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description") },
                    placeholder = { Text("Brief description for AI Agent") },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = tags,
                    onValueChange = { tags = it },
                    label = { Text("Tags (comma separated)") },
                    placeholder = { Text("brochure,pricing,product,clinic") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                
                Text(
                    "AI Agent can match these tags and auto-send relevant document",
                    fontSize = 12.sp,
                    color = Color(0xFF94A3B8),
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (name.isNotBlank() && description.isNotBlank()) {
                        onConfirm(name.trim(), description.trim(), tags.trim())
                    }
                },
                enabled = name.isNotBlank() && description.isNotBlank()
            ) {
                Text("Add Document")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateDocumentGroupDialog(
    documents: List<AgentDocument>,
    onDismiss: () -> Unit,
    onSave: (keyword: String, selected: List<AgentDocument>, matchOption: String, minWordMatch: Int) -> Unit
) {
    var keyword by remember { mutableStateOf("") }
    var matchOption by remember { mutableStateOf("contains") }
    var minWordMatch by remember { mutableStateOf(1) }
    var selectedIds by remember { mutableStateOf<Set<String>>(emptySet()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create Document Group") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = keyword,
                    onValueChange = { keyword = it },
                    label = { Text("Trigger Keyword") },
                    placeholder = { Text("e.g., brochure, pricing, clinic package") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    FilterChip(
                        selected = matchOption == "exact",
                        onClick = { matchOption = "exact" },
                        label = { Text("Exact") }
                    )
                    FilterChip(
                        selected = matchOption == "contains",
                        onClick = { matchOption = "contains" },
                        label = { Text("Contains") }
                    )
                }

                if (matchOption == "contains") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Min word match", modifier = Modifier.weight(1f))
                        IconButton(onClick = { if (minWordMatch > 1) minWordMatch-- }) {
                            Icon(Icons.Default.Remove, contentDescription = "Decrease")
                        }
                        Text(minWordMatch.toString(), fontWeight = FontWeight.Bold)
                        IconButton(onClick = { minWordMatch++ }) {
                            Icon(Icons.Default.Add, contentDescription = "Increase")
                        }
                    }
                }

                Text("Select Multiple Documents", fontWeight = FontWeight.SemiBold)
                if (documents.isEmpty()) {
                    Text("No documents available. Please add documents first.", color = Color(0xFF94A3B8))
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 220.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(documents) { doc ->
                            val selected = selectedIds.contains(doc.id)
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selectedIds = if (selected) {
                                            selectedIds - doc.id
                                        } else {
                                            selectedIds + doc.id
                                        }
                                    },
                                colors = CardDefaults.cardColors(
                                    containerColor = if (selected) Color(0xFF1E3A8A).copy(alpha = 0.25f) else Color(0xFF1F2937).copy(alpha = 0.2f)
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(
                                        checked = selected,
                                        onCheckedChange = null
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(doc.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(
                                            doc.mediaType.displayName,
                                            fontSize = 12.sp,
                                            color = Color(0xFF94A3B8)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val selectedDocs = documents.filter { selectedIds.contains(it.id) }
                    onSave(keyword.trim(), selectedDocs, matchOption, minWordMatch)
                },
                enabled = keyword.isNotBlank() && selectedIds.isNotEmpty()
            ) {
                Text("Save Group")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun EditDocumentDialog(
    document: AgentDocument,
    onDismiss: () -> Unit,
    onConfirm: (String, String, String) -> Unit
) {
    var name by remember(document.id) { mutableStateOf(document.name) }
    var description by remember(document.id) { mutableStateOf(document.description) }
    var tags by remember(document.id) { mutableStateOf(document.tags) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Outlined.Edit,
                    contentDescription = null,
                    tint = Color(0xFF60A5FA)
                )
                Text("Edit ${document.mediaType.displayName}")
            }
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Document Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description") },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = tags,
                    onValueChange = { tags = it },
                    label = { Text("Tags (comma separated)") },
                    placeholder = { Text("brochure,pricing,product,clinic") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name.trim(), description.trim(), tags.trim()) },
                enabled = name.isNotBlank() && description.isNotBlank()
            ) {
                Text("Save Changes")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun MediaTypeOption(
    mediaType: MediaType,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = getMediaTypeColor(mediaType).copy(alpha = 0.1f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                getMediaTypeIcon(mediaType),
                contentDescription = mediaType.displayName,
                tint = getMediaTypeColor(mediaType),
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.width(16.dp))
            Text(
                mediaType.displayName,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp
            )
        }
    }
}

// Helper functions
fun getMediaTypeIcon(mediaType: MediaType): ImageVector {
    return when (mediaType) {
        MediaType.IMAGE -> Icons.Outlined.Image
        MediaType.PDF -> Icons.Outlined.PictureAsPdf
        MediaType.VIDEO -> Icons.Outlined.VideoLibrary
        MediaType.AUDIO -> Icons.Outlined.AudioFile
    }
}

fun getMediaTypeColor(mediaType: MediaType): Color {
    return when (mediaType) {
        MediaType.IMAGE -> Color(0xFF10B981)
        MediaType.PDF -> Color(0xFFFF6B6B)
        MediaType.VIDEO -> Color(0xFF6366F1)
        MediaType.AUDIO -> Color(0xFFF59E0B)
    }
}

fun formatFileSize(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        else -> "${bytes / (1024 * 1024)} MB"
    }
}

private fun AgentDocument.toReplyDocumentFile(): DocumentFile {
    val type = when (mediaType) {
        MediaType.IMAGE -> DocumentType.IMAGE
        MediaType.PDF -> DocumentType.PDF
        MediaType.VIDEO -> DocumentType.VIDEO
        MediaType.AUDIO -> DocumentType.AUDIO
    }
    return DocumentFile(
        id = id,
        originalName = name.ifBlank { "document" },
        savedPath = filePath,
        documentType = type,
        fileSize = fileSize,
        createdAt = createdAt
    )
}
