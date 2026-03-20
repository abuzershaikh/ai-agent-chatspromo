package com.message.bulksend.utils

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.annotation.RequiresApi

object BatteryOptimizationHelper {
    
    /**
     * Check if the app is whitelisted from battery optimization
     */
    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            powerManager.isIgnoringBatteryOptimizations(context.packageName)
        } else {
            true // No battery optimization on older versions
        }
    }
    
    /**
     * Show dialog to request battery optimization whitelist
     */
    fun showBatteryOptimizationDialog(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !isIgnoringBatteryOptimizations(context)) {
            AlertDialog.Builder(context)
                .setTitle("Battery Optimization")
                .setMessage("To ensure scheduled campaigns execute on time, please disable battery optimization for this app.\n\nThis allows the app to run in the background and execute your scheduled messages even when the phone is idle.")
                .setPositiveButton("Open Settings") { _, _ ->
                    requestBatteryOptimizationWhitelist(context)
                }
                .setNegativeButton("Later") { dialog, _ ->
                    dialog.dismiss()
                }
                .setCancelable(false)
                .show()
        }
    }
    
    /**
     * Request to whitelist app from battery optimization
     */
    @RequiresApi(Build.VERSION_CODES.M)
    fun requestBatteryOptimizationWhitelist(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:${context.packageName}")
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            // Fallback to general battery optimization settings
            try {
                val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                context.startActivity(intent)
            } catch (e2: Exception) {
                // Last fallback to app settings
                openAppSettings(context)
            }
        }
    }
    
    /**
     * Open app settings page
     */
    fun openAppSettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            // Fallback to general settings
            val intent = Intent(Settings.ACTION_SETTINGS)
            context.startActivity(intent)
        }
    }
    
    /**
     * Show comprehensive setup dialog for scheduled campaigns
     */
    fun showScheduledCampaignSetupDialog(context: Context) {
        val message = buildString {
            appendLine("For reliable scheduled campaign execution:")
            appendLine()
            appendLine("1. ✅ Disable Battery Optimization")
            appendLine("   • Allows app to run in background")
            appendLine("   • Ensures campaigns execute on time")
            appendLine()
            appendLine("2. ✅ Enable Auto-Start (if available)")
            appendLine("   • Allows app to start automatically")
            appendLine("   • Required for some device manufacturers")
            appendLine()
            appendLine("3. ✅ Keep Accessibility Service Enabled")
            appendLine("   • Required for WhatsApp automation")
            appendLine("   • Must remain active for campaigns")
            appendLine()
            appendLine("Would you like to configure these settings now?")
        }
        
        AlertDialog.Builder(context)
            .setTitle("Scheduled Campaign Setup")
            .setMessage(message)
            .setPositiveButton("Configure Now") { _, _ ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    requestBatteryOptimizationWhitelist(context)
                }
            }
            .setNegativeButton("Skip") { dialog, _ ->
                dialog.dismiss()
            }
            .setCancelable(true)
            .show()
    }
    
    /**
     * Get device-specific battery optimization instructions
     */
    fun getDeviceSpecificInstructions(): String {
        val manufacturer = Build.MANUFACTURER.lowercase()
        
        return when {
            manufacturer.contains("xiaomi") -> {
                "MIUI: Settings → Apps → Manage apps → BulkSend → Battery saver → No restrictions"
            }
            manufacturer.contains("huawei") -> {
                "EMUI: Settings → Apps → BulkSend → Battery → App launch → Manage manually → Enable all"
            }
            manufacturer.contains("oppo") -> {
                "ColorOS: Settings → Apps → BulkSend → Battery usage → Allow background activity"
            }
            manufacturer.contains("vivo") -> {
                "FunTouch: Settings → Apps → BulkSend → Battery → Background app refresh → Allow"
            }
            manufacturer.contains("oneplus") -> {
                "OxygenOS: Settings → Apps → BulkSend → Battery optimization → Don't optimize"
            }
            manufacturer.contains("samsung") -> {
                "One UI: Settings → Apps → BulkSend → Battery → Optimize battery usage → Turn off"
            }
            else -> {
                "Settings → Apps → BulkSend → Battery → Battery optimization → Don't optimize"
            }
        }
    }
}