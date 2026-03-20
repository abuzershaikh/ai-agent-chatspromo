package com.message.bulksend.tablesheet.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.message.bulksend.tablesheet.data.models.TableModel
import com.message.bulksend.tablesheet.data.models.FolderModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

private val HeaderBlue = Color(0xFF1976D2)
private val DarkDrawer = Color(0xFF1E1E2E)
private val DarkSurface = Color(0xFF2D2D3D)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TableListScreen(
    tables: List<TableModel>,
    folders: List<FolderModel> = emptyList(),
    folderTableCounts: Map<Long, Int> = emptyMap(),
    onTableClick: (TableModel) -> Unit,
    onCreateTable: () -> Unit,
    onImportFile: () -> Unit = {},
    onImportFromLink: () -> Unit = {},
    onDeleteTable: (TableModel) -> Unit,
    onRenameTable: ((TableModel, String) -> Unit)? = null,
    onMoveToFolder: ((TableModel, Long?) -> Unit)? = null,
    onCreateFolder: ((String) -> Unit)? = null,
    onDeleteFolder: ((FolderModel) -> Unit)? = null,
    onRenameFolder: ((FolderModel, String) -> Unit)? = null,
    onFolderClick: ((FolderModel) -> Unit)? = null,
    onBackPressed: () -> Unit,
    loadingTableId: Long? = null,
    onRefreshSync: (() -> Unit)? = null
) {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var selectedMenuItem by remember { mutableStateOf("All Tables") }
    var searchQuery by remember { mutableStateOf("") }
    var sortBy by remember { mutableStateOf("Recent") }
    var showSortMenu by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableStateOf(0) } // 0 = Dashboard, 1 = Folders

    // Filter and sort tables
    val filteredTables = remember(tables, searchQuery, sortBy) {
        tables.filter { table ->
            searchQuery.isEmpty() || 
            table.name.contains(searchQuery, ignoreCase = true) ||
            table.tags?.contains(searchQuery, ignoreCase = true) == true
        }.sortedWith(
            when (sortBy) {
                "Name" -> compareBy { it.name.lowercase() }
                "Size" -> compareByDescending { it.rowCount }
                else -> compareByDescending { it.updatedAt }
            }
        )
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(drawerContainerColor = DarkDrawer, modifier = Modifier.width(280.dp)) {
                Box(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier.size(44.dp).background(HeaderBlue, RoundedCornerShape(10.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.TableChart, null, tint = Color.White, modifier = Modifier.size(26.dp))
                        }
                        Spacer(Modifier.width(12.dp))
                        Text("TableSheet", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
                HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
                Spacer(Modifier.height(12.dp))
                
                Text("MENU", fontSize = 11.sp, color = Color.Gray, letterSpacing = 1.sp, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
                
                DrawerItem(Icons.Default.Dashboard, "Dashboard", selectedMenuItem == "Dashboard") { selectedMenuItem = "Dashboard"; scope.launch { drawerState.close() } }
                DrawerItem(Icons.Default.TableChart, "All Tables", selectedMenuItem == "All Tables") { selectedMenuItem = "All Tables"; scope.launch { drawerState.close() } }
                DrawerItem(Icons.Default.Star, "Favorites", selectedMenuItem == "Favorites") { selectedMenuItem = "Favorites"; scope.launch { drawerState.close() } }
                DrawerItem(Icons.Default.History, "Recent", selectedMenuItem == "Recent") { selectedMenuItem = "Recent"; scope.launch { drawerState.close() } }
                
                Spacer(Modifier.height(16.dp))
                HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
                Spacer(Modifier.height(12.dp))
                
                Text("OTHER", fontSize = 11.sp, color = Color.Gray, letterSpacing = 1.sp, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
                DrawerItem(Icons.Default.Settings, "Settings", false) { }
                DrawerItem(Icons.Default.Delete, "Trash", false) { }
                
                Spacer(Modifier.weight(1f))
                HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
                DrawerItem(Icons.AutoMirrored.Filled.Logout, "Exit", false) { onBackPressed() }
                Spacer(Modifier.height(20.dp))
            }
        }
    ) {
        var showFabMenu by remember { mutableStateOf(false) }
        
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("TableSheet", fontWeight = FontWeight.Bold) },
                    navigationIcon = { IconButton(onClick = { scope.launch { drawerState.open() } }) { Icon(Icons.Default.Menu, "Menu") } },
                    actions = { 
                        if (onRefreshSync != null) {
                            IconButton(onClick = onRefreshSync) { 
                                Icon(Icons.Default.Refresh, "Sync") 
                            }
                        }
                        IconButton(onClick = onBackPressed) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } 
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = HeaderBlue, titleContentColor = Color.White, navigationIconContentColor = Color.White, actionIconContentColor = Color.White)
                )
            },
            floatingActionButton = {
                Column(horizontalAlignment = Alignment.End) {
                    // Import options menu
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
                                        "Import CSV/Excel/VCF",
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
                            
                            // Create new table
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
                                        "Create New Table",
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
            Box(modifier = Modifier.fillMaxSize()) {
                Column(modifier = Modifier.fillMaxSize().padding(padding)) {
                    // Tab content based on selected tab
                    when (selectedTab) {
                        0 -> {
                            // Dashboard Tab - Original table list content
                            Column(modifier = Modifier.weight(1f)) {
                                // Search bar and sort
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    OutlinedTextField(
                                        value = searchQuery,
                                        onValueChange = { searchQuery = it },
                                        placeholder = { Text("Search tables or tags...", fontSize = 14.sp) },
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
                                        modifier = Modifier.weight(1f).height(52.dp)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Box {
                                        IconButton(
                                            onClick = { showSortMenu = true },
                                            modifier = Modifier.background(Color(0xFFF3F4F6), RoundedCornerShape(12.dp))
                                        ) {
                                            Icon(Icons.Default.Sort, "Sort", tint = HeaderBlue)
                                        }
                                        DropdownMenu(expanded = showSortMenu, onDismissRequest = { showSortMenu = false }) {
                                            Text("Sort by", fontSize = 12.sp, color = Color.Gray, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
                                            DropdownMenuItem(
                                                text = { Text("Recent", fontWeight = if (sortBy == "Recent") FontWeight.Bold else FontWeight.Normal) },
                                                onClick = { sortBy = "Recent"; showSortMenu = false },
                                                leadingIcon = { Icon(Icons.Default.Schedule, null) },
                                                trailingIcon = { if (sortBy == "Recent") Icon(Icons.Default.Check, null, tint = HeaderBlue) }
                                            )
                                            DropdownMenuItem(
                                                text = { Text("Name", fontWeight = if (sortBy == "Name") FontWeight.Bold else FontWeight.Normal) },
                                                onClick = { sortBy = "Name"; showSortMenu = false },
                                                leadingIcon = { Icon(Icons.Default.SortByAlpha, null) },
                                                trailingIcon = { if (sortBy == "Name") Icon(Icons.Default.Check, null, tint = HeaderBlue) }
                                            )
                                            DropdownMenuItem(
                                                text = { Text("Size", fontWeight = if (sortBy == "Size") FontWeight.Bold else FontWeight.Normal) },
                                                onClick = { sortBy = "Size"; showSortMenu = false },
                                                leadingIcon = { Icon(Icons.Default.Storage, null) },
                                                trailingIcon = { if (sortBy == "Size") Icon(Icons.Default.Check, null, tint = HeaderBlue) }
                                            )
                                        }
                                    }
                                }
                                
                                // Results count
                                if (searchQuery.isNotEmpty()) {
                                    Text(
                                        "${filteredTables.size} result${if (filteredTables.size != 1) "s" else ""} found",
                                        fontSize = 12.sp,
                                        color = Color.Gray,
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                                    )
                                }
                                    LazyVerticalGrid(
                                        columns = GridCells.Fixed(2),
                                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 80.dp),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        // Folders Section
                                        if (searchQuery.isEmpty() && folders.isNotEmpty()) {
                                            item(span = { GridItemSpan(2) }) {
                                                Column(modifier = Modifier.fillMaxWidth()) {
                                                    Text(
                                                        "Folders",
                                                        fontSize = 16.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = Color(0xFF333333),
                                                        modifier = Modifier.padding(bottom = 8.dp)
                                                    )
                                                    androidx.compose.foundation.lazy.LazyRow(
                                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                                        contentPadding = PaddingValues(bottom = 8.dp)
                                                    ) {
                                                        items(folders) { folder ->
                                                            CompactFolderCard(
                                                                folder = folder,
                                                                tableCount = folderTableCounts[folder.id] ?: 0,
                                                                onClick = { onFolderClick?.invoke(folder) }
                                                            )
                                                        }
                                                    }
                                                    Spacer(Modifier.height(8.dp))
                                                    Text(
                                                        "All Tables",
                                                        fontSize = 16.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = Color(0xFF333333),
                                                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                                                    )
                                                }
                                            }
                                        }

                                        if (filteredTables.isEmpty() && searchQuery.isNotEmpty()) {
                                            item(span = { GridItemSpan(2) }) {
                                               Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                        Icon(Icons.Default.SearchOff, null, modifier = Modifier.size(48.dp), tint = Color.Gray.copy(alpha = 0.4f))
                                                        Spacer(Modifier.height(16.dp))
                                                        Text("No matching tables found", fontSize = 16.sp, color = Color.Gray)
                                                    }
                                                }
                                            }
                                        } else if (filteredTables.isEmpty() && folders.isEmpty()) {
                                             item(span = { GridItemSpan(2) }) {
                                                Box(modifier = Modifier.fillMaxWidth().height(300.dp), contentAlignment = Alignment.Center) {
                                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                        Icon(Icons.Default.TableChart, null, modifier = Modifier.size(72.dp), tint = Color.Gray.copy(alpha = 0.4f))
                                                        Spacer(Modifier.height(16.dp))
                                                        Text("No Tables Yet", fontSize = 18.sp, fontWeight = FontWeight.Medium, color = Color.Gray)
                                                        Text("Tap + to create", fontSize = 14.sp, color = Color.Gray.copy(alpha = 0.7f))
                                                    }
                                                }
                                            }
                                        }

                                        items(filteredTables, key = { it.id }) { table ->
                                            TableCard(
                                                table = table, 
                                                isLoading = loadingTableId == table.id, 
                                                onClick = { onTableClick(table) }, 
                                                onDelete = { onDeleteTable(table) }, 
                                                onRename = onRenameTable?.let { r -> { n -> r(table, n) } },
                                                onMoveToFolder = onMoveToFolder?.let { m -> { folderId -> m(table, folderId) } },
                                                availableFolders = folders,
                                                folderTableCounts = folderTableCounts
                                            )
                                        }
                                    }
                                }
                            }
                        1 -> {
                            // Folders Tab
                            Box(modifier = Modifier.weight(1f)) {
                                FolderScreen(
                                    folders = folders,
                                    folderTableCounts = folderTableCounts,
                                    onFolderClick = { folder -> onFolderClick?.invoke(folder) },
                                    onCreateFolder = { name -> onCreateFolder?.invoke(name) },
                                    onDeleteFolder = { folder -> onDeleteFolder?.invoke(folder) },
                                    onRenameFolder = { folder, name -> onRenameFolder?.invoke(folder, name) }
                                )
                            }
                        }
                    }
                }
                
                // Bottom Tab Bar - Fixed at bottom
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter),
                    color = Color.White,
                    shadowElevation = 8.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        BottomTabItem(
                            icon = Icons.Default.Dashboard,
                            label = "Dashboard",
                            selected = selectedTab == 0,
                            onClick = { selectedTab = 0 }
                        )
                        BottomTabItem(
                            icon = Icons.Default.Folder,
                            label = "Folders",
                            selected = selectedTab == 1,
                            onClick = { selectedTab = 1 }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DrawerItem(icon: ImageVector, label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) DarkSurface else Color.Transparent)
            .clickable(onClick = onClick).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = if (selected) HeaderBlue else Color.White.copy(alpha = 0.7f), modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(14.dp))
        Text(label, fontSize = 15.sp, color = if (selected) Color.White else Color.White.copy(alpha = 0.7f), fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal)
        if (selected) {
            Spacer(Modifier.weight(1f))
            Box(modifier = Modifier.width(3.dp).height(22.dp).background(HeaderBlue, RoundedCornerShape(2.dp)))
        }
    }
}

