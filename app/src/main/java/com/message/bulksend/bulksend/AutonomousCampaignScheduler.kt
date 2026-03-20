package com.message.bulksend.bulksend

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.message.bulksend.utils.AlarmPermissionHelper

class AutonomousCampaignScheduler(private val context: Context) {

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun scheduleNextExecution(campaignId: String, triggerAtMillis: Long) {
        if (!AlarmPermissionHelper.canScheduleExactAlarms(context)) {
            throw SecurityException("Exact alarm permission not granted")
        }

        val pendingIntent = buildRequiredPendingIntent(campaignId)
        val safeTriggerAt = triggerAtMillis.coerceAtLeast(System.currentTimeMillis() + 1_000L)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                safeTriggerAt,
                pendingIntent
            )
        } else {
            alarmManager.setExact(
                AlarmManager.RTC_WAKEUP,
                safeTriggerAt,
                pendingIntent
            )
        }

        Log.d(TAG, "Scheduled autonomous campaign $campaignId at $safeTriggerAt")
    }

    fun cancel(campaignId: String) {
        val pendingIntent = buildNullablePendingIntent(campaignId) ?: return
        alarmManager.cancel(pendingIntent)
        pendingIntent.cancel()
        Log.d(TAG, "Cancelled autonomous campaign alarm for $campaignId")
    }

    private fun buildRequiredPendingIntent(campaignId: String): PendingIntent {
        return PendingIntent.getBroadcast(
            context,
            campaignId.hashCode(),
            buildIntent(campaignId),
            mutableFlags(PendingIntent.FLAG_UPDATE_CURRENT)
        )
    }

    private fun buildNullablePendingIntent(campaignId: String): PendingIntent? {
        return PendingIntent.getBroadcast(
            context,
            campaignId.hashCode(),
            buildIntent(campaignId),
            mutableFlags(PendingIntent.FLAG_NO_CREATE)
        )
    }

    private fun buildIntent(campaignId: String): Intent {
        val intent = Intent(context, AutonomousCampaignAlarmReceiver::class.java).apply {
            action = AutonomousCampaignExecutionService.ACTION_EXECUTE_AUTONOMOUS_CAMPAIGN
            putExtra(AutonomousCampaignExecutionService.EXTRA_CAMPAIGN_ID, campaignId)
            putExtra(
                AutonomousCampaignExecutionService.EXTRA_TRIGGER_SOURCE,
                AutonomousCampaignExecutionService.SOURCE_ALARM_RECEIVER
            )
        }
        return intent
    }

    private fun mutableFlags(baseFlags: Int): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            baseFlags or PendingIntent.FLAG_IMMUTABLE
        } else {
            baseFlags
        }
    }

    companion object {
        private const val TAG = "AutoCampaignScheduler"
    }
}
