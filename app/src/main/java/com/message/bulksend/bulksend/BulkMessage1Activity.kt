package com.message.bulksend.bulksend

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.airbnb.lottie.compose.*
import com.message.bulksend.anylatic.ReportlistActivity
import com.message.bulksend.contactmanager.ContactzActivity
import com.message.bulksend.templates.TemplateActivity
import com.message.bulksend.ui.theme.BulksendTestTheme
import kotlinx.coroutines.delay

class BulkMessage1Activity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        setContent {
            BulksendTestTheme {
                BulkMessage1Screen()
            }
        }
    }
}

// Data class for feature items
data class FeatureItem(
    val icon: ImageVector? = null,
    val lottieAsset: String? = null,
    val title: String,
    val subtitle: String = "",
    val infoText: String = "",
    val hasBadge: Boolean = false,
    val badgeText: String = "",
    val gradient: List<Color> = listOf(Color(0xFFE3F2FD), Color(0xFFBBDEFB)),
    val onClick: () -> Unit = {}
)

@Composable
fun TypewriterText(
    text: String,
    modifier: Modifier = Modifier,
    style: androidx.compose.ui.text.TextStyle = LocalTextStyle.current
) {
    var displayedText by remember { mutableStateOf("") }

    LaunchedEffect(text) {
        displayedText = ""
        text.forEachIndexed { index, _ ->
            displayedText = text.substring(0, index + 1)
            delay(50)
        }
    }

    Text(
        text = displayedText,
        modifier = modifier,
        style = style
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BulkMessage1Screen() {
    val context = LocalContext.current
    
    val backgroundBrush = Brush.verticalGradient(
        colors = listOf(
            Color(0xFF0f0c29),
            Color(0xFF302b63),
            Color(0xFF24243e),
            Color(0xFF0f0c29)
        )
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "ChatsPromo",
                        color = Color(0xFF10B981),
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = { 
                            (context as ComponentActivity).finish()
                        }
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color(0xFF00D4FF),
                            modifier = Modifier.size(28.dp)
                        )
                    }
                },
                actions = {
                    // YouTube Tutorial Button
                    AnimatedYouTubeButton(
                        onClick = {
                            context.startActivity(Intent(context, com.message.bulksend.tutorial.VideoTutorialActivity::class.java))
                        }
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        },
        containerColor = Color.Transparent
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(backgroundBrush)
        ) {
            LazyColumn(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize()
                    .background(backgroundBrush),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Report Buttons (Same as MainActivity)
                item { AnimatedReportButtons() }
                
                // Third Report Button - Scheduled History (Full Width) with Purple gradient
                item { 
                    AnimatedReportButton(
                        icon = Icons.Outlined.Schedule,
                        text = "Scheduled History",
                        gradient = Brush.horizontalGradient(
                            colors = listOf(
                                Color(0xFF9C27B0), // Purple
                                Color(0xFF673AB7), // Deep Purple
                                Color(0xFF3F51B5)  // Indigo
                            )
                        ),
                        iconColor = Color.White,
                        textColor = Color.White,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            context.startActivity(Intent(context, com.message.bulksend.bulksend.scheduledcampaign.ScheduleSendActivity::class.java))
                        }
                    )
                }
                
                // Section Divider with Title - Campaign Manager
                item { 
                    SectionDivider(
                        title = "Campaign Manager",
                        icon = Icons.Outlined.Campaign,
                        color = Color(0xFF6366F1)
                    )
                }
                
                // Campaign Manager Section - Modern Grid
                item {
                    ModernFeatureGrid(
                        items = listOf(
                            FeatureItem(
                                lottieAsset = "contactsbook.json",
                                title = "Contact List",
                                subtitle = "Manage campaign contacts",
                                infoText = "Create and manage your contact groups for campaigns.\n\n• Import contacts from CSV, Excel, VCF files\n• Import from Google Sheets\n• Sync WhatsApp contacts\n• Organize contacts into groups\n• Edit or delete contacts anytime",
                                gradient = listOf(Color(0xFFEC4899), Color(0xFFDB2777)), // Pink gradient
                                onClick = {
                                    context.startActivity(Intent(context, ContactzActivity::class.java))
                                }
                            ),
                            FeatureItem(
                                lottieAsset = "notes.json",
                                title = "Templates",
                                subtitle = "Create message templates",
                                infoText = "Create reusable message templates for your campaigns.\n\n• Save frequently used messages\n• Use variables like {name}, {phone}\n• Attach images, videos, documents\n• Quick access during campaign creation\n• Edit or delete templates anytime",
                                gradient = listOf(Color(0xFF10B981), Color(0xFF059669)), // Emerald green gradient
                                onClick = {
                                    context.startActivity(Intent(context, TemplateActivity::class.java))
                                }
                            ),
                            FeatureItem(
                                icon = Icons.Outlined.ContactPage,
                                title = "Extract Contacts",
                                subtitle = "Grab unsaved numbers",
                                infoText = "Extract phone numbers from WhatsApp chats.\n\n• Get unsaved contact numbers\n• Works with individual and group chats\n• Requires accessibility permission\n• Export to CSV or add to contacts\n• Perfect for lead generation",
                                gradient = listOf(Color(0xFFF59E0B), Color(0xFFD97706)),
                                onClick = {
                                    context.startActivity(Intent(context, com.message.bulksend.waextract.TextExtractActivity::class.java))
                                }
                            )
                        )
                    )
                }
                
                // Section Divider - Choose Campaign Style
                item { 
                    SectionDivider(
                        title = "Choose Campaign Style",
                        icon = Icons.Outlined.RocketLaunch,
                        color = Color(0xFFFF6B9D)
                    )
                }
                
                // 4 Horizontal Message Cards - Direct Campaign Creation
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Text Campaign Card
                        CampaignCard(
                            modifier = Modifier.width(130.dp),
                            icon = Icons.Outlined.Message,
                            title = "Text Campaign",
                            gradient = listOf(Color(0xFF667EEA), Color(0xFF764BA2)),
                            onClick = {
                                val intent = Intent(context, ContactSelectActivity::class.java)
                                intent.putExtra("CAMPAIGN_TYPE", "TEXT")
                                intent.putExtra("TARGET_ACTIVITY", "BulktextActivity")
                                context.startActivity(intent)
                            },
                            infoTitle = "Text Campaign",
                            infoDescription = "Send bulk text messages to your contacts instantly via WhatsApp.",
                            infoSteps = listOf(
                                "Select contacts from your saved groups",
                                "Write your message (use #name# for personalization)",
                                "Set delay between messages (5-15 sec recommended)",
                                "Click Start - app opens WhatsApp chat for each contact",
                                "Accessibility service auto-clicks send button",
                                "Track progress via overlay - pause/resume anytime"
                            )
                        )
                        
                        // Caption Campaign Card
                        CampaignCard(
                            modifier = Modifier.width(130.dp),
                            icon = Icons.Outlined.Image,
                            title = "Media Campaign",
                            gradient = listOf(Color(0xFFFF6B9D), Color(0xFFC44569)),
                            onClick = {
                                val intent = Intent(context, ContactSelectActivity::class.java)
                                intent.putExtra("CAMPAIGN_TYPE", "MEDIA")
                                intent.putExtra("TARGET_ACTIVITY", "BulksendActivity")
                                context.startActivity(intent)
                            },
                            infoTitle = "Caption Campaign",
                            infoDescription = "Send media files (images/videos/docs) with caption text to contacts.",
                            infoSteps = listOf(
                                "Select contacts from your saved groups",
                                "Attach media file (image, video, PDF, etc.)",
                                "Write caption text for the media",
                                "Set delay between sends",
                                "Click Start - app shares media via WhatsApp",
                                "Caption is sent along with media file",
                                "Track progress and pause/resume via overlay"
                            )
                        )
                        
                        // Text + Media Campaign Card
                        CampaignCard(
                            modifier = Modifier.width(130.dp),
                            icon = Icons.Outlined.PermMedia,
                            title = "Text + Media",
                            gradient = listOf(Color(0xFFFFD740), Color(0xFFF9A825)),
                            onClick = {
                                val intent = Intent(context, ContactSelectActivity::class.java)
                                intent.putExtra("CAMPAIGN_TYPE", "TEXT_AND_MEDIA")
                                intent.putExtra("TARGET_ACTIVITY", "TextmediaActivity")
                                context.startActivity(intent)
                            },
                            infoTitle = "Text + Media Campaign",
                            infoDescription = "Send both text message AND media file separately to each contact.",
                            infoSteps = listOf(
                                "Select contacts from your saved groups",
                                "Write your text message",
                                "Attach media file (image, video, etc.)",
                                "Choose order: Text First or Media First",
                                "Click Start - sends text message first",
                                "Then sends media file separately",
                                "Both are delivered as separate messages",
                                "Track progress via overlay"
                            )
                        )
                        
                        // Sheet Campaign Card
                        CampaignCard(
                            modifier = Modifier.width(130.dp),
                            icon = Icons.Outlined.GridOn,
                            title = "Sheet Campaign",
                            gradient = listOf(Color(0xFF43A047), Color(0xFF66BB6A)),
                            onClick = {
                                val intent = Intent(context, com.message.bulksend.bulksend.sheetscampaign.SheetsendActivity::class.java)
                                context.startActivity(intent)
                            },
                            infoTitle = "Sheet Campaign",
                            infoDescription = "Send personalized messages using data from Google Sheets or Excel files.",
                            infoSteps = listOf(
                                "Upload Google Sheet link or Excel (.xlsx) file",
                                "Sheet must have 'Number' column (required)",
                                "Other columns like Name, OrderId are optional",
                                "If sheet has 'Message' column - sends directly",
                                "Otherwise, write template with placeholders",
                                "Use {{Name}}, {{OrderId}}, {{Date}} etc.",
                                "Each row's data replaces placeholders",
                                "Unique personalized message for each contact"
                            )
                        )
                    }
                }

                // === Section Divider: Autonomous Sender ===
                item {
                    SectionDivider(
                        title = "Autonomous Sender",
                        icon = Icons.Filled.SmartToy,
                        color = Color(0xFF00E5FF)
                    )
                }

                // AI Agent BulkSend Card (full-width)
                item {
                    AutonomousSenderCard(
                        onClick = {
                            context.startActivity(
                                Intent(context, AutonomousBulkSendActivity::class.java)
                            )
                        }
                    )
                }

            }
        }
    }
}

