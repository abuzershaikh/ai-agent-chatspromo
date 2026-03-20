package com.message.bulksend.utils

import android.content.Context
import android.provider.Settings
import android.text.TextUtils
import com.message.bulksend.bulksend.WhatsAppAutoSendService

object AccessibilityChecker {
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
}

