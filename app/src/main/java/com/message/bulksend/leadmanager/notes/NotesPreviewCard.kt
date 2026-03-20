package com.message.bulksend.leadmanager.notes

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

/**
 * Expandable Notes Preview Card for Lead Detail Screen
 * Shows recent notes with expand/collapse functionality
 */
@Composable
fun NotesPreviewCard(
    leadId: String,
    onViewAllNotes: () -> Unit,
    onAddNote: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val notesManager = remember { NotesManager(context) }
    
    var recentNotes by remember { mutableStateOf<List<Note>>(emptyList()) }
    var notesCount by remember { mutableStateOf(0) }
    var isExpanded by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(true) }
    
    LaunchedEffect(leadId) {
        isLoading = true
        recentNotes = notesManager.getRecentNotes(leadId, 5)
        notesCount = notesManager.getNotesCount(leadId)
        isLoading = false
    }
    
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1a1a2e)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(Color(0xFF3B82F6).copy(alpha = 0.2f), RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Notes,
                            contentDescription = null,
                            tint = Color(0xFF3B82F6),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Column {
                        Text(
                            "Notes",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            "$notesCount notes",
                            fontSize = 12.sp,
                            color = Color(0xFF64748B)
                        )
                    }
                }
                
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    // Add Note Button
                    IconButton(
                        onClick = onAddNote,
                        modifier = Modifier
                            .size(36.dp)
                            .background(Color(0xFF10B981).copy(alpha = 0.2f), CircleShape)
                    ) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = "Add Note",
                            tint = Color(0xFF10B981),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    
                    // Expand/Collapse
                    IconButton(
                        onClick = { isExpanded = !isExpanded },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = if (isExpanded) "Collapse" else "Expand",
                            tint = Color(0xFF64748B)
                        )
                    }
                }
            }
            
            // Expanded Content
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier.padding(top = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (isLoading) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(100.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                color = Color(0xFF3B82F6),
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp
                            )
                        }
                    } else if (recentNotes.isEmpty()) {
                        // Empty state
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    Icons.Default.NoteAdd,
                                    contentDescription = null,
                                    tint = Color(0xFF64748B),
                                    modifier = Modifier.size(32.dp)
                                )
                                Text(
                                    "No notes yet",
                                    fontSize = 14.sp,
                                    color = Color(0xFF94A3B8)
                                )
                                TextButton(onClick = onAddNote) {
                                    Icon(
                                        Icons.Default.Add,
                                        null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text("Add first note")
                                }
                            }
                        }
                    } else {
                        // Notes list (newest first - already sorted)
                        recentNotes.forEach { note ->
                            NotePreviewItem(note = note)
                        }
                        
                        // View All Button
                        if (notesCount > 5) {
                            TextButton(
                                onClick = onViewAllNotes,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    "View all $notesCount notes",
                                    color = Color(0xFF3B82F6)
                                )
                                Icon(
                                    Icons.Default.ArrowForward,
                                    null,
                                    tint = Color(0xFF3B82F6),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
            
            // Collapsed Preview (show latest note)
            AnimatedVisibility(
                visible = !isExpanded && recentNotes.isNotEmpty(),
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    val latestNote = recentNotes.firstOrNull()
                    if (latestNote != null) {
                        NotePreviewItem(note = latestNote, compact = true)
                        
                        if (notesCount > 1) {
                            Text(
                                "+${notesCount - 1} more notes",
                                fontSize = 12.sp,
                                color = Color(0xFF64748B),
                                modifier = Modifier
                                    .padding(top = 8.dp)
                                    .clickable { isExpanded = true }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun NotePreviewItem(
    note: Note,
    compact: Boolean = false
) {
    val timeFormat = SimpleDateFormat("MMM dd, hh:mm a", Locale.getDefault())
    val noteColor = Color(note.noteType.color)
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF0F172A), RoundedCornerShape(10.dp))
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Type Icon
        Box(
            modifier = Modifier
                .size(32.dp)
                .background(noteColor.copy(alpha = 0.2f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                getNoteTypeIcon(note.noteType),
                contentDescription = null,
                tint = noteColor,
                modifier = Modifier.size(16.dp)
            )
        }
        
        Column(modifier = Modifier.weight(1f)) {
            // Title & Pin
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (note.isPinned) {
                        Icon(
                            Icons.Default.PushPin,
                            null,
                            tint = noteColor,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                    Text(
                        note.title,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                
                // Priority badge
                if (note.priority != NotePriority.NORMAL) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(Color(note.priority.color), CircleShape)
                    )
                }
            }
            
            // Content preview
            if (!compact) {
                Text(
                    note.content,
                    fontSize = 13.sp,
                    color = Color(0xFF94A3B8),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            
            // Meta info
            Row(
                modifier = Modifier.padding(top = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Type badge
                Surface(
                    color = noteColor.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        note.noteType.displayName,
                        fontSize = 10.sp,
                        color = noteColor,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
                
                // Time
                Text(
                    timeFormat.format(Date(note.createdAt)),
                    fontSize = 11.sp,
                    color = Color(0xFF64748B)
                )
                
                // Edited indicator
                if (note.isEdited) {
                    Text(
                        "(edited)",
                        fontSize = 10.sp,
                        color = Color(0xFF64748B),
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                    )
                }
                
                // Replies count
                if (note.replies.isNotEmpty()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Icon(
                            Icons.Default.Reply,
                            null,
                            tint = Color(0xFF64748B),
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            "${note.replies.size}",
                            fontSize = 10.sp,
                            color = Color(0xFF64748B)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Quick Add Note Dialog for Lead Detail
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickAddNoteDialog(
    leadId: String,
    onDismiss: () -> Unit,
    onNoteAdded: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val notesManager = remember { NotesManager(context) }
    
    var content by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf(NoteType.GENERAL) }
    var isLoading by remember { mutableStateOf(false) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Quick Note", color = Color.White, fontWeight = FontWeight.Bold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Type selector
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        NoteType.GENERAL,
                        NoteType.CALL_LOG,
                        NoteType.MEETING,
                        NoteType.IMPORTANT
                    ).forEach { type ->
                        FilterChip(
                            selected = selectedType == type,
                            onClick = { selectedType = type },
                            label = { Text(type.displayName, fontSize = 11.sp) },
                            leadingIcon = {
                                Icon(
                                    getNoteTypeIcon(type),
                                    null,
                                    modifier = Modifier.size(14.dp)
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(type.color),
                                selectedLabelColor = Color.White,
                                selectedLeadingIconColor = Color.White
                            )
                        )
                    }
                }
                
                // Content
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    placeholder = { Text("Write your note...", color = Color(0xFF64748B)) },
                    minLines = 3,
                    maxLines = 6,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(selectedType.color),
                        unfocusedBorderColor = Color(0xFF334155),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    )
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (content.isNotBlank()) {
                        isLoading = true
                        coroutineScope.launch {
                            notesManager.quickAddNote(leadId, content, selectedType)
                            isLoading = false
                            onNoteAdded()
                        }
                    }
                },
                enabled = content.isNotBlank() && !isLoading,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("Save")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Color(0xFF94A3B8))
            }
        },
        containerColor = Color(0xFF1a1a2e)
    )
}
