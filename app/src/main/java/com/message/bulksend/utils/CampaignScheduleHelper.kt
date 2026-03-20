package com.message.bulksend.utils

import android.content.Context
import android.widget.Toast
import com.message.bulksend.contactmanager.Contact
import com.message.bulksend.db.AppDatabase
import com.message.bulksend.db.CampaignData
import com.message.bulksend.db.CampaignType
import com.message.bulksend.db.ScheduleStatus
import com.message.bulksend.db.ScheduledCampaign
import com.message.bulksend.db.toJson
import com.message.bulksend.scheduler.CampaignScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.UUID

object CampaignScheduleHelper {
    
    fun scheduleTextCampaign(
        context: Context,
        campaignName: String,
        message: String,
        contacts: List<Contact>,
        groupId: String?,
        groupName: String?,
        countryCode: String,
        delaySettings: String,
        uniqueIdEnabled: Boolean,
        whatsAppPreference: String,
        scheduledTime: Long,
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        val campaignData = CampaignData(
            campaignName = campaignName,
            countryCode = countryCode,
            selectedGroupId = groupId,
            selectedContacts = contacts,
            delaySettings = delaySettings,
            uniqueIdEnabled = uniqueIdEnabled,
            whatsAppPreference = whatsAppPreference,
            messageText = message
        )
        
        scheduleAnyCampaign(
            context = context,
            campaignType = CampaignType.TEXT,
            campaignData = campaignData,
            scheduledTime = scheduledTime,
            groupName = groupName,
            onSuccess = onSuccess,
            onError = onError
        )
    }
    
    fun scheduleMediaCampaign(
        context: Context,
        campaignName: String,
        captionText: String,
        mediaPath: String?,
        contacts: List<Contact>,
        groupId: String?,
        groupName: String?,
        countryCode: String,
        delaySettings: String,
        uniqueIdEnabled: Boolean,
        whatsAppPreference: String,
        scheduledTime: Long,
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        val campaignData = CampaignData(
            campaignName = campaignName,
            countryCode = countryCode,
            selectedGroupId = groupId,
            selectedContacts = contacts,
            delaySettings = delaySettings,
            uniqueIdEnabled = uniqueIdEnabled,
            whatsAppPreference = whatsAppPreference,
            captionText = captionText,
            mediaPath = mediaPath
        )
        
        scheduleAnyCampaign(
            context = context,
            campaignType = CampaignType.MEDIA,
            campaignData = campaignData,
            scheduledTime = scheduledTime,
            groupName = groupName,
            onSuccess = onSuccess,
            onError = onError
        )
    }
    
    fun scheduleTextMediaCampaign(
        context: Context,
        campaignName: String,
        messageText: String,
        mediaPath: String?,
        sendOrder: String,
        contacts: List<Contact>,
        groupId: String?,
        groupName: String?,
        countryCode: String,
        delaySettings: String,
        uniqueIdEnabled: Boolean,
        whatsAppPreference: String,
        scheduledTime: Long,
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        val campaignData = CampaignData(
            campaignName = campaignName,
            countryCode = countryCode,
            selectedGroupId = groupId,
            selectedContacts = contacts,
            delaySettings = delaySettings,
            uniqueIdEnabled = uniqueIdEnabled,
            whatsAppPreference = whatsAppPreference,
            messageText = messageText,
            mediaPath = mediaPath,
            sendOrder = sendOrder
        )
        
        scheduleAnyCampaign(
            context = context,
            campaignType = CampaignType.TEXT_AND_MEDIA,
            campaignData = campaignData,
            scheduledTime = scheduledTime,
            groupName = groupName,
            onSuccess = onSuccess,
            onError = onError
        )
    }
    
    fun scheduleSheetCampaign(
        context: Context,
        campaignName: String,
        sheetUrl: String?,
        sheetFileName: String?,
        sheetDataJson: String?,
        contactCount: Int,
        templateMessage: String,
        countryCode: String,
        delaySettings: String,
        uniqueIdEnabled: Boolean,
        whatsAppPreference: String,
        scheduledTime: Long,
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        val campaignData = CampaignData(
            campaignName = campaignName,
            countryCode = countryCode,
            delaySettings = delaySettings,
            uniqueIdEnabled = uniqueIdEnabled,
            whatsAppPreference = whatsAppPreference,
            sheetUrl = sheetUrl,
            sheetFileName = sheetFileName,
            sheetDataJson = sheetDataJson,
            templateMessage = templateMessage
        )
        
        scheduleAnyCampaign(
            context = context,
            campaignType = CampaignType.SHEET,
            campaignData = campaignData,
            scheduledTime = scheduledTime,
            groupName = null,
            contactCount = contactCount,
            onSuccess = onSuccess,
            onError = onError
        )
    }
    
