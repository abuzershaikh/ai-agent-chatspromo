package com.message.bulksend.autorespond.ai.ui.templates

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.message.bulksend.autorespond.ai.data.model.DoctorEntity
import com.message.bulksend.autorespond.ai.data.repo.ClinicRepository
import com.message.bulksend.autorespond.ai.settings.AIAgentSettingsManager
import com.message.bulksend.ui.theme.BulksendTestTheme
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.json.JSONArray

// ─── White Professional Theme ───
private val BgLight = Color(0xFFF5F7FA)
private val CardWhite = Color(0xFFFFFFFF)
private val CardBorder = Color(0xFFE8ECF0)
private val Accent = Color(0xFF2563EB)
private val AccentLight = Color(0xFFDBEAFE)
private val AccentGreen = Color(0xFF16A34A)
private val AccentGreenLight = Color(0xFFDCFCE7)
private val AccentRed = Color(0xFFDC2626)
private val AccentRedLight = Color(0xFFFEE2E2)
private val AccentOrange = Color(0xFFEA580C)
private val AccentOrangeLight = Color(0xFFFFF7ED)
private val TextDark = Color(0xFF1E293B)
private val TextSecondary = Color(0xFF64748B)
private val TextMuted = Color(0xFF94A3B8)
private val DividerColor = Color(0xFFE2E8F0)

class ClinicConfigActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val repository = ClinicRepository(applicationContext)
        setContent {
            BulksendTestTheme {
                ClinicConfigScreen(repository = repository, onBack = { finish() })
            }
        }
    }
}

// ─── Time Helpers ───
fun to12h(time24: String): String {
    val parts = time24.split(":")
    val h = parts[0].toIntOrNull() ?: 0
    val m = parts.getOrNull(1) ?: "00"
    val amPm = if (h < 12) "AM" else "PM"
    val h12 = when { h == 0 -> 12; h > 12 -> h - 12; else -> h }
    return "$h12:$m $amPm"
}

fun to24h(hour: Int, minute: Int, isAm: Boolean): String {
    val h24 = when {
        isAm && hour == 12 -> 0
        !isAm && hour == 12 -> 12
        !isAm -> hour + 12
        else -> hour
    }
    return "%02d:%02d".format(h24, minute)
}

fun parse24h(time24: String): Triple<Int, Int, Boolean> {
    val parts = time24.split(":")
    val h24 = parts[0].toIntOrNull() ?: 9
    val m = parts.getOrNull(1)?.toIntOrNull() ?: 0
    val isAm = h24 < 12
    val h12 = when { h24 == 0 -> 12; h24 > 12 -> h24 - 12; else -> h24 }
    return Triple(h12, m, isAm)
}

fun timeToMinutes(time24: String): Int {
    val parts = time24.split(":")
    return (parts[0].toIntOrNull() ?: 0) * 60 + (parts.getOrNull(1)?.toIntOrNull() ?: 0)
}

fun calculateTotalSlots(start: String, end: String, lunchStart: String, lunchEnd: String, duration: Int): Int {
    if (duration <= 0) return 0
    val startMin = timeToMinutes(start); val endMin = timeToMinutes(end)
    val lsMin = timeToMinutes(lunchStart); val leMin = timeToMinutes(lunchEnd)
    var count = 0; var t = startMin
    while (t + duration <= endMin) {
        if (t < leMin && t + duration > lsMin) { t = leMin; continue }
        count++; t += duration
    }
    return count
}

// ─────────────────── NAVIGATION ───────────────────

private enum class ClinicScreen { HUB, SETTINGS, SCHEDULE, HOLIDAYS, DOCTORS, TEMPLATE, DASHBOARD, CANCEL_TEMPLATE, AFTER_HOURS, REMINDER }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClinicConfigScreen(repository: ClinicRepository, onBack: () -> Unit) {
    val context = LocalContext.current
    val settingsManager = remember { AIAgentSettingsManager(context) }
    var currentScreen by remember { mutableStateOf(ClinicScreen.HUB) }
    var refreshKey by remember { mutableStateOf(0) } // Key to force refresh

    when (currentScreen) {
        ClinicScreen.HUB -> HubScreen(
            settingsManager = settingsManager,
            onBack = onBack,
            refreshKey = refreshKey // Pass refresh key
        ) { currentScreen = it }
        ClinicScreen.SETTINGS -> SettingsScreen(settingsManager) { 
            refreshKey++ // Increment to refresh HubScreen
            currentScreen = ClinicScreen.HUB 
        }
        ClinicScreen.SCHEDULE -> ScheduleScreen(settingsManager) { 
            refreshKey++
            currentScreen = ClinicScreen.HUB 
        }
        ClinicScreen.HOLIDAYS -> HolidaysScreen(settingsManager) { 
            refreshKey++
            currentScreen = ClinicScreen.HUB 
        }
        ClinicScreen.DOCTORS -> DoctorsScreen(repository) { 
            refreshKey++
            currentScreen = ClinicScreen.HUB 
        }
        ClinicScreen.TEMPLATE -> TemplateScreen(settingsManager) { 
            refreshKey++
            currentScreen = ClinicScreen.HUB 
        }
        ClinicScreen.DASHBOARD -> DashboardScreen(repository) { 
            currentScreen = ClinicScreen.HUB 
        }
        ClinicScreen.CANCEL_TEMPLATE -> CancelTemplateScreen(settingsManager) { 
            refreshKey++
            currentScreen = ClinicScreen.HUB 
        }
        ClinicScreen.AFTER_HOURS -> AfterHoursScreen(settingsManager) { 
            refreshKey++
            currentScreen = ClinicScreen.HUB 
        }
        ClinicScreen.REMINDER -> ReminderSettingsScreen(settingsManager) { 
            refreshKey++
            currentScreen = ClinicScreen.HUB 
        }
    }
}