// Unique AnimatedSendMessageCard for BulkMessage1Activity
@Composable
fun AnimatedSendMessageCard() {
    var isVisible by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0.8f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy), label = ""
    )

    LaunchedEffect(Unit) {
        delay(300)
        isVisible = true
    }

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .border(
                width = 2.dp,
                color = Color(0xFFFBBF24), // Yellow outline
                shape = RoundedCornerShape(20.dp)
            )
            .clickable { }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF10B981), // Emerald green
                            Color(0xFF059669), // Darker emerald
                            Color(0xFF047857), // Even darker emerald
                            Color(0xFF064E3B)  // Dark emerald
                        ),
                        radius = 800f
                    ),
                    shape = RoundedCornerShape(20.dp)
                )
        ) {
            // Decorative circles
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .offset(x = (-30).dp, y = (-30).dp)
                    .background(
                        Color.White.copy(alpha = 0.1f),
                        CircleShape
                    )
            )
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .offset(x = 280.dp, y = 60.dp)
                    .background(
                        Color.White.copy(alpha = 0.08f),
                        CircleShape
                    )
            )
            
            Row(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    // Campaign Icon
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color.White.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Campaign,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    TypewriterText(
                        "Create Campaign",
                        style = androidx.compose.ui.text.TextStyle(
                            color = Color.White,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    )
                }

                val infiniteTransition = rememberInfiniteTransition(label = "")
                val buttonScale by infiniteTransition.animateFloat(
                    initialValue = 1f,
                    targetValue = 1.15f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(800, easing = FastOutSlowInEasing),
                        repeatMode = RepeatMode.Reverse
                    ), label = ""
                )
                
                val buttonRotation by infiniteTransition.animateFloat(
                    initialValue = -2f,
                    targetValue = 2f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(1000, easing = LinearEasing),
                        repeatMode = RepeatMode.Reverse
                    ), label = ""
                )
                
                val context = LocalContext.current

                Card(
                    modifier = Modifier
                        .scale(buttonScale)
                        .rotate(buttonRotation),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.Transparent)
                ) {
                    Box(
                        modifier = Modifier
                            .background(
                                brush = Brush.linearGradient(
                                    colors = listOf(
                                        Color.White,
                                        Color(0xFFF8FAFC),
                                        Color(0xFFE2E8F0)
                                    )
                                ),
                                shape = RoundedCornerShape(16.dp)
                            )
                            .clickable {
                                val intent = Intent(context, SelectActivity::class.java)
                                context.startActivity(intent)
                            }
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                Icons.Default.RocketLaunch,
                                contentDescription = null,
                                tint = Color(0xFF10B981),
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                "Start Now",
                                color = Color(0xFF10B981),
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

// Same AnimatedReportButtons as MainActivity - Back to 2 cards full width with colors
@Composable
fun AnimatedReportButtons() {
    var isVisible by remember { mutableStateOf(false) }
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        delay(600)
        isVisible = true
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        val reportItems = listOf(
            // Message Reports - White background with blue text
            Tuple7(
                Icons.Outlined.Message, 
                "Message Reports",
                Color.White, // White background
                Color(0xFF3B82F6), // Blue icon
                Color(0xFF1E293B), // Dark text
                11.sp, // Smaller font size
                {
                    context.startActivity(Intent(context, ReportlistActivity::class.java))
                }
            ),
            // Campaign Status - White background with green text
            Tuple7(
                Icons.Outlined.Campaign, 
                "Campaign Status",
                Color.White, // White background
                Color(0xFF10B981), // Green icon
                Color(0xFF1E293B), // Dark text
                11.sp, // Smaller font size
                {
                    context.startActivity(Intent(context, CampaignStatusActivity::class.java))
                }
            )
        )

        reportItems.forEachIndexed { index, (icon, text, bgColor, iconColor, textColor, fontSize, onClickAction) ->
            val offsetX by animateIntAsState(
                targetValue = if (isVisible) 0 else if (index == 0) -300 else 300,
                animationSpec = tween(800, delayMillis = index * 100), label = ""
            )

            AnimatedReportButton(
                icon = icon,
                text = text,
                containerColor = bgColor,
                iconColor = iconColor,
                textColor = textColor,
                fontSize = fontSize,
                modifier = Modifier
                    .weight(1f)
                    .offset(x = offsetX.dp),
                onClick = onClickAction
            )
        }
    }
}

// Helper data class for 7 parameters
data class Tuple7<A, B, C, D, E, F, G>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D,
    val fifth: E,
    val sixth: F,
    val seventh: G
)

