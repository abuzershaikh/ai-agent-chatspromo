package com.message.bulksend.autorespond.ai.ui.templates

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.message.bulksend.autorespond.ai.data.model.AppointmentEntity
import com.message.bulksend.autorespond.ai.data.model.DoctorEntity
import com.message.bulksend.autorespond.ai.data.repo.ClinicRepository
import com.message.bulksend.autorespond.ai.settings.AIAgentSettingsManager
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ═══════════════════════════════════════════════════
//           APPOINTMENT DASHBOARD SCREEN
//       Separate file — Doctor-wise filtering
// ═══════════════════════════════════════════════════

private val BgLight = Color(0xFFF5F7FA)
private val CardWhite = Color(0xFFFFFFFF)
private val CardBorder = Color(0xFFE2E8F0)
private val TextDark = Color(0xFF1E293B)
private val TextSecondary = Color(0xFF64748B)
private val TextMuted = Color(0xFF94A3B8)
private val AccentTeal = Color(0xFF0D9488)
private val AccentTealLight = Color(0xFFF0FDFA)
private val AccentGreen = Color(0xFF16A34A)
private val AccentGreenLight = Color(0xFFF0FDF4)

private fun formatTime12h(time24: String): String {
    val parts = time24.split(":")
    val h = parts[0].toIntOrNull() ?: 0
    val m = parts.getOrNull(1) ?: "00"
    val amPm = if (h < 12) "AM" else "PM"
    val h12 = when {
        h == 0 -> 12
        h > 12 -> h - 12
        else -> h
    }
    return "$h12:$m $amPm"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(repository: ClinicRepository, onBack: () -> Unit) {
    val context = LocalContext.current
    val settingsManager = remember { AIAgentSettingsManager(context) }
    
    val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
    val displayFormat = java.text.SimpleDateFormat("dd MMM yyyy, EEEE", java.util.Locale.getDefault())
    val todayFormat = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
    val todayStr = todayFormat.format(java.util.Date())
    
    // Always show TODAY only
    val selectedDateStr = todayStr
    val selectedDate = java.util.Date()
    val selectedDisplay = displayFormat.format(selectedDate)
    
    // Get current time for past appointment detection
    val now = java.util.Calendar.getInstance()
    val currentHour = now.get(java.util.Calendar.HOUR_OF_DAY)
    val currentMinute = now.get(java.util.Calendar.MINUTE)
    val currentTimeMinutes = currentHour * 60 + currentMinute

    // ─── Doctor list ───
    val doctorsList = remember { mutableStateListOf<DoctorEntity>() }
    var selectedDoctor by remember { mutableStateOf<DoctorEntity?>(null) }
    var dropdownExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val docs = repository.getAllDoctorsList()
        doctorsList.clear()
        doctorsList.addAll(docs)
    }

    // ─── Appointments (reactive, changes when doctor changes) ───
    val allAppointments by repository.getConfirmedAppointmentsFlow(selectedDateStr)
        .collectAsState(initial = emptyList())

    val doctorAppointments = if (selectedDoctor != null) {
        allAppointments.filter { it.doctorId == selectedDoctor!!.id }
    } else {
        allAppointments
    }
    
    // Separate past and upcoming appointments
    val (pastAppointments, upcomingAppointments) = remember(doctorAppointments, currentTimeMinutes) {
        doctorAppointments.partition { appt ->
            val timeParts = appt.time.split(":")
            val apptHour = timeParts[0].toIntOrNull() ?: 0
            val apptMinute = timeParts.getOrNull(1)?.toIntOrNull() ?: 0
            val apptTimeMinutes = apptHour * 60 + apptMinute
            apptTimeMinutes < currentTimeMinutes
        }
    }

    // Doctor name lookup
    val doctorNames = remember(doctorsList.toList()) {
        doctorsList.associate { it.id to it.name }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Today's Appointments", fontWeight = FontWeight.Bold, color = TextDark) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back", tint = AccentTeal) }
                },
                actions = {
                    Surface(shape = RoundedCornerShape(20.dp), color = AccentTealLight) {
                        Text(
                            "${doctorAppointments.size} total",
                            color = AccentTeal, fontWeight = FontWeight.SemiBold, fontSize = 13.sp,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                    Spacer(Modifier.width(12.dp))
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
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(vertical = 20.dp)
        ) {
            // ─── Date display ───
            item {
                Text("Today  •  $selectedDisplay", fontSize = 14.sp, color = TextSecondary,
                    modifier = Modifier.padding(start = 4.dp, bottom = 4.dp))
            }

            // ─── Doctor Slots Summary ───
            item {
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = AccentTealLight),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.MedicalServices, "Doctors", tint = AccentTeal, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Doctor Availability", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = AccentTeal)
                        }
                        Spacer(Modifier.height(12.dp))
                        
                        doctorsList.forEach { doctor ->
                            val allSlots = remember(doctor) { 
                                repository.generateAllSlots(doctor)
                            }
                            val availableSlots = remember(selectedDateStr, doctor.id) {
                                kotlinx.coroutines.runBlocking {
                                    repository.getAvailableSlots(doctor.id, selectedDateStr)
                                }
                            }
                            val bookedCount = allSlots.size - availableSlots.size
                            val totalCount = allSlots.size
                            val availableCount = availableSlots.size
                            val percentage = if (totalCount > 0) (bookedCount * 100f / totalCount).toInt() else 0
                            
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text("Dr. ${doctor.name}", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextDark)
                                    Text(
                                        "$bookedCount booked • $availableCount free • $totalCount total",
                                        fontSize = 12.sp, color = TextSecondary
                                    )
                                }
                                
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = when {
                                        percentage >= 80 -> Color(0xFFDC2626)
                                        percentage >= 50 -> Color(0xFFEA580C)
                                        else -> AccentGreen
                                    }.copy(alpha = 0.15f)
                                ) {
                                    Text(
                                        "$percentage%",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = when {
                                            percentage >= 80 -> Color(0xFFDC2626)
                                            percentage >= 50 -> Color(0xFFEA580C)
                                            else -> AccentGreen
                                        },
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    )
                                }
                            }
                            
                            if (doctor != doctorsList.last()) {
                                HorizontalDivider(
                                    color = AccentTeal.copy(alpha = 0.2f),
                                    modifier = Modifier.padding(vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
            }
            
            // ─── Slot Grid View (NEW) ───
            if (selectedDoctor != null) {
                item {
                    Text("Available Slots", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, 
                        color = TextMuted, modifier = Modifier.padding(start = 4.dp, top = 8.dp))
                }
                
                item {
                    val doctor = selectedDoctor!!
                    val allSlots = remember(doctor) { 
                        repository.generateAllSlots(doctor)
                    }
                    val availableSlots = remember(selectedDateStr, doctor.id) {
                        kotlinx.coroutines.runBlocking {
                            repository.getAvailableSlots(doctor.id, selectedDateStr)
                        }
                    }
                    val bookedAppointments = remember(selectedDateStr, doctor.id) {
                        allAppointments.filter { it.doctorId == doctor.id }
                    }
                    
                    var showBookingDialog by remember { mutableStateOf(false) }
                    var selectedSlot by remember { mutableStateOf<String?>(null) }
                    
                    // Booking Dialog
                    if (showBookingDialog && selectedSlot != null) {
                        QuickBookingDialog(
                            time = selectedSlot!!,
                            doctorName = doctor.name,
                            date = selectedDateStr,
                            onDismiss = { showBookingDialog = false },
                            onBook = { name, phone ->
                                CoroutineScope(Dispatchers.IO).launch {
                                    try {
                                        // Create appointment entity
                                        val appointment = AppointmentEntity(
                                            doctorId = doctor.id,
                                            date = selectedDateStr,
                                            time = selectedSlot!!,
                                            patientName = name,
                                            patientPhone = phone.ifBlank { null },
                                            status = "CONFIRMED"
                                        )
                                        repository.addBooking(appointment)
                                        
                                        withContext(Dispatchers.Main) {
                                            showBookingDialog = false
                                        }
                                    } catch (e: Exception) {
                                        android.util.Log.e("Dashboard", "Booking failed: ${e.message}")
                                    }
                                }
                            }
                        )
                    }
                    
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = CardWhite),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            // Grid of slots
                            val chunkedSlots = allSlots.chunked(3)
                            chunkedSlots.forEach { rowSlots ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    rowSlots.forEach { slot ->
                                        val isAvailable = availableSlots.contains(slot)
                                        val isPast = remember(slot, currentTimeMinutes) {
                                            val timeParts = slot.split(":")
                                            val slotHour = timeParts[0].toIntOrNull() ?: 0
                                            val slotMinute = timeParts.getOrNull(1)?.toIntOrNull() ?: 0
                                            val slotTimeMinutes = slotHour * 60 + slotMinute
                                            slotTimeMinutes < currentTimeMinutes
                                        }
                                        val appointment = bookedAppointments.find { it.time == slot }
                                        
                                        SlotCard(
                                            time = slot,
                                            isAvailable = isAvailable,
                                            isPast = isPast,
                                            patientName = appointment?.patientName,
                                            modifier = Modifier.weight(1f),
                                            onClick = {
                                                if (isAvailable && !isPast) {
                                                    selectedSlot = slot
                                                    showBookingDialog = true
                                                }
                                            }
                                        )
                                    }
                                    // Fill remaining space if row has less than 3 items
                                    repeat(3 - rowSlots.size) {
                                        Spacer(Modifier.weight(1f))
                                    }
                                }
                                Spacer(Modifier.height(8.dp))
                            }
                        }
                    }
                }
            }

            // ─── Doctor Dropdown ───
            item {
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = CardWhite),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Filter by Doctor", fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                            color = TextMuted, modifier = Modifier.padding(bottom = 8.dp))

                        ExposedDropdownMenuBox(
                            expanded = dropdownExpanded,
                            onExpandedChange = { dropdownExpanded = it }
                        ) {
                            OutlinedTextField(
                                value = selectedDoctor?.let { "Dr. ${it.name}" } ?: "All Doctors",
                                onValueChange = {},
                                readOnly = true,
                                trailingIcon = {
                                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = dropdownExpanded)
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .menuAnchor(),
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = AccentTeal,
                                    unfocusedBorderColor = CardBorder,
                                    focusedTextColor = TextDark,
                                    unfocusedTextColor = TextDark
                                )
                            )

                            ExposedDropdownMenu(
                                expanded = dropdownExpanded,
                                onDismissRequest = { dropdownExpanded = false }
                            ) {
                                // "All Doctors" option
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            "All Doctors",
                                            fontWeight = if (selectedDoctor == null) FontWeight.Bold else FontWeight.Normal,
                                            color = if (selectedDoctor == null) AccentTeal else TextDark
                                        )
                                    },
                                    onClick = {
                                        selectedDoctor = null
                                        dropdownExpanded = false
                                    },
                                    leadingIcon = {
                                        Icon(Icons.Default.People, "All", tint = AccentTeal, modifier = Modifier.size(20.dp))
                                    }
                                )
                                HorizontalDivider(color = CardBorder)

                                // Each doctor
                                doctorsList.forEach { doctor ->
                                    val isSelected = selectedDoctor?.id == doctor.id
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                "Dr. ${doctor.name}",
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                color = if (isSelected) AccentTeal else TextDark
                                            )
                                        },
                                        onClick = {
                                            selectedDoctor = doctor
                                            dropdownExpanded = false
                                        },
                                        leadingIcon = {
                                            Icon(
                                                Icons.Default.Person,
                                                doctor.name,
                                                tint = if (isSelected) AccentTeal else TextSecondary,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        },
                                        trailingIcon = {
                                            if (isSelected) {
                                                Icon(Icons.Default.Check, "Selected", tint = AccentTeal, modifier = Modifier.size(18.dp))
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ─── Appointment List ───
            if (doctorAppointments.isEmpty()) {
                item {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = CardWhite),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                        modifier = Modifier.fillMaxWidth().height(180.dp)
                    ) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.EventAvailable, "No appointments",
                                    tint = TextMuted, modifier = Modifier.size(48.dp))
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    if (selectedDoctor != null) "No appointments for Dr. ${selectedDoctor!!.name}"
                                    else "No appointments today",
                                    color = TextMuted, fontSize = 15.sp
                                )
                                Text("Bookings will appear here", color = TextMuted, fontSize = 13.sp)
                            }
                        }
                    }
                }
            } else {
                // ─── Upcoming Appointments ───
                if (upcomingAppointments.isNotEmpty()) {
                    item {
                        Text("Upcoming", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, 
                            color = AccentGreen, modifier = Modifier.padding(start = 4.dp, top = 4.dp))
                    }
                    
                    items(upcomingAppointments.size) { index ->
                        val appt = upcomingAppointments[index]
                        val doctorName = doctorNames[appt.doctorId] ?: "Doctor"
                        val time12 = formatTime12h(appt.time)

                        Card(
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = CardWhite),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp).fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Time badge
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = AccentTealLight,
                                    modifier = Modifier.width(70.dp)
                                ) {
                                    Text(
                                        time12, color = AccentTeal, fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp, textAlign = TextAlign.Center,
                                        modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp)
                                    )
                                }
                                Spacer(Modifier.width(14.dp))

                                Column(Modifier.weight(1f)) {
                                    Text(appt.patientName, fontSize = 15.sp,
                                        fontWeight = FontWeight.SemiBold, color = TextDark)
                                    Text("Dr. $doctorName", fontSize = 13.sp, color = TextSecondary)
                                    if (!appt.patientPhone.isNullOrBlank()) {
                                        Text(appt.patientPhone, fontSize = 12.sp, color = TextMuted)
                                    }
                                }

                                // Status badge
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = AccentGreenLight
                                ) {
                                    Text(
                                        "✓", color = AccentGreen, fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                }
                
                // ─── Past Appointments (Completed) ───
                if (pastAppointments.isNotEmpty()) {
                    item {
                        Text("Completed", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, 
                            color = TextMuted, modifier = Modifier.padding(start = 4.dp, top = 12.dp))
                    }
                    
                    items(pastAppointments.size) { index ->
                        val appt = pastAppointments[index]
                        val doctorName = doctorNames[appt.doctorId] ?: "Doctor"
                        val time12 = formatTime12h(appt.time)

                        Card(
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
                            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp).fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Time badge (greyed out)
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = Color(0xFFE2E8F0),
                                    modifier = Modifier.width(70.dp)
                                ) {
                                    Text(
                                        time12, color = TextMuted, fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp, textAlign = TextAlign.Center,
                                        modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp)
                                    )
                                }
                                Spacer(Modifier.width(14.dp))

                                Column(Modifier.weight(1f)) {
                                    Text(appt.patientName, fontSize = 15.sp,
                                        fontWeight = FontWeight.SemiBold, color = TextMuted)
                                    Text("Dr. $doctorName", fontSize = 13.sp, color = TextMuted)
                                    if (!appt.patientPhone.isNullOrBlank()) {
                                        Text(appt.patientPhone, fontSize = 12.sp, color = TextMuted)
                                    }
                                }

                                // Completed badge
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color(0xFFE2E8F0)
                                ) {
                                    Text(
                                        "Done", color = TextMuted, fontWeight = FontWeight.SemiBold,
                                        fontSize = 11.sp,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}


// ═══════════════════════════════════════════════════
//                  SLOT CARD COMPONENT
// ═══════════════════════════════════════════════════

@Composable
fun SlotCard(
    time: String,
    isAvailable: Boolean,
    isPast: Boolean,
    patientName: String?,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val time12 = formatTime12h(time)
    
    val (bgColor, textColor, borderColor) = when {
        isPast -> Triple(Color(0xFFF8FAFC), TextMuted, Color(0xFFE2E8F0))
        !isAvailable -> Triple(Color(0xFFFEE2E2), Color(0xFFDC2626), Color(0xFFFCA5A5))
        else -> Triple(AccentGreenLight, AccentGreen, AccentGreen.copy(alpha = 0.3f))
    }
    
    Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = bgColor),
        border = BorderStroke(1.dp, borderColor),
        modifier = modifier
            .aspectRatio(1f)
            .clickable(enabled = isAvailable && !isPast) { onClick() }
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    time12,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = textColor,
                    textAlign = TextAlign.Center
                )
                
                if (!isAvailable && patientName != null) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        patientName.take(8),
                        fontSize = 9.sp,
                        color = textColor.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center,
                        maxLines = 1
                    )
                } else if (isPast) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "Past",
                        fontSize = 9.sp,
                        color = textColor,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════
