package com.message.bulksend.leadmanager.notifications

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.message.bulksend.leadmanager.LeadManagerActivity
import com.message.bulksend.leadmanager.model.FollowUp
import com.message.bulksend.leadmanager.model.Lead
import com.message.bulksend.leadmanager.repository.LeadRepository
import com.message.bulksend.utils.AlarmPermissionHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class LeadFollowUpNotificationScheduler(context: Context) {

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val alarmManager =
        appContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    private val notificationManager =
        appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val repository = LeadRepository(appContext)

    fun isReminderEnabled(): Boolean = prefs.getBoolean(KEY_REMINDERS_ENABLED, true)

    fun setReminderEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_REMINDERS_ENABLED, enabled).apply()
    }

    suspend fun syncLead(
        previousLead: Lead?,
        updatedLead: Lead,
        showConfirmation: Boolean = false
    ) = withContext(Dispatchers.IO) {
        val previousFollowUps = previousLead?.followUps?.associateBy { it.id }.orEmpty()
        val currentFollowUpIds = updatedLead.followUps.map { it.id }.toSet()

        previousFollowUps.keys
            .filterNot(currentFollowUpIds::contains)
            .forEach(::cancelFollowUp)

        updatedLead.followUps.forEach { followUp ->
            if (!isReminderEnabled() || followUp.isCompleted) {
                cancelFollowUp(followUp.id)
            } else {
                scheduleFollowUpReminder(updatedLead, followUp)
            }

            if (showConfirmation && isReminderEnabled()) {
                val previous = previousFollowUps[followUp.id]
                when {
                    previous == null && !followUp.isCompleted -> {
                        showConfirmationNotification(updatedLead, followUp, isRescheduled = false)
                    }

                    previous != null &&
                        !followUp.isCompleted &&
                        hasReminderTimingChanged(previous, followUp) -> {
                        showConfirmationNotification(updatedLead, followUp, isRescheduled = true)
                    }
                }
            }
        }
    }

    suspend fun cancelLead(lead: Lead) = withContext(Dispatchers.IO) {
        lead.followUps.forEach { cancelFollowUp(it.id) }
    }

    suspend fun rescheduleAllFromDatabase() = withContext(Dispatchers.IO) {
        if (!isReminderEnabled()) {
            cancelAllFromDatabase()
            return@withContext
        }

        repository.getAllLeadsList().forEach { lead ->
            syncLead(previousLead = null, updatedLead = lead, showConfirmation = false)
        }
    }

    suspend fun cancelAllFromDatabase() = withContext(Dispatchers.IO) {
        repository.getAllLeadsList().forEach { lead ->
            lead.followUps.forEach { cancelFollowUp(it.id) }
        }
    }

    fun cancelFollowUp(followUpId: String) {
        val pendingIntent =
            PendingIntent.getBroadcast(
                appContext,
                followUpId.hashCode(),
                Intent(appContext, LeadFollowUpNotificationReceiver::class.java),
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
        pendingIntent?.let {
            alarmManager.cancel(it)
            it.cancel()
        }
    }

    private fun scheduleFollowUpReminder(lead: Lead, followUp: FollowUp) {
        val triggerAt = resolveTriggerAt(followUp) ?: run {
            cancelFollowUp(followUp.id)
            return
        }

        cancelFollowUp(followUp.id)

        val intent =
            Intent(appContext, LeadFollowUpNotificationReceiver::class.java).apply {
                putExtra(EXTRA_LEAD_ID, lead.id)
                putExtra(EXTRA_LEAD_NAME, lead.name)
                putExtra(EXTRA_FOLLOW_UP_ID, followUp.id)
                putExtra(EXTRA_FOLLOW_UP_TITLE, followUp.title)
                putExtra(EXTRA_FOLLOW_UP_TYPE, followUp.type.displayName)
                putExtra(EXTRA_SCHEDULED_AT, followUp.scheduledDate)
            }
        val pendingIntent =
            PendingIntent.getBroadcast(
                appContext,
                followUp.id.hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

        try {
            when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                    AlarmPermissionHelper.canScheduleExactAlarms(appContext) -> {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerAt,
                        pendingIntent
                    )
                }

                Build.VERSION.SDK_INT >= Build.VERSION_CODES.M -> {
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerAt,
                        pendingIntent
                    )
                }

                else -> {
                    alarmManager.set(
                        AlarmManager.RTC_WAKEUP,
                        triggerAt,
                        pendingIntent
                    )
                }
            }
        } catch (_: SecurityException) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAt,
                    pendingIntent
                )
            } else {
                alarmManager.set(
                    AlarmManager.RTC_WAKEUP,
                    triggerAt,
                    pendingIntent
                )
            }
        }
    }

    private fun resolveTriggerAt(followUp: FollowUp): Long? {
        val now = System.currentTimeMillis()
        if (followUp.isCompleted || followUp.scheduledDate <= now) return null

        // CRM follow-up reminders should fire at the exact scheduled time.
        return followUp.scheduledDate
    }

    private fun hasReminderTimingChanged(previous: FollowUp, current: FollowUp): Boolean {
        return previous.scheduledDate != current.scheduledDate ||
            previous.scheduledTime != current.scheduledTime
    }

    private fun showConfirmationNotification(
        lead: Lead,
        followUp: FollowUp,
        isRescheduled: Boolean
    ) {
        ensureChannel()

        val openIntent =
            Intent(appContext, LeadManagerActivity::class.java).apply {
                putExtra(LeadManagerActivity.EXTRA_INITIAL_TAB, 3)
                putExtra(LeadManagerActivity.EXTRA_LEAD_ID, lead.id)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
        val pendingIntent =
            PendingIntent.getActivity(
                appContext,
                followUp.id.hashCode(),
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

        val formattedDate =
            SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()).format(Date(followUp.scheduledDate))
        val body =
            buildString {
                append(if (followUp.title.isNotBlank()) followUp.title else "Follow-up")
                append(" for ${lead.name}")
                append(" on $formattedDate")
            }

        val notification =
            NotificationCompat.Builder(appContext, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(if (isRescheduled) "Follow-up rescheduled" else "Follow-up scheduled")
                .setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .build()

        runCatching {
            notificationManager.notify(
                ((followUp.id.hashCode() * 31L) + if (isRescheduled) 2 else 1).toInt(),
                notification
            )
        }
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel =
                NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_HIGH
                )
            notificationManager.createNotificationChannel(channel)
        }
    }

    companion object {
        private const val PREFS_NAME = "lead_manager_prefs"
        private const val KEY_REMINDERS_ENABLED = "follow_up_reminders_enabled"

        const val CHANNEL_ID = "crm_follow_up_reminder_channel"
        const val CHANNEL_NAME = "CRM Follow-up Reminders"

        const val EXTRA_LEAD_ID = "crm_lead_id"
        const val EXTRA_LEAD_NAME = "crm_lead_name"
        const val EXTRA_FOLLOW_UP_ID = "crm_follow_up_id"
        const val EXTRA_FOLLOW_UP_TITLE = "crm_follow_up_title"
        const val EXTRA_FOLLOW_UP_TYPE = "crm_follow_up_type"
        const val EXTRA_SCHEDULED_AT = "crm_follow_up_scheduled_at"
    }
}
