package com.message.bulksend.referral

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.message.bulksend.ui.theme.BulksendTestTheme
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.max

class AffiliateFunnelActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BulksendTestTheme {
                AffiliateFunnelScreen(onBackPressed = { finish() })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AffiliateFunnelScreen(onBackPressed: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val manager = remember { ReferralManager(context) }
    var isLoading by remember { mutableStateOf(true) }
    var stats by remember { mutableStateOf<ReferralStatsResult?>(null) }
    var clicksResult by remember { mutableStateOf<ReferralClicksResult?>(null) }
    var installsResult by remember { mutableStateOf<ReferralInstallsResult?>(null) }
    var referredUsersResult by remember { mutableStateOf<ReferredUsersResult?>(null) }
    var selectedRange by remember { mutableStateOf(FunnelRange.TODAY) }
    var selectedBucketMode by remember { mutableStateOf(FunnelBucketMode.DAILY) }
    var selectedFilter by remember { mutableStateOf(FunnelEventFilter.ALL) }
    var selectedSort by remember { mutableStateOf(TimelineSort.NEWEST) }
    val scope = rememberCoroutineScope()

    fun loadData() {
        scope.launch {
            try {
                isLoading = true
                stats = manager.getReferralStats()
                clicksResult = manager.getReferralClicks()
                installsResult = manager.getReferralInstalls()
                referredUsersResult = manager.getReferredUsersList()
            } finally {
                isLoading = false
            }
        }
    }

    LaunchedEffect(Unit) {
        loadData()
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                loadData()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("Affiliate Funnel", fontWeight = FontWeight.Bold, color = Color.White)
                },
                navigationIcon = {
                    IconButton(onClick = onBackPressed) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null,
                            tint = Color.White
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { loadData() },
                        enabled = !isLoading
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF09121F))
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xFF09121F), Color(0xFF132235), Color(0xFF0F172A))
                    )
                )
                .padding(padding)
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = Color(0xFF38BDF8)
                )
            } else {
                val allEvents = buildFunnelEvents(
                    clicks = clicksResult?.clickHistory.orEmpty(),
                    installs = installsResult?.installHistory.orEmpty(),
                    referredUsers = referredUsersResult?.referredUsers.orEmpty()
                )
                val rangedEvents = filterEventsByRange(allEvents, selectedRange)
                val summaryBuckets = buildBuckets(rangedEvents, selectedBucketMode)
                val filteredTimelineEvents = sortEvents(
                    events = filterEventsByType(rangedEvents, selectedFilter),
                    sort = selectedSort
                )
                val computedCounts = summarizeEvents(rangedEvents)
                val visibleCounts = if (selectedRange == FunnelRange.ALL_TIME) {
                    computedCounts.copy(
                        clicks = max(computedCounts.clicks, clicksResult?.totalClicks ?: stats?.referralLinkClicks ?: 0),
                        installs = max(computedCounts.installs, installsResult?.totalInstalls ?: stats?.trackedInstalls ?: 0),
                        signups = max(computedCounts.signups, stats?.trackedRegistrations ?: 0),
                        buyers = max(computedCounts.buyers, stats?.successfulReferrals ?: 0)
                    )
                } else {
                    computedCounts
                }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item {
                        FunnelHeroCard(
                            counts = visibleCounts,
                            rangeLabel = selectedRange.label
                        )
                    }
                    item {
                        FunnelControlsCard(
                            selectedRange = selectedRange,
                            onRangeSelected = { selectedRange = it },
                            selectedBucketMode = selectedBucketMode,
                            onBucketModeSelected = { selectedBucketMode = it },
                            selectedFilter = selectedFilter,
                            onFilterSelected = { selectedFilter = it },
                            selectedSort = selectedSort,
                            onSortSelected = { selectedSort = it }
                        )
                    }
                    item { SectionTitle("${selectedBucketMode.label} Summary") }
                    item {
                        Text(
                            text = "Range ke hisaab se clicks, installs, signups aur buyers ka grouped breakup yahan dikh raha hai.",
                            color = Color.White.copy(alpha = 0.72f),
                            fontSize = 13.sp
                        )
                    }
                    if (summaryBuckets.isEmpty()) {
                        item {
                            EmptyStateCard(
                                title = "No ${selectedBucketMode.label.lowercase(Locale.getDefault())} data",
                                message = "Is range me abhi koi affiliate activity nahi mili."
                            )
                        }
                    } else {
                        items(summaryBuckets, key = { it.key }) { bucket ->
                            SummaryBucketCard(bucket = bucket)
                        }
                    }
                    item { SectionTitle("Timeline (${filteredTimelineEvents.size})") }
                    item {
                        Text(
                            text = "Clicks, installs, signups aur buyers ko type aur order ke saath sort karke dekh sakte ho.",
                            color = Color.White.copy(alpha = 0.72f),
                            fontSize = 13.sp
                        )
                    }
                    if (filteredTimelineEvents.isEmpty()) {
                        item {
                            EmptyStateCard(
                                title = "No events found",
                                message = "Range ya filter change karke dusri affiliate activity check karo."
                            )
                        }
                    } else {
                        items(filteredTimelineEvents, key = { it.id }) { event ->
                            TimelineEventCard(event = event)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FunnelHeroCard(counts: FunnelSummaryCounts, rangeLabel: String) {
    Card(
        colors = CardDefaults.cardColors(Color(0xFF0B1627)),
        shape = RoundedCornerShape(22.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFF38BDF8).copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Insights,
                        contentDescription = null,
                        tint = Color(0xFF38BDF8)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text("Traffic and Conversion", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text(
                        "$rangeLabel view for clicks, installs, signups and buyers",
                        color = Color.White.copy(alpha = 0.66f),
                        fontSize = 12.sp
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                FunnelStatCard("Clicks", counts.clicks.toString(), accentFor(FunnelEventType.CLICK), Modifier.weight(1f))
                FunnelStatCard("Installs", counts.installs.toString(), accentFor(FunnelEventType.INSTALL), Modifier.weight(1f))
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                FunnelStatCard("Signups", counts.signups.toString(), accentFor(FunnelEventType.SIGNUP), Modifier.weight(1f))
                FunnelStatCard("Buyers", counts.buyers.toString(), accentFor(FunnelEventType.BUYER), Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun FunnelStatCard(title: String, value: String, accent: Color, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(accent.copy(alpha = 0.12f))
            .border(1.dp, accent.copy(alpha = 0.34f), RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Text(title, color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
        Spacer(modifier = Modifier.height(6.dp))
        Text(value, color = accent, fontWeight = FontWeight.Bold, fontSize = 24.sp)
    }
}

@Composable
private fun FunnelControlsCard(
    selectedRange: FunnelRange,
    onRangeSelected: (FunnelRange) -> Unit,
    selectedBucketMode: FunnelBucketMode,
    onBucketModeSelected: (FunnelBucketMode) -> Unit,
    selectedFilter: FunnelEventFilter,
    onFilterSelected: (FunnelEventFilter) -> Unit,
    selectedSort: TimelineSort,
    onSortSelected: (TimelineSort) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(Color(0xFF0B1627)),
        shape = RoundedCornerShape(22.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Sort and Filter", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            ChoiceFilterRow(
                title = "Range",
                options = FunnelRange.entries.map { ChoiceOption(it, it.label) },
                selected = selectedRange,
                onSelected = onRangeSelected
            )
            ChoiceFilterRow(
                title = "Summary",
                options = FunnelBucketMode.entries.map { ChoiceOption(it, it.label) },
                selected = selectedBucketMode,
                onSelected = onBucketModeSelected
            )
            ChoiceFilterRow(
                title = "Timeline",
                options = FunnelEventFilter.entries.map { ChoiceOption(it, it.label) },
                selected = selectedFilter,
                onSelected = onFilterSelected
            )
            ChoiceFilterRow(
                title = "Order",
                options = TimelineSort.entries.map { ChoiceOption(it, it.label) },
                selected = selectedSort,
                onSelected = onSortSelected
            )
        }
    }
}

@Composable
private fun <T> ChoiceFilterRow(
    title: String,
    options: List<ChoiceOption<T>>,
    selected: T,
    onSelected: (T) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, color = Color.White.copy(alpha = 0.72f), fontSize = 12.sp, fontWeight = FontWeight.Medium)
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(end = 4.dp)
        ) {
            items(options, key = { it.label }) { option ->
                FilterPill(
                    label = option.label,
                    selected = option.value == selected,
                    onClick = { onSelected(option.value) }
                )
            }
        }
    }
}

@Composable
private fun FilterPill(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .background(if (selected) Color(0xFF38BDF8).copy(alpha = 0.18f) else Color.White.copy(alpha = 0.06f))
            .border(
                width = 1.dp,
                color = if (selected) Color(0xFF38BDF8) else Color.White.copy(alpha = 0.16f),
                shape = CircleShape
            )
            .padding(horizontal = 14.dp, vertical = 9.dp)
    ) {
        Text(
            text = label,
            color = if (selected) Color(0xFF38BDF8) else Color.White.copy(alpha = 0.8f),
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun SummaryBucketCard(bucket: FunnelBucket) {
    Card(
        colors = CardDefaults.cardColors(Color(0xFF0B1627)),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(bucket.title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                CompactMetric("Clicks", bucket.counts.clicks, accentFor(FunnelEventType.CLICK), Modifier.weight(1f))
                CompactMetric("Installs", bucket.counts.installs, accentFor(FunnelEventType.INSTALL), Modifier.weight(1f))
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                CompactMetric("Signups", bucket.counts.signups, accentFor(FunnelEventType.SIGNUP), Modifier.weight(1f))
                CompactMetric("Buyers", bucket.counts.buyers, accentFor(FunnelEventType.BUYER), Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun CompactMetric(title: String, value: Int, accent: Color, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(accent.copy(alpha = 0.1f))
            .border(1.dp, accent.copy(alpha = 0.24f), RoundedCornerShape(14.dp))
            .padding(14.dp)
    ) {
        Text(title, color = Color.White.copy(alpha = 0.68f), fontSize = 11.sp)
        Spacer(modifier = Modifier.height(4.dp))
        Text(value.toString(), color = accent, fontWeight = FontWeight.Bold, fontSize = 20.sp)
    }
}

@Composable
private fun TimelineEventCard(event: FunnelEvent) {
    val accent = accentFor(event.type)
    Card(
        colors = CardDefaults.cardColors(Color(0xFF0B1627)),
        shape = RoundedCornerShape(18.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(accent.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = badgeLabelFor(event.type),
                    color = accent,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = event.title,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = formatDateTime(event.occurredAt),
                    color = accent,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = event.subtitle,
                    color = Color.White.copy(alpha = 0.74f),
                    fontSize = 12.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (!event.meta.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = event.meta,
                        color = Color.White.copy(alpha = 0.56f),
                        fontSize = 11.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.14f))
                    .border(1.dp, accent.copy(alpha = 0.28f), CircleShape)
                    .padding(horizontal = 10.dp, vertical = 7.dp)
            ) {
                Text(event.type.label, color = accent, fontSize = 11.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
private fun EmptyStateCard(title: String, message: String) {
    Card(
        colors = CardDefaults.cardColors(Color(0xFF0B1627)),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(title, color = Color.White, fontWeight = FontWeight.Medium, fontSize = 18.sp)
            Text(
                text = message,
                color = Color.White.copy(alpha = 0.64f),
                fontSize = 13.sp
            )
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text = text, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
}

private fun buildFunnelEvents(
    clicks: List<ReferralClick>,
    installs: List<ReferralInstall>,
    referredUsers: List<ReferredUser>
): List<FunnelEvent> {
    return buildClickEvents(clicks) +
        buildInstallEvents(installs) +
        buildSignupEvents(referredUsers) +
        buildBuyerEvents(referredUsers)
}

private fun buildClickEvents(clicks: List<ReferralClick>): List<FunnelEvent> {
    return clicks.mapNotNull { click ->
        val occurredAt = click.clickedAt?.let(::parseIsoDate) ?: return@mapNotNull null
        FunnelEvent(
            id = "click-${click.clickId.ifBlank { occurredAt.time.toString() }}",
            type = FunnelEventType.CLICK,
            occurredAt = occurredAt,
            title = "Affiliate link clicked",
            subtitle = click.referralCode?.let { "Code $it link open hua" } ?: "Affiliate link open hua",
            meta = click.userAgent?.takeIf { it.isNotBlank() }
        )
    }
}

private fun buildInstallEvents(installs: List<ReferralInstall>): List<FunnelEvent> {
    return installs.mapNotNull { install ->
        val occurredAt = install.installTrackedAt?.let(::parseIsoDate) ?: return@mapNotNull null
        val status = if (install.linkedUserId.isNullOrBlank()) "Signup pending" else "Linked to user"
        FunnelEvent(
            id = "install-${install.installId.ifBlank { occurredAt.time.toString() }}",
            type = FunnelEventType.INSTALL,
            occurredAt = occurredAt,
            title = "App installed",
            subtitle = "${sourceLabel(install.installSource)} install tracked from affiliate link",
            meta = status
        )
    }
}

private fun buildSignupEvents(referredUsers: List<ReferredUser>): List<FunnelEvent> {
    return referredUsers.mapNotNull { user ->
        val occurredAt = user.registeredAt?.let(::parseIsoDate) ?: return@mapNotNull null
        FunnelEvent(
            id = "signup-${user.oderId.ifBlank { occurredAt.time.toString() }}",
            type = FunnelEventType.SIGNUP,
            occurredAt = occurredAt,
            title = user.fullName.ifBlank { "New signup" },
            subtitle = "Signed up from your affiliate traffic",
            meta = user.email.takeIf { it.isNotBlank() && it != "N/A" }
        )
    }
}

private fun buildBuyerEvents(referredUsers: List<ReferredUser>): List<FunnelEvent> {
    return referredUsers.mapNotNull { user ->
        val occurredAt = user.purchasedAt?.let(::parseIsoDate) ?: return@mapNotNull null
        val amountText = if (user.purchaseAmount > 0) formatMoney(user.purchaseAmount) else null
        val commissionText = if (user.commissionEarned > 0) "Commission ${formatMoney(user.commissionEarned)}" else null
        FunnelEvent(
            id = "buyer-${user.oderId.ifBlank { occurredAt.time.toString() }}",
            type = FunnelEventType.BUYER,
            occurredAt = occurredAt,
            title = user.fullName.ifBlank { "Plan purchase" },
            subtitle = buildString {
                append("Bought ${planLabel(user.purchasedPlanType)}")
                if (amountText != null) {
                    append(" for $amountText")
                }
            },
            meta = commissionText
        )
    }
}

private fun filterEventsByRange(events: List<FunnelEvent>, range: FunnelRange): List<FunnelEvent> {
    if (range == FunnelRange.ALL_TIME) return events
    val threshold = startDateFor(range)
    return events.filter { !it.occurredAt.before(threshold) }
}

private fun startDateFor(range: FunnelRange): Date {
    val calendar = Calendar.getInstance()
    calendar.time = Date()
    when (range) {
        FunnelRange.TODAY -> Unit
        FunnelRange.SEVEN_DAYS -> calendar.add(Calendar.DAY_OF_YEAR, -6)
        FunnelRange.THIRTY_DAYS -> calendar.add(Calendar.DAY_OF_YEAR, -29)
        FunnelRange.ALL_TIME -> return Date(0)
    }
    calendar.set(Calendar.HOUR_OF_DAY, 0)
    calendar.set(Calendar.MINUTE, 0)
    calendar.set(Calendar.SECOND, 0)
    calendar.set(Calendar.MILLISECOND, 0)
    return calendar.time
}

private fun filterEventsByType(events: List<FunnelEvent>, filter: FunnelEventFilter): List<FunnelEvent> {
    if (filter == FunnelEventFilter.ALL) return events
    return events.filter { it.type == filter.type }
}

private fun sortEvents(events: List<FunnelEvent>, sort: TimelineSort): List<FunnelEvent> {
    return when (sort) {
        TimelineSort.NEWEST -> events.sortedByDescending { it.occurredAt.time }
        TimelineSort.OLDEST -> events.sortedBy { it.occurredAt.time }
    }
}

private fun summarizeEvents(events: List<FunnelEvent>): FunnelSummaryCounts {
    return FunnelSummaryCounts(
        clicks = events.count { it.type == FunnelEventType.CLICK },
        installs = events.count { it.type == FunnelEventType.INSTALL },
        signups = events.count { it.type == FunnelEventType.SIGNUP },
        buyers = events.count { it.type == FunnelEventType.BUYER }
    )
}

private fun buildBuckets(events: List<FunnelEvent>, mode: FunnelBucketMode): List<FunnelBucket> {
    val keyFormatter = SimpleDateFormat(
        if (mode == FunnelBucketMode.DAILY) "yyyy-MM-dd" else "yyyy-MM",
        Locale.US
    )
    val titleFormatter = SimpleDateFormat(
        if (mode == FunnelBucketMode.DAILY) "dd MMM yyyy" else "MMM yyyy",
        Locale.getDefault()
    )

    return events
        .groupBy { keyFormatter.format(it.occurredAt) }
        .map { (key, bucketEvents) ->
            val latestDate = bucketEvents.maxByOrNull { it.occurredAt.time }?.occurredAt ?: Date(0)
            FunnelBucket(
                key = key,
                title = titleFormatter.format(latestDate),
                counts = summarizeEvents(bucketEvents),
                sortTime = latestDate.time
            )
        }
        .sortedByDescending { it.sortTime }
}

private fun badgeLabelFor(type: FunnelEventType): String {
    return when (type) {
        FunnelEventType.CLICK -> "CLK"
        FunnelEventType.INSTALL -> "INS"
        FunnelEventType.SIGNUP -> "SGN"
        FunnelEventType.BUYER -> "BUY"
    }
}

private fun accentFor(type: FunnelEventType): Color {
    return when (type) {
        FunnelEventType.CLICK -> Color(0xFF38BDF8)
        FunnelEventType.INSTALL -> Color(0xFFF59E0B)
        FunnelEventType.SIGNUP -> Color(0xFF818CF8)
        FunnelEventType.BUYER -> Color(0xFF10B981)
    }
}

private fun sourceLabel(source: String?): String {
    return when (source?.lowercase(Locale.getDefault())) {
        "play_store_install" -> "Play Store"
        "direct_install" -> "Direct"
        "app_link" -> "App Link"
        else -> "App"
    }
}

private fun planLabel(planType: String?): String {
    return when (planType?.lowercase(Locale.getDefault())) {
        "monthly" -> "Monthly Plan"
        "yearly" -> "Yearly Plan"
        "lifetime" -> "Lifetime Plan"
        "aiagent499" -> "AI Agent Plan"
        "ai_monthly" -> "AI Monthly Plan"
        "ai_yearly" -> "AI Yearly Plan"
        null, "" -> "Paid Plan"
        else -> planType.replace('_', ' ').replaceFirstChar {
            if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()
        }
    }
}

private fun formatMoney(amount: Int): String = "\u20B9$amount"

private fun formatDateTime(date: Date): String {
    return SimpleDateFormat("dd MMM yyyy • hh:mm a", Locale.getDefault()).format(date)
}

private fun parseIsoDate(value: String): Date? {
    val patterns = listOf(
        "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
        "yyyy-MM-dd'T'HH:mm:ss'Z'",
        "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
        "yyyy-MM-dd'T'HH:mm:ssXXX",
        "yyyy-MM-dd'T'HH:mm:ss.SSS",
        "yyyy-MM-dd'T'HH:mm:ss"
    )

    for (pattern in patterns) {
        try {
            val formatter = SimpleDateFormat(pattern, Locale.US)
            if (pattern.endsWith("'Z'")) {
                formatter.timeZone = TimeZone.getTimeZone("UTC")
            }
            val parsed = formatter.parse(value)
            if (parsed != null) {
                return parsed
            }
        } catch (_: Exception) {
        }
    }

    return null
}

private data class ChoiceOption<T>(val value: T, val label: String)

private data class FunnelEvent(
    val id: String,
    val type: FunnelEventType,
    val occurredAt: Date,
    val title: String,
    val subtitle: String,
    val meta: String? = null
)

private data class FunnelSummaryCounts(
    val clicks: Int = 0,
    val installs: Int = 0,
    val signups: Int = 0,
    val buyers: Int = 0
)

private data class FunnelBucket(
    val key: String,
    val title: String,
    val counts: FunnelSummaryCounts,
    val sortTime: Long
)

private enum class FunnelRange(val label: String) {
    TODAY("Today"),
    SEVEN_DAYS("7 Days"),
    THIRTY_DAYS("30 Days"),
    ALL_TIME("All Time"),
}

private enum class FunnelBucketMode(val label: String) {
    DAILY("Daily"),
    MONTHLY("Monthly"),
}

private enum class FunnelEventFilter(val label: String, val type: FunnelEventType? = null) {
    ALL("All"),
    CLICKS("Clicks", FunnelEventType.CLICK),
    INSTALLS("Installs", FunnelEventType.INSTALL),
    SIGNUPS("Signups", FunnelEventType.SIGNUP),
    BUYERS("Buyers", FunnelEventType.BUYER),
}

private enum class TimelineSort(val label: String) {
    NEWEST("Newest"),
    OLDEST("Oldest"),
}

private enum class FunnelEventType(val label: String) {
    CLICK("Click"),
    INSTALL("Install"),
    SIGNUP("Signup"),
    BUYER("Buyer"),
}