// ═══════════════════════════════════════════════════
//                    HUB SCREEN
// ═══════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HubScreen(
    settingsManager: AIAgentSettingsManager,
    onBack: () -> Unit,
    refreshKey: Int = 0, // Key to trigger refresh
    onNavigate: (ClinicScreen) -> Unit
) {
    var isEnabled by remember { mutableStateOf(settingsManager.activeTemplate == "CLINIC") }
    
    // State variables that refresh when refreshKey changes
    val clinicName by remember(refreshKey) { mutableStateOf(settingsManager.clinicName) }
    val clinicAddress by remember(refreshKey) { mutableStateOf(settingsManager.clinicAddress) }
    val openTime by remember(refreshKey) { mutableStateOf(settingsManager.clinicOpenTime) }
    val closeTime by remember(refreshKey) { mutableStateOf(settingsManager.clinicCloseTime) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Clinic Template", fontWeight = FontWeight.Bold, color = TextDark) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back", tint = Accent) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = CardWhite)
            )
        },
        containerColor = BgLight
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(vertical = 20.dp)
        ) {
            // ── Enable Toggle ──
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = CardWhite),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(20.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Clinic AI Agent", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextDark)
                            Text(
                                if (isEnabled) "Active" else "Inactive",
                                color = if (isEnabled) AccentGreen else TextMuted, fontSize = 14.sp
                            )
                        }
                        Switch(
                            checked = isEnabled,
                            onCheckedChange = {
                                isEnabled = it
                                settingsManager.activeTemplate = if (it) "CLINIC" else "NONE"
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = AccentGreen
                            )
                        )
                    }
                }
            }

            // ── Clinic Info Summary ──
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Accent),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(clinicName, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Spacer(Modifier.height(4.dp))
                        Text(clinicAddress, fontSize = 14.sp, color = Color.White.copy(0.8f))
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "${to12h(openTime)} — ${to12h(closeTime)}",
                            fontSize = 14.sp, color = Color.White.copy(0.9f), fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            // ── Dashboard Card ──
            item {
                NavCard(
                    icon = Icons.Default.Dashboard,
                    title = "Today's Appointments",
                    subtitle = "View all bookings for today",
                    accentColor = Color(0xFF0D9488)
                ) { onNavigate(ClinicScreen.DASHBOARD) }
            }

            // ── Navigation Cards ──
            item {
                Text("Settings", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextMuted,
                    modifier = Modifier.padding(start = 4.dp, top = 4.dp))
            }

            item {
                NavCard(
                    icon = Icons.Default.Business,
                    title = "Clinic Settings",
                    subtitle = "Name, address, hours",
                    accentColor = Accent
                ) { onNavigate(ClinicScreen.SETTINGS) }
            }

            item {
                NavCard(
                    icon = Icons.Default.CalendarMonth,
                    title = "Weekly Schedule",
                    subtitle = "Open days, half days, closed days",
                    accentColor = AccentGreen
                ) { onNavigate(ClinicScreen.SCHEDULE) }
            }

            item {
                NavCard(
                    icon = Icons.Default.EventBusy,
                    title = "Holidays",
                    subtitle = "Manage clinic holidays",
                    accentColor = AccentRed
                ) { onNavigate(ClinicScreen.HOLIDAYS) }
            }

            item {
                NavCard(
                    icon = Icons.Default.MedicalServices,
                    title = "Doctors",
                    subtitle = "Manage doctors & availability",
                    accentColor = AccentOrange
                ) { onNavigate(ClinicScreen.DOCTORS) }
            }

            item {
                NavCard(
                    icon = Icons.Default.Message,
                    title = "Confirmation Template",
                    subtitle = "Customize booking confirmation message",
                    accentColor = Color(0xFF7C3AED)
                ) { onNavigate(ClinicScreen.TEMPLATE) }
            }

            item {
                NavCard(
                    icon = Icons.Default.Cancel,
                    title = "Cancellation Template",
                    subtitle = "Customize cancellation message",
                    accentColor = Color(0xFFE11D48)
                ) { onNavigate(ClinicScreen.CANCEL_TEMPLATE) }
            }

            item {
                NavCard(
                    icon = Icons.Default.NightsStay,
                    title = "After-Hours Reply",
                    subtitle = "Auto-reply when clinic is closed",
                    accentColor = Color(0xFF4338CA)
                ) { onNavigate(ClinicScreen.AFTER_HOURS) }
            }

            item {
                NavCard(
                    icon = Icons.Default.Notifications,
                    title = "Reminder Settings",
                    subtitle = "Auto-reminder configuration",
                    accentColor = Color(0xFF0369A1)
                ) { onNavigate(ClinicScreen.REMINDER) }
            }
        }
    }
}

@Composable
fun NavCard(icon: ImageVector, title: String, subtitle: String, accentColor: Color, onClick: () -> Unit) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = CardWhite),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth().clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(18.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = accentColor.copy(0.1f),
                modifier = Modifier.size(48.dp)
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(icon, title, tint = accentColor, modifier = Modifier.size(26.dp))
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TextDark)
                Text(subtitle, fontSize = 13.sp, color = TextSecondary)
            }
            Icon(Icons.Default.ChevronRight, "Go", tint = TextMuted)
        }
    }
}

