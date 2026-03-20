package com.message.bulksend.autorespond.welcomemessage

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.message.bulksend.autorespond.documentreply.DocumentFile
import com.message.bulksend.autorespond.documentreply.DocumentReplyManager
import com.message.bulksend.autorespond.documentreply.DocumentSendService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Manager class for Welcome Message functionality
 */
class WelcomeMessageManager(private val context: Context) {
    
    companion object {
        const val TAG = "WelcomeMessageManager"
        private const val PREFS_NAME = "welcome_message_prefs"
        private const val KEY_IS_ENABLED = "is_enabled"
        private const val KEY_SEND_MULTIPLE = "send_multiple"
        private const val KEY_DELAY_BETWEEN = "delay_between_messages"
        private const val KEY_ONLY_NEW_CONTACTS = "only_new_contacts"
    }
    
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val database = WelcomeMessageDatabase.getDatabase(context)
    private val messageDao = database.welcomeMessageDao()
    private val sentDao = database.welcomeMessageSentDao()
    private val documentReplyManager = DocumentReplyManager(context)
    
    // Settings
    fun isEnabled(): Boolean = prefs.getBoolean(KEY_IS_ENABLED, false)
    fun setEnabled(enabled: Boolean) = prefs.edit().putBoolean(KEY_IS_ENABLED, enabled).apply()
    
    fun isSendMultiple(): Boolean = prefs.getBoolean(KEY_SEND_MULTIPLE, false)
    fun setSendMultiple(multiple: Boolean) = prefs.edit().putBoolean(KEY_SEND_MULTIPLE, multiple).apply()
    
    fun getDelayBetweenMessages(): Long = prefs.getLong(KEY_DELAY_BETWEEN, 1000L)
    fun setDelayBetweenMessages(delay: Long) = prefs.edit().putLong(KEY_DELAY_BETWEEN, delay).apply()
    
    fun isOnlyNewContacts(): Boolean = prefs.getBoolean(KEY_ONLY_NEW_CONTACTS, true)
    fun setOnlyNewContacts(only: Boolean) = prefs.edit().putBoolean(KEY_ONLY_NEW_CONTACTS, only).apply()
    
    fun getSettings(): WelcomeMessageSettings {
        return WelcomeMessageSettings(
            isEnabled = isEnabled(),
            sendMultiple = isSendMultiple(),
            delayBetweenMessages = getDelayBetweenMessages(),
            onlyNewContacts = isOnlyNewContacts()
        )
    }
    
    /**
     * Check if user should receive welcome message
     */
    suspend fun shouldSendWelcome(userId: String): Boolean = withContext(Dispatchers.IO) {
        if (!isEnabled()) {
            Log.d(TAG, "Welcome message disabled")
            return@withContext false
        }
        
        val hasReceived = sentDao.hasReceivedWelcome(userId)
        Log.d(TAG, "User $userId hasReceivedWelcome: $hasReceived")
        
        !hasReceived
    }
    
    /**
     * Get welcome messages to send
     * Returns list of messages (single or multiple based on settings)
     */
    suspend fun getWelcomeMessages(): List<WelcomeMessage> = withContext(Dispatchers.IO) {
        val messages = messageDao.getEnabledMessages()
        
        if (messages.isEmpty()) {
            Log.d(TAG, "No welcome messages configured")
            return@withContext emptyList()
        }
        
        if (isSendMultiple()) {
            Log.d(TAG, "Returning ${messages.size} welcome messages (multiple mode)")
            messages
        } else {
            Log.d(TAG, "Returning 1 welcome message (single mode)")
            listOf(messages.first())
        }
    }
    
    /**
     * Mark user as having received welcome message
     */
    suspend fun markWelcomeSent(userId: String) = withContext(Dispatchers.IO) {
        val existing = sentDao.getSentRecord(userId)
        if (existing != null) {
            sentDao.incrementMessageCount(userId)
        } else {
            sentDao.insertSentRecord(
                WelcomeMessageSent(
                    oderId = userId,
                    sentAt = System.currentTimeMillis(),
                    messageCount = 1
                )
            )
        }
        Log.d(TAG, "Marked welcome sent for user: $userId")
    }
    
