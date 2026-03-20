package com.message.bulksend.leadmanager.notes

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.*

/**
 * Notes Timeline - Modern CRM style timeline view for notes
 * Similar to Salesforce/HubSpot activity timeline
 */
@Composable
fun NotesTimeline(
    noteGroups: List<NoteGroup>,
    onNoteClick: (Note) -> Unit,
    onEditNote: (Note) -> Unit,
    onDeleteNote: (Note) -> Unit,
    onPinNote: (Note) -> Unit,
    onAddReply: (Note) -> Unit,
    modifier: Modifier = Modifier
) {
    if (noteGroups.isEmpty()) {
        EmptyNotesState(modifier)
        return
    }
    
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        noteGroups.forEach { group ->
            // Date Header
            item(key = "header_${group.date}") {
                DateHeader(dateLabel = group.dateLabel)
            }
            
            // Notes in this group
            items(
                items = group.notes,
                key = { it.id }
            ) { note ->
                NoteTimelineItem(
                    note = note,
                    isLast = note == group.notes.last(),
                    onClick = { onNoteClick(note) },
                    onEdit = { onEditNote(note) },
                    onDelete = { onDeleteNote(note) },
                    onPin = { onPinNote(note) },
                    onReply = { onAddReply(note) }
                )
            }
        }
        
        // Bottom spacing
        item { Spacer(Modifier.height(80.dp)) }
    }
}

