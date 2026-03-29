package com.message.bulksend.autorespond.statusscheduled

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.message.bulksend.autorespond.database.MessageDatabase
import com.message.bulksend.autorespond.statusscheduled.database.StatusBatchRepository
import com.message.bulksend.autorespond.statusscheduled.models.MediaItem
import com.message.bulksend.autorespond.statusscheduled.models.MediaType
import com.message.bulksend.autorespond.statusscheduled.models.BatchStatus
import com.message.bulksend.autorespond.statusscheduled.models.ScheduleType
import com.message.bulksend.autorespond.statusscheduled.models.StatusBatch
import com.message.bulksend.autorespond.statusscheduled.screens.MediaPickerScreen
import com.message.bulksend.autorespond.statusscheduled.screens.ScheduleSettingsScreen
import com.message.bulksend.autorespond.statusscheduled.screens.StatusBatchListScreen
import com.message.bulksend.autorespond.statusscheduled.screens.StatusBatchPreviewScreen
import com.message.bulksend.components.AlarmPermissionDialog
import com.message.bulksend.plan.PrepackActivity
import com.message.bulksend.ui.theme.BulksendTestTheme
import com.message.bulksend.utils.AlarmPermissionHelper
import com.message.bulksend.utils.AccessibilityPermissionDialog
import com.message.bulksend.utils.SubscriptionUtils
import com.message.bulksend.utils.isAccessibilityServiceEnabled
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class StatusSchedulerActivity : ComponentActivity() {

    private lateinit var batchManager: StatusBatchManager
    private lateinit var repository: StatusBatchRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val database = MessageDatabase.getDatabase(applicationContext)
        repository = StatusBatchRepository(database.statusBatchDao())
        batchManager = StatusBatchManager(applicationContext, repository)

        setContent {
            BulksendTestTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    StatusSchedulerApp(
                        batchManager = batchManager,
                        repository = repository,
                        onFinish = { finish() }
                    )
                }
            }
        }
    }
}

enum class SchedulerScreen {
    BATCH_LIST,
    MEDIA_PICKER,
    SCHEDULE_SETTINGS,
    BATCH_PREVIEW
}

private const val STATUS_SCHEDULER_TRIAL_PREFS = "status_scheduler_trial_prefs"
private const val KEY_STATUS_SCHEDULER_TRIAL_STARTED_AT = "status_scheduler_trial_started_at"
private const val STATUS_SCHEDULER_TRIAL_DURATION_MS = 2 * 60 * 60 * 1000L

private fun isStatusSchedulerPremiumActive(context: Context): Boolean {
    val subscriptionInfo = SubscriptionUtils.getLocalSubscriptionInfo(context)
    val type = subscriptionInfo["type"] as? String ?: "free"
    val isExpired = subscriptionInfo["isExpired"] as? Boolean ?: false
    return type == "premium" && !isExpired
}

private fun getStatusSchedulerTrialStartedAt(context: Context): Long {
    return context
        .getSharedPreferences(STATUS_SCHEDULER_TRIAL_PREFS, Context.MODE_PRIVATE)
        .getLong(KEY_STATUS_SCHEDULER_TRIAL_STARTED_AT, 0L)
}

private fun hasStatusSchedulerTrialStarted(context: Context): Boolean {
    return getStatusSchedulerTrialStartedAt(context) > 0L
}

private fun getStatusSchedulerTrialRemainingMillis(context: Context): Long {
    val startedAt = getStatusSchedulerTrialStartedAt(context)
    if (startedAt <= 0L) return 0L
    return (STATUS_SCHEDULER_TRIAL_DURATION_MS - (System.currentTimeMillis() - startedAt))
        .coerceAtLeast(0L)
}

private fun hasActiveStatusSchedulerTrial(context: Context): Boolean {
    return getStatusSchedulerTrialRemainingMillis(context) > 0L
}

private fun startStatusSchedulerTrial(context: Context) {
    context
        .getSharedPreferences(STATUS_SCHEDULER_TRIAL_PREFS, Context.MODE_PRIVATE)
        .edit()
        .putLong(KEY_STATUS_SCHEDULER_TRIAL_STARTED_AT, System.currentTimeMillis())
        .apply()
}

