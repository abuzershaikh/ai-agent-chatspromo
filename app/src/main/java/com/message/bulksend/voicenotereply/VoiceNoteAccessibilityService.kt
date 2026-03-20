package com.message.bulksend.voicenotereply

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Accessibility Service for Voice Note Reply
 * Automates the process of extracting voice notes from WhatsApp
 * 
 * Flow:
 * 1. Detect voice note notification
 * 2. Open WhatsApp chat
 * 3. Find voice note by duration
 * 4. Long press on voice note (1 second)
 * 5. Click 3-dot menu
 * 6. Click "Share"
 * 7. Select Chatspromo app
 * 8. Receive and save voice note
 */
class VoiceNoteAccessibilityService : AccessibilityService() {
    
    companion object {
        private const val TAG = "VoiceNoteAccessibility"
        private const val WHATSAPP_PACKAGE = "com.whatsapp"
        private const val WHATSAPP_BUSINESS_PACKAGE = "com.whatsapp.w4b"
        
        @Volatile
        private var isProcessing = false
        
        @Volatile
        private var targetPhoneNumber: String? = null
        
        @Volatile
        private var targetDuration: String? = null
        
        /**
         * Start voice note extraction process
         */
        fun startExtraction(context: Context, phoneNumber: String, duration: String) {
            if (isProcessing) {
                Log.w(TAG, "Already processing a voice note")
                return
            }
            
            isProcessing = true
            targetPhoneNumber = phoneNumber
            targetDuration = duration
            
            Log.d(TAG, "🎤 Starting voice note extraction")
            Log.d(TAG, "📞 Phone: $phoneNumber")
            Log.d(TAG, "⏱️ Duration: $duration")
            
            // Open WhatsApp chat
            openWhatsAppChat(context, phoneNumber)
        }
        
        /**
         * Open WhatsApp chat with specific contact
         */
        private fun openWhatsAppChat(context: Context, phoneNumber: String) {
            try {
                val cleanNumber = phoneNumber.replace("+", "").replace(" ", "").replace("-", "")
                
                // Try WhatsApp Business first
                val whatsappPackage = if (isPackageInstalled(context, WHATSAPP_BUSINESS_PACKAGE)) {
                    WHATSAPP_BUSINESS_PACKAGE
                } else {
                    WHATSAPP_PACKAGE
                }
                
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    data = Uri.parse("https://wa.me/$cleanNumber")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    setPackage(whatsappPackage)
                }
                
                context.startActivity(intent)
                Log.d(TAG, "📱 Opening WhatsApp chat: $phoneNumber")
                
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error opening WhatsApp: ${e.message}")
                isProcessing = false
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
         * Reset processing state
         */
        fun reset() {
            isProcessing = false
            targetPhoneNumber = null
            targetDuration = null
            Log.d(TAG, "🔄 Reset processing state")
        }
    }
    
    private val handler = Handler(Looper.getMainLooper())
    private var currentStep = Step.IDLE
    
    private enum class Step {
        IDLE,
        WAITING_FOR_CHAT,
        FINDING_VOICE_NOTE,
        LONG_PRESSING,
        CLICKING_MENU,
        CLICKING_SHARE,
        SELECTING_APP,
        COMPLETED
    }
    
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || !isProcessing) return
        
        val packageName = event.packageName?.toString()
        
        // Only process WhatsApp events
        if (packageName != WHATSAPP_PACKAGE && packageName != WHATSAPP_BUSINESS_PACKAGE) {
            return
        }
        
        try {
            when (currentStep) {
                Step.IDLE -> {
                    // Waiting to start
                }
                Step.WAITING_FOR_CHAT -> {
                    // Wait for chat to open
                    if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
                        Log.d(TAG, "✅ Chat opened, finding voice note...")
                        currentStep = Step.FINDING_VOICE_NOTE
                        handler.postDelayed({
                            findAndProcessVoiceNote()
                        }, 2000) // Wait 2 seconds for chat to load
                    }
                }
                Step.FINDING_VOICE_NOTE -> {
                    // Handled in findAndProcessVoiceNote()
                }
                Step.LONG_PRESSING -> {
                    // Handled in performLongPress()
                }
                Step.CLICKING_MENU -> {
                    // Handled in clickThreeDotMenu()
                }
                Step.CLICKING_SHARE -> {
                    // Handled in clickShareOption()
                }
                Step.SELECTING_APP -> {
                    // Handled in selectChatspromoApp()
                }
                Step.COMPLETED -> {
                    // Done
                    reset()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error in accessibility event: ${e.message}")
            reset()
        }
    }
    
