package com.message.bulksend.bulksend

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.message.bulksend.db.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

class AutonomousCampaignProgressActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AutonomousSenderTheme {
                AutonomousCampaignProgressScreen(
                    initialCampaignId = intent.getStringExtra(EXTRA_CAMPAIGN_ID)
                )
            }
        }
    }

    companion object {
        const val EXTRA_CAMPAIGN_ID = "autonomous_campaign_progress_id"

        fun createIntent(context: Context, campaignId: String): Intent {
            return Intent(context, AutonomousCampaignProgressActivity::class.java).apply {
                putExtra(EXTRA_CAMPAIGN_ID, campaignId)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AutonomousCampaignProgressScreen(initialCampaignId: String?) {
    val context = LocalContext.current
    val activity = context as? Activity
    val scope = rememberCoroutineScope()
    val database = remember { AppDatabase.getInstance(context) }
    val campaignDao = remember { database.campaignDao() }
    val queueDao = remember { database.autonomousSendQueueDao() }

    var trackedCampaignId by remember(initialCampaignId) { mutableStateOf(initialCampaignId) }
    var progressState by remember { mutableStateOf<AutonomousCampaignProgressState?>(null) }
    var screenError by remember { mutableStateOf<String?>(null) }
    var showResetDialog by remember { mutableStateOf(false) }
    var isResetting by remember { mutableStateOf(false) }

    LaunchedEffect(initialCampaignId) {
        if (!initialCampaignId.isNullOrBlank()) return@LaunchedEffect
        trackedCampaignId = withContext(Dispatchers.IO) {
            loadAutonomousCampaignLockState(campaignDao, queueDao)?.campaign?.id
        }
    }

    LaunchedEffect(trackedCampaignId) {
        val campaignId = trackedCampaignId
        if (campaignId.isNullOrBlank()) {
            screenError = "No active autonomous campaign found."
            progressState = null
            return@LaunchedEffect
        }

        while (true) {
            val latestState = withContext(Dispatchers.IO) {
                loadAutonomousCampaignProgressState(
                    context = context,
                    campaignDao = campaignDao,
                    autonomousQueueDao = queueDao,
                    campaignId = campaignId
                )
            }
            progressState = latestState
            screenError = if (latestState == null) {
                "Autonomous campaign details are no longer available."
            } else {
                null
            }
            if (latestState == null) return@LaunchedEffect
            delay(if (latestState.phase == AutonomousCampaignPhase.RUNNING) 2500L else 3500L)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "Campaign Progress",
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFF00E5FF)
                        )
                        Text(
                            "Autonomous sending status",
                            fontSize = 11.sp,
                            color = Color.White.copy(alpha = 0.6f)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { activity?.finish() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null,
                            tint = Color(0xFF00E5FF)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF0D0D1A))
            )
        },
        containerColor = Color.Transparent
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF080813),
                            Color(0xFF0D0D23),
                            Color(0xFF080813)
                        )
                    )
                )
                .padding(paddingValues)
        ) {
            if (progressState == null) {
                AutonomousCampaignEmptyState(
                    message = screenError ?: "Loading campaign details...",
                    onOpenSetup = {
                        openAutonomousSetup(context, activity)
                    }
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    AutonomousCampaignHeroCard(progressState!!)
                    AutonomousCampaignMetricsCard(progressState!!)
                    AutonomousCampaignDetailsCard(progressState!!)
                    AutonomousCampaignMessageCard(progressState!!.campaign.message)
                    AutonomousCampaignActions(
                        state = progressState!!,
                        isResetting = isResetting,
                        onReset = { showResetDialog = true },
                        onOpenSetup = {
                            openAutonomousSetup(context, activity)
                        }
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }

    if (showResetDialog && progressState != null) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("Reset current campaign") },
            text = {
                Text(
                    "This will clear the autonomous queue and unlock setup so a new campaign can be created."
                )
            },
            confirmButton = {
                Button(
                    enabled = !isResetting,
                    onClick = {
                        val state = progressState ?: return@Button
                        showResetDialog = false
                        scope.launch {
                            isResetting = true
                            withContext(Dispatchers.IO) {
                                resetAutonomousCampaign(
                                    context = context,
                                    campaignDao = campaignDao,
                                    autonomousQueueDao = queueDao,
                                    campaign = state.campaign
                                )
                            }
                            isResetting = false
                            Toast.makeText(
                                context,
                                "Campaign reset. You can create a new autonomous campaign now.",
                                Toast.LENGTH_LONG
                            ).show()
                            openAutonomousSetup(context, activity)
                        }
                    }
                ) {
                    Text("Reset")
                }
            },
            dismissButton = {
                OutlinedButton(
                    enabled = !isResetting,
                    onClick = { showResetDialog = false }
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun AutonomousCampaignHeroCard(state: AutonomousCampaignProgressState) {
    val tone = progressToneFor(state.phase)
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        shape = RoundedCornerShape(22.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xFF141321), Color(0xFF0D0D1A))
                    )
                )
                .border(1.dp, tone.color.copy(alpha = 0.35f), RoundedCornerShape(22.dp))
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        modifier = Modifier.size(52.dp),
                        shape = CircleShape,
                        color = tone.color.copy(alpha = 0.14f)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = tone.icon,
                                contentDescription = null,
                                tint = tone.color
                            )
                        }
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = state.campaign.campaignName.removePrefix("Autonomous_"),
                            color = Color.White,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 18.sp
                        )
                        Text(
                            text = tone.description,
                            color = Color.White.copy(alpha = 0.68f),
                            fontSize = 12.sp
                        )
                    }
                }
                Text(
                    text = tone.label,
                    color = tone.color,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    modifier = Modifier
                        .background(tone.color.copy(alpha = 0.12f), RoundedCornerShape(999.dp))
                        .padding(horizontal = 12.dp, vertical = 7.dp)
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "${state.completedCount} / ${state.campaign.totalContacts} processed",
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "${(state.progressFraction * 100).roundToInt()}%",
                        color = tone.color,
                        fontWeight = FontWeight.Bold
                    )
                }
                LinearProgressIndicator(
                    progress = { state.progressFraction },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp),
                    color = tone.color,
                    trackColor = Color.White.copy(alpha = 0.08f)
                )
            }
        }
    }
}

