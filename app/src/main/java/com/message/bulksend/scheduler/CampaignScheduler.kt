package com.message.bulksend.scheduler

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.message.bulksend.db.ScheduledCampaign
import com.message.bulksend.utils.AlarmPermissionHelper

class CampaignScheduler(private val context: Context) {
    
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    
    fun scheduleExecution(scheduledCampaign: ScheduledCampaign) {
        // Check if we have permission to schedule exact alarms
        if (!AlarmPermissionHelper.canScheduleExactAlarms(context)) {
            Log.e("CampaignScheduler", "Cannot schedule exact alarms - permission not granted")
            throw SecurityException("SCHEDULE_EXACT_ALARM permission not granted. Please enable it in Settings > Apps > Special app access > Alarms & reminders")
        }
        
        val intent = Intent(context, ScheduledCampaignReceiver::class.java).apply {
            putExtra("SCHEDULED_CAMPAIGN_ID", scheduledCampaign.id)
            putExtra("CAMPAIGN_TYPE", scheduledCampaign.campaignType)
        }
        
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            scheduledCampaign.id.hashCode(),
            intent,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
        )
        
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    scheduledCampaign.scheduledTime,
                    pendingIntent
                )
            } else {
                alarmManager.setExact(
                    AlarmManager.RTC_WAKEUP,
                    scheduledCampaign.scheduledTime,
                    pendingIntent
                )
            }
            
            Log.d("CampaignScheduler", "Campaign ${scheduledCampaign.id} scheduled for ${scheduledCampaign.scheduledTime}")
        } catch (e: SecurityException) {
            Log.e("CampaignScheduler", "SecurityException: SCHEDULE_EXACT_ALARM permission required", e)
            throw e
        } catch (e: Exception) {
            Log.e("CampaignScheduler", "Failed to schedule campaign: ${e.message}", e)
            throw e
        }
    }
    
    fun cancelScheduledExecution(campaignId: String) {
        val intent = Intent(context, ScheduledCampaignReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            campaignId.hashCode(),
            intent,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            } else {
                PendingIntent.FLAG_NO_CREATE
            }
        )
        
        pendingIntent?.let {
            alarmManager.cancel(it)
            it.cancel()
            Log.d("CampaignScheduler", "Cancelled scheduled campaign: $campaignId")
        }
    }
    
    fun rescheduleExecution(scheduledCampaign: ScheduledCampaign) {
        cancelScheduledExecution(scheduledCampaign.id)
        scheduleExecution(scheduledCampaign)
    }
}