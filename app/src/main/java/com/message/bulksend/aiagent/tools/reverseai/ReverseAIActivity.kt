package com.message.bulksend.aiagent.tools.reverseai

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.message.bulksend.ui.theme.BulksendTestTheme

class ReverseAIActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BulksendTestTheme {
                ReverseAIScreen(
                    onBackPressed = { finish() }
                )
            }
        }
    }
}
