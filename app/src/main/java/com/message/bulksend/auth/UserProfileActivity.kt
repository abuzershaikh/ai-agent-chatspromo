package com.message.bulksend.auth

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.*
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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.google.firebase.auth.FirebaseAuth
import com.message.bulksend.data.UserData
import com.message.bulksend.utils.DeviceUtils
import com.message.bulksend.utils.PreferencesSync
import com.message.bulksend.userdetails.UserDetailsActivity
import com.message.bulksend.userdetails.UserDetailsPreferences
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class UserProfileActivity : ComponentActivity() {

    private val auth = FirebaseAuth.getInstance()
    private val userManager by lazy { UserManager(this) }
    private val emailService by lazy { EmailService(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Enable edge-to-edge
        enableEdgeToEdge()

        setContent {
            UserProfileScreen(
                onLogout = { handleLogout() },
                onSendSupportEmail = { issue ->
                    auth.currentUser?.email?.let { email ->
                        emailService.sendSupportEmail(email, issue)
                    }
                }
            )
        }
    }

    private fun handleLogout() {
        lifecycleScope.launch {
            PreferencesSync.stopRealtimeSubscriptionSync()
            auth.currentUser?.email?.let { email ->
                userManager.logoutUser(email)
            }

            // Navigate back to auth screen
            startActivity(Intent(this@UserProfileActivity, AuthActivity::class.java))
            finish()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserProfileScreen(
    onLogout: () -> Unit,
    onSendSupportEmail: (String) -> Unit
) {
    val context = LocalContext.current
    var userData by remember { mutableStateOf<UserData?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var showLogoutDialog by remember { mutableStateOf(false) }
    var isAnimated by remember { mutableStateOf(false) }

    val auth = FirebaseAuth.getInstance()
    val userManager = UserManager(context)
    val userDetailsPrefs = remember { UserDetailsPreferences(context) }

    // Load user data
    LaunchedEffect(Unit) {
        val currentUser = auth.currentUser
        val email = currentUser?.email ?: userDetailsPrefs.getEmail().orEmpty()
        val detailsName = userDetailsPrefs.getFullName().orEmpty().trim()
        val authName = currentUser?.displayName?.trim().orEmpty()
        val fallbackName = when {
            detailsName.isNotBlank() -> detailsName
            authName.isNotBlank() -> authName
            else -> email.substringBefore("@")
                .replaceFirstChar { ch -> if (ch.isLowerCase()) ch.titlecase() else ch.toString() }
                .ifBlank { "User" }
        }

        val fetchedUser = if (email.isNotBlank()) userManager.getUserData(email) else null

        userData = when {
            fetchedUser != null && fetchedUser.displayName.isBlank() && fallbackName.isNotBlank() ->
                fetchedUser.copy(displayName = fallbackName)
            fetchedUser != null -> fetchedUser
            email.isNotBlank() -> UserData(
                email = email,
                userId = currentUser?.uid ?: userDetailsPrefs.getUserId().orEmpty(),
                displayName = fallbackName,
                uniqueIdentifier = DeviceUtils.generateUniqueIdentifier(
                    email = email,
                    deviceId = DeviceUtils.getDeviceId(context)
                )
            )
            else -> null
        }

        isLoading = false
        delay(300)
        isAnimated = true
    }

    val gradientColors = listOf(
        Color(0xFF6366F1),
        Color(0xFF8B5CF6),
        Color(0xFFEC4899)
    )

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Profile",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontFamily = FontFamily.SansSerif
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                ),
                actions = {
                    IconButton(
                        onClick = { showLogoutDialog = true },
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.15f))
                    ) {
                        Icon(
                            Icons.Default.Logout,
                            contentDescription = "Logout",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.verticalGradient(
                        colors = gradientColors,
                        startY = 0f,
                        endY = Float.POSITIVE_INFINITY
                    )
                )
                .padding(paddingValues)
        ) {
            // Background decoration
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.1f),
                                Color.Transparent
                            ),
                            center = Offset(300f, 200f),
                            radius = 500f
                        )
                    )
            )

            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(
                            color = Color.White,
                            strokeWidth = 3.dp,
                            modifier = Modifier.size(50.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Loading Profile...",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            } else {
                userData?.let { user ->
                    AnimatedVisibility(
                        visible = isAnimated,
                        enter = fadeIn(animationSpec = tween(1000)) + slideInVertically(
                            initialOffsetY = { it / 4 },
                            animationSpec = tween(1000, easing = FastOutSlowInEasing)
                        ),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        ProfileContent(
                            userData = user,
                            onSendSupportEmail = onSendSupportEmail
                        )
                    }
                }
                if (userData == null) {
                    EmptyProfileState(
                        onGoToDetails = {
                            context.startActivity(Intent(context, UserDetailsActivity::class.java))
                        }
                    )
                }
            }
        }
    }

    // Logout Confirmation Dialog
    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { 
                Text(
                    "Logout",
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                ) 
            },
            text = { 
                Text(
                    "Are you sure you want to logout?",
                    fontSize = 16.sp
                ) 
            },
            confirmButton = {
                Button(
                    onClick = {
                        showLogoutDialog = false
                        onLogout()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text("Yes")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showLogoutDialog = false }) {
                    Text("Cancel")
                }
            },
            shape = RoundedCornerShape(16.dp)
        )
    }
}

