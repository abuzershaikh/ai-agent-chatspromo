package com.message.bulksend.tablesheet.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun CreateTableDialog(
    onDismiss: () -> Unit,
    onCreate: (name: String, description: String, tags: String) -> Unit
) {
    var tableName by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var tags by remember { mutableStateOf("") }
    var isError by remember { mutableStateOf(false) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1A1A2E),
        shape = RoundedCornerShape(20.dp),
        title = {
            Text(
                "Create New Table",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedTextField(
                    value = tableName,
                    onValueChange = { 
                        tableName = it
                        isError = false
                    },
                    label = { Text("Table Name *") },
                    placeholder = { Text("e.g., Contacts, Sales, Inventory") },
                    isError = isError,
                    supportingText = if (isError) {
                        { Text("Table name is required", color = Color(0xFFEF4444)) }
                    } else null,
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF6366F1),
                        unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
                        focusedLabelColor = Color(0xFF6366F1),
                        unfocusedLabelColor = Color.White.copy(alpha = 0.6f),
                        cursorColor = Color(0xFF6366F1),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description (Optional)") },
                    placeholder = { Text("Brief description of this table") },
                    maxLines = 2,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF6366F1),
                        unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
                        focusedLabelColor = Color(0xFF6366F1),
                        unfocusedLabelColor = Color.White.copy(alpha = 0.6f),
                        cursorColor = Color(0xFF6366F1),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                
                OutlinedTextField(
                    value = tags,
                    onValueChange = { tags = it },
                    label = { Text("Tags (Optional)") },
                    placeholder = { Text("work, personal, project") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF6366F1),
                        unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
                        focusedLabelColor = Color(0xFF6366F1),
                        unfocusedLabelColor = Color.White.copy(alpha = 0.6f),
                        cursorColor = Color(0xFF6366F1),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (tableName.isBlank()) {
                        isError = true
                    } else {
                        onCreate(tableName.trim(), description.trim(), tags.trim())
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6366F1)),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Create", fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Color.White.copy(alpha = 0.7f))
            }
        }
    )
}