@Composable
fun AnimatedReportButton(
    icon: ImageVector,
    text: String,
    modifier: Modifier = Modifier,
    containerColor: Color = Color.White,
    gradient: Brush? = null,
    iconColor: Color = Color(0xFF4F46E5),
    textColor: Color = Color(0xFF1E293B),
    fontSize: androidx.compose.ui.unit.TextUnit = 13.sp,
    onClick: () -> Unit
) {
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = tween(100), label = ""
    )

    Card(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        modifier = modifier
            .height(70.dp)
            .scale(scale),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = gradient ?: Brush.linearGradient(listOf(containerColor, containerColor)),
                    shape = RoundedCornerShape(16.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    icon,
                    contentDescription = text,
                    tint = iconColor,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text,
                    color = textColor,
                    fontWeight = FontWeight.Medium,
                    fontSize = fontSize
                )
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    Icons.AutoMirrored.Filled.ArrowForwardIos,
                    contentDescription = null,
                    tint = textColor.copy(alpha = 0.6f),
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}

// Same SectionDivider as MainActivity
@Composable
fun SectionDivider(title: String, icon: ImageVector, color: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(color.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = color, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(12.dp))
        Text(
            title,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            color = Color.White
        )
        Spacer(Modifier.weight(1f))
        Box(
            modifier = Modifier
                .weight(1f)
                .height(1.dp)
                .background(
                    brush = Brush.horizontalGradient(
                        colors = listOf(color.copy(alpha = 0.5f), Color.Transparent)
                    )
                )
        )
    }
}

