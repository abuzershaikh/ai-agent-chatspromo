package com.message.bulksend.bulksend

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.message.bulksend.components.CountryCodeSelector
import com.message.bulksend.contactmanager.ContactzActivity
import com.message.bulksend.contactmanager.Contact
import com.message.bulksend.contactmanager.Group
import com.message.bulksend.contactmanager.ContactsRepository
import com.message.bulksend.data.ContactStatus
import com.message.bulksend.db.AppDatabase
import com.message.bulksend.db.AutonomousSendQueueDao
import com.message.bulksend.db.Campaign
import com.message.bulksend.db.Setting
import com.message.bulksend.utils.AlarmPermissionHelper
import com.message.bulksend.utils.AccessibilityPermissionDialog
import com.message.bulksend.utils.CampaignAutoSendManager
import com.message.bulksend.utils.Country
import com.message.bulksend.utils.CountryCodeManager
import com.message.bulksend.utils.isAccessibilityServiceEnabled
import com.message.bulksend.utils.isPackageInstalled
import com.message.bulksend.bulksend.textcamp.AutonomousExecutionDashboardCard
import com.message.bulksend.bulksend.textcamp.AutonomousExecutionStats
import com.message.bulksend.bulksend.textcamp.TopBarWhatsAppSelector
import com.message.bulksend.bulksend.textcamp.buildAutonomousQueuePlan
import com.message.bulksend.bulksend.textcamp.computeNextRetryPlan
import com.message.bulksend.bulksend.textcamp.recommendedAutonomousDays
import com.message.bulksend.bulksend.textcamp.startAndEndOfToday
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URLEncoder
import java.util.Date
import java.util.Calendar
import java.util.Locale
import java.text.SimpleDateFormat
import java.util.UUID
import kotlin.math.ceil
import kotlin.math.roundToInt

class AutonomousBulkSendActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AutonomousSenderTheme {
                AutonomousBulkSendScreen()
            }
        }
    }
}

@Composable
fun AutonomousSenderTheme(content: @Composable () -> Unit) {
    val colors = darkColorScheme(
        primary = Color(0xFF00E5FF),
        primaryContainer = Color(0xFF00B8D4),
        secondary = Color(0xFF7C4DFF),
        surface = Color(0xFF0D0D1A),
        surfaceVariant = Color(0xFF131326),
        background = Color(0xFF080813),
        onPrimary = Color(0xFF000000),
        onSurface = Color(0xFFE0E8FF),
        onBackground = Color(0xFFE0E8FF),
        outline = Color(0xFF2A2A4A),
    )
    MaterialTheme(colorScheme = colors) { content() }
}

// === AI Risk & Schedule Logic ===

data class AiScheduleResult(
    val bestSendWindow: String,
    val estimatedHours: Int,
    val riskScore: Int,         // 0-100
    val riskLabel: String,
    val delayMinSec: Int,
    val delayMaxSec: Int,
    val safeTimeStart: String,
    val safeTimeEnd: String,
    val totalMessages: Int,
    val aiInsight: String,
    val selectedDays: Int = 1,
    val suggestedMinimumDays: Int = 1,
    val averagePerDay: Int = 0,
    val activeHoursPerDay: Int = 0,
    val hourlyMin: Int = 5,
    val hourlyMax: Int = 7,
    val sampleDistribution: String = "",
    val isOverSafeLimit: Boolean = false
)

fun calculateAiSchedule(
    totalContacts: Int,
    riskLevel: String, // "Low", "Medium", "Aggressive"
    preferredWindow: String // "Morning", "Afternoon", "Evening", "Any"
): AiScheduleResult {
    val calendar = Calendar.getInstance()
    val hour = calendar.get(Calendar.HOUR_OF_DAY)
    val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK) // 1=Sun, 7=Sat

    val isWeekend = dayOfWeek == Calendar.SATURDAY || dayOfWeek == Calendar.SUNDAY

    // Delay ranges in seconds per risk level
    val (delayMin, delayMax) = when (riskLevel) {
        "Low" -> Pair(45, 180)
        "Medium" -> Pair(20, 60)
        "Aggressive" -> Pair(8, 20)
        else -> Pair(45, 180)
    }

    // Risk score calculation
    val baseRisk = when (riskLevel) {
        "Low" -> 15
        "Medium" -> 45
        "Aggressive" -> 80
        else -> 15
    }
    val timeRiskBonus = when {
        hour in 0..6 || hour in 22..23 -> 25   // night hours = high risk
        hour in 9..12 || hour in 15..18 -> 0   // golden hours = no penalty
        else -> 10
    }
    val weekendBonus = if (isWeekend) 10 else 0
    val volumeBonus = when {
        totalContacts > 500 -> 15
        totalContacts > 200 -> 8
        else -> 0
    }
    val riskScore = (baseRisk + timeRiskBonus + weekendBonus + volumeBonus).coerceIn(0, 100)

    val riskLabel = when {
        riskScore < 30 -> "Safe 🟢"
        riskScore < 60 -> "Moderate 🟡"
        riskScore < 80 -> "High Risk 🟠"
        else -> "Very High ⚠️"
    }

    // Best window selection
    val (safeStart, safeEnd) = when (preferredWindow) {
        "Morning" -> Pair("9:00 AM", "12:00 PM")
        "Afternoon" -> Pair("2:00 PM", "5:00 PM")
        "Evening" -> Pair("6:00 PM", "9:00 PM")
        else -> when {
            isWeekend -> Pair("11:00 AM", "7:00 PM")
            else -> Pair("9:00 AM", "6:00 PM")
        }
    }

    val bestWindow = when {
        hour in 9..11 -> "Right Now (Morning - Best time!)"
        hour in 14..17 -> "Right Now (Afternoon - Great time!)"
        hour in 18..21 -> "Right Now (Evening - Good time)"
        hour in 0..6 -> "Wait until Morning (9 AM recommended)"
        else -> "Good to go - $safeStart to $safeEnd"
    }

    // Estimated hours
    val avgDelaySec = (delayMin + delayMax) / 2.0
    val totalSec = totalContacts * avgDelaySec
    val estimatedHours = (totalSec / 3600).roundToInt().coerceAtLeast(1)

    // AI Insight
    val aiInsight = when {
        riskScore < 30 -> "✅ Excellent conditions. AI has selected optimal delay spacing to reduce pattern detection risk."
        riskScore < 60 -> "⚡ Good conditions with moderate volume. AI will apply smart delay variance to stay under WhatsApp's radar."
        riskScore < 80 -> "⚠️ Higher risk detected due to volume or timing. Consider using Low risk mode or splitting the campaign."
        else -> "🚨 Very high risk! Recommend splitting contacts into smaller batches and scheduling for morning hours."
    }

    return AiScheduleResult(
        bestSendWindow = bestWindow,
        estimatedHours = estimatedHours,
        riskScore = riskScore,
        riskLabel = riskLabel,
        delayMinSec = delayMin,
        delayMaxSec = delayMax,
        safeTimeStart = safeStart,
        safeTimeEnd = safeEnd,
        totalMessages = totalContacts,
        aiInsight = aiInsight
    )
}

