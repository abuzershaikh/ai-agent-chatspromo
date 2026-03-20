package com.message.bulksend.autorespond.sheetreply

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.message.bulksend.ui.theme.BulksendTestTheme
import java.text.SimpleDateFormat
import java.util.*
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Patterns

private enum class SpreadsheetLinkMode {
    GOOGLE_SHEETS,
    DIRECT_LINK
}

class SpreadsheetReplyActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        setContent {
            BulksendTestTheme {
                SpreadsheetReplyScreen(
                    onBackPressed = { finish() }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpreadsheetReplyScreen(onBackPressed: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val spreadsheetManager = remember { SpreadsheetReplyManager(context) }
    
    var selectedTab by remember { mutableStateOf(0) }
    var spreadsheets by remember { mutableStateOf(spreadsheetManager.getAllSpreadsheets()) }
    var showAddDialog by remember { mutableStateOf(false) }
    var linkDialogMode by remember { mutableStateOf<SpreadsheetLinkMode?>(null) }
    
    // File pickers - Using OpenDocument for better compatibility
    val excelFilePicker = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            try {
                // Take persistent permission for long-term access
                context.contentResolver.takePersistableUriPermission(
                    it,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
                
                // Get file name from URI
                val fileName = getFileNameFromUri(context, it) ?: "Excel_${System.currentTimeMillis()}"
                spreadsheetManager.addSpreadsheet(fileName, it.toString(), "excel_file")
                spreadsheets = spreadsheetManager.getAllSpreadsheets()
                android.widget.Toast.makeText(context, "Excel file added successfully", android.widget.Toast.LENGTH_SHORT).show()
            } catch (e: SecurityException) {
                android.widget.Toast.makeText(context, "Permission denied. Please try again.", android.widget.Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                android.widget.Toast.makeText(context, "Error adding file: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
            }
        }
    }
    
    val csvFilePicker = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            try {
                // Take persistent permission for long-term access
                context.contentResolver.takePersistableUriPermission(
                    it,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
                
                // Get file name from URI
                val fileName = getFileNameFromUri(context, it) ?: "Sheet_${System.currentTimeMillis()}"
                spreadsheetManager.addSpreadsheet(fileName, it.toString(), "csv_file")
                spreadsheets = spreadsheetManager.getAllSpreadsheets()
                android.widget.Toast.makeText(context, "File added successfully", android.widget.Toast.LENGTH_SHORT).show()
            } catch (e: SecurityException) {
                android.widget.Toast.makeText(context, "Permission denied. Please try again.", android.widget.Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                android.widget.Toast.makeText(context, "Error adding file: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
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
                    Text("Spreadsheet Reply", color = Color(0xFF00D4FF), fontWeight = FontWeight.Bold)
                },
                navigationIcon = {
                    IconButton(onClick = onBackPressed) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color(0xFF00D4FF))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF1a1a2e))
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = Color(0xFF1a1a2e),
                contentColor = Color(0xFF00D4FF)
            ) {
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                    label = { Text("Settings") },
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color(0xFF00D4FF),
                        selectedTextColor = Color(0xFF00D4FF),
                        unselectedIconColor = Color(0xFF64748B),
                        unselectedTextColor = Color(0xFF64748B),
                        indicatorColor = Color(0xFF1a1a2e)
                    )
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Visibility, contentDescription = "Preview") },
                    label = { Text("Preview") },
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color(0xFF00D4FF),
                        selectedTextColor = Color(0xFF00D4FF),
                        unselectedIconColor = Color(0xFF64748B),
                        unselectedTextColor = Color(0xFF64748B),
                        indicatorColor = Color(0xFF1a1a2e)
                    )
                )
            }
        },
        containerColor = Color.Transparent,
        floatingActionButton = {
            if (selectedTab == 0) {
                FloatingActionButton(
                    onClick = { showAddDialog = true },
                    containerColor = Color(0xFF00D4FF)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add", tint = Color.White)
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(backgroundBrush)
                .padding(padding)
        ) {
            when (selectedTab) {
                0 -> SettingsTabContent(
                    spreadsheetManager = spreadsheetManager,
                    spreadsheets = spreadsheets,
                    onSpreadsheetsChanged = { spreadsheets = it }
                )
                1 -> PreviewTabContent(spreadsheets = spreadsheets)
            }
        }
    }
    
    // Add Spreadsheet Dialog
    if (showAddDialog) {
        AddSpreadsheetDialog(
            onDismiss = { showAddDialog = false },
            onAddGoogleSheetsLink = {
                showAddDialog = false
                linkDialogMode = SpreadsheetLinkMode.GOOGLE_SHEETS
            },
            onAddDirectLink = {
                showAddDialog = false
                linkDialogMode = SpreadsheetLinkMode.DIRECT_LINK
            },
            onAddFile = { type ->
                showAddDialog = false
                when (type) {
                    "csv" -> csvFilePicker.launch(arrayOf("text/csv", "text/comma-separated-values", "application/csv", "*/*"))
                    "excel" -> excelFilePicker.launch(arrayOf(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                        "application/vnd.ms-excel",
                        "*/*"
                    ))
                }
            }
        )
    }

    linkDialogMode?.let { mode ->
        AddSpreadsheetLinkDialog(
            mode = mode,
            onDismiss = { linkDialogMode = null },
            onAddLink = { customName, link ->
                val trimmedLink = link.trim()
                val detectedType = SpreadsheetReader.detectLinkType(trimmedLink)
                when {
                    !isValidSpreadsheetUrl(trimmedLink) -> {
                        android.widget.Toast.makeText(
                            context,
                            "Enter a valid http or https spreadsheet link.",
                            android.widget.Toast.LENGTH_LONG
                        ).show()
                    }
                    else -> {
                        val type = detectedType ?: "spreadsheet_link"
                        val sheetName = customName.trim().ifBlank {
                            buildSpreadsheetDisplayName(trimmedLink, type)
                        }
                        spreadsheetManager.addSpreadsheet(sheetName, trimmedLink, type)
                        spreadsheets = spreadsheetManager.getAllSpreadsheets()
                        linkDialogMode = null
                        android.widget.Toast.makeText(
                            context,
                            "Spreadsheet link added successfully",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        )
    }
}

@Composable
fun SettingsTabContent(
    spreadsheetManager: SpreadsheetReplyManager,
    spreadsheets: List<SpreadsheetData>,
    onSpreadsheetsChanged: (List<SpreadsheetData>) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    
    // Get enabled status from Auto Reply Settings
    val settingsManager = remember { 
        com.message.bulksend.autorespond.settings.AutoReplySettingsManager(context) 
    }
    val isEnabled = settingsManager.isSpreadsheetReplyEnabled()
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Status Card (Read-only)
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (isEnabled) Color(0xFF10B981) else Color(0xFF6B7280)
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        if (isEnabled) Icons.Default.CheckCircle else Icons.Default.Cancel,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            if (isEnabled) "Active" else "Inactive",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            "${spreadsheets.size} spreadsheet(s)",
                            fontSize = 14.sp,
                            color = Color.White.copy(alpha = 0.9f)
                        )
                    }
                }
            }
        }
        
        // Info message
        if (!isEnabled) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1a1a2e).copy(alpha = 0.5f)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = null,
                        tint = Color(0xFF00D4FF),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "Enable Spreadsheet Reply from Auto Reply Settings",
                        fontSize = 13.sp,
                        color = Color(0xFF94A3B8)
                    )
                }
            }
        }
        
        // Spreadsheets List
        Text(
            "My Spreadsheets",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        
        if (spreadsheets.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1a1a2e)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Default.TableChart,
                        contentDescription = null,
                        tint = Color(0xFF64748B),
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "No spreadsheets added",
                        fontSize = 16.sp,
                        color = Color(0xFF94A3B8)
                    )
                }
            }
        } else {
            spreadsheets.forEach { sheet ->
                SpreadsheetCard(
                    spreadsheet = sheet,
                    onDelete = {
                        spreadsheetManager.deleteSpreadsheet(sheet.id)
                        onSpreadsheetsChanged(spreadsheetManager.getAllSpreadsheets())
                        android.widget.Toast.makeText(context, "Deleted", android.widget.Toast.LENGTH_SHORT).show()
                    }
                )
            }
        }
        
        Spacer(modifier = Modifier.height(80.dp))
    }
}