    /**
     * Reset welcome status for a user (they will receive welcome again)
     */
    suspend fun resetWelcomeStatus(userId: String) = withContext(Dispatchers.IO) {
        sentDao.deleteSentRecord(userId)
        Log.d(TAG, "Reset welcome status for user: $userId")
    }
    
    /**
     * Reset all welcome statuses
     */
    suspend fun resetAllWelcomeStatuses() = withContext(Dispatchers.IO) {
        sentDao.deleteAllSentRecords()
        Log.d(TAG, "Reset all welcome statuses")
    }
    
    // Message CRUD operations
    suspend fun addMessage(
        message: String,
        delayMs: Long = 0,
        selectedDocuments: List<DocumentFile> = emptyList()
    ): Long = withContext(Dispatchers.IO) {
        val maxIndex = messageDao.getMaxOrderIndex() ?: -1
        val newMessage = WelcomeMessage(
            message = message,
            orderIndex = maxIndex + 1,
            delayMs = delayMs,
            selectedDocumentsJson = WelcomeMessageDocumentCodec.serialize(selectedDocuments)
        )
        val id = messageDao.insertMessage(newMessage)
        Log.d(TAG, "Added welcome message: $message")
        id
    }
    
    suspend fun updateMessage(message: WelcomeMessage) = withContext(Dispatchers.IO) {
        messageDao.updateMessage(message)
        Log.d(TAG, "Updated welcome message: ${message.id}")
    }
    
    suspend fun deleteMessage(id: Int) = withContext(Dispatchers.IO) {
        messageDao.deleteMessageById(id)
        Log.d(TAG, "Deleted welcome message: $id")
    }
    
    suspend fun getAllMessages(): List<WelcomeMessage> = withContext(Dispatchers.IO) {
        messageDao.getAllMessages()
    }
    
    suspend fun getMessageCount(): Int = withContext(Dispatchers.IO) {
        messageDao.getAllMessages().size
    }
    
    suspend fun getTotalSentCount(): Int = withContext(Dispatchers.IO) {
        sentDao.getTotalSentCount()
    }

    suspend fun queueWelcomeDocuments(
        userId: String,
        senderName: String,
        welcomeMessage: WelcomeMessage
    ): Int = withContext(Dispatchers.IO) {
        val selectedDocuments = welcomeMessage.getSelectedDocuments()
        if (selectedDocuments.isEmpty()) {
            return@withContext 0
        }

        val validDocuments = selectedDocuments.filter { document ->
            val currentDocument = documentReplyManager.getAllDocumentFiles().find { it.id == document.id }
            currentDocument?.savedPath?.let { java.io.File(it).exists() } == true ||
                java.io.File(document.savedPath).exists()
        }.map { document ->
            documentReplyManager.getAllDocumentFiles().find { it.id == document.id && java.io.File(it.savedPath).exists() }
                ?: document
        }.filter { document ->
            java.io.File(document.savedPath).exists()
        }

        if (validDocuments.isEmpty()) {
            Log.w(TAG, "No valid welcome documents found for message ${welcomeMessage.id}")
            return@withContext 0
        }

        DocumentSendService.enableDocumentSend()
        DocumentSendService.getInstance().addDocumentSendTask(
            context = context,
            phoneNumber = userId,
            senderName = senderName,
            keyword = "welcome_message_${welcomeMessage.id}",
            documents = validDocuments,
            documentPaths = validDocuments.map { it.savedPath }
        )

        Log.d(
            TAG,
            "Queued ${validDocuments.size} welcome documents for user $userId from message ${welcomeMessage.id}"
        )
        validDocuments.size
    }
}
