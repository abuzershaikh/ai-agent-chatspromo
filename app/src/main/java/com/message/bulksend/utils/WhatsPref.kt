package com.message.bulksend.utils

import android.content.Context
import android.content.pm.LauncherApps
import android.os.Build
import android.os.UserHandle
import android.os.UserManager
import android.util.Log

/**
 * WhatsApp Preference Manager
 * Handles saving and retrieving user's selected WhatsApp app
 * Supports: WhatsApp, WhatsApp Clone, WhatsApp Business, WhatsApp Business Clone
 */
object WhatsPref {
    
    private const val PREF_NAME = "whatsapp_prefs"
    private const val KEY_SELECTED_PACKAGE = "selected_whatsapp_package"
    private const val KEY_SELECTED_USER_ID = "selected_user_id"
    private const val KEY_SELECTED_APP_NAME = "selected_app_name"
    private const val KEY_IS_CLONE = "is_clone_app"
    private const val KEY_CLONE_INDEX = "clone_index"
    
    private const val TAG = "WhatsPref"
    
    // WhatsApp package names
    const val WHATSAPP_PACKAGE = "com.whatsapp"
    const val WHATSAPP_BUSINESS_PACKAGE = "com.whatsapp.w4b"
    
    /**
     * Data class to hold WhatsApp app info
     */
    data class WhatsAppInfo(
        val packageName: String,
        val appName: String,
        val userHandle: UserHandle,
        val userId: Int,
        val isClone: Boolean = false,
        val cloneIndex: Int = 0  // 0 = main app, 1+ = clone apps
    )
    
