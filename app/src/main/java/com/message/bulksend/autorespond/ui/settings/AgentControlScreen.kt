package com.message.bulksend.autorespond.ui.settings

import android.content.Intent
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.message.bulksend.autorespond.ai.settings.AIAgentSettingsManager
import com.message.bulksend.autorespond.aireply.AIConfigManager
import com.message.bulksend.autorespond.aireply.AIProvider
import com.message.bulksend.autorespond.ui.catalogue.ProductCatalogueActivity
import com.message.bulksend.tablesheet.TableSheetActivity

@Composable
fun AgentControlScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val settingsManager = remember { AIAgentSettingsManager(context) }
    val advancedSettings = remember { com.message.bulksend.autorespond.ai.settings.AIAgentAdvancedSettings(context) }
    val aiConfigManager = remember { AIConfigManager(context) }
    
    // State
    var isEnabled by remember { mutableStateOf(settingsManager.isAgentEnabled) }
    var agentName by remember { mutableStateOf(settingsManager.agentName) }
    var askName by remember { mutableStateOf(settingsManager.askCurrentUserName) }
    var reAskName by remember { mutableStateOf(settingsManager.reAskNameIfNotGiven) }
    var requireName by remember { mutableStateOf(settingsManager.requireNameToContinue) }
    var memoryEnabled by remember { mutableStateOf(settingsManager.enableMemory) }
    var sheetsEnabled by remember { mutableStateOf(settingsManager.enableDataSheetLookup) }
    var productEnabled by remember { mutableStateOf(settingsManager.enableProductLookup) }
    var systemPrompt by remember { mutableStateOf(settingsManager.customSystemPrompt) }
    var apiKey by remember { mutableStateOf("") }
    var geminiModel by remember { mutableStateOf(AIProvider.GEMINI.defaultModel) }
    var showKey by remember { mutableStateOf(false) }
    
    // Collapse/Expand States - Default: Collapsed
    var configExpanded by remember { mutableStateOf(false) }
    var capabilitiesExpanded by remember { mutableStateOf(false) }
    var profileExpanded by remember { mutableStateOf(false) }
    var advancedExpanded by remember { mutableStateOf(false) }
    var ecommerceExpanded by remember { mutableStateOf(false) }
    var personalityExpanded by remember { mutableStateOf(false) }
    
    // Advanced Settings State
    var intentDetection by remember { mutableStateOf(advancedSettings.enableIntentDetection) }
    var autoSaveHistory by remember { mutableStateOf(advancedSettings.autoSaveIntentHistory) }
    var autoCreateSheets by remember { mutableStateOf(advancedSettings.autoCreateHistorySheets) }
    
    // E-commerce Settings State
    var ecommerceMode by remember { mutableStateOf(advancedSettings.enableEcommerceMode) }
    var autoAskAddress by remember { mutableStateOf(advancedSettings.autoAskAddress) }
    var autoCreateSalesSheets by remember { mutableStateOf(advancedSettings.autoCreateSalesSheets) }

    LaunchedEffect(Unit) {
        val config = aiConfigManager.getConfig(AIProvider.GEMINI)
        apiKey = config.apiKey
        geminiModel = config.model
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F0F23)) // Dark Deep Blue Background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            // Gradient Header
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
                    // Back Button & Title Row
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
                            "AI Agent Settings",
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                    
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "Configure your intelligent auto-reply assistant and manage capabilities.",
                        color = Color.White.copy(0.8f),
                        fontSize = 14.sp
                    )
                }
            }
            
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // Master Switch Card
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (isEnabled) Color(0xFF8B5CF6).copy(0.15f) else Color(0xFF1A1A2E)
                    ),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, if (isEnabled) Color(0xFF8B5CF6).copy(0.5f) else Color(0xFF2A2A4A))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Enable AI Agent",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                if (isEnabled) "Active and replying" else "Disabled",
                                fontSize = 13.sp,
                                color = if (isEnabled) Color(0xFFA78BFA) else Color.White.copy(0.5f)
                            )
                        }
                        Switch(
                            checked = isEnabled,
                            onCheckedChange = { 
                                isEnabled = it
                                settingsManager.isAgentEnabled = it
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = Color(0xFF8B5CF6),
                                uncheckedThumbColor = Color(0xFF8B5CF6),
                                uncheckedTrackColor = Color(0xFF1A1A2E),
                                uncheckedBorderColor = Color(0xFF2A2A4A)
                            )
                        )
                    }
                }
                
                // Quick Access Grid
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    QuickAccessCard(
                        "Product\nCatalogue",
                        Icons.Default.ShoppingCart,
                        Modifier.weight(1f)
                    ) {
                        context.startActivity(Intent(context, ProductCatalogueActivity::class.java))
                    }
                    QuickAccessCard(
                        "Data\nSheets",
                        Icons.Default.TableChart,
                        Modifier.weight(1f)
                    ) {
                        val intent = Intent(context, TableSheetActivity::class.java)
                        intent.putExtra("openFolder", "AI Agent Data Sheet")
                        context.startActivity(intent)
                    }
                }
                
                // NEW: AI Agent History Quick Access
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    QuickAccessCard(
                        "Agent\nHistory",
                        Icons.Default.History,
                        Modifier.weight(1f)
                    ) {
                        val intent = Intent(context, TableSheetActivity::class.java)
                        intent.putExtra("openFolder", "AI Agent History")
                        context.startActivity(intent)
                    }
                    QuickAccessCard(
                        "Analytics\n(Soon)",
                        Icons.Default.Analytics,
                        Modifier.weight(1f)
                    ) {
                        // Future: Analytics dashboard
                    }
                }
                
                if (isEnabled) {
                    // API Configuration
                    CollapsibleSection(
                        title = "Configuration",
                        icon = Icons.Default.Settings,
                        expanded = configExpanded,
                        onToggle = { configExpanded = !configExpanded }
                    ) {
                        SettingsCard {
                            // API Key Input
                            OutlinedTextField(
                                value = apiKey,
                                onValueChange = { 
                                    apiKey = it
                                    aiConfigManager.saveConfig(
                                        AIProvider.GEMINI,
                                        apiKey = it,
                                        model = geminiModel,
                                        enableThinking = false
                                    )
                                },
                                label = { Text("Gemini API Key") },
                                modifier = Modifier.fillMaxWidth(),
                                visualTransformation = if (showKey) androidx.compose.ui.text.input.VisualTransformation.None 
                                                     else androidx.compose.ui.text.input.PasswordVisualTransformation(),
                                trailingIcon = {
                                    IconButton(onClick = { showKey = !showKey }) {
                                        Icon(
                                            if (showKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                            "Toggle",
                                            tint = Color.White.copy(0.5f)
                                        )
                                    }
                                },
                                shape = RoundedCornerShape(12.dp),
                                colors = darkFieldColors(),
                                singleLine = true
                            )
                            
                            Spacer(Modifier.height(16.dp))
                            
                            // Agent Name
                            OutlinedTextField(
                                value = agentName,
                                onValueChange = { 
                                    agentName = it 
                                    settingsManager.agentName = it
                                },
                                label = { Text("Agent Name") },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = darkFieldColors(),
                                singleLine = true
                            )
                        }
                    }
                    
                    // Capabilities
                    CollapsibleSection(
                        title = "Capabilities",
                        icon = Icons.Default.AutoAwesome,
                        expanded = capabilitiesExpanded,
                        onToggle = { capabilitiesExpanded = !capabilitiesExpanded }
                    ) {
                        SettingsCard {
                            SettingsSwitchRow(
                                "Conversation Memory", 
                                "Remember previous messages", 
                                Icons.Default.History, 
                                memoryEnabled
                            ) { 
                                memoryEnabled = it
                                settingsManager.enableMemory = it
                            }
                            
                            Divider(color = Color(0xFF2A2A4A))
                            
                            SettingsSwitchRow(
                                "Product Knowledge", 
                                "Answer from Catalogue", 
                                Icons.Default.ShoppingCart, 
                                productEnabled
                            ) { 
                                productEnabled = it
                                settingsManager.enableProductLookup = it
                            }
                            
                            Divider(color = Color(0xFF2A2A4A))
                            
                            SettingsSwitchRow(
                                "Business Data", 
                                "Answer from Table Sheets", 
                                Icons.Default.TableChart, 
                                sheetsEnabled
                            ) { 
                                sheetsEnabled = it
                                settingsManager.enableDataSheetLookup = it
                            }
                        }
                    }
                    
                    // Profile Settings
                    CollapsibleSection(
                        title = "Profile Settings",
                        icon = Icons.Default.AccountCircle,
                        expanded = profileExpanded,
                        onToggle = { profileExpanded = !profileExpanded }
                    ) {
                        SettingsCard {
                            SettingsSwitchRow(
                                "Ask User Name", 
                                "Ask for name if unknown", 
                                Icons.Default.Person, 
                                askName
                            ) { 
                                askName = it
                                settingsManager.askCurrentUserName = it
                            }
                            
                            Divider(color = Color(0xFF2A2A4A))
                            
                            SettingsSwitchRow(
                                "Re-ask Name if Not Given", 
                                "Ask again if user didn't provide name", 
                                Icons.Default.PersonSearch, 
                                reAskName
                            ) { 
                                reAskName = it
                                settingsManager.reAskNameIfNotGiven = it
                            }
                            
                            Divider(color = Color(0xFF2A2A4A))
                            
                            SettingsSwitchRow(
                                "Require Name to Continue", 
                                "Block conversation until name is provided", 
                                Icons.Default.Block, 
                                requireName
                            ) { 
                                requireName = it
                                settingsManager.requireNameToContinue = it
                            }
                        }
                    }
                    
                    // Advanced Settings
                    CollapsibleSection(
                        title = "Advanced Settings",
                        icon = Icons.Default.Science,
                        expanded = advancedExpanded,
                        onToggle = { advancedExpanded = !advancedExpanded }
                    ) {
                        SettingsCard {
                            SettingsSwitchRow(
                                "Intent Detection", 
                                "Automatically detect user intent", 
                                Icons.Default.Psychology, 
                                intentDetection
                            ) { 
                                intentDetection = it
                                advancedSettings.enableIntentDetection = it
                            }
                            
                            Divider(color = Color(0xFF2A2A4A))
                            
                            SettingsSwitchRow(
                                "Auto-Save History", 
                                "Save intent logs to sheets", 
                                Icons.Default.Save, 
                                autoSaveHistory
                            ) { 
                                autoSaveHistory = it
                                advancedSettings.autoSaveIntentHistory = it
                            }
                            
                            Divider(color = Color(0xFF2A2A4A))
                            
                            SettingsSwitchRow(
                                "Auto-Create Sheets", 
                                "Create history sheets automatically", 
                                Icons.Default.AutoAwesome, 
                                autoCreateSheets
                            ) { 
                                autoCreateSheets = it
                                advancedSettings.autoCreateHistorySheets = it
                            }
                        }
                    }
                    
                    // E-commerce Mode
                    CollapsibleSection(
                        title = "E-commerce Mode",
                        icon = Icons.Default.ShoppingCart,
                        expanded = ecommerceExpanded,
                        onToggle = { ecommerceExpanded = !ecommerceExpanded }
                    ) {
                        SettingsCard {
                            SettingsSwitchRow(
                                "Enable E-commerce", 
                                "Auto-manage orders and addresses", 
                                Icons.Default.ShoppingBag, 
                                ecommerceMode
                            ) { 
                                ecommerceMode = it
                                advancedSettings.enableEcommerceMode = it
                            }
                            
                            Divider(color = Color(0xFF2A2A4A))
                            
                            SettingsSwitchRow(
                                "Auto-Ask Address", 
                                "Ask for delivery address after payment", 
                                Icons.Default.LocationOn, 
                                autoAskAddress
                            ) { 
                                autoAskAddress = it
                                advancedSettings.autoAskAddress = it
                            }
                            
                            Divider(color = Color(0xFF2A2A4A))
                            
                            SettingsSwitchRow(
                                "Auto-Create Sales Sheets", 
                                "Create order sheets automatically", 
                                Icons.Default.Receipt, 
                                autoCreateSalesSheets
                            ) { 
                                autoCreateSalesSheets = it
                                advancedSettings.autoCreateSalesSheets = it
                            }
                        }
                    }
                    
                    // Quick Access to Sales Orders
                    if (ecommerceMode) {
                        Spacer(Modifier.height(12.dp))
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            QuickAccessCard(
                                "Sales\nOrders",
                                Icons.Default.Receipt,
                                Modifier.weight(1f)
                            ) {
                                val intent = Intent(context, TableSheetActivity::class.java)
                                intent.putExtra("openFolder", "Sales Orders")
                                context.startActivity(intent)
                            }
                        }
                    }
                    
                    // System Prompt & AI Brain
                    CollapsibleSection(
                        title = "AI Brain & Personality",
                        icon = Icons.Default.Psychology,
                        expanded = personalityExpanded,
                        onToggle = { personalityExpanded = !personalityExpanded }
                    ) {
                        SettingsCard {
                            Text(
                                "Configure your agent's core intelligence, memory rules, and behave logic.",
                                color = Color.White.copy(0.7f),
                                fontSize = 13.sp,
                                modifier = Modifier.padding(bottom = 16.dp)
                            )
                            
                            Button(
                                onClick = {
                                    context.startActivity(Intent(context, AIPersonalityActivity::class.java))
                                },
                                modifier = Modifier.fillMaxWidth().height(50.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8B5CF6)),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.Psychology, null, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Configure AI Brain", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
                
                Spacer(Modifier.height(40.dp))
            }
        }
    }
}

