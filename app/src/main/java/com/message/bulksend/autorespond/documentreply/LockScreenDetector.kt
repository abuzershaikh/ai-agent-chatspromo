package com.message.bulksend.autorespond.documentreply

import android.app.KeyguardManager
import android.content.Context
import android.os.PowerManager
import android.util.Log

/**
 * Utility class to detect if phone is locked or screen is off
 */
object LockScreenDetector {
    
    private const val TAG = "LockScreenDetector"
    
    /**
     * Check if phone is currently locked or screen is off
     */
    fun isPhoneLocked(context: Context): Boolean {
        val keyguardManager = context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        
        val isKeyguardLocked = keyguardManager.isKeyguardLocked
        val isScreenOn = powerManager.isInteractive
        
        Log.d(TAG, "🔒 Lock status - Keyguard locked: $isKeyguardLocked, Screen on: $isScreenOn")
        
        // Phone is considered locked if:
        // 1. Keyguard is active (lock screen showing)
        // 2. Screen is off (device is sleeping)
        val isLocked = isKeyguardLocked || !isScreenOn
        
        Log.d(TAG, "🔒 Phone is locked: $isLocked")
        return isLocked
    }
    
    /**
     * Check if device has any security lock set (PIN, Pattern, Password, Fingerprint, etc.)
     */
    fun hasSecurityLockSet(context: Context): Boolean {
        val keyguardManager = context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        val hasLock = keyguardManager.isKeyguardSecure
        
        Log.d(TAG, "🔐 Security lock set: $hasLock")
        return hasLock
    }
    
    /**
     * Get user-friendly lock status message
     */
    fun getLockStatusMessage(context: Context): String {
        val isLocked = isPhoneLocked(context)
        val hasSecurityLock = hasSecurityLockSet(context)
        
        return when {
            isLocked && hasSecurityLock -> "Phone is locked with security lock"
            isLocked && !hasSecurityLock -> "Screen is off"
            !isLocked && hasSecurityLock -> "Phone is unlocked but has security lock"
            else -> "Phone is unlocked with no security lock"
        }
    }
}