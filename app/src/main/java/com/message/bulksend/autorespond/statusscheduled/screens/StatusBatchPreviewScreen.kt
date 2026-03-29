package com.message.bulksend.autorespond.statusscheduled.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.message.bulksend.autorespond.statusscheduled.models.BatchStatus
import com.message.bulksend.autorespond.statusscheduled.models.MediaItem
import com.message.bulksend.autorespond.statusscheduled.models.MediaType
import com.message.bulksend.autorespond.statusscheduled.models.ScheduleType
import com.message.bulksend.autorespond.statusscheduled.models.StatusBatch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun StatusBatchPreviewScreen(
    batch: StatusBatch,
    onBack: () -> Unit,
    onSchedule: () -> Unit,
    onPostNow: () -> Unit,
    onCancelSchedule: () -> Unit,
    onDelete: () -> Unit
) {
    val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
    val createdAtText = dateFormat.format(Date(batch.createdAt))
    val scheduledText = if (batch.startDate != null && batch.time != null && batch.amPm != null) {
        "${dateFormat.format(Date(batch.startDate))} at ${batch.time} ${batch.amPm}"
    } else {
        "Not scheduled yet"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(top = 24.dp)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF10B981)),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Batch #${batch.id}",
                        color = Color.White,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                }
                Text(
                    text = "Created on $createdAtText",
                    color = Color.White.copy(alpha = 0.92f),
                    fontSize = 14.sp
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PreviewBadge(text = batch.status.name, color = statusColor(batch.status))
                    PreviewBadge(
                        text = if (batch.scheduleType == ScheduleType.AUTO) "AUTO DAILY" else "ONE-TIME",
                        color = if (batch.scheduleType == ScheduleType.AUTO) Color(0xFF4338CA) else Color(0xFF1D4ED8)
                    )
                    PreviewBadge(
                        text = "${batch.mediaList.size} MEDIA",
                        color = Color(0xFF065F46)
                    )
                }
            }
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFF9FAFB)),
            shape = RoundedCornerShape(16.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE5E7EB))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                DetailRow(
                    icon = if (batch.scheduleType == ScheduleType.AUTO) Icons.Default.Autorenew else Icons.Default.Schedule,
                    title = "Mode",
                    value = if (batch.scheduleType == ScheduleType.AUTO) {
                        "Part of auto daily series"
                    } else {
                        "This batch will run once on the selected day"
                    }
                )
                DetailRow(
                    icon = Icons.Default.CalendarToday,
                    title = "Scheduled For",
                    value = scheduledText
                )
                DetailRow(
                    icon = Icons.Default.Repeat,
                    title = "Repeat",
                    value = if (batch.scheduleType == ScheduleType.AUTO) "1 batch per day auto queue" else "No repeat"
                )
                DetailRow(
                    icon = Icons.Default.Notifications,
                    title = "Reminder",
                    value = batch.reminderMinutes?.let { "$it minutes before" } ?: "No reminder"
                )
            }
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (batch.status == BatchStatus.DRAFT) Color(0xFFF0FDF4) else Color(0xFFF8FAFC)
            ),
            shape = RoundedCornerShape(14.dp),
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                if (batch.status == BatchStatus.DRAFT) Color(0xFFA7F3D0) else Color(0xFFE2E8F0)
            )
        ) {
            Text(
                text = when (batch.status) {
                    BatchStatus.DRAFT -> "This batch is only saved as draft right now. It will not go anywhere until you schedule or post it."
                    BatchStatus.SCHEDULED -> "This batch is ready and will run on its assigned day and time."
                    BatchStatus.POSTING -> "This batch is currently being posted."
                    BatchStatus.POSTED -> "This batch has already been posted. You can post again or set a new schedule."
                    BatchStatus.FAILED -> "This batch stopped during posting. You can post again or set a fresh schedule."
                },
                color = Color(0xFF334155),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Media Preview",
                color = Color(0xFF111827),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )

            batch.mediaList.forEachIndexed { index, media ->
                PreviewMediaCard(
                    media = media,
                    label = "Media ${index + 1}",
                    delayText = if (media.delayMinutes > 0) "${media.delayMinutes} min delay after post" else "No extra delay"
                )
            }
        }

        when (batch.status) {
            BatchStatus.SCHEDULED -> {
                ActionButtons(
                    primaryLabel = "Post Now",
                    primaryColor = Color(0xFF10B981),
                    onPrimary = onPostNow,
                    secondaryLabel = "Cancel Schedule",
                    secondaryColor = Color(0xFFEF4444),
                    onSecondary = onCancelSchedule
                )
            }

            BatchStatus.POSTING -> {
                ActionButtons(
                    primaryLabel = "Back To List",
                    primaryColor = Color(0xFF10B981),
                    onPrimary = onBack,
                    secondaryLabel = "Delete Batch",
                    secondaryColor = Color(0xFFEF4444),
                    onSecondary = onDelete
                )
            }

            else -> {
                ActionButtons(
                    primaryLabel = "Post Now",
                    primaryColor = Color(0xFF10B981),
                    onPrimary = onPostNow,
                    secondaryLabel = if (batch.startDate != null && batch.time != null && batch.amPm != null) {
                        "Update Schedule"
                    } else {
                        "Set Schedule"
                    },
                    secondaryColor = Color(0xFF2563EB),
                    onSecondary = onSchedule
                )
            }
        }

        OutlinedButton(
            onClick = onDelete,
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .height(52.dp),
            shape = RoundedCornerShape(14.dp),
            border = androidx.compose.foundation.BorderStroke(2.dp, Color(0xFFEF4444)),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFEF4444))
        ) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.size(8.dp))
            Text(
                text = "Delete Batch",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun ActionButtons(
    primaryLabel: String,
    primaryColor: Color,
    onPrimary: () -> Unit,
    secondaryLabel: String,
    secondaryColor: Color,
    onSecondary: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Button(
            onClick = onPrimary,
            modifier = Modifier
                .weight(1f)
                .height(52.dp),
            colors = ButtonDefaults.buttonColors(containerColor = primaryColor),
            shape = RoundedCornerShape(14.dp)
        ) {
            Text(
                text = primaryLabel,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Button(
            onClick = onSecondary,
            modifier = Modifier
                .weight(1f)
                .height(52.dp),
            colors = ButtonDefaults.buttonColors(containerColor = secondaryColor),
            shape = RoundedCornerShape(14.dp)
        ) {
            Text(
                text = secondaryLabel,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun PreviewMediaCard(
    media: MediaItem,
    label: String,
    delayText: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF9FAFB)),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE5E7EB))
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 10f)
            ) {
                AsyncImage(
                    model = media.uri,
                    contentDescription = media.name,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)),
                    contentScale = ContentScale.Crop
                )
                Surface(
                    color = if (media.type == MediaType.VIDEO) Color(0xFFEF4444) else Color(0xFF10B981),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (media.type == MediaType.VIDEO) Icons.Default.VideoLibrary else Icons.Default.Image,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = if (media.type == MediaType.VIDEO) "VIDEO" else "IMAGE",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp)
                    .padding(bottom = 14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = label,
                    color = Color(0xFF111827),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = media.name,
                    color = Color(0xFF374151),
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "Size: ${media.size / 1024} KB",
                    color = Color(0xFF6B7280),
                    fontSize = 12.sp
                )
                Text(
                    text = delayText,
                    color = Color(0xFF6B7280),
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
private fun DetailRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color(0xFF10B981),
            modifier = Modifier.size(20.dp)
        )
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = title,
                color = Color(0xFF6B7280),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = value,
                color = Color(0xFF111827),
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun PreviewBadge(
    text: String,
    color: Color
) {
    Surface(
        color = color.copy(alpha = 0.18f),
        shape = RoundedCornerShape(8.dp)
    ) {
        Text(
            text = text,
            color = color,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        )
    }
}

private fun statusColor(status: BatchStatus): Color {
    return when (status) {
        BatchStatus.DRAFT -> Color(0xFF6B7280)
        BatchStatus.SCHEDULED -> Color(0xFF2563EB)
        BatchStatus.POSTING -> Color(0xFFF59E0B)
        BatchStatus.POSTED -> Color(0xFF10B981)
        BatchStatus.FAILED -> Color(0xFFEF4444)
    }
}
