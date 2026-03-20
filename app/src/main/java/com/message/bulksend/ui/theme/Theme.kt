package com.message.bulksend.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFFEF4444),
    secondary = Color(0xFF10B981),
    tertiary = Color(0xFF3B82F6)
)

@Composable
fun BulkSendTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = LightColorScheme,
        content = content
    )
}

// Alias for compatibility
@Composable
fun BulksendTestTheme(
    content: @Composable () -> Unit
) {
    BulkSendTheme(content)
}