// ═══════════════════════════════════════════════════
//               CLINIC SETTINGS SCREEN
// ═══════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(settingsManager: AIAgentSettingsManager, onBack: () -> Unit) {
    var name by remember { mutableStateOf(settingsManager.clinicName) }
    var address by remember { mutableStateOf(settingsManager.clinicAddress) }
    var openTime by remember { mutableStateOf(settingsManager.clinicOpenTime) }
    var closeTime by remember { mutableStateOf(settingsManager.clinicCloseTime) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Clinic Settings", fontWeight = FontWeight.Bold, color = TextDark) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back", tint = Accent) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = CardWhite)
            )
        },
        containerColor = BgLight
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(vertical = 20.dp)
        ) {
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = CardWhite),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text("Basic Information", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextDark)
                        
                        OutlinedTextField(
                            value = name, onValueChange = { name = it; settingsManager.clinicName = it },
                            label = { Text("Clinic Name") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Accent, unfocusedBorderColor = CardBorder,
                                focusedTextColor = TextDark, unfocusedTextColor = TextDark
                            )
                        )
                        
                        OutlinedTextField(
                            value = address, onValueChange = { address = it; settingsManager.clinicAddress = it },
                            label = { Text("Clinic Address") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Accent, unfocusedBorderColor = CardBorder,
                                focusedTextColor = TextDark, unfocusedTextColor = TextDark
                            )
                        )
                    }
                }
            }

            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = CardWhite),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text("Operating Hours", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextDark)
                        
                        TimePicker12h("Opening Time", openTime) {
                            openTime = it; settingsManager.clinicOpenTime = it
                        }
                        TimePicker12h("Closing Time", closeTime) {
                            closeTime = it; settingsManager.clinicCloseTime = it
                        }
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════
//              WEEKLY SCHEDULE SCREEN
// ═══════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleScreen(settingsManager: AIAgentSettingsManager, onBack: () -> Unit) {
    val days = listOf("Mon","Tue","Wed","Thu","Fri","Sat","Sun")
    val fullNames = mapOf("Mon" to "Monday","Tue" to "Tuesday","Wed" to "Wednesday","Thu" to "Thursday","Fri" to "Friday","Sat" to "Saturday","Sun" to "Sunday")
    
    val schedule = remember {
        mutableStateMapOf<String, String>().apply {
            try {
                val obj = JSONObject(settingsManager.weeklySchedule)
                days.forEach { put(it, obj.optString(it, "OPEN")) }
            } catch (_: Exception) {
                days.take(5).forEach { put(it, "OPEN") }
                put("Sat", "HALF"); put("Sun", "CLOSED")
            }
        }
    }
    var halfDayClose by remember { mutableStateOf(settingsManager.halfDayCloseTime) }

    fun saveSchedule() {
        val obj = JSONObject()
        schedule.forEach { (k, v) -> obj.put(k, v) }
        settingsManager.weeklySchedule = obj.toString()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Weekly Schedule", fontWeight = FontWeight.Bold, color = TextDark) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back", tint = AccentGreen) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = CardWhite)
            )
        },
        containerColor = BgLight
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(vertical = 20.dp)
        ) {
            item {
                Text("Tap status to change", color = TextMuted, fontSize = 13.sp,
                    modifier = Modifier.padding(start = 4.dp, bottom = 4.dp))
            }

            items(days) { day ->
                val status = schedule[day] ?: "OPEN"
                val (statusColor, statusBg, statusIcon, statusLabel) = when (status) {
                    "OPEN" -> listOf(AccentGreen, AccentGreenLight, "✓", "Open")
                    "HALF" -> listOf(AccentOrange, AccentOrangeLight, "½", "Half Day")
                    else -> listOf(AccentRed, AccentRedLight, "✕", "Closed")
                }

                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = CardWhite),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp).fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(fullNames[day] ?: day, fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                            color = TextDark, modifier = Modifier.weight(1f))

                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = statusBg as Color,
                            modifier = Modifier.clickable {
                                schedule[day] = when (status) {
                                    "OPEN" -> "HALF"
                                    "HALF" -> "CLOSED"
                                    else -> "OPEN"
                                }
                                saveSchedule()
                            }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(statusIcon as String, fontSize = 14.sp, color = statusColor as Color)
                                Spacer(Modifier.width(6.dp))
                                Text(statusLabel as String, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                                    color = statusColor)
                            }
                        }
                    }
                }
            }

            // Half-day close time
            if (schedule.values.any { it == "HALF" }) {
                item {
                    Spacer(Modifier.height(6.dp))
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = AccentOrangeLight),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Text("Half-Day Closing Time", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = AccentOrange)
                            Spacer(Modifier.height(8.dp))
                            TimePicker12h("Closes At", halfDayClose) {
                                halfDayClose = it; settingsManager.halfDayCloseTime = it
                            }
                        }
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════
//               HOLIDAYS SCREEN
// ═══════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HolidaysScreen(settingsManager: AIAgentSettingsManager, onBack: () -> Unit) {
    val holidays = remember {
        mutableStateListOf<Pair<String, String>>().apply {
            try {
                val arr = JSONArray(settingsManager.holidays)
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    add(obj.getString("date") to obj.getString("name"))
                }
            } catch (_: Exception) {}
        }
    }
    var showAddDialog by remember { mutableStateOf(false) }

    fun saveHolidays() {
        val arr = JSONArray()
        holidays.forEach { (d, n) -> arr.put(JSONObject().put("date", d).put("name", n)) }
        settingsManager.holidays = arr.toString()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Holidays", fontWeight = FontWeight.Bold, color = TextDark) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back", tint = AccentRed) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = CardWhite)
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = AccentRed, contentColor = Color.White
            ) { Icon(Icons.Default.Add, "Add Holiday") }
        },
        containerColor = BgLight
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(vertical = 20.dp)
        ) {
            if (holidays.isEmpty()) {
                item {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = CardWhite),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                        modifier = Modifier.fillMaxWidth().height(180.dp)
                    ) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.WbSunny, "No holidays", tint = TextMuted, modifier = Modifier.size(48.dp))
                                Spacer(Modifier.height(8.dp))
                                Text("No holidays added", color = TextMuted, fontSize = 15.sp)
                                Text("Tap + to add a holiday", color = TextMuted, fontSize = 13.sp)
                            }
                        }
                    }
                }
            } else {
                items(holidays.size) { index ->
                    val (date, name) = holidays[index]
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = CardWhite),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp).fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = AccentRedLight,
                                modifier = Modifier.size(44.dp)
                            ) {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.EventBusy, name, tint = AccentRed, modifier = Modifier.size(22.dp))
                                }
                            }
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TextDark)
                                Text(date, fontSize = 13.sp, color = TextSecondary)
                            }
                            IconButton(onClick = {
                                holidays.removeAt(index)
                                saveHolidays()
                            }, modifier = Modifier.size(36.dp)) {
                                Icon(Icons.Default.Delete, "Delete", tint = AccentRed, modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }
            }
            
            item { Spacer(Modifier.height(60.dp)) }
        }
    }

    if (showAddDialog) {
        AddHolidayDialog(
            onDismiss = { showAddDialog = false },
            onSave = { date, name ->
                holidays.add(date to name)
                saveHolidays()
                showAddDialog = false
            }
        )
    }
}

