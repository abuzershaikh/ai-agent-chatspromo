package com.message.bulksend.leadmanager.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.message.bulksend.leadmanager.LeadManagerActivity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class LeadFollowUpNotificationReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val leadId = intent.getStringExtra(LeadFollowUpNotificationScheduler.EXTRA_LEAD_ID).orEmpty()
        val leadName = intent.getStringExtra(LeadFollowUpNotificationScheduler.EXTRA_LEAD_NAME).orEmpty()
        val followUpId = intent.getStringExtra(LeadFollowUpNotificationScheduler.EXTRA_FOLLOW_UP_ID).orEmpty()
        val followUpTitle = intent.getStringExtra(LeadFollowUpNotificationScheduler.EXTRA_FOLLOW_UP_TITLE).orEmpty()
        val followUpType = intent.getStringExtra(LeadFollowUpNotificationScheduler.EXTRA_FOLLOW_UP_TYPE).orEmpty()
        val scheduledAt = intent.getLongExtra(LeadFollowUpNotificationScheduler.EXTRA_SCHEDULED_AT, 0L)
        if (followUpId.isBlank() || leadId.isBlank() || scheduledAt <= 0L) return

        val manager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel =
                NotificationChannel(
                    LeadFollowUpNotificationScheduler.CHANNEL_ID,
                    LeadFollowUpNotificationScheduler.CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_HIGH
                )
            manager.createNotificationChannel(channel)
        }

        val openIntent =
            Intent(context, LeadManagerActivity::class.java).apply {
                putExtra(LeadManagerActivity.EXTRA_INITIAL_TAB, 3)
                putExtra(LeadManagerActivity.EXTRA_LEAD_ID, leadId)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
        val pendingIntent =
            PendingIntent.getActivity(
                context,
                followUpId.hashCode(),
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

        val formattedTime =
            SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()).format(Date(scheduledAt))
        val body =
            buildString {
                append(if (followUpTitle.isNotBlank()) followUpTitle else "Scheduled follow-up")
                if (leadName.isNotBlank()) append(" for $leadName")
                if (followUpType.isNotBlank()) append(" ($followUpType)")
                append(" at $formattedTime")
            }

        val notification =
            NotificationCompat.Builder(context, LeadFollowUpNotificationScheduler.CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("CRM Follow-up Reminder")
                .setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .build()

        runCatching {
            manager.notify(followUpId.hashCode(), notification)
        }
    }
}
