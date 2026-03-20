package com.message.bulksend.autorespond.menureply

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
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
import com.message.bulksend.ui.theme.BulksendTestTheme
import kotlin.math.roundToInt

class MenuReplySettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { BulksendTestTheme { MenuReplySettingsScreen { finish() } } }
    }
}

// Simple Clean Theme for Settings
object PremiumColors {
    val DarkBackground = Color(0xFF0A0A0B)
    val CardBackground = Color(0xFF132033)
    val CardBackgroundSecondary = Color(0xFF1B2E45)
    val CardBackgroundPink = Color(0xFF132033)
    val CardBackgroundGreen = Color(0xFF132033)
    val CardBackgroundEmerald = Color(0xFF132033)
    
    val AccentPrimary = Color(0xFF38BDF8)
    val AccentSecondary = Color(0xFF22C55E)
    val AccentPink = Color(0xFF38BDF8)
    val AccentGreen = Color(0xFF38BDF8)
    val AccentEmerald = Color(0xFF38BDF8)
    val AccentRose = Color(0xFFF472B6) // Rose pink
    val AccentMint = Color(0xFF34D399) // Mint green
    
    val AccentGradientStart = Color(0xFF38BDF8)
    val AccentGradientEnd = Color(0xFF0F766E)
    val PinkGradientStart = Color(0xFFEC4899)
    val PinkGradientEnd = Color(0xFFC084FC)
    val GreenGradientStart = Color(0xFF10B981)
    val GreenGradientEnd = Color(0xFF059669)
    val EmeraldGradientStart = Color(0xFF14B8A6)
    val EmeraldGradientEnd = Color(0xFF0891B2)
    