    /**
     * Find voice note by duration and process it
     */
    private fun findAndProcessVoiceNote() {
        try {
            val rootNode = rootInActiveWindow ?: run {
                Log.e(TAG, "❌ No root node available")
                reset()
                return
            }
            
            Log.d(TAG, "🔍 Searching for voice note with duration: $targetDuration")
            
            // Find voice note by duration text
            val durationNodes = rootNode.findAccessibilityNodeInfosByText(targetDuration)
            
            if (durationNodes.isEmpty()) {
                Log.w(TAG, "⚠️ Voice note duration not found, retrying...")
                handler.postDelayed({
                    findAndProcessVoiceNote()
                }, 1000)
                return
            }
            
            Log.d(TAG, "✅ Found voice note duration: $targetDuration")
            
            // Find the voice note container (parent of duration text)
            val voiceNoteNode = findVoiceNoteContainer(durationNodes[0])
            
            if (voiceNoteNode == null) {
                Log.e(TAG, "❌ Voice note container not found")
                reset()
                return
            }
            
            Log.d(TAG, "✅ Found voice note container")
            
            // Perform long press on voice note
            currentStep = Step.LONG_PRESSING
            performLongPress(voiceNoteNode)
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error finding voice note: ${e.message}")
            reset()
        }
    }
    
    /**
     * Find voice note container from duration node
     */
    private fun findVoiceNoteContainer(durationNode: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var current: AccessibilityNodeInfo? = durationNode
        
        // Traverse up to find clickable parent (voice note container)
        for (i in 0..5) {
            if (current == null) break
            
            if (current.isClickable && current.isLongClickable) {
                return current
            }
            
            current = current.parent
        }
        
        return null
    }
    
    /**
     * Perform long press on voice note
     */
    private fun performLongPress(node: AccessibilityNodeInfo) {
        try {
            Log.d(TAG, "👆 Performing long press on voice note...")
            
            val success = node.performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK)
            
            if (success) {
                Log.d(TAG, "✅ Long press successful")
                currentStep = Step.CLICKING_MENU
                
                // Wait for menu to appear
                handler.postDelayed({
                    clickThreeDotMenu()
                }, 1000)
            } else {
                Log.e(TAG, "❌ Long press failed")
                reset()
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error performing long press: ${e.message}")
            reset()
        }
    }
    
