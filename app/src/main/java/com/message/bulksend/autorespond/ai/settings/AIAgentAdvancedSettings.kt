package com.message.bulksend.autorespond.ai.settings

import android.content.Context
import android.content.SharedPreferences

/**
 * Advanced Settings Manager for AI Agent
 * Manages all advanced features and configurations
 */
class AIAgentAdvancedSettings(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("ai_agent_advanced_settings", Context.MODE_PRIVATE)

    // Intent Detection
    var enableIntentDetection: Boolean
        get() = prefs.getBoolean("enable_intent_detection", true)
        set(value) = prefs.edit().putBoolean("enable_intent_detection", value).apply()

    var autoSaveIntentHistory: Boolean
        get() = prefs.getBoolean("auto_save_intent_history", true)
        set(value) = prefs.edit().putBoolean("auto_save_intent_history", value).apply()

    // Auto Sheet Creation
    var autoCreateHistorySheets: Boolean
        get() = prefs.getBoolean("auto_create_history_sheets", true)
        set(value) = prefs.edit().putBoolean("auto_create_history_sheets", value).apply()

    var autoCreateProfileSheet: Boolean
        get() = prefs.getBoolean("auto_create_profile_sheet", true)
        set(value) = prefs.edit().putBoolean("auto_create_profile_sheet", value).apply()

    // Sentiment Analysis (Future)
    var enableSentimentAnalysis: Boolean
        get() = prefs.getBoolean("enable_sentiment_analysis", false)
        set(value) = prefs.edit().putBoolean("enable_sentiment_analysis", value).apply()

    // Follow-up System (Future)
    var enableAutoFollowUp: Boolean
        get() = prefs.getBoolean("enable_auto_follow_up", false)
        set(value) = prefs.edit().putBoolean("enable_auto_follow_up", value).apply()

    var followUpDelayHours: Int
        get() = prefs.getInt("follow_up_delay_hours", 24)
        set(value) = prefs.edit().putInt("follow_up_delay_hours", value).apply()

    // Analytics (Future)
    var enableAnalytics: Boolean
        get() = prefs.getBoolean("enable_analytics", true)
        set(value) = prefs.edit().putBoolean("enable_analytics", value).apply()

    // Human Handoff (Future)
    var enableHumanHandoff: Boolean
        get() = prefs.getBoolean("enable_human_handoff", false)
        set(value) = prefs.edit().putBoolean("enable_human_handoff", value).apply()

    var handoffKeywords: String
        get() = prefs.getString("handoff_keywords", "talk to human,speak to agent,human support") ?: "talk to human,speak to agent,human support"
        set(value) = prefs.edit().putString("handoff_keywords", value).apply()

    // E-commerce Mode
    var enableEcommerceMode: Boolean
        get() = prefs.getBoolean("enable_ecommerce_mode", false)
        set(value) = prefs.edit().putBoolean("enable_ecommerce_mode", value).apply()

    var autoAskAddress: Boolean
        get() = prefs.getBoolean("auto_ask_address", true)
        set(value) = prefs.edit().putBoolean("auto_ask_address", value).apply()

    var autoCreateSalesSheets: Boolean
        get() = prefs.getBoolean("auto_create_sales_sheets", true)
        set(value) = prefs.edit().putBoolean("auto_create_sales_sheets", value).apply()
}
