package com.message.bulksend.voicenotereply

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.util.Log

/**
 * Manager for Voice Note Reply feature
 * Handles enable/disable state and configuration
 */
object VoiceNoteReplyManager {
    
    private const val TAG = "VoiceNoteReplyManager"
    private const val PREFS_NAME = "voice_note_reply_prefs"
    private const val KEY_ENABLED = "voice_note_reply_enabled"
    private const val KEY_FOLDER_URI = "folder_uri"
    private const val KEY_FOLDER_PATH = "folder_path"
    private const val KEY_LAST_PHONE = "last_phone_number"
    private const val KEY_LAST_DURATION = "last_voice_duration"
    private const val KEY_LAST_FILE = "last_voice_file"
    
    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
    
    /**
     * Check if voice note reply is enabled
     */
    fun isEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_ENABLED, false)
    }
    
    /**
     * Enable or disable voice note reply
     */
    fun setEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply()
        Log.d(TAG, "Voice Note Reply ${if (enabled) "enabled" else "disabled"}")
    }
    
    /**
     * Save folder URI
     */
    fun saveFolderUri(context: Context, uri: Uri) {
        getPrefs(context).edit()
            .putString(KEY_FOLDER_URI, uri.toString())
            .putString(KEY_FOLDER_PATH, uri.path)
            .apply()
        Log.d(TAG, "Folder URI saved: $uri")
    }
    
    /**
     * Get folder URI
     */
    fun getFolderUri(context: Context): Uri? {
        val uriString = getPrefs(context).getString(KEY_FOLDER_URI, null)
        return if (uriString != null) Uri.parse(uriString) else null
    }
    
    /**
     * Get folder path
     */
    fun getFolderPath(context: Context): String? {
        return getPrefs(context).getString(KEY_FOLDER_PATH, null)
    }
    
    /**
     * Check if folder is selected
     */
    fun isFolderSelected(context: Context): Boolean {
        return getFolderUri(context) != null
    }
    
    /**
     * Save last processed voice note info
     */
    fun saveLastVoiceNote(context: Context, phoneNumber: String, fileName: String) {
        getPrefs(context).edit()
            .putString(KEY_LAST_PHONE, phoneNumber)
            .putString(KEY_LAST_FILE, fileName)
            .putLong(KEY_LAST_DURATION, System.currentTimeMillis())
            .apply()
        Log.d(TAG, "Saved voice note: $phoneNumber, file: $fileName")
    }
    
    /**
     * Get last phone number
     */
    fun getLastPhoneNumber(context: Context): String? {
        return getPrefs(context).getString(KEY_LAST_PHONE, null)
    }
    
    /**
     * Get last voice file name
     */
    fun getLastVoiceFile(context: Context): String? {
        return getPrefs(context).getString(KEY_LAST_FILE, null)
    }
}