    /**
     * Click 3-dot menu button
     */
    private fun clickThreeDotMenu() {
        try {
            val rootNode = rootInActiveWindow ?: run {
                Log.e(TAG, "❌ No root node for menu")
                reset()
                return
            }
            
            Log.d(TAG, "🔍 Looking for 3-dot menu...")
            
            // Find 3-dot menu (More options button)
            val menuNodes = findNodesByContentDescription(rootNode, "More options")
            
            if (menuNodes.isEmpty()) {
                Log.w(TAG, "⚠️ 3-dot menu not found, retrying...")
                handler.postDelayed({
                    clickThreeDotMenu()
                }, 500)
                return
            }
            
            Log.d(TAG, "✅ Found 3-dot menu")
            
            val success = menuNodes[0].performAction(AccessibilityNodeInfo.ACTION_CLICK)
            
            if (success) {
                Log.d(TAG, "✅ Clicked 3-dot menu")
                currentStep = Step.CLICKING_SHARE
                
                // Wait for menu to open
                handler.postDelayed({
                    clickShareOption()
                }, 1000)
            } else {
                Log.e(TAG, "❌ Failed to click menu")
                reset()
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error clicking menu: ${e.message}")
            reset()
        }
    }
    
    /**
     * Click "Share" option in menu
     */
    private fun clickShareOption() {
        try {
            val rootNode = rootInActiveWindow ?: run {
                Log.e(TAG, "❌ No root node for share")
                reset()
                return
            }
            
            Log.d(TAG, "🔍 Looking for Share option...")
            
            // Find "Share" text
            val shareNodes = rootNode.findAccessibilityNodeInfosByText("Share")
            
            if (shareNodes.isEmpty()) {
                Log.w(TAG, "⚠️ Share option not found, retrying...")
                handler.postDelayed({
                    clickShareOption()
                }, 500)
                return
            }
            
            Log.d(TAG, "✅ Found Share option")
            
            // Find clickable parent
            val clickableNode = findClickableParent(shareNodes[0])
            
            if (clickableNode == null) {
                Log.e(TAG, "❌ Share button not clickable")
                reset()
                return
            }
            
            val success = clickableNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            
            if (success) {
                Log.d(TAG, "✅ Clicked Share")
                currentStep = Step.SELECTING_APP
                
                // Wait for share sheet to open
                handler.postDelayed({
                    selectChatspromoApp()
                }, 1500)
            } else {
                Log.e(TAG, "❌ Failed to click Share")
                reset()
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error clicking share: ${e.message}")
            reset()
        }
    }
    
    /**
     * Select Chatspromo app from share sheet
     */
    private fun selectChatspromoApp() {
        try {
            val rootNode = rootInActiveWindow ?: run {
                Log.e(TAG, "❌ No root node for app selection")
                reset()
                return
            }
            
            Log.d(TAG, "🔍 Looking for Chatspromo app...")
            
            // Find "Chatspromo" or app name
            val appNodes = rootNode.findAccessibilityNodeInfosByText("Chatspromo")
            
            if (appNodes.isEmpty()) {
                Log.w(TAG, "⚠️ Chatspromo app not found in share sheet")
                // Try alternative names
                val altNodes = rootNode.findAccessibilityNodeInfosByText("Bulk Send")
                if (altNodes.isEmpty()) {
                    Log.e(TAG, "❌ App not found in share sheet")
                    reset()
                    return
                }
                
                val clickableNode = findClickableParent(altNodes[0])
                clickableNode?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            } else {
                Log.d(TAG, "✅ Found Chatspromo app")
                
                val clickableNode = findClickableParent(appNodes[0])
                
                if (clickableNode != null) {
                    val success = clickableNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    
                    if (success) {
                        Log.d(TAG, "✅ Selected Chatspromo app")
                        currentStep = Step.COMPLETED
                        
                        // Save info
                        targetPhoneNumber?.let { phone ->
                            targetDuration?.let { duration ->
                                VoiceNoteReplyManager.saveLastVoiceNote(this, phone, duration)
                            }
                        }
                        
                        // Reset after completion
                        handler.postDelayed({
                            reset()
                        }, 2000)
                    } else {
                        Log.e(TAG, "❌ Failed to select app")
                        reset()
                    }
                } else {
                    Log.e(TAG, "❌ App not clickable")
                    reset()
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error selecting app: ${e.message}")
            reset()
        }
    }
    
    /**
     * Find nodes by content description
     */
    private fun findNodesByContentDescription(
        root: AccessibilityNodeInfo,
        description: String
    ): List<AccessibilityNodeInfo> {
        val result = mutableListOf<AccessibilityNodeInfo>()
        
        if (root.contentDescription?.toString()?.contains(description, ignoreCase = true) == true) {
            result.add(root)
        }
        
        for (i in 0 until root.childCount) {
            val child = root.getChild(i) ?: continue
            result.addAll(findNodesByContentDescription(child, description))
        }
        
        return result
    }
    
    /**
     * Find clickable parent of a node
     */
    private fun findClickableParent(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var current: AccessibilityNodeInfo? = node
        
        for (i in 0..5) {
            if (current == null) break
            
            if (current.isClickable) {
                return current
            }
            
            current = current.parent
        }
        
        return null
    }
    
    override fun onInterrupt() {
        Log.d(TAG, "⚠️ Service interrupted")
        reset()
    }
    
    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "✅ Voice Note Accessibility Service connected")
    }
}
