package com.message.bulksend.referral

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
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

class ReferralActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BulksendTestTheme {
                ReferralScreen { finish() }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReferralScreen(onBackPressed: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val manager = remember { ReferralManager(context) }
    var isLoading by remember { mutableStateOf(true) }
    var stats by remember { mutableStateOf<ReferralStatsResult?>(null) }
    var leads by remember { mutableStateOf<List<ReferredUser>>(emptyList()) }

    fun refreshAffiliateData() {
        scope.launch {
            try {
                isLoading = true
                val statsResult = manager.getReferralStats()
                stats = if (statsResult.success && statsResult.myReferralCode.isNullOrBlank()) {
                    val name = context.getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
                        .getString("full_name", null)
                    if (!name.isNullOrBlank() && manager.generateReferralCode(name).success) {
                        manager.getReferralStats()
                    } else {
                        statsResult
                    }
                } else {
                    statsResult
                }

                val usersResult = manager.getReferredUsersList()
                if (usersResult.success) {
                    leads = usersResult.referredUsers
                }
            } finally {
                isLoading = false
            }
        }
    }

    LaunchedEffect(Unit) {
        refreshAffiliateData()
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshAffiliateData()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val affiliateCode = stats?.myReferralCode
    val affiliateLink = remember(affiliateCode, stats?.referralLink) {
        when {
            !affiliateCode.isNullOrBlank() -> manager.generatePlayStoreLink(affiliateCode)
            !stats?.referralLink.isNullOrBlank() -> stats?.referralLink.orEmpty()
            else -> ""
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("ChatsPromo Affiliate", fontWeight = FontWeight.Bold, color = Color.White)
                },
                navigationIcon = {
                    IconButton(onClick = onBackPressed) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null,
                            tint = Color.White
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { refreshAffiliateData() },
                        enabled = !isLoading
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = Color.White)
                    }
                    if (!affiliateCode.isNullOrBlank()) {
                        IconButton(onClick = { manager.shareReferralLink(affiliateCode, affiliateLink) }) {
                            Icon(Icons.Default.Share, contentDescription = null, tint = Color.White)
                        }
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
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item { HeroCard() }
                    item { EarningsCard(stats) }
                    item {
                        LinkCard(
                            code = affiliateCode,
                            link = affiliateLink,
                            onShare = {
                                if (!affiliateCode.isNullOrBlank()) {
                                    manager.shareReferralLink(affiliateCode, affiliateLink)
                                }
                            },
                            onCopy = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val copyValue = affiliateLink.ifBlank { affiliateCode.orEmpty() }
                                clipboard.setPrimaryClip(ClipData.newPlainText("Affiliate Link", copyValue))
                                Toast.makeText(context, "Affiliate link copied", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                    item {
                        FunnelEntryCard(
                            stats = stats,
                            onOpenFunnel = {
                                context.startActivity(Intent(context, AffiliateFunnelActivity::class.java))
                            }
                        )
                    }
                    item { SectionTitle("Affiliate Leads and Sales (${leads.size})") }
                    item {
                        Text(
                            text = "Every row below belongs to your affiliate link so you can see installs, signups and exactly which plan was purchased.",
                            fontSize = 13.sp,
                            color = Color.White.copy(alpha = 0.72f)
                        )
                    }
                    item {
                        if (leads.isEmpty()) EmptyCard() else LeadsTable(leads)
                    }
                    item { PlaybookCard() }
                }
            }
        }
    }
}

@Composable
private fun HeroCard() {
    Card(colors = CardDefaults.cardColors(Color(0xFF0F766E)), shape = RoundedCornerShape(20.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(58.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Text("30%", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("ChatsPromo Affiliate Program", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text(
                    "Promote on YouTube, Instagram, Facebook, Telegram, WhatsApp and websites. Earn 30% on every paid plan purchase.",
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
            }
            Icon(Icons.Default.Campaign, contentDescription = null, tint = Color.White)
        }
    }
}

@Composable
private fun EarningsCard(stats: ReferralStatsResult?) {
    Card(colors = CardDefaults.cardColors(Color(0xFF0B1627)), shape = RoundedCornerShape(20.dp)) {
        Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
            Text("Affiliate Earnings", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = formatMoney(stats?.totalReferralEarnings ?: 0),
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 34.sp
            )
            Text(nextPayoutSummary(), color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
            Spacer(modifier = Modifier.height(14.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    EarningsStatCard(
                        title = "Pending",
                        value = formatMoney(stats?.pendingEarnings ?: 0),
                        accent = Color(0xFFF59E0B)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Releases on the 20th",
                        color = Color(0xFFFCD34D),
                        fontSize = 11.sp,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    EarningsStatCard(
                        title = "Withdrawn",
                        value = formatMoney(stats?.withdrawnEarnings ?: 0),
                        accent = Color(0xFF10B981),
                        subtitle = "Already paid"
                    )
                }
            }
        }
    }
}

@Composable
private fun EarningsStatCard(
    title: String,
    value: String,
    accent: Color,
    subtitle: String? = null
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(112.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(accent.copy(alpha = 0.12f))
            .border(1.dp, accent.copy(alpha = 0.34f), RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Column {
            Text(title, color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
            Spacer(modifier = Modifier.height(6.dp))
            Text(value, color = accent, fontWeight = FontWeight.Bold, fontSize = 24.sp)
            if (!subtitle.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(subtitle, color = Color.White.copy(alpha = 0.55f), fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun LinkCard(code: String?, link: String, onShare: () -> Unit, onCopy: () -> Unit) {
    Card(colors = CardDefaults.cardColors(Color(0xFF111E32)), shape = RoundedCornerShape(20.dp)) {
        Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
            Text("Your Affiliate Code", color = Color(0xFF94A3B8), fontSize = 14.sp)
            Spacer(modifier = Modifier.height(10.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF081120))
                    .border(1.dp, Color(0xFF38BDF8).copy(alpha = 0.45f), RoundedCornerShape(14.dp))
                    .padding(horizontal = 20.dp, vertical = 16.dp)
            ) {
                Text(
                    text = code ?: "Generating...",
                    color = Color(0xFF38BDF8),
                    fontWeight = FontWeight.Bold,
                    fontSize = 28.sp,
                    letterSpacing = 3.sp
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                "Use this campaign link in YouTube descriptions, Instagram bios, Telegram channels, WhatsApp broadcasts, Facebook posts and website banners.",
                color = Color.White.copy(alpha = 0.72f),
                fontSize = 13.sp,
                lineHeight = 18.sp
            )
            Spacer(modifier = Modifier.height(12.dp))
            Surface(color = Color.White.copy(alpha = 0.06f), shape = RoundedCornerShape(12.dp)) {
                Text(
                    text = if (link.isBlank()) "Affiliate link will appear here after setup." else link,
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    color = Color(0xFFCBD5E1),
                    fontSize = 12.sp,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = onCopy,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Copy Link")
                }
                Button(
                    onClick = onShare,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF38BDF8)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Share Campaign")
                }
            }
        }
    }
}

@Composable
private fun FunnelEntryCard(stats: ReferralStatsResult?, onOpenFunnel: () -> Unit) {
    val clicks = stats?.referralLinkClicks ?: 0
    val installs = stats?.trackedInstalls ?: 0
    val signups = stats?.trackedRegistrations ?: 0
    val purchases = stats?.successfulReferrals ?: 0

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Card(
            colors = CardDefaults.cardColors(Color(0xFF0B1627)),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
                Text("Affiliate Funnel", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Spacer(modifier = Modifier.height(14.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatBox("Clicks", clicks.toString(), "Tracked link opens", Color(0xFF38BDF8), Modifier.weight(1f))
                    StatBox("Installs", installs.toString(), "Play Store tracked", Color(0xFFF59E0B), Modifier.weight(1f))
                }
                Spacer(modifier = Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatBox("Signups", signups.toString(), "Registered users", Color(0xFF818CF8), Modifier.weight(1f))
                    StatBox("Buyers", purchases.toString(), "Paid plans", Color(0xFF10B981), Modifier.weight(1f))
                }
            }
        }
        Button(
            onClick = onOpenFunnel,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF38BDF8)),
            shape = RoundedCornerShape(14.dp)
        ) {
            Text(
                text = "View Clicks",
                color = Color(0xFF081120),
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )
        }
    }
}

@Composable
private fun StatBox(
    title: String,
    value: String,
    subtitle: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(accent.copy(alpha = 0.12f))
            .border(1.dp, accent.copy(alpha = 0.34f), RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Column {
            Text(title, color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
            Spacer(modifier = Modifier.height(6.dp))
            Text(value, color = accent, fontWeight = FontWeight.Bold, fontSize = 24.sp)
            Spacer(modifier = Modifier.height(4.dp))
            Text(subtitle, color = Color.White.copy(alpha = 0.55f), fontSize = 11.sp)
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
}

@Composable
private fun EmptyCard() {
    Card(colors = CardDefaults.cardColors(Color(0xFF0B1627)), shape = RoundedCornerShape(20.dp)) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(Icons.Default.People, contentDescription = null, tint = Color(0xFF64748B), modifier = Modifier.size(60.dp))
            Spacer(modifier = Modifier.height(14.dp))
            Text("No affiliate leads yet", color = Color.White, fontWeight = FontWeight.Medium, fontSize = 18.sp)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Share your affiliate link on social media and YouTube. Installs, signups and plan purchases will appear here.",
                color = Color.White.copy(alpha = 0.65f),
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                lineHeight = 20.sp
            )
        }
    }
}

@Composable
private fun LeadsTable(leads: List<ReferredUser>) {
    Card(colors = CardDefaults.cardColors(Color(0xFF0B1627)), shape = RoundedCornerShape(20.dp)) {
        Column(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
            Row(modifier = Modifier.background(Color(0xFF1E3A5F)).padding(vertical = 12.dp, horizontal = 8.dp)) {
                Header("#", 40.dp)
                Header("Name", 120.dp)
                Header("Source", 90.dp)
                Header("Status", 90.dp)
                Header("Plan", 80.dp)
                Header("Amount", 90.dp)
                Header("Your 30%", 90.dp)
                Header("Date", 90.dp)
            }

            leads.forEachIndexed { index, user ->
                val amount = amountFor(user)
                val commission = if (user.commissionEarned > 0) user.commissionEarned else (amount * 0.30).toInt()

                Row(
                    modifier = Modifier
                        .background(if (index % 2 == 0) Color.Transparent else Color.White.copy(alpha = 0.04f))
                        .padding(vertical = 12.dp, horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Cell("${index + 1}", 40.dp)
                    Cell(user.fullName, 120.dp)
                    Cell(sourceLabel(user.installSource), 90.dp)
                    StatusCell(user.userStatus, 90.dp)
                    Cell(planLabel(user.purchasedPlanType), 80.dp)
                    Cell(if (amount > 0) formatMoney(amount) else "-", 90.dp)
                    Cell(if (commission > 0) "+${formatMoney(commission)}" else "-", 90.dp)
                    Cell(formatDate(leadDate(user)), 90.dp)
                }
            }
        }
    }
}

@Composable
private fun Header(text: String, width: Dp) {
    Text(
        text = text,
        modifier = Modifier.width(width),
        color = Color.White,
        fontWeight = FontWeight.Bold,
        fontSize = 12.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

@Composable
private fun Cell(text: String, width: Dp) {
    Text(
        text = text,
        modifier = Modifier.width(width),
        color = Color.White.copy(alpha = 0.82f),
        fontSize = 12.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

@Composable
private fun StatusCell(status: String, width: Dp) {
    val normalized = status.lowercase(Locale.getDefault())
    val (label, color) = when (normalized) {
        "purchased" -> "Purchased" to Color(0xFF10B981)
        "registered" -> "Signup" to Color(0xFF818CF8)
        "installed" -> "Installed" to Color(0xFFF59E0B)
        else -> normalized.replaceFirstChar { it.titlecase(Locale.getDefault()) } to Color(0xFF94A3B8)
    }

    Box(modifier = Modifier.width(width)) {
        Surface(color = color.copy(alpha = 0.18f), shape = RoundedCornerShape(999.dp)) {
            Text(
                text = label,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                color = color,
                fontWeight = FontWeight.Medium,
                fontSize = 10.sp
            )
        }
    }
}

@Composable
private fun PlaybookCard() {
    Card(colors = CardDefaults.cardColors(Color(0xFF0B1627)), shape = RoundedCornerShape(20.dp)) {
        Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
            Text("Affiliate Playbook", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Spacer(modifier = Modifier.height(12.dp))
            PlaybookLine(
                icon = Icons.Default.Language,
                text = "Share your link in YouTube descriptions, Instagram bios, Facebook posts, Telegram channels, WhatsApp broadcasts and website banners."
            )
            Spacer(modifier = Modifier.height(10.dp))
            PlaybookLine(
                icon = Icons.Default.Storefront,
                text = "The system tracks clicks, Play Store installs, signups and plan purchases that come from your affiliate link."
            )
            Spacer(modifier = Modifier.height(10.dp))
            PlaybookLine(
                icon = Icons.Default.Insights,
                text = "Monthly, yearly and lifetime plan buyers show up in the table above with the exact plan and your commission."
            )
            Spacer(modifier = Modifier.height(16.dp))
            Surface(color = Color(0xFF38BDF8).copy(alpha = 0.08f), shape = RoundedCornerShape(16.dp)) {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    Text("Commission Breakdown (30%)", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(10.dp))
                    BreakdownRow("Monthly Plan", formatMoney(299), formatMoney(90))
                    BreakdownRow("Yearly Plan", formatMoney(1499), formatMoney(450))
                    BreakdownRow("Lifetime Plan", formatMoney(2999), formatMoney(900))
                }
            }
        }
    }
}

@Composable
private fun PlaybookLine(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.Top) {
        Icon(icon, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(10.dp))
        Text(text, color = Color.White.copy(alpha = 0.72f), fontSize = 13.sp, lineHeight = 18.sp)
    }
}

@Composable
private fun BreakdownRow(name: String, price: String, commission: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(name, color = Color.White.copy(alpha = 0.66f), fontSize = 13.sp)
        Row {
            Text(price, color = Color.White.copy(alpha = 0.72f), fontSize = 13.sp)
            Text(" -> ", color = Color.White.copy(alpha = 0.5f), fontSize = 13.sp)
            Text(commission, color = Color(0xFF10B981), fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
    }
}

private fun amountFor(user: ReferredUser): Int {
    if (user.purchaseAmount > 0) return user.purchaseAmount
    return when (user.purchasedPlanType?.lowercase(Locale.getDefault())) {
        "monthly" -> 299
        "yearly" -> 1499
        "lifetime" -> 2999
        "aiagent499" -> 499
        "ai_monthly" -> 199
        "ai_yearly" -> 899
        else -> 0
    }
}

private fun leadDate(user: ReferredUser): String? {
    return user.purchasedAt ?: user.registeredAt ?: user.installTrackedAt ?: user.referredAt
}

private fun sourceLabel(source: String?): String {
    return when (source?.lowercase(Locale.getDefault())) {
        "play_store_install" -> "Play Store"
        "deep_link" -> "Deep Link"
        "manual_entry" -> "Manual"
        else -> "-"
    }
}

private fun planLabel(planType: String?): String {
    return when (planType?.lowercase(Locale.getDefault())) {
        "monthly" -> "Monthly"
        "yearly" -> "Yearly"
        "lifetime" -> "Lifetime"
        "aiagent499" -> "AI Agent"
        "ai_monthly" -> "AI Monthly"
        "ai_yearly" -> "AI Yearly"
        else -> "-"
    }
}

private fun formatMoney(amount: Int): String = "Rs $amount"

private fun nextPayoutSummary(): String {
    val payoutDate = Calendar.getInstance()
    if (payoutDate.get(Calendar.DAY_OF_MONTH) > 20) {
        payoutDate.add(Calendar.MONTH, 1)
    }
    payoutDate.set(Calendar.DAY_OF_MONTH, 20)
    val formatter = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
    return "Next payout: ${formatter.format(payoutDate.time)}"
}

private fun formatDate(iso: String?): String {
    if (iso.isNullOrBlank()) return "-"
    val parsedDate = parseIsoDate(iso) ?: return "-"
    return SimpleDateFormat("dd MMM", Locale.getDefault()).format(parsedDate)
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