// ═══════════════════════════════════════════════════
//                DOCTORS SCREEN
// ═══════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DoctorsScreen(repository: ClinicRepository, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val doctorsFlow = remember { repository.getAllDoctors() }
    val doctors by doctorsFlow.collectAsState(initial = emptyList())
    var showAddDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Doctors", fontWeight = FontWeight.Bold, color = TextDark) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back", tint = AccentOrange) }
                },
                actions = {
                    Text("${doctors.size} total", color = TextMuted, fontSize = 13.sp,
                        modifier = Modifier.padding(end = 16.dp))
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = CardWhite)
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = AccentOrange, contentColor = Color.White
            ) { Icon(Icons.Default.PersonAdd, "Add Doctor") }
        },
        containerColor = BgLight
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(vertical = 20.dp)
        ) {
            if (doctors.isEmpty()) {
                item {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = CardWhite),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                        modifier = Modifier.fillMaxWidth().height(180.dp)
                    ) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.PersonAdd, "Add", tint = TextMuted, modifier = Modifier.size(48.dp))
                                Spacer(Modifier.height(8.dp))
                                Text("No doctors added yet", color = TextMuted, fontSize = 15.sp)
                                Text("Tap + to add a doctor", color = TextMuted, fontSize = 13.sp)
                            }
                        }
                    }
                }
            } else {
                items(doctors) { doctor ->
                    DoctorCard(doctor = doctor, onDelete = {
                        scope.launch { repository.deleteDoctor(doctor) }
                    })
                }
            }
            
            item { Spacer(Modifier.height(60.dp)) }
        }
    }

    if (showAddDialog) {
        AddDoctorDialog(
            onDismiss = { showAddDialog = false },
            onSave = { doctor ->
                scope.launch { repository.saveDoctor(doctor); showAddDialog = false }
            }
        )
    }
}

// ═══════════════════════════════════════════════════
//            CONFIRMATION TEMPLATE SCREEN
// ═══════════════════════════════════════════════════

