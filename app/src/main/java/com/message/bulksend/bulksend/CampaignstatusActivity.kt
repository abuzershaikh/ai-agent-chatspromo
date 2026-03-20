package com.message.bulksend.bulksend

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.message.bulksend.bulksend.AUTONOMOUS_CAMPAIGN_TYPE
import com.message.bulksend.bulksend.sheetscampaign.SheetsendActivity
import com.message.bulksend.bulksend.textcamp.BulktextActivity
import com.message.bulksend.bulksend.textmedia.TextmediaActivity
import com.message.bulksend.data.ContactStatus
import com.message.bulksend.db.AppDatabase
import com.message.bulksend.db.Campaign
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class CampaignStatusActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            WhatsAppCampaignTheme {
                CampaignStatusScreen()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CampaignStatusScreen() {
    val context = LocalContext.current
    val db = remember { AppDatabase.getInstance(context) }
    val scope = rememberCoroutineScope()
    var campaigns by remember { mutableStateOf<List<Campaign>>(emptyList()) }
    var selectedCampaign by remember { mutableStateOf<Campaign?>(null) }
    var campaignToDelete by remember { mutableStateOf<Campaign?>(null) }

    fun refreshCampaigns() {
        scope.launch {
            campaigns = withContext(Dispatchers.IO) {
                db.campaignDao()
                    .getAllCampaigns()
                    .filterNot { it.campaignType == AUTONOMOUS_CAMPAIGN_TYPE }
            }
            if (selectedCampaign?.campaignType == AUTONOMOUS_CAMPAIGN_TYPE) {
                selectedCampaign = null
            }
        }
    }

    LaunchedEffect(Unit) {
        refreshCampaigns()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (selectedCampaign == null) "All Campaigns" else "Campaign Details") },
                navigationIcon = {
                    IconButton(onClick = {
                        if (selectedCampaign != null) {
                            selectedCampaign = null
                        } else {
                            (context as? Activity)?.finish()
                        }
                    }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        if (selectedCampaign == null) {
            if (campaigns.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No campaign history found.", color = MaterialTheme.colorScheme.onSurface)
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(campaigns.sortedByDescending { it.timestamp }) { campaign ->
                        CampaignHistoryCard(
                            campaign = campaign,
                            onClick = { selectedCampaign = campaign },
                            onDelete = { campaignToDelete = campaign }
                        )
                    }
                }
            }
        } else {
            selectedCampaign?.let { campaign ->
                CampaignDetailView(campaign = campaign, modifier = Modifier.padding(padding))
            }
        }
    }

    // Delete Confirmation Dialog
    campaignToDelete?.let { campaign ->
        AlertDialog(
            onDismissRequest = { campaignToDelete = null },
            icon = {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = { Text("Delete Campaign?") },
            text = {
                Text("Are you sure you want to delete \"${campaign.campaignName}\"? This action cannot be undone.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                db.campaignDao().deleteCampaign(campaign.id)
                            }
                            campaignToDelete = null
                            refreshCampaigns()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { campaignToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CampaignHistoryCard(
    campaign: Campaign,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    val progress = if (campaign.totalContacts > 0) {
        (campaign.sentCount.toFloat() + campaign.failedCount.toFloat()) / campaign.totalContacts.toFloat()
    } else 0f

    val isResumable = campaign.contactStatuses.any { it.status == "pending" }

    val statusText = when {
        !isResumable && progress >= 1f -> "Completed"
        else -> "Paused"
    }

    val statusColor = when {
        statusText == "Completed" -> MaterialTheme.colorScheme.primary
        else -> Color(0xFFFFA000) // Amber for Paused/Resumable
    }

    val campaignTypeTitle = when (campaign.campaignType) {
        "BULKTEXT" -> "Text Campaign"
        "BULKSEND" -> "Caption Campaign"
        "TEXTMEDIA" -> "File+Text Campaign"
        "SHEETSSEND" -> "Sheet Campaign"
        else -> "Campaign"
    }

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = campaignTypeTitle,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = campaign.campaignName,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Delete Campaign",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Text(
                        text = statusText,
                        color = Color.White,
                        modifier = Modifier
                            .background(statusColor, RoundedCornerShape(50))
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                        fontSize = 12.sp
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "ID: ${campaign.id.substring(0, 8)}...",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
            Text(
                text = SimpleDateFormat("MMM dd, yyyy - hh:mm a", Locale.getDefault()).format(Date(campaign.timestamp)),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("${campaign.sentCount}/${campaign.totalContacts} Sent", fontWeight = FontWeight.SemiBold)
                Text("${(progress * 100).toInt()}%", fontWeight = FontWeight.SemiBold)
            }
            Spacer(modifier = Modifier.height(4.dp))
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.outline
            )

            if (isResumable) {
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = {
                        val intent = when (campaign.campaignType) {
                            "BULKTEXT" -> Intent(context, BulktextActivity::class.java)
                            "TEXTMEDIA" -> Intent(context, TextmediaActivity::class.java)
                            "SHEETSSEND" -> Intent(context, SheetsendActivity::class.java)
                            else -> Intent(context, BulksendActivity::class.java)
                        }.apply {
                            putExtra("CAMPAIGN_ID_TO_RESUME", campaign.id)
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        }
                        context.startActivity(intent)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = "Resume")
                    Spacer(Modifier.width(8.dp))
                    Text("Resume Campaign")
                }
            }
        }
    }
}

@Composable
fun CampaignDetailView(campaign: Campaign, modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Text(
                text = "Contact Statuses",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }
        items(campaign.contactStatuses) { status ->
            ContactStatusRow(status)
        }
    }
}

@Composable
fun ContactStatusRow(status: ContactStatus) {
    val (icon, color) = when (status.status) {
        "sent" -> Icons.Default.CheckCircle to Color(0xFF00C853) // Green
        "failed" -> Icons.Default.Error to Color(0xFFFF5252) // Red
        else -> Icons.Default.HourglassEmpty to Color(0xFFFFA000) // Amber
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = status.status,
                    tint = color,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = status.number,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = status.status.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() },
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }
        }
    }
}