fun calculateAdaptiveAiSchedule(
    totalContacts: Int,
    selectedDays: Int
): AiScheduleResult {
    val safeDailyLimit = 24
    val hourlyMin = 5
    val hourlyMax = 7
    val days = selectedDays.coerceAtLeast(1)

    if (totalContacts <= 0) {
        return AiScheduleResult(
            bestSendWindow = "Auto",
            estimatedHours = 0,
            riskScore = 0,
            riskLabel = "Safe",
            delayMinSec = 0,
            delayMaxSec = 0,
            safeTimeStart = "Auto",
            safeTimeEnd = "Auto",
            totalMessages = 0,
            aiInsight = "Select contacts and days. App will auto-randomize send timing.",
            selectedDays = days,
            suggestedMinimumDays = 1,
            averagePerDay = 0,
            activeHoursPerDay = 0,
            hourlyMin = hourlyMin,
            hourlyMax = hourlyMax,
            sampleDistribution = "No contacts selected.",
            isOverSafeLimit = false
        )
    }

    val suggestedMinimumDays = ceil(totalContacts / safeDailyLimit.toDouble()).toInt().coerceAtLeast(1)
    val averagePerDay = ceil(totalContacts / days.toDouble()).toInt().coerceAtLeast(1)
    val activeHoursPerDay = ceil(averagePerDay / ((hourlyMin + hourlyMax) / 2.0)).toInt().coerceAtLeast(1)
    val loadFactor = averagePerDay / safeDailyLimit.toDouble()
    val isOverSafeLimit = averagePerDay > safeDailyLimit

    var riskScore = when {
        loadFactor <= 1.0 -> (15 + (loadFactor * 12)).roundToInt()
        loadFactor <= 1.5 -> (28 + ((loadFactor - 1.0) * 42)).roundToInt()
        loadFactor <= 2.5 -> (50 + ((loadFactor - 1.5) * 25)).roundToInt()
        else -> (75 + ((loadFactor - 2.5) * 12)).roundToInt()
    }.coerceIn(0, 100)

    if (days < suggestedMinimumDays) {
        val shortageFactor = (suggestedMinimumDays - days) / suggestedMinimumDays.toDouble()
        riskScore = (riskScore + (shortageFactor * 35).roundToInt()).coerceIn(0, 100)
    }

    val riskLabel = when {
        riskScore < 30 -> "Safe"
        riskScore < 60 -> "Moderate"
        riskScore < 80 -> "High Risk"
        else -> "Very High Risk"
    }

    val random = kotlin.random.Random(seed = totalContacts * 37 + days * 17)
    val dayTargets = createRandomDayTargets(totalContacts, days, random)
    val sampleDistribution = buildSampleHourlyDistribution(
        messagesForDay = dayTargets.firstOrNull() ?: averagePerDay,
        random = random,
        hourlyMin = hourlyMin,
        hourlyMax = hourlyMax
    )

    val aiInsight = when {
        days < suggestedMinimumDays ->
            "Risk warning: contacts are too many for selected days. Choose at least $suggestedMinimumDays days for safer sending."
        isOverSafeLimit ->
            "Daily load is above safe baseline (24/day). AI will still randomize, but risk is higher."
        riskScore < 30 ->
            "Safe plan. App auto-distributes messages randomly with hourly caps."
        riskScore < 60 ->
            "Balanced plan. Keep monitoring responses and avoid reducing campaign days."
        else ->
            "High-risk plan. Increase campaign days to reduce daily pressure."
    }

    return AiScheduleResult(
        bestSendWindow = "Auto Randomized",
        estimatedHours = activeHoursPerDay * days,
        riskScore = riskScore,
        riskLabel = riskLabel,
        delayMinSec = 0,
        delayMaxSec = 0,
        safeTimeStart = "Auto",
        safeTimeEnd = "Auto",
        totalMessages = totalContacts,
        aiInsight = aiInsight,
        selectedDays = days,
        suggestedMinimumDays = suggestedMinimumDays,
        averagePerDay = averagePerDay,
        activeHoursPerDay = activeHoursPerDay,
        hourlyMin = hourlyMin,
        hourlyMax = hourlyMax,
        sampleDistribution = sampleDistribution,
        isOverSafeLimit = isOverSafeLimit
    )
}

private fun createRandomDayTargets(
    totalContacts: Int,
    days: Int,
    random: kotlin.random.Random
): List<Int> {
    if (days <= 1) return listOf(totalContacts)

    val weights = List(days) { random.nextDouble(0.85, 1.20) }
    val weightSum = weights.sum()
    val rawTargets = weights.map { (it / weightSum) * totalContacts }
    val dayTargets = rawTargets.map { it.toInt() }.toMutableList()

    var remaining = totalContacts - dayTargets.sum()
    val fractionalOrder = rawTargets
        .mapIndexed { index, value -> index to (value - value.toInt()) }
        .sortedByDescending { it.second }

    var pointer = 0
    while (remaining > 0) {
        dayTargets[fractionalOrder[pointer % fractionalOrder.size].first] += 1
        remaining--
        pointer++
    }

    if (totalContacts >= days) {
        dayTargets.indices.forEach { index ->
            if (dayTargets[index] == 0) {
                val donor = dayTargets.indices.maxByOrNull { dayTargets[it] } ?: -1
                if (donor >= 0 && dayTargets[donor] > 1) {
                    dayTargets[donor] -= 1
                    dayTargets[index] = 1
                }
            }
        }
    }

    return dayTargets
}

