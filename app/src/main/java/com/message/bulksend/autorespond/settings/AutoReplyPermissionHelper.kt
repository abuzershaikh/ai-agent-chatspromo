package com.message.bulksend.autorespond.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.util.Log

/**
 * Helper class to manage all permissions and settings required for auto-reply to work properly
 */
class AutoReplyPermissionHelper(private val context: Context) {
    
    companion object {
        const val TAG = "AutoReplyPermissionHelper"
    }
    
    /**
     * Check if notification access permission is granted
     */
    fun isNotificationAccessGranted(): Boolean {
        val enabledListeners = Settings.Secure.getString(
            context.contentResolver,
            "enabled_notification_listeners"
        )
        return enabledListeners?.contains(context.packageName) == true
    }
    
    /**
     * Check if battery optimization is disabled (app is whitelisted)
     */
    fun isBatteryOptimizationDisabled(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            powerManager.isIgnoringBatteryOptimizations(context.packageName)
        } else {
            true // Not applicable for older versions
        }
    }
    
    /**
     * Check if auto-start permission is likely granted (manufacturer specific)
     */
    fun isAutoStartEnabled(): Boolean {
        // This is manufacturer specific and hard to detect programmatically
        // We'll assume it's enabled and guide user to check manually
        return true
    }
    
    /**
     * Get all permission statuses
     */
    fun getAllPermissionStatuses(): PermissionStatus {
        return PermissionStatus(
            notificationAccess = isNotificationAccessGranted(),
            batteryOptimization = isBatteryOptimizationDisabled(),
            autoStart = isAutoStartEnabled()
        )
    }
    
    /**
     * Open notification access settings
     */
    fun openNotificationAccessSettings() {
        try {
            val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Error opening notification settings: ${e.message}")
        }
    }
    
    /**
     * Open battery optimization settings
     */
    fun openBatteryOptimizationSettings() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                // First try direct battery optimization request
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                intent.data = Uri.parse("package:${context.packageName}")
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                context.startActivity(intent)
                Log.d(TAG, "Opened battery optimization request dialog")
                return
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error opening battery optimization request: ${e.message}")
        }
        
        // Fallback 1: Try to open ignore battery optimization settings
        try {
            val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(intent)
            Log.d(TAG, "Opened battery optimization settings")
            return
        } catch (e: Exception) {
            Log.e(TAG, "Error opening battery optimization settings: ${e.message}")
        }
        
        // Fallback 2: Try app-specific battery settings
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            intent.data = Uri.parse("package:${context.packageName}")
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(intent)
            Log.d(TAG, "Opened app details settings")
            return
        } catch (e: Exception) {
            Log.e(TAG, "Error opening app details: ${e.message}")
        }
        
        // Fallback 3: General battery settings
        try {
            val intent = Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(intent)
            Log.d(TAG, "Opened battery saver settings")
        } catch (e: Exception) {
            Log.e(TAG, "All battery settings failed: ${e.message}")
            // Last resort - open general settings
            try {
                val intent = Intent(Settings.ACTION_SETTINGS)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                context.startActivity(intent)
            } catch (e2: Exception) {
                Log.e(TAG, "Even general settings failed: ${e2.message}")
            }
        }
    }
    
    /**
     * Open battery whitelist settings specifically - Always opens app settings as reliable fallback
     */
    fun openBatteryWhitelistSettings() {
        var settingsOpened = false
        
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                // Try the direct whitelist request first
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                intent.data = Uri.parse("package:${context.packageName}")
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                context.startActivity(intent)
                Log.d(TAG, "Opened battery whitelist request")
                settingsOpened = true
                return
            }
        } catch (e: Exception) {
            Log.e(TAG, "Battery whitelist request failed: ${e.message}")
        }
        
        // If whitelist request failed, try battery optimization settings
        if (!settingsOpened) {
            try {
                val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                context.startActivity(intent)
                Log.d(TAG, "Opened battery optimization settings")
                settingsOpened = true
                return
            } catch (e: Exception) {
                Log.e(TAG, "Battery optimization settings failed: ${e.message}")
            }
        }
        
        // If all battery-specific settings failed, ALWAYS open app settings
        if (!settingsOpened) {
            openAppSettings()
        }
    }
    
    /**
     * Open app settings for manual configuration - Guaranteed to work
     */
    fun openAppSettings() {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            intent.data = Uri.parse("package:${context.packageName}")
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(intent)
            Log.d(TAG, "Opened app settings successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error opening app settings: ${e.message}")
            // Fallback to general settings if app settings fail
            try {
                val intent = Intent(Settings.ACTION_SETTINGS)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                context.startActivity(intent)
                Log.d(TAG, "Opened general settings as fallback")
            } catch (e2: Exception) {
                Log.e(TAG, "Even general settings failed: ${e2.message}")
            }
        }
    }
    
    /**
     * Open auto-start settings (manufacturer specific)
     */
    fun openAutoStartSettings() {
        try {
            val manufacturer = Build.MANUFACTURER.lowercase()
            val intent = when {
                manufacturer.contains("xiaomi") -> {
                    Intent().apply {
                        component = android.content.ComponentName(
                            "com.miui.securitycenter",
                            "com.miui.permcenter.autostart.AutoStartManagementActivity"
                        )
                    }
                }
                manufacturer.contains("oppo") -> {
                    Intent().apply {
                        component = android.content.ComponentName(
                            "com.coloros.safecenter",
                            "com.coloros.safecenter.permission.startup.StartupAppListActivity"
                        )
                    }
                }
                manufacturer.contains("vivo") -> {
                    Intent().apply {
                        component = android.content.ComponentName(
                            "com.vivo.permissionmanager",
                            "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"
                        )
                    }
                }
                manufacturer.contains("huawei") -> {
                    Intent().apply {
                        component = android.content.ComponentName(
                            "com.huawei.systemmanager",
                            "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"
                        )
                    }
                }
                else -> {
                    // Fallback to general app settings
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.parse("package:${context.packageName}")
                    }
                }
            }
            
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Error opening auto-start settings: ${e.message}")
            // Fallback to app settings
            openAppSettings()
        }
    }
    
    /**
     * Get manufacturer-specific instructions
     */
    fun getManufacturerInstructions(): ManufacturerInstructions {
        val manufacturer = Build.MANUFACTURER.lowercase()
        return when {
            manufacturer.contains("xiaomi") -> ManufacturerInstructions(
                manufacturer = "Xiaomi/MIUI",
                instructions = listOf(
                    "Settings → Apps → Manage apps → Your App",
                    "Battery saver → No restrictions",
                    "Autostart → Enable",
                    "Other permissions → Display pop-up windows while running in background → Enable"
                )
            )
            manufacturer.contains("oppo") -> ManufacturerInstructions(
                manufacturer = "Oppo/ColorOS",
                instructions = listOf(
                    "Settings → Battery → Battery Optimization → Your App → Don't optimize",
                    "Settings → Privacy permissions → Startup manager → Your App → Enable",
                    "Settings → Apps → Your App → Battery → Allow background activity"
                )
            )
            manufacturer.contains("vivo") -> ManufacturerInstructions(
                manufacturer = "Vivo",
                instructions = listOf(
                    "Settings → Battery → Background app refresh → Your App → Enable",
                    "Settings → More settings → Applications → Autostart → Your App → Enable",
                    "Settings → Apps → Your App → Battery → High background activity"
                )
            )
            manufacturer.contains("huawei") -> ManufacturerInstructions(
                manufacturer = "Huawei/EMUI",
                instructions = listOf(
                    "Settings → Apps → Your App → Battery → App launch → Manage manually",
                    "Enable: Auto-launch, Secondary launch, Run in background",
                    "Settings → Battery → App launch → Your App → Manage manually"
                )
            )
            manufacturer.contains("samsung") -> ManufacturerInstructions(
                manufacturer = "Samsung/One UI",
                instructions = listOf(
                    "Settings → Apps → Your App → Battery → Allow background activity",
                    "Settings → Device care → Battery → App power management → Apps that won't be put to sleep → Add Your App",
                    "Settings → Apps → Your App → Permissions → Allow all permissions"
                )
            )
            else -> ManufacturerInstructions(
                manufacturer = "Android",
                instructions = listOf(
                    "Settings → Apps → Your App → Battery → Unrestricted",
                    "Settings → Apps → Your App → Allow background activity",
                    "Disable battery optimization for the app"
                )
            )
        }
    }
}

/**
 * Data class for permission status
 */
data class PermissionStatus(
    val notificationAccess: Boolean,
    val batteryOptimization: Boolean,
    val autoStart: Boolean
) {
    val allGranted: Boolean
        get() = notificationAccess && batteryOptimization && autoStart
}

/**
 * Data class for manufacturer-specific instructions
 */
data class ManufacturerInstructions(
    val manufacturer: String,
    val instructions: List<String>
)