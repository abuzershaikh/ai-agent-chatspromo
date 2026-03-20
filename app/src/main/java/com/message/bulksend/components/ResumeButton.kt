package com.message.bulksend.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
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
fun ResumeButton(
    isVisible: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    progressText: String = ""
) {
    val scale by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "resume_button_scale"
    )

    val shimmerScale by rememberInfiniteTransition(label = "shimmer").animateFloat(
        initialValue = 1f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "shimmer_scale"
    )

    if (isVisible) {
        Column(
            modifier = modifier,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (progressText.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "Previous Campaign Found",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            progressText,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }
                }
            }
            
            Button(
                onClick = onClick,
                modifier = Modifier
                    .scale(scale * shimmerScale)
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF4CAF50),
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(12.dp),
                elevation = ButtonDefaults.buttonElevation(
                    defaultElevation = 6.dp,
                    pressedElevation = 10.dp
                )
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        Icons.Default.PlayArrow,
                        contentDescription = "Resume Campaign",
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "RESUME CAMPAIGN",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun ResumeConfirmationDialog(
    showDialog: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    onStartOver: () -> Unit,
    campaignName: String,
    progressText: String
) {
    if (showDialog) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = {
                Text(
                    "Resume Campaign?",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            text = {
                Column {
                    Text(
                        "Campaign: $campaignName",
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Progress: $progressText",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        "Do you want to resume from where you left off or start over?",
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            },
            confirmButton = {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TextButton(
                        onClick = onStartOver,
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text("Start Over")
                    }
                    Button(
                        onClick = onConfirm,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF4CAF50)
                        )
                    ) {
                        Text("Resume", color = Color.White)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text("Cancel", color = MaterialTheme.colorScheme.onSurface)
                }
            },
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp
        )
    }
}