@Composable
private fun EmptyProfileState(onGoToDetails: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.18f))
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .widthIn(max = 360.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Profile data not available yet",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "Please complete your details once, then reopen profile.",
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center
                )
                Button(
                    onClick = onGoToDetails,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6366F1))
                ) {
                    Text("Complete Details")
                }
            }
        }
    }
}

@Composable
fun ProfileContent(
    userData: UserData,
    onSendSupportEmail: (String) -> Unit
) {
    val scrollState = rememberScrollState()
    val dateFormat = SimpleDateFormat("MMM dd, yyyy 'at' HH:mm", Locale.getDefault())
    val context = LocalContext.current
    var isPremiumAnimated by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(500)
        isPremiumAnimated = true
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Profile Header
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp)
                .shadow(
                    elevation = 12.dp,
                    shape = RoundedCornerShape(24.dp),
                    spotColor = Color.Black.copy(alpha = 0.2f)
                ),
            colors = CardDefaults.cardColors(
                containerColor = Color.White.copy(alpha = 0.15f)
            ),
            shape = RoundedCornerShape(24.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Profile Avatar with animation
                Box(
                    modifier = Modifier
                        .size(110.dp)
                        .clip(CircleShape)
                        .background(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    Color.White.copy(alpha = 0.4f),
                                    Color.White.copy(alpha = 0.1f)
                                )
                            )
                        )
                        .shadow(
                            elevation = 8.dp,
                            shape = CircleShape,
                            spotColor = Color.Black.copy(alpha = 0.3f)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Person,
                        contentDescription = "Profile",
                        modifier = Modifier.size(60.dp),
                        tint = Color.White
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = userData.displayName.ifEmpty { "User" },
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    fontFamily = FontFamily.SansSerif
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = userData.email,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White.copy(alpha = 0.9f)
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(30.dp))
                        .background(Color.White.copy(alpha = 0.2f))
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Icon(
                        Icons.Default.Fingerprint,
                        contentDescription = "ID",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "ID: ${userData.uniqueIdentifier}",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Normal,
                        color = Color.White
                    )
                }
            }
        }

        // Subscription Information
        var subscriptionInfo by remember { mutableStateOf<Map<String, Any?>>(emptyMap()) }
        val userManager = UserManager(context)

        LaunchedEffect(userData.email) {
            subscriptionInfo = userManager.getSubscriptionInfo(userData.email)
        }

        val currentType = subscriptionInfo["type"] as? String ?: "free"
        val currentExpired = subscriptionInfo["isExpired"] as? Boolean ?: false
        val isPremiumActive = currentType == "premium" && !currentExpired
        
        ProfileSection(
            title = "Subscription Plan",
            isSpecial = true,
            items = buildList {
                val type = subscriptionInfo["type"] as? String ?: "free"
                val isExpired = subscriptionInfo["isExpired"] as? Boolean ?: false
                val planType = subscriptionInfo["planType"] as? String ?: ""

                // Show plan type with monthly/lifetime info
                val planDisplay = when {
                    isExpired -> "Plan Ended"
                    type == "premium" && planType == "monthly" -> "Premium (Monthly)"
                    type == "premium" && planType == "yearly" -> "Premium (Yearly)"
                    type == "premium" && planType == "lifetime" -> "Premium (Lifetime)"
                    type == "premium" -> "Premium"
                    else -> "Free"
                }

                add(ProfileItem(
                    Icons.Default.Star,
                    "Plan Type",
                    planDisplay,
                    isPremiumActive
                ))

                if (type == "premium") {
                    subscriptionInfo["endDate"]?.let { endDate ->
                        val dateStr = dateFormat.format((endDate as com.google.firebase.Timestamp).toDate())
                        add(ProfileItem(
                            Icons.Default.Schedule,
                            if (isExpired) "Plan Ended On" else "Plan Expires",
                            if (isExpired) "Plan Ended • $dateStr" else dateStr,
                            isPremiumActive
                        ))
                    }
                }
            }
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Account Information
        ProfileSection(
            title = "Account Information",
            items = listOf(
                ProfileItem(
                    Icons.Default.DateRange, 
                    "First Signup", 
                    dateFormat.format(userData.firstSignupDate.toDate()),
                    false
                ),
                ProfileItem(
                    Icons.Default.Schedule, 
                    "Last Login", 
                    dateFormat.format(userData.lastLoginDate.toDate()),
                    false
                ),
                ProfileItem(
                    Icons.Default.CheckCircle, 
                    "Status", 
                    if (userData.isActive) "Active" else "Inactive",
                    false
                )
            )
        )

        Spacer(modifier = Modifier.height(30.dp))

        // Settings Button
        ModernButton(
            onClick = {
                context.startActivity(Intent(context, UserPreferencesActivity::class.java))
            },
            icon = Icons.Default.Settings,
            text = "Settings",
            gradient = Brush.horizontalGradient(
                colors = listOf(
                    Color(0xFF6366F1),
                    Color(0xFF8B5CF6)
                )
            )
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Accessibility Service Button
        ModernButton(
            onClick = {
                val intent = Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)
                context.startActivity(intent)
            },
            icon = Icons.Default.Accessibility,
            text = "Accessibility Service",
            gradient = Brush.horizontalGradient(
                colors = listOf(
                    Color(0xFF10B981),
                    Color(0xFF059669)
                )
            )
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Support Button
        ModernButton(
            onClick = { onSendSupportEmail("I need help with my account") },
            icon = Icons.Default.Support,
            text = "Contact Support",
            gradient = Brush.horizontalGradient(
                colors = listOf(
                    Color(0xFF8B5CF6),
                    Color(0xFFEC4899)
                )
            )
        )

        Spacer(modifier = Modifier.height(30.dp))
    }
}