private val AccentPurple = Color(0xFF7C3AED)
private val AccentPurpleLight = Color(0xFFF3E8FF)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TemplateScreen(settingsManager: AIAgentSettingsManager, onBack: () -> Unit) {
    var template by remember { mutableStateOf(settingsManager.confirmationTemplate) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Confirmation Template", fontWeight = FontWeight.Bold, color = TextDark) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back", tint = AccentPurple) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = CardWhite)
            )
        },
        containerColor = BgLight
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(vertical = 20.dp)
        ) {
            // Placeholder guide
            item {
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = AccentPurpleLight)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Available Placeholders", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = AccentPurple)
                        Spacer(Modifier.height(6.dp))
                        Text("{name} → Patient Name", fontSize = 13.sp, color = TextSecondary)
                        Text("{doctor} → Doctor Name", fontSize = 13.sp, color = TextSecondary)
                        Text("{date} → Appointment Date", fontSize = 13.sp, color = TextSecondary)
                        Text("{time} → Appointment Time", fontSize = 13.sp, color = TextSecondary)
                        Text("{address} → Clinic Address", fontSize = 13.sp, color = TextSecondary)
                    }
                }
            }

            // Template editor
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = CardWhite),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text("Message Template", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextDark)
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            value = template,
                            onValueChange = {
                                template = it
                                settingsManager.confirmationTemplate = it
                            },
                            modifier = Modifier.fillMaxWidth().height(220.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = AccentPurple, unfocusedBorderColor = CardBorder,
                                focusedTextColor = TextDark, unfocusedTextColor = TextDark
                            )
                        )
                    }
                }
            }

            // Live preview
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = CardWhite),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text("Preview", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextDark)
                        Spacer(Modifier.height(12.dp))
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = BgLight,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                template
                                    .replace("{name}", "Ahmed Khan")
                                    .replace("{doctor}", "Dr. Jamil")
                                    .replace("{date}", "15 Feb 2026")
                                    .replace("{time}", "2:30 PM")
                                    .replace("{address}", settingsManager.clinicAddress),
                                modifier = Modifier.padding(16.dp),
                                color = TextDark, fontSize = 14.sp, lineHeight = 22.sp
                            )
                        }
                    }
                }
            }

            // Reset button
            item {
                TextButton(
                    onClick = {
                        val defaultTemplate = "✅ *Appointment Confirmed*\n\n👤 Name: {name}\n👨‍⚕️ Doctor: {doctor}\n📅 Date: {date}\n⏰ Time: {time}\n📍 Clinic Address: {address}\n\nPlease come 10 minutes early."
                        template = defaultTemplate
                        settingsManager.confirmationTemplate = defaultTemplate
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Reset to Default", color = AccentRed, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════
//          CANCELLATION TEMPLATE SCREEN
// ═══════════════════════════════════════════════════

private val AccentRose = Color(0xFFE11D48)
private val AccentRoseLight = Color(0xFFFFF1F2)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CancelTemplateScreen(settingsManager: AIAgentSettingsManager, onBack: () -> Unit) {
    var template by remember { mutableStateOf(settingsManager.cancellationTemplate) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Cancellation Template", fontWeight = FontWeight.Bold, color = TextDark) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back", tint = AccentRose) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = CardWhite)
            )
        },
        containerColor = BgLight
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(vertical = 20.dp)
        ) {
            item {
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = AccentRoseLight)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Available Placeholders", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = AccentRose)
                        Spacer(Modifier.height(6.dp))
                        Text("{name} → Patient Name", fontSize = 13.sp, color = TextSecondary)
                        Text("{doctor} → Doctor Name", fontSize = 13.sp, color = TextSecondary)
                        Text("{date} → Appointment Date", fontSize = 13.sp, color = TextSecondary)
                        Text("{time} → Appointment Time", fontSize = 13.sp, color = TextSecondary)
                    }
                }
            }

            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = CardWhite),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text("Cancellation Message", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextDark)
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            value = template,
                            onValueChange = { template = it; settingsManager.cancellationTemplate = it },
                            modifier = Modifier.fillMaxWidth().height(200.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = AccentRose, unfocusedBorderColor = CardBorder,
                                focusedTextColor = TextDark, unfocusedTextColor = TextDark
                            )
                        )
                    }
                }
            }

            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = CardWhite),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text("Preview", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextDark)
                        Spacer(Modifier.height(12.dp))
                        Surface(shape = RoundedCornerShape(12.dp), color = BgLight, modifier = Modifier.fillMaxWidth()) {
                            Text(
                                template
                                    .replace("{name}", "Ahmed Khan")
                                    .replace("{doctor}", "Dr. Jamil")
                                    .replace("{date}", "15 Feb 2026")
                                    .replace("{time}", "2:30 PM"),
                                modifier = Modifier.padding(16.dp),
                                color = TextDark, fontSize = 14.sp, lineHeight = 22.sp
                            )
                        }
                    }
                }
            }

            item {
                TextButton(
                    onClick = {
                        val default = "❌ *Appointment Cancelled*\n\n👤 Name: {name}\n👨‍⚕️ Doctor: {doctor}\n📅 Was scheduled: {date}\n⏰ Time: {time}\n\nIf you'd like to reschedule, please let us know."
                        template = default; settingsManager.cancellationTemplate = default
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Reset to Default", color = AccentRed, fontWeight = FontWeight.SemiBold) }
            }
        }
    }
}

// ═══════════════════════════════════════════════════
//              AFTER-HOURS SCREEN
// ═══════════════════════════════════════════════════

private val AccentIndigo = Color(0xFF4338CA)
private val AccentIndigoLight = Color(0xFFEEF2FF)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AfterHoursScreen(settingsManager: AIAgentSettingsManager, onBack: () -> Unit) {
    var template by remember { mutableStateOf(settingsManager.afterHoursMessage) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("After-Hours Reply", fontWeight = FontWeight.Bold, color = TextDark) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back", tint = AccentIndigo) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = CardWhite)
            )
        },
        containerColor = BgLight
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(vertical = 20.dp)
        ) {
            item {
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = AccentIndigoLight)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Available Placeholders", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = AccentIndigo)
                        Spacer(Modifier.height(6.dp))
                        Text("{open} → Clinic Opening Time", fontSize = 13.sp, color = TextSecondary)
                        Text("{close} → Clinic Closing Time", fontSize = 13.sp, color = TextSecondary)
                        Text("{clinic} → Clinic Name", fontSize = 13.sp, color = TextSecondary)
                    }
                }
            }

            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = CardWhite),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text("After-Hours Message", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextDark)
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            value = template,
                            onValueChange = { template = it; settingsManager.afterHoursMessage = it },
                            modifier = Modifier.fillMaxWidth().height(200.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = AccentIndigo, unfocusedBorderColor = CardBorder,
                                focusedTextColor = TextDark, unfocusedTextColor = TextDark
                            )
                        )
                    }
                }
            }

            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = CardWhite),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text("Preview", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextDark)
                        Spacer(Modifier.height(12.dp))
                        Surface(shape = RoundedCornerShape(12.dp), color = BgLight, modifier = Modifier.fillMaxWidth()) {
                            Text(
                                template
                                    .replace("{open}", to12h(settingsManager.clinicOpenTime))
                                    .replace("{close}", to12h(settingsManager.clinicCloseTime))
                                    .replace("{clinic}", settingsManager.clinicName),
                                modifier = Modifier.padding(16.dp),
                                color = TextDark, fontSize = 14.sp, lineHeight = 22.sp
                            )
                        }
                    }
                }
            }

            item {
                TextButton(
                    onClick = {
                        val default = "🌙 Thank you for contacting us!\n\nOur clinic is currently closed.\n🕐 Hours: {open} - {close}\n\nWe'll respond when we reopen. For emergencies, please visit the nearest hospital."
                        template = default; settingsManager.afterHoursMessage = default
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Reset to Default", color = AccentRed, fontWeight = FontWeight.SemiBold) }
            }
        }
    }
}

