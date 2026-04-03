package com.message.bulksend.autorespond.aireply

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.message.bulksend.autorespond.aireply.chatspromo.ChatsPromoGeminiService
import com.message.bulksend.ui.theme.BulksendTestTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class AIReplyActivity : ComponentActivity() {
    private lateinit var configManager: AIConfigManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        configManager = AIConfigManager(this)

        setContent {
            BulksendTestTheme {
                AIReplyScreen(
                    configManager = configManager,
                    onBackPressed = { finish() }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AIReplyScreen(
    configManager: AIConfigManager,
    onBackPressed: () -> Unit
) {
    val context = LocalContext.current
    val replyManager = remember { AIReplyManager(context) }
    val chatsPromoGeminiService = remember { ChatsPromoGeminiService(context) }
    val providers = listOf(AIProvider.CHATSPROMO, AIProvider.GEMINI, AIProvider.CHATGPT)

    var selectedTab by remember {
        mutableStateOf(
            providers.indexOf(replyManager.getSelectedProvider()).takeIf { it >= 0 } ?: 0
        )
    }

    val currentProvider = providers[selectedTab]
    val isChatsPromo = currentProvider == AIProvider.CHATSPROMO
    val providerInfo = AIProviderData.getProviderInfo(currentProvider)

    var config by remember { mutableStateOf(configManager.getConfig(currentProvider)) }
    var apiKey by remember { mutableStateOf(config.apiKey) }
    var selectedModel by remember { mutableStateOf(config.model.ifBlank { currentProvider.defaultModel }) }
    var passwordVisible by remember { mutableStateOf(false) }
    var expandedModel by remember { mutableStateOf(false) }
    var saveButtonText by remember { mutableStateOf("SAVE") }
    var chatsPromoStatus by remember { mutableStateOf("") }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(selectedTab) {
        replyManager.setSelectedProvider(currentProvider)
        config = configManager.getConfig(currentProvider)
        apiKey = if (isChatsPromo) "" else config.apiKey
        selectedModel = config.model.ifBlank { currentProvider.defaultModel }
        passwordVisible = false
        expandedModel = false
        saveButtonText = "SAVE"

        chatsPromoStatus =
            if (isChatsPromo) {
                val status = chatsPromoGeminiService.fetchWorkerStatus()
                if (status.isReady) {
                    "Server connected. Worker model: ${status.model}"
                } else {
                    status.message
                }
            } else {
                ""
            }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("AI Agent Setup", color = Color.White, fontWeight = FontWeight.Medium)
                },
                navigationIcon = {
                    IconButton(onClick = onBackPressed) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "More", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF00796B))
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFF5F5F5))
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                providers.forEachIndexed { index, provider ->
                    FilterChip(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        label = { Text(provider.displayName) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFF00796B),
                            selectedLabelColor = Color.White,
                            containerColor = Color.White,
                            labelColor = Color(0xFF00796B)
                        )
                    )
                }
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFE0F2F1)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.AutoAwesome,
                        contentDescription = null,
                        modifier = Modifier.width(48.dp),
                        tint = Color(0xFF00796B)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            providerInfo.name,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF212121)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            providerInfo.description,
                            fontSize = 14.sp,
                            color = Color(0xFF616161)
                        )
                        if (!isChatsPromo && providerInfo.learnMoreUrl.isNotBlank()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "Learn more",
                                fontSize = 14.sp,
                                color = Color(0xFF2196F3),
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.clickable {
                                    context.startActivity(
                                        Intent(Intent.ACTION_VIEW, Uri.parse(providerInfo.learnMoreUrl))
                                    )
                                }
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            providerInfo.poweredBy,
                            fontSize = 12.sp,
                            color = Color(0xFF9E9E9E)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            if (isChatsPromo) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFEDE9FE)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "ChatsPromo AI worker se control hoga. User yahan API key, model, ya provider settings change nahi kar sakta.",
                            fontSize = 14.sp,
                            color = Color(0xFF4C1D95)
                        )
                        if (chatsPromoStatus.isNotBlank()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                chatsPromoStatus,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFF6D28D9)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Button(
                        onClick = {
                            replyManager.setSelectedProvider(currentProvider)
                            saveButtonText = "SELECTED"
                            Toast.makeText(
                                context,
                                "ChatsPromo AI selected. Worker-managed setup use hogi.",
                                Toast.LENGTH_SHORT
                            ).show()
                            coroutineScope.launch {
                                delay(2000)
                                saveButtonText = "SAVE"
                            }
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00796B)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(saveButtonText, fontWeight = FontWeight.Medium)
                    }

                    OutlinedButton(
                        onClick = {
                            coroutineScope.launch {
                                val status = chatsPromoGeminiService.fetchWorkerStatus()
                                chatsPromoStatus =
                                    if (status.isReady) {
                                        "Server connected. Worker model: ${status.model}"
                                    } else {
                                        status.message
                                    }
                            }
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF00796B)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("REFRESH", fontWeight = FontWeight.Medium)
                    }
                }
            } else {
                Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                    Text(
                        "API Key",
                        fontSize = 12.sp,
                        color = Color(0xFF616161),
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    OutlinedTextField(
                        value = apiKey,
                        onValueChange = { apiKey = it },
                        modifier = Modifier.fillMaxWidth(),
                        visualTransformation =
                            if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(
                                    if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = "Toggle password visibility"
                                )
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF00796B),
                            unfocusedBorderColor = Color(0xFFBDBDBD)
                        )
                    )

                    if (providerInfo.apiKeyUrl.isNotBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "GET API KEY",
                            fontSize = 14.sp,
                            color = Color(0xFF00796B),
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.clickable {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, Uri.parse(providerInfo.apiKeyUrl))
                                )
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                    Text(
                        "Model",
                        fontSize = 12.sp,
                        color = Color(0xFF616161),
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    ExposedDropdownMenuBox(
                        expanded = expandedModel,
                        onExpandedChange = { expandedModel = it }
                    ) {
                        OutlinedTextField(
                            value = selectedModel,
                            onValueChange = { },
                            readOnly = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(),
                            trailingIcon = {
                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedModel)
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFF00796B),
                                unfocusedBorderColor = Color(0xFFBDBDBD)
                            )
                        )

                        DropdownMenu(
                            expanded = expandedModel,
                            onDismissRequest = { expandedModel = false }
                        ) {
                            providerInfo.models.forEach { model ->
                                DropdownMenuItem(
                                    text = { Text(model) },
                                    onClick = {
                                        selectedModel = model
                                        expandedModel = false
                                    }
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Button(
                        onClick = {
                            if (apiKey.isBlank()) {
                                Toast.makeText(
                                    context,
                                    "Please enter API key",
                                    Toast.LENGTH_SHORT
                                ).show()
                                return@Button
                            }

                            replyManager.setSelectedProvider(currentProvider)
                            configManager.saveConfig(currentProvider, apiKey.trim(), selectedModel)
                            saveButtonText = "SAVED"
                            Toast.makeText(
                                context,
                                "${currentProvider.displayName} saved successfully",
                                Toast.LENGTH_SHORT
                            ).show()
                            coroutineScope.launch {
                                delay(2000)
                                saveButtonText = "SAVE"
                            }
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00796B)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(saveButtonText, fontWeight = FontWeight.Medium)
                    }

                    OutlinedButton(
                        onClick = {
                            apiKey = ""
                            selectedModel = currentProvider.defaultModel
                            saveButtonText = "SAVE"
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF00796B)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("RESET", fontWeight = FontWeight.Medium)
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    "AI Agent Settings",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF00796B),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )

                SettingsItem(
                    icon = Icons.Default.Tune,
                    title = "AI Agent Parameters",
                    subtitle = "Configure parameters to control the model's responses.",
                    onClick = {
                        context.startActivity(
                            Intent(context, AIParametersActivity::class.java).apply {
                                putExtra("provider", currentProvider.name)
                            }
                        )
                    }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
fun SettingsItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.width(40.dp),
                tint = Color(0xFF616161)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF212121)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    subtitle,
                    fontSize = 14.sp,
                    color = Color(0xFF757575)
                )
            }
        }
    }
}
