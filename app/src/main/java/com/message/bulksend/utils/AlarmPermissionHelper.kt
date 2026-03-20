package com.message.bulksend.utils

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log

/**
 * Helper class for managing SCHEDULE_EXACT_ALARM permission
 * 
 * Reference: https://developer.android.com/about/versions/14/changes/schedule-exact-alarms
 * 
 * Key Points:
 * - Android 12+ (API 31+): SCHEDULE_EXACT_ALARM permission required
 * - Android 14+ (API 34+): Permission denied by default for new installs
 * - Permission is special app access, not runtime permission
 * - User must grant from Settings > Apps > Special app access > Alarms & reminders
 */
object AlarmPermissionHelper {
    
    private const val TAG = "AlarmPermissionHelper"
    
    /**
     * Check if app can schedule exact alarms
     * 
     * @return true if permission granted or not required (Android < 12)
     */
    fun canScheduleExactAlarms(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val canSchedule = alarmManager.canScheduleExactAlarms()
            Log.d(TAG, "Can schedule exact alarms: $canSchedule (Android ${Build.VERSION.SDK_INT})")
            canSchedule
        } else {
            Log.d(TAG, "Android < 12, exact alarms allowed by default")
            true
        }
    }
    
    /**
     * Open system settings to grant SCHEDULE_EXACT_ALARM permission
     * 
     * This will open: Settings > Apps > Special app access > Alarms & reminders
     * 
     * @param context Context to start the intent
     */
    fun openAlarmPermissionSettings(context: Context) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val intent = Intent(
                    Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                    Uri.parse("package:${context.packageName}")
                ).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
                Log.d(TAG, "Opened alarm permission settings")
            } else {
                Log.d(TAG, "Alarm permission not required for Android < 12")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open alarm permission settings", e)
            // Fallback: Open app settings
            openAppSettings(context)
        }
    }
    
    /**
     * Open app settings as fallback
     */
    private fun openAppSettings(context: Context) {
        try {
            val intent = Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:${context.packageName}")
            ).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            Log.d(TAG, "Opened app settings as fallback")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open app settings", e)
        }
    }
    
    /**
     * Get user-friendly explanation for alarm permission
     */
    fun getPermissionExplanation(): String {
        return """
            Exact Alarm Permission is required to:
            
            ✓ Schedule campaigns at precise times
            ✓ Execute scheduled tasks reliably
            ✓ Send messages exactly when scheduled
            ✓ Work even in battery saver mode
            
            This permission allows the app to wake up the device and run scheduled campaigns at the exact time you specify.
        """.trimIndent()
    }
    
    /**
     * Get permission status info for debugging
     */
    fun getPermissionStatusInfo(context: Context): String {
        return buildString {
            appendLine("Alarm Permission Status:")
            appendLine("• Android Version: ${Build.VERSION.SDK_INT} (${Build.VERSION.RELEASE})")
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                val canSchedule = alarmManager.canScheduleExactAlarms()
                appendLine("• Can Schedule Exact Alarms: ${if (canSchedule) "✅ Yes" else "❌ No"}")
                
                if (!canSchedule) {
                    appendLine("• Action Required: Grant permission from Settings")
                    appendLine("• Path: Settings > Apps > Special app access > Alarms & reminders")
                }
            } else {
                appendLine("• Can Schedule Exact Alarms: ✅ Yes (Not required for Android < 12)")
            }
            
            appendLine("• Battery Optimization: ${if (BatteryOptimizationHelper.isIgnoringBatteryOptimizations(context)) "✅ Disabled" else "❌ Enabled"}")
        }
    }
    
    /**
     * Log permission status for debugging
     */
    fun logPermissionStatus(context: Context) {
        Log.d(TAG, getPermissionStatusInfo(context))
    }
    
    /**
     * Check if all required permissions for scheduled campaigns are granted
     */
    fun hasAllScheduledCampaignPermissions(context: Context): Boolean {
        val hasAlarmPermission = canScheduleExactAlarms(context)
        val hasBatteryOptimization = BatteryOptimizationHelper.isIgnoringBatteryOptimizations(context)
        
        Log.d(TAG, "Scheduled Campaign Permissions - Alarm: $hasAlarmPermission, Battery: $hasBatteryOptimization")
        
        return hasAlarmPermission && hasBatteryOptimization
    }
    
    /**
     * Get list of missing permissions for scheduled campaigns
     */
    fun getMissingPermissions(context: Context): List<String> {
        val missing = mutableListOf<String>()
        
        if (!canScheduleExactAlarms(context)) {
            missing.add("Exact Alarm Permission")
        }
        
        if (!BatteryOptimizationHelper.isIgnoringBatteryOptimizations(context)) {
            missing.add("Battery Optimization Exemption")
        }
        
        return missing
    }
}
