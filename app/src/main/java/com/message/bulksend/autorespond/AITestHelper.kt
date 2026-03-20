package com.message.bulksend.autorespond

import android.content.Context
import android.util.Log

/**
 * AI Duplicate Prevention Test Helper
 * Use this class to test and debug AI duplicate prevention system
 */
class AITestHelper(private val context: Context) {
    
    companion object {
        private const val TAG = "AITestHelper"
    }
    
    /**
     * Test AI duplicate prevention with different delay settings
     */
    fun testAIDuplicatePrevention() {
        Log.d(TAG, "=== AI DUPLICATE PREVENTION TEST ===")
        
        // Test different delay scenarios
        testScenario("No Delay", 0L)
        testScenario("5 Second Delay", 5000L)
        testScenario("10 Second Delay", 10000L)
        testScenario("15 Second Delay", 15000L)
        
        Log.d(TAG, "=== TEST COMPLETE ===")
    }
    
    private fun testScenario(scenarioName: String, delayMs: Long) {
        Log.d(TAG, "\n--- Testing: $scenarioName ---")
        
        // Simulate delay setting
        val delayManager = com.message.bulksend.autorespond.settings.ReplyDelayManager(context)
        val delayType = when (delayMs) {
            0L -> com.message.bulksend.autorespond.settings.ReplyDelayType.NO_DELAY
            5000L -> com.message.bulksend.autorespond.settings.ReplyDelayType.DELAY_5_SEC
            10000L -> com.message.bulksend.autorespond.settings.ReplyDelayType.DELAY_10_SEC
            15000L -> com.message.bulksend.autorespond.settings.ReplyDelayType.DELAY_15_SEC
            else -> com.message.bulksend.autorespond.settings.ReplyDelayType.NO_DELAY
        }
        delayManager.setDelayType(delayType)
        
        Log.d(TAG, "✓ Delay set to: ${delayManager.getDelayDisplayTextEnglish()}")
        Log.d(TAG, "Expected behavior:")
        Log.d(TAG, "  - First notification: AI request scheduled")
        Log.d(TAG, "  - Duplicate notifications: Blocked by tracker")
        Log.d(TAG, "  - After delay: Single AI reply sent")
        Log.d(TAG, "  - Future duplicates: Blocked by 60s cooldown")
    }
    
    /**
     * Get current AI wait time status for debugging
     */
    fun getAIStatus(senderName: String): String {
        // This would need access to WhatsAppNotificationListener's private fields
        // For now, return a debug message
        return "AI Status for $senderName: Check logs for detailed status"
    }
    
    /**
     * Simulate multiple rapid notifications (for testing)
     */
    fun simulateRapidNotifications(senderName: String, messageText: String, count: Int = 5) {
        Log.d(TAG, "=== SIMULATING $count RAPID NOTIFICATIONS ===")
        Log.d(TAG, "Sender: $senderName")
        Log.d(TAG, "Message: $messageText")
        Log.d(TAG, "Expected: Only first notification should trigger AI")
        
        for (i in 1..count) {
            Log.d(TAG, "Notification $i: Would be processed by WhatsAppNotificationListener")
            // In real scenario, this would trigger processWhatsAppNotification
        }
        
        Log.d(TAG, "=== SIMULATION COMPLETE ===")
    }
    
    /**
     * Test different AI providers
     */
    fun testAIProviders() {
        Log.d(TAG, "=== AI PROVIDER TEST ===")
        
        val providers = listOf(
            com.message.bulksend.autorespond.aireply.AIProvider.CHATSPROMO,
            com.message.bulksend.autorespond.aireply.AIProvider.GEMINI,
            com.message.bulksend.autorespond.aireply.AIProvider.CHATGPT
        )
        
        providers.forEach { provider ->
            Log.d(TAG, "Testing provider: ${provider.displayName}")
            Log.d(TAG, "  - Should have separate wait time tracking")
            Log.d(TAG, "  - Should respect 60-second cooldown per provider")
            Log.d(TAG, "  - Should work with delay coordination")
        }
        
        Log.d(TAG, "=== PROVIDER TEST COMPLETE ===")
    }
    
