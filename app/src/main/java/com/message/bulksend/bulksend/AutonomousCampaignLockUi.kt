package com.message.bulksend.bulksend

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.message.bulksend.db.AutonomousSendQueueDao
import com.message.bulksend.db.Campaign
import com.message.bulksend.db.CampaignDao
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class AutonomousCampaignLockState(
    val campaign: Campaign,
    val queuedCount: Int,
    val sentCount: Int,
    val failedCount: Int,
    val pendingCount: Int,
    val nextSendTimeMillis: Long?
)

suspend fun loadAutonomousCampaignLockState(
    campaignDao: CampaignDao,
    autonomousQueueDao: AutonomousSendQueueDao
): AutonomousCampaignLockState? {
    val campaigns = campaignDao.getCampaignsByType(AUTONOMOUS_CAMPAIGN_TYPE)
    return campaigns.firstNotNullOfOrNull { campaign ->
        val queuedCount = autonomousQueueDao.countByStatus(campaign.id, "queued")
        val isLocked = campaign.isRunning || queuedCount > 0
        if (!isLocked) {
            null
        } else {
            AutonomousCampaignLockState(
                campaign = campaign,
                queuedCount = queuedCount,
                sentCount = campaign.sentCount,
                failedCount = campaign.failedCount.coerceAtLeast(
                    autonomousQueueDao.countByStatus(campaign.id, "failed")
                ),
                pendingCount = campaign.contactStatuses.count { it.status == "pending" },
                nextSendTimeMillis = autonomousQueueDao.getNextSendTime(campaign.id)
            )
        }
    }
}

@Composable
fun AutonomousCampaignLockCard(
    state: AutonomousCampaignLockState,
    onReset: () -> Unit
) {
    val accent = when {
        state.campaign.isRunning -> Color(0xFF00E5FF)
        state.queuedCount > 0 -> Color(0xFFFFC107)
        else -> Color(0xFF7C4DFF)
    }
    val statusLabel = when {
        state.campaign.isRunning -> "Running"
        state.queuedCount > 0 -> "Pending Resume"
        else -> "Locked"
    }

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, accent.copy(alpha = 0.35f), RoundedCornerShape(18.dp))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xFF141321), Color(0xFF0D0D1A))
                    )
                )
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "One Campaign Active",
                        color = accent,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 16.sp
                    )
                    Text(
                        state.campaign.campaignName.removePrefix("Autonomous_"),
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
                Text(
                    statusLabel,
                    color = accent,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    modifier = Modifier
                        .background(accent.copy(alpha = 0.12f), RoundedCornerShape(999.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }

            Text(
                "Reset or complete this campaign before creating another autonomous campaign.",
                color = Color.White.copy(alpha = 0.72f),
                fontSize = 12.sp,
                lineHeight = 18.sp
            )

            AutonomousCampaignLockMetricRow(
                leftLabel = "Created",
                leftValue = formatLockTime(state.campaign.timestamp),
                rightLabel = "Next Send",
                rightValue = state.nextSendTimeMillis?.let(::formatLockTime) ?: "Not scheduled"
            )

            AutonomousCampaignLockMetricRow(
                leftLabel = "Total",
                leftValue = state.campaign.totalContacts.toString(),
                rightLabel = "Queued",
                rightValue = state.queuedCount.toString()
            )

            AutonomousCampaignLockMetricRow(
                leftLabel = "Sent",
                leftValue = state.sentCount.toString(),
                rightLabel = "Pending",
                rightValue = state.pendingCount.toString()
            )

            AutonomousCampaignLockMetricRow(
                leftLabel = "Failed",
                leftValue = state.failedCount.toString(),
                rightLabel = "Campaign Id",
                rightValue = state.campaign.id.take(8)
            )

            Button(
                onClick = onReset,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFFF5252).copy(alpha = 0.16f),
                    contentColor = Color(0xFFFFCDD2)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Reset Current Campaign", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun AutonomousCampaignLockMetricRow(
    leftLabel: String,
    leftValue: String,
    rightLabel: String,
    rightValue: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        AutonomousCampaignLockMetricCell(
            label = leftLabel,
            value = leftValue,
            modifier = Modifier.weight(1f)
        )
        AutonomousCampaignLockMetricCell(
            label = rightLabel,
            value = rightValue,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun AutonomousCampaignLockMetricCell(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(Color.White.copy(alpha = 0.04f), RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            label,
            color = Color.White.copy(alpha = 0.55f),
            fontSize = 11.sp
        )
        Text(
            value,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp
        )
    }
}

private fun formatLockTime(timeMillis: Long): String {
    return SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()).format(Date(timeMillis))
}