@Composable
private fun TableCard(
    table: TableModel, 
    isLoading: Boolean, 
    onClick: () -> Unit, 
    onDelete: () -> Unit, 
    onRename: ((String) -> Unit)?,
    onMoveToFolder: ((Long?) -> Unit)? = null,
    availableFolders: List<FolderModel> = emptyList(),
    folderTableCounts: Map<Long, Int> = emptyMap()
) {
    var showMenu by remember { mutableStateOf(false) }
    var showRename by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }
    var showMoveToFolder by remember { mutableStateOf(false) }
    val dateFormat = remember { SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()) }
    
    Card(
        modifier = Modifier.fillMaxWidth().clickable(enabled = !isLoading, onClick = onClick),
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
                            if (onMoveToFolder != null) {
                                DropdownMenuItem(
                                    text = { Text("Move to Folder") }, 
                                    onClick = { showMenu = false; showMoveToFolder = true }, 
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
            
            // Tags - Hide all labels for clean UI
            // Labels completely hidden as requested
            
            // Date time
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Schedule, null, tint = Color.Gray.copy(alpha = 0.6f), modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text(dateFormat.format(Date(table.updatedAt)), fontSize = 11.sp, color = Color.Gray.copy(alpha = 0.8f))
            }
        }
    }
    
    // Rename Dialog
    if (showRename && onRename != null) {
        var newName by remember { mutableStateOf(table.name) }
        AlertDialog(
            onDismissRequest = { showRename = false },
            title = { Text("Rename Table") },
            text = { OutlinedTextField(value = newName, onValueChange = { newName = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth()) },
            confirmButton = { TextButton(onClick = { if (newName.isNotBlank()) { onRename(newName.trim()); showRename = false } }) { Text("Save") } },
            dismissButton = { TextButton(onClick = { showRename = false }) { Text("Cancel") } }
        )
    }
    
    // Move to Folder Dialog
    if (showMoveToFolder && onMoveToFolder != null) {
        
        AlertDialog(
            onDismissRequest = { showMoveToFolder = false },
            title = { Text("Move to Folder") },
            text = {
                Column {
                    Text("Select a folder for '${table.name}':")
                    Spacer(Modifier.height(16.dp))
                    
                    // Root folder option
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { 
                                onMoveToFolder(null)
                                showMoveToFolder = false 
                            }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Home, null, tint = HeaderBlue)
                        Spacer(Modifier.width(12.dp))
                        Text("Root (No Folder)", fontWeight = FontWeight.Medium)
                    }
                    
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    
                    // Available folders
                    availableFolders.forEach { folder ->
                        val folderColor = try {
                            Color(android.graphics.Color.parseColor(folder.colorHex))
                        } catch (e: Exception) {
                            HeaderBlue
                        }
                        val tableCount = folderTableCounts[folder.id] ?: 0
                        
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { 
                                    onMoveToFolder(folder.id)
                                    showMoveToFolder = false 
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Folder, null, tint = folderColor)
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(folder.name, fontWeight = FontWeight.Medium)
                                Text("$tableCount tables", fontSize = 12.sp, color = Color.Gray)
                            }
                        }
                    }
                }
            },
            confirmButton = { },
            dismissButton = {
                TextButton(onClick = { showMoveToFolder = false }) {
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
            confirmButton = { TextButton(onClick = { onDelete(); showDelete = false }) { Text("Delete", color = Color(0xFFEF4444)) } },
            dismissButton = { TextButton(onClick = { showDelete = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun BottomTabItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            icon,
            contentDescription = label,
            tint = if (selected) HeaderBlue else Color.Gray,
            modifier = Modifier.size(24.dp)
        )
        Spacer(Modifier.height(4.dp))
        Text(
            label,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) HeaderBlue else Color.Gray
        )
    }
}

@Composable
fun CompactFolderCard(
    folder: FolderModel,
    tableCount: Int,
    onClick: () -> Unit
) {
    val folderColor = try {
        Color(android.graphics.Color.parseColor(folder.colorHex))
    } catch (e: Exception) {
        Color(0xFF1976D2)
    }

    Card(
        modifier = Modifier
            .width(160.dp)
            .height(105.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = folderColor.copy(alpha = 0.1f)),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(12.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(Icons.Default.Folder, null, tint = folderColor, modifier = Modifier.size(32.dp))
            Spacer(Modifier.height(8.dp))
            Text(
                folder.name,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = Color.Black,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                "$tableCount tables",
                fontSize = 11.sp,
                color = Color.Gray
            )
        }
    }
}
