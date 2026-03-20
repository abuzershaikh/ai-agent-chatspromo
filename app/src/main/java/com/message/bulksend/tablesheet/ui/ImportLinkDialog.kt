package com.message.bulksend.tablesheet.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

@Composable
fun ImportLinkDialog(
    onDismiss: () -> Unit,
    onImport: (String) -> Unit
) {
    var url by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Import from Link",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close"
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Description
                Text(
                    text = "Paste a Google Sheets or Excel Online link to import data",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // URL Input
                OutlinedTextField(
                    value = url,
                    onValueChange = { 
                        url = it
                        errorMessage = null
                    },
                    label = { Text("Sheet URL") },
                    placeholder = { Text("https://docs.google.com/spreadsheets/d/...") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Link,
                            contentDescription = "Link"
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = false,
                    maxLines = 3,
                    isError = errorMessage != null,
                    supportingText = if (errorMessage != null) {
                        { Text(errorMessage!!, color = MaterialTheme.colorScheme.error) }
                    } else null
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Supported formats
                Text(
                    text = "Supported: Google Sheets, Excel Online, Direct CSV/Excel links",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = onDismiss,
                        enabled = !isLoading
                    ) {
                        Text("Cancel")
                    }
                    
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    Button(
                        onClick = {
                            if (url.isBlank()) {
                                errorMessage = "Please enter a URL"
                                return@Button
                            }
                            
                            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                                errorMessage = "URL must start with http:// or https://"
                                return@Button
                            }
                            
                            isLoading = true
                            onImport(url.trim())
                        },
                        enabled = !isLoading && url.isNotBlank()
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text(if (isLoading) "Importing..." else "Import")
                    }
                }
            }
        }
    }
}
