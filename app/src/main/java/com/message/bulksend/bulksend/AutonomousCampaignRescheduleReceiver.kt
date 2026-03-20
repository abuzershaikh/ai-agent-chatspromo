package com.message.bulksend.bulksend

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.message.bulksend.db.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AutonomousCampaignRescheduleReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val appContext = context.applicationContext
                val database = AppDatabase.getInstance(appContext)
                val campaignDao = database.campaignDao()
                val queueDao = database.autonomousSendQueueDao()
                val scheduler = AutonomousCampaignScheduler(appContext)
                val now = System.currentTimeMillis()

                campaignDao.getAllCampaigns()
                    .asSequence()
                    .filter { it.campaignType == AUTONOMOUS_CAMPAIGN_TYPE }
                    .filter { !it.isStopped }
                    .forEach { campaign ->
                        val nextSendAt = queueDao.getNextSendTime(campaign.id)
                        if (nextSendAt == null) {
                            AutonomousCampaignConfigStore.clearConfig(appContext, campaign.id)
                            return@forEach
                        }

                        if (nextSendAt <= now + 1_000L) {
                            AutonomousCampaignExecutionService.startForCampaign(
                                context = appContext,
                                campaignId = campaign.id,
                                source = AutonomousCampaignExecutionService.SOURCE_RESTORE_RECOVERY
                            )
                        } else {
                            scheduler.scheduleNextExecution(campaign.id, nextSendAt)
                        }
                    }

                Log.d(TAG, "Autonomous campaign alarms restored for action=${intent?.action}")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to restore autonomous campaign alarms: ${e.message}", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val TAG = "AutoCampaignReschedRx"
        private const val AUTONOMOUS_CAMPAIGN_TYPE = "BULKTEXT_AUTONOMOUS"
    }
}
