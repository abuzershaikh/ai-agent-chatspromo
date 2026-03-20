package com.message.bulksend.leadmanager.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.message.bulksend.leadmanager.model.Lead
import com.message.bulksend.leadmanager.model.FollowUp
import com.message.bulksend.leadmanager.model.FollowUpType
import java.text.SimpleDateFormat
import java.util.*

// Follow-up type colors
val followUpTypeColors = mapOf(
    FollowUpType.CALL to Color(0xFF3B82F6),
    FollowUpType.EMAIL to Color(0xFF8B5CF6),
    FollowUpType.MEETING to Color(0xFFF59E0B),
    FollowUpType.WHATSAPP to Color(0xFF22C55E),
    FollowUpType.VISIT to Color(0xFFEC4899),
    FollowUpType.OTHER to Color(0xFF64748B)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduledScreen(
    leads: List<Lead>,
    onMarkComplete: (Lead, FollowUp) -> Unit = { _, _ -> },
    onReschedule: (Lead, FollowUp, Long) -> Unit = { _, _, _ -> },
    onLeadClick: (Lead) -> Unit = {}
) {
    val context = LocalContext.current
    val backgroundBrush = Brush.verticalGradient(
        colors = listOf(Color(0xFFF0F4FF), Color(0xFFE8EFFF), Color(0xFFF5F7FA))
    )

    // Get all follow-ups with lead info
    val allFollowUps = leads.flatMap { lead ->
        lead.followUps.map { followUp -> followUp to lead }
    }
    
    val today = Calendar.getInstance()
    val startOfToday = (today.clone() as Calendar).apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    val startOfTomorrow = (startOfToday.clone() as Calendar).apply {
        add(Calendar.DAY_OF_YEAR, 1)
    }
    
    // Categorize follow-ups
    val overdueFollowUps = allFollowUps.filter { (followUp, _) ->
        followUp.scheduledDate < startOfToday.timeInMillis && !followUp.isCompleted
    }.sortedBy { it.first.scheduledDate }
    
    val todayFollowUps = allFollowUps.filter { (followUp, _) ->
        followUp.scheduledDate >= startOfToday.timeInMillis &&
            followUp.scheduledDate < startOfTomorrow.timeInMillis &&
            !followUp.isCompleted
    }.sortedBy { it.first.scheduledDate }
    
    val upcomingFollowUps = allFollowUps.filter { (followUp, _) ->
        followUp.scheduledDate >= startOfTomorrow.timeInMillis && !followUp.isCompleted
    }.sortedBy { it.first.scheduledDate }
    
    val completedFollowUps = allFollowUps.filter { (followUp, _) ->
        followUp.isCompleted
    }.sortedByDescending { it.first.completedDate }
    
    // Calendar state
    var currentMonth by remember { mutableStateOf(Calendar.getInstance()) }
    var selectedDate by remember { mutableStateOf<Calendar?>(null) }
    var selectedTab by remember { mutableStateOf(0) }
    
    // Dialogs
    var showRescheduleDialog by remember { mutableStateOf<Pair<FollowUp, Lead>?>(null) }
    var showCompleteConfirm by remember { mutableStateOf<Pair<FollowUp, Lead>?>(null) }
    
    // Dates with follow-ups for calendar
    val datesWithFollowUps = remember(allFollowUps, currentMonth) {
        allFollowUps.filter { !it.first.isCompleted }.groupBy { (followUp, _) ->
            val cal = Calendar.getInstance().apply { timeInMillis = followUp.scheduledDate }
            Triple(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH))
        }
    }

    // Filter for selected date
    val displayFollowUps = remember(selectedDate, allFollowUps, selectedTab) {
        when {
            selectedDate != null -> allFollowUps.filter { (followUp, _) ->
                val cal = Calendar.getInstance().apply { timeInMillis = followUp.scheduledDate }
                cal.get(Calendar.YEAR) == selectedDate!!.get(Calendar.YEAR) &&
                cal.get(Calendar.MONTH) == selectedDate!!.get(Calendar.MONTH) &&
                cal.get(Calendar.DAY_OF_MONTH) == selectedDate!!.get(Calendar.DAY_OF_MONTH) &&
                !followUp.isCompleted
            }
            selectedTab == 0 -> (overdueFollowUps + todayFollowUps + upcomingFollowUps.take(5))
                .distinctBy { it.first.id }
            selectedTab == 1 -> overdueFollowUps
            selectedTab == 2 -> todayFollowUps
            selectedTab == 3 -> upcomingFollowUps
            else -> completedFollowUps.take(20)
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(backgroundBrush)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header with stats
            item {
                ScheduleHeader(
                    overdueCount = overdueFollowUps.size,
                    todayCount = todayFollowUps.size,
                    upcomingCount = upcomingFollowUps.size,
                    completedCount = completedFollowUps.size
                )
            }
            
            // Calendar
            item {
                CalendarCard(
                    currentMonth = currentMonth,
                    selectedDate = selectedDate,
                    datesWithFollowUps = datesWithFollowUps,
                    onMonthChange = { currentMonth = it },
                    onDateSelect = { selectedDate = it }
                )
            }
            
            // Legend
            item { FollowUpTypeLegend() }
            
            // Tab filters
            item {
                ScheduleTabRow(
                    selectedTab = selectedTab,
                    overdueCount = overdueFollowUps.size,
                    todayCount = todayFollowUps.size,
                    upcomingCount = upcomingFollowUps.size,
                    completedCount = completedFollowUps.size,
                    onTabSelect = { selectedTab = it; selectedDate = null }
                )
            }

            // Section header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        when {
                            selectedDate != null -> "Scheduled for ${SimpleDateFormat("MMM dd", Locale.getDefault()).format(selectedDate!!.time)}"
                            selectedTab == 0 -> "All Follow-ups"
                            selectedTab == 1 -> "Overdue"
                            selectedTab == 2 -> "Today"
                            selectedTab == 3 -> "Upcoming"
                            else -> "Completed"
                        },
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1a1a2e)
                    )
                    if (selectedDate != null) {
                        TextButton(onClick = { selectedDate = null }) {
                            Text("Clear", color = Color(0xFF3B82F6), fontSize = 12.sp)
                        }
                    }
                }
            }
            
            // Follow-up cards
            if (displayFollowUps.isEmpty()) {
                item { EmptyScheduleCard(selectedTab) }
            } else {
                items(displayFollowUps, key = { it.first.id }) { (followUp, lead) ->
                    CRMFollowUpCard(
                        followUp = followUp,
                        lead = lead,
                        isOverdue = overdueFollowUps.any { it.first.id == followUp.id },
                        onComplete = { showCompleteConfirm = followUp to lead },
                        onReschedule = { showRescheduleDialog = followUp to lead },
                        onCall = {
                            context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${lead.phoneNumber}")))
                        },
                        onWhatsApp = {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/${lead.phoneNumber}")))
                        },
                        onLeadClick = { onLeadClick(lead) }
                    )
                }
            }
            
            item { Spacer(Modifier.height(80.dp)) }
        }
    }

    // Complete confirmation dialog
    if (showCompleteConfirm != null) {
        AlertDialog(
            onDismissRequest = { showCompleteConfirm = null },
            title = { Text("Mark as Complete?", fontWeight = FontWeight.Bold) },
            text = { Text("Mark '${showCompleteConfirm!!.first.title}' for ${showCompleteConfirm!!.second.name} as completed?") },
            confirmButton = {
                Button(
                    onClick = {
                        onMarkComplete(showCompleteConfirm!!.second, showCompleteConfirm!!.first)
                        showCompleteConfirm = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
                ) { Text("Complete") }
            },
            dismissButton = {
                TextButton(onClick = { showCompleteConfirm = null }) {
                    Text("Cancel", color = Color(0xFF64748B))
                }
            },
            containerColor = Color.White
        )
    }
    
    // Reschedule dialog
    if (showRescheduleDialog != null) {
        RescheduleDialog(
            followUp = showRescheduleDialog!!.first,
            lead = showRescheduleDialog!!.second,
            onDismiss = { showRescheduleDialog = null },
            onReschedule = { newDate ->
                onReschedule(showRescheduleDialog!!.second, showRescheduleDialog!!.first, newDate)
                showRescheduleDialog = null
            }
        )
    }
}