    private fun scheduleAnyCampaign(
        context: Context,
        campaignType: CampaignType,
        campaignData: CampaignData,
        scheduledTime: Long,
        groupName: String?,
        contactCount: Int = campaignData.selectedContacts?.size ?: 0,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        // Validate scheduled time
        if (scheduledTime <= System.currentTimeMillis()) {
            onError("Please select a future date and time")
            return
        }
        
        // Check battery optimization before scheduling
        if (!BatteryOptimizationHelper.isIgnoringBatteryOptimizations(context)) {
            CoroutineScope(Dispatchers.Main).launch {
                BatteryOptimizationHelper.showBatteryOptimizationDialog(context)
            }
        }
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val database = AppDatabase.getInstance(context)
                val scheduledCampaignDao = database.scheduledCampaignDao()
                
                val scheduledCampaign = ScheduledCampaign(
                    id = UUID.randomUUID().toString(),
                    campaignType = campaignType.name,
                    campaignName = campaignData.campaignName,
                    scheduledTime = scheduledTime,
                    status = ScheduleStatus.SCHEDULED.name,
                    campaignDataJson = campaignData.toJson(),
                    contactCount = contactCount,
                    groupId = campaignData.selectedGroupId,
                    groupName = groupName
                )
                
                // Save to database
                scheduledCampaignDao.insertScheduledCampaign(scheduledCampaign)
                
                // Schedule with AlarmManager
                val scheduler = CampaignScheduler(context)
                scheduler.scheduleExecution(scheduledCampaign)
                
                // Show success message on main thread
                CoroutineScope(Dispatchers.Main).launch {
                    val batteryOptimized = BatteryOptimizationHelper.isIgnoringBatteryOptimizations(context)
                    val message = if (batteryOptimized) {
                        "Campaign scheduled successfully! ⏰"
                    } else {
                        "Campaign scheduled! ⏰\nTip: Disable battery optimization for reliable execution"
                    }
                    
                    Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                    onSuccess()
                }
                
            } catch (e: Exception) {
                CoroutineScope(Dispatchers.Main).launch {
                    onError("Failed to schedule campaign: ${e.message}")
                }
            }
        }
    }
    
    fun cancelScheduledCampaign(
        context: Context,
        campaignId: String,
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val database = AppDatabase.getInstance(context)
                val scheduledCampaignDao = database.scheduledCampaignDao()
                
                // Update status to cancelled
                scheduledCampaignDao.updateCampaignStatus(campaignId, ScheduleStatus.CANCELLED.name)
                
                // Cancel the alarm
                val scheduler = CampaignScheduler(context)
                scheduler.cancelScheduledExecution(campaignId)
                
                CoroutineScope(Dispatchers.Main).launch {
                    Toast.makeText(context, "Campaign cancelled successfully", Toast.LENGTH_SHORT).show()
                    onSuccess()
                }
                
            } catch (e: Exception) {
                CoroutineScope(Dispatchers.Main).launch {
                    onError("Failed to cancel campaign: ${e.message}")
                }
            }
        }
    }
    
    fun rescheduleScheduledCampaign(
        context: Context,
        campaignId: String,
        newScheduledTime: Long,
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        // Validate new scheduled time
        if (newScheduledTime <= System.currentTimeMillis()) {
            onError("Please select a future date and time")
            return
        }
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val database = AppDatabase.getInstance(context)
                val scheduledCampaignDao = database.scheduledCampaignDao()
                
                // Get the campaign to reschedule
                val campaign = scheduledCampaignDao.getCampaignById(campaignId)
                if (campaign != null) {
                    // Update scheduled time
                    scheduledCampaignDao.updateScheduledTime(campaignId, newScheduledTime)
                    
                    // Reschedule with AlarmManager
                    val updatedCampaign = campaign.copy(scheduledTime = newScheduledTime)
                    val scheduler = CampaignScheduler(context)
                    scheduler.rescheduleExecution(updatedCampaign)
                    
                    CoroutineScope(Dispatchers.Main).launch {
                        Toast.makeText(context, "Campaign rescheduled successfully! ⏰", Toast.LENGTH_SHORT).show()
                        onSuccess()
                    }
                } else {
                    CoroutineScope(Dispatchers.Main).launch {
                        onError("Campaign not found")
                    }
                }
                
            } catch (e: Exception) {
                CoroutineScope(Dispatchers.Main).launch {
                    onError("Failed to reschedule campaign: ${e.message}")
                }
            }
        }
    }
}