package com.message.bulksend.autorespond.ui.settings

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.message.bulksend.autorespond.ui.catalogue.ProductCatalogueActivity
import com.message.bulksend.tablesheet.TableSheetActivity

class AIAgentSettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = androidx.compose.ui.graphics.Color(0xFF8B5CF6),
                    secondary = androidx.compose.ui.graphics.Color(0xFF6366F1),
                    background = androidx.compose.ui.graphics.Color(0xFF0F0F23)
                )
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AgentControlScreen(onBack = { finish() })
                }
            }
        }
    }
}