@Composable
fun ScheduleHeader(overdueCount: Int, todayCount: Int, upcomingCount: Int, completedCount: Int) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text("My Schedule", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1a1a2e))
            Text("${todayCount} today • ${upcomingCount} upcoming", fontSize = 12.sp, color = Color(0xFF94A3B8))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (overdueCount > 0) {
                StatBadge(count = overdueCount, color = Color(0xFFEF4444), icon = Icons.Default.Warning)
            }
            StatBadge(count = completedCount, color = Color(0xFF10B981), icon = Icons.Default.CheckCircle)
        }
    }
}

@Composable
fun StatBadge(count: Int, color: Color, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Surface(color = color.copy(alpha = 0.15f), shape = CircleShape) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(icon, null, tint = color, modifier = Modifier.size(14.dp))
            Text(count.toString(), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = color)
        }
    }
}

@Composable
fun ScheduleTabRow(
    selectedTab: Int,
    overdueCount: Int,
    todayCount: Int,
    upcomingCount: Int,
    completedCount: Int,
    onTabSelect: (Int) -> Unit
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        item { FilterChipItem("All", selectedTab == 0, Color(0xFF3B82F6)) { onTabSelect(0) } }
        if (overdueCount > 0) {
            item { FilterChipItem("Overdue ($overdueCount)", selectedTab == 1, Color(0xFFEF4444)) { onTabSelect(1) } }
        }
        item { FilterChipItem("Today ($todayCount)", selectedTab == 2, Color(0xFFF59E0B)) { onTabSelect(2) } }
        item { FilterChipItem("Upcoming ($upcomingCount)", selectedTab == 3, Color(0xFF8B5CF6)) { onTabSelect(3) } }
        item { FilterChipItem("Completed ($completedCount)", selectedTab == 4, Color(0xFF10B981)) { onTabSelect(4) } }
    }
}