private fun buildSampleHourlyDistribution(
    messagesForDay: Int,
    random: kotlin.random.Random,
    hourlyMin: Int,
    hourlyMax: Int
): String {
    if (messagesForDay <= 0) return "No messages for Day 1."

    val availableHours = (8..22).toMutableList().apply { shuffle(random) }
    val maxDailyCapacity = availableHours.size * hourlyMax
    val previewMessages = messagesForDay.coerceAtMost(maxDailyCapacity)
    val overflowMessages = (messagesForDay - previewMessages).coerceAtLeast(0)
    val slotCount = ceil(previewMessages / hourlyMax.toDouble()).toInt()
        .coerceAtLeast(1)
        .coerceAtMost(availableHours.size)
    val selectedHours = availableHours.take(slotCount).sorted()

    var remaining = previewMessages
    val slots = mutableListOf<Pair<Int, Int>>()

    selectedHours.forEachIndexed { index, hour ->
        val slotsLeft = selectedHours.size - index
        val minNeededForRemainingSlots = slotsLeft - 1
        val minAllowed = maxOf(1, remaining - ((slotsLeft - 1) * hourlyMax))
        val maxAllowed = minOf(hourlyMax, remaining - minNeededForRemainingSlots)
        val preferredMin = minOf(hourlyMin, maxAllowed).coerceAtLeast(minAllowed)

        val count = if (slotsLeft == 1) {
            remaining
        } else if (preferredMin >= maxAllowed) {
            preferredMin
        } else {
            random.nextInt(preferredMin, maxAllowed + 1)
        }

        slots += hour to count
        remaining -= count
    }

    val distribution = slots.joinToString("  |  ") { (hour, count) ->
        String.format("%02d:00 -> %d", hour, count)
    }

    return if (overflowMessages > 0) {
        "$distribution  |  +$overflowMessages overflow (needs more days)"
    } else {
        distribution
    }
}

fun makeMessageUnique(base: String, contactName: String, index: Int): String {
    val greetings = listOf("Hey", "Hello", "Hi", "Greetings", "Hii", "Hey there")
    val closings = listOf("👍", "✅", "🙏", "😊", "🌟", "💬")
    val randomGreeting = greetings[index % greetings.size]
    val randomClosing = closings[index % closings.size]
    // Generate an invisible uniqueness marker using zero-width characters
    val zwsp = "\u200B".repeat((index % 5) + 1)
    val personalised = base
        .replace("#name#", contactName, ignoreCase = true)
        .replace("{name}", contactName, ignoreCase = true)
    return "$zwsp$personalised $randomClosing"
}

