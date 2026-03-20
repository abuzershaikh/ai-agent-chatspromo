package com.message.bulksend.notes

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.imePadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.message.bulksend.notes.database.NoteEntity
import com.message.bulksend.notes.viewmodel.NotesViewModel
import com.message.bulksend.ui.theme.BulksendTestTheme
import kotlinx.coroutines.launch

class NoteEditorActivity : ComponentActivity() {
    
    companion object {
        const val EXTRA_NOTE_ID = "note_id"
        
        fun createIntent(context: Context, noteId: Long? = null): Intent {
            return Intent(context, NoteEditorActivity::class.java).apply {
                noteId?.let { putExtra(EXTRA_NOTE_ID, it) }
            }
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        val noteId = intent.getLongExtra(EXTRA_NOTE_ID, -1L).takeIf { it != -1L }
        
        setContent {
            BulksendTestTheme {
                NoteEditorScreen(
                    noteId = noteId,
                    onBackPressed = { finish() }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteEditorScreen(
    noteId: Long?,
    onBackPressed: () -> Unit
) {
    val context = LocalContext.current
    val viewModel: NotesViewModel = viewModel()
    val scope = rememberCoroutineScope()
    
    // Note state
    var note by remember { mutableStateOf<NoteEntity?>(null) }
    var title by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf(NoteCategory.GENERAL) }
    var selectedColor by remember { mutableStateOf(NoteColorTheme.DEFAULT) }
    var isFavorite by remember { mutableStateOf(false) }
    var isPinned by remember { mutableStateOf(false) }
    var showCategoryPicker by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showColorPicker by remember { mutableStateOf(false) }
    
    // Original values to track changes
    var originalTitle by remember { mutableStateOf("") }
    var originalContent by remember { mutableStateOf("") }
    var originalCategory by remember { mutableStateOf(NoteCategory.GENERAL) }
    var originalColor by remember { mutableStateOf(NoteColorTheme.DEFAULT) }
    var originalIsFavorite by remember { mutableStateOf(false) }
    var originalIsPinned by remember { mutableStateOf(false) }
    
    // Intelligent content and change detection
    val hasContent = title.trim().isNotBlank() || content.trim().isNotBlank()
    val hasChanges = title.trim() != originalTitle.trim() || 
                    content.trim() != originalContent.trim() || 
                    selectedCategory != originalCategory || 
                    selectedColor != originalColor || 
                    isFavorite != originalIsFavorite || 
                    isPinned != originalIsPinned
    
    // Show save button only if there's meaningful content AND changes were made
    val showSaveButton = hasContent && hasChanges
    
    // Auto-save functionality
    var lastAutoSave by remember { mutableStateOf(0L) }
    val shouldAutoSave = hasContent && hasChanges
    
    // Auto-save after 3 seconds of inactivity
    LaunchedEffect(title, content, selectedCategory, selectedColor, isFavorite, isPinned) {
        if (shouldAutoSave) {
            kotlinx.coroutines.delay(3000) // Wait 3 seconds
            if (shouldAutoSave) { // Check again after delay
                saveNote(
                    viewModel = viewModel,
                    scope = scope,
                    context = context,
                    note = note,
                    title = title.trim(),
                    content = content.trim(),
                    selectedCategory = selectedCategory,
                    selectedColor = selectedColor,
                    isFavorite = isFavorite,
                    isPinned = isPinned,
                    onComplete = { 
                        lastAutoSave = System.currentTimeMillis()
                    },
                    showToast = false, // Silent auto-save
                    autoSave = true
                )
            }
        }
    }
    
    // Load existing note if editing
    LaunchedEffect(noteId) {
        noteId?.let { id ->
            scope.launch {
                viewModel.repository.getNoteById(id)?.let { existingNote ->
                    note = existingNote
                    title = existingNote.title
                    content = existingNote.content
                    selectedCategory = existingNote.category
                    selectedColor = existingNote.colorTheme
                    isFavorite = existingNote.isFavorite
                    isPinned = existingNote.isPinned
                    
                    // Set original values for change tracking
                    originalTitle = existingNote.title
                    originalContent = existingNote.content
                    originalCategory = existingNote.category
                    originalColor = existingNote.colorTheme
                    originalIsFavorite = existingNote.isFavorite
                    originalIsPinned = existingNote.isPinned
                }
            }
        }
    }
    
    // Handle back button with intelligent save logic
    BackHandler {
        handleBackPress(
            hasContent = hasContent,
            hasChanges = hasChanges,
            viewModel = viewModel,
            scope = scope,
            context = context,
            note = note,
            title = title.trim(),
            content = content.trim(),
            selectedCategory = selectedCategory,
            selectedColor = selectedColor,
            isFavorite = isFavorite,
            isPinned = isPinned,
            onComplete = onBackPressed
        )
    }
    
    // Full screen note editor
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(selectedColor.primaryColor)
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Top Bar with Enhanced Actions
            TopAppBar(
                title = { 
                    // Category indicator in title area
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            selectedCategory.icon,
                            contentDescription = null,
                            tint = selectedColor.textColor.copy(alpha = 0.7f),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            selectedCategory.displayName,
                            color = selectedColor.textColor.copy(alpha = 0.7f),
                            fontSize = 12.sp
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            handleBackPress(
                                hasContent = hasContent,
                                hasChanges = hasChanges,
                                viewModel = viewModel,
                                scope = scope,
                                context = context,
                                note = note,
                                title = title.trim(),
                                content = content.trim(),
                                selectedCategory = selectedCategory,
                                selectedColor = selectedColor,
                                isFavorite = isFavorite,
                                isPinned = isPinned,
                                onComplete = onBackPressed
                            )
                        }
                    ) {
                        Icon(
                            Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = selectedColor.textColor
                        )
                    }
                },
                actions = {
                    // Color Picker Toggle
                    IconButton(onClick = { showColorPicker = !showColorPicker }) {
                        Icon(
                            Icons.Default.Palette,
                            contentDescription = "Colors",
                            tint = selectedColor.textColor.copy(alpha = 0.8f)
                        )
                    }
                    
                    // Pin Toggle
                    IconButton(onClick = { isPinned = !isPinned }) {
                        Icon(
                            if (isPinned) Icons.Default.PushPin else Icons.Default.PushPin,
                            contentDescription = "Pin",
                            tint = if (isPinned) selectedColor.textColor else selectedColor.textColor.copy(alpha = 0.6f)
                        )
                    }
                    
                    // Favorite Toggle
                    IconButton(onClick = { isFavorite = !isFavorite }) {
                        Icon(
                            if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = "Favorite",
                            tint = if (isFavorite) Color.Red else selectedColor.textColor.copy(alpha = 0.6f)
                        )
                    }
                    
                    // Save Button (only show when there's content AND changes)
                    if (showSaveButton) {
                        TextButton(
                            onClick = {
                                saveNote(
                                    viewModel = viewModel,
                                    scope = scope,
                                    context = context,
                                    note = note,
                                    title = title.trim(),
                                    content = content.trim(),
                                    selectedCategory = selectedCategory,
                                    selectedColor = selectedColor,
                                    isFavorite = isFavorite,
                                    isPinned = isPinned,
                                    onComplete = { 
                                        // Update original values after save
                                        originalTitle = title.trim()
                                        originalContent = content.trim()
                                        originalCategory = selectedCategory
                                        originalColor = selectedColor
                                        originalIsFavorite = isFavorite
                                        originalIsPinned = isPinned
                                        lastAutoSave = System.currentTimeMillis()
                                    },
                                    showToast = true
                                )
                            }
                        ) {
                            Text(
                                "Save",
                                color = selectedColor.textColor,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    
                    // More options
                    var showMenu by remember { mutableStateOf(false) }
                    IconButton(onClick = { showMenu = true }) {
                        Icon(
                            Icons.Default.MoreVert,
                            contentDescription = "More",
                            tint = selectedColor.textColor.copy(alpha = 0.6f)
                        )
                    }
                    
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Category") },
                            onClick = {
                                showCategoryPicker = true
                                showMenu = false
                            },
                            leadingIcon = {
                                Icon(selectedCategory.icon, contentDescription = null)
                            }
                        )
                        if (note != null) {
                            DropdownMenuItem(
                                text = { Text("Delete") },
                                onClick = {
                                    showDeleteDialog = true
                                    showMenu = false
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.Delete, contentDescription = null)
                                }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = selectedColor.primaryColor
                )
            )
            
            // Color Picker Row (show/hide based on toggle)
            AnimatedVisibility(
                visible = showColorPicker,
                enter = slideInVertically() + fadeIn(),
                exit = slideOutVertically() + fadeOut()
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = selectedColor.textColor.copy(alpha = 0.1f)
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(16.dp)
                    ) {
                        items(NoteColorTheme.values().toList()) { colorTheme ->
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(colorTheme.primaryColor)
                                    .clickable { 
                                        selectedColor = colorTheme
                                        showColorPicker = false
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                if (selectedColor == colorTheme) {
                                    Icon(
                                        Icons.Default.Check,
                                        contentDescription = null,
                                        tint = colorTheme.textColor,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
            
            // Note Content with Scrolling
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
                    .imePadding() // This handles keyboard padding
            ) {
                // Title Input - Keep Notes Style
                TextField(
                    value = title,
                    onValueChange = { title = it },
                    placeholder = { 
                        Text(
                            "Title", 
                            color = selectedColor.textColor.copy(alpha = 0.5f),
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold
                        ) 
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = TextFieldDefaults.colors(
                        focusedTextColor = selectedColor.textColor,
                        unfocusedTextColor = selectedColor.textColor,
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        cursorColor = selectedColor.textColor
                    ),
                    textStyle = androidx.compose.ui.text.TextStyle(
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    singleLine = false
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Content Input - Keep Notes Style with proper height
                TextField(
                    value = content,
                    onValueChange = { content = it },
                    placeholder = { 
                        Text(
                            "Take a note...", 
                            color = selectedColor.textColor.copy(alpha = 0.5f),
                            fontSize = 16.sp
                        ) 
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 200.dp, max = 600.dp),
                    colors = TextFieldDefaults.colors(
                        focusedTextColor = selectedColor.textColor,
                        unfocusedTextColor = selectedColor.textColor,
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        cursorColor = selectedColor.textColor
                    ),
                    textStyle = androidx.compose.ui.text.TextStyle(
                        fontSize = 16.sp,
                        lineHeight = 24.sp
                    )
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Bottom Info Section with Save Status
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Save status indicator
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        when {
                            !hasContent -> {
                                Icon(
                                    Icons.Default.Edit,
                                    contentDescription = null,
                                    modifier = Modifier.size(12.dp),
                                    tint = selectedColor.textColor.copy(alpha = 0.4f)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    "Start typing...",
                                    fontSize = 10.sp,
                                    color = selectedColor.textColor.copy(alpha = 0.4f)
                                )
                            }
                            hasChanges -> {
                                Icon(
                                    Icons.Default.Circle,
                                    contentDescription = null,
                                    modifier = Modifier.size(8.dp),
                                    tint = Color(0xFFEF4444) // Red dot for unsaved changes
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    "Unsaved changes",
                                    fontSize = 10.sp,
                                    color = selectedColor.textColor.copy(alpha = 0.6f)
                                )
                            }
                            else -> {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    modifier = Modifier.size(12.dp),
                                    tint = Color(0xFF10B981) // Green for saved
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    "Saved",
                                    fontSize = 10.sp,
                                    color = selectedColor.textColor.copy(alpha = 0.6f)
                                )
                            }
                        }
                    }
                    
                    // Stats and last modified info
                    Column(horizontalAlignment = Alignment.End) {
                        if (hasContent) {
                            val wordCount = content.trim().split("\\s+".toRegex()).filter { it.isNotBlank() }.size
                            val charCount = content.length
                            Text(
                                text = "$wordCount words, $charCount chars",
                                fontSize = 9.sp,
                                color = selectedColor.textColor.copy(alpha = 0.4f)
                            )
                        }
                        note?.let {
                            Text(
                                text = "Edited ${java.text.SimpleDateFormat("MMM dd, HH:mm", java.util.Locale.getDefault()).format(java.util.Date(it.lastModified))}",
                                fontSize = 10.sp,
                                color = selectedColor.textColor.copy(alpha = 0.5f)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(100.dp)) // Extra space for keyboard
            }
        }
    }
    
    // Category Picker Dialog
    if (showCategoryPicker) {
        AlertDialog(
            onDismissRequest = { showCategoryPicker = false },
            title = { Text("Select Category") },
            text = {
                Column {
                    NoteCategory.values().forEach { category ->
                        TextButton(
                            onClick = {
                                selectedCategory = category
                                showCategoryPicker = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(
                                    category.icon,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(category.displayName)
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showCategoryPicker = false }) {
                    Text("Cancel")
                }
            }
        )
    }
    
    // Delete Confirmation Dialog
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Note") },
            text = { Text("Are you sure you want to delete this note?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        note?.let { noteToDelete ->
                            scope.launch {
                                viewModel.deleteNote(noteToDelete.id)
                                Toast.makeText(context, "Note deleted", Toast.LENGTH_SHORT).show()
                                onBackPressed()
                            }
                        }
                    }
                ) {
                    Text("Delete", color = Color.Red)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

// Intelligent back press handler
private fun handleBackPress(
    hasContent: Boolean,
    hasChanges: Boolean,
    viewModel: NotesViewModel,
    scope: kotlinx.coroutines.CoroutineScope,
    context: Context,
    note: NoteEntity?,
    title: String,
    content: String,
    selectedCategory: NoteCategory,
    selectedColor: NoteColorTheme,
    isFavorite: Boolean,
    isPinned: Boolean,
    onComplete: () -> Unit
) {
    when {
        // Case 1: No content at all - just exit (discard empty note)
        !hasContent -> {
            Toast.makeText(context, "Empty note discarded", Toast.LENGTH_SHORT).show()
            onComplete()
        }
        // Case 2: Has content and changes - auto-save
        hasContent && hasChanges -> {
            saveNote(
                viewModel = viewModel,
                scope = scope,
                context = context,
                note = note,
                title = title,
                content = content,
                selectedCategory = selectedCategory,
                selectedColor = selectedColor,
                isFavorite = isFavorite,
                isPinned = isPinned,
                onComplete = onComplete,
                showToast = true,
                autoSave = true
            )
        }
        // Case 3: Has content but no changes - just exit
        else -> {
            onComplete()
        }
    }
}

private fun saveNote(
    viewModel: NotesViewModel,
    scope: kotlinx.coroutines.CoroutineScope,
    context: Context,
    note: NoteEntity?,
    title: String,
    content: String,
    selectedCategory: NoteCategory,
    selectedColor: NoteColorTheme,
    isFavorite: Boolean,
    isPinned: Boolean,
    onComplete: () -> Unit,
    showToast: Boolean = false,
    autoSave: Boolean = false
) {
    // Only save if there's meaningful content (not just whitespace)
    val trimmedTitle = title.trim()
    val trimmedContent = content.trim()
    
    if (trimmedTitle.isNotBlank() || trimmedContent.isNotBlank()) {
        scope.launch {
            try {
                if (note != null) {
                    // Update existing note
                    val updatedNote = note.copy(
                        title = trimmedTitle,
                        content = trimmedContent,
                        category = selectedCategory,
                        colorTheme = selectedColor,
                        isFavorite = isFavorite,
                        isPinned = isPinned,
                        lastModified = System.currentTimeMillis()
                    )
                    viewModel.updateNote(updatedNote)
                } else {
                    // Create new note
                    val newNote = NoteEntity(
                        title = trimmedTitle,
                        content = trimmedContent,
                        category = selectedCategory,
                        colorTheme = selectedColor,
                        isFavorite = isFavorite,
                        isPinned = isPinned
                    )
                    viewModel.insertNote(newNote)
                }
                
                if (showToast) {
                    val message = if (autoSave) "Note auto-saved" else "Note saved"
                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                }
                onComplete()
            } catch (e: Exception) {
                Toast.makeText(context, "Failed to save note", Toast.LENGTH_SHORT).show()
            }
        }
    } else {
        // No meaningful content - discard
        if (showToast) {
            Toast.makeText(context, "Empty note discarded", Toast.LENGTH_SHORT).show()
        }
        onComplete()
    }
}