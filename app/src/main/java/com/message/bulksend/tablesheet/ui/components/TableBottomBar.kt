package com.message.bulksend.tablesheet.ui.components

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun TableBottomBar(
    onShowColumnManager: () -> Unit,
    onShowAddRows: () -> Unit,
    onShowSettings: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color(0xFFF5F5F5)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            BottomButton(Icons.Default.ViewColumn, "Column") { onShowColumnManager() }
            BottomButton(Icons.Default.Add, "Row") { onShowAddRows() }
            BottomButton(Icons.Default.FilterList, "Filter") { }
            BottomButton(Icons.Default.Settings, "Settings") { onShowSettings() }
            BottomButton(Icons.Default.Share, "Share") { }
        }
    }
}

@Composable
private fun BottomButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(12.dp, 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            icon,
            label,
            tint = Color(0xFF666666),
            modifier = Modifier.size(24.dp)
        )
        Text(
            label,
            fontSize = 10.sp,
            color = Color(0xFF666666)
        )
    }
}