// ======= Screen Composable =======

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutonomousBulkSendScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val db = remember { AppDatabase.getInstance(context) }
    val campaignDao = remember { db.campaignDao() }
    val settingDao = remember { db.settingDao() }
    val autonomousQueueDao = remember { db.autonomousSendQueueDao() }
    val contactsRepository = remember { ContactsRepository(context) }
    val groups by contactsRepository.loadGroups().collectAsState(initial = emptyList())

    var selectedGroup by remember { mutableStateOf<Group?>(null) }
    var selectedCountry by remember { mutableStateOf(CountryCodeManager.getOrAutoDetectCountry(context)) }
    var message by remember { mutableStateOf("") }
    var selectedDaysSlider by remember { mutableFloatStateOf(3f) }
    var showGroupSheet by remember { mutableStateOf(false) }
    var whatsAppPreference by remember { mutableStateOf("WhatsApp") }
    var isSending by remember { mutableStateOf(false) }
    var campaignProgress by remember { mutableStateOf(0f) }
    var sendingIndex by remember { mutableStateOf(0) }
    var currentCampaignId by remember { mutableStateOf<String?>(null) }
    var campaignStatus by remember { mutableStateOf<List<ContactStatus>>(emptyList()) }
    var autonomousStats by remember { mutableStateOf(AutonomousExecutionStats()) }
    var autoPauseReason by remember { mutableStateOf<String?>(null) }
    var campaignError by remember { mutableStateOf<String?>(null) }
    var resumableProgress by remember { mutableStateOf<Campaign?>(null) }
    var campaignLockState by remember { mutableStateOf<AutonomousCampaignLockState?>(null) }
    var showAccessibilityDialog by remember { mutableStateOf(false) }
    var showRiskDialog by remember { mutableStateOf(false) }
    var riskOverrideAccepted by remember { mutableStateOf(false) }
    var isWhatsAppSelectorExpanded by remember { mutableStateOf(false) }
    var showAlarmPermissionDialog by remember { mutableStateOf(false) }
    var hasProgressRedirected by remember { mutableStateOf(false) }
    val selectedDays = selectedDaysSlider.roundToInt().coerceIn(1, 30)

    val contactzLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { /* group list updates via Flow */ }

    val scheduleResult = remember(selectedGroup, selectedDays) {
        calculateAdaptiveAiSchedule(
            totalContacts = selectedGroup?.contacts?.size ?: 0,
            selectedDays = selectedDays
        )
    }

    LaunchedEffect(Unit) {
        val pref = withContext(Dispatchers.IO) { settingDao.getSetting("whatsapp_preference") }
        whatsAppPreference = pref?.value ?: "WhatsApp"
    }

    LaunchedEffect(Unit) {
        val activityIntent = (context as? Activity)?.intent
        val targetCampaignId =
            activityIntent?.getStringExtra(AutonomousCampaignExecutionService.EXTRA_CAMPAIGN_ID)
                ?: AutonomousCampaignConfigStore.getActiveCampaignId(context)
        if (targetCampaignId.isNullOrBlank()) return@LaunchedEffect

        val activeCampaign = withContext(Dispatchers.IO) {
            campaignDao.getCampaignById(targetCampaignId)
        } ?: return@LaunchedEffect

        if (activeCampaign.campaignType != "BULKTEXT_AUTONOMOUS") return@LaunchedEffect

        currentCampaignId = activeCampaign.id
        isSending = activeCampaign.isRunning
        campaignStatus = activeCampaign.contactStatuses
        val hasQueuedMessages = withContext(Dispatchers.IO) {
            autonomousQueueDao.countByStatus(activeCampaign.id, "queued") > 0
        }
        if (!activeCampaign.isRunning && activeCampaign.isStopped) {
            resumableProgress = activeCampaign
        }
        if (activeCampaign.isRunning || activeCampaign.isStopped || hasQueuedMessages) {
            hasProgressRedirected = true
            context.startActivity(
                AutonomousCampaignProgressActivity.createIntent(context, activeCampaign.id).apply {
                    addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
            (context as? Activity)?.finish()
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            val lockedCampaign = withContext(Dispatchers.IO) {
                loadAutonomousCampaignLockState(campaignDao, autonomousQueueDao)
            }
            campaignLockState = lockedCampaign
            resumableProgress = lockedCampaign?.campaign

            val runningCampaign = lockedCampaign?.campaign?.takeIf { it.isRunning }
            if (currentCampaignId == null && runningCampaign != null) {
                currentCampaignId = runningCampaign.id
                isSending = true
                campaignStatus = runningCampaign.contactStatuses
            }
            if (lockedCampaign == null && currentCampaignId == null) {
                resumableProgress = null
            }
            delay(2500)
        }
    }

    LaunchedEffect(groups, campaignLockState?.campaign?.groupId) {
        val lockedGroupId = campaignLockState?.campaign?.groupId ?: return@LaunchedEffect
        val lockedGroup = groups.find { it.id.toString() == lockedGroupId } ?: return@LaunchedEffect
        if (selectedGroup?.id != lockedGroup.id) {
            selectedGroup = lockedGroup
        }
    }

    LaunchedEffect(campaignLockState?.campaign?.id) {
        val activeLock = campaignLockState ?: run {
            hasProgressRedirected = false
            return@LaunchedEffect
        }
        if (hasProgressRedirected) return@LaunchedEffect

        hasProgressRedirected = true
        context.startActivity(
            AutonomousCampaignProgressActivity.createIntent(context, activeLock.campaign.id).apply {
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )
        (context as? Activity)?.finish()
    }

    LaunchedEffect(currentCampaignId) {
        val campaignId = currentCampaignId ?: return@LaunchedEffect
        while (currentCampaignId == campaignId) {
            val latestState = withContext(Dispatchers.IO) {
                campaignDao.getCampaignById(campaignId)
            } ?: run {
                isSending = false
                currentCampaignId = null
                return@LaunchedEffect
            }

            campaignStatus = latestState.contactStatuses
            val queuedCount = withContext(Dispatchers.IO) {
                autonomousQueueDao.countByStatus(campaignId, "queued")
            }

            isSending = latestState.isRunning
            if (!latestState.isRunning) {
                resumableProgress = if (latestState.isStopped || queuedCount > 0) {
                    latestState
                } else {
                    null
                }
                if (queuedCount == 0 && !latestState.isStopped) {
                    AutonomousCampaignConfigStore.clearConfig(context, campaignId)
                    currentCampaignId = null
                }
                return@LaunchedEffect
            }

            delay(3000)
        }
    }

    LaunchedEffect(isSending, currentCampaignId, autoPauseReason) {
        if (!isSending || currentCampaignId.isNullOrBlank()) return@LaunchedEffect
        while (isSending && !currentCampaignId.isNullOrBlank()) {
            autonomousStats = withContext(Dispatchers.IO) {
                loadAutonomousExecutionStatsForAutonomousCampaign(
                    dao = autonomousQueueDao,
                    campaignId = currentCampaignId!!,
                    pauseReason = autoPauseReason
                )
            }
            delay(3000)
        }
    }

    LaunchedEffect(selectedGroup?.id) {
        riskOverrideAccepted = false
    }

    val bgBrush = Brush.verticalGradient(
        colors = listOf(
            Color(0xFF080813),
            Color(0xFF0D0D23),
            Color(0xFF080813)
        )
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},

                navigationIcon = {
                    IconButton(onClick = { (context as? Activity)?.finish() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color(0xFF00E5FF))
                    }
                },
                actions = {
                    // Compact WhatsApp selector — shows short label to avoid title overflow
                    val waOptions = listOf("WhatsApp Business", "WhatsApp")
                    val waShortLabel = if (whatsAppPreference == "WhatsApp Business") "WA Biz" else "WA"
                    Box(modifier = Modifier.padding(end = 4.dp)) {
                        androidx.compose.material3.ExposedDropdownMenuBox(
                            expanded = isWhatsAppSelectorExpanded,
                            onExpandedChange = { isWhatsAppSelectorExpanded = it }
                        ) {
                            Row(
                                modifier = Modifier
                                    .menuAnchor()
                                    .clickable { isWhatsAppSelectorExpanded = !isWhatsAppSelectorExpanded }
                                    .background(Color(0xFF00E5FF).copy(alpha = 0.1f), RoundedCornerShape(50))
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Chat,
                                    contentDescription = null,
                                    tint = Color(0xFF00E5FF),
                                    modifier = Modifier.size(15.dp)
                                )
                                Text(
                                    text = waShortLabel,
                                    color = Color(0xFF00E5FF),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Icon(
                                    imageVector = if (isWhatsAppSelectorExpanded) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown,
                                    contentDescription = null,
                                    tint = Color(0xFF00E5FF),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            DropdownMenu(
                                expanded = isWhatsAppSelectorExpanded,
                                onDismissRequest = { isWhatsAppSelectorExpanded = false },
                                modifier = Modifier.background(Color(0xFF0D0D1A))
                            ) {
                                waOptions.forEach { option ->
                                    DropdownMenuItem(
                                        text = { Text(option, color = Color(0xFFE0E8FF)) },
                                        onClick = {
                                            whatsAppPreference = option
                                            isWhatsAppSelectorExpanded = false
                                            scope.launch(Dispatchers.IO) {
                                                settingDao.upsertSetting(Setting("whatsapp_preference", option))
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF0D0D1A))
            )
        },
        containerColor = Color.Transparent
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(bgBrush)
        ) {
            LazyColumn(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {

                // Step 1: Select contacts
                item {
                    AiStepCard(
                        stepNumber = 1,
                        title = "Select Contacts",
                        icon = Icons.Outlined.Groups,
                        isCompleted = selectedGroup != null,
                        accentColor = Color(0xFF00E5FF)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            if (selectedGroup != null) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(Color(0xFF00E5FF).copy(alpha = 0.08f))
                                        .border(1.dp, Color(0xFF00E5FF).copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            selectedGroup!!.name,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF00E5FF),
                                            fontSize = 14.sp
                                        )
                                        Text(
                                            "${selectedGroup!!.contacts.size} contacts selected",
                                            fontSize = 12.sp,
                                            color = Color.White.copy(alpha = 0.6f)
                                        )
                                    }
                                    Icon(Icons.Filled.CheckCircle, null, tint = Color(0xFF00C853), modifier = Modifier.size(22.dp))
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Button(
                                    onClick = { showGroupSheet = true },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF).copy(alpha = 0.15f))
                                ) {
                                    Icon(Icons.Outlined.Groups, null, tint = Color(0xFF00E5FF), modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Choose Group", color = Color(0xFF00E5FF), fontSize = 13.sp)
                                }
                                OutlinedButton(
                                    onClick = { contactzLauncher.launch(Intent(context, ContactzActivity::class.java)) },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(10.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF7C4DFF))
                                ) {
                                    Icon(Icons.Outlined.GroupAdd, null, tint = Color(0xFF7C4DFF), modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("New List", color = Color(0xFF7C4DFF), fontSize = 13.sp)
                                }
                            }
                            CountryCodeSelector(
                                selectedCountry = selectedCountry,
                                onCountrySelected = { country ->
                                    selectedCountry = country
                                }
                            )
                        }
                    }
                }

                // Step 2: Message
                item {
                    AiStepCard(
                        stepNumber = 2,
                        title = "Write Your Message",
                        icon = Icons.Outlined.Message,
                        isCompleted = message.isNotBlank(),
                        accentColor = Color(0xFF7C4DFF)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedTextField(
                                value = message,
                                onValueChange = { message = it },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 110.dp),
                                placeholder = {
                                    Text(
                                        "Type your message here...\nUse #name# for personalization",
                                        color = Color.White.copy(alpha = 0.35f),
                                        fontSize = 13.sp
                                    )
                                },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color(0xFF7C4DFF),
                                    unfocusedBorderColor = Color(0xFF2A2A4A),
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    cursorColor = Color(0xFF7C4DFF)
                                ),
                                shape = RoundedCornerShape(12.dp)
                            )
                            /* Unique message toggle removed
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(
                                        if (uniqueMessages) Color(0xFF7C4DFF).copy(alpha = 0.1f)
                                        else Color(0xFF1A1A2E)
                                    )
                                    .border(
                                        1.dp,
                                        if (uniqueMessages) Color(0xFF7C4DFF).copy(alpha = 0.4f) else Color(0xFF2A2A4A),
                                        RoundedCornerShape(10.dp)
                                    )
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        "🤖 AI Unique Messages",
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color.White,
                                        fontSize = 13.sp
                                    )
                                    Text(
                                        "Each message gets a unique signature — avoids spam detection",
                                        fontSize = 11.sp,
                                        color = Color.White.copy(alpha = 0.5f)
                                    )
                                }
                                Switch(
                                    checked = uniqueMessages,
                                    onCheckedChange = { uniqueMessages = it },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color(0xFF7C4DFF),
                                        checkedTrackColor = Color(0xFF7C4DFF).copy(alpha = 0.3f),
                                        uncheckedThumbColor = Color.Gray,
                                        uncheckedTrackColor = Color.Gray.copy(alpha = 0.2f),
                                        uncheckedBorderColor = Color.Gray.copy(alpha = 0.4f)
                                    )
                                )
                            }
                            */
                        }
                    }
                }

                // Step 3: AI settings
                item {
                    AiStepCard(
                        stepNumber = 3,
                        title = "AI Sending Settings",
                        icon = Icons.Outlined.Tune,
                        isCompleted = true,
                        accentColor = Color(0xFFFF6D00)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            // Campaign day selector
                            Text(
                                "Campaign Duration",
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White.copy(alpha = 0.8f),
                                fontSize = 13.sp
                            )
                            Text(
                                "$selectedDays day(s)",
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFFFC107),
                                fontSize = 16.sp
                            )
                            Slider(
                                value = selectedDaysSlider,
                                onValueChange = { selectedDaysSlider = it },
                                valueRange = 1f..30f,
                                steps = 28,
                                colors = SliderDefaults.colors(
                                    thumbColor = Color(0xFFFFC107),
                                    activeTrackColor = Color(0xFFFFC107),
                                    inactiveTrackColor = Color(0xFF2A2A4A)
                                )
                            )
                            Text(
                                if (scheduleResult.totalMessages > 0)
                                    "Recommended minimum: ${scheduleResult.suggestedMinimumDays} day(s) for ${scheduleResult.totalMessages} contacts (safe baseline 24/day)."
                                else
                                    "Select contacts first to get AI day recommendation.",
                                fontSize = 11.sp,
                                color = Color.White.copy(alpha = 0.6f)
                            )
                            if (scheduleResult.totalMessages > 0 && selectedDays < scheduleResult.suggestedMinimumDays) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(Color(0xFFFF5252).copy(alpha = 0.12f))
                                        .border(1.dp, Color(0xFFFF5252).copy(alpha = 0.35f), RoundedCornerShape(10.dp))
                                        .padding(10.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Outlined.Warning, null, tint = Color(0xFFFF8A80), modifier = Modifier.size(16.dp))
                                    Text(
                                        "Risk is high for selected days. Increase days to reduce account risk.",
                                        fontSize = 11.sp,
                                        color = Color(0xFFFFCDD2)
                                    )
                                }
                            }

                            HorizontalDivider(color = Color(0xFF2A2A4A))

                            Text(
                                "Auto-timing is enabled. App chooses random send hours and random message gaps.",
                                fontSize = 12.sp,
                                color = Color.White.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
                // Step 4: AI Schedule Summary
                item {
                    AiScheduleSummaryCard(schedule = scheduleResult)
                }

                if (selectedGroup != null) {
                    item {
                        AutonomousExecutionDashboardCard(
                            stats = autonomousStats,
                            recommendedDays = scheduleResult.suggestedMinimumDays,
                            selectedDays = selectedDays,
                            riskScore = scheduleResult.riskScore
                        )
                    }
                }

                // Launch Button
                item {
                    AiLaunchButton(
                        enabled =
                            selectedGroup != null &&
                                message.isNotBlank() &&
                                !isSending &&
                                campaignLockState == null,
                        isRunning = isSending,
                        onClick = {
                            val group = selectedGroup
                            if (group == null) {
                                Toast.makeText(context, "Please select a contact group", Toast.LENGTH_SHORT).show()
                                return@AiLaunchButton
                            }
                            if (message.isBlank()) {
                                Toast.makeText(context, "Please write your message", Toast.LENGTH_SHORT).show()
                                return@AiLaunchButton
                            }
                            val country = selectedCountry
                            if (country == null) {
                                Toast.makeText(context, "Please select a country code", Toast.LENGTH_SHORT).show()
                                return@AiLaunchButton
                            }
                            val activeLock = campaignLockState
                            if (activeLock != null) {
                                campaignError =
                                    "Only one autonomous campaign can stay active at a time. Reset or complete '${activeLock.campaign.campaignName}' first."
                                return@AiLaunchButton
                            }
                            if (
                                scheduleResult.totalMessages > 0 &&
                                selectedDays < scheduleResult.suggestedMinimumDays &&
                                !riskOverrideAccepted
                            ) {
                                showRiskDialog = true
                                return@AiLaunchButton
                            }

                            if (!isAccessibilityServiceEnabled(context)) {
                                showAccessibilityDialog = true
                                return@AiLaunchButton
                            }

                            if (!AlarmPermissionHelper.canScheduleExactAlarms(context)) {
                                showAlarmPermissionDialog = true
                                return@AiLaunchButton
                            }

                            val packageName = when (whatsAppPreference) {
                                "WhatsApp" -> "com.whatsapp"
                                "WhatsApp Business" -> "com.whatsapp.w4b"
                                else -> null
                            }
                            val hasAnyWhatsAppInstalled =
                                isPackageInstalled(context, "com.whatsapp") ||
                                    isPackageInstalled(context, "com.whatsapp.w4b")
                            if (!hasAnyWhatsAppInstalled) {
                                campaignError = "Install WhatsApp or WhatsApp Business on this device first."
                                return@AiLaunchButton
                            }
                            if (packageName != null && !isPackageInstalled(context, packageName) && !hasAnyWhatsAppInstalled) {
                                campaignError = "$whatsAppPreference is not installed on this device."
                                return@AiLaunchButton
                            }

                            scope.launch {
                                try {
                                    val resumableCampaign =
                                        campaignLockState?.campaign?.takeIf {
                                            it.groupId == group.id.toString() &&
                                                it.campaignType == "BULKTEXT_AUTONOMOUS"
                                        }

                                    val campaignToRun =
                                        if (resumableCampaign != null) {
                                            resumableCampaign.copy(
                                                message = message,
                                                isStopped = false,
                                                isRunning = true,
                                                countryCode = country.dial_code
                                            )
                                        } else {
                                            Campaign(
                                                id = UUID.randomUUID().toString(),
                                                groupId = group.id.toString(),
                                                campaignName = "Autonomous_${group.name}",
                                                message = message,
                                                timestamp = System.currentTimeMillis(),
                                                totalContacts = group.contacts.size,
                                                contactStatuses =
                                                    group.contacts.map {
                                                        ContactStatus(it.number, "pending")
                                                    },
                                                isStopped = false,
                                                isRunning = true,
                                                campaignType = "BULKTEXT_AUTONOMOUS",
                                                countryCode = country.dial_code
                                            )
                                        }

                                    val queuedEntries =
                                        withContext(Dispatchers.IO) {
                                            campaignDao.upsertCampaign(campaignToRun)
                                            var existingQueue =
                                                autonomousQueueDao.getQueuedForCampaign(campaignToRun.id)
                                            val shouldRebuildUntouchedQueue =
                                                resumableCampaign != null &&
                                                    campaignLockState?.campaign?.id == campaignToRun.id &&
                                                    campaignLockState?.sentCount == 0 &&
                                                    campaignLockState?.failedCount == 0 &&
                                                    existingQueue.isNotEmpty()
                                            if (shouldRebuildUntouchedQueue) {
                                                autonomousQueueDao.deleteForCampaign(campaignToRun.id)
                                                existingQueue = emptyList()
                                            }
                                            if (existingQueue.isEmpty()) {
                                                val pendingContacts =
                                                    campaignToRun.contactStatuses
                                                        .filter { it.status == "pending" }
                                                        .mapNotNull { status ->
                                                            group.contacts.find { it.number == status.number }
                                                        }
                                                if (pendingContacts.isNotEmpty()) {
                                                    val plan =
                                                        buildAutonomousQueuePlan(
                                                            campaignId = campaignToRun.id,
                                                            contacts = pendingContacts,
                                                            selectedDays = selectedDays.coerceAtLeast(1)
                                                        )
                                                    autonomousQueueDao.upsertAll(plan)
                                                }
                                            }
                                            autonomousQueueDao.getQueuedForCampaign(campaignToRun.id)
                                        }

                                    if (queuedEntries.isEmpty()) {
                                        isSending = false
                                        campaignError = "No pending contacts were available for this autonomous campaign."
                                        return@launch
                                    }

                                    val scheduler = AutonomousCampaignScheduler(context)
                                    val nextQueued = queuedEntries.first()

                                    AutonomousCampaignConfigStore.saveConfig(
                                        context = context,
                                        campaignId = campaignToRun.id,
                                        config = AutonomousCampaignRuntimeConfig(
                                            countryCode = country.dial_code,
                                            whatsAppPreference = whatsAppPreference
                                        )
                                    )
                                    AutonomousCampaignConfigStore.setActiveCampaignId(
                                        context,
                                        campaignToRun.id
                                    )

                                    CampaignAutoSendManager.onCampaignLaunched(campaignToRun)
                                    currentCampaignId = campaignToRun.id
                                    resumableProgress = campaignToRun
                                    campaignStatus = campaignToRun.contactStatuses
                                    isSending = true
                                    autoPauseReason = null
                                    autonomousStats =
                                        withContext(Dispatchers.IO) {
                                            loadAutonomousExecutionStatsForAutonomousCampaign(
                                                dao = autonomousQueueDao,
                                                campaignId = campaignToRun.id,
                                                pauseReason = null
                                            )
                                        }

                                    if (nextQueued.plannedTimeMillis <= System.currentTimeMillis() + 15_000L) {
                                        AutonomousCampaignExecutionService.startForCampaign(
                                            context = context,
                                            campaignId = campaignToRun.id,
                                            source = AutonomousCampaignExecutionService.SOURCE_ACTIVITY_START
                                        )
                                    } else {
                                        scheduler.scheduleNextExecution(
                                            campaignId = campaignToRun.id,
                                            triggerAtMillis = nextQueued.plannedTimeMillis
                                        )
                                    }

                                    Toast.makeText(
                                        context,
                                        "Autonomous campaign scheduled. It will continue even if you close the app.",
                                        Toast.LENGTH_LONG
                                    ).show()
                                    context.startActivity(
                                        AutonomousCampaignProgressActivity.createIntent(
                                            context,
                                            campaignToRun.id
                                        ).apply {
                                            addFlags(
                                                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                                                    Intent.FLAG_ACTIVITY_NEW_TASK
                                            )
                                        }
                                    )
                                    (context as? Activity)?.finish()
                                } catch (securityException: SecurityException) {
                                    isSending = false
                                    currentCampaignId = null
                                    campaignError =
                                        "Allow exact alarms so autonomous scheduling can keep working after the app is closed."
                                } catch (e: Exception) {
                                    isSending = false
                                    currentCampaignId = null
                                    campaignError = e.message ?: "Failed to schedule autonomous campaign."
                                }
                            }
                        }
                    )
                }

                item { Spacer(Modifier.height(24.dp)) }
            }

            // Group bottom sheet
            if (showGroupSheet) {
                AiGroupPickerSheet(
                    groups = groups,
                    onSelect = { group ->
                        selectedGroup = group
                        showGroupSheet = false
                    },
                    onDismiss = { showGroupSheet = false }
                )
            }

            if (showRiskDialog) {
                AlertDialog(
                    onDismissRequest = { showRiskDialog = false },
                    title = { Text("High-Risk Configuration") },
                    text = {
                        Text(
                            "Selected days are below the recommended safe days (${scheduleResult.suggestedMinimumDays}). Continue anyway?"
                        )
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                riskOverrideAccepted = true
                                showRiskDialog = false
                                Toast.makeText(
                                    context,
                                    "Risk override accepted.",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        ) {
                            Text("Continue")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showRiskDialog = false }) {
                            Text("Cancel")
                        }
                    }
                )
            }

            if (campaignError != null) {
                AlertDialog(
                    onDismissRequest = { campaignError = null },
                    title = { Text("Campaign Error") },
                    text = { Text(campaignError!!) },
                    confirmButton = {
                        Button(onClick = { campaignError = null }) {
                            Text("OK")
                        }
                    }
                )
            }

            if (showAlarmPermissionDialog) {
                AlertDialog(
                    onDismissRequest = { showAlarmPermissionDialog = false },
                    title = { Text("Exact Alarm Permission Required") },
                    text = {
                        Text(
                            "Allow exact alarms so autonomous scheduled messages can continue even after the app is closed."
                        )
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                showAlarmPermissionDialog = false
                                AlarmPermissionHelper.openAlarmPermissionSettings(context)
                            }
                        ) {
                            Text("Open Settings")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showAlarmPermissionDialog = false }) {
                            Text("Cancel")
                        }
                    }
                )
            }

            if (showAccessibilityDialog) {
                AccessibilityPermissionDialog(
                    onAgree = {
                        Toast.makeText(
                            context,
                            "Enable accessibility permission in Settings.",
                            Toast.LENGTH_SHORT
                        ).show()
                    },
                    onDisagree = {
                        Toast.makeText(
                            context,
                            "Accessibility permission is required.",
                            Toast.LENGTH_SHORT
                        ).show()
                    },
                    onDismiss = { showAccessibilityDialog = false }
                )
            }
        }
    }
}

