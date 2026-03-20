package com.message.bulksend.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Section header composable for "Paused Campaigns" and "Completed Campaigns" sections
 * Requirements: 6.1, 6.2
 */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Text(
            text = title,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = when (title) {
                "Paused Campaigns" -> MaterialTheme.colorScheme.primary
                "Completed Campaigns" -> MaterialTheme.colorScheme.secondary
                else -> MaterialTheme.colorScheme.onSurface
            }
        )
        
        subtitle?.let {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = it,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Divider line for visual separation
        HorizontalDivider(
            color = when (title) {
                "Paused Campaigns" -> MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                "Completed Campaigns" -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.3f)
                else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
            },
            thickness = 2.dp
        )
    }
}