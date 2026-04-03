package com.message.bulksend.autorespond

import android.app.Notification
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.message.bulksend.autorespond.database.MessageRepository
import com.message.bulksend.autorespond.utils.PhoneNumberExtractor
import com.message.bulksend.autorespond.smartlead.LeadCaptureManager
import com.message.bulksend.utils.SubscriptionUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.net.URLEncoder

/**
 * Unified Notification Listener Service for WhatsApp, WhatsApp Business, and Instagram
 * Reads incoming message notifications and broadcasts them
 */
class WhatsAppNotificationListener : NotificationListenerService() {

    companion object {
        const val TAG = "UnifiedNotificationListener"
        const val ACTION_NOTIFICATION_RECEIVED = "com.message.bulksend.NOTIFICATION_RECEIVED"
        const val ACTION_INSTAGRAM_NOTIFICATION_RECEIVED = "com.message.bulksend.INSTAGRAM_NOTIFICATION_RECEIVED"
        const val EXTRA_SENDER_NAME = "sender_name"
        const val EXTRA_MESSAGE_TEXT = "message_text"
        const val EXTRA_PACKAGE_NAME = "package_name"
        const val EXTRA_TIMESTAMP = "timestamp"
        
        const val WHATSAPP_PACKAGE = "com.whatsapp"
        const val WHATSAPP_BUSINESS_PACKAGE = "com.whatsapp.w4b"
        const val INSTAGRAM_PACKAGE = "com.instagram.android"
        private const val MAX_AGENTFORM_PDF_BYTES = 12L * 1024L * 1024L
        private const val PREFS_NAME = "subscription_prefs"
        private const val KEY_AUTO_REPLY_FREE_COUNT = "free_auto_reply_count"
        private const val KEY_AUTO_REPLY_FREE_NUMBERS = "free_auto_reply_numbers"
        private const val AUTO_REPLY_LIMIT_BRANDING = "[ChatSpromo Auto Reply]"
        private const val AUTO_REPLY_LIMIT_UPGRADE_TEXT = "Plan is over. Upgrade your chatspromo plan."
    }
    
    // Track processed messages to avoid duplicates - using ConcurrentHashMap for thread safety
    private val processedMessages = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    private val messageTimestamps = java.util.concurrent.ConcurrentHashMap<String, Long>()
    
    // Track last reply time per sender to avoid rapid replies
    private val lastReplyTimeBySender = java.util.concurrent.ConcurrentHashMap<String, Long>()
    
    // Track last processed message per sender
    private val lastMessageBySender = java.util.concurrent.ConcurrentHashMap<String, String>()
    private val autoReplyUsageLock = Any()

    private data class AutoReplyPolicyResult(
        val finalReplyText: String,
        val allowSend: Boolean,
        val incrementUsage: Boolean
    )

    private data class SummaryConversationMessage(
        val senderName: String,
        val messageText: String,
        val senderPhone: String = ""
    )
    
    // Cooldown period for exact duplicate messages (5 seconds)
    private val duplicateMessageCooldownMs = 5000L
    
    // Minimum delay between replies to same sender (2 seconds)
    private val senderReplyCooldownMs = 2000L
    
    // Room Database Repository
    private lateinit var messageRepository: MessageRepository
    
    // Coroutine scope for database operations
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    // Wake lock for catalogue sending (to prevent doze mode issues)
    private var catalogueWakeLock: PowerManager.WakeLock? = null
    
    // Keyguard lock to dismiss lock screen during catalogue sending
    @Suppress("DEPRECATION")
    private var catalogueKeyguardLock: android.app.KeyguardManager.KeyguardLock? = null
    
