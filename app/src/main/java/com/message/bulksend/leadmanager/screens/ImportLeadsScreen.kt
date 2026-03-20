package com.message.bulksend.leadmanager.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.message.bulksend.leadmanager.LeadManager
import com.message.bulksend.leadmanager.model.Lead
import com.message.bulksend.leadmanager.utils.LeadImporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.activity.compose.BackHandler

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportLeadsScreen(
    leadManager: LeadManager,
    onBack: () -> Unit,
    onImportComplete: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val leadImporter = remember { LeadImporter(context) }
    
    var isImporting by remember { mutableStateOf(false) }
    var importResult by remember { mutableStateOf<LeadImporter.ImportResult?>(null) }
    var selectedFileType by remember { mutableStateOf<String?>(null) }
    var previewLeads by remember { mutableStateOf<List<Lead>>(emptyList()) }
    var showPreview by remember { mutableStateOf(false) }
    var showErrorDialog by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            coroutineScope.launch {
                isImporting = true
                val result = withContext(Dispatchers.IO) {
                    leadImporter.importFromUri(it, selectedFileType ?: "Import")
                }
                importResult = result
                previewLeads = result.leads
                
                if (result.leads.isNotEmpty()) {
                    showPreview = true
                } else {
                    // Show error
                    errorMessage = if (result.errors.isNotEmpty()) {
                        result.errors.joinToString("\n")
                    } else {
                        "No valid leads found in file. Make sure file has Name and Phone columns."
                    }
                    showErrorDialog = true
                }
                isImporting = false
            }
        }
    }
    
    val backgroundBrush = Brush.verticalGradient(
        colors = listOf(Color(0xFF0f0c29), Color(0xFF302b63), Color(0xFF24243e))
    )
    
    // Handle back press
    BackHandler { 
        if (showPreview) {
            showPreview = false
        } else {
            onBack()
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Import Leads", color = Color(0xFF10B981), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color(0xFF10B981))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF1a1a2e))
            )
        },
        containerColor = Color.Transparent
    ) { padding ->
        if (showPreview) {
            // Preview Screen
            ImportPreviewScreen(
                leads = previewLeads,
                importResult = importResult!!,
                onConfirm = {
                    coroutineScope.launch {
                        val inserted = withContext(Dispatchers.IO) {
                            leadManager.addLeads(previewLeads)
                        }

                        if (inserted < previewLeads.size) {
                            errorMessage = "You can add only 5 leads on free plan.\n\nAdded: $inserted\nSkipped: ${previewLeads.size - inserted}\n\nUpgrade to Chatspromo Premium to continue."
                            showErrorDialog = true
                        } else {
                            onImportComplete()
                        }
                    }
                },
                onCancel = {
                    showPreview = false
                    previewLeads = emptyList()
                    importResult = null
                },
                modifier = Modifier
                    .fillMaxSize()
                    .background(backgroundBrush)
                    .padding(padding)
            )
        } else {
            // File Type Selection
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .background(backgroundBrush)
                    .padding(padding)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    Text(
                        "Select File Type",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Choose the format of your contact file",
                        fontSize = 14.sp,
                        color = Color(0xFF94A3B8)
                    )
                }
                
                item {
                    ImportOptionCard(
                        icon = Icons.Default.TableChart,
                        title = "Excel File",
                        description = "Import from .xls or .xlsx file",
                        supportedFormats = "XLS, XLSX",
                        color = Color(0xFF10B981),
                        onClick = {
                            selectedFileType = "Excel Import"
                            // Use */* to allow all Excel formats including xlsx
                            filePickerLauncher.launch("*/*")
                        }
                    )
                }
                
                item {
                    ImportOptionCard(
                        icon = Icons.Default.Description,
                        title = "CSV File",
                        description = "Import from comma-separated values file",
                        supportedFormats = "CSV, TXT",
                        color = Color(0xFF3B82F6),
                        onClick = {
                            selectedFileType = "CSV Import"
                            // Use */* to allow CSV and text files
                            filePickerLauncher.launch("*/*")
                        }
                    )
                }
                
                item {
                    ImportOptionCard(
                        icon = Icons.Default.ContactPage,
                        title = "VCF / vCard",
                        description = "Import from contact card file",
                        supportedFormats = "VCF",
                        color = Color(0xFFF59E0B),
                        onClick = {
                            selectedFileType = "VCF Import"
                            // Use */* to allow VCF files
                            filePickerLauncher.launch("*/*")
                        }
                    )
                }
                
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                    ImportInfoCard()
                }
            }
        }
        
        // Loading Overlay
        if (isImporting) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.7f)),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1a1a2e)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(color = Color(0xFF10B981))
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Importing contacts...", color = Color.White)
                    }
                }
            }
        }
        
        // Error Dialog
        if (showErrorDialog) {
            AlertDialog(
                onDismissRequest = { showErrorDialog = false },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Error, null, tint = Color(0xFFEF4444))
                        Spacer(Modifier.width(8.dp))
                        Text("Import Failed", color = Color(0xFFEF4444), fontWeight = FontWeight.Bold)
                    }
                },
                text = {
                    Text(errorMessage, color = Color(0xFF94A3B8))
                },
                confirmButton = {
                    TextButton(onClick = { showErrorDialog = false }) {
                        Text("OK", color = Color(0xFF10B981))
                    }
                },
                containerColor = Color(0xFF1a1a2e)
            )
        }
    }
}

