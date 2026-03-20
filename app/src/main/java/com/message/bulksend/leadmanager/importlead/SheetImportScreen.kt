package com.message.bulksend.leadmanager.importlead

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SheetImportScreen(
    onBack: () -> Unit
) {
    var selectedSheetType by remember { mutableStateOf<SheetType?>(null) }
    var sheetUrl by remember { mutableStateOf("") }
    var isConnecting by remember { mutableStateOf(false) }
    
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Column {
                Text(
                    "Sheet Import",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Import leads from online spreadsheets",
                    fontSize = 14.sp,
                    color = Color(0xFF94A3B8)
                )
            }
        }
        
        item {
            SheetTypeCard(
                icon = Icons.Default.TableChart,
                title = "Google Sheets",
                description = "Import from Google Sheets with real-time sync",
                features = listOf("Real-time sync", "Auto-update", "Share permissions"),
                color = Color(0xFF10B981),
                onClick = { selectedSheetType = SheetType.GOOGLE_SHEETS }
            )
        }
        
        item {
            SheetTypeCard(
                icon = Icons.Default.Description,
                title = "Excel Online",
                description = "Import from Microsoft Excel Online",
                features = listOf("OneDrive sync", "Office 365", "Collaborative editing"),
                color = Color(0xFF3B82F6),
                onClick = { selectedSheetType = SheetType.EXCEL_ONLINE }
            )
        }
        
        item {
            SheetTypeCard(
                icon = Icons.Default.Link,
                title = "Custom URL",
                description = "Import from any CSV/Excel URL",
                features = listOf("Direct URL", "CSV format", "Custom headers"),
                color = Color(0xFFF59E0B),
                onClick = { selectedSheetType = SheetType.CUSTOM_URL }
            )
        }
        
        if (selectedSheetType != null) {
            item {
                SheetConnectionCard(
                    sheetType = selectedSheetType!!,
                    sheetUrl = sheetUrl,
                    onUrlChange = { sheetUrl = it },
                    isConnecting = isConnecting,
                    onConnect = {
                        isConnecting = true
                        // TODO: Implement connection logic
                    }
                )
            }
        }
        
        item {
            Spacer(modifier = Modifier.height(16.dp))
            SheetImportInfoCard()
        }
    }
}

@Composable
fun SheetTypeCard(
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
        shape = RoundedCornerShape(16.dp)
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

@Composable
fun SheetConnectionCard(
    sheetType: SheetType,
    sheetUrl: String,
    onUrlChange: (String) -> Unit,
    isConnecting: Boolean,
    onConnect: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF2a2a3e)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Text(
                "Connect to ${sheetType.displayName}",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(16.dp))
            
            OutlinedTextField(
                value = sheetUrl,
                onValueChange = onUrlChange,
                label = { Text("Sheet URL", color = Color(0xFF94A3B8)) },
                placeholder = { Text(sheetType.placeholder, color = Color(0xFF64748B)) },
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF10B981),
                    unfocusedBorderColor = Color(0xFF64748B),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                singleLine = true
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Button(
                onClick = onConnect,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                enabled = sheetUrl.isNotBlank() && !isConnecting
            ) {
                if (isConnecting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Connecting...")
                } else {
                    Icon(Icons.Default.Link, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Connect Sheet")
                }
            }
        }
    }
}

@Composable
fun SheetImportInfoCard() {
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
                    "Sheet Import Requirements", 
                    fontSize = 14.sp, 
                    fontWeight = FontWeight.Bold, 
                    color = Color.White
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                "• First row should contain headers\n" +
                "• Required columns: Name, Phone\n" +
                "• Optional: Email, Notes, Category\n" +
                "• Sheet must be publicly accessible or shared",
                fontSize = 12.sp,
                color = Color(0xFF94A3B8),
                lineHeight = 18.sp
            )
        }
    }
}

enum class SheetType(val displayName: String, val placeholder: String) {
    GOOGLE_SHEETS("Google Sheets", "https://docs.google.com/spreadsheets/d/..."),
    EXCEL_ONLINE("Excel Online", "https://1drv.ms/x/..."),
    CUSTOM_URL("Custom URL", "https://example.com/leads.csv")
}