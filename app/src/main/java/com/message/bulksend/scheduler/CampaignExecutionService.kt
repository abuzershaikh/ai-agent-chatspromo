package com.message.bulksend.scheduler

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import android.app.KeyguardManager
import androidx.core.app.NotificationCompat
import com.message.bulksend.R
import com.message.bulksend.contactmanager.Contact
import com.message.bulksend.data.ContactStatus
import com.message.bulksend.db.AppDatabase
import com.message.bulksend.db.Campaign
import com.message.bulksend.db.CampaignData
import com.message.bulksend.db.CampaignType
import com.message.bulksend.db.ScheduleStatus
import com.message.bulksend.db.toCampaignData
import com.message.bulksend.utils.CampaignAutoSendManager
import com.message.bulksend.utils.DozeModeHelper
import com.message.bulksend.utils.isAccessibilityServiceEnabled
import com.message.bulksend.utils.isPackageInstalled
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URLEncoder
import java.util.UUID
import kotlin.random.Random

class CampaignExecutionService : Service() {
    
    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())
    private val notificationChannelId = "scheduled_campaign_channel"
    private val notificationId = 1001
    
    // Wake lock to prevent device from sleeping during campaign execution
    private var wakeLock: PowerManager.WakeLock? = null
    
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        acquireWakeLock()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val campaignId = intent?.getStringExtra("SCHEDULED_CAMPAIGN_ID")
        
        if (campaignId != null) {
            startForeground(notificationId, createNotification("Preparing scheduled campaign..."))
            
            serviceScope.launch {
                try {
                    executeScheduledCampaign(campaignId)
                } finally {
                    releaseWakeLock()
                    stopSelf()
                }
            }
        } else {
            releaseWakeLock()
            stopSelf()
        }
        
        return START_NOT_STICKY
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onDestroy() {
        super.onDestroy()
        releaseWakeLock()
    }
    
    private fun acquireWakeLock() {
        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            // Use SCREEN_BRIGHT_WAKE_LOCK | ACQUIRE_CAUSES_WAKEUP to ensure screen turns on
            // This is deprecated in API 17 but still necessary for turning screen on from background service
            // without full activity launch in some cases, though turnScreenOn in Activity is preferred for newer APIs.
            // Since we are in a Service, we use this or start an activity with turnScreenOn flags.
            // Combining with ACQUIRE_CAUSES_WAKEUP forces the screen on.
            val wakeLockLevel = PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP
            
            wakeLock = powerManager.newWakeLock(
                wakeLockLevel,
                "BulkSend:ScheduledCampaignExecution"
            ).apply {
                acquire(30 * 60 * 1000L) // 30 minutes max
            }
            Log.d("CampaignExecutionService", "Wake lock (SCREEN_BRIGHT|ACQUIRE_CAUSES_WAKEUP) acquired")
            
            // Also attempt to disable keyguard to show UI over lock screen if possible
            val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
            val keyguardLock = keyguardManager.newKeyguardLock("BulkSend:KeyguardLock")
            keyguardLock.disableKeyguard()
            Log.d("CampaignExecutionService", "Keyguard disable requested")
            
        } catch (e: Exception) {
            Log.e("CampaignExecutionService", "Failed to acquire wake lock or disable keyguard: ${e.message}")
        }
    }
    
    private fun releaseWakeLock() {
        try {
            wakeLock?.let {
                if (it.isHeld) {
                    it.release()
                    Log.d("CampaignExecutionService", "Wake lock released")
                }
            }
            wakeLock = null
        } catch (e: Exception) {
            Log.e("CampaignExecutionService", "Failed to release wake lock: ${e.message}")
        }
    }
    
    private suspend fun executeScheduledCampaign(campaignId: String) {
        try {
            // Log device power state for debugging
            DozeModeHelper.logDevicePowerState(this, "CampaignExecutionService")
            
            val database = AppDatabase.getInstance(this)
            val scheduledCampaignDao = database.scheduledCampaignDao()
            val campaignDao = database.campaignDao()
            
            val scheduledCampaign = scheduledCampaignDao.getCampaignById(campaignId)
            if (scheduledCampaign == null) {
                Log.e("CampaignExecutionService", "Scheduled campaign not found: $campaignId")
                return
            }
            
            Log.d("CampaignExecutionService", "Executing campaign: ${scheduledCampaign.campaignName}")
            updateNotification("Executing: ${scheduledCampaign.campaignName}")
            
            val campaignData = scheduledCampaign.campaignDataJson.toCampaignData()
            if (campaignData == null) {
                Log.e("CampaignExecutionService", "Invalid campaign data for: $campaignId")
                scheduledCampaignDao.updateCampaignStatus(campaignId, ScheduleStatus.FAILED.name)
                return
            }
            
            // Check prerequisites
            if (!isAccessibilityServiceEnabled(this)) {
                Log.e("CampaignExecutionService", "Accessibility service not enabled")
                scheduledCampaignDao.updateCampaignStatus(campaignId, ScheduleStatus.FAILED.name)
                updateNotification("Failed: Accessibility service not enabled")
                delay(3000)
                return
            }
            
            val packageName = when (campaignData.whatsAppPreference) {
                "WhatsApp" -> "com.whatsapp"
                "WhatsApp Business" -> "com.whatsapp.w4b"
                else -> "com.whatsapp"
            }
            
            // Auto-detect available WhatsApp if the preferred one is not installed
            val finalPackageName = if (!isPackageInstalled(this, packageName)) {
                when {
                    isPackageInstalled(this, "com.whatsapp.w4b") -> {
                        Log.d("CampaignExecutionService", "Preferred WhatsApp not found, using WhatsApp Business")
                        "com.whatsapp.w4b"
                    }
                    isPackageInstalled(this, "com.whatsapp") -> {
                        Log.d("CampaignExecutionService", "Preferred WhatsApp not found, using regular WhatsApp")
                        "com.whatsapp"
                    }
                    else -> {
                        Log.e("CampaignExecutionService", "No WhatsApp app found")
                        scheduledCampaignDao.updateCampaignStatus(campaignId, ScheduleStatus.FAILED.name)
                        updateNotification("Failed: No WhatsApp app installed")
                        delay(3000)
                        return
                    }
                }
            } else {
                packageName
            }
            
            Log.d("CampaignExecutionService", "Using WhatsApp package: $finalPackageName")
            
            // Execute campaign based on type
            when (scheduledCampaign.campaignType) {
                CampaignType.TEXT.name -> executeTextCampaign(campaignData, scheduledCampaign, campaignDao)
                CampaignType.MEDIA.name -> executeMediaCampaign(campaignData, scheduledCampaign, campaignDao)
                CampaignType.TEXT_AND_MEDIA.name -> executeTextMediaCampaign(campaignData, scheduledCampaign, campaignDao)
                CampaignType.SHEET.name -> executeSheetCampaign(campaignData, scheduledCampaign, campaignDao)
                else -> {
                    Log.e("CampaignExecutionService", "Unknown campaign type: ${scheduledCampaign.campaignType}")
                    scheduledCampaignDao.updateCampaignStatus(campaignId, ScheduleStatus.FAILED.name)
                }
            }
            
        } catch (e: Exception) {
            Log.e("CampaignExecutionService", "Error executing campaign: ${e.message}", e)
            val database = AppDatabase.getInstance(this)
            database.scheduledCampaignDao().updateCampaignStatus(campaignId, ScheduleStatus.FAILED.name)
            updateNotification("Campaign execution failed")
            delay(3000)
        }
    }
    
    private suspend fun executeTextCampaign(
        campaignData: CampaignData,
        scheduledCampaign: com.message.bulksend.db.ScheduledCampaign,
        campaignDao: com.message.bulksend.db.CampaignDao
    ) {
        updateNotification("Executing text campaign: ${campaignData.campaignName}")
        
        val contacts = getContactsFromCampaignData(campaignData)
        if (contacts.isEmpty()) {
            Log.e("CampaignExecutionService", "No contacts found for campaign")
            val database = AppDatabase.getInstance(this)
            database.scheduledCampaignDao().updateCampaignStatus(scheduledCampaign.id, ScheduleStatus.FAILED.name)
            return
        }
        
        val campaign = Campaign(
            id = UUID.randomUUID().toString(),
            groupId = campaignData.selectedGroupId ?: "SCHEDULED",
            campaignName = campaignData.campaignName,
            message = campaignData.messageText ?: "",
            timestamp = System.currentTimeMillis(),
            totalContacts = contacts.size,
            contactStatuses = contacts.map { ContactStatus(it.number, "pending") },
            isStopped = false,
            isRunning = true,
            campaignType = "BULKTEXT",
            countryCode = campaignData.countryCode
        )
        
        campaignDao.upsertCampaign(campaign)
        CampaignAutoSendManager.onCampaignLaunched(campaign)
        
        // Update scheduled campaign status to RUNNING
        val database = AppDatabase.getInstance(this)
        database.scheduledCampaignDao().updateCampaignStatus(scheduledCampaign.id, ScheduleStatus.RUNNING.name)
        
        try {
            // Execute the campaign logic and wait for completion
            executeCampaignLogic(campaign, campaignData, contacts)
            
            // Mark campaign as completed in the regular campaign table
            val finishedCampaign = campaign.copy(isRunning = false, isStopped = false)
            campaignDao.upsertCampaign(finishedCampaign)
            CampaignAutoSendManager.onCampaignCompleted(finishedCampaign)
            
            // Only mark scheduled campaign as completed after everything is done
            database.scheduledCampaignDao().updateCampaignStatus(scheduledCampaign.id, ScheduleStatus.COMPLETED.name)
            
        } catch (e: Exception) {
            Log.e("CampaignExecutionService", "Text campaign failed: ${e.message}")
            database.scheduledCampaignDao().updateCampaignStatus(scheduledCampaign.id, ScheduleStatus.FAILED.name)
        }
    }
    
    private suspend fun executeMediaCampaign(
        campaignData: CampaignData,
        scheduledCampaign: com.message.bulksend.db.ScheduledCampaign,
        campaignDao: com.message.bulksend.db.CampaignDao
    ) {
        updateNotification("Executing media campaign: ${campaignData.campaignName}")
        
        val contacts = getContactsFromCampaignData(campaignData)
        if (contacts.isEmpty()) {
            val database = AppDatabase.getInstance(this)
            database.scheduledCampaignDao().updateCampaignStatus(scheduledCampaign.id, ScheduleStatus.FAILED.name)
            return
        }
        
        val campaign = Campaign(
            id = UUID.randomUUID().toString(),
            groupId = campaignData.selectedGroupId ?: "SCHEDULED",
            campaignName = campaignData.campaignName,
            message = campaignData.captionText ?: "",
            timestamp = System.currentTimeMillis(),
            totalContacts = contacts.size,
            contactStatuses = contacts.map { ContactStatus(it.number, "pending") },
            isStopped = false,
            isRunning = true,
            campaignType = "BULKSEND",
            countryCode = campaignData.countryCode,
            mediaPath = campaignData.mediaPath
        )
        
        campaignDao.upsertCampaign(campaign)
        CampaignAutoSendManager.onCampaignLaunched(campaign)
        
        // Update scheduled campaign status to RUNNING
        val database = AppDatabase.getInstance(this)
        database.scheduledCampaignDao().updateCampaignStatus(scheduledCampaign.id, ScheduleStatus.RUNNING.name)
        
        try {
            // Execute the campaign logic and wait for completion
            executeCampaignLogic(campaign, campaignData, contacts)
            
            // Mark campaign as completed in the regular campaign table
            val finishedCampaign = campaign.copy(isRunning = false, isStopped = false)
            campaignDao.upsertCampaign(finishedCampaign)
            CampaignAutoSendManager.onCampaignCompleted(finishedCampaign)
            
            // Only mark scheduled campaign as completed after everything is done
            database.scheduledCampaignDao().updateCampaignStatus(scheduledCampaign.id, ScheduleStatus.COMPLETED.name)
            
        } catch (e: Exception) {
            Log.e("CampaignExecutionService", "Media campaign failed: ${e.message}")
            database.scheduledCampaignDao().updateCampaignStatus(scheduledCampaign.id, ScheduleStatus.FAILED.name)
        }
    }
    
    private suspend fun executeTextMediaCampaign(
        campaignData: CampaignData,
        scheduledCampaign: com.message.bulksend.db.ScheduledCampaign,
        campaignDao: com.message.bulksend.db.CampaignDao
    ) {
        updateNotification("Executing text+media campaign: ${campaignData.campaignName}")
        
        val contacts = getContactsFromCampaignData(campaignData)
        if (contacts.isEmpty()) {
            val database = AppDatabase.getInstance(this)
            database.scheduledCampaignDao().updateCampaignStatus(scheduledCampaign.id, ScheduleStatus.FAILED.name)
            return
        }
        
        val campaign = Campaign(
            id = UUID.randomUUID().toString(),
            groupId = campaignData.selectedGroupId ?: "SCHEDULED",
            campaignName = campaignData.campaignName,
            message = campaignData.messageText ?: "",
            timestamp = System.currentTimeMillis(),
            totalContacts = contacts.size,
            contactStatuses = contacts.map { ContactStatus(it.number, "pending") },
            isStopped = false,
            isRunning = true,
            campaignType = "TEXTMEDIA",
            countryCode = campaignData.countryCode,
            mediaPath = campaignData.mediaPath
        )
        
        campaignDao.upsertCampaign(campaign)
        CampaignAutoSendManager.onCampaignLaunched(campaign)
        
        // Update scheduled campaign status to RUNNING
        val database = AppDatabase.getInstance(this)
        database.scheduledCampaignDao().updateCampaignStatus(scheduledCampaign.id, ScheduleStatus.RUNNING.name)
        
        try {
            // Execute the campaign logic and wait for completion
            executeCampaignLogic(campaign, campaignData, contacts)
            
            // Mark campaign as completed in the regular campaign table
            val finishedCampaign = campaign.copy(isRunning = false, isStopped = false)
            campaignDao.upsertCampaign(finishedCampaign)
            CampaignAutoSendManager.onCampaignCompleted(finishedCampaign)
            
            // Only mark scheduled campaign as completed after everything is done
            database.scheduledCampaignDao().updateCampaignStatus(scheduledCampaign.id, ScheduleStatus.COMPLETED.name)
            
        } catch (e: Exception) {
            Log.e("CampaignExecutionService", "Text+Media campaign failed: ${e.message}")
            database.scheduledCampaignDao().updateCampaignStatus(scheduledCampaign.id, ScheduleStatus.FAILED.name)
        }
    }
    
    private suspend fun executeSheetCampaign(
        campaignData: CampaignData,
        scheduledCampaign: com.message.bulksend.db.ScheduledCampaign,
        campaignDao: com.message.bulksend.db.CampaignDao
    ) {
        updateNotification("Executing sheet campaign: ${campaignData.campaignName}")
        
        val database = AppDatabase.getInstance(this)
        
        // Parse the saved sheet data JSON
        if (campaignData.sheetDataJson.isNullOrBlank()) {
            Log.e("CampaignExecutionService", "No sheet data found for campaign: ${scheduledCampaign.id}")
            database.scheduledCampaignDao().updateCampaignStatus(scheduledCampaign.id, ScheduleStatus.FAILED.name)
            updateNotification("Failed: No sheet data found")
            delay(3000)
            return
        }
        
        // Parse SheetData from JSON
        val sheetData: SheetData? = try {
            com.google.gson.Gson().fromJson(campaignData.sheetDataJson, SheetData::class.java)
        } catch (e: Exception) {
            Log.e("CampaignExecutionService", "Failed to parse sheet data: ${e.message}")
            null
        }
        
        if (sheetData == null || sheetData.rows.isEmpty()) {
            Log.e("CampaignExecutionService", "Sheet data is empty or invalid")
            database.scheduledCampaignDao().updateCampaignStatus(scheduledCampaign.id, ScheduleStatus.FAILED.name)
            updateNotification("Failed: Empty sheet data")
            delay(3000)
            return
        }
        
        // Generate final messages from sheet data
        val generatedMessages = generateMessagesFromSheetData(
            templateMessage = campaignData.templateMessage ?: "",
            sheetData = sheetData,
            countryCode = campaignData.countryCode
        )
        
        if (generatedMessages.isEmpty()) {
            Log.e("CampaignExecutionService", "No messages generated from sheet")
            database.scheduledCampaignDao().updateCampaignStatus(scheduledCampaign.id, ScheduleStatus.FAILED.name)
            updateNotification("Failed: No valid contacts in sheet")
            delay(3000)
            return
        }
        
        Log.d("CampaignExecutionService", "Generated ${generatedMessages.size} messages from sheet")
        
        // Create Campaign object for tracking
        val campaign = Campaign(
            id = UUID.randomUUID().toString(),
            groupId = "SHEET_${campaignData.sheetFileName ?: "Scheduled"}",
            campaignName = campaignData.campaignName,
            message = campaignData.templateMessage ?: "",
            timestamp = System.currentTimeMillis(),
            totalContacts = generatedMessages.size,
            contactStatuses = generatedMessages.map { ContactStatus(it.recipientNumber, "pending") },
            isStopped = false,
            isRunning = true,
            campaignType = "SHEETSSEND",
            countryCode = campaignData.countryCode
        )
        
        campaignDao.upsertCampaign(campaign)
        CampaignAutoSendManager.onCampaignLaunched(campaign)
        
        // Update scheduled campaign status to RUNNING
        database.scheduledCampaignDao().updateCampaignStatus(scheduledCampaign.id, ScheduleStatus.RUNNING.name)
        
        val packageName = when (campaignData.whatsAppPreference) {
            "WhatsApp" -> "com.whatsapp"
            "WhatsApp Business" -> "com.whatsapp.w4b"
            else -> "com.whatsapp"
        }
        
        // Auto-detect available WhatsApp if the preferred one is not installed
        val finalPackageName = if (!isPackageInstalled(this, packageName)) {
            when {
                isPackageInstalled(this, "com.whatsapp.w4b") -> "com.whatsapp.w4b"
                isPackageInstalled(this, "com.whatsapp") -> "com.whatsapp"
                else -> {
                    Log.e("CampaignExecutionService", "No WhatsApp app found")
                    database.scheduledCampaignDao().updateCampaignStatus(scheduledCampaign.id, ScheduleStatus.FAILED.name)
                    updateNotification("Failed: No WhatsApp app installed")
                    delay(3000)
                    return
                }
            }
        } else {
            packageName
        }
        
        val delayMillis = getDelayFromSettings(campaignData.delaySettings)
        
        try {
            for ((index, finalMessage) in generatedMessages.withIndex()) {
                // Update detailed notification with current progress
                updateDetailedNotification(
                    campaignName = campaignData.campaignName,
                    campaignType = "SHEETSSEND",
                    currentContact = finalMessage.recipientName,
                    currentIndex = index + 1,
                    totalContacts = generatedMessages.size
                )
                
                val cleanNumber = finalMessage.recipientNumber.replace(Regex("[^\\d]"), "")
                
                val messageToSend = if (campaignData.uniqueIdEnabled) {
                    finalMessage.messageBody + "\n\n" + generateRandomString()
                } else {
                    finalMessage.messageBody
                }
                
                try {
                    sendTextMessage(cleanNumber, messageToSend, finalPackageName)
                    
                    // Update contact status as sent
                    campaignDao.updateContactStatus(campaign.id, finalMessage.recipientNumber, "sent")
                    
                } catch (e: Exception) {
                    Log.e("CampaignExecutionService", "Failed to send to ${finalMessage.recipientName}: ${e.message}")
                    campaignDao.updateContactStatus(campaign.id, finalMessage.recipientNumber, "failed")
                }
                
                // Delay between messages
                delay(delayMillis)
            }
            
            // Mark campaign as completed
            val finishedCampaign = campaign.copy(isRunning = false, isStopped = false)
            campaignDao.upsertCampaign(finishedCampaign)
            CampaignAutoSendManager.onCampaignCompleted(finishedCampaign)
            
            database.scheduledCampaignDao().updateCampaignStatus(scheduledCampaign.id, ScheduleStatus.COMPLETED.name)
            updateNotification("✅ Sheet campaign completed: ${campaignData.campaignName}")
            delay(2000)
            
        } catch (e: Exception) {
            Log.e("CampaignExecutionService", "Sheet campaign failed: ${e.message}")
            database.scheduledCampaignDao().updateCampaignStatus(scheduledCampaign.id, ScheduleStatus.FAILED.name)
        }
    }
    
    // Helper data class for sheet data parsing (matches SheetsendActivity.SheetData)
    private data class SheetData(
        val headers: List<String> = emptyList(),
        val rows: List<Map<String, String>> = emptyList()
    )
    
    // Helper to generate messages from sheet data (mirrors SheetsendActivity logic)
    private fun generateMessagesFromSheetData(
        templateMessage: String,
        sheetData: SheetData,
        countryCode: String
    ): List<SheetFinalMessage> {
        val messages = mutableListOf<SheetFinalMessage>()
        
        for (row in sheetData.rows) {
            // Find number column (case-insensitive)
            val numberValue = row.entries.find { 
                it.key.lowercase().contains("number") || 
                it.key.lowercase().contains("phone") ||
                it.key.lowercase().contains("mobile")
            }?.value ?: continue
            
            if (numberValue.isBlank()) continue
            
            // Find name column (case-insensitive)
            val nameValue = row.entries.find { 
                it.key.lowercase().contains("name") 
            }?.value ?: "User"
            
            // Process phone number
            val finalNumber = if (numberValue.startsWith("+")) {
                numberValue
            } else if (countryCode.isNotBlank()) {
                val cleanCode = if (countryCode.startsWith("+")) countryCode else "+$countryCode"
                "$cleanCode$numberValue"
            } else {
                numberValue
            }
            
            // Replace placeholders in template
            var messageBody = templateMessage
            for ((header, value) in row) {
                messageBody = messageBody.replace("{$header}", value, ignoreCase = true)
                messageBody = messageBody.replace("{{$header}}", value, ignoreCase = true)
            }
            // Also replace #name# style
            messageBody = messageBody.replace("#name#", nameValue, ignoreCase = true)
            
            messages.add(SheetFinalMessage(
                recipientName = nameValue,
                recipientNumber = finalNumber,
                messageBody = messageBody
            ))
        }
        
        return messages
    }
    
    // Data class for generated sheet messages
    private data class SheetFinalMessage(
        val recipientName: String,
        val recipientNumber: String,
        val messageBody: String
    )
    
    private suspend fun executeCampaignLogic(
        campaign: Campaign,
        campaignData: CampaignData,
        contacts: List<Contact>
    ) {
        val packageName = when (campaignData.whatsAppPreference) {
            "WhatsApp" -> "com.whatsapp"
            "WhatsApp Business" -> "com.whatsapp.w4b"
            else -> "com.whatsapp"
        }
        
        // Auto-detect available WhatsApp if the preferred one is not installed
        val finalPackageName = if (!isPackageInstalled(this, packageName)) {
            when {
                isPackageInstalled(this, "com.whatsapp.w4b") -> {
                    Log.d("CampaignExecutionService", "Preferred WhatsApp not found, using WhatsApp Business")
                    "com.whatsapp.w4b"
                }
                isPackageInstalled(this, "com.whatsapp") -> {
                    Log.d("CampaignExecutionService", "Preferred WhatsApp not found, using regular WhatsApp")
                    "com.whatsapp"
                }
                else -> {
                    Log.e("CampaignExecutionService", "No WhatsApp app found in executeCampaignLogic")
                    throw Exception("No WhatsApp app installed")
                }
            }
        } else {
            packageName
        }
        
        val delayMillis = getDelayFromSettings(campaignData.delaySettings)
        
        for ((index, contact) in contacts.withIndex()) {
            // Update detailed notification with current progress
            updateDetailedNotification(
                campaignName = campaignData.campaignName,
                campaignType = campaign.campaignType,
                currentContact = contact.name,
                currentIndex = index + 1,
                totalContacts = contacts.size
            )
            
            val finalNumber = if (contact.number.startsWith("+")) {
                contact.number.replace(Regex("[^\\d+]"), "")
            } else {
                val cleanCode = campaignData.countryCode.replace(Regex("[^\\d+]"), "")
                val cleanNum = contact.number.replace(Regex("[^\\d]"), "")
                "$cleanCode$cleanNum"
            }
            val cleanNumber = finalNumber.replace("+", "")
            
            val baseMessage = if (campaignData.uniqueIdEnabled) {
                (campaignData.messageText ?: campaignData.captionText ?: "") + "\n\n" + generateRandomString()
            } else {
                campaignData.messageText ?: campaignData.captionText ?: ""
            }
            
            val personalizedMessage = baseMessage.replace("#name#", contact.name, ignoreCase = true)
            
            try {
                // Send message based on campaign type
                when (campaign.campaignType) {
                    "BULKTEXT" -> sendTextMessage(cleanNumber, personalizedMessage, finalPackageName)
                    "BULKSEND" -> sendMediaMessage(cleanNumber, personalizedMessage, campaignData.mediaPath, finalPackageName)
                    "TEXTMEDIA" -> sendTextAndMedia(cleanNumber, personalizedMessage, campaignData.mediaPath, campaignData.sendOrder, finalPackageName)
                }
                
                // Update contact status
                val database = AppDatabase.getInstance(this)
                database.campaignDao().updateContactStatus(campaign.id, contact.number, "sent")
                
            } catch (e: Exception) {
                Log.e("CampaignExecutionService", "Failed to send to ${contact.name}: ${e.message}")
                val database = AppDatabase.getInstance(this)
                database.campaignDao().updateContactStatus(campaign.id, contact.number, "failed")
            }
            
            // Delay between messages
            delay(delayMillis)
        }
        
        // Show completion notification
        updateNotification("✅ Campaign completed: ${campaignData.campaignName}")
        delay(2000)
    }
    
    private suspend fun sendTextMessage(cleanNumber: String, message: String, packageName: String) {
        val encodedMessage = URLEncoder.encode(message, "UTF-8")
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$cleanNumber?text=$encodedMessage")).apply {
            setPackage(packageName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(intent)
        delay(3000) // Wait for WhatsApp to open and send
    }
    
    private suspend fun sendMediaMessage(cleanNumber: String, caption: String, mediaPath: String?, packageName: String) {
        // Open chat first
        val openChatIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$cleanNumber")).apply {
            setPackage(packageName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(openChatIntent)
        delay(2500)
        
        // Send media if available
        if (mediaPath != null) {
            val mediaUri = try {
                // Check if it's already a content URI
                if (mediaPath.startsWith("content://")) {
                    Uri.parse(mediaPath)
                } else {
                    // It's a local file path, use FileProvider
                    val localFile = java.io.File(mediaPath)
                    if (localFile.exists()) {
                        androidx.core.content.FileProvider.getUriForFile(
                            this,
                            "${this.packageName}.provider",
                            localFile
                        )
                    } else {
                        Log.e("CampaignExecutionService", "Media file not found: $mediaPath")
                        return
                    }
                }
            } catch (e: Exception) {
                Log.e("CampaignExecutionService", "Failed to create media URI: ${e.message}")
                return
            }
            
            // Determine MIME type
            val mimeType = if (mediaPath.contains(".")) {
                val extension = mediaPath.substringAfterLast('.', "").lowercase()
                when (extension) {
                    "jpg", "jpeg" -> "image/jpeg"
                    "png" -> "image/png"
                    "gif" -> "image/gif"
                    "mp4" -> "video/mp4"
                    "3gp" -> "video/3gpp"
                    "avi" -> "video/x-msvideo"
                    "mov" -> "video/quicktime"
                    "mp3" -> "audio/mpeg"
                    "wav" -> "audio/wav"
                    "pdf" -> "application/pdf"
                    "doc" -> "application/msword"
                    "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                    "xls" -> "application/vnd.ms-excel"
                    "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                    "ppt" -> "application/vnd.ms-powerpoint"
                    "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
                    "txt" -> "text/plain"
                    else -> "*/*"
                }
            } else {
                "*/*"
            }
            
            val sendMediaIntent = Intent(Intent.ACTION_SEND).apply {
                putExtra(Intent.EXTRA_STREAM, mediaUri)
                type = mimeType
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                if (caption.isNotBlank()) putExtra(Intent.EXTRA_TEXT, caption)
                putExtra("jid", "$cleanNumber@s.whatsapp.net")
                setPackage(packageName)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(sendMediaIntent)
            delay(3000)
        }
    }
    
    private suspend fun sendTextAndMedia(
        cleanNumber: String, 
        message: String, 
        mediaPath: String?, 
        sendOrder: String?, 
        packageName: String
    ) {
        if (sendOrder == "TEXT_FIRST") {
            sendTextMessage(cleanNumber, message, packageName)
            delay(2000)
            if (mediaPath != null) {
                sendMediaMessage(cleanNumber, "", mediaPath, packageName)
            }
        } else {
            if (mediaPath != null) {
                sendMediaMessage(cleanNumber, "", mediaPath, packageName)
                delay(2000)
            }
            sendTextMessage(cleanNumber, message, packageName)
        }
    }
    
    private fun getContactsFromCampaignData(campaignData: CampaignData): List<Contact> {
        return campaignData.selectedContacts ?: emptyList()
    }
    
    private fun getDelayFromSettings(delaySettings: String): Long {
        return when {
            delaySettings.startsWith("Custom") -> {
                try {
                    delaySettings.substringAfter("(").substringBefore(" sec").trim().toLong() * 1000
                } catch (e: Exception) { 5000L }
            }
            delaySettings.startsWith("Random") -> Random.nextLong(5000, 15001)
            else -> {
                try {
                    delaySettings.split(" ")[0].toLong() * 1000
                } catch (e: Exception) { 5000L }
            }
        }
    }
    
    private fun generateRandomString(): String {
        val allowedChars = ('a'..'z') + ('A'..'Z') + ('0'..'9')
        val length = (4..7).random()
        return (1..length).map { allowedChars.random() }.joinToString("")
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                notificationChannelId,
                "Scheduled Campaign Execution",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows progress of scheduled WhatsApp campaigns"
                setShowBadge(true)
                enableLights(true)
                lightColor = android.graphics.Color.GREEN
            }
            
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    private fun createNotification(content: String): Notification {
        val intent = Intent(this, com.message.bulksend.bulksend.scheduledcampaign.ScheduleSendActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
        )
        
        return NotificationCompat.Builder(this, notificationChannelId)
            .setContentTitle("📱 Scheduled Campaign Running")
            .setContentText(content)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
    }
    
    private fun createDetailedNotification(
        campaignName: String,
        campaignType: String,
        currentContact: String,
        currentIndex: Int,
        totalContacts: Int
    ): Notification {
        val intent = Intent(this, com.message.bulksend.bulksend.scheduledcampaign.ScheduleSendActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
        )
        
        // Campaign type icon
        val typeIcon = when (campaignType) {
            "BULKTEXT" -> "📝"
            "BULKSEND" -> "🖼️"
            "TEXTMEDIA" -> "📎"
            "SHEETSSEND" -> "📊"
            else -> "📱"
        }
        
        val typeName = when (campaignType) {
            "BULKTEXT" -> "Text Campaign"
            "BULKSEND" -> "Media Campaign"
            "TEXTMEDIA" -> "Text+Media Campaign"
            "SHEETSSEND" -> "Sheet Campaign"
            else -> "Campaign"
        }
        
        val progress = ((currentIndex.toFloat() / totalContacts.toFloat()) * 100).toInt()
        
        return NotificationCompat.Builder(this, notificationChannelId)
            .setContentTitle("$typeIcon $campaignName")
            .setContentText("Sending to: $currentContact")
            .setSubText("$typeName • $currentIndex/$totalContacts ($progress%)")
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setProgress(totalContacts, currentIndex, false)
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText("Sending to: $currentContact\n$typeName\nProgress: $currentIndex of $totalContacts contacts ($progress%)")
                .setBigContentTitle("$typeIcon $campaignName"))
            .build()
    }
    
    private fun updateNotification(content: String) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(notificationId, createNotification(content))
    }
    
    private fun updateDetailedNotification(
        campaignName: String,
        campaignType: String,
        currentContact: String,
        currentIndex: Int,
        totalContacts: Int
    ) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(
            notificationId, 
            createDetailedNotification(campaignName, campaignType, currentContact, currentIndex, totalContacts)
        )
    }
}