// ═══════════════════════════════════════════════════
//                   COMPONENTS
// ═══════════════════════════════════════════════════

@Composable
fun DoctorCard(doctor: DoctorEntity, onDelete: () -> Unit) {
    val totalSlots = calculateTotalSlots(doctor.startTime, doctor.endTime,
        doctor.lunchStartTime, doctor.lunchEndTime, doctor.slotDurationMinutes)

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CardWhite),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = CircleShape,
                    color = Accent.copy(0.1f),
                    modifier = Modifier.size(44.dp)
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(doctor.name.first().toString(), color = Accent,
                            fontWeight = FontWeight.Bold, fontSize = 20.sp)
                    }
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("Dr. ${doctor.name}", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = TextDark)
                    Text(doctor.specialty, fontSize = 13.sp, color = TextSecondary)
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Delete, "Delete", tint = AccentRed, modifier = Modifier.size(20.dp))
                }
            }

            Spacer(Modifier.height(14.dp))
            Divider(color = DividerColor)
            Spacer(Modifier.height(12.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                InfoChip(Icons.Default.Schedule, "Hours", "${to12h(doctor.startTime)} - ${to12h(doctor.endTime)}")
                InfoChip(Icons.Default.Timer, "Slot", "${doctor.slotDurationMinutes} min")
                InfoChip(Icons.Default.EventAvailable, "Slots", "$totalSlots/day")
            }

            Spacer(Modifier.height(10.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                Surface(shape = RoundedCornerShape(8.dp), color = AccentOrangeLight) {
                    Text("Lunch: ${to12h(doctor.lunchStartTime)} - ${to12h(doctor.lunchEndTime)}",
                        color = AccentOrange, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp))
                }
            }
        }
    }
}

@Composable
fun InfoChip(icon: ImageVector, label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, label, tint = TextMuted, modifier = Modifier.size(16.dp))
        Spacer(Modifier.height(3.dp))
        Text(value, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TextDark)
        Text(label, fontSize = 10.sp, color = TextMuted)
    }
}

// ─── Time Picker ───

@Composable
fun TimePicker12h(label: String, time24: String, onTimeChange: (String) -> Unit) {
    val (h12, min, isAm) = parse24h(time24)
    var hour by remember(time24) { mutableStateOf(h12) }
    var minute by remember(time24) { mutableStateOf(min) }
    var am by remember(time24) { mutableStateOf(isAm) }

    Column {
        Text(label, color = TextSecondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = RoundedCornerShape(10.dp), color = BgLight) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = {
                        hour = if (hour <= 1) 12 else hour - 1
                        onTimeChange(to24h(hour, minute, am))
                    }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.KeyboardArrowDown, "Down", tint = TextMuted, modifier = Modifier.size(18.dp))
                    }
                    Text("%02d".format(hour), color = TextDark, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    IconButton(onClick = {
                        hour = if (hour >= 12) 1 else hour + 1
                        onTimeChange(to24h(hour, minute, am))
                    }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.KeyboardArrowUp, "Up", tint = TextMuted, modifier = Modifier.size(18.dp))
                    }
                }
            }

            Text(" : ", color = TextDark, fontWeight = FontWeight.Bold, fontSize = 18.sp)

            Surface(shape = RoundedCornerShape(10.dp), color = BgLight) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = {
                        minute = if (minute <= 0) 55 else minute - 5
                        onTimeChange(to24h(hour, minute, am))
                    }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.KeyboardArrowDown, "Down", tint = TextMuted, modifier = Modifier.size(18.dp))
                    }
                    Text("%02d".format(minute), color = TextDark, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    IconButton(onClick = {
                        minute = if (minute >= 55) 0 else minute + 5
                        onTimeChange(to24h(hour, minute, am))
                    }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.KeyboardArrowUp, "Up", tint = TextMuted, modifier = Modifier.size(18.dp))
                    }
                }
            }

            Spacer(Modifier.width(8.dp))

            Surface(
                shape = RoundedCornerShape(10.dp),
                color = if (am) AccentGreenLight else AccentOrangeLight,
                modifier = Modifier.clickable {
                    am = !am; onTimeChange(to24h(hour, minute, am))
                }
            ) {
                Text(
                    if (am) "AM" else "PM",
                    color = if (am) AccentGreen else AccentOrange,
                    fontWeight = FontWeight.Bold, fontSize = 16.sp,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                )
            }
        }
    }
}

// ─── Dialogs ───

@Composable
fun AddHolidayDialog(onDismiss: () -> Unit, onSave: (String, String) -> Unit) {
    var date by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CardWhite,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.EventBusy, "Holiday", tint = AccentRed)
                Spacer(Modifier.width(8.dp))
                Text("Add Holiday", color = TextDark, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = date, onValueChange = { date = it },
                    label = { Text("Date (YYYY-MM-DD)") },
                    placeholder = { Text("2026-03-14", color = TextMuted) },
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Accent, unfocusedBorderColor = CardBorder,
                        focusedTextColor = TextDark, unfocusedTextColor = TextDark
                    )
                )
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text("Holiday Name") },
                    placeholder = { Text("e.g. Holi, Eid", color = TextMuted) },
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Accent, unfocusedBorderColor = CardBorder,
                        focusedTextColor = TextDark, unfocusedTextColor = TextDark
                    )
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { if (date.isNotBlank() && name.isNotBlank()) onSave(date, name) },
                colors = ButtonDefaults.buttonColors(containerColor = AccentRed),
                shape = RoundedCornerShape(10.dp)
            ) { Text("Add Holiday") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = TextMuted) }
        }
    )
}

