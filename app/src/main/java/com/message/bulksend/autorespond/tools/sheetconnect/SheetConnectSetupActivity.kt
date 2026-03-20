package com.message.bulksend.autorespond.tools.sheetconnect

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.launch

// ─── Colors ────────────────────────────────────────────────────────────────────
private val BG_DARK     = Color(0xFF0D1117)
private val BG_CARD     = Color(0xFF161B22)
private val BG_CARD2    = Color(0xFF1C2128)
private val GREEN_PRI   = Color(0xFF2EA043)
private val GREEN_LT    = Color(0xFF3FB950)
private val GREEN_DIM   = Color(0xFF238636)
private val BORDER_COL  = Color(0xFF30363D)
private val TEXT_MUT    = Color(0xFF8B949E)
private val TEXT_CODE   = Color(0xFF58A6FF)

// ─── Activity ──────────────────────────────────────────────────────────────────

class SheetConnectSetupActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            SheetConnectSetupScreen(onBackClicked = { finish() })
        }
    }
}

// ─── Screen ────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SheetConnectSetupScreen(onBackClicked: () -> Unit) {
    val context       = LocalContext.current
    val manager       = remember { SheetConnectManager(context) }
    val scope         = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current
    val scrollState   = rememberScrollState()

    var clientId       by remember { mutableStateOf("") }
    var clientSecret   by remember { mutableStateOf("") }
    var secretVisible  by remember { mutableStateOf(false) }
    var isLoading      by remember { mutableStateOf(false) }
    var isSetupDone    by remember { mutableStateOf(false) }
    var isChecking     by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        isChecking = true
        isSetupDone = manager.isSetupDone()
        isChecking = false
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                scope.launch {
                    isChecking = true
                    isSetupDone = manager.isSetupDone()
                    isChecking = false
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BG_DARK)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
        ) {
            // ── Hero header with gradient ────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color(0xFF0D2818), BG_DARK),
                            startY = 0f, endY = 700f
                        )
                    )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 20.dp)
                ) {
                    Spacer(Modifier.height(8.dp))

                    // Back button row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = onBackClicked,
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF21262D))
                        ) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White, modifier = Modifier.size(20.dp))
                        }
                        Spacer(Modifier.weight(1f))
                        // Connection badge
                        if (!isChecking) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(if (isSetupDone) Color(0xFF0D2818) else Color(0xFF1C1C1C))
                                    .border(1.dp, if (isSetupDone) GREEN_DIM else BORDER_COL, RoundedCornerShape(20.dp))
                                    .padding(horizontal = 10.dp, vertical = 5.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(7.dp)
                                            .clip(CircleShape)
                                            .background(if (isSetupDone) GREEN_LT else Color(0xFF666666))
                                    )
                                    Spacer(Modifier.width(5.dp))
                                    Text(
                                        if (isSetupDone) "Connected" else "Not Connected",
                                        color = if (isSetupDone) GREEN_LT else TEXT_MUT,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(28.dp))

                    // Icon + Title block
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(60.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(
                                    Brush.linearGradient(listOf(Color(0xFF1A4731), Color(0xFF0F3020)))
                                )
                                .border(1.dp, GREEN_DIM, RoundedCornerShape(14.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("⊞", color = GREEN_LT, fontSize = 28.sp)
                        }
                        Spacer(Modifier.width(16.dp))
                        Column {
                            Text(
                                "Google Sheets",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 22.sp
                            )
                            Text(
                                "Connect & Configure",
                                color = TEXT_MUT,
                                fontSize = 13.sp
                            )
                        }
                    }

                    Spacer(Modifier.height(20.dp))
                }
            }

            Spacer(Modifier.height(4.dp))

            // ── Content ───────────────────────────────────────────────────────
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                if (isChecking) {
                    Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = GREEN_LT, strokeWidth = 2.dp)
                    }
                } else if (isSetupDone) {
                    ConnectedSection(
                        manager   = manager,
                        context   = context,
                        onReAuth  = { isSetupDone = false }
                    )
                } else {
                    SetupSection(
                        manager      = manager,
                        clientId     = clientId,
                        onClientId   = { clientId = it },
                        clientSecret = clientSecret,
                        onSecret     = { clientSecret = it },
                        secretVisible = secretVisible,
                        onToggleVis  = { secretVisible = !secretVisible },
                        isLoading    = isLoading,
                        context      = context,
                        scope        = scope,
                        onLoading    = { isLoading = it }
                    )
                }

                Spacer(Modifier.navigationBarsPadding().height(24.dp))
            }
        }
    }
}

