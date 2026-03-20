package com.message.bulksend.reminders

import android.content.Context
import com.message.bulksend.aiagent.tools.reverseai.ReverseAIManager
import com.message.bulksend.userdetails.UserDetailsPreferences

/**
 * Enforces strict reminder scheduling origin:
 * reminders from incoming chat are allowed only when sender matches owner number.
 */
class ReminderScheduleGuard(context: Context) {

    private val reverseAIManager = ReverseAIManager(context)
    private val userDetailsPreferences = UserDetailsPreferences(context)

    fun canScheduleFromIncomingChat(senderPhone: String): Boolean {
        val normalizedSender = normalizePhone(senderPhone)
        if (normalizedSender.isBlank()) return false

        val ownerNumbers = linkedSetOf<String>()

        val reverseOwner = normalizePhone(reverseAIManager.ownerPhoneNumber)
        if (reverseOwner.isNotBlank()) ownerNumbers += reverseOwner

        val accountOwner = normalizePhone(userDetailsPreferences.getPhoneNumber().orEmpty())
        if (accountOwner.isNotBlank()) ownerNumbers += accountOwner

        if (ownerNumbers.isEmpty()) return false

        return ownerNumbers.any { owner ->
            normalizedSender == owner ||
                normalizedSender.endsWith(owner) ||
                owner.endsWith(normalizedSender)
        }
    }

    private fun normalizePhone(raw: String): String {
        return raw.replace(Regex("[^0-9]"), "")
    }
}
