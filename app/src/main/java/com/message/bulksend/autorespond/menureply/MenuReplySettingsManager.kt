package com.message.bulksend.autorespond.menureply

import android.content.Context
import android.content.SharedPreferences

/**
 * Default reply type when user enters invalid option
 */
enum class DefaultReplyType {
    MAIN_MENU,      // Send main menu
    SAME_MENU,      // Send same menu again
    CUSTOM_MESSAGE  // Send custom message
}

/**
 * Post-selection reply type (after user completes menu flow)
 */
enum class PostSelectionReplyType {
    NO_REPLY,       // Don't reply
    MAIN_MENU,      // Send main menu
    CUSTOM_MESSAGE  // Send custom message
}

/**
 * Manager for Menu Reply Settings
 */
class MenuReplySettingsManager(context: Context) {
    
    companion object {
        private const val PREFS_NAME = "menu_reply_settings"
        
        // Navigation Options
        private const val KEY_SHOW_MAIN_MENU = "show_main_menu"
        private const val KEY_SHOW_PREVIOUS_MENU = "show_previous_menu"
        
        // Default Reply (Invalid Option)
        private const val KEY_DEFAULT_REPLY_ENABLED = "default_reply_enabled"
        private const val KEY_DEFAULT_REPLY_TYPE = "default_reply_type"
        private const val KEY_CUSTOM_REPLY_MESSAGE = "custom_reply_message"
        
        // Post-Selection Reply (After Final Selection)
        private const val KEY_POST_SELECTION_ENABLED = "post_selection_enabled"
        private const val KEY_POST_SELECTION_TYPE = "post_selection_type"
        private const val KEY_POST_SELECTION_MESSAGE = "post_selection_message"
        
        // Display Options
        private const val KEY_SHOW_EMOJIS = "show_emojis"
        private const val KEY_BOLD_HEADER = "bold_header"
        private const val KEY_SHOW_SELECTION_HINT = "show_selection_hint"
        
        // Session Options
        private const val KEY_MENU_TIMEOUT_SECONDS = "menu_timeout_seconds"
        private const val MIN_MENU_TIMEOUT_SECONDS = 40
        private const val MAX_MENU_TIMEOUT_SECONDS = 300
    }
    
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    // Navigation Options
    fun isShowMainMenuEnabled(): Boolean = prefs.getBoolean(KEY_SHOW_MAIN_MENU, true)
    fun setShowMainMenuEnabled(enabled: Boolean) = prefs.edit().putBoolean(KEY_SHOW_MAIN_MENU, enabled).apply()
    
    fun isShowPreviousMenuEnabled(): Boolean = prefs.getBoolean(KEY_SHOW_PREVIOUS_MENU, true)
    fun setShowPreviousMenuEnabled(enabled: Boolean) = prefs.edit().putBoolean(KEY_SHOW_PREVIOUS_MENU, enabled).apply()
    
    // Default Reply (Invalid Option)
    fun isDefaultReplyEnabled(): Boolean = prefs.getBoolean(KEY_DEFAULT_REPLY_ENABLED, true)
    fun setDefaultReplyEnabled(enabled: Boolean) = prefs.edit().putBoolean(KEY_DEFAULT_REPLY_ENABLED, enabled).apply()
    
    fun getDefaultReplyType(): DefaultReplyType {
        val typeStr = prefs.getString(KEY_DEFAULT_REPLY_TYPE, DefaultReplyType.SAME_MENU.name)
        return try {
            DefaultReplyType.valueOf(typeStr ?: DefaultReplyType.SAME_MENU.name)
        } catch (e: Exception) {
            DefaultReplyType.SAME_MENU
        }
    }
    fun setDefaultReplyType(type: DefaultReplyType) = prefs.edit().putString(KEY_DEFAULT_REPLY_TYPE, type.name).apply()
    
    fun getCustomReplyMessage(): String = prefs.getString(KEY_CUSTOM_REPLY_MESSAGE, "Invalid option. Please select a valid option.") ?: "Invalid option. Please select a valid option."
    fun setCustomReplyMessage(message: String) = prefs.edit().putString(KEY_CUSTOM_REPLY_MESSAGE, message).apply()
    
    // Post-Selection Reply (After Final Selection)
    fun isPostSelectionEnabled(): Boolean = prefs.getBoolean(KEY_POST_SELECTION_ENABLED, false)
    fun setPostSelectionEnabled(enabled: Boolean) = prefs.edit().putBoolean(KEY_POST_SELECTION_ENABLED, enabled).apply()
    