private fun isInternetConnectedForAuto(context: Context): Boolean {
    val cm =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
    val active = cm.activeNetwork ?: return false
    val caps = cm.getNetworkCapabilities(active) ?: return false
    return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
        caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
}

// ======= Sub-Components =======

@Composable
fun AiStepCard(
    stepNumber: Int,
    title: String,
    icon: ImageVector,
    isCompleted: Boolean,
    accentColor: Color,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                1.dp,
                if (isCompleted) accentColor.copy(alpha = 0.5f) else Color(0xFF2A2A4A),
                RoundedCornerShape(16.dp)
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            accentColor.copy(alpha = 0.05f),
                            Color(0xFF0D0D1A)
                        )
                    )
                )
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(
                            if (isCompleted) accentColor.copy(alpha = 0.2f) else Color(0xFF2A2A4A),
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (isCompleted) {
                        Icon(Icons.Filled.Check, null, tint = accentColor, modifier = Modifier.size(16.dp))
                    } else {
                        Text("$stepNumber", color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Icon(icon, null, tint = accentColor, modifier = Modifier.size(18.dp))
                Text(title, fontWeight = FontWeight.Bold, color = Color.White, fontSize = 15.sp)
            }
            content()
        }
    }
}

@Composable
fun RiskChip(
    label: String,
    desc: String,
    color: Color,
    selected: Boolean,
    modifier: Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) color.copy(alpha = 0.18f) else Color(0xFF131326))
            .border(
                width = if (selected) 1.5.dp else 1.dp,
                color = if (selected) color else Color(0xFF2A2A4A),
                shape = RoundedCornerShape(10.dp)
            )
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                label,
                fontWeight = FontWeight.Bold,
                color = if (selected) color else Color.White.copy(alpha = 0.6f),
                fontSize = 12.sp
            )
            Text(
                desc,
                fontSize = 9.sp,
                color = if (selected) color.copy(alpha = 0.7f) else Color.White.copy(alpha = 0.3f),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun TimeWindowChip(
    label: String,
    time: String,
    selected: Boolean,
    modifier: Modifier,
    onClick: () -> Unit
) {
    val accent = Color(0xFF00B8D4)
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) accent.copy(alpha = 0.15f) else Color(0xFF131326))
            .border(
                width = if (selected) 1.5.dp else 1.dp,
                color = if (selected) accent else Color(0xFF2A2A4A),
                shape = RoundedCornerShape(10.dp)
            )
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                label,
                fontWeight = FontWeight.SemiBold,
                color = if (selected) accent else Color.White.copy(alpha = 0.6f),
                fontSize = 11.sp
            )
            Text(
                time,
                fontSize = 9.sp,
                color = if (selected) accent.copy(alpha = 0.6f) else Color.White.copy(alpha = 0.3f)
            )
        }
    }
}