@Composable
fun FilterChipItem(label: String, selected: Boolean, color: Color, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.clickable(onClick = onClick),
        color = if (selected) color else Color.White,
        shape = RoundedCornerShape(20.dp),
        shadowElevation = if (selected) 0.dp else 2.dp
    ) {
        Text(
            label,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) Color.White else Color(0xFF64748B),
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
        )
    }
}

@Composable
fun CalendarCard(
    currentMonth: Calendar,
    selectedDate: Calendar?,
    datesWithFollowUps: Map<Triple<Int, Int, Int>, List<Pair<FollowUp, Lead>>>,
    onMonthChange: (Calendar) -> Unit,
    onDateSelect: (Calendar?) -> Unit
) {
    val monthFormat = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
    val today = Calendar.getInstance()
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Month header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(monthFormat.format(currentMonth.time), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1a1a2e))
                Row {
                    IconButton(onClick = {
                        val newMonth = currentMonth.clone() as Calendar
                        newMonth.add(Calendar.MONTH, -1)
                        onMonthChange(newMonth)
                    }) { Icon(Icons.Default.ChevronLeft, null, tint = Color(0xFF64748B)) }
                    IconButton(onClick = {
                        val newMonth = currentMonth.clone() as Calendar
                        newMonth.add(Calendar.MONTH, 1)
                        onMonthChange(newMonth)
                    }) { Icon(Icons.Default.ChevronRight, null, tint = Color(0xFF64748B)) }
                }
            }
            
            Spacer(Modifier.height(8.dp))
            
            // Day headers
            Row(modifier = Modifier.fillMaxWidth()) {
                listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun").forEach { day ->
                    Text(day, modifier = Modifier.weight(1f), textAlign = TextAlign.Center, fontSize = 11.sp, color = Color(0xFF94A3B8), fontWeight = FontWeight.Medium)
                }
            }
            
            Spacer(Modifier.height(6.dp))
            
            // Calendar grid
            val firstDayOfMonth = currentMonth.clone() as Calendar
            firstDayOfMonth.set(Calendar.DAY_OF_MONTH, 1)
            val startDayOfWeek = (firstDayOfMonth.get(Calendar.DAY_OF_WEEK) + 5) % 7
            val daysInMonth = currentMonth.getActualMaximum(Calendar.DAY_OF_MONTH)

            var dayCounter = 1
            for (week in 0..5) {
                if (dayCounter > daysInMonth) break
                Row(modifier = Modifier.fillMaxWidth()) {
                    for (dayOfWeek in 0..6) {
                        val cellDay = if (week == 0 && dayOfWeek < startDayOfWeek) null
                        else if (dayCounter <= daysInMonth) { dayCounter++; dayCounter - 1 }
                        else null
                        
                        Box(modifier = Modifier.weight(1f).aspectRatio(1f).padding(1.dp), contentAlignment = Alignment.Center) {
                            if (cellDay != null) {
                                val dateKey = Triple(currentMonth.get(Calendar.YEAR), currentMonth.get(Calendar.MONTH), cellDay)
                                val followUpsOnDate = datesWithFollowUps[dateKey] ?: emptyList()
                                val isToday = today.get(Calendar.YEAR) == currentMonth.get(Calendar.YEAR) &&
                                        today.get(Calendar.MONTH) == currentMonth.get(Calendar.MONTH) &&
                                        today.get(Calendar.DAY_OF_MONTH) == cellDay
                                val isSelected = selectedDate?.let {
                                    it.get(Calendar.YEAR) == currentMonth.get(Calendar.YEAR) &&
                                    it.get(Calendar.MONTH) == currentMonth.get(Calendar.MONTH) &&
                                    it.get(Calendar.DAY_OF_MONTH) == cellDay
                                } ?: false
                                
                                CalendarDayCell(cellDay, isToday, isSelected, followUpsOnDate) {
                                    val cal = currentMonth.clone() as Calendar
                                    cal.set(Calendar.DAY_OF_MONTH, cellDay)
                                    onDateSelect(if (isSelected) null else cal)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CalendarDayCell(day: Int, isToday: Boolean, isSelected: Boolean, followUps: List<Pair<FollowUp, Lead>>, onClick: () -> Unit) {
    val hasFollowUps = followUps.isNotEmpty()
    val primaryColor = if (hasFollowUps) followUpTypeColors[followUps.first().first.type] ?: Color(0xFF3B82F6) else Color.Transparent
    val hasOverdue = followUps.any { Calendar.getInstance().apply { timeInMillis = it.first.scheduledDate }.before(Calendar.getInstance()) }
    
    val backgroundColor = when {
        isSelected -> Color(0xFF3B82F6)
        hasOverdue -> Color(0xFFEF4444).copy(alpha = 0.2f)
        hasFollowUps -> primaryColor.copy(alpha = 0.2f)
        isToday -> Color(0xFFF59E0B).copy(alpha = 0.2f)
        else -> Color.Transparent
    }
    val textColor = when {
        isSelected -> Color.White
        hasOverdue -> Color(0xFFEF4444)
        hasFollowUps -> primaryColor
        isToday -> Color(0xFFF59E0B)
        else -> Color(0xFF1a1a2e)
    }
    
    Box(
        modifier = Modifier.size(32.dp).clip(CircleShape).background(backgroundColor)
            .then(if (hasFollowUps && !isSelected) Modifier.border(2.dp, if (hasOverdue) Color(0xFFEF4444) else primaryColor, CircleShape) else Modifier)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(day.toString(), fontSize = 12.sp, fontWeight = if (isToday || hasFollowUps) FontWeight.Bold else FontWeight.Normal, color = textColor)
    }
}

@Composable
fun FollowUpTypeLegend() {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items(FollowUpType.entries.toList()) { type ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(modifier = Modifier.size(8.dp).background(followUpTypeColors[type] ?: Color.Gray, RoundedCornerShape(2.dp)))
                Text(type.displayName, fontSize = 10.sp, color = Color(0xFF64748B))
            }
        }
    }
}

@Composable
fun CRMFollowUpCard(
    followUp: FollowUp,
    lead: Lead,
    isOverdue: Boolean,
    onComplete: () -> Unit,
    onReschedule: () -> Unit,
    onCall: () -> Unit,
    onWhatsApp: () -> Unit,
    onLeadClick: () -> Unit
) {
    val dateFormat = SimpleDateFormat("dd", Locale.getDefault())
    val dayFormat = SimpleDateFormat("EEE", Locale.getDefault())
    val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())
    val followUpDate = Date(followUp.scheduledDate)
    val typeColor = followUpTypeColors[followUp.type] ?: Color(0xFF3B82F6)
    
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onLeadClick),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Header with status color
            Box(
                modifier = Modifier.fillMaxWidth().height(4.dp)
                    .background(if (isOverdue) Color(0xFFEF4444) else if (followUp.isCompleted) Color(0xFF10B981) else typeColor)
            )
            
            Row(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top
            ) {
                // Date column
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(40.dp)) {
                    Text(dateFormat.format(followUpDate), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = if (isOverdue) Color(0xFFEF4444) else Color(0xFF1a1a2e))
                    Text(dayFormat.format(followUpDate), fontSize = 11.sp, color = Color(0xFF94A3B8))
                }
                
                // Divider
                Box(modifier = Modifier.width(3.dp).height(60.dp).background(if (isOverdue) Color(0xFFEF4444) else typeColor, RoundedCornerShape(2.dp)))

                // Content
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(followUp.title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1a1a2e), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        if (isOverdue) {
                            Surface(color = Color(0xFFEF4444).copy(alpha = 0.15f), shape = RoundedCornerShape(4.dp)) {
                                Text("OVERDUE", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFFEF4444), modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                            }
                        }
                        if (followUp.isCompleted) {
                            Surface(color = Color(0xFF10B981).copy(alpha = 0.15f), shape = RoundedCornerShape(4.dp)) {
                                Text("DONE", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFF10B981), modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                            }
                        }
                    }
                    
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Surface(color = typeColor.copy(alpha = 0.15f), shape = RoundedCornerShape(4.dp)) {
                            Text(followUp.type.displayName, fontSize = 10.sp, color = typeColor, fontWeight = FontWeight.Medium, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                        }
                        Text(timeFormat.format(followUpDate), fontSize = 11.sp, color = Color(0xFF64748B))
                    }
                    
                    // Lead info
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Default.Person, null, tint = Color(0xFF94A3B8), modifier = Modifier.size(14.dp))
                        Text(lead.name, fontSize = 12.sp, color = Color(0xFF64748B), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("•", color = Color(0xFF94A3B8))
                        Text(lead.phoneNumber, fontSize = 11.sp, color = Color(0xFF94A3B8))
                    }
                }
            }

            // Action buttons
            if (!followUp.isCompleted) {
                HorizontalDivider(color = Color(0xFFF1F5F9))
                Row(
                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    // Call
                    ActionButton(icon = Icons.Default.Phone, label = "Call", color = Color(0xFF3B82F6), onClick = onCall)
                    // WhatsApp
                    ActionButton(icon = Icons.Default.Chat, label = "WhatsApp", color = Color(0xFF22C55E), onClick = onWhatsApp)
                    // Reschedule
                    ActionButton(icon = Icons.Default.Schedule, label = "Reschedule", color = Color(0xFF8B5CF6), onClick = onReschedule)
                    // Complete
                    ActionButton(icon = Icons.Default.Check, label = "Complete", color = Color(0xFF10B981), onClick = onComplete)
                }
            }
        }
    }
}

