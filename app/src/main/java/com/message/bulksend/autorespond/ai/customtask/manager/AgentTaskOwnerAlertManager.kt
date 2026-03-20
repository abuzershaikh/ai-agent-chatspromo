package com.message.bulksend.autorespond.ai.customtask.manager

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import com.message.bulksend.aiagent.tools.globalsender.GlobalSenderManager
import com.message.bulksend.aiagent.tools.reverseai.ReverseAIManager
import com.message.bulksend.autorespond.ai.customtask.models.AgentTask
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Sends owner alerts when step-flow repeat counter crosses configured threshold.
 */
class AgentTaskOwnerAlertManager(context: Context) {

    private val appContext = context.applicationContext
    private val sender = GlobalSenderManager(appContext)
    private val reverseAIManager = ReverseAIManager(appContext)

    suspend fun alertOwnerIfPossible(
        customerPhone: String,
        task: AgentTask,
        repeatCount: Int,
        limit: Int,
        ownerPhoneOverride: String
    ) = withContext(Dispatchers.IO) {
        showLocalNotification(
            title = "Step Flow repeat limit reached",
            body = "Step ${task.stepOrder} repeated $repeatCount/$limit for $customerPhone"
        )

        val ownerPhone =
            ownerPhoneOverride.trim().ifBlank { reverseAIManager.ownerPhoneNumber.trim() }
        if (ownerPhone.isBlank()) return@withContext

        val message =
            buildString {
                append("Task Alert\n")
                append("Step flow repeat limit reached.\n")
                append("Customer: $customerPhone\n")
                append("Step: ${task.stepOrder} - ${task.title}\n")
                append("Repeat Count: $repeatCount/$limit\n")
                append("Please review this conversation.")
            }

        runCatching {
            sender.sendTextViaAccessibility(
                phoneNumber = ownerPhone,
                message = message
            )
        }
    }

    private fun showLocalNotification(title: String, body: String) {
        val manager =
            appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel =
                NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_HIGH
                )
            manager.createNotificationChannel(channel)
        }
        val notification =
            NotificationCompat.Builder(appContext, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .build()
        manager.notify((System.currentTimeMillis() % Int.MAX_VALUE).toInt(), notification)
    }

    private companion object {
        const val CHANNEL_ID = "agent_task_owner_alert_channel"
        const val CHANNEL_NAME = "Agent Task Alerts"
    }
}
