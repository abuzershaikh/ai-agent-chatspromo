package com.message.bulksend.auth

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.google.firebase.auth.FirebaseAuth
import com.message.bulksend.data.UserData
import com.message.bulksend.data.UserPreferences
import kotlinx.coroutines.launch

class UserPreferencesActivity : ComponentActivity() {

    private val auth = FirebaseAuth.getInstance()
    private val userManager by lazy { UserManager(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            UserPreferencesScreen(
                onSavePreferences = { preferences ->
                    saveUserPreferences(preferences)
                },
                onBack = { finish() }
            )
        }
    }

    private fun saveUserPreferences(preferences: UserPreferences) {
        lifecycleScope.launch {
            auth.currentUser?.email?.let { email ->
                val success = userManager.updateUserPreferences(email, preferences)
                if (success) {
                    // Show success message
                    runOnUiThread {
                        // You can show a toast or snackbar here
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserPreferencesScreen(
    onSavePreferences: (UserPreferences) -> Unit,
    onBack: () -> Unit
) {
    var userData by remember { mutableStateOf<UserData?>(null) }
    var preferences by remember { mutableStateOf(UserPreferences()) }
    var isLoading by remember { mutableStateOf(true) }

    val auth = FirebaseAuth.getInstance()
    val userManager = UserManager(androidx.compose.ui.platform.LocalContext.current)

    // Load user data
    LaunchedEffect(Unit) {
        auth.currentUser?.email?.let { email ->
            userData = userManager.getUserData(email)
            userData?.let {
                preferences = it.preferences
            }
        }
        isLoading = false
    }

    val gradientColors = listOf(
        Color(0xFF1534DE),
        Color(0xFF4611AB),
        Color(0xFFB90FD3)
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(gradientColors)
            )
    ) {
        // Top App Bar
        TopAppBar(
            title = {
                Text(
                    "Preferences",
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.Default.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent
            ),
            actions = {
                TextButton(
                    onClick = { onSavePreferences(preferences) }
                ) {
                    Text("Save", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        )

        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = Color.White)
            }
        } else {
            PreferencesContent(
                preferences = preferences,
                onPreferencesChange = { preferences = it }
            )
        }
    }
}

@Composable
fun PreferencesContent(
    preferences: UserPreferences,
    onPreferencesChange: (UserPreferences) -> Unit
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Language Preference
        PreferenceCard(
            title = "Language",
            icon = Icons.Default.Language
        ) {
            val languages = listOf("en" to "English", "hi" to "Hindi", "ur" to "Urdu")
            languages.forEach { (code, name) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = preferences.language == code,
                        onClick = {
                            onPreferencesChange(preferences.copy(language = code))
                        },
                        colors = RadioButtonDefaults.colors(
                            selectedColor = Color.White,
                            unselectedColor = Color.White.copy(alpha = 0.6f)
                        )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = name,
                        color = Color.White,
                        fontSize = 16.sp
                    )
                }
            }
        }

        // Appearance Preferences
        PreferenceCard(
            title = "Appearance",
            icon = Icons.Default.Palette
        ) {
            PreferenceSwitch(
                title = "Dark Mode",
                subtitle = "Use dark theme",
                checked = preferences.darkMode,
                onCheckedChange = {
                    onPreferencesChange(preferences.copy(darkMode = it))
                }
            )
        }

        // Notification Preferences
        PreferenceCard(
            title = "Notifications",
            icon = Icons.Default.Notifications
        ) {
            PreferenceSwitch(
                title = "Push Notifications",
                subtitle = "Receive app notifications",
                checked = preferences.notificationsEnabled,
                onCheckedChange = {
                    onPreferencesChange(preferences.copy(notificationsEnabled = it))
                }
            )

            Spacer(modifier = Modifier.height(8.dp))

            PreferenceSwitch(
                title = "Email Notifications",
                subtitle = "Receive email alerts",
                checked = preferences.emailNotifications,
                onCheckedChange = {
                    onPreferencesChange(preferences.copy(emailNotifications = it))
                }
            )
        }

        // Data & Storage Preferences
        PreferenceCard(
            title = "Data & Storage",
            icon = Icons.Default.Storage
        ) {
            PreferenceSwitch(
                title = "Auto Backup",
                subtitle = "Automatically backup data",
                checked = preferences.autoBackup,
                onCheckedChange = {
                    onPreferencesChange(preferences.copy(autoBackup = it))
                }
            )

            Spacer(modifier = Modifier.height(8.dp))

            PreferenceSwitch(
                title = "Data Usage Optimization",
                subtitle = "Reduce data consumption",
                checked = preferences.dataUsageOptimization,
                onCheckedChange = {
                    onPreferencesChange(preferences.copy(dataUsageOptimization = it))
                }
            )
        }
    }
}

@Composable
fun PreferenceCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color.White.copy(alpha = 0.1f)
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 12.dp)
            ) {
                Icon(
                    icon,
                    contentDescription = title,
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = title,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            content()
        }
    }
}

@Composable
fun PreferenceSwitch(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White
            )
            Text(
                text = subtitle,
                fontSize = 14.sp,
                color = Color.White.copy(alpha = 0.7f)
            )
        }

        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = Color.White.copy(alpha = 0.3f),
                uncheckedThumbColor = Color.White.copy(alpha = 0.6f),
                uncheckedTrackColor = Color.White.copy(alpha = 0.2f)
            )
        )
    }
}