    /**
     * Save selected WhatsApp app
     */
    fun saveSelectedWhatsApp(context: Context, whatsAppInfo: WhatsAppInfo) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().apply {
            putString(KEY_SELECTED_PACKAGE, whatsAppInfo.packageName)
            putInt(KEY_SELECTED_USER_ID, whatsAppInfo.userId)
            putString(KEY_SELECTED_APP_NAME, whatsAppInfo.appName)
            putBoolean(KEY_IS_CLONE, whatsAppInfo.isClone)
            putInt(KEY_CLONE_INDEX, whatsAppInfo.cloneIndex)
            apply()
        }
        Log.d(TAG, "✅ Saved WhatsApp: ${whatsAppInfo.appName} (${whatsAppInfo.packageName}, clone: ${whatsAppInfo.isClone}, userId: ${whatsAppInfo.userId})")
    }
    
    /**
     * Get selected WhatsApp package name
     */
    fun getSelectedPackage(context: Context): String {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_SELECTED_PACKAGE, WHATSAPP_PACKAGE) ?: WHATSAPP_PACKAGE
    }
    
    /**
     * Get selected user ID (for clone apps)
     */
    fun getSelectedUserId(context: Context): Int {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return prefs.getInt(KEY_SELECTED_USER_ID, 0)
    }
    
    /**
     * Get selected app name
     */
    fun getSelectedAppName(context: Context): String {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_SELECTED_APP_NAME, "WhatsApp") ?: "WhatsApp"
    }
    
    /**
     * Check if selected app is a clone
     */
    fun isCloneSelected(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_IS_CLONE, false)
    }
    
    /**
     * Get clone index (0 = main, 1+ = clone)
     */
    fun getCloneIndex(context: Context): Int {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return prefs.getInt(KEY_CLONE_INDEX, 0)
    }
    
    /**
     * Get all available WhatsApp apps including clones
     * Uses LauncherApps API to detect dual/clone apps
     */
    fun getAvailableWhatsApps(context: Context): List<WhatsAppInfo> {
        val whatsAppList = mutableListOf<WhatsAppInfo>()
        
        try {
            val userManager = context.getSystemService(Context.USER_SERVICE) as UserManager
            val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
            val userProfiles = userManager.userProfiles
            
            Log.d(TAG, "Found ${userProfiles.size} user profiles")
            
            // Track which apps we've found for each user
            val whatsAppUsers = mutableListOf<Pair<UserHandle, Int>>()
            val businessUsers = mutableListOf<Pair<UserHandle, Int>>()
            
            for (userHandle in userProfiles) {
                val userId = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    userHandle.hashCode()
                } else {
                    0
                }
                
                // Check WhatsApp
                val whatsAppActivities = launcherApps.getActivityList(WHATSAPP_PACKAGE, userHandle)
                if (whatsAppActivities.isNotEmpty()) {
                    whatsAppUsers.add(Pair(userHandle, userId))
                    Log.d(TAG, "Found WhatsApp for user: $userId")
                }
                
                // Check WhatsApp Business
                val businessActivities = launcherApps.getActivityList(WHATSAPP_BUSINESS_PACKAGE, userHandle)
                if (businessActivities.isNotEmpty()) {
                    businessUsers.add(Pair(userHandle, userId))
                    Log.d(TAG, "Found WhatsApp Business for user: $userId")
                }
            }
            
            // Add WhatsApp entries
            whatsAppUsers.forEachIndexed { index, (userHandle, userId) ->
                val isClone = index > 0
                val appName = if (isClone) "WhatsApp Clone" else "WhatsApp"
                whatsAppList.add(
                    WhatsAppInfo(
                        packageName = WHATSAPP_PACKAGE,
                        appName = appName,
                        userHandle = userHandle,
                        userId = userId,
                        isClone = isClone,
                        cloneIndex = index
                    )
                )
                Log.d(TAG, "Added: $appName (userId: $userId, isClone: $isClone)")
            }
            
            // Add WhatsApp Business entries
            businessUsers.forEachIndexed { index, (userHandle, userId) ->
                val isClone = index > 0
                val appName = if (isClone) "WhatsApp Business Clone" else "WhatsApp Business"
                whatsAppList.add(
                    WhatsAppInfo(
                        packageName = WHATSAPP_BUSINESS_PACKAGE,
                        appName = appName,
                        userHandle = userHandle,
                        userId = userId,
                        isClone = isClone,
                        cloneIndex = index
                    )
                )
                Log.d(TAG, "Added: $appName (userId: $userId, isClone: $isClone)")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error detecting WhatsApp apps: ${e.message}", e)
            // Fallback to basic detection
            return getAvailableWhatsAppsFallback(context)
        }
        
        // If LauncherApps didn't find anything, use fallback
        if (whatsAppList.isEmpty()) {
            return getAvailableWhatsAppsFallback(context)
        }
        
        return whatsAppList
    }
    
    /**
     * Fallback method for basic WhatsApp detection
     */
    private fun getAvailableWhatsAppsFallback(context: Context): List<WhatsAppInfo> {
        val whatsAppList = mutableListOf<WhatsAppInfo>()
        
        // Check WhatsApp
        if (isPackageInstalled(context, WHATSAPP_PACKAGE)) {
            whatsAppList.add(
                WhatsAppInfo(
                    packageName = WHATSAPP_PACKAGE,
                    appName = "WhatsApp",
                    userHandle = android.os.Process.myUserHandle(),
                    userId = 0,
                    isClone = false,
                    cloneIndex = 0
                )
            )
            Log.d(TAG, "Fallback: Found WhatsApp")
        }
        
        // Check WhatsApp Business
        if (isPackageInstalled(context, WHATSAPP_BUSINESS_PACKAGE)) {
            whatsAppList.add(
                WhatsAppInfo(
                    packageName = WHATSAPP_BUSINESS_PACKAGE,
                    appName = "WhatsApp Business",
                    userHandle = android.os.Process.myUserHandle(),
                    userId = 0,
                    isClone = false,
                    cloneIndex = 0
                )
            )
            Log.d(TAG, "Fallback: Found WhatsApp Business")
        }
        
        return whatsAppList
    }
    
    /**
     * Check if a package is installed
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
     * Get UserHandle for selected WhatsApp
     */
    fun getSelectedUserHandle(context: Context): UserHandle {
        val userId = getSelectedUserId(context)
        // For main user (userId 0), return current process user handle
        if (userId == 0) {
            return android.os.Process.myUserHandle()
        }
        // For clone apps, we need to find the correct UserHandle
        try {
            val userManager = context.getSystemService(Context.USER_SERVICE) as UserManager
            for (userHandle in userManager.userProfiles) {
                if (userHandle.hashCode() == userId) {
                    return userHandle
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting UserHandle: ${e.message}")
        }
        return android.os.Process.myUserHandle()
    }
    
    /**
     * Clear selection (reset to default)
     */
    fun clearSelection(context: Context) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().clear().apply()
        Log.d(TAG, "WhatsApp selection cleared")
    }
    
    /**
     * Check if user has made a selection
     */
    fun hasSelection(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return prefs.contains(KEY_SELECTED_PACKAGE)
    }
}