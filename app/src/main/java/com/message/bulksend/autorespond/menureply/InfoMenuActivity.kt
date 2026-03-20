package com.message.bulksend.autorespond.menureply

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
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.message.bulksend.ui.theme.BulksendTestTheme

class InfoMenuActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { BulksendTestTheme { InfoMenuScreen { finish() } } }
    }
}

// Info Colors - Clean and Professional
object InfoColors {
    val DarkBackground = Color(0xFF0F0F23)
    val CardBackground = Color(0xFF1E293B)
    val CardBackgroundSecondary = Color(0xFF1E40AF)
    val AccentPrimary = Color(0xFF8B5CF6)
    val AccentSecondary = Color(0xFF06B6D4)
    val AccentGreen = Color(0xFF10B981)
    val AccentOrange = Color(0xFFF59E0B)
    val TextPrimary = Color(0xFFFFFFFF)
    val TextSecondary = Color(0xFFD1D5DB)
    val TextTertiary = Color(0xFF9CA3AF)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InfoMenuScreen(onBackPressed: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        "Menu Reply Guide", 
                        color = InfoColors.TextPrimary, 
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    ) 
                },
                navigationIcon = { 
                    IconButton(onClick = onBackPressed) { 
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = InfoColors.TextPrimary) 
                    } 
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = InfoColors.CardBackground)
            )
        },
        containerColor = InfoColors.DarkBackground
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            InfoColors.DarkBackground,
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
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Welcome Section
                InfoCard(
                    title = "🎯 Menu Reply System",
                    subtitle = "Automated menu-based responses for WhatsApp",
                    cardColor = InfoColors.CardBackground,
                    accentColor = InfoColors.AccentPrimary
                ) {
                    Text(
                        "Menu Reply system automatically sends interactive menus to users when they message you. Users can select options by sending numbers, and you can create multi-level menus with submenus.",
                        color = InfoColors.TextSecondary,
                        fontSize = 14.sp,
                        lineHeight = 20.sp
                    )
                }

                // How to Create Menu
                InfoCard(
                    title = "📝 How to Create Menu",
                    subtitle = "Step-by-step guide to build your menu",
                    cardColor = InfoColors.CardBackgroundSecondary,
                    accentColor = InfoColors.AccentSecondary
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        InfoStep(
                            step = "1",
                            title = "Set Root Message",
                            description = "Click on root card to set main menu message (e.g., 'Select option')"
                        )
                        InfoStep(
                            step = "2", 
                            title = "Add Menu Items",
                            description = "Click 'ADD MENU ITEM' to add options. Each item gets a number (1, 2, 3...)"
                        )
                        InfoStep(
                            step = "3",
                            title = "Configure Items",
                            description = "For each item: Set title, choose if it has submenu, or set final message"
                        )
                        InfoStep(
                            step = "4",
                            title = "Create Submenus",
                            description = "Click 'SUBMENU' on items to create multi-level menus"
                        )
                    }
                }

                // Menu Types
                InfoCard(
                    title = "🔧 Menu Item Types",
                    subtitle = "Different types of menu items you can create",
                    cardColor = InfoColors.CardBackground,
                    accentColor = InfoColors.AccentGreen
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        InfoFeature(
                            icon = Icons.Default.Message,
                            title = "Final Message Item",
                            description = "Sends a reply only when a leaf item is selected.",
                            color = InfoColors.AccentGreen
                        )
                        InfoFeature(
                            icon = Icons.Default.List,
                            title = "Submenu Item", 
                            description = "Opens another menu. Final reply stays hidden until user chooses an item inside it.",
                            color = InfoColors.AccentSecondary
                        )
                        InfoFeature(
                            icon = Icons.Default.Settings,
                            title = "Navigation Options",
                            description = "Auto-added options like 'Main Menu' (0) and 'Previous Menu'.",
                            color = InfoColors.AccentOrange
                        )
                    }
                }

                // Settings Guide
                InfoCard(
                    title = "⚙️ Settings Configuration",
                    subtitle = "Customize menu behavior and appearance",
                    cardColor = InfoColors.CardBackgroundSecondary,
                    accentColor = InfoColors.AccentOrange
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        InfoFeature(
                            icon = Icons.Default.Visibility,
                            title = "Display Options",
                            description = "Show emojis, bold header, and optional selection hint above the menu.",
                            color = InfoColors.AccentPrimary
                        )
                        InfoFeature(
                            icon = Icons.Default.Navigation,
                            title = "Navigation Options",
                            description = "Enable Main Menu (0) and Previous Menu options",
                            color = InfoColors.AccentSecondary
                        )
                        InfoFeature(
                            icon = Icons.Default.Timer,
                            title = "Session Timeout",
                            description = "Default is 40 seconds. After timeout, user must send the keyword again.",
                            color = InfoColors.AccentPrimary
                        )
                        InfoFeature(
                            icon = Icons.Default.CheckCircle,
                            title = "After Final Selection",
                            description = "What to do when user sends numbers after completing menu",
                            color = InfoColors.AccentGreen
                        )
                        InfoFeature(
                            icon = Icons.Default.Warning,
                            title = "Invalid Option Reply",
                            description = "Response when user sends invalid option number",
                            color = InfoColors.AccentOrange
                        )
                    }
                }

                // Usage Examples
                InfoCard(
                    title = "💡 Usage Examples",
                    subtitle = "Real-world menu examples",
                    cardColor = InfoColors.CardBackground,
                    accentColor = InfoColors.AccentPrimary
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        ExampleMenu(
                            title = "Business Menu",
                            items = listOf(
                                "1. Our Services",
                                "2. Pricing", 
                                "3. Contact Info",
                                "4. Working Hours",
                                "0. Main Menu"
                            )
                        )
                        ExampleMenu(
                            title = "Support Menu",
                            items = listOf(
                                "1. Technical Support",
                                "2. Billing Questions",
                                "3. Account Issues", 
                                "4. Feature Requests",
                                "0. Main Menu"
                            )
                        )
                    }
                }

                // Tips and Best Practices
                InfoCard(
                    title = "💡 Tips & Best Practices",
                    subtitle = "Make your menus more effective",
                    cardColor = InfoColors.CardBackgroundSecondary,
                    accentColor = InfoColors.AccentGreen
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        InfoTip("Keep menu titles short and clear")
                        InfoTip("Use final messages for important information")
                        InfoTip("Don't create too many submenu levels (max 3-4)")
                        InfoTip("Test your menu flow before using")
                        InfoTip("Enable navigation options for better user experience")
                        InfoTip("Use descriptive final messages instead of just 'OK'")
                    }
                }

                Spacer(modifier = Modifier.height(40.dp))
            }
        }
    }
}

