package com.message.bulksend.reminders

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.PowerManager
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.message.bulksend.autorespond.aireply.AIService
import com.message.bulksend.bulksend.CampaignState
import com.message.bulksend.utils.AccessibilityHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.net.URLEncoder

class ReminderCheckWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    private var wakeLock: PowerManager.WakeLock? = null
    @Suppress("DEPRECATION")
    private var keyguardLock: KeyguardManager.KeyguardLock? = null

    /**
     * Acquire wake lock and dismiss keyguard to prevent doze mode during reminder sending
     */
    private fun acquireWakeLockAndDismissKeyguard() {
        try {
            // Acquire wake lock
            val powerManager = applicationContext.getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK or 
                PowerManager.ACQUIRE_CAUSES_WAKEUP or 
                PowerManager.ON_AFTER_RELEASE,
                "BulkSend:ReminderWakeLock"
            )
            wakeLock?.acquire(10 * 60 * 1000L) // 10 minutes max
            android.util.Log.d("ReminderWorker", "✅ Wake lock acquired (SCREEN_BRIGHT|ACQUIRE_CAUSES_WAKEUP)")
            
            // Dismiss keyguard (lock screen)
            try {
                @Suppress("DEPRECATION")
                val keyguardManager = applicationContext.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
                @Suppress("DEPRECATION")
                keyguardLock = keyguardManager.newKeyguardLock("BulkSend:ReminderKeyguard")
                @Suppress("DEPRECATION")
                keyguardLock?.disableKeyguard()
                android.util.Log.d("ReminderWorker", "✅ Keyguard dismissed for reminder sending")
            } catch (e: Exception) {
                android.util.Log.e("ReminderWorker", "⚠️ Keyguard dismiss failed: ${e.message}")
            }
            
        } catch (e: Exception) {
            android.util.Log.e("ReminderWorker", "⚠️ Wake lock acquisition failed: ${e.message}")
        }
    }

    /**
     * Release wake lock and re-enable keyguard after reminder sending
     */
    private fun releaseWakeLockAndReenableKeyguard() {
        try {
            // Re-enable keyguard first
            try {
                @Suppress("DEPRECATION")
                keyguardLock?.reenableKeyguard()
                keyguardLock = null
                android.util.Log.d("ReminderWorker", "✅ Keyguard re-enabled after reminder sending")
            } catch (e: Exception) {
                android.util.Log.e("ReminderWorker", "⚠️ Keyguard re-enable failed: ${e.message}")
            }
            
            // Release wake lock
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
                android.util.Log.d("ReminderWorker", "✅ Wake lock released")
            }
            wakeLock = null
        } catch (e: Exception) {
            android.util.Log.e("ReminderWorker", "⚠️ Wake lock release failed: ${e.message}")
        }
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        android.util.Log.d("ReminderWorker", "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        android.util.Log.d("ReminderWorker", "🔄 REMINDER WORKER STARTED")
        android.util.Log.d("ReminderWorker", "⏰ Current time: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())}")
        android.util.Log.d("ReminderWorker", "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        
        // Acquire wake lock and dismiss keyguard FIRST
        acquireWakeLockAndDismissKeyguard()
        
        val manager = GlobalReminderManager(applicationContext)
        val aiService = AIService(applicationContext)
        
        // 1. Check if Accessibility Service is enabled
        val isAccessibilityEnabled = AccessibilityHelper.isAccessibilityServiceEnabled(applicationContext, "com.message.bulksend.bulksend.WhatsAppAutoSendService")
        android.util.Log.d("ReminderWorker", "🔍 Accessibility Service enabled: $isAccessibilityEnabled")
        
        if (!isAccessibilityEnabled) {
            android.util.Log.e("ReminderWorker", "❌ Accessibility Service not enabled. Cannot auto-send.")
            android.util.Log.e("ReminderWorker", "⚠️ Please enable: Settings → Accessibility → Chatspromo App")
            releaseWakeLockAndReenableKeyguard()
            return@withContext Result.failure()
        }

        try {
            val dueReminders = manager.getDueReminders()
            android.util.Log.d("ReminderWorker", "📋 Found ${dueReminders.size} due reminders")
            
            if (dueReminders.isEmpty()) {
                android.util.Log.d("ReminderWorker", "✅ No due reminders. Worker completed successfully.")
                releaseWakeLockAndReenableKeyguard()
                return@withContext Result.success()
            }

            // Enable Auto Send - BOTH flags needed!
            com.message.bulksend.bulksend.WhatsAppAutoSendService.activateService()
            CampaignState.isAutoSendEnabled = true
            android.util.Log.d("ReminderWorker", "🤖 Auto-send enabled (Service + State)")

            // Loop and process
            var successCount = 0
            var failCount = 0
            
            for ((index, reminder) in dueReminders.withIndex()) {
                try {
                    android.util.Log.d("ReminderWorker", "📤 Processing reminder ${index + 1}/${dueReminders.size} for: ${reminder.name} (${reminder.phone})")
                    
                    // 1. Resolve Message: prefer exact owner-defined message, fallback to AI generation
                    val message = reminder.ownerMessage.trim().ifBlank {
                        aiService.generateReminderMessage(
                            phone = reminder.phone,
                            name = reminder.name,
                            reminderContext = reminder.context,
                            dateTime = "${reminder.date} ${reminder.time}",
                            template = reminder.template
                        )
                    }
                    
                    android.util.Log.d("ReminderWorker", "� Generated message: ${message.take(50)}...")
                    
                    // 2. Send Message via Accessibility
                    val packageName = getAvailableWhatsAppPackage(applicationContext)
                    
                    if (packageName != null) {
                        android.util.Log.d("ReminderWorker", "📱 WhatsApp package: $packageName")
                        
                        // Reset state
                        CampaignState.isSendActionSuccessful = null
                        
                        // Launch WhatsApp
                        sendWhatsAppMessage(reminder.phone, message, packageName)
                        
                        // Wait for Accessibility Service to click send
                        var attempts = 0
                        var success = false
                        while (attempts < 20) { // Wait up to 10 seconds
                            delay(500)
                            if (CampaignState.isSendActionSuccessful == true) {
                                success = true
                                break
                            }
                            if (CampaignState.isSendActionSuccessful == false) {
                                break // Explicit failure
                            }
                            attempts++
                        }
                        
                        if (success) {
                            android.util.Log.d("ReminderWorker", "✅ Reminder sent successfully via Accessibility")
                            manager.updateReminderStatus(reminder.rowId, GlobalReminderManager.STATUS_SENT)
                            successCount++
                        } else {
                            android.util.Log.e("ReminderWorker", "❌ Failed to auto-send reminder (Restriction or Timeout)")
                            manager.updateReminderStatus(reminder.rowId, GlobalReminderManager.STATUS_FAILED)
                            failCount++
                        }
                    } else {
                        android.util.Log.e("ReminderWorker", "❌ WhatsApp not installed")
                        manager.updateReminderStatus(reminder.rowId, GlobalReminderManager.STATUS_FAILED)
                        failCount++
                    }
                    
                    // Delay between reminders
                    if (index < dueReminders.size - 1) {
                        android.util.Log.d("ReminderWorker", "⏳ Waiting 3 seconds before next reminder...")
                        delay(3000)
                    }
                    
                } catch (e: Exception) {
                    android.util.Log.e("ReminderWorker", "❌ Failed to process reminder: ${e.message}", e)
                    manager.updateReminderStatus(reminder.rowId, GlobalReminderManager.STATUS_FAILED)
                    failCount++
                }
            }
            
            android.util.Log.d("ReminderWorker", "📊 Summary: $successCount sent, $failCount failed")
            
        } catch (e: Exception) {
            android.util.Log.e("ReminderWorker", "❌ Error in worker: ${e.message}", e)
            return@withContext Result.failure()
        } finally {
            // Disable Auto Send
            com.message.bulksend.bulksend.WhatsAppAutoSendService.deactivateService()
            CampaignState.isAutoSendEnabled = false
            android.util.Log.d("ReminderWorker", "🤖 Auto-send disabled (Service + State)")
            
            // Release wake lock and re-enable keyguard
            releaseWakeLockAndReenableKeyguard()
        }
        
        android.util.Log.d("ReminderWorker", "✅ Worker completed successfully")
        return@withContext Result.success()
    }

    private fun sendWhatsAppMessage(phone: String, message: String, packageName: String) {
        val cleanNumber = phone.replace(Regex("[^\\d]"), "")
        val encodedMessage = URLEncoder.encode(message, "UTF-8")
        
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$cleanNumber?text=$encodedMessage")).apply {
            setPackage(packageName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP) // Ensure fresh start
        }
        try {
            applicationContext.startActivity(intent)
        } catch (e: Exception) {
            android.util.Log.e("ReminderWorker", "Failed to start activity: ${e.message}")
        }
    }
    
    private fun getAvailableWhatsAppPackage(context: Context): String? {
        val pm = context.packageManager
        return try {
            pm.getPackageInfo("com.whatsapp", PackageManager.GET_ACTIVITIES)
            "com.whatsapp"
        } catch (e: PackageManager.NameNotFoundException) {
            try {
                pm.getPackageInfo("com.whatsapp.w4b", PackageManager.GET_ACTIVITIES)
                "com.whatsapp.w4b"
            } catch (e: PackageManager.NameNotFoundException) {
                null
            }
        }
    }
}