//              QUICK BOOKING DIALOG
// ═══════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickBookingDialog(
    time: String,
    doctorName: String,
    date: String,
    onDismiss: () -> Unit,
    onBook: (name: String, phone: String) -> Unit
) {
    var patientName by remember { mutableStateOf("") }
    var patientPhone by remember { mutableStateOf("") }
    
    val time12 = formatTime12h(time)
    val dateFormat = java.text.SimpleDateFormat("dd MMM yyyy", java.util.Locale.getDefault())
    val displayDate = try {
        val parsed = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).parse(date)
        dateFormat.format(parsed!!)
    } catch (e: Exception) {
        date
    }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CardWhite,
        title = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.EventAvailable, "Book", tint = AccentGreen, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Quick Booking", color = TextDark, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
                Spacer(Modifier.height(8.dp))
                Card(
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(containerColor = AccentTealLight)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Person, "Doctor", tint = AccentTeal, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Dr. $doctorName", fontSize = 13.sp, color = AccentTeal, fontWeight = FontWeight.SemiBold)
                        }
                        Spacer(Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CalendarToday, "Date", tint = AccentTeal, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(displayDate, fontSize = 12.sp, color = AccentTeal)
                            Spacer(Modifier.width(12.dp))
                            Icon(Icons.Default.AccessTime, "Time", tint = AccentTeal, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(time12, fontSize = 12.sp, color = AccentTeal, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = patientName,
                    onValueChange = { patientName = it },
                    label = { Text("Patient Name *") },
                    placeholder = { Text("Enter patient name") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    leadingIcon = {
                        Icon(Icons.Default.Person, "Name", tint = AccentGreen)
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AccentGreen,
                        unfocusedBorderColor = CardBorder,
                        focusedTextColor = TextDark,
                        unfocusedTextColor = TextDark
                    )
                )
                
                OutlinedTextField(
                    value = patientPhone,
                    onValueChange = { patientPhone = it },
                    label = { Text("Phone Number (Optional)") },
                    placeholder = { Text("Enter phone number") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    leadingIcon = {
                        Icon(Icons.Default.Phone, "Phone", tint = AccentTeal)
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AccentTeal,
                        unfocusedBorderColor = CardBorder,
                        focusedTextColor = TextDark,
                        unfocusedTextColor = TextDark
                    )
                )
                
                Card(
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF7ED))
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(Icons.Default.Info, "Info", tint = Color(0xFFEA580C), modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Quick booking for offline appointments. Phone number is optional.",
                            fontSize = 11.sp,
                            color = Color(0xFFEA580C),
                            lineHeight = 14.sp
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (patientName.isNotBlank()) {
                        onBook(patientName.trim(), patientPhone.trim())
                    }
                },
                enabled = patientName.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = AccentGreen),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(Icons.Default.Check, "Book", modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Book Appointment")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextMuted)
            }
        }
    )
}