@Composable
fun QuickAccessCard(title: String, icon: ImageVector, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Card(
        modifier = modifier.clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = Color(0xFF252547)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .background(Color(0xFF8B5CF6).copy(0.2f), CircleShape)
                    .padding(8.dp)
            ) {
                Icon(icon, null, tint = Color(0xFF8B5CF6), modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(12.dp))
            Text(title, lineHeight = 18.sp, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
        }
    }
}

@Composable
fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A2E)),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, Color(0xFF2A2A4A))
    ) {
        Column(modifier = Modifier.padding(16.dp), content = content)
    }
}

@Composable
fun SettingsSwitchRow(title: String, subtitle: String, icon: ImageVector, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = Color.White.copy(0.5f), modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = Color.White)
            Text(subtitle, fontSize = 12.sp, color = Color.White.copy(0.5f))
        }
        Switch(
            checked = checked, 
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = Color(0xFF8B5CF6),
                uncheckedThumbColor = Color(0xFF8B5CF6),
                uncheckedTrackColor = Color(0xFF0F0F23),
                uncheckedBorderColor = Color(0xFF2A2A4A)
            )
        )
    }
}

@Composable
fun SectionHeader(title: String, icon: ImageVector) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = Color(0xFF8B5CF6), modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(title, color = Color(0xFF8B5CF6), fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun darkFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Color(0xFF8B5CF6),
    unfocusedBorderColor = Color(0xFF2A2A4A),
    cursorColor = Color(0xFF8B5CF6),
    focusedTextColor = Color.White,
    unfocusedTextColor = Color.White,
    focusedLabelColor = Color(0xFF8B5CF6),
    unfocusedLabelColor = Color.White.copy(0.5f),
    focusedPlaceholderColor = Color.White.copy(0.3f),
    unfocusedPlaceholderColor = Color.White.copy(0.3f)
)

@Composable
fun CollapsibleSection(
    title: String,
    icon: ImageVector,
    expanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable () -> Unit
) {
    Column {
        // Header - Clickable to expand/collapse
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onToggle() },
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF1A1A2E)
            ),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, Color(0xFF2A2A4A))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        icon,
                        null,
                        tint = Color(0xFF8B5CF6),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        title,
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    "Toggle",
                    tint = Color(0xFF8B5CF6),
                    modifier = Modifier.size(24.dp)
                )
            }
        }
        
        // Content - Animated expand/collapse
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column(modifier = Modifier.padding(top = 12.dp)) {
                content()
            }
        }
    }
}
