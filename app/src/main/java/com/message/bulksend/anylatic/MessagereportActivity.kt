package com.message.bulksend.anylatic

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.message.bulksend.bulksend.sheetscampaign.SheetData
import com.message.bulksend.contactmanager.Contact
import com.message.bulksend.data.ContactStatus
import com.message.bulksend.db.AppDatabase
import com.message.bulksend.db.Campaign
import com.message.bulksend.ui.theme.BulksendTestTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

// Enhanced data class with additional visual properties
data class ReportDetail(
    val serialNumber: Int,
    val contact: Contact,
    val status: String,
    val statusColor: Color = Color.Gray,
    val statusIcon: ImageVector = Icons.Default.HourglassEmpty
)

// Enhanced enum with colors and icons
enum class ReportStatusFilter(
    val statusName: String,
    val color: Color,
    val icon: ImageVector,
    val gradientColors: List<Color>
) {
    All("All", Color(0xFF6366F1), Icons.Default.List, listOf(Color(0xFF6366F1), Color(0xFF8B5CF6))),
    Success("Success", Color(0xFF10B981), Icons.Default.CheckCircle, listOf(Color(0xFF10B981), Color(0xFF34D399))),
    Failed("Failed", Color(0xFFEF4444), Icons.Default.Error, listOf(Color(0xFFEF4444), Color(0xFFF87171))),
    Pending("Pending", Color(0xFFF59E0B), Icons.Default.Schedule, listOf(Color(0xFFF59E0B), Color(0xFFFBBF24)))
}

private fun toReportStatusText(statusInfo: ContactStatus): String {
    return when {
        statusInfo.status == "failed" && statusInfo.failureReason == "not_on_whatsapp" -> "Not on WhatsApp"
        else -> statusInfo.status.replaceFirstChar {
            if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()
        }
    }
}

private fun isFailedReportStatus(statusText: String): Boolean {
    return statusText == "Failed" || statusText == "Not on WhatsApp"
}

class MessagereportActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Enable edge-to-edge display for a modern UI
        enableEdgeToEdge()
        val campaignId = intent.getStringExtra("CAMPAIGN_ID")
        setContent {
            BulksendTestTheme {
                MessageReportScreen(campaignId = campaignId)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun MessageReportScreen(campaignId: String?) {
    val context = LocalContext.current
    val db = remember { AppDatabase.getInstance(context) }
    val gson = remember { Gson() }
    var campaign by remember { mutableStateOf<Campaign?>(null) }
    var reportDetails by remember { mutableStateOf<List<ReportDetail>>(emptyList()) }
    var groupNotFound by remember { mutableStateOf(false) }
    var fontSize by remember { mutableStateOf(14.sp) }
    var cellWidth by remember { mutableStateOf(100.dp) }
    var selectedFilter by remember { mutableStateOf(ReportStatusFilter.All) }
    var isSheetFullScreen by remember { mutableStateOf(false) }

    // State for pinch-to-zoom
    var scale by remember { mutableStateOf(1f) }

    // Loading states
    var isLoading by remember { mutableStateOf(true) }
    var loadingProgress by remember { mutableStateOf(0f) }
    var loadingMessage by remember { mutableStateOf("Loading campaign data...") }
    var isLoaded by remember { mutableStateOf(false) }

    val animatedFloat by animateFloatAsState(
        targetValue = if (isLoaded) 1f else 0f,
        animationSpec = tween(1000, easing = EaseOutBounce),
        label = "content_animation"
    )

    LaunchedEffect(campaignId) {
        if (campaignId != null) {
            isLoading = true
            loadingProgress = 0f
            loadingMessage = "Loading campaign data..."

            try {
                // Step 1: Load campaign data
                val selectedCampaign = withContext(Dispatchers.IO) {
                    db.campaignDao().getCampaignById(campaignId)
                }
                campaign = selectedCampaign
                loadingProgress = 0.2f

                if (selectedCampaign != null) {
                    loadingMessage = "Processing ${selectedCampaign.totalContacts} contacts..."

                    // All heavy processing moved to background thread
                    val details = withContext(Dispatchers.IO) {
                        if (selectedCampaign.campaignType == "SHEETSSEND") {
                            // Handle Sheet Campaign
                            selectedCampaign.sheetDataJson?.let { json ->
                                val type = object : TypeToken<SheetData>() {}.type
                                val sheetData: SheetData = gson.fromJson(json, type)

                                // Process in chunks to avoid blocking
                                val chunkSize = 500
                                val totalContacts = selectedCampaign.contactStatuses.size
                                val processedDetails = mutableListOf<ReportDetail>()

                                selectedCampaign.contactStatuses.chunked(chunkSize).forEachIndexed { chunkIndex, chunk ->
                                    val chunkDetails = chunk.mapIndexed { indexInChunk, statusInfo ->
                                        val globalIndex = chunkIndex * chunkSize + indexInChunk

                                        val rowData = sheetData.rows.find { row ->
                                            val numberInSheet = row.entries.firstOrNull {
                                                it.key.equals("Phone Number", ignoreCase = true) ||
                                                        it.key.equals("Number", ignoreCase = true)
                                            }?.value

                                            val normalizedNumberInSheet = numberInSheet?.trim()?.let {
                                                if (!it.startsWith("+") && !selectedCampaign.countryCode.isNullOrBlank()) {
                                                    selectedCampaign.countryCode + it
                                                } else {
                                                    it
                                                }
                                            }
                                            normalizedNumberInSheet == statusInfo.number
                                        }

                                        val name = rowData?.entries?.firstOrNull {
                                            it.key.equals("Name", ignoreCase = true)
                                        }?.value ?: "Unknown"
                                        val contact = Contact(name, statusInfo.number)

                                        val status = toReportStatusText(statusInfo)
                                        val (statusColor, statusIcon) = when (statusInfo.status) {
                                            "sent" -> Pair(Color(0xFF10B981), Icons.Default.CheckCircle)
                                            "failed" -> Pair(Color(0xFFEF4444), Icons.Default.Cancel)
                                            else -> Pair(Color(0xFFF59E0B), Icons.Default.Schedule)
                                        }
                                        ReportDetail(globalIndex + 1, contact, status, statusColor, statusIcon)
                                    }

                                    processedDetails.addAll(chunkDetails)

                                    // Update progress
                                    val progress = 0.2f + (0.7f * (chunkIndex + 1) / selectedCampaign.contactStatuses.chunked(chunkSize).size)
                                    withContext(Dispatchers.Main) {
                                        loadingProgress = progress
                                        loadingMessage = "Processing contacts... ${processedDetails.size}/${totalContacts}"
                                    }

                                    // Small delay to prevent overwhelming the system
                                    if (chunkIndex % 5 == 0) {
                                        kotlinx.coroutines.delay(10)
                                    }
                                }

                                processedDetails
                            } ?: emptyList()
                        } else {
                            // Handle Group-based Campaign
                            val campaignGroup = try {
                                db.contactGroupDao().getGroupById(selectedCampaign.groupId.toLong())
                            } catch (e: NumberFormatException) {
                                null
                            }

                            // Process in chunks to avoid blocking.
                            // If original group is missing (e.g. selected-contact campaigns), fallback to contactStatuses.
                            val chunkSize = 500
                            val totalContacts = selectedCampaign.contactStatuses.size
                            val processedDetails = mutableListOf<ReportDetail>()
                            val contactMap = campaignGroup?.contacts?.associateBy { it.number }.orEmpty()

                            selectedCampaign.contactStatuses.chunked(chunkSize).forEachIndexed { chunkIndex, chunk ->
                                val chunkDetails = chunk.mapIndexed { indexInChunk, statusInfo ->
                                    val globalIndex = chunkIndex * chunkSize + indexInChunk

                                    val contactEntity = contactMap[statusInfo.number]
                                    val contact = contactEntity?.let {
                                        Contact(name = it.name, number = it.number, isWhatsApp = it.isWhatsApp)
                                    } ?: Contact(
                                        name = statusInfo.number,
                                        number = statusInfo.number,
                                        isWhatsApp = false
                                    )

                                    val status = toReportStatusText(statusInfo)
                                    val (statusColor, statusIcon) = when (statusInfo.status) {
                                        "sent" -> Pair(Color(0xFF10B981), Icons.Default.CheckCircle)
                                        "failed" -> Pair(Color(0xFFEF4444), Icons.Default.Cancel)
                                        else -> Pair(Color(0xFFF59E0B), Icons.Default.Schedule)
                                    }
                                    ReportDetail(globalIndex + 1, contact, status, statusColor, statusIcon)
                                }

                                processedDetails.addAll(chunkDetails)

                                // Update progress
                                val progress = 0.2f + (0.7f * (chunkIndex + 1) / selectedCampaign.contactStatuses.chunked(chunkSize).size)
                                withContext(Dispatchers.Main) {
                                    loadingProgress = progress
                                    loadingMessage = "Processing contacts... ${processedDetails.size}/${totalContacts}"
                                }

                                // Small delay to prevent overwhelming the system
                                if (chunkIndex % 5 == 0) {
                                    kotlinx.coroutines.delay(10)
                                }
                            }

                            processedDetails
                        }
                    }

                    // Update UI on main thread
                    reportDetails = details
                    groupNotFound = details.isEmpty() && selectedCampaign.totalContacts > 0
                    loadingProgress = 1f
                    loadingMessage = "Complete!"
                } else {
                    groupNotFound = true
                }
            } catch (e: Exception) {
                groupNotFound = true
                loadingMessage = "Error loading data: ${e.message}"
            } finally {
                isLoading = false
                isLoaded = true
            }
        }
    }


    val filteredReportDetails = remember(reportDetails, selectedFilter) {
        when (selectedFilter) {
            ReportStatusFilter.All -> reportDetails
            ReportStatusFilter.Success -> reportDetails.filter { it.status == "Sent" }
            ReportStatusFilter.Failed -> reportDetails.filter { isFailedReportStatus(it.status) }
            ReportStatusFilter.Pending -> reportDetails.filter { it.status == "Pending" }
        }
    }

    val totalContacts = campaign?.totalContacts ?: 0
    val successCount = campaign?.sentCount ?: 0
    val successRate = if (totalContacts > 0) (successCount * 100f / totalContacts) else 0f

    Scaffold(
        modifier = Modifier.fillMaxSize(), // Use full screen for edge-to-edge
        topBar = {
            EnhancedReportTopAppBar(
                campaign = campaign,
                fontSize = fontSize,
                onFontSizeChange = { newSize ->
                    if (newSize >= 8.sp && newSize <= 24.sp) {
                        fontSize = newSize
                    }
                },
                cellWidth = cellWidth,
                onCellWidthChange = { newWidth ->
                    if (newWidth >= 50.dp && newWidth <= 300.dp) {
                        cellWidth = newWidth
                    }
                },
                isFullScreen = isSheetFullScreen,
                onToggleFullScreen = { isSheetFullScreen = !isSheetFullScreen },
                filteredReportDetails = filteredReportDetails
            )
        }
    ) { padding ->
        val currentCampaign = campaign

        // Show loading UI when data is being processed
        if (isLoading || currentCampaign == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                                MaterialTheme.colorScheme.background
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    // Enhanced loading indicator with progress
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.size(120.dp)
                    ) {
                        CircularProgressIndicator(
                            progress = { loadingProgress },
                            modifier = Modifier.size(100.dp),
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 6.dp,
                            trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                        )
                        Text(
                            text = "${(loadingProgress * 100).toInt()}%",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Text(
                        text = if (campaignId == null) "No report selected" else loadingMessage,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                        textAlign = TextAlign.Center
                    )

                    if (isLoading && loadingProgress > 0.2f) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Processing large dataset in background...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        } else {
            AnimatedVisibility(
                visible = isLoaded,
                enter = slideInVertically() + fadeIn(),
                exit = slideOutVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize() // Ensure column takes full space
                        .padding(padding) // Apply insets for system bars
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.background,
                                    MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
                                )
                            )
                        )
                ) {
                    // These sections will be hidden in fullscreen mode
                    AnimatedVisibility(visible = !isSheetFullScreen) {
                        Column {
                            EnhancedHeaderSection(
                                campaign = currentCampaign,
                                successRate = successRate,
                                animatedFloat = animatedFloat
                            )
                            StatisticsCardsRow(
                                totalContacts = currentCampaign.totalContacts,
                                successCount = currentCampaign.sentCount,
                                failedCount = currentCampaign.failedCount,
                                pendingCount = currentCampaign.totalContacts - currentCampaign.sentCount - currentCampaign.failedCount,
                                animatedFloat = animatedFloat
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            EnhancedFilterChipsRow(
                                selectedFilter = selectedFilter,
                                onFilterChange = { newFilter -> selectedFilter = newFilter },
                                reportDetails = reportDetails
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                        }
                    }

                    if (groupNotFound) {
                        ErrorStateCard()
                    } else {
                        // Box to contain the data table and handle zoom gestures
                        Box(
                            modifier = (if (isSheetFullScreen) Modifier.weight(1f) else Modifier)
                                .pointerInput(Unit) {
                                    detectTransformGestures { _, _, zoom, _ ->
                                        // Update the scale state based on the zoom gesture
                                        // Clamp the scale factor to prevent zooming too far in or out
                                        scale = max(0.5f, min(scale * zoom, 3f))
                                    }
                                }
                        ) {
                            EnhancedDataTable(
                                filteredReportDetails = filteredReportDetails,
                                // Apply the zoom scale to the font size and cell width
                                fontSize = (fontSize.value * scale).sp,
                                cellWidth = (cellWidth.value * scale).dp,
                                modifier = Modifier
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EnhancedReportTopAppBar(
    campaign: Campaign?,
    fontSize: TextUnit,
    onFontSizeChange: (TextUnit) -> Unit,
    cellWidth: Dp,
    onCellWidthChange: (Dp) -> Unit,
    isFullScreen: Boolean,
    onToggleFullScreen: () -> Unit,
    filteredReportDetails: List<ReportDetail>
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    TopAppBar(
        title = { /* Title removed as per request */ },
        navigationIcon = {
            IconButton(
                onClick = { (context as? ComponentActivity)?.finish() },
                modifier = Modifier
                    .padding(4.dp)
                    .background(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                        CircleShape
                    )
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        },
        actions = {
            // CSV Download button
            IconButton(onClick = {
                scope.launch {
                    val campaignName = campaign?.campaignName ?: "Report"
                    saveReportAsCsv(context, filteredReportDetails, campaignName)
                }
            }) {
                Icon(
                    imageVector = Icons.Default.Download,
                    contentDescription = "Download Report as CSV",
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            // Fullscreen toggle button
            IconButton(onClick = onToggleFullScreen) {
                Icon(
                    imageVector = if (isFullScreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                    contentDescription = if (isFullScreen) "Exit Fullscreen" else "Enter Fullscreen",
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            // Font Size Controls with enhanced styling
            Card(
                modifier = Modifier.padding(horizontal = 4.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    IconButton(
                        onClick = { onFontSizeChange((fontSize.value - 1).sp) },
                        modifier = Modifier.size(32.dp),
                        enabled = fontSize > 8.sp
                    ) {
                        Icon(
                            Icons.Default.Remove,
                            contentDescription = "Decrease font size",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    Text(
                        "Font",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    IconButton(
                        onClick = { onFontSizeChange((fontSize.value + 1).sp) },
                        modifier = Modifier.size(32.dp),
                        enabled = fontSize < 24.sp
                    ) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = "Increase font size",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(4.dp))

            // Cell Width Controls with enhanced styling
            Card(
                modifier = Modifier.padding(horizontal = 4.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    IconButton(
                        onClick = { onCellWidthChange(cellWidth - 10.dp) },
                        modifier = Modifier.size(32.dp),
                        enabled = cellWidth > 50.dp
                    ) {
                        Icon(
                            Icons.Default.Remove,
                            contentDescription = "Decrease cell width",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    Text(
                        "Cell",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    IconButton(
                        onClick = { onCellWidthChange(cellWidth + 10.dp) },
                        modifier = Modifier.size(32.dp),
                        enabled = cellWidth < 300.dp
                    ) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = "Increase cell width",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
        )
    )
}

@Composable
fun EnhancedHeaderSection(
    campaign: Campaign,
    successRate: Float,
    animatedFloat: Float
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .shadow(8.dp, RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f)
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = campaign.campaignName,
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (campaign.campaignType == "SHEETSSEND") Icons.Default.Description else Icons.Default.Group,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = campaign.sheetFileName ?: campaign.groupId,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Schedule,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = formatTimestamp(campaign.timestamp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }
                }

                // Success Rate Indicator
                CircularSuccessIndicator(
                    successRate = successRate,
                    animatedFloat = animatedFloat
                )
            }
        }
    }
}

@Composable
fun CircularSuccessIndicator(
    successRate: Float,
    animatedFloat: Float
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(80.dp)
    ) {
        CircularProgressIndicator(
            progress = { (successRate / 100f) * animatedFloat },
            modifier = Modifier.size(70.dp),
            color = when {
                successRate >= 80f -> Color(0xFF10B981)
                successRate >= 60f -> Color(0xFFF59E0B)
                else -> Color(0xFFEF4444)
            },
            strokeWidth = 6.dp,
            trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
        )
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "${(successRate * animatedFloat).toInt()}%",
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = FontWeight.Bold
                ),
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Success",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
    }
}

@Composable
fun StatisticsCardsRow(
    totalContacts: Int,
    successCount: Int,
    failedCount: Int,
    pendingCount: Int,
    animatedFloat: Float
) {
    Row(
        // Increased spacing to fix overlapping appearance
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .horizontalScroll(rememberScrollState())
    ) {
        StatCard(
            title = "Total",
            value = (totalContacts * animatedFloat).toInt().toString(),
            icon = Icons.Default.People,
            color = Color(0xFF6366F1),
            gradientColors = listOf(Color(0xFF6366F1), Color(0xFF8B5CF6))
        )
        StatCard(
            title = "Success",
            value = (successCount * animatedFloat).toInt().toString(),
            icon = Icons.Default.CheckCircle,
            color = Color(0xFF10B981),
            gradientColors = listOf(Color(0xFF10B981), Color(0xFF34D399))
        )
        StatCard(
            title = "Failed",
            value = (failedCount * animatedFloat).toInt().toString(),
            icon = Icons.Default.Cancel,
            color = Color(0xFFEF4444),
            gradientColors = listOf(Color(0xFFEF4444), Color(0xFFF87171))
        )
        StatCard(
            title = "Pending",
            value = (pendingCount * animatedFloat).toInt().toString(),
            icon = Icons.Default.Schedule,
            color = Color(0xFFF59E0B),
            gradientColors = listOf(Color(0xFFF59E0B), Color(0xFFFBBF24))
        )
    }
}

@Composable
fun StatCard(
    title: String,
    value: String,
    icon: ImageVector,
    color: Color,
    gradientColors: List<Color>
) {
    Card(
        modifier = Modifier
            .width(120.dp)
            .height(100.dp)
            // Removed shadow, added a border for a clearer outline
            .border(1.dp, color.copy(alpha = 0.4f), RoundedCornerShape(12.dp)),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.Transparent
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(gradientColors.map { it.copy(alpha = 0.1f) })
                ),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(28.dp) // Slightly smaller icon to match smaller text
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = value,
                    // Reduced font size for a more compact look
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = title,
                    // Reduced font size for the title
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EnhancedFilterChipsRow(
    selectedFilter: ReportStatusFilter,
    onFilterChange: (ReportStatusFilter) -> Unit,
    reportDetails: List<ReportDetail>
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Restored FilterChip implementation
        ReportStatusFilter.values().forEach { filter ->
            val count = when (filter) {
                ReportStatusFilter.All -> reportDetails.size
                ReportStatusFilter.Success -> reportDetails.count { it.status == "Sent" }
                ReportStatusFilter.Failed -> reportDetails.count { isFailedReportStatus(it.status) }
                ReportStatusFilter.Pending -> reportDetails.count { it.status == "Pending" }
            }
            val isSelected = selectedFilter == filter
            FilterChip(
                selected = isSelected,
                onClick = { onFilterChange(filter) },
                label = { Text("${filter.statusName} ($count)") },
                leadingIcon = {
                    Icon(
                        imageVector = filter.icon,
                        contentDescription = filter.statusName,
                        modifier = Modifier.size(FilterChipDefaults.IconSize)
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = filter.color,
                    selectedLabelColor = Color.White,
                    selectedLeadingIconColor = Color.White
                ),
                border = FilterChipDefaults.filterChipBorder(
                    borderColor = if (isSelected) Color.Transparent else filter.color.copy(alpha = 0.5f)
                )
            )
        }
    }
}

@Composable
fun ErrorStateCard() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .shadow(4.dp, RoundedCornerShape(12.dp)),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFEF4444).copy(alpha = 0.1f)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.ErrorOutline,
                contentDescription = null,
                tint = Color(0xFFEF4444),
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Group Not Found",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold
                ),
                color = Color(0xFFEF4444),
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Could not load report details. The original contact group may have been deleted.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun EnhancedDataTable(
    filteredReportDetails: List<ReportDetail>,
    fontSize: TextUnit,
    cellWidth: Dp,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp)
            .shadow(8.dp, RoundedCornerShape(12.dp)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Box(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .background(MaterialTheme.colorScheme.surface)
        ) {
            LazyColumn(
                // Add content padding to improve scrolling performance
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                stickyHeader {
                    EnhancedSheetHeader(fontSize = fontSize, cellWidth = cellWidth)
                }
                items(
                    items = filteredReportDetails,
                    key = { it.serialNumber }
                ) { detail ->
                    EnhancedSheetRow(
                        detail = detail,
                        fontSize = fontSize,
                        cellWidth = cellWidth
                    )
                }

                // Add a footer item to show total count for large datasets
                if (filteredReportDetails.size > 1000) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                                .background(
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    RoundedCornerShape(8.dp)
                                )
                                .padding(12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Showing ${filteredReportDetails.size} contacts",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun EnhancedSheetHeader(fontSize: TextUnit, cellWidth: Dp) {
    Row(
        Modifier
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        EnhancedTableCell(text = "Sr. No.", width = cellWidth * 0.8f, isHeader = true, fontSize = fontSize)
        EnhancedTableCell(text = "Phone Number", width = cellWidth * 2.2f, isHeader = true, fontSize = fontSize)
        EnhancedTableCell(text = "Name", width = cellWidth * 2.5f, isHeader = true, fontSize = fontSize)
        EnhancedTableCell(text = "Status", width = cellWidth * 1.5f, isHeader = true, fontSize = fontSize)
    }
}

@Composable
fun EnhancedSheetRow(detail: ReportDetail, fontSize: TextUnit, cellWidth: Dp) {
    Row(
        Modifier
            .background(
                when (detail.serialNumber % 2) {
                    0 -> MaterialTheme.colorScheme.surface
                    else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                }
            )
    ) {
        EnhancedTableCell(text = detail.serialNumber.toString(), width = cellWidth * 0.8f, fontSize = fontSize)
        EnhancedTableCell(text = detail.contact.number, width = cellWidth * 2.2f, fontSize = fontSize)
        EnhancedTableCell(text = detail.contact.name, width = cellWidth * 2.5f, fontSize = fontSize)
        EnhancedTableCell(
            text = detail.status,
            width = cellWidth * 1.5f,
            textColor = detail.statusColor,
            fontSize = fontSize,
            icon = detail.statusIcon
        )
    }
}

@Composable
fun RowScope.EnhancedTableCell(
    text: String,
    width: Dp,
    isHeader: Boolean = false,
    textColor: Color = Color.Unspecified,
    fontSize: TextUnit,
    icon: ImageVector? = null
) {
    Box(
        modifier = Modifier
            .width(width)
            .border(
                0.5.dp,
                MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
            )
            .padding(horizontal = 8.dp, vertical = 12.dp),
        contentAlignment = if (isHeader) Alignment.Center else Alignment.CenterStart
    ) {
        if (icon != null && !isHeader) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = textColor,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = text,
                    color = textColor,
                    fontWeight = if (isHeader) FontWeight.Bold else FontWeight.Medium,
                    textAlign = if (isHeader) TextAlign.Center else TextAlign.Start,
                    overflow = TextOverflow.Ellipsis,
                    fontSize = fontSize,
                    maxLines = 1
                )
            }
        } else {
            Text(
                text = text,
                color = if (textColor == Color.Unspecified) {
                    if (isHeader) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface
                } else textColor,
                fontWeight = if (isHeader) FontWeight.Bold else FontWeight.Normal,
                textAlign = if (isHeader) TextAlign.Center else TextAlign.Start,
                overflow = TextOverflow.Ellipsis,
                fontSize = fontSize,
                maxLines = 1,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

private fun formatTimestamp(timestamp: Long): String {
    val sdf = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
    return sdf.format(Date(timestamp))
}


private fun FilterChipDefaults.filterChipBorder(borderColor: Color): BorderStroke? {
    return if (borderColor != Color.Transparent) {
        BorderStroke(1.dp, borderColor)
    } else {
        null
    }
}

@SuppressLint("ObsoleteSdkInt")
private suspend fun saveReportAsCsv(context: Context, details: List<ReportDetail>, campaignName: String) {
    val safeCampaignName = campaignName.replace(Regex("[^a-zA-Z0-9]"), "_")
    val fileName = "report_${safeCampaignName}_${System.currentTimeMillis()}.csv"
    val csvHeader = "\"Sr. No.\",\"Phone Number\",\"Name\",\"Status\"\n"
    val csvContent = details.joinToString(separator = "\n") {
        "\"${it.serialNumber}\",\"${it.contact.number}\",\"${it.contact.name}\",\"${it.status}\""
    }
    val fullCsv = csvHeader + csvContent

    withContext(Dispatchers.IO) {
        try {
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "text/csv")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val relativePath = Environment.DIRECTORY_DOCUMENTS + "/BulkSender/Reports"
                    put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
                }
            }

            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Files.getContentUri("external"), contentValues)

            uri?.let {
                resolver.openOutputStream(it)?.use { outputStream ->
                    outputStream.write(fullCsv.toByteArray())
                }
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "CSV saved to Documents/BulkSender/Reports", Toast.LENGTH_LONG).show()
                }
            } ?: throw Exception("MediaStore URI was null")

        } catch (e: Exception) {
            e.printStackTrace()
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Error saving CSV file", Toast.LENGTH_SHORT).show()
            }
        }
    }
}

