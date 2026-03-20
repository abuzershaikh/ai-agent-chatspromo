package com.message.bulksend.aiagent.tools.gmail

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.message.bulksend.autorespond.ai.settings.AIAgentSettingsManager
import com.message.bulksend.autorespond.ai.ui.customai.CustomAIAgentActivity
import com.message.bulksend.ui.theme.BulksendTestTheme
import kotlinx.coroutines.launch

private val GmailBg = Color(0xFF0B1220)
private val GmailCard = Color(0xFF131C31)
private val GmailCardAlt = Color(0xFF18243E)
private val GmailBorder = Color(0xFF304363)
private val GmailAccent = Color(0xFFEA4335)
private val GmailAccentSoft = Color(0xFFFCA5A5)
private val GmailSuccess = Color(0xFF22C55E)
private val GmailTextMuted = Color(0xFF94A3B8)

class GoogleGmailSetupActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            BulksendTestTheme {
                GoogleGmailSetupScreen(onBackPressed = { finish() })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoogleGmailSetupScreen(onBackPressed: () -> Unit) {
    val context = LocalContext.current
    val manager = remember(context) { GoogleGmailManager(context) }
    val settingsManager = remember(context) { AIAgentSettingsManager(context) }
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current

    var clientId by rememberSaveable { mutableStateOf("") }
    var clientSecret by rememberSaveable { mutableStateOf("") }
    var showSecret by rememberSaveable { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var isChecking by remember { mutableStateOf(true) }
    var isConnected by remember { mutableStateOf(false) }
    var isAgentEnabled by remember { mutableStateOf(false) }
    var isCustomTemplateActive by remember { mutableStateOf(false) }
    var isGmailToolEnabled by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf("") }
    val isWorkerConfigured = manager.isWorkerConfigured()
    val isFullAiControlEnabled = isAgentEnabled && isCustomTemplateActive && isGmailToolEnabled

    fun refreshStatus() {
        scope.launch {
            isChecking = true
            isConnected = runCatching { manager.isSetupDone() }.getOrDefault(false)
            isAgentEnabled = settingsManager.isAgentEnabled
            isCustomTemplateActive =
                settingsManager.activeTemplate.equals("CUSTOM", ignoreCase = true)
            isGmailToolEnabled = settingsManager.customTemplateEnableGoogleGmailTool
            isChecking = false
        }
    }

    LaunchedEffect(Unit) { refreshStatus() }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshStatus()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Box(
        modifier =
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    colors = listOf(GmailBg, Color(0xFF101A2E), GmailBg)
                )
            )
    ) {
        Column(
            modifier =
                Modifier.fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBackPressed) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White
                    )
                }
                Spacer(Modifier.width(8.dp))
                Icon(
                    Icons.Filled.Email,
                    contentDescription = null,
                    tint = GmailAccent,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Google Gmail", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 22.sp)
                    Text("Connect worker-based Gmail, tracking, threads, drafts, and labels", color = GmailTextMuted, fontSize = 13.sp)
                }
                StatusChip(
                    text =
                        when {
                            isChecking -> "Checking"
                            isConnected -> "Connected"
                            else -> "Setup"
                        },
                    color = if (isConnected) GmailSuccess else GmailAccent
                )
            }

            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = GmailCard),
                border = androidx.compose.foundation.BorderStroke(1.dp, GmailBorder)
            ) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (isWorkerConfigured) Icons.Filled.CheckCircle else Icons.Filled.Warning,
                            contentDescription = null,
                            tint = if (isWorkerConfigured) GmailSuccess else Color(0xFFF59E0B)
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            if (isWorkerConfigured) "Worker URL configured" else "Worker URL not configured",
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Text(
                        if (isWorkerConfigured) manager.workerBaseUrl else GoogleGmailAgentTool.WORKER_URL,
                        color = GmailAccentSoft,
                        fontSize = 12.sp
                    )
                    Text(
                        if (isWorkerConfigured) {
                            "Use your Google OAuth client ID and secret below to connect this account. Every outgoing AI email gets an automatic unique tracking pixel and history record."
                        } else {
                            "Set a real Cloudflare Worker URL in GoogleGmailAgentTool.WORKER_URL before connection can work."
                        },
                        color = GmailTextMuted,
                        fontSize = 12.sp,
                        lineHeight = 18.sp
                    )
                }
            }

            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = GmailCardAlt),
                border = androidx.compose.foundation.BorderStroke(1.dp, GmailBorder)
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        "AI Agent Full Control",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    Text(
                        "Turn this ON to switch the AI agent to the Custom Template and enable full Gmail control for emails, threads, drafts, labels, attachments, automatic tracking, and local TableSheet sync.",
                        color = GmailTextMuted,
                        fontSize = 12.sp,
                        lineHeight = 18.sp
                    )

                    IntegrationRow(
                        title = "Enable Full AI Control",
                        subtitle =
                            if (isConnected) {
                                "Switches AI agent to CUSTOM template and enables Google Gmail commands plus automatic tracking history."
                            } else {
                                "Connect Google Gmail first, then enable AI control."
                            },
                        checked = isFullAiControlEnabled,
                        enabled = isConnected && !isLoading && !isChecking,
                        onCheckedChange = { enabled ->
                            scope.launch {
                                isLoading = true
                                if (enabled) {
                                    settingsManager.isAgentEnabled = true
                                    settingsManager.activeTemplate = "CUSTOM"
                                    settingsManager.customTemplateEnableGoogleGmailTool = true
                                    GmailTrackingTableSheetManager(context).initializeSheetSystem()
                                    GmailTrackingTableSheetManager(context).startRealtimeSync()
                                    statusMessage =
                                        "Full Gmail AI control enabled. Custom template is now active."
                                } else {
                                    settingsManager.customTemplateEnableGoogleGmailTool = false
                                    statusMessage =
                                        "Google Gmail AI control disabled for the custom template."
                                }
                                refreshStatus()
                                isLoading = false
                            }
                        }
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        StatusChip(
                            text = if (isAgentEnabled) "Agent ON" else "Agent OFF",
                            color = if (isAgentEnabled) GmailSuccess else GmailAccent
                        )
                        StatusChip(
                            text =
                                if (isCustomTemplateActive) {
                                    "Template CUSTOM"
                                } else {
                                    "Template ${settingsManager.activeTemplate.ifBlank { "NONE" }}"
                                },
                            color = if (isCustomTemplateActive) GmailSuccess else GmailAccent
                        )
                        StatusChip(
                            text = if (isGmailToolEnabled) "Tool ON" else "Tool OFF",
                            color = if (isGmailToolEnabled) GmailSuccess else GmailAccent
                        )
                    }

                    Button(
                        onClick = {
                            context.startActivity(
                                Intent(context, CustomAIAgentActivity::class.java)
                            )
                        },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        colors =
                            ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF22314F),
                                contentColor = Color.White
                            )
                    ) {
                        Text("Open Custom AI Template")
                    }
                }
            }

            if (isConnected) {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = GmailCardAlt),
                    border = androidx.compose.foundation.BorderStroke(1.dp, GmailBorder)
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp).fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Icon(
                            Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint = GmailSuccess,
                            modifier = Modifier.size(48.dp)
                        )
                        Text("Gmail Connected", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                        Text(
                            if (isFullAiControlEnabled) {
                                "Your AI agent now has full Gmail control with automatic tracking and TableSheet history sync."
                            } else {
                                "Gmail is connected, but AI control stays OFF until the full-control switch above is enabled."
                            },
                            color = GmailTextMuted,
                            fontSize = 14.sp
                        )
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = {
                                scope.launch {
                                    isLoading = true
                                    manager.disconnect()
                                    refreshStatus()
                                    isLoading = false
                                }
                            },
                            enabled = !isLoading,
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFEF4444),
                                contentColor = Color.White
                            )
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                                Spacer(Modifier.width(10.dp))
                            }
                            Text("Disconnect")
                        }
                    }
                }
            } else {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = GmailCardAlt),
                    border = androidx.compose.foundation.BorderStroke(1.dp, GmailBorder)
                ) {
                    Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Text("OAuth Setup", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)

                        OutlinedTextField(
                            value = clientId,
                            onValueChange = { clientId = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Google Client ID") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                            colors = gmailTextFieldColors()
                        )

                        OutlinedTextField(
                            value = clientSecret,
                            onValueChange = { clientSecret = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Google Client Secret") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            visualTransformation =
                                if (showSecret) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = { showSecret = !showSecret }) {
                                    Icon(
                                        if (showSecret) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                        contentDescription = null,
                                        tint = GmailAccentSoft
                                    )
                                }
                            },
                            colors = gmailTextFieldColors()
                        )

                        Button(
                            onClick = {
                                scope.launch {
                                    if (clientId.isBlank() || clientSecret.isBlank()) {
                                        statusMessage = "Client ID and Client Secret are required"
                                        return@launch
                                    }
                                    isLoading = true
                                    val result = manager.initiateOAuthLogin(clientId, clientSecret)
                                    isLoading = false
                                    result.onSuccess { authUrl ->
                                        statusMessage = "Opening Google consent screen"
                                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(authUrl)))
                                    }.onFailure {
                                        statusMessage = it.message ?: "Gmail setup failed"
                                    }
                                }
                            },
                            enabled = !isLoading && isWorkerConfigured,
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            colors =
                                ButtonDefaults.buttonColors(
                                    containerColor = GmailAccent,
                                    contentColor = Color.White,
                                    disabledContainerColor = Color(0xFF334155),
                                    disabledContentColor = GmailTextMuted
                                )
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp
                                )
                                Spacer(Modifier.width(10.dp))
                            } else {
                                Icon(Icons.Filled.OpenInBrowser, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                            }
                            Text(if (isLoading) "Starting..." else "Connect Google Gmail")
                        }

                        if (statusMessage.isNotBlank()) {
                            Text(statusMessage, color = GmailAccentSoft, fontSize = 12.sp)
                        }
                    }
                }
            }

            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = GmailCard),
                border = androidx.compose.foundation.BorderStroke(1.dp, GmailBorder)
            ) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Required Worker Endpoints", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    SetupLine("/auth/login")
                    SetupLine("/auth/callback")
                    SetupLine("/gmail/list")
                    SetupLine("/gmail/read")
                    SetupLine("/gmail/send")
                    SetupLine("/gmail/reply")
                    SetupLine("/gmail/threads/*")
                    SetupLine("/gmail/drafts/*")
                    SetupLine("/gmail/labels/*")
                    SetupLine("/gmail/history/*")
                    SetupLine("/track/pixel")
                }
            }
        }
    }
}

@Composable
private fun IntegrationRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Text(subtitle, color = GmailTextMuted, fontSize = 12.sp, lineHeight = 18.sp)
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}

@Composable
private fun StatusChip(text: String, color: Color) {
    Box(
        modifier =
            Modifier.border(1.dp, color.copy(alpha = 0.55f), RoundedCornerShape(999.dp))
                .background(color.copy(alpha = 0.14f), RoundedCornerShape(999.dp))
                .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(text, color = color, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun SetupLine(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            Icons.Filled.Link,
            contentDescription = null,
            tint = GmailAccent,
            modifier = Modifier.size(16.dp)
        )
        Spacer(Modifier.width(10.dp))
        Text(text, color = GmailTextMuted, fontSize = 13.sp)
    }
}

@Composable
private fun gmailTextFieldColors() =
    OutlinedTextFieldDefaults.colors(
        focusedBorderColor = GmailAccent,
        unfocusedBorderColor = GmailBorder,
        focusedLabelColor = GmailAccent,
        unfocusedLabelColor = GmailTextMuted,
        focusedTextColor = Color.White,
        unfocusedTextColor = Color.White,
        cursorColor = GmailAccent
    )