// Same ModernFeatureGrid as MainActivity
@Composable
fun ModernFeatureGrid(items: List<FeatureItem>) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items.forEach { item ->
            CompactFeatureCard(item)
        }
    }
}

// Same CompactFeatureCard as MainActivity
@Composable
fun CompactFeatureCard(item: FeatureItem) {
    var showInfoDialog by remember { mutableStateOf(false) }
    
    if (showInfoDialog && item.infoText.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { showInfoDialog = false },
            containerColor = Color.White,
            shape = RoundedCornerShape(20.dp),
            icon = {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(brush = Brush.horizontalGradient(item.gradient)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        item.icon ?: Icons.Filled.Star,
                        null,
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            },
            title = {
                Text(item.title, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color(0xFF1E293B))
            },
            text = {
                Text(item.infoText, fontSize = 14.sp, lineHeight = 22.sp, color = Color(0xFF64748B))
            },
            confirmButton = {
                Button(
                    onClick = { showInfoDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = item.gradient.firstOrNull() ?: Color(0xFF6366F1)),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Got it!", fontWeight = FontWeight.SemiBold)
                }
            }
        )
    }
    
    Card(
        modifier = Modifier
            .width(130.dp)
            .clickable(onClick = item.onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(brush = Brush.verticalGradient(item.gradient), shape = RoundedCornerShape(16.dp))
                .padding(12.dp)
        ) {
            if (item.infoText.isNotEmpty()) {
                Icon(
                    Icons.Filled.Info,
                    "Info",
                    tint = Color.White.copy(alpha = 0.3f),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(14.dp)
                        .clickable { showInfoDialog = true }
                )
            }
            
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    if (item.lottieAsset != null) {
                        val composition by rememberLottieComposition(LottieCompositionSpec.Asset(item.lottieAsset))
                        val progress by animateLottieCompositionAsState(composition = composition, iterations = LottieConstants.IterateForever)
                        LottieAnimation(composition = composition, progress = { progress }, modifier = Modifier.size(40.dp))
                    } else if (item.icon != null) {
                        Icon(item.icon, null, tint = Color.White, modifier = Modifier.size(24.dp))
                    }
                }
                
                Spacer(Modifier.height(10.dp))
                
                Text(
                    item.title,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    lineHeight = 14.sp
                )
            }
        }
    }
}