@Composable
fun AddDoctorDialog(onDismiss: () -> Unit, onSave: (DoctorEntity) -> Unit) {
    val context = LocalContext.current
    val settingsManager = remember { AIAgentSettingsManager(context) }
    
    var name by remember { mutableStateOf("") }
    var specialty by remember { mutableStateOf("") }
    var startTime by remember { mutableStateOf("09:00") }
    var endTime by remember { mutableStateOf("17:00") }
    var lunchStart by remember { mutableStateOf("13:00") }
    var lunchEnd by remember { mutableStateOf("14:00") }
    var slotDuration by remember { mutableStateOf("15") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Validate doctor times against clinic times
    fun validateDoctorTimes(): String? {
        val clinicOpen = settingsManager.clinicOpenTime
        val clinicClose = settingsManager.clinicCloseTime
        
        val clinicOpenMin = timeToMinutes(clinicOpen)
        val clinicCloseMin = timeToMinutes(clinicClose)
        val doctorStartMin = timeToMinutes(startTime)
        val doctorEndMin = timeToMinutes(endTime)
        
        android.util.Log.d("DoctorValidation", "🏥 Clinic: ${to12h(clinicOpen)} - ${to12h(clinicClose)} ($clinicOpenMin - $clinicCloseMin min)")
        android.util.Log.d("DoctorValidation", "👨‍⚕️ Doctor: ${to12h(startTime)} - ${to12h(endTime)} ($doctorStartMin - $doctorEndMin min)")
        
        // Check if doctor start time is before clinic open time
        if (doctorStartMin < clinicOpenMin) {
            return "❌ Doctor start time (${to12h(startTime)}) is before clinic opens (${to12h(clinicOpen)})\n\n" +
                   "Please increase clinic opening time first or change doctor start time."
        }
        
        // Check if doctor end time is after clinic close time
        if (doctorEndMin > clinicCloseMin) {
            return "❌ Doctor end time (${to12h(endTime)}) is after clinic closes (${to12h(clinicClose)})\n\n" +
                   "Please increase clinic closing time first or change doctor end time."
        }
        
        return null
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CardWhite,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.PersonAdd, "Doctor", tint = AccentOrange)
                Spacer(Modifier.width(8.dp))
                Text("Add Doctor", color = TextDark, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Show clinic hours info
                item {
                    Card(
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(containerColor = AccentLight),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Info, "Info", tint = Accent, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text("Clinic Hours", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Accent)
                                Text(
                                    "${to12h(settingsManager.clinicOpenTime)} - ${to12h(settingsManager.clinicCloseTime)}",
                                    fontSize = 13.sp, color = Accent, fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
                
                // Show error message if validation fails
                errorMessage?.let { error ->
                    item {
                        Card(
                            shape = RoundedCornerShape(10.dp),
                            colors = CardDefaults.cardColors(containerColor = AccentRedLight),
                            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Icon(Icons.Default.Warning, "Error", tint = AccentRed, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(error, fontSize = 13.sp, color = AccentRed, lineHeight = 18.sp)
                            }
                        }
                    }
                }
                
                item {
                    OutlinedTextField(
                        value = name, onValueChange = { name = it },
                        label = { Text("Doctor Name") },
                        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Accent, unfocusedBorderColor = CardBorder,
                            focusedTextColor = TextDark, unfocusedTextColor = TextDark
                        )
                    )
                }
                item {
                    OutlinedTextField(
                        value = specialty, onValueChange = { specialty = it },
                        label = { Text("Specialty") },
                        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Accent, unfocusedBorderColor = CardBorder,
                            focusedTextColor = TextDark, unfocusedTextColor = TextDark
                        )
                    )
                }
                item {
                    OutlinedTextField(
                        value = slotDuration, onValueChange = { slotDuration = it },
                        label = { Text("Slot Duration (minutes)") },
                        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Accent, unfocusedBorderColor = CardBorder,
                            focusedTextColor = TextDark, unfocusedTextColor = TextDark
                        )
                    )
                }
                item { 
                    TimePicker12h("Start Time", startTime) { 
                        startTime = it
                        errorMessage = validateDoctorTimes()
                    } 
                }
                item { 
                    TimePicker12h("End Time", endTime) { 
                        endTime = it
                        errorMessage = validateDoctorTimes()
                    } 
                }
                item { TimePicker12h("Lunch Start", lunchStart) { lunchStart = it } }
                item { TimePicker12h("Lunch End", lunchEnd) { lunchEnd = it } }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val validationError = validateDoctorTimes()
                    if (validationError != null) {
                        errorMessage = validationError
                    } else if (name.isNotBlank()) {
                        onSave(DoctorEntity(
                            name = name, specialty = specialty,
                            startTime = startTime, endTime = endTime,
                            lunchStartTime = lunchStart, lunchEndTime = lunchEnd,
                            slotDurationMinutes = slotDuration.toIntOrNull() ?: 15
                        ))
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = AccentOrange),
                shape = RoundedCornerShape(10.dp),
                enabled = errorMessage == null && name.isNotBlank()
            ) { Text("Save Doctor") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = TextMuted) }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReminderSettingsScreen(settingsManager: AIAgentSettingsManager, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isEnabled by remember { mutableStateOf(settingsManager.clinicReminderEnabled) }
    var timeBefore by remember { mutableStateOf(settingsManager.clinicReminderTimeBefore.toString()) }
    var template by remember { mutableStateOf(settingsManager.clinicReminderTemplate) }
    var showTestDialog by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Reminder Settings", fontWeight = FontWeight.Bold, color = TextDark) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back", tint = Accent) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = CardWhite)
            )
        },
        containerColor = BgLight
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(vertical = 20.dp)
        ) {
            // Toggle
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = CardWhite),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(20.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Automatic Reminders", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextDark)
                            Text(
                                if (isEnabled) "Reminders will be sent automatically" else "Reminders are disabled",
                                color = if (isEnabled) AccentGreen else TextSecondary, fontSize = 13.sp
                            )
                        }
                        Switch(
                            checked = isEnabled,
                            onCheckedChange = { 
                                isEnabled = it
                                settingsManager.clinicReminderEnabled = it
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = AccentGreen
                            )
                        )
                    }
                }
            }

            if (isEnabled) {
                item {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = CardWhite),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(
                             modifier = Modifier.padding(20.dp),
                             verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Text("Configuration", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextDark)
                            
                            // Time Before
                            OutlinedTextField(
                                value = timeBefore,
                                onValueChange = { 
                                    if (it.all { char -> char.isDigit() }) {
                                        timeBefore = it
                                        settingsManager.clinicReminderTimeBefore = it.toIntOrNull() ?: 60
                                    }
                                },
                                label = { Text("Send Reminder Before (Minutes)") },
                                placeholder = { Text("e.g. 60 (1 hour)") },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Accent, unfocusedBorderColor = CardBorder,
                                    focusedTextColor = TextDark, unfocusedTextColor = TextDark
                                )
                            )
                            
                            // Template
                            OutlinedTextField(
                                value = template,
                                onValueChange = { 
                                    template = it
                                    settingsManager.clinicReminderTemplate = it
                                },
                                label = { Text("Reminder Message Template") },
                                modifier = Modifier.fillMaxWidth().height(180.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Accent, unfocusedBorderColor = CardBorder,
                                    focusedTextColor = TextDark, unfocusedTextColor = TextDark
                                )
                            )
                             Text("Placeholders: {name}, {doctor}, {date}, {time}", fontSize = 12.sp, color = TextSecondary)
                        }
                    }
                }
                
                // Test Send Button
                item {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = AccentLight),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.Science, "Test", tint = Accent, modifier = Modifier.size(24.dp))
                                Text("Test Reminder System", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextDark)
                            }
                            
                            Text(
                                "Send a test reminder to verify your configuration",
                                fontSize = 13.sp,
                                color = TextSecondary
                            )
                            
                            Button(
                                onClick = { showTestDialog = true },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Accent)
                            ) {
                                Icon(Icons.Default.Send, "Send Test", modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Send Test Reminder", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                            }
                            
                            if (testResult != null) {
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    testResult!!,
                                    fontSize = 13.sp,
                                    color = if (testResult!!.contains("✅")) AccentGreen else AccentRed,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }
                
                // Worker Status
                item {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = CardWhite),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text("System Status", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextDark)
                            
                            val workerStatus = remember {
                                com.message.bulksend.reminders.GlobalReminderManager.checkWorkerStatus(context)
                            }
                            
                            Text(
                                "Worker: $workerStatus",
                                fontSize = 13.sp,
                                color = if (workerStatus.contains("✅")) AccentGreen else AccentRed
                            )
                            
                            Text(
                                "Reminders are checked every 15 minutes automatically",
                                fontSize = 12.sp,
                                color = TextMuted
                            )
                        }
                    }
                }
            }
        }
    }
    
    // Test Dialog
    if (showTestDialog) {
        var testPhone by remember { mutableStateOf("+919876543210") }
        
        AlertDialog(
            onDismissRequest = { showTestDialog = false },
            title = { Text("Send Test Reminder", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("This will create a test reminder that will be sent immediately.")
                    
                    OutlinedTextField(
                        value = testPhone,
                        onValueChange = { testPhone = it },
                        label = { Text("Test Phone Number") },
                        placeholder = { Text("e.g. +919876543210") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Accent,
                            unfocusedBorderColor = CardBorder
                        )
                    )
                    
                    Text(
                        "Note: Make sure Accessibility Service is enabled",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (testPhone.isBlank()) {
                            testResult = "❌ Please enter a phone number"
                            showTestDialog = false
                            return@Button
                        }
                        
                        scope.launch {
                            try {
                                val manager = com.message.bulksend.reminders.GlobalReminderManager(context)
                                
                                // Create test reminder for immediate sending (1 minute ago)
                                val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
                                val timeFormat = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
                                val testTime = System.currentTimeMillis() - 60000 // 1 minute ago
                                
                                manager.addReminder(
                                    phone = testPhone,
                                    name = "Test Patient",
                                    date = dateFormat.format(java.util.Date(testTime)),
                                    time = timeFormat.format(java.util.Date(testTime)),
                                    prompt = "Test appointment reminder",
                                    templateType = "CLINIC"
                                )
                                
                                testResult = "✅ Test reminder created for $testPhone! Worker triggered."
                                showTestDialog = false
                                
                                // Trigger worker manually for immediate test
                                androidx.work.WorkManager.getInstance(context)
                                    .enqueue(
                                        androidx.work.OneTimeWorkRequestBuilder<com.message.bulksend.reminders.ReminderCheckWorker>()
                                            .build()
                                    )
                                
                            } catch (e: Exception) {
                                testResult = "❌ Error: ${e.message}"
                                showTestDialog = false
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Accent),
                    enabled = testPhone.isNotBlank()
                ) {
                    Text("Send Test")
                }
            },
            dismissButton = {
                TextButton(onClick = { showTestDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