@Composable
private fun AutonomousCampaignMetricsCard(state: AutonomousCampaignProgressState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF101021)),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "Delivery Snapshot",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ProgressMetricTile("Sent", state.sentCount.toString(), Color(0xFF00E676), Modifier.weight(1f))
                ProgressMetricTile("Failed", state.failedCount.toString(), Color(0xFFFF6E6E), Modifier.weight(1f))
                ProgressMetricTile("Queued", state.queuedCount.toString(), Color(0xFF00E5FF), Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ProgressMetricTile("Pending", state.pendingCount.toString(), Color(0xFFFFC107), Modifier.weight(1f))
                ProgressMetricTile("Queued Today", state.stats.queuedToday.toString(), Color(0xFFFFB74D), Modifier.weight(1f))
                ProgressMetricTile("Sent Today", state.stats.sentToday.toString(), Color(0xFF7C4DFF), Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun ProgressMetricTile(
    label: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(accent.copy(alpha = 0.08f), RoundedCornerShape(14.dp))
            .border(1.dp, accent.copy(alpha = 0.2f), RoundedCornerShape(14.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = value,
            color = accent,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 18.sp
        )
        Text(
            text = label,
            color = Color.White.copy(alpha = 0.62f),
            fontSize = 11.sp
        )
    }
}

@Composable
private fun AutonomousCampaignDetailsCard(state: AutonomousCampaignProgressState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF101021)),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "Campaign Details",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )
            ProgressDetailRow("Created", formatCampaignTime(state.campaign.timestamp))
            ProgressDetailRow(
                "Next Send",
                state.stats.nextSendTimeMillis?.let(::formatCampaignTime) ?: "No pending message"
            )
            ProgressDetailRow("Queued Today", state.stats.queuedToday.toString())
            ProgressDetailRow("Days Left", state.stats.remainingDays.toString())
            ProgressDetailRow("Selected Days", state.selectedDays.toString())
            ProgressDetailRow("Recommended Days", state.recommendedDays.toString())
            ProgressDetailRow(
                "Target App",
                state.runtimeConfig?.whatsAppPreference ?: "Not available"
            )
            HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
            Text(
                "App can stay closed. Scheduled sends continue in the background and this screen tracks the live queue.",
                color = Color.White.copy(alpha = 0.68f),
                fontSize = 12.sp,
                lineHeight = 18.sp
            )
        }
    }
}

