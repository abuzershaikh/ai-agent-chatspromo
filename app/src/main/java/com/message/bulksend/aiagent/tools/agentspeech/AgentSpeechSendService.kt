package com.message.bulksend.aiagent.tools.agentspeech

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.FileProvider
import java.io.File
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Service to send voice messages using accessibility service
 * Similar to DocumentSendService but for audio files
 */
class AgentSpeechSendService private constructor() {
    
    companion object {
        const val TAG = "AgentSpeechSendService"
        private const val RETURN_TO_APP_DELAY_MS = 3000L
        
        @Volatile
        private var INSTANCE: AgentSpeechSendService? = null
        
        @Volatile
        private var isSpeechSendEnabled = false
        
        // Queue for speech send tasks
        private val sendQueue = ConcurrentLinkedQueue<SpeechSendTask>()
        
        // Current task being processed
        @Volatile
        private var currentTask: SpeechSendTask? = null
        
        fun getInstance(): AgentSpeechSendService {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AgentSpeechSendService().also { INSTANCE = it }
            }
        }
        
        fun enableSpeechSend() {
            isSpeechSendEnabled = true
            Log.d(TAG, "✅ Speech send ENABLED")
        }
        
        fun disableSpeechSend() {
            isSpeechSendEnabled = false
            Log.d(TAG, "❌ Speech send DISABLED")
            INSTANCE?.releaseWakeLock()
        }
        
