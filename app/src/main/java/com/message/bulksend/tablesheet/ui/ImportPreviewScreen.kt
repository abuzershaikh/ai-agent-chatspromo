package com.message.bulksend.tablesheet.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.message.bulksend.tablesheet.utils.FileImporter

private val HEADER_BG = Color(0xFF1976D2)
private val CELL_WIDTH = 120.dp
private val CELL_HEIGHT = 44.dp
private val GRID_COLOR = Color(0xFFBDBDBD)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportPreviewScreen(
    importedData: FileImporter.ImportedData,
    onConfirm: (tableName: String) -> Unit,
    onCancel: () -> Unit
) {
    var tableName by remember { mutableStateOf(importedData.fileName.substringBeforeLast(".")) }
    var showNameDialog by remember { mutableStateOf(false) }
    val horizontalScrollState = rememberScrollState()
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Column {
                        Text("Import Preview", fontWeight = FontWeight.Bold)
                        Text(
                            "${importedData.rows.size} rows • ${importedData.headers.size} columns",
                            fontSize = 12.sp,
                            color = Color.White.copy(alpha = 0.8f)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = HEADER_BG,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        },
        bottomBar = {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shadowElevation = 8.dp,
                color = Color.White
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .navigationBarsPadding()
                ) {
                    // Table name input
                    OutlinedTextField(
                        value = tableName,
                        onValueChange = { tableName = it },
                        label = { Text("Table Name") },
                        leadingIcon = { Icon(Icons.Default.TableChart, null) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = HEADER_BG,
                            focusedLabelColor = HEADER_BG
                        )
                    )
                    
                    Spacer(Modifier.height(12.dp))
                    
                    // Import button
                    Button(
                        onClick = { 
                            if (tableName.isNotBlank()) {
                                onConfirm(tableName.trim())
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = HEADER_BG),
                        enabled = tableName.isNotBlank()
                    ) {
                        Icon(Icons.Default.Check, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Import Table", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color.White)
        ) {
            // File info card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .background(HEADER_BG.copy(alpha = 0.1f), RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            when (importedData.fileType) {
                                FileImporter.FileType.CSV -> Icons.Default.Description
                                FileImporter.FileType.EXCEL -> Icons.Default.TableChart
                                FileImporter.FileType.VCF -> Icons.Default.Contacts
                                else -> Icons.Default.InsertDriveFile
                            },
                            contentDescription = null,
                            tint = HEADER_BG,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    
                    Spacer(Modifier.width(16.dp))
                    
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            importedData.fileName,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF333333),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            "${importedData.fileType.name} File",
                            fontSize = 13.sp,
                            color = Color.Gray
                        )
                    }
                    
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFF4CAF50).copy(alpha = 0.1f)
                    ) {
                        Text(
                            "Ready",
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF4CAF50)
                        )
                    }
                }
            }
            
            // Preview label
            Text(
                "Data Preview",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF374151),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            
            // Table preview
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 16.dp)
            ) {
                // Headers
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .horizontalScroll(horizontalScrollState)
                        .background(HEADER_BG)
                ) {
                    importedData.headers.forEach { header ->
                        Box(
                            modifier = Modifier
                                .width(CELL_WIDTH)
                                .fillMaxHeight()
                                .border(1.dp, GRID_COLOR),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                header,
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(horizontal = 8.dp)
                            )
                        }
                    }
                }
                
                // Data rows
                LazyColumn(
                    modifier = Modifier.fillMaxSize()
                ) {
                    itemsIndexed(importedData.rows) { index, row ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(CELL_HEIGHT)
                                .horizontalScroll(horizontalScrollState)
                                .background(if (index % 2 == 0) Color.White else Color(0xFFF8FAFC))
                        ) {
                            row.forEach { cell ->
                                Box(
                                    modifier = Modifier
                                        .width(CELL_WIDTH)
                                        .fillMaxHeight()
                                        .border(1.dp, GRID_COLOR),
                                    contentAlignment = Alignment.CenterStart
                                ) {
                                    Text(
                                        cell,
                                        color = Color(0xFF333333),
                                        fontSize = 13.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.padding(horizontal = 10.dp)
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