@Composable
fun SpreadsheetCard(
    spreadsheet: SpreadsheetData,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1a1a2e)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.TableChart,
                    contentDescription = null,
                    tint = Color(0xFF10B981),
                    modifier = Modifier.size(32.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        spreadsheet.name,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        spreadsheetTypeLabel(spreadsheet.type),
                        fontSize = 12.sp,
                        color = Color(0xFF94A3B8)
                    )
                }
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = Color(0xFFEF4444)
                )
            }
        }
    }
}

@Composable
fun AddSpreadsheetDialog(
    onDismiss: () -> Unit,
    onAddGoogleSheetsLink: () -> Unit,
    onAddDirectLink: () -> Unit,
    onAddFile: (String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1a1a2e),
        title = {
            Text(
                "Add Spreadsheet",
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OptionCard(
                    icon = Icons.Default.Link,
                    title = "Google Sheets Link",
                    onClick = onAddGoogleSheetsLink
                )
                OptionCard(
                    icon = Icons.Default.Language,
                    title = "CSV / Excel Link",
                    onClick = onAddDirectLink
                )
                OptionCard(
                    icon = Icons.Default.CloudUpload,
                    title = "CSV / Sheets File",
                    onClick = { onAddFile("csv") }
                )
                OptionCard(
                    icon = Icons.Default.UploadFile,
                    title = "Excel File",
                    onClick = { onAddFile("excel") }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = Color(0xFF94A3B8))
            }
        }
    )
}

