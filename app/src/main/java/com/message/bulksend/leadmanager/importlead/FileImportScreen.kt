package com.message.bulksend.leadmanager.importlead

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.message.bulksend.leadmanager.screens.ImportLeadsScreen
import androidx.activity.compose.BackHandler

@Composable
fun FileImportScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    
    var showImportLeadsScreen by remember { mutableStateOf(false) }
    
    // Handle back press
    BackHandler { 
        if (showImportLeadsScreen) {
            showImportLeadsScreen = false
        } else {
            onBack()
        }
    }
    
    if (showImportLeadsScreen) {
        // Show the existing ImportLeadsScreen
        ImportLeadsScreen(
            leadManager = remember { com.message.bulksend.leadmanager.LeadManager(context) },
            onBack = { showImportLeadsScreen = false },
            onImportComplete = { 
                showImportLeadsScreen = false
                onBack() // Go back to main import screen after successful import
            }
        )
        return
    }
    
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Column {
                Text(
                    "File Import",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Import leads from various file formats",
                    fontSize = 14.sp,
                    color = Color(0xFF94A3B8)
                )
            }
        }
        
        item {
            FileTypeCard(
                icon = Icons.Default.TableChart,
                title = "Excel Files",
                description = "Import from .xls or .xlsx files",
                supportedFormats = "XLS, XLSX",
                color = Color(0xFF10B981),
                onClick = {
                    showImportLeadsScreen = true
                }
            )
        }
        
        item {
            FileTypeCard(
                icon = Icons.Default.Description,
                title = "CSV Files",
                description = "Import from comma-separated values",
                supportedFormats = "CSV, TXT",
                color = Color(0xFF3B82F6),
                onClick = {
                    showImportLeadsScreen = true
                }
            )
        }
        
        item {
            FileTypeCard(
                icon = Icons.Default.ContactPage,
                title = "VCF Files",
                description = "Import from vCard contact files",
                supportedFormats = "VCF",
                color = Color(0xFFF59E0B),
                onClick = {
                    showImportLeadsScreen = true
                }
            )
        }
        
        item {
            Spacer(modifier = Modifier.height(16.dp))
            FileImportInfoCard()
        }
    }
}

@Composable
fun FileTypeCard(
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
                    fontSize = 16.sp, 
                    fontWeight = FontWeight.Bold, 
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    description, 
                    fontSize = 13.sp, 
                    color = Color(0xFF94A3B8)
                )
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
                Icons.Default.FileUpload,
                contentDescription = null,
                tint = Color(0xFF64748B)
            )
        }
    }
}

@Composable
fun FileImportInfoCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1a1a2e).copy(alpha = 0.5f)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Info, 
                    contentDescription = null, 
                    tint = Color(0xFF3B82F6), 
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "File Format Requirements", 
                    fontSize = 14.sp, 
                    fontWeight = FontWeight.Bold, 
                    color = Color.White
                )
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