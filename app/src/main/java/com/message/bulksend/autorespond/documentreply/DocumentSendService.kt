package com.message.bulksend.autorespond.documentreply

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.content.FileProvider
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Service to send documents using accessibility service
 * Integrates with existing WhatsAppAutoSendService functionality
 */
class DocumentSendService private constructor() {
    
    companion object {
        const val TAG = "DocumentSendService"
        private const val PREFS_NAME = "document_send_queue"
        private const val KEY_SEND_QUEUE = "send_queue"
        private const val DEFAULT_SHARE_LAUNCH_DELAY_MS = 2500L
        private const val VIDEO_SHARE_LAUNCH_DELAY_MS = 3500L
        private const val SEND_CONFIRMATION_POLL_MS = 500L
        private const val SEND_CONFIRMATION_TIMEOUT_MS = 12000L
        private const val VIDEO_SEND_CONFIRMATION_TIMEOUT_MS = 20000L
        private const val POST_SEND_SETTLE_DELAY_MS = 1500L
        private const val NEXT_DOCUMENT_DELAY_MS = 1500L
        
        @Volatile
        private var INSTANCE: DocumentSendService? = null
        
        @Volatile
        private var isDocumentSendEnabled = false
        
        // Queue for document send tasks
        private val sendQueue = ConcurrentLinkedQueue<DocumentSendTask>()
        
        // Current task being processed
        @Volatile
        private var currentTask: DocumentSendTask? = null
        
        fun getInstance(): DocumentSendService {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: DocumentSendService().also { INSTANCE = it }
            }
        }
        
        fun enableDocumentSend() {
            isDocumentSendEnabled = true
            Log.d(TAG, "✅ Document send ENABLED")
        }
        
        fun disableDocumentSend() {
            isDocumentSendEnabled = false
            isDocumentSendEnabled = false
            Log.d(TAG, "❌ Document send DISABLED")
            // Instance access to release lock if needed - tricky since methods are instance
            INSTANCE?.releaseWakeLock()
        }
        
