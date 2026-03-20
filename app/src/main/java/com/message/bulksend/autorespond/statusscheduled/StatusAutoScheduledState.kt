package com.message.bulksend.autorespond.statusscheduled

/**
 * Shared runtime state between StatusAutoScheduledActivity and WhatsAppAutoSendService.
 * Used only for a short-lived status posting flow.
 */
object StatusAutoScheduledState {

    private const val MAX_SEND_RETRIES = 1
    private const val MAX_SEND_ATTEMPTS = 1 + MAX_SEND_RETRIES
    private const val SEND_CLICK_COOLDOWN_MS = 2000L
    private const val CONFIRMATION_GRACE_MS = 2500L
    private const val SEND_BUTTON_MISS_THRESHOLD = 6
    private const val MAX_UI_RECOVERY_ATTEMPTS = 2
    private const val UI_RECOVERY_COOLDOWN_MS = 1200L

    @Volatile
    var isStatusFlowActive: Boolean = false
        private set

    @Volatile
    var hasClickedMyStatus: Boolean = false
        private set

    @Volatile
    var preparedImagePath: String = ""
        private set

    @Volatile
    var preparedImageUri: String = ""
        private set

    @Volatile
    var sharedHyperlink: String = ""
        private set

    @Volatile
    var startedAt: Long = 0L
        private set

    @Volatile
    var sendClicksCompleted: Int = 0
        private set

    @Volatile
    var lastSendClickAt: Long = 0L
        private set

    @Volatile
    var isSendConfirmed: Boolean = false
        private set

    @Volatile
    var sendButtonMisses: Int = 0
        private set

    @Volatile
    var uiRecoveryAttempts: Int = 0
        private set

    @Volatile
    private var lastUiRecoveryAt: Long = 0L

    fun activate(imagePath: String, imageUri: String, hyperlink: String) {
        isStatusFlowActive = true
        hasClickedMyStatus = false
        preparedImagePath = imagePath
        preparedImageUri = imageUri
        sharedHyperlink = hyperlink
        startedAt = System.currentTimeMillis()
        sendClicksCompleted = 0
        lastSendClickAt = 0L
        isSendConfirmed = false
        sendButtonMisses = 0
        uiRecoveryAttempts = 0
        lastUiRecoveryAt = 0L
    }

    fun markMyStatusClicked() {
        hasClickedMyStatus = true
    }

    fun isExpired(timeoutMs: Long = 2 * 60 * 1000L): Boolean {
        if (!isStatusFlowActive || startedAt <= 0L) return false
        return (System.currentTimeMillis() - startedAt) > timeoutMs
    }

    fun markSendClickDone() {
        sendClicksCompleted += 1
        lastSendClickAt = System.currentTimeMillis()
        sendButtonMisses = 0
    }

    fun canAttemptSendClickNow(now: Long = System.currentTimeMillis()): Boolean {
        if (sendClicksCompleted >= MAX_SEND_ATTEMPTS) return false
        return lastSendClickAt <= 0L || (now - lastSendClickAt) >= SEND_CLICK_COOLDOWN_MS
    }

    fun sendClickCooldownMs(): Long = SEND_CLICK_COOLDOWN_MS

    fun markSendConfirmed() {
        isSendConfirmed = true
        sendButtonMisses = 0
    }

    fun retriesUsed(): Int {
        return (sendClicksCompleted - 1).coerceAtLeast(0)
    }

    fun retriesRemaining(): Int {
        return (MAX_SEND_RETRIES - retriesUsed()).coerceAtLeast(0)
    }

    fun maxSendAttemptsReached(): Boolean {
        return sendClicksCompleted >= MAX_SEND_ATTEMPTS
    }

    fun isWithinConfirmationGrace(now: Long = System.currentTimeMillis()): Boolean {
        if (lastSendClickAt <= 0L) return false
        return (now - lastSendClickAt) < CONFIRMATION_GRACE_MS
    }

    fun markSendButtonMiss(): Int {
        sendButtonMisses += 1
        return sendButtonMisses
    }

    fun clearSendButtonMisses() {
        sendButtonMisses = 0
    }

    fun canAttemptUiRecovery(now: Long = System.currentTimeMillis()): Boolean {
        if (sendButtonMisses < SEND_BUTTON_MISS_THRESHOLD) return false
        if (uiRecoveryAttempts >= MAX_UI_RECOVERY_ATTEMPTS) return false
        if (lastUiRecoveryAt <= 0L) return true
        return (now - lastUiRecoveryAt) >= UI_RECOVERY_COOLDOWN_MS
    }

    fun markUiRecoveryAttempt() {
        uiRecoveryAttempts += 1
        lastUiRecoveryAt = System.currentTimeMillis()
        sendButtonMisses = 0
        hasClickedMyStatus = false
        sendClicksCompleted = 0
        lastSendClickAt = 0L
        isSendConfirmed = false
    }

    fun isUiRecoveryExhausted(): Boolean {
        return uiRecoveryAttempts >= MAX_UI_RECOVERY_ATTEMPTS
    }

    fun shouldFailFastForMissingSendButton(): Boolean {
        return sendButtonMisses >= SEND_BUTTON_MISS_THRESHOLD && isUiRecoveryExhausted()
    }

    fun uiRecoveriesRemaining(): Int {
        return (MAX_UI_RECOVERY_ATTEMPTS - uiRecoveryAttempts).coerceAtLeast(0)
    }

    fun reset() {
        isStatusFlowActive = false
        hasClickedMyStatus = false
        preparedImagePath = ""
        preparedImageUri = ""
        sharedHyperlink = ""
        startedAt = 0L
        sendClicksCompleted = 0
        lastSendClickAt = 0L
        isSendConfirmed = false
        sendButtonMisses = 0
        uiRecoveryAttempts = 0
        lastUiRecoveryAt = 0L
    }
}
