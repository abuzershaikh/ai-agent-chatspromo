package com.message.bulksend.autorespond.statusscheduled

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class StatusBatchAlarmReceiver : BroadcastReceiver() {
    
    companion object {
        const val EXTRA_BATCH_ID = "batch_id"
        private const val TAG = "StatusBatchAlarmReceiver"
    }
    
    override fun onReceive(context: Context, intent: Intent) {
        Log.d(
            TAG,
            "onReceive action=${intent.action} extras=${intent.extras?.keySet()?.joinToString()} at=${System.currentTimeMillis()}"
        )
        val batchId = intent.getLongExtra(EXTRA_BATCH_ID, -1L)
        if (batchId == -1L) {
            Log.e(TAG, "Invalid batch ID received action=${intent.action}")
            return
        }

        val source = intent
            .getStringExtra(StatusBatchExecutionService.EXTRA_TRIGGER_SOURCE)
            .orEmpty()
            .ifBlank {
                if (intent.action == StatusBatchExecutionService.ACTION_AUTO_RESUME_BATCH) {
                    StatusBatchExecutionService.SOURCE_AUTO_RESUME
                } else {
                    StatusBatchExecutionService.SOURCE_ALARM_RECEIVER
                }
            }
        
        Log.d(TAG, "Alarm triggered for batch=$batchId source=$source. Starting execution service.")
        try {
            StatusBatchExecutionService.startForBatch(
                context = context,
                batchId = batchId,
                source = source
            )
            Log.d(TAG, "Execution service start requested for batch=$batchId")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start execution service for batch=$batchId: ${e.message}", e)
        }
    }
}