@Composable
fun StatusSchedulerApp(
    batchManager: StatusBatchManager,
    repository: StatusBatchRepository,
    onFinish: () -> Unit
) {
    var currentScreen by remember { mutableStateOf(SchedulerScreen.BATCH_LIST) }
    var batches by remember { mutableStateOf<List<StatusBatch>>(emptyList()) }
    var canAddMore by remember { mutableStateOf(true) }
    var showTrialDialog by remember { mutableStateOf(false) }
    var showUpgradeDialog by remember { mutableStateOf(false) }
    var showAlarmPermissionDialog by remember { mutableStateOf(false) }
    var showAccessibilityDialog by remember { mutableStateOf(false) }

    var selectedMedia by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    var scheduleType by remember { mutableStateOf(ScheduleType.MANUAL) }
    var startDate by remember { mutableStateOf<Long?>(null) }
    var time by remember { mutableStateOf<String?>(null) }
    var amPm by remember { mutableStateOf<String?>(null) }
    var repeatDaily by remember { mutableStateOf(false) }
    var reminderMinutes by remember { mutableStateOf<Int?>(null) }
    var previewBatchId by remember { mutableStateOf<Long?>(null) }
    var scheduleTargetBatchId by remember { mutableStateOf<Long?>(null) }
    var scheduleBackTarget by remember { mutableStateOf(SchedulerScreen.BATCH_LIST) }

    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current
    val currentPreviewBatch = batches.firstOrNull { it.id == previewBatchId }
    val scheduleTargetBatch = batches.firstOrNull { it.id == scheduleTargetBatchId }

    fun canUseStatusScheduler(): Boolean {
        return isStatusSchedulerPremiumActive(context) || hasActiveStatusSchedulerTrial(context)
    }

    fun hasAccessibilityPermission(): Boolean {
        return isAccessibilityServiceEnabled(context)
    }

    fun showAccessRequiredToast() {
        Toast.makeText(
            context,
            "Enable Accessibility permission to run status posting.",
            Toast.LENGTH_LONG
        ).show()
    }

    fun resetNewBatchComposer() {
        selectedMedia = emptyList()
        scheduleType = ScheduleType.MANUAL
        startDate = null
        time = null
        amPm = null
        repeatDaily = false
        reminderMinutes = null
        scheduleTargetBatchId = null
        scheduleBackTarget = SchedulerScreen.BATCH_LIST
    }

    fun openCreateBatchFlow() {
        resetNewBatchComposer()
        currentScreen = SchedulerScreen.MEDIA_PICKER
    }

    fun openScheduleEditor(batch: StatusBatch, returnScreen: SchedulerScreen) {
        scheduleTargetBatchId = batch.id
        scheduleType = batch.scheduleType
        startDate = batch.startDate
        time = batch.time
        amPm = batch.amPm
        repeatDaily = false
        reminderMinutes = batch.reminderMinutes
        scheduleBackTarget = returnScreen
        previewBatchId = batch.id
        currentScreen = SchedulerScreen.SCHEDULE_SETTINGS
    }

    fun postBatchNow(batch: StatusBatch) {
        scope.launch {
            if (!hasAccessibilityPermission()) {
                showAccessibilityDialog = true
                showAccessRequiredToast()
                return@launch
            }

            previewBatchId = batch.id
            StatusBatchExecutionService.startForBatch(
                context = context,
                batchId = batch.id,
                source = StatusBatchExecutionService.SOURCE_POST_NOW
            )
            Toast.makeText(context, "Posting batch now...", Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(Unit) {
        batchManager.restoreScheduledBatches()
        repository.getAllBatches().collectLatest { loadedBatches ->
            batches = loadedBatches
            canAddMore = repository.canAddMoreBatches()

            if (previewBatchId != null && loadedBatches.none { it.id == previewBatchId }) {
                previewBatchId = null
                if (currentScreen == SchedulerScreen.BATCH_PREVIEW) {
                    currentScreen = SchedulerScreen.BATCH_LIST
                }
            }

            if (scheduleTargetBatchId != null && loadedBatches.none { it.id == scheduleTargetBatchId }) {
                scheduleTargetBatchId = null
                if (currentScreen == SchedulerScreen.SCHEDULE_SETTINGS &&
                    scheduleBackTarget != SchedulerScreen.MEDIA_PICKER
                ) {
                    currentScreen = SchedulerScreen.BATCH_LIST
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        if (!isStatusSchedulerPremiumActive(context)) {
            when {
                hasActiveStatusSchedulerTrial(context) -> Unit
                hasStatusSchedulerTrialStarted(context) -> showUpgradeDialog = true
                else -> showTrialDialog = true
            }
        }
    }

    when (currentScreen) {
        SchedulerScreen.BATCH_LIST -> {
            StatusBatchListScreen(
                batches = batches,
                onBatchClick = { batch ->
                    previewBatchId = batch.id
                    currentScreen = SchedulerScreen.BATCH_PREVIEW
                },
                onDeleteBatch = { batch ->
                    scope.launch {
                        batchManager.deleteBatch(batch)
                        if (previewBatchId == batch.id) {
                            previewBatchId = null
                        }
                        Toast.makeText(context, "Batch deleted", Toast.LENGTH_SHORT).show()
                    }
                },
                onPostNow = ::postBatchNow,
                onScheduleBatch = { batch ->
                    openScheduleEditor(batch, SchedulerScreen.BATCH_LIST)
                },
                onCancelSchedule = { batch ->
                    scope.launch {
                        val success = batchManager.cancelBatchSchedule(batch.id)
                        if (success) {
                            Toast.makeText(context, "Schedule cancelled", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(
                                context,
                                "Failed to cancel schedule",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                },
                onAddBatch = {
                    if (!canUseStatusScheduler()) {
                        if (hasStatusSchedulerTrialStarted(context)) {
                            showUpgradeDialog = true
                        } else {
                            showTrialDialog = true
                        }
                    } else if (canAddMore) {
                        openCreateBatchFlow()
                    } else {
                        Toast.makeText(
                            context,
                            "Maximum 30 batches allowed",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                },
                canAddMore = canAddMore
            )
        }

        SchedulerScreen.MEDIA_PICKER -> {
            MediaPickerScreen(
                selectedMedia = selectedMedia,
                onMediaAdded = { uri, type ->
                    scope.launch {
                        val mediaItem = batchManager.importMedia(uri, type)
                        if (mediaItem != null) {
                            selectedMedia = selectedMedia + mediaItem
                            Toast.makeText(context, "Media added", Toast.LENGTH_SHORT).show()
                        } else {
                            if (type == MediaType.VIDEO) {
                                Toast.makeText(
                                    context,
                                    "Video size must be less than 16MB",
                                    Toast.LENGTH_LONG
                                ).show()
                            } else {
                                Toast.makeText(
                                    context,
                                    "Failed to add media",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    }
                },
                onMediaRemoved = { media ->
                    selectedMedia = selectedMedia.filter { it != media }
                },
                onDelayChanged = { media, delay ->
                    selectedMedia = selectedMedia.map {
                        if (it == media) it.copy(delayMinutes = delay) else it
                    }
                },
                onBack = {
                    currentScreen = SchedulerScreen.BATCH_LIST
                },
                onSaveDraft = {
                    if (selectedMedia.isEmpty()) {
                        Toast.makeText(
                            context,
                            "Please add at least one media item",
                            Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        scope.launch {
                            if (!canUseStatusScheduler()) {
                                showUpgradeDialog = true
                                return@launch
                            }

                            val batchId = batchManager.createBatch(
                                mediaList = selectedMedia,
                                scheduleType = ScheduleType.MANUAL
                            )

                            if (batchId != null) {
                                previewBatchId = batchId
                                resetNewBatchComposer()
                                currentScreen = SchedulerScreen.BATCH_LIST
                                Toast.makeText(
                                    context,
                                    "Draft saved. You can schedule it later.",
                                    Toast.LENGTH_SHORT
                                ).show()
                            } else {
                                Toast.makeText(
                                    context,
                                    "Failed to save draft batch",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    }
                },
                onScheduleNext = {
                    if (selectedMedia.isNotEmpty()) {
                        scheduleBackTarget = SchedulerScreen.MEDIA_PICKER
                        currentScreen = SchedulerScreen.SCHEDULE_SETTINGS
                    } else {
                        Toast.makeText(
                            context,
                            "Please add at least one media item",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            )
        }

        SchedulerScreen.SCHEDULE_SETTINGS -> {
            ScheduleSettingsScreen(
                batchTitle = scheduleTargetBatch?.let { "Batch #${it.id}" } ?: "New Batch",
                mediaCount = scheduleTargetBatch?.mediaList?.size ?: selectedMedia.size,
                scheduleType = scheduleType,
                onScheduleTypeChanged = {
                    scheduleType = it
                    repeatDaily = false
                },
                startDate = startDate,
                onStartDateChanged = { startDate = it },
                time = time,
                onTimeChanged = { time = it },
                amPm = amPm,
                onAmPmChanged = { amPm = it },
                repeatDaily = repeatDaily,
                onRepeatDailyChanged = { repeatDaily = it },
                reminderMinutes = reminderMinutes,
                onReminderMinutesChanged = { reminderMinutes = it },
                onSave = {
                    scope.launch {
                        if (!canUseStatusScheduler()) {
                            showUpgradeDialog = true
                            return@launch
                        }

                        if (!hasAccessibilityPermission()) {
                            showAccessibilityDialog = true
                            showAccessRequiredToast()
                            return@launch
                        }

                        if (!batchManager.canScheduleExactAlarms()) {
                            showAlarmPermissionDialog = true
                            Toast.makeText(
                                context,
                                "Enable Exact Alarm permission to schedule batches.",
                                Toast.LENGTH_LONG
                            ).show()
                            return@launch
                        }

                        val normalizedRepeatDaily = false
                        val batchId = if (scheduleTargetBatch != null) {
                            val updatedBatch = scheduleTargetBatch.copy(
                                scheduleType = scheduleType,
                                startDate = startDate,
                                time = time,
                                amPm = amPm,
                                repeatDaily = normalizedRepeatDaily,
                                reminderMinutes = reminderMinutes,
                                status = BatchStatus.DRAFT,
                                scheduledAt = null
                            )
                            batchManager.updateBatch(updatedBatch)
                            updatedBatch.id
                        } else {
                            batchManager.createBatch(
                                mediaList = selectedMedia,
                                scheduleType = scheduleType,
                                startDate = startDate,
                                time = time,
                                amPm = amPm,
                                repeatDaily = normalizedRepeatDaily,
                                reminderMinutes = reminderMinutes
                            )
                        }

                        if (batchId == null) {
                            Toast.makeText(
                                context,
                                "Failed to prepare batch for scheduling",
                                Toast.LENGTH_SHORT
                            ).show()
                            return@launch
                        }

                        val result = batchManager.scheduleBatchWithResult(batchId)
                        if (result.success) {
                            previewBatchId = batchId
                            scheduleTargetBatchId = null
                            selectedMedia = emptyList()
                            currentScreen = SchedulerScreen.BATCH_PREVIEW
                            Toast.makeText(
                                context,
                                result.message,
                                Toast.LENGTH_SHORT
                            ).show()
                        } else {
                            if (!batchManager.canScheduleExactAlarms()) {
                                showAlarmPermissionDialog = true
                                Toast.makeText(
                                    context,
                                    "Exact Alarm permission is required for scheduling.",
                                    Toast.LENGTH_LONG
                                ).show()
                            } else {
                                Toast.makeText(
                                    context,
                                    result.message,
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    }
                },
                onBack = {
                    currentScreen = scheduleBackTarget
                }
            )
        }

        SchedulerScreen.BATCH_PREVIEW -> {
            currentPreviewBatch?.let { batch ->
                StatusBatchPreviewScreen(
                    batch = batch,
                    onBack = { currentScreen = SchedulerScreen.BATCH_LIST },
                    onSchedule = { openScheduleEditor(batch, SchedulerScreen.BATCH_PREVIEW) },
                    onPostNow = { postBatchNow(batch) },
                    onCancelSchedule = {
                        scope.launch {
                            val success = batchManager.cancelBatchSchedule(batch.id)
                            if (success) {
                                Toast.makeText(context, "Schedule cancelled", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(
                                    context,
                                    "Failed to cancel schedule",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    },
                    onDelete = {
                        scope.launch {
                            batchManager.deleteBatch(batch)
                            previewBatchId = null
                            currentScreen = SchedulerScreen.BATCH_LIST
                            Toast.makeText(context, "Batch deleted", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            }
        }
    }

    if (showTrialDialog) {
        StatusSchedulerTrialDialog(
            onDismiss = { showTrialDialog = false },
            onStartTrial = {
                startStatusSchedulerTrial(context)
                showTrialDialog = false
                Toast.makeText(
                    context,
                    "2-hour Status Scheduler trial started",
                    Toast.LENGTH_SHORT
                ).show()
            }
        )
    }

    if (showUpgradeDialog) {
        StatusSchedulerUpgradeDialog(
            onDismiss = { showUpgradeDialog = false },
            onUpgrade = {
                showUpgradeDialog = false
                context.startActivity(Intent(context, PrepackActivity::class.java))
            }
        )
    }

    if (showAlarmPermissionDialog) {
        AlarmPermissionDialog(
            onDismiss = { showAlarmPermissionDialog = false },
            onRequestPermission = {
                AlarmPermissionHelper.openAlarmPermissionSettings(context)
                showAlarmPermissionDialog = false
            }
        )
    }

    if (showAccessibilityDialog) {
        AccessibilityPermissionDialog(
            onAgree = {
                Toast.makeText(
                    context,
                    "Opening Accessibility Settings. Please enable the Bulk Send service and return to the app.",
                    Toast.LENGTH_LONG
                ).show()
            },
            onDisagree = {
                Toast.makeText(
                    context,
                    "Scheduled status posting will not work without Accessibility permission.",
                    Toast.LENGTH_SHORT
                ).show()
            },
            onDismiss = { showAccessibilityDialog = false }
        )
    }
}

@Composable
private fun StatusSchedulerTrialDialog(
    onDismiss: () -> Unit,
    onStartTrial: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        val headerBrush =
            Brush.linearGradient(colors = listOf(Color(0xFF3B82F6), Color(0xFF06B6D4)))

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            shape = RoundedCornerShape(28.dp),
            color = Color.White,
            tonalElevation = 10.dp
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(headerBrush)
                        .padding(horizontal = 20.dp, vertical = 22.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            IconButton(onClick = onDismiss) {
                                Icon(
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = "Close",
                                    tint = Color.White
                                )
                            }
                        }

                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .background(Color.White.copy(alpha = 0.18f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.AccessTime,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(34.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Text(
                            text = "Start Free Trial",
                            color = Color.White,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "You get a 2-hour Status Scheduler trial for testing. Start it when you are ready.",
                            color = Color.White.copy(alpha = 0.92f),
                            fontSize = 14.sp,
                            lineHeight = 20.sp
                        )
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    UpgradePoint("Trial starts only after you tap Start My Trial")
                    UpgradePoint("Use this time to test status scheduling flow")
                    UpgradePoint("After 2 hours, paid plan will be required")
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .padding(bottom = 22.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = onStartTrial,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors =
                            ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB))
                    ) {
                        Icon(
                            imageVector = Icons.Filled.AutoAwesome,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.size(8.dp))
                        Text(
                            text = "Start My Trial",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Button(
                        onClick = onDismiss,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors =
                            ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFF1F5F9),
                                contentColor = Color(0xFF0F172A)
                            )
                    ) {
                        Text(
                            text = "Not Now",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusSchedulerUpgradeDialog(
    onDismiss: () -> Unit,
    onUpgrade: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        val headerBrush =
            Brush.linearGradient(colors = listOf(Color(0xFF10B981), Color(0xFF059669)))

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            shape = RoundedCornerShape(28.dp),
            color = Color.White,
            tonalElevation = 10.dp
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(headerBrush)
                        .padding(horizontal = 20.dp, vertical = 22.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            IconButton(onClick = onDismiss) {
                                Icon(
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = "Close",
                                    tint = Color.White
                                )
                            }
                        }

                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .background(Color.White.copy(alpha = 0.18f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Lock,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(34.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Text(
                            text = "Trial Ended",
                            color = Color.White,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Your 2-hour Status Scheduler trial is over. Upgrade to continue using this feature.",
                            color = Color.White.copy(alpha = 0.92f),
                            fontSize = 14.sp,
                            lineHeight = 20.sp
                        )
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    UpgradePoint("Free testing window has expired")
                    UpgradePoint("Paid plan unlocks full Status Scheduler access")
                    UpgradePoint("Open plan page and upgrade to continue")
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .padding(bottom = 22.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = onUpgrade,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors =
                            ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Lock,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.size(8.dp))
                        Text(
                            text = "View Paid Plans",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Button(
                        onClick = onDismiss,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors =
                            ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFF1F5F9),
                                contentColor = Color(0xFF0F172A)
                            )
                    ) {
                        Text(
                            text = "Maybe Later",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun UpgradePoint(text: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(Color(0xFF10B981), CircleShape)
        )
        Text(
            text = text,
            color = Color(0xFF334155),
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
        )
    }
}
