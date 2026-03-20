package com.message.bulksend.leadmanager.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class LeadFollowUpRescheduleReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action.orEmpty()
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_MY_PACKAGE_REPLACED) {
            return
        }

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                LeadFollowUpNotificationScheduler(context.applicationContext)
                    .rescheduleAllFromDatabase()
            } finally {
                pendingResult.finish()
            }
        }
    }
}