@Composable
fun ImportOptionCard(
    icon: ImageVector,
    title: String,
    description: String,
    supportedFormats: String,
    color: Color,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1a1a2e)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .background(color.copy(alpha = 0.2f), RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(28.dp))
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Spacer(modifier = Modifier.height(4.dp))
                Text(description, fontSize = 13.sp, color = Color(0xFF94A3B8))
                Spacer(modifier = Modifier.height(4.dp))
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = color.copy(alpha = 0.2f)
                ) {
                    Text(
                        supportedFormats,
                        fontSize = 11.sp,
                        color = color,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }
            
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = Color(0xFF64748B)
            )
        }
    }
}

@Composable
fun ImportInfoCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1a1a2e).copy(alpha = 0.5f)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Info, contentDescription = null, tint = Color(0xFF3B82F6), modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("File Format Requirements", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                "• CSV/Excel: First row should be headers\n" +
                "• Required columns: Name, Phone\n" +
                "• Optional: Email, Notes, Category, Tags\n" +
                "• VCF: Standard vCard format supported",
                fontSize = 12.sp,
                color = Color(0xFF94A3B8),
                lineHeight = 18.sp
            )
        }
    }
}

@Composable
fun ImportPreviewScreen(
    leads: List<Lead>,
    importResult: LeadImporter.ImportResult,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.padding(16.dp)) {
        // Stats Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1a1a2e)),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Import Summary", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    StatItem(
                        value = importResult.importedCount.toString(),
                        label = "Ready",
                        color = Color(0xFF10B981)
                    )
                    StatItem(
                        value = importResult.skippedCount.toString(),
                        label = "Skipped",
                        color = Color(0xFFF59E0B)
                    )
                    StatItem(
                        value = importResult.errorCount.toString(),
                        label = "Errors",
                        color = Color(0xFFEF4444)
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            "Preview (${leads.size} contacts)",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Leads Preview List
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(leads.take(50)) { lead ->
                PreviewLeadCard(lead)
            }
            
            if (leads.size > 50) {
                item {
                    Text(
                        "... and ${leads.size - 50} more",
                        fontSize = 14.sp,
                        color = Color(0xFF94A3B8),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(16.dp)
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Action Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF94A3B8))
            ) {
                Text("Cancel")
            }
            Button(
                onClick = onConfirm,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
            ) {
                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Import ${leads.size} Leads")
            }
        }
    }
}

@Composable
fun StatItem(value: String, label: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 28.sp, fontWeight = FontWeight.Bold, color = color)
        Text(label, fontSize = 12.sp, color = Color(0xFF94A3B8))
    }
}

@Composable
fun PreviewLeadCard(lead: Lead) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF2a2a3e)),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(Color(0xFF10B981).copy(alpha = 0.2f), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    lead.name.firstOrNull()?.uppercase() ?: "?",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF10B981)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(lead.name, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Color.White)
                Text(lead.phoneNumber, fontSize = 12.sp, color = Color(0xFF94A3B8))
            }
            if (lead.category.isNotBlank()) {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = Color(0xFF3B82F6).copy(alpha = 0.2f)
                ) {
                    Text(
                        lead.category,
                        fontSize = 10.sp,
                        color = Color(0xFF3B82F6),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }
}
