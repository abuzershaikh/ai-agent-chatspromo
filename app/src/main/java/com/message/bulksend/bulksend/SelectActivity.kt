package com.message.bulksend.bulksend

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.message.bulksend.bulksend.sheetscampaign.SheetsendActivity
import com.message.bulksend.bulksend.textcamp.BulktextActivity
import com.message.bulksend.bulksend.textmedia.TextmediaActivity
import kotlinx.coroutines.delay


class SelectActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SelectCampaignTheme {
                SelectCampaignScreen()
            }
        }
    }
}

@Composable
fun SelectCampaignTheme(content: @Composable () -> Unit) {
    // Enhanced white theme with vibrant accent colors
    val colors = lightColorScheme(
        primary = Color(0xFF6C63FF), // Modern purple
        primaryContainer = Color(0xFFE8E1FF),
        secondary = Color(0xFFFF6B9D), // Pink accent
        secondaryContainer = Color(0xFFFFD6E5),
        tertiary = Color(0xFF4ECDC4), // Teal
        tertiaryContainer = Color(0xFFC5F6F2),
        surface = Color(0xFFFFFFFF),
        background = Color(0xFFF8FAFF), // Very light blue-white
        onSurface = Color(0xFF1A1A2E),
        onBackground = Color(0xFF16213E),
        outline = Color(0xFFE8E8E8),
        outlineVariant = Color(0xFFD0D0D0),
        error = Color(0xFFE53935),
        onError = Color.White,
        surfaceVariant = Color(0xFFF5F5F5)
    )

    MaterialTheme(
        colorScheme = colors,
        content = content
    )
}

