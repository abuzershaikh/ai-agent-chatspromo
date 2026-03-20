package com.message.bulksend.notes

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.message.bulksend.notes.database.NoteEntity
import com.message.bulksend.notes.viewmodel.NotesViewModel
import com.message.bulksend.ui.theme.BulksendTestTheme
import kotlinx.coroutines.launch

class NewNotesActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BulksendTestTheme {
                NewNotesApp()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun NewNotesApp() {
    val context = LocalContext.current
    val viewModel: NotesViewModel = viewModel()
    val scope = rememberCoroutineScope()
    
    // UI State
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf<NoteCategory?>(null) }
    var sortOrder by remember { mutableStateOf(SortOrder.LATEST_FIRST) }
    var showSortMenu by remember { mutableStateOf(false) }
    var showCategoryFilter by remember { mutableStateOf(false) }
    var showSearchBar by remember { mutableStateOf(false) }
    var noteToDelete by remember { mutableStateOf<NoteEntity?>(null) }
    
    // Observe notes from ViewModel
    val notes by viewModel.notes.collectAsState()
    
    // Filter and sort notes
    val filteredNotes = remember(notes, searchQuery, selectedCategory, sortOrder) {
        val filtered = notes.filter { note ->
            (searchQuery.isBlank() || 
             note.title.contains(searchQuery, ignoreCase = true) || 
             note.content.contains(searchQuery, ignoreCase = true)) &&
            (selectedCategory == null || note.category == selectedCategory)
        }
        
        when (sortOrder) {
            SortOrder.LATEST_FIRST -> filtered.sortedByDescending { it.lastModified }
            SortOrder.OLDEST_FIRST -> filtered.sortedBy { it.lastModified }
            SortOrder.TITLE_ASC -> filtered.sortedBy { it.title }
            SortOrder.TITLE_DESC -> filtered.sortedByDescending { it.title }
            SortOrder.CATEGORY -> filtered.sortedBy { it.category.displayName }
            SortOrder.FAVORITES_FIRST -> filtered.sortedWith(
                compareByDescending<NoteEntity> { it.isFavorite }
                    .thenByDescending { it.isPinned }
                    .thenByDescending { it.lastModified }
            )
        }
    }
    
