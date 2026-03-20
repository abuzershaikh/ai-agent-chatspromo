package com.message.bulksend.autorespond.statusscheduled

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.message.bulksend.autorespond.database.MessageDatabase
import com.message.bulksend.autorespond.statusscheduled.database.StatusBatchRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class StatusBatchRescheduleReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val database = MessageDatabase.getDatabase(context.applicationContext)
                val repository = StatusBatchRepository(database.statusBatchDao())
                val manager = StatusBatchManager(context.applicationContext, repository)
                val restored = manager.restoreScheduledBatches()
                Log.d(TAG, "Reschedule receiver restored $restored batches for action=${intent?.action}")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to restore status schedules: ${e.message}", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val TAG = "StatusBatchRescheduleRx"
    }
}
