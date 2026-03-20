package com.message.bulksend.notes.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.message.bulksend.notes.NoteCategory
import com.message.bulksend.notes.NoteColorTheme
import com.message.bulksend.notes.database.NoteEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteEditorDialog(
    note: NoteEntity?,
    onDismiss: () -> Unit,
    onSave: (String, String, NoteCategory, NoteColorTheme, Boolean, Boolean) -> Unit
) {
    var title by remember { mutableStateOf(note?.title ?: "") }
    var content by remember { mutableStateOf(note?.content ?: "") }
    var selectedCategory by remember { mutableStateOf(note?.category ?: NoteCategory.GENERAL) }
    var selectedColor by remember { mutableStateOf(note?.colorTheme ?: NoteColorTheme.DEFAULT) }
    var isFavorite by remember { mutableStateOf(note?.isFavorite ?: false) }
    var isPinned by remember { mutableStateOf(note?.isPinned ?: false) }
    var showCategoryPicker by remember { mutableStateOf(false) }
    
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Card(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = selectedColor.primaryColor
            ),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (note == null) "New Note" else "Edit Note",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = selectedColor.textColor
                    )
                    
                    Row {
                        // Favorite Toggle
                        IconButton(onClick = { isFavorite = !isFavorite }) {
                            Icon(
                                if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                contentDescription = "Favorite",
                                tint = if (isFavorite) Color.Red else selectedColor.textColor.copy(alpha = 0.6f)
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
                        
                        // Close
                        IconButton(onClick = onDismiss) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Close",
                                tint = selectedColor.textColor.copy(alpha = 0.6f)
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Title Input - Keep Notes Style (No Outline)
                TextField(
                    value = title,
                    onValueChange = { title = it },
                    placeholder = { 
                        Text(
                            "Title", 
                            color = selectedColor.textColor.copy(alpha = 0.5f),
                            fontSize = 18.sp,
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
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    singleLine = false,
                    maxLines = 3
                )
                
                // Content Input - Keep Notes Style (No Outline)
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
                        .weight(1f),
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
                
                // Bottom Actions Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Category Selection (Compact)
                    Row(
                        modifier = Modifier
                            .clickable { showCategoryPicker = true }
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            selectedCategory.icon,
                            contentDescription = null,
                            tint = selectedColor.textColor.copy(alpha = 0.7f),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            selectedCategory.displayName,
                            color = selectedColor.textColor.copy(alpha = 0.7f),
                            fontSize = 12.sp
                        )
                    }
                    
                    // Save Button (Compact)
                    TextButton(
                        onClick = {
                            onSave(title, content, selectedCategory, selectedColor, isFavorite, isPinned)
                        },
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = Color(0xFF3B82F6)
                        )
                    ) {
                        Text("Done", fontWeight = FontWeight.Bold)
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Color Theme Selection (Horizontal)
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(NoteColorTheme.values().toList()) { colorTheme ->
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(colorTheme.primaryColor)
                                .clickable { selectedColor = colorTheme },
                            contentAlignment = Alignment.Center
                        ) {
                            if (selectedColor == colorTheme) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = null,
                                    tint = colorTheme.textColor,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
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
}