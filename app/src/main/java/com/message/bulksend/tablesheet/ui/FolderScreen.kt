package com.message.bulksend.tablesheet.ui

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.message.bulksend.tablesheet.data.models.FolderModel
import java.text.SimpleDateFormat
import java.util.*

private val HeaderBlue = Color(0xFF1976D2)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderScreen(
    folders: List<FolderModel> = emptyList(),
    folderTableCounts: Map<Long, Int> = emptyMap(),
    onFolderClick: (FolderModel) -> Unit = {},
    onCreateFolder: (String) -> Unit = {},
    onDeleteFolder: (FolderModel) -> Unit = {},
    onRenameFolder: ((FolderModel, String) -> Unit)? = null
) {
    var searchQuery by remember { mutableStateOf("") }
    var showCreateDialog by remember { mutableStateOf(false) }
    
    // Filter folders based on search
    val filteredFolders = remember(folders, searchQuery) {
        folders.filter { folder ->
            searchQuery.isEmpty() || 
            folder.name.contains(searchQuery, ignoreCase = true)
        }.sortedByDescending { it.updatedAt }
    }

    Column(modifier = Modifier.fillMaxSize().background(Color.White)) {
        // Header Section
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = HeaderBlue,
            shadowElevation = 4.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Folders",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    
                    IconButton(onClick = { showCreateDialog = true }) {
                        Icon(
                            Icons.Default.CreateNewFolder,
                            "Create Folder",
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
                
                Text(
                    "${folders.size} folders",
                    fontSize = 14.sp,
                    color = Color.White.copy(alpha = 0.8f)
                )
            }
        }

        // Search Bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search folders...", fontSize = 14.sp) },
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
        if (filteredFolders.isEmpty()) {
            // Empty State
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Default.Folder,
                        null,
                        modifier = Modifier.size(72.dp),
                        tint = Color.Gray.copy(alpha = 0.4f)
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        if (searchQuery.isNotEmpty()) "No folders found" else "No Folders Yet",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.Gray
                    )
                    Text(
                        if (searchQuery.isNotEmpty()) "Try different search" else "Create folders to organize your tables",
                        fontSize = 14.sp,
                        color = Color.Gray.copy(alpha = 0.7f)
                    )
                    
                    if (searchQuery.isEmpty()) {
                        Spacer(Modifier.height(24.dp))
                        Button(
                            onClick = { showCreateDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = HeaderBlue)
                        ) {
                            Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Create Folder")
                        }
                    }
                }
            }
        } else {
            // Folders List
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(filteredFolders, key = { it.id }) { folder ->
                    FolderCard(
                        folder = folder,
                        tableCount = folderTableCounts[folder.id] ?: 0,
                        onClick = { onFolderClick(folder) },
                        onDelete = { onDeleteFolder(folder) },
                        onRename = onRenameFolder?.let { r -> { name -> r(folder, name) } }
                    )
                }
            }
        }
    }

    // Create Folder Dialog
    if (showCreateDialog) {
        var folderName by remember { mutableStateOf("") }
        
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text("Create New Folder") },
            text = {
                OutlinedTextField(
                    value = folderName,
                    onValueChange = { folderName = it },
                    label = { Text("Folder Name") },
                    placeholder = { Text("Enter folder name") },
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
                        if (folderName.isNotBlank()) {
                            onCreateFolder(folderName.trim())
                            showCreateDialog = false
                            folderName = ""
                        }
                    }
                ) {
                    Text("Create", color = HeaderBlue)
                }
            },
            dismissButton = {
                TextButton(onClick = { 
                    showCreateDialog = false
                    folderName = ""
                }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun FolderCard(
    folder: FolderModel,
    tableCount: Int = 0,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onRename: ((String) -> Unit)?
) {
    var showMenu by remember { mutableStateOf(false) }
    var showRename by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }
    val dateFormat = remember { SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()) }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Folder Icon
            val folderColor = try {
                Color(android.graphics.Color.parseColor(folder.colorHex))
            } catch (e: Exception) {
                HeaderBlue
            }
            
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(
                        folderColor.copy(alpha = 0.1f),
                        RoundedCornerShape(12.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Folder,
                    null,
                    tint = folderColor,
                    modifier = Modifier.size(28.dp)
                )
            }
            
            Spacer(Modifier.width(16.dp))
            
            // Folder Info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    folder.name,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF333333),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                
                Spacer(Modifier.height(4.dp))
                
                Text(
                    "$tableCount table${if (tableCount != 1) "s" else ""}",
                    fontSize = 13.sp,
                    color = Color.Gray
                )
                
                Spacer(Modifier.height(2.dp))
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Schedule,
                        null,
                        tint = Color.Gray.copy(alpha = 0.6f),
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        dateFormat.format(Date(folder.updatedAt)),
                        fontSize = 11.sp,
                        color = Color.Gray.copy(alpha = 0.8f)
                    )
                }
            }
            
            // Menu Button
            // Menu Button - Hide for AI Agent Data Sheet
            if (!folder.name.equals("AI Agent Data Sheet", ignoreCase = true)) {
                Box {
                    IconButton(
                        onClick = { showMenu = true },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.MoreVert, null, tint = Color.Gray)
                    }
                    
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Rename") },
                            onClick = { 
                                showMenu = false
                                showRename = true 
                            },
                            leadingIcon = { Icon(Icons.Default.Edit, null) }
                        )
                        DropdownMenuItem(
                            text = { Text("Delete", color = Color(0xFFEF4444)) },
                            onClick = { 
                                showMenu = false
                                showDelete = true 
                            },
                            leadingIcon = { 
                                Icon(Icons.Default.Delete, null, tint = Color(0xFFEF4444)) 
                            }
                        )
                    }
                }
            }
        }
    }
    
    // Rename Dialog
    if (showRename && onRename != null) {
        var newName by remember { mutableStateOf(folder.name) }
        
        AlertDialog(
            onDismissRequest = { showRename = false },
            title = { Text("Rename Folder") },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("Folder Name") },
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
            title = { Text("Delete Folder") },
            text = { 
                Text("Delete '${folder.name}'? Tables inside will be moved to root. This cannot be undone.") 
            },
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