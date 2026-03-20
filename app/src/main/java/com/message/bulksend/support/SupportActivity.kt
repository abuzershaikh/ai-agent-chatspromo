package com.message.bulksend.support

import android.content.Context
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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.TextStyle
import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.message.bulksend.ui.theme.BulksendTestTheme
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import android.util.Log

import kotlinx.coroutines.delay

class SupportActivity : ComponentActivity() {
    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        setContent {
            BulksendTestTheme {
                var disableContactExpand by remember { mutableStateOf(false) }
                var isLoadingShowField by remember { mutableStateOf(true) }
                
                // Check 'show' field from userDetails
                LaunchedEffect(Unit) {
                    val userId = auth.currentUser?.uid
                    if (userId != null) {
                        try {
                            firestore.collection("userDetails")
                                .document(userId)
                                .get()
                                .addOnSuccessListener { document ->
                                    if (document.exists()) {
                                        val showValue = document.getLong("show")
                                        disableContactExpand = showValue == 1L
                                        Log.d("SupportActivity", "show field value: $showValue, disableContactExpand: $disableContactExpand")
                                    } else {
                                        Log.d("SupportActivity", "userDetails document not found")
                                    }
                                    isLoadingShowField = false
                                }
                                .addOnFailureListener { e ->
                                    Log.e("SupportActivity", "Error fetching show field", e)
                                    isLoadingShowField = false
                                }
                        } catch (e: Exception) {
                            Log.e("SupportActivity", "Exception checking show field", e)
                            isLoadingShowField = false
                        }
                    } else {
                        isLoadingShowField = false
                    }
                }
                
                if (!isLoadingShowField) {
                    SupportScreen(disableContactExpand = disableContactExpand)
                }
            }
        }
    }
}

// Enhanced main screen with animations
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SupportScreen(disableContactExpand: Boolean = false) {
    val context = LocalContext.current
    var isVisible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        isVisible = true
    }

    Scaffold(
        topBar = {
            SupportTopAppBar(
                onBackClicked = { (context as? ComponentActivity)?.finish() }
            )
        },
        // Changed background to a simpler gradient
        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
    ) { padding ->
        AnimatedVisibility(
            visible = isVisible,
            enter = slideInVertically(
                initialOffsetY = { it },
                animationSpec = tween(1000, easing = EaseOutBounce)
            ) + fadeIn(animationSpec = tween(1000))
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Live Chat Support Card - Prominent at top
                item {
                    LiveChatSupportCard()
                }
                
                // Simple welcome message
                item {
                    WelcomeCard()
                }

                // Channel Cards Section
                item {
                    AnimatedSectionHeader("📢 Join Our Community")
                }

                // Animated channel cards with ripple effect
                itemsIndexed(channelItems) { index, item ->
                    AnimatedChannelCard(
                        item = item,
                        index = index,
                        onClick = { handleContactClick(context, item.action, item.subtitle) }
                    )
                }

                // Section divider
                item {
                    AnimatedSectionDivider()
                }

                // Collapsible Contact Support Card
                item {
                    CollapsibleContactCard(
                        disableExpand = disableContactExpand,
                        onContactClick = { action, data -> handleContactClick(context, action, data) }
                    )
                }

                // Section divider
                item {
                    AnimatedSectionDivider()
                }

                // Pages section header
                item {
                    AnimatedSectionHeader("Explore More")
                }

                // Page cards without animation delay
                items(pageItems) { item ->
                    PageCard(
                        item = item,
                        onClick = {
                            // Open WebViewActivity on click with URL
                            val intent = Intent(context, WebViewActivity::class.java).apply {
                                putExtra("TITLE", item.title)
                                putExtra("URL", item.url)
                            }
                            context.startActivity(intent)
                        }
                    )
                }

                // Bottom spacer
                item {
                    Spacer(modifier = Modifier.height(32.dp))
                }
            }
        }
    }
}

