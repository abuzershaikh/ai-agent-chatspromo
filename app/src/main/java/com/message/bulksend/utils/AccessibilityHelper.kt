package com.message.bulksend.utils

import android.content.Context
import android.provider.Settings
import android.text.TextUtils

/**
 * Helper object for accessibility service related functions
 * 
 * Note: For showing the accessibility permission dialog in Compose,
 * use the AccessibilityPermissionDialog composable directly in your screen.
 * 
 * Example:
 * ```
 * var showAccessibilityDialog by remember { mutableStateOf(false) }
 * 
 * if (showAccessibilityDialog) {
 *     AccessibilityPermissionDialog(
 *         onAgree = { /* action */ },
 *         onDisagree = { /* action */ },
 *         onDismiss = { showAccessibilityDialog = false }
 *     )
 * }
 * ```
 */
object AccessibilityHelper {

    /**
     * Check if accessibility service is enabled for the app
     * 
     * @param context Application context
     * @param serviceName Full service class name (e.g., "com.example.MyAccessibilityService")
     * @return true if service is enabled, false otherwise
     */
    fun isAccessibilityServiceEnabled(context: Context, serviceName: String): Boolean {
        val expectedComponentName = "${context.packageName}/$serviceName"
        
        val enabledServicesSetting = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        val colonSplitter = TextUtils.SimpleStringSplitter(':')
        colonSplitter.setString(enabledServicesSetting)

        while (colonSplitter.hasNext()) {
            val componentName = colonSplitter.next()
            if (componentName.equals(expectedComponentName, ignoreCase = true)) {
                return true
            }
        }
        return false
    }

    /**
     * Check if accessibility permission is granted
     * Returns true if granted, false if not
     * 
     * Note: This function only checks the permission status.
     * To show the dialog, use the Compose AccessibilityPermissionDialog directly.
     */
    fun checkAccessibilityPermission(
        context: Context,
        serviceName: String
    ): Boolean {
        return isAccessibilityServiceEnabled(context, serviceName)
    }
}
