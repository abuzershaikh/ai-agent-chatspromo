package com.message.bulksend.aiagent.tools.delhivery

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
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
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.LocalShipping
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Webhook
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.message.bulksend.ui.theme.BulksendTestTheme
import kotlinx.coroutines.launch

/**
 * Delhivery Setup Screen
 *
 * User enters:
 *  - Delhivery API Token (from Delhivery Dashboard → Settings → API Setup)
 *  - Registered Pickup Location name (exact name from Delhivery warehouse)
 *  - Email used on Delhivery account (for webhook URL generation)
 *
 * On Save:
 *  - Worker validates token with Delhivery
 *  - Returns unique Webhook URL for this user
 *  - Webhook URL is displayed with a Copy button
 */
class DelhiverySetupActivity : ComponentActivity() {

    private lateinit var manager: DelhiveryManager

    // Form fields
    private var apiToken by mutableStateOf("")
    private var pickupLocation by mutableStateOf("")
    private var email by mutableStateOf("")
    private var tokenVisible by mutableStateOf(false)

    // State
    private var webhookUrl by mutableStateOf("")
    private var statusMessage by mutableStateOf("")
    private var statusIsError by mutableStateOf(false)
    private var isLoading by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        manager = DelhiveryManager(this)

        setContent {
            BulksendTestTheme {
                DelhiverySetupScreen(
                    apiToken = apiToken,
                    onApiTokenChange = { apiToken = it },
                    tokenVisible = tokenVisible,
                    onToggleTokenVisible = { tokenVisible = !tokenVisible },
                    pickupLocation = pickupLocation,
                    onPickupLocationChange = { pickupLocation = it },
                    email = email,
                    onEmailChange = { email = it },
                    webhookUrl = webhookUrl,
                    statusMessage = statusMessage,
                    statusIsError = statusIsError,
                    isLoading = isLoading,
                    onBackPressed = { finish() },
                    onSaveClick = { doSetup() },
                    onCopyWebhookClick = { copyWebhookUrl() }
                )
            }
        }

        loadExistingConfig()
    }

    private fun loadExistingConfig() {
        lifecycleScope.launch {
            val config = manager.getConfig() ?: return@launch
            apiToken = config.apiToken
            pickupLocation = config.pickupLocation
            email = config.email
            webhookUrl = config.webhookUrl
            if (config.webhookUrl.isNotBlank()) {
                statusMessage = "Delhivery connected. Setup at: ${config.setupAt}"
                statusIsError = false
            }
        }
    }

    private fun doSetup() {
        val token = apiToken.trim()
        val location = pickupLocation.trim()
        val userEmail = email.trim().ifBlank { manager.currentUserEmail }

        when {
            token.isBlank() -> { setStatus("API Token is required", true); return }
            location.isBlank() -> { setStatus("Pickup Location name is required", true); return }
            userEmail.isBlank() -> { setStatus("Email is required", true); return }
        }

        isLoading = true
        setStatus("", false)

        lifecycleScope.launch {
            try {
                val result = manager.setup(
                    apiToken = token,
                    pickupLocation = location,
                    email = userEmail
                )

                if (result.isSuccess) {
                    val config = result.getOrThrow()
                    webhookUrl = config.webhookUrl
                    setStatus(
                        "✓ Delhivery connected! Copy the webhook URL below and register it in your Delhivery account.",
                        false
                    )
                    Toast.makeText(
                        this@DelhiverySetupActivity,
                        "Delhivery setup successful",
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    val err = result.exceptionOrNull()?.message ?: "Unknown error"
                    setStatus("Error: $err", true)
                }
            } catch (e: Exception) {
                setStatus("Exception: ${e.message}", true)
            } finally {
                isLoading = false
            }
        }
    }

    private fun copyWebhookUrl() {
        val url = webhookUrl.trim()
        if (url.isBlank()) return
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Delhivery Webhook URL", url))
        Toast.makeText(this, "Webhook URL copied!", Toast.LENGTH_SHORT).show()
    }

    private fun setStatus(message: String, isError: Boolean) {
        statusMessage = message
        statusIsError = isError
    }
}