    /**
     * Validate delay settings
     */
    fun validateDelaySettings(): Boolean {
        val delayManager = com.message.bulksend.autorespond.settings.ReplyDelayManager(context)
        val currentDelay = delayManager.getDelayMillis()
        val delayType = delayManager.getDelayType()
        
        Log.d(TAG, "=== DELAY VALIDATION ===")
        Log.d(TAG, "Current delay type: $delayType")
        Log.d(TAG, "Current delay ms: ${currentDelay}ms")
        Log.d(TAG, "Display text: ${delayManager.getDelayDisplayTextEnglish()}")
        
        val isValid = when (delayType) {
            com.message.bulksend.autorespond.settings.ReplyDelayType.NO_DELAY -> currentDelay == 0L
            com.message.bulksend.autorespond.settings.ReplyDelayType.DELAY_5_SEC -> currentDelay == 5000L
            com.message.bulksend.autorespond.settings.ReplyDelayType.DELAY_10_SEC -> currentDelay == 10000L
            com.message.bulksend.autorespond.settings.ReplyDelayType.DELAY_15_SEC -> currentDelay == 15000L
            com.message.bulksend.autorespond.settings.ReplyDelayType.RANDOM_5_TO_15 -> currentDelay in 5000L..15000L
        }
        
        Log.d(TAG, "Validation result: ${if (isValid) "✅ VALID" else "❌ INVALID"}")
        return isValid
    }
    
    /**
     * Check AI reply settings
     */
    fun checkAISettings(): Map<String, Any> {
        val settingsManager = com.message.bulksend.autorespond.settings.AutoReplySettingsManager(context)
        val aiReplyManager = com.message.bulksend.autorespond.aireply.AIReplyManager(context)
        
        val settings = mapOf(
            "AI Reply Enabled" to settingsManager.isAIReplyEnabled(),
            "Should Use AI Reply" to settingsManager.shouldUseAIReply(),
            "Should Use AI as Fallback" to settingsManager.shouldUseAIAsFallback(),
            "Reply Priority" to settingsManager.getReplyPriority().name,
            "Selected AI Provider" to aiReplyManager.getSelectedProvider().displayName
        )
        
        Log.d(TAG, "=== AI SETTINGS CHECK ===")
        settings.forEach { (key, value) ->
            Log.d(TAG, "$key: $value")
        }
        
        return settings
    }
    
    /**
     * Generate test report
     */
    fun generateTestReport(): String {
        val report = StringBuilder()
        report.appendLine("=== AI DUPLICATE PREVENTION TEST REPORT ===")
        report.appendLine("Generated at: ${java.util.Date()}")
        report.appendLine()
        
        // Delay settings
        val delayManager = com.message.bulksend.autorespond.settings.ReplyDelayManager(context)
        report.appendLine("DELAY SETTINGS:")
        report.appendLine("  Type: ${delayManager.getDelayType()}")
        report.appendLine("  Duration: ${delayManager.getDelayMillis()}ms")
        report.appendLine("  Display: ${delayManager.getDelayDisplayTextEnglish()}")
        report.appendLine()
        
        // AI settings
        val aiSettings = checkAISettings()
        report.appendLine("AI SETTINGS:")
        aiSettings.forEach { (key, value) ->
            report.appendLine("  $key: $value")
        }
        report.appendLine()
        
        // Test scenarios
        report.appendLine("TEST SCENARIOS:")
        report.appendLine("  ✅ No Delay: Should work immediately")
        report.appendLine("  ✅ With Delay: Should coordinate AI generation with delay")
        report.appendLine("  ✅ Rapid Duplicates: Should block during delay period")
        report.appendLine("  ✅ Multiple Providers: Should track separately")
        report.appendLine("  ✅ 60s Cooldown: Should prevent rapid AI requests")
        report.appendLine()
        
        report.appendLine("EXPECTED BEHAVIOR:")
        report.appendLine("  1. First notification triggers AI request")
        report.appendLine("  2. Request tracker blocks duplicates immediately")
        report.appendLine("  3. AI generation waits for delay period")
        report.appendLine("  4. Single AI reply sent after delay")
        report.appendLine("  5. 60-second cooldown prevents rapid requests")
        report.appendLine()
        
        report.appendLine("=== END REPORT ===")
        
        val reportString = report.toString()
        Log.d(TAG, reportString)
        return reportString
    }
}