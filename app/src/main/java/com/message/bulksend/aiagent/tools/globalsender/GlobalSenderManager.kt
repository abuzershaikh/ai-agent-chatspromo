package com.message.bulksend.aiagent.tools.globalsender

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.util.Log
import com.message.bulksend.autorespond.documentreply.DocumentFile
import com.message.bulksend.autorespond.documentreply.DocumentSendService
import com.message.bulksend.autorespond.documentreply.DocumentType
import com.message.bulksend.bulksend.CampaignState
import com.message.bulksend.bulksend.WhatsAppAutoSendService
import com.message.bulksend.utils.AccessibilityHelper
import com.message.bulksend.utils.WhatsPref
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.net.URLEncoder

/**
 * Global Sender tool for AI Agent.
 * Single place to send text + media using WhatsApp accessibility flow.
 */
class GlobalSenderManager(private val context: Context) {

    private var wakeLock: PowerManager.WakeLock? = null
    @Suppress("DEPRECATION")
    private var keyguardLock: KeyguardManager.KeyguardLock? = null

    data class SendResult(
        val success: Boolean,
        val status: String,
        val message: String,
        val phoneNumber: String = "",
        val targetPackage: String? = null
    )

    suspend fun sendTextViaAccessibility(
        phoneNumber: String,
        message: String,
        preferredPackage: String? = null,
        timeoutMs: Long = 9_000L
    ): SendResult = withContext(Dispatchers.IO) {
        val sanitizedPhone = sanitizePhone(phoneNumber)
        if (sanitizedPhone.isBlank()) {
            return@withContext SendResult(
                success = false,
                status = "INVALID_PHONE",
                message = "Valid phone number not found"
            )
        }
        if (message.isBlank()) {
            return@withContext SendResult(
                success = false,
                status = "EMPTY_MESSAGE",
                message = "Message is empty"
            )
        }
        if (!isAccessibilityEnabled()) {
            return@withContext SendResult(
                success = false,
                status = "ACCESSIBILITY_DISABLED",
                message = "Accessibility service is disabled"
            )
        }

        val packageName = resolveWhatsAppPackage(preferredPackage)
            ?: return@withContext SendResult(
                success = false,
                status = "WHATSAPP_NOT_FOUND",
                message = "WhatsApp package not available"
            )

        val wasAutoSendEnabled = CampaignState.isAutoSendEnabled
        return@withContext try {
            acquireWakeLockAndDismissKeyguard()
            CampaignState.isSendActionSuccessful = null
            WhatsAppAutoSendService.activateService()
            CampaignState.isAutoSendEnabled = true

            val encodedMessage = URLEncoder.encode(message, "UTF-8")
            val intent = Intent(
                Intent.ACTION_VIEW,
                Uri.parse("https://wa.me/$sanitizedPhone?text=$encodedMessage")
            ).apply {
                setPackage(packageName)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            context.startActivity(intent)
            Log.d(TAG, "Global sender text launched to $sanitizedPhone via $packageName")

            val pollDelay = 400L
            val maxAttempts = (timeoutMs / pollDelay).coerceAtLeast(1)
            repeat(maxAttempts.toInt()) {
                delay(pollDelay)
                when (CampaignState.isSendActionSuccessful) {
                    true -> {
                        finalizeTextSendState(wasAutoSendEnabled)
                        return@withContext SendResult(
                            success = true,
                            status = "SENT_ACCESSIBILITY",
                            message = "Message sent by accessibility",
                            phoneNumber = sanitizedPhone,
                            targetPackage = packageName
                        )
                    }

                    false -> {
                        finalizeTextSendState(wasAutoSendEnabled)
                        return@withContext SendResult(
                            success = false,
                            status = "FAILED_ACCESSIBILITY",
                            message = "Accessibility send action failed",
                            phoneNumber = sanitizedPhone,
                            targetPackage = packageName
                        )
                    }

                    null -> Unit
                }
            }

            finalizeTextSendState(wasAutoSendEnabled)
            SendResult(
                success = false,
                status = "TIMEOUT_ACCESSIBILITY",
                message = "Accessibility send timed out",
                phoneNumber = sanitizedPhone,
                targetPackage = packageName
            )
        } catch (e: Exception) {
            finalizeTextSendState(wasAutoSendEnabled)
            Log.e(TAG, "Global sender text failed: ${e.message}", e)
            SendResult(
                success = false,
                status = "ERROR",
                message = e.message ?: "Unknown send error",
                phoneNumber = sanitizedPhone,
                targetPackage = packageName
            )
        }
    }

    fun queueDocumentsForAccessibility(
        phoneNumber: String,
        senderName: String,
        keyword: String = "GLOBAL_SENDER",
        documentPaths: List<String> = emptyList(),
        documentType: DocumentType? = null,
        documents: List<DocumentFile> = emptyList()
    ): SendResult {
        val sanitizedPhone = sanitizePhone(phoneNumber)
        if (sanitizedPhone.isBlank()) {
            return SendResult(
                success = false,
                status = "INVALID_PHONE",
                message = "Valid phone number not found"
            )
        }

        if (!isAccessibilityEnabled()) {
            return SendResult(
                success = false,
                status = "ACCESSIBILITY_DISABLED",
                message = "Accessibility service is disabled"
            )
        }

        val packageName = resolveWhatsAppPackage(null)
            ?: return SendResult(
                success = false,
                status = "WHATSAPP_NOT_FOUND",
                message = "WhatsApp package not available"
            )

        return try {
            DocumentSendService.enableDocumentSend()
            DocumentSendService.getInstance().addDocumentSendTask(
                context = context,
                phoneNumber = sanitizedPhone,
                senderName = senderName,
                keyword = keyword,
                documentPaths = documentPaths,
                documentType = documentType,
                documents = documents
            )

            SendResult(
                success = true,
                status = "DOCUMENT_QUEUED",
                message = "Document task queued for accessibility send",
                phoneNumber = sanitizedPhone,
                targetPackage = packageName
            )
        } catch (e: Exception) {
            Log.e(TAG, "Global sender document queue failed: ${e.message}", e)
            SendResult(
                success = false,
                status = "ERROR",
                message = e.message ?: "Unknown document queue error",
                phoneNumber = sanitizedPhone,
                targetPackage = packageName
            )
        }
    }

    fun isAccessibilityEnabled(): Boolean {
        return AccessibilityHelper.isAccessibilityServiceEnabled(
            context,
            ACCESSIBILITY_SERVICE_NAME
        )
    }

    fun resolveWhatsAppPackage(preferredPackage: String? = null): String? {
        val packageManager = context.packageManager

        fun isInstalled(packageName: String): Boolean {
            return try {
                packageManager.getPackageInfo(packageName, 0)
                true
            } catch (_: Exception) {
                false
            }
        }

        val preferred = preferredPackage?.takeIf { it.isNotBlank() }
            ?: WhatsPref.getSelectedPackage(context)

        if (!preferred.isNullOrBlank() && isInstalled(preferred)) {
            return preferred
        }
        if (isInstalled(WHATSAPP_BUSINESS_PACKAGE)) return WHATSAPP_BUSINESS_PACKAGE
        if (isInstalled(WHATSAPP_PACKAGE)) return WHATSAPP_PACKAGE
        return null
    }

    private fun restoreAutoSendState(wasAutoSendEnabled: Boolean) {
        if (!wasAutoSendEnabled) {
            CampaignState.isAutoSendEnabled = false
            WhatsAppAutoSendService.deactivateService()
        }
    }

    private fun finalizeTextSendState(wasAutoSendEnabled: Boolean) {
        restoreAutoSendState(wasAutoSendEnabled)
        releaseWakeLockAndReenableKeyguard()
    }

    private fun acquireWakeLockAndDismissKeyguard() {
        try {
            if (wakeLock?.isHeld != true) {
                val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
                wakeLock = powerManager.newWakeLock(
                    PowerManager.SCREEN_BRIGHT_WAKE_LOCK or
                        PowerManager.ACQUIRE_CAUSES_WAKEUP or
                        PowerManager.ON_AFTER_RELEASE,
                    "BulkSend:GlobalSenderWakeLock"
                ).apply {
                    acquire(10 * 60 * 1000L)
                }
                Log.d(TAG, "Global sender wake lock acquired")
            }

            @Suppress("DEPRECATION")
            val keyguardManager = context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
            @Suppress("DEPRECATION")
            keyguardLock = keyguardManager.newKeyguardLock("BulkSend:GlobalSenderKeyguard")
            @Suppress("DEPRECATION")
            keyguardLock?.disableKeyguard()
            Log.d(TAG, "Global sender keyguard dismissed")
        } catch (e: Exception) {
            Log.e(TAG, "Global sender wake/keyguard setup failed: ${e.message}")
        }
    }

    private fun releaseWakeLockAndReenableKeyguard() {
        try {
            @Suppress("DEPRECATION")
            keyguardLock?.reenableKeyguard()
            keyguardLock = null
        } catch (e: Exception) {
            Log.e(TAG, "Global sender keyguard re-enable failed: ${e.message}")
        }

        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
            wakeLock = null
        } catch (e: Exception) {
            Log.e(TAG, "Global sender wake lock release failed: ${e.message}")
        }
    }

    private fun sanitizePhone(value: String): String {
        return value.replace(Regex("[^0-9]"), "")
    }

    private companion object {
        const val TAG = "GlobalSender"
        const val ACCESSIBILITY_SERVICE_NAME = "com.message.bulksend.bulksend.WhatsAppAutoSendService"
        const val WHATSAPP_PACKAGE = "com.whatsapp"
        const val WHATSAPP_BUSINESS_PACKAGE = "com.whatsapp.w4b"
    }
}