@Composable
fun AiScheduleSummaryCard(schedule: AiScheduleResult) {
    val riskColor = when {
        schedule.riskScore < 30 -> Color(0xFF00C853)
        schedule.riskScore < 60 -> Color(0xFFFFC107)
        schedule.riskScore < 80 -> Color(0xFFFF6D00)
        else -> Color(0xFFFF5252)
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Color(0xFF2A2A4A), RoundedCornerShape(16.dp))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(listOf(Color(0xFF0A0A1E), Color(0xFF131326)))
                )
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.Analytics, null, tint = Color(0xFF00E5FF), modifier = Modifier.size(20.dp))
                Text("AI Schedule Analysis", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 15.sp)
            }

            HorizontalDivider(color = Color(0xFF2A2A4A))

            if (schedule.totalMessages == 0) {
                Text(
                    "Select contacts to see AI schedule analysis...",
                    color = Color.White.copy(alpha = 0.4f),
                    fontSize = 13.sp,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
            } else {
                // Stats Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    AiStatItem("Messages", "${schedule.totalMessages}", Color(0xFF00E5FF))
                    AiStatItem("Risk", schedule.riskLabel, riskColor)
                    AiStatItem("Avg / Day", "${schedule.averagePerDay}", Color(0xFF7C4DFF))
                }

                // Risk Bar
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Risk Score", fontSize = 12.sp, color = Color.White.copy(alpha = 0.6f))
                        Text("${schedule.riskScore}/100", fontSize = 12.sp, color = riskColor, fontWeight = FontWeight.Bold)
                    }
                    LinearProgressIndicator(
                        progress = { schedule.riskScore / 100f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(6.dp)),
                        color = riskColor,
                        trackColor = Color(0xFF2A2A4A)
                    )
                }

                // Day planning summary
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF00E5FF).copy(alpha = 0.07f))
                        .padding(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(Icons.Outlined.AccessTime, null, tint = Color(0xFF00E5FF), modifier = Modifier.size(16.dp))
                    Column {
                        Text("Auto Day Split", fontSize = 11.sp, color = Color(0xFF00E5FF).copy(alpha = 0.7f))
                        Text(
                            "${schedule.selectedDays} day(s), avg ${schedule.averagePerDay}/day, active ~${schedule.activeHoursPerDay} hour(s)/day",
                            fontSize = 13.sp,
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF7C4DFF).copy(alpha = 0.07f))
                        .padding(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(Icons.Outlined.Timer, null, tint = Color(0xFF7C4DFF), modifier = Modifier.size(16.dp))
                    Column {
                        Text("Hourly Cap", fontSize = 11.sp, color = Color(0xFF7C4DFF).copy(alpha = 0.7f))
                        Text(
                            "${schedule.hourlyMin}-${schedule.hourlyMax} msg/hour max, never above 7 in any hour",
                            fontSize = 13.sp,
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Day 1 sample: ${schedule.sampleDistribution}",
                            fontSize = 11.sp,
                            color = Color.White.copy(alpha = 0.7f)
                        )
                    }
                }

                if (schedule.isOverSafeLimit) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFFFF6D00).copy(alpha = 0.12f))
                            .padding(10.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(Icons.Outlined.Warning, null, tint = Color(0xFFFFAB40), modifier = Modifier.size(16.dp))
                        Text(
                            "Daily load is above safe baseline (24/day). If possible, increase selected days.",
                            fontSize = 12.sp,
                            color = Color(0xFFFFE0B2)
                        )
                    }
                }

                // AI Insight
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(riskColor.copy(alpha = 0.07f))
                        .padding(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(Icons.Outlined.Lightbulb, null, tint = riskColor, modifier = Modifier.size(16.dp))
                    Text(schedule.aiInsight, fontSize = 12.sp, color = Color.White.copy(alpha = 0.75f), lineHeight = 18.sp)
                }
            }
        }
    }
}