// Animated Live Chat Support Card - Separate from Welcome
@Composable
fun LiveChatSupportCard() {
    val context = LocalContext.current
    var isPressed by remember { mutableStateOf(false) }
    
    // Pulsing glow animation
    val infiniteTransition = rememberInfiniteTransition(label = "live_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.02f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )
    
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow_alpha"
    )
    
    // Live dot blinking
    val dotAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot_blink"
    )
    
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "press_scale"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .scale(pressScale * pulseScale)
            .clickable {
                isPressed = true
                context.startActivity(Intent(context, CustomerChatSupportActivity::class.java))
            },
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            Color(0xFF667eea).copy(alpha = glowAlpha),
                            Color(0xFF764ba2).copy(alpha = glowAlpha),
                            Color(0xFF6B8DD6).copy(alpha = glowAlpha)
                        )
                    )
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Live indicator row
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    // Blinking live dot
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .background(
                                Color(0xFF4CAF50).copy(alpha = dotAlpha),
                                CircleShape
                            )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "LIVE",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF4CAF50),
                            letterSpacing = 2.sp
                        )
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Chat icon with glow effect
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .background(
                            Color.White.copy(alpha = 0.2f),
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.SupportAgent,
                        contentDescription = null,
                        modifier = Modifier.size(40.dp),
                        tint = Color.White
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Main title - smaller size for single line
                Text(
                    text = "💬 Live Customer Support",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                    maxLines = 1
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Subtitle with guarantee
                Text(
                    text = "Your Problem 100% Fix - Chat Now!",
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = FontWeight.Medium,
                        color = Color.White.copy(alpha = 0.95f)
                    )
                )
                
                Spacer(modifier = Modifier.height(6.dp))
                
                Text(
                    text = "Get instant help from our support team",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = Color.White.copy(alpha = 0.8f)
                    )
                )
                
                Spacer(modifier = Modifier.height(20.dp))
                
                // Start Chat Button
                Button(
                    onClick = {
                        context.startActivity(Intent(context, CustomerChatSupportActivity::class.java))
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(26.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Chat,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp),
                        tint = Color(0xFF667eea)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Start Chat Now",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF667eea)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        imageVector = Icons.Default.ArrowForward,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = Color(0xFF667eea)
                    )
                }
            }
        }
    }
    
    LaunchedEffect(isPressed) {
        if (isPressed) {
            delay(150)
            isPressed = false
        }
    }
}

// Simple Welcome Card without chat button
@Composable
fun WelcomeCard() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.SupportAgent,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    text = "We're Here to Help!",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                )
                Text(
                    text = "Get instant support and assistance",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// Top App Bar
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SupportTopAppBar(onBackClicked: () -> Unit) {
    TopAppBar(
        title = {
            Text(
                "Help & Support",
                fontWeight = FontWeight.Bold
            )
        },
        navigationIcon = {
            IconButton(onClick = onBackClicked) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back"
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    )
}

// Typewriter Text Effect
@Composable
fun TypewriterText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = TextStyle.Default
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

// Channel Card with Ripple Effect and Typewriter
@Composable
private fun AnimatedChannelCard(
    item: ContactInfo,
    index: Int,
    onClick: () -> Unit
) {
    // Continuous ripple effect
    val infiniteTransition = rememberInfiniteTransition(label = "ripple")
    val rippleScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ripple_scale"
    )

    val rippleAlpha by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ripple_alpha"
    )

    var isPressed by remember { mutableStateOf(false) }
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy), label = "press_scale"
    )

    val animationDelay = (index * 150).toLong()
    var startAnimation by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(animationDelay)
        startAnimation = true
    }

    AnimatedVisibility(
        visible = startAnimation,
        enter = slideInHorizontally(
            initialOffsetX = { it },
            animationSpec = tween(700, easing = EaseOutBounce)
        ) + fadeIn(animationSpec = tween(700))
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .scale(pressScale * rippleScale)
                .clickable {
                    isPressed = true
                    onClick()
                },
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color.Transparent
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.horizontalGradient(
                            colors = getGradientColors(item.action).map { it.copy(alpha = rippleAlpha) }
                        )
                    )
            ) {
                Row(
                    modifier = Modifier.padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .background(
                                Color.White.copy(alpha = 0.3f),
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = item.icon,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(20.dp))
                    Column {
                        Text(
                            text = item.title,
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontWeight = FontWeight.Medium,
                                color = Color.White
                            )
                        )
                        TypewriterText(
                            text = item.subtitle,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        )
                    }
                }
            }
        }
    }

    LaunchedEffect(isPressed) {
        if (isPressed) {
            delay(100)
            isPressed = false
        }
    }
}