@Composable
private fun AddSpreadsheetLinkDialog(
    mode: SpreadsheetLinkMode,
    onDismiss: () -> Unit,
    onAddLink: (String, String) -> Unit
) {
    var sheetName by remember(mode) { mutableStateOf("") }
    var link by remember(mode) { mutableStateOf("") }
    var errorMessage by remember(mode) { mutableStateOf<String?>(null) }

    val title = if (mode == SpreadsheetLinkMode.GOOGLE_SHEETS) {
        "Add Google Sheet"
    } else {
        "Add Spreadsheet Link"
    }
    val description = if (mode == SpreadsheetLinkMode.GOOGLE_SHEETS) {
        "Paste a Google Sheets link. Shared Google Drive spreadsheet links also work if the file is accessible."
    } else {
        "Paste a direct CSV or Excel link. Google Drive, OneDrive, and SharePoint download links also work."
    }
    val placeholder = if (mode == SpreadsheetLinkMode.GOOGLE_SHEETS) {
        "https://docs.google.com/spreadsheets/d/..."
    } else {
        "https://example.com/sheet.xlsx"
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1a1a2e),
        title = {
            Text(
                title,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = description,
                    color = Color(0xFF94A3B8),
                    fontSize = 13.sp
                )

                OutlinedTextField(
                    value = sheetName,
                    onValueChange = { sheetName = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Sheet Name (Optional)") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF00D4FF),
                        unfocusedBorderColor = Color(0xFF64748B),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedLabelColor = Color(0xFF00D4FF),
                        unfocusedLabelColor = Color(0xFF94A3B8)
                    )
                )

                OutlinedTextField(
                    value = link,
                    onValueChange = {
                        link = it
                        errorMessage = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Spreadsheet Link") },
                    placeholder = { Text(placeholder) },
                    maxLines = 3,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF00D4FF),
                        unfocusedBorderColor = Color(0xFF64748B),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedLabelColor = Color(0xFF00D4FF),
                        unfocusedLabelColor = Color(0xFF94A3B8)
                    )
                )

                if (errorMessage != null) {
                    Text(
                        text = errorMessage!!,
                        color = Color(0xFFEF4444),
                        fontSize = 12.sp
                    )
                } else {
                    Text(
                        text = if (mode == SpreadsheetLinkMode.GOOGLE_SHEETS) {
                            "Supports shared Google Sheets and Google Drive spreadsheet links."
                        } else {
                            "Supports direct CSV, XLS, XLSX, Google Drive, OneDrive, and SharePoint links."
                        },
                        color = Color(0xFF64748B),
                        fontSize = 12.sp
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val trimmedLink = link.trim()
                    when {
                        trimmedLink.isBlank() -> errorMessage = "Enter a spreadsheet link."
                        !isValidSpreadsheetUrl(trimmedLink) -> errorMessage = "Link must start with http:// or https://"
                        else -> onAddLink(sheetName, trimmedLink)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00D4FF))
            ) {
                Text("Add", color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Color(0xFF94A3B8))
            }
        }
    )
}

@Composable
fun OptionCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF2a2a3e)),
        shape = RoundedCornerShape(12.dp),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = Color(0xFF10B981),
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White
            )
        }
    }
}

// Helper function to get file name from URI
private fun getFileNameFromUri(context: Context, uri: Uri): String? {
    var fileName: String? = null
    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) {
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (nameIndex != -1) {
                fileName = cursor.getString(nameIndex)
            }
        }
    }
    return fileName
}

private fun isValidSpreadsheetUrl(url: String): Boolean {
    return (url.startsWith("http://") || url.startsWith("https://")) &&
        Patterns.WEB_URL.matcher(url).matches()
}

private fun buildSpreadsheetDisplayName(url: String, type: String): String {
    val stamp = SimpleDateFormat("dd MMM HH:mm", Locale.getDefault()).format(Date())
    val parsedUri = Uri.parse(url)
    val fileName = parsedUri.lastPathSegment
        ?.substringAfterLast('/')
        ?.substringBefore('?')
        ?.takeIf { it.isNotBlank() && it.contains('.') }

    if (!fileName.isNullOrBlank()) {
        return fileName
    }

    return when (type) {
        "google_sheets_link" -> "Google Sheet $stamp"
        "csv_link" -> "CSV Link $stamp"
        "excel_link" -> "Excel Link $stamp"
        else -> "Spreadsheet Link $stamp"
    }
}