// ── Composable UI ──────────────────────────────────────────────────────────

@Composable
fun DelhiverySetupScreen(
    apiToken: String,
    onApiTokenChange: (String) -> Unit,
    tokenVisible: Boolean,
    onToggleTokenVisible: () -> Unit,
    pickupLocation: String,
    onPickupLocationChange: (String) -> Unit,
    email: String,
    onEmailChange: (String) -> Unit,
    webhookUrl: String,
    statusMessage: String,
    statusIsError: Boolean,
    isLoading: Boolean,
    onBackPressed: () -> Unit,
    onSaveClick: () -> Unit,
    onCopyWebhookClick: () -> Unit
) {
    val backgroundBrush = Brush.verticalGradient(
        colors = listOf(Color(0xFF001F3D), Color(0xFF003366), Color(0xFF00264D))
    )

    val accentColor = Color(0xFF00B4D8)
    val accentDark = Color(0xFF0077A8)

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
            Spacer(Modifier.height(28.dp))

            // ── Top Bar ────────────────────────────────────────────────
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
                    tint = accentColor,
                    modifier = Modifier.size(30.dp)
                )
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(
                        "Delhivery Setup",
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        color = Color.White
                    )
                    Text(
                        "Connect your courier account",
                        fontSize = 12.sp,
                        color = Color(0xFF94A3B8)
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // ── Info Card ──────────────────────────────────────────────
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(
                    containerColor = accentColor.copy(alpha = 0.12f)
                ),
                border = androidx.compose.foundation.BorderStroke(1.dp, accentColor.copy(alpha = 0.35f))
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        Icons.Outlined.Info,
                        null,
                        tint = accentColor,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(
                            "Where to get API Token",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                            color = Color.White
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Delhivery Dashboard → Settings → API Setup → Generate Token\n\n" +
                                "Pickup Location: use the exact warehouse name registered in Delhivery " +
                                "(case-sensitive).",
                            fontSize = 12.sp,
                            color = Color(0xFFB0C4D8),
                            lineHeight = 18.sp
                        )
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            // ── Credentials Card ───────────────────────────────────────
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF0A1628).copy(alpha = 0.95f)
                )
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        "Delhivery Credentials",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = Color.White
                    )
                    Spacer(Modifier.height(16.dp))

                    // API Token
                    OutlinedTextField(
                        value = apiToken,
                        onValueChange = onApiTokenChange,
                        label = { Text("Delhivery API Token") },
                        placeholder = { Text("Paste your token here") },
                        leadingIcon = {
                            Icon(Icons.Outlined.Lock, null, tint = accentColor)
                        },
                        trailingIcon = {
                            IconButton(onClick = onToggleTokenVisible) {
                                Icon(
                                    if (tokenVisible)
                                        Icons.Filled.VisibilityOff
                                    else
                                        Icons.Filled.Visibility,
                                    null,
                                    tint = Color(0xFF94A3B8)
                                )
                            }
                        },
                        visualTransformation = if (tokenVisible)
                            VisualTransformation.None
                        else
                            PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = delhiveryFieldColors(accentColor)
                    )

                    Spacer(Modifier.height(12.dp))

                    // Pickup Location
                    OutlinedTextField(
                        value = pickupLocation,
                        onValueChange = onPickupLocationChange,
                        label = { Text("Pickup Location Name") },
                        placeholder = { Text("e.g. Primary Warehouse") },
                        leadingIcon = {
                            Icon(Icons.Outlined.Place, null, tint = accentColor)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = delhiveryFieldColors(accentColor)
                    )

                    Spacer(Modifier.height(12.dp))

                    // Email
                    OutlinedTextField(
                        value = email,
                        onValueChange = onEmailChange,
                        label = { Text("Delhivery Account Email") },
                        placeholder = { Text("your@email.com") },
                        leadingIcon = {
                            Icon(
                                Icons.Outlined.Email,
                                null,
                                tint = accentColor
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        colors = delhiveryFieldColors(accentColor)
                    )

                    Spacer(Modifier.height(20.dp))

                    // Status banner
                    if (statusMessage.isNotBlank()) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (statusIsError)
                                    Color(0xFFFF4444).copy(alpha = 0.15f)
                                else
                                    Color(0xFF00C896).copy(alpha = 0.15f)
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Icon(
                                    if (statusIsError) Icons.Outlined.ErrorOutline
                                    else Icons.Outlined.CheckCircle,
                                    null,
                                    tint = if (statusIsError) Color(0xFFFF6B6B) else Color(0xFF00C896),
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    statusMessage,
                                    fontSize = 13.sp,
                                    color = if (statusIsError) Color(0xFFFF6B6B) else Color(0xFF00C896),
                                    lineHeight = 18.sp
                                )
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                    }

                    // Save Button
                    Button(
                        onClick = onSaveClick,
                        enabled = !isLoading,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = accentColor,
                            disabledContainerColor = accentDark.copy(alpha = 0.5f)
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
                            Icon(
                                Icons.Outlined.LocalShipping,
                                null,
                                modifier = Modifier.size(20.dp),
                                tint = Color.White
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "Save & Connect Delhivery",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }
            }

            // ── Webhook URL Card (appears after setup) ─────────────────
            if (webhookUrl.isNotBlank()) {
                Spacer(Modifier.height(20.dp))

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFF003D1A).copy(alpha = 0.85f)
                    ),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp, Color(0xFF00C896).copy(alpha = 0.4f)
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Outlined.Webhook,
                                null,
                                tint = Color(0xFF00C896),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "Your Webhook URL",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = Color.White
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Register this URL in Delhivery Dashboard → Settings → Webhook:",
                            fontSize = 12.sp,
                            color = Color(0xFF94A3B8),
                            lineHeight = 17.sp
                        )
                        Spacer(Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = webhookUrl,
                                fontSize = 12.sp,
                                color = Color(0xFF00C896),
                                modifier = Modifier.weight(1f),
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis
                            )
                            IconButton(onClick = onCopyWebhookClick) {
                                Icon(
                                    Icons.Outlined.ContentCopy,
                                    contentDescription = "Copy",
                                    tint = Color(0xFF00C896)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            // ── After Setup Info ───────────────────────────────────────
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF0A1628).copy(alpha = 0.7f)
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "What happens after setup",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        color = Color.White
                    )
                    Spacer(Modifier.height(10.dp))
                    listOf(
                        "AI agent creates courier orders from WhatsApp messages",
                        "Real-time shipment tracking via your webhook URL",
                        "Order history stored under customer phone number",
                        "COD & Prepaid shipments both supported",
                        "Pincode serviceability checks before order creation",
                        "Shipping label generation and pickup scheduling"
                    ).forEach { item ->
                        Row(
                            modifier = Modifier.padding(vertical = 3.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Text("•", fontSize = 14.sp, color = accentColor)
                            Spacer(Modifier.width(8.dp))
                            Text(item, fontSize = 12.sp, color = Color(0xFF94A3B8))
                        }
                    }
                }
            }

            Spacer(Modifier.height(40.dp))
        }
    }
}

@Composable
private fun delhiveryFieldColors(accentColor: Color) = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = accentColor,
    unfocusedBorderColor = Color(0xFF334155),
    focusedLabelColor = accentColor,
    unfocusedLabelColor = Color(0xFF94A3B8),
    focusedTextColor = Color.White,
    unfocusedTextColor = Color.White,
    cursorColor = accentColor,
    focusedPlaceholderColor = Color(0xFF475569),
    unfocusedPlaceholderColor = Color(0xFF475569)
)