@Composable
fun ModernButton(
    onClick: () -> Unit,
    icon: ImageVector,
    text: String,
    gradient: Brush
) {
    var isPressed by remember { mutableStateOf(false) }
    
    Button(
        onClick = { 
            isPressed = true
            onClick()
        },
        modifier = Modifier
            .fillMaxWidth()
            .height(60.dp)
            .scale(if (isPressed) 0.98f else 1f),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Transparent
        ),
        shape = RoundedCornerShape(30.dp),
        contentPadding = PaddingValues(0.dp),
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = 8.dp,
            pressedElevation = 12.dp
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(gradient, RoundedCornerShape(30.dp))
                .padding(horizontal = 20.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    icon,
                    contentDescription = text,
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = text,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    fontFamily = FontFamily.SansSerif
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

@Composable
fun ProfileSection(
    title: String,
    isSpecial: Boolean = false,
    items: List<ProfileItem>
) {
    var isVisible by remember { mutableStateOf(false) }
    
    LaunchedEffect(Unit) {
        delay(300)
        isVisible = true
    }
    
    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(animationSpec = tween(800)) + slideInVertically(
            initialOffsetY = { it / 3 },
            animationSpec = tween(800, easing = FastOutSlowInEasing)
        )
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (isSpecial) {
                        Modifier.shadow(
                            elevation = 10.dp,
                            shape = RoundedCornerShape(20.dp),
                            spotColor = Color.Black.copy(alpha = 0.3f)
                        )
                    } else {
                        Modifier
                    }
                ),
            colors = CardDefaults.cardColors(
                containerColor = if (isSpecial) {
                    Color.White.copy(alpha = 0.2f)
                } else {
                    Color.White.copy(alpha = 0.15f)
                }
            ),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = title,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontFamily = FontFamily.SansSerif
                    )
                    
                    if (isSpecial) {
                        var rotation by remember { mutableStateOf(0f) }
                        LaunchedEffect(Unit) {
                            rotation = 360f
                        }
                        
                        Icon(
                            Icons.Default.Star,
                            contentDescription = "Premium",
                            tint = Color(0xFFFFD700),
                            modifier = Modifier
                                .size(24.dp)
                                .rotate(rotation)
                                .animateContentSize(
                                    animationSpec = tween(
                                        durationMillis = 1000,
                                        easing = FastOutSlowInEasing
                                    )
                                )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                items.forEachIndexed { index, item ->
                    ProfileItemRow(item, isSpecial)
                    if (index < items.lastIndex) {
                        Divider(
                            color = Color.White.copy(alpha = 0.2f),
                            modifier = Modifier
                                .padding(vertical = 12.dp)
                                .padding(start = 36.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ProfileItemRow(item: ProfileItem, isSpecial: Boolean = false) {
    var isHovered by remember { mutableStateOf(false) }
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                isHovered = true
            }
            .background(
                if (isHovered) Color.White.copy(alpha = 0.1f) else Color.Transparent,
                RoundedCornerShape(12.dp)
            )
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .then(
                    if (isSpecial) {
                        Modifier.background(
                            brush = Brush.horizontalGradient(
                                colors = listOf(
                                    Color(0xFFFFD700),
                                    Color(0xFFFFC107)
                                )
                            )
                        )
                    } else {
                        Modifier.background(Color.White.copy(alpha = 0.2f))
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                item.icon,
                contentDescription = item.label,
                tint = Color.White,
                modifier = Modifier.size(22.dp)
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.label,
                fontSize = 14.sp,
                fontWeight = FontWeight.Normal,
                color = Color.White.copy(alpha = 0.8f)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = item.value,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                fontFamily = FontFamily.SansSerif
            )
        }
    }
    
    LaunchedEffect(isHovered) {
        if (isHovered) {
            delay(100)
            isHovered = false
        }
    }
}

data class ProfileItem(
    val icon: ImageVector,
    val label: String,
    val value: String,
    val isPremium: Boolean = false
)