// ─── Connected section ─────────────────────────────────────────────────────────

@Composable
fun ConnectedSection(
    manager: SheetConnectManager,
    context: android.content.Context,
    onReAuth: () -> Unit
) {
    // Pulse animation on checkmark
    val pulse = rememberInfiniteTransition(label = "pulse")
    val scale by pulse.animateFloat(
        initialValue = 0.95f, targetValue = 1.05f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "scale"
    )

    // Connected hero card
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0D2818)),
        border = CardDefaults.cardColors(Color.Transparent).let { androidx.compose.foundation.BorderStroke(1.dp, GREEN_DIM) }
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .scale(scale)
                    .size(70.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF1A4731)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = GREEN_LT, modifier = Modifier.size(40.dp))
            }
            Text("Successfully Connected!", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Text(
                "Your AI Agent can now read and write to Google Sheets automatically.",
                color = TEXT_MUT, fontSize = 13.sp, textAlign = TextAlign.Center, lineHeight = 19.sp
            )
        }
    }

    // Capability pills
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        CapabilityPill("Read", Icons.Default.Download, Modifier.weight(1f))
        CapabilityPill("Write", Icons.Default.Upload, Modifier.weight(1f))
        CapabilityPill("Create", Icons.Default.Add, Modifier.weight(1f))
    }

    // Configure button
    Button(
        onClick = { context.startActivity(Intent(context, SheetMappingActivity::class.java)) },
        modifier = Modifier.fillMaxWidth().height(52.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(containerColor = GREEN_PRI)
    ) {
        Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text("Configure Sheet Mappings", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
    }

    // Re-auth link
    TextButton(
        onClick = onReAuth,
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp), tint = TEXT_MUT)
        Spacer(Modifier.width(4.dp))
        Text("Re-Authenticate", color = TEXT_MUT, fontSize = 13.sp)
    }
}

@Composable
fun CapabilityPill(label: String, icon: ImageVector, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(BG_CARD2)
            .border(1.dp, BORDER_COL, RoundedCornerShape(10.dp))
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(icon, null, tint = GREEN_LT, modifier = Modifier.size(16.dp))
            Text(label, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Medium)
        }
    }
}

// ─── Setup / not-connected section ─────────────────────────────────────────────

