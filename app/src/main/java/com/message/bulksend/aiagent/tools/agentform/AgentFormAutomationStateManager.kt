package com.message.bulksend.aiagent.tools.agentform

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

data class AgentFormAutomationState(
    val phone: String,
    val campaign: String = "",
    val formId: String = "",
    val templateKey: String = "",
    val link: String = "",
    val linkSentAt: Long = 0L,
    val lastReminderAt: Long = 0L,
    val reminderCount: Int = 0,
    val verifiedAt: Long = 0L,
    val verifiedMessageSent: Boolean = false,
    val followupVideoUrl: String = "",
    val followupPdfUrls: List<String> = emptyList(),
    val followupQueuedAt: Long = 0L,
    val followupSentAt: Long = 0L,
    val completedAt: Long = 0L
)

data class AgentFormFollowupResources(
    val videoUrl: String,
    val pdfUrls: List<String>
)

class AgentFormAutomationStateManager(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()

    fun setPendingLink(
        phoneRaw: String,
        campaign: String,
        formId: String,
        templateKey: String,
        link: String
    ) {
        val phone = sanitizePhone(phoneRaw)
        if (phone.isBlank()) return
        val states = loadStates()
        states[phone] = AgentFormAutomationState(
            phone = phone,
            campaign = campaign.trim(),
            formId = formId.trim(),
            templateKey = templateKey.trim(),
            link = link.trim(),
            linkSentAt = System.currentTimeMillis()
        )
        persist(states)
    }

    fun getState(phoneRaw: String): AgentFormAutomationState? {
        val phone = sanitizePhone(phoneRaw)
        if (phone.isBlank()) return null
        val states = loadStates()
        val state = states[phone] ?: return null
        if (isExpired(state)) {
            states.remove(phone)
            persist(states)
            return null
        }
        return state
    }

    fun clearState(phoneRaw: String) {
        val phone = sanitizePhone(phoneRaw)
        if (phone.isBlank()) return
        val states = loadStates()
        if (states.remove(phone) != null) {
            persist(states)
        }
    }

    fun shouldSendReminder(phoneRaw: String, minIntervalMs: Long = DEFAULT_REMINDER_INTERVAL_MS): Boolean {
        val state = getState(phoneRaw) ?: return false
        if (state.completedAt > 0L || state.verifiedAt > 0L) return false
        val now = System.currentTimeMillis()
        return state.lastReminderAt <= 0L || (now - state.lastReminderAt) >= minIntervalMs
    }

    fun markReminderSent(phoneRaw: String) {
        update(phoneRaw) { state ->
            state.copy(
                lastReminderAt = System.currentTimeMillis(),
                reminderCount = state.reminderCount + 1
            )
        }
    }

    fun markVerifiedNotified(phoneRaw: String, verifiedAt: Long) {
        update(phoneRaw) { state ->
            state.copy(
                verifiedAt = if (verifiedAt > 0L) verifiedAt else System.currentTimeMillis(),
                verifiedMessageSent = true
            )
        }
    }

    fun queueFollowupResources(phoneRaw: String, videoUrlRaw: String, pdfUrlsRaw: List<String>) {
        val videoUrl = videoUrlRaw.trim()
        val pdfUrls = pdfUrlsRaw.map { it.trim() }.filter { it.isNotBlank() }.distinct()
        if (videoUrl.isBlank() && pdfUrls.isEmpty()) return
        update(phoneRaw) { state ->
            state.copy(
                followupVideoUrl = videoUrl,
                followupPdfUrls = pdfUrls,
                followupQueuedAt = System.currentTimeMillis(),
                followupSentAt = 0L
            )
        }
    }

    fun getPendingFollowup(phoneRaw: String): AgentFormFollowupResources? {
        val state = getState(phoneRaw) ?: return null
        if (state.followupQueuedAt <= 0L || state.followupSentAt > 0L) return null
        val videoUrl = state.followupVideoUrl.trim()
        val pdfUrls = state.followupPdfUrls.map { it.trim() }.filter { it.isNotBlank() }.distinct()
        if (videoUrl.isBlank() && pdfUrls.isEmpty()) return null
        return AgentFormFollowupResources(videoUrl = videoUrl, pdfUrls = pdfUrls)
    }

    fun markFollowupSent(phoneRaw: String) {
        update(phoneRaw) { state ->
            state.copy(
                followupSentAt = System.currentTimeMillis(),
                followupVideoUrl = "",
                followupPdfUrls = emptyList()
            )
        }
    }

    fun markCompleted(phoneRaw: String) {
        update(phoneRaw) { state ->
            state.copy(completedAt = System.currentTimeMillis())
        }
    }

    private fun update(phoneRaw: String, transform: (AgentFormAutomationState) -> AgentFormAutomationState) {
        val phone = sanitizePhone(phoneRaw)
        if (phone.isBlank()) return
        val states = loadStates()
        val current = states[phone] ?: return
        states[phone] = transform(current)
        persist(states)
    }

    private fun sanitizePhone(value: String): String = value.replace(Regex("[^0-9]"), "")

    private fun loadStates(): MutableMap<String, AgentFormAutomationState> {
        val raw = prefs.getString(KEY_STATES, null)?.trim().orEmpty()
        if (raw.isBlank()) return linkedMapOf()
        return runCatching {
            val mapType = object : TypeToken<MutableMap<String, AgentFormAutomationState>>() {}.type
            gson.fromJson<MutableMap<String, AgentFormAutomationState>>(raw, mapType) ?: linkedMapOf()
        }.getOrElse {
            linkedMapOf()
        }
    }

    private fun persist(states: MutableMap<String, AgentFormAutomationState>) {
        prefs.edit().putString(KEY_STATES, gson.toJson(states)).apply()
    }

    private fun isExpired(state: AgentFormAutomationState): Boolean {
        val anchor = when {
            state.completedAt > 0L -> state.completedAt
            state.followupSentAt > 0L -> state.followupSentAt
            state.linkSentAt > 0L -> state.linkSentAt
            else -> 0L
        }
        if (anchor <= 0L) return false
        return System.currentTimeMillis() - anchor > MAX_STATE_AGE_MS
    }

    companion object {
        private const val PREF_NAME = "agentform_automation_state"
        private const val KEY_STATES = "states_json"
        const val DEFAULT_REMINDER_INTERVAL_MS = 3L * 60L * 1000L
        private const val MAX_STATE_AGE_MS = 7L * 24L * 60L * 60L * 1000L
    }
}

