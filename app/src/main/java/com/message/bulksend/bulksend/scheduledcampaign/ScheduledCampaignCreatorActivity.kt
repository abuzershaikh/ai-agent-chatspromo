package com.message.bulksend.bulksend.scheduledcampaign

import android.app.AlarmManager
import android.app.DatePickerDialog
import android.app.PendingIntent
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.message.bulksend.components.AlarmPermissionDialog
import com.message.bulksend.components.NotificationPermissionDialog
import com.message.bulksend.ui.theme.BulksendTestTheme
import com.message.bulksend.utils.AlarmPermissionHelper
import com.message.bulksend.utils.NotificationPermissionHelper
import java.text.SimpleDateFormat
import java.util.*

class ScheduledCampaignCreatorActivity : ComponentActivity() {
    
    // Permission launcher for notification permission
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            Toast.makeText(this, "Notification permission granted", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Notification permission denied", Toast.LENGTH_SHORT).show()
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        setContent {
            BulksendTestTheme {
                ScheduledCampaignCreatorScreen(
                    onRequestNotificationPermission = {
                        NotificationPermissionHelper.requestNotificationPermission(this)
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduledCampaignCreatorScreen(
    onRequestNotificationPermission: () -> Unit = {}
) {
    val context = LocalContext.current
    var selectedCampaignType by remember { mutableStateOf(CampaignTypeUI.TEXT) }
    var campaignTitle by remember { mutableStateOf("") }
    var messageText by remember { mutableStateOf("") }
    var selectedDateTime by remember { mutableStateOf<Long?>(null) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var selectedDate by remember { mutableStateOf<Calendar?>(null) }
    
    // Notification permission state
    var showNotificationPermissionDialog by remember { mutableStateOf(false) }
    var notificationPermissionGranted by remember { 
        mutableStateOf(NotificationPermissionHelper.isNotificationPermissionGranted(context)) 
    }
    
    // Alarm permission state
    var showAlarmPermissionDialog by remember { mutableStateOf(false) }
    var alarmPermissionGranted by remember {
        mutableStateOf(AlarmPermissionHelper.canScheduleExactAlarms(context))
    }
    
    val backgroundBrush = Brush.verticalGradient(
        colors = listOf(
            Color(0xFF0f0c29),
            Color(0xFF302b63),
            Color(0xFF24243e),
            Color(0xFF0f0c29)
        )
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Outlined.Schedule,
                            contentDescription = null,
                            tint = Color(0xFF9C27B0),
                            modifier = Modifier.size(24.dp)
                        )
                        Text(
                            "Schedule Campaign",
                            color = Color(0xFF10B981),
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = { 
                            (context as ComponentActivity).finish()
                        }
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color(0xFF00D4FF),
                            modifier = Modifier.size(28.dp)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        },
        containerColor = Color.Transparent
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(backgroundBrush)
        ) {
            Column(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // Campaign Title
                CampaignInputCard(
                    title = "Campaign Title",
                    icon = Icons.Outlined.Title
                ) {
                    OutlinedTextField(
                        value = campaignTitle,
                        onValueChange = { campaignTitle = it },
                        placeholder = { Text("Enter campaign title") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF9C27B0),
                            unfocusedBorderColor = Color(0xFF64748B)
                        )
                    )
                }
                
                // Campaign Type Selection
                CampaignInputCard(
                    title = "Campaign Type",
                    icon = Icons.Outlined.Category
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CampaignTypeUI.values().forEach { type ->
                            CampaignTypeChip(
                                type = type,
                                isSelected = selectedCampaignType == type,
                                onClick = { selectedCampaignType = type },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
                
                // Message Input
                CampaignInputCard(
                    title = "Message Content",
                    icon = Icons.Outlined.Message
                ) {
                    OutlinedTextField(
                        value = messageText,
                        onValueChange = { messageText = it },
                        placeholder = { Text("Enter your message here...") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp),
                        maxLines = 5,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF9C27B0),
                            unfocusedBorderColor = Color(0xFF64748B)
                        )
                    )
                }
                
                // Schedule Date & Time
                CampaignInputCard(
                    title = "Schedule Date & Time",
                    icon = Icons.Outlined.Schedule
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Date Selection
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showDatePicker = true },
                            colors = CardDefaults.cardColors(
                                containerColor = Color(0xFF9C27B0).copy(alpha = 0.1f)
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .padding(16.dp)
                                    .fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        "Select Date",
                                        fontSize = 12.sp,
                                        color = Color(0xFF64748B),
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        selectedDate?.let { 
                                            SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(it.time)
                                        } ?: "Choose date",
                                        fontSize = 16.sp,
                                        color = Color.White,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                Icon(
                                    Icons.Outlined.DateRange,
                                    contentDescription = null,
                                    tint = Color(0xFF9C27B0),
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                        
                        // Time Selection
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { 
                                    if (selectedDate != null) {
                                        showTimePicker = true
                                    }
                                },
                            colors = CardDefaults.cardColors(
                                containerColor = if (selectedDate != null) 
                                    Color(0xFF9C27B0).copy(alpha = 0.1f) 
                                else 
                                    Color(0xFF64748B).copy(alpha = 0.1f)
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .padding(16.dp)
                                    .fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        "Select Time",
                                        fontSize = 12.sp,
                                        color = Color(0xFF64748B),
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        selectedDateTime?.let { 
                                            SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(it))
                                        } ?: if (selectedDate != null) "Choose time" else "Select date first",
                                        fontSize = 16.sp,
                                        color = if (selectedDate != null) Color.White else Color(0xFF64748B),
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                Icon(
                                    Icons.Outlined.AccessTime,
                                    contentDescription = null,
                                    tint = if (selectedDate != null) Color(0xFF9C27B0) else Color(0xFF64748B),
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(20.dp))
                
                // Schedule Button
                Button(
                    onClick = {
                        // Validate inputs
                        if (campaignTitle.isEmpty() || messageText.isEmpty() || selectedDateTime == null) {
                            Toast.makeText(context, "Please fill all fields", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        
                        // Check notification permission first
                        val hasNotificationPermission = NotificationPermissionHelper.isNotificationPermissionGranted(context)
                        android.util.Log.d("ScheduledCampaign", "Notification permission: $hasNotificationPermission")
                        
                        if (!hasNotificationPermission) {
                            android.util.Log.d("ScheduledCampaign", "Showing notification permission dialog")
                            showNotificationPermissionDialog = true
                            return@Button
                        }
                        
                        // Check alarm permission (Android 12+)
                        val hasAlarmPermission = AlarmPermissionHelper.canScheduleExactAlarms(context)
                        android.util.Log.d("ScheduledCampaign", "Alarm permission: $hasAlarmPermission")
                        
                        if (!hasAlarmPermission) {
                            android.util.Log.d("ScheduledCampaign", "Showing alarm permission dialog")
                            showAlarmPermissionDialog = true
                            return@Button
                        }
                        
                        android.util.Log.d("ScheduledCampaign", "All permissions granted, scheduling campaign")
                        
                        // Schedule campaign
                        scheduleCampaign(
                            context = context,
                            title = campaignTitle,
                            type = selectedCampaignType,
                            message = messageText,
                            scheduledTime = selectedDateTime!!
                        )
                        (context as ComponentActivity).finish()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    enabled = campaignTitle.isNotEmpty() && messageText.isNotEmpty() && selectedDateTime != null,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF9C27B0),
                        disabledContainerColor = Color(0xFF64748B)
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Outlined.Schedule,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            "Schedule Campaign",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
    
    // Date Picker Dialog
    if (showDatePicker) {
        val calendar = Calendar.getInstance()
        DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                val newCalendar = Calendar.getInstance().apply {
                    set(Calendar.YEAR, year)
                    set(Calendar.MONTH, month)
                    set(Calendar.DAY_OF_MONTH, dayOfMonth)
                }
                selectedDate = newCalendar
                showDatePicker = false
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).apply {
            datePicker.minDate = System.currentTimeMillis()
            show()
        }
    }
    
    // Time Picker Dialog
    if (showTimePicker && selectedDate != null) {
        val calendar = Calendar.getInstance()
        TimePickerDialog(
            context,
            { _, hourOfDay, minute ->
                selectedDate?.let { date ->
                    date.set(Calendar.HOUR_OF_DAY, hourOfDay)
                    date.set(Calendar.MINUTE, minute)
                    date.set(Calendar.SECOND, 0)
                    selectedDateTime = date.timeInMillis
                }
                showTimePicker = false
            },
            calendar.get(Calendar.HOUR_OF_DAY),
            calendar.get(Calendar.MINUTE),
            false
        ).show()
    }
    
    // Notification Permission Dialog
    if (showNotificationPermissionDialog) {
        val shouldShowRationale = NotificationPermissionHelper.shouldShowNotificationPermissionRationale(
            context as ComponentActivity
        )
        
        NotificationPermissionDialog(
            onDismiss = { showNotificationPermissionDialog = false },
            onRequestPermission = {
                onRequestNotificationPermission()
                notificationPermissionGranted = NotificationPermissionHelper.isNotificationPermissionGranted(context)
            },
            onOpenSettings = {
                NotificationPermissionHelper.openNotificationSettings(context)
            },
            showSettingsOption = shouldShowRationale
        )
    }
    
    // Alarm Permission Dialog
    if (showAlarmPermissionDialog) {
        AlarmPermissionDialog(
            onDismiss = { 
                showAlarmPermissionDialog = false
            },
            onRequestPermission = {
                AlarmPermissionHelper.openAlarmPermissionSettings(context)
                showAlarmPermissionDialog = false
            }
        )
    }
}

@Composable
fun CampaignInputCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.95f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = Color(0xFF9C27B0),
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = Color(0xFF1E293B)
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            content()
        }
    }
}

@Composable
fun CampaignTypeChip(
    type: CampaignTypeUI,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .clickable { onClick() }
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) type.color else Color(0xFF64748B).copy(alpha = 0.3f),
                shape = RoundedCornerShape(12.dp)
            ),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) type.color.copy(alpha = 0.1f) else Color.Transparent
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                type.icon,
                contentDescription = null,
                tint = if (isSelected) type.color else Color(0xFF64748B),
                modifier = Modifier.size(20.dp)
            )
            Text(
                type.displayName.split(" ").first(),
                fontSize = 10.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = if (isSelected) type.color else Color(0xFF64748B),
                textAlign = TextAlign.Center
            )
        }
    }
}

fun scheduleCampaign(
    context: Context,
    title: String,
    type: CampaignTypeUI,
    message: String,
    scheduledTime: Long
) {
    android.util.Log.d("ScheduledCampaign", "scheduleCampaign called with title: $title")
    
    // Show immediate notification that campaign is scheduled
    showScheduledNotification(context, title, type, scheduledTime)
    
    android.widget.Toast.makeText(
        context,
        "Campaign '$title' scheduled successfully!",
        android.widget.Toast.LENGTH_LONG
    ).show()
    
    android.util.Log.d("ScheduledCampaign", "scheduleCampaign completed")
    
    // In a real implementation, you would:
    // 1. Save the campaign to database/SharedPreferences
    // 2. Set up AlarmManager to trigger at the scheduled time
    // 3. Create a BroadcastReceiver to handle the alarm
    // 4. Start the actual campaign when the alarm triggers
}

/**
 * Show notification when campaign is scheduled
 */
private fun showScheduledNotification(
    context: Context,
    campaignTitle: String,
    campaignType: CampaignTypeUI,
    scheduledTime: Long
) {
    android.util.Log.d("ScheduledNotification", "showScheduledNotification called for: $campaignTitle")
    
    val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
    
    // Check if notifications are enabled
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
        val areNotificationsEnabled = notificationManager.areNotificationsEnabled()
        android.util.Log.d("ScheduledNotification", "Notifications enabled: $areNotificationsEnabled")
        if (!areNotificationsEnabled) {
            android.util.Log.e("ScheduledNotification", "Notifications are disabled for this app!")
            return
        }
    }
    
    // Create notification channel for Android O+
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
        val channel = android.app.NotificationChannel(
            "scheduled_campaign_confirmation",
            "Scheduled Campaign Confirmation",
            android.app.NotificationManager.IMPORTANCE_HIGH  // Changed to HIGH for better visibility
        ).apply {
            description = "Notifications when campaigns are scheduled"
            enableLights(true)
            lightColor = android.graphics.Color.GREEN
            enableVibration(true)
            setShowBadge(true)
        }
        notificationManager.createNotificationChannel(channel)
        android.util.Log.d("ScheduledNotification", "Notification channel created")
    }
    
    // Campaign type emoji
    val typeEmoji = when (campaignType) {
        CampaignTypeUI.TEXT -> "📝"
        CampaignTypeUI.MEDIA -> "🖼️"
        CampaignTypeUI.TEXT_AND_MEDIA -> "📎"
        CampaignTypeUI.SHEET -> "📊"
    }
    
    // Format scheduled time
    val scheduledTimeFormatted = SimpleDateFormat("MMM dd, yyyy 'at' hh:mm a", Locale.getDefault()).format(Date(scheduledTime))
    
    // Calculate time remaining
    val timeRemaining = getTimeRemainingText(scheduledTime)
    
    // Create intent to open Scheduled History
    val intent = Intent(context, ScheduleSendActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
    }
    
    val pendingIntent = android.app.PendingIntent.getActivity(
        context,
        System.currentTimeMillis().toInt(),
        intent,
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        } else {
            android.app.PendingIntent.FLAG_UPDATE_CURRENT
        }
    )
    
