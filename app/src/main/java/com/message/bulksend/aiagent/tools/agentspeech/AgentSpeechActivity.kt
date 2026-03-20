package com.message.bulksend.aiagent.tools.agentspeech

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.message.bulksend.ui.theme.BulkSendTheme
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class AgentSpeechActivity : ComponentActivity() {
    private lateinit var speechManager: AgentSpeechManager
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        speechManager = AgentSpeechManager.getInstance(this)
        
        // Initialize settings
        lifecycleScope.launch {
            speechManager.initializeSettings()
        }
        
        setContent {
            BulkSendTheme {
                AgentSpeechScreen(speechManager)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgentSpeechScreen(speechManager: AgentSpeechManager) {
    val settings by speechManager.getSettings().collectAsState(initial = null)
    val pendingCount by speechManager.getPendingCount().collectAsState(initial = 0)
    val history by speechManager.getRecentHistory().collectAsState(initial = emptyList())
    
    val scope = rememberCoroutineScope()
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Agent Speech") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Enable/Disable Switch
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Send Voice Replies",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (settings?.isEnabled == true) "Voice replies enabled" else "Voice replies disabled",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                            )
                        }
                        
                        Switch(
                            checked = settings?.isEnabled ?: false,
                            onCheckedChange = { enabled ->
                                scope.launch {
                                    speechManager.setEnabled(enabled)
                                }
                            }
                        )
                    }
                }
            }
            
            // Language Selection
            item {
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(bottom = 12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Language,
                                contentDescription = "Language",
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Voice Language",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        
                        // Language options
                        LanguageOption(
                            title = "English (Indian Accent)",
                            subtitle = "EN-IN - Best for Indian users",
                            code = "EN-IN",
                            isSelected = settings?.language == "EN-IN",
                            onSelect = {
                                scope.launch {
                                    speechManager.setLanguage("EN-IN")
                                }
                            }
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        LanguageOption(
                            title = "English (American)",
                            subtitle = "en - Standard American English",
                            code = "en",
                            isSelected = settings?.language == "en",
                            onSelect = {
                                scope.launch {
                                    speechManager.setLanguage("en")
                                }
                            }
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        // Future: Hindi/Hinglish (disabled for now)
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                            shape = MaterialTheme.shapes.small
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Hindi / Hinglish",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                    )
                                    Text(
                                        text = "Coming soon - Not supported yet",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                                    )
                                }
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = "Locked",
                                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                                )
                            }
                        }
                    }
                }
            }
            
            // Voice Selection
            item {
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(bottom = 12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.RecordVoiceOver,
                                contentDescription = "Voice",
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Voice Selection (Hindi Expert)",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        
                        Text(
                            text = "Current Voice: ${settings?.voiceName ?: "Aoede"}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                        
                        // Female Voices
                        Text(
                            text = "Female Voices (महिला आवाज़)",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                        
                        GeminiVoice.getFemaleVoices().forEach { voice ->
                            VoiceOption(
                                voice = voice,
                                isSelected = settings?.voiceName == voice.voiceName,
                                onSelect = {
                                    scope.launch {
                                        speechManager.setVoice(voice.voiceName)
                                    }
                                }
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                        
                        // Male Voices
                        Text(
                            text = "Male Voices (पुरुष आवाज़)",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                        
                        GeminiVoice.getMaleVoices().forEach { voice ->
                            VoiceOption(
                                voice = voice,
                                isSelected = settings?.voiceName == voice.voiceName,
                                onSelect = {
                                    scope.launch {
                                        speechManager.setVoice(voice.voiceName)
                                    }
                                }
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                }
            }
            
            // Queue Status
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (pendingCount > 0) 
                            MaterialTheme.colorScheme.tertiaryContainer 
                        else 
                            MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Queue,
                                contentDescription = "Queue",
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "Pending Queue",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "$pendingCount items waiting",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                        
                        if (pendingCount > 0) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp
                            )
                        }
                    }
                }
            }
            
            // User Info
            item {
                settings?.let { s ->
                    Card(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(bottom = 12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = "Info",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Configuration",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            
                            InfoRow("User Email", s.userEmail)
                            InfoRow("Language", s.language)
                            InfoRow("Webhook URL", s.webhookUrl.take(50) + "...")
                        }
                    }
                }
            }
            
            // History Header
            item {
                Text(
                    text = "Recent History",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            
            // History List
            if (history.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.HistoryToggleOff,
                                contentDescription = "No history",
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "No voice replies generated yet",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                            )
                        }
                    }
                }
            } else {
                items(history) { item ->
                    HistoryCard(item)
                }
            }
        }
    }
}

@Composable
fun LanguageOption(
    title: String,
    subtitle: String,
    code: String,
    isSelected: Boolean,
    onSelect: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        onClick = onSelect,
        color = if (isSelected) 
            MaterialTheme.colorScheme.primaryContainer 
        else 
            MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.small
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
            
            RadioButton(
                selected = isSelected,
                onClick = onSelect
            )
        }
    }
}

@Composable
fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Text(
            text = "$label:",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            modifier = Modifier.width(100.dp)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
fun HistoryCard(history: SpeechHistory) {
    val dateFormat = remember { SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()) }
    
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = history.phoneNumber,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = dateFormat.format(Date(history.createdAt)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
            
            Spacer(modifier = Modifier.height(4.dp))
            
            Text(
                text = history.text.take(100) + if (history.text.length > 100) "..." else "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row {
                    Icon(
                        imageVector = Icons.Default.Timer,
                        contentDescription = "Duration",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "${history.duration}s",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                
                Text(
                    text = history.language,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
fun VoiceOption(
    voice: GeminiVoice,
    isSelected: Boolean,
    onSelect: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        onClick = onSelect,
        color = if (isSelected) 
            MaterialTheme.colorScheme.primaryContainer 
        else 
            MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.small
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = voice.voiceName,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "(${voice.gender})",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
                Text(
                    text = voice.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
            
            RadioButton(
                selected = isSelected,
                onClick = onSelect
            )
        }
    }
}
