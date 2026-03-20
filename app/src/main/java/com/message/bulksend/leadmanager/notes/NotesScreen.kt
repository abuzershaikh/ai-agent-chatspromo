package com.message.bulksend.leadmanager.notes

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

/**
 * Full Notes Screen for a Lead
 * Shows all notes in timeline format with add/edit/delete capabilities
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotesScreen(
    leadId: String,
    leadName: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val notesManager = remember { NotesManager(context) }
    
    var noteGroups by remember { mutableStateOf<List<NoteGroup>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var showAddNoteSheet by remember { mutableStateOf(false) }
    var editingNote by remember { mutableStateOf<Note?>(null) }
    var replyingToNote by remember { mutableStateOf<Note?>(null) }
    var selectedFilter by remember { mutableStateOf<NoteType?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var showSearch by remember { mutableStateOf(false) }
    
    // Load notes
    fun loadNotes() {
        coroutineScope.launch {
            isLoading = true
            noteGroups = if (selectedFilter != null) {
                val filtered = notesManager.getNotesByType(leadId, selectedFilter!!)
                notesManager.run {
                    // Group filtered notes
                    listOf(NoteGroup(
                        date = System.currentTimeMillis(),
                        dateLabel = "${selectedFilter!!.displayName} Notes",
                        notes = filtered
                    ))
                }
            } else if (searchQuery.isNotBlank()) {
                val results = notesManager.searchNotes(leadId, searchQuery)
                listOf(NoteGroup(
                    date = System.currentTimeMillis(),
                    dateLabel = "Search Results",
                    notes = results
                ))
            } else {
                notesManager.getNotesGroupedByDate(leadId)
            }
            isLoading = false
        }
    }
    
    LaunchedEffect(leadId, selectedFilter, searchQuery) {
        loadNotes()
    }
    
    BackHandler { onBack() }
    
    val backgroundBrush = Brush.verticalGradient(
        colors = listOf(Color(0xFF0f0c29), Color(0xFF302b63), Color(0xFF24243e))
    )
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (showSearch) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Search notes...", color = Color(0xFF64748B)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFF3B82F6),
                                unfocusedBorderColor = Color.Transparent,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                cursorColor = Color(0xFF3B82F6)
                            )
                        )
                    } else {
                        Column {
                            Text(
                                "Notes",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                leadName,
                                fontSize = 12.sp,
                                color = Color(0xFF94A3B8)
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (showSearch) {
                            showSearch = false
                            searchQuery = ""
                        } else {
                            onBack()
                        }
                    }) {
                        Icon(
                            if (showSearch) Icons.Default.Close else Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = Color(0xFF10B981)
                        )
                    }
                },
                actions = {
                    if (!showSearch) {
                        IconButton(onClick = { showSearch = true }) {
                            Icon(Icons.Default.Search, "Search", tint = Color(0xFF94A3B8))
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF1a1a2e))
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddNoteSheet = true },
                containerColor = Color(0xFF10B981)
            ) {
                Icon(Icons.Default.Add, "Add Note", tint = Color.White)
            }
        },
        containerColor = Color.Transparent
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(backgroundBrush)
                .padding(padding)
        ) {
            Column {
                // Filter chips
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        FilterChip(
                            selected = selectedFilter == null,
                            onClick = { selectedFilter = null },
                            label = { Text("All") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFF3B82F6),
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                    items(NoteType.entries.toList()) { type ->
                        FilterChip(
                            selected = selectedFilter == type,
                            onClick = { 
                                selectedFilter = if (selectedFilter == type) null else type 
                            },
                            label = { Text(type.displayName) },
                            leadingIcon = {
                                Icon(
                                    getNoteTypeIcon(type),
                                    null,
                                    modifier = Modifier.size(16.dp),
                                    tint = if (selectedFilter == type) Color.White else Color(type.color)
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(type.color),
                                selectedLabelColor = Color.White,
                                containerColor = Color(type.color).copy(alpha = 0.2f),
                                labelColor = Color(type.color)
                            )
                        )
                    }
                }
                
                // Notes Timeline
                if (isLoading) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = Color(0xFF10B981))
                    }
                } else {
                    NotesTimeline(
                        noteGroups = noteGroups,
                        onNoteClick = { /* Expand handled internally */ },
                        onEditNote = { editingNote = it },
                        onDeleteNote = { note ->
                            coroutineScope.launch {
                                notesManager.deleteNote(note.id)
                                loadNotes()
                            }
                        },
                        onPinNote = { note ->
                            coroutineScope.launch {
                                notesManager.togglePin(note.id)
                                loadNotes()
                            }
                        },
                        onAddReply = { replyingToNote = it }
                    )
                }
            }
        }
    }
    
    // Add/Edit Note Bottom Sheet
    if (showAddNoteSheet || editingNote != null) {
        AddEditNoteSheet(
            note = editingNote,
            onDismiss = {
                showAddNoteSheet = false
                editingNote = null
            },
            onSave = { title, content, noteType, priority, tags ->
                coroutineScope.launch {
                    if (editingNote != null) {
                        notesManager.updateNote(
                            id = editingNote!!.id,
                            title = title,
                            content = content,
                            noteType = noteType,
                            priority = priority,
                            tags = tags
                        )
                    } else {
                        notesManager.addNote(
                            leadId = leadId,
                            title = title,
                            content = content,
                            noteType = noteType,
                            priority = priority,
                            tags = tags
                        )
                    }
                    showAddNoteSheet = false
                    editingNote = null
                    loadNotes()
                }
            }
        )
    }
    
    // Reply Sheet
    if (replyingToNote != null) {
        ReplyNoteSheet(
            parentNote = replyingToNote!!,
            onDismiss = { replyingToNote = null },
            onReply = { content ->
                coroutineScope.launch {
                    notesManager.addReply(replyingToNote!!.id, content)
                    replyingToNote = null
                    loadNotes()
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditNoteSheet(
    note: Note?,
    onDismiss: () -> Unit,
    onSave: (String, String, NoteType, NotePriority, List<String>) -> Unit
) {
    var title by remember { mutableStateOf(note?.title ?: "") }
    var content by remember { mutableStateOf(note?.content ?: "") }
    var selectedType by remember { mutableStateOf(note?.noteType ?: NoteType.GENERAL) }
    var selectedPriority by remember { mutableStateOf(note?.priority ?: NotePriority.NORMAL) }
    var tags by remember { mutableStateOf(note?.tags ?: emptyList()) }
    var newTag by remember { mutableStateOf("") }
    var showTypeMenu by remember { mutableStateOf(false) }
    var showPriorityMenu by remember { mutableStateOf(false) }
    
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF1a1a2e)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            Text(
                if (note == null) "Add Note" else "Edit Note",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            
            // Title
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Title") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF3B82F6),
                    unfocusedBorderColor = Color(0xFF334155),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedLabelColor = Color(0xFF3B82F6),
                    unfocusedLabelColor = Color(0xFF64748B)
                )
            )
            
            // Content
            OutlinedTextField(
                value = content,
                onValueChange = { content = it },
                label = { Text("Content") },
                minLines = 4,
                maxLines = 8,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF3B82F6),
                    unfocusedBorderColor = Color(0xFF334155),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedLabelColor = Color(0xFF3B82F6),
                    unfocusedLabelColor = Color(0xFF64748B)
                )
            )
            
            // Type & Priority Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Note Type
                Box(modifier = Modifier.weight(1f)) {
                    OutlinedTextField(
                        value = selectedType.displayName,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Type") },
                        leadingIcon = {
                            Icon(
                                getNoteTypeIcon(selectedType),
                                null,
                                tint = Color(selectedType.color)
                            )
                        },
                        trailingIcon = {
                            IconButton(onClick = { showTypeMenu = true }) {
                                Icon(Icons.Default.ArrowDropDown, null, tint = Color(0xFF64748B))
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showTypeMenu = true },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(selectedType.color),
                            unfocusedBorderColor = Color(0xFF334155),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )
                    
                    DropdownMenu(
                        expanded = showTypeMenu,
                        onDismissRequest = { showTypeMenu = false }
                    ) {
                        NoteType.entries.forEach { type ->
                            DropdownMenuItem(
                                text = { Text(type.displayName) },
                                onClick = {
                                    selectedType = type
                                    showTypeMenu = false
                                },
                                leadingIcon = {
                                    Icon(
                                        getNoteTypeIcon(type),
                                        null,
                                        tint = Color(type.color)
                                    )
                                }
                            )
                        }
                    }
                }
                
                // Priority
                Box(modifier = Modifier.weight(1f)) {
                    OutlinedTextField(
                        value = selectedPriority.displayName,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Priority") },
                        trailingIcon = {
                            IconButton(onClick = { showPriorityMenu = true }) {
                                Icon(Icons.Default.ArrowDropDown, null, tint = Color(0xFF64748B))
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showPriorityMenu = true },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(selectedPriority.color),
                            unfocusedBorderColor = Color(0xFF334155),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )
                    
                    DropdownMenu(
                        expanded = showPriorityMenu,
                        onDismissRequest = { showPriorityMenu = false }
                    ) {
                        NotePriority.entries.forEach { priority ->
                            DropdownMenuItem(
                                text = { Text(priority.displayName) },
                                onClick = {
                                    selectedPriority = priority
                                    showPriorityMenu = false
                                },
                                leadingIcon = {
                                    Box(
                                        modifier = Modifier
                                            .size(12.dp)
                                            .background(Color(priority.color), CircleShape)
                                    )
                                }
                            )
                        }
                    }
                }
            }
            
            // Tags
            Text("Tags", fontSize = 14.sp, color = Color(0xFF94A3B8))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = newTag,
                    onValueChange = { newTag = it },
                    placeholder = { Text("Add tag") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF3B82F6),
                        unfocusedBorderColor = Color(0xFF334155),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    )
                )
                IconButton(
                    onClick = {
                        if (newTag.isNotBlank()) {
                            tags = tags + newTag.trim()
                            newTag = ""
                        }
                    },
                    modifier = Modifier
                        .size(48.dp)
                        .background(Color(0xFF3B82F6), RoundedCornerShape(8.dp))
                ) {
                    Icon(Icons.Default.Add, null, tint = Color.White)
                }
            }
            
            if (tags.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    tags.forEach { tag ->
                        InputChip(
                            selected = false,
                            onClick = { tags = tags - tag },
                            label = { Text(tag) },
                            trailingIcon = {
                                Icon(Icons.Default.Close, null, modifier = Modifier.size(16.dp))
                            }
                        )
                    }
                }
            }
            
            Spacer(Modifier.height(16.dp))
            
            // Save Button
            Button(
                onClick = {
                    if (title.isNotBlank() && content.isNotBlank()) {
                        onSave(title, content, selectedType, selectedPriority, tags)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                enabled = title.isNotBlank() && content.isNotBlank()
            ) {
                Icon(Icons.Default.Save, null)
                Spacer(Modifier.width(8.dp))
                Text(if (note == null) "Add Note" else "Save Changes", fontWeight = FontWeight.Bold)
            }
            
            Spacer(Modifier.height(32.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReplyNoteSheet(
    parentNote: Note,
    onDismiss: () -> Unit,
    onReply: (String) -> Unit
) {
    var content by remember { mutableStateOf("") }
    val sheetState = rememberModalBottomSheetState()
    
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF1a1a2e)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                "Reply to Note",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            
            // Parent note preview
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                shape = RoundedCornerShape(8.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        parentNote.title,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF94A3B8)
                    )
                    Text(
                        parentNote.content.take(100) + if (parentNote.content.length > 100) "..." else "",
                        fontSize = 12.sp,
                        color = Color(0xFF64748B)
                    )
                }
            }
            
            OutlinedTextField(
                value = content,
                onValueChange = { content = it },
                label = { Text("Your reply") },
                minLines = 3,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF3B82F6),
                    unfocusedBorderColor = Color(0xFF334155),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                )
            )
            
            Button(
                onClick = { if (content.isNotBlank()) onReply(content) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                enabled = content.isNotBlank()
            ) {
                Icon(Icons.Default.Reply, null)
                Spacer(Modifier.width(8.dp))
                Text("Reply", fontWeight = FontWeight.Bold)
            }
            
            Spacer(Modifier.height(32.dp))
        }
    }
}
