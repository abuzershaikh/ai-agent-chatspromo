package com.message.bulksend.autorespond.statusscheduled

import android.app.Notification
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import com.message.bulksend.R
import com.message.bulksend.autorespond.database.MessageDatabase
import com.message.bulksend.autorespond.statusscheduled.database.StatusBatchRepository
import com.message.bulksend.autorespond.statusscheduled.models.BatchStatus
import com.message.bulksend.autorespond.statusscheduled.models.MediaItem
import com.message.bulksend.autorespond.statusscheduled.models.MediaType
import com.message.bulksend.autorespond.statusscheduled.models.ScheduleType
import com.message.bulksend.bulksend.CampaignState
import com.message.bulksend.bulksend.WhatsAppAutoSendService
import com.message.bulksend.utils.AccessibilityHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class StatusBatchExecutionService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        val batchId = intent?.getLongExtra(StatusBatchAlarmReceiver.EXTRA_BATCH_ID, -1L) ?: -1L
        val source = intent?.getStringExtra(EXTRA_TRIGGER_SOURCE).orEmpty().ifBlank { SOURCE_UNKNOWN }

        Log.d(
            TAG,
            "onStartCommand action=$action batchId=$batchId source=$source flags=$flags startId=$startId"
        )

        if (action != ACTION_EXECUTE_BATCH) {
            Log.w(TAG, "Ignoring start due to unexpected action=$action source=$source")
            stopSelf(startId)
            return START_NOT_STICKY
        }

        if (batchId <= 0L) {
            Log.e(TAG, "Invalid batch id received in service action=$action source=$source")
            stopSelf(startId)
            return START_NOT_STICKY
        }

        if (!acquireExecutionSlot(batchId)) {
            Log.w(TAG, "Ignoring duplicate execution request for batch=$batchId source=$source")
            stopSelf(startId)
            return START_NOT_STICKY
        }

        Log.d(TAG, "Starting foreground execution for batch=$batchId source=$source")
        startForeground(NOTIFICATION_ID, createNotification("Preparing scheduled status batch..."))

        serviceScope.launch {
            Log.d(TAG, "Execution coroutine started batch=$batchId source=$source")
            try {
                executeBatch(batchId, source)
            } finally {
                cleanupAutomationState()
                releaseExecutionSlot(batchId)
                Log.d(TAG, "Execution coroutine finished batch=$batchId source=$source")
                stopSelf(startId)
            }
        }

        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private suspend fun executeBatch(batchId: Long, source: String) {
        var shouldLaunchApp = false
        try {
            val database = MessageDatabase.getDatabase(applicationContext)
            val repository = StatusBatchRepository(database.statusBatchDao())
            val batchManager = StatusBatchManager(applicationContext, repository)

            val batch = repository.getBatchById(batchId)
            if (batch == null) {
                Log.e(TAG, "Batch not found: $batchId source=$source")
                return
            }

            Log.d(
                TAG,
                "executeBatch batchId=$batchId source=$source status=${batch.status} type=${batch.scheduleType} " +
                    "mediaCount=${batch.mediaList.size} repeatDaily=${batch.repeatDaily} scheduledAt=${formatTimestamp(batch.scheduledAt)}"
            )

            val allowedStatuses = setOf(
                BatchStatus.SCHEDULED,
                BatchStatus.DRAFT,
                BatchStatus.FAILED,
                BatchStatus.POSTING
            )
            if (batch.status !in allowedStatuses) {
                Log.w(TAG, "Skipping batch $batchId due to status=${batch.status} source=$source")
                return
            }

            // Prevent duplicate delayed resume alarms once execution has started again.
            cancelAutoResume(batchId)

            if (batch.mediaList.isEmpty()) {
                Log.e(TAG, "Cannot execute empty media list batch=$batchId source=$source")
                clearBatchProgress(batchId)
                repository.updateBatchStatus(batchId, BatchStatus.FAILED)
                return
            }

            if (!isAccessibilityEnabled()) {
                Log.e(TAG, "Accessibility not enabled. Cannot execute scheduled status batch batch=$batchId source=$source")
                repository.updateBatchStatus(batchId, BatchStatus.FAILED)
                return
            }

            val targetPackage = resolveAvailableWhatsAppPackage()
            if (targetPackage == null) {
                Log.e(TAG, "No WhatsApp package installed batch=$batchId source=$source")
                repository.updateBatchStatus(batchId, BatchStatus.FAILED)
                return
            }

            repository.updateBatchStatus(batchId, BatchStatus.POSTING)

            Log.d(TAG, "Using package=$targetPackage for batch=$batchId source=$source")
            val startIndex = getSavedNextMediaIndex(batchId, batch.mediaList.size)
            if (startIndex > 0) {
                Log.d(
                    TAG,
                    "Resuming batch=$batchId from media ${startIndex + 1}/${batch.mediaList.size} source=$source"
                )
            }

            var allSent = true

            for (index in startIndex until batch.mediaList.size) {
                val media = batch.mediaList[index]
                Log.d(
                    TAG,
                    "Posting media ${index + 1}/${batch.mediaList.size} batch=$batchId name=${media.name} delay=${media.delayMinutes}m"
                )
                updateNotification("Posting status ${index + 1}/${batch.mediaList.size}")

                val sent = shareSingleMedia(media, targetPackage, batchManager)
                if (!sent) {
                    Log.e(
                        TAG,
                        "Media failed batch=$batchId index=${index + 1}/${batch.mediaList.size} name=${media.name}"
                    )
                    allSent = false
                    break
                }

                // Persist progress so a retry continues from the first unsent media.
                saveNextMediaIndex(batchId, index + 1)
                Log.d(TAG, "Media sent batch=$batchId index=${index + 1}/${batch.mediaList.size} name=${media.name}")
                if (index < batch.mediaList.lastIndex) {
                    Log.d(TAG, "Applying fixed ${INTER_MEDIA_GAP_MS}ms gap before next media batch=$batchId")
                    delay(INTER_MEDIA_GAP_MS)
                    if (media.delayMinutes > 0) {
                        Log.d(TAG, "Applying extra delay of ${media.delayMinutes} minutes before next media batch=$batchId")
                        delay(media.delayMinutes * 60_000L)
                    }
                }
            }

            if (allSent) {
                clearBatchProgress(batchId)
                cancelAutoResume(batchId)
                val shouldRepeat = batch.repeatDaily || batch.scheduleType == ScheduleType.AUTO
                if (shouldRepeat) {
                    Log.d(TAG, "Rescheduling daily batch=$batchId after successful run")
                    val rescheduled = batchManager.scheduleBatch(batch.id)
                    if (!rescheduled) {
                        Log.w(TAG, "Reschedule failed for batch=$batchId, marking POSTED")
                        repository.updateBatchStatus(batchId, BatchStatus.POSTED)
                    } else {
                        Log.d(TAG, "Reschedule success for batch=$batchId")
                    }
                } else {
                    repository.updateBatchStatus(batchId, BatchStatus.POSTED)
                    Log.d(TAG, "Marked POSTED for one-time batch=$batchId")
                }
                Log.d(TAG, "Batch $batchId executed successfully source=$source")
                shouldLaunchApp = true
            } else {
                repository.updateBatchStatus(batchId, BatchStatus.FAILED)
                val retryFrom = getSavedNextMediaIndex(batchId, batch.mediaList.size)
                Log.e(
                    TAG,
                    "Batch $batchId failed during execution source=$source retryFromIndex=$retryFrom"
                )
                scheduleAutoResume(batchId, retryFrom)
                if (source == SOURCE_AUTO_RESUME) {
                    // Auto-resume attempt finished but still failed; bring user back to app.
                    shouldLaunchApp = true
                } else {
                    Log.d(TAG, "Skipping app launch for now; waiting auto-resume attempt batch=$batchId")
                }
            }
        } finally {
            if (shouldLaunchApp) {
                launchSchedulerApp(reason = "batch_complete source=$source batchId=$batchId")
            }
        }
    }

    private suspend fun shareSingleMedia(
        media: MediaItem,
        packageName: String,
        batchManager: StatusBatchManager
    ): Boolean {
        Log.d(TAG, "shareSingleMedia start name=${media.name} path=${media.uri} package=$packageName")
        val shareUri = batchManager.getShareUri(media.uri) ?: run {
            Log.e(TAG, "Share URI not found for media: ${media.uri}")
            return false
        }
        Log.d(TAG, "shareSingleMedia shareUri=$shareUri name=${media.name}")

        StatusAutoScheduledState.activate(
            imagePath = media.uri,
            imageUri = shareUri.toString(),
            hyperlink = ""
        )
        CampaignState.isSendActionSuccessful = null
        CampaignState.isAutoSendEnabled = true
        WhatsAppAutoSendService.activateService()

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = resolveMimeType(media)
            setPackage(packageName)
            putExtra(Intent.EXTRA_STREAM, shareUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            clipData = ClipData.newRawUri("status_media", shareUri)
        }

        return try {
            grantUriPermission(packageName, shareUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            Log.d(TAG, "Launching WhatsApp share intent name=${media.name} mime=${shareIntent.type}")
            startActivity(shareIntent)
            val success = waitForSendResult()
            Log.d(TAG, "shareSingleMedia result name=${media.name} success=$success")
            success
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start share intent for media=${media.name}: ${e.message}", e)
            false
        }
    }

    private suspend fun waitForSendResult(timeoutMs: Long = 90_000L): Boolean {
        val start = System.currentTimeMillis()
        while (System.currentTimeMillis() - start < timeoutMs) {
            when (CampaignState.isSendActionSuccessful) {
                true -> {
                    Log.d(TAG, "waitForSendResult success after ${System.currentTimeMillis() - start}ms")
                    return true
                }
                false -> {
                    Log.e(TAG, "waitForSendResult failed after ${System.currentTimeMillis() - start}ms")
                    return false
                }
                null -> delay(500L)
            }
        }
        Log.e(TAG, "waitForSendResult timeout after ${timeoutMs}ms state=${CampaignState.isSendActionSuccessful}")
        return false
    }

    private fun resolveMimeType(media: MediaItem): String {
        val extension = media.uri.substringAfterLast('.', "").lowercase()
        return when (extension) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "webp" -> "image/webp"
            "gif" -> "image/gif"
            "mp4" -> "video/mp4"
            "3gp", "3gpp" -> "video/3gpp"
            "webm" -> "video/webm"
            else -> if (media.type == MediaType.VIDEO) "video/*" else "image/*"
        }
    }

    private fun resolveAvailableWhatsAppPackage(): String? {
        return when {
            isPackageInstalled("com.whatsapp.w4b") -> "com.whatsapp.w4b"
            isPackageInstalled("com.whatsapp") -> "com.whatsapp"
            else -> null
        }
    }

    private fun isPackageInstalled(packageName: String): Boolean {
        return try {
            packageManager.getPackageInfo(packageName, 0)
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun isAccessibilityEnabled(): Boolean {
        return AccessibilityHelper.isAccessibilityServiceEnabled(
            this,
            "com.message.bulksend.bulksend.WhatsAppAutoSendService"
        )
    }

    private fun cleanupAutomationState() {
        Log.d(
            TAG,
            "cleanupAutomationState statusFlowActive=${StatusAutoScheduledState.isStatusFlowActive} " +
                "autoSendEnabled=${CampaignState.isAutoSendEnabled} sendResult=${CampaignState.isSendActionSuccessful}"
        )
        StatusAutoScheduledState.reset()
        CampaignState.isAutoSendEnabled = false
        WhatsAppAutoSendService.deactivateService()
        Log.d(TAG, "cleanupAutomationState complete")
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Status Batch Scheduler",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Scheduled WhatsApp status batch execution"
            }

            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(content: String): Notification {
        val openIntent = Intent(this, StatusSchedulerActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            openIntent,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Status Scheduler Running")
            .setContentText(content)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pendingIntent)
            .build()
    }

    private fun updateNotification(content: String) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, createNotification(content))
    }

    private fun launchSchedulerApp(reason: String) {
        try {
            Handler(Looper.getMainLooper()).post {
                try {
                    val intent = Intent(this, StatusSchedulerActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                        addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                        addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                        putExtra("open_from_status_batch_execution", true)
                        putExtra("open_reason", reason)
                    }
                    startActivity(intent)
                    Log.d(TAG, "Scheduler app launch requested reason=$reason")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to launch scheduler app reason=$reason: ${e.message}", e)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch scheduler app reason=$reason: ${e.message}", e)
        }
    }

    private fun scheduleAutoResume(batchId: Long, retryFromIndex: Int) {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val triggerAt = System.currentTimeMillis() + AUTO_RESUME_DELAY_MS
        val requestCode = autoResumeRequestCode(batchId)
        val intent = Intent(this, StatusBatchAlarmReceiver::class.java).apply {
            action = ACTION_AUTO_RESUME_BATCH
            putExtra(StatusBatchAlarmReceiver.EXTRA_BATCH_ID, batchId)
            putExtra(EXTRA_TRIGGER_SOURCE, SOURCE_AUTO_RESUME)
        }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val pendingIntent = PendingIntent.getBroadcast(this, requestCode, intent, flags)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val canUseExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()
            if (canUseExact) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
            } else {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
        } else {
            alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
        }

        scheduleInProcessAutoResume(batchId, retryFromIndex)
        Log.w(
            TAG,
            "Auto-resume scheduled for batch=$batchId in ${AUTO_RESUME_DELAY_MS}ms retryFromIndex=$retryFromIndex triggerAt=${formatTimestamp(triggerAt)}"
        )
    }

    private fun cancelAutoResume(batchId: Long) {
        cancelInProcessAutoResume(batchId)
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val requestCode = autoResumeRequestCode(batchId)
        val intent = Intent(this, StatusBatchAlarmReceiver::class.java).apply {
            action = ACTION_AUTO_RESUME_BATCH
            putExtra(StatusBatchAlarmReceiver.EXTRA_BATCH_ID, batchId)
            putExtra(EXTRA_TRIGGER_SOURCE, SOURCE_AUTO_RESUME)
        }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_NO_CREATE
        }
        val pendingIntent = PendingIntent.getBroadcast(this, requestCode, intent, flags)
        pendingIntent?.let {
            alarmManager.cancel(it)
            it.cancel()
            Log.d(TAG, "Cancelled pending auto-resume for batch=$batchId")
        }
    }

    private fun scheduleInProcessAutoResume(batchId: Long, retryFromIndex: Int) {
        cancelInProcessAutoResume(batchId)
        val appContext = applicationContext
        val runnable = Runnable {
            synchronized(inProcessResumeRunnables) {
                inProcessResumeRunnables.remove(batchId)
            }
            if (isBatchExecuting(batchId)) {
                Log.d(TAG, "In-process auto-resume skipped; batch already executing batch=$batchId")
                return@Runnable
            }
            Log.d(TAG, "In-process auto-resume refresh trigger batch=$batchId retryFromIndex=$retryFromIndex")
            try {
                startForBatch(appContext, batchId, SOURCE_AUTO_RESUME)
            } catch (e: Exception) {
                Log.e(TAG, "In-process auto-resume trigger failed batch=$batchId: ${e.message}", e)
            }
        }
        synchronized(inProcessResumeRunnables) {
            inProcessResumeRunnables[batchId] = runnable
        }
        mainHandler.postDelayed(runnable, AUTO_RESUME_DELAY_MS)
    }

    private fun cancelInProcessAutoResume(batchId: Long) {
        val runnable = synchronized(inProcessResumeRunnables) {
            inProcessResumeRunnables.remove(batchId)
        }
        if (runnable != null) {
            mainHandler.removeCallbacks(runnable)
            Log.d(TAG, "Cancelled in-process auto-resume refresh batch=$batchId")
        }
    }

    private fun acquireExecutionSlot(batchId: Long): Boolean {
        synchronized(activeBatchExecutions) {
            return activeBatchExecutions.add(batchId)
        }
    }

    private fun releaseExecutionSlot(batchId: Long) {
        synchronized(activeBatchExecutions) {
            activeBatchExecutions.remove(batchId)
        }
    }

    private fun isBatchExecuting(batchId: Long): Boolean {
        synchronized(activeBatchExecutions) {
            return activeBatchExecutions.contains(batchId)
        }
    }

    private fun autoResumeRequestCode(batchId: Long): Int {
        val normalized = (batchId and Int.MAX_VALUE.toLong()).toInt()
        return normalized xor AUTO_RESUME_RC_XOR_MASK
    }

    private fun getSavedNextMediaIndex(batchId: Long, mediaCount: Int): Int {
        val saved = getSharedPreferences(PROGRESS_PREFS, Context.MODE_PRIVATE)
            .getInt(progressKey(batchId), 0)
        return saved.coerceIn(0, mediaCount)
    }

    private fun saveNextMediaIndex(batchId: Long, nextIndex: Int) {
        getSharedPreferences(PROGRESS_PREFS, Context.MODE_PRIVATE)
            .edit()
            .putInt(progressKey(batchId), nextIndex.coerceAtLeast(0))
            .apply()
    }

    private fun clearBatchProgress(batchId: Long) {
        getSharedPreferences(PROGRESS_PREFS, Context.MODE_PRIVATE)
            .edit()
            .remove(progressKey(batchId))
            .apply()
    }

    private fun progressKey(batchId: Long): String = "$PROGRESS_KEY_PREFIX$batchId"

    private fun formatTimestamp(value: Long?): String {
        if (value == null) return "null"
        val formatted = java.text.SimpleDateFormat(
            "yyyy-MM-dd HH:mm:ss",
            java.util.Locale.getDefault()
        ).format(java.util.Date(value))
        return "$value($formatted)"
    }

    companion object {
        private const val TAG = "StatusBatchExecutionSvc"
        private const val CHANNEL_ID = "status_batch_scheduler_channel"
        private const val NOTIFICATION_ID = 32011
        private const val INTER_MEDIA_GAP_MS = 2_000L
        private const val AUTO_RESUME_DELAY_MS = 6_000L
        private const val AUTO_RESUME_RC_XOR_MASK = 0x5A5A0000
        private const val PROGRESS_PREFS = "status_batch_execution_progress"
        private const val PROGRESS_KEY_PREFIX = "next_media_index_"
        private val mainHandler = Handler(Looper.getMainLooper())
        private val inProcessResumeRunnables = mutableMapOf<Long, Runnable>()
        private val activeBatchExecutions = mutableSetOf<Long>()
        const val ACTION_EXECUTE_BATCH = "com.message.bulksend.action.EXECUTE_STATUS_BATCH"
        const val ACTION_AUTO_RESUME_BATCH = "com.message.bulksend.action.AUTO_RESUME_STATUS_BATCH"
        const val EXTRA_TRIGGER_SOURCE = "trigger_source"
        const val SOURCE_ALARM_RECEIVER = "alarm_receiver"
        const val SOURCE_AUTO_RESUME = "auto_resume"
        const val SOURCE_RESTORE_RECOVERY = "restore_recovery"
        const val SOURCE_POST_NOW = "post_now"
        const val SOURCE_POSTING_SERVICE = "posting_service"
        const val SOURCE_UNKNOWN = "unknown"

        fun startForBatch(
            context: Context,
            batchId: Long,
            source: String = SOURCE_UNKNOWN
        ) {
            val intent = Intent(context, StatusBatchExecutionService::class.java).apply {
                action = ACTION_EXECUTE_BATCH
                putExtra(StatusBatchAlarmReceiver.EXTRA_BATCH_ID, batchId)
                putExtra(EXTRA_TRIGGER_SOURCE, source)
            }

            Log.d(TAG, "startForBatch source=$source batchId=$batchId sdk=${Build.VERSION.SDK_INT}")
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                Log.e(TAG, "startForBatch failed source=$source batchId=$batchId: ${e.message}", e)
                throw e
            }
        }
    }
}
