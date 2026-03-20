package com.message.bulksend.aiagent.tools.agentform

import android.content.Context
import com.google.gson.Gson
import com.message.bulksend.aiagent.tools.agentform.models.AgentFormContactSettings
import com.message.bulksend.aiagent.tools.agentform.models.AgentFormPostSubmitPdf
import com.message.bulksend.aiagent.tools.agentform.models.AgentFormSavedContact

class AgentFormContactSettingsManager(context: Context) {
    private val prefs =
            context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()

    fun getSettings(): AgentFormContactSettings {
        val json = prefs.getString(KEY_SETTINGS, null) ?: return AgentFormContactSettings()
        return try {
            val parsed = gson.fromJson(json, AgentFormContactSettings::class.java)
            normalizeSettings(parsed)
        } catch (_: Exception) {
            AgentFormContactSettings()
        }
    }

    fun saveSettings(settings: AgentFormContactSettings) {
        prefs.edit().putString(KEY_SETTINGS, gson.toJson(settings)).apply()
    }

    fun setEnabled(enabled: Boolean) {
        val current = getSettings()
        saveSettings(current.copy(enabled = enabled))
    }

    fun addOrUpdateContact(name: String, phone: String): AgentFormContactSettings {
        val normalizedName = name.trim()
        val normalizedPhone = sanitizePhone(phone)
        if (normalizedName.isBlank() || normalizedPhone.isBlank()) {
            return getSettings()
        }

        val current = getSettings()
        val updatedList = current.contacts.toMutableList()
        val existingIndex =
                updatedList.indexOfFirst { normalizeForMatch(it.phone) == normalizeForMatch(normalizedPhone) }
        if (existingIndex >= 0) {
            val existing = updatedList[existingIndex]
            updatedList[existingIndex] =
                    existing.copy(name = normalizedName, phone = normalizedPhone)
        } else {
            updatedList.add(
                    AgentFormSavedContact(
                            name = normalizedName,
                            phone = normalizedPhone
                    )
            )
        }

        val updated = current.copy(contacts = updatedList)
        saveSettings(updated)
        return updated
    }

    fun removeContact(contactId: String): AgentFormContactSettings {
        val current = getSettings()
        val updated = current.copy(contacts = current.contacts.filterNot { it.id == contactId })
        saveSettings(updated)
        return updated
    }

    fun clearContacts(): AgentFormContactSettings {
        val current = getSettings()
        val updated = current.copy(contacts = emptyList())
        saveSettings(updated)
        return updated
    }

    fun setPostSubmitContentEnabled(enabled: Boolean): AgentFormContactSettings {
        val current = getSettings()
        val updated = current.copy(postSubmitContentEnabled = enabled)
        saveSettings(updated)
        return updated
    }

    fun setPostSubmitVideoUrl(videoUrl: String): AgentFormContactSettings {
        val current = getSettings()
        val updated = current.copy(postSubmitVideoUrl = videoUrl.trim())
        saveSettings(updated)
        return updated
    }

    fun addOrReplacePostSubmitPdf(pdf: AgentFormPostSubmitPdf): AgentFormContactSettings {
        val normalizedUrl = pdf.url.trim()
        val normalizedName = pdf.name.trim()
        if (normalizedUrl.isBlank() || normalizedName.isBlank()) return getSettings()

        val current = getSettings()
        val updatedList = current.postSubmitPdfs.toMutableList()
        val existingIndex = updatedList.indexOfFirst { it.id == pdf.id || it.url == normalizedUrl }
        if (existingIndex >= 0) {
            updatedList[existingIndex] = pdf.copy(name = normalizedName, url = normalizedUrl)
        } else {
            if (updatedList.size >= MAX_POST_SUBMIT_PDFS) {
                updatedList.removeAt(0)
            }
            updatedList.add(pdf.copy(name = normalizedName, url = normalizedUrl))
        }

        val updated = current.copy(postSubmitPdfs = updatedList)
        saveSettings(updated)
        return updated
    }

    fun removePostSubmitPdf(pdfId: String): AgentFormContactSettings {
        val current = getSettings()
        val updated = current.copy(postSubmitPdfs = current.postSubmitPdfs.filterNot { it.id == pdfId })
        saveSettings(updated)
        return updated
    }

    fun clearPostSubmitPdfs(): AgentFormContactSettings {
        val current = getSettings()
        val updated = current.copy(postSubmitPdfs = emptyList())
        saveSettings(updated)
        return updated
    }

    fun setAutoStatusMonitorEnabled(enabled: Boolean): AgentFormContactSettings {
        val current = getSettings()
        val updated = current.copy(autoStatusMonitorEnabled = enabled)
        saveSettings(updated)
        return updated
    }

    fun setAutoReminderEnabled(enabled: Boolean): AgentFormContactSettings {
        val current = getSettings()
        val updated = current.copy(autoReminderEnabled = enabled)
        saveSettings(updated)
        return updated
    }

    fun setAutoVerifiedFollowupEnabled(enabled: Boolean): AgentFormContactSettings {
        val current = getSettings()
        val updated = current.copy(autoVerifiedFollowupEnabled = enabled)
        saveSettings(updated)
        return updated
    }

    fun setReminderMessage(message: String): AgentFormContactSettings {
        val current = getSettings()
        val updated = current.copy(reminderMessage = message.trim())
        saveSettings(updated)
        return updated
    }

    fun setVerifiedMessage(message: String): AgentFormContactSettings {
        val current = getSettings()
        val updated = current.copy(verifiedMessage = message.trim())
        saveSettings(updated)
        return updated
    }

    fun sanitizePhone(value: String): String = value.replace(Regex("[^0-9]"), "")

    private fun normalizeForMatch(value: String): String = value.replace(Regex("[^0-9]"), "")

    private fun normalizeSettings(raw: AgentFormContactSettings?): AgentFormContactSettings {
        if (raw == null) return AgentFormContactSettings()

        val contacts = raw.contacts
            .mapNotNull { contact ->
                val normalizedPhone = sanitizePhone(contact.phone)
                val normalizedName = contact.name.trim()
                if (normalizedPhone.isBlank() || normalizedName.isBlank()) null
                else contact.copy(name = normalizedName, phone = normalizedPhone)
            }
            .distinctBy { normalizeForMatch(it.phone) }

        val postSubmitPdfs = raw.postSubmitPdfs
            .mapNotNull { pdf ->
                val name = pdf.name.trim()
                val url = pdf.url.trim()
                if (name.isBlank() || url.isBlank()) null
                else pdf.copy(name = name, url = url)
            }
            .distinctBy { it.url }
            .take(MAX_POST_SUBMIT_PDFS)

        return AgentFormContactSettings(
            enabled = raw.enabled,
            contacts = contacts,
            postSubmitContentEnabled = raw.postSubmitContentEnabled,
            postSubmitVideoUrl = raw.postSubmitVideoUrl.trim(),
            postSubmitPdfs = postSubmitPdfs,
            autoStatusMonitorEnabled = raw.autoStatusMonitorEnabled,
            autoReminderEnabled = raw.autoReminderEnabled,
            autoVerifiedFollowupEnabled = raw.autoVerifiedFollowupEnabled,
            reminderMessage = raw.reminderMessage.trim(),
            verifiedMessage = raw.verifiedMessage.trim()
        )
    }

    companion object {
        private const val PREF_NAME = "agentform_contact_settings"
        private const val KEY_SETTINGS = "contact_settings_json"
        const val MAX_POST_SUBMIT_PDFS = 2
        const val MAX_POST_SUBMIT_PDF_SIZE_BYTES = 10L * 1024L * 1024L
    }
}
