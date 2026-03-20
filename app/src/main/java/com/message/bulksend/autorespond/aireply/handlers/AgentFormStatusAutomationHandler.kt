package com.message.bulksend.autorespond.aireply.handlers

import android.content.Context
import com.message.bulksend.aiagent.tools.agentform.AgentFormAIIntegration
import com.message.bulksend.aiagent.tools.agentform.AgentFormAutomationStateManager
import com.message.bulksend.aiagent.tools.agentform.models.AgentFormContactSettings
import com.message.bulksend.aiagent.tools.agentform.AgentFormContactSettingsManager

/**
 * Automates contact-verification follow-up for AgentForm contact templates.
 *
 * Flow:
 * - After link send, check whether form opened / contact verified.
 * - If not verified, send reminder message.
 * - If verified, send confirmation message and queue follow-up assets.
 */
class AgentFormStatusAutomationHandler(
    private val agentFormIntegration: AgentFormAIIntegration
) : MessageHandler {

    override fun getPriority(): Int = 43

    override suspend fun handle(
        context: Context,
        message: String,
        response: String,
        senderPhone: String,
        senderName: String
    ): HandlerResult {
        return try {
            val settings = AgentFormContactSettingsManager(context).getSettings()
            if (!settings.autoStatusMonitorEnabled) {
                return HandlerResult(success = true)
            }

            val stateManager = AgentFormAutomationStateManager(context)
            val state = stateManager.getState(senderPhone) ?: return HandlerResult(success = true)

            if (state.completedAt > 0L) {
                return HandlerResult(success = true)
            }

            val now = System.currentTimeMillis()
            if (state.linkSentAt > 0L && now - state.linkSentAt < LINK_FRESH_GRACE_MS) {
                return HandlerResult(success = true)
            }

            // If AI just sent a fresh form link in this same reply, don't override it.
            if (response.contains("chataiform.com/f/", ignoreCase = true)) {
                return HandlerResult(success = true)
            }

            val status = agentFormIntegration.getRecipientVerificationStatus(
                recipientPhoneRaw = senderPhone,
                campaignFilterRaw = state.campaign,
                formIdFilterRaw = state.formId,
                syncToSheet = true
            ) ?: return HandlerResult(success = true)

            if (status.verified) {
                var verifiedReply: String? = null
                if (!state.verifiedMessageSent) {
                    verifiedReply = resolveLanguageAwareMessage(
                        userMessage = message,
                        customMessage = settings.verifiedMessage,
                        defaultEnglish = "Your contact is verified.",
                        defaultHindi = "Aapka contact verify ho gaya hai."
                    )
                    stateManager.markVerifiedNotified(senderPhone, status.latestTimestamp)
                }

                if (settings.autoVerifiedFollowupEnabled) {
                    val videoUrl = settings.postSubmitVideoUrl.trim()
                    val pdfUrls = settings.postSubmitPdfs
                        .map { it.url.trim() }
                        .filter { it.isNotBlank() }
                    if (videoUrl.isNotBlank() || pdfUrls.isNotEmpty()) {
                        stateManager.queueFollowupResources(
                            phoneRaw = senderPhone,
                            videoUrlRaw = videoUrl,
                            pdfUrlsRaw = pdfUrls
                        )
                    } else {
                        stateManager.markCompleted(senderPhone)
                    }
                } else {
                    stateManager.markCompleted(senderPhone)
                }

                return if (verifiedReply.isNullOrBlank()) {
                    HandlerResult(success = true)
                } else {
                    HandlerResult(
                        success = true,
                        modifiedResponse = verifiedReply,
                        shouldStopChain = true
                    )
                }
            }

            if (!settings.autoReminderEnabled || !stateManager.shouldSendReminder(senderPhone)) {
                return HandlerResult(success = true)
            }

            val reminder = buildReminderMessage(
                settings = settings,
                userMessage = message,
                formOpened = status.formOpened,
                formLink = state.link
            )
            stateManager.markReminderSent(senderPhone)
            HandlerResult(
                success = true,
                modifiedResponse = reminder,
                shouldStopChain = true
            )
        } catch (e: Exception) {
            android.util.Log.e("AgentFormStatusHandler", "Error: ${e.message}", e)
            HandlerResult(success = false)
        }
    }

    private fun buildReminderMessage(
        settings: AgentFormContactSettings,
        userMessage: String,
        formOpened: Boolean,
        formLink: String
    ): String {
        val custom = settings.reminderMessage.trim()
        if (custom.isNotBlank()) {
            return appendFormLinkIfMissing(custom, formLink)
        }

        val hindi = isLikelyHindi(userMessage)
        val base = if (formOpened) {
            if (hindi) {
                "Aapne form open kiya hai, lekin contact verify abhi complete nahi hua. Kripya verify complete karein."
            } else {
                "You opened the form, but contact verification is still incomplete. Please complete verification."
            }
        } else {
            if (hindi) {
                "Aapne form abhi tak open nahi kiya. Kripya form open karke contact verify karein."
            } else {
                "You have not opened the form yet. Please open the form and verify your contact."
            }
        }
        return appendFormLinkIfMissing(base, formLink)
    }

    private fun appendFormLinkIfMissing(message: String, formLink: String): String {
        if (formLink.isBlank()) return message
        if (message.contains("http://", ignoreCase = true) || message.contains("https://", ignoreCase = true)) {
            return message
        }
        return "$message\n$formLink"
    }

    private fun resolveLanguageAwareMessage(
        userMessage: String,
        customMessage: String,
        defaultEnglish: String,
        defaultHindi: String
    ): String {
        val custom = customMessage.trim()
        if (custom.isNotBlank()) return custom
        return if (isLikelyHindi(userMessage)) defaultHindi else defaultEnglish
    }

    private fun isLikelyHindi(text: String): Boolean {
        if (text.isBlank()) return false
        val devanagari = Regex("[\\u0900-\\u097F]")
        if (devanagari.containsMatchIn(text)) return true

        val lower = text.lowercase()
        val hints = listOf(
            "kya", "hai", "nahi", "kripya", "aap", "kar", "karo", "verify karo", "bhej", "batao"
        )
        val hitCount = hints.count { lower.contains(it) }
        return hitCount >= 2
    }

    companion object {
        private const val LINK_FRESH_GRACE_MS = 30_000L
    }
}

