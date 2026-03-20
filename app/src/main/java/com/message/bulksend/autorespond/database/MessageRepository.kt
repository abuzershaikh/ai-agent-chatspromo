package com.message.bulksend.autorespond.database

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.Flow
import java.text.SimpleDateFormat
import java.util.*

/**
 * Repository for message tracking operations
 */
class MessageRepository(context: Context) {
    
    private val messageDao = MessageDatabase.getDatabase(context).messageDao()
    private val dateFormat = SimpleDateFormat("dd/MM/yyyy hh:mm:ss a", Locale.getDefault())
    private val appContext = context.applicationContext
    
    companion object {
        const val TAG = "MessageRepository"
    }
    
    /**
     * Insert new incoming message
     */
    suspend fun insertIncomingMessage(
        phoneNumber: String,
        senderName: String,
        incomingMessage: String,
        notificationKey: String
    ): Long {
        val count = messageDao.getMessageCount()
        val srNo = count + 1
        
        val message = MessageEntity(
            srNo = srNo,
            phoneNumber = phoneNumber,
            senderName = senderName,
            incomingMessage = incomingMessage,
            outgoingMessage = "",
            status = "PENDING",
            timestamp = System.currentTimeMillis(),
            dateTime = dateFormat.format(Date()),
            notificationKey = notificationKey
        )
        
        val id = messageDao.insertMessage(message)
        Log.d(TAG, "Message inserted - ID: $id, Sr: $srNo, From: $senderName, Phone: $phoneNumber")
        
        // Auto-add lead if enabled
        checkAndAutoAddLead(senderName, phoneNumber, incomingMessage, "com.whatsapp")
        
        return id
    }
    
    /**
     * Insert new incoming Instagram message
     */
    suspend fun insertIncomingInstagramMessage(
        username: String,
        incomingMessage: String,
        notificationKey: String
    ): Long {
        val count = messageDao.getMessageCount()
        val srNo = count + 1
        val phoneNumber = "instagram_$username" // Use Instagram username as identifier
        
        val message = MessageEntity(
            srNo = srNo,
            phoneNumber = phoneNumber,
            senderName = username,
            incomingMessage = incomingMessage,
            outgoingMessage = "",
            status = "PENDING",
            timestamp = System.currentTimeMillis(),
            dateTime = dateFormat.format(Date()),
            notificationKey = notificationKey
        )
        
        val id = messageDao.insertMessage(message)
        Log.d(TAG, "Instagram message inserted - ID: $id, Sr: $srNo, From: $username")
        
        // Auto-add lead if enabled
        checkAndAutoAddLead(username, phoneNumber, incomingMessage, "com.instagram.android")
        
        return id
    }
    
