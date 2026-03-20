package com.message.bulksend.autorespond.statusscheduled

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.content.FileProvider
import com.message.bulksend.autorespond.statusscheduled.database.StatusBatchRepository
import com.message.bulksend.autorespond.statusscheduled.models.*
import com.message.bulksend.utils.AlarmPermissionHelper
import java.io.File
import java.io.FileOutputStream
import java.util.Calendar
import java.util.TimeZone
import java.util.UUID

class StatusBatchManager(
    private val context: Context,
    private val repository: StatusBatchRepository
) {
    
    companion object {
        private const val TAG = "StatusBatchManager"
        const val MAX_BATCHES = 30
        const val MAX_VIDEO_SIZE_MB = 16L
        const val MAX_VIDEO_SIZE_BYTES = MAX_VIDEO_SIZE_MB * 1024 * 1024
        private const val MANUAL_RESTORE_GRACE_MS = 2 * 60 * 1000L
    }
    
    // Validate video size
    fun validateVideoSize(sizeBytes: Long): Boolean {
        return sizeBytes <= MAX_VIDEO_SIZE_BYTES
    }

    fun canScheduleExactAlarms(): Boolean {
        return AlarmPermissionHelper.canScheduleExactAlarms(context)
    }
    
    // Import media from URI
    suspend fun importMedia(uri: Uri, type: MediaType): MediaItem? {
        return try {
            val folder = File(context.filesDir, "status_media")
            if (!folder.exists()) folder.mkdirs()
            
            val mime = context.contentResolver.getType(uri) ?: ""
            val extension = when {
                mime.contains("png") -> "png"
                mime.contains("jpg") || mime.contains("jpeg") -> "jpg"
                mime.contains("webp") -> "webp"
                mime.contains("mp4") -> "mp4"
                mime.contains("3gp") -> "3gp"
                else -> if (type == MediaType.VIDEO) "mp4" else "jpg"
            }
            
            // Use UUID + timestamp to avoid accidental overwrite when multiple media are added quickly.
            val fileName = "media_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(8)}.$extension"
            val outFile = File(folder, fileName)
            
            var fileSize = 0L
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(outFile).use { output ->
                    val buffer = ByteArray(8192)
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                        fileSize += read
                    }
                }
            }
            
            // Validate video size
            if (type == MediaType.VIDEO && !validateVideoSize(fileSize)) {
                outFile.delete()
                return null
            }
            
            MediaItem(
                uri = outFile.absolutePath,
                type = type,
                name = fileName,
                size = fileSize,
                duration = null,
                delayMinutes = 0
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to import media: ${e.message}", e)
            null
        }
    }
    
    // Get FileProvider URI for sharing
    fun getShareUri(filePath: String): Uri? {
        return try {
            val file = File(filePath)
            if (!file.exists()) return null
            
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",
                file
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get share URI: ${e.message}", e)
            null
        }
    }
    
    // Create new batch
    suspend fun createBatch(
        mediaList: List<MediaItem>,
        scheduleType: ScheduleType,
        startDate: Long? = null,
        time: String? = null,
        amPm: String? = null,
        repeatDaily: Boolean = false,
        reminderMinutes: Int? = null
    ): Long? {
        if (!repository.canAddMoreBatches()) {
            Log.e(TAG, "Cannot add more batches. Max limit reached.")
            return null
        }
        
        val batch = StatusBatch(
            mediaList = mediaList,
            scheduleType = scheduleType,
            startDate = startDate,
            time = time,
            amPm = amPm,
            repeatDaily = repeatDaily,
            reminderMinutes = reminderMinutes,
            status = BatchStatus.DRAFT
        )

        Log.d(
            TAG,
            "createBatch type=$scheduleType mediaCount=${mediaList.size} startDate=${formatMillis(startDate)} " +
                "time=$time amPm=$amPm repeatDaily=$repeatDaily reminder=$reminderMinutes"
        )
        
        return repository.insertBatch(batch)
    }
    
    // Update batch
    suspend fun updateBatch(batch: StatusBatch) {
        repository.updateBatch(batch)
    }
    
    // Delete batch
    suspend fun deleteBatch(batch: StatusBatch) {
        cancelScheduledAlarm(batch.id)

        // Delete media files
        batch.mediaList.forEach { media ->
            try {
                File(media.uri).delete()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to delete media file: ${e.message}")
            }
        }
        repository.deleteBatch(batch)
    }
    
    // Schedule batch
    suspend fun scheduleBatch(batchId: Long): Boolean {
        Log.d(TAG, "scheduleBatch requested batchId=$batchId")
        val batch = repository.getBatchById(batchId) ?: run {
            Log.e(TAG, "scheduleBatch failed: batch not found batchId=$batchId")
            return false
        }
        Log.d(
            TAG,
            "scheduleBatch batchId=$batchId status=${batch.status} type=${batch.scheduleType} " +
                "startDate=${formatMillis(batch.startDate)} time=${batch.time} amPm=${batch.amPm} repeatDaily=${batch.repeatDaily}"
        )
        val triggerAt = calculateNextTriggerTime(batch) ?: run {
            Log.e(TAG, "Cannot schedule batch $batchId: invalid or past schedule")
            return false
        }
        Log.d(TAG, "scheduleBatch computed triggerAt=${formatMillis(triggerAt)} batchId=$batchId")

        val updatedBatch = batch.copy(
            status = BatchStatus.SCHEDULED,
            scheduledAt = triggerAt
        )

        val alarmScheduled = scheduleWithAlarmManager(updatedBatch, triggerAt)
        if (!alarmScheduled) {
            Log.e(TAG, "Cannot schedule batch $batchId: exact alarm could not be registered")
            return false
        }

        repository.updateBatch(updatedBatch)
        Log.d(TAG, "scheduleBatch success batchId=$batchId scheduledAt=${formatMillis(triggerAt)}")
        return true
    }

    suspend fun cancelBatchSchedule(batchId: Long): Boolean {
        Log.d(TAG, "cancelBatchSchedule requested batchId=$batchId")
        val batch = repository.getBatchById(batchId) ?: run {
            Log.e(TAG, "cancelBatchSchedule failed: batch not found batchId=$batchId")
            return false
        }

        cancelScheduledAlarm(batchId)
        val updated = batch.copy(
            status = BatchStatus.DRAFT,
            scheduledAt = null
        )
        repository.updateBatch(updated)
        Log.d(TAG, "cancelBatchSchedule success batchId=$batchId movedTo=DRAFT")
        return true
    }

    suspend fun restoreScheduledBatches(): Int {
        val scheduled = repository.getBatchesByStatus(BatchStatus.SCHEDULED)
        Log.d(TAG, "restoreScheduledBatches start scheduledCount=${scheduled.size}")

        val maxBatchId = repository.getMaxBatchId()
        if (maxBatchId > 0) {
            // Clear old alarms first so deleted/orphan request codes do not keep firing.
            Log.d(TAG, "restoreScheduledBatches clearing alarms from 1..$maxBatchId")
            for (batchId in 1L..maxBatchId) {
                cancelScheduledAlarm(batchId)
            }
        }

        if (scheduled.isEmpty()) return 0

        var restored = 0
        scheduled.forEach { batch ->
            Log.d(
                TAG,
                "restoreScheduledBatches evaluating batch=${batch.id} status=${batch.status} " +
                    "scheduledAt=${formatMillis(batch.scheduledAt)} startDate=${formatMillis(batch.startDate)} " +
                    "time=${batch.time} amPm=${batch.amPm} repeatDaily=${batch.repeatDaily}"
            )
            val triggerAt = calculateNextTriggerTime(batch)
            if (triggerAt == null) {
                if (shouldRunMissedManualBatchNow(batch)) {
                    Log.w(
                        TAG,
                        "Batch ${batch.id} is slightly overdue during restore; triggering immediate execution"
                    )
                    try {
                        repository.updateBatchStatus(batch.id, BatchStatus.POSTING)
                        StatusBatchExecutionService.startForBatch(
                            context = context,
                            batchId = batch.id,
                            source = StatusBatchExecutionService.SOURCE_RESTORE_RECOVERY
                        )
                        restored++
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to trigger immediate restore execution for batch=${batch.id}", e)
                        repository.updateBatchStatus(batch.id, BatchStatus.FAILED)
                    }
                } else {
                    Log.w(TAG, "Marking batch ${batch.id} FAILED during restore (invalid/past manual schedule)")
                    repository.updateBatchStatus(batch.id, BatchStatus.FAILED)
                }
                return@forEach
            }

            val updated = if (batch.scheduledAt != triggerAt) {
                batch.copy(scheduledAt = triggerAt)
            } else {
                batch
            }

            val alarmScheduled = scheduleWithAlarmManager(updated, triggerAt)
            if (alarmScheduled) {
                repository.updateBatch(updated)
                restored++
                Log.d(TAG, "restoreScheduledBatches restored batch=${batch.id} at ${formatMillis(triggerAt)}")
            } else {
                Log.w(TAG, "Skipped restore for batch ${batch.id}: exact alarm not available")
            }
        }

        Log.d(TAG, "Restored $restored scheduled status batches")
        return restored
    }

    private fun shouldRunMissedManualBatchNow(batch: StatusBatch): Boolean {
        if (batch.scheduleType != ScheduleType.MANUAL || batch.repeatDaily) return false
        val scheduledAt = batch.scheduledAt ?: return false
        val now = System.currentTimeMillis()
        val overdueBy = now - scheduledAt
        val withinGrace = overdueBy in 0..MANUAL_RESTORE_GRACE_MS
        if (withinGrace) {
            Log.d(
                TAG,
                "Manual batch ${batch.id} overdue by ${overdueBy}ms; allowing immediate recovery run"
            )
        }
        return withinGrace
    }

    private fun scheduleWithAlarmManager(batch: StatusBatch, triggerAtMillis: Long): Boolean {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val requestCode = batch.id.toInt()

        return try {
            // Cancel old alarm token first. PendingIntent.cancel() invalidates that token,
            // so we must create a fresh PendingIntent after cancellation.
            cancelScheduledAlarm(batch.id)

            val intent = Intent(context, StatusBatchAlarmReceiver::class.java).apply {
                putExtra(StatusBatchAlarmReceiver.EXTRA_BATCH_ID, batch.id)
            }
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
            val pendingIntent = PendingIntent.getBroadcast(context, requestCode, intent, flags)

            Log.d(
                TAG,
                "scheduleWithAlarmManager batch=${batch.id} requestCode=$requestCode triggerAt=${formatMillis(triggerAtMillis)} " +
                    "sdk=${Build.VERSION.SDK_INT}"
            )

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
                Log.e(TAG, "Exact alarm permission unavailable for batch ${batch.id}; skipping inexact fallback")
                return false
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                alarmManager.setExact(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
            } else {
                alarmManager.set(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
            }

            Log.d(
                TAG,
                "Exact status batch alarm set for batch ${batch.id} at ${formatMillis(triggerAtMillis)}"
            )
            true
        } catch (security: SecurityException) {
            Log.e(
                TAG,
                "Exact alarm denied for batch ${batch.id}: ${security.message}",
                security
            )
            false
        } catch (e: Exception) {
            Log.e(TAG, "Failed to schedule batch ${batch.id}: ${e.message}", e)
            false
        }
    }

    private fun cancelScheduledAlarm(batchId: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val requestCode = batchId.toInt()
        val intent = Intent(context, StatusBatchAlarmReceiver::class.java)
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_NO_CREATE
        }
        val pendingIntent = PendingIntent.getBroadcast(context, requestCode, intent, flags)
        pendingIntent?.let {
            alarmManager.cancel(it)
            it.cancel()
            Log.d(TAG, "Cancelled alarm for status batch $batchId")
        } ?: run {
            Log.d(TAG, "No existing alarm to cancel for status batch $batchId")
        }
    }

    private fun calculateNextTriggerTime(batch: StatusBatch): Long? {
        val dateMillis = batch.startDate ?: run {
            Log.w(TAG, "calculateNextTriggerTime missing startDate batch=${batch.id}")
            return null
        }
        val time = batch.time ?: run {
            Log.w(TAG, "calculateNextTriggerTime missing time batch=${batch.id}")
            return null
        }
        val amPm = batch.amPm ?: run {
            Log.w(TAG, "calculateNextTriggerTime missing amPm batch=${batch.id}")
            return null
        }

        val parts = time.split(":")
        if (parts.size != 2) {
            Log.w(TAG, "calculateNextTriggerTime invalid time format batch=${batch.id} time=$time")
            return null
        }

        val hour12 = parts[0].toIntOrNull() ?: run {
            Log.w(TAG, "calculateNextTriggerTime invalid hour batch=${batch.id} time=$time")
            return null
        }
        val minute = parts[1].toIntOrNull() ?: run {
            Log.w(TAG, "calculateNextTriggerTime invalid minute batch=${batch.id} time=$time")
            return null
        }
        if (hour12 !in 1..12 || minute !in 0..59) {
            Log.w(TAG, "calculateNextTriggerTime out-of-range time batch=${batch.id} hour12=$hour12 minute=$minute")
            return null
        }

        val hour24 = when {
            amPm.equals("AM", ignoreCase = true) && hour12 == 12 -> 0
            amPm.equals("AM", ignoreCase = true) -> hour12
            amPm.equals("PM", ignoreCase = true) && hour12 == 12 -> 12
            amPm.equals("PM", ignoreCase = true) -> hour12 + 12
            else -> {
                Log.w(TAG, "calculateNextTriggerTime invalid amPm batch=${batch.id} amPm=$amPm")
                return null
            }
        }

        // DatePicker returns milliseconds since epoch in LOCAL timezone (midnight local time)
        // We need to read it in local timezone to get the correct date
        val selectedDate = Calendar.getInstance().apply {
            timeInMillis = dateMillis
            set(Calendar.HOUR_OF_DAY, hour24)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val now = System.currentTimeMillis()
        val shouldRepeatDaily = batch.repeatDaily || batch.scheduleType == ScheduleType.AUTO
        if (selectedDate.timeInMillis <= now) {
            if (!shouldRepeatDaily) {
                Log.w(
                    TAG,
                    "calculateNextTriggerTime target in past for manual batch=${batch.id} target=${formatMillis(selectedDate.timeInMillis)} now=${formatMillis(now)}"
                )
                return null
            }
            while (selectedDate.timeInMillis <= now) {
                selectedDate.add(Calendar.DAY_OF_YEAR, 1)
            }
            Log.d(
                TAG,
                "calculateNextTriggerTime moved target to next day batch=${batch.id} target=${formatMillis(selectedDate.timeInMillis)}"
            )
        }

        Log.d(
            TAG,
            "calculateNextTriggerTime resolved batch=${batch.id} target=${formatMillis(selectedDate.timeInMillis)} now=${formatMillis(now)} shouldRepeatDaily=$shouldRepeatDaily"
        )
        return selectedDate.timeInMillis
    }
    
    // Get all batches
    fun getAllBatches() = repository.getAllBatches()
    
    // Get batch by ID
    suspend fun getBatchById(batchId: Long) = repository.getBatchById(batchId)
    
    // Check if can add more batches
    suspend fun canAddMoreBatches() = repository.canAddMoreBatches()
    
    // Get active batch count
    suspend fun getActiveBatchCount() = repository.getActiveBatchCount()

    private fun formatMillis(value: Long?): String {
        if (value == null) return "null"
        val formatted = java.text.SimpleDateFormat(
            "yyyy-MM-dd HH:mm:ss",
            java.util.Locale.getDefault()
        ).format(java.util.Date(value))
        return "$value($formatted)"
    }
}
