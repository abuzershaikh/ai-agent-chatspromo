package com.message.bulksend.autorespond.settings

import android.content.Context
import android.content.SharedPreferences

enum class ReplyPriority {
    MENU_FIRST,         // Try menu reply first, then keyword, then spreadsheet, then AI if no match
    KEYWORD_FIRST,      // Try keyword first, then spreadsheet, then AI if no match
    SPREADSHEET_FIRST,  // Try spreadsheet first, then keyword, then AI if no match
    AI_ONLY,            // Always use AI, ignore keywords and spreadsheet
    KEYWORD_ONLY,       // Only use keywords, no AI or spreadsheet
    SPREADSHEET_ONLY,   // Only use spreadsheet, no AI or keywords
    MENU_ONLY           // Only use menu reply, no other methods
}

class AutoReplySettingsManager(context: Context) {
    
    private val prefs: SharedPreferences = context.getSharedPreferences(
        "auto_reply_settings",
        Context.MODE_PRIVATE
    )
    
    companion object {
        private const val KEY_KEYWORD_REPLY_ENABLED = "keyword_reply_enabled"
        private const val KEY_AI_REPLY_ENABLED = "ai_reply_enabled"
        private const val KEY_SPREADSHEET_REPLY_ENABLED = "spreadsheet_reply_enabled"
        private const val KEY_MENU_REPLY_ENABLED = "menu_reply_enabled"
        private const val KEY_DOCUMENT_REPLY_ENABLED = "document_reply_enabled"
        private const val KEY_REPLY_PRIORITY = "reply_priority"
        private const val KEY_WHATSAPP_ENABLED = "whatsapp_enabled"
        private const val KEY_WHATSAPP_BUSINESS_ENABLED = "whatsapp_business_enabled"
        private const val KEY_INSTAGRAM_ENABLED = "instagram_enabled"
    }
    
    /**
     * Check if keyword reply is enabled
     */
    fun isKeywordReplyEnabled(): Boolean {
        return prefs.getBoolean(KEY_KEYWORD_REPLY_ENABLED, true) // Default: enabled
    }
    
