package com.message.bulksend.utils

import android.app.AlarmManager
import android.content.Context
import android.os.Build
import android.os.PowerManager
import android.util.Log

object DozeModeHelper {
    
    /**
     * Check if device is in doze mode
     */
    fun isDeviceInDozeMode(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            powerManager.isDeviceIdleMode
        } else {
            false
        }
    }
    
    /**
     * Check if device is in light doze mode
     */
    fun isDeviceInLightDozeMode(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            // Note: isLightDeviceIdleMode is not available in public API
            // Using alternative approach
            false // Placeholder - would need reflection or other method
        } else {
            false
        }
    }
    
    /**
     * Check if app can schedule exact alarms
     */
    fun canScheduleExactAlarms(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }
    }
    
    /**
     * Get comprehensive device power state info
     */
    fun getDevicePowerStateInfo(context: Context): String {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        
        return buildString {
            appendLine("Device Power State:")
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                appendLine("• Battery Optimization: ${if (BatteryOptimizationHelper.isIgnoringBatteryOptimizations(context)) "Disabled ✅" else "Enabled ❌"}")
                appendLine("• Doze Mode: ${if (powerManager.isDeviceIdleMode) "Active ❌" else "Inactive ✅"}")
                
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    appendLine("• Light Doze: ${if (isDeviceInLightDozeMode(context)) "Active ❌" else "Inactive ✅"}")
                }
            }
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                appendLine("• Exact Alarms: ${if (alarmManager.canScheduleExactAlarms()) "Allowed ✅" else "Restricted ❌"}")
            }
            
            appendLine("• Interactive: ${if (powerManager.isInteractive) "Yes ✅" else "No ❌"}")
            appendLine("• Screen On: ${if (powerManager.isScreenOn) "Yes ✅" else "No ❌"}")
        }
    }
    
    /**
     * Log device power state for debugging
     */
    fun logDevicePowerState(context: Context, tag: String = "DozeModeHelper") {
        Log.d(tag, getDevicePowerStateInfo(context))
    }
    
    /**
     * Check if scheduled campaigns can execute reliably
     */
    fun canExecuteScheduledCampaignsReliably(context: Context): Boolean {
        val batteryOptimized = BatteryOptimizationHelper.isIgnoringBatteryOptimizations(context)
        val canScheduleExact = canScheduleExactAlarms(context)
        
        return batteryOptimized && canScheduleExact
    }
    
    /**
     * Get recommendations for reliable scheduled execution
     */
    fun getReliabilityRecommendations(context: Context): List<String> {
        val recommendations = mutableListOf<String>()
        
        if (!BatteryOptimizationHelper.isIgnoringBatteryOptimizations(context)) {
            recommendations.add("Disable battery optimization for this app")
        }
        
        if (!canScheduleExactAlarms(context)) {
            recommendations.add("Allow exact alarm scheduling in system settings")
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            if (powerManager.isDeviceIdleMode || isDeviceInLightDozeMode(context)) {
                recommendations.add("Device is in doze mode - campaigns may be delayed")
            }
        }
        
        // Device-specific recommendations
        val manufacturer = Build.MANUFACTURER.lowercase()
        when {
            manufacturer.contains("xiaomi") -> {
                recommendations.add("MIUI: Enable 'Autostart' for this app")
                recommendations.add("MIUI: Set battery saver to 'No restrictions'")
            }
            manufacturer.contains("huawei") -> {
                recommendations.add("EMUI: Enable 'App launch' management")
                recommendations.add("EMUI: Add app to 'Protected apps'")
            }
            manufacturer.contains("oppo") -> {
                recommendations.add("ColorOS: Enable 'Allow background activity'")
                recommendations.add("ColorOS: Disable 'Sleep standby optimization'")
            }
            manufacturer.contains("vivo") -> {
                recommendations.add("FunTouch: Enable 'Background app refresh'")
                recommendations.add("FunTouch: Add to 'High background app consumption'")
            }
            manufacturer.contains("oneplus") -> {
                recommendations.add("OxygenOS: Disable 'Battery optimization'")
                recommendations.add("OxygenOS: Enable 'Allow background activity'")
            }
        }
        
        return recommendations
    }
}