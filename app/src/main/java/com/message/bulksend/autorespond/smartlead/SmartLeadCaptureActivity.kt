package com.message.bulksend.autorespond.smartlead

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import kotlinx.coroutines.launch
import com.message.bulksend.ui.theme.BulksendTestTheme

class SmartLeadCaptureActivity : ComponentActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        setContent {
            BulksendTestTheme {
                SmartLeadCaptureScreen(
                    activity = this,
                    onBackPressed = { finish() }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmartLeadCaptureScreen(
    activity: SmartLeadCaptureActivity,
    onBackPressed: () -> Unit
) {
    val backgroundBrush = Brush.verticalGradient(
        colors = listOf(Color(0xFF0f0c29), Color(0xFF302b63), Color(0xFF24243e))
    )
    
    val context = androidx.compose.ui.platform.LocalContext.current
    val leadCaptureManager = remember { LeadCaptureManager(context) }
    
    var isEnabled by remember { mutableStateOf(false) }
    var nameQuestion by remember { mutableStateOf("What's your name?") }
    var mobileQuestion by remember { mutableStateOf("Please share your mobile number") }
    var customFields by remember { mutableStateOf(listOf<CustomField>()) }
    var welcomeMessage by remember { mutableStateOf("Hi! I'd like to know more about you to provide better assistance.") }
    var completionMessage by remember { mutableStateOf("Thank you! Your information has been saved. How can I help you today?") }
    
    // Load settings on startup
    LaunchedEffect(Unit) {
        try {
            val settings = leadCaptureManager.getSettings()
            isEnabled = settings.isEnabled
            nameQuestion = settings.nameQuestion
            mobileQuestion = settings.mobileQuestion
            welcomeMessage = settings.welcomeMessage
            completionMessage = settings.completionMessage
            
            // Parse custom fields
            if (settings.customFields.isNotBlank()) {
                val gson = com.google.gson.Gson()
                val type = object : com.google.gson.reflect.TypeToken<List<CustomFieldConfig>>() {}.type
                customFields = gson.fromJson(settings.customFields, type) ?: emptyList()
            }
        } catch (e: Exception) {
            Log.e("SmartLeadCapture", "Error loading settings: ${e.message}")
        }
    }
    
    // Save settings when changed
    fun saveSettings() {
        activity.lifecycleScope.launch {
            try {
                val gson = com.google.gson.Gson()
                val customFieldsJson = gson.toJson(customFields.map { CustomFieldConfig(it.label, it.question) })
                
                val settings = LeadCaptureSettingsEntity(
                    isEnabled = isEnabled,
                    nameQuestion = nameQuestion,
                    mobileQuestion = mobileQuestion,
                    customFields = customFieldsJson,
                    welcomeMessage = welcomeMessage,
                    completionMessage = completionMessage
                )
                
                leadCaptureManager.updateSettings(settings)
                Log.d("SmartLeadCapture", "Settings saved successfully")
            } catch (e: Exception) {
                Log.e("SmartLeadCapture", "Error saving settings: ${e.message}")
            }
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text("Smart Auto Lead Capture", color = Color(0xFFE4405F), fontWeight = FontWeight.Bold)
                },
                navigationIcon = {
                    IconButton(onClick = onBackPressed) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color(0xFFE4405F))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF1a1a2e))
            )
        },
        containerColor = Color.Transparent
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(backgroundBrush)
                .padding(padding)
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Enable/Disable Card
                item {
                    EnableCard(
                        isEnabled = isEnabled,
                        onToggle = { 
                            isEnabled = it
                            saveSettings()
                        }
                    )
                }
                
                // Configuration Card
                item {
                    ConfigurationCard(
                        nameQuestion = nameQuestion,
                        mobileQuestion = mobileQuestion,
                        welcomeMessage = welcomeMessage,
                        completionMessage = completionMessage,
                        onNameQuestionChange = { 
                            nameQuestion = it
                            saveSettings()
                        },
                        onMobileQuestionChange = { 
                            mobileQuestion = it
                            saveSettings()
                        },
                        onWelcomeMessageChange = {
                            welcomeMessage = it
                            saveSettings()
                        },
                        onCompletionMessageChange = {
                            completionMessage = it
                            saveSettings()
                        }
                    )
                }
                
                // Custom Fields Card
                item {
                    CustomFieldsCard(
                        customFields = customFields,
                        onAddField = { 
                            customFields = customFields + CustomField("", "")
                            saveSettings()
                        },
                        onUpdateField = { index, field ->
                            customFields = customFields.toMutableList().apply {
                                set(index, field)
                            }
                            saveSettings()
                        },
                        onRemoveField = { index ->
                            customFields = customFields.toMutableList().apply {
                                removeAt(index)
                            }
                            saveSettings()
                        }
                    )
                }
                
                // How It Works Card
                item {
                    HowItWorksCard()
                }
                
                // Recent Captures Card
                item {
                    RecentCapturesCard()
                }
            }
        }
    }
}

@Composable
fun EnableCard(
    isEnabled: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1a1a2e)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        Icons.Default.Person,
                        contentDescription = null,
                        tint = Color(0xFFE4405F),
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        "Smart Lead Capture",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                
                Switch(
                    checked = isEnabled,
                    onCheckedChange = onToggle,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = Color(0xFFE4405F),
                        uncheckedThumbColor = Color(0xFF64748B),
                        uncheckedTrackColor = Color(0xFF374151)
                    )
                )
            }
            
            Text(
                if (isEnabled) 
                    "✓ Smart lead capture is active. New Instagram users will be asked sequential questions to capture their information."
                else 
                    "Enable to automatically capture lead information from new Instagram users through sequential questions.",
                color = if (isEnabled) Color(0xFF10B981) else Color(0xFF94A3B8),
                fontSize = 14.sp,
                lineHeight = 20.sp
            )
        }
    }
}

