package com.message.bulksend.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun StopButton(
    isVisible: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scale by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "stop_button_scale"
    )

    val pulseScale by rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue = 1f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    if (isVisible) {
        Button(
            onClick = onClick,
            modifier = modifier
                .scale(scale * pulseScale)
                .fillMaxWidth()
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFFFF5252),
                contentColor = Color.White
            ),
            shape = RoundedCornerShape(12.dp),
            elevation = ButtonDefaults.buttonElevation(
                defaultElevation = 8.dp,
                pressedElevation = 12.dp
            )
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    Icons.Default.Stop,
                    contentDescription = "Stop Campaign",
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "STOP CAMPAIGN",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun StopConfirmationDialog(
    showDialog: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    currentProgress: String
) {
    if (showDialog) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = {
                Text(
                    "Stop Campaign?",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            text = {
                Column {
                    Text(
                        "Are you sure you want to stop the campaign?",
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Current Progress: $currentProgress",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Your progress will be saved and you can resume later.",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = onConfirm,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFFF5252)
                    )
                ) {
                    Text("Stop", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text("Continue", color = MaterialTheme.colorScheme.primary)
                }
            },
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp
        )
    }
}