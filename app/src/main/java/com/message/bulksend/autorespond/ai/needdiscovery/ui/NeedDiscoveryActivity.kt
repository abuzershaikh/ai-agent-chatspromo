package com.message.bulksend.autorespond.ai.needdiscovery.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.message.bulksend.autorespond.ai.needdiscovery.NeedDiscoveryField
import com.message.bulksend.autorespond.ai.needdiscovery.NeedDiscoveryManager
import com.message.bulksend.autorespond.ai.needdiscovery.NeedDiscoverySchema
import kotlinx.coroutines.launch

class NeedDiscoveryActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val manager = NeedDiscoveryManager(this)
        setContent {
            MaterialTheme {
                NeedDiscoveryScreen(
                    manager = manager,
                    onBack = { finish() }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NeedDiscoveryScreen(
    manager: NeedDiscoveryManager,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var requiredLines by remember { mutableStateOf("") }
    var optionalLines by remember { mutableStateOf("") }
    var closureConditions by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        val schema = manager.getSchema()
        requiredLines = schema.requiredFields.joinToString("\n") { formatField(it) }
        optionalLines = schema.optionalFields.joinToString("\n") { formatField(it) }
        closureConditions = schema.closureConditions
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Need Discovery Setup",
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                },
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = Color(0xFF2563EB),
                        titleContentColor = Color.White
                    )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = Color(0xFF0B1220)
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Format: id|label|question|keyword1,keyword2",
                color = Color(0xFF94A3B8)
            )

            OutlinedTextField(
                value = requiredLines,
                onValueChange = { requiredLines = it },
                modifier = Modifier.fillMaxWidth().height(180.dp),
                label = { Text("Required Fields") }
            )

            OutlinedTextField(
                value = optionalLines,
                onValueChange = { optionalLines = it },
                modifier = Modifier.fillMaxWidth().height(160.dp),
                label = { Text("Optional Fields") }
            )

            OutlinedTextField(
                value = closureConditions,
                onValueChange = { closureConditions = it },
                modifier = Modifier.fillMaxWidth().height(120.dp),
                label = { Text("Closure Conditions") }
            )

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onBack,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF334155))
                ) {
                    Text("Cancel")
                }

                Button(
                    onClick = {
                        val schema = NeedDiscoverySchema(
                            requiredFields = parseLines(requiredLines, requiredDefault = true),
                            optionalFields = parseLines(optionalLines, requiredDefault = false),
                            closureConditions = closureConditions.trim()
                        )
                        manager.saveSchema(schema)
                        scope.launch {
                            snackbarHostState.showSnackbar("Need discovery schema saved")
                        }
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
                ) {
                    Text("Save")
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Tip: Add 3-5 required fields (name, requirement, budget, timeline) for better autonomous follow-up.",
                color = Color(0xFF94A3B8)
            )
        }
    }
}

private fun parseLines(raw: String, requiredDefault: Boolean): List<NeedDiscoveryField> {
    return raw
        .lineSequence()
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .mapNotNull { line ->
            val parts = line.split("|").map { it.trim() }
            if (parts.size < 3) return@mapNotNull null
            val id = parts[0]
            val label = parts[1]
            val question = parts[2]
            if (id.isBlank() || label.isBlank() || question.isBlank()) return@mapNotNull null
            val keywords =
                parts.getOrNull(3)
                    ?.split(",")
                    ?.map { it.trim() }
                    ?.filter { it.isNotBlank() }
                    .orEmpty()
            NeedDiscoveryField(
                id = id,
                label = label,
                question = question,
                required = requiredDefault,
                keywords = keywords
            )
        }
        .toList()
}

private fun formatField(field: NeedDiscoveryField): String {
    val keywords = field.keywords.joinToString(",")
    return if (keywords.isBlank()) {
        "${field.id}|${field.label}|${field.question}"
    } else {
        "${field.id}|${field.label}|${field.question}|$keywords"
    }
}
