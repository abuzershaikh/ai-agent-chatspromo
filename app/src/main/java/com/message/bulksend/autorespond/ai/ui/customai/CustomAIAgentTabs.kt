package com.message.bulksend.autorespond.ai.ui.customai

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.ui.graphics.vector.ImageVector

enum class CustomAIAgentTab(
    val title: String,
    val icon: ImageVector
) {
    PROMPT("Prompt", Icons.Default.AutoAwesome),
    SETTINGS("Settings", Icons.Default.Settings),
    TOOLS("Tools", Icons.Default.Build),
    SHEET("Sheet", Icons.Default.TableChart)
}