    // Broadcast receiver for voice note replies
    private val voiceNoteReplyReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "com.message.bulksend.SEND_VOICE_NOTE_REPLY") {
                val phoneNumber = intent.getStringExtra("phoneNumber") ?: return
                val senderName = intent.getStringExtra("senderName") ?: return
                val replyText = intent.getStringExtra("replyText") ?: return
                val messageId = intent.getIntExtra("messageId", -1)
                
                Log.d(TAG, "Ã°Å¸Å½Â¤ Received voice note reply broadcast")
                Log.d(TAG, "Ã°Å¸â€œÅ¾ Phone: $phoneNumber, Name: $senderName")
                Log.d(TAG, "Ã°Å¸â€œÂ Reply: ${replyText.take(50)}...")
                
                // Normalize phone number (remove spaces, dashes, etc.)
                val normalizedPhone = phoneNumber.replace(Regex("[^0-9+]"), "")
                
                // Find active notification for this sender
                try {
                    val activeNotifications = activeNotifications
                    var foundNotification = false
                    
                    activeNotifications?.forEach { sbn ->
                        if (sbn.packageName == WHATSAPP_PACKAGE || 
                            sbn.packageName == WHATSAPP_BUSINESS_PACKAGE) {
                            
                            val title = sbn.notification.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
                            val normalizedTitle = title?.replace(Regex("[^0-9+]"), "") ?: ""
                            
                            // Match by:
                            // 1. Exact sender name match
                            // 2. Normalized phone number match
                            // 3. Title contains phone number
                            if (title == senderName || 
                                normalizedTitle == normalizedPhone ||
                                title?.contains(phoneNumber) == true ||
                                phoneNumber.contains(normalizedTitle)) {
                                if (!isAutoReplyDispatchAllowed(sbn.packageName, messageId, replyText)) {
                                    Log.d(
                                        TAG,
                                        "Skipping voice note reply for disabled package: ${sbn.packageName}"
                                    )
                                    return@forEach
                                }
                                
                                Log.d(TAG, "Ã¢Å“â€œ Found matching notification: $title")
                                sendReplyViaNotificationNoDelay(
                                    sbn = sbn,
                                    replyText = replyText,
                                    messageId = messageId,
                                    senderIdentifier = phoneNumber
                                )
                                foundNotification = true
                                
                                // Check if voice reply should be sent
                                Thread {
                                    try {
                                        Thread.sleep(500) // Small delay before checking voice
                                        
                                        Log.d(TAG, "Ã°Å¸Å½Â¤ Checking if voice reply is enabled...")
                                        
                                        // Initialize speech settings FIRST before checking
                                        val speechManager = com.message.bulksend.aiagent.tools.agentspeech.AgentSpeechManager.getInstance(this@WhatsAppNotificationListener)
                                        kotlinx.coroutines.runBlocking {
                                            speechManager.initializeSettings()  // Ensure settings exist
                                        }
                                        
                                        // Get current settings to log
                                        val currentSettings = kotlinx.coroutines.runBlocking {
                                            speechManager.getSettings().first()
                                        }
                                        Log.d(TAG, "Ã°Å¸Å½Â¤ Current settings: enabled=${currentSettings?.isEnabled}, language=${currentSettings?.language}")
                                        
                                        val speechIntegration = com.message.bulksend.aiagent.tools.agentspeech.AgentSpeechAIIntegration(this@WhatsAppNotificationListener)
                                        val isEnabled = kotlinx.coroutines.runBlocking {
                                            speechIntegration.isSpeechEnabled()
                                        }
                                        Log.d(TAG, "Ã°Å¸Å½Â¤ Voice reply enabled status: $isEnabled")
                                        
                                        if (isEnabled) {
                                            Log.d(TAG, "Ã°Å¸Å½Â¤ Voice reply enabled, queuing text for speech...")
                                            val queueId = kotlinx.coroutines.runBlocking {
                                                speechIntegration.queueTextForSpeech(replyText, phoneNumber)
                                            }
                                            if (queueId > 0) {
                                                Log.d(TAG, "Ã°Å¸Å½Â¤ Text queued for speech: Queue ID=$queueId")
                                            } else {
                                                Log.d(TAG, "Ã°Å¸Å½Â¤ Voice reply skipped (queue failed, ID=$queueId)")
                                            }
                                        } else {
                                            Log.d(TAG, "Ã°Å¸Å½Â¤ Voice reply disabled in settings")
                                        }
                                    } catch (e: Exception) {
                                        Log.e(TAG, "Ã¢ÂÅ’ Error queuing voice reply: ${e.message}", e)
                                    }
                                }.start()
                                
                                return
                            }
                        }
                    }
                    
                    if (!foundNotification) {
                        Log.w(TAG, "Ã¢Å¡Â Ã¯Â¸Â No active notification found")
                        Log.d(TAG, "Looking for: Phone=$normalizedPhone, Name=$senderName")
                        
                        // Log all active notifications for debugging
                        activeNotifications?.forEach { sbn ->
                            if (sbn.packageName == WHATSAPP_PACKAGE || 
                                sbn.packageName == WHATSAPP_BUSINESS_PACKAGE) {
                                val title = sbn.notification.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
                                Log.d(TAG, "Available notification: $title")
                            }
                        }
                        
                        // Reply will be sent when next notification arrives
                    }
                    
                } catch (e: Exception) {
                    Log.e(TAG, "Ã¢ÂÅ’ Error sending voice note reply: ${e.message}")
                }
            }
        }
    }
    
    override fun onCreate() {
        super.onCreate()
        messageRepository = MessageRepository(this)
        
        // Register broadcast receiver for voice note replies
        val filter = android.content.IntentFilter("com.message.bulksend.SEND_VOICE_NOTE_REPLY")
        registerReceiver(voiceNoteReplyReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        
        Log.d(TAG, "Unified NotificationListener created with Room DB (WhatsApp + Instagram)")
        Log.d(TAG, "Ã¢Å“â€œ Voice note reply receiver registered")
    }
    
    override fun onDestroy() {
        super.onDestroy()
        
        // Unregister broadcast receiver
        try {
            unregisterReceiver(voiceNoteReplyReceiver)
            Log.d(TAG, "Ã¢Å“â€œ Voice note reply receiver unregistered")
        } catch (e: Exception) {
            Log.e(TAG, "Error unregistering receiver: ${e.message}")
        }
        
        releaseCatalogueWakeLock()
    }
    
    /**
     * Acquire wake lock AND dismiss keyguard to prevent doze mode during catalogue sending
     */
    private fun acquireCatalogueWakeLock() {
        try {
            if (catalogueWakeLock?.isHeld == true) {
                Log.d(TAG, "Ã¢Å¡Â Ã¯Â¸Â Wake lock already held")
                return
            }
            
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            // Use SCREEN_BRIGHT_WAKE_LOCK | ACQUIRE_CAUSES_WAKEUP to turn screen on
            val wakeLockLevel = PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP
            
            catalogueWakeLock = powerManager.newWakeLock(
                wakeLockLevel,
                "BulkSend:CatalogueSending"
            ).apply {
                acquire(2 * 60 * 1000L) // 2 minutes max
            }
            Log.d(TAG, "Ã¢Å“â€¦ Wake lock acquired for catalogue sending (SCREEN_BRIGHT|ACQUIRE_CAUSES_WAKEUP)")
            
            // Dismiss keyguard (lock screen) so accessibility can click send button
            try {
                @Suppress("DEPRECATION")
                val km = getSystemService(Context.KEYGUARD_SERVICE) as android.app.KeyguardManager
                @Suppress("DEPRECATION")
                catalogueKeyguardLock = km.newKeyguardLock("BulkSend:CatalogueSend")
                @Suppress("DEPRECATION")
                catalogueKeyguardLock?.disableKeyguard()
                Log.d(TAG, "Ã¢Å“â€¦ Keyguard dismissed for catalogue sending")
            } catch (e: Exception) {
                Log.e(TAG, "Ã¢Å¡Â Ã¯Â¸Â Keyguard dismiss failed: ${e.message}")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Ã¢ÂÅ’ Failed to acquire wake lock: ${e.message}")
        }
    }
    
    /**
     * Release wake lock and re-enable keyguard after catalogue sending
     */
    private fun releaseCatalogueWakeLock() {
        try {
            // Re-enable keyguard first
            try {
                @Suppress("DEPRECATION")
                catalogueKeyguardLock?.reenableKeyguard()
                catalogueKeyguardLock = null
                Log.d(TAG, "Ã¢Å“â€¦ Keyguard re-enabled after catalogue sending")
            } catch (e: Exception) {
                Log.e(TAG, "Ã¢Å¡Â Ã¯Â¸Â Keyguard re-enable failed: ${e.message}")
            }
            
            catalogueWakeLock?.let {
                if (it.isHeld) {
                    it.release()
                    Log.d(TAG, "Ã¢Å“â€¦ Wake lock released")
                }
            }
            catalogueWakeLock = null
        } catch (e: Exception) {
            Log.e(TAG, "Ã¢ÂÅ’ Failed to release wake lock: ${e.message}")
        }
    }

    private fun getAppDisplayName(packageName: String): String {
        return when (packageName) {
            WHATSAPP_PACKAGE -> "WhatsApp"
            WHATSAPP_BUSINESS_PACKAGE -> "WhatsApp Business"
            INSTAGRAM_PACKAGE -> "Instagram"
            else -> packageName
        }
    }

    private fun isAutoReplyDispatchAllowed(
        packageName: String,
        messageId: Int = -1,
        replyText: String = ""
    ): Boolean {
        val autoRespondManager = AutoRespondManager(this)
        if (!autoRespondManager.isAutoRespondEnabled()) {
            Log.d(TAG, "Blocking reply dispatch because auto-respond is disabled")
            if (messageId >= 0) {
                serviceScope.launch {
                    messageRepository.updateMessageWithReply(messageId, replyText, "DISABLED")
                }
            }
            return false
        }

        val settingsManager = com.message.bulksend.autorespond.settings.AutoReplySettingsManager(this)
        if (!settingsManager.shouldReplyToPackage(packageName)) {
            val appName = getAppDisplayName(packageName)
            Log.d(TAG, "Blocking reply dispatch because $appName is disabled in settings")
            if (messageId >= 0) {
                serviceScope.launch {
                    messageRepository.updateMessageWithReply(messageId, replyText, "APP_DISABLED")
                }
            }
            return false
        }

        return true
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        
        sbn?.let {
            val packageName = it.packageName
            
            // Check if notification is from WhatsApp, WhatsApp Business, or Instagram
            when (packageName) {
                WHATSAPP_PACKAGE, WHATSAPP_BUSINESS_PACKAGE -> {
                    processWhatsAppNotification(it)
                }
                INSTAGRAM_PACKAGE -> {
                    processInstagramNotification(it)
                }
            }
        }
    }

    private fun processWhatsAppNotification(sbn: StatusBarNotification) {
        try {
            val notification = sbn.notification
            val extras = notification.extras
            
            // Extract sender name and message text
            val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim() ?: ""
            val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim() ?: ""

            if (isWhatsAppBackupNotification(title, text, extras)) {
                Log.d(
                    TAG,
                    "Skipping WhatsApp backup notification: title='$title', text='${text.take(80)}'"
                )
                return
            }
             
            // Check if this is a voice note notification
            val isVoiceNote = text.contains("Ã°Å¸Å½Â¤") || 
                             text.contains("Voice message") || 
                             text.contains("voice note", ignoreCase = true) ||
                             text.contains("PTT", ignoreCase = true)
            
            if (isVoiceNote) {
                Log.d(TAG, "Ã°Å¸Å½Â¤ Voice note detected from: $title")
                
                // Extract phone number
                val phoneNumber = if (PhoneNumberExtractor.isPhoneNumber(title)) {
                    title.replace(Regex("[^0-9+]"), "")
                } else {
                    PhoneNumberExtractor.extractFromNotification(extras) 
                        ?: PhoneNumberExtractor.getPhoneNumber(this, title)
                        ?: "Unknown"
                }
                
                // Track voice note notification
                try {
                    com.message.bulksend.voicenotereply.VoiceNoteNotificationTracker.trackVoiceNoteNotification(phoneNumber, title)
                    Log.d(TAG, "Ã¢Å“â€œ Voice note tracked for: $phoneNumber ($title)")
                    
                    // Trigger immediate fetch if voice note reply is enabled
                    if (com.message.bulksend.voicenotereply.VoiceNoteReplyManager.isEnabled(this)) {
                        val folderUri = com.message.bulksend.voicenotereply.VoiceNoteReplyManager.getFolderUri(this)
                        if (folderUri != null) {
                            Log.d(TAG, "Ã°Å¸Å¡â‚¬ Triggering immediate voice note fetch...")
                            com.message.bulksend.voicenotereply.VoiceNoteFileObserver.triggerImmediateFetch(this, folderUri)
                        } else {
                            Log.w(TAG, "Ã¢Å¡Â Ã¯Â¸Â Voice note folder not selected")
                        }
                    } else {
                        Log.d(TAG, "Ã¢Å¡Â Ã¯Â¸Â Voice note reply is disabled")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error tracking voice note: ${e.message}")
                }
            }
            
            // Skip if sender is "You" (own messages)
            if (title == "You") {
                Log.d(TAG, "Skipping own message")
                return
            }

            val isSummaryNotification = isMultiChatSummaryNotification(title, text)
            if (isSummaryNotification) {
                val handled = processMultiChatSummaryNotification(sbn, title, text, extras)
                if (handled) {
                    return
                }
                Log.d(
                    TAG,
                    "Skipping summary notification without resolvable chats: title='$title', text='${text.take(60)}'"
                )
                return
            }

            if (isTransientMediaProgressMessage(text)) {
                Log.d(
                    TAG,
                    "Skipping transient media progress notification: title='$title', text='${text.take(80)}'"
                )
                return
            }

            if (isEmojiReactionNotification(text)) {
                Log.d(
                    TAG,
                    "Skipping emoji reaction notification: title='$title', text='${text.take(80)}'"
                )
                return
            }
             
            // Skip group notifications and app notifications
            if (title.isEmpty() || text.isEmpty() ||
                title.contains("WhatsApp") ||
                title.contains("messages") ||
                title.contains(":") ||
                text.contains("new messages")) {
                Log.d(
                    TAG,
                    "Skipping non-chat notification: title='$title', text='${text.take(60)}'"
                )
                return
            }
            
            val currentTime = System.currentTimeMillis()
            val postTime = sbn.postTime
            
            // Create unique message key combining sender and message
            val messageKey = "$title|$text"
            val notificationKey = sbn.key
            
            // Check if message is a 1-2 digit number (menu selection)
            val trimmedText = text.trim()
            val isMenuNumber = trimmedText.all { it.isDigit() } && trimmedText.length <= 2 && trimmedText.isNotEmpty()
            
            if (!isMenuNumber) {
                // Regular duplicate checks for non-menu messages
                if (processedMessages.contains(messageKey)) {
                    Log.d(TAG, "Duplicate notification ('$messageKey') ignored")
                    return
                }
                
                if (processedMessages.contains(notificationKey)) {
                    Log.d(TAG, "Notification key already processed: $notificationKey")
                    return
                }
                
                // Time-based duplicate check (within 2 seconds with same content)
                val lastProcessedTime = messageTimestamps[messageKey]
                if (lastProcessedTime != null && (currentTime - lastProcessedTime) < 2000) {
                    Log.d(TAG, "Duplicate within 2 seconds, skipping")
                    return
                }
            } else {
                // For menu numbers, only check if same message was processed very recently (within 500ms)
                // This prevents double notifications but allows menu navigation
                val lastProcessedTime = messageTimestamps[messageKey]
                if (lastProcessedTime != null && (currentTime - lastProcessedTime) < 500) {
                    Log.d(TAG, "Menu number duplicate within 500ms, skipping: $trimmedText")
                    return
                }
                Log.d(TAG, "Menu number detected ($trimmedText), allowing with short duplicate check")
            }
            
            // Mark as processed immediately (always mark to prevent duplicates)
            processedMessages.add(messageKey)
            processedMessages.add(notificationKey)
            messageTimestamps[messageKey] = currentTime
            lastMessageBySender[title] = messageKey
            
            // Extract phone number and sender name
            var phoneNumber: String
            var senderName: String
            
            // Check if title is a phone number using helper function
            if (PhoneNumberExtractor.isPhoneNumber(title)) {
                // Title is phone number (unsaved contact)
                phoneNumber = title.replace(Regex("[^0-9+]"), "")
                senderName = "Unknown"
                Log.d(TAG, "Ã¢Å“â€œ Title is phone number - Phone: $phoneNumber, Name: $senderName")
            } else {
                // Title is name (saved contact)
                senderName = title
                // Try to extract phone number from notification extras first
                phoneNumber = PhoneNumberExtractor.extractFromNotification(extras) 
                    ?: PhoneNumberExtractor.getPhoneNumber(this, title)
                    ?: "Unknown"
                Log.d(TAG, "Ã¢Å“â€œ Title is name - Name: $senderName, Phone: $phoneNumber")
            }
            
            Log.d(TAG, "Ã¢Å“â€œ Final - Sender: $senderName, Phone: $phoneNumber, Message: ${text.take(50)}")
            
            // Save notification data for debug screen
            saveNotificationDebugData(title, text, sbn.packageName, extras)
            
            // Schedule removal of message key after cooldown period (only for exact duplicates)
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                processedMessages.remove(messageKey)
                processedMessages.remove(notificationKey)
                Log.d(TAG, "Removed keys from cooldown: $messageKey")
            }, duplicateMessageCooldownMs)
            
            // Clean old entries (older than 1 minute)
            cleanOldMessages(currentTime)
            
            // Broadcast the notification data
            val intent = Intent(ACTION_NOTIFICATION_RECEIVED).apply {
                putExtra(EXTRA_SENDER_NAME, title)
                putExtra(EXTRA_MESSAGE_TEXT, text)
                putExtra(EXTRA_PACKAGE_NAME, sbn.packageName)
                putExtra(EXTRA_TIMESTAMP, System.currentTimeMillis())
            }
            sendBroadcast(intent)
            
            // Process in background - save to DB and send reply
            serviceScope.launch {
                try {
                    // Insert message into database with PENDING status
                    // Auto-add lead logic is now inside MessageRepository.insertIncomingMessage()
                    val messageId = messageRepository.insertIncomingMessage(
                        phoneNumber = phoneNumber,
                        senderName = senderName,
                        incomingMessage = text,
                        notificationKey = notificationKey
                    )
                    
                    Log.d(TAG, "Ã¢Å“â€œ Message saved to DB - ID: $messageId, Sr: ${messageRepository.getMessageCount()}, Status: PENDING")
                    
                    // Process auto-reply
                    checkAndSendAutoReply(
                        sbn = sbn,
                        incomingMessage = text,
                        messageId = messageId.toInt(),
                        phoneNumber = phoneNumber,
                        senderNameOverride = senderName
                    )
                    
                } catch (e: Exception) {
                    Log.e(TAG, "Error in background processing: ${e.message}")
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error processing WhatsApp notification: ${e.message}")
        }
    }

    private fun processMultiChatSummaryNotification(
        sbn: StatusBarNotification,
        title: String,
        text: String,
        extras: Bundle
    ): Boolean {
        val candidates = buildSummaryConversationCandidates(extras)
        if (candidates.isEmpty()) {
            Log.w(TAG, "Multi-chat summary has no resolvable conversation messages")
            return false
        }

        Log.d(
            TAG,
            "Multi-chat summary detected: title='$title', text='${text.take(60)}', conversations=${candidates.size}"
        )

        var handledCount = 0
        candidates.forEachIndexed { index, candidate ->
            val targetNotification = findActiveConversationNotification(sbn.packageName, candidate)
            if (targetNotification == null) {
                Log.w(
                    TAG,
                    "Multi-chat sender not mapped to active notification: sender='${candidate.senderName}'"
                )
                return@forEachIndexed
            }

            val targetExtras = targetNotification.notification.extras
            val targetTitle = targetExtras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim().orEmpty()
            val messageText = candidate.messageText.trim()
            if (messageText.isBlank()) return@forEachIndexed

            val resolvedPhone = extractPhoneFromNotificationTitleAndExtras(
                title = targetTitle,
                extras = targetExtras,
                fallback = candidate.senderPhone
            ).ifBlank { "Unknown" }

            val resolvedSenderName = when {
                targetTitle.isBlank() -> candidate.senderName
                PhoneNumberExtractor.isPhoneNumber(targetTitle) -> "Unknown"
                else -> targetTitle
            }

            val currentTime = System.currentTimeMillis()
            val senderKey = if (resolvedPhone != "Unknown") resolvedPhone else resolvedSenderName
            val messageKey = "$senderKey|$messageText"
            val notificationKey = "${targetNotification.key}|multi|$index|${messageText.hashCode()}"

            if (processedMessages.contains(messageKey)) {
                Log.d(TAG, "Multi-chat duplicate ignored: '$messageKey'")
                return@forEachIndexed
            }

            if (processedMessages.contains(notificationKey)) {
                Log.d(TAG, "Multi-chat notification key already processed: $notificationKey")
                return@forEachIndexed
            }

            val lastProcessedTime = messageTimestamps[messageKey]
            if (lastProcessedTime != null && (currentTime - lastProcessedTime) < 2000) {
                Log.d(TAG, "Multi-chat duplicate within 2 seconds, skipping: $messageKey")
                return@forEachIndexed
            }

            processedMessages.add(messageKey)
            processedMessages.add(notificationKey)
            messageTimestamps[messageKey] = currentTime
            lastMessageBySender[senderKey] = messageKey

            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                processedMessages.remove(messageKey)
                processedMessages.remove(notificationKey)
                Log.d(TAG, "Removed multi-chat keys from cooldown: $messageKey")
            }, duplicateMessageCooldownMs)

            cleanOldMessages(currentTime)

            saveNotificationDebugData(
                title = if (targetTitle.isNotBlank()) targetTitle else candidate.senderName,
                text = messageText,
                packageName = targetNotification.packageName,
                extras = targetExtras
            )

            serviceScope.launch {
                try {
                    val messageId = messageRepository.insertIncomingMessage(
                        phoneNumber = resolvedPhone,
                        senderName = resolvedSenderName,
                        incomingMessage = messageText,
                        notificationKey = notificationKey
                    )

                    Log.d(
                        TAG,
                        "Ã¢Å“â€œ Multi-chat message saved - ID: $messageId, Sender: $resolvedSenderName, Phone: $resolvedPhone"
                    )

                    checkAndSendAutoReply(
                        sbn = targetNotification,
                        incomingMessage = messageText,
                        messageId = messageId.toInt(),
                        phoneNumber = resolvedPhone,
                        senderNameOverride = resolvedSenderName
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Error processing multi-chat conversation: ${e.message}")
                }
            }

            handledCount++
        }

        Log.d(TAG, "Multi-chat summary processed: $handledCount/${candidates.size} conversations queued")
        return handledCount > 0
    }

    private fun buildSummaryConversationCandidates(extras: Bundle): List<SummaryConversationMessage> {
        val candidates = mutableListOf<SummaryConversationMessage>()
        val seen = mutableSetOf<String>()

        extractSummaryMessagesFromMessagingStyle(extras).forEach { message ->
            addSummaryConversationCandidate(candidates, seen, message)
        }

        val textLines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)
            ?: extras.getCharSequenceArray("android.textLines")

        textLines?.forEach { line ->
            val parsed = parseSummaryTextLine(line?.toString().orEmpty())
            addSummaryConversationCandidate(candidates, seen, parsed)
        }

        return candidates
    }

    private fun extractSummaryMessagesFromMessagingStyle(extras: Bundle): List<SummaryConversationMessage> {
        val rawMessages = extras.getParcelableArray(Notification.EXTRA_MESSAGES)
            ?: extras.getParcelableArray("android.messages")
            ?: return emptyList()

        val messageBundles = rawMessages.mapNotNull { it as? Bundle }.toTypedArray()
        if (messageBundles.isEmpty()) return emptyList()

        return try {
            Notification.MessagingStyle.Message.getMessagesFromBundleArray(messageBundles).mapNotNull { message ->
                val messageText = message.text?.toString()?.trim().orEmpty()
                if (messageText.isBlank()) return@mapNotNull null

                val senderFromPerson = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    message.senderPerson?.name?.toString()?.trim().orEmpty()
                } else {
                    ""
                }
                @Suppress("DEPRECATION")
                val senderFromMessage = message.sender?.toString()?.trim().orEmpty()
                val senderName = senderFromPerson.ifBlank { senderFromMessage }

                if (senderName.isBlank() || senderName.equals("You", ignoreCase = true)) {
                    return@mapNotNull null
                }
                if (isMultiChatSummaryNotification(senderName, messageText)) {
                    return@mapNotNull null
                }

                val personKey = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    message.senderPerson?.key
                } else {
                    null
                }

                val senderPhone = sanitizePhoneForStorage(
                    extractPhoneFromRawValue(personKey).ifBlank {
                        if (PhoneNumberExtractor.isPhoneNumber(senderName)) senderName else ""
                    }
                )

                SummaryConversationMessage(
                    senderName = senderName,
                    messageText = messageText,
                    senderPhone = senderPhone
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing messaging style summary: ${e.message}")
            emptyList()
        }
    }

    private fun parseSummaryTextLine(lineRaw: String): SummaryConversationMessage? {
        val line = lineRaw.trim()
        if (line.isBlank()) return null

        val separator = line.indexOf(':')
        if (separator <= 0 || separator >= line.length - 1) return null

        val senderName = line.substring(0, separator).trim()
        val messageText = line.substring(separator + 1).trim()

        if (senderName.isBlank() || messageText.isBlank()) return null
        if (senderName.equals("You", ignoreCase = true)) return null
        if (isMultiChatSummaryNotification(senderName, messageText)) return null

        return SummaryConversationMessage(
            senderName = senderName,
            messageText = messageText,
            senderPhone = sanitizePhoneForStorage(
                if (PhoneNumberExtractor.isPhoneNumber(senderName)) senderName else ""
            )
        )
    }

    private fun addSummaryConversationCandidate(
        target: MutableList<SummaryConversationMessage>,
        seen: MutableSet<String>,
        candidate: SummaryConversationMessage?
    ) {
        if (candidate == null) return

        val senderName = candidate.senderName.trim()
        val messageText = candidate.messageText.trim()
        if (senderName.isBlank() || messageText.isBlank()) return
        if (isEmojiReactionNotification(messageText)) return

        val dedupeKey = "${normalizeSenderForMatching(senderName)}|${messageText.lowercase()}"
        if (!seen.add(dedupeKey)) return

        target.add(
            candidate.copy(
                senderName = senderName,
                messageText = messageText
            )
        )
    }

    private fun findActiveConversationNotification(
        packageName: String,
        candidate: SummaryConversationMessage
    ): StatusBarNotification? {
        val currentActiveNotifications = activeNotifications
            ?.asSequence()
            ?.filter { it.packageName == packageName }
            ?.sortedByDescending { it.postTime }
            ?.toList()
            .orEmpty()

        if (currentActiveNotifications.isEmpty()) return null

        val targetName = normalizeSenderForMatching(candidate.senderName)
        val targetPhone = normalizePhoneForMatching(
            candidate.senderPhone.ifBlank { candidate.senderName }
        )

        for (activeSbn in currentActiveNotifications) {
            val activeExtras = activeSbn.notification.extras
            val activeTitle = activeExtras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim().orEmpty()
            val activeText = activeExtras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim().orEmpty()

            if (activeTitle.isBlank() || activeTitle.equals("You", ignoreCase = true)) continue
            if (isMultiChatSummaryNotification(activeTitle, activeText)) continue

            val activeName = normalizeSenderForMatching(activeTitle)
            val activePhone = normalizePhoneForMatching(
                extractPhoneFromNotificationTitleAndExtras(activeTitle, activeExtras)
            )
            val activeTitlePhone = normalizePhoneForMatching(activeTitle)

            val isNameMatch = targetName.isNotBlank() && targetName == activeName
            val isPhoneMatch = targetPhone.isNotBlank() &&
                ((activePhone.isNotBlank() && activePhone == targetPhone) || activeTitlePhone == targetPhone)

            if (isNameMatch || isPhoneMatch) {
                return activeSbn
            }
        }

        return null
    }

    private fun extractPhoneFromNotificationTitleAndExtras(
        title: String,
        extras: Bundle,
        fallback: String = ""
    ): String {
        val fromTitle = if (PhoneNumberExtractor.isPhoneNumber(title)) title else ""
        val fromExtras = PhoneNumberExtractor.extractFromNotification(extras).orEmpty()
        val fromContact =
            if (fromTitle.isBlank() && title.isNotBlank()) {
                PhoneNumberExtractor.getPhoneNumber(this, title).orEmpty()
            } else {
                ""
            }

        return sanitizePhoneForStorage(
            when {
                fallback.isNotBlank() -> fallback
                fromTitle.isNotBlank() -> fromTitle
                fromExtras.isNotBlank() -> fromExtras
                fromContact.isNotBlank() -> fromContact
                else -> ""
            }
        )
    }

    private fun extractPhoneFromRawValue(raw: String?): String {
        if (raw.isNullOrBlank()) return ""

        val phoneWithPlus = Regex("\\+\\d{1,4}[\\s\\-]?\\d{6,14}").find(raw)
        if (phoneWithPlus != null) return phoneWithPlus.value

        val phoneDigits = Regex("\\d{10,15}").find(raw)
        return phoneDigits?.value.orEmpty()
    }

    private fun sanitizePhoneForStorage(value: String): String {
        if (value.isBlank()) return ""

        val digits = value.replace(Regex("[^0-9]"), "")
        if (digits.isBlank()) return ""

        return if (value.contains("+")) "+$digits" else digits
    }

    private fun normalizeSenderForMatching(value: String): String {
        return value.lowercase().replace(Regex("[^\\p{L}\\p{Nd}]"), "")
    }

    private fun normalizePhoneForMatching(value: String): String {
        val digits = value.replace(Regex("[^0-9]"), "")
        if (digits.isBlank()) return ""
        return if (digits.length > 10) digits.takeLast(10) else digits
    }
    
    /**
     * Process Instagram notification
     */
    private fun processInstagramNotification(sbn: StatusBarNotification) {
        try {
            val notification = sbn.notification
            val extras = notification.extras
            
            // Extract sender name and message text
            val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim() ?: ""
            val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim() ?: ""
            
            // Skip if sender is "You" (own messages)
            if (title == "You") {
                Log.d(TAG, "Skipping own Instagram message")
                return
            }

            if (isEmojiReactionNotification(text)) {
                Log.d(TAG, "Skipping Instagram emoji reaction: title='$title', text='${text.take(80)}'")
                return
            }
            
            // Skip group notifications and app notifications
            if (title.isEmpty() || text.isEmpty() || 
                title.contains("Instagram") || 
                title.contains("messages") ||
                text.contains("new messages")) {
                return
            }
            
            val currentTime = System.currentTimeMillis()
            val postTime = sbn.postTime
            
            // Create unique message key combining sender and message
            val messageKey = "$title|$text"
            val notificationKey = sbn.key
            
            // Check if message is a 1-2 digit number (menu selection)
            val trimmedText = text.trim()
            val isMenuNumber = trimmedText.all { it.isDigit() } && trimmedText.length <= 2 && trimmedText.isNotEmpty()
            
            if (!isMenuNumber) {
                // Regular duplicate checks for non-menu messages
                if (processedMessages.contains(messageKey)) {
                    Log.d(TAG, "Duplicate Instagram notification ('$messageKey') ignored")
                    return
                }
                
                if (processedMessages.contains(notificationKey)) {
                    Log.d(TAG, "Instagram notification key already processed: $notificationKey")
                    return
                }
                
                // Time-based duplicate check (within 2 seconds with same content)
                val lastProcessedTime = messageTimestamps[messageKey]
                if (lastProcessedTime != null && (currentTime - lastProcessedTime) < 2000) {
                    Log.d(TAG, "Duplicate Instagram notification within 2 seconds, skipping")
                    return
                }
            } else {
                // For menu numbers, only check if same message was processed very recently (within 500ms)
                // This prevents double notifications but allows menu navigation
                val lastProcessedTime = messageTimestamps[messageKey]
                if (lastProcessedTime != null && (currentTime - lastProcessedTime) < 500) {
                    Log.d(TAG, "Instagram menu number duplicate within 500ms, skipping: $trimmedText")
                    return
                }
                Log.d(TAG, "Instagram menu number detected ($trimmedText), allowing with short duplicate check")
            }
            
            // Mark as processed immediately
            processedMessages.add(messageKey)
            processedMessages.add(notificationKey)
            messageTimestamps[messageKey] = currentTime
            lastMessageBySender[title] = messageKey
            
            // For Instagram, we'll use the username as identifier
            val senderName = title
            val phoneNumber = "instagram_$senderName" // Use Instagram username as identifier
            
            Log.d(TAG, "Ã¢Å“â€œ Instagram - Sender: $senderName, Message: ${text.take(50)}")
            
            // Save notification data for debug screen
            saveNotificationDebugData(title, text, sbn.packageName, extras)
            
            // Schedule removal of message key after cooldown period (only for exact duplicates)
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                processedMessages.remove(messageKey)
                processedMessages.remove(notificationKey)
                Log.d(TAG, "Removed Instagram keys from cooldown: $messageKey")
            }, duplicateMessageCooldownMs)
            
            // Clean old entries (older than 1 minute)
            cleanOldMessages(currentTime)
            
            // Broadcast the Instagram notification data
            val intent = Intent(ACTION_INSTAGRAM_NOTIFICATION_RECEIVED).apply {
                putExtra(EXTRA_SENDER_NAME, title)
                putExtra(EXTRA_MESSAGE_TEXT, text)
                putExtra(EXTRA_PACKAGE_NAME, sbn.packageName)
                putExtra(EXTRA_TIMESTAMP, System.currentTimeMillis())
            }
            sendBroadcast(intent)
            
            // Process in background - save to DB and send reply
            serviceScope.launch {
                try {
                    // Insert Instagram message into database with PENDING status
                    val messageId = messageRepository.insertIncomingMessage(
                        phoneNumber = phoneNumber,
                        senderName = senderName,
                        incomingMessage = text,
                        notificationKey = notificationKey
                    )
                    
                    Log.d(TAG, "Ã¢Å“â€œ Instagram message saved to DB - ID: $messageId, Status: PENDING")
                    
                    // Process auto-reply for Instagram
                    checkAndSendAutoReply(
                        sbn = sbn,
                        incomingMessage = text,
                        messageId = messageId.toInt(),
                        phoneNumber = phoneNumber,
                        senderNameOverride = senderName
                    )
                    
                } catch (e: Exception) {
                    Log.e(TAG, "Error in Instagram background processing: ${e.message}")
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error processing Instagram notification: ${e.message}")
        }
    }
    
    /**
     * Check if message matches any keyword and send auto-reply
     * @param phoneNumber - User's phone number for menu context tracking
     */
    private suspend fun checkAndSendAutoReply(
        sbn: StatusBarNotification,
        incomingMessage: String,
        messageId: Int,
        phoneNumber: String = "",
        senderNameOverride: String? = null
    ) {
        try {
            val currentTitle = senderNameOverride?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?: sbn.notification.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()

            if (isWhatsAppBackupNotification(currentTitle, incomingMessage, sbn.notification.extras)) {
                Log.d(TAG, "Ignoring WhatsApp backup notification in auto-reply flow")
                messageRepository.updateMessageWithReply(messageId, "", "IGNORED_BACKUP_NOTIFICATION")
                return
            }

            if (isTransientMediaProgressMessage(incomingMessage)) {
                Log.d(TAG, "Ignoring transient media progress message in auto-reply flow")
                messageRepository.updateMessageWithReply(messageId, "", "IGNORED_TRANSIENT_STATUS")
                return
            }

            if (isEmojiReactionNotification(incomingMessage)) {
                Log.d(TAG, "Ignoring emoji reaction in auto-reply flow")
                messageRepository.updateMessageWithReply(messageId, "", "IGNORED_REACTION")
                return
            }

            // CRITICAL: Check if this is a voice note - BLOCK AI reply until transcription completes
            val isVoiceNote = incomingMessage.contains("Ã°Å¸Å½Â¤") || 
                             incomingMessage.contains("Voice message") || 
                             incomingMessage.contains("voice note", ignoreCase = true) ||
                             incomingMessage.contains("PTT", ignoreCase = true)
            
            if (isVoiceNote) {
                Log.d(TAG, "Ã°Å¸Å½Â¤ Voice note detected - BLOCKING AI reply until transcription completes")
                messageRepository.updateMessageWithReply(messageId, "", "WAITING_TRANSCRIPTION")
                // Don't process any reply - transcription service will trigger AI after completion
                return
            }
            
            // NEW: Check for Owner Assist text interception (strict owner-number only)
            val ownerAssistManager = com.message.bulksend.aiagent.tools.ownerassist.OwnerAssistManager(this)
            val ownerCheckPhone =
                if (phoneNumber.isNotEmpty() && !phoneNumber.equals("Unknown", ignoreCase = true)) {
                    phoneNumber
                } else {
                    ""
                }
            if (ownerAssistManager.isAuthorizedOwner(ownerCheckPhone)) {
                Log.d(TAG, "Ã°Å¸â€ž Text message is from Owner ($ownerCheckPhone)! Diverting to Owner Assist.")

                // Launch coroutine to process AI request without blocking the listener
                serviceScope.launch {
                    val responseMsg = ownerAssistManager.processOwnerInstruction(incomingMessage)
                    messageRepository.updateMessageWithReply(messageId, responseMsg, "SENT")
                    sendReplyViaNotificationNoDelay(
                        sbn = sbn,
                        replyText = responseMsg,
                        messageId = messageId,
                        senderIdentifier = ownerCheckPhone
                    )
                }
                return
            }
            // END Owner Assist Owner Interception

            // Check if auto-respond is enabled
            val autoRespondManager = AutoRespondManager(this)
            if (!autoRespondManager.isAutoRespondEnabled()) {
                Log.d(TAG, "Auto-respond is disabled")
                messageRepository.updateMessageWithReply(messageId, "", "DISABLED")
                return
            }
            
            // Get settings manager
            val settingsManager = com.message.bulksend.autorespond.settings.AutoReplySettingsManager(this)
            
            // Check if this app is enabled for auto-reply
            val packageName = sbn.packageName
            if (!settingsManager.shouldReplyToPackage(packageName)) {
                val appName = when (packageName) {
                    WHATSAPP_PACKAGE -> "WhatsApp"
                    WHATSAPP_BUSINESS_PACKAGE -> "WhatsApp Business"
                    INSTAGRAM_PACKAGE -> "Instagram"
                    else -> "Unknown App"
                }
                Log.d(TAG, "Ã¢ÂÅ’ Auto-reply disabled for $appName")
                messageRepository.updateMessageWithReply(messageId, "", "APP_DISABLED")
                return
            }
            Log.d(TAG, "Ã¢Å“â€œ Auto-reply enabled for package: $packageName")
            
            // Extract sender name from notification
            val senderName = senderNameOverride?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?: sbn.notification.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
                ?: "User"
            
            // Use phone number for user ID, fallback to sender name
            val userId = if (phoneNumber.isNotEmpty() && phoneNumber != "Unknown") phoneNumber else senderName
            
            // Check if sender is in exclude list
            val excludeManager = com.message.bulksend.autorespond.settings.ExcludeNumberManager(this)
            if (excludeManager.shouldExclude(phoneNumber, senderName)) {
                Log.d(TAG, "Ã°Å¸Å¡Â« Sender excluded from auto-reply: $senderName")
                messageRepository.updateMessageWithReply(messageId, "", "EXCLUDED")
                return
            }
            Log.d(TAG, "Ã¢Å“â€œ Sender not in exclude list: $senderName")
            
            // FIRST: Check if user is already in menu flow - need this before cooldown check
            val menuReplyManager = com.message.bulksend.autorespond.menureply.MenuReplyManager(this)
            val userMenuContext = kotlinx.coroutines.runBlocking {
                menuReplyManager.getUserContext(userId)
            }
            val isInMenuFlow = userMenuContext != null && userMenuContext.isActive
            val requiresKeywordRestart = userMenuContext?.requiresKeywordRestart == true
            
            // Check if message is a 1-2 digit number (menu selection)
            val trimmedMessage = incomingMessage.trim()
            val isMenuNumber = trimmedMessage.all { it.isDigit() } && trimmedMessage.length <= 2 && trimmedMessage.isNotEmpty()
            
            Log.d(TAG, "Ã°Å¸â€Â Menu Context Check - UserId: $userId, Context: ${userMenuContext?.currentParentId ?: "null"}, IsActive: ${userMenuContext?.isActive ?: false}, RequiresKeywordRestart: $requiresKeywordRestart, InMenuFlow: $isInMenuFlow, IsMenuNumber: $isMenuNumber")
            
            // Check if we replied to this sender very recently (within 2 seconds to avoid rapid-fire replies)
            // Skip cooldown check if:
            // 1. User is in active menu flow - they need to select options quickly
            // 2. Message is a 1-2 digit number (menu selection)
            val currentTime = System.currentTimeMillis()
            val lastReplyTime = lastReplyTimeBySender[senderName]
            val shouldSkipCooldown = isInMenuFlow || isMenuNumber
            
            if (!shouldSkipCooldown && lastReplyTime != null && (currentTime - lastReplyTime) < senderReplyCooldownMs) {
                Log.d(TAG, "Ã¢ÂÂ³ Cooldown active for $senderName - ${(currentTime - lastReplyTime)/1000}s ago (Menu flow: $isInMenuFlow, Menu number: $isMenuNumber)")
                messageRepository.updateMessageWithReply(messageId, "", "COOLDOWN")
                return
            } else if (shouldSkipCooldown) {
                Log.d(TAG, "Ã°Å¸Å¡â‚¬ Cooldown SKIPPED - User in menu flow: $isInMenuFlow OR menu number: $isMenuNumber")
            } else {
                Log.d(TAG, "Ã¢Å“â€¦ Cooldown check passed - User not in menu flow or no recent reply")
            }
            
            // Check for Welcome Message (first-time contacts)
            val welcomeManager = com.message.bulksend.autorespond.welcomemessage.WelcomeMessageManager(this)
            val shouldSendWelcome = kotlinx.coroutines.runBlocking { welcomeManager.shouldSendWelcome(userId) }
            
            if (shouldSendWelcome) {
                Log.d(TAG, "Ã°Å¸â€˜â€¹ First-time contact $userId - checking welcome messages")
                val welcomeMessages = kotlinx.coroutines.runBlocking { welcomeManager.getWelcomeMessages() }
                
                if (welcomeMessages.isNotEmpty()) {
                    Log.d(TAG, "Ã°Å¸â€œÂ¨ Sending ${welcomeMessages.size} welcome message(s) to $userId")
                    
                    // Mark as sent first to prevent duplicate sends
                    kotlinx.coroutines.runBlocking { welcomeManager.markWelcomeSent(userId) }
                    
                    // Send welcome messages
                    welcomeMessages.forEachIndexed { index, welcomeMsg ->
                        if (index > 0 && welcomeMsg.delayMs > 0) {
                            Thread.sleep(welcomeMsg.delayMs)
                        }

                        if (welcomeMsg.message.isNotBlank()) {
                            sendReplyViaNotification(sbn, welcomeMsg.message, messageId, senderIdentifier = phoneNumber)
                            Log.d(TAG, "Welcome message ${index + 1} text sent: ${welcomeMsg.message.take(30)}...")
                            Thread.sleep(1200)
                        }

                        val queuedDocuments = kotlinx.coroutines.runBlocking {
                            welcomeManager.queueWelcomeDocuments(userId, senderName, welcomeMsg)
                        }

                        if (queuedDocuments > 0) {
                            Log.d(TAG, "Welcome message ${index + 1} queued $queuedDocuments document(s)")
                        }
                    }
                    
                    lastReplyTimeBySender[senderName] = currentTime
                    // Continue to process other replies (don't return)
                }
            }
            
            val replyPriority = settingsManager.getReplyPriority()
            
            Log.d(TAG, "Reply Priority: $replyPriority")
            
            // Process menu selection if user is in menu flow
            if (isInMenuFlow) {
                Log.d(TAG, "Ã°Å¸â€œâ€¹ User $userId is in menu flow, processing menu selection...")
                val menuReply = kotlinx.coroutines.runBlocking {
                    // Force enabled skips the global enabled check but keeps the current submenu context
                    menuReplyManager.findMenuReplyForUser(userId, incomingMessage, forceEnabled = true)
                }
                
                when (menuReply) {
                    is com.message.bulksend.autorespond.menureply.MenuReplyResult.MenuResponse -> {
                        Log.d(TAG, "Ã¢Å“â€œ Menu submenu: ${menuReply.menuText.take(50)}")
                        lastReplyTimeBySender[senderName] = currentTime
                        sendReplyViaNotification(sbn, menuReply.menuText, messageId, senderIdentifier = phoneNumber)
                        return
                    }
                    is com.message.bulksend.autorespond.menureply.MenuReplyResult.FinalResponse -> {
                        Log.d(TAG, "Ã¢Å“â€œ Menu final: ${menuReply.responseText.take(50)}")
                        lastReplyTimeBySender[senderName] = currentTime
                        sendReplyViaNotification(sbn, menuReply.responseText, messageId, senderIdentifier = phoneNumber)
                        return
                    }
                    is com.message.bulksend.autorespond.menureply.MenuReplyResult.SessionExpired -> {
                        Log.d(TAG, "Menu session expired for user: $userId")
                        lastReplyTimeBySender[senderName] = currentTime
                        sendReplyViaNotification(sbn, menuReply.message, messageId, senderIdentifier = phoneNumber)
                        return
                    }
                    else -> {
                        Log.d(TAG, "Menu flow result: $menuReply, continuing to keyword check...")
                    }
                }
            }
            
            // Check for post-selection reply (after final selection) - INDEPENDENT of menu reply being enabled
            if (!isInMenuFlow && isMenuNumber && !requiresKeywordRestart) {
                Log.d(TAG, "Ã°Å¸â€Â Checking post-selection reply for user $userId with number: $trimmedMessage")
                val menuReplyManager = com.message.bulksend.autorespond.menureply.MenuReplyManager(this)
                val settingsManager = com.message.bulksend.autorespond.menureply.MenuReplySettingsManager(this)
                val settings = settingsManager.getSettings()
                
                if (settings.postSelectionEnabled) {
                    Log.d(TAG, "Ã¢Å“â€¦ Post-selection enabled, type: ${settings.postSelectionType}")
                    
                    when (settings.postSelectionType) {
                        com.message.bulksend.autorespond.menureply.PostSelectionReplyType.NO_REPLY -> {
                            Log.d(TAG, "Post-selection: No reply")
                            messageRepository.updateMessageWithReply(messageId, "", "POST_SELECTION_NO_REPLY")
                            return
                        }
                        com.message.bulksend.autorespond.menureply.PostSelectionReplyType.MAIN_MENU -> {
                            Log.d(TAG, "Post-selection: Showing main menu")
                            val menuReply = kotlinx.coroutines.runBlocking {
                                menuReplyManager.findMenuReplyForUser(userId, "menu", forceEnabled = true)
                            }
                            
                            when (menuReply) {
                                is com.message.bulksend.autorespond.menureply.MenuReplyResult.MenuResponse -> {
                                    Log.d(TAG, "Ã¢Å“â€œ Post-selection main menu: ${menuReply.menuText.take(50)}")
                                    lastReplyTimeBySender[senderName] = currentTime
                                    sendReplyViaNotification(sbn, menuReply.menuText, messageId, senderIdentifier = phoneNumber)
                                    return
                                }
                                else -> {
                                    Log.d(TAG, "Ã¢Å“â€” Post-selection main menu failed: $menuReply")
                                }
                            }
                        }
                        com.message.bulksend.autorespond.menureply.PostSelectionReplyType.CUSTOM_MESSAGE -> {
                            Log.d(TAG, "Post-selection: Custom message")
                            lastReplyTimeBySender[senderName] = currentTime
                            sendReplyViaNotification(sbn, settings.postSelectionMessage, messageId, senderIdentifier = phoneNumber)
                            return
                        }
                    }
                } else {
                    Log.d(TAG, "Ã¢ÂÅ’ Post-selection disabled")
                }
            }
            
            // Priority-based reply logic
            when (replyPriority) {
                com.message.bulksend.autorespond.settings.ReplyPriority.KEYWORD_FIRST -> {
                    // Try keyword first, then spreadsheet, then AI if no match
                    if (settingsManager.shouldUseKeywordReply()) {
                        val keywordReplyManager = com.message.bulksend.autorespond.keywordreply.KeywordReplyManager(this)
                        val matchingReply = keywordReplyManager.findMatchingReply(incomingMessage)
                        
                        if (matchingReply != null) {
                            Log.d(TAG, "Ã¢Å“â€œ Keyword match found: ${matchingReply.incomingKeyword}, replyOption: ${matchingReply.replyOption}")
                            lastReplyTimeBySender[senderName] = currentTime
                            
                            // Check if reply option is "menu" - trigger menu reply instead
                            if (matchingReply.replyOption == "menu") {
                                Log.d(TAG, "Ã°Å¸â€â‚¬ Keyword triggers Menu Reply for user: $userId")
                                val menuReply = kotlinx.coroutines.runBlocking {
                                    menuReplyManager.findMenuReplyForUser(userId, "menu", forceEnabled = true)
                                }
                                
                                when (menuReply) {
                                    is com.message.bulksend.autorespond.menureply.MenuReplyResult.MenuResponse -> {
                                        Log.d(TAG, "Ã¢Å“â€œ Menu reply text: ${menuReply.menuText.take(50)}")
                                        sendReplyViaNotification(sbn, menuReply.menuText, messageId, senderIdentifier = phoneNumber)
                                    }
                                    is com.message.bulksend.autorespond.menureply.MenuReplyResult.FinalResponse -> {
                                        Log.d(TAG, "Ã¢Å“â€œ Menu final response: ${menuReply.responseText.take(50)}")
                                        sendReplyViaNotification(sbn, menuReply.responseText, messageId, senderIdentifier = phoneNumber)
                                    }
                                    is com.message.bulksend.autorespond.menureply.MenuReplyResult.NoOptions -> {
                                        Log.d(TAG, "Ã¢Å“â€” No menu options configured")
                                        messageRepository.updateMessageWithReply(messageId, "", "MENU_NO_OPTIONS")
                                    }
                                    else -> {
                                        Log.d(TAG, "Ã¢Å“â€” Menu not available, result: $menuReply")
                                        if (matchingReply.replyMessage.isNotBlank()) {
                                            sendReplyViaNotification(sbn, matchingReply.replyMessage, messageId, senderIdentifier = phoneNumber)
                                        } else {
                                            messageRepository.updateMessageWithReply(messageId, "", "MENU_NOT_CONFIGURED")
                                        }
                                    }
                                }
                            } else {
                                sendReplyViaNotification(sbn, matchingReply.replyMessage, messageId, senderIdentifier = phoneNumber)
                            }
                            return
                        } else {
                            Log.d(TAG, "Ã¢Å“â€” No keyword match, checking document reply...")
                        }
                    }
                    
                    // No keyword match, try document reply
                    if (settingsManager.shouldUseDocumentReply()) {
                        val documentReplyManager = com.message.bulksend.autorespond.documentreply.DocumentReplyManager(this)
                        val documentReplyResult = documentReplyManager.findMatchingDocumentReply(incomingMessage)
                        
                        when (documentReplyResult) {
                            is com.message.bulksend.autorespond.documentreply.DocumentReplyResult.Match -> {
                                Log.d(TAG, "Ã¢Å“â€œ Document reply match found: ${documentReplyResult.reply.keyword}")
                                lastReplyTimeBySender[senderName] = currentTime

                                val documentPolicy = evaluateAutoReplyLimit(
                                    replyText = "",
                                    senderIdentifier = phoneNumber,
                                    isDocumentReply = true
                                )
                                if (!documentPolicy.allowSend) {
                                    sendReplyViaNotification(
                                        sbn = sbn,
                                        replyText = documentPolicy.finalReplyText,
                                        messageId = messageId,
                                        senderIdentifier = phoneNumber
                                    )
                                    return
                                }

                                if (documentPolicy.incrementUsage) {
                                    incrementAutoReplyUsageCount(phoneNumber)
                                }
                                
                                // Enable document send service
                                com.message.bulksend.autorespond.documentreply.DocumentSendService.enableDocumentSend()
                                
                                // Add to document send queue
                                val documentSendService = com.message.bulksend.autorespond.documentreply.DocumentSendService.getInstance()
                                documentSendService.addDocumentSendTask(
                                    context = this,
                                    phoneNumber = phoneNumber,
                                    senderName = senderName,
                                    keyword = documentReplyResult.reply.keyword,
                                    documents = documentReplyResult.reply.documents,
                                    documentPaths = documentReplyResult.reply.getAllDocumentPaths()
                                )
                                
                                // NO TEXT REPLY - Only document send via accessibility
                                messageRepository.updateMessageWithReply(messageId, "", "DOCUMENT_QUEUED")
                                return
                            }
                            is com.message.bulksend.autorespond.documentreply.DocumentReplyResult.Error -> {
                                Log.w(TAG, "Ã¢Å¡Â Ã¯Â¸Â Document reply error: ${documentReplyResult.message}")
                                if (documentReplyResult.message.contains("locked", ignoreCase = true)) {
                                    Log.w(TAG, "Ã°Å¸â€â€™ Document reply blocked due to lock screen")
                                    // Don't process further - let other reply methods handle it
                                }
                            }
                            else -> {
                                Log.d(TAG, "Ã¢Å“â€” No document reply match, checking spreadsheet...")
                            }
                        }
                    }
                    
                    // No document match, try spreadsheet
                    if (settingsManager.shouldUseSpreadsheetReply()) {
                        val matchingReply = findSpreadsheetReply(incomingMessage)
                        
                        if (matchingReply != null) {
                            Log.d(TAG, "Ã¢Å“â€œ Spreadsheet match found")
                            lastReplyTimeBySender[senderName] = currentTime
                            sendReplyViaNotification(sbn, matchingReply, messageId, senderIdentifier = phoneNumber)
                            return
                        } else {
                            Log.d(TAG, "Ã¢Å“â€” No spreadsheet match, checking AI fallback...")
                        }
                    }
                    
                    // No match, try AI fallback
                    if (settingsManager.shouldUseAIAsFallback()) {
                        Log.d(TAG, "Ã°Å¸Â¤â€“ Using AI as fallback")
                        lastReplyTimeBySender[senderName] = currentTime
                        val aiReplyManager = com.message.bulksend.autorespond.aireply.AIReplyManager(this)
                        generateAndSendAIReply(
                            sbn = sbn,
                            incomingMessage = incomingMessage,
                            senderName = senderName,
                            aiReplyManager = aiReplyManager,
                            messageId = messageId,
                            senderPhoneOverride = phoneNumber
                        )
                    } else {
                        Log.d(TAG, "Ã¢Å“â€” No match found and AI fallback disabled")
                        messageRepository.updateMessageWithReply(messageId, "", "NO_MATCH")
                    }
                }
                
                com.message.bulksend.autorespond.settings.ReplyPriority.AI_ONLY -> {
                    // Always use AI, ignore keywords
                    if (settingsManager.shouldUseAIReply()) {
                        Log.d(TAG, "Ã°Å¸Â¤â€“ AI Only mode - generating response...")
                        lastReplyTimeBySender[senderName] = currentTime
                        val aiReplyManager = com.message.bulksend.autorespond.aireply.AIReplyManager(this)
                        generateAndSendAIReply(
                            sbn = sbn,
                            incomingMessage = incomingMessage,
                            senderName = senderName,
                            aiReplyManager = aiReplyManager,
                            messageId = messageId,
                            senderPhoneOverride = phoneNumber
                        )
                    } else {
                        Log.d(TAG, "AI Reply is disabled")
                        messageRepository.updateMessageWithReply(messageId, "", "DISABLED")
                    }
                }
                
                com.message.bulksend.autorespond.settings.ReplyPriority.SPREADSHEET_FIRST -> {
                    // Try spreadsheet first, then keyword, then AI if no match
                    if (settingsManager.shouldUseSpreadsheetReply()) {
                        val matchingReply = findSpreadsheetReply(incomingMessage)
                        
                        if (matchingReply != null) {
                            Log.d(TAG, "Ã¢Å“â€œ Spreadsheet match found")
                            lastReplyTimeBySender[senderName] = currentTime
                            sendReplyViaNotification(sbn, matchingReply, messageId, senderIdentifier = phoneNumber)
                            return
                        } else {
                            Log.d(TAG, "Ã¢Å“â€” No spreadsheet match, checking keyword...")
                        }
                    }
                    
                    // No spreadsheet match, try keyword
                    if (settingsManager.shouldUseKeywordReply()) {
                        val keywordReplyManager = com.message.bulksend.autorespond.keywordreply.KeywordReplyManager(this)
                        val matchingReply = keywordReplyManager.findMatchingReply(incomingMessage)
                        
                        if (matchingReply != null) {
                            Log.d(TAG, "Ã¢Å“â€œ Keyword match found: ${matchingReply.incomingKeyword}, replyOption: ${matchingReply.replyOption}")
                            lastReplyTimeBySender[senderName] = currentTime
                            
                            // Check if reply option is "menu" - trigger menu reply instead
                            if (matchingReply.replyOption == "menu") {
                                Log.d(TAG, "Ã°Å¸â€â‚¬ Keyword triggers Menu Reply for user: $userId")
                                val menuReplyManager = com.message.bulksend.autorespond.menureply.MenuReplyManager(this)
                                val menuReply = menuReplyManager.findMenuReplyForUser(userId, "menu", forceEnabled = true)
                                
                                when (menuReply) {
                                    is com.message.bulksend.autorespond.menureply.MenuReplyResult.MenuResponse -> {
                                        sendReplyViaNotification(sbn, menuReply.menuText, messageId, senderIdentifier = phoneNumber)
                                    }
                                    is com.message.bulksend.autorespond.menureply.MenuReplyResult.FinalResponse -> {
                                        sendReplyViaNotification(sbn, menuReply.responseText, messageId, senderIdentifier = phoneNumber)
                                    }
                                    else -> {
                                        sendReplyViaNotification(sbn, matchingReply.replyMessage, messageId, senderIdentifier = phoneNumber)
                                    }
                                }
                            } else {
                                sendReplyViaNotification(sbn, matchingReply.replyMessage, messageId, senderIdentifier = phoneNumber)
                            }
                            return
                        } else {
                            Log.d(TAG, "Ã¢Å“â€” No keyword match, checking AI fallback...")
                        }
                    }
                    
                    // No match, try AI fallback
                    if (settingsManager.shouldUseAIAsFallback()) {
                        Log.d(TAG, "Ã°Å¸Â¤â€“ Using AI as fallback")
                        lastReplyTimeBySender[senderName] = currentTime
                        val aiReplyManager = com.message.bulksend.autorespond.aireply.AIReplyManager(this)
                        generateAndSendAIReply(
                            sbn = sbn,
                            incomingMessage = incomingMessage,
                            senderName = senderName,
                            aiReplyManager = aiReplyManager,
                            messageId = messageId,
                            senderPhoneOverride = phoneNumber
                        )
                    } else {
                        Log.d(TAG, "Ã¢Å“â€” No match found and AI fallback disabled")
                        messageRepository.updateMessageWithReply(messageId, "", "NO_MATCH")
                    }
                }
                
                com.message.bulksend.autorespond.settings.ReplyPriority.KEYWORD_ONLY -> {
                    // Only use keywords and documents, no AI - supports menu reply trigger
                    if (settingsManager.shouldUseKeywordReply()) {
                        val keywordReplyManager = com.message.bulksend.autorespond.keywordreply.KeywordReplyManager(this)
                        val matchingReply = keywordReplyManager.findMatchingReply(incomingMessage)
                        
                        if (matchingReply != null) {
                            Log.d(TAG, "Ã¢Å“â€œ Keyword match found: ${matchingReply.incomingKeyword}, replyOption: ${matchingReply.replyOption}")
                            lastReplyTimeBySender[senderName] = currentTime
                            
                            // Check if reply option is "menu" - trigger menu reply instead
                            if (matchingReply.replyOption == "menu") {
                                Log.d(TAG, "Ã°Å¸â€â‚¬ Keyword triggers Menu Reply for user: $userId")
                                val menuReplyManager = com.message.bulksend.autorespond.menureply.MenuReplyManager(this)
                                val menuReply = kotlinx.coroutines.runBlocking {
                                    menuReplyManager.findMenuReplyForUser(userId, "menu", forceEnabled = true) // Force enabled, start fresh menu
                                }
                                
                                when (menuReply) {
                                    is com.message.bulksend.autorespond.menureply.MenuReplyResult.MenuResponse -> {
                                        Log.d(TAG, "Ã¢Å“â€œ Menu reply text: ${menuReply.menuText.take(50)}")
                                        sendReplyViaNotification(sbn, menuReply.menuText, messageId, senderIdentifier = phoneNumber)
                                    }
                                    is com.message.bulksend.autorespond.menureply.MenuReplyResult.FinalResponse -> {
                                        Log.d(TAG, "Ã¢Å“â€œ Menu final response: ${menuReply.responseText.take(50)}")
                                        sendReplyViaNotification(sbn, menuReply.responseText, messageId, senderIdentifier = phoneNumber)
                                    }
                                    is com.message.bulksend.autorespond.menureply.MenuReplyResult.NoOptions -> {
                                        Log.d(TAG, "Ã¢Å“â€” No menu options configured")
                                        messageRepository.updateMessageWithReply(messageId, "", "MENU_NO_OPTIONS")
                                    }
                                    else -> {
                                        // Fallback to reply message if menu not available
                                        Log.d(TAG, "Ã¢Å“â€” Menu not available, result: $menuReply")
                                        if (matchingReply.replyMessage.isNotBlank()) {
                                            sendReplyViaNotification(sbn, matchingReply.replyMessage, messageId, senderIdentifier = phoneNumber)
                                        } else {
                                            messageRepository.updateMessageWithReply(messageId, "", "MENU_NOT_CONFIGURED")
                                        }
                                    }
                                }
                            } else {
                                sendReplyViaNotification(sbn, matchingReply.replyMessage, messageId, senderIdentifier = phoneNumber)
                            }
                            return
                        } else {
                            Log.d(TAG, "Ã¢Å“â€” No keyword match, checking document reply...")
                        }
                    }
                    
                    // No keyword match, try document reply
                    if (settingsManager.shouldUseDocumentReply()) {
                        val documentReplyManager = com.message.bulksend.autorespond.documentreply.DocumentReplyManager(this)
                        val documentReplyResult = documentReplyManager.findMatchingDocumentReply(incomingMessage)
                        
                        when (documentReplyResult) {
                            is com.message.bulksend.autorespond.documentreply.DocumentReplyResult.Match -> {
                                Log.d(TAG, "Ã¢Å“â€œ Document reply match found: ${documentReplyResult.reply.keyword}")
                                lastReplyTimeBySender[senderName] = currentTime

                                val documentPolicy = evaluateAutoReplyLimit(
                                    replyText = "",
                                    senderIdentifier = phoneNumber,
                                    isDocumentReply = true
                                )
                                if (!documentPolicy.allowSend) {
                                    sendReplyViaNotification(
                                        sbn = sbn,
                                        replyText = documentPolicy.finalReplyText,
                                        messageId = messageId,
                                        senderIdentifier = phoneNumber
                                    )
                                    return
                                }

                                if (documentPolicy.incrementUsage) {
                                    incrementAutoReplyUsageCount(phoneNumber)
                                }
                                
                                // Enable document send service
                                com.message.bulksend.autorespond.documentreply.DocumentSendService.enableDocumentSend()
                                
                                // Add to document send queue
                                val documentSendService = com.message.bulksend.autorespond.documentreply.DocumentSendService.getInstance()
                                documentSendService.addDocumentSendTask(
                                    context = this,
                                    phoneNumber = phoneNumber,
                                    senderName = senderName,
                                    keyword = documentReplyResult.reply.keyword,
                                    documents = documentReplyResult.reply.documents,
                                    documentPaths = documentReplyResult.reply.getAllDocumentPaths()
                                )
                                
                                // NO TEXT REPLY - Only document send via accessibility
                                messageRepository.updateMessageWithReply(messageId, "", "DOCUMENT_QUEUED")
                                return
                            }
                            is com.message.bulksend.autorespond.documentreply.DocumentReplyResult.Error -> {
                                Log.w(TAG, "Ã¢Å¡Â Ã¯Â¸Â Document reply error: ${documentReplyResult.message}")
                                if (documentReplyResult.message.contains("locked", ignoreCase = true)) {
                                    Log.w(TAG, "Ã°Å¸â€â€™ Document reply blocked due to lock screen")
                                    messageRepository.updateMessageWithReply(messageId, "", "LOCKED_SCREEN")
                                } else {
                                    messageRepository.updateMessageWithReply(messageId, "", "ERROR")
                                }
                            }
                            else -> {
                                Log.d(TAG, "Ã¢Å“â€” No document reply match found (Keyword Only mode)")
                                messageRepository.updateMessageWithReply(messageId, "", "NO_MATCH")
                            }
                        }
                    } else {
                        Log.d(TAG, "Ã¢Å“â€” No keyword match found (Keyword Only mode)")
                        messageRepository.updateMessageWithReply(messageId, "", "NO_MATCH")
                    }
                }
                
                com.message.bulksend.autorespond.settings.ReplyPriority.SPREADSHEET_ONLY -> {
                    // Only use spreadsheet, no AI or keywords
                    if (settingsManager.shouldUseSpreadsheetReply()) {
                        val matchingReply = findSpreadsheetReply(incomingMessage)
                        
                        if (matchingReply != null) {
                            Log.d(TAG, "Ã¢Å“â€œ Spreadsheet match found")
                            lastReplyTimeBySender[senderName] = currentTime
                            sendReplyViaNotification(sbn, matchingReply, messageId, senderIdentifier = phoneNumber)
                        } else {
                            Log.d(TAG, "Ã¢Å“â€” No spreadsheet match found (Spreadsheet Only mode)")
                            messageRepository.updateMessageWithReply(messageId, "", "NO_MATCH")
                        }
                    } else {
                        Log.d(TAG, "Spreadsheet Reply is disabled")
                        messageRepository.updateMessageWithReply(messageId, "", "DISABLED")
                    }
                }
                
                com.message.bulksend.autorespond.settings.ReplyPriority.MENU_FIRST -> {
                    // Try menu first, then keyword, then spreadsheet, then AI if no match
                    if (settingsManager.shouldUseMenuReply()) {
                        val menuReplyManager = com.message.bulksend.autorespond.menureply.MenuReplyManager(this)
                        val menuReply = kotlinx.coroutines.runBlocking { 
                            menuReplyManager.findMenuReplyForUser(userId, incomingMessage) 
                        }
                        
                        when (menuReply) {
                            is com.message.bulksend.autorespond.menureply.MenuReplyResult.MenuResponse -> {
                                Log.d(TAG, "Ã¢Å“â€œ Menu reply found for user: $userId")
                                lastReplyTimeBySender[senderName] = currentTime
                                sendReplyViaNotification(sbn, menuReply.menuText, messageId, senderIdentifier = phoneNumber)
                                return
                            }
                            is com.message.bulksend.autorespond.menureply.MenuReplyResult.FinalResponse -> {
                                Log.d(TAG, "Ã¢Å“â€œ Menu final selection for user: $userId")
                                lastReplyTimeBySender[senderName] = currentTime
                                sendReplyViaNotification(sbn, menuReply.responseText, messageId, senderIdentifier = phoneNumber)
                                return
                            }
                            is com.message.bulksend.autorespond.menureply.MenuReplyResult.SessionExpired -> {
                                Log.d(TAG, "Menu session expired for user: $userId")
                                lastReplyTimeBySender[senderName] = currentTime
                                sendReplyViaNotification(sbn, menuReply.message, messageId, senderIdentifier = phoneNumber)
                                return
                            }
                            else -> {
                                Log.d(TAG, "Ã¢Å“â€” No menu match, checking keyword...")
                            }
                        }
                    }
                    
                    // No menu match, try keyword
                    if (settingsManager.shouldUseKeywordReply()) {
                        val keywordReplyManager = com.message.bulksend.autorespond.keywordreply.KeywordReplyManager(this)
                        val matchingReply = keywordReplyManager.findMatchingReply(incomingMessage)
                        
                        if (matchingReply != null) {
                            Log.d(TAG, "Ã¢Å“â€œ Keyword match found: ${matchingReply.incomingKeyword}")
                            lastReplyTimeBySender[senderName] = currentTime
                            sendReplyViaNotification(sbn, matchingReply.replyMessage, messageId, senderIdentifier = phoneNumber)
                            return
                        }
                    }
                    
                    // No keyword match, try spreadsheet
                    if (settingsManager.shouldUseSpreadsheetReply()) {
                        val matchingReply = findSpreadsheetReply(incomingMessage)
                        
                        if (matchingReply != null) {
                            Log.d(TAG, "Ã¢Å“â€œ Spreadsheet match found")
                            lastReplyTimeBySender[senderName] = currentTime
                            sendReplyViaNotification(sbn, matchingReply, messageId, senderIdentifier = phoneNumber)
                            return
                        }
                    }
                    
                    // No match, try AI fallback
                    if (settingsManager.shouldUseAIAsFallback()) {
                        Log.d(TAG, "Ã°Å¸Â¤â€“ Using AI as fallback")
                        lastReplyTimeBySender[senderName] = currentTime
                        val aiReplyManager = com.message.bulksend.autorespond.aireply.AIReplyManager(this)
                        generateAndSendAIReply(
                            sbn = sbn,
                            incomingMessage = incomingMessage,
                            senderName = senderName,
                            aiReplyManager = aiReplyManager,
                            messageId = messageId,
                            senderPhoneOverride = phoneNumber
                        )
                    } else {
                        Log.d(TAG, "Ã¢Å“â€” No match found and AI fallback disabled")
                        messageRepository.updateMessageWithReply(messageId, "", "NO_MATCH")
                    }
                }
                
                com.message.bulksend.autorespond.settings.ReplyPriority.MENU_ONLY -> {
                    // Only use menu reply
                    if (settingsManager.shouldUseMenuReply()) {
                        val menuReplyManager = com.message.bulksend.autorespond.menureply.MenuReplyManager(this)
                        val menuReply = kotlinx.coroutines.runBlocking { 
                            menuReplyManager.findMenuReplyForUser(userId, incomingMessage) 
                        }
                        
                        when (menuReply) {
                            is com.message.bulksend.autorespond.menureply.MenuReplyResult.MenuResponse -> {
                                Log.d(TAG, "Ã¢Å“â€œ Menu reply found for user: $userId")
                                lastReplyTimeBySender[senderName] = currentTime
                                sendReplyViaNotification(sbn, menuReply.menuText, messageId, senderIdentifier = phoneNumber)
                            }
                            is com.message.bulksend.autorespond.menureply.MenuReplyResult.FinalResponse -> {
                                Log.d(TAG, "Ã¢Å“â€œ Menu final selection for user: $userId")
                                lastReplyTimeBySender[senderName] = currentTime
                                sendReplyViaNotification(sbn, menuReply.responseText, messageId, senderIdentifier = phoneNumber)
                            }
                            is com.message.bulksend.autorespond.menureply.MenuReplyResult.SessionExpired -> {
                                Log.d(TAG, "Menu session expired for user: $userId")
                                lastReplyTimeBySender[senderName] = currentTime
                                sendReplyViaNotification(sbn, menuReply.message, messageId, senderIdentifier = phoneNumber)
                            }
                            else -> {
                                Log.d(TAG, "Ã¢Å“â€” No menu match found (Menu Only mode)")
                                messageRepository.updateMessageWithReply(messageId, "", "NO_MATCH")
                            }
                        }
                    } else {
                        Log.d(TAG, "Menu Reply is disabled")
                        messageRepository.updateMessageWithReply(messageId, "", "DISABLED")
                    }
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error in auto-reply: ${e.message}")
            messageRepository.updateMessageWithReply(messageId, "", "ERROR")
        }
    }
    
    /**
     * Generate AI reply and send it (UPDATED: No 60s cooldown, Room DB check for duplicates)
     */
    private fun generateAndSendAIReply(
        sbn: StatusBarNotification, 
        incomingMessage: String, 
        senderName: String,
        aiReplyManager: com.message.bulksend.autorespond.aireply.AIReplyManager,
        messageId: Int,
        senderPhoneOverride: String = ""
    ) {
        val senderKey = senderName
        val messageKey = "$senderKey|$incomingMessage"
        
        // Check Room DB for same message reply in last 20 seconds
        serviceScope.launch {
            try {
                val resolvedSenderPhone = senderPhoneOverride
                    .takeIf { it.isNotBlank() && it != "Unknown" }
                    ?: extractPhoneFromSender(senderName)

                val lastSameMessage = messageRepository.getLastMessageByPhoneAndText(
                    resolvedSenderPhone,
                    incomingMessage
                )
                
                if (lastSameMessage != null && lastSameMessage.status == "SENT") {
                    val timeDiff = System.currentTimeMillis() - lastSameMessage.timestamp
                    if (timeDiff < 20000) { // 20 seconds
                        Log.d(TAG, "Ã°Å¸Å¡Â« Same message replied ${timeDiff/1000}s ago - skipping duplicate")
                        messageRepository.updateMessageWithReply(messageId, "", "RECENT_DUPLICATE")
                        return@launch
                    }
                }
                
                Log.d(TAG, "Ã°Å¸Â¤â€“ Starting AI reply generation for message: $messageKey")
                
                // Check if delay is enabled - coordinate with delay system
                val delayManager = com.message.bulksend.autorespond.settings.ReplyDelayManager(this@WhatsAppNotificationListener)
                val delayMs = delayManager.getDelayMillis()
                
                if (delayMs > 0) {
                    Log.d(TAG, "Ã¢ÂÂ³ AI reply will be generated after delay: ${delayMs}ms for message: $messageKey")
                    
                    // Schedule AI generation after delay
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        generateAIReplyAfterDelay(
                            sbn = sbn,
                            incomingMessage = incomingMessage,
                            senderName = senderName,
                            senderPhone = resolvedSenderPhone,
                            aiReplyManager = aiReplyManager,
                            messageId = messageId,
                            messageKey = messageKey
                        )
                    }, delayMs)
                } else {
                    // No delay - generate immediately
                    generateAIReplyAfterDelay(
                        sbn = sbn,
                        incomingMessage = incomingMessage,
                        senderName = senderName,
                        senderPhone = resolvedSenderPhone,
                        aiReplyManager = aiReplyManager,
                        messageId = messageId,
                        messageKey = messageKey
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error checking duplicate in DB: ${e.message}")
            }
        }
    }
    
    /**
     * Extract phone number from sender name for DB lookup
     */
    private fun extractPhoneFromSender(senderName: String): String {
        // If sender name is already a phone number, return it
        if (PhoneNumberExtractor.isPhoneNumber(senderName)) {
            return senderName.replace(Regex("[^0-9+]"), "")
        }
        // For contact names, we'll use the sender name as identifier
        return senderName
    }
    
    /**
     * Generate AI reply after delay (or immediately if no delay)
     */
    private fun generateAIReplyAfterDelay(
        sbn: StatusBarNotification, 
        incomingMessage: String, 
        senderName: String,
        senderPhone: String,
        aiReplyManager: com.message.bulksend.autorespond.aireply.AIReplyManager,
        messageId: Int,
        messageKey: String
    ) {
        // Run in background thread
        Thread {
            try {
                val provider = aiReplyManager.getSelectedProvider()
                val aiService = com.message.bulksend.autorespond.aireply.AIService(this)
                
                Log.d(TAG, "Generating AI reply with provider: ${provider.displayName}")
                
                // Check subscription for ChatsPromo AI provider
                if (false && provider == com.message.bulksend.autorespond.aireply.AIProvider.CHATSPROMO) {
                    val subscriptionManager = com.message.bulksend.autorespond.aireply.ChatsPromoAISubscriptionManager(this)
                    
                    // Sync from Firestore and check subscription
                    kotlinx.coroutines.runBlocking {
                        subscriptionManager.syncFromFirestore()
                    }
                    
                    if (!subscriptionManager.canUseAI()) {
                        Log.d(TAG, "Ã¢ÂÅ’ ChatsPromo AI subscription expired or not active")
                        serviceScope.launch {
                            messageRepository.updateMessageWithReply(messageId, "", "SUBSCRIPTION_EXPIRED")
                        }
                        return@Thread
                    }
                    Log.d(TAG, "Ã¢Å“â€œ ChatsPromo AI subscription active")
                }
                
                // Generate reply using coroutine WITH phone number
                kotlinx.coroutines.runBlocking {
                    try {
                        val aiReply = aiService.generateReply(provider, incomingMessage, senderName, senderPhone)
                        
                        // Check for specific error conditions
                        if (aiReply.startsWith("Error") || 
                            aiReply.startsWith("AI not configured") || 
                            aiReply.startsWith("SUBSCRIPTION_REQUIRED") ||
                            aiReply.contains("blocked by safety")) {
                            
                            Log.e(TAG, "Ã¢ÂÅ’ AI Generation Failed: $aiReply")
                            showErrorNotification(aiReply)
                            
                            serviceScope.launch {
                                messageRepository.updateMessageWithReply(messageId, "", "AI_GENERATION_FAILED")
                            }
                            return@runBlocking
                        }
                        
                        Log.d(TAG, "Ã¢Å“â€¦ AI reply generated for message: $messageKey - ${aiReply.take(50)}...")
                        
                        // Send the AI reply WITHOUT additional delay (delay already applied)
                        sendReplyViaNotificationNoDelay(
                            sbn = sbn,
                            replyText = aiReply,
                            messageId = messageId,
                            senderIdentifier = senderPhone
                        )
                        
                        // Check if voice reply should be sent
                        Thread.sleep(500) // Small delay before checking voice
                        
                        Log.d(TAG, "Ã°Å¸Å½Â¤ Checking if voice reply is enabled...")
                        try {
                            // Initialize speech settings FIRST before checking
                            val speechManager = com.message.bulksend.aiagent.tools.agentspeech.AgentSpeechManager.getInstance(this@WhatsAppNotificationListener)
                            speechManager.initializeSettings()  // Ensure settings exist
                            
                            // Get current settings to log (use runBlocking for synchronous call)
                            val currentSettings = kotlinx.coroutines.runBlocking {
                                speechManager.getSettings().first()
                            }
                            Log.d(TAG, "Ã°Å¸Å½Â¤ Current settings: enabled=${currentSettings?.isEnabled}, language=${currentSettings?.language}")
                            
                            val speechIntegration = com.message.bulksend.aiagent.tools.agentspeech.AgentSpeechAIIntegration(this@WhatsAppNotificationListener)
                            val isEnabled = speechIntegration.isSpeechEnabled()
                            Log.d(TAG, "Ã°Å¸Å½Â¤ Voice reply enabled status: $isEnabled")
                            
                            if (isEnabled) {
                                Log.d(TAG, "Ã°Å¸Å½Â¤ Voice reply enabled, queuing text for speech...")
                                val queueId = speechIntegration.queueTextForSpeech(aiReply, senderPhone)
                                if (queueId > 0) {
                                    Log.d(TAG, "Ã°Å¸Å½Â¤ Text queued for speech: Queue ID=$queueId")
                                } else {
                                    Log.d(TAG, "Ã°Å¸Å½Â¤ Voice reply skipped (queue failed, ID=$queueId)")
                                }
                            } else {
                                Log.d(TAG, "Ã°Å¸Å½Â¤ Voice reply disabled in settings")
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Ã¢ÂÅ’ Error queuing voice reply: ${e.message}", e)
                        }
                        
                        // Check if catalogue needs to be sent AFTER reply is sent
                        Thread.sleep(2000) // Wait 2 seconds for reply to be sent
                        
                        try {
                            val catalogueState = com.message.bulksend.autorespond.ai.product.CatalogueSendingState.getInstance()
                            if (catalogueState.hasPendingCatalogue(this@WhatsAppNotificationListener, senderPhone)) {
                                Log.d(TAG, "Ã°Å¸â€œÂ¦ Pending catalogue detected, sending media files...")
                                
                                // ACQUIRE WAKE LOCK to prevent doze mode
                                acquireCatalogueWakeLock()
                                
                                val productId = catalogueState.getPendingProductId(this@WhatsAppNotificationListener, senderPhone)
                                if (productId != null) {
                                    val productManager = aiService.getProductManager()
                                    var success = productManager.sendProductCatalogue(senderPhone, senderName, productId)
                                     
                                    if (!success) {
                                        Log.w(TAG, "Ã¢Å¡Â Ã¯Â¸Â Catalogue first attempt failed, retrying with staggered send...")
                                        success = productManager.sendProductCatalogueWithDelay(
                                            phoneNumber = senderPhone,
                                            userName = senderName,
                                            productId = productId,
                                            delayBetweenMedia = 7000
                                        )
                                    }

                                    if (success) {
                                        Log.d(TAG, "Ã¢Å“â€¦ Catalogue media sent successfully")
                                        catalogueState.clearPendingCatalogue(this@WhatsAppNotificationListener, senderPhone)
                                    } else {
                                        Log.e(TAG, "Ã¢ÂÅ’ Failed to send catalogue media after retry. Keeping pending state for next attempt.")
                                    }
                                } else {
                                    Log.e(TAG, "Ã¢ÂÅ’ No product ID found in pending catalogue")
                                    catalogueState.clearPendingCatalogue(this@WhatsAppNotificationListener, senderPhone)
                                }
                                
                                // RELEASE WAKE LOCK after catalogue sending
                                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                                    releaseCatalogueWakeLock()
                                }, 10000) // Release after 10 seconds (enough time for all media to be sent)
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Ã¢ÂÅ’ Error sending catalogue: ${e.message}", e)
                            // Clear flag to prevent crash loops
                            try {
                                com.message.bulksend.autorespond.ai.product.CatalogueSendingState.getInstance()
                                    .clearPendingCatalogue(this@WhatsAppNotificationListener, senderPhone)
                            } catch (clearError: Exception) {
                                Log.e(TAG, "Ã¢ÂÅ’ Error clearing catalogue state: ${clearError.message}")
                            }
                            // Release wake lock on error
                            releaseCatalogueWakeLock()
                        }

                        try {
                            handlePendingAgentFormFollowup(
                                sourceNotification = sbn,
                                messageId = messageId,
                                senderPhone = senderPhone,
                                senderName = senderName,
                                aiService = aiService
                            )
                        } catch (e: Exception) {
                            Log.e(TAG, "Error handling AgentForm follow-up: ${e.message}", e)
                        }

                    } catch (e: Exception) {
                        Log.e(TAG, "Error generating AI reply: ${e.message}")
                        serviceScope.launch {
                            messageRepository.updateMessageWithReply(messageId, "", "AI_ERROR")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in AI reply thread: ${e.message}")
            }
        }.start()
    }

    private fun handlePendingAgentFormFollowup(
        sourceNotification: StatusBarNotification,
        messageId: Int,
        senderPhone: String,
        senderName: String,
        aiService: com.message.bulksend.autorespond.aireply.AIService
    ) {
        val stateManager = com.message.bulksend.aiagent.tools.agentform.AgentFormAutomationStateManager(this)
        val payload = stateManager.getPendingFollowup(senderPhone) ?: return

        Log.d(TAG, "Ã°Å¸â€œâ€¹ Pending AgentForm follow-up found for $senderPhone")
        var anyDelivered = false

        val pdfPaths = payload.pdfUrls.mapIndexedNotNull { index, url ->
            downloadAgentFormPdfToCache(url, index)
        }

        if (pdfPaths.isNotEmpty()) {
            val sent = kotlinx.coroutines.runBlocking {
                aiService.getDocumentManager().sendDocumentToUser(
                    phoneNumber = senderPhone,
                    userName = senderName,
                    documentPaths = pdfPaths,
                    documentType = com.message.bulksend.autorespond.documentreply.DocumentType.PDF
                )
            }
            if (sent) {
                anyDelivered = true
                Log.d(TAG, "Ã¢Å“â€¦ AgentForm follow-up PDFs queued via accessibility (${pdfPaths.size})")
            } else {
                Log.e(TAG, "Ã¢ÂÅ’ AgentForm follow-up PDF send failed")
            }
        }

        if (payload.videoUrl.isNotBlank()) {
            val followupText = "Your contact is verified.\nHere is your video link:\n${payload.videoUrl}"
            sendReplyViaNotificationNoDelay(
                sbn = sourceNotification,
                replyText = followupText,
                messageId = messageId,
                senderIdentifier = senderPhone
            )
            anyDelivered = true
            Log.d(TAG, "Ã¢Å“â€¦ AgentForm follow-up video link sent")
        }

        if (anyDelivered) {
            stateManager.markFollowupSent(senderPhone)
            stateManager.markCompleted(senderPhone)
        }
    }

    private fun downloadAgentFormPdfToCache(urlRaw: String, index: Int): String? {
        val normalizedUrl = urlRaw.trim()
        if (normalizedUrl.isBlank()) return null
        return try {
            val folder = java.io.File(cacheDir, "agentform_followup").apply {
                if (!exists()) mkdirs()
            }
            val fileName = "af_${normalizedUrl.hashCode()}_$index.pdf"
            val outFile = java.io.File(folder, fileName)
            if (outFile.exists() && outFile.length() > 0L) {
                return outFile.absolutePath
            }

            val url = java.net.URL(normalizedUrl)
            val connection = url.openConnection().apply {
                connectTimeout = 15_000
                readTimeout = 25_000
            }
            val contentLength = connection.contentLengthLong
            if (contentLength > MAX_AGENTFORM_PDF_BYTES) {
                Log.e(TAG, "AgentForm follow-up PDF too large: $normalizedUrl")
                return null
            }

            connection.getInputStream().use { input ->
                java.io.FileOutputStream(outFile).use { output ->
                    val copied = input.copyTo(output)
                    if (copied <= 0L || copied > MAX_AGENTFORM_PDF_BYTES) {
                        throw IllegalStateException("Invalid PDF payload size: $copied")
                    }
                }
            }

            if (!outFile.exists() || outFile.length() <= 0L) {
                return null
            }
            outFile.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "Failed to cache AgentForm PDF: ${e.message}")
            null
        }
    }

    private fun isFreeAutoReplyUser(): Boolean {
        val subscriptionInfo = SubscriptionUtils.getLocalSubscriptionInfo(this)
        val type = subscriptionInfo["type"] as? String ?: "free"
        val isExpired = subscriptionInfo["isExpired"] as? Boolean ?: false
        return type != "premium" || isExpired
    }

    private fun normalizeAutoReplyUsageIdentifier(senderIdentifier: String): String {
        val trimmedIdentifier = senderIdentifier.trim()
        if (trimmedIdentifier.isBlank() || trimmedIdentifier.equals("unknown", ignoreCase = true)) {
            return ""
        }

        if (trimmedIdentifier.startsWith("instagram_", ignoreCase = true)) {
            return trimmedIdentifier.lowercase()
        }

        val digitsOnly = trimmedIdentifier.replace(Regex("[^0-9]"), "")
        if (digitsOnly.length >= 7) {
            return digitsOnly
        }

        return trimmedIdentifier.lowercase()
    }

    private fun getTrackedAutoReplyNumbers(
        prefs: android.content.SharedPreferences
    ): MutableSet<String> {
        return prefs.getStringSet(KEY_AUTO_REPLY_FREE_NUMBERS, emptySet())?.toMutableSet()
            ?: mutableSetOf()
    }

    private fun getAutoReplyUsageCount(): Int {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return synchronized(autoReplyUsageLock) {
            getTrackedAutoReplyNumbers(prefs).size
        }
    }

    private fun isAutoReplyUsageTracked(normalizedIdentifier: String): Boolean {
        if (normalizedIdentifier.isBlank()) return false
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return synchronized(autoReplyUsageLock) {
            getTrackedAutoReplyNumbers(prefs).contains(normalizedIdentifier)
        }
    }

    private fun incrementAutoReplyUsageCount(senderIdentifier: String) {
        val normalizedIdentifier = normalizeAutoReplyUsageIdentifier(senderIdentifier)
        if (normalizedIdentifier.isBlank()) return

        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        synchronized(autoReplyUsageLock) {
            val trackedNumbers = getTrackedAutoReplyNumbers(prefs)
            if (trackedNumbers.add(normalizedIdentifier)) {
                prefs.edit()
                    .remove(KEY_AUTO_REPLY_FREE_COUNT) // Legacy message-based counter
                    .putStringSet(KEY_AUTO_REPLY_FREE_NUMBERS, trackedNumbers)
                    .apply()
            }
        }
    }

    private fun addAutoReplyBranding(replyText: String): String {
        if (replyText.isBlank()) return replyText
        return "$replyText\n\n$AUTO_REPLY_LIMIT_BRANDING"
    }

    private fun evaluateAutoReplyLimit(
        replyText: String,
        senderIdentifier: String,
        isDocumentReply: Boolean = false
    ): AutoReplyPolicyResult {
        if (!isFreeAutoReplyUser()) {
            return AutoReplyPolicyResult(
                finalReplyText = replyText,
                allowSend = true,
                incrementUsage = false
            )
        }

        val currentCount = getAutoReplyUsageCount()
        val normalizedIdentifier = normalizeAutoReplyUsageIdentifier(senderIdentifier)
        val alreadyTracked = isAutoReplyUsageTracked(normalizedIdentifier)
        val shouldTrackThisSender = normalizedIdentifier.isNotBlank() && !alreadyTracked
        val isLimitReached = shouldTrackThisSender && currentCount >= 10
        val isDocumentLimitReached = isDocumentReply && shouldTrackThisSender && currentCount >= 5

        if (isLimitReached || isDocumentLimitReached) {
            return AutoReplyPolicyResult(
                finalReplyText = AUTO_REPLY_LIMIT_UPGRADE_TEXT,
                allowSend = false,
                incrementUsage = false
            )
        }

        return if (currentCount >= 5) {
            AutoReplyPolicyResult(
                finalReplyText = addAutoReplyBranding(replyText),
                allowSend = true,
                incrementUsage = shouldTrackThisSender
            )
        } else {
            AutoReplyPolicyResult(
                finalReplyText = replyText,
                allowSend = true,
                incrementUsage = shouldTrackThisSender
            )
        }
    }

    /**
     * Send reply using notification's RemoteInput action (with delay support)
     */
    private fun sendReplyViaNotification(
        sbn: StatusBarNotification,
        replyText: String,
        messageId: Int,
        isDocumentReply: Boolean = false,
        senderIdentifier: String = ""
    ) {
        if (!isAutoReplyDispatchAllowed(sbn.packageName, messageId, replyText)) {
            return
        }

        // Get delay from settings
        val delayManager = com.message.bulksend.autorespond.settings.ReplyDelayManager(this)
        val delayMs = delayManager.getDelayMillis()
        
        if (delayMs > 0) {
            Log.d(TAG, "Applying reply delay: ${delayMs}ms")
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                sendReplyViaNotificationNoDelay(
                    sbn = sbn,
                    replyText = replyText,
                    messageId = messageId,
                    isDocumentReply = isDocumentReply,
                    senderIdentifier = senderIdentifier
                )
            }, delayMs)
        } else {
            sendReplyViaNotificationNoDelay(
                sbn = sbn,
                replyText = replyText,
                messageId = messageId,
                isDocumentReply = isDocumentReply,
                senderIdentifier = senderIdentifier
            )
        }
    }

    private fun sendReplyViaNotificationNoDelay(
        sbn: StatusBarNotification,
        replyText: String,
        messageId: Int,
        isDocumentReply: Boolean = false,
        senderIdentifier: String = ""
    ) {
        if (!isAutoReplyDispatchAllowed(sbn.packageName, messageId, replyText)) {
            return
        }

        val fallbackIdentifier = sbn.notification.extras
            .getCharSequence(Notification.EXTRA_TITLE)
            ?.toString()
            .orEmpty()
        val resolvedSenderIdentifier = senderIdentifier.ifBlank { fallbackIdentifier }

        val policyResult = evaluateAutoReplyLimit(
            replyText = replyText,
            senderIdentifier = resolvedSenderIdentifier,
            isDocumentReply = isDocumentReply
        )

        if (!policyResult.allowSend) {
            if (policyResult.finalReplyText.isNotBlank()) {
                sendReplyViaNotificationInternal(sbn, policyResult.finalReplyText, messageId)
            }
            return
        }

        if (policyResult.incrementUsage) {
            incrementAutoReplyUsageCount(resolvedSenderIdentifier)
        }

        sendReplyViaNotificationInternal(sbn, policyResult.finalReplyText, messageId)
    }
    
    /**
     * Internal function to send reply (called after delay)
     */
    private fun sendReplyViaNotificationInternal(sbn: StatusBarNotification, replyText: String, messageId: Int) {
        if (!isAutoReplyDispatchAllowed(sbn.packageName, messageId, replyText)) {
            return
        }

        try {
            // First try the provided notification
            val notification = sbn.notification
            var foundAction = false
            
            // Check if notification has reply action
            notification.actions?.forEach { action ->
                action.remoteInputs?.forEach { remoteInput ->
                    if (remoteInput.resultKey != null) {
                        foundAction = true
                        sendReplyAction(
                            action = action,
                            remoteInput = remoteInput,
                            replyText = replyText,
                            messageId = messageId,
                            packageName = sbn.packageName,
                            sourceNotification = sbn
                        )
                        return
                    }
                }
            }
            
            // If no action found, try to find latest WhatsApp notification from active notifications
            if (!foundAction) {
                Log.d(TAG, "No reply action in provided notification, searching active notifications...")
                
                try {
                    val activeNotifications = activeNotifications
                    val senderName = sbn.notification.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
                    
                    // Find matching notification (WhatsApp, WhatsApp Business, or Instagram)
                    activeNotifications?.forEach { activeSbn ->
                        if ((activeSbn.packageName == WHATSAPP_PACKAGE || 
                             activeSbn.packageName == WHATSAPP_BUSINESS_PACKAGE ||
                             activeSbn.packageName == INSTAGRAM_PACKAGE)) {
                            
                            val activeTitle = activeSbn.notification.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
                            
                            // Check if it's from the same sender
                            if (activeTitle == senderName) {
                                activeSbn.notification.actions?.forEach { action ->
                                    action.remoteInputs?.forEach { remoteInput ->
                                        if (remoteInput.resultKey != null) {
                                            val appName = when (activeSbn.packageName) {
                                                WHATSAPP_PACKAGE -> "WhatsApp"
                                                WHATSAPP_BUSINESS_PACKAGE -> "WhatsApp Business"
                                                INSTAGRAM_PACKAGE -> "Instagram"
                                                else -> "Unknown"
                                            }
                                            Log.d(TAG, "Found reply action in active $appName notification")
                                            foundAction = true
                                            sendReplyAction(
                                                action = action,
                                                remoteInput = remoteInput,
                                                replyText = replyText,
                                                messageId = messageId,
                                                packageName = activeSbn.packageName,
                                                sourceNotification = activeSbn
                                            )
                                            return
                                        }
                                    }
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error searching active notifications: ${e.message}")
                }
            }
            
            if (!foundAction) {
                Log.w(TAG, "No reply action found in any notification")
                val fallbackTriggered =
                    trySendReplyViaAccessibilityFallback(
                        sourceNotification = sbn,
                        replyText = replyText,
                        messageId = messageId,
                        failureReason = "NO_ACTION"
                    )
                if (!fallbackTriggered && messageId >= 0) {
                    serviceScope.launch {
                        messageRepository.updateMessageWithReply(messageId, replyText, "NO_ACTION")
                    }
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error sending reply: ${e.message}")
            if (messageId >= 0) {
                serviceScope.launch {
                    messageRepository.updateMessageWithReply(messageId, replyText, "ERROR")
                }
            }
        }
    }
    
    /**
     * Send reply action with RemoteInput
     */
    private fun sendReplyAction(
        action: Notification.Action,
        remoteInput: android.app.RemoteInput,
        replyText: String,
        messageId: Int,
        packageName: String,
        sourceNotification: StatusBarNotification?
    ) {
        if (!isAutoReplyDispatchAllowed(packageName, messageId, replyText)) {
            return
        }

        try {
            // Create reply intent
            val replyIntent = Intent()
            val bundle = Bundle()
            bundle.putCharSequence(remoteInput.resultKey, replyText)
            android.app.RemoteInput.addResultsToIntent(
                arrayOf(remoteInput),
                replyIntent,
                bundle
            )
            
            // Send the reply
            try {
                action.actionIntent.send(this, 0, replyIntent)
                Log.d(TAG, "Ã¢Å“â€œ Auto-reply sent: ${replyText.take(50)}...")
                
                // Update database with SENT status
                if (messageId >= 0) {
                    serviceScope.launch {
                        messageRepository.updateMessageWithReply(messageId, replyText, "SENT")
                        Log.d(TAG, "Ã¢Å“â€œ Database updated - Message ID: $messageId, Status: SENT")
                    }
                }
                
                // Show notification that reply was sent
                showReplyNotification(replyText, packageName)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send reply: ${e.message}")

                val fallbackTriggered =
                    sourceNotification?.let {
                        trySendReplyViaAccessibilityFallback(
                            sourceNotification = it,
                            replyText = replyText,
                            messageId = messageId,
                            failureReason = "REMOTE_INPUT_FAILED"
                        )
                    } ?: false

                if (!fallbackTriggered && messageId >= 0) {
                    // Update database with FAILED status
                    serviceScope.launch {
                        messageRepository.updateMessageWithReply(messageId, replyText, "FAILED")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in sendReplyAction: ${e.message}")
        }
    }

    /**
     * Fallback path for WhatsApp replies when notification remote input fails.
     * Uses wa.me + accessibility auto-click flow.
     */
    private fun trySendReplyViaAccessibilityFallback(
        sourceNotification: StatusBarNotification,
        replyText: String,
        messageId: Int,
        failureReason: String
    ): Boolean {
        if (!isAutoReplyDispatchAllowed(sourceNotification.packageName, messageId, replyText)) {
            return false
        }

        if (sourceNotification.packageName != WHATSAPP_PACKAGE &&
            sourceNotification.packageName != WHATSAPP_BUSINESS_PACKAGE) {
            return false
        }

        val recipientPhone = extractRecipientPhoneForFallback(sourceNotification)
        if (recipientPhone.isBlank()) {
            Log.w(TAG, "Accessibility fallback skipped: recipient phone unavailable")
            return false
        }

        val globalSender = com.message.bulksend.aiagent.tools.globalsender.GlobalSenderManager(this)
        if (!globalSender.isAccessibilityEnabled()) {
            Log.w(TAG, "Accessibility fallback skipped: service not enabled")
            return false
        }

        serviceScope.launch {
            if (messageId >= 0) {
                messageRepository.updateMessageWithReply(
                    messageId,
                    replyText,
                    "FALLBACK_ACCESSIBILITY"
                )
            }

            val sendResult = globalSender.sendTextViaAccessibility(
                phoneNumber = recipientPhone,
                message = replyText,
                preferredPackage = sourceNotification.packageName
            )

            if (messageId >= 0) {
                messageRepository.updateMessageWithReply(
                    messageId,
                    replyText,
                    sendResult.status
                )
            }

            Log.d(
                TAG,
                "Accessibility fallback result ($failureReason): ${sendResult.status} for $recipientPhone"
            )
        }

        return true
    }

    private fun extractRecipientPhoneForFallback(sbn: StatusBarNotification): String {
        val extras = sbn.notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim().orEmpty()

        val fromTitle = if (PhoneNumberExtractor.isPhoneNumber(title)) title else ""
        val fromExtras = PhoneNumberExtractor.extractFromNotification(extras).orEmpty()
        val fromContact =
            if (fromTitle.isBlank()) PhoneNumberExtractor.getPhoneNumber(this, title).orEmpty()
            else ""

        return sanitizeFallbackPhone(
            when {
                fromTitle.isNotBlank() -> fromTitle
                fromExtras.isNotBlank() -> fromExtras
                else -> fromContact
            }
        )
    }

    private fun sanitizeFallbackPhone(value: String?): String {
        return value.orEmpty().replace(Regex("[^0-9]"), "")
    }

    private fun isMultiChatSummaryNotification(title: String, text: String): Boolean {
        val normalizedTitle = title.lowercase()
        val normalizedText = text.lowercase()

        if ((normalizedTitle.contains("wa business") || normalizedTitle.contains("whatsapp")) &&
            normalizedText.contains("messages from") &&
            normalizedText.contains("chat")) {
            return true
        }

        return Regex("\\b\\d+\\s+messages?\\s+from\\s+\\d+\\s+chats?\\b")
            .containsMatchIn(normalizedText)
    }

    private fun resolveFallbackWhatsAppPackage(preferredPackage: String): String? {
        if (preferredPackage == WHATSAPP_PACKAGE && isPackageInstalled(WHATSAPP_PACKAGE)) {
            return WHATSAPP_PACKAGE
        }
        if (preferredPackage == WHATSAPP_BUSINESS_PACKAGE &&
            isPackageInstalled(WHATSAPP_BUSINESS_PACKAGE)) {
            return WHATSAPP_BUSINESS_PACKAGE
        }
        if (isPackageInstalled(WHATSAPP_PACKAGE)) return WHATSAPP_PACKAGE
        if (isPackageInstalled(WHATSAPP_BUSINESS_PACKAGE)) return WHATSAPP_BUSINESS_PACKAGE
        return null
    }

    private fun isPackageInstalled(packageName: String): Boolean {
        return try {
            packageManager.getPackageInfo(packageName, PackageManager.GET_ACTIVITIES)
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun isWhatsAppBackupNotification(title: String, text: String, extras: Bundle): Boolean {
        val normalizedTitle = title.trim().lowercase()
        val normalizedText = text.trim().lowercase()
        val normalizedSubText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)
            ?.toString()
            ?.trim()
            ?.lowercase()
            .orEmpty()
        val normalizedBigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)
            ?.toString()
            ?.trim()
            ?.lowercase()
            .orEmpty()

        val combinedText = listOf(normalizedText, normalizedSubText, normalizedBigText)
            .filter { it.isNotBlank() }
            .joinToString(" ")

        val hasBackupSignal = combinedText.contains("backup") ||
            combinedText.contains("backing up") ||
            combinedText.contains("back up") ||
            combinedText.contains("google drive") ||
            combinedText.contains("restore backup") ||
            combinedText.contains("restoring backup") ||
            combinedText.contains("backup paused") ||
            combinedText.contains("backup failed") ||
            combinedText.contains("backup complete") ||
            combinedText.contains("backup in progress") ||
            Regex("\\bbackup\\b").containsMatchIn(combinedText)

        if (!hasBackupSignal) return false

        val isSystemStyleTitle = normalizedTitle.isBlank() ||
            normalizedTitle == "whatsapp" ||
            normalizedTitle == "wa business" ||
            normalizedTitle.contains("whatsapp business") ||
            normalizedTitle.contains("google drive") ||
            normalizedTitle.contains("backup")

        return isSystemStyleTitle
    }

    private fun isTransientMediaProgressMessage(message: String): Boolean {
        val text = message.trim()
        if (text.isBlank()) return false

        val normalized = text.lowercase()
        val hasDataProgressPattern = Regex(
            "\\b\\d+(?:\\.\\d+)?\\s*(kb|mb|gb)\\s+of\\s+\\d+(?:\\.\\d+)?\\s*(kb|mb|gb)\\b",
            RegexOption.IGNORE_CASE
        ).containsMatchIn(text)
        val hasPercentPattern = Regex("\\(\\d{1,3}%\\)").containsMatchIn(text)

        return normalized.startsWith("uploading:") ||
            normalized.startsWith("downloading:") ||
            normalized.startsWith("sending media") ||
            normalized.startsWith("preparing media") ||
            normalized.startsWith("uploading media") ||
            (hasDataProgressPattern && hasPercentPattern)
    }

    private fun isEmojiReactionNotification(message: String): Boolean {
        val text = message.trim()
        if (text.isBlank()) return false

        val candidates = buildReactionCandidates(text)
        return candidates.any { candidate -> isDirectEmojiReactionText(candidate) }
    }

    private fun buildReactionCandidates(message: String): List<String> {
        val trimmed = message.trim()
        val candidates = linkedSetOf(trimmed)

        val separatorIndex = trimmed.indexOf(':')
        if (separatorIndex in 1..80) {
            val prefix = trimmed.substring(0, separatorIndex).trim()
            val suffix = trimmed.substring(separatorIndex + 1).trim()
            val prefixWordCount = prefix.split(Regex("\\s+")).count { it.isNotBlank() }
            val looksLikeSenderPrefix =
                prefixWordCount in 1..6 &&
                    !prefix.contains('?') &&
                    !prefix.contains('!') &&
                    !prefix.contains('.') &&
                    (PhoneNumberExtractor.isPhoneNumber(prefix) || prefix.any { it.isLetterOrDigit() })

            if (looksLikeSenderPrefix && suffix.isNotBlank()) {
                candidates.add(suffix)
            }
        }

        return candidates.toList()
    }

    private fun isDirectEmojiReactionText(message: String): Boolean {
        val text = message.trim()
        if (text.isBlank()) return false
        val normalized = text.lowercase()
        val directReactionPrefixes = listOf(
            "reacted ",
            "liked your message",
            "liked a message",
            "sent a reaction",
            "removed a reaction",
            "removed their reaction",
            "reacted â¤ï¸",
            "reacted â¤",
            "reacted ðŸ‘",
            
            "reacted ðŸ˜‚",
            "reacted ðŸ˜"
        )

        if (directReactionPrefixes.any { normalized.startsWith(it) }) {
            return true
        }

        val quotedReactionPattern = Regex(
            "^[^\\p{L}\\p{Nd}]{1,8}\\s+to\\s+[\"â€œ'â€˜].+",
            RegexOption.IGNORE_CASE
        )
        if (quotedReactionPattern.containsMatchIn(text)) {
            return true
        }

        if (!startsWithLikelyReactionGlyph(text)) {
            return false
        }

        val reactionTargetPhrases = listOf(
            "to your message",
            "to your photo",
            "to your video",
            "to your reel",
            "to your story",
            "to your status",
            "to your note",
            "to your audio",
            "to your voice message",
            "to your voice note",
            "on your message",
            "on your note"
        )

        return reactionTargetPhrases.any { normalized.contains(it) }
    }

    private fun startsWithLikelyReactionGlyph(message: String): Boolean {
        val first = message.trimStart().firstOrNull() ?: return false
        if (first.isLetterOrDigit()) return false

        return when (Character.getType(first)) {
            Character.OTHER_SYMBOL.toInt(),
            Character.SURROGATE.toInt(),
            Character.NON_SPACING_MARK.toInt() -> true
            else -> false
        }
    }

    /**
     * Spreadsheet reply lookup (sheetreply mode only).
     */
    private fun findSpreadsheetReply(incomingMessage: String): String? {
        return try {
            com.message.bulksend.autorespond.sheetreply.SpreadsheetReplyManager(this)
                .findMatchingReply(incomingMessage)
        } catch (e: Exception) {
            Log.e(TAG, "Link spreadsheet lookup failed: ${e.message}", e)
            null
        }
    }
    
    /**
     * Show a notification that auto-reply was sent
     */
    private fun showReplyNotification(replyText: String, packageName: String) {
        try {
            val notificationManager = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
            
            // Create notification channel for Android O+
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                val channel = android.app.NotificationChannel(
                    "auto_reply_channel",
                    "Auto Reply Notifications",
                    android.app.NotificationManager.IMPORTANCE_LOW
                )
                notificationManager.createNotificationChannel(channel)
            }
            
            // Determine app name and title
            val appName = when (packageName) {
                WHATSAPP_PACKAGE -> "WhatsApp"
                WHATSAPP_BUSINESS_PACKAGE -> "WhatsApp Business"
                INSTAGRAM_PACKAGE -> "Instagram"
                else -> "Unknown App"
            }
            
            // Build notification
            val notification = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                Notification.Builder(this, "auto_reply_channel")
            } else {
                @Suppress("DEPRECATION")
                Notification.Builder(this)
            }
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("$appName Auto Reply Sent")
                .setContentText(replyText)
                .setAutoCancel(true)
                .build()
            
            notificationManager.notify(System.currentTimeMillis().toInt(), notification)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error showing reply notification: ${e.message}")
        }
    }

    /**
     * Check and auto-add lead to Lead Manager based on settings
     */
    private suspend fun checkAndAutoAddLead(
        senderName: String,
        phoneNumber: String?,
        messageText: String,
        packageName: String
    ) {
        try {
            val leadManager = com.message.bulksend.leadmanager.LeadManager(this)
            val settings = leadManager.getAutoAddSettings()
            
            Log.d(TAG, "Ã°Å¸â€Â Auto-add check - Sender: $senderName, Phone: $phoneNumber")
            Log.d(TAG, "Ã°Å¸â€Â Settings - Enabled: ${settings?.isAutoAddEnabled}, AutoAddAll: ${settings?.autoAddAllMessages}, KeywordBased: ${settings?.keywordBasedAdd}")
            
            // Check if auto-add is enabled
            if (settings == null || !settings.isAutoAddEnabled) {
                Log.d(TAG, "Ã¢ÂÅ’ Auto-add leads is disabled or settings null")
                return
            }
            
            val phone = phoneNumber
            if (phone.isNullOrBlank()) {
                Log.d(TAG, "Ã¢ÂÅ’ Phone number is null or blank, cannot add lead")
                return
            }
            
            Log.d(TAG, "Ã¢Å“â€œ Phone number valid: $phone")
            
            // Check if lead already exists
            if (settings.excludeExistingContacts) {
                val existingLead = leadManager.getLeadByPhone(phone)
                if (existingLead != null) {
                    Log.d(TAG, "Lead already exists: ${existingLead.name}")
                    
                    // Save chat message to existing lead
                    saveChatMessageToLead(leadManager, existingLead.id, senderName, phone, messageText, packageName, true, null)
                    return
                }
            }
            
            var matchedKeyword: String? = null
            var shouldAddLead = false
            
            // Check if we should add all messages or keyword-based
            if (settings.autoAddAllMessages) {
                shouldAddLead = true
                Log.d(TAG, "Auto-add all messages enabled - adding lead")
            } else if (settings.keywordBasedAdd) {
                // Check keyword rules
                val matchedRule = leadManager.checkAutoAddKeywordMatch(messageText)
                if (matchedRule != null) {
                    shouldAddLead = true
                    matchedKeyword = matchedRule.keyword
                    Log.d(TAG, "Keyword match found: ${matchedRule.keyword} - adding lead")
                }
            }
            
            if (shouldAddLead) {
                // Add lead
                val newLead = leadManager.addLeadFromAutoRespond(
                    senderName = senderName,
                    senderPhone = phone,
                    messageText = messageText,
                    matchedKeyword = matchedKeyword
                )
                
                if (newLead != null) {
                    Log.d(TAG, "Ã¢Å“â€œ Lead added: ${newLead.name} (${newLead.phoneNumber})")
                
                    // Save chat message to new lead
                    saveChatMessageToLead(leadManager, newLead.id, senderName, phone, messageText, packageName, true, matchedKeyword)
                } else {
                    Log.d(TAG, "Lead add blocked for free plan limit (5 leads).")
                }
                
                // Show notification if enabled
                if (settings.notifyOnNewLead) {
                    newLead?.let { showNewLeadNotification(it.name, phone) }
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error in auto-add lead: ${e.message}")
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
        packageName: String,
        isIncoming: Boolean,
        matchedKeyword: String?
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
            Log.d(TAG, "Ã¢Å“â€œ Chat message saved to lead: $leadId")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error saving chat message: ${e.message}")
        }
    }
    
    /**
     * Show notification when new lead is added
     */
    private fun showNewLeadNotification(leadName: String, phone: String) {
        try {
            val notificationManager = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
            
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
                Notification.Builder(this, "new_lead_channel")
            } else {
                @Suppress("DEPRECATION")
                Notification.Builder(this)
            }
                .setSmallIcon(android.R.drawable.ic_input_add)
                .setContentTitle("New Lead Added")
                .setContentText("$leadName ($phone) added from WhatsApp")
                .setAutoCancel(true)
                .build()
            
            notificationManager.notify((System.currentTimeMillis() + 1000).toInt(), notification)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error showing new lead notification: ${e.message}")
        }
    }
    
    /**
     * Clean old processed messages (older than 1 minute)
     */
    private fun cleanOldMessages(currentTime: Long) {
        // Clean old message timestamps
        val messageIterator = messageTimestamps.iterator()
        while (messageIterator.hasNext()) {
            val entry = messageIterator.next()
            if (currentTime - entry.value > 60000) { // 1 minute
                processedMessages.remove(entry.key)
                messageIterator.remove()
            }
        }
        
        // Clean old sender reply times (older than 1 minute)
        val senderIterator = lastReplyTimeBySender.iterator()
        while (senderIterator.hasNext()) {
            val entry = senderIterator.next()
            if (currentTime - entry.value > 60000) { // 1 minute
                senderIterator.remove()
            }
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
    }
    
    /**
     * Save notification data for debug screen
     */
    private fun saveNotificationDebugData(
        title: String,
        text: String,
        packageName: String,
        extras: android.os.Bundle
    ) {
        try {
            // Extract all extras
            val allExtras = mutableMapOf<String, String>()
            for (key in extras.keySet()) {
                val value = extras.get(key)
                allExtras[key] = value?.toString() ?: "null"
            }
            
            // Save to static variable for debug screen
            NotificationDebugActivity.lastNotificationData = NotificationDebugData(
                title = title,
                text = text,
                packageName = packageName,
                timestamp = System.currentTimeMillis(),
                allExtras = allExtras
            )
            
            android.util.Log.d("WANotificationListener", "Ã¢Å“â€œ Debug data saved - ${allExtras.size} extras")
        } catch (e: Exception) {
            android.util.Log.e("WANotificationListener", "Error saving debug data: ${e.message}")
        }
    }
    
    /**
     * Check if lead capture should be triggered and process it
     */
    private suspend fun checkLeadCapture(username: String, message: String, packageName: String): String? {
        try {
            // Only process lead capture for Instagram
            if (packageName != INSTAGRAM_PACKAGE) {
                return null
            }
            
            val leadCaptureManager = LeadCaptureManager(this)
            
            // Check if lead capture is enabled
            if (!leadCaptureManager.isLeadCaptureEnabled()) {
                Log.d(TAG, "Lead capture is disabled")
                return null
            }
            
            // Check if this is a new user
            if (leadCaptureManager.isNewUser(username, LeadCaptureManager.PLATFORM_INSTAGRAM)) {
                Log.d(TAG, "New Instagram user detected: $username - starting lead capture")
                return leadCaptureManager.startLeadCapture(username, LeadCaptureManager.PLATFORM_INSTAGRAM)
            } else {
                // Existing user in capture process - process their response
                Log.d(TAG, "Processing lead capture response from: $username")
                return leadCaptureManager.processLeadCaptureResponse(username, LeadCaptureManager.PLATFORM_INSTAGRAM, message)
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error in lead capture: ${e.message}", e)
            return null
        }
    }

    /**
     * Show a notification for AI errors
     */
    private fun showErrorNotification(errorText: String) {
        try {
            val notificationManager = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
            
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                val channel = android.app.NotificationChannel(
                    "ai_error_channel",
                    "AI Error Notifications",
                    android.app.NotificationManager.IMPORTANCE_HIGH
                )
                notificationManager.createNotificationChannel(channel)
            }
            
            val notification = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                Notification.Builder(this, "ai_error_channel")
            } else {
                @Suppress("DEPRECATION")
                Notification.Builder(this)
            }
                .setSmallIcon(android.R.drawable.stat_notify_error)
                .setContentTitle("AI Auto-Reply Failed")
                .setContentText(errorText)
                .setStyle(Notification.BigTextStyle().bigText(errorText))
                .setAutoCancel(true)
                .build()
            
            notificationManager.notify(System.currentTimeMillis().toInt(), notification)
            
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error showing error notification: ${e.message}")
        }
    }
}
