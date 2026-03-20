package com.message.bulksend.leadmanager.importlead

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.BackHandler
import com.message.bulksend.ui.theme.BulksendTestTheme
import com.message.bulksend.leadmanager.screens.AutoAddSettingsScreen
import com.message.bulksend.leadmanager.screens.ImportLeadsScreen

class ImportLeadActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        setContent {
            BulksendTestTheme {
                ImportLeadScreen(
                    onBackPressed = { finish() }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportLeadScreen(onBackPressed: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    
    var selectedImportType by remember { mutableStateOf<ImportType?>(null) }
    
    val backgroundBrush = Brush.verticalGradient(
        colors = listOf(Color(0xFF0f0c29), Color(0xFF302b63), Color(0xFF24243e))
    )
    
    // Handle back press
    BackHandler { 
        if (selectedImportType != null) {
            selectedImportType = null
        } else {
            onBackPressed()
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        "Import Leads", 
                        color = Color(0xFF10B981), 
                        fontWeight = FontWeight.Bold
                    ) 
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (selectedImportType != null) {
                            selectedImportType = null
                        } else {
                            onBackPressed()
                        }
                    }) {
                        Icon(
                            Icons.Default.ArrowBack, 
                            contentDescription = "Back", 
                            tint = Color(0xFF10B981)
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
            when (selectedImportType) {
                ImportType.AUTO_ADD -> {
                    // Direct navigation to AutoAddSettingsScreen
                    AutoAddSettingsScreen(
                        leadManager = remember { com.message.bulksend.leadmanager.LeadManager(context) },
                        onBack = { selectedImportType = null }
                    )
                }
                ImportType.FILE_IMPORT -> {
                    // Direct navigation to existing ImportLeadsScreen
                    ImportLeadsScreen(
                        leadManager = remember { com.message.bulksend.leadmanager.LeadManager(context) },
                        onBack = { selectedImportType = null },
                        onImportComplete = { 
                            selectedImportType = null
                        }
                    )
                }
                ImportType.MANUAL_IMPORT -> {
                    ManualImportScreen(
                        onBack = { selectedImportType = null }
                    )
                }
                null -> {
                    ImportOptionsScreen(
                        onImportTypeSelected = { type ->
                            selectedImportType = type
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun ImportOptionsScreen(
    onImportTypeSelected: (ImportType) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Column {
                Text(
                    "Import Leads",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Choose how you want to import your leads",
                    fontSize = 14.sp,
                    color = Color(0xFF94A3B8)
                )
            }
        }
        
        item {
            ImportOptionCard(
                icon = Icons.Default.AutoAwesome,
                title = "Auto Add Leads",
                description = "Automatically add leads from connected sources",
                features = listOf("WhatsApp contacts", "Auto-respond leads", "Smart capture"),
                color = Color(0xFF10B981),
                onClick = { onImportTypeSelected(ImportType.AUTO_ADD) }
            )
        }
        
        item {
            ImportOptionCard(
                icon = Icons.Default.FileUpload,
                title = "File Import",
                description = "Import from CSV, Excel, VCF files",
                features = listOf("CSV files", "Excel files", "VCF contacts"),
                color = Color(0xFFF59E0B),
                onClick = { onImportTypeSelected(ImportType.FILE_IMPORT) }
            )
        }
        
        item {
            ImportOptionCard(
                icon = Icons.Default.PersonAdd,
                title = "Manual Import",
                description = "Add leads manually or from phone contacts",
                features = listOf("Phone contacts", "Manual entry", "Bulk add"),
                color = Color(0xFF8B5CF6),
                onClick = { onImportTypeSelected(ImportType.MANUAL_IMPORT) }
            )
        }
    }
}

@Composable
fun ImportOptionCard(
    icon: ImageVector,
    title: String,
    description: String,
    features: List<String>,
    color: Color,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1a1a2e)),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .background(color.copy(alpha = 0.2f), RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        icon, 
                        contentDescription = null, 
                        tint = color, 
                        modifier = Modifier.size(28.dp)
                    )
                }
                
                Spacer(modifier = Modifier.width(16.dp))
                
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        title, 
                        fontSize = 18.sp, 
                        fontWeight = FontWeight.Bold, 
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        description, 
                        fontSize = 14.sp, 
                        color = Color(0xFF94A3B8)
                    )
                }
                
                Icon(
                    Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = Color(0xFF64748B)
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Features list
            features.forEach { feature ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(vertical = 2.dp)
                ) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = null,
                        tint = color,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        feature,
                        fontSize = 12.sp,
                        color = Color(0xFF94A3B8)
                    )
                }
            }
        }
    }
}

enum class ImportType {
    AUTO_ADD,
    FILE_IMPORT,
    MANUAL_IMPORT
}