@Composable
fun ActionButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, color: Color, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick).padding(8.dp)
    ) {
        Box(
            modifier = Modifier.size(36.dp).background(color.copy(alpha = 0.15f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = color, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.height(4.dp))
        Text(label, fontSize = 10.sp, color = color, fontWeight = FontWeight.Medium)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RescheduleDialog(followUp: FollowUp, lead: Lead, onDismiss: () -> Unit, onReschedule: (Long) -> Unit) {
    var selectedDate by remember { mutableStateOf(Calendar.getInstance().apply { timeInMillis = followUp.scheduledDate }) }
    val datePickerState = rememberDatePickerState(initialSelectedDateMillis = followUp.scheduledDate)
    var showDatePicker by remember { mutableStateOf(false) }
    var selectedHour by remember { mutableStateOf(selectedDate.get(Calendar.HOUR_OF_DAY)) }
    var selectedMinute by remember { mutableStateOf(selectedDate.get(Calendar.MINUTE)) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Reschedule Follow-up", fontWeight = FontWeight.Bold, color = Color(0xFF1a1a2e)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("${followUp.title} for ${lead.name}", fontSize = 14.sp, color = Color(0xFF64748B))
                
                // Date picker button
                OutlinedButton(
                    onClick = { showDatePicker = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.DateRange, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(selectedDate.time))
                }
                
                // Time picker
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = String.format("%02d", selectedHour),
                        onValueChange = { selectedHour = it.toIntOrNull()?.coerceIn(0, 23) ?: selectedHour },
                        label = { Text("Hour") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = String.format("%02d", selectedMinute),
                        onValueChange = { selectedMinute = it.toIntOrNull()?.coerceIn(0, 59) ?: selectedMinute },
                        label = { Text("Min") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }
                
                // Quick options
                Text("Quick Options", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Color(0xFF64748B))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    QuickDateChip("Tomorrow") {
                        selectedDate = Calendar.getInstance().apply { add(Calendar.DAY_OF_MONTH, 1) }
                    }
                    QuickDateChip("Next Week") {
                        selectedDate = Calendar.getInstance().apply { add(Calendar.WEEK_OF_YEAR, 1) }
                    }
                    QuickDateChip("+3 Days") {
                        selectedDate = Calendar.getInstance().apply { add(Calendar.DAY_OF_MONTH, 3) }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    selectedDate.set(Calendar.HOUR_OF_DAY, selectedHour)
                    selectedDate.set(Calendar.MINUTE, selectedMinute)
                    onReschedule(selectedDate.timeInMillis)
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8B5CF6))
            ) { Text("Reschedule") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = Color(0xFF64748B)) } },
        containerColor = Color.White
    )
    
    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { selectedDate.timeInMillis = it }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancel") } }
        ) { DatePicker(state = datePickerState) }
    }
}

