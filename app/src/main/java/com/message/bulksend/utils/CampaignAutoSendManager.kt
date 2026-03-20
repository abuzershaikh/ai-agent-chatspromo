package com.message.bulksend.utils

import android.util.Log
import com.message.bulksend.bulksend.CampaignState
import com.message.bulksend.bulksend.WhatsAppAutoSendService
import com.message.bulksend.db.Campaign

/**
 * Campaign auto-send service ko manage karne ke liye utility class.
 * Yeh class campaign ki state ke hisab se auto-send service ko enable/disable karti hai.
 */
object CampaignAutoSendManager {

    /**
     * Campaign launch hone par auto-send service ko enable karta hai.
     * @param campaign Campaign object jiska status check karna hai
     */
    fun onCampaignLaunched(campaign: Campaign) {
        if (campaign.isRunning && !campaign.isStopped) {
            enableAutoSendService()
            Log.i("CampaignAutoSendManager", "Campaign '${campaign.campaignName}' launch hua, auto-send service enable kar di.")
        }
    }

    /**
     * Campaign status update hone par auto-send service ko manage karta hai.
     * @param campaign Updated campaign object
     */
    fun onCampaignStatusUpdated(campaign: Campaign) {
        if (campaign.isRunning && !campaign.isStopped) {
            // Campaign running state me hai, service enable rakho
            if (!CampaignState.isAutoSendEnabled) {
                enableAutoSendService()
                Log.i("CampaignAutoSendManager", "Campaign '${campaign.campaignName}' running hai, auto-send service enable kar di.")
            }
        } else {
            // Campaign stopped ya complete ho gaya, service disable karo
            disableAutoSendService()
            Log.i("CampaignAutoSendManager", "Campaign '${campaign.campaignName}' stopped/completed, auto-send service disable kar di.")
        }
    }

    /**
     * Campaign complete hone par auto-send service ko disable karta hai.
     * @param campaign Completed campaign object
     */
    fun onCampaignCompleted(campaign: Campaign) {
        disableAutoSendService()
        Log.i("CampaignAutoSendManager", "Campaign '${campaign.campaignName}' complete hua, auto-send service disable kar di.")
    }

    /**
     * Campaign stop hone par auto-send service ko disable karta hai.
     * @param campaign Stopped campaign object
     */
    fun onCampaignStopped(campaign: Campaign) {
        disableAutoSendService()
        Log.i("CampaignAutoSendManager", "Campaign '${campaign.campaignName}' stop hua, auto-send service disable kar di.")
    }

    /**
     * Auto-send service ko enable karta hai.
     */
    private fun enableAutoSendService() {
        CampaignState.isAutoSendEnabled = true
        WhatsAppAutoSendService.activateService()
        Log.d("CampaignAutoSendManager", "✅ Auto-send service ENABLED (both flags set).")
    }

    /**
     * Auto-send service ko disable karta hai.
     */
    private fun disableAutoSendService() {
        CampaignState.isAutoSendEnabled = false
        WhatsAppAutoSendService.deactivateService()
        Log.d("CampaignAutoSendManager", "❌ Auto-send service DISABLED (both flags cleared).")
    }

    /**
     * Current auto-send service status check karta hai.
     * @return true agar service enabled hai, false otherwise
     */
    fun isAutoSendServiceEnabled(): Boolean {
        return CampaignState.isAutoSendEnabled
    }
}
    /*
*
     * Debug function - current state ko log karta hai
     */
    fun logCurrentState() {
        Log.d("CampaignAutoSendManager", "Current auto-send service state: ${if (CampaignState.isAutoSendEnabled) "ENABLED" else "DISABLED"}")
    }