@Composable
fun DateHeader(dateLabel: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = Color(0xFF334155)
        )
        Surface(
            color = Color(0xFF1E293B),
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(1.dp, Color(0xFF334155))
        ) {
            Text(
                text = dateLabel,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF94A3B8),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
            )
        }
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = Color(0xFF334155)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteTimelineItem(
    note: Note,
    isLast: Boolean,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onPin: () -> Unit,
    onReply: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())
    val noteColor = Color(note.noteType.color)
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
    ) {
        // Timeline connector column
        Column(
            modifier = Modifier.width(48.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Node circle
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(noteColor.copy(alpha = 0.2f), CircleShape)
                    .border(2.dp, noteColor, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = getNoteTypeIcon(note.noteType),
                    contentDescription = null,
                    tint = noteColor,
                    modifier = Modifier.size(20.dp)
                )
            }
            
            // Connecting line
            if (!isLast) {
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .weight(1f)
                        .background(Color(0xFF334155))
                )
            }
        }
        
        Spacer(Modifier.width(12.dp))
        
        // Note Card
        Card(
            modifier = Modifier
                .weight(1f)
                .padding(bottom = if (isLast) 0.dp else 16.dp)
                .clickable { expanded = !expanded },
            colors = CardDefaults.cardColors(
                containerColor = if (note.isPinned) 
                    noteColor.copy(alpha = 0.1f) 
                else 
                    Color(0xFF1E293B)
            ),
            shape = RoundedCornerShape(12.dp),
            border = if (note.isPinned) BorderStroke(1.dp, noteColor.copy(alpha = 0.3f)) else null
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                // Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        // Pinned badge
                        if (note.isPinned) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(bottom = 4.dp)
                            ) {
                                Icon(
                                    Icons.Default.PushPin,
                                    contentDescription = null,
                                    tint = noteColor,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    "Pinned",
                                    fontSize = 10.sp,
                                    color = noteColor,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                        
                        // Title
                        Text(
                            text = note.title,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = if (expanded) Int.MAX_VALUE else 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        
                        // Type & Time
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(top = 4.dp)
                        ) {
                            // Type badge
                            Surface(
                                color = noteColor.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = note.noteType.displayName,
                                    fontSize = 10.sp,
                                    color = noteColor,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                            
                            // Priority badge if not normal
                            if (note.priority != NotePriority.NORMAL) {
                                Surface(
                                    color = Color(note.priority.color).copy(alpha = 0.2f),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = note.priority.displayName,
                                        fontSize = 10.sp,
                                        color = Color(note.priority.color),
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            
                            // Time
                            Text(
                                text = timeFormat.format(Date(note.createdAt)),
                                fontSize = 11.sp,
                                color = Color(0xFF64748B)
                            )
                            
                            // Edited indicator
                            if (note.isEdited) {
                                Text(
                                    text = "(edited)",
                                    fontSize = 10.sp,
                                    color = Color(0xFF64748B),
                                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                                )
                            }
                        }
                    }
                    
                    // Menu button
                    Box {
                        IconButton(
                            onClick = { showMenu = true },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.Default.MoreVert,
                                contentDescription = "More",
                                tint = Color(0xFF64748B),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(if (note.isPinned) "Unpin" else "Pin") },
                                onClick = { onPin(); showMenu = false },
                                leadingIcon = {
                                    Icon(
                                        if (note.isPinned) Icons.Default.PushPin else Icons.Default.PushPin,
                                        null,
                                        tint = noteColor
                                    )
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Edit") },
                                onClick = { onEdit(); showMenu = false },
                                leadingIcon = {
                                    Icon(Icons.Default.Edit, null, tint = Color(0xFF3B82F6))
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Reply") },
                                onClick = { onReply(); showMenu = false },
                                leadingIcon = {
                                    Icon(Icons.Default.Reply, null, tint = Color(0xFF10B981))
                                }
                            )
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text("Delete", color = Color(0xFFEF4444)) },
                                onClick = { onDelete(); showMenu = false },
                                leadingIcon = {
                                    Icon(Icons.Default.Delete, null, tint = Color(0xFFEF4444))
                                }
                            )
                        }
                    }
                }
                
                Spacer(Modifier.height(8.dp))
                
                // Content
                Text(
                    text = note.content,
                    fontSize = 14.sp,
                    color = Color(0xFFCBD5E1),
                    maxLines = if (expanded) Int.MAX_VALUE else 3,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 20.sp
                )
                
                // Tags
                if (note.tags.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.horizontalScroll(rememberScrollState())
                    ) {
                        note.tags.forEach { tag ->
                            Surface(
                                color = Color(0xFF334155),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Tag,
                                        null,
                                        tint = Color(0xFF64748B),
                                        modifier = Modifier.size(10.dp)
                                    )
                                    Spacer(Modifier.width(2.dp))
                                    Text(tag, fontSize = 10.sp, color = Color(0xFF94A3B8))
                                }
                            }
                        }
                    }
                }
                
                // Attachments indicator
                if (note.hasAttachments) {
                    Spacer(Modifier.height(8.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            Icons.Default.AttachFile,
                            null,
                            tint = Color(0xFF64748B),
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            "${note.attachments.size} attachment(s)",
                            fontSize = 11.sp,
                            color = Color(0xFF64748B)
                        )
                    }
                }
                
                // Replies
                if (note.replies.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    HorizontalDivider(color = Color(0xFF334155))
                    Spacer(Modifier.height(8.dp))
                    
                    Text(
                        "${note.replies.size} replies",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF94A3B8)
                    )
                    
                    AnimatedVisibility(visible = expanded) {
                        Column(
                            modifier = Modifier.padding(top = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            note.replies.forEach { reply ->
                                ReplyItem(reply)
                            }
                        }
                    }
                }
                
                // Expand/Collapse indicator
                if (note.content.length > 150 || note.replies.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { expanded = !expanded },
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            if (expanded) "Show less" else "Show more",
                            fontSize = 12.sp,
                            color = noteColor
                        )
                        Icon(
                            if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            null,
                            tint = noteColor,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ReplyItem(reply: Note) {
    val timeFormat = SimpleDateFormat("MMM dd, hh:mm a", Locale.getDefault())
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF0F172A), RoundedCornerShape(8.dp))
            .padding(12.dp)
    ) {
        Icon(
            Icons.Default.SubdirectoryArrowRight,
            null,
            tint = Color(0xFF64748B),
            modifier = Modifier.size(16.dp)
        )
        Spacer(Modifier.width(8.dp))
        Column {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    reply.createdBy,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF94A3B8)
                )
                Text(
                    timeFormat.format(Date(reply.createdAt)),
                    fontSize = 10.sp,
                    color = Color(0xFF64748B)
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                reply.content,
                fontSize = 13.sp,
                color = Color(0xFFCBD5E1)
            )
        }
    }
}

@Composable
fun EmptyNotesState(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .background(Color(0xFF3B82F6).copy(alpha = 0.2f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.NoteAdd,
                    contentDescription = null,
                    tint = Color(0xFF3B82F6),
                    modifier = Modifier.size(40.dp)
                )
            }
            Text(
                "No notes yet",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                "Add your first note to track\ninteractions with this lead",
                fontSize = 14.sp,
                color = Color(0xFF94A3B8),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

/**
 * Get icon for note type
 */
fun getNoteTypeIcon(noteType: NoteType): ImageVector {
    return when (noteType) {
        NoteType.GENERAL -> Icons.Default.Note
        NoteType.CALL_LOG -> Icons.Default.Phone
        NoteType.MEETING -> Icons.Default.Groups
        NoteType.EMAIL -> Icons.Default.Email
        NoteType.TASK -> Icons.Default.Task
        NoteType.IMPORTANT -> Icons.Default.Star
        NoteType.FOLLOW_UP -> Icons.Default.Schedule
        NoteType.DEAL -> Icons.Default.Handshake
        NoteType.FEEDBACK -> Icons.Default.Feedback
        NoteType.INTERNAL -> Icons.Default.Lock
    }
}
