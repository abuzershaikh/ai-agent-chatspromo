package com.message.bulksend.aiagent.tools.reverseai

import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReverseAIScreen(onBackPressed: () -> Unit) {
    val context = LocalContext.current
    val manager = remember { ReverseAIManager(context) }
    
    var isEnabled by remember { mutableStateOf(manager.isReverseAIEnabled) }
    var ownerPhone by remember { mutableStateOf(manager.ownerPhoneNumber) }
    var requireReminderKeyword by remember { mutableStateOf(manager.requireReminderTriggerKeyword) }
    var reminderKeywords by remember { mutableStateOf(manager.reminderTriggerKeywords) }
    val ownerPhoneDigits = remember(ownerPhone) { ownerPhone.filter { it.isDigit() } }
    val isOwnerPhoneValid = remember(ownerPhone, ownerPhoneDigits) {
        ownerPhone.isBlank() || (ownerPhone.startsWith("+") && ownerPhoneDigits.length >= 7)
    }
    val scrollState = rememberScrollState()

    val backgroundBrush = Brush.verticalGradient(
        colors = listOf(
            Color(0xFF0f0c29),
            Color(0xFF302b63),
            Color(0xFF24243e)
        )
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Reverse AI (Owner Assistant)", color = Color.White, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBackPressed) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF1a1a2e)
                )
            )
        },
        containerColor = Color.Transparent
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(backgroundBrush)
                .verticalScroll(scrollState)
                .padding(innerPadding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Outlined.AutoAwesome,
                contentDescription = "Reverse AI",
                tint = Color(0xFFEAB308),
                modifier = Modifier.size(80.dp)
            )
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "Reverse AI Assistant",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Your personal AI assistant to execute voice commands, manage CRM, and schedule follow-ups autonomously.",
                fontSize = 16.sp,
                color = Color(0xFF94A3B8),
                textAlign = TextAlign.Center,
                lineHeight = 24.sp
            )
            Spacer(modifier = Modifier.height(32.dp))
            
            // Settings Card
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0x33FFFFFF)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Enable Reverse AI",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Switch(
                            checked = isEnabled,
                            onCheckedChange = { 
                                isEnabled = it
                                manager.isReverseAIEnabled = it
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color(0xFFEAB308),
                                checkedTrackColor = Color(0xFFEAB308).copy(alpha = 0.5f)
                            )
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    OutlinedTextField(
                        value = ownerPhone,
                        onValueChange = { 
                            val normalized = normalizeOwnerPhoneInput(it)
                            ownerPhone = normalized
                            manager.ownerPhoneNumber = normalized
                        },
                        label = { Text("Owner Phone Number (with Country Code)", color = Color(0xFF94A3B8)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        isError = ownerPhone.isNotBlank() && !isOwnerPhoneValid,
                        placeholder = { Text("+919137167857", color = Color(0xFF94A3B8)) },
                        supportingText = {
                            if (ownerPhone.isNotBlank() && !isOwnerPhoneValid) {
                                Text(
                                    text = "Enter the number with country code. Example: +919137167857",
                                    color = Color(0xFFFFB4AB)
                                )
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFFEAB308),
                            unfocusedBorderColor = Color(0xFFe2e8f0),
                            errorBorderColor = Color(0xFFFF6B6B),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            errorTextColor = Color.White
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Reverse AI owner number is always saved in +countrycode format.",
                        color = Color(0xFF94A3B8),
                        fontSize = 12.sp
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Reminder Keyword Guard",
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Reminder is scheduled only when the owner instruction includes a keyword.",
                                color = Color(0xFF94A3B8),
                                fontSize = 12.sp
                            )
                        }
                        Switch(
                            checked = requireReminderKeyword,
                            onCheckedChange = {
                                requireReminderKeyword = it
                                manager.requireReminderTriggerKeyword = it
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color(0xFFEAB308),
                                checkedTrackColor = Color(0xFFEAB308).copy(alpha = 0.5f)
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = reminderKeywords,
                        onValueChange = {
                            reminderKeywords = it
                            manager.reminderTriggerKeywords = it
                        },
                        label = { Text("Reminder Trigger Keywords (comma separated)", color = Color(0xFF94A3B8)) },
                        placeholder = { Text("reminder,schedule,follow up,set reminder", color = Color(0xFF94A3B8)) },
                        enabled = requireReminderKeyword,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFFEAB308),
                            unfocusedBorderColor = Color(0xFFe2e8f0),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                        maxLines = 4
                    )
                }
            }
        }
    }
}

private fun normalizeOwnerPhoneInput(value: String): String {
    val raw = value.trim()
    if (raw.isBlank()) return ""

    val digits = raw.filter { it.isDigit() }.take(15)
    if (digits.isBlank()) return if (raw.contains('+')) "+" else ""

    return "+$digits"
}
