package com.message.bulksend.autorespond.ai.ui.customai

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
import com.message.bulksend.ui.theme.BulksendTestTheme

class CustomAIAgentActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            BulksendTestTheme {
                CustomAIAgentScreen(onBack = { finish() })
            }
        }
    }
}
