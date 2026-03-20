package com.message.bulksend.autorespond.documentreply

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddDocumentReplyScreen(
    documentManager: DocumentReplyManager,
    imagePickerLauncher: androidx.activity.compose.ManagedActivityResultLauncher<String, Uri?>,
    videoPickerLauncher: androidx.activity.compose.ManagedActivityResultLauncher<String, Uri?>,
    pdfPickerLauncher: androidx.activity.compose.ManagedActivityResultLauncher<String, Uri?>,
    audioPickerLauncher: androidx.activity.compose.ManagedActivityResultLauncher<String, Uri?>,
    permissionLauncher: androidx.activity.compose.ManagedActivityResultLauncher<Array<String>, Map<String, Boolean>>,
    onBackPressed: () -> Unit,
    onSave: (DocumentReplyData) -> Unit,
    onDocumentAdded: (DocumentFile) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    var keyword by remember { mutableStateOf("") }
    var selectedDocuments by remember { mutableStateOf<List<DocumentFile>>(emptyList()) }
    var matchOption by remember { mutableStateOf("exact") }
    var minWordMatch by remember { mutableStateOf(1) }
    var showDocumentPicker by remember { mutableStateOf(false) }
    var showPermissionCheck by remember { mutableStateOf(false) }
    
    // Permission handler
    val permissionReply = remember { DocumentPermissionReply(context) }
    
    // Get all available documents (mixed types)
    val allAvailableDocuments = remember {
        val images = documentManager.getDocumentsByType(DocumentType.IMAGE)
        val videos = documentManager.getDocumentsByType(DocumentType.VIDEO)
        val pdfs = documentManager.getDocumentsByType(DocumentType.PDF)
        val audios = documentManager.getDocumentsByType(DocumentType.AUDIO)
        images + videos + pdfs + audios
    }
    
    // Custom file picker launchers that update selectedDocuments
    val customImagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            scope.launch {
                android.util.Log.d("DocumentReply", "📸 Image picked in AddScreen: $uri")
                val success = documentManager.saveDocument(it, DocumentType.IMAGE)
                if (success) {
                    // Get the newly added document and add to selectedDocuments
                    val newDocuments = documentManager.getDocumentsByType(DocumentType.IMAGE)
                    val latestDoc = newDocuments.maxByOrNull { it.id }
                    latestDoc?.let { doc ->
                        selectedDocuments = selectedDocuments + doc
                        android.util.Log.d("DocumentReply", "✅ Image added to selectedDocuments: ${doc.originalName}")
                    }
                    onDocumentAdded(latestDoc ?: return@launch)
                    android.widget.Toast.makeText(context, "✅ Image added successfully!", android.widget.Toast.LENGTH_SHORT).show()
                } else {
                    android.widget.Toast.makeText(context, "❌ Failed to add image", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    val customVideoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            scope.launch {
                android.util.Log.d("DocumentReply", "🎥 Video picked in AddScreen: $uri")
                val success = documentManager.saveDocument(it, DocumentType.VIDEO)
                if (success) {
                    // Get the newly added document and add to selectedDocuments
                    val newDocuments = documentManager.getDocumentsByType(DocumentType.VIDEO)
                    val latestDoc = newDocuments.maxByOrNull { it.id }
                    latestDoc?.let { doc ->
                        selectedDocuments = selectedDocuments + doc
                        android.util.Log.d("DocumentReply", "✅ Video added to selectedDocuments: ${doc.originalName}")
                    }
                    onDocumentAdded(latestDoc ?: return@launch)
                    android.widget.Toast.makeText(context, "✅ Video added successfully!", android.widget.Toast.LENGTH_SHORT).show()
                } else {
                    android.widget.Toast.makeText(context, "❌ Failed to add video", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    val customPdfPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            scope.launch {
                android.util.Log.d("DocumentReply", "📄 PDF picked in AddScreen: $uri")
                val success = documentManager.saveDocument(it, DocumentType.PDF)
                if (success) {
                    // Get the newly added document and add to selectedDocuments
                    val newDocuments = documentManager.getDocumentsByType(DocumentType.PDF)
                    val latestDoc = newDocuments.maxByOrNull { it.id }
                    latestDoc?.let { doc ->
                        selectedDocuments = selectedDocuments + doc
                        android.util.Log.d("DocumentReply", "✅ PDF added to selectedDocuments: ${doc.originalName}")
                    }
                    onDocumentAdded(latestDoc ?: return@launch)
                    android.widget.Toast.makeText(context, "✅ PDF added successfully!", android.widget.Toast.LENGTH_SHORT).show()
                } else {
                    android.widget.Toast.makeText(context, "❌ Failed to add PDF", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    val customAudioPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            scope.launch {
                android.util.Log.d("DocumentReply", "🎵 Audio picked in AddScreen: $uri")
                val success = documentManager.saveDocument(it, DocumentType.AUDIO)
                if (success) {
                    // Get the newly added document and add to selectedDocuments
                    val newDocuments = documentManager.getDocumentsByType(DocumentType.AUDIO)
                    val latestDoc = newDocuments.maxByOrNull { it.id }
                    latestDoc?.let { doc ->
                        selectedDocuments = selectedDocuments + doc
                        android.util.Log.d("DocumentReply", "✅ Audio added to selectedDocuments: ${doc.originalName}")
                    }
                    onDocumentAdded(latestDoc ?: return@launch)
                    android.widget.Toast.makeText(context, "✅ Audio added successfully!", android.widget.Toast.LENGTH_SHORT).show()
                } else {
                    android.widget.Toast.makeText(context, "❌ Failed to add audio", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    // Multiple file picker launcher for mixed types
    val customMultiplePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNotEmpty()) {
            scope.launch {
                android.util.Log.d("DocumentReply", "📎 Multiple files picked: ${uris.size} files")
                var successCount = 0
                
                for (uri in uris) {
                    // Determine document type from URI
                    val mimeType = context.contentResolver.getType(uri)
                    val documentType = when {
                        mimeType?.startsWith("image/") == true -> DocumentType.IMAGE
                        mimeType?.startsWith("video/") == true -> DocumentType.VIDEO
                        mimeType?.startsWith("audio/") == true -> DocumentType.AUDIO
                        mimeType == "application/pdf" -> DocumentType.PDF
                        else -> {
                            android.util.Log.w("DocumentReply", "⚠️ Unknown file type: $mimeType for $uri")
                            continue
                        }
                    }
                    
                    val success = documentManager.saveDocument(uri, documentType)
                    if (success) {
                        // Get the newly added document and add to selectedDocuments
                        val newDocuments = documentManager.getDocumentsByType(documentType)
                        val latestDoc = newDocuments.maxByOrNull { it.id }
                        latestDoc?.let { doc ->
                            selectedDocuments = selectedDocuments + doc
                            android.util.Log.d("DocumentReply", "✅ ${documentType.name} added: ${doc.originalName}")
                        }
                        successCount++
                    }
                }
                
                if (successCount > 0) {
                    onDocumentAdded(DocumentFile("", "", "", DocumentType.IMAGE, 0)) // Dummy call for refresh
                    android.widget.Toast.makeText(context, "✅ $successCount files added successfully!", android.widget.Toast.LENGTH_SHORT).show()
                } else {
                    android.widget.Toast.makeText(context, "❌ Failed to add files", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    val backgroundBrush = Brush.verticalGradient(
        colors = listOf(Color(0xFF0f0c29), Color(0xFF302b63), Color(0xFF24243e))
    )
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text("Add Document Reply", color = Color(0xFF00D4FF), fontWeight = FontWeight.Bold)
                },
                navigationIcon = {
                    IconButton(onClick = onBackPressed) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color(0xFF00D4FF))
                    }
                },
                actions = {
                    TextButton(
                        onClick = {
                            if (keyword.isNotBlank() && selectedDocuments.isNotEmpty()) {
                                // Show permission check instead of direct save
                                showPermissionCheck = true
                            }
                        },
                        enabled = keyword.isNotBlank() && selectedDocuments.isNotEmpty()
                    ) {
                        Text(
                            "Save",
                            color = if (keyword.isNotBlank() && selectedDocuments.isNotEmpty()) 
                                Color(0xFF00D4FF) else Color(0xFF64748B),
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF1a1a2e))
            )
        },
        containerColor = Color.Transparent
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(backgroundBrush)
                .padding(padding)
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // Keyword Input
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1a1a2e))
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Text(
                                "Keyword",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                modifier = Modifier.padding(bottom = 12.dp)
                            )
                            OutlinedTextField(
                                value = keyword,
                                onValueChange = { keyword = it },
                                label = { Text("Enter keyword") },
                                placeholder = { Text("e.g., hi, hello, catalog") },
                                modifier = Modifier.fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color(0xFF00D4FF),
                                    unfocusedBorderColor = Color(0xFF64748B),
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedLabelColor = Color(0xFF00D4FF),
                                    unfocusedLabelColor = Color(0xFF94A3B8)
                                )
                            )
                        }
                    }
                }
                
                // Add Document Buttons (Outside Card)
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Multiple files picker button (mixed types)
                        Button(
                            onClick = {
                                checkPermissionAndLaunchPicker(context, permissionLauncher) {
                                    customMultiplePickerLauncher.launch("*/*")
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF00D4FF)
                            ),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
                        ) {
                            Icon(
                                Icons.Default.Add,
                                contentDescription = "Add Multiple Files",
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Add Multiple Files (Images, Videos, PDFs, Audios)", fontSize = 14.sp)
                        }
                        
                        // Individual file type buttons (horizontally scrollable)
                        LazyRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(horizontal = 4.dp)
                        ) {
                            // Add Image button
                            item {
                                OutlinedButton(
                                    onClick = {
                                        checkPermissionAndLaunchPicker(context, permissionLauncher) {
                                            customImagePickerLauncher.launch("image/*")
                                        }
                                    },
                                    modifier = Modifier.width(90.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = Color(0xFF4CAF50)
                                    ),
                                    border = BorderStroke(1.dp, Color(0xFF4CAF50)),
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp)
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Icon(
                                            Icons.Default.Image,
                                            contentDescription = "Add Image",
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text("Image", fontSize = 10.sp)
                                    }
                                }
                            }
                            
                            // Add Video button
                            item {
                                OutlinedButton(
                                    onClick = {
                                        checkPermissionAndLaunchPicker(context, permissionLauncher) {
                                            customVideoPickerLauncher.launch("video/*")
                                        }
                                    },
                                    modifier = Modifier.width(90.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = Color(0xFF2196F3)
                                    ),
                                    border = BorderStroke(1.dp, Color(0xFF2196F3)),
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp)
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Icon(
                                            Icons.Default.VideoFile,
                                            contentDescription = "Add Video",
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text("Video", fontSize = 10.sp)
                                    }
                                }
                            }
                            
                            // Add PDF button
                            item {
                                OutlinedButton(
                                    onClick = {
                                        checkPermissionAndLaunchPicker(context, permissionLauncher) {
                                            customPdfPickerLauncher.launch("application/pdf")
                                        }
                                    },
                                    modifier = Modifier.width(90.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = Color(0xFFFF5722)
                                    ),
                                    border = BorderStroke(1.dp, Color(0xFFFF5722)),
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp)
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Icon(
                                            Icons.Default.PictureAsPdf,
                                            contentDescription = "Add PDF",
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text("PDF", fontSize = 10.sp)
                                    }
                                }
                            }
                            
                            // Add Audio button
                            item {
                                OutlinedButton(
                                    onClick = {
                                        checkPermissionAndLaunchPicker(context, permissionLauncher) {
                                            customAudioPickerLauncher.launch("audio/*")
                                        }
                                    },
                                    modifier = Modifier.width(90.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = Color(0xFF9C27B0)
                                    ),
                                    border = BorderStroke(1.dp, Color(0xFF9C27B0)),
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp)
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Icon(
                                            Icons.Default.AudioFile,
                                            contentDescription = "Add Audio",
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text("Audio", fontSize = 10.sp)
                                    }
                                }
                            }
                        }
                        
                        // Select from saved documents button
                        if (allAvailableDocuments.isNotEmpty()) {
                            OutlinedButton(
                                onClick = { showDocumentPicker = true },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = Color(0xFF00D4FF)
                                ),
                                border = BorderStroke(1.dp, Color(0xFF00D4FF)),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)
                            ) {
                                Icon(
                                    Icons.Default.Folder,
                                    contentDescription = "Select Saved",
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Select from Saved Documents", fontSize = 13.sp)
                            }
                        }
                    }
                }
                
                // Document Selection Card
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1a1a2e))
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Text(
                                "Selected Documents (${selectedDocuments.size})",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                modifier = Modifier.padding(bottom = 12.dp)
                            )
                            
                            // Show selected documents
                            if (selectedDocuments.isNotEmpty()) {
                                LazyColumn(
                                    modifier = Modifier.heightIn(max = 200.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    items(selectedDocuments) { doc ->
                                        Card(
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(8.dp),
                                            colors = CardDefaults.cardColors(containerColor = Color(0xFF2a2a3e))
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(12.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        doc.originalName,
                                                        fontSize = 14.sp,
                                                        color = Color.White,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                    Text(
                                                        documentManager.formatFileSize(doc.fileSize),
                                                        fontSize = 12.sp,
                                                        color = Color(0xFF94A3B8)
                                                    )
                                                }
                                                IconButton(
                                                    onClick = {
                                                        selectedDocuments = selectedDocuments.filter { it.id != doc.id }
                                                    },
                                                    modifier = Modifier.size(32.dp)
                                                ) {
                                                    Icon(
                                                        Icons.Default.Close,
                                                        contentDescription = "Remove",
                                                        tint = Color(0xFFEF4444),
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            } else {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(8.dp),
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFF2a2a3e))
                                ) {
                                    Column(
                                        modifier = Modifier.padding(24.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Icon(
                                            Icons.Default.Description,
                                            contentDescription = null,
                                            tint = Color(0xFF64748B),
                                            modifier = Modifier.size(32.dp)
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            "No documents selected",
                                            fontSize = 14.sp,
                                            color = Color(0xFF94A3B8)
                                        )
                                        Text(
                                            "Add files or select from saved documents",
                                            fontSize = 12.sp,
                                            color = Color(0xFF64748B)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                
                // Match Option
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1a1a2e))
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Text(
                                "Match Option",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                modifier = Modifier.padding(bottom = 12.dp)
                            )
                            
                            Column {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .selectable(
                                            selected = matchOption == "exact",
                                            onClick = { matchOption = "exact" }
                                        )
                                        .padding(vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = matchOption == "exact",
                                        onClick = { matchOption = "exact" },
                                        colors = RadioButtonDefaults.colors(
                                            selectedColor = Color(0xFF00D4FF)
                                        )
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text("Exact Match", color = Color.White, fontWeight = FontWeight.SemiBold)
                                        Text("Message must match keyword exactly", color = Color(0xFF94A3B8), fontSize = 12.sp)
                                    }
                                }
                                
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .selectable(
                                            selected = matchOption == "contains",
                                            onClick = { matchOption = "contains" }
                                        )
                                        .padding(vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = matchOption == "contains",
                                        onClick = { matchOption = "contains" },
                                        colors = RadioButtonDefaults.colors(
                                            selectedColor = Color(0xFF00D4FF)
                                        )
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text("Contains", color = Color.White, fontWeight = FontWeight.SemiBold)
                                        Text("Message contains keyword words", color = Color(0xFF94A3B8), fontSize = 12.sp)
                                    }
                                }
                            }
                            
                            // Min Word Match (only for contains)
                            if (matchOption == "contains") {
                                Spacer(modifier = Modifier.height(12.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        "Min Words to Match:",
                                        color = Color.White,
                                        modifier = Modifier.weight(1f)
                                    )
                                    
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        IconButton(
                                            onClick = { if (minWordMatch > 1) minWordMatch-- }
                                        ) {
                                            Icon(Icons.Default.Remove, contentDescription = "Decrease", tint = Color(0xFF00D4FF))
                                        }
                                        
                                        Text(
                                            minWordMatch.toString(),
                                            color = Color.White,
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 16.dp)
                                        )
                                        
                                        IconButton(
                                            onClick = { minWordMatch++ }
                                        ) {
                                            Icon(Icons.Default.Add, contentDescription = "Increase", tint = Color(0xFF00D4FF))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                
                // Add bottom padding
                item {
                    Spacer(modifier = Modifier.height(20.dp))
                }
            }
        }
    }
    
    // Permission check dialog
    if (showPermissionCheck) {
        DocumentReplyPermissionHandler(
            permissionReply = permissionReply,
            onSave = {
                // All permissions granted, proceed with save
                val reply = DocumentReplyData(
                    keyword = keyword.trim(),
                    documents = selectedDocuments,
                    matchOption = matchOption,
                    minWordMatch = minWordMatch
                )
                onSave(reply)
                showPermissionCheck = false
            },
            onCancel = {
                // User disagreed or permissions denied
                showPermissionCheck = false
                android.widget.Toast.makeText(
                    context, 
                    "⚠️ Permissions required for Document Reply to work", 
                    android.widget.Toast.LENGTH_LONG
                ).show()
            }
        )
    }
    
    // Document Picker Dialog
    if (showDocumentPicker) {
        DocumentPickerDialog(
            documents = allAvailableDocuments,
            selectedDocuments = selectedDocuments,
            onDismiss = { showDocumentPicker = false },
            onSelectionChanged = { selectedDocuments = it }
        )
    }
}

@Composable
fun DocumentPickerDialog(
    documents: List<DocumentFile>,
    selectedDocuments: List<DocumentFile>,
    onDismiss: () -> Unit,
    onSelectionChanged: (List<DocumentFile>) -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1a1a2e))
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    "Select Documents",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                
                if (documents.isEmpty()) {
                    Text(
                        "No documents available. Please add documents first.",
                        color = Color(0xFF94A3B8),
                        modifier = Modifier.padding(vertical = 16.dp)
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 300.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(documents) { doc ->
                            val isSelected = selectedDocuments.any { it.id == doc.id }
                            
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .selectable(
                                        selected = isSelected,
                                        onClick = {
                                            val newSelection = if (isSelected) {
                                                selectedDocuments.filter { it.id != doc.id }
                                            } else {
                                                selectedDocuments + doc
                                            }
                                            onSelectionChanged(newSelection)
                                        }
                                    )
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = isSelected,
                                    onCheckedChange = null,
                                    colors = CheckboxDefaults.colors(
                                        checkedColor = Color(0xFF00D4FF)
                                    )
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        doc.originalName,
                                        color = Color.White,
                                        fontSize = 14.sp
                                    )
                                    Text(
                                        DocumentReplyManager(androidx.compose.ui.platform.LocalContext.current)
                                            .formatFileSize(doc.fileSize),
                                        color = Color(0xFF94A3B8),
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                    }
                }
                
                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = Color(0xFF94A3B8)
                        )
                    ) {
                        Text("Cancel")
                    }
                    
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF00D4FF)
                        )
                    ) {
                        Text("Done")
                    }
                }
            }
        }
    }
}

private fun checkPermissionAndLaunchPicker(
    context: Context,
    permissionLauncher: ManagedActivityResultLauncher<Array<String>, Map<String, Boolean>>,
    onPermissionGranted: () -> Unit
) {
    val permissions = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
        // Android 13+ doesn't need READ_EXTERNAL_STORAGE for file picker
        arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE)
    } else {
        arrayOf(
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        )
    }
    
    val allPermissionsGranted = permissions.all { permission ->
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }
    
    if (allPermissionsGranted || android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
        android.util.Log.d("DocumentReply", "✅ Permissions granted, launching file picker")
        onPermissionGranted()
    } else {
        android.util.Log.d("DocumentReply", "❌ Permissions needed, requesting permissions")
        permissionLauncher.launch(permissions)
    }
}