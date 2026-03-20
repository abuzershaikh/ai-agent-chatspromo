package com.message.bulksend.bulksend

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class AutonomousCampaignAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val campaignId = intent.getStringExtra(AutonomousCampaignExecutionService.EXTRA_CAMPAIGN_ID)
        if (campaignId.isNullOrBlank()) {
            Log.e(TAG, "Missing campaign id in autonomous alarm")
            return
        }

        val source = intent.getStringExtra(AutonomousCampaignExecutionService.EXTRA_TRIGGER_SOURCE)
            .orEmpty()
            .ifBlank { AutonomousCampaignExecutionService.SOURCE_ALARM_RECEIVER }

        Log.d(TAG, "Alarm received for campaign=$campaignId source=$source")
        AutonomousCampaignExecutionService.startForCampaign(context, campaignId, source)
    }

    companion object {
        private const val TAG = "AutoCampaignAlarmRx"
    }
}