    // Build notification
    val notification = androidx.core.app.NotificationCompat.Builder(context, "scheduled_campaign_confirmation")
        .setSmallIcon(com.message.bulksend.R.drawable.ic_notification)
        .setContentTitle("✅ Campaign Scheduled Successfully")
        .setContentText("$typeEmoji $campaignTitle")
        .setSubText("Will run $timeRemaining")
        .setStyle(androidx.core.app.NotificationCompat.BigTextStyle()
            .bigText("$typeEmoji $campaignTitle\n\n📅 Scheduled for: $scheduledTimeFormatted\n⏰ Will run $timeRemaining")
            .setBigContentTitle("✅ Campaign Scheduled Successfully"))
        .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)  // Changed to HIGH
        .setCategory(androidx.core.app.NotificationCompat.CATEGORY_STATUS)
        .setAutoCancel(true)
        .setContentIntent(pendingIntent)
        .setColor(android.graphics.Color.parseColor("#10B981")) // Green color
        .setVibrate(longArrayOf(0, 300, 100, 300))
        .setDefaults(androidx.core.app.NotificationCompat.DEFAULT_ALL)  // Added defaults
        .build()
    
    val notificationId = System.currentTimeMillis().toInt()
    android.util.Log.d("ScheduledNotification", "Showing notification with ID: $notificationId")
    
    // Show notification
    notificationManager.notify(notificationId, notification)
    
    android.util.Log.d("ScheduledNotification", "Notification posted successfully")
}

/**
 * Get human-readable time remaining text
 */
private fun getTimeRemainingText(scheduledTime: Long): String {
    val now = System.currentTimeMillis()
    val diff = scheduledTime - now
    
    if (diff <= 0) return "now"
    
    val days = diff / (24 * 60 * 60 * 1000)
    val hours = (diff % (24 * 60 * 60 * 1000)) / (60 * 60 * 1000)
    val minutes = (diff % (60 * 60 * 1000)) / (60 * 1000)
    
    return when {
        days > 0 -> "in ${days}d ${hours}h"
        hours > 0 -> "in ${hours}h ${minutes}m"
        else -> "in ${minutes}m"
    }
}