    fun getPostSelectionType(): PostSelectionReplyType {
        val typeStr = prefs.getString(KEY_POST_SELECTION_TYPE, PostSelectionReplyType.MAIN_MENU.name)
        return try {
            PostSelectionReplyType.valueOf(typeStr ?: PostSelectionReplyType.MAIN_MENU.name)
        } catch (e: Exception) {
            PostSelectionReplyType.MAIN_MENU
        }
    }
    fun setPostSelectionType(type: PostSelectionReplyType) = prefs.edit().putString(KEY_POST_SELECTION_TYPE, type.name).apply()
    
    fun getPostSelectionMessage(): String = prefs.getString(KEY_POST_SELECTION_MESSAGE, "Menu session ended. Reply with any number to start again.") ?: "Menu session ended. Reply with any number to start again."
    fun setPostSelectionMessage(message: String) = prefs.edit().putString(KEY_POST_SELECTION_MESSAGE, message).apply()
    
    // Display Options
    fun isShowEmojisEnabled(): Boolean = prefs.getBoolean(KEY_SHOW_EMOJIS, false)
    fun setShowEmojisEnabled(enabled: Boolean) = prefs.edit().putBoolean(KEY_SHOW_EMOJIS, enabled).apply()
    
    fun isBoldHeaderEnabled(): Boolean = prefs.getBoolean(KEY_BOLD_HEADER, false)
    fun setBoldHeaderEnabled(enabled: Boolean) = prefs.edit().putBoolean(KEY_BOLD_HEADER, enabled).apply()

    fun isShowSelectionHintEnabled(): Boolean = prefs.getBoolean(KEY_SHOW_SELECTION_HINT, true)
    fun setShowSelectionHintEnabled(enabled: Boolean) = prefs.edit().putBoolean(KEY_SHOW_SELECTION_HINT, enabled).apply()
    
    // Session Options
    fun getMenuTimeoutSeconds(): Int {
        val seconds = prefs.getInt(KEY_MENU_TIMEOUT_SECONDS, MIN_MENU_TIMEOUT_SECONDS)
        return seconds.coerceIn(MIN_MENU_TIMEOUT_SECONDS, MAX_MENU_TIMEOUT_SECONDS)
    }
    
    fun getMenuTimeoutMillis(): Long = getMenuTimeoutSeconds() * 1000L
    
    fun setMenuTimeoutSeconds(seconds: Int) {
        prefs.edit()
            .putInt(KEY_MENU_TIMEOUT_SECONDS, seconds.coerceIn(MIN_MENU_TIMEOUT_SECONDS, MAX_MENU_TIMEOUT_SECONDS))
            .apply()
    }
    
    /**
     * Get all settings as a data class for easy access
     */
    fun getSettings(): MenuReplySettings {
        return MenuReplySettings(
            showMainMenuOption = isShowMainMenuEnabled(),
            showPreviousMenuOption = isShowPreviousMenuEnabled(),
            defaultReplyEnabled = isDefaultReplyEnabled(),
            defaultReplyType = getDefaultReplyType(),
            customReplyMessage = getCustomReplyMessage(),
            postSelectionEnabled = isPostSelectionEnabled(),
            postSelectionType = getPostSelectionType(),
            postSelectionMessage = getPostSelectionMessage(),
            showEmojis = isShowEmojisEnabled(),
            boldHeader = isBoldHeaderEnabled(),
            showSelectionHint = isShowSelectionHintEnabled(),
            menuTimeoutSeconds = getMenuTimeoutSeconds(),
            separateLines = false
        )
    }
}

/**
 * Data class to hold all menu reply settings
 */
data class MenuReplySettings(
    val showMainMenuOption: Boolean = true,
    val showPreviousMenuOption: Boolean = true,
    val defaultReplyEnabled: Boolean = true,
    val defaultReplyType: DefaultReplyType = DefaultReplyType.SAME_MENU,
    val customReplyMessage: String = "Invalid option. Please select a valid option.",
    val postSelectionEnabled: Boolean = false,
    val postSelectionType: PostSelectionReplyType = PostSelectionReplyType.MAIN_MENU,
    val postSelectionMessage: String = "Menu session ended. Reply with any number to start again.",
    val showEmojis: Boolean = false,
    val boldHeader: Boolean = false,
    val showSelectionHint: Boolean = true,
    val menuTimeoutSeconds: Int = 40,
    val separateLines: Boolean = false
)