        fun isDocumentSendEnabled(): Boolean = isDocumentSendEnabled
    }
    
    private val handler = Handler(Looper.getMainLooper())
    private val gson = Gson()
    
    // Wake lock for document sending
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
                
                wakeLock = pm.newWakeLock(flags, "BulkSend:DocumentSendService").apply {
                    acquire(10 * 60 * 1000L) // 10 minutes max safety timeout
                }
                Log.d(TAG, "✅ WakeLock acquired (screen on)")
            }
            
            // 2. Dismiss keyguard (lock screen) so accessibility can interact
            try {
                @Suppress("DEPRECATION")
                val km = context.getSystemService(Context.KEYGUARD_SERVICE) as android.app.KeyguardManager
                @Suppress("DEPRECATION")
                keyguardLock = km.newKeyguardLock("BulkSend:DocumentSend")
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
     * Add document send task to queue
     */
    fun addDocumentSendTask(
        context: Context,
        phoneNumber: String,
        senderName: String,
        keyword: String,
        documentPaths: List<String> = emptyList(),
        documentType: DocumentType? = null,
        documents: List<DocumentFile> = emptyList()
    ) {
        val task = DocumentSendTask(
            phoneNumber = phoneNumber,
            senderName = senderName,
            keyword = keyword,
            documentPaths = documentPaths,
            documentType = documentType,
            documents = documents
        )
        
        sendQueue.offer(task)
        saveQueueToPrefs(context)
        
        Log.d(
            TAG,
            "📄 Document send task added to queue: $phoneNumber, keyword: $keyword, files: ${documentPaths.size + documents.size}"
        )
        
        // Start processing if not already processing
        if (currentTask == null) {
            processNextTask(context)
        }
    }
    
    /**
     * Process next task in queue
     */
    private fun processNextTask(context: Context) {
        if (!isDocumentSendEnabled) {
            Log.d(TAG, "Document send is disabled, skipping task processing")
            return
        }
        
        val task = sendQueue.poll()
        if (task == null) {
            Log.d(TAG, "No more tasks in queue")
            currentTask = null
            
            // Disable auto-send service when no more tasks
            com.message.bulksend.bulksend.CampaignState.isAutoSendEnabled = false
            com.message.bulksend.bulksend.WhatsAppAutoSendService.deactivateService()
            com.message.bulksend.bulksend.WhatsAppAutoSendService.deactivateService()
            Log.d(TAG, "❌ Auto-send service DISABLED (no more document tasks)")
            
            // Release wake lock as queue is empty
            releaseWakeLock()
            return
        }
        
        currentTask = task
        Log.d(TAG, "🚀 Processing document send task: ${task.phoneNumber}")
        
        // Acquire wake lock to keep screen on and CPU running
        acquireWakeLock(context)
        
        // Enable auto-send service for accessibility button clicking
        com.message.bulksend.bulksend.CampaignState.isAutoSendEnabled = true
        com.message.bulksend.bulksend.WhatsAppAutoSendService.activateService()
        Log.d(TAG, "✅ Auto-send service ENABLED for document sending")
        
        // Update task status
        updateTaskStatus(context, task.copy(status = DocumentSendStatus.SENDING))
        
        // Start sending documents for this task
        sendDocumentsToContact(context, task)
    }
    
    /**
     * Send documents to contact using accessibility service
     */
    private fun sendDocumentsToContact(context: Context, task: DocumentSendTask) {
        try {
            // Send documents sequentially
            sendDocumentsSequentially(context, task, 0)
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error sending documents: ${e.message}")
            updateTaskStatus(context, task.copy(
                status = DocumentSendStatus.FAILED,
                errorMessage = e.message
            ))
            
            // Process next task
            handler.postDelayed({
                processNextTask(context)
            }, 1000)
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
     * Send documents sequentially (one by one)
     */
    private fun sendDocumentsSequentially(context: Context, task: DocumentSendTask, index: Int) {
        val allDocuments = task.getAllDocuments()
        val allPaths = task.getAllDocumentPaths()
        
        if (index >= allPaths.size) {
            // All documents sent successfully
            Log.d(TAG, "✅ All documents sent for task: ${task.phoneNumber}")
            updateTaskStatus(context, task.copy(
                status = DocumentSendStatus.SENT,
                sentAt = System.currentTimeMillis()
            ))
            com.message.bulksend.bulksend.CampaignState.isSendActionSuccessful = null
            com.message.bulksend.bulksend.CampaignState.sendFailureReason = null
            handler.postDelayed({
                bringAppToForeground(context, "document-task-complete")
                handler.postDelayed({
                    processNextTask(context)
                }, 1000)
            }, 500)
            return
        }
        
        val documentPath = allPaths[index]
        
        // Determine document type
        val documentType = if (allDocuments.isNotEmpty() && index < allDocuments.size) {
            allDocuments[index].documentType
        } else {
            task.documentType ?: DocumentType.IMAGE // Fallback for backward compatibility
        }
        
        Log.d(TAG, "📎 Sending document ${index + 1}/${allPaths.size}: $documentPath (${documentType.name})")
        com.message.bulksend.bulksend.CampaignState.isSendActionSuccessful = null
        com.message.bulksend.bulksend.CampaignState.sendFailureReason = null

        // Send single document using BulksendActivity approach
        sendSingleDocumentLikeBulksend(context, documentPath, documentType, task.phoneNumber) { launched ->
            if (!launched) {
                failCurrentTask(
                    context = context,
                    task = task,
                    reason = "Failed to launch ${documentType.name.lowercase()} share flow"
                )
                return@sendSingleDocumentLikeBulksend
            }

            waitForSendConfirmation(
                context = context,
                task = task,
                index = index,
                documentType = documentType
            )
        }
    }

    private fun waitForSendConfirmation(
        context: Context,
        task: DocumentSendTask,
        index: Int,
        documentType: DocumentType,
        startedAt: Long = System.currentTimeMillis()
    ) {
        when (com.message.bulksend.bulksend.CampaignState.isSendActionSuccessful) {
            true -> {
                Log.d(
                    TAG,
                    "WhatsApp confirmed send for document ${index + 1}/${task.getAllDocumentPaths().size} (${documentType.name})"
                )
                com.message.bulksend.bulksend.CampaignState.isSendActionSuccessful = null
                com.message.bulksend.bulksend.CampaignState.sendFailureReason = null
                handler.postDelayed({
                    bringAppToForeground(context, "document-send-confirmed")
                    handler.postDelayed({
                        sendDocumentsSequentially(context, task, index + 1)
                    }, NEXT_DOCUMENT_DELAY_MS)
                }, POST_SEND_SETTLE_DELAY_MS)
            }

            false -> {
                val failureReason =
                    com.message.bulksend.bulksend.CampaignState.sendFailureReason
                        ?: "WhatsApp send button was not confirmed"
                com.message.bulksend.bulksend.CampaignState.isSendActionSuccessful = null
                com.message.bulksend.bulksend.CampaignState.sendFailureReason = null
                failCurrentTask(context, task, failureReason)
            }

            null -> {
                val timeoutMs = if (documentType == DocumentType.VIDEO) {
                    VIDEO_SEND_CONFIRMATION_TIMEOUT_MS
                } else {
                    SEND_CONFIRMATION_TIMEOUT_MS
                }

                if ((System.currentTimeMillis() - startedAt) >= timeoutMs) {
                    val reason = if (documentType == DocumentType.VIDEO) {
                        "Video send timed out before WhatsApp confirmed send"
                    } else {
                        "Document send timed out before WhatsApp confirmed send"
                    }
                    failCurrentTask(context, task, reason)
                    return
                }

                handler.postDelayed({
                    waitForSendConfirmation(context, task, index, documentType, startedAt)
                }, SEND_CONFIRMATION_POLL_MS)
            }
        }
    }

    private fun failCurrentTask(
        context: Context,
        task: DocumentSendTask,
        reason: String
    ) {
        Log.e(TAG, "Document send failed for ${task.phoneNumber}: $reason")
        updateTaskStatus(context, task.copy(
            status = DocumentSendStatus.FAILED,
            errorMessage = reason
        ))
        com.message.bulksend.bulksend.CampaignState.isSendActionSuccessful = null
        com.message.bulksend.bulksend.CampaignState.sendFailureReason = null
        handler.postDelayed({
            bringAppToForeground(context, "document-send-failed")
            handler.postDelayed({
                processNextTask(context)
            }, 1000)
        }, 500)
    }
    
    /**
     * Open WhatsApp with specific contact
     */
    private fun openWhatsAppContact(context: Context, phoneNumber: String) {
        try {
            // Clean phone number (remove + and spaces)
            val cleanNumber = phoneNumber.replace("+", "").replace(" ", "").replace("-", "")
            
            // Get available WhatsApp package
            val whatsappPackage = getAvailableWhatsAppPackage(context)
            if (whatsappPackage == null) {
                Log.e(TAG, "❌ No WhatsApp installed")
                throw Exception("No WhatsApp installed")
            }
            
            // Create WhatsApp intent
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse("https://wa.me/$cleanNumber")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                setPackage(whatsappPackage)
            }
            
            context.startActivity(intent)
            Log.d(TAG, "📱 Opening $whatsappPackage for: $phoneNumber")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error opening WhatsApp: ${e.message}")
            throw e
        }
    }
    
    /**
     * Send single document using accessibility service
     */
    /**
     * Send single document using BulksendActivity approach
     */
    private fun sendSingleDocumentLikeBulksend(
        context: Context,
        documentPath: String,
        documentType: DocumentType,
        phoneNumber: String,
        onShareFlowStarted: (Boolean) -> Unit
    ) {
        try {
            Log.d(TAG, "Attempting to send document: $documentPath")

            val mediaToSend = resolveShareUri(context, documentPath)
            if (mediaToSend == null) {
                Log.e(TAG, "Document URI resolve failed: $documentPath")
                onShareFlowStarted(false)
                return
            }
            val mediaDisplayName = resolveDisplayName(mediaToSend)
            Log.d(TAG, "Resolved media URI: $mediaToSend")
            Log.d(TAG, "Resolved media name: $mediaDisplayName")

            // Get WhatsApp package name using WhatsPref (same as BulksendActivity)
            var packageName = com.message.bulksend.utils.WhatsPref.getSelectedPackage(context)

            // Fallback: Check if selected package is actually installed
            val packageManager = context.packageManager
            try {
                packageManager.getPackageInfo(packageName, 0)
                Log.d(TAG, "Selected package is installed: $packageName")
            } catch (e: Exception) {
                Log.w(TAG, "Selected package not installed: $packageName, checking alternatives...")

                try {
                    packageManager.getPackageInfo("com.whatsapp.w4b", 0)
                    packageName = "com.whatsapp.w4b"
                    Log.d(TAG, "Using WhatsApp Business: $packageName")
                } catch (e2: Exception) {
                    try {
                        packageManager.getPackageInfo("com.whatsapp", 0)
                        packageName = "com.whatsapp"
                        Log.d(TAG, "Using WhatsApp: $packageName")
                    } catch (e3: Exception) {
                        Log.e(TAG, "No WhatsApp found")
                        onShareFlowStarted(false)
                        return
                    }
                }
            }

            val cleanNumber = phoneNumber.replace("+", "").replace(" ", "").replace("-", "")
            context.grantUriPermission(packageName, mediaToSend, Intent.FLAG_GRANT_READ_URI_PERMISSION)

            val openChatIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$cleanNumber")).apply {
                setPackage(packageName)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(openChatIntent)
            val shareLaunchDelayMs = if (documentType == DocumentType.VIDEO) {
                VIDEO_SHARE_LAUNCH_DELAY_MS
            } else {
                DEFAULT_SHARE_LAUNCH_DELAY_MS
            }

            handler.postDelayed({
                try {
                    val mimeType = resolveMimeType(documentPath, documentType)

                    val sendMediaIntent = Intent(Intent.ACTION_SEND).apply {
                        putExtra(Intent.EXTRA_STREAM, mediaToSend)
                        type = mimeType
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        putExtra("jid", "$cleanNumber@s.whatsapp.net")
                        setPackage(packageName)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        clipData = android.content.ClipData.newRawUri("shared_media", mediaToSend)
                    }

                    context.startActivity(sendMediaIntent)
                    Log.d(TAG, "Document share intent launched: $mediaDisplayName mime=$mimeType")
                    onShareFlowStarted(true)
                } catch (e: Exception) {
                    Log.e(TAG, "Error in media send step: ${e.message}", e)
                    onShareFlowStarted(false)
                }
            }, shareLaunchDelayMs)

        } catch (e: Exception) {
            Log.e(TAG, "Error sharing document: ${e.message}", e)
            Log.e(TAG, "Document path: $documentPath")
            Log.e(TAG, "Document type: $documentType")
            onShareFlowStarted(false)
        }
    }

    private fun resolveShareUri(context: Context, documentPath: String): Uri? {
        return try {
            when {
                documentPath.startsWith("content://", ignoreCase = true) -> Uri.parse(documentPath)
                documentPath.startsWith("file://", ignoreCase = true) -> {
                    val filePath = Uri.parse(documentPath).path ?: return null
                    val file = File(filePath)
                    if (!file.exists()) return null
                    FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
                }
                else -> {
                    val file = File(documentPath)
                    if (!file.exists()) return null
                    FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error resolving share URI: ${e.message}", e)
            null
        }
    }

    private fun resolveDisplayName(uri: Uri): String {
        return uri.lastPathSegment?.takeIf { it.isNotBlank() } ?: "media"
    }

    private fun resolveMimeType(documentPath: String, documentType: DocumentType): String {
        val extension = documentPath.substringAfterLast('.', "").lowercase()
        return when (extension) {
            "csv" -> "text/csv"
            "vcf" -> "text/x-vcard"
            "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            "xls" -> "application/vnd.ms-excel"
            "pdf" -> "application/pdf"
            "mp3" -> "audio/mpeg"
            "wav" -> "audio/wav"
            "aac" -> "audio/aac"
            "m4a" -> "audio/mp4"
            "ogg" -> "audio/ogg"
            "mp4" -> "video/mp4"
            "3gp", "3gpp" -> "video/3gpp"
            "mov" -> "video/quicktime"
            "mkv" -> "video/x-matroska"
            "avi" -> "video/x-msvideo"
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "webp" -> "image/webp"
            "gif" -> "image/gif"
            else -> when (documentType) {
                DocumentType.IMAGE -> "image/*"
                DocumentType.VIDEO -> "video/*"
                DocumentType.PDF -> "application/pdf"
                DocumentType.AUDIO -> "audio/*"
            }
        }
    }

    private fun bringAppToForeground(context: Context, reason: String) {
        try {
            Log.d(TAG, "Returning to app ($reason)")
            val homeIntent = Intent(context, com.message.bulksend.MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(homeIntent)
        } catch (e: Exception) {
            Log.e(TAG, "Error navigating to home: ${e.message}", e)
        }
    }
    
    /**
     * Handle accessibility events for document sending
     */
    fun handleAccessibilityEvent(event: AccessibilityEvent?, accessibilityService: AccessibilityService) {
        if (event == null || !isDocumentSendEnabled || currentTask == null) return
        
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
     * Handle "Share with" dialog for document sharing
     */
    private fun handleShareWithDialog(rootNode: AccessibilityNodeInfo, accessibilityService: AccessibilityService): Boolean {
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
                        node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                        return true
                    }
                    // Try clicking parent if node itself is not clickable
                    val parent = node.parent
                    if (parent != null && parent.isClickable && parent.isVisibleToUser) {
                        Log.d(TAG, "✅ Clicking OK/Send button parent")
                        parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                        return true
                    }
                }
            }
            
            // Method 2: Find button by resource ID
            val buttonIds = listOf(
                "android:id/button1",
                "android:id/button_positive", 
                "com.whatsapp:id/send",
                "com.whatsapp.w4b:id/send",
                "com.whatsapp:id/send_btn",
                "com.whatsapp.w4b:id/send_btn",
                "com.whatsapp:id/ok",
                "com.whatsapp.w4b:id/ok",
                "com.whatsapp:id/positive_button",
                "com.whatsapp.w4b:id/positive_button"
            )
            
            for (id in buttonIds) {
                val nodes = rootNode.findAccessibilityNodeInfosByViewId(id)
                if (nodes.isNotEmpty()) {
                    val button = nodes[0]
                    if (button.isVisibleToUser && button.isClickable) {
                        Log.d(TAG, "✅ Clicking button by ID: $id")
                        button.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                        return true
                    }
                }
            }
            
            // Method 3: Find any clickable button in the dialog
            val clickableButtons = findClickableButtons(rootNode)
            for (button in clickableButtons) {
                val buttonText = button.text?.toString() ?: button.contentDescription?.toString() ?: ""
                val viewId = button.viewIdResourceName ?: ""
                if (buttonText.isNotEmpty() && 
                    (buttonText.contains("OK", ignoreCase = true) || 
                     buttonText.contains("Send", ignoreCase = true) ||
                     buttonText.contains("Share", ignoreCase = true) ||
                     viewId.contains("send", ignoreCase = true))) {
                    Log.d(TAG, "✅ Clicking button with text: $buttonText")
                    button.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    return true
                }
            }

            // Method 4: WhatsApp Business fallback - click likely send icon near bottom-right.
            findLikelySendButtonFallback(rootNode)?.let { fallbackButton ->
                Log.d(
                    TAG,
                    "✅ Clicking fallback send candidate: class=${fallbackButton.className}, id=${fallbackButton.viewIdResourceName}, desc=${fallbackButton.contentDescription}"
                )
                val clicked = fallbackButton.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                if (clicked) return true
            }
            
            Log.d(TAG, "⚠️ Share dialog detected but no clickable button found")
            return false
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error handling share dialog: ${e.message}")
            return false
        }
    }
    
    /**
     * Find all clickable buttons in the node tree
     */
    private fun findClickableButtons(node: AccessibilityNodeInfo): List<AccessibilityNodeInfo> {
        val buttons = mutableListOf<AccessibilityNodeInfo>()
        
        if (node.isVisibleToUser && (node.isClickable || node.isCheckable)) {
            buttons.add(node)
        }
        
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null) {
                buttons.addAll(findClickableButtons(child))
            }
        }
        
        return buttons
    }

    private fun findLikelySendButtonFallback(rootNode: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val rootBounds = Rect()
        rootNode.getBoundsInScreen(rootBounds)
        if (rootBounds.isEmpty) return null

        val minBottom = rootBounds.top + ((rootBounds.height()) * 0.55f).toInt()
        val minRight = rootBounds.left + ((rootBounds.width()) * 0.55f).toInt()
        var candidate: AccessibilityNodeInfo? = null
        var candidateScore = Int.MIN_VALUE

        for (node in findClickableButtons(rootNode)) {
            if (!node.isVisibleToUser || !node.isEnabled) continue

            val className = node.className?.toString().orEmpty()
            val desc = node.contentDescription?.toString().orEmpty()
            val text = node.text?.toString().orEmpty()
            val viewId = node.viewIdResourceName?.lowercase().orEmpty()

            val isSendLikeBySemantics =
                desc.contains("send", ignoreCase = true) ||
                    text.contains("send", ignoreCase = true) ||
                    text.contains("share", ignoreCase = true) ||
                    viewId.contains("send")

            val isIconButtonClass =
                className.contains("ImageButton", ignoreCase = true) ||
                    className.contains("FloatingActionButton", ignoreCase = true) ||
                    className.contains("MaterialButton", ignoreCase = true) ||
                    className.contains("Button", ignoreCase = true)

            if (!isSendLikeBySemantics && !isIconButtonClass) continue

            val bounds = Rect()
            node.getBoundsInScreen(bounds)
            if (bounds.isEmpty) continue
            if (bounds.bottom < minBottom || bounds.right < minRight) continue

            val score = bounds.right + bounds.bottom + if (isSendLikeBySemantics) 10_000 else 0
            if (score > candidateScore) {
                candidateScore = score
                candidate = node
            }
        }

        return candidate
    }
    
    /**
     * Update task status and save to preferences
     */
    private fun updateTaskStatus(context: Context, updatedTask: DocumentSendTask) {
        // Update current task if it matches
        if (currentTask?.id == updatedTask.id) {
            currentTask = updatedTask
        }
        
        saveQueueToPrefs(context)
        
        // Broadcast status update
        val intent = Intent("com.message.bulksend.DOCUMENT_SEND_STATUS").apply {
            putExtra("task_id", updatedTask.id)
            putExtra("status", updatedTask.status.name)
            putExtra("phone_number", updatedTask.phoneNumber)
        }
        context.sendBroadcast(intent)
    }
    
    /**
     * Save queue to SharedPreferences
     */
    private fun saveQueueToPrefs(context: Context) {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val queueList = sendQueue.toList()
            val json = gson.toJson(queueList)
            prefs.edit().putString(KEY_SEND_QUEUE, json).apply()
        } catch (e: Exception) {
            Log.e(TAG, "Error saving queue to prefs: ${e.message}")
        }
    }
    
    /**
     * Load queue from SharedPreferences
     */
    fun loadQueueFromPrefs(context: Context) {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val json = prefs.getString(KEY_SEND_QUEUE, null) ?: return
            
            val type = object : TypeToken<List<DocumentSendTask>>() {}.type
            val queueList: List<DocumentSendTask> = gson.fromJson(json, type) ?: return
            
            sendQueue.clear()
            sendQueue.addAll(queueList.filter { 
                it.status == DocumentSendStatus.PENDING || it.status == DocumentSendStatus.SENDING 
            })
            
            Log.d(TAG, "📥 Loaded ${sendQueue.size} tasks from preferences")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error loading queue from prefs: ${e.message}")
        }
    }
    
    /**
     * Get current queue status
     */
    fun getQueueStatus(): Pair<Int, DocumentSendTask?> {
        return Pair(sendQueue.size, currentTask)
    }
    
    /**
     * Clear all tasks from queue
     */
    fun clearQueue(context: Context) {
        sendQueue.clear()
        currentTask = null
        saveQueueToPrefs(context)
        Log.d(TAG, "🗑️ Queue cleared")
    }
    
    /**
     * Cancel current task
     */
    fun cancelCurrentTask(context: Context) {
        currentTask?.let { task ->
            updateTaskStatus(context, task.copy(status = DocumentSendStatus.CANCELLED))
            currentTask = null
            
            // Process next task
            handler.postDelayed({
                processNextTask(context)
            }, 1000)
        }
    }
}





