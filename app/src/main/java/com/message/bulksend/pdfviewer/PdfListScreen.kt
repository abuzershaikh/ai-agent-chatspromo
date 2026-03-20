package com.message.bulksend.pdfviewer

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfListScreen(
    onBack: () -> Unit,
    onPdfSelected: (Uri) -> Unit
) {
    val context = LocalContext.current
    var recentPdfs by remember { mutableStateOf<List<RecentPdfManager.RecentPdf>>(emptyList()) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var pdfToDelete by remember { mutableStateOf<RecentPdfManager.RecentPdf?>(null) }
    
    // File picker launcher
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { 
            onPdfSelected(it) 
        }
    }
    
    // Load recent PDFs
    LaunchedEffect(Unit) {
        recentPdfs = RecentPdfManager.getRecentPdfs(context)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("PDF Viewer") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFFEF4444),
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Header with Select Button
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color(0xFFEF4444)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Button(
                        onClick = { filePickerLauncher.launch("application/pdf") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White,
                            contentColor = Color(0xFFEF4444)
                        )
                    ) {
                        Text(
                            "+ Select PDF File",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Recent PDFs Section
            if (recentPdfs.isNotEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize()
                ) {
                    // Section Header
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = Color(0xFFF9FAFB)
                    ) {
                        Text(
                            "Recent PDFs",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1F2937),
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                    
                    // Recent PDFs List
                    LazyColumn(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(recentPdfs) { pdf ->
                            RecentPdfItem(
                                pdf = pdf,
                                onClick = {
                                    try {
                                        // Open from local file path
                                        val file = java.io.File(pdf.filePath)
                                        if (file.exists()) {
                                            onPdfSelected(Uri.fromFile(file))
                                        } else {
                                            android.widget.Toast.makeText(
                                                context,
                                                "PDF file not found",
                                                android.widget.Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    } catch (e: Exception) {
                                        android.widget.Toast.makeText(
                                            context,
                                            "Cannot open PDF: ${e.message}",
                                            android.widget.Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                },
                                onLongClick = {
                                    pdfToDelete = pdf
                                    showDeleteDialog = true
                                }
                            )
                            Divider(color = Color(0xFFE5E7EB), thickness = 1.dp)
                        }
                    }
                }
            } else {
                // Empty State
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        Icons.Default.Description,
                        contentDescription = null,
                        modifier = Modifier.size(120.dp),
                        tint = Color(0xFFD1D5DB)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "No Recent PDFs",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF9CA3AF)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Open a PDF file to see it here",
                        fontSize = 14.sp,
                        color = Color(0xFFD1D5DB)
                    )
                }
            }
        }
    }
    
    // Delete Confirmation Dialog
    if (showDeleteDialog && pdfToDelete != null) {
        AlertDialog(
            onDismissRequest = { 
                showDeleteDialog = false
                pdfToDelete = null
            },
            title = { Text("Delete PDF") },
            text = { 
                Text("Are you sure you want to delete \"${pdfToDelete?.name}\"?")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pdfToDelete?.let { pdf ->
                            RecentPdfManager.deletePdf(context, pdf.filePath)
                            recentPdfs = RecentPdfManager.getRecentPdfs(context)
                            android.widget.Toast.makeText(
                                context,
                                "PDF deleted",
                                android.widget.Toast.LENGTH_SHORT
                            ).show()
                        }
                        showDeleteDialog = false
                        pdfToDelete = null
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = Color(0xFFEF4444)
                    )
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { 
                    showDeleteDialog = false
                    pdfToDelete = null
                }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun RecentPdfItem(
    pdf: RecentPdfManager.RecentPdf,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Default.Description,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = Color(0xFFEF4444)
        )
        
        Spacer(modifier = Modifier.width(16.dp))
        
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                pdf.name,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1F2937),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                getTimeAgo(pdf.timestamp),
                fontSize = 12.sp,
                color = Color(0xFF6B7280)
            )
        }
    }
}

private fun getTimeAgo(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    
    return when {
        diff < 60000 -> "Just now"
        diff < 3600000 -> "${diff / 60000} minutes ago"
        diff < 86400000 -> "${diff / 3600000} hours ago"
        diff < 604800000 -> "${diff / 86400000} days ago"
        else -> SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(Date(timestamp))
    }
}