@Composable
fun InfoCard(
    title: String,
    subtitle: String,
    cardColor: Color,
    accentColor: Color,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = cardColor),
        elevation = CardDefaults.cardElevation(8.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Text(
                text = title,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = accentColor,
                modifier = Modifier.padding(bottom = 4.dp)
            )
            Text(
                text = subtitle,
                fontSize = 14.sp,
                color = InfoColors.TextTertiary,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            content()
        }
    }
}

@Composable
fun InfoStep(step: String, title: String, description: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = InfoColors.AccentPrimary,
            modifier = Modifier.size(32.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = step,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = InfoColors.TextPrimary,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp
            )
            Text(
                text = description,
                color = InfoColors.TextSecondary,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}

@Composable
fun InfoFeature(icon: ImageVector, title: String, description: String, color: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = InfoColors.TextPrimary,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp
            )
            Text(
                text = description,
                color = InfoColors.TextSecondary,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}

@Composable
fun ExampleMenu(title: String, items: List<String>) {
    Column {
        Text(
            text = title,
            color = InfoColors.TextPrimary,
            fontWeight = FontWeight.SemiBold,
            fontSize = 15.sp,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Card(
            shape = RoundedCornerShape(8.dp),
            colors = CardDefaults.cardColors(containerColor = InfoColors.DarkBackground.copy(alpha = 0.5f))
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                items.forEach { item ->
                    Text(
                        text = item,
                        color = InfoColors.TextSecondary,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun InfoTip(tip: String) {
    Row(
        verticalAlignment = Alignment.Top,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = "•",
            color = InfoColors.AccentGreen,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = tip,
            color = InfoColors.TextSecondary,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            modifier = Modifier.weight(1f)
        )
    }
}
