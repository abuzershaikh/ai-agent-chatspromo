package com.message.bulksend.autorespond.sheetreply

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PreviewTabContent(spreadsheets: List<SpreadsheetData>) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var selectedSheet by remember { mutableStateOf<SpreadsheetData?>(null) }
    var expandedDropdown by remember { mutableStateOf(false) }
    var previewData by remember { mutableStateOf<List<SpreadsheetRow>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    
    var errorMessage by remember { mutableStateOf<String?>(null) }
    
    // Load data when sheet is selected
    LaunchedEffect(selectedSheet) {
        selectedSheet?.let { sheet ->
            isLoading = true
            errorMessage = null
            previewData = try {
                val data = withContext(Dispatchers.IO) {
                    SpreadsheetReader.readSpreadsheetPreviewData(context, sheet.url)
                }
                if (data.isEmpty()) {
                    errorMessage = "No data found in spreadsheet"
                }
                data
            } catch (e: Exception) {
                errorMessage = "Error reading spreadsheet: ${e.message}"
                emptyList()
            }
            isLoading = false
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header
        Text(
            "Spreadsheet Preview",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        
        if (spreadsheets.isEmpty()) {
            // Empty State
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1a1a2e)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(48.dp),
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
                        "No spreadsheets available",
                        fontSize = 16.sp,
                        color = Color(0xFF94A3B8)
                    )
                    Text(
                        "Add a spreadsheet from Settings tab",
                        fontSize = 14.sp,
                        color = Color(0xFF64748B)
                    )
                }
            }
        } else {
            // Dropdown to select spreadsheet
            Text(
                "Select Spreadsheet",
                fontSize = 14.sp,
                color = Color(0xFF94A3B8)
            )
            
            ExposedDropdownMenuBox(
                expanded = expandedDropdown,
                onExpandedChange = { expandedDropdown = it }
            ) {
                OutlinedTextField(
                    value = selectedSheet?.name ?: "Select a spreadsheet...",
                    onValueChange = {},
                    readOnly = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedDropdown)
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF00D4FF),
                        unfocusedBorderColor = Color(0xFF64748B),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    )
                )
                
                ExposedDropdownMenu(
                    expanded = expandedDropdown,
                    onDismissRequest = { expandedDropdown = false },
                    modifier = Modifier.background(Color(0xFF1a1a2e))
                ) {
                    spreadsheets.forEach { sheet ->
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(
                                        sheet.name,
                                        color = Color.White,
                                        fontSize = 14.sp
                                    )
                                    Text(
                                        spreadsheetTypeLabel(sheet.type),
                                        color = Color(0xFF94A3B8),
                                        fontSize = 12.sp
                                    )
                                }
                            },
                            onClick = {
                                selectedSheet = sheet
                                expandedDropdown = false
                            },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.TableChart,
                                    contentDescription = null,
                                    tint = Color(0xFF10B981)
                                )
                            }
                        )
                    }
                }
            }
            
            // Preview Table
            if (selectedSheet != null) {
                Spacer(modifier = Modifier.height(8.dp))
                
                // Info Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (errorMessage != null) Color(0xFF991B1B) else Color(0xFF1a1a2e)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            when {
                                isLoading -> Icons.Default.Sync
                                errorMessage != null -> Icons.Default.Error
                                else -> Icons.Default.Info
                            },
                            contentDescription = null,
                            tint = if (errorMessage != null) Color.White else Color(0xFF00D4FF),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            when {
                                isLoading -> "Loading..."
                                errorMessage != null -> errorMessage!!
                                else -> "Showing ${previewData.size} rows"
                            },
                            fontSize = 13.sp,
                            color = if (errorMessage != null) Color.White else Color(0xFF94A3B8)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Table with horizontal scroll
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .horizontalScroll(rememberScrollState())
                ) {
                    // Table Header
                    Card(
                        modifier = Modifier.wrapContentWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1a1a2e)),
                        shape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp, bottomStart = 0.dp, bottomEnd = 0.dp),
                        elevation = CardDefaults.cardElevation(4.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .background(Color(0xFF2a2a3e))
                                .border(1.dp, Color(0xFF64748B).copy(alpha = 0.3f))
                                .padding(vertical = 14.dp, horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Sr No
                            Box(
                                modifier = Modifier.width(60.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "Sr No",
                                    color = Color(0xFF94A3B8),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                            }
                            
                            // Column A Header
                            Box(
                                modifier = Modifier
                                    .width(200.dp)
                                    .padding(horizontal = 4.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Text(
                                    "Incoming Message",
                                    color = Color(0xFF00D4FF),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                            }
                            
                            // Column B Header
                            Box(
                                modifier = Modifier
                                    .width(300.dp)
                                    .padding(horizontal = 4.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Text(
                                    "Outgoing Reply",
                                    color = Color(0xFF10B981),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                    
                    // Table Body
                    LazyColumn(
                        modifier = Modifier.wrapContentWidth()
                    ) {
                        items(previewData.size) { index ->
                            val row = previewData[index]
                            val isLastItem = index == previewData.size - 1
                            
                            Card(
                                modifier = Modifier.wrapContentWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (index % 2 == 0) Color(0xFF1a1a2e) else Color(0xFF16162a)
                                ),
                                shape = if (isLastItem) {
                                    RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp)
                                } else {
                                    RoundedCornerShape(0.dp)
                                }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .border(
                                            width = 0.5.dp,
                                            color = Color(0xFF64748B).copy(alpha = 0.2f)
                                        )
                                        .padding(vertical = 12.dp, horizontal = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Sr No
                                    Box(
                                        modifier = Modifier.width(60.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            "${index + 1}",
                                            color = Color(0xFF00D4FF),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp
                                        )
                                    }
                                    
                                    // Incoming Message
                                    Box(
                                        modifier = Modifier
                                            .width(200.dp)
                                            .padding(horizontal = 4.dp),
                                        contentAlignment = Alignment.CenterStart
                                    ) {
                                        Text(
                                            row.incomingMessage,
                                            color = Color.White,
                                            fontSize = 12.sp,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    
                                    // Outgoing Reply
                                    Box(
                                        modifier = Modifier
                                            .width(300.dp)
                                            .padding(horizontal = 4.dp),
                                        contentAlignment = Alignment.CenterStart
                                    ) {
                                        Text(
                                            row.outgoingMessage,
                                            color = Color.White,
                                            fontSize = 12.sp,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