    // Beautiful Black & Blue Theme Background
    val backgroundBrush = Brush.verticalGradient(
        colors = listOf(
            Color(0xFF0F172A), // Space black
            Color(0xFF1E293B), // Navy blue
            Color(0xFF334155)  // Slate blue
        )
    )
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundBrush)
    ) {
        Scaffold(
            topBar = {
                // Enhanced TopAppBar with larger height
                Column {
                    TopAppBar(
                        title = { 
                            Column {
                                Text(
                                    "Smart Notes", 
                                    color = Color(0xFF60A5FA),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 20.sp
                                )
                                if (searchQuery.isNotEmpty() || selectedCategory != null) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            "${filteredNotes.size} notes found",
                                            fontSize = 12.sp,
                                            color = Color(0xFF94A3B8)
                                        )
                                        if (searchQuery.isNotEmpty() || selectedCategory != null) {
                                            Spacer(modifier = Modifier.width(8.dp))
                                            TextButton(
                                                onClick = {
                                                    searchQuery = ""
                                                    selectedCategory = null
                                                    showSearchBar = false
                                                },
                                                modifier = Modifier.height(24.dp),
                                                contentPadding = PaddingValues(horizontal = 8.dp)
                                            ) {
                                                Text(
                                                    "Clear",
                                                    fontSize = 10.sp,
                                                    color = Color(0xFF60A5FA)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        },
                    navigationIcon = {
                        IconButton(onClick = { (context as ComponentActivity).finish() }) {
                            Icon(
                                Icons.Default.ArrowBack, 
                                contentDescription = "Back",
                                tint = Color(0xFF60A5FA)
                            )
                        }
                    },
                    actions = {
                        // Search Toggle with Badge
                        Box {
                            IconButton(onClick = { 
                                showSearchBar = !showSearchBar
                                if (!showSearchBar) {
                                    searchQuery = "" // Clear search when hiding
                                }
                            }) {
                                Icon(
                                    Icons.Default.Search, 
                                    contentDescription = "Search",
                                    tint = if (showSearchBar || searchQuery.isNotEmpty()) Color(0xFF3B82F6) else Color(0xFF60A5FA),
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            // Active search indicator
                            if (searchQuery.isNotEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .background(Color(0xFF10B981), CircleShape)
                                        .align(Alignment.TopEnd)
                                )
                            }
                        }
                        
                        // Sort
                        IconButton(onClick = { showSortMenu = true }) {
                            Icon(
                                Icons.Default.Sort, 
                                contentDescription = "Sort",
                                tint = Color(0xFF60A5FA)
                            )
                        }
                        
                        DropdownMenu(
                            expanded = showSortMenu,
                            onDismissRequest = { showSortMenu = false }
                        ) {
                            SortOrder.values().forEach { order ->
                                DropdownMenuItem(
                                    text = { Text(order.displayName) },
                                    onClick = {
                                        sortOrder = order
                                        showSortMenu = false
                                    }
                                )
                            }
                        }
                        
                        // Category Filter
                        IconButton(onClick = { showCategoryFilter = true }) {
                            Icon(
                                Icons.Default.FilterList, 
                                contentDescription = "Filter",
                                tint = Color(0xFF60A5FA)
                            )
                        }
                        
                        DropdownMenu(
                            expanded = showCategoryFilter,
                            onDismissRequest = { showCategoryFilter = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("All Categories") },
                                onClick = {
                                    selectedCategory = null
                                    showCategoryFilter = false
                                }
                            )
                            NoteCategory.values().forEach { category ->
                                DropdownMenuItem(
                                    text = { 
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                category.icon, 
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(category.displayName)
                                        }
                                    },
                                    onClick = {
                                        selectedCategory = category
                                        showCategoryFilter = false
                                    }
                                )
                            }
                        }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = Color(0xFF1E293B)
                        ),
                        modifier = Modifier.height(if (searchQuery.isNotEmpty() || selectedCategory != null) 80.dp else 64.dp)
                    )
                    
                    // Search Bar directly below TopAppBar
                    AnimatedVisibility(
                        visible = showSearchBar,
                        enter = slideInVertically(
                            initialOffsetY = { -it }
                        ) + fadeIn(),
                        exit = slideOutVertically(
                            targetOffsetY = { -it }
                        ) + fadeOut()
                    ) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = Color(0xFF1E293B),
                            shadowElevation = 4.dp
                        ) {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = Color(0xFF334155)
                                ),
                                shape = RoundedCornerShape(24.dp),
                                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                            ) {
                                TextField(
                                    value = searchQuery,
                                    onValueChange = { searchQuery = it },
                                    placeholder = { 
                                        Text(
                                            "Search notes by title or content...", 
                                            color = Color(0xFF94A3B8),
                                            fontSize = 16.sp
                                        ) 
                                    },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Default.Search,
                                            contentDescription = null,
                                            tint = Color(0xFF60A5FA),
                                            modifier = Modifier.size(24.dp)
                                        )
                                    },
                                    trailingIcon = {
                                        if (searchQuery.isNotEmpty()) {
                                            IconButton(onClick = { searchQuery = "" }) {
                                                Icon(
                                                    Icons.Default.Clear,
                                                    contentDescription = "Clear",
                                                    tint = Color(0xFF94A3B8),
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                        }
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 8.dp, vertical = 4.dp),
                                    colors = TextFieldDefaults.colors(
                                        focusedTextColor = Color(0xFFE0E6ED),
                                        unfocusedTextColor = Color(0xFFE0E6ED),
                                        focusedContainerColor = Color.Transparent,
                                        unfocusedContainerColor = Color.Transparent,
                                        focusedIndicatorColor = Color.Transparent,
                                        unfocusedIndicatorColor = Color.Transparent,
                                        cursorColor = Color(0xFF60A5FA)
                                    ),
                                    textStyle = androidx.compose.ui.text.TextStyle(
                                        fontSize = 16.sp
                                    ),
                                    singleLine = true
                                )
                            }
                        }
                    }
                }
            },
            floatingActionButton = {
                FloatingActionButton(
                    onClick = {
                        context.startActivity(NoteEditorActivity.createIntent(context))
                    },
                    containerColor = Color(0xFF3B82F6),
                    contentColor = Color.White
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add Note")
                }
            },
            containerColor = Color.Transparent
        ) { padding ->
            
            if (filteredNotes.isEmpty()) {
                // Empty State
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            Icons.Outlined.Note,
                            contentDescription = null,
                            modifier = Modifier.size(80.dp),
                            tint = Color(0xFF60A5FA)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            if (searchQuery.isNotEmpty()) "No notes found" else "No notes yet",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFFE0E6ED)
                        )
                        Text(
                            if (searchQuery.isNotEmpty()) "Try a different search term" else "Tap + to create your first note",
                            fontSize = 14.sp,
                            color = Color(0xFF94A3B8)
                        )
                    }
                }
            } else {
                // Notes Grid
                LazyVerticalStaggeredGrid(
                    columns = StaggeredGridCells.Fixed(2),
                    contentPadding = padding,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalItemSpacing = 8.dp,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp)
                ) {
                    items(filteredNotes) { note ->
                        NoteCard(
                            note = note,
                            onClick = {
                                context.startActivity(NoteEditorActivity.createIntent(context, note.id))
                            },
                            onToggleFavorite = {
                                scope.launch {
                                    viewModel.toggleFavorite(note.id, !note.isFavorite)
                                }
                            },
                            onTogglePin = {
                                scope.launch {
                                    viewModel.togglePin(note.id, !note.isPinned)
                                }
                            },
                            onDelete = {
                                noteToDelete = note
                            }
                        )
                    }
                }
            }
        }
    }
    
    // Delete Confirmation Dialog
    noteToDelete?.let { note ->
        AlertDialog(
            onDismissRequest = { noteToDelete = null },
            title = { Text("Delete Note") },
            text = { Text("Are you sure you want to delete \"${note.title}\"?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            viewModel.deleteNote(note.id)
                            noteToDelete = null
                            Toast.makeText(context, "Note deleted", Toast.LENGTH_SHORT).show()
                        }
                    }
                ) {
                    Text("Delete", color = Color.Red)
                }
            },
            dismissButton = {
                TextButton(onClick = { noteToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun NoteCard(
    note: NoteEntity,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    onTogglePin: () -> Unit,
    onDelete: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = note.colorTheme.primaryColor
        ),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 24.dp)
            ) {
                // Header with category and menu - Added significant top spacing
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            note.category.icon,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = note.colorTheme.textColor.copy(alpha = 0.7f)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            note.category.displayName,
                            fontSize = 10.sp,
                            color = note.colorTheme.textColor.copy(alpha = 0.7f)
                        )
                    }
                    
                    Box {
                        IconButton(
                            onClick = { showMenu = true },
                            modifier = Modifier.size(20.dp)
                        ) {
                            Icon(
                                Icons.Default.MoreVert,
                                contentDescription = "Menu",
                                modifier = Modifier.size(16.dp),
                                tint = note.colorTheme.textColor.copy(alpha = 0.7f)
                            )
                        }
                        
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(if (note.isFavorite) "Remove from Favorites" else "Add to Favorites") },
                                onClick = {
                                    onToggleFavorite()
                                    showMenu = false
                                },
                                leadingIcon = {
                                    Icon(
                                        if (note.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                        contentDescription = null
                                    )
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(if (note.isPinned) "Unpin" else "Pin") },
                                onClick = {
                                    onTogglePin()
                                    showMenu = false
                                },
                                leadingIcon = {
                                    Icon(
                                        if (note.isPinned) Icons.Default.PushPin else Icons.Outlined.PushPin,
                                        contentDescription = null
                                    )
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Delete") },
                                onClick = {
                                    onDelete()
                                    showMenu = false
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.Delete, contentDescription = null)
                                }
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Title - Keep Notes Style
                if (note.title.isNotBlank()) {
                    Text(
                        text = note.title,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = note.colorTheme.textColor,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                }
                
                // Content - Keep Notes Style
                if (note.content.isNotBlank()) {
                    Text(
                        text = note.content,
                        fontSize = 14.sp,
                        color = note.colorTheme.textColor.copy(alpha = 0.9f),
                        maxLines = if (note.title.isBlank()) 8 else 6,
                        overflow = TextOverflow.Ellipsis,
                        lineHeight = 20.sp
                    )
                } else if (note.title.isBlank()) {
                    Text(
                        text = "Empty note",
                        fontSize = 14.sp,
                        color = note.colorTheme.textColor.copy(alpha = 0.5f),
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Footer with indicators
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row {
                        if (note.isPinned) {
                            Icon(
                                Icons.Default.PushPin,
                                contentDescription = "Pinned",
                                modifier = Modifier.size(12.dp),
                                tint = note.colorTheme.textColor.copy(alpha = 0.6f)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                        }
                        if (note.isFavorite) {
                            Icon(
                                Icons.Default.Favorite,
                                contentDescription = "Favorite",
                                modifier = Modifier.size(12.dp),
                                tint = Color.Red.copy(alpha = 0.8f)
                            )
                        }
                    }
                    
                    Text(
                        text = java.text.SimpleDateFormat("MMM dd", java.util.Locale.getDefault())
                            .format(java.util.Date(note.lastModified)),
                        fontSize = 10.sp,
                        color = note.colorTheme.textColor.copy(alpha = 0.6f)
                    )
                }
            }
        }
    }
}