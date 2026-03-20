package com.message.bulksend.autorespond.aireply

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    var selectedTab by remember { mutableStateOf(0) }
    val providers = listOf(AIProvider.GEMINI, AIProvider.CHATGPT)
    val currentProvider = providers[selectedTab]
    
    var config by remember { mutableStateOf(configManager.getConfig(currentProvider)) }
    var apiKey by remember { mutableStateOf(config.apiKey) }
    var selectedModel by remember { mutableStateOf(config.model) }
    var passwordVisible by remember { mutableStateOf(false) }
    var expandedModel by remember { mutableStateOf(false) }
    var saveButtonText by remember { mutableStateOf("SAVE") }
    val coroutineScope = rememberCoroutineScope()
    
    val providerInfo = AIProviderData.getProviderInfo(currentProvider)
    
    LaunchedEffect(selectedTab) {
        config = configManager.getConfig(currentProvider)
        apiKey = config.apiKey
        selectedModel = config.model
        saveButtonText = "SAVE"
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text("AI Reply", color = Color.White, fontWeight = FontWeight.Medium)
                },
                navigationIcon = {
                    IconButton(onClick = onBackPressed) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                actions = {
                    IconButton(onClick = { /* More options */ }) {
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
            // Tabs
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
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
            
            // Info Card
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
                        modifier = Modifier.size(48.dp),
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
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Learn more",
                            fontSize = 14.sp,
                            color = Color(0xFF2196F3),
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.clickable {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(providerInfo.learnMoreUrl)))
                            }
                        )
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
            
            // API Key Section
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
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
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
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    "GET API KEY",
                    fontSize = 14.sp,
                    color = Color(0xFF00796B),
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.clickable {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(providerInfo.apiKeyUrl)))
                    }
                )
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Model Selection
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
                        onValueChange = {},
                        readOnly = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(),
                        trailingIcon = {
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF00796B),
                            unfocusedBorderColor = Color(0xFFBDBDBD)
                        )
                    )
                    
                    ExposedDropdownMenu(
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
            
            // Save and Reset Buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Button(
                    onClick = { 
                        if (apiKey.isNotBlank()) {
                            configManager.saveConfig(currentProvider, apiKey, selectedModel)
                            saveButtonText = "SAVED"
                            android.widget.Toast.makeText(context, "Saved successfully!", android.widget.Toast.LENGTH_SHORT).show()
                            
                            // Reset button text after 2 seconds
                            coroutineScope.launch {
                                delay(2000)
                                saveButtonText = "SAVE"
                            }
                        } else {
                            android.widget.Toast.makeText(context, "Please enter API key", android.widget.Toast.LENGTH_SHORT).show()
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
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF00796B)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("RESET", fontWeight = FontWeight.Medium)
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // AI Settings Section
            Text(
                "AI Settings",
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF00796B),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            
            SettingsItem(
                icon = Icons.Default.Tune,
                title = "AI Parameters",
                subtitle = "Configure parameters to control the model's responses.",
                onClick = { 
                    context.startActivity(android.content.Intent(context, AIParametersActivity::class.java).apply {
                        putExtra("provider", currentProvider.name)
                    })
                }
            )
            
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
                modifier = Modifier.size(40.dp),
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