// CampaignCard for 4 horizontal message cards - Same size as CompactFeatureCard
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CampaignCard(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    title: String,
    gradient: List<Color>,
    onClick: () -> Unit,
    infoTitle: String = title,
    infoDescription: String = "",
    infoSteps: List<String> = emptyList()
) {
    var showInfoDialog by remember { mutableStateOf(false) }

    Card(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.verticalGradient(gradient),
                    shape = RoundedCornerShape(16.dp)
                )
                .padding(12.dp)
        ) {
            // Info button at top-right corner
            if (infoSteps.isNotEmpty()) {
                Icon(
                    Icons.Filled.Info,
                    "Info",
                    tint = Color.White.copy(alpha = 0.3f),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(14.dp)
                        .clickable { showInfoDialog = true }
                )
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        icon,
                        null,
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(Modifier.height(10.dp))

                Text(
                    title,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    lineHeight = 14.sp
                )
            }
        }
    }
    
    // Info Dialog
    if (showInfoDialog) {
        AlertDialog(
            onDismissRequest = { showInfoDialog = false },
            containerColor = Color.White,
            shape = RoundedCornerShape(20.dp),
            icon = {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(Brush.linearGradient(gradient)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }
            },
            title = {
                Text(
                    infoTitle,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    color = Color(0xFF1E293B)
                )
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (infoDescription.isNotEmpty()) {
                        Text(
                            infoDescription,
                            fontSize = 14.sp,
                            color = Color(0xFF64748B),
                            textAlign = TextAlign.Center
                        )
                    }
                    
                    if (infoSteps.isNotEmpty()) {
                        Text(
                            "📋 Campaign Process:",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                            color = gradient.first()
                        )
                        
                        infoSteps.forEachIndexed { index, step ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.Top
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(22.dp)
                                        .clip(CircleShape)
                                        .background(gradient.first()),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        "${index + 1}",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    step,
                                    fontSize = 13.sp,
                                    color = Color(0xFF64748B),
                                    lineHeight = 18.sp,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { showInfoDialog = false },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = gradient.first()
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Got it!", fontWeight = FontWeight.SemiBold)
                }
            }
        )
    }
}