// Collapsible Contact Support Card
@Composable
private fun CollapsibleContactCard(
    disableExpand: Boolean = false,
    onContactClick: (ContactAction, String) -> Unit
) {
    var isExpanded by remember { mutableStateOf(false) }
    val rotationAngle by animateFloatAsState(
        targetValue = if (isExpanded) 180f else 0f,
        animationSpec = tween(300),
        label = "rotation"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow
                )
            ),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column {
            // Header - Always visible, clickable to expand/collapse (disabled if disableExpand = true)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (!disableExpand) {
                            Modifier.clickable { isExpanded = !isExpanded }
                        } else {
                            Modifier
                        }
                    )
                    .padding(20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(
                            Brush.linearGradient(
                                colors = listOf(Color(0xFF667eea), Color(0xFF764ba2))
                            ),
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.ContactSupport,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "📞 Contact Support",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Text(
                        text = "Phone, WhatsApp, Email",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                // Show expand icon only if not disabled
                if (!disableExpand) {
                    Icon(
                        imageVector = Icons.Default.ExpandMore,
                        contentDescription = if (isExpanded) "Collapse" else "Expand",
                        modifier = Modifier
                            .size(28.dp)
                            .graphicsLayer { rotationZ = rotationAngle },
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            // Expandable content (only if not disabled)
            if (isExpanded && !disableExpand) {
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = MaterialTheme.colorScheme.outlineVariant
                )
                
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    contactItems.forEach { item ->
                        ContactOptionRow(
                            item = item,
                            onClick = { onContactClick(item.action, item.subtitle) }
                        )
                    }
                }
            }
        }
    }
}

// Contact Option Row inside collapsible card
@Composable
private fun ContactOptionRow(
    item: ContactInfo,
    onClick: () -> Unit
) {
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "scale"
    )
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .clickable {
                isPressed = true
                onClick()
            },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(
                        colors = getGradientColors(item.action)
                    )
                )
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(Color.White.copy(alpha = 0.25f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Medium,
                            color = Color.White
                        )
                    )
                    Text(
                        text = item.subtitle,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    )
                }
            }
        }
    }
    
    LaunchedEffect(isPressed) {
        if (isPressed) {
            delay(100)
            isPressed = false
        }
    }
}

// Simple Contact Card (No Animation) - Keep for reference
@Composable
private fun SimpleContactCard(
    item: ContactInfo,
    onClick: () -> Unit
) {
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy), label = "scale"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .clickable {
                isPressed = true
                onClick()
            },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.Transparent
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(
                        colors = getGradientColors(item.action)
                    )
                )
        ) {
            Row(
                modifier = Modifier.padding(20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .background(
                            Color.White.copy(alpha = 0.3f),
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }
                Spacer(modifier = Modifier.width(20.dp))
                Column {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontWeight = FontWeight.Medium,
                            color = Color.White
                        )
                    )
                    Text(
                        text = item.subtitle,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        ),
                        fontSize = 16.sp
                    )
                }
            }
        }
    }

    LaunchedEffect(isPressed) {
        if (isPressed) {
            delay(100)
            isPressed = false
        }
    }
}

// Page Card (No animation delay)
@Composable
private fun PageCard(
    item: PageInfo,
    onClick: () -> Unit
) {
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy), label = "scale"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .clickable {
                isPressed = true
                onClick()
            },
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(
                        getPageIconBackground(pageItems.indexOf(item)),
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = item.icon,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = item.title,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    }

    LaunchedEffect(isPressed) {
        if (isPressed) {
            delay(100)
            isPressed = false
        }
    }
}

// Animated Section Header
@Composable
fun AnimatedSectionHeader(title: String) {
    var isVisible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(800)
        isVisible = true
    }

    AnimatedVisibility(
        visible = isVisible,
        enter = slideInVertically(
            initialOffsetY = { it },
            animationSpec = tween(600, easing = EaseOutCubic)
        ) + fadeIn(animationSpec = tween(600))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    }
}

// Animated Section Divider
@Composable
fun AnimatedSectionDivider() {
    var isVisible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(600)
        isVisible = true
    }

    AnimatedVisibility(
        visible = isVisible,
        enter = scaleIn(animationSpec = tween(600, easing = EaseOutBounce)) +
                fadeIn(animationSpec = tween(600))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.MoreHoriz,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}

// Helper functions for colors
private fun getGradientColors(action: ContactAction): List<Color> {
    return when (action) {
        ContactAction.CALL -> listOf(Color(0xFF667eea), Color(0xFF764ba2))
        ContactAction.WHATSAPP -> listOf(Color(0xFF25D366), Color(0xFF128C7E))
        ContactAction.EMAIL -> listOf(Color(0xFFf093fb), Color(0xFFf5576c))
        ContactAction.WHATSAPP_CHANNEL -> listOf(Color(0xFF25D366), Color(0xFF128C7E))
        ContactAction.TELEGRAM -> listOf(Color(0xFF0088cc), Color(0xFF229ED9))
    }
}

