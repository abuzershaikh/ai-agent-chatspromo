package com.message.bulksend.bulksend

import android.accessibilityservice.AccessibilityService
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.message.bulksend.autorespond.statusscheduled.StatusAutoScheduledState
import java.util.*
import java.util.concurrent.Executors
import java.util.regex.Pattern

class WhatsAppAutoSendService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private var extractionRunnable: Runnable? = null
    private var statusSecondClickRunnable: Runnable? = null
    private val DEBOUNCE_DELAY_MS = 200L

    // Background thread executor for contact extraction
    private val extractionExecutor = Executors.newSingleThreadExecutor()

    companion object {
        private const val TAG = "AutoSendService"
        const val ACTION_TEXT_CAPTURED = "com.message.bulksend.TEXT_CAPTURED"
        const val EXTRA_CAPTURED_TEXT = "CAPTURED_TEXT"
        private const val PREFS_NAME = "CapturedTextPrefs"
        private const val PREFS_KEY = "UniqueTexts"

        // New SharedPreferences for extracted numbers
        private const val NUMBERS_PREFS_NAME = "ExtractedNumbersPrefs"
        private const val NUMBERS_PREFS_KEY = "ExtractedNumbers"
        private const val NUMBERS_COUNT_KEY = "NumbersCount"

        @Volatile
        private var isServiceActive = false

        @Volatile
        private var isContactExtractionEnabled = false

        val extractedTexts = HashSet<String>()
        val extractedNumbers = HashSet<String>()

        fun activateService() {
            isServiceActive = true
            Log.d(TAG, "âœ… Service ACTIVATED autoSend=${CampaignState.isAutoSendEnabled}")
        }

        fun deactivateService() {
            isServiceActive = false
            Log.d(TAG, "âŒ Service DEACTIVATED autoSend=${CampaignState.isAutoSendEnabled}")
        }

        fun isActive(): Boolean = isServiceActive

        fun enableContactExtraction() {
            isContactExtractionEnabled = true
            // Clear previous data when enabling
            extractedTexts.clear()
            extractedNumbers.clear()
            Log.d(TAG, "âœ… Contact Extraction ENABLED (data cleared)")
        }

        fun disableContactExtraction() {
            isContactExtractionEnabled = false
            Log.d(TAG, "âŒ Contact Extraction DISABLED")
        }

        fun isContactExtractionActive(): Boolean = isContactExtractionEnabled

        fun clearExtractedData() {
            extractedTexts.clear()
            extractedNumbers.clear()
            Log.d(TAG, "ðŸ—‘ï¸ Extracted data cleared")
        }

        // New methods for SharedPreferences-based number sharing
        fun saveExtractedNumbers(context: Context, numbers: Set<String>) {
            val prefs = context.getSharedPreferences(NUMBERS_PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit()
                .putStringSet(NUMBERS_PREFS_KEY, numbers)
                .putInt(NUMBERS_COUNT_KEY, numbers.size)
                .apply()
            Log.d(TAG, "ðŸ’¾ Saved ${numbers.size} numbers to SharedPreferences")
        }

        fun getExtractedNumbers(context: Context): Set<String> {
            val prefs = context.getSharedPreferences(NUMBERS_PREFS_NAME, Context.MODE_PRIVATE)
            return prefs.getStringSet(NUMBERS_PREFS_KEY, emptySet()) ?: emptySet()
        }

        fun getExtractedNumbersCount(context: Context): Int {
            val prefs = context.getSharedPreferences(NUMBERS_PREFS_NAME, Context.MODE_PRIVATE)
            return prefs.getInt(NUMBERS_COUNT_KEY, 0)
        }

        fun clearSavedNumbers(context: Context) {
            val prefs = context.getSharedPreferences(NUMBERS_PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit()
                .remove(NUMBERS_PREFS_KEY)
                .remove(NUMBERS_COUNT_KEY)
                .apply()
            Log.d(TAG, "ðŸ—‘ï¸ Cleared saved numbers from SharedPreferences")
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        val packageName = event.packageName?.toString()

        // Log all events for debugging
        Log.d(TAG, "ðŸ“± Event received from: $packageName, type: ${event.eventType}")

        // Only process WhatsApp and WhatsApp Business
        if (packageName != "com.whatsapp" && packageName != "com.whatsapp.w4b") {
            return
        }

        Log.d(TAG, "âœ… WhatsApp event detected, extraction enabled: $isContactExtractionEnabled")

        // Handle Contact Extraction (if enabled)
        if (isContactExtractionEnabled) {
            Log.d(TAG, "ðŸ” Starting contact extraction...")
            handleContactExtraction(event)
        }

        // Handle Auto Send (if enabled and service active)
        if (isServiceActive && CampaignState.isAutoSendEnabled) {
            handleAutoSend(event)
        }
        
        // Handle Document Send (if enabled)
        if (com.message.bulksend.autorespond.documentreply.DocumentSendService.isDocumentSendEnabled()) {
            val documentSendService = com.message.bulksend.autorespond.documentreply.DocumentSendService.getInstance()
            documentSendService.handleAccessibilityEvent(event, this)
        }
        
        // Handle Speech Send (if enabled)
        if (com.message.bulksend.aiagent.tools.agentspeech.AgentSpeechSendService.isSpeechSendEnabled()) {
            val speechSendService = com.message.bulksend.aiagent.tools.agentspeech.AgentSpeechSendService.getInstance()
            speechSendService.handleAccessibilityEvent(event, this)
        }
    }

    private fun handleContactExtraction(event: AccessibilityEvent) {
        try {
            extractionRunnable?.let { handler.removeCallbacks(it) }

            extractionRunnable = Runnable {
                // Submit extraction task to background thread executor
                extractionExecutor.execute {
                    try {
                        Log.d(TAG, "ðŸ”„ Starting extraction on background thread: ${Thread.currentThread().name}")

                        val rootNode = rootInActiveWindow
                        if (rootNode != null) {
                            extractTextsFromNode(rootNode)
                            rootNode.recycle()

                            // Broadcast extracted numbers on main thread
                            if (extractedNumbers.isNotEmpty()) {
                                handler.post {
                                    Log.d(TAG, "âœ… Extracted ${extractedNumbers.size} numbers (broadcasted on main thread)")
                                    broadcastExtractedNumbers()
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "âŒ Error in background extraction: ${e.message}", e)
                    }
                }
            }
            handler.postDelayed(extractionRunnable!!, DEBOUNCE_DELAY_MS)
        } catch (e: Exception) {
            Log.e(TAG, "âŒ Error in contact extraction: ${e.message}", e)
        }
    }

    private fun handleAutoSend(event: AccessibilityEvent) {
        val isStatusFlowActive = StatusAutoScheduledState.isStatusFlowActive
        val isAllowedEvent = event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED ||
            event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
            (isStatusFlowActive && event.eventType == AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED)

        if (!isAllowedEvent) {
            return
        }

        val rootNode = rootInActiveWindow

        try {
            if (isStatusFlowActive) {
                Log.d(
                    TAG,
                    "[StatusSchedDbg] handleAutoSend eventType=${event.eventType} class=${event.className} " +
                        "clicksDone=${StatusAutoScheduledState.sendClicksCompleted} " +
                        "expired=${StatusAutoScheduledState.isExpired()} autoSend=${CampaignState.isAutoSendEnabled}"
                )
            }

            if (
                isStatusFlowActive &&
                StatusAutoScheduledState.sendClicksCompleted > 0 &&
                hasStatusSentConfirmation(event, null)
            ) {
                StatusAutoScheduledState.markSendConfirmed()
                CampaignState.isSendActionSuccessful = true
                clearPendingSecondStatusSendClick()
                Log.d(TAG, "[StatusSchedDbg] status confirmation detected from event text.")
                StatusAutoScheduledState.reset()
                CampaignState.isAutoSendEnabled = false
                deactivateService()
                return
            }

            val activeRoot = rootNode ?: run {
                if (isStatusFlowActive) {
                    Log.d(TAG, "[StatusSchedDbg] root node unavailable; waiting next event")
                }
                return
            }

            if (
                isStatusFlowActive &&
                StatusAutoScheduledState.sendClicksCompleted > 0 &&
                hasStatusSentConfirmation(event, activeRoot)
            ) {
                StatusAutoScheduledState.markSendConfirmed()
                CampaignState.isSendActionSuccessful = true
                clearPendingSecondStatusSendClick()
                Log.d(TAG, "[StatusSchedDbg] status confirmation detected. marking success.")
                StatusAutoScheduledState.reset()
                CampaignState.isAutoSendEnabled = false
                deactivateService()
                return
            }

            if (handleShareWithDialog(activeRoot)) {
                if (isStatusFlowActive) {
                    Log.d(TAG, "[StatusSchedDbg] share-with dialog handled")
                }
                return
            }

            if (hasNotOnWhatsAppPopup(event, activeRoot)) {
                CampaignState.sendFailureReason = CampaignState.FAILURE_NOT_ON_WHATSAPP
                CampaignState.isSendActionSuccessful = false
                dismissNotOnWhatsAppPopup(activeRoot)
                Log.w(TAG, "Detected 'number isn't on WhatsApp' popup. Marking current contact as failed.")
                return
            }

            if (isStatusFlowActive) {
                if (StatusAutoScheduledState.isExpired()) {
                    Log.w(TAG, "Status flow expired. Resetting state.")
                    clearPendingSecondStatusSendClick()
                    StatusAutoScheduledState.reset()
                    CampaignState.isAutoSendEnabled = false
                    deactivateService()
                    return
                }

                if (!StatusAutoScheduledState.hasClickedMyStatus) {
                    val clickedMyStatus = clickMyStatusEntry(activeRoot)
                    if (clickedMyStatus) {
                        StatusAutoScheduledState.markMyStatusClicked()
                        Log.d(TAG, "Status flow: My status selected, waiting for send arrow")
                        return
                    } else {
                        Log.d(TAG, "Status flow: My status item not found, trying direct send fallback")
                    }
                }
            }

            Log.d(TAG, "Searching for SEND button...")
            val sendButtonNode = findSendButtonNode(activeRoot)

            if (sendButtonNode != null) {
                if (isStatusFlowActive) {
                    StatusAutoScheduledState.clearSendButtonMisses()
                }

                if (isStatusFlowActive && !StatusAutoScheduledState.canAttemptSendClickNow()) {
                    if (StatusAutoScheduledState.maxSendAttemptsReached()) {
                        if (StatusAutoScheduledState.isWithinConfirmationGrace()) {
                            Log.d(TAG, "[StatusSchedDbg] max attempts consumed; waiting final confirmation window")
                        } else {
                            CampaignState.isSendActionSuccessful = false
                            Log.e(TAG, "[StatusSchedDbg] max attempts reached without confirmation. marking failed.")
                            clearPendingSecondStatusSendClick()
                            StatusAutoScheduledState.reset()
                            CampaignState.isAutoSendEnabled = false
                            deactivateService()
                        }
                    } else {
                        Log.d(
                            TAG,
                            "[StatusSchedDbg] retry cooldown active attempts=${StatusAutoScheduledState.sendClicksCompleted} " +
                                "retriesRemaining=${StatusAutoScheduledState.retriesRemaining()}"
                        )
                    }
                    sendButtonNode.recycle()
                    return
                }

                Log.d(TAG, "Clicking send button: ${sendButtonNode.viewIdResourceName} / ${sendButtonNode.contentDescription}")
                val result = sendButtonNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                if (result) {
                    Log.d(TAG, "Send click action returned true")
                    if (isStatusFlowActive) {
                        StatusAutoScheduledState.markSendClickDone()
                        if (StatusAutoScheduledState.maxSendAttemptsReached()) {
                            CampaignState.isSendActionSuccessful = true
                            Log.d(
                                TAG,
                                "[StatusSchedDbg] second send click done after gap. marking success."
                            )
                            clearPendingSecondStatusSendClick()
                            StatusAutoScheduledState.reset()
                            CampaignState.isAutoSendEnabled = false
                            deactivateService()
                        } else {
                            CampaignState.isSendActionSuccessful = null
                            Log.d(
                                TAG,
                                "[StatusSchedDbg] first send click done. waiting 2s before second click."
                            )
                            scheduleSecondStatusSendClick()
                        }
                    } else {
                        CampaignState.isSendActionSuccessful = true
                    }
                } else {
                    Log.e(TAG, "Send click action returned false")
                    CampaignState.isSendActionSuccessful = false
                    clearPendingSecondStatusSendClick()
                    Log.e(
                        TAG,
                        "[StatusSchedDbg] send click failed statusFlow=$isStatusFlowActive class=${event.className}"
                    )
                }
                sendButtonNode.recycle()
            } else {
                Log.w(TAG, "Send button not found in ${activeRoot.childCount} children")
                if (isStatusFlowActive) {
                    val misses = StatusAutoScheduledState.markSendButtonMiss()
                    Log.w(
                        TAG,
                        "[StatusSchedDbg] send button not found while status flow active class=${event.className} " +
                            "childCount=${activeRoot.childCount} misses=$misses recoveriesLeft=${StatusAutoScheduledState.uiRecoveriesRemaining()}"
                    )

                    if (attemptStatusFlowRecovery(event, activeRoot)) {
                        return
                    }

                    if (StatusAutoScheduledState.shouldFailFastForMissingSendButton()) {
                        CampaignState.isSendActionSuccessful = false
                        clearPendingSecondStatusSendClick()
                        Log.e(
                            TAG,
                            "[StatusSchedDbg] send button unavailable after recovery attempts. marking failed."
                        )
                        StatusAutoScheduledState.reset()
                        CampaignState.isAutoSendEnabled = false
                        deactivateService()
                    }
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error in handleAutoSend: ${e.message}", e)
        } finally {
            rootNode?.recycle()
        }
    }

    private fun clearPendingSecondStatusSendClick() {
        statusSecondClickRunnable?.let { handler.removeCallbacks(it) }
        statusSecondClickRunnable = null
    }

    private fun scheduleSecondStatusSendClick() {
        if (!StatusAutoScheduledState.isStatusFlowActive) return
        clearPendingSecondStatusSendClick()

        val delayMs = StatusAutoScheduledState.sendClickCooldownMs()
        statusSecondClickRunnable = Runnable {
            statusSecondClickRunnable = null
            performDelayedSecondStatusSendClick()
        }
        handler.postDelayed(statusSecondClickRunnable!!, delayMs)
        Log.d(TAG, "[StatusSchedDbg] scheduled second send click in ${delayMs}ms")
    }

    private fun performDelayedSecondStatusSendClick() {
        if (!StatusAutoScheduledState.isStatusFlowActive || !CampaignState.isAutoSendEnabled) {
            return
        }
        if (StatusAutoScheduledState.maxSendAttemptsReached()) {
            return
        }
        if (!StatusAutoScheduledState.canAttemptSendClickNow()) {
            scheduleSecondStatusSendClick()
            return
        }

        val rootNode = rootInActiveWindow ?: run {
            Log.w(TAG, "[StatusSchedDbg] delayed second click skipped: root not available")
            return
        }

        try {
            val sendButtonNode = findSendButtonNode(rootNode)
            if (sendButtonNode == null) {
                Log.w(TAG, "[StatusSchedDbg] delayed second click skipped: send button missing")
                return
            }

            val clickResult = sendButtonNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            sendButtonNode.recycle()

            if (!clickResult) {
                CampaignState.isSendActionSuccessful = false
                Log.e(TAG, "[StatusSchedDbg] delayed second click action returned false")
                return
            }

            StatusAutoScheduledState.markSendClickDone()
            if (StatusAutoScheduledState.maxSendAttemptsReached()) {
                CampaignState.isSendActionSuccessful = true
                Log.d(TAG, "[StatusSchedDbg] delayed second click completed")
                clearPendingSecondStatusSendClick()
                StatusAutoScheduledState.reset()
                CampaignState.isAutoSendEnabled = false
                deactivateService()
            }
        } catch (e: Exception) {
            Log.e(TAG, "[StatusSchedDbg] delayed second click failed: ${e.message}", e)
        } finally {
            rootNode.recycle()
        }
    }

    private fun attemptStatusFlowRecovery(
        event: AccessibilityEvent,
        rootNode: AccessibilityNodeInfo
    ): Boolean {
        if (!StatusAutoScheduledState.canAttemptUiRecovery()) {
            return false
        }

        val packageName = event.packageName?.toString().orEmpty()
        if (packageName.isBlank()) return false

        val className = event.className?.toString().orEmpty()
        val looksLikeStatusList = className.contains("HomeActivity", ignoreCase = true) ||
            className.contains("MyStatusesActivity", ignoreCase = true) ||
            className.contains("ListView", ignoreCase = true)

        if (!looksLikeStatusList && StatusAutoScheduledState.hasClickedMyStatus) {
            return false
        }

        StatusAutoScheduledState.markUiRecoveryAttempt()
        clearPendingSecondStatusSendClick()

        if (clickMyStatusEntry(rootNode)) {
            StatusAutoScheduledState.markMyStatusClicked()
            Log.w(TAG, "[StatusSchedDbg] recovery clicked My Status entry class=$className")
            return true
        }

        if (restartPreparedStatusShare(packageName)) {
            Log.w(TAG, "[StatusSchedDbg] recovery relaunched share intent package=$packageName class=$className")
            return true
        }

        val backPressed = performGlobalAction(GLOBAL_ACTION_BACK)
        Log.w(TAG, "[StatusSchedDbg] recovery fallback backPressed=$backPressed class=$className")
        return backPressed
    }

    private fun restartPreparedStatusShare(packageName: String): Boolean {
        val uriString = StatusAutoScheduledState.preparedImageUri
        if (uriString.isBlank()) {
            Log.w(TAG, "[StatusSchedDbg] recovery skipped: prepared uri missing")
            return false
        }

        val shareUri = runCatching { Uri.parse(uriString) }.getOrNull()
        if (shareUri == null) {
            Log.e(TAG, "[StatusSchedDbg] recovery failed: invalid uri=$uriString")
            return false
        }

        val mime = resolveMimeTypeForRecovery(
            StatusAutoScheduledState.preparedImagePath,
            uriString
        )

        return try {
            grantUriPermission(packageName, shareUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = mime
                setPackage(packageName)
                putExtra(Intent.EXTRA_STREAM, shareUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                clipData = ClipData.newRawUri("status_media", shareUri)
            }
            startActivity(shareIntent)
            true
        } catch (e: Exception) {
            Log.e(TAG, "[StatusSchedDbg] recovery share relaunch failed: ${e.message}", e)
            false
        }
    }

    private fun resolveMimeTypeForRecovery(path: String, uriString: String): String {
        val source = if (path.isNotBlank()) path else uriString
        val extension = source.substringAfterLast('.', "").lowercase(Locale.getDefault())
        return when (extension) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "webp" -> "image/webp"
            "gif" -> "image/gif"
            "mp4" -> "video/mp4"
            "3gp", "3gpp" -> "video/3gpp"
            "webm" -> "video/webm"
            else -> "image/*"
        }
    }

    private fun findSendButtonNode(rootNode: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val sendButtonIds = listOf(
            "com.whatsapp:id/send",
            "com.whatsapp.w4b:id/send",
            "com.whatsapp:id/send_btn",
            "com.whatsapp.w4b:id/send_btn"
        )

        for (id in sendButtonIds) {
            val nodes = rootNode.findAccessibilityNodeInfosByViewId(id) ?: continue
            var candidate: AccessibilityNodeInfo? = null
            for (node in nodes) {
                if (node.isVisibleToUser && (node.isClickable || node.isCheckable)) {
                    candidate = node
                    break
                }
            }

            for (node in nodes) {
                if (node !== candidate) node.recycle()
            }

            if (candidate != null) {
                if (!candidate.isEnabled) {
                    Log.d(TAG, "Send button found (ID: $id) but disabled. waiting...")
                    candidate.recycle()
                    continue
                }
                Log.d(TAG, "Send button found by ID: $id")
                return candidate
            }
        }

        val sendDescriptions = listOf("Send", "send", "SEND", "Bhejein", "Envoyer", "Enviar", "Submit")
        val clickableButtons = findClickableButtons(rootNode)
        var matchedButton: AccessibilityNodeInfo? = null
        for (button in clickableButtons) {
            val desc = button.contentDescription?.toString()
            val text = button.text?.toString()
            val isMatch = sendDescriptions.any { target ->
                (desc != null && desc.contains(target, ignoreCase = true)) ||
                    (text != null && text.contains(target, ignoreCase = true))
            }

            if (!isMatch) continue

            if (!button.isEnabled) {
                Log.d(TAG, "Send button found (recursive) but disabled. waiting...")
                continue
            }

            matchedButton = button
            Log.d(TAG, "Send button found by recursive search")
            break
        }

        for (button in clickableButtons) {
            if (button !== matchedButton) button.recycle()
        }

        return matchedButton
    }

    private fun hasStatusSentConfirmation(
        event: AccessibilityEvent,
        rootNode: AccessibilityNodeInfo?
    ): Boolean {
        val exactTexts = listOf(
            "Status sent",
            "status sent",
            "Status update sent",
            "status update sent",
            "Sent to status",
            "sent to status",
            "Added to status",
            "added to status",
            "Added to my status",
            "added to my status"
        )

        fun matches(text: String?): Boolean {
            if (text.isNullOrBlank()) return false
            val normalized = text.trim().lowercase(Locale.getDefault())
            return exactTexts.any { normalized.contains(it.lowercase(Locale.getDefault())) }
        }

        if (matches(event.contentDescription?.toString())) return true
        event.text?.forEach { value ->
            if (matches(value?.toString())) return true
        }

        if (rootNode != null) {
            for (probe in exactTexts) {
                val nodes = rootNode.findAccessibilityNodeInfosByText(probe)
                if (nodes != null) {
                    try {
                        if (nodes.any { it.isVisibleToUser }) {
                            return true
                        }
                    } finally {
                        nodes.forEach { it.recycle() }
                    }
                }
            }
        }

        return false
    }

    private fun hasNotOnWhatsAppPopup(
        event: AccessibilityEvent,
        rootNode: AccessibilityNodeInfo?
    ): Boolean {
        val markers = listOf(
            "isn't on whatsapp",
            "is not on whatsapp",
            "not on whatsapp",
            "phone number shared via url is invalid",
            "invalid phone number"
        )

        fun matches(text: String?): Boolean {
            if (text.isNullOrBlank()) return false
            val normalized = text.trim().lowercase(Locale.getDefault())
            return markers.any { normalized.contains(it) }
        }

        if (matches(event.contentDescription?.toString())) return true
        event.text?.forEach { value ->
            if (matches(value?.toString())) return true
        }

        if (rootNode != null) {
            val probes = listOf(
                "isn't on WhatsApp",
                "is not on WhatsApp",
                "not on WhatsApp",
                "phone number shared via url is invalid",
                "invalid phone number"
            )

            for (probe in probes) {
                val nodes = rootNode.findAccessibilityNodeInfosByText(probe)
                if (nodes != null) {
                    try {
                        if (nodes.any { it.isVisibleToUser }) {
                            return true
                        }
                    } finally {
                        nodes.forEach { it.recycle() }
                    }
                }
            }
        }

        return false
    }

    private fun dismissNotOnWhatsAppPopup(rootNode: AccessibilityNodeInfo) {
        val dismissTexts = listOf("Cancel", "cancel", "CANCEL", "OK", "Ok", "ok")
        for (text in dismissTexts) {
            val nodes = rootNode.findAccessibilityNodeInfosByText(text) ?: continue
            try {
                for (node in nodes) {
                    if (!node.isVisibleToUser) continue
                    if (clickNodeOrParent(node)) {
                        Log.d(TAG, "Dismissed not-on-WhatsApp popup using button text: $text")
                        return
                    }
                }
            } finally {
                nodes.forEach { it.recycle() }
            }
        }
    }

    private fun clickMyStatusEntry(rootNode: AccessibilityNodeInfo): Boolean {
        val targets = listOf(
            "My status",
            "My Status",
            "MY STATUS",
            "Add to status",
            "Status updates",
            "Status update"
        )
        for (target in targets) {
            val nodes = rootNode.findAccessibilityNodeInfosByText(target) ?: continue
            try {
                for (node in nodes) {
                    if (!node.isVisibleToUser) continue
                    if (clickNodeOrParent(node)) {
                        Log.d(TAG, "Clicked status target: $target")
                        return true
                    }
                }
            } finally {
                nodes.forEach { it.recycle() }
            }
        }

        // Locale fallback: try generic status labels
        val genericTargets = listOf("status", "Status", "STATUS")
        for (target in genericTargets) {
            val genericStatusNodes = rootNode.findAccessibilityNodeInfosByText(target) ?: continue
            try {
                for (node in genericStatusNodes) {
                    if (!node.isVisibleToUser) continue
                    if (clickNodeOrParent(node)) {
                        Log.d(TAG, "Clicked status target via generic match: $target")
                        return true
                    }
                }
            } finally {
                genericStatusNodes.forEach { it.recycle() }
            }
        }

        return false
    }

    private fun clickNodeOrParent(node: AccessibilityNodeInfo): Boolean {
        if (node.isClickable) {
            return node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        }

        var parent = node.parent
        var depth = 0
        while (parent != null && depth < 5) {
            if (parent.isClickable) {
                val result = parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                parent.recycle()
                return result
            }
            val next = parent.parent
            parent.recycle()
            parent = next
            depth++
        }
        return false
    }
    
    /**
     * Recursive function to find all clickable buttons (Copied from DocumentSendService logic)
     */
    private fun findClickableButtons(node: AccessibilityNodeInfo?): List<AccessibilityNodeInfo> {
        val buttons = mutableListOf<AccessibilityNodeInfo>()
        if (node == null) return buttons
        
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
    
    /**
     * Handle "Share with" dialog that appears when sharing video/media
     * Clicks OK button to proceed with sharing
     */
    private fun handleShareWithDialog(rootNode: AccessibilityNodeInfo): Boolean {
        try {
            // Check for "Share with" text to confirm dialog is present
            val shareWithTexts = listOf("Share with", "share with", "SHARE WITH")
            var isShareDialog = false
            
            for (text in shareWithTexts) {
                val shareNodes = rootNode.findAccessibilityNodeInfosByText(text)
                if (shareNodes != null && shareNodes.isNotEmpty()) {
                    isShareDialog = true
                    Log.d(TAG, "ðŸ“¤ Share with dialog detected")
                    // Recycle these nodes as we only needed check existence
                    for (node in shareNodes) node.recycle()
                    break
                }
            }
            
            if (!isShareDialog) return false
            
            // Try to find and click OK button by various methods
            
            // Method 1: Find by button IDs (WhatsApp and WhatsApp Business)
            val okButtonIds = listOf(
                "com.whatsapp:id/ok",
                "com.whatsapp.w4b:id/ok",
                "com.whatsapp:id/positive_button",
                "com.whatsapp.w4b:id/positive_button",
                "android:id/button1",
                "android:id/button_positive"
            )
            
            for (id in okButtonIds) {
                val nodes = rootNode.findAccessibilityNodeInfosByViewId(id)
                if (nodes != null) {
                    for (node in nodes) {
                        if (node.isVisibleToUser && node.isClickable) {
                            Log.d(TAG, "âœ… OK button found by ID: $id, clicking...")
                            node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                            node.recycle()
                            // Recycle remaining nodes in this list
                            for (j in nodes.indexOf(node) + 1 until nodes.size) {
                                nodes[j].recycle()
                            }
                            return true
                        }
                        node.recycle()
                    }
                }
            }
            
            // Method 2: Find by text "OK", "Ok", "ok", "SEND", "Send"
            val okTexts = listOf("OK", "Ok", "ok", "SEND", "Send", "send", "DONE", "Done")
            for (text in okTexts) {
                val okNodes = rootNode.findAccessibilityNodeInfosByText(text)
                if (okNodes != null) {
                    for (node in okNodes) {
                        if (node.isVisibleToUser && node.isClickable) {
                            Log.d(TAG, "âœ… OK button found by text: $text, clicking...")
                            node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                            node.recycle()
                            // Recycle remaining
                            for (j in okNodes.indexOf(node) + 1 until okNodes.size) {
                                okNodes[j].recycle()
                            }
                            return true
                        }
                        // Try clicking parent if node itself is not clickable
                        val parent = node.parent
                        if (parent != null && parent.isClickable) {
                            Log.d(TAG, "âœ… OK button parent found, clicking...")
                            parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                            parent.recycle()
                            node.recycle()
                            // Recycle remaining
                             for (j in okNodes.indexOf(node) + 1 until okNodes.size) {
                                okNodes[j].recycle()
                            }
                            return true
                        }
                        // Recycle parent if logical check failed
                        parent?.recycle()
                        node.recycle()
                    }
                }
            }
            
            // Method 3: Search recursively for clickable button with OK text
            // Note: recursive function handles its own recycling conceptually? 
            // The original findButtonByText didn't recycle unrelated nodes. 
            // Given the complexity, let's stick to the flattening methods above for now 
            // or re-implement finding safely. For reliability, let's skip the deep recursion
            // if we can, or ensure we don't leak.
            
            Log.d(TAG, "âš ï¸ Share dialog detected but OK button not found")
            return false
            
        } catch (e: Exception) {
            Log.e(TAG, "âŒ Error handling share dialog: ${e.message}", e)
            return false
        }
    }

    private fun extractTextsFromNode(node: AccessibilityNodeInfo?) {
        if (node == null) return

        for (i in 0 until node.childCount) {
            val childNode = node.getChild(i)
            if (childNode != null) {
                val text = childNode.text?.toString()
                if (text != null && text.isNotBlank()) {
                    // No need to check processedMessages - let HashSets handle duplicates
                    if (!extractedTexts.contains(text)) {
                        extractedTexts.add(text)
                        Log.d(TAG, "ðŸ“ Extracted text: $text")
                    }

                    // Always try to extract phone numbers (HashSet will handle duplicates)
                    val numbers = findNumbersInText(text)
                    if (numbers.isNotEmpty()) {
                        val newNumbers = numbers.filter { !extractedNumbers.contains(it) }
                        if (newNumbers.isNotEmpty()) {
                            Log.d(TAG, "ðŸ“ž Found new numbers: $newNumbers")
                            extractedNumbers.addAll(newNumbers)
                        }
                    }
                }

                // Recursive call to process child nodes
                extractTextsFromNode(childNode)
                childNode.recycle()
            }
        }
    }

    private fun findNumbersInText(text: String): ArrayList<String> {
        val numbers = ArrayList<String>()
        // Pattern to match phone numbers with country code (MUST start with +)
        val pattern = Pattern.compile("(?!\\+0)\\+\\d+(?:[-\\s(]*\\d+[\\s)-]*)+")
        val lines = text.split("\\n".toRegex()).dropLastWhile { it.isEmpty() }

        for (line in lines) {
            // Only process lines that contain +
            if (!line.contains("+")) continue

            val matcher = pattern.matcher(line)
            while (matcher.find()) {
                var phoneNumber = matcher.group()
                // Remove spaces and symbols from the phone number
                phoneNumber = phoneNumber.replace("[\\s()-]".toRegex(), "")

                // Double check: ONLY add if starts with +
                if (phoneNumber.startsWith("+") && phoneNumber.length >= 8) {
                    numbers.add(phoneNumber)
                    Log.d(TAG, "âœ… Extracted number: $phoneNumber")
                }
            }
        }
        return numbers
    }

    private fun broadcastExtractedNumbers() {
        val numbersText = extractedNumbers.joinToString("\n")
        Log.d(TAG, "ðŸ” broadcastExtractedNumbers called, extracted numbers: ${extractedNumbers.size}")
        Log.d(TAG, "ï¿½ Numbersa text: '$numbersText'")
        if (numbersText.isNotBlank()) {
            Log.d(TAG, "ðŸ“¤ Broadcasting ${extractedNumbers.size} numbers")

            // Save to SharedPreferences as backup
            saveExtractedNumbers(this, extractedNumbers)

            // Also try broadcast (keep both methods)
            saveText(numbersText)
            broadcastText(numbersText)
        } else {
            Log.d(TAG, "âš ï¸ No numbers to broadcast")
        }
    }

    private fun saveText(text: String) {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedTexts = prefs.getStringSet(PREFS_KEY, mutableSetOf())?.toMutableSet() ?: mutableSetOf()
        savedTexts.add(text)
        prefs.edit().putStringSet(PREFS_KEY, savedTexts).apply()
    }

    private fun broadcastText(text: String) {
        val intent = Intent(ACTION_TEXT_CAPTURED).putExtra(EXTRA_CAPTURED_TEXT, text)
        Log.d(TAG, "ðŸ“¡ Sending broadcast with action: $ACTION_TEXT_CAPTURED")
        Log.d(TAG, "ðŸ“¡ Broadcast text length: ${text.length} characters")
        sendBroadcast(intent)
        Log.d(TAG, "ðŸ“¡ Broadcast sent successfully")
    }

    override fun onInterrupt() {
        Log.e(TAG, "Accessibility service interrupt ho gayi.")
        clearPendingSecondStatusSendClick()
        StatusAutoScheduledState.reset()
        deactivateService()
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        // Service connected but INACTIVE by default
        isServiceActive = false
        isContactExtractionEnabled = false
        
        // Initialize DocumentSendService
        val documentSendService = com.message.bulksend.autorespond.documentreply.DocumentSendService.getInstance()
        documentSendService.loadQueueFromPrefs(this)
        
        Log.i(TAG, "âœ… Accessibility service connected (Both features INACTIVE by default)")
        Log.i(TAG, "Auto-send enabled status: ${CampaignState.isAutoSendEnabled}")
        Log.i(TAG, "Document send service initialized")
    }

    override fun onDestroy() {
        super.onDestroy()
        extractionRunnable?.let { handler.removeCallbacks(it) }
        clearPendingSecondStatusSendClick()
        extractionExecutor.shutdown()
        StatusAutoScheduledState.reset()
        deactivateService()
        disableContactExtraction()
        Log.i(TAG, "Service destroyed, all features deactivated, executor shutdown")
    }
}