// ======================================================
// Autonomous Sender Card
// ======================================================

@Composable
fun AutonomousSenderCard(onClick: () -> Unit) {
    var showInfoDialog by remember { mutableStateOf(false) }
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = tween(100), label = ""
    )

    val shimmer = rememberInfiniteTransition(label = "shimmer")
    val shimmerOffset by shimmer.animateFloat(
        initialValue = -600f,
        targetValue = 600f,
        animationSpec = infiniteRepeatable(tween(2800, easing = LinearEasing)),
        label = "shimmerOffset"
    )
    val pulseAlpha by shimmer.animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1200), RepeatMode.Reverse),
        label = "pulseAlpha"
    )

    Box(
        modifier = androidx.compose.ui.Modifier
            .fillMaxWidth()
            .scale(scale)
    ) {
        Card(
            onClick = {
                isPressed = true
                onClick()
            },
            modifier = androidx.compose.ui.Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color.Transparent),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
        ) {
            Box(
                modifier = androidx.compose.ui.Modifier
                    .fillMaxWidth()
                    .background(
                        brush = Brush.linearGradient(
                            colors = listOf(
                                Color(0xFF0A0A20),
                                Color(0xFF0D0D2E),
                                Color(0xFF0A1A30),
                                Color(0xFF0D0D2E)
                            )
                        ),
                        shape = RoundedCornerShape(20.dp)
                    )
                    .border(
                        width = 1.5.dp,
                        brush = Brush.linearGradient(
                            colors = listOf(
                                Color(0xFF00E5FF).copy(alpha = 0.6f),
                                Color(0xFF7C4DFF).copy(alpha = 0.6f),
                                Color(0xFF00E5FF).copy(alpha = 0.6f)
                            ),
                            start = androidx.compose.ui.geometry.Offset(shimmerOffset - 300, 0f),
                            end = androidx.compose.ui.geometry.Offset(shimmerOffset + 300, 0f)
                        ),
                        shape = RoundedCornerShape(20.dp)
                    )
            ) {
                // Decorative glowing orbs
                Box(
                    modifier = androidx.compose.ui.Modifier
                        .size(120.dp)
                        .offset(x = (-20).dp, y = (-20).dp)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    Color(0xFF00E5FF).copy(alpha = 0.12f),
                                    Color.Transparent
                                )
                            ),
                            CircleShape
                        )
                )
                Box(
                    modifier = androidx.compose.ui.Modifier
                        .size(100.dp)
                        .align(Alignment.TopEnd)
                        .offset(x = 10.dp, y = (-10).dp)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    Color(0xFF7C4DFF).copy(alpha = 0.12f),
                                    Color.Transparent
                                )
                            ),
                            CircleShape
                        )
                )

                Column(
                    modifier = androidx.compose.ui.Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                ) {
                    // Top row: badge + info icon
                    Row(
                        modifier = androidx.compose.ui.Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        // AI badge
                        Box(
                            modifier = androidx.compose.ui.Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    Brush.linearGradient(
                                        listOf(Color(0xFF00E5FF).copy(0.2f), Color(0xFF7C4DFF).copy(0.2f))
                                    )
                                )
                                .border(0.5.dp, Color(0xFF00E5FF).copy(0.4f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = androidx.compose.ui.Modifier
                                        .size(6.dp)
                                        .background(
                                            Color(0xFF00E5FF).copy(alpha = pulseAlpha),
                                            CircleShape
                                        )
                                )
                                Text(
                                    "AI AUTONOMOUS",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color(0xFF00E5FF)
                                )
                            }
                        }
                        // Info button
                        Box(
                            modifier = androidx.compose.ui.Modifier
                                .size(28.dp)
                                .background(Color.White.copy(alpha = 0.08f), CircleShape)
                                .clickable { showInfoDialog = true },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Filled.Info,
                                null,
                                tint = Color.White.copy(alpha = 0.5f),
                                modifier = androidx.compose.ui.Modifier.size(16.dp)
                            )
                        }
                    }

                    Spacer(modifier = androidx.compose.ui.Modifier.height(14.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Brain icon
                        Box(
                            modifier = androidx.compose.ui.Modifier
                                .size(56.dp)
                                .background(
                                    Brush.radialGradient(
                                        listOf(
                                            Color(0xFF00E5FF).copy(0.25f),
                                            Color(0xFF7C4DFF).copy(0.15f),
                                            Color.Transparent
                                        )
                                    ),
                                    CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Filled.Psychology,
                                null,
                                tint = Color(0xFF00E5FF),
                                modifier = androidx.compose.ui.Modifier.size(34.dp)
                            )
                        }

                        Column(modifier = androidx.compose.ui.Modifier.weight(1f)) {
                            Text(
                                "AI Agent BulkSend",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 20.sp,
                                color = Color.White
                            )
                            Text(
                                "Smart scheduling • Unique messages • Risk analysis",
                                fontSize = 12.sp,
                                color = Color.White.copy(alpha = 0.55f)
                            )
                        }
                    }

                    Spacer(modifier = androidx.compose.ui.Modifier.height(16.dp))

                    // Feature pills
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = androidx.compose.ui.Modifier.fillMaxWidth()
                    ) {
                        AutonomousFeaturePill("🧠 AI Timing", Color(0xFF00E5FF))
                        AutonomousFeaturePill("🛡️ Anti-ban", Color(0xFF7C4DFF))
                        AutonomousFeaturePill("✨ Unique Msgs", Color(0xFF00C853))
                    }

                    Spacer(modifier = androidx.compose.ui.Modifier.height(16.dp))

                    // Launch button
                    Box(
                        modifier = androidx.compose.ui.Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                Brush.linearGradient(
                                    listOf(Color(0xFF00B8D4), Color(0xFF7C4DFF))
                                )
                            )
                            .clickable(onClick = onClick)
                            .padding(vertical = 14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Filled.SmartToy,
                                null,
                                tint = Color.White,
                                modifier = androidx.compose.ui.Modifier.size(18.dp)
                            )
                            Text(
                                "Activate Autonomous Sender",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 14.sp,
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }
    }

    // Info Dialog
    if (showInfoDialog) {
        AutonomousSenderInfoDialog(onDismiss = { showInfoDialog = false })
    }
}

