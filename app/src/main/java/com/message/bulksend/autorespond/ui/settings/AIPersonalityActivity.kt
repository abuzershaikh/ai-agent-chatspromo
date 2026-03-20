package com.message.bulksend.autorespond.ui.settings

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Save
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
import com.message.bulksend.autorespond.ai.settings.AIAgentSettingsManager

class AIPersonalityActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = androidx.compose.ui.graphics.Color(0xFF8B5CF6),
                    secondary = androidx.compose.ui.graphics.Color(0xFF6366F1),
                    background = androidx.compose.ui.graphics.Color(0xFF0F0F23)
                )
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AIPersonalityScreen(onBack = { finish() })
                }
            }
        }
    }
}

@Composable
fun AIPersonalityScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val settingsManager = remember { AIAgentSettingsManager(context) }
    
    var systemPrompt by remember { mutableStateOf(settingsManager.customSystemPrompt) }
    var productInstruction by remember { mutableStateOf(settingsManager.productInstruction) }
    var sheetInstruction by remember { mutableStateOf(settingsManager.sheetInstruction) }
    var memoryInstruction by remember { mutableStateOf(settingsManager.memoryInstruction) }
    var advancedInstruction by remember { mutableStateOf(settingsManager.advancedInstruction) }
    
    var showResetDialog by remember { mutableStateOf(false) }
    
    val scrollState = rememberScrollState()
    
    // Reset to defaults function
    fun resetToDefaults() {
        systemPrompt = ""
        productInstruction = ""
        sheetInstruction = ""
        memoryInstruction = ""
        advancedInstruction = ""
        
        settingsManager.customSystemPrompt = ""
        settingsManager.productInstruction = ""
        settingsManager.sheetInstruction = ""
        settingsManager.memoryInstruction = ""
        settingsManager.advancedInstruction = ""
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F0F23))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
        ) {
            // Header
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.linearGradient(
                            colors = listOf(Color(0xFF8B5CF6), Color(0xFF6366F1))
                        )
                    )
                    .padding(top = 48.dp, bottom = 24.dp, start = 20.dp, end = 20.dp)
            ) {
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = onBack,
                                modifier = Modifier
                                    .background(Color.White.copy(0.2f), CircleShape)
                                    .size(36.dp)
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                    "Back",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(Modifier.width(16.dp))
                            Text(
                                "AI Brain Config",
                                fontSize = 26.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                        
                        // Reset Button
                        TextButton(
                            onClick = { showResetDialog = true },
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = Color.White
                            )
                        ) {
                            Icon(
                                Icons.Default.RestartAlt,
                                contentDescription = "Reset",
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text("Reset")
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Configure the AI's intelligence, memory, and behavior.",
                        color = Color.White.copy(0.8f),
                        fontSize = 14.sp
                    )
                }
            }
            
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                // System Prompt
                PromptField(
                    title = "System Personality",
                    description = "Who is the AI? What is its role and tone?",
                    value = systemPrompt,
                    onValueChange = { 
                        systemPrompt = it
                        settingsManager.customSystemPrompt = it
                    },
                    placeholder = "You are a helpful assistant for XYZ company..."
                )
                
                // Product Instructions
                PromptField(
                    title = "Product & Catalogue Logic",
                    description = "How to present products and when to send catalogues?",
                    value = productInstruction,
                    onValueChange = { 
                        productInstruction = it
                        settingsManager.productInstruction = it
                    },
                    placeholder = "Show only 3 items in list. Always mention discount..."
                )
                
                // Sheet Instructions
                PromptField(
                    title = "Sheet Processing Logic",
                    description = "How should the AI handle data from sheets?",
                    value = sheetInstruction,
                    onValueChange = { 
                        sheetInstruction = it
                        settingsManager.sheetInstruction = it
                    },
                    placeholder = "If user asks for price, look at column Price..."
                )
                
                // Memory Instructions
                PromptField(
                    title = "Memory & Context",
                    description = "How should the AI use previous conversation history?",
                    value = memoryInstruction,
                    onValueChange = { 
                        memoryInstruction = it
                        settingsManager.memoryInstruction = it
                    },
                    placeholder = "Remember user preferences but ignore small talk..."
                )
                
                // Advanced Instructions
                PromptField(
                    title = "Advanced Intelligence",
                    description = "High-priority override rules and logic.",
                    value = advancedInstruction,
                    onValueChange = { 
                        advancedInstruction = it
                        settingsManager.advancedInstruction = it
                    },
                    placeholder = "ALWAYS prioritize sheet data. NEVER say..."
                )
                
                Spacer(Modifier.height(80.dp)) // Extra space at bottom
            }
        }
        
        // Floating Save Button
        FloatingActionButton(
            onClick = onBack,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(24.dp),
            containerColor = Color(0xFF8B5CF6),
            contentColor = Color.White
        ) {
            Icon(Icons.Default.Save, "Save")
        }
        
        // Reset Confirmation Dialog
        if (showResetDialog) {
            AlertDialog(
                onDismissRequest = { showResetDialog = false },
                title = {
                    Text(
                        "Reset to Defaults?",
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Text(
                        "This will clear all custom prompts and instructions. The AI will use default behavior. This action cannot be undone.",
                        color = Color.White.copy(0.8f)
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            resetToDefaults()
                            showResetDialog = false
                            android.widget.Toast.makeText(context, "Reset to defaults", android.widget.Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFFF6B6B)
                        )
                    ) {
                        Text("Reset", color = Color.White)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showResetDialog = false }) {
                        Text("Cancel", color = Color.White)
                    }
                },
                containerColor = Color(0xFF1A1A2E),
                titleContentColor = Color.White,
                textContentColor = Color.White
            )
        }
    }
}

@Composable
fun PromptField(
    title: String,
    description: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String
) {
    Column {
        Text(
            title,
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            description,
            color = Color.White.copy(0.6f),
            fontSize = 13.sp,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            minLines = 5,
            placeholder = { Text(placeholder, color = Color.White.copy(0.3f)) },
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color(0xFF8B5CF6),
                unfocusedBorderColor = Color(0xFF2A2A4A),
                cursorColor = Color(0xFF8B5CF6),
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedLabelColor = Color(0xFF8B5CF6),
                unfocusedLabelColor = Color.White.copy(0.5f),
                focusedContainerColor = Color(0xFF1A1A2E),
                unfocusedContainerColor = Color(0xFF1A1A2E),
                focusedPlaceholderColor = Color.White.copy(0.3f),
                unfocusedPlaceholderColor = Color.White.copy(0.3f)
            )
        )
    }
}