    /**
     * Enable/disable keyword reply
     */
    fun setKeywordReplyEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_KEYWORD_REPLY_ENABLED, enabled).apply()
    }
    
    /**
     * Check if AI reply is enabled
     */
    fun isAIReplyEnabled(): Boolean {
        return prefs.getBoolean(KEY_AI_REPLY_ENABLED, false) // Default: disabled
    }
    
    /**
     * Enable/disable AI reply
     */
    fun setAIReplyEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AI_REPLY_ENABLED, enabled).apply()
    }
    
    /**
     * Check if spreadsheet reply is enabled
     */
    fun isSpreadsheetReplyEnabled(): Boolean {
        return prefs.getBoolean(KEY_SPREADSHEET_REPLY_ENABLED, false) // Default: disabled
    }
    
    /**
     * Enable/disable spreadsheet reply
     */
    fun setSpreadsheetReplyEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SPREADSHEET_REPLY_ENABLED, enabled).apply()
    }
    
    /**
     * Check if menu reply is enabled
     */
    fun isMenuReplyEnabled(): Boolean {
        return prefs.getBoolean(KEY_MENU_REPLY_ENABLED, false) // Default: disabled
    }
    
    /**
     * Enable/disable menu reply
     */
    fun setMenuReplyEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_MENU_REPLY_ENABLED, enabled).apply()
    }
    
    /**
     * Check if document reply is enabled
     */
    fun isDocumentReplyEnabled(): Boolean {
        return prefs.getBoolean(KEY_DOCUMENT_REPLY_ENABLED, true) // Default: enabled
    }
    
    /**
     * Enable/disable document reply
     */
    fun setDocumentReplyEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_DOCUMENT_REPLY_ENABLED, enabled).apply()
    }
    
    /**
     * Get reply priority
     */
    fun getReplyPriority(): ReplyPriority {
        val priorityString = prefs.getString(KEY_REPLY_PRIORITY, null)
        val normalizedPriorityString = if (priorityString.isNullOrBlank()) null else priorityString
        if (normalizedPriorityString == null) {
            val defaultPriority = ReplyPriority.KEYWORD_FIRST
            setReplyPriority(defaultPriority)
            return defaultPriority
        }
        return try {
            ReplyPriority.valueOf(normalizedPriorityString)
        } catch (e: Exception) {
            val defaultPriority = ReplyPriority.KEYWORD_FIRST
            setReplyPriority(defaultPriority)
            defaultPriority
        }
    }
    
    /**
     * Set reply priority
     */
    fun setReplyPriority(priority: ReplyPriority) {
        prefs.edit().putString(KEY_REPLY_PRIORITY, priority.name).apply()
    }
    
    /**
     * Should use keyword reply based on settings
     */
    fun shouldUseKeywordReply(): Boolean {
        val priority = getReplyPriority()
        return isKeywordReplyEnabled() && 
               (priority == ReplyPriority.MENU_FIRST ||
                priority == ReplyPriority.KEYWORD_FIRST || 
                priority == ReplyPriority.KEYWORD_ONLY ||
                priority == ReplyPriority.SPREADSHEET_FIRST)
    }
    
    /**
     * Should use spreadsheet reply based on settings
     */
    fun shouldUseSpreadsheetReply(): Boolean {
        val priority = getReplyPriority()
        return isSpreadsheetReplyEnabled() && 
               (priority == ReplyPriority.MENU_FIRST ||
                priority == ReplyPriority.KEYWORD_FIRST || 
                priority == ReplyPriority.SPREADSHEET_FIRST ||
                priority == ReplyPriority.SPREADSHEET_ONLY)
    }
    
    /**
     * Should use document reply based on settings
     */
    fun shouldUseDocumentReply(): Boolean {
        val priority = getReplyPriority()
        return isDocumentReplyEnabled() && 
               (priority == ReplyPriority.MENU_FIRST ||
                priority == ReplyPriority.KEYWORD_FIRST || 
                priority == ReplyPriority.KEYWORD_ONLY ||
                priority == ReplyPriority.SPREADSHEET_FIRST)
    }
    
    /**
     * Should use menu reply based on settings
     */
    fun shouldUseMenuReply(): Boolean {
        val priority = getReplyPriority()
        return isMenuReplyEnabled() && 
               (priority == ReplyPriority.MENU_FIRST ||
                priority == ReplyPriority.MENU_ONLY)
    }
    
    /**
     * Should use AI reply based on settings
     */
    fun shouldUseAIReply(): Boolean {
        val priority = getReplyPriority()
        return isAIReplyEnabled() && 
               (priority == ReplyPriority.MENU_FIRST ||
                priority == ReplyPriority.KEYWORD_FIRST || 
                priority == ReplyPriority.SPREADSHEET_FIRST ||
                priority == ReplyPriority.AI_ONLY)
    }
    
    /**
     * Should use AI as fallback (when keyword/spreadsheet doesn't match)
     */
    fun shouldUseAIAsFallback(): Boolean {
        return isAIReplyEnabled() && 
               (getReplyPriority() == ReplyPriority.MENU_FIRST ||
                getReplyPriority() == ReplyPriority.KEYWORD_FIRST ||
                getReplyPriority() == ReplyPriority.SPREADSHEET_FIRST)
    }
    
    // ==================== WhatsApp Selection Settings ====================
    
    /**
     * Check if WhatsApp (personal) auto-reply is enabled
     */
    fun isWhatsAppEnabled(): Boolean {
        return prefs.getBoolean(KEY_WHATSAPP_ENABLED, true) // Default: enabled
    }
    
    /**
     * Enable/disable WhatsApp (personal) auto-reply
     */
    fun setWhatsAppEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_WHATSAPP_ENABLED, enabled).apply()
    }
    
    /**
     * Check if WhatsApp Business auto-reply is enabled
     */
    fun isWhatsAppBusinessEnabled(): Boolean {
        return prefs.getBoolean(KEY_WHATSAPP_BUSINESS_ENABLED, true) // Default: enabled
    }
    
    /**
     * Enable/disable WhatsApp Business auto-reply
     */
    fun setWhatsAppBusinessEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_WHATSAPP_BUSINESS_ENABLED, enabled).apply()
    }
    
    /**
     * Check if Instagram auto-reply is enabled
     */
    fun isInstagramEnabled(): Boolean {
        return prefs.getBoolean(KEY_INSTAGRAM_ENABLED, true) // Default: enabled
    }
    
    /**
     * Enable/disable Instagram auto-reply
     */
    fun setInstagramEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_INSTAGRAM_ENABLED, enabled).apply()
    }
    
    /**
     * Check if auto-reply should be sent to Instagram
     */
    fun shouldReplyToInstagram(): Boolean {
        return isInstagramEnabled()
    }
    
    /**
     * Check if auto-reply should be sent for a specific package
     * @param packageName The package name of the app (com.whatsapp, com.whatsapp.w4b, or com.instagram.android)
     */
    fun shouldReplyToPackage(packageName: String): Boolean {
        return when (packageName) {
            "com.whatsapp" -> isWhatsAppEnabled()
            "com.whatsapp.w4b" -> isWhatsAppBusinessEnabled()
            "com.instagram.android" -> isInstagramEnabled()
            else -> false
        }
    }
}
