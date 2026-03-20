package com.message.bulksend.autorespond.documentreply

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.Log

/**
 * Dialog to warn user about lock screen limitations for document reply
 */
object LockScreenWarningDialog {
    
    private const val TAG = "LockScreenWarning"
    
    /**
     * Show warning dialog about lock screen limitations
     */
    fun showLockScreenWarning(context: Context, onDismiss: (() -> Unit)? = null) {
        try {
            val dialog = AlertDialog.Builder(context)
                .setTitle("🔒 Document Reply - Lock Screen Issue")
                .setMessage(
                    "Document Reply feature cannot work when your phone is locked.\n\n" +
                    "📱 Current Status: Phone is locked or screen is off\n\n" +
                    "✅ To use Document Reply:\n" +
                    "• Keep your phone unlocked when expecting messages\n" +
                    "• Or temporarily disable lock screen security\n\n" +
                    "⚠️ Document Reply will work normally when phone is unlocked."
                )
                .setPositiveButton("Open Security Settings") { _, _ ->
                    try {
                        // Open security settings to let user disable lock
                        val intent = Intent(Settings.ACTION_SECURITY_SETTINGS).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        context.startActivity(intent)
                        Log.d(TAG, "✅ Opened security settings")
                    } catch (e: Exception) {
                        Log.e(TAG, "❌ Error opening security settings: ${e.message}")
                    }
                    onDismiss?.invoke()
                }
                .setNegativeButton("Got It") { _, _ ->
                    Log.d(TAG, "✅ User acknowledged lock screen warning")
                    onDismiss?.invoke()
                }
                .setNeutralButton("Disable Lock Screen") { _, _ ->
                    try {
                        // Open lock screen settings
                        val intent = Intent(Settings.ACTION_SECURITY_SETTINGS).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        context.startActivity(intent)
                        Log.d(TAG, "✅ Opened lock screen settings")
                    } catch (e: Exception) {
                        Log.e(TAG, "❌ Error opening lock screen settings: ${e.message}")
                    }
                    onDismiss?.invoke()
                }
                .setCancelable(true)
                .setOnDismissListener {
                    onDismiss?.invoke()
                }
                .create()
            
            dialog.show()
            Log.d(TAG, "✅ Lock screen warning dialog shown")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error showing lock screen warning dialog: ${e.message}")
            onDismiss?.invoke()
        }
    }
    
    /**
     * Show simple toast-like warning for background scenarios
     */
    fun showSimpleLockWarning(context: Context) {
        try {
            // For background scenarios, we can't show dialogs
            // So we'll log and potentially show a notification
            Log.w(TAG, "⚠️ Document Reply blocked: Phone is locked")
            
            // Could add notification here if needed
            // NotificationHelper.showLockScreenWarning(context)
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error showing simple lock warning: ${e.message}")
        }
    }
}