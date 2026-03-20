package com.message.bulksend.aiagent.tools.shipping

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.LocalShipping
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.message.bulksend.ui.theme.BulksendTestTheme
import kotlinx.coroutines.launch

/**
 * Lets user connect Shiprocket Dedicated API credentials.
 */
class ShiprocketSetupActivity : ComponentActivity() {

    private lateinit var shiprocketManager: ShiprocketManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        shiprocketManager = ShiprocketManager(this)

        setContent {
            BulksendTestTheme {
                ShiprocketSetupScreen(
                    shiprocketManager = shiprocketManager,
                    onBackPressed = { finish() },
                    onSetupComplete = {
                        Toast.makeText(this, "Shiprocket connected successfully.", Toast.LENGTH_LONG).show()
                        finish()
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShiprocketSetupScreen(
    shiprocketManager: ShiprocketManager,
    onBackPressed: () -> Unit,
    onSetupComplete: () -> Unit
) {
    val scope = rememberCoroutineScope()

    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf("") }
    var isError by remember { mutableStateOf(false) }

    val backgroundBrush = Brush.verticalGradient(
        colors = listOf(Color(0xFF0F0C29), Color(0xFF302B63), Color(0xFF24243E))
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundBrush)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(24.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBackPressed) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White)
                }
                Icon(
                    Icons.Outlined.LocalShipping,
                    null,
                    tint = Color(0xFF6366F1),
                    modifier = Modifier.size(28.dp)
                )
                Spacer(Modifier.width(10.dp))
                Column {
                    Text("Shiprocket Setup", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Color.White)
                    Text("Connect your shipping account", fontSize = 12.sp, color = Color(0xFF94A3B8))
                }
            }

            Spacer(Modifier.height(16.dp))

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1B4B).copy(alpha = 0.8f)),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF6366F1).copy(alpha = 0.4f))
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(Icons.Outlined.Info, null, tint = Color(0xFF6366F1), modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(
                            "Dedicated API User Required",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = Color.White
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Main Shiprocket account login is not supported here. " +
                                "Create a separate API user:\n\n" +
                                "Shiprocket Dashboard -> Settings -> API -> Add New API User",
                            fontSize = 12.sp,
                            color = Color(0xFF94A3B8),
                            lineHeight = 18.sp
                        )
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E2E).copy(alpha = 0.9f))
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text("API User Credentials", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.White)
                    Spacer(Modifier.height(16.dp))

                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = { Text("Dedicated API Email") },
                        placeholder = { Text("apiuser@yourbusiness.com") },
                        leadingIcon = { Icon(Icons.Outlined.Email, null, tint = Color(0xFF6366F1)) },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF6366F1),
                            unfocusedBorderColor = Color(0xFF334155),
                            focusedLabelColor = Color(0xFF6366F1),
                            unfocusedLabelColor = Color(0xFF94A3B8),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            cursorColor = Color(0xFF6366F1)
                        )
                    )

                    Spacer(Modifier.height(12.dp))

                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("API User Password") },
                        leadingIcon = { Icon(Icons.Outlined.Lock, null, tint = Color(0xFF6366F1)) },
                        trailingIcon = {
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(
                                    if (passwordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                    null,
                                    tint = Color(0xFF94A3B8)
                                )
                            }
                        },
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF6366F1),
                            unfocusedBorderColor = Color(0xFF334155),
                            focusedLabelColor = Color(0xFF6366F1),
                            unfocusedLabelColor = Color(0xFF94A3B8),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            cursorColor = Color(0xFF6366F1)
                        )
                    )

                    Spacer(Modifier.height(20.dp))

                    if (statusMessage.isNotEmpty()) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isError) Color(0xFFFF6B6B).copy(alpha = 0.15f)
                                else Color(0xFF10B981).copy(alpha = 0.15f)
                            )
                        ) {
                            Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    if (isError) Icons.Outlined.ErrorOutline else Icons.Outlined.CheckCircle,
                                    null,
                                    tint = if (isError) Color(0xFFFF6B6B) else Color(0xFF10B981),
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    statusMessage,
                                    fontSize = 13.sp,
                                    color = if (isError) Color(0xFFFF6B6B) else Color(0xFF10B981)
                                )
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                    }

                    Button(
                        onClick = {
                            if (email.isBlank() || password.isBlank()) {
                                isError = true
                                statusMessage = "Email and password are required."
                                return@Button
                            }
                            scope.launch {
                                isLoading = true
                                statusMessage = ""
                                val result = shiprocketManager.setupShiprocketCredentials(email.trim(), password)
                                isLoading = false
                                result.onSuccess {
                                    isError = false
                                    statusMessage = it
                                    onSetupComplete()
                                }.onFailure {
                                    isError = true
                                    statusMessage = it.message ?: "Setup failed"
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(14.dp),
                        enabled = !isLoading,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF6366F1),
                            disabledContainerColor = Color(0xFF6366F1).copy(alpha = 0.5f)
                        )
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                            Spacer(Modifier.width(10.dp))
                            Text("Connecting...", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        } else {
                            Icon(Icons.Filled.Link, null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Connect Shiprocket", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E2E).copy(alpha = 0.7f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("After Setup", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = Color.White)
                    Spacer(Modifier.height(12.dp))
                    listOf(
                        "AI agent can place shipping orders",
                        "Real-time shipment tracking via webhook",
                        "Automatic token renewal every 8 days",
                        "Customers receive shipping status updates on WhatsApp"
                    ).forEach { text ->
                        Row(modifier = Modifier.padding(vertical = 4.dp)) {
                            Text("-", fontSize = 16.sp, color = Color(0xFF94A3B8))
                            Spacer(Modifier.width(10.dp))
                            Text(text, fontSize = 13.sp, color = Color(0xFF94A3B8))
                        }
                    }
                }
            }

            Spacer(Modifier.height(40.dp))
        }
    }
}
