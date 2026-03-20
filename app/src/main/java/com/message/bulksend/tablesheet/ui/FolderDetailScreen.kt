package com.message.bulksend.tablesheet.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.message.bulksend.tablesheet.data.models.TableModel
import com.message.bulksend.tablesheet.data.models.FolderModel

private val HeaderBlue = Color(0xFF1976D2)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderDetailScreen(
    folder: FolderModel,
    tables: List<TableModel>,
    onBackPressed: () -> Unit,
    onTableClick: (TableModel) -> Unit,
    onCreateTable: () -> Unit,
    onImportFile: () -> Unit = {},
    onImportFromLink: () -> Unit = {},
    onDeleteTable: (TableModel) -> Unit,
    onRenameTable: ((TableModel, String) -> Unit)? = null,
    onMoveTableOut: ((TableModel) -> Unit)? = null,
    loadingTableId: Long? = null
) {
    var searchQuery by remember { mutableStateOf("") }
    var showFabMenu by remember { mutableStateOf(false) }
    
    // Filter tables in this folder
    val folderTables = remember(tables, searchQuery) {
        tables.filter { table ->
            table.folderId == folder.id &&
            (searchQuery.isEmpty() || 
             table.name.contains(searchQuery, ignoreCase = true) ||
             table.tags?.contains(searchQuery, ignoreCase = true) == true)
        }.sortedByDescending { it.updatedAt }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Column {
                        Text(folder.name, fontWeight = FontWeight.Bold)
                        Text(
                            "${folderTables.size} tables",
                            fontSize = 12.sp,
                            color = Color.White.copy(alpha = 0.8f)
                        )
                    }
                },
                navigationIcon = { 
                    IconButton(onClick = onBackPressed) { 
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") 
                    } 
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = HeaderBlue, 
                    titleContentColor = Color.White, 
                    navigationIconContentColor = Color.White
                )
            )
        },
        floatingActionButton = {
            Column(horizontalAlignment = Alignment.End) {
                // Import/Create options menu
                if (showFabMenu) {
                    Column(
                        modifier = Modifier.padding(bottom = 8.dp),
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Import from Link
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable { 
                                showFabMenu = false
                                onImportFromLink()
                            }
                        ) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color.White,
                                shadowElevation = 4.dp
                            ) {
                                Text(
                                    "Import from Link",
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            FloatingActionButton(
                                onClick = { 
                                    showFabMenu = false
                                    onImportFromLink()
                                },
                                containerColor = Color(0xFF2196F3),
                                contentColor = Color.White,
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(Icons.Default.Link, "Import Link", modifier = Modifier.size(24.dp))
                            }
                        }
                        
                        // Import CSV
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable { 
                                showFabMenu = false
                                onImportFile()
                            }
                        ) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color.White,
                                shadowElevation = 4.dp
                            ) {
                                Text(
                                    "Import to Folder",
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            FloatingActionButton(
                                onClick = { 
                                    showFabMenu = false
                                    onImportFile()
                                },
                                containerColor = Color(0xFF4CAF50),
                                contentColor = Color.White,
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(Icons.Default.Upload, "Import", modifier = Modifier.size(24.dp))
                            }
                        }
                        
                        // Create new table in folder
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable { 
                                showFabMenu = false
                                onCreateTable()
                            }
                        ) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color.White,
                                shadowElevation = 4.dp
                            ) {
                                Text(
                                    "Create in Folder",
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            FloatingActionButton(
                                onClick = { 
                                    showFabMenu = false
                                    onCreateTable()
                                },
                                containerColor = HeaderBlue,
                                contentColor = Color.White,
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(Icons.Default.Add, "Create", modifier = Modifier.size(24.dp))
                            }
                        }
                    }
                }
                
                // Main FAB
                FloatingActionButton(
                    onClick = { showFabMenu = !showFabMenu },
                    containerColor = HeaderBlue,
                    contentColor = Color.White
                ) {
                    Icon(
                        if (showFabMenu) Icons.Default.Close else Icons.Default.Add,
                        if (showFabMenu) "Close" else "Menu"
                    )
                }
            }
        },
        containerColor = Color.White
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // Search bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search tables in folder...", fontSize = 14.sp) },
                leadingIcon = { Icon(Icons.Default.Search, null, tint = Color.Gray) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Clear, null, tint = Color.Gray)
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = HeaderBlue,
                    unfocusedBorderColor = Color.Gray.copy(alpha = 0.3f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            )

            // Content
            if (folderTables.isEmpty()) {
                // Empty State
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.TableChart,
                            null,
                            modifier = Modifier.size(72.dp),
                            tint = Color.Gray.copy(alpha = 0.4f)
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            if (searchQuery.isNotEmpty()) "No tables found" else "No Tables in Folder",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.Gray
                        )
                        Text(
                            if (searchQuery.isNotEmpty()) "Try different search" else "Create or move tables to this folder",
                            fontSize = 14.sp,
                            color = Color.Gray.copy(alpha = 0.7f)
                        )
                        
                        if (searchQuery.isEmpty()) {
                            Spacer(Modifier.height(24.dp))
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Button(
                                    onClick = onCreateTable,
                                    colors = ButtonDefaults.buttonColors(containerColor = HeaderBlue)
                                ) {
                                    Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("Create Table")
                                }
                                
                                OutlinedButton(
                                    onClick = onImportFile,
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = HeaderBlue)
                                ) {
                                    Icon(Icons.Default.Upload, null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("Import File")
                                }
                            }
                        }
                    }
                }
            } else {
                // Tables Grid
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(folderTables, key = { it.id }) { table ->
                        FolderTableCard(
                            table = table,
                            isLoading = loadingTableId == table.id,
                            onClick = { onTableClick(table) },
                            onDelete = { onDeleteTable(table) },
                            onRename = onRenameTable?.let { r -> { name -> r(table, name) } },
                            onMoveOut = onMoveTableOut?.let { m -> { m(table) } }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FolderTableCard(
    table: TableModel,
    isLoading: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onRename: ((String) -> Unit)?,
    onMoveOut: (() -> Unit)?
) {
    var showMenu by remember { mutableStateOf(false) }
    var showRename by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }
    val dateFormat = remember { java.text.SimpleDateFormat("dd MMM, hh:mm a", java.util.Locale.getDefault()) }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !isLoading, onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            // Top row - icon and menu
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(44.dp).background(HeaderBlue.copy(alpha = 0.1f), RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(22.dp), color = HeaderBlue, strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.TableChart, null, tint = HeaderBlue, modifier = Modifier.size(24.dp))
                    }
                }
                Spacer(Modifier.weight(1f))
                if (!isLoading) {
                    Box {
                        IconButton(onClick = { showMenu = true }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.MoreVert, null, tint = Color.Gray)
                        }
                        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                            DropdownMenuItem(
                                text = { Text("Rename") }, 
                                onClick = { showMenu = false; showRename = true }, 
                                leadingIcon = { Icon(Icons.Default.Edit, null) }
                            )
                            if (onMoveOut != null) {
                                DropdownMenuItem(
                                    text = { Text("Move Out of Folder") }, 
                                    onClick = { showMenu = false; onMoveOut() }, 
                                    leadingIcon = { Icon(Icons.Default.DriveFileMove, null) }
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("Delete", color = Color(0xFFEF4444)) }, 
                                onClick = { showMenu = false; showDelete = true }, 
                                leadingIcon = { Icon(Icons.Default.Delete, null, tint = Color(0xFFEF4444)) }
                            )
                        }
                    }
                }
            }
            
            Spacer(Modifier.height(12.dp))
            
            // Table name
            Text(table.name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF333333), maxLines = 1, overflow = TextOverflow.Ellipsis)
            
            Spacer(Modifier.height(4.dp))
            
            // Info row
            Text(
                if (isLoading) "Loading..." else "${table.rowCount} rows • ${table.columnCount} columns",
                fontSize = 12.sp,
                color = if (isLoading) HeaderBlue else Color.Gray
            )
            
            Spacer(Modifier.height(6.dp))
            
            // Tags
            if (!table.tags.isNullOrEmpty()) {
                val tagsList = table.tags.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                if (tagsList.isNotEmpty()) {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        tagsList.take(2).forEach { tag ->
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = HeaderBlue.copy(alpha = 0.1f),
                                modifier = Modifier.padding(end = 4.dp)
                            ) {
                                Text(
                                    tag,
                                    fontSize = 10.sp,
                                    color = HeaderBlue,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        if (tagsList.size > 2) {
                            Text("+${tagsList.size - 2}", fontSize = 10.sp, color = Color.Gray)
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                }
            }
            
            // Date time
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Schedule, null, tint = Color.Gray.copy(alpha = 0.6f), modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text(dateFormat.format(java.util.Date(table.updatedAt)), fontSize = 11.sp, color = Color.Gray.copy(alpha = 0.8f))
            }
        }
    }
    
    // Rename Dialog
    if (showRename && onRename != null) {
        var newName by remember { mutableStateOf(table.name) }
        AlertDialog(
            onDismissRequest = { showRename = false },
            title = { Text("Rename Table") },
            text = { 
                OutlinedTextField(
                    value = newName, 
                    onValueChange = { newName = it }, 
                    label = { Text("Name") }, 
                    singleLine = true, 
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = HeaderBlue,
                        focusedLabelColor = HeaderBlue
                    )
                ) 
            },
            confirmButton = { 
                TextButton(
                    onClick = { 
                        if (newName.isNotBlank()) { 
                            onRename(newName.trim())
                            showRename = false 
                        } 
                    }
                ) { 
                    Text("Save", color = HeaderBlue) 
                } 
            },
            dismissButton = { 
                TextButton(onClick = { showRename = false }) { 
                    Text("Cancel") 
                } 
            }
        )
    }
    
    // Delete Dialog
    if (showDelete) {
        AlertDialog(
            onDismissRequest = { showDelete = false },
            title = { Text("Delete Table") },
            text = { Text("Delete '${table.name}'? This cannot be undone.") },
            confirmButton = { 
                TextButton(
                    onClick = { 
                        onDelete()
                        showDelete = false 
                    }
                ) { 
                    Text("Delete", color = Color(0xFFEF4444)) 
                } 
            },
            dismissButton = { 
                TextButton(onClick = { showDelete = false }) { 
                    Text("Cancel") 
                } 
            }
        )
    }
}