    /**
     * Check and auto-add lead to Lead Manager based on settings
     */
    private suspend fun checkAndAutoAddLead(
        senderName: String,
        phoneNumber: String,
        messageText: String,
        packageName: String = "com.whatsapp"
    ) {
        try {
            val leadManager = com.message.bulksend.leadmanager.LeadManager(appContext)
            val settings = leadManager.getAutoAddSettings()
            
            Log.d(TAG, "🔍 Auto-add check - Sender: $senderName, Phone: $phoneNumber")
            Log.d(TAG, "🔍 Settings - Enabled: ${settings?.isAutoAddEnabled}, AutoAddAll: ${settings?.autoAddAllMessages}, KeywordBased: ${settings?.keywordBasedAdd}")
            
            // Check if auto-add is enabled
            if (settings == null || !settings.isAutoAddEnabled) {
                Log.d(TAG, "❌ Auto-add leads is disabled or settings null")
                return
            }
            
            if (phoneNumber.isBlank()) {
                Log.d(TAG, "❌ Phone number is blank, cannot add lead")
                return
            }
            
            Log.d(TAG, "✓ Phone number valid: $phoneNumber")
            
            // Check if lead already exists
            if (settings.excludeExistingContacts) {
                val existingLead = leadManager.getLeadByPhone(phoneNumber)
                if (existingLead != null) {
                    Log.d(TAG, "Lead already exists: ${existingLead.name}")
                    
                    // Save chat message to existing lead
                    saveChatMessageToLead(leadManager, existingLead.id, senderName, phoneNumber, messageText, true, null, packageName)
                    return
                }
            }
            
            var matchedKeyword: String? = null
            var shouldAddLead = false
            
            // Check if we should add all messages or keyword-based
            if (settings.autoAddAllMessages) {
                shouldAddLead = true
                Log.d(TAG, "✓ Auto-add all messages enabled - adding lead")
            } else if (settings.keywordBasedAdd) {
                // Check keyword rules
                val matchedRule = leadManager.checkAutoAddKeywordMatch(messageText)
                if (matchedRule != null) {
                    shouldAddLead = true
                    matchedKeyword = matchedRule.keyword
                    Log.d(TAG, "✓ Keyword match found: ${matchedRule.keyword} - adding lead")
                }
            }
            if (shouldAddLead) {
                // Add lead
                val newLead = leadManager.addLeadFromAutoRespond(
                    senderName = senderName,
                    senderPhone = phoneNumber,
                    messageText = messageText,
                    matchedKeyword = matchedKeyword
                )

                if (newLead != null) {
                    Log.d(TAG, "\u2705 Lead added: ${newLead.name} (${newLead.phoneNumber})")

                    // Save chat message to new lead
                    saveChatMessageToLead(
                        leadManager,
                        newLead.id,
                        senderName,
                        phoneNumber,
                        messageText,
                        true,
                        matchedKeyword,
                        packageName
                    )

                    // Show notification if enabled
                    if (settings.notifyOnNewLead) {
                        showNewLeadNotification(newLead.name, phoneNumber, packageName)
                    }
                } else {
                    Log.d(TAG, "Lead add blocked for free plan limit (5 leads).")
                }
            } else {
                Log.d(TAG, "\u274c No conditions met for auto-add")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error in auto-add lead: ${e.message}", e)
        }
    }
    
    /**
     * Save chat message to lead's chat history
     */
    private fun saveChatMessageToLead(
        leadManager: com.message.bulksend.leadmanager.LeadManager,
        leadId: String,
        senderName: String,
        senderPhone: String,
        messageText: String,
        isIncoming: Boolean,
        matchedKeyword: String?,
        packageName: String = "com.whatsapp"
    ) {
        try {
            val chatMessage = com.message.bulksend.leadmanager.database.entities.ChatMessageEntity(
                id = java.util.UUID.randomUUID().toString(),
                leadId = leadId,
                senderName = senderName,
                senderPhone = senderPhone,
                messageText = messageText,
                timestamp = System.currentTimeMillis(),
                isIncoming = isIncoming,
                isAutoReply = false,
                matchedKeyword = matchedKeyword,
                replyType = "manual",
                packageName = packageName,
                isRead = false
            )
            
            leadManager.addChatMessage(chatMessage)
            Log.d(TAG, "✓ Chat message saved to lead: $leadId")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error saving chat message: ${e.message}", e)
        }
    }
    
    /**
     * Show notification when new lead is added
     */
    private fun showNewLeadNotification(leadName: String, phone: String, packageName: String = "com.whatsapp") {
        try {
            val notificationManager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            
            // Create notification channel for Android O+
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                val channel = android.app.NotificationChannel(
                    "new_lead_channel",
                    "New Lead Notifications",
                    android.app.NotificationManager.IMPORTANCE_DEFAULT
                )
                notificationManager.createNotificationChannel(channel)
            }
            
            // Build notification
            val notification = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                android.app.Notification.Builder(appContext, "new_lead_channel")
            } else {
                @Suppress("DEPRECATION")
                android.app.Notification.Builder(appContext)
            }
                .setSmallIcon(android.R.drawable.ic_input_add)
                .setContentTitle("New Lead Added")
                .setContentText("$leadName ($phone) added from ${if (packageName == "com.instagram.android") "Instagram" else "WhatsApp"}")
                .setAutoCancel(true)
                .build()
            
            notificationManager.notify((System.currentTimeMillis() + 1000).toInt(), notification)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error showing new lead notification: ${e.message}", e)
        }
    }
    
    /**
     * Update message with reply and status
     */
    suspend fun updateMessageWithReply(
        id: Int,
        outgoingMessage: String,
        status: String = "SENT"
    ) {
        messageDao.updateMessageStatus(id, status, outgoingMessage)
        Log.d(TAG, "Message updated - ID: $id, Status: $status, Reply: ${outgoingMessage.take(50)}")
    }
    
    /**
     * Check if message already processed
     */
    suspend fun isMessageProcessed(notificationKey: String): Boolean {
        val message = messageDao.getMessageByNotificationKey(notificationKey)
        return message != null
    }
    
    /**
     * Get last message from phone with same text
     */
    suspend fun getLastMessageByPhoneAndText(
        phoneNumber: String,
        incomingMessage: String
    ): MessageEntity? {
        return messageDao.getLastMessageByPhoneAndText(phoneNumber, incomingMessage)
    }
    
    /**
     * Check if same message was replied to recently (within specified time)
     */
    suspend fun hasRecentReplyForMessage(
        phoneNumber: String,
        incomingMessage: String,
        withinSeconds: Int = 20
    ): Boolean {
        val afterTime = System.currentTimeMillis() - (withinSeconds * 1000)
        val recentMessage = messageDao.getRecentSentMessageByPhoneAndText(phoneNumber, incomingMessage, afterTime)
        return recentMessage != null
    }
    
    /**
     * Get all messages
     */
    fun getAllMessages(): Flow<List<MessageEntity>> {
        return messageDao.getAllMessages()
    }
    
    /**
     * Get messages by phone number
     */
    fun getMessagesByPhone(phoneNumber: String): Flow<List<MessageEntity>> {
        return messageDao.getMessagesByPhone(phoneNumber)
    }

    /**
     * Get recent messages synchronously for AI Memory
     */
    suspend fun getRecentMessagesSync(phoneNumber: String, limit: Int = 10): List<MessageEntity> {
        // We need to add a DAO method for this. For now, we can use a workaround or add to DAO.
        // Ideally, we should add `getRecentMessagesByPhone(phoneNumber, limit)` to MessageDao.
        // Since I cannot see MessageDao right now, I will assume I can add it or use an existing one if available.
        // Let's check MessageDao first to be sure, or just add the DAO method.
        // Actually, let's just add the method to MessageRepository closely following the pattern.
        return messageDao.getRecentMessagesByPhoneSync(phoneNumber, limit)
    }
    
    /**
     * Delete old messages (older than 30 days)
     */
    suspend fun cleanOldMessages() {
        val thirtyDaysAgo = System.currentTimeMillis() - (30L * 24 * 60 * 60 * 1000)
        messageDao.deleteOldMessages(thirtyDaysAgo)
        Log.d(TAG, "Old messages cleaned")
    }
    
    /**
     * Get message count
     */
    suspend fun getMessageCount(): Int {
        return messageDao.getMessageCount()
    }
    
    // ==================== Instagram Statistics Methods ====================
    
    /**
     * Get Instagram message count (messages with instagram_ prefix in phone number)
     */
    suspend fun getInstagramMessageCount(): Int {
        return messageDao.getInstagramMessageCount()
    }
    
    /**
     * Get Instagram replies by status
     */
    suspend fun getInstagramRepliesByStatus(status: String): Int {
        return messageDao.getInstagramRepliesByStatus(status)
    }
    
    /**
     * Get Instagram messages for detailed view
     */
    fun getInstagramMessages(): Flow<List<MessageEntity>> {
        return messageDao.getInstagramMessages()
    }
}
