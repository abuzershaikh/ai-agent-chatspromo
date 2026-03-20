package com.message.bulksend.utils

import android.content.Context
import android.util.Log
import com.message.bulksend.db.AppDatabase
import com.message.bulksend.db.ScheduleStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object CampaignStatusUpdater {
    
    /**
     * Update campaign statuses based on current time
     * Campaigns that have passed their scheduled time should be marked as completed
     */
    fun updateCampaignStatuses(context: Context, onComplete: () -> Unit = {}) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val database = AppDatabase.getInstance(context)
                val dao = database.scheduledCampaignDao()
                val currentTime = System.currentTimeMillis()
                
                // Get campaigns that are scheduled but past their time
                val overdueCampaigns = dao.getOverdueCampaigns(currentTime)
                
                Log.d("CampaignStatusUpdater", "Found ${overdueCampaigns.size} overdue campaigns")
                
                // Update each overdue campaign to completed status
                overdueCampaigns.forEach { campaign ->
                    // Check if campaign was scheduled more than 5 minutes ago
                    val timePassed = currentTime - campaign.scheduledTime
                    val fiveMinutes = 5 * 60 * 1000L
                    
                    if (timePassed > fiveMinutes) {
                        dao.updateCampaignStatus(campaign.id, ScheduleStatus.COMPLETED.name)
                        Log.d("CampaignStatusUpdater", "Marked campaign '${campaign.campaignName}' as completed")
                    }
                }
                
                // Call completion callback on main thread
                CoroutineScope(Dispatchers.Main).launch {
                    onComplete()
                }
                
            } catch (e: Exception) {
                Log.e("CampaignStatusUpdater", "Error updating campaign statuses: ${e.message}")
                CoroutineScope(Dispatchers.Main).launch {
                    onComplete()
                }
            }
        }
    }
    
    /**
     * Check if a campaign should be marked as finished based on time
     */
    fun shouldMarkAsFinished(scheduledTime: Long, currentStatus: String): Boolean {
        val currentTime = System.currentTimeMillis()
        val timePassed = currentTime - scheduledTime
        val fiveMinutes = 5 * 60 * 1000L
        
        return currentStatus == ScheduleStatus.SCHEDULED.name && timePassed > fiveMinutes
    }
    
    /**
     * Get display status for a campaign (handles automatic "Finished" status)
     */
    fun getDisplayStatus(scheduledTime: Long, currentStatus: String): String {
        return if (shouldMarkAsFinished(scheduledTime, currentStatus)) {
            "FINISHED"
        } else {
            currentStatus
        }
    }
    
    /**
     * Get display status with proper formatting
     */
    fun getFormattedDisplayStatus(scheduledTime: Long, currentStatus: String): Pair<String, Boolean> {
        val displayStatus = getDisplayStatus(scheduledTime, currentStatus)
        val isFinished = displayStatus == "FINISHED"
        
        val formattedStatus = when (displayStatus) {
            "SCHEDULED" -> "Scheduled"
            "RUNNING" -> "Running"
            "COMPLETED" -> "Completed"
            "CANCELLED" -> "Cancelled"
            "FAILED" -> "Failed"
            "FINISHED" -> "Finished"
            else -> displayStatus
        }
        
        return Pair(formattedStatus, isFinished)
    }
}