@Composable
fun AutonomousFeaturePill(text: String, color: Color) {
    Box(
        modifier = androidx.compose.ui.Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.12f))
            .border(0.5.dp, color.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(text, fontSize = 11.sp, color = color, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun AutonomousSenderInfoDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF0D0D1A),
        shape = RoundedCornerShape(24.dp),
        icon = {
            Box(
                modifier = androidx.compose.ui.Modifier
                    .size(64.dp)
                    .background(
                        Brush.radialGradient(
                            listOf(Color(0xFF00E5FF).copy(0.3f), Color(0xFF7C4DFF).copy(0.2f), Color.Transparent)
                        ),
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.Psychology,
                    null,
                    tint = Color(0xFF00E5FF),
                    modifier = androidx.compose.ui.Modifier.size(38.dp)
                )
            }
        },
        title = {
            Text(
                "AI Agent BulkSend",
                fontWeight = FontWeight.ExtraBold,
                fontSize = 20.sp,
                color = Color(0xFF00E5FF),
                textAlign = TextAlign.Center
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "The AI Autonomous Sender handles everything — you just add contacts & message. The AI does the rest.",
                    fontSize = 13.sp,
                    color = Color.White.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center,
                    lineHeight = 19.sp
                )

                HorizontalDivider(color = Color(0xFF2A2A4A))

                listOf(
                    Triple(Icons.Filled.Schedule, Color(0xFF00E5FF), "Smart Auto-Scheduling" to "AI calculates the safest time of day (9–12AM, 3–6PM) to send based on your timezone and volume."),
                    Triple(Icons.Filled.Shield, Color(0xFF00C853), "Risk Calculator" to "Computes a risk score (0–100) based on contact volume, time of day, and weekends. Adjusts delay automatically."),
                    Triple(Icons.Filled.AutoAwesome, Color(0xFF7C4DFF), "Unique Every Message" to "Each message gets a subtle unique AI signature — invisible to receivers but prevents WhatsApp spam detection."),
                    Triple(Icons.Filled.Person, Color(0xFFFFD740), "#name# Personalization" to "Use #name# in your message and AI replaces it with each contact's name for genuine personal touch."),
                    Triple(Icons.Filled.BarChart, Color(0xFFFF6D00), "Risk Levels" to "Low (45–180s delay) • Medium (20–60s) • Aggressive (8–20s). AI recommends Low for maximum safety.")
                ).forEach { (icon, color, pair) ->
                    val (heading, desc) = pair
                    Row(
                        modifier = androidx.compose.ui.Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(color.copy(alpha = 0.07f))
                            .padding(10.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(icon, null, tint = color, modifier = androidx.compose.ui.Modifier.size(18.dp))
                        Column {
                            Text(heading, fontWeight = FontWeight.Bold, color = color, fontSize = 13.sp)
                            Text(desc, fontSize = 12.sp, color = Color.White.copy(alpha = 0.6f), lineHeight = 17.sp)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF00B8D4)
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = androidx.compose.ui.Modifier.fillMaxWidth()
            ) {
                Text("Let's Go! 🚀", fontWeight = FontWeight.ExtraBold)
            }
        }
    )
}

// YouTube Tutorial Button with Lottie Animation
@Composable
fun AnimatedYouTubeButton(onClick: () -> Unit) {
    val composition by rememberLottieComposition(
        LottieCompositionSpec.Asset("Youtube.json")
    )
    val progress by animateLottieCompositionAsState(
        composition = composition,
        iterations = LottieConstants.IterateForever
    )

    // Show icon if composition is null (file not found)
    if (composition == null) {
        IconButton(onClick = onClick) {
            Icon(
                Icons.Outlined.PlayCircle,
                contentDescription = "Video Tutorial",
                tint = Color.White,
                modifier = Modifier.size(28.dp)
            )
        }
    } else {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clickable(onClick = onClick)
                .padding(4.dp),
            contentAlignment = Alignment.Center
        ) {
            LottieAnimation(
                composition = composition,
                progress = { progress },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}