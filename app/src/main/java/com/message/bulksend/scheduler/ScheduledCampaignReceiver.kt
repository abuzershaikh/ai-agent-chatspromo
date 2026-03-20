package com.message.bulksend.scheduler

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.message.bulksend.db.AppDatabase
import com.message.bulksend.db.ScheduleStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ScheduledCampaignReceiver : BroadcastReceiver() {
    
    override fun onReceive(context: Context, intent: Intent) {
        val campaignId = intent.getStringExtra("SCHEDULED_CAMPAIGN_ID")
        val campaignType = intent.getStringExtra("CAMPAIGN_TYPE")
        
        Log.d("ScheduledCampaignReceiver", "Received alarm for campaign: $campaignId, type: $campaignType")
        
        if (campaignId != null) {
            // Use coroutine to handle database operations
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val database = AppDatabase.getInstance(context)
                    val scheduledCampaignDao = database.scheduledCampaignDao()
                    
                    val scheduledCampaign = scheduledCampaignDao.getCampaignById(campaignId)
                    
                    if (scheduledCampaign != null && scheduledCampaign.status == ScheduleStatus.SCHEDULED.name) {
                        Log.d("ScheduledCampaignReceiver", "Executing scheduled campaign: ${scheduledCampaign.campaignName}")
                        
                        // Update status to RUNNING
                        scheduledCampaignDao.updateCampaignStatus(campaignId, ScheduleStatus.RUNNING.name)
                        
                        // Start the campaign execution service
                        val executionIntent = Intent(context, CampaignExecutionService::class.java).apply {
                            putExtra("SCHEDULED_CAMPAIGN_ID", campaignId)
                        }
                        
                        context.startForegroundService(executionIntent)
                        
                    } else {
                        Log.w("ScheduledCampaignReceiver", "Campaign not found or not in SCHEDULED status: $campaignId")
                    }
                } catch (e: Exception) {
                    Log.e("ScheduledCampaignReceiver", "Error executing scheduled campaign: ${e.message}", e)
                }
            }
        }
    }
}