@Composable
fun SetupSection(
    manager: SheetConnectManager,
    clientId: String,
    onClientId: (String) -> Unit,
    clientSecret: String,
    onSecret: (String) -> Unit,
    secretVisible: Boolean,
    onToggleVis: () -> Unit,
    isLoading: Boolean,
    context: android.content.Context,
    scope: kotlinx.coroutines.CoroutineScope,
    onLoading: (Boolean) -> Unit
) {
    // Instructions card
    GhostCard {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Info, null, tint = TEXT_CODE, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text("Setup Instructions", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
            HorizontalDivider(color = BORDER_COL, thickness = 0.5.dp)
            StepRow("1", "Go to Google Cloud Console → create a new project")
            StepRow("2", "Enable the Google Sheets API")
            StepRow("3", "Credentials → Create OAuth client ID (Web app)")
            StepRow("4", "Under Authorized redirect URIs, add:")
            // Redirect URI box
            SelectionContainer {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF0D1117))
                        .border(1.dp, TEXT_CODE.copy(0.4f), RoundedCornerShape(8.dp))
                        .padding(10.dp)
                ) {
                    Text(
                        "${manager.workerBaseUrl}/auth/callback",
                        color = TEXT_CODE,
                        fontSize = 12.sp,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                    )
                }
            }
            StepRow("5", "Copy the Client ID and Secret into the fields below")
        }
    }

    // Credentials card
    GhostCard {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("OAuth Credentials", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            HorizontalDivider(color = BORDER_COL, thickness = 0.5.dp)

            // Client ID
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Client ID", color = TEXT_MUT, fontSize = 12.sp)
                OutlinedTextField(
                    value = clientId,
                    onValueChange = onClientId,
                    placeholder = { Text("Paste your OAuth Client ID", color = Color(0xFF444C56), fontSize = 13.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor   = Color(0xFF0D1117),
                        unfocusedContainerColor = Color(0xFF0D1117),
                        focusedTextColor        = Color.White,
                        unfocusedTextColor      = Color(0xFFCDD9E5),
                        focusedBorderColor      = GREEN_LT,
                        unfocusedBorderColor    = BORDER_COL
                    ),
                    shape = RoundedCornerShape(10.dp)
                )
            }

            // Client Secret
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Client Secret", color = TEXT_MUT, fontSize = 12.sp)
                OutlinedTextField(
                    value = clientSecret,
                    onValueChange = onSecret,
                    placeholder = { Text("Paste your OAuth Client Secret", color = Color(0xFF444C56), fontSize = 13.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    visualTransformation = if (secretVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = onToggleVis) {
                            Icon(
                                if (secretVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = null,
                                tint = TEXT_MUT,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor   = Color(0xFF0D1117),
                        unfocusedContainerColor = Color(0xFF0D1117),
                        focusedTextColor        = Color.White,
                        unfocusedTextColor      = Color(0xFFCDD9E5),
                        focusedBorderColor      = GREEN_LT,
                        unfocusedBorderColor    = BORDER_COL
                    ),
                    shape = RoundedCornerShape(10.dp)
                )
            }
        }
    }

    // Connect button
    Button(
        onClick = {
            if (clientId.isBlank() || clientSecret.isBlank()) {
                Toast.makeText(context, "Please enter both credentials", Toast.LENGTH_SHORT).show()
                return@Button
            }
            onLoading(true)
            scope.launch {
                val result = manager.initiateOAuthLogin(clientId.trim(), clientSecret.trim())
                onLoading(false)
                result.onSuccess { authUrl ->
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(authUrl)))
                    Toast.makeText(context, "Opening Google Login…", Toast.LENGTH_SHORT).show()
                }.onFailure { e ->
                    Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        },
        modifier = Modifier.fillMaxWidth().height(54.dp),
        enabled = !isLoading,
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = GREEN_PRI,
            disabledContainerColor = Color(0xFF238636).copy(alpha = 0.5f)
        )
    ) {
        if (isLoading) {
            CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(10.dp))
            Text("Connecting…", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
        } else {
            Icon(Icons.Default.Lock, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Connect Google Sheets", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
        }
    }

    // Note
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF161B22))
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.Shield, null, tint = GREEN_DIM, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(6.dp))
        Text("Your credentials are handled securely via OAuth 2.0 — we never store your Google password.", color = TEXT_MUT, fontSize = 11.sp, lineHeight = 16.sp)
    }
}

// ─── Reusable sub-components ───────────────────────────────────────────────────

@Composable
fun GhostCard(content: @Composable ColumnScope.() -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(BG_CARD)
            .border(1.dp, BORDER_COL, RoundedCornerShape(14.dp))
            .padding(16.dp)
    ) {
        Column(content = content)
    }
}

@Composable
fun StepRow(num: String, text: String) {
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .background(GREEN_DIM),
            contentAlignment = Alignment.Center
        ) {
            Text(num, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }
        Text(text, color = Color(0xFFCDD9E5), fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.weight(1f))
    }
}