    val TextPrimary = Color(0xFFFFFFFF)
    val TextSecondary = Color(0xFFD1D5DB)
    val TextTertiary = Color(0xFF9CA3AF)
    val DividerColor = Color(0xFF374151)
    val SwitchTrack = Color(0xFF4B5563)
    val Success = Color(0xFF10B981)
    val Warning = Color(0xFFF59E0B)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MenuReplySettingsScreen(onBackPressed: () -> Unit) {
    val context = LocalContext.current
    val settingsManager = remember { MenuReplySettingsManager(context) }
    
    // Load settings
    var showMainMenu by remember { mutableStateOf(settingsManager.isShowMainMenuEnabled()) }
    var showPreviousMenu by remember { mutableStateOf(settingsManager.isShowPreviousMenuEnabled()) }
    var defaultReplyEnabled by remember { mutableStateOf(settingsManager.isDefaultReplyEnabled()) }
    var defaultReplyType by remember { mutableStateOf(settingsManager.getDefaultReplyType()) }
    var customReplyMessage by remember { mutableStateOf(settingsManager.getCustomReplyMessage()) }
    var showEmojis by remember { mutableStateOf(settingsManager.isShowEmojisEnabled()) }
    var boldHeader by remember { mutableStateOf(settingsManager.isBoldHeaderEnabled()) }
    var showSelectionHint by remember { mutableStateOf(settingsManager.isShowSelectionHintEnabled()) }
    var menuTimeoutSeconds by remember { mutableStateOf(settingsManager.getMenuTimeoutSeconds()) }
    
    var showReplyTypeDropdown by remember { mutableStateOf(false) }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        "Menu Settings", 
                        color = PremiumColors.TextPrimary, 
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    ) 
                },
                navigationIcon = {
                    IconButton(onClick = onBackPressed) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack, 
                            "Back", 
                            tint = PremiumColors.TextPrimary
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { 
                        context.startActivity(Intent(context, InfoMenuActivity::class.java))
                    }) { 
                        Icon(Icons.Default.Info, "Help", tint = PremiumColors.TextPrimary) 
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = PremiumColors.CardBackground
                )
            )
        },
        containerColor = PremiumColors.DarkBackground
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            PremiumColors.DarkBackground,
                            Color(0xFF0F0F12)
                        )
                    )
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // Display Options Section (moved to top)
                PremiumSettingsSection(
                    title = "🎨 Display Options",
                    subtitle = "Customize menu appearance"
                ) {
                    PremiumSettingsCheckbox(
                        title = "Show Emojis",
                        subtitle = "Add emojis to navigation options (🏠 ⬅️)",
                        checked = showEmojis,
                        onCheckedChange = {
                            showEmojis = it
                            settingsManager.setShowEmojisEnabled(it)
                        }
                    )
                    
                    PremiumDivider()
                    
                    PremiumSettingsCheckbox(
                        title = "Bold Header",
                        subtitle = "Send the menu header in WhatsApp bold format",
                        checked = boldHeader,
                        onCheckedChange = {
                            boldHeader = it
                            settingsManager.setBoldHeaderEnabled(it)
                        }
                    )

                    PremiumDivider()

                    PremiumSettingsCheckbox(
                        title = "Show Selection Hint",
                        subtitle = "Show 'Reply with a number to continue.' above the menu options",
                        checked = showSelectionHint,
                        onCheckedChange = {
                            showSelectionHint = it
                            settingsManager.setShowSelectionHintEnabled(it)
                        }
                    )
                }
                
                PremiumSettingsSection(
                    title = "Session Timeout",
                    subtitle = "Default is 40 seconds. Maximum is 5 minutes."
                ) {
                    Text(
                        text = formatMenuTimeoutLabel(menuTimeoutSeconds),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = PremiumColors.TextPrimary
                    )
                    Text(
                        text = "After timeout, the customer must send the keyword again to reopen the menu.",
                        fontSize = 13.sp,
                        color = PremiumColors.TextSecondary,
                        modifier = Modifier.padding(top = 6.dp, bottom = 12.dp)
                    )
                    Slider(
                        value = menuTimeoutSeconds.toFloat(),
                        onValueChange = { value ->
                            val roundedValue = (value / 5f).roundToInt() * 5
                            val clampedValue = roundedValue.coerceIn(40, 300)
                            menuTimeoutSeconds = clampedValue
                            settingsManager.setMenuTimeoutSeconds(clampedValue)
                        },
                        valueRange = 40f..300f,
                        steps = 51,
                        colors = SliderDefaults.colors(
                            thumbColor = PremiumColors.AccentPrimary,
                            activeTrackColor = PremiumColors.AccentPrimary,
                            inactiveTrackColor = PremiumColors.DividerColor
                        )
                    )
                    Text(
                        text = "Range: 40 sec to 5 min",
                        fontSize = 12.sp,
                        color = PremiumColors.TextTertiary
                    )
                }
                
                // Navigation Options Section
                PremiumSettingsSection(
                    title = "🧭 Navigation Options",
                    subtitle = "Configure menu navigation behavior"
                ) {
                    PremiumSettingsCheckbox(
                        title = "Main Menu Option",
                        subtitle = "Add '0. 🏠 Main Menu' option to return to root",
                        checked = showMainMenu,
                        onCheckedChange = {
                            showMainMenu = it
                            settingsManager.setShowMainMenuEnabled(it)
                        }
                    )
                    
                    PremiumDivider()
                    
                    PremiumSettingsCheckbox(
                        title = "Previous Menu Option",
                        subtitle = "Add 'N+1. ⬅️ Previous Menu' option in submenus",
                        checked = showPreviousMenu,
                        onCheckedChange = {
                            showPreviousMenu = it
                            settingsManager.setShowPreviousMenuEnabled(it)
                        }
                    )
                }
                
                // Invalid Option Reply Section
                PremiumSettingsSection(
                    title = "⚠️ Invalid Option Reply",
                    subtitle = "Handle invalid user selections"
                ) {
                    PremiumSettingsCheckbox(
                        title = "Send Reply on Invalid Option",
                        subtitle = "Send a reply when user enters invalid option",
                        checked = defaultReplyEnabled,
                        onCheckedChange = {
                            defaultReplyEnabled = it
                            settingsManager.setDefaultReplyEnabled(it)
                        }
                    )
                    
                    if (defaultReplyEnabled) {
                        PremiumDivider()
                        
                        // Reply Type Dropdown
                        Column(modifier = Modifier.padding(vertical = 12.dp)) {
                            Text(
                                "Reply Type",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = PremiumColors.TextPrimary
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            ExposedDropdownMenuBox(
                                expanded = showReplyTypeDropdown,
                                onExpandedChange = { showReplyTypeDropdown = it }
                            ) {
                                OutlinedTextField(
                                    value = when (defaultReplyType) {
                                        DefaultReplyType.MAIN_MENU -> "📋 Send Main Menu"
                                        DefaultReplyType.SAME_MENU -> "🔄 Send Same Menu Again"
                                        DefaultReplyType.CUSTOM_MESSAGE -> "💬 Send Custom Message"
                                    },
                                    onValueChange = {},
                                    readOnly = true,
                                    trailingIcon = { 
                                        ExposedDropdownMenuDefaults.TrailingIcon(
                                            expanded = showReplyTypeDropdown
                                        ) 
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .menuAnchor(),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = PremiumColors.AccentPrimary,
                                        unfocusedBorderColor = PremiumColors.DividerColor,
                                        focusedTextColor = PremiumColors.TextPrimary,
                                        unfocusedTextColor = PremiumColors.TextSecondary,
                                        focusedContainerColor = PremiumColors.CardBackgroundSecondary,
                                        unfocusedContainerColor = PremiumColors.CardBackgroundSecondary
                                    ),
                                    shape = RoundedCornerShape(12.dp)
                                )
                                
                                ExposedDropdownMenu(
                                    expanded = showReplyTypeDropdown,
                                    onDismissRequest = { showReplyTypeDropdown = false },
                                    modifier = Modifier.background(PremiumColors.CardBackground)
                                ) {
                                    DropdownMenuItem(
                                        text = { 
                                            Text(
                                                "📋 Send Main Menu", 
                                                color = PremiumColors.TextPrimary
                                            ) 
                                        },
                                        onClick = {
                                            defaultReplyType = DefaultReplyType.MAIN_MENU
                                            settingsManager.setDefaultReplyType(DefaultReplyType.MAIN_MENU)
                                            showReplyTypeDropdown = false
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { 
                                            Text(
                                                "🔄 Send Same Menu Again", 
                                                color = PremiumColors.TextPrimary
                                            ) 
                                        },
                                        onClick = {
                                            defaultReplyType = DefaultReplyType.SAME_MENU
                                            settingsManager.setDefaultReplyType(DefaultReplyType.SAME_MENU)
                                            showReplyTypeDropdown = false
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { 
                                            Text(
                                                "💬 Send Custom Message", 
                                                color = PremiumColors.TextPrimary
                                            ) 
                                        },
                                        onClick = {
                                            defaultReplyType = DefaultReplyType.CUSTOM_MESSAGE
                                            settingsManager.setDefaultReplyType(DefaultReplyType.CUSTOM_MESSAGE)
                                            showReplyTypeDropdown = false
                                        }
                                    )
                                }
                            }
                        }
                        
                        // Custom Message Field
                        if (defaultReplyType == DefaultReplyType.CUSTOM_MESSAGE) {
                            Spacer(modifier = Modifier.height(12.dp))
                            OutlinedTextField(
                                value = customReplyMessage,
                                onValueChange = {
                                    customReplyMessage = it
                                    settingsManager.setCustomReplyMessage(it)
                                },
                                label = { 
                                    Text(
                                        "Custom Message", 
                                        color = PremiumColors.AccentSecondary
                                    ) 
                                },
                                placeholder = { 
                                    Text(
                                        "Enter message for invalid option", 
                                        color = PremiumColors.TextTertiary
                                    ) 
                                },
                                modifier = Modifier.fillMaxWidth(),
                                minLines = 2,
                                maxLines = 4,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = PremiumColors.AccentPrimary,
                                    unfocusedBorderColor = PremiumColors.DividerColor,
                                    focusedTextColor = PremiumColors.TextPrimary,
                                    unfocusedTextColor = PremiumColors.TextSecondary,
                                    focusedContainerColor = PremiumColors.CardBackgroundSecondary,
                                    unfocusedContainerColor = PremiumColors.CardBackgroundSecondary
                                ),
                                shape = RoundedCornerShape(12.dp)
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(40.dp))
            }
        }
    }
}

@Composable
fun PremiumSettingsSection(
    title: String, 
    subtitle: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = PremiumColors.CardBackground),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(20.dp)
        ) {
            Text(
                text = title,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = PremiumColors.AccentPrimary,
                modifier = Modifier.padding(bottom = 4.dp)
            )
            Text(
                text = subtitle,
                fontSize = 14.sp,
                color = PremiumColors.TextTertiary,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            content()
        }
    }
}

@Composable
fun PremiumSettingsCheckbox(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = PremiumColors.TextPrimary
            )
            Text(
                text = subtitle,
                fontSize = 13.sp,
                color = PremiumColors.TextSecondary,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
        
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedTrackColor = PremiumColors.AccentPrimary,
                checkedThumbColor = PremiumColors.TextPrimary,
                uncheckedTrackColor = PremiumColors.SwitchTrack,
                uncheckedThumbColor = PremiumColors.TextTertiary
            )
        )
    }
}

@Composable
fun PremiumDivider() {
    HorizontalDivider(
        color = PremiumColors.DividerColor,
        thickness = 1.dp,
        modifier = Modifier.padding(vertical = 8.dp)
    )
}

private fun formatMenuTimeoutLabel(seconds: Int): String {
    return if (seconds >= 60 && seconds % 60 == 0) {
        val minutes = seconds / 60
        "$minutes minute" + if (minutes == 1) "" else "s"
    } else {
        "$seconds seconds"
    }
}