@Composable
fun ConfigurationCard(
    nameQuestion: String,
    mobileQuestion: String,
    welcomeMessage: String,
    completionMessage: String,
    onNameQuestionChange: (String) -> Unit,
    onMobileQuestionChange: (String) -> Unit,
    onWelcomeMessageChange: (String) -> Unit,
    onCompletionMessageChange: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1a1a2e)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    Icons.Default.Settings,
                    contentDescription = null,
                    tint = Color(0xFFE4405F),
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    "Message Configuration",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            
            // Welcome Message
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "Welcome Message",
                    color = Color(0xFF94A3B8),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
                OutlinedTextField(
                    value = welcomeMessage,
                    onValueChange = onWelcomeMessageChange,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFFE4405F),
                        unfocusedBorderColor = Color(0xFF374151),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    shape = RoundedCornerShape(8.dp),
                    maxLines = 2
                )
            }
            
            // Name Question
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "Name Question",
                    color = Color(0xFF94A3B8),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
                OutlinedTextField(
                    value = nameQuestion,
                    onValueChange = onNameQuestionChange,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFFE4405F),
                        unfocusedBorderColor = Color(0xFF374151),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    shape = RoundedCornerShape(8.dp)
                )
            }
            
            // Mobile Question
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "Mobile Number Question",
                    color = Color(0xFF94A3B8),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
                OutlinedTextField(
                    value = mobileQuestion,
                    onValueChange = onMobileQuestionChange,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFFE4405F),
                        unfocusedBorderColor = Color(0xFF374151),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    shape = RoundedCornerShape(8.dp)
                )
            }
            
            // Completion Message
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "Completion Message",
                    color = Color(0xFF94A3B8),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
                OutlinedTextField(
                    value = completionMessage,
                    onValueChange = onCompletionMessageChange,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFFE4405F),
                        unfocusedBorderColor = Color(0xFF374151),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    shape = RoundedCornerShape(8.dp),
                    maxLines = 2
                )
            }
        }
    }
}

@Composable
fun CustomFieldsCard(
    customFields: List<CustomField>,
    onAddField: () -> Unit,
    onUpdateField: (Int, CustomField) -> Unit,
    onRemoveField: (Int) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1a1a2e)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = null,
                        tint = Color(0xFFE4405F),
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        "Custom Fields",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                
                IconButton(
                    onClick = onAddField,
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = Color(0xFFE4405F)
                    )
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = "Add Field",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            
            if (customFields.isEmpty()) {
                Text(
                    "No custom fields added yet. Click + to add custom questions.",
                    color = Color(0xFF64748B),
                    fontSize = 14.sp,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            } else {
                customFields.forEachIndexed { index, field ->
                    CustomFieldItem(
                        field = field,
                        onUpdate = { onUpdateField(index, it) },
                        onRemove = { onRemoveField(index) }
                    )
                }
            }
        }
    }
}

@Composable
fun CustomFieldItem(
    field: CustomField,
    onUpdate: (CustomField) -> Unit,
    onRemove: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0f0c29)),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Custom Field",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
                IconButton(
                    onClick = onRemove,
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = Color(0xFFEF4444)
                    ),
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Remove",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            
            OutlinedTextField(
                value = field.label,
                onValueChange = { onUpdate(field.copy(label = it)) },
                label = { Text("Field Label", color = Color(0xFF94A3B8)) },
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFFE4405F),
                    unfocusedBorderColor = Color(0xFF374151),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                shape = RoundedCornerShape(8.dp)
            )
            
            OutlinedTextField(
                value = field.question,
                onValueChange = { onUpdate(field.copy(question = it)) },
                label = { Text("Question to Ask", color = Color(0xFF94A3B8)) },
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFFE4405F),
                    unfocusedBorderColor = Color(0xFF374151),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                shape = RoundedCornerShape(8.dp)
            )
        }
    }
}

@Composable
fun HowItWorksCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1a1a2e)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    Icons.Default.Info,
                    contentDescription = null,
                    tint = Color(0xFFE4405F),
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    "How It Works",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StepItem(
                    number = "1",
                    title = "New User Detection",
                    description = "When a new Instagram user sends their first message"
                )
                StepItem(
                    number = "2",
                    title = "Sequential Questions",
                    description = "System asks name, mobile number, and custom fields one by one"
                )
                StepItem(
                    number = "3",
                    title = "Lead Capture",
                    description = "User responses are automatically saved as lead information"
                )
                StepItem(
                    number = "4",
                    title = "Normal Auto Reply",
                    description = "After capture, regular auto-reply system takes over"
                )
            }
        }
    }
}

@Composable
fun StepItem(
    number: String,
    title: String,
    description: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .background(Color(0xFFE4405F), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                number,
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
        
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                title,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                description,
                color = Color(0xFF94A3B8),
                fontSize = 13.sp,
                lineHeight = 18.sp
            )
        }
    }
}

@Composable
fun RecentCapturesCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1a1a2e)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    Icons.Default.List,
                    contentDescription = null,
                    tint = Color(0xFFE4405F),
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    "Recent Captures (0)",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            
            Text(
                "No leads captured yet. Enable the feature and start receiving Instagram messages to see captured leads here.",
                color = Color(0xFF64748B),
                fontSize = 14.sp,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }
    }
}

data class CustomField(
    val label: String,
    val question: String
)