@Composable
private fun ProgressDetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            color = Color.White.copy(alpha = 0.55f),
            fontSize = 12.sp
        )
        Text(
            value,
            color = Color.White,
            fontWeight = FontWeight.SemiBold,
            fontSize = 12.sp
        )
    }
}

@Composable
private fun AutonomousCampaignMessageCard(message: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF101021)),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                "Message Preview",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )
            Text(
                text = message.ifBlank { "No message found." },
                color = Color.White.copy(alpha = 0.78f),
                fontSize = 13.sp,
                maxLines = 6,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun AutonomousCampaignActions(
    state: AutonomousCampaignProgressState,
    isResetting: Boolean,
    onReset: () -> Unit,
    onOpenSetup: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (state.phase == AutonomousCampaignPhase.COMPLETED) {
            Button(
                onClick = onOpenSetup,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00B8D4))
            ) {
                Text("Create New Campaign", fontWeight = FontWeight.Bold)
            }
        } else {
            Button(
                onClick = onReset,
                enabled = !isResetting,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5252))
            ) {
                if (isResetting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = Color.White
                    )
                    Spacer(Modifier.size(8.dp))
                }
                Text("Reset Current Campaign", fontWeight = FontWeight.Bold)
            }
        }

        OutlinedButton(
            onClick = onOpenSetup,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp)
        ) {
            Text("Open Setup Screen")
        }
    }
}

@Composable
private fun AutonomousCampaignEmptyState(
    message: String,
    onOpenSetup: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = message,
            color = Color.White.copy(alpha = 0.72f),
            fontSize = 14.sp
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onOpenSetup) {
            Text("Open Setup")
        }
    }
}

private data class ProgressTone(
    val label: String,
    val description: String,
    val color: Color,
    val icon: ImageVector
)

private fun progressToneFor(phase: AutonomousCampaignPhase): ProgressTone {
    return when (phase) {
        AutonomousCampaignPhase.RUNNING -> ProgressTone(
            label = "Running",
            description = "Messages are being sent in the background.",
            color = Color(0xFF00E676),
            icon = Icons.Filled.SmartToy
        )

        AutonomousCampaignPhase.PAUSED -> ProgressTone(
            label = "Paused",
            description = "Queue is locked and waiting for your action.",
            color = Color(0xFFFFC107),
            icon = Icons.Filled.PauseCircle
        )

        AutonomousCampaignPhase.PENDING -> ProgressTone(
            label = "Scheduled",
            description = "Campaign is queued and waiting for the next slot.",
            color = Color(0xFF00E5FF),
            icon = Icons.Filled.Schedule
        )

        AutonomousCampaignPhase.COMPLETED -> ProgressTone(
            label = "Completed",
            description = "Campaign finished. You can start a new one now.",
            color = Color(0xFF7C4DFF),
            icon = Icons.Filled.CheckCircle
        )
    }
}

private fun openAutonomousSetup(context: Context, activity: Activity?) {
    context.startActivity(
        Intent(context, AutonomousBulkSendActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    )
    activity?.finish()
}

private fun formatCampaignTime(timeMillis: Long): String {
    return SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()).format(Date(timeMillis))
}