@Composable
fun QuickDateChip(label: String, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.clickable(onClick = onClick),
        color = Color(0xFF8B5CF6).copy(alpha = 0.1f),
        shape = RoundedCornerShape(16.dp)
    ) {
        Text(label, fontSize = 11.sp, color = Color(0xFF8B5CF6), fontWeight = FontWeight.Medium, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
    }
}

@Composable
fun EmptyScheduleCard(selectedTab: Int) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            val (icon, message, subMessage) = when (selectedTab) {
                1 -> Triple(Icons.Default.CheckCircle, "No Overdue Follow-ups", "Great job staying on track!")
                2 -> Triple(Icons.Default.Today, "Nothing Scheduled Today", "Enjoy your free day or add new follow-ups")
                3 -> Triple(Icons.Default.EventAvailable, "No Upcoming Follow-ups", "Schedule follow-ups from lead details")
                4 -> Triple(Icons.Default.History, "No Completed Follow-ups", "Complete follow-ups to see them here")
                else -> Triple(Icons.Default.Schedule, "No Follow-ups", "Add follow-ups from lead details")
            }
            
            Box(
                modifier = Modifier.size(64.dp).background(Color(0xFF8B5CF6).copy(alpha = 0.1f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = Color(0xFF8B5CF6), modifier = Modifier.size(32.dp))
            }
            Text(message, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1a1a2e))
            Text(subMessage, fontSize = 12.sp, color = Color(0xFF94A3B8), textAlign = TextAlign.Center)
        }
    }
}
