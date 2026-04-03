package com.message.bulksend.autorespond.aireply

import android.os.Bundle
import android.widget.Toast
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.message.bulksend.ui.theme.BulksendTestTheme

class AIParametersActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        val provider = AIProvider.valueOf(intent.getStringExtra("provider") ?: "GEMINI")
        
        setContent {
            BulksendTestTheme {
                AIParametersScreen(
                    provider = provider,
                    onBackPressed = { finish() }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AIParametersScreen(provider: AIProvider, onBackPressed: () -> Unit) {
    val context = LocalContext.current
    val configManager = remember { AIConfigManager(context) }
    val config = configManager.getConfig(provider)
    
    var enableThinking by remember { mutableStateOf(config.enableThinking) }
    var selectedResponseLength by remember { mutableStateOf(config.responseLength) }
    var temperature by remember { mutableStateOf(config.temperature.toFloat()) }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text("AI Agent Parameters", color = Color.White, fontWeight = FontWeight.Medium)
                },
                navigationIcon = {
                    IconButton(onClick = onBackPressed) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                actions = {
                    IconButton(onClick = {
                        configManager.saveConfig(
                            provider = provider,
                            apiKey = config.apiKey,
                            model = config.model,
                            template = config.template,
                            enableThinking = enableThinking,
                            responseLength = selectedResponseLength,
                            temperature = temperature.toDouble()
                        )
                        Toast.makeText(context, "Parameters saved!", Toast.LENGTH_SHORT).show()
                    }) {
                        Icon(Icons.Default.Save, contentDescription = "Save", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF00796B))
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFF5F5F5))
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // Response Length
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.TextFields,
                            contentDescription = null,
                            tint = Color(0xFF00796B),
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            "Response Length",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF212121)
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    Text(
                        "Control how detailed AI Agent responses should be",
                        fontSize = 14.sp,
                        color = Color(0xFF6B7280)
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    ResponseLength.values().forEach { length ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedResponseLength == length,
                                onClick = { selectedResponseLength = length },
                                colors = RadioButtonDefaults.colors(selectedColor = Color(0xFF00796B))
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    length.displayName,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color(0xFF212121)
                                )
                                Text(
                                    "${length.tokens} tokens - ${length.instruction}",
                                    fontSize = 12.sp,
                                    color = Color(0xFF6B7280)
                                )
                            }
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Thinking Mode (only for Pro models)
            if (provider == AIProvider.GEMINI && config.model.contains("pro", ignoreCase = true)) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Psychology,
                                contentDescription = null,
                                tint = Color(0xFF8B5CF6),
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "Thinking Mode",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF212121)
                                )
                                Text(
                                    "Better quality but slower & uses more tokens",
                                    fontSize = 12.sp,
                                    color = Color(0xFF6B7280)
                                )
                            }
                            Switch(
                                checked = enableThinking,
                                onCheckedChange = { enableThinking = it },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = Color(0xFF8B5CF6)
                                )
                            )
                        }
                        
                        if (enableThinking) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF3C7)),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.Info,
                                        contentDescription = null,
                                        tint = Color(0xFFD97706),
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        "Thinking mode uses ~500 extra tokens per request",
                                        fontSize = 12.sp,
                                        color = Color(0xFF92400E)
                                    )
                                }
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
            }
            
            // Temperature
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Thermostat,
                            contentDescription = null,
                            tint = Color(0xFFEF4444),
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Temperature",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF212121)
                            )
                            Text(
                                "Controls randomness: ${String.format("%.1f", temperature)}",
                                fontSize = 14.sp,
                                color = Color(0xFF6B7280)
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Slider(
                        value = temperature,
                        onValueChange = { temperature = it },
                        valueRange = 0f..1f,
                        steps = 9,
                        colors = SliderDefaults.colors(
                            thumbColor = Color(0xFFEF4444),
                            activeTrackColor = Color(0xFFEF4444)
                        )
                    )
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("0.0 (Focused)", fontSize = 12.sp, color = Color(0xFF6B7280))
                        Text("1.0 (Creative)", fontSize = 12.sp, color = Color(0xFF6B7280))
                    }
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    Text(
                        when {
                            temperature < 0.3f -> "Very focused and deterministic responses"
                            temperature < 0.7f -> "Balanced responses (recommended)"
                            else -> "More creative and varied responses"
                        },
                        fontSize = 13.sp,
                        color = Color(0xFF059669),
                        fontWeight = FontWeight.Medium
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Info Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFDCFCE7)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Lightbulb,
                        contentDescription = null,
                        tint = Color(0xFF059669),
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            "Tip",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF065F46)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "For customer support, use Short length with temperature 0.3-0.5 for consistent, professional responses.",
                            fontSize = 13.sp,
                            color = Color(0xFF047857)
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(80.dp))
        }
    }
}