@Composable
fun AiStatItem(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontWeight = FontWeight.ExtraBold, color = color, fontSize = 18.sp)
        Text(label, fontSize = 11.sp, color = Color.White.copy(alpha = 0.5f))
    }
}

@Composable
fun AiLaunchButton(enabled: Boolean, isRunning: Boolean, onClick: () -> Unit) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (enabled) 1.03f else 1f,
        animationSpec = infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "scale"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .scale(pulseScale)
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (enabled)
                    Brush.linearGradient(listOf(Color(0xFF00B8D4), Color(0xFF7C4DFF), Color(0xFF00B8D4)))
                else
                    Brush.linearGradient(listOf(Color(0xFF2A2A4A), Color(0xFF2A2A4A)))
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 18.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                Icons.Filled.SmartToy,
                null,
                tint = if (enabled) Color.White else Color.White.copy(0.3f),
                modifier = Modifier.size(22.dp)
            )
            Text(
                if (enabled) "🚀 Launch AI Campaign" else "Complete Steps Above",
                fontWeight = FontWeight.ExtraBold,
                fontSize = 16.sp,
                color = if (enabled) Color.White else Color.White.copy(alpha = 0.3f)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiGroupPickerSheet(
    groups: List<Group>,
    onSelect: (Group) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF0D0D1A),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 12.dp, bottom = 8.dp)
                    .width(40.dp)
                    .height(4.dp)
                    .background(Color(0xFF3A3A5A), RoundedCornerShape(2.dp))
            )
        }
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "Select Contact Group",
                fontWeight = FontWeight.Bold,
                color = Color.White,
                fontSize = 16.sp,
                modifier = Modifier.padding(bottom = 4.dp)
            )
            if (groups.isEmpty()) {
                Text(
                    "No groups found. Create a group first.",
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 13.sp,
                    modifier = Modifier.padding(vertical = 16.dp)
                )
            } else {
                groups.forEach { group ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF131326))
                            .clickable { onSelect(group) }
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(Color(0xFF00E5FF).copy(alpha = 0.15f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Outlined.Groups, null, tint = Color(0xFF00E5FF), modifier = Modifier.size(22.dp))
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(group.name, fontWeight = FontWeight.SemiBold, color = Color.White, fontSize = 14.sp)
                            Text("${group.contacts.size} contacts", fontSize = 12.sp, color = Color.White.copy(alpha = 0.5f))
                        }
                        Icon(Icons.Filled.ChevronRight, null, tint = Color.White.copy(alpha = 0.3f))
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}