private fun getPageIconBackground(index: Int): Brush {
    val colors = listOf(
        listOf(Color(0xFF667eea), Color(0xFF764ba2)),
        listOf(Color(0xFFf093fb), Color(0xFFf5576c)),
        listOf(Color(0xFF4facfe), Color(0xFF00f2fe)),
        listOf(Color(0xFF43e97b), Color(0xFF38f9d7)),
        listOf(Color(0xFFfa709a), Color(0xFFfee140))
    )
    return Brush.linearGradient(colors[index % colors.size])
}

// Data classes and enums remain the same
private enum class ContactAction { CALL, WHATSAPP, EMAIL, WHATSAPP_CHANNEL, TELEGRAM }
private data class ContactInfo(val icon: ImageVector, val title: String, val subtitle: String, val action: ContactAction)
private data class PageInfo(val icon: ImageVector, val title: String, val url: String)

private val channelItems = listOf(
    ContactInfo(Icons.Default.Campaign, "WhatsApp Channel", "Join for updates & tips", ContactAction.WHATSAPP_CHANNEL),
    ContactInfo(Icons.Default.Send, "Telegram Channel", "Join for updates & tips", ContactAction.TELEGRAM)
)

// Store actual URLs separately
private val channelUrls = mapOf(
    ContactAction.WHATSAPP_CHANNEL to "https://whatsapp.com/channel/0029VbB2Ul289inhpZ69VC29",
    ContactAction.TELEGRAM to "https://t.me/Chatspromobulksend"
)

private val contactItems = listOf(
    ContactInfo(Icons.Default.Phone, "24x7 Customer Service", "+91 7400 212 304", ContactAction.CALL),
    ContactInfo(Icons.Default.Whatsapp, "WhatsApp Support", "+91 7400 212 304", ContactAction.WHATSAPP),
    ContactInfo(Icons.Default.Email, "Email Support", "wealthmize@gmail.com", ContactAction.EMAIL)
)

private val pageItems = listOf(
    PageInfo(Icons.Default.Info, "About Us", "https://chatspromo.blogspot.com/p/about-chatspromo-bulk-sender.html"),
    PageInfo(Icons.AutoMirrored.Filled.Article, "Terms & Conditions", "https://chatspromo.blogspot.com/p/terms-conditions.html"),
    PageInfo(Icons.Default.PrivacyTip, "Privacy Policy", "https://chatspromo.blogspot.com/p/privacy-policy-chatspromo-bulk-sender.html"),
    PageInfo(Icons.Default.CurrencyRupee, "Refund & Cancellation", "https://chatspromo.blogspot.com/p/cancellation-policy.html"),
    PageInfo(Icons.Default.Groups, "Join Our Community", "https://chatspromo.blogspot.com")
)

// Click handler
private fun handleContactClick(context: Context, action: ContactAction, data: String) {
    try {
        val intent = when (action) {
            ContactAction.CALL -> Intent(Intent.ACTION_DIAL, Uri.parse("tel:$data"))
            ContactAction.WHATSAPP -> {
                val formattedNumber = data.replace(" ", "").replace("+", "")
                Intent(Intent.ACTION_VIEW, Uri.parse("https://api.whatsapp.com/send?phone=$formattedNumber"))
            }
            ContactAction.EMAIL -> Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$data"))
            ContactAction.WHATSAPP_CHANNEL -> Intent(Intent.ACTION_VIEW, Uri.parse(channelUrls[action] ?: ""))
            ContactAction.TELEGRAM -> Intent(Intent.ACTION_VIEW, Uri.parse(channelUrls[action] ?: ""))
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        Toast.makeText(context, "Could not perform action", Toast.LENGTH_SHORT).show()
    }
}

// Live Chat Support Button
@Composable
fun LiveChatButton(onClick: () -> Unit) {
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "scale"
    )
    
    // Pulsing animation
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.7f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .clickable {
                isPressed = true
                onClick()
            },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            Color(0xFF667eea).copy(alpha = pulseAlpha),
                            Color(0xFF764ba2).copy(alpha = pulseAlpha)
                        )
                    )
                )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .background(Color.White.copy(alpha = 0.3f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Chat,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        text = "💬 Live Chat Support",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    )
                    Text(
                        text = "Chat with our support team",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = Color.White.copy(alpha = 0.9f)
                        )
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                Icon(
                    imageVector = Icons.Default.ArrowForward,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }

    LaunchedEffect(isPressed) {
        if (isPressed) {
            delay(100)
            isPressed = false
        }
    }
}

@Preview(showBackground = true)
@Composable
fun SupportScreenPreview() {
    BulksendTestTheme {
        SupportScreen()
    }
}