        fun isSpeechSendEnabled(): Boolean = isSpeechSendEnabled
    }
    
    private val handler = Handler(Looper.getMainLooper())
    
    // Wake lock for speech sending
    private var wakeLock: android.os.PowerManager.WakeLock? = null
    
    // Keyguard lock to dismiss lock screen
    @Suppress("DEPRECATION")
    private var keyguardLock: android.app.KeyguardManager.KeyguardLock? = null
    
    /**
     * Acquire wake lock AND dismiss keyguard (lock screen)
     */
    private fun acquireWakeLock(context: Context) {
        try {
            // 1. Acquire WakeLock to turn screen on
            if (wakeLock?.isHeld != true) {
                val pm = context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
                val flags = android.os.PowerManager.SCREEN_BRIGHT_WAKE_LOCK or android.os.PowerManager.ACQUIRE_CAUSES_WAKEUP
                
                wakeLock = pm.newWakeLock(flags, "BulkSend:SpeechSendService").apply {
                    acquire(10 * 60 * 1000L) // 10 minutes max safety timeout
                }
                Log.d(TAG, "✅ WakeLock acquired (screen on)")
            }
            
            // 2. Dismiss keyguard (lock screen)
            try {
                @Suppress("DEPRECATION")
                val km = context.getSystemService(Context.KEYGUARD_SERVICE) as android.app.KeyguardManager
                @Suppress("DEPRECATION")
                keyguardLock = km.newKeyguardLock("BulkSend:SpeechSend")
                @Suppress("DEPRECATION")
                keyguardLock?.disableKeyguard()
                Log.d(TAG, "✅ Keyguard dismissed (lock screen bypassed)")
            } catch (e: Exception) {
                Log.e(TAG, "⚠️ Keyguard dismiss failed: ${e.message}")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to acquire WakeLock: ${e.message}")
        }
    }
    
    /**
     * Release wake lock AND re-enable keyguard
     */
    private fun releaseWakeLock() {
        try {
            // Re-enable keyguard first
            try {
                @Suppress("DEPRECATION")
                keyguardLock?.reenableKeyguard()
                keyguardLock = null
                Log.d(TAG, "✅ Keyguard re-enabled")
            } catch (e: Exception) {
                Log.e(TAG, "⚠️ Keyguard re-enable failed: ${e.message}")
            }
            
            // Release WakeLock
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
                Log.d(TAG, "✅ WakeLock released")
            }
            wakeLock = null
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to release WakeLock: ${e.message}")
        }
    }
    
    /**
     * Add speech send task to queue
     */
    fun addSpeechSendTask(
        context: Context,
        phoneNumber: String,
        senderName: String,
        audioPath: String,
        queueId: Long
    ) {
        val task = SpeechSendTask(
            phoneNumber = phoneNumber,
            senderName = senderName,
            audioPath = audioPath,
            queueId = queueId,
            timestamp = System.currentTimeMillis()
        )
        
        sendQueue.offer(task)
        Log.d(TAG, "📥 Speech send task added to queue: $phoneNumber")
        Log.d(TAG, "📊 Queue size: ${sendQueue.size}")
        
        // Process queue if not already processing
        if (currentTask == null) {
            processNextTask(context)
        }
    }
    
    /**
     * Process next task in queue
     */
    private fun processNextTask(context: Context) {
        if (!isSpeechSendEnabled) {
            Log.w(TAG, "⚠️ Speech send is disabled, skipping queue processing")
            return
        }
        
        val task = sendQueue.poll()
        if (task == null) {
            Log.d(TAG, "✅ Queue empty, releasing wake lock")
            com.message.bulksend.bulksend.CampaignState.isAutoSendEnabled = false
            com.message.bulksend.bulksend.CampaignState.isSendActionSuccessful = null
            com.message.bulksend.bulksend.WhatsAppAutoSendService.deactivateService()
            bringAppToForeground(context, "queue-empty")
            releaseWakeLock()
            currentTask = null
            return
        }
        
        currentTask = task
        Log.d(TAG, "🎤 Processing speech send task: ${task.phoneNumber}")
        
        // Acquire wake lock
        acquireWakeLock(context)

        // Enable accessibility auto-send so WhatsApp send button is clicked automatically.
        com.message.bulksend.bulksend.CampaignState.isSendActionSuccessful = null
        com.message.bulksend.bulksend.CampaignState.isAutoSendEnabled = true
        com.message.bulksend.bulksend.WhatsAppAutoSendService.activateService()
        Log.d(TAG, "✅ Auto-send service ENABLED for speech sending")
        
        // Send voice message
        sendVoiceMessage(context, task)
    }
    
    /**
     * Send voice message via WhatsApp
     * Uses same approach as DocumentSendService - opens chat first, then shares audio
     */
    private fun sendVoiceMessage(context: Context, task: SpeechSendTask) {
        try {
            val audioFile = File(task.audioPath)
            
            if (!audioFile.exists()) {
                Log.e(TAG, "❌ Audio file not found: ${task.audioPath}")
                // Process next task
                handler.postDelayed({ processNextTask(context) }, 1000)
                return
            }
            
            Log.d(TAG, "📁 Audio file found: ${audioFile.name}, size: ${audioFile.length()} bytes")
            
            // Get WhatsApp package name
            val packageName = getAvailableWhatsAppPackage(context)
            if (packageName == null) {
                Log.e(TAG, "❌ No WhatsApp installed")
                handler.postDelayed({ processNextTask(context) }, 1000)
                return
            }
            
            Log.d(TAG, "📱 Using WhatsApp package: $packageName")
            
            // Clean phone number
            val cleanNumber = task.phoneNumber.replace("+", "").replace(" ", "").replace("-", "")
            Log.d(TAG, "📞 Clean number: $cleanNumber")
            
            // Create FileProvider URI
            val audioUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",
                audioFile
            )
            Log.d(TAG, "🔗 Audio URI created: $audioUri")
            
            // Step 1: Open chat first (same as DocumentSendService)
            val openChatIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$cleanNumber")).apply {
                setPackage(packageName)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(openChatIntent)
            Log.d(TAG, "📱 Opening chat for: ${task.phoneNumber}")
            
            // Wait for chat to open
            handler.postDelayed({
                try {
                    // Step 2: Send audio (same as DocumentSendService)
                    val sendAudioIntent = Intent(Intent.ACTION_SEND).apply {
                        putExtra(Intent.EXTRA_STREAM, audioUri)
                        type = "audio/*"
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        putExtra("jid", "$cleanNumber@s.whatsapp.net")
                        // Add a space as text to avoid "Can't send empty message" error
                        putExtra(Intent.EXTRA_TEXT, " ")
                        setPackage(packageName)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    
                    context.startActivity(sendAudioIntent)
                    Log.d(TAG, "✅ Voice message shared successfully: ${audioFile.name}")
                    
                    // Match DocumentSendService timing:
                    // wait for send button click window, then return to app.
                    handler.postDelayed({
                        Log.d(TAG, "⏳ Waiting for accessibility service to click send button...")
                        handler.postDelayed({
                            bringAppToForeground(context, "audio-send-complete")
                            handler.postDelayed({
                                Log.d(TAG, "⏭️ Moving to next task...")
                                processNextTask(context)
                            }, 300)
                        }, RETURN_TO_APP_DELAY_MS)
                    }, 5000) // Wait 5 seconds for send button to be clicked
                    
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Error in audio send step: ${e.message}", e)
                    handler.postDelayed({ processNextTask(context) }, 1000)
                }
            }, 2500) // Same delay as DocumentSendService
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error sending voice message: ${e.message}", e)
            
            // Process next task after error
            handler.postDelayed({ processNextTask(context) }, 2000)
        }
    }

    /**
     * Bring Bulk Send app to foreground after send flow.
     */
    private fun bringAppToForeground(context: Context, reason: String) {
        try {
            Log.d(TAG, "🏠 Returning to app ($reason)")
            val homeIntent = Intent(context, com.message.bulksend.MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(homeIntent)
            Log.d(TAG, "✅ App moved to foreground")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to return app to foreground: ${e.message}", e)
        }
    }
    
    /**
     * Check if package is installed
     */
    private fun isPackageInstalled(context: Context, packageName: String): Boolean {
        return try {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Handle accessibility events for speech sending
     * Called by WhatsAppAutoSendService
     */
    fun handleAccessibilityEvent(event: android.view.accessibility.AccessibilityEvent?, accessibilityService: android.accessibilityservice.AccessibilityService) {
        if (event == null || !isSpeechSendEnabled || currentTask == null) return
        
        val packageName = event.packageName?.toString()
        Log.d(TAG, "🔍 Accessibility event: type=${event.eventType}, package=$packageName")
        
        if (packageName != "com.whatsapp" && packageName != "com.whatsapp.w4b") return
        
        try {
            val rootNode = accessibilityService.rootInActiveWindow ?: run {
                Log.w(TAG, "⚠️ No root node available")
                return
            }
            
            Log.d(TAG, "🔍 Root node found, checking for share dialog...")
            
            // Handle "Share with" dialog - click OK/Send button
            if (handleShareWithDialog(rootNode, accessibilityService)) {
                Log.d(TAG, "✅ Share dialog handled")
            } else {
                Log.d(TAG, "⚠️ Share dialog not found or not handled")
            }
            
            rootNode.recycle()
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error handling accessibility event: ${e.message}")
        }
    }
    
    /**
     * Handle "Share with" dialog for audio sharing
     */
    private fun handleShareWithDialog(rootNode: android.view.accessibility.AccessibilityNodeInfo, accessibilityService: android.accessibilityservice.AccessibilityService): Boolean {
        try {
            // Check for "Share with" text or WhatsApp share dialog
            val shareDialogTexts = listOf(
                "Share with", "share with", "SHARE WITH",
                "Send to", "send to", "SEND TO",
                "Share via", "share via", "SHARE VIA"
            )
            var isShareDialog = false
            
            for (text in shareDialogTexts) {
                val shareNodes = rootNode.findAccessibilityNodeInfosByText(text)
                if (shareNodes.isNotEmpty()) {
                    isShareDialog = true
                    Log.d(TAG, "📤 Share dialog detected with text: $text")
                    break
                }
            }
            
            if (!isShareDialog) {
                // Also check for WhatsApp specific elements
                val whatsappNodes = rootNode.findAccessibilityNodeInfosByText("WhatsApp")
                if (whatsappNodes.isNotEmpty()) {
                    isShareDialog = true
                    Log.d(TAG, "📤 WhatsApp share dialog detected")
                }
            }
            
            if (!isShareDialog) return false
            
            // Method 1: Find and click OK/Send button by text
            val okTexts = listOf("OK", "Ok", "ok", "SEND", "Send", "send", "DONE", "Done", "done", "SHARE", "Share")
            for (text in okTexts) {
                val okNodes = rootNode.findAccessibilityNodeInfosByText(text)
                for (node in okNodes) {
                    if (node.isVisibleToUser && node.isClickable) {
                        Log.d(TAG, "✅ Clicking OK/Send button by text: $text")
                        node.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK)
                        return true
                    }
                    // Try clicking parent if node itself is not clickable
                    val parent = node.parent
                    if (parent != null && parent.isClickable && parent.isVisibleToUser) {
                        Log.d(TAG, "✅ Clicking OK/Send button parent")
                        parent.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK)
                        return true
                    }
                }
            }
            
            // Method 2: Find button by resource ID
            val buttonIds = listOf(
                "android:id/button1",
                "android:id/button_positive", 
                "com.whatsapp:id/send",
                "com.whatsapp:id/ok",
                "com.whatsapp:id/positive_button",
                "com.whatsapp.w4b:id/send",
                "com.whatsapp.w4b:id/ok",
                "com.whatsapp.w4b:id/positive_button"
            )
            
            for (id in buttonIds) {
                val nodes = rootNode.findAccessibilityNodeInfosByViewId(id)
                if (nodes.isNotEmpty()) {
                    val button = nodes[0]
                    if (button.isVisibleToUser && button.isClickable) {
                        Log.d(TAG, "✅ Clicking button by ID: $id")
                        button.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK)
                        return true
                    }
                }
            }
            
            Log.d(TAG, "⚠️ Share dialog detected but no clickable button found")
            return false
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error handling share dialog: ${e.message}")
            return false
        }
    }
    
    /**
     * Get available WhatsApp package name
     */
    private fun getAvailableWhatsAppPackage(context: Context): String? {
        val packageManager = context.packageManager
        
        // Check WhatsApp Business first (more likely for business use)
        try {
            packageManager.getPackageInfo("com.whatsapp.w4b", 0)
            Log.d(TAG, "📱 WhatsApp Business found")
            return "com.whatsapp.w4b"
        } catch (e: Exception) {
            // WhatsApp Business not installed
        }
        
        // Check regular WhatsApp
        try {
            packageManager.getPackageInfo("com.whatsapp", 0)
            Log.d(TAG, "📱 WhatsApp found")
            return "com.whatsapp"
        } catch (e: Exception) {
            // Regular WhatsApp not installed
        }
        
        Log.e(TAG, "❌ No WhatsApp found")
        return null
    }
    
    /**
     * Get queue status
     */
    fun getQueueStatus(): Pair<Int, SpeechSendTask?> {
        return Pair(sendQueue.size, currentTask)
    }
    
    /**
     * Clear queue
     */
    fun clearQueue() {
        sendQueue.clear()
        currentTask = null
        releaseWakeLock()
        Log.d(TAG, "🗑️ Queue cleared")
    }
    
    /**
     * Get current task
     */
    fun getCurrentTask(): SpeechSendTask? = currentTask
}

/**
 * Speech send task data class
 */
data class SpeechSendTask(
    val phoneNumber: String,
    val senderName: String,
    val audioPath: String,
    val queueId: Long,
    val timestamp: Long
)
