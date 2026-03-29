package com.message.bulksend.autorespond

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.text.TextUtils
import android.util.Log

/**
 * Manager class to handle Auto Respond logic
 * Keeps Activity code clean and minimal
 */
class AutoRespondManager(private val context: Context) {

    companion object {
        const val TAG = "AutoRespondManager"
        private const val PREFS_NAME = "auto_respond_prefs"
        private const val KEY_AUTO_RESPOND_ENABLED = "auto_respond_enabled"
        private const val KEY_RESPONSE_MESSAGE = "response_message"
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Check if notification listener permission is granted
     */
    fun isNotificationPermissionGranted(): Boolean {
        val enabledListeners = Settings.Secure.getString(
            context.contentResolver,
            "enabled_notification_listeners"
        )
        val packageName = context.packageName
        return enabledListeners != null && enabledListeners.contains(packageName)
    }

    /**
     * Open notification listener settings
     */
    fun openNotificationSettings() {
        val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    /**
     * Legacy toggle setter kept for backward compatibility.
     * Auto-reply now depends on notification access instead of a separate enable switch.
     */
    fun setAutoRespondEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_RESPOND_ENABLED, enabled).apply()
        Log.d(TAG, "Stored legacy auto respond flag: ${if (enabled) "enabled" else "disabled"}")
    }

    /**
     * Auto-reply is considered enabled when notification listener permission is granted.
     * This removes the extra in-app enable step.
     */
    fun isAutoRespondEnabled(): Boolean {
        return isNotificationPermissionGranted()
    }

    /**
     * Save response message
     */
    fun saveResponseMessage(message: String) {
        prefs.edit().putString(KEY_RESPONSE_MESSAGE, message).apply()
        Log.d(TAG, "Response message saved: $message")
    }

    /**
     * Get saved response message
     */
    fun getResponseMessage(): String {
        return prefs.getString(KEY_RESPONSE_MESSAGE, "") ?: ""
    }

    /**
     * Process incoming notification and generate response
     */
    fun processNotification(senderName: String, messageText: String): String? {
        if (!isAutoRespondEnabled()) {
            return null
        }

        val responseTemplate = getResponseMessage()
        if (responseTemplate.isEmpty()) {
            return null
        }

        // Replace placeholders in response message
        return responseTemplate
            .replace("{name}", senderName)
            .replace("{message}", messageText)
    }
}
