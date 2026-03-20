package com.message.bulksend.utils

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.text.TextUtils
import android.util.Log
import com.message.bulksend.bulksend.WhatsAppAutoSendService

/**
 * Checks if the accessibility service for this app is enabled in the device settings.
 */
fun isAccessibilityServiceEnabled(context: Context): Boolean {
    val accessibilityEnabled = Settings.Secure.getInt(
        context.contentResolver,
        Settings.Secure.ACCESSIBILITY_ENABLED,
        0
    )
    if (accessibilityEnabled == 0) return false

    val settingValue = Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
    )
    if (settingValue != null) {
        val serviceName = context.packageName + "/" + WhatsAppAutoSendService::class.java.name
        val splitter = TextUtils.SimpleStringSplitter(':')
        splitter.setString(settingValue)
        while (splitter.hasNext()) {
            if (splitter.next().equals(serviceName, ignoreCase = true)) {
                return true
            }
        }
    }
    return false
}

/**
 * Checks if a specific package is installed on the device.
 */
fun isPackageInstalled(context: Context, packageName: String): Boolean {
    return try {
        // Try with different flags for Android 11+ compatibility
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // For Android 11+, try with MATCH_UNINSTALLED_PACKAGES flag
            try {
                context.packageManager.getPackageInfo(packageName, PackageManager.MATCH_UNINSTALLED_PACKAGES)
                true
            } catch (e: PackageManager.NameNotFoundException) {
                // Fallback to regular check
                try {
                    context.packageManager.getPackageInfo(packageName, 0)
                    true
                } catch (e2: PackageManager.NameNotFoundException) {
                    false
                }
            }
        } else {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        }
    } catch (e: PackageManager.NameNotFoundException) {
        false
    } catch (e: Exception) {
        Log.e("AppUtils", "Error checking package $packageName: ${e.message}")
        false
    }
}
