package com.message.bulksend.utils

import android.content.Context
import android.widget.Toast
import androidx.compose.runtime.MutableState


object CampaignStopHandler {

    fun handleStopRequest(
        context: Context,
        campaignId: String,
        showStopConfirmation: MutableState<Boolean>
    ) {
        showStopConfirmation.value = true
    }

    fun confirmStop(
        context: Context,
        campaignId: String,
        showStopConfirmation: MutableState<Boolean>
    ) {
        CampaignProgressManager.setStopFlag(context, campaignId, true)
        Toast.makeText(context, "Stopping campaign...", Toast.LENGTH_SHORT).show()
        showStopConfirmation.value = false
    }

    fun checkShouldStop(context: Context, campaignId: String): Boolean {
        return CampaignProgressManager.shouldStop(context, campaignId)
    }

    fun saveProgressDuringCampaign(
        context: Context,
        campaignId: String,
        campaignName: String,
        campaignType: String,
        message: String,
        totalContacts: Int,
        sentCount: Int,
        failedCount: Int,
        remainingCount: Int,
        currentIndex: Int,
        isRunning: Boolean = true,
        errorMessage: String? = null
    ) {
        val progress = CampaignProgress(
            campaignId = campaignId,
            campaignName = campaignName,
            campaignType = campaignType,
            message = message,
            totalContacts = totalContacts,
            sentCount = sentCount,
            failedCount = failedCount,
            remainingCount = remainingCount,
            currentIndex = currentIndex,
            isRunning = isRunning,
            isStopped = !isRunning,
            contactStatuses = emptyList(), // CampaignStopHandler doesn't manage contact statuses
            errorMessage = errorMessage
        )
        CampaignProgressManager.saveProgress(context, progress)
    }

    fun initializeCampaign(context: Context, campaignId: String) {
        // Clear any previous stop flag
        CampaignProgressManager.setStopFlag(context, campaignId, false)
    }

    fun finalizeCampaign(
        context: Context,
        campaignId: String,
        campaignName: String,
        campaignType: String,
        message: String,
        totalContacts: Int,
        sentCount: Int,
        failedCount: Int,
        remainingCount: Int,
        wasStopped: Boolean
    ) {
        val finalProgress = CampaignProgress(
            campaignId = campaignId,
            campaignName = campaignName,
            campaignType = campaignType,
            message = message,
            totalContacts = totalContacts,
            sentCount = sentCount,
            failedCount = failedCount,
            remainingCount = remainingCount,
            currentIndex = totalContacts,
            isRunning = false,
            isStopped = wasStopped,
            contactStatuses = emptyList() // CampaignStopHandler doesn't manage contact statuses
        )
        CampaignProgressManager.saveProgress(context, finalProgress)
    }
}