@Composable
fun SelectCampaignScreen() {
    val context = LocalContext.current
    var isVisible by remember { mutableStateOf(false) }

    // SharedPreferences for saving view preference
    val sharedPrefs = remember {
        context.getSharedPreferences("SelectActivityPrefs", Context.MODE_PRIVATE)
    }

    // Load saved preference, default to true (grid view)
    var isGridView by remember {
        mutableStateOf(sharedPrefs.getBoolean("isGridView", true))
    }

    // Trigger animation on composition
    LaunchedEffect(Unit) {
        delay(100)
        isVisible = true
    }

    // Animated gradient background
    val infiniteTransition = rememberInfiniteTransition(label = "background")
    val gradientOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "gradient"
    )

    val backgroundBrush = Brush.verticalGradient(
        colors = listOf(
            Color(0xFFFFFFFF).copy(alpha = 0.9f),
            Color(0xFFF8FAFF).copy(alpha = 0.8f + gradientOffset * 0.2f),
            Color(0xFFF0F4FF).copy(alpha = 0.7f + gradientOffset * 0.3f)
        )
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundBrush)
    ) {
        // Floating particles animation
        FloatingParticles()

        Scaffold(
            containerColor = Color.Transparent
        ) { innerPadding ->
            // Use a regular Column with scrolling for small device support
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Animated header
                AnimatedVisibility(
                    visible = isVisible,
                    enter = slideInVertically(
                        initialOffsetY = { -100 },
                        animationSpec = tween(800, easing = FastOutSlowInEasing)
                    ) + fadeIn(animationSpec = tween(800))
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        val rotation by infiniteTransition.animateFloat(
                            initialValue = 0f,
                            targetValue = 360f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(10000, easing = LinearEasing)
                            ), label = "rotation"
                        )

                        Box(
                            modifier = Modifier
                                .size(60.dp) // Reduced from 80dp
                                .clip(CircleShape)
                                .background(
                                    Brush.linearGradient(
                                        colors = listOf(
                                            MaterialTheme.colorScheme.primary,
                                            MaterialTheme.colorScheme.secondary
                                        )
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Campaign,
                                contentDescription = "Campaign",
                                tint = Color.White,
                                modifier = Modifier
                                    .size(30.dp) // Reduced from 40dp
                                    .rotate(rotation * 0.1f)
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp)) // Reduced from 16dp

                        Text(
                            text = "Choose Your Campaign Style",
                            style = MaterialTheme.typography.titleLarge, // Changed from headlineMedium
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground,
                            textAlign = TextAlign.Center
                        )

                        Text(
                            text = "Select the perfect way to reach your audience",
                            style = MaterialTheme.typography.bodyMedium, // Changed from bodyLarge
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 6.dp) // Reduced from 8dp
                        )

                        Spacer(modifier = Modifier.height(12.dp)) // Reduced from 16dp

                        // View Toggle Buttons
                        Row(
                            modifier = Modifier
                                .background(
                                    MaterialTheme.colorScheme.surfaceVariant,
                                    RoundedCornerShape(12.dp)
                                )
                                .padding(4.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            ViewToggleButton(
                                icon = Icons.Default.Apps,
                                text = "Grid",
                                isSelected = isGridView,
                                onClick = {
                                    isGridView = true
                                    sharedPrefs.edit().putBoolean("isGridView", true).apply()
                                }
                            )
                            ViewToggleButton(
                                icon = Icons.Default.ViewList,
                                text = "List",
                                isSelected = !isGridView,
                                onClick = {
                                    isGridView = false
                                    sharedPrefs.edit().putBoolean("isGridView", false).apply()
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))

                // Animated cards - Grid or List view
                AnimatedVisibility(
                    visible = isVisible,
                    enter = slideInVertically(
                        initialOffsetY = { 200 },
                        animationSpec = tween(1000, delayMillis = 300, easing = FastOutSlowInEasing)
                    ) + fadeIn(animationSpec = tween(1000, delayMillis = 300))
                ) {
                    if (isGridView) {
                        // Grid View - 2 columns with minimal info
                        Column(
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                GridCampaignCard(
                                    modifier = Modifier.weight(1f),
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
                                GridCampaignCard(
                                    modifier = Modifier.weight(1f),
                                    icon = Icons.Outlined.Image,
                                    title = "Caption Campaign",
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
                            }
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                GridCampaignCard(
                                    modifier = Modifier.weight(1f),
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
                                GridCampaignCard(
                                    modifier = Modifier.weight(1f),
                                    icon = Icons.Outlined.GridOn,
                                    title = "Sheet Campaign",
                                    gradient = listOf(Color(0xFF43A047), Color(0xFF66BB6A)),
                                    onClick = {
                                        val intent = Intent(context, SheetsendActivity::class.java)
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
                    } else {
                        // List View - Original detailed cards
                        Column(
                            verticalArrangement = Arrangement.spacedBy(24.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            EnhancedCampaignTypeCard(
                                modifier = Modifier.fillMaxWidth(),
                                icon = Icons.Outlined.Message,
                                title = "Text Campaign",
                                description = "Send bulk text messages instantly",
                                benefits = listOf(
                                    "Fast and direct communication",
                                    "High open rates",
                                    "Cost-effective for large audiences",
                                    "Personalized messaging options"
                                ),
                                gradient = listOf(
                                    Color(0xFF667EEA),
                                    Color(0xFF764BA2)
                                ),
                                onClick = {
                                    val intent = Intent(context, ContactSelectActivity::class.java)
                                    intent.putExtra("CAMPAIGN_TYPE", "TEXT")
                                    intent.putExtra("TARGET_ACTIVITY", "BulktextActivity")
                                    context.startActivity(intent)
                                }
                            )

                            EnhancedCampaignTypeCard(
                                modifier = Modifier.fillMaxWidth(),
                                icon = Icons.Outlined.Image,
                                title = "Caption Campaign",
                                description = "Send media files with text captions",
                                benefits = listOf(
                                    "Send images, videos, or documents",
                                    "Add optional text caption with media",
                                    "Perfect for promotional content",
                                    "Visual content increases engagement"
                                ),
                                gradient = listOf(
                                    Color(0xFFFF6B9D),
                                    Color(0xFFC44569)
                                ),
                                onClick = {
                                    val intent = Intent(context, ContactSelectActivity::class.java)
                                    intent.putExtra("CAMPAIGN_TYPE", "MEDIA")
                                    intent.putExtra("TARGET_ACTIVITY", "BulksendActivity")
                                    context.startActivity(intent)
                                }
                            )

                            EnhancedCampaignTypeCard(
                                modifier = Modifier.fillMaxWidth(),
                                icon = Icons.Outlined.PermMedia,
                                title = "Text + Media Campaign",
                                description = "Combine text and media for maximum impact",
                                benefits = listOf(
                                    "Complete messaging solution",
                                    "Maximum engagement rates",
                                    "Versatile content delivery",
                                    "Best for product launches and events"
                                ),
                                gradient = listOf(
                                    Color(0xFFFFD740),
                                    Color(0xFFF9A825)
                                ),
                                onClick = {
                                    val intent = Intent(context, ContactSelectActivity::class.java)
                                    intent.putExtra("CAMPAIGN_TYPE", "TEXT_AND_MEDIA")
                                    intent.putExtra("TARGET_ACTIVITY", "TextmediaActivity")
                                    context.startActivity(intent)
                                }
                            )
                            EnhancedCampaignTypeCard(
                                modifier = Modifier.fillMaxWidth(),
                                icon = Icons.Outlined.GridOn,
                                title = "Sheet Campaign",
                                description = "Send personalized messages from Google Sheet or Excel Sheet with placeholders.",
                                benefits = listOf(
                                    "Upload a Google Sheet (link) or Excel Sheet (.xlsx).",
                                    "Only 'Number' column is required, other fields are optional.",
                                    "If the sheet already has a 'Message' column, messages will be sent directly.",
                                    "If not, write one Compose message with placeholders (e.g., {{Name}}, {{OrderId}}, {{Date}}).",
                                    "Each row’s data will auto-replace the placeholders to generate unique messages."
                                ),
                                gradient = listOf(
                                    Color(0xFF43A047),
                                    Color(0xFF66BB6A)
                                ),
                                onClick = {
                                    val intent = Intent(context, SheetsendActivity::class.java)
                                    context.startActivity(intent)
                                }
                            )


                        }
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

@Composable
fun FloatingParticles() {
    val particles = remember {
        List(12) {
            Particle(
                x = (0..100).random().dp,
                y = (0..100).random().dp,
                size = (4..8).random().dp,
                color = listOf(
                    Color(0xFF6C63FF).copy(alpha = 0.3f),
                    Color(0xFFFF6B9D).copy(alpha = 0.3f),
                    Color(0xFF4ECDC4).copy(alpha = 0.3f),
                    Color(0xFFFFD93D).copy(alpha = 0.3f)
                ).random()
            )
        }
    }

    particles.forEach { particle ->
        FloatingParticle(particle = particle)
    }
}

@Composable
fun FloatingParticle(particle: Particle) {
    val infiniteTransition = rememberInfiniteTransition(label = "particle")
    val offsetY by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 40f,
        animationSpec = infiniteRepeatable(
            animation = tween((3000..5000).random(), easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "offsetY"
    )
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.1f,
        targetValue = 0.5f,
        animationSpec = infiniteRepeatable(
            animation = tween((2000..4000).random(), easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "alpha"
    )

    Box(
        modifier = Modifier
            .offset(x = particle.x, y = particle.y + offsetY.dp)
            .size(particle.size)
            .clip(CircleShape)
            .background(particle.color.copy(alpha = alpha))
    )
}

data class Particle(
    val x: Dp,
    val y: Dp,
    val size: Dp,
    val color: Color
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EnhancedCampaignTypeCard(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    title: String,
    description: String,
    benefits: List<String>,
    gradient: List<Color>,
    onClick: () -> Unit
) {
    var isPressed by remember { mutableStateOf(false) }
    var isExpanded by remember { mutableStateOf(false) }

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ), label = "scale"
    )

    Card(
        onClick = onClick,
        modifier = modifier
            .scale(scale)
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) {
                isPressed = !isPressed
            },
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, gradient.first().copy(alpha = 0.2f)),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 8.dp,
            pressedElevation = 12.dp
        )
    ) {
        Box {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.linearGradient(
                            colors = gradient.map { it.copy(alpha = 0.05f) }
                        )
                    )
            )

            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(gradient)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }

                Text(
                    text = title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Text(
                    text = description,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    lineHeight = 18.sp
                )

                // Benefits section
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.Start,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Key Benefits:",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f)
                        )

                        IconButton(
                            onClick = { isExpanded = !isExpanded },
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer)
                        ) {
                            Icon(
                                imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                contentDescription = if (isExpanded) "Collapse" else "Expand",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    AnimatedVisibility(
                        visible = isExpanded,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            benefits.forEach { benefit ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .clip(CircleShape)
                                            .background(gradient.first())
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = benefit,
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                        lineHeight = 16.sp
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(
                            Brush.horizontalGradient(gradient)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Select Campaign",
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp
                        )
                        Icon(
                            imageVector = Icons.Default.ArrowForward,
                            contentDescription = "Start",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    val shimmerTransition = rememberInfiniteTransition(label = "shimmer")
                    val shimmerTranslate by shimmerTransition.animateFloat(
                        initialValue = -300f,
                        targetValue = 1300f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(durationMillis = 1500, delayMillis = 500, easing = LinearEasing),
                            repeatMode = RepeatMode.Restart
                        ), label = "shimmer_translate"
                    )

                    val shimmerBrush = Brush.horizontalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.0f),
                            Color.White.copy(alpha = 0.4f),
                            Color.White.copy(alpha = 0.0f),
                        ),
                        startX = shimmerTranslate - 100f,
                        endX = shimmerTranslate
                    )

                    Box(modifier = Modifier
                        .fillMaxSize()
                        .background(shimmerBrush)
                    )
                }
            }
        }
    }
}

@Composable
fun ViewToggleButton(
    icon: ImageVector,
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val backgroundColor = if (isSelected) {
        MaterialTheme.colorScheme.primary
    } else {
        Color.Transparent
    }

    val contentColor = if (isSelected) {
        Color.White
    } else {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
    }

    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = backgroundColor,
            contentColor = contentColor
        ),
        shape = RoundedCornerShape(8.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = if (isSelected) 2.dp else 0.dp
        )
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = text,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = text,
                fontSize = 12.sp,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GridCampaignCard(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    title: String,
    gradient: List<Color>,
    onClick: () -> Unit,
    infoTitle: String = title,
    infoDescription: String = "",
    infoSteps: List<String> = emptyList()
) {
    var isPressed by remember { mutableStateOf(false) }
    var showInfoDialog by remember { mutableStateOf(false) }

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ), label = "scale"
    )

    Card(
        onClick = onClick,
        modifier = modifier
            .scale(scale)
            .aspectRatio(1f) // Square cards
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) {
                isPressed = !isPressed
            },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, gradient.first().copy(alpha = 0.2f)),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 4.dp,
            pressedElevation = 8.dp
        )
    ) {
        Box {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.linearGradient(
                            colors = gradient.map { it.copy(alpha = 0.05f) }
                        )
                    )
            )
            
            // Info button at top-right corner
            IconButton(
                onClick = { showInfoDialog = true },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .size(28.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Info,
                    contentDescription = "Info",
                    tint = gradient.first().copy(alpha = 0.7f),
                    modifier = Modifier.size(18.dp)
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(gradient)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = title,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    lineHeight = 16.sp
                )
            }
        }
    }
    
    // Info Dialog
    if (showInfoDialog) {
        AlertDialog(
            onDismissRequest = { showInfoDialog = false },
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
                    textAlign = TextAlign.Center
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
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
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
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
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
                    )
                ) {
                    Text("Got it!")
                }
            }
        )
    }
}

@Preview(showBackground = true, device = "spec:width=360dp,height=640dp,dpi=480")
@Composable
fun SelectCampaignScreenPreview() {
    SelectCampaignTheme {
        SelectCampaignScreen()
    }
}

